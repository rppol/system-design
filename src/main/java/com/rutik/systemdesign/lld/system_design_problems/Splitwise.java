package com.rutik.systemdesign.lld.system_design_problems;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

// =============================================================================
//  SPLITWISE (Expense Sharing) — Low-Level Design
//  Patterns used:
//    - Strategy : SplitStrategy (EqualSplitStrategy, ExactSplitStrategy, PercentageSplitStrategy)
//    - Factory  : SplitStrategyFactory (maps SplitType -> SplitStrategy)
//  Core algorithm:
//    - DebtSimplifier : greedy min-cashflow using two max-heaps (creditors / debtors)
// =============================================================================

// ─────────────────────────────────────────────
//  ENUMS
// ─────────────────────────────────────────────

enum SplitType { EQUAL, EXACT, PERCENTAGE }

// ─────────────────────────────────────────────
//  USER
// ─────────────────────────────────────────────

class User {
    private final String id;
    private final String name;

    public User(String id, String name) {
        this.id   = id;
        this.name = name;
    }

    public String getId()   { return id; }
    public String getName() { return name; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        return id.equals(((User) o).id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() { return name; }
}

// ─────────────────────────────────────────────
//  SPLIT — value object: one participant's share of an expense
// ─────────────────────────────────────────────

class Split {
    private final User       user;
    private final BigDecimal amount;

    public Split(User user, BigDecimal amount) {
        this.user   = user;
        this.amount = amount;
    }

    public User       getUser()   { return user; }
    public BigDecimal getAmount() { return amount; }

    @Override
    public String toString() {
        return user.getName() + "=$" + amount;
    }
}

// ─────────────────────────────────────────────
//  STRATEGY PATTERN — SplitStrategy
//  Each concrete strategy encapsulates one way of dividing a total amount
//  among participants. All strategies guarantee sum(splits) == totalAmount
//  exactly (to the cent), fixing rounding remainders deterministically.
// ─────────────────────────────────────────────

interface SplitStrategy {
    /**
     * Computes how `totalAmount` is divided among `participants`.
     * `extraData` carries strategy-specific input (exact amounts, percentages);
     * may be null/empty for strategies that don't need it (e.g., EQUAL).
     */
    List<Split> computeSplits(BigDecimal totalAmount, List<User> participants, Map<User, BigDecimal> extraData);
}

/**
 * Divides totalAmount evenly among participants. Any leftover cents
 * (from integer division) are distributed one-cent-each to the first
 * `remainder` participants in iteration order, guaranteeing the sum
 * of splits equals totalAmount exactly.
 */
class EqualSplitStrategy implements SplitStrategy {
    @Override
    public List<Split> computeSplits(BigDecimal totalAmount, List<User> participants, Map<User, BigDecimal> extraData) {
        int n = participants.size();
        if (n == 0) throw new IllegalArgumentException("Cannot split among zero participants.");

        BigDecimal totalCents = totalAmount.movePointRight(2).setScale(0, RoundingMode.HALF_UP);
        BigDecimal[] divRem   = totalCents.divideAndRemainder(BigDecimal.valueOf(n));
        long baseCents        = divRem[0].longValueExact();
        int  remainderCents   = divRem[1].intValueExact();

        List<Split> splits = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            long cents = baseCents + (i < remainderCents ? 1 : 0); // first `remainder` users get +1 cent
            BigDecimal amount = BigDecimal.valueOf(cents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
            splits.add(new Split(participants.get(i), amount));
        }
        return splits;
    }
}

/**
 * Uses caller-provided exact amounts (extraData: User -> amount).
 * Validates the provided amounts sum to totalAmount within a 1-cent tolerance.
 */
class ExactSplitStrategy implements SplitStrategy {
    @Override
    public List<Split> computeSplits(BigDecimal totalAmount, List<User> participants, Map<User, BigDecimal> extraData) {
        if (extraData == null || extraData.size() != participants.size()) {
            throw new IllegalArgumentException("ExactSplitStrategy requires an amount for every participant.");
        }

        BigDecimal sum = BigDecimal.ZERO;
        List<Split> splits = new ArrayList<>();
        for (User u : participants) {
            BigDecimal amount = extraData.get(u);
            if (amount == null) {
                throw new IllegalArgumentException("Missing exact amount for participant " + u.getName());
            }
            amount = amount.setScale(2, RoundingMode.HALF_UP);
            sum    = sum.add(amount);
            splits.add(new Split(u, amount));
        }

        BigDecimal diff = sum.subtract(totalAmount).abs();
        if (diff.compareTo(new BigDecimal("0.01")) > 0) {
            throw new IllegalArgumentException(
                    "Exact split amounts (" + sum + ") do not sum to total (" + totalAmount + ").");
        }
        return splits;
    }
}

/**
 * Uses caller-provided percentages (extraData: User -> percentage, e.g., 50 for 50%).
 * Validates percentages sum to 100 (within tolerance), computes each share as
 * totalAmount * pct / 100, then fixes the rounding remainder by adjusting the
 * largest share so the sum equals totalAmount exactly.
 */
class PercentageSplitStrategy implements SplitStrategy {
    @Override
    public List<Split> computeSplits(BigDecimal totalAmount, List<User> participants, Map<User, BigDecimal> extraData) {
        if (extraData == null || extraData.size() != participants.size()) {
            throw new IllegalArgumentException("PercentageSplitStrategy requires a percentage for every participant.");
        }

        BigDecimal pctSum = BigDecimal.ZERO;
        for (User u : participants) {
            BigDecimal pct = extraData.get(u);
            if (pct == null) {
                throw new IllegalArgumentException("Missing percentage for participant " + u.getName());
            }
            pctSum = pctSum.add(pct);
        }
        if (pctSum.subtract(new BigDecimal("100")).abs().compareTo(new BigDecimal("0.01")) > 0) {
            throw new IllegalArgumentException("Percentages must sum to 100, got " + pctSum);
        }

        List<Split> splits = new ArrayList<>();
        BigDecimal sumSoFar = BigDecimal.ZERO;
        int largestIdx = 0;
        BigDecimal largestPct = BigDecimal.valueOf(-1);

        for (int i = 0; i < participants.size(); i++) {
            User u = participants.get(i);
            BigDecimal pct    = extraData.get(u);
            BigDecimal amount = totalAmount.multiply(pct)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            splits.add(new Split(u, amount));
            sumSoFar = sumSoFar.add(amount);
            if (pct.compareTo(largestPct) > 0) {
                largestPct = pct;
                largestIdx = i;
            }
        }

        // Fix rounding remainder by adjusting the largest share
        BigDecimal remainder = totalAmount.subtract(sumSoFar);
        if (remainder.compareTo(BigDecimal.ZERO) != 0) {
            Split largest = splits.get(largestIdx);
            splits.set(largestIdx, new Split(largest.getUser(), largest.getAmount().add(remainder)));
        }
        return splits;
    }
}

// ─────────────────────────────────────────────
//  FACTORY PATTERN — SplitStrategyFactory
//  Maps SplitType enum to the right (stateless, reusable) strategy instance.
// ─────────────────────────────────────────────

class SplitStrategyFactory {
    private static final SplitStrategy EQUAL_STRATEGY      = new EqualSplitStrategy();
    private static final SplitStrategy EXACT_STRATEGY      = new ExactSplitStrategy();
    private static final SplitStrategy PERCENTAGE_STRATEGY = new PercentageSplitStrategy();

    public static SplitStrategy create(SplitType type) {
        return switch (type) {
            case EQUAL      -> EQUAL_STRATEGY;
            case EXACT      -> EXACT_STRATEGY;
            case PERCENTAGE -> PERCENTAGE_STRATEGY;
        };
    }
}

// ─────────────────────────────────────────────
//  EXPENSE
// ─────────────────────────────────────────────

class Expense {
    private static int counter = 1000;

    private final String         id;
    private final String         description;
    private final User           payer;
    private final BigDecimal     totalAmount;
    private final List<Split>    splits;
    private final LocalDateTime  timestamp;

    public Expense(String description, User payer, BigDecimal totalAmount, List<Split> splits) {
        this.id          = "EXP-" + (++counter);
        this.description = description;
        this.payer       = payer;
        this.totalAmount = totalAmount;
        this.splits      = splits;
        this.timestamp   = LocalDateTime.now();
    }

    public String        getId()          { return id; }
    public String        getDescription() { return description; }
    public User          getPayer()       { return payer; }
    public BigDecimal    getTotalAmount() { return totalAmount; }
    public List<Split>   getSplits()      { return splits; }
    public LocalDateTime getTimestamp()   { return timestamp; }

    @Override
    public String toString() {
        return String.format("Expense[%s | %s | payer=%s | total=$%s | splits=%s]",
                id, description, payer.getName(), totalAmount, splits);
    }
}

// ─────────────────────────────────────────────
//  BALANCE SHEET (LEDGER)
//  userId -> otherUserId -> net amount the *other* user owes *this* user.
//  Positive value: otherUser owes userId. Negative: userId owes otherUser.
//  Always maintained symmetric: balances[a][b] == -balances[b][a]
// ─────────────────────────────────────────────

class BalanceSheet {
    private final Map<String, Map<String, BigDecimal>> balances = new HashMap<>();

    /**
     * Records that `debtor` owes `creditor` an additional `amount`
     * (amount > 0). Updates both directions symmetrically.
     */
    public void updateBalance(User creditor, User debtor, BigDecimal amount) {
        if (creditor.equals(debtor) || amount.compareTo(BigDecimal.ZERO) == 0) return;

        adjust(creditor.getId(), debtor.getId(), amount);
        adjust(debtor.getId(), creditor.getId(), amount.negate());
    }

    private void adjust(String userId, String otherId, BigDecimal delta) {
        Map<String, BigDecimal> row = balances.computeIfAbsent(userId, k -> new HashMap<>());
        BigDecimal current = row.getOrDefault(otherId, BigDecimal.ZERO);
        BigDecimal updated = current.add(delta).setScale(2, RoundingMode.HALF_UP);
        if (updated.compareTo(BigDecimal.ZERO) == 0) {
            row.remove(otherId);
        } else {
            row.put(otherId, updated);
        }
    }

    /** Returns userId's balance with every other user they have a non-zero balance with. */
    public Map<String, BigDecimal> getBalances(String userId) {
        return balances.getOrDefault(userId, Collections.emptyMap());
    }

    /** Net overall balance for a user: sum of all their pairwise balances (positive = net creditor). */
    public BigDecimal getNetBalance(String userId) {
        return getBalances(userId).values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
}

// ─────────────────────────────────────────────
//  TRANSACTION (SETTLEMENT)
// ─────────────────────────────────────────────

class Transaction {
    private final User       fromUser; // pays
    private final User       toUser;   // receives
    private final BigDecimal amount;

    public Transaction(User fromUser, User toUser, BigDecimal amount) {
        this.fromUser = fromUser;
        this.toUser   = toUser;
        this.amount   = amount;
    }

    public User       getFromUser() { return fromUser; }
    public User       getToUser()   { return toUser; }
    public BigDecimal getAmount()   { return amount; }

    @Override
    public String toString() {
        return String.format("%-6s pays %-6s $%s", fromUser.getName(), toUser.getName(), amount);
    }
}

// ─────────────────────────────────────────────
//  DEBT SIMPLIFIER — greedy min-cashflow algorithm
//  Reduces a tangle of pairwise debts to the minimum number of
//  settling transactions using two max-heaps (creditors / debtors).
//  Complexity: O(N log N) for N users.
// ─────────────────────────────────────────────

class DebtSimplifier {

    /** Internal record pairing a user with their net balance magnitude. */
    private record Balance(User user, BigDecimal amount) {}

    /**
     * @param netBalances map of user -> net balance (positive = is owed money overall,
     *                     negative = owes money overall, zero = settled)
     * @return minimal list of transactions that zero out all net balances
     */
    public static List<Transaction> simplify(Map<User, BigDecimal> netBalances) {
        PriorityQueue<Balance> creditors =
                new PriorityQueue<>((a, b) -> b.amount().compareTo(a.amount())); // max-heap by amount owed to them
        PriorityQueue<Balance> debtors =
                new PriorityQueue<>((a, b) -> b.amount().compareTo(a.amount())); // max-heap by amount they owe

        for (Map.Entry<User, BigDecimal> entry : netBalances.entrySet()) {
            BigDecimal amount = entry.getValue().setScale(2, RoundingMode.HALF_UP);
            int cmp = amount.compareTo(BigDecimal.ZERO);
            if (cmp > 0) {
                creditors.add(new Balance(entry.getKey(), amount));
            } else if (cmp < 0) {
                debtors.add(new Balance(entry.getKey(), amount.abs()));
            }
            // cmp == 0 -> already settled, skip
        }

        List<Transaction> transactions = new ArrayList<>();

        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            Balance creditor = creditors.poll();
            Balance debtor   = debtors.poll();

            BigDecimal settleAmount = creditor.amount().min(debtor.amount());
            transactions.add(new Transaction(debtor.user(), creditor.user(), settleAmount));

            BigDecimal creditorRemainder = creditor.amount().subtract(settleAmount);
            BigDecimal debtorRemainder   = debtor.amount().subtract(settleAmount);

            if (creditorRemainder.compareTo(BigDecimal.ZERO) > 0) {
                creditors.add(new Balance(creditor.user(), creditorRemainder));
            }
            if (debtorRemainder.compareTo(BigDecimal.ZERO) > 0) {
                debtors.add(new Balance(debtor.user(), debtorRemainder));
            }
        }

        return transactions;
    }
}

// ─────────────────────────────────────────────
//  GROUP
// ─────────────────────────────────────────────

class Group {
    private final String        id;
    private final String        name;
    private final List<User>    members  = new ArrayList<>();
    private final List<Expense> expenses = new ArrayList<>();
    private final BalanceSheet  ledger   = new BalanceSheet();

    public Group(String id, String name, List<User> initialMembers) {
        this.id = id;
        this.name = name;
        this.members.addAll(initialMembers);
    }

    public void addMember(User user) {
        if (!members.contains(user)) members.add(user);
    }

    public void addExpense(Expense expense) {
        expenses.add(expense);
        // Update the ledger: every non-payer participant now owes the payer their split amount.
        for (Split split : expense.getSplits()) {
            User participant = split.getUser();
            if (!participant.equals(expense.getPayer())) {
                ledger.updateBalance(expense.getPayer(), participant, split.getAmount());
            }
        }
    }

    public String        getId()       { return id; }
    public String        getName()     { return name; }
    public List<User>    getMembers()  { return members; }
    public List<Expense> getExpenses() { return expenses; }
    public BalanceSheet  getLedger()   { return ledger; }
}

// ─────────────────────────────────────────────
//  EXPENSE MANAGER — coordinator
// ─────────────────────────────────────────────

class ExpenseManager {
    private final Map<String, Group> groups = new HashMap<>();
    private int groupCounter = 0;

    public Group createGroup(String name, List<User> members) {
        String id = "GRP-" + (++groupCounter);
        Group group = new Group(id, name, members);
        groups.put(id, group);
        return group;
    }

    /**
     * Adds an expense to a group using the strategy appropriate for `splitType`.
     * `extraData` carries strategy-specific input (exact amounts or percentages);
     * pass null/empty map for EQUAL.
     */
    public Expense addExpense(Group group, String description, User payer, BigDecimal totalAmount,
                               List<User> participants, SplitType splitType, Map<User, BigDecimal> extraData) {
        SplitStrategy strategy = SplitStrategyFactory.create(splitType);
        List<Split> splits = strategy.computeSplits(totalAmount, participants, extraData);

        // Defensive validation: sum of computed splits must equal the total exactly.
        BigDecimal sum = splits.stream().map(Split::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(totalAmount.setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw new IllegalStateException("Split sum (" + sum + ") does not equal total (" + totalAmount + ").");
        }

        Expense expense = new Expense(description, payer, totalAmount, splits);
        group.addExpense(expense);
        return expense;
    }

    /** Returns a user's pairwise balances within a group (otherUserId -> amount owed to this user). */
    public Map<String, BigDecimal> getBalances(Group group, User user) {
        return group.getLedger().getBalances(user.getId());
    }

    /** Computes the minimal set of transactions to settle all debts within a group. */
    public List<Transaction> simplifyGroupDebts(Group group) {
        Map<User, BigDecimal> netBalances = new HashMap<>();
        for (User member : group.getMembers()) {
            netBalances.put(member, group.getLedger().getNetBalance(member.getId()));
        }
        return DebtSimplifier.simplify(netBalances);
    }

    /**
     * Records a settlement payment: `from` pays `to` the given amount,
     * reducing the amount `from` owes `to` (and `to`'s claim on `from`) by that amount.
     */
    public void recordSettlement(Group group, User from, User to, BigDecimal amount) {
        // `to` is owed less by `from` now -> reduce `to`'s credit against `from` by `amount`,
        // which is the same as updateBalance(from, to, amount): from's credit on to increases by amount,
        // canceling out part of what `from` owed `to`.
        group.getLedger().updateBalance(from, to, amount);
    }

    public Collection<Group> getGroups() { return groups.values(); }
}

// ─────────────────────────────────────────────
//  DEMO / MAIN
// ─────────────────────────────────────────────

public class Splitwise {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   Splitwise (Expense Sharing) — LLD Demo");
        System.out.println("========================================\n");

        ExpenseManager manager = new ExpenseManager();

        // 1. Create users
        User alice = new User("U1", "Alice");
        User bob   = new User("U2", "Bob");
        User carol = new User("U3", "Carol");
        User dave  = new User("U4", "Dave");

        // 2. Create group
        Group goaTrip = manager.createGroup("Goa Trip", List.of(alice, bob, carol, dave));
        System.out.println("--- Creating group \"" + goaTrip.getName() + "\" ---");
        System.out.println("Members: Alice, Bob, Carol, Dave\n");

        // 3. Expense 1: Alice pays $120.00 for "Hotel", split EQUAL among all 4
        System.out.println("--- Expense 1: Alice pays $120.00 for \"Hotel\" (EQUAL split among 4) ---");
        Expense e1 = manager.addExpense(goaTrip, "Hotel", alice, new BigDecimal("120.00"),
                List.of(alice, bob, carol, dave), SplitType.EQUAL, null);
        System.out.println("Splits: " + e1.getSplits());
        printBalanceSheet(manager, goaTrip);

        // 4. Expense 2: Bob pays $60.00 for "Taxi", split EXACT (Alice=25, Carol=20, Dave=15)
        System.out.println("\n--- Expense 2: Bob pays $60.00 for \"Taxi\" (EXACT split: Alice=$25.00, Carol=$20.00, Dave=$15.00) ---");
        Map<User, BigDecimal> exactAmounts = new HashMap<>();
        exactAmounts.put(alice, new BigDecimal("25.00"));
        exactAmounts.put(carol, new BigDecimal("20.00"));
        exactAmounts.put(dave,  new BigDecimal("15.00"));
        Expense e2 = manager.addExpense(goaTrip, "Taxi", bob, new BigDecimal("60.00"),
                List.of(alice, carol, dave), SplitType.EXACT, exactAmounts);
        System.out.println("Splits: " + e2.getSplits());
        printBalanceSheet(manager, goaTrip);

        // 5. Expense 3: Carol pays $100.00 for "Groceries", split PERCENTAGE (Alice=50%, Bob=30%, Dave=20%)
        System.out.println("\n--- Expense 3: Carol pays $100.00 for \"Groceries\" (PERCENTAGE split: Alice=50%, Bob=30%, Dave=20%) ---");
        Map<User, BigDecimal> percentages = new HashMap<>();
        percentages.put(alice, new BigDecimal("50"));
        percentages.put(bob,   new BigDecimal("30"));
        percentages.put(dave,  new BigDecimal("20"));
        Expense e3 = manager.addExpense(goaTrip, "Groceries", carol, new BigDecimal("100.00"),
                List.of(alice, bob, dave), SplitType.PERCENTAGE, percentages);
        System.out.println("Splits: " + e3.getSplits());
        printBalanceSheet(manager, goaTrip);

        // 6. Net balance per user
        System.out.println("\n--- Net balance per user (positive = is owed overall) ---");
        for (User u : goaTrip.getMembers()) {
            System.out.printf("  %-6s: %s$%s%n", u.getName(),
                    sign(goaTrip.getLedger().getNetBalance(u.getId())),
                    goaTrip.getLedger().getNetBalance(u.getId()).abs());
        }

        // 7. Simplify debts
        System.out.println("\n--- Simplified debt summary (minimum transactions) ---");
        List<Transaction> transactions = manager.simplifyGroupDebts(goaTrip);
        for (Transaction t : transactions) {
            System.out.println("  " + t);
        }

        // 8. Record a settlement: Dave pays Bob $25.00
        if (!transactions.isEmpty()) {
            Transaction first = transactions.get(0);
            System.out.println("\n--- Recording settlement: " + first.getFromUser().getName()
                    + " pays " + first.getToUser().getName() + " $" + first.getAmount() + " ---");
            manager.recordSettlement(goaTrip, first.getFromUser(), first.getToUser(), first.getAmount());
        }

        System.out.println("Updated net balances after settlement:");
        for (User u : goaTrip.getMembers()) {
            BigDecimal net = goaTrip.getLedger().getNetBalance(u.getId());
            System.out.printf("  %-6s: %s$%s%n", u.getName(), sign(net), net.abs());
        }

        System.out.println("\n========================================");
        System.out.println("              Demo complete");
        System.out.println("========================================");
    }

    /** Prints each member's pairwise balances within the group. */
    private static void printBalanceSheet(ExpenseManager manager, Group group) {
        System.out.println("Balance sheet:");
        for (User user : group.getMembers()) {
            Map<String, BigDecimal> balances = manager.getBalances(group, user);
            if (balances.isEmpty()) {
                System.out.println("  " + user.getName() + ": settled up");
                continue;
            }
            StringBuilder sb = new StringBuilder("  " + user.getName() + ": ");
            List<String> parts = new ArrayList<>();
            for (User other : group.getMembers()) {
                if (other.equals(user)) continue;
                BigDecimal amount = balances.get(other.getId());
                if (amount == null) continue;
                String relation = amount.compareTo(BigDecimal.ZERO) > 0
                        ? other.getName() + " owes " + user.getName() + " $" + amount
                        : other.getName() + " is owed $" + amount.abs() + " by " + user.getName();
                parts.add(relation);
            }
            sb.append(String.join(", ", parts));
            System.out.println(sb);
        }
    }

    private static String sign(BigDecimal amount) {
        return amount.compareTo(BigDecimal.ZERO) < 0 ? "-" : "+";
    }
}
