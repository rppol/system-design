# System Design Master Repository

A comprehensive, one-stop repository for learning **Low-Level Design (LLD)**, **High-Level Design (HLD)**, **Large Language Models (LLM)**, **Machine Learning (ML)**, **Java**, **Spring**, **Python**, **FastAPI**, **Backend Engineering**, **Database Engineering**, **DevOps / Cloud / Platform Engineering**, **Technologies (per-tool deep dives)**, and **CS Fundamentals** — with practical examples, real-world scenarios, and interview preparation material.

---

## Learn it daily — LORA, the `game/` section

Reading 820 files is not the hard part; showing up every day is. **LORA — Learn Often, Recall Always** (by Rutik; the **[Daily Learning Game](https://rppol.github.io/system-design/src/main/java/com/rutik/systemdesign/game)**) turns this repo's ~8,800 interview Q&As (every module README and its deep-dive sub-files; case studies excluded) into a one-click, 5-minute multiple-choice blitz with streaks, XP, and per-section mastery. Pick the coach's suggested topic or choose a section and drill specific sub-topics; skip hard questions to have them taught back at the end. An in-app coach picks the day's topic, and spaced-repetition reviews protect what you already learned. It also ships as a fully-offline sideloadable Android APK — every push to `main` triggers a signed CI build, newest push wins; one tap below downloads the latest build directly (see [`android/README.md`](android/README.md) for install notes).

[![Download the Android APK](https://img.shields.io/badge/Download-Android%20APK-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/rppol/system-design/releases/latest/download/systemdesign-daily.apk) [![Latest build](https://img.shields.io/github/v/release/rppol/system-design?style=for-the-badge&label=Latest&color=1f6feb)](https://github.com/rppol/system-design/releases/latest)

```bash
python3 -m http.server 8901        # from the repo ROOT (stdlib only — no build, no install)
# open http://localhost:8901/src/main/java/com/rutik/systemdesign/game/index.html
```

See [`game/README.md`](src/main/java/com/rutik/systemdesign/game/README.md) for the architecture, feature inventory, and authoring contract.

---

## Repository Structure

### LLD (Low-Level Design) - Design Patterns

#### Creational Patterns
Patterns that deal with object creation mechanisms, trying to create objects in a manner suitable to the situation.

| Pattern | Description | Complexity |
|---------|-------------|------------|
| [Singleton](src/main/java/com/rutik/systemdesign/lld/creational/singleton/singleton.md) | Ensures a class has only one instance | Simple |
| [Factory Method](src/main/java/com/rutik/systemdesign/lld/creational/factory_method/factory_method.md) | Defines an interface for creating objects, letting subclasses decide | Medium |
| [Abstract Factory](src/main/java/com/rutik/systemdesign/lld/creational/abstract_factory/abstract_factory.md) | Creates families of related objects without specifying concrete classes | Complex |
| [Builder](src/main/java/com/rutik/systemdesign/lld/creational/builder/builder.md) | Constructs complex objects step by step | Medium |
| [Prototype](src/main/java/com/rutik/systemdesign/lld/creational/prototype/prototype.md) | Creates new objects by cloning existing ones | Medium |

#### Structural Patterns
Patterns that deal with object composition, creating relationships between objects to form larger structures.

| Pattern | Description | Complexity |
|---------|-------------|------------|
| [Adapter](src/main/java/com/rutik/systemdesign/lld/structural/adapter/adapter.md) | Allows incompatible interfaces to work together | Simple |
| [Bridge](src/main/java/com/rutik/systemdesign/lld/structural/bridge/bridge.md) | Separates abstraction from implementation | Complex |
| [Composite](src/main/java/com/rutik/systemdesign/lld/structural/composite/composite.md) | Composes objects into tree structures | Medium |
| [Decorator](src/main/java/com/rutik/systemdesign/lld/structural/decorator/decorator.md) | Adds behavior to objects dynamically | Medium |
| [Facade](src/main/java/com/rutik/systemdesign/lld/structural/facade/facade.md) | Provides a simplified interface to a complex subsystem | Simple |
| [Flyweight](src/main/java/com/rutik/systemdesign/lld/structural/flyweight/flyweight.md) | Shares common state between multiple objects | Complex |
| [Proxy](src/main/java/com/rutik/systemdesign/lld/structural/proxy/proxy.md) | Provides a surrogate or placeholder for another object | Medium |

#### Behavioral Patterns
Patterns that deal with communication between objects, defining how objects interact and distribute responsibility.

| Pattern | Description | Complexity |
|---------|-------------|------------|
| [Chain of Responsibility](src/main/java/com/rutik/systemdesign/lld/behavioral/chain_of_responsibility/chain_of_responsibility.md) | Passes requests along a chain of handlers | Medium |
| [Command](src/main/java/com/rutik/systemdesign/lld/behavioral/command/command.md) | Encapsulates a request as an object | Medium |
| [Iterator](src/main/java/com/rutik/systemdesign/lld/behavioral/iterator/iterator.md) | Provides sequential access to collection elements | Simple |
| [Mediator](src/main/java/com/rutik/systemdesign/lld/behavioral/mediator/mediator.md) | Reduces chaotic dependencies between objects | Complex |
| [Memento](src/main/java/com/rutik/systemdesign/lld/behavioral/memento/memento.md) | Captures and restores an object's state | Medium |
| [Observer](src/main/java/com/rutik/systemdesign/lld/behavioral/observer/observer.md) | Defines a subscription mechanism to notify objects | Medium |
| [State](src/main/java/com/rutik/systemdesign/lld/behavioral/state/state.md) | Alters object behavior when its state changes | Complex |
| [Strategy](src/main/java/com/rutik/systemdesign/lld/behavioral/strategy/strategy.md) | Defines a family of interchangeable algorithms | Simple |
| [Template Method](src/main/java/com/rutik/systemdesign/lld/behavioral/template_method/template_method.md) | Defines the skeleton of an algorithm | Medium |
| [Visitor](src/main/java/com/rutik/systemdesign/lld/behavioral/visitor/visitor.md) | Separates algorithms from object structures | Complex |
| [Interpreter](src/main/java/com/rutik/systemdesign/lld/behavioral/interpreter/interpreter.md) | Defines a grammar and interprets sentences | Complex |

#### Extras
| Section | Description |
|---------|-------------|
| [SOLID Principles](src/main/java/com/rutik/systemdesign/lld/solid_principles/solid_principles.md) | The 5 foundational principles of OOP |
| [Anti-Patterns](src/main/java/com/rutik/systemdesign/lld/anti_patterns/anti_patterns.md) | Common design mistakes and how to avoid them |
| [Pattern Comparisons](src/main/java/com/rutik/systemdesign/lld/pattern_comparisons/pattern_comparisons.md) | Head-to-head comparisons of similar patterns |
| [Design Principles](src/main/java/com/rutik/systemdesign/lld/design_principles/design_principles.md) | DRY, KISS, YAGNI, Law of Demeter, Composition over Inheritance, Program to Interface |
| [Concurrency Patterns](src/main/java/com/rutik/systemdesign/lld/concurrency_patterns/concurrency_patterns.md) | Thread-Safe Singleton, Producer-Consumer, Read-Write Lock, Thread Pool |
| [System Design Problems](src/main/java/com/rutik/systemdesign/lld/system_design_problems/system_design_problems.md) | Parking Lot, Elevator, Library, Chess, Vending Machine, ATM, Online Booking, Ride Sharing, LRU Cache, Rate Limiter, Tic-Tac-Toe, Splitwise |

---

### HLD (High-Level Design) - System Design Concepts

| Concept | Description |
|---------|-------------|
| [Scalability](src/main/java/com/rutik/systemdesign/hld/scalability/scalability.md) | Horizontal vs Vertical scaling strategies |
| [Load Balancing](src/main/java/com/rutik/systemdesign/hld/load_balancing/load_balancing.md) | Distributing traffic across servers |
| [Caching](src/main/java/com/rutik/systemdesign/hld/caching/caching.md) | Caching strategies and cache invalidation |
| [Database Design](src/main/java/com/rutik/systemdesign/hld/database_design/database_design.md) | SQL vs NoSQL, replication, indexing |
| [Message Queues](src/main/java/com/rutik/systemdesign/hld/message_queues/message_queues.md) | Async communication with Kafka, RabbitMQ |
| [Microservices](src/main/java/com/rutik/systemdesign/hld/microservices/microservices.md) | Microservices architecture and patterns |
| [API Design](src/main/java/com/rutik/systemdesign/hld/api_design/api_design.md) | REST, GraphQL, gRPC best practices |
| [CAP Theorem](src/main/java/com/rutik/systemdesign/hld/cap_theorem/cap_theorem.md) | Consistency, Availability, Partition Tolerance |
| [Consistent Hashing](src/main/java/com/rutik/systemdesign/hld/consistent_hashing/consistent_hashing.md) | Distributed hash ring for load distribution |
| [Rate Limiting](src/main/java/com/rutik/systemdesign/hld/rate_limiting/rate_limiting.md) | Throttling strategies and algorithms |
| [CDN](src/main/java/com/rutik/systemdesign/hld/cdn/cdn.md) | Content Delivery Network architecture |
| [Database Sharding](src/main/java/com/rutik/systemdesign/hld/database_sharding/database_sharding.md) | Partitioning data across databases |
| [Consensus Algorithms](src/main/java/com/rutik/systemdesign/hld/consensus_algorithms/consensus_algorithms.md) | Raft, Paxos, PBFT, etcd/ZooKeeper, split-brain prevention, quorum math |
| [Event Sourcing & CQRS](src/main/java/com/rutik/systemdesign/hld/event_sourcing_cqrs/event_sourcing_cqrs.md) | Event sourcing, CQRS read/write separation, projections, Saga pattern, snapshots |
| [Distributed Transactions](src/main/java/com/rutik/systemdesign/hld/distributed_transactions/distributed_transactions.md) | 2PC, 3PC, Saga, TCC, outbox pattern, idempotency keys |
| [Observability](src/main/java/com/rutik/systemdesign/hld/observability/observability.md) | Metrics, logs, traces, SLI/SLO/error budgets, distributed tracing |
| [Security and Authentication/Authorization](src/main/java/com/rutik/systemdesign/hld/security_and_auth/security_and_auth.md) | AuthN vs AuthZ, OAuth2/OIDC, JWT, mTLS, RBAC vs ABAC, encryption |
| [Resilience Patterns](src/main/java/com/rutik/systemdesign/hld/resilience_patterns/resilience_patterns.md) | Circuit breaker, bulkhead, retries with backoff + jitter, graceful degradation, failover |

---

### LLM (Large Language Models) - AI Systems Guide

A distilled, one-stop reference for everything LLM — from transformer fundamentals to production deployment, agents, safety, and real-world system design.

#### Foundations
| Topic | Key Concepts |
|-------|-------------|
| [Foundations & Architecture](src/main/java/com/rutik/systemdesign/llm/foundations_and_architecture/foundations_and_architecture.md) | Transformers, self-attention, MoE, scaling laws, GPT/LLaMA/DeepSeek — with 4 deep-dive sub-files (Flash Attention, RoPE, training dynamics, state-space/linear-attention alternatives) |
| [Tokenization & Embeddings](src/main/java/com/rutik/systemdesign/llm/tokenization_and_embeddings/tokenization_and_embeddings.md) | BPE, WordPiece, SentencePiece, tiktoken, vocabulary design |
| [Embeddings & Similarity Search](src/main/java/com/rutik/systemdesign/llm/embeddings_and_similarity_search/embeddings_and_similarity_search.md) | Sentence embeddings, contrastive learning, HNSW, FAISS, Matryoshka |
| [FAISS Deep Dive](src/main/java/com/rutik/systemdesign/llm/faiss_deep_dive/faiss_deep_dive.md) | Index-factory grammar, coarse quantizers, PQ/SQ/RaBitQ encodings, training, GPU limits, mmap and on-disk lists, filtered search |

#### Training
| Topic | Key Concepts |
|-------|-------------|
| [Pre-Training](src/main/java/com/rutik/systemdesign/llm/pre_training/pre_training.md) | CLM/MLM/FIM objectives, data curation, Chinchilla scaling laws |
| [Training Infrastructure](src/main/java/com/rutik/systemdesign/llm/training_infrastructure/training_infrastructure.md) | Tensor/pipeline/data parallelism, ZeRO, FSDP, mixed precision |
| [Synthetic Data Generation](src/main/java/com/rutik/systemdesign/llm/synthetic_data_generation/synthetic_data_generation.md) | Self-Instruct, Evol-Instruct, LIMA insight, quality filtering |
| [Fine-Tuning](src/main/java/com/rutik/systemdesign/llm/fine_tuning/fine_tuning.md) | LoRA, QLoRA, PEFT, instruction tuning, domain adaptation |
| [Alignment & RLHF](src/main/java/com/rutik/systemdesign/llm/alignment_and_rlhf/alignment_and_rlhf.md) | RLHF, DPO, Constitutional AI, ORPO, reward hacking |

#### Using LLMs
| Topic | Key Concepts |
|-------|-------------|
| [Prompt Engineering](src/main/java/com/rutik/systemdesign/llm/prompt_engineering/prompt_engineering.md) | CoT, few-shot, ReAct, self-consistency, structured outputs |
| [RAG Fundamentals](src/main/java/com/rutik/systemdesign/llm/rag_fundamentals/rag_fundamentals.md) | Chunking, hybrid retrieval, reranking, RAGAS evaluation |
| [Advanced RAG](src/main/java/com/rutik/systemdesign/llm/advanced_rag/advanced_rag.md) | Graph RAG, Agentic RAG, HyDE, Self-RAG, multi-query expansion |
| [Context Engineering](src/main/java/com/rutik/systemdesign/llm/context_engineering/context_engineering.md) | Context budget allocation, "lost in the middle" fix, KV-cache-aware ordering, compaction, retrieval vs long-context decision matrix |
| [Reasoning Models](src/main/java/com/rutik/systemdesign/llm/reasoning_models/reasoning_models.md) | o1/o3, DeepSeek-R1, test-time compute, PRM/ORM, MCTS |
| [Code Generation](src/main/java/com/rutik/systemdesign/llm/code_generation/code_generation.md) | FIM, Copilot architecture, HumanEval, SWE-bench, StarCoder |

#### Agents & Frameworks
| Topic | Key Concepts |
|-------|-------------|
| [Agents & Tool Use](src/main/java/com/rutik/systemdesign/llm/agents_and_tool_use/agents_and_tool_use.md) | Function calling, ReAct, plan-and-execute, memory systems |
| [Agentic Frameworks](src/main/java/com/rutik/systemdesign/llm/agentic_frameworks/agentic_frameworks.md) | LangChain, LangGraph, LlamaIndex, CrewAI, AutoGen, Haystack |
| [Multi-Agent Systems](src/main/java/com/rutik/systemdesign/llm/multi_agent_systems/multi_agent_systems.md) | Orchestrator, debate, hierarchical, ChatDev, MetaGPT, Swarm |

#### Production
| Topic | Key Concepts |
|-------|-------------|
| [Inference & Decoding](src/main/java/com/rutik/systemdesign/llm/inference_and_decoding/inference_and_decoding.md) | KV cache, PagedAttention, speculative decoding, continuous batching — with 4 deep-dive sub-files (constrained decoding, speculative decoding, sampling & decoding strategies, KV cache optimization) |
| [Inference Engines](src/main/java/com/rutik/systemdesign/llm/inference_engines/inference_engines.md) | vLLM, TensorRT-LLM, llama.cpp, SGLang, Ollama, TGI |
| [vLLM Deep Dive](src/main/java/com/rutik/systemdesign/llm/vllm_deep_dive/vllm_deep_dive.md) | PagedAttention, continuous batching, prefix caching, speculative decoding, quantization, TP/PP, LoRA, structured output |
| [Optimization & Quantization](src/main/java/com/rutik/systemdesign/llm/optimization_and_quantization/optimization_and_quantization.md) | GPTQ, AWQ, Flash Attention, MoE, pruning, distillation |
| [Deployment & MLOps](src/main/java/com/rutik/systemdesign/llm/deployment_and_mlops/deployment_and_mlops.md) | LLM gateway, model routing, semantic caching, observability |
| [Token Economics & Cost Optimization](src/main/java/com/rutik/systemdesign/llm/token_economics_and_cost_optimization/token_economics_and_cost_optimization.md) | Per-token pricing, prompt caching, batch APIs, self-hosting break-even |
| [LLM Routing & Model Selection](src/main/java/com/rutik/systemdesign/llm/llm_routing_and_model_selection/llm_routing_and_model_selection.md) | Multi-model routing, cascade patterns, confidence thresholds |
| [Knowledge Distillation & Model Merging](src/main/java/com/rutik/systemdesign/llm/knowledge_distillation_and_model_merging/knowledge_distillation_and_model_merging.md) | Teacher-student distillation, SLERP/TIES/DARE merging, structured pruning |
| [LLM Observability & Monitoring](src/main/java/com/rutik/systemdesign/llm/llm_observability_and_monitoring/llm_observability_and_monitoring.md) | Tracing, quality monitoring, cost attribution, alerting, Langfuse |
| [LLM Caching](src/main/java/com/rutik/systemdesign/llm/llm_caching/llm_caching.md) | Exact-match, semantic cache, Anthropic/OpenAI prompt caching, vLLM APC, threshold tuning, invalidation |
| [Prompt Management & PromptOps](src/main/java/com/rutik/systemdesign/llm/prompt_management_and_promptops/prompt_management_and_promptops.md) | Prompt versioning, registries, eval-gated CI, A/B testing, aliases, injection-safe templates |

#### Safety & Evaluation
| Topic | Key Concepts |
|-------|-------------|
| [Guardrails & Content Safety](src/main/java/com/rutik/systemdesign/llm/guardrails_and_content_safety/guardrails_and_content_safety.md) | NeMo Guardrails, Llama Guard, PII detection, HIPAA compliance |
| [Safety & Alignment](src/main/java/com/rutik/systemdesign/llm/safety_and_alignment/safety_and_alignment.md) | Jailbreaking, hallucination, bias, prompt injection, red teaming |
| [Mechanistic Interpretability](src/main/java/com/rutik/systemdesign/llm/mechanistic_interpretability/mechanistic_interpretability.md) | Superposition, sparse autoencoders, activation patching, circuit analysis, activation steering, model editing |
| [LLM Security](src/main/java/com/rutik/systemdesign/llm/llm_security/llm_security.md) | Prompt injection, data extraction, model theft, supply chain, adversarial robustness |
| [Evaluation & Benchmarks](src/main/java/com/rutik/systemdesign/llm/evaluation_and_benchmarks/evaluation_and_benchmarks.md) | MMLU, HumanEval, RAGAS, LLM-as-judge, Chatbot Arena |
| [Error Analysis & Eval Design](src/main/java/com/rutik/systemdesign/llm/error_analysis_and_eval_design/error_analysis_and_eval_design.md) | Trace sampling, open/axial coding, failure taxonomies, assertion-first eval design, annotator agreement, judge alignment, slice coverage |

#### Advanced & Landscape
| Topic | Key Concepts |
|-------|-------------|
| [Multimodal Models](src/main/java/com/rutik/systemdesign/llm/multimodal_models/multimodal_models.md) | VLMs, CLIP, LLaVA, diffusion models, Whisper, video models |
| [Context Windows & Long Context](src/main/java/com/rutik/systemdesign/llm/context_windows_and_long_context/context_windows_and_long_context.md) | RoPE, YaRN, ALiBi, "lost in the middle", long context vs RAG |
| [AI Applications](src/main/java/com/rutik/systemdesign/llm/ai_applications/ai_applications.md) | Healthcare, legal, finance, education, customer support, ROI |
| [LLM Ecosystem & Landscape](src/main/java/com/rutik/systemdesign/llm/llm_ecosystem_and_landscape/llm_ecosystem_and_landscape.md) | Model families, licensing, cost analysis, timeline 2017-2025 |
| [Small Language Models & Edge AI](src/main/java/com/rutik/systemdesign/llm/small_language_models_and_edge_ai/small_language_models_and_edge_ai.md) | Phi-3/4, Gemma, on-device inference, quantization for mobile |
| [Mixture of Experts](src/main/java/com/rutik/systemdesign/llm/mixture_of_experts/mixture_of_experts.md) | MoE routing, Mixtral, DeepSeek-V3, load balancing, sparse activation |
| [MCP (Model Context Protocol)](src/main/java/com/rutik/systemdesign/llm/mcp_model_context_protocol/mcp_model_context_protocol.md) | Universal LLM-tool protocol, servers/clients, JSON-RPC, A2A |
| [Data Flywheels & Continuous Learning](src/main/java/com/rutik/systemdesign/llm/data_flywheels_and_continuous_learning/data_flywheels_and_continuous_learning.md) | Production feedback loops, active learning, drift detection, A/B testing |
| [LLM Testing Strategies](src/main/java/com/rutik/systemdesign/llm/llm_testing_strategies/llm_testing_strategies.md) | Golden datasets, LLM-as-judge, regression eval, flakiness detection, CI/CD integration |
| [AI Regulations & Compliance](src/main/java/com/rutik/systemdesign/llm/ai_regulations_and_compliance/ai_regulations_and_compliance.md) | EU AI Act risk tiers, GDPR Art. 22, NIST AI RMF, model cards, bias auditing, DPIA |
| [LLMOps Platforms](src/main/java/com/rutik/systemdesign/llm/llm_ops_platforms/llm_ops_platforms.md) | MLflow, W&B, LangSmith, LangFuse, Braintrust, DeepEval — experiment tracking, observability, eval CI/CD |
| [Vision-Language Models](src/main/java/com/rutik/systemdesign/llm/vision_language_models/vision_language_models.md) | CLIP dual encoder, LLaVA adapter, BLIP-2 Q-Former, visual grounding, Grounding DINO, VQA |
| [VLA & Robotics Foundation Models](src/main/java/com/rutik/systemdesign/llm/vla_and_robotics_foundation_models/vla_and_robotics_foundation_models.md) | Vision-Language-Action models — RT-1/RT-2, OpenVLA, pi-0/pi-0.5, Octo, Gemini Robotics, GR00T, flow-matching action experts |
| [Diffusion Language Models](src/main/java/com/rutik/systemdesign/llm/diffusion_language_models/diffusion_language_models.md) | Non-autoregressive text generation — LLaDA, Mercury, SEDD, D3PM, masked/discrete diffusion, block diffusion |
| [Constitutional AI](src/main/java/com/rutik/systemdesign/llm/constitutional_ai/constitutional_ai.md) | SL-CAI critique-revision, RL-CAI/RLAIF, constitution design, RLAIF vs RLHF tradeoffs |
| [Agentic Workflow Patterns](src/main/java/com/rutik/systemdesign/llm/agentic_workflow_patterns/agentic_workflow_patterns.md) | Anthropic taxonomy — chaining, routing, parallelization, orchestrator-workers, evaluator-optimizer |
| [Coding Agents](src/main/java/com/rutik/systemdesign/llm/coding_agents/coding_agents.md) | SWE-agent ACI, OpenHands, Aider, Devin, Cursor Composer, Claude Code, SWE-bench |
| [Voice Agents](src/main/java/com/rutik/systemdesign/llm/voice_agents/voice_agents.md) | OpenAI Realtime, Gemini Live, STT→LLM→TTS pipelines, VAD, barge-in, telephony |
| [Browser Agents Deep Dive](src/main/java/com/rutik/systemdesign/llm/browser_agents_deep_dive/browser_agents_deep_dive.md) | Browser Use, Stagehand, Playwright MCP, DOM vs vision, WebArena |

#### LLM Case Studies
| Case Study | What It Covers |
|------------|---------------|
| [Design ChatGPT](src/main/java/com/rutik/systemdesign/llm/case_studies/design_chatgpt.md) | Streaming, context management, PagedAttention, tool use, safety |
| [Design GitHub Copilot](src/main/java/com/rutik/systemdesign/llm/case_studies/design_copilot.md) | FIM completions, repo RAG, speculative decoding, license filter |
| [Design RAG Pipeline](src/main/java/com/rutik/systemdesign/llm/case_studies/design_rag_pipeline.md) | Chunking, hybrid retrieval, reranking, multi-tenant, RAGAS |
| [Design AI Search Engine](src/main/java/com/rutik/systemdesign/llm/case_studies/design_ai_search_engine.md) | Web fetch, source ranking, synthesis, caching, freshness |
| [Design LLM Gateway](src/main/java/com/rutik/systemdesign/llm/case_studies/design_llm_gateway.md) | Routing, semantic cache, circuit breaker, budget enforcement |
| [Design AI Coding Assistant](src/main/java/com/rutik/systemdesign/llm/case_studies/design_ai_coding_assistant.md) | Completions, agent loops, sandboxed execution, privacy |
| [Design Customer Support Bot](src/main/java/com/rutik/systemdesign/llm/case_studies/design_customer_support_bot.md) | Intent routing, escalation, tool use, multilingual, CSAT |
| [Design AI Content Moderation](src/main/java/com/rutik/systemdesign/llm/case_studies/design_ai_content_moderation.md) | Multi-tier filtering, toxicity classification, appeals workflow |
| [Design LLM Fine-Tuning Platform](src/main/java/com/rutik/systemdesign/llm/case_studies/design_llm_fine_tuning_platform.md) | Self-serve fine-tuning, data pipeline, distributed training |
| [Design Notion AI](src/main/java/com/rutik/systemdesign/llm/case_studies/design_notion_ai.md) | Permission-aware RAG, workspace search, multi-tenant isolation |
| [Design AI Data Analyst](src/main/java/com/rutik/systemdesign/llm/case_studies/design_ai_data_analyst.md) | File upload, NL-to-SQL, code sandbox, visualization |
| [Design AI Code Review](src/main/java/com/rutik/systemdesign/llm/case_studies/design_ai_code_review.md) | PR diff analysis, security detection, CI/CD gate |
| [Design Real-Time Translation](src/main/java/com/rutik/systemdesign/llm/case_studies/design_real_time_translation.md) | Sub-1s latency, context preservation, streaming translations |
| [Design GPU Inference Platform](src/main/java/com/rutik/systemdesign/llm/case_studies/design_gpu_inference_platform.md) | Multi-tenant GPU serving, LoRA multiplexing, MFU/MBU, spot blending |
| [Design Autonomous SWE Agent](src/main/java/com/rutik/systemdesign/llm/case_studies/design_autonomous_swe_agent.md) | Durable agent execution, SWE-bench, sandboxed code, self-correction loop |
| [Design Computer Use Agent](src/main/java/com/rutik/systemdesign/llm/case_studies/design_computer_use_agent.md) | VLM grounding, action confirmation, VM sandboxing, audit trail |
| [Design Deep Research Agent](src/main/java/com/rutik/systemdesign/llm/case_studies/design_browser_research_agent.md) | Parallel web crawl, citation grounding, gap detection, iterative deepening |
| [Design Legal AI Platform](src/main/java/com/rutik/systemdesign/llm/case_studies/design_legal_ai_platform.md) | Citation-grade RAG, matter isolation, privilege classification, conflict check |
| [Design Video Generation Platform](src/main/java/com/rutik/systemdesign/llm/case_studies/design_video_generation_platform.md) | DiT inference, temporal consistency, async queuing, per-second GPU economics |
| [Design Image Generation Platform](src/main/java/com/rutik/systemdesign/llm/case_studies/design_image_generation_platform.md) | LoRA hot-swap, CFG batching, safety pipeline, resolution-tier routing |
| [Design LLM Eval Platform](src/main/java/com/rutik/systemdesign/llm/case_studies/design_llm_eval_platform.md) | Golden-set CI, LLM-as-judge, Welch t-test regression detection, eval-gated deploys |
| [Design Voice Cloning & TTS Platform](src/main/java/com/rutik/systemdesign/llm/case_studies/design_voice_cloning_tts_platform.md) | Streaming TTS <200ms TTFB, speaker encoder, C2PA watermark, consent token |
| [Design Medical AI Scribe](src/main/java/com/rutik/systemdesign/llm/case_studies/design_medical_ai_scribe.md) | HIPAA-compliant STT in VPC, PHI boundary, SOAP extraction, FHIR write-back |
| [Design Sales AI Agent](src/main/java/com/rutik/systemdesign/llm/case_studies/design_sales_ai_agent.md) | Multi-week durable sequences, TCPA compliance, deliverability management, CRM sync |
| [Design AI Companion Platform](src/main/java/com/rutik/systemdesign/llm/case_studies/design_ai_companion_platform.md) | Prefix-cache economics at 1B msg/day, episodic memory, minor protection gate |
| [Design AI Meeting Assistant](src/main/java/com/rutik/systemdesign/llm/case_studies/design_ai_meeting_assistant.md) | Bot-in-meeting vs local-process, sliding-window Whisper, diarization, GDPR consent |
| [Design Financial Research Agent](src/main/java/com/rutik/systemdesign/llm/case_studies/design_financial_research_agent.md) | XBRL-first extraction, citation verification, per-matter isolation, fiscal alignment |
| [Design Synthetic Data Platform](src/main/java/com/rutik/systemdesign/llm/case_studies/design_synthetic_data_platform.md) | Diversity sampling, best-of-N DPO pairs, quality filter pipeline, dataset lineage |
| [Design Avatar Video Platform](src/main/java/com/rutik/systemdesign/llm/case_studies/design_avatar_video_platform.md) | TTS→lip-sync pipeline pipelining, tier economics, deepfake consent, C2PA signing |

See the [LLM Master Index](src/main/java/com/rutik/systemdesign/llm/README.md) for the full 6-phase learning path and system design interview framework.

---

### Java (Pure Java) - Senior Engineer & Interview Prep Guide

A comprehensive, one-stop reference for mastering **pure Java** — language internals, JVM mechanics, concurrency, collections, performance tuning, and interview patterns. No frameworks, no Spring — core Java only.

#### Phase 1 — Language Core
| Module | Key Concepts |
|--------|-------------|
| [Core Language](src/main/java/com/rutik/systemdesign/java/core_language/core_language.md) | OOP, equals/hashCode contract, inner classes, polymorphism, Object methods |
| [Strings and Text](src/main/java/com/rutik/systemdesign/java/strings_and_text/strings_and_text.md) | String immutability, constant pool, Compact Strings (JEP 254), invokedynamic concat, text blocks |
| [Structured Concurrency & Loom](src/main/java/com/rutik/systemdesign/java/structured_concurrency_and_loom/structured_concurrency_and_loom.md) | Virtual threads, carrier threads, pinning, StructuredTaskScope, ScopedValue — Java 21 GA |
| [Foreign Function & Memory API](src/main/java/com/rutik/systemdesign/java/foreign_function_and_memory_api/foreign_function_and_memory_api.md) | Arena, MemorySegment, Linker downcall/upcall, jextract, replacing Unsafe/JNI — Java 22 GA |
| [Reactive Programming](src/main/java/com/rutik/systemdesign/java/reactive_programming/reactive_programming.md) | Reactor Flux/Mono, cold vs hot, flatMap/concatMap, backpressure, Schedulers, Reactor Context, RxJava 3, StepVerifier |
| [Microservices Patterns](src/main/java/com/rutik/systemdesign/java/microservices_patterns/microservices_patterns.md) | Saga (choreography + orchestration), transactional outbox, idempotency keys, tracing context propagation, strangler fig, bulkhead |
| [gRPC & Protocol Buffers](src/main/java/com/rutik/systemdesign/java/grpc_protobuf/grpc_protobuf.md) | Protobuf wire format + schema evolution, 4 RPC modes, generated stubs, interceptors, deadlines/cancellation, Status model, HTTP/2 |
| [Annotation Processing](src/main/java/com/rutik/systemdesign/java/annotation_processing/annotation_processing.md) | JSR 269 rounds, AbstractProcessor, Filer/Messager, JavaPoet codegen, Lombok AST mutation, MapStruct, compile-time vs runtime |
| [Generics & Type System](src/main/java/com/rutik/systemdesign/java/generics_and_type_system/generics_and_type_system.md) | PECS, type erasure, bridge methods, wildcards, dynamic proxies |
| [Exceptions & I/O](src/main/java/com/rutik/systemdesign/java/exceptions_and_io/exceptions_and_io.md) | Checked/unchecked, try-with-resources, NIO.2, serialization security |
| [JSON Processing with Jackson](src/main/java/com/rutik/systemdesign/java/json_processing_jackson/json_processing_jackson.md) | ObjectMapper thread-safety + reuse, streaming/tree/databind, records, TypeReference, polymorphic-typing CVEs, JavaTimeModule |

#### Phase 2 — Modern Java
| Module | Key Concepts |
|--------|-------------|
| [Java 8 Features](src/main/java/com/rutik/systemdesign/java/java8_features/java8_features.md) | Lambdas, Streams overview, Optional, Collectors, Date/Time API |
| [Java Date/Time (java.time)](src/main/java/com/rutik/systemdesign/java/java_time_datetime/java_time_datetime.md) | Instant vs LocalDateTime, ZoneId/offsets, Duration vs Period, TemporalAdjuster, Clock (testable time), DST gaps/overlaps |
| [Java Streams — Deep Dive](src/main/java/com/rutik/systemdesign/java/java_streams/java_streams.md) | All 20+ ops, lazy eval, flatMap, reduce, Spliterator, parallel rules |
| [Functional Programming](src/main/java/com/rutik/systemdesign/java/functional_programming/functional_programming.md) | Function composition, custom Collectors, parallel streams, immutability |
| [Java 9–21 Features](src/main/java/com/rutik/systemdesign/java/java9_to_21_features/java9_to_21_features.md) | Records, Sealed classes, Virtual threads, Pattern matching, JPMS |
| [JPMS — Java Platform Module System](src/main/java/com/rutik/systemdesign/java/java_platform_module_system/java_platform_module_system.md) | module-info, requires/exports/opens, requires transitive, automatic modules, split packages, services (ServiceLoader), jlink |

#### Phase 3 — JVM Internals
| Module | Key Concepts |
|--------|-------------|
| [JVM Internals](src/main/java/com/rutik/systemdesign/java/jvm_internals/jvm_internals.md) | G1/ZGC algorithms, JIT tiers, Java Memory Model, class loading, object layout |
| [Reference Types & Cleaners](src/main/java/com/rutik/systemdesign/java/reference_types_and_cleaners/reference_types_and_cleaners.md) | Strong/Soft/Weak/Phantom, ReferenceQueue, WeakHashMap, Cleaner vs finalize, ThreadLocal/ClassLoader leaks |
| [Bytecode & Class-File Format](src/main/java/com/rutik/systemdesign/java/bytecode_and_classfile/bytecode_and_classfile.md) | .class structure, constant pool, opcode families, invokedynamic, javap, ASM/Byte Buddy, java agents & Instrumentation |
| [GraalVM Native Image](src/main/java/com/rutik/systemdesign/java/graalvm_native_image/graalvm_native_image.md) | AOT native-image, closed-world reachability, reflection/resource metadata, build-time vs runtime init, startup vs throughput |
| [Java Memory Model](src/main/java/com/rutik/systemdesign/java/java_memory_model/java_memory_model.md) | Happens-before rules, memory barriers, volatile semantics, safe publication, double-checked locking without volatile |

#### Phase 4 — Concurrency + Collections (Most Tested)
| Module | Key Concepts |
|--------|-------------|
| [Concurrency](src/main/java/com/rutik/systemdesign/java/concurrency/concurrency.md) | synchronized, volatile, ThreadPoolExecutor, CompletableFuture, CAS, virtual threads |
| [Collections Internals](src/main/java/com/rutik/systemdesign/java/collections_internals/collections_internals.md) | HashMap treeification, ArrayList growth, fail-fast, ConcurrentHashMap |

#### Phase 5 — Performance & Integration
| Module | Key Concepts |
|--------|-------------|
| [Performance & Tuning](src/main/java/com/rutik/systemdesign/java/performance_and_tuning/performance_and_tuning.md) | GC tuning, JMH, heap/thread dumps, false sharing, async-profiler |
| [Networking & HTTP Client](src/main/java/com/rutik/systemdesign/java/networking_and_http_client/networking_and_http_client.md) | HttpClient (Java 11+), NIO Selector, Reactor pattern, HTTP/2, connection pooling |
| [JDBC & Database](src/main/java/com/rutik/systemdesign/java/jdbc_and_database/jdbc_and_database.md) | PreparedStatement, HikariCP, transaction isolation, batch inserts, ResultSet streaming |
| [Security & Cryptography](src/main/java/com/rutik/systemdesign/java/security_and_cryptography/security_and_cryptography.md) | JCA/JCE, MessageDigest/Cipher/KeyStore/SecureRandom, AES-GCM, TLS/SSLEngine handshake, password hashing, JAAS |

#### Phase 6 — Interview Consolidation
| Module | Key Concepts |
|--------|-------------|
| [Java Interview Patterns](src/main/java/com/rutik/systemdesign/java/java_interview_patterns/java_interview_patterns.md) | Immutable class, Builder, equals contract, Integer cache, enum singleton |
| [Design Patterns in Java](src/main/java/com/rutik/systemdesign/java/design_patterns_in_java/design_patterns_in_java.md) | GoF patterns with Java idioms, concurrency patterns, anti-patterns, Effective Java references |
| [Testing with JUnit & Mockito](src/main/java/com/rutik/systemdesign/java/testing_junit_mockito/testing_junit_mockito.md) | JUnit 5 lifecycle, Mockito argument matchers, test doubles, AAA pattern, parameterized tests |
| [Logging](src/main/java/com/rutik/systemdesign/java/logging/logging.md) | SLF4J facade, Logback/Log4j2, parameterized logging, MDC across threads/virtual-threads, async appenders, Log4Shell |
| [Build Tools — Maven & Gradle](src/main/java/com/rutik/systemdesign/java/build_tools_maven_gradle/build_tools_maven_gradle.md) | Maven lifecycle/dependency mediation/BOM/shade, Gradle task graph/build cache/version catalogs, dependency hell |

#### Java Case Studies
| Case Study | Core Concepts |
|------------|--------------|
| [Design Connection Pool](src/main/java/com/rutik/systemdesign/java/case_studies/design_connection_pool.md) | BlockingQueue, AtomicInteger, timeouts, health checks |
| [Design Rate Limiter](src/main/java/com/rutik/systemdesign/java/case_studies/design_rate_limiter_java.md) | AtomicLong, CAS, token bucket, ScheduledExecutor |
| [Design Event Bus](src/main/java/com/rutik/systemdesign/java/case_studies/design_event_bus.md) | WeakReference, CopyOnWriteArrayList, CompletableFuture, generics |
| [Design LRU Cache](src/main/java/com/rutik/systemdesign/java/case_studies/design_lru_cache_java.md) | LinkedHashMap, ConcurrentHashMap, SoftReference, ReentrantLock |
| [Design Thread Pool](src/main/java/com/rutik/systemdesign/java/case_studies/design_thread_pool_java.md) | ThreadPoolExecutor internals, ctl AtomicInteger, Worker extends AQS, queue growth model |
| [Design DI Container](src/main/java/com/rutik/systemdesign/java/case_studies/design_di_container_java.md) | Reflection, Binding record, LinkedHashSet cycle detection, @Inject/@PostConstruct |
| [Design Circuit Breaker](src/main/java/com/rutik/systemdesign/java/case_studies/design_circuit_breaker_java.md) | CountBasedSlidingWindow, CAS state machine, HALF_OPEN probes, transitionTo() |
| [Design Snowflake ID Generator](src/main/java/com/rutik/systemdesign/java/case_studies/design_snowflake_id_generator_java.md) | 41+10+12 bit packing, custom epoch, clock-skew wait, virtual thread pinning |

See the [Java Master Index](src/main/java/com/rutik/systemdesign/java/README.md) for the full 6-phase learning path, Java version matrix, and cross-reference map.

---

### Spring Framework — Senior Engineer & Interview Prep Guide

A comprehensive guide to mastering **Spring Framework internals**, Spring Boot, Spring Security, Spring Data, Spring Cloud, and production patterns — targeting senior engineers and interview preparation.

#### Phase 1 — Core Container
| Module | Key Concepts |
|--------|-------------|
| [IoC Container](src/main/java/com/rutik/systemdesign/spring/ioc_container/ioc_container.md) | BeanFactory vs. ApplicationContext, bean scopes, component scan, BeanPostProcessor |
| [Bean Lifecycle](src/main/java/com/rutik/systemdesign/spring/bean_lifecycle/bean_lifecycle.md) | Instantiation, populate properties, BeanPostProcessor, init/destroy, @PostConstruct/@PreDestroy |
| [Dependency Injection](src/main/java/com/rutik/systemdesign/spring/dependency_injection/dependency_injection.md) | Constructor vs. field vs. setter injection, circular deps, @Qualifier, @Primary |
| [Spring Configuration](src/main/java/com/rutik/systemdesign/spring/spring_configuration/spring_configuration.md) | @Configuration, @Bean, @ComponentScan, @PropertySource, @Profile, @Conditional |

#### Phase 2 — Proxies & AOP
| Module | Key Concepts |
|--------|-------------|
| [Spring Proxies](src/main/java/com/rutik/systemdesign/spring/spring_proxies/spring_proxies.md) | JDK dynamic proxy, CGLIB, proxyTargetClass, self-invocation bypass, proxy ordering |
| [Spring AOP](src/main/java/com/rutik/systemdesign/spring/spring_aop/spring_aop.md) | Pointcut expressions, advice types (@Around, @Before, @After), AspectJ weaving, proxy limits |

#### Phase 3 — Spring Boot
| Module | Key Concepts |
|--------|-------------|
| [Spring Boot Auto-Configuration](src/main/java/com/rutik/systemdesign/spring/spring_boot_autoconfiguration/spring_boot_autoconfiguration.md) | @EnableAutoConfiguration, AutoConfiguration.imports, @Conditional*, custom starters |
| [Spring Boot Configuration](src/main/java/com/rutik/systemdesign/spring/spring_boot_configuration/spring_boot_configuration.md) | @ConfigurationProperties, relaxed binding, config server integration, secrets management |
| [Spring Boot Actuator](src/main/java/com/rutik/systemdesign/spring/spring_boot_actuator/spring_boot_actuator.md) | Health indicators, Micrometer metrics, custom endpoints, Prometheus integration |

#### Phase 4 — Spring Web
| Module | Key Concepts |
|--------|-------------|
| [Spring MVC Architecture](src/main/java/com/rutik/systemdesign/spring/spring_mvc_architecture/spring_mvc_architecture.md) | DispatcherServlet, HandlerMapping, HandlerAdapter, ViewResolver, message converters |
| [Request Handling](src/main/java/com/rutik/systemdesign/spring/request_handling/request_handling.md) | @RequestMapping, argument resolvers, @ControllerAdvice, exception handling, content negotiation |
| [Filters & Interceptors](src/main/java/com/rutik/systemdesign/spring/filters_and_interceptors/filters_and_interceptors.md) | Servlet Filter vs. HandlerInterceptor, filter order, OncePerRequestFilter |
| [Spring WebFlux](src/main/java/com/rutik/systemdesign/spring/spring_webflux/spring_webflux.md) | Project Reactor, Mono/Flux, Netty event loop, RouterFunction, backpressure, WebClient |
| [Spring HATEOAS & REST Maturity](src/main/java/com/rutik/systemdesign/spring/spring_hateoas_rest_maturity/spring_hateoas_rest_maturity.md) | Richardson Maturity Model L0-L3, Spring HATEOAS (EntityModel/Link/assemblers), HAL/HAL-FORMS, @HttpExchange/RestClient, ProblemDetail |
| [Spring gRPC](src/main/java/com/rutik/systemdesign/spring/spring_grpc/spring_grpc.md) | @GrpcService beans, server/channel autoconfig, Server/ClientInterceptor, Status-exception mapping, deadlines, streaming, security + tracing |
| [Spring HTTP Clients](src/main/java/com/rutik/systemdesign/spring/spring_http_clients/spring_http_clients.md) | RestTemplate vs WebClient vs RestClient (6.1) vs @HttpExchange, connection pooling, timeouts, error handling, MockRestServiceServer |

#### Phase 5 — Spring Data & Transactions
| Module | Key Concepts |
|--------|-------------|
| [Spring Data JPA](src/main/java/com/rutik/systemdesign/spring/spring_data_jpa/spring_data_jpa.md) | JpaRepository, query derivation, JPQL, native queries, projections, N+1 problem |
| [Spring Transactions](src/main/java/com/rutik/systemdesign/spring/spring_transactions/spring_transactions.md) | Propagation types, isolation levels, @Transactional internals, proxy limits, distributed TX |
| [Spring Caching](src/main/java/com/rutik/systemdesign/spring/spring_caching/spring_caching.md) | @Cacheable, @CacheEvict, @CachePut, CacheManager, Redis integration, cache stampede |
| [Spring Data NoSQL](src/main/java/com/rutik/systemdesign/spring/spring_data_nosql/spring_data_nosql.md) | Spring Data MongoDB (MongoTemplate, aggregation, transactions) + Redis (RedisTemplate, @RedisHash, pub/sub) + reactive repositories |
| [Database Migrations](src/main/java/com/rutik/systemdesign/spring/database_migrations/database_migrations.md) | Flyway (versioned/repeatable, checksums) + Liquibase (changesets, rollback), expand-contract zero-downtime, Boot integration, Testcontainers |

#### Phase 6 — Spring Security
| Module | Key Concepts |
|--------|-------------|
| [Spring Security Architecture](src/main/java/com/rutik/systemdesign/spring/spring_security_architecture/spring_security_architecture.md) | SecurityFilterChain, AuthenticationManager, SecurityContext, CSRF, CORS, method security |
| [Spring Security JWT & OAuth2](src/main/java/com/rutik/systemdesign/spring/spring_security_jwt_oauth/spring_security_jwt_oauth.md) | JWT validation, OAuth2 resource server, PKCE, Spring Authorization Server, token introspection |
| [Spring Session](src/main/java/com/rutik/systemdesign/spring/spring_session/spring_session.md) | SessionRepositoryFilter, Redis/JDBC/Hazelcast backends, session fixation, concurrent-session control, WebSession, JWT vs stateful tradeoff |

#### Phase 7 — Spring Cloud & Messaging
| Module | Key Concepts |
|--------|-------------|
| [Spring Cloud Config](src/main/java/com/rutik/systemdesign/spring/spring_cloud_config/spring_cloud_config.md) | Config server, @RefreshScope, Vault integration, config encryption, Bootstrap context |
| [Spring Cloud Patterns](src/main/java/com/rutik/systemdesign/spring/spring_cloud_patterns/spring_cloud_patterns.md) | Eureka, Resilience4j (circuit breaker, retry, bulkhead), Spring Cloud Gateway, load balancer |
| [Spring Messaging](src/main/java/com/rutik/systemdesign/spring/spring_messaging/spring_messaging.md) | @KafkaListener, @RabbitListener, message converters, dead-letter queues, idempotency |
| [Spring WebSocket & STOMP](src/main/java/com/rutik/systemdesign/spring/spring_websocket_stomp/spring_websocket_stomp.md) | WebSocket handshake, STOMP over WebSocket, simple vs external broker relay, SockJS, per-user destinations, scaling |

#### Phase 8 — Testing & Production
| Module | Key Concepts |
|--------|-------------|
| [Spring Testing](src/main/java/com/rutik/systemdesign/spring/spring_testing/spring_testing.md) | @SpringBootTest, @WebMvcTest, @DataJpaTest, MockMvc, WebTestClient, Testcontainers, @MockBean |
| [Spring Performance](src/main/java/com/rutik/systemdesign/spring/spring_performance/spring_performance.md) | Startup optimization, lazy init, virtual threads (Boot 3.2+), GraalVM native, connection pool sizing |
| [Spring Batch](src/main/java/com/rutik/systemdesign/spring/spring_batch/spring_batch.md) | Job/Step/chunk model, ItemReader/Processor/Writer, JobRepository, @StepScope, partitioning, skip/retry |
| [Spring Events & Scheduling](src/main/java/com/rutik/systemdesign/spring/spring_events_and_scheduling/spring_events_and_scheduling.md) | ApplicationEventPublisher, @EventListener, @TransactionalEventListener, @Scheduled, ShedLock |
| [Validation & Error Handling](src/main/java/com/rutik/systemdesign/spring/validation_and_error_handling/validation_and_error_handling.md) | Bean Validation (JSR-380), @Valid/@Validated, custom ConstraintValidator, ProblemDetail (RFC 7807) |
| [Observability & Tracing](src/main/java/com/rutik/systemdesign/spring/observability_and_tracing/observability_and_tracing.md) | Micrometer Observation API, Micrometer Tracing + OTLP, W3C traceparent, structured logging |
| [Spring AI](src/main/java/com/rutik/systemdesign/spring/spring_ai/spring_ai.md) | ChatClient fluent API, prompt templates, structured output, VectorStore + RAG advisors, @Tool function calling, model routing via beans (Spring AI 1.0 GA) |
| [Spring Native & GraalVM](src/main/java/com/rutik/systemdesign/spring/spring_native_graalvm/spring_native_graalvm.md) | AOT processing, reachability metadata/hints, build-time vs runtime init, tracing agent, startup/memory vs peak-throughput tradeoff |
| [Spring Integration](src/main/java/com/rutik/systemdesign/spring/spring_integration/spring_integration.md) | EIP: channels, adapters/gateways, router/splitter/aggregator/transformer, Java DSL; contrast with spring_messaging |
| [Spring Modulith](src/main/java/com/rutik/systemdesign/spring/spring_modulith/spring_modulith.md) | Modular monolith: @ApplicationModule, ArchUnit verification, @ApplicationModuleListener, event publication registry, module tests, docs |
| [Spring for GraphQL](src/main/java/com/rutik/systemdesign/spring/spring_graphql/spring_graphql.md) | Schema-first @QueryMapping/@SchemaMapping, @BatchMapping/DataLoader (N+1), subscriptions, cursor pagination, error handling |

#### Spring Case Studies
| Case Study | Core Concepts |
|------------|--------------|
| [Design a Multi-Tenant SaaS API](src/main/java/com/rutik/systemdesign/spring/case_studies/design_multitenant_api.md) | Request-scoped beans, per-tenant data sources, dynamic routing, security context propagation |
| [Design an Event-Driven Microservice](src/main/java/com/rutik/systemdesign/spring/case_studies/design_event_driven_microservice.md) | Spring Kafka, transactional outbox, idempotent consumers, Saga choreography |
| [Design a Reactive API Gateway](src/main/java/com/rutik/systemdesign/spring/case_studies/design_api_gateway.md) | Spring Cloud Gateway, WebFlux, global filters, Resilience4j circuit breaker, JWT relay |
| [Design a Spring Batch Pipeline](src/main/java/com/rutik/systemdesign/spring/case_studies/design_batch_pipeline.md) | Job/Step/chunk model, partitioning, skip/retry, JobRepository, remote chunking |
| [Design a Distributed Cache](src/main/java/com/rutik/systemdesign/spring/case_studies/design_distributed_caching.md) | Two-level cache, Redis Pub/Sub invalidation, stampede prevention, @Cacheable |
| [Design a Distributed Rate Limiter](src/main/java/com/rutik/systemdesign/spring/case_studies/design_distributed_rate_limiter_spring.md) | Redis token bucket Lua script, OncePerRequestFilter, fail-open fallback |
| [Design an OAuth2 Authorization Server](src/main/java/com/rutik/systemdesign/spring/case_studies/design_oauth2_authorization_server.md) | Spring Authorization Server, PKCE, refresh token rotation, JWKS key rollover |
| [Design an Idempotent Payment API](src/main/java/com/rutik/systemdesign/spring/case_studies/design_idempotent_payment_api.md) | Idempotency keys, outbox pattern, pg_advisory_xact_lock, exactly-once semantics |
| [Design a Real-Time Notification Service](src/main/java/com/rutik/systemdesign/spring/case_studies/design_realtime_notification_service.md) | WebSocket + SSE, Redis Pub/Sub fan-out, virtual threads, Redis ZSET history |

See the [Spring Master Index](src/main/java/com/rutik/systemdesign/spring/README.md) for the full 8-phase learning path, version matrix, and cross-reference map.

---

### Python — Senior Engineer & Interview Prep Guide

A comprehensive, one-stop reference for mastering **pure Python** — from language internals and CPython mechanics (reference counting, generational GC, the GIL, metaclasses) through asyncio and structured concurrency, the type system, performance profiling, testing, and packaging. Everything a senior Python software engineer is expected to know in technical interviews. For the FastAPI/ASGI web stack, see the **FastAPI** section below.

#### Phase 1 — Language Core & Data Model
| Module | Key Concepts |
|--------|-------------|
| [Data Model & Objects](src/main/java/com/rutik/systemdesign/python/data_model_and_objects/data_model_and_objects.md) | Dunder methods, `__slots__`, MRO/C3 linearization, operator overloading, hashing/equality contract |
| [Core Language Idioms](src/main/java/com/rutik/systemdesign/python/core_language_idioms/core_language_idioms.md) | Mutability vs identity, EAFP vs LBYL, comprehensions, walrus `:=`, `match`/`case` (3.10) |
| [Iterators & Generators](src/main/java/com/rutik/systemdesign/python/iterators_and_generators/iterators_and_generators.md) | Iterator protocol, `yield`/`yield from`, lazy pipelines, `itertools`, generator coroutines |
| [Decorators & Closures](src/main/java/com/rutik/systemdesign/python/decorators_and_closures/decorators_and_closures.md) | Closures, function/class/parametrized decorators, `functools.wraps/lru_cache/cached_property` |
| [Context Managers & Exceptions](src/main/java/com/rutik/systemdesign/python/context_managers_and_exceptions/context_managers_and_exceptions.md) | `contextlib`, `ExitStack`, async CMs, `ExceptionGroup`/`except*` (3.11), traceback manipulation |
| [Collections & Data Structures](src/main/java/com/rutik/systemdesign/python/collections_and_data_structures/collections_and_data_structures.md) | `list`/`dict`/`set` internals, `collections` module, `heapq`, `bisect`, Big-O |
| [Strings, Bytes, Encoding & Regex](src/main/java/com/rutik/systemdesign/python/strings_bytes_encoding_and_regex/strings_bytes_encoding_and_regex.md) | `str` vs `bytes`, Unicode/codecs, `re` engine, catastrophic backtracking |
| [File I/O & Serialization](src/main/java/com/rutik/systemdesign/python/file_io_and_serialization/file_io_and_serialization.md) | `pathlib`, text/binary I/O, `json`/`csv`, `pickle` security |

#### Phase 2 — CPython Internals & Type System
| Module | Key Concepts |
|--------|-------------|
| [CPython Memory Model](src/main/java/com/rutik/systemdesign/python/cpython_memory_model/cpython_memory_model.md) | Reference counting, generational GC, `PyObject` header, arenas/pools/blocks, interning |
| [GIL & Free-Threading](src/main/java/com/rutik/systemdesign/python/the_gil_and_free_threading/the_gil_and_free_threading.md) | GIL mechanics, GIL release points, contention profiling, PEP 703 (3.13), sub-interpreters |
| [Metaclasses & Metaprogramming](src/main/java/com/rutik/systemdesign/python/metaclasses_and_metaprogramming/metaclasses_and_metaprogramming.md) | `type()`, metaclasses, `__init_subclass__`, descriptors (non-data/data), `__getattr__` |
| [Type System & Typing](src/main/java/com/rutik/systemdesign/python/the_type_system_and_typing/the_type_system_and_typing.md) | Type hints, generics, `Protocol`, `TypeVar`/`ParamSpec`, variance, PEP 695 (3.12), mypy/pyright |
| [Performance & Profiling](src/main/java/com/rutik/systemdesign/python/performance_and_profiling/performance_and_profiling.md) | `cProfile`/`dis`, CPython 3.11+ speedups, Cython/mypyc/C extensions, common slow patterns |
| [Functional Programming](src/main/java/com/rutik/systemdesign/python/functional_programming/functional_programming.md) | `map`/`filter`/`reduce`, `functools`, immutability, currying/partial, comprehension vs generator perf |

#### Phase 3 — Concurrency, Async & Quality
| Module | Key Concepts |
|--------|-------------|
| [Threading & Multiprocessing](src/main/java/com/rutik/systemdesign/python/threading_and_multiprocessing/threading_and_multiprocessing.md) | `threading`, GIL impact, `multiprocessing`, `concurrent.futures`, shared memory, pickling |
| [asyncio & Event Loop](src/main/java/com/rutik/systemdesign/python/asyncio_and_event_loop/asyncio_and_event_loop.md) | Coroutines, event-loop internals, `gather`/`wait`, `TaskGroup` (3.11), structured concurrency |
| [Async Patterns & Pitfalls](src/main/java/com/rutik/systemdesign/python/async_patterns_and_pitfalls/async_patterns_and_pitfalls.md) | Blocking-in-async detection, `run_in_executor`, backpressure, async generators, retries |
| [Design Patterns in Python](src/main/java/com/rutik/systemdesign/python/design_patterns_in_python/design_patterns_in_python.md) | Pythonic GoF, singleton via module, strategy via callables, anti-patterns |
| [stdlib: datetime & Logging](src/main/java/com/rutik/systemdesign/python/stdlib_datetime_and_logging/stdlib_datetime_and_logging.md) | `datetime`/`zoneinfo`/tz pitfalls, structured logging, `argparse`, `subprocess` |
| [Testing with pytest](src/main/java/com/rutik/systemdesign/python/testing_with_pytest/testing_with_pytest.md) | pytest, fixtures/scopes, `parametrize`, `monkeypatch`, `hypothesis`, `pytest-asyncio` |
| [Packaging & Project Tooling](src/main/java/com/rutik/systemdesign/python/packaging_and_project_tooling/packaging_and_project_tooling.md) | `pyproject.toml`, `uv`/poetry/pip, `ruff`/mypy, wheels/sdist, dependency resolution |

See the [Python Master Index](src/main/java/com/rutik/systemdesign/python/README.md) for the full learning path, version matrix, build tracker, and cross-reference map.

---

### FastAPI — Senior Engineer & Interview Prep Guide

A comprehensive, one-stop reference for mastering **FastAPI** and the **ASGI** production stack — from Starlette/Uvicorn fundamentals and Pydantic v2 through routing, dependency injection, and middleware, to production concerns (async SQLAlchemy, JWT/OAuth2, WebSockets/streaming, task queues, observability, K8s deployment, and OWASP hardening). Everything a senior backend engineer building Python APIs is expected to know in technical interviews.

#### Phase 1 — FastAPI Core & ASGI
| Module | Key Concepts |
|--------|-------------|
| [FastAPI Fundamentals & ASGI](src/main/java/com/rutik/systemdesign/fastapi/fastapi_fundamentals_asgi/fastapi_fundamentals_asgi.md) | ASGI vs WSGI, Starlette, Uvicorn, `lifespan`, ASGI 3 scope/receive/send, auto OpenAPI/Swagger |
| [Pydantic v2 — Deep Dive](src/main/java/com/rutik/systemdesign/fastapi/pydantic_v2_deep_dive/pydantic_v2_deep_dive.md) | Validation/serialization, `@field_validator`, `pydantic-core` Rust, v1→v2 migration, `BaseSettings` |
| [Routing & Request Handling](src/main/java/com/rutik/systemdesign/fastapi/routing_and_request_handling/routing_and_request_handling.md) | Path operations, `APIRouter`, response models, status codes, content negotiation |
| [Dependency Injection in FastAPI](src/main/java/com/rutik/systemdesign/fastapi/dependency_injection_in_fastapi/dependency_injection_in_fastapi.md) | `Depends`, sub-dependencies, `yield` deps, caching/scopes, `dependency_overrides` for tests |
| [Middleware & Lifecycle](src/main/java/com/rutik/systemdesign/fastapi/middleware_and_lifecycle/middleware_and_lifecycle.md) | Middleware stack, `BackgroundTasks`, CORS/GZip, custom middleware, exception handler ordering |
| [Configuration & Settings](src/main/java/com/rutik/systemdesign/fastapi/configuration_and_settings_management/configuration_and_settings_management.md) | `pydantic-settings`, 12-factor config, env vars/secrets, layered settings, per-env overrides |

#### Phase 2 — FastAPI Production Concerns
| Module | Key Concepts |
|--------|-------------|
| [Async Database & SQLAlchemy](src/main/java/com/rutik/systemdesign/fastapi/async_database_sqlalchemy/async_database_sqlalchemy.md) | SQLAlchemy 2.0 async, `AsyncSession`, Alembic, SQLModel, session-per-request via `Depends`, N+1 |
| [Authentication & Security](src/main/java/com/rutik/systemdesign/fastapi/authentication_and_security/authentication_and_security.md) | OAuth2 password flow, JWT, scopes, passlib/bcrypt/argon2, OIDC, security deps, CSRF/CORS |
| [Error Handling & Validation](src/main/java/com/rutik/systemdesign/fastapi/error_handling_and_validation/error_handling_and_validation.md) | `HTTPException`, custom handlers, `RequestValidationError`, RFC 7807 Problem Details |
| [Testing FastAPI](src/main/java/com/rutik/systemdesign/fastapi/testing_fastapi/testing_fastapi.md) | `TestClient`, `httpx.AsyncClient`, `pytest-asyncio`, `dependency_overrides`, transactional rollback |
| [WebSockets, SSE & Streaming](src/main/java/com/rutik/systemdesign/fastapi/websockets_sse_and_streaming/websockets_sse_and_streaming.md) | WebSockets, SSE, `StreamingResponse`, Redis pub/sub fan-out, connection registry, backpressure |
| [Background Jobs & Task Queues](src/main/java/com/rutik/systemdesign/fastapi/background_jobs_and_task_queues/background_jobs_and_task_queues.md) | `BackgroundTasks` vs Celery vs ARQ vs Dramatiq, idempotency, retries, dead-letter queues |
| [HTTP Clients & External APIs](src/main/java/com/rutik/systemdesign/fastapi/http_clients_and_external_apis/http_clients_and_external_apis.md) | `httpx`/`aiohttp` async clients, connection pooling, retries/backoff, circuit breakers |
| [Message Queues & Event-Driven](src/main/java/com/rutik/systemdesign/fastapi/message_queues_and_event_driven/message_queues_and_event_driven.md) | `aiokafka`/`aio-pika`, outbox pattern, consumer groups, idempotent consumers |

#### Phase 3 — Deployment, Observability & Scale
| Module | Key Concepts |
|--------|-------------|
| [Production Deployment & Scaling](src/main/java/com/rutik/systemdesign/fastapi/production_deployment_and_scaling/production_deployment_and_scaling.md) | Gunicorn+Uvicorn workers, worker tuning, container/K8s, graceful shutdown, ASGI scaling |
| [Observability & Monitoring](src/main/java/com/rutik/systemdesign/fastapi/observability_and_monitoring/observability_and_monitoring.md) | Structured logging, OpenTelemetry tracing, Prometheus metrics, health/readiness probes |
| [Caching & Performance](src/main/java/com/rutik/systemdesign/fastapi/caching_and_performance/caching_and_performance.md) | Redis caching, response/in-process caching, connection pooling, async perf pitfalls |
| [API Design & Versioning](src/main/java/com/rutik/systemdesign/fastapi/api_design_and_versioning/api_design_and_versioning.md) | REST best practices, versioning, cursor pagination, rate limiting in FastAPI, idempotency keys |
| [Security Hardening & OWASP](src/main/java/com/rutik/systemdesign/fastapi/security_hardening_and_owasp/security_hardening_and_owasp.md) | OWASP API Top 10 in FastAPI, injection/SSRF, secrets handling, pip-audit, input validation |

#### FastAPI Case Studies
| Case Study | Core Concepts |
|------------|--------------|
| [Design a Rate-Limited API with FastAPI](src/main/java/com/rutik/systemdesign/fastapi/case_studies/design_rate_limited_api_fastapi.md) | Token-bucket via Redis Lua, `Depends`-injected limiter, async middleware, 429 error handling |
| [Design a Multi-Tenant SaaS API](src/main/java/com/rutik/systemdesign/fastapi/case_studies/design_multi_tenant_saas_api.md) | Async SQLAlchemy tenant isolation, JWT/RBAC, `Depends` scoping, schema-per-tenant |
| [Design a Real-Time Chat System](src/main/java/com/rutik/systemdesign/fastapi/case_studies/design_realtime_chat_fastapi.md) | WebSockets, Redis pub/sub fan-out, connection registry, backpressure |
| [Design an Async Task Queue System](src/main/java/com/rutik/systemdesign/fastapi/case_studies/design_async_task_queue.md) | ARQ/Celery, idempotency, retries with exponential backoff, dead-letter queues |
| [Design an Async Web Scraper](src/main/java/com/rutik/systemdesign/fastapi/case_studies/design_async_web_scraper.md) | asyncio + aiohttp, `Semaphore`, producer/consumer, politeness/crawl budget |
| [Design an ML Inference API (FastAPI)](src/main/java/com/rutik/systemdesign/fastapi/case_studies/design_ml_inference_api_fastapi.md) | Async model serving, micro-batching, async cache, `lifespan` model loading, streaming responses |

See the [FastAPI Master Index](src/main/java/com/rutik/systemdesign/fastapi/README.md) for the full learning path, version matrix, and cross-reference map.

---

### Backend Engineering — Senior Engineer & Interview Prep Guide

A deep-dive guide to building, optimizing, inspecting, and testing production backend systems. Primary focus is Java/Spring. Covers networking fundamentals, API design, performance engineering, database internals, resilience patterns, security, testing, event-driven architecture, and microservices — with production war stories, concrete numbers, and broken-code-then-fix examples throughout.

#### Phase 1 — Networking Fundamentals
| Module | Key Concepts |
|--------|-------------|
| [OSI Model & Networking](src/main/java/com/rutik/systemdesign/backend/osi_model_and_networking/osi_model_and_networking.md) | 7 OSI layers, TCP/IP 4-layer mapping, ARP, NAT, MTU/fragmentation, ICMP, subnetting |
| [TCP/IP Deep Dive](src/main/java/com/rutik/systemdesign/backend/tcp_ip_deep_dive/tcp_ip_deep_dive.md) | 3-way handshake, congestion control (CUBIC/BBR), TIME_WAIT, Nagle's algorithm, socket tuning |
| [UDP and QUIC](src/main/java/com/rutik/systemdesign/backend/udp_and_quic/udp_and_quic.md) | UDP stateless model, QUIC 0-RTT/connection migration/HoL-blocking elimination, HTTP/3 |
| [HTTP Protocols](src/main/java/com/rutik/systemdesign/backend/http_protocols/http_protocols.md) | HTTP/1.1 vs 2 vs 3, TLS 1.2 vs 1.3, HPACK, ALPN, SNI, HSTS, ETag, Cache-Control |

#### Phase 2 — API Design & Protocols
| Module | Key Concepts |
|--------|-------------|
| [REST API Design](src/main/java/com/rutik/systemdesign/backend/rest_api_design/rest_api_design.md) | REST constraints, resource modeling, versioning, idempotency, cursor pagination, RFC 7807 |
| [gRPC and Protobuf](src/main/java/com/rutik/systemdesign/backend/grpc_and_protobuf/grpc_and_protobuf.md) | Protobuf wire format, 4 RPC modes, interceptors, deadlines, health checking, gRPC-Web |
| [GraphQL](src/main/java/com/rutik/systemdesign/backend/graphql/graphql.md) | Schema-first design, DataLoader N+1 fix, persisted queries, complexity limiting, federation |
| [WebSockets and SSE](src/main/java/com/rutik/systemdesign/backend/websockets_and_sse/websockets_and_sse.md) | WebSocket upgrade, frame structure, SSE reconnection, STOMP, Redis pub/sub fan-out |

#### Phase 3 — Performance Engineering
| Module | Key Concepts |
|--------|-------------|
| [Performance Profiling](src/main/java/com/rutik/systemdesign/backend/performance_profiling/performance_profiling.md) | async-profiler, JFR, flamegraph reading, heap/thread dump analysis, GC log parsing |
| [Connection Pooling Deep Dive](src/main/java/com/rutik/systemdesign/backend/connection_pooling_deep_dive/connection_pooling_deep_dive.md) | HikariCP ConcurrentBag internals, pool sizing formula, leak detection, PgBouncer |
| [Caching Strategies Deep Dive](src/main/java/com/rutik/systemdesign/backend/caching_strategies_deep_dive/caching_strategies_deep_dive.md) | Cache-aside/write-through/write-behind, Redis data structures, XFetch stampede prevention |
| [Async and Concurrency Patterns](src/main/java/com/rutik/systemdesign/backend/async_and_concurrency_patterns/async_and_concurrency_patterns.md) | Thread pool sizing, CompletableFuture pitfalls, virtual thread pinning, reactive backpressure |

#### Phase 4 — Database Engineering
| Module | Key Concepts |
|--------|-------------|
| [Database Internals and Indexing](src/main/java/com/rutik/systemdesign/backend/database_internals_and_indexing/database_internals_and_indexing.md) | B+tree, WAL mechanics, MVCC, index types (B-tree/GIN/BRIN), covering indexes, VACUUM |
| [Query Optimization](src/main/java/com/rutik/systemdesign/backend/query_optimization/query_optimization.md) | EXPLAIN ANALYZE, N+1 detection and fixes, keyset pagination, JDBC batch inserts |
| [Database Migrations](src/main/java/com/rutik/systemdesign/backend/database_migrations/database_migrations.md) | Flyway vs Liquibase, expand-contract pattern, CREATE INDEX CONCURRENTLY, gh-ost |
| [Distributed Transactions and Consistency](src/main/java/com/rutik/systemdesign/backend/distributed_transactions_and_consistency/distributed_transactions_and_consistency.md) | 2PC failure modes, Saga (choreography vs orchestration), outbox pattern, idempotency keys |
| [Database Types Deep Dive](src/main/java/com/rutik/systemdesign/backend/database_types_deep_dive/database_types_deep_dive.md) | Relational/Document/Key-Value/Wide-Column/Time-Series/Search/Graph/NewSQL — internals, tradeoffs, when to use |

#### Phase 5 — Resilience & Reliability
| Module | Key Concepts |
|--------|-------------|
| [Fault Tolerance Patterns](src/main/java/com/rutik/systemdesign/backend/fault_tolerance_patterns/fault_tolerance_patterns.md) | Circuit breaker (Resilience4j), retry with jitter, bulkhead, timeout hierarchy, fallback |
| [Rate Limiting In Depth](src/main/java/com/rutik/systemdesign/backend/rate_limiting_in_depth/rate_limiting_in_depth.md) | Token bucket, leaky bucket, sliding window, Redis Lua atomic scripts, adaptive throttling |
| [Observability and Monitoring](src/main/java/com/rutik/systemdesign/backend/observability_and_monitoring/observability_and_monitoring.md) | Metrics/logs/traces, Micrometer, MDC correlation, OpenTelemetry, SLO/SLI/error budget |

#### Phase 6 — Security
| Module | Key Concepts |
|--------|-------------|
| [Backend Security (OWASP)](src/main/java/com/rutik/systemdesign/backend/backend_security_owasp/backend_security_owasp.md) | OWASP Top 10 2021, SQL injection fix, CSRF/XSS/SSRF, secret management, security headers |
| [Auth and Authorization Systems](src/main/java/com/rutik/systemdesign/backend/auth_and_authorization_systems/auth_and_authorization_systems.md) | JWT internals, OAuth2 flows + PKCE, OIDC, opaque vs JWT, refresh rotation, RBAC vs ABAC |

#### Phase 7 — Testing & Quality
| Module | Key Concepts |
|--------|-------------|
| [Backend Testing Strategies](src/main/java/com/rutik/systemdesign/backend/backend_testing_strategies/backend_testing_strategies.md) | Testing pyramid, test doubles taxonomy, Pact contracts, PIT mutation testing, Testcontainers |
| [Load and Performance Testing](src/main/java/com/rutik/systemdesign/backend/load_and_performance_testing/load_and_performance_testing.md) | k6/Gatling scripting, coordinated omission, percentile analysis, capacity planning |
| [Chaos Engineering](src/main/java/com/rutik/systemdesign/backend/chaos_engineering/chaos_engineering.md) | Steady-state hypothesis, fault injection taxonomy, blast radius, GameDay runbook, AWS FIS |

#### Phase 8 — Event-Driven Architecture
| Module | Key Concepts |
|--------|-------------|
| [Event-Driven Fundamentals](src/main/java/com/rutik/systemdesign/backend/event_driven_fundamentals/event_driven_fundamentals.md) | Events vs commands vs queries, event taxonomy, choreography vs orchestration, event storming |
| [Kafka Deep Dive](src/main/java/com/rutik/systemdesign/backend/kafka_deep_dive/kafka_deep_dive.md) | Producer internals (acks/batching/idempotence), consumer rebalancing, EOS, Kafka Streams, Schema Registry |
| [RabbitMQ Deep Dive](src/main/java/com/rutik/systemdesign/backend/rabbitmq_deep_dive/rabbitmq_deep_dive.md) | AMQP 0-9-1 object model, exchange/binding matching, confirms vs transactions, quorum queues (Raft), streams and replay, prefetch, DLX, Khepri |
| [Event Sourcing and CQRS](src/main/java/com/rutik/systemdesign/backend/event_sourcing_and_cqrs/event_sourcing_and_cqrs.md) | Events as source of truth, aggregate design, snapshots, projections, event upcasting, Axon Framework |
| [Messaging Patterns](src/main/java/com/rutik/systemdesign/backend/messaging_patterns/messaging_patterns.md) | Outbox pattern (polling + Debezium CDC), transactional inbox, DLQ handling, Avro schema evolution |

#### Phase 9 — Microservices Architecture
| Module | Key Concepts |
|--------|-------------|
| [Microservices Fundamentals](src/main/java/com/rutik/systemdesign/backend/microservices_fundamentals/microservices_fundamentals.md) | When NOT to decompose, DDD bounded contexts, strangler fig, data ownership, distributed monolith |
| [API Gateway Patterns](src/main/java/com/rutik/systemdesign/backend/api_gateway_patterns/api_gateway_patterns.md) | Gateway responsibilities, BFF pattern, API composition, Spring Cloud Gateway, Kong, AWS API GW |
| [Service Mesh and Service Discovery](src/main/java/com/rutik/systemdesign/backend/service_mesh_and_service_discovery/service_mesh_and_service_discovery.md) | Istio/Envoy, mTLS zero-trust, xDS protocol, Eureka vs DNS vs mesh discovery, probe design |
| [Distributed System Operational Patterns](src/main/java/com/rutik/systemdesign/backend/distributed_system_operational_patterns/distributed_system_operational_patterns.md) | Bulkhead, sidecar, ACL, strangler fig steps, correlation ID, distributed config, graceful shutdown |
| [Container and Deployment Patterns](src/main/java/com/rutik/systemdesign/backend/container_and_deployment_patterns/container_and_deployment_patterns.md) | Docker multi-stage builds, K8s rolling/blue-green/canary, probe design, resource limits, HPA/KEDA |

#### Backend Case Studies
| Case Study | Core Concepts |
|------------|--------------|
| [Design Booking System](src/main/java/com/rutik/systemdesign/backend/case_studies/design_booking_system/design_booking_system.md) | Optimistic locking, idempotency key, Redis distributed lock, race condition prevention |
| [Design Feed Service](src/main/java/com/rutik/systemdesign/backend/case_studies/design_feed_service/design_feed_service.md) | Fan-out-on-write vs fan-out-on-read, Redis sorted sets, cursor pagination, cache warming |
| [Design Payment Processor](src/main/java/com/rutik/systemdesign/backend/case_studies/design_payment_processor/design_payment_processor.md) | Saga orchestration, outbox pattern, idempotency table, audit log, compensating transactions |
| [Design Event-Driven Order System](src/main/java/com/rutik/systemdesign/backend/case_studies/design_event_driven_order_system/design_event_driven_order_system.md) | Kafka choreography, CQRS read model, Avro schema evolution, DLQ handling, exactly-once |
| [Design Microservices Migration](src/main/java/com/rutik/systemdesign/backend/case_studies/design_microservices_migration/design_microservices_migration.md) | Strangler fig steps, Spring Cloud Gateway routing, Debezium CDC, feature flag cutover |

See the [Backend Master Index](src/main/java/com/rutik/systemdesign/backend/README.md) for the full 9-phase learning path, version matrix (Java 21, Spring Boot 3.2+, Kafka 3.6+), and cross-reference map.

---

### Database Engineering — Principal Engineer & Interview Prep Guide

A laser-focused, principal-engineer-level reference for database internals, selection strategies, production operations, distributed systems, and real-world case studies. 30 modules across 7 phases covering relational, NoSQL, emerging, and distributed database concepts.

#### Phase 1 — Foundations
| Module | Key Concepts |
|--------|-------------|
| [Database Fundamentals](src/main/java/com/rutik/systemdesign/database/database_fundamentals/database_fundamentals.md) | ACID, BASE, CAP, PACELC, isolation levels, MVCC |
| [Storage Engines Internals](src/main/java/com/rutik/systemdesign/database/storage_engines_internals/storage_engines_internals.md) | B+tree, LSM-tree, WAL, buffer pool, row vs columnar storage |
| [Indexing Deep Dive](src/main/java/com/rutik/systemdesign/database/indexing_deep_dive/indexing_deep_dive.md) | B+tree, GIN, BRIN, covering indexes, partial, composite, index bloat |
| [Concurrency Control & Locking](src/main/java/com/rutik/systemdesign/database/concurrency_control_and_locking/concurrency_control_and_locking.md) | MVCC, deadlocks, gap locks, SELECT FOR UPDATE SKIP LOCKED, advisory locks |

#### Phase 2 — Relational Databases
| Module | Key Concepts |
|--------|-------------|
| [PostgreSQL Internals](src/main/java/com/rutik/systemdesign/database/postgresql_internals/postgresql_internals.md) | VACUUM, autovacuum, EXPLAIN ANALYZE, TOAST, replication slots, partitioning |
| [MySQL InnoDB Internals](src/main/java/com/rutik/systemdesign/database/mysql_innodb_internals/mysql_innodb_internals.md) | Clustered index, redo/undo log, binary log, online DDL, GTID |
| [SQL Query Optimization](src/main/java/com/rutik/systemdesign/database/sql_query_optimization/sql_query_optimization.md) | Join algorithms, CBO statistics, keyset pagination, N+1, window functions |
| [Schema Design & Normalization](src/main/java/com/rutik/systemdesign/database/schema_design_and_normalization/schema_design_and_normalization.md) | Normal forms, temporal data, audit trails, multi-tenancy, JSONB |
| [Database Migrations (Zero Downtime)](src/main/java/com/rutik/systemdesign/database/database_migrations_zero_downtime/database_migrations_zero_downtime.md) | Flyway, Liquibase, expand-contract, gh-ost, CREATE INDEX CONCURRENTLY |

#### Phase 3 — NoSQL Databases
| Module | Key Concepts |
|--------|-------------|
| [Document Databases](src/main/java/com/rutik/systemdesign/database/document_databases/document_databases.md) | MongoDB WiredTiger, embedding vs referencing, aggregation pipeline, sharding |
| [Key-Value Stores](src/main/java/com/rutik/systemdesign/database/key_value_stores/key_value_stores.md) | Redis data structures, persistence (RDB/AOF), Cluster, Streams, Redlock |
| [Redis Internals](src/main/java/com/rutik/systemdesign/database/redis_internals/redis_internals.md) | SDS/listpack/dict encodings, event loop and io-threads, eviction (LRU/LFU), fork + copy-on-write, multi-part AOF, PSYNC and resharding, locks and fencing, Redis 8 vs Valkey 9 |
| [Wide-Column Databases](src/main/java/com/rutik/systemdesign/database/wide_column_databases/wide_column_databases.md) | Cassandra ring, partition key, compaction strategies, consistency levels |
| [Search Engines](src/main/java/com/rutik/systemdesign/database/search_engines/search_engines.md) | Inverted index, BM25, Elasticsearch ILM, aggregations, deep pagination |
| [Elasticsearch Internals](src/main/java/com/rutik/systemdesign/database/elasticsearch_internals/elasticsearch_internals.md) | Lucene segment files, refresh/flush/merge, translog durability, block-tree term index, inlined skip data, doc values vs stored vs `_source`, BM25 one-byte norms, per-shard IDF and `dfs_query_then_fetch`, PIT + `search_after`, routing factor and `_split`, seq_no/primary-term checkpoints, voting configuration, `doc_count_error`, logsdb/TSDS, frozen tier, `dense_vector` BBQ |
| [Graph Databases](src/main/java/com/rutik/systemdesign/database/graph_databases/graph_databases.md) | Property graph, Neo4j index-free adjacency, Cypher, fraud detection patterns |
| [Time-Series Databases](src/main/java/com/rutik/systemdesign/database/time_series_databases/time_series_databases.md) | TimescaleDB, InfluxDB, ClickHouse, Prometheus, Gorilla XOR compression |

#### Phase 4 — Emerging Databases
| Module | Key Concepts |
|--------|-------------|
| [Vector Databases](src/main/java/com/rutik/systemdesign/database/vector_databases/vector_databases.md) | HNSW, IVF, PQ, pgvector, hybrid search, multi-tenancy, RAG integration |
| [NewSQL & Distributed SQL](src/main/java/com/rutik/systemdesign/database/newsql_and_distributed_sql/newsql_and_distributed_sql.md) | Spanner TrueTime, CockroachDB Raft, TiDB HTAP, YugabyteDB, global ACID |
| [In-Memory Databases](src/main/java/com/rutik/systemdesign/database/in_memory_databases/in_memory_databases.md) | Redis vs Memcached, VoltDB, Ignite, eviction policies, durability modes |

#### Phase 5 — Distributed Database Concepts
| Module | Key Concepts |
|--------|-------------|
| [Replication & High Availability](src/main/java/com/rutik/systemdesign/database/replication_and_high_availability/replication_and_high_availability.md) | Sync vs async, Patroni, split-brain prevention, replication slots, multi-region |
| [Sharding & Partitioning](src/main/java/com/rutik/systemdesign/database/sharding_and_partitioning/sharding_and_partitioning.md) | Consistent hashing, shard key selection, Vitess, celebrity shard, resharding |
| [Distributed Transactions](src/main/java/com/rutik/systemdesign/database/distributed_transactions/distributed_transactions.md) | 2PC, Saga (choreography vs orchestration), outbox pattern, idempotency keys |
| [Consistency Models & Consensus](src/main/java/com/rutik/systemdesign/database/consistency_models_and_consensus/consistency_models_and_consensus.md) | Linearizability, Raft, Paxos, CRDTs, vector clocks, fencing tokens |
| [Database Caching Patterns](src/main/java/com/rutik/systemdesign/database/database_caching_patterns/database_caching_patterns.md) | Cache-aside, write-through, write-behind, stampede prevention, hot key |

#### Phase 6 — Production Operations
| Module | Key Concepts |
|--------|-------------|
| [Connection Pool Management](src/main/java/com/rutik/systemdesign/database/connection_pool_management/connection_pool_management.md) | HikariCP internals, pool sizing formula, PgBouncer transaction mode, K8s storm |
| [Database Performance Tuning](src/main/java/com/rutik/systemdesign/database/database_performance_tuning/database_performance_tuning.md) | shared_buffers, work_mem, checkpoint tuning, autovacuum, slow query analysis |
| [Backup, Recovery & Disaster Recovery](src/main/java/com/rutik/systemdesign/database/backup_recovery_and_disaster_recovery/backup_recovery_and_disaster_recovery.md) | PITR, WAL-G, pgBackRest, RPO/RTO measurement, restore drills |
| [Database Security & Compliance](src/main/java/com/rutik/systemdesign/database/database_security_and_compliance/database_security_and_compliance.md) | RLS, scram-sha-256, pgAudit, HashiCorp Vault, GDPR erasure, TDE |

#### Phase 7 — Architecture & Selection
| Module | Key Concepts |
|--------|-------------|
| [Database Selection Framework](src/main/java/com/rutik/systemdesign/database/database_selection_framework/database_selection_framework.md) | Selection matrix, benchmark traps, TCO analysis, scorecard methodology |
| [Polyglot Persistence Patterns](src/main/java/com/rutik/systemdesign/database/polyglot_persistence_patterns/polyglot_persistence_patterns.md) | CQRS, CDC (Debezium), dual-write failure modes, event sourcing, data mesh |

#### Database Case Studies
| Case Study | Key Databases | Core Concepts | Level |
|------------|--------------|---------------|-------|
| [Banking Ledger](src/main/java/com/rutik/systemdesign/database/case_studies/design_banking_ledger/design_banking_ledger.md) | PostgreSQL, Redis | Double-entry bookkeeping, SERIALIZABLE isolation, idempotency, RPO=0 | Expert |
| [E-Commerce Catalog](src/main/java/com/rutik/systemdesign/database/case_studies/design_ecommerce_catalog/design_ecommerce_catalog.md) | PostgreSQL, Elasticsearch, Redis | Polyglot persistence, CDC sync, inventory counters, full-text search | Advanced |
| [Social Media Feed Storage](src/main/java/com/rutik/systemdesign/database/case_studies/design_social_media_feed_storage/design_social_media_feed_storage.md) | Cassandra, Redis, PostgreSQL | Fan-out on write vs read, celebrity problem, TWCS, trending leaderboards | Advanced |
| [Real-Time Analytics Platform](src/main/java/com/rutik/systemdesign/database/case_studies/design_realtime_analytics_platform/design_realtime_analytics_platform.md) | ClickHouse, Kafka, Redis | Columnar storage, materialized views, HyperLogLog, tenant isolation | Expert |
| [Multi-Tenant SaaS Database](src/main/java/com/rutik/systemdesign/database/case_studies/design_multitenant_saas_database/design_multitenant_saas_database.md) | PostgreSQL (RLS/schema/DB), PgBouncer | Three-tier isolation, RLS, schema-per-tenant, connection pooling at scale | Expert |
| [Monolith to Polyglot Migration](src/main/java/com/rutik/systemdesign/database/case_studies/design_monolith_to_polyglot_migration/design_monolith_to_polyglot_migration.md) | MySQL, PostgreSQL, Elasticsearch, ClickHouse | Strangler fig, CDC, dual-write, validation, zero-downtime migration | Expert |

See the [Database Engineering Master Index](src/main/java/com/rutik/systemdesign/database/README.md) for the full 7-phase learning path, version matrix, and cross-reference map.

---

### Machine Learning (ML) — Senior ML/AI Engineer & Interview Prep Guide

A comprehensive, senior-engineer-level guide to Machine Learning — from mathematical foundations through production MLOps. Covers classical algorithms, deep learning, ML system design, domain specializations (CV, RecSys, NLP, RL, time series), trust-and-safety topics (adversarial ML and robustness, uncertainty quantification and conformal prediction, active learning and weak supervision), a dedicated algorithm-selection module (#33), interpretability and explainability, privacy-preserving ML, multi-task and multi-objective learning, anomaly detection, imbalanced data and leakage traps, and 24 end-to-end case studies (all on the principal 11-section template, plus 5 cross-cutting shared-primitive files). 46 modules across 8 phases. Deliberately scoped to not overlap with the LLM section (which handles transformers, fine-tuning, RAG, and agents).

#### Phase 1 — Mathematical Foundations
| Module | Key Concepts |
|--------|-------------|
| [Linear Algebra and Calculus](src/main/java/com/rutik/systemdesign/ml/linear_algebra_and_calculus/linear_algebra_and_calculus.md) | Vectors, matrices, eigendecomposition, SVD, gradients, Jacobians, Hessians, chain rule |
| [Probability and Statistics](src/main/java/com/rutik/systemdesign/ml/probability_and_statistics/probability_and_statistics.md) | Distributions, Bayes theorem, MLE, MAP, hypothesis testing, confidence intervals, CLT |
| [Optimization Theory](src/main/java/com/rutik/systemdesign/ml/optimization_theory/optimization_theory.md) | SGD, momentum, Adam, AdamW, LR schedules, convexity, saddle points, second-order methods |
| [Information Theory](src/main/java/com/rutik/systemdesign/ml/information_theory/information_theory.md) | Entropy, cross-entropy loss derivation, KL divergence, mutual information, information gain |

#### Phase 2 — Classical ML (Most Interview-Tested)
| Module | Key Concepts |
|--------|-------------|
| [Supervised Learning](src/main/java/com/rutik/systemdesign/ml/supervised_learning/supervised_learning.md) | Linear/logistic regression, SVM, decision trees, KNN, Naive Bayes — with 4 deep-dive sub-files |
| [Ensemble Methods](src/main/java/com/rutik/systemdesign/ml/ensemble_methods/ensemble_methods.md) | Random Forest, XGBoost, LightGBM, CatBoost, stacking, blending — with 4 deep-dive sub-files |
| [Unsupervised Learning](src/main/java/com/rutik/systemdesign/ml/unsupervised_learning/unsupervised_learning.md) | k-means, DBSCAN, hierarchical clustering, PCA, t-SNE, UMAP |
| [Feature Engineering](src/main/java/com/rutik/systemdesign/ml/feature_engineering/feature_engineering.md) | Encoding, scaling, imputation, target encoding, feature selection, Pipeline patterns |
| [Model Evaluation and Selection](src/main/java/com/rutik/systemdesign/ml/model_evaluation_and_selection/model_evaluation_and_selection.md) | Cross-validation, AUC-ROC/AUC-PR, calibration, bias-variance, Optuna hyperparameter search |
| [Imbalanced Data and Leakage Traps](src/main/java/com/rutik/systemdesign/ml/imbalanced_data_and_leakage_traps/imbalanced_data_and_leakage_traps.md) | SMOTE/class weights/focal loss, PR-AUC vs ROC under imbalance, target/temporal/group leakage, fit-inside-fold discipline |

#### Phase 3 — Deep Learning Foundations
| Module | Key Concepts |
|--------|-------------|
| [Neural Network Fundamentals](src/main/java/com/rutik/systemdesign/ml/neural_network_fundamentals/neural_network_fundamentals.md) | MLPs, backpropagation, activations, weight initialization, batch norm, dropout |
| [Convolutional Neural Networks](src/main/java/com/rutik/systemdesign/ml/convolutional_neural_networks/convolutional_neural_networks.md) | Conv2D, pooling, ResNet skip connections, EfficientNet compound scaling, transfer learning |
| [Recurrent Neural Networks](src/main/java/com/rutik/systemdesign/ml/recurrent_neural_networks/recurrent_neural_networks.md) | LSTM, GRU, vanishing gradients, bidirectional, seq2seq, teacher forcing, CTC loss |
| [Training Deep Networks](src/main/java/com/rutik/systemdesign/ml/training_deep_networks/training_deep_networks.md) | LR warmup, gradient clipping, mixed precision, data augmentation, gradient accumulation |
| [PyTorch Deep Dive](src/main/java/com/rutik/systemdesign/ml/pytorch_deep_dive/pytorch_deep_dive.md) | Storage/strides/views, autograd tape, caching allocator and OOM debugging, torch.compile guards and recompilation, autocast policy, DataLoader workers, torch.export |
| [Generative Models](src/main/java/com/rutik/systemdesign/ml/generative_models/generative_models.md) | VAEs, GANs, Diffusion (DDPM), mode collapse, FID score, classifier-free guidance |

#### Phase 4 — Domain Specializations
| Module | Key Concepts |
|--------|-------------|
| [Computer Vision](src/main/java/com/rutik/systemdesign/ml/computer_vision/computer_vision.md) | Object detection, segmentation, ViT, CLIP, self-supervised vision — with 4 deep-dive sub-files |
| [Natural Language Processing](src/main/java/com/rutik/systemdesign/ml/natural_language_processing/natural_language_processing.md) | Word2Vec, GloVe, TF-IDF, text classification, NER (BIO tagging), CRF, topic modeling — with 5 deep-dive sub-files (BERT, attention/seq2seq, retrieval, evaluation, tokenization) |
| [Recommender Systems](src/main/java/com/rutik/systemdesign/ml/recommender_systems/recommender_systems.md) | Two-tower retrieval, collaborative filtering, LTR ranking, bandits — with 5 deep-dive sub-files |
| [Time Series Forecasting](src/main/java/com/rutik/systemdesign/ml/time_series_forecasting/time_series_forecasting.md) | ARIMA, Prophet, DeepAR, Temporal Fusion Transformer, walk-forward validation |
| [Reinforcement Learning](src/main/java/com/rutik/systemdesign/ml/reinforcement_learning/reinforcement_learning.md) | MDP, Q-learning, DQN, PPO, actor-critic, reward shaping, RLHF connection |
| [Multi-Task and Multi-Objective Learning](src/main/java/com/rutik/systemdesign/ml/multi_task_and_multi_objective_learning/multi_task_and_multi_objective_learning.md) | Shared-bottom, MMoE, PLE, uncertainty weighting, PCGrad, Pareto optimization, multi-objective ranking |
| [Anomaly Detection](src/main/java/com/rutik/systemdesign/ml/anomaly_detection/anomaly_detection.md) | Isolation Forest, One-Class SVM, LOF, autoencoders, EVT thresholds, streaming detection, PR-AUC evaluation |
| [Information Retrieval and Search](src/main/java/com/rutik/systemdesign/ml/information_retrieval_and_search/information_retrieval_and_search.md) | Inverted index, BM25, dense/hybrid retrieval (HNSW, RRF), cross-encoder reranking, learning-to-rank (LambdaMART), NDCG/MRR/MAP |
| [Speech and Audio ML](src/main/java/com/rutik/systemdesign/ml/speech_and_audio_ml/speech_and_audio_ml.md) | Spectrograms/MFCC, ASR (CTC, RNN-T, Whisper, wav2vec2), speaker ID/diarization, TTS, WER, SpecAugment |

#### Phase 5 — ML Systems and Infrastructure
| Module | Key Concepts |
|--------|-------------|
| [ML System Design](src/main/java/com/rutik/systemdesign/ml/ml_system_design/ml_system_design.md) | 6-step design framework, feature stores, A/B testing, latency budgets — with 5 deep-dive sub-files |
| [Data Pipelines and Processing](src/main/java/com/rutik/systemdesign/ml/data_pipelines_and_processing/data_pipelines_and_processing.md) | PySpark, Great Expectations, DVC, schema evolution, data validation, Lambda vs Kappa |
| [Distributed Training](src/main/java/com/rutik/systemdesign/ml/distributed_training/distributed_training.md) | PyTorch DDP, FSDP, DeepSpeed ZeRO stages, gradient accumulation, mixed precision BF16 |
| [Experiment Tracking and Versioning](src/main/java/com/rutik/systemdesign/ml/experiment_tracking_and_versioning/experiment_tracking_and_versioning.md) | MLflow, W&B, Optuna TPE, DVC, reproducibility checklist, hyperparameter sweeps |
| [MLflow Deep Dive](src/main/java/com/rutik/systemdesign/ml/mlflow_deep_dive/mlflow_deep_dive.md) | Tracking server and schema, `MLmodel` format, signatures, registry aliases vs stages, autolog gaps, MLflow 3 migration, tracing and prompt registry |
| [GPU and Hardware Optimization](src/main/java/com/rutik/systemdesign/ml/gpu_and_hardware_optimization/gpu_and_hardware_optimization.md) | CUDA, tensor cores, memory hierarchy, profiling, gradient checkpointing, DataLoader tuning |
| [Active Learning and Weak Supervision](src/main/java/com/rutik/systemdesign/ml/active_learning_and_weak_supervision/active_learning_and_weak_supervision.md) | Uncertainty/diversity sampling, query-by-committee, BALD, Snorkel labeling functions, label model, pseudo-labeling, data-centric AI |

#### Phase 6 — Production ML Engineering
| Module | Key Concepts |
|--------|-------------|
| [Model Serving and Inference](src/main/java/com/rutik/systemdesign/ml/model_serving_and_inference/model_serving_and_inference.md) | TorchServe, ONNX, gRPC, dynamic batching, A/B testing, canary, shadow mode |
| [Model Compression and Efficiency](src/main/java/com/rutik/systemdesign/ml/model_compression_and_efficiency/model_compression_and_efficiency.md) | PTQ, QAT, pruning, knowledge distillation, TensorRT, low-rank factorization |
| [Monitoring and Drift Detection](src/main/java/com/rutik/systemdesign/ml/monitoring_and_drift_detection/monitoring_and_drift_detection.md) | Data drift, concept drift, PSI, KS test, SHAP attribution drift, delayed label handling |
| [MLOps and CI/CD](src/main/java/com/rutik/systemdesign/ml/mlops_and_ci_cd/mlops_and_ci_cd.md) | MLflow Registry, Kubeflow Pipelines, Vertex AI, canary deployment, rollback, data validation gates |

#### Phase 7 — Advanced Topics
| Module | Key Concepts |
|--------|-------------|
| [Graph Neural Networks](src/main/java/com/rutik/systemdesign/ml/graph_neural_networks/graph_neural_networks.md) | GCN, GraphSAGE, GAT, GIN, message passing, oversmoothing, PyTorch Geometric |
| [Self-Supervised and Contrastive Learning](src/main/java/com/rutik/systemdesign/ml/self_supervised_and_contrastive_learning/self_supervised_and_contrastive_learning.md) | NT-Xent, InfoNCE, BYOL, ELECTRA, graph SSL, tabular SSL (SCARF) |
| [Causal Inference and ML](src/main/java/com/rutik/systemdesign/ml/causal_inference_and_ml/causal_inference_and_ml.md) | Potential outcomes, propensity scores, uplift modeling, CausalForest, Double ML |
| [Adversarial ML and Robustness](src/main/java/com/rutik/systemdesign/ml/adversarial_ml_and_robustness/adversarial_ml_and_robustness.md) | FGSM/PGD/C&W evasion, data poisoning, backdoors, model extraction, membership inference, adversarial training, randomized smoothing |
| [Uncertainty Quantification and Conformal Prediction](src/main/java/com/rutik/systemdesign/ml/uncertainty_quantification_and_conformal_prediction/uncertainty_quantification_and_conformal_prediction.md) | Aleatoric vs epistemic, MC dropout, deep ensembles, ECE/temperature scaling, conformal prediction sets/intervals, CQR |
| [Interpretability and Explainability](src/main/java/com/rutik/systemdesign/ml/interpretability_and_explainability/interpretability_and_explainability.md) | SHAP (KernelSHAP/TreeSHAP), LIME, integrated gradients, Grad-CAM, permutation importance, PDP/ICE, counterfactuals |
| [Privacy-Preserving ML](src/main/java/com/rutik/systemdesign/ml/privacy_preserving_ml/privacy_preserving_ml.md) | Differential privacy, DP-SGD, federated learning (FedAvg), secure aggregation, PATE, membership inference |
| [Meta-Learning and Few-Shot](src/main/java/com/rutik/systemdesign/ml/meta_learning_and_few_shot/meta_learning_and_few_shot.md) | N-way K-shot, Prototypical Networks, MAML/Reptile, episodic training, metric vs optimization-based |
| [Fairness and Responsible AI](src/main/java/com/rutik/systemdesign/ml/fairness_and_responsible_ai/fairness_and_responsible_ai.md) | Fairness definitions + impossibility, disparate impact, pre/in/post-processing mitigation, proxies, model cards, EU AI Act |

#### Phase 8 — Interview Consolidation
| Module | Key Concepts |
|--------|-------------|
| [ML Interview Patterns](src/main/java/com/rutik/systemdesign/ml/ml_interview_patterns/ml_interview_patterns.md) | 6-step design framework, debug checklist, system design templates, tradeoff tables |

#### Cross-Cutting Reference (Module #33)
| Module | Key Concepts |
|--------|-------------|
| [Model Selection and Algorithm Choice](src/main/java/com/rutik/systemdesign/ml/model_selection_and_algorithm_choice/model_selection_and_algorithm_choice.md) | Algorithm decision matrix, problem-type → algorithm mapping, constraint-driven elimination (latency, interpretability, regulatory), baseline discipline |

#### ML Case Studies (22 total)
| Case Study | Core ML Concepts |
|------------|-----------------|
| [Design Recommendation Engine](src/main/java/com/rutik/systemdesign/ml/case_studies/design_recommendation_engine.md) | Two-tower retrieval, LightGBM ranking, FAISS ANN, MMR diversity, cold start |
| [Design Fraud Detection](src/main/java/com/rutik/systemdesign/ml/case_studies/design_fraud_detection.md) | Imbalanced classification, SMOTE, Flink streaming features, threshold optimization |
| [Design Search Ranking](src/main/java/com/rutik/systemdesign/ml/case_studies/design_search_ranking.md) | LambdaMART, BM25 + dense hybrid, RRF, IPW position bias, LTR |
| [Design Image Classification Pipeline](src/main/java/com/rutik/systemdesign/ml/case_studies/design_image_classification_pipeline.md) | EfficientNet, DDP training, ONNX, TorchServe batching, drift detection |
| [Design Ads CTR Prediction](src/main/java/com/rutik/systemdesign/ml/case_studies/design_ads_click_prediction.md) | DeepFM, feature hashing, Platt calibration, real-time serving, online learning |
| [Design Anomaly Detection](src/main/java/com/rutik/systemdesign/ml/case_studies/design_anomaly_detection.md) | Isolation Forest, Autoencoder, STL decomposition, CUSUM, alert correlation |
| [Design Demand Forecasting](src/main/java/com/rutik/systemdesign/ml/case_studies/design_demand_forecasting.md) | Global LightGBM, lag features, MinT reconciliation, cold start, walk-forward CV |
| [Design Content Feed Ranking](src/main/java/com/rutik/systemdesign/ml/case_studies/design_content_feed_ranking.md) | MMOE multi-task, DPP diversity, position bias IPW, feedback loop handling |
| [Design Autonomous Driving Perception](src/main/java/com/rutik/systemdesign/ml/case_studies/design_autonomous_driving_perception.md) | Sensor fusion, Kalman filter, Hungarian tracking, 3D detection, safety margins |
| [Design ML Platform](src/main/java/com/rutik/systemdesign/ml/case_studies/design_ml_platform.md) | Feature store, Kubeflow, MLflow registry, A/B routing, GPU cost tracking |
| [Design Churn Prediction](src/main/java/com/rutik/systemdesign/ml/case_studies/design_churn_prediction.md) | Temporal CV, GBDT vs survival vs uplift, T-learner, calibration for budgeting, SHAP |
| [Design Credit Risk Scoring](src/main/java/com/rutik/systemdesign/ml/case_studies/design_credit_risk_scoring.md) | WOE/scorecard, monotonic constraints, reject inference, ECOA/FCRA, fairness audit |
| [Design ETA Prediction](src/main/java/com/rutik/systemdesign/ml/case_studies/design_eta_prediction.md) | Quantile regression, cyclic geo features, real-time Flink pipeline, p90 coverage SLO |
| [Design Marketplace Matching](src/main/java/com/rutik/systemdesign/ml/case_studies/design_marketplace_matching.md) | Demand/supply forecasting, LambdaRank scoring, Hungarian assignment, switchback A/B |
| [Design Customer LTV Prediction](src/main/java/com/rutik/systemdesign/ml/case_studies/design_customer_ltv_prediction.md) | BG/NBD vs LightGBM Cox survival, censoring correction, cohort CV, bid optimization |
| [Design Multi-Touch Attribution](src/main/java/com/rutik/systemdesign/ml/case_studies/design_multi_touch_attribution.md) | Markov removal effects, Shapley Monte Carlo, SUTVA violations, geo holdout |
| [Design Dynamic Pricing](src/main/java/com/rutik/systemdesign/ml/case_studies/design_dynamic_pricing.md) | Demand elasticity, constrained price optimizer, contextual bandits, price war dampening |
| [Design NLP Classification Pipeline](src/main/java/com/rutik/systemdesign/ml/case_studies/design_nlp_classification_pipeline.md) | TF-IDF+LR → DistilBERT cascade, active learning, knowledge distillation |
| [Design Real-Time Personalization](src/main/java/com/rutik/systemdesign/ml/case_studies/design_real_time_personalization.md) | Session encoder GRU, two-tower + FAISS 50k req/s, exploration, cold-start |
| [Design Semantic Search Engine](src/main/java/com/rutik/systemdesign/ml/case_studies/design_semantic_search_engine.md) | Bi-encoder SBERT, FAISS IVF, RRF hybrid, cross-encoder reranking, hard negative mining |
| [Design NER Pipeline](src/main/java/com/rutik/systemdesign/ml/case_studies/design_ner_pipeline.md) | BERT-CRF, BIO tagging, span extraction, subword alignment, active learning for annotation |
| [Design Question Answering System](src/main/java/com/rutik/systemdesign/ml/case_studies/design_question_answering_system.md) | DPR dual-encoder, BERT-large reader, SQuAD 2.0 null score, multi-hop, hybrid RRF |

See the [ML Master Index](src/main/java/com/rutik/systemdesign/ml/README.md) for the full 8-phase learning path, sub-files index, and LLM/ML non-overlap boundary.

---

### DevOps, Cloud & Platform Engineering — Senior Engineer & Interview Prep Guide

A comprehensive, senior-engineer-level guide to **DevOps, SRE, Cloud, and Platform Engineering** — from Linux/OS internals and container runtimes through the full Kubernetes stack, CI/CD and GitOps, Infrastructure as Code, cloud platforms (AWS-primary, GCP/Azure compared), the observability stack, SRE practice, DevSecOps supply-chain security, and specialized platforms (ML/GPU infrastructure, event-streaming operations, performance/load testing). 41 modules across 8 phases + 13 principal case studies. Deliberately scoped to cross-reference (not duplicate) the `backend/` and `database/` sections.

#### Phase 1 — Foundations
| Module | Key Concepts |
|--------|-------------|
| [Linux & OS Fundamentals](src/main/java/com/rutik/systemdesign/devops/linux_and_os_fundamentals/linux_and_os_fundamentals.md) | Processes, signals, file descriptors, cgroups v2, namespaces, systemd, /proc, ulimits, OOM killer |
| [Shell Scripting & Automation](src/main/java/com/rutik/systemdesign/devops/shell_scripting_and_automation/shell_scripting_and_automation.md) | Bash, `sed`/`awk`/`jq`, Python for ops, idempotent scripts, `set -euo pipefail` |
| [Networking for DevOps](src/main/java/com/rutik/systemdesign/devops/networking_for_devops/networking_for_devops.md) | DNS, CIDR/subnetting, NAT, firewalls, L4/L7 load balancing, TLS/mTLS/certs, Nginx/Envoy |
| [Version Control & Git Workflows](src/main/java/com/rutik/systemdesign/devops/version_control_and_git_workflows/version_control_and_git_workflows.md) | Git internals, trunk-based vs GitFlow, monorepo vs polyrepo, hooks, release tagging |

#### Phase 2 — Containers & Kubernetes
| Module | Key Concepts |
|--------|-------------|
| [Containers & Docker](src/main/java/com/rutik/systemdesign/devops/containers_and_docker/containers_and_docker.md) | Namespaces/cgroups, image layers, multi-stage builds, distroless, BuildKit, registries |
| [Container Runtimes & OCI](src/main/java/com/rutik/systemdesign/devops/container_runtimes_and_oci/container_runtimes_and_oci.md) | containerd, runc, CRI-O, OCI image/runtime spec, gVisor/Kata isolation |
| [Kubernetes Architecture](src/main/java/com/rutik/systemdesign/devops/kubernetes_architecture/kubernetes_architecture.md) | API server, etcd, scheduler, controller-manager, kubelet, kube-proxy, reconciliation loop |
| [Kubernetes Workloads & Objects](src/main/java/com/rutik/systemdesign/devops/kubernetes_workloads_and_objects/kubernetes_workloads_and_objects.md) | Pods, Deployments, StatefulSets, DaemonSets, Jobs/CronJobs, Services, Ingress, ConfigMap/Secret |
| [Kubernetes Networking](src/main/java/com/rutik/systemdesign/devops/kubernetes_networking/kubernetes_networking.md) | CNI (Calico/Cilium/eBPF), kube-proxy modes, Ingress, Gateway API, NetworkPolicy, CoreDNS |
| [Kubernetes Storage & State](src/main/java/com/rutik/systemdesign/devops/kubernetes_storage_and_state/kubernetes_storage_and_state.md) | PV/PVC, StorageClass, CSI, StatefulSet storage, volume snapshots |
| [Kubernetes Scheduling & Autoscaling](src/main/java/com/rutik/systemdesign/devops/kubernetes_scheduling_and_autoscaling/kubernetes_scheduling_and_autoscaling.md) | Affinity/taints/tolerations, requests/limits, QoS, HPA/VPA/KEDA, Cluster Autoscaler/Karpenter, PDB |
| [Kubernetes Security](src/main/java/com/rutik/systemdesign/devops/kubernetes_security/kubernetes_security.md) | RBAC, ServiceAccounts, Pod Security Standards, admission control, secrets-at-rest, image policy |
| [Helm & Package Management](src/main/java/com/rutik/systemdesign/devops/helm_and_package_management/helm_and_package_management.md) | Helm charts/templating/releases, Kustomize overlays, repositories |
| [Kubernetes Operators & CRDs](src/main/java/com/rutik/systemdesign/devops/kubernetes_operators_and_crds/kubernetes_operators_and_crds.md) | CRDs, custom controllers, operator pattern, Operator SDK, reconcile loop |

#### Phase 3 — CI/CD & GitOps
| Module | Key Concepts |
|--------|-------------|
| [CI/CD Fundamentals](src/main/java/com/rutik/systemdesign/devops/ci_cd_fundamentals/ci_cd_fundamentals.md) | Pipeline anatomy, stages, artifacts, caching, parallelism, ephemeral runners |
| [CI/CD Platforms](src/main/java/com/rutik/systemdesign/devops/ci_cd_platforms/ci_cd_platforms.md) | GitHub Actions, GitLab CI, Jenkins, Argo Workflows/Tekton, CircleCI |
| [Deployment Strategies](src/main/java/com/rutik/systemdesign/devops/deployment_strategies/deployment_strategies.md) | Rolling/blue-green/canary, feature flags, progressive delivery (Argo Rollouts/Flagger) |
| [GitOps (ArgoCD & Flux)](src/main/java/com/rutik/systemdesign/devops/gitops_argocd_flux/gitops_argocd_flux.md) | Declarative delivery, ArgoCD, Flux, drift detection, app-of-apps, sync waves |
| [Artifact & Registry Management](src/main/java/com/rutik/systemdesign/devops/artifact_and_registry_management/artifact_and_registry_management.md) | Container/artifact registries, Artifactory/Nexus, SemVer, promotion across envs |

#### Phase 4 — Infrastructure as Code & Config
| Module | Key Concepts |
|--------|-------------|
| [Infrastructure as Code (Terraform)](src/main/java/com/rutik/systemdesign/devops/infrastructure_as_code_terraform/infrastructure_as_code_terraform.md) | Core, state, modules, providers, workspaces, drift, import, remote backends, locking |
| [Terraform Advanced & Alternatives](src/main/java/com/rutik/systemdesign/devops/terraform_advanced_and_alternatives/terraform_advanced_and_alternatives.md) | Terragrunt, Pulumi, CloudFormation/CDK, OpenTofu, Terratest, policy (Sentinel/OPA) |
| [Configuration Management](src/main/java/com/rutik/systemdesign/devops/configuration_management/configuration_management.md) | Ansible/Chef/Puppet/Salt, idempotency, push vs pull, immutable infra, Packer |
| [Secrets Management](src/main/java/com/rutik/systemdesign/devops/secrets_management/secrets_management.md) | HashiCorp Vault, dynamic secrets, AWS/GCP secret managers, External Secrets Operator, SOPS, rotation |

#### Phase 5 — Cloud Platforms (AWS-primary)
| Module | Key Concepts |
|--------|-------------|
| [Cloud Fundamentals & AWS](src/main/java/com/rutik/systemdesign/devops/cloud_fundamentals_and_aws/cloud_fundamentals_and_aws.md) | IAM, VPC, EC2, S3/EBS, ELB/ALB, Route53, RDS, EKS, Well-Architected Framework |
| [GCP & Azure Essentials](src/main/java/com/rutik/systemdesign/devops/gcp_and_azure_essentials/gcp_and_azure_essentials.md) | GKE/GCS/Cloud Run/IAM, AKS/Blob/Entra ID; AWS↔GCP↔Azure mapping |
| [Serverless & FaaS](src/main/java/com/rutik/systemdesign/devops/serverless_and_faas/serverless_and_faas.md) | Lambda/Cloud Functions/Knative, cold starts, event-driven, API Gateway, Step Functions |
| [Cloud Networking & CDN](src/main/java/com/rutik/systemdesign/devops/cloud_networking_and_cdn/cloud_networking_and_cdn.md) | VPC peering, Transit Gateway, PrivateLink, CloudFront/Cloudflare CDN, global LB, DNS |
| [Cloud Cost Optimization (FinOps)](src/main/java/com/rutik/systemdesign/devops/cloud_cost_optimization_finops/cloud_cost_optimization_finops.md) | Tagging, rightsizing, spot/reserved/savings plans, FinOps practice, cost allocation |

#### Phase 6 — Observability & SRE
| Module | Key Concepts |
|--------|-------------|
| [Observability: Metrics & Prometheus](src/main/java/com/rutik/systemdesign/devops/observability_metrics_prometheus/observability_metrics_prometheus.md) | Prometheus architecture, PromQL, exporters, recording/alerting rules, Thanos/Mimir, cardinality |
| [Observability: Logging](src/main/java/com/rutik/systemdesign/devops/observability_logging/observability_logging.md) | Aggregation, EFK/ELK, Loki, structured logging, retention/sampling, parsing pipelines |
| [Observability: Tracing & OTel](src/main/java/com/rutik/systemdesign/devops/observability_tracing_and_otel/observability_tracing_and_otel.md) | OpenTelemetry collector pipelines, Jaeger/Tempo, sampling, span propagation |
| [Visualization & Alerting](src/main/java/com/rutik/systemdesign/devops/visualization_and_alerting/visualization_and_alerting.md) | Grafana dashboards, Alertmanager routing, PagerDuty/OpsGenie, SLO burn-rate alerts |
| [SRE Principles & SLOs](src/main/java/com/rutik/systemdesign/devops/sre_principles_and_slos/sre_principles_and_slos.md) | SLI/SLO/SLA, error budgets, toil, capacity planning, Google SRE practices |
| [Incident Management & On-Call](src/main/java/com/rutik/systemdesign/devops/incident_management_and_oncall/incident_management_and_oncall.md) | Incident command, severity levels, on-call rotations, blameless postmortems, MTTR/MTTD |

#### Phase 7 — DevSecOps & Reliability
| Module | Key Concepts |
|--------|-------------|
| [DevSecOps & Supply Chain Security](src/main/java/com/rutik/systemdesign/devops/devsecops_and_supply_chain_security/devsecops_and_supply_chain_security.md) | SAST/DAST/SCA, image scanning (Trivy/Grype), SBOM, Sigstore/cosign, SLSA levels |
| [Policy as Code & Compliance](src/main/java/com/rutik/systemdesign/devops/policy_as_code_and_compliance/policy_as_code_and_compliance.md) | OPA/Rego, Gatekeeper, Kyverno, CIS benchmarks, SOC2/PCI/HIPAA controls, admission control |
| [Disaster Recovery & Resilience](src/main/java/com/rutik/systemdesign/devops/disaster_recovery_and_resilience/disaster_recovery_and_resilience.md) | RTO/RPO, multi-region DR (active-active/passive), failover, restore drills |
| [Platform Engineering & IDP](src/main/java/com/rutik/systemdesign/devops/platform_engineering_and_idp/platform_engineering_and_idp.md) | Internal developer platforms, Backstage, golden paths, self-service, Crossplane |

#### Phase 8 — Specialized Platforms & Performance
| Module | Key Concepts |
|--------|-------------|
| [ML Platform & GPU Infrastructure](src/main/java/com/rutik/systemdesign/devops/ml_platform_and_gpu_infrastructure/ml_platform_and_gpu_infrastructure.md) | NVIDIA GPU Operator, device plugin, MIG/time-slicing, Karpenter GPU NodePools, Kubeflow/Ray on K8s, training vs serving infra, multi-tenant GPU scheduling |
| [Event Streaming Operations](src/main/java/com/rutik/systemdesign/devops/event_streaming_operations/event_streaming_operations.md) | Strimzi/Kafka operator, partition & disk sizing, consumer-lag monitoring, rebalancing, rack awareness, KRaft, tiered storage |
| [Performance & Load Testing](src/main/java/com/rutik/systemdesign/devops/performance_and_load_testing/performance_and_load_testing.md) | k6, Locust, distributed load generation, soak/spike/capacity tests, percentile latency, coordinated omission, CI performance gates |

#### DevOps Case Studies
| Case Study | Core Concepts |
|------------|--------------|
| [Design a CI/CD Platform](src/main/java/com/rutik/systemdesign/devops/case_studies/design_ci_cd_platform.md) | Multi-tenant CI/CD at scale, ephemeral runners, distributed artifact caching, pipeline isolation |
| [Design a Kubernetes Platform](src/main/java/com/rutik/systemdesign/devops/case_studies/design_kubernetes_platform.md) | Multi-cluster, multi-tenant K8s platform, control-plane scaling, admission policy, Karpenter |
| [Design an Observability Platform](src/main/java/com/rutik/systemdesign/devops/case_studies/design_observability_platform.md) | Metrics+logs+traces at scale (Prometheus/Thanos + Loki + Tempo), cardinality, retention tiers |
| [Design a GitOps Delivery Pipeline](src/main/java/com/rutik/systemdesign/devops/case_studies/design_gitops_delivery_pipeline.md) | ArgoCD app-of-apps, canary via Argo Rollouts, metric-gated promotion, automated rollback |
| [Design a Secrets Management Platform](src/main/java/com/rutik/systemdesign/devops/case_studies/design_secrets_management_platform.md) | Vault dynamic secrets, External Secrets Operator, rotation, lease/revocation, audit |
| [Design Multi-Region DR](src/main/java/com/rutik/systemdesign/devops/case_studies/design_multi_region_dr_architecture.md) | RTO/RPO targets, active-active vs active-passive, failover automation, restore drills |
| [Design an Autoscaling Platform](src/main/java/com/rutik/systemdesign/devops/case_studies/design_autoscaling_platform.md) | HPA/VPA/KEDA, Karpenter consolidation, scale-to-zero, cost-aware elasticity |
| [Design a Log Aggregation Pipeline](src/main/java/com/rutik/systemdesign/devops/case_studies/design_log_aggregation_pipeline.md) | High-volume ingestion, backpressure, parsing pipelines, hot/warm/cold retention |
| [Design an Internal Developer Platform](src/main/java/com/rutik/systemdesign/devops/case_studies/design_internal_developer_platform.md) | Backstage golden paths, Crossplane infra abstraction, paved-road self-service |
| [Design an Incident Response System](src/main/java/com/rutik/systemdesign/devops/case_studies/design_incident_response_system.md) | Alert routing, on-call escalation, SLO burn-rate alerting, postmortem workflow |
| [Design a Container Registry](src/main/java/com/rutik/systemdesign/devops/case_studies/design_container_registry.md) | Image scanning, cosign signing, promotion across environments, admission enforcement |
| [Design a Zero-Downtime Infra Migration](src/main/java/com/rutik/systemdesign/devops/case_studies/design_zero_downtime_infra_migration.md) | Strangler-fig infra migration, dual-run, traffic shifting, rollback safety |
| [Design an ML Platform Infrastructure](src/main/java/com/rutik/systemdesign/devops/case_studies/design_ml_platform_infrastructure.md) | Multi-tenant GPU scheduling (Kueue gang/quota/borrowing), MIG vs time-slicing, Karpenter Spot/On-Demand pools, KServe scale-to-zero, 22%→65% utilization |

See the [DevOps Master Index](src/main/java/com/rutik/systemdesign/devops/README.md) for the full 8-phase learning path, AWS↔GCP↔Azure mapping, cross-reference map, non-overlap boundary, and the build-status tracker.

---

### GPGPU & CUDA Programming — Senior Engineer & Interview Prep Guide

A comprehensive, senior-engineer-level guide to **general-purpose GPU programming with CUDA**, from the **kernel author's** viewpoint — from the SIMT execution model and GPU hardware architecture through the core CUDA programming model, the performance-engineering discipline that dominates senior GPU interviews (coalescing, shared-memory tiling, occupancy, warp primitives), advanced execution (streams, CUDA graphs, multi-GPU/NCCL), the library and Tensor-Core ecosystem, and the profiling/debugging/portability toolchain. 24 modules across 6 phases + 6 principal case studies. Code is dual CUDA C++ + Python (CuPy/Numba/Triton/PyTorch). Deliberately scoped to cross-reference (not duplicate) the GPU material in `ml/`, `llm/`, and `devops/`.

#### Phase 1 — GPU Foundations
| Module | Key Concepts |
|--------|-------------|
| [GPU Computing Foundations](src/main/java/com/rutik/systemdesign/cuda/gpu_computing_foundations/gpu_computing_foundations.md) | Throughput vs latency, SIMT vs SIMD, Amdahl/Gustafson, host/device model, PCIe vs NVLink, when the GPU wins |
| [GPU Hardware Architecture](src/main/java/com/rutik/systemdesign/cuda/gpu_hardware_architecture/gpu_hardware_architecture.md) | SM anatomy, CUDA cores, warp schedulers, register file, L1/L2/HBM, generations (Volta→Blackwell), compute capability, Tensor Cores |
| [CUDA Toolkit & Compilation](src/main/java/com/rutik/systemdesign/cuda/cuda_toolkit_and_compilation/cuda_toolkit_and_compilation.md) | nvcc pipeline, PTX vs SASS, fatbin/JIT, `compute_XX`/`sm_XX`, `__CUDA_ARCH__`, driver vs runtime API, nvrtc |

#### Phase 2 — Core CUDA Programming
| Module | Key Concepts |
|--------|-------------|
| [CUDA Programming Model & Kernels](src/main/java/com/rutik/systemdesign/cuda/cuda_programming_model_and_kernels/cuda_programming_model_and_kernels.md) | `<<<grid,block>>>`, thread hierarchy, `threadIdx`/`blockIdx`, 1D/2D/3D indexing, grid-stride loops, function qualifiers |
| [Warps & SIMT Execution](src/main/java/com/rutik/systemdesign/cuda/warps_and_simt_execution/warps_and_simt_execution.md) | warp=32, lockstep, warp scheduling, divergence + predication, `__syncwarp`, active mask, independent thread scheduling |
| [CUDA Memory Model & Hierarchy](src/main/java/com/rutik/systemdesign/cuda/cuda_memory_model_and_hierarchy/cuda_memory_model_and_hierarchy.md) | global/shared/local/constant/texture/register, scope + lifetime, unified virtual addressing, L1/L2/HBM, `__restrict__` |
| [Memory Management & Data Transfer](src/main/java/com/rutik/systemdesign/cuda/memory_management_and_data_transfer/memory_management_and_data_transfer.md) | `cudaMalloc`/`cudaMemcpy`, pinned vs pageable, unified memory (prefetch/advise), zero-copy, async copy, error checking |

#### Phase 3 — Performance Engineering (the interview core)
| Module | Key Concepts |
|--------|-------------|
| [Memory Coalescing & Access Patterns](src/main/java/com/rutik/systemdesign/cuda/memory_coalescing_and_access_patterns/memory_coalescing_and_access_patterns.md) | Coalesced vs strided, 128-byte transactions, alignment, AoS vs SoA, vectorized loads (`float4`), the transpose problem |
| [Shared Memory & Bank Conflicts](src/main/java/com/rutik/systemdesign/cuda/shared_memory_and_bank_conflicts/shared_memory_and_bank_conflicts.md) | Tiling, 32 banks, conflicts + padding, broadcast, dynamic shared memory, shared-mem GEMM tile |
| [Occupancy & Launch Configuration](src/main/java/com/rutik/systemdesign/cuda/occupancy_and_launch_configuration/occupancy_and_launch_configuration.md) | Occupancy, register/shared-mem limits, occupancy calculator, block-size tuning, latency hiding, `__launch_bounds__` |
| [Synchronization & Atomics](src/main/java/com/rutik/systemdesign/cuda/synchronization_and_atomics/synchronization_and_atomics.md) | `__syncthreads`, races, `atomicAdd`/CAS, memory fences, atomic contention, `cuda::atomic`, cooperative groups |
| [Parallel Patterns: Reduction, Scan, Histogram](src/main/java/com/rutik/systemdesign/cuda/parallel_patterns_reduction_scan_histogram/parallel_patterns_reduction_scan_histogram.md) | Reduction ladder, scan/prefix-sum, histogram, the canonical optimization walkthroughs |
| [Warp-Level Primitives & Cooperative Groups](src/main/java/com/rutik/systemdesign/cuda/warp_level_primitives_and_cooperative_groups/warp_level_primitives_and_cooperative_groups.md) | `__shfl_*_sync`, vote/ballot, warp-aggregated atomics, cooperative groups, grid sync, warp reduction |

#### Phase 4 — Advanced Execution & Multi-GPU
| Module | Key Concepts |
|--------|-------------|
| [Streams, Events & Concurrency](src/main/java/com/rutik/systemdesign/cuda/streams_events_and_concurrency/streams_events_and_concurrency.md) | Streams, async, events/timing, overlap compute+transfer, default vs per-thread-default stream, priorities, callbacks |
| [CUDA Graphs](src/main/java/com/rutik/systemdesign/cuda/cuda_graphs/cuda_graphs.md) | Graph capture, instantiate, launch-overhead reduction, graph update, when it wins |
| [Multi-GPU Programming & NCCL](src/main/java/com/rutik/systemdesign/cuda/multi_gpu_programming_and_nccl/multi_gpu_programming_and_nccl.md) | P2P, NVLink/NVSwitch, GPUDirect, device selection, NCCL collectives, data/model decomposition |
| [Dynamic Parallelism & Advanced Kernels](src/main/java/com/rutik/systemdesign/cuda/dynamic_parallelism_and_advanced_kernels/dynamic_parallelism_and_advanced_kernels.md) | Device-side launch, nested parallelism, persistent kernels, producer-consumer, when it helps vs hurts |

#### Phase 5 — Libraries, Tensor Cores & Ecosystem
| Module | Key Concepts |
|--------|-------------|
| [Tensor Cores & Mixed Precision](src/main/java/com/rutik/systemdesign/cuda/tensor_cores_and_mixed_precision/tensor_cores_and_mixed_precision.md) | WMMA/`mma`, FP16/BF16/TF32/FP8, matrix fragments, loss scaling, cuBLAS/cuDNN TC paths, when TC engages |
| [CUDA Math & DNN Libraries](src/main/java/com/rutik/systemdesign/cuda/cuda_math_and_dnn_libraries/cuda_math_and_dnn_libraries.md) | cuBLAS, cuDNN, CUTLASS, cuFFT, cuSPARSE, cuRAND, Thrust; library-vs-custom-kernel; CUTLASS templating |
| [Python GPU Ecosystem](src/main/java/com/rutik/systemdesign/cuda/python_gpu_ecosystem/python_gpu_ecosystem.md) | CuPy, Numba CUDA, PyCUDA, PyTorch custom CUDA/C++ extensions, `torch.compile`/Inductor, DLPack |
| [Triton & Kernel DSLs](src/main/java/com/rutik/systemdesign/cuda/triton_and_kernel_dsls/triton_and_kernel_dsls.md) | Triton programming model, block-level abstraction, autotuning, Triton vs CUDA C++, where Triton wins/loses |

#### Phase 6 — Profiling, Correctness & Portability
| Module | Key Concepts |
|--------|-------------|
| [Profiling & Performance Analysis](src/main/java/com/rutik/systemdesign/cuda/profiling_and_performance_analysis/profiling_and_performance_analysis.md) | Nsight Systems vs Compute, roofline, achieved occupancy, DRAM throughput, warp-stall reasons, guided analysis |
| [Debugging, Correctness & Numerics](src/main/java/com/rutik/systemdesign/cuda/debugging_correctness_and_numerics/debugging_correctness_and_numerics.md) | cuda-gdb, compute-sanitizer (memcheck/racecheck/synccheck/initcheck), error macros, FP/FMA, determinism, fast-math |
| [GPU Portability: HIP, SYCL & Beyond](src/main/java/com/rutik/systemdesign/cuda/gpu_portability_hip_sycl_and_beyond/gpu_portability_hip_sycl_and_beyond.md) | HIP/ROCm, SYCL/oneAPI, Metal, WebGPU, legacy OpenCL, `hipify`, portability-vs-peak-performance tradeoff |

#### CUDA Case Studies
| Case Study | What It Covers |
|------------|----------------|
| [Optimize a Matrix Multiplication Kernel](src/main/java/com/rutik/systemdesign/cuda/case_studies/optimize_matrix_multiplication_kernel.md) | The full GEMM ladder: naive → coalesced → shared-mem tiled → register-blocked → Tensor Core, with roofline at each rung |
| [Implement a High-Performance Reduction](src/main/java/com/rutik/systemdesign/cuda/case_studies/implement_high_performance_reduction.md) | The 7-rung reduction ladder from divergent addressing to warp-shuffle and cooperative-groups grid reduction |
| [Build a Flash Attention Kernel](src/main/java/com/rutik/systemdesign/cuda/case_studies/build_a_flash_attention_kernel.md) | Fused softmax-attention, online softmax, shared-memory tiling — the "why fuse to avoid HBM round-trips" argument |
| [Accelerate 2D Convolution & Stencil](src/main/java/com/rutik/systemdesign/cuda/case_studies/accelerate_2d_convolution_and_stencil.md) | Shared-memory tiling with halo regions, separable filters, constant memory |
| [Port a CPU Pipeline to GPU](src/main/java/com/rutik/systemdesign/cuda/case_studies/port_a_cpu_pipeline_to_gpu.md) | End-to-end methodology: profile → Amdahl budget → incremental port → transfer overlap → verify numerics |
| [Optimize LLM Inference Kernels](src/main/java/com/rutik/systemdesign/cuda/case_studies/optimize_llm_inference_kernels.md) | GEMV/attention/KV-cache kernels, INT8/FP8 matmul, fusion — the kernel-level twin of the LLM inference platform |

See the [CUDA Master Index](src/main/java/com/rutik/systemdesign/cuda/README.md) for the full 6-phase learning path, the Full/Interview learning paths, the GPU architecture & compute-capability reference, the cross-reference map, and the build-status tracker.

---

### CS Fundamentals — Senior Engineer & Interview Prep Guide

The language-agnostic computer-science spine: 24 modules across 6 phases + 6 interview-problem walkthrough case studies + the DSA pattern playbooks topic (25 Study topics in the game). Teaches DS&A, operating systems, computer architecture, systems foundations, and cryptography at the CS-theory level, with explicit crosslinks to the deep applied treatments in `java/`, `python/`, `backend/`, `database/`, and `devops/`.

#### Phase 1 — Complexity & Computation

| Module | Key Concepts |
|--------|-------------|
| [complexity_analysis_and_big_o](src/main/java/com/rutik/systemdesign/cs_fundamentals/complexity_analysis_and_big_o/complexity_analysis_and_big_o.md) | Big-O/Θ/Ω, amortized analysis, Master theorem, recurrences |
| [number_systems_and_bit_manipulation](src/main/java/com/rutik/systemdesign/cs_fundamentals/number_systems_and_bit_manipulation/number_systems_and_bit_manipulation.md) | Binary/hex, two's complement, IEEE-754, bitwise ops and tricks, endianness |
| [recursion_and_problem_solving_patterns](src/main/java/com/rutik/systemdesign/cs_fundamentals/recursion_and_problem_solving_patterns/recursion_and_problem_solving_patterns.md) | Call stack, backtracking, two-pointer, sliding window, divide-and-conquer framing |
| [discrete_math_for_engineers](src/main/java/com/rutik/systemdesign/cs_fundamentals/discrete_math_for_engineers/discrete_math_for_engineers.md) | Propositional/predicate logic, sets/relations/functions, induction, combinatorics, recurrences (Master Theorem), probability, modular arithmetic |

#### Phase 2 — Data Structures

| Module | Key Concepts |
|--------|-------------|
| [arrays_strings_and_hashing](src/main/java/com/rutik/systemdesign/cs_fundamentals/arrays_strings_and_hashing/arrays_strings_and_hashing.md) | Dynamic arrays (1.5–2× growth), hash tables (chaining/open addressing, load factor 0.75), sets |
| [linked_lists_stacks_and_queues](src/main/java/com/rutik/systemdesign/cs_fundamentals/linked_lists_stacks_and_queues/linked_lists_stacks_and_queues.md) | Singly/doubly lists, stacks, queues, deques, monotonic stack/queue, circular buffers |
| [trees_and_binary_search_trees](src/main/java/com/rutik/systemdesign/cs_fundamentals/trees_and_binary_search_trees/trees_and_binary_search_trees.md) | Binary tree traversals, BST, AVL/red-black (concept), B/B+ trees (concept), trie |
| [heaps_and_priority_queues](src/main/java/com/rutik/systemdesign/cs_fundamentals/heaps_and_priority_queues/heaps_and_priority_queues.md) | Binary heap, heapify O(n), d-ary heaps, extract-min O(log n), k-way merge |
| [graphs_tries_and_advanced_structures](src/main/java/com/rutik/systemdesign/cs_fundamentals/graphs_tries_and_advanced_structures/graphs_tries_and_advanced_structures.md) | Graph representations, trie, union-find/DSU (path compression + union by rank), segment tree, Fenwick tree, Bloom filter |

#### Phase 3 — Algorithms

| Module | Key Concepts |
|--------|-------------|
| [sorting_and_searching](src/main/java/com/rutik/systemdesign/cs_fundamentals/sorting_and_searching/sorting_and_searching.md) | Merge/quick/heap sort, counting/radix sort, binary search variants |
| [dynamic_programming](src/main/java/com/rutik/systemdesign/cs_fundamentals/dynamic_programming/dynamic_programming.md) | Memoisation vs tabulation, knapsack, LCS, edit distance, coin change |
| [greedy_and_divide_and_conquer](src/main/java/com/rutik/systemdesign/cs_fundamentals/greedy_and_divide_and_conquer/greedy_and_divide_and_conquer.md) | Exchange argument, interval scheduling, Huffman coding, D&C recurrences |
| [graph_and_string_algorithms](src/main/java/com/rutik/systemdesign/cs_fundamentals/graph_and_string_algorithms/graph_and_string_algorithms.md) | BFS/DFS, Dijkstra, Bellman-Ford, Kruskal/Prim, KMP, Rabin-Karp, Z-algorithm |

#### Phase 4 — Operating Systems

| Module | Key Concepts |
|--------|-------------|
| [processes_threads_and_context_switching](src/main/java/com/rutik/systemdesign/cs_fundamentals/processes_threads_and_context_switching/processes_threads_and_context_switching.md) | Process vs thread, address spaces, user/kernel mode, context switch cost ~1–10 µs |
| [cpu_scheduling_algorithms](src/main/java/com/rutik/systemdesign/cs_fundamentals/cpu_scheduling_algorithms/cpu_scheduling_algorithms.md) | FCFS, SJF, Round-Robin, MLFQ, preemption, starvation, CFS concept |
| [memory_management_and_virtual_memory](src/main/java/com/rutik/systemdesign/cs_fundamentals/memory_management_and_virtual_memory/memory_management_and_virtual_memory.md) | Paging (4 KB pages), TLB, page faults, page-replacement (OPT/LRU/Clock) |
| [deadlocks_and_synchronization](src/main/java/com/rutik/systemdesign/cs_fundamentals/deadlocks_and_synchronization/deadlocks_and_synchronization.md) | Mutex/semaphore/monitor, Coffman conditions, prevention/avoidance/detection, dining philosophers |

#### Phase 5 — Systems & Security Foundations

| Module | Key Concepts |
|--------|-------------|
| [computer_architecture_and_memory_hierarchy](src/main/java/com/rutik/systemdesign/cs_fundamentals/computer_architecture_and_memory_hierarchy/computer_architecture_and_memory_hierarchy.md) | CPU pipeline, branch prediction, L1/L2/L3 cache (64 B line), false sharing, NUMA |
| [networking_fundamentals](src/main/java/com/rutik/systemdesign/cs_fundamentals/networking_fundamentals/networking_fundamentals.md) | OSI/TCP-IP primer, TCP vs UDP, DNS resolution, TLS handshake concept |
| [database_and_storage_fundamentals](src/main/java/com/rutik/systemdesign/cs_fundamentals/database_and_storage_fundamentals/database_and_storage_fundamentals.md) | ACID, isolation levels, indexing concept, storage hierarchy (SSD vs HDD latency) |
| [cryptography_fundamentals](src/main/java/com/rutik/systemdesign/cs_fundamentals/cryptography_fundamentals/cryptography_fundamentals.md) | Hash functions, symmetric/asymmetric encryption, HMAC, digital signatures, Diffie-Hellman, salting |
| [character_encoding_deep_dive](src/main/java/com/rutik/systemdesign/cs_fundamentals/character_encoding_deep_dive/character_encoding_deep_dive.md) | Unicode code points/planes, UTF-8/16/32, surrogate pairs, BOM, normalization (NFC/NFD/NFKC/NFKD), grapheme clusters, mojibake |
| [theory_of_computation](src/main/java/com/rutik/systemdesign/cs_fundamentals/theory_of_computation/theory_of_computation.md) | DFA/NFA, regular languages & pumping lemma, CFG/PDA, Turing machines, halting problem, P vs NP, NP-completeness |
| [how_code_runs_compilers_and_interpreters](src/main/java/com/rutik/systemdesign/cs_fundamentals/how_code_runs_compilers_and_interpreters/how_code_runs_compilers_and_interpreters.md) | Lexer/parser/AST, symbol tables, IR & optimization, codegen, compiler vs interpreter, JIT vs AOT, linker/loader, ELF |

#### CS Fundamentals Case Studies (interview-problem walkthroughs)

| Case Study | Core Concepts |
|-----------|--------------|
| [design_lru_cache.md](src/main/java/com/rutik/systemdesign/cs_fundamentals/case_studies/design_lru_cache.md) | HashMap + doubly-linked list, O(1) get/put, eviction policy spectrum |
| [top_k_and_streaming_problems.md](src/main/java/com/rutik/systemdesign/cs_fundamentals/case_studies/top_k_and_streaming_problems.md) | Min-heap top-K, quickselect O(n), count-min sketch, streaming median with two heaps |
| [dynamic_programming_patterns.md](src/main/java/com/rutik/systemdesign/cs_fundamentals/case_studies/dynamic_programming_patterns.md) | Four DP families, memoisation vs tabulation, space optimisation |
| [graph_traversal_and_shortest_path.md](src/main/java/com/rutik/systemdesign/cs_fundamentals/case_studies/graph_traversal_and_shortest_path.md) | BFS/DFS/Dijkstra/topo sort/union-find on canonical interview problems |
| [autocomplete_and_string_search.md](src/main/java/com/rutik/systemdesign/cs_fundamentals/case_studies/autocomplete_and_string_search.md) | Trie + KMP/Rabin-Karp, search-engine and IDE autocomplete |
| [interval_and_scheduling_problems.md](src/main/java/com/rutik/systemdesign/cs_fundamentals/case_studies/interval_and_scheduling_problems.md) | Greedy + sorting + heap: merge intervals, meeting rooms, task scheduler |

#### DSA Pattern Playbooks — L5 Interview Pattern-Recognition Layer

> **New:** After completing Phases 1–3, use [dsa_patterns/](src/main/java/com/rutik/systemdesign/cs_fundamentals/dsa_patterns/dsa_patterns.md) to develop the "fairly certain guess" skill — recognizing which pattern to apply to an unseen problem.

| File | Purpose |
|------|---------|
| [dsa_patterns/README.md](src/main/java/com/rutik/systemdesign/cs_fundamentals/dsa_patterns/dsa_patterns.md) | Recognition engine: UMPIRE method, constraints→complexity table, cue→pattern lookup, decision tree, 25-pattern index |
| [dsa_patterns/interview_execution_playbook.md](src/main/java/com/rutik/systemdesign/cs_fundamentals/dsa_patterns/interview_execution_playbook.md) | L5 rubric, 5-minute opening ritual, communication scripts, "what to say when stuck" |
| [dsa_patterns/study_plans.md](src/main/java/com/rutik/systemdesign/cs_fundamentals/dsa_patterns/study_plans.md) | Blind 75 + NeetCode 150 mapped to patterns with LeetCode links |
| 25 pattern playbooks | Two pointers, sliding window, fast/slow, prefix sum, hashing, cyclic sort, monotonic stack, LL reversal, merge intervals, binary search, top-K, k-way merge, two heaps, tree BFS/DFS, graph traversal, topo sort, union-find, trie, shortest path, backtracking, DP, greedy, bit manipulation, matrix traversal |

See the [CS Fundamentals Master Index](src/main/java/com/rutik/systemdesign/cs_fundamentals/README.md) for the full 6-phase learning path, scope & non-overlap boundary, cross-reference map, and build-status tracker.

---

### Technologies — Per-Technology Deep Dives

A canonical, senior-engineer-level module per major infrastructure technology — architecture, internals with real configs, operations, and when NOT to reach for it — cross-linked back into the concept sections (`backend/`, `llm/`, `ml/`, `devops/`, `cuda/`) that use each technology as a worked example. A technology earns a module here only when no existing section already owns it (Kafka stays in `backend/kafka_deep_dive`; vLLM stays in `llm/vllm_deep_dive`).

| Module | Category | Key Topics |
|--------|----------|-----------|
| [apache_airflow](src/main/java/com/rutik/systemdesign/technologies/apache_airflow/apache_airflow.md) | Workflow Orchestration | Scheduler loop, executors (Local/Celery/Kubernetes), DAGs, deferrable operators, backfills, HA scheduler |
| [temporal_durable_execution](src/main/java/com/rutik/systemdesign/technologies/temporal_durable_execution/temporal_durable_execution.md) | Durable Execution | Event history + replay determinism, the four activity timeouts, signals/queries/updates, Continue-As-New, patching vs Pinned Worker Deployments, immutable `numHistoryShards` |
| [nvidia_triton_inference_server](src/main/java/com/rutik/systemdesign/technologies/nvidia_triton_inference_server/nvidia_triton_inference_server.md) | GPU Model Serving | Model repository + `config.pbtxt`, backends, dynamic batching, ensembles/BLS, `perf_analyzer` |
| [intel_openvino](src/main/java/com/rutik/systemdesign/technologies/intel_openvino/intel_openvino.md) | CPU/Edge Inference & Optimization | IR + `ovc`/`convert_model`, device plugins (CPU/GPU/NPU), AUTO/HETERO + performance hints, async infer requests, NNCF INT8/INT4, model caching, OVMS, `openvino-genai` |
| [envoy_proxy](src/main/java/com/rutik/systemdesign/technologies/envoy_proxy/envoy_proxy.md) | L7 Proxy & Service-Mesh Data Plane | Listener/filter-chain/route/cluster/endpoint model, xDS + ADS ordering, LB policies and locality/priority/panic mode, the `enforcing_*` outlier trap, circuit breaking as five ceilings, retry budgets, the seven-layer timeout stack, `%RESPONSE_FLAGS%`, Wasm/Lua/ext_authz/ext_proc |
| [hashicorp_vault](src/main/java/com/rutik/systemdesign/technologies/hashicorp_vault/hashicorp_vault.md) | Secrets Management | Barrier + four-layer key hierarchy, seal/unseal and why recovery keys cannot unseal, Integrated Storage, every secrets engine, the KV v2 `data/` policy trap, leases and the lease-count arithmetic, secret-zero and auth methods, `bound_claims` vs `claim_mappings`, policies + identity, response wrapping, audit refusal, Agent/VSO/CSI, quotas, rekey vs rotate, the OpenBao delta |
| [debezium_change_data_capture](src/main/java/com/rutik/systemdesign/technologies/debezium_change_data_capture/debezium_change_data_capture.md) | Change Data Capture | Logical decoding and output plugins, what a replication slot pins (`restart_lsn`, `confirmed_flush_lsn`, `catalog_xmin`), `flush.lsn.source` and the idle-captured-table heartbeat trap, `REPLICA IDENTITY` and `before: null`, TOAST and `__debezium_unavailable_value`, snapshot modes and their removals, the incremental-snapshot watermark algorithm, the four-quadrant position-loss recovery table, the never-compact schema-history topic, ordering per key / per table / never across tables, the five tombstone modes, the `EventRouter` defaults, why `tasks.max` is ignored |

See the [Technologies Master Index](src/main/java/com/rutik/systemdesign/technologies/README.md) for the learning path, Knowledge-Question Map, and Study Plan.

---

### Book Summaries — Read the Section, Skip the Book

In-depth, chapter-by-chapter summaries of foundational engineering books, written so that reading the section is as close as possible to reading the book — then cross-linked back into the topic deep dives (`database/`, `hld/`, `backend/`). Organized by book (one folder per book; one sub-folder per chapter), using a book-faithful chapter template that mirrors each chapter's own section order so nothing is dropped.

| Book | Author | Chapters | Folder |
|------|--------|----------|--------|
| [Designing Data-Intensive Applications](src/main/java/com/rutik/systemdesign/book/designing_data_intensive_applications/designing_data_intensive_applications.md) | Martin Kleppmann | 12 (+ preface) | `book/designing_data_intensive_applications/` |
| [System Design Interview — Vol 1](src/main/java/com/rutik/systemdesign/book/system_design_interview_vol_1/system_design_interview_vol_1.md) | Alex Xu | 16 | `book/system_design_interview_vol_1/` |
| [System Design Interview — Vol 2](src/main/java/com/rutik/systemdesign/book/system_design_interview_vol_2/system_design_interview_vol_2.md) | Alex Xu & Sahn Lam | 13 | `book/system_design_interview_vol_2/` |
| [Machine Learning System Design Interview](src/main/java/com/rutik/systemdesign/book/machine_learning_system_design_interview/machine_learning_system_design_interview.md) | Ali Aminian & Alex Xu | 11 | `book/machine_learning_system_design_interview/` |
| [Designing Machine Learning Systems](src/main/java/com/rutik/systemdesign/book/designing_machine_learning_systems/designing_machine_learning_systems.md) | Chip Huyen | 11 | `book/designing_machine_learning_systems/` |
| [Understanding Distributed Systems](src/main/java/com/rutik/systemdesign/book/understanding_distributed_systems/understanding_distributed_systems.md) | Roberto Vitillo | 33 (as 5 parts) | `book/understanding_distributed_systems/` |

Each chapter covers the book's sections in depth with Mermaid/ASCII diagrams, a key-concept glossary, tradeoff tables, production pitfalls, and 15+ interview Q&As. See the [Book Section Index](src/main/java/com/rutik/systemdesign/book/README.md) for the per-book master indexes (part maps, chapter tables, and recommended reading paths).

---

## How to Use This Repository

### By role

| You are... | Start here |
|------------|-----------|
| **Java/Spring backend engineer** | `java/` phases 1-4 → `spring/` phases 1-5 → `hld/` case studies |
| **Python/FastAPI engineer** | `python/` phases 1-3 (language + async) → `fastapi/` phases 1-3 (ASGI stack) → FastAPI case studies |
| **ML engineer** | `ml/` phases 1-3 (math + classical) → phase 5 (ML systems) → ML case studies |
| **LLM/AI engineer** | `llm/foundations_and_architecture/` → `llm/rag_fundamentals/` → `llm/agents_and_tool_use/` → LLM case studies |
| **System design interview** | `hld/` core concepts → `lld/` design patterns → section-specific case studies |
| **Database/backend infra** | `database/` phases 1-3 → `backend/` phases 1-4 → respective case studies |
| **DevOps / SRE / Platform engineer** | `devops/` phase 1-2 (Linux → containers → Kubernetes) → phase 3-4 (CI/CD, IaC) → phase 6 (Observability, SRE) → DevOps case studies |
| **Algorithm / coding interview prep** | `cs_fundamentals/` phase 1 (Big-O) → phase 2 (data structures) → phase 3 (algorithms) → `cs_fundamentals/case_studies/` for walkthroughs |
| **Senior AI + Java engineer** | `java/` phases 1-4 → `spring/spring_ai/` → `llm/foundations_and_architecture/` → `llm/agents_and_tool_use/` → `llm/` case studies; cross-reference `hld/consensus_algorithms/` + `ml/neural_network_fundamentals/` |

### Navigation

- Every section has a **master index** (`README.md`) with a phase diagram and cross-reference map
- Every section has a **`CLAUDE.md`** with its module list, planned additions, cross-reference map, and section-specific authoring rules — used by Claude Code when editing that section
- Every `case_studies/` directory has a **learning path** (`case_studies/README.md`) showing reading order
- All modules follow the **14-section template**: §2 Intuition, §6 Mechanics, §12 Interview Q&As — same structure everywhere
- **Cross-links** connect related concepts across sections (e.g., SQLAlchemy pooling → `database/connection_pool_management`, FastAPI WebSockets → `backend/websockets_and_sse`)

### Content format

Each module README contains: concept overview, intuition analogy, core principles, architecture diagrams (ASCII), detailed mechanics with real code, real-world examples, tradeoff tables, pitfalls with BROKEN→FIX examples, 10+ interview Q&As, best practices, and a capstone case study.

## Quick Reference: Pattern Selection Flowchart

```
Need to create objects?
  |-> Need exactly one instance? -> Singleton
  |-> Need to create families of related objects? -> Abstract Factory
  |-> Need to create one of several related objects? -> Factory Method
  |-> Need to construct complex objects step by step? -> Builder
  |-> Need to copy existing objects? -> Prototype

Need to compose objects?
  |-> Need to make incompatible things work together? -> Adapter
  |-> Need to separate abstraction from implementation? -> Bridge
  |-> Need tree structures? -> Composite
  |-> Need to add responsibilities dynamically? -> Decorator
  |-> Need a simple interface to a complex system? -> Facade
  |-> Need to share objects to save memory? -> Flyweight
  |-> Need to control access to an object? -> Proxy

Need to manage communication?
  |-> Need to pass requests through a pipeline? -> Chain of Responsibility
  |-> Need to parameterize/queue/undo operations? -> Command
  |-> Need to traverse a collection? -> Iterator
  |-> Need to reduce coupling between components? -> Mediator
  |-> Need to save/restore state? -> Memento
  |-> Need to notify objects about events? -> Observer
  |-> Need to change behavior based on state? -> State
  |-> Need to swap algorithms at runtime? -> Strategy
  |-> Need to define algorithm skeleton, defer steps? -> Template Method
  |-> Need to add operations to object structures? -> Visitor
  |-> Need to evaluate language/expressions? -> Interpreter
```

## Contributing

Feel free to add more patterns, examples, or improve existing documentation. Ensure each addition follows the established format.
