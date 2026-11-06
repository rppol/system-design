# Fintech Section - CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/fintech/`
Global conventions (formatting, templates, Q&A rules, Mermaid rules): see root `CLAUDE.md`.
Build manifest and status tracker: `README.md` §7 "Build Manifest". Check it before you start a file.

---

## Why this section exists

The repo has payment and trading pages in seven sections: `hld/` (payment system, digital
wallet, stock exchange), `database/` (banking ledger), `backend/` (payment processor), `lld/`
(order matching engine), `spring/` (idempotent payment API), `react/` (checkout) and `llm/`
(agentic commerce). `ml/` has a fraud study and `java/` has a market-data handler study. Each
page designs one system end to end. No module teaches the domain those pages assume.

So each page explains minor units, double entry and rails again, each at a different depth.
Several pages also state dated rules that no page owns: the SCA exemptions and the FedNow
claim in the digital wallet study, and the PCI retention rule in the payment processor study.

`fintech/` owns the domain concepts and the dated rule facts. Placement is link-only. No
existing page moves into `fintech/`, so no existing question id changes.

---

## Build process for this section

- Work happens in the main checkout on branch `feat/payments-and-applied-ai`. There is no
  worktree and no PR. The owner makes the commit and pushes to `main`. Builders never run git
  commands that change state.
- The applied-AI module (`llm/applied_ai_product_decisions`) is already wired on this branch.
  The fintech integrator works after it and keeps the `llm` question count at 2388.
- `extract.py --strict` is red between phases on the unpushed branch. That is expected.
  Builders do not run `extract.py`, because it writes `game/questions/` and parallel runs collide.
- One integrator owns `README.md` (the `<!-- study-paths -->` block, the manifest and the tier
  tables), `game/app.js`, the root `CLAUDE.md` and `README.md`, the `CLAUDE.md` files of other
  sections, and every inbound link in other sections. Builders edit only their own files.
- Links from another section's root `CLAUDE.md` to this section use `../fintech/...`, not
  `../../fintech/...`. Verify every link on disk.
- Prose: apply the `humanizer`, `writing-voice`, `use-conversational-language` and
  `asd-ste100` skills. Plain, direct technical English. Then run Vale once per file and fix
  every House hit:
  `vale --config ~/.claude/vale/.vale.ini --output=line <file> | grep ':House\.'`
- `npm ci` is done in the repo root. `node_modules/` exists. The Docker images
  `eclipse-temurin:25-jdk` and `postgres:18` are local.

---

## Non-overlap boundary

If a subtopic is in the middle column, link to that page. Do not teach it again. Treatment:
"link" means a link only. "para" means one paragraph plus the link. "own" means `fintech/`
teaches it.

A module's §1 ends with one boundary sentence and the link only where this table names a real
overlap for that module. A module with no overlap here does not get a "Not here" paragraph.

The trading modules (microstructure, order books, exchange architecture, market data, low
latency) and the payment modules read the owner pages first and teach only the delta: the
general concept, the alternatives the existing study does not cover, and the dated rule facts.
Material that only restates an owner page becomes a link.

| Topic | Owner page (link, do not repeat) | Treatment, and what `fintech/` owns |
|---|---|---|
| IEEE-754, why `0.1 + 0.2 != 0.3` | `cs_fundamentals/number_systems_and_bit_manipulation/number_systems_and_bit_manipulation.md` | para. Fintech owns minor units, ISO 4217 exponents, rounding modes, allocation and FX precision |
| Java `BigDecimal` `equals` vs `compareTo` | `java/core_language/core_language.md` | link |
| The client never computes totals; minor units in the UI | `react/case_studies/design_a_checkout_and_payment_flow.md` §4.2 | link |
| Idempotency-key pattern, its race and its storage; 2PC, TCC, saga, outbox | `hld/distributed_transactions/distributed_transactions.md`, `backend/distributed_transactions_and_consistency/distributed_transactions_and_consistency.md`, `database/distributed_transactions/distributed_transactions.md`, `backend/messaging_patterns/messaging_patterns.md` | link. Fintech owns the cross-rail key chain, unknown-outcome resolution and the per-rail retry rules |
| Spring transaction propagation and isolation | `spring/spring_transactions/spring_transactions.md` | link |
| Spring advisory-lock idempotency, outbox poller | `spring/case_studies/design_idempotent_payment_api.md` | link |
| Saga-orchestrated charge, PSP timeout handling, ledger with DB constraints | `backend/case_studies/design_payment_processor/design_payment_processor.md` | link |
| Durable workflows for payouts and dunning | `backend/durable_execution_and_workflows/durable_execution_and_workflows.md`, `technologies/temporal_durable_execution/temporal_durable_execution.md` | link |
| Webhook signing, retries, receiver dedupe | `backend/webhooks_and_event_delivery/webhooks_and_event_delivery.md`, `backend/case_studies/design_webhook_delivery_service/design_webhook_delivery_service.md` | link. Fintech owns "a PSP webhook is evidence, not truth" |
| ACID, isolation, write skew, `SELECT ... FOR UPDATE`, deadlock order | `database/concurrency_control_and_locking/concurrency_control_and_locking.md`, `database/database_fundamentals/database_fundamentals.md` | para in the ledger module |
| Postgres ledger schema, SERIALIZABLE, RLS, CQRS balance read model, RPO 0, partitioning | `database/case_studies/design_banking_ledger/design_banking_ledger.md` | link. Fintech owns the accounting model and its invariants |
| Payment system end to end (idempotency key, double entry, saga, outbox, recon job) | `hld/case_studies/design_payment_system.md` | link |
| Wallet P2P transfers, optimistic locking, KYC tier table, SCA exemptions, instant-rail table | `hld/case_studies/design_digital_wallet.md` | link. Fintech owns the dated rule facts |
| Consensus, Raft, state-machine replication, deterministic simulation | `hld/consensus_algorithms/consensus_algorithms.md`, `database/consistency_models_and_consensus/consistency_models_and_consensus.md` | para in exchange architecture |
| Partitioned log, offsets, `acks=all` | `hld/case_studies/design_distributed_message_queue.md`, `backend/kafka_deep_dive/kafka_deep_dive.md` | link |
| Exchange end to end: single writer per symbol, sequencer, snapshot plus delta, pre-trade risk, halts, venue latency table, IEX, LMAX | `hld/case_studies/design_stock_exchange.md` | link, never retell. Fintech owns the general concepts |
| Price-time `TreeMap` book, FOK dry run, ticks as `long`, trade at the resting price | `lld/system_design_problems/OrderMatchingEngine.md` | link. Fintech owns pro-rata, auctions, STP modes and array ladders |
| Off-heap ring buffer, padded cursors, GC-free feed handler | `java/case_studies/design_low_latency_market_data_handler.md` | link |
| Disruptor | `java/case_studies/design_event_bus.md`, `lld/concurrency_patterns/ProducerConsumer.md` | link |
| Cache lines, false sharing, NUMA, branch prediction | `cs_fundamentals/computer_architecture_and_memory_hierarchy/computer_architecture_and_memory_hierarchy.md` | link |
| Syscall cost, network-stack latency, CFS throttling | `backend/os_and_io_performance/os_and_io_performance.md` | link |
| Tail latency, profiling, benchmarking | `backend/tail_latency_and_fan_out/tail_latency_and_fan_out.md`, `backend/performance_profiling/performance_profiling.md`, `backend/benchmarking_methodology/benchmarking_methodology.md` | link |
| PCI Req 3.5.1 (PAN unreadable), TDE, pgAudit | `database/database_security_and_compliance/database_security_and_compliance.md` | link. Fintech owns scope, SAQs, tokenization and HSMs |
| PCI 6.4.3 and 11.6.1 payment-page scripts, CSP, SRI | `frontend_platform/browser_security_and_the_client_threat_model/browser_security_and_the_client_threat_model.md` | link |
| Card-field iframe, client-side 3DS round trip | `react/case_studies/design_a_checkout_and_payment_flow.md` §4.5 and §4.6 | link |
| PCI 6.3.2 SBOM; policy-as-code mapping | `devops/devsecops_and_supply_chain_security/regulated_supply_chain.md`, `devops/policy_as_code_and_compliance/policy_as_code_and_compliance.md` | link |
| Envelope encryption, KMS, Vault, crypto primitives | `hld/security_and_auth/security_and_auth.md`, `technologies/hashicorp_vault/hashicorp_vault.md`, `cs_fundamentals/cryptography_fundamentals/cryptography_fundamentals.md` | link |
| Passkeys and WebAuthn | `hld/security_and_auth/security_and_auth.md` §4.7, `backend/auth_and_authorization_systems/auth_and_authorization_systems.md` | link. Fintech owns the SCA rules |
| Fraud model internals (XGBoost, class imbalance, PSI, streaming features) | `ml/case_studies/design_fraud_detection.md`, `ml/case_studies/design_anomaly_detection.md` | link. Fintech owns the payment risk-decision system |
| Champion/challenger with label delay; decision models | `mlops/case_studies/design_a_continuous_training_and_champion_challenger_system.md`, `mlops/decision_models/decision_models.md` | link |
| Model risk management (SR 11-7) | `mlops/ai_governance_and_model_risk_operations/ai_governance_and_model_risk_operations.md` | link |
| Agentic payments (AP2, x402, ACP, mandates) | `llm/multi_agent_systems/agentic_commerce_and_payments.md` | link. Fintech owns the rails and roles that page assumes |
| Revenue-recognition vocabulary (ASC 606) | `soft_skills/business_and_finance_vocabulary/business_and_finance_vocabulary.md` | para in billing |
| Distributed ids, rate limiting, multi-region and residency | `hld/case_studies/design_distributed_unique_id.md`, `hld/rate_limiting/rate_limiting.md`, `hld/multi_region_and_data_residency/multi_region_and_data_residency.md` | link (para on payment-data localization in PCI) |
| Event sourcing | `hld/event_sourcing_cqrs/event_sourcing_cqrs.md`, `backend/event_sourcing_and_cqrs/event_sourcing_and_cqrs.md` | para: "a ledger is an event log, and not every event store is a ledger" |
| Tick storage | `database/time_series_databases/time_series_databases.md` | link |
| Book chapter summaries: payment system, digital wallet, stock exchange | `book/system_design_interview_vol_2/11_payment_system/11_payment_system.md`, `book/system_design_interview_vol_2/12_digital_wallet/12_digital_wallet.md`, `book/system_design_interview_vol_2/13_stock_exchange/13_stock_exchange.md` | link only, from the README and the relevant modules. Never edit a `book/` file |

Lending and credit underwriting are out of scope. `ml/case_studies/design_credit_risk_scoring.md`
owns them. The other out-of-scope lines are in `README.md` §1.

---

## Fact pins (fetched 2026-11-06)

Three fetchers read the primary sources on 2026-11-06. The tables below merge their rows by
topic. The raw fetch notes are in `.work/fintech/facts_payments.md`, `facts_compliance.md` and
`facts_markets.md`. These are working files and do not ship.

### Pin rules

- Every module's anchor blockquote and every date, threshold, limit, fee, version or company
  figure in a module comes from a row below.
- A fact that is not here is fetched from its primary source before you write it. If the fetch
  fails, cut the fact. No "verify" marker ships.
- Items in "Do not state" stay out of every file, including Q&As and `**Short:**` lines.
- A row says only what its Value cell says. Do not widen its scope. For example, F2 and F3 are
  Visa rules, and they say nothing about Mastercard.
- Prices and fees carry their check date. Company figures are attributed ("Square reported"),
  never asserted.
- Design inputs (capacity, targets, SLOs) are not facts. Label them "assumption" or
  "illustrative" in the text, separate from pinned facts.

### Verdicts and flags

| Mark | Meaning |
|---|---|
| Confirmed | The fetcher read the primary source and the value matches. |
| Corrected | The fetcher read the primary source and it differs from the plan. The Value cell is the corrected fact. Use it. |
| [V] | Volatile. Fetch the row again before any audit pass and before you ship a page that states it. |
| [R] | Re-fetch before audit. The direct fetch failed (HTTP 403, WAF, timeout or a binary PDF) and the fetcher read the search index copy of that same primary page. |

Where a fetcher contradicted PLAN §11, the fetcher wins. Each such row says so in its Verdict
cell, and "Fact corrections" below lists them.

### Money representation

| ID | Value | Source | Source date | Verdict |
|---|---|---|---|---|
| F1 | ISO 4217 minor-unit exponents. 0: BIF, CLP, DJF, GNF, ISK, JPY, KMF, KRW, PYG, RWF, UGX, UYI, VND, VUV, XAF, XOF, XPF. 3: BHD, IQD, JOD, KWD, LYD, OMR, TND. 4: CLF, UYW. Metal and fund codes (XAU, XDR) have "N.A.". Most currencies use 2 | https://www.six-group.com/dam/download/financial-information/data-center/iso-currrency/lists/list-one.xml | list published 2026-09-17 | Corrected: CLF is not the only 4-decimal code. UYW is also 4 |
| W1 | Bitcoin Core `CAmount` is `int64_t`. `COIN` = 100,000,000 sat. `MAX_MONEY` = 21,000,000 x `COIN` = 2.1e15 sat, far below the int64 maximum (about 9.22e18). 1 ETH = 1e18 wei, so int64 holds at most about 9.22 ETH in wei. Large wei balances need 128 bits or more (EVM words are 256-bit). Required integer width follows max notional x scale | https://github.com/bitcoin/bitcoin/blob/master/src/consensus/amount.h, https://ethereum.org/en/developers/docs/intro-to-ether/ | accessed 2026-11-06; ethereum.org updated 2026-10-16 | Confirmed. Matches §11 |

### Ledgers and idempotency

| ID | Value | Source | Source date | Verdict |
|---|---|---|---|---|
| F62 | TigerBeetle latest release 0.17.9. Transfer flags: `linked`, `pending`, `post_pending_transfer`, `void_pending_transfer`, `balancing_debit`, `balancing_credit`, `closing_debit`, `closing_credit`, `imported`. Transfer amount is u128 | https://github.com/tigerbeetle/tigerbeetle/releases, https://docs.tigerbeetle.com/reference/transfer/ | 2026-07-06 | Confirmed [V] |
| F63a | Square "Books, an immutable double-entry accounting database service": journal entries must balance to 0, books hold balances, entries are append-only, debits are positive and credits negative. Runs on Google Cloud Spanner and GKE | https://developer.squareup.com/blog/books-an-immutable-double-entry-accounting-database-service/ | 2019-10-16 | Confirmed |
| F63b | Uber "How LedgerStore Supports Trillions of Indexes": LedgerStore is immutable storage that indexes hundreds of billions of ledgers. Uber built over 2 trillion unique indexes and moved them from DynamoDB to Docstore | https://www.uber.com/us/en/blog/how-ledgerstore-supports-trillions-of-indexes/ | 2024-04-04 | Confirmed |
| F64 | The IETF `Idempotency-Key` header is an Internet-Draft, not an RFC: draft-ietf-httpapi-idempotency-key-header-08, Active I-D, httpapi WG, intended Standards Track | https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/ | 2026-10-22 | Confirmed [V] |

### Cards, networks and interchange

| ID | Value | Source | Source date | Verdict |
|---|---|---|---|---|
| F2 | Visa sets a maximum time between a valid authorization and processing, by transaction type. Card-absent CIT 10 days. Card-absent CIT with the extended-authorization indicator 30 days. Estimated auth at cruise, lodging and vehicle rental 30 days. Estimated auth at other rental MCCs 10 days. Other card-present 5 days. AFD preauth completion or reversal within 2 hours. An incremental authorization does not extend the window. Country overrides exist (India card-absent untokenized domestic 2 days, Japan domestic 30 days) | https://usa.visa.com/dam/VCOM/download/about-visa/visa-rules-public.pdf (Visa Core Rules §5.7.3.5, Tables 5-11 and 5-12) | edition 24 October 2026 | Confirmed for Visa [V] |
| F3 | Visa groups decline codes into 4 categories. Category 1 (issuer will never approve, for example 04, 07, 14, 41, 43, 46, R0, R1, R3): never reattempt with the same credential. Categories 2, 3 and 4: up to 20 attempts in 30 days. A reattempt must not change data elements such as MCC, POS entry mode or ECI | Visa Core Rules §7.3.6.3, Table 7-2; §1.7.2.1 | edition 24 October 2026 | Confirmed for Visa [V] |
| F4 | EU IFR (Reg. 2015/751) caps consumer interchange at 0.2% for debit (Art. 3) and 0.3% for credit (Art. 4). US Reg II caps covered debit interchange at 21 cents + 5 bps of value (12 CFR 235.3(b)), plus up to 1 cent for the fraud-prevention adjustment. The Fed proposal of 2023-10-25 (docket R-1818) to lower the cap closed for comment on 2024-05-12. State only "proposed, not finalized as of the fetch" | https://eur-lex.europa.eu/eli/reg/2015/751/oj/eng (search index), https://www.ecfr.gov/current/title-12/part-235/section-235.3, https://www.federalreserve.gov/apps/proposals/FR-0000-0095-01/details | IFR 2015-04-29; eCFR accessed 2026-11-06; Fed page updated 2024-05-17 | Confirmed [V] [R] |
| F5 | Visa reported more than 10 billion tokens issued since the 2014 launch (release 2024-06-04). Visa states no numeric target. Mastercard committed in June 2024 to 100% e-commerce tokenization in Europe by 2030 | https://usa.visa.com/about-visa/newsroom/press-releases.releaseId.20701.html, https://www.mastercard.com/news/press/2024/june/mastercard-reimagines-online-checkout-commits-to-reaching-100-e-commerce-tokenization-by-2030-in-europe/ (search index) | 2024-06-04; June 2024 | Confirmed [V] [R] |
| F13 | Capital One completed its acquisition of Discover on 2025-05-18 and owns the Discover, PULSE and Diners Club International networks | https://www.sec.gov/Archives/edgar/data/927628/000119312525122059/d934475dex991.htm | 2025-05-18 | Confirmed |
| F39 | Visa Direct and Mastercard Move are the networks' push-payment and money-movement product names. Visa states 18B+ eligible endpoints in 195+ countries and territories. Mastercard Move is a portfolio of domestic and cross-border money movement | https://www.visa.com/en-us/products/visa-direct, https://www.mastercard.com/news/press/2025/april/moneygram-and-mastercard-collaborate-to-advance-digital-money-movement (search index) | accessed 2026-11-06; April 2025 | Confirmed [R] |

### SCA, 3DS and PSD3

| ID | Value | Source | Source date | Verdict |
|---|---|---|---|---|
| F8 | EMVCo's latest final 3DS spec is v2.3.1.1 (replaces v2.3.1.0 for testing). Protocol and Core Functions v2.4.0.0 DRAFT 1 was published for comment on 2026-11-04 and is a draft only. EMVCo recommends v2.2 or higher | https://www.emvco.com/emv-technologies/3-d-secure/ | accessed 2026-11-06 | Confirmed [V] |
| F9 | PSD2 SCA RTS (Reg. 2018/389) exemption limits. Contactless (Art. 11): EUR 50 per transaction, cumulative EUR 150 or 5 transactions since the last SCA. Low-value remote (Art. 16): EUR 30 per transaction, cumulative EUR 100 or 5 transactions. A PSP tracks either the money limit or the count limit, not both. TRA for cards (Art. 18): EUR 100 needs a fraud rate of 0.13% or less, EUR 250 needs 0.06% or less, and a EUR 500 tier exists | https://eba.europa.eu/single-rule-book-qa/qna/view/publicId/2018_4225, https://www.eba.europa.eu/single-rule-book-qa/qna/view/publicId/2018_4043, https://www.legislation.gov.uk/eur/2018/389/article/11 (search index, for EUR 50) | EBA Q&A 2019-10-11 and 2018-12-21 | Confirmed [R] |
| S3 | Exemption roles. The payer's PSP (issuer) can apply an exemption. For some exemptions (Art. 11 and Art. 16 for cards) the payee's PSP (acquirer) can apply it. The issuer always makes the final decision and can step up to SCA. Payee-initiated card payments (MITs) are out of scope of SCA, so they need no exemption. Setting up the MIT mandate remotely requires SCA | https://www.eba.europa.eu/single-rule-book-qa/qna/view/publicId/2019_4480, https://www.eba.europa.eu/single-rule-book-qa/qna/view/publicId/2018_4242, https://www.eba.europa.eu/single-rule-book-qa/qna/view/publicId/2018_4031 | 2020-06-19; 2019-10-11; 2019-03-01 | Confirmed. The fetcher wins over §11: the issuer can also apply exemptions, so "the acquirer requests, the issuer decides" is incomplete |
| F10 | PSD3 and the PSR are not law. Coreper approved on 2026-10-28 and the Council adopted its first-reading positions on the PSD and the PSR on 2026-10-29. Parliament's second-reading plenary has an indicative date of 2026-12-14. Nothing is in the Official Journal. Pin no application date | https://data.consilium.europa.eu/doc/document/ST-14536-2026-INIT/en/pdf, https://data.consilium.europa.eu/doc/document/ST-8535-2026-REV-1-ADD-1/en/pdf, https://oeil.europarl.europa.eu/oeil/en/procedure-file?reference=2023/0210(COD) | 2026-10-28; 2026-10-29; accessed 2026-11-06 | Corrected [V]: the plan's status (April 2026 compromise texts, OJ pending) is out of date |

### PCI DSS and data localization

| ID | Value | Source | Source date | Verdict |
|---|---|---|---|---|
| F6 | PCI SSC FAQ 1091, truncated PAN storage maximum. 16-digit PAN (6- or 8-digit BIN): remove at least 4 digits, keep at most "first 8, any other 4". 15-digit Amex: remove at least 5, keep at most "first 6, last 4". This is a storage maximum, not a display rule | https://www.pcisecuritystandards.org/faq/articles/Frequently_Asked_Question/What-are-acceptable-formats-for-truncation-of-primary-account-numbers/ | FAQ dated June 2022 | Corrected: the rule is "first 8, any other 4", not "first 8 with last 4" |
| F7 | PCI DSS v4.0.1 is the only active version. Announced 2024-06-11. v4.0 retired 2024-12-31. Future-dated requirements became mandatory on 2025-03-31. An RFC on v4.0.1 ran from 2026-06-03 to 2026-07-20 with no published outcome and no next version | https://blog.pcisecuritystandards.org/just-published-pci-dss-v4-0-1, https://blog.pcisecuritystandards.org/request-for-comments-pci-data-security-standard-pci-dss-v4.0.1 | 2024-06-11; 2026-06-03 | Confirmed [V] |
| S5 | An iframe alone does not make a merchant SAQ A eligible. The January 2025 SAQ A, effective 2025-03-31, removed Requirements 6.4.3, 11.6.1 and 12.3.1 and added a criterion: the merchant confirms its site is not susceptible to attacks from scripts. Core criteria: card-not-present only, all account-data functions outsourced to PCI DSS compliant third parties, no electronic storage, processing or transmission of account data on merchant systems, paper records only. The script criterion applies to pages that embed a TPSP form (iframe), not to a full redirect (FAQ 1588, February 2025) | https://blog.pcisecuritystandards.org/important-updates-announced-for-merchants-validating-to-self-assessment-questionnaire-a, https://www.pcisecuritystandards.org/faqs/1588/ | 2025-01-30; February 2025 | Confirmed [V] |
| F65 | RBI requires all payment-system data to be stored only in India. Circular RBI/2017-18/153 (DPSS.CO.OD No.2785/06.08.005/2017-2018) of 2018-04-06: comply within 6 months, report by 2018-10-15, CERT-In audit report by 2018-12-31 | https://www.rbi.org.in/Scripts/NotificationUser.aspx?ID=11244 | 2018-04-06 | Confirmed |

### Disputes and error resolution

| ID | Value | Source | Source date | Verdict |
|---|---|---|---|---|
| F11 | Visa Dispute Condition 10.4 (card-absent fraud) has a time limit of 120 calendar days from the processing date. Visa's rules carry the test known as "Compelling Evidence 3.0" without that name: the same credential was used in 2 earlier undisputed, non-fraud-reported transactions processed more than 120 and not more than 365 days before the dispute, with a matching device ID or fingerprint, or IP address, plus one more element (login ID, shipping address). VAMP exists, but its thresholds are in a separate guide | Visa Core Rules §11.8.5.4, §11.8.5.6, §10.4.3.1 | edition 24 October 2026 | Confirmed for Visa [V] |
| F12 | Reg E 12 CFR 1005.11: consumer notice within 60 days after the statement is sent. Investigate in 10 business days, or up to 45 days with provisional credit within 10 business days. New accounts (first 30 days): 20 business days and 90 days. POS debit and transfers not initiated in a state: 90 days. Reg Z 12 CFR 1026.13: notice within 60 days of the first statement, acknowledge within 30 days, resolve within 2 complete billing cycles and never more than 90 days | https://www.ecfr.gov/current/title-12/part-1005/section-1005.11, https://www.ecfr.gov/current/title-12/part-1026/section-1026.13 | accessed 2026-11-06 | Confirmed |

### Bank rails: ACH, wires and SWIFT

| ID | Value | Source | Source date | Verdict |
|---|---|---|---|---|
| F14 | Same Day ACH third window: submit by 4:45 p.m. ET, settle 6:00 p.m. ET (since 2021-03-19). The ACH Operators set exact schedules, not the Nacha Rules. Per-entry SDA limit $1M since 2022-03-18, rising to $10M on 2027-09-17. Unauthorized consumer debit return (R10, R11): 60 days with a WSUD. Fraud monitoring phase 1 on 2026-03-20 (all ODFIs; Originators, TPSPs and TPSs with 6M+ 2023 volume; RDFIs with 10M+ receipts). Phase 2 on 2026-06-19 for all others (practical date 2026-06-22) | https://www.nacha.org/rules/expanding-same-day-ach, https://www.nacha.org/million, https://www.nacha.org/rules/increasing-same-day-ach-dollar-limit-10-million-0, https://www.nacha.org/rules/differentiating-unauthorized-return-reasons, https://www.nacha.org/rules/risk-management-topics-fraud-monitoring-phase-2 | accessed 2026-11-06 | Confirmed [V] |
| S1 | Nacha permits an Originator or ODFI to reverse an erroneous entry. The reversal must reach the RDFI within 5 banking days after the original settlement date. Erroneous entry: duplicate, wrong receiver, wrong amount, certain PPD credits after termination, and (since 2021-06-30) wrong date. "ACH has no recall" is wrong | https://www.nacha.org/rules/reversals-and-enforcement | rule effective 2021-06-30; accessed 2026-11-06 | Confirmed. Matches §11 |
| F15 | Fedwire Funds moved to ISO 20022 in a single-day cutover on 2025-07-14 and retired the FAIM format | https://www.frbservices.org/wp-content/uploads/funds-service-disclosure.pdf | December 2025 | Confirmed |
| F16 | Swift ended MT and ISO 20022 coexistence for cross-border payment instructions (CBPR+) on 2025-11-22. MT 199/299 tracking messages stay, with no end date | https://www.swift.com/standards/iso-20022/iso-20022-faqs/implementation (search index) | accessed 2026-11-06 | Confirmed [R] |
| F17 | CHIPS moved to ISO 20022 on the 2024-04-08 banking day | https://theclearinghouse.org/payment-systems/Articles/2024/04/CHIPS_Network_Migrates_ISO_20022_04-10-2024 | 2024-04-10 | Confirmed |
| S6 | CHIPS gives continuous intraday settlement with finality. A liquidity-saving algorithm matches and offsets payments, so it nets continuously, not at end of day. A payment is final and irrevocable once released and settled. 21-hour processing window. More than $2 trillion a day. Fedwire Funds is a "real-time gross settlement" system, and its transfers are "immediate, final, and irrevocable" | https://www.theclearinghouse.org/payment-systems/CHIPS, https://www.theclearinghouse.org/payment-systems/Articles/2024/04/CHIPS_Liquidity_Algorithm_04-02-2024, https://www.federalreserve.gov/paymentsystems/fedfunds_about.htm | accessed 2026-11-06; 2024-04-02; 2024-06-25 | Confirmed. The fetcher wins over §11 on wording: TCH does not use the phrase "continuous net settlement". Use TCH's words |
| F60 | Citibank meant to send Revlon's lenders about $7.8M of interest. On 2020-08-11 it sent $894M of principal plus the $7.8M. About $385M came back. Lenders holding about $500M refused. Defendants received $558,558,375.74. The Second Circuit decided on 2022-09-08 to vacate and remand: the discharge-for-value defense did not apply | https://www.govinfo.gov/content/pkg/USCOURTS-ca2-21-00487/pdf/USCOURTS-ca2-21-00487-0.pdf | 2022-09-08 | Confirmed |

### Instant payments and open banking

| ID | Value | Source | Source date | Verdict |
|---|---|---|---|---|
| F19 | EU Instant Payments Regulation (2024/886). Euro area: receive and equal charges 2025-01-09; send and Verification of Payee 2025-10-09; EMIs and PIs receive and send 2027-04-09. Non-euro area: receive 2027-01-09; send and VoP 2027-07-09. Sanctions screening at least daily | https://www.ecb.europa.eu/paym/retail/instant_payments/html/instant_payments_regulation.en.html | accessed 2026-11-06 | Confirmed |
| F20 | FedNow went live on 2023-07-20. Its network limit rose from $1M to $10M effective 2025-11-12. The RTP limit is $10M effective 2025-02-09 (it was $1M from April 2022). Participants may set lower limits. Nacha's page says RTP raised its limit "in June 2025". TCH's own release says 2025-02-09. Use TCH | https://www.federalreserve.gov/paymentsystems/fednow-additional-questions-and-answers.htm, https://www.frbservices.org/news/communications/111225-fednow-transaction-limit-increase (search index), https://prnewswire.com/news-releases/higher-10-million-rtp-network-transaction-limit-empowers-new-uses-302321724.html (TCH release), https://www.theclearinghouse.org/payment-systems/rtp/institution | Fed page 2024-12-03; TCH release 2024-12-04 | Confirmed [V] [R] |
| S2 | Instant payments are final for the sender, and each rail has a request-for-return message. A return moves money as a new payment. FedNow: a request for return of funds is a non-value message, and an actual return is a new value message (pacs.004). RTP: settlement is final and irrevocable, and the network has a Request for Return of Funds message. Do not state that the receiver must return funds | https://www.frbservices.org/binaries/content/assets/crsocms/resources/rules-regulations/040126-operating-circular-8.pdf (search index), https://www.theclearinghouse.org/payment-systems/rtp/institution | 2026-04-01; accessed 2026-11-06 | Confirmed [R]. Matches §11 |
| F22 | Pix Automatico launched on 2025-06-16. MED self-service in bank apps from 2025-10-01. MED 2.0 extends fraud refunds past the first receiving account: optional from 2025-11-26, mandatory from 2026-02-02 | https://www.bcb.gov.br/content/estabilidadefinanceira/pix/Forum_Pix_Plenaria/20250626-Forum_Pix.pdf (search index), https://www.bcb.gov.br/content/estabilidadefinanceira/pix/Forum_Pix_Plenaria/20251204-Forum_Pix.pdf | 2025-06-26; 2025-12-04 | Confirmed [R] |
| F23 | UK APP scam reimbursement is mandatory from 2024-10-07, at most GBP 85,000 per claim for Faster Payments. The Bank of England set the same for CHAPS. The government decided to fold the PSR into the FCA, which needs primary legislation (HMT response updated 2026-04-21). Confirmation of Payee: Group 2 PSPs by 2024-10-31. PS26/2 (October 2026) removed the SD17 expiry | https://www.psr.org.uk/publications/policy-statements/ps247-faster-payments-app-scams-reimbursement-requirement-confirming-the-maximum-level-of-reimbursement/ (search index), https://www.gov.uk/government/consultations/a-streamlined-approach-to-payment-systems-regulation-consultation/outcome/a-streamlined-approach-to-payment-systems-regulation-consultation-response, https://www.psr.org.uk/our-work/app-scams/confirmation-of-payee/confirmation-of-payee-requirements-under-specific-direction-17/ (search index) | October 2024; 2026-04-21; October 2026 | Confirmed [V] [R] |
| F24 | CFPB Section 1033: the codified dates still read 2026-04-01 to 2030-04-01. A court order stayed them 90 days (the first date became 2026-06-30). The ANPR of 2025-08-22 says the CFPB will propose to extend the dates. No later final rule is on consumerfinance.gov. Present no compliance date as active | https://files.consumerfinance.gov/f/documents/cfpb_personal-financial-data-rights-reconsideration_anpr_2025-08.pdf, https://www.consumerfinance.gov/rules-policy/regulations/1033/121/ | FR 2025-08-22; accessed 2026-11-06 | Confirmed [V]. Matches §11 |

### KYC, AML and sanctions

| ID | Value | Source | Source date | Verdict |
|---|---|---|---|---|
| F25a | US persons must block the property of persons on OFAC's SDN List that is in the US or in their possession or control, and generally must not deal with them | https://ofac.treasury.gov/faqs/topic/1501 (FAQ 91, FAQ 10) | FAQ 91 2024-08-21; FAQ 10 2026-10-26 | Confirmed |
| F25b | OFAC's 50 Percent Rule blocks an entity owned 50 percent or more, directly or indirectly, in the aggregate, by one or more blocked persons. It covers ownership, not control. Stakes of persons blocked under different programs add together | https://ofac.treasury.gov/faqs/topic/1521 (FAQs 398, 399, 401) | revised guidance 2014-08-13 | Confirmed |
| F25c | The Commission's DG FISMA maintains the EU consolidated list of persons, groups and entities under EU financial sanctions. It mirrors the legal acts in the Official Journal, and the Official Journal text is the authentic source | https://finance.ec.europa.eu/eu-and-world/sanctions-restrictive-measures/overview-sanctions-and-related-resources_en | page dated 2025-11-17 | Confirmed |
| F25d | The OFSI Consolidated List of Asset Freeze Targets closed, and the FCDO's UK Sanctions List became the single source for UK designations on 2026-01-28 at 09:00 UK time | https://www.gov.uk/guidance/moving-to-a-single-list-for-uk-sanctions-designations-28-january-2026 | published 2025-10-13, updated 2026-01-28 | Confirmed [V] |
| F26a | A financial institution other than a casino files a CTR for each transaction in currency of more than $10,000 (31 CFR 1010.311) | https://www.ecfr.gov/current/title-31/subtitle-B/chapter-X/part-1010/subpart-C/section-1010.311 | eCFR as of 2026-11-04 | Corrected: the trigger is "more than $10,000", not "$10,000 or more". Write "over $10,000" |
| F26b | Travel Rule: the transmittor's or intermediary financial institution passes originator and transmittal information in the transmittal order for funds transfers of $3,000 or more (31 CFR 1010.410(f)) | https://www.ecfr.gov/current/title-31/subtitle-B/chapter-X/part-1010/subpart-D/section-1010.410 | eCFR as of 2026-11-04 | Confirmed |
| F27a | A bank files a SAR no later than 30 calendar days after initial detection. With no suspect identified it may wait another 30 days, but never more than 60 days after detection. An ongoing violation also needs an immediate phone call to law enforcement (31 CFR 1020.320(b)(3)) | https://www.ecfr.gov/current/title-31/subtitle-B/chapter-X/part-1020/subpart-C/section-1020.320 | eCFR as of 2026-11-04 | Confirmed |
| F27b | Bank SAR threshold $5,000 (involves or aggregates). Money services business SAR threshold $2,000, also due within 30 calendar days. A bank keeps the SAR and supporting documents for five years from filing | https://www.ecfr.gov/current/title-31/subtitle-B/chapter-X/part-1020/subpart-C/section-1020.320, https://www.ecfr.gov/current/title-31/subtitle-B/chapter-X/part-1022/subpart-C/section-1022.320 | eCFR as of 2026-11-04 | Confirmed |
| F28a | EU AML Regulation (EU) 2024/1624 applies from 2027-07-10, and from 2029-07-10 for football agents and professional football clubs (Art. 90) | https://eur-lex.europa.eu/eli/reg/2024/1624/oj | signed 2024-05-31 | Confirmed |
| F28b | Under the AMLR, persons trading in goods or providing services may accept or make a cash payment only up to EUR 10,000. Member States may set lower limits (Art. 80) | https://eur-lex.europa.eu/eli/reg/2024/1624/oj | signed 2024-05-31 | Confirmed |
| F28c | AMLA Regulation (EU) 2024/1620 applies from 2025-07-01 (some articles from 2024-06-26). AMLA is in Frankfurt. It selects 40 obliged entities for direct supervision during 2027 and starts direct supervision during 2028 | https://eur-lex.europa.eu/eli/reg/2024/1620/oj, https://www.amla.europa.eu/about-amla_en | signed 2024-05-31; AMLA page accessed 2026-11-06 | Confirmed |
| F29a | FATF published its revision of Recommendation 16 (payment transparency) on 2025-06-18, with end-2030 as the implementation target, which FATF can adjust. The changes cover payment-chain roles, originator and beneficiary information, beneficiary information checks, the card-payment exemption and cross-border cash withdrawals | https://www.fsa.go.jp/inter/fatf/20250619/20250619.html (Japan FSA notice; FATF's page returned 403) | 2025-06-19, updated 2025-12-08 | Confirmed |
| F30a | EU Transfer of Funds Regulation (EU) 2023/1113, which carries the crypto travel rule, applies from 2024-12-30 (Art. 40) | https://eur-lex.europa.eu/eli/reg/2023/1113/oj | signed 2023-05-31 | Confirmed |
| F30b | Crypto-asset transfers carry originator and beneficiary information at any amount. For a transfer of more than EUR 1,000 to a self-hosted address, the originator's CASP assesses whether the originator owns or controls that address (Recitals 30 and 39, Art. 14) | https://eur-lex.europa.eu/eli/reg/2023/1113/oj | signed 2023-05-31 | Confirmed |
| F31 | FinCEN's final rule permanently removes BOI reporting for US companies and US persons. Only foreign entities registered to do business in a US state or tribal jurisdiction still report, and only for foreign individuals. Issued 2026-08-11, published and effective 2026-08-14. It followed the interim final rule of 2025-03-21 | https://www.fincen.gov/news/news-releases/fincen-permanently-ends-beneficial-ownership-reporting-requirements-millions, https://www.fincen.gov/boi, https://www.federalregister.gov/documents/2026/08/14/2026-16576/beneficial-ownership-information-reporting-requirement-revision | 2026-08-11 | Confirmed [V] |
| F32a | Records required under the BSA chapter are kept for five years (31 CFR 1010.430(d)) | https://www.ecfr.gov/current/title-31/subtitle-B/chapter-X/part-1010/subpart-D/section-1010.430 | eCFR as of 2026-11-04 | Confirmed |

### Wallets, licensing and safeguarding

| ID | Value | Source | Source date | Verdict |
|---|---|---|---|---|
| F33 | CSBS reports that 31 states have enacted the Money Transmission Modernization Act in full or in part, as of September 2026 | https://www.csbs.org/csbs-money-transmission-modernization-act-mtma | September 2026 | Confirmed [V] |
| F34a | EMD2 Art. 7: an e-money institution safeguards funds received for issued e-money (by the PSD methods). Funds paid by payment instrument are safeguarded once credited, and in any case within five business days of issuance | https://eur-lex.europa.eu/eli/dir/2009/110/oj | Directive 2009/110/EC | Confirmed |
| F34b | EMD2 requires issuance and redemption at par value at any moment (Art. 11) and bans interest tied to holding time (Art. 12) | https://eur-lex.europa.eu/eli/dir/2009/110/oj | Directive 2009/110/EC | Confirmed |
| F34c | PSD2 Art. 10: a payment institution safeguards user funds by segregation or by insurance. Segregation: never commingled, and funds still held at the end of the business day after receipt go to a separate credit-institution account or secure liquid low-risk assets, insulated from other creditors in insolvency. Insurance: an insurance policy, or comparable cover from an insurer or bank outside the payment institution's group | https://eur-lex.europa.eu/eli/dir/2015/2366/oj | Directive (EU) 2015/2366 | Confirmed |
| F59a | The CFPB says Synapse's partner banks held less than Synapse's records showed consumers were owed, a shortfall of between $60 million and $90 million. Consumers had no access to funds for weeks or months, and many did not get their full balance | https://www.consumerfinance.gov/enforcement/actions/synapse-financial-technologies-inc/ | complaint filed 2025-08-21 | Confirmed [V] |
| F59b | The CFPB filed in the US Bankruptcy Court for the Central District of California. The court entered a stipulated final judgment on 2025-09-12 with a $1 civil money penalty, which opens the Civil Penalty Fund. The CFPB has allocated $55,214,058 from that fund to harmed consumers | https://files.consumerfinance.gov/f/documents/cfpb_spring-2026-semi-annual-report.pdf_202610.pdf | spring 2026 semi-annual report (file dated 2026-10) | Confirmed [V] |

### Tax and billing

| ID | Value | Source | Source date | Verdict |
|---|---|---|---|---|
| F35 | The One, Big, Beautiful Bill retroactively restored the earlier Form 1099-K threshold: a third party settlement organization files only when gross payments to a payee exceed $20,000 and transactions exceed 200 | https://www.irs.gov/newsroom/irs-issues-faqs-on-form-1099-k-threshold-under-the-one-big-beautiful-bill-dollar-limit-reverts-to-20000 | IR-2025-107, 2025-10-23 | Confirmed [V] |
| F36a | ViDA is Council Directive (EU) 2025/516, in the Official Journal on 2025-03-25. Mandatory e-invoicing (EN 16931) and per-transaction digital reporting for intra-EU B2B supplies apply from 2030-07-01, with the invoice issued within 10 days of the chargeable event. Member States with a domestic real-time reporting system align it by 2035-01-01. Platform deemed-supplier rules for short-term accommodation and passenger transport apply from 2028-07-01 | https://eur-lex.europa.eu/eli/dir/2025/516/oj (Arts. 3, 5, 6) | OJ 2025-03-25 | Confirmed |
| F36b | DAC7 (Council Directive (EU) 2021/514): platform operators report seller data from 2023-01-01, by 31 January of the following year. Sellers of goods with fewer than 30 sales and no more than EUR 2,000 in a period are excluded | https://eur-lex.europa.eu/eli/dir/2021/514/oj (Art. 2, Annex V) | OJ 2021-03-25 | Confirmed |
| F37 | South Dakota v. Wayfair (decided 2018-06-21) overruled the Quill and National Bellas Hess physical-presence rule. The South Dakota law it upheld applied to sellers that deliver more than $100,000 of goods or services into the state a year, or have 200 or more separate transactions. Each state sets its own threshold, so teach the threshold as a variable | https://www.law.cornell.edu/supremecourt/text/17-494 | 2018-06-21 | Confirmed |
| F38 | ASC 606 five steps: identify the contract, identify the performance obligations, determine the transaction price, allocate the price to the performance obligations, recognize revenue when (or as) each obligation is satisfied | https://storage.fasb.org/ASU%202014-09_Section%20A.pdf | ASU 2014-09, May 2014 | Confirmed |

### Settlement cycles and post-trade

| ID | Value | Source | Source date | Verdict |
|---|---|---|---|---|
| F40 | The SEC moved the standard settlement cycle from T+2 to T+1 for most broker-dealer transactions in securities. Compliance date 2024-05-28 | https://www.sec.gov/newsroom/press-releases/2023-29 | 2023-02-15 | Corrected: scope it as "most broker-dealer securities transactions". 2024-05-28 is the compliance date |
| F41a | The EU moves to T+1 on 2027-10-11. Regulation (EU) 2025/2075 amends CSDR Art. 5(2). ESMA set an earlier step: allocation and confirmation changes by 2026-12-07. Cite ESMA (EUR-Lex text not fetched) | https://www.esma.europa.eu/node/214007, https://www.esma.europa.eu/esmas-activities/markets-and-infrastructure/shortening-settlement-cycle-t1-eu (search index), https://www.esma.europa.eu/sites/default/files/2026-07/ESMA74-2119945926-3773_T_1_statement.pdf (search index) | 2024-11-18; 2026-07 | Confirmed [R] |
| F41b | The UK moves to T+1 on 2027-10-11 under a draft SI, The Central Securities Depositories (Amendment) (Intended Settlement Date) Regulations 2026. No evidence that the final SI was made or laid. Write "draft SI" for the legal status | https://www.fca.org.uk/news/statements/government-publishes-draft-statutory-instrument-t-plus-1-settlement, https://www.gov.uk/government/publications/accelerated-settlement-t1/policy-note-mandating-t1-settlement-in-the-uk | 2025-11-21 (FCA); 2025-11-20 (HMT) | Confirmed [V]. The fetcher refines §11: the date matches, and the legal status is a draft SI |
| F42 | SEC Treasury clearing rule compliance dates, extended by one year in Release 34-102487 (2025-02-25): cash 2026-12-31, repo 2027-06-30. An SEC statement of 2026-08-07 still treats these dates as current | https://www.sec.gov/newsroom/speeches-statements/uyeda-statement-update-secs-work-toward-treasury-clearing-implementation-080726-update-secs-work-toward-treasury-clearing-implementation-august-2026 | 2026-08-07 | Confirmed [V]. The cash date is close: fetch again before you ship |

### Market structure, reporting and retention

| ID | Value | Source | Source date | Verdict |
|---|---|---|---|---|
| F43 | SEC order 34-105656 extends exemptive relief from the 2024 Reg NMS tick-size (Rules 600(b)(89)(i)(F), 612) and access-fee cap (Rule 610(c)) amendments until the first business day of November 2027. The 2024 amendments set a $0.005 increment for certain NMS stocks at or above $1.00 and a $0.001 access-fee cap for those stocks | https://www.sec.gov/newsroom/speeches-statements/atkins-statement-minimum-pricing-increments-access-fee-caps-061126, https://www.sec.gov/files/rules/exorders/2026/34-105656.pdf (search index) | 2026-06-11 | Confirmed [V] [R]. Matches §11 |
| F43b | The SEC proposed to rescind Reg NMS Rule 611 (trade-through) and Rule 610(e) (locked and crossed quotes) on 2026-06-11: Release 34-105655, File S7-2026-20, 91 FR 36656 (2026-06-17). Proposal only. Write "proposed" | https://www.sec.gov/newsroom/press-releases/2026-54-sec-proposes-rescission-regulation-nms-rules-611-610e | 2026-06-11 | Confirmed [V]. Matches §11 |
| F44 | CAT: SEC cost-savings exemptive order (Release 2025-127, 34-104144) on 2025-09-30. SEC approved the CAIS amendment (34-104586) on 2026-01-13: Industry Members no longer report customer names, addresses and years of birth, and CAIS deletes those fields. SEC concept release on a full CAT review (34-105251, S7-2026-12) on 2026-04-16 | https://www.sec.gov/newsroom/press-releases/2025-127-sec-issues-order-reduce-operating-costs-consolidated-audit-trail, https://www.sec.gov/files/rules/sro/nms/2026/34-104586.pdf (search index), https://www.sec.gov/rules-regulations/2026/04/s7-2026-12 (search index) | 2025-09-30; 2026-01-13; 2026-04-16 | Confirmed [V] [R] |
| F45a | MiFID II RTS 25 (Delegated Reg. (EU) 2017/574) clock sync. HFT activity: max divergence from UTC 100 microseconds, timestamp granularity 1 microsecond or finer. Voice trading: 1 second and 1 second. For venues, the 100 microsecond tier applies when gateway-to-gateway latency is 1 ms or less. Applies from 2018-01-03 | https://www.legislation.gov.uk/eur/2017/574/data.html (search index) | 2017 regulation | Confirmed [R] |
| F45b | RTS 22 (Delegated Reg. (EU) 2017/590) defines the MiFIR transaction-report fields in Annex I Table 2. MiFIR Art. 26(1) deadline: as soon as possible and no later than the close of the following working day | https://www.cssf.lu/en/2022/02/monitoring-the-quality-of-transaction-reports-received-under-article-26-of-mifir-2/ | 2022-02-01 | Confirmed |
| F46 | EU consolidated tape providers. Bonds: Ediphy (fairCT), selected 2025-07-03, authorisation ongoing. Shares and ETFs: EuroCTP, selected 2025-12-19, authorised (announced 2026-07-27), launched 2026-09-14. OTC derivatives: Etrading Software (Netherlands) B.V., selected 2026-07-06, authorisation ongoing | https://www.esma.europa.eu/esmas-activities/markets-and-infrastructure/consolidated-tape-providers | accessed 2026-11-06 | Confirmed [V] |
| F47 | SEC 2022 amendments to Rule 17a-4 (Release 34-96034, 2022-10-12) keep WORM as an option and add an audit-trail alternative: a system that can recreate an original record if it is modified or deleted. Effective 2023-01-03, compliance 2023-05-03 | https://www.sec.gov/rules-regulations/staff-guidance/trading-markets-frequently-asked-questions/rule-amendments-broker | FAQ 2023-01-18, updated 2026-01-06 | Confirmed |
| F32b | SEC Rule 17a-4: broker-dealers keep the core 17a-3 records (blotters, ledgers and the others in 17a-3(a)(1) to (3), (5), (21), (22)) for 6 years, and most other records for 3 years, the first 2 years easily accessible. An electronic system may use a complete time-stamped audit trail of all changes and deletions as the alternative to non-rewriteable storage | https://www.ecfr.gov/current/title-17/chapter-II/part-240/subject-group-ECFR3dd95cf4d3f6730/section-240.17a-4 | eCFR as of 2026-11-04 | Confirmed |
| F32c | FINRA Rule 4511: members keep FINRA records for at least six years where no other period is set, in a format that complies with SEA Rule 17a-4 | https://www.finra.org/rules-guidance/rulebooks/finra-rules/4511 | effective 2011-12-05 | Confirmed |
| F58 | Knight Capital, 2012-08-01: in about 45 minutes SMARS sent millions of child orders, giving over 4 million executions in 154 stocks for over 397 million shares. Knight took a net long position of about $3.5B in 80 stocks and lost more than $460 million. SEC penalty $12 million | https://www.sec.gov/litigation/admin/2013/34-70694.pdf | 2013-10-16 | Confirmed |

### Market data, order entry and matching

| ID | Value | Source | Source date | Verdict |
|---|---|---|---|---|
| F48a | Nasdaq TotalView-ITCH 5.0 is a binary feed. Timestamps are 6-byte nanoseconds since midnight. Prices have 4 implied decimals. Delivered over SoupBinTCP or MoldUDP64 (the FPGA feed is MoldUDP64 only). Latest revision-log entry 2023-04-28 | https://www.nasdaqtrader.com/content/technicalsupport/specifications/dataproducts/NQTVITCHspecification.pdf | revision log 2023-04-28 | Confirmed |
| F48b | MoldUDP64 v1.00 downstream header, 20 bytes: Session 10 bytes (offset 0), Sequence Number 8 bytes (offset 10, the number of the first message), Message Count 2 bytes (offset 18). Count 0 is a heartbeat. 0xFFFF is end of session | https://www.finra.org/sites/default/files/2024-03/moldudp64.pdf (FINRA-hosted copy of the Nasdaq spec) | file 2024-03 | Confirmed |
| F48c | Nasdaq OUCH 5.0 is tag-based with variable-length appendages (OUCH 4.2 is fixed-length). Orders are keyed by UserRefNum, unique and strictly increasing per day. Launched 2022-11-14. Spec PDF updated October 2026 | https://nasdaqtrader.com/content/technicalsupport/specifications/TradingProducts/Ouch5.0.pdf (search index), https://nasdaqtrader.com/content/productsservices/trading/OUCH_5.0_FAQ.pdf (search index) | spec 2026-10 | Confirmed [R] |
| F49 | FIX Latest is the FIX application layer: FIX 5.0 SP2 plus all later Extension Packs. EP260 added ApplVerID(1128)=10 "FIXLatest". FIX Trading Community plans no further FIX version. SBE 1.0 is the final standard (errata November 2020). SBE 2.0 is at Release Candidate 2, not a standard | https://www.fixtrading.org/transition-from-fix-5-0-sp2-to-fix-latest-completed/, https://fixtrading.org/standards/simple-binary-encoding-sbe/ | 2021-05-17; SBE page accessed 2026-11-06 | Confirmed |
| F50 | CME Globex sets a matching algorithm per product. Codes include F (FIFO), A (Allocation), K (configurable split FIFO and pro rata with LMM) and Q (threshold pro rata with LMM). FIFO replaces the product algorithm when the aggressor quantity is at or above the displayed quantity at the level. iLink 3 order entry is SBE over FIXP (iLink 3 launched on the Convenience Gateways on 2024-01-28; iLink 2 on CGW was announced for decommission on 2025-04-06, read via search index). MDP 3.0 market data is SBE over UDP multicast (MBO full depth, 10-deep MBP). Pin no product-to-algorithm list | https://www.cmegroup.com/confluence/display/EPICSANDBOX/Supported+Matching+Algorithms (search index), https://www.cmegroup.com/confluence/display/EPICSANDBOX/CME+Globex+Matching+Algorithm+Steps (search index), https://cmegroup.com/confluence/display/EPICSANDBOX/CME%20MDP%203.0%20Market%20Data (search index) | accessed 2026-11-06 | Confirmed [R] |
| F61 | LMAX: the Business Logic Processor handles 6 million orders per second on a single thread, as a benchmark on a dual-socket quad-core 3 GHz Nehalem with 32 GB. LMAX did not disclose production volumes. No end-to-end latency figure is given. Call it a benchmark, not production throughput | https://martinfowler.com/articles/lmax.html | 2011-07-12 | Confirmed |
| F63c | Coinbase Exchange matching engine: continuous order book with price-time priority. Trades execute at the resting order's price. Self-trade prevention modes `dc` (default), `co`, `cn`, `cb`. Order states `received`, `open`, `done` | https://docs.cdp.coinbase.com/exchange/concepts/matching-engine | accessed 2026-11-06 | Confirmed |

### Margin, perpetuals and liquidation

| ID | Value | Source | Source date | Verdict |
|---|---|---|---|---|
| F51 | CME SPAN 2: energy and equity products are margined under SPAN 2 (equity migration complete by January 2025). Other products stay on SPAN, with cross-model offsets. No end date for full migration found | https://www.cmegroup.com/solutions/risk-management/performance-bonds-margins/span-methodology-overview/launching-span-2.html (search index), https://www.cmegroup.com/notices/clearing/2026/08/26-276.html (search index) | notices to 2026-08 | Confirmed [V] [R] |
| F52a | Binance USD-margined (USDⓈ-M) funding: default every 8 h (00:00, 08:00, 16:00 UTC). A contract that hits its cap or floor can switch to 1 h settlement (from 2025-05-02). An hourly contract reverts to 4 h after 16 cycles at an absolute rate of 0.025% or less (from 2026-01-02). Formula F = [average premium index P + clamp(I - P, -0.05%, +0.05%)] / (8/N). Default interest 0.01% per 8 h. Cap and floor: plus or minus 0.75 x maintenance margin ratio for major contracts, plus or minus 2% for others. Funding is a venue and contract rule: teach the general mechanism and cite Binance as a dated example | https://www.binance.com/en/support/faq/introduction-to-binance-futures-funding-rates-360033525031 | updated 2026-03-06 | Confirmed [V]. Matches §11 (1, 4 or 8 h) |
| F52b | Bybit perpetual mark price = median(Price 1, Price 2, last traded price). Price 1 = index x [1 + last funding rate x (time to funding / 8)]. Price 2 uses a 2.5-minute moving average of mid minus index. If an index source is abnormal or data is too thin, mark price falls back to Bybit's last traded price | https://www.bybit.com/en/help-center/article/Mark-Price-Calculation-Perpetual-Expiry-Contracts | updated 2026-09-04 | Confirmed [V]. Matches §11 |
| F52c | Binance ADL is the last step of liquidation. It runs only if the insurance fund cannot take over a bankrupt position. Binance calls the forced close "ADL liquidation". Queue rank is `PnL% * effective_leverage` when PnL% >= 0, and `PnL% / effective_leverage` when PnL% < 0. The profitable position closes at the bankrupt position's bankruptcy price, with no fee. The insurance fund absorbs losses and keeps profits of bankrupt positions it takes over | https://www.binance.com/en/support/faq/what-is-auto-deleveraging-adl-and-how-does-it-work-360033525471, https://www.binance.com/en/support/faq/introduction-to-futures-insurance-funds-360033525371 (search index) | ADL FAQ updated 2026-01-13 | Confirmed [V] [R]. Matches §11 |
| F52e | CME lists no perpetual futures. Its Q2 2026 earnings script says it has specs ready but has not heard client demand. Do not cite CME as a funding-rate or perpetual mark-price example | https://investor.cmegroup.com/static-files/f03a4407-750b-4ce3-a3db-02d59b60b8a6 (search index) | 2026-07-22 | Corrected [R]: CME is not a perpetual funding example |

### Crypto custody, stablecoins and MiCA

| ID | Value | Source | Source date | Verdict |
|---|---|---|---|---|
| F54 | MiCA: Titles III and IV (ARTs, EMTs) apply from 2024-06-30, the rest from 2024-12-30. The Art. 143(3) CASP transitional period ended at the latest on 2026-07-01 (earlier in Member States that chose so). ESMA, 2026-06-23: unauthorised CASPs must stop onboarding and wind down. Write the transitional period in the past tense | https://www.esma.europa.eu/sites/default/files/2026-06/ESMA75-113276571-1710_Public_Statement_MiCA_transitional_period_ends.pdf, https://www.esma.europa.eu/esmas-activities/digital-finance-and-innovation/markets-crypto-assets-regulation-mica, https://www.eba.europa.eu/sites/default/files/2024-07/7dcd9ce9-96e3-4c5c-8d86-39d7784d1f03/EBA%20statement%20on%20Application%20of%20MiCAR%20to%20ARTs%20and%20EMTs.pdf (search index) | 2026-06-23; 2024-07-05 | Confirmed [R] |
| F55a | GENIUS Act (S. 1582) is Public Law 119-27, signed 2025-07-18. It takes effect on the earlier of 18 months after enactment (2027-01-18) or 120 days after the primary regulators issue final rules | https://www.govinfo.gov/metadata/pkg/PLAW-119publ27/mods.xml, https://www.congress.gov/119/plaws/publ27/PLAW-119publ27.pdf (search index) | 2025-07-18 | Confirmed |
| F55b | OCC final rule implementing the GENIUS Act (OCC Bulletin 2026-50): new 12 CFR 15. It covers national banks, FSAs, federal branches, foreign issuers, nonbank federal qualified issuers and some state issuers. BSA, AML and OFAC rules are a separate rulemaking. Issued 2026-10-21, effective 2027-01-18 | https://occ.gov/news-issuances/bulletins/2026/bulletin-2026-50.html | 2026-10-21 | Confirmed [V]. Matches §11 |
| F55c | The Federal Reserve Board proposed two GENIUS Act rules on 2026-09-24: reserve, capital and custody standards for Board-supervised issuers, and an application process. Docket R-1899. Comments due 2026-11-30 | https://federalreserve.gov/newsevents/pressreleases/bcreg20260924a.htm, https://govinfo.gov/content/pkg/FR-2026-09-29/html/2026-19860.htm | 2026-09-24; FR 2026-09-29 | Confirmed [V]. Matches §11. Fetch again after 2026-11-30 |
| F56a | Binance proof of reserves: a Merkle tree of user net balances per asset, plus a zk-SNARK circuit that proves the leaf balances sum to the stated total and each net balance is non-negative. Each user can verify their leaf | https://www.binance.com/en/proof-of-reserves | accessed 2026-11-06 | Confirmed |
| F56b | Kraken proof of reserves: an independent accountant builds a Merkle tree from an anonymized balance snapshot and checks that on-chain holdings meet or exceed it. Clients verify inclusion. Kraken lists the limits: it cannot prove exclusive key control, cannot see hidden encumbrances, and is a snapshot only. Latest snapshot 2026-06-30 | https://www.kraken.com/proof-of-reserves | snapshot 2026-06-30 | Confirmed [V] |
| F57a | BIP32 (HD wallets) and BIP44 (path m / purpose' / coin_type' / account' / change / address_index) both have status Deployed | https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki, https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki | accessed 2026-11-06 | Confirmed |
| F57b | EIP-1559 has status Final. The base fee per gas is burned, and the validator keeps only the priority fee. `BASE_FEE_MAX_CHANGE_DENOMINATOR` = 8 (at most 12.5% change per block). `ELASTICITY_MULTIPLIER` = 2 | https://github.com/ethereum/EIPs/blob/master/EIPS/eip-1559.md | created 2019-04-13 | Confirmed |
| F57c | Ethereum PoS: 12 s slots, 32-slot epochs. A checkpoint is finalized when a supermajority link (2/3 of staked ETH) joins two checkpoints. The page gives no finality time. You may write "about two epochs (64 slots x 12 s = 12.8 min) in the normal case" only as arithmetic | https://ethereum.org/en/developers/docs/consensus-mechanisms/pos/ | updated 2026-10-07 | Confirmed |

### Pins added during the build

Builders fetched these rows from primary sources on 2026-11-06, after Phase 0, and reported
them to the integrator. The same pin rules apply. Rows marked "Measured" were run in the
local `eclipse-temurin:25-jdk` and `postgres:18` images.

#### Calendars, cut-offs and rail hours

| ID | Value | Source | Source date | Verdict |
|---|---|---|---|---|
| P1 | Federal Reserve holiday schedule lists the 2026 and 2027 holidays. A holiday on a Saturday: the Federal Reserve Banks open on the preceding Friday. A holiday on a Sunday: they close on the following Monday. On 2027-06-18 and 2027-12-24 the Federal Reserve Banks are open and the Board is closed | https://www.federalreserve.gov/aboutthefed/k8.htm | updated 2026-07-08 | Confirmed [V] |
| P2 | Fedwire Funds Service is closed on Saturdays, Sundays and Federal Reserve holidays. Its business day runs from 9:00 p.m. ET on the preceding calendar day to 7:00 p.m. ET. Customer transfers (pacs.008) close at 6:45 p.m. ET (CTRC). Bank transfers (pacs.009) close at 7:00 p.m. ET (BTRC) | frbservices.org, Fedwire Funds Service operating hours page | accessed 2026-11-06 | Confirmed [V] |
| P3 | FedNow processes payments 24x7x365, with a 24-hour business day every day, weekends and holidays included | https://www.federalreserve.gov/paymentsystems/fednow_about.htm | 2023-07-20 | Confirmed |
| P4 | NYSE publishes its 2026 and 2027 holiday lists. Core trading runs 9:30 a.m. to 4:00 p.m. ET. Early closes at 1:00 p.m. ET on 2026-11-27 and 2026-12-24 | https://www.nyse.com/markets/hours-calendars | accessed 2026-11-06 | Confirmed [V] |
| P5 | T2 is open Monday to Friday. It is closed on 1 January, Good Friday, Easter Monday, 1 May, 25 December and 26 December | ecb.europa.eu, T2 service pages | accessed 2026-11-06 | Confirmed |

#### Ledgers, idempotency and money types

| ID | Value | Source | Source date | Verdict |
|---|---|---|---|---|
| P6 | TigerBeetle 0.17.9 accounts keep debit and credit counters (pending and posted) as u128. Account flags include `debits_must_not_exceed_credits`. The reference also gives the two-phase transfer rules and the result codes | https://docs.tigerbeetle.com/reference/account | accessed 2026-11-06 | Confirmed [V] |
| P7 | Stripe idempotency keys are up to 255 characters. Stripe may prune a key after it is at least 24 hours old. Stripe saves the first response for a key, 500 errors included, and returns it on a retry | Stripe API reference, idempotent requests | accessed 2026-11-06 | Confirmed [V] |
| P8 | Stripe amounts are in the currency's minor unit. ISK and UGX are sent as two-decimal amounts that end in 00. HUF and TWD payouts must be divisible by 100 | Stripe docs, supported currencies | accessed 2026-11-06 | Confirmed [V] |
| P9 | Adyen idempotency keys are up to 64 characters and are kept for 7 to 14 days. Error code 704 rejects a reused key | Adyen docs, API idempotency | accessed 2026-11-06 | Confirmed [V] |
| P10 | draft-ietf-httpapi-idempotency-key-header-08 defines the status codes a server returns for idempotency-key errors (key missing, key reused with a different payload, request still in progress) | https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/ | 2026-10-22 | Confirmed [V]. See F64 |
| P11 | JDK 25.0.5: `Currency.getInstance("UYW")` throws, and `Currency.getInstance("XAU").getDefaultFractionDigits()` returns -1 | run in `eclipse-temurin:25-jdk` | 2026-11-06 | Measured |
| P12 | PostgreSQL 18.6: `round(2.5::numeric)` = 3, `round(2.5::float8)` = 2, and `SUM(bigint)` returns `numeric` | run in `postgres:18` | 2026-11-06 | Measured |

#### Cards, SCA and interchange

| ID | Value | Source | Source date | Verdict |
|---|---|---|---|---|
| P13 | Visa Core Rules and Visa Product and Service Rules, edition 24 October 2026: glossary terms; §5.3.1.4, a sponsored merchant with more than USD 1M in annual Visa volume needs a direct agreement with an acquirer; Table 7-2 decline categories; Table 5-21 stored-credential rules, including a 7-day notice after a declined MIT; §8.6.1.1 Visa Account Updater enrollment; Table 11-29 ECI rules for Dispute Condition 10.4; §7.9.1.1 SCA for EEA and UK e-commerce; US credit surcharge at most 3.00% | https://usa.visa.com/dam/VCOM/download/about-visa/visa-rules-public.pdf | edition 24 October 2026 | Confirmed for Visa [V] |
| P14 | EBA Q&A 2020_5619 and 2018_4414 on SCA application | https://www.eba.europa.eu/single-rule-book-qa/qna/view/publicId/2020_5619, https://www.eba.europa.eu/single-rule-book-qa/qna/view/publicId/2018_4414 | 2021-04-23; 2021-02-26 | Confirmed |
| P15 | Reg II debit routing: an issuer must enable at least two unaffiliated networks on each debit card, and must not inhibit a merchant's choice of network. 12 CFR 235.5(a) exempts an issuer that, with its affiliates, has less than $10 billion in assets from the interchange cap | federalreserve.gov Regulation II page, https://www.ecfr.gov/current/title-12/part-235/section-235.5 | Fed page 2024-05-17; eCFR accessed 2026-11-06 | Confirmed [V] |
| P16 | Secure Payment Confirmation is a W3C Candidate Recommendation Draft | https://www.w3.org/TR/secure-payment-confirmation/ | 2026-10-13 | Confirmed [V] |
| P17 | RBI Directions issued 2025-09-25 and 2026-04-21, cited by title | https://www.rbi.org.in | 2025-09-25; 2026-04-21 | Confirmed |

#### Bank rails and instant payments

| ID | Value | Source | Source date | Verdict |
|---|---|---|---|---|
| P18 | Nacha ACH file format: 94-character records; record types 1, 5, 6, 7, 8 and 9; blocking factor 10; transaction codes 22, 27, 32 and 37 (checking credit, checking debit, savings credit, savings debit). IAT entries are not eligible for Same Day ACH | Nacha Operating Rules, ACH file format | accessed 2026-11-06 | Confirmed |
| P19 | FedNow settles in Federal Reserve master accounts, supports liquidity management transfers (LMTs) and an optional request for payment, and uses ISO 20022 messages | https://www.federalreserve.gov/paymentsystems/fednow-additional-questions-and-answers.htm | 2024-12-03 | Confirmed |
| P20 | The RTP network launched in 2017. It runs 24/7, supports request for payment, and its payments are final and irrevocable | https://www.theclearinghouse.org/payment-systems/rtp/institution | accessed 2026-11-06 | Confirmed |
| P21 | EU Instant Payments Regulation, Verification of Payee: the outcome is a match, a close match, no match, or verification not possible. The service is free to the payer. PSPs screen their customers against EU sanctions at least daily. EMIs and PIs outside the euro area apply the rules from 2027-07-09 | https://www.ecb.europa.eu/paym/retail/instant_payments/html/instant_payments_regulation.en.html | accessed 2026-11-06 | Confirmed. See F19 |
| P22 | CLS settles FX payment-versus-payment in 18 currencies, more than USD 8.0 trillion a day, with netting above 96% | cls-group.com | accessed 2026-11-06 | Confirmed [V] |
| P23 | BIS CPSS report "Settlement risk in foreign exchange transactions" defines FX settlement risk | BIS CPSS publication | 1996-03-09 | Confirmed |

#### PCI, KYC, AML and disputes

| ID | Value | Source | Source date | Verdict |
|---|---|---|---|---|
| P24 | PCI SSC FAQ 1154: under Requirement 3.3.1, sensitive authentication data is not stored after authorization, even when encrypted | PCI SSC FAQ 1154 | July 2025 | Confirmed |
| P25 | PCI SSC FAQ 1293 says which payment-page elements decide SAQ A vs SAQ A-EP | PCI SSC FAQ 1293 | accessed 2026-11-06 | Confirmed |
| P26 | PCI DSS customized approach: each customized requirement needs a targeted risk analysis (Requirement 12.3.2), compensating controls do not apply to it, and it is validated only by a ROC. SAQ D is the catch-all for merchants that fit no other SAQ | PCI SSC documents | accessed 2026-11-06 | Confirmed [R] |
| P27 | 31 CFR 1020.220 sets the minimum CIP fields. 31 CFR 1010.230(d) defines beneficial owners as each person with 25% or more ownership plus one control person. 31 CFR 1020.320(e) makes a SAR confidential | https://www.ecfr.gov/current/title-31/subtitle-B/chapter-X | eCFR as of 2026-11-04 | Confirmed |
| P28 | OFAC FAQ 401 on the 50 Percent Rule | https://ofac.treasury.gov/faqs/topic/1521 | revised guidance 2014-08-13 | Confirmed. See F25b |
| P29 | Visa Core Rules §11.3.1 to §11.3.3 dispute timelines: 30, 30, 30 and 10 days for dispute categories 12 and 13. A credit-netting change takes effect 2027-04-24 | https://usa.visa.com/dam/VCOM/download/about-visa/visa-rules-public.pdf | edition 24 October 2026 | Confirmed for Visa [V] |
| P30 | Visa Dispute Management Guide for Merchants | visa.com | October 2026 | Confirmed [V] |

#### Billing and issuing vendor docs

These are optional rows. They hold vendor behavior, not rules.

| ID | Value | Source | Source date | Verdict |
|---|---|---|---|---|
| P31 | Stripe Billing prorates to the second. Meter events are accepted up to 35 days in the past and 5 minutes in the future. The meter event endpoint allows 1,000 calls a second. Smart Retries defaults to 8 tries within 2 weeks | Stripe Billing docs | read 2026-11-06 | Confirmed [V] |
| P32 | Lago drops duplicate usage events by `transaction_id` | Lago docs | read 2026-11-06 | Confirmed [V] |
| P33 | Stripe Issuing gives about 2 seconds to answer a real-time authorization. Most captures arrive within 24 hours. Travel merchants can capture up to 31 days later | Stripe Issuing docs | read 2026-11-06 | Confirmed [V] |
| P34 | Lithic Auth Stream Access (ASA) times out after 6 seconds. Lithic recommends a response within 3 seconds | Lithic docs | read 2026-11-06 | Confirmed [V] |
| P35 | Marqeta product names: JIT Funding and Commando Mode | Marqeta docs | read 2026-11-06 | Confirmed |

#### Brokers, perpetuals and post-trade

| ID | Value | Source | Source date | Verdict |
|---|---|---|---|---|
| P36 | FINRA Rule 5310 Supplementary Material .09: a firm that routes orders without an order-by-order review does a regular and rigorous review at least quarterly (effective 2014-05-09). Rule 5310(a)(1) lists the best-execution factors | https://www.finra.org/rules-guidance/rulebooks/finra-rules/5310 | accessed 2026-11-06 | Confirmed |
| P37 | SEC Rule 606(a): a quarterly report of the top 10 venues plus any venue with 5% or more of orders, with net payment for order flow, published within one month after the quarter and kept public for 3 years. Rule 606(b)(3): a customer report on request within seven business days | 17 CFR 242.606 | accessed 2026-11-06 | Confirmed |
| P38 | 12 CFR 220.8 (Reg T): a cash account that sells a security before paying for it is frozen for 90 days | https://www.ecfr.gov/current/title-12/part-220/section-220.8 | accessed 2026-11-06 | Confirmed |
| P39 | FINRA AWC with Robinhood Financial, 2021-06-30: a $57 million fine and $12,598,445.16 in restitution | FINRA AWC | 2021-06-30 | Confirmed [R] |
| P40 | Hyperliquid pays funding every hour at one eighth of the computed 8-hour rate. Funding is capped at 0.5% an hour. Maintenance margin is half the initial margin at maximum leverage. The docs also describe liquidation and ADL | https://hyperliquid.gitbook.io/hyperliquid-docs/trading/funding | accessed 2026-11-06 | Confirmed [V] |
| P41 | Binance funding FAQ: the funding amount is mark price x position size, and Binance charges no fee on funding. Binance ADL FAQ (2026-01-13) as in F52c | https://www.binance.com/en/support/faq/introduction-to-binance-futures-funding-rates-360033525031 | 2026-03-06 | Confirmed [V] |
| P42 | DTCC CNS, Q3 2026: 98.6% netting and $18.3B average margin | dtcc.com | Q3 2026 | Confirmed [V] |
| P43 | PFMI cover-2 wording: a CCP with a more complex risk profile or systemic importance in several jurisdictions keeps financial resources to cover the default of the two participants that would cause the largest exposure | CPSS-IOSCO PFMI, via the BIS database | 2012-04-16 | Confirmed |

#### Exchanges, market data and order entry

| ID | Value | Source | Source date | Verdict |
|---|---|---|---|---|
| P44 | FIX Latest dictionary on Orchimate: tags, MsgType values, and ExecType and OrdStatus codes. In a ResendRequest, EndSeqNo = 0 means infinity | orchimate.org | accessed 2026-11-06 | Confirmed |
| P45 | FIXP was derived from SoupBinTCP and UFO | fixtrading.org | accessed 2026-11-06 | Confirmed |
| P46 | Nasdaq TotalView-ITCH 5.0: the Add Order message is 36 bytes; fields are unsigned big-endian; Price(4) has a maximum of 200,000.0000; stock locate codes are assigned each day from 1; GLIMPSE is the snapshot service | https://www.nasdaqtrader.com/content/technicalsupport/specifications/dataproducts/NQTVITCHspecification.pdf | revision log 2023-04-28 | Confirmed. See F48a |
| P47 | MoldUDP64 1.00 gives the request, heartbeat and retransmit rules | https://www.finra.org/sites/default/files/2024-03/moldudp64.pdf | file 2024-03 | Confirmed. See F48b |
| P48 | SBE message structure: fixed-length fields, then repeating groups, then variable-length data. The message header carries blockLength, templateId, schemaId and version. Reference schema repository FIXTradingCommunity, v1-0-STANDARD | https://fixtrading.org/standards/simple-binary-encoding-sbe/ | accessed 2026-11-06 | Confirmed |
| P49 | Coinbase Exchange self-trade prevention modes `dc`, `co`, `cn` and `cb` | https://docs.cdp.coinbase.com/exchange/concepts/matching-engine | accessed 2026-11-06 | Confirmed. See F63c |
| P50 | 17 CFR 240.15c3-5, the market access rule: broker-dealers with market access keep pre-trade risk controls | Cornell LII copy of 17 CFR 240.15c3-5 | accessed 2026-11-06 | Confirmed [R]. Fetch again from eCFR before audit |
| P51 | SEC press release 2013-222: Knight Capital paid $12 million for violating the market access rule | SEC press release 2013-222 | 2013-10-16 | Confirmed. See F58 |
| P52 | JEP 306 (Java 17) made floating-point operations strict everywhere. The `java.lang.Math` Javadoc for Java 25 says Math methods need not give bit-for-bit identical results across platforms, unlike `StrictMath` | https://openjdk.org/jeps/306, Java 25 `java.lang.Math` Javadoc | accessed 2026-11-06 | Confirmed |
| P53 | The Aeron README describes Aeron Cluster as Raft-based | Aeron README on GitHub | accessed 2026-11-06 | Confirmed |

#### Regulation: DORA and EMIR

| ID | Value | Source | Source date | Verdict |
|---|---|---|---|---|
| P54 | DORA, Regulation (EU) 2022/2554, applies from 2025-01-17 | EIOPA DORA page | fetched 2026-11-06 | Confirmed |
| P55 | EMIR: counterparties report all derivatives to trade repositories. The revised reporting RTS and ITS apply from 2024-04-29 | ESMA EMIR reporting page | fetched 2026-11-06 | Confirmed |

### Do not state

These items failed to fetch or are not in the fetched source. They stay out of every file. A
builder that needs one fetches its primary source first and reports the new row to the
integrator.

Rows with no confirmed part:

- F18: SEPA SCT and SDD rulebook timings, SDD refund rights (the 8-week refund) and SCT Inst
  amount rules (the removed EUR 100,000 cap). The EPC site returned 403 and EUR-Lex (PSD2
  Arts. 71 and 76) returned empty pages.
- F21: every UPI rule, limit and volume. npci.org.in served an empty page to the fetcher.
- S7: ISO 8583 MTI codes (0100/0110, 0200/0210) and the mapping of single-message to PIN debit
  and ATM. The ISO text is paywalled and the Mastercard rule PDFs returned 403. The fetcher wins
  over §11 here.
- F29b: amount thresholds in the revised FATF R.16.
- F52d: the BitMEX funding formula and interval. Use Binance as the funding example.
- F53: the 2025-10-10/11 crypto liquidation figures (about $19B, about 1.6M accounts) and the
  Hyperliquid ADL event. The figures are aggregator estimates with no exchange primary source.
  Omit the event.
- F59c: the number of Synapse end users affected.

Parts of confirmed rows:

- F2, F3: every Mastercard rule (authorization windows, Merchant Advice Codes, retry limits).
- F4: the outcome of the Reg II litigation (Corner Post, Linney's Pizza).
- F5: a Visa token target. Mastercard tokenization progress figures from 2025 and 2026.
- F7: an RFC outcome or a date for a next PCI DSS version.
- F8: the publication date of v2.3.1.1. Any claim that v2.4 is published.
- F9: the reference fraud rate for the EUR 500 TRA tier, and the credit-transfer TRA table.
- F10: any PSD3 or PSR application date.
- F11: VAMP thresholds. Mastercard ECM and EFM thresholds and time frames.
- F13: card migration onto the Discover network.
- F14: the first and second Same Day ACH window times, and the general 2-banking-day return
  window.
- F23: that the PSR is abolished (Royal Assent of the 2026 Bill is not confirmed).
- F24: any CFPB 1033 compliance date as active.
- F39: Mastercard Move reach figures.
- F43: round-lot and odd-lot data compliance dates.
- F44: CAT budget figures beyond the 2025-09-30 press release.
- F45b: the count of RTS 22 fields (for example 65). ESMA's June 2025 final report revises
  Table 2.
- F56a: reserve ratios.
- F57c: "about 15 minutes" as Ethereum finality time.
- F63c: the Coinbase derivatives engine throughput claim (100k orders/s).

---

## Fact corrections

Do not repeat these errors. The first list comes from PLAN §6, updated by PLAN §11. The second
list records where a fetcher corrected the plan or §11. In both, the fetched pin wins.

Domain rules:

- Clearing is not settlement. Authorization moves no money.
- Interchange flows from the acquirer to the issuer, not to the network.
- Visa and Mastercard do not issue cards.
- SWIFT moves messages, not money. ISO 20022 is a message standard, not a rail.
- Same Day ACH is not real-time.
- ACH has reversals. Nacha permits an Originator or ODFI to reverse an erroneous entry within 5
  banking days after settlement (S1).
- Instant rails are push payments, final for the sender. FedNow and RTP each have a
  request-for-return message, and a return is a new payment. The receiver is not obliged to
  return funds (S2).
- "Exactly once" means effect, not delivery.
- Reconciliation is not "the final arbiter". Authority is scoped. The PSP or network record
  proves the card event. The ledger proves the obligation. The bank statement proves cash.
  Reconciliation finds and resolves the disagreements between them.
- A decimal type without an explicit scale and rounding mode is still wrong.
- Integer width follows max notional x scale. Satoshi balances fit in int64. Large wei amounts
  need 128 bits or more (W1).
- A compliant PSP does not make the merchant compliant.
- Tokenization is not encryption.
- An iframe or hosted page alone does not establish SAQ A eligibility. State the published
  criteria and their date (S5). State no RFC outcome (F7).
- The issuer makes the final SCA decision. The issuer can apply an exemption, and for some
  exemptions the acquirer can apply it. The issuer can still step up. MITs are out of scope by
  classification, not by exemption (S3).
- PSD3 and the PSR: state the Council adoption of 2026-10-29 and the pending Parliament vote.
  Pin no application date (F10).
- CFPB 1033: the compliance dates are stayed and the rule is under reconsideration. Present no
  active timetable (F24).
- Proof of reserves without liabilities proves nothing.
- Price-time priority is not universal. CME sets the matching algorithm per product, and some
  products use pro-rata variants (F50).
- T+1 is a settlement cycle. It covers most US broker-dealer securities transactions since
  2024-05-28 (F40). The EU moves on 2027-10-11 (F41a) and the UK on 2027-10-11 under a draft SI
  (F41b).
- Fedwire is RTGS. CHIPS gives continuous intraday settlement with finality through a
  liquidity-saving algorithm, not end-of-day DNS (S6).
- Reg NMS: tick-size and access-fee relief runs to the first business day of November 2027
  (F43). The Rule 611 rescission is a proposal (F43b).
- GENIUS Act: the OCC final rule is effective 2027-01-18 (F55b). The Federal Reserve proposal
  takes comments through 2026-11-30 (F55c).
- Liquidation uses the mark price, not the last price. Mark price, the funding formula and the
  funding interval are venue and contract rules. Teach the general mechanism and cite a venue
  with its date (F52a, F52b).
- Funding is paid between longs and shorts.
- ADL is not liquidation. It is the venue-defined final step of the liquidation process, after
  the insurance fund. Binance calls the forced close "ADL liquidation" (F52c).
- No pinned source sets a fuzzy-match threshold for sanctions screening. Teach the threshold as
  a risk-appetite choice.
- The CTR trigger is a currency transaction of more than $10,000 (F26a).

Where the fetchers overrode the plan or §11:

| Item | Plan or §11 said | Fetched fact (use this) |
|---|---|---|
| F1 | CLF is the only 4-decimal currency | CLF and UYW are both 4 |
| F6 | PAN truncation "first 8 with last 4" | "first 8, any other 4", as a storage maximum |
| F10 | OJ publication pending after the April 2026 compromise texts | Council adopted first-reading positions on 2026-10-29. Parliament vote pending |
| F26a | CTR at $10,000 | more than $10,000 |
| F40 | US T+1 effective 2024-05-28 with no scope | most broker-dealer securities transactions, compliance date 2024-05-28 |
| F41b | UK T+1 on 2027-10-11 | same date, under a draft SI |
| F52e | CME as a funding or mark-price example | CME lists no perpetual futures |
| S3 (§11) | the acquirer or PSP requests, the issuer decides | the issuer or (for some exemptions) the acquirer applies it, the issuer decides |
| S6 (§11) | "continuous intraday net settlement" | continuous intraday settlement with finality, in TCH's words |
| S7 (§11) | dual-message 0100/0110 vs single-message 0200/0210, single for PIN debit and ATM | not confirmed: state neither the MTI codes nor the mapping |
| F20 | RTP limit date from Nacha ("June 2025") | 2025-02-09, from TCH's own release |

---

## Glossary

One definition each, so that modules agree. A module may expand a term. It must not contradict
the term here.

| Term | Definition |
|---|---|
| Authorization | The issuer's approval of a card transaction. It reserves funds and moves no money |
| Hold | The amount an authorization reserves against the cardholder's available balance until capture, reversal or expiry |
| Capture | The merchant's instruction to collect an authorized amount |
| Clearing | The exchange of transaction details between acquirer and issuer through the network, which fixes what each party owes |
| Settlement | The movement of funds between the parties to discharge what clearing fixed |
| Funding | The acquirer or PSP paying settled funds to the merchant, net of fees |
| Payout | A transfer from a platform to a recipient's bank account, card or wallet |
| Journal entry | A balanced set of postings that records one business event |
| Posting | One debit or credit line of a journal entry against one account |
| Ledger balance | The sum of posted entries on an account |
| Available balance | The ledger balance minus holds and pending debits, which is what the customer can spend |
| Value date | The date from which funds count for interest and availability |
| Cut-off | The latest time a rail or bank accepts an instruction for a given processing date |
| Return | A bank-rail message that sends back an entry the receiving bank cannot or must not post |
| Reversal | A message that undoes an earlier entry or authorization through the same rail |
| Refund | A new merchant-initiated credit to the payer for an earlier payment |
| Chargeback | An issuer-initiated dispute that takes funds back from the acquirer under network rules |
| Representment | The acquirer's response to a chargeback, with evidence, to reclaim the funds |
| MDR | Merchant discount rate: what the merchant pays per transaction, which is interchange plus scheme fees plus the acquirer's markup |
| Interchange | The fee the acquirer pays to the issuer on a card transaction |
| Scheme fee | The fee a card network charges its members |
| IC++ | Pricing that passes interchange and scheme fees through at cost and adds a stated acquirer markup |
| BIN | The leading digits of a PAN that identify the issuer and product |
| PAN | Primary account number, the card number |
| Network token | A network-issued substitute for a PAN, usable only in a restricted domain such as one merchant or one device |
| PSP | Payment service provider: a company that gives merchants payment acceptance through one integration |
| Acquirer | The bank that holds the merchant relationship and receives card settlements for the merchant |
| Issuer | The bank that issues the card and holds the cardholder relationship |
| PayFac | Payment facilitator: a master merchant that boards sub-merchants under its own acquirer account |
| MoR | Merchant of record: the legal seller on the transaction, which holds the liability for it |
| FBO account | A "for benefit of" bank account that holds customer funds in the platform's name for its customers |
| Nostro and vostro | The same correspondent account seen from each side: "our account at your bank" and "your account at our bank" |
| RTGS | Real-time gross settlement: each payment settles individually and with finality as it is processed |
| DNS | Deferred net settlement: payments net over a period and settle as net amounts at set times |
| CCP | Central counterparty: a clearing house that becomes buyer to every seller and seller to every buyer |
| Novation | The replacement of the original contract between two parties with two contracts against the CCP |
| DvP | Delivery versus payment: securities move only if the matching payment moves |
| IM | Initial margin: collateral that covers potential future loss on a position |
| VM | Variation margin: the daily or intraday transfer of mark-to-market gains and losses |
| MM | Maintenance margin: the minimum margin that keeps a position open. Below it, liquidation starts |
| Mark price | A venue-defined fair price for margin and liquidation, which resists manipulation of the last trade price |
| Funding rate | A periodic payment between longs and shorts on a perpetual contract that pulls its price toward the index |
| ADL | Auto-deleveraging: the venue-defined final step of liquidation that closes opposing profitable positions after the insurance fund cannot absorb a loss |
| NBBO | National best bid and offer across US equity venues |
| MBO | Market by order: a feed with every individual order |
| MBP | Market by price: a feed with aggregate quantity per price level |
| ClOrdID | FIX client order id. Each new, cancel or replace request carries a new one, and OrigClOrdID points to the previous one |
| Drop copy | A copy of a firm's execution reports, sent to risk, compliance or clearing systems |

---

## Module list (28 modules, 6 phases)

The slugs are frozen. Renaming a module directory re-keys its questions (see "Question ids").

| # | Phase | Module directory | Q&A floor | Tiers | Facts |
|---|---|---|---|---|---|
| 1 | 1 Money and the Ledger | `money_representation_and_currency/` | 18 | senior | F1, W1 |
| 2 | 1 | `double_entry_ledger_design/` | 18 | senior, principal | F62, F63a, F63b |
| 3 | 1 | `idempotency_and_exactly_once_money_movement/` | 18 | senior, principal | F64, S1, S2 |
| 4 | 2 Payment Rails and the Ecosystem | `psps_acquirers_issuers_and_networks/` | 15 | senior | F13 |
| 5 | 2 | `card_payments_lifecycle/` | 18 | senior, principal | F2, F3, F5 |
| 6 | 2 | `bank_rails_ach_sepa_and_wires/` | 18 | senior | F14, F15, F16, F17, S1, S6, F60 |
| 7 | 2 | `real_time_payments_and_open_banking/` | 15 | senior | F10, F19, F20, S2, F22, F23, F24 |
| 8 | 3 Risk, Security and Compliance | `strong_customer_authentication_and_3ds/` | 15 | senior | F8, F9, F10, S3 |
| 9 | 3 | `pci_dss_scope_and_tokenization/` | 18 | senior, principal | F5, F6, F7, S5, F65 |
| 10 | 3 | `fraud_and_risk_scoring/` | 18 | senior | F11 (where cited) |
| 11 | 3 | `kyc_aml_and_sanctions_screening/` | 18 | principal | F25a to F28c, F29a, F31, F32a |
| 12 | 3 | `disputes_chargebacks_and_refunds/` | 15 | senior | F11, F12 |
| 13 | 4 Money Operations | `reconciliation_and_settlement_files/` | 18 | senior, principal | F59a, F60 |
| 14 | 4 | `wallets_stored_value_and_payouts/` | 15 | senior | F33, F34a to F34c, F35, F39 |
| 15 | 4 | `subscriptions_billing_and_invoicing/` | 18 | senior | F38 |
| 16 | 4 | `tax_for_payment_engineers/` | 15 | - (Full only) | F35, F36a, F36b, F37 |
| 17 | 4 | `treasury_liquidity_and_fx_operations/` | 15 | principal | F16 |
| 18 | 5 Markets and Trading Systems | `market_microstructure_and_order_types/` | 18 | senior | F43, F43b, F46 |
| 19 | 5 | `order_books_and_matching_engines/` | 18 | senior | F50, F63c |
| 20 | 5 | `exchange_architecture_and_determinism/` | 18 | senior, principal | F45a, F61, F63c |
| 21 | 5 | `market_data_feeds_and_multicast/` | 15 | senior | F48a, F48b, F49, F50 |
| 22 | 5 | `fix_protocol_and_order_entry/` | 15 | senior | F48c, F49, F50 |
| 23 | 5 | `low_latency_trading_systems/` | 15 | senior | F58, F61 |
| 24 | 6 Post-Trade, Digital Assets and Regulation | `ccp_clearing_and_settlement/` | 15 | principal | F40, F41a, F41b, F42 |
| 25 | 6 | `margin_liquidation_and_derivatives_risk/` | 18 | senior, principal | F51, F52a, F52b, F52c |
| 26 | 6 | `crypto_custody_and_onchain_operations/` | 18 | principal | F29a, F30a, F30b, F54, F55a to F55c, F56a, F56b, F57a to F57c, W1 |
| 27 | 6 | `regulatory_reporting_and_audit_trails/` | 15 | principal | F27a, F32a to F32c, F44, F45a, F45b, F47, F58 |
| 28 | 6 | `build_vs_buy_and_licensing_strategy/` | 15 | principal | F33, F34a to F34c, F59a, F59b |

The floor is a floor. The target is about 20 Q&As per module page (22 for the 18-floor
modules). Floor total for the section, sub-files included: 495.

The Facts column is the starting set, not a limit. A module may use any pin row. SOX has no
pin row: a module that states a date or threshold for it fetches it first. DORA and EMIR are
P54 and P55.

### Decision models

Each row holds the section's only copy of that decision. Other pages link to it.

| Page | Decision | Form |
|---|---|---|
| `money_representation_and_currency` | Money type and storage; rounding mode per context (FX, tax, interest, allocation) | table plus worked examples |
| `double_entry_ledger_design` | Balance model: derived vs materialized in the same transaction vs snapshot plus delta | table |
| `double_entry_ledger_design/hot_accounts_and_ledger_scaling.md` | Hot-account strategy and ledger store | flowchart plus numbers table |
| `idempotency_and_exactly_once_money_movement` | Unknown outcome: retry with the same key, query, wait for evidence, reverse, never retry | flowchart plus per-rail table |
| `card_payments_lifecycle` | Sale vs auth and capture vs incremental; decline retry policy | table |
| `card_payments_lifecycle/interchange_and_payment_economics.md` | Blended vs IC++; least-cost vs approval-rate routing | table plus cost example |
| `bank_rails_ach_sepa_and_wires` | Rail selection (the section's only rail matrix) | matrix |
| `strong_customer_authentication_and_3ds` | Exemption vs challenge vs out of scope | flowchart |
| `pci_dss_scope_and_tokenization` | Integration shape vs PCI scope | quadrant plus SAQ table |
| `fraud_and_risk_scoring` | Rules vs model vs vendor vs network tools; threshold economics | table plus expected-cost formula |
| `kyc_aml_and_sanctions_screening` | Screening points and threshold by risk tier; block vs hold vs alert | table |
| `disputes_chargebacks_and_refunds` | Fight vs accept | table |
| `reconciliation_and_settlement_files` | Matching strategy; auto vs manual; batch vs streaming | table |
| `wallets_stored_value_and_payouts` | Instant vs standard payout; prefund vs post-fund | table |
| `subscriptions_billing_and_invoicing` | Proration policy; rating timing | table |
| `treasury_liquidity_and_fx_operations` | Prefund vs credit line; quote-and-lock vs hedge | table |
| `order_books_and_matching_engines` | Matching algorithm per product; book structure | table |
| `exchange_architecture_and_determinism` | Hot standby with replay vs active-active vs Raft log | table plus flowchart |
| `market_data_feeds_and_multicast` | Multicast vs TCP vs WebSocket; MBO vs MBP; conflation | table |
| `fix_protocol_and_order_entry` | tag=value vs binary order entry | table |
| `low_latency_trading_systems` | Tuned kernel vs kernel bypass vs FPGA; language | table with ns budgets |
| `ccp_clearing_and_settlement` | Self-clear vs clearing broker | table |
| `margin_liquidation_and_derivatives_risk` | Isolated vs cross vs portfolio margin; the loss waterfall | table plus waterfall diagram |
| `crypto_custody_and_onchain_operations` | Hot, warm and cold split; MPC vs multisig vs HSM | table |
| `regulatory_reporting_and_audit_trails` | Audit-trail store | table |
| `build_vs_buy_and_licensing_strategy` | PSP vs PayFac vs direct; multi-PSP; vendor vs build; partner bank vs own license | scorecard plus flowchart |

---

## Sub-files (2, 14-section template, 15+ Q&As each)

Each sub-file exists because it needs a different study tier from its parent page. Both are
principal only.

| File | Parent | Tiers | Why separate |
|---|---|---|---|
| `double_entry_ledger_design/hot_accounts_and_ledger_scaling.md` | 2 | principal | Hot-account contention (fee account, FBO account, promo sender), batching and netting, sharded sub-accounts, async posting behind a reservation, balance reads at scale, ledger sharding keys, Spanner-class vs Postgres vs TigerBeetle, ledger migration. Holds the hot-account decision model |
| `card_payments_lifecycle/interchange_and_payment_economics.md` | 5 | principal | MDR as interchange plus scheme fees plus acquirer markup, blended vs IC++, MCC and interchange categories, EU IFR caps, US Reg II and dual-network debit routing, least-cost routing, network tokens for cost and approval rate, surcharging. Holds the routing and pricing decision model. Facts: F4, F5 |

No file in this section may be named `*_deep_dive.md` or `*_internals.md`. `DEEP_NAME_RE` in
`game/build_tech.py` makes such a name FATAL under `--strict` unless it is listed in
`PRODUCT_DEEP_DIVES` or `CONCEPT_DEEP_DIVES`. This section adds no entry there.

---

## Case studies (6 plus 2 cross-cutting)

11-section principal template: `## Intuition`, then `## 1. Requirements Clarification`
through `## 11. Interview Discussion Points`. Quality bar for every study:

- 900 to 1100 lines with no filler. If the content runs short, cut to the lower bound and
  deepen §4. Never pad.
- 4 or more references to `case_studies/cross_cutting/` files.
- Executable Java 25 or SQL in §4, with a `// BROKEN` then `// FIX` pair.
- Named companies in §6, with fetched figures only.
- Quantified impact in §9. A figure without a source is labelled illustrative.
- 10 or more Q&As in §11, each with a `**Short:**` line.

The scale figures in each study are design assumptions, labelled as such. Each `## Intuition`
names the existing study it must not duplicate and links it.

| File | Tiers | Concern and assumed scale | Must not duplicate |
|---|---|---|---|
| `case_studies/design_a_payment_reconciliation_platform.md` | senior, principal | 40M ledger transfers a day across 6 PSPs and acquirers, 14 bank accounts in 5 currencies. Three-way match, fee netting, break taxonomy and aging, suspense, provisional T+0 vs final T+1, a 99.95% auto-match target, an ops case UI | hld payment system §4.5; payment processor §4.6 and runbook 1; banking ledger §8 |
| `case_studies/design_a_payment_orchestration_layer.md` | senior, principal | A merchant with $20B a year, 4 PSPs and acquirers, 30 countries. PCI vault and network tokens, routing on BIN, cost, approval rate and health, soft-decline cascades with idempotency across PSPs, 3DS orchestration, failover, per-PSP recon hand-off | hld payment system, react checkout, payment processor |
| `case_studies/design_a_subscription_billing_engine.md` | senior | 3M subscriptions, 2B usage events a day. Idempotent metering, rating, anchor-date invoicing, proration, credits, the tax call, dunning, revenue-schedule export, immutable invoices and credit notes | none existing |
| `case_studies/design_a_card_issuing_platform.md` | senior | 5M cards through a BIN sponsor. The issuer-side authorization decision inside the network time budget (fetched, or stated as a variable), JIT funding, spend controls, holds and incremental auths, clearing-to-auth matching, issuer-side disputes, stand-in | hld digital wallet (lists card issuing as out of scope) |
| `case_studies/design_a_broker_order_management_system.md` | senior | Retail broker, 5M accounts, 20k orders/s at the open. OMS state machine, buying-power checks, smart order routing to venues and wholesalers over FIX, fractional shares, drop-copy recon, T+1 through a clearing firm, best-execution reports | hld stock exchange (venue side), lld OrderMatchingEngine |
| `case_studies/design_a_perpetual_futures_risk_engine.md` | senior, principal | Crypto derivatives venue, 500 markets, 2M open positions. Mark price, funding each interval, the liquidation engine, the insurance fund, ADL ranking, deterministic replay beside the sequencer | hld stock exchange (matching), `order_books_and_matching_engines` |
| `case_studies/cross_cutting/money_movement_invariants.md` | not listed | Conservation per currency, no negative available balance without an overdraft product, idempotent effect, append-only with reversals, one internal transfer per external movement, an end-to-end trace id. Each invariant maps to a DB constraint, a runtime assertion and a property test. Every study's §8 uses its checklist | - |
| `case_studies/cross_cutting/business_days_cutoffs_and_value_dates.md` | not listed | Rail and market calendars, cut-off times, value vs booking vs settlement date, time zones and DST, month end, T+n arithmetic across holidays, and the effect on recon, payouts and billing anchors | - |

The cross-border payouts and AML screening studies from the first plan are dropped. The
`wallets_stored_value_and_payouts`, `treasury_liquidity_and_fx_operations` and
`kyc_aml_and_sanctions_screening` modules and the hld digital wallet study cover that ground. A
crypto custody study and an exchange study are not built: module 26 §14 carries the custody
case, and `hld/` owns the exchange.

The cross-cutting files use the 14-section template.

`case_studies/case_studies.md` has the 5 standard sections:

1. Quick Start: reconciliation, orchestration, perpetual risk engine.
2. Full Learning Path in 3 groups: "Moving money" (orchestration, card issuing), "Recording
   money" (reconciliation, billing) and "Markets" (broker OMS, perpetual risk). Each group ends
   with a `### Elsewhere in the repo` line that links the existing studies with full relative
   paths (`../../hld/...`). `CS_LINK_RE` in `extract.py` keeps only slugs in this section's own
   `case_studies/`, so a `../../` link never enters the fintech Case Studies tab.
3. Cross-Cutting / Shared Primitives table.
4. ASCII dependency map. Out-of-section prerequisites are leaves tagged `[hld]`, `[database]`,
   `[backend]`.
5. Interview Prep Shortcuts.

Case-study Q&As never enter the main bank. `extract.py` puts them in the reader-only
`game/case_questions/fintech.json` pool. Do not move one under a module's §12 to "rescue" it.

`case_studies/cross_cutting/` is in `CS_EXCLUDE_DIRS`. Listing a file under it in the
`<!-- study-paths -->` block fails `--strict`. Neither cross-cutting file appears in the block.

---

## Learning paths

Senior is the craft: build it, debug it, say why it broke. Principal is the judgment: which
approach at what cost, what failure modes, what not to build. They are different cuts, not
nested depths. Do not promote a module to principal because it is advanced.

- Senior: 21 modules. Every module except KYC, tax, treasury, CCP, crypto, regulatory reporting
  and build vs buy.
- Principal: 13 modules. Six are principal-only (`kyc_aml_and_sanctions_screening`,
  `treasury_liquidity_and_fx_operations`, `ccp_clearing_and_settlement`,
  `crypto_custody_and_onchain_operations`, `regulatory_reporting_and_audit_trails`,
  `build_vs_buy_and_licensing_strategy`), plus the two principal-only sub-files.
- Full only: `tax_for_payment_engineers`.

Membership is declared once, in the `<!-- study-paths -->` block in `README.md`. The
integrator writes that block from PLAN §5, without the two dropped case studies. Order comes
from `STUDY_ORDER.fintech` in `game/app.js`. There is no path array in `app.js` to edit.

---

## Q&A rules

- Every Q&A is `**Q: ...?**` followed directly by an authored `**Short:**` line of 15 to 220
  characters as the first non-blank answer line. This section ships Short-migrated from the
  start.
- A `**Short:**` line is self-contained, has no code fence and no list, and does not restate
  the question.
- Floors as in the module table: 15, or 18 where marked. 15 per sub-file.
- Order by interview frequency: traps first, then mechanics, then edge cases.
- Keep first sentences lexically distinct. Answers cluster on "settlement", "ledger",
  "payment", "order" and "margin", and near-identical openers make MCQ options that a learner
  cannot tell apart.
- Never number Q&As as a Markdown list.
- Every fact in a Q&A follows the pin rules. A `**Short:**` line states no fact from "Do not
  state".

---

## Authoring rules

- Module pages and sub-files: the 14-section template. No line-count target. Do not pad.
- Under the H1, a dated anchor blockquote that uses only pin rows:
  `> Rules and figures checked 2026-11-06. Every date, threshold and fee on this page comes from the fact pins in fintech/CLAUDE.md.`
- Compliance modules (SCA, PCI, fraud, KYC and AML, disputes, wallets, tax, CCP, crypto,
  regulatory reporting, build vs buy) add one line to the anchor or to §1: the page is an
  engineering view as of the pin date, not legal advice, and covers the US, the EU, the UK,
  India and Brazil only.
- Design assumptions (capacity inputs, targets, SLOs, scale figures) are labelled "assumption"
  or "illustrative", apart from factual claims. A factual claim traces to a pin row or a
  fetched primary source.
- §6 heading is `## 6. How It Works - Detailed Mechanics` with a plain hyphen, as in `mlops/` and
  `rust/`.
- §11 stays `## 11. Technologies & Tools`. This section feeds `tech_index.json`. Run the §11
  lint in `technologies/CLAUDE.md` ("The §11 lint") before you finish a §11.
- §11 column 0 is a distinctive product name. A vendor whose name is one dictionary word goes in
  the description column, never in column 0. Names at risk: Persona, Alloy, Sift, Unit, Orb,
  Column, Mercury (already in the index), Chronicle (write `Chronicle Queue`), Galileo, Wise,
  Square. Write `SWIFT gpi` or `SWIFTNet`, never bare `SWIFT`, which collides with the Swift
  language.
- Boundary sentence at the end of §1: only where the boundary table names a real overlap.
- Link at least 2 sibling `fintech/` pages, written `../<slug>/<slug>.md`. Link other
  sections' module PAGES, never a bare directory. Every relative link resolves on disk.
- No em dash (U+2014), no en dash (U+2013), no emoji.
- No `README.md` inside a module directory.

### Code rules

- Executable languages: Java 25 (` ```java `) and PostgreSQL 18 (` ```sql `). Nothing else runs.
- Data fences are not checked: ` ```text ` for FIX (with `|` for SOH) and ITCH byte layouts,
  ` ```xml ` for ISO 20022, ` ```json ` for APIs.
- Java: records for value types. `long` minor units, or `BigDecimal` with an explicit scale and
  `RoundingMode`. No `double` for money except inside a `// BROKEN` fence. Single-file programs
  that run with `java -ea`.
- Each ` ```java ` fence compiles alone, so it cannot use a type from another fence. A run
  times out after 60 seconds.
- SQL: the ` ```sql ` fences of one file run in file order in one database, so a later fence
  can use a table that an earlier fence created. Ledger invariants go in constraints.
- `// BROKEN` then `// FIX` pairs in §10 and §14 of every module.
- Mark a partial snippet `// fragment` (or `-- fragment`) only because it is partial, never to
  hide a failure.

### The fincheck contract

Run `python3 /Users/rutik/rutik/code/system-design/.work/fintech/fincheck.py <file.md>`. It
must exit 0.

| Marker in the fence | Java (`eclipse-temurin:25-jdk`) | SQL (`postgres:18`, `ON_ERROR_STOP=1`) |
|---|---|---|
| none | Must compile. If it has `main`, must run with `java -ea` and exit 0 | Must run without error |
| `// BROKEN` or `-- BROKEN` | Must compile. The run result is reported, not checked. Show the wrong output in a comment or in the prose | Runs inside `BEGIN ... ROLLBACK`. The result is reported, not checked |
| `// BROKEN: compile-error` or `-- BROKEN: error` | Must fail to compile | Runs inside `BEGIN ... ROLLBACK` and must error |
| `// fragment` or `-- fragment` | Skipped | Skipped |

Most money bugs compile and give a wrong answer, so plain `// BROKEN` is the common case. Use
`// BROKEN: compile-error` only when the lesson is a compile error.

A Java fence is saved as the file named for its `public` top-level type, else `Main.java`.

---

## Question ids: what re-keys them

`qid()` in `game/extract.py` hashes `module|question`, giving `fintech/<module>#<md5[:12]>`.
`sourceFile` is not hashed.

- Renaming a sub-file does not re-key its questions. They keep their module.
- Editing the text of a question re-keys that question.
- Moving, merging or renaming a module directory re-keys every question in it.
  `startReview()` in `game/app.js` then deletes the orphaned review ids, and learners lose
  their spaced-repetition state for that module. The section slug `fintech` and the 28 module
  slugs are frozen before the first deploy.

---

## Mermaid traps

A Mermaid fence that does not parse fails the build (`scripts/build_diagrams.mjs` exits 1).
Validate every file you touch: `node scripts/check_render.mjs <absolute-file.md>` from the
repo root. Run `/mermaid-diagrams` before you author a diagram. Colour every node with the
One-Dark `classDef` block from that skill.

| Trap | Where fintech content hits it | Fix |
|---|---|---|
| `call` as a node id or in a `class` list | margin call, option call | `mCall` |
| `end` as a node id | end of day | `eod` |
| `default` as a node id | member default, default waterfall | `dflt` |
| `class`, `style` or `click` as a node id | asset class | `assetCls` |
| `%%` in a label | it starts a comment | never write `%%` |
| `$$` in a label | Mermaid 11 math | avoid it |
| `;` in a `sequenceDiagram` Note or message | ISO 8583 field lists, SQL statements | use `,` or `.` |
| `<` or `>` in a label | "amount > limit" | words, or `#lt;` and `#gt;` |
| `&` in a label | P&L, "DvP & netting" | "and", or `#amp;` |
| `\|` in a label | raw FIX strings | keep FIX in a ` ```text ` fence |
| `:` in a quadrant label or a `stateDiagram-v2` description | "17:00 cutoff" | write `1700 ET`, or quote the quadrant label |
| `()` in a quadrant label | "PayFac (sub-merchants)" | quote the label |
| a quadrant point without brackets | `"P": 0.2, 0.8` | `"P": [0.2, 0.8]` |
| a quadrant coordinate of exactly `1.0` | quadrant charts | `0.98` |
| `#` in a label | "Order #42" | `Order 42` |
| `\"` in a label | quotes in labels | `#quot;` |
| spaces after an edge label's closing `\|` | an aligned column of edges | one space only |
| `{` or `}` in an unquoted label | JSON payloads | quote the label, or keep JSON in a ` ```json ` fence |

Mermaid fences are not allowed in any `CLAUDE.md` file, this one included.

---

## Per-file checks

Run these from the repo root on every file you touch. All must pass before you report the file
as done.

```bash
F="$PWD/src/main/java/com/rutik/systemdesign/fintech/<module>/<file>.md"
python3 .work/fintech/qacheck.py "$F" <floor>     # prints OK
python3 .work/fintech/fincheck.py "$F"            # exit 0
node scripts/check_render.mjs "$F"                # exit 0
python3 .claude/skills/visual-intuition-diagrams/diagram_tools.py check "$F"
grep -nP '[\x{2013}\x{2014}]' "$F"                # prints nothing
vale --config ~/.claude/vale/.vale.ini --output=line "$F" | grep ':House\.'   # prints nothing
```

`qacheck.py` checks the heading template (14 sections for a module or a cross-cutting file, 11
plus `## Intuition` for a case study), the Q&A floor (10 for a case study), the `**Q:` and
`**Short:**` shape, numbered Q&A lists, dashes and emoji.

Also:

- Read the §11 table against the §11 lint in `technologies/CLAUDE.md`. The in-process coverage
  run happens at integration.
- Confirm that every relative `.md` link resolves on disk.
- Confirm that every number traces to a pin row, a fetched source, or an "assumption" label.

---

## Adding a new fintech module

1. Create `<module>/<module>.md` with the 14-section template. The page is named for its folder.
2. Write the anchor blockquote from the pin rows. Fetch anything else from its primary source.
3. Meet the Q&A floor, each answer opening with its `**Short:**` line.
4. Run the per-file checks above.
5. Add `"fintech/<module>"` to `STUDY_ORDER.fintech` in `game/app.js` at its phase position.
6. Add the module and every file it owns to the `<!-- study-paths -->` block in `README.md`,
   with tiers (`-` for Full path only). Then run `python3 game/extract.py --write-paths`.
7. Run `python3 game/extract.py --strict`. It must exit 0 and show a fintech count.
8. Flip the file's status in the `README.md` Build Manifest.
9. Update the module table in `README.md`, the module list in this file, and the root
   `README.md` and root `CLAUDE.md` rows for this section.

Steps 5 to 9 belong to the integrator during the build.

To add a case study: write it, add its block line, and update `case_studies/case_studies.md`
(all 5 sections), the `README.md` case-study table and the case-study list in this file, in
the same change.

---

## HARD RULE: structure lives in `README.md`, content files hold only content

The section `README.md` is the single source of truth for this section's file inventory and
study-tier membership. Its `<!-- study-paths -->` block lists every module, every file that
module owns (the module page and every sub-file), and every case study, each tagged with its
tiers. `-` means Full path only.

A content file carries no structural metadata: no `<!-- study-paths -->` block, no tier
declaration, no path membership.

A file on disk that is missing from the block, or listed there and absent from disk, fails
`python3 game/extract.py --strict` and takes the Pages deploy red. The parser splits each
file line on its last double space, so put at least two spaces between a filename and its
tiers.

Order is never declared in the block. It comes from `STUDY_ORDER` in `game/app.js`. The tier
tables in the README are generated from the block by `python3 game/extract.py --write-paths`.
Never hand-edit them.
