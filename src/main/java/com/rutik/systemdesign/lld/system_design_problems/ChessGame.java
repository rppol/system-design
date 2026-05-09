package com.rutik.systemdesign.lld.system_design_problems;

import java.util.*;

// ─────────────────────────────────────────────
// ENUMS
// ─────────────────────────────────────────────

enum PieceType { KING, QUEEN, ROOK, BISHOP, KNIGHT, PAWN }

enum Color { WHITE, BLACK;
    public Color opposite() { return this == WHITE ? BLACK : WHITE; }
}

// ─────────────────────────────────────────────
// POSITION
// ─────────────────────────────────────────────

/** Immutable (row, col) coordinate on the board (0-indexed). */
class Position {
    public final int row;
    public final int col;

    public Position(int row, int col) {
        this.row = row;
        this.col = col;
    }

    public boolean isValid() {
        return row >= 0 && row < 8 && col >= 0 && col < 8;
    }

    public Position offset(int dr, int dc) {
        return new Position(row + dr, col + dc);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Position)) return false;
        Position p = (Position) o;
        return row == p.row && col == p.col;
    }

    @Override public int hashCode() { return Objects.hash(row, col); }

    @Override public String toString() {
        return "" + (char)('a' + col) + (row + 1);
    }
}

// ─────────────────────────────────────────────
// PIECES — Abstract + Concrete
// ─────────────────────────────────────────────

/**
 * Abstract base for all chess pieces.
 * Each subclass implements getValidMoves() with its specific movement rules.
 */
abstract class Piece {
    protected final Color color;
    protected final PieceType type;
    protected boolean hasMoved = false;

    public Piece(Color color, PieceType type) {
        this.color = color;
        this.type  = type;
    }

    public Color getColor()    { return color; }
    public PieceType getType() { return type; }
    public boolean hasMoved()  { return hasMoved; }
    public void setMoved()     { hasMoved = true; }

    /** Returns all positions this piece could legally move to (ignores check). */
    public abstract List<Position> getValidMoves(Position current, Board board);

    /** Helper: slide in a direction until blocked or out of bounds. */
    protected List<Position> slide(Position start, int dr, int dc, Board board) {
        List<Position> moves = new ArrayList<>();
        Position next = start.offset(dr, dc);
        while (next.isValid()) {
            Optional<Piece> occupant = board.getPiece(next);
            if (occupant.isPresent()) {
                if (occupant.get().getColor() != this.color) moves.add(next); // capture
                break;
            }
            moves.add(next);
            next = next.offset(dr, dc);
        }
        return moves;
    }

    @Override
    public String toString() {
        return color.name().charAt(0) + type.name().substring(0, 1);
    }
}

class King extends Piece {
    public King(Color color) { super(color, PieceType.KING); }

    @Override
    public List<Position> getValidMoves(Position current, Board board) {
        List<Position> moves = new ArrayList<>();
        int[][] deltas = {{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}};
        for (int[] d : deltas) {
            Position next = current.offset(d[0], d[1]);
            if (!next.isValid()) continue;
            Optional<Piece> occ = board.getPiece(next);
            if (occ.isEmpty() || occ.get().getColor() != color) moves.add(next);
        }
        return moves;
    }
}

class Queen extends Piece {
    public Queen(Color color) { super(color, PieceType.QUEEN); }

    @Override
    public List<Position> getValidMoves(Position current, Board board) {
        List<Position> moves = new ArrayList<>();
        int[][] directions = {{-1,0},{1,0},{0,-1},{0,1},{-1,-1},{-1,1},{1,-1},{1,1}};
        for (int[] d : directions) moves.addAll(slide(current, d[0], d[1], board));
        return moves;
    }
}

class Rook extends Piece {
    public Rook(Color color) { super(color, PieceType.ROOK); }

    @Override
    public List<Position> getValidMoves(Position current, Board board) {
        List<Position> moves = new ArrayList<>();
        for (int[] d : new int[][]{{-1,0},{1,0},{0,-1},{0,1}})
            moves.addAll(slide(current, d[0], d[1], board));
        return moves;
    }
}

class Bishop extends Piece {
    public Bishop(Color color) { super(color, PieceType.BISHOP); }

    @Override
    public List<Position> getValidMoves(Position current, Board board) {
        List<Position> moves = new ArrayList<>();
        for (int[] d : new int[][]{{-1,-1},{-1,1},{1,-1},{1,1}})
            moves.addAll(slide(current, d[0], d[1], board));
        return moves;
    }
}

class Knight extends Piece {
    public Knight(Color color) { super(color, PieceType.KNIGHT); }

    @Override
    public List<Position> getValidMoves(Position current, Board board) {
        List<Position> moves = new ArrayList<>();
        int[][] jumps = {{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}};
        for (int[] j : jumps) {
            Position next = current.offset(j[0], j[1]);
            if (!next.isValid()) continue;
            Optional<Piece> occ = board.getPiece(next);
            if (occ.isEmpty() || occ.get().getColor() != color) moves.add(next);
        }
        return moves;
    }
}

class Pawn extends Piece {
    public Pawn(Color color) { super(color, PieceType.PAWN); }

    @Override
    public List<Position> getValidMoves(Position current, Board board) {
        List<Position> moves = new ArrayList<>();
        int direction = (color == Color.WHITE) ? 1 : -1; // White moves up (+row), Black moves down

        // One step forward
        Position oneStep = current.offset(direction, 0);
        if (oneStep.isValid() && board.getPiece(oneStep).isEmpty()) {
            moves.add(oneStep);
            // Two steps from starting row
            if (!hasMoved) {
                Position twoStep = current.offset(2 * direction, 0);
                if (twoStep.isValid() && board.getPiece(twoStep).isEmpty())
                    moves.add(twoStep);
            }
        }

        // Diagonal captures
        for (int dc : new int[]{-1, 1}) {
            Position diag = current.offset(direction, dc);
            if (diag.isValid()) {
                Optional<Piece> occ = board.getPiece(diag);
                if (occ.isPresent() && occ.get().getColor() != color) moves.add(diag);
            }
        }

        return moves;
    }
}

// ─────────────────────────────────────────────
// BOARD — Singleton
// ─────────────────────────────────────────────

/**
 * Board is a Singleton (one board per game process).
 * It holds an 8x8 grid of Optional<Piece> and exposes atomic move/undo operations.
 */
class Board {
    private static Board instance;
    private final Optional<Piece>[][] grid;

    @SuppressWarnings("unchecked")
    private Board() {
        grid = new Optional[8][8];
        for (Optional<Piece>[] row : grid) Arrays.fill(row, Optional.empty());
    }

    /** Singleton accessor — creates if not already created. */
    public static Board getInstance() {
        if (instance == null) instance = new Board();
        return instance;
    }

    /** Reset for a new game (test convenience). */
    public static void reset() { instance = new Board(); }

    public Optional<Piece> getPiece(Position pos) {
        return grid[pos.row][pos.col];
    }

    public void setPiece(Position pos, Piece piece) {
        grid[pos.row][pos.col] = Optional.ofNullable(piece);
    }

    public void clearSquare(Position pos) {
        grid[pos.row][pos.col] = Optional.empty();
    }

    /** Execute a move on the board (does NOT validate legality). */
    public void applyMove(Move move) {
        clearSquare(move.getFrom());
        setPiece(move.getTo(), move.getPiece());
        move.getPiece().setMoved();
    }

    /** Undo a move (restores source and destination). */
    public void undoMove(Move move) {
        setPiece(move.getFrom(), move.getPiece());
        // Restore captured piece (or clear square)
        if (move.getCapturedPiece() != null) {
            setPiece(move.getTo(), move.getCapturedPiece());
        } else {
            clearSquare(move.getTo());
        }
    }

    /** Returns the position of the king of the given color, or empty if not found. */
    public Optional<Position> findKing(Color color) {
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++) {
                Optional<Piece> p = grid[r][c];
                if (p.isPresent() && p.get().getType() == PieceType.KING &&
                    p.get().getColor() == color)
                    return Optional.of(new Position(r, c));
            }
        return Optional.empty();
    }

    /** Standard chess starting position. */
    public void setupInitialPosition() {
        reset();
        Board b = getInstance();
        // White back rank
        b.setPiece(new Position(0,0), new Rook(Color.WHITE));
        b.setPiece(new Position(0,1), new Knight(Color.WHITE));
        b.setPiece(new Position(0,2), new Bishop(Color.WHITE));
        b.setPiece(new Position(0,3), new Queen(Color.WHITE));
        b.setPiece(new Position(0,4), new King(Color.WHITE));
        b.setPiece(new Position(0,5), new Bishop(Color.WHITE));
        b.setPiece(new Position(0,6), new Knight(Color.WHITE));
        b.setPiece(new Position(0,7), new Rook(Color.WHITE));
        for (int c = 0; c < 8; c++) b.setPiece(new Position(1,c), new Pawn(Color.WHITE));

        // Black back rank
        b.setPiece(new Position(7,0), new Rook(Color.BLACK));
        b.setPiece(new Position(7,1), new Knight(Color.BLACK));
        b.setPiece(new Position(7,2), new Bishop(Color.BLACK));
        b.setPiece(new Position(7,3), new Queen(Color.BLACK));
        b.setPiece(new Position(7,4), new King(Color.BLACK));
        b.setPiece(new Position(7,5), new Bishop(Color.BLACK));
        b.setPiece(new Position(7,6), new Knight(Color.BLACK));
        b.setPiece(new Position(7,7), new Rook(Color.BLACK));
        for (int c = 0; c < 8; c++) b.setPiece(new Position(6,c), new Pawn(Color.BLACK));
    }

    public void printBoard() {
        System.out.println("  a  b  c  d  e  f  g  h");
        for (int r = 7; r >= 0; r--) {
            System.out.print((r + 1) + " ");
            for (int c = 0; c < 8; c++) {
                Optional<Piece> p = grid[r][c];
                System.out.print(p.map(piece -> "[" + piece + "]").orElse("[ ]"));
            }
            System.out.println(" " + (r + 1));
        }
        System.out.println("  a  b  c  d  e  f  g  h");
    }
}

// ─────────────────────────────────────────────
// COMMAND PATTERN — Move + MoveHistory
// ─────────────────────────────────────────────

/**
 * Value object representing a single move.
 */
class Move {
    private final Position from;
    private final Position to;
    private final Piece piece;
    private final Piece capturedPiece; // null if no capture

    public Move(Position from, Position to, Piece piece, Piece capturedPiece) {
        this.from          = from;
        this.to            = to;
        this.piece         = piece;
        this.capturedPiece = capturedPiece;
    }

    public Position getFrom()          { return from; }
    public Position getTo()            { return to; }
    public Piece getPiece()            { return piece; }
    public Piece getCapturedPiece()    { return capturedPiece; }

    @Override
    public String toString() {
        String capture = (capturedPiece != null) ? "x" + capturedPiece : "";
        return piece + " " + from + capture + to;
    }
}

/** Command interface for moves — supports undo. */
interface MoveCommand {
    void execute();
    void undo();
    Move getMove();
}

/**
 * Concrete command: applies/undoes a chess move on the board.
 */
class ChessMoveCommand implements MoveCommand {
    private final Move move;
    private final Board board;

    public ChessMoveCommand(Move move, Board board) {
        this.move  = move;
        this.board = board;
    }

    @Override public void execute() { board.applyMove(move); }
    @Override public void undo()    { board.undoMove(move); }
    @Override public Move getMove() { return move; }
}

/**
 * MoveHistory maintains a stack of executed commands.
 * Supports unlimited undo (bounded by stack size).
 */
class MoveHistory {
    private final Deque<MoveCommand> stack = new ArrayDeque<>();

    public void push(MoveCommand cmd) { stack.push(cmd); }

    public Optional<MoveCommand> pop() {
        return stack.isEmpty() ? Optional.empty() : Optional.of(stack.pop());
    }

    public List<Move> getMoveList() {
        List<Move> list = new ArrayList<>();
        // Convert deque to list in chronological order
        List<MoveCommand> cmds = new ArrayList<>(stack);
        Collections.reverse(cmds);
        cmds.forEach(c -> list.add(c.getMove()));
        return list;
    }

    public int size() { return stack.size(); }
}

// ─────────────────────────────────────────────
// OBSERVER PATTERN — Game Events
// ─────────────────────────────────────────────

/** Observer interface for game lifecycle events. */
interface GameObserver {
    void onCheck(Color kingInCheck);
    void onCheckmate(Color losingColor);
    void onDraw(String reason);
    void onMoveMade(Move move, int moveNumber);
}

/** Concrete observer: logs all game events to stdout. */
class GameLogger implements GameObserver {
    @Override
    public void onMoveMade(Move move, int moveNumber) {
        System.out.printf("[Log] Move %d: %s%n", moveNumber, move);
    }

    @Override
    public void onCheck(Color kingInCheck) {
        System.out.println("[Log] CHECK — " + kingInCheck + " king is in check!");
    }

    @Override
    public void onCheckmate(Color losingColor) {
        System.out.println("[Log] CHECKMATE — " + losingColor + " loses!");
    }

    @Override
    public void onDraw(String reason) {
        System.out.println("[Log] DRAW — " + reason);
    }
}

// ─────────────────────────────────────────────
// GAME CLOCK
// ─────────────────────────────────────────────

/** Simple per-player clock with time remaining in seconds. */
class GameClock {
    private final Map<Color, Long> timeRemainingMs = new EnumMap<>(Color.class);
    private final Map<Color, Long> lastTickMs       = new EnumMap<>(Color.class);
    private Color activeClock;

    public GameClock(long whiteMs, long blackMs) {
        timeRemainingMs.put(Color.WHITE, whiteMs);
        timeRemainingMs.put(Color.BLACK, blackMs);
    }

    public void start(Color color) {
        activeClock = color;
        lastTickMs.put(color, System.currentTimeMillis());
    }

    public void switchClock() {
        if (activeClock != null) {
            long elapsed = System.currentTimeMillis() - lastTickMs.getOrDefault(activeClock, 0L);
            timeRemainingMs.merge(activeClock, -elapsed, Long::sum);
        }
        activeClock = activeClock == null ? Color.WHITE : activeClock.opposite();
        lastTickMs.put(activeClock, System.currentTimeMillis());
    }

    public boolean isOutOfTime(Color color) {
        return timeRemainingMs.getOrDefault(color, 1L) <= 0;
    }

    public long getRemainingMs(Color color) {
        return timeRemainingMs.getOrDefault(color, 0L);
    }
}

// ─────────────────────────────────────────────
// CHESS GAME ORCHESTRATOR
// ─────────────────────────────────────────────

/**
 * ChessGame ties together Board, MoveHistory, GameClock, and Observers.
 * It is responsible for:
 *   - Turn management
 *   - Move validation (piece moves + check legality)
 *   - Check / checkmate detection
 *   - Notifying all registered observers
 */
class ChessGame {
    private final Board board;
    private final MoveHistory history = new MoveHistory();
    private final List<GameObserver> observers = new ArrayList<>();
    private final GameClock clock;
    private Color currentTurn = Color.WHITE;
    private int moveNumber = 1;
    private boolean gameOver = false;

    public ChessGame(GameClock clock) {
        this.board = Board.getInstance();
        this.board.setupInitialPosition();
        this.clock = clock;
        clock.start(Color.WHITE);
    }

    public void addObserver(GameObserver o)    { observers.add(o); }
    public void removeObserver(GameObserver o) { observers.remove(o); }

    // ── Core API ──────────────────────────────────────────────

    /**
     * Attempts to move a piece from `from` to `to`.
     * Returns true if the move was successfully made.
     */
    public boolean makeMove(Position from, Position to) {
        if (gameOver) {
            System.out.println("[Game] Game is over.");
            return false;
        }
        Optional<Piece> pieceOpt = board.getPiece(from);
        if (pieceOpt.isEmpty()) {
            System.out.println("[Game] No piece at " + from);
            return false;
        }
        Piece piece = pieceOpt.get();
        if (piece.getColor() != currentTurn) {
            System.out.println("[Game] Not " + piece.getColor() + "'s turn.");
            return false;
        }
        List<Position> validMoves = piece.getValidMoves(from, board);
        if (!validMoves.contains(to)) {
            System.out.println("[Game] " + piece + " cannot move from " + from + " to " + to);
            return false;
        }

        // Build and execute command
        Piece captured = board.getPiece(to).orElse(null);
        Move move = new Move(from, to, piece, captured);
        MoveCommand cmd = new ChessMoveCommand(move, board);
        cmd.execute();

        // Verify the move does not leave own king in check
        if (isInCheck(currentTurn)) {
            cmd.undo(); // illegal — self-check
            System.out.println("[Game] Move would leave own king in check.");
            return false;
        }

        history.push(cmd);
        notifyMoveMade(move);
        clock.switchClock();

        // Check / Checkmate detection for opponent
        Color opponent = currentTurn.opposite();
        if (isInCheck(opponent)) {
            if (isCheckmate(opponent)) {
                notifyCheckmate(opponent);
                gameOver = true;
            } else {
                notifyCheck(opponent);
            }
        }

        currentTurn = opponent;
        if (currentTurn == Color.WHITE) moveNumber++;
        return true;
    }

    /**
     * Undoes the last move. Both players' last half-moves are undone in pairs
     * for full-turn undo; here we undo one half-move at a time.
     */
    public boolean undoLastMove() {
        Optional<MoveCommand> cmdOpt = history.pop();
        if (cmdOpt.isEmpty()) { System.out.println("[Game] No moves to undo."); return false; }
        cmdOpt.get().undo();
        currentTurn = currentTurn.opposite();
        if (currentTurn == Color.BLACK && moveNumber > 1) moveNumber--;
        System.out.println("[Game] Last move undone. It is now " + currentTurn + "'s turn.");
        gameOver = false;
        return true;
    }

    // ── Check / Checkmate Logic ────────────────────────────────

    /** Returns true if the given color's king is currently in check. */
    public boolean isInCheck(Color color) {
        Optional<Position> kingPos = board.findKing(color);
        if (kingPos.isEmpty()) return false; // king not on board (shouldn't happen)
        return isSquareAttackedBy(kingPos.get(), color.opposite());
    }

    /** Returns true if there is no legal move that escapes check. */
    public boolean isCheckmate(Color color) {
        if (!isInCheck(color)) return false;
        return !hasAnyLegalMove(color);
    }

    /** Stalemate: not in check but has no legal move. */
    public boolean isStalemate(Color color) {
        if (isInCheck(color)) return false;
        return !hasAnyLegalMove(color);
    }

    // ── Private helpers ────────────────────────────────────────

    private boolean isSquareAttackedBy(Position square, Color attackerColor) {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Optional<Piece> p = board.getPiece(new Position(r, c));
                if (p.isPresent() && p.get().getColor() == attackerColor) {
                    List<Position> attacks = p.get().getValidMoves(new Position(r, c), board);
                    if (attacks.contains(square)) return true;
                }
            }
        }
        return false;
    }

    private boolean hasAnyLegalMove(Color color) {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Position from = new Position(r, c);
                Optional<Piece> p = board.getPiece(from);
                if (p.isEmpty() || p.get().getColor() != color) continue;
                for (Position to : p.get().getValidMoves(from, board)) {
                    // Simulate move
                    Piece captured = board.getPiece(to).orElse(null);
                    Move m = new Move(from, to, p.get(), captured);
                    MoveCommand cmd = new ChessMoveCommand(m, board);
                    cmd.execute();
                    boolean stillInCheck = isInCheck(color);
                    cmd.undo();
                    if (!stillInCheck) return true;
                }
            }
        }
        return false;
    }

    // ── Observer notifications ─────────────────────────────────

    private void notifyMoveMade(Move m) {
        observers.forEach(o -> o.onMoveMade(m, moveNumber));
    }
    private void notifyCheck(Color c)      { observers.forEach(o -> o.onCheck(c)); }
    private void notifyCheckmate(Color c)  { observers.forEach(o -> o.onCheckmate(c)); }
    @SuppressWarnings("unused")
    private void notifyDraw(String reason) { observers.forEach(o -> o.onDraw(reason)); }

    // ── Getters ────────────────────────────────────────────────

    public Color getCurrentTurn() { return currentTurn; }
    public int getMoveNumber()    { return moveNumber; }
    public MoveHistory getHistory() { return history; }
    public Board getBoard()       { return board; }
    public boolean isGameOver()   { return gameOver; }
}

// ─────────────────────────────────────────────
// MAIN / DEMO
// ─────────────────────────────────────────────

public class ChessGame {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║       CHESS GAME DEMO                ║");
        System.out.println("╚══════════════════════════════════════╝\n");

        // ── Setup ──────────────────────────────────────────────
        GameClock clock = new GameClock(10 * 60 * 1000L, 10 * 60 * 1000L); // 10 min each
        ChessGame game  = new ChessGame(clock);          // Singleton board is initialized
        game.addObserver(new GameLogger());

        Board board = game.getBoard();
        System.out.println("=== Initial Board ===");
        board.printBoard();

        // ── Scenario 1: Normal opening moves ──────────────────
        System.out.println("\n>>> Scenario 1: Opening moves (e2→e4, e7→e5)");
        game.makeMove(new Position(1, 4), new Position(3, 4)); // e2→e4 (White pawn)
        game.makeMove(new Position(6, 4), new Position(4, 4)); // e7→e5 (Black pawn)

        // ── Scenario 2: Invalid turn ───────────────────────────
        System.out.println("\n>>> Scenario 2: White tries to move Black's pawn");
        game.makeMove(new Position(4, 4), new Position(3, 4)); // should fail — Black's pawn on White's turn

        // ── Scenario 3: Knight moves ───────────────────────────
        System.out.println("\n>>> Scenario 3: Knight moves (g1→f3, b8→c6)");
        game.makeMove(new Position(0, 6), new Position(2, 5)); // Ng1→f3
        game.makeMove(new Position(7, 1), new Position(5, 2)); // Nb8→c6

        // ── Scenario 4: Invalid move ───────────────────────────
        System.out.println("\n>>> Scenario 4: Try to move pawn sideways (invalid)");
        game.makeMove(new Position(3, 4), new Position(3, 5)); // invalid for pawn

        // ── Scenario 5: Print board after moves ───────────────
        System.out.println("\n=== Board after 2 full moves ===");
        board.printBoard();

        // ── Scenario 6: Undo last move ─────────────────────────
        System.out.println("\n>>> Scenario 6: Undo last move (Black's Nb8→c6)");
        game.undoLastMove();
        System.out.println("Current turn after undo: " + game.getCurrentTurn());
        System.out.println("=== Board after undo ===");
        board.printBoard();

        // ── Scenario 7: Move history ───────────────────────────
        System.out.println("\n>>> Scenario 7: Move history so far");
        game.getHistory().getMoveList().forEach(m ->
                System.out.println("  " + m));

        // ── Scenario 8: Scholar's Mate setup (manual check demo) ─
        System.out.println("\n>>> Scenario 8: Quick check detection demo");
        // Reset to a position where White queen threatens Black king
        Board.reset();
        Board freshBoard = Board.getInstance();
        // Place minimal pieces: White queen at d5, Black king at e8
        freshBoard.setPiece(new Position(7, 4), new King(Color.BLACK));
        freshBoard.setPiece(new Position(0, 4), new King(Color.WHITE));
        freshBoard.setPiece(new Position(4, 3), new Queen(Color.WHITE)); // d5

        GameClock clock2 = new GameClock(600000, 600000);
        ChessGame game2  = new ChessGame(clock2) {
            // Override to use the manually-set board (skip setupInitialPosition)
        };
        // We'll check manually:
        boolean whiteInCheck = game2.isInCheck(Color.WHITE);
        boolean blackInCheck = game2.isInCheck(Color.BLACK);
        System.out.println("White in check: " + whiteInCheck);
        System.out.println("Black in check: " + blackInCheck);

        System.out.println("\n[Demo complete]");
    }
}
