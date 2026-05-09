# System Design: Uber

## Table of Contents
1. Requirements Clarification
2. Scale Estimation
3. High-Level Architecture
4. Real-time Location Tracking
5. Driver-Rider Matching
6. Surge Pricing
7. Trip State Machine
8. Database Design
9. Payment Processing
10. Notifications
11. Maps and ETA
12. Bottlenecks and Solutions
13. Trade-offs
14. Interview Discussion Tips

---

## Intuition

> **Design intuition**: Uber's core challenge is real-time geospatial matching at scale — matching millions of driver locations (updating every 4 seconds) to millions of ride requests, with sub-second response time. The key data structures are geohashing (for proximity queries) and the dispatch algorithm (ETA-based matching).

**Key insight**: The matching problem is fundamentally a nearest-neighbor search in a constantly changing dataset. Geohashing divides the world into hierarchical grid cells — finding nearby drivers means querying the driver's cell + neighboring cells, then ranking by ETA. This makes proximity queries O(1) instead of O(N) where N = total drivers.

---

## 1. Requirements Clarification

### Functional Requirements
- **Request Ride**: Rider opens app, enters destination, sees fare estimate, requests ride
- **Match Driver**: System finds nearest available driver and assigns them to the ride
- **Real-time Tracking**: Both rider and driver can see each other's location on a live map
- **Payment**: Automatic payment at trip end via stored card (cashless in most markets)
- **Rating**: Both rider and driver can rate each other after trip completion
- **Surge Pricing**: Dynamic pricing based on real-time supply/demand in an area
- **Trip History**: Users can view past trips with details and receipts
- **Driver Registration**: Drivers can register, go online/offline, accept/reject rides

### Non-Functional Requirements
- **Low Latency**: Driver match must happen within 5 seconds of request
- **High Availability**: 99.99% uptime — a failed ride request has direct revenue impact
- **Real-time**: Location updates must reflect within 1-2 seconds on both screens
- **Consistency**: Trip state must be consistent (a trip cannot be assigned to two drivers)
- **Durability**: Payment records must be durable and accurate
- **Globally Distributed**: Must work in 70+ countries with local regulations

### Out of Scope
- Uber Eats (food delivery)
- Uber for Business (corporate accounts)
- Driver onboarding and background checks

---

## 2. Scale Estimation

### Users and Traffic
- 25M rides per day globally
- 5M active drivers (some fraction online at any time; assume 1M drivers online at peak)
- 75M monthly active riders
- Peak concurrent active trips: ~500K

### Location Update Load (Most Critical)
- Online drivers send location every **4 seconds**
- 1M online drivers / 4 sec = **250K location writes/sec**
- Riders in active trips also send location: ~500K / 4 sec = ~125K more
- **Total: ~375K location updates/sec** (often cited as ~1.25M in peak scenarios globally including all regions)

### Matching QPS
- 25M rides / 86,400 sec = ~290 ride requests/sec
- Each request triggers a nearby driver search = 290 geospatial queries/sec

### Storage
- Trip record: ~500 bytes
- 25M trips/day * 500 bytes = **12.5 GB/day** of trip data
- GPS trace for a trip (every 4 sec for 20 min avg): 300 points * 16 bytes = 4.8 KB per trip
- 25M * 4.8 KB = **120 GB/day** of GPS trace data
- 5-year storage: ~220 TB for trips + GPS data

---

## 3. High-Level Architecture

```
  +------------------+               +------------------+
  |   Rider App      |               |   Driver App     |
  | (iOS/Android)    |               | (iOS/Android)    |
  +--------+---------+               +---------+--------+
           |                                   |
           |  HTTPS REST/WebSocket             |  HTTPS REST/WebSocket
           v                                   v
  +------------------+               +------------------+
  |   API Gateway    |               |   API Gateway    |
  | (Auth, Routing,  |               | (Auth, Routing,  |
  |  Rate Limiting)  |               |  Rate Limiting)  |
  +--------+---------+               +---------+--------+
           |                                   |
           +-------------------+---------------+
                               |
           +-------------------v-------------------+
           |                                       |
  +--------v---------+                 +-----------v---------+
  | Location Service |                 | Matching Service    |
  | (WebSocket       |                 | (Find nearest       |
  |  receiver)       |                 |  available drivers) |
  +--------+---------+                 +-----------+---------+
           |                                       |
           v                                       v
  +--------+----------+                +-----------+---------+
  | Redis (Geo Index) |                | Trip Service        |
  | Driver locations  |                | (State machine,     |
  | (GEOADD/GEORADIUS)|                |  trip lifecycle)    |
  +-------------------+                +-----------+---------+
                                                   |
           +------------------+        +-----------v---------+
           | Surge Pricing    |        | Notification Service|
           | Service          |        | (Push to rider/     |
           | (Supply/demand   |        |  driver apps)       |
           | per geohash cell)|        +---------------------+
           +------------------+
                                       +---------------------+
                                       | Payment Service     |
                                       | (Post-trip billing) |
                                       +---------------------+
```

---

## 4. Real-time Location Tracking

### Why WebSocket (Not REST Polling)
- REST polling (driver app calls `/update_location` every 4 sec): works but creates overhead per request (TCP handshake, HTTP headers, auth check)
- **WebSocket**: persistent TCP connection, send data with minimal overhead
- At 1M drivers: 1M open WebSocket connections to Location Service servers
- Each connection server handles ~50K connections → need ~20 connection servers

### Location Update Pipeline
```
Driver App
    |
    | WebSocket frame: {driver_id, lat, lng, timestamp, heading, speed}
    v
Location Service (Connection Server)
    |
    | 1. Validate driver is active
    | 2. Convert lat/lng to geohash
    v
Redis Geospatial Index
    | GEOADD drivers_online {lng} {lat} {driver_id}
    |
    | Also update:
    | HSET driver:{driver_id} lat {lat} lng {lng} ts {ts} status "available"
    v
Kafka (location_update topic)
    |
    +--> Trip Service (if driver has active trip: relay location to rider)
    +--> Analytics Service (GPS trace storage, ETA model updates)
    +--> Surge Pricing Service (density recalculation per geohash)
```

### Geohash for Spatial Indexing

Geohash encodes a lat/lng coordinate into a short string:
```
Precision 6: ~1.2 km × 0.6 km cell   (good for city-level density)
Precision 7: ~150m × 75m cell         (good for street-level matching)

Example: New York Times Square
  Lat: 40.7580, Lng: -73.9855
  Geohash: dr5ru7
```

How Uber uses geohash for matching:
1. Rider requests ride at lat/lng → convert to geohash (precision 7)
2. Look up available drivers in same geohash cell
3. If fewer than N drivers found, expand to neighboring cells (8 neighbors at same precision)
4. If still not enough, reduce precision by 1 (larger cell) and search again

### Redis Geospatial Commands
```bash
# Driver goes online / updates location
GEOADD drivers_available -73.9855 40.7580 "driver_abc"

# Find drivers within 2km of rider
GEORADIUS drivers_available -73.9850 40.7575 2 km ASC COUNT 10

# Response: [driver_abc (0.3km), driver_xyz (0.8km), driver_def (1.4km)]

# Remove driver when they go offline or get matched
ZREM drivers_available "driver_abc"
```

### Handling 375K Location Updates/Sec
- Partition location updates by geographic region (continent/country) across Redis clusters
- Each Redis instance handles a geographic partition
- Use Redis Cluster for horizontal scaling
- Location data in Redis has TTL (expire driver after 30 sec of no update = went offline)

---

## 5. Driver-Rider Matching

### Matching Algorithm
```
Ride Request Event: {rider_id, pickup_lat, pickup_lng, destination}
    |
    v
Matching Service:
  1. Query Redis GEORADIUS for available drivers within 2km (expand if < 3 results)
  2. For each candidate driver:
     - Calculate ETA from driver's current location to rider pickup (Google Maps API or internal)
     - Check driver rating (exclude drivers below threshold)
     - Check driver vehicle type matches requested service tier
  3. Rank candidates by: ETA (primary), rating (secondary)
  4. Select top candidate → set driver status to "PENDING_ACCEPTANCE" in Redis
  5. Send ride request push notification to driver
  6. Wait up to 10 seconds for driver acceptance
  7. If accepted: create trip record, notify rider
  8. If rejected or timeout: try next candidate
```

### Preventing Double-Booking
- Driver status stored in Redis with compare-and-swap (CAS):
```bash
# Atomic: only set to PENDING if currently AVAILABLE
SET driver:abc:status PENDING NX EX 15
# NX = only set if Not eXists (not already set)
# EX 15 = expire in 15 seconds (auto-release if no response)
```
- If two matching requests race to claim the same driver, only one will succeed (Redis is single-threaded for commands)

### Supply-Demand Balancing
- If demand > supply in an area: raise surge multiplier to incentivize more drivers to come online
- If supply > demand: lower surge, potentially prompt idle drivers to move to higher-demand zones
- Matching service factors in supply-demand balance when ranking candidates

---

## 6. Surge Pricing

### Goal
Dynamically adjust prices to balance supply (drivers) and demand (ride requests) in real time.

### Calculation
```
For each geohash cell (precision 6, ~1 km²) every 5 minutes:

  available_drivers = count of AVAILABLE drivers in cell
  pending_requests  = count of unmatched ride requests in last 5 min in cell

  demand_supply_ratio = pending_requests / max(available_drivers, 1)

  surge_multiplier:
    ratio < 0.5  → 1.0x (no surge)
    ratio 0.5-1  → 1.2x
    ratio 1-2    → 1.5x
    ratio 2-3    → 2.0x
    ratio 3-5    → 2.5x
    ratio > 5    → 3.0x (cap at 3x in most markets)
```

### Implementation
- Surge Pricing Service subscribes to location updates and ride request events from Kafka
- Maintains a counter per geohash cell in Redis
- Runs a scheduled job every minute to recalculate surge per cell
- Stores current surge: `HSET surge:dr5ru 1.5`
- API Gateway reads surge multiplier before showing fare estimate to rider

### Rider Experience
- Rider sees surge multiplier and must explicitly confirm ("Prices are 2.0x higher than normal. Confirm?")
- This confirmation creates a price lock (guaranteed fare won't increase during trip)

---

## 7. Trip State Machine

### States
```
REQUESTED
    |
    | Driver accepts
    v
DRIVER_ASSIGNED
    |
    | Driver starts navigating to pickup
    v
DRIVER_ARRIVING
    |
    | Driver arrives at pickup location
    v
DRIVER_ARRIVED (waiting for rider)
    |
    | Rider gets in, driver starts trip
    v
TRIP_STARTED
    |
    | Driver reaches destination
    v
TRIP_ENDED
    |
    | Payment processed
    v
PAID
    |
    | Ratings exchanged (optional)
    v
COMPLETED

CANCELLED (from REQUESTED, DRIVER_ASSIGNED, or DRIVER_ARRIVING)
```

### State Transitions (Trip Service)
- Each state transition is an atomic write to the database
- Kafka event emitted on each transition (consumers: notification service, billing service, analytics)
- Invalid transitions are rejected (e.g., cannot go from REQUESTED to TRIP_STARTED directly)

```python
# Trip state transition (pseudocode)
def transition_trip(trip_id, new_state, actor_id):
    trip = db.get_trip(trip_id)
    if not is_valid_transition(trip.state, new_state):
        raise InvalidTransitionError
    trip.state = new_state
    trip.updated_at = now()
    db.save_trip(trip)  # atomic write
    kafka.emit("trip_state_changed", {trip_id, new_state, actor_id})
```

---

## 8. Database Design

### Redis (Hot Data — Active Trips and Driver Locations)
```
driver:{driver_id}         HASH: {lat, lng, heading, status, last_update}
trip:{trip_id}             HASH: {rider_id, driver_id, state, pickup, dest, fare}
drivers_available          Geo sorted set (GEOADD)
surge:{geohash}            STRING: surge multiplier
driver:{driver_id}:status  STRING: AVAILABLE | PENDING | ON_TRIP (with TTL)
```

### Cassandra (Trip History — Write-Heavy, Distributed)
```sql
-- Primary trip records
CREATE TABLE trips (
    trip_id     UUID PRIMARY KEY,
    rider_id    UUID,
    driver_id   UUID,
    status      TEXT,
    pickup_lat  DOUBLE,
    pickup_lng  DOUBLE,
    dest_lat    DOUBLE,
    dest_lng    DOUBLE,
    fare        DECIMAL,
    distance_km DOUBLE,
    duration_sec INT,
    surge       DOUBLE,
    created_at  TIMESTAMP,
    ended_at    TIMESTAMP
);

-- For rider's trip history (lookup by rider)
CREATE TABLE trips_by_rider (
    rider_id    UUID,
    trip_id     UUID,
    created_at  TIMESTAMP,
    fare        DECIMAL,
    PRIMARY KEY (rider_id, created_at, trip_id)
) WITH CLUSTERING ORDER BY (created_at DESC);

-- For driver's trip history (lookup by driver)
CREATE TABLE trips_by_driver (
    driver_id   UUID,
    trip_id     UUID,
    created_at  TIMESTAMP,
    fare        DECIMAL,
    PRIMARY KEY (driver_id, created_at, trip_id)
) WITH CLUSTERING ORDER BY (created_at DESC);

-- GPS trace (high write volume)
CREATE TABLE gps_traces (
    trip_id     UUID,
    recorded_at TIMESTAMP,
    lat         DOUBLE,
    lng         DOUBLE,
    speed       FLOAT,
    heading     FLOAT,
    PRIMARY KEY (trip_id, recorded_at)
) WITH CLUSTERING ORDER BY (recorded_at ASC);
```

### PostgreSQL (User and Driver Profiles)
```sql
CREATE TABLE users (
    user_id         UUID PRIMARY KEY,
    phone           VARCHAR(20) UNIQUE,
    email           VARCHAR(255),
    name            VARCHAR(100),
    rating          DECIMAL(3,2),  -- e.g., 4.87
    ride_count      INTEGER,
    created_at      TIMESTAMP,
    payment_method  UUID REFERENCES payment_methods(id)
);

CREATE TABLE drivers (
    driver_id       UUID PRIMARY KEY,
    user_id         UUID REFERENCES users(user_id),
    license_no      VARCHAR(50),
    vehicle_id      UUID REFERENCES vehicles(vehicle_id),
    status          TEXT,          -- OFFLINE | AVAILABLE | ON_TRIP
    rating          DECIMAL(3,2),
    total_trips     INTEGER,
    approved_at     TIMESTAMP
);

CREATE TABLE vehicles (
    vehicle_id      UUID PRIMARY KEY,
    driver_id       UUID,
    make            VARCHAR(50),
    model           VARCHAR(50),
    year            INTEGER,
    plate           VARCHAR(20),
    tier            TEXT           -- UberX | UberXL | UberBlack
);
```

---

## 9. Payment Processing

### Flow
```
Trip ends (TRIP_ENDED state)
    |
    v
Payment Service (triggered by Kafka event)
    |
    | 1. Calculate final fare:
    |    base_fare + (per_km_rate * distance) + (per_min_rate * duration)
    |    * surge_multiplier
    |    + tolls (if any, from maps API)
    v
    | 2. Apply any promotions/discount codes
    v
    | 3. Charge rider's saved payment method (credit card / UPI / wallet)
    |    via payment gateway (Stripe, Braintree, local PSP)
    v
    | 4. On success: transition trip to PAID, send receipt email/SMS
    | 5. On failure: retry 3x, then flag for manual resolution
    v
Escrow Model:
    - Rider's card pre-authorized at trip start (estimated fare + buffer)
    - Final charge captured at trip end with actual fare
    - Pre-authorization holds the amount but does not charge until capture
```

### Why Escrow/Pre-authorization?
- Rider may have insufficient funds → catch this upfront, not after the trip
- Protects driver from unpaid trips
- Allows dynamic fare adjustments (surge that increases during trip is absorbed by buffer)

---

## 10. Notifications

### Notification Types and Channels
| Event | Recipient | Channel |
|-------|-----------|---------|
| Driver found | Rider | Push notification |
| Driver arriving | Rider | Push notification |
| Ride request | Driver | Push notification + in-app alert |
| Trip started | Rider | Push notification |
| Payment receipt | Rider | Push notification + Email |
| Surge pricing active | Nearby riders | Push notification |

### Implementation
```
Kafka (trip_state_changed topic)
    |
    v
Notification Service
    |
    | 1. Determine notification type from event
    | 2. Look up user's device tokens (from Device Registry)
    | 3. Check notification preferences
    | 4. Route to appropriate channel:
    |
    +---> APNs (Apple Push Notification Service) — iOS
    +---> FCM (Firebase Cloud Messaging) — Android
    +---> SMS gateway (Twilio) — fallback for critical alerts
    +---> Email (SES/SendGrid) — receipts, weekly summaries
```

---

## 11. Maps and ETA

### Map Data Options
- **Google Maps Platform**: accurate, global coverage, expensive at scale
- **Mapbox**: cheaper, customizable, good for display
- **In-house (Uber's approach)**: Uber built their own mapping stack (H3, OSRM, custom routing)

### Uber H3: Hexagonal Grid System
- Uber divides the globe into hexagonal cells at multiple resolutions
- Hexagons (vs. squares/geohashes) have uniform distance to all 6 neighbors
- Used for: surge pricing zones, driver supply analysis, heat maps, demand prediction

```
Resolution 7: ~5 km² hexagons  → surge pricing zones
Resolution 9: ~0.1 km² hexagons → matching radius
Resolution 11: ~25 m² hexagons  → precise driver positioning
```

### ETA Calculation
- Short distances: OSRM (Open Source Routing Machine) with local road graph
- Traffic-aware: Uber collects real-time GPS data from all active drivers to build live traffic model
- Machine learning ETA model:
  - Features: origin, destination, time of day, day of week, weather, historical travel times
  - Trained on millions of historical trips
  - Continuously updated as new trip data arrives

---

## 12. Bottlenecks and Solutions

| Bottleneck | Impact | Solution |
|---|---|---|
| 375K location updates/sec | Redis write hotspot | Geo-partitioned Redis Cluster, partition by region |
| Matching latency | Rider waits > 5 sec | Pre-built geospatial index in Redis, GEORADIUS < 1ms |
| Driver double-booking | Two riders assigned same driver | Redis SET NX for atomic driver status claim |
| Surge pricing computation | Stale surge data | Kafka Streams real-time aggregation, 1-minute refresh |
| Trip state race conditions | Inconsistent trip state | Cassandra LWT (lightweight transactions), optimistic locking |
| Payment failures | Revenue loss, rider friction | Pre-authorization, retry with exponential backoff, manual review queue |
| Peak traffic (New Year's Eve) | All services overloaded | Horizontal auto-scaling, pre-provisioned capacity for known events |

---

## 13. Trade-offs Made

### Geohash vs. H3 for Spatial Indexing
- **Choice**: Redis geospatial (internally uses sorted set with geohash) for matching; H3 for analytics/surge
- **Reason**: Redis GEORADIUS is battle-tested and fast; H3 is better for analytical use cases
- **Trade-off**: Two spatial indexing systems to maintain

### WebSocket vs. HTTP Long Polling for Location
- **Choice**: WebSocket
- **Reason**: Lower overhead, true bidirectional, essential for real-time tracking
- **Trade-off**: Stateful connections (connection servers hold state); harder to scale horizontally

### Cassandra vs. PostgreSQL for Trip History
- **Choice**: Cassandra for trip history, PostgreSQL for profiles
- **Reason**: Trip data is high write volume, immutable, geographically distributed; profile data has complex queries and relationships
- **Trade-off**: Two databases, different query patterns

### Strong Consistency for Trip State
- **Choice**: Use Cassandra Lightweight Transactions (LWT) for trip state transitions (at the cost of latency)
- **Reason**: A trip double-assigned to two drivers is a terrible user experience; correctness is critical here
- **Trade-off**: LWT adds ~10ms latency to state transitions (uses Paxos internally)

### Pre-authorization vs. Post-payment
- **Choice**: Pre-authorize at trip start, capture at trip end
- **Reason**: Prevents unpaid trips, gives rider upfront commitment
- **Trade-off**: Temporary hold on rider's account; some payment methods don't support pre-authorization

---

## 14. Interview Discussion Tips

### How to Structure Your Answer (45-minute interview)
1. **Clarify requirements** (5 min): ride request, matching, tracking, payment, surge
2. **Scale estimation** (5 min): 25M rides/day, 1M drivers online, 375K location updates/sec
3. **High-level architecture** (5 min): draw the service diagram
4. **Location tracking deep dive** (10 min): WebSocket, Redis GEORADIUS, geohash — this is the most interesting part
5. **Matching algorithm** (5 min): GEORADIUS query, driver claim with SET NX, fallback expansion
6. **Surge pricing** (5 min): supply-demand ratio per geohash cell, real-time computation
7. **Trip state machine** (5 min): states, transitions, Cassandra LWT for consistency
8. **Database design** (5 min): Redis (hot), Cassandra (trips), PostgreSQL (profiles)

### Key Things Interviewers Look For
- **Location update scale**: 375K/sec is the defining scalability challenge — must address it
- **Geospatial indexing**: geohash or H3 with Redis GEORADIUS (not "just query the database")
- **Driver double-booking prevention**: atomic Redis SET NX operation
- **Eventual vs. strong consistency**: which parts need strong consistency (trip state, payment) vs. eventual (location, surge)
- **State machine thinking**: explicit states for trip lifecycle

### Common Mistakes to Avoid
- Polling for location updates (use WebSocket)
- Querying relational DB for "find nearest drivers" (must use geospatial index)
- Not mentioning the driver double-booking race condition
- Ignoring surge pricing mechanism
- Using a single database for everything

### Follow-up Questions You May Get
- "How do you handle a driver going offline mid-trip?" — Heartbeat timeout, flag trip, contact rider, attempt reassignment
- "How does the driver app work offline (tunnel)?" — Local GPS buffering, replay when connection restored
- "How do you calculate ETA accurately?" — ML model on historical trips + real-time traffic from driver GPS traces
- "How do you handle payments in countries with different payment methods?" — Payment service abstraction layer with per-country payment adapters
- "How does the surge pricing defend against manipulation?" — Rate limit requests, anomaly detection on request patterns

### Numbers to Remember
- 25M rides/day, 5M drivers globally, 1M online at peak
- Location update: every 4 seconds
- 375K location updates/sec
- Match must happen in < 5 seconds
- Geohash precision 6 = ~1km², precision 7 = ~150m
- Redis GEORADIUS typical latency: < 1ms
- H3 resolution 7 = ~5 km² hexagons for surge zones
