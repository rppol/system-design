package com.rutik.systemdesign.lld.behavioral.memento; /**
 * Memento Pattern - Real World Example
 *
 * Scenario: Text Editor with Save Points and History
 *
 * The editor supports:
 *   - Named save points ("checkpoint"): user can jump back to any named snapshot.
 *   - Auto-save history: every edit is pushed onto a stack; Ctrl+Z walks back.
 *   - Redo support: undone states go on a redo stack.
 *
 * The Memento stores the full document content plus cursor position. The
 * Caretaker (HistoryManager) maintains both the undo stack and named saves.
 * The Editor (Originator) never exposes its internal StringBuilder directly.
 */

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

// ─── Memento: EditorSnapshot ──────────────────────────────────────────────────

/**
 * Immutable snapshot of editor state. Only the TextEditor can create one.
 */
final class EditorSnapshot {
    private final String content;
    private final int    cursorPosition;
    private final String label; // human-readable label for display

    // Package-private constructor: only TextEditor can instantiate
    EditorSnapshot(String content, int cursorPosition, String label) {
        this.content        = content;
        this.cursorPosition = cursorPosition;
        this.label          = label;
    }

    String getContent()        { return content; }
    int    getCursorPosition() { return cursorPosition; }
    String getLabel()          { return label; }

    @Override
    public String toString() {
        String preview = content.length() > 30
                ? content.substring(0, 30) + "..."
                : content;
        return "Snapshot[label='" + label + "', cursor=" + cursorPosition
                + ", content=\"" + preview + "\"]";
    }
}

// ─── Originator: TextEditor ───────────────────────────────────────────────────

/**
 * The editor holds document content and a cursor. Every mutating operation
 * should be preceded by save() so the Caretaker can record a snapshot.
 */
class TextEditor {
    private StringBuilder content        = new StringBuilder();
    private int           cursorPosition = 0;
    private int           snapshotCounter = 0;

    // ── Editing operations ────────────────────────────────────────────────────

    /** Inserts text at the current cursor position and advances the cursor. */
    public void type(String text) {
        content.insert(cursorPosition, text);
        cursorPosition += text.length();
        System.out.println("  [type] \"" + text + "\"  | doc: \"" + content + "\" | cursor: " + cursorPosition);
    }

    /** Deletes 'count' characters to the left of the cursor (backspace). */
    public void backspace(int count) {
        int start = Math.max(0, cursorPosition - count);
        content.delete(start, cursorPosition);
        cursorPosition = start;
        System.out.println("  [backspace " + count + "]  | doc: \"" + content + "\" | cursor: " + cursorPosition);
    }

    /** Moves the cursor to an absolute position. */
    public void moveCursorTo(int position) {
        cursorPosition = Math.max(0, Math.min(position, content.length()));
        System.out.println("  [move cursor to " + cursorPosition + "]");
    }

    // ── Snapshot API (Originator protocol) ───────────────────────────────────

    /** Creates a snapshot of the current state with an auto-generated label. */
    public EditorSnapshot save() {
        String label = "auto-" + (++snapshotCounter);
        return new EditorSnapshot(content.toString(), cursorPosition, label);
    }

    /** Creates a snapshot with a custom label (for named save points). */
    public EditorSnapshot saveWithLabel(String label) {
        return new EditorSnapshot(content.toString(), cursorPosition, label);
    }

    /** Restores state from a snapshot. */
    public void restore(EditorSnapshot snapshot) {
        content        = new StringBuilder(snapshot.getContent());
        cursorPosition = snapshot.getCursorPosition();
        System.out.println("  [restore] Snapshot '" + snapshot.getLabel()
                + "' | doc: \"" + content + "\" | cursor: " + cursorPosition);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String getContent()        { return content.toString(); }
    public int    getCursorPosition() { return cursorPosition; }
}

// ─── Caretaker: HistoryManager ────────────────────────────────────────────────

/**
 * Manages two kinds of history:
 *   1. Undo/Redo stacks for step-by-step history.
 *   2. Named save points the user can jump back to at any time.
 */
class HistoryManager {
    private final TextEditor             editor;
    private final Deque<EditorSnapshot>  undoStack = new ArrayDeque<>();
    private final Deque<EditorSnapshot>  redoStack = new ArrayDeque<>();
    private final Map<String, EditorSnapshot> savePoints = new LinkedHashMap<>();

    public HistoryManager(TextEditor editor) {
        this.editor = editor;
    }

    // ── Auto-save (undo/redo) ─────────────────────────────────────────────────

    /**
     * Captures the current state. Call this BEFORE making an edit so the
     * pre-edit state is available for undo.
     */
    public void record() {
        undoStack.push(editor.save());
        redoStack.clear(); // new edit invalidates redo history
    }

    /** Reverts the last recorded edit. */
    public void undo() {
        if (undoStack.isEmpty()) {
            System.out.println("  [undo] Nothing to undo.");
            return;
        }
        // Push current state onto redo before restoring
        redoStack.push(editor.saveWithLabel("redo-point"));
        EditorSnapshot snapshot = undoStack.pop();
        System.out.println("  [undo] " + snapshot);
        editor.restore(snapshot);
    }

    /** Re-applies the last undone edit. */
    public void redo() {
        if (redoStack.isEmpty()) {
            System.out.println("  [redo] Nothing to redo.");
            return;
        }
        undoStack.push(editor.saveWithLabel("undo-point"));
        EditorSnapshot snapshot = redoStack.pop();
        System.out.println("  [redo] " + snapshot);
        editor.restore(snapshot);
    }

    // ── Named save points ─────────────────────────────────────────────────────

    /** Creates a named save point at the current editor state. */
    public void createSavePoint(String name) {
        EditorSnapshot snapshot = editor.saveWithLabel(name);
        savePoints.put(name, snapshot);
        System.out.println("  [save point created] \"" + name + "\" -> " + snapshot);
    }

    /** Jumps back to a previously created named save point. */
    public void loadSavePoint(String name) {
        EditorSnapshot snapshot = savePoints.get(name);
        if (snapshot == null) {
            System.out.println("  [load save point] \"" + name + "\" not found.");
            return;
        }
        System.out.println("  [loading save point] \"" + name + "\"");
        editor.restore(snapshot);
    }

    /** Lists all named save points. */
    public void listSavePoints() {
        System.out.println("  [save points]: " + savePoints.keySet());
    }

    /** Shows depth of undo/redo stacks. */
    public void showStats() {
        System.out.println("  [history] undo depth=" + undoStack.size()
                + ", redo depth=" + redoStack.size());
    }
}

// ─── Main ─────────────────────────────────────────────────────────────────────

public class RealWorldExample {

    /** Shorthand: record state, then perform the edit. */
    private static void edit(HistoryManager history, Runnable action) {
        history.record();
        action.run();
    }

    public static void main(String[] args) {
        System.out.println("=== Text Editor with Save Points (Memento Pattern) ===\n");

        TextEditor     editor  = new TextEditor();
        HistoryManager history = new HistoryManager(editor);

        // ── Writing a document ───────────────────────────────────────────────
        System.out.println("--- Composing document ---");
        edit(history, () -> editor.type("Hello"));
        edit(history, () -> editor.type(", World"));
        edit(history, () -> editor.type("!"));

        // ── Create a named save point after first sentence ───────────────────
        System.out.println();
        history.createSavePoint("first-sentence");

        // ── Continue editing ─────────────────────────────────────────────────
        System.out.println("\n--- More edits ---");
        edit(history, () -> editor.type(" How are you?"));
        edit(history, () -> editor.type(" I hope well."));

        history.createSavePoint("two-sentences");

        // ── Typo: backspace and retype ───────────────────────────────────────
        System.out.println("\n--- Fix typo ---");
        edit(history, () -> editor.backspace(5));    // delete "well."
        edit(history, () -> editor.type("great!")); // retype

        history.showStats();

        // ── Undo the typo fix ─────────────────────────────────────────────────
        System.out.println("\n--- Undo / Redo ---");
        history.undo();  // undo retype
        history.undo();  // undo backspace

        // ── Redo ──────────────────────────────────────────────────────────────
        history.redo();  // redo backspace
        history.redo();  // redo retype
        history.redo();  // nothing to redo

        // ── Jump to named save points ─────────────────────────────────────────
        System.out.println("\n--- Jump to named save points ---");
        history.listSavePoints();
        history.loadSavePoint("first-sentence");
        System.out.println("  Current content: \"" + editor.getContent() + "\"");

        history.loadSavePoint("two-sentences");
        System.out.println("  Current content: \"" + editor.getContent() + "\"");

        history.loadSavePoint("draft-v3"); // non-existent
    }
}
