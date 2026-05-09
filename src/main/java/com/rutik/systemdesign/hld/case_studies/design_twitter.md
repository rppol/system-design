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
