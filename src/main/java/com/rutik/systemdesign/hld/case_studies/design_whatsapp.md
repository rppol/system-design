# Design WhatsApp

## Intuition

> **Design intuition**: WhatsApp's core challenges are message delivery guarantees (at-least-once with acknowledgments), real-time presence (online/offline for 2 billion users), and end-to-end encryption (keys on device, not server). The persistent WebSocket connection architecture is what enables real-time message delivery without polling.

**Key insight**: WhatsApp stores messages only until delivered — once a message reaches the recipient's device, it's deleted from servers. This is what enables end-to-end encryption: messages exist on servers only transiently, and in encrypted form. Server design is built around this "store and forward" with delivery receipts model.

---

## System Overview

WhatsApp is a cross-platform messaging application with:
- 2 billion monthly active users
- 100 billion messages sent per day (65B+ at peak)
- 1-to-1 messaging, group chats (up to 1024 members), voice/video calls
- End-to-end encryption for all messages
- Media sharing (photos, videos, documents, voice notes)
- Read receipts (single tick → double tick → blue ticks)

---

## Step 1: Requirements Clarification

### Functional Requirements
- One-on-one messaging with delivery and read receipts
- Group messaging (up to 1024 members)
- Media sharing (images, video, audio, documents)
- Online presence and "last seen" status
- Message history (accessible after app reinstall)
- Push notifications for offline users
- Voice and video calls (out of scope for this design)

### Non-Functional Requirements
- **Availability**: 99.99% (52 min/year downtime)
- **Latency**: Messages delivered in < 500ms for online users
- **Consistency**: Eventual consistency acceptable (messages may arrive slightly out of order)
- **Durability**: Messages must not be lost in transit
- **Scale**: 65 billion messages/day peak

### Out of Scope
- Payment features, status/stories, business API

---

## Step 2: Scale Estimation

```
Users: 2 billion MAU, ~500M DAU
Messages: 65 billion/day = 750,000 messages/sec at peak
           Average: 750K/sec, peak: ~2.5M/sec
Media: ~20% of messages contain media
       130B media messages/day × avg 500KB = 65 PB/day (WAY too much!)
       → Media stored on S3/CDN, messages only store URLs
       → Realistic: 65B × 300 bytes (avg text) = ~19 TB/day for message text

Connections: 500M DAU online simultaneously ≈ 200M concurrent WebSocket connections
             (users aren't all online at the same time; assume 40% online)
```

---

## Step 3: High-Level Architecture

```
┌─────────────┐     ┌─────────────┐
│  iOS App    │     │  Android    │
└──────┬──────┘     └──────┬──────┘
       │                   │
       └──────────┬─────────┘
                  │ HTTPS/WebSocket
                  ▼
         ┌────────────────┐
         │  Load Balancer  │
         └───────┬────────┘
                 │
    ┌────────────┼────────────┐
    │            │            │
    ▼            ▼            ▼
┌─────────┐ ┌─────────┐ ┌─────────┐
│  Chat   │ │  Chat   │ │  Chat   │
│ Server 1│ │ Server 2│ │ Server N│
└────┬────┘ └────┬────┘ └────┬────┘
     │            │            │
     └────────────┼────────────┘
                  │
     ┌────────────┼────────────┬──────────────────┐
     │            │            │                  │
     ▼            ▼            ▼                  ▼
┌─────────┐ ┌─────────┐ ┌──────────┐      ┌──────────┐
│  Redis  │ │ Kafka   │ │  Media   │      │  User/   │
│(presence│ │(message │ │ Service  │      │ Group DB │
│+routing)│ │ queue)  │ │ (S3+CDN) │      │(Cassandra│
└─────────┘ └────┬────┘ └──────────┘      └──────────┘
                 │
          ┌──────┴──────┐
          │             │
          ▼             ▼
   ┌─────────────┐  ┌──────────┐
   │  Message    │  │  Push    │
   │  Store      │  │Notification│
   │ (Cassandra) │  │ Service  │
   └─────────────┘  └──────────┘
```

---

## Step 4: Core Components Deep Dive

### 4.1 WebSocket Connection Management

WhatsApp maintains a persistent WebSocket connection for each online user.

**Why WebSocket?**
- Bidirectional: server can push messages without client polling
- Low overhead: no HTTP headers per message
- Real-time: sub-100ms delivery

**Connection mapping** (critical for routing):
```
When User B connects → ChatServer N handles the connection
Redis stores: userId_B → serverN

When User A sends to User B:
  1. ChatServer (handling A's connection) looks up: where is B?
  2. Redis returns: serverN
  3. ChatServer forwards to serverN
  4. serverN delivers to B's WebSocket
```

```
Redis key: conn:{userId}
Value:     {serverId, socketId, connectedAt}
TTL:       30 seconds (renewed by heartbeat every 10s)
```

If TTL expires → user is considered offline → route to push notification.

---

### 4.2 Message Flow

**Happy path (both online)**:
```
Alice sends "Hey Bob!"
      │
      ▼ (WebSocket)
Chat Server A
      │
      ├─► Redis: lookup Bob's server → Server B
      │
      ├─► Kafka: persist message (async, for reliability)
      │
      └─► Chat Server B (direct connection or message broker)
                │
                ▼ (WebSocket)
              Bob sees "Hey Bob!" + delivery tick ✓
              Bob's app sends ACK
                │
                ▼
Chat Server B → Chat Server A → Alice's app updates to ✓✓
```

**Message delivery states**:
```
Sent     (✓)   — Message reached WhatsApp servers
Delivered(✓✓)  — Message reached recipient's device
Read     (✓✓blue) — Recipient opened the conversation
```

**Offline recipient**:
```
Alice sends to offline Bob
      │
      ▼
Chat Server checks Redis: Bob not connected
      │
      ├─► Store message in Cassandra (message queue for Bob)
      │
      └─► Push Notification Service → APNs (iOS) or FCM (Android)
              │
              ▼
           Bob's device wakes up, connects WebSocket
              │
              ▼
           Server delivers queued messages
```

---

### 4.3 Message Storage

**WhatsApp's actual approach**: Messages are stored on the device, NOT long-term on servers.

- Server stores messages until they're delivered to all recipients
- Once delivered, server deletes (or can archive in encrypted form)
- This is enforced by the end-to-end encryption — server can't read messages anyway

**Schema (Cassandra)**:
```
Table: messages
  conversation_id: UUID      (partition key — hash of sender+receiver for 1:1)
  message_id:      TIMEUUID  (clustering key — time-ordered within conversation)
  sender_id:       UUID
  content:         BLOB      (encrypted)
  message_type:    TEXT      (TEXT, IMAGE, VIDEO, AUDIO, DOCUMENT)
  media_url:       TEXT      (if media)
  status:          TEXT      (SENT, DELIVERED, READ)
  created_at:      TIMESTAMP

Table: user_message_receipt
  user_id:         UUID      (partition key)
  message_id:      TIMEUUID
  delivered_at:    TIMESTAMP
  read_at:         TIMESTAMP
```

**Why Cassandra?**
- Write-heavy (65B writes/day)
- No complex queries (lookup by conversation_id + time range)
- Naturally distributed, no single point of failure
- TTL for auto-expiry when messages are delivered

---

### 4.4 Group Messaging

**Challenge**: 1 message → potentially 1024 deliveries

**Fanout approaches**:

*Option A: Fanout on write (WhatsApp's approach)*
```
Alice sends to Group G (1024 members)
      │
      ▼
Group Service fetches member list
      │
      ▼
Creates 1024 individual message copies in Kafka
      │
      ▼
Each member's Chat Server delivers to their WebSocket (or offline queue)
```
Pros: Simple; member-specific delivery tracking
Cons: 1024× write amplification per group message

*Option B: Fanout on read*
- Store one copy, each member reads from group log
- Pros: low write amplification
- Cons: complex read path, harder delivery tracking per member

*Optimization for large groups*: Don't fan out to offline members immediately. Only deliver to online members. Offline members pull group messages on reconnect from a group message log.

---

### 4.5 End-to-End Encryption (Signal Protocol)

WhatsApp uses the Signal Protocol for E2E encryption. Key concepts:

**Initial Key Exchange (X3DH — Extended Triple Diffie-Hellman)**:
```
Alice and Bob each publish to server:
  - Identity key (long-term)
  - Signed prekey (medium-term)
  - One-time prekeys (single use)

Alice can compute a shared secret using Bob's public keys
WITHOUT Bob being online — this is the magic of X3DH
```

**Message Encryption (Double Ratchet Algorithm)**:
```
Every message uses a different key (forward secrecy + break-in recovery)
Key chain: RootKey → ChainKey → MessageKey (new key per message)

Even if an attacker gets one message's key:
  - Can't decrypt past messages (forward secrecy)
  - Future messages will use different keys (break-in recovery)
```

**What the server sees**: Only encrypted blobs + metadata (sender, recipient, timestamp, size). WhatsApp cannot read message content.

**Key storage challenge**: Private keys stored ONLY on device. If phone is lost, messages are gone unless backed up. Backup encryption is separate (iCloud/Google Drive with user-controlled key).

---

### 4.6 Online Presence and Last Seen

```
User connects   → Set online status in Redis, TTL=30s
Heartbeat       → Client sends heartbeat every 10s → renews Redis TTL
User disconnects→ Redis TTL expires OR disconnect event removes key
Last seen       → Written to User DB when user goes offline
```

**Redis structure**:
```
Key: presence:{userId}
Value: {status: ONLINE, lastSeen: timestamp, serverId: ...}
TTL: 30 seconds
```

**Privacy settings**: Users can hide last seen, profile photo, status from specific people. This is a filter at read time, not write time.

---

### 4.7 Media Sharing

```
User selects photo
      │
      ▼
Client compresses (JPEG quality reduction) and encrypts media
      │
      ▼
Upload to S3 via pre-signed URL (bypasses chat servers for large files)
      │
      ▼
Client sends message with media URL + encryption key
      │
      ▼ (encrypted message travels via normal chat flow)

Recipient receives message with URL + decryption key
      │
      ▼
Downloads from CloudFront CDN (closest edge node)
      │
      ▼
Decrypts locally
```

Media on S3 is encrypted with a randomly generated key. The key travels with the message (encrypted). Even Amazon can't read the media.

---

## Step 5: Database Design Summary

| Data | Database | Why |
|------|----------|-----|
| Messages in transit | Cassandra | Write-heavy, TTL, distributed |
| User profiles | PostgreSQL | Relational, ACID, infrequently written |
| Contacts/Social graph | Redis + PostgreSQL | Fast lookup + durable storage |
| Online presence | Redis | Ephemeral, fast TTL, pub/sub |
| Connection routing | Redis | Low-latency lookup |
| Media files | S3 + CloudFront | Blob storage + CDN delivery |
| Group membership | Cassandra | Large groups, write-heavy |

---

## Step 6: Key Trade-offs

| Decision | Choice | Trade-off |
|----------|--------|-----------|
| Message storage | Mostly on-device | Privacy + E2E encryption, but messages lost with phone |
| Fanout for groups | Write fanout | Delivery tracking per member, but write amplification |
| Consistency | Eventual | Messages may arrive out of order in rare cases; use timestamps to reorder |
| Compression | Client-side | Reduces server load + bandwidth, but higher client CPU |
| Presence tracking | Redis TTL | Fast and scalable, but ~30s staleness |

---

## Step 7: Interview Discussion Points

**Q: How would you handle a message sent to 1 billion users (WhatsApp broadcast)?**
Broadcast to 1B users is inherently expensive. Solutions:
- CDN-based push (content hash, pull model)
- Tiered delivery: online users first, offline queued
- Rate-limited fanout workers
- In practice, WhatsApp limits broadcast lists to 256 contacts

**Q: How do you guarantee message ordering in group chats?**
- Each message gets a server-assigned `message_id` (TIMEUUID in Cassandra)
- Clients display by timestamp; occasional reordering is acceptable
- For strict ordering: Kafka partition per group (all messages for a group → same partition → guaranteed order)

**Q: What happens if a Chat Server crashes?**
- WebSocket connections are lost; clients detect TCP disconnect
- Clients reconnect to a different server via load balancer
- Redis shows user as offline until reconnect
- Kafka messages for the crashed server are re-processed from last committed offset

**Q: How would you design the call feature?**
- Separate from messaging: WebRTC for peer-to-peer media
- Signaling server (like a Chat Server) for SDP negotiation
- TURN servers for NAT traversal
- Not covered in this design scope

**Q: How do you handle multi-device support?**
- Each device has its own identity keys
- Server maintains a device list per user
- Messages are encrypted separately for each device
- Web WhatsApp uses key linking to the phone's key

---

## 10. Erlang/OTP Heritage and the Actor Model

WhatsApp's choice of Erlang/OTP at its founding (2009) is foundational to understanding how 50 engineers served 450M users in 2014 — roughly **9M users per engineer**, an efficiency ratio unmatched in the industry.

### Why Erlang?
- **Lightweight processes**: Each Erlang process consumes ~2KB of memory (vs ~1MB for a JVM thread or ~8MB for a native pthread). A single BEAM VM hosts millions of processes.
- **Share-nothing concurrency**: Processes communicate only via message passing. No shared mutable state means no locks, no deadlocks.
- **"Let it crash" philosophy**: Supervision trees restart failed processes within microseconds. The system self-heals rather than defensively coding against every error path.
- **Hot code reloading**: Deploy new code without dropping any of the ~2M long-lived TCP connections per server.

### One Process Per Phone Number
At WhatsApp, each registered phone number maps to a long-lived Erlang process (an `ejabberd` user session). When a message arrives for user X, the routing layer:
1. Looks up the PID (process ID) of user X's session on whichever server hosts it (via Mnesia distributed table).
2. Sends an Erlang message `{deliver, MsgPayload}` to that PID.
3. The session process pushes the message over its WebSocket (or queues it if offline).

Each server (typically a FreeBSD box with 96GB RAM) handled **2M+ concurrent connections**. In 2012 WhatsApp announced a single server hitting 2M TCP connections — a benchmark few competitors matched.

### Mnesia → Custom Storage Evolution
- **Early (2009–2012)**: Mnesia (Erlang's built-in distributed DB) held offline message queues. Excellent for sub-millisecond reads but caps out at ~4GB per table.
- **Mid (2012–2016)**: Sharded Mnesia, then custom on-disk storage built directly on raw block devices (bypassing the file system overhead).
- **Post-Facebook acquisition**: Migration to RocksDB-based stores and integration with Facebook's TAO/Cassandra infrastructure.

---

## 11. Failure Scenarios and Recovery

A principal-engineer review of WhatsApp must walk through specific failure modes — not just "what if a server dies" but the exact protocol behavior and time-to-recovery.

### Failure 1: Chat Server Pod Crashes Mid-Send

**Scenario**: User A sends a message to User B. The chat server holding A's WebSocket crashes after receiving the message but before persisting it to Kafka.

**Protocol behavior**:
- The client never received an ACK (server-side message_id).
- Client's send queue still holds the message marked as "pending".
- Client detects TCP RST or heartbeat timeout (5s).
- Client reconnects via load balancer to a new chat server.
- Client replays unACKed messages from its local queue, deduplicated by client-generated UUID (idempotency key).

**TTR**: 5–10 seconds for the user; zero message loss because the client retains the message until server ACK.

### Failure 2: Cassandra Node Failure During Message Store

**Scenario**: Replication factor 3, consistency level LOCAL_QUORUM (writes need 2/3 acks). One Cassandra node in the local datacenter dies during a write.

**Behavior**:
- Coordinator still receives 2 acks → write succeeds.
- Hinted handoff stores the missed write on a peer node; replayed when the failed node returns (default hint TTL: 3 hours).
- If node down > 3 hours: a repair (`nodetool repair`) is required to re-sync.
- Reads at LOCAL_QUORUM continue succeeding from the 2 live replicas.

**TTR for cluster**: Node restart ~2 minutes; full repair ~30 minutes for a single node's range.

### Failure 3: Cross-Datacenter Network Partition

**Scenario**: WAN link between US and EU datacenters fails. EU users cannot reach US-hosted chat sessions and vice versa.

**Behavior**:
- Each DC continues serving its local users; chat sessions for users whose "home" DC is unreachable get queued in the local DC's Kafka.
- Cross-DC message replication (using MirrorMaker or custom Erlang dist) buffers writes.
- On partition heal: backlogged messages are drained in arrival order; clients see a burst of "delayed" messages.

**TTR**: Depends on partition duration. WhatsApp's offline queue retains undelivered messages for **30 days** — a partition lasting hours has zero user-visible message loss, only delay.

### Failure 4: Push Notification Provider (APNs / FCM) Outage

**Scenario**: Apple's APNs (Apple Push Notification service) has an outage; iOS users with backgrounded apps cannot be woken.

**Behavior**:
- WhatsApp's push notification service queues notifications with exponential backoff retry (1s, 2s, 4s, 8s … capped at 5 min).
- Messages still arrive instantly when the user opens the app (fetched from offline queue).
- On APNs recovery, a thundering herd of queued pushes is rate-limited (10K/sec per app token) to avoid being blocklisted by Apple.

**TTR**: User-visible impact = 0 for foregrounded apps; backgrounded apps see notifications delayed until APNs recovers (historically 1–6 hours for major APNs incidents).

### Failure 5: Redis Presence Store Loses a Shard

**Scenario**: A Redis shard storing presence state (online/offline + last_seen) crashes; replica promoted but state lost in the 30s window.

**Behavior**:
- Presence is "soft state" — derived from active TCP connections, not authoritative.
- After failover, presence resyncs from chat servers' connection tables within 60 seconds.
- During the gap, contacts see "last seen unknown" (graceful degradation).

**TTR**: 60–90 seconds, no message impact.

---

## 12. Capacity Planning Math

Back-of-envelope numbers for a WhatsApp-scale deployment. These are the numbers a principal engineer should be able to derive in an interview without referring to notes.

### Messaging Throughput
- **2B users, 100B messages/day** = 100,000,000,000 ÷ 86,400 = **~1.16M messages/sec average**.
- Peak factor 3× (evenings in populous timezones) = **~3.5M messages/sec peak**.
- Average message size = 5KB (mostly small text + protocol overhead).
- **Inbound bandwidth average**: 1.16M × 5KB = 5.8 GB/sec = **~50 TB/day** of message payload.
- Outbound fanout (group chats avg 5 recipients × 0.2 of all msgs are group) = ~1.4× write-side fan-out.

### Storage Math
- **Server-side retention until delivery**: 7 days for undelivered messages (then dropped).
- Working set assumption: 5% of messages stay undelivered > 1 hour (users offline) × 7 days × 5KB ≈ **3.5 PB** of in-flight queue storage.
- After Cassandra RF=3: **10.5 PB** raw disk required.
- After compression (Snappy ~2.5× on text): **~4.5 PB** physical.

### Media Storage
- **1B photo/video shares/day**, average 50KB after WebP/H.264 compression → **50 TB/day** ingest.
- Media retention: 30 days on server (clients download and keep locally).
- Hot storage: 30 × 50TB = **1.5 PB** in blob store (S3-equivalent with cross-region replication).
- Annual cold/archive (regulatory retention for some jurisdictions): **~18 PB/year**.

### Connection Capacity
- 2B users; ~500M concurrent online at peak (25% concurrency).
- Per server: 2M concurrent WebSocket connections (Erlang BEAM benchmark).
- **Servers needed for connection termination**: 500M ÷ 2M = **~250 chat servers** at peak.
- Add 50% headroom + multi-DC redundancy → ~500 physical chat servers.

### Voice/Video Calls
- **2B call minutes/day** = 2B ÷ 1,440 = **~1.4M concurrent call legs** at peak.
- Voice call: ~32 kbps Opus = 4 KB/sec/leg.
- Video call: ~500 kbps VP9 = 62 KB/sec/leg.
- Most call traffic is **peer-to-peer (WebRTC)**; TURN relay needed for ~20% (NAT traversal failures).
- TURN bandwidth at peak: 1.4M × 0.2 × 30 KB/sec avg = **~8 GB/sec relay throughput** across the TURN fleet.

### Cost Estimate (Order-of-Magnitude)
- Compute: 500 chat servers + 200 Cassandra nodes + 100 Kafka brokers + 50 Redis nodes ≈ **~1000 boxes** at ~$30k/year fully loaded = **$30M/year compute**.
- Bandwidth: 50 TB/day inbound × 2 (egress fanout) = 100 TB/day egress at $0.02/GB blended = **~$700M/year** (most heavily discounted via private peering and Facebook's own network).
- Blob storage: 18 PB/year × $0.023/GB-month × 12 = **~$5M/year** for S3-equivalent.
- Total infra envelope: **~$1B/year** at this scale — efficient relative to Facebook Messenger's larger spend.

---

## 13. Multi-Region and Global Deployment

WhatsApp operates from multiple datacenters globally, with users "homed" to the closest region but able to communicate with users on any other region.

### Active-Active with User Affinity
- Each user has a **home region** assigned at registration based on phone number country code (and updated on roaming).
- All sessions, presence, and offline queues for a user live in their home region.
- Cross-region delivery uses a global routing fabric: chat server in EU receives a message destined for a US user → forwards via the WAN link to a US chat server → US server pushes to recipient.

### Data Residency and Regulatory Compliance
- **Brazil, India, Indonesia, EU**: Local data residency requirements pin user metadata (contact lists, last-seen, profile) to in-country DCs.
- E2E encryption simplifies compliance: WhatsApp servers never see plaintext, so most jurisdictions accept "ciphertext-in-transit storage" as not constituting personal data under GDPR Article 4(1).
- The **2021 Brazil ruling** required local data storage for user metadata; WhatsApp built a São Paulo DC in response.

### Cross-Region Replication
- **Synchronous within a region**: Cassandra LOCAL_QUORUM for low-latency writes (~5ms).
- **Asynchronous across regions**: Cassandra EACH_QUORUM is too slow (cross-Atlantic ~80ms RTT). Replication is async via Cassandra's NetworkTopologyStrategy with eventual consistency.
- Typical cross-region replication lag: **50–200ms** under normal load; up to several seconds during peak.

### Conflict Resolution
- E2E encryption means messages are immutable ciphertext blobs — no merge conflicts possible.
- Profile updates (display name, avatar) use **last-writer-wins** with vector clocks tracking the originating region.
- Group membership uses CRDT-like semantics: an "add member" event from region A and "remove member" from region B converge deterministically.

### Failover Drill: Region Loss
- If the EU region is fully lost (rare — earthquake, cable cut, AWS-style regional outage):
- DNS-based traffic steering (Route 53 / FB internal equivalent) redirects EU users to the closest healthy DC (typically US-East for Western Europe, Singapore for Eastern Europe).
- Users re-authenticate; sessions are rebuilt; offline queue replicas (async-replicated to US) are promoted.
- **Time to recovery**: 5–15 minutes for new traffic to be served; ~30 minutes for full state convergence.

---

## 14. Operational Concerns

### Key Metrics to Alert On
| Metric | Threshold | Why It Matters |
|--------|-----------|----------------|
| Message delivery p99 latency | > 2s | E2E user perception of "instant" breaks above 2s |
| WebSocket reconnect rate | > 5% of fleet/min | Indicates network or server health degradation |
| Kafka consumer lag (offline queue) | > 30s | Messages may not be delivered when user comes online |
| Cassandra read p99 | > 50ms | Will cascade to chat server timeouts |
| APNs / FCM push acceptance rate | < 95% | Provider issue; backgrounded users miss notifications |
| TURN relay CPU utilization | > 70% | Call quality degradation imminent |
| Erlang process count per node | > 2.5M | Approaching BEAM scheduler limits |
| Cross-DC replication lag | > 500ms | Recent messages may not failover cleanly |

### Deployment Strategy
- **Hot code reload (Erlang)** for non-breaking changes: deploy without dropping connections. The BEAM VM swaps modules atomically.
- **Rolling restart with connection draining** for breaking changes: drain TCP connections gradually (5% of fleet at a time), forcing clients to reconnect to upgraded servers.
- **Canary fleet**: 1% of traffic served by new build for 24h; auto-rollback on >0.1% error rate increase.
- **Feature flags** via centralized config service; staged rollout by country code and user_id hash bucket.

### On-Call Runbook Highlights
**Runbook: Sudden spike in message delivery latency**
1. Check Grafana for which DC is affected.
2. If single DC: check Cassandra cluster health (`nodetool status`) — node down, GC pause, or compaction storm.
3. Check Kafka consumer lag — if > 30s, scale out consumer group.
4. If multi-DC: check cross-DC WAN link saturation (often a single tenant flooding).
5. Mitigation: shed load by rate-limiting new connections (allows backlog to drain).
6. Escalate to Erlang infra on-call if BEAM scheduler utilization > 80%.

**Runbook: Chat server fleet capacity exhausted**
1. Verify by checking connection-per-server metric in DataDog.
2. Trigger auto-scaler to add 20% capacity (provisioning takes ~3 min for warm fleet).
3. Update load balancer weights to direct new connections to fresh servers.
4. Investigate root cause: traffic spike (event-driven, e.g., World Cup goal) vs. mass disconnect causing reconnect storm.

---

## 15. Evolution and Future Improvements

### At 10× Scale (20B Users — Hypothetical)
- **Erlang/OTP would still scale**, but per-server connection count (2M) becomes the bottleneck — would require 5,000+ chat servers vs. today's ~500. Operational complexity grows linearly.
- **Cassandra would need re-sharding** more aggressively; today's ~200-node clusters become 2,000-node clusters with significant gossip-protocol overhead.
- Consider migrating presence to a **CRDT-based distributed store** (Riak-style) to remove the Redis SPOF per shard.
- **Edge compute for message routing**: deploy chat servers in 50+ PoPs (vs. 5 DCs today) to reduce p99 latency from 50ms to <15ms globally.

### Technical Debt
- **XMPP/ejabberd legacy**: WhatsApp's protocol is XMPP-derived; the verbosity (XML stanzas) wastes bandwidth on mobile. Migration to a custom protobuf-based protocol is ongoing but the long tail of old clients keeps XMPP alive.
- **Mnesia residuals**: Some metadata paths still hit Mnesia for legacy reasons; migration to RocksDB/Cassandra is multi-year.
- **Manual capacity planning**: Most scaling decisions are still operator-driven. Modern systems would use ML-based forecasting (Twitter's "Capacity Planner" or Netflix's "Scryer").

### Future Capabilities
- **Multi-device E2E without phone**: Currently each linked device requires the phone to be online to bootstrap keys. Migrate to per-device key trees so the phone can be permanently offline.
- **Federation (interoperability with Signal / iMessage)**: EU's Digital Markets Act mandates interop by 2027. Requires standardizing the Signal protocol layer across providers.
- **On-device AI features**: Smart reply, language translation running locally via small models (e.g., Llama 3.2 1B on-device). Avoids server-side plaintext exposure but constrained by phone compute.
- **Quantum-resistant cryptography**: Signal protocol is being upgraded to PQXDH (Post-Quantum Extended Diffie-Hellman); WhatsApp will follow.


---

## 16. End-to-End Encryption Deep Dive (Signal Protocol)

WhatsApp implemented the Signal Protocol (formerly TextSecure / Axolotl) globally by April 2016 — one of the largest E2E deployments ever (1B+ users at the time). Understanding the protocol mechanics is critical for any senior system design discussion.

### Key Components

**Identity keys** (long-term): Each device generates an Ed25519 identity key pair on first install. Public key uploaded to WhatsApp server.

**Signed pre-keys**: Medium-term keys (rotated weekly). Signed by the identity key.

**One-time pre-keys**: Short-term keys (100+ uploaded at a time; consumed one per new conversation). When stock falls below threshold, client uploads more.

**Session keys**: Derived per-conversation using X3DH (Extended Triple Diffie-Hellman) handshake.

**Message keys**: Derived per-message via the Double Ratchet algorithm, providing **forward secrecy** (past messages stay secure if current key compromised) and **future secrecy** (compromise doesn't extend forward).

### X3DH Handshake (First Message to a New Contact)
1. Alice fetches Bob's identity key + signed pre-key + one one-time pre-key from server.
2. Alice computes shared secret SK = KDF(DH(IK_A, SPK_B) || DH(EK_A, IK_B) || DH(EK_A, SPK_B) || DH(EK_A, OPK_B)).
3. Alice sends Bob: her identity key + ephemeral key + first encrypted message.
4. Bob receives, looks up the OPK_B by ID, computes the same SK, decrypts.
5. From this point forward, both have a shared session; the Double Ratchet takes over.

### Double Ratchet for Subsequent Messages
- Each message uses a new key, derived by ratcheting forward.
- Two ratchets: a **DH ratchet** (renewed on each message direction change) and a **chain ratchet** (per-direction symmetric key derivation).
- Lost messages can be decrypted out-of-order using "skipped message keys" (stored up to a configurable horizon).

### Group Messaging (Sender Keys)
- Each member maintains a unique "sender key" for the group.
- When a member sends a group message: encrypts once with their sender key; sends individually (encrypted point-to-point) the sender key to each member at session setup.
- Avoids N² encryption cost per message (would be untenable for 1024-member groups).
- New member added: existing members each send their sender keys to the new member.
- Member removed: all remaining members rotate their sender keys (forward secrecy for the removed member).

### Server's Limited View
- WhatsApp server stores only ciphertext + minimal metadata: sender, recipient(s), timestamp, message ID.
- Cannot decrypt content (no key access).
- Metadata still reveals communication graphs (who talks to whom, when) — a critical privacy limitation.

### Key Transparency (2023 Initiative)
- A directory service publishes verifiable logs of identity keys.
- Users can verify "is the key I'm encrypting to actually Bob's, not a server-substituted MITM key?"
- Based on CONIKS / Key Transparency designs; opt-in feature.

---

## 17. Numbers to Remember

| Metric | Value |
|--------|-------|
| MAU | 2B users |
| Messages/day | 100B (1.16M/sec avg, 3.5M/sec peak) |
| Voice/video minutes/day | 2B minutes |
| Concurrent connections/server | 2M+ (Erlang/BEAM) |
| Chat server fleet | ~500 servers |
| Cassandra cluster | ~200 nodes per region |
| Message storage (in-flight) | ~3.5 PB |
| Media storage (30-day hot) | ~1.5 PB |
| Engineers per million users (2014) | ~1 per 9M users |
| Cross-region replication lag | 50–200ms typical |
| E2E protocol | Signal (X3DH + Double Ratchet) |
| Group size limit | 1024 members (2023) |
| Offline queue retention | 30 days |


---

## 18. Protocol Internals — XMPP / Custom WhatsApp Protocol

### Original Protocol (XMPP-based)
WhatsApp originally used a heavily modified XMPP (eXtensible Messaging and Presence Protocol). Standard XMPP uses verbose XML stanzas, which are wasteful on mobile networks.

Example standard XMPP message stanza:
```xml
<message from="alice@whatsapp.net" to="bob@whatsapp.net" type="chat" id="abc123">
  <body>Hello Bob</body>
</message>
```

WhatsApp's binary-encoded variant compresses this to ~30 bytes using:
- **Dictionary-based token compression**: Common XML tag names ("message", "from", "to") are encoded as single-byte tokens.
- **Length-prefixed strings**: Avoids parser overhead.
- **Binary integer encoding** for IDs and timestamps.

Bandwidth savings: ~70% over standard XMPP. Critical for users on 2G/Edge in emerging markets where data costs are prohibitive.

### Connection Establishment
1. Client opens TCP connection on port 5222 (XMPP standard) or 443 (TLS-wrapped, used by ~90% of clients to bypass restrictive firewalls).
2. TLS handshake (uses 0-RTT resumption when possible to save round trips).
3. Authentication via SRP or token-based after initial registration (no plaintext password ever sent).
4. Resume previous session if `<resume>` token presented (avoids re-authenticating after brief disconnects).
5. Server sends queued offline messages.

### Resumption and Reliability
- Each connection has a unique session ID; the server holds the session state for ~30s after TCP disconnect.
- If client reconnects within 30s with the resume token: no need to re-fetch offline queue, no presence flap.
- Reduces "user went offline / back online" notifications during brief network blips (subway, elevator).

### Message Receipts (Ticks)
- One tick (single grey): client sent message to server, server ACKed.
- Two ticks (double grey): server delivered to recipient device.
- Two blue ticks: recipient client confirmed READ (sent only if user has Read Receipts enabled).

Each receipt is a separate XMPP `<receipt>` stanza. Delivery receipts add ~30% overhead to total message traffic but are essential UX.

### Multi-Device Architecture (2021 Rewrite)
- Pre-2021: phone was the source of truth; web/desktop clients were just mirrors that required the phone online.
- Post-2021: each device has its own Signal Protocol identity; messages are encrypted separately to each linked device.
- Phone can be offline; other devices function independently for up to 14 days.
- Server maintains a per-user **device list**; senders encrypt N copies of each message (one per recipient device, typically 1-4).

---

## 19. Numbers to Remember (Continued)

- BEAM scheduler threads: 1 per CPU core; can manage millions of processes per node.
- Message ID format: 64-bit, derived from server-side counter + node ID.
- TLS handshake budget: <300ms on slow networks (using session resumption).
- Cassandra LOCAL_QUORUM write latency: ~5ms p99.
- Push notification latency (APNs/FCM): typically 200-500ms; can spike to seconds.
- TURN relay bandwidth at peak: ~8 GB/sec global.
- 30-day offline message retention.
- Group chat fan-out: per-sender-key model avoids O(N²) encryption.


---

## 20. Group Messaging Deep Dive

Groups are technically distinct from 1:1 chats and present their own scaling challenges. Maximum group size in WhatsApp evolved: 100 (2014) → 256 (2018) → 512 (2022) → 1024 (2023) → 2048 (Communities, 2024).

### Group State Storage
- Each group has a `group_id` (server-assigned, 64-bit).
- Group metadata (name, avatar, description, admin list, settings) lives in Cassandra:
  ```
  CREATE TABLE groups (
    group_id bigint PRIMARY KEY,
    name text,
    description text,
    creator_id bigint,
    created_at timestamp,
    admin_ids set<bigint>,
    member_ids set<bigint>,
    settings map<text, text>
  );
  ```
- For very large groups, member list is sharded into a separate table to avoid wide rows.

### Group Message Fan-Out
When Alice sends a message to a 1000-member group:
1. Client encrypts once with her sender key for the group (Signal Protocol).
2. Sends ciphertext to server with destination = `group_id`.
3. Server looks up member list, **but does NOT decrypt or re-encrypt** (E2E preserved).
4. Server fans out the ciphertext to each member's device(s).
5. With multi-device: 1000 members × avg 1.5 devices = ~1500 deliveries per message.

**Bandwidth implication**: A 1024-member group sending 100 messages/day amplifies to 100K device-level deliveries. At population scale (1M active large groups), this is 100B device-deliveries/day from group fan-out alone.

### Sender Key Distribution
- Initial setup: when Alice joins a group, every existing member sends Alice (encrypted point-to-point) their current sender key.
- Cost: O(N) per join, O(1) per subsequent message.
- Member removed: every remaining member rotates their sender key and re-distributes. O(N²) cost on removal — expensive for large groups, which is why removals are batched in admin operations.

### Admin Operations
- Add/remove member, change name/avatar, modify settings: special XMPP stanzas that trigger:
  - Update of Cassandra group state.
  - Notification fan-out to all members ("Alice added Bob to the group").
  - Sender key rotation if removing a member.
- All admin operations are idempotent (replayed safely).

### Communities (2022 Feature)
- A "community" groups up to 100 sub-groups under a parent identity.
- Each sub-group still uses its own Signal session and sender keys.
- The community layer is purely an organizational wrapper; not a new encryption layer.
- Total reach: 100 sub-groups × 1024 members = ~100K users in one community.

### Broadcast Lists
- One-to-many distribution: sender selects up to 256 contacts; message sent individually to each (each appears as a 1:1 message).
- Recipients don't see other recipients (unlike groups).
- Server-side: just N parallel 1:1 sends; no special infrastructure.

### Polls, Reactions, Replies
- Built as message types: a poll vote is itself an encrypted message referencing the poll's message ID.
- Reactions: small encrypted payloads (just the emoji + message_id reference).
- Bandwidth-light: <100 bytes per reaction.
- Aggregation done client-side: each device collects reactions and displays counts.

