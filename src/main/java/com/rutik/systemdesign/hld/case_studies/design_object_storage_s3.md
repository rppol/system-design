# System Design: Distributed Object Storage (S3-Style)

## Intuition

> **Design intuition**: An object storage system is what you get when you strip a filesystem down to its absolute essentials — `PUT(key, bytes)`, `GET(key)`, `DELETE(key)` — and then rebuild durability and scale from scratch on top of that minimal interface. There is no directory tree, no in-place edit, no `seek()`-and-overwrite; every object is an immutable blob identified by a flat `(bucket, key)` pair. That simplicity is the entire point: by refusing to support POSIX semantics (partial writes, file locks, hierarchical renames), the system can shard objects across tens of thousands of machines with almost no coordination, replicate or erasure-code each object independently, and scale a single bucket to trillions of keys without ever needing a global lock. Everything else — multipart upload, versioning, lifecycle tiering, strong consistency — is built on this one constraint.

**Key insight**: Two numbers dominate the entire design: **11 nines of durability (99.999999999%)** and **erasure-coding storage overhead of ~1.5x instead of replication's 3x**. Durability at that level cannot come from "store it on a disk that's backed up nightly" — it comes from mathematically guaranteeing that an object survives the *simultaneous* loss of several storage devices, racks, or even an entire availability zone. Reed-Solomon erasure coding (§4.2) achieves this for roughly half the storage cost of 3x replication, but the catch is that *reading back* an object after a failure requires reconstructing it from surviving shards — a CPU- and network-intensive "repair" operation that, if not carefully throttled, can itself become the outage (War Story 1, §9). The entire architecture — metadata index, placement groups, repair queues — exists to make that durability number true under continuous, low-grade hardware failure without ever making repair traffic visible to a user doing a `GET`.

---

## 1. Requirements Clarification

### Functional Requirements

- **`PUT object(bucket, key, bytes, metadata, tags)`**: store a blob of arbitrary bytes (0 bytes to multiple terabytes) under a `(bucket, key)` address, with optional user-defined metadata (key-value headers) and tags (for lifecycle/billing classification)
- **`GET object(bucket, key, [versionId], [range])`**: retrieve an object's bytes (optionally a specific version, optionally a byte range for partial reads — e.g., resuming a download or reading a chunk of a large file)
- **`DELETE object(bucket, key, [versionId])`**: remove an object (or a specific version); with versioning enabled, a "delete" without a version ID writes a **delete marker** rather than physically removing data
- **`LIST objects(bucket, prefix, delimiter)`**: enumerate keys in a bucket, optionally filtered by prefix and "directory-like" delimiter grouping — must scale to **buckets containing billions of keys** without full-bucket scans
- **Multipart upload**: for large objects (hundreds of MB to multiple TB), split the upload into independently-uploaded parts (5 MB - 5 GB each, up to 10,000 parts), each acknowledged with an ETag, and atomically "completed" into a single object once all parts are present
- **Versioning**: when enabled on a bucket, every `PUT` to an existing key creates a new version rather than overwriting; all versions remain individually addressable and listable
- **Object metadata and tags**: arbitrary user-defined key-value metadata (HTTP-header-style, e.g., `Content-Type`, `x-amz-meta-*`) and a small set of tags used for lifecycle rules and cost allocation
- **Access control**: bucket policies (JSON documents specifying which principals can perform which actions) and object ACLs (cross-ref [`../security_and_auth/README.md`](../security_and_auth/README.md)) — every request is authorized before any data path work begins
- **Lifecycle policies**: automatic transition of objects between storage tiers (hot -> infrequent-access -> archive) and automatic expiration, based on object age or tags (§4.5)

### Non-Functional Requirements

- **Durability: 99.999999999% (11 nines)** annually for a given object — translated, the expected annual loss rate is on the order of 1 object per 10 billion stored per year. This is a *data-loss* guarantee, distinct from availability.
- **Availability: 99.99%** (the "four nines") for the read/write API — roughly 52 minutes of downtime/year — distinct from durability; an object can be 100% durable (the bytes are safe on disk) while being temporarily unavailable (the service serving it is down)
- **Exabyte-scale**: the system must scale to **exabytes** of logical data and **hundreds of billions to trillions of objects**, across millions of buckets, with no single component (especially the metadata index) becoming a bottleneck at that scale
- **Object size range**: from **0 bytes** (zero-length "folder marker" objects, a common client convention) to **multiple terabytes** (via multipart upload) — no single API call ever transfers more than 5 GB (a single `PUT` or a single multipart part is capped at 5 GB)
- **Strong read-after-write consistency**: a successful `PUT` (or `DELETE`) must be immediately visible to *every* subsequent `GET`, `LIST`, and `HEAD` request, from any client, anywhere — including `GET`s for objects that did not previously exist (the historical "eventually consistent" model is explicitly called out in §4.4 as the thing this design improves on)
- **High throughput, read-skewed**: the system must sustain very high aggregate PUT/GET throughput (§2), with reads dramatically outnumbering writes in most workloads (data lake / media-serving access patterns)
- **Multi-AZ fault tolerance**: the loss of an entire availability zone (power, network, or facility failure) must not cause data loss and should not cause a user-visible outage for objects placed with AZ-aware redundancy

### Out of Scope

- **POSIX filesystem semantics** — there is no concept of a directory that "contains" objects in the filesystem sense (a "folder" is purely a UI/listing convention based on key prefixes and delimiters), no file locks, no `mmap`, no partial in-place writes. An application that needs POSIX semantics mounts a separate network filesystem (EFS/NFS-style) layered *on top of* object storage, which is a distinct system not designed here.
- **In-place object edits** — objects are immutable once written. "Overwriting" `key=foo` means writing a brand-new object (and, with versioning, a new version) that happens to share the same key; there is no `seek()`-and-modify operation, no append, no partial-write-then-read-back-the-same-object-handle.
- **Transactional multi-object operations** — there is no cross-object ACID transaction (`PUT a AND PUT b atomically`). Applications needing that build it at the application layer (cross-ref [`../distributed_transactions/README.md`](../distributed_transactions/README.md)) using patterns like write-then-pointer-flip.

---

## 2. Scale Estimation

### Object Count and Logical Data Volume

- Target: **100 billion objects** stored across all buckets and customers
- Average object size: **512 KB** (a realistic blended average across small JSON/log objects, medium images, and large media/backup files — real-world distributions are heavily bimodal, with a huge population of small objects and a small population of huge ones, but 256 KB-1 MB is a reasonable working average)
- Total logical data: `100,000,000,000 x 512 KB` = **~51.2 PB** at this average — but real exabyte-scale deployments skew toward **1 EB+** once large-object workloads (backups, video, data lakes, with average sizes in the tens of MB) are weighted in. Use **1 exabyte (1,000 PB)** as the working logical-data target for capacity planning (§10).

### Object Size Distribution

The "512 KB average" hides a heavily **bimodal** distribution that matters for both chunking (§4.2) and metadata sizing (below):

| Size Band | Example Workloads | Approx. Share of Object *Count* | Approx. Share of Logical *Bytes* |
|---|---|---|---|
| < 16 KB | Config files, small JSON records, thumbnails | ~50% | < 1% |
| 16 KB - 1 MB | Images, log fragments, API response caches | ~35% | ~5% |
| 1 MB - 100 MB | Documents, mid-size media, ML feature shards | ~12% | ~25% |
| 100 MB - 5 GB | Video segments, dataset shards, single-part backups | ~2.5% | ~30% |
| > 5 GB (multipart, §4.3) | VM images, database dumps, training-data archives, multi-TB backups | ~0.5% | ~39% |

The practical consequence: **roughly half of all objects are smaller than a single 64 MB erasure-coding chunk (§4.2)** — for these, "one object = one chunk = 9 shards" and the 1.5x overhead applies directly. The largest 0.5% of objects (by count) account for nearly 40% of total bytes and are *always* multipart — this is the tail that makes the §10 capacity numbers dominated by large-object storage even though small objects dominate the metadata-index row count (§4.1, §10).

### Ingestion and Read Rates

- Target ingestion: **1,000,000 PUTs/sec** sustained globally across the fleet (aggregate across all customers/buckets — a single bucket sees a tiny fraction of this)
- Read:write ratio of roughly **10:1** (typical for content-serving and data-lake workloads — write once, read many times during the "hot" period of an object's life) -> **10,000,000 GETs/sec** sustained globally
- At 512 KB average object size: PUT bandwidth = `1,000,000 x 512 KB` ~= **512 GB/sec** ingest; GET bandwidth = `10,000,000 x 512 KB` ~= **~5.1 TB/sec** egress (before CDN offload — in practice a CDN absorbs the majority of GET traffic for hot objects, cross-ref [`../cdn/README.md`](../cdn/README.md))

### Multipart Upload Volume

- Large-object uploads (backups, video masters, ML training datasets) use multipart upload; a 1 TB object split into 5 GB parts requires **200 parts** (well under the 10,000-part limit)
- Estimate **1% of PUTs** are multipart (10,000 multipart-initiations/sec), averaging **50 parts/upload** -> 500,000 part-uploads/sec contributing to overall PUT throughput

### Metadata Index Sizing (Preview — full math in §10)

- Each object's metadata record (bucket, key, version ID, size, ETag, content-type, storage-class, ACL pointer, shard-location list) is roughly **500 bytes - 1 KB**
- At 100 billion objects x ~1 KB/record (including versioning overhead) ~= **~100 TB of metadata** — small relative to 1 EB of object data (a ~0.01% metadata-to-data ratio), but **100 TB across 100 billion keys is itself a massive distributed-database problem**, which is why §4.1 treats the metadata index as a first-class distributed system in its own right (cross-ref [`./design_key_value_store.md`](./design_key_value_store.md))

---

## 3. High-Level Architecture

```
                                +----------------------+
                                |       Clients         |
                                |  (SDKs, CLI, browsers |
                                |   via REST/HTTPS)      |
                                +-----------+-----------+
                                            |
                                            v
                            +-------------------------------+
                            |   API / Gateway Layer (S3 REST) |
                            |  - AuthN/AuthZ (bucket policy,  |
                            |    ACLs, sig v4) (§7, sec&auth)  |
                            |  - request routing, throttling   |
                            +---------------+-----------------+
                                            |
                  +--------------------------+--------------------------+
                  |                                                      |
                  v                                                      v
   +-------------------------------+                    +-------------------------------+
   |     Metadata Service            |                    |   Placement / Chunking Service |
   |  (bucket,key,version) ->         |<------------------|   - splits object into chunks   |
   |    {objectId, size, ETag,        |   register shard   |   - erasure-encodes each chunk  |
   |     storageClass, shard          |   locations after   |     (6 data + 3 parity, §4.2)   |
   |     locations, ACL ptr}           |   placement          |   - selects placement group    |
   |  Sharded KV index (§4.1,          |                    |     across AZs/racks            |
   |  cross-ref design_kv_store.md)    |                    +---------------+-----------------+
   +---------------+-----------------+                                    |
                   ^                                                      v
                   | strong read-after-write              +-------------------------------+
                   | (write metadata BEFORE ack, §4.4)     |     Storage Nodes               |
                   |                                       |  organized into Placement       |
                   +---------------------------------------+  Groups spanning 3 AZs;          |
                                                            |  each shard on a distinct        |
                                                            |  node/rack/AZ                    |
                                                            +-------------------------------+

PUT path:
  client -> gateway (authz) -> chunk object -> erasure-encode each chunk (6+3) ->
  write 9 shards to 9 storage nodes across 3 AZs (3 shards/AZ) -> on >= write-quorum
  acks -> write metadata record (object -> shard locations) -> ack client

GET path:
  client -> gateway (authz) -> metadata lookup (bucket,key[,version]) -> shard
  locations -> fetch >= 6 of 9 shards (any 6 data-or-parity shards suffice) ->
  reconstruct chunk(s) -> stream to client
```

### Request Flow

1. **Every request** (PUT/GET/DELETE/LIST) first passes through the **API/Gateway layer** (§4 implicit, §7), which authenticates the caller (SigV4-style request signing), evaluates the bucket policy and any object ACL (cross-ref [`../security_and_auth/README.md`](../security_and_auth/README.md)), and applies per-bucket/per-account rate limiting (cross-ref [`../rate_limiting/README.md`](../rate_limiting/README.md)).
2. **PUT**: the gateway streams the object body to the **Placement/Chunking Service** (§4.2), which splits the object into fixed-size chunks (e.g., 64 MB), erasure-encodes each chunk into 9 shards (6 data + 3 parity), and writes the 9 shards to 9 distinct storage nodes selected from a **placement group** that spans 3 availability zones (3 shards/AZ). Once a write-quorum of shards is durably persisted, the **Metadata Service** (§4.1) is updated with the new object's location map — *and only then* is the client acknowledged, which is the architectural core of strong read-after-write consistency (§4.4).
3. **GET**: the gateway looks up `(bucket, key[, version])` in the **Metadata Service** to get the chunk/shard location map, fetches any 6 of the 9 shards per chunk from storage nodes (the erasure-coding scheme tolerates up to 3 simultaneous shard losses, §4.2), reconstructs each chunk, and streams the reassembled object to the client.
4. **DELETE**: with versioning off, the metadata record is marked with a tombstone and the underlying shards are scheduled for asynchronous garbage collection (§4.6); with versioning on, a delete marker becomes the new "current" version while prior versions remain fully intact and listable.
5. **LIST**: served entirely from the **Metadata Service** (§4.1) — `LIST bucket, prefix=X` is a range scan over keys sorted lexicographically within the bucket's metadata partition, never touching storage nodes.
6. **Multipart upload** (§4.3): each part is placed and erasure-coded independently as it arrives (effectively a small PUT per part); `CompleteMultipartUpload` validates the client-supplied ordered list of part ETags against the Metadata Service's record of received parts, then atomically creates the single logical-object metadata record referencing all part-chunks in order.

### Chunking and Erasure-Coding Layout (Zoomed In)

The diagram above shows the *service*-level path; this diagram shows what happens to **one object's bytes** as they flow from the gateway to storage, which is the picture worth drawing on a whiteboard when asked "where do the 9 shards actually live?":

```
Object bytes (e.g., a 200 MB file)
        |
        v
+----------------------------------------------------------+
|  Split into fixed-size CHUNKS (e.g., 64 MB each)           |
|  200 MB -> chunk0 (64MB), chunk1 (64MB), chunk2 (64MB),    |
|            chunk3 (8MB, final partial chunk)                |
+----------------------------------------------------------+
        |
        v   (per chunk, independently)
+----------------------------------------------------------+
|  Reed-Solomon encode: split chunk into 6 DATA shards        |
|  (~10.7MB each for a 64MB chunk) + compute 3 PARITY shards  |
|  D1 D2 D3 D4 D5 D6  P1 P2 P3   <- 9 shards, ~10.7MB each     |
+----------------------------------------------------------+
        |
        v   (placement group selects 9 storage nodes across 3 AZs)
+-----------------------+  +-----------------------+  +-----------------------+
|        AZ-1            |  |        AZ-2            |  |        AZ-3            |
|  Rack A: D1             |  |  Rack D: D4             |  |  Rack G: P1             |
|  Rack B: D2             |  |  Rack E: D5             |  |  Rack H: P2             |
|  Rack C: D3             |  |  Rack F: D6             |  |  Rack I: P3             |
+-----------------------+  +-----------------------+  +-----------------------+

A chunk survives the loss of ANY 3 of these 9 shards (e.g., all of AZ-3,
or one rack from each AZ) - reconstruction reads any 6 survivors.
```

A 200 MB object thus produces **4 chunks x 9 shards = 36 shard-write RPCs** fanned out across the placement group — but these fan out in parallel, and the client only waits for the write-quorum (§4.4) across each chunk's 9 shards, not for all 36 sequentially. The metadata record (§4.1) stores, per chunk, a reference to which 9 (node, shard-index) pairs hold that chunk's shards — this `chunkLocations` list is exactly what the GET path's "shard locations" lookup in the diagram above resolves.

---

## 4. Component Deep Dives

### 4.1 Metadata Index — `(bucket, key) -> Object Location`

The metadata index is the system's "phone book": every read, write, and list operation begins or ends here. Structurally, it is a **distributed sorted key-value store** — the exact mechanics of consistent hashing, replication, and quorum reads/writes for a KV store of this kind are covered in depth in [`./design_key_value_store.md`](./design_key_value_store.md) (§4.1-§4.5 there); this section focuses on what's *specific* to object storage: the schema, the key design, and why **lexicographic key ordering within a bucket** (not hash-based distribution of the *whole* keyspace) is the dominant design constraint.

**Key design**: the index's primary key is `(bucketId, objectKey, versionId)`, ordered lexicographically by `objectKey` *within* a bucket's partition. This ordering is what makes `LIST bucket, prefix=X` a cheap range scan instead of a full scan — but it's also exactly the property that creates **sequential-key hotspotting** when object keys themselves are sequential (timestamps, auto-incrementing IDs), because all new writes land at the lexicographic "end" of one partition (War Story 2, §9). The index is therefore **sharded by `bucketId` (or a hash of it for very large buckets)**, with each shard internally maintaining the lexicographic order needed for prefix listing — the sharding key and the sort key are deliberately different, which is the same tension [`./design_distributed_unique_id.md`](./design_distributed_unique_id.md) discusses for time-ordered IDs (§9 there) applied to a different layer of the stack.

**Record schema** (per `(bucket, key, version)`):

| Field | Purpose |
|---|---|
| `objectId` | Internal immutable identifier, distinct from the user-facing key (allows key renames/versioning without rewriting data) |
| `size`, `etag` | Object size in bytes; ETag (MD5 of single-part objects, or a composite hash for multipart, §4.3) |
| `storageClass` | Current tier — STANDARD, INFREQUENT_ACCESS, ARCHIVE (§4.5) |
| `chunkLocations` | Ordered list of `{chunkId -> [9 shard locations across 3 AZs]}` (§4.2) |
| `aclPointer` | Reference to the effective ACL/policy (§7) |
| `isLatest`, `deleteMarker` | Versioning flags — `isLatest=true` marks the version returned by an unversioned `GET` |
| `createdAt`, `lastModified` | Timestamps for lifecycle-policy evaluation (§4.5) |

```java
package com.rutik.systemdesign.hld.case_studies.objectstore;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A single-shard view of the metadata index: (bucket, key, version) -> ObjectMetadata.
 * In production this shard is one partition of a much larger sharded KV store
 * (cross-ref design_key_value_store.md for the sharding/replication/quorum layer);
 * this class focuses on the schema and the operations object storage needs:
 * versioned put/get/delete and prefix-ordered listing.
 */
public class ObjectMetadataIndex {

    // Keyed by bucketId -> (objectKey -> ordered versions, newest first)
    // ConcurrentSkipListMap keeps keys lexicographically sorted for prefix scans.
    private final Map<String, ConcurrentSkipListMap<String, Deque<ObjectMetadata>>> buckets =
        new ConcurrentHashMap<>();

    public void put(String bucketId, String key, ObjectMetadata metadata, boolean versioningEnabled) {
        ConcurrentSkipListMap<String, Deque<ObjectMetadata>> bucket =
            buckets.computeIfAbsent(bucketId, b -> new ConcurrentSkipListMap<>());

        Deque<ObjectMetadata> versions = bucket.computeIfAbsent(key, k -> new ArrayDeque<>());

        if (!versioningEnabled) {
            // Overwrite semantics: only one logical version exists; the old
            // shard locations are scheduled for GC (§4.6), not deleted inline.
            versions.clear();
        } else if (!versions.isEmpty()) {
            // Demote the previously-current version.
            versions.peekFirst().setLatest(false);
        }
        metadata.setLatest(true);
        versions.addFirst(metadata); // newest version first
    }

    /** Returns the current ("latest", non-deleted) version, or a specific versionId if given. */
    public Optional<ObjectMetadata> get(String bucketId, String key, String versionId) {
        ConcurrentSkipListMap<String, Deque<ObjectMetadata>> bucket = buckets.get(bucketId);
        if (bucket == null) return Optional.empty();
        Deque<ObjectMetadata> versions = bucket.get(key);
        if (versions == null || versions.isEmpty()) return Optional.empty();

        if (versionId == null) {
            ObjectMetadata current = versions.peekFirst();
            return (current.isDeleteMarker()) ? Optional.empty() : Optional.of(current);
        }
        return versions.stream()
            .filter(v -> versionId.equals(v.getVersionId()))
            .findFirst();
    }

    /** Soft-delete: writes a delete marker as the new "latest" version (versioned buckets),
     *  or removes the single current version and tombstones it (unversioned buckets). */
    public void delete(String bucketId, String key, boolean versioningEnabled) {
        ConcurrentSkipListMap<String, Deque<ObjectMetadata>> bucket = buckets.get(bucketId);
        if (bucket == null) return;
        Deque<ObjectMetadata> versions = bucket.get(key);
        if (versions == null || versions.isEmpty()) return;

        if (versioningEnabled) {
            versions.peekFirst().setLatest(false);
            ObjectMetadata marker = ObjectMetadata.deleteMarker(UUID.randomUUID().toString());
            marker.setLatest(true);
            versions.addFirst(marker);
        } else {
            ObjectMetadata current = versions.removeFirst();
            current.setPendingGc(true); // §4.6 reclaims the underlying shards async
        }
    }

    /**
     * LIST objects(bucket, prefix, maxKeys): a lexicographic range scan over the
     * bucket's sorted keyspace, starting at `prefix` and stopping once a key no
     * longer starts with `prefix` or `maxKeys` results have been collected.
     * O(maxKeys + log(N)) — never a full-bucket scan, even for billions of keys.
     */
    public List<ObjectMetadata> listByPrefix(String bucketId, String prefix, int maxKeys) {
        ConcurrentSkipListMap<String, Deque<ObjectMetadata>> bucket = buckets.get(bucketId);
        if (bucket == null) return List.of();

        List<ObjectMetadata> results = new ArrayList<>(maxKeys);
        for (Map.Entry<String, Deque<ObjectMetadata>> entry : bucket.tailMap(prefix).entrySet()) {
            if (!entry.getKey().startsWith(prefix)) break; // past the prefix range - stop
            ObjectMetadata current = entry.getValue().peekFirst();
            if (current != null && !current.isDeleteMarker()) {
                results.add(current);
            }
            if (results.size() >= maxKeys) break;
        }
        return results;
    }

    public static class ObjectMetadata {
        private final String objectId;
        private final String versionId;
        private long size;
        private String etag;
        private String storageClass = "STANDARD";
        private List<String> chunkLocations = new ArrayList<>(); // §4.2 shard refs
        private boolean latest;
        private boolean deleteMarker;
        private boolean pendingGc;
        private final long createdAt = System.currentTimeMillis();

        private ObjectMetadata(String objectId, String versionId, boolean deleteMarker) {
            this.objectId = objectId;
            this.versionId = versionId;
            this.deleteMarker = deleteMarker;
        }

        public static ObjectMetadata of(String objectId, String versionId) {
            return new ObjectMetadata(objectId, versionId, false);
        }

        public static ObjectMetadata deleteMarker(String versionId) {
            return new ObjectMetadata(null, versionId, true);
        }

        public String getVersionId() { return versionId; }
        public boolean isDeleteMarker() { return deleteMarker; }
        public boolean isLatest() { return latest; }
        public void setLatest(boolean latest) { this.latest = latest; }
        public void setPendingGc(boolean pendingGc) { this.pendingGc = pendingGc; }
        public long getCreatedAt() { return createdAt; }
    }
}
```

The critical property this class hints at but a single shard can't show: **`put()` is called synchronously, in the write path, before the client is acknowledged** (§4.4). This is the architectural decision that makes strong read-after-write consistency possible — the metadata write *is* the commit point, not an asynchronous side effect.

### 4.2 Durability — Erasure Coding vs. Replication

Durability comes from **redundancy**: storing extra copies or extra parity so that the loss of some subset of storage devices doesn't lose data. Two schemes dominate:

**3x Replication**: store 3 full copies of every object on 3 different nodes (typically one per AZ). Storage overhead = **3.0x** the logical data size. Tolerates the loss of any 2 of the 3 copies.

**Reed-Solomon Erasure Coding (6 data + 3 parity, "6+3")**: split each object chunk into 6 equal **data shards**, compute 3 **parity shards** via Reed-Solomon polynomial arithmetic over the 6 data shards, and store all 9 shards on 9 different nodes/racks/AZs. Storage overhead = `9/6` = **1.5x** the logical data size — **half the overhead of 3x replication** — while still tolerating the loss of **any 3 of the 9 shards** (any 6 of the 9 are sufficient to reconstruct the original chunk, whether those 6 are data shards, parity shards, or a mix).

#### Working the Durability Math

Assume each shard (a disk/node) has an **annual failure rate (AFR) of 1%** (a realistic figure for modern enterprise HDDs) and failures are independent (placement groups, below, are what make this independence assumption defensible).

- **3x replication** loses an object only if **all 3 replicas** fail within the repair window. P(lose all 3) ~= `0.01^3` = `10^-6` per object per year — "6 nines," i.e., 99.9999% durability, *before* accounting for repair-time reduction of this probability.
- **6+3 erasure coding** loses an object only if **4 or more of the 9 shards** fail simultaneously (since any 6 survivors suffice). P(>=4 of 9 fail) is a tail of the binomial distribution with `n=9, p=0.01` — dominated by the `k=4` term, `C(9,4) x 0.01^4 x 0.99^5` ~= `126 x 10^-8 x 0.95` ~= `1.2 x 10^-6`. This is in the *same order of magnitude* as 3x replication's `10^-6`, **despite using half the storage** — the extra shards (9 vs. 3) and the "any 6 of 9" threshold more than compensate for needing more simultaneous failures to cause loss.
- Real systems push this further with **faster repair** (§9, War Story 1): the moment one shard is lost, the system reconstructs a replacement from the surviving 8 shards and writes a fresh 9th shard — shrinking the window during which *additional* failures could cause permanent loss from "until the disk is replaced" (days) to "until the network can stream a reconstruction" (minutes to hours for a single chunk). This repair-time reduction is what pushes the realized durability from "~6 nines of raw math" to the advertised **11 nines** — the published number already accounts for continuous background repair, not just the static combinatorics above.

#### Storage Cost Comparison

| Scheme | Storage Overhead | Simultaneous Failures Tolerated | Repair Cost on Failure |
|---|---|---|---|
| 3x Replication | 3.0x | 2 of 3 | Cheap — copy one full replica |
| 6+3 Erasure Coding | 1.5x | 3 of 9 | Expensive — read 6 shards, compute Reed-Solomon reconstruction, write 1-3 new shards |
| 10+4 Erasure Coding (wider) | 1.4x | 4 of 14 | More expensive still — wider reconstruction reads, but marginally better overhead |

At **1 EB of logical data** (§2), the difference between 1.5x and 3.0x overhead is `1.5 EB` vs. `3.0 EB` of **raw storage** — 1.5 EB saved, which at typical cloud storage hardware costs (low single-digit cents/GB/month for raw capacity) is on the order of **tens of millions of dollars per month** at exabyte scale. This is *the* economic argument for erasure coding over replication in object storage, and it's why every major provider (§6) uses erasure coding for its standard storage tier.

#### A Minimal XOR-Based Illustration (1 Parity Shard)

Full Reed-Solomon uses Galois-field polynomial arithmetic to generate multiple independent parity shards from any number of data shards. A single-parity-shard scheme using plain **XOR** illustrates the core principle (tolerating 1 failure from N+1 shards) without the field-arithmetic complexity:

```java
package com.rutik.systemdesign.hld.case_studies.objectstore;

/**
 * Minimal illustration of erasure coding using XOR parity: N data shards
 * produce 1 parity shard (XOR of all data shards), tolerating the loss of
 * ANY ONE of the N+1 shards. Real systems use Reed-Solomon over a Galois
 * field to support MULTIPLE parity shards (e.g., 6 data + 3 parity
 * tolerating 3 losses) - this XOR version generalizes the "any 1 of N+1"
 * case only, but shows the reconstruction principle identically.
 */
public class XorParityEncoder {

    /** Computes the parity shard as the byte-wise XOR of all data shards. Shards must be equal length. */
    public byte[] computeParity(byte[][] dataShards) {
        int shardLength = dataShards[0].length;
        byte[] parity = new byte[shardLength];
        for (byte[] shard : dataShards) {
            for (int i = 0; i < shardLength; i++) {
                parity[i] ^= shard[i];
            }
        }
        return parity;
    }

    /**
     * Reconstructs a single missing shard (data or parity) given all OTHER
     * shards (data shards + the parity shard), by XOR-ing everything else
     * together. XOR is its own inverse: A xor B xor C xor parity(A,B,C) = 0
     * for any one operand removed, so XOR-ing the survivors recovers the
     * missing one.
     */
    public byte[] reconstructMissingShard(byte[][] survivingShards, int shardLength) {
        byte[] reconstructed = new byte[shardLength];
        for (byte[] shard : survivingShards) {
            for (int i = 0; i < shardLength; i++) {
                reconstructed[i] ^= shard[i];
            }
        }
        return reconstructed;
    }

    /** Demonstrates: encode 3 data shards -> 1 parity, drop one, reconstruct it. */
    public static void main(String[] args) {
        XorParityEncoder encoder = new XorParityEncoder();
        byte[] d1 = {0x01, 0x02, 0x03};
        byte[] d2 = {0x10, 0x20, 0x30};
        byte[] d3 = {0x05, 0x06, 0x07};
        byte[] parity = encoder.computeParity(new byte[][]{d1, d2, d3});

        // Simulate losing d2: reconstruct it from {d1, d3, parity}
        byte[] reconstructedD2 = encoder.reconstructMissingShard(new byte[][]{d1, d3, parity}, 3);
        // reconstructedD2 now equals the original d2, byte-for-byte.
    }
}
```

The production 6+3 scheme replaces this XOR with Reed-Solomon's Galois-field matrix arithmetic so that **any 3** of 9 shards (not just any 1 of 4) can be lost and reconstructed — the math generalizes, but the *principle* (redundant shards encode enough information to recover missing ones, and reconstruction reads the survivors and recomputes) is identical.

#### Placement Groups: Making "Independent Failure" True

The durability math above assumes shard failures are **independent** — but 9 shards stored on 9 disks in the same rack all lose power together if that rack's PDU fails, which makes them perfectly *correlated*, not independent. **Placement groups** are the mechanism that makes the independence assumption hold: for a 6+3 scheme, the 9 shards of a chunk are distributed as **3 shards per AZ across 3 AZs**, and within an AZ, across **distinct racks/power domains**. A single rack failure costs at most 1-2 shards per chunk (well within the "any 3 of 9" tolerance); a full AZ failure costs exactly 3 shards per chunk (exactly at the tolerance boundary, which is why the scheme is *designed* around 3 AZs x 3 shards, not an arbitrary 9-way spread).

### 4.3 Multipart Upload Protocol

Large objects (the multi-TB end of §1's size range) cannot be uploaded as a single HTTP request — connection failures mid-upload would require restarting from byte zero, and a single chunking/erasure-coding pipeline (§4.2) handling a multi-TB stream end-to-end has no natural checkpoints. **Multipart upload** solves both: the client splits the object into parts (5 MB - 5 GB each, up to 10,000 parts), uploads each part independently (in parallel, with independent retry), and finally sends an ordered manifest of part ETags that the service validates and assembles into one logical object.

```java
package com.rutik.systemdesign.hld.case_studies.objectstore;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the multipart upload lifecycle: initiate -> uploadPart (N times,
 * any order, any parallelism) -> completeMultipartUpload (validates the
 * client's ordered manifest against what the service actually received).
 */
public class MultipartUploadManager {

    private static final long MIN_PART_SIZE = 5L * 1024 * 1024;       // 5 MB
    private static final long MAX_PART_SIZE = 5L * 1024 * 1024 * 1024; // 5 GB
    private static final int MAX_PARTS = 10_000;

    // uploadId -> (partNumber -> PartRecord)
    private final Map<String, Map<Integer, PartRecord>> activeUploads = new ConcurrentHashMap<>();
    private final Map<String, UploadContext> uploadContexts = new ConcurrentHashMap<>();
    private final ChunkingService chunkingService; // §4.2 - erasure-encodes and places each part

    public MultipartUploadManager(ChunkingService chunkingService) {
        this.chunkingService = chunkingService;
    }

    /** Step 1: client calls this once to obtain an uploadId for a (bucket, key). */
    public String initiateUpload(String bucket, String key, Map<String, String> userMetadata) {
        String uploadId = UUID.randomUUID().toString();
        activeUploads.put(uploadId, new ConcurrentHashMap<>());
        uploadContexts.put(uploadId, new UploadContext(bucket, key, userMetadata));
        return uploadId;
    }

    /**
     * Step 2: client uploads each part independently (any order, any
     * parallelism). Each part is immediately chunked+erasure-coded+placed
     * (§4.2) - the part's bytes are durable the moment this returns, well
     * before completeMultipartUpload is called.
     */
    public String uploadPart(String uploadId, int partNumber, byte[] data) {
        if (partNumber < 1 || partNumber > MAX_PARTS) {
            throw new IllegalArgumentException("partNumber must be 1.." + MAX_PARTS);
        }
        if (data.length < MIN_PART_SIZE && !isLastPartHeuristic(data)) {
            // Real systems can't know "is this the last part?" at upload time;
            // S3's actual rule is: only the LAST part may be < 5MB. Validation
            // of "exactly one undersized part, and it's the highest partNumber"
            // happens in completeMultipartUpload, not here.
        }
        if (data.length > MAX_PART_SIZE) {
            throw new IllegalArgumentException("part exceeds 5GB max part size");
        }

        String etag = md5Hex(data);
        ChunkPlacement placement = chunkingService.encodeAndPlace(data); // §4.2

        Map<Integer, PartRecord> parts = activeUploads.get(uploadId);
        if (parts == null) {
            throw new IllegalStateException("unknown or already-completed uploadId: " + uploadId);
        }
        parts.put(partNumber, new PartRecord(partNumber, etag, data.length, placement));
        return etag; // client must record this ETag for the completion manifest
    }

    /**
     * Step 3: client sends an ORDERED manifest of (partNumber -> ETag) pairs.
     * The service validates: (a) every referenced part was actually received,
     * (b) ETags match exactly (detects corruption/retried-with-different-data
     * bugs), (c) only the final part may be under the 5MB minimum, then
     * assembles the final ObjectMetadata referencing all parts' chunk placements
     * in order.
     */
    public ObjectMetadataIndex.ObjectMetadata completeMultipartUpload(
            String uploadId, List<PartManifestEntry> orderedManifest) {

        Map<Integer, PartRecord> receivedParts = activeUploads.get(uploadId);
        if (receivedParts == null) {
            throw new IllegalStateException("unknown or already-completed uploadId: " + uploadId);
        }
        if (orderedManifest.isEmpty()) {
            throw new IllegalArgumentException("manifest must contain at least one part");
        }

        List<PartRecord> orderedParts = new ArrayList<>(orderedManifest.size());
        long totalSize = 0;
        for (int i = 0; i < orderedManifest.size(); i++) {
            PartManifestEntry entry = orderedManifest.get(i);
            PartRecord received = receivedParts.get(entry.partNumber());
            if (received == null) {
                throw new IllegalStateException("part " + entry.partNumber() + " was never uploaded");
            }
            if (!received.etag().equals(entry.etag())) {
                throw new IllegalStateException(
                    "ETag mismatch for part " + entry.partNumber() +
                    ": client manifest says " + entry.etag() + ", server has " + received.etag());
            }
            boolean isLastPart = (i == orderedManifest.size() - 1);
            if (!isLastPart && received.sizeBytes() < MIN_PART_SIZE) {
                throw new IllegalStateException(
                    "part " + entry.partNumber() + " is below the 5MB minimum and is not the last part");
            }
            orderedParts.add(received);
            totalSize += received.sizeBytes();
        }

        UploadContext ctx = uploadContexts.get(uploadId);
        String compositeEtag = computeCompositeEtag(orderedParts); // ETag-of-ETags, marks it multipart

        ObjectMetadataIndex.ObjectMetadata result =
            ObjectMetadataIndex.ObjectMetadata.of(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        // (in a full implementation: result.size = totalSize; result.etag = compositeEtag;
        //  result.chunkLocations = orderedParts.stream().map(p -> p.placement().chunkRef())...)

        activeUploads.remove(uploadId);
        uploadContexts.remove(uploadId);
        return result;
    }

    /** Composite ETag: S3's real convention is MD5(concat(MD5(part_i))) + "-" + partCount,
     *  which is why multipart object ETags are NOT a valid MD5 of the full object body. */
    private String computeCompositeEtag(List<PartRecord> parts) {
        StringBuilder concatenatedHashes = new StringBuilder();
        for (PartRecord part : parts) {
            concatenatedHashes.append(part.etag());
        }
        return md5Hex(concatenatedHashes.toString().getBytes()) + "-" + parts.size();
    }

    private boolean isLastPartHeuristic(byte[] data) {
        return false; // placeholder - real validation deferred to completeMultipartUpload
    }

    private String md5Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record PartManifestEntry(int partNumber, String etag) {}
    public record PartRecord(int partNumber, String etag, long sizeBytes, ChunkPlacement placement) {}
    public record UploadContext(String bucket, String key, Map<String, String> userMetadata) {}
    public record ChunkPlacement(String chunkRef) {}
    public interface ChunkingService {
        ChunkPlacement encodeAndPlace(byte[] data); // erasure-encode (§4.2) + write 9 shards
    }
}
```

**What happens if one part fails?** Nothing is rolled back — `uploadPart` failures are simply retried by the client for that part number alone (each part upload is independently idempotent: re-uploading part 5 with the same bytes produces the same ETag and simply overwrites the prior attempt's placement). If the client abandons the upload entirely (crashes, gives up), the uploaded-but-never-completed parts become **orphaned storage** until an **AbortMultipartUpload lifecycle rule** (§4.5) reclaims them — typically configured to abort incomplete multipart uploads after 7 days, feeding into the same garbage-collection pipeline as deleted objects (§4.6).

### 4.4 Strong Read-After-Write Consistency — The Architectural Shift

Historically (pre-2020 for Amazon S3, and still true for some object stores), object storage offered **eventual consistency**: after a `PUT`, a `GET` for the same key might return the *old* version (or, for a brand-new key, a 404) for some window of time, because the system replicated the write to multiple internal locations asynchronously and a `GET` could be served by a replica that hadn't yet received the update. Applications worked around this with patterns like "write, then poll until you read back what you wrote" or by avoiding read-after-write patterns entirely (write to a new key, never overwrite).

**The architectural shift to strong consistency** is fundamentally a shift in **what the metadata index is, and when it's updated**:

- **Old model**: the "source of truth" for an object's existence was distributed across the storage replicas themselves, with the metadata layer (if any) acting as a cache or index that could lag behind. A `GET` might consult a metadata cache that hadn't yet been invalidated/updated, or might land on a storage replica that hadn't yet received the replicated write.
- **New model** (this design, §3-§4.1): the **metadata index is the single source of truth** for "does this object exist, and where are its shards." A `PUT` is acknowledged to the client **only after** (a) a write-quorum of erasure-coded shards are durably persisted across the placement group, **and** (b) the metadata record pointing to those shards is durably committed to the metadata index (§4.1). Since *every* `GET`/`LIST`/`HEAD` request **must** consult the metadata index first (there is no path that bypasses it), and the metadata index itself uses a strongly-consistent replication protocol for its own writes (consensus-based, cross-ref [`./design_key_value_store.md`](./design_key_value_store.md) for the underlying replication mechanics), any request arriving after the `PUT`'s acknowledgment is *guaranteed* to see the new metadata record — there is no second, independently-replicated "existence" signal that could lag.

The key reframing for an interview: **strong consistency here is not "we made replication synchronous everywhere"** (that would be prohibitively slow for object *data*, which can be gigabytes) — **it's "we made the single small metadata record that every read path depends on synchronously consistent, and made every read path depend on it."** Object *data* shards can still be written with relaxed durability-vs-latency tradeoffs (e.g., acknowledging after a write-quorum of 6-of-9 shards rather than all 9, with the remaining shards catching up asynchronously) without affecting read-after-write consistency for *existence and location* — a `GET` that follows the metadata pointer to a chunk whose 9th shard hasn't landed yet still finds 6+ shards, which is sufficient for reconstruction (§4.2). Consistency and durability-completion are decoupled.

### 4.5 Storage Tiering and Lifecycle Policies

Not all objects are accessed equally over their lifetime — a log file or a backup snapshot is often write-once, read-rarely-if-ever after the first few days. **Storage classes** let a bucket's lifecycle policy automatically move objects to cheaper tiers as they age, trading retrieval latency/cost for storage cost:

| Tier | Typical Use | Retrieval Latency | Relative Storage Cost | Relative Retrieval Cost |
|---|---|---|---|---|
| **Standard (hot)** | Actively-served content, frequently read | Milliseconds | 1.0x (baseline) | Low |
| **Infrequent Access (IA)** | Backups, older logs, monthly reports | Milliseconds (same as Standard) | ~0.5x | Higher per-GB retrieval fee |
| **Archive (Glacier-class)** | Compliance archives, cold backups, rarely-touched data | **Minutes to hours** (requires an explicit "restore" request before `GET` succeeds) | ~0.1-0.25x | Highest per-GB retrieval fee, plus the restore-and-wait workflow |

A **lifecycle policy** is a per-bucket (or per-prefix, or per-tag) rule like "transition objects to IA after 30 days, transition to Archive after 90 days, expire (delete) after 365 days." The lifecycle engine runs as a background scan over the metadata index (§4.1) — for each object whose `lastModified` (or a tag-based age) crosses a threshold, it (a) for IA transitions, simply updates the `storageClass` field and re-places the object's shards onto IA-tier storage nodes (cheaper, often denser/slower drives, but still using the same erasure-coding scheme since IA retrieval latency must match Standard); (b) for Archive transitions, re-encodes/re-places onto archive-tier media (which may use a *wider* erasure-coding scheme, e.g., 10+4, since archive data is read so rarely that the extra reconstruction cost on the rare read is an acceptable tradeoff for even-lower storage overhead) and marks the object as requiring an explicit restore before `GET`; (c) for expiration, triggers the same delete-and-GC path as a user-initiated `DELETE` (§4.6).

The Archive tier's "minutes to hours" retrieval latency is the single biggest UX difference exposed to applications — a `GET` on an archived object doesn't return data; it either errors (object not yet restored) or the application must first call a `RestoreObject` API, wait for an asynchronous job to re-place the object's shards onto Standard/IA-tier storage, and then retry the `GET`. This asymmetry (instant write, slow read after archival) is unusual enough that it's a common source of application bugs when teams move cold data to Archive without updating their read paths to handle the restore workflow (cross-ref §11's edge-case discussion).

### 4.6 Garbage Collection for Deleted and Overwritten Objects

Because objects are immutable (§1) and `DELETE`/`PUT`-over-existing-key never *synchronously* free the old shards, every storage node accumulates **orphaned shards** — data belonging to object versions that are no longer the "current" version and (for unversioned buckets, or versioned buckets past their `noncurrent` retention) are no longer referenced by *any* metadata record. Garbage collection reclaims this space asynchronously:

1. **Tombstone creation**: a `DELETE` (unversioned bucket) or a lifecycle expiration marks the metadata record's `pendingGc` flag (the `ObjectMetadata.setPendingGc()` call in §4.1's `delete()`) rather than immediately issuing 9 shard-delete RPCs. This keeps the delete path's latency identical to a normal metadata write — no fan-out to storage nodes on the critical path.
2. **Versioned-bucket interaction**: with versioning enabled, a `DELETE` writes a delete marker as the new "latest" version (§4.1) — the *prior* version's shards are **not** GC-eligible at all (they remain a fully-addressable non-current version) unless a separate lifecycle rule (`NoncurrentVersionExpiration`) marks noncurrent versions older than N days as `pendingGc` too. This is a common source of "why is my bill so high" surprises: versioning without a noncurrent-expiration policy means **every overwrite and delete is purely additive** to storage consumption.
3. **Async reclaim sweep**: a background job periodically scans the metadata index for `pendingGc=true` records, issues shard-delete RPCs to the 9 storage nodes referenced by each record's `chunkLocations`, and only removes the metadata record itself once all 9 (or a configurable quorum, e.g., 8/9 with the 9th retried) shard-deletes are confirmed. This two-phase approach (metadata-tombstone now, shard-reclaim later) means a crash mid-sweep leaves at most "shards that should be deleted but aren't yet" — a storage-efficiency problem, never a correctness/durability problem (an orphaned shard can never be mistaken for live data, because no metadata record points to it).
4. **Reclaim throttling**: the sweep runs at a deliberately bounded rate (a fraction of normal write throughput) — an unthrottled GC sweep competing for the same storage-node disk I/O and network bandwidth as live PUT/GET traffic is exactly the kind of background-work-vs-foreground-latency conflict that War Story 1 (§9) describes for repair traffic, and the same prioritized-queue mitigation applies.

### 4.7 Read-Path Caching and Hot-Object Serving

The §2 estimate of **~5.1 TB/sec** aggregate GET egress cannot be served by storage nodes alone reconstructing from erasure-coded shards on every request — most of that traffic is concentrated on a small fraction of objects (the same Zipfian skew that affects every large-scale storage system, cross-ref [`./design_key_value_store.md`](./design_key_value_store.md)'s hot-key discussion). Two caching layers absorb this:

1. **CDN edge caching** (cross-ref [`../cdn/README.md`](../cdn/README.md)): for objects served to many distinct clients (public website assets, shared media, software distribution artifacts), a CDN sits in front of the gateway and caches object bytes at edge POPs close to readers. A `GET` for a CDN-cached object never reaches the metadata index or storage nodes at all — this is the single biggest lever on the §2 egress numbers, the same role the CDN plays for Google Maps' tile traffic (cross-ref [`./design_google_maps.md`](./design_google_maps.md) §4.2).
2. **Storage-node-local read cache**: even for objects that aren't CDN-eligible (private data, per-request-signed URLs), storage nodes keep a small in-memory/SSD cache of recently-reconstructed chunks. Because reconstructing a chunk from 6 shards costs real CPU (§4.2), a storage node that served chunk C five seconds ago and is asked for it again can skip reconstruction entirely if the assembled chunk is still cached — this matters most for **systematic** erasure codes, where the first `k` of `n` shards are literally the original data (no reconstruction needed if those specific shards are healthy and local), versus **non-systematic** codes where every shard is a linear combination requiring reconstruction even in the healthy case.

**Cache invalidation is structurally simple** compared to most caching problems (cross-ref [`../caching/README.md`](../caching/README.md)) precisely because objects are immutable (§1): a cached copy of object version `V` is valid **forever** — it can never become stale, because `V`'s bytes never change. The only invalidation event is a new version (or deletion) producing a *new* version ID, which is a different cache key entirely. This is the same principle behind Google Maps' versioned-tile-URL scheme (cross-ref [`./design_google_maps.md`](./design_google_maps.md) War Story 3) — immutable, versioned content sidesteps cache-invalidation complexity by construction, at the cost of old versions needing their own GC path (§4.6) once no longer referenced.

---

## 5. Design Decisions & Tradeoffs

### Erasure Coding vs. Replication

| Dimension | 3x Replication | 6+3 Erasure Coding |
|---|---|---|
| Storage overhead | 3.0x | 1.5x |
| Write latency | Lower — write 3 full copies in parallel, ack on quorum (2/3) | Higher — must encode (CPU) before writing 9 shards, ack on quorum |
| Read latency (healthy) | Lowest — read any 1 of 3 full copies directly | Slightly higher — must fetch 6 shards and reconstruct (CPU) even when nothing has failed, UNLESS systematic codes let a "primary" data shard be read directly without reconstruction (common optimization) |
| Repair cost on failure | Cheap — copy one intact replica | Expensive — read 6 surviving shards, run Reed-Solomon reconstruction, write new shard(s) |
| Best fit | Small objects, latency-critical metadata/indexes, hot tiers where repair-network cost matters more than storage cost | Large objects, Standard/IA/Archive tiers at exabyte scale where storage cost dominates |
| This design's choice | Used for the metadata index itself (§4.1) — small, latency-critical, benefits from cheap-repair replication | **Used for object data (§4.2)** — the 1.5x vs 3.0x gap is worth tens of millions of dollars/month at exabyte scale (§4.2) |

### Strong vs. Eventual Consistency for the Metadata Index

| Dimension | Eventual Consistency (legacy model) | Strong Consistency (this design, §4.4) |
|---|---|---|
| Write acknowledgment | Can return before all replicas/indexes agree | Must wait for metadata-index quorum commit |
| Read-after-write | Not guaranteed — "read your own write" workarounds needed | Guaranteed — any read after a PUT ack sees the new state |
| Write latency | Lower (no quorum wait on the metadata path) | Slightly higher (metadata quorum write is now on the critical path) |
| Application complexity | Higher — apps must tolerate stale reads, often avoid overwrite patterns | Lower — apps can `PUT` then immediately `GET`/`LIST` and trust the result |
| This design's choice | — | **Strong** — the metadata index (§4.1, §4.4) is small enough that quorum-committing it adds negligible latency relative to the data-shard write it's already waiting on |

### Storage Tiers: Cost vs. Retrieval Latency

| Dimension | Standard | Infrequent Access | Archive |
|---|---|---|---|
| Storage cost | Highest | ~50% of Standard | ~10-25% of Standard |
| Retrieval latency | Milliseconds | Milliseconds | Minutes to hours (restore workflow) |
| Best fit | Active/hot data | Backups, monthly-access data | Compliance archives, rarely-restored cold data |
| Risk if misapplied | Overpaying for cold data | — | Application breaks if it expects synchronous `GET` (§4.5, §11) |

### Content-Addressable Deduplication vs. Simplicity

A tempting optimization: hash each chunk's content and store only one physical copy per unique hash (content-addressable storage), letting many objects (or many versions of one object) share underlying shards when their bytes are identical.

| Dimension | No Dedup (this design's baseline) | Content-Addressable Dedup |
|---|---|---|
| Storage savings | None beyond erasure-coding overhead | Significant for workloads with repeated content (VM images, container layers, repeated backups) |
| Metadata complexity | Simple — one object's metadata maps directly to its own shards | Higher — shards need reference counts; GC (§4.6) can't delete a shard just because *one* referencing object was deleted |
| GC complexity | Tombstone -> async reclaim, no cross-object coordination | Reclaim requires checking the reference count reaches zero across *all* objects that might share the chunk |
| Multi-tenancy / security | Trivial — no tenant ever shares physical storage with another's data by construction | Requires care — a naive implementation could let one tenant's upload "discover" (via timing/dedup side-channels) that another tenant has identical content |
| This design's choice | **No dedup** — simplicity, GC tractability (§4.6), and avoiding cross-tenant storage-sharing concerns outweigh the storage savings for a general-purpose object store | Dedup is more appropriate as an *opt-in*, single-tenant feature (e.g., a backup product built on top of this object store) than a core multi-tenant primitive |

---

## 6. Real-World Implementations

- **Amazon S3**: the reference architecture for this entire design. In **December 2020**, S3 announced it had achieved **strong read-after-write consistency** for all objects, replacing its prior eventual-consistency model — a change AWS described as requiring no application changes and no performance tradeoff, achieved (per AWS's public description) by re-architecting the metadata layer that every read path depends on (§4.4 is modeled directly on this change). S3 also pioneered the storage-class tiering model (Standard / Standard-IA / One Zone-IA / Glacier / Glacier Deep Archive, §4.5) and the multipart upload API (§4.3) that most other providers' APIs mirror closely.
- **Google Cloud Storage (GCS)**: offers a directly analogous storage-class model (Standard, Nearline, Coldline, Archive) and has provided strong consistency for object metadata operations since launch — GCS's design explicitly treats "list after write" and "read after write" consistency as a baseline guarantee, an architectural starting point rather than a later migration (contrast with S3's 2020 change).
- **Azure Blob Storage**: uses a similar tiering model (Hot / Cool / Archive) and implements redundancy options ranging from **locally-redundant storage (LRS)**, which is conceptually 3x replication within one datacenter, up to **zone-redundant (ZRS)** and **geo-redundant (GRS)** storage — giving customers an explicit dial between the replication-heavy and erasure-coded-and-distributed ends of the spectrum described in §5.
- **MinIO**: a widely-deployed open-source, S3-API-compatible object store, notable for running its own erasure-coding scheme (configurable, commonly deployed as variants of N data + M parity, e.g., 8+4) across a much smaller node count than hyperscale providers — MinIO documentation explicitly frames its erasure-coding choice in the same storage-overhead-vs-failure-tolerance terms as §4.2's 6+3 example, just at a self-hosted scale (a single MinIO deployment might span 4-16 nodes rather than tens of thousands).
- **Ceph / RADOS**: the storage backend underlying many private-cloud and on-prem object stores (via the RADOS Gateway, S3-API-compatible). RADOS supports both **replicated pools** and **erasure-coded pools** as a per-pool configuration choice — operators explicitly choose, pool by pool, the same 3x-replication-vs-erasure-coding tradeoff from §5, often using replicated pools for small/hot metadata-like data and erasure-coded pools for bulk object data, mirroring this design's split between the metadata index (§4.1, replicated) and object data (§4.2, erasure-coded).
- **Backblaze B2**: published detailed engineering-blog cost breakdowns of its Reed-Solomon-based erasure-coding implementation (a 17-shard scheme: 17 data + 3 parity in some configurations, optimized for Backblaze's specific "Storage Pod" hardware density), making it one of the most concretely-documented public examples of the storage-overhead-vs-durability math worked through in §4.2 — Backblaze's blog posts on this topic are commonly cited as a real-world grounding for erasure-coding interview discussions.

### Provider Comparison at a Glance

| Provider | Redundancy Scheme | Consistency Model | Tiering Granularity | Notable Differentiator |
|---|---|---|---|---|
| Amazon S3 | Erasure-coded across 3+ AZs (proprietary scheme) | Strong (since Dec 2020, §4.4) | Standard / Standard-IA / One Zone-IA / Glacier / Glacier Deep Archive | First mover; the de facto API standard every other entry in this table is compatible with |
| Google Cloud Storage | Erasure-coded, multi-region by default for "multi-region" buckets | Strong since launch | Standard / Nearline / Coldline / Archive | Strong consistency was a day-one guarantee, not a later migration |
| Azure Blob Storage | LRS (3x replication) up to GRS (cross-region replication) — customer-selectable | Strong for metadata operations | Hot / Cool / Archive | Explicit replication-vs-erasure-coding dial exposed directly to the customer (§5) |
| MinIO | Configurable N+M erasure coding (e.g., 8+4), per-deployment | Strong within a single deployment | Customer-managed via lifecycle policies | Self-hosted; same erasure-coding math at a 4-16 node scale instead of tens of thousands |
| Ceph/RADOS | Per-pool: replicated or erasure-coded | Strong within a cluster | Customer-managed via CRUSH placement rules | Operators choose redundancy scheme *per pool*, mirroring this design's metadata-vs-data split |
| Backblaze B2 | Reed-Solomon, ~17+3-style wide erasure coding | Strong | Standard tier only (no Archive-class tier as of public docs) | Most-published cost/durability engineering math for erasure coding at scale |

---

## 7. Technologies & Tools

| Component | Representative Technologies | Notes |
|---|---|---|
| API/Gateway layer | S3-compatible REST API (SigV4 request signing), API gateway/load balancer fleet | §3, §7 — AuthN/AuthZ via bucket policies and ACLs (cross-ref [`../security_and_auth/README.md`](../security_and_auth/README.md)) |
| Metadata index | Distributed sorted KV store (Bigtable/HBase-style or a custom sharded-B-tree service) | §4.1 — cross-ref [`./design_key_value_store.md`](./design_key_value_store.md) for the underlying sharding/replication/quorum mechanics |
| Erasure coding library | Reed-Solomon implementations (e.g., ISA-L, jerasure, Galois-field GF(2^8) arithmetic) | §4.2 |
| Storage nodes | Commodity servers with dense HDD/SSD arrays, organized into placement groups across AZs/racks | §4.2, §4.6 |
| Multipart upload tracking | Same metadata index, or a dedicated short-lived "in-progress uploads" table | §4.3 |
| Lifecycle/tiering engine | Background scanning service over the metadata index, scheduled scans | §4.5 |
| Garbage collection | Async reclaim workers, rate-limited queues | §4.6, §9 |
| Repair/anti-entropy | Background reconstruction workers, prioritized repair queues | §4.2, §9 |
| CDN (for hot-object GETs) | Edge caching layer in front of the gateway | cross-ref [`../cdn/README.md`](../cdn/README.md) |

### Build vs. Buy Considerations

| Component | Build | Buy / Open-Source | This Design's Choice |
|---|---|---|---|
| Metadata index | Custom sharded KV/B-tree service | Bigtable/HBase/FoundationDB-style managed or open-source KV store | Either is viable — the schema and access patterns (§4.1) are the bespoke part; the underlying replicated-KV mechanics are well-served by an existing system (cross-ref [`./design_key_value_store.md`](./design_key_value_store.md)) |
| Erasure coding | Custom Reed-Solomon implementation tuned to placement-group topology | ISA-L (Intel), jerasure, or MinIO's/Ceph's built-in EC engines | Buy the math (Reed-Solomon libraries are mature and heavily optimized), build the placement-group integration (§4.2) — the latter is where the AZ/rack-awareness that makes the durability math hold lives |
| API layer | Custom S3-compatible REST implementation | MinIO (full open-source S3-compatible server) as a reference or even production base for smaller deployments | Build for hyperscale (custom gateway integrates with proprietary metadata/placement); MinIO is a legitimate production choice at smaller scale (§6) |
| CDN | Custom edge cache | Commodity CDN (cross-ref [`../cdn/README.md`](../cdn/README.md)) | Buy — CDN is a commodity layer in front of any object store's hot-object GET path |

---

## 8. Operational Playbook

### Key Metrics

| Metric | What It Measures | Alert Threshold (Illustrative) |
|---|---|---|
| **Durability (shards-below-redundancy-floor count)** | Number of chunks currently at or below the minimum surviving-shard count before data loss (e.g., chunks with only 6 of 9 shards healthy) | Page immediately if any chunk drops to **5 of 9** (one failure away from permanent loss) |
| **Repair-queue depth** | Number of chunks awaiting shard reconstruction | Page if depth grows faster than reconstruction throughput for > 15 minutes — leading indicator of War Story 1 |
| **PUT/GET p99 latency** | End-to-end request latency at the gateway | Page if GET p99 > ~100ms (Standard tier) or PUT p99 > ~200ms, sustained |
| **Metadata-index hot-shard rate** | Requests/sec to the single busiest metadata partition vs. cluster average | Investigate if any shard exceeds ~5x the per-shard average — leading indicator of War Story 2 |
| **Storage-tier transition lag** | Time between an object crossing a lifecycle-policy age threshold and its actual tier transition completing | Alert if lag exceeds 24 hours — customers are being billed for the wrong tier |
| **Availability (5xx rate)** | Fraction of requests failing with server-side errors | Page if 5xx rate exceeds 0.1% sustained — threatens the 99.99% availability NFR (§1) |

### Runbook: Rack/AZ Failure and Erasure-Coding Repair Storm

1. **Detect**: a rack or AZ failure is detected via storage-node health checks failing en masse for nodes sharing a placement-group failure domain (§4.2). The repair-queue depth metric spikes as every chunk with a shard on the failed nodes becomes repair-eligible simultaneously.
2. **Triage by durability floor, not FIFO**: do **not** repair chunks in arrival order. Sort the repair queue by **current surviving-shard count** — a chunk now at 6/9 (one AZ's worth of shards lost, still healthy) is far less urgent than a chunk that *also* had a pre-existing failed shard and is now at 5/9 (one failure from data loss). This is the single highest-leverage triage step (War Story 1).
3. **Throttle repair bandwidth**: cap aggregate repair-read/reconstruction/write bandwidth to a fixed fraction (e.g., 10-20%) of the cluster's provisioned network capacity, leaving headroom for live PUT/GET traffic. Repair is urgent but not instantaneous — a chunk at 6/9 has a wide safety margin; saturating the network to repair it in seconds instead of minutes isn't worth degrading every other tenant's read latency.
4. **Monitor unrelated-traffic latency**: confirm GET/PUT p99 for objects *not involved* in the repair stays within SLA throughout. If it degrades, reduce the repair-bandwidth cap further — the durability floor (step 2's sort) ensures the most at-risk chunks are still prioritized within whatever bandwidth is allotted.
5. **Verify and close**: once all affected chunks are back to 9/9 (or the failed AZ is restored and its shards re-validated), confirm the durability-floor metric returns to its baseline (no chunks below 9/9) before closing the incident.

### Runbook: Metadata-Index Hot Shard from Sequential Key Naming

1. **Detect**: the metadata-index hot-shard-rate metric flags one shard receiving disproportionate write traffic; PUT latency for keys in that bucket/prefix degrades while the rest of the fleet is healthy.
2. **Confirm the pattern**: sample recently-written keys for the affected bucket. A common signature is a shared literal prefix that is monotonically increasing — timestamps (`2026-06-12T00:00:01-...`), zero-padded sequence numbers, or auto-incrementing IDs. Because the metadata index is ordered lexicographically within a bucket partition (§4.1), every one of these keys sorts to the "end" of the same partition, and all new writes land on the same shard.
3. **Immediate mitigation — client-side key redesign**: work with the customer/team to add a **hashed prefix** to new object keys (e.g., prepend 2-4 hex characters of `hash(originalKey)` before the timestamp: `a3f2/2026-06-12T00:00:01-...`). New writes now scatter across the hash-prefix space, which spans many metadata-index shards. This does not fix already-written keys but stops the hotspot from growing.
4. **Longer-term mitigation — shard by a hashed key, not the literal prefix**: if this pattern recurs across many customers, the metadata index's sharding function itself can be changed to shard by `hash(objectKey)` (or a hash of a configurable prefix) rather than the literal lexicographic key — at the cost of `LIST bucket, prefix=X` becoming a fan-out query across shards rather than a single-shard range scan (§4.1's tradeoff). This mirrors the time-ordered-ID hotspotting discussion in [`./design_distributed_unique_id.md`](./design_distributed_unique_id.md) (§9 there) applied to bucket keyspaces instead of primary-key ID generation.
5. **Verify**: confirm the hot-shard-rate metric returns to baseline and PUT latency for the affected bucket recovers.

---

## 9. Common Pitfalls & War Stories

### War Story 1: A Rack Failure Triggers a Cluster-Wide Repair Storm — Broken, Then Fixed

**Broken**: An early version of the repair pipeline (§4.2, §4.6) processed the repair queue strictly **FIFO**, with **no bandwidth cap** — the reasoning was "repair is urgent, so repair as fast as possible." When a chunk's shard count dropped below 9/9, a reconstruction job (read 6 surviving shards, run Reed-Solomon reconstruction, write the missing shard(s)) was enqueued and dequeued as fast as worker capacity allowed, with workers given no ceiling on network bandwidth consumption.

**Impact**: A single rack lost power, taking down roughly 40 storage nodes simultaneously. Because placement groups (§4.2) spread each chunk's 9 shards across distinct racks within an AZ, **every chunk that had even one shard on that rack** — which, across a large cluster, was a substantial fraction of all chunks with data in that AZ — became repair-eligible at the same instant. The repair queue depth jumped from a steady-state near-zero to **hundreds of millions of chunks**. Workers immediately saturated the AZ's inter-rack network fabric reading surviving shards for reconstruction — the *same* network fabric that live `GET` requests use to fetch shards for unrelated, perfectly-healthy objects. For several hours, **p99 read latency for objects with no connection whatsoever to the failed rack spiked roughly 10x**, because their shard-fetch traffic was competing with repair traffic for the same saturated links. The on-call team initially misdiagnosed this as a storage-node performance regression, since the affected objects' own shards were all healthy — the actual cause (network saturation from an unrelated repair storm) wasn't visible without correlating the latency spike against the repair-queue-depth metric, which at the time wasn't surfaced on the same dashboard.

**Fixed**: Two changes, both now baked into §8's runbook:
1. **Durability-floor-first triage**: the repair queue is sorted by **current surviving-shard count**, not arrival time. A chunk that dropped from 9/9 to 8/9 (still far from the loss threshold) waits behind chunks that dropped to 6/9 or lower. This ensures that *if* repair bandwidth is constrained, the chunks actually at risk of permanent loss are repaired first — durability is preserved even under a bandwidth cap.
2. **Repair-bandwidth throttling**: aggregate repair traffic is capped at a fixed fraction of provisioned inter-rack/inter-AZ bandwidth (illustratively 10-20%), with the cap enforced *before* repair workers issue shard-read RPCs (not after-the-fact rate limiting that still lets a burst through). The repair storm now takes proportionally longer to fully drain — hours instead of tens of minutes — but unrelated read/write latency stays within SLA throughout, which is the actual NFR (§1) that matters. The repair-queue-depth and durability-floor metrics (§8) were also added to the primary on-call dashboard so a future repair storm is immediately recognizable as such, rather than misdiagnosed as a generic latency regression.

### War Story 2: Sequential Object Keys Hotspot the Metadata Index — Broken, Then Fixed

**Broken**: A customer ingesting time-series telemetry wrote one object per second per device, with keys of the form `telemetry/{deviceId}/2026-06-12T00:00:01Z.json` — a literal ISO-8601 timestamp as the trailing key component. The metadata index (§4.1) shards by `bucketId` and orders keys lexicographically within a shard's partition to support efficient prefix-based `LIST`.

**Impact**: Because the timestamp suffix is **monotonically increasing**, every new object's key sorted lexicographically *after* every previously-written key for that bucket — meaning every single new write touched the **same lexicographic "end" of the same metadata-index partition**. As the customer scaled from a pilot (a few hundred devices) to production (tens of thousands of devices, each writing once per second), aggregate PUT volume for this one bucket climbed into the tens of thousands per second — and **all of it** landed on one metadata-index shard, because that shard owned the "current end" of the bucket's keyspace. The shard's write throughput became the bucket's throughput ceiling: PUT latency for this customer's bucket climbed from single-digit milliseconds to multiple seconds, while every *other* bucket on the same metadata-index cluster (whose keys didn't follow this pattern) remained perfectly healthy — making the issue look, from a cluster-wide dashboard, like an isolated customer problem rather than a systemic one, which delayed root-causing it.

**Fixed**: The customer's ingestion pipeline was changed to prepend a **hashed prefix** derived from `hash(deviceId)` (a stable, well-distributed 2-byte hex prefix) to each object key: `telemetry/{hashPrefix}/{deviceId}/2026-06-12T00:00:01Z.json`. Because `hashPrefix` is effectively random across devices (and unrelated to the monotonically-increasing timestamp), new writes now scatter across the lexicographic range spanned by all possible `hashPrefix` values — which, by construction, spans many metadata-index shards rather than concentrating on one. `LIST` operations that previously relied on a clean `telemetry/{deviceId}/` prefix scan now need to either iterate over all 256 possible `hashPrefix` values (a small, bounded fan-out) or maintain a secondary index if prefix-listing by device remains a hot query pattern — a real but manageable tradeoff (§5's tradeoff table) compared to a throughput ceiling that scaled with **time** rather than with **provisioned capacity**. This is the same fundamental tension [`./design_distributed_unique_id.md`](./design_distributed_unique_id.md) describes for time-ordered primary keys (§9 there): a key design that's great for range-scan locality is, by the same token, terrible for write distribution, and the fix is always some form of "add entropy to the part of the key that determines placement, keep the sortable part for query convenience."

---

## 10. Capacity Planning

### Storage Overhead from the Erasure-Coding Scheme

- Logical data target: **1 EB** (§2)
- 6+3 erasure coding (1.5x overhead, §4.2): raw storage required = `1 EB x 1.5` = **1.5 EB**
- For comparison, 3x replication would require `1 EB x 3.0` = **3.0 EB** — the erasure-coding choice saves **1.5 EB** of raw storage at this scale
- IA tier (assume 20% of data, same 6+3 scheme but on denser/cheaper media): `0.2 EB x 1.5` = 0.3 EB raw
- Archive tier (assume 10% of data, wider 10+4 scheme, 1.4x overhead): `0.1 EB x 1.4` = 0.14 EB raw
- Standard tier (remaining 70%): `0.7 EB x 1.5` = 1.05 EB raw
- **Total raw storage**: `1.05 + 0.3 + 0.14` ~= **1.49 EB** across tiers — within rounding of the flat 1.5 EB estimate, since IA uses the same overhead ratio and Archive's slightly-better ratio on a smaller fraction barely moves the total

### Durability Probability Given Shard Annual Failure Rate

- Per-shard AFR: **1%** (§4.2)
- 6+3 scheme: P(data loss) ~= P(>=4 of 9 shards fail in the same repair window) ~= `1.2 x 10^-6`/object/year (§4.2's binomial-tail calculation)
- With continuous background repair reducing the *effective* window during which a second/third/fourth failure must occur (from "until a human replaces a disk," ~days, to "until automated reconstruction completes," ~hours), the *effective* per-shard failure probability *within the repair window* drops by roughly 1-2 orders of magnitude, pushing the realized P(data loss) toward `10^-8` to `10^-11`/object/year — consistent with the advertised **11 nines (99.999999999%)** durability target (§1)
- At **100 billion objects** (§2) and `10^-11`/object/year, expected loss = `100,000,000,000 x 10^-11` = **~1 object/year cluster-wide** — the "11 nines" figure translated into an absolute number an interviewer can sanity-check

### Metadata Index Sizing

- 100 billion objects x ~1 KB/record (§2) = **~100 TB** of metadata
- Metadata index uses 3x replication (§5 — small, latency-critical, cheap-repair tradeoff favors replication over erasure coding here): `100 TB x 3` = **~300 TB** raw metadata storage
- At ~1-2 TB usable capacity per metadata-index node (favoring fast SSDs for low-latency lookups, distinct hardware profile from bulk object-storage nodes): `300 TB / 1.5 TB` ~= **~200 metadata-index nodes**
- Sharded by `bucketId` (§4.1) across these ~200 nodes; with virtual-node-style rebalancing (cross-ref [`../consistent_hashing/README.md`](../consistent_hashing/README.md) and [`./design_key_value_store.md`](./design_key_value_store.md)) to avoid the single-huge-bucket hotspot case (War Story 2) when one bucket's data volume vastly exceeds another's

### Repair Bandwidth Budget

- Per §9's War Story 1 fix, repair traffic is capped at **10-20%** of inter-rack/inter-AZ provisioned bandwidth
- Illustrative inter-AZ link capacity: 100 Gbps per AZ-pair link; repair cap at 15% = **15 Gbps** dedicated to repair traffic
- A single chunk reconstruction (6+3 scheme): read 6 shards of, say, 64 MB each (the chunk size from §3) = 384 MB read, write 1-3 shards of 64 MB each = 64-192 MB write -> ~450-576 MB of network I/O per chunk repaired
- At 15 Gbps (~1.875 GB/sec) repair bandwidth: `1.875 GB / 0.5 GB` ~= **~3,750 chunk-repairs/sec** sustainable — sized against the repair-queue-depth metric (§8) to bound drain time for a rack-scale failure (tens of millions of affected chunks / 3,750/sec ~= a few hours, consistent with War Story 1's "hours instead of tens of minutes" outcome)

### Summary Table

| Component | Sizing Basis | Estimated Footprint |
|---|---|---|
| Raw object storage (all tiers) | 1 EB logical x blended ~1.49x overhead | ~1.49 EB |
| Metadata index | 100B objects x ~1KB x 3x replication | ~300 TB, ~200 nodes |
| Repair bandwidth (per AZ-pair link) | 15% of 100 Gbps | ~15 Gbps, ~3,750 chunk-repairs/sec |
| Expected annual object loss | 100B objects x ~10^-11/object/year | ~1 object/year |

---

## 11. Interview Discussion Points

**Q: How did S3 achieve strong read-after-write consistency, and what architecturally changed?**
A: The shift (§4.4) was fundamentally about making the **metadata index** the single, strongly-consistent source of truth that every read path depends on, and ensuring a `PUT` is acknowledged only after that metadata record is durably committed. Previously, "does this object exist and where is it" could be answered differently by different internal components that updated asynchronously; afterward, there's exactly one small, quorum-committed record per object-version, and no read path can bypass it. Crucially, this didn't require making large object-*data* writes fully synchronous across all replicas/shards — only the small metadata pointer needed to become strongly consistent, which is why AWS could roll this out with no performance penalty.

**Q: Erasure coding vs. replication — when does each win?**
A: Erasure coding (e.g., 6+3, 1.5x overhead) wins for **large objects at scale**, where the storage-cost savings (half the overhead of 3x replication) dwarf the extra CPU/network cost of reconstruction on read or repair (§4.2, §5). Replication wins for **small, latency-critical, frequently-read-and-rewritten data** — like the metadata index itself (§4.1) — where repair is cheap (just copy an intact replica) and you can't afford reconstruction CPU on every read. The rule of thumb: erasure code the bulk data plane, replicate the small control/metadata plane.

**Q: Why are objects immutable — why is there no in-place edit?**
A: Immutability is what lets the system shard and erasure-code objects independently with no coordination (§1, §4.2). An in-place edit would require either rewriting all 9 shards of every affected chunk atomically (a distributed-transaction problem, explicitly out of scope, §1) or some kind of write-ahead log per object — both of which would re-introduce the coordination overhead the flat-namespace design exists to avoid. "Overwriting" a key is therefore just writing a brand-new object (and, with versioning, a new version) under the same key — cheap, because it requires no coordination with the old version's shards at all.

**Q: What happens to a multipart upload if one part fails?**
A: Nothing rolls back — each `uploadPart` call is independent and idempotent (§4.3); the client simply retries the failed part number with the same bytes, producing the same ETag. If the *entire upload* is abandoned (client crash, never calls `CompleteMultipartUpload`), the already-uploaded parts become orphaned storage that a lifecycle rule (`AbortIncompleteMultipartUpload`, commonly after 7 days) reclaims via the same GC pipeline (§4.6) used for deleted objects.

**Q: A customer's bucket is suddenly slow for writes, but the rest of the cluster is healthy — what's your first hypothesis?**
A: Sequential-key hotspotting on the metadata index (§4.1, War Story 2) — check whether the bucket's recently-written keys share a monotonically-increasing literal prefix (timestamps, auto-incrementing IDs). Because the metadata index orders keys lexicographically within a shard for `LIST` efficiency, sequential keys concentrate all new writes on one shard, and that shard's throughput becomes the bucket's ceiling regardless of how much capacity the rest of the cluster has. The fix is adding a hashed prefix to scatter writes across shards (§8's runbook).

**Q: A rack just failed and the repair queue has exploded — what's the wrong way to handle it, and the right way?**
A: The wrong way (War Story 1) is FIFO, unbounded-bandwidth repair — reconstructing every affected chunk as fast as possible saturates the shared network fabric and degrades read/write latency for completely unrelated objects by ~10x. The right way is **durability-floor-first triage** (repair the chunks closest to the data-loss threshold first, regardless of arrival order) combined with a **bandwidth cap** on aggregate repair traffic — repair takes longer in wall-clock time, but unrelated traffic's SLA is preserved throughout, which is the metric that actually matters.

**Q: What's the actual durability math behind "11 nines," and is it really 11 nines from day one?**
A: The raw combinatorics of a 6+3 scheme with 1% per-shard AFR give roughly `1.2 x 10^-6` probability of losing any given chunk in a year (§4.2, §10) — about "6 nines" on its own. The advertised 11 nines comes from **continuous background repair** shrinking the window during which additional failures must occur to cause loss from days (manual disk replacement) to hours (automated reconstruction) — this repair-driven reduction is what pushes realized durability from ~10^-6 to ~10^-11. So yes, the 11-nines figure already assumes the repair pipeline (§4.6, §8, War Story 1) is working — which is precisely why repair-queue health is a top-tier alerting metric (§8), not a nice-to-have.

**Q: How does the metadata index handle `LIST bucket` for a bucket with billions of keys without scanning everything?**
A: `LIST bucket, prefix=X, maxKeys=N` is a bounded lexicographic range scan (§4.1's `listByPrefix`) — it seeks to the first key >= `prefix`, reads forward only until either a key no longer matches `prefix` or `maxKeys` results are collected, then stops. It never touches keys outside that range, so cost is `O(maxKeys + log(shardSize))` regardless of whether the bucket has a thousand keys or a trillion. This is also *why* the index orders keys lexicographically within a shard in the first place — losing that ordering (e.g., to fix War Story 2 via pure hashing) is exactly what makes prefix-listing become a multi-shard fan-out instead of a single-shard scan (§5, §9).

**Q: How are placement groups designed, and why does the 6+3 scheme map to "3 AZs x 3 shards"?**
A: A placement group spreads a chunk's 9 shards as 3 shards per AZ across 3 AZs, and within an AZ across distinct racks/power domains (§4.2), so that correlated failures (a rack losing power, an AZ going dark) cost a bounded, *known* number of shards per chunk. A single-AZ failure costs exactly 3 of 9 shards — exactly at the "any 3 of 9 can be lost" tolerance boundary of the 6+3 scheme, which is not a coincidence: the scheme is chosen so that the most likely large-blast-radius failure (one AZ) is survivable with zero data loss, just reduced redundancy until repair completes.

**Q: How does garbage collection interact with versioning — why might enabling versioning silently increase storage costs a lot?**
A: With versioning enabled, every `PUT`-over-existing-key and every `DELETE` becomes purely *additive* — the old version (or, for delete, the pre-delete version) remains a fully-addressable non-current version and is **not** GC-eligible (§4.6) unless a separate `NoncurrentVersionExpiration` lifecycle rule explicitly marks old versions for reclaim after some age. Teams that enable versioning for safety but don't configure noncurrent-version expiration often discover months later that their storage bill has grown far faster than their "current" data volume, because every historical version of every overwritten object is still being stored and erasure-coded in full.

**Q: What's the tradeoff of moving an object to the Archive tier, and what application-level bug does it commonly cause?**
A: Archive tiers (Glacier-class, §4.5) cut storage cost to roughly 10-25% of Standard but make `GET` **asynchronous** — a `GET` on an archived object doesn't return data; the application must first call `RestoreObject`, wait minutes to hours for the object to be re-placed onto Standard/IA storage, then retry. The common bug is teams configuring a lifecycle policy to archive old data without updating the read path to handle this — the read path was written assuming `GET` is always synchronous, and starts erroring (or hanging) the moment any object it tries to read has been archived.

**Q: Why does the metadata index use replication (not erasure coding) when object data uses erasure coding?**
A: Different access patterns and size profiles (§5). The metadata index holds small (~1KB) records that are read on the hot path of *every* request and must support cheap, fast repair (just copy an intact replica) — erasure coding's reconstruction CPU cost on every read, or on every repair, would be a poor tradeoff for data this small and this latency-critical. Object data is the opposite: large payloads where the 1.5x-vs-3.0x storage-cost gap is enormous in absolute terms (§4.2), and reconstruction cost is amortized over a much larger payload.

**Q: An object is 5TB — how is it actually stored and retrieved?**
A: Via multipart upload (§4.3): the client splits it into up to 10,000 parts (5MB-5GB each — a 5TB object needs at least 1,000 parts at the 5GB max), uploads parts independently and in parallel, and calls `CompleteMultipartUpload` with an ordered ETag manifest. Each part is independently chunked and erasure-coded (§4.2) as it arrives — by the time `CompleteMultipartUpload` runs, all the data is already durably stored; completion just assembles the single logical-object metadata record referencing all parts' chunk placements in order. A subsequent `GET` with a byte-range header maps that range onto the relevant part(s) and chunk(s) without needing to reconstruct the entire 5TB object.

**Q: Two clients `PUT` to the same key at almost the same time — what does the second client's write do to the first, and what does a concurrent `GET` see?**
A: With strong consistency (§4.4) and no versioning, the second `PUT` to complete wins — its metadata record becomes "latest," fully replacing the first's pointer (the first `PUT`'s shards become GC-eligible, §4.6). A `GET` racing with either `PUT` sees either the old object or the new one in its entirety — never a mix — because the metadata record (the single thing a `GET` consults) is updated atomically. With versioning enabled, *both* writes succeed as distinct versions; "latest" is whichever metadata-commit won the race, and the other remains addressable by its version ID.

**Q: Why is `hash(key)`-based sharding for the metadata index not simply the obvious choice from the start?**
A: Because `LIST bucket, prefix=X` — a core required operation (§1) — depends on keys within a bucket being stored in **lexicographic order** so a prefix query is a contiguous range scan (§4.1). Pure hash-based sharding destroys that ordering: a prefix scan would have to fan out to every shard and merge results. The design instead shards by `bucketId` (preserving in-shard lexicographic order for `LIST`) and accepts that *pathological* per-bucket key patterns (sequential keys, War Story 2) can still hotspot a single bucket's shard — solved locally (hashed key prefixes) rather than by abandoning ordering globally.

**Q: How would this design need to change to support a billion-object bucket with extremely high single-bucket write throughput?**
A: The single-`bucketId`-shard model (§4.1) becomes the bottleneck — one bucket's metadata can't exceed one shard's throughput. The fix is **sub-sharding within a bucket**, typically by hashing a configurable portion of the key (the same hashed-prefix technique from War Story 2/§8, but applied proactively rather than reactively) so a single bucket's metadata spans many shards. The cost is that `LIST bucket` (without a matching prefix) becomes a fan-out-and-merge across those sub-shards instead of a single contiguous scan — an explicit instance of §5's ordering-vs-distribution tradeoff, just applied at a finer grain.

---

## Cross-References

- **Metadata index sharding, replication, and quorum mechanics (§4.1, §4.4, §10)** -> [`./design_key_value_store.md`](./design_key_value_store.md)
- **Sequential-key hotspotting and the hashed-prefix mitigation (§8, War Story 2, §11)** -> [`./design_distributed_unique_id.md`](./design_distributed_unique_id.md)
- **Consistent hashing for metadata-shard placement and rebalancing (§10)** -> [`../consistent_hashing/README.md`](../consistent_hashing/README.md)
- **LSM-tree/B-tree tradeoffs for the metadata index's per-node storage engine (§4.1)** -> [`../../database/storage_engines_internals/README.md`](../../database/storage_engines_internals/README.md)
- **Sharding strategy generalities applied to the metadata index (§4.1, §10)** -> [`../../database/sharding_and_partitioning/README.md`](../../database/sharding_and_partitioning/README.md)
- **Horizontal scaling and stateless API/gateway tier (§3, §7)** -> [`../scalability/README.md`](../scalability/README.md)
- **Repair-queue depth, durability-floor, and freshness alerting (§8, War Story 1)** -> [`../observability/README.md`](../observability/README.md)
- **Bucket policies, object ACLs, and request signing/authorization (§1, §7)** -> [`../security_and_auth/README.md`](../security_and_auth/README.md)
- **CDN offload for hot-object GET traffic (§2, §7)** -> [`../cdn/README.md`](../cdn/README.md)
