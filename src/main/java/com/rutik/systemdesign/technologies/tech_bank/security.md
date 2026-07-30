# Security & identity — technology bank

<!-- tech-bank tier: security -->

The 217 tools whose PRIMARY role — the first, best-weighted one — sits in
the **Security & identity** tier. A tool appears in exactly one shard and carries all
of its roles here, so Redis is filed under Caching and still declares its
key-value, rate-limiting, broker and semantic-cache roles.

Record format and the full rules: [tech_bank.md](tech_bank.md).

### A2A JWT signing
**Short:** Signing A2A agent messages with JWTs so a receiving agent can verify the sender's identity.
**Kind:** concept
**Lang:** *
**Roles:** security/authentication-and-identity @1, llm-apps/agent-framework @3

### ACM
**Short:** AWS Certificate Manager: issues, stores and auto-renews TLS certificates for AWS load balancers and CDN.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, platform-delivery/cloud-platform-and-cost @3

### AdvBench
**Short:** Standard adversarial-prompt benchmark of harmful behaviours, used to measure jailbreak and refusal rates.
**Kind:** dataset
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1, ml-lifecycle/evaluation-and-benchmarks @2

### Adversarial Robustness Toolbox
**Short:** IBM library implementing evasion, poisoning and extraction attacks plus defenses across model types.
**Kind:** tech
**Lang:** python
**Roles:** security/ai-safety-and-guardrails @1, ml-lifecycle/evaluation-and-benchmarks @3

### AgentHarm
**Short:** Benchmark of 110 harmful agent behaviours over synthetic tools that scores refusal and harm separately.
**Kind:** dataset
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1, ml-lifecycle/evaluation-and-benchmarks @1

### Ansible Vault
**Short:** Ansible's built-in encryption for secrets kept in playbooks and vars files, decrypted at run time.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, platform-delivery/infrastructure-as-code-and-config @2

### Anthropic's Responsible Scaling Policy
**Short:** Published governance framework tying model capability thresholds to required safety measures before release.
**Kind:** concept
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1, security/privacy-and-compliance @3

### Argon2
**Short:** Memory-hard password hashing function (RFC 9106, PHC winner); OWASP's recommended choice for storing passwords.
**Kind:** spec
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, security/authentication-and-identity @2

### argon2-cffi
**Short:** Python binding for Argon2id password hashing with tunable memory, time and parallelism cost.
**Kind:** tech
**Lang:** python
**Roles:** security/secrets-and-cryptography @1, security/authentication-and-identity @2

### ARX
**Short:** Data anonymization tool applying k-anonymity, l-diversity and t-closeness to tabular datasets before release.
**Kind:** tech
**Lang:** *
**Roles:** security/privacy-and-compliance @1

### Auth0
**Short:** Managed identity provider and OAuth2/OIDC authorization server with social login, SAML and custom claim actions.
**Kind:** tech
**Lang:** *
**Roles:** security/authentication-and-identity @1, security/authorization-and-policy @3, traffic-edge/api-gateway @3

### authlib
**Short:** Python OAuth 2.0/OIDC and JOSE library covering full third-party login flows on both client and server sides.
**Kind:** tech
**Lang:** python
**Roles:** security/authentication-and-identity @1, security/secrets-and-cryptography @3

### AutoAttack
**Short:** Parameter-free ensemble of strong adversarial attacks; the standard for reporting honest robust accuracy.
**Kind:** tech
**Lang:** python
**Roles:** security/ai-safety-and-guardrails @1, ml-lifecycle/evaluation-and-benchmarks @2

### AutoDAN
**Short:** Reference implementation of genetic-algorithm jailbreak search, used to red-team model refusal behavior.
**Kind:** tech
**Lang:** python
**Roles:** security/ai-safety-and-guardrails @1

### AutoDAN-Turbo
**Short:** Reference implementation of automated jailbreak generation via genetic search and strategy discovery.
**Kind:** tech
**Lang:** python
**Roles:** security/ai-safety-and-guardrails @1

### AWS Bedrock Guardrails
**Short:** AWS managed content and topic filtering for LLMs, with versioned policies and a standalone ApplyGuardrail API.
**Kind:** tech
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1, platform-delivery/cloud-platform-and-cost @3

### AWS Cedar
**Short:** AWS's open policy language and evaluation engine for RBAC/ABAC authorization decisions in applications.
**Kind:** tech
**Lang:** *
**Roles:** security/authorization-and-policy @1

### AWS Cognito
**Short:** AWS managed identity service: OIDC/OAuth2 user pools, hosted sign-in, MFA and federated identity providers.
**Kind:** tech
**Lang:** *
**Roles:** security/authentication-and-identity @1, platform-delivery/cloud-platform-and-cost @3

### AWS Comprehend PII
**Short:** Managed AWS API that detects and redacts personally identifiable information in text across many languages.
**Kind:** tech
**Lang:** *
**Roles:** security/privacy-and-compliance @1, applied-ml/nlp-and-text @2, security/ai-safety-and-guardrails @3

### AWS IAM DB Auth
**Short:** RDS/Aurora feature swapping database passwords for short-lived IAM-signed tokens at connection time.
**Kind:** tech
**Lang:** *
**Roles:** security/authentication-and-identity @1, data-access/drivers-and-connection-pooling @3

### AWS KMS
**Short:** AWS managed key service: creates and rotates keys and performs envelope encryption without exposing key material.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, security/privacy-and-compliance @3, platform-delivery/cloud-platform-and-cost @3

### AWS Macie
**Short:** AWS managed service that discovers and classifies PII and other sensitive data in S3.
**Kind:** tech
**Lang:** *
**Roles:** security/privacy-and-compliance @1

### AWS Secrets Manager
**Short:** AWS managed secret store with versioning and built-in automatic rotation for database credentials and API keys.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, platform-delivery/cloud-platform-and-cost @3

### AWS SSM Parameter Store
**Short:** Cheap AWS store for configuration parameters and KMS-encrypted secrets, versioned and readable via IAM.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, apis-frameworks/dependency-injection-and-config @2, platform-delivery/cloud-platform-and-cost @3

### AWS WAF
**Short:** AWS web application firewall: managed and custom rules blocking common attack patterns at the edge.
**Kind:** tech
**Lang:** *
**Roles:** security/supply-chain-and-runtime-security @1, traffic-edge/rate-limiting-and-resilience @2, platform-delivery/cloud-platform-and-cost @3

### Azure AD
**Short:** Microsoft's cloud identity provider (now Entra ID) issuing OIDC/OAuth tokens for SSO, MFA and conditional access.
**Kind:** tech
**Lang:** *
**Roles:** security/authentication-and-identity @1, security/authorization-and-policy @3

### Azure AI Content Safety
**Short:** Microsoft managed moderation API: severity-scored text/image categories, Prompt Shields and groundedness detection.
**Kind:** tech
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1, platform-delivery/cloud-platform-and-cost @3

### Azure Key Vault
**Short:** Azure managed store for secrets, certificates and keys, with HSM-backed key management and rotation.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, platform-delivery/cloud-platform-and-cost @3

### Azure PII
**Short:** Azure AI Language managed API that detects and redacts personally identifiable information across many languages.
**Kind:** tech
**Lang:** *
**Roles:** security/privacy-and-compliance @1, applied-ml/nlp-and-text @3

### bcrypt
**Short:** Adaptive password hashing with a tunable cost factor (10-12 typical, ~100-300ms); implemented in every language.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, security/authentication-and-identity @2

### BCryptPasswordEncoder
**Short:** Spring Security password hasher applying bcrypt with a tunable cost factor and per-password salt.
**Kind:** api
**Lang:** java
**Roles:** security/secrets-and-cryptography @1, security/authentication-and-identity @2

### Bedrock Guardrails
**Short:** AWS Bedrock content filter applying denied topics, word filters, PII redaction and grounding checks.
**Kind:** tech
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1, platform-delivery/cloud-platform-and-cost @3

### Bouncy Castle
**Short:** Full-suite Java/C# crypto provider covering algorithms and PKI formats the JDK does not ship.
**Kind:** tech
**Lang:** java
**Roles:** security/secrets-and-cryptography @1

### BouncyCastle
**Short:** Java crypto provider adding algorithms the JDK lacks: Argon2, scrypt, ChaCha20-Poly1305, extended curves, GOST.
**Kind:** tech
**Lang:** java
**Roles:** security/secrets-and-cryptography @1

### Burp Suite
**Short:** Intercepting proxy and DAST scanner for probing a running web application for vulnerabilities.
**Kind:** tech
**Lang:** *
**Roles:** security/supply-chain-and-runtime-security @1, devtools/testing-and-mocking @3

### Casbin
**Short:** Embeddable access-control library whose model file expresses RBAC, ABAC or ACL rules evaluated per request.
**Kind:** tech
**Lang:** *
**Roles:** security/authorization-and-policy @1

### cert-manager
**Short:** Kubernetes operator that issues and auto-renews TLS certificates from ACME or internal CAs into Secrets.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, platform-delivery/kubernetes-and-orchestration @2

### checkov
**Short:** Static security scanner for Terraform, CloudFormation and Kubernetes manifests with Python-authored policy rules.
**Kind:** tech
**Lang:** *
**Roles:** security/supply-chain-and-runtime-security @1, platform-delivery/infrastructure-as-code-and-config @2, security/authorization-and-policy @2, devtools/static-analysis-and-linting @2

### circuit-breaker tooling
**Short:** Representation-engineering defence that ablates harmful internal directions rather than filtering the output text.
**Kind:** concept
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1, applied-ml/interpretability-fairness-and-causal @2

### Cisco AI Defense
**Short:** Commercial AI firewall: runtime guardrails plus continuous validation and red-teaming of deployed model applications.
**Kind:** tech
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1, security/supply-chain-and-runtime-security @3

### CleverHans
**Short:** Adversarial-example library for benchmarking attacks and defences against machine-learning models.
**Kind:** tech
**Lang:** python
**Roles:** security/ai-safety-and-guardrails @1, ml-lifecycle/evaluation-and-benchmarks @2

### Cloud IAM
**Short:** Google Cloud's identity and access management: service accounts, roles and policy bindings on every resource.
**Kind:** tech
**Lang:** *
**Roles:** security/authentication-and-identity @1, security/authorization-and-policy @1, platform-delivery/cloud-platform-and-cost @3

### Conftest
**Short:** CLI that runs Rego policies against config files and Terraform plan JSON as a CI gate.
**Kind:** tech
**Lang:** *
**Roles:** security/authorization-and-policy @1, platform-delivery/infrastructure-as-code-and-config @2, devtools/static-analysis-and-linting @2

### Constitutional AI
**Short:** Anthropic's alignment method where a model critiques and revises its own outputs against a written set of principles.
**Kind:** concept
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1, model-training/alignment-and-rl @2

### Container hardening
**Short:** Shrinking a container's attack surface: minimal base image, non-root user, read-only mounts, digest-pinned tags.
**Kind:** concept
**Lang:** *
**Roles:** security/supply-chain-and-runtime-security @1, platform-delivery/container-and-image @2

### cosign
**Short:** Sigstore CLI that signs, attests and verifies container images and artifacts, keylessly via OIDC identities.
**Kind:** tech
**Lang:** *
**Roles:** security/supply-chain-and-runtime-security @1, security/secrets-and-cryptography @3, platform-delivery/container-and-image @3

### cryptography
**Short:** Python's standard crypto library: AES-GCM, RSA, ECDSA, X25519, X.509 and JWT-grade key operations.
**Kind:** tech
**Lang:** python
**Roles:** security/secrets-and-cryptography @1

### Deletion ledgers
**Short:** A durable record of deletion requests, used to fan a right-to-erasure out across every store that holds the data.
**Kind:** concept
**Lang:** *
**Roles:** security/privacy-and-compliance @1

### Dependabot
**Short:** GitHub bot that opens automated dependency-bump PRs and flags vulnerable transitive versions.
**Kind:** tech
**Lang:** *
**Roles:** security/supply-chain-and-runtime-security @1, devtools/build-and-dependency-management @2, platform-delivery/ci-cd-and-release @3

### detect-secrets
**Short:** Pre-commit and CI scanner that flags credentials accidentally committed into source code.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, devtools/static-analysis-and-linting @2, security/supply-chain-and-runtime-security @2

### did-resolver
**Short:** JavaScript library that resolves decentralized identifiers (did:web and others) to DID Documents.
**Kind:** tech
**Lang:** js
**Roles:** security/authentication-and-identity @1

### dp-accounting
**Short:** Google library that composes differential-privacy budgets with RDP and PLD accountants outside a training loop.
**Kind:** tech
**Lang:** python
**Roles:** security/privacy-and-compliance @1

### dp-transformers
**Short:** Microsoft library wiring DP-SGD and privacy accounting into Hugging Face Transformers fine-tuning.
**Kind:** tech
**Lang:** python
**Roles:** security/privacy-and-compliance @1, model-training/fine-tuning-and-peft @3

### DSR platforms
**Short:** Tooling that orchestrates GDPR-style data-subject deletion and access requests across every store.
**Kind:** concept
**Lang:** *
**Roles:** security/privacy-and-compliance @1

### Dual-LLM pattern
**Short:** Prompt-injection defence splitting work between a quarantined LLM that sees untrusted data and a privileged one.
**Kind:** concept
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1, llm-apps/agent-framework @3

### EasyJailbreak
**Short:** Red-team framework implementing GCG, AutoDAN, PAIR, TAP and GPTFuzzer under one comparable interface.
**Kind:** tech
**Lang:** python
**Roles:** security/ai-safety-and-guardrails @1, ml-lifecycle/evaluation-and-benchmarks @3

### Entra ID
**Short:** Microsoft's cloud identity provider (formerly Azure AD): OIDC/SAML SSO, MFA, conditional access and workload identities.
**Kind:** tech
**Lang:** *
**Roles:** security/authentication-and-identity @1, security/authorization-and-policy @3

### External Secrets Operator
**Short:** Kubernetes operator that syncs secrets from Vault or a cloud secret manager into native K8s Secrets.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, platform-delivery/kubernetes-and-orchestration @2

### Falco
**Short:** CNCF runtime security tool watching kernel syscalls and flagging suspicious container behaviour against rules live.
**Kind:** tech
**Lang:** *
**Roles:** security/supply-chain-and-runtime-security @1, platform-delivery/kubernetes-and-orchestration @3, observability/alerting-and-incident-response @3

### fastapi.security
**Short:** FastAPI's built-in security dependencies: OAuth2PasswordBearer, APIKeyHeader and HTTPBearer schemes.
**Kind:** api
**Lang:** python
**Roles:** security/authentication-and-identity @1, apis-frameworks/web-framework-and-http-client @2

### FindByIndexNameSessionRepository
**Short:** Spring Session interface indexing sessions by principal, powering max-sessions limits and logout-everywhere.
**Kind:** api
**Lang:** java
**Roles:** security/authentication-and-identity @1, caching/distributed-cache @2

### Foolbox
**Short:** Adversarial attack library (FGSM, PGD, C&W, boundary) with a clean PyTorch and TensorFlow API for robustness evals.
**Kind:** tech
**Lang:** python
**Roles:** security/ai-safety-and-guardrails @1, ml-lifecycle/evaluation-and-benchmarks @3

### Garak
**Short:** Open-source LLM vulnerability scanner running ~20 probe families for jailbreaks, injection and data leakage.
**Kind:** tech
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1, ml-lifecycle/evaluation-and-benchmarks @3

### Gatekeeper
**Short:** OPA Gatekeeper - a Kubernetes admission controller enforcing Rego constraint templates before an object is applied.
**Kind:** tech
**Lang:** *
**Roles:** security/authorization-and-policy @1, platform-delivery/kubernetes-and-orchestration @2

### GCP Cloud KMS
**Short:** Google Cloud managed key management: envelope encryption, key rotation and HSM-backed keys.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1

### GCP DLP
**Short:** Google Cloud Sensitive Data Protection: managed APIs that find, classify and redact PII across many languages.
**Kind:** tech
**Lang:** *
**Roles:** security/privacy-and-compliance @1, security/ai-safety-and-guardrails @2

### GCP Secret Manager
**Short:** Google Cloud managed secret store with versioning, IAM-scoped access and rotation.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, platform-delivery/cloud-platform-and-cost @3

### git-secrets
**Short:** Pre-commit and CI scanner that blocks commits containing credential patterns before a secret reaches the repository.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, security/supply-chain-and-runtime-security @2, devtools/static-analysis-and-linting @2

### gitleaks
**Short:** Fast secret scanner for git history, working trees and CI, using rule plus entropy detection.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, security/supply-chain-and-runtime-security @2

### GnuPG
**Short:** OpenPGP CLI implementation for file encryption, detached signatures and keyring management.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, security/supply-chain-and-runtime-security @2

### Google Cloud Model Armor
**Short:** GCP managed prompt/response screening: jailbreak, injection, sensitive-data and URL filters with org policy floors.
**Kind:** tech
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1, security/authorization-and-policy @2, security/privacy-and-compliance @3

### Google DP library
**Short:** Google's open-source differential privacy library: DP SUM/COUNT/MEAN aggregations, including Beam and Spark pipelines.
**Kind:** tech
**Lang:** *
**Roles:** security/privacy-and-compliance @1, data-movement/batch-and-distributed-compute @3

### Google RAPPOR
**Short:** Local differential privacy scheme randomizing client reports before collection; the Chrome telemetry reference design.
**Kind:** tech
**Lang:** *
**Roles:** security/privacy-and-compliance @1

### grpc-spring-boot-starter security
**Short:** Spring Boot gRPC security layer bridging call credentials into SecurityContextHolder for @Secured methods.
**Kind:** tech
**Lang:** java
**Roles:** security/authentication-and-identity @1, security/authorization-and-policy @2, apis-frameworks/rpc-graphql-and-streaming @3

### Grype
**Short:** CVE scanner for container images and filesystems; consumes Syft SBOMs and gates builds on vulnerabilities.
**Kind:** tech
**Lang:** *
**Roles:** security/supply-chain-and-runtime-security @1, platform-delivery/container-and-image @3

### Guardrails AI
**Short:** Code-first output validation for LLMs: Pydantic-style guards with validators installed from Guardrails Hub.
**Kind:** tech
**Lang:** python
**Roles:** security/ai-safety-and-guardrails @1, llm-apps/prompting-context-and-structured-output @2

### gVisor
**Short:** User-space kernel that intercepts container syscalls, isolating untrusted or agent-run workloads without a full VM.
**Kind:** tech
**Lang:** *
**Roles:** security/supply-chain-and-runtime-security @1, platform-delivery/container-and-image @2, llm-apps/agentic-environments @3, runtime-systems/memory-processes-and-os @3

### HarmBench
**Short:** Standardised red-team benchmark of 510 harmful behaviours across text, contextual, copyright and multimodal categories.
**Kind:** dataset
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1, ml-lifecycle/evaluation-and-benchmarks @2

### HashiCorp Vault
**Short:** Central secrets manager with dynamic short-lived credentials, key rotation, PKI issuance and transit encryption.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, security/authentication-and-identity @3, platform-delivery/infrastructure-as-code-and-config @3

### hashlib
**Short:** Python stdlib module exposing SHA-2/SHA-3, BLAKE2 and PBKDF2 hashing primitives.
**Kind:** api
**Lang:** python
**Roles:** security/secrets-and-cryptography @1

### helm-secrets
**Short:** Helm plugin that encrypts values files (via SOPS/age) so secrets can live safely in git.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, platform-delivery/kubernetes-and-orchestration @2

### IAM
**Short:** AWS Identity and Access Management: users, roles, federation and the JSON policies that grant every API action.
**Kind:** tech
**Lang:** *
**Roles:** security/authentication-and-identity @1, security/authorization-and-policy @2

### IAM Identity Center
**Short:** AWS single sign-on service: central workforce identity, permission sets and federated access across accounts.
**Kind:** tech
**Lang:** *
**Roles:** security/authentication-and-identity @1, security/authorization-and-policy @2, platform-delivery/cloud-platform-and-cost @3

### InetAddressFilter
**Short:** Spring Boot 4.1 filter blocking outbound requests to private or link-local addresses, mitigating SSRF.
**Kind:** api
**Lang:** java
**Roles:** security/supply-chain-and-runtime-security @1, apis-frameworks/web-framework-and-http-client @2

### IRSA
**Short:** IAM Roles for Service Accounts: EKS pods assume AWS roles via a projected OIDC token, so no static keys.
**Kind:** concept
**Lang:** *
**Roles:** security/authentication-and-identity @1, platform-delivery/kubernetes-and-orchestration @2, security/secrets-and-cryptography @3

### JailbreakBench
**Short:** Jailbreak robustness benchmark: 100 policy-violating behaviours, a live adversarial-prompt repository and a leaderboard.
**Kind:** dataset
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1, ml-lifecycle/evaluation-and-benchmarks @2

### jarsigner
**Short:** JDK CLI that signs JAR files with a keystore key and verifies existing signatures and certificate chains.
**Kind:** tech
**Lang:** java
**Roles:** security/supply-chain-and-runtime-security @1, security/secrets-and-cryptography @2

### Java javax.crypto
**Short:** JDK cryptography package: AES ciphers, HMAC, key generation and key derivation through the JCA provider framework.
**Kind:** api
**Lang:** java
**Roles:** security/secrets-and-cryptography @1

### Java KMS SDKs
**Short:** Java SDKs for cloud key management services, used to keep the KEK in an HSM and do envelope encryption.
**Kind:** tech
**Lang:** java
**Roles:** security/secrets-and-cryptography @1, platform-delivery/cloud-platform-and-cost @3

### java-jwt
**Short:** Auth0's Java library for creating and verifying JSON Web Tokens with HMAC and RSA/EC signing algorithms.
**Kind:** tech
**Lang:** java
**Roles:** security/authentication-and-identity @1, security/secrets-and-cryptography @3

### java.io.ObjectInputFilter
**Short:** JDK hook applying an allowlist to Java deserialization so untrusted streams cannot instantiate gadget classes.
**Kind:** api
**Lang:** java
**Roles:** security/supply-chain-and-runtime-security @1, runtime-systems/io-networking-and-syscalls @2

### javax.crypto.Cipher
**Short:** JCA facade for encryption and decryption: one API over provider, algorithm, mode and padding chosen by transform string.
**Kind:** api
**Lang:** java
**Roles:** security/secrets-and-cryptography @1, apis-frameworks/design-patterns-and-principles @3

### javax.net.ssl.SSLContext
**Short:** Configured TLS factory producing matched socket factories and SSLEngines so every connection shares one trust setup.
**Kind:** api
**Lang:** java
**Roles:** security/secrets-and-cryptography @1, runtime-systems/io-networking-and-syscalls @2, apis-frameworks/design-patterns-and-principles @3

### JJWT
**Short:** Java JWT library with a fluent builder and parser for signing, verifying and extracting token claims.
**Kind:** tech
**Lang:** java
**Roles:** security/authentication-and-identity @1, security/secrets-and-cryptography @2

### JWT
**Short:** Signed, self-contained token format carrying claims; the usual bearer credential for stateless API auth.
**Kind:** spec
**Lang:** *
**Roles:** security/authentication-and-identity @1, apis-frameworks/data-formats-and-api-contracts @3

### jwt.io
**Short:** Web tool for pasting a JWT to decode its header and payload and check the signature while debugging auth.
**Kind:** tech
**Lang:** *
**Roles:** security/authentication-and-identity @1

### Key Vault
**Short:** Azure Key Vault: managed store for secrets, certificates and keys with HSM backing and RBAC access.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, platform-delivery/cloud-platform-and-cost @3

### Keycloak
**Short:** Self-hosted identity provider and OAuth2/OIDC/SAML authorization server with user federation and RBAC.
**Kind:** tech
**Lang:** *
**Roles:** security/authentication-and-identity @1, security/authorization-and-policy @2

### keytool
**Short:** JDK CLI for managing KeyStores: generate keypairs, create CSRs, import certificates and inspect trust stores.
**Kind:** tech
**Lang:** java
**Roles:** security/secrets-and-cryptography @1

### kube-bench
**Short:** Go tool auditing a Kubernetes cluster against the CIS Benchmark and reporting failing hardening controls.
**Kind:** tech
**Lang:** *
**Roles:** security/supply-chain-and-runtime-security @1, platform-delivery/kubernetes-and-orchestration @2, security/privacy-and-compliance @3

### Kyverno
**Short:** Kubernetes-native policy engine: YAML admission rules that validate, mutate, generate and verify image signatures.
**Kind:** tech
**Lang:** *
**Roles:** security/authorization-and-policy @1, platform-delivery/kubernetes-and-orchestration @2, security/supply-chain-and-runtime-security @3

### Lakera Guard
**Short:** Hosted API detecting prompt injection, jailbreaks, PII and unsafe content in LLM inputs and outputs.
**Kind:** tech
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1

### Let's Encrypt
**Short:** Free public certificate authority issuing short-lived TLS certificates automatically over the ACME protocol.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, security/authentication-and-identity @3

### libsodium
**Short:** Modern, hard-to-misuse crypto library (Curve25519, XSalsa20, Poly1305) with bindings in nearly every language.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1

### Llama Guard
**Short:** Meta's open safety classifier for input/output moderation against the MLCommons hazard taxonomy.
**Kind:** model
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1, model-training/alignment-and-rl @3

### Llama Guard 4 12B
**Short:** Meta's open multimodal safety classifier that labels prompts and responses against a hazard taxonomy in eight languages.
**Kind:** model
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1

### Llama Prompt Guard 2
**Short:** Meta classifier (86M/22M) that flags prompt injection and jailbreak attempts, ~97.5% recall at 1% FPR.
**Kind:** model
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1

### LLM Guard
**Short:** Open-source scanner suite for LLM input and output: prompt injection, toxicity, PII, secrets and topic bans.
**Kind:** tech
**Lang:** python
**Roles:** security/ai-safety-and-guardrails @1, security/privacy-and-compliance @3

### llm-attacks
**Short:** Reference codebase for greedy coordinate gradient jailbreaks; the basis of most white-box attack research.
**Kind:** tech
**Lang:** python
**Roles:** security/ai-safety-and-guardrails @1

### Microsoft Entra ID
**Short:** Microsoft's cloud identity provider: OIDC/SAML sign-in, conditional access and workload identities.
**Kind:** tech
**Lang:** *
**Roles:** security/authentication-and-identity @1

### Microsoft Presidio
**Short:** Open-source PII detection and anonymization toolkit: recognizers plus redact/mask/encrypt operators.
**Kind:** tech
**Lang:** python
**Roles:** security/privacy-and-compliance @1, security/ai-safety-and-guardrails @2, applied-ml/nlp-and-text @3

### Microsoft SEAL
**Short:** Microsoft's homomorphic encryption library (BFV and CKKS), letting you compute on ciphertext without decrypting.
**Kind:** tech
**Lang:** cpp
**Roles:** security/privacy-and-compliance @1, security/secrets-and-cryptography @2

### mkcert
**Short:** CLI that installs a local CA and issues trusted development TLS certificates so localhost can run real HTTPS/HTTP2.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, devtools/version-control-and-workbench @3

### MLCommons AILuminate
**Short:** Cross-language hazard benchmark: 12 categories with tens of thousands of natively authored prompts per language.
**Kind:** dataset
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1, ml-lifecycle/evaluation-and-benchmarks @2

### ModelScan
**Short:** Scanner that inspects serialized model files for unsafe deserialization and code-execution payloads.
**Kind:** tech
**Lang:** python
**Roles:** security/supply-chain-and-runtime-security @1, security/ai-safety-and-guardrails @2

### ModSecurity
**Short:** Open-source web application firewall module for nginx/Apache, usually run with the OWASP Core Rule Set.
**Kind:** tech
**Lang:** *
**Roles:** security/supply-chain-and-runtime-security @1, traffic-edge/proxy-and-load-balancer @2

### Mozilla Observatory
**Short:** Online scanner grading a site's HTTP security headers, TLS configuration and cookie flags.
**Kind:** tech
**Lang:** *
**Roles:** security/supply-chain-and-runtime-security @1, apis-frameworks/web-framework-and-http-client @3

### MySQL Audit Plugin
**Short:** MySQL Enterprise plugin writing a tamper-evident audit log of connections and statements for compliance review.
**Kind:** tech
**Lang:** *
**Roles:** security/privacy-and-compliance @1, observability/logging @3, data-stores/relational @3

### NaCl
**Short:** Networking and Cryptography library - the original opinionated C primitives (Curve25519, XSalsa20) behind libsodium.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1

### nanoGCG
**Short:** Compact reimplementation of the GCG adversarial-suffix attack, used to automate red-teaming of aligned models.
**Kind:** tech
**Lang:** python
**Roles:** security/ai-safety-and-guardrails @1

### NeMo Guardrails
**Short:** NVIDIA's programmable rails toolkit: Colang-defined topic, safety and security guardrails around an LLM.
**Kind:** tech
**Lang:** python
**Roles:** security/ai-safety-and-guardrails @1, llm-apps/agent-framework @3

### Neural Cleanse
**Short:** Backdoor-detection method that reverse-engineers a minimal trigger per class to spot a trojaned classifier.
**Kind:** concept
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1, applied-ml/interpretability-fairness-and-causal @3

### Nightfall, Skyflow, Very Good Security
**Short:** Commercial PII tokenization vaults that replace sensitive values with reversible tokens held outside your systems.
**Kind:** tech
**Lang:** *
**Roles:** security/privacy-and-compliance @1, security/secrets-and-cryptography @3

### Nimbus JOSE
**Short:** Java JOSE/JWT library for signing, encrypting and validating tokens; used by Spring Security resource servers.
**Kind:** tech
**Lang:** java
**Roles:** security/authentication-and-identity @1, security/secrets-and-cryptography @2

### Nimbus JOSE+JWT
**Short:** Java JOSE library for JWT signing/verification and JWKS fetching; the default under Spring Security OAuth2.
**Kind:** tech
**Lang:** java
**Roles:** security/authentication-and-identity @1, security/secrets-and-cryptography @2

### nimbus-jose-jwt
**Short:** Java JOSE library for signing, encrypting and verifying JWTs; used internally by Spring Security.
**Kind:** tech
**Lang:** java
**Roles:** security/authentication-and-identity @1, security/secrets-and-cryptography @2

### NIST AI RMF
**Short:** US government AI Risk Management Framework: a voluntary structure for governing, mapping and measuring AI risk.
**Kind:** spec
**Lang:** *
**Roles:** security/privacy-and-compliance @1, security/ai-safety-and-guardrails @2

### OAuth 2.1
**Short:** Consolidated OAuth framework: PKCE required, implicit and password grants dropped; the auth basis for remote MCP.
**Kind:** spec
**Lang:** *
**Roles:** security/authentication-and-identity @1, security/authorization-and-policy @2, llm-apps/tool-use-and-mcp @3

### OIDC
**Short:** OpenID Connect: an identity layer over OAuth 2 issuing ID tokens; also used for keyless CI-to-cloud auth.
**Kind:** spec
**Lang:** *
**Roles:** security/authentication-and-identity @1, platform-delivery/ci-cd-and-release @3, security/secrets-and-cryptography @3

### Okta
**Short:** Managed identity provider for workforce and customer SSO: OIDC, SAML, MFA and a hosted OAuth2 authorization server.
**Kind:** tech
**Lang:** *
**Roles:** security/authentication-and-identity @1, security/authorization-and-policy @3

### OPA
**Short:** Open Policy Agent: general-purpose ABAC policy engine evaluating Rego rules over JSON input.
**Kind:** tech
**Lang:** *
**Roles:** security/authorization-and-policy @1, platform-delivery/kubernetes-and-orchestration @3, platform-delivery/infrastructure-as-code-and-config @3

### OPA Gatekeeper
**Short:** Kubernetes admission controller running OPA Rego as ConstraintTemplates and Constraints, with audit of existing objects.
**Kind:** tech
**Lang:** *
**Roles:** security/authorization-and-policy @1, platform-delivery/kubernetes-and-orchestration @2

### Opacus
**Short:** PyTorch library for differentially private training: DP-SGD via PrivacyEngine with a built-in RDP privacy accountant.
**Kind:** tech
**Lang:** python
**Roles:** security/privacy-and-compliance @1, model-training/deep-learning-framework @3

### Open Policy Agent
**Short:** General-purpose policy engine evaluating Rego rules for ABAC authorization, Kubernetes admission control and IaC checks.
**Kind:** tech
**Lang:** *
**Roles:** security/authorization-and-policy @1, platform-delivery/kubernetes-and-orchestration @3, platform-delivery/infrastructure-as-code-and-config @3

### OpenAI Moderation API
**Short:** Free hosted content classifier (omni-moderation-latest) with per-category scores; recalibrate after upgrades.
**Kind:** tech
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1

### OpenFHE
**Short:** Open-source fully homomorphic encryption library (BFV, BGV, CKKS) for computing directly on encrypted data.
**Kind:** tech
**Lang:** cpp
**Roles:** security/privacy-and-compliance @1, security/secrets-and-cryptography @2

### OpenMined PySyft
**Short:** Privacy-preserving ML toolkit covering federated learning, differential privacy, secure MPC and homomorphic encryption.
**Kind:** tech
**Lang:** python
**Roles:** security/privacy-and-compliance @1, model-training/distributed-training @3

### OpenSSL
**Short:** TLS and crypto toolkit plus CLI: AES, RSA, ECC, X.509 certificates and keypair generation for servers and clients.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, runtime-systems/io-networking-and-syscalls @3

### OTel masking hooks
**Short:** OpenTelemetry span processors and attribute hooks that scrub PII and prompt content before traces are exported.
**Kind:** concept
**Lang:** *
**Roles:** security/privacy-and-compliance @1, observability/tracing-apm-and-llm-observability @2

### OWASP Dependency-Check
**Short:** Software composition analysis scanner that maps direct and transitive dependencies to known CVEs in a build or CI step.
**Kind:** tech
**Lang:** *
**Roles:** security/supply-chain-and-runtime-security @1, devtools/build-and-dependency-management @3, platform-delivery/ci-cd-and-release @3

### OWASP SQLMap
**Short:** Penetration-testing CLI that automatically finds and exploits SQL injection to prove a parameter is unsafe.
**Kind:** tech
**Lang:** *
**Roles:** security/supply-chain-and-runtime-security @1, data-stores/relational @3

### OWASP ZAP
**Short:** OWASP's DAST scanner: crawls and attacks a running application to find injection, XSS and auth flaws.
**Kind:** tech
**Lang:** *
**Roles:** security/supply-chain-and-runtime-security @1, devtools/testing-and-mocking @3

### PAIR
**Short:** Prompt Automatic Iterative Refinement: an attacker LLM iteratively rewrites a prompt to jailbreak a target model.
**Kind:** concept
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1

### Palo Alto Prisma AIRS
**Short:** AI security suite combining model supply-chain scanning with runtime prompt-injection protection.
**Kind:** tech
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1, security/supply-chain-and-runtime-security @2

### Perspective API
**Short:** Google Jigsaw's hosted toxicity scoring API returning per-attribute scores (toxicity, insult, threat) for text.
**Kind:** tech
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1

### pgAudit
**Short:** PostgreSQL extension emitting detailed session and object audit log entries for compliance evidence.
**Kind:** tech
**Lang:** *
**Roles:** security/privacy-and-compliance @1, observability/logging @2, data-stores/relational @3

### picklescan
**Short:** Scanner that inspects pickle and PyTorch checkpoint files for known malicious opcodes before you load them.
**Kind:** tech
**Lang:** python
**Roles:** security/supply-chain-and-runtime-security @1

### pip-audit
**Short:** Scans installed Python packages against OSV and the PyPI Advisory DB; run in CI to block deploys on high CVEs.
**Kind:** tech
**Lang:** python
**Roles:** security/supply-chain-and-runtime-security @1, devtools/build-and-dependency-management @3

### PipelineDP
**Short:** Library adding differentially private SUM/COUNT/MEAN aggregations to Beam and Spark pipelines.
**Kind:** tech
**Lang:** python
**Roles:** security/privacy-and-compliance @1, data-movement/batch-and-distributed-compute @2

### PKCE
**Short:** OAuth extension binding an authorization code to a per-request verifier, blocking code interception on public clients.
**Kind:** spec
**Lang:** *
**Roles:** security/authentication-and-identity @1

### Pod Security Admission
**Short:** Kubernetes built-in admission controller enforcing Pod Security Standards per namespace in enforce/audit/warn.
**Kind:** tech
**Lang:** *
**Roles:** security/authorization-and-policy @1, platform-delivery/kubernetes-and-orchestration @2, security/supply-chain-and-runtime-security @2

### policy-controller
**Short:** Sigstore Kubernetes admission controller verifying image signatures and attestations before a pod is allowed to run.
**Kind:** tech
**Lang:** *
**Roles:** security/supply-chain-and-runtime-security @1, security/authorization-and-policy @2, platform-delivery/kubernetes-and-orchestration @2

### PromptArmor
**Short:** Commercial multi-layer defense product detecting prompt injection and unsafe agent behaviour at runtime.
**Kind:** tech
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1

### Prowler
**Short:** Open-source cloud security posture scanner auditing AWS/Azure/GCP config against CIS and compliance benchmarks.
**Kind:** tech
**Lang:** *
**Roles:** security/supply-chain-and-runtime-security @1, security/privacy-and-compliance @2, platform-delivery/cloud-platform-and-cost @3

### pwdlib[argon2,bcrypt]
**Short:** Maintained Python password-hashing front end; PasswordHash.recommended() is argon2id and rehashes legacy hashes.
**Kind:** tech
**Lang:** python
**Roles:** security/secrets-and-cryptography @1, security/authentication-and-identity @2

### pwdlib[argon2]
**Short:** Modern Python password-hashing library defaulting to argon2id, the current recommendation for stored passwords.
**Kind:** tech
**Lang:** python
**Roles:** security/secrets-and-cryptography @1, security/authentication-and-identity @2

### PyJWT
**Short:** Python JWT encode/decode library (HS256/RS256) shipping PyJWKClient, which fetches and caches an OIDC JWKS.
**Kind:** tech
**Lang:** python
**Roles:** security/authentication-and-identity @1, security/secrets-and-cryptography @3

### PyRIT
**Short:** Microsoft's Python Risk Identification Toolkit: orchestrates automated multi-turn red-team attacks on generative AI.
**Kind:** tech
**Lang:** python
**Roles:** security/ai-safety-and-guardrails @1, ml-lifecycle/evaluation-and-benchmarks @3

### Python hashlib
**Short:** Python stdlib hashing module: SHA-2/SHA-3, BLAKE2, and PBKDF2-HMAC key derivation.
**Kind:** api
**Lang:** python
**Roles:** security/secrets-and-cryptography @1

### Python hmac
**Short:** Python stdlib module for HMAC signatures, with constant-time comparison via compare_digest.
**Kind:** api
**Lang:** python
**Roles:** security/secrets-and-cryptography @1

### Python secrets
**Short:** Python stdlib module for cryptographically secure random tokens and constant-time comparison.
**Kind:** api
**Lang:** python
**Roles:** security/secrets-and-cryptography @1

### python-jose
**Short:** Python JOSE implementation covering JWT signing plus JWE encryption and JWK sets.
**Kind:** tech
**Lang:** python
**Roles:** security/authentication-and-identity @1, security/secrets-and-cryptography @2

### RBAC
**Short:** Role-based access control: permissions attach to roles and roles to identities; the default model in K8s and clouds.
**Kind:** concept
**Lang:** *
**Roles:** security/authorization-and-policy @1, security/authentication-and-identity @3

### Rebuff
**Short:** Prompt-injection detector combining heuristics, an LLM check, a vector store of known attacks and canary tokens.
**Kind:** tech
**Lang:** python
**Roles:** security/ai-safety-and-guardrails @1

### Rego
**Short:** Open Policy Agent's declarative policy language: query-style rules evaluated over JSON input to allow or deny.
**Kind:** spec
**Lang:** *
**Roles:** security/authorization-and-policy @1, platform-delivery/infrastructure-as-code-and-config @3

### safety
**Short:** Python dependency CVE scanner backed by a commercial vulnerability database, complementing pip-audit.
**Kind:** tech
**Lang:** python
**Roles:** security/supply-chain-and-runtime-security @1, devtools/static-analysis-and-linting @2

### scrypt
**Short:** Memory-hard password hashing and key derivation function (RFC 7914), tuned by cost, block and parallelism factors.
**Kind:** spec
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, security/authentication-and-identity @2

### SD-JWT
**Short:** Selective-disclosure JWT: the holder reveals only the claims a verifier needs, keeping the rest hidden.
**Kind:** spec
**Lang:** *
**Roles:** security/authentication-and-identity @1, apis-frameworks/data-formats-and-api-contracts @2

### sdcMicro
**Short:** Statistical disclosure control package applying k-anonymity, l-diversity and t-closeness to tabular data releases.
**Kind:** tech
**Lang:** *
**Roles:** security/privacy-and-compliance @1

### Sealed Secrets
**Short:** Kubernetes controller that decrypts SealedSecret CRs into Secrets, letting encrypted secrets live in git.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, platform-delivery/kubernetes-and-orchestration @2, platform-delivery/ci-cd-and-release @3

### seccomp+namespaces
**Short:** Linux kernel sandboxing primitives: syscall filtering plus namespace isolation, the base layer under containers.
**Kind:** tech
**Lang:** *
**Roles:** security/supply-chain-and-runtime-security @1, runtime-systems/memory-processes-and-os @2, llm-apps/agentic-environments @3

### Secret Manager
**Short:** Google Cloud's managed secret store with versioning, IAM-scoped access and audit logging.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, platform-delivery/cloud-platform-and-cost @3

### Secrets Manager
**Short:** AWS service storing credentials encrypted with KMS, with fine-grained IAM access, versioning and automatic rotation.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, apis-frameworks/dependency-injection-and-config @3, platform-delivery/cloud-platform-and-cost @3

### Secrets Store CSI Driver
**Short:** Kubernetes CSI driver mounting secrets from Vault or a cloud secret manager into a pod as a tmpfs volume.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, platform-delivery/kubernetes-and-orchestration @2

### SecureRandom
**Short:** JDK cryptographically secure PRNG; getInstanceStrong() for key and token material.
**Kind:** api
**Lang:** java
**Roles:** security/secrets-and-cryptography @1

### Sigstore
**Short:** Keyless signing and transparency-log verification for container images, artifacts and build attestations.
**Kind:** tech
**Lang:** *
**Roles:** security/supply-chain-and-runtime-security @1, security/secrets-and-cryptography @3

### Sigstore/cosign
**Short:** Keyless signing and transparency-log verification for container images, model artifacts and attestations.
**Kind:** tech
**Lang:** *
**Roles:** security/supply-chain-and-runtime-security @1, security/secrets-and-cryptography @2

### Skyfire
**Short:** Agent identity (KYA) and wallet payment network letting autonomous agents pay other agents or metered APIs per call.
**Kind:** tech
**Lang:** *
**Roles:** security/authentication-and-identity @1, llm-apps/tool-use-and-mcp @3

### SmoothLLM
**Short:** Randomized-smoothing jailbreak defence: perturb a prompt many times and aggregate to break adversarial suffixes.
**Kind:** concept
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1

### Snyk
**Short:** Commercial dependency, container and secret scanner that reports CVEs in CI with concrete upgrade fixes.
**Kind:** tech
**Lang:** *
**Roles:** security/supply-chain-and-runtime-security @1, security/secrets-and-cryptography @3, platform-delivery/ci-cd-and-release @3

### SOPS
**Short:** Encrypts values inside YAML/JSON/env files with KMS/age/PGP so secrets can live safely in Git for GitOps.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, platform-delivery/infrastructure-as-code-and-config @3, platform-delivery/ci-cd-and-release @3

### Spring Authorization Server
**Short:** Spring's own OAuth2/OIDC authorization server for issuing and introspecting tokens inside your stack.
**Kind:** tech
**Lang:** java
**Roles:** security/authentication-and-identity @1, security/secrets-and-cryptography @3

### Spring Security 6.x
**Short:** Spring's security framework: filter chain, authentication providers, method security, SecurityContext propagation.
**Kind:** tech
**Lang:** java
**Roles:** security/authentication-and-identity @1, security/authorization-and-policy @2, apis-frameworks/aop-middleware-and-scheduling @3

### Spring Security 7.x
**Short:** Spring's security framework: filter chain, authentication, method security, CSRF and security headers.
**Kind:** tech
**Lang:** java
**Roles:** security/authentication-and-identity @1, security/authorization-and-policy @1, apis-frameworks/aop-middleware-and-scheduling @3

### Spring Security ACL
**Short:** Spring module storing per-domain-object permissions in ACL tables, for row-specific authorization decisions.
**Kind:** tech
**Lang:** java
**Roles:** security/authorization-and-policy @1

### Spring Security OAuth2
**Short:** Spring's OAuth2/OIDC support: authorization-code login, resource-server token validation and client credentials.
**Kind:** tech
**Lang:** java
**Roles:** security/authentication-and-identity @1, security/authorization-and-policy @2

### Spring Security OAuth2 Resource Server
**Short:** Spring Security module validating Bearer JWTs or opaque tokens and mapping claims to authorities.
**Kind:** tech
**Lang:** java
**Roles:** security/authentication-and-identity @1, security/authorization-and-policy @2

### Spring Security Reactive
**Short:** WebFlux security stack: SecurityWebFilterChain rules and ReactiveSecurityContextHolder for non-blocking auth.
**Kind:** tech
**Lang:** java
**Roles:** security/authentication-and-identity @1, security/authorization-and-policy @2, apis-frameworks/aop-middleware-and-scheduling @3

### Spring Security SecurityFilterChain
**Short:** Spring Security's ordered servlet filter chain, configured with a lambda DSL and scoped by securityMatcher.
**Kind:** api
**Lang:** java
**Roles:** security/authentication-and-identity @1, security/authorization-and-policy @2, apis-frameworks/aop-middleware-and-scheduling @2

### spring-boot-starter-oauth2-resource-server
**Short:** Boot starter auto-configuring JWT or opaque-token validation so an API accepts OAuth2 bearer tokens.
**Kind:** tech
**Lang:** java
**Roles:** security/authentication-and-identity @1, apis-frameworks/dependency-injection-and-config @3

### spring-boot-starter-security
**Short:** Starter that auto-configures Spring Security's filter chain with HTTP Basic and a generated dev password.
**Kind:** tech
**Lang:** java
**Roles:** security/authentication-and-identity @1, security/authorization-and-policy @2, apis-frameworks/dependency-injection-and-config @3

### spring-cloud-vault-config
**Short:** Spring Cloud property source backed by HashiCorp Vault: KV v1/v2 secrets and dynamic credentials injected at startup.
**Kind:** tech
**Lang:** java
**Roles:** security/secrets-and-cryptography @1, apis-frameworks/dependency-injection-and-config @2

### spring-security-oauth2-client
**Short:** Spring Security module implementing the OAuth2/OIDC authorization-code flow for social login and SSO.
**Kind:** tech
**Lang:** java
**Roles:** security/authentication-and-identity @1

### spring-security-oauth2-resource-server
**Short:** Spring Security module validating bearer JWTs or opaque tokens on API requests and mapping them to authorities.
**Kind:** tech
**Lang:** java
**Roles:** security/authentication-and-identity @1, security/authorization-and-policy @2

### spring-security-rsocket
**Short:** Spring Security module securing RSocket routes with @EnableRSocketSecurity, JWT auth and payload authorization.
**Kind:** tech
**Lang:** java
**Roles:** security/authentication-and-identity @1, security/authorization-and-policy @2, apis-frameworks/rpc-graphql-and-streaming @3

### ssl
**Short:** Python stdlib module that wraps sockets in TLS and controls certificate verification, hostname checks and cipher policy.
**Kind:** api
**Lang:** python
**Roles:** security/secrets-and-cryptography @1, runtime-systems/io-networking-and-syscalls @2

### ssllabs.com
**Short:** Qualys SSL Labs online scanner that grades a host's TLS configuration: protocols, ciphers, chain and known weaknesses.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, security/supply-chain-and-runtime-security @3

### starlette-csrf
**Short:** ASGI middleware adding double-submit cookie CSRF protection to Starlette and FastAPI applications.
**Kind:** tech
**Lang:** python
**Roles:** security/authentication-and-identity @1, apis-frameworks/aop-middleware-and-scheduling @2

### SunJCE, SunEC, SunRsaSign
**Short:** The default JDK JCA security providers supplying AES, RSA, elliptic-curve and PBKDF2 implementations.
**Kind:** api
**Lang:** java
**Roles:** security/secrets-and-cryptography @1

### syft
**Short:** SBOM generator that inventories packages in container images and filesystems as CycloneDX or SPDX.
**Kind:** tech
**Lang:** *
**Roles:** security/supply-chain-and-runtime-security @1, platform-delivery/container-and-image @2

### TenSEAL
**Short:** Python library wrapping Microsoft SEAL to run BFV/CKKS homomorphic encryption over tensors for encrypted inference.
**Kind:** tech
**Lang:** python
**Roles:** security/privacy-and-compliance @1, security/secrets-and-cryptography @2

### TensorFlow Federated
**Short:** Google's federated learning framework for simulating and running training that never centralizes raw data.
**Kind:** tech
**Lang:** python
**Roles:** security/privacy-and-compliance @1, model-training/distributed-training @2, model-training/deep-learning-framework @3

### TensorFlow Privacy
**Short:** TensorFlow library for differentially private training: DP-SGD optimizers plus a moments/RDP privacy accountant.
**Kind:** tech
**Lang:** python
**Roles:** security/privacy-and-compliance @1

### Tetragon
**Short:** eBPF runtime security for Kubernetes: observes and enforces on syscalls, process exec and network activity.
**Kind:** tech
**Lang:** *
**Roles:** security/supply-chain-and-runtime-security @1, platform-delivery/kubernetes-and-orchestration @3

### TF Privacy
**Short:** TensorFlow library for differentially private training via gradient clipping, noise and an epsilon accountant.
**Kind:** tech
**Lang:** python
**Roles:** security/privacy-and-compliance @1, model-training/deep-learning-framework @3

### Tink
**Short:** Google's misuse-resistant crypto library over JCA: safe AEAD, signing and key rotation defaults.
**Kind:** tech
**Lang:** java
**Roles:** security/secrets-and-cryptography @1

### ToxicChat
**Short:** Corpus of 10,166 real user-chatbot queries labelled for toxicity and jailbreaks; the chatbot-domain reference set.
**Kind:** dataset
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1, ml-lifecycle/evaluation-and-benchmarks @2

### Trivy
**Short:** Single-binary scanner for CVEs, dependencies, IaC and Kubernetes clusters, usually run as a CI gate.
**Kind:** tech
**Lang:** *
**Roles:** security/supply-chain-and-runtime-security @1, platform-delivery/container-and-image @3, platform-delivery/ci-cd-and-release @3

### trufflehog
**Short:** Scanner finding and live-verifying leaked credentials in git history, filesystems and CI; usual pre-commit gate.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, security/supply-chain-and-runtime-security @2, platform-delivery/ci-cd-and-release @3

### Trusted Agent Protocol
**Short:** Visa's agent-payment scheme: signed agent identity in HTTP headers plus agent tokens with programmable controls.
**Kind:** spec
**Lang:** *
**Roles:** security/authentication-and-identity @1, llm-apps/tool-use-and-mcp @2

### Vault
**Short:** HashiCorp's secrets store for credentials, signing keys and PKI, with dynamic leases and audited access.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1

### Vault Agent Injector
**Short:** Kubernetes mutating webhook that adds a Vault Agent init/sidecar to pods so secrets land as files, not env vars.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, platform-delivery/kubernetes-and-orchestration @2

### Vec2Text
**Short:** Embedding-inversion toolkit that reconstructs source text from vectors, used to red-team vector stores.
**Kind:** tech
**Lang:** python
**Roles:** security/ai-safety-and-guardrails @1, security/privacy-and-compliance @2, data-stores/vector-store @3

### Visa Intelligent Commerce
**Short:** Visa's agent-payment program: agent-scoped payment tokens with programmable controls and signed agent identity.
**Kind:** tech
**Lang:** *
**Roles:** security/authentication-and-identity @1, llm-apps/agent-framework @3

### WAF
**Short:** Web application firewall: rule-based filtering of HTTP requests at the edge, plus per-endpoint rate limiting.
**Kind:** concept
**Lang:** *
**Roles:** security/supply-chain-and-runtime-security @1, traffic-edge/rate-limiting-and-resilience @2

### WildGuard
**Short:** Open safety moderation model and benchmark covering prompt harm, response harm and refusal, calibration-aware.
**Kind:** model
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1, ml-lifecycle/evaluation-and-benchmarks @2

### Workload Identity
**Short:** Maps a Kubernetes service account to a cloud IAM identity so pods get short-lived credentials, no static keys.
**Kind:** tech
**Lang:** *
**Roles:** security/authentication-and-identity @1, platform-delivery/kubernetes-and-orchestration @2, security/secrets-and-cryptography @3
