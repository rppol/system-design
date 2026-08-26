# llm RAG/reasoning/prompting cluster — supplementary profile

Recovered from a profiling subagent whose parent auditor had already exited. These findings
are NOT in `llm.md`; treat this file as an addendum to it.

## 1. Template headings
None missing. All 16 files conformant 1-14.

**`llm/CLAUDE.md` records a divergence for `advanced_rag/*` (unnumbered Intuition, missing
§4, swapped 5/6) that is STALE — all 7 files were verified already fixed.** Correct the
CLAUDE.md note; do not "fix" the files.

## 2. Files with no broken-code -> fix example (§10 pitfalls are prose-only)
- `rag_fundamentals/embedding_models.md`
- `rag_fundamentals/retrieval_methods.md`
- `rag_fundamentals/reranking.md`
- `advanced_rag/corrective_rag.md` (full reference impl, not a broken/fixed pair)
- `advanced_rag/graph_rag.md` (2 flagged code-comment bugs, no paired blocks)
- `advanced_rag/multimodal_rag.md`
- `advanced_rag/query_transformation.md`
- `advanced_rag/self_rag.md` (reference impl only)

## 3. Stale datable claims — several are INTERNAL CONTRADICTIONS

| File | Line | Issue |
|---|---|---|
| `code_generation.md` | 346-352 | "Claude 3.5 Sonnet \| 93% \| 200K" given as the current recommendation |
| `code_generation.md` | 510 | case-study code says `"Generate with Claude 3.5 Sonnet (200K context)"` |
| `code_generation.md` | 317-322 | Copilot history frozen at Codex(2021) -> GPT-4 Copilot(2023) -> custom models |
| `code_generation.md` | 336-339 | DeepSeek-Coder 33B as SOTA open model — superseded elsewhere in this corpus |
| `code_generation.md` | 422 | "71% with o3 + scaffolding **as of 2025**" — annotation, not replacement |
| `code_generation.md` | 458 | "unsettled **as of 2025**; class actions against GitHub are ongoing" |
| `code_generation.md` | 214-218 | HumanEval scores presented as current, no 2026 model |
| `multimodal_rag.md` | 168, 230, 400, 719, 743 | code pins `model="gpt-4o"` while **§8's own table at L460-466** treats GPT-5.6 / Sonnet 5 / Gemini 3.5 as current |
| `rag_fundamentals.md` | 1077, 1402, 1434 | `model="claude-sonnet-4-6"` — inconsistent with the corpus's Opus 5 / Sonnet 5 naming |
| `advanced_rag.md` | 911, 1084, 1163 | same `claude-sonnet-4-6` artifact |

Excluded as correctly fresh: the "July 2026"-stamped claims in `embedding_models.md`,
`multimodal_rag.md` §8, and `graph_rag.md` §6.3.

**Note the shape:** `multimodal_rag.md` contradicts itself — its prose table is current and
its code is not. That is the cheapest class of fix and the most embarrassing to ship.
The two "as of 2025" lines in `code_generation.md` are REPLACE-DO-NOT-ANNOTATE violations.

## 4. Absent topics, by cluster
- **RAG:** RAPTOR; audio/video-modality RAG; retrieval-augmented fine-tuning (RAFT)
- **Reasoning:** 2025-2026 models beyond the named five (Kimi k1.5, Gemini Deep Think);
  PRM/verifier training methodology; reasoning-model distillation recipes
- **Prompting:** PromptOps (versioning, CI-gated eval); constrained-decoding internals;
  cross-model prompt portability
