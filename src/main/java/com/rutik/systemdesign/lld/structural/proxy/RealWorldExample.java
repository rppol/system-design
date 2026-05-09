package com.rutik.systemdesign.lld.structural.proxy; /**
 * PROXY PATTERN - Real World Examples
 *
 * Example 1: Image Lazy-Loading Proxy
 *   - Loading high-resolution images is expensive (I/O, memory)
 *   - ImageProxy delays loading until the image is actually displayed
 *   - Caches the loaded image so subsequent displays are fast
 *
 * Example 2: Database Access Control Proxy
 *   - Wraps a DatabaseService with role-based access control
 *   - Admin users: full read/write access
 *   - Regular users: read-only access
 *   - Audit log on every operation
 */

import java.util.HashMap;
import java.util.Map;

// ══════════════════════════════════════════════════════════════
// EXAMPLE 1: Image Lazy-Loading Proxy
// ══════════════════════════════════════════════════════════════

interface Image {
    void display();
    String getFilename();
    int getWidth();
    int getHeight();
}

/** RealImage - expensive to create (simulates disk I/O and decoding) */
class RealImage implements Image {

    private final String filename;
    private int width;
    private int height;
    private byte[] pixelData;  // simulated in-memory image data

    public RealImage(String filename) {
        this.filename = filename;
        loadFromDisk();
    }

    private void loadFromDisk() {
        // Simulate expensive I/O
        System.out.println("  [RealImage] Loading '" + filename + "' from disk...");
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        this.width = 1920;
        this.height = 1080;
        this.pixelData = new byte[width * height * 3]; // 6MB for RGB
        System.out.println("  [RealImage] Loaded " + filename + " (" + width + "x" + height + ")");
    }

    @Override
    public void display() {
        System.out.println("  [RealImage] Displaying '" + filename + "' at " + width + "x" + height);
    }

    @Override public String getFilename() { return filename; }
    @Override public int getWidth()       { return width; }
    @Override public int getHeight()      { return height; }
}

/**
 * ImageProxy - Virtual + Caching proxy for images.
 * - Delays RealImage creation until display() is first called
 * - Caches the RealImage for subsequent calls
 */
class ImageProxy implements Image {

    private final String filename;
    private RealImage realImage;  // null until needed

    public ImageProxy(String filename) {
        this.filename = filename;
        System.out.println("  [ImageProxy] Proxy created for '" + filename + "' (not loaded yet)");
    }

    @Override
    public void display() {
        if (realImage == null) {
            System.out.println("  [ImageProxy] First display — loading image now");
            realImage = new RealImage(filename);
        } else {
            System.out.println("  [ImageProxy] Using cached image");
        }
        realImage.display();
    }

    // Proxy can return metadata without loading the real image
    @Override public String getFilename() { return filename; }
    @Override public int getWidth()  { return realImage != null ? realImage.getWidth()  : -1; }
    @Override public int getHeight() { return realImage != null ? realImage.getHeight() : -1; }
}

// ══════════════════════════════════════════════════════════════
// EXAMPLE 2: Database Access Control Proxy
// ══════════════════════════════════════════════════════════════

interface DatabaseService {
    String query(String sql);
    void execute(String sql);
    void deleteRecord(String table, int id);
}

/** RealDatabaseService - the actual database operations */
class RealDatabaseService implements DatabaseService {

    @Override
    public String query(String sql) {
        System.out.println("  [DB] Executing query: " + sql);
        return "ResultSet{rows=42}";
    }

    @Override
    public void execute(String sql) {
        System.out.println("  [DB] Executing statement: " + sql);
    }

    @Override
    public void deleteRecord(String table, int id) {
        System.out.println("  [DB] Deleting row id=" + id + " from table='" + table + "'");
    }
}

/** User roles */
enum Role { ADMIN, USER, READ_ONLY }

/**
 * SecurityProxy - Protection + Logging proxy for database access.
 * - ADMIN: full access (query, execute, delete)
 * - USER:  can query and execute, but NOT delete
 * - READ_ONLY: can only query
 * - All operations are audit-logged
 */
class SecurityProxy implements DatabaseService {

    private final DatabaseService realService;
    private final String username;
    private final Role role;
    private final java.util.List<String> auditLog = new java.util.ArrayList<>();

    public SecurityProxy(DatabaseService service, String username, Role role) {
        this.realService = service;
        this.username = username;
        this.role = role;
    }

    private void audit(String operation, String detail) {
        String entry = String.format("[AUDIT] user='%s' role=%s op=%s detail='%s'",
                username, role, operation, detail);
        auditLog.add(entry);
        System.out.println("  " + entry);
    }

    @Override
    public String query(String sql) {
        // All roles can query
        audit("QUERY", sql);
        return realService.query(sql);
    }

    @Override
    public void execute(String sql) {
        if (role == Role.READ_ONLY) {
            audit("EXECUTE_DENIED", sql);
            throw new SecurityException("User '" + username + "' (READ_ONLY) cannot execute write statements.");
        }
        audit("EXECUTE", sql);
        realService.execute(sql);
    }

    @Override
    public void deleteRecord(String table, int id) {
        if (role != Role.ADMIN) {
            audit("DELETE_DENIED", "table=" + table + " id=" + id);
            throw new SecurityException("User '" + username + "' (" + role + ") cannot delete records. ADMIN required.");
        }
        audit("DELETE", "table=" + table + " id=" + id);
        realService.deleteRecord(table, id);
    }

    public void printAuditLog() {
        System.out.println("\n  --- Audit Log for " + username + " ---");
        auditLog.forEach(e -> System.out.println("  " + e));
    }
}

// ══════════════════════════════════════════════════════════════
// Demo
// ══════════════════════════════════════════════════════════════
class ProxyRealWorldDemo {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║  Example 1: Image Lazy-Loading Proxy     ║");
        System.out.println("╚══════════════════════════════════════════╝");

        // Gallery loads many image proxies upfront — no actual disk I/O yet
        Image[] gallery = {
            new ImageProxy("vacation_photo.jpg"),
            new ImageProxy("wedding_photo.jpg"),
            new ImageProxy("profile_picture.jpg")
        };

        System.out.println("\nGallery created with " + gallery.length + " images (none loaded yet)");
        System.out.println("User scrolls to first image:");
        gallery[0].display();  // loads from disk

        System.out.println("\nUser views first image again:");
        gallery[0].display();  // uses cache — no disk I/O

        System.out.println("\nUser scrolls to second image:");
        gallery[1].display();  // loads from disk

        System.out.println("\n╔══════════════════════════════════════════╗");
        System.out.println("║  Example 2: DB Access Control Proxy      ║");
        System.out.println("╚══════════════════════════════════════════╝");

        DatabaseService realDb = new RealDatabaseService();

        // Admin user — full access
        System.out.println("\n--- Admin User ---");
        DatabaseService adminDb = new SecurityProxy(realDb, "alice", Role.ADMIN);
        adminDb.query("SELECT * FROM users");
        adminDb.execute("UPDATE users SET status='active' WHERE id=5");
        adminDb.deleteRecord("users", 99);

        // Regular user — read + write, no delete
        System.out.println("\n--- Regular User ---");
        DatabaseService userDb = new SecurityProxy(realDb, "bob", Role.USER);
        userDb.query("SELECT name FROM products");
        userDb.execute("INSERT INTO cart VALUES (1, 'item_x')");
        try {
            userDb.deleteRecord("products", 10);
        } catch (SecurityException e) {
            System.out.println("  SecurityException: " + e.getMessage());
        }

        // Read-only user — query only
        System.out.println("\n--- Read-Only User ---");
        DatabaseService readOnlyDb = new SecurityProxy(realDb, "charlie", Role.READ_ONLY);
        readOnlyDb.query("SELECT COUNT(*) FROM orders");
        try {
            readOnlyDb.execute("DROP TABLE orders");
        } catch (SecurityException e) {
            System.out.println("  SecurityException: " + e.getMessage());
        }

        ((SecurityProxy) adminDb).printAuditLog();
        ((SecurityProxy) userDb).printAuditLog();
        ((SecurityProxy) readOnlyDb).printAuditLog();
    }
}
