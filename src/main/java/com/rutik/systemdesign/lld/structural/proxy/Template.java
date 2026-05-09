package com.rutik.systemdesign.lld.structural.proxy;

/**
 * PROXY PATTERN - Template / Skeleton
 *
 * Intent: Provide a surrogate or placeholder for another object to control access to it.
 *
 * Types of Proxies:
 *   1. Virtual Proxy    - lazy initialization of expensive objects
 *   2. Protection Proxy - access control based on permissions
 *   3. Remote Proxy     - local representative for a remote object
 *   4. Logging Proxy    - adds logging/auditing around real subject calls
 *   5. Caching Proxy    - caches results of expensive operations
 *
 * Structure:
 *   Subject      <<interface>>  - common interface for RealSubject and Proxy
 *   RealSubject               - the real object the proxy represents
 *   Proxy                     - maintains reference to RealSubject; controls access
 */

// ─────────────────────────────────────────────────────────────
// 1. Subject Interface
// ─────────────────────────────────────────────────────────────
interface Subject {
    void request();
    String getData(String key);
}

// ─────────────────────────────────────────────────────────────
// 2. RealSubject - the actual implementation (may be expensive to create)
// ─────────────────────────────────────────────────────────────
class RealSubject implements Subject {

    public RealSubject() {
        // Simulate expensive initialization (DB connection, file load, etc.)
        System.out.println("RealSubject: Expensive initialization done.");
    }

    @Override
    public void request() {
        System.out.println("RealSubject: Handling request.");
    }

    @Override
    public String getData(String key) {
        System.out.println("RealSubject: Fetching data for key=" + key);
        return "value_for_" + key;
    }
}

// ─────────────────────────────────────────────────────────────
// 3. Virtual Proxy - defers creation of RealSubject until needed
// ─────────────────────────────────────────────────────────────
class VirtualProxy implements Subject {

    private RealSubject realSubject;  // null until first use

    @Override
    public void request() {
        // Lazy initialization
        if (realSubject == null) {
            realSubject = new RealSubject();
        }
        realSubject.request();
    }

    @Override
    public String getData(String key) {
        if (realSubject == null) {
            realSubject = new RealSubject();
        }
        return realSubject.getData(key);
    }
}

// ─────────────────────────────────────────────────────────────
// 4. Protection Proxy - controls access based on caller permissions
// ─────────────────────────────────────────────────────────────
class ProtectionProxy implements Subject {

    private final RealSubject realSubject;
    private final String callerRole;  // "ADMIN", "USER", "GUEST"

    public ProtectionProxy(String callerRole) {
        this.realSubject = new RealSubject();
        this.callerRole = callerRole;
    }

    @Override
    public void request() {
        if ("ADMIN".equals(callerRole) || "USER".equals(callerRole)) {
            realSubject.request();
        } else {
            throw new SecurityException("Access denied for role: " + callerRole);
        }
    }

    @Override
    public String getData(String key) {
        if ("ADMIN".equals(callerRole)) {
            return realSubject.getData(key);
        } else {
            throw new SecurityException("getData requires ADMIN role. Current role: " + callerRole);
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 5. Logging Proxy - adds audit trail around real subject calls
// ─────────────────────────────────────────────────────────────
class LoggingProxy implements Subject {

    private final RealSubject realSubject;

    public LoggingProxy() {
        this.realSubject = new RealSubject();
    }

    @Override
    public void request() {
        System.out.println("[LOG] Before request() call");
        long start = System.currentTimeMillis();
        realSubject.request();
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("[LOG] After request() — elapsed=" + elapsed + "ms");
    }

    @Override
    public String getData(String key) {
        System.out.println("[LOG] getData called with key=" + key);
        String result = realSubject.getData(key);
        System.out.println("[LOG] getData returned: " + result);
        return result;
    }
}

// ─────────────────────────────────────────────────────────────
// 6. Caching Proxy - memoizes expensive calls
// ─────────────────────────────────────────────────────────────
class CachingProxy implements Subject {

    private final RealSubject realSubject;
    private final java.util.Map<String, String> cache = new java.util.HashMap<>();

    public CachingProxy() {
        this.realSubject = new RealSubject();
    }

    @Override
    public void request() {
        realSubject.request();
    }

    @Override
    public String getData(String key) {
        if (cache.containsKey(key)) {
            System.out.println("[CACHE HIT] key=" + key);
            return cache.get(key);
        }
        System.out.println("[CACHE MISS] key=" + key);
        String value = realSubject.getData(key);
        cache.put(key, value);
        return value;
    }
}

// ─────────────────────────────────────────────────────────────
// 7. Client / Demo
// ─────────────────────────────────────────────────────────────
class ProxyTemplateDemo {

    public static void main(String[] args) {
        System.out.println("=== Virtual Proxy (lazy init) ===");
        Subject virtual = new VirtualProxy();
        System.out.println("Proxy created — RealSubject NOT yet initialized");
        virtual.request(); // RealSubject created here
        virtual.request(); // reuses existing instance

        System.out.println("\n=== Protection Proxy ===");
        Subject adminProxy = new ProtectionProxy("ADMIN");
        adminProxy.request();
        System.out.println(adminProxy.getData("secret"));

        Subject guestProxy = new ProtectionProxy("GUEST");
        try {
            guestProxy.request();
        } catch (SecurityException e) {
            System.out.println("Caught: " + e.getMessage());
        }

        System.out.println("\n=== Logging Proxy ===");
        Subject loggingProxy = new LoggingProxy();
        loggingProxy.request();
        loggingProxy.getData("user123");

        System.out.println("\n=== Caching Proxy ===");
        Subject cachingProxy = new CachingProxy();
        cachingProxy.getData("profile");  // cache miss -> calls real
        cachingProxy.getData("profile");  // cache hit -> no real call
        cachingProxy.getData("settings"); // cache miss
    }
}
