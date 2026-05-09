package com.rutik.systemdesign.lld.behavioral.chain_of_responsibility; /**
 * Chain of Responsibility Pattern - Real World Example
 *
 * Scenario: HTTP Middleware Pipeline
 *
 * When an HTTP request arrives it passes through a chain of middleware handlers:
 *   Authentication -> Authorization -> Logging -> Rate Limiting -> [actual handler]
 *
 * Each middleware either short-circuits (rejects) the request or forwards it
 * to the next handler. This mirrors how web frameworks (Express, Spring, etc.)
 * work internally.
 */

import java.util.HashMap;
import java.util.Map;

// ─── Domain Objects ──────────────────────────────────────────────────────────

/**
 * Represents an incoming HTTP request with metadata the middleware inspects.
 */
class HttpRequest {
    private final String method;
    private final String path;
    private final String authToken;   // e.g. "Bearer <token>"
    private final String role;        // user role attached after auth
    private final String clientIp;

    public HttpRequest(String method, String path, String authToken,
                       String role, String clientIp) {
        this.method    = method;
        this.authToken = authToken;
        this.role      = role;
        this.clientIp  = clientIp;
        this.path      = path;
    }

    public String getMethod()    { return method; }
    public String getPath()      { return path; }
    public String getAuthToken() { return authToken; }
    public String getRole()      { return role; }
    public String getClientIp()  { return clientIp; }

    @Override
    public String toString() {
        return method + " " + path + " [ip=" + clientIp + ", role=" + role + "]";
    }
}

/**
 * Simple wrapper that indicates whether the pipeline succeeded or was rejected.
 */
class HttpResponse {
    private int    statusCode;
    private String body;

    public HttpResponse(int statusCode, String body) {
        this.statusCode = statusCode;
        this.body       = body;
    }

    // The response is passed by reference through the chain so handlers can write to it.
    public void set(int code, String message) {
        this.statusCode = code;
        this.body       = message;
    }

    @Override
    public String toString() {
        return "HTTP " + statusCode + " - " + body;
    }
}

// ─── Middleware (Handler) ─────────────────────────────────────────────────────

/**
 * Abstract middleware handler. Each concrete middleware calls next() to continue
 * the pipeline or writes to the response and stops the chain.
 */
abstract class Middleware {

    private Middleware next;

    /** Fluent builder for chaining: auth.linkWith(authz).linkWith(logger)... */
    public Middleware linkWith(Middleware next) {
        this.next = next;
        return next;
    }

    /**
     * Process the request. Implementations should either:
     *   - Handle/reject the request and return, OR
     *   - Call forward(request, response) to continue the chain.
     */
    public abstract void process(HttpRequest request, HttpResponse response);

    /** Passes the request to the next middleware in the chain. */
    protected void forward(HttpRequest request, HttpResponse response) {
        if (next != null) {
            next.process(request, response);
        } else {
            // End of middleware chain — final handler reached
            response.set(200, "OK — Request processed successfully for path: " + request.getPath());
            System.out.println("  [Final Handler] Request reached business logic.");
        }
    }
}

// ─── Concrete Middlewares ─────────────────────────────────────────────────────

/**
 * 1. Authentication Middleware
 *
 * Validates that the request carries a recognised auth token.
 * Rejects with 401 if token is missing or invalid.
 */
class AuthenticationMiddleware extends Middleware {

    // Simulated token store: token -> username
    private static final Map<String, String> VALID_TOKENS = new HashMap<>();
    static {
        VALID_TOKENS.put("token-alice", "alice");
        VALID_TOKENS.put("token-bob",   "bob");
        VALID_TOKENS.put("token-admin", "admin");
    }

    @Override
    public void process(HttpRequest request, HttpResponse response) {
        System.out.println("  [Authentication] Checking token: " + request.getAuthToken());

        if (request.getAuthToken() == null || request.getAuthToken().isEmpty()) {
            response.set(401, "Unauthorized — missing auth token");
            System.out.println("  [Authentication] REJECTED — no token provided.");
            return; // short-circuit; do not forward
        }

        if (!VALID_TOKENS.containsKey(request.getAuthToken())) {
            response.set(401, "Unauthorized — invalid token");
            System.out.println("  [Authentication] REJECTED — token not recognised.");
            return;
        }

        System.out.println("  [Authentication] PASSED — user: " + VALID_TOKENS.get(request.getAuthToken()));
        forward(request, response);
    }
}

/**
 * 2. Authorization Middleware
 *
 * Checks that the authenticated user has the right role to access the resource.
 * Admin-only paths require the "admin" role.
 */
class AuthorizationMiddleware extends Middleware {

    @Override
    public void process(HttpRequest request, HttpResponse response) {
        System.out.println("  [Authorization] Checking role '" + request.getRole()
                + "' for path: " + request.getPath());

        boolean isAdminPath = request.getPath().startsWith("/admin");
        boolean isAdmin     = "admin".equalsIgnoreCase(request.getRole());

        if (isAdminPath && !isAdmin) {
            response.set(403, "Forbidden — admin role required for " + request.getPath());
            System.out.println("  [Authorization] REJECTED — insufficient privileges.");
            return;
        }

        System.out.println("  [Authorization] PASSED.");
        forward(request, response);
    }
}

/**
 * 3. Logging Middleware
 *
 * Records request metadata. Never rejects requests — always forwards.
 */
class LoggingMiddleware extends Middleware {

    @Override
    public void process(HttpRequest request, HttpResponse response) {
        System.out.println("  [Logging] LOG >> " + request);
        // Logging middleware always passes through
        forward(request, response);
        // Post-processing: log the response after the chain returns
        System.out.println("  [Logging] Response >> " + response);
    }
}

/**
 * 4. Rate Limiting Middleware
 *
 * Tracks request counts per IP. Rejects with 429 when the limit is exceeded.
 */
class RateLimitingMiddleware extends Middleware {

    private static final int MAX_REQUESTS = 3; // low limit for demo purposes
    private final Map<String, Integer> requestCounts = new HashMap<>();

    @Override
    public void process(HttpRequest request, HttpResponse response) {
        String ip    = request.getClientIp();
        int    count = requestCounts.getOrDefault(ip, 0) + 1;
        requestCounts.put(ip, count);

        System.out.println("  [RateLimiting] IP " + ip + " has made " + count + " request(s).");

        if (count > MAX_REQUESTS) {
            response.set(429, "Too Many Requests — rate limit exceeded for IP: " + ip);
            System.out.println("  [RateLimiting] REJECTED — limit of " + MAX_REQUESTS + " exceeded.");
            return;
        }

        System.out.println("  [RateLimiting] PASSED.");
        forward(request, response);
    }
}

// ─── Main ─────────────────────────────────────────────────────────────────────

public class RealWorldExample {

    /** Helper to send a request through the pipeline and print the result. */
    private static void send(Middleware pipeline, HttpRequest request) {
        System.out.println("\n>>> " + request);
        HttpResponse response = new HttpResponse(0, "");
        pipeline.process(request, response);
        System.out.println("<<< " + response);
    }

    public static void main(String[] args) {
        System.out.println("=== HTTP Middleware Chain (Chain of Responsibility) ===");

        // ── Build the middleware chain ──────────────────────────────────────
        // Authentication -> Authorization -> Logging -> RateLimiting -> [handler]
        AuthenticationMiddleware auth      = new AuthenticationMiddleware();
        AuthorizationMiddleware  authz     = new AuthorizationMiddleware();
        LoggingMiddleware        logger    = new LoggingMiddleware();
        RateLimitingMiddleware   rateLimit = new RateLimitingMiddleware();

        auth.linkWith(authz).linkWith(logger).linkWith(rateLimit);

        // ── Test scenarios ──────────────────────────────────────────────────

        // 1. No token — rejected at Authentication
        System.out.println("\n--- Scenario 1: Missing token ---");
        send(auth, new HttpRequest("GET", "/api/data", "", "user", "10.0.0.1"));

        // 2. Invalid token — rejected at Authentication
        System.out.println("\n--- Scenario 2: Invalid token ---");
        send(auth, new HttpRequest("GET", "/api/data", "bad-token", "user", "10.0.0.1"));

        // 3. Valid user token on admin path — rejected at Authorization
        System.out.println("\n--- Scenario 3: Insufficient role for admin path ---");
        send(auth, new HttpRequest("GET", "/admin/dashboard", "token-alice", "user", "10.0.0.2"));

        // 4. Admin token on admin path — all middleware pass
        System.out.println("\n--- Scenario 4: Admin accessing admin path ---");
        send(auth, new HttpRequest("GET", "/admin/dashboard", "token-admin", "admin", "10.0.0.3"));

        // 5. Rate limiting — same IP hammers the endpoint
        System.out.println("\n--- Scenario 5: Rate limiting (same IP, 4 requests) ---");
        for (int i = 1; i <= 4; i++) {
            System.out.println("\n  [Request " + i + "]");
            send(auth, new HttpRequest("GET", "/api/data", "token-bob", "user", "10.0.0.4"));
        }
    }
}
