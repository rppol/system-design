package com.rutik.systemdesign.lld.creational.builder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BUILDER PATTERN — Real World Example
 *
 * Scenario: HTTP Request Builder
 *
 * Problem:
 *   Constructing HTTP requests involves many parameters: method, URL, headers,
 *   query params, body, timeouts, authentication, retry policy, etc. Most
 *   parameters are optional and have sensible defaults. A constructor with 10+
 *   parameters is unreadable and error-prone (which timeout is the first arg?).
 *   Creating overloaded constructors for each combination explodes combinatorially.
 *
 * Solution:
 *   A fluent HttpRequest.Builder that accumulates configuration step-by-step and
 *   validates everything in the terminal build() call. The caller only sets what
 *   they need; defaults handle the rest. The resulting HttpRequest is immutable.
 *
 * Run: javac RealWorldExample.java && java HttpRequestBuilderDemo
 */

// ─────────────────────────────────────────────────────────────────────────────
// Supporting enums and types
// ─────────────────────────────────────────────────────────────────────────────
enum HttpMethod {
    GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS
}

enum BodyType {
    NONE, JSON, FORM_URLENCODED, MULTIPART, RAW_TEXT
}

// Immutable value object for retry configuration
class RetryPolicy {

    private final int maxAttempts;
    private final long backoffMs;
    private final boolean retryOnServerError;

    private RetryPolicy(int maxAttempts, long backoffMs, boolean retryOnServerError) {
        this.maxAttempts = maxAttempts;
        this.backoffMs = backoffMs;
        this.retryOnServerError = retryOnServerError;
    }

    public static RetryPolicy noRetry() {
        return new RetryPolicy(1, 0, false);
    }

    public static RetryPolicy withRetries(int maxAttempts, long backoffMs, boolean retryOnServerError) {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");
        return new RetryPolicy(maxAttempts, backoffMs, retryOnServerError);
    }

    @Override
    public String toString() {
        if (maxAttempts == 1) return "RetryPolicy{disabled}";
        return "RetryPolicy{maxAttempts=" + maxAttempts
                + ", backoffMs=" + backoffMs
                + ", retryOnServerError=" + retryOnServerError + "}";
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Product: HttpRequest — immutable once constructed
// ─────────────────────────────────────────────────────────────────────────────
class HttpRequest {

    // Required
    private final HttpMethod method;
    private final String url;

    // Optional — all have defaults
    private final Map<String, String> headers;
    private final Map<String, String> queryParams;
    private final String body;
    private final BodyType bodyType;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final String authToken;
    private final RetryPolicy retryPolicy;
    private final boolean followRedirects;

    // Private — use Builder
    private HttpRequest(Builder builder) {
        this.method            = builder.method;
        this.url               = builder.url;
        this.headers           = Collections.unmodifiableMap(new HashMap<>(builder.headers));
        this.queryParams       = Collections.unmodifiableMap(new HashMap<>(builder.queryParams));
        this.body              = builder.body;
        this.bodyType          = builder.bodyType;
        this.connectTimeoutMs  = builder.connectTimeoutMs;
        this.readTimeoutMs     = builder.readTimeoutMs;
        this.authToken         = builder.authToken;
        this.retryPolicy       = builder.retryPolicy;
        this.followRedirects   = builder.followRedirects;
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public HttpMethod getMethod()           { return method; }
    public String getUrl()                  { return url; }
    public Map<String, String> getHeaders() { return headers; }
    public Map<String, String> getQueryParams() { return queryParams; }
    public String getBody()                 { return body; }
    public BodyType getBodyType()           { return bodyType; }
    public int getConnectTimeoutMs()        { return connectTimeoutMs; }
    public int getReadTimeoutMs()           { return readTimeoutMs; }
    public String getAuthToken()            { return authToken; }
    public RetryPolicy getRetryPolicy()     { return retryPolicy; }
    public boolean isFollowRedirects()      { return followRedirects; }

    /**
     * Produces the full request URL including query parameters.
     */
    public String getFullUrl() {
        if (queryParams.isEmpty()) return url;
        StringBuilder sb = new StringBuilder(url).append("?");
        queryParams.forEach((k, v) -> sb.append(k).append("=").append(v).append("&"));
        return sb.substring(0, sb.length() - 1); // trim trailing &
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("HttpRequest {\n");
        sb.append("  method:         ").append(method).append("\n");
        sb.append("  url:            ").append(getFullUrl()).append("\n");
        sb.append("  headers:        ").append(headers).append("\n");
        sb.append("  body:           ").append(body == null ? "(none)" : body).append("\n");
        sb.append("  bodyType:       ").append(bodyType).append("\n");
        sb.append("  connectTimeout: ").append(connectTimeoutMs).append("ms\n");
        sb.append("  readTimeout:    ").append(readTimeoutMs).append("ms\n");
        sb.append("  authToken:      ").append(authToken == null ? "(none)" : "[REDACTED]").append("\n");
        sb.append("  retryPolicy:    ").append(retryPolicy).append("\n");
        sb.append("  followRedirects:").append(followRedirects).append("\n");
        sb.append("}");
        return sb.toString();
    }

    // ── Fluent Builder ─────────────────────────────────────────────────────────
    static class Builder {

        // Required
        private final HttpMethod method;
        private final String url;

        // Optional with defaults
        private Map<String, String> headers       = new HashMap<>();
        private Map<String, String> queryParams   = new HashMap<>();
        private String body                       = null;
        private BodyType bodyType                 = BodyType.NONE;
        private int connectTimeoutMs              = 5_000;
        private int readTimeoutMs                 = 30_000;
        private String authToken                  = null;
        private RetryPolicy retryPolicy           = RetryPolicy.noRetry();
        private boolean followRedirects           = true;

        public Builder(HttpMethod method, String url) {
            if (method == null) throw new IllegalArgumentException("method is required");
            if (url == null || url.isBlank()) throw new IllegalArgumentException("url is required");
            this.method = method;
            this.url = url;
        }

        public Builder header(String name, String value) {
            this.headers.put(name, value);
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers.putAll(headers);
            return this;
        }

        public Builder queryParam(String name, String value) {
            this.queryParams.put(name, value);
            return this;
        }

        public Builder jsonBody(String jsonBody) {
            this.body = jsonBody;
            this.bodyType = BodyType.JSON;
            // Automatically set Content-Type if not already set
            this.headers.putIfAbsent("Content-Type", "application/json");
            return this;
        }

        public Builder formBody(String formBody) {
            this.body = formBody;
            this.bodyType = BodyType.FORM_URLENCODED;
            this.headers.putIfAbsent("Content-Type", "application/x-www-form-urlencoded");
            return this;
        }

        public Builder rawBody(String body) {
            this.body = body;
            this.bodyType = BodyType.RAW_TEXT;
            return this;
        }

        public Builder connectTimeoutMs(int ms) {
            if (ms < 0) throw new IllegalArgumentException("connectTimeoutMs must be >= 0");
            this.connectTimeoutMs = ms;
            return this;
        }

        public Builder readTimeoutMs(int ms) {
            if (ms < 0) throw new IllegalArgumentException("readTimeoutMs must be >= 0");
            this.readTimeoutMs = ms;
            return this;
        }

        public Builder bearerAuth(String token) {
            this.authToken = token;
            this.headers.put("Authorization", "Bearer " + token);
            return this;
        }

        public Builder basicAuth(String username, String password) {
            // Base64 would be used in real code — simplified here
            this.authToken = username + ":" + password;
            this.headers.put("Authorization", "Basic [encoded:" + username + "]");
            return this;
        }

        public Builder retryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = retryPolicy;
            return this;
        }

        public Builder noFollowRedirects() {
            this.followRedirects = false;
            return this;
        }

        public HttpRequest build() {
            // ── Cross-field validation ────────────────────────────────────────
            if ((method == HttpMethod.GET || method == HttpMethod.HEAD)
                    && body != null) {
                throw new IllegalStateException(method + " requests must not have a body");
            }
            if ((method == HttpMethod.POST || method == HttpMethod.PUT)
                    && body == null) {
                System.out.println("  [Builder] Warning: " + method + " request has no body");
            }
            // Add a default Accept header if none was specified
            headers.putIfAbsent("Accept", "application/json");
            return new HttpRequest(this);
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Director: HttpRequestFactory — pre-baked request templates
// ─────────────────────────────────────────────────────────────────────────────
class HttpRequestFactory {

    private static final String BASE_URL = "https://api.example.com/v1";
    private static final String API_TOKEN = "secret-api-token-xyz";

    /** Standard authenticated GET request with sensible timeouts and retry. */
    public static HttpRequest.Builder authenticatedGet(String endpoint) {
        return new HttpRequest.Builder(HttpMethod.GET, BASE_URL + endpoint)
                .bearerAuth(API_TOKEN)
                .header("Accept", "application/json")
                .connectTimeoutMs(3_000)
                .readTimeoutMs(15_000)
                .retryPolicy(RetryPolicy.withRetries(3, 500, true));
    }

    /** Standard authenticated POST request for JSON payloads. */
    public static HttpRequest.Builder authenticatedPost(String endpoint) {
        return new HttpRequest.Builder(HttpMethod.POST, BASE_URL + endpoint)
                .bearerAuth(API_TOKEN)
                .header("Accept", "application/json")
                .connectTimeoutMs(3_000)
                .readTimeoutMs(30_000)
                .retryPolicy(RetryPolicy.withRetries(2, 1_000, false));
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Simulated HTTP Client — executes the request (for demo purposes)
// ─────────────────────────────────────────────────────────────────────────────
class HttpClient {

    public String execute(HttpRequest request) {
        System.out.println("  => " + request.getMethod() + " " + request.getFullUrl());
        System.out.println("     Headers: " + request.getHeaders());
        if (request.getBody() != null) {
            System.out.println("     Body: " + request.getBody());
        }
        System.out.println("     Timeouts: connect=" + request.getConnectTimeoutMs()
                + "ms, read=" + request.getReadTimeoutMs() + "ms");
        System.out.println("     Retry: " + request.getRetryPolicy());
        // Simulated response
        return "HTTP/1.1 200 OK";
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Demo Entry Point
// ─────────────────────────────────────────────────────────────────────────────
public class RealWorldExample {

    public static void main(String[] args) {
        System.out.println("=== Builder Pattern: HTTP Request Builder Demo ===\n");

        HttpClient client = new HttpClient();

        // ── Simple GET — only required params ─────────────────────────────────
        System.out.println("--- 1. Simple GET request ---");
        HttpRequest getUsers = new HttpRequest.Builder(HttpMethod.GET, "https://api.example.com/users")
                .build();
        System.out.println(getUsers);
        System.out.println("Executing: " + client.execute(getUsers));

        // ── GET with auth, query params, custom timeout ────────────────────────
        System.out.println("\n--- 2. GET with auth + query params + custom timeout ---");
        HttpRequest searchRequest = new HttpRequest.Builder(HttpMethod.GET, "https://api.example.com/v1/products")
                .bearerAuth("my-api-token")
                .queryParam("category", "electronics")
                .queryParam("inStock", "true")
                .queryParam("page", "2")
                .readTimeoutMs(10_000)
                .retryPolicy(RetryPolicy.withRetries(3, 500, true))
                .build();
        System.out.println(searchRequest);
        System.out.println("Executing: " + client.execute(searchRequest));

        // ── POST with JSON body ────────────────────────────────────────────────
        System.out.println("\n--- 3. POST with JSON body ---");
        HttpRequest createOrder = new HttpRequest.Builder(HttpMethod.POST, "https://api.example.com/v1/orders")
                .bearerAuth("my-api-token")
                .jsonBody("{\"userId\": 42, \"item\": \"Laptop\", \"quantity\": 1}")
                .retryPolicy(RetryPolicy.withRetries(2, 1_000, false))
                .build();
        System.out.println(createOrder);
        System.out.println("Executing: " + client.execute(createOrder));

        // ── Using Director (pre-baked factory methods) ─────────────────────────
        System.out.println("\n--- 4. Using HttpRequestFactory (Director pattern) ---");
        HttpRequest userRequest = HttpRequestFactory.authenticatedGet("/users/42")
                .queryParam("fields", "id,name,email")
                .build();
        System.out.println("Executing: " + client.execute(userRequest));

        HttpRequest updateRequest = HttpRequestFactory.authenticatedPost("/users/42")
                .jsonBody("{\"email\": \"newemail@example.com\"}")
                .build();
        System.out.println("Executing: " + client.execute(updateRequest));

        // ── Validation: GET with body should throw ─────────────────────────────
        System.out.println("\n--- 5. Validation: GET request with body (should throw) ---");
        try {
            HttpRequest invalid = new HttpRequest.Builder(HttpMethod.GET, "https://api.example.com/test")
                    .rawBody("this should not be here")
                    .build();
        } catch (IllegalStateException e) {
            System.out.println("Caught expected validation error: " + e.getMessage());
        }

        // ── Immutability check ────────────────────────────────────────────────
        System.out.println("\n--- 6. Immutability: modifying returned headers map has no effect ---");
        HttpRequest req = new HttpRequest.Builder(HttpMethod.GET, "https://api.example.com/test")
                .header("X-Custom", "value")
                .build();
        try {
            req.getHeaders().put("X-Injected", "attack"); // should throw
        } catch (UnsupportedOperationException e) {
            System.out.println("Headers map is immutable — modification blocked.");
        }

        System.out.println("\n=== Demo complete ===");
    }
}
