# LLM (Large Language Model) — Complete Educational Guide

A comprehensive, one-stop reference for understanding everything about Large Language Models — from transformer internals and training to production deployment, agents, safety, and the full ecosystem. Designed for engineers, ML practitioners, and anyone preparing for LLM system design interviews.

---

## Intuition

> **One-line analogy**: LLMs are next-token prediction engines at their core — everything else (reasoning, coding, instruction following) is an emergent property of doing that prediction at enormous scale with carefully curated data.

**Mental model**: A language model is trained to answer one question billions of times: "given these tokens, what comes next?" From that simple objective, with enough data and parameters, emergent capabilities arise — code generation, logical reasoning, language translation, summarization. The system design challenge is: how do you train, serve, and evaluate a model at this scale? The answer involves distributed training, quantization, KV caching, RAG, fine-tuning, RLHF, and guardrails — each addressing a different bottleneck or risk.

**Why it matters**: LLMs are now a platform — an API layer that applications are built on top of. Engineers who understand the internals make better product decisions: knowing when to fine-tune vs prompt engineer, when context window limits matter, when RAG helps vs hurts, and how latency/cost tradeoffs work.

**Key insight**: The most important mental model is the inference pipeline: prompt → tokenization → embedding → transformer blocks (attention + FFN, repeated N times) → LM head → softmax → token sampling → repeat. Everything else in LLM engineering is optimizing or extending this loop.

---

## What This Section Covers

This section is organized into **52 topic directories** plus **29 real-world case studies**, covering the full LLM lifecycle. Thirteen topics have **deep-dive sub-files** (82 sub-files total — 73 module sub-files + 9 cross-cutting case study sub-files — see the Sub-Files Index below):

- How models are built (architecture, tokenization, pre-training, fine-tuning, alignment)
- How to use models effectively (prompting, RAG, reasoning, code generation)
- How to build production LLM systems (inference, optimization, deployment)
- How to build AI agents (tool use, frameworks, multi-agent systems)
- How to keep LLM systems safe (guardrails, safety, evaluation)
- The broader landscape (multimodal, long context, applications, ecosystem)

---

## All Topics

Rows are ordered to match the [Recommended Learning Order](#recommended-learning-order). The `#`
column reflects learning sequence, not addition order.

| # | Phase | Topic | Key Concepts | Difficulty |
|---|-------|-------|-------------|------------|
| 1 | 1 — Foundations | [Foundations & Architecture](foundations_and_architecture/README.md) | Transformers, self-attention, scaling laws, GPT vs BERT — 4 sub-files: attention derivations, positional encoding, training dynamics, state-space/linear-attention alternatives | Intermediate |
| 2 | 1 — Foundations | [Tokenization & Embeddings](tokenization_and_embeddings/README.md) | BPE, WordPiece, SentencePiece, vocabulary design — with 1 deep-dive sub-file (byte-level & tokenizer-free) | Beginner |
| 3 | 1 — Foundations | [Embeddings & Similarity Search](embeddings_and_similarity_search/README.md) | Sentence embeddings, HNSW, IVF, Matryoshka, FAISS | Intermediate |
| 4 | 2 — Training | [Pre-Training](pre_training/README.md) | CLM, MLM, data curation, training dynamics, compute | Advanced |
| 5 | 2 — Training | [Training Infrastructure](training_infrastructure/README.md) | Distributed training, tensor/pipeline parallelism, ZeRO, FSDP | Advanced |
| 6 | 2 — Training | [Synthetic Data Generation](synthetic_data_generation/README.md) | Self-Instruct, Evol-Instruct, quality filtering, LIMA | Intermediate |
| 7 | 2 — Training | [Fine-Tuning](fine_tuning/README.md) | LoRA, QLoRA, PEFT, instruction tuning, domain adaptation — with 5 deep-dive sub-files | Intermediate |
| 8 | 2 — Training | [Alignment & RLHF](alignment_and_rlhf/README.md) | RLHF, DPO, Constitutional AI, ORPO, KTO, reward models — with 1 deep-dive sub-file (GRPO & RLVR) | Advanced |
| 9 | 2 — Training | [Constitutional AI](constitutional_ai/README.md) | SL-CAI critique-revision pipeline, RL-CAI/RLAIF, constitution design, RLAIF vs RLHF, Llama Guard | Advanced |
| 10 | 3 — Using LLMs | [Prompt Engineering](prompt_engineering/README.md) | CoT, few-shot, ReAct, structured outputs, system prompts | Beginner |
| 11 | 3 — Using LLMs | [RAG Fundamentals](rag_fundamentals/README.md) | Chunking, vector DBs, retrieval, reranking, hybrid search — with 4 deep-dive sub-files | Intermediate |
| 12 | 3 — Using LLMs | [Advanced RAG](advanced_rag/README.md) | Agentic RAG, Graph RAG, multi-modal RAG, evaluation — with 6 deep-dive sub-files | Advanced |
| 13 | 3 — Using LLMs | [Context Engineering](context_engineering/README.md) | Context budget allocation, "lost in the middle", KV-cache-aware ordering, compaction, retrieval vs long-context decision matrix | Intermediate |
| 14 | 3 — Using LLMs | [Reasoning Models](reasoning_models/README.md) | o1/o3, test-time compute, MCTS, DeepSeek-R1, PRM/ORM | Advanced |
| 15 | 3 — Using LLMs | [Code Generation](code_generation/README.md) | FIM, CodeLLaMA, Copilot architecture, SWE-bench, code agents | Intermediate |
| 16 | 4 — Agents | [Agents & Tool Use](agents_and_tool_use/README.md) | Function calling, ReAct, plan-and-execute, memory, sub-agents, sandboxes, cost — with 15 deep-dive sub-files | Intermediate |
| 17 | 4 — Agents | [Agentic Workflow Patterns](agentic_workflow_patterns/README.md) | Prompt chaining, routing, parallelization, orchestrator-workers, evaluator-optimizer (Anthropic taxonomy) | Intermediate |
| 18 | 4 — Agents | [Agentic Frameworks](agentic_frameworks/README.md) | LangChain, LangGraph, LlamaIndex, CrewAI, AutoGen, OpenAI Agents SDK, Anthropic API, PydanticAI, Smolagents, Strands, Mastra, LiteLLM, Google ADK — with 18 deep-dive sub-files | Intermediate |
| 19 | 4 — Agents | [Multi-Agent Systems](multi_agent_systems/README.md) | Orchestrator, debate, ChatDev, MetaGPT, Swarm/Agents SDK, Magentic-One, A2A protocols, agentic commerce, multi-agent security — with 8 deep-dive sub-files | Advanced |
| 20 | 4 — Agents | [MCP (Model Context Protocol)](mcp_model_context_protocol/README.md) | MCP servers/clients, resources/tools/prompts, transports, security, registries — with 5 deep-dive sub-files | Intermediate |
| 21 | 4 — Agents | [Coding Agents](coding_agents/README.md) | SWE-agent ACI, OpenHands, Aider, Devin, Cursor Composer, Claude Code, SWE-bench | Advanced |
| 22 | 4 — Agents | [Voice Agents](voice_agents/README.md) | OpenAI Realtime, Gemini Live, STT→LLM→TTS pipelines, VAD, barge-in, telephony | Advanced |
| 23 | 4 — Agents | [Browser Agents Deep Dive](browser_agents_deep_dive/README.md) | Browser Use, Stagehand, Playwright MCP, DOM vs vision extraction, WebArena | Advanced |
| 24 | 5a — Serve | [Inference & Decoding](inference_and_decoding/README.md) | Sampling, KV cache, speculative decoding, continuous batching — with 4 deep-dive sub-files (constrained decoding, speculative decoding, sampling & decoding strategies, KV cache optimization) | Advanced |
| 25 | 5a — Serve | [Context Windows & Long Context](context_windows_and_long_context/README.md) | RoPE, ALiBi, YaRN, long context vs RAG, positional encoding | Advanced |
| 26 | 5a — Serve | [Inference Engines](inference_engines/README.md) | vLLM, TensorRT-LLM, llama.cpp, SGLang, Ollama, TGI | Intermediate |
| 27 | 5a — Serve | [vLLM Deep Dive](vllm_deep_dive/README.md) | PagedAttention, continuous batching, prefix caching, speculative decoding, quantization, TP/PP, LoRA, structured output | Advanced |
| 28 | 5a — Serve | [Optimization & Quantization](optimization_and_quantization/README.md) | GPTQ, AWQ, Flash Attention, MoE, pruning, distillation — with 1 deep-dive sub-file (GPU architecture & roofline) | Advanced |
| 29 | 5a — Serve | [Knowledge Distillation & Model Merging](knowledge_distillation_and_model_merging/README.md) | Teacher-student distillation, SLERP/TIES/DARE merging, structured pruning, SparseGPT | Advanced |
| 30 | 5b — Operate | [Deployment & MLOps](deployment_and_mlops/README.md) | Serving, GPU cost, monitoring, routing, edge deployment | Intermediate |
| 31 | 5b — Operate | [LLM Caching](llm_caching/README.md) | Exact-match, semantic cache, provider prompt caching (Anthropic/OpenAI), vLLM APC, embedding cache, threshold tuning, invalidation | Advanced |
| 32 | 5b — Operate | [LLM Observability & Monitoring](llm_observability_and_monitoring/README.md) | Tracing, quality monitoring, cost attribution, alerting, Langfuse, Arize Phoenix | Intermediate |
| 33 | 5b — Operate | [LLMOps Platforms](llm_ops_platforms/README.md) | MLflow, W&B, LangSmith, Langfuse, Braintrust, DeepEval, Ragas — experiment tracking, eval CI/CD | Intermediate |
| 34 | 5b — Operate | [Token Economics & Cost Optimization](token_economics_and_cost_optimization/README.md) | Per-token pricing, prompt caching, batch APIs, self-hosting break-even, budget enforcement | Intermediate |
| 35 | 5b — Operate | [LLM Routing & Model Selection](llm_routing_and_model_selection/README.md) | Multi-model routing, cascade patterns, confidence thresholds, cost-quality optimization | Intermediate |
| 36 | 5b — Operate | [Prompt Management & PromptOps](prompt_management_and_promptops/README.md) | Prompt versioning, registries, aliases, eval-gated CI, A/B testing, prompt injection prevention | Intermediate |
| 37 | 5c — Quality Gate | [Evaluation & Benchmarks](evaluation_and_benchmarks/README.md) | MMLU, HumanEval, LLM-as-judge, Chatbot Arena, RAGAS | Intermediate |
| 38 | 5c — Quality Gate | [LLM Testing Strategies](llm_testing_strategies/README.md) | Golden datasets, LLM-as-judge, regression eval, flakiness detection, CI/CD integration | Intermediate |
| 39 | 5c — Quality Gate | [Guardrails & Content Safety](guardrails_and_content_safety/README.md) | NeMo Guardrails, Llama Guard, input/output filters | Intermediate |
| 40 | 6 — Advanced | [Safety & Alignment](safety_and_alignment/README.md) | Jailbreaking, prompt injection, hallucination, bias, red teaming — with 1 deep-dive sub-file (automated jailbreak algorithms) | Intermediate |
| 41 | 6 — Advanced | [Mechanistic Interpretability](mechanistic_interpretability/README.md) | Superposition, sparse autoencoders (SAEs), activation patching, circuit analysis, activation steering, model editing (ROME/MEMIT) | Advanced |
| 42 | 6 — Advanced | [LLM Security](llm_security/README.md) | Prompt injection, data extraction, model theft, supply chain, adversarial robustness, red teaming — with 1 deep-dive sub-file (privacy & data governance) | Advanced |
| 43 | 6 — Advanced | [AI Regulations & Compliance](ai_regulations_and_compliance/README.md) | EU AI Act risk tiers, GDPR Art. 22, NIST AI RMF, model cards, bias auditing, DPIA, right to explanation | Intermediate |
| 44 | 6 — Advanced | [Multimodal Models](multimodal_models/README.md) | VLMs, vision encoders, diffusion, speech, video | Advanced |
| 45 | 6 — Advanced | [Vision-Language Models](vision_language_models/README.md) | CLIP dual encoder, LLaVA adapter architecture, BLIP-2 Q-Former, visual grounding, Grounding DINO, VQA benchmarks | Advanced |
| 46 | 6 — Advanced | [VLA & Robotics Foundation Models](vla_and_robotics_foundation_models/README.md) | Vision-Language-Action models — RT-1/RT-2, OpenVLA, pi-0/pi-0.5, Octo, Gemini Robotics, GR00T, action tokenization, flow-matching action experts | Advanced |
| 47 | 6 — Advanced | [Small Language Models & Edge AI](small_language_models_and_edge_ai/README.md) | Phi-3/4, LLaMA 3.2 1B/3B, on-device inference, quantization for mobile | Intermediate |
| 48 | 6 — Advanced | [Mixture of Experts](mixture_of_experts/README.md) | MoE architecture, top-k routing, load balancing, Mixtral, DeepSeek-V3 | Advanced |
| 49 | 6 — Advanced | [Diffusion Language Models](diffusion_language_models/README.md) | Non-autoregressive text generation — LLaDA, Mercury, SEDD, D3PM, masked/discrete diffusion, block diffusion, parallel decoding economics | Advanced |
| 50 | 6 — Advanced | [AI Applications](ai_applications/README.md) | Healthcare, legal, finance, education, customer support | Beginner |
| 51 | 6 — Advanced | [LLM Ecosystem & Landscape](llm_ecosystem_and_landscape/README.md) | Model families, licensing, cost analysis, timeline | Beginner |
| 52 | 6 — Advanced | [Data Flywheels & Continuous Learning](data_flywheels_and_continuous_learning/README.md) | Production feedback loops, active learning, drift detection, A/B testing for LLMs | Advanced |

---

## Sub-Files Index

Topics marked with sub-files above contain individual deep-dive files in addition to their README. Each sub-file follows the full 14-section module template with 15+ interview Q&As.

### Foundations & Architecture (`foundations_and_architecture/`)
| File | Topic |
|------|-------|
| [attention_mechanisms.md](foundations_and_architecture/attention_mechanisms.md) | Flash Attention internals, online softmax, MQA/GQA/MLA, sparse/linear attention, derivations |
| [positional_encoding.md](foundations_and_architecture/positional_encoding.md) | RoPE derivation (complex plane proof), ALiBi, NTK-aware scaling, YaRN, context extension |
| [training_dynamics.md](foundations_and_architecture/training_dynamics.md) | Warmup theory, WSD schedule, loss spikes, BF16/FP16, critical batch size, muP, data mixing |
| [state_space_models_and_linear_attention.md](foundations_and_architecture/state_space_models_and_linear_attention.md) | S4/S5, Mamba selective SSM, Mamba-2/SSD, RWKV v4-v7, RetNet, Jamba/Zamba hybrids, Hyena, gated linear attention |

### Tokenization & Embeddings (`tokenization_and_embeddings/`)
| File | Topic |
|------|-------|
| [byte_level_and_tokenizer_free.md](tokenization_and_embeddings/byte_level_and_tokenizer_free.md) | Byte-level & tokenizer-free models — BLT (entropy-based patching), MEGABYTE, ByT5; escaping the tokenizer |

### Fine-Tuning (`fine_tuning/`)
| File | Topic |
|------|-------|
| [lora.md](fine_tuning/lora.md) | Low-Rank Adaptation — rank decomposition, merging, adapter placement |
| [qlora.md](fine_tuning/qlora.md) | QLoRA — 4-bit NF4 quantization, double quantization, paged optimizers |
| [peft_methods.md](fine_tuning/peft_methods.md) | PEFT survey — LoRA, prefix tuning, prompt tuning, IA3, adapters |
| [instruction_tuning.md](fine_tuning/instruction_tuning.md) | Instruction tuning — FLAN, Alpaca, dataset curation, format design |
| [domain_adaptation.md](fine_tuning/domain_adaptation.md) | Domain adaptation — continued pre-training vs SFT, catastrophic forgetting |

### Alignment & RLHF (`alignment_and_rlhf/`)
| File | Topic |
|------|-------|
| [grpo_and_rlvr.md](alignment_and_rlhf/grpo_and_rlvr.md) | GRPO vs PPO, verifiable rewards (RLVR), DeepSeek-R1 pipeline, DAPO/Dr. GRPO/GSPO, verifier hardening |

### RAG Fundamentals (`rag_fundamentals/`)
| File | Topic |
|------|-------|
| [chunking_strategies.md](rag_fundamentals/chunking_strategies.md) | Chunking — fixed-size, sentence, semantic, recursive, late chunking |
| [embedding_models.md](rag_fundamentals/embedding_models.md) | Embedding models — dense vs sparse, BGE, E5, Matryoshka, fine-tuning |
| [retrieval_methods.md](rag_fundamentals/retrieval_methods.md) | Retrieval — BM25, vector search, hybrid, SPLADE, ColBERT |
| [reranking.md](rag_fundamentals/reranking.md) | Reranking — cross-encoders, Cohere Rerank, RRF, LLM-as-reranker |

### Advanced RAG (`advanced_rag/`)
| File | Topic |
|------|-------|
| [agentic_rag.md](advanced_rag/agentic_rag.md) | Agentic RAG — iterative retrieval, tool-augmented RAG, self-reflection |
| [corrective_rag.md](advanced_rag/corrective_rag.md) | CRAG — relevance grading, web fallback, knowledge refinement |
| [graph_rag.md](advanced_rag/graph_rag.md) | Graph RAG — knowledge graphs, community summaries, global search |
| [multimodal_rag.md](advanced_rag/multimodal_rag.md) | Multimodal RAG — image + text retrieval, ColPali, late interaction |
| [query_transformation.md](advanced_rag/query_transformation.md) | Query transformation — HyDE, step-back, multi-query, decomposition |
| [self_rag.md](advanced_rag/self_rag.md) | Self-RAG — retrieve/generate/critique tokens, adaptive retrieval |

### Agents & Tool Use (`agents_and_tool_use/`)
| File | Topic |
|------|-------|
| [function_calling_and_tool_design.md](agents_and_tool_use/function_calling_and_tool_design.md) | Function calling — OpenAI/Anthropic tool use, schema design, parallel calls |
| [react_and_reasoning_patterns.md](agents_and_tool_use/react_and_reasoning_patterns.md) | ReAct — Thought/Action/Observation loop, chain-of-thought integration |
| [plan_and_execute.md](agents_and_tool_use/plan_and_execute.md) | Plan-and-execute — task decomposition, replanning, subgoal tracking |
| [agent_memory.md](agents_and_tool_use/agent_memory.md) | Agent memory — short-term, long-term, episodic, semantic, working memory |
| [computer_use_and_browser_agents.md](agents_and_tool_use/computer_use_and_browser_agents.md) | Computer use — Anthropic CUA, browser automation, screen understanding |
| [agent_evaluation_and_benchmarking.md](agents_and_tool_use/agent_evaluation_and_benchmarking.md) | Agent eval — SWE-bench, GAIA, WebArena, trajectory evaluation |
| [agent_reliability.md](agents_and_tool_use/agent_reliability.md) | Agent reliability — timeout/circuit breaker, retry, checkpointing, dead-loop detection, human handoff |
| [reflexion_and_self_correction.md](agents_and_tool_use/reflexion_and_self_correction.md) | Reflexion verbal RL, Self-Refine, CRITIC, sycophancy pitfalls |
| [tree_of_thoughts_for_agents.md](agents_and_tool_use/tree_of_thoughts_for_agents.md) | ToT for planning — BFS/DFS/beam/MCTS, value functions, cost explosion |
| [tool_selection_at_scale.md](agents_and_tool_use/tool_selection_at_scale.md) | Tool retrieval — RAG-over-tools, hierarchical menus, classifiers for N>50 tools |
| [sandboxed_code_execution.md](agents_and_tool_use/sandboxed_code_execution.md) | E2B, Riza, Daytona, Modal — isolation, network ACL, resource limits |
| [subagents_and_delegation.md](agents_and_tool_use/subagents_and_delegation.md) | Parallel dispatch, context isolation, structured return contracts |
| [agent_ux_patterns.md](agents_and_tool_use/agent_ux_patterns.md) | Streaming, interrupt/resume, approval gates, artifacts, confidence signaling |
| [durable_long_running_agents.md](agents_and_tool_use/durable_long_running_agents.md) | Temporal, Inngest, Restate, LangGraph checkpointing, idempotency |
| [agent_cost_and_token_budget.md](agents_and_tool_use/agent_cost_and_token_budget.md) | Budgets, model cascade, compaction, caching, Batch API |

### Agentic Frameworks (`agentic_frameworks/`)
| File | Topic |
|------|-------|
| [langchain_and_lcel.md](agentic_frameworks/langchain_and_lcel.md) | LangChain — Runnable protocol, LCEL pipe composition, RAG chains, LangSmith |
| [langgraph.md](agentic_frameworks/langgraph.md) | LangGraph — StateGraph, TypedDict state, checkpointing, human-in-the-loop |
| [llamaindex.md](agentic_frameworks/llamaindex.md) | LlamaIndex — node parsers, index types, sentence window, sub-question engine |
| [crewai.md](agentic_frameworks/crewai.md) | CrewAI — agent roles/goals, Task delegation, sequential vs hierarchical process |
| [autogen.md](agentic_frameworks/autogen.md) | AutoGen — ConversableAgent, code execution loop, GroupChat, human_input_mode |
| [semantic_kernel.md](agentic_frameworks/semantic_kernel.md) | Semantic Kernel — Kernel, plugins, planners, Kernel filters, enterprise patterns |
| [haystack.md](agentic_frameworks/haystack.md) | Haystack — typed pipeline DAG, document stores, hybrid retrieval, serialization |
| [dspy.md](agentic_frameworks/dspy.md) | DSPy — signatures, modules, optimizers (BootstrapFewShot, MIPRO), compilation |
| [framework_observability.md](agentic_frameworks/framework_observability.md) | Observability — LangSmith, Langfuse, OpenTelemetry, cost tracking, LLM-as-judge |
| [structured_outputs_and_instructor.md](agentic_frameworks/structured_outputs_and_instructor.md) | Structured outputs — Instructor, Pydantic extraction, native structured outputs |
| [openai_agents_sdk.md](agentic_frameworks/openai_agents_sdk.md) | OpenAI Agents SDK — Agent primitives, Runner, handoffs, guardrails, tracing |
| [claude_agent_sdk.md](agentic_frameworks/claude_agent_sdk.md) | Anthropic API native — tool use loop, parallel tools, subagents, prompt caching |
| [pydantic_ai.md](agentic_frameworks/pydantic_ai.md) | PydanticAI — typed Agent[Deps,Result], DI, structured output, evals |
| [smolagents.md](agentic_frameworks/smolagents.md) | HuggingFace smolagents — CodeAgent vs ToolCallingAgent, secure_executor, MCP |
| [strands_aws.md](agentic_frameworks/strands_aws.md) | AWS Strands — @tool decorator, Bedrock integration, agent_as_tool |
| [mastra_typescript.md](agentic_frameworks/mastra_typescript.md) | Mastra (TS) — workflows, agents, MCP client, evals, Vercel/CF deployment |
| [litellm_routing.md](agentic_frameworks/litellm_routing.md) | LiteLLM — unified routing, fallback, cost tracking, semantic caching, virtual keys |
| [google_adk.md](agentic_frameworks/google_adk.md) | Google Agent Development Kit — LlmAgent, Workflow agents (Sequential/Parallel/Loop), tools, sessions/state, A2A integration, Vertex AI Agent Engine |

### Multi-Agent Systems (`multi_agent_systems/`)
| File | Topic |
|------|-------|
| [orchestrator_worker_pattern.md](multi_agent_systems/orchestrator_worker_pattern.md) | Supervisor decomposition, task ledgers, result aggregation |
| [agent_debate_and_consensus.md](multi_agent_systems/agent_debate_and_consensus.md) | Multi-agent debate, majority vote, judge agent, temperature diversity |
| [chatdev_and_software_simulation.md](multi_agent_systems/chatdev_and_software_simulation.md) | ChatDev roles, MetaGPT SOPs, product→design→code→QA pipeline |
| [openai_swarm_and_handoffs.md](multi_agent_systems/openai_swarm_and_handoffs.md) | Swarm primitives, Agents SDK, handoffs, routines, statelessness |
| [magentic_one_and_autogen_v04.md](multi_agent_systems/magentic_one_and_autogen_v04.md) | Magentic-One orchestrator, GAIA benchmark, AutoGen v0.4 event-driven core |
| [agent_to_agent_protocols.md](multi_agent_systems/agent_to_agent_protocols.md) | A2A protocol, ACP, ANP, agent cards, inter-agent auth |
| [agentic_commerce_and_payments.md](multi_agent_systems/agentic_commerce_and_payments.md) | x402, AP2, ACP, Visa/Mastercard agent commerce — intent-cart-payment mandates, verifiable credentials, spend limits |
| [multi_agent_security.md](multi_agent_systems/multi_agent_security.md) | Cross-agent prompt injection, prompt-infection, collusion, confused-deputy, capability scoping, dual-LLM pattern |

### MCP (`mcp_model_context_protocol/`)
| File | Topic |
|------|-------|
| [mcp_server_building.md](mcp_model_context_protocol/mcp_server_building.md) | Server skeleton, resources/tools/prompts/sampling, lifecycle, MCP Inspector |
| [mcp_client_patterns.md](mcp_model_context_protocol/mcp_client_patterns.md) | ClientSession, capability negotiation, tool discovery, multi-server |
| [mcp_transports_and_jsonrpc.md](mcp_model_context_protocol/mcp_transports_and_jsonrpc.md) | JSON-RPC 2.0, stdio vs Streamable HTTP vs SSE, connection lifecycle |
| [mcp_security.md](mcp_model_context_protocol/mcp_security.md) | Tool injection, prompt shadowing, confused deputy, OAuth/PKCE |
| [mcp_registries_and_ecosystem.md](mcp_model_context_protocol/mcp_registries_and_ecosystem.md) | Smithery, MCP Hub, official servers, versioning, signed servers |

### Inference & Decoding (`inference_and_decoding/`)
| File | Topic |
|------|-------|
| [constrained_decoding_and_structured_outputs.md](inference_and_decoding/constrained_decoding_and_structured_outputs.md) | Logit masking, FSM/CFG compilation, XGrammar/llguidance internals, jump-forward decoding, provider structured outputs |
| [sampling_and_decoding_strategies.md](inference_and_decoding/sampling_and_decoding_strategies.md) | Min-p/typical/eta/epsilon/Mirostat sampling, repetition/presence/frequency penalties, DRY, contrastive search vs. contrastive decoding, XTC, beam search, sampler-ordering gotcha, determinism |
| [kv_cache_optimization.md](inference_and_decoding/kv_cache_optimization.md) | KV cache memory formula and capacity planning, GQA/MQA/MLA impact, KV quantization impact, H2O/SnapKV/StreamingLLM/Scissorhands eviction, cross-layer KV sharing (YOCO/CLA) |
| [speculative_decoding.md](inference_and_decoding/speculative_decoding.md) | Rejection-sampling exactness proof, EAGLE/EAGLE-2/EAGLE-3, Medusa, lookahead/Jacobi decoding, prompt-lookup/ngram decoding, self-speculative/LayerSkip, DeepSeek-V3 MTP, production tuning |

### Optimization & Quantization (`optimization_and_quantization/`)
| File | Topic |
|------|-------|
| [gpu_architecture_and_roofline.md](optimization_and_quantization/gpu_architecture_and_roofline.md) | Memory hierarchy, roofline model, prefill vs decode intensity, FP8/FP4 tensor cores, NVLink/IB topology, MFU/MBU, H100/H200/B200 |

### Safety & Alignment (`safety_and_alignment/`)
| File | Topic |
|------|-------|
| [automated_jailbreak_algorithms.md](safety_and_alignment/automated_jailbreak_algorithms.md) | GCG, AutoDAN/AutoDAN-Turbo, TAP, BEAST, GPTFuzzer, PAP — gradient-based and automated jailbreak search, layered defenses |

### LLM Security (`llm_security/`)
| File | Topic |
|------|-------|
| [privacy_and_data_governance.md](llm_security/privacy_and_data_governance.md) | Memorization & extraction, membership inference, PII pipelines, DP-SGD, machine unlearning, deletion requests |

---

## Recommended Learning Order

### Phase 1 — Foundations (Start Here)
```
Foundations & Architecture  -->  Tokenization & Embeddings  -->  Embeddings & Similarity Search
```
Understand what a transformer is, how text becomes tokens, and how semantic representations enable
search.

### Phase 2 — Training
```
Pre-Training  -->  Training Infrastructure  -->  Synthetic Data Generation  -->  Fine-Tuning
        -->  Alignment & RLHF  -->  Constitutional AI
```
Follow the full pipeline from raw data to an aligned, instruction-following model. Constitutional AI
closes the phase: it is the scalable-oversight alternative to human-label RLHF (critique-revision
SFT, then RLAIF) and builds directly on the reward-model concepts from Alignment & RLHF.

### Phase 3 — Using LLMs
```
Prompt Engineering  -->  RAG Fundamentals  -->  Advanced RAG  -->  Context Engineering  -->  Reasoning Models  -->  Code Generation
```
Learn how to elicit the best behavior from existing models without retraining. Context Engineering
covers context-window budget allocation, compaction, and the retrieval-vs-long-context
decision matrix — essential before building agents or production systems.

### Phase 4 — Agents & Frameworks
```
Agents & Tool Use  -->  Agentic Workflow Patterns  -->  Agentic Frameworks  -->  Multi-Agent Systems
        |
        +--> MCP (Model Context Protocol)
        +--> Coding Agents  -->  Voice Agents  -->  Browser Agents Deep Dive
```
Build autonomous systems that take actions, use tools, and coordinate. Agentic Workflow Patterns
covers the Anthropic taxonomy (chaining, routing, parallelization, orchestrator-workers,
evaluator-optimizer) and should be read immediately after the core Agents & Tool Use module. MCP
sits here because it is an agent tooling protocol, not an advanced research topic. Coding, Voice,
and Browser Agents are specialized agent types best studied after the general agent foundations.

### Phase 5 — Production
Production spans three concerns; read the sub-clusters in order:

**5a — Serve (inference stack)**
```
Inference & Decoding  -->  Context Windows & Long Context  -->  Inference Engines  -->  vLLM Deep Dive
        -->  Optimization & Quantization  -->  Knowledge Distillation & Model Merging
```

**5b — Operate (deployment and cost)**
```
Deployment & MLOps  -->  LLM Caching  -->  LLM Observability & Monitoring  -->  LLMOps Platforms
        -->  Token Economics & Cost Optimization  -->  LLM Routing & Model Selection
        -->  Prompt Management & PromptOps
```
LLM Caching belongs here because caching decisions are tightly coupled with deployment
architecture and cost optimization. LLMOps Platforms follows Observability: once you understand
what to trace and measure, this module compares the platforms (MLflow, W&B, LangSmith, Langfuse,
Braintrust) that operationalize it. Prompt Management closes the loop on the operational
side: versioning, testing, and safe deployment of the prompts that drive your production systems.

**5c — Quality gate**
```
Evaluation & Benchmarks  -->  LLM Testing Strategies  -->  Guardrails & Content Safety
```
LLM Testing Strategies sits between Evaluation and Guardrails: it covers the engineering
side of evaluation (golden datasets, regression suites, CI/CD integration) that bridges benchmark
methodology and production safety hardening.

### Phase 6 — Advanced Topics
```
Safety & Alignment  -->  Mechanistic Interpretability  -->  LLM Security  -->  AI Regulations & Compliance
        -->  Multimodal Models  -->  Vision-Language Models  -->  VLA & Robotics Foundation Models
        -->  Small Language Models & Edge AI  -->  Mixture of Experts  -->  Diffusion Language Models
        -->  AI Applications  -->  LLM Ecosystem & Landscape
        -->  Data Flywheels & Continuous Learning
```
Broaden understanding of safety, security threats and defenses, the regulatory landscape (EU AI
Act, NIST AI RMF), multimodal capabilities, VLM internals (CLIP, LLaVA, BLIP-2), embodied/robotics
foundation models, small models, MoE architecture, non-autoregressive generation, domain
applications, the full landscape, and continuous improvement. Mechanistic Interpretability follows
Safety & Alignment because it provides the white-box tooling (SAEs, activation patching, circuit
tracing) that underlies the scalable-oversight and debugging techniques introduced there. AI
Regulations follows LLM Security because compliance obligations (DPIA, conformity assessment) build
on the threat models covered there; Vision-Language Models deepens the architecture half of
Multimodal Models. VLA & Robotics Foundation Models follows Vision-Language Models directly because
a VLA is architecturally a VLM plus an action head — the perception stack (Grounding DINO/SAM) is
shared. Diffusion Language Models follows Mixture of Experts to group the two
architecture/generation-alternative modules (MoE's sparse activation, diffusion's non-autoregressive
decoding) adjacently, both contrasted against the dense-autoregressive default.

---

## Learning Paths

This section is deliberately exhaustive — 52 modules covering the full LLM lifecycle. That is the right
depth for a reference, but the wrong shape for someone with an interview in two weeks. So there are **two
ways through it**. Pick based on your goal; the browser learning game's **Study** view surfaces both as a
**Full / Interview** toggle (Full is the default).

### Full Path (52 modules)

The complete curriculum, in the order above — see [Recommended Learning Order](#recommended-learning-order).
Use this when you want genuine mastery of the whole field: research frontier (diffusion LMs, VLA/robotics,
mechanistic interpretability), breadth (multimodal, edge SLMs, ecosystem, regulations), and every
specialized agent type. Nothing is dropped.

### Interview-Specific Path (30 modules)

A ruthless cut to what a **senior AI engineer interview** actually probes, curated from this section's own
[Knowledge-Question Map](#knowledge-question-map) and [Study Plan](#study-plan)
(both already encode interview priority). Same learning order, ~40% fewer modules. Each group below says
why it earns interview time.

| Phase | Modules | Why it's tested |
|-------|---------|-----------------|
| Foundations | [Foundations & Architecture](foundations_and_architecture/README.md), [Tokenization & Embeddings](tokenization_and_embeddings/README.md), [Embeddings & Similarity Search](embeddings_and_similarity_search/README.md), [Pre-Training](pre_training/README.md) | "Explain self-attention", scaling laws, BPE, HNSW — the guaranteed openers |
| Training | [Fine-Tuning](fine_tuning/README.md), [Alignment & RLHF](alignment_and_rlhf/README.md) | LoRA/QLoRA and RLHF-vs-DPO-vs-GRPO are the two most-asked training topics |
| Using LLMs | [Prompt Engineering](prompt_engineering/README.md), [RAG Fundamentals](rag_fundamentals/README.md), [Advanced RAG](advanced_rag/README.md), [Context Engineering](context_engineering/README.md), [Reasoning Models](reasoning_models/README.md) | RAG design and "RAG vs fine-tune vs long context" are near-universal; o1/R1 test-time compute is current |
| Agents | [Agents & Tool Use](agents_and_tool_use/README.md), [Agentic Workflow Patterns](agentic_workflow_patterns/README.md), [Multi-Agent Systems](multi_agent_systems/README.md), [MCP](mcp_model_context_protocol/README.md) | Function calling / ReAct mechanics, the Anthropic workflow taxonomy, and MCP are hot |
| Serving | [Inference & Decoding](inference_and_decoding/README.md), [Context Windows & Long Context](context_windows_and_long_context/README.md), [Inference Engines](inference_engines/README.md), [vLLM Deep Dive](vllm_deep_dive/README.md), [Optimization & Quantization](optimization_and_quantization/README.md) | KV cache, speculative decoding, RoPE/YaRN, PagedAttention, GPTQ/AWQ/FP8 — the systems core |
| Operate | [Deployment & MLOps](deployment_and_mlops/README.md), [LLM Caching](llm_caching/README.md), [Token Economics & Cost Optimization](token_economics_and_cost_optimization/README.md), [LLM Routing & Model Selection](llm_routing_and_model_selection/README.md) | "Cut serving cost 10x" is a staple system-design ask |
| Quality & Safety | [Evaluation & Benchmarks](evaluation_and_benchmarks/README.md), [LLM Testing Strategies](llm_testing_strategies/README.md), [Guardrails & Content Safety](guardrails_and_content_safety/README.md), [Safety & Alignment](safety_and_alignment/README.md), [LLM Security](llm_security/README.md) | LLM-as-judge pitfalls, prompt injection defense, hallucination mitigation |
| Architecture | [Mixture of Experts](mixture_of_experts/README.md) | MoE training/serving tradeoffs come up whenever frontier models are discussed |

**Deliberately deferred to the Full Path** (still valuable, just lower interview yield): training
infrastructure, synthetic data, Constitutional AI, code generation, agentic frameworks, coding/voice/
browser agents, distillation & merging, observability, LLMOps platforms, PromptOps, mechanistic
interpretability, AI regulations, multimodal & vision-language models, VLA & robotics, small/edge models,
diffusion LMs, AI applications, ecosystem & landscape, and data flywheels. A niche flagged in an interview
(e.g. "have you looked at Mamba/SSMs?") is a bonus, not a gate — reach for these once the 30 above are solid.

---

## LLM System Design Interview Framework

When asked to design an LLM-powered system, use this 5-step framework:

```
Step 1: Clarify Requirements
  |-- Functional: What does the system do? (QA, chat, search, code gen?)
  |-- Non-functional: Latency targets, scale (QPS), cost budget, accuracy threshold
  |-- Constraints: Online vs. offline? Real-time vs. batch?

Step 2: Choose the Right Model
  |-- Open source (LLaMA, Mistral) vs. API (GPT-4, Claude, Gemini)?
  |-- Size vs. latency tradeoff (7B vs. 70B vs. 405B)?
  |-- Fine-tuning needed, or will prompting suffice?

Step 3: Design the Data Flow
  |-- Input processing: parsing, PII redaction, context injection
  |-- Core LLM call: prompt construction, model invocation
  |-- Output processing: parsing, validation, guardrails

Step 4: Address Production Concerns
  |-- Latency: streaming, caching, speculative decoding
  |-- Scale: batching, load balancing, horizontal scaling
  |-- Cost: quantization, model routing, caching
  |-- Reliability: fallbacks, circuit breakers, monitoring

Step 5: Evaluate and Iterate
  |-- Offline eval: benchmark suite, regression tests
  |-- Online eval: A/B tests, user feedback, LLM-as-judge
  |-- Iteration loop: data flywheel, fine-tuning pipeline
```

---

## Knowledge-Question Map

The highest-frequency LLM *knowledge* questions mapped to the exact file that answers them. For *system design* ("design X") questions, use the interview prep shortcuts table in [case_studies/README.md](case_studies/README.md).

| Interview question | Where the answer lives |
|--------------------|------------------------|
| Explain self-attention; why did transformers win? | [Foundations & Architecture](foundations_and_architecture/README.md), [attention_mechanisms.md](foundations_and_architecture/attention_mechanisms.md) |
| Why is LLM decode memory-bound? Compute a throughput ceiling. | [gpu_architecture_and_roofline.md](optimization_and_quantization/gpu_architecture_and_roofline.md) |
| How does the KV cache work and how big does it get? | [kv_cache_optimization.md](inference_and_decoding/kv_cache_optimization.md), [gpu_architecture_and_roofline.md](optimization_and_quantization/gpu_architecture_and_roofline.md) |
| How do PagedAttention and continuous batching work? | [vLLM Deep Dive](vllm_deep_dive/README.md) |
| RLHF vs DPO vs GRPO — when does each apply? | [Alignment & RLHF](alignment_and_rlhf/README.md), [grpo_and_rlvr.md](alignment_and_rlhf/grpo_and_rlvr.md) |
| How was DeepSeek-R1 trained? What are verifiable rewards? | [grpo_and_rlvr.md](alignment_and_rlhf/grpo_and_rlvr.md), [Reasoning Models](reasoning_models/README.md) |
| LoRA vs QLoRA vs full fine-tune — tradeoffs? | [lora.md](fine_tuning/lora.md), [qlora.md](fine_tuning/qlora.md) |
| RAG vs fine-tuning vs long context — how do you decide? | [Context Engineering](context_engineering/README.md), [RAG Fundamentals](rag_fundamentals/README.md) |
| Walk through chunking, embedding, retrieval, reranking choices | [chunking_strategies.md](rag_fundamentals/chunking_strategies.md), [retrieval_methods.md](rag_fundamentals/retrieval_methods.md), [reranking.md](rag_fundamentals/reranking.md) |
| How do you guarantee the model emits valid JSON? | [constrained_decoding_and_structured_outputs.md](inference_and_decoding/constrained_decoding_and_structured_outputs.md) |
| How do you evaluate an LLM system? LLM-as-judge pitfalls? | [Evaluation & Benchmarks](evaluation_and_benchmarks/README.md), [LLM Testing Strategies](llm_testing_strategies/README.md) |
| How do you evaluate an agent? | [agent_evaluation_and_benchmarking.md](agents_and_tool_use/agent_evaluation_and_benchmarking.md) |
| How does function calling / ReAct actually work? | [function_calling_and_tool_design.md](agents_and_tool_use/function_calling_and_tool_design.md), [react_and_reasoning_patterns.md](agents_and_tool_use/react_and_reasoning_patterns.md) |
| What is MCP and when is it better than native tools? | [MCP](mcp_model_context_protocol/README.md) |
| How do you defend against prompt injection? | [LLM Security](llm_security/README.md), [Safety & Alignment](safety_and_alignment/README.md) |
| Can you delete a user's data from a trained model? | [privacy_and_data_governance.md](llm_security/privacy_and_data_governance.md) |
| How would you cut LLM serving cost by 10x? | [Token Economics](token_economics_and_cost_optimization/README.md), [LLM Caching](llm_caching/README.md), [LLM Routing](llm_routing_and_model_selection/README.md) |
| GPTQ vs AWQ vs FP8 — what does quantization actually buy? | [Optimization & Quantization](optimization_and_quantization/README.md), [gpu_architecture_and_roofline.md](optimization_and_quantization/gpu_architecture_and_roofline.md) |
| How does Mixture of Experts change training and serving? | [Mixture of Experts](mixture_of_experts/README.md) |
| How does speculative decoding work and when does it not help? | [speculative_decoding.md](inference_and_decoding/speculative_decoding.md) |
| What sampler settings should you use, and why can identical temperature/top-p values behave differently across inference engines? | [sampling_and_decoding_strategies.md](inference_and_decoding/sampling_and_decoding_strategies.md) |
| How do you mitigate hallucination in production? | [Safety & Alignment](safety_and_alignment/README.md), [Advanced RAG](advanced_rag/README.md) |
| What is a sparse autoencoder, and how do activation steering and model editing (ROME/MEMIT) actually work? | [Mechanistic Interpretability](mechanistic_interpretability/README.md) |
| How would you debug a hallucination/jailbreak at the activation level, beyond prompt tweaking? | [Mechanistic Interpretability](mechanistic_interpretability/README.md), [Safety & Alignment](safety_and_alignment/README.md) |
| What are scaling laws and why do they matter for budgets? | [Foundations & Architecture](foundations_and_architecture/README.md), [Pre-Training](pre_training/README.md) |
| How does a diffusion language model generate text, and how does it compare to autoregressive decoding? | [Diffusion Language Models](diffusion_language_models/README.md) |
| How do Vision-Language-Action models turn a VLM into a robot control policy? | [VLA & Robotics Foundation Models](vla_and_robotics_foundation_models/README.md) |
| What is Mamba / a state-space model, and how does it avoid the KV-cache growth problem? | [state_space_models_and_linear_attention.md](foundations_and_architecture/state_space_models_and_linear_attention.md) |
| How do GCG and AutoDAN construct adversarial jailbreak prompts, and how do you defend against them? | [automated_jailbreak_algorithms.md](safety_and_alignment/automated_jailbreak_algorithms.md) |
| How do AI agents pay for things autonomously — what protocols enforce spend limits? | [agentic_commerce_and_payments.md](multi_agent_systems/agentic_commerce_and_payments.md) |
| How do you secure a multi-agent system against a compromised or colluding peer agent? | [multi_agent_security.md](multi_agent_systems/multi_agent_security.md) |

---

## Study Plan

An 8-week zero-to-hero plan. Each week pairs modules with must-read deep dives and one case study.

| Week | Focus | Modules | Must-read deep dives | Case study |
|------|-------|---------|---------------------|------------|
| 1 | Foundations (Phase 1) | Foundations & Architecture, Tokenization, Embeddings & Similarity Search | attention_mechanisms, positional_encoding | Skim [Design RAG Pipeline](case_studies/design_rag_pipeline.md) |
| 2 | Training (Phase 2) | Pre-Training, Training Infrastructure, Synthetic Data, Fine-Tuning, Alignment & RLHF, Constitutional AI | lora, qlora, grpo_and_rlvr | [Design LLM Fine-Tuning Platform](case_studies/design_llm_fine_tuning_platform.md) |
| 3 | Using LLMs (Phase 3) | Prompt Engineering, RAG Fundamentals, Advanced RAG, Context Engineering, Reasoning Models, Code Generation | chunking_strategies, retrieval_methods, reranking, query_transformation | [Design AI Search Engine](case_studies/design_ai_search_engine.md) |
| 4 | Agents (Phase 4) | Agents & Tool Use, Agentic Workflow Patterns, Agentic Frameworks, Multi-Agent Systems, MCP | function_calling_and_tool_design, agent_memory, langgraph, mcp_server_building | [Design Autonomous SWE Agent](case_studies/design_autonomous_swe_agent.md) |
| 5 | Serving (Phase 5a) | Inference & Decoding, Context Windows, Inference Engines, vLLM Deep Dive, Optimization & Quantization, Distillation & Merging | gpu_architecture_and_roofline, constrained_decoding_and_structured_outputs, speculative_decoding, sampling_and_decoding_strategies, kv_cache_optimization | [Design GPU Inference Platform](case_studies/design_gpu_inference_platform.md) |
| 6 | Operating (Phases 5b + 5c) | Deployment & MLOps, LLM Caching, Observability, LLMOps Platforms, Token Economics, Routing, PromptOps, Evaluation, Testing, Guardrails | framework_observability | [Design LLM Gateway](case_studies/design_llm_gateway.md), [Design LLM Eval Platform](case_studies/design_llm_eval_platform.md) |
| 7 | Advanced (Phase 6) | Safety, Mechanistic Interpretability, LLM Security, AI Regulations, Multimodal, VLMs, VLA & Robotics, SLMs & Edge, MoE, Diffusion Language Models, Applications, Ecosystem, Data Flywheels | privacy_and_data_governance, mcp_security, automated_jailbreak_algorithms | [Design AI Content Moderation](case_studies/design_ai_content_moderation.md) |
| 8 | Interview drills | Re-read weak areas; all 9 [cross_cutting/](case_studies/cross_cutting/) primitives | — | 3–4 principal case studies via the [interview shortcuts](case_studies/README.md) + mock "design X" sessions using the 5-step framework above |

---

## Key Tradeoffs at a Glance

| Decision | Option A | Option B | Key Factor |
|----------|----------|----------|-----------|
| Model source | API (OpenAI, Anthropic) | Self-hosted open source | Cost vs. control |
| Model size | Larger (better quality) | Smaller (faster/cheaper) | Latency + cost budget |
| Fine-tuning | Full fine-tune | LoRA/PEFT | GPU budget |
| RAG vs. fine-tuning | RAG (dynamic data) | Fine-tuning (style/format) | Data change frequency |
| Context length | Long context | RAG | Latency vs. accuracy |
| Inference engine | vLLM (throughput) | llama.cpp (edge) | Deployment target |
| Quantization | INT4 (2x faster) | FP16 (better quality) | Quality vs. speed |
| Sampling | Temperature > 0 (creative) | Temperature = 0 (deterministic) | Task type |
| Guardrails | Pre-LLM filter | Post-LLM filter | Latency sensitivity |
| Model routing | Single model | Multi-model routing | Cost vs. complexity |
| Tool integration | Native function calling | MCP protocol | Portability vs. simplicity |
| Model size | Cloud LLM (70B+) | Edge SLM (1-7B) | Privacy + latency vs. quality |
| Architecture | Dense transformer | Mixture of Experts | Inference cost vs. memory |
| Improvement | Static model | Data flywheel | Maintenance effort vs. quality gains |
| Security depth | More guardrails (safer) | Fewer guardrails (faster) | Latency vs. safety |
| Observability | Full tracing (comprehensive) | Sampling (low overhead) | Cost vs. visibility |
| Model compression | Distillation (quality) | Quantization (simplicity) | Quality retention vs. effort |
| Caching | Semantic cache (higher hit rate) | Exact-match cache (zero false positives) | Query diversity vs. quality safety |
| Prompt storage | Registry service (hot-swap) | Git files (simple, code-review) | Team size and iteration speed |
| Context filling | Long context (simpler, complete) | RAG + context engineering (cost-efficient) | Corpus size and update frequency |
| Alignment feedback | RLHF (human labels, gold quality) | RLAIF / Constitutional AI (scalable, cheap) | Label budget vs. preference quality |
| Behavioral control | Activation steering / model editing (training-free, fast, reversible) | Fine-tuning / RLHF (durable, broad, expensive) | Latency & cost budget vs. permanence and precision |
| LLMOps tooling | Managed platform (LangSmith, Braintrust) | Self-hosted OSS (MLflow, Langfuse) | Data residency vs. setup effort |
| Compliance posture | Conformity before launch (EU AI Act high-risk) | Ship fast, remediate later | Market access vs. iteration speed |
| Generation paradigm | Autoregressive (one token/step, exact KV-cache) | Diffusion-LM (parallel per-step, fewer steps but O(L^2) each) | Sequence length & latency budget |
| Robot action representation | Discrete action tokens (RT-2 style, reuses LM vocabulary/sampling) | Continuous flow-matching action expert (pi-0 style, smoother control) | Control-frequency requirements vs. architectural simplicity |
| Sequence-mixing layer | Attention (Transformer — quadratic, growing KV-cache, strong recall/ICL) | State-space / linear attention (Mamba, RWKV — O(N), constant-memory inference, recall gap) | Context length & inference-memory budget vs. in-context-learning quality |

---

## Technologies Overview

| Category | Key Tools |
|----------|-----------|
| Model APIs | OpenAI, Anthropic, Google Vertex AI, Cohere, Mistral AI, Together AI |
| Open Models | LLaMA 3, Mistral, Qwen, DeepSeek, Gemma, Phi, Command R |
| Inference Engines | vLLM, TensorRT-LLM, llama.cpp, SGLang, Ollama, TGI (HuggingFace) |
| Vector Databases | Pinecone, Weaviate, Qdrant, Milvus, pgvector, Chroma |
| Orchestration | LangChain, LangGraph, LlamaIndex, CrewAI, AutoGen, Haystack |
| Fine-tuning | Axolotl, Unsloth, HuggingFace PEFT, LLaMA-Factory, torchtune |
| Evaluation | RAGAS, LangSmith, Weights & Biases, TruLens, DeepEval |
| Guardrails | NeMo Guardrails, Llama Guard, Guardrails AI, Rebuff |
| Training infra | DeepSpeed, FSDP, Megatron-LM, Ray Train, SkyPilot |
| Monitoring | LangSmith, Arize Phoenix, Helicone, Langfuse, OpenTelemetry, Weights & Biases |
| Security | Rebuff, Lakera Guard, Prompt Armor, NVIDIA NeMo Guardrails, canary tokens |
| Interpretability | TransformerLens, SAELens, Neuronpedia, NNsight, pyvene, Goodfire/Ember |
| Distillation & Merging | mergekit, distilabel, Hugging Face PEFT, SparseML, Neural Magic |
| LLMOps Platforms | MLflow, Weights & Biases, LangSmith, Langfuse, Braintrust, DeepEval, Ragas |
| Compliance & Governance | Model cards, NIST AI RMF, EU AI Act conformity tooling, Fairlearn, AIF360 |
| Diffusion-LM | LLaDA, Mercury / Mercury Coder, Gemini Diffusion, SEDD, D3PM |
| VLA & Robotics | RT-1/RT-2, OpenVLA, pi-0/pi-0.5, Octo, Gemini Robotics, NVIDIA Isaac GR00T |
| State-Space / Linear Attention | Mamba, Mamba-2 (SSD), RWKV, RetNet, Jamba, Zamba, Hyena |
| Agentic Framework SDKs | Google Agent Development Kit (ADK), Vertex AI Agent Engine |

---

## Cross-Reference Map

| If you're reading... | Also see... |
|---------------------|-------------|
| Foundations & Architecture | Tokenization, Pre-Training, Scaling Laws |
| RAG Fundamentals | Embeddings & Similarity Search, Advanced RAG, Vector DBs |
| Agents & Tool Use | Prompt Engineering, Agentic Frameworks, Multi-Agent Systems |
| Inference & Decoding | Optimization & Quantization, Inference Engines, KV Cache |
| Fine-Tuning | Alignment & RLHF, Synthetic Data Generation, Training Infrastructure |
| Safety & Alignment | Guardrails & Content Safety, Evaluation & Benchmarks, Mechanistic Interpretability |
| Mechanistic Interpretability | Safety & Alignment, LLM Security, Evaluation & Benchmarks, Guardrails & Content Safety |
| Reasoning Models | Prompt Engineering (CoT), Evaluation & Benchmarks |
| Code Generation | Agents & Tool Use, Evaluation (HumanEval, SWE-bench) |
| MCP | Agents & Tool Use, Agentic Frameworks, Function Calling |
| Small Language Models | Optimization & Quantization, Inference Engines, Deployment |
| Mixture of Experts | Foundations & Architecture, Inference Engines, LLM Ecosystem |
| LLM Routing | Deployment & MLOps, Token Economics, Evaluation |
| Token Economics | Deployment & MLOps, LLM Ecosystem, LLM Routing |
| Data Flywheels | Fine-Tuning, Evaluation & Benchmarks, Deployment & MLOps |
| LLM Testing Strategies | Evaluation & Benchmarks, Agents & Tool Use, Data Flywheels, Deployment & MLOps |
| Knowledge Distillation & Model Merging | Optimization & Quantization, Fine-Tuning, Deployment & MLOps, Inference Engines |
| LLM Observability & Monitoring | Deployment & MLOps, Evaluation & Benchmarks, LLM Testing Strategies, Token Economics |
| LLM Security | Safety & Alignment, Guardrails & Content Safety, Prompt Engineering, Deployment & MLOps |
| Prompt Management & PromptOps | LLM Observability & Monitoring, LLM Testing Strategies, Token Economics, Deployment & MLOps |
| Context Engineering | Prompt Engineering, RAG Fundamentals, Agent Memory, Context Windows & Long Context, LLM Caching |
| LLM Caching | Token Economics & Cost Optimization, Inference & Decoding, vLLM Deep Dive, LLM Routing, Deployment & MLOps |
| Constitutional AI | Alignment & RLHF, Guardrails & Content Safety, Safety & Alignment, Synthetic Data Generation |
| Vision-Language Models | Multimodal Models, Foundations & Architecture, Advanced RAG (ColPali), Browser Agents Deep Dive |
| LLMOps Platforms | LLM Observability & Monitoring, LLM Testing Strategies, Evaluation & Benchmarks, Prompt Management & PromptOps |
| AI Regulations & Compliance | Safety & Alignment, LLM Security, Guardrails & Content Safety, AI Applications |
| Diffusion Language Models | Multimodal Models (image-diffusion contrast), Inference & Decoding (parallel decoding economics), Foundations & Architecture (state-space AR alternatives) |
| VLA & Robotics Foundation Models | Vision-Language Models (perception front-end — Grounding DINO/SAM), Small Language Models & Edge AI (on-robot inference), Alignment & RLHF (robotics RL contrast) |
| Multi-Agent Systems | Agentic Frameworks, Agentic Workflow Patterns, LLM Security, Agents & Tool Use |
| Agentic Frameworks | Multi-Agent Systems, MCP, Agents & Tool Use |

---

## Case Studies

| Case Study | Core Concepts | Difficulty |
|------------|---------------|------------|
| [Design ChatGPT](case_studies/design_chatgpt.md) | Multi-turn chat, RLHF, scaling inference | Advanced |
| [Design GitHub Copilot](case_studies/design_copilot.md) | Code completion, FIM, IDE integration, latency | Advanced |
| [Design RAG Pipeline](case_studies/design_rag_pipeline.md) | Chunking, retrieval, reranking, evaluation | Intermediate |
| [Design AI Search Engine](case_studies/design_ai_search_engine.md) | Query understanding, hybrid search, LLM synthesis | Advanced |
| [Design LLM Gateway](case_studies/design_llm_gateway.md) | Routing, rate limiting, caching, observability | Intermediate |
| [Design AI Coding Assistant](case_studies/design_ai_coding_assistant.md) | Code agents, tool use, context gathering | Advanced |
| [Design Customer Support Bot](case_studies/design_customer_support_bot.md) | RAG + guardrails + escalation, evaluation | Intermediate |
| [Design AI Content Moderation](case_studies/design_ai_content_moderation.md) | Multi-tier filtering, toxicity classification, appeals workflow, multi-language | Advanced |
| [Design LLM Fine-Tuning Platform](case_studies/design_llm_fine_tuning_platform.md) | Self-serve fine-tuning, data pipeline, distributed training, model registry | Advanced |
| [Design Notion AI](case_studies/design_notion_ai.md) | Permission-aware RAG, workspace search, AI writing, multi-tenant isolation | Advanced |
| [Design AI Data Analyst](case_studies/design_ai_data_analyst.md) | File upload, auto-EDA, NL-to-SQL, code sandbox, visualization, report synthesis | Intermediate |
| [Design AI Code Review](case_studies/design_ai_code_review.md) | PR diff analysis, security/performance detection, CI/CD gate, learning loop | Advanced |
| [Design Real-Time Translation](case_studies/design_real_time_translation.md) | Sub-1s latency, context preservation, streaming partial translations, confidence scoring | Advanced |
| [Design GPU Inference Platform](case_studies/design_gpu_inference_platform.md) | Multi-tenant GPU serving, LoRA multiplexing, cold-start, MFU/MBU, spot blending | Principal |
| [Design Autonomous SWE Agent](case_studies/design_autonomous_swe_agent.md) | Long-horizon planning, SWE-bench, durable execution, sandboxed code, self-correction | Principal |
| [Design Computer Use Agent](case_studies/design_computer_use_agent.md) | VLM grounding, action confirmation gates, VM sandboxing, trajectory replay | Principal |
| [Design Deep Research Agent](case_studies/design_browser_research_agent.md) | Parallel web crawl, citation grounding, gap detection, iterative deepening | Principal |
| [Design Legal AI Platform](case_studies/design_legal_ai_platform.md) | Citation-grade RAG, matter isolation, privilege classification, conflict check | Principal |
| [Design Video Generation Platform](case_studies/design_video_generation_platform.md) | DiT inference, temporal consistency, async job queue, per-second economics | Principal |
| [Design Image Generation Platform](case_studies/design_image_generation_platform.md) | LoRA adapter hot-swap, CFG batching, safety pipeline, ResolutionRouter economics | Principal |
| [Design LLM Eval Platform](case_studies/design_llm_eval_platform.md) | Golden-set CI, LLM-as-judge, regression detection, Welch t-test, eval-gated deploys | Principal |
| [Design Voice Cloning & TTS Platform](case_studies/design_voice_cloning_tts_platform.md) | TTFB <200ms streaming, consent verification, C2PA watermarking, RTF metrics | Principal |
| [Design Medical AI Scribe](case_studies/design_medical_ai_scribe.md) | HIPAA-compliant STT, PHI boundary enforcement, FHIR write-back, speaker diarization | Principal |
| [Design Sales AI Agent](case_studies/design_sales_ai_agent.md) | Multi-week durable sequences, TCPA/CAN-SPAM compliance, CRM integration, deliverability | Principal |
| [Design AI Companion Platform](case_studies/design_ai_companion_platform.md) | Prefix cache hit rate economics, INT8 inference, episodic memory, minor protection | Principal |
| [Design AI Meeting Assistant](case_studies/design_ai_meeting_assistant.md) | Bot-in-meeting vs local-process, sliding-window Whisper, speaker diarization, GDPR | Principal |
| [Design Financial Research Agent](case_studies/design_financial_research_agent.md) | XBRL-first extraction, citation verification, per-matter isolation, fiscal alignment | Principal |
| [Design Synthetic Data Platform](case_studies/design_synthetic_data_platform.md) | Diversity sampling, best-of-N preference pairs, quality filter pipeline, lineage tracking | Principal |
| [Design Avatar Video Platform](case_studies/design_avatar_video_platform.md) | Sequential pipeline pipelining, lip-sync tier economics, deepfake consent gates, C2PA | Principal |

### Cross-Cutting Case Study Sub-Files (`case_studies/cross_cutting/`)

Shared infrastructure primitives referenced by multiple case studies. Each follows the 14-section module template with 15+ Q&As.

| File | Topic | Referenced by |
|------|-------|---------------|
| [llm_eval_harness_in_production.md](case_studies/cross_cutting/llm_eval_harness_in_production.md) | Golden-set construction, LLM-as-judge, regression detection, eval-gated CI | 8 case studies |
| [opentelemetry_for_llm_apps.md](case_studies/cross_cutting/opentelemetry_for_llm_apps.md) | gen_ai.* semconv, trace propagation, streaming spans, cost attribution | 7 case studies |
| [multi_region_llm_topology.md](case_studies/cross_cutting/multi_region_llm_topology.md) | Active-active, anycast routing, KV-cache locality, region failover | 6 case studies |
| [red_team_eval_harness.md](case_studies/cross_cutting/red_team_eval_harness.md) | Adversarial datasets, jailbreak scoring, prompt injection CI gate | 5 case studies |
| [gpu_pool_economics.md](case_studies/cross_cutting/gpu_pool_economics.md) | MFU/MBU math, H100/H200 specs, spot blending, prefill/decode disaggregation | 5 case studies |
| [tenant_isolation_patterns.md](case_studies/cross_cutting/tenant_isolation_patterns.md) | Per-tenant vector DB, ACL pushdown, noisy-neighbor mitigation | 6 case studies |
| [streaming_at_scale.md](case_studies/cross_cutting/streaming_at_scale.md) | SSE vs WebSocket, half-open detection, backpressure, partial-token replay | 6 case studies |
| [agent_durability_patterns.md](case_studies/cross_cutting/agent_durability_patterns.md) | Checkpointing, idempotent tool calls, Temporal/LangGraph, interrupt-resume | 4 case studies |
| [training_loop_internals.md](case_studies/cross_cutting/training_loop_internals.md) | Forward/backward/optimizer, FSDP, gradient accumulation, loss-spike detection | 4 case studies |
