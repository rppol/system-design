# Spring Boot Configuration

## 1. Concept Overview

Spring Boot's configuration system provides a unified, hierarchical way to externalize application settings. It supports properties files, YAML files, environment variables, command-line arguments, and remote config servers — all merged into a single `Environment` with a well-defined priority order. `@ConfigurationProperties` provides type-safe, validated binding of configuration to Java classes with IDE support, relaxed binding, and structured documentation.

---

## 2. Intuition

Think of Spring Boot's configuration as a layered cake. The bottom layers are defaults baked in (application.properties in the JAR). Each upper layer can override lower layers: profile-specific files override base files; environment variables override files; command-line arguments override everything. The topmost layer always wins.

**One-line analogy:** Spring Boot configuration is a priority-ordered stack where each level overrides everything below it — you only configure what differs from the layer beneath.

**Key insight:** Property source order matters enormously in production. A common production gotcha: a developer sets a property in `application.properties` but the deployment team sets the same property via an OS environment variable. The env var wins — the developer's value is silently overridden.

---

## 3. Core Principles

1. **Priority ordering:** Higher-priority sources override lower-priority ones. Command-line args beat env vars beat properties files.
2. **Relaxed binding:** `app.max-connections`, `APP_MAX_CONNECTIONS`, `app.maxConnections` all bind to the same Java field.
3. **Profile-specific overrides:** `application-{profile}.properties` overrides `application.properties` when the profile is active.
4. **@ConfigurationProperties over @Value:** For any group of related properties, `@ConfigurationProperties` provides type safety, validation, metadata, and relaxed binding.
5. **Fail fast on missing required config:** Use `@Validated` + `@NotNull` on `@ConfigurationProperties` to catch misconfiguration at startup.

---

## 4. Types / Architectures / Strategies

### Property Source Priority (Highest to Lowest)

| Priority | Source |
|----------|--------|
| 1 (highest) | Command-line arguments (`--server.port=8081`) |
| 2 | SPRING_APPLICATION_JSON (inline JSON in env var) |
| 3 | ServletConfig init parameters |
| 4 | ServletContext init parameters |
| 5 | JNDI attributes from `java:comp/env` |
| 6 | Java System properties (`-Dserver.port=8081`) |
| 7 | OS environment variables (`SERVER_PORT=8081`) |
| 8 | Profile-specific files outside JAR (`application-{profile}.properties`) |
| 9 | Profile-specific files inside JAR |
| 10 | `application.properties` outside JAR |
| 11 | `application.properties` inside JAR |
| 12 | `@PropertySource` on `@Configuration` classes |
| 13 | Default properties (`SpringApplication.setDefaultProperties`) |

### @ConfigurationProperties vs @Value

| Feature | `@ConfigurationProperties` | `@Value` |
|---------|---------------------------|---------|
| Type safety | Full (compile-time binding) | No (String expression) |
| Relaxed binding | Yes | No |
| Validation (`@Validated`) | Yes | No |
| IDE autocompletion | Yes (with metadata processor) | No |
| Complex types (Duration, DataSize) | Yes | Limited |
| Lists/Maps | Yes | Limited |
| Re-binding on refresh | Yes (with `@RefreshScope`) | Yes (with `@RefreshScope`) |
| Default values | Via Java field initialization | In expression `${key:default}` |

---

## 5. Architecture Diagrams

```
Property Resolution Flow
=========================

  Application starts
         |
         v
  SpringApplication.run()
         |
         v
  EnvironmentPostProcessors run
  (add property sources: system props, env vars, config files)
         |
         v
  ConfigDataEnvironmentPostProcessor
  loads application.properties / application.yml
  and profile-specific variants
         |
         v
  Spring Environment
  +----------------------------------------------+
  | [0] CommandLinePropertySource                 |  --server.port=8081
  | [1] SystemProperties                          |  -Dserver.port=8082
  | [2] SystemEnvironmentPropertySource           |  SERVER_PORT=8083
  | [3] application-production.properties         |  server.port=8084
  | [4] application.properties                    |  server.port=8085
  +----------------------------------------------+
  getProperty("server.port") -> 8081 (first match wins)
```

```
@ConfigurationProperties Relaxed Binding
==========================================

  Java field: private int maxConnections;

  All of these bind to maxConnections:
  +-------------------------------------------+
  | app.max-connections=25  (kebab-case)       |  <- application.properties
  | APP_MAX_CONNECTIONS=25  (SCREAMING_SNAKE)  |  <- OS environment variable
  | app.maxConnections=25   (camelCase)        |  <- application.properties
  | app.max_connections=25  (underscore)       |  <- application.properties
  +-------------------------------------------+
  @Value("${app.max-connections}") would ONLY match exact key "app.max-connections"
```

---

## 6. How It Works — Detailed Mechanics

### @ConfigurationProperties — Type-Safe Binding

```java
// Properties class
@ConfigurationProperties(prefix = "app.database")
@Validated  // enables JSR-303 validation
public class DatabaseProperties {
    @NotEmpty
    private String url;

    @NotEmpty
    private String username;

    @Min(1) @Max(100)
    private int poolSize = 10;  // default value

    private Duration connectionTimeout = Duration.ofSeconds(30);  // Duration binding

    @DataSizeUnit(DataUnit.MEGABYTES)
    private DataSize maxMemory = DataSize.ofMegabytes(512);  // DataSize binding

    private List<String> allowedHosts = new ArrayList<>();

    private Map<String, String> connectionProperties = new LinkedHashMap<>();

    // Getters and setters required (or use @ConstructorBinding for immutable)
}

// Registration (two ways)
// Way 1: @EnableConfigurationProperties
@Configuration
@EnableConfigurationProperties(DatabaseProperties.class)
public class AppConfig { }

// Way 2: @ConfigurationPropertiesScan (Spring Boot 2.2+)
@SpringBootApplication
@ConfigurationPropertiesScan  // scans for @ConfigurationProperties in current package
public class MyApplication { }

// application.properties:
# app.database.url=jdbc:postgresql://localhost:5432/mydb
# app.database.username=app_user
# app.database.pool-size=25
# app.database.connection-timeout=10s
# app.database.max-memory=1GB
# app.database.allowed-hosts[0]=host1
# app.database.allowed-hosts[1]=host2
# app.database.connection-properties.ssl=true
# app.database.connection-properties.sslMode=verify-full
```

### Immutable @ConfigurationProperties with @ConstructorBinding

```java
// Spring Boot 2.2+: immutable properties via constructor binding
@ConfigurationProperties(prefix = "app.server")
@ConstructorBinding  // Spring Boot 2.x (optional in Boot 3.x if single constructor)
public class ServerProperties {
    private final int port;
    private final String host;
    private final Duration readTimeout;

    public ServerProperties(int port, String host, Duration readTimeout) {
        this.port = port;
        this.host = host;
        this.readTimeout = readTimeout;
    }

    // Only getters — no setters (immutable)
    public int getPort() { return port; }
    public String getHost() { return host; }
    public Duration getReadTimeout() { return readTimeout; }
}
```

### YAML Configuration

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mydb
    username: app_user
    hikari:
      maximum-pool-size: 25
      connection-timeout: 30000

app:
  database:
    allowed-hosts:
      - host1.example.com
      - host2.example.com
    connection-properties:
      ssl: "true"
      sslMode: verify-full

---
# Multi-document YAML — next document only active when profile matches
spring:
  config:
    activate:
      on-profile: production
  datasource:
    url: jdbc:postgresql://prod-db.internal:5432/mydb
```

### Profile-Specific Configuration

```yaml
# application.yml (base)
server:
  port: 8080
spring:
  datasource:
    url: jdbc:h2:mem:testdb

---
# Only active in 'production' profile
spring:
  config:
    activate:
      on-profile: production
  datasource:
    url: jdbc:postgresql://prod-db:5432/mydb
server:
  port: 80
```

```properties
# application.properties (base)
server.port=8080

# application-production.properties (overrides base when production profile active)
server.port=80
spring.datasource.url=jdbc:postgresql://prod-db:5432/mydb

# application-test.properties
spring.datasource.url=jdbc:h2:mem:testdb
```

### spring.config.import — Importing Additional Config

```properties
# application.properties
spring.config.import=optional:file:./config/extra.properties,\
                     optional:classpath:additional.properties,\
                     configserver:  # imports from Spring Cloud Config Server

# Kubernetes: import from config tree (mounted ConfigMap/Secret)
spring.config.import=optional:configtree:/etc/config/
# Files in /etc/config/ become properties: filename=filecontents
```

### EnvironmentPostProcessor — Programmatic Property Sources

```java
// Add custom property source before application starts
public class VaultEnvironmentPostProcessor implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                        SpringApplication application) {
        // Load secrets from Vault
        Map<String, Object> secrets = vaultClient.readSecrets("secret/myapp");
        MapPropertySource source = new MapPropertySource("vault", secrets);
        // Add with highest priority (first in list)
        environment.getPropertySources().addFirst(source);
    }
}

// Registration in META-INF/spring.factories:
// org.springframework.boot.env.EnvironmentPostProcessor=\
//   com.example.VaultEnvironmentPostProcessor
```

---

## 7. Real-World Examples

**Kubernetes deployment:** `SERVER_PORT`, `SPRING_DATASOURCE_URL`, and `SPRING_DATASOURCE_PASSWORD` are set as Kubernetes `env:` vars or `envFrom:` from a Secret. OS environment variables (priority 7) override the packaged `application.properties` (priority 11). The same JAR runs in dev (H2 database from properties) and production (PostgreSQL from env vars) with zero code change.

**Blue-green deployment:** `spring.config.import=configserver:` pulls configuration from Spring Cloud Config Server. Updating the Config Server and calling `/actuator/refresh` (or broadcasting via Spring Cloud Bus) live-reloads `@RefreshScope` beans without restarting the application.

**Feature flags:** `app.feature.new-checkout-flow.enabled=false` in `application.properties`. Overridden to `true` via environment variable during canary rollout. Overridden back to `false` via command-line argument during emergency rollback. All three override mechanisms work without code changes.

---

## 8. Tradeoffs

| Approach | Type Safety | IDE Support | Refresh | Complexity |
|----------|-------------|-------------|---------|------------|
| `@Value` | Low | Limited | With `@RefreshScope` | Low |
| `@ConfigurationProperties` | High | Excellent | With `@RefreshScope` | Medium |
| Programmatic `Environment` | None | None | Always current | Low |
| Spring Cloud Config | High (via CP) | Excellent | Push-based | High |
| Vault | High | Limited | Auto-renewal | High |

---

## 9. When to Use / When NOT to Use

**Use `@ConfigurationProperties` when:**
- You have 3+ related properties (database config, email config, API client config)
- You need validation, complex types (Duration, DataSize), or nested objects
- You want IDE autocompletion and documentation

**Use `@Value` when:**
- You need exactly one property in a class
- The value is a SpEL expression or system property

**Use environment variables when:**
- Setting secrets (passwords, API keys) — never put secrets in application.properties
- Kubernetes/Docker deployments
- Values that differ per environment

**Do NOT:**
- Put secrets in `application.properties` committed to source control
- Use `@Value` for complex/nested configuration
- Use YAML with `@PropertySource` (not supported — only `.properties` files)

---

## 10. Common Pitfalls

### Pitfall 1: @Value with YAML List (Does Not Work)

```yaml
# application.yml
app:
  allowed-hosts:
    - host1
    - host2
```

```java
// BROKEN: @Value cannot bind YAML lists
@Value("${app.allowed-hosts}")
private List<String> allowedHosts;  // injection fails or gets "[host1, host2]" as string

// FIXED: use @ConfigurationProperties
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private List<String> allowedHosts;  // properly bound as List<String>
}

// OR for @Value: use comma-separated property value (not YAML list):
// application.properties: app.allowed-hosts=host1,host2
@Value("${app.allowed-hosts}")
private List<String> allowedHosts;  // Spring auto-splits on comma
```

### Pitfall 2: @ConfigurationProperties Without Validation Failing Silently

```java
// BROKEN: invalid value accepted silently
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private int poolSize = 10;  // no validation
    // poolSize=-5 is accepted — no error until first DB connection attempt
}

// FIXED: add @Validated and constraints
@ConfigurationProperties(prefix = "app")
@Validated
public class AppProperties {
    @Min(1) @Max(100)
    private int poolSize = 10;
    // poolSize=-5 -> BindValidationException at startup with clear message
}
```

### Pitfall 3: @PropertySource with YAML (Unsupported)

```java
// BROKEN: @PropertySource does not support YAML files
@Configuration
@PropertySource("classpath:custom.yml")  // YAML not supported here!
public class AppConfig { }
// No error thrown — file silently ignored

// FIXED: use .properties file with @PropertySource
@PropertySource("classpath:custom.properties")

// OR: use spring.config.import in application.properties
// spring.config.import=classpath:custom.yml  (Boot 2.4+ supports YAML here)
```

---

## 11. Technologies & Tools

| Component | Role |
|-----------|------|
| `ConfigDataEnvironmentPostProcessor` | Loads `application.properties` / YAML |
| `@ConfigurationProperties` | Type-safe property binding |
| `@ConstructorBinding` | Immutable `@ConfigurationProperties` (Spring Boot 2.x) |
| `@Validated` | JSR-303 validation on `@ConfigurationProperties` |
| `spring-boot-configuration-processor` | Generates metadata for IDE autocompletion |
| `EnvironmentPostProcessor` | Programmatic property source addition |
| `spring.config.import` | Import additional config files or Config Server |
| `RelaxedPropertyResolver` | Handles relaxed binding lookups |
| `/actuator/env` | Shows resolved property values at runtime |

---

## 12. Interview Questions with Answers

**What is the property source priority order in Spring Boot?**
From highest to lowest: command-line arguments, SPRING_APPLICATION_JSON env var, servlet init params, JNDI, Java system properties (-D flags), OS environment variables, profile-specific config files outside JAR, profile-specific files inside JAR, base application.properties outside JAR, base application.properties inside JAR, @PropertySource annotations, default properties. Command-line args win, meaning `--spring.datasource.password=...` overrides everything else. OS environment variables override packaged config files, which is the standard pattern for Kubernetes secrets.

**What is relaxed binding and how does it help with Kubernetes secrets?**
Relaxed binding maps multiple property name formats to the same Java field. `maxConnections`, `max-connections`, `max_connections`, and `MAX_CONNECTIONS` all bind to a Java field named `maxConnections`. This is critical for Kubernetes: Kubernetes Secret env vars must use uppercase and underscores (`SPRING_DATASOURCE_PASSWORD`), but Java code uses camelCase (`password`) under prefix `spring.datasource`. Relaxed binding handles the translation automatically. `@Value` does NOT support relaxed binding — only `@ConfigurationProperties`.

**What is the difference between profile-specific files inside and outside the JAR?**
Profile-specific files outside the JAR (filesystem, current directory) have higher priority than those inside the JAR. This allows operations teams to place an `application-production.properties` file alongside the JAR to override packaged defaults. Spring Boot checks `./config/`, `./`, then `classpath:/config/`, then `classpath:/` in order. The convention enables the same JAR to be configured differently per environment without rebuilding.

**How does @ConstructorBinding work and why is it preferred for immutable classes?**
`@ConstructorBinding` (Spring Boot 2.x) or automatic detection of single constructor (Spring Boot 3.x) tells Spring to bind properties via constructor parameters instead of setters. This enables `final` fields — the bound object is immutable after creation. Immutable configuration is safer: no code can accidentally modify configuration post-initialization, and the object can be safely shared across threads. In Spring Boot 3.x, if `@ConfigurationProperties` class has only one constructor, `@ConstructorBinding` is implicit.

**What is spring.config.import and how does it replace bootstrap.yml?**
`spring.config.import` (Spring Boot 2.4+) is a property that specifies additional configuration files or sources to import. It supports file paths, classpath resources, and protocol handlers like `configserver:` (Spring Cloud Config) and `vault:` (Vault). Before 2.4, Spring Cloud Config required a separate `bootstrap.yml` file loaded by a bootstrap ApplicationContext. `spring.config.import=configserver:` replaces this, importing Config Server properties into the main context with proper priority ordering. It also supports `optional:` prefix to silently skip missing imports.

**How does @ConfigurationProperties validation work?**
Add `@Validated` to the `@ConfigurationProperties` class and JSR-303 annotations (`@NotNull`, `@Min`, `@Max`, `@NotEmpty`, `@Pattern`) to fields. Spring Boot runs Bean Validation on bound properties during context startup. A validation failure throws `BindValidationException` with the full list of constraint violations, preventing the application from starting with invalid configuration. This is the correct "fail fast" behavior for misconfiguration. Without `@Validated`, invalid values like `poolSize=-5` or `url=null` are silently accepted and cause failures much later.

**Can YAML files be used with @PropertySource?**
No. `@PropertySource` only supports `.properties` files by default. It uses `PropertiesPropertySourceLoader` which parses key=value format. YAML requires `YamlPropertySourceLoader`. You can register a custom `PropertySourceFactory` on `@PropertySource` to handle YAML: `@PropertySource(value="classpath:extra.yml", factory=YamlPropertySourceFactory.class)`. Alternatively, use `spring.config.import=classpath:extra.yml` (Boot 2.4+), which uses the full config loading pipeline supporting YAML natively.

**What is the @ConfigurationProperties metadata processor and why should you use it?**
Add `spring-boot-configuration-processor` to build dependencies (optional compile scope). It runs at compile time and generates `META-INF/spring-configuration-metadata.json` describing all `@ConfigurationProperties` classes. IDE plugins (IntelliJ, Eclipse) read this metadata to provide: property name autocompletion in `application.properties`, documentation tooltips showing field descriptions, type information, and default values. Without this, application.properties is edited without any IDE assistance. For library/starter authors, this metadata is what makes their configuration discoverable and documented.

**How do multi-document YAML files work with profiles?**
A YAML file can contain multiple documents separated by `---`. Each document can be conditionally activated using `spring.config.activate.on-profile`. When a profile is active, only the matching documents (plus documents with no activation condition) are applied. Documents are applied in order — later documents override earlier ones for the same key. This allows a single `application.yml` to contain all environment configurations, which some teams prefer over multiple files. Multi-document `.properties` files are not supported; only YAML supports this.

**What is EnvironmentPostProcessor and when would you use it?**
`EnvironmentPostProcessor` is an SPI that runs before the ApplicationContext is created, allowing programmatic manipulation of the `ConfigurableEnvironment`. Register via `spring.factories` (key: `org.springframework.boot.env.EnvironmentPostProcessor`) or `META-INF/spring/...` (Boot 3.x). Use cases: loading secrets from Vault or AWS Secrets Manager before other config is bound (ensuring secrets are available at highest priority), decrypting encrypted property values, or adding environment-specific property sources based on system detection.

**What does /actuator/env expose and what are the security risks?**
`/actuator/env` exposes the full `Environment` including all property source values, showing which property source provides each property. The response masks values matching patterns like `*password*`, `*secret*`, `*key*` with `******`. However, this masking is pattern-based and not exhaustive — less obvious secret names may be exposed. In production, always secure actuator endpoints with Spring Security: `management.endpoints.web.exposure.include=health,info` (whitelist), and require authentication for the rest via `SecurityFilterChain`. Never expose `/actuator/env` without authentication.

---

## 13. Best Practices

1. **Use `@ConfigurationProperties` for all groups of related properties** — type safety, validation, IDE support.
2. **Use OS environment variables for secrets in production** — never commit passwords to `application.properties`.
3. **Validate all required config at startup** with `@Validated` + `@NotNull` — fail fast, not on first request.
4. **Add `spring-boot-configuration-processor`** to pom.xml for IDE autocompletion.
5. **Use profile-specific files** (`application-production.properties`) for environment-specific values.
6. **Understand property source priority** — env vars beat packaged properties (critical for understanding production behavior).
7. **Prefer `spring.config.import`** over bootstrap context for external config sources (Spring Boot 2.4+).
8. **Use `Duration` and `DataSize` types** in `@ConfigurationProperties` — `connection-timeout=30s` is more readable than `connection-timeout-ms=30000`.
9. **Restrict actuator endpoints** in production — never expose `/actuator/env`, `/actuator/heapdump`, or `/actuator/shutdown` without authentication.
10. **Test configuration binding** with `ApplicationContextRunner` or `@SpringBootTest` slice tests to catch binding errors early.

---

## 14. Case Study

### Problem: Production Deployment Picks Up Wrong Database URL

**Symptom:** Application starts successfully in production but connects to staging database. Data from staging users is leaking into production operations.

**Investigation:**

```bash
# Check what URL is actually resolved
curl -u admin:secret http://prod-service:8080/actuator/env | jq '."spring.datasource.url"'

# Output:
# {
#   "property": {
#     "source": "applicationConfig: [classpath:/application-staging.properties]",
#     "value": "jdbc:postgresql://staging-db:5432/mydb"
#   }
# }
```

The property source is `application-staging.properties`, not production config.

**Root cause:** The Docker image was built with `SPRING_PROFILES_ACTIVE=staging` hardcoded in the `Dockerfile`:

```dockerfile
# BROKEN: hardcoded profile in Docker image
ENV SPRING_PROFILES_ACTIVE=staging
```

When deployed to production, the Kubernetes deployment YAML also set:

```yaml
# kubernetes deployment.yaml
env:
  - name: SPRING_PROFILES_ACTIVE
    value: production
```

Kubernetes env var has priority 7 and should override... but `ENV` in Dockerfile sets environment variables that are baked into the container image as OS-level vars. The Kubernetes `env:` spec also sets OS-level vars, but container OS vars from Docker `ENV` are overridden by Kubernetes `env:` which sets vars AFTER the container is created. Actually, Kubernetes `env:` overrides Docker `ENV` at runtime. The real bug was elsewhere: the build pipeline was using the wrong Docker image that never received the Kubernetes override.

**Fix:**

```dockerfile
# FIXED: no hardcoded profile in Dockerfile
# Profile is set entirely by the deployment environment

# application.properties (default, safe for local dev)
spring.profiles.active=development

# Kubernetes deployment.yaml (production)
env:
  - name: SPRING_PROFILES_ACTIVE
    value: production
  - name: SPRING_DATASOURCE_URL
    valueFrom:
      secretKeyRef:
        name: db-credentials
        key: url
```

**Lesson:** Never hardcode profile names in Docker images. Secrets and environment-specific config belong in Kubernetes Secrets and ConfigMaps, not in the JAR or Docker image. The `/actuator/env` endpoint (secured, accessible to ops) is invaluable for diagnosing exactly which property source is winning for each property.
