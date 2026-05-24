# MCP Transports and JSON-RPC — Deep Dive

---

## 1. Concept Overview

MCP uses JSON-RPC 2.0 as its message format over two primary transports: **stdio** (client spawns server as a subprocess, communicates via standard input/output streams) and **Streamable HTTP** (HTTP service receiving POST requests and emitting Server-Sent Events). A deprecated SSE transport exists for legacy compatibility.

This deep-dive covers the wire format (JSON-RPC 2.0 requests, responses, notifications, batching), transport selection trade-offs (stdio vs HTTP), the new Streamable HTTP protocol (2025 spec, replaces SSE-only transport), connection lifecycle (handshake, ping/pong, graceful shutdown), reconnection semantics, and concrete latency numbers for each.

---

## 2. Intuition

**One-line analogy**: MCP transports are like the difference between a Unix pipe (stdio: parent-child, intimate, fast) and an HTTP API (Streamable HTTP: anyone-anywhere, scalable, network-aware).

**Mental model**: JSON-RPC is the language; transports are the delivery mechanism. Every MCP message is `{"jsonrpc": "2.0", "id": 1, "method": "tools/call", "params": {...}}` (request) or `{"jsonrpc": "2.0", "id": 1, "result": {...}}` (response) or `{"jsonrpc": "2.0", "method": "notifications/...", "params": {...}}` (notification — no id, no response expected). Transport adds framing (how to know where one message ends and the next begins).

**Why it matters**: Transport choice affects latency, security, scalability, and deployment topology. Stdio is fast (~1-2ms message round-trip) but local-only. Streamable HTTP supports remote servers (10-50ms RTT) and multi-client serving. Get the transport choice wrong, and you'll fight infrastructure constraints.

**Key insight**: The 2025 protocol revision replaced the original SSE-only HTTP transport with Streamable HTTP — a single endpoint that handles both stateless single-request and stateful streaming sessions. Massively simpler to deploy than the previous "two endpoints, one for events, one for requests" SSE design.

---

## 3. Core Principles

- **JSON-RPC 2.0 contract**: id-based correlation, error codes, batch support.
- **Three message types**: request (id, expects response), response (echoes id), notification (no id, no response).
- **Stdio framing**: newline-delimited JSON-RPC over stdin/stdout.
- **HTTP framing**: POST per request; response may be JSON or SSE stream.
- **Bidirectional**: server can send requests too (e.g., `sampling/createMessage`).
- **Ordered delivery**: messages over a single connection are processed in order.
- **Lifecycle hygiene**: initialize → operate → shutdown; ping for keepalive.

---

## 4. Types / Architectures / Strategies

### 4.1 Stdio Transport

Client spawns server as subprocess. Server reads requests from stdin (newline-delimited JSON-RPC), writes responses to stdout. Use for local tools (filesystem, local DB, single-user CLI).

### 4.2 Streamable HTTP Transport (2025)

Server is an HTTP service with a single MCP endpoint. Client POSTs requests; server responds with either standard JSON (one-shot) or `text/event-stream` (SSE) for streaming responses. Supports stateful sessions via `Mcp-Session-Id` header.

### 4.3 Legacy SSE Transport (Deprecated)

Older spec: server exposed two endpoints — `/sse` (GET, server→client events) and `/messages` (POST, client→server). Replaced by Streamable HTTP in 2025 spec. Some older servers still use it.

### 4.4 Custom Transports

In theory, any reliable bidirectional message channel works. Some use cases: WebSocket transports, Unix domain sockets, named pipes. Not standardized.

---

## 5. Architecture Diagrams

```
JSON-RPC Message Types
=======================

Request:
  {
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {"name": "create_issue", "arguments": {...}}
  }

Response (success):
  {
    "jsonrpc": "2.0",
    "id": 1,
    "result": {"content": [{"type": "text", "text": "..."}]}
  }

Response (error):
  {
    "jsonrpc": "2.0",
    "id": 1,
    "error": {"code": -32602, "message": "Invalid params"}
  }

Notification (no id, no response):
  {
    "jsonrpc": "2.0",
    "method": "notifications/tools/list_changed",
    "params": {}
  }


Stdio Transport Framing
========================

  Client stdin -> Server: 
    {"jsonrpc":"2.0","id":1,"method":"initialize",...}\n
    {"jsonrpc":"2.0","method":"notifications/initialized"}\n
    {"jsonrpc":"2.0","id":2,"method":"tools/list"}\n
  
  Server stdout -> Client:
    {"jsonrpc":"2.0","id":1,"result":{...}}\n
    {"jsonrpc":"2.0","id":2,"result":{"tools":[...]}}\n
  
  Newline-delimited; one JSON object per line


Streamable HTTP Flow
=====================

  Stateless single request:
    Client: POST /mcp
            Body: {"jsonrpc":"2.0","id":1,"method":"tools/list"}
    Server: 200 OK, Content-Type: application/json
            Body: {"jsonrpc":"2.0","id":1,"result":{...}}
  
  Stateful session (multiple requests, one stream):
    Client: POST /mcp
            Header: Mcp-Session-Id: abc123
            Body: {"jsonrpc":"2.0","id":1,"method":"...","params":{...}}
    Server: 200 OK, Content-Type: text/event-stream
            Body: data: {"jsonrpc":"2.0","id":1,"result":...}\n\n
                  data: {"jsonrpc":"2.0","method":"notifications/...","params":...}\n\n


Lifecycle Sequence
===================

  +-----------+                       +-----------+
  |  Client   |                       |  Server   |
  +-----------+                       +-----------+
      |   initialize(version, caps)        |
      |----------------------------------->|
      |                                    |
      |    initialize_result(server_caps)  |
      |<-----------------------------------|
      |                                    |
      |    notifications/initialized       |
      |----------------------------------->|
      |                                    |
      |    [normal operation]              |
      |    list_tools, call_tool, etc      |
      |<==================================>|
      |                                    |
      |    ping (keepalive, every 30s)     |
      |----------------------------------->|
      |    pong                             |
      |<-----------------------------------|
      |                                    |
      |    [shutdown]                       |
      |    close connection                |
```

---

## 6. How It Works — Detailed Mechanics

### Manual JSON-RPC over Stdio (Python)

```python
import json
import subprocess
import sys

# Spawn server subprocess
proc = subprocess.Popen(
    ["python", "my_server.py"],
    stdin=subprocess.PIPE,
    stdout=subprocess.PIPE,
    stderr=subprocess.PIPE,
    text=True,
    bufsize=1,  # Line-buffered
)


def send_message(msg: dict) -> None:
    """Send a JSON-RPC message to the server."""
    line = json.dumps(msg) + "\n"
    proc.stdin.write(line)
    proc.stdin.flush()


def read_message() -> dict:
    """Read one JSON-RPC message from the server."""
    line = proc.stdout.readline()
    if not line:
        raise ConnectionError("Server closed connection")
    return json.loads(line)


# Initialize handshake
send_message({
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
        "protocolVersion": "2025-03-26",
        "capabilities": {"sampling": {}},
        "clientInfo": {"name": "my-client", "version": "1.0.0"},
    },
})
init_response = read_message()
print("Server capabilities:", init_response["result"]["capabilities"])

# Send initialized notification (no id, no response expected)
send_message({"jsonrpc": "2.0", "method": "notifications/initialized"})

# Now we can call tools
send_message({
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/list",
})
tools_response = read_message()
for tool in tools_response["result"]["tools"]:
    print(f"  {tool['name']}: {tool['description']}")
```

### Streamable HTTP Client (Manual)

```python
import httpx
import json

class StreamableHTTPClient:
    def __init__(self, url: str):
        self.url = url
        self.session_id: str | None = None
        self.next_id = 1
        self.http = httpx.AsyncClient(timeout=60)
    
    async def call(self, method: str, params: dict = None) -> dict:
        """Single JSON-RPC call."""
        message = {
            "jsonrpc": "2.0",
            "id": self.next_id,
            "method": method,
            "params": params or {},
        }
        self.next_id += 1
        
        headers = {"Content-Type": "application/json", "Accept": "application/json, text/event-stream"}
        if self.session_id:
            headers["Mcp-Session-Id"] = self.session_id
        
        response = await self.http.post(self.url, json=message, headers=headers)
        
        # Capture session ID from response (for stateful sessions)
        if "Mcp-Session-Id" in response.headers:
            self.session_id = response.headers["Mcp-Session-Id"]
        
        content_type = response.headers.get("Content-Type", "")
        if content_type.startswith("application/json"):
            # Stateless response
            return response.json()
        elif content_type.startswith("text/event-stream"):
            # SSE stream — parse events
            for line in response.iter_lines():
                if line.startswith("data: "):
                    data = json.loads(line[6:])
                    if "result" in data or "error" in data:
                        return data
        raise ValueError(f"Unexpected content type: {content_type}")
    
    async def initialize(self) -> dict:
        return await self.call("initialize", {
            "protocolVersion": "2025-03-26",
            "capabilities": {},
            "clientInfo": {"name": "manual-http-client", "version": "1.0.0"},
        })


# Usage
client = StreamableHTTPClient("https://my-mcp.example.com/mcp")
init = await client.initialize()
tools = await client.call("tools/list")
```

### Batching (JSON-RPC 2.0 Feature)

```json
// Request batch
[
  {"jsonrpc": "2.0", "id": 1, "method": "tools/list"},
  {"jsonrpc": "2.0", "id": 2, "method": "resources/list"},
  {"jsonrpc": "2.0", "id": 3, "method": "prompts/list"}
]

// Response batch (same order or by id)
[
  {"jsonrpc": "2.0", "id": 1, "result": {...}},
  {"jsonrpc": "2.0", "id": 2, "result": {...}},
  {"jsonrpc": "2.0", "id": 3, "result": {...}}
]
```

Most MCP servers support batching; reduces round-trips when listing multiple capability types at startup.

---

## 7. Real-World Examples

**Claude Desktop**: uses stdio for all servers (subprocess per server, in user's process tree).

**Smithery-hosted MCP servers**: use Streamable HTTP for remote access; users connect by URL.

**Internal enterprise MCP gateway**: HTTP-based with auth proxy; multiple stdio servers behind it.

**Cursor MCP**: stdio for local servers; HTTP for cloud-hosted (e.g., browser automation services).

---

## 8. Tradeoffs

| Transport | Latency | Security | Scalability | Best For |
|---|---|---|---|---|
| Stdio | 1-2ms | High (no network) | One client per server | Local tools, single-user |
| Streamable HTTP | 10-50ms | TLS + auth required | Many clients per server | Cloud services, shared |
| Legacy SSE | 10-50ms | TLS + auth required | Same | Compat with older servers |

---

## 9. When to Use / When NOT to Use

**Use stdio when:**
- Server runs on user's machine (filesystem, local DB)
- Single user per server instance
- Low latency required
- No network egress allowed

**Use Streamable HTTP when:**
- Server is a shared cloud service
- Multiple users/clients per server
- Need OAuth-based auth
- Server can scale horizontally

---

## 10. Common Pitfalls

### Pitfall 1: Mixing notification and request semantics

```python
# BROKEN: sent a request that should have been a notification
send_message({
    "jsonrpc": "2.0",
    "id": 1,  # Has id! But it's a notification by spec
    "method": "notifications/initialized",
    "params": {},
})
# Server waits for response forever (you didn't send one)
```

```python
# FIXED: notifications have no id
send_message({
    "jsonrpc": "2.0",
    "method": "notifications/initialized",
    "params": {},
})
# No response expected
```

### Pitfall 2: Reading stdout when server writes to stderr too

```python
# BROKEN: server log noise on stdout breaks JSON-RPC parsing
proc.stdout.readline()  # Returns "INFO: starting up..." — not valid JSON!
```

```python
# FIXED: ensure server logs to stderr ONLY
# In Python MCP server:
logging.basicConfig(stream=sys.stderr)  # NOT sys.stdout!
# Stdio servers must keep stdout pristine for JSON-RPC only
```

**War story**: A team built a custom MCP server in Go that printed startup info to stdout. Client kept getting "Invalid JSON" errors. Took hours to diagnose because the error message blamed the client. Lesson: stdio MCP servers MUST log to stderr, never stdout — stdout is JSON-RPC only.

---

## 11. Technologies & Tools

| Tool | Purpose |
|---|---|
| JSON-RPC 2.0 spec | Wire format |
| MCP spec (2025-03-26) | Protocol + transports |
| `mcp` SDK | Hides transport details |
| MCP Inspector | Debug JSON-RPC traffic |
| `httpx` / `aiohttp` | HTTP client for Streamable transport |
| Server-Sent Events (SSE) spec | Streaming over HTTP |

---

## 12. Interview Questions with Answers

**What's the difference between a request and a notification in JSON-RPC?**
Requests have an `id` field; the receiver MUST send a response with the same id. Notifications have no `id`; no response expected. The MCP `notifications/initialized` message is a notification (no id, no response). Mixing them up causes deadlocks (one party waits forever).

**Why does MCP use JSON-RPC 2.0 specifically?**
JSON-RPC 2.0 is mature (2010), simple, language-agnostic, supports requests/responses/notifications/batching. Alternatives (gRPC, custom protocols) would add weight without benefit. JSON is human-readable for debugging.

**When should you use stdio vs HTTP transport?**
Stdio for local tools (filesystem, local DB) — fastest, most secure, single-user per server. HTTP (Streamable HTTP) for shared cloud services, multi-tenant, requires network — adds 10-50ms RTT but enables scale.

**What replaced the legacy SSE-only HTTP transport?**
Streamable HTTP (2025 spec). The legacy design had two endpoints (`/sse` for events, `/messages` for requests) which was complex to deploy and limited stateless usage. Streamable HTTP uses a single endpoint that handles both stateless one-shots (JSON response) and streaming (SSE response) based on content type negotiation.

**What's the role of the `Mcp-Session-Id` header?**
For stateful sessions over Streamable HTTP. Server returns an Mcp-Session-Id on the first response; client echoes it on subsequent requests in the same session. Lets the server route requests to the right session state (e.g., conversation memory).

**How does ping/pong keepalive work?**
Either party can send a `ping` request; the other responds with an empty `pong`. Default interval: 30 seconds. Detects dead connections. Most SDK implementations handle automatically; only matters when you implement manually.

**What does the initialize handshake negotiate?**
Protocol version (both parties must agree on a compatible version), capabilities (client says "I support sampling"; server says "I have tools and resources"). After initialize, both know what the other supports.

**What's the typical latency for each transport?**
Stdio: 1-2ms per message (in-process pipe + JSON parse). Streamable HTTP local: ~5-10ms (loopback + HTTP overhead). Streamable HTTP across internet: 30-100ms (network RTT + TLS + HTTP). Stdio is essentially free latency-wise.

**How is JSON-RPC batching used in MCP?**
Send an array of requests instead of one; server responds with an array of responses. Useful at startup when you want to list tools, resources, and prompts in one round-trip. Most servers support; not all clients use it.

**What happens if the same id is used twice in JSON-RPC?**
Spec says don't do it (id should be unique per session). In practice: response for the second request may overwrite the first, or be misrouted. Use a monotonically increasing counter for ids.

**Can the server send requests to the client?**
Yes — bidirectional. The main use case is `sampling/createMessage` (server asks client to call its LLM). Notifications can also flow both ways. JSON-RPC supports this naturally; clients must be prepared to receive and handle.

**Why must stdio MCP servers log to stderr only?**
Stdout is the JSON-RPC channel; any non-JSON output breaks the parser. Stderr is for diagnostics/logs and is read separately (or not at all) by the client. All MCP SDK implementations set up logging to stderr by default; custom implementations must do the same.

**How does graceful shutdown work?**
Client sends shutdown notification or closes the connection. Server cleans up resources. For stdio: client closes stdin → server detects EOF → exits gracefully. For HTTP: client sends a DELETE on the session URL (per spec).

**What error codes does JSON-RPC define?**
-32700 Parse error, -32600 Invalid Request, -32601 Method not found, -32602 Invalid params, -32603 Internal error, -32000 to -32099 Server error (application-defined). MCP defines additional codes for protocol-specific errors.

**How do you debug JSON-RPC traffic?**
(1) MCP Inspector for interactive debugging. (2) Log all messages with timestamps in your client. (3) For stdio: intercept the pipes with a logging proxy. (4) For HTTP: standard HTTP debugging (Charles Proxy, mitmproxy, browser network tab).

---

## 13. Best Practices

1. Use the SDK's built-in transports — don't roll your own JSON-RPC unless absolutely necessary.
2. For stdio: log to stderr ONLY in your server. Stdout is sacred.
3. For HTTP: use Streamable HTTP (2025 spec), not legacy SSE.
4. Always include `Accept: application/json, text/event-stream` on Streamable HTTP requests — server may respond with either.
5. Handle bidirectional messages: server may send requests (sampling, notifications) — your client must process them.
6. Use unique, monotonic ids per session — never reuse.
7. Implement ping/pong keepalive on long-lived connections (15-30s interval).
8. For HTTP servers, use TLS + auth always — MCP servers may expose privileged tools.
9. Cap message size (1MB typical) — JSON-RPC isn't designed for huge payloads.
10. Test with MCP Inspector at every stage — catches protocol bugs early.

---

## 14. Case Study

**Internal MCP Gateway Architecture**

**Context**: A large enterprise had 40+ internal MCP servers (Snowflake, Salesforce, GitHub Enterprise, internal APIs). Wanted to centralize access through one gateway with audit logging, auth, and per-team quotas.

**Architecture**:
- Single Streamable HTTP MCP server (the "gateway") exposed to clients
- Gateway authenticates clients via OAuth (corporate SSO)
- Gateway internally connects to 40 backend MCP servers (stdio for local-host, HTTP for cloud)
- Gateway proxies `tools/list`, `resources/list` etc — aggregates and prefixes
- Tool calls routed to correct backend server based on prefix
- Gateway logs every JSON-RPC call with user, tool, latency, result size
- Per-team rate limits + budget caps enforced

**Wire-protocol benefits**:
- Stateless clients to gateway (any client instance can serve any request)
- Backend servers can use whatever transport suits them
- Gateway batches requests where possible (saving round-trips)

**Results**:
- 200+ developers using single gateway URL
- ~500K MCP calls/day; P95 latency 95ms
- Centralized audit log used for compliance reviews
- Per-server failures isolated (one bad backend doesn't break gateway)

**Lessons**:
1. Streamable HTTP at the edge + stdio internally was the right hybrid.
2. JSON-RPC batching at the gateway cut RTT by 40% on startup capability discovery.
3. Audit logs revealed which MCP tools were most used → guided investment in caching.
4. Sticky session via Mcp-Session-Id mattered for stateful operations (multi-turn tool sequences).
