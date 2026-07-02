# LLM Case Studies — Learning Path

29 case studies + 9 cross-cutting infrastructure deep-dives.

Each case study is 900–1,100 lines: scale math, architecture diagrams (ASCII and Mermaid), executable Python code, production war stories, and 10+ design-rationale interview Q&As. Each cross-cutting file is 600–800 lines covering one infrastructure primitive that recurs across many products.

---

## Quick Start (if you only have time for three)

| Order | File | Why start here |
|-------|------|----------------|
| 1 | [design_chatgpt.md](./design_chatgpt.md) | Canonical reference — covers the core LLM serving loop, streaming, RLHF, prefix-cache economics. All other case studies assume you know this foundation. |
| 2 | [design_rag_pipeline.md](./design_rag_pipeline.md) | The backbone of 80% of enterprise LLM applications. Covers chunking, embedding, hybrid retrieval, and reranking end-to-end. |
| 3 | [design_gpu_inference_platform.md](./design_gpu_inference_platform.md) | Infrastructure layer that every product sits on top of. Covers multi-tenant GPU scheduling, cold-start, LoRA multiplexing, autoscaling. |

---

## Full Learning Path

### Phase 1 — Core LLM Serving (start here)

Read these before anything else. They define the vocabulary and patterns every subsequent case study references.

| File | Primary Engineering Concern | What It Teaches |
|------|-----------------------------|-----------------|
| [design_chatgpt.md](./design_chatgpt.md) | Conversation serving at scale | Streaming SSE pipeline, RLHF reward model, prefix-cache hit economics, multi-region active-active routing, abuse detection without false positives |
| [design_rag_pipeline.md](./design_rag_pipeline.md) | Retrieval-Augmented Generation | Chunking strategies, embedding model selection, BM25 + dense hybrid retrieval, cross-encoder reranking, answer grounding and hallucination detection |
| [design_llm_gateway.md](./design_llm_gateway.md) | Provider abstraction and control plane | Semantic cache with threshold tuning, provider-adapter pattern, Lua sliding-window rate limiting, cost-aware model routing, per-tenant quota enforcement |

---

### Phase 2 — Production Applications

Core product categories. Each teaches a different primary engineering constraint.

| File | Primary Engineering Concern | What It Teaches |
|------|-----------------------------|-----------------|
| [design_copilot.md](./design_copilot.md) | Latency-critical completions | Fill-in-the-middle (FIM) prompt assembly, debounce + cancellation state machine, prefix-cache hit ratio math, 200ms latency budget breakdown, streaming partial tokens to IDE |
| [design_ai_search_engine.md](./design_ai_search_engine.md) | Grounded answer generation | Parallel query fan-out (BM25 + dense + sparse), RRF merging, cross-encoder reranking, citation extraction, answer-grounding scoring, real-time web crawler integration |
| [design_notion_ai.md](./design_notion_ai.md) | Per-tenant workspace isolation | Per-workspace vector DB namespacing, ACL pushdown into Pinecone/Qdrant filters, block-level SSE handler, noisy-neighbor mitigation, personal vs workspace context separation |
| [design_customer_support_bot.md](./design_customer_support_bot.md) | Intent classification and escalation | Intent classifier with confidence thresholds, conversation state machine (active → handoff → resolved), injection adversarial gating, human escalation queue, SLA-bound response routing |
| [design_real_time_translation.md](./design_real_time_translation.md) | Ultra-low latency pipelines | STT→MT→TTS WebSocket pipeline, VAD threshold tuning, partial-result re-translation on ASR finalize, per-stage latency budget (STT 80ms + MT 40ms + TTS 60ms = 180ms total) |

---

### Phase 3 — Content Generation and Moderation

Systems where the primary concern is quality, safety, or creative generation.

| File | Primary Engineering Concern | What It Teaches |
|------|-----------------------------|-----------------|
| [design_ai_content_moderation.md](./design_ai_content_moderation.md) | Classifier cascade and adversarial eval | Cheap-to-expensive moderation cascade, explicit confidence thresholds at each stage, jailbreak adversarial eval harness, human-reviewer queue integration, precision/recall tradeoffs per content category |
| [design_ai_code_review.md](./design_ai_code_review.md) | Diff-aware generation | Diff parser, hunk-to-prompt assembly, comment-posting via GitHub App webhook, false-positive rate eval harness, PR-level context window management |
| [design_ai_coding_assistant.md](./design_ai_coding_assistant.md) | Code-aware RAG | Repository-level context engineering, function-level chunking, test generation from function signatures, multi-file diff application, SWE-bench Verified scoring |
| [design_image_generation_platform.md](./design_image_generation_platform.md) | Async generation at scale | Diffusion step-count vs quality tradeoffs, async job queue + webhook notification UX, NSFW safety filter cascade, C2PA watermarking, GPU cost per image math |
| [design_video_generation_platform.md](./design_video_generation_platform.md) | Multi-modal generation sequencing | DiT vs UNet scheduling, temporal coherence across 5–30s clips, audio-video sync (Veo 3 pattern), storage IO sizing (1 video = 50MB+), 5–10 minute generation UX |
| [design_voice_cloning_tts_platform.md](./design_voice_cloning_tts_platform.md) | Real-time voice synthesis | Speaker embedding extraction, zero-shot voice cloning, streaming TTS with <200ms first-byte latency, voice consent gate, deepfake abuse detection |

---

### Phase 4 — Agents and Agentic Systems

Read [agent_durability_patterns.md](./cross_cutting/agent_durability_patterns.md) from the cross-cutting files before this phase.

| File | Primary Engineering Concern | What It Teaches |
|------|-----------------------------|-----------------|
| [design_computer_use_agent.md](./design_computer_use_agent.md) | VLM-driven UI control | Screenshot tokenization economics (~1,500 tokens/frame), action confirmation gates, OS-level sandboxing (VM vs container), trajectory replay for debugging, CAPTCHA/auth-wall handling |
| [design_autonomous_swe_agent.md](./design_autonomous_swe_agent.md) | Long-horizon durable execution | 50+ tool call planning, hour-scale task checkpointing, repo-level context engineering, test-driven self-correction loop, per-task cost ceiling ($2–20), SWE-bench scoring infra |
| [design_browser_research_agent.md](./design_browser_research_agent.md) | Multi-source synthesis | Sub-question decomposition, source deduplication across 100+ URLs, citation provenance graph, mid-process plan revision, paywall/JS-rendering handling, report-grade vs chat-grade output |
| [design_ai_data_analyst.md](./design_ai_data_analyst.md) | Sandboxed code execution | Schema inference, iterative SQL/Python generation, sandboxed execution with memory limits, chart rendering pipeline, ambiguous query clarification loop |
| [design_sales_ai_agent.md](./design_sales_ai_agent.md) | Multi-week stateful agents | Prospect sequence state across weeks (Temporal/Postgres checkpoints), CRM integration, email/LinkedIn tool orchestration, personalization without hallucinating company facts |
| [design_ai_meeting_assistant.md](./design_ai_meeting_assistant.md) | Streaming transcription and summarization | 30s sliding-window Whisper transcription with overlap dedup, bot-in-meeting vs local-process architecture, map-reduce summarization for 2-hour meetings, GDPR per-participant consent |

---

### Phase 5 — Platform and Infrastructure

Systems that other products run on top of. Highest engineering depth in the collection.

| File | Primary Engineering Concern | What It Teaches |
|------|-----------------------------|-----------------|
| [design_gpu_inference_platform.md](./design_gpu_inference_platform.md) | Multi-tenant GPU scheduling | Cold-start latency (70B load from S3 = 60–300s), LoRA multiplexing across 1,000s of adapters, autoscaling on tokens/sec not RPS, spot/preemptible blending, per-tenant token accounting |
| [design_llm_fine_tuning_platform.md](./design_llm_fine_tuning_platform.md) | Distributed training platform | Training loop code (forward/backward/optimizer), FSDP wrap policy, async checkpoint manager, loss-spike detection, per-tenant K8s namespace + GPU quotas |
| [design_llm_eval_platform.md](./design_llm_eval_platform.md) | Eval-gated continuous delivery | Golden dataset construction, LLM-as-judge orchestration, regression detection with statistical significance, eval-gated CI that blocks deploys, online eval sampling in production |
| [design_synthetic_data_platform.md](./design_synthetic_data_platform.md) | Data quality and diversity at scale | Inverse-frequency diversity sampling (prevents topic collapse), best-of-N preference pairs with margin thresholds, 5-stage quality filter pipeline, SHA-256 content-addressed dataset registry |

---

### Phase 6 — Vertical AI (Domain-Specific Compliance)

Each of these has a hard constraint (legal privilege, HIPAA, financial regulation) that dominates every architecture decision. Read after Phase 5.

| File | Primary Engineering Concern | What It Teaches |
|------|-----------------------------|-----------------|
| [design_legal_ai_platform.md](./design_legal_ai_platform.md) | Per-matter data isolation | Citation-grade retrieval (Bluebook format), per-matter vector DB isolation (regulatory non-negotiable), attorney-client privilege routing, conflict-of-interest checks, compliance audit logs |
| [design_medical_ai_scribe.md](./design_medical_ai_scribe.md) | HIPAA-compliant LLM pipeline | Self-hosted Whisper in VPC (never send PHI audio to external API), SOAP note generation, clinical NLP for ICD-10 code extraction, EHR integration, BAA requirements |
| [design_financial_research_agent.md](./design_financial_research_agent.md) | Citation verification and regulatory isolation | XBRL-first extraction (SEC EDGAR machine-readable data is authoritative), citation verifier with VerificationStatus enum, per-tenant Qdrant collections, MNPI-sensitive document routing |
| [design_ai_companion_platform.md](./design_ai_companion_platform.md) | Prefix-cache economics and memory compression | Separating cacheable system prefix from conversation suffix, 70% cache hit rate = $500K/day savings, LLM-based session memory compression, multi-stage safety pipeline with crisis detection |
| [design_avatar_video_platform.md](./design_avatar_video_platform.md) | Sequential pipeline overlap | TTS→lip-sync→compositor streaming (3s audio chunks enable pipeline overlap), FFmpeg single-pass filter chain, 4-layer deepfake consent defense (FAISS blocklist + cosine match + C2PA + monitoring) |

---

## Cross-Cutting Infrastructure Files

These nine files live in `cross_cutting/` and are referenced by multiple case studies. Read them alongside the phase where they become relevant — not all at the start.

| When to Read | File | What It Covers |
|--------------|------|----------------|
| Phase 1 | [streaming_at_scale.md](./cross_cutting/streaming_at_scale.md) | SSE vs WebSocket vs HTTP/2, half-open connection detection, backpressure on slow clients, partial-token replay, abort handling |
| Phase 1 | [opentelemetry_for_llm_apps.md](./cross_cutting/opentelemetry_for_llm_apps.md) | Trace propagation across LLM/tool/agent spans, `gen_ai.*` semantic conventions, span hierarchy for streaming, cost attribution per request |
| Phase 1 | [multi_region_llm_topology.md](./cross_cutting/multi_region_llm_topology.md) | Active-active topology, anycast + latency routing, KV-cache locality, conversation stickiness, region failover runbooks |
| Phase 2 | [llm_eval_harness_in_production.md](./cross_cutting/llm_eval_harness_in_production.md) | Golden-set construction, LLM-as-judge orchestration, regression detection, eval-gated CI, online eval sampling — the canonical eval pattern referenced by 8 case studies |
| Phase 2 | [tenant_isolation_patterns.md](./cross_cutting/tenant_isolation_patterns.md) | Per-tenant vector DB namespacing vs collections vs clusters, ACL pushdown into retrieval, noisy-neighbor mitigation, cross-tenant injection defenses |
| Phase 3 | [red_team_eval_harness.md](./cross_cutting/red_team_eval_harness.md) | Adversarial dataset construction, jailbreak scoring, prompt-injection corpora, model card generation, continuous red-team CI |
| Phase 4 | [agent_durability_patterns.md](./cross_cutting/agent_durability_patterns.md) | Checkpointing, mid-task interruption + resume, replay determinism, idempotent tool calls, Temporal/LangGraph integration — read before any agent case study |
| Phase 5 | [gpu_pool_economics.md](./cross_cutting/gpu_pool_economics.md) | H100/H200/B200 utilization math (MFU/MBU), spot/preemptible blending, cold-start optimization, prefill/decode disaggregation cost accounting |
| Phase 5 | [training_loop_internals.md](./cross_cutting/training_loop_internals.md) | Forward/backward/optimizer step code, gradient accumulation, FSDP wrap policy, async checkpoint manager, loss-spike detection |

---

## Dependency Map

Some case studies build on patterns established by others. Follow these if you want to study a specific file without reading its full phase first.

```
design_chatgpt
    └─> design_llm_gateway          (routing and rate limiting)
    └─> design_copilot              (latency-critical serving)
    └─> design_gpu_inference_platform (infrastructure layer)

design_rag_pipeline
    └─> design_ai_search_engine     (adds grounding + citations)
    └─> design_notion_ai            (adds tenant isolation)
    └─> design_legal_ai_platform    (adds citation-grade retrieval + compliance)
    └─> design_financial_research_agent (adds XBRL + citation verification)

design_autonomous_swe_agent
    └─> design_browser_research_agent (shares durable agent pattern)
    └─> design_computer_use_agent     (shares sandboxing + VLM grounding)

design_llm_fine_tuning_platform
    └─> design_synthetic_data_platform (feeds training data into fine-tuning)
    └─> design_llm_eval_platform       (evals the output of fine-tuning)
```

---

## Interview Prep Shortcuts

If you are preparing for a specific interview question, jump directly to the case study most likely to be asked.

| Interview Topic | Best Case Study |
|----------------|-----------------|
| "Design a system like ChatGPT" | [design_chatgpt.md](./design_chatgpt.md) |
| "Design a RAG pipeline" | [design_rag_pipeline.md](./design_rag_pipeline.md) |
| "How would you serve LLMs at scale?" | [design_gpu_inference_platform.md](./design_gpu_inference_platform.md) |
| "Design GitHub Copilot" | [design_copilot.md](./design_copilot.md) |
| "Design an AI coding agent (Devin/SWE-agent)" | [design_autonomous_swe_agent.md](./design_autonomous_swe_agent.md) |
| "How would you eval LLM outputs in production?" | [design_llm_eval_platform.md](./design_llm_eval_platform.md) + [cross_cutting/llm_eval_harness_in_production.md](./cross_cutting/llm_eval_harness_in_production.md) |
| "Design a fine-tuning platform" | [design_llm_fine_tuning_platform.md](./design_llm_fine_tuning_platform.md) |
| "How do you handle multi-tenancy in LLM apps?" | [cross_cutting/tenant_isolation_patterns.md](./cross_cutting/tenant_isolation_patterns.md) |
| "Design a content moderation system" | [design_ai_content_moderation.md](./design_ai_content_moderation.md) |
| "Design a legal / medical / financial AI platform" | Phase 6 files |
