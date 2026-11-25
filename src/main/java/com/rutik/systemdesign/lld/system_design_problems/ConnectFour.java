package com.rutik.systemdesign.lld.system_design_problems;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.StringJoiner;

// =============================================================================
//  CONNECT FOUR - Low-Level Design
//
//  Patterns used:
//    - Strategy : C4MoveStrategy (scripted human input, win-or-block AI)
//    - State    : C4GameState gates which operations are legal
//    - Command  : C4Move on a stack gives undo and an append-only move log
//    - Observer : C4GameListener receives move and game-over events
//
//  Key design points:
//    - Gravity is O(1): heights[col] is the next free row, never a column scan.
//    - The win check looks only at lines through the LAST disc, in four
//      directions, counting BOTH ways from it. One-way counting is the classic
//      bug and is run for real in demonstrateOneWayBug().
//    - A move carries the move number the client saw. A stale move (double
//      click, second tab, retried request) is rejected, which is the same
//      compare-and-set a database row version would give across instances.
//    - The move log is the whole state: replay it and you get the same board.
//    - C4BitBoard is the 64-bit encoding (7 bits per column) with the
//      shift-and-AND win test; it is cross-checked against the array board.
//
//  All type names carry the C4 prefix because every file in this package is
//  compiled together and TicTacToe.java already owns Board, Player, GameState.
// =============================================================================

enum C4Disc {
    RED('R'), YELLOW('Y');

    private final char symbol;

    C4Disc(char symbol) {
        this.symbol = symbol;
    }

    char symbol() {
        return symbol;
    }

    C4Disc opponent() {
        return this == RED ? YELLOW : RED;
    }
}

enum C4GameState { IN_PROGRESS, RED_WINS, YELLOW_WINS, DRAW }

class C4IllegalMoveException extends RuntimeException {
    C4IllegalMoveException(String message) {
        super(message);
    }
}

/** One applied move. Kept on a stack for undo and serialised for replay. */
record C4Move(int moveNumber, C4Disc disc, int column, int row) { }

// -----------------------------------------------------------------------------
//  BOARD: grid + per-column heights. Knows geometry, not turns or players.
// -----------------------------------------------------------------------------
class C4Board {
    private static final int[][] DIRECTIONS = {
        {0, 1},   // horizontal
        {1, 0},   // vertical
        {1, 1},   // diagonal  (up-right)
        {1, -1}   // anti-diagonal (up-left)
    };

    private final int rows;
    private final int cols;
    private final int connect;
    private final C4Disc[][] grid;   // grid[0] is the BOTTOM row
    private final int[] heights;     // heights[c] = next free row in column c
    private int discCount;

    C4Board(int rows, int cols, int connect) {
        if (rows < connect && cols < connect) {
            throw new IllegalArgumentException("board too small to ever connect " + connect);
        }
        this.rows = rows;
        this.cols = cols;
        this.connect = connect;
        this.grid = new C4Disc[rows][cols];
        this.heights = new int[cols];
    }

    int rows() { return rows; }
    int cols() { return cols; }
    int connect() { return connect; }

    boolean isColumnFull(int col) {
        return heights[col] == rows;
    }

    boolean isFull() {
        return discCount == rows * cols;
    }

    boolean inBounds(int col) {
        return col >= 0 && col < cols;
    }

    C4Disc at(int row, int col) {
        return grid[row][col];
    }

    /** O(1) gravity: the disc lands at heights[col]. Returns the landing row. */
    int drop(int col, C4Disc disc) {
        int row = heights[col];
        grid[row][col] = disc;
        heights[col]++;
        discCount++;
        return row;
    }

    /** Undo support: the last disc in a column is always the top one. */
    void lift(int col) {
        heights[col]--;
        grid[heights[col]][col] = null;
        discCount--;
    }

    /** FIX: counts both ways from the last disc, in all four directions. O(connect). */
    boolean wins(int row, int col) {
        C4Disc disc = grid[row][col];
        for (int[] d : DIRECTIONS) {
            int run = 1
                    + countRun(row, col, d[0], d[1], disc)
                    + countRun(row, col, -d[0], -d[1], disc);
            if (run >= connect) {
                return true;
            }
        }
        return false;
    }

    /** BROKEN on purpose: counts only in the +direction, so a disc dropped into
     *  the MIDDLE of a line never sees the discs on its other side. */
    boolean winsOneWay(int row, int col) {
        C4Disc disc = grid[row][col];
        for (int[] d : DIRECTIONS) {
            if (1 + countRun(row, col, d[0], d[1], disc) >= connect) {
                return true;
            }
        }
        return false;
    }

    private int countRun(int row, int col, int dr, int dc, C4Disc disc) {
        int n = 0;
        int r = row + dr;
        int c = col + dc;
        while (r >= 0 && r < rows && c >= 0 && c < cols && grid[r][c] == disc) {
            n++;
            r += dr;
            c += dc;
        }
        return n;
    }

    String render() {
        StringBuilder sb = new StringBuilder();
        for (int r = rows - 1; r >= 0; r--) {
            sb.append("  |");
            for (int c = 0; c < cols; c++) {
                sb.append(grid[r][c] == null ? '.' : grid[r][c].symbol()).append('|');
            }
            sb.append('\n');
        }
        sb.append("   ");
        for (int c = 0; c < cols; c++) {
            sb.append(c);
            if (c < cols - 1) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }
}

// -----------------------------------------------------------------------------
//  STRATEGY: who picks the next column
// -----------------------------------------------------------------------------
interface C4MoveStrategy {
    int chooseColumn(C4Board board, C4Disc me);
}

/** Stands in for a human: columns arrive from outside (UI, socket, test). */
class C4ScriptedStrategy implements C4MoveStrategy {
    private final Deque<Integer> columns = new ArrayDeque<>();

    C4ScriptedStrategy(int... cols) {
        for (int c : cols) {
            columns.addLast(c);
        }
    }

    @Override
    public int chooseColumn(C4Board board, C4Disc me) {
        Integer next = columns.pollFirst();
        if (next == null) {
            throw new IllegalStateException("script ran out of moves");
        }
        return next;
    }
}

/** One-ply AI: win now if possible, else block the opponent's immediate win,
 *  else prefer the centre column (the centre takes part in the most lines). */
class C4WinOrBlockStrategy implements C4MoveStrategy {
    private final Random random;

    C4WinOrBlockStrategy(long seed) {
        this.random = new Random(seed);
    }

    @Override
    public int chooseColumn(C4Board board, C4Disc me) {
        int win = findImmediateWin(board, me);
        if (win >= 0) {
            return win;
        }
        int block = findImmediateWin(board, me.opponent());
        if (block >= 0) {
            return block;
        }
        List<Integer> open = new ArrayList<>();
        int centre = board.cols() / 2;
        for (int offset = 0; offset <= centre; offset++) {
            for (int c : new int[] {centre - offset, centre + offset}) {
                if (board.inBounds(c) && !board.isColumnFull(c) && !open.contains(c)) {
                    open.add(c);
                }
            }
        }
        // Mostly centre-first, with some randomness so AI-vs-AI games differ by seed.
        int pick = random.nextInt(10) < 7 ? 0 : random.nextInt(open.size());
        return open.get(pick);
    }

    private static int findImmediateWin(C4Board board, C4Disc disc) {
        for (int c = 0; c < board.cols(); c++) {
            if (board.isColumnFull(c)) {
                continue;
            }
            int row = board.drop(c, disc);      // try it
            boolean wins = board.wins(row, c);
            board.lift(c);                      // and take it back
            if (wins) {
                return c;
            }
        }
        return -1;
    }
}

// -----------------------------------------------------------------------------
//  OBSERVER
// -----------------------------------------------------------------------------
interface C4GameListener {
    void onMove(C4Move move);

    void onGameOver(C4GameState result);
}

class C4ConsoleListener implements C4GameListener {
    private final boolean verbose;

    C4ConsoleListener(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public void onMove(C4Move move) {
        if (verbose) {
            System.out.printf("  move %2d: %s -> column %d (lands row %d)%n",
                    move.moveNumber(), move.disc(), move.column(), move.row());
        }
    }

    @Override
    public void onGameOver(C4GameState result) {
        System.out.println("  game over: " + result);
    }
}

// -----------------------------------------------------------------------------
//  GAME: turns, validation, state, undo, move log
// -----------------------------------------------------------------------------
class C4Game {
    private final C4Board board;
    private final Deque<C4Move> history = new ArrayDeque<>();
    private final List<C4GameListener> listeners = new ArrayList<>();
    private C4Disc toMove = C4Disc.RED;   // RED always opens
    private C4GameState state = C4GameState.IN_PROGRESS;

    C4Game(int rows, int cols, int connect) {
        this.board = new C4Board(rows, cols, connect);
    }

    static C4Game standard() {
        return new C4Game(6, 7, 4);
    }

    void addListener(C4GameListener listener) {
        listeners.add(listener);
    }

    C4Board board() { return board; }
    C4GameState state() { return state; }
    C4Disc toMove() { return toMove; }

    /** Number of the NEXT move. Clients echo it back to prove they saw the latest board. */
    int nextMoveNumber() {
        return history.size() + 1;
    }

    /** Single-player convenience: no staleness check. */
    synchronized C4Move play(int column) {
        return play(toMove, column, nextMoveNumber());
    }

    /** The real entry point. `expectedMoveNumber` is an optimistic-concurrency token. */
    synchronized C4Move play(C4Disc who, int column, int expectedMoveNumber) {
        if (state != C4GameState.IN_PROGRESS) {
            throw new IllegalStateException("game is over: " + state);
        }
        if (expectedMoveNumber != nextMoveNumber()) {
            throw new C4IllegalMoveException("stale move: client saw move " + expectedMoveNumber
                    + " but the game is at move " + nextMoveNumber());
        }
        if (who != toMove) {
            throw new C4IllegalMoveException("not " + who + "'s turn");
        }
        if (!board.inBounds(column)) {
            throw new C4IllegalMoveException("column " + column + " is out of range 0.." + (board.cols() - 1));
        }
        if (board.isColumnFull(column)) {
            throw new C4IllegalMoveException("column " + column + " is full");
        }
        int row = board.drop(column, who);
        C4Move move = new C4Move(nextMoveNumber(), who, column, row);
        history.push(move);
        if (board.wins(row, column)) {
            state = who == C4Disc.RED ? C4GameState.RED_WINS : C4GameState.YELLOW_WINS;
        } else if (board.isFull()) {
            state = C4GameState.DRAW;
        }
        toMove = who.opponent();
        listeners.forEach(l -> l.onMove(move));
        if (state != C4GameState.IN_PROGRESS) {
            C4GameState result = state;
            listeners.forEach(l -> l.onGameOver(result));
        }
        return move;
    }

    /** Undo the last move. Legal even after the game ended: the state goes back to IN_PROGRESS. */
    synchronized C4Move undo() {
        C4Move last = history.poll();
        if (last == null) {
            throw new IllegalStateException("nothing to undo");
        }
        board.lift(last.column());
        toMove = last.disc();
        state = C4GameState.IN_PROGRESS;
        return last;
    }

    /** Persistence: the move log IS the state. "3,3,4,..." in play order. */
    synchronized String moveLog() {
        StringJoiner joiner = new StringJoiner(",");
        List<C4Move> inOrder = new ArrayList<>(history);
        for (int i = inOrder.size() - 1; i >= 0; i--) {
            joiner.add(String.valueOf(inOrder.get(i).column()));
        }
        return joiner.toString();
    }

    static C4Game replay(String log) {
        C4Game game = standard();
        if (!log.isEmpty()) {
            for (String token : log.split(",")) {
                game.play(Integer.parseInt(token.trim()));
            }
        }
        return game;
    }

    /** Drive a whole game from two strategies. */
    C4GameState playOut(C4MoveStrategy red, C4MoveStrategy yellow) {
        while (state == C4GameState.IN_PROGRESS) {
            C4MoveStrategy s = toMove == C4Disc.RED ? red : yellow;
            play(s.chooseColumn(board, toMove));
        }
        return state;
    }
}

// -----------------------------------------------------------------------------
//  BITBOARD: the 64-bit encoding for a fixed 6x7 board (Tromp's layout).
//  Bit index = col * 7 + row; row 6 of each column is an always-empty
//  sentinel so that shifts never carry a line from one column into the next.
// -----------------------------------------------------------------------------
class C4BitBoard {
    private static final int H = 6;
    private static final int H1 = H + 1;
    private final long[] discs = new long[2];   // one bitboard per colour
    private final int[] height = new int[7];

    C4BitBoard() {
        for (int c = 0; c < 7; c++) {
            height[c] = c * H1;
        }
    }

    boolean canPlay(int col) {
        return height[col] < col * H1 + H;
    }

    /** Returns true when this move connects four for `player` (0 or 1). */
    boolean playAndCheck(int col, int player) {
        discs[player] |= 1L << height[col]++;
        return hasFour(discs[player]);
    }

    /** 4 directions x (one shift-AND + one shift-AND) = the "8 shifts/ands" win test. */
    static boolean hasFour(long b) {
        int[] steps = {1, H1, H1 - 1, H1 + 1};   // vertical, horizontal, diag \, diag /
        for (int d : steps) {
            long m = b & (b >> d);
            if ((m & (m >> (2 * d))) != 0) {
                return true;
            }
        }
        return false;
    }
}

// -----------------------------------------------------------------------------
//  DEMO
// -----------------------------------------------------------------------------
public class ConnectFour {

    public static void main(String[] args) {
        System.out.println("=== Connect Four: gravity board, both-way win check, undo, replay ===");
        System.out.println();
        demonstrateOneWayBug();
        demonstrateScriptedGame();
        demonstrateIllegalMoves();
        demonstrateUndo();
        demonstrateStaleMove();
        demonstrateReplay();
        demonstrateAiGame();
        crossCheckBitBoard();
    }

    /** BROKEN vs FIX: the disc that completes a line lands in the middle of it. */
    private static void demonstrateOneWayBug() {
        System.out.println("--- Broken: counting only one way from the last disc ---");
        C4Board board = new C4Board(6, 7, 4);
        board.drop(0, C4Disc.RED);
        board.drop(1, C4Disc.RED);
        board.drop(3, C4Disc.RED);
        int row = board.drop(2, C4Disc.RED);    // completes 0-1-2-3 from the inside
        System.out.println(board.render());
        System.out.println("last disc: row " + row + ", column 2");
        System.out.println("one-way check says win?  " + board.winsOneWay(row, 2) + "   <- misses columns 0 and 1");
        System.out.println("both-way check says win? " + board.wins(row, 2));
        System.out.println();
    }

    private static void demonstrateScriptedGame() {
        System.out.println("--- Step 1: scripted game, RED wins on a diagonal ---");
        C4Game game = C4Game.standard();
        game.addListener(new C4ConsoleListener(true));
        // R:0 Y:1 R:1 Y:2 R:2 Y:3 R:2 Y:3 R:3 Y:6 R:3 -> R holds (0,0)(1,1)(2,2)(3,3)
        C4GameState result = game.playOut(
                new C4ScriptedStrategy(0, 1, 2, 2, 3, 3),
                new C4ScriptedStrategy(1, 2, 3, 3, 6));
        System.out.println(game.board().render());
        System.out.println("result: " + result + "  after " + (game.nextMoveNumber() - 1) + " moves");
        System.out.println();
    }

    private static void demonstrateIllegalMoves() {
        System.out.println("--- Step 2: illegal moves are rejected with a reason, board untouched ---");
        C4Game game = C4Game.standard();
        for (int i = 0; i < 6; i++) {
            game.play(0);   // fill column 0: R,Y,R,Y,R,Y -- no four, column now full
        }
        tryMove(game, () -> game.play(0));
        tryMove(game, () -> game.play(7));
        tryMove(game, () -> game.play(C4Disc.YELLOW, 3, game.nextMoveNumber()));
        String before = game.moveLog();
        System.out.println("move log unchanged after 3 rejected moves? " + before.equals(game.moveLog())
                + "  (" + before + ")");
        C4Game won = C4Game.replay("0,1,0,1,0,1,0");
        tryMove(won, () -> won.play(4));
        System.out.println();
    }

    private static void demonstrateUndo() {
        System.out.println("--- Step 3: undo walks back a winning move and reopens the game ---");
        C4Game game = C4Game.replay("0,1,0,1,0,1,0");
        System.out.println("state after vertical four in column 0: " + game.state());
        C4Move undone = game.undo();
        System.out.println("undo -> removed " + undone + "; state now " + game.state()
                + ", to move: " + game.toMove());
        game.play(3);
        System.out.println("RED plays column 3 instead; state: " + game.state());
        System.out.println(game.board().render());
        System.out.println();
    }

    private static void demonstrateStaleMove() {
        System.out.println("--- Step 4: two clients, one stale move ---");
        C4Game game = C4Game.standard();
        int seenByTabA = game.nextMoveNumber();
        int seenByTabB = game.nextMoveNumber();
        game.play(C4Disc.RED, 3, seenByTabA);
        System.out.println("tab A: RED -> column 3 with token " + seenByTabA + " accepted");
        tryMove(game, () -> game.play(C4Disc.RED, 4, seenByTabB));
        System.out.println("discs on board: " + (game.nextMoveNumber() - 1) + "  (the double-submit never landed)");
        System.out.println();
    }

    private static void demonstrateReplay() {
        System.out.println("--- Step 5: persistence by move log, replayed into a fresh game ---");
        C4Game original = C4Game.standard();
        original.playOut(new C4WinOrBlockStrategy(11), new C4WinOrBlockStrategy(12));
        String log = original.moveLog();
        C4Game restored = C4Game.replay(log);
        System.out.println("move log (" + log.split(",").length + " moves): " + log);
        System.out.println("original: " + original.state() + "   restored: " + restored.state());
        System.out.println("boards identical? " + original.board().render().equals(restored.board().render()));
        System.out.println();
    }

    private static void demonstrateAiGame() {
        System.out.println("--- Step 6: win-or-block AI vs win-or-block AI over 200 seeded games ---");
        int red = 0;
        int yellow = 0;
        int draw = 0;
        for (long seed = 0; seed < 200; seed++) {
            C4Game game = C4Game.standard();
            switch (game.playOut(new C4WinOrBlockStrategy(seed), new C4WinOrBlockStrategy(seed + 1000))) {
                case RED_WINS -> red++;
                case YELLOW_WINS -> yellow++;
                case DRAW -> draw++;
                default -> throw new IllegalStateException();
            }
        }
        System.out.println("RED (moves first) wins " + red + ", YELLOW wins " + yellow + ", draws " + draw);
        System.out.println();
    }

    /** Play random games on both encodings and compare every win verdict. */
    private static void crossCheckBitBoard() {
        System.out.println("--- Step 7: bitboard win test cross-checked against the array board ---");
        Random rnd = new Random(42);
        int games = 10_000;
        long verdicts = 0;
        int mismatches = 0;
        for (int g = 0; g < games; g++) {
            C4Board arr = new C4Board(6, 7, 4);
            C4BitBoard bits = new C4BitBoard();
            int player = 0;
            while (true) {
                int col = rnd.nextInt(7);
                if (arr.isColumnFull(col)) {
                    if (arr.isFull()) {
                        break;
                    }
                    continue;
                }
                int row = arr.drop(col, player == 0 ? C4Disc.RED : C4Disc.YELLOW);
                boolean a = arr.wins(row, col);
                boolean b = bits.playAndCheck(col, player);
                verdicts++;
                if (a != b) {
                    mismatches++;
                }
                if (a || arr.isFull()) {
                    break;
                }
                player ^= 1;
            }
        }
        System.out.println(games + " random games, " + verdicts + " move verdicts compared, mismatches: " + mismatches);
    }

    private static void tryMove(C4Game game, Runnable move) {
        try {
            move.run();
            System.out.println("  accepted (unexpected)");
        } catch (C4IllegalMoveException | IllegalStateException e) {
            System.out.println("  rejected: " + e.getMessage());
        }
    }
}
