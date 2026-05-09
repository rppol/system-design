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
