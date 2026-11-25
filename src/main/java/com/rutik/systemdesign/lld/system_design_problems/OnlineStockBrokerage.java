package com.rutik.systemdesign.lld.system_design_problems;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

// =============================================================================
//  ONLINE STOCK BROKERAGE - Low-Level Design
//
//  The broker side of trading: accounts, buying power, orders, execution
//  reports, positions and T+1 settlement. The venue (exchange or wholesaler)
//  is behind an interface and simulated here; its matching engine is the
//  subject of OrderMatchingEngine.md.
//
//  Patterns used:
//    - State    : BkOrderStatus with an explicit transition check
//    - Adapter  : BkVenue hides the venue protocol (FIX, OUCH, REST) from the OMS
//    - Observer : the venue pushes BkExecReport objects to the brokerage
//    - Facade   : BkBrokerage is the one entry point for apps and the venue
//
//  Key design points:
//    - Reserve at accept, release on evidence. A buy moves cash from settled
//      to reserved BEFORE it is routed; a sell reserves shares. Fills consume
//      the reservation; cancel, reject or a cheaper fill releases the rest.
//    - One lock PER ACCOUNT around check-and-reserve. Without it two orders
//      both see the same buying power: demonstrateDoubleSpend().
//    - Every effect is keyed on the venue's execId, never on arrival. A
//      resent execution report is ignored: demonstrateDuplicateExecution().
//    - The client order id makes order entry idempotent.
//    - Sale proceeds are UNSETTLED until T+1 (next business day) and are not
//      buying power until then in this conservative cash-account model.
//    - No lock is held while calling the venue, and the venue calls back
//      after releasing its own lock, so the two lock orders never cross.
//
//  Money is a long in cents; prices are cents per share; quantities are whole
//  shares. All type names carry the Bk prefix because every file in this
//  package is compiled together.
// =============================================================================

enum BkSide { BUY, SELL }

enum BkOrderType { MARKET, LIMIT }

enum BkOrderStatus {
    OPEN, PARTIALLY_FILLED, PENDING_CANCEL, FILLED, CANCELED, REJECTED;

    boolean isTerminal() {
        return this == FILLED || this == CANCELED || this == REJECTED;
    }
}

enum BkExecType { FILL, CANCELED, REJECTED }

/** What the venue sends back. execId is unique per venue and is the idempotency key. */
record BkExecReport(String execId, long orderId, BkExecType type, long qty, long priceCents, String text) { }

/** What the OMS sends to the venue: an immutable copy, never the live order. */
record BkVenueOrder(long orderId, String symbol, BkSide side, BkOrderType type, long qty, long limitCents) { }

record BkTrade(String accountId, String symbol, BkSide side, long qty, long amountCents,
               LocalDate tradeDate, LocalDate settleDate) { }

final class BkMoney {
    private BkMoney() { }

    static String fmt(long cents) {
        String sign = cents < 0 ? "-" : "";
        long abs = Math.abs(cents);
        return String.format("%s$%,d.%02d", sign, abs / 100, abs % 100);
    }
}

// -----------------------------------------------------------------------------
//  ORDER: mutable, always changed under its account's lock
// -----------------------------------------------------------------------------
class BkOrder {
    final long id;
    final String clientOrderId;
    final String accountId;
    final String symbol;
    final BkSide side;
    final BkOrderType type;
    final long qty;
    final long limitCents;
    BkOrderStatus status = BkOrderStatus.OPEN;
    long filledQty;
    long filledNotionalCents;
    long reservedCents;          // buys only: cash still held for this order
    String rejectReason;

    BkOrder(long id, String clientOrderId, String accountId, String symbol, BkSide side,
            BkOrderType type, long qty, long limitCents) {
        this.id = id;
        this.clientOrderId = clientOrderId;
        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.type = type;
        this.qty = qty;
        this.limitCents = limitCents;
    }

    long remaining() {
        return qty - filledQty;
    }

    String describe() {
        String avg = filledQty == 0 ? "-" : BkMoney.fmt(filledNotionalCents / filledQty);
        return "#" + id + " " + side + " " + qty + " " + symbol + " " + type
                + (type == BkOrderType.LIMIT ? " @" + BkMoney.fmt(limitCents) : "")
                + "  status=" + status + "  filled=" + filledQty + "  avg=" + avg
                + (rejectReason == null ? "" : "  (" + rejectReason + ")");
    }
}

class BkPosition {
    long qty;
    long reservedQty;            // shares held by open sell orders
    long costCents;              // total cost basis of the shares held

    long available() {
        return qty - reservedQty;
    }
}

// -----------------------------------------------------------------------------
//  ACCOUNT: cash buckets + positions, guarded by one lock per account
// -----------------------------------------------------------------------------
class BkAccount {
    final String id;
    final ReentrantLock lock = new ReentrantLock();
    long settledCents;           // spendable
    long unsettledCents;         // sale proceeds waiting for T+1
    long reservedCents;          // held by open buy orders
    long boughtCents;            // lifetime totals, for the conservation check
    long soldCents;
    final long depositedCents;
    final Map<String, BkPosition> positions = new HashMap<>();

    BkAccount(String id, long depositCents) {
        this.id = id;
        this.settledCents = depositCents;
        this.depositedCents = depositCents;
    }

    BkPosition position(String symbol) {
        return positions.computeIfAbsent(symbol, s -> new BkPosition());
    }

    /** Cash-account buying power in this model: settled cash only. */
    long buyingPower() {
        return settledCents;
    }

    /** Every cent is in exactly one bucket, or has left through a buy, or arrived through a sell. */
    boolean conserved() {
        return settledCents + unsettledCents + reservedCents + boughtCents - soldCents == depositedCents;
    }

    String describe() {
        StringBuilder sb = new StringBuilder(id + ": settled=" + BkMoney.fmt(settledCents)
                + " unsettled=" + BkMoney.fmt(unsettledCents) + " reserved=" + BkMoney.fmt(reservedCents));
        positions.forEach((s, p) -> {
            if (p.qty != 0 || p.reservedQty != 0) {
                sb.append("  ").append(s).append("=").append(p.qty)
                  .append(p.reservedQty > 0 ? " (" + p.reservedQty + " held for sells)" : "");
            }
        });
        return sb.toString();
    }
}

// -----------------------------------------------------------------------------
//  ADAPTER: the venue boundary
// -----------------------------------------------------------------------------
interface BkVenue {
    void setReportListener(Consumer<BkExecReport> listener);

    long[] quote(String symbol);   // {bid, bidSize, ask, askSize} or null

    void submit(BkVenueOrder order);

    void cancel(long orderId);
}

/** A tiny venue: fills against a top-of-book quote, rests limits, reports AFTER releasing its lock. */
class BkSimulatedVenue implements BkVenue {
    private final Map<String, long[]> quotes = new HashMap<>();
    private final List<long[]> restingMeta = new ArrayList<>();      // {orderId, remaining}
    private final Map<Long, BkVenueOrder> resting = new HashMap<>();
    private final AtomicLong execSeq = new AtomicLong();
    private Consumer<BkExecReport> listener = r -> { };

    @Override
    public void setReportListener(Consumer<BkExecReport> listener) {
        this.listener = listener;
    }

    @Override
    public synchronized long[] quote(String symbol) {
        long[] q = quotes.get(symbol);
        return q == null ? null : q.clone();
    }

    void setQuote(String symbol, long bid, long bidSize, long ask, long askSize) {
        List<BkExecReport> out = new ArrayList<>();
        synchronized (this) {
            quotes.put(symbol, new long[] {bid, bidSize, ask, askSize});
            for (Iterator<long[]> it = restingMeta.iterator(); it.hasNext(); ) {
                long[] meta = it.next();
                BkVenueOrder o = resting.get(meta[0]);
                if (o.symbol().equals(symbol)) {
                    meta[1] = match(o, meta[1], out);
                    if (meta[1] == 0) {
                        it.remove();
                        resting.remove(o.orderId());
                    }
                }
            }
        }
        out.forEach(listener);
    }

    @Override
    public void submit(BkVenueOrder order) {
        List<BkExecReport> out = new ArrayList<>();
        synchronized (this) {
            long remaining = match(order, order.qty(), out);
            if (remaining > 0) {
                if (order.type() == BkOrderType.MARKET) {
                    out.add(report(order.orderId(), BkExecType.CANCELED, remaining, 0, "no more liquidity"));
                } else {
                    resting.put(order.orderId(), order);
                    restingMeta.add(new long[] {order.orderId(), remaining});
                }
            }
        }
        out.forEach(listener);
    }

    @Override
    public void cancel(long orderId) {
        List<BkExecReport> out = new ArrayList<>();
        synchronized (this) {
            for (Iterator<long[]> it = restingMeta.iterator(); it.hasNext(); ) {
                long[] meta = it.next();
                if (meta[0] == orderId) {
                    out.add(report(orderId, BkExecType.CANCELED, meta[1], 0, "canceled by request"));
                    it.remove();
                    resting.remove(orderId);
                }
            }
        }
        out.forEach(listener);
    }

    /** Demo hook: a venue resends a report after a reconnect. */
    void resend(BkExecReport r) {
        listener.accept(r);
    }

    private long match(BkVenueOrder o, long remaining, List<BkExecReport> out) {
        long[] q = quotes.get(o.symbol());
        if (q == null) {
            return remaining;
        }
        boolean buy = o.side() == BkSide.BUY;
        long px = buy ? q[2] : q[0];
        int sizeIdx = buy ? 3 : 1;
        boolean marketable = o.type() == BkOrderType.MARKET || (buy ? o.limitCents() >= px : o.limitCents() <= px);
        if (!marketable || q[sizeIdx] == 0) {
            return remaining;
        }
        long fill = Math.min(remaining, q[sizeIdx]);
        q[sizeIdx] -= fill;
        out.add(report(o.orderId(), BkExecType.FILL, fill, px, null));
        return remaining - fill;
    }

    private BkExecReport report(long orderId, BkExecType type, long qty, long px, String text) {
        return new BkExecReport("EX" + execSeq.incrementAndGet(), orderId, type, qty, px, text);
    }
}

// -----------------------------------------------------------------------------
//  SETTLEMENT: T+1 on business days
// -----------------------------------------------------------------------------
class BkCalendar {
    private final Set<LocalDate> holidays;

    BkCalendar(Set<LocalDate> holidays) {
        this.holidays = holidays;
    }

    boolean isBusinessDay(LocalDate d) {
        return d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY
                && !holidays.contains(d);
    }

    LocalDate plusBusinessDays(LocalDate d, int n) {
        LocalDate x = d;
        int added = 0;
        while (added < n) {
            x = x.plusDays(1);
            if (isBusinessDay(x)) {
                added++;
            }
        }
        return x;
    }
}

// -----------------------------------------------------------------------------
//  FACADE / OMS
// -----------------------------------------------------------------------------
class BkBrokerage {
    private static final long MARKET_COLLAR_PERCENT = 5;   // assumption: reserve ask + 5% for market buys

    private final BkVenue venue;
    private final BkCalendar calendar;
    private final boolean dedupeExecIds;
    private final Map<String, BkAccount> accounts = new ConcurrentHashMap<>();
    private final Map<Long, BkOrder> orders = new ConcurrentHashMap<>();
    private final Map<String, BkOrder> byClientOrderId = new ConcurrentHashMap<>();
    private final Set<String> appliedExecIds = ConcurrentHashMap.newKeySet();
    private final List<BkTrade> pendingSettlement = new ArrayList<>();
    private final AtomicLong orderSeq = new AtomicLong(1000);
    private final AtomicLong duplicatesIgnored = new AtomicLong();
    private volatile LocalDate tradeDate;

    BkBrokerage(BkVenue venue, BkCalendar calendar, LocalDate tradeDate, boolean dedupeExecIds) {
        this.venue = venue;
        this.calendar = calendar;
        this.tradeDate = tradeDate;
        this.dedupeExecIds = dedupeExecIds;
        venue.setReportListener(this::onExecutionReport);
    }

    BkAccount open(String id, long depositCents) {
        BkAccount a = new BkAccount(id, depositCents);
        accounts.put(id, a);
        return a;
    }

    BkAccount account(String id) {
        return accounts.get(id);
    }

    BkOrder order(long id) {
        return orders.get(id);
    }

    void setTradeDate(LocalDate d) {
        tradeDate = d;
    }

    long duplicatesIgnored() {
        return duplicatesIgnored.get();
    }

    /** Order entry: idempotent on clientOrderId, reserve under the account lock, route outside it. */
    BkOrder placeOrder(String accountId, String clientOrderId, String symbol, BkSide side,
                       BkOrderType type, long qty, long limitCents) {
        BkAccount acct = accounts.get(accountId);
        BkOrder order;
        acct.lock.lock();
        try {
            BkOrder existing = byClientOrderId.get(accountId + "/" + clientOrderId);
            if (existing != null) {
                return existing;                                  // retry: same order back
            }
            order = new BkOrder(orderSeq.incrementAndGet(), clientOrderId, accountId, symbol, side, type, qty, limitCents);
            byClientOrderId.put(accountId + "/" + clientOrderId, order);
            orders.put(order.id, order);
            String reason = reserve(acct, order);
            if (reason != null) {
                order.status = BkOrderStatus.REJECTED;
                order.rejectReason = reason;
                return order;
            }
        } finally {
            acct.lock.unlock();
        }
        venue.submit(new BkVenueOrder(order.id, symbol, side, type, qty, limitCents));   // no lock held
        return order;
    }

    /** Returns a reject reason, or null after reserving cash or shares. Caller holds the account lock. */
    private String reserve(BkAccount acct, BkOrder order) {
        if (order.qty <= 0) {
            return "quantity must be positive";
        }
        if (order.type == BkOrderType.LIMIT && order.limitCents <= 0) {
            return "limit price required";
        }
        if (order.side == BkSide.BUY) {
            long perShare;
            if (order.type == BkOrderType.LIMIT) {
                perShare = order.limitCents;
            } else {
                long[] q = venue.quote(order.symbol);
                if (q == null) {
                    return "no quote for " + order.symbol;
                }
                perShare = q[2] * (100 + MARKET_COLLAR_PERCENT) / 100;
            }
            long need = Math.multiplyExact(perShare, order.qty);
            if (need > acct.buyingPower()) {
                return "insufficient buying power: need " + BkMoney.fmt(need) + ", have " + BkMoney.fmt(acct.buyingPower());
            }
            acct.settledCents -= need;
            acct.reservedCents += need;
            order.reservedCents = need;
        } else {
            BkPosition p = acct.position(order.symbol);
            if (order.qty > p.available()) {
                return "insufficient shares: want " + order.qty + ", available " + p.available();
            }
            p.reservedQty += order.qty;
        }
        return null;
    }

    boolean cancel(long orderId) {
        BkOrder order = orders.get(orderId);
        BkAccount acct = accounts.get(order.accountId);
        acct.lock.lock();
        try {
            if (order.status.isTerminal() || order.status == BkOrderStatus.PENDING_CANCEL) {
                return false;
            }
            order.status = BkOrderStatus.PENDING_CANCEL;   // fills may still arrive until the venue confirms
        } finally {
            acct.lock.unlock();
        }
        venue.cancel(orderId);
        return true;
    }

    /** The venue's callback. Every effect is keyed on execId. */
    void onExecutionReport(BkExecReport r) {
        BkOrder order = orders.get(r.orderId());
        BkAccount acct = accounts.get(order.accountId);
        acct.lock.lock();
        try {
            if (dedupeExecIds && !appliedExecIds.add(r.execId())) {
                duplicatesIgnored.incrementAndGet();
                return;
            }
            if (order.status.isTerminal()) {
                duplicatesIgnored.incrementAndGet();      // late report for a closed order: log, never apply
                return;
            }
            switch (r.type()) {
                case FILL -> applyFill(acct, order, r.qty(), r.priceCents());
                case CANCELED -> close(acct, order, BkOrderStatus.CANCELED);
                case REJECTED -> {
                    order.rejectReason = r.text();
                    close(acct, order, BkOrderStatus.REJECTED);
                }
            }
        } finally {
            acct.lock.unlock();
        }
    }

    private void applyFill(BkAccount acct, BkOrder order, long qty, long px) {
        long amount = Math.multiplyExact(qty, px);
        BkPosition p = acct.position(order.symbol);
        if (order.side == BkSide.BUY) {
            acct.reservedCents -= amount;
            order.reservedCents -= amount;
            acct.boughtCents += amount;
            p.qty += qty;
            p.costCents += amount;
        } else {
            long costOut = p.qty == 0 ? 0 : p.costCents * qty / p.qty;
            p.qty -= qty;
            p.reservedQty -= qty;
            p.costCents -= costOut;
            acct.unsettledCents += amount;
            acct.soldCents += amount;
        }
        order.filledQty += qty;
        order.filledNotionalCents += amount;
        synchronized (pendingSettlement) {
            pendingSettlement.add(new BkTrade(acct.id, order.symbol, order.side, qty, amount,
                    tradeDate, calendar.plusBusinessDays(tradeDate, 1)));
        }
        if (order.filledQty >= order.qty) {
            close(acct, order, BkOrderStatus.FILLED);
        } else if (order.status != BkOrderStatus.PENDING_CANCEL) {
            order.status = BkOrderStatus.PARTIALLY_FILLED;
        }
    }

    /** Terminal transition: release whatever the order still holds. */
    private void close(BkAccount acct, BkOrder order, BkOrderStatus terminal) {
        if (order.side == BkSide.BUY) {
            acct.reservedCents -= order.reservedCents;
            acct.settledCents += order.reservedCents;
            order.reservedCents = 0;
        } else {
            acct.position(order.symbol).reservedQty -= order.remaining();
        }
        order.status = terminal;
    }

    /** End-of-day batch: move sale proceeds whose settle date has arrived into settled cash. */
    int runSettlement(LocalDate asOf) {
        List<BkTrade> due = new ArrayList<>();
        synchronized (pendingSettlement) {
            for (Iterator<BkTrade> it = pendingSettlement.iterator(); it.hasNext(); ) {
                BkTrade t = it.next();
                if (!t.settleDate().isAfter(asOf)) {
                    due.add(t);
                    it.remove();
                }
            }
        }
        for (BkTrade t : due) {
            if (t.side() == BkSide.SELL) {
                BkAccount a = accounts.get(t.accountId());
                a.lock.lock();
                try {
                    a.unsettledCents -= t.amountCents();
                    a.settledCents += t.amountCents();
                } finally {
                    a.lock.unlock();
                }
            }
        }
        return due.size();
    }
}

// -----------------------------------------------------------------------------
//  BROKEN on purpose: buying-power check without a lock
// -----------------------------------------------------------------------------
class BkNaiveAccount {
    long buyingPowerCents;
    int ordersAccepted;

    BkNaiveAccount(long cents) {
        this.buyingPowerCents = cents;
    }

    boolean buy(long costCents, Runnable afterCheck) {
        if (costCents > buyingPowerCents) {
            return false;
        }
        afterCheck.run();                       // the other order runs its check here
        buyingPowerCents -= costCents;          // both subtract: the account goes negative
        ordersAccepted++;
        return true;
    }
}

// -----------------------------------------------------------------------------
//  DEMO
// -----------------------------------------------------------------------------
public class OnlineStockBrokerage {
    private static final LocalDate FRIDAY = LocalDate.of(2026, 11, 20);
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 23);

    public static void main(String[] args) throws Exception {
        System.out.println("=== Online Stock Brokerage: reserve at accept, release on evidence, settle T+1 ===");
        System.out.println();
        demonstrateDoubleSpend();
        demonstrateDuplicateExecution();
        demonstrateLimitOrderLifecycle();
        demonstrateCancelRace();
        demonstrateSellAndSettle();
        demonstrateIdempotentEntry();
        demonstrateConcurrentStress();
    }

    private static BkBrokerage newBroker(BkSimulatedVenue venue, boolean dedupe) {
        return new BkBrokerage(venue, new BkCalendar(Set.of()), FRIDAY, dedupe);
    }

    private static void demonstrateDoubleSpend() throws Exception {
        System.out.println("--- Broken: two buy orders check buying power without a lock ---");
        BkNaiveAccount naive = new BkNaiveAccount(1_000_000);
        CountDownLatch bothChecked = new CountDownLatch(2);
        Runnable pause = () -> {
            bothChecked.countDown();
            try {
                bothChecked.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        Thread a = new Thread(() -> naive.buy(800_000, pause));
        Thread b = new Thread(() -> naive.buy(800_000, pause));
        a.start();
        b.start();
        a.join();
        b.join();
        System.out.println("buying power $10,000.00; two $8,000.00 orders both passed the check");
        System.out.println("orders accepted: " + naive.ordersAccepted + ", buying power now "
                + BkMoney.fmt(naive.buyingPowerCents) + "   <- spent money the account never had");

        BkSimulatedVenue venue = new BkSimulatedVenue();
        BkBrokerage broker = newBroker(venue, true);
        broker.open("ACC-1", 1_000_000);
        BkOrder o1 = broker.placeOrder("ACC-1", "c1", "ACME", BkSide.BUY, BkOrderType.LIMIT, 80, 10_000);
        BkOrder o2 = broker.placeOrder("ACC-1", "c2", "ACME", BkSide.BUY, BkOrderType.LIMIT, 80, 10_000);
        System.out.println("Fix (check and reserve under the account lock):");
        System.out.println("  " + o1.describe());
        System.out.println("  " + o2.describe());
        System.out.println("  " + broker.account("ACC-1").describe());
        System.out.println();
    }

    private static void demonstrateDuplicateExecution() {
        System.out.println("--- Broken: the venue resends a fill after a reconnect ---");
        for (boolean dedupe : new boolean[] {false, true}) {
            BkSimulatedVenue venue = new BkSimulatedVenue();
            BkBrokerage broker = newBroker(venue, dedupe);
            broker.open("ACC-2", 2_000_000);
            venue.setQuote("ACME", 9_990, 1_000, 10_000, 60);           // only 60 shares offered
            BkOrder o = broker.placeOrder("ACC-2", "d1", "ACME", BkSide.BUY, BkOrderType.LIMIT, 100, 10_050);
            BkExecReport firstFill = new BkExecReport("EX1", o.id, BkExecType.FILL, 60, 10_000, null);
            venue.resend(firstFill);                                     // same execId again
            BkAccount acct = broker.account("ACC-2");
            System.out.println((dedupe ? "Fix (dedupe on execId):  " : "no dedupe:               ")
                    + "filled=" + o.filledQty + "/100  status=" + o.status + "  position=" + acct.position("ACME").qty
                    + "  paid=" + BkMoney.fmt(acct.boughtCents) + "  (the venue filled 60)");
        }
        System.out.println();
    }

    private static void demonstrateLimitOrderLifecycle() {
        System.out.println("--- Step 1: limit buy, partial fill, price moves, full fill, unused cash released ---");
        BkSimulatedVenue venue = new BkSimulatedVenue();
        BkBrokerage broker = newBroker(venue, true);
        broker.open("ACC-3", 2_000_000);
        venue.setQuote("ACME", 9_990, 1_000, 10_000, 60);
        BkOrder o = broker.placeOrder("ACC-3", "L1", "ACME", BkSide.BUY, BkOrderType.LIMIT, 100, 10_050);
        System.out.println("reserved 100 x $100.50 = " + BkMoney.fmt(100 * 10_050));
        System.out.println("after submit: " + o.describe());
        System.out.println("  " + broker.account("ACC-3").describe());
        venue.setQuote("ACME", 9_980, 1_000, 9_990, 500);
        System.out.println("ask drops to $99.90: " + o.describe());
        System.out.println("  " + broker.account("ACC-3").describe());
        System.out.println("cost 60 x $100.00 + 40 x $99.90 = " + BkMoney.fmt(60 * 10_000 + 40 * 9_990)
                + "; reservation released: " + BkMoney.fmt(100 * 10_050 - (60 * 10_000 + 40 * 9_990)));
        BkOrder market = broker.placeOrder("ACC-3", "L2", "ACME", BkSide.BUY, BkOrderType.MARKET, 90, 0);
        System.out.println("market buy 90: reserve 90 x ($99.90 ask + 5% collar = $104.89) = " + BkMoney.fmt(90 * 10_489)
                + ", then " + market.describe());
        System.out.println("  " + broker.account("ACC-3").describe());
        BkOrder tooBig = broker.placeOrder("ACC-3", "L3", "ACME", BkSide.BUY, BkOrderType.MARKET, 1_000, 0);
        System.out.println("market buy 1,000: " + tooBig.describe());
        System.out.println();
    }

    private static void demonstrateCancelRace() {
        System.out.println("--- Step 2: cancel while fills are still arriving ---");
        BkSimulatedVenue venue = new BkSimulatedVenue();
        BkBrokerage broker = newBroker(venue, true);
        broker.open("ACC-4", 2_000_000);
        venue.setQuote("ACME", 9_900, 1_000, 10_100, 0);
        BkOrder o = broker.placeOrder("ACC-4", "K1", "ACME", BkSide.BUY, BkOrderType.LIMIT, 100, 10_000);
        System.out.println("resting: " + o.describe());
        venue.setQuote("ACME", 9_900, 1_000, 10_000, 30);              // 30 shares trade before the cancel lands
        System.out.println("30 fill:  " + o.describe());
        broker.cancel(o.id);
        System.out.println("cancel:   " + o.describe());
        System.out.println("  " + broker.account("ACC-4").describe()
                + "   (30 x $100.00 spent, the other 70 x $100.00 released)");
        System.out.println("second cancel accepted? " + broker.cancel(o.id));
        System.out.println();
    }

    private static void demonstrateSellAndSettle() {
        System.out.println("--- Step 3: sell on Friday, proceeds settle T+1 = Monday ---");
        BkSimulatedVenue venue = new BkSimulatedVenue();
        BkBrokerage broker = newBroker(venue, true);
        broker.open("ACC-5", 1_000_000);
        venue.setQuote("ACME", 10_000, 1_000, 10_010, 1_000);
        broker.placeOrder("ACC-5", "S0", "ACME", BkSide.BUY, BkOrderType.LIMIT, 50, 10_010);
        BkOrder sell = broker.placeOrder("ACC-5", "S1", "ACME", BkSide.SELL, BkOrderType.LIMIT, 50, 10_000);
        System.out.println("bought 50, sold 50 on " + FRIDAY + " (" + FRIDAY.getDayOfWeek() + "): " + sell.describe());
        System.out.println("  " + broker.account("ACC-5").describe());
        BkOrder oversell = broker.placeOrder("ACC-5", "S2", "ACME", BkSide.SELL, BkOrderType.MARKET, 1, 0);
        System.out.println("sell 1 more: " + oversell.describe());
        BkOrder spendUnsettled = broker.placeOrder("ACC-5", "S3", "ACME", BkSide.BUY, BkOrderType.LIMIT, 60, 10_010);
        System.out.println("buy 60 @ $100.10 with unsettled proceeds: " + spendUnsettled.describe());
        System.out.println("settlement run for " + FRIDAY.plusDays(1) + " (Sat): settled " + broker.runSettlement(FRIDAY.plusDays(1)) + " trades");
        System.out.println("settlement run for " + MONDAY + " (Mon): settled " + broker.runSettlement(MONDAY) + " trades");
        System.out.println("  " + broker.account("ACC-5").describe());
        BkCalendar withHoliday = new BkCalendar(Set.of(LocalDate.of(2026, 11, 26)));
        System.out.println("T+1 of Wed 2026-11-25 with Thu 2026-11-26 as a holiday: "
                + withHoliday.plusBusinessDays(LocalDate.of(2026, 11, 25), 1));
        System.out.println();
    }

    private static void demonstrateIdempotentEntry() {
        System.out.println("--- Step 4: the app retries an order after a timeout ---");
        BkSimulatedVenue venue = new BkSimulatedVenue();
        BkBrokerage broker = newBroker(venue, true);
        broker.open("ACC-6", 1_000_000);
        venue.setQuote("ACME", 9_990, 0, 10_100, 0);
        BkOrder first = broker.placeOrder("ACC-6", "app-uuid-42", "ACME", BkSide.BUY, BkOrderType.LIMIT, 10, 10_000);
        BkOrder retry = broker.placeOrder("ACC-6", "app-uuid-42", "ACME", BkSide.BUY, BkOrderType.LIMIT, 10, 10_000);
        System.out.println("first  -> order #" + first.id + ", retry -> order #" + retry.id + ", same object? " + (first == retry));
        System.out.println("  " + broker.account("ACC-6").describe() + "   (reserved once, not twice)");
        System.out.println();
    }

    private static void demonstrateConcurrentStress() throws Exception {
        System.out.println("--- Step 5: 8 threads x 1,000 random market orders across 4 accounts ---");
        BkSimulatedVenue venue = new BkSimulatedVenue();
        BkBrokerage broker = newBroker(venue, true);
        String[] ids = {"A", "B", "C", "D"};
        for (String id : ids) {
            broker.open(id, 5_000_000);
        }
        venue.setQuote("ACME", 9_990, Long.MAX_VALUE / 4, 10_010, Long.MAX_VALUE / 4);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        AtomicLong rejected = new AtomicLong();
        for (int t = 0; t < 8; t++) {
            final int tid = t;
            pool.submit(() -> {
                Random rnd = new Random(tid);
                for (int i = 0; i < 1_000; i++) {
                    String acct = ids[rnd.nextInt(ids.length)];
                    BkSide side = rnd.nextBoolean() ? BkSide.BUY : BkSide.SELL;
                    BkOrder o = broker.placeOrder(acct, "t" + tid + "-" + i, "ACME", side, BkOrderType.MARKET,
                            1 + rnd.nextInt(80), 0);
                    if (o.status == BkOrderStatus.REJECTED) {
                        rejected.incrementAndGet();
                    }
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(1, TimeUnit.MINUTES);
        boolean allConserved = true;
        boolean noneNegative = true;
        boolean nothingHeld = true;
        for (String id : ids) {
            BkAccount a = broker.account(id);
            BkPosition p = a.position("ACME");
            allConserved &= a.conserved();
            noneNegative &= a.settledCents >= 0 && a.unsettledCents >= 0 && a.reservedCents >= 0 && p.qty >= 0;
            nothingHeld &= a.reservedCents == 0 && p.reservedQty == 0;
        }
        System.out.println("orders: 8,000; rejected for buying power or shares: " + (rejected.get() > 0 ? "some" : "none"));
        System.out.println("every account conserves cash (settled + unsettled + reserved + bought - sold == deposit)? " + allConserved);
        System.out.println("no negative cash bucket or position? " + noneNegative);
        System.out.println("no cash or shares left held after all orders closed? " + nothingHeld);
        System.out.println("duplicate or late reports ignored: " + broker.duplicatesIgnored());
    }
}
