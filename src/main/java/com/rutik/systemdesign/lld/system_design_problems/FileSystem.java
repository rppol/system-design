package com.rutik.systemdesign.lld.system_design_problems;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;

// =============================================================================
//  IN-MEMORY FILE SYSTEM - Low-Level Design
//  Patterns used:
//    - Composite : FsDirectory and FsFile share FsNode; size() recurses
//    - Iterator  : find() walks the tree depth-first with an explicit stack
//  Paths are absolute; "." and ".." are resolved lexically before lookup.
//  One ReentrantReadWriteLock guards the whole tree: reads (ls, cat, find, du)
//  run in parallel, mutations (mkdir, write, rm, mv) are exclusive.
// =============================================================================

final class FsException extends RuntimeException {
    FsException(String message) {
        super(message);
    }
}

sealed abstract class FsNode permits FsFile, FsDirectory {
    String name;
    FsDirectory parent;

    FsNode(String name, FsDirectory parent) {
        this.name = name;
        this.parent = parent;
    }

    abstract long size();

    String path() {
        if (parent == null) return "/";
        String parentPath = parent.path();
        return (parentPath.equals("/") ? "" : parentPath) + "/" + name;
    }
}

final class FsFile extends FsNode {
    private final StringBuilder content = new StringBuilder();

    FsFile(String name, FsDirectory parent) {
        super(name, parent);
    }

    void write(String text, boolean append) {
        if (!append) content.setLength(0);
        content.append(text);
    }

    String read() {
        return content.toString();
    }

    @Override
    long size() {
        return content.length();
    }
}

final class FsDirectory extends FsNode {
    final Map<String, FsNode> children = new TreeMap<>();   // sorted, so ls needs no sort

    FsDirectory(String name, FsDirectory parent) {
        super(name, parent);
    }

    @Override
    long size() {
        long total = 0;
        for (FsNode child : children.values()) total += child.size();
        return total;
    }
}

public final class FileSystem {
    private final FsDirectory root = new FsDirectory("", null);
    private final ReentrantReadWriteLock rw = new ReentrantReadWriteLock();

    /** Creates every missing directory on the path, like mkdir -p. */
    public void mkdirs(String path) {
        rw.writeLock().lock();
        try {
            FsDirectory dir = root;
            for (String part : parse(path)) {
                FsNode next = dir.children.get(part);
                if (next == null) {
                    next = new FsDirectory(part, dir);
                    dir.children.put(part, next);
                } else if (next instanceof FsFile) {
                    throw new FsException("not a directory: " + next.path());
                }
                dir = (FsDirectory) next;
            }
        } finally {
            rw.writeLock().unlock();
        }
    }

    public void write(String path, String text, boolean append) {
        rw.writeLock().lock();
        try {
            List<String> parts = parse(path);
            if (parts.isEmpty()) throw new FsException("is a directory: /");
            FsDirectory dir = directory(parts.subList(0, parts.size() - 1));
            String name = parts.get(parts.size() - 1);
            FsNode node = dir.children.get(name);
            if (node instanceof FsDirectory) throw new FsException("is a directory: " + node.path());
            FsFile file = node == null ? new FsFile(name, dir) : (FsFile) node;
            dir.children.put(name, file);
            file.write(text, append);
        } finally {
            rw.writeLock().unlock();
        }
    }

    public String cat(String path) {
        rw.readLock().lock();
        try {
            if (resolve(path) instanceof FsFile f) return f.read();
            throw new FsException("is a directory: " + path);
        } finally {
            rw.readLock().unlock();
        }
    }

    /** A directory lists its children in name order; a file lists itself. */
    public List<String> ls(String path) {
        rw.readLock().lock();
        try {
            FsNode node = resolve(path);
            if (node instanceof FsDirectory d) {
                List<String> out = new ArrayList<>();
                for (FsNode c : d.children.values()) out.add(c instanceof FsDirectory ? c.name + "/" : c.name);
                return out;
            }
            return List.of(node.name);
        } finally {
            rw.readLock().unlock();
        }
    }

    public long du(String path) {
        rw.readLock().lock();
        try {
            return resolve(path).size();
        } finally {
            rw.readLock().unlock();
        }
    }

    public void rm(String path, boolean recursive) {
        rw.writeLock().lock();
        try {
            FsNode node = resolve(path);
            if (node == root) throw new FsException("refusing to remove /");
            if (node instanceof FsDirectory d && !d.children.isEmpty() && !recursive) {
                throw new FsException("directory not empty: " + node.path());
            }
            node.parent.children.remove(node.name);   // the subtree becomes garbage in one step
        } finally {
            rw.writeLock().unlock();
        }
    }

    public void mv(String from, String to) {
        rw.writeLock().lock();
        try {
            FsNode node = resolve(from);
            if (node == root) throw new FsException("cannot move /");
            List<String> target = parse(to);
            if (target.isEmpty()) throw new FsException("target exists: /");
            FsDirectory dest = directory(target.subList(0, target.size() - 1));
            for (FsDirectory d = dest; d != null; d = d.parent) {
                if (d == node) throw new FsException("cannot move " + node.path() + " into itself");
            }
            String newName = target.get(target.size() - 1);
            if (dest.children.containsKey(newName)) throw new FsException("target exists: " + to);
            node.parent.children.remove(node.name);
            node.name = newName;
            node.parent = dest;
            dest.children.put(newName, node);
        } finally {
            rw.writeLock().unlock();
        }
    }

    /** Depth-first walk with an explicit stack, so deep trees cannot overflow the call stack. */
    public List<String> find(String path, Predicate<FsNode> match) {
        rw.readLock().lock();
        try {
            List<String> out = new ArrayList<>();
            Deque<FsNode> stack = new ArrayDeque<>();
            stack.push(resolve(path));
            while (!stack.isEmpty()) {
                FsNode n = stack.pop();
                if (match.test(n)) out.add(n.path());
                if (n instanceof FsDirectory d) {
                    List<FsNode> kids = new ArrayList<>(d.children.values());
                    for (int i = kids.size() - 1; i >= 0; i--) stack.push(kids.get(i));   // keep name order
                }
            }
            return out;
        } finally {
            rw.readLock().unlock();
        }
    }

    // ----- path handling -----

    /** Splits an absolute path and resolves "." and ".." lexically. */
    static List<String> parse(String path) {
        if (path == null || !path.startsWith("/")) throw new FsException("path must be absolute: " + path);
        List<String> parts = new ArrayList<>();
        for (String p : path.split("/")) {
            if (p.isEmpty() || p.equals(".")) continue;
            if (p.equals("..")) {
                if (!parts.isEmpty()) parts.remove(parts.size() - 1);
                continue;
            }
            parts.add(p);
        }
        return parts;
    }

    private FsNode resolve(String path) {
        FsNode node = root;
        for (String part : parse(path)) {
            if (!(node instanceof FsDirectory d)) throw new FsException("not a directory: " + node.path());
            node = d.children.get(part);
            if (node == null) throw new FsException("no such file or directory: " + path);
        }
        return node;
    }

    private FsDirectory directory(List<String> parts) {
        FsNode node = root;
        for (String part : parts) {
            if (!(node instanceof FsDirectory d)) throw new FsException("not a directory: " + node.path());
            node = d.children.get(part);
            if (node == null) throw new FsException("no such directory: /" + String.join("/", parts));
        }
        if (node instanceof FsDirectory d) return d;
        throw new FsException("not a directory: " + node.path());
    }

    public static void main(String[] args) {
        FileSystem fs = new FileSystem();
        fs.mkdirs("/home/alice/docs");
        fs.mkdirs("/home/bob");
        fs.write("/home/alice/docs/notes.txt", "hello", false);
        fs.write("/home/alice/docs/notes.txt", " world", true);
        fs.write("/home/alice/todo.md", "- ship it", false);
        fs.write("/home/bob/build.log", "OK", false);

        System.out.println("ls /home -> " + fs.ls("/home"));
        System.out.println("ls /home/alice -> " + fs.ls("/home/alice"));
        System.out.println("cat notes.txt -> " + fs.cat("/home/alice/docs/notes.txt"));
        System.out.println("cat via .. -> " + fs.cat("/home/bob/../alice/./docs/notes.txt"));
        System.out.println("du /home/alice -> " + fs.du("/home/alice") + " bytes");
        System.out.println("du / -> " + fs.du("/") + " bytes");
        System.out.println("find *.txt or *.md -> "
                + fs.find("/", n -> n instanceof FsFile && (n.name.endsWith(".txt") || n.name.endsWith(".md"))));

        fs.mv("/home/alice/docs", "/home/bob/docs");
        System.out.println("after mv, ls /home/bob -> " + fs.ls("/home/bob"));
        System.out.println("du /home/bob -> " + fs.du("/home/bob") + " bytes");

        String[][] bad = {
            {"mv", "/home/bob", "/home/bob/docs/inner"},
            {"rm", "/home/bob", ""},
            {"cat", "/home/alice", ""},
            {"mkdirs", "/home/bob/build.log/x", ""},
        };
        for (String[] op : bad) {
            try {
                switch (op[0]) {
                    case "mv" -> fs.mv(op[1], op[2]);
                    case "rm" -> fs.rm(op[1], false);
                    case "cat" -> fs.cat(op[1]);
                    default -> fs.mkdirs(op[1]);
                }
            } catch (FsException e) {
                System.out.println(op[0] + " rejected: " + e.getMessage());
            }
        }
        fs.rm("/home/bob", true);
        System.out.println("after rm -r /home/bob, ls /home -> " + fs.ls("/home") + ", du / -> " + fs.du("/"));
    }
}
