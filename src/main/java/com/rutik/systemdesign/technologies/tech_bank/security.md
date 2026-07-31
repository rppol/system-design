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

The sender mints a short-lived JWT whose claims name the calling agent and the task, signs it with a private key, and the receiver fetches the sender's published JWKS to check the signature along with the `iss`, `aud` and `exp` claims. Binding `aud` to the specific receiving agent is what stops a token captured by one downstream agent being replayed against another, and a short expiry bounds the window if one leaks into a log.

Use it wherever agents cross a trust boundary, so an incoming request carries a verifiable identity instead of arriving as an anonymous HTTP call. It proves who sent the message, not that the message is safe: a prompt-injected agent signs its instructions just as validly, so pair it with per-agent authorization and scoped tool permissions. Mutual TLS is the alternative when both ends are yours and transport-level identity is enough.

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

It is the harmful-behaviours and harmful-strings set released alongside the greedy coordinate gradient attack: several hundred short instructions requesting clearly disallowed content, each paired with a target affirmative prefix such as an agreement to answer, which a white-box attack optimises the model toward. Attack success is conventionally scored by checking the response against a list of refusal phrases.

Reach for it as the common denominator when comparing jailbreak attacks or defences, because nearly every paper reports on it. Its weaknesses follow from its simplicity: behaviours are short, single-turn and English, refusal-substring scoring both over- and under-counts, and the set is old enough to have leaked into training data. HarmBench or JailbreakBench give a broader taxonomy and a trained judge instead of string matching.

### Adversarial Robustness Toolbox
**Short:** IBM library implementing evasion, poisoning and extraction attacks plus defenses across model types.
**Kind:** tech
**Lang:** python
**Roles:** security/ai-safety-and-guardrails @1, ml-lifecycle/evaluation-and-benchmarks @3

ART wraps your model in an estimator for whichever framework it uses, then runs attacks and defences against that uniform interface, so the same FGSM, PGD, Carlini-Wagner, boundary or HopSkipJump implementation works whether you have gradients or only query access. Its scope goes past evasion to data poisoning and backdoors, model extraction, and inference attacks such as membership inference, and it covers scikit-learn and gradient-boosted models rather than only deep networks.

Reach for it when one library must span several model types and both white-box and black-box threat models. The breadth costs depth: for a robust-accuracy number on an image classifier, AutoAttack is what reviewers expect, and the defences implemented here are baselines for comparison rather than protections to deploy and trust.

### AgentHarm
**Short:** Benchmark of 110 harmful agent behaviours over synthetic tools that scores refusal and harm separately.
**Kind:** dataset
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1, ml-lifecycle/evaluation-and-benchmarks @1

The benchmark measures whether an agent carries a harmful task through, not merely whether it says something objectionable. Each behaviour ships with synthetic tools the model can call, and grading separates two signals: whether the model refused, and whether the tool-call trajectory actually completed the task. That split matters because a model can refuse in prose while still executing the steps, or comply verbally and fail at the mechanics.

Use it when you ship tool-using agents and want evidence about the agentic surface rather than chat-only safety, including how much refusal survives a jailbreak template wrapped around the request. It is synthetic by construction, so it says nothing about your own tools or the injection paths in your retrieval corpus, and those need probes you write yourself.

### Ansible Vault
**Short:** Ansible's built-in encryption for secrets kept in playbooks and vars files, decrypted at run time.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, platform-delivery/infrastructure-as-code-and-config @2

`ansible-vault encrypt` turns a whole file, or a single value via `encrypt_string`, into AES-256 ciphertext keyed from a password supplied at run time with `--ask-vault-pass`, a password file, or a script that fetches it from elsewhere. Encrypted variables are decrypted in memory during the play, and vault ids let one playbook carry separately keyed staging and production secrets.

It is the zero-infrastructure answer when Ansible is already the deployment path and you want secrets versioned in git. The limits arrive quickly: one shared password guards everything it encrypts, an encrypted blob makes `git diff` useless for review, and there is no rotation, leasing or audit trail. Once several teams share the repository, Vault or a cloud secret store is the upgrade.

### Anthropic's Responsible Scaling Policy
**Short:** Published governance framework tying model capability thresholds to required safety measures before release.
**Kind:** concept
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1, security/privacy-and-compliance @3

The policy defines AI Safety Levels, tiers of model capability with correspondingly stricter deployment and security requirements. Evaluations run before release to test whether a model has crossed the capability thresholds for the next level in domains such as biological and cyber uplift and autonomous action; crossing one obliges the matching safeguards to be in place first, and commits to holding deployment if they are not.

It matters as a template more than as a product: a published commitment tying a measurable capability to a required control is far easier to audit than a general promise of care, and comparable frameworks now exist at other labs. The unresolved part is the same everywhere, that a threshold is only as meaningful as the evaluation deciding you have reached it, and those evaluations are the weakest link in the chain.

### Argon2
**Short:** Memory-hard password hashing function (RFC 9106, PHC winner); OWASP's recommended choice for storing passwords.
**Kind:** spec
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, security/authentication-and-identity @2

Three variants exist: Argon2d is data-dependent and faster but exposes side channels, Argon2i is data-independent, and Argon2id is the hybrid you should use, running one data-independent pass before switching. It fills a large block of memory with derived state and reads it back pseudorandomly, so an attacker's GPU or ASIC must provide that memory per guess rather than only arithmetic units, which is the property iteration-only functions such as PBKDF2 lack.

Use Argon2id for any new password store, tuning memory, iterations and parallelism to what the login path can afford. The constraint people miss is that memory cost is charged per concurrent verification, so a generous setting on a busy endpoint becomes a capacity and denial-of-service question. bcrypt remains acceptable for existing systems, and scrypt is the memory-hard fallback where no Argon2 implementation is available.

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

ARX is an anonymization workbench: you load a table, mark each column as identifying, quasi-identifying, sensitive or insensitive, supply generalization hierarchies such as a birth date to a year or a postcode to its first digits, and it searches transformations satisfying the privacy model you chose while losing as little utility as possible. It reports both sides of that trade, giving re-identification risk under prosecutor, journalist and marketer attacker models next to information-loss metrics.

Reach for it before releasing or sharing a microdata table, where the deliverable is the dataset itself and the release has to be defensible. It works on static tabular data, not streams or free text, and the guarantees are syntactic: k-anonymity bounds linkage but is not differential privacy and composes badly across repeated releases of the same population.

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

One library covers both sides. The client performs authorization-code, PKCE, client-credentials and device flows with integrations for requests, httpx, Flask, Django and Starlette, while the server side can issue and introspect tokens for your own provider. Underneath sits a complete JOSE implementation covering JWS, JWE, JWK and JWT, so signing, verification and JWKS handling need no second dependency.

Reach for it when you are implementing third-party login or an OAuth provider rather than only validating someone else's tokens, and especially when a framework integration saves writing the redirect and callback plumbing. If all you need is to verify a bearer JWT against a provider's JWKS, PyJWT is a smaller surface. Either way pass the accepted algorithms, expected issuer and expected audience explicitly at verification.

### AutoAttack
**Short:** Parameter-free ensemble of strong adversarial attacks; the standard for reporting honest robust accuracy.
**Kind:** tech
**Lang:** python
**Roles:** security/ai-safety-and-guardrails @1, ml-lifecycle/evaluation-and-benchmarks @2

It is not a new attack but a fixed ensemble run in sequence, and the point is that nothing is tunable: two auto-PGD variants with different losses, a targeted FAB attack and the black-box Square Attack, with an example counted as broken if any component succeeds. Removing the step size and iteration count removes the two knobs by which a weak evaluation accidentally flatters a defence.

Report robust accuracy with it whenever you claim a defence works, which is why RobustBench standardises on it. It is expensive, several times the cost of plain PGD, and it targets Lp-bounded perturbations, so it says nothing about patch attacks, semantic perturbations or language models. A defence that survived only through gradient masking will show its real number here.

### AutoDAN
**Short:** Reference implementation of genetic-algorithm jailbreak search, used to red-team model refusal behavior.
**Kind:** tech
**Lang:** python
**Roles:** security/ai-safety-and-guardrails @1

The attack searches for a jailbreak prompt with a genetic algorithm rather than gradient descent over tokens. It starts from handwritten jailbreak prompts as the initial population, then mutates and recombines them at sentence and paragraph level, often using an LLM to paraphrase, scoring candidates by how close the target comes to complying. Because the population stays fluent prose throughout, the survivors read like text a person could have written.

That fluency is why it belongs in a red-team suite next to gradient attacks: a perplexity filter that catches a garbled adversarial suffix will not catch this. The costs are many target queries per behaviour and a search fitted to one model, so transfer to a different target is not guaranteed and usually needs rerunning.

### AutoDAN-Turbo
**Short:** Reference implementation of automated jailbreak generation via genetic search and strategy discovery.
**Kind:** tech
**Lang:** python
**Roles:** security/ai-safety-and-guardrails @1

It removes the handwritten seeds. An attacker agent probes the target, judges which attempts moved it toward compliance, names the tactic it just used, and writes that into a growing strategy library which later attempts retrieve and recombine, so the system accumulates reusable jailbreak strategies across runs instead of restarting each time. A library discovered against one model can be injected as the starting point against another.

Use it when you want red-teaming that keeps finding new angles rather than replaying a fixed corpus, which is exactly what a static benchmark stops providing after the first round of fixes. It is query-hungry and needs a capable attacker and scorer model, so a full run has real cost, and every discovered strategy still needs a human to judge whether the resulting output is genuinely harmful.

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

A policy is a `permit` or `forbid` over a principal, action and resource with an optional `when` condition, evaluated deny-by-default with any `forbid` overriding every `permit`. The language is deliberately not Turing-complete, which is what lets an SMT solver analyse a policy set: you can ask whether two versions are equivalent or whether any request could ever reach a resource, instead of testing examples. A schema types entities and actions so mistakes surface before deployment.

Reach for it for application authorization where policies should be data you can reason about, embedded as a library or hosted by Amazon Verified Permissions. Against Rego the trade is expressiveness for analysability, and the harder half of the work is unchanged: Cedar decides over the entity graph and context you hand it, so modelling groups, ownership and tenancy into that input is the real design task.

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

Two calls do different jobs. `DetectPiiEntities` returns typed spans with offsets and confidence for entity types such as name, address, bank account and national identifier, while `ContainsPiiEntities` reports only which categories are present, which is cheaper when you just need to route or block a document. Asynchronous redaction jobs run the same detection across S3 prefixes and write masked copies, and the entity types considered are selectable.

Reach for it when you are already in AWS and want detection you do not operate, especially for bulk redaction of stored documents. It is a managed model you cannot retrain, so an identifier specific to your business is invisible to it and recall on free text is never total: treat it as one control, not a compliance guarantee. Presidio is the self-hosted alternative when custom recognizers or keeping text in your network matter.

### AWS IAM DB Auth
**Short:** RDS/Aurora feature swapping database passwords for short-lived IAM-signed tokens at connection time.
**Kind:** tech
**Lang:** *
**Roles:** security/authentication-and-identity @1, data-access/drivers-and-connection-pooling @3

`generate-db-auth-token` never touches the database. It builds a SigV4-signed string from your IAM credentials which you then pass as the password, and RDS validates the signature, so the credential lives about fifteen minutes and no password exists to store or rotate. The database user must be created with the IAM authentication plugin or granted `rds_iam`, and TLS is mandatory because the token is a bearer credential on the wire.

Use it so database access is governed by the same IAM policies and CloudTrail trail as everything else. Two limits shape the design: new connections per second are throttled well below what a plain password allows, and a pooled connection outlives the token that opened it, so generate a fresh token per new connection and keep the pool warm rather than reconnecting per request.

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

Macie samples and inspects objects in S3, matching them against managed data identifiers for things like payment card numbers, credentials and health identifiers, plus custom regexes with keyword proximity rules. Automated sensitive data discovery continuously profiles buckets at low daily cost and builds a map of where sensitive data probably lives, while scheduled classification jobs do the deeper per-object scan. It also flags buckets that are public, unencrypted or shared outside the account.

Reach for it to answer where the sensitive data actually is across accounts, a question nobody can answer from memory once a data lake grows. Plan the cost: job pricing scales with bytes inspected, so scope by prefix, object tag and file type rather than scanning everything. It only looks at S3, so databases and other stores need their own tooling.

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

Parameters are named on a path hierarchy, typed `String`, `StringList` or `SecureString`, and versioned on every write. A `SecureString` is encrypted with a KMS key and returned in clear only when the caller passes `--with-decryption` and holds both the parameter and the key permissions. `GetParametersByPath` pulls a whole environment's configuration in one call, and labels let you pin a known-good version.

Reach for it as the default home for configuration and for secrets that do not need managed rotation, since the standard tier is free and every AWS service already integrates with it. Know the ceilings first: standard parameters cap at 4 KB with modest throughput, the advanced tier bills per parameter per month, and there is no built-in rotation because a schedule only invokes your own code. Secrets Manager is the upgrade when rotation is the requirement.

### AWS WAF
**Short:** AWS web application firewall: managed and custom rules blocking common attack patterns at the edge.
**Kind:** tech
**Lang:** *
**Roles:** security/supply-chain-and-runtime-security @1, traffic-edge/rate-limiting-and-resilience @2, platform-delivery/cloud-platform-and-cost @3

Rules live in a web ACL attached to CloudFront, an Application Load Balancer, API Gateway, AppSync or Cognito, and each request is evaluated in priority order until one allows, blocks, counts or challenges it. Managed rule groups from AWS and vendors cover the common families, and your own rules match on headers, URI, body, IP set, geography or a rate-based counter. Every rule consumes capacity units from a fixed budget per ACL, which is the real constraint on how elaborate a policy can get.

Reach for it as the edge layer absorbing scanner traffic, obvious injection attempts and abusive request rates before they reach your application. Deploy every new rule in count mode first and read the sampled requests, because a managed group promoted straight to block will refuse legitimate traffic. It filters patterns, not logic, so it does not fix the injectable query or the missing authorization check underneath.

### Azure AD
**Short:** Microsoft's cloud identity provider (now Entra ID) issuing OIDC/OAuth tokens for SSO, MFA and conditional access.
**Kind:** tech
**Lang:** *
**Roles:** security/authentication-and-identity @1, security/authorization-and-policy @3

A tenant holds users, groups, application registrations and the service principals representing an application inside it, and the service issues OIDC ID tokens and OAuth2 access tokens that applications validate against the tenant's published JWKS, checking `iss`, `aud` and the `roles` or `scp` claims. Conditional access is the distinguishing piece: policy evaluated at sign-in against signals such as device compliance, location and detected risk, which is how step-up authentication gets applied selectively.

It is the default identity provider anywhere Microsoft 365 already runs, because the workforce directory exists and single sign-on becomes a matter of registering an application. The name is now Entra ID, so current portal blades, SDKs and documentation have moved even though `login.microsoftonline.com` and older tooling names remain in wide use; expect to translate between the two while reading.

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

The PII feature of Azure AI Language returns typed entities with offsets and confidence, and can return a redacted copy of the text in the same call, with the masking policy selectable between a fixed character, the entity type name, or removal. Categories can be restricted to the ones you care about, and a separate conversational mode is tuned for transcripts, where speaker turns and disfluencies break models trained on documents.

Reach for it when you are on Azure and want redaction without hosting a model, particularly for scrubbing prompts and logs before they leave a boundary. The model is fixed, so a business-specific identifier goes undetected and recall on free text is never complete, which makes it a defence layer rather than proof of compliance. Presidio is the self-hosted route when you need custom recognizers or the text must not leave your network.

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

### Bouncy Castle
**Short:** Full-suite Java/C# crypto provider covering algorithms and PKI formats the JDK does not ship.
**Kind:** tech
**Lang:** java
**Roles:** security/secrets-and-cryptography @1

The provider registers with the JCA and JCE, so a call like `Cipher.getInstance("ChaCha20-Poly1305", "BC")` reaches its implementations through standard APIs and application code stays portable. Its reach is why it is everywhere: Argon2, scrypt, ChaCha20-Poly1305, EdDSA, extended and national curves, and more often the real motive, the PKI and message formats the JDK does not handle at all, including CMS, PKCS#10 certificate requests, OpenPGP, timestamping and S/MIME.

Add it when the JDK genuinely lacks the algorithm or format you need, not reflexively: the default Sun providers are well maintained, and a second cryptographic implementation is one more dependency to track for advisories and one more question in a FIPS-validated environment, where a separately certified distribution exists. For certificate and CMS work the lightweight API is often easier than going through the JCA.

### Burp Suite
**Short:** Intercepting proxy and DAST scanner for probing a running web application for vulnerabilities.
**Kind:** tech
**Lang:** *
**Roles:** security/supply-chain-and-runtime-security @1, devtools/testing-and-mocking @3

Everything routes through the intercepting proxy: point a browser at Burp, the traffic lands in HTTP history, and from there a request goes to Repeater for hand-editing, to Intruder for automated payload iteration over marked positions, or to the scanner, which both passively flags issues in observed traffic and actively probes with crafted payloads. The session-handling rules and macros are what keep it authenticated while doing so.

It is the standard tool for manual web application testing, with an extension ecosystem covering the specialised cases. The split matters commercially, since the Community edition has no active scanner and throttles Intruder, so serious use means the Professional licence. Test only what you are authorised to test: active scanning sends real attack traffic and writes real data.

### Casbin
**Short:** Embeddable access-control library whose model file expresses RBAC, ABAC or ACL rules evaluated per request.
**Kind:** tech
**Lang:** *
**Roles:** security/authorization-and-policy @1

Authorization is split in two. A model file written in PERM terms declares the shape of a decision, naming the request attributes, the policy attributes, the matcher expression, the role definition and the effect rule, while the policy itself is rows loaded through an adapter from CSV, a database or anywhere else. Because the matcher is an expression over those attributes, the same engine expresses an ACL, RBAC with role inheritance and domains, or full ABAC by changing the model rather than the code.

Reach for it when one permission model must hold across services in different languages, since the Go, Java, Python and Node ports share model and policy files. It evaluates in-process and is fast, but the policy set lives in your memory and storage, so very large rule tables need filtered adapters and watchers to stay in sync. When policy should be versioned, tested and deployed independently of services, OPA is the alternative.

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

Instead of classifying inputs or outputs, the technique trains the model so that internal representations leading to harmful completions get rerouted toward unrelated directions, using a short adapter-based run whose loss pushes those activations away from the harmful subspace while leaving ordinary behaviour intact. A model treated this way degrades into incoherence partway through a harmful generation rather than producing it, which is why an adversarial suffix that defeated a text filter buys nothing here.

Reach for it when you hold the weights and want a defence that generalises across attack phrasings, since it acts on what the model is representing rather than on surface text an attacker can perturb. It is a research-stage method, it requires training access, and its coverage is only as broad as the harmful set used to build it, so keep output classifiers and tool-permission limits in place alongside it.

### Cisco AI Defense
**Short:** Commercial AI firewall: runtime guardrails plus continuous validation and red-teaming of deployed model applications.
**Kind:** tech
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1, security/supply-chain-and-runtime-security @3

It covers three phases rather than one control point: discovering which AI applications and models an organisation is actually using, validating a model or application through automated algorithmic red-teaming that generates attacks specific to it, and enforcing runtime guardrails on prompts and responses. The validation half derives from the Robust Intelligence technology Cisco acquired, and enforcement is designed to sit inline with the network and security stack already deployed.

Reach for it in an enterprise where the problem is shadow AI and one central policy across many teams, rather than hardening a single application you own. That framing is also the limit: it is a commercial platform priced accordingly, and generic attack coverage cannot know your application's own abuse cases, so application-specific probes and least-privilege tool design stay your responsibility.

### CleverHans
**Short:** Adversarial-example library for benchmarking attacks and defences against machine-learning models.
**Kind:** tech
**Lang:** python
**Roles:** security/ai-safety-and-guardrails @1, ml-lifecycle/evaluation-and-benchmarks @2

It is the original adversarial-example library from the researchers who named the field, and it exists mostly to make reported numbers comparable: reference implementations of the canonical attacks including FGSM, the basic iterative method, PGD, Carlini-Wagner and SPSA, written to match the papers rather than to be fast. Current versions target JAX, PyTorch and TensorFlow 2 after a rewrite that dropped the old graph-based API.

Reach for it when reproducing or teaching a classic attack and you want code that matches the published description. For actually evaluating a defence the field has moved on: AutoAttack is what people expect behind a robust-accuracy claim, and Foolbox or ART are better maintained for building custom attacks. Tutorials written for the old TensorFlow 1 API will not run against the current package.

### Cloud IAM
**Short:** Google Cloud's identity and access management: service accounts, roles and policy bindings on every resource.
**Kind:** tech
**Lang:** *
**Roles:** security/authentication-and-identity @1, security/authorization-and-policy @1, platform-delivery/cloud-platform-and-cost @3

A policy is a set of bindings attaching a role, whether basic, predefined or custom, to members, and it is set on a node of the resource hierarchy: organisation, folder, project or an individual resource. Policies inherit downward and are additive, so a grant made at project level cannot be narrowed further down, which is what deny policies exist to correct. Conditions add attribute tests on request time, resource name or tags, and service accounts are first-class members so workloads are principals in the same model.

The practical work is avoiding the basic roles such as Editor, which are far broader than anyone intends, and giving each workload its own service account with predefined roles bound at the tightest level that works. Service account keys are the recurring incident because they are long-lived credentials in files: use Workload Identity Federation or impersonation instead, and use the policy analyser to answer who can actually reach a resource.

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

Training runs in two stages. First the model generates a response, critiques it against written principles and revises it, and is fine-tuned on the revisions. Second, it labels its own pairwise preferences using those same principles, and the resulting AI-generated preference data trains a reward model for reinforcement learning, which is the RLHF loop with the human labellers replaced at the preference step.

The appeal is that the behaviour target becomes a short reviewable document instead of the implicit consensus of a labelling workforce, so a policy change is a change you can read in a diff. The catch is that the principles are only as effective as the model's ability to apply them, so humans are not removed from the loop, they move to writing and auditing the constitution and checking the preferences the model produced.

### Container hardening
**Short:** Shrinking a container's attack surface: minimal base image, non-root user, read-only mounts, digest-pinned tags.
**Kind:** concept
**Lang:** *
**Roles:** security/supply-chain-and-runtime-security @1, platform-delivery/container-and-image @2

The moves are concrete: build from a distroless or minimal base so there is no shell or package manager for an attacker to use, run as an unprivileged user with `runAsNonRoot` enforced, drop every Linux capability and add back only what is needed, mount the root filesystem read-only with writable paths declared as explicit volumes, and reference base images by digest rather than a mutable tag. Multi-stage builds keep compilers, source and build credentials out of the final layer.

Do it because it cheapens every later defence: a scanner has less to report when the image carries fifty packages instead of five hundred, and a remote-code-execution bug is far harder to escalate with no shell and no writable filesystem. The friction is debugging, since exec into a distroless container gives you nothing, so plan for ephemeral debug containers rather than adding a shell back.

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

The pattern turns an erasure request into a durable append-only record, carrying the subject identifier, request time and scope, which a fan-out process then drives against every system holding that subject's data, marking per-store completion as each one acknowledges. That record is what makes deletion resumable and provable: an offline warehouse, a backup restore or an unreachable downstream partner gets replayed later, and an auditor can be shown when each store confirmed.

Adopt it as soon as personal data lives in more than one place, because a delete issued against the primary database leaves copies in caches, search indexes, event logs, analytics tables and backups. The awkward part is the ledger itself, which must retain enough identity to recognise a returning record without becoming another copy of what you deleted, so key it on a hashed or tokenised identifier and set its own retention deliberately.

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

A decentralized identifier names its method in the middle of the string, so this library is essentially a registry: you construct a `Resolver` with the method resolvers you support, and resolving `did:web:example.com` dispatches to the right one and returns a DID Document listing the subject's verification methods and service endpoints. The public keys in that document are what you then use to check a signature or a verifiable credential.

Reach for it on the verifier side of a decentralized-identity flow, where a credential or signed message arrives and you need the issuer's keys without a central directory. Each method resolver is a separate dependency with its own trust story, since `did:web` is only HTTPS and DNS while ledger-backed methods need network access to a chain, so the guarantee you end up with is whatever the weakest registered method provides.

### dp-accounting
**Short:** Google library that composes differential-privacy budgets with RDP and PLD accountants outside a training loop.
**Kind:** tech
**Lang:** python
**Roles:** security/privacy-and-compliance @1

The library does one job: given a sequence of privacy-consuming mechanisms, it computes the epsilon and delta they compose to. It implements Renyi differential privacy accounting and the tighter privacy loss distribution approach, which numerically convolves each mechanism's loss distribution instead of relying on a closed-form bound, and it inverts the calculation so you can ask for the noise needed to hit a target epsilon.

Reach for it when budget is spent outside a training loop, such as differentially private aggregations, repeated queries against a dataset, or several releases over time, where the accountant bundled with a DP-SGD framework does not apply. It only accounts: it adds no noise and enforces nothing, so the calibration it reports is correct only if the mechanism you actually ran matches the one you described to it.

### dp-transformers
**Short:** Microsoft library wiring DP-SGD and privacy accounting into Hugging Face Transformers fine-tuning.
**Kind:** tech
**Lang:** python
**Roles:** security/privacy-and-compliance @1, model-training/fine-tuning-and-peft @3

It closes the gap between DP-SGD and the Hugging Face training loop, wiring Opacus into `Trainer` so per-sample gradient clipping, Gaussian noise and Poisson sampling apply to an ordinary fine-tuning script, with the budget expressed as a target epsilon and delta in the training arguments. The transformer-specific work is making per-sample gradients tractable for the layers these models use, and settling what counts as one example when the unit is a document rather than a row.

Reach for it to fine-tune a language model on data whose individual records must not be memorised or extractable, which is the concrete defence against a model reciting a training document verbatim. Budget for real cost: per-sample gradients raise memory and step time, effective batch sizes must be large for the noise to be tolerable, and quality falls as epsilon tightens, so measure the drop before committing.

### DSR platforms
**Short:** Tooling that orchestrates GDPR-style data-subject deletion and access requests across every store.
**Kind:** concept
**Lang:** *
**Roles:** security/privacy-and-compliance @1

A data-subject request platform is the workflow layer over an obligation with a statutory clock. It takes requests from a portal or web form, verifies the requester is who they claim, discovers which internal systems hold that person's data, dispatches access, correction or deletion tasks to each system through connectors or tickets, assembles the responses into one package, and retains the evidence trail for a regulator.

Reach for one when request volume or the number of data stores makes a spreadsheet and a shared inbox untenable, which happens quickly under GDPR or CCPA-style rules with deadlines measured in days. What these tools do not remove is the mapping: a connector still needs to know which table and which key holds the subject, so a data inventory and a stable subject identifier are prerequisites rather than outputs.

### Dual-LLM pattern
**Short:** Prompt-injection defence splitting work between a quarantined LLM that sees untrusted data and a privileged one.
**Kind:** concept
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1, llm-apps/agent-framework @3

Two models with different privileges. A quarantined model reads untrusted content such as a web page, an email or a retrieved document and may return only structured data, never instructions; a privileged model orchestrates and holds the tool access but never sees that raw content, working instead with opaque references such as variable names that the controller substitutes at call time. Instructions hidden in the untrusted text therefore land in a model with nothing to actuate.

Reach for it in agents that must both read attacker-influenced content and take consequential actions, because an injection detector is a filter that eventually fails while this is a structural boundary. The cost is expressiveness: anything requiring the privileged model to reason over the untrusted text itself breaks the separation, so the design forces you to decide in advance which data flows are permitted, the same discipline behind later capability-based variants.

### EasyJailbreak
**Short:** Red-team framework implementing GCG, AutoDAN, PAIR, TAP and GPTFuzzer under one comparable interface.
**Kind:** tech
**Lang:** python
**Roles:** security/ai-safety-and-guardrails @1, ml-lifecycle/evaluation-and-benchmarks @3

It decomposes jailbreak attacks into interchangeable parts, namely a seed set, mutators that rewrite a prompt, constraints that filter candidates, a selector deciding what to try next, and an evaluator scoring whether the target complied, then reimplements published attacks such as GCG, AutoDAN, PAIR, TAP and GPTFuzzer on that shared scaffolding. Because they share components and one evaluator, results across attacks are comparable in a way separate research repositories never are.

Reach for it to run several attack families against one model and get numbers that belong in the same table, or when building a new attack and wanting the surrounding machinery for free. It follows the papers rather than optimising them, so expect research-grade throughput, and the evaluator's definition of a successful jailbreak is a decision to inspect rather than accept, because it sets every number you report.

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

Falco reads a stream of kernel events, gathered through a modern eBPF probe or a kernel module, plus Kubernetes audit events, and matches them against rules written as conditions over event fields, so notions like a shell spawned inside a container, a sensitive file read, or an outbound connection to an unexpected address become rules you can read. Container and Kubernetes metadata is attached to every event, so an alert names the pod and image rather than a bare process id.

Reach for it as the detection layer for behaviour no image scan can predict, since a compromised process looks perfectly fine on disk. Two realities dominate operations: the default rule set is noisy until tuned to your workloads, and Falco alerts rather than blocks, so response comes from routing its output through falcosidekick into incident tooling. For in-kernel enforcement instead of notification, Tetragon is the neighbouring choice.

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

Foolbox is built on EagerPy, so one attack implementation runs natively against PyTorch, TensorFlow and JAX models with no conversion layer and gradients flowing in the native framework. Its API separates the model wrapper, the attack and the epsilon, so you can pass several perturbation budgets in a single call and get the whole accuracy-versus-epsilon curve, and attacks report the minimal perturbation found rather than only a success flag.

Reach for it while iterating on attacks during development, where clean and fast framework-agnostic implementations matter most. For the headline robustness number in a paper or report run AutoAttack instead, because that is the standard reviewers expect, and for non-neural models or poisoning and extraction threats ART covers ground Foolbox does not.

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

Policy comes in two objects. A `ConstraintTemplate` carries the Rego and declares a new CRD with a typed parameter schema; a `Constraint` instantiates that template with actual parameters and a match block naming the kinds and namespaces it covers, so the person writing Rego and the person applying a policy to a namespace can be different people. Beyond admission it audits existing objects on a schedule and records violations in the constraint's status, which is how you learn what the cluster already contains.

Reach for it when policy must be enforced at the API server and Rego is already your language, especially if the same rules gate CI and application authorization. Set new constraints to dry run first and read the audit, since enforcement blocks deployments of workloads that predate the rule. Kyverno is the alternative when YAML policies beat learning Rego, and Pod Security Admission covers the standard pod cases with no controller at all.

### GCP Cloud KMS
**Short:** Google Cloud managed key management: envelope encryption, key rotation and HSM-backed keys.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1

Keys live in a key ring in a location, and a key holds numbered versions; you encrypt against the key and the returned ciphertext records which version was used, so rotation adds a new primary version while older ciphertext still decrypts. Protection level is chosen per key as software, HSM, or external through a partner key manager, and the API covers symmetric encryption, asymmetric signing and verification, and MAC operations.

The pattern that matters is envelope encryption: generate a data key, encrypt bulk data locally with it, and store only the KMS-wrapped copy, so a terabyte of data costs one small API call rather than a terabyte of traffic. Watch per-key request quotas and latency on hot decrypt paths, and note that automatic rotation applies to symmetric keys only, since asymmetric keys are rotated by you creating a version and migrating traffic to it.

### GCP DLP
**Short:** Google Cloud Sensitive Data Protection: managed APIs that find, classify and redact PII across many languages.
**Kind:** tech
**Lang:** *
**Roles:** security/privacy-and-compliance @1, security/ai-safety-and-guardrails @2

Inspection runs a large catalog of built-in infoType detectors, plus custom regex, dictionary and stored infoTypes, over text, images, BigQuery tables or Cloud Storage objects, returning findings with a likelihood rating rather than a boolean. De-identification then applies a transform per infoType: redaction, masking, bucketing, date shifting, or format-preserving tokenization with a wrapped key that can be reversed later. A separate risk analysis job measures k-anonymity and l-diversity on a table before you release it.

Reach for it to scrub prompts, logs and documents before they cross a boundary, and to find where sensitive data already sits in a lake. Cost tracks bytes inspected, so sample and scope jobs instead of scanning everything, and remember detectors are probabilistic: likelihood thresholds trade false positives against misses, and a business-specific identifier needs a custom infoType you define.

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

It installs git hooks that grep the staged diff against a list of prohibited patterns and abort the commit on a match, with an allowed-patterns list for the false positives. `git secrets --register-aws` loads patterns for AWS access key ids and secret keys, and `--scan-history` runs the same rules across every commit already in the repository.

Reach for it as the cheapest possible guard on a repository that touches cloud credentials, since it is a small shell script with no service behind it. Its limits are the flip side of that simplicity: pure regex with no entropy analysis, so it catches recognisable key shapes and misses a random token, and hooks are per-clone and skippable, so it must be paired with a CI scan. gitleaks or trufflehog are the fuller replacements.

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

GnuPG is the reference OpenPGP implementation. It manages a keyring of public and private keys, encrypts a file to one or more recipients by generating a random session key and wrapping it per recipient, and produces signatures either attached or detached in a separate file. `gpg --verify` checks a signature against a key in your ring, which is the mechanism behind signed release artifacts and package repository metadata, and `gpg-agent` holds the unlocked private key between operations.

Reach for it when you must interoperate with the OpenPGP ecosystem, such as verifying a distribution's release signatures, signing commits and tags, or feeding the SOPS and pass workflows built on top of it. For new designs the awkwardness is well known: the web of trust rarely gets used properly, the CLI is unforgiving, and age or libsodium are far simpler when you only need to encrypt a file to a key you already have.

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

The library provides differentially private building blocks rather than a framework: bounded sum, count, mean, variance and quantile aggregations that clamp each contribution to declared bounds and add Laplace or Gaussian noise calibrated to the resulting sensitivity, with an accountant tracking budget spent. Contribution bounding is the part that is easy to get wrong by hand, limiting how many partitions one user may affect and how much they contribute to each, and the library enforces it as part of the aggregation.

Reach for it when publishing statistics over per-user data and a formal guarantee is required rather than a policy of aggregating and hoping. A C++ core carries Go and Java wrappers, while Privacy on Beam and PipelineDP apply the same primitives at pipeline scale. The hard decisions remain yours: what the privacy unit is, how tight the clamping bounds should be, and what epsilon you can defend, since noise on small partitions can make a result useless.

### Google RAPPOR
**Short:** Local differential privacy scheme randomizing client reports before collection; the Chrome telemetry reference design.
**Kind:** tech
**Lang:** *
**Roles:** security/privacy-and-compliance @1

RAPPOR randomises on the client, before anything is sent, so the collector never holds a true value. A client's string is hashed into a Bloom filter, each bit is flipped with fixed probability to give a permanent randomised response memoised for that value, and every report then flips bits again to produce the instantaneous response actually transmitted. The two layers are what protect a client reporting the same value repeatedly, which plain randomised response would eventually expose.

The server recovers population frequencies by a statistical decode against a candidate string list, learning the distribution without any individual's value. That is the trade: noise per report is large, so it needs many clients and answers questions about common values only, since a rare string is indistinguishable from noise. Central differential privacy gives far better accuracy when you are trusted with raw data, and local privacy is for when you are not.

### grpc-spring-boot-starter security
**Short:** Spring Boot gRPC security layer bridging call credentials into SecurityContextHolder for @Secured methods.
**Kind:** tech
**Lang:** java
**Roles:** security/authentication-and-identity @1, security/authorization-and-policy @2, apis-frameworks/rpc-graphql-and-streaming @3

The starter bridges gRPC call metadata into Spring Security's model: an authentication reader pulls a credential out of the incoming `Metadata` headers, whether HTTP Basic, a bearer JWT or a client certificate from the TLS session, hands it to the usual `AuthenticationManager`, and populates the security context for the duration of the call so `@Secured` and `@PreAuthorize` on the service method behave as they do in a servlet application.

Reach for it when a Spring Boot service exposes gRPC and you want one authorization model across both protocols instead of hand-rolling interceptors. The mismatch to keep in mind is the request model: a streaming call is one authentication covering many messages, and the context is propagated per call rather than by a servlet thread-local, so anything reading the principal from an asynchronous or reactive continuation needs explicit propagation.

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

A `Guard` wraps the model call and runs validators over the output, and optionally the input, where each validator is a small installable component checking one property: valid JSON against a Pydantic schema, no PII, no competitor mentions, a value inside a range, no unsupported citation. What happens on failure is configurable per validator, and the interesting option is reasking, where the guard feeds the validation error back to the model for another attempt instead of simply rejecting.

Reach for it when LLM output feeds code that needs structure and constraints held, and you want those constraints declared and testable rather than buried in a prompt. The costs are real: reasking multiplies latency and tokens, several validators are themselves model calls, and validating a streamed response only completes once enough of it has arrived. For pure schema conformance a constrained-decoding library is cheaper.

### gVisor
**Short:** User-space kernel that intercepts container syscalls, isolating untrusted or agent-run workloads without a full VM.
**Kind:** tech
**Lang:** *
**Roles:** security/supply-chain-and-runtime-security @1, platform-delivery/container-and-image @2, llm-apps/agentic-environments @3, runtime-systems/memory-processes-and-os @3

`runsc` runs the container's processes but never lets them reach the host kernel directly. A user-space kernel called the Sentry implements the Linux syscall surface itself, intercepting the guest's syscalls and making only a small vetted set of host calls, while a separate Gofer process mediates filesystem access. The result is a second boundary between workload and host without booting a full virtual machine for every container.

Reach for it when running code you do not trust, such as multi-tenant functions, CI for untrusted pull requests, or an agent executing generated code, and a shared kernel is too thin a boundary. The costs are concrete: syscall-heavy and IO-heavy workloads slow measurably, and the syscall implementation is broad but incomplete, so some software simply will not run. Firecracker or Kata are the alternatives when you need a real kernel per workload.

### HarmBench
**Short:** Standardised red-team benchmark of 510 harmful behaviours across text, contextual, copyright and multimodal categories.
**Kind:** dataset
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1, ml-lifecycle/evaluation-and-benchmarks @2

It standardises the whole evaluation rather than only the prompts. Behaviours are grouped into functional categories, including standard requests, copyright, contextual behaviours that supply a document to work from, and multimodal, and success is decided by classifiers trained for the task instead of by checking whether the response contains a refusal phrase. Attacks and defences run through a common harness, so an attack success rate from one paper is comparable with another's.

Reach for it when you need a defensible red-team number across several attack methods and refusal-substring scoring is too crude to trust; the contextual behaviours in particular test the realistic case where harmful capability comes from supplied content rather than model memory. It is a static published set, so treat a good score as evidence about known attack families, not about attacks written specifically for your application.

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

The plugin sits between Helm and your values files: `helm secrets upgrade` decrypts the SOPS-encrypted values into a temporary file, passes it to Helm as an ordinary values argument, and cleans up afterwards, so the chart itself needs no knowledge of encryption. Because SOPS encrypts values while leaving keys and document structure in plaintext, review still shows which setting changed, and each file's data key is wrapped with a cloud KMS key, an `age` key or PGP so environments decrypt with different identities.

Reach for it when Helm is the deployment path and secrets should be versioned alongside the chart. The dependency chain is the caveat, since a Helm plugin wrapping SOPS wrapping a key manager must all exist on whichever machine or runner performs the release. In a GitOps flow where the cluster reconciles rather than a person running Helm, the SOPS integrations in Flux or Argo CD, or the External Secrets operator, fit better.

### IAM
**Short:** AWS Identity and Access Management: users, roles, federation and the JSON policies that grant every API action.
**Kind:** tech
**Lang:** *
**Roles:** security/authentication-and-identity @1, security/authorization-and-policy @2

Identities are users, groups and roles; permissions are JSON policies attached to an identity or to the resource itself, and evaluation is deny-by-default with an explicit `Deny` beating any `Allow`, further bounded by permission boundaries and organisation service control policies. Roles are the part that matters in design, because a role holds no credentials of its own and a principal assumes it through STS to receive temporary keys, which is how a service, another account or a federated user gets access with no stored secret.

The entire surface of AWS is one API guarded by this, so the discipline is granting the narrowest action and resource ARN that works and using conditions rather than wildcards. The recurring incidents are long-lived access keys sitting in files and repositories, which roles, instance profiles and IRSA exist to remove, and policies that read as scoped but are not, which the policy simulator and Access Analyzer are there to catch.

### IAM Identity Center
**Short:** AWS single sign-on service: central workforce identity, permission sets and federated access across accounts.
**Kind:** tech
**Lang:** *
**Roles:** security/authentication-and-identity @1, security/authorization-and-policy @2, platform-delivery/cloud-platform-and-cost @3

It is the workforce front door across many AWS accounts. Users come from its own directory or an external identity provider over SAML with SCIM provisioning, and a permission set, effectively a named policy bundle, is assigned to a group for a set of accounts. Identity Center then provisions the corresponding role into each account, so a person picks an account and role from a portal and receives short-lived credentials, with `aws sso login` doing the same for the CLI and SDKs.

Reach for it the moment there is more than one account, because the alternative is IAM users duplicated everywhere and access keys that outlive employment. It replaces what was called AWS SSO, so older documentation still uses that name. It governs human and cross-account access, while workloads should still take roles attached to the compute, whether instance profiles, task roles or IRSA.

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

The EKS cluster publishes an OIDC discovery document, and the pod's service account token is projected into the container as a signed JWT with the cluster as issuer. The AWS SDK finds that token file through environment variables an admission webhook injects, calls `sts:AssumeRoleWithWebIdentity`, and receives temporary credentials, so the role's trust policy conditions on the cluster's OIDC issuer plus the exact `sub` naming a namespace and service account, and no static key exists anywhere.

Use it for any pod that calls an AWS API, since the alternatives are node-wide instance-profile permissions inherited by every pod on the node, or access keys in a Secret. The traps live in the trust policy, where a wildcard in the `sub` condition grants the role to any service account in the cluster, and in token lifetime, which only a recent enough SDK refreshes. EKS Pod Identity is the newer mechanism that removes the per-cluster OIDC setup.

### JailbreakBench
**Short:** Jailbreak robustness benchmark: 100 policy-violating behaviours, a live adversarial-prompt repository and a leaderboard.
**Kind:** dataset
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1, ml-lifecycle/evaluation-and-benchmarks @2

It combines three things jailbreak research usually keeps apart: a fixed behaviour set with matching benign counterparts, so a defence's false-refusal rate is measured next to its block rate; an open repository of the adversarial prompts actually submitted, so a claimed attack can be rerun rather than merely cited; and a leaderboard whose entries declare the threat model, stating what access the attack assumed.

Reach for it when reproducibility is the point, because the artifact repository is what lets you test whether last quarter's successful attack still works after a model or filter change. The caveat common to public sets applies with extra force here, since the prompts are published and therefore leak into training data and filter rules: a good score is evidence about known attacks, not about the ones written for your application tomorrow.

### jarsigner
**Short:** JDK CLI that signs JAR files with a keystore key and verifies existing signatures and certificate chains.
**Kind:** tech
**Lang:** java
**Roles:** security/supply-chain-and-runtime-security @1, security/secrets-and-cryptography @2

It signs a JAR with a private key from a keystore, writing a signature block and per-entry digests into `META-INF`, and `jarsigner -verify -verbose -certs` checks those digests, the signature and the certificate chain. The `-tsa` option adds a timestamp from a timestamping authority, which is what keeps a signature valid after the signing certificate expires; without it, verification starts failing on the certificate's expiry date.

Use it where the platform genuinely checks signatures, such as artifacts consumed by a runtime that verifies them. Two things surprise people: a signature only covers entries listed in the manifest, so files added afterwards can slip in as unsigned content, and weak algorithms are progressively disabled by JDK security policy, so an old signature can stop verifying on a newer JDK. For container and general artifact signing, cosign with keyless identities is the modern path.

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

The shape is the same across AWS, Google Cloud and Azure: the SDK never hands you the key encryption key, so you ask the service to generate a data key, receive it both in plaintext and wrapped, encrypt locally with the plaintext copy, zero it, and persist the wrapped copy beside the ciphertext. Higher-level libraries such as the AWS Encryption SDK or Tink's KMS integration implement that envelope and its message format for you, including data-key caching so a hot path does not call the service per record.

Reach for this rather than an application-held key whenever custody, rotation or an audit trail is required, since every use of the wrapping key is logged and centrally revocable. Design around two costs: latency and per-key request quotas on decrypt-heavy paths, which caching addresses at the price of a wider blast radius per cached key, and availability coupling, because data not already unwrapped cannot be read while the service is unreachable.

### java-jwt
**Short:** Auth0's Java library for creating and verifying JSON Web Tokens with HMAC and RSA/EC signing algorithms.
**Kind:** tech
**Lang:** java
**Roles:** security/authentication-and-identity @1, security/secrets-and-cryptography @3

Creation and verification are both fluent: `JWT.create()` sets claims and calls `sign` with an `Algorithm`, while verification builds a `JWTVerifier` from an algorithm plus expected issuer and audience and then throws rather than returning a token you might use unchecked. The algorithm object carries the key, which is the design decision that closes the classic vulnerability, because the verifier cannot be talked into honouring the `alg` value inside an attacker-supplied token.

Reach for it in a plain Java or non-Spring service that issues or checks its own tokens. Fetching a provider's rotating public keys is not included and needs the companion JWKS client library selecting by the token's `kid`. In Spring Boot prefer the resource-server starter, which wires the same validation from an issuer URI, and remember that verifying a signature is not authorization: expiry, audience and scope are still checks you make deliberately.

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

The API is a builder and a parser: `Jwts.builder()` sets claims and signs with a `Key`, and parsing verifies with an explicit key before returning claims or throwing. It ships as three artifacts, an API jar plus a runtime implementation and a JSON binding, so application code compiles only against interfaces. Recent versions removed the string-key convenience methods that encouraged weak HMAC secrets and now refuse keys shorter than the algorithm requires.

Reach for it in a service that mints its own tokens and wants a small opinionated surface. It deliberately covers JWS and JWE rather than the wider JOSE ecosystem, so fetching an external provider's JWKS is better served by Nimbus, or by Spring Security's resource server built on top of it. Always parse with an explicit key and check issuer, audience and expiry, because a token that parses is not a token you have authorized.

### JWT
**Short:** Signed, self-contained token format carrying claims; the usual bearer credential for stateless API auth.
**Kind:** spec
**Lang:** *
**Roles:** security/authentication-and-identity @1, apis-frameworks/data-formats-and-api-contracts @3

Three base64url segments separated by dots: a header naming the signing algorithm and key id, a payload of claims such as `iss`, `sub`, `aud` and `exp` plus whatever your application adds, and a signature over the first two. Nothing is encrypted, so the payload is readable by anyone holding the token, and the signature is the only thing making it trustworthy. Verification means checking that signature with a key you chose, never the algorithm the token names for itself.

Reach for it when a resource server must authenticate a caller with no shared session store, which is what makes it the default for APIs and for OIDC. The property that makes it stateless is the same one that makes it awkward: a token stays valid until it expires and cannot be recalled, so logout and revocation need short lifetimes with refresh tokens, or a denylist that reintroduces the state you were avoiding. An opaque token with introspection is the alternative when instant revocation matters more.

### jwt.io
**Short:** Web tool for pasting a JWT to decode its header and payload and check the signature while debugging auth.
**Kind:** tech
**Lang:** *
**Roles:** security/authentication-and-identity @1

Pasting a token splits it into header, payload and signature and decodes the first two, which is enough to see immediately whether `iss`, `aud`, `exp` and your custom claims are what the code expects. Supplying the secret or public key also checks the signature, which turns a vague complaint that the API rejects a token into a definite answer about whether the problem is the signature, a claim, or the clock.

It is the fastest way to read a token during development, and the site also catalogues JWT libraries per language. Remember that a JWT is not encrypted and pasting one into a web page discloses everything inside it, so never paste a production or customer token: use a locally issued test token, or decode offline with a CLI, since base64url decoding needs no service at all.

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

The JDK's keystore utility: `-genkeypair` creates a key with a self-signed certificate, `-certreq` produces a CSR for a real authority to sign, `-importcert` brings the reply or a trusted root back in, and `-list -v` shows what a store holds including expiry dates. Stores are PKCS#12 by default on current JDKs with the older JKS format still readable, and the JDK's own trust anchors live in the `cacerts` file that `-importcert` extends when a private CA must be trusted.

Use it for Java-specific work such as populating a trust store, preparing a keystore a server or client will load, or inspecting a chain. Its syntax is verbose and inconsistent enough that people confuse it with OpenSSL, which is the better tool for inspecting PEM files and converting formats; the usual bridge is building a PKCS#12 with OpenSSL and importing it. A keystore password protects the file, not the process holding the key in memory.

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

It is a single hosted classification call placed around your model: send the user prompt, the retrieved context or the model's response and it returns detections for prompt injection and jailbreak attempts, PII, unsafe content and data leakage, leaving enforcement to you. The detection models are trained on a large corpus of real attack attempts, including those collected from the company's public prompt-injection game, which is what distinguishes it from a regex or a general moderation endpoint.

Reach for it when you want injection detection you do not train or host and low added latency matters more than owning the model. Two things stay in view: it is a commercial API, so every screened request leaves your boundary and is billed, and injection detection is probabilistic and always defeatable by a novel phrasing, so it belongs alongside least-privilege tool design and human approval on consequential actions rather than in place of them.

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

It is a portable packaging of NaCl with the same premise: no algorithm negotiation, no mode or padding to choose, one obvious function per task. `crypto_secretbox` gives authenticated symmetric encryption, `crypto_box` does authenticated public-key encryption over Curve25519, `crypto_sign` uses Ed25519, and `crypto_pwhash` is Argon2id. Implementations are constant-time, and it supplies the pieces people forget, including secure memory zeroing and constant-time comparison.

Reach for it whenever application code needs cryptography and you can choose the library, because the API removes the decisions that produce vulnerabilities: there is no way to select ECB or forget the MAC. Bindings exist for nearly every language, so a polyglot system can share one construction. It does not do X.509, TLS or JWT, so certificate handling still goes to OpenSSL and token work to a JOSE library.

### Llama Guard
**Short:** Meta's open safety classifier for input/output moderation against the MLCommons hazard taxonomy.
**Kind:** model
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1, model-training/alignment-and-rl @3

It is a fine-tuned Llama used as a classifier rather than a chat model. You send a prompt template containing the hazard taxonomy and the conversation, and it replies with a safe or unsafe verdict followed by the category codes violated. Because the taxonomy lives in the prompt rather than the weights, you can add, remove or reword categories to match your own policy without retraining, and classifying a user prompt and a model response are two separate tasks.

Reach for it when moderation must run inside your own boundary, when a hosted API is unacceptable, or when the category set has to be yours. The cost is a second model on the request path, meaning real GPU memory and latency in both directions, which is why smaller guard models exist. Being a generative classifier, its output should be parsed defensively, and only a locally labelled set tells you where its thresholds sit for your traffic.

### Llama Guard 4 12B
**Short:** Meta's open multimodal safety classifier that labels prompts and responses against a hazard taxonomy in eight languages.
**Kind:** model
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1

This generation is multimodal and dense, so a single model classifies text and images against the same hazard taxonomy supplied in an editable prompt template, replying with a verdict plus the violated categories. Handling images matters because a policy enforced only on text is bypassed by putting the request in a picture, and running one model across both modalities avoids maintaining two separate thresholds.

Deploy it as the input and output filter around a multimodal application whose content must not leave your infrastructure. Budget honestly: a 12B model in front of and behind every turn is significant GPU memory and added latency, and the taxonomy it was trained on is a general hazard set, so product-specific rules need taxonomy edits or a separate classifier. Measure its false-refusal rate on your own traffic, not only its catch rate.

### Llama Prompt Guard 2
**Short:** Meta classifier (86M/22M) that flags prompt injection and jailbreak attempts, ~97.5% recall at 1% FPR.
**Kind:** model
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1

Unlike a generative guard model this is a small encoder classifier, released in two sizes, that labels a string as an injection or jailbreak attempt. Being small is the design: it is cheap enough to run over every user message and every retrieved chunk, which is the placement that matters, since the dangerous instruction usually arrives inside a document or a tool result rather than typed by the user. The smaller variant exists for latency-critical and CPU-only paths.

Reach for it as the always-on first filter in a retrieval or agent pipeline where a large guard model per turn is unaffordable. It flags text as suspicious, which is not the same as knowing whether an instruction is legitimate in context, so expect false positives on content that legitimately discusses instructions, and treat it as cost-raising rather than a boundary. Quarantined models, scoped tools and approval on destructive actions are what actually contain an injection that slips through.

### LLM Guard
**Short:** Open-source scanner suite for LLM input and output: prompt injection, toxicity, PII, secrets and topic bans.
**Kind:** tech
**Lang:** python
**Roles:** security/ai-safety-and-guardrails @1, security/privacy-and-compliance @3

It is a library of composable scanners rather than one model: input scanners for prompt injection, banned topics, secrets, token limits, invisible characters and language, and output scanners for toxicity, sensitive data, relevance to the prompt, malicious URLs and code. Each returns sanitised text, a verdict and a risk score, so you compose several and decide the policy yourself, and the anonymize scanner can vault the values it removes and restore them in the response.

Reach for it when guardrails must run inside your own infrastructure, with no user text leaving the boundary and no per-call bill. The cost is that several scanners are transformer models, so an enabled stack adds real latency and memory per turn and should be enabled selectively after measurement. As with any classifier-based defence, treat it as a filter that raises an attacker's cost while tool scoping and human approval do the containment.

### llm-attacks
**Short:** Reference codebase for greedy coordinate gradient jailbreaks; the basis of most white-box attack research.
**Kind:** tech
**Lang:** python
**Roles:** security/ai-safety-and-guardrails @1

This is the reference implementation of greedy coordinate gradient. It optimises a short adversarial suffix appended to a harmful request by using gradients with respect to one-hot token inputs to propose candidate substitutions at each position, then evaluating a batch of those candidates exactly and keeping the best, with the objective of maximising the probability of an affirmative opening. Optimising against several models and prompts at once yields suffixes that transfer to models whose weights you never had.

Reach for it as the canonical white-box attack when you hold weights and want a worst-case measurement rather than a plausible one. Two properties bound it: gradients are required, so a hosted model is only reachable through transfer, and the suffixes are high-perplexity gibberish that a simple filter can flag, which is exactly why fluent attacks such as AutoDAN belong in the same suite.

### Microsoft Entra ID
**Short:** Microsoft's cloud identity provider: OIDC/SAML sign-in, conditional access and workload identities.
**Kind:** tech
**Lang:** *
**Roles:** security/authentication-and-identity @1

The service issues OIDC and SAML tokens from a tenant holding users, groups, devices and application registrations, and a workload identity, whether a managed identity on Azure resources or a federated credential for something outside, lets code authenticate with no secret in configuration. Conditional access evaluates policy at sign-in against signals such as device compliance, location and detected risk, which is how step-up authentication is applied selectively rather than to everyone.

Reach for it wherever the organisation already runs Microsoft 365, since the directory, groups and joiner-mover-leaver process are in place and adding an application is a registration. The friction is administrative rather than technical: the rename from Azure AD leaves inconsistent naming across tools and documentation, the legacy directory APIs have given way to Microsoft Graph, and application versus delegated permissions with admin consent is what most often blocks a first integration.

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

SEAL implements two schemes with different arithmetic. BFV works on exact integers modulo a plaintext modulus and suits counting and exact sums, while CKKS works on approximate fixed-point reals, which is what makes machine-learning inference on ciphertext feasible at all. Both are lattice-based and every operation adds noise, so you choose a polynomial modulus degree and a chain of coefficient moduli that buy a fixed multiplicative depth, and the computation must finish inside that budget.

Reach for it when a computation must run somewhere you do not trust with the plaintext and the function is a shallow arithmetic circuit. State the costs plainly: ciphertexts are orders of magnitude larger than plaintexts, operations are far slower than cleartext arithmetic, comparisons and branching are not natural operations, and SEAL does not implement bootstrapping, so unbounded depth is out of scope and OpenFHE is the library that covers it.

### mkcert
**Short:** CLI that installs a local CA and issues trusted development TLS certificates so localhost can run real HTTPS/HTTP2.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, devtools/version-control-and-workbench @3

It generates a local certificate authority once, installs that CA into the system trust store and into the browser stores that keep their own, then issues leaf certificates for whatever names you ask for, including `localhost`, `127.0.0.1` and wildcard development domains. Because the CA is trusted locally, the browser shows an ordinary padlock with no exception to click, so features gated behind a secure context such as service workers and HTTP/2 work in development.

Reach for it to make local development match production transport instead of debugging TLS only after deploying. Two rules apply: the CA private key on your machine can mint a certificate for any domain, so protect it and never copy it to a shared or CI machine, and nothing it issues is trusted by anyone else, so it is strictly for development. Use Let's Encrypt for real certificates and cert-manager with a private issuer for an internal CA at scale.

### MLCommons AILuminate
**Short:** Cross-language hazard benchmark: 12 categories with tens of thousands of natively authored prompts per language.
**Kind:** dataset
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1, ml-lifecycle/evaluation-and-benchmarks @2

The benchmark asks whether a system responds safely to prompts across a set of hazard categories, and the design detail that makes it usable is the split between a public practice set and a held-out official set, so a vendor can iterate without the graded prompts leaking into training. Responses are graded by an ensemble of evaluator models rather than a single judge, and results are reported as a grade relative to reference systems instead of a bare percentage that means nothing on its own.

Reach for it when you need a third-party comparable safety result for a chat-facing system, such as a procurement conversation, a vendor comparison or a public claim. Its limits follow from being a standardised benchmark: it measures conversational hazards against a fixed taxonomy, not agent tool misuse, not your application's own abuse cases, and not a determined multi-turn attacker.

### ModelScan
**Short:** Scanner that inspects serialized model files for unsafe deserialization and code-execution payloads.
**Kind:** tech
**Lang:** python
**Roles:** security/supply-chain-and-runtime-security @1, security/ai-safety-and-guardrails @2

Loading a model file is code execution in disguise, because Python pickle, which PyTorch checkpoints and many scikit-learn artifacts use, can name arbitrary callables to invoke during deserialization. ModelScan reads the file without executing it, walking the opcodes or graph for the format at hand, whether a pickle-based checkpoint, an HDF5 Keras model or a TensorFlow SavedModel, and reports the operations capable of running code with a severity per finding.

Run it on anything downloaded from a model hub or received from a third party, in CI before the artifact is promoted, because a poisoned checkpoint executes with the privileges of your training or serving process. It is a static scanner, so it recognises known dangerous patterns and can be evaded by a novel one; the durable fix is preferring formats that cannot carry code, above all safetensors, and loading untrusted artifacts in a sandbox.

### ModSecurity
**Short:** Open-source web application firewall module for nginx/Apache, usually run with the OWASP Core Rule Set.
**Kind:** tech
**Lang:** *
**Roles:** security/supply-chain-and-runtime-security @1, traffic-edge/proxy-and-load-balancer @2

The engine parses an HTTP transaction into phases covering request headers, request body, response headers, response body and logging, and evaluates `SecRule` directives against variables at each phase, applying transformations before matching so an attack cannot hide behind URL encoding or case. In practice nobody writes the rules: the OWASP Core Rule Set is what gets deployed, scoring each match into an anomaly total and blocking only when the total crosses a threshold, with paranoia levels trading detection against false positives.

Reach for it when you terminate TLS on your own nginx or Apache and want rule-based filtering without a cloud WAF. Start in detection-only mode and tune exclusions against real traffic, because the Core Rule Set at default paranoia will block legitimate requests carrying JSON, base64 or free text. The project is now maintained under OWASP after its original vendor stepped back, body inspection costs memory and latency, and none of it substitutes for parameterised queries and output encoding.

### Mozilla Observatory
**Short:** Online scanner grading a site's HTTP security headers, TLS configuration and cookie flags.
**Kind:** tech
**Lang:** *
**Roles:** security/supply-chain-and-runtime-security @1, apis-frameworks/web-framework-and-http-client @3

You give it a hostname and it fetches the site, then scores the response against a published rubric weighted heavily toward Content-Security-Policy, followed by HSTS, `X-Content-Type-Options`, frame protection, referrer policy, cookie flags including `Secure`, `HttpOnly` and `SameSite`, subresource integrity and cross-origin resource sharing. Each check adds or subtracts points from a score that maps to a letter grade, and every finding links to an explanation of why it matters.

Use it as a fast external check after a deployment and as a way to make header work legible to people who do not read configuration files. Understand what the grade is: a measurement of declared browser-side defences on one URL, not a penetration test, since an application with perfect headers can still have broken authorization or an injectable query. The CSP portion in particular rewards a strict policy, which is an engineering project rather than a header you paste.

### MySQL Audit Plugin
**Short:** MySQL Enterprise plugin writing a tamper-evident audit log of connections and statements for compliance review.
**Kind:** tech
**Lang:** *
**Roles:** security/privacy-and-compliance @1, observability/logging @3, data-stores/relational @3

The plugin hooks the server's audit API and writes a record for connection, login and query events, so the log is produced by the server rather than reconstructed from the general query log. Filters decide what is captured by user, event class or database, which is the difference between a usable audit trail and gigabytes of noise, and output is XML or JSON to a file that can be rotated and shipped. The commercial Enterprise plugin is the canonical one, with comparable audit plugins shipped by MariaDB and Percona for community servers.

Reach for it when an auditor needs evidence of who connected and what they ran against a database holding regulated data. Plan the cost, since auditing every statement on a busy server adds latency and produces large volume, so filter down to the tables and privileged accounts that matter. And a log written by the server can be altered by whoever holds the host, so ship it off the machine to a write-once destination if it must be trusted.

### NaCl
**Short:** Networking and Cryptography library - the original opinionated C primitives (Curve25519, XSalsa20) behind libsodium.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1

This is where the modern opinionated style started: a handful of functions with no algorithm negotiation, no configuration, and primitives chosen by the authors, namely Curve25519 for key agreement, XSalsa20 for the stream cipher, Poly1305 for authentication and Ed25519 for signatures. Implementations avoid data-dependent branches and table lookups, so timing side channels are addressed by construction rather than by careful use.

In practice you use libsodium, the portable packaging with bindings, build support and extra functions such as Argon2id password hashing; the original is worth knowing as the design that libsodium and much of modern protocol work inherited. The tradeoff of the whole approach is deliberate: no negotiation means no downgrade attacks and also no interoperability with a peer expecting something else, for which you need TLS or a JOSE library.

### nanoGCG
**Short:** Compact reimplementation of the GCG adversarial-suffix attack, used to automate red-teaming of aligned models.
**Kind:** tech
**Lang:** python
**Roles:** security/ai-safety-and-guardrails @1

A compact readable implementation of the greedy coordinate gradient attack: it appends a suffix of optimisable tokens to a harmful prompt, takes the gradient of the loss with respect to one-hot token embeddings to propose candidate replacements at each position, evaluates a batch of those candidates exactly, keeps the best, and repeats until the model begins its response with the target affirmative string. Practical touches such as multi-position updates and probe sampling make runs cheaper than the original.

Reach for it when you hold model weights and want a reproducible worst-case jailbreak measurement, or a baseline attack to test a defence against. It requires white-box gradient access, consumes GPU time per behaviour, and produces high-perplexity gibberish suffixes that a perplexity filter can catch, so pair it with fluent attacks such as AutoDAN if the defence should be evaluated against both shapes.

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

The method assumes a backdoored classifier has a small trigger that forces one label. For every output class it solves an optimisation for the minimal perturbation making any input classify as that class, then compares the sizes: in a clean model those minimal perturbations are all roughly comparable, while a trojaned model has one class reachable by a suspiciously tiny pattern. An outlier test over the per-class norms flags it, and the recovered pattern is itself the trigger, usable to filter inputs or patch the model.

Reach for it when you inherit a model you cannot audit the training data for, such as a pretrained checkpoint or a supplier's artifact. Know the assumptions before trusting a clean result: it targets image classifiers with a manageable number of classes and small localised static triggers, and later work built backdoors that are large, distributed or input-dependent specifically to evade it. A negative result is weak evidence, not a clean bill of health.

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

The library implements the full JOSE family in Java: JWS signing and verification, JWE encryption, JWK and JWK-set parsing, and JWT claims handling on top, with a processor object you configure with the algorithms you accept and the claim checks you require. The remote key source and its caching selector are the piece that matters in service code, fetching an issuer's keys over HTTPS, selecting by the token's `kid` and refreshing on an unknown one, which makes provider key rotation a non-event.

You most often meet it indirectly, since Spring Security's resource server builds its decoder on it; reach for it directly when you need JWE, nested tokens or JWK manipulation the framework does not expose. As always the security lives in the verification configuration: pin the accepted algorithms and check issuer, audience and expiry instead of trusting a token because its signature parsed.

### Nimbus JOSE+JWT
**Short:** Java JOSE library for JWT signing/verification and JWKS fetching; the default under Spring Security OAuth2.
**Kind:** tech
**Lang:** java
**Roles:** security/authentication-and-identity @1, security/secrets-and-cryptography @2

It is the reference-quality Java implementation of JWS, JWE, JWK and JWT, offering builders for creating tokens, verifiers per algorithm family, and a claims-set verifier for the registered claims. The part that earns its place in server code is JWKS handling: a remote key source that caches an issuer's published keys, selects the right one by `kid`, and refuses to fall back to whatever key the token itself suggests.

Reach for it directly when you need encrypted or nested tokens, or key handling beyond verifying a bearer token; in a Spring application you are already using it, because the resource-server support is built on top. Configure the accepted algorithm list explicitly and validate issuer and audience, since the common production bug is accepting any signature the library can verify rather than only the ones you intended.

### nimbus-jose-jwt
**Short:** Java JOSE library for signing, encrypting and verifying JWTs; used internally by Spring Security.
**Kind:** tech
**Lang:** java
**Roles:** security/authentication-and-identity @1, security/secrets-and-cryptography @2

The Maven coordinate for the library underpinning Spring Security's JWT support: JWS, JWE, JWK sets, and a processor you configure with a key source, the algorithms you accept and the claims you require. Because it handles JWKS retrieval, caching and `kid` selection, a resource server survives an identity provider rotating its signing keys without a redeployment or a restart.

You rarely add it deliberately, since it usually arrives transitively, but pull it in directly when you need JWE, an unusual algorithm, or programmatic JWK handling. Watch the version, because it is a security-sensitive transitive dependency and an old pinned copy is worth checking against advisories, and always verify issuer, audience and expiry rather than treating a parsed token as an authorized one.

### NIST AI RMF
**Short:** US government AI Risk Management Framework: a voluntary structure for governing, mapping and measuring AI risk.
**Kind:** spec
**Lang:** *
**Roles:** security/privacy-and-compliance @1, security/ai-safety-and-guardrails @2

The framework organises AI risk work into four functions, with govern as the cross-cutting one establishing policies, roles and accountability, and map, measure and manage covering context and risk identification, measurement including evaluation and monitoring, and prioritised response. It is voluntary and not a certification: it describes outcomes to achieve rather than controls to implement, and a companion profile addresses generative AI specifically.

Reach for it when you need a common vocabulary and structure for AI governance that regulators, auditors and customers already recognise, and as scaffolding for an internal policy you would otherwise invent from scratch. Its weakness is the flip side of its strength, since it will not tell you which evaluation to run or what threshold is acceptable, so the measure function is where the real engineering sits and where a mapped framework can quietly become paperwork.

### OAuth 2.1
**Short:** Consolidated OAuth framework: PKCE required, implicit and password grants dropped; the auth basis for remote MCP.
**Kind:** spec
**Lang:** *
**Roles:** security/authentication-and-identity @1, security/authorization-and-policy @2, llm-apps/tool-use-and-mcp @3

It is a consolidation rather than a new protocol, folding the accumulated best-practice documents into one specification. PKCE becomes mandatory for every authorization-code request rather than a public-client extra, redirect URIs must match exactly instead of by pattern, the implicit and resource-owner-password grants are removed, bearer tokens may not travel in query strings, and refresh tokens must be sender-constrained or rotated on use.

Treat it as the checklist for any new OAuth deployment and as the baseline that newer specifications, including the authorization model for remote MCP servers, assume. Nothing in it is unavailable in OAuth 2.0 with the right extensions, so the practical question is whether your provider and client libraries default to these behaviours. The two migrations that hurt are single-page applications still using implicit and legacy clients still posting usernames and passwords to the token endpoint.

### OIDC
**Short:** OpenID Connect: an identity layer over OAuth 2 issuing ID tokens; also used for keyless CI-to-cloud auth.
**Kind:** spec
**Lang:** *
**Roles:** security/authentication-and-identity @1, platform-delivery/ci-cd-and-release @3, security/secrets-and-cryptography @3

OIDC adds identity to OAuth 2.0's delegated authorization. Alongside the access token the provider returns an ID token, a JWT carrying `iss`, `sub`, `aud`, `exp`, `iat` and a `nonce` echoing the one you sent, which the client verifies against keys from the provider's JWKS, discovered together with every endpoint from the well-known configuration document. The distinction that matters is that an access token is for calling an API while the ID token is for you, the client, to learn who signed in.

Use it for any sign-in flow rather than inventing one, and note the second life it has acquired: CI systems and Kubernetes clusters present OIDC tokens to cloud providers and exchange them for short-lived credentials, which is how keyless deployment pipelines removed their static keys. The recurring implementation bugs are checking the signature but not the audience, skipping the `nonce`, and treating an access token as proof of identity.

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

Rego is packaged as Kubernetes objects. A `ConstraintTemplate` contains the policy and defines a CRD with a typed parameter schema, and a `Constraint` of that kind supplies the parameters and the match criteria of kinds, namespaces and label selectors that decide where it applies. A validating webhook enforces on admission while a periodic audit re-evaluates objects already in the cluster and records violations in status, which is how you discover what a new rule would have blocked.

Reach for it when Rego is already your policy language across CI and application authorization and one engine everywhere is worth something. Roll out with the dry-run enforcement action and read the audit before switching to deny, because pre-existing workloads will fail a rule written today. Against Kyverno you gain expressive power and pay with learning Rego and the two-object indirection; mutation exists but arrived later and is less central.

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

One engine, one contract: JSON in as `input`, policy in Rego, JSON decision out. Because policy is data evaluated against a document rather than code compiled into a service, the same deployment answers a microservice's authorization question through a sidecar or a library, a Kubernetes admission request through Gatekeeper, a Terraform plan check in CI through Conftest, and a tool-use decision for an agent. Bundles let a policy change roll out by upload rather than by redeploying services.

Reach for it when authorization rules should be versioned, unit-tested and reviewed independently of the services enforcing them, and when one language across admission control and application policy is worth the investment. Two costs: Rego's evaluation model rewards learning it properly rather than guessing, and the harder half remains assembling the `input`, since the engine can only decide over the roles, ownership and tenancy context you supply.

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

The successor to PALISADE, OpenFHE implements the main lattice schemes in one library, with BFV and BGV for exact integer arithmetic, CKKS for approximate real arithmetic, and the boolean schemes DM and CGGI for arbitrary gate evaluation and lookup tables. It supports bootstrapping, so a computation is not confined to a fixed multiplicative depth, and scheme switching to move between arithmetic and boolean styles inside one workload.

Reach for it when the computation is deeper than a leveled scheme allows or needs comparisons and branching that arithmetic-only schemes cannot express; for a shallow inference circuit, SEAL through TenSEAL is a much smaller thing to adopt. The costs remain those of fully homomorphic encryption: ciphertext expansion of orders of magnitude, operations far slower than plaintext, bootstrapping expensive enough to dominate a run, and parameter selection that requires understanding the noise budget rather than accepting a default.

### OpenMined PySyft
**Short:** Privacy-preserving ML toolkit covering federated learning, differential privacy, secure MPC and homomorphic encryption.
**Kind:** tech
**Lang:** python
**Roles:** security/privacy-and-compliance @1, model-training/distributed-training @3

The current model is remote data science. A data owner runs a datasite publishing only mock data that describes the schema, a scientist writes and submits code against that mock, and the owner reviews and approves it before it executes over the real data, returning only the approved result. That approval boundary is the actual mechanism, with differential privacy applied to outputs and federated learning, secure multi-party computation and homomorphic encryption in the surrounding ecosystem.

Reach for it when the collaboration itself is the problem, such as a hospital or a bank that cannot hand over data but can permit a vetted computation over it. Expect a research-grade platform whose API has changed substantially across versions, and note that a human reviewing submitted code is both a real workflow cost and the genuine security boundary. For training on data you already hold, Opacus or TensorFlow Privacy address the narrower problem directly.

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

The interception points are the span processor and the exporter. A custom processor sees each span as it starts or ends and can drop, hash or truncate attributes before it is queued, and the same can be done centrally in the OpenTelemetry Collector, where attribute and redaction processors match keys and patterns across every service at once. For LLM applications the sensitive fields are the obvious ones, namely prompt and completion bodies, tool arguments, retrieved documents and user identifiers, which are exactly what the semantic conventions encourage recording.

Add this before turning on rich tracing in anything handling personal data, because a trace backend is a searchable widely readable copy of whatever you put in it, usually under different retention rules from your database. Do it in the Collector when you want one policy a new service cannot forget, and in the SDK when the data must never leave the process. Either way redaction is pattern matching, so sample real traces and check what actually got through.

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

You give it a request, whether a URL, a saved HTTP request file or a proxy log, and it fingerprints the backend and works through the injection techniques: boolean-based blind, time-based blind, error-based, UNION query, stacked queries and out-of-band. Once a parameter is confirmed it enumerates databases, tables and columns, dumps data, and on permissive configurations reads or writes files and opens an operating system shell.

Its real use is proving exploitability, turning a scanner's tentative finding into an argument that ends when the tool returns actual table names. Two cautions matter more than usual: it sends destructive and heavy traffic, so run it against a test environment with written authorisation and never casually against production, and the fix it motivates is always the same, parameterised queries rather than an escaping helper or a firewall rule.

### OWASP ZAP
**Short:** OWASP's DAST scanner: crawls and attacks a running application to find injection, XSS and auth flaws.
**Kind:** tech
**Lang:** *
**Roles:** security/supply-chain-and-runtime-security @1, devtools/testing-and-mocking @3

It is both an intercepting proxy and a scanner. Traffic routed through it is passively checked for issues visible in the response, such as missing headers, cookie flags and information leakage, while the active scanner replays requests with attack payloads to test for injection, cross-site scripting and authentication flaws. A traditional spider plus an AJAX spider driving a real browser build the site map first, and the automation framework turns a scan into a YAML-defined job that runs headless in CI.

Reach for it as the free scriptable DAST option, typically a baseline scan on every build with a fuller authenticated scan on a schedule. The work is in configuration rather than in running it, because without authentication and session handling set up it scans little more than the login page, and a crawler misses much of a modern single-page application. Being dynamic it exercises real behaviour but only what it can reach, so pair it with static analysis and dependency scanning.

### PAIR
**Short:** Prompt Automatic Iterative Refinement: an attacker LLM iteratively rewrites a prompt to jailbreak a target model.
**Kind:** concept
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1

Two language models talk about a third. An attacker model is given the target behaviour, produces a candidate prompt, sees the target's response and a judge model's score, and rewrites the prompt, so each turn is informed by how the target actually replied. It needs only black-box query access and typically converges within tens of queries, which is what distinguishes it from gradient search over tokens.

Include it in a red-team suite because it models the realistic attacker: no weights, few queries, and semantically fluent prompts that a perplexity filter cannot flag. Its variability is the practical annoyance, since results depend on the attacker and judge models and on the seed, so run several trials rather than quoting one, and inspect the judge's definition of a successful jailbreak because it sets the number you will report.

### Palo Alto Prisma AIRS
**Short:** AI security suite combining model supply-chain scanning with runtime prompt-injection protection.
**Kind:** tech
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1, security/supply-chain-and-runtime-security @2

The suite spans a lifecycle rather than one control point: scanning models and their dependencies for unsafe serialization and tampering before deployment, posture management over the AI services and data an organisation has connected, automated red teaming against a deployed application, and a runtime layer inspecting prompts and responses for injection, sensitive data and malicious URLs, delivered as an API or through the network security stack. Much of the model-scanning and posture capability comes from the Protect AI technology Palo Alto acquired.

Reach for it in an enterprise standardising AI security centrally, particularly where the network security platform is already Palo Alto and one policy plane beats best-of-breed pieces. The tradeoffs are commercial and architectural, covering licensing, inline inspection latency, and the limit every generic product shares: it does not know your application's own abuse cases, so application-level authorization and scoped tool permissions remain yours to design.

### Perspective API
**Short:** Google Jigsaw's hosted toxicity scoring API returning per-attribute scores (toxicity, insult, threat) for text.
**Kind:** tech
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1

You post a comment and it returns a probability-like score between zero and one for each requested attribute, including toxicity, severe toxicity, identity attack, insult, profanity and threat, where the score estimates the fraction of readers who would find the text toxic rather than delivering a verdict. It is free within a rate limit, built for comment moderation at scale, and covers a number of languages with varying quality.

Reach for it to triage a user-content queue, ranking or hiding the worst material so human moderators see less of it. Two documented weaknesses shape any deployment: the models score mentions of identity terms and some dialects higher regardless of intent, so an unexamined threshold moderates some groups more aggressively, and toxicity is not the same as policy violation, so spam, self-harm, coordinated harassment and your own product rules need separate handling.

### pgAudit
**Short:** PostgreSQL extension emitting detailed session and object audit log entries for compliance evidence.
**Kind:** tech
**Lang:** *
**Roles:** security/privacy-and-compliance @1, observability/logging @2, data-stores/relational @3

Loaded through `shared_preload_libraries`, it emits audit entries into the normal PostgreSQL log in two modes. Session auditing logs statements by class, such as read, write, ddl, role and function, for a role or the whole server, while object auditing grants a dedicated audit role permissions on specific tables so only statements touching those objects are logged. Entries are structured with the class, statement and parameters, so they can be parsed rather than grepped.

Reach for it when an auditor needs to see who read or changed regulated data, which the standard statement-logging setting cannot express at that granularity. Prefer object-level auditing, because logging every statement on a busy database produces volume that costs real IO and fills disks quickly. And the log is written wherever the database writes its logs, so ship it to a separate append-only destination if a database superuser must not be able to edit it.

### picklescan
**Short:** Scanner that inspects pickle and PyTorch checkpoint files for known malicious opcodes before you load them.
**Kind:** tech
**Lang:** python
**Roles:** security/supply-chain-and-runtime-security @1

Rather than unpickling, it disassembles the pickle stream and inspects which globals the opcodes reference, flagging imports known to enable code execution such as `os`, `subprocess`, `builtins.eval` and reduce-based tricks. It understands the containers these files arrive in, since a PyTorch checkpoint is a zip with pickles inside and numpy and joblib artifacts are similar, so it inspects the payload rather than only the wrapper.

Run it before loading any checkpoint from a hub or a stranger, and in CI as a gate on artifacts entering your registry, because loading an untrusted pickle is remote code execution by design. Take the result for what it is, a denylist of known-dangerous references that a determined author can evade. The durable answer is converting to and requiring safetensors, which stores tensors only and cannot carry code.

### pip-audit
**Short:** Scans installed Python packages against OSV and the PyPI Advisory DB; run in CI to block deploys on high CVEs.
**Kind:** tech
**Lang:** python
**Roles:** security/supply-chain-and-runtime-security @1, devtools/build-and-dependency-management @3

Maintained by the Python Packaging Authority, it resolves what is actually installed, whether the current environment, a requirements file or a lock, and queries the OSV database and the PyPI advisory feed for known vulnerabilities in those exact versions, printing the affected package, the advisory id and the fixed version. A fix mode upgrades to that version, and machine-readable output makes it a CI gate.

Reach for it as the default free CVE check on Python dependencies, since it is a small tool over public advisory data with no account required. Two things to plan for: it reports vulnerable versions present rather than vulnerable call paths you reach, so triage remains human work, and resolving a requirements file involves installing to resolve it, which is why auditing the built environment or a lock file is the more predictable pipeline step.

### PipelineDP
**Short:** Library adding differentially private SUM/COUNT/MEAN aggregations to Beam and Spark pipelines.
**Kind:** tech
**Lang:** python
**Roles:** security/privacy-and-compliance @1, data-movement/batch-and-distributed-compute @2

It brings differentially private aggregation to distributed pipelines running on Beam, Spark or locally, and handles the two things that make DP hard at scale: bounding how many partitions one privacy unit may contribute to and how much it contributes within each, and applying private partition selection so the mere existence of a rare key does not leak. You declare the privacy unit, the budget and the value bounds, and it applies calibrated noise and tracks the spend.

Reach for it when a recurring metrics job over per-user data must produce releasable numbers rather than an internal dashboard. The utility cost is where the design happens, since tight contribution bounds add bias while loose ones add noise, and partitions with few users may be dropped entirely, so decide up front which slices must survive. For budget accounting outside a pipeline use dp-accounting, and for private model training Opacus or TensorFlow Privacy.

### PKCE
**Short:** OAuth extension binding an authorization code to a per-request verifier, blocking code interception on public clients.
**Kind:** spec
**Lang:** *
**Roles:** security/authentication-and-identity @1

Before redirecting, the client generates a random `code_verifier`, hashes it with SHA-256, and sends the base64url result as `code_challenge` with `code_challenge_method=S256`. When it later exchanges the authorization code it must present the original verifier, which the server hashes and compares. An attacker who intercepts the code, from a mobile custom-scheme redirect, a browser log, or a malicious app claiming the same scheme, cannot redeem it without the verifier, which never left the client.

Use it on every authorization-code flow rather than only for public clients, since OAuth 2.1 makes it mandatory and it costs one hash. Two details decide whether it works: use `S256` and not `plain`, which sends the verifier in the clear and protects nothing, and generate the verifier from a cryptographically secure source of adequate length. It defends the code exchange only, so exact redirect-URI matching and state validation are still required.

### Pod Security Admission
**Short:** Kubernetes built-in admission controller enforcing Pod Security Standards per namespace in enforce/audit/warn.
**Kind:** tech
**Lang:** *
**Roles:** security/authorization-and-policy @1, platform-delivery/kubernetes-and-orchestration @2, security/supply-chain-and-runtime-security @2

Built into the API server, it applies the three Pod Security Standards, privileged, baseline and restricted, to pods at admission, and you select the level per namespace with labels, independently for each of three modes: enforce rejects, audit records into the audit log, and warn returns a message to whoever applied the object. Restricted is the meaningful one, requiring non-root execution, no privilege escalation, a seccomp profile and all capabilities dropped.

Reach for it as the zero-dependency baseline for pod hardening, since it replaced PodSecurityPolicy, which was removed. The design deliberately gives nothing beyond the three fixed levels: no per-workload exemptions inside a namespace, no custom rules, no mutation to fix a pod that nearly complies. Label namespaces with warn and audit first and read what would break, then enforce; for policy past the standards, Kyverno or Gatekeeper sit alongside it rather than replacing it.

### policy-controller
**Short:** Sigstore Kubernetes admission controller verifying image signatures and attestations before a pod is allowed to run.
**Kind:** tech
**Lang:** *
**Roles:** security/supply-chain-and-runtime-security @1, security/authorization-and-policy @2, platform-delivery/kubernetes-and-orchestration @2

The Sigstore admission controller checks image provenance before a pod runs. A `ClusterImagePolicy` matches images by glob and states what must be true of them, whether a signature from a given public key or, in keyless mode, a Fulcio certificate whose identity and issuer match an expected workflow, plus optional attestations such as an SBOM or provenance evaluated against a policy. Matching images that fail are rejected, and the controller can resolve tags to digests so the thing admitted is the thing verified.

Deploy it wherever image signing is meant to be an actual control, because a signature nobody verifies changes nothing. Two operational realities: verification needs network access to the registry and transparency log, so plan for that path being unavailable, and start in warn mode with a narrow glob, since a cluster-wide policy will immediately block unsigned third-party images. Kyverno's image verification covers similar ground if you already run it.

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

Prowler runs a large library of individual checks against a cloud account using read-only credentials, with hundreds for AWS and growing coverage for Azure, Google Cloud and Kubernetes, and each finding names the resource, the severity and the remediation. Checks are grouped into compliance frameworks, so one scan reports against CIS benchmarks and against control sets such as PCI, HIPAA, SOC 2 or GDPR, with output to JSON, CSV, HTML or straight into Security Hub.

Reach for it for an honest posture baseline and for evidence at audit time, especially across many accounts, since it is open source and needs no agent. Understand what it measures, namely configuration as it exists now rather than exploitability or runtime behaviour, so a long findings list needs triage and per-check suppression with a documented reason. A managed posture product buys continuous monitoring and drift alerting that a scheduled scan does not.

### pwdlib[argon2,bcrypt]
**Short:** Maintained Python password-hashing front end; PasswordHash.recommended() is argon2id and rehashes legacy hashes.
**Kind:** tech
**Lang:** python
**Roles:** security/secrets-and-cryptography @1, security/authentication-and-identity @2

This is the maintained successor to passlib, deliberately small: the recommended hasher is argon2id with sensible parameters, `hash` and `verify` do the obvious things, and `verify_and_update` returns both the verification result and, when the stored hash used older parameters or a different algorithm, a freshly computed hash to save. Installing both extras is what makes a migration possible, since bcrypt is needed to verify legacy hashes while new ones are written as argon2id.

Reach for it in new Python services, and specifically with both extras when an existing password table has to move: rehash at login, because that is the only moment you hold the plaintext. Keep the argon2 memory cost in view, since it is charged per concurrent verification, which makes the login endpoint a capacity question and a denial-of-service surface that needs rate limiting.

### pwdlib[argon2]
**Short:** Modern Python password-hashing library defaulting to argon2id, the current recommendation for stored passwords.
**Kind:** tech
**Lang:** python
**Roles:** security/secrets-and-cryptography @1, security/authentication-and-identity @2

The library wraps password hashing in the two calls that matter, `hash` and `verify`, with the algorithm and its parameters encoded into the stored string, so raising the cost later still verifies old hashes and an update-on-verify helper hands you a rehashed value at the next successful login. With only the argon2 extra you get argon2id, the memory-hard function that makes GPU and ASIC cracking expensive rather than merely slow.

Use it for a greenfield password store where no legacy bcrypt hashes need verifying, and add the bcrypt extra as well if any exist. Tune memory, time and parallelism to what the login path can afford, remembering the memory cost applies per concurrent verification, so a burst of logins becomes a capacity problem and the endpoint needs rate limiting. Never substitute a plain SHA-2 hash, salted or not, because speed is exactly the wrong property here.

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

PyRIT is a framework of composable pieces rather than a fixed benchmark: targets wrap the system under test, converters transform a seed prompt by encoding it, translating it, rewriting it as a story or hiding it in a document, scorers judge whether a response is harmful or the attack succeeded, and orchestrators drive the loop, including multi-turn strategies where one model attacks another and adapts. Memory records every attempt, so a run is reproducible and reportable.

Reach for it when red teaming is a repeated engineering activity rather than a one-off exercise, automating the boring breadth so human red teamers spend their time on novel attacks. It is a toolkit and not a verdict: you supply the harm definitions, the seed prompts specific to your product and the scoring criteria, and a run tells you what got through your configuration rather than whether the system is safe.

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

Permissions attach to roles and roles to identities, so access is granted by the job someone does rather than person by person, and revoking a role revokes everything it implied at once. In practice the model is a small lattice: roles can inherit, bindings can be scoped to a namespace, project or tenant, and the check reduces to whether any role bound to the caller carries the permission for this action on this resource, which is cheap enough to evaluate per request.

It is the right default because it is legible, so an auditor can read who has what and joiner-mover-leaver becomes a role change. It runs out where the decision depends on the specific object, such as the owner of this document, the region of this record or the amount on this transaction, since adding a role per case produces role explosion. That is where ABAC or a policy engine such as OPA or Cedar takes over, usually alongside RBAC rather than replacing it.

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

Rego is a declarative query language descended from Datalog: rules define documents rather than execute steps, `input` is the request under evaluation and `data` is everything else loaded, and a rule body is a conjunction of expressions where iteration happens implicitly by unifying a variable across a collection. Two idioms carry most policies, a complete rule with a default of false that any matching case can satisfy, and a partial rule collecting deny messages, which is how policy tools report every violation instead of the first.

It repays learning when authorization or configuration policy is a real part of the system, because rules become testable and version-controlled. The learning curve is the genuine cost: undefined is not false, negation and unification behave unlike an imperative language, and a rule that quietly evaluates to undefined looks exactly like a policy that passed. Write tests alongside the policy, and note the current syntax requires keywords that older examples omit.

### safety
**Short:** Python dependency CVE scanner backed by a commercial vulnerability database, complementing pip-audit.
**Kind:** tech
**Lang:** python
**Roles:** security/supply-chain-and-runtime-security @1, devtools/static-analysis-and-linting @2

It checks installed packages or a requirements file against a curated vulnerability database, and the curation is the pitch, since entries are reviewed and often include advisories absent from public feeds, with the newer scan command also flagging insecure configuration and suggesting remediation. It runs as a CLI locally and in CI, and current versions require an account and authentication even on the free tier.

Reach for it as a second opinion alongside pip-audit rather than a replacement, because running two scanners over one dependency set genuinely surfaces different findings. The considerations are licensing and dependence: the full database is a commercial product with terms attached to CI use, and the curation you are buying is precisely the thing you cannot reproduce. As with any composition scanner, a hit means a vulnerable version is present, not that your code reaches the vulnerable path.

### scrypt
**Short:** Memory-hard password hashing and key derivation function (RFC 7914), tuned by cost, block and parallelism factors.
**Kind:** spec
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, security/authentication-and-identity @2

The function makes the cost of a guess a memory cost as well as a time cost. It derives a large array of pseudorandom blocks and reads them back in an order determined by their own contents, so an attacker cannot trade memory away for computation without a large slowdown. The parameters are `N` for cost, `r` for block size and `p` for parallelism, with memory use approximately 128 times `N` times `r` bytes, which is the number to reason about when tuning.

Use it for password storage or key derivation when Argon2id is unavailable, and note it is also the derivation function behind several cryptocurrency keystores. Argon2id is the current recommendation because it addresses tradeoff and side-channel attacks that scrypt does not fully, but scrypt remains a legitimate choice and is far better than PBKDF2. As with any memory-hard function the memory is consumed per concurrent verification, so tune with your login concurrency in mind.

### SD-JWT
**Short:** Selective-disclosure JWT: the holder reveals only the claims a verifier needs, keeping the rest hidden.
**Kind:** spec
**Lang:** *
**Roles:** security/authentication-and-identity @1, apis-frameworks/data-formats-and-api-contracts @2

The issuer replaces selected claim values with salted hashes inside the signed JWT and hands the holder the matching disclosures, each a salt, name and value triple. To present, the holder appends only the disclosures a verifier needs, separated by tildes; the verifier hashes each one and matches it against the digests in the signed payload, so the signature still covers everything while unrevealed claims stay hidden. An optional key-binding JWT signed by the holder proves the presentation was not simply replayed by whoever copied it.

Reach for it for verifiable credentials where one issued document must satisfy many verifiers with different needs, such as proving an age threshold without disclosing a birth date. It is considerably more machinery than a plain JWT and requires holder-side software, so it is not the tool for ordinary API authentication, and unlinkability is limited because the issuer's signature stays constant across presentations.

### sdcMicro
**Short:** Statistical disclosure control package applying k-anonymity, l-diversity and t-closeness to tabular data releases.
**Kind:** tech
**Lang:** *
**Roles:** security/privacy-and-compliance @1

A statistical disclosure control package built around measuring risk before and after treatment: it computes individual re-identification risk from the combination of quasi-identifiers, counts k-anonymity violations and global risk, then applies treatments such as recoding categories, local suppression targeted at the risky records, microaggregation, noise addition or rank swapping for continuous variables, and reports the information loss each one cost. A graphical wrapper exists for analysts who do not work in R.

Reach for it when the deliverable is a microdata file released to researchers or the public and the release must be documented as assessed, which is why national statistics offices use it. The guarantees are syntactic rather than differential, so they bound linkage against a modelled attacker and degrade if the same population is released repeatedly, and its natural home is static survey-style tables rather than streaming or free-text data.

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

These are the two kernel mechanisms containers are made from. Namespaces virtualise a process's view of the system, giving it its own pid, mount, network, uts, ipc, user and cgroup namespaces so it sees a private version of each resource, while a seccomp-bpf filter attached to the process decides per syscall number and argument whether to allow, error, kill or trace, permanently and inherited by children. Capabilities and cgroups complete the picture by splitting root's privileges and capping resource use.

Reach for them directly when building a sandbox rather than consuming one, such as a language runtime executing user code, an agent running generated commands, or a CI job. The important caveat is that this is still one shared kernel, so a kernel vulnerability reachable through an allowed syscall is a full escape, and a runtime's default profile blocks only a few dozen syscalls while leaving hundreds available. When the workload is genuinely hostile, gVisor or a microVM adds the boundary these cannot.

### Secret Manager
**Short:** Google Cloud's managed secret store with versioning, IAM-scoped access and audit logging.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, platform-delivery/cloud-platform-and-cost @3

Each secret is a container of immutable versions: you add a version and the latest alias moves, so a bad value is rolled back by pointing at the previous version rather than by editing in place, and pinning an explicit version number is what makes deployments reproducible. Access is IAM at the secret level, values are encrypted at rest with Google-managed or customer-managed keys, and every access appears in audit logs. Workloads authenticate as their own service account, so no bootstrap credential is needed.

Use it for database passwords, API keys and signing material that must not live in a repository or an image. It stores and versions values but does not know how to change a credential on the target system, since a rotation schedule only triggers your own function, so Vault's dynamic secrets engines are the alternative when the store itself should mint short-lived credentials. Cache reads in the application, because fetching per request is both slow and billable.

### Secrets Store CSI Driver
**Short:** Kubernetes CSI driver mounting secrets from Vault or a cloud secret manager into a pod as a tmpfs volume.
**Kind:** tech
**Lang:** *
**Roles:** security/secrets-and-cryptography @1, platform-delivery/kubernetes-and-orchestration @2

The driver presents secrets as a volume. A `SecretProviderClass` names the provider, whether AWS, Azure, Google Cloud or Vault, and which objects to fetch, and when a pod mounts the volume the node-level driver authenticates with the pod's identity and writes the values into a tmpfs mount inside the container. Nothing is written to the node's disk and, by default, nothing is written into a Kubernetes Secret at all, though optional syncing can create one for a workload that needs an environment variable.

Reach for it when secrets should stay out of etcd, which is the substantive difference from an operator that materialises them as Secret objects. The tradeoffs are that applications must read files rather than environment variables to get the benefit, since the sync option puts the value back into etcd, and that rotation only rewrites the mounted file, so the process must reread it or be restarted. External Secrets Operator fits better when Secret objects are what your tooling expects.

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

The pairing is the signing tool and the infrastructure behind it: cosign performs signing and verification, Fulcio issues a short-lived certificate bound to an OIDC identity such as a CI workflow or a developer account, and Rekor records the signature in a public append-only transparency log. Signatures and attestations are stored as OCI objects next to the image, so verification needs only registry access plus the log.

Reach for it for container images, model artifacts and build attestations such as SBOMs and provenance, and specifically to answer which workflow produced an artifact rather than merely that some key signed it. Plan for the parts that bite: verification depends on reaching the log and registry, the public transparency log makes artifact names and signer identities public, and a signature only becomes a control once an admission controller or policy engine rejects the unsigned.

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

The defence exploits the brittleness of optimised adversarial suffixes. It makes several perturbed copies of the incoming prompt with small random character-level edits such as swaps, insertions and patches, runs each through the model, and aggregates the results, refusing when a sufficient share of the copies produce a refusal. A suffix found by gradient search is fragile enough that a few random character changes usually destroy it, while ordinary prose survives the same perturbation with its meaning intact.

Reach for it as a cheap-to-reason-about defence against gibberish-suffix attacks specifically, since it needs no retraining and no access to weights. The costs are why it is not a default: every prompt costs several inference passes, so latency and spend multiply, and perturbing input degrades quality on tasks where exact text matters, such as code. It also does nothing against fluent semantic jailbreaks, which survive character noise perfectly well.

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

It is the authorization-server half of OAuth2 for Spring, replacing the long-deprecated Spring Security OAuth project. Clients are `RegisteredClient` records carrying an id, secret, allowed grant types, redirect URIs and scopes, and the server implements authorization code with PKCE, client credentials, refresh tokens and the device flow, plus OIDC discovery, a JWKS endpoint backed by the key source you configure, and token introspection and revocation. Token contents are shaped by a customizer bean.

Reach for it when you must issue your own tokens, whether for an internal identity provider, a product that is itself an OAuth provider, or an air-gapped environment, rather than validating someone else's. Be clear about what you take on: an authorization server is the login path for everything behind it, so key management, client registration, consent, high availability and upgrades all become operational problems that Keycloak, Auth0, Okta or Cognito exist to absorb.

### Spring Security 6.x
**Short:** Spring's security framework: filter chain, authentication providers, method security, SecurityContext propagation.
**Kind:** tech
**Lang:** java
**Roles:** security/authentication-and-identity @1, security/authorization-and-policy @2, apis-frameworks/aop-middleware-and-scheduling @3

This is the Jakarta-namespace generation that arrived with Spring Boot 3, and it removed the configuration style most existing code used: the configurer adapter base class is gone in favour of declaring `SecurityFilterChain` and `AuthenticationManager` beans, and the lambda DSL replaced the chained builder calls. Matcher methods were renamed to `requestMatchers`, and the authorization internals moved to `AuthorizationManager`, which is what lets the same rules express both web and method security.

You are on it if you are on Boot 3, and the upgrade from 5.x is mostly mechanical but wide, since every security configuration class changes and behavioural changes such as stricter request matching can silently alter which rules apply. Read the migration guide rather than fixing compile errors one at a time, and re-test matcher order afterwards, because the first matching rule wins and a permissive pattern moved up during refactoring quietly opens an endpoint.

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

For decisions that depend on the specific row rather than the endpoint, this module stores permissions per domain object across four tables covering security identities, the object class, the object identity with a parent link for inheritance, and the entries granting or denying bit-masked permissions such as read, write and administration. Post-authorize and post-filter expressions calling `hasPermission` route through a `PermissionEvaluator` that reads those tables, with a cache in front because otherwise every check is a query.

Reach for it when per-object sharing is genuinely a product feature, such as documents shared with named users or folder hierarchies with inherited permissions. Most applications should not: the schema is intrusive, filtering a collection after loading it means fetching rows the user may not see and paginating incorrectly, and the ACL tables grow with your data. If the rule is expressible as a predicate on owner, tenant or team, put it in the query instead, or use a policy engine over attributes.

### Spring Security OAuth2
**Short:** Spring's OAuth2/OIDC support: authorization-code login, resource-server token validation and client credentials.
**Kind:** tech
**Lang:** java
**Roles:** security/authentication-and-identity @1, security/authorization-and-policy @2

The name covers two distinct modules doing opposite jobs. The client side performs the authorization-code and client-credentials flows, driven by `ClientRegistration` entries in configuration, a login endpoint per provider, and an authorized-client manager that attaches tokens to outgoing `WebClient` or `RestClient` calls with refresh handled for you. The resource-server side performs no flows at all: it validates an incoming bearer token against an issuer's JWKS, or by introspection, and maps claims to authorities.

Pick by role, since a browser application that logs users in needs the client, an API that receives tokens needs the resource server, and a gateway often needs both. This replaces the retired Spring Security OAuth project, so older tutorials using the single-sign-on and resource-server annotations no longer apply. Two defaults cause most confusion: scopes become authorities with a `SCOPE_` prefix while roles in a custom claim are ignored, and audience is unvalidated unless you add a validator.

### Spring Security OAuth2 Resource Server
**Short:** Spring Security module validating Bearer JWTs or opaque tokens and mapping claims to authorities.
**Kind:** tech
**Lang:** java
**Roles:** security/authentication-and-identity @1, security/authorization-and-policy @2

Configuring the resource server with a JWT decoder turns the application into a pure token consumer: it reads the `Authorization: Bearer` header, verifies the signature against keys fetched and cached from the issuer's JWKS endpoint, checks expiry and issuer, and populates the security context with a token whose authorities come from the `scope` or `scp` claim. Opaque tokens are supported too, validated by calling the provider's introspection endpoint instead.

Use it for any API behind a separate identity provider, since it needs one property, the issuer URI, and no login code. Two defaults deserve attention: scopes arrive prefixed with `SCOPE_` while roles in a provider-specific claim need a `JwtAuthenticationConverter`, and audience is not checked unless you add a validator, which means a token issued for a different API in the same tenant will verify. Introspection costs a network call per request, so prefer JWTs unless immediate revocation is required.

### Spring Security Reactive
**Short:** WebFlux security stack: SecurityWebFilterChain rules and ReactiveSecurityContextHolder for non-blocking auth.
**Kind:** tech
**Lang:** java
**Roles:** security/authentication-and-identity @1, security/authorization-and-policy @2, apis-frameworks/aop-middleware-and-scheduling @3

The WebFlux stack mirrors the servlet one with different primitives: `@EnableWebFluxSecurity` and a `SecurityWebFilterChain` bean built with `ServerHttpSecurity` rather than `HttpSecurity`, and reactive equivalents of the entry point and authentication manager. The important difference is that the security context lives in the Reactor context instead of a thread-local, so `ReactiveSecurityContextHolder.getContext()` returns a `Mono` and the principal has to be composed into the reactive chain.

Use it whenever the application is WebFlux, including Spring Cloud Gateway. The mistakes come from mixing paradigms: calling a blocking user lookup or JDBC query inside a filter stalls an event-loop thread, and reading the principal from a thread-local, which some libraries and logging setups do, returns nothing because there is no thread affinity. Method security needs the reactive annotation to be enabled and applies only to methods returning a `Mono` or `Flux`.

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

Adding the starter and one property naming the issuer URI is the whole configuration: Boot fetches the issuer's OIDC discovery document, builds a `JwtDecoder` around its JWKS endpoint with caching and `kid` selection, and installs the bearer-token filter so requests arrive already authenticated. Setting the JWK set URI directly skips discovery, and an introspection URI switches the same starter to opaque-token validation.

Reach for it for any Spring Boot API sitting behind an external identity provider. Know what the defaults do not do: audience goes unvalidated unless you supply a validator, so a token minted for a sibling API of the same issuer passes; authorities come from `scope` or `scp` with a `SCOPE_` prefix, so provider-specific role claims need a converter; and startup or first requests can hang when the issuer is unreachable, which matters for local development and air-gapped environments.

### spring-boot-starter-security
**Short:** Starter that auto-configures Spring Security's filter chain with HTTP Basic and a generated dev password.
**Kind:** tech
**Lang:** java
**Roles:** security/authentication-and-identity @1, security/authorization-and-policy @2, apis-frameworks/dependency-injection-and-config @3

Putting the starter on the classpath is itself a configuration change. Auto-configuration registers the filter chain, requires authentication for every request, enables HTTP Basic and a form login, and generates a random password for a default user that is printed once in the startup log. It also switches on CSRF protection, session fixation protection and a set of security response headers. Declaring your own `SecurityFilterChain` bean backs the defaults off entirely.

It is the right starting point precisely because the default is deny, so you notice security exists on the first request rather than after a review. Two consequences follow: the generated password is a development affordance that a real user store or identity provider must replace, and adding the starter to an existing service will break its callers until rules are written. For an API consuming bearer tokens, add the resource-server starter rather than configuring token parsing by hand.

### spring-cloud-vault-config
**Short:** Spring Cloud property source backed by HashiCorp Vault: KV v1/v2 secrets and dynamic credentials injected at startup.
**Kind:** tech
**Lang:** java
**Roles:** security/secrets-and-cryptography @1, apis-frameworks/dependency-injection-and-config @2

It makes Vault a property source, so importing a `vault://` location pulls secrets into the `Environment` at startup and `@Value` or `@ConfigurationProperties` sees them like any other property. Authentication uses an identity the workload already has, whether a Kubernetes service-account token, an AppRole, cloud IAM or an OIDC login, rather than a stored token, and KV version 2 path handling is done for you. Dynamic backends are the real draw, since the database engine mints a unique credential at startup and the lifecycle manager renews the lease in the background.

Reach for it for a Spring Boot service that should hold short-lived credentials instead of long-lived environment variables. The incidents come from leases: a credential rotated at lease expiry does not fix connection pools still holding the old one, so the pool needs configuring for rotation, and a refresh is required for values to change without a restart. Vault becoming a startup dependency is the architectural cost to accept up front.

### spring-security-oauth2-client
**Short:** Spring Security module implementing the OAuth2/OIDC authorization-code flow for social login and SSO.
**Kind:** tech
**Lang:** java
**Roles:** security/authentication-and-identity @1

The module implements the client half of OAuth2 and OIDC. A `ClientRegistration` carrying the provider issuer, client id and secret, scopes and redirect URI is usually declared in properties, and Spring exposes an authorization endpoint per registration to start the flow, handles the callback with state and PKCE, and stores the result as an authorized client. Beyond login it serves the service-to-service case, where a manager acquires and refreshes client-credentials tokens and a filter attaches them to outgoing HTTP client calls.

Reach for it for social or enterprise sign-in in a browser application, and for calling a protected downstream API without hand-rolling token refresh. Points to watch: the authorized-client repository is session-backed by default, so a multi-instance deployment needs shared sessions or a JDBC repository, the redirect URI registered at the provider must match exactly, and login support authenticates users while the client support only obtains tokens, so choosing the wrong one produces a confusing half-working setup.

### spring-security-rsocket
**Short:** Spring Security module securing RSocket routes with @EnableRSocketSecurity, JWT auth and payload authorization.
**Kind:** tech
**Lang:** java
**Roles:** security/authentication-and-identity @1, security/authorization-and-policy @2, apis-frameworks/rpc-graphql-and-streaming @3

RSocket has no HTTP request to hang security off, so this module works on frames. Enabling it installs a payload interceptor, credentials arrive in the setup frame or in per-request metadata using the standard authentication metadata extension as either simple username and password or a bearer JWT, and authorization rules match on the route, with method-level annotations available on the message-mapping handlers.

Reach for it whenever an RSocket endpoint is exposed beyond a trusted network, since the transport itself provides nothing. The model to keep straight is the connection lifetime: setup-frame authentication establishes one identity for the whole connection including long-lived streams and channels, so an expiring token is not re-checked mid-stream unless you authenticate per request. The security context propagates through the Reactor context, so reading the principal from a thread-local will not work.

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

The scan opens many handshakes against a host from different simulated clients, so the report shows exactly which browsers and runtime versions can connect and with which protocol and cipher. It grades the certificate and chain, protocol support, key exchange and cipher strength, and checks for known issues, with published grading rules: support for an obsolete protocol or a broken cipher caps the grade regardless of everything else, and the top mark requires HSTS.

Use it as the external confirmation after changing a TLS configuration or certificate chain, particularly for the two things that are hard to see from the server, namely an incomplete intermediate chain that works in your browser only because it cached the intermediate, and client compatibility you would otherwise learn about from a user complaint. It only tests public endpoints, results are published on a public board unless you opt out, and an API exists for scripted use.

### starlette-csrf
**Short:** ASGI middleware adding double-submit cookie CSRF protection to Starlette and FastAPI applications.
**Kind:** tech
**Lang:** python
**Roles:** security/authentication-and-identity @1, apis-frameworks/aop-middleware-and-scheduling @2

The middleware implements the double-submit cookie pattern: it sets a random token in a cookie, and any request using an unsafe method must echo that value in a header, which a same-origin script can read from the cookie while a cross-site attacker cannot. Rejected requests receive a 403 before reaching your route. It can be configured to enforce only when a session cookie is present, so a token-authenticated API client is not forced to carry a CSRF token it does not need.

Reach for it when a browser sends credentials automatically, which means cookie-based sessions in a FastAPI or Starlette application, because that is the only situation CSRF exists in. An API authenticated purely by an `Authorization` header does not need it, since the browser will not attach that header on its own. Set `SameSite=Lax` or `Strict` on the session cookie as the first layer, keep this as the defence behind it, and pair both with a restrictive CORS policy.

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

It puts a Python and tensor-shaped API over Microsoft SEAL: you build a context choosing the scheme, the polynomial modulus degree, the chain of coefficient modulus bit sizes and a global scale, then work with vector objects supporting addition, multiplication, dot products and matrix multiplication against plaintext, with the heavy arithmetic staying in C++. Serializing the context without the secret key is what lets a server compute on ciphertext only the client can decrypt.

Reach for it for encrypted inference over a shallow model, such as a logistic regression or a small network where the client will not share features and the server will not share weights. The constraints are the scheme's rather than the wrapper's: parameters buy a fixed multiplicative depth and exceeding it corrupts the result, CKKS is approximate so results carry numerical error, nonlinear activations must be replaced by polynomial approximations, and everything is orders of magnitude slower and larger than plaintext.

### TensorFlow Federated
**Short:** Google's federated learning framework for simulating and running training that never centralizes raw data.
**Kind:** tech
**Lang:** python
**Roles:** security/privacy-and-compliance @1, model-training/distributed-training @2, model-training/deep-learning-framework @3

TFF is built in two layers. Federated Learning offers ready-made processes such as federated averaging that take a Keras model and per-client datasets, while underneath Federated Core is a strongly typed functional language embedded in Python where values carry placements, at the clients or at the server, and computations are expressed with primitives like broadcast and federated aggregation, so data movement is part of the type rather than something you implement.

Reach for it to design and simulate a federated algorithm, covering the round structure, the aggregation, client sampling, and how much accuracy you give up against centralised training. Be clear that the open-source runtime is primarily a simulator running clients on your own machines, since production on-device deployment is a separate engineering problem of device availability, secure aggregation and a fleet you control, and that federation alone is not a privacy guarantee because updates leak information without differential privacy or secure aggregation.

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

Tetragon attaches eBPF programs to kernel hooks covering syscalls, process execution and exit, and file and network operations, and crucially filters in the kernel rather than streaming everything to userspace, which is what keeps overhead low at high event rates. Events carry full process ancestry and Kubernetes identity, so you see which container, pod and workload spawned a process. A `TracingPolicy` custom resource declares what to watch and can also enforce, terminating a matching process in-kernel rather than merely reporting it.

Reach for it in Kubernetes when you want low-overhead runtime detection, or synchronous enforcement that does not depend on a userspace agent reacting in time. The requirements and costs are real: a recent kernel with the right eBPF support, policies expressed at the syscall and argument level rather than in behavioural language, and a genuine risk of killing legitimate processes if an enforcing policy ships without an observation period. Falco is the neighbouring tool with a broader out-of-the-box rule set.

### TF Privacy
**Short:** TensorFlow library for differentially private training via gradient clipping, noise and an epsilon accountant.
**Kind:** tech
**Lang:** python
**Roles:** security/privacy-and-compliance @1, model-training/deep-learning-framework @3

The package supplies drop-in differentially private optimizers for Keras and TensorFlow: each example's gradient is clipped to a fixed L2 norm so no single record can dominate an update, Gaussian noise scaled to that bound is added to the summed batch gradient, and microbatching controls the memory-versus-fidelity trade of computing per-example gradients. A separate accountant converts the noise multiplier, sampling probability and step count into the epsilon and delta you can state.

Reach for it when the model trains on personal records and you need a formal bound on what it memorises rather than a policy that it should not. Expect two concrete costs: training is substantially slower because per-example gradients defeat the usual batching, and accuracy degrades as epsilon tightens, especially on small or imbalanced datasets. Choose epsilon before training rather than reporting whatever the run happened to produce, since that number is the claim you will have to defend.

### Tink
**Short:** Google's misuse-resistant crypto library over JCA: safe AEAD, signing and key rotation defaults.
**Kind:** tech
**Lang:** java
**Roles:** security/secrets-and-cryptography @1

Tink removes the choices that produce vulnerabilities. You program against a primitive interface for authenticated encryption, MAC, signing or hybrid encryption and never name an algorithm, mode or padding, because the algorithm comes from a key template and keys live in a keyset rather than alone. A keyset has one primary key for encryption and retains older keys for decryption, so rotation is adding a key and moving the primary, and a keyset is normally stored wrapped by a cloud KMS key rather than in the clear.

Reach for it whenever application code must encrypt or sign something and the alternative is assembling a cipher transform string by hand, because the API makes ECB, a missing MAC and a reused nonce unreachable rather than merely discouraged. It handles associated data properly, which is how a ciphertext gets bound to its context. It is not a TLS or certificate library and supports a curated algorithm set, so interoperating with a peer that needs something outside it means dropping to the JCA or Bouncy Castle.

### ToxicChat
**Short:** Corpus of 10,166 real user-chatbot queries labelled for toxicity and jailbreaks; the chatbot-domain reference set.
**Kind:** dataset
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1, ml-lifecycle/evaluation-and-benchmarks @2

The value is provenance: the prompts are real user messages sent to a public chatbot demo rather than crowdworker inventions, and they are human-annotated for toxicity and for jailbreaking attempts. That distribution looks quite different from the social-media comment corpora most toxicity classifiers were trained on, which is why models with strong reported numbers elsewhere do noticeably worse here, since the harms arrive as requests made to an assistant rather than as insults aimed at another user.

Reach for it to evaluate or fine-tune a moderation model destined to sit in front of a conversational product, and specifically to measure false positives, because most of the corpus is benign and an over-refusing filter shows up immediately. Its limits are the usual ones: a single English-language demo's traffic at a point in time, so it under-represents other languages, other product surfaces and attack styles that appeared after collection.

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

The distinguishing step is verification. After a detector matches a candidate credential, and there are hundreds of detectors covering individual providers, the tool calls that provider's API to check whether the key still works, so filtering to verified results gives a list of live secrets instead of thousands of maybes. It scans git history commit by commit, along with filesystems, buckets, container images and CI logs, because a secret removed in a later commit still ships with every clone.

Reach for it when a repository has years of history and the question is which leaked credentials are still dangerous today, the triage a pattern-only scanner cannot do. Verification has two consequences: it makes outbound requests to third-party APIs from wherever it runs, which some environments prohibit, and an unverified finding is not automatically safe, since it may belong to a system the tool cannot reach. Anything found in history must be rotated, because rewriting history does not recall the clones.

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

The injector is a mutating admission webhook watching for agent-inject annotations on a pod. When it sees them it rewrites the pod spec to add a Vault Agent init container that fetches secrets before the application starts and a sidecar that keeps them fresh, authenticating with the pod's Kubernetes service-account token. The agent renders values through a template into a shared in-memory volume under `/vault/secrets`, so the application reads a file and needs no Vault client library at all.

Reach for it to bring existing applications onto Vault without code changes, and for dynamic credentials where the sidecar renews the lease. Weigh the costs: an extra container in every pod, an annotation-driven templating language that becomes its own configuration surface, and the fact that a re-rendered file does not restart the process, so the application must reread it or you must trigger a rollout. The Secrets Store CSI driver with the Vault provider is lighter when you only need static values mounted.

### Vec2Text
**Short:** Embedding-inversion toolkit that reconstructs source text from vectors, used to red-team vector stores.
**Kind:** tech
**Lang:** python
**Roles:** security/ai-safety-and-guardrails @1, security/privacy-and-compliance @2, data-stores/vector-store @3

It demonstrates that an embedding is not a one-way hash. A model is trained to invert embeddings back to text by iterative correction: it proposes a candidate string, re-embeds it with the same encoder, compares against the target vector, and refines, repeating until the reconstruction closes in, which for short texts can recover a substantial part of the original wording. It needs query access to the embedding model in order to re-embed hypotheses, and a separate inversion model per encoder.

Use it as the red-team argument that changes how vector stores are treated: if embeddings of customer messages, clinical notes or internal documents sit in a database with weaker access control than the source text, that database is approximately a copy of the text. The practical responses are to protect and encrypt the vector store as sensitive data, keep it inside your trust boundary rather than in a third-party index, and note that perturbing embeddings defends against this only at a measurable retrieval-quality cost.

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

A web application firewall inspects HTTP requests, and sometimes responses, against rules before they reach the application: signatures for injection, cross-site scripting and traversal patterns, protocol validation, address and geography lists, bot signals, and per-endpoint rate limits. Mature rule sets score matches into an anomaly total and act on the total rather than blocking on any single hit, which is what keeps false positives tolerable. It must terminate or otherwise see inside TLS to work at all.

Deploy one to absorb background scanning, buy time on a newly disclosed vulnerability while a real fix ships, and enforce rate limits at the edge instead of in every service. Be honest about what it is: pattern matching over a request, so it does not know your authorization rules, cannot see a broken object-level access check, and is bypassable by an attacker who understands encoding. Always run new rules in count mode first, since a default rule set will block legitimate requests carrying JSON, markup or free text.

### WildGuard
**Short:** Open safety moderation model and benchmark covering prompt harm, response harm and refusal, calibration-aware.
**Kind:** model
**Lang:** *
**Roles:** security/ai-safety-and-guardrails @1, ml-lifecycle/evaluation-and-benchmarks @2

It scores three things per exchange rather than one: whether the prompt is harmful, whether the response is harmful, and whether the model refused. That third output is what makes it usable for tuning, because a guardrail's real cost is refusing benign requests and a two-way safe-or-unsafe classifier cannot show you that. It is an open model trained on a mix of synthetic and human-annotated data covering both plain and adversarial prompts, and it is released alongside the benchmark it was evaluated on.

Reach for it when moderation must run in your own infrastructure and you want an open inspectable alternative to a hosted classifier or a much larger guard model, particularly when the split between vanilla and adversarial prompts matters for your traffic. The usual conditions apply: it is a model on the request path with its own latency and memory cost, its taxonomy is a general research one rather than your product policy, and thresholds should be validated against your own labelled sample.

### Workload Identity
**Short:** Maps a Kubernetes service account to a cloud IAM identity so pods get short-lived credentials, no static keys.
**Kind:** tech
**Lang:** *
**Roles:** security/authentication-and-identity @1, platform-delivery/kubernetes-and-orchestration @2, security/secrets-and-cryptography @3

The binding replaces credential files with a trust relationship. A Kubernetes service account is associated with a cloud identity, the cluster issues the pod a short-lived audience-scoped projected token, and the cloud provider's token service exchanges that token for credentials because it trusts the cluster's OIDC issuer for exactly that subject. Client libraries pick this up automatically through the metadata server or an injected credential configuration, so application code calls the API exactly as it always did.

Use it for every pod that talks to a cloud API, because the alternatives fail in known ways: a service account key in a Secret is a long-lived credential that gets copied and never rotated, and node-level permissions are inherited by every pod scheduled there. The details that trip people are in the binding, since the trust must name the exact namespace and service account or a wildcard grants the identity cluster-wide, and each cloud names the same idea differently.
