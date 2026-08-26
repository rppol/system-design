# Wave 2 — the serialized wiring pass

**Run this as ONE agent, never in parallel.** Every item below touches `game/app.js`
(`STUDY_ORDER`), a section README's `<!-- study-paths -->` block, or a generated tier table.
Concurrent edits to those take the Pages deploy red. Wave 1 was content-only precisely so
this could be a single ordered pass.

## Order of operations (non-negotiable)

1. Create the files.
2. Add each to `STUDY_ORDER["<section>"]` in `game/app.js` at its stated position.
   **Sub-files get NO `STUDY_ORDER` entry** — they group under their parent via
   `splitModulePath()`. Adding one is fatal under `--strict`.
3. Add each to its section README's `<!-- study-paths -->` block with its tiers.
4. **TIER PROMOTIONS BEFORE OR WITH SUB-FILE CREATION.** A sub-file cannot join a tier its
   parent module skips. `devops` found four of its five proposed sub-files have Full-only
   (`-`) parents, so tagging them `senior` would leave them invisible to Senior readers with
   a green build. This is the wave's most dangerous silent failure.
5. Section README module/case-study tables, section CLAUDE.md, root README, root CLAUDE.md counts.
6. New case studies ALSO need rows in `case_studies/case_studies.md` — all five parts
   (Quick Start, Full Learning Path, Cross-Cutting, Dependency Map, Interview Prep) in the
   same commit.
7. `python3 game/extract.py --write-paths` — then `--strict`. Never hand-edit a tier table.
8. `bash scripts/build_banks.sh` — **must exit 0. `--strict` passing does NOT mean the
   script passes; it has its own verifier after it.**

## Files to create

### backend
| File | Tier | STUDY_ORDER position |
|---|---|---|
| `serving_ai_workloads/serving_ai_workloads.md` | senior + principal | Phase 3, after `async_and_concurrency_patterns` (idx 14) |
| `webhooks_and_event_delivery/webhooks_and_event_delivery.md` | senior + principal | Phase 2, after `websockets_and_sse` (idx 9) |
| `api_contracts_and_schema_governance/api_contracts_and_schema_governance.md` | senior + principal | Phase 2, after `rest_api_design` (idx 5) |
| `case_studies/design_llm_serving_api/design_llm_serving_api.md` | senior | none (case study) |
| `case_studies/design_webhook_delivery_service/design_webhook_delivery_service.md` | senior | none |

### java (`STUDY_ORDER["java"]` at `game/app.js:265-272`)
| File | Tier | Position |
|---|---|---|
| `java22_to_26_features/java22_to_26_features.md` | senior + principal | after `"java/java9_to_21_features"` (line 266) |
| `aot_startup_and_class_data_sharing/aot_startup_and_class_data_sharing.md` | senior + principal | **before** `"java/graalvm_native_image"` (line 267) |
| `security_and_cryptography/post_quantum_and_modern_jca.md` | principal | sub-file, NO entry |
| `performance_and_tuning/jvm_in_containers.md` | senior + principal | sub-file, NO entry. **Scope shrank** — cgroups/`MaxRAMPercentage`/NMT/exit-137 already landed in `cross_cutting/jvm_tuning_and_gc_for_services.md` |
| `performance_and_tuning/jfr_and_production_diagnostics.md` | senior | sub-file, NO entry |

### database
`analytical_databases_and_olap/`, `lakehouse_and_table_formats/`,
`case_studies/design_global_multiregion_oltp/`, `case_studies/design_vector_search_platform/`.
**Coordinate `lakehouse_and_table_formats` with `technologies/apache_iceberg`** — both were
proposed; pick one home.

### devops (ALL FIVE ARE SUB-FILES — no `STUDY_ORDER` entries)
`device_scheduling_and_dra`, `gpu_scheduling_and_inference_ops`, `regulated_supply_chain`,
`ai_workloads_and_agent_operations`, `gpu_and_ai_finops`.
**Only `device_scheduling_and_dra` has a Senior parent.** The other four parents are `-`, so
sequence the tier promotions first (see rule 4). Each needs a README §8 status row following
the `31a` precedent, and `CLAUDE.md` "Deep-dive sub-files — 1" → 6.

### cs_fundamentals — three are PARTLY PRE-EMPTED; move, do not re-derive
| File | Note |
|---|---|
| `probabilistic_and_streaming_structures/` (pos 12, senior) | `graphs_tries_and_advanced_structures` §4 now holds the sizing formulas, comparison table and broken→fix. **Move them and leave a stub link.** |
| `memory_models_and_concurrency_theory/` (pos 22, senior) | `deadlocks_and_synchronization` §3/§4/§5/§10 now hold happens-before, SC/TSO/weak, linearizability, the litmus grid, the ABA pair. **Its See-Also and two sibling modules already point HERE for the model — repoint them in the same commit or the links go stale.** |
| `dsa_patterns/ai_assisted_round_playbook.md` | sub-file. Highest value, shortest shelf life — re-verify formats at write time |
| `information_theory_and_compression/` (pos 27, tier `-`) | `greedy_and_divide_and_conquer` §6 now defines self-information, `H(X)`, source coding, KL, Kolmogorov |
| `processes_threads_and_context_switching/async_io_and_event_loops.md` | sub-file. Genuinely untouched — `io_uring`/`futex`/`vDSO` still zero |

### technologies (all sub-files except iceberg)
`apache_iceberg/` (pos 9, coordinate with database), plus sub-files for Dynamo,
Gateway API Inference Extension, Temporal versioning, Debezium failover/lakehouse,
Vault workload identity, Airflow assets/state-store.
**Tiering (senior 8 / principal 8) unblocks only once these land** — and
`game/CLAUDE.md`'s toggle-exception language still lists `technologies` as untiered; update
it in the same commit.

### ml
`time_series_forecasting/time_series_foundation_models.md` (sub-file — content now inline in
§4.4, **extract it**), `recommender_systems/generative_recommenders.md` (sub-file — content
now in `deep_learning_recommenders.md` §4.7),
`case_studies/design_llm_hybrid_support_triage.md` (§14 of `classical_ml_vs_llm.md` is a
111-line seed).

### Remaining sections
Fill in from the fixer reports for llm, cuda, book, hld, lld, fastapi, python, spring as they land.

## Owner decisions — RESOLVED 2026-08-25. Do not re-ask.

### Taken by the assistant (single-section, mechanical, each recommended with reasoning by the agent that read the whole section)
- **ml**: `model_selection_and_algorithm_choice` 47 -> 11
- **spring**: `validation_and_error_handling` 19 -> 12
- **llm**: `coding_agents` + `code_generation` into senior; `agentic_frameworks` add senior
- **lld**: swap `solid_principles` ahead of `design_principles`
- **cuda**: `triton_and_kernel_dsls` 21 -> 14 and into senior
- **fastapi**: `testing_fastapi` 12 -> 7; `api_design_and_versioning` tier fix
- **backend**: demote `rabbitmq_deep_dive` from senior; promote `kafka_deep_dive` +
  `rate_limiting_in_depth` to principal
- **database**: `database_caching_patterns` 25 -> 8 and onto senior

`python`'s full `STUDY_ORDER` reshape + 8 tier moves is NOT in this list — it is a whole-section
resequence, not a move. Execute it only if the python fixer's report justifies each of the 8
individually; otherwise take only the moves it argues for and record the rest as declined.

### Owner-decided
- **devops: CREATE a Principal tier.** Reverses `devops/CLAUDE.md`'s "needs none" — update that
  sentence in the same commit. This is a curated cut, NOT a re-tag of the senior list: principal
  is judgment (multi-region cost tradeoffs, platform-team boundaries, migration sequencing, what
  to tell a team NOT to do), and roughly half of it should be material senior never sees.
  **Sequence this BEFORE the five devops sub-files** — four of them have Full-only parents today,
  and a sub-file cannot join a tier its parent skips (see rule 4).
- **cs_fundamentals: NO Principal tier.** `cs_fundamentals/CLAUDE.md` stands as written. The
  audit's recommendation is recorded and declined. Still do the
  `computer_architecture_and_memory_hierarchy` 20 -> 2 move and its senior tagging.
- **technologies: DEFER the tiering AGAIN.** Wave 2 creates the P0 sub-files; it does NOT assign
  senior 8 / principal 8. Tiering happens in a later pass, once the sub-files are on disk — that
  was the deferral's own stated precondition and it is still unmet at the START of wave 2.
  `game/CLAUDE.md`'s toggle-exception language therefore stays as-is; do not touch it.
- **book: CARRY BOTH DDIA EDITIONS.** Add `book/ddia_2e/` as a sibling book folder; the existing
  1e summaries are NOT retired. **This is NOT a wave-2 item** — it is ~14 new chapter summaries
  and belongs to its own authoring pass with its own agent. Wave 2 does no book work beyond
  what a fixer already left wired. When it does run: `BOOK_LABELS` + `STUDY_ORDER.book` in
  `game/app.js` need the new book, and a per-book index `book/ddia_2e/ddia_2e.md` is required.
- **root CLAUDE.md: the 900-1100 line principal case-study bar STANDS.** ml's proposal to lower
  it to 500-800 is declined. Do not edit that line. A specific padded case study is trimmed on
  its own merits, not because the bar moved.

## RESOLVED — do not re-litigate
- **`backend/load_and_performance_testing` vs `devops/performance_and_load_testing`**:
  reciprocal cross-link + a one-sentence scope statement in each §1. **No content move.**
  (backend's granular reading won; devops's own evidence — Kubernetes mentioned 3 times —
  argues for strengthening its platform angle, not stripping its concept layer.)
- **`java`: `functional_programming` → `java_streams` merge**: DON'T. Retarget in place to
  ~450-500 lines (custom `Collector` authoring, composition, immutability). Deletion is the
  most expensive op in this repo and risks silent Q&A loss.
- **`cs_fundamentals/case_studies/`**: documented adapted template. Never rewrite to the
  11-section principal one.

## Known tooling hazards
- Concurrent `extract.py` runs race on `game/questions/` — retry on
  `PermissionError`/`FileNotFoundError`.
- `check_render.mjs` has a hardcoded 120s cap and **exits 3 on timeout** — that means NOT
  VALIDATED, not "parse failure". Piping through `tail` masks it.
- `game/extract.py` lives under `src/main/java/com/rutik/systemdesign/`; `scripts/` is at repo root.
