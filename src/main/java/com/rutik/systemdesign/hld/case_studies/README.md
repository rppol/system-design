# HLD — Case Studies

Five end-to-end system design case studies at the high-level architectural overview depth. These are interview-format studies — start with requirements, estimate scale, draw the architecture, then discuss tradeoffs.

---

## Quick Start

If you only have time for three, read these first:

| File | Why |
|------|-----|
| [Design Twitter](design_twitter.md) | Canonical fan-out / feed problem. Teaches the write-vs-read fan-out tradeoff that recurs in every social and notification system. |
| [Design Uber](design_uber.md) | Real-time geo-matching under high write load. Teaches geospatial indexing, quadtrees, and the driver-rider matching loop. |
| [Design a URL Shortener](design_url_shortener.md) | The simplest well-scoped system design problem. Best for understanding how to handle 100:1 read-write ratio and ID generation at scale. |

---

## Full Learning Path

Grouped by primary engineering concern:

### Read-Heavy Systems & Caching

| Case Study | Primary Concern | What It Teaches |
|------------|----------------|----------------|
| [Design a URL Shortener](design_url_shortener.md) | Read-heavy with 100:1 read/write ratio, ID generation | Base62 encoding for short IDs, consistent hashing for distributed KV, Bloom filter to avoid DB lookups on 404s. |
| [Design Netflix](design_netflix.md) | CDN, video streaming, read scalability | Content delivery network architecture, adaptive bitrate streaming, how Netflix uses open-connect CDN nodes, title search vs browse. |

### Write-Heavy & Fan-Out Systems

| Case Study | Primary Concern | What It Teaches |
|------------|----------------|----------------|
| [Design Twitter](design_twitter.md) | Feed fan-out, write amplification vs read amplification | Fan-out-on-write (push model) vs fan-out-on-read (pull model); celebrity problem; hybrid model; Redis sorted sets for timeline. |
| [Design WhatsApp](design_whatsapp.md) | Real-time messaging, online presence, multi-device sync | WebSocket connection management at scale; message ordering; last-seen/online presence; multi-device message sync with sequence numbers. |

### Real-Time & Geo-Distributed

| Case Study | Primary Concern | What It Teaches |
|------------|----------------|----------------|
| [Design Uber](design_uber.md) | Real-time geo-matching, location updates, dynamic pricing | Quadtree / geohash for driver lookup; high-frequency location write load; surge pricing as a feedback loop; trip state machine. |

---

## Dependency Map

```
design_url_shortener   (standalone — best first study)
    └─ teaches consistent hashing used in design_twitter (distributed cache)

design_twitter
    └─ fan-out concept reused in design_netflix (content recommendation feed)
    └─ Redis timeline patterns reused in design_whatsapp (message delivery receipts)

design_uber
    └─ real-time WebSocket patterns shared with design_whatsapp
    └─ geo-indexing complements design_twitter (location-tagged posts)
```

---

## Interview Prep Shortcuts

| "Design X" Interview Question | Best Case Study |
|-------------------------------|----------------|
| Design a URL shortener / TinyURL | [design_url_shortener](design_url_shortener.md) |
| Design a pastebin | [design_url_shortener](design_url_shortener.md) — same read-heavy KV pattern |
| Design Twitter / social feed | [design_twitter](design_twitter.md) |
| Design Instagram feed | [design_twitter](design_twitter.md) — fan-out patterns identical |
| Design WhatsApp / messaging system | [design_whatsapp](design_whatsapp.md) |
| Design Slack | [design_whatsapp](design_whatsapp.md) + channel fan-out from [design_twitter](design_twitter.md) |
| Design Uber / Lyft | [design_uber](design_uber.md) |
| Design DoorDash dispatch | [design_uber](design_uber.md) — geo-matching core is the same |
| Design Netflix / YouTube | [design_netflix](design_netflix.md) |

---

## Back to HLD Section

[HLD Master Index](../README.md)
