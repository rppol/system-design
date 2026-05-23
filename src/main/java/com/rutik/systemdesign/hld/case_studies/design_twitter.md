# System Design: Twitter/X

## Table of Contents
1. Requirements Clarification
2. Scale Estimation
3. High-Level Architecture
4. Feed Generation (Core Problem)
5. Timeline Storage
6. Tweet Storage
7. User Graph
8. Trending Topics
9. Search
10. Notifications
11. Media Upload
12. Sharding Strategy
13. Bottlenecks and Solutions
14. Trade-offs
15. Interview Discussion Tips

---

## Intuition

> **Design intuition**: Twitter's core challenge is the "fan-out problem" — when a celebrity with 10M followers tweets, you can't query all their followers' feeds on read (too slow). You must precompute (fan-out on write) or find a hybrid. The feed generation architecture is the heart of Twitter's system design.

**Key insight**: The split between fan-out-on-write (precompute timelines for each follower) and fan-out-on-read (merge feeds at query time) isn't binary — Twitter uses both. Regular users: fan-out-on-write (fast read). Celebrities with millions of followers: fan-out-on-read (writing to 10M feeds per tweet is too expensive). The hybrid approach handles the full range.

---

## 1. Requirements Clarification

### Functional Requirements
- **Tweet**: Users can post tweets (text up to 280 chars, images, videos, links)
- **Follow**: Users can follow/unfollow other users
- **Home Timeline**: Users see a feed of tweets from people they follow, in reverse-chronological or ranked order
- **User Timeline**: View all tweets by a specific user
- **Like / Retweet / Reply**: Engagement actions on tweets
- **Trending Topics**: Display top trending hashtags and keywords globally or by region
- **Notifications**: Notify users of likes, retweets, mentions, new followers
- **Search**: Full-text search over tweets, users, hashtags

### Non-Functional Requirements
- High availability (99.99% uptime — users expect the feed to always load)
- Low read latency (home timeline < 200ms p99)
- Eventual consistency is acceptable (a tweet may take a few seconds to appear in all followers' feeds)
- Durable tweet storage (tweets must not be lost once posted)
- Horizontally scalable (must handle global traffic spikes)

### Out of Scope
- Direct messages (separate system)
- Ads serving
- Content moderation pipeline

---

## 2. Scale Estimation

### Users and Traffic
- 300M Daily Active Users (DAU)
- Average user follows 200 people, has 200 followers
- 100M tweets posted per day
  - 100M / 86,400 sec ~ **1,200 writes/sec** (peak ~3x = 3,600/sec)
- Read-to-write ratio: **1000:1** (Twitter is extremely read-heavy)
  - Read QPS: 1,200 * 1,000 = **1.2M reads/sec** at steady state
  - Peak reads: ~3-5M reads/sec

### Storage
- Tweet text: 280 chars ~ 300 bytes
- Tweet metadata (user_id, timestamps, counters): ~200 bytes
- Total per tweet: ~500 bytes
- 100M tweets/day * 500 bytes = **50 GB/day** of tweet data
- 5-year tweet storage: 50 GB * 365 * 5 ~ **90 TB**

### Media Storage
- ~10% of tweets include an image (~1 MB avg), ~2% include video (~5 MB avg)
- Image: 10M * 1 MB = 10 TB/day
- Video: 2M * 5 MB = 10 TB/day
- Total media: ~20 TB/day
- 5-year media: **36 PB** (requires CDN and tiered storage)

### Timeline Fanout
- Average user has 200 followers
- 1,200 writes/sec * 200 followers = **240,000 timeline insertions/sec**
- Celebrity with 10M followers: 1 tweet = 10M insertions (fanout problem)

---

## 3. High-Level Architecture

```
                        +------------------+
                        |    DNS / CDN     |
                        +--------+---------+
                                 |
                        +--------v---------+
                        |  Load Balancer   |
                        +---+----------+---+
                            |          |
              +-------------v--+    +--v--------------+
              |   API Servers  |    |   API Servers   |
              | (Write Path)   |    | (Read Path)     |
              +---+--------+---+    +--+----------+---+
                  |        |           |          |
     +------------v--+  +--v---------+ |   +------v-------+
     | Tweet Service |  | Fanout     | |   | Timeline     |
     |               |  | Service    | |   | Service      |
     +-------+-------+  +-----+------+ |   +------+-------+
             |                |        |          |
     +-------v-------+  +-----v------+ |   +------v-------+
     | Tweet Store   |  | Timeline   | |   | Timeline     |
     | (Cassandra)   |  | Cache      | |   | Cache        |
     +---------------+  | (Redis)    | |   | (Redis)      |
                        +-----+------+ |   +------+-------+
                              |        |          |
                        +-----v--------v----------v------+
                        |         Message Queue          |
                        |           (Kafka)              |
                        +----+----------+----------+-----+
                             |          |          |
                    +--------v--+  +----v----+  +--v---------+
                    | Search    |  | Notif.  |  | Analytics  |
                    | Service   |  | Service |  | Service    |
                    | (ES)      |  |         |  |            |
                    +-----------+  +---------+  +------------+

     +-------------------+        +---------------------------+
     |   Object Store    |        |    User Graph Service     |
     |   (S3 + CDN)      |        |  (Redis Set / Graph DB)   |
     +-------------------+        +---------------------------+
```

---

## 4. Feed Generation (The Core Problem)

This is the most important design decision for Twitter. The challenge: when a user opens their home timeline, how do we assemble the feed of tweets from all people they follow?

### Option A: Pull Model (Fanout on Read)
- When user requests their timeline, query tweets from all followees in real time
- Merge and sort results

**Pros:**
- No wasted computation for inactive users
- Perfect for celebrities (no massive fanout)

**Cons:**
- High read latency (must query N followees and merge)
- Expensive at scale (300M users * 200 follows = massive fan-in)
- Not suitable for real-time feel

### Option B: Push Model (Fanout on Write)
- When a user tweets, immediately push the tweet_id into each follower's timeline cache
- Timeline read = single cache lookup

**Pros:**
- Extremely fast reads (O(1) cache hit)
- Simple read path

**Cons:**
- Celebrity problem: 1 tweet from a user with 10M followers = 10M cache writes
- Wasted writes for inactive users
- Increased write latency for popular users

### Option C: Hybrid Model (Twitter's Actual Approach)
- **Normal users (< 10K followers)**: Use push model — fanout on write
- **Celebrities (>= 10K followers)**: Use pull model — excluded from push fanout

**At Read Time (Hybrid merge):**
1. Fetch pre-computed timeline from Redis (contains tweets from non-celebrity followees)
2. Identify which followees are celebrities
3. Fetch recent tweets from each celebrity directly from tweet store
4. Merge and rank all results
5. Return to client

**Why this works:**
- A user follows ~5 celebrities on average — only 5 extra fetches at read time
- 99% of accounts are non-celebrity, so fanout is bounded
- Celebrities' tweets are cached individually (hot data anyway)

### Fanout Service Implementation
```
Fanout Service (async, via Kafka):
  1. Tweet Service writes tweet to Cassandra
  2. Tweet Service publishes tweet_event to Kafka
  3. Fanout Service consumes event
  4. Looks up followers of tweet author in User Graph Service
  5. If follower count < 10K: push tweet_id to each follower's Redis timeline
  6. If follower count >= 10K: skip push (pull at read time)
```

---

## 5. Timeline Storage

### Redis Sorted Set (Hot Timelines)
- Key: `timeline:{user_id}`
- Value: tweet_id (as member)
- Score: tweet timestamp (Unix epoch in milliseconds)
- **Why sorted set**: efficient range queries, automatic ordering, O(log N) insert

```
ZADD timeline:user123 1700000001000 tweet_abc
ZADD timeline:user123 1700000002000 tweet_def
ZREVRANGE timeline:user123 0 19  -- get latest 20 tweets
```

- Store only last **800 tweet_ids** per user in Redis (trim with ZREMRANGEBYRANK)
- Memory: 800 tweet_ids * 8 bytes * 300M users = **1.92 TB RAM** (use Redis Cluster)
- Only maintain timelines for users active in last 7 days (lazy eviction)

### Cassandra (Persistent Timeline Storage)
- For users whose Redis cache has expired or for historical scrolling
- Partition key: user_id
- Clustering key: tweet_id (descending)
- Allows efficient range scans per user

```sql
CREATE TABLE user_timeline (
    user_id     UUID,
    tweet_id    BIGINT,    -- Snowflake ID (time-ordered)
    created_at  TIMESTAMP,
    PRIMARY KEY (user_id, tweet_id)
) WITH CLUSTERING ORDER BY (tweet_id DESC);
```

---

## 6. Tweet Storage

### Schema (Cassandra)
```sql
CREATE TABLE tweets (
    tweet_id    BIGINT PRIMARY KEY,   -- Snowflake ID
    user_id     UUID,
    content     TEXT,
    media_url   TEXT,
    reply_to    BIGINT,               -- null if original tweet
    retweet_of  BIGINT,               -- null if original tweet
    like_count  COUNTER,
    retweet_count COUNTER,
    created_at  TIMESTAMP
);
```

### Why Cassandra for Tweets?
- Write-heavy workload fits Cassandra's LSM-tree storage
- Horizontal scalability across multiple data centers
- Tunable consistency (write with QUORUM, read with ONE for speed)
- No complex joins needed for tweet lookup
- Built-in TTL support for ephemeral data

### Tweet ID: Snowflake
Twitter's Snowflake generates 64-bit unique IDs:
```
[41 bits: timestamp ms] [10 bits: machine id] [12 bits: sequence]
```
- Time-sortable without additional index
- Distributed generation without coordination
- 41 bits of timestamp = 69 years of IDs

---

## 7. User Graph

### Data Model
- Need to answer: "Who does user X follow?" and "Who follows user X?"
- Both queries needed for timeline fanout and follower notifications

### Storage Options

**Option A: RDBMS (follows table)**
```sql
CREATE TABLE follows (
    follower_id UUID,
    followee_id UUID,
    created_at  TIMESTAMP,
    PRIMARY KEY (follower_id, followee_id)
);
```
- Works at small scale, poor at 300M+ relationship queries

**Option B: Redis Set**
```
following:{user_id}  -> SET of user_ids this user follows
followers:{user_id}  -> SET of user_ids following this user
```
- O(1) membership check (SISMEMBER)
- O(N) full list retrieval for fanout
- Memory-intensive but fast

**Option C: Graph Database (Neo4j)**
- Natural fit for social graph traversal
- Useful for "People You May Know" feature
- Higher operational complexity

**Recommendation**: Redis for hot graph data (fast fanout), backed by Cassandra/MySQL for persistence. For advanced social features (mutual friends, recommendations), use a graph DB or dedicated graph processing (Apache Giraph).

---

## 8. Trending Topics

### Requirements
- Top-K trending hashtags/topics in the last 1 hour, per region
- Update frequency: every 5 minutes is acceptable

### Approach: Sliding Window Counter + Top-K

**Step 1: Count hashtag occurrences**
- Each tweet is published to Kafka
- Stream processing (Apache Flink or Kafka Streams) counts hashtag occurrences
- Use a **sliding window** of 1 hour with 5-minute buckets (12 buckets)

**Step 2: Distributed counting**
- Partition Kafka by hashtag for parallelism
- Each partition processor maintains a count map
- Periodically flush counts to a central aggregator

**Step 3: Top-K Selection**
- Use a **min-heap of size K** to maintain top-K items efficiently
- Or use Count-Min Sketch for approximate counting with low memory

```
Count-Min Sketch:
- Probabilistic data structure for frequency estimation
- Uses d hash functions, w-wide array
- Overestimates but never underestimates
- Space: O(d * w), much smaller than exact counting
```

**Step 4: Storage and Serving**
- Store trending results in Redis with TTL
- `trending:{region}` -> sorted set of (hashtag, score)
- Refresh every 5 minutes via a cron job or stream processor output

---

## 9. Search

### Requirements
- Full-text search over tweets
- Search by user, hashtag, keyword
- Results should be near real-time (new tweets indexed within seconds)

### Architecture
- **Elasticsearch** cluster for tweet indexing
- Kafka consumer indexes new tweets into Elasticsearch asynchronously
- Each tweet document:
```json
{
  "tweet_id": "123456",
  "user_id": "user_abc",
  "content": "Hello world #tech",
  "hashtags": ["tech"],
  "created_at": "2024-01-01T00:00:00Z",
  "like_count": 10
}
```

### Sharding in Elasticsearch
- Shard by time (monthly indices) for efficient time-range queries
- Alias `tweets_current` points to current month's index
- Older indices are tiered to warm/cold nodes

### Ranking
- Default: recency + engagement score
- Personalized: boost tweets from accounts user interacts with
- Trending: boost tweets with rapidly growing engagement

---

## 10. Notifications

### Types
- Like, Retweet, Reply, Mention, New Follower, Direct Message

### Pipeline
```
User Action
    |
    v
Action Service (writes to DB)
    |
    v
Kafka (notification_events topic)
    |
    v
Notification Service (consumer)
    |-- User preferences check (do not disturb, frequency settings)
    |-- Aggregation (batch: "X and 5 others liked your tweet")
    |
    v
Delivery Channel Router
    |-- Push Notification (APNs / FCM)
    |-- Email (SendGrid / SES)
    |-- In-app notification store
```

### Notification Store
```sql
CREATE TABLE notifications (
    user_id     UUID,
    notif_id    BIGINT,     -- Snowflake
    type        TEXT,
    actor_id    UUID,
    target_id   BIGINT,     -- tweet_id or user_id
    read        BOOLEAN,
    created_at  TIMESTAMP,
    PRIMARY KEY (user_id, notif_id)
) WITH CLUSTERING ORDER BY (notif_id DESC);
```

---

## 11. Media Upload

### Upload Flow
```
Client
  |
  v
Media Upload Service (pre-signed URL generation)
  |
  v
S3 (raw upload directly from client using pre-signed URL)
  |
  v
Kafka (media_uploaded event)
  |
  v
Media Processing Service (async)
  |-- Image: resize, compress, generate thumbnails
  |-- Video: transcode to multiple bitrates, extract thumbnail
  |
  v
S3 (processed versions stored)
  |
  v
CDN (CloudFront / Fastly) — serves all media globally
```

### CDN Strategy
- Cache media at edge nodes close to users
- Immutable content (once uploaded, media never changes) — set long TTL (1 year)
- Use content-addressed URLs (hash of content = URL) to prevent cache poisoning

---

## 12. Sharding Strategy

### Tweet Table Sharding
- **Shard key: tweet_id (Snowflake)**
- Since Snowflake IDs are time-ordered, naive range sharding creates hot spots (all new tweets go to same shard)
- Solution: use **consistent hashing** on tweet_id to distribute evenly

### User Table Sharding
- **Shard key: user_id (UUID)**
- UUID is already random — consistent hashing distributes evenly

### Timeline Cache Sharding (Redis Cluster)
- Redis Cluster handles sharding automatically using hash slots
- `timeline:{user_id}` key hashes to one of 16,384 slots
- Each Redis node owns a range of slots

### Avoiding Cross-Shard Queries
- Never query "tweets by user across all shards" — use a secondary index (user_id → [tweet_ids]) stored in a separate lookup table
- All tweet lookups by tweet_id are single-shard (shard is determined by tweet_id)

---

## 13. Bottlenecks and Solutions

| Bottleneck | Impact | Solution |
|---|---|---|
| Celebrity fanout | 1 tweet → 10M Redis writes | Hybrid model: pull celebrities at read time |
| Timeline read latency | Slow cache misses | Redis Cluster, pre-warm on login |
| Hot user_id on tweet table | Write hotspot | Snowflake shard key, consistent hashing |
| Trending computation | High CPU for counting | Count-Min Sketch, Kafka Streams |
| Search indexing lag | New tweets not searchable | Async Kafka consumer, near-real-time indexing |
| Media storage cost | PBs of media | Tiered S3 (hot/warm/cold), CDN edge caching |
| WebSocket connections | Millions of persistent connections | Connection multiplexing, horizontal scaling of connection servers |

---

## 14. Trade-offs Made

### Consistency vs. Availability
- **Choice**: Eventual consistency for timelines
- **Reason**: A tweet appearing 1-2 seconds late in a follower's feed is acceptable; downtime is not
- **Implementation**: Cassandra with QUORUM writes, ONE reads

### Push vs. Pull for Timeline
- **Choice**: Hybrid (push for normal users, pull for celebrities)
- **Reason**: Pure push fails for celebrities (10M writes); pure pull is too slow for reads
- **Trade-off**: Read path complexity increases (must identify celebrities and merge)

### Redis Timeline Cache Size (800 tweets)
- **Choice**: Cap at 800 tweet_ids per user
- **Reason**: Users rarely scroll back more than 800 tweets; beyond that, serve from Cassandra
- **Trade-off**: Cold reads for deep scroll (rare but slower)

### 302 vs 301 for short URLs (if applicable)
- Not directly applicable, but for tweet links: use CDN origin shield to avoid repeated lookups

### Elasticsearch for Search (vs. Solr or custom)
- **Choice**: Elasticsearch
- **Reason**: Managed, scalable, real-time indexing, rich query DSL
- **Trade-off**: Operationally complex, eventual consistency with main tweet store

---

## 15. Interview Discussion Tips

### How to Structure Your Answer (45-minute interview)
1. **Clarify requirements** (5 min): Ask about scale, which features are in scope, consistency requirements
2. **Estimate scale** (5 min): Write down QPS, storage, bandwidth
3. **Draw high-level diagram** (5 min): Services, databases, queues
4. **Deep dive on core problem** (15 min): Feed generation is THE problem for Twitter — spend most time here
5. **Database design** (5 min): Schema, choice of DB, sharding
6. **Handle bottlenecks** (5 min): Celebrity problem, cache strategy
7. **Trade-offs** (5 min): Be explicit about what you chose and why

### Key Things Interviewers Look For
- Recognition that Twitter is **read-heavy** and the solution must optimize reads
- Understanding of the **celebrity / thundering herd problem**
- Concrete explanation of **hybrid fanout** (not just "use a cache")
- Snowflake ID and why time-sortable IDs matter
- Understanding that Redis sorted sets are ideal for timeline storage
- Awareness that you need separate services (single-responsibility)

### Common Mistakes to Avoid
- Jumping to SQL for tweet storage without justification
- Forgetting the celebrity problem entirely
- Not distinguishing home timeline from user timeline
- Over-engineering search (Elasticsearch is sufficient — don't build custom inverted index)
- Ignoring the media upload flow (images/video are a significant part of the system)

### Follow-up Questions You May Get
- "How would you implement the ranking algorithm for timeline?" — ML-based relevance scoring, engagement signals
- "How do you handle a tweet going viral?" — Thundering herd on cache, circuit breakers, rate limiting fanout
- "How would you shard the user graph?" — adjacency list partitioning, separate read replicas
- "How do you ensure exactly-once delivery for notifications?" — Idempotency keys, deduplication in Kafka consumer
- "How would you design the retweet feature?" — Store as a separate record with reference to original tweet_id

### Numbers to Remember
- 300M DAU, 100M tweets/day
- 1,200 writes/sec, 1.2M reads/sec
- 1000:1 read-to-write ratio
- Average 200 followers per user
- Celebrity threshold: 10K followers for hybrid switch
- Redis timeline: 800 tweet_ids per user
- Snowflake ID: 64-bit, 41-bit timestamp

---

## 18. Failure Scenarios and Recovery

### Failure 1: Timeline Redis Cluster Master Loss
**Scenario**: A Redis master holding ~5% of users' precomputed home timelines crashes (process kill, hardware failure, OOM from a hot key).

**Behavior**:
- Sentinel detects master failure in 10–15 seconds (configurable failover-timeout).
- A replica is promoted; client libraries (Jedis/Lettuce with Sentinel support) re-resolve the master endpoint.
- Writes that hit the failed master in the gap window are buffered in the fan-out service's local in-memory queue with retry.
- Stale reads possible during the 30s window: a user might miss a tweet posted right before failover.

**TTR**: 30–45 seconds for full read/write recovery. Tweet itself is never lost (durable in Manhattan/MySQL); only the precomputed timeline index needs rebuild.

**Mitigation at scale**: Sharded Redis with replication factor 2 (1 master + 2 replicas per shard); failover impacts only ~0.5% of users at any moment.

### Failure 2: Timeline Cache Cold Start (Thundering Herd)
**Scenario**: Entire Redis tier restarted after a config change or OS patch. All ~300M home timeline caches are empty. Each user login triggers a full fan-out reconstruction.

**Cost of rebuild per user**: Fetch latest 800 tweets from followees (avg 200 followees × 4 recent tweets each via Manhattan) → ~10ms per user.

**Behavior without mitigation**:
- 1M users/sec attempt to load home timelines.
- Each triggers a Manhattan read storm: 1M × 200 = 200M reads/sec → 6× normal load → Manhattan latency explodes → cascading failure.

**Mitigation**:
- **Warm-up procedure**: Pre-rebuild timelines for top 10% most-active users *before* restoring traffic (1 hour batch job using Hadoop/Spark).
- **Request coalescing**: If 1000 requests for the same user's timeline arrive within a 100ms window, only one rebuild executes; others wait on the resulting promise.
- **Graceful degradation**: Serve "last known good" timeline from a backup Cassandra cache (1-hour stale), then async-refresh.

**TTR**: 30–60 minutes to fully warm cache for active users; passive users warm on first login.

### Failure 3: Manhattan KV Store Hot Partition (Celebrity Tweet)
**Scenario**: An A-list celebrity (100M followers) tweets. The fan-out service writes the tweet ID to 100M home timeline indexes. The Manhattan shards holding those indexes get hammered.

**Behavior**:
- Manhattan shards holding the celebrity's followers see a 100× write spike.
- p99 latency on those shards jumps from 5ms to 200ms.
- Fan-out backlog grows; lag visible in "tweet appears in followers' timelines" metric.

**Mitigation**:
- **Hybrid timeline**: For users with >10K followers, *don't* push on write. Pull on read instead.
- For users in the 1K–10K range: fan-out with rate limiting (max 50K writes/sec per tweet).
- For sub-1K users: immediate fan-out.

**TTR**: Tweets from celebrities are visible to followers within 5–30 seconds (vs. <1s for normal users). Acceptable tradeoff.

### Failure 4: Cross-DC Network Partition
**Scenario**: WAN link between US-East and EU-West fails. Twitter serves traffic from both DCs with cross-region replication.

**Behavior**:
- EU users continue to read tweets; new tweets posted in EU replicate locally but not to US.
- US users miss EU-originated tweets until partition heals (asymmetric visibility).
- Fan-out service queues cross-DC fan-outs in Kafka MirrorMaker; drained on recovery.

**TTR**: User-visible eventual consistency: typically 1–5 minutes after partition heal for full convergence.

### Failure 5: Snowflake ID Generator Clock Skew
**Scenario**: NTP failure causes one Snowflake node's clock to drift backwards by 100ms.

**Behavior**:
- Snowflake refuses to generate IDs while the clock is behind its last-generated timestamp (prevents duplicate IDs).
- That node returns errors for the drift duration (100ms).
- Clients retry against a different Snowflake instance.

**TTR**: < 1 second user-visible; node self-heals when clock catches up.

---

## 19. Capacity Planning Math (Bitly-Scale Twitter Numbers)

### Tweet Storage
- **500M tweets/day** × 280 chars (~ 1KB after metadata: user_id, timestamp, mentions, hashtags, media refs) = **500 GB/day** raw.
- With RF=3 replication: **1.5 TB/day**.
- Annual: 500 GB × 365 = **~180 TB/year** raw, **~540 TB/year** replicated.
- 10-year retention: **~5.4 PB**.
- Manhattan compression (LZ4 ~2×): **~2.7 PB** physical.

### Read Throughput
- **300K reads/sec** average; 1M reads/sec peak.
- Each timeline read = 1 Redis GET (3 ms p99) + 20 Manhattan reads for tweet content (hydration).
- Redis fleet: 300K req/sec / 100K req/sec/node = **3 Redis nodes** for timeline indices, plus replicas → ~20 nodes for HA.
- Manhattan fleet: 300K × 20 = **6M reads/sec** hydration → ~200 Manhattan nodes.

### Fan-Out Cost
- 500M tweets/day, avg 200 followers/tweet (long tail dominated by small accounts).
- **Total fan-out writes/day**: 500M × 200 = **100B writes/day** = ~1.16M writes/sec average.
- Without celebrity pull-model: would be ~10× more (top accounts have 100M+ followers).
- Fan-out service: 1.16M/sec ÷ 10K writes/sec/worker = **~120 fan-out workers** + 50% headroom.

### Search Indexing (Earlybird)
- All 500M tweets/day indexed in real-time into Lucene-based Earlybird shards.
- Each tweet generates ~50 index terms (tokens + hashtags + entities).
- **Index writes/day**: 500M × 50 = 25B postings/day = 290K postings/sec.
- Earlybird hot tier holds 7 days of tweets in memory: 7 × 500GB = **3.5 TB RAM** spread across shards.

### Media Storage
- 25% of tweets have media: 125M media uploads/day.
- Avg size 500KB (mostly images, some video) = **62.5 TB/day** ingest.
- Annual: ~23 PB; long-term archived to cold storage at ~$0.005/GB-month.

### Cost Envelope
- Manhattan (~200 nodes), Redis (~20), Earlybird (~50), fan-out workers (~120), GQL/REST API tier (~500), edge cache (~100) ≈ **~1000 nodes** at $25K/year fully loaded = **$25M/year compute**.
- Bandwidth egress: ~3 PB/day client traffic at $0.01/GB blended (heavy CDN offload) = **~$110M/year**.
- Media storage (hot + cold): ~$15M/year.

---

## 20. Multi-Region and Global Deployment

### Active-Active Architecture
- Twitter operates primarily from **us-east-1** (Atlanta-area DCs historically, then GCP) and **eu-west** (Dublin), with smaller PoPs in APAC.
- Both regions serve reads and writes; tweets replicate asynchronously.

### GDPR Data Residency
- EU users' PII (email, phone, IP logs) stored in EU DCs only.
- Tweets themselves are public content — replicated globally for low-latency reads.
- DM (direct messages) are stored in the originating user's home region; cross-region DM has slightly higher latency (~50ms vs. <10ms).

### Replication Lag
- Tweet write → global visibility: **2–5 seconds typical**, 10s p99.
- Acceptable because Twitter UX doesn't promise read-your-writes globally (it does promise it within the user's home region via stickiness).

### Conflict Resolution
- Tweet content is immutable (no edits historically; edit feature added in 2022 with version vectors).
- Retweet/like counters: eventually consistent via CRDT counter (G-counter).
- Username availability: globally coordinated via consensus (etcd/Zookeeper).

### Cross-Region Failover
- Route 53 health checks every 10s; failover DNS TTL of 60s.
- Full us-east-1 loss: traffic shifts to us-west and EU within 2–5 minutes.
- Recent unreplicated tweets (~5 sec window) may be temporarily invisible until restored from snapshot.

---

## 21. Operational Concerns

### Critical Alerts
| Metric | Threshold | Response |
|--------|-----------|----------|
| Tweet write p99 latency | > 500ms | Page on-call; check Manhattan + Snowflake |
| Fan-out lag (write → visible in followers' timelines) | > 30s | Check Kafka backlog, scale workers |
| Home timeline read p99 | > 200ms | Redis health + Manhattan hydration latency |
| Earlybird indexing lag | > 60s | Search shows stale results; scale indexers |
| Fan-out service error rate | > 0.1% | Often signals celebrity-tweet thundering herd |
| CDN cache hit rate (images) | < 90% | Check origin egress; possible cache pollution attack |

### Deployment Strategy
- **Canary**: 0.1% of traffic for 1 hour → 1% for 2 hours → 10% for 4 hours → 100%.
- **Auto-rollback** triggers: error rate >0.5% above baseline, latency p99 >20% above baseline.
- **Feature flags** via internal "Decider" service: enable per-country, per-user-bucket, gradually ramp.
- Deployments are continuous: ~50 deploys/day across the microservice fleet at peak.

### On-Call Runbook: Fan-Out Service Backlog
1. Check Kafka consumer lag: `kafka-consumer-groups --describe --group fanout-workers`.
2. If lag > 5 min and growing: a celebrity tweet may be amplifying. Look at recent high-follower tweets.
3. Mitigation: temporarily lower the fan-out fan-out cap (e.g., from 100K to 10K) to shed load.
4. Scale workers: HPA based on Kafka lag metric typically auto-scales, but manual bump may be needed.
5. Verify Manhattan write latency isn't the actual bottleneck.

### On-Call Runbook: Timeline Reads Returning Empty
1. Reproduce with a known test account.
2. Check Redis cluster: `INFO replication` — is the user's shard healthy?
3. If shard is down: failover to replica (usually automatic via Sentinel).
4. If shard is empty (cache lost): trigger rebuild from Manhattan; user sees fallback timeline.
5. Long-term: enable Redis AOF persistence to avoid full rebuilds.

---

## 22. Evolution and Future Improvements

### At 10× Scale (3B MAU, 5B tweets/day)
- Manhattan would need re-sharding to 10K+ nodes; gossip overhead becomes prohibitive. Migration to a hierarchical sharding scheme (region → shard → micro-shard).
- Fan-out economics break down further: pure pull-model timeline (Facebook News Feed style) with aggressive caching would replace push-based fan-out for all users, not just celebrities.
- Earlybird search would migrate to a distributed inverted-index store like Apache Pinot or ClickHouse for sub-second analytical queries.

### Technical Debt
- **Legacy Rails monolith remnants**: Some admin tooling and internal dashboards still hit a Rails app from 2010. Slow migration to Scala/JVM.
- **Manhattan's lack of secondary indexes**: forces denormalization everywhere; modern alternative would be FoundationDB or TiKV.
- **Fan-out heuristic constants** (10K-follower threshold) are hand-tuned; ML-based dynamic threshold per user behavior would improve efficiency.

### Future Capabilities
- **Edit window beyond 30 min**: Requires versioned tweet storage and view-time resolution; trade-off with retweet semantics (does a retweet show v1 or v2?).
- **Long-form posts (Notes, 4000+ chars)**: Already launched; requires different ranking signals because dwell-time is the engagement metric vs. instant scroll.
- **End-to-end encrypted DMs**: Twitter announced E2E DMs in 2023; full rollout requires key management infrastructure similar to WhatsApp's.
- **AI-generated timeline ranking**: Move from heuristic + simple ML to LLM-based "explainable" ranking ("why am I seeing this tweet?").

