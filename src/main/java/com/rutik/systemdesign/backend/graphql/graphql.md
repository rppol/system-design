# GraphQL

## 1. Concept Overview

GraphQL is a query language for APIs and a runtime for executing those queries, developed by Facebook (Meta) in 2012 and open-sourced in 2015. Unlike REST, where the server defines what data each endpoint returns, GraphQL lets clients specify exactly what fields they need. This eliminates over-fetching (getting more data than needed) and under-fetching (needing multiple requests to get enough data).

GraphQL defines a strongly-typed schema as the contract between client and server. Clients send queries declaring the shape of data they want, mutations to change data, and subscriptions for real-time updates. The server executes queries by calling field resolvers and assembles the response matching the query shape.

---

## 2. Intuition

> **One-line analogy**: REST is a set of fixed restaurant menu items — you order what's on the menu and get exactly that, even if you only wanted the garnish. GraphQL is a custom order system — you specify exactly what you want (just the title, author, and first 3 comments of a post), and the kitchen prepares exactly that.

**Mental model**: The GraphQL schema defines all available types and fields. A client query is a tree selecting specific fields from that schema. The server executes the query by traversing this tree, calling resolvers for each field. Resolvers fetch data from databases, other APIs, or caches. The result mirrors the structure of the query.

**Why it matters**: Mobile apps particularly benefit from GraphQL — they can request only the fields needed for a specific view, reducing payload size and parse time on resource-constrained devices. For complex data models with many relationships, GraphQL eliminates the N+1 round-trip problem common in REST (one request for the root resource, then N requests for related resources).

**Key insight**: GraphQL's main operational challenges are not in the query language but in the runtime: the N+1 resolver problem (each field resolver independently queries the database), preventing abuse via unbounded queries, and schema evolution across many clients.

---

## 3. Core Principles

- **Schema-first**: The type system defines all available types, fields, and operations. Clients can only query what the schema allows.
- **Client-specified queries**: Clients declare exactly the shape of data they need — not the server.
- **Strongly typed**: Every field has a type. The runtime validates queries against the schema before execution.
- **Hierarchical**: Queries mirror the shape of the response. Nested fields are resolved by nested resolvers.
- **Introspection**: Clients can query the schema itself (field names, types, descriptions) to enable tooling and documentation.
- **Single endpoint**: Typically all GraphQL operations go to POST /graphql — a departure from REST's resource-based URL design.

---

## 4. Types / Architectures / Strategies

### 4.1 Schema Types

| Type | Description | Example |
|------|-------------|---------|
| Object type | Named set of fields | `type User { id: ID!, name: String! }` |
| Scalar | Leaf values | String, Int, Float, Boolean, ID, custom |
| Enum | Named constants | `enum Status { ACTIVE, INACTIVE }` |
| Interface | Abstract type (fields must be present) | `interface Node { id: ID! }` |
| Union | One of multiple types | `union SearchResult = User \| Post` |
| Input type | Object used as argument | `input CreateUserInput { name: String! }` |
| OneOf input type | Input object where exactly one field is set, to a non-null value (September 2025 edition) | `input UserBy @oneOf { id: ID, email: String }` |
| List | Array of a type | `[User]`, `[User!]!` |
| Non-null | Field cannot be null | `String!` |

### 4.2 Operations

| Operation | Purpose | Execution |
|-----------|---------|-----------|
| Query | Fetch data (read-only) | Parallel field resolution possible |
| Mutation | Modify data | Sequential (one at a time per spec) |
| Subscription | Real-time updates | WebSocket or SSE-based |

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    subgraph QP["Query: parallel"]
        R1([Root]) --> FA["resolve field A"]
        R1 --> FB["resolve field B"]
        R1 --> FC["resolve field C"]
    end

    subgraph MS["Mutation: sequential"]
        R2([Root]) --> G1["createOrder"] --> G2["sendConfirmation"]
    end

    class R1,R2 io
    class FA,FB,FC train
    class G1,G2 req
```

Per the GraphQL spec, query root fields may resolve in parallel, but mutation root fields execute strictly one after another — the reason `createOrder` is guaranteed to finish before `sendConfirmation` starts.

### 4.3 Schema Composition Approaches

| Approach | Description | Use Case |
|----------|-------------|---------|
| Schema stitching | Merge multiple schemas at gateway level | Legacy; more manual |
| Federation (Apollo) | Distributed schema with reference resolver pattern | Modern microservices |
| Monolithic schema | Single service owns entire schema | Simpler, fewer moving parts |

---

## 5. Architecture Diagrams

### GraphQL Request Lifecycle

```mermaid
sequenceDiagram
    participant C as Client
    participant S as GraphQL Server
    participant UR as UserResolver
    participant OR as OrderResolver

    C->>S: POST /graphql<br/>query GetUser(id: "123")
    S->>S: 1. Parse: build AST from query string
    S->>S: 2. Validate: check AST against schema
    S->>UR: 3a. resolve Query.user(id: "123")
    UR-->>S: User{id:123, name:"Alice", email:"alice@example.com"}
    S->>OR: 3b. resolve User.orders(last: 5)
    OR-->>S: orders for user 123
    Note over S: 3c. resolve id/total/status per order<br/>(field accessors, no DB calls)
    S-->>C: 4. JSON response {data: {user: {...}}}
```

The server treats one query as a four-stage pipeline — parse to an AST, validate against the schema, execute field resolvers (each independently callable), then collect results into a response tree that mirrors the query shape.

### N+1 Problem and DataLoader Solution

Query: list 10 users with their departments (`{ users { id name department { name } } }`).

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    subgraph noDL["Without DataLoader<br/>11 total queries"]
        U1(["SELECT users<br/>LIMIT 10"]) --> D1["SELECT dept<br/>WHERE id=1"]
        U1 --> D2["SELECT dept<br/>WHERE id=2"]
        U1 --> D3["SELECT dept<br/>WHERE id=3"]
        U1 --> D4["... 7 more<br/>one-by-one queries"]
    end

    subgraph withDL["With DataLoader<br/>2 total queries"]
        U2(["SELECT users<br/>LIMIT 10"]) --> COL{"DataLoader collects<br/>10 dept IDs"}
        COL --> D5["SELECT dept<br/>WHERE id IN (1..10)"]
    end

    class U1,U2 io
    class D1,D2,D3,D4 lossN
    class COL mathOp
    class D5 train
```

Without batching, resolving each user's department independently fans out into 10 extra one-by-one queries (11 total); DataLoader collects every pending department ID and fetches them in a single `WHERE id IN (...)` query (2 total).

**The idea behind it.** "Query count grows with your result set instead of with your schema depth — so the cost of a page is set by how many rows it happens to return, not by how the query was written."

The name "N+1" hides the important property: N is not a constant you control at development time. It is the page size a client picked at runtime, which is why this bug passes every test written against three seed rows and then melts under a production page of 500.

| Symbol | What it is |
|--------|------------|
| 1 | The root query that fetches the list of users |
| N | One query per returned user, issued independently by the child field resolver |
| `1 + N` | Total round trips without batching |
| 2 | Total with DataLoader — the root query plus one batched `WHERE id IN (...)` |
| Batch window | The tick during which DataLoader accumulates pending keys before firing one query |
| Request cache | DataLoader's per-request memo, so a department requested by 10 users is fetched once |

**Walk one example.** Assume a modest 2 ms per database round trip:

```
                       queries       round-trip cost
  N = 10   no loader    1 + 10 = 11    11 x 2 ms  =  22 ms
           DataLoader            2      2 x 2 ms  =   4 ms

  N = 100  no loader   1 + 100 = 101   101 x 2 ms = 202 ms
           DataLoader            2      2 x 2 ms  =   4 ms
```

Note what the DataLoader column does: it stays at 2 queries and 4 ms while N grows tenfold.
The batched path is `O(1)` in round trips and the naive path is `O(N)`, so the gap is not a
constant speedup to be weighed against complexity — it widens without bound as clients ask for
larger pages. That asymmetry is why the Common Pitfalls section calls DataLoader mandatory
rather than recommended: there is no page size at which the naive resolver becomes acceptable
again, and the failure arrives on the day a client raises `first:` from 10 to 500.

### Apollo Federation Architecture

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    C([Client]) --> GW["Apollo Router<br/>routes and composes"]
    GW --> US["User Service<br/>owns User.id, name"]
    GW --> OS["Order Service<br/>extends User: orders"]
    GW --> PS["Product Service<br/>extends Order: items"]
    US --> J(("Join via<br/>key refs"))
    OS --> J
    PS --> J
    J --> R([Response to client])

    class C,R io
    class GW,J mathOp
    class US,OS,PS frozen
```

The gateway composes the three services' schemas, routes each field to the service that owns it, and joins the partial results back together using each type's `@key` reference.

---

## 6. How It Works — Detailed Mechanics

### 6.1 Schema Definition Language (SDL)

```graphql
schema {
  query: Query
  mutation: Mutation
  subscription: Subscription
}

type Query {
  user(id: ID!): User
  users(filter: UserFilter, first: Int, after: String): UserConnection!
  searchUsers(query: String!): [SearchResult!]!
}

type Mutation {
  createUser(input: CreateUserInput!): CreateUserPayload!
  updateUser(id: ID!, input: UpdateUserInput!): User!
  deleteUser(id: ID!): Boolean!
}

type Subscription {
  userUpdated(id: ID!): User!
}

type User {
  id: ID!
  name: String!
  email: String!
  createdAt: DateTime!
  orders(last: Int, before: String): OrderConnection!
  # Deprecated field
  legacyId: String @deprecated(reason: "Use id instead")
}

type UserConnection {
  edges: [UserEdge!]!
  pageInfo: PageInfo!
  totalCount: Int!
}

type UserEdge {
  cursor: String!
  node: User!
}

type PageInfo {
  hasNextPage: Boolean!
  hasPreviousPage: Boolean!
  startCursor: String
  endCursor: String
}

input CreateUserInput {
  name: String!
  email: String!
}

union SearchResult = User | Organization

interface Timestamped {
  createdAt: DateTime!
  updatedAt: DateTime!
}

scalar DateTime
scalar EmailAddress
```

### 6.2 DataLoader Implementation (Java)

```java
// DataLoader batches and caches resolver calls within a single request
@Component
public class DepartmentDataLoader {

    private final DepartmentRepository deptRepo;

    // Called by DataLoader when batch is ready
    public CompletableFuture<List<Department>> load(List<Long> ids) {
        return CompletableFuture.supplyAsync(() ->
            deptRepo.findAllById(ids)
        );
    }

    // Register DataLoader in GraphQL context
    public DataLoader<Long, Department> create() {
        BatchLoader<Long, Department> batchLoader = ids -> {
            List<Department> depts = deptRepo.findAllById(ids);
            // Must return results in same order as ids
            Map<Long, Department> byId = depts.stream()
                .collect(Collectors.toMap(Department::getId, d -> d));
            return CompletableFuture.completedFuture(
                ids.stream().map(byId::get).collect(Collectors.toList())
            );
        };
        return DataLoaderFactory.newDataLoader(batchLoader,
            DataLoaderOptions.newOptions()   // returns a Builder
                .setCachingEnabled(true)
                .build());
    }
}

// Spring for GraphQL: the field resolver takes the per-request DataLoader as a parameter.
// It resolves the loader registered with BatchLoaderRegistry.forTypePair(Long.class, Department.class).
@Controller
public class UserController {

    @SchemaMapping
    public CompletableFuture<Department> department(
            User user, DataLoader<Long, Department> loader) {
        // This call is deferred — DataLoader batches all pending loads
        return loader.load(user.getDepartmentId());
    }
}

// Shorthand: @BatchMapping registers the DataLoader itself and gets every User in one call
// @BatchMapping
// public Map<User, Department> department(List<User> users) { ... }
```

### 6.3 Query Complexity and Depth Limiting

Every incoming query is checked against two independent gates before execution — either one tripping rejects the query outright:

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    Q([Incoming query]) --> P{"depth over 10?"}
    P -->|"yes"| REJ1["Reject:<br/>DoS protection"]
    P -->|"no"| C{"complexity score<br/>over 100?"}
    C -->|"yes"| REJ2["Reject:<br/>DoS protection"]
    C -->|"no"| EXEC([Execute query])

    class Q io
    class P,C mathOp
    class REJ1,REJ2 lossN
    class EXEC train
```

`MaxQueryDepthInstrumentation` and `MaxQueryComplexityInstrumentation` must both be configured — a query can be shallow but expensive (wide fan-out) or deep but cheap, so either limit alone leaves a DoS gap.

```java
// Prevent DoS via deeply nested or expensive queries
@Configuration
public class GraphQLSecurityConfig {

    @Bean
    public Instrumentation queryComplexityInstrumentation() {
        return new MaxQueryComplexityInstrumentation(
            100  // maximum complexity score
        );
        // Each field has a default complexity of 1
        // Custom: @Directive or FieldComplexityCalculator
    }

    @Bean
    public Instrumentation queryDepthInstrumentation() {
        return new MaxQueryDepthInstrumentation(
            10  // maximum nesting depth
        );
    }
}

// Example: this query would be rejected
// {
//   users {              depth 1
//     friends {          depth 2
//       friends {        depth 3
//         friends {      depth 4
//           friends {    depth 5
//             ...        ...
//           }
//         }
//       }
//     }
//   }
// }
```

**Stated plainly.** "Depth counts how far the query nests; complexity counts how many nodes it will actually materialize — and only the second one tracks what the server has to pay."

The two limits exist because neither is sufficient alone, and the reason is arithmetic: nesting is additive but fan-out is *multiplicative*. Five levels of nesting is a small number; five levels of nesting each returning 100 items is not.

| Symbol | What it is |
|--------|------------|
| Depth | Nesting levels from the root selection to the deepest leaf. `users → friends → friends` is depth 3 |
| `MaxQueryDepthInstrumentation(10)` | Reject any query nesting deeper than 10 levels |
| Complexity score | Sum of per-field weights across every node the query is projected to resolve. Default weight 1 |
| `MaxQueryComplexityInstrumentation(100)` | Reject any query scoring above 100 |
| `FieldComplexityCalculator` | Custom weighting, so an expensive field can cost more than 1 |
| Page-size multiplier | The `first:`/`limit:` argument, which multiplies every field weight beneath it |

**Walk one example.** One recursive `friends` field, 100 per page, and the node count at each level:

```
  depth 1    100^1 =             100 nodes    cumulative           100
  depth 2    100^2 =          10,000          cumulative        10,100
  depth 3    100^3 =       1,000,000          cumulative     1,010,100
  depth 4    100^4 =     100,000,000          cumulative   101,010,100
  depth 5    100^5 =  10,000,000,000          cumulative 10,101,010,100
```

Five levels — well inside a depth limit of 10 — asks the server for ten billion nodes from a
query that fits comfortably on one screen. That is the DoS: the attacker spends a few hundred
bytes and the server spends its entire heap. Conversely, a query that selects 200 scalar fields
off a single object is depth 2 and utterly harmless in nesting terms, yet doubles the complexity
budget of 100.

**Why the complexity limit is the one that actually protects you.** Depth limiting is a blunt
guard against unbounded recursion; complexity limiting is the one that prices fan-out. Configure
both, because they fail in opposite directions — a wide-but-shallow query slips past the depth
check, and a deep-but-narrow one slips past a naive complexity count. Also make sure your
calculator multiplies by the pagination argument rather than counting fields flatly: without
that multiplier the 100^5 query above scores as though it returned a single node, and the limit
you configured provides no protection at all.

### 6.4 Persisted Queries

```mermaid
sequenceDiagram
    participant C as Client
    participant S as Server

    Note over C: Build time: SHA-256 hash<br/>of query string = a1b2c3d4...
    C->>S: hash only (sha256Hash=a1b2c3d4)
    S-->>C: 404 PersistedQueryNotFound (miss)
    C->>S: full query + hash (first use)
    S-->>C: result (stores hash to query mapping)
    Note over C,S: Subsequent requests
    C->>S: hash only (sha256Hash=a1b2c3d4)
    S-->>C: result (cache hit, executes stored query)
```

The client sends only a hash at runtime; on a miss the server asks for the full query once, then remembers it under that hash for every later request.

The flow above is **Automatic Persisted Queries (APQ)**, and its benefits are purely about the wire:

- GET requests (cacheable by CDN) for queries with persisted hashes
- Reduced request payload size

APQ gives you **no safelisting**. The server populates its APQ cache dynamically from whatever operation a client registers on a miss, so any client can register any query and then execute it by hash — the registry is a cache, not an allowlist. Safelisting comes from a separate mechanism: a **persisted query list (trusted documents)**, where a build step extracts the operations your first-party clients actually ship and publishes that list to the server, which then rejects every hash it was not given ahead of time. Because APQ lets clients register operations at runtime, you must turn APQ **off** for a trusted-document safelist to mean anything.

### 6.5 September 2025 Spec Edition, GraphQL over HTTP, and Incremental Delivery

**The September 2025 edition** (spec.graphql.org/September2025) is the first GraphQL spec edition since October 2021. It adds:

| Feature | What it gives you |
|---------|-------------------|
| OneOf input objects (`@oneOf`) | An input where the client sets exactly one field, checked by validation instead of resolver code |
| Schema coordinates | A standard string for any schema element: `User`, `User.email`, `Query.user(by:)`, `UserBy.email`, `@deprecated(reason:)` |
| Descriptions on executable documents | Operations and fragments can carry a description, for people and for tools that pick operations |
| Wider `@deprecated` | Deprecation on more schema elements, so input values can be retired the same way as output fields |
| Full Unicode | The language grammar accepts the whole Unicode range |

**OneOf input objects** replace the "several optional arguments, exactly one required" pattern that every resolver used to check by hand. The rules: every field of a `@oneOf` input must be nullable and have no default value, and a request must set exactly one field to a non-null value; two fields, zero fields, or one field set to `null` fail validation before any resolver runs. Introspection exposes it as `__Type.isOneOf`. Support starts at GraphQL.js 16, GraphQL Java 21.2, GraphQL Ruby 2.0.21, and HotChocolate 16.

```graphql
input UserBy @oneOf {
  id: ID
  email: String
  username: String
}

type Query {
  user(by: UserBy!): User
}
```

```java
public record UserBy(String id, String email, String username) {}

@Controller
class UserController {

    private final UserRepository users;

    UserController(UserRepository users) { this.users = users; }

    @QueryMapping
    User user(@Argument UserBy by) {
        if (by.id() != null) return users.findById(by.id());
        if (by.email() != null) return users.findByEmail(by.email());
        return users.findByUsername(by.username());
    }
}
```

The resolver has no "none or more than one set" branch, because validation has already rejected those requests.

**Schema coordinates** give field-usage metrics, lint rules, error messages, and deprecation reports one shared name per element. "Remove `User.legacyId` once usage is zero" is now a string every tool parses the same way.

**GraphQL over HTTP** is a separate specification, still a working draft. It defines the response media type `application/graphql-response+json`, which servers must support; a client that does not know the server sends `Accept: application/graphql-response+json, application/json;q=0.9`. The status codes in the October 2026 working draft:

| Situation | Status |
|-----------|--------|
| Request body is not valid JSON, or the document does not parse | 400 |
| Request is not a well-formed GraphQL-over-HTTP request, the document fails validation, the operation is ambiguous, or variable coercion fails | 422 |
| Mutation sent with GET | 405, with an `Allow` header |
| None of the `Accept` media types is supported | 406 |
| Result has both `data` (even null) and `errors` | 294 "Partial Success", recommended only with `application/graphql-response+json`; a client that does not know 294 treats it as 200 |
| Result has `data` and no errors | 200 |

With `application/graphql-response+json`, a client reads the body whatever the status code says. A response that is not a well-formed GraphQL response at all (a proxy's 502 page) must not use that media type, which is how a client tells the two apart.

**Incremental delivery (`@defer`, `@stream`)** is not in the September 2025 edition. It is a working-draft RFC (September 2024 revision) that servers such as Apollo Router implement ahead of the spec. `@defer(if:, label:)` on a fragment and `@stream(if:, label:, initialCount:)` on a list field let the server send the fast part of a response first:

```
  t=40 ms   payload 1: { data: {user: {name}}, pending: [{id:"0",path:["user"]}], hasNext: true }
  t=120 ms  payload 2: { incremental: [{ id:"0", data: { orders: [..] } }], hasNext: true }
  t=310 ms  payload 3: { incremental: [{ id:"0", data: { reviews: [..] } }],
                         completed: [{ id:"0" }], hasNext: false }
            first paint at 40 ms instead of 310 ms; the server does the same total work
```

*`pending` announces each deferred or streamed part, `incremental` delivers it (`data` for `@defer`, `items` for `@stream`), and `completed` closes it; the last payload sets `hasNext: false`.*

Servers commonly send the payloads as a `multipart/mixed` HTTP response. Any proxy, CDN, or gzip layer on the path that buffers the full response turns the stream back into one late response.

---

## 7. Real-World Examples

**Facebook (Meta)**: GraphQL was created at Facebook and powers the Facebook mobile app. The driving need was mobile clients with different data requirements than web clients. Instead of a dedicated mobile API and a web API, one GraphQL schema serves both with clients requesting only what they need.

**GitHub API v4**: GitHub's GraphQL API replaced multiple REST endpoints. A query that previously required 4 REST calls (user info, repos list, repo details, contributor stats) can be expressed as one GraphQL query. The API uses cursor-based pagination (Relay-style connections), introspection for tooling, and rate limiting based on query complexity.

**Shopify Storefront API**: Shopify exposes its storefront data via GraphQL, used by millions of stores. They use query complexity limits, persisted queries for production apps, and Apollo Federation for their microservices.

---

## 8. Tradeoffs

| Aspect | GraphQL | REST |
|--------|---------|------|
| Over/under-fetching | Eliminated (client specifies) | Inherent (fixed response) |
| Caching | Complex (POST, variable responses) | Simple (GET + URL-based) |
| N+1 queries | Requires DataLoader | Handled at endpoint |
| Learning curve | Higher | Lower |
| Tooling | Excellent (GraphiQL, Apollo GraphOS Studio) | Excellent (curl, Postman) |
| Error handling | `errors` array in the body; with `application/graphql-response+json`, 4xx/5xx only when no `data` | Standard HTTP codes |
| File uploads | Not in spec (workarounds exist) | Native multipart |
| Type safety | Strong (introspection) | Optional (OpenAPI) |
| Schema evolution | Good (additive only) | Version-based |

---

## 9. When to Use / When NOT to Use

**Use GraphQL when**: Multiple clients (mobile, web, third parties) with different data requirements; complex data models with many relationships; rapid iteration where API shape changes frequently; you want a single API for multiple consumer types.

**Do not use GraphQL when**: Simple CRUD API with few consumers; highly cacheable public APIs where HTTP GET caching is critical; file upload/download is the primary use case; your team lacks GraphQL expertise (operational complexity is high); you need strict HTTP-level caching at CDN layer.

---

## 10. Common Pitfalls

**N+1 queries without DataLoader**: Every field resolver in GraphQL executes independently. Without DataLoader, resolving 100 user objects and their departments results in 101 database queries. DataLoader is not optional for production GraphQL — it is mandatory. Every resolver that loads related data must use a DataLoader.

**Monitoring on HTTP status alone**: A partial success (some fields resolved, some failed) returns 2xx with an `errors` array alongside non-null `data`, and servers on the legacy `application/json` response type return 2xx even when execution fails entirely. Dashboards and circuit breakers keyed on non-2xx miss these errors. Serve `application/graphql-response+json` so total failures get 4xx/5xx, and count `errors` entries in instrumentation for partial ones.

**`@defer` behind a buffering proxy**: A team added `@defer` to a product page query so the price could paint before reviews, and saw no change in production. The CDN in front of the router buffered the whole `multipart/mixed` response before forwarding it, so the client got every payload at once at the old total latency. Fix: disable response buffering for the GraphQL route on every hop (CDN, ingress, service mesh), and check that compression does not hold back chunks; measure time to first payload, not total time.

**Missing query depth/complexity limits**: A malicious client can send an exponentially complex query (nested friends of friends of friends) that exhausts server resources. Always configure MaxQueryDepthInstrumentation and MaxQueryComplexityInstrumentation before going to production.

**Exposing internal schema via introspection in production**: Introspection reveals your entire schema — all types, fields, and descriptions. This is a recon goldmine for attackers. Disable introspection in production (`introspection: false` in server config) or restrict it to authenticated requests.

**Schema changes without deprecation**: Removing a field breaks all clients using it. Always deprecate with `@deprecated(reason: "...")` and monitor usage via metrics before removing. Use Apollo GraphOS Studio or similar schema management to track field usage.

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| Apollo Server | Node.js GraphQL server |
| Netflix DGS | Java Spring Boot GraphQL framework |
| graphql-java | Core Java GraphQL implementation |
| Apollo Client | JavaScript/TypeScript GraphQL client |
| urql | Lightweight GraphQL client |
| DataLoader | Batching/caching for N+1 prevention |
| Apollo Federation | Distributed schema composition |
| Apollo Router | Rust-based federation gateway |
| GraphiQL | In-browser GraphQL IDE |
| Apollo GraphOS Studio | Schema management, observability |
| GraphQL over HTTP (working draft) | Transport rules: `application/graphql-response+json`, status codes, GET versus POST |
| `graphql-code-generator` | Generate types from schema |

---

## 12. Interview Questions with Answers

**Q: What is GraphQL and what problem does it solve?**
**Short:** GraphQL lets clients request exactly the fields they need, eliminating REST's over-fetching and under-fetching.

GraphQL is an API query language and runtime that lets clients specify exactly what data they need. It solves over-fetching (REST returns fixed response shapes with more data than needed) and under-fetching (needing multiple REST calls for a single view). Clients describe the shape of the data they want; the server returns exactly that structure. This makes GraphQL particularly useful for mobile clients and complex data models.

**Q: What is the N+1 problem in GraphQL and how do you solve it?**
**Short:** DataLoader solves GraphQL N+1 by batching all pending field loads within a request into one query.

When resolving a list of objects with a related field (e.g., 10 users and their departments), each field resolver executes independently — causing 1 query for users and 10 queries for departments = 11 total. DataLoader solves this by batching all pending loads within a single request execution: after resolving all 10 users, DataLoader collects all 10 department IDs and fetches them in one SELECT IN query. DataLoader also caches within the request so duplicate IDs are fetched once.

**Q: How does GraphQL handle errors differently from REST?**
**Short:** Errors go in an errors array next to data; a partial success still returns 2xx, so status codes alone miss field-level failures.

GraphQL reports errors in an `errors` array next to the `data` field. The GraphQL-over-HTTP working draft (October 2026) gives a document that fails to parse 400, a document that fails validation or variable coercion 422, and a result with both `data` and `errors` the 2xx code 294 "Partial Success" (recommended only with the `application/graphql-response+json` media type; clients that do not know 294 treat it as 200). Many servers on the legacy `application/json` response type return 200 for every well-formed request, execution failures included. Errors include a message, locations in the query, and a path to the failing field. Partial responses are possible: some fields may resolve successfully while others fail. This differs fundamentally from REST where HTTP status codes communicate success/failure. For monitoring, parse the errors array; HTTP status catches only whole-request failures.

**Q: What are GraphQL subscriptions and how are they implemented?**
**Short:** GraphQL subscriptions push real-time updates to clients over WebSocket using the graphql-ws protocol.

Subscriptions are real-time operations where the server pushes updates to clients. They are typically implemented over WebSocket using the `graphql-ws` protocol (`graphql-transport-ws` subprotocol), which every current server and client library speaks; GraphQL over SSE is the alternative where a plain HTTP stream is preferred. The client subscribes with a subscription operation; the server publishes events when underlying data changes (via a pub/sub system like Redis). In production, you need a stateful connection manager — WebSocket connections cannot be horizontally scaled without shared state (Redis pub/sub or similar).

**Q: What is the difference between schema stitching and Apollo Federation?**
**Short:** Schema stitching merges schemas manually at a gateway, while Apollo Federation lets services own and compose schema parts declaratively.

Schema stitching merges multiple GraphQL schemas at the gateway level using shared types and remote execution. It is older, requires more manual configuration, and can create tight coupling. Apollo Federation is a specification for a distributed graph where each service owns part of the schema and can extend types defined in other services using @key and @external directives. The Apollo Router (or Gateway) composes them automatically. Federation is the modern approach for microservices.

**Q: How do you prevent abuse of GraphQL with malicious queries?**
**Short:** Guard against malicious GraphQL queries with depth limiting, complexity limiting, persisted queries, rate limiting, and disabled introspection.

Bound every query before it executes, using five layers that each cap a different axis of cost. Depth limiting (MaxQueryDepthInstrumentation — reject queries deeper than N levels), complexity limiting (MaxQueryComplexityInstrumentation — reject queries with score > threshold, where each field has a weight), operation safelisting via trusted documents (a build-time persisted query list; not APQ, which registers whatever it is sent), rate limiting (by IP or user), and disabling introspection in production. Never run GraphQL without at least depth and complexity limits.

**Q: What are persisted queries and why are they used?**
**Short:** Persisted queries send only a query hash at runtime, shrinking requests and letting a CDN cache GET requests.

Persisted queries associate a hash (SHA-256 of the query string) with the full query on the server, and clients send only the hash at runtime. Benefits: (1) reduced request size; (2) GET requests with hash + variables are cacheable by CDN, unlike a POST carrying the full query body; (3) performance — a registered operation can be pre-validated and pre-analyzed. The distinction that matters in production is which of the two variants you are running. **Automatic Persisted Queries (APQ)** register on a cache miss: the server stores whatever operation the client sends, so the registry is a cache and provides no security — any client can register any query and then run it by hash. **Trusted documents (a persisted query list)** are extracted from your clients at build time and published to the server ahead of any traffic, so the server can reject every hash it was not given; that is what delivers safelisting, and it requires APQ to be disabled, since APQ would otherwise let a caller add to the list at runtime.

**Q: How do you implement pagination in GraphQL?**
**Short:** Relay-style cursor connections are the GraphQL pagination standard, staying consistent under concurrent writes unlike offset pagination.

Relay-style connections are the standard: a Connection type with edges (cursor + node) and pageInfo (hasNextPage, endCursor). Query: `users(first: 20, after: "cursor")`. This enables cursor-based pagination, consistent even with concurrent writes. Simple pagination: `users(limit: 20, offset: 0)` is simpler but has offset performance problems at scale. Use Relay connections for user-facing paginated lists; offset for admin interfaces.

**Q: What is GraphQL introspection and should you disable it?**
**Short:** GraphQL introspection exposes the schema itself and should be disabled in production public APIs to prevent reconnaissance.

Introspection allows clients to query the schema itself: what types exist, what fields they have, what arguments each field takes. It powers GraphiQL, Apollo Sandbox, and code generators. In production, disable it for public APIs to prevent schema reconnaissance: in graphql-java call `Introspection.enabledJvmWide(false)` (or put `Introspection.INTROSPECTION_DISABLED` in the per-request `GraphQLContext`); in Spring for GraphQL set `spring.graphql.schema.introspection.enabled=false`. For internal APIs with authenticated access, it's acceptable to leave enabled. Always ensure query depth limits are set before enabling introspection to prevent introspection-based DoS.

**Q: How does GraphQL handle schema evolution compared to REST?**
**Short:** GraphQL schemas evolve additively by adding fields and deprecating old ones, since the spec has no built-in versioning.

GraphQL schemas evolve additively: add new fields, types, and operations freely — existing clients are unaffected (they only request what they know about). Deprecate old fields with @deprecated. Never remove a field without checking usage metrics first. Breaking changes (removing fields, changing types, renaming arguments) require versioning. Unlike REST, GraphQL has no built-in versioning mechanism — additive evolution is the primary strategy. Apollo GraphOS Studio tracks field usage to safely identify when deprecated fields can be removed.

**Q: What is the difference between queries and mutations in GraphQL execution?**
**Short:** GraphQL queries can resolve in parallel, but the spec requires root-level mutations to execute strictly in sequence.

Queries can execute root-level resolvers in parallel (the spec allows this for optimization). Mutations execute sequentially — the spec requires that each root-level mutation completes before the next starts. This ensures mutations like `createOrder` followed by `sendConfirmation` execute in order. Nested resolvers within a single mutation execute normally (DataLoader still batches). For multiple independent mutations, clients should send separate requests.

**Q: How do you design a GraphQL schema for a social network feed?**
**Short:** Feed schemas define a FeedItem interface with cursor pagination and use DataLoader to batch author and reaction lookups.

Define a FeedItem interface with implementing types (Post, Story, Share, AdUnit). The feed query: `feed(userId: ID!, first: Int!, after: String): FeedConnection!` uses cursor-based pagination. FeedItems include only the fields needed for the feed list view; full post content is in a separate Post type fetched on demand. Use DataLoader for author resolution, reaction counts (batched to a counting service), and media metadata. Subscriptions for real-time new items.

**Q: How do you implement file uploads in GraphQL, given it's not part of the spec?**
**Short:** GraphQL has no native file upload spec, so uploads use multipart requests or, preferably, presigned object-storage URLs.

File uploads sit outside the core GraphQL spec, handled instead by the multipart request convention or a presigned-URL pattern. The multipart approach lets a client send a mutation and binary file parts together in one `multipart/form-data` POST, supported by Apollo Server and graphql-java through community libraries, but it complicates caching and CDN behavior since the request is no longer a plain JSON POST. The presigned-URL pattern has a mutation return a signed upload URL, and the client uploads the file directly to object storage, so the binary data never passes through the GraphQL server at all. Prefer presigned URLs for production systems with large or frequent uploads, and reserve multipart requests for small, occasional file attachments.

**Q: What is the difference between a GraphQL union and an interface type?**
**Short:** A union groups unrelated types with no shared fields, while an interface requires implementers to share a common field set.

A union lists a set of unrelated object types with no shared fields, while an interface requires every implementing type to share a common set of fields. `union SearchResult = User | Organization` lets a query return either type with no fields in common between them, so the client must use an inline fragment (`... on User`) to select fields from either branch. `interface Timestamped { createdAt: DateTime! }` requires every implementing type to expose `createdAt`, so a client can query that field directly on the interface without an inline fragment, and only needs fragments for fields specific to one implementation. Use a union for genuinely unrelated result types like search results, and an interface when multiple types share a meaningful common contract.

**Q: What are custom scalars in GraphQL and how do you implement one?**
**Short:** A custom scalar adds serialize, parseValue, and parseLiteral functions to validate a leaf value beyond the built-in types.

A custom scalar is a leaf type beyond String, Int, Float, Boolean, and ID that adds validation and serialization rules for a specific value shape, such as `DateTime` or `EmailAddress`. You implement one by providing three functions to the runtime: `serialize` (converts the internal server value to the wire format sent to the client), `parseValue` (converts a client-supplied variable into the internal server representation), and `parseLiteral` (converts a value written inline in the query string). A `DateTime` scalar might serialize a `java.time.Instant` to an ISO-8601 string and reject any input that fails ISO-8601 parsing during `parseValue`, giving validation for free at the schema boundary instead of in every resolver. Define custom scalars for any value type that has format rules you would otherwise repeat as ad hoc validation across many fields.

**Q: How do you implement field-level authorization in GraphQL?**
**Short:** Field-level authorization checks access per field via a resolver or an @auth directive, since GraphQL queries cross domains.

Field-level authorization is enforced per-field, either inside a resolver or via a directive like `@auth(role: "ADMIN")` applied in the schema. Because a single GraphQL query can traverse many types and fields owned by different parts of the domain, endpoint-level authorization — the REST model of "can this user call this URL" — does not translate directly; a `user` query might be public, but a nested `user.ssn` field might require an admin role. A custom `@auth` directive wraps the field's data-fetcher with a permission check, returning null or a field-level error for unauthorized access without failing the rest of the query. Implement authorization directives early in schema design, since retrofitting per-field checks onto an already-large schema means auditing every resolver individually.

**Q: Why must every field of a @oneOf input object be nullable, and what happens if a client sends two of its fields?**
**Short:** Exactly one field may be set, so none can be required or defaulted; a request with two fields, no field, or one field set to null fails validation before any resolver runs.

A `@oneOf` input (September 2025 spec edition) says "the client picks exactly one of these". If any field were non-null (`ID!`) the client would be forced to set it every time, and if any field had a default value it would count as set, so the spec requires every field to be nullable and to have no default. At request time, validation and input coercion check that exactly one field is present with a non-null value: `user(by: {id: "1", email: "a@b.com"})`, `user(by: {})`, and `user(by: {email: null})` all fail with a request error and no resolver runs. The common mistake is reading the nullable field types as "optional, any combination allowed" and adding resolver code for the two-field case; that branch is dead. The other mistake is adding a field with a default later to keep old clients working, which the schema rejects; add a new nullable field instead.

**Q: What changed in the September 2025 GraphQL spec edition, and why does it matter for schema design?**
**Short:** The first edition since 2021 added OneOf input objects, schema coordinates, descriptions on operations, wider @deprecated, and full Unicode; OneOf replaces hand-checked exactly-one argument sets.

The September 2025 edition is the first since October 2021. OneOf input objects (`input UserBy @oneOf { id: ID, email: String }`) move "exactly one of these arguments" from resolver code into validation, so one `user(by: UserBy!)` field replaces `userById`, `userByEmail`, and friends, and the rule shows in introspection as `__Type.isOneOf`. Schema coordinates give every element a standard name (`User.email`, `Query.user(by:)`, `@deprecated(reason:)`), which usage metrics, lint rules, and error messages can share. Executable documents can carry descriptions. `@deprecated` covers more schema elements, so input values can be retired through the same deprecate-then-remove cycle as output fields. The grammar accepts full Unicode. Support for OneOf starts at GraphQL.js 16, GraphQL Java 21.2, and HotChocolate 16, so check every server and code generator in the path before using it in a public schema.

**Q: How do @defer and @stream deliver one GraphQL response in parts, and are they in the spec?**
**Short:** They are a working-draft RFC outside the September 2025 edition; the server sends an initial payload, then incremental parts announced as pending, until a payload sets hasNext to false.

`@defer` on a fragment and `@stream` on a list field are defined by the incremental delivery RFC (working draft, September 2024 revision), not by the September 2025 edition, though Apollo Router and other servers implement them. The first payload is a normal response minus the deferred fields and with only `initialCount` items of a streamed list, plus a `pending` array (each entry an `id`, optional `label`, and `path`) and `hasNext: true`. Later payloads carry `incremental` entries (`data` for a deferred fragment, `items` for streamed list items) keyed by those ids, and `completed` when a part is done; the last payload sets `hasNext: false`. Both directives take `if` to switch deferral off per request. The benefit is time to first paint: a page whose slow reviews take 310 ms can render the product at 40 ms. The cost is that every hop must forward the `multipart/mixed` response unbuffered, and clients and caches must handle a response that arrives in parts.

---

## 13. Best Practices

- Always use DataLoader for resolving related entities — no exceptions.
- Set MaxQueryDepthInstrumentation (10 recommended) and MaxQueryComplexityInstrumentation (100–200 recommended) before going to production.
- Disable introspection in production or restrict it to authenticated requests.
- Use persisted queries in production: APQ for CDN cacheability and smaller requests, and a build-time trusted-document list (with APQ off) when you need operation safelisting.
- Follow Relay connection spec for paginated lists.
- Deprecate, never remove fields; monitor usage with Apollo GraphOS Studio or similar.
- Return structured errors with error codes in extensions: `{"code": "NOT_FOUND", "field": "userId"}`.
- Use Input types for mutations (not inline arguments) to enable clean schema evolution.
- Model "exactly one of these" arguments as a `@oneOf` input once every server and client tool in the path supports it, instead of checking the combination in resolvers.

---

## 14. Case Study

**Problem**: A mobile app for a content platform was making 5 REST API calls on launch: GET /user/profile, GET /feed?page=1, GET /user/notifications, GET /user/stats, GET /feed/recommendations. Each call was sequential (each needed the user ID from the previous), adding up to 800ms on first launch over a 4G connection.

**Analysis**: The five endpoints were defined around server convenience (separate microservices), not client needs. The mobile app needed specific fields from each, but each endpoint returned full objects.

**Migration to GraphQL**:
```graphql
query AppLaunch($userId: ID!) {
  user(id: $userId) {
    name
    avatarUrl
    unreadNotificationCount
    stats {
      followers
      following
      postsCount
    }
  }
  feed(userId: $userId, first: 10) {
    edges {
      node {
        id
        author { name avatarUrl }
        content
        likeCount
        commentCount
      }
    }
    pageInfo { hasNextPage endCursor }
  }
  recommendations(userId: $userId, first: 5) {
    id
    title
    thumbnailUrl
  }
}
```

One request, returning only the fields the mobile app needed. DataLoader batched all author lookups. The gateway fan-out to the three microservices in parallel.

**Results**:
- Launch API calls: 5 → 1
- Launch latency: 800ms → 180ms (all three services queried in parallel)
- Payload size: 12 KB combined → 3.8 KB (only requested fields)
- Developer experience: frontend team could iterate on data requirements without backend API changes

**Tradeoff accepted**: GraphQL caching is more complex (POST requests, variable responses). Solution: persisted queries with GET requests for the AppLaunch query, enabling CDN caching with 30s TTL.
