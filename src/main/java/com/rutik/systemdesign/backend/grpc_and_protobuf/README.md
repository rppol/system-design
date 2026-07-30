# gRPC & Protocol Buffers

<!-- study-paths
senior: README.md
files this module contributes to each curated path; omit a tier to leave it out
-->
## 1. Concept Overview

gRPC is a high-performance, open-source remote procedure call (RPC) framework developed by Google. It uses Protocol Buffers (protobuf) as the interface definition language and serialization format, and HTTP/2 as the transport. gRPC generates type-safe client and server stubs from .proto files in 10+ languages, making it ideal for polyglot microservice communication.

Protocol Buffers serialize structured data into a compact binary format that is normally smaller and cheaper to parse than the equivalent JSON, though the actual ratio depends entirely on message shape (the widely-quoted "3–10x smaller, 20–100x faster" figure is Google's protobuf-vs-**XML** claim, not a protobuf-vs-JSON benchmark — see the field-by-field comparison in Section 5). gRPC provides four communication modes (unary, server-streaming, client-streaming, bidirectional), built-in deadline propagation, interceptors (middleware), and a health-checking protocol. It is widely used for internal service-to-service communication, including at Google and Netflix.

---

## 2. Intuition

> **One-line analogy**: gRPC is a strongly-typed telephone system — you define the conversation protocols upfront in a contract (.proto), everyone speaks the same language (protobuf), and the connection is both efficient and reliable (HTTP/2 multiplexing). REST is like sending letters — flexible, but verbose, slow, and you can send anything without checking if it makes sense.

**Mental model**: Define services and messages in .proto files. Run protoc to generate stubs. The generated code handles serialization, HTTP/2 framing, and connection management. Your client calls generated method stubs; the framework handles everything else. The server implements generated service interfaces.

**Why it matters**: For internal microservice APIs, gRPC provides strong typing (catch schema mismatches at compile time), efficient serialization (reduces bandwidth and CPU), streaming (server push, bidirectional), and built-in deadline/cancellation propagation (critical for distributed systems). The main tradeoff is human-readability — you cannot debug with curl.

**Key insight**: The biggest operational advantage of gRPC is not performance but schema enforcement. Protobuf's field numbering rules and compatibility guarantees make breaking changes harder to accidentally introduce than with JSON-based REST.

---

## 3. Core Principles

- **Contract-first**: .proto files are the API contract, not documentation.
- **Binary serialization**: Protobuf encodes fields by number, not name — more efficient and forward/backward compatible.
- **HTTP/2 transport**: Streams, multiplexing, header compression, flow control all from HTTP/2.
- **Deadline propagation**: Deadlines cascade across service calls; if the client cancels, downstream services should stop work.
- **Interceptors**: Cross-cutting concerns (logging, auth, tracing, retry) are added via interceptors, not business logic.
- **Health checking**: Standardized health checking protocol (grpc.health.v1.Health) for load balancers and orchestrators.

---

## 4. Types / Architectures / Strategies

### 4.1 Four RPC Modes

| Mode | Client | Server | Use Case |
|------|--------|--------|---------|
| Unary | Single request | Single response | Standard request-response |
| Server streaming | Single request | Stream of responses | Real-time data, file download |
| Client streaming | Stream of requests | Single response | File upload, bulk data |
| Bidirectional streaming | Stream of requests | Stream of responses | Chat, real-time collaboration |

### 4.2 Protobuf Field Types

| Proto type | Wire type | Notes |
|------------|-----------|-------|
| int32/int64 | Varint | Negative numbers inefficient (use sint32/sint64) |
| sint32/sint64 | Varint (zigzag) | Efficient for negative numbers |
| bool | Varint | |
| fixed32/sfixed32 | 32-bit | `fixed32` beats `uint32` when values are often > 2^28 |
| fixed64/sfixed64 | 64-bit | |
| float | 32-bit | |
| double | 64-bit | |
| string | Length-delimited | UTF-8 |
| bytes | Length-delimited | Arbitrary binary |
| message | Length-delimited | Nested message |
| repeated | Per-element, or length-delimited if packed | In proto3, repeated scalar *numeric* fields are packed by default (one LEN record for the whole array); strings, bytes and messages cannot be packed |
| map | Key-value pairs | Key must be integral or string (no float, bytes, enum or message); value can be anything except another map |

---

## 5. Architecture Diagrams

### gRPC Architecture

```mermaid
sequenceDiagram
    participant C as Client Application
    participant S as gRPC Client Stub
    participant H as HTTP/2 Connection
    participant G as gRPC Server
    participant I as Service Implementation

    C->>S: userService.getUser(id=123)
    Note over S: serialize to protobuf bytes<br/>set method=POST, path=/users.UserService/GetUser
    S->>H: send over multiplexed TLS stream
    H->>G: forward frame (Netty / OkHttp)
    Note over G: deserialize protobuf<br/>apply interceptors (auth, logging, tracing)
    G->>I: dispatch getUser(request, StreamObserver)
    I-->>G: build response
    Note over G: serialize response to protobuf
    G-->>H: DATA frame + trailing HEADERS<br/>grpc-status=0 (OK)
    H-->>C: deserialize, return to application
```

A single unary call traced end-to-end: the stub serializes the request onto one multiplexed HTTP/2 stream, the server deserializes and runs interceptors before dispatch, and the response returns as a DATA frame followed by trailing HEADERS carrying `grpc-status: 0`.

### Four RPC Modes by Streaming Direction

```mermaid
quadrantChart
    title Four gRPC Modes by Streaming Direction
    x-axis Single Client Request --> Streaming Client Requests
    y-axis Single Server Response --> Streaming Server Responses
    quadrant-1 Bidirectional Streaming
    quadrant-2 Server Streaming
    quadrant-3 Unary
    quadrant-4 Client Streaming
    Unary: [0.25, 0.25]
    Server streaming: [0.25, 0.75]
    Client streaming: [0.75, 0.25]
    Bidirectional streaming: [0.75, 0.75]
```

The four modes from the Section 4.1 table are not four unrelated cases — they are every combination of two independent yes/no choices, does the client stream and does the server stream, and only bidirectional streaming sends and receives concurrently on the same call.

### Protobuf Wire Format

```
Message: Person { name: "Alice", age: 30 }

Proto definition:
  message Person {
    string name = 1;
    int32  age  = 2;
  }

Wire encoding (bytes):
  Field 1, wire type 2 (length-delimited):
    Tag: (1 << 3) | 2 = 0x0A
    Length: 5 (length of "Alice")
    Data: 41 6C 69 63 65 ("Alice" in UTF-8)

  Field 2, wire type 0 (varint):
    Tag: (2 << 3) | 0 = 0x10
    Value: 30 = 0x1E

Full encoding: 0A 05 41 6C 69 63 65 10 1E (9 bytes)
JSON equivalent: {"name":"Alice","age":30} (25 bytes)

Note: Field names are NOT in the wire format.
      The receiver uses field numbers (1, 2) to identify fields.
      This is why field numbers must never be reused.
```

**The idea behind it.** "Every field is announced by a packed tag varint that carries two facts at once — which field this is, and how to read what comes next — and then the value follows with no name attached." The tag is a varint, so it is one byte only while `(field_number << 3) | wire_type` stays under 128: field numbers **1–15 cost one tag byte, 16–2047 cost two**. That is why the protobuf guide tells you to spend numbers 1–15 on your hottest, most frequently-set fields.

The tag is where the size win comes from. JSON spends the field name on every message; protobuf spends 3 bits on a type code and the rest on a number, then never mentions the name again.

| Symbol | What it is |
|--------|------------|
| field number | The `= 1` / `= 2` in the `.proto`. The permanent identity of a field. Legal range 1–536,870,911, minus 19,000–19,999 which protobuf reserves for itself |
| wire type | 3-bit code for how to parse the value: `0`=varint, `1`=64-bit, `2`=length-delimited, `5`=32-bit (`3`/`4` are the deprecated group markers) |
| `<< 3` | Shift the field number left 3 bits to make room for the wire type |
| `\|` | Bitwise OR — drops the wire type into the 3 low bits just vacated |
| tag | The two packed together as a varint, the first thing on the wire for every field — one byte for field numbers 1–15, two for 16–2047 |

**Walk one example.** Build both tag bytes from scratch, in binary:

```
  field 1, wire type 2 (length-delimited, for the string):
    1 << 3        = 8         binary 0000 1000
    8 | 2         = 10        binary 0000 1010   -> 0x0A
    read back: 10 >> 3 = 1 (field), 10 & 7 = 2 (wire type)   correct

  field 2, wire type 0 (varint, for the int32):
    2 << 3        = 16        binary 0001 0000
    16 | 0        = 16        binary 0001 0000   -> 0x10
    read back: 16 >> 3 = 2 (field), 16 & 7 = 0 (wire type)   correct

  full message:
    0A  05  41 6C 69 63 65   10  1E
    ^   ^   ^--- "Alice" ---  ^   ^-- 30
    |   +-- length 5          +-- tag: field 2, varint
    +-- tag: field 1, length-delimited
    = 9 bytes total
```

**Why 3 bits, and what breaks without them.** The parser must know how many bytes to consume *before* it knows what the field means — otherwise an unknown field would be unparseable and the whole message unreadable. The wire type tells it "read a varint" or "read a length then that many bytes," so a receiver can skip fields it has never heard of and keep going. That single property is what makes protobuf forward-compatible: old code reading new messages just steps over the additions. Take the wire type away and every schema change becomes a breaking change.

**Where the byte savings actually come from.** Compare the two encodings field by field:

```
  field       protobuf bytes            JSON bytes
  ---------   -----------------------   -------------------------------
  name        1 tag + 1 len + 5 data    "name":"Alice"  = 14
                              = 7
  age         1 tag + 1 varint = 2      "age":30        = 8
  structure   0 (none needed)           { } and one ,   = 3
  ---------   -----------------------   -------------------------------
  total       9 bytes                   25 bytes

  protobuf is 9/25 = 36% the size  ->  64% smaller
```

Note where the win is concentrated: `age` costs 2 bytes instead of 8, a 4x reduction, because a small integer is one varint byte rather than two ASCII digits plus the 5 bytes of `"age"` and its colon. Strings barely improve — `"Alice"` still costs 5 bytes of UTF-8 either way. This is the rule of thumb worth carrying into an interview: **protobuf's advantage scales with how numeric and how deeply-nested your messages are**, and nearly vanishes for messages that are mostly long free-text strings.

### Protobuf Schema Evolution: Safe vs. Breaking Changes

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    Change(["proto schema change"]) --> Decision{"safe or breaking?"}
    Decision -->|"safe"| S1["add field<br/>new number"]
    Decision -->|"safe"| S2["add enum value"]
    Decision -->|"safe"| S3["add new RPC"]
    Decision -->|"safe"| S4["rename field<br/>number unchanged"]
    Decision -->|"breaking"| B1["remove field<br/>without reserving number"]
    Decision -->|"breaking"| B2["change to an<br/>incompatible field type"]
    Decision -->|"breaking"| B3["reuse a field number"]

    class Change io
    class Decision mathOp
    class S1,S2,S3,S4 train
    class B1,B2,B3 lossN
```

Additive changes stay wire-compatible because the field number never moves; removing a field without reserving it, changing a field's type, or reusing a number breaks old clients silently — exactly what `buf breaking --against` catches in CI.

### Deadline Propagation

```mermaid
sequenceDiagram
    participant Client
    participant A as Service A
    participant B as Service B
    participant DB as Database

    Client->>A: call, deadline 1000ms
    Note over A: 900ms remaining
    A->>B: call, deadline propagated
    Note over B: 800ms remaining
    B->>DB: query, deadline propagated
    Note over DB: 600ms remaining
    DB-->>B: query takes 700ms, exceeds remaining deadline
    B-->>A: DEADLINE_EXCEEDED
    Note over B: detects timeout, cancels own work
    A-->>Client: DEADLINE_EXCEEDED
    Note over A: cancels own work, propagates up
    Note over Client: does not wait for the full 1000ms
```

The 700ms database query blows the 600ms budget it was handed, so DEADLINE_EXCEEDED unwinds Service B, then Service A, then the client immediately — without propagation, only the client would time out while Service A, Service B, and the database keep working for nothing.

---

## 6. How It Works — Detailed Mechanics

### 6.1 .proto Service Definition

```protobuf
syntax = "proto3";
package users;
option java_package = "com.example.users.proto";
option java_multiple_files = true;

import "google/protobuf/timestamp.proto";
import "google/protobuf/empty.proto";

message User {
  int64 id = 1;
  string email = 2;
  string name = 3;
  google.protobuf.Timestamp created_at = 4;
  repeated string roles = 5;
  UserStatus status = 6;
}

enum UserStatus {
  USER_STATUS_UNSPECIFIED = 0;  // always include unspecified as 0
  USER_STATUS_ACTIVE = 1;
  USER_STATUS_INACTIVE = 2;
}

message GetUserRequest {
  int64 id = 1;
}

message ListUsersRequest {
  int32 page_size = 1;   // max items to return
  string page_token = 2; // cursor from previous response
  string filter = 3;     // optional filter expression
}

message ListUsersResponse {
  repeated User users = 1;
  string next_page_token = 2;
}

service UserService {
  // Unary RPC
  rpc GetUser (GetUserRequest) returns (User);

  // Server streaming: client requests, server streams users
  rpc ListUsers (ListUsersRequest) returns (stream User);

  // Client streaming: client streams user IDs for batch lookup
  rpc GetUsersBatch (stream GetUserRequest) returns (ListUsersResponse);

  // Bidirectional streaming
  rpc WatchUsers (stream GetUserRequest) returns (stream User);
}
```

### 6.2 Java Server Implementation (Spring Boot + gRPC)

```java
@GrpcService
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {

    private final UserRepository userRepository;

    @Override
    public void getUser(GetUserRequest request,
                        StreamObserver<User> responseObserver) {
        try {
            com.example.domain.User user = userRepository
                .findById(request.getId())
                .orElseThrow(() -> new StatusRuntimeException(
                    Status.NOT_FOUND.withDescription(
                        "User " + request.getId() + " not found")));

            responseObserver.onNext(userMapper.toProto(user));
            responseObserver.onCompleted();
        } catch (StatusRuntimeException e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void listUsers(ListUsersRequest request,
                          StreamObserver<User> responseObserver) {
        // Server streaming: send users one by one
        userRepository.findAll(request.getFilter()).forEach(user -> {
            // Check if client cancelled
            if (!Context.current().isCancelled()) {
                responseObserver.onNext(userMapper.toProto(user));
            }
        });
        responseObserver.onCompleted();
    }
}
```

### 6.3 Interceptors

```java
// Server-side authentication interceptor
public class AuthInterceptor implements ServerInterceptor {

    @Override
    public <Req, Resp> ServerCall.Listener<Req> interceptCall(
            ServerCall<Req, Resp> call,
            Metadata headers,
            ServerCallHandler<Req, Resp> next) {

        String token = headers.get(
            Metadata.Key.of("authorization", ASCII_STRING_MARSHALLER));

        if (token == null || !tokenService.validate(token)) {
            call.close(Status.UNAUTHENTICATED
                .withDescription("Invalid token"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        // Add identity to context for business logic
        Context ctx = Context.current().withValue(
            USER_IDENTITY_KEY, tokenService.extract(token));
        return Contexts.interceptCall(ctx, call, headers, next);
    }
}

// Client-side retry interceptor
public class RetryInterceptor implements ClientInterceptor {

    // ClientInterceptor declares exactly one method, and it takes the next
    // Channel as its third argument — you must delegate to that, not to a
    // field of your own.
    @Override
    public <Req, Resp> ClientCall<Req, Resp> interceptCall(
            MethodDescriptor<Req, Resp> method,
            CallOptions callOptions,
            Channel next) {
        // Delegate downstream; retry policy itself lives in the service config
        return next.newCall(method, callOptions);
    }
}
```

### 6.4 Error Handling — gRPC Status Codes

| Status Code | Equivalent HTTP | Meaning |
|-------------|----------------|---------|
| OK | 200 | Success |
| CANCELLED | 499 | Client cancelled the call |
| UNKNOWN | 500 | Unknown error |
| INVALID_ARGUMENT | 400 | Client specified invalid argument |
| DEADLINE_EXCEEDED | 504 | Deadline expired |
| NOT_FOUND | 404 | Resource not found |
| ALREADY_EXISTS | 409 | Resource already exists |
| PERMISSION_DENIED | 403 | Insufficient permissions |
| UNAUTHENTICATED | 401 | Not authenticated |
| RESOURCE_EXHAUSTED | 429 | Quota exhausted / rate limited |
| FAILED_PRECONDITION | 400 | System not in required state |
| UNAVAILABLE | 503 | Service temporarily unavailable |
| INTERNAL | 500 | Internal server error |

---

## 7. Real-World Examples

**Google internal use**: gRPC is the open-source successor to Google's internal Stubby RPC system, and Google uses it across its cloud products and public APIs. The framework handles load balancing, health checking, retries, and deadline propagation automatically.

**Netflix gRPC adoption**: Netflix's Runtime Platform team adopted and extended gRPC for internal service-to-service calls, replacing hand-written REST clients with generated stubs. The CNCF case study on Netflix records the wins in exactly these terms: client creation time fell "from 2-3 weeks to minutes", hundreds of lines of hand-written cache-management code collapsed to two or three lines of proto config per client, and on latency the strongest claim made is qualitative — "we've seen an incredible reduction in P99s for gRPC-oriented services" and "a squishing and a narrowing of our latency windows consistently across the board." No CPU- or latency-reduction *percentage* appears there or in Netflix's own gRPC write-ups, so treat any circulating figure (50% CPU, 35% p99 and similar) as unsourced.

**Kubernetes API server**: kubectl, controllers and kubelets talk to the API server over its **HTTP REST API**, not gRPC — including `watch`, which is a long-lived `GET ...?watch=true` streamed with HTTP chunked transfer encoding (JSON or protobuf-encoded objects), with an optional WebSocket variant. gRPC is used elsewhere in the ecosystem: the API server to etcd, and the kubelet to the CRI, CSI and device-plugin sockets.

---

## 8. Tradeoffs

| Aspect | gRPC | REST+JSON |
|--------|------|-----------|
| Schema | Enforced (.proto) | Optional (OpenAPI) |
| Serialization | Binary (compact, fast) | Text (readable, slow) |
| Browser support | gRPC-Web needed | Native |
| Streaming | Built-in (4 modes) | SSE/WebSocket add-on |
| Debugging | Harder (binary) | Easy (curl, browser) |
| Code generation | Required | Optional |
| Ecosystem | Excellent for Go/Java/C++ | Universal |
| Firewall traversal | HTTP/2 port 443 | HTTP/1.1 port 80/443 |
| Metadata | gRPC metadata (headers) | HTTP headers |

---

## 9. When to Use / When NOT to Use

**Use gRPC when**: Internal microservice APIs, polyglot services needing type-safe contracts, streaming use cases (real-time, bidirectional), mobile clients where bandwidth is constrained, or when you need deadline propagation across service chains.

**Do not use gRPC when**: Public APIs consumed by browsers directly (requires gRPC-Web proxy), teams unfamiliar with protobuf toolchain, debuggability is critical and team lacks gRPC tooling, or you need CORS support for browser clients.

---

## 10. Common Pitfalls

**Reusing field numbers in proto evolution**: Removing a field and reusing its number for a new field breaks backward compatibility. Clients running old stubs will interpret the new field as the old field. Always reserve removed field numbers: `reserved 3, 5 to 7;` and `reserved "old_field_name";`

**Not setting deadlines on the client**: Without a deadline, a gRPC call can hang indefinitely if the server is slow or the network drops the FIN. Every production gRPC call must have a deadline. Use `stub.withDeadlineAfter(5, TimeUnit.SECONDS)` or configure a default deadline via the stub factory.

**Forgetting to check context cancellation in server streaming**: In a server-streaming RPC, if the client cancels, `Context.current().isCancelled()` becomes true. If your streaming loop does not check this, it continues computing and sending results to a cancelled context — wasting server resources. Check cancellation in the streaming loop.

**gRPC default max message size**: gRPC defaults to 4 MB max inbound message size. Services that stream large messages (bulk exports, media metadata) will receive RESOURCE_EXHAUSTED errors. Configure `maxInboundMessageSize` on the channel/server. Better: redesign to stream smaller messages.

**gRPC-Web vs gRPC**: Browsers cannot use HTTP/2 Trailers (gRPC uses HTTP/2 trailing HEADERS for the grpc-status). gRPC-Web is a proxy protocol (Envoy or grpc-web proxy) that translates browser HTTP requests to gRPC. It only supports unary and server-streaming (no client or bidirectional streaming). Plan the proxy layer if browser clients are needed.

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| `protoc` | Protocol Buffer compiler |
| `protoc-gen-grpc-java` | Java gRPC code generator plugin |
| `grpcurl` | Command-line gRPC client (like curl for gRPC) |
| `evans` | Interactive gRPC client with REPL |
| Kubernetes `grpc` probe | Native liveness/readiness probe that calls `grpc.health.v1.Health/Check` directly (GA in 1.27) — the default choice on Kubernetes |
| `grpc-health-probe` | Exec-based health check binary, for non-Kubernetes environments or when you need custom metadata/TLS on the probe |
| `buf` | Modern protobuf toolchain (linting, breaking change detection) |
| `grpc-gateway` | Transcodes gRPC to REST/JSON (Go) |
| Envoy Proxy | gRPC-Web transcoding, load balancing, retries |
| `spring-grpc-spring-boot-starter` | Official Spring gRPC starter. The long-standing community alternative is `net.devh:grpc-spring-boot-starter` |
| Kreya / Postman | GUI gRPC client — build requests from a `.proto` file or server reflection |

---

## 12. Interview Questions with Answers

**Q: What is gRPC and how does it differ from REST?**
**Short:** gRPC uses Protocol Buffers over HTTP/2 with generated typed stubs and streaming, unlike REST's JSON request-response model.

gRPC is an RPC framework using Protocol Buffers for serialization and HTTP/2 for transport. It differs from REST in: using a binary format (compact, fast) vs JSON (human-readable), having strict schema enforcement via .proto files vs optional OpenAPI, built-in streaming support (4 modes) vs REST's single request-response, and generated type-safe stubs vs manual HTTP client code. gRPC is preferred for internal service-to-service communication; REST for public/browser-facing APIs.

**Q: Explain Protocol Buffers wire format and field numbering.**
**Short:** Protobuf encodes each field as a tag combining field number and wire type, so field numbers must never be reused.

Protobuf serializes each field as a tag-value pair. The tag is `(field_number << 3) | wire_type`, encoding both the field number (1–536,870,911, minus the 19,000–19,999 range protobuf reserves for itself) and the wire type (0=varint, 1=64-bit, 2=length-delimited, 5=32-bit; 3 and 4 are the deprecated group markers). The tag is itself a varint, so field numbers 1–15 cost one tag byte and 16–2047 cost two — spend the low numbers on your hottest fields. Field names are not in the wire format — only numbers. This means field numbers must never be reused after a field is removed (doing so causes old clients to misinterpret new fields). Varint encoding uses variable-length encoding: values 0–127 fit in 1 byte.

**Q: What are the four gRPC RPC modes?**
**Short:** gRPC has four RPC modes: unary, server-streaming, client-streaming, and bidirectional streaming.

Unary: one request, one response (like REST). Server-streaming: one request, stream of responses (useful for real-time data, large result sets). Client-streaming: stream of requests, one response (useful for bulk uploads, aggregation). Bidirectional streaming: both sides stream independently (useful for real-time chat, collaborative editing, game state sync). All modes use HTTP/2 streams, just with different DATA frame patterns.

**Q: How does deadline propagation work in gRPC?**
**Short:** gRPC propagates a deadline as grpc-timeout metadata so any service in the chain can cancel work once it expires.

The client sets a deadline (absolute timestamp). gRPC encodes the remaining deadline as `grpc-timeout` in the request metadata. The downstream service receives the deadline and propagates it to its own outbound calls. If any service in the chain exceeds the deadline, it returns DEADLINE_EXCEEDED. The parent service, on receiving DEADLINE_EXCEEDED from its child, also cancels its work and propagates the error. This prevents resource waste in partial-failure scenarios.

**Q: What is a gRPC interceptor?**
**Short:** A gRPC interceptor is middleware wrapping RPC calls for concerns like auth, tracing, logging, and metrics.

An interceptor is middleware that runs before/after RPC handling. Server interceptors wrap service method invocations; client interceptors wrap outbound calls. Common uses: authentication (extract and validate JWT from metadata), distributed tracing (inject/extract trace context), logging (log request/response), metrics (record call latency and error rates), retry with backoff. Interceptors chain and each calls next.interceptCall() to pass control.

**Q: How do you handle errors in gRPC?**
**Short:** gRPC errors use Status codes returned as StatusRuntimeException, sent to the client as trailing grpc-status metadata.

gRPC uses Status codes (similar to HTTP but gRPC-specific). Return a StatusRuntimeException on the server with the appropriate code (NOT_FOUND, INVALID_ARGUMENT, UNAUTHENTICATED, etc.). The gRPC framework sends it as trailing metadata grpc-status and grpc-message. For structured error details, use the google.rpc.Status type with google.rpc.ErrorInfo, google.rpc.BadRequest etc. from the googleapis/googleapis error.proto definitions. On the client, catch StatusRuntimeException and inspect the Status.

**Q: What is the Health Checking Protocol in gRPC?**
**Short:** gRPC's standard Health service exposes Check, Watch, and List RPCs reporting SERVING, NOT_SERVING, or UNKNOWN status.

gRPC defines a standard health service, grpc.health.v1.Health, whose ServingStatus enum is UNKNOWN, SERVING, NOT_SERVING and SERVICE_UNKNOWN. It exposes more than one RPC: Check for a point-in-time answer, Watch for a stream of status changes, and (more recently) List for a snapshot of every registered service. SERVICE_UNKNOWN is only ever returned by Watch — Check answers an unregistered service name with a NOT_FOUND status instead. Kubernetes calls this service directly via its native `grpc` probe type (GA in 1.27); the `grpc-health-probe` exec binary is only needed outside Kubernetes or for custom metadata/TLS on the probe. Load balancers use it for backend health checks. Implement it by registering HealthStatusManager on the server and calling setStatus() when the service starts/stops.

**Q: How do you evolve a protobuf schema without breaking clients?**
**Short:** Safe protobuf changes add fields or enum values and rename fields; unsafe ones delete or reuse a field number or change type.

Safe changes: add new optional fields with new numbers, add new enum values, add new RPCs, and rename a field. A rename changes the name only, not the number, so it has no wire impact — though it does break generated code and JSON/TextFormat parsing. Breaking changes: delete a field without reserving its number, reuse a field number, or change a field to an incompatible type. Type changes are not uniformly breaking — int32, uint32, int64, uint64 and bool are mutually wire-compatible, sint32 and sint64 are compatible with each other but with nothing else, fixed32 with sfixed32, fixed64 with sfixed64, and string with bytes when the bytes are valid UTF-8. Best practice: use `buf breaking --against` in CI to automatically detect breaking changes.

**Q: What is the difference between proto2 and proto3?**
**Short:** Proto3 dropped required fields and custom defaults, and gained explicit field presence via the optional label in 3.15.

Proto3 is the default syntax for new .proto files and proto2 is its predecessor. Key differences: proto3 dropped `required` entirely, dropped user-specified custom defaults (the default is always the zero value), and defines a canonical JSON mapping. Proto2 had `required` fields (dangerous — adding one breaks every old sender), explicit `optional` with custom defaults, and extension ranges. Field presence is the subtle one: proto3 originally had no way to distinguish an unset scalar from one explicitly set to zero, which is why `google.protobuf.Int32Value`-style wrapper types were the old workaround — but protobuf 3.15 (February 2021) made the `optional` label generally available in proto3, and that, not a wrapper type, is now the standard way to get explicit presence. (`google.protobuf.FieldMask` is unrelated: it is a client-supplied list of paths for partial reads and updates, not a presence mechanism.) Newer still, Protobuf Editions replaces the proto2/proto3 syntax split with per-feature settings, so expect `edition = "2023"` files alongside proto3 ones.

**Q: What is gRPC-Web and when do you need it?**
**Short:** gRPC-Web lets browsers call gRPC services by encoding trailers into a data frame through a translating proxy.

gRPC-Web is a variant of the gRPC protocol that browsers can use. Browsers cannot use HTTP/2 trailers (gRPC uses trailers for the grpc-status code), so gRPC-Web encodes trailing metadata in a special data frame. An Envoy sidecar or nginx gRPC-Web proxy translates between gRPC and gRPC-Web. gRPC-Web only supports unary and server-streaming; bidirectional streaming is not supported. Use gRPC-Web when browser clients need to call gRPC services directly.

**Q: How does gRPC handle load balancing?**
**Short:** gRPC load balances client-side via channel-resolved backend IPs, or through a proxy that distributes connections or RPCs.

gRPC supports both client-side and proxy load balancing. Client-side: the gRPC channel resolves DNS, gets all backend IPs, and applies a load balancing policy (round-robin, pick-first). This is common with Kubernetes headless services. Proxy load balancing: a proxy (Envoy, Nginx, AWS NLB) receives all connections and distributes them. With HTTP/2, a single gRPC connection multiplexes many RPCs — L4 load balancers see one connection per client; L7 load balancers can distribute individual RPCs.

**Q: What is the maximum message size in gRPC and how do you change it?**
**Short:** gRPC's default maximum inbound message size is 4 MB, changeable via maxInboundMessageSize on the server and client builders.

The default maximum inbound message size is 4 MB (4,194,304 bytes). gRPC sets no default cap on outbound (send) size, so in practice the receiving peer's 4 MB inbound limit is what actually rejects your message. Change via: server-side: `ServerBuilder.maxInboundMessageSize(50 * 1024 * 1024)` (50 MB). Client-side: `ManagedChannelBuilder.maxInboundMessageSize(50 * 1024 * 1024)`. Better approach for large payloads: stream messages to avoid sending one large message; or store data in object storage and pass a reference URI in the message.

**Q: Describe a scenario where gRPC bidirectional streaming provides value REST cannot easily match.**
**Short:** Bidirectional streaming lets both sides of a real-time order matching engine stream independently over one persistent connection.

A real-time bidirectional order matching engine: traders send market orders and receive execution notifications in real-time. REST would require polling (latency), WebSocket (adds complexity and library overhead), or SSE (unidirectional only). gRPC bidirectional streaming provides full-duplex communication with protobuf efficiency and built-in flow control. Each side streams independently — the trader sends new orders, the server sends execution confirmations and market data updates — over one persistent connection with back-pressure from HTTP/2 flow control.

**Q: What is the grpc reflection protocol?**
**Short:** gRPC reflection lets clients like grpcurl discover service definitions at runtime without having the .proto files.

gRPC reflection allows clients to query the server for service definitions at runtime, without having the .proto files. Tools like `grpcurl` and `evans` use reflection to discover available services and methods. In grpc-java, register `ProtoReflectionServiceV1.newInstance()`, which speaks the stable `grpc.reflection.v1` protocol. Disable in production if you do not want to expose your API schema to clients (reflection is informational only — it does not grant access, but may expose schema information to attackers).

**Q: How do you implement retries in gRPC?**
**Short:** gRPC retries are configured via a service config's retry policy and are only safe for idempotent RPCs.

gRPC supports a service config JSON with retry policy: `maxAttempts` (max total attempts), `initialBackoff`, `maxBackoff`, `backoffMultiplier`, and `retryableStatusCodes` (e.g., UNAVAILABLE, DEADLINE_EXCEEDED). This is specified in the service config loaded by the name resolver. For Java, configure via ManagedChannelBuilder with a default service config. Retries are transparent to the application code — the channel handles them. Only retry idempotent RPCs or explicitly idempotent operations.

---

## 13. Best Practices

- Always set deadlines on every gRPC call in production.
- Use `buf` for proto linting and breaking change detection in CI.
- Reserve removed field numbers: `reserved 3; reserved "old_field";`
- Implement the Health Checking Protocol on every gRPC server.
- Use interceptors for cross-cutting concerns — do not put auth/logging in business logic.
- Prefer proto3 for all new services; avoid required fields.
- Use status codes precisely — INVALID_ARGUMENT for client errors, INTERNAL for server errors; avoid UNKNOWN.
- Document .proto services with comments — they appear in generated code and grpcurl output.
- For high-throughput streaming, respect back-pressure: check `isReady()` before sending on streaming observers.

---

## 14. Case Study

**Problem** (illustrative worked example, not a published incident): A financial data service was sending JSON batch updates every 10 seconds (200 KB per update) to 500 subscriber services over REST — 0.2 MB x 500 / 10 s = **10 MB/s** of egress, plus significant JSON parsing CPU overhead.

**Migration to gRPC server streaming** (one subscribe request, an open-ended stream of updates back — the `.proto` below is server-streaming, not bidirectional):
```protobuf
message MarketUpdate {
  int64 timestamp = 1;
  repeated PricePoint prices = 2;

  message PricePoint {
    string symbol = 1;
    double bid = 2;
    double ask = 3;
    int64 volume = 4;
  }
}

message SubscribeRequest {
  repeated string symbols = 1;
}

service MarketDataService {
  rpc StreamPrices (SubscribeRequest) returns (stream MarketUpdate);
}
```

**Results after migration**:
- Protobuf serialization: 200 KB JSON → 34 KB protobuf (83% reduction)
- Parsing CPU: a large reduction, but no figure is offered here — the direction is safe (protobuf decodes fixed-width fields by tag with no text scanning, no string-to-number conversion and no key interning), while the magnitude swings wildly with message shape, language and library. Nobody publishes a number that transfers, so measure your own rather than inheriting one
- Latency: each update delivered within 50ms vs 10s batch polling
- Connection overhead: 500 persistent HTTP/2 streams vs 500 * 6 polling connections/minute
- Total bandwidth: 10 MB/s → 1.7 MB/s (the same 83%, since the payload shrank and the fan-out did not change)

**Lessons**: gRPC streaming is particularly valuable for high-frequency, structured data. Binary serialization pays off most when messages have many numeric fields. The migration required updating 500 subscriber services to use generated stubs — feasible because all were internal Java services with access to the proto repository.
