# Replication and High Availability

## 1. Concept Overview

Replication is the process of maintaining copies of data on multiple database servers so that the system continues to function when individual nodes fail. High availability (HA) combines replication with automated failover to eliminate (or minimize) downtime from hardware failures, software crashes, or planned maintenance. The key tension is between consistency (synchronous replication = zero data loss, high latency) and availability/performance (asynchronous replication = low latency, potential data loss on failover).

Production databases use one of three topologies: single-primary with replicas (most common), multi-primary (active-active, complex), or leaderless (Dynamo-style, used by Cassandra/DynamoDB).

---

## 2. Intuition

Think of a primary database as a master ledger and replicas as copies given to trusted assistants. Synchronous replication is like requiring every assistant to sign before the transaction is complete — slow but you are guaranteed everyone has the update. Asynchronous replication is like handing out carbon copies — faster, but if the master ledger burns before the carbon copies arrive, those transactions are lost.

High availability adds a protocol: when the master ledger is lost, the most up-to-date assistant is promoted and becomes the new master.

---

## 3. Core Principles

**Replication log**: Changes are captured as a sequential log (WAL in PostgreSQL, binlog in MySQL, oplog in MongoDB). Replicas consume this log and apply changes to their own copy.

**Replication lag**: The time between a write being committed on the primary and applied on a replica. Even 0.1 seconds of lag matters if your application reads from replicas and expects to see its own writes.

**Quorum**: For HA decisions (is the primary dead? should we failover?), a majority of nodes must agree to prevent split-brain.

**Fencing**: After failover, the old primary must be prevented from accepting writes. A fencing token or STONITH (Shoot The Other Node In The Head) ensures the old primary cannot corrupt data after it is demoted.

---

## 4. Types / Architectures / Strategies

```
Topology            | Description                      | Use Case
--------------------|----------------------------------|----------------------------
Single-primary      | One writer, N readers            | Most OLTP workloads
Multi-primary       | Multiple writers, conflict mgmt  | Multi-region active-active
Leaderless          | Any node accepts writes          | Dynamo, Cassandra, Riak
Cascading           | Replica of a replica             | Reduce primary load

Replication Method  | Description                      | Lag Profile
--------------------|----------------------------------|----------------------------
Streaming (physical)| Raw WAL bytes, page-level changes| Lowest lag (sub-second)
Logical             | Row-level change events (CDC)    | Slightly higher lag, selective
Statement-based     | SQL statements                   | Non-deterministic (avoid)
```

---

## 5. Architecture Diagrams

**PostgreSQL streaming replication with Patroni HA.** A Distributed Configuration Store (DCS) arbitrates leadership; the Patroni agent holding the lease runs the PostgreSQL primary, while the other agents run standbys that pull WAL over streaming replication.

```mermaid
flowchart TD
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    DCS(["etcd / Consul / ZooKeeper<br/>DCS · 3-node quorum"])
    subgraph Agents["Patroni agents on every DB node"]
        AG1(Agent 1<br/>primary)
        AG2(Agent 2<br/>replica)
        AG3(Agent 3<br/>replica)
    end
    DCS -->|"leader lease"| Agents
    AG1 --> PG1(PostgreSQL<br/>Primary)
    AG2 --> PG2(PostgreSQL<br/>Standby)
    AG3 --> PG3(PostgreSQL<br/>Standby)
    PG1 -.->|"WAL stream"| PG2
    PG1 -.->|"WAL stream"| PG3

    class DCS base
    class AG1 train
    class AG2,AG3 frozen
    class PG1 train
    class PG2,PG3 frozen
```

**Failover sequence.** Losing the primary's heartbeat is only the first of six state transitions Patroni walks through before traffic reaches a new primary.

```mermaid
stateDiagram-v2
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    state "Primary Healthy" as Healthy
    state "Heartbeat Lost" as Lost
    state "DCS Lease Expired (30s TTL)" as Expired
    state "Leader Election" as Election
    state "Replica Promoted" as Promoted
    state "Replicas Reconfigured" as Reconfigured
    state "Load Balancer Updated" as LBUpdated

    [*] --> Healthy
    Healthy --> Lost: primary crashes
    Lost --> Expired: no lease renewal
    Expired --> Election: agents compete
    Election --> Promoted: winner runs<br/>pg_ctl promote
    Promoted --> Reconfigured: replicas rewire<br/>to new primary
    Reconfigured --> LBUpdated: HAProxy updated<br/>via Patroni REST API
    LBUpdated --> [*]

    class Healthy train
    class Lost lossN
    class Expired mathOp
    class Election mathOp
    class Promoted train
    class Reconfigured base
    class LBUpdated train
```

**Replication lag sources.** Each hop between commit and apply adds its own slice of latency.

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    C(Primary COMMIT<br/>t=0) --> WS(WAL Sender<br/>reads WAL · t=0.1ms)
    WS --> NET(Network transmit<br/>t=1ms LAN)
    NET --> WR(WAL Receiver<br/>writes WAL · t=1.1ms)
    WR --> AP(Apply to replica<br/>t=1.5ms)

    class C io
    class WS,WR mathOp
    class NET frozen
    class AP train
```

Total lag: 1.5ms on a LAN to 50ms+ across regions.

**Multi-region active-active (conflict territory).** Two primaries in two regions each accept local writes and replicate asynchronously to the other; nothing stops the same row from being written in both regions before either replica catches up.

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    subgraph RegionA["Region A"]
        PA(Primary A)
        RA1(Replica A1)
        RA2(Replica A2)
        PA --> RA1
        PA --> RA2
    end
    subgraph RegionB["Region B"]
        PB(Primary B)
        RB1(Replica B1)
        RB2(Replica B2)
        PB --> RB1
        PB --> RB2
    end
    PA -.->|"async repl"| PB
    PB -.->|"async repl"| PA

    class PA,PB train
    class RA1,RA2,RB1,RB2 frozen
```

```mermaid
sequenceDiagram
    participant UA as Client (Region A)
    participant PA as Primary A
    participant PB as Primary B
    participant UB as Client (Region B)

    UA->>PA: SET email=alice@a.com (T=100)
    PA-->>UA: commit (local)
    UB->>PB: SET email=bob@b.com (T=101)
    PB-->>UB: commit (local)
    PA->>PB: async replicate write (T=100)
    PB->>PA: async replicate write (T=101)
    Note over PA,PB: Same row, two writes -<br/>resolve via LWW, app merge,<br/>or reject second write
```

Both writes commit locally before either region learns about the other's update, so replication delivers a genuine conflict that only application-level policy (last-write-wins, merge, or reject) can resolve.

---

## 6. How It Works — Detailed Mechanics

### PostgreSQL WAL and Streaming Replication

PostgreSQL writes every change to the WAL (Write-Ahead Log) before applying it. Each WAL record has an LSN (Log Sequence Number). The WAL sender process on the primary streams WAL segments to WAL receiver processes on replicas. Replicas apply WAL in order, maintaining an exact copy of the primary's data.

```
WAL segment size: 16MB per file (pg_wal/ directory)
LSN format: 0/1234ABCD (segment/offset within segment)

Key configuration:
  wal_level = replica        -- minimum for streaming replication
  max_wal_senders = 10       -- max concurrent replication connections
  wal_keep_size = 1GB        -- retain this much WAL for slow replicas (without slot)

Monitoring replication lag:
  SELECT
      client_addr,
      state,
      sent_lsn,
      write_lsn,
      flush_lsn,
      replay_lsn,
      (sent_lsn - replay_lsn) AS replication_lag_bytes,
      write_lag,
      flush_lag,
      replay_lag
  FROM pg_stat_replication;
```

### Replication Slots

A replication slot prevents the primary from discarding WAL that a replica has not yet consumed. Without a slot, if a replica falls behind, the primary may delete WAL before the replica reads it, breaking replication.

```sql
-- Create a physical replication slot
SELECT pg_create_physical_replication_slot('replica_1');

-- Monitor slot WAL retention
SELECT
    slot_name,
    pg_size_pretty(pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn)) AS lag_size
FROM pg_replication_slots;
```

**Replication slot danger**: If a replica disconnects for days, the primary accumulates all WAL since the replica's last consumed LSN. A 1TB/day write workload fills disk in hours. This has caused production outages: replica slot WAL accumulation fills the primary's disk, crashing the database.

**Rule**: Always monitor slot lag size. Alert at 10GB. Drop the slot if the replica is permanently gone: `SELECT pg_drop_replication_slot('replica_1');`

### Synchronous vs Asynchronous Replication

```
postgresql.conf:
  synchronous_commit = on          -- default: wait for primary local WAL flush
  synchronous_standby_names = ''   -- asynchronous (no replica acknowledgment)

For synchronous replication:
  synchronous_standby_names = 'FIRST 1 (replica1, replica2)'
  -- Wait for at least 1 replica to flush WAL before returning commit to client

Impact:
  synchronous_commit = on, no standby: ~0.1ms overhead (local fsync)
  synchronous_commit = on, one synchronous standby (LAN): +0.5–2ms per commit
  synchronous_commit = on, one synchronous standby (cross-region): +10–200ms per commit

synchronous_commit = off (async):
  Write acknowledged before WAL flushed to disk. Up to ~200ms of data loss window.
  3x higher throughput for write-heavy workloads. Acceptable for session data, logs.
  NOT acceptable for financial transactions.
```

The commit-latency cost of synchronous replication scales dramatically with distance — the same RPO=0 guarantee that costs about 1-2ms on a LAN can cost up to 200ms across regions:

```mermaid
xychart-beta
    title "Commit Latency Overhead by Replication Mode"
    x-axis ["No standby", "Sync - same LAN", "Sync - cross-region"]
    y-axis "Added latency per commit (ms)" 0 --> 200
    bar [0.1, 2, 200]
```

That roughly 2000x range between the cheapest and most expensive synchronous option is why most teams keep synchronous standbys in the same datacenter and fall back to asynchronous replication for cross-region replicas.

### Patroni HA and Split-Brain Prevention

Patroni uses a Distributed Configuration Store (DCS — etcd, Consul, or ZooKeeper) as the arbiter. The DCS is itself a 3-node quorum, tolerating 1 node failure. Patroni's leader holds a DCS lease (TTL = 30s). If the leader fails to renew, the lease expires and a new leader is elected.

**Fencing**: Before promoting a new primary, Patroni ensures the old primary is fenced:
1. Patroni calls the DCS to revoke the old primary's lease
2. Optionally: STONITH (power off the old primary via IPMI, cloud API) — prevents it from accepting writes even if it comes back up
3. The new primary issues a `pg_ctl promote`

Without fencing, the old primary could temporarily serve stale writes in a split-brain scenario: both the old and new primary accept writes, and data diverges.

### MySQL Binary Log and GTID Replication

MySQL uses the binary log (binlog) for replication. With GTID (Global Transaction Identifier), each transaction has a globally unique ID of the format `server_uuid:transaction_id`. Replicas use GTIDs to track exactly which transactions they have applied, making failover self-healing: a new replica can connect to any primary and say "I have all transactions up to GTID X; send me the rest."

```sql
-- Enable GTID replication in my.cnf
gtid_mode = ON
enforce_gtid_consistency = ON

-- Check replica GTID status
SHOW REPLICA STATUS\G
  Executed_Gtid_Set: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx:1-1000
  Retrieved_Gtid_Set: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx:1-1001
  -- Lag: 1 transaction behind
```

---

## 7. Real-World Examples

**GitHub**: Uses Orchestrator for MySQL HA. Orchestrator monitors topology, detects failures, and performs automated failover. GitHub's "loss aversion" approach: they accept a brief failover window (30–60s) to avoid data loss, using semi-synchronous replication.

**Zalando**: Open-sourced Patroni. Their PostgreSQL HA setup uses Patroni + etcd with automated failover tested weekly via chaos engineering (they intentionally kill primaries in production).

**Instagram**: Migrated from MySQL to PostgreSQL with Patroni for HA. Used logical replication for zero-downtime major version upgrades (PG 9.6 → PG 10) without downtime.

---

## 8. Tradeoffs

```
Mode                    | RPO      | RTO         | Throughput Impact | Complexity
------------------------|----------|-------------|-------------------|------------
Async replication       | Seconds  | 30-120s     | None              | Low
Sync (1 standby, LAN)   | 0        | 30-60s      | +1ms/write        | Medium
Sync (1 standby, WAN)   | 0        | 30-60s      | +50-200ms/write   | Medium
Multi-primary (active)  | 0        | 0 (no fail) | Conflict overhead | Very High
Patroni HA              | Seconds  | 15-30s      | None              | Medium
Cloud HA (RDS Multi-AZ) | 0        | 60-120s     | None (+3ms AZ)    | Low (managed)
```

---

## 9. When to Use / When NOT to Use

The five rules below collapse into a single decision path — RPO first, then region topology, then operational appetite:

```mermaid
flowchart TD
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    Start{"RPO = 0<br/>required?"} -->|"yes"| Sync(Synchronous replication<br/>same-DC standby)
    Start -->|"no"| Region{"Multi-region<br/>active-active?"}
    Region -->|"yes, conflict<br/>handling ready"| Multi(Multi-primary<br/>active-active)
    Region -->|"no"| Ops{"Ops simplicity<br/>over cost?"}
    Ops -->|"yes"| Managed(Managed HA<br/>RDS Multi-AZ / Cloud SQL)
    Ops -->|"no"| Async(Patroni + async<br/>read replicas)

    class Start,Region,Ops mathOp
    class Sync,Managed,Async train
    class Multi lossN
```

**Use synchronous replication when**: RPO=0 is a hard requirement (financial, healthcare). Data loss on failover is unacceptable even for a single write.

**Use asynchronous replication when**: Performance trumps RPO=0. Accept 1–60 seconds of potential data loss on primary failure. Typical for read-scaling replicas, analytics replicas, and reporting databases.

**Use Patroni when**: You run self-hosted PostgreSQL and need automated HA. It is the de facto standard. Requires managing etcd/Consul separately.

**Use managed HA (RDS Multi-AZ, Cloud SQL HA) when**: Operational simplicity trumps cost. No need to manage Patroni or etcd.

**Avoid multi-primary when**: Your application is not designed for conflict resolution. The complexity cost is almost always higher than the benefit for single-region applications.

---

## 10. Common Pitfalls

**Replication slot causing disk fill**: A replica is offline for maintenance. Its replication slot holds WAL on the primary. A high-write workload generates 50GB/hour of WAL. 12 hours later, the primary's disk fills and PostgreSQL crashes — taking down the entire service. The fix: monitor slot lag aggressively; set a maximum retained WAL size; drop slots for disconnected replicas immediately.

**Split-brain after network partition**: Primary and replica lose connectivity. HAProxy's health checks detect the primary as down and starts routing writes to the promoted replica. The old primary's health check reconnects and it also starts accepting writes. Data diverges on both. Fix: use Patroni with proper fencing; never run HA without a quorum-based DCS.

**Read-your-writes violation**: User updates their profile, then reads it back. The read is routed to an async replica with 500ms lag. The user sees the old profile. Fix: route write-following reads to the primary, or use a stale-read tolerance window with replica lag checks.

**Failover during high-traffic**: Planned failover during peak traffic. The new primary starts cold — its buffer pool is empty. Query performance degrades 10x for 5 minutes while the buffer pool warms up. Fix: schedule failovers during low-traffic windows, or use `pg_prewarm` extension to preload frequently accessed pages on the new primary before promoting.

---

## 11. Technologies & Tools

| Tool            | Purpose                                   | Database     |
|-----------------|-------------------------------------------|--------------|
| Patroni         | PostgreSQL HA with DCS-based failover     | PostgreSQL   |
| repmgr          | PostgreSQL replication management         | PostgreSQL   |
| Orchestrator    | MySQL HA and topology management          | MySQL        |
| MHA             | MySQL Master HA (older, less used)        | MySQL        |
| HAProxy         | TCP load balancing, health-check routing  | Any          |
| PgBouncer       | Connection pooling during failover        | PostgreSQL   |
| WAL-G           | WAL archiving + continuous backup         | PostgreSQL   |
| Debezium        | CDC from replication log to Kafka         | PG/MySQL/Mongo|

---

## 12. Interview Questions with Answers

**Q: What is split-brain and how does Patroni prevent it?**
Split-brain occurs when two nodes both believe they are the primary and accept writes simultaneously, creating divergent data that cannot be automatically reconciled. Patroni prevents this using a Distributed Configuration Store (etcd, Consul, or ZooKeeper) as an external arbiter. Only the node holding the DCS leader lease may act as primary. When a primary fails to renew its lease (due to crash or network partition), the lease expires and a new election occurs. Before promoting a new primary, Patroni optionally fences the old primary (via STONITH or cloud API) to ensure it cannot accept writes even if it recovers connectivity.

**Q: Why can replication slots be dangerous in production?**
Replication slots prevent the primary from discarding WAL that a replica has not yet consumed. If a replica disconnects (maintenance, failure, misconfiguration) and its slot is not dropped, the primary retains all WAL generated since the replica's last applied position. In a write-heavy environment, this can fill the primary's disk in hours. PostgreSQL does not automatically drop slots or limit WAL accumulation — it will crash with "no space left on device" rather than drop a slot. Always monitor `pg_replication_slots.restart_lsn` lag and alert when slot lag exceeds 10GB.

**Q: How do you achieve read-your-writes consistency with read replicas?**
Options in increasing complexity: (1) Route all reads to the primary — simple but defeats the purpose of replicas. (2) Track the last write's LSN per session and only read from a replica that has caught up to that LSN — requires replica lag monitoring and routing logic. (3) Use sticky sessions: after a write, route all reads from that session to the primary for a brief window (e.g., 500ms). PostgreSQL `pg_stat_replication.replay_lag` provides the per-replica lag. Application frameworks like Spring Data can route reads using a custom `AbstractRoutingDataSource` with lag awareness.

**Q: Explain semi-synchronous replication tradeoffs.**
Semi-synchronous replication (MySQL `rpl_semi_sync_source_enabled`) waits for at least one replica to acknowledge receipt of WAL before returning success to the client — but only acknowledgment of receipt (written to replica's relay log), not full apply. This provides a guarantee that committed data exists on at least two servers, reducing data loss window to near-zero, while adding only one network RTT of latency (~0.5–2ms on LAN). If no replica acknowledges within `rpl_semi_sync_source_timeout` (default 10s), MySQL falls back to asynchronous mode to avoid blocking indefinitely. The fallback means semi-sync is not a hard RPO=0 guarantee.

**Q: What is the difference between streaming replication and logical replication in PostgreSQL?**
Streaming replication sends raw WAL bytes (physical replication) — the replica maintains an identical bit-for-bit copy of the primary. It is simpler, lower overhead, and used for HA standby servers. Logical replication decodes WAL into row-level change events (INSERT/UPDATE/DELETE) and replicates specific tables or databases. It allows replication to a different PostgreSQL major version, to a replica with different schema (e.g., additional indexes), or to non-PostgreSQL systems (via Debezium). The tradeoff: logical replication is slightly higher overhead and does not replicate DDL automatically.

**Q: How do you perform a zero-downtime major version upgrade of PostgreSQL?**
Use logical replication across versions: (1) Set up PostgreSQL 17 instance alongside PG 15 primary. (2) Use `pg_logical` or `pglogical` extension to create logical replication from PG 15 → PG 17. Wait for PG 17 to fully catch up (lag < 1 second). (3) Put application into read-only mode briefly (or use traffic shaping to drain writes). (4) Wait for PG 17 replication lag to reach 0. (5) Update application DSN to PG 17 primary. (6) Remove PG 15 instance. Alternatively, use `pg_upgrade --link` (hard links, fast but requires downtime for switchover) or AWS Aurora's blue/green deployments.

**Q: What is cascading replication and when is it useful?**
Cascading replication is a replica-of-a-replica topology: Primary → Replica A → Replica B. Replica B receives WAL from Replica A rather than the primary. This reduces WAL sender connections on the primary (each WAL sender uses ~5MB RAM and CPU), useful when you have many replicas (> 10). Replica B has higher replication lag (primary lag + A-to-B lag). Use cascading for analytics or reporting replicas that can tolerate higher lag, while keeping lag-sensitive read replicas directly connected to the primary.

**Q: How does MySQL Group Replication differ from traditional MySQL replication?**
Group Replication (MySQL 5.7.17+) uses a Paxos-based group communication protocol where all group members agree on the order of transactions before they commit. Every write is certified against concurrent writes before committing, detecting conflicts automatically. It supports both single-primary mode (one writer, automated failover) and multi-primary mode (all nodes accept writes, conflict detection). Unlike traditional async replication where the primary decides and replicas follow, Group Replication requires a quorum (N/2+1) to commit any transaction, making it CP rather than AP. Trade-off: higher write latency (~1ms additional for group certification) in exchange for stronger consistency.

**Q: What monitoring metrics are essential for replication health?**
Critical metrics: (1) `replication_lag` (seconds or bytes behind primary) — alert at > 60 seconds. (2) `pg_replication_slots` slot lag in bytes — alert at > 10GB. (3) `pg_stat_replication.write_lag / flush_lag / replay_lag` — distinguish network lag from apply lag. (4) `seconds_behind_master` in MySQL `SHOW REPLICA STATUS` — alert at > 30 seconds. (5) Replica count: alert if fewer replicas than expected are connected. (6) Primary WAL generation rate (bytes/sec) vs replica apply rate — if primary generates faster than replica applies, lag will grow. (7) Error log entries: replication errors often appear here before they cause visible lag.

**Q: How do you handle a lagging replica that is falling further behind?**
Diagnose first: is lag from network saturation, slow disk on replica, long-running transactions on primary causing large WAL, or DDL locks on replica? Tools: `pg_stat_replication`, `pg_stat_activity` on both primary and replica. Fixes: (1) Reduce primary write volume (batch more, write less). (2) If primary has long transactions, set `max_standby_streaming_delay = -1` on replica to allow it to cancel conflicting queries. (3) Add replica compute/storage resources. (4) If the lag is unrecoverable, rebuild the replica from a fresh base backup with `pg_basebackup`.

**Q: What is STONITH and why is it necessary for HA?**
STONITH (Shoot The Other Node In The Head) is a fencing mechanism that forcibly powers off or isolates a failed node before promoting its replacement. Without STONITH, a scenario arises: primary crashes and appears dead to the HA manager; new primary is promoted; old primary recovers connectivity and believes it is still primary. Both accept writes — split-brain. STONITH prevents this by cutting power to the old primary via IPMI/iLO, cloud API (AWS: terminate instance, stop EBS volume), or network-level isolation before promoting the new primary. Patroni supports STONITH via callback scripts.

**Q: Explain the differences between RPO and RTO in the context of database replication.**
RPO (Recovery Point Objective) is the maximum acceptable data loss measured in time: how many minutes or seconds of committed transactions can the business afford to lose? With async replication, RPO = replication lag at the time of failure (typically seconds to minutes). With synchronous replication, RPO = 0. RTO (Recovery Time Objective) is the maximum acceptable downtime: how long can the service be unavailable? With Patroni HA, RTO = 15–30 seconds (automatic failover). With manual failover, RTO = 5–30 minutes (human response time). Cloud managed HA (RDS Multi-AZ) typically achieves RTO < 2 minutes. Both metrics must be defined by business requirements before designing the replication topology.

**Q: What is the role of the pg_hba.conf in replication setup?**
`pg_hba.conf` controls client authentication on PostgreSQL. For replication connections, a specific entry is needed: `host replication replicator 10.0.0.0/24 scram-sha-256`. This allows the `replicator` role (created with `CREATE ROLE replicator WITH REPLICATION LOGIN`) from the replica's subnet to connect for WAL streaming. Without this entry, replicas cannot authenticate. In Patroni setups, the `pg_hba.conf` is managed by Patroni itself using the `bootstrap.dcs.postgresql.pg_hba` configuration, ensuring consistent auth across failover.

**Q: How does multi-region replication work and what are its limitations?**
In multi-region active-passive replication, the primary is in region A and replicas are in region B and C. Writes go to region A; replicas consume WAL over the WAN link (typically 50–200ms latency). RTO involves failing over to region B, which may have 1–10 seconds of lag, implying potential data loss at that lag. Active-active (multi-primary) replication across regions is more complex: every write must be replicated to all regions, conflicts from concurrent writes to the same row must be resolved (LWW, custom merge, or reject), and the application must handle conflict resolution. Systems like CockroachDB and Spanner handle this natively; for PostgreSQL, BDR (Bi-Directional Replication, by EDB) provides multi-master with conflict detection.

**Q: How does logical replication enable CDC (Change Data Capture)?**
Logical replication decodes WAL into row-level change events. Debezium connects to PostgreSQL as a logical replication client using the `pgoutput` output plugin. It receives INSERT/UPDATE/DELETE events per row with old and new values, and publishes them to Kafka topics. Consumers (Elasticsearch indexers, data warehouse loaders, cache invalidation services) process these events asynchronously. The key advantage: Debezium reads the WAL directly without impacting primary performance (WAL is generated anyway) and provides exactly-ordered, exactly-captured changes. The risk: the Debezium logical replication slot retains WAL if the Kafka consumer falls behind.

**Q: How do you monitor and alert on replication lag in production?**
For PostgreSQL: query `pg_stat_replication.replay_lag` from the primary every 30 seconds and expose it as a Prometheus gauge. Alert at lag > 30 seconds (warning) and > 5 minutes (critical). Also monitor slot lag via `pg_replication_slots.confirmed_flush_lsn` vs `pg_current_wal_lsn()`. For MySQL: use Prometheus `mysqld_exporter` which exports `mysql_slave_status_seconds_behind_master`. For automated alerting, set PagerDuty or Slack alerts on these metrics with severity thresholds matching your RPO requirements. Test alerting quarterly by deliberately pausing a replica.

---

## 13. Best Practices

- **Use synchronous replication for RPO=0 requirements** on a single standby in the same data center; accept the ~1–2ms latency addition.
- **Never run without a quorum DCS** for HA — a single Patroni agent without etcd can promote incorrectly during network partitions.
- **Drop replication slots immediately** when a replica is permanently decommissioned; never leave dangling slots.
- **Test failover quarterly** under realistic load — failover behavior under a busy buffer pool is different from failover on an idle test server.
- **Monitor replication lag with two signals**: time lag (seconds) for application impact, and WAL lag (bytes) for disk risk.
- **Use connection pooling (PgBouncer) in front of the primary** so that failover is transparent to the connection pool, not to every application connection.
- **Enable `wal_log_hints = on`** in PostgreSQL to allow `pg_rewind` — used by Patroni to resynchronize an old primary after failover without a full base backup.
- **Set `idle_in_transaction_session_timeout`** on the primary: long idle transactions block VACUUM and can cause replica recovery conflicts.

---

## 14. Case Study

**Scenario**: An e-commerce platform runs PostgreSQL on a single primary with two async replicas for reads. During Black Friday, the primary node's SSD fails. Unmonitored replication slots on both replicas have accumulated 50GB of WAL on the primary disk. The team has no automated HA.

**Timeline of the incident**:

```mermaid
timeline
    title Black Friday Primary Failure - Incident Timeline
    section Outage
        T+0 min : Primary SSD I/O errors, checksum failures begin
        T+5 min : Primary crashes - disk full from WAL + replica slots
        T+7 min : On-call engineer paged
    section Manual Failover
        T+20 min : Primary confirmed dead, replicas 45s behind
        T+35 min : Replica-1 promoted (pg_ctl promote)
        T+40 min : Application DNS updated to replica-1
        T+45 min : App reconnects - 45s of order data lost
    section Recovery
        T+2 hr : Replica-2 reconfigured to new primary
        T+8 hr : New SSD provisioned, base backup restored
```

**Total downtime**: 40 minutes. **Data loss**: 45 seconds of transactions.

**Post-mortem fixes**:
1. Installed Patroni + etcd (3-node); automated failover now takes 15–30 seconds
2. Set `max_slot_wal_keep_size = 10GB` in PostgreSQL 13+ to cap slot WAL retention
3. Added Prometheus alert on `pg_replication_slots` lag > 5GB
4. Added Prometheus alert on `pg_stat_replication.replay_lag > 30s`
5. Switched primary-to-replica1 to synchronous replication (RPO=0 for order data)
6. Scheduled monthly automated failover drills (chaos engineering, non-peak hours)

**Result after changes**: Next failure (3 months later, same cause: disk I/O error) triggered Patroni automated failover in 18 seconds with zero data loss (synchronous replication).
