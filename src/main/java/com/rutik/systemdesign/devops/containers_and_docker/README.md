# Containers & Docker

> Phase 2 — Containers & Kubernetes · Difficulty: Intermediate

Containers package an application with its dependencies into a portable, immutable image that runs identically on a laptop, in CI, and in production. Docker popularized the workflow, but the value is in understanding what an image *is* (layered, content-addressed), how builds cache, and how to produce small, secure, reproducible images — the artifacts every Kubernetes pod ultimately runs.

> The kernel primitives (namespaces, cgroups) are covered in [linux_and_os_fundamentals](../linux_and_os_fundamentals/); the runtime that actually executes images (containerd/runc) is in [container_runtimes_and_oci](../container_runtimes_and_oci/). This module is about *images and the build*.

---

## 1. Concept Overview

A **container image** is a stack of read-only **layers** plus a JSON config (entrypoint, env, exposed ports). Each layer is a tarball of filesystem changes, content-addressed by digest. At runtime the engine stacks the layers via a union filesystem (overlayfs) and adds a thin writable layer on top — that's the running **container**.

Key properties:
- **Immutable + layered** — layers are shared and cached across images; pulling a new image only fetches layers you don't already have.
- **Content-addressed** — `sha256:...` digests make images reproducible and verifiable.
- **OCI-standard** — the image and runtime formats are open standards, so images built by Docker run on containerd, Podman, etc.

The build is driven by a **Dockerfile**: each instruction (`RUN`, `COPY`, `ADD`) produces a layer; the build cache reuses unchanged layers, making instruction *ordering* the single biggest determinant of build speed.

---

## 2. Intuition

> **One-line analogy**: An image is like a stack of transparent sheets — a base OS sheet, a dependencies sheet, an app-code sheet — laid on top of each other; you only reprint the sheets that changed, and many apps can share the same base sheet underneath.

**Mental model**: Think of the Dockerfile as a recipe where each step bakes a layer. Docker caches each baked layer keyed by the instruction *and* the state of its inputs. Change an early step and every later layer's cache is invalidated (cache busting). The runtime just stacks these baked layers read-only and gives your process a writable scratch layer on top.

**Why it matters**: Image size and build time directly affect deploy speed, registry cost, cold-start latency, and attack surface. A 1.2 GB image with a full OS and build toolchain pulls slowly, costs more to store, and ships hundreds of CVEs; a 30 MB distroless image pulls fast and has almost nothing to exploit. The difference is entirely in how you write the Dockerfile.

**Key insight**: Layer ordering is a caching strategy. Put rarely-changing things (base image, dependency installs) *early* and frequently-changing things (your source code) *late*, so a code change only rebuilds the last cheap layer — not a 5-minute `npm install`.

---

## 3. Core Principles

1. **Images are immutable, layered, content-addressed.** Tag for humans, pin by digest for reproducibility.
2. **Order layers by change frequency.** Stable first (deps), volatile last (code), to maximize cache hits.
3. **Multi-stage builds separate build-time from run-time.** Compile in a fat builder, copy only artifacts into a lean runtime image.
4. **Smaller is better.** Less to pull, store, scan, and exploit. Prefer slim/distroless/scratch bases.
5. **Run as non-root, read-only where possible.** Reduce blast radius of a compromise.
6. **One concern per container.** A container runs one main process; orchestration composes them.

---

## 4. Types / Architectures / Strategies

### Base image choices

| Base | Size (approx) | Pros | Cons |
|------|---------------|------|------|
| `ubuntu` / `debian` | 70–120 MB | Familiar, full toolset, shell | Large, many packages = CVEs |
| `*-slim` | 30–80 MB | Smaller, still has package manager | Some tools missing |
| `alpine` | ~5 MB base | Tiny, musl libc | musl vs glibc bugs (DNS, Python wheels) |
| `distroless` (Google) | ~2–20 MB | No shell/package manager, minimal CVEs | Hard to debug (no shell) |
| `scratch` | 0 | Absolute minimum (static binaries) | Only for fully static apps (Go) |

### Multi-stage build (the standard pattern)

Compile/build in a stage with the full toolchain, then `COPY --from=builder` only the final artifact into a minimal runtime image. The toolchain, source, and intermediate files never ship.

---

## 5. Architecture Diagrams

```
Image = read-only layers + writable container layer

  +-----------------------------+  <- writable layer (per running container)
  | container R/W (logs, tmp)   |
  +=============================+  <- image layers (read-only, shared, cached)
  | COPY app code      (digest) |   changes every commit -> keep LAST
  | RUN npm ci         (digest) |   changes when deps change
  | COPY package.json  (digest) |
  | FROM node:20-slim  (digest) |   base, shared across many images
  +-----------------------------+

Multi-stage build

  Stage 1 (builder: node:20)        Stage 2 (runtime: distroless)
  +------------------------+        +---------------------------+
  | COPY src               |        | COPY --from=builder       |
  | RUN npm ci && build    | =====> |   /app/dist  /app/node_*  |
  | -> dist/, node_modules |  only  | CMD ["server.js"]         |
  | (toolchain 900MB)      | artifa.| (final image ~80MB)       |
  +------------------------+        +---------------------------+
       discarded                         shipped
```

---

## 6. How It Works — Detailed Mechanics

### Cache-optimized multi-stage Dockerfile

```dockerfile
# ---- build stage: has the full toolchain ----
FROM node:20-slim AS builder
WORKDIR /app

# Copy ONLY manifests first so the expensive install layer caches across code changes.
COPY package.json package-lock.json ./
RUN npm ci                                   # cached unless package*.json changes

COPY . .                                     # source changes often -> later layer
RUN npm run build                            # produces /app/dist

# ---- runtime stage: minimal, no toolchain, no source ----
FROM gcr.io/distroless/nodejs20-debian12 AS runtime
WORKDIR /app
COPY --from=builder /app/dist        ./dist
COPY --from=builder /app/node_modules ./node_modules
USER nonroot                                 # distroless ships a nonroot user
EXPOSE 3000
CMD ["dist/server.js"]
```

Why this order: editing source busts only `COPY . .` and `npm run build`, reusing the cached `npm ci` (often the slowest step). Copying everything first would re-run `npm ci` on every code change.

### Inspecting layers and size

```bash
docker history myimg:latest          # per-layer size + the instruction that created it
docker image inspect myimg:latest --format '{{.RootFS.Layers}}'   # layer digests
dive myimg:latest                    # interactive layer/wasted-space explorer
```

### BuildKit features (default in modern Docker)

```dockerfile
# syntax=docker/dockerfile:1
# Cache mount: persist the package cache across builds without baking it into a layer.
RUN --mount=type=cache,target=/root/.npm npm ci

# Secret mount: use a token at build time WITHOUT it landing in any layer.
RUN --mount=type=secret,id=npmtoken \
    NPM_TOKEN=$(cat /run/secrets/npmtoken) npm ci
```

```bash
# Multi-arch build (amd64 + arm64) in one command:
docker buildx build --platform linux/amd64,linux/arm64 -t myimg:1.0 --push .
```

### .dockerignore matters

```
# Without it, "COPY . ." sends node_modules, .git, secrets, and 500MB of junk
# into the build context -> slow builds, fat images, leaked files.
node_modules
.git
.env
*.log
```

### Reproducibility: tag vs digest

```bash
# Tags are mutable (someone can re-push :latest). Digests are immutable.
FROM node:20-slim                                   # mutable
FROM node:20-slim@sha256:abc123...                  # pinned, reproducible
```

---

## 7. Real-World Examples

- **Distroless at Google**: production services ship on `gcr.io/distroless/*` — no shell, no package manager — cutting image CVEs to near zero and shrinking attack surface.
- **Go `scratch` images**: a statically linked Go binary in a `FROM scratch` image is often 10–20 MB total, with literally nothing else to exploit.
- **GitHub Actions / CI build caching**: `docker buildx` with registry or GHA cache backends reuses layers across pipeline runs, turning 6-minute builds into 40-second ones on code-only changes.
- **Multi-arch images** (Apple Silicon dev, Graviton/ARM prod): one manifest list serves both `amd64` and `arm64`, so the same tag runs on x86 CI and ARM nodes.

---

## 8. Tradeoffs

| Decision | Option A | Option B | Key factor |
|----------|----------|----------|-----------|
| Base image | Alpine (tiny, musl) | Debian-slim (compatible, glibc) | Compatibility vs size; musl can break DNS/wheels |
| Final image | Distroless/scratch (secure, small) | Full distro (debuggable) | Security vs ease of debugging |
| Build secret | Build-arg (LEAKS into layer) | BuildKit secret mount (safe) | Never use ARG for secrets |
| Image reference | Tag (convenient) | Digest (reproducible) | Reproducibility/supply-chain needs |
| Layers | Few big (simple) | Ordered by change frequency | Cache efficiency |
| Process model | One process/container | Multiple (supervisor) | Orchestration vs legacy apps |

---

## 9. When to Use / When NOT to Use

**Containers fit when:** you want environment parity (dev/CI/prod), fast horizontal scaling, immutable deploys, and orchestration via Kubernetes/ECS.

**Containers are a poor fit when:** the workload needs a full GUI desktop, kernel modules/specialized hardware drivers not exposed to containers, or strong isolation of untrusted code (use microVMs/gVisor — see [container_runtimes_and_oci](../container_runtimes_and_oci/)). Stateful databases *can* be containerized but often run better as managed services.

---

## 10. Common Pitfalls

**Pitfall 1 — Baking a secret into a layer via `ARG`.**

```dockerfile
# BROKEN: the token is permanently recorded in image history; anyone with the image
# can run `docker history --no-trunc` / extract the layer and read it.
ARG NPM_TOKEN
RUN npm config set //registry.npmjs.org/:_authToken=$NPM_TOKEN && npm ci
```

```dockerfile
# FIX: BuildKit secret mount — available only during that RUN, never stored in a layer.
# syntax=docker/dockerfile:1
RUN --mount=type=secret,id=npmtoken \
    npm config set //registry.npmjs.org/:_authToken="$(cat /run/secrets/npmtoken)" && npm ci
# build: docker build --secret id=npmtoken,env=NPM_TOKEN .
```

**Pitfall 2 — Cache-busting layer order.** Putting `COPY . .` before `RUN npm ci` means every source edit re-runs the full dependency install (minutes per build). FIX: copy manifests, install, *then* copy source (see §6).

**Pitfall 3 — Running as root.** The default container user is root; a container escape or RCE then has root on the namespace (and with a host mount, potentially worse).

```dockerfile
# FIX: create and switch to a non-root user.
RUN useradd -r -u 10001 appuser
USER 10001
# In Kubernetes also set securityContext: runAsNonRoot: true, readOnlyRootFilesystem: true
```

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| Docker / `buildx` | Build/run; BuildKit, multi-arch |
| Podman / Buildah | Daemonless, rootless builds |
| `dive` | Inspect layers, find wasted space |
| Trivy / Grype | Image CVE scanning (see [devsecops](../devsecops_and_supply_chain_security/)) |
| distroless / Chainguard images | Minimal, low-CVE base images |
| Kaniko / BuildKit | In-cluster, daemonless image builds (CI) |
| `docker-slim` | Auto-minify images |
| cosign | Sign/verify images |

---

## 12. Interview Questions with Answers

**Q1: What is a container image, structurally?**
It's an ordered stack of read-only filesystem layers (each a content-addressed tarball of changes) plus a JSON config holding the entrypoint, env, and metadata. At runtime the engine unions the layers (overlayfs) and adds a thin writable layer for the running container. Layers are shared and cached across images, so pulling a new image only downloads layers you don't already have.

**Q2: How does the build cache work and how do you exploit it?**
Each Dockerfile instruction produces a layer cached by the instruction plus the state of its inputs (e.g., the files a `COPY` references). If an early layer's inputs change, all subsequent layers are rebuilt. You exploit this by ordering instructions from least- to most-frequently-changing: base image and dependency installs early, source code copy and build late — so a code edit rebuilds only the cheap final layers.

**Q3: What does a multi-stage build buy you?**
It separates the build environment from the runtime environment. You compile in a fat "builder" stage with the full toolchain, then `COPY --from=builder` only the final artifact into a minimal runtime image. The toolchain, source code, and intermediate files never ship — yielding a much smaller, more secure final image (e.g., a 900 MB builder producing an 80 MB runtime).

**Q4: Why is running as root in a container dangerous, and how do you avoid it?**
Root in the container is root in its namespaces; combined with a kernel container-escape vulnerability or a host mount, it escalates the blast radius of any compromise. Avoid it by creating a non-root user (`USER 10001`) in the image and enforcing `runAsNonRoot: true` plus `readOnlyRootFilesystem: true` in the Kubernetes `securityContext`.

**Q5: Tag vs digest — why pin by digest?**
A tag (`node:20`) is a mutable pointer — the same tag can be re-pushed to point at different content, so builds aren't reproducible and a supply-chain attacker could swap it. A digest (`node:20@sha256:...`) is immutable and content-verified, guaranteeing you get exactly the bits you tested. Pin by digest for reproducible builds and supply-chain integrity.

**Q6: Alpine vs Debian-slim — what's the catch with Alpine?**
Alpine is tiny (~5 MB) but uses musl libc instead of glibc. This causes real problems: different DNS resolution behavior, Python packages that ship glibc wheels needing recompilation (slower builds), and occasional subtle runtime bugs. Debian-slim is larger but glibc-compatible and avoids these surprises — many teams choose slim for reliability over Alpine's size.

**Q7: How do you keep build secrets out of the final image?**
Never use `ARG` or `ENV` for secrets — they persist in image history/layers. Use BuildKit's `--mount=type=secret`, which exposes the secret only during a specific `RUN` and never writes it to a layer. For the image itself, inject runtime secrets via the orchestrator (env from a Secret, mounted file), not baked in.

**Q8: Why is `.dockerignore` important?**
The build "context" (everything sent to the daemon for `COPY`) defaults to the whole directory. Without `.dockerignore`, `COPY . .` ships `node_modules`, `.git`, `.env`, and logs into the image — bloating size, slowing builds, and leaking secrets/history. `.dockerignore` excludes them, keeping the context and image lean and safe.

**Q9: What is distroless and what's the tradeoff?**
Distroless images contain only your app and its runtime dependencies — no shell, no package manager, no busybox. This minimizes attack surface and CVE count dramatically. The tradeoff is debuggability: you can't `kubectl exec … sh` into them, so you debug via ephemeral debug containers, host-side `nsenter`, or by reproducing locally.

**Q10: How do multi-arch images work and why care?**
A multi-arch image is a manifest list mapping each platform (`linux/amd64`, `linux/arm64`) to a platform-specific image; the runtime pulls the one matching its CPU. `docker buildx build --platform ...` builds them in one command. It matters because devs run Apple Silicon (arm64), CI may be x86, and prod may use ARM nodes (AWS Graviton) — one tag serves all.

**Q11: What's the difference between an image and a container?**
An image is the immutable, layered template (build artifact); a container is a running (or stopped) instance of an image — the read-only layers plus a writable scratch layer and a process with its own namespaces/cgroups. Many containers can run from one image; deleting a container doesn't affect the image.

**Q12: How do you reduce a 1.2 GB image to under 100 MB?**
Use a multi-stage build (drop the toolchain and source), choose a minimal runtime base (distroless/slim/scratch), order layers for caching, add a `.dockerignore`, combine and clean `RUN` steps (remove apt caches in the same layer), and avoid installing unnecessary packages. The biggest win is usually multi-stage + a minimal base.

---

## 13. Best Practices

- Use **multi-stage builds**; ship a minimal runtime base (distroless/slim/scratch).
- **Order layers by change frequency**; copy dependency manifests before source.
- Add a **`.dockerignore`**; never `COPY` `.git`/secrets/`node_modules`.
- **Run as non-root**, read-only rootfs, drop capabilities.
- **Pin base images by digest** for reproducibility; scan every image (Trivy) in CI.
- Use **BuildKit secret/cache mounts**; never bake secrets via `ARG`/`ENV`.
- One main process per container; use exec-form `CMD`/`ENTRYPOINT` (signals — see [linux_and_os_fundamentals](../linux_and_os_fundamentals/)).
- Build **multi-arch** images when dev/prod CPU architectures differ.

---

## 14. Case Study

### Scenario: 6-minute CI builds and a 1.4 GB image slowing every deploy

A Node.js service's image is 1.4 GB and rebuilds take ~6 minutes on every commit, throttling the team's deploy frequency. Registry storage and pull times at scale (200 nodes) are also painful.

```
Original Dockerfile (single stage, bad order)
  FROM node:20            # full image ~1.1GB
  COPY . .                # source FIRST -> any code change busts cache below
  RUN npm install         # full devDependencies + build toolchain shipped
  CMD npm start           # shell-form, root user, dev deps in prod image
   -> 1.4GB, 6min builds, runs as root, hundreds of CVEs
```

```dockerfile
# FIX: multi-stage + cache-friendly order + distroless + non-root.
# syntax=docker/dockerfile:1
FROM node:20-slim AS builder
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci      # cached across code changes
COPY . .
RUN npm run build && npm prune --omit=dev            # drop devDependencies

FROM gcr.io/distroless/nodejs20-debian12
WORKDIR /app
COPY --from=builder /app/dist ./dist
COPY --from=builder /app/node_modules ./node_modules
USER nonroot
CMD ["dist/server.js"]
```

**Outcome metrics:**

| Metric | Before | After |
|--------|--------|-------|
| Image size | 1.4 GB | 95 MB |
| Cold build | 6 min | 5 min (deps changed) |
| Code-only rebuild | 6 min | ~40 s (deps cached) |
| Image CVEs (high/critical) | ~180 | ~3 |
| Runs as | root | nonroot |
| Pull time across 200 nodes | minutes | seconds |

**Discussion questions:**
1. Why did the `npm ci` cache mount cut code-only rebuilds from 6 min to ~40 s?
2. What debugging capability did moving to distroless cost, and how do you get it back? (Ephemeral debug containers, `kubectl debug`.)
3. How would pinning the base by digest and scanning in CI fit the supply-chain story (see [devsecops_and_supply_chain_security](../devsecops_and_supply_chain_security/))?

---

**Cross-references:** [linux_and_os_fundamentals](../linux_and_os_fundamentals/) (namespaces/cgroups, PID 1, signals), [container_runtimes_and_oci](../container_runtimes_and_oci/) (how images execute), [kubernetes_workloads_and_objects](../kubernetes_workloads_and_objects/) (pods run these images), [devsecops_and_supply_chain_security](../devsecops_and_supply_chain_security/) (scanning, signing, SBOM).
