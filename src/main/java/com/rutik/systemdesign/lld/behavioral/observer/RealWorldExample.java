package com.rutik.systemdesign.lld.behavioral.observer; /**
 * Observer Pattern - Real World Example
 *
 * Scenario: Stock Price Notification System
 *
 * A StockMarket (Subject) tracks real-time prices for multiple ticker symbols.
 * When a stock's price changes, all registered observers are notified immediately:
 *
 *   - PriceAlertObserver   : triggers an alert when a stock crosses a threshold.
 *   - PortfolioObserver    : recalculates portfolio value on any price change.
 *   - TradingBotObserver   : executes simulated buy/sell orders based on rules.
 *   - MarketDashboard      : displays a live snapshot of watched stocks.
 *
 * Observers can watch specific tickers or all tickers (wildcard). They can be
 * attached and detached at runtime without touching the StockMarket class.
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ─── Domain Types ─────────────────────────────────────────────────────────────

/**
 * Immutable event published whenever a stock price changes.
 */
class PriceChangeEvent {
    public final String ticker;
    public final double oldPrice;
    public final double newPrice;
    public final double changePercent;

    public PriceChangeEvent(String ticker, double oldPrice, double newPrice) {
        this.ticker        = ticker;
        this.oldPrice      = oldPrice;
        this.newPrice      = newPrice;
        this.changePercent = oldPrice == 0 ? 0
                : ((newPrice - oldPrice) / oldPrice) * 100.0;
    }

    @Override
    public String toString() {
        return String.format("%s: $%.2f -> $%.2f (%+.2f%%)",
                ticker, oldPrice, newPrice, changePercent);
    }
}

// ─── Observer Interface ───────────────────────────────────────────────────────

/**
 * Any component interested in stock price changes implements this interface.
 */
interface StockObserver {
    void onPriceChange(PriceChangeEvent event);
    String observerName();
}

// ─── Subject: StockMarket ─────────────────────────────────────────────────────

/**
 * Maintains real-time stock prices and notifies observers on every price change.
 *
 * Observers may subscribe to a specific ticker (e.g. "AAPL") or to all
 * tickers using the wildcard "*".
 */
class StockMarket {
    private final Map<String, Double>            prices    = new HashMap<>();
    private final Map<String, List<StockObserver>> listeners = new HashMap<>();

    // ── Subscription management ───────────────────────────────────────────────

    /**
     * Subscribes an observer to a specific ticker symbol.
     * Use ticker="*" to receive all price change events.
     */
    public void subscribe(String ticker, StockObserver observer) {
        listeners.computeIfAbsent(ticker, k -> new ArrayList<>()).add(observer);
        System.out.println("[StockMarket] " + observer.observerName()
                + " subscribed to " + ticker);
    }

    public void unsubscribe(String ticker, StockObserver observer) {
        List<StockObserver> list = listeners.get(ticker);
        if (list != null) {
            list.remove(observer);
            System.out.println("[StockMarket] " + observer.observerName()
                    + " unsubscribed from " + ticker);
        }
    }

    // ── Price update (triggers notifications) ─────────────────────────────────

    /**
     * Updates the price of a ticker and notifies relevant observers.
     * Both ticker-specific subscribers AND wildcard ("*") subscribers are notified.
     */
    public void updatePrice(String ticker, double newPrice) {
        double oldPrice = prices.getOrDefault(ticker, 0.0);
        prices.put(ticker, newPrice);

        if (oldPrice == newPrice) return; // no change — no notification

        PriceChangeEvent event = new PriceChangeEvent(ticker, oldPrice, newPrice);
        System.out.println("\n[StockMarket] Price update: " + event);

        // Notify ticker-specific observers
        List<StockObserver> tickerObservers = listeners.getOrDefault(ticker, List.of());
        for (StockObserver o : tickerObservers) {
            o.onPriceChange(event);
        }

        // Notify wildcard observers (avoiding duplicates)
        List<StockObserver> wildcardObservers = listeners.getOrDefault("*", List.of());
        for (StockObserver o : wildcardObservers) {
            if (!tickerObservers.contains(o)) {
                o.onPriceChange(event);
            }
        }
    }

    public double getPrice(String ticker) {
        return prices.getOrDefault(ticker, 0.0);
    }
}

// ─── Concrete Observers ───────────────────────────────────────────────────────

/**
 * 1. PriceAlertObserver
 *
 * Fires an alert when a stock crosses a user-defined price threshold.
 */
class PriceAlertObserver implements StockObserver {
    private final String name;
    private final String watchTicker;
    private final double threshold;
    private final boolean alertAbove; // true = alert when price goes above threshold

    public PriceAlertObserver(String name, String watchTicker,
                              double threshold, boolean alertAbove) {
        this.name        = name;
        this.watchTicker = watchTicker;
        this.threshold   = threshold;
        this.alertAbove  = alertAbove;
    }

    @Override
    public void onPriceChange(PriceChangeEvent event) {
        if (!event.ticker.equals(watchTicker)) return;

        boolean triggered = alertAbove
                ? event.newPrice > threshold
                : event.newPrice < threshold;

        if (triggered) {
            String direction = alertAbove ? "above" : "below";
            System.out.printf("  [ALERT] %s: %s crossed %s $%.2f! Current: $%.2f%n",
                    name, watchTicker, direction, threshold, event.newPrice);
        }
    }

    @Override public String observerName() { return name; }
}

/**
 * 2. PortfolioObserver
 *
 * Tracks a user's holdings and recalculates total portfolio value on price updates.
 */
class PortfolioObserver implements StockObserver {
    private final String             ownerName;
    private final Map<String, Integer> holdings = new HashMap<>(); // ticker -> shares

    public PortfolioObserver(String ownerName) {
        this.ownerName = ownerName;
    }

    public void addHolding(String ticker, int shares) {
        holdings.put(ticker, holdings.getOrDefault(ticker, 0) + shares);
    }

    private final Map<String, Double> latestPrices = new HashMap<>();

    @Override
    public void onPriceChange(PriceChangeEvent event) {
        if (!holdings.containsKey(event.ticker)) return;
        latestPrices.put(event.ticker, event.newPrice);

        double totalValue = 0;
        for (Map.Entry<String, Integer> entry : holdings.entrySet()) {
            double price = latestPrices.getOrDefault(entry.getKey(), 0.0);
            totalValue  += price * entry.getValue();
        }

        System.out.printf("  [Portfolio:%s] %s changed: portfolio value = $%.2f%n",
                ownerName, event.ticker, totalValue);
    }

    @Override public String observerName() { return "Portfolio:" + ownerName; }
}

/**
 * 3. TradingBotObserver
 *
 * A simple rules-based trading bot that buys on dips and sells on rallies.
 */
class TradingBotObserver implements StockObserver {
    private final String botName;
    private final double buyThresholdPercent;  // buy if price drops by this %
    private final double sellThresholdPercent; // sell if price rises by this %

    public TradingBotObserver(String botName,
                              double buyThresholdPercent,
                              double sellThresholdPercent) {
        this.botName              = botName;
        this.buyThresholdPercent  = buyThresholdPercent;
        this.sellThresholdPercent = sellThresholdPercent;
    }

    @Override
    public void onPriceChange(PriceChangeEvent event) {
        if (event.changePercent <= -buyThresholdPercent) {
            System.out.printf("  [Bot:%s] BUY signal for %s at $%.2f (drop of %.2f%%)%n",
                    botName, event.ticker, event.newPrice, event.changePercent);
        } else if (event.changePercent >= sellThresholdPercent) {
            System.out.printf("  [Bot:%s] SELL signal for %s at $%.2f (rise of %.2f%%)%n",
                    botName, event.ticker, event.newPrice, event.changePercent);
        }
    }

    @Override public String observerName() { return "Bot:" + botName; }
}

/**
 * 4. MarketDashboard
 *
 * Displays a compact one-line update for every price change it observes.
 * Subscribes to "*" (all tickers).
 */
class MarketDashboard implements StockObserver {
    @Override
    public void onPriceChange(PriceChangeEvent event) {
        String arrow = event.changePercent >= 0 ? "↑" : "↓";
        System.out.printf("  [Dashboard] %s %s $%.2f (%+.2f%%)%n",
                event.ticker, arrow, event.newPrice, event.changePercent);
    }

    @Override public String observerName() { return "MarketDashboard"; }
}

// ─── Main ─────────────────────────────────────────────────────────────────────

public class RealWorldExample {

    public static void main(String[] args) {
        System.out.println("=== Stock Price Notification System (Observer Pattern) ===\n");

        StockMarket market = new StockMarket();

        // ── Observers ────────────────────────────────────────────────────────
        MarketDashboard dashboard = new MarketDashboard();

        // Alice wants an alert if AAPL exceeds $190
        PriceAlertObserver aliceAlert = new PriceAlertObserver(
                "Alice-AAPL-alert", "AAPL", 190.00, true);

        // Bob wants an alert if TSLA drops below $200
        PriceAlertObserver bobAlert = new PriceAlertObserver(
                "Bob-TSLA-alert", "TSLA", 200.00, false);

        // Alice's portfolio: 10 AAPL + 5 GOOGL
        PortfolioObserver alicePortfolio = new PortfolioObserver("Alice");
        alicePortfolio.addHolding("AAPL",  10);
        alicePortfolio.addHolding("GOOGL",  5);

        // A trading bot that buys on 2% drops and sells on 3% rises
        TradingBotObserver bot = new TradingBotObserver("MomentumBot", 2.0, 3.0);

        // ── Subscriptions ────────────────────────────────────────────────────
        System.out.println("--- Setting up subscriptions ---");
        market.subscribe("*",     dashboard);     // dashboard watches everything
        market.subscribe("AAPL",  aliceAlert);
        market.subscribe("TSLA",  bobAlert);
        market.subscribe("AAPL",  alicePortfolio);
        market.subscribe("GOOGL", alicePortfolio);
        market.subscribe("AAPL",  bot);
        market.subscribe("TSLA",  bot);
        market.subscribe("GOOGL", bot);

        // ── Simulate market activity ──────────────────────────────────────────
        System.out.println("\n--- Market opens ---");
        market.updatePrice("AAPL",  185.00);
        market.updatePrice("TSLA",  220.00);
        market.updatePrice("GOOGL", 140.00);

        System.out.println("\n--- AAPL rallies past alert threshold ---");
        market.updatePrice("AAPL", 192.50); // triggers Alice's alert + bot sell

        System.out.println("\n--- TSLA drops sharply ---");
        market.updatePrice("TSLA", 197.00); // triggers Bob's alert + bot buy

        System.out.println("\n--- GOOGL moderate move ---");
        market.updatePrice("GOOGL", 143.20);

        System.out.println("\n--- AAPL dips (bot buy signal) ---");
        market.updatePrice("AAPL", 181.50); // >2% drop from 192.50

        // ── Unsubscribe Alice's alert ---
        System.out.println("\n--- Alice removes her AAPL alert ---");
        market.unsubscribe("AAPL", aliceAlert);

        System.out.println("\n--- AAPL surges again (Alice alert should NOT fire) ---");
        market.updatePrice("AAPL", 195.00);
    }
}
