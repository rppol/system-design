# Linux & OS Fundamentals

> Phase 1 — Foundations · Difficulty: Intermediate

The operating system is the substrate every container, pod, and cloud VM runs on. A senior DevOps engineer who understands processes, signals, file descriptors, cgroups, and namespaces can explain *why* a pod gets OOMKilled, *why* a graceful shutdown hangs, and *why* a "container" is just a process with a restricted view of the kernel — not a virtual machine.

---

## 1. Concept Overview

Linux is a multi-user, multi-tasking, monolithic-kernel operating system. For DevOps the kernel matters because **containers are a kernel feature, not a product**: Docker, containerd, and Kubernetes are orchestration layers over three Linux primitives — **namespaces** (what a process can *see*), **cgroups** (what a process can *use*), and **capabilities** (what a process can *do*).

The kernel manages four resource classes that every production incident eventually traces back to:

- **CPU** — scheduled in time slices by the CFS (Completely Fair Scheduler); cgroup quotas can throttle it.
- **Memory** — virtual memory backed by physical RAM and swap; the OOM killer reaps processes under pressure.
- **I/O** — block devices and the page cache; `iostat`/`iotop` expose saturation.
- **Processes** — created via `fork()`/`exec()`, terminated via signals, reaped via `wait()`.

User space talks to the kernel through **system calls** (`read`, `write`, `openat`, `clone`, `mmap`). Tracing those syscalls (`strace`, `bpftrace`) is how you debug a process whose logs say nothing.

---

## 2. Intuition

> **One-line analogy**: The kernel is the building's facilities management — it owns the power (CPU), the rooms (memory), the plumbing (I/O), and the keys (capabilities); every process is a tenant that must ask facilities for everything via a service desk (system calls).

**Mental model**: A running Linux system is a tree of processes descending from PID 1 (`init`/`systemd`), each with its own virtual address space, file-descriptor table, and set of credentials. The kernel time-slices the CPU among them and gives each the *illusion* of owning the whole machine. A container shrinks that illusion further: the process still runs on the host kernel, but namespaces narrow its view (it sees PID 1 = its own entrypoint) and cgroups cap its consumption.

**Why it matters**: Most production failures are resource failures — out of memory, out of file descriptors, out of PIDs, CPU-throttled, disk full. You cannot diagnose them from application logs; you read them from `/proc`, `dmesg`, `cgroup` files, and `ss`. The engineer who knows where these live resolves incidents in minutes instead of hours.

**Key insight**: "Containerization" is subtractive, not additive. You start with a normal process and *remove* its ability to see/use the rest of the system. Understanding the un-restricted process first makes every container behavior obvious.

---

## 3. Core Principles

1. **Everything is a process or a file.** Devices, sockets, and pipes are file descriptors. A process is an entry in the kernel's task list with an address space and credentials.
2. **Isolation is composed from namespaces.** There are 8 namespace types (PID, NET, MNT, UTS, IPC, USER, CGROUP, TIME). A container is a process placed into a fresh set of them.
3. **Limits come from cgroups.** Control groups (v2 is the modern unified hierarchy) cap CPU, memory, I/O, and PIDs per group. Kubernetes resource limits become cgroup limits.
4. **Signals are the IPC of lifecycle.** SIGTERM (graceful), SIGKILL (forced, uncatchable), SIGHUP (reload) drive every shutdown and restart you operate.
5. **The kernel protects itself with the OOM killer.** When memory is exhausted and cannot be reclaimed, the kernel kills the process with the worst `oom_score` to survive.
6. **Resources are finite and per-namespace.** File descriptors (`ulimit -n`), PIDs (`pids.max`), and memory (`memory.max`) all have ceilings that production workloads routinely hit.

---

## 4. Types / Architectures / Strategies

### The 8 namespace types

| Namespace | Isolates | Container effect |
|-----------|----------|------------------|
| PID | Process IDs | Container's entrypoint becomes PID 1 |
| NET | Network stack (interfaces, routes, ports) | Container gets its own `eth0`, ports |
| MNT | Mount points | Container sees only its own filesystem |
| UTS | Hostname/domain | `hostname` differs from host |
| IPC | System V IPC, POSIX message queues | Isolated shared memory |
| USER | UID/GID mappings | Root in container ≠ root on host |
| CGROUP | cgroup root view | Hides host cgroup hierarchy |
| TIME | Boot/monotonic clocks | (5.6+) per-container clock offset |

### cgroups v1 vs v2

| Aspect | cgroups v1 | cgroups v2 |
|--------|-----------|-----------|
| Hierarchy | Multiple, one per controller | Single unified tree |
| Memory limit file | `memory.limit_in_bytes` | `memory.max` |
| Soft limit | `memory.soft_limit_in_bytes` | `memory.high` (throttle before OOM) |
| Default on modern distros | Legacy | Default (RHEL 9, Ubuntu 22.04+) |
| Kubernetes support | Mature | GA since 1.25 |

---

## 5. Architecture Diagrams

```
            USER SPACE                                KERNEL SPACE
+--------------------------------------+   +-------------------------------+
|  systemd (PID 1)                     |   |  Scheduler (CFS)              |
|    |                                 |   |  Memory mgr (VM, page cache)  |
|    +-- sshd                          |   |  VFS / block layer            |
|    +-- containerd                    |   |  Network stack                |
|         +-- container shim           |   |  Namespaces  cgroups  caps    |
|              +-- app (PID 1 in ns)   |   +-------------------------------+
|                   |  syscalls               ^   ^   ^   ^
|                   +---- read/write/clone ----+   |   |   |
+--------------------------------------+           |   |   |
                                                   |   |   |
   /proc/<pid>/  <----- introspection -------------+   |   |
   /sys/fs/cgroup/ <--- limits + accounting ------------+   |
   signals (SIGTERM/SIGKILL) <--- lifecycle ---------------+
```

```
A "container" = a process with restricted views

   normal process                  containerized process
   +-----------------+             +-----------------+
   | sees all PIDs   |             | PID ns: sees    |
   | sees host net   |    minus    | only own tree   |
   | sees host fs    |  namespaces | NET ns: own eth0|
   | unlimited RAM   |   + cgroups | MNT ns: own /   |
   |                 |             | memory.max=512M |
   +-----------------+             +-----------------+
```

---

## 6. How It Works — Detailed Mechanics

### Process creation: fork + exec

```bash
# A shell running "ls" does roughly this:
#   pid = fork();              // clone the process (copy-on-write address space)
#   if (pid == 0) execve("/bin/ls", argv, envp);  // replace image in child
#   else waitpid(pid, &status, 0);                 // parent reaps the child
```

Containers use `clone()` (the generalized `fork`) with namespace flags:

```c
clone(child_fn, stack, CLONE_NEWPID | CLONE_NEWNET | CLONE_NEWNS | SIGCHLD, arg);
```

### Inspecting a process via /proc

```bash
PID=$(pgrep -n nginx)
cat /proc/$PID/status      | grep -E 'VmRSS|Threads|FDSize'  # memory, thread, fd counts
ls  /proc/$PID/fd          | wc -l                            # open file descriptors
cat /proc/$PID/limits      | grep 'open files'                # soft/hard fd limit
cat /proc/$PID/cgroup                                         # which cgroups it belongs to
readlink /proc/$PID/ns/pid                                    # PID namespace inode
```

### cgroup v2 limits in practice

```bash
# Kubernetes translates "resources: limits: memory: 512Mi, cpu: 500m" into:
cat /sys/fs/cgroup/kubepods.slice/.../memory.max     # 536870912  (512 * 1024^2)
cat /sys/fs/cgroup/kubepods.slice/.../cpu.max        # 50000 100000  (0.5 CPU: 50ms per 100ms)
cat /sys/fs/cgroup/kubepods.slice/.../memory.current # live usage
cat /sys/fs/cgroup/kubepods.slice/.../memory.events  # oom_kill counter
```

`cpu.max = "50000 100000"` means 50 ms of CPU time per 100 ms period = 0.5 cores. Exceed it and the process is **throttled** (paused), not killed — which manifests as latency spikes, not crashes.

### The OOM killer

When memory cannot be reclaimed, the kernel computes an `oom_score` per process (roughly proportional to memory used, adjusted by `oom_score_adj` in `-1000..1000`) and kills the highest. In a cgroup-limited container, hitting `memory.max` triggers a **cgroup-local** OOM kill even if the node has free RAM:

```bash
dmesg -T | grep -i 'killed process'
# [Tue ...] Memory cgroup out of memory: Killed process 12345 (java) total-vm:...
```

### Signals and graceful shutdown

```bash
kill -TERM <pid>   # 15: polite "please exit"; app can trap and drain
kill -KILL <pid>   #  9: kernel removes the process; cannot be caught/ignored
kill -HUP  <pid>   #  1: conventionally "reload config" (nginx, haproxy)
```

Kubernetes sends **SIGTERM**, waits `terminationGracePeriodSeconds` (default **30s**), then **SIGKILL**. An app that ignores SIGTERM will always be hard-killed after the grace period — dropping in-flight requests.

---

## 7. Real-World Examples

- **Kubernetes pod eviction**: under node memory pressure the kubelet evicts BestEffort pods first by sending SIGTERM; the underlying mechanism is cgroup accounting plus the OOM killer for limit breaches.
- **Datadog/Prometheus node exporters** read `/proc` and `/sys/fs/cgroup` to produce `node_memory_*`, `container_cpu_cfs_throttled_seconds_total`, etc. — the metrics you alert on are literally these files.
- **systemd as PID 1** on cloud VMs manages service units, restarts, and journald logging; `systemctl` and `journalctl -u <svc>` are the first tools on any EC2 debugging session.
- **gVisor (Google)** and **Kata Containers** trade some of the "shared kernel" model for stronger isolation by interposing a user-space kernel or a lightweight VM — relevant when running untrusted multi-tenant workloads.

---

## 8. Tradeoffs

| Decision | Option A | Option B | Key factor |
|----------|----------|----------|-----------|
| Init in containers | App as PID 1 (simple) | `tini`/`dumb-init` (reaps zombies, forwards signals) | Does the app spawn children / handle SIGTERM? |
| Memory control | `memory.max` hard limit (OOM on breach) | `memory.high` throttle (reclaim pressure) | Tolerate latency vs tolerate kills |
| CPU control | CPU limits (throttling) | CPU requests only (no cap) | Latency-sensitive vs throughput batch |
| Isolation strength | Namespaces (shared kernel, fast) | microVM/gVisor (stronger, overhead) | Trust level of workload |
| Swap | Off (K8s default) | On (zswap/swap accounting) | Predictability vs memory headroom |

---

## 9. When to Use / When NOT to Use

**Reach for OS-level reasoning when:**
- Diagnosing OOMKills, CPU throttling, "too many open files", zombie processes, or hung shutdowns.
- Sizing requests/limits and `ulimit`s for a workload.
- Hardening a container (drop capabilities, run non-root, read-only rootfs).

**Do NOT over-index on it when:**
- The problem is clearly application logic (a 500 from a bad query) — go to app logs/traces first.
- A managed platform abstracts it well; you rarely tune CFS parameters by hand on a managed node pool.

---

## 10. Common Pitfalls

**Pitfall 1 — PID 1 doesn't reap zombies, and ignores SIGTERM.**

```dockerfile
# BROKEN: shell-form CMD makes /bin/sh PID 1; it does NOT forward SIGTERM to your app,
# and a parent process leaves <defunct> zombie children.
CMD npm start
```

```dockerfile
# FIX: exec-form so the app is PID 1 and receives signals directly,
# plus a tiny init to reap zombies and forward signals.
ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["node", "server.js"]
# Or in Kubernetes: spec.shareProcessNamespace + a proper init, or just exec-form CMD.
```

**Pitfall 2 — "Too many open files" under load.** The default soft `ulimit -n` is often **1024**. A high-connection proxy or DB pool exhausts it and `accept()` returns `EMFILE`.

```bash
# Diagnose:
cat /proc/$(pgrep -n envoy)/limits | grep 'open files'   # 1024  4096
ls /proc/$(pgrep -n envoy)/fd | wc -l                    # 1023  <- at the ceiling
# FIX (systemd unit): LimitNOFILE=1048576   (or pod securityContext / sysctls)
```

**Pitfall 3 — Setting CPU limits on a latency-sensitive service.** A limit of `cpu: 500m` throttles the process every 100 ms period once it spends 50 ms; tail latency spikes appear with `container_cpu_cfs_throttled_periods_total` climbing while average CPU looks low. Often the fix is to set **requests** for scheduling but drop the **limit**.

---

## 11. Technologies & Tools

| Tool | Purpose | When |
|------|---------|------|
| `top` / `htop` / `btop` | Live process + resource view | First glance at load |
| `ps aux` / `pgrep` | Process enumeration/lookup | Find a PID |
| `strace` / `ltrace` | Trace syscalls / library calls | "Why is it stuck?" |
| `bpftrace` / `bcc` | eBPF tracing, low overhead | Production deep-dive |
| `lsof` | List open files/sockets | FD leaks, port owners |
| `ss` (replaces `netstat`) | Socket statistics | Connection states |
| `dmesg` / `journalctl` | Kernel ring buffer / systemd logs | OOM kills, driver errors |
| `systemctl` | Service management | Start/stop/status on VMs |
| `nsenter` | Enter a process's namespaces | Debug a container from the host |
| `vmstat` / `iostat` / `mpstat` | Memory / I/O / per-CPU stats | Saturation triage |

---

## 12. Interview Questions with Answers

**Q1: What actually is a container at the kernel level?**
A container is an ordinary Linux process placed into its own set of namespaces and constrained by cgroups, optionally with reduced capabilities and a pivoted root filesystem. There is no "container" object in the kernel — `docker run` calls `clone()` with `CLONE_NEW*` flags, writes cgroup limits, and `execve()`s the entrypoint. This is why containers share the host kernel and start in milliseconds, unlike VMs.

**Q2: A pod is OOMKilled but `kubectl top node` shows free memory. Why?**
The container exceeded its cgroup `memory.max` (its `resources.limits.memory`), triggering a *cgroup-local* OOM kill independent of node-level free memory. Requests affect scheduling and QoS; limits are a hard ceiling enforced per cgroup. Check `dmesg` for "Memory cgroup out of memory" and `memory.events` `oom_kill` count.

**Q3: Difference between a process's requests and limits, and how do they map to the kernel?**
Requests are a scheduling guarantee (`cpu.weight` / reserved memory used by the scheduler to place the pod); limits are enforcement — `cpu.max` (CFS quota, causes throttling) and `memory.max` (causes OOM kill on breach). Requests never throttle or kill; limits do.

**Q4: SIGTERM vs SIGKILL — why does graceful shutdown sometimes fail?**
SIGTERM (15) is catchable: a well-behaved app traps it, stops accepting new work, drains in-flight requests, then exits. SIGKILL (9) is uncatchable — the kernel destroys the process immediately. Shutdown "fails" (drops requests) when the app ignores SIGTERM or takes longer than `terminationGracePeriodSeconds`, after which Kubernetes escalates to SIGKILL.

**Q5: What does the OOM killer use to choose a victim?**
It computes `oom_score` per process, roughly proportional to memory footprint, adjusted by `oom_score_adj` (`-1000` = never kill, `+1000` = kill first). Under whole-node pressure it kills the highest scorer; under cgroup pressure it kills within the offending cgroup. Kubernetes sets `oom_score_adj` so BestEffort pods die before Guaranteed ones.

**Q6: Why might a process show 100% CPU but the service is slow, vs low CPU but slow?**
High CPU + slow = genuinely CPU-bound (profile the hot path). Low CPU + slow + rising `cpu.cfs_throttled` = CFS throttling against a CPU limit. Low CPU + slow + high iowait (`vmstat`/`iostat`) = blocked on disk/network I/O. The metrics distinguish the three.

**Q7: What is a zombie process and how does it arise in containers?**
A zombie (`<defunct>`) is a terminated child whose exit status hasn't been reaped by its parent via `wait()`. In containers, if PID 1 is a shell or an app that spawns children but never reaps them, zombies accumulate and can exhaust the PID table. Fix: use an init like `tini`/`dumb-init` as PID 1, or `shareProcessNamespace` with a proper init.

**Q8: How do you debug a running container from the host without a shell inside it?**
Use `nsenter --target <host-pid> --mount --net --pid` to enter the container's namespaces with host tools, or `kubectl debug` ephemeral containers. Distroless/scratch images have no shell, so host-side `nsenter`, `crictl`, `strace -p`, and `/proc/<pid>/` inspection are essential.

**Q9: What are Linux capabilities and why drop them?**
Capabilities split root's powers into ~40 units (`CAP_NET_BIND_SERVICE`, `CAP_SYS_ADMIN`, etc.). Dropping all and adding back only what's needed shrinks the blast radius of a compromise — a container that only needs to bind port 80 gets `CAP_NET_BIND_SERVICE` and nothing else. `securityContext.capabilities.drop: ["ALL"]` is the hardened default.

**Q10: cgroups v1 vs v2 — why does it matter for Kubernetes?**
v2 is a single unified hierarchy with better memory pressure control (`memory.high` throttles before `memory.max` OOMs) and accurate accounting. Kubernetes added GA cgroup v2 support in 1.25; on cgroup v2 nodes, memory QoS and the OOM behavior are more predictable. Mismatches between the runtime's expectation and the node's cgroup version cause subtle resource-enforcement bugs.

**Q11: What does `ulimit -n` control and where does it bite in production?**
It's the per-process soft limit on open file descriptors (default often 1024). Sockets are FDs, so high-connection servers (proxies, brokers, DB pools) hit `EMFILE` ("too many open files") and stop accepting connections while CPU/memory look fine. Raise it via systemd `LimitNOFILE` or pod limits, and monitor open FDs against the ceiling.

**Q12: How does the page cache affect "memory usage" readings?**
Linux uses free RAM as page cache for file I/O; tools that report "used" memory including cache look alarming but the cache is reclaimable. `free -m` distinguishes `used` from `buff/cache` and `available`. In cgroups, page cache counts toward `memory.current`, which is why a file-heavy container can approach `memory.max` from cache alone and trigger reclaim.

---

## 13. Best Practices

- **Run as non-root**, drop all capabilities, add back the minimum (`securityContext: runAsNonRoot: true`, `capabilities.drop: ["ALL"]`).
- **Use exec-form `CMD`/`ENTRYPOINT`** so the app is PID 1 and receives SIGTERM; add `tini` if it spawns children.
- **Always trap SIGTERM** in long-running services to drain connections within the grace period.
- **Set memory limits = requests** for predictable QoS (Guaranteed class); be cautious with CPU limits on latency-sensitive paths.
- **Raise `LimitNOFILE`** for connection-heavy services and alert on FD usage approaching the ceiling.
- **Read `/proc` and `/sys/fs/cgroup` first** during resource incidents — they are ground truth, not the application logs.
- **Disable swap on Kubernetes nodes** (or use the swap feature deliberately) for predictable OOM behavior.

---

## 14. Case Study

### Scenario: Java service randomly killed in Kubernetes; node has free RAM

A payments service running OpenJDK in Kubernetes is restarted every few hours with exit code 137 (128 + SIGKILL=9). The on-call engineer sees no application errors and `kubectl top node` shows 40% memory free.

```
+-------------------------------------------------------------+
| Pod: payments  resources.limits.memory: 1Gi                 |
|   JVM started with NO -Xmx flag                             |
|   -> JVM ergonomics sees the *node's* memory (32Gi)         |
|   -> sets max heap ~= 8Gi                                   |
|   -> heap grows past cgroup memory.max (1Gi)                |
|   -> cgroup OOM kill -> exit 137                            |
+-------------------------------------------------------------+
```

**Diagnosis path:**

```bash
kubectl describe pod payments | grep -A2 'Last State'
#  Last State: Terminated  Reason: OOMKilled  Exit Code: 137
kubectl exec payments -- cat /sys/fs/cgroup/memory.events
#  oom_kill 4
dmesg -T | grep -i 'cgroup out of memory'
#  Memory cgroup out of memory: Killed process ... (java)
```

```dockerfile
# BROKEN: JVM ignores the cgroup limit, sizes the heap from host RAM.
CMD ["java", "-jar", "app.jar"]
```

```dockerfile
# FIX: modern JDK (11+) is cgroup-aware; let it read the limit and cap the heap fraction.
CMD ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
# Result: with memory.limit=1Gi, max heap ~= 768Mi, leaving headroom for
# metaspace/threads/native; no more OOMKills.
```

**Outcome metrics:** OOMKills dropped from ~6/day to 0; P99 latency stabilized (no restart-induced cold starts); the fix was a one-line JVM flag once the cgroup mechanism was understood.

**Discussion questions:**
1. Why does `MaxRAMPercentage` need to leave 25% headroom — what else lives in the container's memory besides heap?
2. How would you alert on this *before* the first OOMKill? (Hint: `container_memory_working_set_bytes / container_spec_memory_limit_bytes`.)
3. If the app were Node.js or Python instead of Java, what is the equivalent "sees host memory" trap?

---

**Cross-references:** [containers_and_docker](../containers_and_docker/) (how images become these processes), [kubernetes_scheduling_and_autoscaling](../kubernetes_scheduling_and_autoscaling/) (requests/limits/QoS), [kubernetes_security](../kubernetes_security/) (capabilities, non-root), [`../../backend/async_and_concurrency_patterns`](../../backend/async_and_concurrency_patterns/) (thread/connection sizing).
