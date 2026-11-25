package com.rutik.systemdesign.lld.system_design_problems;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

// =============================================================================
//  ONLINE AUCTION - Low-Level Design
//
//  Patterns used:
//    - Strategy : AuIncrementPolicy (bid ladder), AuEndPolicy (fixed end vs
//                 soft close), AuClock (real vs fake time)
//    - State    : AuAuctionState (SCHEDULED -> OPEN -> SOLD / UNSOLD / CANCELLED)
//    - Observer : AuAuctionListener (outbid, new leader, closed)
//    - Facade   : AuAuctionHouse is the one entry point that owns idempotency
//
//  Key design points:
//    - Proxy bidding: a bidder submits a MAXIMUM. The visible price is the
//      second-highest maximum plus one increment, capped at the leader's max.
//      Equal maxima: the EARLIER bid keeps the lead.
//    - One lock PER AUCTION. The check ("is this bid high enough?") and the
//      write ("record the new leader") run under it. Without it, two bids that
//      both passed the check overwrite each other: demonstrateLostUpdate().
//    - Close is a rule evaluated on every bid ("now < endTime"), not only a
//      timer. A bid that arrives after the end is rejected even if the close
//      job has not run yet. Closing is idempotent.
//    - Every bid carries a client request id. A retried request returns the
//      first result instead of placing a second bid.
//    - The accepted-bid log replays into an identical auction.
//
//  Money is a long in cents. All type names carry the Au prefix because every
//  file in this package is compiled together.
// =============================================================================

enum AuAuctionState { SCHEDULED, OPEN, SOLD, UNSOLD, CANCELLED }

final class AuMoney {
    private AuMoney() { }

    static String fmt(long cents) {
        return String.format("$%d.%02d", cents / 100, cents % 100);
    }
}

// -----------------------------------------------------------------------------
//  STRATEGY: time
// -----------------------------------------------------------------------------
interface AuClock {
    Instant now();
}

class AuFakeClock implements AuClock {
    private Instant now;

    AuFakeClock(Instant start) {
        this.now = start;
    }

    @Override
    public synchronized Instant now() {
        return now;
    }

    synchronized void set(Instant t) {
        now = t;
    }

    synchronized void advance(Duration d) {
        now = now.plus(d);
    }
}

// -----------------------------------------------------------------------------
//  STRATEGY: bid increments
// -----------------------------------------------------------------------------
interface AuIncrementPolicy {
    /** The minimum step above `priceCents` for the next valid bid. */
    long incrementAt(long priceCents);
}

/** The ladder eBay publishes on its "Automatic bidding" help page (USD). */
class AuLadderIncrements implements AuIncrementPolicy {
    private static final long[][] LADDER = {
        // {upper bound of the bracket, exclusive, in cents ; increment in cents}
        {100, 5},
        {500, 25},
        {2_500, 50},
        {10_000, 100},
        {25_000, 250},
        {50_000, 500},
        {100_000, 1_000},
        {250_000, 2_500},
        {500_000, 5_000},
        {Long.MAX_VALUE, 10_000},
    };

    @Override
    public long incrementAt(long priceCents) {
        for (long[] bracket : LADDER) {
            if (priceCents < bracket[0]) {
                return bracket[1];
            }
        }
        throw new IllegalStateException("unreachable");
    }
}

// -----------------------------------------------------------------------------
//  STRATEGY: how the end time reacts to late bids
// -----------------------------------------------------------------------------
interface AuEndPolicy {
    /** Returns the (possibly extended) end time after a bid accepted at `now`. */
    Instant afterAcceptedBid(Instant currentEnd, Instant now);

    String name();
}

class AuFixedEnd implements AuEndPolicy {
    @Override
    public Instant afterAcceptedBid(Instant currentEnd, Instant now) {
        return currentEnd;
    }

    @Override
    public String name() {
        return "fixed end";
    }
}

/** Anti-sniping: a bid inside the last `window` pushes the end to now + `extension`. */
class AuSoftClose implements AuEndPolicy {
    private final Duration window;
    private final Duration extension;

    AuSoftClose(Duration window, Duration extension) {
        this.window = window;
        this.extension = extension;
    }

    @Override
    public Instant afterAcceptedBid(Instant currentEnd, Instant now) {
        if (Duration.between(now, currentEnd).compareTo(window) < 0) {
            Instant extended = now.plus(extension);
            return extended.isAfter(currentEnd) ? extended : currentEnd;
        }
        return currentEnd;
    }

    @Override
    public String name() {
        return "soft close (" + window.toMinutes() + " min window)";
    }
}

// -----------------------------------------------------------------------------
//  RESULTS, EVENTS, OBSERVER
// -----------------------------------------------------------------------------
record AuBidResult(boolean accepted, String reason, String leader, long priceCents, Instant endTime) {
    @Override
    public String toString() {
        String head = accepted
                ? (reason == null ? "ACCEPTED" : "ACCEPTED (" + reason + ")")
                : "REJECTED (" + reason + ")";
        return head + "  leader=" + leader + "  price=" + AuMoney.fmt(priceCents);
    }
}

/** One accepted bid, as written to the append-only log. */
record AuBidEvent(String requestId, String bidder, long maxCents, Instant at) { }

interface AuAuctionListener {
    void onNewLeader(String auctionId, String leader, long priceCents);

    void onOutbid(String auctionId, String bidder, long priceCents);

    void onClosed(String auctionId, AuAuctionState result, String winner, long priceCents);
}

class AuConsoleListener implements AuAuctionListener {
    private final boolean showLeaders;

    AuConsoleListener(boolean showLeaders) {
        this.showLeaders = showLeaders;
    }

    @Override
    public void onNewLeader(String auctionId, String leader, long priceCents) {
        if (showLeaders) {
            System.out.println("    [notify] " + leader + " now leads " + auctionId + " at " + AuMoney.fmt(priceCents));
        }
    }

    @Override
    public void onOutbid(String auctionId, String bidder, long priceCents) {
        if (showLeaders) {
            System.out.println("    [notify] " + bidder + " was outbid on " + auctionId + " (price " + AuMoney.fmt(priceCents) + ")");
        }
    }

    @Override
    public void onClosed(String auctionId, AuAuctionState result, String winner, long priceCents) {
        System.out.println("    [notify] " + auctionId + " closed: " + result
                + (winner == null ? "" : ", winner " + winner + " pays " + AuMoney.fmt(priceCents)));
    }
}

// -----------------------------------------------------------------------------
//  THE AUCTION: all mutable state guarded by one lock per auction
// -----------------------------------------------------------------------------
class AuAuction {
    private final String id;
    private final String sellerId;
    private final long startPriceCents;
    private final long reserveCents;           // 0 = no reserve
    private final Instant startTime;
    private final AuIncrementPolicy increments;
    private final AuEndPolicy endPolicy;
    private final List<AuAuctionListener> listeners = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();

    // ---- guarded by lock ----
    private Instant endTime;
    private AuAuctionState state = AuAuctionState.SCHEDULED;
    private String leader;                     // null until the first valid bid
    private long leaderMaxCents;               // hidden proxy maximum
    private long priceCents;                   // visible current price
    private final List<AuBidEvent> log = new ArrayList<>();

    AuAuction(String id, String sellerId, long startPriceCents, long reserveCents,
              Instant startTime, Instant endTime, AuIncrementPolicy increments, AuEndPolicy endPolicy) {
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("end must be after start");
        }
        this.id = id;
        this.sellerId = sellerId;
        this.startPriceCents = startPriceCents;
        this.reserveCents = reserveCents;
        this.startTime = startTime;
        this.endTime = endTime;
        this.increments = increments;
        this.endPolicy = endPolicy;
        this.priceCents = startPriceCents;
    }

    String id() { return id; }

    void addListener(AuAuctionListener l) {
        listeners.add(l);
    }

    /** The whole bid path: lazy open/close, validation, proxy resolution, end extension. */
    AuBidResult bid(String requestId, String bidder, long maxCents, Instant now) {
        lock.lock();
        try {
            refreshState(now);
            if (state == AuAuctionState.SCHEDULED) {
                return reject("auction has not started");
            }
            if (state != AuAuctionState.OPEN) {
                return reject("auction is closed (" + state + ")");
            }
            if (bidder.equals(sellerId)) {
                return reject("seller cannot bid on own item");
            }
            if (leader == null) {
                if (maxCents < startPriceCents) {
                    return reject("below start price " + AuMoney.fmt(startPriceCents));
                }
                leader = bidder;
                leaderMaxCents = maxCents;
                priceCents = startPriceCents;
            } else if (bidder.equals(leader)) {
                if (maxCents <= leaderMaxCents) {
                    return reject("new maximum must exceed your current maximum");
                }
                leaderMaxCents = maxCents;          // raising your own max does not raise the price
            } else {
                long minimum = priceCents + increments.incrementAt(priceCents);
                if (maxCents < minimum) {
                    return reject("below minimum bid " + AuMoney.fmt(minimum));
                }
                if (maxCents > leaderMaxCents) {
                    String previous = leader;
                    long step = increments.incrementAt(leaderMaxCents);
                    priceCents = Math.min(maxCents, leaderMaxCents + step);
                    leader = bidder;
                    leaderMaxCents = maxCents;
                    applyReserve();
                    log.add(new AuBidEvent(requestId, bidder, maxCents, now));
                    endTime = endPolicy.afterAcceptedBid(endTime, now);
                    String l = leader;
                    long p = priceCents;
                    listeners.forEach(x -> x.onOutbid(id, previous, p));
                    listeners.forEach(x -> x.onNewLeader(id, l, p));
                    return new AuBidResult(true, null, leader, priceCents, endTime);
                }
                // Challenger did not beat the hidden maximum: the leader's proxy answers.
                long step = increments.incrementAt(maxCents);
                priceCents = Math.min(leaderMaxCents, maxCents + step);
                applyReserve();
                log.add(new AuBidEvent(requestId, bidder, maxCents, now));
                endTime = endPolicy.afterAcceptedBid(endTime, now);
                long p = priceCents;
                listeners.forEach(x -> x.onOutbid(id, bidder, p));
                return new AuBidResult(true, "accepted but immediately outbid by a proxy", leader, priceCents, endTime);
            }
            applyReserve();
            log.add(new AuBidEvent(requestId, bidder, maxCents, now));
            endTime = endPolicy.afterAcceptedBid(endTime, now);
            String l = leader;
            long p = priceCents;
            listeners.forEach(x -> x.onNewLeader(id, l, p));
            return new AuBidResult(true, null, leader, priceCents, endTime);
        } finally {
            lock.unlock();
        }
    }

    /** Called by a scheduler AND lazily by every bid and read. Safe to call any number of times. */
    boolean closeIfDue(Instant now) {
        lock.lock();
        try {
            AuAuctionState before = state;
            refreshState(now);
            return before == AuAuctionState.OPEN && state != AuAuctionState.OPEN;
        } finally {
            lock.unlock();
        }
    }

    boolean cancel(Instant now) {
        lock.lock();
        try {
            refreshState(now);
            if (state != AuAuctionState.SCHEDULED && !(state == AuAuctionState.OPEN && leader == null)) {
                return false;   // only before start, or while nobody has bid
            }
            state = AuAuctionState.CANCELLED;
            listeners.forEach(x -> x.onClosed(id, AuAuctionState.CANCELLED, null, 0));
            return true;
        } finally {
            lock.unlock();
        }
    }

    /** End time is EXCLUSIVE: a bid is valid only if now < endTime. */
    private void refreshState(Instant now) {
        if (state == AuAuctionState.SCHEDULED && !now.isBefore(startTime)) {
            state = AuAuctionState.OPEN;
        }
        if (state == AuAuctionState.OPEN && !now.isBefore(endTime)) {
            boolean sold = leader != null && priceCents >= reserveCents;
            state = sold ? AuAuctionState.SOLD : AuAuctionState.UNSOLD;
            String winner = sold ? leader : null;
            long p = sold ? priceCents : 0;
            AuAuctionState result = state;
            listeners.forEach(x -> x.onClosed(id, result, winner, p));
        }
    }

    /** When the leader's maximum covers the reserve, the price jumps to the reserve. */
    private void applyReserve() {
        if (reserveCents > 0 && leaderMaxCents >= reserveCents && priceCents < reserveCents) {
            priceCents = reserveCents;
        }
    }

    private AuBidResult reject(String reason) {
        return new AuBidResult(false, reason, leader, priceCents, endTime);
    }

    // ---- read side, all under the lock so a reader never sees half a bid ----
    String snapshot(Instant now) {
        lock.lock();
        try {
            refreshState(now);
            return id + " " + state + " leader=" + leader + " price=" + AuMoney.fmt(priceCents)
                    + " bids=" + log.size() + " ends=" + endTime;
        } finally {
            lock.unlock();
        }
    }

    List<AuBidEvent> bidLog() {
        lock.lock();
        try {
            return List.copyOf(log);
        } finally {
            lock.unlock();
        }
    }

    String leader() {
        lock.lock();
        try {
            return leader;
        } finally {
            lock.unlock();
        }
    }

    long leaderMax() {
        lock.lock();
        try {
            return leaderMaxCents;
        } finally {
            lock.unlock();
        }
    }

    long price() {
        lock.lock();
        try {
            return priceCents;
        } finally {
            lock.unlock();
        }
    }

    Instant endTime() {
        lock.lock();
        try {
            return endTime;
        } finally {
            lock.unlock();
        }
    }
}

// -----------------------------------------------------------------------------
//  FACADE: registry of auctions + request-id idempotency
// -----------------------------------------------------------------------------
class AuAuctionHouse {
    private final Map<String, AuAuction> auctions = new ConcurrentHashMap<>();
    private final Map<String, AuBidResult> resultsByRequestId = new ConcurrentHashMap<>();
    private final AuClock clock;

    AuAuctionHouse(AuClock clock) {
        this.clock = clock;
    }

    AuAuction list(AuAuction auction) {
        auctions.put(auction.id(), auction);
        return auction;
    }

    /** A retried request (same requestId) gets the FIRST result back and places nothing. */
    AuBidResult placeBid(String requestId, String auctionId, String bidder, long maxCents) {
        AuAuction auction = auctions.get(auctionId);
        if (auction == null) {
            return new AuBidResult(false, "no such auction", null, 0, null);
        }
        return resultsByRequestId.computeIfAbsent(requestId,
                rid -> auction.bid(rid, bidder, maxCents, clock.now()));
    }

    /** What a scheduler would call every second. Lazy close on each bid covers any gap. */
    int closeDue() {
        int closed = 0;
        for (AuAuction a : auctions.values()) {
            if (a.closeIfDue(clock.now())) {
                closed++;
            }
        }
        return closed;
    }
}

// -----------------------------------------------------------------------------
//  BROKEN on purpose: check-then-act with no lock (a plain English auction)
// -----------------------------------------------------------------------------
class AuNaiveAuction {
    long highestCents;
    String highestBidder;

    AuNaiveAuction(long startCents) {
        this.highestCents = startCents;
    }

    /** `afterCheck` lets the demo pause a thread between the check and the write. */
    boolean bid(String bidder, long cents, Runnable afterCheck) {
        if (cents <= highestCents) {
            return false;
        }
        afterCheck.run();             // another thread runs here in the demo
        highestCents = cents;         // last writer wins, even if it is the LOWER bid
        highestBidder = bidder;
        return true;
    }
}

// -----------------------------------------------------------------------------
//  DEMO
// -----------------------------------------------------------------------------
public class OnlineAuction {
    private static final Instant T0 = Instant.parse("2026-11-25T12:00:00Z");

    public static void main(String[] args) throws Exception {
        System.out.println("=== Online Auction: proxy bids, per-auction lock, close rules, idempotent retries ===");
        System.out.println();
        demonstrateLostUpdate();
        demonstrateProxyBidding();
        demonstrateSniping();
        demonstrateCloseRules();
        demonstrateIdempotentRetry();
        demonstrateReplay();
        demonstrateConcurrentStress();
    }

    /** Two threads, forced into the bad interleaving with latches, so the result is the same every run. */
    private static void demonstrateLostUpdate() throws Exception {
        System.out.println("--- Broken: check-then-act without a lock ---");
        AuNaiveAuction naive = new AuNaiveAuction(10_000);
        CountDownLatch bothChecked = new CountDownLatch(2);
        CountDownLatch aWrote = new CountDownLatch(1);
        Thread a = new Thread(() -> naive.bid("alice", 12_000, () -> {
            bothChecked.countDown();
            await(bothChecked);
        }));
        Thread b = new Thread(() -> naive.bid("bob", 11_000, () -> {
            bothChecked.countDown();
            await(bothChecked);
            await(aWrote);            // make bob write second: the worst case
        }));
        a.start();
        b.start();
        a.join();
        aWrote.countDown();
        b.join();
        System.out.println("alice bid $120.00, bob bid $110.00, both passed the check against $100.00");
        System.out.println("final highest: " + naive.highestBidder + " at " + AuMoney.fmt(naive.highestCents)
                + "   <- the $120.00 bid was silently overwritten");

        AuFakeClock clock = new AuFakeClock(T0);
        AuAuction safe = new AuAuction("A-0", "seller", 10_000, 0, T0, T0.plus(Duration.ofHours(1)),
                new AuLadderIncrements(), new AuFixedEnd());
        CountDownLatch go = new CountDownLatch(1);
        AuBidResult[] r = new AuBidResult[2];
        Thread a2 = new Thread(() -> { await(go); r[0] = safe.bid("a", "alice", 12_000, clock.now()); });
        a2.start();
        go.countDown();
        a2.join();
        r[1] = safe.bid("b", "bob", 11_000, clock.now());
        System.out.println("Fix (one lock per auction): alice " + r[0]);
        System.out.println("                            bob   " + r[1]);
        System.out.println();
    }

    private static void demonstrateProxyBidding() {
        System.out.println("--- Step 1: proxy bidding on the eBay increment ladder ---");
        AuFakeClock clock = new AuFakeClock(T0);
        AuAuctionHouse house = new AuAuctionHouse(clock);
        AuAuction a = house.list(new AuAuction("A-1", "sam", 1_000, 0, T0, T0.plus(Duration.ofDays(7)),
                new AuLadderIncrements(), new AuFixedEnd()));
        a.addListener(new AuConsoleListener(true));
        System.out.println("start price $10.00; ladder step at $10.00 is "
                + AuMoney.fmt(new AuLadderIncrements().incrementAt(1_000)) + ", at $30.00 is "
                + AuMoney.fmt(new AuLadderIncrements().incrementAt(3_000)));
        System.out.println("alice max $50.00 -> " + house.placeBid("r1", "A-1", "alice", 5_000));
        System.out.println("bob   max $30.00 -> " + house.placeBid("r2", "A-1", "bob", 3_000));
        System.out.println("carol max $50.00 -> " + house.placeBid("r3", "A-1", "carol", 5_000));
        System.out.println("dave  max $75.00 -> " + house.placeBid("r4", "A-1", "dave", 7_500));
        System.out.println("erin  max $51.50 -> " + house.placeBid("r5", "A-1", "erin", 5_150));
        System.out.println("sam (seller)     -> " + house.placeBid("r6", "A-1", "sam", 9_000));
        System.out.println("dave raises own max to $90.00 -> " + house.placeBid("r7", "A-1", "dave", 9_000));
        System.out.println();
    }

    private static void demonstrateSniping() {
        System.out.println("--- Step 2: a bid 10 seconds before the end, fixed end vs soft close ---");
        for (AuEndPolicy policy : new AuEndPolicy[] {new AuFixedEnd(), new AuSoftClose(Duration.ofMinutes(2), Duration.ofMinutes(2))}) {
            AuFakeClock clock = new AuFakeClock(T0);
            AuAuction a = new AuAuction("A-2", "sam", 1_000, 0, T0, T0.plus(Duration.ofHours(1)),
                    new AuLadderIncrements(), policy);
            a.bid("s1", "alice", 4_000, clock.now());
            clock.set(T0.plus(Duration.ofHours(1)).minusSeconds(10));
            AuBidResult snipe = a.bid("s2", "sniper", 4_500, clock.now());
            System.out.println(policy.name() + ": sniper " + snipe + "  end=" + snipe.endTime());
            clock.set(T0.plus(Duration.ofHours(1)).plusSeconds(30));
            AuBidResult reply = a.bid("s3", "alice", 6_000, clock.now());
            System.out.println("  alice answers 30 s after the original end -> " + reply);
        }
        System.out.println();
    }

    private static void demonstrateCloseRules() {
        System.out.println("--- Step 3: close semantics ---");
        AuFakeClock clock = new AuFakeClock(T0.minusSeconds(60));
        AuAuctionHouse house = new AuAuctionHouse(clock);
        Instant end = T0.plus(Duration.ofHours(1));
        AuAuction reserve = house.list(new AuAuction("A-3", "sam", 1_000, 20_000, T0, end,
                new AuLadderIncrements(), new AuFixedEnd()));
        reserve.addListener(new AuConsoleListener(false));
        System.out.println("bid before start -> " + house.placeBid("c0", "A-3", "alice", 5_000));
        clock.set(T0);
        System.out.println("alice max $150.00 (reserve $200.00 hidden) -> " + house.placeBid("c1", "A-3", "alice", 15_000));
        clock.set(end);
        System.out.println("bob bids at EXACTLY the end time -> " + house.placeBid("c2", "A-3", "bob", 30_000));
        System.out.println("scheduler pass 1 closed: " + house.closeDue() + "   (already closed lazily by bob's bid)");
        System.out.println("scheduler pass 2 closed: " + house.closeDue() + "   (idempotent)");

        AuAuction met = house.list(new AuAuction("A-4", "sam", 1_000, 20_000, T0, end.plusSeconds(3_600),
                new AuLadderIncrements(), new AuFixedEnd()));
        met.addListener(new AuConsoleListener(false));
        System.out.println("A-4: carol max $250.00 covers the $200.00 reserve -> "
                + house.placeBid("c3", "A-4", "carol", 25_000));
        clock.set(end.plusSeconds(3_600));
        System.out.println("scheduler pass 3 closed: " + house.closeDue());
        System.out.println();
    }

    private static void demonstrateIdempotentRetry() {
        System.out.println("--- Step 4: a client retries the same request after a timeout ---");
        AuFakeClock clock = new AuFakeClock(T0);
        AuAuctionHouse house = new AuAuctionHouse(clock);
        AuAuction a = house.list(new AuAuction("A-5", "sam", 1_000, 0, T0, T0.plus(Duration.ofDays(1)),
                new AuLadderIncrements(), new AuFixedEnd()));
        house.placeBid("req-77", "A-5", "alice", 2_000);
        house.placeBid("req-78", "A-5", "bob", 2_500);
        AuBidResult retry = house.placeBid("req-77", "A-5", "alice", 2_000);
        System.out.println("retry of req-77 returns the stored first result: " + retry);
        System.out.println("bids in the log: " + a.bidLog().size() + "  (not 3)");
        System.out.println();
    }

    private static void demonstrateReplay() {
        System.out.println("--- Step 5: rebuild an auction from its accepted-bid log ---");
        AuFakeClock clock = new AuFakeClock(T0);
        AuAuction original = new AuAuction("A-6", "sam", 1_000, 0, T0, T0.plus(Duration.ofDays(1)),
                new AuLadderIncrements(), new AuFixedEnd());
        Random rnd = new Random(7);
        String[] bidders = {"alice", "bob", "carol", "dave"};
        int attempts = 0;
        for (int i = 0; i < 40; i++) {
            clock.advance(Duration.ofMinutes(1 + rnd.nextInt(5)));
            original.bid("p" + i, bidders[rnd.nextInt(bidders.length)], 1_000 + rnd.nextInt(20_000), clock.now());
            attempts++;
        }
        AuAuction restored = new AuAuction("A-6", "sam", 1_000, 0, T0, T0.plus(Duration.ofDays(1)),
                new AuLadderIncrements(), new AuFixedEnd());
        for (AuBidEvent e : original.bidLog()) {
            restored.bid(e.requestId(), e.bidder(), e.maxCents(), e.at());   // recorded time, never now()
        }
        System.out.println(attempts + " attempts, " + original.bidLog().size() + " accepted and logged");
        System.out.println("original: leader=" + original.leader() + " price=" + AuMoney.fmt(original.price()));
        System.out.println("restored: leader=" + restored.leader() + " price=" + AuMoney.fmt(restored.price()));
        System.out.println("identical? " + (original.leader().equals(restored.leader()) && original.price() == restored.price()));
        System.out.println();
    }

    private static void demonstrateConcurrentStress() throws Exception {
        System.out.println("--- Step 6: 8 threads x 2,000 bids on one auction, with the per-auction lock ---");
        AuFakeClock clock = new AuFakeClock(T0);
        AuAuctionHouse house = new AuAuctionHouse(clock);
        AuAuction a = house.list(new AuAuction("A-7", "sam", 100, 0, T0, T0.plus(Duration.ofDays(1)),
                new AuLadderIncrements(), new AuFixedEnd()));
        int threads = 8;
        int perThread = 2_000;
        AtomicInteger accepted = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            final int tid = t;
            pool.submit(() -> {
                Random rnd = new Random(tid);
                for (int i = 0; i < perThread; i++) {
                    long max = 100 + rnd.nextInt(1_000_000);
                    if (house.placeBid("t" + tid + "-" + i, "A-7", "user" + tid + "-" + i, max).accepted()) {
                        accepted.incrementAndGet();
                    }
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(1, TimeUnit.MINUTES);
        long highestLogged = 0;
        for (AuBidEvent e : a.bidLog()) {
            highestLogged = Math.max(highestLogged, e.maxCents());
        }
        System.out.println("bids submitted: " + (threads * perThread)
                + ", accepted == logged? " + (accepted.get() == a.bidLog().size()));
        System.out.println("leader's max == highest accepted max? " + (a.leaderMax() == highestLogged));
        System.out.println("price <= leader's max? " + (a.price() <= a.leaderMax()));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
