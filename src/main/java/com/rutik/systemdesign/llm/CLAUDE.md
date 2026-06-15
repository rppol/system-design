# LLM Section — CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/llm/`
Global conventions (formatting, templates, Q&A rules): see root `CLAUDE.md`.

---

## Module List — 52 Modules

| Module Directory | Topic | Sub-files |
|-----------------|-------|-----------|
| `foundations_and_architecture/` | Transformers, self-attention, scaling laws, GPT vs BERT | attention_mechanisms, positional_encoding, training_dynamics, state_space_models_and_linear_attention |
| `tokenization_and_embeddings/` | BPE, WordPiece, SentencePiece, vocabulary design | — |
| `embeddings_and_similarity_search/` | Sentence embeddings, HNSW, IVF, Matryoshka, FAISS | — |
| `pre_training/` | CLM, MLM, data curation, training dynamics, compute | — |
| `training_infrastructure/` | Distributed training, tensor/pipeline parallelism, ZeRO, FSDP | — |
| `synthetic_data_generation/` | Self-Instruct, Evol-Instruct, quality filtering, LIMA | — |
| `fine_tuning/` | LoRA, QLoRA, PEFT, instruction tuning, domain adaptation | lora, qlora, peft_methods, instruction_tuning, domain_adaptation |
| `alignment_and_rlhf/` | RLHF, DPO, Constitutional AI, ORPO, KTO, reward models | grpo_and_rlvr |
| `prompt_engineering/` | CoT, few-shot, ReAct, structured outputs, system prompts | — |
| `rag_fundamentals/` | Chunking, vector DBs, retrieval, reranking, hybrid search | chunking_strategies, embedding_models, retrieval_methods, reranking |
| `advanced_rag/` | Agentic RAG, Graph RAG, multimodal RAG, evaluation | agentic_rag, corrective_rag, graph_rag, multimodal_rag, query_transformation, self_rag |
| `reasoning_models/` | o1/o3, test-time compute, MCTS, DeepSeek-R1, PRM/ORM | — |
| `code_generation/` | FIM, CodeLLaMA, Copilot architecture, SWE-bench, code agents | — |
| `agents_and_tool_use/` | Function calling, ReAct, plan-and-execute, memory, sub-agents, sandboxes, cost | function_calling_and_tool_design, react_and_reasoning_patterns, plan_and_execute, agent_memory, computer_use_and_browser_agents, agent_evaluation_and_benchmarking, agent_reliability, reflexion_and_self_correction, tree_of_thoughts_for_agents, tool_selection_at_scale, sandboxed_code_execution, subagents_and_delegation, agent_ux_patterns, durable_long_running_agents, agent_cost_and_token_budget |
| `agentic_frameworks/` | LangChain, LangGraph, LlamaIndex, CrewAI, AutoGen, SK, Haystack, DSPy, OpenAI Agents SDK, Anthropic API, PydanticAI, Smolagents, Strands, Mastra, LiteLLM, Google ADK | langchain_and_lcel, langgraph, llamaindex, crewai, autogen, semantic_kernel, haystack, dspy, framework_observability, structured_outputs_and_instructor, openai_agents_sdk, claude_agent_sdk, pydantic_ai, smolagents, strands_aws, mastra_typescript, litellm_routing, google_adk |
| `multi_agent_systems/` | Orchestrator, debate, ChatDev, MetaGPT, Swarm, Magentic-One, A2A | orchestrator_worker_pattern, agent_debate_and_consensus, chatdev_and_software_simulation, openai_swarm_and_handoffs, magentic_one_and_autogen_v04, agent_to_agent_protocols, agentic_commerce_and_payments, multi_agent_security |
| `agentic_workflow_patterns/` | Anthropic taxonomy — chaining, routing, parallelization, orchestrator-workers, evaluator-optimizer | — |
| `coding_agents/` | SWE-agent, OpenHands, Aider, Devin, Cursor, Claude Code, SWE-bench | — |
| `voice_agents/` | Realtime API, Gemini Live, STT→LLM→TTS, VAD, barge-in, telephony | — |
| `browser_agents_deep_dive/` | Browser Use, Stagehand, Playwright MCP, DOM vs vision, WebArena | — |
| `inference_and_decoding/` | Sampling, KV cache, speculative decoding, continuous batching | constrained_decoding_and_structured_outputs, speculative_decoding, sampling_and_decoding_strategies, kv_cache_optimization |
| `inference_engines/` | vLLM, TensorRT-LLM, llama.cpp, SGLang, Ollama, TGI | — |
| `optimization_and_quantization/` | GPTQ, AWQ, Flash Attention, MoE, pruning, distillation | gpu_architecture_and_roofline |
| `deployment_and_mlops/` | Serving, GPU cost, monitoring, routing, edge deployment | — |
| `guardrails_and_content_safety/` | NeMo Guardrails, Llama Guard, input/output filters | — |
| `safety_and_alignment/` | Jailbreaking, prompt injection, hallucination, bias, red teaming | automated_jailbreak_algorithms |
| `mechanistic_interpretability/` | Superposition, sparse autoencoders (SAEs), activation patching, circuit analysis, activation steering, model editing (ROME/MEMIT) | — |
| `evaluation_and_benchmarks/` | MMLU, HumanEval, LLM-as-judge, Chatbot Arena, RAGAs | — |
| `multimodal_models/` | VLMs, vision encoders, diffusion, speech, video | — |
| `context_windows_and_long_context/` | RoPE, ALiBi, YaRN, long context vs RAG | — |
| `ai_applications/` | Healthcare, legal, finance, education, customer support | — |
| `llm_ecosystem_and_landscape/` | Model families, licensing, cost analysis, timeline | — |
| `vllm_deep_dive/` | PagedAttention, continuous batching, prefix caching, TP/PP | — |
| `mcp_model_context_protocol/` | MCP protocol, servers/clients, resources/tools/prompts, JSON-RPC, transports, security, registries | mcp_server_building, mcp_client_patterns, mcp_transports_and_jsonrpc, mcp_security, mcp_registries_and_ecosystem |
| `small_language_models_and_edge_ai/` | Phi-3/4, Gemma, on-device inference, ONNX, Core ML, quantization | — |
| `mixture_of_experts/` | MoE routing, Mixtral 8x7B, DeepSeek-V3, load balancing, sparse activation | — |
| `llm_routing_and_model_selection/` | Multi-model routing, cascade patterns, confidence thresholds, cost optimization | — |
| `token_economics_and_cost_optimization/` | Per-token pricing, prompt caching, batch APIs, self-hosting break-even | — |
| `data_flywheels_and_continuous_learning/` | Production feedback loops, active learning, drift detection, A/B testing | — |
| `llm_testing_strategies/` | Golden datasets, LLM-as-judge, regression eval, flakiness detection, CI/CD integration | — |
| `knowledge_distillation_and_model_merging/` | Teacher-student distillation, SLERP/TIES/DARE merging, structured pruning, SparseGPT | — |
| `llm_observability_and_monitoring/` | Tracing, quality monitoring, cost attribution, alerting, Langfuse, Arize Phoenix | — |
| `llm_security/` | Prompt injection, data extraction, model theft, supply chain, adversarial robustness, red teaming | privacy_and_data_governance |
| `prompt_management_and_promptops/` | Prompt versioning, registries, aliases, eval-gated CI, A/B testing, injection-safe templates | — |
| `context_engineering/` | Context budget allocation, "lost in the middle", KV-cache-aware ordering, compaction, retrieval vs long-context decision matrix | — |
| `llm_caching/` | Exact-match, semantic cache, provider prompt caching, vLLM APC, embedding cache, threshold tuning, invalidation | — |
| `ai_regulations_and_compliance/` | EU AI Act risk tiers, GDPR Art. 22, NIST AI RMF, model cards, bias auditing, DPIA, right to explanation | — |
| `llm_ops_platforms/` | MLflow, W&B, LangSmith, LangFuse, Braintrust, DeepEval, Ragas — experiment tracking, LLM observability, eval CI/CD | — |
| `vision_language_models/` | CLIP dual encoder, LLaVA adapter architecture, BLIP-2 Q-Former, visual grounding, Grounding DINO, VQA benchmarks | — |
| `constitutional_ai/` | SL-CAI critique-revision pipeline, RL-CAI/RLAIF, constitution design, RLAIF vs RLHF, Llama Guard | — |
| `vla_and_robotics_foundation_models/` | Vision-Language-Action models — RT-1/RT-2, OpenVLA, pi-0/pi-0.5, Octo, Gemini Robotics, GR00T, action tokenization, flow-matching action experts | — |
| `diffusion_language_models/` | Non-autoregressive text generation — LLaDA, Mercury, SEDD, D3PM, masked/discrete diffusion, block diffusion | — |

---

## Planned / Missing Topics (not yet created)

The following are gaps for senior AI engineer coverage, identified by the 2026-06-14 full-section gap audit. This is a roadmap, not a queue — pursue only on explicit user request. The 2026-06-14 audit's other 9 items (2 new modules, 4 sub-file slots covering 5 sub-files, 3 content expansions) were built on 2026-06-15; this single row — explicitly out of scope for that build — is the only remaining gap.

### New Sub-Files (within existing topics)

| Parent Module | Sub-File Topic | Priority |
|---------------|-----------------|---------|
| `tokenization_and_embeddings/` | Byte-level / tokenizer-free models — BLT, MEGABYTE | Low |

---

## Case Studies — 29 Total

`case_studies/` directory — all use the 11-section principal template.

Reference: `case_studies/design_gpu_inference_platform.md`
Learning-path index: `case_studies/README.md` (mandatory; update with every new case study)

**Wave 1 (original 13):** design_chatgpt, design_copilot, design_rag_pipeline, design_ai_search_engine, design_llm_gateway, design_ai_coding_assistant, design_customer_support_bot, design_ai_content_moderation, design_llm_fine_tuning_platform, design_notion_ai, design_ai_data_analyst, design_ai_code_review, design_real_time_translation

**Wave 2 (6):** design_gpu_inference_platform, design_autonomous_swe_agent, design_computer_use_agent, design_browser_research_agent, design_legal_ai_platform, design_video_generation_platform

**Wave 3 (5):** design_image_generation_platform, design_llm_eval_platform, design_voice_cloning_tts_platform, design_medical_ai_scribe, design_sales_ai_agent

**Wave 4 (5):** design_ai_companion_platform, design_ai_meeting_assistant, design_financial_research_agent, design_synthetic_data_platform, design_avatar_video_platform

---

## Cross-Cutting Sub-Files — 9 Files

`case_studies/cross_cutting/` — all use the 14-section template:

llm_eval_harness_in_production, opentelemetry_for_llm_apps, multi_region_llm_topology, red_team_eval_harness, gpu_pool_economics, tenant_isolation_patterns, streaming_at_scale, agent_durability_patterns, training_loop_internals

Principal case studies must cross-reference at least 4 of these files via relative links.

---

## Cross-Reference Map

| LLM Module | See Also (other sections) |
|-----------|--------------------------|
| `foundations_and_architecture/` | `../../ml/neural_network_fundamentals/` — MLPs, backprop, activations; foundational math |
| `pre_training/` | `../../ml/data_pipelines_and_processing/` — data curation pipelines |
| `training_infrastructure/` | `../../ml/distributed_training/` — DDP, FSDP, ZeRO |
| `fine_tuning/` | `../../ml/supervised_learning/` — transfer learning theory |
| `multimodal_models/` | `../../ml/computer_vision/` — vision encoders; `../../ml/generative_models/` — diffusion |
| `evaluation_and_benchmarks/` | `../../ml/model_evaluation_and_selection/` — calibration, ROC/PR, cross-validation |
| `deployment_and_mlops/` | `../../devops/ml_platform_and_gpu_infrastructure/` — GPU infra; `../../ml/mlops_and_ci_cd/` |
| `embeddings_and_similarity_search/` | `../../database/vector_databases/` — pgvector, HNSW at DB level |

---

## Adding a New LLM Module

1. Create `<module_name>/README.md` — 14-section template; minimum 15 Q&As (root `CLAUDE.md` hard floor; 15+ for sub-files too)
2. All code in Python with type hints (3.10+ style); no pseudocode — real executable-shaped code
3. Update `README.md` (this section's master index): add row to module table, note sub-file count
4. Update root `README.md` LLM phase table
5. Update root `CLAUDE.md` LLM module table (Sub-files column)

## Adding a New LLM Sub-File (deep-dive within an existing topic)

1. Create `<topic>/<subtopic>.md` — 14-section template; minimum 15 Q&As
2. Update the topic's own `README.md` — add "Deep Dive Files" table at top linking to the new file
3. Update `README.md` — note sub-file count in table row; add file to Sub-Files Index section
4. Update root `CLAUDE.md` LLM module table (Sub-files column)

## Adding a New LLM Case Study

1. Write `case_studies/<name>/README.md` or `case_studies/<name>.md` — 11-section principal template
2. Quality bar: 900–1100 lines; 4+ cross_cutting/ references; executable Python in §4; concrete numbers; broken→fix example; named companies in §6; quantified impact in §9; 10+ Q&As in §11
3. Update `case_studies/README.md` learning path — add to correct phase, update dependency map, add interview prep row
4. Update `README.md` case study count and list
5. Update root `CLAUDE.md` case study list and memory/MEMORY.md if significant

---

## Principal Case Study Quality Bar

- 900–1100 lines per file
- Minimum 4 cross-references to `cross_cutting/` sub-files via relative links
- Real executable-shaped Python code in Section 4 (not pseudocode or prose)
- Concrete numbers in every quantitative claim (no "a few", "some", "significant")
- At least one "show broken code, then fix" example in Section 4
- Section 6 must name actual companies with specific technical details
- Section 9 must have quantified impact ($ lost, users affected, SLA violated)
- Section 11: minimum 10 Q&As (not 8)
