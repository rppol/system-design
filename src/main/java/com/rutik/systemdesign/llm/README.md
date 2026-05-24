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

This section is organized into **42 topic directories** plus **13 real-world case studies**, covering the full LLM lifecycle. Nine topics have **deep-dive sub-files** (65 sub-files total, see the Sub-Files Index below):

- How models are built (architecture, tokenization, pre-training, fine-tuning, alignment)
- How to use models effectively (prompting, RAG, reasoning, code generation)
- How to build production LLM systems (inference, optimization, deployment)
- How to build AI agents (tool use, frameworks, multi-agent systems)
- How to keep LLM systems safe (guardrails, safety, evaluation)
- The broader landscape (multimodal, long context, applications, ecosystem)

---

## All Topics

| # | Topic | Key Concepts | Difficulty |
|---|-------|-------------|------------|
| 1 | [Foundations & Architecture](foundations_and_architecture/README.md) | Transformers, self-attention, scaling laws, GPT vs BERT — 3 sub-files: attention derivations, positional encoding, training dynamics | Intermediate |
| 2 | [Tokenization & Embeddings](tokenization_and_embeddings/README.md) | BPE, WordPiece, SentencePiece, vocabulary design | Beginner |
| 3 | [Embeddings & Similarity Search](embeddings_and_similarity_search/README.md) | Sentence embeddings, HNSW, IVF, Matryoshka, FAISS | Intermediate |
| 4 | [Pre-Training](pre_training/README.md) | CLM, MLM, data curation, training dynamics, compute | Advanced |
| 5 | [Training Infrastructure](training_infrastructure/README.md) | Distributed training, tensor/pipeline parallelism, ZeRO, FSDP | Advanced |
| 6 | [Synthetic Data Generation](synthetic_data_generation/README.md) | Self-Instruct, Evol-Instruct, quality filtering, LIMA | Intermediate |
| 7 | [Fine-Tuning](fine_tuning/README.md) | LoRA, QLoRA, PEFT, instruction tuning, domain adaptation — with 5 deep-dive sub-files | Intermediate |
| 8 | [Alignment & RLHF](alignment_and_rlhf/README.md) | RLHF, DPO, Constitutional AI, ORPO, KTO, reward models | Advanced |
| 9 | [Prompt Engineering](prompt_engineering/README.md) | CoT, few-shot, ReAct, structured outputs, system prompts | Beginner |
| 10 | [RAG Fundamentals](rag_fundamentals/README.md) | Chunking, vector DBs, retrieval, reranking, hybrid search — with 4 deep-dive sub-files | Intermediate |
| 11 | [Advanced RAG](advanced_rag/README.md) | Agentic RAG, Graph RAG, multi-modal RAG, evaluation — with 6 deep-dive sub-files | Advanced |
| 12 | [Reasoning Models](reasoning_models/README.md) | o1/o3, test-time compute, MCTS, DeepSeek-R1, PRM/ORM | Advanced |
| 13 | [Code Generation](code_generation/README.md) | FIM, CodeLLaMA, Copilot architecture, SWE-bench, code agents | Intermediate |
| 14 | [Agents & Tool Use](agents_and_tool_use/README.md) | Function calling, ReAct, plan-and-execute, memory, sub-agents, sandboxes, cost — with 15 deep-dive sub-files | Intermediate |
| 15 | [Agentic Frameworks](agentic_frameworks/README.md) | LangChain, LangGraph, LlamaIndex, CrewAI, AutoGen, OpenAI Agents SDK, Anthropic API, PydanticAI, Smolagents, Strands, Mastra, LiteLLM — with 17 deep-dive sub-files | Intermediate |
| 16 | [Multi-Agent Systems](multi_agent_systems/README.md) | Orchestrator, debate, ChatDev, MetaGPT, Swarm/Agents SDK, Magentic-One, A2A protocols — with 6 deep-dive sub-files | Advanced |
| 17 | [Inference & Decoding](inference_and_decoding/README.md) | Sampling, KV cache, speculative decoding, continuous batching | Advanced |
| 18 | [Context Windows & Long Context](context_windows_and_long_context/README.md) | RoPE, ALiBi, YaRN, long context vs RAG, positional encoding | Advanced |
| 19 | [Inference Engines](inference_engines/README.md) | vLLM, TensorRT-LLM, llama.cpp, SGLang, Ollama, TGI | Intermediate |
| 20 | [vLLM Deep Dive](vllm_deep_dive/README.md) | PagedAttention, continuous batching, prefix caching, speculative decoding, quantization, TP/PP, LoRA, structured output | Advanced |
| 21 | [Optimization & Quantization](optimization_and_quantization/README.md) | GPTQ, AWQ, Flash Attention, MoE, pruning, distillation | Advanced |
| 22 | [Deployment & MLOps](deployment_and_mlops/README.md) | Serving, GPU cost, monitoring, routing, edge deployment | Intermediate |
| 23 | [Token Economics & Cost Optimization](token_economics_and_cost_optimization/README.md) | Per-token pricing, prompt caching, batch APIs, self-hosting break-even, budget enforcement | Intermediate |
| 24 | [LLM Routing & Model Selection](llm_routing_and_model_selection/README.md) | Multi-model routing, cascade patterns, confidence thresholds, cost-quality optimization | Intermediate |
| 25 | [Evaluation & Benchmarks](evaluation_and_benchmarks/README.md) | MMLU, HumanEval, LLM-as-judge, Chatbot Arena, RAGAs | Intermediate |
| 26 | [Guardrails & Content Safety](guardrails_and_content_safety/README.md) | NeMo Guardrails, Llama Guard, input/output filters | Intermediate |
| 27 | [Safety & Alignment](safety_and_alignment/README.md) | Jailbreaking, prompt injection, hallucination, bias, red teaming | Intermediate |
| 28 | [Multimodal Models](multimodal_models/README.md) | VLMs, vision encoders, diffusion, speech, video | Advanced |
| 29 | [Small Language Models & Edge AI](small_language_models_and_edge_ai/README.md) | Phi-3/4, LLaMA 3.2 1B/3B, on-device inference, quantization for mobile | Intermediate |
| 30 | [Mixture of Experts](mixture_of_experts/README.md) | MoE architecture, top-k routing, load balancing, Mixtral, DeepSeek-V3 | Advanced |
| 31 | [MCP (Model Context Protocol)](mcp_model_context_protocol/README.md) | MCP servers/clients, resources/tools/prompts, transports, security, registries — with 5 deep-dive sub-files | Intermediate |
| 32 | [AI Applications](ai_applications/README.md) | Healthcare, legal, finance, education, customer support | Beginner |
| 33 | [LLM Ecosystem & Landscape](llm_ecosystem_and_landscape/README.md) | Model families, licensing, cost analysis, timeline | Beginner |
| 34 | [Data Flywheels & Continuous Learning](data_flywheels_and_continuous_learning/README.md) | Production feedback loops, active learning, drift detection, A/B testing for LLMs | Advanced |
| 35 | [LLM Testing Strategies](llm_testing_strategies/README.md) | Golden datasets, LLM-as-judge, regression eval, flakiness detection, CI/CD integration | Intermediate |
| 36 | [Knowledge Distillation & Model Merging](knowledge_distillation_and_model_merging/README.md) | Teacher-student distillation, SLERP/TIES/DARE merging, structured pruning, SparseGPT | Advanced |
| 37 | [LLM Observability & Monitoring](llm_observability_and_monitoring/README.md) | Tracing, quality monitoring, cost attribution, alerting, Langfuse, Arize Phoenix | Intermediate |
| 38 | [LLM Security](llm_security/README.md) | Prompt injection, data extraction, model theft, supply chain, adversarial robustness, red teaming | Advanced |
| 39 | [Agentic Workflow Patterns](agentic_workflow_patterns/README.md) | Prompt chaining, routing, parallelization, orchestrator-workers, evaluator-optimizer (Anthropic taxonomy) | Intermediate |
| 40 | [Coding Agents](coding_agents/README.md) | SWE-agent ACI, OpenHands, Aider, Devin, Cursor Composer, Claude Code, SWE-bench | Advanced |
| 41 | [Voice Agents](voice_agents/README.md) | OpenAI Realtime, Gemini Live, STT→LLM→TTS pipelines, VAD, barge-in, telephony | Advanced |
| 42 | [Browser Agents Deep Dive](browser_agents_deep_dive/README.md) | Browser Use, Stagehand, Playwright MCP, DOM vs vision extraction, WebArena | Advanced |

---

## Sub-Files Index

Topics marked with sub-files above contain individual deep-dive files in addition to their README. Each sub-file follows the full 14-section module template with 15+ interview Q&As.

### Foundations & Architecture (`foundations_and_architecture/`)
| File | Topic |
|------|-------|
| [attention_mechanisms.md](foundations_and_architecture/attention_mechanisms.md) | Flash Attention internals, online softmax, MQA/GQA/MLA, sparse/linear attention, derivations |
| [positional_encoding.md](foundations_and_architecture/positional_encoding.md) | RoPE derivation (complex plane proof), ALiBi, NTK-aware scaling, YaRN, context extension |
| [training_dynamics.md](foundations_and_architecture/training_dynamics.md) | Warmup theory, WSD schedule, loss spikes, BF16/FP16, critical batch size, muP, data mixing |

### Fine-Tuning (`fine_tuning/`)
| File | Topic |
|------|-------|
| [lora.md](fine_tuning/lora.md) | Low-Rank Adaptation — rank decomposition, merging, adapter placement |
| [qlora.md](fine_tuning/qlora.md) | QLoRA — 4-bit NF4 quantization, double quantization, paged optimizers |
| [peft_methods.md](fine_tuning/peft_methods.md) | PEFT survey — LoRA, prefix tuning, prompt tuning, IA3, adapters |
| [instruction_tuning.md](fine_tuning/instruction_tuning.md) | Instruction tuning — FLAN, Alpaca, dataset curation, format design |
| [domain_adaptation.md](fine_tuning/domain_adaptation.md) | Domain adaptation — continued pre-training vs SFT, catastrophic forgetting |

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

### Multi-Agent Systems (`multi_agent_systems/`)
| File | Topic |
|------|-------|
| [orchestrator_worker_pattern.md](multi_agent_systems/orchestrator_worker_pattern.md) | Supervisor decomposition, task ledgers, result aggregation |
| [agent_debate_and_consensus.md](multi_agent_systems/agent_debate_and_consensus.md) | Multi-agent debate, majority vote, judge agent, temperature diversity |
| [chatdev_and_software_simulation.md](multi_agent_systems/chatdev_and_software_simulation.md) | ChatDev roles, MetaGPT SOPs, product→design→code→QA pipeline |
| [openai_swarm_and_handoffs.md](multi_agent_systems/openai_swarm_and_handoffs.md) | Swarm primitives, Agents SDK, handoffs, routines, statelessness |
| [magentic_one_and_autogen_v04.md](multi_agent_systems/magentic_one_and_autogen_v04.md) | Magentic-One orchestrator, GAIA benchmark, AutoGen v0.4 event-driven core |
| [agent_to_agent_protocols.md](multi_agent_systems/agent_to_agent_protocols.md) | A2A protocol, ACP, ANP, agent cards, inter-agent auth |

### MCP (`mcp_model_context_protocol/`)
| File | Topic |
|------|-------|
| [mcp_server_building.md](mcp_model_context_protocol/mcp_server_building.md) | Server skeleton, resources/tools/prompts/sampling, lifecycle, MCP Inspector |
| [mcp_client_patterns.md](mcp_model_context_protocol/mcp_client_patterns.md) | ClientSession, capability negotiation, tool discovery, multi-server |
| [mcp_transports_and_jsonrpc.md](mcp_model_context_protocol/mcp_transports_and_jsonrpc.md) | JSON-RPC 2.0, stdio vs Streamable HTTP vs SSE, connection lifecycle |
| [mcp_security.md](mcp_model_context_protocol/mcp_security.md) | Tool injection, prompt shadowing, confused deputy, OAuth/PKCE |
| [mcp_registries_and_ecosystem.md](mcp_model_context_protocol/mcp_registries_and_ecosystem.md) | Smithery, MCP Hub, official servers, versioning, signed servers |

---

## Recommended Learning Order

### Phase 1 — Foundations (Start Here)
```
Foundations & Architecture  -->  Tokenization & Embeddings  -->  Embeddings & Similarity Search
```
Understand what a transformer is, how text becomes tokens, and how semantic representations enable search.

### Phase 2 — Training
```
Pre-Training  -->  Training Infrastructure  -->  Synthetic Data Generation  -->  Fine-Tuning  -->  Alignment & RLHF
```
Follow the full pipeline from raw data to an aligned, instruction-following model.

### Phase 3 — Using LLMs
```
Prompt Engineering  -->  RAG Fundamentals  -->  Advanced RAG  -->  Reasoning Models  -->  Code Generation
```
Learn how to elicit the best behavior from existing models without retraining.

### Phase 4 — Agents & Frameworks
```
Agents & Tool Use  -->  Agentic Frameworks  -->  Multi-Agent Systems
```
Build autonomous systems that take actions, use tools, and coordinate with other agents.

### Phase 5 — Production
```
Inference & Decoding  -->  Context Windows & Long Context  -->  Inference Engines  -->  vLLM Deep Dive  -->  Optimization & Quantization  -->  Knowledge Distillation & Model Merging  -->  Deployment & MLOps  -->  LLM Observability & Monitoring  -->  Token Economics & Cost Optimization  -->  LLM Routing & Model Selection  -->  Evaluation & Benchmarks  -->  Guardrails & Content Safety
```
Deploy LLMs efficiently, cheaply, and safely. Compress models via distillation and merging; instrument with observability; optimize costs with routing and caching; evaluate before hardening with guardrails.

### Phase 6 — Advanced Topics
```
Safety & Alignment  -->  LLM Security  -->  Multimodal Models  -->  Small Language Models & Edge AI  -->  Mixture of Experts  -->  MCP (Model Context Protocol)  -->  AI Applications  -->  LLM Ecosystem & Landscape  -->  Data Flywheels & Continuous Learning
```
Broaden understanding of safety, security threats and defenses, multimodal capabilities, small models, MoE architecture, tool protocols, domain applications, the full landscape, and continuous improvement.

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
| Distillation & Merging | mergekit, distilabel, Hugging Face PEFT, SparseML, Neural Magic |

---

## Cross-Reference Map

| If you're reading... | Also see... |
|---------------------|-------------|
| Foundations & Architecture | Tokenization, Pre-Training, Scaling Laws |
| RAG Fundamentals | Embeddings & Similarity Search, Advanced RAG, Vector DBs |
| Agents & Tool Use | Prompt Engineering, Agentic Frameworks, Multi-Agent Systems |
| Inference & Decoding | Optimization & Quantization, Inference Engines, KV Cache |
| Fine-Tuning | Alignment & RLHF, Synthetic Data Generation, Training Infrastructure |
| Safety & Alignment | Guardrails & Content Safety, Evaluation & Benchmarks |
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
