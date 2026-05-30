# Cross-Cutting Case Study Sub-Files

These nine files are shared infrastructure primitives consumed by multiple LLM case studies. Each follows the full 14-section module template with 15+ interview Q&As. They exist here rather than inside individual case studies to prevent the same pattern (eval harness, multi-region topology, tenant isolation) from being invented independently — and inconsistently — in each case study.

Read a cross-cutting file when you want to understand a production infrastructure pattern in depth. The case studies that reference each file are listed in that file's Section 14 (Case Study).

---

## Deep-Dive Files

| File | Topic | Referenced by |
|------|-------|---------------|
| [llm_eval_harness_in_production.md](./llm_eval_harness_in_production.md) | Golden-set construction, LLM-as-judge orchestration, regression detection, eval-gated CI, online eval sampling | chatgpt, search_engine, notion_ai, legal_ai, code_review, customer_support, data_analyst, autonomous_swe |
| [opentelemetry_for_llm_apps.md](./opentelemetry_for_llm_apps.md) | Trace propagation across LLM/tool/agent spans, `gen_ai.*` semconv, span hierarchy for streaming, cost attribution | llm_gateway, chatgpt, copilot, data_analyst, autonomous_swe, browser_research, search_engine |
| [multi_region_llm_topology.md](./multi_region_llm_topology.md) | Active-active topology, anycast + latency routing, KV-cache locality, conversation stickiness, region failover | chatgpt, copilot, llm_gateway, real_time_translation, gpu_inference_platform, browser_research |
| [red_team_eval_harness.md](./red_team_eval_harness.md) | Adversarial dataset construction, jailbreak scoring, prompt-injection corpora, model card generation, continuous red-team CI | chatgpt, content_moderation, customer_support, legal_ai, computer_use_agent |
| [gpu_pool_economics.md](./gpu_pool_economics.md) | H100/H200/B200 utilization math (MFU/MBU), spot/preemptible blending, cold-start optimization, prefill/decode disaggregation | gpu_inference_platform, chatgpt, copilot, video_generation, fine_tuning_platform |
| [tenant_isolation_patterns.md](./tenant_isolation_patterns.md) | Per-tenant vector DB namespacing vs collections vs clusters, ACL pushdown into retrieval, noisy-neighbor mitigation | legal_ai, notion_ai, customer_support, gpu_inference_platform, llm_gateway, data_analyst |
| [streaming_at_scale.md](./streaming_at_scale.md) | SSE vs WebSocket vs HTTP/2, half-open connection detection, backpressure on slow clients, partial-token replay, abort handling | chatgpt, copilot, real_time_translation, data_analyst, browser_research, video_generation |
| [agent_durability_patterns.md](./agent_durability_patterns.md) | Checkpointing, mid-task interruption + resume, replay determinism, idempotent tool calls, Temporal/LangGraph integration | autonomous_swe, browser_research, computer_use_agent, data_analyst |
| [training_loop_internals.md](./training_loop_internals.md) | Forward/backward/optimizer step, gradient accumulation, FSDP wrap policy, async checkpoint manager, loss-spike detection | fine_tuning_platform, chatgpt (RLHF), legal_ai (domain pre-training), video_generation (diffusion) |

---

## How to Use These Files

Each sub-file is self-contained — read it independently to understand the infrastructure pattern end-to-end. Then navigate to the case studies listed in its Section 14 to see how the pattern is applied in a specific product context.

Cross-reference links within case studies point here using relative paths:
```
[LLM Eval Harness](./cross_cutting/llm_eval_harness_in_production.md)
[Multi-Region Topology](./cross_cutting/multi_region_llm_topology.md)
```

---

## Adding a New Cross-Cutting Sub-File

A pattern belongs here (not inside a case study) when it is referenced by 3+ case studies. Steps:

1. Create `cross_cutting/<pattern_name>.md` — 14-section module template, 15+ Q&As, 600-800 lines
2. Section 14 must list all case studies that use it, with one paragraph each on the specific application
3. Update this README table
4. Update `../../../README.md` (root) and `../../README.md` (llm master index) Sub-Files Index
