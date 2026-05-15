# Schema Design and Normalization

## 1. Concept Overview

Schema design determines how data is structured in tables, what relationships exist, and what constraints enforce correctness. Normalization is the formal process of organizing columns and tables to reduce redundancy and preserve data integrity. Denormalization is the deliberate reversal — duplicating data or combining tables to improve read performance at the cost of update complexity.

---

## 2. Intuition

Normalization is like a well-organized filing system: each piece of information has exactly one place, so you never have contradictory information. Denormalization is like keeping frequently-needed information on sticky notes everywhere — faster to find, but you must update all copies when it changes.

- **Key insight**: Normalize first, denormalize only when you have measured a performance problem. Most premature denormalization causes data inconsistency bugs that are harder to fix than slow queries.

---

## 3. Core Principles

### Normal Forms

**First Normal Form (1NF)**:
- Each column contains atomic (indivisible) values
- No repeating groups (no arrays in a column)

```sql
-- Violates 1NF: multiple values in one column
orders (id, products)
VALUES (1, 'book,pen,paper');  -- WRONG: comma-separated list

-- 1NF compliant:
order_items (order_id, product_id, quantity)
```

**Second Normal Form (2NF)**:
- Is in 1NF
- Every non-key attribute is fully functionally dependent on the entire primary key (no partial dependencies)
- Only relevant when primary key is composite

```sql
-- Violates 2NF: supplier_name depends on supplier_id, not the full (supplier_id, product_id) PK
order_details (supplier_id, product_id, quantity, supplier_name)  -- WRONG

-- 2NF compliant:
order_details (supplier_id, product_id, quantity)
suppliers (supplier_id, supplier_name)
```

**Third Normal Form (3NF)**:
- Is in 2NF
- No transitive dependencies (non-key attributes do not depend on other non-key attributes)

```sql
-- Violates 3NF: zip_code → city (zip determines city, transitive dependency)
employees (id, name, zip_code, city, state)  -- WRONG

-- 3NF compliant:
employees (id, name, zip_code)
zip_codes (zip_code, city, state)
```

**Boyce-Codd Normal Form (BCNF)**:
- Stronger than 3NF: every determinant must be a candidate key
- Handles anomalies 3NF misses with overlapping composite candidate keys (rare in practice)

**Fourth Normal Form (4NF)**:
- Eliminates multi-valued dependencies (column A → column B independently of column C)

---

## 4. Types / Architectures / Strategies

### Data Types: Choosing Correctly

**Integer types**:
```sql
SMALLINT   -- 2 bytes, -32768 to 32767. Use for: age, year, status codes
INTEGER    -- 4 bytes, -2.1B to 2.1B. Use for: most IDs if < 2B rows
BIGINT     -- 8 bytes, ±9.2×10^18. Use for: high-volume tables, user_id at scale, financial amounts (cents)
SERIAL     -- 4-byte auto-increment integer (PostgreSQL)
BIGSERIAL  -- 8-byte auto-increment integer (PostgreSQL)
```

**String types**:
```sql
VARCHAR(n) -- Variable up to n characters. Enforces length. Use when max length is business-defined (email, phone)
TEXT       -- Unlimited variable. PostgreSQL: same storage as VARCHAR(n), no performance difference
CHAR(n)    -- Fixed-length, pads with spaces. Avoid unless fixed-width protocol field (legacy)
-- PostgreSQL tip: VARCHAR without (n) is equivalent to TEXT
```

**Date/Time types**:
```sql
TIMESTAMPTZ  -- Timestamp with timezone (stores UTC internally, displays in session timezone)
             -- ALWAYS use for application timestamps — prevents timezone bugs
TIMESTAMP    -- Without timezone — ambiguous, avoid for user-facing data
DATE         -- Date only, no time
TIME         -- Time only, no date
INTERVAL     -- Duration ('7 days', '2 hours')
```

**JSON types (PostgreSQL)**:
```sql
JSON     -- Stores raw JSON text, validates syntax on insert. Slower queries (re-parses each time)
JSONB    -- Stores parsed binary representation. Faster queries, supports indexing, slightly larger storage
         -- Use JSONB always unless you need to preserve key ordering or duplicate keys
```

### Temporal Data Patterns

**Simple effective dating** (one dimension):
```sql
-- Track changes over time
CREATE TABLE product_prices (
    product_id  BIGINT NOT NULL,
    valid_from  TIMESTAMPTZ NOT NULL,
    valid_to    TIMESTAMPTZ,          -- NULL means current
    price       NUMERIC(10,2) NOT NULL,
    PRIMARY KEY (product_id, valid_from)
);

-- Current price:
SELECT price FROM product_prices
WHERE product_id = 42 AND valid_to IS NULL;

-- Price at a point in time:
SELECT price FROM product_prices
WHERE product_id = 42 AND valid_from <= '2024-06-15' AND (valid_to IS NULL OR valid_to > '2024-06-15');
```

**Bi-temporal tables** (two time dimensions):
- **Valid time**: when the fact was true in reality (business time)
- **Transaction time**: when the fact was recorded in the database

```sql
-- Bi-temporal: handle retroactive corrections
CREATE TABLE employee_salary (
    employee_id         BIGINT NOT NULL,
    valid_from          DATE NOT NULL,   -- When this salary was/is effective (business time)
    valid_to            DATE NOT NULL,   -- End of effectiveness (9999-12-31 for current)
    recorded_from       TIMESTAMPTZ NOT NULL,  -- When we knew this (system time)
    recorded_to         TIMESTAMPTZ NOT NULL,  -- When this record was superseded
    salary              NUMERIC(12,2) NOT NULL,
    PRIMARY KEY (employee_id, valid_from, recorded_from)
);

-- PostgreSQL 16+ has temporal table support (SQL:2011 temporal tables)
-- SQL Server has built-in system-versioned temporal tables
```

### Audit Trail Patterns

**Separate audit table**:
```sql
-- Works: captures all changes, who made them, when
CREATE TABLE orders_audit (
    audit_id    BIGSERIAL PRIMARY KEY,
    order_id    BIGINT NOT NULL,
    operation   CHAR(1) NOT NULL CHECK (operation IN ('I','U','D')),
    changed_by  TEXT NOT NULL DEFAULT current_user,
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    old_data    JSONB,
    new_data    JSONB
);

-- Trigger to populate:
CREATE OR REPLACE FUNCTION audit_orders() RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO orders_audit (order_id, operation, old_data, new_data)
    VALUES (
        COALESCE(NEW.id, OLD.id),
        TG_OP::CHAR(1),
        CASE WHEN TG_OP != 'INSERT' THEN to_jsonb(OLD) END,
        CASE WHEN TG_OP != 'DELETE' THEN to_jsonb(NEW) END
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER orders_audit_trigger
AFTER INSERT OR UPDATE OR DELETE ON orders
FOR EACH ROW EXECUTE FUNCTION audit_orders();
```

### Soft Delete

```sql
-- Soft delete pattern:
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ;

-- "Active" means deleted_at IS NULL
-- Queries need WHERE deleted_at IS NULL

-- Pitfall: UNIQUE constraint on email no longer works for soft-deleted rows!
-- Fix: partial unique index
CREATE UNIQUE INDEX idx_users_email_active
ON users (email) WHERE deleted_at IS NULL;

-- Permanent query filter via row-level security or view:
CREATE VIEW active_users AS
SELECT * FROM users WHERE deleted_at IS NULL;

-- OR row-level security policy:
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
CREATE POLICY show_active ON users USING (deleted_at IS NULL);
```

### Multi-Tenancy Schema Patterns

| Pattern | Description | Isolation | Query Complexity | Migration | Connection Overhead |
|---------|-------------|-----------|-----------------|-----------|--------------------|
| Database-per-tenant | Separate DB per tenant | Complete | Simple | Per-tenant | Very High |
| Schema-per-tenant | Separate schema per tenant | Strong | Simple (set search_path) | Per-schema | High (connection pools) |
| Shared schema | `tenant_id` column in every table | Weak (RLS) | Added WHERE clause | Global | Low |

```sql
-- Shared schema with RLS:
ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON orders
    USING (tenant_id = current_setting('app.tenant_id')::BIGINT);

-- Application sets tenant context on connection:
SET app.tenant_id = '42';
-- All subsequent queries automatically filter by tenant_id = 42

-- Problem: tenant_id must be on every table and every query
-- Missing tenant_id index is catastrophic (full table scan for every query)
CREATE INDEX idx_orders_tenant ON orders (tenant_id);
```

### EAV Anti-Pattern

Entity-Attribute-Value (EAV): store arbitrary attributes as rows.

```sql
-- EAV schema (anti-pattern):
CREATE TABLE product_attributes (
    product_id  BIGINT,
    attr_name   TEXT,
    attr_value  TEXT
);
INSERT INTO product_attributes VALUES (1, 'weight_kg', '2.5');
INSERT INTO product_attributes VALUES (1, 'color', 'red');

-- Problem: Query "products with weight_kg > 2 AND color = 'red'":
SELECT p.id FROM products p
JOIN product_attributes w ON w.product_id = p.id AND w.attr_name = 'weight_kg' AND w.attr_value::NUMERIC > 2
JOIN product_attributes c ON c.product_id = p.id AND c.attr_name = 'color' AND c.attr_value = 'red';
-- Two self-joins, type casts, no index on value, horrible performance

-- Better alternatives:
-- 1. JSONB column for flexible attributes:
ALTER TABLE products ADD COLUMN attributes JSONB;
UPDATE products SET attributes = '{"weight_kg": 2.5, "color": "red"}' WHERE id = 1;
CREATE INDEX idx_products_attrs ON products USING GIN (attributes);
-- Query: WHERE attributes @> '{"color": "red"}' AND (attributes->>'weight_kg')::NUMERIC > 2

-- 2. Typed columns for known attributes:
ALTER TABLE products ADD COLUMN weight_kg NUMERIC;
ALTER TABLE products ADD COLUMN color TEXT;

-- 3. Sparse columns table + JSONB for unknowns:
-- Known fields as columns, catch-all JSONB for custom fields
```

---

## 5. Architecture Diagrams

```
NORMALIZATION LEVELS:

Un-normalized:
+------------------------------------------+
| order_id | customer_name | items         |
+----------+---------------+---------------+
|    1     | Alice Smith   | book,pen,tape |
|    2     | Bob Jones     | pen           |
+------------------------------------------+

1NF (atomic values, no repeating groups):
+----------+------------+------------+----------+
| order_id | product_id | customer_id| quantity |
+----------+------------+------------+----------+
|    1     |     10     |    100     |    1     |
|    1     |     11     |    100     |    1     |
|    1     |     12     |    100     |    1     |
|    2     |     11     |    200     |    1     |
+----------+------------+------------+----------+

2NF (split out partial dependency: customer → customer table):
orders:       (order_id, customer_id, order_date)
order_items:  (order_id, product_id, quantity)
customers:    (customer_id, name, email)
products:     (product_id, name, price)

3NF (already in 3NF — no transitive dependencies remain)
```

---

## 6. How It Works — Detailed Mechanics

### Choosing Data Types for Performance

```sql
-- Financial amounts: use INTEGER storing cents, not DECIMAL
-- Floating point (REAL, DOUBLE) should NEVER store money

-- Bad:
CREATE TABLE payments (amount DOUBLE PRECISION);
INSERT INTO payments VALUES (0.1 + 0.2);  -- Stores 0.30000000000000004

-- Good:
CREATE TABLE payments (amount_cents BIGINT NOT NULL);  -- Store in cents
-- 1.99 USD stored as 199
-- Division and rounding done explicitly in application

-- Or: use NUMERIC (exact decimal, slower than integer)
CREATE TABLE payments (amount NUMERIC(12,2) NOT NULL);  -- Up to 9,999,999,999.99
```

### Foreign Keys and Referential Integrity

```sql
-- Always define FK constraints for referential integrity
CREATE TABLE orders (
    id          BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    -- ON DELETE RESTRICT: prevent deleting customers who have orders
    -- ON DELETE CASCADE: delete orders when customer is deleted
    -- ON DELETE SET NULL: set customer_id to NULL when customer is deleted

    product_id  BIGINT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    total       NUMERIC(12,2) NOT NULL CHECK (total >= 0)
);

-- Index FK columns! Without index, every DELETE from parent table causes full scan of child
CREATE INDEX idx_orders_customer ON orders (customer_id);
CREATE INDEX idx_orders_product ON orders (product_id);
```

### JSONB vs Normalized Columns Decision Matrix

| Use case | JSONB | Normalized column |
|----------|-------|-------------------|
| Schema known at design time | | Yes |
| Schema varies per row | Yes | |
| Need to filter/sort on value | | Yes (indexed column is faster) |
| Need containment queries | Yes (GIN index) | |
| Need to enforce type | | Yes (column type) |
| High cardinality attribute with index | | Yes |
| Arbitrary user-defined attributes | Yes | |
| Need aggregate on value (SUM, AVG) | | Yes |

---

## 7. Real-World Examples

- **Shopify product variants**: Product attributes (size, color, material) stored in JSONB per variant. Known attributes (price, inventory_quantity, SKU) are normalized columns with indexes.
- **Stripe events table**: Append-only. `data JSONB` stores the full event payload. Normalized columns: `id`, `type`, `created`, `livemode`. Queried by `type` and `created` — both indexed normalized columns.
- **GitHub pull requests**: Soft delete with `deleted_at`. Partial unique index on `(repo_id, number) WHERE deleted_at IS NULL`.
- **Multi-tenant SaaS**: Shared schema with `tenant_id` everywhere + RLS. Tenants with large data volumes get dedicated schema or database.

---

## 8. Tradeoffs

| Approach | Write Performance | Read Performance | Consistency | Flexibility |
|----------|-----------------|-----------------|-------------|-------------|
| Fully normalized | Best (one write) | Poor (many joins) | Best (no duplication) | Poor (schema changes needed) |
| Partially denormalized | Medium | Good | Good (limited duplication) | Medium |
| JSONB flexible schema | Good | Medium (GIN index) | Enforced by app | Excellent |
| EAV | Poor (multiple rows) | Terrible | Poor (app-level only) | Excellent |

---

## 9. When to Use / When NOT to Use

**When to normalize (3NF)**:
- OLTP systems with high write rates
- Data integrity is critical (financial, healthcare)
- Schema is relatively stable
- Joins are acceptable (indexes make them fast)

**When to denormalize deliberately**:
- Read-heavy, write-rare (e.g., product catalog)
- Join cost measured and proven to be the bottleneck
- Write is centralized (one service owns updates)

**When to use JSONB**:
- Attributes are truly flexible and unknown at design time
- User-configurable fields
- Event payloads that vary by event type

**When NOT to use EAV**:
- Almost never — JSONB provides the same flexibility with far better query performance

---

## 10. Common Pitfalls

**Pitfall 1: Using TEXT for timestamps**
```sql
-- Broken: string comparisons don't sort correctly
CREATE TABLE logs (ts TEXT, message TEXT);
INSERT INTO logs VALUES ('2024-01-15 10:30', 'event1');
INSERT INTO logs VALUES ('2024-02-01 08:00', 'event2');
-- String comparison '2024-02-01' > '2024-01-15' works accidentally
-- But: '2024-12-01' < '2024-2-01' (string sort!) — broken

-- Fix: always use TIMESTAMPTZ
CREATE TABLE logs (ts TIMESTAMPTZ NOT NULL DEFAULT now(), message TEXT);
```

**Pitfall 2: Not indexing foreign key columns**
```sql
-- Without index on orders.customer_id:
DELETE FROM customers WHERE id = 42;
-- InnoDB: full scan of orders table to check FK constraint → table lock
-- Fix:
CREATE INDEX idx_orders_customer_id ON orders (customer_id);
```

**Pitfall 3: Storing amounts in FLOAT/DOUBLE**
A payments system stored `amount DOUBLE PRECISION`. Over 3 years, rounding errors accumulated in financial reconciliation. $0.01 discrepancies per transaction × 100M transactions = $1M unreconciled difference. Fix: migrate to `amount_cents BIGINT` (integer cents). Never use floating-point for money.

**Pitfall 4: Unique constraint bypassed by soft delete**
Users can re-register with a deleted account's email because `UNIQUE (email)` doesn't distinguish deleted from active users. Fix: `CREATE UNIQUE INDEX ON users (email) WHERE deleted_at IS NULL`.

**Pitfall 5: schema-per-tenant approach with 10,000 tenants**
Each schema needed its own connection pool (min 2 connections per pool = 20,000 connections to PostgreSQL, well above `max_connections=500`). Fix: shared schema with RLS, or PgBouncer with schema-level pool sharing.

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| `pg_dump --schema-only` | Export schema for review/version control |
| `pgBadger` | Identify queries that fail to use normalized schema indexes |
| `liquibase` / `flyway` | Schema version control and migration |
| `pgTAP` | Test schema constraints and data integrity |
| `pgAdmin` ERD | Visual entity-relationship diagram |
| `SchemaSpy` | Generate ERD from existing schema |
| `atlas` | Modern schema-as-code migration tool |

---

## 12. Interview Questions with Answers

**When is denormalization the right call?**
Denormalization is right when: (1) You've measured that a specific join is the bottleneck (EXPLAIN ANALYZE shows expensive nested loops or hash joins on hot queries). (2) The data is read far more often than written (product catalog: read 10,000 times per write). (3) The denormalized copy is maintained by exactly one service/code path (no risk of partial updates). (4) The performance gain is significant (10x+ query speedup) and cannot be achieved by indexing. Wrong reasons: "joins might be slow someday," premature optimization before measuring, or convenience.

**How do you model bi-temporal data and why would you need it?**
Bi-temporal data tracks two independent time dimensions: valid time (when the fact was true in the real world) and transaction time (when the database recorded it). Needed when: correcting historical data retroactively — if a salary raise was backdated to January but recorded in March, you need to capture both the valid date (January) and the recording date (March). Schema: `valid_from DATE, valid_to DATE, recorded_from TIMESTAMPTZ, recorded_to TIMESTAMPTZ`. Queries like "what did we know in March about the January state" require checking both time ranges simultaneously. PostgreSQL 16+ adds `PERIOD FOR` syntax for temporal primary keys and foreign keys (SQL:2011).

**What are the tradeoffs of schema-per-tenant vs shared schema in a SaaS product?**
Schema-per-tenant: each tenant has a separate PostgreSQL schema (`search_path` isolation). Advantages: complete data isolation (no cross-tenant query risk), simple tenant deletion (DROP SCHEMA), per-tenant indexes. Disadvantages: connection pool complexity (different schemas need different connections), PostgreSQL handles 10,000+ schemas but planning overhead grows, cross-tenant reporting requires UNION ALL. Shared schema with tenant_id: simpler connections, but cross-tenant data risk if WHERE tenant_id is forgotten. Mitigate with RLS. Cross-tenant reports are single queries. Tenant deletion requires DELETE WHERE tenant_id=X (risk of missing tables). Recommendation: shared schema + RLS for < 10,000 tenants. Schema-per-tenant for large enterprise tenants with strict isolation requirements.

**Explain why EAV is an anti-pattern and what you'd use instead.**
EAV stores attributes as rows (product_id, attr_name, attr_value). Problems: (1) Type safety lost — all values are TEXT, requiring casts. (2) Multi-attribute queries require multiple self-joins (one per attribute), which are slow even with indexes. (3) Constraints (NOT NULL, foreign keys, CHECK) cannot be applied per attribute. (4) Aggregations (SUM, AVG on numeric attributes) require CAST and filtering. (5) Schema changes (add attribute) require documentation rather than column metadata. Alternative: JSONB for flexible attributes (GIN index supports containment queries), generated/computed columns for commonly queried JSONB fields, or a proper table for known attributes with a JSONB catch-all.

**What is the difference between TIMESTAMPTZ and TIMESTAMP in PostgreSQL and which should you use?**
TIMESTAMPTZ (timestamp with time zone): stores the moment in UTC internally. Displays in the session's configured timezone (via `SET TimeZone`). When you insert '2024-01-15 10:00 PST', it's stored as '2024-01-15 18:00 UTC'. All arithmetic and comparisons are timezone-correct. TIMESTAMP (without timezone): stores exactly what you put in — no timezone conversion. If you insert '2024-01-15 10:00' from Tokyo and retrieve from New York, you get the same string '2024-01-15 10:00' — ambiguous. Always use TIMESTAMPTZ for application data — it prevents timezone bugs when application servers are in different regions or when daylight saving time changes occur.

**How do you implement a "soft delete" pattern safely with unique constraints?**
Standard soft delete adds a `deleted_at TIMESTAMPTZ` column (NULL = active, non-NULL = deleted). Problem: `UNIQUE (email)` prevents re-registration with a deleted user's email. Fix: replace the standard unique constraint with a partial unique index: `CREATE UNIQUE INDEX ON users (email) WHERE deleted_at IS NULL`. This enforces uniqueness only among non-deleted rows. Similarly, add partial indexes for all frequently-queried "active-only" patterns: `CREATE INDEX ON orders (customer_id) WHERE deleted_at IS NULL` (avoids scanning deleted orders). Consider a database view `active_users` that always filters `deleted_at IS NULL` to avoid forgetting the filter.

**What is the difference between CHAR(n), VARCHAR(n), and TEXT in PostgreSQL performance?**
In PostgreSQL, CHAR(n), VARCHAR(n), and TEXT are all stored with the same mechanism (varlena header + data). There is no performance difference between VARCHAR and TEXT — they use the same storage. CHAR(n) pads shorter strings with spaces, which wastes storage and causes equality comparison surprises ('abc' = 'abc   '). The n in VARCHAR(n) only enforces a maximum length (good for business constraints like email VARCHAR(254)). Use TEXT when no maximum length makes business sense. Use VARCHAR(n) when there's a meaningful limit to enforce (phone number, country code). Never use CHAR(n) except for fixed-width legacy protocol fields.

**How would you design the schema for a multi-currency financial application?**
Store all amounts in the smallest unit of the currency (cents for USD, pence for GBP, yen for JPY — yen has no decimal). Never use FLOAT/DOUBLE — use BIGINT for amounts in minor units. Store the currency code alongside the amount: `amount_minor_units BIGINT NOT NULL, currency_code CHAR(3) NOT NULL REFERENCES currencies(code)`. The currencies table defines the minor unit exponent (USD: 2 = divide by 100, JPY: 0 = no division). For exchange rates: store as NUMERIC(20,10) (high precision rational), with a timestamp and source. For display, convert in the application, never in the database. For accounting: double-entry bookkeeping — every debit has a corresponding credit, sum of all entries = 0 at all times (enforced by application or trigger).

**What are the pros and cons of using JSONB columns for product attributes?**
Pros: (1) No schema migration needed to add a new attribute. (2) GIN index supports containment and existence queries efficiently. (3) Attributes can vary per product category without sparse columns. (4) Can query nested structures (`data->'dimensions'->>'height'`). (5) Works well for user-defined custom fields. Cons: (1) No column-level constraints (NOT NULL, CHECK, foreign key) per attribute — must enforce in application. (2) Aggregations on JSONB values require CAST and are slower than typed columns. (3) GIN index is 3-5x larger than a B+tree index on a typed column. (4) Query syntax is more complex than `WHERE attribute = value`. (5) Cannot easily add a standard B+tree index on a JSONB path (can use generated columns). Best practice: known, frequently-queried attributes as typed columns; flexible/custom attributes in JSONB.

**When would you use a materialized view vs a regular view?**
Regular view: logical alias for a query, always recomputed on access. Performs well when the underlying tables have good indexes and the view adds just filtering or simple joining. Zero storage overhead. Materialized view: pre-computed and stored. Fast reads regardless of underlying query complexity. Must be refreshed to see new data. Use when: the underlying query is expensive (large aggregations, complex joins across many tables) and results don't need to be real-time. `REFRESH MATERIALIZED VIEW CONCURRENTLY` (with a unique index) allows refreshing without locking reads. Use regular views for simple transformations; use materialized views for expensive aggregations that can tolerate staleness.

**How do you handle schema changes (adding columns, changing types) in a multi-tenant shared schema?**
Add columns: use DEFAULT values (PostgreSQL 11+ stores DEFAULT in catalog for NOT NULL columns, making ADD COLUMN with DEFAULT instant even for large tables). `ALTER TABLE orders ADD COLUMN priority INTEGER NOT NULL DEFAULT 0` — instant on PostgreSQL 11+. Change column type: requires table rewrite unless it's a compatible upcast (e.g., VARCHAR(50) → VARCHAR(100) is instant metadata change). For incompatible type changes, use the expand-contract pattern. Drop columns: mark as unused in application first (deploy code that ignores column), then drop in a later release. This ensures rollback safety — you can revert the code without schema incompatibility.

**What is the purpose of CHECK constraints and when do you use them vs application-level validation?**
CHECK constraints are database-level validation that cannot be bypassed (unlike application code which can have bugs or be bypassed by direct DB access). Use for: invariants that must always hold regardless of what code path writes the data: `CHECK (price > 0)`, `CHECK (quantity >= 0)`, `CHECK (status IN ('pending', 'active', 'cancelled'))`, `CHECK (end_date > start_date)`. Application-level validation: use for user-friendly error messages, complex business rules involving multiple tables, or async validation. The combination: validate in application for UX, enforce in database for correctness. Never rely solely on application-level validation for data integrity — direct DB writes, scripts, and bugs can bypass it.

**How do you design a schema for hierarchical data (e.g., organizational chart, category tree)?**
Multiple patterns: (1) Adjacency list: `parent_id BIGINT REFERENCES table(id)`. Simple, supports unlimited depth, but recursive queries (WITH RECURSIVE CTE) are needed for tree traversal. (2) Closure table: separate `ancestors(ancestor_id, descendant_id, depth)` table — fast queries for "all descendants" (`WHERE ancestor_id = X`), expensive insert/update (must update closure table). (3) Materialized path: `path TEXT` column (e.g., '/1/4/7/') — fast subtree queries (`WHERE path LIKE '/1/4/%'`), path updates cascade. (4) Nested sets: `lft` and `rgt` integers encoding DFS order — extremely fast reads, slow writes (must update all rgt/lft values on insert). PostgreSQL `ltree` extension: native hierarchical labels (`category_path LTREE`) with optimized operators and indexes.

**What is normalization vs denormalization and how do you decide the right balance?**
Normalization (3NF) eliminates redundancy by splitting data into separate tables with foreign keys. Ensures data consistency (one update, one place), enables flexible queries via joins, and reduces storage. Denormalization reintroduces redundancy for read performance: duplicating a frequently-read column avoids a join. Decision: start normalized, measure actual query performance in production with production data volume. If a specific join is measured to be the bottleneck (EXPLAIN ANALYZE shows expensive join on hot path) AND the data is read far more often than written, consider denormalization. Always maintain the normalized source of truth — denormalized copies are projections maintained by triggers or application logic.

---

## 13. Best Practices

1. Use BIGINT GENERATED ALWAYS AS IDENTITY (or BIGSERIAL) for primary keys in PostgreSQL.
2. Always use TIMESTAMPTZ, never TIMESTAMP — prevents timezone bugs.
3. Store monetary values as BIGINT cents, never FLOAT or DOUBLE.
4. Always index foreign key columns — prevents full scans on DELETE from parent table.
5. Use JSONB for flexible attributes; avoid EAV tables.
6. Implement soft delete with partial unique indexes: `WHERE deleted_at IS NULL`.
7. Add CHECK constraints at the database level for invariants that cannot be violated.
8. For multi-tenant SaaS: start with shared schema + RLS; migrate high-value customers to schema-per-tenant as needed.
9. Document schema design decisions in migration files (Flyway/Liquibase) — explain WHY, not just WHAT.
10. Use `NOT NULL` by default; add `NULL` only when null has a specific business meaning distinct from absent.

---

## 14. Case Study

**Scenario**: A B2B SaaS platform for project management has performance issues with its schema. Every user query must join `users`, `project_members`, `projects`, `organizations` — 3-4 join hops for basic operations.

**Original schema (over-normalized for access patterns)**:
```sql
organizations (id, name, settings_jsonb)
users (id, email, org_id FK)
projects (id, name, org_id FK)
project_members (user_id FK, project_id FK, role)
tasks (id, title, assignee_id FK users, project_id FK, created_by FK users)
```

**Problem query** (user's task list):
```sql
SELECT t.id, t.title, p.name as project_name, o.name as org_name
FROM tasks t
JOIN users u ON u.id = t.assignee_id
JOIN projects p ON p.id = t.project_id
JOIN organizations o ON o.id = p.org_id
WHERE t.assignee_id = :user_id AND t.status = 'open';
-- 4-table join, planner often chooses suboptimal order for small orgs
```

**Targeted denormalization** (add org_id to tasks, add project_name cache):
```sql
-- Add org_id to tasks (eliminates join to organizations via projects):
ALTER TABLE tasks ADD COLUMN org_id BIGINT NOT NULL REFERENCES organizations(id);
-- Populated via trigger on INSERT/UPDATE

-- Covering index:
CREATE INDEX idx_tasks_assignee_status ON tasks (assignee_id, status)
INCLUDE (id, title, project_id) WHERE status = 'open';

-- Separate query for project_name (eliminate from hot path):
-- Application caches project names locally (they change rarely)
```

**Result**: task list query reduced from 4-table join to single-table index-only scan + application-side project name lookup from cache. p50 latency: 35ms → 2ms. No data consistency issues because `org_id` is set by trigger on task creation and is immutable.

**Lesson**: Measure first, then denormalize precisely. Do not denormalize the entire schema — identify the specific join on the hot path and eliminate only that.
