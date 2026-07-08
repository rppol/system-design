# Online Booking System — Builder + Strategy + Observer

## Intuition

> **One-line analogy**: Online Booking is a concurrency problem dressed as a design problem — the interesting challenge isn't the class hierarchy, it's preventing two users from booking the same seat simultaneously at scale.

**Mental model**: The functional model is straightforward: Show → Seats → Booking → Payment. The real design challenge is the seat reservation race condition. At BookMyShow scale (1M+ bookings/day), two users can click the same seat in milliseconds. The solution is a short-lived pessimistic lock (optimistic locking with version checks, or a `reserved` flag + TTL) that holds the seat while the user completes payment. Dynamic pricing (Strategy pattern) then applies multipliers based on seat type, show time, and membership tier.

**Why it matters**: This problem combines Builder (complex Movie/Show construction), Strategy (pricing algorithms), Observer (email/SMS/loyalty notifications), and concurrency thinking — making it a comprehensive interview problem that tests both patterns and distributed systems instincts.

**Key insight**: The most common interview miss is ignoring seat lock expiry. Model the reservation as a time-bounded lock: if payment isn't completed within N minutes, the seat is released. This also requires a cleanup mechanism (scheduler or TTL).

---

## Problem Statement

Design a movie ticket booking system that handles:
- 1M+ bookings/day (BookMyShow, Fandango scale)
- Concurrent seat selection without double-booking
- Dynamic pricing based on time, membership, group size
- Multi-channel notifications (email, SMS, loyalty points)

---

## Class Diagram

```mermaid
classDiagram
    class Movie {
        -movieId String
        -title String
        -duration int
        -genre Genre
        -rating String
        -language String
        -imdbScore double
    }

    class MovieBuilder {
        <<Builder>>
        +Builder(movieId, title, duration)
        +genre(genre) Builder
        +imdbScore(score) Builder
        +build() Movie
    }
    note for MovieBuilder "Movie.Builder — required: movieId, title, duration"

    class Show {
        -showId String
        -movie Movie
        -theater String
        -screen String
        -startTime LocalDateTime
        -seats List~Seat~
        -isPeakHour boolean
    }

    class ShowBuilder {
        <<Builder>>
        +Builder(showId, movie, theater, screen, startTime)
        +withDefaultSeating(rows, seatsPerRow) Builder
        +build() Show
    }
    note for ShowBuilder "Show.Builder — nested static builder"

    class Seat {
        -seatId String
        -row int
        -number int
        -type SeatType
        -basePrice double
        -reserved boolean
    }

    class Booking {
        -bookingId String
        -userId String
        -show Show
        -seats List~Seat~
        -totalAmount double
        -pricingStrategy PricingStrategy
        -status BookingStatus
    }

    class PricingStrategy {
        <<interface>>
        +calculatePrice(seat, show) double
    }
    class RegularPricing
    class PeakHourPricing
    class MemberDiscount
    class ChildPricing
    class GroupPricing

    class BookingObserver {
        <<interface>>
        +onBookingConfirmed(booking)
        +onBookingCancelled(booking)
    }
    class EmailService
    class SMSService
    class LoyaltyPointsService

    class BookingService {
        +addShow(show)
        +searchShows(title, date)
        +selectAndBook(userId, showId, seatIds, strategy)
        +cancelBooking(bookingId)
    }

    MovieBuilder ..> Movie : builds
    ShowBuilder ..> Show : builds
    Show "*" --> "1" Movie : movie
    Show "1" *-- "*" Seat : seats
    Booking "*" --> "1" Show : show
    Booking "1" o-- "*" Seat : books
    Booking "*" --> "1" PricingStrategy : pricingStrategy
    PricingStrategy <|.. RegularPricing
    PricingStrategy <|.. PeakHourPricing
    PricingStrategy <|.. MemberDiscount
    PricingStrategy <|.. ChildPricing
    PricingStrategy <|.. GroupPricing
    BookingObserver <|.. EmailService
    BookingObserver <|.. SMSService
    BookingObserver <|.. LoyaltyPointsService
    BookingService "1" --> "*" Show : manages
    BookingService "1" --> "*" BookingObserver : notifies
    BookingService ..> PricingStrategy : uses
    BookingService ..> Booking : creates
```

Movie/Show are assembled via nested Builders (required fields enforced in the constructor, optional fields via fluent setters); `PricingStrategy` and `BookingObserver` are the two interfaces realized by five pricing algorithms and three notification channels respectively — swapping either in requires zero changes to `BookingService`.

---

## Patterns Used

### Builder — Movie and Show

Both `Movie` and `Show` have many optional fields. Builder makes construction readable and validates required fields.

```java
Movie movie = new Movie.Builder("M001", "Inception", 148) // required
    .genre(Genre.SCIFI)
    .director("Christopher Nolan")
    .imdbScore(8.8)
    .build();

Show show = new Show.Builder("S001", movie, "CityPlex", "Screen A", showTime)
    .withDefaultSeating(6, 8)  // 6 rows, 8 seats each
    .build();
```

### Strategy — Pricing

Pricing is a classic Strategy example — the algorithm for calculating price varies, but the interface is the same.

```java
// At booking time, client selects the strategy
PricingStrategy strategy = member.isPremium()
    ? new MemberDiscountPricing(20)
    : new PeakHourPricing();

service.selectAndBook(userId, showId, seatIds, strategy);
```

| Strategy | Logic |
|----------|-------|
| `RegularPricing` | Base price |
| `PeakHourPricing` | 1.5× on evenings/weekends |
| `MemberDiscountPricing` | configurable % off |
| `ChildPricing` | max $7 flat |
| `GroupPricing` | 20% off for 10+ people |

### Observer — Notifications

```mermaid
sequenceDiagram
    participant BS as BookingService
    participant Email as EmailNotificationService
    participant SMS as SMSNotificationService
    participant Loyalty as LoyaltyPointsService

    BS->>Email: onBookingConfirmed(booking)
    BS->>SMS: onBookingConfirmed(booking)
    BS->>Loyalty: onBookingConfirmed(booking)
```

Adding a new channel (push notification, WhatsApp) = add one class + register it. No changes to `BookingService`.

---

## Concurrency Challenge: Double-Booking Prevention

The hardest problem in booking systems is concurrent seat selection.

### The Race Condition:
```mermaid
sequenceDiagram
    participant UserA as User A
    participant UserB as User B
    participant DB as Seat A1 (DB)

    UserA->>DB: read seat A1 status
    DB-->>UserA: available
    UserB->>DB: read seat A1 status
    DB-->>UserB: available
    Note over UserA,UserB: same moment — both think it's free
    UserA->>DB: book seat A1
    DB-->>UserA: confirmed
    UserB->>DB: book seat A1
    DB-->>UserB: confirmed
    Note over DB: DOUBLE BOOKING — two confirmations for one seat
```

Both reads land in the same race window before either write commits, so both bookings appear to succeed — this is exactly why the fix below needs an atomic check-and-set instead of a read-then-write.

### Solutions:

**1. Optimistic Locking (recommended for most systems)**
```sql
UPDATE seats SET reserved = true, version = version + 1
WHERE seat_id = 'A1' AND reserved = false AND version = :expectedVersion
-- If 0 rows updated → someone else got it first → retry
```

This resolves the exact UserA/UserB race shown above: both transactions read the same `version`, but only the first `UPDATE` to commit advances it — the second's `WHERE version = :expectedVersion` then matches zero rows instead of silently overwriting.

```mermaid
sequenceDiagram
    participant UserA as User A
    participant UserB as User B
    participant DB as seats table

    UserA->>DB: UPDATE ... WHERE seat_id='A1' AND version=5
    DB-->>UserA: 1 row updated, version to 6
    UserB->>DB: UPDATE ... WHERE seat_id='A1' AND version=5
    DB-->>UserB: 0 rows updated, version already 6
    Note over UserB: stale version — retry with version=6
```

UserB's compare-and-set fails cleanly instead of double-booking; the retry re-reads the seat at `version = 6` and, if it is now reserved, surfaces `SeatAlreadyTakenException` to the caller.

**2. Pessimistic Locking**
```sql
SELECT * FROM seats WHERE seat_id = 'A1' FOR UPDATE;
-- Locks the row, others must wait
```

**3. Redis Distributed Lock**
```java
String key = "seat_lock:" + seatId;
boolean locked = redis.setNX(key, userId, 10, TimeUnit.SECONDS);
if (!locked) throw new SeatAlreadyTakenException();
// ... proceed with booking
```

**4. Database Transaction with Isolation Level**
```java
@Transactional(isolation = Isolation.SERIALIZABLE)
public Booking selectAndBook(...) { ... }
```

The current implementation uses in-memory synchronization suitable for single-instance; production needs option 1 or 3.

---

## Pricing Calculation Flow

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    A(["User selects seats<br/>A1 STANDARD $10, C1 PREMIUM $15"]) --> B["PricingStrategy.calculatePrice(seat, show)<br/>for each seat"]
    B --> C["PeakHourPricing applies<br/>9 PM show, isPeakHour = true"]
    C --> D["A1: $10 × 1.5 = $15.00"]
    C --> E["C1: $15 × 1.5 = $22.50"]
    D --> F(["Total = $37.50"])
    E --> F

    class A,F io
    class B,C mathOp
    class D,E train
```

Each seat's `calculatePrice` runs independently through the same `PeakHourPricing` strategy (9 PM show triggers the 1.5× multiplier), then the per-seat results sum to the booking's `totalAmount`.

---

## Database Schema

```sql
CREATE TABLE movies (
    movie_id VARCHAR PRIMARY KEY,
    title VARCHAR NOT NULL,
    duration_minutes INT,
    genre VARCHAR,
    rating VARCHAR
);

CREATE TABLE shows (
    show_id VARCHAR PRIMARY KEY,
    movie_id VARCHAR REFERENCES movies(movie_id),
    theater VARCHAR,
    screen VARCHAR,
    start_time TIMESTAMP NOT NULL,
    is_peak_hour BOOLEAN
);

CREATE TABLE seats (
    seat_id VARCHAR,
    show_id VARCHAR REFERENCES shows(show_id),
    row_label VARCHAR,
    seat_number INT,
    seat_type VARCHAR,  -- STANDARD, PREMIUM, VIP
    base_price DECIMAL,
    reserved BOOLEAN DEFAULT false,
    version INT DEFAULT 0,  -- for optimistic locking
    PRIMARY KEY (seat_id, show_id)
);

CREATE TABLE bookings (
    booking_id VARCHAR PRIMARY KEY,
    user_id VARCHAR,
    show_id VARCHAR REFERENCES shows(show_id),
    total_amount DECIMAL,
    pricing_strategy VARCHAR,
    status VARCHAR,
    booked_at TIMESTAMP
);

CREATE TABLE booking_seats (
    booking_id VARCHAR REFERENCES bookings(booking_id),
    seat_id VARCHAR,
    price_paid DECIMAL,
    PRIMARY KEY (booking_id, seat_id)
);
```

---

## Scale Estimates (BookMyShow India scale)

- 5M bookings/day → ~60 bookings/sec average, ~300/sec peak
- 10K shows/day, 200 seats/show → 2M seat records/day
- Caching: show catalog (Redis, TTL 1hr), seat availability (Redis, TTL 5min)
- Sharding by show_id for seats table

---

## Cross-Perspective: HLD Connections

**HLD View — Where Online Booking Scales to Distributed Systems**

- **Seat lock → distributed lock** — The temporary seat reservation during checkout is a distributed locking problem. At scale (BookMyShow: 1M+ bookings/day), the lock is implemented via Redis SETNX with a TTL: if payment isn't completed within 10 minutes, the lock expires and the seat is released. A cleanup job handles expired locks as backup.
- **Double-booking prevention → optimistic locking** — Two users clicking the same seat simultaneously requires either pessimistic locking (SETNX) or optimistic locking (version field in the database, reject on CAS failure). At high concurrency, optimistic locking with conflict detection and user-facing retry is more scalable.
- **Dynamic pricing → pricing microservice** — Pricing logic (peak hours, membership tier, group discount, demand surge) becomes a dedicated pricing service with its own data store at HLD scale. The Strategy pattern structures the pricing algorithms internally within the pricing service.
- **Notification fan-out → event-driven** — Post-booking notifications (email receipt, SMS confirmation, loyalty points update) are decoupled from the booking flow via a message queue. The booking service publishes `BookingConfirmed`; email, SMS, and loyalty services consume it independently — Observer at infrastructure scale.

---

## Follow-Up Extensions

1. **Waitlist**: Queue of users waiting for cancellations
2. **Dynamic pricing**: ML model adjusting prices based on real-time demand
3. **Food & beverage**: Add-ons using Decorator pattern
4. **Seat recommendation**: Suggest best available seats based on preference
5. **Multi-city rollout**: Tenant-aware sharding by city
6. **Corporate bookings**: Bulk booking with invoice generation
