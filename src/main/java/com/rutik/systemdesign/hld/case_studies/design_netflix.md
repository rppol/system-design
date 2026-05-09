# System Design: Netflix

## Table of Contents
1. Requirements Clarification
2. Scale Estimation
3. High-Level Architecture
4. Content Delivery and Open Connect CDN
5. Adaptive Bitrate Streaming
6. Video Transcoding Pipeline
7. Recommendation System
8. Database Architecture
9. Chaos Engineering
10. Microservices Architecture
11. Search
12. Bottlenecks and Solutions
13. Trade-offs
14. Interview Discussion Tips

---

## Intuition

> **Design intuition**: Netflix's central challenges are video delivery at global scale (solved by Open Connect CDN — Netflix's own 800+ PoP network) and personalized recommendations (ML-driven, multi-factor ranking). 90% of Netflix traffic is video bytes; CDN architecture is the dominant engineering concern.

**Key insight**: Netflix solved the CDN problem by building their own: Open Connect appliances (ISP-co-located servers) pre-populate with popular content during off-peak hours. Most Netflix video is served from within your ISP's network, never touching the internet backbone. This is why Netflix streams 4K reliably where YouTube struggles.

---

## 1. Requirements Clarification

### Functional Requirements
- **Stream Video**: Users can stream movies and TV shows on-demand with smooth playback
- **Browse and Search**: Users can browse catalog, search by title/genre/actor, view details
- **Recommendations**: Personalized content recommendations on the home screen
- **Upload (Admin)**: Content team can upload new movies/shows (not end-user uploads)
- **Billing**: Subscription management, payment processing, plan tiers (Basic, Standard, Premium)
- **User Profiles**: Multiple profiles per account (kids, adults), separate watch history
- **Continue Watching**: Resume playback from where user left off
- **Download**: Offline viewing on mobile (Premium plan)

### Non-Functional Requirements
- **High Availability**: 99.99% uptime — video must always be streamable
- **Low Latency Startup**: Video should start playing within 2 seconds of pressing play
- **Adaptive Quality**: Seamlessly adjusts video quality based on available bandwidth
- **Global Scale**: Serve users across 190+ countries
- **Fault Tolerant**: A single server/region failure should not interrupt active streams
- **Scalability**: Handle 10M+ concurrent streams at peak

### Out of Scope
- Live streaming (Netflix is primarily on-demand)
- Social features (sharing, reviews)
- Content licensing and DRM key management details

---

## 2. Scale Estimation

### Users and Traffic
- 200M paid subscribers globally
- Peak concurrent streams: **10M simultaneous streams**
- Average stream bitrate: 5 Mbps (1080p)
- Peak bandwidth: 10M * 5 Mbps = **50 Tbps** (Netflix is ~15% of global internet traffic)
- Requests per second (API): 200M DAU / 86,400 sec * avg 10 API calls = ~23K API RPS

### Content Library
- ~15,000 titles in the catalog
- Each title encoded in ~30+ quality variants (resolution + codec combinations)
- Average movie: 2 hours at highest quality (4K HDR) = ~50 GB per variant
- 15,000 titles * 30 variants * 10 GB avg per variant = **4.5 PB** for video content
- Plus thumbnails, metadata, subtitle files: additional ~100 TB

### Storage Growth
- Netflix adds ~500 new titles/month
- Each new title: 500 titles * 30 variants * 10 GB = **150 TB/month** new content

### CDN Cache
- 80/20 rule: 20% of content accounts for 80% of views
- Top 1,000 titles need to be aggressively cached at edge nodes
- Each Open Connect Appliance (OCA): 100-200 TB SSD storage

---

## 3. High-Level Architecture

```
                    +------------------+
                    |   Client Apps    |
                    | (iOS/Android/TV/ |
                    |  Web/Smart TV)   |
                    +--------+---------+
                             |
              +--------------+---------------+
              |                              |
     +--------v---------+        +----------v----------+
     |   Control Plane  |        |    Data Plane       |
     |   (AWS Cloud)    |        | (Open Connect CDN)  |
     +--------+---------+        +----------+----------+
              |                             |
     +--------v---------+                  |
     |   API Gateway    |        Video streams served
     |   (Zuul)         |        directly from OCA
     +--------+---------+        nodes embedded at ISPs
              |
     +--------v------------------------------------------+
     |              Netflix Microservices (700+)         |
     |                                                   |
     | +-------------+  +-------------+  +-----------+  |
     | | User Service|  | Catalog Svc |  | Search    |  |
     | +-------------+  +-------------+  | (ES)      |  |
     |                                   +-----------+  |
     | +-------------+  +-------------+  +-----------+  |
     | | Playback    |  | Recommend.  |  | Billing   |  |
     | | Service     |  | Service     |  | Service   |  |
     | +-------------+  +-------------+  +-----------+  |
     |                                                   |
     | +-------------+  +-------------+  +-----------+  |
     | | Encoding    |  | Analytics   |  | A/B Test  |  |
     | | Service     |  | (Flink)     |  | Platform  |  |
     | +-------------+  +-------------+  +-----------+  |
     +---------------------------------------------------+
              |
     +--------v---------+
     |   Data Stores    |
     | Cassandra | MySQL |
     | EVCache   | S3    |
     +------------------+

     Upload Path (Admin):
     Studio Upload → S3 (raw) → Encoding Farm (EC2 Spot)
         → 30+ encoded variants → Open Connect CDN
```

---

## 4. Content Delivery and Open Connect CDN

### Why Netflix Built Their Own CDN
- **Cost**: Paying commercial CDNs (Akamai, Cloudflare) for 50 Tbps = billions/year
- **Control**: Full control over cache eviction, pre-positioning, routing decisions
- **Performance**: Co-locate hardware inside ISPs — video travels fewer network hops
- **Custom Hardware**: Optimized for large sequential reads (video streaming), not general web assets

### Open Connect Appliances (OCA)
- Custom hardware servers installed **inside ISP data centers** at no cost to ISPs
- ISPs benefit: traffic stays local, lower transit costs, better user experience
- Each OCA: 100 TB to 200 TB of SSD + high-speed NICs (100 Gbps)
- There are thousands of OCAs in over 1,000 cities globally

### Content Pre-Positioning
Netflix does not wait for a cache miss to populate edge nodes — they **proactively push content**:

```
Daily Proactive Push (runs at off-peak hours, 2-4 AM local time):
  1. Analytics Service identifies top-500 titles per region for next 24 hours
  2. Pre-positioning algorithm determines which OCAs to push to
  3. Content is replicated from S3 origin to selected OCAs via Netflix backbone
  4. By the time users wake up, content is already at the edge

Benefits:
  - Near-zero cache miss rate for popular content
  - Origin S3 is rarely hit during peak hours
  - Completely eliminates buffering for popular titles
```

### Routing: Steering Service
- When a client initiates playback, it contacts Netflix's **Steering Service**
- Steering Service returns an ordered list of OCA IPs to try
- Selection criteria: geographic proximity, OCA health, available bandwidth, network path quality
- Client tries OCAs in order; falls back to next if connection fails

### CDN Fallback Hierarchy
```
Client Request for Video
    |
    v
1st: Local ISP OCA (fastest, same AS)
    |-- if unavailable --v
2nd: Regional OCA cluster
    |-- if unavailable --v
3rd: Netflix's own data center (origin, AWS S3)
```

---

## 5. Adaptive Bitrate Streaming

### Protocols Used
- **MPEG-DASH** (Dynamic Adaptive Streaming over HTTP): Used for most platforms
- **HLS** (HTTP Live Streaming): Required for Apple devices (iOS, macOS, Apple TV)
- Both work the same way: divide video into small chunks (2-10 second segments)

### Quality Ladder
Netflix encodes each title at multiple quality levels:

| Profile | Resolution | Video Bitrate | Audio |
|---------|-----------|--------------|-------|
| 240p    | 426x240   | 235 kbps     | 64 kbps |
| 360p    | 640x360   | 375 kbps     | 64 kbps |
| 480p    | 854x480   | 750 kbps     | 128 kbps |
| 720p    | 1280x720  | 2 Mbps       | 192 kbps |
| 1080p   | 1920x1080 | 4.3 Mbps     | 256 kbps |
| 1080p+  | 1920x1080 | 5.8 Mbps     | 320 kbps |
| 4K HDR  | 3840x2160 | 15.6 Mbps    | 640 kbps |

### How the Client Selects Quality

The client maintains a **bandwidth estimator** that measures download speed of recent chunks:

```
Adaptive Algorithm (simplified):
  1. Client downloads segment at current quality Q
  2. Measures: download_speed = segment_size / download_time
  3. Buffer: tracks how many seconds of video are buffered ahead
  4. Decision logic:
     - If buffer > 30 sec AND bandwidth > next_quality_bitrate * 1.5:
         upgrade quality
     - If buffer < 10 sec OR bandwidth < current_quality_bitrate:
         downgrade quality
     - Otherwise: stay at current quality
  5. Pre-fetch next 2-3 segments at decided quality
```

### Manifest File (MPD/M3U8)
The client first downloads a **manifest file** that lists all available quality variants and chunk URLs:
```
#EXTM3U
#EXT-X-STREAM-INF:BANDWIDTH=2000000,RESOLUTION=1280x720
https://oca1.netflix.com/title123/720p/chunk_001.m4s
#EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
https://oca1.netflix.com/title123/1080p/chunk_001.m4s
```

---

## 6. Video Transcoding Pipeline

### The Problem
A 2-hour movie in raw studio format (ProRes 4K) = **1-2 TB**. It must be:
- Encoded into 30+ quality variants
- Multiple codecs: H.264, H.265 (HEVC), AV1, VP9
- Subtitles embedded or as separate tracks
- Audio in multiple languages
- All of this before the title can go live

### Pipeline Architecture
```
Studio Delivery
     |
     v
[S3: Raw Content Bucket]
     |
     v
[Validation Service]
  -- checks file integrity, format compliance, metadata
     |
     v
[Job Scheduler]
  -- splits movie into parallel encoding jobs
  -- each job handles one (quality, codec) combination
     |
     v (parallel, 100s of jobs simultaneously)
[Encoding Farm: AWS EC2 Spot Instances]
  -- Spot instances for cost efficiency (60-90% cheaper)
  -- Each worker encodes one variant
  -- Uses FFmpeg under the hood with custom optimizations
     |
     v
[Quality Validation Service]
  -- automated quality checks (PSNR, VMAF scores)
  -- catches encoding artifacts, audio sync issues
     |
     v
[S3: Encoded Content Bucket]
     |
     v
[CDN Distribution]
  -- popular titles pushed to OCAs immediately
  -- others available on-demand from S3 origin
```

### Per-Scene Encoding Optimization
Netflix's innovation: **Variable Bitrate Encoding per Scene Complexity**
- Simple scenes (static backgrounds, talking heads) = lower bitrate needed
- Complex scenes (action, explosions, rapid motion) = higher bitrate needed
- Netflix analyzes each scene independently and allocates bitrate accordingly
- Result: Better quality at same or lower average bitrate vs. fixed-bitrate encoding

### Codec Strategy
- **H.264 (AVC)**: Universal compatibility, older devices
- **H.265 (HEVC)**: 40% better compression than H.264, newer devices
- **AV1**: 30-40% better than HEVC, royalty-free, used for mobile (battery efficient)
- Netflix encodes all titles in all supported codecs for device-specific serving

---

## 7. Recommendation System

### Why Recommendations Matter
- Netflix's homepage shows 40+ rows of personalized content
- 80% of watched content comes from recommendations (not search)
- Improving recommendations directly increases retention

### Collaborative Filtering
- **User-based**: "Users similar to you watched X"
- **Item-based**: "Users who watched A also watched B"
- Uses matrix factorization (SVD, ALS) to decompose user-item interaction matrix
- Input signals: watch history, ratings, search queries, time of day, device

### Content-Based Filtering
- Tags each title with attributes: genre, actors, director, tone, pacing
- Recommends titles with similar attribute vectors to user's history
- Good for new users (cold start) where collaborative filtering lacks data

### Deep Learning Models
- Netflix uses neural collaborative filtering (NCF) and transformer-based models
- Input: user embedding + item embedding → predicted rating/engagement score
- Trained on billions of viewing interactions

### A/B Testing at Scale
Netflix runs **hundreds of A/B tests simultaneously**:
```
A/B Test Framework:
  1. Users randomly assigned to treatment/control (user_id % N)
  2. Each experiment has a hold-out group
  3. Metrics tracked: play rate, completion rate, retention, cancel rate
  4. Statistical significance via automated system
  5. Winner rolled out to 100% of users

Example Experiment: "Does showing trailers autoplay increase click rate?"
  - Result: Yes, +10% click rate → rolled out
```

### Personalized Thumbnails
- The same title shows different thumbnails to different users
- ML model predicts which thumbnail a user will click based on:
  - Their watch history (action lover → action scene thumbnail)
  - Demographic signals
- System tests 10-20 thumbnail candidates per title and learns optimal per user segment

### Two-Tower Architecture for Recommendations
```
User Tower                    Item Tower
(User Features)              (Item Features)
    |                              |
    v                              v
[User Embedding]            [Item Embedding]
         \                   /
          \                 /
           v               v
         [Dot Product / Score]
                 |
                 v
         Ranking + Re-ranking
         (business rules, diversity)
                 |
                 v
         Personalized Homepage
```

---

## 8. Database Architecture

### Cassandra (User Activity, Viewing History)
- Stores: viewing history, pause/resume positions, ratings, interactions
- Why: high write throughput, multi-region replication, no complex joins
- Write-heavy: every play event, pause, skip generates a write
- Schema example:
```sql
CREATE TABLE viewing_history (
    user_id     UUID,
    content_id  UUID,
    profile_id  UUID,
    watched_at  TIMESTAMP,
    position_sec INT,        -- resume point in seconds
    completed   BOOLEAN,
    PRIMARY KEY ((user_id, profile_id), watched_at, content_id)
) WITH CLUSTERING ORDER BY (watched_at DESC);
```

### MySQL (Billing, Subscriptions)
- ACID transactions required for financial data
- Schema: users, subscriptions, payments, invoices, plans
- Replicated with read replicas for reporting
- Sharded by user_id at large scale
- Why not NoSQL: billing requires strong consistency (you cannot double-charge)

### EVCache (Netflix's Memcached Wrapper)
- Distributed cache across all AWS regions
- Stores: session tokens, user preferences, computed recommendation lists, rate limits
- Netflix built EVCache as a wrapper around Memcached that:
  - Replicates cache writes to multiple zones automatically
  - Handles zone failover transparently
  - Provides metrics and circuit breaker integration

### S3
- Raw video files (originals)
- Encoded video chunks (before CDN distribution)
- Thumbnails and artwork
- Model artifacts (recommendation models)
- Log archives

### Apache Kafka
- Event bus for all user activity events (play, pause, search, click)
- Feeds: analytics pipeline, recommendation model training, billing events
- 700B+ events/day

---

## 9. Chaos Engineering

### Philosophy
Netflix operates on the premise: **"Everything will fail — build systems that work despite failures"**

### Chaos Monkey
- Tool that **randomly terminates EC2 instances** in production during business hours
- Forces engineers to design services that automatically recover
- If a service goes down and takes the site with it, that's a design flaw exposed proactively

### Chaos Kong
- Terminates an **entire AWS availability zone**
- Tests that Netflix can survive a full AZ outage
- Run periodically, not randomly

### Failure Injection Testing (FIT)
- Injects: latency, errors, resource exhaustion into service dependencies
- Example: inject 500ms latency into recommendation service call from homepage
- Ensures the homepage degrades gracefully (shows generic rows) instead of failing entirely

### Circuit Breaker Pattern (Hystrix)
```
Service A calls Service B:
  - Circuit starts CLOSED (requests flow normally)
  - If error rate > 50% in last 10 sec: circuit opens (OPEN state)
  - In OPEN state: Service A returns cached/fallback response immediately
  - After 5 seconds: circuit moves to HALF-OPEN, allows one request through
  - If that request succeeds: circuit closes again
  - If it fails: circuit stays open

Fallback for recommendation service failure:
  - Return generic "Top 10 Most Popular" list
  - User sees degraded but functional homepage
```

---

## 10. Microservices Architecture

### Scale
- Netflix operates **700+ microservices**, each independently deployable
- Teams own their services end-to-end (you build it, you run it)
- Services communicate via REST/gRPC over Netflix's internal service mesh

### Key Services
| Service | Responsibility |
|---------|---------------|
| Playback Service | DRM license, OCA selection, manifest generation |
| Catalog Service | Title metadata, availability by region |
| User Service | Account management, profiles |
| Recommendation Service | Personalized content ranking |
| Search Service | Full-text search |
| Streaming Service | Video chunk delivery via OCA |
| Billing Service | Subscription, payment, invoicing |
| Analytics Service | Event ingestion and processing |
| A/B Test Service | Experiment assignment and measurement |
| Encoding Service | Video transcoding job management |

### Service Discovery (Eureka)
- Netflix's open-source service registry
- Each service instance registers itself on startup with IP, port, health endpoint
- Clients query Eureka to find available instances of a dependency
- Eureka is replicated across all regions for availability

### API Gateway (Zuul)
- Single entry point for all client API calls
- Handles: authentication, rate limiting, routing, A/B test assignment
- Filters applied per request (auth check, device type detection, request logging)

---

## 11. Search

### Requirements
- Search by title, actor, director, genre, description
- Multi-language support (190 countries, dozens of languages)
- Near-instant results with typo tolerance
- Personalized ranking (your recently watched genres rank higher)

### Architecture
- **Elasticsearch** cluster with per-language analyzers
- Index per language for proper stemming and tokenization (English vs. Japanese vs. Arabic behave differently)
- Each document:
```json
{
  "content_id": "tt1234567",
  "title": "Stranger Things",
  "title_en": "Stranger Things",
  "title_es": "Cosas más extrañas",
  "description": "In a small town...",
  "genres": ["sci-fi", "thriller", "horror"],
  "cast": ["Millie Bobby Brown", "Winona Ryder"],
  "year": 2016,
  "rating": 8.7,
  "tags": ["supernatural", "80s", "kids"]
}
```

### Ranking Signals
1. Text relevance score (BM25 from Elasticsearch)
2. Title popularity (global view count)
3. User personalization (user's genre preferences)
4. Recency (newly added content gets a boost)
5. Regional availability (only show available content)

---

## 12. Bottlenecks and Solutions

| Bottleneck | Impact | Solution |
|---|---|---|
| 10M concurrent streams | Massive bandwidth demand | Open Connect CDN, ISP-embedded OCAs |
| Video startup latency | Users abandon if > 2 sec | OCA proximity, pre-positioned content, adaptive bitrate |
| Recommendation cold start | New users get poor recs | Content-based filtering + popularity-based fallback |
| Encoding new title | Hours of compute time | Parallel EC2 Spot instances, scene-level parallelism |
| Cache miss for cold content | High origin S3 load | Tiered caching, pre-positioning for new releases |
| Database hotspot (popular titles) | Read overload on Cassandra | EVCache in front of Cassandra, read replicas |
| Region-level AWS failure | Site goes down | Multi-region active-active, Chaos Kong testing |

---

## 13. Trade-offs Made

### Own CDN vs. Commercial CDN
- **Choice**: Build Open Connect (own CDN)
- **Reason**: At Netflix's scale, commercial CDN costs are prohibitive; custom hardware optimized for video
- **Trade-off**: Massive upfront investment in hardware, ISP relationships, and engineering

### Cassandra vs. MySQL for Viewing History
- **Choice**: Cassandra for viewing history, MySQL for billing
- **Reason**: Viewing history is write-heavy, append-only, no transactions needed; billing requires ACID
- **Trade-off**: Two different database systems to operate

### MPEG-DASH vs. HLS
- **Choice**: Support both
- **Reason**: MPEG-DASH is the open standard (Netflix prefers it), but HLS is mandatory for Apple devices
- **Trade-off**: Increased encoding and CDN storage cost (maintain both formats)

### Spot Instances for Encoding
- **Choice**: AWS EC2 Spot instances for encoding farm
- **Reason**: 60-90% cost savings; encoding is interruptible (can checkpoint and restart)
- **Trade-off**: A Spot interruption delays a title's encoding; mitigated by checkpointing

### Microservices vs. Monolith
- **Choice**: 700+ microservices
- **Reason**: Independent deployability, team autonomy, fault isolation, polyglot persistence
- **Trade-off**: Massive operational complexity; distributed tracing, service mesh, and observability investment required

---

## 14. Interview Discussion Tips

### How to Structure Your Answer (45-minute interview)
1. **Clarify requirements** (5 min): streaming, upload, recommendations, billing, search
2. **Scale estimation** (5 min): 200M users, 10M streams, 50 Tbps bandwidth, PB storage
3. **High-level architecture** (5 min): control plane vs. data plane separation
4. **Content delivery deep dive** (10 min): Open Connect CDN, pre-positioning, OCA routing
5. **Adaptive streaming** (5 min): DASH/HLS, quality ladder, client algorithm
6. **Transcoding pipeline** (5 min): parallel encoding, EC2 Spot, quality validation
7. **Database choices** (5 min): Cassandra for activity, MySQL for billing, EVCache
8. **Recommendations** (5 min): collaborative filtering, A/B testing, personalized thumbnails

### Key Things Interviewers Look For
- Understanding of **CDN architecture** and why Netflix built their own
- Explanation of **adaptive bitrate streaming** (not just "it adjusts quality")
- Awareness of the **transcoding pipeline** as a critical path for new content
- Database selection rationale (Cassandra for scale, MySQL for financial data)
- Understanding of **fault tolerance** (Circuit breaker, Chaos Engineering mindset)
- Separation of **control plane** (API, auth, metadata) from **data plane** (video bytes)

### Common Mistakes to Avoid
- Saying "use a CDN" without explaining Netflix's Open Connect differentiation
- Ignoring the transcoding pipeline (it's a major subsystem)
- Using only one database for everything
- Not mentioning fault tolerance and graceful degradation
- Forgetting the client-side ABR algorithm

### Follow-up Questions You May Get
- "How does Netflix handle a new hit show with millions of simultaneous viewers?" — Pre-positioning, capacity planning, auto-scaling
- "How would you design the recommendation system in detail?" — Two-tower model, feature engineering, training pipeline
- "How do you ensure 99.99% uptime?" — Multi-region, circuit breakers, Chaos Monkey, graceful degradation
- "How does DRM work?" — Widevine/PlayReady/FairPlay, license server, encrypted video chunks
- "How would you optimize streaming for mobile (battery + data)?" — AV1 codec, adaptive quality, background prefetch limits

### Numbers to Remember
- 200M subscribers, 10M peak concurrent streams
- 15% of global internet traffic
- 1B+ hours watched per day
- 700+ microservices
- OCAs in 1,000+ cities, embedded in ISPs
- 30+ encoded variants per title
- 80% of views come from recommendations
- Spot instances: 60-90% cheaper than on-demand for encoding
