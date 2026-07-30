# Chess Game — Command + Observer + Singleton

## Intuition

> **One-line analogy**: Chess design is a Command pattern showcase — every move is an object that can be executed and undone, giving you a full undo stack for free.

**Mental model**: The board is a Singleton (one board per game). Each move (MoveCommand) encapsulates the source, destination, and captured piece — enough to both execute and undo the move. The move history is a stack of commands. Observers (game logger, clock, UI) react to events without being coupled to game logic. Piece subclasses (King, Queen, Rook, Bishop, Knight, Pawn) each implement their own `getValidMoves()` — the cleanest expression of polymorphism in the problem.

**Why it matters**: Chess exercises Command (undo/redo), Singleton (board), Observer (event notifications), and polymorphism (piece hierarchy) in a problem everyone understands. It's the go-to interview question for testing whether a candidate can model domain logic cleanly.

**Key insight**: The trickiest rules are special moves (castling, en passant, promotion) and check detection. In an interview, call them out explicitly and propose how you'd extend the design to support them — this signals design foresight without over-engineering upfront.

---

## Problem Statement

Design a Chess game system that:
- Manages a standard 8x8 chess board
- Supports all piece types with valid move generation
- Records move history with full undo capability
- Detects check, checkmate, and stalemate
- Notifies observers (loggers, UI, clock) of game events
- Supports time controls

---

## Class Diagram

```mermaid
classDiagram
    class Board {
        <<Singleton>>
        -Optional~Piece~[][] grid
        +getPiece(Position) Optional~Piece~
        +applyMove(Move) void
        +undoMove(Move) void
        +findKing(Color) Optional~Position~
    }

    class Piece {
        <<abstract>>
        +Color color
        +PieceType type
        +boolean hasMoved
        +getValidMoves(Position, Board) List~Position~
    }

    class King
    class Queen
    class Rook
    class Bishop
    class Knight
    class Pawn

    class MoveCommand {
        <<interface>>
        +execute() void
        +undo() void
    }

    class ChessMoveCommand {
        -Move move
        -Board board
        -boolean pieceHadMovedBefore
    }

    class Move {
        +Position from
        +Position to
        +Piece piece
        +Piece capturedPiece
    }

    class MoveHistory {
        <<stack>>
        +push(MoveCommand) void
        +pop() Optional~MoveCommand~
        +getMoveList() List~Move~
        +size() int
    }

    class ChessGame {
        -Board board
        -MoveHistory history
        -List~GameObserver~ observers
        -GameClock clock
        +makeMove(from, to) boolean
        +undoLastMove() boolean
        +isCheckmate(Color) boolean
        +isStalemate(Color) boolean
        +isInCheck(Color) boolean
    }

    class GameObserver {
        <<interface>>
        +onCheck(Color)
        +onCheckmate(Color)
        +onDraw(String reason)
        +onMoveMade(Move, int moveNumber)
    }

    class GameLogger
    class GameClock

    Board *-- Piece : contains
    Piece <|-- King
    Piece <|-- Queen
    Piece <|-- Rook
    Piece <|-- Bishop
    Piece <|-- Knight
    Piece <|-- Pawn
    MoveCommand <|.. ChessMoveCommand : implements
    ChessMoveCommand --> Move : wraps
    ChessMoveCommand --> Board : mutates
    ChessMoveCommand --> Piece : capturedPiece
    Move --> Piece : piece, capturedPiece
    MoveHistory --> MoveCommand : stores
    ChessGame *-- MoveHistory : owns
    ChessGame --> Board : uses
    ChessGame --> GameObserver : notifies
    ChessGame --> GameClock : switches
    GameObserver <|.. GameLogger : implements
```

Board is the Singleton container for every `Piece` on the grid; `MoveCommand`/`ChessMoveCommand` form the Command pattern's execute/undo contract, `MoveHistory` is the undo stack, and `GameObserver` decouples `ChessGame` from `GameLogger`. `GameClock` is held directly by `ChessGame` (it is called, not notified) — making it a second `GameObserver` is one of the follow-up extensions below.

---

## Patterns Used

| Pattern | Where | Why |
|---------|-------|-----|
| **Command** | `MoveCommand`, `ChessMoveCommand`, `MoveHistory` | Encapsulates each move as an object; enables undo |
| **Observer** | `GameObserver`, `GameLogger` | Notifies UI and logger of events without coupling |
| **Singleton** | `Board` | Only one board per game; prevents duplicate state |

---

## Command Pattern — Undo Stack

```java
// Every move is wrapped in a Command. The Move value object carries the
// captured piece (or null), which is what makes undo() self-contained.
Piece captured = board.getPiece(to).orElse(null);
Move move = new Move(from, to, piece, captured);
MoveCommand cmd = new ChessMoveCommand(move, board);
cmd.execute();                   // applies the move
moveHistory.push(cmd);

// Undo: pops from stack and calls undo()
moveHistory.pop().ifPresent(MoveCommand::undo);   // pop() returns Optional
```

The `undo()` in `ChessMoveCommand`:
1. Move piece back from destination to source position
2. Restore any captured piece to its original position (or clear the square)
3. Restore the piece's `hasMoved` flag, so a pawn regains its two-square option
4. Update the board state

Pawn promotion, castling and en passant are **not** modelled by `Move`, so they are
not undoable here — see the Follow-Up Extensions for how to add them.

```mermaid
sequenceDiagram
    participant G as ChessGame
    participant C as ChessMoveCommand
    participant H as MoveHistory
    participant B as Board

    G->>C: new ChessMoveCommand(move, board)
    G->>C: execute()
    C->>B: movePiece(move)
    B-->>C: board updated
    G->>H: push(command)

    Note over G,H: later, undo requested

    G->>H: pop()
    H-->>G: last command
    G->>C: undo()
    C->>B: restore captured piece, revert position
    B-->>C: board reverted
```

`ChessGame` drives execution through `ChessMoveCommand`, which mutates `Board` directly and is pushed onto `MoveHistory`; undo pops the same command and reverses it — the four steps listed above, made explicit as a call sequence.

---

## State Diagram: Piece Movement

```mermaid
stateDiagram-v2
    [*] --> WHITE_TURN

    WHITE_TURN --> WHITE_TURN: invalid move, error
    WHITE_TURN --> WHITE_TURN: leaves own king in check, illegal
    WHITE_TURN --> EVAL_W: valid move, updates board and notifies observers

    state EVAL_W <<choice>>
    EVAL_W --> GAME_OVER: checkmate, Black loses
    EVAL_W --> BLACK_TURN: check or normal, continue

    BLACK_TURN --> BLACK_TURN: invalid move, error
    BLACK_TURN --> BLACK_TURN: leaves own king in check, illegal
    BLACK_TURN --> EVAL_B: valid move, updates board and notifies observers

    state EVAL_B <<choice>>
    EVAL_B --> GAME_OVER: checkmate, White loses
    EVAL_B --> WHITE_TURN: check or normal, continue

    GAME_OVER --> [*]
```

Turns alternate between `WHITE_TURN` and `BLACK_TURN`; the same evaluation runs after **every** half-move — `makeMove` tests the opponent for check, then for checkmate, and only checkmate ends the game. Stalemate is detected by `isStalemate(Color)` but is not wired into `makeMove`, so the demo game never terminates on a draw.

---

## Move Validation Design

Each `Piece` subclass implements `getValidMoves(Position current, Board board)`, which
returns the destination squares reachable by that piece's movement rule alone — check is
deliberately ignored at this layer:

```
Rook   → horizontal + vertical rays, stops at first occupied square
Bishop → diagonal rays, stops at first occupied square
Queen  → Rook + Bishop combined
Knight → L-shapes (8 possible), can jump over pieces
King   → 1 step any direction (moving into check is filtered one layer up)
Pawn   → forward 1 (or 2 from the start square), captures diagonally
```

Castling, en passant and promotion are the deliberate omissions — see Key insight above.

Legal move filter (applied on top of piece movement):
- A move is **legal** only if it does not leave the moving player's king in check
- Achieved by executing the `ChessMoveCommand` on the real board, testing `isInCheck`,
  and calling `undo()` if the move turns out to be illegal — the Command pattern makes a
  defensive board copy unnecessary

---

## Check Detection

```java
/** Returns true if the given color's king is currently in check. */
public boolean isInCheck(Color color) {
    Optional<Position> kingPos = board.findKing(color);
    if (kingPos.isEmpty()) return false;              // king not on board
    return isSquareAttackedBy(kingPos.get(), color.opposite());
}

private boolean isSquareAttackedBy(Position square, Color attackerColor) {
    for (int r = 0; r < 8; r++) {
        for (int c = 0; c < 8; c++) {
            Position from = new Position(r, c);
            Optional<Piece> p = board.getPiece(from);
            if (p.isPresent() && p.get().getColor() == attackerColor
                    && p.get().getValidMoves(from, board).contains(square)) {
                return true;
            }
        }
    }
    return false;
}
```

---

## Design Decisions

**Q: Why Singleton for Board?**
A chess game has exactly one board. Using Singleton prevents multiple board instances and the consistency bugs that would cause. In tests, the Singleton is reset between games.

**Q: Why Command for moves instead of just calling methods directly?**
Command enables undo/redo without keeping a parallel copy of the board state. Each Command knows how to reverse itself. Also enables: move serialization (save/load), move replay, algebraic notation export.

**Q: Why Observer for game events?**
The game logic should not know about logging, UI updates, or clock management. Observer decouples these cross-cutting concerns. New features (move sound effects, network broadcast) add a new Observer class without touching `ChessGame`.

---

## Cross-Perspective: HLD Connections

**HLD View — Where Chess Design Scales to Distributed Systems**

- **Command + undo → CQRS + event sourcing** — The chess move history (a Command stack) maps to event sourcing at HLD scale. Every move is an immutable event; replaying the event log from any point reconstructs the board state. CQRS separates the command (make move) from the query (display current board).
- **Board Singleton → shared mutable state problem** — The Singleton board is fine for a local game. A distributed multiplayer game cannot use a local Singleton — the board state must be stored in a shared, consistent store (Redis, DynamoDB) with optimistic locking to prevent conflicting moves from concurrent players.
- **Observer → real-time game sync** — Move notifications to the logger, UI, and clock via Observer map to WebSocket push for real-time game state synchronization to both players' browsers and to spectators at HLD scale.
- **Move history → audit log** — The Command history is an audit log. At HLD scale, every game action is appended to an immutable audit log (Kafka topic, DynamoDB stream), enabling game replay, anti-cheat analysis, and undo-up-to-N-moves features.

---

## Follow-Up Extensions

1. **AI player**: `Strategy` pattern — `RandomStrategy`, `MinimaxStrategy`, `AlphaBetaStrategy`
2. **Save/Load game**: `Memento` pattern — serialize the full game state
3. **Online multiplayer**: Network proxy for remote player moves
4. **Opening book**: Pre-defined move sequences loaded from database
5. **PGN export**: Convert `MoveHistory` to Portable Game Notation
6. **Draw offers**: State machine for draw negotiation
7. **Time pressure**: `GameClock` Observer handles flag fall (time loss)

---

## Complexity

| Operation | Time Complexity |
|-----------|----------------|
| Move validation | O(P × M) where P=pieces, M=max moves per piece |
| Check detection | O(P × M) — check all opponent moves |
| Checkmate detection | O(L × P × M) where L=legal moves for all pieces |
| Undo | O(1) — pop from stack, restore state |

---

## Interview Discussion Points

- **How would you represent the board?** 2D array (8x8) with `Optional<Piece>` or nullable references. Alternatively, bitboards for performance (chess engines).
- **How do you prevent memory leaks in MoveHistory?** Cap history size or use WeakReferences if RAM is a concern.
- **How would you scale this to millions of concurrent games?** Stateless game logic, game state in Redis, horizontal scaling.
- **How do you test this system?** Unit test each piece's move generation, integration test check/checkmate detection with known positions.
