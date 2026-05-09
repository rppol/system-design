# Networking & HTTP Client

## 1. Concept Overview

Java has three generations of networking APIs. The original `java.net` API (blocking, per-connection thread model) was suitable for low-concurrency servers. Java NIO (Java 4, 2002) introduced non-blocking I/O with `Selector` and `Channel`, enabling the Reactor event-loop pattern used by Netty and Mina. The modern `java.net.http.HttpClient` (Java 11) provides a clean, high-level HTTP/1.1 and HTTP/2 client with both synchronous and asynchronous interfaces.

For server-side networking, NIO's `Selector` enables a single thread to monitor thousands of connections — the foundation of high-performance servers. With Java 21 virtual threads, the original blocking model scales to the same concurrency without NIO complexity.

---

## 2. Intuition

> **One-line analogy**: Blocking I/O is like a bank teller who sits idle while waiting for your account lookup — one teller per customer. NIO is like a teller who hands you a number and calls you when your account is ready — one teller for many customers. Virtual threads are like hiring millions of bank tellers that cost nothing when idle.

**Mental model**: Every network connection involves waiting — for DNS, TCP handshake, server processing, data transfer. In blocking I/O, waiting means a thread is parked doing nothing. In NIO, a `Selector` monitors multiple channels; when any becomes readable/writable, the single event-loop thread handles it. With virtual threads, the JVM unmounts blocked virtual threads from carrier threads, achieving the same concurrency as NIO without the complexity.

**Why it matters**: Understanding when to use blocking vs NIO vs virtual threads is a key system design decision. `HttpClient` is the standard for HTTP/1.1 and HTTP/2 in Java 11+. NIO's Reactor pattern is the architecture behind Netty, which powers gRPC, Spring WebFlux, and Kafka's network layer.

**Key insight**: NIO does NOT mean faster I/O transfer speed — the network is the bottleneck, not the Java API. NIO means fewer OS threads, which means lower memory and lower context-switch overhead. At 10,000 concurrent connections: blocking = 10,000 threads (~10GB stack), NIO = 1 thread, virtual threads = 10,000 virtual threads (~few MB total).

---

## 3. Core Principles

- **Blocking I/O**: Thread blocks on read/write; simple programming model; one thread per connection.
- **Non-blocking I/O**: Channel registered with `Selector`; `Selector.select()` returns when any channel is ready; single thread handles many.
- **Reactor pattern**: Event loop that demultiplexes I/O events and dispatches to handlers.
- **HTTP/2 multiplexing**: Multiple requests/responses over a single TCP connection via streams (frame IDs).
- **`CompletableFuture` for async**: `HttpClient.sendAsync()` returns `CompletableFuture<HttpResponse<T>>` — compose with `.thenApply()`, `.exceptionally()`.

---

## 4. Types / Architectures / Strategies

### 4.1 Networking API Generations

| Generation | Java Version | Model | Key Classes |
|-----------|-------------|-------|-------------|
| Classic Blocking | Java 1.0 | Thread-per-connection | `Socket`, `ServerSocket` |
| NIO Non-blocking | Java 4 | Selector/Reactor | `Selector`, `SocketChannel`, `SelectionKey` |
| NIO2 Async | Java 7 | Completion handler callbacks | `AsynchronousSocketChannel`, `CompletionHandler` |
| HTTP Client | Java 11 | Fluent API, sync/async | `HttpClient`, `HttpRequest`, `HttpResponse` |
| Virtual Threads | Java 21 | Thread-per-connection (again) | `Thread.ofVirtual()` + classic blocking |

### 4.2 `SelectionKey` Operations

| Key | Meaning | Register when |
|-----|---------|---------------|
| `OP_ACCEPT` | Server channel ready to accept connection | `ServerSocketChannel` |
| `OP_CONNECT` | Client channel finished connecting | After `connect()` |
| `OP_READ` | Channel has data to read | After accept/connect |
| `OP_WRITE` | Channel buffer has space (usually always) | Only when write buffer was full |

### 4.3 HTTP Version Comparison

| Feature | HTTP/1.1 | HTTP/2 | HTTP/3 (QUIC) |
|---------|----------|--------|---------------|
| Transport | TCP | TCP | UDP (QUIC) |
| Multiplexing | No (pipelining only, head-of-line blocking) | Yes (streams over 1 connection) | Yes (independent streams, no HOL blocking) |
| Header compression | None | HPACK | QPACK |
| Server push | No | Yes | Yes |
| Java support | HttpClient Java 11 | HttpClient Java 11 | JEP pending |

---

## 5. Architecture Diagrams

### Classic Blocking: One Thread Per Connection
```
Client 1 ─── TCP ─── Thread 1 (blocks on read, holds thread while waiting)
Client 2 ─── TCP ─── Thread 2 (blocks on read)
Client 3 ─── TCP ─── Thread 3 (blocks on read)
  ...
Client N ─── TCP ─── Thread N

Problem: N=10,000 → 10,000 threads × ~1MB stack = ~10GB memory
         + context-switch overhead between 10,000 OS threads
Solution: Virtual threads (Java 21) make this affordable again
```

### NIO Reactor Pattern
```
ServerSocketChannel (registered OP_ACCEPT)
        |
        v
   Selector.select()  ←─────────────────────────────────┐
        |                                                 |
        | returns ready keys                              |
        v                                                 |
  for (SelectionKey key : selector.selectedKeys()) {     |
      if (key.isAcceptable()) accept(key) ──> register OP_READ
      if (key.isReadable())   read(key)   ──> process request
      if (key.isWritable())   write(key)  ──> send response
  }                                                       |
        └─────────────────────────────────────────────────┘

ONE thread handles thousands of connections.
Trade-off: code is more complex; blocking inside event loop is catastrophic.
```

### HttpClient HTTP/2 Multiplexing
```
Single TCP connection to api.example.com:443

Stream 1 ──> GET /users/1        <── 200 OK {"id":1}
Stream 3 ──> GET /orders/100     <── 200 OK [{...}]
Stream 5 ──> POST /events        <── 201 Created
Stream 7 ──> GET /users/2        <── 200 OK {"id":2}

All 4 requests in flight simultaneously over ONE connection.
HTTP/2 frames include a stream ID (1, 3, 5, 7...) to demultiplex.
HttpClient manages connection pool and multiplexing automatically.
```

---

## 6. How It Works — Detailed Mechanics

### HttpClient — Synchronous and Asynchronous

```java
// Create a reusable HttpClient (thread-safe, connection-pooling)
HttpClient client = HttpClient.newBuilder()
    .version(HttpClient.Version.HTTP_2)     // prefer HTTP/2
    .followRedirects(HttpClient.Redirect.NORMAL)
    .connectTimeout(Duration.ofSeconds(5))
    .executor(Executors.newVirtualThreadPerTaskExecutor()) // use virtual threads
    .build();

// Build a request
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.example.com/users/1"))
    .header("Accept", "application/json")
    .header("Authorization", "Bearer " + token)
    .timeout(Duration.ofSeconds(10))
    .GET()  // also: .POST(BodyPublishers.ofString(json)), .PUT(), .DELETE()
    .build();

// Synchronous send (blocks current thread)
HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
int statusCode = response.statusCode();
String body = response.body();

// Asynchronous send (returns CompletableFuture immediately)
CompletableFuture<String> bodyFuture = client
    .sendAsync(request, BodyHandlers.ofString())
    .thenApply(resp -> {
        if (resp.statusCode() != 200)
            throw new RuntimeException("HTTP " + resp.statusCode());
        return resp.body();
    })
    .exceptionally(ex -> "{}");  // fallback on error

// Fire multiple requests in parallel
List<URI> uris = List.of(uri1, uri2, uri3);
List<CompletableFuture<String>> futures = uris.stream()
    .map(uri -> client.sendAsync(
            HttpRequest.newBuilder(uri).GET().build(),
            BodyHandlers.ofString())
        .thenApply(HttpResponse::body))
    .collect(Collectors.toList());

List<String> results = futures.stream()
    .map(CompletableFuture::join)  // join = get without checked exception
    .collect(Collectors.toList());
```

### BodyHandlers and BodyPublishers

```java
// BodyHandlers: how to process the response body
BodyHandlers.ofString()                        // String (UTF-8)
BodyHandlers.ofString(StandardCharsets.ISO_8859_1) // String (custom charset)
BodyHandlers.ofBytes()                         // byte[]
BodyHandlers.ofFile(Path.of("output.json"))   // stream directly to file
BodyHandlers.ofInputStream()                   // lazy InputStream
BodyHandlers.ofLines()                         // Stream<String> of lines
BodyHandlers.discarding()                      // discard body, get status only

// BodyPublishers: how to send request body
BodyPublishers.noBody()                        // no request body
BodyPublishers.ofString(jsonString)           // String body
BodyPublishers.ofByteArray(bytes)             // byte[] body
BodyPublishers.ofFile(Path.of("upload.csv")) // stream from file
BodyPublishers.ofInputStream(() -> stream)    // lazy InputStream
```

### Classic Blocking ServerSocket (and its limits)

```java
// Simple echo server — one thread per connection
ServerSocket serverSocket = new ServerSocket(8080);
ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();  // Java 21

while (true) {
    Socket clientSocket = serverSocket.accept();  // blocks until connection
    pool.submit(() -> handleClient(clientSocket));  // new virtual thread per connection
}

void handleClient(Socket socket) {
    try (socket;  // try-with-resources closes socket
         var in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
         var out = new PrintWriter(socket.getOutputStream(), true)) {
        String line;
        while ((line = in.readLine()) != null) {
            out.println("Echo: " + line);
        }
    } catch (IOException e) {
        System.err.println("Client error: " + e.getMessage());
    }
}
// With virtual threads: this scales to millions of connections
// Without virtual threads: each connection costs ~1MB stack → limited
```

### NIO Non-blocking Server

```java
// Single-threaded NIO server — Reactor pattern
ServerSocketChannel serverChannel = ServerSocketChannel.open();
serverChannel.configureBlocking(false);  // KEY: non-blocking
serverChannel.bind(new InetSocketAddress(8080));

Selector selector = Selector.open();
serverChannel.register(selector, SelectionKey.OP_ACCEPT);

ByteBuffer buffer = ByteBuffer.allocate(1024);

while (true) {
    selector.select();  // blocks until at least one channel is ready

    Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
    while (keys.hasNext()) {
        SelectionKey key = keys.next();
        keys.remove();  // MUST remove to avoid re-processing

        if (key.isAcceptable()) {
            // Accept new connection
            SocketChannel clientChannel = serverChannel.accept();
            clientChannel.configureBlocking(false);
            clientChannel.register(selector, SelectionKey.OP_READ);

        } else if (key.isReadable()) {
            // Read from existing connection
            SocketChannel client = (SocketChannel) key.channel();
            buffer.clear();
            int bytesRead = client.read(buffer);

            if (bytesRead == -1) {
                client.close();  // client disconnected
            } else {
                buffer.flip();
                // Echo back
                client.write(buffer);  // may not write all bytes if buffer full
                // Production: register OP_WRITE, write remaining in next cycle
            }
        }
    }
}
// Limitation: any blocking operation inside the event loop (DB call, sleep)
// blocks ALL connections. Never block in a Selector event loop.
```

### NIO2 — Asynchronous Channels with CompletionHandler

```java
// NIO2: true async I/O — OS notifies completion, no thread blocks
AsynchronousServerSocketChannel server = AsynchronousServerSocketChannel.open();
server.bind(new InetSocketAddress(8080));

// Accept is async: callback invoked when connection arrives
server.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
    @Override
    public void completed(AsynchronousSocketChannel client, Void attachment) {
        server.accept(null, this);  // immediately accept next connection

        ByteBuffer buf = ByteBuffer.allocate(1024);
        client.read(buf, buf, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer bytesRead, ByteBuffer buf) {
                buf.flip();
                // process data; write response via client.write(...)
            }
            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                System.err.println("Read failed: " + exc.getMessage());
            }
        });
    }
    @Override
    public void failed(Throwable exc, Void attachment) {
        System.err.println("Accept failed: " + exc.getMessage());
    }
});
// Callbacks make the code deeply nested ("callback hell")
// CompletableFuture API alternative exists but still complex
// Virtual threads (Java 21) provide simpler model for new code
```

---

## 7. Real-World Examples

- **Netty**: Uses NIO `Selector`-based event loops internally (one thread per CPU core, N channels per thread). Powers gRPC, Apache Cassandra's network layer, and Kafka's network communication.
- **Spring WebFlux**: Built on Project Reactor + Netty; uses NIO under the hood; non-blocking HTTP server for reactive applications.
- **Java's `HttpClient` in microservices**: Standard choice for service-to-service HTTP calls in Java 11+; HTTP/2 multiplexing reduces connection overhead in high-throughput scenarios.
- **Virtual threads replacing NIO for application code** (Java 21): Tomcat, Jetty, and Helidon support virtual-thread-per-request mode, making NIO complexity unnecessary for most CRUD services.

---

## 8. Tradeoffs

| Approach | Concurrency | Complexity | When to Use |
|----------|-------------|-----------|-------------|
| Blocking + virtual threads | Millions | Low | Java 21+, I/O-bound, simple code preferred |
| NIO Reactor | Millions | High | Java 8-20, frameworks (Netty), ultra-low latency |
| NIO2 Async | Millions | Very High | File I/O with kernel async (rare) |
| Blocking + platform threads | Thousands | Low | Simple, low-concurrency services |
| `HttpClient.sendAsync()` | Millions | Medium | Outbound HTTP, parallel fan-out |

---

## 9. When to Use / When NOT to Use

**Use `HttpClient` (Java 11+)** for all outbound HTTP/1.1 and HTTP/2 from Java services. It's the standard library — no external dependency needed. Use `sendAsync()` for parallel requests; `send()` for simple sequential calls.

**Use virtual threads** (Java 21) for inbound server code needing high concurrency. Eliminates need to write NIO-based servers for most use cases.

**Use NIO Selector** when: building a networking framework, need maximum control over threading, or working on Java 8-20. Do NOT use for application-level code if virtual threads are available.

**Do NOT block** inside a `Selector` event loop — one blocked call blocks all registered channels. If you need to call a database or sleep, hand off to a separate thread pool.

---

## 10. Common Pitfalls

### War Story 1: Not removing selected keys from the Selector
A developer implemented a NIO server but forgot `keys.remove()` after processing each `SelectionKey`. The same key was processed every loop iteration — the server appeared to hang, processing phantom events. **Fix**: Always call `keys.remove()` (on the iterator, not `selector.selectedKeys().remove()`) after processing each key.

### War Story 2: `HttpClient` instance created per request
A team created `new HttpClient.newHttpClient()` for every outbound request. `HttpClient` maintains a connection pool and should be shared across requests. Each new instance started its own connection pool, connection to the same host required a new TCP handshake every time, and the old instances' connection pools were never closed. Under load, FD (file descriptor) exhaustion occurred. **Fix**: Create one `HttpClient` per target service (or one application-wide) and reuse it.

### War Story 3: Blocking inside a NIO event loop
A developer added a database call inside the `isReadable()` handler of a Selector event loop to validate an incoming request. One slow DB query blocked the thread for 500ms — during which NO other connections could be served. For 100ms per query average, the server maxed out at 2 requests/second per selector thread. **Fix**: Hand off work to a separate thread pool immediately; only do minimal parsing in the event loop.

### War Story 4: HTTP/2 multiplexing misconfigured
A service made 20 parallel `HttpClient.sendAsync()` calls but response times didn't improve over sequential calls. Investigation revealed `HttpClient` was negotiating HTTP/1.1 (server didn't support HTTP/2 or TLS was not configured). HTTP/1.1 doesn't multiplex — each request needed its own connection, and the connection pool limit was 10. **Fix**: Ensure server supports HTTP/2; enable TLS (HTTP/2 typically requires HTTPS); set `HttpClient.Version.HTTP_2`.

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| `java.net.http.HttpClient` (Java 11+) | High-level HTTP/1.1 and HTTP/2 client |
| `java.nio.channels.*` | NIO channels, Selector |
| `java.nio.channels.AsynchronousServerSocketChannel` | NIO2 async server |
| Netty | High-performance NIO framework |
| OkHttp | Popular third-party HTTP client with interceptors |
| Wireshark | Capture and inspect network traffic |
| `curl -v` / `httpie` | Test HTTP endpoints from command line |

---

## 12. Interview Questions with Answers

**Q1: How does `HttpClient.sendAsync()` work internally?**
`HttpClient.sendAsync()` returns a `CompletableFuture<HttpResponse<T>>` immediately. Internally, the `HttpClient` uses an `Executor` (configurable; defaults to `ForkJoinPool.commonPool()` if not set) for completion callbacks. The actual I/O is handled via the JDK's internal async HTTP implementation which uses NIO (selectors) for HTTP/1.1 and a separate multiplexed connection pool for HTTP/2. When the response arrives, the completion callbacks (`.thenApply()` etc.) run on the executor. Best practice: provide a custom executor (`Executors.newVirtualThreadPerTaskExecutor()`) to control thread usage.

**Q2: What is the difference between NIO `Selector` and NIO2 asynchronous channels?**
NIO `Selector` (Java 4): non-blocking polling model. A thread calls `selector.select()`, which returns when any registered channel has pending I/O. The thread then handles the I/O itself. Still synchronous from the application's perspective — the event-loop thread does the work. NIO2 async channels (Java 7): true async model — the application registers a `CompletionHandler` callback. The OS notifies the JVM when I/O completes, and the JVM invokes the callback on a system-managed thread pool. NIO2 is conceptually simpler for some patterns but the callback model leads to "callback hell." Most production code uses NIO (via Netty) rather than NIO2 directly.

**Q3: What is the Reactor pattern and how does Java NIO map to it?**
The Reactor pattern: one or more `Reactor` threads run event loops, demultiplexing I/O events and dispatching them to registered handlers. Java NIO maps directly: `Selector` is the Reactor, `SelectionKey` is the event, handler code in the `isReadable()`/`isWritable()` branches is the handler, and `SocketChannel` is the channel abstraction. Netty extends this with `NioEventLoop` (Reactor thread) and `ChannelHandler` pipeline for handler composition. The key invariant: handlers must never block — they must complete quickly and return control to the event loop.

**Q4: What are the limitations of one-thread-per-connection blocking I/O?**
(1) Memory: each platform thread requires ~512KB-1MB stack. 10,000 connections = 5-10GB stack memory alone. (2) Context switches: OS scheduling 10,000 threads causes significant overhead (~microseconds per switch × millions of switches per second). (3) Scheduler thrashing: OS spends more time deciding which thread to run than actually running them. (4) File descriptor limits: OS limits open FDs (default ~1024 on Linux, configurable to ~65536). With Java 21 virtual threads: virtual thread stacks start at ~few KB, scheduler is JVM (not OS), carrier threads = CPU count. Virtual threads restore the simplicity of blocking code at NIO-level concurrency.

**Q5: How would you implement a basic NIO server?**
(1) Open `ServerSocketChannel`, configure non-blocking, bind to port. (2) Open `Selector`. (3) Register `ServerSocketChannel` with selector for `OP_ACCEPT`. (4) Event loop: call `selector.select()`, iterate `selectedKeys()`. (5) On `OP_ACCEPT`: call `serverChannel.accept()`, configure new channel non-blocking, register for `OP_READ`. (6) On `OP_READ`: read into `ByteBuffer`, flip, process data, write response, register for `OP_WRITE` if write buffer full. (7) On `OP_WRITE`: write remaining data, deregister `OP_WRITE` when done. (8) Always remove keys from `selectedKeys()` iterator. See Section 6 for complete implementation.

**Q6: What is HTTP/2 multiplexing and how does `HttpClient` expose it?**
HTTP/2 multiplexing sends multiple request/response pairs concurrently over a single TCP connection via numbered "streams." HTTP/1.1 requires a separate TCP connection per concurrent request (or uses pipelining with head-of-line blocking). HTTP/2 frames carry a stream ID: frames for stream 1 (GET /users), stream 3 (GET /orders), stream 5 (POST /events) interleave on one TCP connection. `HttpClient` with `HTTP_2` version and TLS negotiates HTTP/2 automatically; the client maintains a connection pool. Multiple `sendAsync()` calls to the same host reuse the same connection with multiplexed streams. No application code change is required — `HttpClient` abstracts the streams.

**Q7: How does `HttpClient` handle TLS/SSL?**
`HttpClient` uses the JVM's default `SSLContext` (configured via `javax.net.ssl.*` system properties) unless you provide a custom one via `HttpClient.newBuilder().sslContext(customSSLContext)`. For testing, you can create a `SSLContext` that trusts all certificates — never in production. Certificate pinning requires a custom `TrustManager`. HTTP/2 over HTTPS uses TLS ALPN (Application Layer Protocol Negotiation) extension — the client and server negotiate "h2" during TLS handshake. HTTP/2 over plain text (h2c) is technically possible but rarely used.

**Q8: How does virtual thread I/O differ from NIO at the implementation level?**
With virtual threads (Java 21), blocking I/O calls like `socket.read()` are reimplemented internally using NIO selectors. When a virtual thread calls `read()` on a socket, the JVM registers the channel with an internal selector and parks (unmounts) the virtual thread. When data arrives, the selector wakes up, the virtual thread is rescheduled, and resumes at the `read()` call as if it returned normally. The application writes simple blocking code; the JVM uses NIO internally. This is the "loom" project vision: blocking API, NIO scalability, no callback complexity.

**Q9: What are `BodyHandlers.ofInputStream()` and when do you use it?**
`BodyHandlers.ofInputStream()` returns a `HttpResponse<InputStream>` where the body is lazily consumed. Unlike `ofString()` (buffers entire response in memory) or `ofBytes()` (same), `ofInputStream()` lets you process the response body as a stream without loading it all into memory. Use it for: large file downloads, streaming JSON (combined with a streaming JSON parser like Jackson's `StreamingParser`), large CSV processing. Remember to close the `InputStream` when done — `try-with-resources` on the response handles this if you call `response.body().close()` or use the `response` in a try block.

**Q10: What happens to in-flight `HttpClient` requests when the application shuts down?**
`HttpClient` implements `AutoCloseable` (Java 21). If not explicitly closed, the client's internal connection pool and executor survive until GC. For clean shutdown: call `client.close()` (Java 21) which waits for in-flight requests to complete then closes connections, or `client.shutdown()` (Java 21) which initiates graceful shutdown and returns a `CompletableFuture<Void>` completing when done. In Java 11-20, there is no `close()` method — shutdown is handled by the provided executor's shutdown. Best practice: use a custom executor so you control its lifecycle independently of the `HttpClient`.

---

## 13. Best Practices

1. **Create `HttpClient` once and reuse** — it manages connection pools; one per service or one application-wide.
2. **Set timeouts** on both `HttpClient.newBuilder().connectTimeout()` and `HttpRequest.newBuilder().timeout()` — no timeout = threads can hang indefinitely.
3. **Use `sendAsync()` for parallel fan-out** — collect multiple `CompletableFuture`s, join at the end.
4. **Always remove processed `SelectionKey`s** from the iterator in NIO event loops.
5. **Never block inside a NIO Selector event loop** — hand off to a separate executor.
6. **Use virtual threads for new server code** (Java 21) — simpler than NIO for application-level code.
7. **Use HTTP/2** for high-volume service-to-service calls — multiplexing reduces connection overhead.
8. **Close `InputStream`s** from `BodyHandlers.ofInputStream()` — unclosed streams hold connection resources.
9. **Monitor connection pool usage** in production — connection pool exhaustion manifests as `HttpTimeoutException`.
10. **Provide a custom executor to `HttpClient`** — controls thread priority and lifecycle for the async callback threads.

---

## 14. Case Study

### Building a Parallel API Aggregator

**Problem**: A service must call 3 downstream APIs in parallel, aggregate results, and respond within 2 seconds total.

```java
public class UserProfileAggregator {
    private final HttpClient client;

    UserProfileAggregator() {
        this.client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(2))
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();
    }

    record UserProfile(User user, List<Order> orders, Preferences prefs) {}

    UserProfile fetchProfile(String userId) {
        HttpRequest userReq  = buildRequest("/users/" + userId);
        HttpRequest orderReq = buildRequest("/orders?userId=" + userId);
        HttpRequest prefReq  = buildRequest("/preferences/" + userId);

        // Fire all three in parallel
        CompletableFuture<User>          userFuture  = fetch(userReq,  User.class);
        CompletableFuture<List<Order>>   orderFuture = fetchList(orderReq, Order.class);
        CompletableFuture<Preferences>   prefFuture  = fetch(prefReq,  Preferences.class);

        // Wait for all; fail fast if any fails
        CompletableFuture.allOf(userFuture, orderFuture, prefFuture)
            .orTimeout(2, TimeUnit.SECONDS)  // overall timeout
            .join();

        return new UserProfile(userFuture.join(), orderFuture.join(), prefFuture.join());
    }

    private <T> CompletableFuture<T> fetch(HttpRequest request, Class<T> type) {
        return client.sendAsync(request, BodyHandlers.ofString())
            .thenApply(resp -> {
                if (resp.statusCode() != 200)
                    throw new RuntimeException("HTTP " + resp.statusCode() + " for " + request.uri());
                return deserialize(resp.body(), type);
            });
    }

    private HttpRequest buildRequest(String path) {
        return HttpRequest.newBuilder()
            .uri(URI.create("https://internal-api.company.com" + path))
            .header("Authorization", "Bearer " + getToken())
            .timeout(Duration.ofMillis(1500))
            .GET()
            .build();
    }

    @SuppressWarnings("unchecked")
    private <T> T deserialize(String json, Class<T> type) {
        // JSON deserialization via Jackson or Gson
        return objectMapper.readValue(json, type);
    }
}
// With virtual threads executor: all 3 HTTP calls run in parallel virtual threads
// .orTimeout(2, SECONDS): CompletableFuture fails if not all complete within 2s
// .join() on CompletableFuture.allOf: blocks the calling virtual thread (free — no carrier thread wasted)
```

**Performance**: 3 sequential calls × 400ms avg = 1200ms. 3 parallel calls = ~400ms (limited by slowest). Well within 2s SLA.
