# Validation and Error Handling in Spring

## 1. Concept Overview

Spring's validation and error handling stack combines the Java Bean Validation specification (JSR-380 / Hibernate Validator) with Spring MVC's exception resolution pipeline and Spring Boot 3's RFC 7807 `ProblemDetail` support.

Key components:
- **Bean Validation (JSR-380)** — constraint annotations (`@NotNull`, `@Size`, `@Pattern`, custom `@ConstraintValidator`)
- **`@Valid`** — triggers JSR-380 validation on a method parameter (Spring MVC, WebFlux)
- **`@Validated`** — Spring wrapper; adds validation groups + enables method-level validation on non-`@Controller` beans via AOP
- **`MethodValidationPostProcessor`** — enables `@Validated` method validation on services/repositories
- **`@ControllerAdvice` / `@ExceptionHandler`** — centralised exception-to-response mapping
- **`ProblemDetail`** — RFC 7807-compliant error response body (Spring Boot 3+ / Spring Framework 6)
- **`spring.mvc.problemdetails.enabled=true`** — auto-enables ProblemDetail for standard Spring exceptions

Spring Boot 3.0 / Spring Framework 6 made `ProblemDetail` the standard error response format for built-in exceptions (`MethodArgumentNotValidException`, `ConstraintViolationException`, etc.).

---

## 2. Intuition

> Validation is the API's contract enforcer: every public method should specify what it requires, and the contract should be checked at the system boundary — not scattered throughout the business logic with `if (x == null)` guards.

**Key insight:** Bean Validation moves contract enforcement from imperative code (`if (!valid) throw ...`) to declarative annotations. This has two effects: (1) constraints are documented alongside the field definition — no hunting through code for validation logic; (2) a single `@Valid` annotation triggers all constraints in one pass, returning all violations at once rather than failing on the first one.

**Why this matters:** Most teams apply `@Valid` only at the `@Controller` layer. Senior engineers understand that `@Validated` + `MethodValidationPostProcessor` extends this to the service layer — critical for internal APIs called programmatically (not via HTTP), where invalid input should fail fast rather than propagate to the database.

---

## 3. Core Principles

1. **Validate at the boundary**: validate HTTP request bodies, `@RequestParam`, path variables, and service method inputs — not deep inside domain logic.
2. **`@Valid` vs `@Validated`**: `@Valid` = JSR-380 validation without group support; `@Validated` = Spring extension with groups + method-level AOP validation on any Spring bean.
3. **All violations at once**: Bean Validation collects ALL constraint violations before throwing `ConstraintViolationException` (service) or `MethodArgumentNotValidException` (MVC). Clients can fix all errors in one round trip.
4. **`@ControllerAdvice` centralises error mapping**: `@ExceptionHandler` methods in a `@ControllerAdvice` class handle exceptions thrown by any controller, avoiding duplicated error-handling code.
5. **`ProblemDetail` (RFC 7807)**: Spring Boot 3+ returns structured JSON error bodies (`type`, `title`, `status`, `detail`, `instance`) by default for Spring's built-in exceptions.
6. **Validation groups allow phased constraints**: annotate constraints with `groups` to apply different rules for CREATE vs UPDATE operations (e.g., `id` is forbidden on CREATE, required on UPDATE).

---

## 4. Types / Architectures / Strategies

### 4.1 @Valid vs @Validated

| Feature | `@Valid` (JSR-380) | `@Validated` (Spring) |
|---|---|---|
| Standard | JSR-380 (Jakarta Validation 3.0) | Spring Framework extension |
| Validation groups | No | Yes — `@Validated(Create.class)` |
| Usage in `@Controller` | Yes (parameter-level) | Yes (class-level for group selection) |
| Usage in `@Service` | No (no AOP proxy) | Yes — requires `MethodValidationPostProcessor` |
| Cascade to nested objects | Yes — `@Valid` on a field triggers nested validation | Yes |
| Method return value validation | No | Yes — `@Validated` + `@NotNull` on return type |

### 4.2 Built-in Constraint Annotations

| Annotation | Type | Description |
|---|---|---|
| `@NotNull` | Any | Not null |
| `@NotEmpty` | String, Collection, Map, Array | Not null and not empty |
| `@NotBlank` | String | Not null, not empty, not whitespace-only |
| `@Size(min, max)` | String, Collection, Array | Length/size within range |
| `@Min(value)` / `@Max(value)` | Integer, Long | Numeric range |
| `@DecimalMin` / `@DecimalMax` | BigDecimal, String | Inclusive/exclusive decimal range |
| `@Pattern(regexp)` | String | Regex match |
| `@Email` | String | Valid email format |
| `@Positive` / `@PositiveOrZero` | Numeric | > 0 or >= 0 |
| `@Future` / `@Past` | Temporal | Date in future/past |
| `@Valid` | Object field | Triggers cascade validation on nested object |

---

## 5. Architecture Diagrams

### Request Validation Pipeline (Spring MVC)

```
  HTTP POST /orders  {body}
      │
      ▼
  DispatcherServlet
      │
      ▼
  HandlerAdapter → resolves @RequestBody
      │
      ├── HttpMessageConverter.read()  ← parse JSON to CreateOrderRequest
      │
      ├── @Valid annotation present?
      │     └─ YES: invoke Validator
      │           ├─ all constraints pass? → continue
      │           └─ violations found?    → throw MethodArgumentNotValidException
      │
      ▼
  @RestController method executes
      │  (if @Validated on class + MethodValidationPostProcessor)
      │  ├─ validate @RequestParam / @PathVariable via AOP proxy
      │
      ▼
  Response
      │
  [on MethodArgumentNotValidException]
  @ControllerAdvice / ResponseEntityExceptionHandler
      │
      ▼
  ProblemDetail (RFC 7807) response:
  {
    "type": "about:blank",
    "title": "Bad Request",
    "status": 400,
    "detail": "name: must not be blank; quantity: must be greater than 0",
    "instance": "/orders"
  }
```

### Exception Handling Resolution Order

```
  Exception thrown in Controller
      │
      ├─ 1. @ExceptionHandler on the same controller
      │
      ├─ 2. @ExceptionHandler in @ControllerAdvice (ordered by @Order)
      │
      ├─ 3. ResponseEntityExceptionHandler (base class for Boot 3 ProblemDetail)
      │
      └─ 4. DefaultHandlerExceptionResolver → 500 response
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 Basic @Valid on @RequestBody

```java
public record CreateOrderRequest(
    @NotBlank(message = "Product name is required") String productName,
    @Positive(message = "Quantity must be positive") int quantity,
    @NotNull @DecimalMin("0.01") BigDecimal price,
    @Valid @NotNull ShippingAddress address   // cascade: validates Address fields too
) {}

@RestController
@RequestMapping("/orders")
public class OrderController {

    @PostMapping
    public ResponseEntity<Order> create(@Valid @RequestBody CreateOrderRequest req) {
        // If any constraint fails: MethodArgumentNotValidException is thrown BEFORE
        // this method body executes. Controller code never runs with invalid input.
        return ResponseEntity.status(201).body(orderService.create(req));
    }
}
```

### 6.2 Validation Groups for CREATE vs UPDATE

```java
// Group marker interfaces
public interface OnCreate {}
public interface OnUpdate {}

public record ProductRequest(
    @Null(groups = OnCreate.class, message = "ID must be null on create")
    @NotNull(groups = OnUpdate.class, message = "ID required for update")
    Long id,

    @NotBlank(groups = {OnCreate.class, OnUpdate.class})
    String name,

    @Positive(groups = OnCreate.class)
    int initialStock
) {}

@RestController
public class ProductController {
    @PostMapping("/products")
    public ResponseEntity<Product> create(
            @Validated(OnCreate.class) @RequestBody ProductRequest req) {
        // id must be null; initialStock must be positive
        return ResponseEntity.status(201).body(service.create(req));
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<Product> update(
            @Validated(OnUpdate.class) @RequestBody ProductRequest req) {
        // id must not be null; initialStock not validated
        return ResponseEntity.ok(service.update(req));
    }
}
```

### 6.3 Custom ConstraintValidator

```java
// Custom constraint annotation
@Documented
@Constraint(validatedBy = UniqueEmailValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface UniqueEmail {
    String message() default "Email address is already registered";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

// Validator implementation — Spring-managed, injection supported
@Component
public class UniqueEmailValidator implements ConstraintValidator<UniqueEmail, String> {

    private final UserRepository userRepo;

    public UniqueEmailValidator(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        if (email == null) return true;  // let @NotNull handle null case
        return !userRepo.existsByEmail(email);
    }
}

// Usage
public record RegisterRequest(
    @UniqueEmail @Email @NotBlank String email,
    @NotBlank @Size(min = 8) String password
) {}
```

### 6.4 @Validated on @Service (Method-Level Validation)

```java
// Enable method validation for all @Validated beans
@Configuration
public class ValidationConfig {
    @Bean
    public MethodValidationPostProcessor methodValidationPostProcessor(
            jakarta.validation.Validator validator) {
        MethodValidationPostProcessor pp = new MethodValidationPostProcessor();
        pp.setValidator(validator);
        return pp;
    }
}
// Spring Boot 3 auto-configures this; explicit bean only needed for customization.

// Service with method-level validation
@Service
@Validated  // wraps entire class in a validating AOP proxy
public class OrderService {

    public Order createOrder(@Valid @NotNull CreateOrderRequest req) {
        // ConstraintViolationException if req is null or any @Valid constraint fails
        return repository.save(req.toEntity());
    }

    @NotNull  // validate return value
    public Order findById(@Positive long id) {
        // ConstraintViolationException if id <= 0
        return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(id));
    }
}
```

### 6.5 @ControllerAdvice with ProblemDetail (Spring Boot 3+)

```java
// Spring Boot 3.x: enable ProblemDetail for built-in Spring exceptions
// application.yaml:
//   spring:
//     mvc:
//       problemdetails:
//         enabled: true

// Custom exception handler extending ResponseEntityExceptionHandler (Spring 6 base class)
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // Override built-in handler for validation errors
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        ProblemDetail pd = ex.getBody();   // ProblemDetail already populated by Spring 6
        pd.setProperty("violations",
            ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(), "message", fe.getDefaultMessage()))
                .toList());
        return ResponseEntity.status(status).body(pd);
    }

    // Handle custom application exceptions
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Resource Not Found");
        pd.setInstance(URI.create(req.getRequestURI()));
        pd.setProperty("resourceId", ex.getResourceId());
        return pd;
    }

    // Handle @Service-level ConstraintViolationException
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Validation Failed");
        pd.setDetail("One or more fields failed validation");
        pd.setProperty("violations",
            ex.getConstraintViolations().stream()
                .map(cv -> Map.of(
                    "field", cv.getPropertyPath().toString(),
                    "message", cv.getMessage()
                ))
                .toList());
        return pd;
    }
}
```

### 6.6 BROKEN vs FIX — Validation Group Ordering Issue

```java
// BROKEN: applying two group annotations independently — both groups validate independently
// @Validated(OnCreate.class) AND @Validated(OnUpdate.class) on same method does NOT exist.
// You can only pass ONE @Validated(...) or multiple groups to the same annotation.

// BROKEN: using @Valid instead of @Validated for groups
@PostMapping
public ResponseEntity<Product> create(@Valid @RequestBody ProductRequest req) {
    // @Valid does NOT process groups — OnCreate.class constraint on 'id' is NOT checked
}

// FIX: use @Validated(OnCreate.class) for group selection
@PostMapping
public ResponseEntity<Product> create(@Validated(OnCreate.class) @RequestBody ProductRequest req) {
    // Only constraints in OnCreate.class group are active
}
```

### 6.7 API Versioning Strategies

A related concern to error handling: how to evolve the API contract without breaking existing clients.

| Strategy | Implementation | Pros | Cons |
|---|---|---|---|
| URI versioning | `/v1/orders`, `/v2/orders` | Explicit, cache-friendly | URL proliferation; clients must update |
| Header versioning | `Accept: application/vnd.myapi.v2+json` | Clean URIs | Harder to test in browser |
| Query param | `?version=2` | Simple | Pollutes query strings |
| No versioning (additive) | Add new fields, never remove | Zero breaking changes | Accumulates cruft; unclear deprecation |
| Content negotiation | Media type negotiation via `Accept` | RESTful | Complex content negotiation setup |

Spring MVC: use `@RequestMapping(value="/orders", produces="application/vnd.myapi.v2+json")` for media-type versioning, or separate `@RestController` classes per version for URI versioning.

---

## 7. Real-World Examples

### 7.1 GitHub API Error Responses (RFC 7807 Style)

GitHub's REST API returns error bodies similar to RFC 7807:
```json
{
  "message": "Validation Failed",
  "errors": [{"field": "name", "code": "missing_field"}],
  "documentation_url": "https://docs.github.com/..."
}
```
Spring Boot 3's `ProblemDetail` follows the same pattern but with the standardised RFC 7807 fields (`type`, `title`, `status`, `detail`, `instance`) plus custom extension fields via `setProperty()`.

### 7.2 Stripe API — Idempotency + Validation Error Codes

Stripe returns structured error codes (`"error_code": "invalid_number"`) allowing programmatic error handling. Spring's `ProblemDetail.setProperty("errorCode", "INVALID_CARD_NUMBER")` enables the same pattern — clients can branch on error codes rather than parsing human-readable messages.

### 7.3 Spring Boot Actuator Health as Implicit Validation

`HealthIndicator` is a form of operational validation: it checks system constraints (DB connectivity, disk space, cache availability) and returns structured results. This reuses the constraint-checking mindset at the operational level.

---

## 8. Tradeoffs

| Approach | Implementation Cost | Error Granularity | Client UX |
|---|---|---|---|
| Manual `if/throw` | Low | Fails on first error | Bad — must fix one at a time |
| Bean Validation (`@Valid`) | Low | All errors at once | Good — all violations returned |
| Custom `ConstraintValidator` | Medium | Per-field, rich messages | Good — domain-specific messages |
| `@ControllerAdvice` + `ProblemDetail` | Medium | Centralised, RFC 7807 | Good — machine-readable errors |
| GraphQL / gRPC validation | High | Schema-level | Excellent — typed errors |

---

## 9. When to Use / When NOT to Use

### Use Bean Validation when:
- Input comes from HTTP (REST API, form submission, GraphQL arguments)
- Data crosses a service boundary (inter-service calls via REST, Feign clients)
- Internal service methods need contract enforcement (`@Validated` on `@Service`)
- You want validation rules co-located with the data class (not scattered in controller/service logic)

### Do NOT use Bean Validation when:
- Business rules are complex and stateful (e.g., "discount is valid only if the customer has purchased > $500 this year") — use a domain service with explicit validation logic
- Cross-field validation with complex interdependencies — consider a dedicated `Validator` class (Spring's `org.springframework.validation.Validator` interface, not JSR-380)
- Performance-critical paths with very high validation volume — Hibernate Validator is fast but not free; avoid validating in tightest inner loops

---

## 10. Common Pitfalls

### Pitfall 1: @Valid cascade not working for nested objects
```java
// BROKEN: nested Address is not validated
public record CreateOrderRequest(
    @NotBlank String productName,
    ShippingAddress address   // missing @Valid — Address fields are NOT validated
) {}

// FIX: add @Valid to cascade
public record CreateOrderRequest(
    @NotBlank String productName,
    @Valid @NotNull ShippingAddress address   // now Address fields are validated
) {}
```

### Pitfall 2: @Validated on a @Service bean not working (no proxy)
```java
// BROKEN: no MethodValidationPostProcessor → @Validated has no effect on @Service
@Service
@Validated
public class ProductService {
    public Product find(@Positive long id) { ... }
    // ConstraintViolationException is NEVER thrown even if id < 0
}

// FIX: ensure MethodValidationPostProcessor is registered (Spring Boot 3 auto-configures it;
// in pure Spring check that @EnableMethodValidation or the MVPP bean is present)
```

### Pitfall 3: Swallowing ConstraintViolationException at service layer
If `@ControllerAdvice` only handles `MethodArgumentNotValidException` (MVC-level), `ConstraintViolationException` from `@Validated` on a service will propagate as a 500. Add a separate `@ExceptionHandler(ConstraintViolationException.class)` to map it to 400.

### Pitfall 4: Validation in @Async method — no transaction context
If a `@Validated` service method is called via `@Async`, the validation AOP proxy runs on the async thread. If the validator needs a DB lookup (like `UniqueEmailValidator`), the transaction from the caller is not available. The validator must open its own transaction — annotate the validator's repository method with `@Transactional(readOnly=true, propagation=SUPPORTS)`.

### Pitfall 5: `@ControllerAdvice` not applying to `@Controller` beans from different packages
`@ControllerAdvice` scans `basePackages` of `@SpringBootApplication` by default. A `@Controller` in a non-scanned package is not covered. Explicitly set `@ControllerAdvice(basePackages = "com.example")` or ensure all controllers are within the scan path.

---

## 11. Technologies & Tools

| Tool / Feature | Version | Purpose |
|---|---|---|
| Jakarta Bean Validation 3.0 (JSR-380) | Java EE 8 / Jakarta EE 9+ | Constraint annotation specification |
| Hibernate Validator 8.x | Spring Boot 3.x default | JSR-380 reference implementation |
| `@Valid` | JSR-380 | Cascade validation trigger |
| `@Validated` | Spring Framework | Groups support + method-level validation |
| `MethodValidationPostProcessor` | Spring Framework | AOP proxy for service-level @Validated |
| `@ControllerAdvice` + `@ExceptionHandler` | Spring MVC | Centralised exception-to-response mapping |
| `ResponseEntityExceptionHandler` | Spring MVC | Base class for ProblemDetail error handling |
| `ProblemDetail` | Spring 6 / Boot 3 | RFC 7807-compliant error response object |
| `spring.mvc.problemdetails.enabled=true` | Spring Boot 3 | Auto-enables ProblemDetail for built-in exceptions |
| `ErrorAttributes` / `BasicErrorController` | Spring Boot | Customise `/error` fallback endpoint |
| `spring-boot-starter-validation` | Spring Boot | Includes Hibernate Validator; required for Bean Validation |

---

## 12. Interview Questions with Answers

**Q1: What is the difference between `@Valid` and `@Validated` in Spring?**
`@Valid` is the standard JSR-380 annotation (from `jakarta.validation`). It triggers constraint validation on the annotated parameter, including cascading to nested `@Valid` fields. It does not support validation groups. `@Validated` is Spring's extension (`org.springframework.validation.annotation.Validated`). It adds group selection — `@Validated(OnCreate.class)` activates only constraints in that group. At the class level, `@Validated` marks a bean for method-level validation via `MethodValidationPostProcessor` — enabling `@NotNull`, `@Size`, etc. on service and repository method parameters and return values. In Spring MVC controllers, both are interchangeable for non-group cases; only `@Validated` supports groups.

**Q2: What exception is thrown for MVC validation failure vs service-layer validation failure, and how do you handle both?**
When `@Valid` / `@Validated` fails on a `@Controller` `@RequestBody` parameter, Spring MVC throws `MethodArgumentNotValidException` (which extends `BindException`). When `@Validated` + `MethodValidationPostProcessor` fails on a service method parameter, Spring throws `ConstraintViolationException` (from `jakarta.validation`). Handle both in a `@ControllerAdvice`: `handleMethodArgumentNotValid()` via `ResponseEntityExceptionHandler` override for MVC-level errors, and a separate `@ExceptionHandler(ConstraintViolationException.class)` method for service-level errors. Map both to HTTP 400 with `ProblemDetail` bodies listing all violations.

**Q3: How do validation groups work, and what is a common use case?**
Validation groups are marker interfaces (empty, by convention). Each constraint annotation has a `groups` attribute — e.g., `@NotNull(groups = OnUpdate.class)`. Using `@Validated(OnUpdate.class)` activates only constraints whose `groups` include `OnUpdate.class`. The default group is `Default.class`. Use case: a `ProductRequest` record used for both `POST /products` (create) and `PUT /products/{id}` (update). For create: `id` must be `@Null` (not provided by client). For update: `id` must be `@NotNull` (must reference existing product). Without groups, you'd need two different request classes or manual `if` checks.

**Q4: How would you write a custom `ConstraintValidator` that checks a DB-level uniqueness constraint?**
Create an annotation (e.g., `@UniqueEmail`) with `@Constraint(validatedBy = UniqueEmailValidator.class)`. The validator class implements `ConstraintValidator<UniqueEmail, String>`. Because the validator is managed by Spring (via `@Component` + Hibernate Validator's Spring integration), `@Autowired` works inside it. The `isValid()` method calls `userRepository.existsByEmail(email)` — if the email exists, return `false` (violation). Important: return `true` for null values (let `@NotNull` handle null — compose constraints rather than duplicating logic). Gotcha: the validator runs inside a request transaction if invoked via Spring MVC, but may need `@Transactional(propagation = SUPPORTS)` if called from a non-transactional context.

**Q5: What is `ProblemDetail` and why was it introduced in Spring Boot 3?**
`ProblemDetail` (`org.springframework.http.ProblemDetail`) is Spring's implementation of RFC 7807 "Problem Details for HTTP APIs." Before Spring Boot 3, error response bodies were inconsistent — some APIs returned `{message: "..."}`, others returned `{error: "...", status: 400}`, with no standard structure. RFC 7807 defines: `type` (URI identifying the error kind), `title` (human-readable summary), `status` (HTTP status), `detail` (specific description for this occurrence), `instance` (URI of the specific request that failed), plus extensible custom properties. `ProblemDetail` lets `@ExceptionHandler` methods return a structured, machine-readable body without reinventing the format. Spring Boot 3 auto-applies it to built-in Spring exceptions (`MethodArgumentNotValidException`, `HttpMessageNotReadableException`, etc.) when `spring.mvc.problemdetails.enabled=true`.

**Q6: How does `@ControllerAdvice` determine which controllers it applies to?**
By default, `@ControllerAdvice` applies to all `@Controller` and `@RestController` beans in the application context (within the component-scan path). It can be scoped: `@ControllerAdvice(basePackages = "com.example.api")` — only controllers in that package; `@ControllerAdvice(assignableTypes = {OrderController.class})` — only that controller; `@ControllerAdvice(annotations = RestController.class)` — only REST controllers. Multiple `@ControllerAdvice` beans are ordered by `@Order` — lower order value = higher precedence. `ResponseEntityExceptionHandler` is typically extended as one `@ControllerAdvice`; additional specific handlers can be added with higher order priority.

**Q7: Describe a scenario where Bean Validation alone is insufficient and a domain validator is needed.**
Bean Validation is field-level and stateless — it cannot express: "If `discountCode` is set, `orderTotal` must be > $50" (cross-field rule), or "A user can only place 5 orders per day" (stateful/DB rule), or "The `expiryDate` must be after `startDate`" (multi-field temporal rule). For cross-field rules, use a class-level custom `@Constraint` + validator that receives the whole object, or a Spring `org.springframework.validation.Validator` with `validate(Object, Errors)`. For stateful/domain rules (rate limiting, quota), use an explicit service-layer validation method called before the business logic. Bean Validation is for syntactic constraints; domain rules require semantic validation in the domain model.

**Q8: What happens if an exception is thrown inside a `@ControllerAdvice @ExceptionHandler` method?**
If the `@ExceptionHandler` method itself throws an exception, Spring falls back to the `DefaultHandlerExceptionResolver`, which typically returns a 500 Internal Server Error with a minimal response body. The exception from the handler is propagated to the container (Tomcat/Jetty). To prevent this: wrap `@ExceptionHandler` bodies in try/catch; return a safe default `ProblemDetail` with status 500 if the handler itself encounters an unexpected error. This is particularly important for database calls inside exception handlers (e.g., `UniqueEmailValidator` calling the DB in response to a validation error).

**Q9: How does the HandlerExceptionResolver chain work in Spring MVC?**
Spring MVC resolves exceptions through a chain of `HandlerExceptionResolver` implementations, consulted in order: (1) `ExceptionHandlerExceptionResolver` — processes `@ExceptionHandler` methods in `@ControllerAdvice`; (2) `ResponseStatusExceptionResolver` — processes `@ResponseStatus` annotations on exception classes; (3) `DefaultHandlerExceptionResolver` — handles Spring-specific exceptions (`NoSuchRequestHandlingMethodException`, `MethodArgumentNotValidException` in pre-Boot-3, etc.); (4) `SimpleMappingExceptionResolver` — maps exception class names to view names (MVC, rarely used in REST). If no resolver handles the exception, Spring passes it to the servlet container (`response.sendError(500)`). `ResponseEntityExceptionHandler` (extended by custom `@ControllerAdvice`) hooks into `(1)`.

**Q10: How would you validate a `@RequestParam` or `@PathVariable` (not a request body)?**
Request params and path variables are primitive or String types, not POJOs — `@Valid` cascade does not apply. Two approaches: (1) `@Validated` at the class level + `MethodValidationPostProcessor` — put constraints directly on the method parameters: `public ResponseEntity<User> getUser(@PathVariable @Positive long id)`. When `id <= 0`, Spring throws `ConstraintViolationException` (map to 400 in `@ControllerAdvice`). (2) Custom `@ExceptionHandler(MethodArgumentTypeMismatchException.class)` for type conversion failures (e.g., `"abc"` as a `long` path variable). Spring Boot 3 with `problemdetails.enabled=true` handles type mismatch automatically.

**Q11: What is the difference between `@ResponseStatus` on an exception class and `@ExceptionHandler`?**
`@ResponseStatus(HttpStatus.NOT_FOUND)` on a custom exception class (e.g., `@ResponseStatus(HttpStatus.NOT_FOUND) class ResourceNotFoundException`) causes Spring to return the given status code automatically when that exception is thrown — no `@ExceptionHandler` needed. Limitation: the response body is the default error format (a `Map` with `timestamp`, `status`, `error`, `path`), not a `ProblemDetail`. `@ExceptionHandler` gives full control over the response body, status, and headers. For APIs that must return `ProblemDetail` or custom JSON bodies, `@ExceptionHandler` in a `@ControllerAdvice` is the correct approach. `@ResponseStatus` is useful for simple cases where only the status code matters.

**Q12: How does Spring Boot's `/error` fallback endpoint work and how do you customise it?**
When an exception escapes all `HandlerExceptionResolver` instances (or is thrown in a filter before the servlet dispatches), Tomcat/Jetty forward the request to `/error`. Spring Boot's `BasicErrorController` serves this endpoint, returning a `Map` of `{timestamp, status, error, message, path}`. Customise by: (1) extending `BasicErrorController` or implementing `ErrorController`; (2) implementing `ErrorAttributes` to change the attributes map; (3) adding a `DefaultErrorViewResolver` for HTML error pages (Thymeleaf). For REST APIs, override `errorHtmlAttributes()` and `error()` in `BasicErrorController` to return `ProblemDetail` consistently. This is the last line of defence — errors from filters (`AuthenticationException`, `AccessDeniedException`) end up here if not handled by the security filter chain.

**Q13: How do you write an integration test that verifies constraint violations return proper ProblemDetail responses?**
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class OrderControllerValidationTest {

    @Autowired MockMvc mockMvc;

    @Test
    void createOrder_blankName_returns400WithProblemDetail() throws Exception {
        mockMvc.perform(post("/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"productName": "", "quantity": 1, "price": 10.0}"""))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType("application/problem+json"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.violations[0].field").value("productName"))
            .andExpect(jsonPath("$.violations[0].message").value("Product name is required"));
    }
}
```
Verify `Content-Type: application/problem+json` — the RFC 7807 media type. `ProblemDetail` serialises to this content type in Spring Boot 3.

**Q14: What is `@AssertTrue` / `@AssertFalse` and when is it useful for cross-field validation?**
`@AssertTrue(message = "End date must be after start date")` on a method `isEndAfterStart()` that returns `boolean` provides lightweight cross-field validation without a custom `ConstraintValidator`. The method accesses `this.startDate` and `this.endDate`. This is appropriate for simple temporal and logical relationships. For complex cross-field rules (involving external lookups or multiple conditions), a class-level `@Constraint` validator is cleaner because it separates the validation logic from the data class. The `@AssertTrue` approach couples validation logic with the domain object, which violates separation of concerns in domain-driven designs.

**Q15: How would you globally configure the message source for constraint violation messages?**
Bean Validation uses `ValidationMessages.properties` in the classpath root for constraint message templates (e.g., `{jakarta.validation.constraints.NotBlank.message}`). Override messages by creating `src/main/resources/ValidationMessages.properties`:
```properties
jakarta.validation.constraints.NotBlank.message=This field is required
com.example.UniqueEmail.message=This email is already taken
```
Spring Boot also integrates with `MessageSource` — configure `spring.validation.message-source` or override the `ValidatorFactory`'s `MessageInterpolator` with a `MessageSourceMessageInterpolator` to use `messages.properties` (the standard Spring i18n file) for all constraint messages. This enables locale-aware error messages for internationalised APIs.

---

## 13. Best Practices

1. **Annotate request models with Bean Validation constraints** — colocate constraints with the data definition, not scattered in controller/service code.
2. **Use `@Valid` for cascade on nested objects** — without it, nested constraints are ignored.
3. **Use `@Validated(Group.class)` for CREATE vs UPDATE** — avoids duplicate request record classes.
4. **Extend `ResponseEntityExceptionHandler`** in your `@ControllerAdvice` to inherit Spring's built-in exception handling + ProblemDetail integration.
5. **Enable `spring.mvc.problemdetails.enabled=true`** in Spring Boot 3 — auto-applies RFC 7807 to standard Spring exceptions.
6. **Handle `ConstraintViolationException`** separately from `MethodArgumentNotValidException` — service-layer violations reach `@ControllerAdvice` as `ConstraintViolationException`, not `MethodArgumentNotValidException`.
7. **Return ALL violations at once** — populate `ProblemDetail.setProperty("violations", list)` with all `FieldError`s / `ConstraintViolation`s.
8. **Keep `@ExceptionHandler` methods exception-safe** — wrap with try/catch; never let a handler itself throw uncaught.
9. **Test validation in integration tests** (`@SpringBootTest` or `@WebMvcTest`) — unit tests of controllers don't activate the full Spring validation pipeline.
10. **Use `spring-boot-starter-validation`** explicitly in `pom.xml` / `build.gradle` — Hibernate Validator is not on the classpath by default in Spring Boot 3; the starter adds it.

---

## 14. Case Study

See the Spring case study: [Design a Multi-Tenant API](../case_studies/design_multitenant_api.md)

**Quick scenario:** An enterprise SaaS API validates request bodies at the HTTP layer with `@Valid` + `ProblemDetail`. Service-layer methods use `@Validated` to catch invalid tenant configurations before DB writes. A custom `@ActiveTenant` constraint validator checks that the `tenantId` parameter references a non-suspended tenant (DB lookup). All validation errors return `application/problem+json` responses with a `violations` extension field listing all constraint failures.

**Cross-links:**
- [Request Handling](../request_handling/README.md) — `@RequestMapping`, `@ControllerAdvice`, MVC exception handling
- [Spring MVC Architecture](../spring_mvc_architecture/README.md) — `HandlerExceptionResolver` chain
- [Spring Boot Autoconfiguration](../spring_boot_autoconfiguration/README.md) — how `spring-boot-starter-validation` auto-configures `MethodValidationPostProcessor`

---

## Related / See Also

- [Request Handling](../request_handling/README.md) — @ControllerAdvice, ProblemDetail
- [Spring MVC Architecture](../spring_mvc_architecture/README.md) — HandlerExceptionResolver
- [Case Study: Multi-Tenant API](../case_studies/design_multitenant_api.md) — tenant-aware error responses
