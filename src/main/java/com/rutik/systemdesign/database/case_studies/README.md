# Database Engineering — Case Studies

Six end-to-end database design case studies, each covering a real-world scenario with production-grade architecture decisions, implementation details, tradeoffs, and interview discussion points.

---

## Case Studies

| Case Study | Databases Used | Core Concepts | Complexity |
|------------|---------------|---------------|------------|
| [Banking Ledger](design_banking_ledger/README.md) | PostgreSQL, Redis | Double-entry bookkeeping, SERIALIZABLE isolation, idempotency, RPO=0 | Expert |
| [E-Commerce Catalog](design_ecommerce_catalog/README.md) | PostgreSQL, Elasticsearch, Redis, ClickHouse | Polyglot persistence, CDC sync, inventory counters, full-text search | Advanced |
| [Social Media Feed](design_social_media_feed_storage/README.md) | Cassandra, PostgreSQL, Redis | Fan-out on write vs read, celebrity problem, TWCS, trending leaderboards | Advanced |
| [Real-Time Analytics](design_realtime_analytics_platform/README.md) | ClickHouse, Kafka, Redis | Columnar storage, materialized views, HyperLogLog, tenant isolation | Advanced |
| [Multi-Tenant SaaS](design_multitenant_saas_database/README.md) | PostgreSQL (RLS/schema/database), PgBouncer | Three-tier isolation, RLS, schema-per-tenant, connection pooling at scale | Expert |
| [Monolith to Polyglot Migration](design_monolith_to_polyglot_migration/README.md) | MySQL → PostgreSQL + Elasticsearch + ClickHouse | Strangler fig, CDC, dual-write, validation, zero-downtime migration | Expert |

---

## Case Study Format

Each case study follows this structure:
- **Problem Statement** — requirements, scale, constraints
- **Architecture Overview** — ASCII diagram of the full data flow
- **Key Design Decisions** — schema, SQL, configuration with rationale
- **Implementation** — Java/SQL code for critical paths
- **Tradeoffs and Alternatives** — decision matrix with alternatives considered
- **Interview Discussion Points** — 4 deep Q&As per case study

---

## Back to Database Section

[Database Engineering Master Index](../README.md)
