package com.rutik.systemdesign.lld.system_design_problems;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

// =============================================================================
//  SNAKE AND LADDER - Low-Level Design
//  Extracted verbatim from SnakeAndLadder.md's Implementation section.
//  Patterns used:
//    - Strategy : DiceStrategy (single die / N dice), WinRule (exact-landing /
//                 overshoot-allowed)
//    - Observer : GameObserver reacts to rolls, moves, snake bites, ladder
//                 climbs and the win, without SnakeAndLadderGame knowing who
//                 is listening
//    - Factory  : BoardFactory builds a validated Board from either a fixed
//                 classic layout or a randomly generated one
//  Board.resolve() is deliberately single-hop -- landing on a jump moves you
//  once, even if the destination is itself another jump's start. The BROKEN
//  chain-resolving alternative (resolveChainedBroken() below) is teaching
//  material only: it is not called from the game engine, only from main()'s
//  bounded demonstration of why it is unsafe.
// =============================================================================

enum JumpType { SNAKE, LADDER }

/** One snake or ladder. The type is derived, never passed in -- end > start is a
 *  ladder, end < start is a snake, so the two can never be mixed up. */
final class Jump {
    final int start;
    final int end;
    final JumpType type;

    Jump(int start, int end) {
        if (start == end) {
            throw new IllegalArgumentException("a jump cannot start and end on the same cell: " + start);
        }
        this.start = start;
        this.end = end;
        this.type = end > start ? JumpType.LADDER : JumpType.SNAKE;
    }
}

/** The board: a size x size grid flattened to cells 1..size*size, plus a validated
 *  set of jumps keyed by their start cell. Validation is what keeps the engine
 *  simple -- every rule that would otherwise need a runtime check on every move
 *  is instead rejected once, at construction time. */
final class Board {
    final int size;
    final int totalCells;
    private final Map<Integer, Jump> jumpsByStart;

    Board(int size, List<Jump> jumps) {
        this.size = size;
        this.totalCells = size * size;

        Map<Integer, Jump> map = new HashMap<>();
        for (Jump j : jumps) {
            if (j.start < 2 || j.start >= totalCells) {
                throw new IllegalArgumentException(
                    "jump start " + j.start + " must be in [2, " + (totalCells - 1) + "]");
            }
            if (j.end < 2 || j.end >= totalCells) {
                throw new IllegalArgumentException(
                    "jump end " + j.end + " must be in [2, " + (totalCells - 1)
                        + "] -- a jump cannot land a player on the winning cell directly,"
                        + " that must always come from a dice roll");
            }
            if (map.putIfAbsent(j.start, j) != null) {
                throw new IllegalArgumentException(
                    "two jumps cannot start on the same cell: " + j.start);
            }
        }

        // Chain-adjacency guard: reject any jump whose destination is itself another
        // jump's origin. This is the fix for the off-by-one trap where a ladder's top
        // lands exactly on a snake's head (or vice versa) -- see Design Decisions and
        // resolveChainedBroken() in SnakeAndLadder.main() for what happens without it.
        Set<Integer> starts = map.keySet();
        for (Jump j : jumps) {
            if (starts.contains(j.end)) {
                throw new IllegalArgumentException(
                    "cell " + j.end + " is both this jump's destination and another jump's"
                        + " origin -- chaining is disallowed (a ladder's top cannot equal a"
                        + " snake's head, or vice versa)");
            }
        }

        this.jumpsByStart = Map.copyOf(map);
    }

    /** Single-hop resolution: land on a jump's start, move once to its end, done --
     *  even if that end happens to be a jump start too. The chain-adjacency check in
     *  the constructor guarantees that can never happen anyway, but resolve() does not
     *  rely on that guarantee; it simply never loops. */
    int resolve(int cell) {
        Jump j = jumpsByStart.get(cell);
        return j == null ? cell : j.end;
    }

    Jump jumpAt(int cell) {
        return jumpsByStart.get(cell);
    }
}

interface DiceStrategy {
    int roll();
}

/** A single standard six-sided die. */
final class SingleDiceStrategy implements DiceStrategy {
    private final Random rng;

    SingleDiceStrategy(Random rng) {
        this.rng = rng;
    }

    @Override public int roll() {
        return 1 + rng.nextInt(6);
    }
}

/** N six-sided dice, summed. House rules like "roll again on doubles" are a natural
 *  extension here and nowhere else -- the engine never needs to know how many dice
 *  produced a roll, only what the total was. */
final class MultiDiceStrategy implements DiceStrategy {
    private final Random rng;
    private final int diceCount;

    MultiDiceStrategy(Random rng, int diceCount) {
        this.rng = rng;
        this.diceCount = diceCount;
    }

    @Override public int roll() {
        int sum = 0;
        for (int i = 0; i < diceCount; i++) {
            sum += 1 + rng.nextInt(6);
        }
        return sum;
    }
}

/** Decides where a roll actually lands a player, before any snake/ladder is applied. */
interface WinRule {
    int nextPosition(int current, int roll, int totalCells);
}

/** A roll that would overshoot the final cell is simply capped there -- the player wins
 *  the instant the roll reaches or passes the last cell. */
final class OvershootAllowedWinRule implements WinRule {
    @Override public int nextPosition(int current, int roll, int totalCells) {
        return Math.min(current + roll, totalCells);
    }
}

/** A roll that would overshoot the final cell is rejected outright -- the player must
 *  roll the exact remaining distance, and an overshooting roll forfeits the turn. */
final class ExactLandingWinRule implements WinRule {
    @Override public int nextPosition(int current, int roll, int totalCells) {
        int target = current + roll;
        return target <= totalCells ? target : current;
    }
}

interface GameObserver {
    void onDiceRolled(Player player, int roll);
    void onMove(Player player, int from, int to);
    void onSnakeBite(Player player, int head, int tail);
    void onLadderClimb(Player player, int bottom, int top);
    void onInvalidMove(Player player, int roll);
    void onPlayerWon(Player player);
}

final class ConsoleGameObserver implements GameObserver {
    @Override public void onDiceRolled(Player player, int roll) {
        // rolls are implied by the move line below; kept as a separate hook so a
        // different observer (a UI, a log aggregator) can react to the roll alone
    }

    @Override public void onMove(Player player, int from, int to) {
        System.out.printf("%-8s %3d -> %3d%n", player.name, from, to);
    }

    @Override public void onSnakeBite(Player player, int head, int tail) {
        System.out.printf("%-8s    bitten by snake %d -> %d%n", player.name, head, tail);
    }

    @Override public void onLadderClimb(Player player, int bottom, int top) {
        System.out.printf("%-8s    climbs ladder %d -> %d%n", player.name, bottom, top);
    }

    @Override public void onInvalidMove(Player player, int roll) {
        System.out.printf("%-8s    rolled %d, overshoots the board -- forfeits this turn%n", player.name, roll);
    }

    @Override public void onPlayerWon(Player player) {
        System.out.printf("%-8s    reaches the final cell -- WINS!%n", player.name);
    }
}

final class Player {
    final String name;
    int position = 1;

    Player(String name) {
        this.name = name;
    }
}

/** Builds validated boards so SnakeAndLadderGame never has to. */
final class BoardFactory {
    private BoardFactory() { }

    static Board classicBoard() {
        List<Jump> jumps = List.of(
            new Jump(4, 25), new Jump(13, 46), new Jump(33, 49), new Jump(63, 81), new Jump(71, 91),
            new Jump(17, 7), new Jump(54, 34), new Jump(62, 19), new Jump(89, 68), new Jump(98, 28)
        );
        return new Board(10, jumps);
    }

    /** Randomly places snakes and ladders on a size x size board with no two jumps
     *  sharing a start or end cell. Retries on collision rather than failing outright --
     *  collisions are common with modest board sizes and small jump counts. */
    static Board randomBoard(int size, int snakeCount, int ladderCount, Random rng) {
        int totalCells = size * size;
        List<Jump> jumps = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        used.add(1);
        used.add(totalCells);

        int attempts = 0;
        while (jumps.size() < snakeCount + ladderCount) {
            if (++attempts > 10_000) {
                throw new IllegalStateException("could not place all jumps without collisions after 10000 attempts");
            }
            boolean wantLadder = jumps.size() < ladderCount;
            int a = 2 + rng.nextInt(totalCells - 2);
            int b = 2 + rng.nextInt(totalCells - 2);
            if (a == b) {
                continue;
            }
            int start = wantLadder ? Math.min(a, b) : Math.max(a, b);
            int end = wantLadder ? Math.max(a, b) : Math.min(a, b);
            if (used.contains(start) || used.contains(end)) {
                continue;
            }
            used.add(start);
            used.add(end);
            jumps.add(new Jump(start, end));
        }
        return new Board(size, jumps);
    }
}

final class SnakeAndLadderGame {
    private final Board board;
    private final Deque<Player> turnOrder;
    private final DiceStrategy dice;
    private final WinRule winRule;
    private final List<GameObserver> observers = new ArrayList<>();
    private Player winner;

    SnakeAndLadderGame(Board board, List<Player> players, DiceStrategy dice, WinRule winRule) {
        if (players.size() < 2) {
            throw new IllegalArgumentException("need at least 2 players");
        }
        this.board = board;
        this.turnOrder = new ArrayDeque<>(players);
        this.dice = dice;
        this.winRule = winRule;
    }

    void addObserver(GameObserver observer) {
        observers.add(observer);
    }

    boolean isFinished() {
        return winner != null;
    }

    Player winner() {
        return winner;
    }

    void playTurn() {
        if (isFinished()) {
            return;
        }
        Player current = turnOrder.poll();
        int roll = dice.roll();
        for (GameObserver o : observers) {
            o.onDiceRolled(current, roll);
        }

        int target = winRule.nextPosition(current.position, roll, board.totalCells);
        if (target == current.position) {
            for (GameObserver o : observers) {
                o.onInvalidMove(current, roll);
            }
            turnOrder.offer(current);
            return;
        }

        int resolved = board.resolve(target);
        for (GameObserver o : observers) {
            o.onMove(current, current.position, target);
        }
        Jump jump = board.jumpAt(target);
        if (jump != null) {
            if (jump.type == JumpType.SNAKE) {
                for (GameObserver o : observers) {
                    o.onSnakeBite(current, jump.start, jump.end);
                }
            } else {
                for (GameObserver o : observers) {
                    o.onLadderClimb(current, jump.start, jump.end);
                }
            }
        }
        current.position = resolved;

        if (current.position == board.totalCells) {
            winner = current;
            for (GameObserver o : observers) {
                o.onPlayerWon(current);
            }
            return;
        }
        turnOrder.offer(current);
    }

    void playUntilWinner(int maxTurns) {
        int turns = 0;
        while (!isFinished() && turns++ < maxTurns) {
            playTurn();
        }
    }
}

public final class SnakeAndLadder {

    public static void main(String[] args) {
        System.out.println("=== 1. Classic board, single die, exact-landing rule ===");
        Board classic = BoardFactory.classicBoard();
        List<Player> players = List.of(new Player("Alice"), new Player("Bob"), new Player("Charlie"));
        SnakeAndLadderGame game = new SnakeAndLadderGame(
            classic, players, new SingleDiceStrategy(new Random(42)), new ExactLandingWinRule());
        game.addObserver(new ConsoleGameObserver());
        game.playUntilWinner(300);
        if (game.isFinished()) {
            System.out.println(game.winner().name + " wins the game!");
        } else {
            System.out.println("no winner within the turn cap");
        }

        System.out.println();
        System.out.println("=== 2. Two-dice strategy, five sample rolls (seeded) ===");
        DiceStrategy twoDice = new MultiDiceStrategy(new Random(7), 2);
        for (int i = 1; i <= 5; i++) {
            System.out.println("roll " + i + ": " + twoDice.roll());
        }

        System.out.println();
        System.out.println("=== 3. Exact-landing vs overshoot-allowed at the edge of the board ===");
        WinRule exact = new ExactLandingWinRule();
        WinRule overshoot = new OvershootAllowedWinRule();
        int totalCells = 100;
        int position = 97;
        int roll = 6;
        System.out.printf("player at %d rolls %d -> exact-landing:     %d (must roll exactly 3 to win)%n",
            position, roll, exact.nextPosition(position, roll, totalCells));
        System.out.printf("player at %d rolls %d -> overshoot-allowed: %d (wins immediately)%n",
            position, roll, overshoot.nextPosition(position, roll, totalCells));

        System.out.println();
        System.out.println("=== 4. Board validation rejects two jumps starting on the same cell ===");
        try {
            new Board(10, List.of(new Jump(20, 40), new Jump(20, 5)));
        } catch (IllegalArgumentException e) {
            System.out.println("rejected: " + e.getMessage());
        }

        System.out.println();
        System.out.println("=== 5. Board validation rejects a ladder whose top equals a snake's head ===");
        try {
            new Board(10, List.of(new Jump(10, 50), new Jump(50, 30)));
        } catch (IllegalArgumentException e) {
            System.out.println("rejected: " + e.getMessage());
        }

        System.out.println();
        System.out.println("=== 6. BROKEN: naive chain-resolution loops forever on that exact config ===");
        Map<Integer, Jump> unsafeChain = new HashMap<>();
        unsafeChain.put(10, new Jump(10, 50));
        unsafeChain.put(50, new Jump(50, 10)); // a genuine 2-cycle: 10 -> 50 -> 10 -> ...
        try {
            resolveChainedBroken(unsafeChain, 10, 1000);
        } catch (IllegalStateException e) {
            System.out.println("caught: " + e.getMessage());
        }
        System.out.println("Board.resolve() never has this problem -- it is single-hop by construction.");
    }

    /** BROKEN on purpose -- teaching material, never called from SnakeAndLadderGame.
     *  Keeps following a jump's destination as long as the destination is itself
     *  another jump's start. Given an acyclic chain this "just works" and looks like
     *  a more thorough implementation than Board.resolve()'s single hop -- until the
     *  jump data contains a cycle, at which point it spins forever. maxIterations
     *  exists only so this demo can prove the point without actually hanging. */
    private static int resolveChainedBroken(Map<Integer, Jump> jumps, int cell, int maxIterations) {
        int current = cell;
        int iterations = 0;
        while (jumps.containsKey(current)) {
            current = jumps.get(current).end;
            iterations++;
            if (iterations >= maxIterations) {
                throw new IllegalStateException(
                    "cycle detected after " + maxIterations + " chained jumps starting from cell "
                        + cell + " -- this loops forever in production; Board's chain-adjacency"
                        + " validation and single-hop resolve() are the fix, not a bigger iteration cap");
            }
        }
        return current;
    }
}
