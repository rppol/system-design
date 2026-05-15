# Case Study: Design an E-Commerce Product Catalog

## Problem Statement

Design the database architecture for a product catalog serving a marketplace with:

- 50 million SKUs across 1000+ product categories
- Full-text search with faceted filtering (brand, price range, rating, availability)
- Real-time inventory tracking (updated on every purchase and restock)
- Product recommendations (frequently bought together, similar products)
- Sub-200ms P99 search latency; sub-50ms P99 product detail latency
- 100K concurrent users; 500K search queries/minute at peak
- Inventory counts must be eventually consistent (brief oversell acceptable for most SKUs; strict for high-demand items)
- Product data updated by 50K merchants; changes must appear in search within 5 seconds
- 5-year retention of pricing history for analytics

---

## Architecture Overview

```
Client
  │
[API Gateway]
  │
  ├── [Product Detail API] → Redis (L1 cache, 30min TTL) → PostgreSQL
  │
  ├── [Search API] → Elasticsearch cluster
  │
  ├── [Inventory API] → Redis counters (DECR/INCR) → PostgreSQL (async sync)
  │
  └── [Recommendation API] → Redis sorted sets / precomputed pairs

Data Flow (synchronization):
  Merchant → [Product Update API] → PostgreSQL (source of truth)
                                          │
                                    [Debezium CDC]
                                          │
                                    [Kafka: product.events]
                                          │
                       ┌─────────────────┼──────────────────────┐
                       ▼                 ▼                      ▼
              [ES Indexer]      [Price History Writer]    [Cache Invalidator]
                       │                 │                      │
              [Elasticsearch]   [ClickHouse analytics]    [Redis DEL]
```

---

## Key Design Decisions

### 1. PostgreSQL as Source of Truth

```sql
-- Core product schema
CREATE TABLE products (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku           VARCHAR(50) UNIQUE NOT NULL,
    merchant_id   UUID NOT NULL REFERENCES merchants(id),
    title         TEXT NOT NULL,
    description   TEXT,
    brand         VARCHAR(100),
    category_id   INT REFERENCES categories(id),
    status        VARCHAR(20) DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ DEFAULT now(),
    updated_at    TIMESTAMPTZ DEFAULT now()
);

-- Pricing (supports multiple currencies, time-bounded promotions)
CREATE TABLE product_pricing (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id  UUID NOT NULL REFERENCES products(id),
    currency    CHAR(3) NOT NULL,
    price       DECIMAL(12, 2) NOT NULL,
    list_price  DECIMAL(12, 2),             -- Strike-through price
    valid_from  TIMESTAMPTZ DEFAULT now(),
    valid_until TIMESTAMPTZ,                -- NULL = indefinite
    created_at  TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_pricing_product_current ON product_pricing (product_id, valid_from DESC)
    WHERE valid_until IS NULL OR valid_until > now();

-- Inventory (source of truth, updated via atomic operations)
CREATE TABLE inventory (
    product_id      UUID PRIMARY KEY REFERENCES products(id),
    quantity        INT NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    reserved        INT NOT NULL DEFAULT 0,  -- reserved but not yet purchased
    available       INT GENERATED ALWAYS AS (quantity - reserved) STORED,
    warehouse_id    UUID,
    updated_at      TIMESTAMPTZ DEFAULT now()
);

-- Product attributes (EAV avoided — use JSONB instead)
CREATE TABLE product_attributes (
    product_id  UUID PRIMARY KEY REFERENCES products(id),
    attributes  JSONB NOT NULL DEFAULT '{}'
    -- e.g., {"color": "red", "size": "XL", "material": "cotton", "weight_kg": 0.5}
);
CREATE INDEX idx_attrs_gin ON product_attributes USING gin(attributes);

-- Category hierarchy (nested set or closure table)
CREATE TABLE categories (
    id          INT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    parent_id   INT REFERENCES categories(id),
    path        LTREE,  -- 'Electronics.Phones.Smartphones' (ltree extension)
    level       INT GENERATED ALWAYS AS (COALESCE(array_length(string_to_array(path::TEXT, '.'), 1), 0)) STORED
);
CREATE INDEX idx_category_path ON categories USING gist(path);

-- Pricing history in ClickHouse (not PostgreSQL — too large for OLTP)
-- PostgreSQL only keeps current price; history streams to ClickHouse via CDC
```

### 2. Elasticsearch for Search and Facets

```json
// Elasticsearch index mapping for products
{
  "mappings": {
    "properties": {
      "id": {"type": "keyword"},
      "sku": {"type": "keyword"},
      "title": {
        "type": "text",
        "analyzer": "english",
        "fields": {
          "keyword": {"type": "keyword"},
          "suggest": {"type": "completion"}
        }
      },
      "description": {"type": "text", "analyzer": "english"},
      "brand": {"type": "keyword"},
      "category_path": {"type": "keyword"},
      "price_usd": {"type": "scaled_float", "scaling_factor": 100},
      "available_quantity": {"type": "integer"},
      "rating_average": {"type": "float"},
      "rating_count": {"type": "integer"},
      "status": {"type": "keyword"},
      "attributes": {"type": "object", "dynamic": true},
      "updated_at": {"type": "date"}
    }
  },
  "settings": {
    "number_of_shards": 10,
    "number_of_replicas": 1,
    "refresh_interval": "1s"
  }
}
```

```json
// Search query: laptops under $1500, brand Dell, sorted by rating
{
  "query": {
    "bool": {
      "must": [
        {"match": {"title": "laptop"}}
      ],
      "filter": [
        {"term": {"brand": "Dell"}},
        {"range": {"price_usd": {"lte": 1500}}},
        {"range": {"available_quantity": {"gt": 0}}},
        {"term": {"status": "ACTIVE"}}
      ]
    }
  },
  "aggs": {
    "brands": {"terms": {"field": "brand", "size": 20}},
    "price_ranges": {
      "range": {
        "field": "price_usd",
        "ranges": [
          {"to": 500}, {"from": 500, "to": 1000},
          {"from": 1000, "to": 1500}, {"from": 1500}
        ]
      }
    },
    "rating_buckets": {"histogram": {"field": "rating_average", "interval": 1}}
  },
  "sort": [{"rating_average": "desc"}, {"_score": "desc"}],
  "size": 24,
  "search_after": ["4.5", "0.92", "product-id-xyz"]  // Cursor pagination
}
```

### 3. Redis for Inventory Counters

```
Inventory in Redis — atomic counters:
  Key pattern: inventory:{product_id}
  Value: integer (available quantity)

  Purchase flow (atomic via Lua script):
    local available = redis.call('GET', KEYS[1])
    if tonumber(available) >= tonumber(ARGV[1]) then
        return redis.call('DECRBY', KEYS[1], ARGV[1])  -- Success: decrement
    else
        return -1  -- Insufficient stock
    end

  Restock:
    INCRBY inventory:{product_id} {quantity}

  Sync to PostgreSQL (async, every 5 seconds):
    Background job: GETSET inventory:{product_id} {current_value}
    → UPDATE inventory SET quantity = {value} WHERE product_id = ...

  Fallback on cache miss: read from PostgreSQL, populate Redis
  TTL: none (inventory counters never expire — always valid)

High-demand product (limited edition):
  Use PostgreSQL SELECT FOR UPDATE (strict inventory):
  BEGIN;
  SELECT quantity FROM inventory WHERE product_id = ? FOR UPDATE;
  -- verify quantity > 0
  UPDATE inventory SET quantity = quantity - 1 WHERE product_id = ?;
  COMMIT;
  -- Only for SKUs marked as strict_inventory=true
```

### 4. CDC Pipeline for Search Index Sync

```yaml
# Debezium connector for products table → Kafka → Elasticsearch
connector.class: io.debezium.connector.postgresql.PostgresConnector
database.dbname: ecommerce
table.include.list: public.products,public.product_pricing,public.inventory,public.product_attributes

# Elasticsearch sink connector (Confluent)
connector.class: io.confluent.connect.elasticsearch.ElasticsearchSinkConnector
topics: ecommerce.public.products
connection.url: http://elasticsearch:9200
type.name: _doc
key.ignore: false
schema.ignore: true
behavior.on.null.values: delete  # Soft delete in ES when product is deactivated
```

```java
// Kafka consumer: joins events from multiple topics to build ES document
@Component
public class ProductIndexBuilder {

    // Triggered by any product, pricing, or inventory update
    @KafkaListener(topics = {"products.events", "pricing.events", "inventory.events"})
    public void onProductChange(ProductChangeEvent event) {
        // Fetch complete product state from PostgreSQL for full document
        // (denormalize into a single ES document with all fields)
        ProductDocument doc = productReadService.buildDocument(event.getProductId());

        if (doc == null || "DELETED".equals(doc.getStatus())) {
            elasticsearchOps.delete(String.valueOf(event.getProductId()), ProductDocument.class);
        } else {
            elasticsearchOps.index(IndexQuery.builder()
                .id(String.valueOf(doc.getId()))
                .object(doc)
                .build());
        }
    }
}
```

---

## Implementation

### Product Detail API with Two-Level Cache

```java
@Service
public class ProductDetailService {

    // L1: Caffeine (JVM-local, 60s TTL, 10K entries)
    private final Cache<UUID, Product> l1Cache = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(60, TimeUnit.SECONDS)
        .build();

    public Product getProduct(UUID productId) {
        // L1 check (< 0.1ms)
        Product cached = l1Cache.getIfPresent(productId);
        if (cached != null) return cached;

        // L2: Redis (0.5ms)
        String redisKey = "product:" + productId;
        Product redisProduct = redis.opsForValue().get(redisKey);
        if (redisProduct != null) {
            l1Cache.put(productId, redisProduct);
            return redisProduct;
        }

        // L3: PostgreSQL (5-20ms)
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new NotFoundException("Product not found"));

        // Populate both caches
        redis.opsForValue().set(redisKey, product, Duration.ofMinutes(30));
        l1Cache.put(productId, product);
        return product;
    }

    // Invalidate on update (called by CDC consumer)
    public void invalidate(UUID productId) {
        redis.delete("product:" + productId);
        l1Cache.invalidate(productId);
    }
}
```

### Price History in ClickHouse

```sql
-- ClickHouse: pricing history for analytics
CREATE TABLE price_history (
    product_id  UUID,
    merchant_id UUID,
    currency    FixedString(3),
    price       Decimal(12, 2),
    list_price  Nullable(Decimal(12, 2)),
    changed_at  DateTime64(3),
    event_type  String   -- 'PRICE_SET', 'PROMOTION_START', 'PROMOTION_END'
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(changed_at)
ORDER BY (product_id, changed_at)
TTL changed_at + INTERVAL 5 YEAR;

-- Query: price elasticity analysis
SELECT
    toStartOfWeek(changed_at) AS week,
    AVG(price) AS avg_price,
    SUM(units_sold) AS units_sold
FROM price_history ph
JOIN sales_events se ON ph.product_id = se.product_id
    AND se.sale_date BETWEEN ph.changed_at AND ph.next_changed_at
WHERE product_id = 'uuid-here'
GROUP BY week
ORDER BY week;
-- Runs in 1-2 seconds on years of data (ClickHouse columnar scan)
```

---

## Tradeoffs and Alternatives

| Decision | Choice | Alternative | Reason |
|----------|--------|-------------|--------|
| Search | Elasticsearch | PostgreSQL FTS | ES provides faceted aggregations, relevance ranking, and horizontal scale for 50M SKUs |
| Inventory | Redis DECR | PostgreSQL UPDATE | Redis atomic DECR is sub-millisecond; PostgreSQL UPDATE at 100K TPS would saturate |
| Product data | PostgreSQL JSONB for attributes | Normalized EAV table | EAV is an anti-pattern; JSONB with GIN index provides flexible attributes without join complexity |
| Price history | ClickHouse | PostgreSQL partitioned | ClickHouse compresses price history 20x; analytics queries run 50x faster |
| CDC | Debezium | Application dual-write | Debezium provides atomic, ordered change stream without application-layer coordination |
| Search sync lag | ~1-5 seconds (CDC) | Synchronous (blocking) | Synchronous would add latency to product update API; 5s lag is acceptable for catalog search |

---

## Interview Discussion Points

**How do you prevent inventory oversell at 100K concurrent purchases?**
Two tiers: (1) Redis atomic DECR via Lua script for most products — the Lua script checks available quantity and decrements atomically. Sub-millisecond, handles 1M ops/second. Brief oversell possible if Redis restarts before PostgreSQL sync (configurable: AOF fsync=everysec = 1s max data loss). (2) PostgreSQL SELECT FOR UPDATE for high-demand, limited-inventory products (flash sale items, pre-orders). This serializes concurrent purchases at the DB level with absolute accuracy. The tradeoff: slower (5–10ms vs 0.5ms) and limited throughput (1K TPS per product vs unlimited in Redis). Mark these products with `strict_inventory=true` and route them to the PostgreSQL path.

**How do you keep Elasticsearch consistent with PostgreSQL for 50M products?**
Debezium CDC tails the PostgreSQL WAL and publishes change events to Kafka within 100ms. A Kafka consumer reads these events, fetches the complete product document from PostgreSQL (to denormalize all related fields: title, brand, pricing, inventory, attributes), and upserts into Elasticsearch. Typical end-to-end lag: 1–5 seconds. At 50K merchants × 100 updates/day = 5M updates/day = ~58 updates/second. Elasticsearch handles this comfortably (can index thousands/second). For zero-downtime mapping changes (e.g., adding a new facet field), use the index alias pattern: create new index v2, reindex 50M documents in background (hours), swap alias atomically.

**How do you handle a merchant updating 1000 products simultaneously?**
Batch update: the merchant uploads a CSV. The API processes it as a bulk update job: (1) Validate all 1000 rows before writing any. (2) Upsert products in batches of 100 within individual transactions. (3) Each batch writes an outbox event. (4) Debezium picks up the changes. (5) Kafka consumer processes up to 100 events/second. All 1000 products appear in search within 10–15 seconds (1000/100 events × 1s CDC lag). The search index update is asynchronous — the merchant sees their products "updating" with a status indicator in the UI. This is acceptable because search results for the merchant's products are typically not shown to other users until the merchant explicitly publishes.

**Why is JSONB used for product attributes instead of a normalized schema?**
Product categories have wildly different attribute sets: laptops have RAM/CPU/screen size; shirts have color/size/material; furniture has dimensions/weight capacity. A normalized attribute schema (EAV: entity-attribute-value) requires a 3-table join for every attribute query and cannot be efficiently indexed for multi-attribute filtering. JSONB stores structured data with the flexibility of semi-structured schema and can be indexed with GIN (`CREATE INDEX ON product_attributes USING gin(attributes)`) for containment queries (`attributes @> '{"color": "red"}'`). For range queries on attributes (price < 500 within attributes), add a functional index on the specific attribute: `CREATE INDEX ON product_attributes ((attributes->>'weight_kg')::FLOAT)`.
