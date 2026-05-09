# Case Study: Microservices Migration via Strangler Fig

## Problem Statement

A mid-size e-commerce platform runs on a Spring Boot monolith (500K lines of Java, 50 tables in one PostgreSQL database, deployed as a single WAR). Symptoms:
- 25 engineers step on each other's code; a bug in the search module requires redeploying checkout
- Deployments take 3 hours and happen twice a month (fear of breaking things)
- The product catalog service needs to scale to 50K RPS during sales events but the entire monolith must scale, wasting compute on order processing
- The engineering team wants to move to microservices but a big-bang rewrite is too risky

**Goal**: Decompose the monolith incrementally using the strangler fig pattern with zero downtime and the ability to roll back any individual migration.

---

## Architecture Overview

```
Phase 0: Monolith (baseline)
==============================

[Client] --all traffic--> [Monolith]
                            |--- /search
                            |--- /products
                            |--- /inventory
                            |--- /orders
                            |--- /payments
                            |--- /users
                            |--- PostgreSQL (all tables)


Phase 1: Introduce Gateway Facade
====================================

[Client] --all traffic--> [Spring Cloud Gateway] --all routes--> [Monolith]
(no user-visible change; gateway added as infrastructure)


Phase 2: Extract Search Service
==================================

[Client]
  |
  v
[Spring Cloud Gateway]
  |--- /api/search/** ---> [Search Service]    (NEW — Elasticsearch)
  |--- all other ------- > [Monolith]
  |
  Parallel run: both search endpoints active for 2 weeks
  Traffic split: 10% → 50% → 100% via gateway weight filter


Phase 4: Extract Inventory Service
=====================================

[Client]
  |
  v
[Spring Cloud Gateway]
  |--- /api/search/**     -> [Search Service]      (extracted)
  |--- /api/inventory/**  -> [Inventory Service]   (extracting)
  |--- all other -------> [Monolith]

  Data migration: Debezium CDC syncs inventory table from monolith DB
  to inventory DB during parallel run period
  Cutover: stop CDC, monolith delegates to inventory service API


Phase N: Monolith as Legacy Stub
====================================

[Client]
  |
  v
[Spring Cloud Gateway]
  |--- /api/search/**     -> [Search Service]
  |--- /api/products/**   -> [Product Service]
  |--- /api/inventory/**  -> [Inventory Service]
  |--- /api/orders/**     -> [Order Service]
  |--- /api/payments/**   -> [Payment Service]
  |--- /api/users/**      -> [User Service]
  |--- (legacy endpoints) -> [Monolith stub]
```

---

## Key Design Decisions

**1. Gateway First**

Introducing Spring Cloud Gateway as the first step costs nothing functionally — all traffic is proxied through to the monolith unchanged. But it establishes the control plane for all future migrations. From this point, routing decisions can be made without touching the monolith or client code. This is the pivot point.

**2. Extract Low-Coupling Services First**

Migration order chosen by decreasing independence:
1. Search (read-only, can be rebuilt from product catalog events)
2. Product Catalog (low write frequency, clean domain)
3. Inventory (high write frequency, but isolated domain)
4. Order (complex, many consumers — migrated last)
5. Payment (security-critical, migrated last)

**3. Database per Service via CDC Sync**

The monolith's shared PostgreSQL database cannot be split all at once. The pattern:
1. New service is deployed with its own database
2. Debezium CDC connector reads the monolith DB's WAL for the relevant tables
3. CDC events flow to Kafka, a consumer populates the new service's DB
4. New service is in "shadow mode" (receives writes via monolith delegation) while CDC syncs reads
5. After 4+ weeks of consistency validation, the new service takes primary ownership
6. CDC sync stopped, monolith table deprecated

**4. Feature Flag for Traffic Cutover**

Each migration step uses a feature flag (Unleash) rather than hardcoded routing. This allows:
- Instant rollback without a deployment (flip flag, 100% traffic back to monolith)
- Gradual rollout by percentage (1%, 10%, 50%, 100%)
- User-specific rollout (dogfood with internal users first)

**5. Anti-Corruption Layer at Each Service Boundary**

The monolith's data model uses legacy conventions (`prod_cat_code`, `inv_qty_on_hand`). Each new service has a clean domain model. An ACL (adapter layer) in the new service translates from the monolith's event/API schema to the new service's domain model.

---

## Implementation

### Step 1: Introduce Spring Cloud Gateway

```java
@Configuration
public class GatewayRoutingConfig {

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder,
                                UnleashClient unleash) {
        return builder.routes()
            // All traffic to monolith initially
            .route("monolith-catchall", r -> r
                .path("/**")
                .uri("lb://monolith"))
            .build();
    }
}
```

```yaml
spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      discovery:
        locator:
          enabled: true
      default-filters:
        - AddRequestHeader=X-Gateway-Version, 2.0
        - AddResponseHeader=X-Processed-By, api-gateway
```

### Step 2: Canary Traffic Splitting for Search Service

```java
@Configuration
public class SearchRoutingConfig {

    @Bean
    public RouteLocator searchRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
            // 10% of search traffic to new service (canary)
            .route("search-service-canary", r -> r
                .path("/api/search/**")
                .and().weight("search-group", 10)
                .filters(f -> f
                    .circuitBreaker(c -> c
                        .setName("search-cb")
                        .setFallbackUri("forward:/fallback/search"))
                    .retry(config -> config.setRetries(2))
                )
                .uri("lb://search-service"))

            // 90% still to monolith
            .route("search-monolith", r -> r
                .path("/api/search/**")
                .and().weight("search-group", 90)
                .uri("lb://monolith"))
            .build();
    }
}
```

### Step 3: Debezium CDC for Database Migration

```json
// Debezium connector: read inventory table from monolith DB → Kafka
{
  "name": "inventory-migration-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "monolith-postgres",
    "database.port": "5432",
    "database.user": "debezium_reader",
    "database.password": "${secrets:postgres-password}",
    "database.dbname": "ecommerce",
    "plugin.name": "pgoutput",
    "slot.name": "inventory_migration_slot",

    // Only capture inventory-related tables
    "table.include.list": "public.inventory_items,public.inventory_reservations",

    "transforms": "route",
    "transforms.route.type": "org.apache.kafka.connect.transforms.ReplaceField$Value",
    "transforms.route.renames": "prod_id:productId,inv_qty_on_hand:quantityOnHand",

    "topic.prefix": "migration"
  }
}
```

```java
// Inventory service CDC consumer — populates new service's DB
@Component
public class InventoryMigrationConsumer {

    private final InventoryRepository inventoryRepository;
    private final LegacyInventoryAdapter adapter;

    @KafkaListener(topics = "migration.public.inventory_items",
                   groupId = "inventory-migration")
    @Transactional
    public void handleInventoryChange(ConsumerRecord<String, JsonNode> record) {
        JsonNode after = record.value().get("after");
        if (after == null || after.isNull()) {
            // DELETE event — mark as deleted in new DB
            String legacyId = record.value().get("before").get("id").asText();
            inventoryRepository.deleteByLegacyId(legacyId);
            return;
        }

        // Translate legacy schema to new domain model via ACL
        InventoryItem item = adapter.fromLegacy(after);
        inventoryRepository.save(item);
    }
}
```

### Step 4: Consistency Validation Before Cutover

```java
// Shadow mode: compare monolith inventory response vs new service response
@Service
public class InventoryShadowValidator {

    private final MonolithInventoryClient monolithClient;
    private final InventoryService inventoryService;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelay = 30000)  // every 30 seconds
    public void validateConsistency() {
        List<String> sampleSkus = getSampleSkus(100);

        long mismatches = sampleSkus.stream()
            .filter(sku -> {
                int monolithQty = monolithClient.getQuantity(sku);
                int newServiceQty = inventoryService.getQuantity(sku);
                return monolithQty != newServiceQty;
            })
            .count();

        meterRegistry.gauge("migration.inventory.mismatch_count", mismatches);

        if (mismatches > 5) {
            alertingService.alert("Inventory migration: " + mismatches + " mismatches in 100 samples");
        }
    }
}
// Deploy to production, monitor for 4 weeks. Cutover only when mismatch_count == 0 for 7 days.
```

### Step 5: Feature-Flag-Based Cutover

```java
@Configuration
public class InventoryRoutingConfig {

    @Bean
    public RouteLocator inventoryRoutes(RouteLocatorBuilder builder,
                                        Unleash unleash) {
        return builder.routes()
            .route("inventory-new-service", r -> r
                .path("/api/inventory/**")
                .and().predicate(exchange -> {
                    // Route to new service only when flag is enabled
                    String userId = exchange.getRequest().getHeaders().getFirst("X-User-ID");
                    return unleash.isEnabled("inventory-service-migration",
                        UnleashContext.builder().userId(userId).build());
                })
                .uri("lb://inventory-service"))

            .route("inventory-monolith-fallback", r -> r
                .path("/api/inventory/**")
                .uri("lb://monolith"))
            .build();
    }
}
// Rollback: flip "inventory-service-migration" flag to 0%
// Cutover: increment flag from 1% → 10% → 50% → 100% over 2 weeks
```

---

## Technologies Used

| Technology | Usage |
|------------|-------|
| Spring Cloud Gateway | Facade for traffic routing, gradual cutover |
| Debezium | CDC from monolith PostgreSQL to Kafka |
| Apache Kafka | Event backbone for CDC events and new service integration |
| Unleash | Feature flags for per-percentage traffic cutover |
| PostgreSQL | Both monolith DB and per-service DBs |
| Docker / Kubernetes | New services deployed as containers alongside monolith |
| Resilience4j | Circuit breakers on gateway routes to new services |
| Micrometer | Migration consistency metrics, canary error rate |

---

## Tradeoffs and Alternatives

**Strangler Fig vs Big-Bang Rewrite**:
The big-bang rewrite would mean developing a complete replacement in parallel, then switching all traffic at once. The risk is high: the new system may not have feature parity, there is no intermediate validation, and rollback means throwing away months of work. Strangler fig migrates one bounded context at a time — each migration is independently rollbackable and validated in production with real traffic.

**CDC vs API-Based Data Sync**:
An alternative to CDC is having the new service call the monolith's API to seed its database. This is simpler to set up but: it puts load on the monolith, has rate limiting concerns, misses updates that happen between polls, and requires the monolith to have an export API. CDC from the WAL is more reliable and near-real-time, but requires database replication permissions and adds operational complexity (Debezium, replication slots).

**Data Ownership Transition**:
The most critical decision for each service extraction is: when does the new service become the system of record? The pattern is "write to both, validate, then write to one." During migration: monolith owns writes (source of truth), new service is a replica. After cutover: new service owns writes, monolith is deprecated. Never have two services both owning writes to the same data — that is a distributed monolith.

---

## Interview Discussion Points

- **How do you handle in-flight requests during cutover?** The gateway weight change takes effect immediately (no pod restart). In-flight requests complete against their original backend. New requests route to the new backend at the new weight. There is no request interruption.

- **What do you do if the new service has a bug after 50% traffic cutover?** Flip the feature flag to 0%. This routes all traffic back to the monolith within seconds. No deployment needed. Fix the bug in the new service, validate in staging, increment the flag again.

- **How long do you run the CDC sync in parallel?** Minimum 4 weeks, ideally until the consistency validator shows zero mismatches for 7 consecutive days. The longer the parallel run, the higher the confidence. The parallel run also serves as a load test for the new service.

- **What is the distributed monolith anti-pattern and how do you avoid it?** A distributed monolith is a system split into microservices that are still tightly coupled: they share a database, have circular synchronous dependencies, or must be deployed together. Each extracted service must own its data (separate DB), have no runtime dependencies on the monolith for reads (use CDC-synced data), and be independently deployable. If the order service must call the inventory service synchronously for every order, consider whether they should be the same service.

- **How do you decompose the shared database?** Use the expand-contract pattern: add new columns/tables in the monolith DB that the new service writes to, while keeping old columns for backward compatibility. New service reads from new tables. Monolith reads from old tables with a CDC migration job syncing between them. After cutover, old columns/tables are deprecated (contract phase). Never delete the old schema until the monolith no longer references it.
