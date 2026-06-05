# Design: OAuth 2.1 Authorization Server (Spring Authorization Server)

> **"A trusted notary that issues time-limited credentials."**
> An authorization server is the single source of trust for "who are you and what can you do."
> It does not know your business logic — it only issues signed tokens that downstream services
> can verify without calling home. Every access decision ultimately traces back to a token the
> authorization server issued.
>
> **Key insight:** The authorization server's primary security obligation is to ensure that
> only the entity who proved their identity (the user) can redeem an authorization code, and
> that clients can never forge tokens. PKCE, short-lived tokens, refresh token rotation, and
> JWKS key rotation each address a different class of token theft or replay attack.

---

## 1. Requirements Clarification

### Functional Requirements
- Issue OAuth 2.1 authorization codes, access tokens (JWT), and refresh tokens to registered clients.
- Support PKCE (RFC 7636) for public clients (mobile apps, SPAs) and confidential clients.
- Provide a JWKS endpoint (`/oauth2/jwks`) so resource servers can verify tokens offline.
- Support client credential flow (machine-to-machine) and authorization code flow (user-facing).
- Expose OpenID Connect discovery document (`/.well-known/openid-configuration`).
- Rotate signing keys without invalidating in-flight tokens (dual-key rollover).

### Non-Functional Requirements
- **Latency:** Token issuance P99 < 50 ms; JWKS endpoint P99 < 10 ms (cached).
- **Availability:** 99.99% (four nines); authorization server downtime breaks all authentication.
- **Security:** Tokens expire in 15 minutes; refresh tokens expire in 7 days; refresh token rotation
  on every use (invalidate old, issue new).
- **Scalability:** Stateless token verification at resource servers (JWT + JWKS); authorization server
  handles 10,000 token requests/min (167 req/s).

### Out of Scope
- User identity storage (delegated to an Identity Provider via OIDC federation).
- Multi-factor authentication (handled by the IdP).
- Fine-grained authorization (handled by resource servers using token claims).

---

## 2. Scale Estimation

### Traffic
```
Active users:                      100,000
Average sessions per user per day: 3
Average token refreshes per session: 4 (15-min tokens, 1-hour sessions)
Token requests per day:            100,000 × 3 × 4 = 1,200,000
Peak multiplier (10×):             12,000,000 req/day → 139 req/s sustained, 1,390 req/s peak
```

### Storage
```
Authorization codes: TTL = 60 seconds; at 139 req/s: 139 × 60 = 8,340 active codes in Redis
Refresh tokens:      TTL = 7 days; 100,000 users × 3 sessions = 300,000 active refresh tokens
Refresh token size:  ~200 bytes each → 300,000 × 200 = 60 MB in Redis
JWKS keys:           2–3 RSA-2048 or EC P-256 keys; ~5 KB total in config/database
```

### Pod Sizing
```
JWT signing (RSA-2048): ~2,000 signatures/s per CPU core
139 req/s at P50 utilization: 1 core sufficient; 2 pods × 2 cores = 4× headroom
Memory per pod: 256 MB (Spring Authorization Server is not memory-hungry)
```

---

## 3. High-Level Architecture

```
 Browser / Mobile App
        |
        | (1) Authorization Request + PKCE code_challenge
        v
 +---------------------------+       +-----------------------+
 |  Authorization Server     |       |  Identity Provider    |
 |  (Spring Auth Server)     |<----->|  (Okta/Cognito/LDAP)  |
 |  /oauth2/authorize        |       |  (user authentication)|
 |  /oauth2/token            |       +-----------------------+
 |  /oauth2/jwks             |
 |  /oauth2/introspect       |
 |  /.well-known/openid-conf |
 +---------------------------+
        |           |
        |           | (2) authorization code
        v           v
 Client App    +---------+
        |      | Redis   |  ← code, refresh token storage
        | (3) code + verifier → access token + refresh token
        v
 +---------------------------+
 |  Resource Server (API)    |
 |  - validates JWT locally  |
 |  - fetches JWKS on startup|
 |  - caches JWKS 1 hour     |
 +---------------------------+
```

### Data Flow (Authorization Code + PKCE)
1. Client generates `code_verifier` (random, 43–128 chars), computes `code_challenge = BASE64URL(SHA256(verifier))`.
2. Client sends `GET /oauth2/authorize?response_type=code&client_id=X&redirect_uri=Y&code_challenge=Z&code_challenge_method=S256`.
3. Auth server authenticates user (via IdP or local login); stores `(code, code_challenge, client_id, scope, user)` in Redis with 60 s TTL.
4. Auth server redirects to `redirect_uri?code=<code>`.
5. Client sends `POST /oauth2/token` with `grant_type=authorization_code&code=<code>&code_verifier=<verifier>`.
6. Auth server retrieves code from Redis, verifies `BASE64URL(SHA256(verifier)) == code_challenge`.
7. Auth server issues JWT access token (15 min) + refresh token (7 days); deletes code from Redis.
8. On token expiry, client sends `POST /oauth2/token` with `grant_type=refresh_token&refresh_token=<old>`.
9. Auth server validates refresh token, issues new access + refresh token pair, **invalidates the old refresh token** (rotation).

---

## 4. Component Deep Dives

### 4.1 Spring Authorization Server Configuration

```java
@Configuration
@EnableWebSecurity
public class AuthorizationServerConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
            .oidc(Customizer.withDefaults());  // Enable OIDC 1.0

        http.exceptionHandling(ex -> ex
            .defaultAuthenticationEntryPointFor(
                new LoginUrlAuthenticationEntryPoint("/login"),
                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .formLogin(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository() {
        RegisteredClient webApp = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId("web-app")
            .clientSecret("{bcrypt}" + new BCryptPasswordEncoder().encode("secret"))
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("https://app.example.com/callback")
            .scope(OidcScopes.OPENID)
            .scope(OidcScopes.PROFILE)
            .scope("read:orders")
            .clientSettings(ClientSettings.builder()
                .requireProofKey(true)                // Enforce PKCE
                .requireAuthorizationConsent(false)   // Skip consent screen for trusted clients
                .build())
            .tokenSettings(TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofMinutes(15))
                .refreshTokenTimeToLive(Duration.ofDays(7))
                .reuseRefreshTokens(false)            // Rotate on every use
                .build())
            .build();

        RegisteredClient m2mClient = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId("payment-service")
            .clientSecret("{bcrypt}" + new BCryptPasswordEncoder().encode("svc-secret"))
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .scope("write:payments")
            .tokenSettings(TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofMinutes(5))
                .build())
            .build();

        return new InMemoryRegisteredClientRepository(webApp, m2mClient);
    }
}
```

### 4.2 JWT Token Customization

Add custom claims (user roles, tenant ID) to issued access tokens:

```java
@Bean
public OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer(UserDetailsService userDetailsService) {
    return context -> {
        if (context.getTokenType().equals(OAuth2TokenType.ACCESS_TOKEN)) {
            Authentication principal = context.getPrincipal();
            if (principal instanceof UsernamePasswordAuthenticationToken) {
                UserDetails user = userDetailsService.loadUserByUsername(principal.getName());
                Set<String> roles = user.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toSet());
                context.getClaims()
                    .claim("roles", roles)
                    .claim("tenant_id", resolveTenantId(principal.getName()));
            }
        }
    };
}
```

### 4.3 JWKS Key Rotation Without Token Invalidation

```java
@Bean
public JWKSource<SecurityContext> jwkSource() {
    // Generate two keys: one for signing new tokens, one kept for verifying in-flight tokens
    RSAKey activeKey  = generateRsaKey("key-2024-06");    // kid = "key-2024-06"
    RSAKey previousKey = generateRsaKey("key-2024-03");   // kid = "key-2024-03"
    JWKSet jwkSet = new JWKSet(List.of(activeKey, previousKey));
    return new ImmutableJWKSet<>(jwkSet);
}

// Resource servers call /oauth2/jwks, get both public keys, and verify by kid claim
// Active tokens use "key-2024-06"; tokens issued before rotation use "key-2024-03"
// Remove "key-2024-03" after its tokens' max TTL (15 min) has elapsed

private static RSAKey generateRsaKey(String keyId) {
    try {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
            .privateKey(keyPair.getPrivate())
            .keyID(keyId)
            .build();
    } catch (Exception e) {
        throw new IllegalStateException("Failed to generate RSA key", e);
    }
}
```

### 4.4 Broken Pattern: Storing Authorization Codes In-Memory

```java
// BROKEN: in-memory ConcurrentHashMap for authorization codes
private final Map<String, AuthCode> codes = new ConcurrentHashMap<>();

public void storeCode(String code, AuthCode authCode) {
    codes.put(code, authCode);
    // No TTL; no eviction; codes accumulate forever
    // Two auth server pods have separate maps: code issued on pod-1 cannot be redeemed on pod-2
}
```

**Failure mode 1:** Code issued on pod-1 is redirected to the client. Client calls `/oauth2/token`
which is load-balanced to pod-2. Pod-2 has no record of the code — returns `invalid_grant` error.
**Failure mode 2:** Unused codes accumulate in memory. 10,000 authorization attempts × 200 bytes
= 2 MB/hour; after one week of leakage = 336 MB OOM crash.

**Fix:** Use Spring Authorization Server's `JdbcOAuth2AuthorizationService` backed by PostgreSQL
or `RedisOAuth2AuthorizationService` (community extension). Authorization codes are stored centrally
with TTL enforcement.

### 4.5 Redis-Backed Authorization Service

```java
@Configuration
public class TokenStoreConfig {

    @Bean
    public OAuth2AuthorizationService authorizationService(
            RegisteredClientRepository clients,
            RedisConnectionFactory redisConnectionFactory) {
        // RedisOAuth2AuthorizationService from spring-authorization-server community
        // stores authorizations as JSON; TTL matches token lifetime
        return new RedisOAuth2AuthorizationService(redisConnectionFactory, clients);
    }

    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(
            RegisteredClientRepository clients,
            RedisConnectionFactory redisConnectionFactory) {
        return new RedisOAuth2AuthorizationConsentService(redisConnectionFactory, clients);
    }
}
```

### 4.6 Resource Server Configuration

```java
@Configuration
@EnableMethodSecurity
public class ResourceServerConfig {

    @Bean
    public SecurityFilterChain resourceServerFilterChain(HttpSecurity http) throws Exception {
        http.oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt
                .jwkSetUri("https://auth.example.com/oauth2/jwks")
                // JWKS is cached locally; re-fetched on cache miss (unknown kid) or 1-hour TTL
            )
        );
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/orders/**").hasAuthority("SCOPE_read:orders")
            .anyRequest().authenticated()
        );
        return http.build();
    }
}
```

---

## 5. Design Decisions & Tradeoffs

### Decision 1: JWT vs Opaque Tokens

| Property | JWT | Opaque Token |
|----------|-----|-------------|
| Verification | Local (JWKS cached) | Remote introspection call |
| Latency | 0 ms (offline) | ~5–10 ms (introspection endpoint) |
| Revocation | Hard (must wait for expiry) | Immediate (delete from store) |
| Token size | 300–600 bytes | 32 bytes |
| Info leakage | Claims visible in payload | Opaque to client |

**Decision:** JWT for access tokens (low latency, offline verification). Short 15-minute TTL
mitigates the revocation gap — a stolen token expires quickly.

### Decision 2: Refresh Token Rotation vs Long-Lived Refresh Tokens

With rotation disabled, a stolen refresh token can be reused indefinitely. With rotation enabled,
each refresh creates a new refresh token and invalidates the old. If the old token is presented
after rotation, the authorization server detects a "refresh token reuse" attack (two valid tokens
for the same session) and revokes the entire token family.

**Decision:** `reuseRefreshTokens(false)`. The cost is that single-use refresh tokens require
both old-token deletion and new-token issuance to be atomic — Spring Authorization Server handles
this transactionally when using `JdbcOAuth2AuthorizationService`.

### Decision 3: PKCE vs Client Secret for Confidential Clients

PKCE is mandatory for public clients (SPA, mobile). For confidential clients (server-side web apps),
PKCE adds a defense-in-depth layer against authorization code interception. OAuth 2.1 (draft) makes
PKCE mandatory for all clients. Spring Authorization Server's `requireProofKey(true)` enforces this.

### Decision 4: InMemory vs JDBC vs Redis Token Store

| Store | Suitable for | Limitation |
|-------|-------------|-----------|
| `InMemoryOAuth2AuthorizationService` | Single-pod dev/test | Lost on restart; no cross-pod sharing |
| `JdbcOAuth2AuthorizationService` | Production multi-pod | DB write on every token operation |
| Redis (community `RedisOAuth2AuthorizationService`) | Production high-throughput | Redis dependency; data loss on Redis failure |

**Decision:** Redis for authorization codes (60-second TTL; fast reads) + PostgreSQL for refresh
tokens (7-day lifetime; need durability; survive Redis restart).

### Decision 5: EC P-256 vs RSA-2048 Signing Keys

| Algorithm | Signature size | Signing speed | Verification speed | Token size |
|-----------|---------------|--------------|-------------------|------------|
| RSA-2048 | 256 bytes | ~0.5 ms | ~0.05 ms | ~600 bytes |
| EC P-256 | 64 bytes | ~0.1 ms | ~0.1 ms | ~400 bytes |

EC P-256 (ES256) produces smaller tokens and signs 5× faster. Modern clients and Java (since 11)
support ES256. Use EC P-256 for new deployments; keep RSA-2048 for compatibility with legacy clients.

---

## 6. Real-World Implementations

**Okta / Auth0:** Fully managed authorization servers built on OAuth 2.0/OIDC. They use RS256 JWT
access tokens, refresh token rotation, and expose JWKS endpoints with key rotation built in.
Auth0's documentation explicitly describes their 5-minute rolling JWKS cache at resource servers,
requiring resource servers to re-fetch on unknown `kid` before rejecting a token.

**Google OAuth 2.0:** Uses JWT access tokens for Google APIs. The JWKS endpoint
(`https://www.googleapis.com/oauth2/v3/certs`) is publicly cached. Google rotates signing keys
every few days; resource servers are expected to re-fetch JWKS on `invalid_signature` errors or
on `kid` miss. Google's token introspection endpoint (`tokeninfo`) allows opaque-token verification
for legacy integrations.

**Spring Authorization Server (Broadcom):** The official Spring OAuth2 server (GA since 2023,
Spring Security 6.x). Used by enterprises building private OAuth2/OIDC infrastructure. LinkedIn,
Pivotal, and major banking clients use it as the backend for internal API authorization. It ships
with JDBC stores for production use and supports custom token customizers via `OAuth2TokenCustomizer`.

**Keycloak (Red Hat):** Open-source IdP + authorization server. Uses Postgres-backed token store;
implements refresh token rotation; supports key providers (RSA, EC, HMAC). Used by 1000+ enterprises.
Keycloak's key management UI allows live key rotation with graceful expiry of old keys.

---

## 7. Technologies & Tools

| Technology | Role | Notes |
|------------|------|-------|
| Spring Authorization Server 1.x | OAuth2 / OIDC server | Production GA; Spring Boot 3.x; replaces legacy Spring Security OAuth2 |
| Nimbus JOSE+JWT | JWT encoding / JWKS | Bundled with Spring Authorization Server; handles RS256, ES256, PS256 |
| Spring Security 6.x | Resource server filter chain | `oauth2ResourceServer()` DSL; JWKS auto-fetch + cache |
| Redis (Lettuce) | Authorization code + consent store | Community `RedisOAuth2AuthorizationService` |
| PostgreSQL | Refresh token + client registration store | `JdbcOAuth2AuthorizationService`; survives Redis restart |
| `spring-security-oauth2-jose` | JWT validation at resource server | Bundled; `NimbusJwtDecoder` with JWKS cache |

---

## 8. Operational Playbook

### Runbook 1: Token Signing Key Rotation

**Trigger:** Scheduled quarterly or on key compromise detection.

**Steps:**
1. Generate new key with new `kid` (e.g., `key-2024-09`).
2. Add new key to `jwkSource()` alongside current key — resource servers cache JWKS for 1 hour.
3. Deploy auth server with both keys: new key is the default signer; old key retained in JWKS for verification.
4. Wait 15 minutes (access token TTL) — all outstanding tokens signed by old key expire.
5. Remove old key from `jwkSource()` and redeploy.

**Pitfall:** Removing the old key before its tokens expire causes `invalid_signature` errors at resource
servers for up to 15 minutes. Always wait at least one full access token TTL after switching the
active key before removing the old key.

---

### Runbook 2: Refresh Token Reuse Attack Detected

**Symptom:** `OAuth2AuthorizationService.save()` throws `OAuth2AuthorizationException` with
`error=invalid_token, description=refresh token already invalidated`; authorization server revokes
all tokens in the affected session family.

**Diagnosis:**
1. Check auth server logs for `principal_name` and `client_id` of the attacked session.
2. Determine if the reuse is from a legitimate client retry (network timeout on the first refresh)
   or an attacker using a stolen token.

**Mitigation for legitimate retry:** Allow a 2-second grace window where the same refresh token can
be reused once. Spring Authorization Server does not build this in; implement via a short TTL Redis
key `used:<old_refresh_token>` with 2 s TTL before deletion.

**Resolution:** Inform the user their session was terminated due to a suspected security event.
Require re-authentication.

---

### Runbook 3: JWKS Endpoint Down — Resource Servers Rejecting All Tokens

**Symptom:** Resource servers return 401 on all requests; logs show `Failed to fetch JWKS`.

**Diagnosis:**
1. Check auth server health: `GET /actuator/health`.
2. Verify JWKS is reachable: `curl https://auth.example.com/oauth2/jwks`.
3. Check if resource server's JWKS cache expired: default cache TTL in `NimbusJwtDecoder` is 5 minutes
   for re-fetch; tokens already verified before the outage continue to work until cache expires.

**Mitigation:** Resource servers using `NimbusJwtDecoder` with `jwkSetUri` cache the JWKS in memory.
They only re-fetch when they encounter a `kid` they don't recognize or after the TTL. Tokens issued
before the outage continue to be verified from cache — no immediate user impact if the JWKS endpoint
goes down for < 5 minutes.

**Resolution:** Deploy auth server in at least 2 pods behind a load balancer with `minReadySeconds=30`
and readiness probe on `/actuator/health/readiness`. The JWKS endpoint must be available before
pods receive traffic.

---

### Runbook 4: Client Secret Leaked in Git

**Symptom:** A developer accidentally committed `client_secret: my-secret` to a public repository.

**Mitigation:**
1. Immediately revoke all active tokens for the affected `client_id`:
   `DELETE FROM oauth2_authorization WHERE registered_client_id = '<id>'` (JDBC store).
2. Generate a new `client_secret` in the `registered_client` table (BCrypt-hashed).
3. Rotate env vars in all deployments using the affected client.

**Resolution:** Store client secrets in Vault or K8s Secrets; never in `application.yml`.
Use `@ConfigurationProperties` bound to `SPRING_SECURITY_OAUTH2_CLIENT_*` environment variables.

---

## 9. Common Pitfalls & War Stories

**Pitfall 1: Clock Skew Between Auth Server and Resource Server (financial SaaS, 2022)**
A resource server's clock ran 45 seconds ahead of the authorization server. JWT `iat` (issued-at)
claims appeared to be in the future, causing the resource server's `NimbusJwtDecoder` to reject
tokens with `Token was issued in the future`. Affected 100% of login attempts for 3 hours.
Impact: $800K in lost transactions. Fix: add `clockSkew(Duration.ofSeconds(30))` to `JwtDecoder`
configuration; ensure NTP sync across all nodes.

---

**Pitfall 2: Refresh Token Rotation Race Condition (mobile app backend, 2021)**
Mobile apps on poor networks sometimes retry the `POST /oauth2/token?grant_type=refresh_token`
request on timeout. With rotation enabled, the server issued a new token but the client didn't
receive the response. The client retried with the original (now-invalidated) refresh token.
The auth server revoked the entire session (security policy). 15% of mobile users on 3G/4G
connections were logged out daily. Fix: implement a 5-second replay window using Redis — store
`replay:<old_refresh_token>` → `<new_refresh_token>` with 5 s TTL. On reuse within the window,
return the cached new token instead of revoking.

---

**Pitfall 3: JWKS Cached Indefinitely by CDN (enterprise API platform, 2023)**
The JWKS endpoint (`/oauth2/jwks`) was served through a CDN with a misconfigured 24-hour cache-control.
After a key rotation, resource servers continued verifying with the old JWKS from CDN cache for
24 hours. New tokens signed with the new key failed verification. Fix: set `Cache-Control: max-age=3600`
on the JWKS endpoint; configure CDN to respect `Cache-Control` headers.

---

**Pitfall 4: Scope Inflation via Token Customizer Bug (SaaS platform, 2022)**
A `OAuth2TokenCustomizer` that loaded user roles from the database accidentally included every role
in the database rather than only the user's assigned roles — a SQL join without a WHERE clause.
All access tokens for the next 2 hours contained `roles: ["ADMIN", "SUPERUSER", "BILLING_ADMIN", ...]`
for every user. A security audit triggered by an anomaly detection alert caught it in 45 minutes.
No known exploitation. Fix: integration tests that verify token claims for specific test users;
automated security scanning of token payloads in staging.

---

**Pitfall 5: Missing PKCE Enforcement — Authorization Code Interception (pen-test finding, 2023)**
A mobile banking app used the authorization code flow without PKCE (`requireProofKey=false`).
A pen-tester installed a malicious app that registered the same custom URI scheme (`banking://callback`)
as the legitimate app. On Android, when the legitimate app initiated the OAuth flow, the authorization
code redirect was intercepted by the malicious app (OS asked the user which app to open). The malicious
app exchanged the code for a token — successfully, because no PKCE verifier was required.
Fix: enable `requireProofKey(true)` on all clients; mandated by OAuth 2.1 for all flows.

---

## 10. Capacity Planning

### Token Signing Throughput

```
RSA-2048 signing:  ~2,000 signatures/s per CPU core (Java 17, no HSM)
EC P-256 signing:  ~10,000 signatures/s per CPU core
Peak token demand: 1,390 req/s (including token refresh)
Cores needed (EC): ceil(1,390 / 10,000) = 1 core; 2 pods × 2 cores = 8× safety margin
```

### Redis Load (Authorization Codes)
```
Active codes:     139 new codes/s × 60 s TTL = 8,340 concurrent codes in Redis
Redis reads:      139 HGET/s (code lookups)
Redis writes:     139 HSET/s (code storage) + 139 DEL/s (code redemption) = 278 writes/s
Total Redis ops:  ~417 ops/s (negligible for a single r6g.small)
```

### PostgreSQL Load (Refresh Tokens)
```
Active refresh tokens: 300,000 (100k users × 3 sessions)
Writes per minute:     100 refreshes/min (at steady state)
Reads per minute:      100 reads/min
Postgres IOPS:         < 10 IOPS (far below even db.t3.micro capacity)
```

---

## 11. Interview Discussion Points

**Q: What is PKCE and why is it required for mobile and SPA clients?**
PKCE (Proof Key for Code Exchange) prevents authorization code interception attacks on public
clients. The client generates a random `code_verifier` and sends `code_challenge = BASE64URL(SHA256(verifier))`
in the authorization request. When exchanging the code for tokens, it sends the original `code_verifier`.
The authorization server verifies `SHA256(verifier) == stored_challenge` before issuing tokens.
An attacker who intercepts the authorization code cannot exchange it without the `code_verifier`,
which never left the legitimate client. OAuth 2.1 mandates PKCE for all clients.

**Q: Why are access tokens short-lived (15 minutes) while refresh tokens are long-lived (7 days)?**
Access tokens are sent on every API request — they're the highest-value target for theft.
A 15-minute TTL limits the window during which a stolen access token is useful without requiring
revocation infrastructure at every resource server. Refresh tokens are sent only to the authorization
server over a secure channel, are stored server-side, and can be invalidated immediately on
compromise. The longer TTL (7 days) avoids forcing users to re-authenticate daily, while rotation
on every use detects theft: if an attacker uses a stolen refresh token, the legitimate client's
next refresh detects the invalidated token and triggers session revocation.

**Q: How do resource servers verify JWT tokens without calling the authorization server on every request?**
Resource servers fetch the JWKS (JSON Web Key Set) from the authorization server's `/oauth2/jwks`
endpoint at startup and cache it locally. To verify a JWT, the resource server reads the `kid`
(key ID) claim from the JWT header, finds the matching public key in the cached JWKS, and verifies
the JWT signature cryptographically. No network call is needed per request. If the `kid` is not
in the cache (e.g., after a key rotation), the resource server re-fetches JWKS once to handle the
rotation. Typical JWKS cache TTL is 1 hour; Spring's `NimbusJwtDecoder` re-fetches on unknown `kid`.

**Q: What is refresh token rotation and how does it detect token theft?**
With rotation, every successful token refresh consumes the current refresh token and issues a new
one. If a stolen refresh token is used by an attacker after the legitimate client has already used
it (rotation means the old token is now invalid), the authorization server detects that an already-
invalidated token was presented. This is a "refresh token reuse" signal — the server should revoke
the entire token family (all refresh tokens for that session) and force re-authentication. Without
rotation, a stolen refresh token is valid indefinitely until it expires (7 days), giving an
attacker a week-long access window.

**Q: How would you handle a key rotation without invalidating existing tokens?**
Maintain at least two keys in the JWKS endpoint simultaneously: the new active key (used for
signing new tokens) and the previous key (retained for verifying existing tokens). Resource
servers cache the full JWKS with both keys. Tokens signed by the old key include the old `kid`
in their header; resource servers verify against the matching key. After one full access token
TTL (15 minutes for a 15-minute token), all tokens signed by the old key have expired naturally.
Only then should the old key be removed from the JWKS response.

**Q: What is the difference between `OAuth2AuthorizationService` backed by Redis vs JDBC?**
Redis: stores authorization codes, access tokens, and refresh tokens as hash values with TTL-based
expiry. Extremely fast reads/writes (~0.3 ms); TTL auto-expires tokens without a cleanup job.
Risk: Redis failure loses all active sessions (no durability). JDBC: stores tokens in a relational
table with strong consistency and durability. Slower writes (~2–5 ms); requires a cleanup job to
delete expired rows. For high-throughput scenarios: use Redis for short-lived codes; use JDBC for
long-lived refresh tokens where durability matters.

**Q: How do you prevent the authorization server from becoming a single point of failure?**
Run at least 2 pods behind a load balancer. The authorization server is mostly stateless for
JWT verification (keys loaded from config). State (codes, tokens) must be centralized in Redis
or JDBC — not in-memory. For multi-region HA: deploy an authorization server per region with
a shared token store (Redis Global Datastore or Aurora Global Database). JWKS endpoints are
cached at resource servers for up to 1 hour, so a brief auth server outage (< 1 hour) doesn't
invalidate existing tokens.

**Q: How do you implement per-resource fine-grained authorization using OAuth2 scopes?**
Scopes defined at authorization time (`read:orders`, `write:payments`) are embedded as JWT claims
(`scope` claim). Resource servers check `hasAuthority("SCOPE_read:orders")` using Spring Security's
`@EnableMethodSecurity` + `@PreAuthorize`. For row-level access (only see your own orders),
scopes alone are insufficient — the resource server must additionally filter by the `sub` (user ID)
claim from the JWT. OAuth2 scopes are coarse-grained (capability); row-level policies are implemented
in the resource server's business logic.

**Q: What are the implications of using `requireAuthorizationConsent(false)`?**
With consent skipped, users are not shown a screen asking them to approve the requested scopes —
the authorization server silently grants them. This is appropriate for first-party applications
(your own SPA/mobile app accessing your own APIs) where the user already agreed to terms of service.
For third-party integrations (like an OAuth app on GitHub that accesses your data), consent MUST
be enabled — users need to see and approve what data they're granting access to. Disabling consent
for third-party clients is an OAuth2 security anti-pattern.

**Q: How would you implement client credential flow for machine-to-machine authentication?**
Machine-to-machine services use `grant_type=client_credentials`. The service authenticates with
its `client_id` + `client_secret` (or private key JWT for higher security). No user is involved.
The issued access token contains `client_id` as `sub` and the granted `scope`. In Spring Authorization
Server, register the client with `AuthorizationGrantType.CLIENT_CREDENTIALS` and the allowed scopes.
Resource servers verify the token the same way (JWKS-based JWT verification). Use short-lived tokens
(5 minutes); services should cache tokens until near-expiry and request a fresh one before expiry.

---

## Cross-Cutting References

- [Zero-Downtime Deploys and Config](cross_cutting/zero_downtime_deploys_and_config.md) — rolling key rotation without token invalidation; `@RefreshScope` for JWKS configuration reloading.
- [Testcontainers and Test Strategy](cross_cutting/testcontainers_and_test_strategy.md) — integration testing OAuth2 flows with a real `PostgreSQLContainer` for token storage.
- [OTel Observability for Spring](cross_cutting/otel_observability_for_spring.md) — distributed tracing of token issuance; `@Observed` on token generation methods.
- [Resilience4j Patterns](cross_cutting/resilience4j_patterns.md) — circuit breaker protecting resource server JWKS re-fetch; retry + timeout for token introspection endpoint.
