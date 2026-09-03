# Frontend Platform Section — CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/frontend_platform/`
Global conventions (formatting, templates, Q&A rules): see root `CLAUDE.md`.
Build manifest / status tracker: `README.md` §7 — check it before starting a new module.

---

## Why this section exists

A survey of all 1,248 content files found the repository covers the **server half** of
every front-end concern and **none of the client half**:

| Already covered, server-side | Missing, client-side — this section |
|------------------------------|-------------------------------------|
| CORS config in `fastapi/`, `spring/` | preflight as the browser performs it, `credentials`, opaque responses |
| WebSocket framing in `backend/websockets_and_sse/` | the browser `WebSocket`/`EventSource` client, reconnect, ordering |
| Asset caching in `hld/cdn/`, `devops/` | content-hash invalidation, the post-deploy chunk 404 |
| JWT storage verdict in `hld/security_and_auth/` | PKCE, silent refresh, the WebAuthn ceremony |
| OWASP in `backend/backend_security_owasp/` | XSS sinks, CSP, Trusted Types, prototype pollution |

Hard zeros before this section existed: **Core Web Vitals (LCP/INP/CLS) appeared zero
times**; WCAG and ARIA appeared once; there was no module on the DOM, CSS, the rendering
pipeline, bundling, service workers or front-end testing anywhere in the repo.

This is the platform third of a three-section front-end track — `../typescript/` is the
language, `../react/` is the library. **Nothing here is React-specific.** Every technique
applies to any framework, or to none.

---

## PRIMARY TARGET VERSIONS

This section pins faster-moving things than any other in the repo. Verified 2026-09-02 —
**verify against each project's own release page before writing a version number, never
from memory.**

| Area | Pin | Note |
|------|-----|------|
| Node.js | **26 Current**, **24 "Krypton" Active LTS** | 24 started 2025-05-06, 30-month window; 22 in Maintenance |
| Vite | **8**, on **Rolldown** | Rolldown is the default bundler; the esbuild-dev / Rollup-build split is gone |
| ESLint | **10** | flat config only, `eslintrc` fully removed; ESLint 9 EOL 2026-08-06 |
| typescript-eslint | **8.69.0** | does **not** yet support TypeScript 7 / tsgo — no Compiler API until 7.1 |
| Biome | **2.5** | |
| oxlint type-aware | **GA 2026-07-22** | no longer alpha; docs still note incomplete rule coverage and memory use on very large repos |
| esbuild / swc | **0.28.2** / **1.16.1** | neither is declared legacy; Rolldown displaces esbuild's dev-server role only |
| pnpm / npm / Yarn Berry | **11.25.0** / **12.0.2** / **4.18.0** | pnpm 12 (Rust rewrite) stable 2026-08-26 under the `next-12` tag |
| Turborepo / Nx | **2.10.12** / **23.2.0** | |
| Prettier | **3.9.6** | |
| Deno / Bun | **2.9** / **1.4.0** | Bun 1.4.0 (2026-08-20) is the Zig to Rust core rewrite |

### The two standards every answer is graded against

**Core Web Vitals.** LCP good below **2.5s**, INP good below **200ms**, CLS good below
**0.1** — all at the **75th percentile** of real users via the CrUX dataset; a URL must pass
all three to pass overall. **INP fully replaced FID in March 2024.** Never present FID as
current, and never quote a threshold without saying "at p75" — the percentile is half the
definition.

**Accessibility.** **WCAG 2.2 AA is the operative standard** — ADA, Section 508 and the
European Accessibility Act all reference it. **WCAG 3.0 is a W3C Working Draft** (latest
2026-03-03): 174 "requirements" rather than success criteria, Bronze/Silver/Gold in place
of A/AA/AAA, with Bronze roughly equal to today's 2.2 AA. Candidate Recommendation is
targeted **Q4 2027**; legal adoption is estimated 2030 or later. **Teach 2.2 AA as
actionable and 3.0 as direction only** — a module that presents 3.0 as current is wrong.

**REPLACE, DO NOT ANNOTATE.** Never write "`foo()` is deprecated, use `bar()`" — write
`bar()`. The three exceptions live in root `CLAUDE.md`.

---

## Module List — 20 Modules (4 Phases)

| # | Module Directory | Phase | Key Concepts |
|---|-----------------|-------|-------------|
| 1 | `javascript_language_semantics/` | 1 — The Browser | Prototypes, closures, `this` binding, coercion, iterators/generators, `Proxy`/`Reflect`/`Symbol`/`WeakRef`, ESM live bindings |
| 2 | `browser_architecture_and_the_rendering_pipeline/` | 1 | Multi-process, site isolation, parse to style to layout to paint to composite, reflow vs repaint, forced synchronous layout |
| 3 | `the_dom_and_web_apis/` | 1 | DOM as tree and API, capture/bubble/delegation, passive listeners, the three observers, custom elements, shadow DOM |
| 4 | `css_layout_and_the_cascade/` | 1 | Box model, flexbox vs grid, specificity, cascade layers, container queries, logical properties and RTL, stacking contexts |
| 5 | `css_animation_and_motion/` | 1 | Compositor-only properties, FLIP, Web Animations API, View Transitions API, scroll-driven animation, `prefers-reduced-motion` |
| 6 | `the_event_loop_and_scheduling/` | 1 | Tasks vs microtasks, `requestAnimationFrame`, `requestIdleCallback`, long tasks, `scheduler.yield`, Web Workers |
| 7 | `http_in_the_browser_and_fetch/` | 2 — Network from the Client | `fetch`, streams, `AbortController`, retry and timeout, the browser HTTP cache, ETag, HTTP/2 and /3 request strategy |
| 8 | `cors_cookies_and_the_same_origin_model/` | 2 | Same-origin policy, preflight from the browser side, `credentials`, opaque responses, `SameSite`, partitioned cookies |
| 9 | `browser_security_and_the_client_threat_model/` | 2 | XSS sinks, CSP and nonces, Trusted Types, SRI, prototype pollution, `postMessage`, build-time env leaks |
| 10 | `auth_in_the_browser/` | 2 | Token storage tradeoffs, OAuth 2.1 with PKCE, silent refresh, passkeys and WebAuthn, logout and session invalidation |
| 11 | `realtime_in_the_browser/` | 2 | `WebSocket` and `EventSource` clients, reconnect with backoff and jitter, heartbeats, ordering, WebTransport |
| 12 | `client_memory_and_performance_debugging/` | 3 — Runtimes, Build & Toolchain | Heap snapshots, detached DOM and listener retention, GC-hostile shapes, hidden classes, the WASM boundary |
| 13 | `the_node_and_edge_runtimes/` | 3 | libuv event-loop phases, streams and backpressure, ESM/CJS interop, worker threads, edge runtimes and their constraints |
| 14 | `modules_bundling_and_the_build_pipeline/` | 3 | ESM semantics, tree-shaking and what defeats it, code splitting, Vite 8 on Rolldown, Turbopack, HMR, source maps |
| 15 | `package_management_and_supply_chain/` | 3 | npm/pnpm/yarn resolution, lockfiles, workspaces and task graphs, peer deps and duplicate React, provenance |
| 16 | `linting_formatting_and_code_quality/` | 3 | ESLint 10 flat config, typescript-eslint type-aware rules and their cost, Biome, oxlint, Prettier |
| 17 | `web_performance_and_core_web_vitals/` | 4 — Quality & Delivery | LCP/INP/CLS at p75, lab vs field, resource hints, images and fonts, the performance budget |
| 18 | `accessibility_and_wcag/` | 4 | WCAG 2.2 AA, the accessibility tree, semantic HTML first, ARIA as last resort, keyboard and focus, axe in CI |
| 19 | `pwa_service_workers_and_offline/` | 4 | Storage APIs and quotas, SW lifecycle and scope traps, caching strategies, background sync, install and update UX |
| 20 | `frontend_delivery_and_observability/` | 4 | Static hosting and edge, immutable hashed assets, the post-deploy chunk 404, RUM, source-map upload, client OpenTelemetry |

## Sub-Files (0)

None. **If you add one, never name it `*_deep_dive.md` or `*_internals.md`** —
`DEEP_NAME_RE` in `game/build_tech.py` makes any such page a FATAL `--strict` error unless
hand-listed. `client_memory_and_performance_debugging` is named around this rule; the
obvious names (`v8_internals.md`, `browser_internals.md`) are all forbidden.

## Case Studies — 0 standalone

Two module `## 14. Case Study` sections carry the weight instead and are held above a
normal §14:

- **`web_performance_and_core_web_vitals`** — a complete before-and-after performance
  incident with real numbers at every stage. This is the most transferable single artifact
  in the whole front-end track; do not write it as a tour of APIs.
- **`pwa_service_workers_and_offline`** — offline-first sync and conflict resolution, which
  is the genuinely hard half of "offline". Not a service-worker API tour.

---

## Learning Paths (Full + Senior + Principal)

`README.md` documents the **Full Path** (all 20 modules) plus two curated tiers. They are
**different cuts, not nested depths** — senior is the craft, principal is the judgment.
Membership is declared ONCE, in the `<!-- study-paths -->` block in this section's
`README.md`. Order is never declared there — it comes from `STUDY_ORDER.frontend_platform`
in `game/app.js`. **There is no path array in `app.js` to edit**; `extract.py` emits the
gitignored `questions/paths.json`. The tier tables between `<!-- study-path-table <tier> -->`
markers are **generated** by `python3 game/extract.py --write-paths` — never hand-edit them,
and note that `--write-paths` only fills marker pairs that already exist.

Intended tier shape once modules land:

- **Senior:** 1, 2, 3, 4, 6, 7, 8, 9, 10, 11, 14, 17, 18, 19
- **Principal:** 9, 12, 13, 14, 15, 17, 20

---

## Q&A Minimums

- **15+** per module page — the hard floor from root `CLAUDE.md`
- **18+** for: `cors_cookies_and_the_same_origin_model`,
  `browser_security_and_the_client_threat_model`,
  `modules_bundling_and_the_build_pipeline`, `web_performance_and_core_web_vitals`,
  `accessibility_and_wcag`
- Order by interview frequency: gotchas and traps first, then internals, then edge cases
- Every answer opens with its `**Short:**` line — 15-220 characters, self-contained
- **Any pass that edits an answer MUST update the `**Short:**` above it in the same edit**

---

## Code Style in This Section

- Fences: ` ```ts `, ` ```js `, ` ```css `, ` ```html `, ` ```json `. **Never**
  ` ```typescript ` — the reader's `LANG_ALIAS` does not alias the long form and it renders
  unhighlighted.
- Browser API examples run in a real browser as written, or are marked a fragment.
- At least one **`// BROKEN` → `// FIX`** pair in §10 and one in §14 of every module.
- Concrete numbers everywhere — real thresholds, real byte counts, real millisecond
  budgets. Never "a few", "some", "significant".
- This repository's own learning game (`game/app.js`, 578KB of dependency-free vanilla
  JavaScript with no build step, plus `game/sw.js`) is a legitimate worked example for the
  DOM, service-worker and bundling modules. It is an **app, not study content** — cite it,
  never treat it as a module.

---

## The "hydration" term collision — MANDATORY

**`hydration` already means *feed-object hydration*** in 14 files across `backend/` and
`hld/` case studies (feed fan-out, `design_feed_service`, `design_twitter`). MCQ distractors
are drawn from sibling answers and IDF-ranked across the section, widening to the repo, so a
bare "hydration" question will be paired with feed fan-out answers.

**Always write "client hydration" or "SSR hydration" on first use in every module.** This
applies to `react/` too and is stated in both section CLAUDE.md files for that reason.

---

## Boundary with the neighbouring sections — who owns what

Every row is a real collision. The existing module owns the server or theory side; this
section owns the client side; neither restates the other.

| This section's module | Existing owner | Boundary |
|-----------------------|----------------|----------|
| `http_in_the_browser_and_fetch` | `backend/http_protocols/` | backend owns the protocol (HTTP/2, HPACK, TLS, ALPN); this owns the client |
| `cors_cookies_and_the_same_origin_model` | `fastapi/middleware_and_lifecycle/`, `spring/spring_security_architecture/` | those own server config; this owns preflight and opaque responses |
| `auth_in_the_browser` | `backend/auth_and_authorization_systems/`, `hld/security_and_auth/` | those own issuance and the storage verdict; this owns PKCE and WebAuthn |
| `realtime_in_the_browser` | `backend/websockets_and_sse/`, `hld/realtime_updates/` | those own framing and scaling; this owns the client |
| `the_node_and_edge_runtimes` | `backend/os_and_io_performance/`, `cs_fundamentals/.../async_io_and_event_loops.md` | those own OS I/O and the general concept; this owns Node's phases and edge constraints |
| `client_memory_and_performance_debugging` | `cs_fundamentals/how_code_runs_compilers_and_interpreters/` | that owns JIT theory; this owns heap snapshots and SPA leaks |
| `package_management_and_supply_chain` | `python/packaging_and_project_tooling/`, `go/build_deployment_and_supply_chain/`, `devops/` | those own their language; this owns npm resolution |
| `frontend_delivery_and_observability` | `hld/cdn/`, `devops/cloud_networking_and_cdn/`, `backend/observability_and_monitoring/` | those own CDN mechanics and server telemetry; this owns invalidation, RUM, source maps |
| `browser_security_and_the_client_threat_model` | `backend/backend_security_owasp/` | that owns server-side OWASP; this owns browser-resident attack classes |
| `javascript_language_semantics` | `typescript/` | this section owns the JavaScript substrate; `typescript/` starts where types are added |
| `browser_architecture_...` vs `css_layout_and_the_cascade` | each other | the pipeline module owns layout/paint/composite *mechanics*; the CSS module owns *authoring*. State the line in both |

---

## Section §11 stays `## 11. Technologies & Tools`

This section **wants** to feed the technology index. Do not retitle §11. A §11 row's first
cell is a bare product name, under 42 characters, at most 5 spaces. A **spaced** ` / ` is
split into two records correctly; an **unspaced** slash (`Vite/Rolldown`) is kept as one
literal name and becomes a garbled record. Use two rows.

Expect mostly **new** bank records — Vite, webpack, Rollup, esbuild, Turbopack, Rolldown,
ESLint, Prettier, Biome, pnpm, Lighthouse, axe and Playwright are not currently banked. One
live collision: **`yarn` in the bank is already YaRN**, the RoPE rescaling method
(`tech_bank/llm-apps.md`, `Kind: concept`); a bare "Yarn" citation needs a `TECH_HOMONYMS`
entry keyed by section. **`Astro` does not** — the bank's record is headed
`### Astronomer Astro` and keys separately.

---

## Diagram Traps

Diagrams are **appeal-first**. Mermaid is the default; run `/mermaid-diagrams` first,
colour every node with the One-Dark `classDef` palette, keep labels short (`<br/>`), use
rounded shapes. **A fence that does not parse FAILS THE BUILD** —
`scripts/build_diagrams.mjs` exits 1 and takes Pages and the APK red.

| Trap | Breaks | Fix |
|------|--------|-----|
| `;` in a `sequenceDiagram` `Note` or message | statement separator cuts the text | `,` or `.` |
| `call` as a node id | reserved word | rename |
| Extra spaces after an edge label's closing `\|` | parse error | single space |
| `\"` in a node label | no escape exists | `#quot;` |
| `<link>`, `<script>`, `<meta>` written raw in a label | `<` opens a tag | quote the label, or write the tag name without brackets |
| `:` or `()` in a `quadrant-N` label | `:` is the data separator | quote it |
| A quadrant coordinate of exactly `1.0` | must be `< 1.0` | `0.98` |

This section writes more HTML-ish labels than any other — the fifth row is its most likely
failure. **Validate anything you touch:**
`node scripts/check_render.mjs <absolute-file.md>`.

**ASCII stays** for the shapes Mermaid cannot draw, and this section has several genuine
ones: the CSS box model, a flexbox/grid axis diagram, a stacking-context tree, the
event-loop turn as a timeline, and a waterfall chart. Use `/visual-intuition-diagrams`;
conventions (no tabs, no emojis, widest line <= 100 cols, caption every diagram) are in root
`CLAUDE.md`.

---

## Adding a New Frontend Platform Module

1. Create `<module_name>/<module_name>.md` — the 14-section template, named for its folder
2. Verify every version and threshold against the upstream source before writing it
3. Meet the Q&A minimum, every answer opening with its `**Short:**` line
4. Add the module dir to `STUDY_ORDER.frontend_platform` in `game/app.js` at its phase
   position — a module missing from it fails `--strict`
5. Add the module and EVERY file it owns to the `<!-- study-paths -->` block in this
   section's `README.md`, tagging tiers. **Do NOT put a block in the module page.** Then run
   `python3 game/extract.py --write-paths`
6. Flip Status `pending` to `done` in `README.md` §7 with measured counts
7. Update this file's Module List and `README.md` §2 and §3
8. Update root `README.md` and root `CLAUDE.md`

---

## HARD RULE — structure lives in `README.md`, content files hold only content

**The section `README.md` is the single source of truth for this section's file inventory
and study-tier membership.** Its `<!-- study-paths -->` block lists EVERY module and EVERY
file it owns, each tagged with its tiers — `-` means Full path only.

**A content file carries NO structural metadata** — no block, no tier declaration, no path
membership. It holds the content of its topic and nothing else.

**Adding a file? Add its line to the block in the same commit.** A file on disk missing from
the block — or listed there and absent from disk — FAILS
`python3 game/extract.py --strict` and takes the Pages deploy red. Both directions are
fatal, which is why the block is grown file-by-file rather than pre-populated.

Order comes from `STUDY_ORDER` in `game/app.js`. The tier TABLES in the README are
GENERATED by `python3 game/extract.py --write-paths` — never hand-edit them.
