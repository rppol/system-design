# System Design: Hotel Reservation System

## Intuition

> **Design intuition**: A hotel reservation system looks, on the surface, like a search problem — "find me a room in Paris for these three nights under $200." But search is the easy 95% of the traffic and the easy 5% of the engineering risk. The hard part is the other direction: **"available" is a claim about the future, made by reading a number that two people can read at the same moment and both believe is true.** A search result that says "1 room left" is a snapshot, not a promise — and the system must arbitrate, in the few seconds between two people seeing that snapshot and clicking "Book," who actually gets the room without either selling it twice or leaving it empty. Every other piece of this design — the inventory model, the hold-with-TTL mechanism, the booking saga, the cache-staleness tradeoffs — exists in service of resolving that race correctly, cheaply, and at a scale where the read traffic outnumbers the write traffic by a factor not unlike a URL shortener.

**Key insight**: The system has two fundamentally different consistency requirements living side by side. **Search** ("show me available rooms") can be stale by seconds or minutes — a slightly-out-of-date result just means an extra click before the user discovers the room sold out, an annoyance but not a financial loss. **Booking** (the moment a specific room-night is decremented from the inventory count) must be linearizable — it is the one place in the system where "probably correct" causes chargebacks, overbooking penalties, and a guest standing at a front desk with no room. The entire architecture is organized around keeping these two paths separate: a wide, cheap, eventually-consistent read path for search, and a narrow, strongly-consistent, carefully-guarded write path for the actual reservation — connected by a **hold-with-TTL** mechanism that gives a user just enough exclusivity to complete payment without permanently locking inventory against a booking that might never finish.

---

## 1. Requirements Clarification

### Functional Requirements
- **Search availability**: given a destination (city/region), check-in date, check-out date, number of guests, and optional filters (price range, star rating, amenities), return a ranked list of hotels with available rooms and per-night prices for the requested date range.
- **View hotel/room details**: given a hotel ID, return room types, photos, amenities, cancellation policy, and a per-date availability/price calendar for the next N months.
- **Hold a room**: when a user selects a room and date range, place a short-lived "hold" on that inventory so that, while the user enters payment details, no one else can book the same room-night out from under them.
- **Book a room**: convert a valid, unexpired hold into a confirmed reservation, charging the guest (cross-ref [`./design_payment_system.md`](./design_payment_system.md)) and permanently decrementing inventory for the stayed date range.
- **Cancel / modify a reservation**: cancel a confirmed booking (subject to the hotel's cancellation policy) or change dates/room type, releasing or re-decrementing inventory accordingly.
- **Rate and inventory management (hotelier-facing)**: hotels (directly or via a channel manager / Property Management System, §6) update room counts, rates, and restrictions (minimum stay, closed-to-arrival) that flow into the search and booking paths.

### Non-Functional Requirements
- **No overbooking beyond a configured tolerance**: the system must never confirm more bookings for a (hotel, room type, date) than `total_rooms` allows — except where a hotel has explicitly opted into a small overbooking buffer (a common hotel-industry practice to offset no-shows), which is an explicit, hotel-configured number, not an accidental one.
- **Extreme read:write ratio**: search traffic vastly exceeds booking traffic — directionally similar to a URL shortener's 100:1 read/write split (§2), so the read path must be horizontally scalable and cache-friendly without compromising the write path's correctness.
- **Search latency**: p99 search latency under roughly 300-500ms even when a search spans hundreds of hotels and a multi-night date range.
- **Booking latency and correctness over speed**: a booking request can take longer than a search request (it includes a hold, a payment call, and a confirmation) — correctness (never double-selling a room) is prioritized over shaving milliseconds off this path.
- **Global scale, regional data locality**: hotels and their inventory are geographically anchored; the system should serve searches and bookings for a region primarily from infrastructure local to that region.
- **Auditability**: every inventory change (hold placed, hold released, booking confirmed, booking canceled) must be traceable — hoteliers and finance teams need to reconcile "why does our occupancy report show 41 rooms sold when we have 40 rooms."

### Out of Scope
- **Dynamic pricing / revenue management algorithms** — the system stores and serves rates set by a pricing engine (a hotel's revenue manager or an automated yield-management system), but the algorithm that decides "raise the rate for Friday because demand is high" is a separate ML/analytics system, not designed here.
- **Loyalty programs and points redemption** — points balances, tier benefits, and redemption-as-payment are treated as an integration the Payment step (§4.3, cross-ref [`./design_payment_system.md`](./design_payment_system.md)) can call out to, not designed here.
- **Property Management System (PMS) internals** — the on-premises software hotels use to manage front-desk operations, housekeeping, and check-in/check-out is an external system this design integrates with (§6), not one this design builds.

---

## 2. Scale Estimation

### Inventory Footprint
- **500,000 hotels/properties** globally (a Booking.com/Expedia-scale aggregator), averaging **80 rooms/property** across **3 room types** (e.g., standard, deluxe, suite) -> roughly **40 million physical rooms**.
- Inventory is tracked per (hotel_id, room_type, date) — call this an **inventory row**. For a rolling **2-year (730-day) booking horizon**: `500,000 hotels x 3 room types x 730 days` ~= **~1.1 billion inventory rows**.
- Each inventory row is small — `(hotel_id, room_type, date, total_rooms, available_rooms, rate_cents, version)` ~= **~40 bytes** -> 1.1B x 40 bytes ~= **~44 GB** for the entire 2-year global inventory table — large, but well within reach of a sharded relational store (§4.5).

### Search (Read) Traffic
- **200 million searches/day** globally (a search is "show me hotels in Paris, these dates").
- 200,000,000 / 86,400 ~= **~2,300 searches/sec average**, peaking at roughly **3-4x average during seasonal/flash-sale spikes** -> **~8,000-9,000 searches/sec peak**.
- A single search typically fans out to **50-200 candidate hotels** for the destination, and for a multi-night stay (avg ~3 nights) checks availability across **all 3 nights** for each candidate's relevant room types — so one "search" can imply tens of thousands of inventory-row lookups, which is exactly why this path is served from cache/read replicas, never the booking source of truth (§4.4).

### Booking (Write) Traffic
- **2 million confirmed bookings/day** -> 2,000,000 / 86,400 ~= **~23 bookings/sec average**, peaking at **~150-200 bookings/sec** during flash sales or major events.
- **Read:write ratio**: ~2,300 : 23 ~= **~100:1** at average load — directionally identical to the URL shortener's classic 100:1 split (cross-ref [`./design_url_shortener.md`](./design_url_shortener.md)), which is the headline justification for treating search and booking as architecturally separate paths with very different consistency models.
- Each booking touches `room_type_count x nights` inventory rows — for a 3-night stay, **3 inventory-row decrements per booking**, all of which must succeed or none must (§4.2) -> ~23 bookings/sec x 3 rows ~= **~70 inventory-row writes/sec average**, ~500-600/sec at peak. This is a tiny absolute number compared to the read volume, but each write carries a correctness obligation the reads don't.

### Hold Volume
- Not every search-to-hold conversion becomes a booking — industry abandonment rates mean roughly **3-5x more holds are created than bookings completed**. At 2M bookings/day and a conservative 4x ratio: **~8 million holds/day** -> ~93 holds/sec average, ~400-500/sec peak.
- A hold's TTL (§4.2) is typically **10-15 minutes** — at steady state, the number of *concurrently active* holds is `holds_per_sec x TTL_seconds` ~= `93 x 720` ~= **~67,000 concurrently active holds** at average load, growing to the low hundreds of thousands at peak. Each hold record is small (~150 bytes: hotel_id, room_type, date range, user/session ID, expiry) -> ~67,000 x 150 bytes ~= **~10MB** — trivial for an in-memory store, but the *operation* of placing/expiring a hold (an atomic decrement/increment, §4.2) is the operation this entire design protects.

### Cache Footprint for Search
- The "hot" search surface — popular destinations and the next 90 days — covers a much smaller slice than the full 2-year horizon: roughly **50,000 high-traffic hotels x 3 room types x 90 days** ~= **~13.5 million inventory rows**, at ~40 bytes each ~= **~540MB**. This comfortably fits in a single large Redis/Memcached tier with room for replication, and is the working set behind §4.4's cache-aside search layer.

---

## 3. High-Level Architecture

```
                                   +----------------------+
                                   |       Clients         |
                                   |  (Web / Mobile App,    |
                                   |   ~8-9K searches/sec   |
                                   |   peak, ~150-200       |
                                   |   bookings/sec peak)   |
                                   +-----------+------------+
                                               |
                                               v
                                   +----------------------+
                                   |     API Gateway        |
                                   |  (authn, rate limiting,|
                                   |   routing)             |
                                   +-----------+------------+
                                               |
                  +----------------------------+----------------------------+
                  |                                                          |
                  v                                                          v
       +---------------------+                                  +-------------------------+
       |   Search Service      |                                 |   Booking Service        |
       |  (read path, §4.4)     |                                 |  (write path / saga,     |
       |                       |                                  |   §4.2, §4.3)            |
       |  - resolves dest ->    |                                 |                          |
       |    candidate hotel IDs |                                 |  1. validate hold        |
       |  - per-hotel/room-type/|                                 |  2. create/extend hold   |
       |    date availability   |                                 |     (atomic decrement,   |
       |    + price lookups     |                                 |     TTL)                 |
       +-----------+------------+                                 |  3. call Payment Service |
                   |                                              |  4. confirm or release   |
                   | cache-aside, short TTL                       +-----------+--------------+
                   v                                                          |
       +---------------------+                                               |
       |  Search Cache         |                                              |
       |  (Redis, ~540MB hot   |                                              |
       |   working set, TTL    |                                              |
       |   30-120s, §4.4)      |                                              |
       +-----------+------------+                                            |
                   | cache miss                                              |
                   v                                                          v
       +-------------------------------------------------------------------------------+
       |                       Inventory Service (source of truth, §4.1, §4.2)          |
       |                                                                                  |
       |  inventory table: (hotel_id, room_type, date) -> total_rooms, available_rooms, |
       |  rate_cents, version          -- sharded by hotel_id / region (§4.5)            |
       |                                                                                  |
       |  holds table: (hold_id, hotel_id, room_type, date_range, expires_at, status)    |
       +--------------------------+-------------------------------------------------------+
                                  |
                  +----------------+----------------+
                  |                                  |
                  v                                  v
       +---------------------+          +-------------------------+
       |  Payment Service      |          |   Hold-Expiry Worker     |
       |  (cross-ref            |         |  (background job, scans  |
       |   design_payment_      |         |   expired holds, releases|
       |   system.md)            |         |   inventory back, §4.2)  |
       +---------------------+          +-------------------------+

                  Out-of-band: Rate/Inventory Management (hoteliers / channel managers / PMS, §6)
       +---------------------------------------------------------------------------------+
       |   Channel Manager / PMS Sync  -->  Inventory Service (writes total_rooms, rates)  |
       +---------------------------------------------------------------------------------+
```

### Request Flow

1. **Search**: a search request (`destination, check-in, check-out, guests, filters`) hits the **Search Service**, which resolves the destination to a set of candidate hotel IDs (typically via a geo/region index, cross-ref [`./design_proximity_service.md`](./design_proximity_service.md) for the geo-search half of this problem), then looks up per-hotel availability and pricing for the requested date range from the **Search Cache** — a Redis layer with a short TTL (§4.4) sitting in front of the Inventory Service's read replicas.
2. **Hold**: when the user selects a room, the **Booking Service** calls the **Inventory Service** to atomically decrement `available_rooms` for every (hotel_id, room_type, date) row in the stay's date range and create a `hold` row with a TTL (§4.2). This is the first point where the request touches the strongly-consistent source of truth, not the cache.
3. **Payment**: the Booking Service calls the **Payment Service** (cross-ref [`./design_payment_system.md`](./design_payment_system.md)) with the held reservation's total and an idempotency key. The Payment Service's own guarantees (exactly-once charge, double-entry ledger) are reused as-is — this design does not re-implement them.
4. **Confirm or release**: on payment success, the hold is converted into a confirmed `booking` row (the decrement becomes permanent); on payment failure or hold-TTL expiry, the **Hold-Expiry Worker** (or the booking flow itself, on failure) increments `available_rooms` back, releasing the inventory for other searchers.
5. **Inventory updates from hoteliers**: independent of guest traffic, hotels (or their channel-manager/PMS integrations, §6) push updates to `total_rooms`, rates, and restrictions into the Inventory Service — these writes invalidate the relevant Search Cache entries (§4.4) so that rate/availability changes propagate to search results within the cache's short TTL.

---

## 4. Component Deep Dives

### 4.1 Inventory Model

The foundational data structure is the **inventory row**: one row per `(hotel_id, room_type, date)`, holding `total_rooms` (the hotel's configured allotment for that room type on that date) and `available_rooms` (how many are not yet held or booked).

```sql
CREATE TABLE inventory (
    hotel_id        BIGINT      NOT NULL,
    room_type_id    BIGINT      NOT NULL,
    stay_date       DATE        NOT NULL,
    total_rooms     INT         NOT NULL,
    available_rooms INT         NOT NULL,
    rate_cents      BIGINT      NOT NULL,   -- per-night rate for this date
    version         BIGINT      NOT NULL DEFAULT 0,  -- optimistic-lock counter
    PRIMARY KEY (hotel_id, room_type_id, stay_date),
    CHECK (available_rooms >= 0),
    CHECK (available_rooms <= total_rooms)
);
```

Two design points fall directly out of this schema:

- **Why a row per date, not a date-range row**: a hotel's allotment for a room type can (and does) change night-to-night — a hotel might allocate 20 "Deluxe King" rooms on weeknights and only 10 on weekends (holding the rest back for higher-rate weekend packages), or close out a specific date entirely for a private event. A single `(start_date, end_date, count)` range row cannot represent "10 available on Friday, 20 on Saturday" without becoming a list of ranges anyway — so the system normalizes to one row per date from the start, and a "rate plan + allotment" variant (where a *rate plan* references a *base allotment* with date-specific overrides) is layered on top as a modeling convenience for hoteliers, not a different underlying storage shape.
- **Why a 3-night booking must decrement 3 rows atomically**: a guest booking "Friday through Monday checkout" (3 nights: Fri, Sat, Sun) needs `available_rooms > 0` for **all three** date rows, and all three decrements must happen together. If only Friday and Saturday decrement successfully but Sunday's row has zero availability, the booking must roll back Friday and Saturday too — a guest cannot be booked into "2 nights of a 3-night reservation." This is the direct motivation for treating a hold (§4.2) as **one multi-row atomic operation**, not three independent ones.

### 4.2 Overbooking Prevention and Concurrency Control

This is the component the entire case study's intuition (above) is about. Three approaches are worth knowing; the design picks one as primary and explains why the other two are the alternatives.

#### Approach A: Atomic `WHERE available_rooms > 0` Decrement (Optimistic, DB-Enforced)

The simplest correct approach: every decrement is a single `UPDATE ... WHERE available_rooms >= N` statement, and the database's row lock plus the `WHERE` clause make "check" and "act" one atomic operation — there is no separate `SELECT` to read a value that might be stale by the time the `UPDATE` runs.

```sql
UPDATE inventory
SET available_rooms = available_rooms - 1, version = version + 1
WHERE hotel_id = ? AND room_type_id = ? AND stay_date = ?
  AND available_rooms >= 1;
-- 0 rows affected => sold out for this date, caller must reject/rollback
-- 1 row affected  => decrement succeeded
```

This is correct and cheap, but **on its own it has no concept of "the user is still entering their card number."** A naive booking flow that does this decrement and then waits on a slow payment call has effectively reserved the room for the duration of that call with no way to "give it back" cleanly if the user simply closes the tab — the inventory is decremented, but there's no record of *why*, and no automatic cleanup.

#### Approach B: Hold-with-TTL (Pessimistic Reservation, This Design's Primary Approach)

The design adds a **`holds` table** that records *why* inventory is decremented and *for how long* that decrement is allowed to stand without becoming a real booking:

```sql
CREATE TABLE holds (
    hold_id      UUID        PRIMARY KEY,
    hotel_id     BIGINT      NOT NULL,
    room_type_id BIGINT      NOT NULL,
    check_in     DATE        NOT NULL,
    check_out    DATE        NOT NULL,   -- exclusive; nights = check_out - check_in
    user_id      BIGINT      NOT NULL,
    status       VARCHAR(20) NOT NULL,   -- ACTIVE | CONFIRMED | RELEASED | EXPIRED
    expires_at   TIMESTAMPTZ NOT NULL,   -- created_at + TTL (10-15 min)
    idempotency_key VARCHAR(64) NOT NULL UNIQUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_holds_expiry ON holds (expires_at) WHERE status = 'ACTIVE';
```

The `available_rooms` decrement from Approach A still happens — a hold *is* a decrement — but it is now **paired with a durable record that says "this decrement is provisional, and reverts automatically at `expires_at` unless converted to a booking first."** This is the key property: the decrement and the "undo if nothing happens" logic are part of the same data model, not bolted on separately.

```java
@Service
public class InventoryService {

    private final JdbcTemplate jdbc;
    private static final Duration HOLD_TTL = Duration.ofMinutes(12);

    /**
     * Atomically decrements available_rooms for every date in [checkIn, checkOut)
     * for the given (hotelId, roomTypeId), and creates a time-bounded hold row.
     *
     * Returns the created hold on success. Throws SoldOutException if ANY date
     * in the range has insufficient availability -- in which case NO rows are
     * decremented (the whole operation is one DB transaction).
     *
     * idempotencyKey makes a retried request (e.g., client double-click, or a
     * network-timeout retry) a no-op rather than a second hold -- same pattern
     * as design_payment_system.md's Idempotency-Key handling.
     */
    @Transactional
    public Hold reserveRoom(long hotelId, long roomTypeId,
                             LocalDate checkIn, LocalDate checkOut,
                             long userId, String idempotencyKey) {

        // 1. Idempotency check FIRST -- a retried request with the same key
        //    must return the existing hold, not create a duplicate or
        //    double-decrement inventory.
        Optional<Hold> existing = findHoldByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        List<LocalDate> nights = checkIn.datesUntil(checkOut).toList();
        if (nights.isEmpty()) {
            throw new IllegalArgumentException("checkOut must be after checkIn");
        }

        // 2. Atomic, all-or-nothing decrement across every night of the stay.
        //    The WHERE available_rooms >= 1 guard makes "check" and "act" one
        //    statement per row -- no separate SELECT that could go stale.
        for (LocalDate night : nights) {
            int updated = jdbc.update("""
                UPDATE inventory
                SET available_rooms = available_rooms - 1, version = version + 1
                WHERE hotel_id = ? AND room_type_id = ? AND stay_date = ?
                  AND available_rooms >= 1
                """, hotelId, roomTypeId, night);

            if (updated == 0) {
                // Sold out for this specific night. Because we're inside
                // @Transactional, throwing here rolls back ANY decrements
                // already applied to earlier nights in this loop -- the
                // guest is never left holding "2 of 3 nights."
                throw new SoldOutException(hotelId, roomTypeId, night);
            }
        }

        // 3. Record the hold itself -- the durable "why" behind the
        //    decrements above, including its automatic expiry.
        UUID holdId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(HOLD_TTL);
        jdbc.update("""
            INSERT INTO holds
              (hold_id, hotel_id, room_type_id, check_in, check_out,
               user_id, status, expires_at, idempotency_key)
            VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
            """, holdId, hotelId, roomTypeId, checkIn, checkOut,
            userId, expiresAt, idempotencyKey);

        return new Hold(holdId, hotelId, roomTypeId, checkIn, checkOut,
                         userId, "ACTIVE", expiresAt);
    }

    /**
     * Converts an ACTIVE, unexpired hold into a permanent booking. Called
     * after the Payment Service (design_payment_system.md) confirms the
     * charge succeeded. The inventory decrement from reserveRoom() is NOT
     * repeated here -- it already happened; this just flips the hold's
     * status so the expiry worker (below) leaves it alone.
     */
    @Transactional
    public Booking confirmHold(UUID holdId, String paymentReference) {
        int updated = jdbc.update("""
            UPDATE holds SET status = 'CONFIRMED'
            WHERE hold_id = ? AND status = 'ACTIVE' AND expires_at > now()
            """, holdId);

        if (updated == 0) {
            // The hold expired (or was already confirmed/released) before
            // payment completed. Inventory has ALREADY been given back by
            // the expiry worker (or never will be confirmed) -- the caller
            // must NOT charge the guest for a room that's no longer held.
            throw new HoldExpiredException(holdId);
        }

        UUID bookingId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO bookings (booking_id, hold_id, payment_reference, status, created_at)
            VALUES (?, ?, ?, 'CONFIRMED', now())
            """, bookingId, holdId, paymentReference);

        return new Booking(bookingId, holdId, "CONFIRMED");
    }

    /**
     * Releases an ACTIVE hold's inventory back -- called either by the
     * booking flow itself (payment failed/declined) or by the background
     * Hold-Expiry Worker (§4.3) for holds whose TTL elapsed unconsumed.
     */
    @Transactional
    public void releaseHold(UUID holdId) {
        int updated = jdbc.update("""
            UPDATE holds SET status = 'RELEASED'
            WHERE hold_id = ? AND status = 'ACTIVE'
            """, holdId);

        if (updated == 0) {
            return; // already confirmed or already released -- no-op
        }

        Hold hold = findHold(holdId);
        for (LocalDate night : hold.checkIn().datesUntil(hold.checkOut()).toList()) {
            jdbc.update("""
                UPDATE inventory
                SET available_rooms = available_rooms + 1, version = version + 1
                WHERE hotel_id = ? AND room_type_id = ? AND stay_date = ?
                """, hold.hotelId(), hold.roomTypeId(), night);
        }
    }
}
```

Three correctness properties worth calling out explicitly:

1. **All-or-nothing across nights**: the `@Transactional` boundary around the per-night loop in `reserveRoom` is what prevents the "2 of 3 nights" failure mode described in §4.1 — a `SoldOutException` on night 3 rolls back the decrements already applied to nights 1 and 2.
2. **Idempotency-key dedup**: a retried `reserveRoom` call (double-click, client timeout-and-retry) with the same `idempotencyKey` returns the *existing* hold rather than creating a second one and double-decrementing — the same pattern as [`./design_payment_system.md`](./design_payment_system.md) §4.1's `Idempotency-Key` handling, applied to inventory instead of charges.
3. **`confirmHold` re-checks `expires_at`**: even if the hold row is still `ACTIVE` in the database, a `confirmHold` call arriving *after* `expires_at` must fail — otherwise a slow payment confirmation could "resurrect" a hold whose inventory the expiry worker already gave back to someone else, producing exactly the double-sell this design exists to prevent. The `WHERE status = 'ACTIVE' AND expires_at > now()` guard closes this window.

#### Approach C: Distributed Lock per (hotel_id, room_type, date-range)

A third option: before touching inventory at all, acquire a distributed lock (e.g., Redis `SETNX` with a TTL, or a ZooKeeper/etcd lease) keyed by `(hotel_id, room_type_id, date_range)`, do the read-check-decrement under the lock, then release it.

This is **strictly worse than Approach B for this problem** and is included mainly so an interview candidate can articulate *why* it's worse: a lock held across a payment call (which can take seconds and occasionally times out) either has to have a TTL long enough to cover the slowest payment call (during which **no other booking attempt for that room-night can even be *evaluated*, not just "must wait briefly"**) or a TTL short enough to be safe (in which case it can expire mid-payment, and now two parties can both believe they hold the lock). Approach B sidesteps this entirely: the *decrement* happens immediately and atomically (sub-millisecond, Approach A's mechanism), and the *TTL* governs only how long the provisional state can persist before auto-reverting — no lock is held across the slow payment call at all. The "lock" in Approach B is really just the `holds` row plus its `expires_at`, which is a much cheaper thing to hold than a live distributed-lock lease.

| Dimension | A: Atomic decrement only | B: Hold-with-TTL (this design) | C: Distributed lock per date-range |
|---|---|---|---|
| Prevents double-sell | Yes, for the instant of the decrement | Yes, plus governs the provisional window | Yes, while the lock is held |
| Handles "user abandoned checkout" | No — decrement is permanent with no undo path | Yes — TTL auto-reverts (§4.3) | Only if lock TTL is tuned correctly (hard) |
| Blocks other bookers during payment | No | No — only the specific held room-nights are unavailable, other inventory is untouched | Yes — the lock itself is the bottleneck |
| Operational complexity | Lowest | Moderate (needs the expiry worker, §4.3) | Highest (lock-service dependency, TTL tuning, fencing tokens) |
| This design's role | The atomic primitive *inside* B's decrement | **Primary approach** | Discussed as an alternative, not used |

### 4.3 Booking Saga

The end-to-end booking flow — **search (cached) -> hold -> pay -> confirm or release** — is a Saga (cross-ref [`../distributed_transactions/README.md`](../distributed_transactions/README.md)), because it spans two systems (Inventory Service and Payment Service) that cannot share a single database transaction, and one of those systems (the payment provider behind the Payment Service) is external and cannot participate in a 2PC.

```
  Client            Booking Service          Inventory Service        Payment Service
    |                      |                         |                        |
    |--- search (cached) ->|                         |                        |
    |<-- results ----------|                         |                        |
    |                      |                         |                        |
    |--- select room ----->|                         |                        |
    |   POST /holds        |                         |                        |
    |   Idempotency-Key:X   |--- reserveRoom() ------>|                        |
    |                      |   (atomic decrement,    |                        |
    |                      |    all nights, §4.2)    |                        |
    |                      |<-- hold {id, expires} ---|                        |
    |<-- hold_id, expires --|                         |                        |
    |   (countdown shown    |                         |                        |
    |    to user)           |                         |                        |
    |                      |                         |                        |
    |--- enter payment ---->|                         |                        |
    |   POST /bookings      |                         |                        |
    |   {hold_id,           |                         |                        |
    |    payment_method}    |--- charge() -------------------------------------->|
    |                      |   (Idempotency-Key,     |                        |  (PSP processes
    |                      |    cross-ref            |                        |   charge --
    |                      |    design_payment_      |                        |   cross-ref
    |                      |    system.md)            |                        |   design_payment_
    |                      |                         |                        |   system.md §4)
    |                      |<-- charge succeeded ------------------------------|
    |                      |                         |                        |
    |                      |--- confirmHold() ------->|                        |
    |                      |   (flip hold ->          |                        |
    |                      |    CONFIRMED, no new     |                        |
    |                      |    decrement, §4.2)      |                        |
    |                      |<-- booking confirmed ----|                        |
    |<-- booking confirmed -|                         |                        |
    |                      |                         |                        |
    |     ===== ALTERNATIVE PATH: payment fails or hold expires =====          |
    |                      |                         |                        |
    |                      |--- charge() -------------------------------------->|
    |                      |<-- charge DECLINED -------------------------------|
    |                      |--- releaseHold() ------->|                        |
    |                      |   (increment available_  |                        |
    |                      |    rooms back, §4.2)     |                        |
    |<-- booking failed ----|                         |                        |
    |                      |                         |                        |
    |     (OR: user never completes payment within 12 min)                    |
    |                      |                         |                        |
    |                      |     Hold-Expiry Worker --|                        |
    |                      |     scans holds WHERE    |                        |
    |                      |     status='ACTIVE' AND  |                        |
    |                      |     expires_at < now()   |                        |
    |                      |     -> releaseHold()     |                        |
    |                      |     (inventory returned  |                        |
    |                      |      to the pool)        |                        |
```

The saga's steps map onto the outbox/saga vocabulary from [`../distributed_transactions/README.md`](../distributed_transactions/README.md) directly:

- **Step 1 (Try / Reserve)**: `reserveRoom()` (§4.2) — the inventory decrement and hold creation happen in one local transaction. No money has moved; the *intent* is durably recorded with a built-in expiry.
- **Step 2 (external call)**: the Payment Service charge (cross-ref [`./design_payment_system.md`](./design_payment_system.md) §4.1-§4.3 for the idempotency-key and saga details of *that* step — this design does not re-derive them). This is the step that can fail for reasons outside the Booking Service's control and is correctly modeled as living *outside* any database transaction.
- **Step 3 (Confirm)**: `confirmHold()` — flips the hold's status; **does not re-decrement inventory**, because the decrement already happened in Step 1. This mirrors [`./design_payment_system.md`](./design_payment_system.md) §4.3's observation that "ledger writes happen only after the PSP confirms success" — here, "inventory commitment becomes permanent only after payment confirms success," even though the *decrement itself* happened earlier, at hold time.
- **Compensation (Step 2 fails)**: `releaseHold()` — the cheap compensation case, because the decrement is trivially reversible (increment back) and no payment was ever completed, so there's nothing on the Payment Service side to compensate either.
- **Compensation (TTL expiry, no Step 2/3 ever happens)**: the **Hold-Expiry Worker** is the saga's timeout-driven compensation path — a background job (typically a scheduled query against `idx_holds_expiry`, or a delayed-queue/TTL-index mechanism) that finds `ACTIVE` holds past `expires_at` and calls `releaseHold()` on each. This is the mechanism that makes "user closed the tab during checkout" a self-healing condition rather than a permanent inventory leak.

### 4.4 Search and Caching

Search is the read-heavy, ~100:1 side of the system (§2), and its caching strategy is shaped by one fact that distinguishes it from, say, a CDN-cacheable image: **the data being cached changes for reasons the cache cannot observe directly** — a booking happening on a *different* server, seconds ago, makes a cached "2 rooms available" result wrong *right now*, with no natural "this URL changed" signal the way a versioned asset URL provides (cross-ref [`./design_google_maps.md`](./design_google_maps.md) §4.2's versioned-tile-URL pattern, which doesn't apply here because availability isn't keyed by a version number — it's keyed by a number that decreases continuously).

The mitigation is **short TTLs, not invalidation**:

- **Cache-aside** (cross-ref [`../caching/README.md`](../caching/README.md), [`../../database/database_caching_patterns/README.md`](../../database/database_caching_patterns/README.md)): the Search Service checks Redis first for `(hotel_id, room_type_id, date_range) -> {available_rooms, rate_cents}`; on a miss, it reads from an Inventory Service **read replica** (never the primary that the booking path writes to) and populates the cache.
- **TTL of roughly 30-120 seconds** for availability/price data — short enough that a cached "available" result is very unlikely to still be wrong by the time the user clicks through to the hold step, but long enough to absorb the ~100:1 read amplification (§2's ~13.5M hot inventory rows, ~540MB) without hammering the read replicas at 8,000-9,000 searches/sec peak.
- **The cache is allowed to be wrong in one direction only**: a cached "available" that has since sold out is an *inconvenience* (the hold attempt in §4.2 fails with `SoldOutException`, and the user sees "sorry, this room just sold out — here are similar options"). A cached "sold out" that is actually available is a **lost booking** (the user never even tries) — but this is the safer of the two failure modes for the *business* (no overbooking risk) even though it costs a conversion. The short TTL bounds *both* directions, but the system's hold step (§4.2) is the actual correctness backstop regardless of what the cache said.
- **Why this differs from CDN-style caching**: a CDN-cached map tile (cross-ref [`./design_google_maps.md`](./design_google_maps.md) §4.2) is cacheable for a long TTL precisely because *the underlying tile doesn't change due to other users' read traffic* — only an explicit map-data update changes it, and that update can bump a version number. Hotel availability changes as a **direct, continuous side effect of the read-heavy system's own success** (every booking is, from the cache's perspective, an uncoordinated write against data it's holding a stale copy of) — there's no "version bump" moment to key off of, only the passage of time. This is the fundamental reason availability search uses short-TTL cache-aside rather than long-TTL versioned caching.

War Story 2 (§9) covers what happens when this TTL discipline is violated during a flash sale.

### 4.5 Sharding

Inventory rows (§4.1) are sharded **by `hotel_id`** (commonly with a secondary grouping by region for locality), because:

- **Every read and write that matters is hotel-scoped**: a search for "Paris hotels" fans out to many hotels but each hotel's availability lookup is independent; a booking's atomic multi-night decrement (§4.2) is entirely within one `(hotel_id, room_type_id)` — never spans hotels. Sharding by `hotel_id` means the operation that most needs strong consistency (the hold decrement) is **always single-shard**, never a cross-shard transaction.
- **Cross-shard date-range searches**: a single user's search ("hotels in Paris, these dates") touches many hotels, which after sharding by `hotel_id` means **many shards** — but each shard-local query (`give me availability for hotel X, room types Y, dates D1-D3`) is independent and can be issued in parallel (a classic **scatter-gather**), with the Search Service merging results. This is the same scatter-gather shape as a federated search across partitions, and it's tractable specifically *because* the per-shard query is cheap and the results are merged by simple ranking (price, rating, distance) rather than requiring any cross-shard join.
- **Region-aligned shard placement**: because hotels are geographically anchored and most searches are geographically scoped (a user searching "Tokyo" essentially never needs a shard containing only European hotels), shards can be placed in or near the region whose hotels they hold — reducing cross-region network hops for the common case, similar in spirit to [`./design_google_maps.md`](./design_google_maps.md) §10's regional-tiering of its routing fleet. Cross-ref [`../../database/sharding_and_partitioning/README.md`](../../database/sharding_and_partitioning/README.md) for the general sharding mechanics (hash vs. range partitioning, resharding cost) that apply here with `hotel_id` as the shard key.
- **Hotspot consideration**: a small number of extremely large or extremely popular properties (a 3,000-room Las Vegas resort, or a hotel that goes viral) could in principle dominate a shard's write load during a flash sale — the mitigation is the same as any hash-based sharding scheme's hotspot mitigation (cross-ref [`../consistent_hashing/README.md`](../consistent_hashing/README.md)): such properties can be given dedicated shards rather than sharing a shard sized for an average 80-room property.

### 4.6 Rate Plans, Restrictions, and the Cancellation Policy

So far §4.1's inventory row has carried a single `rate_cents` per `(hotel_id, room_type_id, stay_date)` — adequate for a "one price per room type per night" model, but real hotel pricing is layered, and the layering matters for both search ranking and the booking saga.

**Rate plans as a view over the base allotment**: a hotel typically sells the *same physical inventory* (the same pool of, say, 40 "Deluxe King" rooms) under multiple **rate plans** — "Standard Flexible" (cancellable up to 24 hours before arrival, full price), "Non-Refundable" (10-15% cheaper, no cancellation), "Member Rate" (requires loyalty-program login, slightly cheaper), "Breakfast Included" (slightly more expensive, bundles a fixed-cost add-on). Modeling each rate plan as its *own* inventory row with its *own* `available_rooms` would be wrong — booking a "Non-Refundable" room and a "Standard Flexible" room both consume from the **same physical pool**, so a hotel with 40 rooms and two rate plans does not have 80 sellable room-nights.

The resolution is a two-table split:

```sql
-- The base allotment -- exactly §4.1's inventory table, unchanged.
-- total_rooms / available_rooms reflect the PHYSICAL pool.
CREATE TABLE inventory ( ... );  -- as in §4.1

-- Rate plans reference the base allotment; they do NOT have their own
-- available_rooms. A rate plan adds a price multiplier/delta and a set
-- of RESTRICTIONS on top of the shared pool.
CREATE TABLE rate_plans (
    rate_plan_id     BIGINT      PRIMARY KEY,
    hotel_id         BIGINT      NOT NULL,
    room_type_id     BIGINT      NOT NULL,
    name             VARCHAR(50) NOT NULL,  -- 'Standard Flexible', 'Non-Refundable', ...
    cancellation_policy VARCHAR(30) NOT NULL, -- 'FREE_UNTIL_24H', 'NON_REFUNDABLE', ...
    refundable       BOOLEAN     NOT NULL
);

CREATE TABLE rate_plan_overrides (
    rate_plan_id  BIGINT REFERENCES rate_plans(rate_plan_id),
    stay_date     DATE   NOT NULL,
    rate_cents    BIGINT NOT NULL,        -- this rate plan's price for this date
    min_stay_nights INT  DEFAULT 1,       -- e.g., "2-night minimum on weekends"
    closed_to_arrival BOOLEAN DEFAULT FALSE, -- can't CHECK IN on this date (can stay through it)
    PRIMARY KEY (rate_plan_id, stay_date)
);
```

A search result's "price" for a given `(hotel, room_type, dates)` is therefore computed by joining the **availability** from `inventory` (§4.1, "is there a room at all") with the **cheapest applicable** `rate_plan_overrides` row that satisfies the requested dates' restrictions (`min_stay_nights`, `closed_to_arrival`). Crucially, **the hold step (§4.2) still decrements `inventory.available_rooms`**, regardless of which rate plan the guest chose — the rate plan only determined the *price* and the *cancellation terms*, not which physical pool was decremented. This is why §4.2's `reserveRoom` signature takes `(hotelId, roomTypeId, ...)` rather than `(rate_plan_id, ...)`: the rate plan is recorded on the `holds`/`bookings` row (so the correct price and cancellation policy are honored at confirmation and at cancellation time, per §11's "rate locked at hold time" discussion) but is not part of the atomic decrement's identity.

**Restrictions and search filtering**: `min_stay_nights` and `closed_to_arrival` are evaluated **during search**, not at hold time — a search for a 1-night stay starting on a date with `min_stay_nights = 2` for a given rate plan simply excludes that rate plan from the results (it may still surface a *different* rate plan on the same room type that has no such restriction, or omit the room type from results entirely if no rate plan qualifies). This keeps restriction logic out of the hold/booking path entirely — by the time a hold is requested, the rate plan has already been validated as eligible for the requested dates, so `reserveRoom` (§4.2) only needs to worry about the atomic-decrement correctness problem, not restriction validation.

**Cancellation as the inverse of `confirmHold`**: canceling a confirmed booking — subject to the rate plan's `cancellation_policy` — runs the inverse of §4.2's `confirmHold`: a new operation (call it `cancelBooking`) marks the `bookings` row `CANCELED` and increments `inventory.available_rooms` back for every night of the stay, exactly like `releaseHold`. Whether the guest receives a refund depends on the rate plan's `cancellation_policy` and the cancellation's timing relative to check-in — that determination is a Payment Service concern (a refund, cross-ref [`./design_payment_system.md`](./design_payment_system.md) §11's partial-refund discussion), entirely separate from the inventory-side increment. The two operations (inventory increment, refund decision) are independent and can both be triggered from the same `cancelBooking` call without being coupled into one transaction — if the refund call fails, the inventory increment should still proceed (the room genuinely becomes available again regardless of the refund's outcome), following the same outbox-driven decoupling as [`./design_payment_system.md`](./design_payment_system.md) §4.4.

### 4.7 Confirmation, Audit Trail, and Downstream Notifications

The moment `confirmHold()` (§4.2) commits, two things need to happen that are **not** part of the inventory-correctness story but matter operationally: the guest needs a confirmation (email/push/SMS with a confirmation number), and the hotel's own systems (PMS, front-desk software) need to learn about the new reservation so housekeeping and front-desk staff see it.

This is the **outbox pattern** again, applied at the booking layer exactly as [`./design_payment_system.md`](./design_payment_system.md) §4.4 applies it at the payment layer — `confirmHold()`'s transaction writes a `bookings` row *and* an `outbox` row (`event_type = 'BookingConfirmed'`) in the same local transaction:

```sql
-- Same local transaction as the holds.status = 'CONFIRMED' UPDATE and the
-- bookings INSERT in confirmHold() (§4.2).
INSERT INTO outbox (aggregate_type, aggregate_id, event_type, payload)
VALUES ('Booking', :booking_id, 'BookingConfirmed', :payload_json);
```

An outbox relay (identical in shape to [`./design_payment_system.md`](./design_payment_system.md) §4.4's `OutboxRelay`) publishes this event to a topic consumed by:

- **Notification Service**: sends the guest-facing confirmation email/push/SMS — at-least-once delivery means a guest could occasionally receive a duplicate confirmation email, which is a minor annoyance, not a correctness issue (the same "downstream consumers must be idempotent on `(aggregate_id, event_type)`" principle from [`./design_payment_system.md`](./design_payment_system.md) §4.4 applies — the Notification Service dedupes on `booking_id` before sending).
- **PMS Sync Service**: pushes the new reservation into the hotel's PMS (or the channel-manager intermediary, §6) so front-desk and housekeeping systems reflect it — this is the *reverse* direction of the inventory-update flow in §3's "out-of-band" box, and is similarly tolerant of a few seconds to low-minutes of propagation delay (a guest checking in 5 minutes after booking online is a vanishingly rare edge case, and most PMS integrations have an explicit "check PMS for any reservations made in the last N minutes that haven't synced yet" reconciliation step for exactly this reason).
- **Audit/Analytics pipeline**: every `BookingConfirmed` (and `BookingCanceled`, `HoldReleased`, `HoldExpired`) event feeds an append-only audit log — this is the data source for the §8 accounting-invariant checks and for finance/ops answering "why does our occupancy report show 41 rooms sold when we have 40" (§1's auditability NFR). Because the outbox already captures every state transition as an event, the audit trail is a natural byproduct of the saga (§4.3) rather than a separately-maintained log that could drift from the source of truth.

The unifying point: **the inventory decrement (§4.2) is synchronous and in the critical path because it's the correctness-critical operation; everything in §4.7 is asynchronous and off the critical path because none of it can cause an overbooking if delayed by a few seconds** — the same "match update latency to blast radius" principle articulated in [`./design_google_maps.md`](./design_google_maps.md) §9's four-speed map-data pipeline, applied here to the booking confirmation's fan-out instead of a map-data diff's fan-out.

---

## 5. Design Decisions & Tradeoffs

### Hold-with-TTL vs. Atomic-Decrement-Only vs. Distributed Lock

| Dimension | Atomic decrement only (A) | Hold-with-TTL (B, this design) | Distributed lock per date-range (C) |
|---|---|---|---|
| Correctness under concurrent bookers | Yes, at the instant of decrement | Yes, plus a durable record of *why* inventory moved | Yes, while lock held |
| Abandoned-checkout recovery | None — manual cleanup only | Automatic, via expiry worker (§4.3) | Requires correct TTL tuning; easy to get wrong |
| Blocks unrelated bookings during slow payment | No | No | Yes — entire date-range locked |
| Auditability ("why is this room unavailable right now") | Poor — just a number | Good — `holds` table is the audit trail | Poor — lock state isn't typically durable/queryable |
| Operational dependencies | None beyond the primary DB | Background expiry worker | External lock service (ZooKeeper/etcd/Redis) |
| Verdict | Used *as the primitive inside* B | **Primary** | Alternative, discussed for completeness |

### Search Caching: Short-TTL Cache-Aside vs. Write-Through vs. No Cache

| Dimension | No cache (read replicas only) | Write-through cache | Short-TTL cache-aside (this design) |
|---|---|---|---|
| Read-replica load at 8-9K searches/sec peak | Very high — every search hits the DB tier directly | Moderate — cache absorbs reads, but every booking write also writes the cache | Low — cache absorbs the ~100:1 read amplification |
| Staleness window | None (always current) | None (cache updated synchronously with DB) | Bounded by TTL (30-120s) |
| Write-path coupling | None | Booking path must update cache entries for every affected (hotel, room_type, date) on every hold/release — couples the write-hot path to the cache | None — booking path is entirely decoupled from the cache |
| Failure mode if cache update is missed | N/A | Cache silently diverges from truth with **no self-correction** (worse than TTL expiry) | Self-corrects within one TTL window |
| Verdict | Doesn't scale to peak read volume | Tempting but couples a correctness-critical write path to a best-effort cache — rejected | **Primary** — bounded staleness is acceptable for search; the hold step is the real correctness gate |

### Sharding by `hotel_id` vs. by Region vs. by Date

| Dimension | Shard by `hotel_id` (this design) | Shard by region | Shard by date |
|---|---|---|---|
| Hold decrement (§4.2) locality | Always single-shard | Always single-shard (region implies a set of hotel_ids) | **Cross-shard** — a 3-night stay spanning a shard boundary requires a cross-shard atomic decrement |
| Search scatter-gather fan-out | Many shards per multi-city search; few shards per single-destination search | Few shards per single-destination search (best locality) | Every search touches every date-shard in range — high fan-out regardless of destination |
| Resharding triggers | New large/popular hotel added (rare) | A region's hotel count grows unevenly | The booking horizon rolling forward continuously (constant resharding) |
| Verdict | **Primary** — keeps the correctness-critical operation single-shard | A reasonable *placement* strategy layered on top of hotel_id sharding (§4.5), not a replacement for it | Rejected — breaks the all-or-nothing multi-night decrement (§4.1, §4.2) |

### Hold Confirmation: Synchronous Saga (This Design) vs. Fully Asynchronous Queued Booking

| Dimension | Synchronous saga (this design, §4.3) | Fully asynchronous (queue the booking, notify later) |
|---|---|---|
| User-facing latency | The user waits through hold-creation and payment, typically a few hundred ms to a couple of seconds (dominated by the PSP call, cross-ref [`./design_payment_system.md`](./design_payment_system.md) §5) | The user submits and is told "we'll confirm shortly" — lower perceived latency for the initial response |
| Confirmation certainty at response time | The user knows whether they have a room *before* leaving the booking flow | The user does not know at submission time — requires a follow-up notification (email/push) and a "pending" UI state |
| Hold TTL pressure | The hold (§4.2) only needs to outlast one synchronous round trip (payment) — 10-15 minutes is generous headroom | A queued booking holds inventory for an *unbounded* period until the async worker processes it, unless the hold itself is created synchronously and only the *notification* is async |
| User experience for "sold out" | Immediate — the user can pick a different room/hotel in the same session | Delayed — the user may have already closed the app, requiring a second engagement (email/push) just to tell them their first choice failed |
| Verdict | **Primary** — for a transactional flow like booking a specific room for specific dates, "did it work" is precisely the information the user needs before moving on, and the saga's synchronous portions (§4.3) are short enough (PSP round-trip, not days) to make this practical | Appropriate for flows where the *result* doesn't gate the user's next action (e.g., "request to book" flows common for unique/curated inventory, where host approval is inherently asynchronous regardless of the underlying inventory model) |

---

## 6. Real-World Implementations

| Company / Product | Inventory Model | Notable Characteristic |
|---|---|---|
| **Booking.com** | Rate-plan + allotment model across an enormous direct-connect and channel-manager network | Operates inventory for **2.3+ million properties** worldwide and processes on the order of **1.5+ million room-nights booked per day** at peak — at that volume, the per-(hotel, room_type, date) inventory row (§4.1) is the unit that every connectivity partner (hotel extranets, channel managers) ultimately writes to, making "one canonical inventory row, many writers" the central integration challenge. |
| **Marriott's Central Reservation System (CRS)** | Pooled inventory across Marriott's ~30 brands and 8,000+ properties post-merger | Marriott's CRS underwent a **multi-year migration** after the Starwood acquisition to unify two previously-separate reservation systems (Marriott's and Starwood's) onto one platform — a real-world illustration of the "inventory model must be a shared contract" problem: two CRSs each had their own (hotel_id, room_type, date) semantics, rate-plan structures, and loyalty-point integrations, and merging them required reconciling those models property-by-property rather than a single cutover, precisely because a hard cutover risks the exact double-booking failure mode this case study's §4.2 and §9 War Story 1 are about. |
| **Airbnb's calendar/availability service** | **Per-listing calendar**, not pooled inventory | Fundamentally different model from a hotel: each listing typically has **exactly one unit** (a single apartment, room, or house), so "availability" for a given date is a boolean (open/blocked), not a count that can go from N to N-1. This means Airbnb's core concurrency problem is **"don't let two guests book the same listing for overlapping dates"** — a single-row check rather than this design's multi-row, multi-night atomic decrement across a *pool* of interchangeable rooms. The hold-with-TTL pattern (§4.2) still applies (an instant-book or request-to-book flow holds a date range briefly), but there is no equivalent of "39 rooms left, sells out at 40 bookings" — it's binary per listing. |
| **Expedia's rate-parity and channel-manager integrations** | Multi-channel distribution against hotel PMS-managed inventory | Expedia (and OTAs generally) integrate with hotels' **Property Management Systems (PMS)** via channel managers — third-party software that synchronizes a hotel's `total_rooms`/rates/restrictions across *every* OTA the hotel sells through simultaneously. **Rate parity** clauses (common in OTA-hotel contracts) require the rate shown on Expedia to match the rate on the hotel's own site and other OTAs for the same room/date — which means a rate change pushed from the PMS must propagate to every channel's cached search results (§4.4) within a contractually-bounded window, making the short-TTL cache-invalidation discipline not just a technical nicety but a **contractual SLA** in this real-world setting. |

### Comparison: Pooled-Inventory (Hotel) vs. Per-Listing (Airbnb) Models

| Dimension | Pooled inventory (this design, Booking.com, Marriott CRS) | Per-listing calendar (Airbnb) |
|---|---|---|
| Unit of availability | `available_rooms` count per (hotel, room_type, date) | Boolean open/blocked per (listing, date) |
| Overbooking failure mode | "Sold 41 of 40 rooms" — a count exceeds its bound | "Booked the same listing twice for overlapping dates" — a double-write on a single row |
| Concurrency primitive | Atomic decrement with `WHERE available_rooms >= 1` (§4.2) | Atomic `INSERT ... WHERE NOT EXISTS (overlapping booking)` or an exclusion constraint on a date-range column |
| Hold-with-TTL applicability | Primary mechanism (§4.2) | Applicable to instant-book/request flows, but the "pool" concept doesn't exist — it's a single binary flip |
| Channel-manager / PMS integration | Central to the model (Expedia, Booking.com) | Less prominent — most Airbnb hosts manage one calendar directly through Airbnb itself |

---

## 7. Technologies & Tools

| Component | Representative Technologies | Notes |
|---|---|---|
| Inventory source of truth | Relational store (PostgreSQL/MySQL), sharded by `hotel_id` | §4.1, §4.5 — `WHERE available_rooms >= 1` atomic decrement (§4.2) relies on row-level locking and `CHECK` constraints, which favor a relational engine over a wide-column store for this table specifically |
| Holds table | Same relational store as inventory, or a fast KV store (Redis) with a secondary durable log | §4.2 — `idx_holds_expiry` supports the expiry worker's scan; a TTL-capable store (Redis with `EXPIRE`, or a delayed-queue) is a common optimization for high hold volume |
| Search cache | Redis / Memcached | §4.4 — ~540MB hot working set (§2), 30-120s TTL, cache-aside |
| Search read replicas | Read replicas of the sharded inventory store | §4.4 — search never reads from the primary that the booking path writes to |
| Hold-Expiry Worker | Scheduled job (cron-style) or TTL-indexed queue (Redis keyspace notifications, SQS delay queues) | §4.3 — must run frequently enough that expired holds release inventory within seconds-to-low-minutes of expiry, not after the next nightly batch |
| Payment | Delegated to [`./design_payment_system.md`](./design_payment_system.md) | §4.3 — this design does not re-implement PSP integration, idempotency, or the ledger |
| Geo/destination resolution | Geospatial index (cross-ref [`./design_proximity_service.md`](./design_proximity_service.md), [`./design_google_maps.md`](./design_google_maps.md) §4.1) | §3 — resolving "Paris" or a lat/lng to candidate hotel IDs |
| Channel-manager / PMS integration | Webhook/API ingestion from third-party channel managers | §6 — writes to `total_rooms`/rates flow in from outside the core system |

### Build vs. Buy Considerations

| Component | Build | Buy / Third-Party | This Design's Choice |
|---|---|---|---|
| Inventory + holds model | Custom schema and `reserveRoom`/`confirmHold`/`releaseHold` logic (§4.2) | Generic e-commerce "inventory reservation" platforms exist but rarely model multi-night, multi-room-type, date-range decrements correctly out of the box | Build — the multi-night atomic decrement (§4.1, §4.2) is sufficiently domain-specific that a generic inventory system would need heavy customization anyway |
| Payment processing | — | PSP (Stripe/Adyen), per [`./design_payment_system.md`](./design_payment_system.md) | Buy — reuse the existing Payment System design wholesale |
| Channel-manager connectivity | Custom integrations per PMS vendor | Third-party channel-manager aggregators (SiteMinder, RateGain-style) that normalize many PMS APIs into one feed | Buy for the long tail of small properties; build direct integrations only for the highest-volume hotel chains, where the integration cost is amortized over enormous inventory volume |
| Search cache | Self-managed Redis cluster | Managed Redis (ElastiCache/MemoryStore-equivalent) | Either — the cache-aside pattern (§4.4) is infrastructure-agnostic |

---

## 8. Operational Playbook

### Key Metrics

| Metric | What It Measures | Alert Threshold (Illustrative) |
|---|---|---|
| **Search p99 latency** | End-to-end search response time | Page if p99 > 500ms sustained for 5 minutes |
| **Search Cache hit rate** | Fraction of availability lookups served from Redis vs. read replicas | Warn if < 90% sustained — a drop indicates either a TTL misconfiguration or a sudden traffic-pattern shift (e.g., flash sale, §9 War Story 2) |
| **`reserveRoom` failure rate (`SoldOutException`)** | Fraction of hold attempts rejected as sold out | A *sudden spike* correlated with a *stale-looking* cache hit rate is the signature of War Story 2 — investigate cache TTL and invalidation paths |
| **Active holds count vs. expected steady state** | Health of the hold lifecycle | Alert if active holds significantly exceed the `holds_per_sec x TTL` steady-state estimate (§2, ~67K average) — could indicate the expiry worker has stalled |
| **Hold-Expiry Worker lag** | Time between a hold's `expires_at` and its actual `releaseHold()` execution | Page if lag > a few minutes — every minute of lag is inventory sitting idle that searchers are told is sold out |
| **Inventory decrement failure rate vs. confirmed-booking rate** | Ratio of `reserveRoom` successes that never reach `confirmHold` | A high abandonment rate is expected (§2 estimated ~4x), but a *sudden* increase suggests a payment-path problem (cross-ref [`./design_payment_system.md`](./design_payment_system.md) §8) rather than an inventory problem |
| **Ledger-style invariant: `SUM(total_rooms - available_rooms)` vs. `COUNT(active holds) + COUNT(confirmed bookings)` per (hotel, room_type, date)** | Inventory accounting invariant | Any mismatch is a CRITICAL — it means a decrement happened with no corresponding hold/booking record, or vice versa (analogous to [`./design_payment_system.md`](./design_payment_system.md) §8's "ledger doesn't balance to zero") |

### Runbook: Inventory Accounting Mismatch (CRITICAL)

1. **Page immediately** — `available_rooms` having drifted from `total_rooms - (active holds + confirmed bookings)` for any (hotel_id, room_type, date) means either inventory was decremented without a corresponding `holds`/`bookings` row (a silent overbooking risk) or a `holds`/`bookings` row exists with no corresponding decrement (inventory is being under-reported as available, costing bookings but not causing overbooking).
2. Identify the specific `(hotel_id, room_type_id, stay_date)` row(s) via a query analogous to the payment ledger's `GROUP BY ... HAVING SUM(...) != 0` (cross-ref [`./design_payment_system.md`](./design_payment_system.md) §8) — this query should return zero rows under normal operation.
3. Determine whether the drift is in the **overbooking-risk direction** (`available_rooms` too high relative to commitments) — if so, immediately and temporarily reduce `available_rooms` for the affected row(s) to a safe value (even to 0) to stop further bookings while investigating, since a guest showing up to no room is the worst-case outcome this entire design exists to prevent.
4. Root-cause candidates: a `releaseHold()` that ran twice for the same hold (non-idempotent expiry worker re-processing), a direct write to `inventory.available_rooms` from a hotelier/PMS sync (§6) that didn't account for active holds, or a `confirmHold()` that incorrectly performed a *second* decrement (re-read §4.2's note that confirmation must not re-decrement).
5. Correct via a reviewed, audited adjustment to `available_rooms` — not a silent script — and add a regression test reproducing the exact sequence that caused the drift.

### Runbook: Search Cache Hit-Rate Drop During a Flash Sale

1. Check whether the drop correlates with a sudden spike in `reserveRoom`/`SoldOutException` rate (§8 metrics) — if searches are increasingly returning "available" results that immediately fail at hold time, the cache TTL (§4.4) is too long relative to the current booking velocity, not too short.
2. As an immediate mitigation, **temporarily reduce the Search Cache TTL** for the affected hotels/dates (e.g., from 60s to 10-15s) — this increases read-replica load but reduces the rate of "available in search, sold out at hold" user-facing failures.
3. Confirm read replicas can absorb the increased load at the reduced TTL — if not, scale out replicas *before* further reducing TTL, since an overloaded replica tier degrades search latency (§8's p99 metric) for the entire region, not just the flash-sale hotels.
4. Once the flash sale's booking velocity normalizes, restore the standard TTL — document the incident as input to a longer-term fix (e.g., dynamic TTL based on a hotel's recent booking velocity) per War Story 2 (§9).

### Runbook: Hold-Expiry Worker Lag Spike

1. The Active-Holds-Count metric (§8) climbing well above the `holds_per_sec x TTL` steady-state estimate (§2, ~67K average, ~200-300K at peak) is the leading indicator — it means holds are accumulating faster than `releaseHold()` (§4.2) is processing expirations.
2. Check the worker fleet's health directly (§10's 2-3 partitioned instances) — a single stalled or crashed instance leaves its partition of `idx_holds_expiry` unprocessed while the others continue normally, so the metric may show a *partial* spike correlated with one `hotel_id`-hash range rather than a global one.
3. While the backlog is elevated, affected hotels' `available_rooms` undercounts true availability — searches (§4.4) will show fewer rooms than actually exist, which is the "fail safe" direction (§10's DR table: no overbooking risk) but directly suppresses bookings for those hotels. **Do not attempt to "fix" this by manually incrementing `available_rooms`** — the correct fix is processing the backlog, because a manual increment without a corresponding `holds` status change reintroduces exactly the accounting-mismatch failure mode of the runbook above.
4. Restart or rebalance the stalled worker instance(s); confirm the backlog drains and the Active-Holds-Count metric returns to its steady-state band. Once drained, spot-check a sample of the hotels affected during the outage window against the accounting invariant (§8's first runbook) to confirm no holds were double-released by both the recovering worker and a failover instance that picked up the same partition.

---

## 9. Common Pitfalls & War Stories

### War Story 1: The Last Room Sold Twice — Broken, Then Fixed

**Broken**: An early version of the booking flow implemented availability checking as a separate **read-then-write** sequence — a `SELECT available_rooms FROM inventory WHERE ...` followed, in application code, by `if (available_rooms > 0) { UPDATE inventory SET available_rooms = available_rooms - 1 WHERE ... }`. This is the textbook check-then-act race:

```
Inventory row: hotel_id=42, room_type=DELUXE, date=2026-12-24, available_rooms=1

Thread A (User 1 booking)              Thread B (User 2 booking)
  SELECT available_rooms                  SELECT available_rooms
  WHERE hotel_id=42 ... --> returns 1     WHERE hotel_id=42 ... --> returns 1
  (app code: 1 > 0, proceed)               (app code: 1 > 0, proceed)
  UPDATE inventory SET                     UPDATE inventory SET
  available_rooms = 1 - 1 = 0              available_rooms = 1 - 1 = 0
  WHERE hotel_id=42 ...                    WHERE hotel_id=42 ...

RESULT: available_rooms = 0 (correct final value), but BOTH User 1 and
User 2 received "booking confirmed" -- the application code's "1 > 0"
check for BOTH threads ran against the SAME pre-decrement value of 1,
because nothing prevented both SELECTs from completing before either
UPDATE.
```

**Impact**: For New Year's Eve at a popular hotel — the single highest-demand night of the year for that property — the last available "Deluxe King" room was sold to two different guests within the same second, both of whom received confirmation emails, both of whom had been charged (this predated the hold-with-TTL design, so payment had already been captured at booking time). One guest arrived at the front desk on December 31st to find their confirmed reservation occupied by someone else. The resolution required the hotel to comp an upgrade to a suite for one guest (at the hotel's cost, with the OTA absorbing a goodwill credit) and triggered a wider audit that found the same race pattern had produced **dozens of similar double-bookings** across high-demand dates over the preceding months — most resolved quietly via upgrades or refunds, but each one a direct cost and a guest-trust incident.

**Fixed**: Replaced the read-then-write pair with the single atomic statement from §4.2:

```sql
UPDATE inventory
SET available_rooms = available_rooms - 1, version = version + 1
WHERE hotel_id = 42 AND room_type_id = ? AND stay_date = '2026-12-24'
  AND available_rooms >= 1;
-- Thread A runs first: available_rooms (1) >= 1 -> TRUE. Row updated to 0.
--   1 row affected -- Thread A's booking proceeds.
-- Thread B runs second (after A's row lock releases):
--   available_rooms is now 0. 0 >= 1 -> FALSE. 0 rows affected.
--   Thread B's application code sees "0 rows affected" and throws
--   SoldOutException -- the booking is correctly rejected.
```

The fix costs nothing extra in the success case (it's the same number of statements) and, critically, **removes the window entirely** rather than narrowing it — there is no interleaving of two concurrent `reserveRoom` calls for the same row that can both succeed, because the database's row lock makes the second `UPDATE` wait for the first to commit (or roll back) before it can even evaluate its own `WHERE` clause against the now-updated value. This is the same `UPDATE ... WHERE balance >= ?` pattern as [`./design_payment_system.md`](./design_payment_system.md) §9 War Story 2's wallet-balance race — the lesson generalizes: **any "check a number, then change it" sequence across two statements is a race; collapse it to one statement.**

### War Story 2: Stale Search Cache Causes a Flash-Sale Failed-Booking Spike — Broken, Then Fixed

**Broken**: For a major holiday flash sale, a popular destination's hotels were heavily discounted and promoted, driving search traffic to roughly 6x normal levels. The Search Cache (§4.4) was configured with a **15-minute TTL** — a value that had been perfectly adequate under normal booking velocity (a hotel's availability for a given date might change a handful of times per hour under normal conditions, so a 15-minute-stale count was rarely *wrong* in practice).

**Impact**: Under flash-sale conditions, popular discounted rooms — especially small allotments (e.g., a hotel offering only 5 rooms at the flash-sale rate) — sold out in **under a minute** of the sale opening. But the Search Cache continued serving "available" results for up to 15 minutes after a room type had actually sold out, because nothing in the cache-aside model (§4.4) proactively invalidated entries on a booking elsewhere — the cache simply hadn't expired yet. For roughly 14 minutes, a large fraction of search results for the flash-sale's most popular hotels showed rooms as "available" that had, in reality, sold out within the sale's first 60 seconds. Users clicked through, reached the hold step (§4.2), and immediately received `SoldOutException` — a spike in failed-booking-attempt rate that, from the user's perspective, looked like "the site is broken" (search says available, booking says sold out) rather than "you were 13 minutes too late." Support volume spiked, and the flash sale's conversion rate for its first 15 minutes — the period of peak interest — was far lower than for the (less-trafficked) remainder of the sale.

**Fixed**: Two complementary changes:
1. **Reduced the default Search Cache TTL from 15 minutes to 60 seconds** for availability/price data — a TTL chosen to be short relative to *flash-sale* booking velocity rather than *normal* booking velocity, accepting the higher read-replica load (§4.4's tradeoff table) as the cost of bounding the staleness window to something flash-sale traffic could no longer exploit.
2. **Velocity-aware dynamic TTL**: for hotels/room-types whose recent booking rate (rolling 5-minute window) exceeds a threshold — the signature of a flash sale or a sudden demand spike — the cache TTL for that specific entry is reduced further (down to 10-15 seconds), while unaffected hotels keep the standard 60-second TTL. This targets the *fix* at exactly the inventory experiencing the staleness-amplifying condition, without paying the read-replica cost of a globally short TTL during normal operation.

The deeper lesson: a cache TTL is implicitly a bet about **how fast the underlying data changes relative to the TTL** (§4.4's framing) — and "how fast" is not a constant. A TTL tuned for steady-state velocity will under-protect during exactly the high-velocity events (flash sales, major event date-ranges) where stale availability is most likely to be shown to the most users at once.

---

## 10. Capacity Planning

### Inventory Store Sizing

- Total inventory rows: **~1.1 billion** (§2: 500K hotels x 3 room types x 730-day horizon), ~40 bytes/row -> **~44GB** of raw inventory data, sharded by `hotel_id` (§4.5).
- At an average of **80 rooms/property** across 500K properties, a reasonable shard granularity is **one shard per ~5,000-10,000 hotels**, giving roughly **50-100 shards** globally — each shard holding `(1.1B / 75) ~= ~14.7M rows ~= ~590MB`, comfortably within a single relational instance's working set, with read replicas per shard for the Search path (§4.4).
- Holds table: at ~67,000 concurrently active holds at average load (§2), growing to perhaps **200,000-300,000 at peak** (flash sales), each ~150 bytes -> **30-45MB** at peak — trivial; the operational concern is the *rate* of inserts/updates (§2: ~93/sec average, ~400-500/sec peak for holds, plus the expiry worker's releases), not the storage volume.

### Search Service and Cache Fleet

- Peak search load: **~8,000-9,000 requests/sec** (§2).
- At a >90% cache hit rate target (§8) and each search fanning out to 50-200 candidate hotels (§2), a single search can generate **tens to low-hundreds of Redis lookups** (one per `(hotel_id, room_type_id, date)` combination in range, often batched via pipelining/`MGET`). Budgeting ~100 Redis ops/search at 8,500 searches/sec peak -> **~850,000 Redis ops/sec** at peak — within range for a sharded Redis cluster (commodity nodes handle 100K+ ops/sec each), implying roughly **8-10 cache shard-nodes** at peak with headroom, before replication factor.
- The <10% cache-miss traffic (~850/sec equivalent in hotel-lookups, translating to a smaller number of actual replica queries after batching) hits read replicas — sized with standard replica-per-shard provisioning, scaled by region per §4.5's locality discussion.

### Booking Service Fleet

- Peak booking-related writes: ~500-600 inventory-row decrements/sec (§2, from ~150-200 bookings/sec x ~3 nights/booking), plus a comparable volume of hold-creation and (separately) hold-release/expiry operations.
- Each `reserveRoom` call (§4.2) is a single multi-statement transaction touching 1-3 inventory rows plus one `holds` insert — at low-single-digit-millisecond transaction times typical for a small, well-indexed relational transaction, a single shard's primary can sustain **hundreds to low-thousands of such transactions/sec**, comfortably covering the ~500-600/sec peak per the *global* total, let alone per-shard after the §4.5 sharding split.
- Following this repo's HikariCP convention of a default pool size of 10 per service instance (cross-ref [`./design_payment_system.md`](./design_payment_system.md) §10): a modest fleet of **10-20 Booking Service instances**, each with a connection pool sized to its shard-routing fan-out, covers peak booking volume with headroom for retries and the idempotency-key lookup (§4.2) on every request.

### Hold-Expiry Worker Sizing

- At ~93 holds/sec average (§2) and a 12-minute TTL, holds expire (absent confirmation) at roughly the same rate they're created in steady state — the expiry worker must process on the order of **~70-90 expirations/sec average, 300-400/sec at peak** (accounting for the flash-sale hold-creation spike from §2 working through the TTL ~12 minutes later).
- Each expiration is a `releaseHold()` call (§4.2) — a small transaction (1-3 row increments + 1 status update). A single worker instance can process several hundred such transactions/sec; **2-3 worker instances** with leader-election or partition-based ownership (each owning a slice of the `idx_holds_expiry` index, e.g., partitioned by `hotel_id` hash to align with §4.5's sharding) provide both throughput headroom and fault tolerance — a single worker instance is a single point of failure for "inventory gets returned to the pool," which directly impacts the search-availability accuracy this whole design is built around.

### Summary Table

| Component | Sizing Basis | Estimated Footprint |
|---|---|---|
| Inventory store (sharded) | 1.1B rows x ~40 bytes, ~50-100 shards | ~44GB total, ~590MB/shard |
| Holds table | ~200-300K concurrent holds at peak x ~150 bytes | ~30-45MB at peak |
| Search Cache (Redis) | ~13.5M hot rows x ~40 bytes, >90% hit rate at ~850K ops/sec peak | ~540MB hot working set, ~8-10 cache shard-nodes |
| Search Service fleet | ~8-9K searches/sec peak | Sized for read-replica fan-out per shard, region-local placement |
| Booking Service fleet | ~500-600 inventory writes/sec peak | ~10-20 instances |
| Hold-Expiry Worker | ~300-400 expirations/sec peak | 2-3 partitioned worker instances |

### Cold-Start: Onboarding a New Hotel Chain

When a large hotel chain (say, 500 properties) onboards onto the platform:

1. **Bulk inventory seed**: for each property, room type, and date across the 2-year horizon (§2), seed `inventory` rows with the chain's provided `total_rooms` and initial rates — `500 properties x 3 room types x 730 days` ~= **~1.1M rows**, a bulk-insert operation, not a per-row API call.
2. **Shard assignment**: each new property is assigned to a shard per §4.5's `hotel_id`-based scheme — if the chain includes a small number of very large flagship properties, those may warrant dedicated shards (§4.5's hotspot mitigation) decided at onboarding time rather than discovered reactively after a launch-driven traffic spike.
3. **Channel-manager/PMS connectivity** (§6): the chain's existing PMS or channel-manager integration is pointed at the platform's inventory-update API — initial sync establishes the baseline `total_rooms`/rates, and ongoing sync keeps them current. A **rate-parity validation pass** (§6) compares the newly-synced rates against the chain's own booking channels to catch integration mapping errors (e.g., a room-type mapping mismatch that under- or over-reports `total_rooms`) before the inventory is exposed to live search traffic.
4. **Search-cache warm-up**: rather than letting the new chain's 1.1M inventory rows populate the Search Cache (§4.4) purely reactively (cold-cache misses for the first searches after launch), a pre-warm job proactively populates cache entries for the chain's properties across the "hot" 90-day window (§2's ~13.5M-row hot working set) ahead of the chain's public launch announcement — avoiding a thundering-herd of cache misses against read replicas at launch.

### Disaster Recovery: Inventory Shard Failure

Search and booking degrade very differently under a shard failure, and the difference is the same "read path is forgiving, write path is not" theme that runs through this entire design.

| Failure | Detection | Mitigation | Degraded Behavior |
|---|---|---|---|
| A single inventory shard's primary fails | Database health check / replication-lag alert | Promote a synchronous standby (cross-ref [`../../database/consistency_models_and_consensus/README.md`](../../database/consistency_models_and_consensus/README.md) for the promotion mechanics) | Holds/bookings for the affected hotels (§4.5) are unavailable for the promotion window (typically seconds); other shards are entirely unaffected because §4.5's design keeps every hold single-shard |
| A shard's read replicas fail, primary healthy | Search Service replica health checks | Search Service routes around the failed replica to remaining replicas, or falls back to the Search Cache (§4.4) with a longer effective TTL for that shard's hotels | Search results for the affected shard's hotels become slightly more stale; booking is entirely unaffected (it never reads from replicas) |
| Search Cache (Redis) cluster down | Cache client connection errors | Search Service falls back to read replicas directly (cache-aside's natural degradation, cross-ref [`../caching/README.md`](../caching/README.md)) | Read-replica load spikes to ~100% of search traffic for the affected region (§2's full ~8-9K/sec, not the <10% miss rate) — replicas must be provisioned with this worst case in mind, not just the steady-state <10% miss load |
| Hold-Expiry Worker fleet down | Active-holds-count metric (§8) climbing without bound | Failover to standby worker instances (§10's 2-3 partitioned instances already provide this) | Inventory that *should* have been released stays unavailable in search until the worker fleet recovers — a "fail safe" direction (no overbooking risk), but a lost-bookings risk that grows with outage duration |
| Entire region's inventory shards unreachable (regional outage) | Circuit breaker trips for the region (cross-ref [`../resilience_patterns/README.md`](../resilience_patterns/README.md)) | Search for that region's hotels returns "temporarily unavailable" rather than stale/incorrect results; booking attempts for that region's hotels are rejected outright (never allowed to proceed against a guess) | Search/booking for *other* regions' hotels is completely unaffected — §4.5's region-aligned shard placement means a regional outage has a regional, not global, blast radius |

The key design point for an interview: **the booking path fails closed, the search path fails open (to a more-stale cache or a "temporarily unavailable" message), and neither path's failure mode is "guess and hope."** A search result that says "temporarily unavailable" costs a few seconds of user frustration; a booking that proceeds against a shard the system can't currently confirm is consistent risks exactly the double-sell this design exists to prevent (§4.2, War Story 1) — so booking simply refuses to proceed rather than degrade.

---

## 11. Interview Discussion Points

**Q: Two users click "book" on the last available room at the exact same moment — walk through what happens.**
A: Both requests reach the Booking Service, which calls `reserveRoom()` (§4.2) on the Inventory Service. Both attempt `UPDATE inventory SET available_rooms = available_rooms - 1 ... WHERE available_rooms >= 1` for the same `(hotel_id, room_type_id, stay_date)` row. The database serializes these two `UPDATE`s via row-level locking: the first to acquire the lock sees `available_rooms = 1 >= 1`, succeeds, and the row becomes 0. The second `UPDATE` then evaluates against the *new* value, `0 >= 1` is false, 0 rows are affected, and that request throws `SoldOutException`. One user gets a hold (and proceeds to payment); the other sees "sorry, this room just sold out." This is the fix from War Story 1 (§9) — the critical property is that "check" and "act" are the *same statement*, so there is no window in which both requests can observe `available_rooms = 1`.

**Q: Why use a "hold with TTL" instead of just decrementing inventory directly at booking time?**
A: A direct decrement at booking time has no representation for "the user is mid-checkout but hasn't paid yet" — if you decrement immediately and the user abandons checkout (closes the tab, payment fails, session times out), the room is permanently unavailable to other searchers with no automatic recovery path (§4.2 Approach A's limitation). The hold (§4.2 Approach B) performs the *same* atomic decrement immediately — so it's just as protective against overbooking — but pairs it with a durable `holds` row carrying an `expires_at` (typically 10-15 minutes). If payment completes, `confirmHold()` flips the hold to `CONFIRMED` with no additional inventory change. If payment fails or the TTL elapses, `releaseHold()` (called either by the failed-payment path or the background Hold-Expiry Worker) increments `available_rooms` back — making "abandoned checkout" a self-healing condition rather than a slow inventory leak.

**Q: A user searches and sees "2 rooms available," but when they try to book, it says sold out. Is this a bug?**
A: Not necessarily — it's the expected behavior of the cache-staleness tradeoff in §4.4. Search results are served from a short-TTL (30-120s normally, as low as 10-15s under high-velocity conditions per War Story 2) cache-aside layer that can be slightly behind the source of truth; the hold step (§4.2) always re-checks against the real, current `available_rooms` with an atomic decrement. If the cached "2 available" was accurate when cached but both rooms sold in the interim, the hold correctly rejects with `SoldOutException`. It *becomes* a bug — and the subject of War Story 2 — if this happens at a rate far higher than the TTL would predict, which signals the TTL is miscalibrated for current booking velocity (e.g., a flash sale).

**Q: How would you detect and fix the read-then-write race from War Story 1 if you inherited a codebase that had it?**
A: Look for any code path where availability is checked via a `SELECT` and the decrement happens in a *separate* statement — especially if there's any gap between them (an `if` check in application code, a call to another service, a cache lookup used as the check). The fix is always the same shape: collapse the check and the act into one atomic, conditional `UPDATE ... WHERE <condition>` and branch on **rows-affected**, not on a previously-read value. To detect it in production *before* a customer-facing incident, a useful technique is a reconciliation job (§8) that periodically verifies `available_rooms = total_rooms - (active holds + confirmed bookings)` for a sample of rows — any drift, even without a reported double-booking yet, indicates the invariant can be violated and is worth investigating proactively.

**Q: How does the booking flow integrate with the payment system, and what happens if the payment step times out?**
A: The Booking Service calls the Payment Service (cross-ref [`./design_payment_system.md`](./design_payment_system.md)) with the held reservation's amount and an `Idempotency-Key`, exactly as that design's §4.1 describes for any charge. If the call times out, the Booking Service does **not** assume failure and immediately release the hold — because the payment might have actually succeeded on the PSP's side with the response lost in transit (the canonical scenario from [`./design_payment_system.md`](./design_payment_system.md) §9 War Story 1). Instead, the Booking Service retries the *same* idempotency key — Stripe/Adyen-style idempotency guarantees the retry either returns the original success (and `confirmHold()` proceeds) or the original failure (and `releaseHold()` proceeds) without double-charging. The hold's own TTL (§4.2) acts as the outer bound: if retries and ambiguity resolution can't complete before `expires_at`, the Hold-Expiry Worker releases the inventory regardless, and a payment that turns out to have succeeded *after* that point is reconciled and refunded — the same "flag, don't silently auto-resolve" philosophy as [`./design_payment_system.md`](./design_payment_system.md) §4.5's reconciliation job.

**Q: Why is search traffic ~100x booking traffic, and how does that ratio shape the architecture?**
A: Users browse, compare, and reconsider far more than they commit — a single booking is typically preceded by multiple searches across dates, destinations, and price points, and most searches never convert (§2's estimates: ~2,300 searches/sec average vs. ~23 bookings/sec average). This ratio is the direct justification for architecturally separating the two paths (§3): search is served by a horizontally-scalable, cache-fronted, eventually-consistent read path (§4.4) that can absorb huge fan-out cheaply, while booking is a narrow, strongly-consistent write path (§4.2) that doesn't need to scale nearly as far in *absolute* terms but must be correct on every single request. Trying to serve both from the same consistency model would either make search prohibitively expensive (every search hitting the strongly-consistent source of truth) or make booking unsafe (treating cached data as authoritative for a decrement).

**Q: How is the inventory table sharded, and why does a 3-night booking not become a cross-shard transaction?**
A: Inventory rows are sharded by `hotel_id` (§4.5) — every row for a given hotel, across all room types and all dates in the 2-year horizon, lives on the same shard. A multi-night booking's atomic decrement (§4.2) touches multiple *rows* (one per night) but always within **one hotel**, hence one shard — so the all-or-nothing transaction from §4.1/§4.2 is always single-shard, never requiring a distributed/cross-shard transaction. The cost is paid on the *search* side instead: a single destination search fans out across many hotels, which after `hotel_id`-sharding means many shards — but that fan-out is a parallel scatter-gather of independent read-only queries (§4.5), which is a far easier problem than a cross-shard atomic write would be.

**Q: What's the difference between this design's inventory model and Airbnb's, and why does it differ?**
A: This design models **pooled inventory** — a count (`available_rooms`) per (hotel, room_type, date) that decreases as interchangeable rooms of the same type are booked (§4.1, §6). Airbnb's model is **per-listing**: each listing is typically a single, unique unit, so "availability" for a date is a boolean, not a count — the concurrency problem becomes "don't let two bookings claim overlapping dates on the same listing" (an exclusion constraint or a single-row atomic check) rather than "don't let `available_rooms` go negative across a pool." The difference reflects the underlying business: a hotel sells fungible inventory (any of 40 "Deluxe King" rooms satisfies a booking for that room type), while a short-term-rental listing is its own unique unit with no fungible siblings (§6's comparison table).

**Q: How would you handle a hotel's allotment changing mid-flight — e.g., the hotel reduces `total_rooms` from 40 to 35 because 5 rooms are taken offline for renovation?**
A: A reduction in `total_rooms` must never *directly* set `available_rooms` to `total_rooms - (existing commitments)` if that would make `available_rooms` negative relative to already-confirmed bookings — the `CHECK (available_rooms <= total_rooms)` and `CHECK (available_rooms >= 0)` constraints from §4.1 exist precisely to make this an explicit, visible conflict rather than a silent inconsistency. In practice, the inventory-update API (§6, channel-manager/PMS sync) computes the new `available_rooms` as `new_total_rooms - (total_rooms - old_available_rooms)` (i.e., preserve the *committed* count, reduce only the *uncommitted* slack) — and if that would go negative (the hotel is trying to remove more rooms than are currently uncommitted), the update is rejected or flagged for manual resolution (the hotel has effectively overbooked itself relative to its new, smaller allotment, which is a business decision — comp a guest, find alternate accommodation — not something the inventory system should silently paper over).

**Q: A hotelier complains that their rooms aren't showing up in search even though they have availability — how do you debug this?**
A: Start at the cache layer (§4.4) — confirm whether the Search Cache has a stale "sold out" or "no availability" entry for the affected (hotel, room_type, date) that hasn't yet hit its TTL (30-120s normally). If the cache is correct, check the inventory row directly: is `available_rooms` actually > 0, or did a recent channel-manager/PMS sync (§6) push an incorrect `total_rooms`/`available_rooms` value — a common root cause being a room-type mapping mismatch between the hotel's PMS room-type codes and the platform's `room_type_id`, causing updates intended for "Deluxe King" to land on a different room type's row. If both the cache and the underlying row look correct, check whether the destination-resolution step (§3) is including this hotel in the candidate set at all for the searched destination/dates — a geo-index or filter-matching issue (cross-ref [`./design_proximity_service.md`](./design_proximity_service.md)) can exclude a hotel from results even when its inventory is perfectly fine.

**Q: How would you support a hotel that wants to deliberately overbook by a small margin to offset no-shows — a common hotel-industry practice?**
A: Add an explicit, hotel-configured `overbooking_buffer` to the room-type's configuration, and adjust the `reserveRoom` guard from `available_rooms >= 1` to effectively allow `available_rooms >= 1 - overbooking_buffer` (equivalently, initialize `available_rooms = total_rooms + overbooking_buffer` and keep the `available_rooms >= 1` check unchanged, adjusting the `CHECK (available_rooms <= total_rooms)` constraint to `<= total_rooms + overbooking_buffer`). The key point for an interview: this must be an **explicit, visible, hotel-opted-in number** surfaced in the hotel's configuration and ideally in their operational dashboards — not a default behavior or a "fudge factor" baked into the system's logic, because the whole point of §1's NFR is that overbooking beyond a *configured tolerance* never happens. A hotel that sets `overbooking_buffer = 2` is making an informed business tradeoff (occasional walk-a-guest-to-a-sister-property costs vs. revenue from selling 2 extra room-nights that usually go unused due to no-shows) — the system's job is to make that number explicit and bounded, not to overbook by accident.

**Q: What happens to an active hold if the underlying inventory row is being concurrently modified by a hotelier's rate/allotment update?**
A: The hold's decrement (§4.2) already happened and is reflected in `available_rooms` — a subsequent hotelier update to `total_rooms` or rates operates on the *current* `available_rooms` value, which already accounts for the hold. The interesting case is a rate change: if a hotelier changes the per-night rate *after* a hold was created at the old rate, the hold/booking should honor the **rate at the time the hold was created** (stored on the `holds` row itself, not re-derived from `inventory.rate_cents` at confirmation time) — otherwise a guest could be quoted one price during checkout and charged a different one at confirmation, which is both a poor user experience and, depending on jurisdiction, a potential consumer-protection issue. This is analogous to a price being "locked in" at cart-checkout time in any e-commerce system.

**Q: Why is the hold's TTL set to roughly 10-15 minutes — what determines that number?**
A: It's a tradeoff between two costs (§2, §4.2): too short, and legitimate users who take a normal amount of time to enter payment details (especially with 3-D Secure / bank-app verification steps, cross-ref [`./design_payment_system.md`](./design_payment_system.md) §5's note on PSP round-trip latency including 3DS challenges) have their hold expire mid-checkout, causing a failed booking for no fault of their own. Too long, and inventory sits artificially unavailable for users who abandoned checkout immediately — directly reducing the pool of bookable rooms shown in search during high-demand periods. 10-15 minutes is calibrated to comfortably exceed the typical payment-completion time (including 3DS) while bounding the "ghost unavailability" window from abandoned checkouts to something search-cache TTLs (§4.4, 30-120s) are already operating within the same order of magnitude as — i.e., the hold TTL and the cache TTL are both, in different ways, expressions of "how long can this system tolerate showing a number that might already be wrong."

**Q: How would this design change for a system with only a handful of huge hotel chains versus hundreds of thousands of small independent properties?**
A: The core inventory model, hold mechanism, and saga (§4.1-§4.3) don't change — but the **sharding and integration strategy** (§4.5, §6) would shift significantly. A handful of huge chains (Marriott-scale, §6) justifies **dedicated shards per chain** (or even per flagship property) and **direct, custom PMS integrations** (the "build" side of §7's build-vs-buy table) because the integration cost is amortized over enormous booking volume. Hundreds of thousands of small independents instead favor **shared shards** (§4.5's default ~5,000-10,000 hotels/shard) and **third-party channel-manager aggregators** (the "buy" side of §7) that normalize many small PMS integrations behind one feed — because building and maintaining direct integrations for each small property individually would not be cost-effective at that long-tail scale.

**Q: A hotel offers both a "Standard Flexible" rate and a "Non-Refundable" rate on the same room type — how does the inventory model avoid double-counting that room?**
A: Both rate plans reference the **same** `inventory` row for `(hotel_id, room_type_id, stay_date)` — they do not each carry their own `available_rooms` (§4.6). A `rate_plans` table holds the pricing and cancellation-policy differences (refundable vs. non-refundable, price delta), and `rate_plan_overrides` carries per-date price and restriction (`min_stay_nights`, `closed_to_arrival`) overrides — but the *physical pool* of rooms is singular. When `reserveRoom()` (§4.2) decrements `available_rooms` for a hold, it doesn't matter which rate plan the guest selected — the decrement is against the shared pool, and the chosen rate plan is recorded only on the `holds`/`bookings` row for pricing and cancellation purposes. This is precisely why a hotel with 40 physical "Deluxe King" rooms cannot simultaneously sell 40 under "Standard Flexible" *and* 40 under "Non-Refundable" — selling 25 under one rate plan and 15 under the other still draws from the same 40-room pool, and the 41st booking attempt (regardless of rate plan) correctly fails with `SoldOutException`.

**Q: A search for "hotels in the San Francisco Bay Area" needs to query hotels that, after sharding by `hotel_id` (§4.5), are spread across dozens of shards — how do you keep this fast?**
A: Three things work together. First, **region-aligned shard placement** (§4.5) means the Bay Area's hotels are concentrated on a relatively small number of shards local to that region, not uniformly spread across all ~50-100 global shards — the scatter-gather fan-out for a regionally-scoped search is bounded by "how many shards does this region's hotel population span," not by the total shard count. Second, **the Search Cache (§4.4) absorbs the overwhelming majority of these per-shard lookups** at >90% hit rate — the scatter-gather to read replicas only happens on the <10% miss path, and even then it's a parallel fan-out (each shard query is independent and cheap) rather than a sequential one. Third, the destination-resolution step (§3, cross-ref [`./design_proximity_service.md`](./design_proximity_service.md)) pre-filters the candidate hotel set *before* the inventory fan-out — a geo-index narrows "San Francisco Bay Area" to a bounded set of `hotel_id`s (typically the 50-200/§2 candidate range) rather than scanning every hotel in every shard, so the fan-out width is governed by the destination's hotel density, not the global inventory size.

---

## Cross-References

- **Payment processing for the confirm step of the booking saga (§4.3, §11)** -> [`./design_payment_system.md`](./design_payment_system.md)
- **Saga pattern, hold-then-confirm/compensate semantics (§4.3)** -> [`../distributed_transactions/README.md`](../distributed_transactions/README.md)
- **Inventory sharding by `hotel_id`/region and cross-shard scatter-gather search (§4.5, §5)** -> [`../../database/sharding_and_partitioning/README.md`](../../database/sharding_and_partitioning/README.md)
- **Search availability cache-aside, TTL design, and the staleness tradeoff (§4.4, §9 War Story 2)** -> [`../../database/database_caching_patterns/README.md`](../../database/database_caching_patterns/README.md)
- **General caching strategies (cache-aside vs. write-through) underlying §4.4** -> [`../caching/README.md`](../caching/README.md)
- **Strong consistency requirement for the inventory decrement, vs. eventual consistency for search (§4.2, §4.4, Intuition)** -> [`../../database/consistency_models_and_consensus/README.md`](../../database/consistency_models_and_consensus/README.md)
- **Hotspot mitigation for very large/popular properties under hash-based sharding (§4.5)** -> [`../consistent_hashing/README.md`](../consistent_hashing/README.md)
- **Destination/geo resolution for search candidate-hotel lookup (§3, §4.5, §11)** -> [`./design_proximity_service.md`](./design_proximity_service.md)
