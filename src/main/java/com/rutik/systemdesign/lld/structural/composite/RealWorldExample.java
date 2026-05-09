package com.rutik.systemdesign.lld.structural.composite; /**
 * COMPOSITE PATTERN — Real-World Example: File System Tree
 *
 * Scenario:
 *   A file system where directories contain files and/or other directories.
 *   Any node — whether a single file or an entire directory tree — must
 *   support the same operations: getSize(), print(), search().
 *
 *   This is the canonical real-world Composite use case: the tree is
 *   arbitrarily deep, and callers treat leaves (files) and branches
 *   (directories) through one interface.
 *
 * Roles:
 *   - FileSystemNode  : Component  — common interface for all nodes
 *   - File            : Leaf       — terminal node, real data
 *   - Directory       : Composite  — holds other FileSystemNodes
 */

import java.util.ArrayList;
import java.util.List;

// ─────────────────────────────────────────────
// COMPONENT: FileSystemNode
// Common interface for both files and directories.
// ─────────────────────────────────────────────

interface FileSystemNode {
    /** Returns the name of this node (file or directory name). */
    String getName();

    /**
     * Returns the total size in bytes.
     * For a File: its own size.
     * For a Directory: sum of all children's sizes (recursive).
     */
    long getSize();

    /**
     * Prints this node and its contents (for directories, recursively).
     * @param indent Current indentation level for tree display.
     */
    void print(String indent);

    /**
     * Searches for nodes whose name contains the query string.
     * Returns matching nodes at any depth below (and including) this node.
     */
    List<FileSystemNode> search(String query);
}

// ─────────────────────────────────────────────
// LEAF: File
// A terminal node — no children.
// Holds actual file data (name + size in bytes).
// ─────────────────────────────────────────────

class File implements FileSystemNode {

    private final String name;
    private final long sizeInBytes;

    public File(String name, long sizeInBytes) {
        this.name = name;
        this.sizeInBytes = sizeInBytes;
    }

    @Override public String getName() { return name; }

    /** File size is its own stored size — no children to sum. */
    @Override public long getSize() { return sizeInBytes; }

    @Override
    public void print(String indent) {
        System.out.println(indent + "|- " + name + "  [" + formatSize(sizeInBytes) + "]");
    }

    @Override
    public List<FileSystemNode> search(String query) {
        List<FileSystemNode> results = new ArrayList<>();
        if (name.contains(query)) {
            results.add(this);
        }
        return results;
    }

    /** Format bytes into human-readable string. */
    private static String formatSize(long bytes) {
        if (bytes < 1024)       return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return (bytes / (1024 * 1024)) + " MB";
    }
}

// ─────────────────────────────────────────────
// COMPOSITE: Directory
// A branch node — holds a list of FileSystemNodes
// (files and/or subdirectories).
// Delegates getSize(), search() recursively.
// ─────────────────────────────────────────────

class Directory implements FileSystemNode {

    private final String name;

    // Children can be Files (leaves) or other Directories (composites)
    private final List<FileSystemNode> children = new ArrayList<>();

    public Directory(String name) {
        this.name = name;
    }

    /** Add a child node (file or subdirectory). */
    public Directory add(FileSystemNode node) {
        children.add(node);
        return this;  // fluent API for convenient tree construction
    }

    /** Remove a child node. */
    public void remove(FileSystemNode node) {
        children.remove(node);
    }

    @Override public String getName() { return name; }

    /**
     * Total size = sum of all children's sizes.
     * Because children may themselves be Directories, this
     * recursively computes the size of the entire subtree.
     */
    @Override
    public long getSize() {
        return children.stream()
                .mapToLong(FileSystemNode::getSize)
                .sum();
    }

    /**
     * Print directory, then recursively print all children with increased indent.
     */
    @Override
    public void print(String indent) {
        long totalSize = getSize();
        System.out.println(indent + "+ " + name + "/  [total: " + formatSize(totalSize) + "]");
        for (FileSystemNode child : children) {
            child.print(indent + "  ");
        }
    }

    /**
     * Search: check this directory's name, then recursively search all children.
     * Returns a flat list of all matching nodes anywhere in the subtree.
     */
    @Override
    public List<FileSystemNode> search(String query) {
        List<FileSystemNode> results = new ArrayList<>();
        if (name.contains(query)) {
            results.add(this);
        }
        for (FileSystemNode child : children) {
            results.addAll(child.search(query));   // recursive
        }
        return results;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024)        return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return (bytes / (1024 * 1024)) + " MB";
    }
}

// ─────────────────────────────────────────────
// MAIN
// ─────────────────────────────────────────────

public class RealWorldExample {

    public static void main(String[] args) {

        // Build a file system tree:
        //
        // / (root)
        // ├── home/
        // │   └── alice/
        // │       ├── documents/
        // │       │   ├── resume.pdf        (128 KB)
        // │       │   └── notes.txt         (4 KB)
        // │       ├── photos/
        // │       │   ├── vacation.jpg      (3 MB)
        // │       │   └── profile.png       (512 KB)
        // │       └── .bashrc               (2 KB)
        // ├── etc/
        // │   ├── hosts                     (1 KB)
        // │   └── nginx/
        // │       └── nginx.conf            (8 KB)
        // └── var/
        //     └── log/
        //         ├── syslog                (2 MB)
        //         └── auth.log              (512 KB)

        Directory root = new Directory("/");

        Directory home = new Directory("home");
        Directory alice = new Directory("alice");

        Directory documents = new Directory("documents");
        documents.add(new File("resume.pdf", 128 * 1024))
                 .add(new File("notes.txt", 4 * 1024));

        Directory photos = new Directory("photos");
        photos.add(new File("vacation.jpg", 3 * 1024 * 1024))
              .add(new File("profile.png", 512 * 1024));

        alice.add(documents)
             .add(photos)
             .add(new File(".bashrc", 2 * 1024));

        home.add(alice);

        Directory etc = new Directory("etc");
        Directory nginx = new Directory("nginx");
        nginx.add(new File("nginx.conf", 8 * 1024));
        etc.add(new File("hosts", 1024))
           .add(nginx);

        Directory var = new Directory("var");
        Directory log = new Directory("log");
        log.add(new File("syslog", 2 * 1024 * 1024))
           .add(new File("auth.log", 512 * 1024));
        var.add(log);

        root.add(home).add(etc).add(var);

        // ── Tree structure ──
        System.out.println("========================================");
        System.out.println(" Full file system tree");
        System.out.println("========================================");
        root.print("");

        System.out.println();

        // ── Size calculations ──
        System.out.println("========================================");
        System.out.println(" Size calculations (uniform interface)");
        System.out.println("========================================");

        // Calling getSize() on a File and a Directory look identical to the caller
        FileSystemNode resumePdf = documents.search("resume.pdf").get(0);
        System.out.println("Size of resume.pdf:    " + resumePdf.getSize() + " bytes");
        System.out.println("Size of documents/:    " + documents.getSize() + " bytes");
        System.out.println("Size of alice/:        " + alice.getSize() + " bytes");
        System.out.println("Size of entire root/:  " + root.getSize() + " bytes");

        System.out.println();

        // ── Search ──
        System.out.println("========================================");
        System.out.println(" Search: nodes containing 'log'");
        System.out.println("========================================");
        List<FileSystemNode> logResults = root.search("log");
        if (logResults.isEmpty()) {
            System.out.println("No results found.");
        } else {
            for (FileSystemNode node : logResults) {
                String type = (node instanceof Directory) ? "[DIR] " : "[FILE]";
                System.out.println("  " + type + " " + node.getName()
                        + "  (" + node.getSize() + " bytes)");
            }
        }

        System.out.println();

        // ── Subtree operations ──
        System.out.println("========================================");
        System.out.println(" Subtree print (same interface, subtree only)");
        System.out.println("========================================");
        System.out.println("Printing only /home/alice/photos/:");
        photos.print("  ");
    }
}

/*
 * WHAT THIS EXAMPLE DEMONSTRATES:
 *
 * 1. Uniform operations:
 *    getSize() and search() on a File and on a Directory are the same call.
 *    The Composite traversal is invisible to the caller.
 *
 * 2. Recursive size calculation:
 *    root.getSize() is the recursive sum of all files, no matter how deep.
 *    Adding a new file updates all ancestor sizes automatically.
 *
 * 3. Recursive search:
 *    search("log") traverses the entire tree and returns matches at any depth.
 *    The client calls one method on root — no explicit tree traversal needed.
 *
 * 4. Fluent builder:
 *    Directory.add() returns `this`, enabling chained construction.
 *    This is an ergonomic addition to the Composite — not part of the core pattern.
 *
 * 5. Open/Closed:
 *    Add a new node type (e.g., SymLink) implementing FileSystemNode.
 *    No existing Directory or File code changes.
 */
