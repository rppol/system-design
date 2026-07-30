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

A public certificate is free, and ACM handles the DNS or email validation, holds the private key, and renews automatically before expiry, so certificate expiry stops being the recurring outage it is when someone has to remember to renew. The natural fit is a certificate attached to an integrated service such as an ALB, NLB, CloudFront distribution or API Gateway, where rotation is invisible to you. Two rules bite in practice: a certificate used by CloudFront must be issued in `us-east-1` no matter where the rest of the stack lives, and DNS validation keeps renewing only while the validation CNAME stays in the zone, so deleting it silently breaks the next renewal. ACM Private CA is the separately billed offering for issuing internal certificates to your own workloads.

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

Argon2id is the memory-hard password hash OWASP recommends, because raising the memory cost makes GPU and ASIC cracking expensive in a way that iteration count alone never did for bcrypt or PBKDF2. This package binds the reference C implementation and exposes a `PasswordHasher` whose `hash` and `verify` embed the parameters in the encoded string, so raising cost later still verifies old hashes and `check_needs_rehash` tells you to upgrade one at the next successful login. Tune `memory_cost`, `time_cost` and `parallelism` to what your login path can afford, and remember the memory cost is charged per concurrent verification, so a login storm becomes a capacity question. Higher-level wrappers such as `pwdlib` use it underneath if you would rather not touch the primitives.

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

It hosts the parts of authentication you would otherwise build and then have to keep secure: login and password storage, MFA, social and enterprise SAML/OIDC federation, and the token endpoints. Your services stay resource servers — they fetch the tenant's JWKS and validate a JWT's signature, issuer, audience and expiry, and never see a password — while Actions run JavaScript inside the login pipeline to attach roles or tenant ids as custom claims.

Reach for it when identity is table stakes rather than your product, especially with enterprise SSO on the roadmap. The tradeoffs are per-active-user pricing that grows with your success and a user directory living outside your database, which makes an eventual migration a real project.

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

A guardrail is a policy object combining content filters with per-category strength, denied topics written in natural language, word and PII filters, and contextual grounding checks; it evaluates the input prompt and the output completion as separate passes. You edit a DRAFT and publish immutable numbered versions, so pinning a version in production makes a policy change a deployable artifact rather than a live config edit. Because `ApplyGuardrail` runs a policy without invoking a model, the same policy can screen retrieved documents before they reach a prompt, or guard a model that is not hosted on Bedrock at all. Reach for it when you are already on AWS and want filtering you do not maintain -- it bills per request, and its topic definitions are coarser than a classifier trained on your own labelled data.

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

It is two services that are easy to confuse. A user pool is the directory and OpenID Connect provider: sign-up and sign-in, password policies, multi-factor authentication, a hosted sign-in page, federation to social and SAML providers, and issued identity, access and refresh tokens. An identity pool does something different, exchanging a token from a user pool or an external provider for temporary IAM credentials so a mobile or browser client can call AWS services directly under a scoped role.

Reach for it when the application lives in AWS and you want managed authentication that the surrounding services already understand, since API Gateway and the application load balancer can validate its tokens without code. The reasons teams move to Auth0, Okta or Keycloak instead are its limited customization of flows and screens, and the migration cost once every user's credentials live inside it.

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

Key material never leaves the service's HSMs. For small payloads you call `Encrypt` and `Decrypt` directly, but the pattern that matters is `GenerateDataKey`: KMS returns a fresh data key twice, once in plaintext and once encrypted under your KMS key. You encrypt the data locally with the plaintext copy, discard it from memory, and store the encrypted copy next to the ciphertext — which is why encrypting a terabyte does not mean a terabyte of traffic to KMS, and why the blast radius of a single leaked data key is one object.

Access is the intersection of IAM policy and the key policy on the key itself, every operation is recorded in CloudTrail, and most AWS services integrate directly, which together make "encrypted at rest with a customer-managed key" a configuration choice with an audit trail attached. Watch two things: per-key request quotas and cost on a hot decrypt path, which is an argument for caching data keys; and rotation semantics, where new material encrypts new data while older ciphertext stays readable under the previous material.

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

A secret is a versioned value with staging labels (`AWSCURRENT`, `AWSPENDING`, `AWSPREVIOUS`), and rotation is a Lambda function following a four-step contract: create a new credential, set it on the target, test it, then move the label. That sequence is what allows a credential to be rotated without a window in which the old one is already dead and the new one is not yet live. AWS supplies ready-made rotation functions for its own database services, which is where this earns its price over cheaper stores. Access is IAM-controlled and values are KMS-encrypted, and because billing is per secret per month plus per API call, the client-side caching libraries matter: fetching the secret on every request is the expensive mistake. Without managed rotation, SSM Parameter Store holds `SecureString` values for far less.

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

It returns per-category severity scores for text and images across hate, sexual, violence and self-harm rather than a single allow or block verdict, so you set the thresholds and can be stricter about one category than another. Two additional detectors matter for LLM applications: prompt shields flag both direct jailbreak attempts in user input and instructions smuggled inside retrieved documents, and groundedness detection checks whether a response is actually supported by the sources you provide.

Reach for it when you need a moderation layer without training or hosting a classifier, and when a documented service level and regional data handling are part of the requirement. Log the raw scores rather than only the decision, because block rates vary sharply by language and category, and tuning thresholds against your own traffic is the only way to find the point where you are catching abuse without refusing legitimate requests.

### Azure Key Vault
**Short:** Azure managed store for secrets, certificates and keys, with HSM-backed key management and rotation.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, platform-delivery/cloud-platform-and-cost @3

One service holds three object types with different semantics: secrets are arbitrary strings you read back, certificates bundle a key with issuance and automatic renewal, and keys never leave the vault at all — you send data or a digest and the vault performs the sign, verify, wrap or unwrap operation, HSM-backed in the Premium tier or in Managed HSM.

Applications authenticate with a managed identity, so nothing but the vault URL is in configuration and no bootstrap credential ships with the app; authorization is Azure RBAC or the older per-vault access policies. Reach for it as the default secret store for anything running on Azure. Two operational details cause most incidents: fetching a secret on every request rather than caching it will hit request throttling, and soft-delete with purge protection means a deleted vault or secret name stays reserved during the retention window, which surprises teardown-and-recreate automation.

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

bcrypt is a deliberately slow password hash built on Blowfish's expensive key schedule, with a cost factor that doubles the work each time you raise it, so you can keep pace with faster hardware by changing one number. The salt and the cost are encoded in the output string itself, so verification simply re-derives using whatever parameters are stored with the hash, and old hashes stay verifiable after you raise the cost for new ones.

Reach for it, or for Argon2id which additionally resists GPU and ASIC attack by being memory-hard, for any stored password; a plain SHA-256 is not a password hash no matter how it is salted. Two traps are worth remembering: input is truncated past 72 bytes, so long passphrases can collide unless you pre-hash, and the 100 to 300 milliseconds you tuned for is 100 to 300 milliseconds of your own CPU per login attempt, which makes the login endpoint a denial-of-service surface that needs rate limiting.
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

You install issuers describing where certificates come from, whether an ACME provider such as Let's Encrypt using an HTTP or DNS challenge, an internal certificate authority, or Vault, and then request certificates as ordinary Kubernetes resources. The controller performs the challenge, writes the key and certificate into a Secret that your Ingress or workload mounts, and renews well before expiry without anyone remembering to. An annotation on an Ingress makes the whole cycle automatic per hostname.

Reach for it in any cluster that terminates TLS, including internal traffic where a private CA issues short-lived certificates. The trap when setting it up is looping on failed issuance against the production ACME endpoint and hitting its rate limits, which locks you out for a week; develop against the staging endpoint and switch over once issuance succeeds. DNS challenges are the route for wildcard certificates and for hosts not reachable from the internet.

### checkov
**Short:** Static security scanner for Terraform, CloudFormation and Kubernetes manifests with Python-authored policy rules.
**Kind:** tech
**Lang:** *
**Roles:** security/supply-chain-and-runtime-security @1, platform-delivery/infrastructure-as-code-and-config @2, security/authorization-and-policy @2, devtools/static-analysis-and-linting @2

checkov scans infrastructure as code — Terraform source and plan output, CloudFormation, Kubernetes manifests, Helm charts, Dockerfiles, ARM templates — against a large built-in policy set and reports each failure with file, line and a description. The findings are the ones that keep causing incidents: a bucket readable by anyone, an unencrypted volume or snapshot, a security group open to the whole internet, logging or versioning left off.

The value is where it runs: in the pull request, so the misconfiguration is discussed before apply rather than found in a quarterly audit. Custom policies are written in Python or YAML when your organisation's rules go beyond the defaults. When you must accept a finding, suppress that specific check inline with a comment so the exception is visible and reviewable, rather than dropping the rule for the whole repository.
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

It parses a structured file — Kubernetes YAML, a Dockerfile, `terraform show -json` plan output, a CI config — into a document, evaluates Rego rules against it, and exits non-zero when a `deny` rule fires. Policies and their fixtures live beside the code and are unit-testable with `conftest verify`, so a rule is something you can prove rather than something written on a wiki page.

Put it in the pipeline before apply, to block a security group open to the world or a pod spec with no resource limits. It only sees what the file says, so pair it with an admission controller such as Gatekeeper or Kyverno when the same rules must also hold against the live cluster.

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

It signs a container image or any OCI artifact and stores the signature as a companion object in the same registry, so verification needs nothing beyond registry access. `cosign verify` checks it against a public key or, in keyless mode, against a short-lived certificate that Fulcio issued for an OIDC identity such as a specific GitHub Actions workflow, with the event recorded in the Rekor transparency log. Keyless is the mode that matters in practice, because it removes the long-lived signing key that was the weak point of the whole idea. It also attaches attestations, an SBOM or SLSA provenance, as signed statements about the image. Pair it with an admission controller that rejects unsigned or unattested images, because a signature nobody verifies changes nothing.

### cryptography
**Short:** Python's standard crypto library: AES-GCM, RSA, ECDSA, X25519, X.509 and JWT-grade key operations.
**Kind:** tech
**Lang:** python
**Roles:** security/secrets-and-cryptography @1

The package splits deliberately into two layers: a small recipes layer — Fernet for authenticated symmetric encryption — that is hard to misuse, and a hazardous-materials layer exposing primitives such as AES-GCM, ChaCha20-Poly1305, RSA, ECDSA, Ed25519, X25519, HKDF and X.509 handling, where correctness is your responsibility. The heavy lifting is compiled native code rather than Python, and this is the package underneath most Python TLS and JWT tooling, which is why generating an RS256 key pair or publishing a JWKS endpoint lands here.

Stay in the recipes layer unless you know precisely which primitive you need, and never assemble your own encrypt-then-MAC construction when an AEAD mode exists.

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

Configured by a `dependabot.yml` in the repository, it watches your manifests and lockfiles and opens one pull request per outdated dependency, with the changelog and compatibility signals in the body so an upgrade gets reviewed like any other change. Security updates are driven separately by the GitHub advisory database and fire on vulnerable versions including transitive ones your manifest never names — the practice that became standard after Log4Shell, because the vulnerable library is usually one you did not choose.

The failure mode is volume: without grouped updates and a sane schedule a busy repository drowns in PRs and the team stops reading them, which is worse than not running it at all.

### detect-secrets
**Short:** Pre-commit and CI scanner that flags credentials accidentally committed into source code.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, devtools/static-analysis-and-linting @2, security/supply-chain-and-runtime-security @2

It runs plugins over your files — regexes for recognisable credential shapes such as cloud keys, private key headers and tokens, plus entropy checks for anything that merely looks random — but its distinguishing feature is the baseline. You audit the existing findings once, mark them, and commit the baseline file, so later runs report only what is new. That is what makes it adoptable on a repository already carrying years of false positives.

Run it as a pre-commit hook and again in CI, since a local hook can be skipped. And remember it only prevents new leaks: anything it finds that was already pushed must be rotated, not merely deleted.

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

The operator adds two custom resources: a `SecretStore` that says where secrets live and how to authenticate — HashiCorp Vault, AWS Secrets Manager, Google Secret Manager, Azure Key Vault — and an `ExternalSecret` that names which keys to fetch and what the resulting Kubernetes Secret should look like. The controller authenticates using the workload's own identity, fetches the values, and writes an ordinary Secret that pods consume as an environment variable or mounted file with no application changes.

That is what makes GitOps workable with secrets: the repository holds only a reference to a secret's name, never its value, so manifests can be public while the value stays in the vault. Two things to plan for anyway — the fetched value does land in etcd as a normal Secret, which is base64 encoding rather than encryption, so enable encryption at rest and keep RBAC tight; and refreshing a value does not restart the pods holding the old one, so pair it with a reloader or a rollout.
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

Garak is a scanner you point at a model, an endpoint or a whole application. It runs probe families — prompt injection, jailbreak templates, encoding and obfuscation attacks, glitch tokens, training-data replay, malware and toxic-content generation — and scores the responses with detectors, producing a report of which attacks got through and at what rate.

Treat it as a red-team regression suite rather than a runtime guardrail: wire it into CI so that changing a system prompt, a model version or a filter tells you whether an attack that was previously blocked now succeeds. Its coverage is deliberately generic, so it will not know your application's own abuse cases — the payment flow that must never be talked into a refund, the agent tool that must never be reachable from user text — and those need probes you write yourself.
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

A secret is a named resource holding immutable versions: you add a version and move the `latest` alias rather than editing in place, so a bad rotation is a one-line rollback and every access is attributable in audit logs. Access control is plain IAM granted per secret, and workloads on GCP authenticate with their own service-account identity, which removes the bootstrap problem of needing a credential in order to fetch credentials.

Use it for anything that must not sit in a repo or be baked into an image — database passwords, API keys, signing material. It stores and versions secrets but does not itself know how to change a database password; a rotation schedule only triggers your code, so reach for Vault's dynamic credentials when you want the store to mint short-lived ones.

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

gitleaks scans the git history as well as the working tree, which matters because a secret deleted in a later commit is still in the repository and still arrives with every clone. Detection combines a built-in rule set for common providers, extendable with your own patterns, and entropy scoring for unrecognised formats; findings can be allowlisted by fingerprint so the scan stays green without switching the rule off entirely.

Run it over full history in CI and as a fast pre-commit hook locally. A hit in history means the credential is compromised and must be rotated — rewriting history does not un-clone it.

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

It is a model-agnostic screening service called over REST on the way in and again on the way out, so the same policy applies whether the request goes to Gemini, a self-hosted model or a third-party API. A template bundles the filters: prompt injection and jailbreak detection, responsible-AI categories, Sensitive Data Protection for PII, malicious URL checks, and screening of document and image content. What separates it from a library-level guardrail is the organization-level floor, where a security team sets a minimum policy that individual projects inherit and cannot weaken, which is an enforcement primitive application-side filtering cannot provide. Treat it as one layer only, since classifier-based filtering is probabilistic and belongs alongside least-privilege tool design and human review for consequential actions, not instead of them.

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

Grype inventories the packages in an image, a directory or a Syft SBOM handed to it, matches them against a vulnerability database it downloads and caches locally, and reports each hit with its severity and the version that fixes it. Because it can scan an SBOM directly, the standard pipeline generates the SBOM once at build time and rescans it later without rebuilding — which is how you learn about a CVE published after the image shipped.

Gate the build with a fail-on severity, but pair it with an ignore file from the start: base-image findings that have no fix available will otherwise block every deploy and train the team to ignore the scanner.

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

A client authenticates with an identity it already has — a Kubernetes service-account token, a cloud instance identity, an OIDC login — and receives a token bound to a lease and a policy. Secrets engines then do the work: a key-value engine stores static values, but the database engine creates a real, uniquely named PostgreSQL or MySQL user for that application on demand and drops it when the lease expires, the PKI engine issues short-lived certificates the same way, and the transit engine performs encryption and decryption so applications hold ciphertext and never key material.

Dynamic, short-lived credentials are the point: a leak becomes a bounded window rather than an incident, revocation is real rather than a coordination exercise, and every access is audited to a named identity. The price is that Vault moves onto the critical path — it must be highly available or applications cannot start, unseal keys and the root token need a genuine custody process, and every consumer needs a plan for renewing leases and reconnecting when a rotated credential invalidates its existing connection pool.

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

It runs the OAuth2 and OIDC flows so your services never see a password: the browser is redirected to Keycloak, and what comes back to your API is a JWT that a resource server validates against the published JWKS endpoint. Realms isolate tenants, clients define applications, and roles, groups and protocol mappers decide exactly which claims land in a token - which is the piece worth designing, since your authorization logic reads those claims.

User federation to LDAP or Active Directory and brokering to upstream social or enterprise identity providers let it front an existing user store rather than replacing it. Reach for it when you want an identity provider you control instead of a managed one, and go in knowing what that means: a stateful, database-backed service on the critical path of every login, with its own upgrade and high-availability story to own.

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

kube-bench runs the CIS Kubernetes Benchmark checks against a cluster: it reads the flags, configuration files and file permissions of the API server, controller manager, scheduler, etcd and kubelet, and reports each control as pass, fail or warn with the remediation text attached. It is normally deployed as a Job or DaemonSet so it can see the host filesystem and the process arguments it needs.

Use it to get an honest baseline on a self-managed cluster and to re-check after upgrades, which quietly change defaults. On a managed control plane the master-node checks do not apply — you cannot see or set those flags — so run the managed profile and focus on the node and policy controls you actually own. It audits configuration only; it does not scan images, workloads or running behaviour.
### Kyverno
**Short:** Kubernetes-native policy engine: YAML admission rules that validate, mutate, generate and verify image signatures.
**Kind:** tech
**Lang:** *
**Roles:** security/authorization-and-policy @1, platform-delivery/kubernetes-and-orchestration @2, security/supply-chain-and-runtime-security @3

Kyverno is a Kubernetes admission controller whose policies are themselves Kubernetes resources written in YAML, so there is no separate policy language to learn. A rule matches resources and then does one of four things: `validate` rejects a pod that has no resource limits or runs as root, `mutate` injects a sidecar or a default label, `generate` creates companion objects such as a default NetworkPolicy in every new namespace, and `verifyImages` requires a valid signature and refuses unsigned images.

That familiarity is the main reason teams adopt it over an engine with its own language; the tradeoff is less expressive power once the logic gets genuinely complicated, where Rego-based tooling still wins. Roll every new policy out in audit mode first and read the reports, because a validate rule promoted straight to enforce will block deployments of workloads that predate it.
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

An ACME client — certbot, `lego`, Caddy or cert-manager in a cluster — proves you control the name by serving a token over HTTP on port 80 or publishing a DNS TXT record, and receives a certificate minutes later; the same client re-runs on a timer and renews long before expiry. Certificates are domain-validated only, with no organization or extended validation, and wildcards require the DNS challenge because HTTP validation cannot prove control of a whole subtree.

The 90-day lifetime is deliberate: it makes unautomated renewal untenable and it caps how long a compromised key stays useful. Use the staging endpoint while getting the automation right, since the rate limits per registered domain are easy to hit with a loop of failed attempts. The classic outage is not issuance at all — it is a freshly renewed certificate sitting on disk that the web server was never told to reload.

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

The analyzer runs a set of recognizers over text and returns typed spans with confidence scores: regex plus checksum validation for structured identifiers like credit cards and national IDs, an NER model for names, locations, and organizations, and context words nearby that boost or lower a score. The anonymizer then applies an operator per entity type — redact, replace with a placeholder, mask all but the last digits, hash, or reversibly encrypt when you need to restore the original later.

It is built to be extended with your own recognizers and deny lists, and separate packages handle images and structured data. Reach for it to scrub prompts, logs, and training data before they cross a boundary; treat it as one layer of defence rather than a compliance guarantee, because recall on free-form text is never complete.

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

Rails are declared in Colang -- canonical user intents, bot intents and flows -- plus configuration for input, output, retrieval and execution rails, and at runtime each turn is checked before and after the model call so the system can refuse, rewrite, or route the request into a defined flow. It composes with jailbreak detection, topic control, fact checking and PII filters rather than replacing them.

Reach for it when policy has to be explicit, reviewable and testable instead of buried in a system prompt. Every rail is extra work per turn, usually extra model calls, so it costs latency and tokens and needs its own test set -- an over-eager rail refusing legitimate requests is the common failure.

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

It plays the identity provider in an OIDC or SAML flow: your application redirects to Okta, which authenticates the user against its own directory or a federated one, applies MFA and conditional-access policy, and returns tokens your service then validates as a resource server. Centralizing that is what makes joiner-mover-leaver work, because deprovisioning one account cuts access everywhere, which a per-application user table can never do; it covers workforce SSO and customer identity, with Auth0 as the developer-oriented product under the same company. Reach for it when identity is a compliance and lifecycle problem rather than a login form. The dependency is real, since an outage at the IdP is an outage for everything behind it, so cache the signing keys and think carefully about token lifetimes.

### OPA
**Short:** Open Policy Agent: general-purpose ABAC policy engine evaluating Rego rules over JSON input.
**Kind:** tech
**Lang:** *
**Roles:** security/authorization-and-policy @1, platform-delivery/kubernetes-and-orchestration @3, platform-delivery/infrastructure-as-code-and-config @3

OPA is one evaluator with a simple contract: give it a JSON `input` document, plus policies written in Rego and any reference data, and it returns a JSON decision. That decoupling is the value — authorization logic lives outside the service it protects, so it can be reviewed, unit-tested and versioned on its own, and the same engine answers "may this user call this endpoint", gates a Kubernetes admission request through Gatekeeper, checks a Terraform plan before apply, and decides whether an agent may invoke a given tool.

Deployed as a sidecar or linked as a library, it evaluates in-process against a policy bundle it pulls periodically, so a decision costs no network round trip and a policy change rolls out without redeploying services. Two things account for the effort: Rego is a declarative query language whose evaluation model has to be learned rather than guessed at, and you still own the harder half of the problem, which is getting the right context — roles, resource attributes, tenancy — into `input`.

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

Opacus makes a PyTorch training loop differentially private by wrapping the model, optimizer and data loader in a `PrivacyEngine`: it computes per-sample gradients, clips each to a norm bound, adds calibrated Gaussian noise to the batch sum, and tracks the spent budget with an RDP accountant so you can ask for a target epsilon up front. Per-sample gradients are the expensive part — both memory and step time rise — and Poisson sampling replaces the ordinary shuffled loader.

Reach for it when a model trains on data whose individual records must not be memorized or reconstructable, which is the concrete defense against membership-inference and some poisoning attacks. The cost is accuracy: a meaningful epsilon on a small dataset can move metrics enough that the private model is not worth shipping, so measure the drop before committing to it.

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

This is a hosted classifier endpoint: send text, or text and images with the omni model, and it returns a flag and a score per category — harassment, hate, self-harm, sexual content, violence and their sub-categories. It is free to call with an API key, which makes it the cheapest possible first filter to place in front of user input and behind model output.

Two limits shape how you use it. The category set is fixed and safety-oriented, so your product's own policy — competitor mentions, unlicensed advice, personal data leaking into a response — still needs a classifier you build. And any threshold you tune against the raw scores is tied to the current model version, so treat a model upgrade as a reason to re-validate the cutoffs against a labelled set rather than assuming the numbers carry over.
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

Two things share the name: `libssl` and `libcrypto`, the libraries implementing TLS and the primitives underneath most Linux software, and the `openssl` command-line tool, which is the everyday way to generate an RSA or EC keypair, build a CSR, inspect a certificate chain, or open a connection with `s_client` and see exactly what a server presents. The CLI is the fastest route to "is the certificate wrong, or is it the client?", and to producing the keypair a JWT signer needs for RS256. Its argument surface is famously unfriendly and easy to get subtly wrong, so prefer a high-level library for application cryptography and keep `openssl` for inspection, key generation and debugging. LibreSSL and BoringSSL are forks; current OpenSSL is Apache-2.0 licensed.

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

It gathers evidence from the artifacts in a build - jar manifests, POM coordinates, file names, lock files - maps them to CPE identifiers, and reports the CVEs the NVD lists against them, optionally failing the build above a CVSS threshold. It runs as a Maven or Gradle plugin, a CLI, or a CI step, which is why it is the usual free baseline for software composition analysis.

Two things make or break it in practice. It needs a populated local vulnerability database and an NVD API key, so the first run is slow and a stale cache silently reports old news. And CPE matching produces false positives, so a suppression file with a documented reason per entry is part of the job. Remember what the result means: it finds a known-vulnerable version on the classpath, not a vulnerable call path, so triage is still human work.

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

Encoding and decoding are one call each, but the decode call is where the security lives. It verifies the signature and the registered claims, and you must pass the algorithms you accept, the expected audience and the expected issuer explicitly, because a token that merely parses is not a token you may trust. The classic vulnerability in this space is honouring the algorithm named inside the token itself, which lets an attacker downgrade to none or to HMAC using the public key as the secret; passing an explicit algorithm list is what closes it.

For OpenID Connect it ships a JWKS client that fetches a provider's public keys over HTTPS, caches them and selects the right one from the token's key id, so RS256 verification with key rotation needs nothing further. Install it with the cryptography extra for RSA and ECDSA support.

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

It implements the JOSE family - JWS for signed tokens, JWE for encrypted ones, and JWK/JWK sets for key material - behind a small `jwt.encode` and `jwt.decode` surface, with a pluggable cryptography backend.

Decoding is where the security actually lives. Always pass the expected `algorithms` list explicitly and never trust the algorithm named in the token's own header; verify audience and issuer rather than only the signature; and fetch the issuer's JWKS and select the key by `kid` instead of pinning one public key that rotation will break. Reach for it when you need JWE or JWK-set handling in one place; for plain signed JWT verification against a provider's JWKS, PyJWT covers the same ground and is what most FastAPI codebases use.

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

Rebuff layers cheap-to-expensive checks over an incoming prompt: heuristic patterns for known injection phrasings, a similarity lookup against a vector store of attacks seen before, a dedicated LLM call asked to judge whether the input is an injection attempt, and a canary token planted in the system prompt whose appearance in any output proves the prompt leaked.

No layer is sufficient alone — a paraphrase defeats the heuristics and the judging model can itself be argued around — so treat it as a filter that raises an attacker's cost, not a boundary you can rely on. The real containment is elsewhere: least-privilege tool permissions, human approval on destructive actions, and scanning what the model is about to send back.

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

You encrypt a Secret with `kubeseal` against the controller's public key, producing a SealedSecret custom resource that is safe to commit to a public repository; only the controller running in that cluster holds the private key and decrypts it into a real Secret. Encryption is scoped to a namespace and name by default, so a sealed value cannot simply be copied into another namespace and unsealed there.

That makes it the low-friction answer for GitOps, where the whole desired state including secrets has to live in the repository. Know the two limits before adopting it: the controller's private key is now a critical backup - lose it and every sealed value in git is unrecoverable - and rotation is manual, since the file in git does not change when the underlying credential should. For dynamic credentials, central audit or cross-cluster reuse, a real secret manager fronted by the External Secrets operator is the alternative.

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

`cosign` authenticates you through OIDC — a CI workload identity or a developer's account — receives a short-lived certificate from Fulcio bound to that identity, signs the artifact, and records the signature in the Rekor transparency log. Because the certificate expires in minutes, there is no long-lived signing key to store, leak, or rotate, and verification checks both the identity in the certificate and the artifact's inclusion proof in the public log.

Admission controllers and policy engines then enforce that only images signed by an expected identity and workflow may run, and the same mechanism signs SLSA provenance and SBOM attestations. Reach for it for container images and build attestations; the mental shift is that you are verifying which workflow built something, not merely that some key signed it.

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

It resolves the dependency graph from lock files rather than matching package names, so it reports which transitive path introduced a vulnerable version and, where one exists, the smallest upgrade of a direct dependency that removes it - often opened as a pull request automatically. That fix-path emphasis is the practical difference from a scanner that only hands you a CVE list. Coverage extends past libraries to container base images, infrastructure-as-code templates and first-party code.

It runs as a CLI in CI, an IDE plugin and a git integration, and can break a build above a severity threshold. It is a commercial product with a limited free tier, and its curated vulnerability database is simultaneously the reason to use it and the thing you become dependent on - worth weighing against an open scanner backed by the public NVD feed.

### SOPS
**Short:** Encrypts values inside YAML/JSON/env files with KMS/age/PGP so secrets can live safely in Git for GitOps.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, platform-delivery/infrastructure-as-code-and-config @3, platform-delivery/ci-cd-and-release @3

SOPS encrypts the values in a structured file and leaves the keys and document shape in plaintext, so `git diff` still shows which setting changed and code review still works while the secret itself stays unreadable. Each file's data key is wrapped with a KMS key, an `age` or PGP key, or Vault transit, and `.sops.yaml` maps path patterns to recipients — so CI, staging and production can each decrypt with their own identity, and removing one recipient means re-encrypting rather than rotating every secret everywhere.

That is what makes it fit GitOps: the whole desired state including secrets lives in the repository, and the cluster decrypts through a Flux or Argo CD integration or the SOPS operator at apply time. The residual risk is unchanged by the encryption — whoever can use the wrapping key can read every secret it wraps, so the access policy on that key is the real control, not the file.

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

Everything happens in a chain of servlet filters that run before your controllers: one filter establishes the authentication from the request — session cookie, bearer token, client certificate — another decides whether the resulting principal may reach the endpoint, and later ones add CSRF protection and response security headers. You configure it by declaring a `SecurityFilterChain` bean with the lambda DSL, listing request matchers and their authorization rules, and add `@PreAuthorize` where the decision needs the method's arguments rather than just the URL.

The recurring mistakes are both about defaults: matcher order decides the outcome, so a permissive pattern placed first wins over the stricter one below it, and disabling CSRF to make a browser-facing form work removes a protection instead of fixing the form.

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

Syft scans a container image, a directory or an archive and catalogs what is inside it — OS packages from the distro database plus the language ecosystems it can see (npm, pip, Go modules, Maven, gems) — emitting the result as CycloneDX or SPDX. It reads the artifact itself rather than the build's lockfile, so it also finds what was installed along the way and whatever the base image contributed.

An SBOM is only worth generating because of what consumes it: pipe the output into Grype or another scanner to match components against vulnerability feeds, and store it alongside the image so that when a new CVE lands you can answer which images ship the affected library without rebuilding anything.

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

The library provides differentially private training: optimizers that clip each individual example's gradient to a fixed norm and then add calibrated Gaussian noise to the summed batch gradient, so no single training record can move the resulting weights by more than a bounded amount and membership inference is provably limited. Alongside them ships a privacy accountant that converts your noise multiplier, sampling rate and number of steps into the epsilon and delta you can actually state in a document.

Reach for it when a formal privacy claim about a model trained on personal data is a requirement rather than a preference — regulated data, a model you intend to publish, a claim you must defend. The costs are concrete: per-example gradient clipping is substantially slower than ordinary training, and accuracy falls as epsilon tightens, so the epsilon you pick is an explicit trade of utility for guarantee.
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

One binary scans many targets: a container image layer by layer, mapping OS packages against distro advisories and language lockfiles against advisory databases; a filesystem or git repository; an infrastructure-as-code directory of Terraform, CloudFormation, Kubernetes manifests or Dockerfiles against built-in misconfiguration policies; a running cluster; and an SBOM. It also detects hard-coded secrets and can emit SBOMs in CycloneDX or SPDX format.

Practical notes: it downloads and caches a vulnerability database, which you should mirror or pre-warm in CI rather than fetch on every build; `--severity` together with `--exit-code` is how it becomes a gate; and `.trivyignore` entries want expiry dates so accepted risks do not become permanent. Reach for it as the default pipeline scanner since it is fast and needs no server -- but a scanner reports known CVEs in declared dependencies, which is a floor for supply-chain security rather than the whole of it.

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

It is organized as secrets engines rather than one flat store: KV for static secrets, dynamic engines that mint a short-lived database or cloud credential on request and revoke it when the lease expires, a PKI engine that issues certificates, and transit, which encrypts and signs on your behalf so the key never leaves Vault at all. Auth methods map a workload identity — a Kubernetes service account, cloud IAM role, or OIDC subject — to policies, and every access is written to an audit log.

Reach for it when credentials must be short-lived and centrally revocable instead of long-lived environment variables copied between systems; the dynamic-credential model means a leaked secret expires on its own. The costs are that unsealing, key rotation, and storage backend health become your problem, and that Vault sits on the critical path of everything that needs a credential — which is why it is run highly available.

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
