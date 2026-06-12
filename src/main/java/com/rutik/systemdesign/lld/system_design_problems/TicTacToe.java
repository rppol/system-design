package com.rutik.systemdesign.lld.system_design_problems;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

// =============================================================================
//  TIC-TAC-TOE — Low-Level Design
//  Patterns used:
//    - Strategy : MoveStrategy (RandomMoveStrategy, BlockingMoveStrategy) for AI moves
//    - State    : GameState enum gates which operations are valid
//
//  Key design point: win detection is O(N) via incremental row/col/diagonal
//  counters maintained on Board, NOT an O(N^2) full-board rescan after every move.
// =============================================================================

// ─────────────────────────────────────────────
//  ENUMS
// ─────────────────────────────────────────────

enum Symbol {
    X, O, EMPTY;

    /** Signed contribution used by Board's incremental counters: X = +1, O = -1, EMPTY = 0. */
    int delta() {
        return switch (this) {
            case X -> 1;
            case O -> -1;
            case EMPTY -> 0;
        };
    }
}

enum GameState { IN_PROGRESS, X_WINS, O_WINS, DRAW }

// ─────────────────────────────────────────────
//  BOARD
//  NxN grid + per-row/col/diagonal counters for O(N) win detection.
//
//  Counter encoding: each counter is a signed running sum where placing X
//  contributes +1 and placing O contributes -1. A line is won the instant
//  any counter reaches +size (all X) or -size (all O) — a single integer
//  comparison, no rescanning of the line's cells.
// ─────────────────────────────────────────────

class Board {
    private final int size;
    private final Symbol[][] grid;

    private final int[] rowCounts;
    private final int[] colCounts;
    private int diagonalCount;      // top-left -> bottom-right: (0,0),(1,1),...,(n-1,n-1)
    private int antiDiagonalCount;  // top-right -> bottom-left: (0,n-1),(1,n-2),...,(n-1,0)

    private int filledCells;

    public Board(int size) {
        if (size < 3) throw new IllegalArgumentException("Board size must be at least 3.");
        this.size = size;
        this.grid = new Symbol[size][size];
        for (Symbol[] row : grid) java.util.Arrays.fill(row, Symbol.EMPTY);
        this.rowCounts = new int[size];
        this.colCounts = new int[size];
    }

    public int getSize() { return size; }

    public boolean isInBounds(int row, int col) {
        return row >= 0 && row < size && col >= 0 && col < size;
    }

    public boolean isEmpty(int row, int col) {
        return grid[row][col] == Symbol.EMPTY;
    }

    public Symbol getCell(int row, int col) {
        return grid[row][col];
    }

    /**
     * Places {@code symbol} at (row, col), updates the O(N)-maintainable
     * row/column/diagonal counters in O(1), and returns whether this move
     * just completed a winning line.
     *
     * Total work per move is O(1) for the grid write plus O(1) for each of
     * the (up to) four counters touched — O(1) amortized, O(N) only in the
     * sense that each counter itself ranges over N cells conceptually; we
     * never iterate over those N cells again. Contrast with a full-board
     * rescan, which is O(N^2) per move.
     */
    public boolean placeMove(int row, int col, Symbol symbol) {
        if (!isInBounds(row, col)) {
            throw new IllegalArgumentException(
                    "Move (" + row + "," + col + ") is out of bounds for a " + size + "x" + size + " board.");
        }
        if (!isEmpty(row, col)) {
            throw new IllegalArgumentException("Cell (" + row + "," + col + ") is already occupied.");
        }
        if (symbol == Symbol.EMPTY) {
            throw new IllegalArgumentException("Cannot place EMPTY symbol on the board.");
        }

        grid[row][col] = symbol;
        filledCells++;

        int d = symbol.delta();
        rowCounts[row] += d;
        colCounts[col] += d;
        if (row == col) diagonalCount += d;
        if (row + col == size - 1) antiDiagonalCount += d;

        return Math.abs(rowCounts[row]) == size
                || Math.abs(colCounts[col]) == size
                || Math.abs(diagonalCount) == size
                || Math.abs(antiDiagonalCount) == size;
    }

    public boolean isFull() {
        return filledCells == size * size;
    }

    /** Pretty-prints the board with " . " for empty cells. */
    public void print() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                sb.append(grid[r][c] == Symbol.EMPTY ? " ." : " " + grid[r][c]);
                if (c < size - 1) sb.append(" |");
            }
            sb.append("\n");
            if (r < size - 1) {
                sb.append("-".repeat(size * 4 - 1)).append("\n");
            }
        }
        System.out.print(sb);
    }
}

// ─────────────────────────────────────────────
//  STRATEGY PATTERN — AI move selection
// ─────────────────────────────────────────────

interface MoveStrategy {
    /**
     * Returns a {row, col} pair for the next move.
     * @param board           current board state
     * @param mySymbol        this AI player's symbol
     * @param opponentSymbol  the opponent's symbol
     */
    int[] selectMove(Board board, Symbol mySymbol, Symbol opponentSymbol);
}

/** Picks any empty cell uniformly at random. Simplest possible AI. */
class RandomMoveStrategy implements MoveStrategy {
    private final Random random;

    public RandomMoveStrategy()      { this.random = new Random(); }
    public RandomMoveStrategy(long seed) { this.random = new Random(seed); }

    @Override
    public int[] selectMove(Board board, Symbol mySymbol, Symbol opponentSymbol) {
        List<int[]> emptyCells = collectEmptyCells(board);
        if (emptyCells.isEmpty()) {
            throw new IllegalStateException("No empty cells remain.");
        }
        return emptyCells.get(random.nextInt(emptyCells.size()));
    }

    static List<int[]> collectEmptyCells(Board board) {
        List<int[]> cells = new ArrayList<>();
        int n = board.getSize();
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                if (board.isEmpty(r, c)) cells.add(new int[]{r, c});
            }
        }
        return cells;
    }
}

/**
 * Blocks the opponent's immediate winning move if one exists; otherwise
 * falls back to a random empty cell. This is a one-ply lookahead, far
 * cheaper than minimax (see TicTacToe_README.md "Follow-Up Extensions").
 */
class BlockingMoveStrategy implements MoveStrategy {
    private final RandomMoveStrategy fallback;

    public BlockingMoveStrategy()          { this.fallback = new RandomMoveStrategy(); }
    public BlockingMoveStrategy(long seed) { this.fallback = new RandomMoveStrategy(seed); }

    @Override
    public int[] selectMove(Board board, Symbol mySymbol, Symbol opponentSymbol) {
        for (int[] cell : RandomMoveStrategy.collectEmptyCells(board)) {
            if (wouldWin(board, cell[0], cell[1], opponentSymbol)) {
                return cell; // block the opponent's winning cell
            }
        }
        return fallback.selectMove(board, mySymbol, opponentSymbol);
    }

    /** Simulates placing {@code symbol} at (row, col) on a scratch copy to check for a win. */
    private boolean wouldWin(Board board, int row, int col, Symbol symbol) {
        Board scratch = copyOf(board);
        return scratch.placeMove(row, col, symbol);
    }

    private Board copyOf(Board board) {
        int n = board.getSize();
        Board copy = new Board(n);
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                Symbol s = board.getCell(r, c);
                if (s != Symbol.EMPTY) copy.placeMove(r, c, s);
            }
        }
        return copy;
    }
}

// ─────────────────────────────────────────────
//  PLAYERS
// ─────────────────────────────────────────────

abstract class Player {
    protected final String id;
    protected final Symbol symbol;

    public Player(String id, Symbol symbol) {
        this.id = id;
        this.symbol = symbol;
    }

    public String getId()     { return id; }
    public Symbol getSymbol()  { return symbol; }

    /** Returns the {row, col} this player wants to play next. */
    public abstract int[] getMove(Board board);

    @Override
    public String toString() { return id + "(" + symbol + ")"; }
}

/**
 * Stand-in for a human player. Since this demo has no real stdin, "human"
 * moves are supplied programmatically via a pre-set queue of (row, col) pairs.
 */
class HumanPlayer extends Player {
    private final Deque<int[]> moveQueue;

    public HumanPlayer(String id, Symbol symbol, List<int[]> presetMoves) {
        super(id, symbol);
        this.moveQueue = new ArrayDeque<>(presetMoves);
    }

    @Override
    public int[] getMove(Board board) {
        if (moveQueue.isEmpty()) {
            throw new IllegalStateException("HumanPlayer " + id + " has no more pre-set moves.");
        }
        return moveQueue.poll();
    }
}

/** AI player that delegates move selection to a pluggable MoveStrategy. */
class AIPlayer extends Player {
    private final MoveStrategy strategy;
    private final Symbol opponentSymbol;

    public AIPlayer(String id, Symbol symbol, Symbol opponentSymbol, MoveStrategy strategy) {
        super(id, symbol);
        this.opponentSymbol = opponentSymbol;
        this.strategy = strategy;
    }

    @Override
    public int[] getMove(Board board) {
        return strategy.selectMove(board, symbol, opponentSymbol);
    }
}

// ─────────────────────────────────────────────
//  GAME COORDINATOR
// ─────────────────────────────────────────────

class TicTacToeGame {
    private final Board board;
    private final List<Player> players;
    private int currentPlayerIndex;
    private GameState state;

    public TicTacToeGame(int boardSize, List<Player> players) {
        if (players.size() < 2) {
            throw new IllegalArgumentException("Tic-Tac-Toe requires at least 2 players.");
        }
        this.board = new Board(boardSize);
        this.players = players;
        this.currentPlayerIndex = 0;
        this.state = GameState.IN_PROGRESS;
    }

    public GameState getState() { return state; }
    public Board getBoard()     { return board; }
    public Player getCurrentPlayer() { return players.get(currentPlayerIndex); }

    /**
     * Plays one turn for the current player: gets their move, validates and
     * applies it (O(1) amortized via Board's incremental counters), and
     * transitions GameState if the move wins the game or fills the board.
     */
    public void playTurn() {
        if (state != GameState.IN_PROGRESS) {
            throw new IllegalStateException("Game is already over: " + state);
        }

        Player player = players.get(currentPlayerIndex);
        int[] move = player.getMove(board);
        int row = move[0], col = move[1];

        boolean won = board.placeMove(row, col, player.getSymbol());

        if (won) {
            state = (player.getSymbol() == Symbol.X) ? GameState.X_WINS : GameState.O_WINS;
            return;
        }
        if (board.isFull()) {
            state = GameState.DRAW;
            return;
        }

        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
    }
}

// ─────────────────────────────────────────────
//  DEMO / MAIN
// ─────────────────────────────────────────────

public class TicTacToe {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   Tic-Tac-Toe -- LLD Demo (3x3)");
        System.out.println("========================================\n");

        // Human plays X with a pre-set move sequence. AI plays O with BlockingMoveStrategy,
        // which blocks X's first threat (top row) but cannot stop the second threat that
        // appears once X also occupies the main diagonal -- a classic "fork".
        List<int[]> humanMoves = List.of(
                new int[]{0, 0}, // top-left corner
                new int[]{0, 1}, // top-middle -> threatens top row (0,0)-(0,1)-(0,2)
                new int[]{1, 1}, // center -> creates a second threat: main diagonal (0,0)-(1,1)-(2,2)
                new int[]{2, 2}, // bottom-right -> completes main diagonal -> X wins
                new int[]{0, 2}  // unused if the game ends on the previous move
        );
        Player human = new HumanPlayer("Human", Symbol.X, humanMoves);
        Player ai    = new AIPlayer("AI-Blocker", Symbol.O, Symbol.X, new BlockingMoveStrategy(0L));

        TicTacToeGame game = new TicTacToeGame(3, List.of(human, ai));

        System.out.println("Players: " + human + " vs " + ai + "\n");

        int turn = 1;
        while (game.getState() == GameState.IN_PROGRESS) {
            Player current = game.getCurrentPlayer();
            game.playTurn();
            System.out.println("Turn " + turn + ": " + current + " moves");
            game.getBoard().print();
            System.out.println();
            turn++;
        }

        System.out.println(">>> Game Over: " + game.getState());

        // ── Invalid move demonstration ───────────────────────────────────
        System.out.println("\n--- Invalid move demo ---");
        Board demoBoard = new Board(3);
        demoBoard.placeMove(1, 1, Symbol.X);

        System.out.println("Attempting to play on occupied cell (1,1)...");
        try {
            demoBoard.placeMove(1, 1, Symbol.O);
        } catch (IllegalArgumentException e) {
            System.out.println("Caught: " + e.getMessage());
        }

        System.out.println("\nAttempting to play out-of-bounds cell (3,3)...");
        try {
            demoBoard.placeMove(3, 3, Symbol.O);
        } catch (IllegalArgumentException e) {
            System.out.println("Caught: " + e.getMessage());
        }

        System.out.println("\n========================================");
        System.out.println("              Demo complete");
        System.out.println("========================================");
    }
}
