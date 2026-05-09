# Multi-Tenant SaaS REST API with Schema-per-Tenant

## Problem Statement

Design a multi-tenant SaaS REST API where each tenant is completely isolated at the database schema level. The system must:

- Support 500+ tenants, each with their own PostgreSQL schema
- Identify tenant from subdomain (tenant1.api.com) or X-Tenant-ID header
- Route every database connection to the correct tenant schema
- Authenticate users against per-tenant user tables
- Apply Flyway migrations to each tenant schema on onboarding
- Handle 10,000 req/sec at peak with sub-100ms p99 latency
- Zero cross-tenant data leakage — a misconfigured query must never read another tenant's data

Constraints: PostgreSQL as the database engine, Spring Boot 3.x, Hibernate 6.x, single application deployment (no per-tenant pods).

---

## Architecture Overview

```
                        Internet
                           |
              +------------+------------+
              |     Load Balancer       |
              |  (SNI / Host header)    |
              +------------+------------+
                           |
              +------------+------------+
              |   Spring Boot App       |
              |                         |
              |  [TenantResolutionFilter]|  <- reads subdomain / header
              |         |               |
              |  [TenantContext]        |  <- ThreadLocal store
              |         |               |
              |  [HandlerInterceptor]   |  <- validates tenant exists
              |         |               |
              |  [Spring Security]      |  <- per-tenant UserDetailsService
              |         |               |
              |  [Service Layer]        |
              |         |               |
              |  [Hibernate ORM]        |
              |         |               |
              |  [MultiTenantConnProv]  |  <- switches schema
              +------------+------------+
                           |
              +------------+------------+
              |        PostgreSQL        |
              |                         |
              |  schema: public         |  <- tenant registry, system tables
              |  schema: tenant_acme    |  <- ACME Corp data
              |  schema: tenant_globex  |  <- Globex Corp data
              |  schema: tenant_initech |  <- Initech data
              +-------------------------+
```

---

## Key Design Decisions

### 1. Schema-per-Tenant vs Row-Level Security vs Separate Databases

Schema-per-tenant was chosen because it provides strong isolation without the operational overhead of hundreds of separate database instances. PostgreSQL schemas share a connection pool but have completely separate table namespaces. Row-level security (RLS) was rejected because a single missing WHERE clause leaks data across tenants — schema separation fails at the connection level rather than the query level, which is a much safer boundary.

### 2. ThreadLocal for Tenant Context

The tenant identifier is stored in a ThreadLocal for the duration of each request. This means the Hibernate connection provider can read it without any method-parameter threading. The key risk is context leakage in thread pool reuse, which is mitigated by a servlet filter that always clears the context in a finally block, and a custom TaskDecorator for async operations.

### 3. HandlerInterceptor Over OncePerRequestFilter for Tenant Validation

Tenant resolution (extracting the ID from the subdomain) runs as a servlet filter so it executes before Spring Security. Tenant validation (verifying the tenant exists and is active) runs as a HandlerInterceptor so it can access the Spring application context and tenant registry without coupling to the raw servlet API.

### 4. Per-Tenant UserDetailsService via AuthenticationManagerResolver

Spring Security 5.2+ introduced AuthenticationManagerResolver which selects an AuthenticationManager at runtime based on the request. Each tenant gets an AuthenticationManager backed by a UserDetailsService that queries that tenant's schema. This avoids a global user table and allows per-tenant password policies.

### 5. Flyway Schema Migration Strategy

Flyway is not run on application startup with the default datasource. Instead, a TenantProvisioningService runs Flyway programmatically against each tenant's schema during onboarding, and on application startup for any schema whose version is behind the current baseline. This keeps migrations tenant-scoped and prevents a bad migration from affecting all tenants simultaneously.

---

## Implementation

### Tenant Context (ThreadLocal Store)

```java
package com.rutik.systemdesign.spring.multitenant;

public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new InheritableThreadLocal<>();

    private TenantContext() {}

    public static void setTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant ID must not be blank");
        }
        CURRENT_TENANT.set(tenantId);
    }

    public static String getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
```

### Tenant Resolution Filter (Servlet Filter — before Security)

```java
package com.rutik.systemdesign.spring.multitenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(1)  // Must run before Spring Security filter chain
public class TenantResolutionFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-Tenant-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String tenantId = resolveTenantId(request);
            if (tenantId != null) {
                TenantContext.setTenantId(tenantId);
            }
            filterChain.doFilter(request, response);
        } finally {
            // Always clear — prevents context leakage into next request on same thread
            TenantContext.clear();
        }
    }

    private String resolveTenantId(HttpServletRequest request) {
        // 1. Try explicit header (useful for internal service-to-service calls)
        String headerTenant = request.getHeader(TENANT_HEADER);
        if (headerTenant != null && !headerTenant.isBlank()) {
            return headerTenant.toLowerCase();
        }

        // 2. Extract from subdomain: tenant1.api.example.com -> tenant1
        String serverName = request.getServerName(); // e.g. "tenant1.api.example.com"
        String[] parts = serverName.split("\\.");
        if (parts.length >= 4) {
            // subdomain.api.example.com pattern
            return parts[0].toLowerCase();
        }

        return null;
    }
}
```

### Tenant Registry Configuration Properties

```java
package com.rutik.systemdesign.spring.multitenant;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "multitenancy")
public record TenantProperties(
        Map<String, TenantConfig> tenants
) {
    public record TenantConfig(
            String schemaName,
            String jdbcUrl,
            String username,
            String password,
            boolean active
    ) {}
}
```

```yaml
# application.yml
multitenancy:
  tenants:
    acme:
      schema-name: tenant_acme
      jdbc-url: jdbc:postgresql://localhost:5432/saasdb
      username: saas_user
      password: ${ACME_DB_PASSWORD}
      active: true
    globex:
      schema-name: tenant_globex
      jdbc-url: jdbc:postgresql://localhost:5432/saasdb
      username: saas_user
      password: ${GLOBEX_DB_PASSWORD}
      active: true
```

### Tenant Validation Interceptor (Spring MVC HandlerInterceptor)

```java
package com.rutik.systemdesign.spring.multitenant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TenantValidationInterceptor implements HandlerInterceptor {

    private final TenantProperties tenantProperties;

    public TenantValidationInterceptor(TenantProperties tenantProperties) {
        this.tenantProperties = tenantProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                              HttpServletResponse response,
                              Object handler) throws Exception {
        String tenantId = TenantContext.getTenantId();

        if (tenantId == null) {
            response.sendError(HttpStatus.BAD_REQUEST.value(), "Tenant identifier missing");
            return false;
        }

        TenantProperties.TenantConfig config = tenantProperties.tenants().get(tenantId);

        if (config == null) {
            response.sendError(HttpStatus.NOT_FOUND.value(), "Unknown tenant: " + tenantId);
            return false;
        }

        if (!config.active()) {
            response.sendError(HttpStatus.FORBIDDEN.value(), "Tenant is suspended: " + tenantId);
            return false;
        }

        return true;
    }
}
```

### Web MVC Configuration

```java
package com.rutik.systemdesign.spring.multitenant;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final TenantValidationInterceptor tenantValidationInterceptor;

    public WebMvcConfig(TenantValidationInterceptor tenantValidationInterceptor) {
        this.tenantValidationInterceptor = tenantValidationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantValidationInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/health", "/api/tenants/register");
    }
}
```

### Hibernate Multi-Tenant Connection Provider

```java
package com.rutik.systemdesign.spring.multitenant;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Component
public class SchemaMultiTenantConnectionProvider
        implements MultiTenantConnectionProvider<String> {

    private final DataSource dataSource;
    private final TenantProperties tenantProperties;

    public SchemaMultiTenantConnectionProvider(DataSource dataSource,
                                                TenantProperties tenantProperties) {
        this.dataSource = dataSource;
        this.tenantProperties = tenantProperties;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantId) throws SQLException {
        TenantProperties.TenantConfig config = tenantProperties.tenants().get(tenantId);
        if (config == null) {
            throw new SQLException("No configuration found for tenant: " + tenantId);
        }

        Connection connection = dataSource.getConnection();
        // PostgreSQL SET search_path switches all unqualified table references to this schema
        connection.createStatement()
                  .execute("SET search_path TO " + config.schemaName() + ", public");
        return connection;
    }

    @Override
    public void releaseConnection(String tenantId, Connection connection) throws SQLException {
        // Reset search_path before returning to pool — defensive cleanup
        connection.createStatement().execute("SET search_path TO public");
        connection.close();
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }
}
```

### Current Tenant Identifier Resolver

```java
package com.rutik.systemdesign.spring.multitenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    private static final String DEFAULT_TENANT = "public";

    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenantId = TenantContext.getTenantId();
        return (tenantId != null) ? tenantId : DEFAULT_TENANT;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        // Return true to force Hibernate to re-validate tenant on session reuse
        return true;
    }
}
```

### JPA and Hibernate Configuration

```java
package com.rutik.systemdesign.spring.multitenant;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class HibernateMultiTenantConfig {

    private final SchemaMultiTenantConnectionProvider connectionProvider;
    private final TenantIdentifierResolver tenantIdentifierResolver;

    public HibernateMultiTenantConfig(SchemaMultiTenantConnectionProvider connectionProvider,
                                       TenantIdentifierResolver tenantIdentifierResolver) {
        this.connectionProvider = connectionProvider;
        this.tenantIdentifierResolver = tenantIdentifierResolver;
    }

    @Bean
    public HibernatePropertiesCustomizer hibernateMultiTenancyCustomizer() {
        return (properties) -> {
            properties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
            properties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantIdentifierResolver);
        };
    }
}
```

### Per-Tenant Spring Security Configuration

```java
package com.rutik.systemdesign.spring.multitenant;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TenantAuthenticationManagerResolver
        implements AuthenticationManagerResolver<HttpServletRequest> {

    private final Map<String, AuthenticationManager> managers = new ConcurrentHashMap<>();
    private final TenantUserDetailsServiceFactory userDetailsServiceFactory;

    public TenantAuthenticationManagerResolver(
            TenantUserDetailsServiceFactory userDetailsServiceFactory) {
        this.userDetailsServiceFactory = userDetailsServiceFactory;
    }

    @Override
    public AuthenticationManager resolve(HttpServletRequest request) {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant context for authentication");
        }
        // Cache AuthenticationManager per tenant — DaoAuthenticationProvider is thread-safe
        return managers.computeIfAbsent(tenantId, this::buildAuthenticationManager);
    }

    private AuthenticationManager buildAuthenticationManager(String tenantId) {
        UserDetailsService userDetailsService =
                userDetailsServiceFactory.createForTenant(tenantId);

        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(new BCryptPasswordEncoder(12));
        provider.afterPropertiesSet();

        return provider::authenticate;
    }
}
```

```java
package com.rutik.systemdesign.spring.multitenant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;

@Component
public class TenantUserDetailsServiceFactory {

    private final DataSource dataSource;
    private final TenantProperties tenantProperties;

    public TenantUserDetailsServiceFactory(DataSource dataSource,
                                            TenantProperties tenantProperties) {
        this.dataSource = dataSource;
        this.tenantProperties = tenantProperties;
    }

    public UserDetailsService createForTenant(String tenantId) {
        TenantProperties.TenantConfig config = tenantProperties.tenants().get(tenantId);
        String schemaName = config.schemaName();

        return username -> {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            // Query against the tenant-specific schema
            String sql = "SELECT username, password_hash, role FROM " +
                         schemaName + ".users WHERE username = ? AND enabled = true";

            return jdbc.query(sql, (rs, rowNum) -> {
                String role = rs.getString("role");
                return (UserDetails) User.builder()
                        .username(rs.getString("username"))
                        .password(rs.getString("password_hash"))
                        .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + role)))
                        .build();
            }, username).stream().findFirst()
                    .orElseThrow(() -> new UsernameNotFoundException(
                            "User not found: " + username + " in tenant " + tenantId));
        };
    }
}
```

### Security Filter Chain

```java
package com.rutik.systemdesign.spring.multitenant;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final TenantAuthenticationManagerResolver authenticationManagerResolver;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(TenantAuthenticationManagerResolver authenticationManagerResolver,
                          JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.authenticationManagerResolver = authenticationManagerResolver;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/api/health").permitAll()
                .anyRequest().authenticated())
            .authenticationManagerResolver(authenticationManagerResolver)
            .addFilterBefore(jwtAuthenticationFilter,
                             UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
```

### Flyway Tenant Migration Service

```java
package com.rutik.systemdesign.spring.multitenant;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

@Service
public class TenantMigrationService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TenantMigrationService.class);

    private final DataSource dataSource;
    private final TenantProperties tenantProperties;

    public TenantMigrationService(DataSource dataSource, TenantProperties tenantProperties) {
        this.dataSource = dataSource;
        this.tenantProperties = tenantProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        tenantProperties.tenants().forEach((tenantId, config) -> {
            if (config.active()) {
                migrateSchema(tenantId, config.schemaName());
            }
        });
    }

    public void migrateSchema(String tenantId, String schemaName) {
        log.info("Running Flyway migrations for tenant={} schema={}", tenantId, schemaName);
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                // Schema-specific migrations live under db/migration/tenant/
                // Common migrations under db/migration/common/
                .locations("classpath:db/migration/tenant", "classpath:db/migration/common")
                .schemas(schemaName)
                .defaultSchema(schemaName)
                .createSchemas(true)
                // Prefix migrations with V to keep ordering consistent
                .table("flyway_schema_history")
                .load();
        flyway.migrate();
        log.info("Flyway migration complete for tenant={}", tenantId);
    }

    // Called during tenant onboarding
    public void provisionNewTenant(String tenantId, String schemaName) {
        migrateSchema(tenantId, schemaName);
    }
}
```

### Async Context Propagation (TaskDecorator)

```java
package com.rutik.systemdesign.spring.multitenant;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    @Bean(name = "tenantAwareExecutor")
    public Executor tenantAwareExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setTaskDecorator(tenantContextDecorator());
        executor.setThreadNamePrefix("tenant-async-");
        executor.initialize();
        return executor;
    }

    @Bean
    public TaskDecorator tenantContextDecorator() {
        return task -> {
            // Capture the tenant ID from the submitting thread
            String tenantId = TenantContext.getTenantId();
            return () -> {
                try {
                    if (tenantId != null) {
                        TenantContext.setTenantId(tenantId);
                    }
                    task.run();
                } finally {
                    // Always clean up on the worker thread
                    TenantContext.clear();
                }
            };
        };
    }
}
```

---

## Spring Components Used

| Spring Component | Purpose |
|---|---|
| `OncePerRequestFilter` | Extracts tenant ID from subdomain or header early in filter chain |
| `HandlerInterceptor` | Validates tenant exists and is active before controller invocation |
| `@ConfigurationProperties` | Binds `multitenancy.tenants.*` YAML into typed `TenantProperties` record |
| `MultiTenantConnectionProvider` | Hibernate SPI — switches PostgreSQL `search_path` per request |
| `CurrentTenantIdentifierResolver` | Hibernate SPI — reads `TenantContext` ThreadLocal for each session |
| `AuthenticationManagerResolver` | Selects per-tenant `AuthenticationManager` at runtime |
| `DaoAuthenticationProvider` | Authenticates against per-tenant `users` table via `UserDetailsService` |
| `HibernatePropertiesCustomizer` | Injects Hibernate multi-tenancy SPI beans into JPA auto-configuration |
| `ApplicationRunner` | Runs Flyway migrations for all active tenant schemas on startup |
| `TaskDecorator` | Propagates `TenantContext` ThreadLocal across async thread pool boundaries |
| `SecurityFilterChain` | Stateless JWT-based filter chain wired to `AuthenticationManagerResolver` |

---

## Tradeoffs and Alternatives

### Schema-per-Tenant vs Alternatives

| Approach | Isolation | Connection Pool Efficiency | Operational Complexity |
|---|---|---|---|
| Schema-per-tenant (chosen) | High — DDL boundary | Good — shared pool | Medium — Flyway per schema |
| Row-level security | Medium — single bug leaks data | Excellent | Low |
| Database-per-tenant | Highest | Poor — pool per DB | Very high |
| Table prefix per tenant | Low — easy mistakes | Excellent | Low |

Schema-per-tenant was chosen because 500 tenants on one database is manageable in PostgreSQL, and the schema boundary means a missing WHERE clause cannot leak data.

### ThreadLocal vs Request-Scoped Bean

`ThreadLocal` was chosen over a Spring `@RequestScope` bean because Hibernate SPIs (`MultiTenantConnectionProvider`, `CurrentTenantIdentifierResolver`) are invoked outside the Spring request scope — they run during EntityManager session creation which may not have an active HTTP request context. `ThreadLocal` is always accessible.

### JWT vs Session-Based Auth

Stateless JWT was chosen because: horizontal scaling without sticky sessions, each token carries the tenant claim which serves as a second verification layer beyond the subdomain. The JWT is verified against the tenant's signing key (different key per tenant stored in a KMS), so a token for tenant A cannot be used against tenant B's API even if the subdomain is manipulated.

### Flyway at Startup vs On-Demand

Running all tenant migrations at startup was chosen over on-demand migration (running Flyway on first request for a tenant) because:
- On-demand migration blocks the first request and can cause timeouts
- Startup migration failures are surfaced immediately during deployment, not after traffic lands
- Accepted tradeoff: startup time grows linearly with tenant count (~200ms per tenant migration check)

---

## Interview Discussion Points

**Q: What happens if TenantContext.clear() is not called in the finally block?**

A: The thread returns to the application server thread pool still carrying the previous tenant's ID. The next request processed on that thread will silently query the wrong tenant's schema. This is a data leak bug, not a crash, which makes it particularly dangerous because it may not be caught in testing. The finally block in `TenantResolutionFilter` is the safety net. A defensive second measure is to call `clear()` at the start of `doFilterInternal` before `set()`, which handles the unlikely case where a filter higher in the chain threw an exception before reaching the finally.

**Q: How do you handle a request that has no tenant context, such as a health check endpoint?**

A: The `TenantValidationInterceptor` is registered with `excludePathPatterns("/api/health")`. The filter still runs but sets no context if the subdomain is the root API domain rather than a tenant subdomain. The `TenantIdentifierResolver.resolveCurrentTenantIdentifier()` returns the "public" schema as the default, which only contains the tenant registry and system tables — no tenant data.

**Q: With 500 tenants and connection pool size of 20, how many connections does the database see?**

A: With a single shared HikariCP pool of 20 connections, PostgreSQL sees exactly 20 connections regardless of tenant count. The `search_path` switch is a session-level setting applied after acquiring a connection and reset before returning it. This is the key advantage of schema-per-tenant over database-per-tenant. If each tenant had its own connection pool, the database would see 500 * 20 = 10,000 connections, which would exhaust PostgreSQL's connection limits.

**Q: How do you run a cross-tenant administrative query — for example, to generate a billing report?**

A: The system service runs with a separate admin `DataSource` that does not use the multi-tenant connection provider. Administrative jobs set `TenantContext` explicitly in a loop, or bypass it entirely by using schema-qualified SQL (`SELECT * FROM tenant_acme.orders UNION ALL SELECT * FROM tenant_globex.orders`). A separate `@Profile("admin")` configuration provides a non-multi-tenant `EntityManager` for these use cases.

**Q: How do you add a new tenant at runtime without restarting the application?**

A: The tenant registry is backed by the public schema's `tenants` table, not just the YAML file. A `TenantRegistrationController` endpoint: (1) inserts the tenant record into the registry, (2) calls `TenantMigrationService.provisionNewTenant()` to create the schema and run migrations, (3) calls `tenantProperties.tenants().put(...)` to add it to the in-memory registry. The `AuthenticationManagerResolver` cache lazily adds the new tenant's `AuthenticationManager` on first login. No restart required.

**Q: How do you prevent a slow tenant query from starving other tenants?**

A: All tenants share the same connection pool. A tenant running a full-table scan can exhaust the pool. Mitigations: (1) per-tenant query timeout via `SET statement_timeout = '5s'` immediately after `SET search_path`, (2) per-tenant connection quota enforced at the application level by counting active connections per tenant in a `ConcurrentHashMap` and throwing a 429 if over quota, (3) slow query logging with tenant ID tagged in the MDC allows identifying the offending tenant.

**Q: How does the JWT signing key separation work per tenant?**

A: During tenant provisioning, a 256-bit signing key is generated and stored in AWS KMS (or Vault) under a key alias scoped to that tenant. The JWT filter calls KMS to retrieve the key using the tenant ID embedded in the JWT header's `kid` (Key ID) claim. This means even if an attacker intercepts a valid JWT for tenant A, it cannot be accepted by tenant B's JWT verification because the signature check will fail — the `kid` points to tenant A's key, and that key does not validate a crafted tenant B claim.
