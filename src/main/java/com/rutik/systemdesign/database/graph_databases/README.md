# Graph Databases

## 1. Concept Overview

Graph databases model data as nodes (entities), relationships (connections), and properties (key-value pairs on nodes and relationships). They excel at traversing highly connected data where relationships are first-class citizens rather than foreign-key JOIN operations. Neo4j is the dominant native graph database; Amazon Neptune, TigerGraph, and JanusGraph are notable alternatives.

---

## 2. Intuition

Relational databases store relationships as foreign keys — finding connected data requires JOIN operations that scan index trees. Graph databases use index-free adjacency: each node directly stores pointers to its adjacent relationships. Traversing from node to node follows pointers at O(1) per hop, making multi-hop traversals dramatically faster than recursive JOINs.

- **Key insight**: Graph databases win when the query is relationship-traversal-heavy (fraud ring detection, recommendation paths, access control graphs). They lose for high write throughput, simple queries, and OLAP aggregations.

---

## 3. Core Principles

### Property Graph Model

```
Property Graph:
  Nodes: entities with labels (type tags) and properties (key-value pairs)
  Relationships: directed, typed connections between nodes with properties

Example: Social network
  (User {id: 1, name: "Alice", age: 30})
    -[:FOLLOWS {since: 2023-01-15}]→
  (User {id: 2, name: "Bob", age: 25})
    -[:WORKS_AT {from: 2022-06-01, role: "Engineer"}]→
  (Company {id: 10, name: "Acme Corp"})

vs RDF/Triple Store (W3C standard):
  Subject → Predicate → Object
  (alice) → (follows) → (bob)
  (bob)   → (works_at)→ (acme)
  No native property support — attributes are additional triples
  Used for: semantic web, knowledge graphs, linked data
```

### Index-Free Adjacency

```
Relational (finding friends of friends):
  SELECT u.name FROM users u
  JOIN friendships f1 ON f1.user_id = 1
  JOIN friendships f2 ON f2.user_id = f1.friend_id AND f2.friend_id != 1
  JOIN users u ON u.id = f2.friend_id;
  → Each JOIN: O(log n) index lookup × N rows = O(N log n)

Neo4j native graph (index-free adjacency):
  MATCH (alice:User {id: 1})-[:FRIENDS]->(:User)-[:FRIENDS]->(fof:User)
  WHERE fof.id != 1 RETURN fof.name;
  → alice's node record → pointer to FRIENDS rel records (O(1))
  → Each rel record → pointer to friend node (O(1))
  → Repeat per friend → total O(degree), not O(N log N)

Concrete numbers:
  Neo4j relationship traversal: ~O(1) per hop (following stored pointer)
  PostgreSQL recursive CTE: O(log n) per hop for indexed FK lookup
  At 6 degrees of separation: Neo4j ~millions of hops in seconds;
    relational: O(n^6) — impractical for 1M+ nodes
```

### Neo4j Record Files

```
Neo4j storage (physical layout):
  neostore.nodestore.db:    Fixed 15-byte records per node
  neostore.relationshipstore.db:  Fixed 34-byte records per relationship
  neostore.propertystore.db:  Variable-length property records

Node record (15 bytes):
  1 byte: in-use flag
  4 bytes: first relationship ID
  4 bytes: first property ID
  4 bytes: label store ID
  2 bytes: extra

Relationship record (34 bytes):
  1 byte: in-use flag
  4 bytes: first node ID (source)
  4 bytes: second node ID (target)
  4 bytes: relationship type ID
  4 bytes: first relationship of first node (doubly-linked list)
  4 bytes: second relationship of first node
  4 bytes: first relationship of second node
  4 bytes: second relationship of second node
  4 bytes: next property ID
  1 byte: flags (first in chain)

Why fixed-size records matter:
  Random access by record ID: record offset = ID × record_size
  Traversal: follow relationship → find node at ID × 15 bytes offset
  This is the "index-free adjacency" — no B+tree lookup needed
```

---

## 4. Types / Architectures / Strategies

### Cypher Query Language

```cypher
-- Create nodes and relationships:
CREATE (alice:User {name: "Alice", age: 30})
CREATE (bob:User {name: "Bob", age: 25})
CREATE (alice)-[:FOLLOWS {since: date("2024-01-15")}]->(bob)

-- Find: users Alice follows who work at tech companies:
MATCH (alice:User {name: "Alice"})-[:FOLLOWS]->(u:User)-[:WORKS_AT]->(c:Company)
WHERE c.industry = "Technology"
RETURN u.name, c.name

-- Shortest path (BFS):
MATCH path = shortestPath((alice:User {name: "Alice"})-[:KNOWS*]-(target:User {name: "Charlie"}))
RETURN path, length(path) AS degrees

-- Variable-length traversal (1 to 5 hops):
MATCH (alice:User {name: "Alice"})-[:FOLLOWS*1..5]->(u:User)
RETURN u.name, count(*) AS reach

-- Pattern for fraud detection (ring transaction):
MATCH (a:Account)-[:SENT_TO]->(b:Account)-[:SENT_TO]->(c:Account)-[:SENT_TO]->(a)
WHERE a <> c
RETURN a.id, b.id, c.id

-- MERGE (upsert — create if not exists, match if exists):
MERGE (u:User {email: "alice@example.com"})
ON CREATE SET u.created_at = datetime(), u.name = "Alice"
ON MATCH SET u.last_seen = datetime()
```

### Graph Algorithms (GDS — Graph Data Science Library)

```cypher
-- PageRank (node importance/centrality):
CALL gds.pageRank.stream('social-graph', {
  maxIterations: 20,
  dampingFactor: 0.85
}) YIELD nodeId, score
RETURN gds.util.asNode(nodeId).name, score
ORDER BY score DESC LIMIT 10

-- Community detection (Louvain):
CALL gds.louvain.stream('social-graph') YIELD nodeId, communityId
RETURN communityId, count(*) AS members ORDER BY members DESC

-- Shortest path (Dijkstra weighted):
CALL gds.shortestPath.dijkstra.stream('road-network', {
  sourceNode: sourceId,
  targetNode: targetId,
  relationshipWeightProperty: 'distance'
}) YIELD totalCost, nodeIds
```

---

## 5. Architecture Diagrams

```
FRAUD DETECTION GRAPH SCHEMA:

(:Account {id, balance, created_at})
  -[:SENT_TO {amount, timestamp}]→ (:Account)
  -[:OWNED_BY]→ (:Person {name, ssn, dob})
                   -[:LIVES_AT]→ (:Address)
                   -[:USES_DEVICE]→ (:Device {ip, fingerprint})

Ring fraud pattern:
Account A ──SENT_TO──→ Account B ──SENT_TO──→ Account C ──SENT_TO──→ Account A

Query:
MATCH (a:Account)-[:SENT_TO]->(b:Account)-[:SENT_TO]->(c:Account)-[:SENT_TO]->(a)
WHERE a.id <> c.id AND a.id <> b.id
RETURN a.id, b.id, c.id, "ring_fraud" AS pattern

SOCIAL RECOMMENDATION GRAPH:
(:User)-[:WATCHED {rating, timestamp}]→ (:Movie)
(:User)-[:FRIENDS_WITH]-(:User)

"Movies my friends watched that I haven't":
MATCH (me:User {id: 42})-[:FRIENDS_WITH]-(friend:User)-[:WATCHED]->(movie:Movie)
WHERE NOT (me)-[:WATCHED]->(movie)
RETURN movie.title, count(friend) AS friends_who_watched, avg(friend.rating) AS avg_rating
ORDER BY friends_who_watched DESC
LIMIT 10
```

---

## 6. How It Works — Detailed Mechanics

### TinkerPop / Gremlin (Vendor-Neutral Traversal)

```groovy
// Gremlin traversal (works with Neo4j, JanusGraph, Amazon Neptune):
// Find products purchased by customers who also bought product 42:
g.V().has('Product', 'id', 42)        // Start at product 42
  .in('PURCHASED')                    // Traverse to customers who bought it
  .out('PURCHASED')                  // Traverse to what they also bought
  .where(neq(V().has('Product', 'id', 42).next()))  // Exclude product 42
  .groupCount()                       // Count by product
  .order(local).by(values, desc)     // Sort by frequency
  .limit(local, 10)                   // Top 10
```

### Access Control Graph

```cypher
-- RBAC/ABAC via graph:
(:User {id, name})
  -[:MEMBER_OF]→ (:Group {name})
  -[:HAS_ROLE]→ (:Role {name})
  -[:GRANTS]→ (:Permission {action, resource})

-- "Can Alice delete documents in project X?":
MATCH (alice:User {name: "Alice"})-[:MEMBER_OF*1..3]->(:Group)-[:HAS_ROLE]->(:Role)-[:GRANTS]->(p:Permission)
WHERE p.action = "delete" AND p.resource = "document"
  AND (p)-[:SCOPED_TO]->(:Project {id: "X"})
RETURN count(p) > 0 AS allowed

-- This handles nested group membership automatically (GROUP-IN-GROUP)
-- Relational equivalent: 3-5 recursive CTEs + JOINs — much more complex
```

---

## 7. Real-World Examples

- **PayPal**: Uses graph databases for fraud detection — detecting rings of compromised accounts performing circular transactions.
- **LinkedIn**: Social graph for "people you may know" — 2nd-degree connections, common connections.
- **NASA**: Knowledge graph for equipment maintenance, tracking dependencies between systems.
- **Airbnb**: Knowledge graph for recommendations — host-guest interaction patterns, location graphs.
- **eBay**: Shopping graph for product recommendations based on browsing patterns.
- **Twitter**: Social graph for following/followers — initially used relational, moved to graph for relationship traversal.

---

## 8. Tradeoffs

| Feature | Graph DB (Neo4j) | PostgreSQL (recursive CTE) | MongoDB |
|---------|-----------------|--------------------------|---------|
| Multi-hop traversal | O(degree) per hop | O(n log n) per hop | Not designed for |
| Query language | Cypher (intuitive for graphs) | SQL (complex for graphs) | MQL (poor for graphs) |
| ACID | Yes (full) | Yes | Yes (4.0+) |
| Write throughput | Medium | High | High |
| Horizontal scale | Limited | Manual sharding | Built-in |
| Analytics/OLAP | Poor | Good | Good |
| Use case fit | Highly connected data | General relational | Documents |

---

## 9. When to Use / When NOT to Use

**Use graph database when**:
- Data is highly connected and relationships are queried heavily
- Variable-depth traversal (1 to N hops)
- Relationship properties matter (weight, timestamp)
- Fraud detection patterns, social recommendations, access control
- Knowledge graphs, taxonomy traversal

**Do not use when**:
- High write throughput > 50K writes/second (graph DBs are not optimized for this)
- Simple key-value or document lookups (no traversal benefit)
- OLAP aggregations over all data (use ClickHouse or PostgreSQL)
- Hierarchical data with bounded depth (PostgreSQL recursive CTE is sufficient)
- Team is not comfortable with graph data modeling

**PostgreSQL recursive CTE is sufficient when**:
- Depth is bounded (< 5 levels)
- Relationship structure is simple (one relationship type, few properties)
- The rest of the data is relational and adding another database is not worth the operational overhead

---

## 10. Common Pitfalls

**Pitfall 1: Modeling everything as a property instead of a relationship**
```cypher
-- Broken: friend IDs as a list property
(:User {id: 1, friends: [2, 3, 4, ...]})
-- Cannot traverse, index only on id (not list contents), no relationship properties

-- Fix: explicit relationships
(:User {id: 1})-[:FRIENDS_WITH {since: date("2024")}]→(:User {id: 2})
-- Now: traversal is native, relationship has properties, indexable
```

**Pitfall 2: Supernode problem (high-degree nodes)**
A "celebrity" user with 50 million followers. Every traversal starting from or passing through this user (e.g., "who follows people who follow @celebrity?") must navigate 50M relationships. Solution: (1) Filter by additional criteria before traversing the high-degree node. (2) Cache popular traversal results. (3) Use sampling for recommendations (don't traverse all 50M edges). (4) Store popularity separately as a property and filter before graph traversal.

**Pitfall 3: Treating Neo4j as a general database**
A team replaced their entire PostgreSQL with Neo4j. Simple queries like "count all users by country" required full graph scans (no columnar optimization). Customer reports taking 30 minutes. Graph databases are not good at aggregation over all nodes. Fix: use PostgreSQL for tabular analytics, Neo4j only for relationship-heavy queries.

**Pitfall 4: Missing indexes on node properties**
Neo4j without indexes on node properties is equivalent to a full graph scan for every lookup. `MATCH (u:User {email: "alice@example.com"})` — without an index on `User.email`: scans ALL user nodes. Fix: `CREATE INDEX FOR (u:User) ON (u.email)`.

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| Neo4j Browser | Graph visualization, Cypher query interface |
| Neo4j GDS | Graph Data Science library (PageRank, community detection) |
| `EXPLAIN` / `PROFILE` | Query plan analysis in Neo4j |
| Amazon Neptune | Managed graph database (Gremlin + SPARQL) |
| TigerGraph | Distributed graph for very large graphs |
| JanusGraph | Open-source distributed graph (Cassandra/HBase backend) |
| Memgraph | In-memory graph database |
| ArangoDB | Multi-model (graph + document + KV) |
| Apache AGE | PostgreSQL extension for graph queries |
| APOC | Neo4j procedures library (utility functions) |

---

## 12. Interview Questions with Answers

**Q: How does index-free adjacency in Neo4j differ from a foreign key join in PostgreSQL?**
In PostgreSQL, finding connected data requires a foreign key join — an index lookup (B+tree traversal) to find matching rows. For a query with 4 hops: 4 index traversals, each O(log n) where n is the number of rows. Total: O(4 × log n) minimum, but often O(n^k) for k-hop queries on large datasets. In Neo4j, each node record stores a direct pointer to its first relationship record. Each relationship record stores pointers to the source and destination nodes plus the next relationship of each node (doubly-linked list of relationships). Traversal: follow pointer from node → relationship → next node — all pointer dereferences at fixed offsets in fixed-size record files. Time per hop: O(1). For 4 hops: O(degree1 × degree2 × degree3 × degree4) — proportional to the number of actual connections, not the total graph size.

**Q: Design a fraud detection graph schema for detecting ring transactions.**
Schema:
- Nodes: `(:Account {id, balance, created_at, risk_score})`, `(:Person {id, name, ssn})`, `(:Device {fingerprint, ip})`
- Relationships: `(:Account)-[:SENT_TO {amount, timestamp, tx_id}]→(:Account)`, `(:Person)-[:OWNS]→(:Account)`, `(:Person)-[:USES]→(:Device)`

Ring pattern (3-hop loop):
```cypher
MATCH (a:Account)-[:SENT_TO]->(b:Account)-[:SENT_TO]->(c:Account)-[:SENT_TO]->(a)
WHERE a.id <> b.id AND b.id <> c.id
  AND all(r IN relationships() WHERE r.timestamp > datetime() - duration('P7D'))
RETURN a.id, b.id, c.id AS ring
```

Advanced: shared device/address detection (money mule network):
```cypher
MATCH (p1:Person)-[:USES]->(d:Device)<-[:USES]-(p2:Person)
WHERE p1 <> p2
MATCH (p1)-[:OWNS]->(a1:Account), (p2)-[:OWNS]->(a2:Account)
RETURN p1.id, p2.id, d.fingerprint AS shared_device, a1.id, a2.id
```

**Q: When would you choose a graph database over PostgreSQL with recursive CTEs?**
Choose graph database when: (1) Variable-depth traversal with no fixed maximum depth ("who can reach X within any number of hops"). (2) Complex pattern matching across multiple relationship types (fraud rings, supply chain). (3) Relationship properties matter and are frequently queried (when did they connect, how strong is the connection). (4) Graph algorithms (PageRank, community detection, shortest path) are core to the application. Choose PostgreSQL recursive CTEs when: (1) Maximum depth is bounded and small (< 5 levels). (2) The rest of the data is relational and the overhead of running a separate database outweighs the graph traversal benefit. (3) Simple parent-child hierarchy (org chart, file system) — ltree extension may suffice. (4) Team is more comfortable with SQL than Cypher/Gremlin.

**Q: What is the Cypher MERGE statement and when do you use it?**
MERGE is a combination of MATCH and CREATE: it matches the pattern if it exists and creates it if not. Essential for idempotent graph updates — prevents duplicate node or relationship creation. `MERGE (u:User {email: "alice@example.com"}) ON CREATE SET u.created_at = datetime() ON MATCH SET u.last_login = datetime()` — creates the user if the email doesn't exist, updates last_login if it does. Common mistake: `MERGE` on a large pattern without indexes — it performs a full scan to find the pattern before deciding to create it. Always ensure all properties used in MERGE conditions are indexed. Use `MERGE` for nodes when you have a natural unique identifier; use CREATE for relationships when you want to allow multiple relationships of the same type between two nodes.

**Q: How does Neo4j GDS (Graph Data Science) library enable graph analytics?**
GDS provides in-memory graph projections and algorithm implementations: (1) Project the graph: `CALL gds.graph.project('my-graph', 'User', 'FOLLOWS')` — loads nodes and relationships into an optimized in-memory format. (2) Run algorithms: PageRank (node importance), Louvain community detection (cluster discovery), Dijkstra shortest path (weighted path), betweenness centrality (bridge nodes). (3) Write results back: stream results to application or write as node properties. GDS uses parallel execution (multi-threaded traversals) for large graphs. Use cases: recommendation systems (community detection → recommend within community), content ranking (PageRank for important documents), fraud detection (betweenness centrality to find money mule nodes in transaction networks).

**Q: What is the supernode problem and how do you mitigate it?**
A supernode is a node with extremely high degree (many relationships) — examples: a celebrity user with 50M followers, a highly-connected product in a recommendation graph, an IP address seen in millions of transactions. Problem: any traversal that reaches a supernode must consider all N edges, even if only a few lead to the answer. For a 3-hop traversal that hits a supernode with 50M relationships at hop 2: 50M edges must be examined. Mitigation strategies: (1) Filter before reaching the supernode (use additional predicates to narrow the traversal earlier). (2) Cap the degree of traversal: use relationship properties to select only recent or high-weight connections. (3) Cache: precompute traversals from/to supernodes as static properties. (4) Avoid using supernodes as traversal waypoints — start traversals from the query-specific node, not from the supernode. (5) Consider not modeling extremely high-degree relationships in the graph at all — use alternative structures.

**Q: What graph databases work at billion-node scale?**
Neo4j: handles billions of nodes on a single server with high RAM (2-4TB RAM servers handle 10B+ nodes). Not horizontally scalable in the traditional sense — Fabric provides federation across multiple Neo4j instances. TigerGraph: natively distributed, handles 100B+ nodes and 1T+ edges across a cluster. Uses parallel graph computation (GSQL parallel traversals). Used by financial institutions for global fraud detection. JanusGraph: distributed graph using Cassandra or HBase as backend storage — horizontally scalable but with higher latency per hop than native graphs. Amazon Neptune: managed, scales to billions of nodes, but latency is higher than on-premise native graphs due to network round trips. At extreme scale (>100B nodes): consider specialized graph processing frameworks like Apache Spark GraphX or Pregel for batch analytics.

**Q: How does Neo4j handle transactions and ACID compliance?**
Neo4j provides full ACID compliance at the graph level. Write transactions: all changes to nodes and relationships within a transaction are atomic (either all committed or all rolled back). WAL (write-ahead log): changes written to WAL before data files, ensuring crash recovery. MVCC: readers do not block writers; each transaction gets a consistent snapshot. Constraint enforcement: unique constraints (on node properties), existence constraints. Transaction timeouts: configurable to prevent long-running transactions from holding locks. Cluster: in a causal cluster, write transactions are applied to the leader (primary), replicated via Raft consensus to followers. Read transactions can be served by followers (potentially slightly stale). `USING PERIODIC COMMIT` (or `CALL { ... } IN TRANSACTIONS OF N ROWS`): batch large write operations to avoid memory exhaustion on large imports.

**Q: What are the index types in Neo4j and how do you choose?**
Neo4j index types: (1) Range index (B+tree based): supports equality, range, prefix, and ordering. Use for: numeric properties (age, price), dates, string prefixes. Default index type. (2) Text index (Lucene-based): full-text search on string properties. Use for: free-text search within graph queries (`MATCH (u:User) WHERE u.bio CONTAINS "database expert"`). (3) Point index: for spatial properties (latitude, longitude). Use for geospatial queries (within distance, bounding box). (4) Composite index: multiple properties in one index for compound queries. (5) Full-text index: Lucene-based search across multiple node labels and properties. Create: `CREATE INDEX idx_user_email FOR (u:User) ON (u.email)`. Always create indexes on properties used in MERGE conditions, MATCH patterns, and WHERE clauses on high-cardinality properties.

**Q: How do you model a hierarchical permission system in a graph database?**
RBAC (Role-Based Access Control) graph schema:
```
(:User)-[:MEMBER_OF]→(:Group)-[:SUBGROUP_OF*]→(:Group) // Nested groups
(:Group)-[:HAS_ROLE]→(:Role)-[:GRANTS]→(:Permission {action, resource})
(:Role)-[:SCOPED_TO]→(:Tenant|Project|Document)
```
Advantages over relational RBAC: inherited permissions through group hierarchies work naturally via variable-depth traversal. Complex permission queries like "what can Alice do?" or "who can delete document 42?" are single Cypher queries — no multi-level SQL JOINs. ABAC (Attribute-Based): add property predicates to the `GRANTS` relationship or permission node (`IF request.time > 9AM AND request.department = "Finance"`). Policy evaluation becomes a graph traversal with property filtering.

**Q: What is the difference between a labeled property graph and an RDF triple store?**
Labeled property graph (LPG — used by Neo4j, Amazon Neptune property graph): nodes and relationships are first-class objects with labels (type tags) and properties (key-value pairs attached directly). Relationships have a direction, type, and properties. Query: Cypher or Gremlin. RDF triple store (used by Amazon Neptune RDF, AllegroGraph): data modeled as subject-predicate-object triples. Attributes are additional triples: `(alice, age, 30)` instead of a property. No native relationship properties — must reify relationships as nodes. Query: SPARQL. RDF follows W3C standards for semantic web and linked data. LPG: more intuitive for application development, better performance for typical graph queries. RDF: better for knowledge graph integration, linked data, ontology reasoning. Many enterprise knowledge graphs use RDF (Google Knowledge Graph, DBpedia).

---

## 13. Best Practices

1. Create indexes on all node properties used in MATCH conditions, MERGE patterns, and WHERE clauses.
2. Always use MERGE for idempotent writes, never CREATE for nodes that should be unique.
3. Avoid supernodes (nodes with millions of relationships) — bound degree with timestamps/weights.
4. Keep relationship types specific and meaningful (PURCHASED is better than RELATED_TO).
5. Model relationships with direction that matches your query direction.
6. Use relationship properties for metadata (timestamps, weights, labels) rather than intermediate nodes.
7. Profile traversal queries with EXPLAIN and PROFILE to catch full graph scans.
8. Limit unbounded variable-length traversals with max depth ([:KNOWS*1..6] not [:KNOWS*]).
9. Use APOC library for utility operations (data import, path finding utilities).
10. For production: use causal cluster (leader + followers) for HA and read scale.

---

## 14. Case Study

**Scenario**: A fintech company needs to detect money laundering networks in real-time as transactions are processed. Traditional rule-based systems flag less than 2% of actual fraud. Need: detect ring transactions (A→B→C→A), layering patterns (>3 hops of rapid transfers), and shared infrastructure (two "unrelated" accounts using the same device/IP).

**Graph schema**:
```cypher
// Nodes:
CREATE (:Account {id: $id, balance: $balance, risk_score: 0, created_at: datetime()})
CREATE (:Person {id: $id, name: $name, kyc_level: $level})
CREATE (:Device {fingerprint: $fp, last_ip: $ip})
CREATE (:BankBranch {id: $id, location: $location})

// Relationships:
CREATE (p:Person)-[:OWNS {since: datetime()}]->(a:Account)
CREATE (a1:Account)-[:SENT_TO {amount: $amount, ts: datetime(), tx_id: $id}]->(a2:Account)
CREATE (p:Person)-[:USES {last_seen: datetime()}]->(d:Device)
```

**Real-time fraud queries** (run on each transaction):
```cypher
// Query 1: Ring detection (3-hop)
MATCH (new_account:Account {id: $new_account_id})
MATCH ring = (new_account)-[:SENT_TO*2..5]->(new_account)
WHERE ALL(r IN relationships(ring) WHERE r.ts > datetime() - duration('P1D'))
RETURN count(ring) > 0 AS is_ring, length(ring) AS ring_length

// Query 2: Shared infrastructure (money mule network)
MATCH (a:Account {id: $account_id})<-[:OWNS]-(p:Person)-[:USES]->(d:Device)<-[:USES]-(p2:Person)-[:OWNS]->(a2:Account)
WHERE a.id <> a2.id AND a2.created_at > datetime() - duration('P30D')
RETURN count(DISTINCT a2) AS suspicious_accounts_sharing_device

// Query 3: Rapid transfer chain (layering)
MATCH path = (a:Account {id: $account_id})-[:SENT_TO*3..10]->(:Account)
WHERE ALL(r IN relationships(path) WHERE r.ts > datetime() - duration('PT1H'))
RETURN length(path) AS chain_length, extract(r IN relationships(path) | r.amount) AS amounts
ORDER BY chain_length DESC LIMIT 5
```

**Performance**:
- Average query time (3-hop ring): 2ms (vs 500ms+ for equivalent PostgreSQL recursive CTE at 10M accounts)
- Real-time scoring: each incoming transaction scored in < 10ms
- Fraud detection rate improved from 2% (rule-based) to 23% (graph patterns)

**Result**: Graph traversal found patterns invisible to rule-based systems — particularly ring transactions that span 5+ hops and money mule networks identified by shared device fingerprints across "unrelated" accounts.
