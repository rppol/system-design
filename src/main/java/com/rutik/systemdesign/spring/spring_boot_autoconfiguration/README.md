# Spring Boot Auto-Configuration

## 1. Concept Overview

Spring Boot auto-configuration is the mechanism that automatically configures Spring application components based on the JARs present on the classpath, beans already defined, and property values. When you add `spring-boot-starter-data-jpa` to your `pom.xml`, Spring Boot automatically configures `DataSource`, `EntityManagerFactory`, `TransactionManager`, and Spring Data repositories — without a single line of explicit configuration.

Auto-configuration classes are loaded by `@EnableAutoConfiguration` via the `AutoConfigurationImportSelector`, which reads a list of configuration class names from `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (Spring Boot 2.7+).

---

## 2. Intuition

Auto-configuration is like a smart hotel room that detects your preferences and sets itself up. If it detects you brought a laptop (Hibernate JAR), it sets up a desk and ethernet (DataSource + EntityManagerFactory). If you brought workout clothes (Actuator JAR), it sets up a gym access card (health endpoints). You can override anything — bring your own coffee maker (define your own `DataSource` bean) and the hotel's coffee machine stays off.

**One-line analogy:** Auto-configuration is an opinionated default setup that activates only when needed and backs off when you provide your own configuration.

**Key insight:** Auto-configuration is not magic — it is just `@Configuration` classes with layered `@Conditional` annotations. The `--debug` flag or `/actuator/conditions` endpoint shows exactly which configurations were applied and why others were skipped.

---

## 3. Core Principles

1. **Convention over configuration:** Sensible defaults are applied automatically; you only configure what differs from convention.
2. **Condition-based activation:** Every auto-configuration class has `@Conditional` gates that prevent registration when inappropriate.
3. **User config takes priority:** `@ConditionalOnMissingBean` ensures auto-configuration backs off when user provides their own bean.
4. **Order matters:** `@AutoConfigureAfter` and `@AutoConfigureBefore` control the order auto-configuration classes are processed.
5. **Discoverable via SPI:** Auto-configuration classes are registered in a standard file, not via component scanning — this is intentional to keep starter packages isolated from the application's scan.

---

## 4. Types / Architectures / Strategies

### Auto-Configuration Loading (Boot 2.7+ vs Boot 2.x)

| Spring Boot Version | Registration File | Location |
|--------------------|------------------|---------|
| < 2.7 | `spring.factories` | `META-INF/spring.factories` |
| 2.7+ (preferred) | `AutoConfiguration.imports` | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |
| 3.0+ | `AutoConfiguration.imports` only | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |

### Condition Evaluation Report Categories

| Category | Meaning |
|----------|---------|
| Positive matches | Conditions passed; bean/configuration registered |
| Negative matches | One or more conditions failed; not registered |
| Exclusions | Explicitly excluded via `exclude` attribute |
| Unconditional classes | No conditions; always registered |

---

## 5. Architecture Diagrams

```
Auto-Configuration Loading Flow
================================

  @SpringBootApplication
          |
          v
  @EnableAutoConfiguration
          |
          v
  AutoConfigurationImportSelector.selectImports()
          |
          v
  Reads: META-INF/spring/org.springframework.boot.autoconfigure
         .AutoConfiguration.imports
         (list of ~140 class names in spring-boot-autoconfigure)
          |
          v
  For each class, evaluate @Conditional annotations:
  +------------------------------------------+
  | DataSourceAutoConfiguration               |
  |   @ConditionalOnClass(DataSource.class)  |
  |   @ConditionalOnMissingBean(DataSource)  | --> PASS? Register bean
  +------------------------------------------+     FAIL? Skip
          |
          v
  Registered configurations processed by container
  (alongside user's @Configuration classes)
```

```
Condition Layering Example: DataSourceAutoConfiguration
========================================================

  JAR on classpath?          is HikariCP present?
  +--------------------------+
  | @ConditionalOnClass      |  --> HikariDataSource.class in classpath? YES/NO
  | (DataSource.class,       |
  |  EmbeddedDatabase.class) |
  +--------------------------+
              |
              YES
              v
  User defined DataSource?
  +----------------------------+
  | @ConditionalOnMissingBean  |  --> DataSource bean in context? YES(skip) / NO(proceed)
  | (DataSource.class)         |
  +----------------------------+
              |
              NO (user has no DataSource)
              v
  Property spring.datasource.url defined?
  +------------------------------+
  | @ConditionalOnProperty       |  (for JDBC auto-config; not shown for simplicity)
  +------------------------------+
              |
              YES
              v
  Register HikariDataSource bean with config from spring.datasource.*
```

---

## 6. How It Works — Detailed Mechanics

### AutoConfigurationImportSelector Internals

```java
// Simplified logic of AutoConfigurationImportSelector
public class AutoConfigurationImportSelector implements DeferredImportSelector {

    @Override
    public String[] selectImports(AnnotationMetadata annotationMetadata) {
        // 1. Load all candidate class names from imports file / spring.factories
        List<String> candidates = getCandidateConfigurations(annotationMetadata);

        // 2. Remove duplicates
        candidates = removeDuplicates(candidates);

        // 3. Apply exclusions from @SpringBootApplication(exclude=...) and
        //    spring.autoconfigure.exclude property
        Set<String> exclusions = getExclusions(annotationMetadata);
        candidates.removeAll(exclusions);

        // 4. Filter by conditions (the actual @Conditional evaluation happens later)
        //    Here just returns all non-excluded candidates
        return candidates.toArray(new String[0]);
        // Actual @Conditional evaluation happens in BeanFactory processing
    }
}
```

### Writing a Custom Auto-Configuration Class

```java
// 1. The auto-configuration class
@AutoConfiguration  // Spring Boot 3.x annotation (@Configuration in 2.x)
@ConditionalOnClass(ObservabilityClient.class)  // only if client JAR present
@ConditionalOnMissingBean(ObservabilityReporter.class)  // don't override user's bean
@AutoConfigureAfter(MetricsAutoConfiguration.class)  // needs MeterRegistry first
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnProperty(
        prefix = "observability",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true  // enabled by default; set false to disable
    )
    public ObservabilityReporter reporter(ObservabilityProperties props,
                                           MeterRegistry registry) {
        return new ObservabilityReporter(props.getEndpoint(), registry);
    }

    @Bean
    @ConditionalOnMissingBean
    public ObservabilityHealthIndicator healthIndicator(ObservabilityReporter reporter) {
        return new ObservabilityHealthIndicator(reporter);
    }
}

// 2. Properties class
@ConfigurationProperties(prefix = "observability")
public class ObservabilityProperties {
    private boolean enabled = true;
    private String endpoint = "localhost:4317";
    private Duration timeout = Duration.ofSeconds(5);
    // getters, setters
}

// 3. Registration file (Spring Boot 3.x)
// src/main/resources/META-INF/spring/
//   org.springframework.boot.autoconfigure.AutoConfiguration.imports
// Content:
// com.company.observability.ObservabilityAutoConfiguration

// For Spring Boot 2.x: src/main/resources/META-INF/spring.factories
// org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
//   com.company.observability.ObservabilityAutoConfiguration
```

### Debugging Auto-Configuration

```bash
# Method 1: --debug flag on startup
java -jar app.jar --debug

# Outputs ConditionEvaluationReport like:
# Positive matches:
#    DataSourceAutoConfiguration matched
#       - @ConditionalOnClass found required classes 'javax.sql.DataSource'...
#       - @ConditionalOnMissingBean (types: DataSource) did not find any beans
#
# Negative matches:
#    MongoAutoConfiguration:
#       Did not match:
#          - @ConditionalOnClass did not find required class 'com.mongodb.MongoClient'

# Method 2: Actuator conditions endpoint
# GET /actuator/conditions
# Returns JSON with same information

# Method 3: In tests
@SpringBootTest
class MyTest {
    @Autowired
    private ApplicationContext ctx;

    @Test
    void verifyDataSourceAutoConfigured() {
        assertThat(ctx.getBean(DataSource.class)).isNotNull();
        assertThat(ctx.getBean(DataSource.class)).isInstanceOf(HikariDataSource.class);
    }
}
```

### Excluding Auto-Configuration

```java
// Method 1: @SpringBootApplication exclude attribute (compile-time safe)
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class MyApplication { }

// Method 2: property (useful when class may not be on classpath)
// application.properties:
// spring.autoconfigure.exclude=\
//   org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,\
//   org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
```

### Boot 2.7 → 3.0 Migration

```
Spring Boot 2.x:
META-INF/spring.factories:
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
  com.example.MyAutoConfiguration

Spring Boot 3.x (no more spring.factories for auto-config):
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:
com.example.MyAutoConfiguration

Also:
- javax.* -> jakarta.*
- WebSecurityConfigurerAdapter removed -> SecurityFilterChain @Bean
- @Configuration on auto-config classes -> @AutoConfiguration (3.x)
```

---

## 7. Real-World Examples

**HikariCP auto-configuration:** Adding `spring-boot-starter-jdbc` adds HikariCP to the classpath. `DataSourceAutoConfiguration` detects `DataSource.class` on classpath, no user-defined `DataSource` bean, and `spring.datasource.url` property defined, then auto-creates a `HikariDataSource` with pool size 10 and all HikariCP defaults. Zero `@Bean` methods needed.

**Redis auto-configuration:** `spring-boot-starter-data-redis` triggers `RedisAutoConfiguration` (creates `RedisTemplate` and `StringRedisTemplate`) and `RedisRepositoriesAutoConfiguration` (creates Redis repository proxies). Override by providing your own `RedisConnectionFactory` bean — auto-configuration backs off.

**Custom starter in a platform team:** A platform team creates `platform-spring-boot-starter` that auto-configures distributed tracing, security policies, and audit logging for every service. Teams add one dependency to `pom.xml` and get compliant observability automatically. Individual services can override specific beans with their own definitions.

---

## 8. Tradeoffs

| Aspect | Auto-Configuration | Manual Configuration |
|--------|-------------------|---------------------|
| Setup time | Near zero | Hours for complex stacks |
| Understanding | Opaque until debugged | Explicit and readable |
| Upgrade safety | May change behavior on version bump | Full control |
| Customization | Via properties or bean override | Anything |
| Spring Boot dependency | Tightly coupled | Can use plain Spring |
| Debuggability | Need --debug or /conditions | Read the code |

---

## 9. When to Use / When NOT to Use

**Use auto-configuration when:**
- Building Spring Boot applications (the standard approach)
- Writing Spring Boot starters for team-wide infrastructure
- Rapid prototyping

**Override auto-configuration when:**
- Default configuration does not match your requirements (define your own bean)
- Auto-configuration conflicts with another library (exclude it)

**Do NOT rely on auto-configuration order** for bean availability — use explicit `@DependsOn` or `@AutoConfigureAfter` if your configuration depends on an auto-configured bean being present.

---

## 10. Common Pitfalls

### Pitfall 1: Defining a Bean That Conflicts with Auto-Configuration

```java
// CONFUSION: defining a DataSource AND expecting auto-config Redis to work
@Configuration
public class AppConfig {
    @Bean
    public DataSource dataSource() { ... }  // overrides auto-config DataSource (good)
}

// Problem: user defines a partial config that doesn't satisfy all conditions,
// but auto-config skips because @ConditionalOnMissingBean sees the partial config.
// Symptom: NullPointerException on beans that depend on the "missing" auto-configured bean.

// RULE: if you define a bean, take full responsibility for it and related dependencies.
// Use @ConditionalOnMissingBean in your own auto-configurations.
```

### Pitfall 2: spring.factories Not Found in JAR

```java
// BROKEN: file not at correct path in JAR
// Wrong: src/main/resources/META-INF/spring-factories (typo — missing dot)
// Wrong: src/main/resources/spring.factories (wrong directory)
// Correct: src/main/resources/META-INF/spring.factories (Boot 2.x)
// Correct: src/main/resources/META-INF/spring/
//          org.springframework.boot.autoconfigure.AutoConfiguration.imports (Boot 3.x)

// Verify in JAR:
// jar tf my-starter.jar | grep -i META-INF
// Should show: META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

### Pitfall 3: @ComponentScan in Starter Package

```java
// BROKEN: starter's @Configuration scans its own packages into user's context
@Configuration
@ComponentScan("com.company.starter")  // BUG: scans starter's internal beans
public class MyStarterAutoConfiguration { }

// This exposes internal beans that should be implementation details.
// User's context gets polluted with starter's private beans.

// FIXED: never use @ComponentScan in auto-configuration classes
// Define all beans explicitly with @Bean methods in the auto-config class.
@AutoConfiguration
public class MyStarterAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public MyFeature myFeature(MyProperties props) {
        return new MyFeatureImpl(props);  // explicit registration, no scanning
    }
}
```

---

## 11. Technologies & Tools

| Component | Role |
|-----------|------|
| `AutoConfigurationImportSelector` | Loads and filters auto-configuration classes |
| `@AutoConfiguration` | Spring Boot 3.x annotation for auto-config classes |
| `@ConditionalOnClass` | Condition: specific class on classpath |
| `@ConditionalOnMissingBean` | Condition: no bean of type already defined |
| `@ConditionalOnProperty` | Condition: property has specific value |
| `@AutoConfigureAfter/Before` | Ordering between auto-configurations |
| `spring-boot-autoconfigure-processor` | Generates metadata for IDE autocompletion |
| `ConditionEvaluationReport` | Printable report of all condition outcomes |
| `ApplicationContextRunner` | Test utility for testing auto-configuration |

---

## 12. Interview Questions with Answers

**How does Spring Boot auto-configuration work?**
`@EnableAutoConfiguration` (included via `@SpringBootApplication`) imports `AutoConfigurationImportSelector`, which reads class names from `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (Boot 3.x) or `spring.factories`. Each listed class is a `@Configuration` class with `@Conditional` annotations. The container evaluates each condition — if all pass, the beans are registered; if any fail, the entire configuration class is skipped. Users override by defining their own beans (caught by `@ConditionalOnMissingBean`) or excluding via `spring.autoconfigure.exclude`.

**What is the difference between spring.factories and AutoConfiguration.imports?**
Both register auto-configuration classes for discovery. `spring.factories` (Boot 2.x) is a generic key-value properties file supporting multiple Spring extension points. `AutoConfiguration.imports` (Boot 2.7+, required in Boot 3.x) is a dedicated file for auto-configuration class names only. Boot 3.x removed support for auto-configuration registration via `spring.factories` to improve startup performance and clarity. The `spring.factories` file still works for other extension points (failure analyzers, environment post-processors).

**Why does @ConditionalOnMissingBean work correctly even though Spring beans are initialized in dependency order?**
`@ConditionalOnMissingBean` is evaluated during the `BeanFactoryPostProcessor` phase, after all `BeanDefinition`s are loaded but before any beans are instantiated. It checks the `BeanDefinitionRegistry` for existing definitions (not instances). This means: if you define a `@Bean DataSource` in your `@Configuration` class, the `BeanDefinition` for it exists in the registry when `DataSourceAutoConfiguration` evaluates `@ConditionalOnMissingBean(DataSource.class)` — the condition fails and auto-configuration backs off correctly.

**How would you write a custom Spring Boot starter?**
Four steps: (1) Create an autoconfigure module with `@AutoConfiguration` class containing `@Conditional`-guarded `@Bean` methods and `@ConfigurationProperties` for user-overridable settings. (2) Register the class in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. (3) Do NOT use `@ComponentScan` — register all beans explicitly. (4) Create a starter POM that depends on your autoconfigure module and any required third-party JARs. Users add the starter dependency and get auto-configured behavior immediately.

**How do you debug which auto-configurations are applied and which are skipped?**
Start the application with `--debug` flag: `java -jar app.jar --debug`. This prints the `ConditionEvaluationReport` to the console showing positive matches (applied), negative matches (skipped with reasons), and exclusions. Alternatively, expose the `/actuator/conditions` endpoint (require `spring-boot-actuator` on classpath and `management.endpoints.web.exposure.include=conditions`). For unit testing auto-configuration, use `ApplicationContextRunner` from `spring-boot-test` to test specific configurations in isolation.

**What is @AutoConfigureAfter and why does order matter?**
`@AutoConfigureAfter(OtherAutoConfig.class)` ensures `OtherAutoConfig` is processed before the current class. This matters when your auto-configuration needs beans from another auto-configuration to be available. For example, `JdbcTemplateAutoConfiguration` is `@AutoConfigureAfter(DataSourceAutoConfiguration.class)` because it needs the `DataSource` bean. Without ordering, `@ConditionalOnBean(DataSource.class)` in `JdbcTemplateAutoConfiguration` might evaluate before `DataSourceAutoConfiguration` runs, failing the condition incorrectly.

**What happens when you exclude an auto-configuration class?**
The class name is added to an exclusion set checked by `AutoConfigurationImportSelector`. The class is removed from the import list before any condition evaluation. This is a hard exclusion — even if all conditions would have passed, the class is never imported and its beans are never registered. This is useful when: you want to completely replace auto-configuration with your own, the auto-configuration conflicts with another library, or you want to disable a feature entirely (like Spring Security auto-config in tests).

**What is @ConditionalOnProperty and when is matchIfMissing important?**
`@ConditionalOnProperty(name="feature.enabled", havingValue="true", matchIfMissing=false)` registers the bean only when the property is set to "true" — if the property is missing, the bean is NOT registered (the default with `matchIfMissing=false`). Setting `matchIfMissing=true` inverts this: the bean is registered when the property is missing (treat "absent" as "true"). Use `matchIfMissing=true` for features that should be on by default and disabled by setting the property to "false".

**How does spring-boot-autoconfigure-processor improve performance?**
The processor runs at compile time and generates `META-INF/spring-autoconfigure-metadata.properties`. This file lists the conditions for each auto-configuration class (class names for `@ConditionalOnClass`, property names for `@ConditionalOnProperty`). Spring Boot reads this metadata at startup and filters out auto-configuration classes whose conditions obviously fail (e.g., required class not on classpath) WITHOUT loading and processing the configuration class. This makes startup faster because fewer classes are loaded and parsed.

**What is the ApplicationContextRunner and how is it used for testing?**
`ApplicationContextRunner` is a test utility from `spring-boot-test` that creates a minimal `ApplicationContext` for testing auto-configuration. It does not start a full Spring Boot application — just evaluates specific configurations in isolation. Example: `new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(MyAutoConfig.class)).withPropertyValues("my.property=value").run(context -> { assertThat(context).hasSingleBean(MyBean.class); })`. This is the recommended way to test auto-configuration without requiring `@SpringBootTest`.

---

## 13. Best Practices

1. **Use `@ConditionalOnMissingBean` generously** in auto-configuration to give users full override capability.
2. **Never use `@ComponentScan` in auto-configuration** — define beans explicitly with `@Bean`.
3. **Use `@AutoConfigureAfter`/`@AutoConfigureBefore`** when your config depends on another auto-config's beans.
4. **Add spring-boot-autoconfigure-processor** to your starter's compile dependencies for metadata generation.
5. **Use `@ConfigurationProperties` with metadata** (`spring-boot-configuration-processor`) for IDE autocompletion and validation.
6. **Test auto-configuration with `ApplicationContextRunner`** — faster than full `@SpringBootTest`.
7. **Provide sensible defaults** with `matchIfMissing=true` so features work out of the box.
8. **Document which beans are auto-configured** and how to override them in your starter's README.
9. **Keep auto-configuration classes small and focused** — one per concern (data source, metrics, security).
10. **Use the `--debug` flag** to verify your auto-configuration is applying correctly during development.

---

## 14. Case Study

### Scenario: Company-Wide Distributed Tracing Starter

A platform team ships `company-tracing-spring-boot-starter` (Spring Boot 3.2 / Java 17) so all 80+ internal services get distributed tracing by adding one Maven dependency. Requirements:

- Auto-configure a `Tracer` bean only when the tracing library is on the classpath
- Back off entirely if a service defines its own `Tracer` (advanced teams customize)
- Bind configuration via `@EnableConfigurationProperties(TracingProperties.class)`
- Register via the Boot 3 mechanism (`AutoConfiguration.imports`), not the removed `spring.factories`
- Adoption goal: zero code in consuming services; opt-out via a property

Two recurring failures drove the design hardening: double bean registration when the autoconfig class was accidentally component-scanned, and `@ConditionalOnMissingBean` not backing off because of bean-definition ordering.

### Architecture Overview

```
   consuming-service (depends on starter jar)
        |
        v
  Spring Boot startup
        |
        v
  AutoConfigurationImportSelector reads:
   META-INF/spring/
     org.springframework.boot.autoconfigure.AutoConfiguration.imports
        |
        v
  +------------------------------------------------------+
  | TracingAutoConfiguration                              |
  |   @ConditionalOnClass(Tracer.class)  --- backs off    |
  |       if tracing lib absent                           |
  |   @ConditionalOnMissingBean(Tracer.class) --- backs   |
  |       off if user defines their own Tracer            |
  |   @EnableConfigurationProperties(TracingProperties)   |
  +-----------------------+------------------------------+
                          | (only if both conditions pass)
                          v
                   Tracer bean (default)
```

### Implementation

The autoconfiguration class lives in a package that is NOT component-scanned by consumers, and is registered via the imports file.

```java
@AutoConfiguration
@ConditionalOnClass(Tracer.class)                      // backs off if lib missing
@EnableConfigurationProperties(TracingProperties.class)
public class TracingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean                          // user's own Tracer wins
    @ConditionalOnProperty(prefix = "company.tracing",
                           name = "enabled", havingValue = "true", matchIfMissing = true)
    public Tracer tracer(TracingProperties props) {
        return Tracer.builder()
                     .serviceName(props.getServiceName())
                     .samplingRate(props.getSamplingRate())
                     .endpoint(props.getCollectorEndpoint())
                     .build();
    }
}

@ConfigurationProperties(prefix = "company.tracing")
public class TracingProperties {
    private String serviceName = "unknown";
    private double samplingRate = 0.1;
    private String collectorEndpoint = "http://otel-collector:4317";
    // getters/setters
}
```

Boot 3 registration replaces the old `spring.factories` key with a plain text imports file.

```
# Boot 2.x (REMOVED in 3.x):
#   src/main/resources/META-INF/spring.factories
#   org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
#     com.company.tracing.TracingAutoConfiguration

# Boot 3.x (current):
#   src/main/resources/META-INF/spring/
#     org.springframework.boot.autoconfigure.AutoConfiguration.imports
#   one fully-qualified class name per line:
com.company.tracing.autoconfigure.TracingAutoConfiguration
```

Debugging which conditions matched uses the `--debug` flag's condition evaluation report.

```bash
java -jar service.jar --debug 2>&1 | grep -A4 "TracingAutoConfiguration"
# TracingAutoConfiguration matched:
#   - @ConditionalOnClass found required class 'com.company.tracing.Tracer'
#   - @ConditionalOnMissingBean (Tracer) did not find any beans
```

### Metrics

| Metric | Before | After |
|--------|--------|-------|
| Services with tracing | 12 (manual) | 80+ (automatic) |
| Code per service to enable | ~40 lines | 0 (just the dependency) |
| Double-registration startup failures | recurring | 0 |
| Time to onboard a new service | ~half day | minutes |

### Common Pitfalls

**Pitfall 1 — autoconfig class in a component-scanned package causes double registration.**

```java
// BROKEN: placed under com.company.app... so consumers' @ComponentScan picks it up,
// AND it's in AutoConfiguration.imports -> bean defined twice / ordering chaos
package com.company.app.config;
@AutoConfiguration
public class TracingAutoConfiguration { ... }
```

```java
// FIX: keep autoconfig in a dedicated package outside any consumer scan base package
package com.company.tracing.autoconfigure;   // only reached via AutoConfiguration.imports
@AutoConfiguration
public class TracingAutoConfiguration { ... }
```

**Pitfall 2 — `@ConditionalOnMissingBean` does not back off due to definition ordering.**

```java
// BROKEN: user's @Bean is in a @Configuration processed AFTER autoconfig, so at the time
// the condition is evaluated, the user bean isn't registered yet -> both get created
@AutoConfiguration   // no ordering hint
public class TracingAutoConfiguration {
    @Bean @ConditionalOnMissingBean public Tracer tracer() { ... }
}
```

```java
// FIX: ensure user config is processed first via @AutoConfiguration(after=...) / ordering;
// user @Bean definitions (regular @Configuration) are registered before autoconfiguration,
// and stating intent with ordering annotations makes the back-off deterministic
@AutoConfiguration(after = UserTracingConfig.class)
public class TracingAutoConfiguration {
    @Bean @ConditionalOnMissingBean public Tracer tracer() { ... }
}
```

**Pitfall 3 — using `@Configuration` instead of `@AutoConfiguration` (Boot 3).**

```java
// BROKEN: @Configuration listed in imports is treated as a user config, losing
// autoconfiguration ordering and @AutoConfigureBefore/After semantics
@Configuration
public class TracingAutoConfiguration { ... }
```

```java
// FIX: Boot 3 autoconfiguration classes must use @AutoConfiguration
@AutoConfiguration
public class TracingAutoConfiguration { ... }
```

### Interview Discussion Points

**How did autoconfiguration registration change from Boot 2 to Boot 3?** Boot 2 listed autoconfigurations under the `EnableAutoConfiguration` key in `META-INF/spring.factories`. Boot 3 removed that key and uses a dedicated file, `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, with one fully-qualified class name per line. Classes must also be annotated `@AutoConfiguration` rather than `@Configuration`.

**What is the role of `@ConditionalOnClass` and `@ConditionalOnMissingBean` in a starter?** `@ConditionalOnClass` makes the autoconfiguration apply only when a required type is on the classpath, so the starter degrades gracefully if the underlying library is absent. `@ConditionalOnMissingBean` makes the default bean back off when the consumer has defined their own, implementing the "sensible defaults, fully overridable" contract that defines good Boot starters.

**Why must autoconfiguration classes live outside the consumer's component-scan path?** If the class sits under the application's base package, `@ComponentScan` registers it as a normal `@Configuration` in addition to the `AutoConfiguration.imports` mechanism, producing duplicate bean definitions and breaking the conditional ordering. Keeping it in a separate package ensures it is only loaded through the autoconfiguration import selector, after user configuration.

**Why might `@ConditionalOnMissingBean` fail to back off, and how do you fix it?** The condition is evaluated when the autoconfiguration is processed; if the user's bean definition is registered later than expected, the condition sees no existing bean and creates the default too. Because autoconfiguration runs after user `@Configuration`, this is usually fine, but explicit ordering with `@AutoConfiguration(after=...)`/`@AutoConfigureBefore`/`@AutoConfigureAfter` makes the back-off deterministic across configurations.

**How do you debug whether an autoconfiguration was applied?** Run with `--debug` (or set `debug=true`) to print the condition evaluation report, which lists positive matches (with the satisfied conditions) and negative matches (with the reason a class was skipped). It immediately tells you whether the class was even discovered (registration file present), and which `@ConditionalOn*` gate failed.

**How do `@EnableConfigurationProperties` and `@ConfigurationProperties` cooperate in a starter?** `@ConfigurationProperties(prefix="company.tracing")` defines a typed binding target, and `@EnableConfigurationProperties(TracingProperties.class)` registers and binds it within the autoconfiguration without requiring the consumer to scan or annotate anything. Consumers then tune behavior purely through `application.yml` keys under that prefix, with relaxed binding and validation support.
