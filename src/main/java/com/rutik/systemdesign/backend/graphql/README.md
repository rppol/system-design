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
| Union | One of multiple types | `union SearchResult = User | Post` |
| Input type | Object used as argument | `input CreateUserInput { name: String! }` |
| List | Array of a type | `[User]`, `[User!]!` |
| Non-null | Field cannot be null | `String!` |

### 4.2 Operations

| Operation | Purpose | Execution |
|-----------|---------|-----------|
| Query | Fetch data (read-only) | Parallel field resolution possible |
| Mutation | Modify data | Sequential (one at a time per spec) |
| Subscription | Real-time updates | WebSocket or SSE-based |

### 4.3 Schema Composition Approaches

| Approach | Description | Use Case |
|----------|-------------|---------|
| Schema stitching | Merge multiple schemas at gateway level | Legacy; more manual |
| Federation (Apollo) | Distributed schema with reference resolver pattern | Modern microservices |
| Monolithic schema | Single service owns entire schema | Simpler, fewer moving parts |

---

## 5. Architecture Diagrams

### GraphQL Request Lifecycle

```
Client sends:
POST /graphql
{
  "query": "query GetUser($id: ID!) {
    user(id: $id) {
      name
      email
      orders(last: 5) {
        id
        total
        status
      }
    }
  }",
  "variables": { "id": "123" }
}

Server execution:
1. Parse: build AST from query string
2. Validate: check AST against schema (field exists? types match?)
3. Execute:
   a. Resolve Query.user(id: "123") → calls UserResolver.user()
      → fetches User{id:123, name:"Alice", email:"alice@example.com"}
   b. Resolve User.orders(last: 5) → calls OrderResolver.orders()
      → fetches orders for user 123
   c. For each Order, resolve id, total, status (field accessors, no DB calls)
4. Collect results into response shape:
{
  "data": {
    "user": {
      "name": "Alice",
      "email": "alice@example.com",
      "orders": [
        { "id": "1", "total": 99.99, "status": "DELIVERED" },
        ...
      ]
    }
  }
}
```

### N+1 Problem and DataLoader Solution

```
Query: list 10 users with their departments
{
  users {
    id
    name
    department {
      name
    }
  }
}

WITHOUT DataLoader:
  1. SELECT * FROM users LIMIT 10           → 10 users
  2. SELECT * FROM departments WHERE id=1   → user[0]'s dept
  3. SELECT * FROM departments WHERE id=2   → user[1]'s dept
  4. SELECT * FROM departments WHERE id=3   → user[2]'s dept
  ...
  11. SELECT * FROM departments WHERE id=10  → user[9]'s dept
  Total: 11 queries (1 + N where N=10)

WITH DataLoader:
  1. SELECT * FROM users LIMIT 10              → 10 users
     DataLoader collects dept IDs: [1,2,3,...,10]
  2. SELECT * FROM departments WHERE id IN (1,2,3,...,10)  → all depts at once
  Total: 2 queries (batched)
```

### Apollo Federation Architecture

```
Client
  |
  v
Apollo Gateway (or Router)
  |
  +------ User Service (/graphql)
  |         type User @key(fields: "id") {
  |           id: ID!
  |           name: String!
  |         }
  |
  +------ Order Service (/graphql)
  |         extend type User @key(fields: "id") {
  |           orders: [Order]
  |         }
  |         type Order { ... }
  |
  +------ Product Service (/graphql)
            extend type Order @key(fields: "id") {
              items: [Product]
            }

Gateway composes schemas, routes fields to owning services,
joins results using @key references.
```

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
        return DataLoader.newDataLoader(batchLoader,
            DataLoaderOptions.newOptions().setCachingEnabled(true));
    }
}

// Resolver uses DataLoader
@Component
public class UserResolver implements GraphQLResolver<User> {

    public CompletableFuture<Department> department(
            User user, DataFetchingEnvironment env) {
        DataLoader<Long, Department> loader =
            env.getDataLoader("department");
        // This call is deferred — DataLoader batches all pending loads
        return loader.load(user.getDepartmentId());
    }
}
```

### 6.3 Query Complexity and Depth Limiting

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

### 6.4 Persisted Queries

```
Client flow with persisted queries:
1. During build: compute SHA-256 of all query strings
   hash("query GetUser($id: ID!) { user(id: $id) { name } }")
   = "a1b2c3d4..."

2. Runtime request (first use):
   { "extensions": { "persistedQuery": {
       "version": 1, "sha256Hash": "a1b2c3d4..."
   }}}
   Server: miss, responds with 404/PersistedQueryNotFound

3. Client retries with full query:
   { "query": "...", "extensions": { "persistedQuery": {
       "version": 1, "sha256Hash": "a1b2c3d4..."
   }}}
   Server: stores hash→query mapping, returns result

4. Subsequent requests:
   { "extensions": { "persistedQuery": { "sha256Hash": "a1b2c3d4..." }}}
   Server: cache hit, executes stored query, returns result

Benefits:
- GET requests (cacheable by CDN) for queries with persisted hashes
- Reduced request payload size
- Security: reject arbitrary query strings (only allow registered hashes)
```

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
| Tooling | Excellent (GraphiQL, Apollo Studio) | Excellent (curl, Postman) |
| Error handling | Non-standard (HTTP 200 with errors) | Standard HTTP codes |
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

**HTTP 200 for all errors**: GraphQL returns HTTP 200 even when there are errors in the response, with an `errors` array alongside `data`. This breaks monitoring tools expecting non-2xx for errors, breaks circuit breakers, and confuses logging. Add instrumentation to propagate error presence to HTTP 4xx/5xx when no data is returned.

**Missing query depth/complexity limits**: A malicious client can send an exponentially complex query (nested friends of friends of friends) that exhausts server resources. Always configure MaxQueryDepthInstrumentation and MaxQueryComplexityInstrumentation before going to production.

**Exposing internal schema via introspection in production**: Introspection reveals your entire schema — all types, fields, and descriptions. This is a recon goldmine for attackers. Disable introspection in production (`introspection: false` in server config) or restrict it to authenticated requests.

**Schema changes without deprecation**: Removing a field breaks all clients using it. Always deprecate with `@deprecated(reason: "...")` and monitor usage via metrics before removing. Use Apollo Studio or similar schema management to track field usage.

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
| Apollo Studio | Schema management, observability |
| `graphql-code-generator` | Generate types from schema |

---

## 12. Interview Questions with Answers

**What is GraphQL and what problem does it solve?**
GraphQL is an API query language and runtime that lets clients specify exactly what data they need. It solves over-fetching (REST returns fixed response shapes with more data than needed) and under-fetching (needing multiple REST calls for a single view). Clients describe the shape of the data they want; the server returns exactly that structure. This makes GraphQL particularly useful for mobile clients and complex data models.

**What is the N+1 problem in GraphQL and how do you solve it?**
When resolving a list of objects with a related field (e.g., 10 users and their departments), each field resolver executes independently — causing 1 query for users and 10 queries for departments = 11 total. DataLoader solves this by batching all pending loads within a single request execution: after resolving all 10 users, DataLoader collects all 10 department IDs and fetches them in one SELECT IN query. DataLoader also caches within the request so duplicate IDs are fetched once.

**How does GraphQL handle errors differently from REST?**
GraphQL always returns HTTP 200, with a `data` field for results and an `errors` array for errors. Errors include a message, locations in the query, and a path to the failing field. Partial responses are possible: some fields may resolve successfully while others fail. This differs fundamentally from REST where HTTP status codes communicate success/failure. For monitoring, you must parse the errors array, not rely on HTTP status codes.

**What are GraphQL subscriptions and how are they implemented?**
Subscriptions are real-time operations where the server pushes updates to clients. They are typically implemented over WebSocket (using graphql-ws or subscriptions-transport-ws protocols). The client subscribes with a subscription operation; the server publishes events when underlying data changes (via a pub/sub system like Redis). In production, you need a stateful connection manager — WebSocket connections cannot be horizontally scaled without shared state (Redis pub/sub or similar).

**What is the difference between schema stitching and Apollo Federation?**
Schema stitching merges multiple GraphQL schemas at the gateway level using shared types and remote execution. It is older, requires more manual configuration, and can create tight coupling. Apollo Federation is a specification for a distributed graph where each service owns part of the schema and can extend types defined in other services using @key and @external directives. The Apollo Router (or Gateway) composes them automatically. Federation is the modern approach for microservices.

**How do you prevent abuse of GraphQL with malicious queries?**
Depth limiting (MaxQueryDepthInstrumentation — reject queries deeper than N levels), complexity limiting (MaxQueryComplexityInstrumentation — reject queries with score > threshold, where each field has a weight), query whitelisting / persisted queries (only allow registered query hashes in production), rate limiting (by IP or user), and disabling introspection in production. Never run GraphQL without at least depth and complexity limits.

**What are persisted queries and why are they used?**
Persisted queries associate a hash (SHA-256 of the query string) with the full query on the server. Clients send only the hash at runtime. Benefits: (1) reduced request size; (2) GET requests with hash + variables are cacheable by CDN (unlike POST with full query body); (3) security — reject any query not in the registry, preventing query injection; (4) performance — queries can be pre-validated and pre-analyzed. Used in production by most large GraphQL deployments.

**How do you implement pagination in GraphQL?**
Relay-style connections are the standard: a Connection type with edges (cursor + node) and pageInfo (hasNextPage, endCursor). Query: `users(first: 20, after: "cursor")`. This enables cursor-based pagination, consistent even with concurrent writes. Simple pagination: `users(limit: 20, offset: 0)` is simpler but has offset performance problems at scale. Use Relay connections for user-facing paginated lists; offset for admin interfaces.

**What is GraphQL introspection and should you disable it?**
Introspection allows clients to query the schema itself: what types exist, what fields they have, what arguments each field takes. It powers GraphiQL, Apollo Sandbox, and code generators. In production, disable it for public APIs to prevent schema reconnaissance: `GraphQL.newGraphQL().introspection(false)`. For internal APIs with authenticated access, it's acceptable to leave enabled. Always ensure query depth limits are set before enabling introspection to prevent introspection-based DoS.

**How does GraphQL handle schema evolution compared to REST?**
GraphQL schemas evolve additively: add new fields, types, and operations freely — existing clients are unaffected (they only request what they know about). Deprecate old fields with @deprecated. Never remove a field without checking usage metrics first. Breaking changes (removing fields, changing types, renaming arguments) require versioning. Unlike REST, GraphQL has no built-in versioning mechanism — additive evolution is the primary strategy. Apollo Studio tracks field usage to safely identify when deprecated fields can be removed.

**What is the difference between queries and mutations in GraphQL execution?**
Queries can execute root-level resolvers in parallel (the spec allows this for optimization). Mutations execute sequentially — the spec requires that each root-level mutation completes before the next starts. This ensures mutations like `createOrder` followed by `sendConfirmation` execute in order. Nested resolvers within a single mutation execute normally (DataLoader still batches). For multiple independent mutations, clients should send separate requests.

**How do you design a GraphQL schema for a social network feed?**
Define a FeedItem interface with implementing types (Post, Story, Share, AdUnit). The feed query: `feed(userId: ID!, first: Int!, after: String): FeedConnection!` uses cursor-based pagination. FeedItems include only the fields needed for the feed list view; full post content is in a separate Post type fetched on demand. Use DataLoader for author resolution, reaction counts (batched to a counting service), and media metadata. Subscriptions for real-time new items.

---

## 13. Best Practices

- Always use DataLoader for resolving related entities — no exceptions.
- Set MaxQueryDepthInstrumentation (10 recommended) and MaxQueryComplexityInstrumentation (100–200 recommended) before going to production.
- Disable introspection in production or restrict it to authenticated requests.
- Use persisted queries in production for security and CDN cacheability.
- Follow Relay connection spec for paginated lists.
- Deprecate, never remove fields; monitor usage with Apollo Studio or similar.
- Return structured errors with error codes in extensions: `{"code": "NOT_FOUND", "field": "userId"}`.
- Use Input types for mutations (not inline arguments) to enable clean schema evolution.

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
