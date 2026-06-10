# Computer Architecture and Memory Hierarchy

> The CPU is a race car with a tiny tank — the memory hierarchy exists because DRAM is 100x slower than the engine that consumes it.

---

## 1. Concept Overview

Modern CPUs execute billions of instructions per second, but main memory (DRAM) delivers data in ~100 nanoseconds — roughly 200–300 CPU cycles of waiting on a 3 GHz processor. The **memory hierarchy** bridges this gap by placing small, fast storage (registers, caches) close to the CPU and large, slow storage (DRAM, SSD, HDD) further away.

**Computer architecture** refers to the design of the CPU itself: how instructions move through execution stages (the **pipeline**), how the processor predicts which instructions will be needed next (**branch prediction**), and how it manages hazards that would otherwise stall forward progress.

Together, these two topics — CPU micro-architecture and the memory hierarchy — determine the actual wall-clock performance of software. A theoretically O(n) algorithm can be 10× slower than a competitor if it ignores cache locality. A tight loop can stall for 15 cycles on a single mispredicted branch.

Understanding these fundamentals is not just academic: it explains why columnar databases beat row stores for analytics, why false sharing kills multi-threaded throughput, why sorting data before a branch-heavy loop speeds it up, and why NUMA-aware thread pinning matters on two-socket servers.

---

## 2. Intuition

> **One-line analogy**: The memory hierarchy is a chef's workspace — registers are the cutting board (instant access), L1/L2/L3 caches are the counter, fridge, and pantry (seconds to minutes to fetch), DRAM is the grocery store (10-minute round trip), and SSD/HDD is the warehouse across town (hours).

**Mental model**: Every time the CPU needs a value, it looks first in the register file (0 cycles), then L1 cache (~4 cycles), then L2 (~30 cycles), then L3 (~130 cycles), then DRAM (~300 cycles). A cache **miss** at each level wastes hundreds of cycles staring at a stalled pipeline. The pipeline exists to hide latency by executing other instructions while a load is in-flight — but it only works when those instructions are independent.

**Why it matters**: The gap between CPU speed and memory speed has widened every decade (Hennessy and Patterson call this the "memory wall"). A single cache miss to DRAM wastes ~300 CPU cycles — the same CPU that can execute 4+ instructions per cycle. Software that respects the memory hierarchy (sequential access, data-structure layout, avoiding false sharing) consistently outperforms software that ignores it, sometimes by 10–100×.

**Key insight**: Cache performance is about **locality** — spatial (access nearby addresses together) and temporal (reuse the same address soon). The CPU loads data in 64-byte **cache lines**, not individual bytes. Reading one byte of a cache line pulls all 64 bytes into cache. Software that reads data sequentially exploits spatial locality; software that reads the same data repeatedly exploits temporal locality.

---

## 3. Core Principles

**Principle 1 — The memory hierarchy is a latency/capacity tradeoff.** Faster storage is more expensive per byte and harder to manufacture at scale. The hierarchy is: registers (< 1 ns, < 1 KB) → L1 cache (1–4 ns, 32–64 KB per core) → L2 cache (~10 ns, 256 KB–1 MB per core) → L3 cache (~40 ns, 4–32 MB shared) → DRAM (~100 ns, 8–256 GB) → NVMe SSD (~100 µs) → HDD (~10 ms) → tape/cold storage (seconds).

**Principle 2 — Cache lines are the unit of transfer.** A cache line is 64 bytes on all modern x86-64 and ARM processors. When the CPU reads a single byte from DRAM, it fetches the entire 64-byte aligned block containing that byte. This makes sequential memory access far cheaper than random access.

**Principle 3 — Locality drives performance.** Spatial locality: if you access address A, you will likely access A+1, A+2, ... soon — load the whole cache line. Temporal locality: if you access A now, you will likely access A again soon — keep it in cache.

**Principle 4 — The CPU pipeline hides latency by overlapping work.** A modern out-of-order processor has dozens of in-flight instructions simultaneously. Stalls happen when an instruction depends on a result not yet available (data hazard), or when the processor cannot determine which instruction to fetch next (control hazard on a branch).

**Principle 5 — Branch prediction is ~95% accurate on modern hardware.** When a branch is mispredicted, the pipeline must be flushed and refilled — a penalty of ~15 cycles on modern CPUs. The CPU uses a Branch Target Buffer (BTB) and pattern-history tables to predict outcomes.

**Principle 6 — Cache coherence is required in multi-core systems.** Each core has its own L1/L2 cache. If two cores cache the same memory address and one modifies it, the caches would diverge. The **MESI protocol** (Modified, Exclusive, Shared, Invalid) maintains coherence: a core that wants to write a line must first invalidate all other copies, triggering expensive cache-to-cache traffic.

**Principle 7 — NUMA topology matters on multi-socket servers.** A two-socket x86-64 server has two independent memory controllers. A CPU on socket 0 accessing memory attached to socket 1 pays ~200–300 ns instead of ~100 ns. OS schedulers and application code can pin threads and memory to the same NUMA node to avoid this penalty.

---

## 4. Types / Architectures / Strategies

### CPU Pipeline Stages

A classic 4-stage pipeline:

| Stage | What Happens |
|-------|-------------|
| Fetch (IF) | Load instruction bytes from I-cache (L1 instruction cache) into the instruction register |
| Decode (ID) | Decode opcode, identify operands, read registers from register file |
| Execute (EX) | Perform ALU operation, address calculation, or initiate memory load/store |
| Writeback (WB) | Write result back to destination register |

Modern out-of-order processors have 15–25+ pipeline stages, multiple execution units, and can retire 4–6 instructions per cycle.

### Pipeline Hazards

| Hazard Type | Cause | Resolution |
|-------------|-------|-----------|
| Data hazard | Instruction depends on result of a prior instruction still in-flight | Register forwarding (bypass unit) or pipeline stall (bubble) |
| Control hazard | Branch — next PC unknown until branch resolves | Branch prediction; flush on misprediction (~15-cycle penalty) |
| Structural hazard | Two instructions need the same hardware resource simultaneously | Stall one instruction; modern CPUs mostly avoid by duplicating units |

### Branch Prediction Strategies

| Strategy | Description | Accuracy |
|----------|-------------|----------|
| Static (always-not-taken) | Predict all branches not taken | ~50–60% |
| 1-bit saturating counter | Remember last outcome | ~85% |
| 2-bit saturating counter | Hysteresis — needs 2 consecutive wrong before flipping | ~93% |
| Tournament predictor | Combine local and global history predictors | ~95%+ |
| TAGE predictor (modern Intel/AMD) | Tagged geometric history length tables | ~97–99% in benchmarks |

Branch Target Buffer (BTB): a cache of recent branch addresses → predicted target address. Allows the CPU to speculatively fetch from the predicted target before the branch instruction is even decoded.

### Cache Write Policies

| Policy | Write hit behavior | Write miss behavior | Use case |
|--------|--------------------|--------------------|---------| 
| Write-through | Write to cache AND main memory simultaneously | Allocate or no-allocate | Simple; always-consistent but high bandwidth |
| Write-back | Write only to cache; mark line dirty | Allocate on write miss | Lower bandwidth; must flush dirty lines on eviction |
| Write-combining | Buffer multiple writes; flush as burst | — | GPU framebuffers, PCIe DMA regions |

### Cache Replacement Policies

| Policy | Description | Notes |
|--------|-------------|-------|
| LRU (Least Recently Used) | Evict the line accessed longest ago | Too expensive to implement exactly at scale |
| Pseudo-LRU | Tree-based approximation | Used in most L1/L2 caches |
| RRIP (Re-Reference Interval Prediction) | Default Intel L3 replacement | Handles scans better than LRU |

### MESI Cache Coherence States

| State | Meaning | Can read without bus traffic? | Can write without bus traffic? |
|-------|---------|------------------------------|-------------------------------|
| Modified (M) | Only copy; dirty (differs from DRAM) | Yes | Yes |
| Exclusive (E) | Only copy; clean (matches DRAM) | Yes | Yes (transitions to M) |
| Shared (S) | Multiple cores hold clean copies | Yes | No — must invalidate others first |
| Invalid (I) | Line not present or stale | No — must fetch | No — must fetch and invalidate |

### NUMA Topologies

| Topology | Description | Remote memory penalty |
|----------|-------------|----------------------|
| UMA (single socket) | All cores share one memory controller | None |
| NUMA 2-socket | Two sockets, each with local DRAM, QPI/UPI interconnect | ~200–300 ns vs ~100 ns local |
| NUMA 4-socket | Four sockets; some hops cross 2 interconnects | ~300–500 ns for cross-hop |
| AMD EPYC (within-socket NUMA) | Multiple CCDs per socket, each CCD has local L3 | ~10–50 ns intra-socket NUMA hop |

---

## 5. Architecture Diagrams

### Memory Hierarchy with Latency and Size

```
+---------------------------+
|     CPU Core              |
|  +---------------------+  |
|  |   Register File     |  |  < 1 ns   ~1 KB (integer + FP regs)
|  +---------------------+  |
|  |     L1 Cache        |  |  1-4 ns   32-64 KB per core (split I$/D$)
|  +---------------------+  |
|  |     L2 Cache        |  |  ~10 ns   256 KB - 1 MB per core
|  +---------------------+  |
+---------------------------+
         |
+---------------------------+
|     L3 Cache (shared)     |  ~40 ns   4-32 MB (all cores share)
+---------------------------+
         |
+---------------------------+
|     Main Memory (DRAM)    |  ~100 ns  8 GB - 4 TB
+---------------------------+
         |
+---------------------------+
|     NVMe SSD              |  ~100 µs  500 GB - 16 TB
+---------------------------+
         |
+---------------------------+
|     HDD / Network Storage |  ~10 ms   1 TB - PB scale
+---------------------------+
```

### CPU Pipeline (4-stage simplified)

```
Clock cycle:   1     2     3     4     5     6     7
               |     |     |     |     |     |     |
Instruction 1: [IF]--[ID]--[EX]--[WB]
Instruction 2:       [IF]--[ID]--[EX]--[WB]
Instruction 3:             [IF]--[ID]--[EX]--[WB]
Instruction 4:                   [IF]--[ID]--[EX]--[WB]

Data hazard (RAW — Read After Write):
  ADD R1, R2, R3     (R1 written in EX at cycle 3)
  SUB R4, R1, R5     (R1 read in ID at cycle 3 — TOO EARLY without forwarding)

  Without forwarding:  stall 2 cycles (bubble inserted)
  With forwarding:     result bypassed from EX output directly to next EX input
```

### Cache Line and False Sharing

```
Cache line (64 bytes):
+--------+--------+--------+--------+--------+--------+--------+--------+
| byte 0 | byte 1 | byte 2 | ...    | byte 7 | ...    | ...    |byte 63 |
+--------+--------+--------+--------+--------+--------+--------+--------+

Two threads, two counters packed in same 64-byte cache line:
   Thread 0 writes counter_a (bytes 0-7)
   Thread 1 writes counter_b (bytes 8-15)

   Core 0 cache line:  [M] counter_a | counter_b | padding...
   Core 1 cache line:  [I] <invalid — must re-fetch from Core 0>

   Every write by Thread 0 invalidates Core 1's copy, and vice versa.
   This is FALSE SHARING: two logically independent variables share a cache line.

Solution — pad to 64 bytes per counter:
   counter_a: bytes 0-7,   padding: bytes 8-63    (fills one cache line)
   counter_b: bytes 64-71, padding: bytes 72-127  (fills next cache line)
   Now writes to counter_a never touch counter_b's cache line.
```

### Two-Socket NUMA Topology

```
+---------------------+       QPI/UPI (~200-300 ns)      +---------------------+
|   Socket 0          |<=================================>|   Socket 1          |
|                     |                                   |                     |
|  Core 0  Core 1     |                                   |  Core 4  Core 5     |
|    |       |        |                                   |    |       |        |
|  L1      L1         |                                   |  L1      L1         |
|    \     /          |                                   |    \     /          |
|     L2              |                                   |     L2              |
|      |              |                                   |      |              |
|   L3 Cache          |                                   |   L3 Cache          |
|      |              |                                   |      |              |
|  Memory Controller  |                                   |  Memory Controller  |
|      |              |                                   |      |              |
|   DRAM (~100 ns)    |                                   |   DRAM (~100 ns)    |
+---------------------+                                   +---------------------+

Socket 0 core accessing Socket 1 DRAM: ~200-300 ns (2-3x penalty)
```

---

## 6. How It Works — Detailed Mechanics

### Demonstrating Cache Locality Impact

```python
from __future__ import annotations

import time
import ctypes
from typing import Callable

ROWS = 4096
COLS = 4096
ITERATIONS = 10


def allocate_matrix(rows: int, cols: int) -> list[list[int]]:
    return [[i * cols + j for j in range(cols)] for i in range(rows)]


def row_major_sum(matrix: list[list[int]]) -> int:
    total = 0
    for row in matrix:
        for val in row:
            total += val
    return total


def column_major_sum(matrix: list[list[int]]) -> int:
    total = 0
    for col in range(COLS):
        for row in range(ROWS):
            total += matrix[row][col]
    return total


def benchmark(fn: Callable[[], int], label: str, runs: int = 3) -> float:
    times = []
    for _ in range(runs):
        start = time.perf_counter()
        fn()
        times.append(time.perf_counter() - start)
    avg = sum(times) / len(times)
    print(f"{label}: {avg * 1000:.1f} ms")
    return avg


if __name__ == "__main__":
    matrix = allocate_matrix(ROWS, COLS)

    row_time = benchmark(lambda: row_major_sum(matrix), "Row-major (cache-friendly)")
    col_time = benchmark(lambda: column_major_sum(matrix), "Column-major (cache-unfriendly)")

    print(f"Slowdown ratio: {col_time / row_time:.1f}x")
```

Row-major access: each successive read hits a value in the same 64-byte cache line — 8 consecutive `int64` values fit in one cache line, so every 8th access is a cache miss. Column-major: each successive read jumps 4096 columns × 8 bytes = 32 KB forward in memory, almost certainly a cache miss each time. On a modern laptop with a 4 MB L3 cache and a 4096×4096 int64 matrix (128 MB), column-major is typically 5–10× slower.

### Simulating Branch Prediction Impact

```python
from __future__ import annotations

import random
import time


def sum_with_branch(data: list[int], threshold: int) -> int:
    total = 0
    for val in data:
        if val >= threshold:
            total += val
    return total


def benchmark_branch_prediction(size: int = 1_000_000) -> None:
    threshold = 128

    sorted_data = sorted(random.randint(0, 255) for _ in range(size))
    unsorted_data = list(random.randint(0, 255) for _ in range(size))

    runs = 5

    sorted_times = []
    for _ in range(runs):
        t = time.perf_counter()
        sum_with_branch(sorted_data, threshold)
        sorted_times.append(time.perf_counter() - t)

    unsorted_times = []
    for _ in range(runs):
        t = time.perf_counter()
        sum_with_branch(unsorted_data, threshold)
        unsorted_times.append(time.perf_counter() - t)

    s_avg = sum(sorted_times) / runs * 1000
    u_avg = sum(unsorted_times) / runs * 1000

    print(f"Sorted data:   {s_avg:.1f} ms  (branch predictor learns the pattern)")
    print(f"Unsorted data: {u_avg:.1f} ms  (branch predictor mispredicts ~50%)")
    print(f"Speedup from sorting: {u_avg / s_avg:.1f}x")


if __name__ == "__main__":
    benchmark_branch_prediction()
```

With sorted data the branch predictor sees a long run of "not taken" (values < 128) followed by a long run of "taken" (values >= 128). It learns the pattern, achieving near-zero mispredictions. With random data it mispredicts ~50% of branches. Each misprediction flushes the pipeline and wastes ~15 cycles. At 1 million branches, 500 K × 15 cycles at 3 GHz = 2.5 ms of pure penalty. In Python the GIL and interpreter overhead swamp this; in compiled C/C++ the difference can be 3–6×.

### Cache Line False Sharing (Python simulation)

```python
from __future__ import annotations

import threading
import time
from dataclasses import dataclass, field


ITERATIONS = 10_000_000
CACHE_LINE = 64  # bytes


@dataclass
class SharedCounters:
    """Two counters packed tightly — they likely share a cache line."""
    counter_a: int = 0
    counter_b: int = 0


@dataclass
class PaddedCounters:
    """Two counters separated by padding to occupy distinct cache lines."""
    counter_a: int = 0
    _pad_a: bytes = field(default_factory=lambda: bytes(CACHE_LINE - 8))
    counter_b: int = 0
    _pad_b: bytes = field(default_factory=lambda: bytes(CACHE_LINE - 8))


def increment_a_shared(counters: SharedCounters) -> None:
    for _ in range(ITERATIONS):
        counters.counter_a += 1


def increment_b_shared(counters: SharedCounters) -> None:
    for _ in range(ITERATIONS):
        counters.counter_b += 1


def benchmark_false_sharing() -> None:
    shared = SharedCounters()
    t1 = threading.Thread(target=increment_a_shared, args=(shared,))
    t2 = threading.Thread(target=increment_b_shared, args=(shared,))

    start = time.perf_counter()
    t1.start()
    t2.start()
    t1.join()
    t2.join()
    shared_time = time.perf_counter() - start

    print(f"Shared cache line (false sharing): {shared_time:.3f} s")
    print(f"  Note: Python GIL serializes threads — use ctypes or C extension")
    print(f"  to observe hardware-level false sharing. In C, expect 5-10x slowdown.")
    print(f"  In Java, use @jdk.internal.vm.annotation.Contended.")


if __name__ == "__main__":
    benchmark_false_sharing()
```

Note: Python's GIL prevents true parallel execution of threads, so the false sharing penalty is not visible at the Python level. The real penalty appears in compiled languages (C, C++, Java, Rust) where two cores genuinely write to adjacent bytes simultaneously. In Java, `@jdk.internal.vm.annotation.Contended` (public as `sun.misc.Contended` before JDK 9) adds 128 bytes of padding around a field or class, ensuring it occupies its own cache line. See `../../java/concurrency/` for the JVM-specific treatment.

### MESI Protocol Walkthrough (conceptual)

```python
from __future__ import annotations

from dataclasses import dataclass
from enum import Enum


class MESIState(Enum):
    MODIFIED = "M"
    EXCLUSIVE = "E"
    SHARED = "S"
    INVALID = "I"


@dataclass
class CacheLine:
    address: int
    state: MESIState
    data: int = 0

    def can_read_locally(self) -> bool:
        return self.state != MESIState.INVALID

    def can_write_locally(self) -> bool:
        return self.state in (MESIState.MODIFIED, MESIState.EXCLUSIVE)

    def on_remote_read(self) -> None:
        """Another core snoops a read. If we hold Modified, we must write back."""
        if self.state == MESIState.MODIFIED:
            print(f"  Core holding M: writing back line {self.address:#x} to DRAM")
            self.state = MESIState.SHARED
        elif self.state == MESIState.EXCLUSIVE:
            self.state = MESIState.SHARED

    def on_remote_write(self) -> None:
        """Another core requests write ownership — we must invalidate."""
        print(f"  Invalidating line {self.address:#x} (state was {self.state.value})")
        self.state = MESIState.INVALID


def mesi_scenario() -> None:
    line_core0 = CacheLine(address=0x1000, state=MESIState.EXCLUSIVE, data=42)
    print(f"Core 0 has line 0x1000 in {line_core0.state.value} state")

    print("\nCore 1 reads line 0x1000:")
    line_core0.on_remote_read()
    line_core1 = CacheLine(address=0x1000, state=MESIState.SHARED, data=42)
    print(f"  Core 0: {line_core0.state.value}, Core 1: {line_core1.state.value}")

    print("\nCore 1 writes to line 0x1000:")
    line_core0.on_remote_write()
    line_core1.state = MESIState.MODIFIED
    line_core1.data = 99
    print(f"  Core 0: {line_core0.state.value}, Core 1: {line_core1.state.value}")

    print("\nCore 0 attempts to read line 0x1000:")
    if not line_core0.can_read_locally():
        print("  Core 0 cache miss — must fetch from Core 1 (cache-to-cache transfer)")
        line_core1.on_remote_read()
        line_core0 = CacheLine(address=0x1000, state=MESIState.SHARED, data=99)
        print(f"  Core 0: {line_core0.state.value}, Core 1: {line_core1.state.value}")


if __name__ == "__main__":
    mesi_scenario()
```

### NUMA-Aware Memory Allocation (Linux/Python)

```python
from __future__ import annotations

import subprocess
import sys


def get_numa_topology() -> None:
    """Display NUMA topology — requires numactl on Linux."""
    try:
        result = subprocess.run(
            ["numactl", "--hardware"],
            capture_output=True,
            text=True,
            timeout=5
        )
        print(result.stdout)
    except FileNotFoundError:
        print("numactl not found — run on Linux: sudo apt-get install numactl")
    except subprocess.TimeoutExpired:
        print("numactl timed out")


def numa_bind_example() -> None:
    """
    To pin a process to NUMA node 0 (uses only local memory and CPU):
      numactl --cpunodebind=0 --membind=0 python my_app.py

    To check which NUMA node a running process uses:
      numastat -p <pid>

    Python-level: use os.sched_setaffinity() to pin to specific CPU cores.
    For memory locality in NumPy/PyTorch:
      - Allocate arrays on the process's NUMA node
      - Use torch.Tensor.pin_memory() for pinned (page-locked) CPU memory
        that enables faster PCIe DMA to GPU
    """
    print(__doc__)


if __name__ == "__main__":
    get_numa_topology()
```

---

## 7. Real-World Examples

### Columnar vs Row Storage (Cache Locality in Databases)

PostgreSQL and MySQL store rows contiguously on a heap page (row-oriented). An analytics query scanning a single column (e.g., `SELECT SUM(revenue) FROM orders`) must load every column of every row into cache even though it only needs one field. ClickHouse and Apache Parquet store each column contiguously. The query reads only the relevant column's bytes, achieving far better cache utilization. For a table with 100 columns and 10 million rows, columnar storage reads ~1/100th of the data — the rest stays on disk.

### Linux Kernel — Cache-Line Padding with `____cacheline_aligned_in_smp`

The Linux kernel's `spinlock_t` and per-CPU variables are explicitly padded to 64 bytes using `____cacheline_aligned_in_smp`. Without this, hot lock structures shared between interrupt handlers and process context would cause cache-line ping-pong across cores. The `struct task_struct` (PCB) has cache-line-aligned sections for scheduler fields, separating read-mostly metadata from frequently-written scheduling state.

### Java — `@Contended` in `LongAdder`

Java's `java.util.concurrent.atomic.LongAdder` (introduced in Java 8) uses cell striping: each thread increments a separate `Cell` object. The `Cell` class is annotated with `@jdk.internal.vm.annotation.Contended`, which the JVM expands to 128 bytes of padding (accounting for a 64-byte cache line on each side). Without this, all cells would fit in a few cache lines, and parallel increments would trigger MESI invalidation storms. With padding, each cell occupies its own cache line and threads never share cache lines. `LongAdder.sum()` aggregates all cells at read time. See `../../java/concurrency/` for the full treatment.

### NUMA on Two-Socket Servers (PostgreSQL, Elasticsearch)

A 2-socket server running PostgreSQL with `shared_buffers = 64GB` allocates the buffer pool across both NUMA nodes. A query serviced by a CPU on socket 0 that needs a buffer page allocated on socket 1's DRAM incurs ~200 ns instead of ~100 ns — a 2× penalty on every buffer pool access. Production deployments of Elasticsearch and PostgreSQL on NUMA systems commonly use `numactl --interleave=all` to spread memory round-robin across nodes, trading the worst-case remote penalty for a predictable average (~150 ns), or use `--membind=0 --cpunodebind=0` to run each instance entirely on one NUMA node.

### Branch Prediction — The Famous Sorting Example

In 2012, a Stack Overflow answer by Mysticial became one of the most-viewed programming answers ever. It demonstrated that a loop processing a sorted array ran 6× faster than the same loop on unsorted data, because the branch predictor in the sorted case learned the pattern (all values < 128, then all >= 128) and achieved near-zero mispredictions. This popularized the concept among working engineers and is now a standard interview illustration.

### Hardware Prefetcher

Modern CPUs include a **hardware prefetcher** that monitors access patterns and issues prefetch requests to L3/DRAM before the CPU explicitly requests the data. The prefetcher works best with sequential (strided) access patterns. It fails on pointer-chasing patterns (linked lists, tree traversals) where the next address is not known until the current load completes. This is why array-based data structures (arrays, vectors, flat hash maps) consistently outperform pointer-based structures (linked lists, trees with pointer children) even when asymptotic complexity is equal.

---

## 8. Tradeoffs

### Cache Write Policies

| Dimension | Write-Through | Write-Back |
|-----------|--------------|------------|
| Consistency | Always consistent with DRAM | Dirty data in cache; DRAM stale until eviction |
| Write bandwidth | High (every write goes to DRAM) | Low (writes batched; only evictions write DRAM) |
| Read after write | No stale reads | No stale reads (reads hit cache) |
| Power loss risk | Safe — DRAM always current | Dirty data lost if power fails before flush |
| Common use | L1 write-through to L2 | L2/L3 write-back to DRAM |

### False Sharing vs True Sharing

| Dimension | True Sharing | False Sharing |
|-----------|-------------|--------------|
| Definition | Two threads access the same variable | Two threads access different variables on the same cache line |
| Correct fix | Synchronization (lock, atomic) | Padding / alignment to separate cache lines |
| Symptom | Data race / incorrect results | Correct results but poor scalability |
| Detection | Sanitizers (TSan), code review | Hardware PMU events: `perf stat -e cache-misses`; Intel VTune; Linux `perf c2c` |
| Java solution | `synchronized`, `volatile`, `Atomic*` | `@Contended` annotation |

### NUMA Strategies

| Strategy | Latency | Bandwidth | Complexity | Use case |
|----------|---------|-----------|-----------|---------|
| Bind to single node | Best local (~100 ns) | Limited to single node | Low (numactl --membind) | Small apps, microservices |
| Interleave across nodes | Average (~150 ns) | Full bandwidth of all nodes | Low (numactl --interleave) | Large in-memory databases |
| First-touch policy (OS default) | Varies (depends on which core first touches) | Full | None (OS handles) | General purpose; can cause imbalance |
| Manual NUMA-aware allocator | Best achievable | Full | High (custom allocator) | HPC, DPDK, NUMA-aware caches |

### Sequential vs Random Access

| Access Pattern | L1 hit rate | Cache misses per 1M ops | Throughput |
|---------------|-------------|------------------------|-----------|
| Sequential (row-major) | ~99% | ~1,000 | ~10 GB/s effective |
| Strided (stride = cache line) | ~99% | ~1,000 | ~10 GB/s effective |
| Strided (stride = page = 4 KB) | ~60% | ~400,000 | ~500 MB/s effective |
| Random (linked list traversal) | ~5% | ~950,000 | ~100–200 MB/s effective |

---

## 9. When to Use / When NOT to Use

### When Cache-Friendly Design Matters Most

- **Analytics and scan-heavy queries**: choosing columnar storage or column-first array layout gives 5–20× improvement for wide tables.
- **Tight numerical loops** (ML inference, physics simulation, cryptography): struct-of-arrays (SoA) layout instead of array-of-structs (AoS) improves SIMD vectorization and cache hit rate.
- **High-throughput multi-threaded counters**: use per-thread/per-core counters (e.g., `LongAdder`) padded to cache-line boundaries to avoid false sharing.
- **Lock-free data structures**: any shared mutable state must be on its own cache line to avoid triggering MESI invalidations on unrelated state.
- **NUMA-aware services**: databases, message brokers, and key-value stores on multi-socket servers should bind threads and memory to the same NUMA node, or explicitly interleave.

### When It is NOT Worth the Complexity

- **Rarely-executed code paths** (startup, configuration loading, error handling): cache optimization has no measurable impact.
- **I/O-bound code**: if the bottleneck is a network call (100 µs–10 ms) or disk read (100 µs–10 ms), shaving cache misses (10–100 ns) is irrelevant.
- **Low-concurrency services**: false sharing only matters when multiple threads run simultaneously on different cores. A single-threaded service cannot suffer false sharing.
- **Interpreted languages at the prototype stage**: Python's interpreter overhead dwarfs cache effects; optimize in Python only after profiling confirms the bottleneck is a tight loop that will be compiled (e.g., NumPy, Cython, PyPy).
- **Premature micro-optimization**: profile first with `perf`, VTune, or async-profiler before restructuring data layouts. The most impactful optimization is almost always algorithmic (O(n log n) vs O(n²)), not cache-level.

---

## 10. Common Pitfalls

### Pitfall 1 — False Sharing From a Naive Shared Counter Array

A common pattern in concurrent aggregation: allocate an array of per-thread counters to avoid locks, then sum them at the end. If each counter is just an integer, all eight counters for eight threads fit in a single 64-byte cache line. Every thread's write invalidates all other threads' cache-line copies.

**BROKEN** — all 8 counters share one or two cache lines:

```python
from __future__ import annotations

import threading
import time

NUM_THREADS = 8
ITERS = 5_000_000

def run_broken() -> float:
    counters = [0] * NUM_THREADS  # 8 ints packed together

    def increment(idx: int) -> None:
        for _ in range(ITERS):
            counters[idx] += 1  # writes to shared cache line

    threads = [threading.Thread(target=increment, args=(i,)) for i in range(NUM_THREADS)]
    start = time.perf_counter()
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    return time.perf_counter() - start
```

In CPython, the GIL masks the hardware-level effect. In C/C++/Rust, this pattern causes a 5–10× throughput loss due to MESI invalidation between cores.

**FIX** — pad each counter to its own cache line:

```python
from __future__ import annotations

import ctypes
import threading
import time

NUM_THREADS = 8
ITERS = 5_000_000
CACHE_LINE = 64

class PaddedCounter(ctypes.Structure):
    _fields_ = [
        ("value", ctypes.c_long),
        ("_pad", ctypes.c_byte * (CACHE_LINE - ctypes.sizeof(ctypes.c_long)))
    ]

def run_fixed() -> float:
    counters = (PaddedCounter * NUM_THREADS)()

    def increment(idx: int) -> None:
        for _ in range(ITERS):
            counters[idx].value += 1  # each counter on its own cache line

    threads = [threading.Thread(target=increment, args=(i,)) for i in range(NUM_THREADS)]
    start = time.perf_counter()
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    return time.perf_counter() - start
```

Each `PaddedCounter` is exactly 64 bytes. In C or Rust with true parallel threads, the padded version achieves near-linear scaling with core count while the packed version saturates the coherence interconnect.

### Pitfall 2 — Column-Major Traversal of a Row-Major Matrix

Iterating a 2D array (or NumPy ndarray) in column-major order when the underlying storage is row-major causes a cache miss on almost every element access.

**BROKEN** — column-major traversal of a row-major array (C-order NumPy):

```python
from __future__ import annotations

import numpy as np
import time

ROWS, COLS = 4096, 4096

def broken_column_sum(matrix: np.ndarray) -> np.intp:
    total = np.intp(0)
    for col in range(COLS):
        for row in range(ROWS):
            total += matrix[row, col]  # jumps COLS * 8 bytes in memory each step
    return total
```

Each `matrix[row, col]` access moves forward by `COLS * sizeof(int64) = 32 KB` in physical memory — virtually guaranteed cache miss at every step.

**FIX** — row-major traversal, or use NumPy's vectorized sum which internally handles this:

```python
from __future__ import annotations

import numpy as np
import time

ROWS, COLS = 4096, 4096

def fixed_row_sum(matrix: np.ndarray) -> np.intp:
    total = np.intp(0)
    for row in range(ROWS):
        for col in range(COLS):
            total += matrix[row, col]  # sequential access — 8 cache misses per 64-byte cache line
    return total

def vectorized_sum(matrix: np.ndarray) -> np.intp:
    return matrix.sum()  # NumPy uses SIMD + cache-friendly sequential scan internally

def benchmark() -> None:
    rng = np.random.default_rng(42)
    matrix = rng.integers(0, 100, size=(ROWS, COLS), dtype=np.int64)

    t = time.perf_counter()
    broken_column_sum(matrix)
    broken_time = time.perf_counter() - t

    t = time.perf_counter()
    fixed_row_sum(matrix)
    fixed_time = time.perf_counter() - t

    t = time.perf_counter()
    vectorized_sum(matrix)
    vec_time = time.perf_counter() - t

    print(f"Column-major (broken):   {broken_time * 1000:.1f} ms")
    print(f"Row-major (fixed):       {fixed_time * 1000:.1f} ms")
    print(f"Vectorized (best):       {vec_time * 1000:.1f} ms")

if __name__ == "__main__":
    benchmark()
```

If you need to work column-by-column, use Fortran-order storage: `np.array(..., order='F')`. A transposed view `matrix.T` gives a Fortran-order view with no data copy.

### Pitfall 3 — Ignoring NUMA on Multi-Socket Servers

A service starts threads without NUMA awareness on a 2-socket server. The OS schedules some threads on socket 0, some on socket 1. All threads allocate memory via the default first-touch policy — the memory is mapped to whichever socket first touches each page. Cross-socket memory accesses incur 200–300 ns instead of 100 ns.

**BROKEN** — default OS scheduling, no NUMA affinity:

```python
import multiprocessing as mp

def worker(n: int) -> int:
    data = list(range(n))  # allocated on whichever NUMA node OS chose
    return sum(data)

pool = mp.Pool(processes=16)  # OS may place workers across both sockets freely
results = pool.map(worker, [10_000_000] * 16)
```

**FIX** — bind workers to a single NUMA node via numactl or `os.sched_setaffinity`:

```python
import os
import multiprocessing as mp

NUMA_NODE_0_CPUS = list(range(0, 16))   # first 16 logical CPUs on socket 0

def worker_numa_pinned(n: int) -> int:
    os.sched_setaffinity(0, NUMA_NODE_0_CPUS)  # pin this process to socket 0 cores
    data = list(range(n))  # first-touch now happens on socket 0 → local memory
    return sum(data)

pool = mp.Pool(processes=8)  # use only socket 0 capacity
results = pool.map(worker_numa_pinned, [10_000_000] * 8)
```

For production systems, launch the entire process with `numactl --cpunodebind=0 --membind=0 python service.py`, which guarantees both CPU affinity and memory locality without code changes.

---

## 11. Technologies & Tools

| Tool / Technology | Category | What It Does | Key Notes |
|-------------------|----------|-------------|-----------|
| `perf` (Linux) | Performance profiling | Hardware PMU events: `cache-misses`, `cache-references`, `branch-misses`, `instructions` | `perf stat -e cache-misses,branch-misses ./program` |
| `perf c2c` | False sharing detection | Reports cache-line contention between cores | Available in Linux perf >= 4.10; shows hot false-sharing lines |
| Intel VTune Profiler | Micro-architecture analysis | Memory access patterns, NUMA hot spots, pipeline stalls, false sharing | Detailed hardware event breakdown; free for non-commercial |
| AMD uProf | Micro-architecture analysis (AMD) | Same as VTune for AMD Ryzen/EPYC | Includes NUMA topology view |
| `numactl` | NUMA management | Bind processes to NUMA nodes; query topology | `numactl --hardware`; `numactl --cpunodebind=0 --membind=0 <cmd>` |
| `numastat` | NUMA stats | Show per-node memory allocation and hit/miss rates | `numastat -p <pid>` |
| `lstopo` / `hwloc` | Topology visualization | ASCII/graphical view of CPU, cache, NUMA topology | `lstopo` produces cache hierarchy diagram |
| `valgrind --tool=cachegrind` | Cache simulation | Simulate L1/L2 cache; report miss rates per source line | Slower than real execution (~20–100×) |
| `callgrind` + `KCachegrind` | Instruction-level profiling | Cache simulation + call graph | GUI in KCachegrind |
| `taskset` | CPU affinity | Pin a process to specific CPU cores | `taskset -c 0-3 python script.py` |
| `@Contended` (Java) | False sharing prevention | Pads a field/class to a cache-line boundary | `@jdk.internal.vm.annotation.Contended`; requires `-XX:-RestrictContended` |
| `LongAdder` (Java) | High-concurrency counter | Striped counter with `@Contended` padded cells | Preferred over `AtomicLong` under high contention |
| `numpy` order parameter | Data layout | Control row-major (C) vs column-major (F) array layout | `np.array(data, order='F')` for Fortran-order |
| LIKWID | HPC performance tools | Measure hardware counters, bandwidth, NUMA | `likwid-perfctr`; common in HPC environments |

---

## 12. Interview Questions with Answers

**Why does sorting data before a branch-heavy loop speed it up?**
Sorting places all values that satisfy a branch condition contiguously, so the CPU's branch predictor learns the pattern (long run of "not taken" → long run of "taken"). With random data, the predictor mispredicts ~50% of branches; with sorted data, it mispredicts only at the transition point. Each misprediction flushes the pipeline — ~15-cycle penalty on modern CPUs. At millions of iterations, this compounds to a measurable speedup, often 3–6× in C/C++ benchmarks. The canonical example (std::sort before a branch loop) is a standard interview gotcha.

**What is false sharing, and how do you detect and fix it?**
False sharing occurs when two threads on different cores write to different variables that happen to occupy the same 64-byte cache line. Thread A's write invalidates Thread B's cache-line copy (MESI protocol), forcing Thread B to re-fetch the line — even though Thread B's variable never changed. Symptom: correct results but poor multi-threaded scalability. Detected with `perf c2c` (Linux), Intel VTune Memory Access analysis, or by padding structs and observing throughput change. Fixed by aligning each independently-written variable to a cache-line boundary (64 bytes on x86-64): C `alignas(64)`, Java `@Contended`, Rust `#[repr(align(64))]`.

**What is a cache miss penalty, and what are the three types of cache misses?**
A cache miss forces the CPU to stall (or switch to other independent instructions) while fetching data from a lower cache level or DRAM. Penalty: L1 miss → L2 hit: ~6 extra cycles; L2 miss → L3 hit: ~20 extra cycles; L3 miss → DRAM: ~200–300 cycles. The three types: (1) Compulsory (cold) miss — first access to a line that was never in cache; unavoidable. (2) Capacity miss — working set exceeds cache size; reduce working set or improve temporal locality. (3) Conflict miss — two frequently-used addresses map to the same cache set in a set-associative cache; resolve with data padding or changing array sizes.

**Explain the MESI protocol and why it is necessary.**
In a multi-core CPU, each core has its own L1/L2 cache. Without coordination, two cores could hold contradictory values for the same address. MESI is a cache coherence protocol with four states per cache line: Modified (dirty, only copy), Exclusive (clean, only copy), Shared (clean, multiple copies), Invalid (stale/absent). When a core wants to write a Shared line, it broadcasts an invalidation to all other cores; they transition to Invalid. When they next read the line, they get the updated value. MESI ensures cache coherence with minimal bus traffic.

**What is the difference between spatial and temporal locality?**
Spatial locality: if you access address A, you are likely to access addresses near A (A+1, A+2, ...) soon. The CPU exploits this by loading a full 64-byte cache line on each miss. Sequential array traversal achieves near-perfect spatial locality. Temporal locality: if you access address A, you are likely to access A again soon. Loop bodies, counters, and frequently-used objects exhibit temporal locality and stay in L1/L2 cache between accesses.

**What is a pipeline hazard, and how do modern CPUs handle data hazards?**
A pipeline hazard is a situation that prevents the next instruction from executing in the next cycle. Data hazard (RAW — Read After Write): instruction i+1 reads a register that instruction i has not yet written. Solution: register forwarding (bypass) — the output of the Execute stage is wired directly to the input of the next Execute stage, skipping Writeback→Decode. Without forwarding, 1–2 stall cycles are inserted. Modern out-of-order processors also reorder instructions to find independent ones that can execute while a dependent instruction waits.

**What is branch misprediction, and what is its cost?**
Branch misprediction occurs when the CPU's branch predictor guesses the wrong path for a conditional branch. The CPU has been executing instructions on the wrong path speculatively; when the branch resolves, the pipeline is flushed and those instructions are discarded. Cost: ~15 cycles on modern Intel/AMD (varies 10–20 cycles by microarchitecture). Modern predictors (TAGE) achieve ~97–99% accuracy in benchmarks. Misprediction rate spikes with random/unpredictable branches (e.g., a search loop terminating at a random position).

**What is a cache line, and how large is it on modern hardware?**
A cache line is the minimum unit of data transfer between cache levels and between cache and DRAM. On all modern x86-64 (Intel, AMD) and ARM (Cortex-A, Apple M-series) processors, the cache line size is 64 bytes. When you read one byte, the CPU fetches the entire aligned 64-byte block. When you write one byte, the entire line is loaded (write-allocate policy), modified, and marked dirty. Cache-line alignment is why struct padding and `alignas(64)` matter for false sharing.

**Explain NUMA and why it matters for multi-socket servers.**
Non-Uniform Memory Access (NUMA): in a multi-socket server, each CPU socket has its own memory controller and local DRAM. Accessing local memory takes ~100 ns; accessing the other socket's DRAM crosses an inter-socket interconnect (Intel QPI/UPI, AMD Infinity Fabric) and costs ~200–300 ns. A process running threads across both sockets with memory allocated on one socket will incur ~2× memory latency for cross-socket accesses. On 2-socket servers running Elasticsearch, PostgreSQL, or Redis, `numactl --interleave=all` spreads memory round-robin across nodes; for latency-critical workloads, `--membind=0 --cpunodebind=0` keeps everything local.

**Why are linked lists often slower than arrays in practice, even when O(n) in both cases?**
Each node in a linked list is independently heap-allocated at an arbitrary address. Traversal is a pointer-chase: you cannot know the address of node n+1 until you have loaded node n (pointer field). This defeats spatial prefetching — the hardware prefetcher cannot predict the next address. Each node access is a potential L3 miss (~40 ns) or DRAM miss (~100 ns). A sequentially-allocated array allows the prefetcher to stream ahead, keeping data in L1/L2. Traversing 1 million linked-list nodes can be 5–20× slower than traversing an equivalent array, despite identical O(n) complexity.

**What is write-through vs write-back caching, and which does x86-64 use?**
Write-through: every store writes simultaneously to the cache and to the next level of the hierarchy (L2 or DRAM). Simpler to implement and always consistent, but generates high write bandwidth. Write-back: the store writes only to the cache; the line is marked "dirty." The dirty line is written back to the next level only when evicted. Generates far less write traffic. Modern x86-64 processors use write-back for L2 and L3; L1 may use write-through to L2 (implementation-specific). DRAM writes happen only on cache eviction or explicit `clflush`.

**How does the hardware prefetcher work, and what patterns does it handle poorly?**
The hardware prefetcher monitors the stream of cache miss addresses. If it detects a stride pattern (e.g., misses at +64, +128, +192 bytes), it issues speculative DRAM reads ahead of time, filling the cache before the CPU explicitly requests the data. It handles sequential access and fixed-stride access well. It handles poorly: pointer chasing (linked lists, trees) where the next address is unknown until the current load completes; irregular strides; access patterns that switch stride mid-stream. Software prefetch (`__builtin_prefetch` in GCC, `_mm_prefetch` intrinsic) can compensate for pointer-chase patterns in performance-critical code.

**What is the Branch Target Buffer (BTB)?**
The BTB is a CPU cache that stores recent branch instruction addresses mapped to their predicted target addresses. When the CPU fetches a branch instruction, it looks it up in the BTB before even fully decoding the instruction. If found, the CPU immediately starts fetching from the predicted target. The BTB handles both conditional branches (predicting not-taken/taken) and indirect branches (e.g., virtual function calls, function pointers), where the target address is computed at runtime. Spectre variant 2 (branch target injection) exploits the BTB to leak data across process boundaries.

**How does row-major vs column-major storage affect NumPy performance?**
NumPy defaults to C-order (row-major) storage: elements of a row are contiguous in memory. Iterating row-by-row achieves sequential memory access (spatial locality). Column-by-column iteration jumps by `ncols * 8` bytes between accesses — almost certainly a cache miss for large arrays. For column-heavy workloads, use Fortran-order: `np.array(data, order='F')` or `np.asfortranarray(arr)`. For mixed-access patterns, explicit transposition `arr.T` returns a Fortran-order view with zero data copy. NumPy's vectorized operations (`.sum()`, `.mean()`) internally stride optimally and use SIMD regardless of order for simple reductions.

**What are the concrete latency numbers for each level of the memory hierarchy?**
Registers: < 1 ns (0–1 cycle). L1 cache: 1–4 ns (4–12 cycles), 32–64 KB per core. L2 cache: ~10 ns (30–40 cycles), 256 KB–1 MB per core. L3 cache: ~40 ns (100–130 cycles), 4–32 MB shared. DRAM: ~100 ns (~300 cycles at 3 GHz). NVMe SSD: ~100 µs (100,000 ns). SATA SSD: ~500 µs. HDD: ~10 ms (10,000,000 ns). These numbers vary slightly by microarchitecture (Intel Golden Cove, AMD Zen 4, Apple M3) but the ratios are stable: L1 is ~100× faster than DRAM; DRAM is ~1000× faster than HDD.

**What is out-of-order execution, and how does it relate to the memory hierarchy?**
Out-of-order (OoO) execution: the CPU does not execute instructions in strict program order. Instead, a scheduler (reservation station) tracks which instructions have all their operands ready and issues them to execution units as soon as they are ready, regardless of program order. The reorder buffer (ROB) holds results until they can be committed in program order (ensuring precise exceptions). OoO execution hides memory latency: while an L3-miss load is pending (~130 cycles), the CPU finds other independent instructions to execute, keeping execution units busy. Without OoO, a single L3 miss would stall the pipeline for 130 cycles.

---

## 13. Best Practices

**Structure data for access patterns, not logical grouping.** Choose struct-of-arrays (SoA) over array-of-structs (AoS) when hot loops only access a subset of fields. Example: a particle simulation that updates position (x, y, z) every frame should store all x-values contiguously, then all y-values, rather than storing `{x, y, z, mass, charge, ...}` per particle.

**Align independently-written fields to cache-line boundaries.** Any variable written by one thread while other threads may read or write nearby variables should be aligned to 64 bytes. In Python/C: `ctypes.Structure` with explicit padding. In Java: `@Contended`. In Rust: `#[repr(align(64))]`. In C: `alignas(64)`.

**Prefer sequential memory access over random access.** Hash tables, linked lists, and trees have poor spatial locality. When performance is critical and the working set fits in cache, consider sorted arrays with binary search (excellent spatial locality, log n lookups) or flat hash maps (Robin Hood, open addressing) that avoid pointer chasing.

**Minimize working-set size.** A smaller working set fits in a faster cache level. Compress data structures (use `int32` instead of `int64` where range permits; use bitfields). Process data in cache-sized chunks (cache-oblivious algorithms, tiling/blocking for matrix operations).

**Loop tiling (cache blocking) for matrix operations.** Instead of traversing an entire N×N matrix row-by-row (which may evict earlier rows from cache before they are reused in a subsequent pass), tile the matrix into B×B blocks where B × B × element_size fits in L2 cache (~256 KB). Process all operations on a block before moving to the next. BLAS libraries (OpenBLAS, MKL) do this automatically.

**On multi-socket NUMA systems, use numactl.** For latency-critical single-instance services: `numactl --cpunodebind=0 --membind=0`. For throughput services that need all memory: `numactl --interleave=all`. Check NUMA topology with `numactl --hardware` and `lstopo`. Monitor with `numastat`.

**Avoid branch-heavy inner loops on unpredictable data.** Restructure loops to use branchless equivalents where possible. Example: instead of `if x > 0: total += x`, use `total += x * (x > 0)` (branchless; the condition evaluates to 0 or 1). Or sort the data first if multiple passes will be made. Profile with `perf stat -e branch-misses` to quantify misprediction cost before restructuring.

**Profile before optimizing.** Use `perf stat`, `perf record`/`perf report`, Intel VTune, or `cachegrind` to identify actual hotspots. A 10× cache miss reduction on a function that accounts for 1% of runtime yields a 0.1% total speedup — not worth the code complexity.

**Understand your CPU's cache topology before micro-optimizing.** L1/L2 sizes, associativity, and replacement policy vary by CPU. `lstopo`, `lscpu`, or `cpuid` reveal these. What fits in L2 on one machine may spill to L3 on another.

---

## 14. Case Study

### Cache-Line False Sharing: Measuring the True Cost

#### Scenario

A Python web service aggregates request counts per route. The original implementation uses a shared dict protected by a lock. After profiling, the team moves to a lock-free per-thread counter approach: each thread increments its own slot in a pre-allocated array. Initial benchmarks show no improvement over the lock-based approach. The investigation leads to false sharing.

#### Architecture

```
8 worker threads, 8 logical CPUs (4 physical cores × 2 HT, single-socket)

Array layout (BROKEN):
  [cnt_t0][cnt_t1][cnt_t2][cnt_t3][cnt_t4][cnt_t5][cnt_t6][cnt_t7]
   8 bytes  8 bytes  ...   <- all 8 counters fit in one cache line (64 bytes)

Thread 0 writes cnt_t0 → invalidates cache line on ALL cores
Thread 1 writes cnt_t1 → re-fetches same cache line → invalidates on ALL cores
...
Result: 8 threads serialize through the cache coherence protocol

Array layout (FIXED):
  [cnt_t0 + 56 bytes padding][cnt_t1 + 56 bytes padding]...
  Each counter occupies its own 64-byte cache line
  Writes are independent → no inter-core invalidation
```

#### Implementation

```python
from __future__ import annotations

import ctypes
import multiprocessing
import threading
import time
from dataclasses import dataclass

CACHE_LINE_BYTES = 64
ITERATIONS = 20_000_000
NUM_THREADS = 8


class PackedCounterArray(ctypes.Structure):
    """8 counters packed — will likely share cache lines."""
    _fields_ = [("values", ctypes.c_int64 * NUM_THREADS)]


class PaddedCounterEntry(ctypes.Structure):
    """Single counter padded to exactly one cache line."""
    _fields_ = [
        ("value", ctypes.c_int64),
        ("_pad", ctypes.c_byte * (CACHE_LINE_BYTES - ctypes.sizeof(ctypes.c_int64))),
    ]


class PaddedCounterArray(ctypes.Structure):
    """8 counters each on their own cache line."""
    _fields_ = [("entries", PaddedCounterEntry * NUM_THREADS)]


def run_packed() -> float:
    arr = PackedCounterArray()
    barrier = threading.Barrier(NUM_THREADS)

    def worker(tid: int) -> None:
        barrier.wait()
        for _ in range(ITERATIONS):
            arr.values[tid] += 1

    threads = [threading.Thread(target=worker, args=(i,)) for i in range(NUM_THREADS)]
    t0 = time.perf_counter()
    for th in threads:
        th.start()
    for th in threads:
        th.join()
    return time.perf_counter() - t0


def run_padded() -> float:
    arr = PaddedCounterArray()
    barrier = threading.Barrier(NUM_THREADS)

    def worker(tid: int) -> None:
        barrier.wait()
        for _ in range(ITERATIONS):
            arr.entries[tid].value += 1

    threads = [threading.Thread(target=worker, args=(i,)) for i in range(NUM_THREADS)]
    t0 = time.perf_counter()
    for th in threads:
        th.start()
    for th in threads:
        th.join()
    return time.perf_counter() - t0


def verify_layout() -> None:
    packed_size = ctypes.sizeof(PackedCounterArray)
    padded_entry_size = ctypes.sizeof(PaddedCounterEntry)
    padded_total = ctypes.sizeof(PaddedCounterArray)

    print(f"Packed array total size:   {packed_size} bytes")
    print(f"  ({NUM_THREADS} counters × 8 bytes = {NUM_THREADS * 8} bytes)")
    print(f"  All {NUM_THREADS} counters fit in {packed_size // CACHE_LINE_BYTES} cache line(s)")
    print()
    print(f"Padded entry size:         {padded_entry_size} bytes (= 1 cache line)")
    print(f"Padded array total size:   {padded_total} bytes")
    print(f"  Each of {NUM_THREADS} counters occupies its own {CACHE_LINE_BYTES}-byte cache line")


if __name__ == "__main__":
    verify_layout()
    print()

    packed_time = run_packed()
    padded_time = run_padded()

    throughput_packed = (NUM_THREADS * ITERATIONS) / packed_time / 1e6
    throughput_padded = (NUM_THREADS * ITERATIONS) / padded_time / 1e6

    print(f"Packed  (false sharing): {packed_time:.3f} s  |  {throughput_packed:.0f} M ops/s")
    print(f"Padded  (no sharing):    {padded_time:.3f} s  |  {throughput_padded:.0f} M ops/s")
    print(f"Speedup: {packed_time / padded_time:.1f}x")
    print()
    print("Note: CPython GIL serializes Python bytecode execution.")
    print("ctypes C-level writes bypass Python object overhead but GIL still present.")
    print("In C/Rust/Java with true parallelism, expect 5-10x speedup from padding.")
```

#### BROKEN → FIX: Route Counter Service

**BROKEN** — route hit counters in a shared list, packed together:

```python
from __future__ import annotations

import threading

class RouteCounter:
    def __init__(self, num_routes: int) -> None:
        self._counts = [0] * num_routes  # all counts packed in adjacent memory

    def increment(self, route_id: int) -> None:
        self._counts[route_id] += 1  # concurrent writes from different threads
                                      # share cache lines → false sharing

    def get(self, route_id: int) -> int:
        return self._counts[route_id]
```

**FIX** — pad each counter to its own cache line using ctypes:

```python
from __future__ import annotations

import ctypes
import threading

CACHE_LINE = 64

class _PaddedInt(ctypes.Structure):
    _fields_ = [
        ("count", ctypes.c_int64),
        ("_pad", ctypes.c_byte * (CACHE_LINE - 8)),
    ]

class RouteCounter:
    def __init__(self, num_routes: int) -> None:
        self._counts = (_PaddedInt * num_routes)()

    def increment(self, route_id: int) -> None:
        self._counts[route_id].count += 1  # writes to isolated cache lines

    def get(self, route_id: int) -> int:
        return self._counts[route_id].count
```

#### Metrics

| Configuration | 8-thread throughput (C/Rust baseline) | Cache-line invalidations/s | Scalability |
|--------------|---------------------------------------|--------------------------|-------------|
| Packed (false sharing) | ~50 M ops/s | ~7.5 M/s per cross-core write | Worse than single-threaded on 4+ cores |
| Padded (cache-line isolated) | ~400 M ops/s | ~0 cross-core invalidations | Near-linear scaling to core count |
| Speedup | **8×** | | |
| Java `LongAdder` vs `AtomicLong` under 16-thread contention | 12× higher throughput | | |

Numbers from published benchmarks on 8-core Intel Ice Lake (Azul Systems blog, 2022; JMH results for Java). Python GIL prevents observing the full hardware effect; use ctypes, Cython, or Rust extensions to benchmark at the hardware level.

#### Discussion Questions

1. **Your service runs 16 worker threads on a 2-socket NUMA server (8 cores per socket). The RouteCounter is allocated by the main thread at startup. How would you diagnose and fix NUMA-related memory latency on top of the false-sharing fix?**

   Allocate the counter array per NUMA node: 8 counters for socket-0 threads, 8 for socket-1 threads. Use `numactl` to pin threads to their respective sockets. At read time (reporting counters), aggregate across both per-node arrays. Use `perf c2c` to confirm false sharing is resolved, and `numastat` to confirm memory locality.

2. **The production system shows high cache-miss rates despite padding the counters. What else could be causing L3 misses?**

   Candidate causes: (a) the padded counter array itself is cold at startup — compulsory misses on first access; (b) another hot data structure (e.g., the routing table) is cache-unfriendly and polluting L3; (c) the working thread is sharing an L3 with a co-located container process doing I/O, causing capacity evictions; (d) the counter reads (done by a reporter thread summing all counters) are causing cross-core cache traffic if they race with writer threads. Use `perf record -e cache-misses -g` to identify which call sites are generating misses.

3. **Can you apply the same false-sharing principle to a lock-based counter implementation? Where does the cache-line effect appear in the lock itself?**

   Yes. A `threading.Lock()` (which wraps a `pthread_mutex_t`) is a small struct that itself occupies a few bytes. If two locks protecting different data items sit in the same cache line, locking/unlocking one lock invalidates the cache line holding the other lock — even though they protect unrelated state. This is false sharing on the lock objects themselves. Fix: allocate lock objects with 64-byte alignment. In Java, `ReentrantLock` instances can also false-share; the `@Contended` annotation on AQS internals in JDK 9+ addresses this. The `AbstractQueuedSynchronizer` state field is explicitly isolated. See `../../java/concurrency/` for the full treatment of `@Contended` and AQS layout.

---

## See Also

- [processes_threads_and_context_switching](../processes_threads_and_context_switching/) — TLB flush on context switch; OS thread model; per-core stack allocation
- [memory_management_and_virtual_memory](../memory_management_and_virtual_memory/) — TLB, page tables, physical frames, virtual address space layout; how the MMU translates virtual to physical addresses
- [../../java/concurrency](../../java/concurrency/) — `@Contended` annotation, false sharing in Java, memory barriers, `volatile` happens-before, `LongAdder` internals, JMM
