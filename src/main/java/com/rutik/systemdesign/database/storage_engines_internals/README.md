# Storage Engines Internals

## 1. Concept Overview

A storage engine is the component of a database system responsible for storing, retrieving, and managing data on disk. The choice of storage engine determines fundamental performance characteristics: read/write latency, throughput, compression ratios, recovery time, and space amplification. Two dominant storage engine families are B+tree-based engines (PostgreSQL heap + index, InnoDB) and LSM-tree-based engines (RocksDB, LevelDB, Cassandra SSTables).

---

## 2. Intuition

- **B+tree** is like a phone book: excellent for point lookups and range scans, but updating requires in-place edits that may cause page splits and random I/O.
- **LSM-tree** is like sticky notes that you periodically consolidate into organized binders: all writes go to a fast sequential log (MemTable), periodically flushed and merged. Reads must check multiple layers.
- **WAL** (Write-Ahead Log) is a safety net: before changing any page, write the intent to the log. On crash, replay the log to recover.
- **Key insight**: No storage engine is optimal for all workloads. B+tree wins for read-heavy OLTP; LSM-tree wins for write-heavy time-series and wide-column stores.

---

## 3. Core Principles

### Write-Ahead Log (WAL)

Before any data page is modified, the change is written to the WAL (a sequential append-only file). On crash, the database replays WAL from the last checkpoint to recover in-progress transactions.

```
Write flow:
Client WRITE → WAL record appended (sequential, durable) → Memory page updated → Return success
                                                         ↓ (async)
                                                 Background: flush dirty pages to disk (checkpoint)
```

WAL levels (PostgreSQL):
- `minimal`: Sufficient for crash recovery only
- `replica`: Sufficient for streaming replication (includes changes needed by WAL receiver)
- `logical`: Sufficient for logical decoding (change data capture)

### Buffer Pool / Page Cache

The buffer pool is a memory-resident cache of disk pages. All reads go through the buffer pool; cache misses trigger a disk read. All writes update the buffer pool page (making it "dirty") without immediately writing to disk.

**LRU-K eviction (PostgreSQL clock-sweep, InnoDB LRU with young/old sublists)**: evicts pages not recently accessed. InnoDB uses a 5/8 (young sublist) + 3/8 (old sublist) split to protect frequently accessed pages from being evicted by large scans.

Concrete numbers:
- PostgreSQL `shared_buffers` default = 128 MB; recommend 25% of RAM
- InnoDB `innodb_buffer_pool_size` recommend 70-80% of RAM
- Each page = 8 KB (PostgreSQL) or 16 KB (InnoDB)

---

## 4. Types / Architectures / Strategies

### B+Tree Storage Engine

```
Internal Node (fanout ~400 for 16KB page, 8-byte keys):
+--------+--------+--------+--------+
|  key1  | ptr1   |  key2  | ptr2   |
+--------+--------+--------+--------+

Leaf Node (data or pointers to heap):
+--------+--------+--------+--------+
| row1   | row2   | row3   | next_ptr|
+--------+--------+--------+--------+
(Leaf nodes linked as doubly-linked list for range scans)
```

Properties:
- Height: `log_fanout(N)` — for 64M rows with fanout 400: height = 3 (log_400(64,000,000) ≈ 2.9)
- I/O cost per lookup: O(log_fanout(N)) = 3 I/Os for 64M rows
- Writes cause in-place updates and potential page splits
- Fill factor: leave free space in pages to reduce split frequency

### LSM-Tree Storage Engine

```
Write Path:
Client WRITE → MemTable (in-memory sorted tree)
                 ↓ (when full, ~64MB)
              SSTable L0 (immutable, flushed to disk)
                 ↓ (when L0 count threshold reached)
              Compaction → SSTable L1, L2, L3...

Read Path:
Client READ → Check MemTable → Check L0 SSTables (bloom filter first)
           → Check L1 SSTables → ... → Check Ln SSTables
           → Merge (take newest version)
```

Compaction strategies:
- **STCS (Size-Tiered Compaction)**: merge SSTables of similar size. Good write amplification, bad space amplification.
- **LCS (Leveled Compaction)**: each level is a sorted run. Good space and read amplification, higher write amplification (~10-30x).
- **TWCS (Time-Window Compaction)**: groups by time window. Best for time-series (TTL expiry efficient).

Amplification factors:
- **Write amplification** (WA): bytes written to disk / bytes written by application. LSM: WA=10-30x (LCS); B+tree: WA~1-3x.
- **Read amplification** (RA): I/Os per read. LSM: RA=levels+1 (with bloom filters, often 1-2 I/Os); B+tree: RA=tree height (3-4 I/Os).
- **Space amplification** (SA): disk space / actual data size. LSM: SA=1.1-1.5x (LCS); B+tree: SA=1.3-2x (page fragmentation).

### Row vs Columnar Storage

**Row storage (PostgreSQL, MySQL)**:
```
Row 1: [id=1, name="Alice", age=30, salary=100000]
Row 2: [id=2, name="Bob",   age=25, salary=90000]
```
- Optimal for OLTP: fetch entire row in one I/O
- Poor compression (mixed types in same page)
- Full row scan to compute aggregates

**Columnar storage (ClickHouse, Parquet, DuckDB)**:
```
id column:     [1, 2, 3, 4, ...]
name column:   ["Alice", "Bob", "Carol", ...]
salary column: [100000, 90000, 120000, ...]
```
- Optimal for analytics: read only relevant columns
- Excellent compression (same-type data, delta/RLE encoding)
- Vectorized operations (SIMD on column arrays)
- Poor for point lookups (must reconstruct row across columns)

Compression ratios: columnar typically achieves 5-20x compression vs row storage.

### Copy-on-Write (CoW) Trees

Used in LMDB and TiKV's TitanDB:
- On write, copy modified path from root to leaf, never mutate in-place
- Old version remains accessible until readers release it
- Enables lock-free MVCC: readers always see a consistent snapshot
- Higher write amplification than B+tree in-place (must copy full path)

---

## 5. Architecture Diagrams

```
B+TREE ENGINE (PostgreSQL heap + index):
+------------------+
|   Client Query   |
+------------------+
         |
+------------------+
|   Buffer Pool    | <-- 25% RAM, 8KB pages
|  (shared_buffers)|
+------------------+
    |         |
 Cache     Cache miss
  hit          |
    |    +----------+
    |    | Disk I/O |
    |    | (heap or |
    |    |  index   |
    |    |  file)   |
    |    +----------+
+------------------+
|      WAL         | <-- Sequential writes, pg_wal directory
| (Write-Ahead Log)|
+------------------+

LSM-TREE ENGINE (RocksDB):
+------------------+
|   Client WRITE   |
+------------------+
         |
+------------------+
|  WAL (Commit Log)| <-- Durability
+------------------+
         |
+------------------+
|    MemTable      | <-- In-memory sorted tree (skiplist), ~64MB
| (Active Write    |
|  Buffer)         |
+------------------+
         | (flush when full)
+------------------+
|  L0 SSTables     | <-- 4-8 files, may overlap
+------------------+
         | (compaction)
+------------------+
|  L1 SSTables     | <-- 10x size of L0, no overlap
+------------------+
         | (compaction)
+------------------+
|  L2, L3... LSTs  | <-- Each level 10x previous
+------------------+
```

---

## 6. How It Works — Detailed Mechanics

### B+Tree Page Split

```
Before insert (page full):
[10, 20, 30, 40, 50]

After insert of 35 (page split):
Parent: [... 30 ...]
         /       \
[10,20,30]    [35,40,50]

Cost:
- Write new sibling page
- Write updated parent page (pointer addition)
- In-memory: O(log n) to find insertion point
- Disk: 2-3 additional page writes for split
```

### LSM Bloom Filter

Each SSTable has a bloom filter (typically 10 bits/key, ~1% false positive rate):

```
Read for key K:
1. Check MemTable (exact) — key found → return
2. For each L0 SSTable: check bloom filter
   - Filter says NO  → skip SSTable (100% correct, no false negatives)
   - Filter says YES → search SSTable (may be false positive, 1% rate)
3. For L1+: binary search bloom filter by key range, then check file
```

Without bloom filters: O(levels) SSTable reads per lookup.
With bloom filters: O(1) SSTable reads with 99% probability.

### WAL Crash Recovery

```
Timeline:
T1: Checkpoint (all dirty pages flushed to disk)
T2: Transaction A begins, writes WAL records
T3: Transaction B begins
T4: Transaction A commits (WAL record fsync'd)
T5: Transaction B modifies pages (WAL records written)
T6: CRASH (before B commits, before dirty pages flushed)

Recovery:
1. Find last checkpoint (T1)
2. Replay WAL from T1 → T6
3. Transaction A: committed → redo its changes
4. Transaction B: no commit record → rollback (apply undo log)
Result: state consistent as of T4
```

### Double-Write Buffer (InnoDB)

Protects against torn pages (partial 16KB write during crash):

```
Without double-write:
Page write = OS writes in 4KB chunks
Crash mid-write → partial page = corrupted (unrecoverable)

With double-write (innodb_doublewrite=ON):
1. Write page to sequential double-write buffer on disk (fast, sequential)
2. fsync
3. Write page to its actual location on disk
Crash after step 1 but before 3:
→ Recovery copies from double-write buffer to actual location
Cost: ~5-10% write throughput overhead
```

---

## 7. Real-World Examples

- **PostgreSQL** uses heap files (unordered row storage) + B+tree indexes. Primary key is not the physical storage order (unlike InnoDB). Requires VACUUM to reclaim dead tuples.
- **InnoDB** (MySQL): clustered B+tree index — the primary key IS the physical storage order. Secondary indexes store the PK value as the row locator.
- **RocksDB**: LSM-tree, used by CockroachDB (range=64MB shards), TiKV, Cassandra (SSTables), MyRocks (MySQL engine).
- **LMDB**: Copy-on-write B+tree. Used by OpenLDAP, some embedded applications. Lock-free reads, single-writer constraint.
- **ClickHouse**: MergeTree family — columnar LSM variant. Each part is immutable; background merge compacts parts.

---

## 8. Tradeoffs

| Storage Engine | Write Speed | Read Speed | Compression | Space | Recovery | Best For |
|---------------|-------------|------------|-------------|-------|----------|---------|
| B+tree (PostgreSQL) | Medium | Fast | Low | Medium | Fast | OLTP reads |
| B+tree (InnoDB) | Medium | Fast | Low | Medium | Fast | OLTP mixed |
| LSM (RocksDB) | Fast | Medium | High | Low | Medium | Write-heavy |
| Columnar (ClickHouse) | Slow (batch) | Very Fast | Very High | Low | Medium | Analytics |
| CoW B+tree (LMDB) | Slow (single writer) | Fast | Low | Medium | Instant | Read-heavy embedded |

---

## 9. When to Use / When NOT to Use

**B+tree**: Use for OLTP with mixed read/write workloads, range queries, frequent point lookups. Do not use for write-heavy append-only workloads.

**LSM-tree**: Use for write-heavy workloads (time-series, log ingestion, NoSQL stores), good compression needed, sequential writes dominate. Do not use for read-heavy random-access OLTP.

**Columnar**: Use for analytics, aggregation queries on large datasets, OLAP workloads. Do not use for OLTP point lookups or frequent updates.

**In-memory**: Use for cache layers, session stores, leaderboards. Do not use as primary store without WAL/persistence for durability requirements.

---

## 10. Common Pitfalls

**Pitfall 1: LSM read amplification in production**
A team using Cassandra (LSM) noticed p99 read latency spiking to 500ms. Root cause: L0 SSTable count grew to 20 (default threshold 4) during write bursts, causing each read to check 20 files. Fix: tune `l0_file_num_compaction_trigger=4`, increase compaction throughput budget.

**Pitfall 2: WAL fsync misconfiguration**
A team set `innodb_flush_log_at_trx_commit=0` for performance. During a database server crash, they lost 1 second of transactions. This setting means WAL is only flushed to disk once per second, not per commit. Correct setting for full ACID durability: `innodb_flush_log_at_trx_commit=1`.

**Pitfall 3: Buffer pool too small causing thrashing**
Production PostgreSQL with 64GB RAM but `shared_buffers=128MB` (default). Hot pages were evicted constantly. Simple fix: set `shared_buffers=16GB`. Cache hit rate went from 60% to 99%, query latency dropped 10x.

**Pitfall 4: B+tree page fill factor too high**
An application with heavy sequential inserts near primary key (UUID v4 = random) caused constant page splits. Fix: set `FILLFACTOR=70` on the index to leave 30% free space, reducing split frequency. Alternatively, use ULIDv2 or sequential UUIDs.

**Pitfall 5: LSM compaction falling behind**
Write throughput exceeded compaction throughput → L0 files accumulate → read latency degrades → reads trigger more compaction → positive feedback loop. Fix: throttle ingestion rate, increase `compaction_throughput_mb_per_sec`, add compaction threads.

**Pitfall 6: Torn page without double-write buffer**
Team disabled InnoDB double-write buffer (`innodb_doublewrite=OFF`) for 10% write performance gain. After power failure, several 16KB pages had only their first 4KB written. The pages were unreadable — not even crash recovery could fix them. The team had to restore from backup (4 hours of data loss). Always keep double-write enabled or use SSDs with power-loss protection.

---

## 11. Technologies & Tools

| Tool | Storage Engine | Type | Use Case |
|------|---------------|------|---------|
| PostgreSQL | Heap + B+tree | Row | OLTP, HTAP |
| MySQL/InnoDB | Clustered B+tree | Row | OLTP |
| SQLite | B+tree | Row | Embedded |
| RocksDB | LSM-tree | Row | KV store, embedded NoSQL |
| LevelDB | LSM-tree | Row | Embedded KV |
| LMDB | CoW B+tree | Row | Embedded, read-heavy |
| Cassandra | LSM (SSTables) | Wide-column | Write-heavy distributed |
| ClickHouse | MergeTree | Columnar | Analytics |
| Parquet | Columnar | Columnar | Data lake analytics |
| WiredTiger | B+tree + LSM | Both | MongoDB default engine |

---

## 12. Interview Questions with Answers

**Q: Why does InnoDB use a clustered index and what is the impact on secondary indexes?**
InnoDB's primary key IS the B+tree — rows are physically stored in primary key order within leaf nodes. This makes primary key lookups require only one B+tree traversal. Secondary indexes store the primary key value as the row locator, not the physical row address. A secondary index lookup requires two B+tree traversals: first through the secondary index to get the PK, then through the clustered index (primary) to get the full row. This "double lookup" costs an extra I/O per secondary index scan if the data is not in the buffer pool.

**Q: Walk me through a write operation in RocksDB from application to durable storage.**
(1) Write is appended to the WAL file synchronously (ensures durability). (2) Write is inserted into the MemTable (an in-memory skip list, ordered by key). (3) Write is acknowledged to the application. (4) When MemTable reaches ~64MB, it becomes immutable and a new MemTable opens. (5) Background thread flushes the immutable MemTable to an SSTable on disk (L0 file). (6) Background compaction merges L0 SSTables into L1, L1 into L2, etc. Each merge step produces larger, sorted, de-duplicated SSTables.

**Q: How does WAL enable point-in-time recovery?**
The WAL is an append-only sequence of all changes ever made to the database. By archiving WAL segments continuously (pg_archivecommand, WAL-G), you accumulate a complete change log. To recover to time T: start from the last base backup before T, replay archived WAL segments one by one until reaching T. Each WAL record is idempotent (replay-safe) because it's a physical delta (page number, offset, old value, new value). PostgreSQL WAL segments are 16MB each by default.

**Q: What is write amplification in LSM-trees and how do you minimize it?**
Write amplification (WA) = bytes written to disk / bytes written by application. In Leveled Compaction (LCS), data moves through multiple levels: L0→L1→L2→L3. Each level-crossing rewrites the data. WA = sum over levels of (level_ratio), typically 10-30x. Minimization strategies: (1) Increase SSTable sizes (fewer compaction events). (2) Use Size-Tiered Compaction (STCS) which has lower WA (~10x) at cost of space amplification. (3) Tune `level0_file_num_compaction_trigger` to reduce premature compaction. (4) Use WA-optimized algorithms like RocksDB's Dynamic Leveled Compaction.

**Q: Explain the difference between B+tree and LSM-tree for a workload with 10,000 writes/second and 100 reads/second.**
With 10K writes/sec, B+tree suffers because each write potentially causes random I/O (page lookup + possible split + WAL write). This generates high IOPS demand. LSM-tree converts random writes to sequential writes (WAL + MemTable), dramatically reducing IOPS at the cost of read amplification. For this write-heavy, read-light workload, LSM-tree is superior — it can sustain higher write throughput on the same hardware. Use RocksDB, Cassandra, or similar LSM-based systems. For 100 reads/sec with bloom filters, read latency is acceptable even with 2-3 SSTable checks per read.

**Q: How does the buffer pool handle dirty pages and when does it flush them to disk?**
Dirty pages (modified but not yet written to disk) are flushed by: (1) Checkpoint process — periodically flushes all dirty pages to disk and advances the checkpoint LSN in WAL (default checkpoint_timeout=5min in PostgreSQL). (2) Background writers — bgwriter in PostgreSQL continuously flushes least-recently-used dirty pages to avoid checkpoint I/O spikes. (3) LRU eviction — when a clean page is needed but buffer pool is full, evict the LRU page; if it's dirty, flush it first. (4) explicit CHECKPOINT command.

**Q: What is a page cache and how does it differ from the buffer pool?**
The OS page cache caches file system blocks. The database buffer pool caches database pages. When a database has its own buffer pool (PostgreSQL, InnoDB), data can be cached twice: once in the buffer pool and once in the OS page cache — "double buffering." PostgreSQL uses O_RDONLY + madvise(MADV_DONTNEED) for sequential scans to avoid OS page cache pollution. `effective_cache_size` in PostgreSQL tells the query planner how much OS cache is available without actually allocating it.

**Q: Explain Copy-on-Write trees and their advantage for MVCC.**
In CoW trees (LMDB, TiKV), every write creates a new version of the modified path from root to the changed leaf — the old path remains unchanged. Readers take a pointer to the root at their snapshot time and traverse it locklessly, never seeing any in-progress write. This enables true lock-free reads: no latches, no shared memory contention, no MVCC garbage to collect. The downside: LMDB allows only one writer at a time (writer takes a file-level lock), and write amplification is proportional to tree height (typically 3-4 pages copied per write vs LSM's sequential write).

**Q: What is tombstone accumulation in LSM-trees and how does it affect performance?**
A delete in an LSM-tree writes a tombstone marker (a special delete record). The original row may still exist in older SSTables. During reads, the system must check all SSTables, find the tombstone, and skip the row — increasing read amplification. Tombstones are only removed during compaction when all SSTables containing the original row have been merged. In Cassandra with heavy deletes and slow compaction, tombstone count can reach millions, degrading read latency from <5ms to >500ms. Fix: tune `tombstone_compaction_interval`, use TTL-based expiry instead of explicit deletes, use TWCS with time-based data.

**Q: How does RocksDB's bloom filter reduce read amplification?**
Each SSTable has a per-file Bloom filter (typically 10 bits/key, ~1% false positive rate). For a read: (1) Check MemTable — exact. (2) For each SSTable (newest to oldest), check bloom filter first. Filter says NO → SSTable definitely does not have the key (skip — no I/O). Filter says YES → search the SSTable (1% chance of false positive — one extra I/O). Net effect: instead of reading N SSTable files, only ~1-2 SSTable files are read per key lookup. Memory cost: ~10 bits/key × number of keys.

**Q: Compare the recovery times for B+tree vs LSM-tree engines after a crash.**
B+tree recovery (PostgreSQL/InnoDB): Replay WAL from last checkpoint. Checkpoint interval = 5min default. WAL at 100MB/sec for 5min = 30GB WAL to replay in worst case. Recovery takes seconds to minutes depending on WAL size and replay speed. LSM-tree recovery (RocksDB): Replay WAL for only the MemTable contents (since last flush). MemTable flush happens every ~64MB. WAL to replay is much smaller. Recovery typically takes < 1 second for small MemTables. However, opening an LSM database requires reading all SSTable metadata files, which can take several seconds for large databases.

**Q: What is the doublewrite buffer in InnoDB and is it still needed on modern SSDs?**
The doublewrite buffer is a sequential area in the InnoDB tablespace where pages are written before being written to their actual locations. This prevents torn pages: if a crash occurs during the 16KB page write, InnoDB recovers the page from the doublewrite buffer. On modern SSDs with power-loss protection (PLP) capacitors (enterprise SSDs, NVMe with 'power loss data protection'), torn writes are guaranteed not to occur because the capacitor provides enough power to complete the in-flight write. With PLP SSDs, `innodb_doublewrite=OFF` is safe and eliminates ~10% write overhead. Consumer SSDs without PLP still need the doublewrite buffer.

**Q: How does columnar storage achieve 10-100x compression compared to row storage?**
Columnar stores adjacent values of the same column together. Same-type data compresses dramatically: (1) Delta encoding for sorted numeric columns (timestamps, sequential IDs) — store deltas instead of full values. Example: [1000, 1001, 1002] → delta=[1000, 1, 1], fits in fewer bits. (2) RLE (Run-Length Encoding) for low-cardinality columns (e.g., country codes) — store [US×1000, UK×500]. (3) Dictionary encoding for string columns — map values to 2-byte integers. (4) Bit-packing for small integers. ClickHouse uses LZ4 on top of these encoding, achieving 10-100x compression on typical analytical data.

**Q: What is the role of the WAL sender and WAL receiver in PostgreSQL streaming replication?**
The WAL sender is a backend process on the primary that continuously reads the WAL and streams WAL records to connected replicas. The WAL receiver is a process on the replica that receives WAL records, writes them to the replica's WAL, and applies them to update the replica's data files. This is physical replication: the replica applies the exact same byte changes as the primary. Recovery point: if the primary crashes, promote the replica (it has replayed all received WAL). Monitoring: `pg_stat_replication` on primary shows WAL sender lag per replica.

**Q: Explain the InnoDB redo log (circular) and why it has a size limit.**
The InnoDB redo log (ib_logfile0, ib_logfile1 — or auto-sized in MySQL 8) is a circular buffer. New redo records are appended at the write position. The oldest records that are no longer needed (because their dirty pages have been flushed to disk) are overwritten. If dirty pages are not flushed fast enough, the write position catches up to the oldest needed record — this triggers a "sharp checkpoint" (emergency flush of all dirty pages), causing severe I/O spikes. Default redo log size in MySQL 5.7: 48MB (way too small). Recommendation: 1-4GB or use MySQL 8's auto-sizing. Set `innodb_log_file_size` accordingly.

**Q: How do MVCC dead tuples cause table bloat in PostgreSQL?**
In PostgreSQL's MVCC, UPDATE = delete old version + insert new version. The old version (dead tuple) is marked with xmax = committing transaction's ID but remains physically in the heap until VACUUM reclaims it. Dead tuples consume disk space and increase heap scan cost. On a table with 100M rows and 10% update rate per day: after 30 days without VACUUM, 30M dead tuples accumulate. A sequential scan reads them all. VACUUM removes dead tuples by marking their space as reusable (doesn't return space to OS — that requires VACUUM FULL, which takes an exclusive lock). Auto-vacuum triggers when dead tuple count exceeds `autovacuum_vacuum_scale_factor` (default 0.2 = 20% of table) × table row count.

**Q: What is the LSM tree's space amplification and how does leveled compaction reduce it?**
Space amplification = actual disk space / minimum space needed for data. Size-Tiered Compaction (STCS) has SA up to 2x because multiple overlapping SSTables can contain different versions of the same key. Leveled Compaction (LCS) limits SA to ~1.1x because within each level (L1+), there is no key overlap — at most two versions of a key exist simultaneously (one in Lk, one being written to Lk+1 during compaction). The tradeoff: LCS has higher write amplification (10-30x) because keys are rewritten across levels more frequently.

---

## 13. Best Practices

1. Size the buffer pool to fit the working set: `shared_buffers = 25% RAM` (PostgreSQL), `innodb_buffer_pool_size = 75% RAM` (InnoDB).
2. Monitor write amplification in RocksDB/Cassandra with `rocksdb.bytes.written` metric.
3. Use `FILLFACTOR=70-90` on B+tree indexes with heavy update patterns to reduce page splits.
4. Configure WAL durability explicitly: never set `fsync=off` in production; the default `synchronous_commit=on` is correct.
5. Enable InnoDB double-write buffer unless on enterprise SSDs with power-loss protection.
6. For LSM-based systems, tune compaction to stay ahead of write throughput — falling behind causes read latency spikes.
7. Choose primary key data types carefully: random UUIDs cause random B+tree inserts (cache thrashing); use sequential ULIDs or timestamp-prefixed IDs for insert-heavy workloads.
8. Monitor buffer pool hit rate: below 99% means the working set doesn't fit in memory — increase buffer pool or add RAM.

---

## 14. Case Study

**Scenario**: A fintech company's PostgreSQL database handling 5,000 transactions/second starts showing p99 write latency spiking from 5ms to 500ms every 5 minutes, exactly at checkpoint intervals.

**Diagnosis**:
```sql
-- Check checkpoint frequency and duration
SELECT * FROM pg_stat_bgwriter;
-- Result: checkpoints_timed=288 (5min interval),
--         checkpoint_write_time=45000ms (45 seconds!),
--         buffers_checkpoint=800000 (6.4GB of dirty data per checkpoint)

-- Check WAL configuration
SHOW max_wal_size; -- 1GB (too small causing frequent checkpoints)
SHOW checkpoint_completion_target; -- 0.5 (default, compresses checkpoint into first 50% of interval)
```

**Root cause**: `max_wal_size=1GB` caused checkpoint every 60 seconds (not 5 minutes). `checkpoint_completion_target=0.5` caused all I/O to happen in first 30 seconds of the 60-second interval — massive I/O spike affecting query latency.

**Fix applied**:
```
max_wal_size = 8GB               -- Allow more WAL before forcing checkpoint
checkpoint_completion_target = 0.9 -- Spread checkpoint I/O over 90% of interval
checkpoint_timeout = 10min       -- Maximum time between checkpoints
```

**Result**: Checkpoint I/O spread over 9 minutes (vs 30 seconds), eliminating spikes. p99 write latency returned to 5-8ms consistently. Disk I/O bandwidth utilization smoothed from 100% bursts to steady 30%.

Key lesson: checkpoint tuning is one of the most impactful PostgreSQL performance levers, but it requires understanding the interaction between `max_wal_size`, `checkpoint_timeout`, and `checkpoint_completion_target`.
