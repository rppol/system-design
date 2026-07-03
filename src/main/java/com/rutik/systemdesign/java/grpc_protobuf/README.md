# gRPC and Protocol Buffers in Java

> How gRPC actually works on the wire and in the generated Java stubs: the protobuf
> binary format and its schema-evolution rules, the four RPC modes, interceptors,
> deadlines/cancellation, the `Status` error model, and the HTTP/2 transport that
> makes streaming possible. Pure Java/`io.grpc` — the Spring-applied view lives in
> the section cross-links.

---

## 1. Concept Overview

gRPC is a contract-first RPC framework: you declare services and messages in a
`.proto` file, a code generator produces typed client stubs and server base classes,
and calls travel as **Protocol Buffers** (a compact binary serialization) over
**HTTP/2**. Two technologies are doing the work and it pays to keep them separate:

- **Protocol Buffers** — the *serialization format and IDL*. It defines how a
  message is described (`.proto`) and how it is encoded into bytes (tag-length-value
  with varints). Protobuf is usable entirely without gRPC.
- **gRPC** — the *RPC layer*. It defines how a method call maps onto HTTP/2 streams,
  how metadata (headers) travel, how errors are reported (`Status`), and how
  streaming, deadlines, and cancellation behave.

The combination matters for senior interviews because it is the default for
internal service-to-service traffic at scale (Google, Netflix, most cloud-native
shops): it is faster and smaller than JSON/REST, strongly typed across language
boundaries, and supports bidirectional streaming that REST cannot express cleanly.
The tradeoffs — binary opacity, browser-unfriendliness, HTTP/2 requirement — are
the other half of the discussion.

---

## 2. Intuition

**One-line analogy.** REST/JSON is mailing a handwritten letter (verbose,
human-readable, anyone can open it). gRPC/protobuf is sending a pre-printed form
where each field has a numbered box — tiny, unambiguous, but you need the form
template (the `.proto`) to read it.

**Mental model.** The field *number*, not the field *name*, is the identity of a
field on the wire. The name exists only in your `.proto` and generated code; the
bytes carry `1`, `2`, `3`. This single fact explains almost every schema-evolution
rule: you can rename freely, but you can never reuse a number.

**Why it matters.** Most gRPC production incidents are schema-evolution mistakes
(reused field numbers, changed types, removed required fields) or deadline/cancel
omissions (a client gives up but the server keeps grinding). Understanding the wire
format and the deadline propagation model prevents both classes.

**Key insight.** A gRPC stream *is* an HTTP/2 stream. Everything gRPC offers —
multiplexing many calls on one TCP connection, client/server/bidi streaming, flow
control, cancellation — is HTTP/2's stream machinery exposed through a typed API.

---

## 3. Core Principles

1. **Contract first.** The `.proto` is the source of truth; clients and servers in
   any language generate from it. Compatibility is a property of the contract, not
   of any one implementation.

2. **Field numbers are forever.** Encoding keys on numbers makes the format compact
   and evolvable — but only if numbers are treated as immutable identities. Renaming
   is free; renumbering or reusing is corruption.

3. **Everything is optional on the wire.** In proto3, unset scalar fields are simply
   absent and decode to defaults (0, "", false). There is no "required" — robustness
   to missing fields is built in, which is what makes forward/backward compatibility
   possible.

4. **One call = one HTTP/2 stream.** Unary and streaming differ only in how many
   messages flow on that stream and in which direction(s). Many streams multiplex
   over one connection.

5. **Deadlines, not timeouts; and they propagate.** A deadline is an absolute point
   in time sent with the call and carried downstream, so the whole call tree shares
   one budget. Cancellation flows from client to server automatically.

6. **Errors are a `Status` code + message + optional details**, not exceptions on
   the wire. The 17-code `Status` enum is the universal vocabulary.

---

## 4. Types / Architectures / Strategies

### The four RPC modes

| Mode | Client sends | Server sends | Java stub signature shape | Example use |
|------|-------------|--------------|---------------------------|-------------|
| **Unary** | 1 | 1 | `Reply rpc(Request)` (blocking) | Fetch a user |
| **Server streaming** | 1 | many | `Iterator<Reply> rpc(Request)` | Stream search results / a feed |
| **Client streaming** | many | 1 | `StreamObserver<Request> rpc(StreamObserver<Reply>)` | Upload chunks, aggregate |
| **Bidirectional streaming** | many | many | `StreamObserver<Request> rpc(StreamObserver<Reply>)` | Chat, live telemetry |

### Generated Java stub flavors

- **Blocking stub** — synchronous; unary returns the value, server-streaming returns
  a blocking `Iterator`. Cannot do client/bidi streaming.
- **Async stub** — callback-based via `StreamObserver`; required for client/bidi
  streaming.
- **Future stub** — unary only; returns a `ListenableFuture` (Guava).

### Protobuf wire types (how the tag's low 3 bits pick a decoder)

| Wire type | Number | Used by |
|-----------|--------|---------|
| VARINT | 0 | int32/64, uint, bool, enum, sint (zigzag) |
| I64 | 1 | fixed64, sfixed64, double |
| LEN | 2 | string, bytes, embedded messages, packed repeated |
| I32 | 5 | fixed32, sfixed32, float |

---

## 5. Architecture Diagrams

### Protobuf field encoding: the field number drives the wire format

```
.proto:                 message User { string name = 1; int32 age = 2; }
on the wire (name="Al", age=30):

  byte:   0x0A   0x02   'A'  'l'    0x10   0x1E
          ^^^^   ^^^^   ^^^^^^^^    ^^^^   ^^^^
  tag = (field<<3)|wiretype        tag = (2<<3)|0   value (varint 30)
  (1<<3)|2 = 0x0A  len=2  "Al"     = 0x10

The NAME "name"/"age" never appears. Field number 1 and 2 are the identity.
=> rename name->fullName: wire-compatible. reuse number 1 for a new field: CORRUPTION.
```

### One HTTP/2 connection multiplexes many gRPC streams

```mermaid
sequenceDiagram
    participant Client
    participant Server
    Note over Client,Server: single TCP + TLS connection (HTTP/2) carries all streams below
    par Stream 1 - GetUser (unary)
        Client->>Server: request
        Server-->>Client: response
    and Stream 3 - Search (server streaming)
        Client->>Server: request
        Server-->>Client: response
        Server-->>Client: response
        Server-->>Client: response
    and Stream 5 - Chat (bidirectional)
        Client->>Server: message
        Server-->>Client: message
        Client->>Server: message
        Server-->>Client: message
    end
    Note over Client,Server: no head-of-line blocking between streams; each has its own flow-control window
```
Three independent gRPC calls — unary, server-streaming, bidirectional — multiplex
over one HTTP/2 connection as separate streams with independent flow control.

REST/HTTP/1.1 would need one connection per concurrent call (or pipelining with
head-of-line blocking). HTTP/2 multiplexing is why gRPC streaming is cheap.

### Deadline propagation shares one budget across the call tree

```mermaid
sequenceDiagram
    participant Client
    participant A as Service A
    participant B as Service B
    Client->>A: call, deadline = now + 300ms
    Note right of Client: 300ms budget travels in the grpc-timeout header
    A->>A: spends 50ms
    A->>B: call, remaining deadline = 250ms
    B->>B: spends 250ms - deadline hit
    B-->>A: DEADLINE_EXCEEDED
    A-->>Client: DEADLINE_EXCEEDED
    Note over A,B: cancellation propagates back up automatically; B stops work, A stops waiting
```
A single absolute deadline set by the client travels downstream and shrinks at each
hop; once it is exceeded, cancellation propagates back up the call tree so no
service keeps working for a caller that has already given up.

---

## 6. How It Works — Detailed Mechanics

### 6.1 The contract

```protobuf
syntax = "proto3";
package shop;
option java_package = "com.rutik.shop.grpc";
option java_multiple_files = true;

message GetUserRequest  { int64 id = 1; }
message User            { int64 id = 1; string name = 2; int32 age = 3; }
message SearchRequest   { string query = 1; }

service UserService {
  rpc GetUser   (GetUserRequest) returns (User);                 // unary
  rpc Search    (SearchRequest)  returns (stream User);          // server streaming
  rpc BulkAdd   (stream User)    returns (GetUserRequest);       // client streaming
  rpc Chat      (stream User)    returns (stream User);          // bidirectional
}
```

The `protoc` plugin (`protoc-gen-grpc-java`) generates `UserServiceGrpc` with the
base class and the three stub flavors, plus message classes with builders.

### 6.2 Server: implementing unary and server-streaming

`StreamObserver` is the universal callback: `onNext` emits a message, `onCompleted`
ends the stream, `onError` ends it with a `Status`.

```java
public class UserServiceImpl extends UserServiceGrpc.UserServiceImplBase {

    @Override
    public void getUser(GetUserRequest req, StreamObserver<User> obs) {
        User user = repository.find(req.getId());
        if (user == null) {
            // gRPC errors are a Status, not a thrown exception on the wire
            obs.onError(Status.NOT_FOUND
                .withDescription("no user " + req.getId())
                .asRuntimeException());
            return;
        }
        obs.onNext(user);     // exactly one message for unary
        obs.onCompleted();    // closes the stream
    }

    @Override
    public void search(SearchRequest req, StreamObserver<User> obs) {
        for (User u : repository.search(req.getQuery())) {
            if (Context.current().isCancelled()) return;  // honor client cancel
            obs.onNext(u);                                 // many messages
        }
        obs.onCompleted();
    }
}
```

### 6.3 Client: blocking unary and async bidi

```java
ManagedChannel channel = ManagedChannelBuilder
        .forAddress("user-svc", 50051)
        .usePlaintext()                       // TLS in prod; plaintext for demo
        .build();

// blocking unary with a deadline
UserServiceGrpc.UserServiceBlockingStub blocking =
        UserServiceGrpc.newBlockingStub(channel)
            .withDeadlineAfter(300, TimeUnit.MILLISECONDS);   // per-call deadline
User u = blocking.getUser(GetUserRequest.newBuilder().setId(42).build());

// async bidirectional
UserServiceGrpc.UserServiceStub async = UserServiceGrpc.newStub(channel);
StreamObserver<User> toServer = async.chat(new StreamObserver<>() {
    @Override public void onNext(User reply)    { handle(reply); }
    @Override public void onError(Throwable t)  { log.warn("chat failed", t); }
    @Override public void onCompleted()         { log.info("chat done"); }
});
toServer.onNext(User.newBuilder().setName("hi").build());
toServer.onCompleted();
```

### 6.4 Interceptors (the gRPC equivalent of filters)

Interceptors wrap calls for cross-cutting concerns — auth, logging, tracing,
metrics. Server side:

```java
public class AuthInterceptor implements ServerInterceptor {
    static final Metadata.Key<String> TOKEN =
        Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <Req, Resp> ServerCall.Listener<Req> interceptCall(
            ServerCall<Req, Resp> call, Metadata headers,
            ServerCallHandler<Req, Resp> next) {
        String token = headers.get(TOKEN);
        if (!isValid(token)) {
            call.close(Status.UNAUTHENTICATED.withDescription("bad token"),
                       new Metadata());
            return new ServerCall.Listener<>() {};   // no-op listener
        }
        return next.startCall(call, headers);        // proceed
    }
}
```

Metadata (the `Metadata` object) is gRPC's headers/trailers — this is where trace
ids and auth tokens ride, analogous to HTTP headers.

### 6.5 Schema evolution rules (the part that bites in production)

| Change | Safe? | Why |
|--------|-------|-----|
| Add a new field with a new number | Yes | Old code ignores unknown fields; new code reads default if absent |
| Rename a field (same number) | Yes | Name is not on the wire |
| Remove a field | Yes if you `reserve` the number | Prevents future reuse |
| **Reuse a deleted field's number** | **No** | Old data decodes new field as garbage |
| Change a field's type (e.g. int32→string) | No (mostly) | Wire type differs → decode error/garbage |
| int32 ↔ int64 ↔ bool ↔ uint32 (all varint) | Sometimes | Same varint wire type, but value truncation risk |
| Change field number | No | It is a different field |

```protobuf
message User {
  reserved 4, 7;                 // numbers of deleted fields - never reuse
  reserved "legacy_email";       // and the old name
  int64 id = 1;
  string name = 2;
}
```

`reserved` is the guardrail: it makes the compiler reject any future attempt to
reuse a retired number or name.

---

## 7. Real-World Examples

- **Google** — gRPC is the public face of Stubby, the internal RPC system that
  carries essentially all of Google's service traffic; protobuf is the lingua franca
  for inter-service data.
- **Netflix** — moved much internal service-to-service communication to gRPC for the
  typed contracts and bidi streaming; ties into their service mesh.
- **Kubernetes / etcd** — the entire etcd API and many K8s components speak gRPC;
  `kubectl`'s watch is a server-streaming RPC.
- **CockroachDB / TiKV** — node-to-node communication is gRPC streaming.
- **gRPC-Web + Envoy** — because browsers cannot speak raw HTTP/2 trailers, Envoy
  translates gRPC-Web (over HTTP/1.1/2) to backend gRPC — the standard answer to
  "how do I call gRPC from a browser."

---

## 8. Tradeoffs

| Dimension | gRPC/protobuf | REST/JSON | gRPC wins when… |
|-----------|---------------|-----------|-----------------|
| Payload size | Compact binary | Verbose text | Bandwidth/CPU matter at scale |
| Schema | Strong, enforced, codegen | Loose (OpenAPI optional) | Cross-language type safety needed |
| Streaming | Native 4 modes incl. bidi | Awkward (SSE/chunked) | Real-time / long-lived streams |
| Browser support | Needs gRPC-Web + proxy | Native | Public/browser APIs |
| Human-debuggability | Opaque (need `.proto`) | curl-able | Internal traffic where tooling exists |
| Transport | Requires HTTP/2 | HTTP/1.1 fine | Modern infra |

| Stub type | Pros | Cons |
|-----------|------|------|
| Blocking | Simple, sequential code | Ties up a thread per call; no client/bidi streaming |
| Async (StreamObserver) | All 4 modes; non-blocking | Callback complexity |
| Future | Composable for unary | Unary only; Guava dependency |

---

## 9. When to Use / When NOT to Use

**Use gRPC when** you control both ends (internal microservices), want strong typed
contracts across languages, need streaming, or are bandwidth/latency sensitive.
It shines in a service mesh with mTLS and HTTP/2 already in play.

**Avoid gRPC when** the consumer is a browser or third party expecting REST (use
gRPC-Web or a REST gateway), when human-readable debuggability/curl-ability is a
hard requirement, when your infra/proxies do not support HTTP/2 end-to-end, or for
simple public CRUD APIs where REST's ubiquity wins.

**Use protobuf without gRPC** for compact storage, Kafka message payloads (with a
schema registry), or any place you want a compact, evolvable binary format
independent of the RPC layer.

---

## 10. Common Pitfalls

1. **Reusing a field number.** A team deleted `email = 4` and later added
   `phone = 4`. Old clients sent strings into a field new servers now read
   differently → silent data corruption. *Fix:* always `reserved 4;` on deletion.

2. **No deadlines.** Clients called without `withDeadlineAfter`, so when a downstream
   hung, server threads piled up waiting indefinitely until the pool exhausted and
   the service fell over. *Fix:* every call gets a deadline; deadlines propagate.

3. **Ignoring cancellation on the server.** A server-streaming RPC kept producing
   into a stream whose client had long disconnected, burning CPU and DB. *Fix:*
   check `Context.current().isCancelled()` in the produce loop.

4. **Channel per call.** Creating a new `ManagedChannel` for every request (instead
   of reusing one) destroyed the whole point of HTTP/2 multiplexing and exhausted
   sockets. *Fix:* one long-lived channel, shared and reused.

5. **Treating proto3 defaults as "set".** Code checked `if (user.getAge() != 0)` to
   mean "age provided," but a real age of 0 and an unset field are indistinguishable
   for proto3 scalars. *Fix:* use `optional` (proto3 field presence) or a wrapper
   when you must distinguish unset from default.

6. **Giant unary instead of streaming.** Returning a 200 MB list in one unary
   message blew past the default 4 MB max message size and OOM'd. *Fix:* server
   streaming pages results instead of one huge message.

7. **Forgetting blocking stubs can't stream client/bidi.** Reaching for the blocking
   stub then discovering it cannot do client-streaming. *Fix:* use the async stub for
   anything beyond unary + server-streaming.

---

## 11. Technologies & Tools

| Concern | Tools |
|---------|-------|
| Codegen | `protoc`, `protoc-gen-grpc-java`, `protobuf-maven-plugin`, Gradle protobuf plugin |
| Runtime | `io.grpc:grpc-netty`/`grpc-netty-shaded`, `grpc-protobuf`, `grpc-stub` |
| Browser | gRPC-Web, Envoy, Connect (buf) |
| Debugging | `grpcurl`, `grpcui`, BloomRPC/Kreya, server reflection |
| Schema mgmt | Buf (`buf lint`, `buf breaking`), Protobuf schema registry |
| Observability | grpc interceptors + OpenTelemetry, channelz |
| Health/LB | grpc health checking protocol, xDS / service mesh |

---

## 12. Interview Questions with Answers

**What is the difference between gRPC and Protocol Buffers?**
Protocol Buffers is the serialization format and interface definition language — it
defines how messages are described in `.proto` and encoded to compact binary;
it is usable on its own for storage or messaging. gRPC is the RPC framework layered
on top: it maps service methods to HTTP/2 streams and defines metadata, the `Status`
error model, streaming modes, deadlines, and cancellation. Protobuf is the "what's in
the message"; gRPC is the "how the call travels."

**Why are protobuf field numbers so important, and what are the rules around them?**
The field number, not the name, is the field's identity on the wire — the encoded
bytes carry the number, never the name. So you can freely rename fields (wire-
compatible), but you must never reuse or change a number, because old data encoded
under that number would be misinterpreted. When you delete a field you should mark
its number `reserved` so the compiler prevents anyone from reusing it later. This one
rule underlies almost all of protobuf's schema-evolution guidance.

**What are the four gRPC RPC modes?**
Unary (one request, one response — like a normal function call); server streaming
(one request, a stream of responses — e.g. a search feed); client streaming (a
stream of requests, one response — e.g. chunked upload with an aggregate); and
bidirectional streaming (independent request and response streams over the same
connection — e.g. chat). All four are HTTP/2 streams differing only in how many
messages flow in each direction.

**How does gRPC use HTTP/2, and why does that matter?**
Each gRPC call is an HTTP/2 stream, and many streams multiplex over a single TCP
connection with independent flow-control windows and no head-of-line blocking
between them. This is what makes streaming and high concurrency cheap — you do not
need a connection per concurrent call as you effectively do with HTTP/1.1. It also
means gRPC requires end-to-end HTTP/2 support, which is why browsers (no raw HTTP/2
trailer access) need gRPC-Web via a proxy.

**What is the difference between a deadline and a timeout, and why does it matter?**
A timeout is a per-call relative duration; a deadline is an absolute point in time
that gRPC sends with the request (in the `grpc-timeout` header) and propagates
downstream, so the entire call tree shares one budget. If service A spends 50ms of a
300ms deadline, B sees only 250ms remaining. This prevents the classic waste where a
client times out but downstream services keep working — the deadline cancels the
whole chain. Always set deadlines; never call without one in production.

**How does cancellation work in gRPC?**
When a client cancels (explicitly, or by hitting its deadline, or by disconnecting),
gRPC propagates the cancellation down the call tree as a `Context` cancellation.
Server code should observe it — e.g. check `Context.current().isCancelled()` in a
streaming produce loop — and stop work, releasing resources. Without honoring
cancellation, a server keeps computing results nobody will read, wasting CPU,
threads, and DB connections.

**How are errors represented in gRPC?**
As a `Status`: a numeric code from a fixed set of ~17 (e.g. `NOT_FOUND`,
`INVALID_ARGUMENT`, `UNAVAILABLE`, `DEADLINE_EXCEEDED`, `UNAUTHENTICATED`), an
optional message, and optional structured details. Servers signal errors by calling
`onError` with a `Status` (e.g. `Status.NOT_FOUND.asRuntimeException()`), not by
letting arbitrary exceptions escape. Clients receive a `StatusRuntimeException`. The
fixed code set gives a portable, language-neutral error vocabulary — unlike the open-
ended world of HTTP status + JSON bodies.

**What are the differences between the blocking, async, and future stubs?**
The blocking stub is synchronous: unary returns the value directly and server-
streaming returns a blocking `Iterator`, but it cannot do client or bidirectional
streaming. The async stub is callback-based via `StreamObserver` and supports all
four modes — it is required for client/bidi streaming. The future stub returns a
Guava `ListenableFuture` and supports unary only. Choose blocking for simple
sequential code, async whenever you stream from the client side.

**In proto3, how do you tell an unset field from one set to its default value?**
For plain scalar fields you cannot — an unset `int32` and one set to 0 both decode to
0, because proto3 omits default-valued scalars from the wire. To distinguish, mark
the field `optional` (which re-enables field presence/hasser methods in proto3) or
use a wrapper type like `google.protobuf.Int32Value`. This is a common bug: checking
`!= 0` or `!isEmpty()` to mean "provided" silently mishandles legitimate
default-valued inputs.

**Why is reusing one `ManagedChannel` important?**
A channel owns the HTTP/2 connection(s) and their multiplexing; creating a new
channel per call throws away connection reuse, forces a fresh TCP+TLS handshake each
time, and can exhaust sockets — defeating the main performance benefit of HTTP/2.
Channels are designed to be long-lived, thread-safe, and shared across the
application; create one per target service and reuse it.

**What are gRPC interceptors and what do you use them for?**
Interceptors wrap calls to apply cross-cutting concerns without touching service
logic — authentication, logging, metrics, tracing, retry. A `ServerInterceptor`
inspects incoming `Metadata` (gRPC's headers) and can short-circuit (e.g. close with
`UNAUTHENTICATED`) or proceed; a `ClientInterceptor` can attach metadata like trace
ids or auth tokens. They are the gRPC analogue of servlet filters / middleware.

**What is Metadata in gRPC?**
Metadata is the key-value header/trailer mechanism that travels alongside the
message payload — the place auth tokens, trace context (`traceparent`), and custom
routing hints ride, analogous to HTTP headers. Keys ending in `-bin` carry binary
values; others are ASCII. Interceptors are the usual place to read/write metadata.

**How would you safely evolve a protobuf schema?**
Only add new fields with brand-new numbers; never reuse, renumber, or change the type
of existing fields. When removing a field, `reserved` its number (and optionally its
name) so it can never be reused. Avoid changing wire types (int32→string breaks);
varint-to-varint changes (int32↔int64↔bool) are technically wire-compatible but risk
value truncation. Tools like `buf breaking` mechanically enforce these rules in CI.

**Why can't a browser call a gRPC service directly, and what's the fix?**
Browsers cannot access raw HTTP/2 frames and trailers (where gRPC carries its status)
through the fetch/XHR APIs, so they cannot speak native gRPC. The fix is gRPC-Web: a
browser-compatible variant that a proxy (Envoy, or the Connect/buf stack) translates
to/from backend gRPC. Alternatively, expose a REST/JSON gateway (e.g. grpc-gateway)
in front of the gRPC services for public consumption.

**What is the default max message size and why does it matter?**
gRPC defaults to a 4 MB max inbound message size to bound memory per call. Trying to
return a very large result in a single unary message exceeds it and fails (or OOMs if
raised carelessly). The right answer for large/unbounded results is server streaming
— page the data as many small messages — rather than inflating the limit. The limit
is a deliberate backpressure/safety guard, not an obstacle to remove.

**How does protobuf encode an integer, and what is a varint?**
A varint is a variable-length encoding where each byte uses 7 bits for the value and
1 continuation bit, so small numbers take one byte and large ones take more — making
typical small integers very compact. Each field is encoded as a tag (the field number
shifted left 3 bits, OR'd with a 3-bit wire type) followed by the value. Signed
numbers use zigzag encoding (`sint32`/`sint64`) so small-magnitude negatives stay
small, because plain two's-complement negatives would otherwise always take the
maximum bytes.

---

## 13. Best Practices

- **Always set a deadline** on every client call and let it propagate; treat a
  deadline-less call as a bug.
- **Reuse one `ManagedChannel`** per target; never create one per request.
- **`reserved` deleted field numbers and names** — make schema breakage a compile
  error, and run `buf breaking` in CI.
- **Honor cancellation** in streaming servers via `Context.current().isCancelled()`.
- **Use `optional` (or wrappers)** when you genuinely must distinguish unset from
  default.
- **Stream large/unbounded results**; do not stuff them into one unary message.
- **Put cross-cutting concerns in interceptors**, not in every service method.
- **Use TLS/mTLS** in production; `usePlaintext()` is for local demos only.
- **Map exceptions to meaningful `Status` codes** rather than leaking `UNKNOWN`.

---

## 14. Case Study

### Internal product-catalog service: REST→gRPC migration

**Problem.** A catalog service served three internal consumers over REST/JSON. At
peak, JSON (de)serialization dominated CPU, payloads were large, and a new
recommendations team needed a *live stream* of price changes — awkward over REST
(they were polling every 2s, hammering the service). The team migrated to gRPC.

**Requirements.**
- Cut serialization CPU and payload size for the high-volume `GetProduct` path.
- Provide a real-time price-change stream instead of polling.
- Strong typed contracts shared across Java and Go consumers.
- Bound resource use: no runaway calls, no giant responses.

**Design.**

1. **Contract.** `GetProduct` as unary; `WatchPrices` as server streaming:

```protobuf
service Catalog {
  rpc GetProduct  (GetProductRequest) returns (Product);
  rpc WatchPrices (WatchRequest)      returns (stream PriceUpdate);
}
message Product { int64 id = 1; string name = 2; int64 price_cents = 3;
                  reserved 4; reserved "legacy_currency"; }  // retired field guarded
```

2. **Deadlines + channel reuse.** Every consumer uses one shared `ManagedChannel`
   and `withDeadlineAfter(200, MS)` on `GetProduct`, propagated to the downstream
   pricing service so the whole chain shares a 200ms budget.

3. **Streaming replaces polling.** `WatchPrices` pushes updates; the server checks
   `Context.current().isCancelled()` so a disconnected recommender stops producing
   immediately.

4. **Interceptors.** One `AuthInterceptor` (validates a service token in metadata)
   and one tracing interceptor (propagates `traceparent`) applied to all methods.

**Broken → fixed (the schema-evolution bug that hit staging):**

```protobuf
// BROKEN: a later PR deleted currency=4 then reused number 4 for a new field
message Product {
  int64 id = 1; string name = 2; int64 price_cents = 3;
  bool on_sale = 4;            // <- number 4 was once 'currency' (a string)
}
// Old Go clients still sent a string into field 4; new Java servers read it
// as a bool -> garbage decode, intermittent wrong "on_sale" flags.

// FIXED: reserve the retired number, give the new field a fresh number
message Product {
  int64 id = 1; string name = 2; int64 price_cents = 3;
  reserved 4; reserved "currency";
  bool on_sale = 5;
}
```

**Outcomes (measured).**
- `GetProduct` payload size: **~4.1 KB JSON → ~900 B protobuf** (~78% smaller).
- Serialization CPU on the hot path dropped enough to remove **2 of 8** service
  instances at the same traffic.
- Recommendations polling (every 2s from 12 workers) was replaced by a single
  `WatchPrices` stream per worker — peak request rate on the catalog service fell by
  an order of magnitude, and price-change propagation latency went from up-to-2s to
  sub-100ms.
- The reserved-number discipline (enforced by `buf breaking` in CI) prevented any
  repeat of the staging corruption after it was caught.

**Tradeoffs accepted.** Loss of curl-ability (mitigated with `grpcurl` + server
reflection) and the need for an Envoy gRPC-Web hop for one browser-based internal
admin tool. Both were acceptable given the CPU, bandwidth, and streaming wins.

---

## See Also

- [networking_and_http_client](../networking_and_http_client/README.md) — HTTP/2
  multiplexing and the `HttpClient` that underlies modern transports.
- [microservices_patterns](../microservices_patterns/README.md) — deadlines,
  cancellation, and context propagation in the broader resilience picture.
- [generics_and_type_system](../generics_and_type_system/README.md) — generated
  stub generics and `StreamObserver<T>` typing.
- [gRPC & Protobuf (backend design)](../../backend/grpc_and_protobuf/README.md) —
  gRPC at the architecture/design level (load balancing, mesh, API design).
- [Spring gRPC](../../spring/spring_grpc/README.md) — the Spring Boot-managed
  channel/server lifecycle, interceptors as beans, and auto-configuration built on
  top of the pure-`io.grpc` mechanics covered here.
