package com.rutik.systemdesign.lld.behavioral.command; /**
 * Command Pattern - Real World Example
 *
 * Scenario: Text Editor with Full Undo/Redo Support
 *
 * Every edit operation (type, delete, bold, replace) is encapsulated as a
 * Command object. The editor's Invoker maintains history and redo stacks so
 * any sequence of operations can be walked back and forth.
 *
 * This mirrors how "Edit > Undo" works in real editors such as VS Code or
 * IntelliJ IDEA.
 */

import java.util.ArrayDeque;
import java.util.Deque;

// ─── Receiver: the Document ───────────────────────────────────────────────────

/**
 * The Document is the Receiver. It contains the raw text and exposes low-level
 * mutation methods that Command objects call.
 */
class Document {
    private StringBuilder content = new StringBuilder();

    // ── Low-level mutations ──────────────────────────────────────────────────

    public void insertAt(int position, String text) {
        position = clamp(position, 0, content.length());
        content.insert(position, text);
    }

    public void deleteRange(int start, int length) {
        start  = clamp(start,  0, content.length());
        length = clamp(length, 0, content.length() - start);
        content.delete(start, start + length);
    }

    public String getRange(int start, int length) {
        start  = clamp(start,  0, content.length());
        length = clamp(length, 0, content.length() - start);
        return content.substring(start, start + length);
    }

    public void replaceRange(int start, int length, String replacement) {
        deleteRange(start, length);
        insertAt(start, replacement);
    }

    public String getContent() { return content.toString(); }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

// ─── Command Interface ────────────────────────────────────────────────────────

interface EditorCommand {
    void execute();
    void undo();
    String description(); // human-readable label for history display
}

// ─── Concrete Commands ────────────────────────────────────────────────────────

/**
 * Types (inserts) text at a given position.
 */
class TypeCommand implements EditorCommand {
    private final Document document;
    private final int      position;
    private final String   text;

    public TypeCommand(Document document, int position, String text) {
        this.document = document;
        this.position = position;
        this.text     = text;
    }

    @Override public void execute() { document.insertAt(position, text); }
    @Override public void undo()    { document.deleteRange(position, text.length()); }
    @Override public String description() {
        return "Type \"" + text + "\" at position " + position;
    }
}

/**
 * Deletes a range of characters. Saves the deleted text for undo.
 */
class DeleteCommand implements EditorCommand {
    private final Document document;
    private final int      position;
    private final int      length;
    private String         deletedText; // captured at execute() time for undo

    public DeleteCommand(Document document, int position, int length) {
        this.document = document;
        this.position = position;
        this.length   = length;
    }

    @Override
    public void execute() {
        deletedText = document.getRange(position, length);
        document.deleteRange(position, length);
    }

    @Override
    public void undo() {
        document.insertAt(position, deletedText);
    }

    @Override public String description() {
        return "Delete " + length + " char(s) at position " + position;
    }
}

/**
 * Replaces a range of text with new content.
 * Saves the original text so it can be restored on undo.
 */
class ReplaceCommand implements EditorCommand {
    private final Document document;
    private final int      position;
    private final int      originalLength;
    private final String   replacement;
    private String         originalText; // saved at execute() for undo

    public ReplaceCommand(Document document, int position,
                          int originalLength, String replacement) {
        this.document       = document;
        this.position       = position;
        this.originalLength = originalLength;
        this.replacement    = replacement;
    }

    @Override
    public void execute() {
        originalText = document.getRange(position, originalLength);
        document.replaceRange(position, originalLength, replacement);
    }

    @Override
    public void undo() {
        document.replaceRange(position, replacement.length(), originalText);
    }

    @Override public String description() {
        return "Replace " + originalLength + " char(s) at position "
                + position + " with \"" + replacement + "\"";
    }
}

/**
 * Appends text to the end of the document — a common editor shortcut.
 */
class AppendCommand implements EditorCommand {
    private final Document document;
    private final String   text;

    public AppendCommand(Document document, String text) {
        this.document = document;
        this.text     = text;
    }

    @Override
    public void execute() {
        document.insertAt(document.getContent().length(), text);
    }

    @Override
    public void undo() {
        int len = document.getContent().length();
        document.deleteRange(len - text.length(), text.length());
    }

    @Override public String description() {
        return "Append \"" + text + "\"";
    }
}

// ─── Invoker: the Editor ──────────────────────────────────────────────────────

/**
 * The Editor is the Invoker. It exposes execute/undo/redo and keeps the history.
 * It delegates all actual mutations to Command objects.
 */
class Editor {
    private final Document           document  = new Document();
    private final Deque<EditorCommand> history  = new ArrayDeque<>();
    private final Deque<EditorCommand> redoStack = new ArrayDeque<>();

    // ── Public API ───────────────────────────────────────────────────────────

    public void execute(EditorCommand command) {
        command.execute();
        history.push(command);
        redoStack.clear(); // new action clears the redo stack
        printState("execute: " + command.description());
    }

    public void undo() {
        if (history.isEmpty()) {
            System.out.println("[Editor] Nothing to undo.");
            return;
        }
        EditorCommand cmd = history.pop();
        cmd.undo();
        redoStack.push(cmd);
        printState("undo:    " + cmd.description());
    }

    public void redo() {
        if (redoStack.isEmpty()) {
            System.out.println("[Editor] Nothing to redo.");
            return;
        }
        EditorCommand cmd = redoStack.pop();
        cmd.execute();
        history.push(cmd);
        printState("redo:    " + cmd.description());
    }

    public String getContent() { return document.getContent(); }
    public Document getDocument() { return document; }

    private void printState(String action) {
        System.out.printf("  %-55s | content: \"%s\"%n", action, document.getContent());
    }
}

// ─── Main ─────────────────────────────────────────────────────────────────────

public class RealWorldExample {

    public static void main(String[] args) {
        System.out.println("=== Text Editor with Undo/Redo (Command Pattern) ===\n");
        System.out.printf("  %-55s | %s%n", "Action", "Document state");
        System.out.println("  " + "-".repeat(55) + "---" + "-".repeat(30));

        Editor editor = new Editor();

        // ── Build up a document ──────────────────────────────────────────────
        editor.execute(new TypeCommand(editor.getDocument(), 0, "Hello"));
        editor.execute(new AppendCommand(editor.getDocument(), ", World"));
        editor.execute(new AppendCommand(editor.getDocument(), "!"));

        // ── Replace "World" with "Java" ──────────────────────────────────────
        // "Hello, World!" — "World" starts at index 7, length 5
        editor.execute(new ReplaceCommand(editor.getDocument(), 7, 5, "Java"));

        // ── Delete the "!" ───────────────────────────────────────────────────
        // "Hello, Java!" — "!" is the last char
        editor.execute(new DeleteCommand(editor.getDocument(),
                editor.getContent().length() - 1, 1));

        // ── Undo last two actions ────────────────────────────────────────────
        System.out.println();
        editor.undo(); // restore "!"
        editor.undo(); // restore "World"

        // ── Redo one action ──────────────────────────────────────────────────
        System.out.println();
        editor.redo(); // reapply replace "World" -> "Java"

        // ── Undo everything ──────────────────────────────────────────────────
        System.out.println();
        editor.undo();
        editor.undo();
        editor.undo();
        editor.undo();
        editor.undo(); // nothing left
    }
}
