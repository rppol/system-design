# Spring Testing

## 1. Concept Overview

Spring Testing is the comprehensive framework for writing automated tests against Spring Boot applications. It covers everything from unit tests of individual components to full integration tests that load the complete ApplicationContext, spin up embedded HTTP servers, and interact with real (or containerized) databases.

The testing pyramid in Spring Boot has three layers:
- **Unit tests**: No Spring context, pure Java, fast (milliseconds). Use Mockito for dependencies.
- **Slice tests**: Partial Spring context loading only the layer under test (web, JPA, JSON, etc.). Faster than full context.
- **Integration tests**: Full ApplicationContext with @SpringBootTest. Slow but comprehensive.

Spring Boot's test infrastructure provides:
- @SpringBootTest for full context integration tests
- Test slices (@WebMvcTest, @DataJpaTest, @WebFluxTest, @RestClientTest, @JsonTest) for narrow context loading
- @MockBean / @SpyBean for context-level mocking
- MockMvc for HTTP layer testing without a real server
- TestRestTemplate and WebTestClient for end-to-end HTTP testing
- Testcontainers integration for real external dependencies
- @Sql for test data management
- Context caching for performance optimization

---

## 2. Intuition

One-line analogy: Testing a Spring Boot application is like testing a car — unit tests check individual parts (a spark plug in isolation), slice tests check subsystems (the engine block without the transmission), and integration tests drive the whole car on a test track.

Mental model: Spring Boot's test framework answers the question "how much of the application do I need running to test this?" For a controller's routing and validation logic, you do not need the database or service layer — @WebMvcTest gives you just the web layer. For a JPA repository query, you do not need HTTP or services — @DataJpaTest gives you just the JPA stack. For end-to-end flows, @SpringBootTest loads everything.

Why it matters: Loading the full ApplicationContext takes 5–30 seconds depending on the application. A test suite with 200 tests that each load a fresh context is unusable. Spring Boot's context caching, slice tests, and @MockBean infrastructure exist to make large test suites run in seconds, not minutes.

Key insight: @MockBean is the single biggest performance enemy in test suites. Every @MockBean annotation creates a context variation that cannot be shared with other tests — it forces a new context load. Minimizing @MockBean usage (prefer constructor injection with interface types, test in isolation) is the most impactful test performance optimization.

---

## 3. Core Principles

**Test isolation**: Each test should be independent of others. Shared state (database, static variables, caches) between tests causes flakiness. Use @Transactional with auto-rollback or @Sql with cleanup scripts to ensure isolation.

**Fast feedback**: Tests should complete in seconds at the unit level and minutes at the integration level. Use the narrowest context that proves correctness.

**Context caching**: Spring caches the ApplicationContext between test classes that share the same configuration signature (same @SpringBootTest properties, same @MockBeans, same active profiles). @DirtiesContext bypasses the cache — use it only when state genuinely cannot be cleaned up.

**Real dependencies over mocks for integration tests**: Mocking a database in an integration test provides false confidence. Use Testcontainers to run real PostgreSQL, Kafka, or Redis in Docker containers, ensuring tests reflect production behavior.

**Behavior verification over state verification**: Test what the system does, not how it does it. Assert on HTTP responses, database state, or published events — not on internal method call counts (which couple tests to implementation).

---

## 4. Types / Architectures / Strategies

### @SpringBootTest Modes

| webEnvironment | Description | Use When |
|---------------|-------------|---------|
| MOCK (default) | Loads WebApplicationContext with MockServletEnvironment; no real server | Controller + full context integration |
| RANDOM_PORT | Starts real embedded server on random port; use TestRestTemplate | End-to-end HTTP tests |
| DEFINED_PORT | Starts real embedded server on server.port (8080) | Specific port required |
| NONE | Loads ApplicationContext without web environment | Service/repository integration, no HTTP |

### Test Slices

| Annotation | Context Loaded | Use For |
|-----------|---------------|---------|
| @WebMvcTest | MVC layer (controllers, filters, converters, security) | Controller unit/integration |
| @DataJpaTest | JPA (entities, repositories, H2 or configured DB) | Repository queries |
| @WebFluxTest | WebFlux layer | Reactive controllers |
| @RestClientTest | RestTemplate + MessageConverter + MockRestServiceServer | HTTP client beans |
| @JsonTest | Jackson ObjectMapper, @JsonComponent | JSON serialization/deserialization |
| @DataMongoTest | MongoDB repositories | Mongo repositories |
| @DataRedisTest | Redis repositories | Redis repositories |

### Mocking Strategies

- **Mockito.mock()**: Pure Mockito, no Spring context. Fast. Use in unit tests.
- **@Mock**: Mockito annotation, initialized by MockitoExtension. No Spring.
- **@MockBean**: Creates a Mockito mock AND registers it in the Spring ApplicationContext, replacing any existing bean of that type.
- **@SpyBean**: Creates a Mockito spy wrapping the REAL bean. Real methods execute unless stubbed.
- **MockRestServiceServer**: Mocks RestTemplate calls at the HTTP client level, without a real server.
- **WireMock**: Starts a real HTTP mock server. Use when testing HTTP client behavior including retries, timeouts.

---

## 5. Architecture Diagrams

### Spring Test Context Hierarchy

```
@SpringBootTest (full context)
+--------------------------------------------------+
|  @SpringBootApplication                          |
|  All @Configuration, @Component, @Service,       |
|  @Repository beans                               |
|                                                  |
|  Real DataSource (or TestContainers)             |
|  Real Kafka (or EmbeddedKafka / TestContainers)  |
|  @MockBean replaces specific beans               |
+--------------------------------------------------+

@WebMvcTest (MVC slice)
+--------------------------------------------------+
|  @Controller, @ControllerAdvice                  |
|  @Filter, HandlerInterceptor                     |
|  WebMvcConfigurer                                |
|  Spring Security (if present)                    |
|                                                  |
|  Services: NOT loaded — must @MockBean           |
|  Repositories: NOT loaded — must @MockBean       |
+--------------------------------------------------+

@DataJpaTest (JPA slice)
+--------------------------------------------------+
|  @Entity, @Repository (JPA)                      |
|  JPA auto-configuration                          |
|  Embedded H2 (default) or configured DataSource  |
|                                                  |
|  @Service: NOT loaded                            |
|  Web layer: NOT loaded                           |
+--------------------------------------------------+
```

### Context Caching

```
Test Class A                   Test Class B                   Test Class C
@SpringBootTest                @SpringBootTest                @SpringBootTest
@MockBean(ServiceX)            @MockBean(ServiceX)            @MockBean(ServiceX)
                                                              @MockBean(ServiceY)  <-- different signature
        |                              |                              |
        v                              v                              v
  [Context 1]  <-- cached --> [Context 1 reused]          [Context 2 created]
  (slow: 8s)                  (instant: 0ms)               (slow: 8s)

Key: same set of @MockBeans + same @SpringBootTest config = same context key = cache hit
```

### MockMvc Request Flow

```
  Test Method
      |
      | mockMvc.perform(get("/orders/123"))
      |
      v
  DispatcherServlet (in-memory, no real HTTP socket)
      |
      v
  HandlerMapping --> OrderController.getOrder(123)
      |
      v
  OrderService (real or @MockBean)
      |
      v
  MockHttpServletResponse
      |
      v
  .andExpect(status().isOk())
  .andExpect(jsonPath("$.id").value(123))
```

### Testcontainers Lifecycle

```
  @Testcontainers
  @SpringBootTest
  Test Suite Start
      |
      v
  Docker: start PostgreSQLContainer  <-- once per test class (or suite with @Container static)
      |
      v
  @DynamicPropertySource sets spring.datasource.url = jdbc:postgresql://localhost:<mapped-port>/test
      |
      v
  Spring ApplicationContext starts with real PostgreSQL connection
      |
  Each @Test method:
      | @Transactional -> auto-rollback at end
      | OR @Sql("cleanup.sql") -> explicit cleanup
      v
  Test Suite End -> Docker: stop container
```

---

## 6. How It Works — Detailed Mechanics

### @SpringBootTest with MockMvc

```java
@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService; // replace real bean

    @Test
    void createOrder_returnsCreated() throws Exception {
        // Given
        CreateOrderRequest request = new CreateOrderRequest("item-1", 3);
        given(paymentService.charge(any())).willReturn(PaymentResult.success("txn-123"));

        // When / Then
        mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.orderId").isNotEmpty())
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
            .andExpect(header().exists("Location"));
    }

    @Test
    void getOrder_notFound_returns404() throws Exception {
        mockMvc.perform(get("/orders/99999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("Order not found"));
    }
}
```

### @WebMvcTest — Controller Slice

```java
// Loads ONLY the web layer — OrderService must be @MockBean
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService; // required — not loaded by @WebMvcTest

    @Test
    void getOrder_delegatesToService() throws Exception {
        Order order = Order.builder().id(1L).status("CONFIRMED").build();
        given(orderService.getOrder(1L)).willReturn(order);

        mockMvc.perform(get("/orders/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.status").value("CONFIRMED"));

        then(orderService).should().getOrder(1L);
    }

    @Test
    void createOrder_invalidRequest_returns400() throws Exception {
        // Empty item ID — violates @NotBlank validation
        String invalidJson = "{\"itemId\": \"\", \"quantity\": 0}";

        mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors").isArray());

        then(orderService).shouldHaveNoInteractions();
    }
}
```

### @DataJpaTest — Repository Slice

```java
@DataJpaTest
// By default uses H2 in-memory. Override with @AutoConfigureTestDatabase(replace = NONE)
// to use the configured DataSource (e.g., a Testcontainers PostgreSQL)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @Transactional // auto-rollback after test
    void findByCustomerIdAndStatus_returnsMatchingOrders() {
        // Given: persist test data directly via EntityManager
        Order o1 = entityManager.persistAndFlush(
            Order.builder().customerId("cust-1").status("CONFIRMED").build());
        Order o2 = entityManager.persistAndFlush(
            Order.builder().customerId("cust-1").status("PENDING").build());
        Order o3 = entityManager.persistAndFlush(
            Order.builder().customerId("cust-2").status("CONFIRMED").build());
        entityManager.clear(); // detach all — force DB round-trip

        // When
        List<Order> result = orderRepository.findByCustomerIdAndStatus("cust-1", "CONFIRMED");

        // Then
        assertThat(result).hasSize(1).extracting(Order::getId).containsExactly(o1.getId());
    }
}
```

### @SpyBean — Partial Mock

```java
@SpringBootTest
class AuditServiceTest {

    @SpyBean
    private AuditService auditService; // wraps the REAL bean

    @Autowired
    private OrderService orderService;

    @Test
    void placeOrder_triggersAuditLog() {
        orderService.placeOrder(new CreateOrderRequest("item-1", 1));

        // Verify the real AuditService was called
        then(auditService).should().log(eq("ORDER_PLACED"), any());
    }

    @Test
    void placeOrder_withStubbedAudit() {
        // Override one method while keeping all others real
        doNothing().when(auditService).log(any(), any());

        orderService.placeOrder(new CreateOrderRequest("item-1", 1));
        // audit log call was intercepted but other AuditService methods run normally
    }
}
```

### TestRestTemplate — RANDOM_PORT End-to-End

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderApiE2ETest {

    @Autowired
    private TestRestTemplate restTemplate; // follows redirects, includes error handling

    @LocalServerPort
    private int port;

    @Test
    void fullOrderFlow_createsAndRetrieves() {
        // Create
        CreateOrderRequest request = new CreateOrderRequest("item-1", 2);
        ResponseEntity<OrderResponse> createResponse =
            restTemplate.postForEntity("/orders", request, OrderResponse.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long orderId = createResponse.getBody().getOrderId();

        // Retrieve
        ResponseEntity<OrderResponse> getResponse =
            restTemplate.getForEntity("/orders/" + orderId, OrderResponse.class);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().getStatus()).isEqualTo("CONFIRMED");
    }
}
```

### WebTestClient — Reactive Testing

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReactiveOrderApiTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void streamOrders_returnsFlux() {
        webTestClient.get()
            .uri("/orders/stream")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(OrderEvent.class)
            .hasSize(5);
    }

    @Test
    void createOrder_returnsCreated() {
        webTestClient.post()
            .uri("/orders")
            .bodyValue(new CreateOrderRequest("item-1", 1))
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.orderId").isNotEmpty()
            .jsonPath("$.status").isEqualTo("CONFIRMED");
    }
}
```

### Testcontainers — Real PostgreSQL

```java
@Testcontainers
@SpringBootTest
@ActiveProfiles("integration")
class OrderRepositoryContainerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("orders_test")
        .withUsername("test")
        .withPassword("test");

    // DynamicPropertySource runs before Spring context starts
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Force Flyway/Liquibase to run against test container
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
    }

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @Sql("/sql/order_test_data.sql")          // insert before
    @Sql(scripts = "/sql/cleanup.sql",         // delete after
         executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void complexQuery_worksOnRealPostgres() {
        // Query uses PostgreSQL-specific features (jsonb, array operators)
        List<Order> orders = orderRepository.findOrdersWithJsonbFilter("CONFIRMED");
        assertThat(orders).isNotEmpty();
    }
}
```

### @Sql — Data Setup and Teardown

```java
@DataJpaTest
class OrderQueryTest {

    @Autowired
    private OrderRepository repo;

    @Test
    @Sql("/test-data/orders.sql")  // runs BEFORE_TEST_METHOD (default)
    void findByStatus_returnsOrders() {
        List<Order> confirmed = repo.findByStatus("CONFIRMED");
        assertThat(confirmed).hasSize(3); // matches data in orders.sql
    }

    // Using @SqlGroup for multiple scripts with different phases
    @Test
    @SqlGroup({
        @Sql(scripts = "/test-data/reference_data.sql"),
        @Sql(scripts = "/test-data/orders.sql"),
        @Sql(scripts = "/test-data/cleanup.sql",
             executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    })
    void complexScenario() { ... }
}
```

### @JsonTest — Serialization Testing

```java
@JsonTest
class OrderResponseJsonTest {

    @Autowired
    private JacksonTester<OrderResponse> json;

    @Test
    void serialize_producesExpectedJson() throws IOException {
        OrderResponse response = OrderResponse.builder()
            .orderId(1L)
            .status("CONFIRMED")
            .createdAt(Instant.parse("2024-01-15T10:00:00Z"))
            .build();

        assertThat(json.write(response))
            .hasJsonPathNumberValue("$.orderId")
            .extractingJsonPathStringValue("$.status").isEqualTo("CONFIRMED")
            .extractingJsonPathStringValue("$.createdAt").isEqualTo("2024-01-15T10:00:00Z");
    }

    @Test
    void deserialize_parsesJson() throws IOException {
        String jsonContent = """
            {"orderId": 1, "status": "CONFIRMED", "createdAt": "2024-01-15T10:00:00Z"}
            """;

        OrderResponse response = json.parseObject(jsonContent);
        assertThat(response.getOrderId()).isEqualTo(1L);
        assertThat(response.getStatus()).isEqualTo("CONFIRMED");
    }
}
```

---

## 7. Real-World Examples

**Controller validation testing**: @WebMvcTest loads only the controller layer. Tests verify that @Valid constraints on request bodies return 400 with structured error responses, and that valid requests are delegated to a @MockBean service. No database or service initialization needed — tests run in under 100ms each.

**JPA repository query testing with Testcontainers**: A reporting query uses PostgreSQL window functions and jsonb operators that do not work on H2. @DataJpaTest with @AutoConfigureTestDatabase(replace=NONE) combined with a static PostgreSQLContainer in a base test class gives all repository tests a real PostgreSQL instance. Flyway migrations run automatically, matching production schema exactly.

**Kafka integration test**: @SpringBootTest + @EmbeddedKafka starts an in-process Kafka broker. Tests publish messages via KafkaTemplate and assert on database state (after giving the consumer time to process) or use a CountDownLatch in the listener to synchronize. KafkaContainer from Testcontainers is preferred when real broker behavior (e.g., exactly-once, schema registry) must be tested.

**Security testing**: @WebMvcTest includes Spring Security by default. Tests annotated with @WithMockUser(roles="ADMIN") verify secured endpoints return 200 for authorized users, while unauthenticated requests return 401 or 403. @WithSecurityContext creates complex security scenarios (multi-tenant, OAuth tokens).

---

## 8. Tradeoffs

### Test Slice vs Full @SpringBootTest

| Dimension | Slice Test | @SpringBootTest |
|-----------|-----------|----------------|
| Context load time | ~0.5–2s | ~5–30s |
| Context scope | Narrow (one layer) | Full application |
| @MockBean frequency | Necessary for missing beans | Optional — real beans available |
| Confidence level | Medium (layer isolation) | High (cross-layer integration) |
| Debugging complexity | Low | Higher |

### H2 vs Testcontainers for @DataJpaTest

| Dimension | H2 (embedded) | Testcontainers (PostgreSQL) |
|-----------|--------------|---------------------------|
| Speed | Very fast (no Docker) | Slower (container startup ~3–5s) |
| Fidelity | Low (dialect differences) | High (matches production) |
| PostgreSQL features | Not supported (jsonb, arrays) | Full support |
| CI dependency | None | Docker required |
| Recommendation | Simple CRUD queries | Complex/PostgreSQL-specific queries |

### @MockBean vs Interface Injection

| Dimension | @MockBean | Interface + Constructor Injection |
|-----------|----------|----------------------------------|
| Context cache impact | Dirties cache (new context per variation) | No impact |
| Test speed | Slower (new context per MockBean set) | Faster |
| Applicability | When Spring context must see the mock | Unit tests without Spring |
| Best for | Integration tests requiring Spring wiring | Unit tests of service logic |

---

## 9. When to Use / When NOT to Use

### Use @SpringBootTest when:
- Testing cross-layer integration (controller -> service -> repository flow)
- Verifying auto-configuration behavior
- Testing security configuration end-to-end
- Performing smoke tests on application startup

### Use @WebMvcTest when:
- Testing controller routing, validation, exception handling
- Verifying request/response serialization
- Testing Spring Security rules at the controller layer
- Keeping test context minimal for fast feedback

### Use @DataJpaTest when:
- Testing custom JPQL, @Query, derived query methods
- Verifying Flyway/Liquibase migrations (with Testcontainers)
- Testing entity lifecycle callbacks (@PrePersist, @PostLoad)

### Use Testcontainers when:
- Production uses PostgreSQL, MySQL, MongoDB, or Redis (not H2-compatible)
- Testing Kafka consumer/producer integration
- Any test that requires real broker or database behavior

### Do NOT use @DirtiesContext unless necessary:
- It forces a full context reload, typically costing 5–20 seconds
- Prefer @Transactional rollback or @Sql cleanup for test isolation
- Reserve @DirtiesContext for tests that mutate global Spring state (e.g., change ApplicationContext, test @ConditionalOnMissingBean behavior)

### Do NOT use @MockBean in a base class shared by many test classes:
- Every class that inherits the @MockBean declaration creates a unique context signature
- If 50 test classes share a base class with @MockBean(ServiceA), all 50 use the same cached context — this is acceptable
- But if 10 test classes have different @MockBean combinations, that is 10 different contexts

---

## 10. Common Pitfalls

### Pitfall 1 — @MockBean causes context cache miss, tests slow (broken)

```java
// BROKEN: every test class adds @MockBean for a slightly different set of beans
// Each unique combination of @MockBeans creates a separate ApplicationContext
// A test suite of 50 classes with varied @MockBean combos = 50 context loads = very slow

@SpringBootTest
class OrderServiceTest {
    @MockBean EmailService emailService;
    @MockBean InventoryService inventoryService;
    // context key: {SpringBootTest, MockBean[EmailService, InventoryService]}
}

@SpringBootTest
class PaymentServiceTest {
    @MockBean EmailService emailService;
    @MockBean InventoryService inventoryService;
    @MockBean AuditService auditService; // one extra MockBean = NEW CONTEXT
}
```

```java
// FIXED: create a shared base test class with all @MockBeans
// All subclasses share the same context because they have the same signature

@SpringBootTest
public abstract class BaseIntegrationTest {
    @MockBean protected EmailService emailService;
    @MockBean protected InventoryService inventoryService;
    @MockBean protected AuditService auditService;
    // All subclasses inherit these — same context key — one context load
}

class OrderServiceTest extends BaseIntegrationTest {
    // Uses shared context — no additional context load
}

class PaymentServiceTest extends BaseIntegrationTest {
    // Uses shared context — no additional context load
}
```

### Pitfall 2 — @DataJpaTest with H2 masks PostgreSQL-specific query failures (broken)

```java
// BROKEN: test passes on H2 but fails in production with PostgreSQL
// PostgreSQL uses different SQL syntax: boolean literals are 'true'/'false', not 1/0
// jsonb operators, array_agg, unnest, pg_trgm — none work on H2

@DataJpaTest  // uses H2 by default
class OrderRepositoryTest {

    @Test
    void findByJsonbAttribute_returnsOrders() {
        // This query uses PostgreSQL jsonb: WHERE metadata->>'type' = 'express'
        // On H2: passes because the JPQL falls back to a basic comparison
        // On PostgreSQL in production: native query uses ->> operator, which H2 doesn't support
        List<Order> orders = repo.findExpressOrders(); // silently broken
    }
}
```

```java
// FIXED: replace H2 with a real PostgreSQL via Testcontainers

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class OrderRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void findByJsonbAttribute_worksOnRealPostgres() {
        List<Order> orders = repo.findExpressOrders(); // tests against actual PostgreSQL
        assertThat(orders).isNotEmpty();
    }
}
```

### Pitfall 3 — @Transactional on test method but verifying state that relies on commit (broken)

```java
// BROKEN: test method is @Transactional, auto-rollback at end
// The OrderService.placeOrder() is also @Transactional
// Both run in the SAME transaction because of Spring's default REQUIRED propagation
// commit never happens during the test
// @AfterEach SQL that checks for ORDER in DB finds nothing because TX not committed

@SpringBootTest
class OrderServiceTest {

    @Autowired OrderService orderService;
    @Autowired OrderRepository orderRepo;

    @Test
    @Transactional // wraps entire test in a transaction — auto-rollback after
    void placeOrder_persistsToDatabase() {
        orderService.placeOrder(new CreateOrderRequest("item-1", 1));
        // orderService.placeOrder() runs in THIS test transaction (REQUIRED propagation)
        // The order IS in the EntityManager cache — findById returns it
        // But: if you verify from a DIFFERENT connection (e.g., Testcontainers container query),
        // it is NOT there — the transaction has not committed
        assertThat(orderRepo.findAll()).hasSize(1); // passes — but misleading
    }
}
```

```java
// FIXED option A: remove @Transactional from the test, use @Sql for cleanup
// The service method commits its own transaction; the test verifies committed state

@SpringBootTest
class OrderServiceTest {

    @Autowired OrderService orderService;
    @Autowired OrderRepository orderRepo;

    @AfterEach
    void cleanup() {
        orderRepo.deleteAll(); // explicit cleanup since no auto-rollback
    }

    @Test
    void placeOrder_persistsToDatabase() {
        orderService.placeOrder(new CreateOrderRequest("item-1", 1));
        // Service committed its transaction; we can now verify real DB state
        assertThat(orderRepo.findAll()).hasSize(1);
    }
}

// FIXED option B: use @Commit to force commit in test (then manual cleanup needed)
@Test
@Transactional
@Commit // commits the transaction — use only when you need to verify external DB state
void placeOrder_verifyFromExternalConnection() {
    orderService.placeOrder(new CreateOrderRequest("item-1", 1));
    // After @Commit, the data is visible to other connections
}
```

### Pitfall 4 — @WebMvcTest missing security configuration (broken)

```java
// BROKEN: @WebMvcTest includes Spring Security
// If SecurityConfig references UserDetailsService bean (not loaded by @WebMvcTest),
// context fails to start with NoSuchBeanDefinitionException

@WebMvcTest(OrderController.class)
class OrderControllerTest {
    @Autowired MockMvc mockMvc;
    // Missing: @MockBean UserDetailsService — SecurityConfig requires it
    // Result: ApplicationContext fails to initialize
}
```

```java
// FIXED: @MockBean all beans required by SecurityConfig within the test slice

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean OrderService orderService;
    @MockBean UserDetailsService userDetailsService; // required by SecurityConfig

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void getOrder_authenticated_returns200() throws Exception {
        given(orderService.getOrder(1L)).willReturn(Order.builder().id(1L).build());
        mockMvc.perform(get("/orders/1"))
            .andExpect(status().isOk());
    }

    @Test
    void getOrder_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/orders/1"))
            .andExpect(status().isUnauthorized());
    }
}
```

### Pitfall 5 — Slow tests due to @DirtiesContext overuse (broken)

```java
// BROKEN: @DirtiesContext on every test class forces a context reload for every class
// A suite of 30 classes with @DirtiesContext each + 10s context load = 300 seconds overhead

@SpringBootTest
@DirtiesContext // used carelessly "to ensure clean state"
class OrderServiceTest { ... }

@SpringBootTest
@DirtiesContext
class PaymentServiceTest { ... }
```

```java
// FIXED: use @Transactional for DB state isolation; @Sql for data cleanup
// Reserve @DirtiesContext ONLY for tests that genuinely mutate Spring context state
// (e.g., modifying application context beans, testing @RefreshScope, CacheManager eviction)

@SpringBootTest
@Transactional // auto-rollback: each test gets a clean DB state
class OrderServiceTest {
    // No @DirtiesContext needed — @Transactional rollback ensures isolation
    // Context is shared and reused across all test classes with matching configuration
}
```

---

## 11. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| spring-boot-test | Core test support | @SpringBootTest, @MockBean, TestRestTemplate |
| spring-test | MVC test, context management | MockMvc, @Sql, @DirtiesContext |
| JUnit 5 (Jupiter) | Test runner | @Test, @ParameterizedTest, @BeforeEach, @ExtendWith |
| Mockito | Mocking and verification | @Mock, @Spy, BDDMockito |
| AssertJ | Fluent assertions | Preferred over Hamcrest in Spring Boot tests |
| Testcontainers | Real external services in Docker | PostgreSQLContainer, KafkaContainer, RedisContainer |
| WireMock | HTTP mock server | Mocking external REST APIs with real HTTP |
| H2 | Embedded in-memory DB | @DataJpaTest default; avoid for PostgreSQL-specific features |
| @EmbeddedKafka | In-process Kafka broker | Lighter than Testcontainers for basic Kafka tests |
| spring-security-test | Security testing utilities | @WithMockUser, @WithSecurityContext |
| JSONPath (json-path) | JSON assertion in MockMvc | .andExpect(jsonPath("$.field").value(...)) |

---

## 12. Interview Questions with Answers

**Q: What is the difference between @MockBean and @Mock in Spring Boot tests?**
@Mock is a pure Mockito annotation that creates a mock object managed by MockitoExtension — it has no interaction with the Spring ApplicationContext. @MockBean creates a Mockito mock AND registers it as a Spring bean, replacing any existing bean of that type in the ApplicationContext. Use @Mock in pure unit tests with no Spring context. Use @MockBean in integration tests where the mock must be injected into Spring-managed beans via the context. The critical side-effect of @MockBean is that it changes the ApplicationContext configuration signature, preventing the test from sharing a cached context with tests that do not have that @MockBean.

**Q: How does Spring Boot test context caching work, and what breaks it?**
Spring caches ApplicationContext instances keyed by the set of configuration parameters: @SpringBootTest properties, @ActiveProfiles, @MockBean types, @SpyBean types, the test class's location in the source tree, and any @ContextConfiguration overrides. If two test classes have identical configuration keys, the second class reuses the first class's context — no new Spring startup. What breaks the cache: @DirtiesContext (explicitly invalidates the cached context), different @MockBean combinations (different key = different context), @ActiveProfiles differences, and adding @SpringBootTest(properties="...") with different values. In a large test suite, monitoring unique context counts (visible in test logs as "Creating shared instance of singleton bean") is a key performance optimization task.

**Q: Explain the difference between @SpringBootTest(webEnvironment=MOCK) and RANDOM_PORT.**
MOCK (the default) loads the full WebApplicationContext but creates a MockServletEnvironment rather than starting a real embedded Tomcat server. No TCP socket is opened. MockMvc operates directly against the DispatcherServlet in memory — no real HTTP is involved. This is faster and simpler. RANDOM_PORT starts a real embedded Tomcat on a randomly selected available port (accessible via @LocalServerPort). HTTP requests go through real TCP sockets, through real Tomcat thread pools, through real HTTP parsing. Use MOCK for most integration tests. Use RANDOM_PORT when testing HTTP-level concerns (redirect handling, HTTPS, actual HTTP headers, WebSocket upgrades, TestRestTemplate).

**Q: What is the purpose of @WebMvcTest and what does it NOT load?**
@WebMvcTest loads the Spring MVC layer: @Controller, @ControllerAdvice, @JsonComponent, Converter, Filter, and WebMvcConfigurer beans. It also loads Spring Security if present. It does NOT load @Service, @Repository, @Component beans, or auto-configuration unrelated to MVC (DataSource, JPA, Kafka, etc.). This makes @WebMvcTest contexts fast to initialize — typically under 2 seconds. Because services and repositories are not loaded, all dependencies of the controller must be provided as @MockBean. @WebMvcTest is the primary tool for testing controller routing, request validation, exception handler responses, and Spring Security rules at the HTTP layer.

**Q: How does @DataJpaTest configure the database, and when should you override it?**
@DataJpaTest auto-configures an embedded H2 in-memory database by default. It loads JPA auto-configuration, @Entity classes, and Spring Data JPA repositories. It does NOT load @Service or web-layer beans. Each test method is wrapped in a transaction that is rolled back at the end, ensuring isolation. Override the default database with @AutoConfigureTestDatabase(replace=NONE) when your queries use PostgreSQL-specific features (jsonb, window functions, pg_trgm, array operators) that H2 does not support. In this case, combine with Testcontainers to run a real PostgreSQL container, or configure a shared PostgreSQL test instance via DynamicPropertySource.

**Q: What is @SpyBean and when should you use it instead of @MockBean?**
@SpyBean wraps an existing real Spring bean with a Mockito spy. Unlike @MockBean (which replaces the bean with a pure mock that returns null by default), @SpyBean keeps the real implementation — only stubbed methods are intercepted, all others delegate to the real object. Use @SpyBean when you want to verify interactions on a real bean (verify it was called with specific arguments) but do not want to replace its behavior. Use it also when you want to override only one method of a complex real bean. The performance implication is the same as @MockBean — it creates a unique ApplicationContext configuration, potentially breaking context caching.

**Q: How do you test a @Transactional service method to verify that it actually commits to the database?**
By default, if the test method is annotated with @Transactional, it participates in the same transaction as the service method (via REQUIRED propagation), and the entire transaction is rolled back at test completion. The data appears in the EntityManager cache during the test but is never committed. To verify a real DB commit: remove @Transactional from the test method (use @Sql or @AfterEach for cleanup instead), or add @Commit to force the transaction to commit. For critical tests that verify ACID properties or trigger database constraints (unique index violations, cascades), remove @Transactional from the test and rely on explicit cleanup. Use @Transactional on tests only when you explicitly want auto-rollback isolation.

**Q: How do Testcontainers containers integrate with Spring Boot's ApplicationContext startup?**
@DynamicPropertySource is a static method annotated with @DynamicPropertySource that runs before the Spring ApplicationContext is created. It receives a DynamicPropertyRegistry where you can add properties whose values come from the container (getJdbcUrl(), getMappedPort(), etc.). Because the container is a @Container static field (lifecycle tied to the test class or suite), it starts before the @DynamicPropertySource method runs, and the Spring context starts after, using the correct container-assigned ports. For sharing containers across the entire test suite (avoiding restart per class), use a base test class with a @Container static field and inherit it. Spring Boot 3.1+ also supports a ServiceConnection abstraction that eliminates @DynamicPropertySource boilerplate.

**Q: What is the risk of using @MockBean in a base test class?**
When @MockBean is in a base class, every test class that extends that base shares the same @MockBean declaration. This is actually desirable — it means all those test classes use the same ApplicationContext key, which maximizes cache hits. The risk is that the base class's @MockBean declarations may be overly broad: mocking beans that some tests want to be real, forcing every subclass to work with mocked versions of those beans. A secondary risk is that adding @MockBean to the base class invalidates the cache for all existing tests that previously shared a context without that @MockBean. Design the base class carefully — it should contain the minimal set of @MockBeans that all subclasses require.

**Q: How do you test asynchronous behavior (e.g., @Async methods or Kafka consumers) in Spring Boot?**
For @Async methods, inject the result as a CompletableFuture and call .get(timeout, TimeUnit) to block until completion, or use Awaitility (await().atMost(5, SECONDS).until(() -> condition)). Never use Thread.sleep() — it makes tests brittle. For Kafka consumers in @SpringBootTest with @EmbeddedKafka or Testcontainers, publish a message via KafkaTemplate and use Awaitility to poll the database or a CountDownLatch in the listener for up to a few seconds. The CountDownLatch approach: inject a test-controlled CountDownLatch into the listener bean, decrement it when a message is processed, and await it in the test. Reset the latch with @BeforeEach.

**Q: What is MockMvc's jsonPath() assertion and what library powers it?**
jsonPath() uses the Jayway JsonPath library (json-path), which applies XPath-like path expressions to JSON documents. Common patterns: jsonPath("$.field").value("expected") for exact match, jsonPath("$.array").isArray() for type check, jsonPath("$.array", hasSize(3)) for collection size (using Hamcrest matcher), jsonPath("$.nested.field").exists() for presence check. For testing large or complex JSON structures, prefer ResponseBody.content().json("{}") with AssertJ or rest-assured's JsonPath. Avoid overly specific jsonPath assertions on fields that frequently change (e.g., timestamps, generated IDs) — use isNotEmpty() or exists() instead of exact value matching for volatile fields.

**Q: What does @Sql do and how does it differ from using @BeforeEach with JdbcTemplate?**
@Sql executes SQL scripts against the test database at a specified execution phase (BEFORE_TEST_METHOD by default, or AFTER_TEST_METHOD for cleanup). It is declarative — the script file is loaded from the classpath and executed as a unit. @BeforeEach with JdbcTemplate is imperative — Java code constructs and executes SQL statements. @Sql is preferred for complex test data setup (large INSERT scripts, schema changes) because the SQL is readable, version-controlled, and reusable across tests. @BeforeEach with JdbcTemplate is preferred when test data depends on runtime values or when you want type-safe construction of test records using repositories. Both approaches work — choose based on data complexity.

**Q: How does @RestClientTest differ from @WebMvcTest?**
@WebMvcTest tests the server side — it loads the DispatcherServlet and tests how your controllers respond to incoming requests. @RestClientTest tests the client side — it loads beans that make outgoing HTTP calls via RestTemplate or RestTemplateBuilder, and provides a MockRestServiceServer to stub and verify those outbound calls. Use @RestClientTest when testing service beans that call external REST APIs, verifying that they construct the correct HTTP requests, handle error responses correctly (404 -> NotFoundException, 503 -> retry), and deserialize responses properly. It does NOT load controllers, services unrelated to the HTTP client, or the JPA layer.

**Q: Explain the difference between a static @Container field and an instance @Container field in Testcontainers.**
A static @Container field is started once before any test in the class runs and stopped after the last test completes (lifecycle: test class). All test methods in the class share the same container instance. A non-static @Container field is started before each test method and stopped after each — creating and destroying a container per test. Non-static containers are slower but provide complete isolation. In practice, use static containers for performance, and rely on @Transactional rollback or @Sql cleanup scripts for data isolation between tests. For container reuse across multiple test classes, use Ryuk-based singleton containers or a shared base class with a static @Container.

**Q: What is @JsonTest and what does it actually configure?**
@JsonTest loads a minimal Spring ApplicationContext containing only Jackson (or Gson, or Jsonb) auto-configuration and any @JsonComponent beans you have defined. It does NOT load controllers, services, repositories, or any other infrastructure. It provides JacksonTester<T>, GsonTester<T>, etc. for fluent assertion on serialized and deserialized JSON. Use @JsonTest to verify custom serializers, custom deserializers, Jackson module configuration (e.g., JavaTimeModule, custom mixins), and @JsonProperty mappings. This is far faster than loading a full context to test JSON behavior and catches serialization regressions early.

**Q: How do you use @WithMockUser and @WithSecurityContext in security tests?**
@WithMockUser populates the Spring Security SecurityContext with a UsernamePasswordAuthenticationToken containing the specified username, password, and roles before the test method executes. The roles are automatically prefixed with ROLE_ (so roles="USER" creates ROLE_USER). Use it to test secured endpoints in @WebMvcTest without an actual authentication flow. @WithSecurityContext is lower-level — you implement a WithSecurityContextFactory that builds a SecurityContext from a custom annotation. Use @WithSecurityContext when you need non-standard authentication types (OAuth2 tokens, JWT claims, multi-tenant principal objects) that @WithMockUser cannot express.

---

## 13. Best Practices

**Follow the test pyramid**: Write many unit tests (no Spring context, fast), fewer slice tests (@WebMvcTest, @DataJpaTest, medium speed), and even fewer full @SpringBootTest integration tests. The majority of test runtime should be at the unit level.

**Maximize context cache hits**: Design test base classes with a canonical set of @MockBeans so that all integration tests sharing the same set reuse one context. Audit test suite startup logs for "Creating shared instance" entries to count unique context loads.

**Never use H2 for PostgreSQL-specific features**: Discover query compatibility issues in tests, not production. Use @AutoConfigureTestDatabase(replace=NONE) with Testcontainers whenever queries use database-specific syntax.

**Use Awaitility for async assertions**: Replace Thread.sleep() with Awaitility's await().atMost(10, SECONDS).until(() -> assertion). This avoids test timing fragility and documents the expected completion window explicitly.

**Prefer AssertJ over Hamcrest**: AssertJ provides better IDE completion, better failure messages, and a fluent API consistent with modern Java. Spring Boot's starter-test includes AssertJ.

**Name test methods descriptively**: Follow the pattern `methodUnderTest_condition_expectedBehavior`: e.g., `placeOrder_insufficientInventory_throwsException`. This makes CI failure reports immediately actionable.

**Use @Sql over @BeforeEach for complex data setup**: SQL scripts are readable, version-controlled alongside the schema, and independent of Java object mapping. Reserve @BeforeEach for dynamic test data that depends on runtime-generated values.

**Test exception paths explicitly**: Verify that 400/404/500 responses include the correct error structure, not just the status code. Exception handlers are frequently broken silently when adding new error types.

**Verify MockMvc interactions on @MockBeans**: After asserting the response, verify that the service mock was called with the expected arguments using BDDMockito.then(service).should().methodName(argCaptor.capture()). This prevents controllers from returning hardcoded responses while ignoring service delegation.

**Separate integration tests from unit tests**: Use Maven Failsafe plugin with a naming convention (IT suffix) to run integration tests separately from unit tests. This allows fast local feedback (unit tests in <10s) and comprehensive CI validation (integration tests in 2–5 minutes).

---

## 14. Case Study

### Problem: Slow Test Suite Blocking CI Pipeline

A Spring Boot microservice had 180 test classes. The CI pipeline was taking 22 minutes to run the test suite, blocking developer productivity. The root causes were:

1. Every test class annotated with @SpringBootTest was creating its own ApplicationContext because each had a slightly different combination of @MockBeans — 47 unique context configurations across 180 test classes.
2. 15 @DataJpaTest classes used H2 but the service was running on PostgreSQL. Several queries with PostgreSQL-specific operators were passing on H2 but failing in production with syntax errors.
3. 8 test classes used @DirtiesContext to ensure clean state after tests, each forcing a context reload.
4. Async consumer tests used Thread.sleep(2000) to wait for Kafka message processing — adding 2 seconds per test.

### Analysis

The team ran the test suite with JVM test logging enabled and counted ApplicationContext creation events:
- 47 unique context keys were found
- 12 were caused by @MockBean(EmailService.class) appearing in some classes but not others
- 8 were caused by @DirtiesContext
- Context startup averaged 8 seconds: 47 * 8s = 376 seconds of context startup alone

### Fix 1 — Consolidate @MockBeans in a base class

```java
@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {
    @MockBean EmailService emailService;
    @MockBean NotificationService notificationService;
    @MockBean PaymentGatewayClient paymentGatewayClient;
    // All integration test classes extend this
}
// Result: 47 context variations reduced to 3 (base, slice tests, no-mock unit-style)
// Context loads: 3 * 8s = 24s instead of 376s
```

### Fix 2 — Replace H2 with Testcontainers PostgreSQL for repository tests

```java
// Single shared PostgreSQL container via base class
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class BaseRepositoryTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:15-alpine").withReuse(true); // Testcontainers reuse
    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
// Result: 5 query bugs discovered and fixed that were passing silently on H2
```

### Fix 3 — Remove @DirtiesContext, use @Transactional

All 8 tests using @DirtiesContext were using it to "ensure clean state." In 6 of 8 cases, @Transactional with auto-rollback achieved the same result. The remaining 2 tests were testing CacheManager eviction, which genuinely required context state changes — those were kept with @DirtiesContext and isolated in a separate test class.

### Fix 4 — Replace Thread.sleep() with Awaitility

```java
// Before: Thread.sleep(2000) in 23 Kafka consumer tests = 46 seconds
// After:
await().atMost(5, SECONDS)
       .pollInterval(100, MILLISECONDS)
       .until(() -> orderRepo.findByStatus("PROCESSED").size() == 1);
// Average actual wait: 150ms. No wasted time.
```

### Results

| Metric | Before | After |
|--------|--------|-------|
| CI pipeline duration | 22 minutes | 4.5 minutes |
| Unique context loads | 47 | 3 |
| Production query bugs caught by tests | 0 | 5 |
| Thread.sleep() wasted time | 46 seconds | ~3 seconds |
| @DirtiesContext invocations | 8 | 2 |

The key insight was that test performance is a first-class concern. Every @MockBean and @DirtiesContext annotation has a measurable cost in context load time. Treating the test architecture with the same intentional design as production code — shared base classes, canonical context configurations, realistic databases — reduced CI time by 80% without reducing test coverage.

---

**Expanded Case Study: Test Strategy for a Multi-Tenant Order Management System**

**Scenario:** An order management system (OMS) for a B2B marketplace has 14 Spring MVC controllers, 6 Spring Data JPA repositories, 2 Kafka listeners, 1 scheduled batch job, and 3 external REST integrations (inventory, shipping, payment). The team ships twice weekly. Full `@SpringBootTest` context starts in 45 seconds and takes 12 minutes for the full suite — CI is a bottleneck. The goal: restructure the test pyramid so CI finishes under 4 minutes without sacrificing coverage.

**Scale:** 14 controllers, 6 repos, 3 external clients, 1 batch job, 2 Kafka listeners → 3 test layers × ~150 tests each = ~450 tests total.

```
Test pyramid for the OMS:

  ┌─────────────────────────────────────────┐
  │  E2E / Contract tests (5)               │  ~45s each, run nightly
  │  Testcontainers (Postgres+Kafka full)   │
  ├─────────────────────────────────────────┤
  │  Integration tests (120)                │  ~3s each
  │  @WebMvcTest, @DataJpaTest              │  lightweight slices
  │  MockMvc + Testcontainers (single DB)   │
  ├─────────────────────────────────────────┤
  │  Unit tests (350)                       │  ~20ms each
  │  Plain JUnit 5 + Mockito                │  no Spring context
  └─────────────────────────────────────────┘

Total CI target: (350 × 20ms) + (120 × 3s) + (5 × 45s) = 7s + 360s + 225s ≈ 10min
After slicing:   (350 × 20ms) + (120 × 1.5s) + (5 × 45s) = 7s + 180s + 225s ≈ 6.9min
With context caching: reduces to ~4min
```

**Controller layer — @WebMvcTest with @MockBean:**

```java
@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc
class OrderControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    OrderService orderService;

    @MockBean
    SecurityConfig securityConfig;

    @Test
    @WithMockUser(roles = "BUYER")
    void createOrder_validRequest_returns201() throws Exception {
        var request = new CreateOrderRequest("tenant-1", List.of("sku-42"), 2);
        var response = new OrderResponse(UUID.randomUUID(), "PENDING");

        given(orderService.createOrder(any())).willReturn(response);

        mockMvc.perform(post("/orders")
                   .contentType(APPLICATION_JSON)
                   .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.status").value("PENDING"));

        verify(orderService).createOrder(argThat(r ->
            r.tenantId().equals("tenant-1") && r.quantity() == 2));
    }

    @Test
    void createOrder_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/orders").contentType(APPLICATION_JSON)
                   .content("{}"))
               .andExpect(status().isUnauthorized());
    }
}
```

**Repository layer — @DataJpaTest with Testcontainers:**

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class OrderRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("oms_test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired OrderRepository repo;
    @Autowired TestEntityManager em;

    @Test
    void findByTenantId_returnsOnlyTenantOrders() {
        em.persistAndFlush(new Order("tenant-A", "PENDING"));
        em.persistAndFlush(new Order("tenant-B", "SHIPPED"));

        List<Order> result = repo.findByTenantId("tenant-A");

        assertThat(result).hasSize(1)
            .extracting(Order::getTenantId).containsOnly("tenant-A");
    }
}
```

**Kafka listener — @SpringBootTest with EmbeddedKafka:**

```java
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"order-events"})
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.consumer.auto-offset-reset=earliest"
})
class OrderEventListenerTest {

    @Autowired KafkaTemplate<String, OrderEvent> template;
    @Autowired OrderRepository repo;

    @Test
    void onOrderShipped_updatesOrderStatus() throws Exception {
        var orderId = UUID.randomUUID();
        repo.save(new Order(orderId, "PENDING"));

        template.send("order-events",
            new OrderEvent(orderId, "SHIPPED", Instant.now()));

        // Poll up to 5s for async listener to process
        await().atMost(5, SECONDS)
               .until(() -> repo.findById(orderId)
                                .map(o -> "SHIPPED".equals(o.getStatus()))
                                .orElse(false));
    }
}
```

**BROKEN→FIX: @MockBean causes ApplicationContext cache miss**

```java
// BROKEN: three test classes each use @MockBean for different beans
// Spring cannot reuse the same context; starts 3 full contexts = 3 × 45s = 135s

@SpringBootTest
class OrderServiceTest {
    @MockBean InventoryClient inventoryClient;    // different mock set A
}

@SpringBootTest
class ShippingServiceTest {
    @MockBean ShippingClient shippingClient;      // different mock set B
}

// FIX: consolidate all @MockBean declarations in a shared @TestConfiguration
// that is loaded by all integration tests → Spring reuses ONE context

@TestConfiguration
class GlobalTestMocks {
    @MockBean InventoryClient inventoryClient;
    @MockBean ShippingClient shippingClient;
    @MockBean PaymentClient paymentClient;
}

// Each test class annotates with @Import(GlobalTestMocks.class)
// Result: single context for all integration tests → 45s → 45s (not 45s × N)
```

**BROKEN→FIX: Testing @Transactional service with @Rollback vs real commit**

```java
// BROKEN: @Transactional on test class auto-rolls back;
// Kafka outbox message (saved in a committed tx) is never sent — test passes
// but production fails because Kafka listener never sees the event

@SpringBootTest
@Transactional  // rolls back — outbox never committed!
class OrderServiceIntegrationTest {
    @Test
    void createOrder_sendsKafkaEvent() { /* always passes, outbox not checked */ }
}

// FIX: don't annotate test class with @Transactional; instead,
// use @Sql to set up and tear down data explicitly
@SpringBootTest
@Sql("/sql/clean.sql")    // before each test
class OrderServiceIntegrationTest {
    @Test
    void createOrder_sendsKafkaEvent() {
        service.createOrder(request);
        // tx commits → outbox row committed → poll Kafka for the event
        await().atMost(3, SECONDS).until(() -> kafkaConsumer.hasReceived("order-events"));
    }
}
```

**Context caching results after restructure:**

| Phase | Before | After |
|---|---|---|
| Unit tests (350) | 7s | 7s |
| @WebMvcTest (60) | 180s (3 contexts × 45s) | 45s (1 shared context) |
| @DataJpaTest (40) | 120s | 60s (Testcontainers reused) |
| @SpringBootTest (20) | 300s | 90s (1 context, @MockBean consolidated) |
| Total CI | ~607s | ~202s |

**Interview discussion points:**

**What is the ApplicationContext cache in Spring tests and how do you maximize reuse?** Spring caches `ApplicationContext` instances keyed by: the set of configuration classes, active profiles, context customizers, and the set of `@MockBean`/`@SpyBean` declarations. Any difference in this key starts a new context. Consolidating all `@MockBean` into a shared `@TestConfiguration` and using the same profile everywhere maximizes cache hits.

**When should you use @WebMvcTest instead of @SpringBootTest for controller tests?** `@WebMvcTest` loads only the web layer: controllers, filters, `@ControllerAdvice`, message converters. It skips JPA, Kafka, scheduling, and security configuration (or lets you partial-mock it). Tests start in ~1s vs 45s for full context. Use `@WebMvcTest` whenever you're testing request mapping, serialization, validation, or authentication; use `@SpringBootTest` only when you need the full wiring.

**How do you test a @KafkaListener without a real Kafka broker?** Use `@EmbeddedKafka` from `spring-kafka-test`. It starts an in-process Kafka broker, overrides `spring.kafka.bootstrap-servers` via `@TestPropertySource`, and runs at full speed. Alternatively use Testcontainers with a real Kafka image for higher fidelity (catches version-specific behavior). Always set `auto.offset.reset=earliest` in tests so the consumer sees messages published before it started.

**What is @DynamicPropertySource and when is it essential?** It registers property overrides after the Testcontainers container has started and its random port is known. Without it, you'd need to hard-code ports in `@TestPropertySource` or use fixed-port containers. Use it whenever a test infrastructure component (Postgres, Redis, Kafka) binds to a random port.

**How do you test security — authentication and authorization — without a real auth server?** Use `@WithMockUser` for simple role-based tests. For OAuth2, use `SecurityMockMvcRequestPostProcessors.jwt()` or `oidcLogin()` from Spring Security Test to inject a synthetic JWT claims set. Never start a real Keycloak or auth server in unit/integration tests — mock the token validation boundary instead.

---

## Related / See Also

- [Spring Boot Auto-Configuration](../spring_boot_autoconfiguration/README.md) — test slices
- [Case Study: Testcontainers & Test Strategy](../case_studies/cross_cutting/testcontainers_and_test_strategy.md) — @ServiceConnection, integration tests
