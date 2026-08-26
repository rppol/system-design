package com.rutik.systemdesign.lld.system_design_problems;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

// =============================================================================
//  ORDER MATCHING ENGINE - Low-Level Design
//  Extracted verbatim from OrderMatchingEngine.md's Implementation section.
//
//  This is the single-JVM, single-threaded, in-memory-data-structure view of
//  matching (contrast with ../../hld/case_studies/design_stock_exchange.md,
//  the distributed sequencer/gateway/fan-out view of the SAME problem at scale).
//
//  Core data structures:
//    - OrderBook.bids : TreeMap<Long, Deque<Order>> keyed DESCENDING (best
//      bid = highest price = firstKey())
//    - OrderBook.asks : TreeMap<Long, Deque<Order>> keyed ASCENDING (best
//      ask = lowest price = firstKey())
//    - each Deque is FIFO -- time priority among orders tied on price
//    - price is a `long` in minor-unit ticks (cents), never a double/float
//      -- see demonstrateDoublePriceBug() below for why, run for real.
//
//  Patterns: the order lifecycle (NEW -> PARTIALLY_FILLED -> FILLED /
//  CANCELLED / REJECTED) is a simplified State machine expressed as an enum
//  field rather than polymorphic State objects, because no state has distinct
//  BEHAVIOR, only distinct legal next-states -- see the page's Patterns Used
//  section for why a full State pattern would be over-engineering here.
//
//  The page's main() lives on OrderMatchingEngine itself (house style for
//  this folder), and reproduces every scenario the page's Sample Output
//  section quotes verbatim.
// =============================================================================

enum Side { BUY, SELL }

enum OrderType { LIMIT, MARKET }

enum TimeInForce {
    GTC, // Good-Til-Cancelled: fine to rest on the book indefinitely
    IOC, // Immediate-Or-Cancel: fill what crosses right now, kill the rest, never rest
    FOK  // Fill-Or-Kill: fill the ENTIRE quantity right now, or touch nothing at all
}

enum OrderStatus { NEW, PARTIALLY_FILLED, FILLED, CANCELLED, REJECTED }

final class Order {
    private static final AtomicLong SEQUENCE = new AtomicLong();

    final long id;         // also doubles as the time-priority tie-break: strictly increasing
    final Side side;
    final OrderType type;
    final TimeInForce tif;
    final long price;      // minor-unit ticks (cents); meaningless for MARKET orders
    final long originalQty;
    long remainingQty;
    OrderStatus status = OrderStatus.NEW;

    Order(Side side, OrderType type, TimeInForce tif, long price, long qty) {
        if (type == OrderType.MARKET && tif == TimeInForce.GTC) {
            throw new IllegalArgumentException(
                "a MARKET order can never rest on the book -- GTC is meaningless for it; use IOC or FOK");
        }
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be positive, got " + qty);
        }
        this.id = SEQUENCE.getAndIncrement();
        this.side = side;
        this.type = type;
        this.tif = tif;
        this.price = price;
        this.originalQty = qty;
        this.remainingQty = qty;
    }

    static Order limit(Side side, long price, long qty) {
        return new Order(side, OrderType.LIMIT, TimeInForce.GTC, price, qty);
    }

    static Order limit(Side side, long price, long qty, TimeInForce tif) {
        return new Order(side, OrderType.LIMIT, tif, price, qty);
    }

    static Order market(Side side, long qty, TimeInForce tif) {
        return new Order(side, OrderType.MARKET, tif, 0L, qty);
    }

    static String fmt(long cents) {
        return String.format("$%d.%02d", cents / 100, Math.abs(cents % 100));
    }

    @Override
    public String toString() {
        String priceStr = (type == OrderType.MARKET) ? "MKT" : fmt(price);
        return String.format("#%02d %-4s %-6s %-3s qty=%d/%d price=%s status=%s",
            id, side, type, tif, remainingQty, originalQty, priceStr, status);
    }
}

record Trade(long tradeId, long buyOrderId, long sellOrderId, long price, long qty) {
    @Override
    public String toString() {
        return String.format("TRADE #%d: buy#%02d x sell#%02d @ %s x%d",
            tradeId, buyOrderId, sellOrderId, Order.fmt(price), qty);
    }
}

/**
 * The limit-order book: two price-ordered maps of FIFO queues, plus an id
 * index for O(1) cancel lookup. Pure data structure -- no matching logic
 * lives here, only storage, ordering, and the crossed-book invariant check.
 */
final class OrderBook {
    // best bid = HIGHEST price = firstKey() -> reverse natural order
    private final NavigableMap<Long, Deque<Order>> bids = new TreeMap<>(java.util.Collections.reverseOrder());
    // best ask = LOWEST price = firstKey() -> natural order
    private final NavigableMap<Long, Deque<Order>> asks = new TreeMap<>();
    private final Map<Long, Order> restingById = new HashMap<>();

    NavigableMap<Long, Deque<Order>> sideBook(Side side) {
        return side == Side.BUY ? bids : asks;
    }

    NavigableMap<Long, Deque<Order>> oppositeBook(Side side) {
        return side == Side.BUY ? asks : bids;
    }

    void rest(Order order) {
        sideBook(order.side).computeIfAbsent(order.price, p -> new ArrayDeque<>()).addLast(order);
        restingById.put(order.id, order);
    }

    /** Called by the matching loop once a resting order's remainingQty hits 0. */
    void detachFilled(Order order, Deque<Order> level, long priceKey, Side side) {
        level.pollFirst();
        restingById.remove(order.id);
        if (level.isEmpty()) {
            sideBook(side).remove(priceKey);
        }
    }

    boolean cancel(long orderId) {
        Order order = restingById.get(orderId);
        if (order == null) {
            return false; // never rested, already filled, already cancelled, or never existed
        }
        Deque<Order> level = sideBook(order.side).get(order.price);
        if (level == null || !level.remove(order)) { // identity-based remove -- no equals() override needed
            return false;
        }
        if (level.isEmpty()) {
            sideBook(order.side).remove(order.price);
        }
        order.status = OrderStatus.CANCELLED;
        restingById.remove(orderId);
        return true;
    }

    Long bestPrice(Side side) {
        NavigableMap<Long, Deque<Order>> book = sideBook(side);
        return book.isEmpty() ? null : book.firstKey();
    }

    /** The invariant the whole design exists to protect: never a resting cross. */
    void assertNoCross() {
        Long bestBid = bestPrice(Side.BUY);
        Long bestAsk = bestPrice(Side.SELL);
        if (bestBid != null && bestAsk != null && bestBid >= bestAsk) {
            throw new IllegalStateException(
                "CROSSED BOOK: bestBid=" + Order.fmt(bestBid) + " >= bestAsk=" + Order.fmt(bestAsk));
        }
    }

    List<String> depthLines(Side side) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<Long, Deque<Order>> level : sideBook(side).entrySet()) {
            long qty = 0;
            for (Order o : level.getValue()) {
                qty += o.remainingQty;
            }
            lines.add(String.format("  %-4s %-9s x%-5d (%d order%s)",
                side, Order.fmt(level.getKey()), qty, level.getValue().size(),
                level.getValue().size() == 1 ? "" : "s"));
        }
        if (lines.isEmpty()) {
            lines.add("  " + side + " <empty>");
        }
        return lines;
    }
}

/**
 * The matching algorithm: price-time priority over an OrderBook. Every
 * submit() either produces trades, rests the leftover (GTC LIMIT only), or
 * kills the leftover (MARKET / IOC) -- and FOK is checked BEFORE any
 * mutation happens at all, so a rejected FOK never touches the book.
 */
final class MatchingEngine {
    private static final AtomicLong TRADE_SEQ = new AtomicLong();

    private final OrderBook book = new OrderBook();

    OrderBook book() {
        return book;
    }

    List<Trade> submit(Order incoming) {
        List<Trade> trades = new ArrayList<>();

        if (incoming.tif == TimeInForce.FOK && !fullyFillable(incoming)) {
            incoming.status = OrderStatus.REJECTED;
            return trades; // book is completely untouched -- no partial fill on a rejected FOK
        }

        match(incoming, trades);

        if (incoming.remainingQty == 0) {
            incoming.status = OrderStatus.FILLED;
        } else if (incoming.type == OrderType.LIMIT && incoming.tif == TimeInForce.GTC) {
            book.rest(incoming);
            incoming.status = trades.isEmpty() ? OrderStatus.NEW : OrderStatus.PARTIALLY_FILLED;
        } else {
            // MARKET or IOC: never rest. Whatever qty is left is simply discarded here.
            incoming.status = trades.isEmpty() ? OrderStatus.CANCELLED : OrderStatus.PARTIALLY_FILLED;
        }

        book.assertNoCross();
        return trades;
    }

    boolean cancel(long orderId) {
        return book.cancel(orderId);
    }

    /**
     * The matching loop. Deliberately a `while` pulling firstEntry()/getKey()
     * each pass -- NEVER an enhanced-for over a live NavigableMap that this
     * loop also removes keys from. See demonstrateConcurrentModificationBug()
     * below for exactly what breaks if you write it the other way.
     */
    private void match(Order incoming, List<Trade> trades) {
        NavigableMap<Long, Deque<Order>> opposite = book.oppositeBook(incoming.side);

        while (incoming.remainingQty > 0) {
            Map.Entry<Long, Deque<Order>> best = opposite.firstEntry();
            if (best == null || !crosses(incoming, best.getKey())) {
                break;
            }

            long priceKey = best.getKey();
            Deque<Order> level = best.getValue();
            Order resting = level.peekFirst(); // FIFO -- oldest order at this price wins first

            long fillQty = Math.min(incoming.remainingQty, resting.remainingQty);
            long fillPrice = resting.price; // the RESTING order's price always sets the trade price

            long buyId = incoming.side == Side.BUY ? incoming.id : resting.id;
            long sellId = incoming.side == Side.SELL ? incoming.id : resting.id;
            trades.add(new Trade(TRADE_SEQ.incrementAndGet(), buyId, sellId, fillPrice, fillQty));

            incoming.remainingQty -= fillQty;
            resting.remainingQty -= fillQty;

            if (resting.remainingQty == 0) {
                resting.status = OrderStatus.FILLED;
                book.detachFilled(resting, level, priceKey, resting.side); // mutation happens HERE, between loop passes
            } else {
                resting.status = OrderStatus.PARTIALLY_FILLED;
            }
        }
    }

    private boolean crosses(Order incoming, long oppositeBestPrice) {
        if (incoming.type == OrderType.MARKET) {
            return true; // marketable against any price -- that is the definition of MARKET
        }
        return incoming.side == Side.BUY
            ? incoming.price >= oppositeBestPrice
            : incoming.price <= oppositeBestPrice;
    }

    /**
     * Read-only dry run for FOK: sums exactly what a real match() would
     * consume, WITHOUT mutating anything, so a rejected FOK leaves zero trace.
     */
    private boolean fullyFillable(Order incoming) {
        NavigableMap<Long, Deque<Order>> opposite = book.oppositeBook(incoming.side);
        long need = incoming.remainingQty;
        for (Map.Entry<Long, Deque<Order>> level : opposite.entrySet()) {
            if (!crosses(incoming, level.getKey())) {
                break;
            }
            for (Order resting : level.getValue()) {
                need -= resting.remainingQty;
                if (need <= 0) {
                    return true;
                }
            }
        }
        return false;
    }
}

public final class OrderMatchingEngine {

    public static void main(String[] args) {
        demonstrateDoublePriceBug();
        demonstrateConcurrentModificationBug();

        System.out.println();
        System.out.println("=== Order Matching Engine: price-time priority over a live book ===");

        MatchingEngine engine = new MatchingEngine();

        // ---- Step 1: build the initial book -------------------------------
        Order a1 = Order.limit(Side.SELL, 10_005, 100);
        Order a2 = Order.limit(Side.SELL, 10_010, 200);
        Order a3 = Order.limit(Side.SELL, 10_015, 150);
        Order b1 = Order.limit(Side.BUY, 9_995, 120);
        Order b2 = Order.limit(Side.BUY, 9_990, 200);
        Order b3 = Order.limit(Side.BUY, 9_985, 100);
        for (Order resting : List.of(a1, a2, a3, b1, b2, b3)) {
            engine.submit(resting);
        }
        System.out.println();
        System.out.println("--- Step 1: initial book ---");
        printDepth(engine);

        // ---- Step 2: a crossing LIMIT order sweeps + partially fills a level
        System.out.println();
        System.out.println("--- Step 2: LIMIT BUY qty=150 @ $100.10 crosses two ask levels ---");
        Order c1 = Order.limit(Side.BUY, 10_010, 150);
        printTrades(engine.submit(c1));
        System.out.println("incoming: " + c1);
        printDepth(engine);

        // ---- Step 3: incoming order itself partially fills and rests ------
        System.out.println();
        System.out.println("--- Step 3: LIMIT BUY qty=500 @ $100.10 -- fills what crosses, rests the remainder ---");
        Order c2 = Order.limit(Side.BUY, 10_010, 500);
        printTrades(engine.submit(c2));
        System.out.println("incoming: " + c2);
        printDepth(engine);

        // ---- Step 4: MARKET order sweeps multiple bid levels ---------------
        System.out.println();
        System.out.println("--- Step 4: MARKET SELL qty=400 sweeps two bid levels, no price given ---");
        Order c3 = Order.market(Side.SELL, 400, TimeInForce.IOC);
        printTrades(engine.submit(c3));
        System.out.println("incoming: " + c3);
        printDepth(engine);

        // ---- Step 5: IOC leaves no residual on the book --------------------
        System.out.println();
        System.out.println("--- Step 5: LIMIT BUY IOC qty=300 @ $100.15 -- fills 150, kills the other 150 ---");
        Order c4 = Order.limit(Side.BUY, 10_015, 300, TimeInForce.IOC);
        printTrades(engine.submit(c4));
        System.out.println("incoming: " + c4 + "  (never entered the book -- IOC never rests)");
        printDepth(engine);

        // ---- Step 6: FOK rejects outright when liquidity is insufficient ---
        System.out.println();
        System.out.println("--- Step 6: LIMIT SELL FOK qty=1000 @ $99.85 -- only 370 available, engine rejects ---");
        List<String> beforeBids = engine.book().depthLines(Side.BUY);
        Order c5 = Order.limit(Side.SELL, 9_985, 1000, TimeInForce.FOK);
        printTrades(engine.submit(c5));
        System.out.println("incoming: " + c5);
        List<String> afterBids = engine.book().depthLines(Side.BUY);
        System.out.println("book unchanged by the rejected FOK? " + beforeBids.equals(afterBids));
        printDepth(engine);

        // ---- Step 7: cancel ------------------------------------------------
        System.out.println();
        System.out.println("--- Step 7: cancel a resting order, then try to cancel one that's gone ---");
        boolean cancelledB2 = engine.cancel(b2.id);
        System.out.println("cancel(b2 #" + b2.id + ") -> " + cancelledB2 + "  status=" + b2.status);
        boolean cancelledAgain = engine.cancel(a1.id); // a1 was fully filled back in Step 2
        System.out.println("cancel(a1 #" + a1.id + ", already filled) -> " + cancelledAgain);
        printDepth(engine);

        // ---- Step 8: final summary ------------------------------------------
        System.out.println();
        System.out.println("--- Step 8: crossed-book invariant held across every submit() above ---");
        Long bestBid = engine.book().bestPrice(Side.BUY);
        Long bestAsk = engine.book().bestPrice(Side.SELL);
        System.out.println("final bestBid=" + (bestBid == null ? "none" : Order.fmt(bestBid))
            + "  bestAsk=" + (bestAsk == null ? "none" : Order.fmt(bestAsk)));
    }

    // -------------------------------------------------------------------
    // Broken-code demonstrations. Both are run for real, output pasted
    // verbatim into the page's Sample Output section -- neither is
    // hypothetical.
    // -------------------------------------------------------------------

    /** Why price must be a `long` in ticks, never a `double`. */
    private static void demonstrateDoublePriceBug() {
        System.out.println("--- Broken: a double price key silently splits one price level into two ---");
        double base = 100.00;
        double tick = 0.10;
        double walkedUp = base;
        for (int i = 0; i < 3; i++) {
            walkedUp += tick; // simulate a resting order priced by walking the book up 3 ticks
        }
        double literal = 100.30; // a second order submitted directly at "$100.30"
        System.out.printf("walked-up price (100.00 + 0.10 x3) = %.17f%n", walkedUp);
        System.out.printf("literal price      (100.30)         = %.17f%n", literal);
        System.out.println("walked == literal ? " + (walkedUp == literal) + "  <- both mean $100.30, and are not equal");

        java.util.TreeMap<Double, String> brokenBook = new java.util.TreeMap<>();
        brokenBook.put(walkedUp, "resting SELL 100 @ $100.30 (price built by walking ticks)");
        boolean incomingFindsIt = brokenBook.containsKey(literal);
        System.out.println("an incoming BUY at literal $100.30 finds that resting order? " + incomingFindsIt);
        System.out.println("Fix: price is a long in minor-unit ticks (cents) -- 10_000 + 10*3 == 10_030, always, exactly.");
        System.out.println();
    }

    /** Why the matching loop must never mutate a NavigableMap through a live enhanced-for. */
    private static void demonstrateConcurrentModificationBug() {
        System.out.println("--- Broken: removing a drained price level while an enhanced-for iterates the book ---");
        NavigableMap<Long, Deque<String>> toyBook = new TreeMap<>();
        toyBook.put(10_000L, new ArrayDeque<>(List.of("ask@100.00 x50")));
        toyBook.put(10_100L, new ArrayDeque<>(List.of("ask@101.00 x50")));
        toyBook.put(10_200L, new ArrayDeque<>(List.of("ask@102.00 x50")));
        try {
            for (Map.Entry<Long, Deque<String>> level : toyBook.entrySet()) {
                level.getValue().pollFirst(); // fully consume the level
                if (level.getValue().isEmpty()) {
                    toyBook.remove(level.getKey()); // structural mutation mid-iteration -- BUG
                }
            }
            System.out.println("loop completed cleanly (should not happen)");
        } catch (ConcurrentModificationException e) {
            System.out.println("threw " + e + " after only 1 of 3 levels processed");
            System.out.println("remaining levels frozen mid-crash: " + toyBook.keySet());
            System.out.println("Fix: pull firstEntry()/remove(key) in a while loop -- no live iterator, nothing to trip.");
        }
        System.out.println();
    }

    private static void printTrades(List<Trade> trades) {
        if (trades.isEmpty()) {
            System.out.println("  (no trades)");
        }
        for (Trade t : trades) {
            System.out.println("  " + t);
        }
    }

    private static void printDepth(MatchingEngine engine) {
        System.out.println("book:");
        for (String line : engine.book().depthLines(Side.SELL)) {
            System.out.println(line);
        }
        System.out.println("  -------");
        for (String line : engine.book().depthLines(Side.BUY)) {
            System.out.println(line);
        }
    }
}
