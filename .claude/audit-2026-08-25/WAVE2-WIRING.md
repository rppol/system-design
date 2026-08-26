# Wave 2, phase B — the wiring spec, accumulated from phase A reports

**ONE SERIALIZED AGENT DOES ALL OF THIS.** Each section below is a phase-A agent's own report of
what it created and exactly how to wire it. Nothing here has been wired yet.

## Order of operations (non-negotiable)
1. **TIER PROMOTIONS FIRST**, before or with the sub-files that depend on them. A sub-file cannot
   join a tier its parent module page skips — get this wrong and the file ships invisible to that
   tier with a GREEN BUILD.
2. `STUDY_ORDER` entries in `game/app.js`. **Sub-files get NO entry** (fatal under `--strict`).
   **Indices below are cumulative — insert top-down or they shift.**
3. `<!-- study-paths -->` lines in each section README.
4. README module/case-study tables, section CLAUDE.md, root README, root CLAUDE.md counts.
5. `case_studies/case_studies.md` — all five parts, same commit.
6. `python3 game/extract.py --write-paths`, then `--strict`. Never hand-edit a tier table.
7. `bash scripts/build_banks.sh` — **must exit 0. `--strict` passing does NOT mean the script
   passes; it has its own verifier after it.**

**PATH TRAP:** `extract.py` is at `src/main/java/com/rutik/systemdesign/game/extract.py`. Run it
from the repo root and it fails with "No such file or directory" while the shell reports EXIT=0.

---

# backend — 5 files, COMPLETE

| Path | Type | Lines | Q&As | Shorts | Mermaid | check_render |
|---|---|---|---|---|---|---|
| `serving_ai_workloads/serving_ai_workloads.md` | module | 652 | 19 | 19 | 6 | exit 0 |
| `webhooks_and_event_delivery/webhooks_and_event_delivery.md` | module | 641 | 20 | 20 | 6 | exit 0 |
| `api_contracts_and_schema_governance/api_contracts_and_schema_governance.md` | module | 878 | 18 | 18 | 5 | exit 0 |
| `case_studies/design_llm_serving_api/design_llm_serving_api.md` | case study | 1080 | 12 | 12 | 3 | exit 0 |
| `case_studies/design_webhook_delivery_service/design_webhook_delivery_service.md` | case study | 991 | 12 | 12 | 4 | exit 0 |

**No sub-files -> no tier-promotion conflict in this section.**

## `STUDY_ORDER["backend"]` — insert IN THIS ORDER (indices cumulative)
| # | Entry | Index | After |
|---|---|---|---|
| 1 | `"backend/api_contracts_and_schema_governance"` | **5** | `"backend/rest_api_design"` (was 4) |
| 2 | `"backend/webhooks_and_event_delivery"` | **9** | `"backend/websockets_and_sse"` (now 8) |
| 3 | `"backend/serving_ai_workloads"` | **14** | `"backend/async_and_concurrency_patterns"` (now 13) |

Case studies get NO `STUDY_ORDER` entry.

## study-paths lines
```
api_contracts_and_schema_governance
  api_contracts_and_schema_governance.md  senior, principal
webhooks_and_event_delivery
  webhooks_and_event_delivery.md  senior, principal
serving_ai_workloads
  serving_ai_workloads.md  senior, principal
```
Under the existing `case_studies` entry:
```
  design_llm_serving_api/design_llm_serving_api.md                      senior
  design_webhook_delivery_service/design_webhook_delivery_service.md    senior
```

## README module-table rows
```
| [Serving AI Workloads](serving_ai_workloads/serving_ai_workloads.md) | Streaming transport, admission control, cancellation, per-tenant token budgets, GPU queueing | 19 | Advanced |
| [Webhooks & Event Delivery](webhooks_and_event_delivery/webhooks_and_event_delivery.md) | Outbox dispatch, HMAC + RFC 9421 signing, retry ladders, per-endpoint isolation, SSRF defence | 20 | Advanced |
| [API Contracts & Schema Governance](api_contracts_and_schema_governance/api_contracts_and_schema_governance.md) | OpenAPI 3.2, AsyncAPI 3.1, Spectral/oasdiff/buf gates, compatibility modes, Deprecation/Sunset | 18 | Advanced |
```
README case-study table:
```
| [Design an LLM Serving API](case_studies/design_llm_serving_api/design_llm_serving_api.md) | Multi-tenant streaming inference gateway | Admission control, SSE streaming, cancellation propagation, token budgets |
| [Design a Webhook Delivery Service](case_studies/design_webhook_delivery_service/design_webhook_delivery_service.md) | Outbound delivery at fleet scale | Per-endpoint isolation, retry ladder, signing + rotation, safe redrive |
```

## `backend/CLAUDE.md`
Phase 2 table:
```
| `api_contracts_and_schema_governance/` | OpenAPI 3.2, AsyncAPI 3.1, Spectral, oasdiff, buf breaking, compatibility modes | 18 |
| `webhooks_and_event_delivery/` | Signing, retry ladders, per-endpoint isolation, DLQ/redrive, SSRF | 20 |
```
Phase 3 table:
```
| `serving_ai_workloads/` | Streaming transport, admission control, cancellation, token budgets, GPU queueing | 19 |
```
Case-study table — **bump the heading to "Case Studies — 7 Total"**:
```
| `design_llm_serving_api/` | Admission control, SSE streaming, cancellation, token budgets |
| `design_webhook_delivery_service/` | Per-endpoint isolation, retry ladder, signing rotation, redrive |
```

## `case_studies/case_studies.md` — all five parts
**1. Quick Start** — no change (editorial call left to the owner).
**2. Full Learning Path** — TWO NEW `###` groups (neither fits the five existing concerns):
```
### AI Workload Serving

| Case Study | Concern | Summary |
|---|---|---|
| [Design an LLM Serving API](design_llm_serving_api/design_llm_serving_api.md) | Admission control and streaming under GPU scarcity | The HTTP boundary in front of a GPU fleet: a bounded admission queue that sheds with 429 rather than queueing unboundedly, SSE streaming with heartbeats and resume, cancellation propagated to the model backend, and per-tenant token budgets reserved on admission. |

### Outbound Integration

| Case Study | Concern | Summary |
|---|---|---|
| [Design a Webhook Delivery Service](design_webhook_delivery_service/design_webhook_delivery_service.md) | At-least-once delivery to endpoints you do not control | Transactional outbox to per-endpoint queues, HMAC signing with a dual-secret rotation window, an exponential retry ladder with circuit breaking and auto-disable, a customer-visible delivery log, and an SSRF-safe egress path. |
```
**3. Cross-Cutting:**
```
| [Rate Limiting In Depth](../rate_limiting_in_depth/rate_limiting_in_depth.md) | Phase: AI Workload Serving — per-tenant budgets are rate limiting with tokens as the unit |
| [Fault Tolerance Patterns](../fault_tolerance_patterns/fault_tolerance_patterns.md) | Phase: Outbound Integration — per-endpoint circuit breaking is the delivery engine's core primitive |
```
**4. Dependency Map** — both are standalone roots; add as unconnected mermaid nodes and amend the
caption (currently "`design_feed_service` and `design_microservices_migration` share no patterns"):
```
    llmapi(["design_llm_serving_api"])
    webhook(["design_webhook_delivery_service"])
```
**5. Interview Prep Shortcuts:**
```
| Design an LLM inference / completions API | [design_llm_serving_api](design_llm_serving_api/design_llm_serving_api.md) |
| Design an inference batching system for a single GPU | [design_llm_serving_api](design_llm_serving_api/design_llm_serving_api.md) |
| Design a webhook / event delivery system | [design_webhook_delivery_service](design_webhook_delivery_service/design_webhook_delivery_service.md) |
| Design a notification delivery service | [design_webhook_delivery_service](design_webhook_delivery_service/design_webhook_delivery_service.md) |
```

## Two corrections it made mid-write (both the "one word apart" shape)
- **RabbitMQ delayed delivery**: the Raft-backed Delayed Queue is **VMware Tanzu RabbitMQ 4.3
  only, NOT open-source RabbitMQ**. The community `rabbitmq-delayed-message-exchange` plugin is
  deprecated and its repo is archived (confirmed `archived: true` via the GitHub API). File now
  says so and gives open-source readers the `due_at`-column / TTL+DLX fallback.
- **`InferenceModel`**: at v1.0 GA **`InferenceObjective` REPLACED it**, and the API group moved
  `inference.networking.x-k8s.io` -> `inference.networking.k8s.io`. It carries a numeric
  priority, not a traffic-split policy. The first draft had annotated the rename — which the
  REPLACE-DO-NOT-ANNOTATE rule forbids — and was rewritten to lead with `InferenceObjective`.

Re-verified and left alone: RFC 9745 `Deprecation` (Proposed Standard, structured-field Date
`@1688169599`), RFC 8594 `Sunset`, Optic archived 2026-01-12, AsyncAPI 3.1.0 (2026-01-31),
`InferencePool` stable v1 GA.

## Notes for phase B
- Nothing on backend's list had been pre-empted by wave 1 — all five paths were genuinely absent.
- The two modules landed at 641/652 lines against 750/800 estimates. They clear the section floor
  (smallest existing module is 540) and both beat the 18-Q&A bar, so they were not padded.
- `design_llm_serving_api` has only 3 mermaid fences over 1080 lines — thinnest of the five.
- The audit's wider backend reordering (`rabbitmq_deep_dive` demotion, `messaging_patterns` move,
  8 tier changes) is a LATER wave and is NOT reflected in the indices above.

---

# ml — 3 files, COMPLETE (2 were EXTRACTIONS, not fresh writes)

| Path | Type | Lines | Q&As | Mermaid | check_render |
|---|---|---|---|---|---|
| `time_series_forecasting/time_series_foundation_models.md` | sub-file | 782 | 18 | 3 | exit 0 |
| `recommender_systems/generative_recommenders.md` | sub-file | 833 | 18 | 5 | exit 0 |
| `case_studies/design_llm_hybrid_support_triage.md` | case study | 902 | 15 | 3 | exit 0 |

Both sub-files confirmed reaching the bank at 18 questions each. `STUDY_ORDER`: **no entries** —
two sub-files (parents already at idx 23 and 19) and one case study.

## *** LOUD TIER FLAG — the dangerous one in this section ***
**`time_series_forecasting.md` is on NEITHER tier (`-`).** Its new sub-file therefore cannot join
one either. The ml audit §3 explicitly recommends promoting the parent to `senior` — *"demand
forecasting is one of the two or three most-asked ML design prompts and the module backing it is
on no curated path"*. **If phase B makes that promotion, BOTH lines must read `senior` in the same
commit**, or the section's newest and most interview-relevant content ships invisible to every
curated reader on a green build.

```
time_series_forecasting
  time_series_forecasting.md        -
  time_series_foundation_models.md  -
```

**`generative_recommenders.md` — a second, softer tier question.** Parent
`deep_learning_recommenders.md` is `senior` only, so the sub-file was set `senior` (safe and
correct as written). On merit it is arguably `senior, principal` — it is a
which-approach-at-what-cost migration argument, not craft. Promoting it requires promoting the
parent to `senior, principal` in the same commit. **Phase B's call; `senior` alone is not wrong.**
```
  generative_recommenders.md      senior
```

Case study:
```
  design_llm_hybrid_support_triage.md      principal
```

## The three extractions — what moved and what stub was left
- **`time_series_forecasting.md` 1544 -> 1368 lines, 24 -> 18 Q&As.** Moved §4.4 body, §6's
  Chronos-2 zero-shot block, §8's TSFM-vs-GBDT table, §9's when/when-not, §10 pitfalls 6-8, and 6
  Q&As. Left a ~10-line orienting paragraph at each site ending in a bold link. §11 tool rows
  stayed (a forecasting tools table should still name `chronos-forecasting`). Parent keeps 18 —
  above the floor.
- **`deep_learning_recommenders.md` 1285 -> 1017 lines, 24 -> 18 Q&As.** Moved §4.7 entire, §5.5's
  diagram, §6's RQ-VAE/beam-search code + 40-line walkthrough, §7's four company examples, §8's
  table, §9's blocks, §10 pitfalls 6-8, §11's `RQ-VAE`/`HSTU` rows, 6 Q&As. Left a substantive
  §4.7 stub that still teaches the atomic-ID-vs-semantic-ID contrast — the one idea the rest of
  the file depends on. Parent keeps 18.
- **`classical_ml_vs_llm.md` 714 -> 642 lines, 33 Q&As UNTOUCHED.** §14 compacted ~110 -> ~40
  lines: kept the problem statement, both why-not-pure-X paragraphs, the `route_ticket` snippet,
  the three-row cost table and the sync-vs-async lesson; moved the tau sweep, the 91.4%
  arithmetic, monitoring, rollout, the sequence diagram, human review and capacity into the case
  study. §1-§6 and §12 untouched as the audit required.

## README / CLAUDE.md
- `time_series_forecasting/` has **no sub-file table today** — the section header itself is new:
  `### time_series_forecasting/ (1 sub-file)`
  `| [time_series_foundation_models.md](time_series_forecasting/time_series_foundation_models.md) | Time-Series Foundation Models | Chronos-2, TimesFM, Moirai-2, zero-shot forecasting, patching, quantile heads, pretraining leakage |`
- `### recommender_systems/ (5 sub-files)` -> **6**:
  `| [generative_recommenders.md](recommender_systems/generative_recommenders.md) | Generative Recommenders | Semantic IDs via RQ-VAE, TIGER, HSTU, retrieval-as-decoding, beam search over the ID tree, scaling laws |`
- `ml/CLAUDE.md`: both `Sub-files` cells updated, both rows added to the Sub-Files table, heading
  **37 -> 39**.
- README case-study row **#25** and the case-study count **24 -> 25** in root `README.md`, root
  `CLAUDE.md`, and `ml/CLAUDE.md`:
  `| 25 | Design an LLM + Classical Hybrid Support-Ticket Triage System | Head/tail paradigm split, LLM label distillation, calibrated confidence router, async correction, cost curves, prompt-drift eval | Principal | [design_llm_hybrid_support_triage](case_studies/design_llm_hybrid_support_triage.md) |`

## `case_studies/case_studies.md` — all five parts
1. **Quick Start** — no change needed.
2. **Full Learning Path**, into `### Group 6: NLP Engineering`:
   `| [LLM + Classical Hybrid Support Triage](design_llm_hybrid_support_triage.md) | Paradigm choice per traffic slice | Head/tail split, LLM label distillation, temperature-scaled confidence router, conformal abstention, async correction, prompt-drift eval harness, category graduation |`
3. **Cross-Cutting** — consumes `model_calibration_and_thresholding.md` (router design),
   `drift_monitoring_and_retraining.md` (operations), `experimentation_and_online_evaluation.md`
   (rollout), `responsible_ai_fairness_and_explainability.md` (requirements).
4. **Dependency Map** — `nlp_classification_pipeline -> llm_hybrid_support_triage`; shares the
   cascade lesson with `harmful_content_detection` (cost pressure vs adversarial pressure).
5. **Interview Prep:** `| Support ticket triage / when to use an LLM vs a classical model | [LLM + Classical Hybrid Support Triage](design_llm_hybrid_support_triage.md) | Head/tail split, distillation, confidence routing, async fallback, cost curves |`
6. **Build Manifest** row: `| \`design_llm_hybrid_support_triage.md\` | done | 11-section principal template, 15 Q&As |`

## Verification notes
- Fixed two broken links its own subagents produced: a `../time_series_forecasting/` self-reference,
  and a `../retrieval_and_ranking/retrieval_and_ranking.md` pointing at a directory that does not
  exist — **`retrieval_and_ranking.md` is a sibling SUB-FILE, not a module.** Worth remembering:
  the module-vs-sub-file distinction breaks relative links silently.
- Claude Haiku 4.5 pricing verified at platform.claude.com ($1/MTok in, $5/MTok out, cache hits
  0.1x), which **confirms the seed's $0.0007/call** — re-derived rather than asserted, so the
  whole cost table survives. Also verified: Zendesk intelligent triage, Salesforce Einstein Case
  Classification (its documented 400-1,000-closed-case floor is what makes the tail argument
  concrete), Intercom Fin, Klarna/OpenAI. Chronos-2 (120M params, 8,192 context, Apache-2.0) and
  TimesFM 2.5 (200M, 16,384 context) verified at their HF model cards; HSTU/TorchRec at GitHub.
- **`package.json` being modified is NOT another agent** — it is the orchestrator's own
  `mermaid: "11.16.0"` pin, added earlier this session because `check_render.mjs` read
  `node_modules/mermaid` while `mermaid` was in neither `package.json` nor the lockfile. Verified
  by diff. No action for phase B.

## !! INDEX DISCREPANCY — phase B must READ INDICES OFF DISK, not trust these reports
The ml agent reported twice and gave `ml/time_series_forecasting` as index **23** in one report
and **24** in the other. Actual value measured from `game/app.js` at the time of writing is
recorded below. **Treat every index in this document as a POINTER TO THE NEIGHBOUR, not as a
number.** The reliable instruction is always "insert after `<named entry>`" — the numeric index
is only valid until the previous insertion shifts it, and phase A agents were reading a file that
other agents were not modifying but that had already been edited for `soft_skills`.

**Phase B rule: locate the named predecessor entry, insert after it, ignore the number.**

**MEASURED FROM `game/app.js` (authoritative):**
```
  idx 18: ml/recommender_systems       (agent reported 19 in BOTH reports — wrong)
  idx 22: ml/time_series_forecasting   (agent reported 23 in one report, 24 in the other — both wrong)
  ml has 47 entries
```
Three different numbers for two entries, and **not one of them was right**. The agents were
reading correctly-ordered arrays and miscounting, which is exactly the failure mode a numeric
index invites and a named predecessor does not. Neither entry is affected by any wave-2 insertion
either, so this is not drift — it is arithmetic.

**This is now a hard rule for phase B: NEVER paste an index from a phase-A report. Find the named
predecessor with grep, insert after it, and let the array renumber itself.**

---

# spring — 2 files written, 5 DECLINED (already folded in by wave 1)

| Path | Type | Lines | Q&As | Shorts | Mermaid | check_render |
|---|---|---|---|---|---|---|
| `spring_boot_4_migration/spring_boot_4_migration.md` | module (P0) | 1045 | 20 | 20 (max 174 ch) | 3 | exit 0 |
| `case_studies/design_mcp_tool_platform.md` | case study (P1) | 1211 | 10 | n/a (case studies excluded from the bank) | 2 | exit 0 |

Confirmed reaching the bank: `spring/spring_boot_4_migration has 20 questions`.

## `STUDY_ORDER["spring"]`
**Append last, after `"spring/spring_native_graalvm"`** — `"spring/spring_boot_4_migration"`.
(Array was 39 entries; DO NOT trust that number — find the named predecessor. See the index rule
above: ml's agent got its indices wrong three different ways.)

## study-paths
```
spring_boot_4_migration
  spring_boot_4_migration.md  principal
```
Case-study block: `  design_mcp_tool_platform.md  principal`

**TIER PRECONDITION IS GONE.** `spring_ai.md` is indeed tier `-`, but since NEITHER `spring_ai/`
sub-file was written, that promotion no longer blocks anything. It survives as a standalone
audit §3 recommendation (`spring_ai` `-` -> `senior`) on its own merits.

## README / CLAUDE.md
Module row (both files): `| spring_boot_4_migration/ | 9 | Boot 3.5->4.x: modular starters, package relocations to org.springframework.boot.<technology>, Jackson 3, JSpecify null-safety, @MockitoBean, OpenRewrite recipes, spring-boot-properties-migrator, the Spring Cloud compatibility question |`
`spring/CLAUDE.md`: "39 Modules" -> **40**; case-study count 9 -> **10**, plus
`| design_mcp_tool_platform/ | MCP server, scope-filtered discovery, token exchange |`

## `case_studies/case_studies.md` — five parts
1. Quick Start — no change.
2. Full Learning Path — NEW **Group 6: AI and Tool Platforms**:
   `| [MCP Tool Platform](design_mcp_tool_platform.md) | Model-facing tool APIs, OAuth 2.1 resource server | @McpTool/@McpResource/@McpPrompt on ordinary beans, scope-filtered tools/list, RFC 8693 token exchange instead of passthrough, progressive disclosure for large catalogues, handle-based state |`
3. Cross-Cutting — add **MCP Tool Platform** to the "Best Read With" cell of
   `otel_observability_for_spring`, `resilience4j_patterns`, `testcontainers_and_test_strategy`.
4. Dependency Map — under `design_oauth2_authorization_server`:
   `+---> design_mcp_tool_platform  (OAuth 2.1 resource server + RFC 8693 token exchange)`
5. Interview Prep — `| Design an MCP server / internal tool platform for LLM agents | ... |` and
   `| How do you stop a service forwarding a caller's token downstream? | ... |`
6. Build Manifest — `| [design_mcp_tool_platform.md](design_mcp_tool_platform.md) | 11-section principal | 10 | DONE |`

## FIVE FILES DELIBERATELY NOT WRITTEN — wave 1 had already folded the payload in
The brief's "a redundant module is worse than a missing one" rule, applied after reading each
destination span. **Phase B must strike these from the worklist, not chase them.**

| Proposed | Where the payload now lives |
|---|---|
| `spring_security_modern_auth/` (P0, 950 lines) | `spring_security_architecture.md` §6 (L375-507) + §4 + 4 Q&As (L1129-1147) + §14; DPoP/OAuth 2.1 in `spring_security_jwt_oauth.md` §6.7 (L482-560). Covers `webAuthn()`, the in-memory-repository trap, `rpId` drift, `oneTimeTokenLogin()`, `@EnableMultiFactorAuthentication`, `AuthorizationManagerFactories.multiFactor()` — the entire proposed §4/§6/§10 |
| `spring_ai/mcp_servers_and_clients.md` (P0, 800) | `spring_ai.md` §6.8 (L428-500), §7.4, §5, §12 |
| `spring_performance/startup_and_aot_cache.md` (P0, 550) | `spring_performance.md` §6 four-option startup + CDS + Leyden AOT cache + CRaC (L583-664) + 2 Q&As; `spring_native_graalvm.md` §8 gained the AOT-cache column |
| `spring_cloud_patterns/core_resilience_framework7.md` (P1, 500) | `spring_cloud_patterns.md` §4 (L84-148) + Q&As — incl. the `maxAttempts`-vs-`maxRetries` collision trap |
| `spring_ai/agents_and_tool_calling.md` (P1, 600) | `spring_ai.md` §6.5, §7.3, §5 |

## *** THREE CONTRADICTIONS FOUND LIVE IN WAVE-1's OWN TEXT — fix dispatched ***
Wave 1 corrected these files and, in three places, asserted something now shown wrong. The spring
wave-2 agent verified each at a primary source and has been sent back to fix them in place.

1. **Spring Cloud is NOT "pinned to Boot 4.0.x".** `spring.io/projects/spring-cloud` lists the
   2025.1 train against *"Spring Boot Generation 4.0.x, 4.1.x (Starting with 2025.1.2)"*, and the
   2025.1.2 announcement *"introduces compatibility for Spring Boot 4.1.0"*. The
   `spring-cloud-release` wiki table the audit relied on is STALE. The numbers (2025.1.3,
   2026-08-20) are right; the framing ("pinned", "lags one Boot minor") is wrong — **it tells a
   reader they are blocked when they are not.** Live in `spring/CLAUDE.md` and
   `spring_cloud_patterns.md`.
2. **Spock support was RESTORED in Boot 4.1** (Spock 2.4 on Groovy 5). Undertow stays removed.
   `spring_boot_autoconfiguration.md`'s "Undertow and Spock support removed" row is half-wrong.
3. **`spring.jackson.read.*` / `.write.*` were reintroduced in Boot 4.1** as FORMAT-AGNOSTIC
   features across CBOR/JSON/XML, while `spring.jackson.json.read/.write` stay JSON-specific.
   `spring_boot_autoconfiguration.md` documents only the 4.0 half. **The one migration change
   where doing nothing yields a WRONG result rather than none.**

## Verified and used
Boot 4.0.0 GA 2025-11-20; Boot 4.1.0 GA 2026-06-10 (Framework 7.0.8, Java 17-26); Jakarta EE 11 /
Servlet 6.1 / Tomcat 11.0 / Hibernate 7.1 / JPA 3.2; the modular starters; `@MockBean`/`@SpyBean`
**removed**; `@SpringBootTest` no longer provides MockMvc/TestRestTemplate; Undertow removed;
Session Hazelcast/Mongo removed; `HttpHeaders` no longer a `MultiValueMap`; RestTemplate
doc-deprecated 7.0 -> formally `@Deprecated` **7.1** -> removal 8.0 (7.1 is M1, not GA); all
OpenRewrite recipe ids; MCP spec **2026-07-28** current vs MCP Java SDK 2.0.0 targeting
**2025-11-25**; Spring AI 2.0 has **seven** core providers — **the audit said eight and was wrong;
`spring_ai.md` already had seven, correctly.**

Left unasserted per the supplement: `InetAddressFilter` on `RestTemplate`; Batch `retry` as a
package relocation (it is a dependency swap); "trusted documents" for Spring for GraphQL.

## Notes for phase B
- `design_mcp_tool_platform.md` is ~110 lines over the 900-1100 bar. It trimmed twice and stopped
  rather than cut verified spec material. **Approved — leave it.** The bar prevents padding; it
  does not force deleting substance.
- One Mermaid fix applied while writing: `call` as a flowchart node id is RESERVED and failed the
  parse; renamed to `invoke`. This is the documented trap, hit for real.

---

## MEASUREMENT CAVEAT — `grep -c '^\*\*Q'` LIES. Use `^\*\*Q:`
It also matches bold prose beginning with "Qu…". Hit twice now, both times manufacturing a
phantom missing-`**Short:**`:
- `soft_skills` — `**Quantified outage cost…**`, `**Questions the calibration committee…**`
- `hld/capacity_estimation` — `**QPS** (requests/sec, read and write separately)…` in §2, which
  made 25 Q&As with 25 Shorts read as "26 Q, 25 Short".

**Correct counters:**
```
grep -c '^\*\*Q:' <file>          # questions
grep -c '^\*\*Short:\*\*' <file>  # authored summaries
```
And a **case study showing 0 Shorts is CORRECT, not a defect** — `case_studies/` paths are
excluded from the Q&A bank by design, so their Q&As never become MCQ options.

---

# hld + lld — 17 files, one agent, still reporting

Measured off disk (the agent has not filed its final report yet; these are verified counts,
using `^\*\*Q:`, not the agent's claims):

## hld
| File | Lines | Q&As |
|---|---|---|
| `capacity_estimation/capacity_estimation.md` | 875 | 25 |
| `geospatial_indexing/geospatial_indexing.md` | 648 | 18 |
| `multi_region_and_data_residency/multi_region_and_data_residency.md` | 848 | 18 |
| `stream_processing/stream_processing.md` | 779 | 18 |
| `coordination_and_contention/coordination_and_contention.md` | 1007 | 18 |
| `cell_based_architecture/cell_based_architecture.md` | 827 | 18 |
| `realtime_updates/realtime_updates.md` | 616 | 18 |
| `case_studies/design_youtube_upload_pipeline.md` | 832 | 17 | (0 Shorts — correct) |
| `case_studies/design_distributed_job_scheduler.md` | 919 | 17 | (0 Shorts — correct) |
| `case_studies/design_flash_sale_ticketing.md` | 890 | 24 | (0 Shorts — correct) |

## lld
| File | Lines | Q&As |
|---|---|---|
| `dependency_injection_and_testability/dependency_injection_and_testability.md` | 651 | 18 |
| `refactoring/refactoring.md` | 1044 | 18 |
| `oop_and_uml_foundations/oop_and_uml_foundations.md` | 644 | 18 |
| `system_design_problems/NotificationService.md` | 849 | 15 |
| `system_design_problems/TaskScheduler.md` | 783 | 16 |
| `system_design_problems/LoggingFramework.md` | 870 | 15 |
| `concurrency_patterns/modern_java_concurrency.md` | 449 | 18 |

Every module page has a matching `## 12. Interview Questions with Answers` heading and clears the
15-Q&A floor — checked directly, because this is the pair of sections that lost 217 + 45 Q&As to
exactly those two defects. **Await the agent's report for STUDY_ORDER positions and tiers.**

Note the `lld/system_design_problems/` and `lld/concurrency_patterns/` files are SUB-FILES under
existing modules — **no `STUDY_ORDER` entry** (a 3-segment key is fatal under `--strict`).

---

# devops — 5 sub-files, COMPLETE. 3,254 lines, 81 Q&As, 12 fences, all render exit 0.

## *** THE TIER TABLE — read off disk, four of five parents are Full-only ***
| Sub-file | Parent | Parent tier ON DISK | Needs | Action |
|---|---|---|---|---|
| `device_scheduling_and_dra.md` | `kubernetes_scheduling_and_autoscaling` | `senior` | `senior` | none — safe |
| `gpu_scheduling_and_inference_ops.md` | `ml_platform_and_gpu_infrastructure` | **`-`** | `senior, principal` | **PROMOTE PARENT** |
| `regulated_supply_chain.md` | `devsecops_and_supply_chain_security` | **`-`** | `senior` | **PROMOTE PARENT** |
| `ai_workloads_and_agent_operations.md` | `platform_engineering_and_idp` | **`-`** | `senior, principal` | **PROMOTE PARENT** |
| `gpu_and_ai_finops.md` | `cloud_cost_optimization_finops` | **`-`** | `senior, principal` | **PROMOTE PARENT** |

All four promotions are what audit §3b independently recommends — same decision, now load-bearing.
**ALL FIVE ARE SUB-FILES: no `STUDY_ORDER` entry for any.**

```
kubernetes_scheduling_and_autoscaling
  kubernetes_scheduling_and_autoscaling.md  senior
  device_scheduling_and_dra.md              senior
cloud_cost_optimization_finops
  cloud_cost_optimization_finops.md         senior, principal
  gpu_and_ai_finops.md                      senior, principal
devsecops_and_supply_chain_security
  devsecops_and_supply_chain_security.md    senior
  regulated_supply_chain.md                 senior
platform_engineering_and_idp
  platform_engineering_and_idp.md           senior, principal
  ai_workloads_and_agent_operations.md      senior, principal
ml_platform_and_gpu_infrastructure
  ml_platform_and_gpu_infrastructure.md     senior, principal
  gpu_scheduling_and_inference_ops.md       senior, principal
```
Files: `device_scheduling_and_dra` 722/18Q · `gpu_scheduling_and_inference_ops` 721/18Q ·
`ai_workloads_and_agent_operations` 651/15Q · `regulated_supply_chain` 582/15Q ·
`gpu_and_ai_finops` 578/15Q.

README §8 status rows (the `31a` precedent):
```
| 11a | `kubernetes_scheduling_and_autoscaling/device_scheduling_and_dra.md` | 2 | 3 | done | 18 |
| 28a | `cloud_cost_optimization_finops/gpu_and_ai_finops.md` | 5 | 6 | done | 15 |
| 35a | `devsecops_and_supply_chain_security/regulated_supply_chain.md` | 7 | 8 | done | 15 |
| 38a | `platform_engineering_and_idp/ai_workloads_and_agent_operations.md` | 7 | 8 | done | 15 |
| 39a | `ml_platform_and_gpu_infrastructure/gpu_scheduling_and_inference_ops.md` | 8 | 11 | done | 18 |
```
Also: README §3 rows 11/28/35/38/39 gain "— plus 1 deep-dive sub-file"; the Senior file-count
table (~line 318) shows those parents as "2 files"; `devops/CLAUDE.md` **sub-files 1 -> 6**.

## Two findings — ONE ALREADY FIXED BY THE ORCHESTRATOR
1. **FIXED.** `kubernetes_scheduling_and_autoscaling.md:105` taught YAML the API server REJECTS:
   `requests: [{name: gpu, deviceClassName: gpu}]` is the `v1beta1` shape. In
   `resource.k8s.io/v1`, `DeviceRequest` is a `oneOf` of `name` + `exactly` | `firstAvailable`
   (verified in `release-1.36` `staging/src/k8s.io/api/resource/v1/types.go`). Corrected in place
   to `requests: [{name: gpu, exactly: {deviceClassName: gpu}}]`; re-rendered exit 0. The new
   sub-file calls this "the single most common authoring error" and has a dedicated Q&A, so the
   parent was contradicting its own child.
2. **The audit report `devops.md:34` cites a FABRICATED statistic** — "73% of platform teams ship
   AI assistants." **No CNCF Platform Engineering Survey 2026 exists** and no CNCF publication
   contains that figure. Real source: Q1 2026 CNCF Technology Radar (CNCF + SlashData) — 35%
   hybrid platform approach for AI workloads, 28% dedicated platform teams, 41% multi-team. The
   agent used those and wrote the debunk into the file so it cannot propagate.

Verification highlights: prices from **AWS's own Price List Bulk API**, two agents matching to the
cent. DRA feature-gate stages read from `pkg/features/kube_features.go` on `release-1.36` — this
**corrected both the k8s blog and its own subagent: `DRAAdminAccess` is GA-and-locked in 1.36,
not Beta.** Three subagent claims discarded as wrong: `vllm:gpu_cache_usage_perc` (v0 name; v1 is
`kv_cache_usage_perc`), `InferenceModel` (superseded by `InferenceObjective`), and doc YAML
resurrecting the `source:` field tombstoned in 1.31. Omitted as unverifiable: any "average GPU
utilization is X%" stat, Bedrock Provisioned Throughput per-model-unit pricing, P6e-GB300 GA.

---

# cs_fundamentals — 5 files, COMPLETE. Section 716 -> 786 Q&As.

| Path | Type | Lines | Q&As |
|---|---|---|---|
| `probabilistic_and_streaming_structures/probabilistic_and_streaming_structures.md` | module | 1002 | 18 |
| `memory_models_and_concurrency_theory/memory_models_and_concurrency_theory.md` | module | 847 | 18 |
| `information_theory_and_compression/information_theory_and_compression.md` | module | 896 | 16 |
| `processes_threads_and_context_switching/async_io_and_event_loops.md` | sub-file | 782 | 15 |
| `dsa_patterns/ai_assisted_round_playbook.md` | sub-file | 561 | 15 |

`STUDY_ORDER["cs_fundamentals"]` — insert AFTER THE NAMED NEIGHBOUR, in this order:
`probabilistic_and_streaming_structures` after `graphs_tries_and_advanced_structures`;
`memory_models_and_concurrency_theory` after `deadlocks_and_synchronization`;
`information_theory_and_compression` after `theory_of_computation`.
**Both sub-files take NO entry** — confirmed empirically (extract.py reports gaps for exactly the
three modules, none for the sub-files).

```
probabilistic_and_streaming_structures
  probabilistic_and_streaming_structures.md  senior
memory_models_and_concurrency_theory
  memory_models_and_concurrency_theory.md  senior
information_theory_and_compression
  information_theory_and_compression.md  -
```
Add under EXISTING blocks: `  async_io_and_event_loops.md  senior` and
`  ai_assisted_round_playbook.md  senior`.
**NO TIER PROMOTION NEEDED** — both parents already `senior`. **Nothing tagged principal**, per
the owner's decision. Module counts 24 -> **27** in section README, section CLAUDE.md, root
README, root CLAUDE.md. `dsa_patterns/` gains the playbook as a **third guide**, NOT a 26th
pattern — no Pattern Index row.

## The three moves — origins left EXACTLY at their floors
- `graphs_tries_and_advanced_structures` -185 lines, now **18 Q&As = its 18 floor**
- `deadlocks_and_synchronization` -301 lines, now **15 Q&As = its 15 floor**
- `greedy_and_divide_and_conquer` -43 lines, now **15 Q&As = its 15 floor**
**All four stale inbound links repointed**, including the two in SIBLING modules
(`computer_architecture_and_memory_hierarchy.md:1287`,
`processes_threads_and_context_switching.md:809`) that pointed at deadlocks *for the model*.

**Every code block in all five files was EXECUTED** (section rule). Measured results now in the
pages: Bloom double-hashing done wrong degrades a 1% target to **14.79%** (0.91% correct);
merging Bloom filters with different seeds -> **94.4% false negatives**; HLL 0.143% actual vs
0.812% bound; consistent hashing moved 9.19% vs 9.09% theoretical; false sharing **0.231s packed
vs 0.068s padded**; the async-IO edge-triggered bug stranded **5,952 of 8,000 bytes**; `zstd -19`
12.70x vs gzip 8.98x on a real 20 MB log.

**Two brief claims CONTRADICTED and kept out**: "at E7+ the AI round is the only coding round"
(unsupported — the picture is one AI-free plus one AI-enabled round) and "the AI cannot touch the
editor" (wrong — CoderPad's **Edit mode** has write access; what is true is the recorded,
replayable transcript). Also caught a subagent confusing `tokio-rs/loom`, a **Rust** crate, with
OpenJDK Project Loom.

Note for CLAUDE.md "Still open": the AI playbook is the shortest-shelf-life page in the repo and
should be re-verified quarterly.

---

# database — 4 files, COMPLETE. Section -> 609 Q&As.

| Path | Type | Lines | Q&As |
|---|---|---|---|
| `analytical_databases_and_olap/analytical_databases_and_olap.md` | module | 1303 | 18 |
| `lakehouse_and_table_formats/lakehouse_and_table_formats.md` | module | 1281 | 16 |
| `case_studies/design_global_multiregion_oltp/design_global_multiregion_oltp.md` | case study | 1194 | 13 |
| `case_studies/design_vector_search_platform/design_vector_search_platform.md` | case study | 1143 | 13 |

`STUDY_ORDER["database"]` — **OLAP first, then lakehouse**, or the second index is wrong:
`analytical_databases_and_olap` after `database/time_series_databases`;
`lakehouse_and_table_formats` after `database/in_memory_databases`.
```
analytical_databases_and_olap
  analytical_databases_and_olap.md  senior, principal
lakehouse_and_table_formats
  lakehouse_and_table_formats.md  principal
```
Case studies: `  design_global_multiregion_oltp/design_global_multiregion_oltp.md  principal`
and `  design_vector_search_platform/design_vector_search_platform.md  senior, principal`
(its tier is the agent's recommendation — `senior` alone is defensible; pairs with audit §3's
proposal to promote `vector_databases` to `senior, principal`).

**Case-study count 6 -> 8** in `case_studies.md` opening line, section README, section CLAUDE.md,
root README. **AND the dependency-map caption saying "the six case studies do not link to one
another" is NOW FALSE** — both new files link to existing ones. Rewrite it or it ships stale.

**Boundary held.** `lakehouse_and_table_formats` owns the CONCEPT and defers twice to
`../../technologies/apache_iceberg/apache_iceberg.md`, which now exists on disk. Both links resolve.

## ~20 primary-source corrections applied (the audit or its own draft was wrong)
ClickHouse stable **26.7.5.10** (not 26.3.9.8) · Aurora zero-ETL GA **Mar/Oct 2024**, not 2026 ·
Iceberg Java **1.11.0** · Delta **4.4.0** (four versions newer than the audit's 4.0) ·
**Iceberg/Delta deletion-vector bitmaps converged but CONTAINERS did not** (Delta `.bin` vs
Iceberg Puffin — not interchangeable) · DuckDB Iceberg **write is GA** (INSERT 1.4, UPDATE/DELETE
1.4.2, MERGE+v3 1.5.3), not read-only · Polaris **Apache TLP since 18 Feb 2026** · CockroachDB
follower reads **4.2s** not 4.8s · CockroachDB PTP/AWS Time Sync **NOT SHIPPED** (open issue only)
· Aurora DSQL **3,000** rows/txn not 10,000, **sequences ARE supported**, and isolation is
**repeatable read / snapshot, NOT serializable** · Aurora Limitless **not retired** · pgvector
**0.8.6** · HNSW citation is **arXiv 2016 / TPAMI 2020**, not "2018 TPAMI".

**The pgvectorscale number was half-fabricated.** "471 QPS" is real but is a **vendor benchmark**
on 50M x **768-dim** (not 1536) Cohere embeddings on `r6id.4xlarge` — and the same benchmark shows
Qdrant with **39-48% lower p95/p99** and a **3.3h vs 11.1h** build. The **"10M -> 50M ceiling" has
no primary source at all** and was DROPPED, replaced with derived RAM math.

**CVE-2026-3172 confirmed REAL** — CVSS 8.1, parallel HNSW build overflow, pgvector 0.6.0-0.8.1,
fixed 0.8.2.

Deliberately NOT asserted: Snowflake/BigQuery/Redshift list prices, Pinecone RU/WU pricing, exact
TiDB/SingleStore versions, Spanner's TrueTime epsilon (**Google no longer publishes one** — the
file uses their current "absorbed into the Paxos RTT" framing and says so), Shopify/Stripe
lakehouse figures. ClickHouse company numbers for Uber/eBay/ByteDance/Sentry were **unsourced and
dropped**; Cloudflare **is** sourced (1.61 quadrillion events/day, 96 trillion scanned in <2s).

Line-count honesty: 1194 and 1143 against the 900-1100 bar, trimmed deliberately then stopped when
cuts began removing verified load-bearing content. 4-9% over. Cheapest ~100 lines in each are the
§11 Q&As (13 against a 10+ bar) if phase B wants them in range.

---

# ===== PHASE A COMPLETE — AUTHORITATIVE STATE, MEASURED FROM THE TOOL =====

**This section supersedes every agent's self-reported numbers.** Produced by running
`extract.py` directly. Where an agent's report and this disagree, THIS is right.

## Bank growth across wave 2
```
                 before wave 2   ->   after phase A
parsed Q&As          12,822              14,005      (+1,183)
authored Shorts      11,731              12,914      (+1,183)
gap                   1,091               1,091      <- book's ORIGINAL remainder, unchanged
```
**The invariant holds exactly.** Every one of the 1,183 new Q&As carries a `**Short:**`,
including all 14 DDIA 2e chapters — which were authored migrated from the start, so they did
NOT widen book's unmigrated gap.

## Per-section (measured)
```
backend 656 · book 1712 · cs_fundamentals 786 · cuda 496 · database 609 · devops 793
fastapi 423 · hld 459 · java 766 · lld 1374 · llm 2254 · ml 1636 · python 497
soft_skills 439 · spring 793 · technologies 306
```

## Sections that delivered MORE than their agent reported before being killed
- **fastapi** — killed by the session limit, but `serving_llm_and_ai_workloads` (**32 Q&As**)
  and `python_api_framework_landscape` (17) are on disk, plus the sub-file
  `api_design_and_versioning/openapi_31_and_client_generation.md`. Section 358 -> **423**.
- **technologies** — killed, but `apache_iceberg` is on disk with **48 Q&As**, plus the sub-file
  `nvidia_triton_inference_server/dynamo_and_the_nvidia_serving_stack.md`. Section 239 -> **306**.
- **python** — 3 new modules: `the_import_system`, `dataclasses_enums_and_data_modeling`,
  `c_extensions_and_native_interop` (16 each), plus the sub-file
  `performance_and_profiling/the_modern_cpython_runtime.md`. Section 434 -> **497**.
- **book / DDIA 2e — ALL 14 CHAPTERS COMPLETE**, `ddia_2e.md` index present. Section 1456 -> **1712**.

**Always re-measure from `extract.py` before concluding an interrupted agent produced nothing.**

## RENDER VALIDATION: DONE CENTRALLY, GREEN
```
bash scripts/prerender_diagrams.sh
[diagrams] 4142 unique fences
[diagrams] done: 559 rendered, 3583 cached, 113 pruned, 0 FAILED; 4142 live assets
PRERENDER_EXIT=0
```
**0 failed** across every fence in the repo, covering all 559 wave-2 diagrams — including the two
`llm` files and java's newly-tagged JFR fence that had no valid per-file result. **No agent needs
to re-validate anything.** `node_modules` was repaired first (`rm -rf` + `npm ci`).

TOOLING NOTE: `build_diagrams.mjs` does NOT start its own server — it needs one on :8901 and
fails with `ERR_CONNECTION_REFUSED`, which looks nothing like a Mermaid error.
**`scripts/prerender_diagrams.sh` is the wrapper that starts it.** Use the wrapper.

## `**Short:**` length: ZERO out of range repo-wide
Both build-failing lines fixed: `technologies/apache_iceberg` (232 -> in range, by its own agent)
and `devops/cloud_fundamentals_and_aws:610` (222 -> in range, by the orchestrator).

## Orchestrator content fixes applied directly (no agent owned these)
1. `devops/kubernetes_scheduling_and_autoscaling.md:105` — the DRA `DeviceRequest` flat form is
   `v1beta1` and is REJECTED by `resource.k8s.io/v1`. Now
   `requests: [{name: gpu, exactly: {deviceClassName: gpu}}]`. The parent was contradicting its
   own new sub-file, which calls this "the single most common authoring error".
2. `java/performance_and_tuning/performance_and_tuning.md:49,653` — recommended
   `-Djdk.tracePinnedThreads=full`, **removed by JEP 491 in Java 24**, while its own new
   sub-files teach the replacement. Both now use the `jdk.VirtualThreadPinned` JFR event.
3. `devops/cloud_fundamentals_and_aws.md:610` — 222-char Short trimmed.

# ===== PHASE B WORKLIST — EXACT, MEASURED =====

## 41 modules need a `STUDY_ORDER` entry
book 14 · hld 7 · python 3 · lld 3 · cs_fundamentals 3 · backend 3 · java 2 · fastapi 2 ·
database 2 · technologies 1 · spring 1

**`book/ddia_2e/*` is the exception to the two-segment rule** — book ids are
`book/<book>/<chapter>`. Also needs `BOOK_LABELS` + a `STUDY_ORDER.book` position in `app.js`.

**NEVER paste an index from an agent report — grep the named predecessor and insert after it.**
(ml's agent gave three different wrong numbers for two entries.)

## 24 files exist on disk but are missing from a `<!-- study-paths -->` block
Each is FATAL under `--strict`. Full list, by section:
- **cs_fundamentals** (2): `dsa_patterns/ai_assisted_round_playbook.md`,
  `processes_threads_and_context_switching/async_io_and_event_loops.md`
- **cuda** (2): `streams_events_and_concurrency/green_contexts_and_sm_partitioning.md`,
  `multi_gpu_programming_and_nccl/device_initiated_communication.md`
- **devops** (5): all five sub-files
- **java** (3): `performance_and_tuning/jfr_and_production_diagnostics.md`,
  `performance_and_tuning/jvm_in_containers.md`,
  `security_and_cryptography/post_quantum_and_modern_jca.md`
- **lld** (4): `concurrency_patterns/modern_java_concurrency.md`,
  `system_design_problems/{LoggingFramework,NotificationService,TaskScheduler}.md`
- **llm** (3): `alignment_and_rlhf/rl_environments_and_agentic_rl.md`,
  `agents_and_tool_use/agent_skills_and_progressive_disclosure.md`,
  `agentic_frameworks/microsoft_agent_framework.md`
- **ml** (2): `recommender_systems/generative_recommenders.md`,
  `time_series_forecasting/time_series_foundation_models.md`
- **python** (1): `performance_and_profiling/the_modern_cpython_runtime.md`
- **fastapi** (1): `api_design_and_versioning/openapi_31_and_client_generation.md`
- **technologies** (1): `nvidia_triton_inference_server/dynamo_and_the_nvidia_serving_stack.md`

## THE TIER PRECONDITIONS — every one is a silent, green-build failure
A sub-file CANNOT join a tier its parent module page skips.

| Section | Sub-file | Parent tier NOW | Needs | Verdict |
|---|---|---|---|---|
| devops | `gpu_scheduling_and_inference_ops` | `-` | senior+principal | **PROMOTE PARENT** |
| devops | `regulated_supply_chain` | `-` | senior | **PROMOTE PARENT** |
| devops | `ai_workloads_and_agent_operations` | `-` | senior+principal | **PROMOTE PARENT** |
| devops | `gpu_and_ai_finops` | `-` | senior+principal | **PROMOTE PARENT** |
| cuda | `device_initiated_communication` | `-` | senior (audit §3) | **PROMOTE PARENT, or tag `-`** |
| ml | `time_series_foundation_models` | `-` | senior (audit §3) | **PROMOTE PARENT, or tag `-`** |
| ml | `generative_recommenders` | senior | senior (principal on merit) | safe as `senior` |
| llm | `microsoft_agent_framework` | principal only | — | **TAG `-`** (agent's own recommendation: all 18 sibling sub-files incl. `langgraph.md` are `-`; 19 vendor files would flood any tier) |
| java, cs_fundamentals, spring, cuda(green_contexts) | — | already covered | — | no promotion needed |

## Counts to update
cs_fundamentals modules 24 -> 27 · java modules 33 -> 35 · spring 39 -> 40 modules, 9 -> 10 case
studies · database case studies 6 -> 8 · cuda case studies 6 -> 8 + sub-files 0 -> 2 · devops
sub-files 1 -> 6 · ml case studies 24 -> 25, sub-files 37 -> 39 · llm sub-files 85 -> 88, case
studies 29 -> 30 · backend case studies 5 -> 7 · **book: a whole new DDIA 2e entry**.

## Two stale sentences that become FALSE once wave 2 lands
- `database/case_studies/case_studies.md`: "**Six** end-to-end database design case studies" ->
  eight, AND the dependency-map caption "the six case studies do not link to one another" is now
  wrong — both new files link to existing ones.
- `backend/case_studies/case_studies.md`: the caption naming which studies "share no patterns".

## Files agents DECLINED to write — STRIKE from the worklist, do not chase
- **spring 5**: `spring_security_modern_auth`, `spring_ai/mcp_servers_and_clients`,
  `spring_performance/startup_and_aot_cache`, `spring_cloud_patterns/core_resilience_framework7`,
  `spring_ai/agents_and_tool_calling` — all payloads already inline from wave 1.
- **cuda 5**: `modern_mma_wgmma_and_tcgen05`, `pytorch_custom_ops_and_integration`,
  `cutile_and_tile_ir`, `nsight_compute_metrics_reference`, and the whole proposed
  `asynchronous_execution_and_warp_specialization` MODULE — its every bullet now lives across
  four existing modules. Writing it means duplicating four modules or gutting them: **an owner
  structural decision, not a phase-B write.**
- **llm 4**: `optimizers_and_hyperparameter_transfer` (audit claim FALSE — muP already has a
  diagram, `muPLinear`, and scaling rules; only **Muon** was missing, added in-file),
  `serving_fleet_orchestration`, `reasoning_distillation`, reasoning-mode routing.
- **fastapi**: `asgi_runtimes_and_free_threading` — absorbed into
  `production_deployment_and_scaling`.

## Audit claims proven FABRICATED or FALSE this wave — do not resurrect
- `devops.md:34` **"73% of platform teams ship AI assistants"** — **no CNCF Platform Engineering
  Survey 2026 exists.** Real: Q1 2026 CNCF Technology Radar, 35%/28%/41%.
- Spring GraphQL **"trusted documents"** — does not exist; both feature requests closed declined.
- **JSR-352 removed in Batch 6.0** — it was **5.0**, 2022.
- **"Framework 7 made CGLIB the framework-wide default"** — false; core Framework still defaults
  to interface proxies. Was live in `spring/README.md`; corrected.
- **Spring Cloud "pinned to Boot 4.0.x"** — false; the 2025.1 train supports 4.0.x AND 4.1.x. Was
  live in FOUR files; all corrected (8 edits).
- **pgvectorscale "10M -> 50M ceiling"** — no primary source at all; dropped.
- llm audit's **muP "name-checked without derivation"** — false.
- cs_fundamentals brief's **"at E7+ the AI round is the only coding round"** and **"the AI cannot
  touch the editor"** — both unsupported/wrong.

---

# ===== PHASE B EXECUTED — 2026-08-26 — BUILD GREEN =====

Done by the orchestrator directly (session limits had killed the agent pool). One serialized pass,
as required.

## What was wired
- **41 `STUDY_ORDER` entries** inserted in `game/app.js`, every one placed **after a named
  predecessor**, never by index — the rule this document established after ml's agent reported
  three different wrong numbers for two entries.
- **`BOOK_LABELS.ddia_2e`** added: `{ name: "Designing Data-Intensive Applications (2nd ed.)",
  author: "Kleppmann & Riccomini", short: "DDIA 2e" }`, plus all 14 chapters in `STUDY_ORDER.book`.
- **~60 `<!-- study-paths -->` lines** across 15 section READMEs: new module blocks, the 24
  orphaned sub-files, and 11 case studies.
- **6 tier promotions**, each a silent-failure precondition:
  devops `ml_platform_and_gpu_infrastructure` `-` -> `senior, principal`;
  `devsecops_and_supply_chain_security` `-` -> `senior`;
  `platform_engineering_and_idp` `-` -> `senior, principal`;
  `cloud_cost_optimization_finops` `-` -> `senior, principal`;
  cuda `multi_gpu_programming_and_nccl` `-` -> `senior`;
  ml `time_series_forecasting` `-` -> `senior`.
- **devops now has a Principal tier** (the owner's decision), with the
  `<!-- study-path-table principal -->` markers added and the table generated.
- `llm/microsoft_agent_framework.md` tagged **`-`**, per its own agent's recommendation — all 18
  sibling sub-files including `langgraph.md` are `-`, and that curation is deliberate.

## THE TIER GUARD CAUGHT A REAL ONE
`--strict` rejected the first attempt with:
```
fastapi/README.md study-paths[api_design_and_versioning]: 'api_design_and_versioning.md' must
carry 'senior' -- the module page is never optional when any of its files is in a tier
```
`openapi_31_and_client_generation.md` was tagged `senior` under a `-` parent. **This is exactly the
failure this whole document warns about, and the guard caught it rather than shipping it
invisible.** Parent promoted to `senior`. It also surfaced a sub-file nobody had reported —
`fastapi/serving_llm_and_ai_workloads/mcp_servers_from_fastapi.md` — now listed.

## Case-study indexes updated — all 7 sections, per the same-commit maintenance rule
backend (2 new groups: AI Workload Serving, Outbound Integration) · database (Global Distribution
& Multi-Region, Vector Search & Retrieval) · spring (Group 6: AI and Tool Platforms) · ml (into
Group 6 NLP) · llm (Phase 7 — Internal Agent Platforms) · cuda (Phase D — Modern Architecture &
Multi-GPU) · hld (Media Pipelines & Scheduled Work). Interview Prep rows added in each.

## Three stale sentences corrected (each became FALSE when wave 2 landed)
- `database/case_studies.md`: "Six end-to-end..." -> "Eight...", and the dependency-map caption
  claiming **"the six case studies do not link to one another"** — now scoped to the original six,
  with the two new real citations named (the multi-region study reuses the banking ledger's
  claim-check-complete protocol; the vector study cites the multi-tenant isolation tiers and the
  `SET LOCAL`-under-PgBouncer trap).
- `backend/case_studies.md`: the "share no patterns" caption extended to the two new studies.
- `cuda/case_studies.md`: "6 principal-grade case studies" -> 8.

## Root `CLAUDE.md` counts refreshed against measured reality
backend 35->38 mod / 5->7 cs · database 31->33 / 6->8 · java 33->35 · spring 39->40 / 9->10 ·
python 21->24 / **0->5 case studies** · fastapi 19->21 / 6->7 · ml 24->25 cs · llm 29->30 cs,
sub-files 85->88 · devops sub-files 1->6 · cuda 6->8 cs + 2 sub-files · cs_fundamentals 24->28 ·
hld 18->25 mod / 26 cs · technologies 7->8 · book gains **DDIA 2e (Kleppmann & Riccomini) 14**.

## FINAL VERIFICATION
```
python3 game/extract.py --strict     EXIT 0    wiring check: OK
bash scripts/build_banks.sh          EXIT 0    OK: sections=16 questions=13999 graphs=16
**Short:** out of range               0        (repo-wide)
parsed Q&As                          14,005    authored Shorts 12,914    gap 1,091
```
**The gap is still exactly 1,091 — book's original unmigrated remainder.** It has not moved through
the entire wave, which is the proof that all 1,183 new Q&As carry an authored `**Short:**`.

Working tree: **462 files changed, +40,967 / -8,126**, 531 paths. Nothing committed.
