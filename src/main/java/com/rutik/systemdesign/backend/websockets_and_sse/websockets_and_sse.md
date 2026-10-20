# WebSockets & Server-Sent Events

## 1. Concept Overview

HTTP is fundamentally request-response: the client initiates every interaction. For real-time applications — live chat, collaborative editing, trading dashboards, gaming — this model breaks down. Two technologies bridge this gap: WebSocket provides full-duplex, bidirectional communication over a persistent connection; Server-Sent Events (SSE) provides a server-to-client push channel over a standard HTTP connection.

Long polling was the original workaround: the client sends a request and the server holds it open until data is available, then the client immediately sends another. It works but creates connection overhead and cannot scale efficiently. WebSocket and SSE solve real-time communication properly — each with distinct characteristics that suit different use cases.

---

## 2. Intuition

> **One-line analogy**: HTTP request-response is a walkie-talkie where you push a button to talk. WebSocket is a phone call — both parties can talk simultaneously at any time. SSE is a radio broadcast — the station (server) continuously transmits and listeners (clients) only receive.

**Mental model**: WebSocket upgrades an HTTP connection to a full-duplex TCP channel. Once established, it is a raw bidirectional byte channel with a thin framing layer. SSE keeps an HTTP connection open indefinitely, with the server sending text/event-stream messages and the browser's EventSource API parsing them automatically.

**Why it matters**: Choosing incorrectly causes either wasted engineering effort (implementing full WebSocket when SSE suffices) or architecture limitations (using SSE when bidirectional communication is needed). Infrastructure also matters: load balancers, firewalls, and proxies treat WebSocket and SSE differently.

**Key insight**: SSE is simpler, more reliable (automatic reconnection is built into the browser), and works with standard HTTP/2 multiplexing. Use SSE whenever communication is server-to-client only. Use WebSocket only when clients need to send data at high frequency (gaming, collaborative editing, trading order entry).

---

## 3. Core Principles

- **WebSocket**: Starts as HTTP/1.1, upgrades to WS protocol. Persistent TCP connection. Bidirectional. Binary or text frames. No automatic reconnection.
- **SSE**: Standard HTTP/1.1 (or HTTP/2). Persistent response stream. Server-to-client only. Text format. Automatic reconnection built into EventSource API.
- **Long polling**: Standard HTTP. Client sends request, server holds it. After response, client immediately sends next request. Works everywhere; less efficient.
- **Stateful connections**: Both WebSocket and SSE are stateful — the server must route reconnecting clients to the same server instance (sticky sessions) or use a shared pub/sub backend.

---

## 4. Types / Architectures / Strategies

### 4.1 Comparison Matrix

| Feature | HTTP Long Polling | WebSocket | SSE | WebTransport |
|---------|-----------------|-----------|-----|--------------|
| Direction | Bidirectional | Bidirectional | Server to Client only | Bidirectional |
| Connection | New per message | Persistent | Persistent | Persistent session with many independent streams |
| Protocol | HTTP/1.1 | WS/WSS | HTTP/1.1 or HTTP/2 | HTTP/3 over QUIC (`draft-ietf-webtrans-http3-16`, Internet-Draft in WG Last Call, not an RFC) |
| Auto-reconnect | Manual | Manual | Built-in (EventSource) | Manual |
| HTTP/2 support | Yes | Yes, as an extended CONNECT stream (RFC 8441); HTTP/3 via RFC 9220; only if the server advertises it | Yes (multiplexed) | No (needs HTTP/3) |
| Proxy/firewall | Works | Sometimes blocked | Works | Fails where UDP 443 is blocked; keep a WebSocket fallback |
| Browser support | Universal | Universal | Universal (no IE, but IE is dead) | Chrome 97, Firefox 114, Safari 26.4 (MDN) |
| CDN support | Yes | Limited | Limited | Limited |
| Load balancing | Standard | Requires sticky/pub-sub | Requires sticky/pub-sub | Requires a QUIC-aware L4/L7 balancer plus pub-sub |
| Message format | JSON | Binary or text | Text/UTF-8 only | Binary streams plus unreliable datagrams |

WebTransport runs each stream as its own QUIC stream, so a lost packet stalls only that stream, not the session; WebSocket over TCP stalls every message behind one lost segment. Its datagrams are never retransmitted, which suits game state or live media where a late update is worthless. The draft also permits running the capsule-based protocol of `draft-ietf-webtrans-http2` over a single HTTP/3 stream; in that mode all streams share head-of-line blocking and datagrams are retransmitted, so endpoints should prefer the native mapping. QUIC itself is covered in [UDP and QUIC](../udp_and_quic/udp_and_quic.md).

### 4.2 WebSocket Frame Structure

| Bits | Field | Description |
|------|-------|-------------|
| 1 | FIN | Last fragment of message |
| 3 | RSV1-3 | Extension bits |
| 4 | Opcode | 0=continuation, 1=text, 2=binary, 8=close, 9=ping, 10=pong |
| 1 | MASK | Client frames must be masked; server frames must not |
| 7 | Payload length | 0-125: actual length; 126: next 2 bytes; 127: next 8 bytes |
| 32 | Masking key | Present if MASK=1 |
| * | Payload | Application data |

```
header_bits  = FIN + RSV1-3 + Opcode + MASK + Payload_length
header_bytes = header_bits / 8
```

**Stated plainly.** "The whole header is two bytes, and the payload-length field is only seven bits — so two of its values are reserved as escapes that say 'the real length is in the next 2 or 8 bytes instead.'"

That escape trick is the reason a protocol carrying gigabyte messages still costs two bytes to frame a chat message. The header is variable-length, sized to the message rather than to the worst case.

| Symbol | What it is |
|--------|------------|
| FIN (1 bit) | Last fragment of a message. `0` means a continuation frame follows |
| RSV1-3 (3 bits) | Reserved for extensions such as permessage-deflate |
| Opcode (4 bits) | Frame type. 16 possible values, of which 6 are defined |
| MASK (1 bit) | Whether a masking key follows. Mandatory `1` client→server, mandatory `0` server→client |
| Payload length (7 bits) | Holds `0–125` literally; `126` and `127` are escapes, not lengths |
| Masking key (32 bits) | 4 bytes XORed over the payload. Anti-cache-poisoning, not encryption |

**Walk one example.** How the bits pack into bytes, and what each frame actually costs:

```
  Byte 1:  FIN 1 + RSV 3 + opcode 4        =  8 bits  =  1 byte
  Byte 2:  MASK 1 + payload length 7       =  8 bits  =  1 byte
                                              minimum header = 2 bytes

  Total header by direction and payload size:
                              server -> client      client -> server
    payload <= 125 bytes          2 bytes               2 + 4 =  6
    payload 126 .. 65,535         2 + 2 =  4            4 + 4 =  8
    payload > 65,535              2 + 8 = 10           10 + 4 = 14

  What each escape value reaches:
    literal        0 .. 125
    escape 126     up to 2^16 - 1  =              65,535 bytes
    escape 127     up to 2^64 - 1  =  18,446,744,073,709,551,615
```

A 20-byte JSON message from a browser therefore costs `20 + 6 = 26` bytes on the wire — about
23% overhead, and *constant* rather than per-request. That is the number to compare against
long polling, where every message re-sends a full set of HTTP request and response headers.
The 7-bit field is also why the framing table lists 125 rather than 127 as the literal maximum:
the top two values were spent on the escapes, so the literal range stops two short of the
`2^7 − 1 = 127` the field could otherwise express.

**Why the masking key exists at all.** It is required on every client frame and forbidden on
every server frame, which looks arbitrary until you consider the threat: a malicious script
could otherwise craft a WebSocket payload that a transparent HTTP proxy misreads as a fresh
HTTP request and caches. XORing with a fresh random 4-byte key per frame makes the bytes on the
wire unpredictable, so no attacker-chosen sequence survives to the proxy. Servers do not mask
because the attack has no reverse direction — and the 4 bytes it would cost per frame are pure
waste on the high-volume side of a fan-out.

### 4.3 WebSocket over HTTP/2 and HTTP/3 (RFC 8441, RFC 9220)

The `Upgrade` header and the `101` status do not exist in HTTP/2 or HTTP/3, so the HTTP/1.1 handshake cannot run there. RFC 8441 (September 2018) instead opens a WebSocket as one HTTP/2 stream, using an extended `CONNECT`; RFC 9220 (June 2022) does the same for HTTP/3 over QUIC.

| Step | HTTP/1.1 (RFC 6455) | HTTP/2 (RFC 8441) / HTTP/3 (RFC 9220) |
|------|---------------------|----------------------------------------|
| Server opt-in | none | Server sends `SETTINGS_ENABLE_CONNECT_PROTOCOL = 1` (setting id `0x8` in both) |
| Request | `GET` + `Upgrade: websocket` + `Sec-WebSocket-Key` | `:method = CONNECT`, `:protocol = websocket`, `:scheme`, `:path`, `:authority` |
| Success | `101 Switching Protocols` + `Sec-WebSocket-Accept` | `200` on that stream; no key and no accept hash |
| What carries the frames | the whole TCP connection | one stream; DATA frames carry RFC 6455 frames unchanged |
| Close | close frame, then TCP FIN | close frame, then `END_STREAM` on that stream only |

The RFC 6455 frame format, masking included, stays the same inside the stream. Three operational effects follow:

- **Multiplexing.** Many WebSockets, plus ordinary requests, share one TCP (or QUIC) connection and one TLS handshake. A browser tab with ten sockets to one origin opens one connection, not ten. Over HTTP/2 a lost TCP segment still stalls every stream on the connection; over HTTP/3 only the stream that lost the packet stalls.
- **Fallback is silent.** A browser uses extended CONNECT only after it sees the setting on an existing HTTP/2 connection. A server or proxy that does not send it gets a separate HTTP/1.1 connection with the classic `Upgrade`. nginx does not implement RFC 8441, so every WebSocket through nginx runs on its own HTTP/1.1 connection even when the page loads over HTTP/2.
- **Proxies translate.** Envoy accepts RFC 8441 when `allow_connect: true` is set in `http2_protocol_options` (`allow_extended_connect` in `http3_protocol_options`, marked work-in-progress in the Envoy API), and turns the stream back into an HTTP/1.1 `GET` + `Upgrade` toward the upstream. HAProxy accepts it from 2.4. The backend app therefore usually still sees an HTTP/1.1 upgrade, and a stream-level timeout on the proxy (Envoy `stream_idle_timeout`, 5 minutes by default) now applies to a long-lived socket.

Load-balancing changes too. With HTTP/1.1 one socket is one TCP connection, so an L4 balancer spreads sockets. With extended CONNECT many sockets ride one connection, so an L4 balancer pins all of them to one backend; only an L7 proxy that terminates HTTP/2 can spread them stream by stream.

### 4.4 STOMP over WebSocket

STOMP (Simple Text Oriented Messaging Protocol) adds a pub/sub messaging layer over WebSocket. Spring's Spring WebSocket + SockJS stack supports STOMP:

- Client subscribes to destinations: `/topic/room.123`
- Server sends to destinations: `/topic/room.123`
- Broker relay (RabbitMQ STOMP plugin or ActiveMQ) handles routing at scale

---

## 5. Architecture Diagrams

### WebSocket Upgrade Handshake

```mermaid
sequenceDiagram
    participant C as Client
    participant S as Server

    C->>S: GET /ws HTTP/1.1<br/>Upgrade: websocket<br/>Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
    S-->>C: 101 Switching Protocols<br/>Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
    Note over C,S: TCP connection reused as a WebSocket frame channel

    C->>S: text frame: "hello"
    S-->>C: text frame: "world"
    C->>S: binary frame (bytes)
    C->>S: ping frame (keepalive probe)
    S-->>C: pong frame
    C->>S: close frame (1000 Normal)
    S-->>C: close frame (1000 Normal, echoed)
    Note over C,S: TCP connection closed
```

One 101 response upgrades the same TCP connection into a persistent, full-duplex frame channel — every line after it is a WebSocket frame, not a new HTTP request.

The `Sec-WebSocket-Accept` is computed as:
SHA1(Sec-WebSocket-Key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"), Base64-encoded.

### WebSocket over HTTP/2 Extended CONNECT

```mermaid
sequenceDiagram
    participant B as Browser
    participant E as Envoy (HTTP/2 front)
    participant A as App (HTTP/1.1)

    B->>E: HTTP/2 connection preface
    E-->>B: SETTINGS ENABLE_CONNECT_PROTOCOL=1
    B->>E: stream 5 HEADERS<br/>:method=CONNECT :protocol=websocket<br/>:path=/ws
    E->>A: GET /ws HTTP/1.1<br/>Upgrade: websocket
    A-->>E: 101 Switching Protocols
    E-->>B: stream 5 HEADERS :status=200
    Note over B,E: RFC 6455 frames ride in DATA frames on stream 5
    B->>E: stream 7 GET /api/profile (same TCP connection)
    B->>E: stream 5 DATA (close frame) + END_STREAM
```

The socket is one stream among many on the browser connection, and the proxy converts it to a classic HTTP/1.1 upgrade on the hop to the app. Without the SETTINGS line, the browser opens a new HTTP/1.1 connection instead.

### SSE Event Stream

```mermaid
sequenceDiagram
    participant C as Client (EventSource)
    participant S as Server

    C->>S: GET /events<br/>Accept: text/event-stream
    S-->>C: 200 OK<br/>Content-Type: text/event-stream<br/>X-Accel-Buffering: no
    Note over S: nginx buffering disabled, events flush immediately

    S-->>C: id: 1 · event: user-joined<br/>data: userId 123, name Alice
    S-->>C: id: 2 · data: type ping (no event name)
    S-->>C: comment line starting with a bare colon (keepalive, ignored by client)
    S-->>C: retry: 5000 (reconnect interval, ms)

    Note over C,S: connection drops
    C->>S: GET /events<br/>Last-Event-ID: 2
    Note over S: resumes the stream after event 2
    S-->>C: id: 3 · event: ...
```

Each `id:`/`event:`/`data:` triplet is one SSE message terminated by a blank line; on reconnect the browser automatically resends `Last-Event-ID`, so the server only has to replay what the client missed.

### Scaling WebSocket with Redis Pub/Sub

```mermaid
flowchart TD
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    subgraph without["Without shared state"]
        direction LR
        alice1(["Alice sends<br/>to Room 1"]) --> srv1a["Server 1<br/>hosts Alice, Bob"]
        srv1a -.->|"no visibility"| srv2a{"Server 2 unaware<br/>of Room 1"}
        srv2a --> lost(["Charlie, Dave<br/>never see it"])
    end

    subgraph withredis["With Redis Pub/Sub"]
        direction LR
        alice2(["Alice sends<br/>to Room 1"]) --> srv1b["Server 1"]
        srv1b -->|"PUBLISH room:1"| redis@{ icon: "logos:redis", form: "square", label: "Redis", pos: "b", h: 44 }
        redis -->|"broadcast"| srv2b["Server 2<br/>subscribed to room:1"]
        srv2b --> delivered(["Charlie, Dave<br/>receive it"])
    end

    without -.->|"fix"| withredis

    class alice1,alice2 io
    class srv1a,srv1b,srv2b mathOp
    class srv2a,lost lossN
    class delivered train
```

Without a shared channel, each server only knows about the sessions it holds locally, so Room 1's message silently stops at Server 1; publishing it to a Redis channel that every server subscribes to lets any server deliver to any connected client, regardless of which process the message originated on.

---

## 6. How It Works — Detailed Mechanics

### 6.1 Spring WebSocket Server (STOMP)

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // In-memory simple broker for single-server deployments
        config.enableSimpleBroker("/topic", "/queue");

        // For multi-server: relay to external STOMP broker (RabbitMQ)
        // config.enableStompBrokerRelay("/topic", "/queue")
        //     .setRelayHost("rabbitmq")
        //     .setRelayPort(61613)
        //     .setClientLogin("user")
        //     .setClientPasscode("password");

        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOrigins("https://app.example.com")
            .withSockJS();  // SockJS fallback for non-WS environments
    }
}

@Controller
public class ChatController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.send")
    @SendTo("/topic/room.{roomId}")
    public ChatMessage send(@DestinationVariable String roomId,
                            ChatMessage message,
                            Principal principal) {
        // Principal from WebSocket auth (set in handshake interceptor)
        message.setSender(principal.getName());
        return message;
    }

    // Server-initiated push (from any @Service)
    public void notifyRoom(String roomId, String event) {
        messagingTemplate.convertAndSend(
            "/topic/room." + roomId,
            event
        );
    }
}
```

### 6.2 Spring SSE Implementation

```java
@RestController
public class NotificationController {

    private final Map<String, SseEmitter> emitters =
        new ConcurrentHashMap<>();

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(
            @RequestParam String userId,
            HttpServletResponse response) {

        // Disable buffering at nginx level
        response.setHeader("X-Accel-Buffering", "no");

        // Create emitter with 30-minute timeout
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        emitters.put(userId, emitter);

        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> {
            emitters.remove(userId);
            emitter.complete();
        });
        emitter.onError(e -> emitters.remove(userId));

        // Send initial event
        try {
            emitter.send(SseEmitter.event()
                .id("0")
                .name("connected")
                .data("{\"userId\":\"" + userId + "\"}")
                .reconnectTime(5000));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }

    // Called when notification needs to be pushed
    public void sendNotification(String userId, Notification notification) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                    .id(notification.getId())
                    .name("notification")
                    .data(objectMapper.writeValueAsString(notification)));
            } catch (IOException e) {
                emitters.remove(userId);
            }
        }
    }
}
```

### 6.3 WebSocket Authentication

```java
// WebSocket sessions are established once — authenticate during handshake
@Component
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        // Extract JWT from query param or header
        String token = extractToken(request);
        if (token == null || !jwtService.validate(token)) {
            return false;  // reject handshake
        }
        // Store user info in session attributes
        attributes.put("userId", jwtService.extractUserId(token));
        return true;
    }
}

// In STOMP, also handle CONNECT frame authentication
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
            MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = accessor.getFirstNativeHeader("Authorization");
            // Validate token, set principal
            accessor.setUser(jwtService.extractPrincipal(token));
        }
        return message;
    }
}
```

### 6.4 Heartbeat/Ping-Pong

WebSocket keeps connections alive with ping-pong frames:
- Client or server sends a Ping frame (opcode 9, arbitrary payload up to 125 bytes)
- Recipient must respond with a Pong frame (opcode 10, same payload) as soon as possible
- If no Pong received within timeout, the sender should close the connection

```mermaid
stateDiagram-v2
    [*] --> Open
    Open --> AwaitingPong: send Ping (opcode 9)
    AwaitingPong --> Open: Pong received (opcode 10, same payload)
    AwaitingPong --> Closed: no Pong before timeout
    Open --> Closed: Close frame (1000 Normal)
    Closed --> [*]
```

The only way out of `AwaitingPong` without a matching Pong is a timeout that forces the connection closed — this is the mechanism that turns silent, half-dead TCP connections into detectable failures instead of leaked phantom sessions (see Common Pitfalls).

Spring configures STOMP heartbeats (not WebSocket ping-pong, but application-level heartbeats in the STOMP protocol):
```java
registry.enableSimpleBroker("/topic").setHeartbeatValue(new long[]{10000, 10000});
// [server sends heartbeat every 10s, wants client heartbeat every 10s]
```

### 6.5 permessage-deflate Memory per Connection

permessage-deflate (RFC 7692) compresses each message with DEFLATE and sets the RSV1 bit on the first frame. With context takeover (the default), each side keeps its LZ77 window between messages, so repeated JSON keys compress well. That window and the zlib state are per connection and live for the life of the socket.

zlib documents its own memory use (`zconf.h`): deflate needs `(1 << (windowBits + 2)) + (1 << (memLevel + 9))` bytes, inflate needs `1 << windowBits` plus about 7 KB.

```
  defaults: windowBits = 15, memLevel = 8
    deflate  = 2^17 + 2^17          = 262,144 B  ~ 256 KB
    inflate  = 2^15 + ~7 KB         ~  39 KB
    per socket, both directions     ~ 295 KB

  x 1,000,000 sockets               ~ 295 GB of RAM for compression state alone

  tuned: server_max_window_bits = 10, client_max_window_bits = 10, memLevel = 1
    deflate  = 2^12 + 2^10          =   5 KB
    inflate  = 2^10 + ~7 KB         ~   8 KB
    per socket                      ~  13 KB  ->  ~13 GB at 1M sockets
```

The levers, in the order they are usually pulled:

1. Negotiate `server_no_context_takeover`. The server resets its compressor after every message and can release or pool the deflate state between messages. Compression ratio drops on small, repetitive messages.
2. Shrink `server_max_window_bits` / `client_max_window_bits` toward 8 to 10, and lower `memLevel`.
3. Skip compression for small messages. The Node `ws` library compresses only messages over `threshold` (1024 bytes by default) and caps parallel zlib work with `concurrencyLimit` (10 by default).
4. Turn it off. The `ws` README disables the extension on the server by default, citing significant CPU and memory overhead and memory fragmentation under high concurrency in Node.js. Compress large payloads at the application layer instead.

### 6.6 Receive Backpressure and WebSocketStream

The browser `WebSocket` API has no receive backpressure. Every incoming message fires a `message` event whether or not the page has handled the last one, so a fast server can fill tab memory. On the send side the only signal is `bufferedAmount`, which the code must poll. TCP flow control does not help: the browser drains the socket into its event queue as fast as packets arrive.

`WebSocketStream` exposes the socket as a `ReadableStream` and a `WritableStream`. The browser reads from the network only when the page calls `reader.read()`, so a slow consumer fills the TCP receive window and the server's send buffer, and the server sees backpressure. `await writer.write()` resolves only when the data is queued, so the sender slows down with no polling. It is Chromium-only and non-standard (MDN, standardisation tracked in `whatwg/websockets` PR 48), so cross-browser code still needs application-level flow control: credits, acknowledgements, or a server-side per-socket send queue with a byte cap that drops or disconnects slow consumers.

---

## 7. Real-World Examples

**Slack**: Uses WebSocket for real-time message delivery. Messages are sent server-to-client via WebSocket; the client sends messages via REST POST (not WebSocket). This hybrid approach simplifies message delivery guarantees (REST POST is acknowledged; WebSocket is best-effort). On reconnect, Slack fetches missed messages via REST.

**Twitter/X live timeline**: Uses SSE for the home timeline. New tweets are pushed as SSE events. The client only receives; the client sends new tweets via REST POST. SSE is ideal because it is unidirectional (server pushes tweets to client) and HTTP/2 multiplexes it efficiently.

**Google Docs real-time collaboration**: Uses WebSocket for operational transforms — bidirectional is required because each user's changes must be sent to the server and server must push others' changes. The bidirectional nature is inherent to collaborative editing.

**Trading platforms**: Use WebSocket for order entry and market data. Both directions are needed: clients submit orders, server pushes execution confirmations and price ticks. Latency is critical — the persistent connection avoids handshake overhead.

---

## 8. Tradeoffs

| Scenario | Recommendation | Reason |
|----------|---------------|--------|
| Notifications, feeds, live scores | SSE | Server-to-client only; simpler, auto-reconnect |
| Chat, collaborative editing | WebSocket | Both sides send and receive |
| Price tickers, dashboards | SSE | Server pushes data; no client sends |
| Order entry, gaming input | WebSocket | Client sends high-frequency updates |
| Simple periodic updates | Long polling | Simplest; works through all proxies |

---

## 9. When to Use / When NOT to Use

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    start(["Real-time push<br/>needed?"]) --> bidir{"Client also sends<br/>at high frequency?"}

    bidir -->|"yes: gaming, trading,<br/>collaborative editing"| loss{"Stale updates useless,<br/>or many independent streams?"}
    loss -->|"yes, and UDP 443<br/>is reachable"| wt(["WebTransport<br/>(WebSocket fallback)"])
    loss -->|"no"| ws(["WebSocket"])
    bidir -->|"no: server-to-client or<br/>occasional client sends"| proxy{"Strict corporate proxy<br/>blocks persistent streams?"}

    proxy -->|"yes"| lp(["Long polling<br/>(SockJS fallback)"])
    proxy -->|"no"| sse(["SSE<br/>(+ REST POST if client<br/>sends occasionally)"])

    class start io
    class bidir,proxy,loss mathOp
    class ws,sse,wt train
    class lp lossN
```

The decision turns on three questions: does the client need to send at high frequency, if so can it tolerate dropped or out-of-order updates (WebTransport) or not (WebSocket), and does the network path even allow a persistent connection? SSE (optionally paired with REST POST, as Twitter/X does) and long polling are both server-push-oriented fallbacks for when the answer to the first question is no.

**SSE**: Use when only the server needs to push data to the client, and the data is text/JSON. SSE works through HTTP/2 transparently (each SSE stream is one HTTP/2 stream), through most corporate proxies, and has built-in reconnection with Last-Event-ID resume.

**WebSocket**: Use when the client needs to send data at high frequency and the latency of a new HTTP request per send is unacceptable. Do not use WebSocket when SSE would suffice — WebSocket requires more infrastructure work (sticky sessions or pub/sub fan-out).

**WebTransport**: Use when the client and server both send often and either a late message is worthless (game state, live cursor positions, media) or many independent streams must not block each other. It needs HTTP/3 end to end, and the spec is still an Internet-Draft (`draft-ietf-webtrans-http3-16`, July 2026, in WG Last Call), so ship a WebSocket fallback for networks that block UDP.

**Long polling**: Use only as a last resort in environments that block WebSocket and SSE (some strict corporate proxies). The SockJS library falls back to long polling automatically.

---

## 10. Common Pitfalls

**Nginx buffering SSE responses**: Nginx buffers upstream responses by default. For SSE, this means events are held in Nginx's buffer until it fills or the response completes — clients never receive events until the stream ends (which defeats the purpose). Fix: add `proxy_buffering off;` and `X-Accel-Buffering: no` response header from the backend.

**WebSocket behind a non-sticky load balancer**: WebSocket connections are stateful. Round-robin load balancers route different connections to different backends. For WebSocket, either (1) use sticky sessions (route by session cookie/IP to the same backend), or (2) use a shared pub/sub backend (Redis Pub/Sub) so any backend can deliver messages to any connected client. A missing sticky session causes clients to lose messages or fail to connect to their session.

**Not handling WebSocket disconnects gracefully**: Clients disconnect without sending a Close frame (network failure, mobile app backgrounded, browser tab closed). The server must detect these via ping-pong timeouts or connection-level TCP keep-alive. Without detection, the server accumulates phantom sessions and leaks memory. Clean up all subscriptions and resource allocations when a session is closed.

**SSE and proxy timeout**: Enterprise HTTP proxies often close connections idle for more than 60 seconds. SSE streams may have no events for minutes. Send SSE comment lines (`: heartbeat\n\n`) every 30 seconds to keep the connection alive through intermediary proxies. The browser's EventSource ignores comments; proxies see activity and keep the connection open.

**permessage-deflate on by default at 1M connections**: With context takeover and zlib defaults, compression state costs about 295 KB per socket, which is roughly 295 GB across a million sockets before any application data. Load tests with a few thousand sockets do not show it. Negotiate `server_no_context_takeover` and smaller window bits, or leave the extension off and compress only large payloads (see 6.5).

**WebSocket message framing for large messages**: WebSocket applications that try to send large messages (>1 MB) as a single frame can exhaust memory and cause buffering issues. Fragment large messages into multiple frames or redesign the protocol to send smaller chunks. Use Binary frames for non-text data — UTF-8 validation overhead on Text frames is significant for large payloads.

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| Spring WebSocket | Spring Boot WebSocket + STOMP support |
| socket.io | Node.js WebSocket with fallbacks |
| `wscat` | Command-line WebSocket client |
| Wireshark | WebSocket frame inspection |
| `curl -N --no-buffer` | Basic SSE testing from CLI |
| SockJS | WebSocket with long-polling fallback |
| Redis Pub/Sub | Shared state for multi-server WebSocket |
| RabbitMQ STOMP plugin | STOMP broker relay for production |
| Netty | High-performance WebSocket server (underlies Spring WebFlux) |
| Envoy (`allow_connect`) | Terminates WebSocket over HTTP/2 (RFC 8441) and converts it to an HTTP/1.1 upgrade upstream |
| `WebSocketStream` | Chromium-only streams API with read and write backpressure |
| WebSocket King | Browser extension for WebSocket testing |

---

## 12. Interview Questions with Answers

**Q: How does the WebSocket upgrade handshake work?**
**Short:** The client sends a Sec-WebSocket-Key in an HTTP Upgrade request, and the server proves support with a computed Accept value.

The client sends an HTTP/1.1 GET request with headers: `Upgrade: websocket`, `Connection: Upgrade`, `Sec-WebSocket-Key: <random-base64>`, `Sec-WebSocket-Version: 13`. The server responds with `101 Switching Protocols` and `Sec-WebSocket-Accept: <SHA1(key + GUID) base64>`. The key computation proves the server understands WebSocket (not just reflecting headers). After 101, the TCP connection is reused as a WebSocket channel.

**Q: What is the difference between WebSocket and SSE?**
**Short:** WebSocket is bidirectional; SSE is server-to-client only but reconnects automatically over plain HTTP.

WebSocket is bidirectional: both client and server can send frames at any time. SSE is unidirectional: only the server can push events. SSE uses standard HTTP; browsers handle reconnection automatically via the EventSource API and Last-Event-ID. WebSocket requires manual reconnection logic. SSE works with HTTP/2 multiplexing with no server opt-in; WebSocket shares an HTTP/2 or HTTP/3 connection only when the server advertises extended CONNECT (RFC 8441, RFC 9220), otherwise it gets its own HTTP/1.1 connection. Use SSE for server-push-only scenarios; WebSocket for full-duplex communication.

**Q: How do you scale WebSocket connections horizontally?**
**Short:** Scale WebSocket servers with sticky sessions or, more resiliently, a shared Redis Pub/Sub fan-out backend.

WebSocket connections are stateful — a client is connected to a specific server. Horizontal scaling requires either: (1) sticky sessions — load balancer routes all requests from a client to the same server (by IP or cookie); (2) shared pub/sub backend — each server publishes messages to Redis Pub/Sub, all servers subscribe, and each delivers to its locally connected clients. Option 2 is more resilient to server failure. Sticky sessions can cause imbalanced load if some connections are more active.

**Q: Your site serves over HTTP/2, yet every WebSocket still opens its own TCP connection. Why?**
**Short:** The server or proxy never advertised SETTINGS_ENABLE_CONNECT_PROTOCOL, so the browser fell back to an HTTP/1.1 Upgrade on a fresh connection.

HTTP/2 has no `Upgrade` header and no `101` status, so a WebSocket can share an HTTP/2 connection only through RFC 8441 extended CONNECT: `:method = CONNECT`, `:protocol = websocket`, answered by `200` on that stream. The browser uses it only after the server sends `SETTINGS_ENABLE_CONNECT_PROTOCOL = 1` on the existing connection; otherwise it silently opens a new HTTP/1.1 connection and runs the classic handshake. nginx does not implement RFC 8441, so every socket behind it gets its own connection; Envoy does when `allow_connect: true` is set, and converts the stream back to an HTTP/1.1 upgrade toward the app. RFC 9220 defines the same mechanism for HTTP/3. Check the browser network panel: the protocol column reads `h2` or `http/1.1` for the WebSocket row.

**Q: Why can permessage-deflate exhaust memory on a server holding a million WebSocket connections?**
**Short:** With context takeover and zlib defaults each socket keeps about 295 KB of compression state, so a million sockets need about 295 GB.

permessage-deflate (RFC 7692) keeps a DEFLATE context per connection so later messages can reference earlier ones. zlib's documented cost is `2^(windowBits+2) + 2^(memLevel+9)` bytes for deflate and `2^windowBits` plus about 7 KB for inflate: 256 KB plus 39 KB at the defaults of 15 and 8. That state lives as long as the socket, idle or not. Fixes: negotiate `server_no_context_takeover` so the server can free or pool its compressor between messages, cut window bits to 10 (about 13 KB per socket), skip messages under about 1 KB, or leave the extension off. The Node `ws` library ships it disabled on the server for this reason.

**Q: What is the WebSocket ping-pong mechanism?**
**Short:** WebSocket Ping and Pong control frames act as a keep-alive; a missing Pong marks the connection dead.

Ping and Pong are WebSocket control frames (opcodes 9 and 10). Either party can send a Ping with up to 125 bytes of payload. The recipient must respond with a Pong containing the same payload as soon as possible. This serves as a keep-alive: if no Pong is received within a timeout, the connection is considered dead and closed. Spring's STOMP heartbeat uses STOMP-level heartbeats (not WS ping-pong) for higher-level keep-alive.

**Q: How does SSE automatic reconnection work?**
**Short:** EventSource reconnects automatically and resends a Last-Event-ID header so the server can resume from that point.

The browser's EventSource API automatically reconnects when the connection closes. After reconnection, it sends the `Last-Event-ID: <id>` header with the id of the last received event. The server should use this to resume delivery from that point, preventing missed events. If the SSE stream includes a `retry: <ms>` field, the browser uses that interval for reconnection (default is a few seconds). This makes SSE inherently more reliable than WebSocket, which requires manual reconnection logic.

**Q: What is STOMP and why is it used over WebSocket?**
**Short:** STOMP adds SEND, SUBSCRIBE, and ACK semantics on top of WebSocket's raw bidirectional channel.

STOMP (Simple Text Oriented Messaging Protocol) is a simple message protocol with SEND, SUBSCRIBE, UNSUBSCRIBE, and ACK semantics. By itself, WebSocket provides only a raw bidirectional channel — you must build message routing, pub/sub, and acknowledgment on top. STOMP adds this structure. Spring's SockJS/STOMP integration provides a complete pub/sub system over WebSocket, with message broker support (in-memory or external RabbitMQ/ActiveMQ).

**Q: How do you authenticate WebSocket connections?**
**Short:** Pass an auth token as a query parameter or cookie during the handshake, since custom headers aren't supported.

WebSocket connections cannot send custom headers in the initial browser WebSocket API (the JavaScript API does not support custom headers on the Upgrade request). Solutions: (1) send token as a URL query parameter (e.g., `wss://api.example.com/ws?token=xxx`) — validate in the server's handshake interceptor; (2) use a cookie for authentication (HTTP cookies are sent with the Upgrade request); (3) for STOMP, authenticate in the CONNECT frame (send token as a STOMP header) — the server validates before allowing subscriptions. Option 1 is most common despite token visibility in logs (use HTTPS/WSS to prevent interception).

**Q: What happens to a WebSocket connection when the server restarts?**
**Short:** A server restart closes every WebSocket via RST or a Close frame, requiring client-side backoff reconnection.

A server restart closes all TCP connections (sends RST or FIN). The client receives a WebSocket Close frame or a TCP error. Without reconnection logic, the client's connection dies. The client must implement exponential backoff reconnection: retry after 1s, 2s, 4s, 8s, ... with a maximum interval. After reconnecting, the client must re-subscribe to channels/rooms. This is why SSE is often preferred — EventSource reconnection is automatic and built-in.

**Q: How do you prevent WebSocket connections from being used for DDoS?**
**Short:** Rate limit upgrade requests and messages per connection, and reject unauthenticated handshakes immediately.

Rate limit at the load balancer or API gateway: max WebSocket upgrade requests per IP per second. Rate limit at the application layer: max messages per second per connection. Validate authentication during the handshake — unauthenticated connections should be rejected immediately, not after messages arrive. Implement message size limits. Use connection-level rate limiters to disconnect clients sending too many messages. Monitor connection counts per IP.

**Q: What is SockJS and when do you need it?**
**Short:** SockJS falls back to long polling or XHR streaming when a corporate proxy blocks raw WebSocket connections.

SockJS is a JavaScript library that provides a WebSocket-like API but falls back to long polling, XHR streaming, or other transports when WebSocket is blocked by corporate proxies or firewalls. Spring's SockJS integration automatically handles the negotiation. Use SockJS when supporting corporate network environments that block WebSocket. For modern consumer applications where WebSocket is universally available, SockJS adds unnecessary complexity.

**Q: How does Spring SseEmitter work and what are its lifecycle methods?**
**Short:** SseEmitter stays open until complete(), completeWithError(), or its timeout fires, each with its own callback.

SseEmitter wraps a Server-Sent Events response. You return it from a controller method. The emitter stays open until: `emitter.complete()` is called (graceful end), `emitter.completeWithError(Exception)` is called (error), or the timeout expires (configured in the constructor). Lifecycle callbacks: `onCompletion()`, `onTimeout()`, `onError()` — use these to clean up references in registries. Sending events via `emitter.send(event)` is not thread-safe for concurrent sends — synchronize or use a single-thread sender.

**Q: How would you design a live collaboration system using WebSocket?**
**Short:** Route edits through STOMP topics per document, resolve conflicts with CRDTs, and fan out via Redis Pub/Sub.

Schema: WebSocket connection per document, authenticated during handshake. Client joins via STOMP SUBSCRIBE `/topic/doc.{docId}`. Edits sent to STOMP `/app/doc.{docId}.edit`. Server applies Operational Transforms (OT) or CRDTs, broadcasts transformed edit to `/topic/doc.{docId}`. Scaling: Redis Pub/Sub channel per document; any server instance can handle edits from any user. For conflict resolution: use CRDTs (e.g., Yjs, Automerge) client-side with eventual consistency rather than OT server-side. State recovery: when a user reconnects, fetch full document state via REST, then subscribe to WebSocket for incremental updates.

**Q: What is the Last-Event-ID in SSE and how does it enable reliability?**
**Short:** The server replays events after the client's Last-Event-ID, so a reconnecting client misses nothing it hasn't seen.

Last-Event-ID is a header sent by the EventSource on reconnection containing the `id` field of the last event the client received. The server stores the current event ID and, on reconnection, can replay all events after the provided ID. This requires the server to buffer recent events (in memory or a message queue) for replay. Without this, events sent while the client was disconnected are lost. For high-reliability systems, buffer events for at least the maximum expected reconnection time (commonly 60–120 seconds).

**Q: Why must WebSocket frames from client to server be masked, while server-to-client frames must not be?**
**Short:** Client-to-server frames are masked with a random per-frame XOR key to stop proxy cache-poisoning attacks.

Masking exists to prevent cache-poisoning and request-smuggling attacks against proxies and CDNs that sit between the browser and the server, not to provide any real confidentiality. Without masking, an attacker could craft WebSocket frame payloads that, byte-for-byte, resemble a valid HTTP request, and a misbehaving intermediary caching or forwarding raw bytes might interpret them as a second smuggled request. The RFC 6455 masking key XORs every client-to-server payload byte with a random per-frame key, so the bytes on the wire never form a predictable, attacker-chosen pattern that a proxy could be tricked into parsing as HTTP. Server-to-client frames are not masked because the server is trusted infrastructure, and only the browser-originated direction faces the specific threat masking defends against.

**Q: Why must nginx response buffering be disabled for SSE, and how do you do it?**
**Short:** Set proxy_buffering off and send X-Accel-Buffering: no, or nginx holds SSE events until its buffer fills.

Nginx buffers upstream responses by default, holding SSE events until the buffer fills or the response completes. This defeats the purpose of a live event stream, since the client receives nothing until the buffer flushes, sometimes minutes after the events were actually generated. Disable it by setting `proxy_buffering off;` in the nginx location block and by sending an `X-Accel-Buffering: no` response header from the backend, both of which tell nginx to forward each chunk to the client immediately as it is written. Always verify SSE endpoints behind nginx with a manual curl test, since buffering bugs are invisible in local development without a reverse proxy in front.

**Q: How do you stop a fast WebSocket server from overwhelming a slow browser client?**
**Short:** The classic WebSocket API cannot push back on receive, so use WebSocketStream in Chromium or add credit-based flow control in the protocol.

The `WebSocket` API fires a `message` event for every arrival and drains the TCP socket regardless of how fast the page handles events, so TCP flow control never reaches the server and the tab's memory grows. Send-side throttling exists only as polling `bufferedAmount`. `WebSocketStream` reads from the network only when the page calls `reader.read()`, so a slow reader fills the TCP window and the server's send buffer blocks; `await writer.write()` gives send backpressure. It is Chromium-only and not yet in a standard, so portable code grants credits (client acks every N messages, server stops at zero credit) and caps each socket's server-side send queue in bytes, dropping or disconnecting a consumer that exceeds it.

**Q: How do you safely handle large WebSocket messages (over 1MB)?**
**Short:** Split a large payload across continuation frames, with FIN=0 on all but the final chunk.

Fragment the message into multiple WebSocket frames using the continuation opcode, rather than sending the entire payload as one giant frame that must be buffered whole in memory on both ends. The first frame carries the real opcode (text or binary) with FIN=0, each subsequent chunk is sent as a continuation frame (opcode 0) with FIN=0, and the final chunk sets FIN=1 to signal the message is complete. Prefer binary frames over text frames for large non-text payloads, since text frames require UTF-8 validation on the full payload, and that validation cost becomes significant at multi-megabyte sizes. An application-level alternative is to redesign the protocol to avoid large single messages entirely, such as streaming a file in smaller application-defined chunks with their own sequence numbers.

---

## 13. Best Practices

- Use SSE for server-to-client push and WebSocket only for bidirectional real-time communication.
- Always disable nginx buffering for SSE: `proxy_buffering off` and `X-Accel-Buffering: no`.
- Send SSE comment heartbeats every 25–30 seconds to keep connections alive through proxies.
- Implement Redis Pub/Sub fan-out for WebSocket at scale — avoid sticky sessions as the only strategy.
- Authenticate during WebSocket handshake (query param token or cookie), not after the upgrade.
- Implement WebSocket reconnection with exponential backoff and jitter.
- Clean up session state immediately on WebSocket close or timeout — memory leaks from phantom sessions are common.
- Use STOMP over WebSocket for application-level pub/sub semantics; do not reinvent messaging on raw WebSocket.

---

## 14. Case Study

**Problem**: A live trading platform serving 50,000 concurrent traders was running a single WebSocket server process. WebSocket connections were stateful per process, and horizontal scaling was impossible without breaking sessions. A deployment required a 30-second maintenance window that disconnected all traders simultaneously — unacceptable.

**Architecture before**:
```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    traders(["50,000 traders"]) --> srv["Single WebSocket Server"]
    srv --> sessions["In-memory<br/>session map"]

    class traders io
    class srv lossN
    class sessions base
```

Every trader's session lives only in this one process's memory, so there is no way to add a second server or restart this one without dropping all 50,000 connections at once — the 30-second maintenance window described above.

**Architecture after (Redis Pub/Sub fan-out)**:
```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    lb(["Load Balancer<br/>(sticky)"]) --> s1["WebSocket Server 1<br/>10k connections"]
    lb --> s2["WebSocket Server 2<br/>10k connections"]
    lb --> s3["WebSocket Server 3<br/>10k connections"]

    feed(["Price Feed Service"]) -->|"PUBLISH AAPL:151.23:151.24"| redis@{ icon: "logos:redis", form: "square", label: "Redis", pos: "b", h: 44 }
    redis -.->|"broadcast"| s1
    redis -.->|"broadcast"| s2
    redis -.->|"broadcast"| s3

    class lb,feed io
    class s1,s2,s3 train
```

Sticky routing only decides which of the 3 servers a trader's connection lands on; delivery itself goes through Redis, so any server can publish a price tick and every server — and therefore every subscribed trader — receives it.

**Zero-downtime deployment**:
1. Start new server with the same load balancer entry.
2. Load balancer routes new connections to new server (connection-level sticky).
3. Drain old server: stop accepting new connections, wait for existing connections to timeout or disconnect naturally.
4. Shut down old server after 30 minutes of draining.

**Results**: Zero-downtime deployments, horizontal scaling to 200k concurrent connections across 20 servers, 99.99% message delivery rate. Redis Pub/Sub added ~1ms latency per message (acceptable for this use case).
