# Chapter 2: Data Models and Query Languages

> Part I — Foundations of Data Systems · DDIA (Kleppmann) · builds on Ch 1, leads to Ch 3 (storage)

## Chapter Map

Data models are the most important layer of abstraction: each layer hides the complexity of
the one below by offering a clean model. This chapter compares the three dominant models —
**relational**, **document**, and **graph** — and the query languages that go with them. The
recurring theme: there is no universal best model; the right one depends on the
*relationships* in your data.

**TL;DR:**
- **Relational** wins for many-to-one and many-to-many relationships and joins.
- **Document** wins for one-to-many tree-shaped data with locality and schema flexibility,
  but handles joins poorly.
- **Graph** wins when many-to-many relationships are the norm and highly connected.
- Declarative query languages (SQL, Cypher, SPARQL) beat imperative because the engine — not
  you — chooses the execution strategy and can parallelize.

## The Big Question

> "How should I shape my data — and does the shape of the *relationships* in my domain
> dictate which database family I should reach for?"

Analogy: choosing a data model is like choosing a natural language — each can express most
things, but each makes some thoughts easy and others awkward. Tree-shaped data feels natural
in a document; a web of relationships feels natural in a graph; tabular, normalized data
feels natural in the relational model.

---

## 2.1 Relational Model Versus Document Model

### The birth of NoSQL

The relational model (Codd, 1970; dominant since the 1980s) organizes data into *relations*
(tables) of *tuples* (rows). NoSQL (a 2010s hashtag-turned-movement) arose from: the need for
greater scale (very large datasets, very high write throughput); a preference for free,
open-source software; specialized query operations the relational model handles poorly; and
frustration with the rigidity of relational schemas. The likely future is **polyglot
persistence** — relational and non-relational used together.

### The object-relational mismatch

Application code is objects; relational tables are rows. The awkward translation layer
between them is the **impedance mismatch**, partially papered over by ORMs (Hibernate,
ActiveRecord). For a self-contained document (e.g. a LinkedIn résumé — one person, many jobs,
many education entries), a JSON document model reduces this mismatch: the tree of one-to-many
relationships becomes one nested document instead of multiple joined tables. JSON also
provides better **locality** — the whole résumé is fetched in one read.

### Many-to-one and many-to-many relationships

The catch: real data has many-to-*one* relationships (many people live in one region; many
people have the same industry). Normalizing these means using an **ID** (not duplicated text)
so that the human-readable value lives in one place — good for consistency, avoids write
anomalies, and supports localization/search. But IDs require a **join** to resolve. Document
databases handle one-to-many (trees) well but **joins weakly** — you either denormalize (and
accept update anomalies) or emulate joins in application code. As features grow, data tends
to become more interconnected (many-to-many), which favors the relational/graph models.

### Historical echo: the network and hierarchical models

The 1970s debate repeats. IMS's **hierarchical model** (trees, like JSON documents) struggled
with many-to-many. The **network model (CODASYL)** used access paths (manual pointer chasing)
— flexible but a nightmare to query and evolve. The **relational model** won by laying data
out plainly and letting a **query optimizer** decide access paths automatically. Document
databases are, in a sense, the hierarchical model reborn — same strengths, same weakness with
many-to-many.

### Relational vs document today

- **Document strengths:** schema flexibility, locality (read the whole doc at once), closer
  to app object structure for tree data.
- **Relational strengths:** joins, many-to-one and many-to-many relationships.
- **Locality caveat:** locality only helps if you need most of the document at once;
  databases usually load the *whole* document even for a small field, which is wasteful on
  large documents. Updates typically rewrite the whole document, so keep documents small.
- **Convergence:** relational DBs added JSON/XML column types and document-like features;
  document DBs added join-like references. The models are converging, and a hybrid is likely
  the future of mainstream databases.

### Schema-on-read vs schema-on-write

Document DBs are often called "schemaless," but more precisely they use **schema-on-read**
(structure is interpreted when data is read — like dynamic typing) versus the relational
**schema-on-write** (schema enforced at write time — like static typing). Schema-on-read
shines when items are heterogeneous or the structure comes from external systems you don't
control. Schema-on-write shines when you want guarantees and the structure is uniform.
Changing a field's shape: in a document DB you just start writing the new shape and handle
both at read time; in a relational DB you run a migration (`ALTER TABLE`, possibly a slow
`UPDATE`).

## 2.2 Query Languages for Data

### Declarative versus imperative

An **imperative** language (most programming languages, CODASYL navigation) tells the machine
*how* to do something, step by step. A **declarative** language (SQL, relational algebra)
specifies *what* result you want and lets the engine decide *how*. Declarative wins because:
it hides engine internals (the optimizer can change the execution plan without app changes);
it's more concise; and crucially it leaves room for **automatic parallelization** across
cores/machines, because you didn't pin a sequential order of operations. Kleppmann's CSS/DOM
analogy: declarative CSS selectors let the browser optimize rendering; doing the same in
imperative JavaScript would be verbose, brittle, and unparallelizable.

### MapReduce querying

**MapReduce** (popularized by Google) is a programming model for bulk processing across
many machines — a midpoint between declarative and imperative. You supply two pure
(side-effect-free) functions, `map` and `reduce`; the framework handles distribution. MongoDB
offered MapReduce for aggregation, but its declarative **aggregation pipeline** later proved
easier for most queries. The lesson: even where you must drop to imperative snippets for
custom logic, a declarative wrapper is preferable for the common cases.

## 2.3 Graph-Like Data Models

When many-to-many relationships dominate and data is highly connected (social graphs, the web,
road networks), a **graph** is the natural model. Vertices (nodes) + edges (relationships).
Two well-known graph models:

### Property graphs (and Cypher)

Each **vertex** has an ID, a set of outgoing/incoming edges, and a collection of
key-value properties. Each **edge** has an ID, a tail/head vertex, a label (relationship
type), and properties. This is very flexible: any vertex can connect to any other; you
traverse by following edges in either direction; you can mix many relationship types in one
graph. **Cypher** (Neo4j's declarative language) expresses traversals like
"find people who emigrated from the US to Europe" as a pattern of nodes and edges; the engine
finds matching paths regardless of length. The same query in SQL needs **recursive common
table expressions** (`WITH RECURSIVE`) and is far more verbose, because the number of joins
is not fixed in advance — exactly where SQL is awkward and graph queries shine.

### Triple-stores and SPARQL

The **triple-store** model expresses everything as three-part statements:
*(subject, predicate, object)* — e.g. *(Jim, likes, bananas)* or *(Lucy, age, 33)*. The
object is either a value (a property) or another vertex (an edge). This underlies the
**Semantic Web** and **RDF**. **SPARQL** is the declarative query language for triple-stores
(predates and inspired Cypher). Triples are a compact, uniform way to encode a property graph.

### Datalog

**Datalog** is an older (1980s) foundation: data as facts `predicate(subject, object)`, and
queries as **rules** that derive new predicates from existing ones, *recursively*. Rules
compose and can refer to one another, which makes complex queries built up from small pieces.
It's less convenient for one-offs but powerful for sophisticated, reusable query logic, and it
underlies systems like Datomic and Cascalog.

---

## Visual Intuition

```
WHICH MODEL FITS WHICH RELATIONSHIP SHAPE?

  one-to-many (a tree)            many-to-one / many-to-many (a web)
                                          ┌──────┐
      résumé                              │ Alice│───works_at──▶┌────────┐
      ├─ job1                             └──┬───┘              │ AcmeCo │
      ├─ job2                  follows│      │             ┌───▶└────────┘
      └─ education                    ▼      ▼             │
         ├─ school1               ┌──────┐ ┌──────┐  works_at
         └─ school2               │ Bob  │ │ Carol│──────┘
                                  └──────┘ └──────┘
  ◀── DOCUMENT wins ──▶            ◀──── RELATIONAL / GRAPH win ────▶
  (locality, schema flex,         (joins resolve shared entities once;
   maps to app objects)            graph excels when edges are the point)
```

```
DECLARATIVE LETS THE ENGINE PARALLELIZE; IMPERATIVE PINS THE ORDER

  imperative:  for row in table:          ← you fixed a sequential loop;
                   if row.color=="red":     the engine cannot safely split it
                       result.append(row)    across cores without proving it's safe

  declarative: SELECT * WHERE color='red' ← no order specified ⇒ optimizer is free to
                                             scan in parallel, use an index, reorder joins
```

Caption: the two load-bearing ideas of the chapter — relationship shape picks the model, and
declarative queries hand the engine the freedom to optimize and parallelize.

---

## Key Concepts Glossary

- **Data model** — the abstraction defining how data is structured and queried.
- **Relational model** — data as relations (tables) of tuples (rows); joins resolve refs.
- **Document model** — self-contained nested documents (JSON/BSON/XML); trees.
- **NoSQL** — umbrella for non-relational stores driven by scale, OSS, specialized queries,
  and schema flexibility.
- **Polyglot persistence** — using multiple datastore types in one application.
- **Impedance mismatch (object-relational)** — friction translating app objects to rows.
- **Locality** — storing related data physically together for single-read access.
- **Normalization** — storing each fact once and referencing it by ID to avoid duplication.
- **Denormalization** — duplicating data for read speed, at the cost of update anomalies.
- **One-to-many / many-to-one / many-to-many** — the relationship cardinalities that drive
  model choice.
- **Schema-on-read** — structure interpreted at read time (dynamic typing analogy).
- **Schema-on-write** — structure enforced at write time (static typing analogy).
- **Declarative query** — specifies the result (SQL, Cypher, SPARQL); engine picks the plan.
- **Imperative query** — specifies the steps; pins execution order.
- **MapReduce** — bulk-processing model using pure map and reduce functions.
- **Aggregation pipeline** — MongoDB's declarative alternative to MapReduce.
- **Property graph** — vertices and edges, each with labels and key-value properties.
- **Cypher** — declarative query language for property graphs (Neo4j).
- **Triple-store** — data as (subject, predicate, object) statements; RDF; queried by SPARQL.
- **Datalog** — rule-based, recursive query foundation; composable derived predicates.
- **Recursive CTE (`WITH RECURSIVE`)** — SQL's way to express variable-length graph traversal.

---

## Tradeoffs & Decision Tables

| | Relational | Document | Graph |
|---|---|---|---|
| Best relationship shape | Many-to-one, many-to-many | One-to-many (trees) | Many-to-many, highly connected |
| Joins | Strong, optimizer-chosen | Weak (app-side or denormalize) | Native traversal |
| Schema | Schema-on-write | Schema-on-read | Flexible per vertex/edge |
| Locality | Per-row | Whole document in one read | Per vertex |
| Typical use | OLTP, reporting, anything tabular | Self-contained docs, catalogs | Social, fraud, recommendations, networks |

| | Schema-on-read | Schema-on-write |
|---|---|---|
| Analogy | Dynamic typing | Static typing |
| Best when | Heterogeneous items; external structure | Uniform structure; want guarantees |
| Change a field | Write new shape, branch at read | Migration (`ALTER TABLE` / `UPDATE`) |

| | Declarative (SQL/Cypher) | Imperative (app loop/CODASYL) |
|---|---|---|
| Specifies | What result | How, step by step |
| Optimization | Engine chooses plan | Fixed by you |
| Parallelizable | Yes (no pinned order) | Hard (order is pinned) |

---

## Common Pitfalls / War Stories

- **Denormalizing into documents, then drowning in update anomalies.** Embedding a region
  *name* in every user document means renaming the region requires rewriting millions of
  documents — and any you miss are now inconsistent. Use an ID and resolve it; accept the
  join.
- **Modeling many-to-many in a document store.** Order items referencing products, products
  referencing categories, categories referencing… you end up emulating joins in application
  code, which is slow and error-prone. This is the historical hierarchical-model weakness
  resurfacing; reach for relational or graph instead.
- **Giant documents.** Because most document DBs load and rewrite the *whole* document, a
  multi-megabyte document makes every small update expensive and every read wasteful. Keep
  documents bounded; locality is a benefit only when you usually need the whole thing.
- **Writing variable-depth traversals in SQL without recursion.** "Friends of friends of
  friends" with a fixed number of joins breaks the moment depth varies; you need
  `WITH RECURSIVE` (verbose) or, better, a graph database with Cypher.
- **Believing "schemaless" means no schema.** There is always an implicit schema — it's just
  enforced by your application at read time. Undocumented schema-on-read becomes a minefield
  of "which version of this document am I looking at?"

---

## Real-World Systems Referenced

Oracle/DB2/SQL Server/PostgreSQL/MySQL (relational), MongoDB/RethinkDB/CouchDB/Espresso
(document), IMS (hierarchical), CODASYL (network), Neo4j/Titan/InfiniteGraph (property
graphs), Datomic/AllegroGraph (triple-stores/RDF), Datomic/Cascalog (Datalog), Hibernate/
ActiveRecord (ORMs), Google MapReduce, CSS/XSL (declarative analogy).

---

## Summary

Data models shape how you think about a problem. The relational, document, and graph models
each fit a different *relationship shape*: documents for one-to-many trees (with locality and
schema flexibility but weak joins), relational for many-to-one and many-to-many (joins
resolved by an optimizer), and graphs for highly connected many-to-many data (native
traversal via Cypher/SPARQL/Datalog). The schemaless/schema debate is really schema-on-read
(dynamic) vs schema-on-write (static). Declarative query languages beat imperative ones
because they let the engine choose and parallelize the execution plan — the same reason the
relational model defeated the navigational CODASYL model decades ago. The history rhymes:
document databases reprise the hierarchical model's strengths and its many-to-many weakness.

---

## Interview Questions

**Q: When would you choose a document database over a relational one, and what is the main thing you give up?**
Choose a document database when your data is a self-contained tree of one-to-many relationships (a résumé, a product with its variants) that you usually read all at once — you gain locality, schema flexibility, and a closer match to application objects. What you give up is good support for joins and many-to-many relationships; resolving references means denormalizing (risking update anomalies) or emulating joins in application code. As data grows more interconnected, that weakness dominates.

**Q: What is the object-relational impedance mismatch, and how does the document model reduce it?**
It's the awkward translation between application objects (nested, with collections) and the flat rows/columns of the relational model, usually bridged by an ORM. The document model reduces it for tree-shaped data because a nested JSON document mirrors the object's structure directly — a person with many jobs and schools is one document, not several joined tables. It does not eliminate the mismatch for many-to-many data, where you still need references.

**Q: Explain schema-on-read versus schema-on-write and give a scenario favoring each.**
Schema-on-read interprets structure when data is read (like dynamic typing) and suits heterogeneous data or data whose structure is dictated by external systems you don't control — e.g. ingesting varied third-party events. Schema-on-write enforces structure at write time (like static typing) and suits uniform data where you want guarantees and the ability to reason about every row — e.g. a financial ledger. The distinction reframes "schemaless" as "schema enforced later," because there's always an implicit schema.

**Q: Why did the relational model win over the navigational CODASYL/network model, and how does that history apply to document databases?**
The relational model laid data out plainly and let a query optimizer choose access paths automatically, whereas CODASYL forced programmers to hand-navigate pointer chains, which was rigid and broke whenever the data layout changed. Document databases echo the older hierarchical model: great for tree-shaped one-to-many data, weak at many-to-many — so the same limitation that hurt hierarchical databases resurfaces, and many-to-many data again pushes you toward relational or graph models.

**Q: Why are declarative query languages generally preferable to imperative ones?**
Because a declarative query states *what* result you want, not *how* to compute it, the engine is free to choose the best execution plan, change it as data/indexes evolve without touching app code, and parallelize across cores and machines since no sequential order is pinned. Imperative code fixes the order and hides intent, so the engine can't safely optimize or parallelize it. SQL's declarativeness is precisely what lets the same query keep working as the optimizer and storage improve.

**Q: When is a graph model clearly better than a relational one, and why is the equivalent SQL awkward?**
A graph model is clearly better when many-to-many relationships dominate and you traverse variable-length paths (social networks, fraud rings, routing). The equivalent SQL is awkward because the number of joins isn't known in advance — "people connected to X within N hops" needs recursive CTEs (`WITH RECURSIVE`), which are verbose and hard to read, whereas Cypher expresses the same as a concise node-edge pattern the engine matches at any path length.

**Q: Describe the property-graph model's components.**
A property graph has vertices and edges. Each vertex carries a unique ID, a set of outgoing and incoming edges, and a collection of key-value properties. Each edge carries a unique ID, its tail (start) and head (end) vertices, a label naming the relationship type, and its own key-value properties. This structure lets any vertex connect to any other and lets multiple relationship types coexist in one graph, which is what makes traversal so flexible.

**Q: What is a triple-store, and how does it relate to a property graph?**
A triple-store represents all data as three-part statements: (subject, predicate, object), such as (Lucy, age, 33) or (Lucy, marriedTo, Alain). When the object is a primitive value the triple is a property; when it's another subject the triple is an edge. This is essentially a compact, uniform encoding of a property graph and underlies RDF and the Semantic Web, queried with SPARQL.

**Q: What problem does locality solve in document databases, and when does it backfire?**
Locality means a document's related data is stored physically together, so loading it takes one read instead of multiple joins — great when you typically need most of the document at once. It backfires for large documents because the database generally loads the *entire* document even to read one field, and updates usually rewrite the whole document, so big documents make reads wasteful and writes expensive. The guidance is to keep documents reasonably small.

**Q: Why does normalization require joins, and what's the tradeoff against denormalization?**
Normalization stores each fact once and references it by ID (so a region name lives in one row), which means resolving the human-readable value requires a join back to that row. Denormalization duplicates the value into many records to avoid the join, speeding reads but creating update anomalies — changing the value means finding and rewriting every copy, and missing one causes inconsistency. The choice trades read simplicity against write consistency.

**Q: What is MapReduce as a query mechanism, and why did MongoDB add a declarative aggregation pipeline alongside it?**
MapReduce is a bulk-processing model where you supply pure `map` and `reduce` functions and the framework distributes the work across machines — a middle ground between declarative and imperative. MongoDB initially exposed MapReduce for aggregation but added the declarative aggregation pipeline because, for most queries, expressing intent declaratively is more concise and lets the engine optimize, reserving imperative functions for genuinely custom logic.

**Q: What is Datalog and what makes it powerful for complex queries?**
Datalog stores data as simple facts (`predicate(subject, object)`) and expresses queries as rules that derive new predicates from existing ones, including recursively. Its power is composability: you build complex queries from small, named, reusable rules that can reference each other, which scales to sophisticated logic better than writing one giant query. It's less convenient for quick one-offs but excellent for reusable analytical logic, and it underlies systems like Datomic.

**Q: What does "polyglot persistence" mean and why is it the likely future?**
Polyglot persistence is using several different datastore types within one application, each for the workload it fits — a relational DB for transactions, a document store for catalogs, a graph DB for recommendations, a search index for full-text. It's the likely future because no single model is best for every relationship shape and access pattern, and the models are converging (relational DBs gaining JSON, document DBs gaining references) rather than one winning outright.

**Q: Why does data tend to become more interconnected over a system's lifetime, and what does that imply for model choice?**
As features accumulate, entities that started independent acquire references to each other — users gain followers, posts gain tags, products gain related-product links — so one-to-many trees evolve into many-to-many webs. This implies that a document model chosen early for its simplicity may need to give way to relational or graph models as relationships proliferate, so it's worth anticipating which relationships will grow rather than optimizing only for today's shape.

**Q: How do relational and document databases converge, and why does that matter for choosing one?**
They converge because relational databases have added JSON/XML column types and document-like nested storage, while document databases have added references and join-like lookups. This matters because the choice is less binary than it appears: you can get document-style flexibility inside PostgreSQL or relational-style references inside MongoDB, so you should choose based on which model is *primary* for your dominant access pattern rather than assuming you must forgo the other's features entirely.

---

## Cross-links in this repo

- [database/schema_design_and_normalization/ — normal forms, JSONB, multi-tenancy](../../../database/schema_design_and_normalization/README.md)
- [database/document_databases/ — MongoDB internals, embedding vs referencing](../../../database/document_databases/README.md)
- [database/graph_databases/ — Neo4j, index-free adjacency, Cypher](../../../database/graph_databases/README.md)
- [database/sql_query_optimization/ — how the declarative optimizer chooses a plan](../../../database/sql_query_optimization/README.md)

## Further Reading

- Kleppmann, DDIA Ch 2 — original text and references.
- Codd, "A Relational Model of Data for Large Shared Data Banks," 1970 — the founding paper.
- Robinson, Webber & Eifrem, *Graph Databases* — property graphs and Cypher in depth.
