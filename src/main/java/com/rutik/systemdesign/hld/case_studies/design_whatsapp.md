# Design WhatsApp

## Intuition

> **Design intuition**: WhatsApp's core challenges are message delivery guarantees (at-least-once with acknowledgments), real-time presence (online/offline for 2 billion users), and end-to-end encryption (keys on device, not server). The persistent WebSocket connection architecture is what enables real-time message delivery without polling.

**Key insight**: WhatsApp stores messages only until delivered — once a message reaches the recipient's device, it's deleted from servers. This is what enables end-to-end encryption: messages exist on servers only transiently, and in encrypted form. Server design is built around this "store and forward" with delivery receipts model.

**System at a glance**: WhatsApp is a cross-platform messaging application with 2 billion monthly active users, 100 billion messages sent per day (65B+ at peak), 1-to-1 and group messaging (up to 1024 members), voice/video calls, end-to-end encryption for all messages, media sharing (photos, videos, documents, voice notes), and read receipts (single tick -> double tick -> blue ticks).

---

## 1. Requirements Clarification

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

## 2. Scale Estimation

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

## 3. High-Level Architecture

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

## 4. Component Deep Dives

### WebSocket Connection Management

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

### Message Flow

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

### Message Storage

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

### Group Messaging

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

**Maximum group size evolution**: 100 (2014) → 256 (2018) → 512 (2022) → 1024 (2023) → 2048 (Communities, 2024).

#### Group State Storage

Each group has a `group_id` (server-assigned, 64-bit). Group metadata (name, avatar, description, admin list, settings) lives in Cassandra:

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

For very large groups, the member list is sharded into a separate table to avoid wide rows.

#### Group Message Fan-Out at Scale

When Alice sends a message to a 1000-member group:
1. Client encrypts once with her sender key for the group (Signal Protocol).
2. Sends ciphertext to server with destination = `group_id`.
3. Server looks up the member list, but does NOT decrypt or re-encrypt (E2E preserved).
4. Server fans out the ciphertext to each member's device(s).
5. With multi-device: 1000 members × avg 1.5 devices = ~1500 deliveries per message.

**Bandwidth implication**: A 1024-member group sending 100 messages/day amplifies to 100K device-level deliveries. At population scale (1M active large groups), this is 100B device-deliveries/day from group fan-out alone.

#### Sender Key Distribution

- Initial setup: when Alice joins a group, every existing member sends Alice (encrypted point-to-point) their current sender key.
- Cost: O(N) per join, O(1) per subsequent message.
- Member removed: every remaining member rotates their sender key and re-distributes. O(N²) cost on removal — expensive for large groups, which is why removals are batched in admin operations.

#### Admin Operations

Add/remove member, change name/avatar, modify settings: special protocol stanzas that trigger an update of Cassandra group state, a notification fan-out to all members ("Alice added Bob to the group"), and a sender key rotation if removing a member. All admin operations are idempotent (replayed safely).

#### Communities (2022 Feature)

A "community" groups up to 100 sub-groups under a parent identity. Each sub-group still uses its own Signal session and sender keys — the community layer is purely an organizational wrapper, not a new encryption layer. Total reach: 100 sub-groups × 1024 members = ~100K users in one community.

#### Broadcast Lists

One-to-many distribution: sender selects up to 256 contacts; message sent individually to each (each appears as a 1:1 message). Recipients don't see other recipients (unlike groups). Server-side: just N parallel 1:1 sends — no special infrastructure.

#### Polls, Reactions, and Replies

Built as message types: a poll vote is itself an encrypted message referencing the poll's message ID. Reactions are small encrypted payloads (just the emoji + message_id reference) — bandwidth-light at <100 bytes per reaction. Aggregation is done client-side: each device collects reactions and displays counts.

---

### End-to-End Encryption (Signal Protocol)

WhatsApp implemented the Signal Protocol (formerly TextSecure / Axolotl) globally by April 2016 — one of the largest E2E deployments ever (1B+ users at the time). Understanding the protocol mechanics is critical for any senior system design discussion.

#### Key Hierarchy

- **Identity keys** (long-term): Each device generates an Ed25519 identity key pair on first install. Public key uploaded to WhatsApp's server.
- **Signed pre-keys**: Medium-term keys (rotated weekly), signed by the identity key.
- **One-time pre-keys**: Short-term keys (100+ uploaded at a time; consumed one per new conversation). When stock falls below a threshold, the client uploads more.
- **Session keys**: Derived per-conversation using the X3DH (Extended Triple Diffie-Hellman) handshake.
- **Message keys**: Derived per-message via the Double Ratchet algorithm, providing **forward secrecy** (past messages stay secure if the current key is compromised) and **future secrecy** (compromise doesn't extend forward).

#### X3DH Handshake (First Message to a New Contact)

```
Alice and Bob each publish to server:
  - Identity key (long-term)
  - Signed prekey (medium-term)
  - One-time prekeys (single use)
```

1. Alice fetches Bob's identity key + signed pre-key + one one-time pre-key from the server.
2. Alice computes a shared secret SK = KDF(DH(IK_A, SPK_B) || DH(EK_A, IK_B) || DH(EK_A, SPK_B) || DH(EK_A, OPK_B)).
3. Alice sends Bob her identity key + ephemeral key + first encrypted message.
4. Bob receives the message, looks up OPK_B by ID, computes the same SK, and decrypts.
5. From this point forward, both have a shared session; the Double Ratchet takes over.

Alice can compute a shared secret using Bob's public keys WITHOUT Bob being online — this is the magic of X3DH.

#### Double Ratchet for Subsequent Messages

Key chain: RootKey → ChainKey → MessageKey (a new key for every message). Two ratchets work together:
- A **DH ratchet**, renewed on each message-direction change.
- A **chain ratchet**, deriving the next symmetric key per direction.

Even if an attacker gets one message's key: past messages stay safe (forward secrecy) and future messages use different keys (break-in recovery). Lost messages can still be decrypted out-of-order using "skipped message keys" (stored up to a configurable horizon).

#### Server's Limited View

WhatsApp's server stores only ciphertext plus minimal metadata (sender, recipient(s), timestamp, message ID, size) and cannot decrypt content — it has no key access. Metadata still reveals communication graphs (who talks to whom, when), which remains a critical privacy limitation even with full E2E encryption.

#### Key Transparency (2023 Initiative)

A directory service publishes verifiable logs of identity keys, so users can verify "is the key I'm encrypting to actually Bob's, not a server-substituted MITM key?" The design is based on CONIKS / Key Transparency research and is currently an opt-in feature.

**Key storage challenge**: Private keys are stored ONLY on device. If the phone is lost, messages are gone unless backed up — backup encryption is a separate system (iCloud/Google Drive with a user-controlled key).

---

### Online Presence and Last Seen

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

### Media Sharing

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

### Database Design

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

## 5. Design Decisions & Tradeoffs

### Message Storage: On-Device vs. Server-Side

**Option A: Server-side persistent storage** (like email) — messages remain in a central datastore indefinitely, recoverable from any device, easy to back up centrally.

**Option B: On-device storage with transient server-side queueing (WhatsApp's approach)** — the server holds a message only until it has been delivered to all of a user's devices, then deletes it (capped at 30 days for undelivered messages).

**Decision**: On-device storage. This is what makes end-to-end encryption meaningful — if the server retained plaintext-equivalent copies, a subpoena or breach would expose history regardless of transport encryption. The trade-off is durability: losing a phone without a configured backup means losing message history. WhatsApp mitigates this with optional encrypted cloud backups (iCloud/Google Drive), which are a separate trust boundary from the messaging system itself.

### Group Fanout: Write-Time vs. Read-Time

**Option A: Fanout on write** — at send time, create one message copy per group member (up to 1024x write amplification), each independently tracked for delivery and read receipts.

**Option B: Fanout on read** — store a single copy in a group log; each member's client pulls from the shared log and tracks its own read cursor.

**Decision**: Fanout on write for online members, with fanout on read as a fallback for offline members (pull on reconnect). This hybrid preserves per-member delivery and read receipts — a core WhatsApp UX feature — for the common case, while avoiding the full 1024x write cost for members who stay offline for days.

### Consistency: Eventual vs. Strict Ordering

**Option A: Strict global ordering** — a single sequencer assigns a global order to every message, guaranteeing identical message order on every device.

**Option B: Eventual consistency with client-side reordering (WhatsApp's approach)** — each message gets a server-assigned timestamp/TIMEUUID; clients display by timestamp and tolerate small reorderings.

**Decision**: Eventual consistency. A global sequencer would be a single point of contention at 1M+ messages/sec and would conflict with the per-conversation partitioning that lets Cassandra scale horizontally. For group chats, routing all messages for a group to the same Kafka partition gives "good enough" per-group ordering without a global bottleneck.

### Real-Time Delivery: WebSocket vs. HTTP Long Polling

**Option A: HTTP long polling** — the client issues a request that the server holds open until a message arrives or a timeout expires, then immediately reissues it.

**Option B: Persistent WebSocket connection (WhatsApp's approach)** — a single bidirectional TCP connection per online user, held open for the session.

**Decision**: WebSocket. Long polling re-establishes a TCP+TLS handshake on every cycle (prohibitively expensive at 500M concurrent users) and adds HTTP header overhead per round trip. A persistent WebSocket amortizes connection setup across the entire session and lets the server push messages with sub-100ms latency. The cost is operational: ~500M long-lived connections require careful connection-table management (the Redis routing table with TTL) and graceful draining during deploys.

### Presence Tracking: Redis TTL vs. Persistent Store

**Option A: Persistent presence store** — write every connect/disconnect/heartbeat event to a durable database, queryable for historical "last seen" analytics.

**Option B: Redis key with TTL (WhatsApp's approach)** — presence is "soft state": a Redis key with a 30-second TTL, renewed by a 10-second heartbeat; absence of the key means offline.

**Decision**: Redis TTL. Presence changes extremely frequently — every connect, disconnect, and 10-second heartbeat across 500M users — and persisting every transition would be an enormous write volume for a value only needed "right now." The trade-off is ~30 seconds of staleness after a crash: a Redis failover briefly shows everyone as "last seen unknown" until reconnections repopulate the table (see War Story 5 below).

---

## 6. Real-World Implementations

WhatsApp's architecture is unusually well-documented for a messaging system at this scale, in part because of the 2014 Facebook acquisition (for $19B) and the 2016 global rollout of end-to-end encryption to over 1 billion users — both events drew intense public technical scrutiny.

### Erlang/OTP and the Actor Model

WhatsApp's choice of Erlang/OTP at its founding (2009) is foundational to understanding how 50 engineers served 450M users in 2014 — roughly **9M users per engineer**, an efficiency ratio unmatched in the industry.

#### Why Erlang?
- **Lightweight processes**: Each Erlang process consumes ~2KB of memory (vs ~1MB for a JVM thread or ~8MB for a native pthread). A single BEAM VM hosts millions of processes.
- **Share-nothing concurrency**: Processes communicate only via message passing. No shared mutable state means no locks, no deadlocks.
- **"Let it crash" philosophy**: Supervision trees restart failed processes within microseconds. The system self-heals rather than defensively coding against every error path.
- **Hot code reloading**: Deploy new code without dropping any of the ~2M long-lived TCP connections per server.

#### One Process Per Phone Number
At WhatsApp, each registered phone number maps to a long-lived Erlang process (an `ejabberd` user session). When a message arrives for user X, the routing layer:
1. Looks up the PID (process ID) of user X's session on whichever server hosts it (via Mnesia distributed table).
2. Sends an Erlang message `{deliver, MsgPayload}` to that PID.
3. The session process pushes the message over its WebSocket (or queues it if offline).

Each server (typically a FreeBSD box with 96GB RAM) handled **2M+ concurrent connections**. In 2012 WhatsApp announced a single server hitting 2M TCP connections — a benchmark few competitors matched.

#### Mnesia → Custom Storage Evolution
- **Early (2009–2012)**: Mnesia (Erlang's built-in distributed DB) held offline message queues. Excellent for sub-millisecond reads but caps out at ~4GB per table.
- **Mid (2012–2016)**: Sharded Mnesia, then custom on-disk storage built directly on raw block devices (bypassing the file system overhead).
- **Post-Facebook acquisition**: Migration to RocksDB-based stores and integration with Facebook's TAO/Cassandra infrastructure.

### Protocol Internals: From XMPP to Binary Encoding

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

Bandwidth savings: ~70% over standard XMPP — critical for users on 2G/Edge in emerging markets where data costs are prohibitive.

#### Connection Establishment
1. Client opens a TCP connection on port 5222 (XMPP standard) or 443 (TLS-wrapped, used by ~90% of clients to bypass restrictive firewalls).
2. TLS handshake (uses 0-RTT resumption when possible to save round trips).
3. Authentication via SRP or token-based auth after initial registration (no plaintext password ever sent).
4. Resume the previous session if a `<resume>` token is presented (avoids re-authenticating after brief disconnects).
5. Server sends queued offline messages.

#### Resumption and Reliability
- Each connection has a unique session ID; the server holds the session state for ~30s after a TCP disconnect.
- If the client reconnects within 30s with the resume token: no need to re-fetch the offline queue, no presence flap.
- This reduces "user went offline / back online" notifications during brief network blips (subway, elevator).

#### Message Receipts (Ticks)
- One tick (single grey): client sent the message to the server, server ACKed.
- Two ticks (double grey): server delivered to the recipient device.
- Two blue ticks: recipient client confirmed READ (sent only if the user has Read Receipts enabled).

Each receipt is a separate protocol stanza. Delivery receipts add ~30% overhead to total message traffic but are essential UX.

#### Multi-Device Architecture (2021 Rewrite)
- Pre-2021: the phone was the source of truth; web/desktop clients were just mirrors that required the phone online.
- Post-2021: each device has its own Signal Protocol identity; messages are encrypted separately to each linked device.
- The phone can be offline; other devices function independently for up to 14 days.
- The server maintains a per-user **device list**; senders encrypt N copies of each message (one per recipient device, typically 1-4).

### Comparable Systems for Cross-Reference

| System | Similarity | Key Difference |
|--------|-----------|-----------------|
| **Signal** | Same E2E protocol (Signal Protocol), pioneered X3DH + Double Ratchet | Smaller scale (~40M users), minimal metadata retention by policy, fully open source |
| **Telegram** | Similar client/server chat architecture, custom binary protocol (MTProto) | Cloud chat history is NOT E2E by default — only "Secret Chats" are; the server can read regular chat content |
| **Facebook Messenger** | Shares post-acquisition infrastructure lineage (TAO, RocksDB) with WhatsApp | E2E ("Secret Conversations", later default) was added later; historically optimized for rich media and bots over minimal metadata |
| **iMessage** | Apple-ecosystem E2E messaging with a similar per-device key model | Falls back to SMS for non-Apple recipients; key directory historically less transparent than WhatsApp's Key Transparency |

---

## 7. Technologies & Tools

| Component | Technology | Why |
|-----------|-----------|-----|
| Connection layer | Erlang/OTP (BEAM VM), ejabberd-derived | Lightweight processes (~2KB each) support 2M+ concurrent connections per server with "let it crash" supervision |
| Real-time transport | WebSocket, binary XMPP-derived protocol | Persistent bidirectional connection; ~30 bytes per message after binary encoding |
| Message queue | Kafka | Durable, ordered, replayable buffer between chat servers and the message store / push pipeline |
| In-flight message storage | Cassandra | Write-heavy (1.16M msgs/sec avg), TTL-based auto-expiry, naturally distributed |
| User profiles & social graph | PostgreSQL + Redis | ACID for infrequent profile writes; Redis for fast contact lookups |
| Presence & connection routing | Redis (key TTL) | Sub-millisecond lookups for "where is user X connected"; soft-state presence with a 30s TTL |
| Media storage | S3 + CloudFront | Encrypted blob storage with global CDN edge delivery |
| End-to-end encryption | Signal Protocol (X3DH + Double Ratchet + Sender Keys) | Industry-standard forward-secret E2E; the server never sees plaintext |
| Push notifications | APNs (iOS) / FCM (Android) | Wake backgrounded apps for offline message delivery |
| Voice/video | WebRTC + TURN/STUN | Peer-to-peer media with relay fallback for the ~20% of calls behind restrictive NATs |

---

## 8. Operational Playbook

### Multi-Region and Global Deployment

WhatsApp operates from multiple datacenters globally, with users "homed" to the closest region but able to communicate with users in any other region.

#### Active-Active with User Affinity
- Each user has a **home region** assigned at registration based on phone number country code (and updated on roaming).
- All sessions, presence, and offline queues for a user live in their home region.
- Cross-region delivery uses a global routing fabric: a chat server in the EU receives a message destined for a US user → forwards via the WAN link to a US chat server → the US server pushes to the recipient.

#### Data Residency and Regulatory Compliance
- **Brazil, India, Indonesia, EU**: Local data residency requirements pin user metadata (contact lists, last-seen, profile) to in-country DCs.
- E2E encryption simplifies compliance: WhatsApp servers never see plaintext, so most jurisdictions accept "ciphertext-in-transit storage" as not constituting personal data under GDPR Article 4(1).
- The **2021 Brazil ruling** required local data storage for user metadata; WhatsApp built a São Paulo DC in response.

#### Cross-Region Replication
- **Synchronous within a region**: Cassandra LOCAL_QUORUM for low-latency writes (~5ms).
- **Asynchronous across regions**: Cassandra EACH_QUORUM is too slow (cross-Atlantic ~80ms RTT). Replication is async via Cassandra's NetworkTopologyStrategy with eventual consistency.
- Typical cross-region replication lag: **50–200ms** under normal load; up to several seconds during peak.

#### Conflict Resolution
- E2E encryption means messages are immutable ciphertext blobs — no merge conflicts possible.
- Profile updates (display name, avatar) use **last-writer-wins** with vector clocks tracking the originating region.
- Group membership uses CRDT-like semantics: an "add member" event from region A and "remove member" from region B converge deterministically.

#### Failover Drill: Region Loss
If the EU region is fully lost (rare — earthquake, cable cut, AWS-style regional outage):
- DNS-based traffic steering (Route 53 / FB internal equivalent) redirects EU users to the closest healthy DC (typically US-East for Western Europe, Singapore for Eastern Europe).
- Users re-authenticate; sessions are rebuilt; offline queue replicas (async-replicated to US) are promoted.
- **Time to recovery**: 5–15 minutes for new traffic to be served; ~30 minutes for full state convergence.

### Deployment and Alerting

#### Critical Alerts

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

#### Deployment Strategy
- **Hot code reload (Erlang)** for non-breaking changes: deploy without dropping connections — the BEAM VM swaps modules atomically.
- **Rolling restart with connection draining** for breaking changes: drain TCP connections gradually (5% of fleet at a time), forcing clients to reconnect to upgraded servers.
- **Canary fleet**: 1% of traffic served by the new build for 24h; auto-rollback on >0.1% error rate increase.
- **Feature flags** via centralized config service; staged rollout by country code and user_id hash bucket.

#### On-Call Runbooks

**Runbook: Sudden spike in message delivery latency**
1. Check Grafana for which DC is affected.
2. If single DC: check Cassandra cluster health (`nodetool status`) — node down, GC pause, or compaction storm.
3. Check Kafka consumer lag — if > 30s, scale out the consumer group.
4. If multi-DC: check cross-DC WAN link saturation (often a single tenant flooding).
5. Mitigation: shed load by rate-limiting new connections (allows the backlog to drain).
6. Escalate to Erlang infra on-call if BEAM scheduler utilization > 80%.

**Runbook: Chat server fleet capacity exhausted**
1. Verify by checking the connection-per-server metric in DataDog.
2. Trigger the auto-scaler to add 20% capacity (provisioning takes ~3 min for a warm fleet).
3. Update load balancer weights to direct new connections to fresh servers.
4. Investigate root cause: traffic spike (event-driven, e.g., World Cup goal) vs. mass disconnect causing a reconnect storm.

### Evolution and Future Improvements

#### At 10x Scale (20B Users — Hypothetical)
- **Erlang/OTP would still scale**, but per-server connection count (2M) becomes the bottleneck — would require 5,000+ chat servers vs. today's ~500. Operational complexity grows non-linearly.
- **Cassandra would need re-sharding** more aggressively; today's ~200-node clusters become 2,000-node clusters with significant gossip-protocol overhead.
- Consider migrating presence to a **CRDT-based distributed store** (Riak-style) to remove the Redis SPOF per shard.
- **Edge compute for message routing**: deploy chat servers in 50+ PoPs (vs. 5 DCs today) to reduce p99 latency from 50ms to <15ms globally.

#### Technical Debt
- **XMPP/ejabberd legacy**: WhatsApp's protocol is XMPP-derived; the verbosity (XML stanzas) wastes bandwidth on mobile. Migration to a custom protobuf-based protocol is ongoing but the long tail of old clients keeps XMPP alive.
- **Mnesia residuals**: Some metadata paths still hit Mnesia for legacy reasons; migration to RocksDB/Cassandra is multi-year.
- **Manual capacity planning**: Most scaling decisions are still operator-driven. Modern systems would use ML-based forecasting (Twitter's "Capacity Planner" or Netflix's "Scryer").

#### Future Capabilities
- **Multi-device E2E without phone**: Currently each linked device requires the phone to be online to bootstrap keys. Migrate to per-device key trees so the phone can be permanently offline.
- **Federation (interoperability with Signal / iMessage)**: The EU's Digital Markets Act mandates interop by 2027, requiring standardization of the Signal protocol layer across providers.
- **On-device AI features**: Smart reply and language translation running locally via small models (e.g., Llama 3.2 1B on-device) — avoids server-side plaintext exposure but is constrained by phone compute.
- **Quantum-resistant cryptography**: The Signal protocol is being upgraded to PQXDH (Post-Quantum Extended Diffie-Hellman); WhatsApp will follow.

---

## 9. Common Pitfalls & War Stories

### Pitfall Summary

| Pitfall | Impact | Fix |
|---------|--------|-----|
| Treating server-side message storage as a backup | Users assume messages are recoverable from "the cloud" after losing a phone, but WhatsApp deletes messages after delivery | Default to (and clearly explain) encrypted local backups (iCloud/Google Drive) as a separate trust boundary |
| Naive write-time fanout to ALL group members, including offline ones | 1024x write amplification even for members who won't read for days, wasting Kafka/Cassandra capacity | Fan out only to online members; offline members pull from a group log on reconnect |
| Treating Redis presence as authoritative | A single Redis shard failure makes every affected user appear "offline" even though their WebSocket is still connected | Treat presence as soft state derived from chat-server connection tables; resync from source of truth on failover |
| Ignoring Cassandra hinted-handoff TTL | A node down for >3 hours misses writes permanently unless a manual repair runs | Alert on node-down duration approaching the 3-hour hint TTL and trigger `nodetool repair` proactively |
| Un-throttled push notification retries after a provider outage | When APNs/FCM recovers, a thundering herd of queued pushes can get the app's token rate-limited or blocklisted | Rate-limit push delivery on recovery (e.g., 10K/sec per app token) with exponential backoff |
| Removing a member from a large group without batching | Sender-key rotation is O(N²) — every remaining member re-distributes a new key to every other member | Batch admin removals and rotate/redistribute keys asynchronously rather than synchronously per removal |
| Verbose XML-based protocol on mobile networks | Standard XMPP stanzas waste ~70% more bandwidth than necessary — a real cost on 2G/Edge | Binary-encode the protocol with dictionary-based token compression |

### War Story 1: Chat Server Crashes Mid-Send

**What happened**: User A sends a message to User B. The chat server holding A's WebSocket crashes after receiving the message but before persisting it to Kafka.

**Impact**: The client never receives an ACK (server-side message_id) for the in-flight message. Its local send queue still shows the message as pending — to the user, it appears stuck behind a single grey clock icon.

**Fix**: The client detects the failure via a TCP RST or a missed heartbeat (5-second timeout), reconnects through the load balancer to a healthy chat server, and replays every unACKed message from its local queue. Each replayed message carries a client-generated UUID as an idempotency key, so even if the original message *did* reach Kafka before the crash, the server deduplicates and the recipient sees it only once.

**Lesson**: Durability for at-least-once delivery lives on the client, not the server. Because the client retains the message until it gets a server ACK, this entire failure mode resolves in 5–10 seconds with zero message loss — the source of truth for "was this sent?" is the client's local queue, not any server-side state.

### War Story 2: Cassandra Node Failure During Message Store

**Scenario**: Replication factor 3, consistency level LOCAL_QUORUM (writes need 2/3 acks). One Cassandra node in the local datacenter dies during a write.

**Behavior**:
- The coordinator still receives 2 acks → the write succeeds.
- Hinted handoff stores the missed write on a peer node; replayed when the failed node returns (default hint TTL: 3 hours).
- If the node is down > 3 hours: a repair (`nodetool repair`) is required to re-sync.
- Reads at LOCAL_QUORUM continue succeeding from the 2 live replicas.

**TTR**: Node restart ~2 minutes; full repair ~30 minutes for a single node's range.

### War Story 3: Cross-Datacenter Network Partition

**Scenario**: The WAN link between the US and EU datacenters fails. EU users cannot reach US-hosted chat sessions and vice versa.

**Behavior**:
- Each DC continues serving its local users; chat sessions for users whose "home" DC is unreachable get queued in the local DC's Kafka.
- Cross-DC message replication (using MirrorMaker or a custom Erlang dist mechanism) buffers writes.
- On partition heal: backlogged messages drain in arrival order; clients see a burst of "delayed" messages.

**TTR**: Depends on partition duration. WhatsApp's offline queue retains undelivered messages for **30 days** — a partition lasting hours produces zero user-visible message loss, only delay.

### War Story 4: Push Notification Provider (APNs/FCM) Outage

**Scenario**: Apple's APNs (Apple Push Notification service) has an outage; iOS users with backgrounded apps cannot be woken.

**Behavior**:
- WhatsApp's push notification service queues notifications with exponential backoff retry (1s, 2s, 4s, 8s … capped at 5 min).
- Messages still arrive instantly when the user opens the app (fetched from the offline queue).
- On APNs recovery, the resulting thundering herd of queued pushes is rate-limited (10K/sec per app token) to avoid being blocklisted by Apple.

**TTR**: User-visible impact = 0 for foregrounded apps; backgrounded apps see notifications delayed until APNs recovers (historically 1–6 hours for major APNs incidents).

### War Story 5: Redis Presence Store Loses a Shard

**Scenario**: A Redis shard storing presence state (online/offline + last_seen) crashes; a replica is promoted but the 30-second window of presence state is lost.

**Behavior**:
- Presence is "soft state" — derived from active TCP connections, not authoritative.
- After failover, presence resyncs from chat servers' connection tables within 60 seconds.
- During the gap, contacts see "last seen unknown" (graceful degradation).

**TTR**: 60–90 seconds, no message impact.

---

## 10. Capacity Planning

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

## 11. Interview Discussion Points

### How to Structure a 45-Minute Answer

1. **Clarify scope** (2-3 min): 1:1 messaging vs. groups vs. calls; confirm end-to-end encryption is in scope (it should be — it's WhatsApp's defining feature).
2. **Establish scale** (3-5 min): 2B users, 100B messages/day -> derive ~1.16M messages/sec average, ~3.5M peak; note ~500M users online concurrently.
3. **Sketch the high-level architecture** (5 min): clients -> load balancer -> chat server fleet -> Redis (presence + connection routing) + Kafka (durability buffer) + Cassandra (in-flight message store) + S3/CDN (media).
4. **Deep dive on connection management and message flow** (8-10 min): persistent WebSocket per user, the Redis-based routing table, the three delivery states (sent/delivered/read), and the offline-recipient path via push notifications.
5. **Address group messaging fanout** (5 min): write-time fanout for online members, read-time pull for offline members; discuss the up-to-1024x write amplification trade-off.
6. **Cover end-to-end encryption** (5-8 min): at minimum, explain X3DH for session setup and the Double Ratchet for forward secrecy — interviewers reward candidates who can explain *why* the server can't read messages, not just *that* it can't.
7. **Justify the database choices** (3-5 min): Cassandra for write-heavy, TTL-bound message storage; PostgreSQL for relational user data; Redis for ephemeral presence.
8. **Discuss multi-region deployment** (3-5 min): home-region affinity, async cross-region replication, data residency for regulated markets.
9. **Walk through a failure mode** (3-5 min): pick one (a chat server crash is the most common follow-up) and trace the exact recovery path end-to-end.
10. **Close with capacity numbers and future evolution** (2-3 min): connection-server math, storage math, and what changes at 10x scale.

**Q: Why does WhatsApp delete messages from its servers after delivery, and isn't that a durability risk?**
It's an intentional trade-off, not an oversight. WhatsApp uses a transient-storage model — once a message is delivered to all of a recipient's devices, the server-side copy is deleted (capped at 30 days if undelivered). This is what makes end-to-end encryption meaningful: if the server retained long-term plaintext-equivalent copies, a legal subpoena or data breach would expose chat history regardless of how messages were transported in transit. The durability risk — losing history if a phone is lost — is pushed to the client, mitigated by optional encrypted cloud backups (iCloud/Google Drive) that are a separate trust boundary from the messaging pipeline itself.

**Q: How would you handle a message broadcast to 1 billion users?**
Broadcasting to 1B recipients is inherently expensive regardless of architecture, so the practical answer combines scope-limiting and tiered delivery. WhatsApp caps broadcast lists at 256 contacts, sends to online users immediately via the normal chat path, and queues offline users for push-notification-triggered pull on reconnect. For true platform-wide announcements, a CDN-based "pull" model (publish once, clients fetch on next connect) avoids a synchronous fanout entirely.

**Q: How do you guarantee message ordering in group chats?**
Each message gets a server-assigned TIMEUUID (time-ordered) when it's written to Cassandra, and clients display messages by this timestamp — occasional reordering across different senders is acceptable and usually invisible to users. For stricter per-group ordering, all messages for a given group are routed to the same Kafka partition, which guarantees they're processed in send order by a single consumer — "good enough" ordering without a global sequencer.

**Q: Why is WhatsApp built on Erlang/OTP, and would you choose it today?**
Erlang's process model is an unusually good fit for "millions of mostly-idle, long-lived connections" — each Erlang process costs ~2KB of memory versus ~1MB for a JVM thread, so a single 96GB server can host 2M+ concurrent WebSocket sessions, each backed by its own process. The "let it crash" supervision model also means individual session crashes are isolated and self-healing rather than requiring defensive code everywhere. Whether you'd choose it *today* depends on team expertise: Go or Rust with an async runtime can approach similar connection density with a more mainstream hiring pool, but neither matches Erlang's decades-proven hot-code-reload story for zero-downtime deploys across millions of live connections.

**Q: Walk me through what happens, step by step, when Alice sends her very first message to Bob.**
This is the X3DH handshake. Alice fetches Bob's identity key, signed pre-key, and one of his one-time pre-keys from the server (Bob doesn't need to be online). Alice computes a shared secret by combining four Diffie-Hellman exchanges between her and Bob's various keys, then sends Bob her identity key, an ephemeral key, and the first encrypted message. Bob, when he comes online, looks up the one-time pre-key Alice consumed, performs the same DH computations to derive the identical shared secret, and decrypts. From here, both sides hold a shared session, and the Double Ratchet takes over for all subsequent messages.

**Q: How does the Double Ratchet provide forward secrecy, and why does that matter if a phone is compromised?**
Every message is encrypted with a unique key derived by "ratcheting" forward a key chain (RootKey -> ChainKey -> MessageKey), combining a DH ratchet (renewed on each direction change) with a per-direction symmetric chain ratchet. If an attacker compromises a device and extracts the current key state, they can decrypt messages going forward (until the next DH ratchet step) but *cannot* decrypt previously sent messages, because earlier message keys were already derived and discarded — they aren't recoverable from the current state. This is forward secrecy, and it limits the blast radius of a single compromised device to a narrow window of messages.

**Q: Why is removing a member from a 1024-person group an expensive operation?**
Group messages use per-member "sender keys" — each member encrypts once with their own sender key, which was distributed point-to-point to every other member at setup, avoiding O(N²) per-message encryption. But when a member is *removed*, every remaining member must rotate their sender key (so the removed member can no longer decrypt future messages — forward secrecy for the group) and re-distribute the new key to every other remaining member. That re-distribution is O(N²) in the group size, which is why WhatsApp batches admin removals rather than processing them synchronously one at a time.

**Q: How do you handle multi-device support given that encryption is per-device?**
Since the 2021 multi-device rewrite, each linked device (phone, web, desktop) has its own independent Signal Protocol identity. The server maintains a per-user device list, and a sender encrypts N separate copies of each message — one per recipient device, typically 1-4. The phone is no longer required to be online to relay messages to other devices (pre-2021 it was the single source of truth); each device can operate independently for up to 14 days before needing to resync.

**Q: How would you design the voice/video call feature?**
It's architecturally separate from messaging: call setup uses a signaling server (structurally similar to a chat server) to exchange SDP offers/answers over the existing WebSocket, and the actual media streams flow peer-to-peer via WebRTC. TURN servers provide a relay fallback for the ~20% of calls where NAT traversal fails directly. At WhatsApp's scale (2B call-minutes/day, ~1.4M concurrent call legs at peak), the TURN relay fleet alone needs to handle roughly 8 GB/sec of throughput.

**Q: A user in Brazil messages a user in Germany. Walk through the cross-region path, and what happens if the transatlantic link drops mid-conversation.**
Each user has a "home region" assigned by phone country code — the Brazilian user's session lives in a São Paulo (or US) DC, the German user's in an EU DC. The Brazilian user's chat server forwards the message over the WAN link to the EU DC, which delivers it to the German user's chat server and WebSocket. If the transatlantic link drops, each DC keeps serving its local users normally; the message gets queued in the Brazilian DC's Kafka and cross-DC replication buffers the write. When the link heals, queued messages drain in arrival order — the German user sees a burst of "delayed" messages, but because the 30-day offline-queue retention vastly exceeds any realistic outage, there's no message loss, only added latency.

**Q: How does presence ("last seen") stay roughly accurate without writing to a database on every heartbeat?**
Presence is treated as soft, ephemeral state rather than durable state: each online user's connection is represented by a Redis key with a 30-second TTL, refreshed by a heartbeat every 10 seconds. No database write happens per heartbeat — only Redis TTL renewals. If the TTL expires (heartbeat stops, e.g., the user closes the app or their connection drops), the key disappears and the user is considered offline; "last seen" is written to the user's profile in the durable database only at that offline transition, which happens far less often than heartbeats.

**Q: How would you scale this design to 20 billion users (10x growth)?**
The connection layer scales roughly linearly — going from ~500 to ~5,000 chat servers, each still handling ~2M connections via Erlang/BEAM — though operational complexity (deploys, monitoring, capacity planning) grows non-linearly and would push toward more automation. Cassandra clusters would grow from ~200 to ~2,000 nodes per region, where gossip-protocol overhead becomes a real concern and more aggressive re-sharding is needed. Presence would likely move off single-shard Redis to a CRDT-based distributed store to remove per-shard single points of failure, and message routing would push toward edge compute — 50+ points of presence instead of ~5 datacenters — to keep p99 latency under 15ms globally instead of today's ~50ms.

### Numbers to Remember

- MAU: 2B users; ~500M concurrently online at peak
- Messages/day: 100B (1.16M/sec avg, 3.5M/sec peak)
- Voice/video minutes/day: 2B minutes (~1.4M concurrent call legs at peak)
- Concurrent connections per server: 2M+ (Erlang/BEAM)
- Chat server fleet: ~500 servers today, ~5,000 at 10x scale
- Cassandra cluster: ~200 nodes per region; LOCAL_QUORUM write latency ~5ms p99
- Message storage (in-flight): ~3.5 PB; media storage (30-day hot): ~1.5 PB
- Cross-region replication lag: 50-200ms typical
- E2E protocol: Signal (X3DH + Double Ratchet + Sender Keys)
- Group size limit: 1024 members (2048 for Communities, 2024)
- Offline queue retention: 30 days
- Push notification latency (APNs/FCM): 200-500ms typical, can spike to hours during provider outages

---

## Cross-References

- **Wide-column message storage** -> [`../../database/wide_column_databases/README.md`](../../database/wide_column_databases/README.md)
- **Redis for presence and connection routing** -> [`../../database/key_value_stores/README.md`](../../database/key_value_stores/README.md)
- **Kafka as the durability buffer** -> [`../../backend/kafka_deep_dive/README.md`](../../backend/kafka_deep_dive/README.md)
- **Eventual consistency and cross-region replication** -> [`../../database/consistency_models_and_consensus/README.md`](../../database/consistency_models_and_consensus/README.md)
- **Service decomposition (chat, group, push, media services)** -> [`../microservices/README.md`](../microservices/README.md)
- **"Let it crash" supervision and failure recovery** -> [`../../backend/fault_tolerance_patterns/README.md`](../../backend/fault_tolerance_patterns/README.md)
- **Media delivery via CDN** -> [`../../devops/cloud_networking_and_cdn/README.md`](../../devops/cloud_networking_and_cdn/README.md)
- **Partitioning by conversation_id and data residency sharding** -> [`../../database/sharding_and_partitioning/README.md`](../../database/sharding_and_partitioning/README.md)

