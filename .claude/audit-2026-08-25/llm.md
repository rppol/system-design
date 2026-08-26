# LLM Audit — 2026-08-25

## 0. Snapshot

`llm/` is the flagship section and, measured against its own contract, it is in very good
shape: **zero files below the 15-Q&A floor**, zero sub-files orphaned from their parent page,
`extract.py --strict` green, and the module pages are startlingly current — `faiss_deep_dive`
pins FAISS 1.15.0 with a 2026-08-03 wheel date, `vllm_deep_dive` pins v0.26.0 (2026-07-25) and
enumerates its real quantization registry, `agent_evaluation_and_benchmarking` carries
leaderboard figures dated 2026-07-29 including τ³-bench and OSWorld 2.0, and
`cross_cutting/opentelemetry_for_llm_apps` already knows the GenAI semantic conventions moved
to their own repo. The 2026-08-04 audit did real work here.

The single biggest weakness is **structural, not factual: staleness has pooled almost entirely in
the tier-`-` deep-dive sub-files, and nobody looks at them because they are on no curated path.**
**Seven `agentic_frameworks/` files declare a 2024 vintage on line 11 or 12** — `langgraph 0.1.x
(2024)` against a live 1.2.11, `dspy-ai 2.4.x` against `dspy` 3.3.1, `haystack-ai 2.x (2024)`
against 3.1.0, `crewai 0.60.x` against 1.x, `framework_observability.md`'s literal "**Current
landscape (2024)**", plus AutoGen and Semantic Kernel, which merged into Microsoft Agent Framework
1.0 in April 2026 and are taught here as two live, separate products. That is ~5,000 lines of the
most-interviewed material in the domain, frozen two years back, sitting beside a
`langchain_and_lcel.md` that was correctly refreshed to 1.3.x. The second-biggest weakness is the
same shape one level up: the 13 Wave-1 case studies never adopted the 11-section principal template
(they run 10–21 differently-named sections, missing Scale Estimation, Real-World Implementations,
Technologies & Tools and Capacity Planning) and 9 of them fall under the 4+ `cross_cutting/` link
bar, two with zero. And `coding_agents` — the topic every 2026 applied-AI interview opens with —
is in neither study tier.

| Metric | Value |
|---|---|
| Modules | 54 |
| Case studies | 29 (+ 10 `cross_cutting/` sub-files) |
| Deep-dive sub-files | 75 module + 10 cross-cutting = 85 |
| Total files (excl. CLAUDE.md) | 130 content + README |
| Total lines | 168,210 |
| Q&As in the quiz bank | 2,161 (largest section; next is `ml` at 1,574) |
| Raw `**Q` lines across the section | 2,676 |
| Avg lines / module page | ~1,150 |
| Files below the 15-Q&A floor | **0** |
| Tiers | Senior 32 modules · Principal 30 · neither 15 |
| Case studies below the 4+ `cross_cutting/` link bar | 9 of 29 (two at zero) |

---

## 1. Missing — proposed ADDITIONS

| Proposed file path | Type | Why now (cited) | Pri | Est. lines |
|---|---|---|---|---|
| `agents_and_tool_use/agent_skills_and_progressive_disclosure.md` | sub-file | Anthropic published the **Agent Skills** spec 2025-12-18; within 48h Microsoft (VS Code) and OpenAI (ChatGPT + Codex CLI) shipped it, and by March 2026 **32 tools** implement it including Gemini CLI, JetBrains Junie, AWS Kiro, Block Goose, Cursor. Google ADK shipped a native `SkillToolset`. The section has **zero occurrences** of "Agent Skills", "SKILL.md", or progressive capability disclosure. https://www.paperclipped.de/en/blog/agent-skills-open-standard-interoperability/ · https://agentman.ai/blog/agent-skills-ecosystem-report-2026 | **P0** | 700 |
| `agentic_frameworks/microsoft_agent_framework.md` (replacing `autogen.md` + `semantic_kernel.md`) | sub-file (merge) | **Agent Framework 1.0 shipped 2026-04-03**, unifying Semantic Kernel with AutoGen's orchestration model; both existing files are pinned to 2024 and teach the two as live separate products. https://learn.microsoft.com/en-us/agent-framework/overview/ · https://alexbevi.com/blog/2026/06/18/two-lineages-one-framework-how-autogen-and-semantic-kernel-became-the-microsoft-agent-framework/ | **P0** | 750 (net −700) |
| `alignment_and_rlhf/rl_environments_and_agentic_rl.md` | sub-file | The 2026 bottleneck in agentic RL is **the environment, not the optimizer** — "the rate at which your environment can generate verified rollouts determines training wall-clock time more than your GPU count". Prime Intellect's Environments Hub + `verifiers` + `prime-rl` (1000+ GPU scale) is a whole tooling layer the section never names (0 hits for "Prime Intellect", "RL environment" outside the robotics file, "reinforcement fine-tuning"). `grpo_and_rlvr.md` covers the algorithm and stops. https://www.primeintellect.ai/blog/environments · https://www.spheron.network/blog/rl-environments-gpu-cloud-gymnasium-prime-intellect-verifiers/ | **P0** | 800 |
| `case_studies/design_agent_platform.md` | case study | Job specs for 2026 applied-AI roles centre on "multi-agent orchestration, evals and observability, cost optimization, tool calling design, memory architecture, prompt injection defense, OpenTelemetry tracing" as one system. There are 29 case studies and none designs the *internal agent platform* (skill/tool registry, sandbox pool, durable runtime, per-tenant budget, trace store) that every one of those bullets lives in. https://www.ayautomate.com/blog/ai-engineer-skills-2026 | P1 | 1,000 |
| `knowledge_distillation_and_model_merging/reasoning_distillation.md` | sub-file | The file covers logit/feature/sequence distillation and merging but never **CoT/reasoning-trace distillation** (the R1-distill family), which is the dominant 2025–26 distillation pattern and the reason most small reasoning models exist. | P1 | 600 |
| `foundations_and_architecture/optimizers_and_hyperparameter_transfer.md` | sub-file | `training_dynamics.md` covers AdamW/LAMB/LARS and stops. **Zero hits for "Muon"** anywhere in the section, and muP/hyperparameter transfer is name-checked without derivation. Both are now standard in frontier pretraining reports. | P1 | 650 |
| `inference_engines/serving_fleet_orchestration.md` | sub-file | `llm-d` has **zero hits** section-wide; NVIDIA Dynamo 1.0 went GA 2026-03-16 and is mentioned but never given a mechanism section; prefix-cache-aware *fleet* routing (as opposed to in-engine RadixAttention) is absent. Dynamo's AIConfigurator (SLA→topology for vLLM/SGLang/TRT-LLM) is exactly a principal-tier judgment tool. https://docs.dynamo.nvidia.com/dynamo/design-docs/disaggregated-serving | P1 | 700 |
| `agents_and_tool_use/reasoning_models_vs_scaffolds.md` | sub-file | The four-file reasoning cluster (ReAct / plan-and-execute / ToT / Reflexion) never confronts the 2026 question an interviewer actually asks: *when does a native extended-thinking model subsume your hand-rolled Thought/Action loop, and when does it not?* Flagged independently by the agents-cluster profile. | P2 | 550 |
| `llm_security/agent_identity_and_authorization.md` | sub-file | Three different auth flows are taught in three files with no unifying page — MCP's OAuth 2.1 + PKCE + RFC 9728 + RFC 8707, A2A's bare JWT (`aud`-scoped, **no RFC 8707 binding**), and agentic-commerce mandates. A 2026 principal is asked "how does an agent prove who it is across trust boundaries" as one question. | P2 | 600 |
| `small_language_models_and_edge_ai/on_device_runtimes.md` | sub-file | Apple **MLX** is never named in this file; no QAT-for-SLM (only PTQ via GGUF/AWQ); no llama.cpp/GGUF version pins. | P2 | 500 |

**Gaps better filled as sections inside existing files than as new files** (cheaper, and the host module already exists):

| Host file | What to add | Why | Pri |
|---|---|---|---|
| `token_economics_and_cost_optimization` + `llm_routing_and_model_selection` | **Reasoning/extended-thinking token economics**, and reasoning-mode-aware routing (cheap query → non-thinking mode; hard query → extended thinking) | Both files are otherwise the best-maintained in the operate phase, and both are silent on the single largest new cost variable of 2025–26. Routing today is taught purely as model-tier selection; the 2026 version has a second axis. | **P0-ish** |
| `prompt_management_and_promptops` | **Programmatic prompt optimization** — DSPy optimizers, TextGrad, APE-style compilers as a PromptOps stage | The section already ships `agentic_frameworks/dspy.md`; PromptOps treats prompts as hand-written artifacts under version control and never mentions compiling them. | P1 |
| `llm_observability_and_monitoring` | Tail-based sampling at the collector; a hard cross-link to `cross_cutting/opentelemetry_for_llm_apps.md` | The canonical observability module is the section's stalest quantitative file while the cross-cutting file next door is fully current. | P1 |
| `diffusion_language_models` | Post-training for diffusion LMs (the RLHF/DPO equivalent) | The file is otherwise excellent and 2026-current, but alignment is central everywhere else in the section and absent here. | P1 |
| `data_flywheels_and_continuous_learning` | DPO / online-RL from production traces; the agentic flywheel (tool-call failures as training signal) | It teaches the classic thumbs-up→SFT loop and stops at the 2023 state of the art. | P1 |
| `ai_regulations_and_compliance` | EU AI Act **GPAI Code of Practice**; US state law (Colorado AI Act) | Both absent; the module has a GPAI section that stops at the FLOP threshold. | P2 |
| `llm_security/privacy_and_data_governance` | China **PIPL**, India **DPDP Act** | The file frames itself globally and covers only GDPR/CCPA. | P2 |
| `knowledge_distillation_and_model_merging` | Cross-link `gpt-oss` and add **Llama 4 (Scout/Maverick)** to `mixture_of_experts`'s model table | Both already covered elsewhere in the section, just not linked. | P2 |

### What the P0s would contain

**`agent_skills_and_progressive_disclosure.md`** — 1: what a Skill is (a directory + `SKILL.md` with YAML frontmatter) and the problem it solves that MCP does not. 2: MCP connects an agent to *tools and data*; Skills give it *procedure* — the analogy is a driver vs a runbook. 3: progressive disclosure — name+description always loaded, body loaded on match, bundled scripts/resources loaded on demand; the token-budget arithmetic that makes this necessary at 50+ skills. 4: skill vs tool vs prompt vs subagent vs MCP server — a five-way decision table. 5: a flowchart of the three-level load and where the model decides. 6: frontmatter schema, matching/selection mechanics, bundled executable scripts, versioning, precedence when two skills match. 7: the 32 shipping implementations — Claude Code, Codex CLI/ChatGPT, VS Code + Copilot, Gemini CLI, JetBrains Junie, AWS Kiro, Goose, Cursor; Google ADK's `SkillToolset`. 8: skills vs fine-tuning vs long system prompts vs RAG-over-docs, on cost/latency/portability/auditability. 9: use when procedure is stable and reusable across sessions; do not when it is one-shot, or when it needs live data (that is a tool). 10: skill sprawl and description collisions; a skill that silently never matches; unversioned skills drifting from the codebase they describe; skills as an untrusted-code supply chain. 11: the spec, the registries, the linters. 12: 18 Q&As. 13: write descriptions for the *matcher*, not the human; keep bodies under a budget; pin and review skills like dependencies. 14: consolidating 40 team runbooks into 12 skills — what got merged, what regressed.

**`microsoft_agent_framework.md`** — 1: what AF 1.0 is and that it is the single successor to *both* AutoGen and Semantic Kernel. 2: two lineages, one framework — AutoGen's agent abstractions plus SK's enterprise plumbing. 3: agents, threads, middleware, typed workflows. 4: `ChatAgent` vs graph workflows vs the AutoGen-style group chat; .NET and Python parity. 5: a workflow graph diagram. 6: runnable Python — agent construction, tool registration, middleware, checkpointed workflow, OTel telemetry. 7: Azure AI Foundry deployments; who migrated and why. 8: AF vs LangGraph vs OpenAI Agents SDK vs Google ADK. 9: use when you are already on Azure/.NET or on AutoGen/SK; do not for a greenfield Python-only project where LangGraph is thinner. 10: **the migration guide as pitfall material** — what breaks coming from `autogen-agentchat` 0.7.x and from `semantic-kernel` 1.x. 11: packages and versions. 12: 18 Q&As, several framed as migration questions (which the Q&A currency rule explicitly permits). 13: pin, migrate low-risk workloads first, build migration evidence. 14: porting a 6-agent SK pipeline.

**`rl_environments_and_agentic_rl.md`** — 1: why post-training moved from "algorithm work" to "environment work". 2: the optimizer is a small loop around a very expensive `env.step()`. 3: verifiable reward, rollout throughput, environment determinism, reward-hacking surface. 4: single-turn verifier vs multi-turn tool environment vs full sandboxed agent env; the Environments Hub packaging model (wheels + `pyproject.toml`). 5: a rollout-pipeline diagram showing where the GPUs idle. 6: a `verifiers`-shaped environment with a real verifier, plus the throughput arithmetic (rollouts/s × steps → wall-clock). 7: `prime-rl` at 1000+ GPUs, INTELLECT-3, Tulu-3 RLVR, DeepSeek-R1's pipeline. 8: build vs pull an environment; sandbox cost vs fidelity; on-policy vs replay. 9: when RLVR beats SFT and when it is a trap. 10: verifier gaming; environment nondeterminism poisoning advantage estimates; sandbox spend outrunning GPU spend. 11: `verifiers`, `prime-rl`, Environments Hub, veRL, TRL `GRPOTrainer`, E2B/Modal as rollout sandboxes. 12: 18 Q&As. 13: version your environments; log rollout throughput as a first-class metric. 14: building a code-repair environment and the 3 reward exploits found in week one.

---

## 2. Stale — verified CORRECTIONS

| File | Line / heading | What it says | What is true in Aug 2026 | Source |
|---|---|---|---|---|
| `agentic_frameworks/langgraph.md` | L11, L733-735 | "**Current version**: langgraph 0.1.x (2024)"; version history ends "langgraph 0.1.x (mid 2024)" | **langgraph 1.2.11, published 2026-08-11**; 1.0 shipped alongside LangChain 1.0. The whole 1,006-line file — `interrupt()` framing, `TypedDict`-only state, `astream_events(version="v1")` — is written against a two-year-old API, while its sibling `langchain_and_lcel.md` L11 correctly says 1.3.x. | https://pypi.org/project/langgraph/ |
| `agentic_frameworks/dspy.md` | L11 | "**Current version**: dspy-ai 2.4.x (2024)" | **`dspy` 3.3.1, released 2026-08-21.** Package renamed off `dspy-ai`; a major version behind, and the import path in every code block should be re-checked. | https://pypi.org/project/dspy/ |
| `agentic_frameworks/autogen.md` | L11 + whole file | "**Current version**: pyautogen 0.4.x (2024)" | Two errors. `pyautogen` was never the v0.4 package (that is `autogen-agentchat`/`autogen-core`), and **AutoGen's successor, Microsoft Agent Framework 1.0, shipped 2026-04-03**. The section's own `magentic_one_and_autogen_v04.md` L11 already says "GA 1.0 April 2026" — so two files in the same section contradict each other. | https://learn.microsoft.com/en-us/agent-framework/overview/ |
| `agentic_frameworks/semantic_kernel.md` | L11 + whole file | "**Current version**: semantic-kernel 1.x (Python), 1.x (C#) (2024)" | Same merger: Agent Framework is Semantic Kernel's stated successor, built by the same team. Taught here as an independent live product with no successor pointer. | https://atlan.com/know/ai-agent/microsoft/semantic-kernel/ |
| `agentic_frameworks/crewai.md` | L11 | "**Current version**: crewai 0.60.x (2024)" | crewai is on the **1.x line (1.14.3, 2026-04-24)** — a 0.x→1.x boundary, so this is an API-stability claim that is now wrong in both directions. | https://pypi.org/project/crewai/ |
| `agentic_frameworks/haystack.md` | L11 | "**Current version**: haystack-ai 2.x (2024)" | **haystack-ai 3.1.0, released 2026-08-24** — a full major version behind; the 2.x pipeline/component API claims need re-checking against 3.x. | https://pypi.org/project/haystack-ai/ |
| `agentic_frameworks/framework_observability.md` | L12 | "**Current landscape (2024)**: LangSmith is the most integrated tool… OpenTelemetry Semantic Conventions for GenAI are **still stabilizing**" | Self-declared 2024 vintage. The conventions moved to their own repo `open-telemetry/semantic-conventions-genai` at semconv **v1.42.0 on 2026-06-12**, and every `gen_ai.*` attribute still carries a **Development** badge — which the section's own `cross_cutting/opentelemetry_for_llm_apps.md` L19 already states correctly. Two files in one section, contradicting each other on the same spec. | https://john-hodge.com/blog/opentelemetry-genai-semantic-conventions/ |
| `llm_observability_and_monitoring/llm_observability_and_monitoring.md` | L91-96 | `# Per-model pricing table … (list prices as of July 2026 …)` followed by **only** `"gpt-4o": 2.50/10.00`, `"gpt-4o-mini": 0.15/0.60` | A **mislabelled** table — 2024 prices under a July-2026 stamp, which is worse than an undated one because it defeats the section's own dated-anchor discipline. `token_economics_and_cost_optimization.md` L24-26 handles the identical models correctly by tagging them "(legacy, still served)". §10 pitfall 4 (L675) also names the judge as bare "GPT-4". | internal cross-file conflict |
| `data_flywheels_and_continuous_learning/…md` | L477, L731, L742, L978 | "use a strong frontier model (**GPT-4o, Claude**) for the first 3-6 months"; ROI baseline "$50K/month on GPT-4o" | Undated, unversioned frontier naming presented as *current advice*, disconnected from the gpt-5.6 / Opus-5 / Sonnet-5 ladder every other operate-phase file uses. Stalest of the 11 ops/eval files. | internal cross-file conflict |
| `prompt_management_and_promptops/…md` | L385, L504-505; L12 | Prompt-bloat cost example on GPT-4o's $2.50/1M with **no legacy caveat**; "code without version control… unthinkable in **2025**" | Same models, same section, opposite discipline from `token_economics`. | internal cross-file conflict |
| `vision_language_models/vision_language_models.md` | L148-171 | Open-VLM roster ends at LLaVA-1.6, Qwen-VL, InternVL2, MiniCPM-V; closed models are GPT-4V/4o, Gemini 1.5 Pro, Claude 3/3.5 | Roster is 2023–24 vintage: no Qwen2.5-VL, no Llama 4 multimodal, and the closed-model line is three generations behind `llm_ecosystem_and_landscape.md`'s own Opus-5 / Gemini-3.x table. Same for `multimodal_models.md` L99-107 (Gemini 1.5 Pro, Claude 3.5 Sonnet, LLaVA-1.6). | internal cross-file conflict |
| `mcp_model_context_protocol/mcp_model_context_protocol.md` | L17 (and L27, L271, L646, L650, L735) | "unless stated otherwise this module describes `2025-11-25`, the current released revision, with `2026-07-28` in release candidate at the time of writing (July 2026)" | **`2026-07-28` was released as the stable specification on 2026-07-28** — the largest revision since launch. It is no longer an RC. It removes the `initialize` handshake, deletes protocol-level sessions and `Mcp-Session-Id`, replaces held-open SSE with Multi Round-Trip Requests, makes list results cacheable, and adds a formal extensions framework and a 12-month deprecation policy. Every "the release candidate will…" sentence across `mcp_transports_and_jsonrpc.md` (L378-395, L525, L553, L561-565, L646), `mcp_server_building.md` (L302), and `mcp_registries_and_ecosystem.md` (L381) is now a past-tense fact. | https://blog.modelcontextprotocol.io/posts/2026-07-28/ · https://modelcontextprotocol.io/specification/2026-07-28/changelog |
| `ai_regulations_and_compliance/ai_regulations_and_compliance.md` | L26-29 vs **L1041** | **The file contradicts itself.** L1041 correctly records the Digital Omnibus deferring Annex III high-risk to **2027-12-02** and Annex I to **2028-08-02**. But L28 still reads "Aug 2026: High-risk AI system obligations fully apply; EU database for high-risk systems goes live", and so do the §5 risk-tier Mermaid label (L198 "register in EU AI Act database by Aug 2026"), §6 (L335), the compliance-tracker outputs (L397, L1282), and the whole §14 case study "EU AI Act First Wave — Insurance Sector (2026)" whose stated pressure is "database registration opens August 2026" (L790). | One paragraph was updated and six other places were not. Also newer than what L1041 has: the Omnibus **entered into force 2026-07-27** (the file says only "approved by the Council in June 2026"), and what actually applied on 2026-08-02 was Article 50 transparency plus the Article 4 AI-literacy duty, both untouched by the deferral. **Highest-impact single correction in the section** — a learner reading §1, §5, §6 or §14 gets a deadline that does not exist, and a learner reading §12 gets the right one. | https://www.gibsondunn.com/eu-ai-act-omnibus-agreement-postponed-high-risk-deadlines-and-other-key-changes/ · https://compliancehub.wiki/eu-ai-act-august-2-2026-what-actually-applies-article-50-omnibus/ |
| `multi_agent_systems/agent_to_agent_protocols.md` | L10, L52, L60, L82, L93, L754 | "the specification reached **v1.0.0**"; agent card `"version": "1.0.0"` | **A2A v1.0.1 (May 2026)** added an extension mechanism for new data, requirements, RPC methods and state machines. The project passed **150+ organisations** at its one-year mark (April 2026) with production deployments in supply chain, financial services, insurance and IT ops — the file's "seven founding members" framing understates it by an order of magnitude. | https://www.linuxfoundation.org/press/a2a-protocol-surpasses-150-organizations-lands-in-major-cloud-platforms-and-sees-enterprise-production-use-in-first-year |
| `constitutional_ai/constitutional_ai.md` | L394-397 | "Anthropic Claude (all versions): Claude 2, 3, 3.5 Sonnet, Claude 3 Opus" | Roster stops three generations back and **contradicts the same section's own files** (`fine_tuning.md` L821 cites Claude Opus 4.6; `llm_ecosystem_and_landscape.md` L77 lists Opus 5). Cross-file inconsistency, not merely staleness. | internal cross-file conflict |
| `code_generation/code_generation.md` | L422, L458 | "SWE-bench is still challenging (71% with o3 + scaffolding **as of 2025**)"; "The legal risk remains unsettled **as of 2025**" | The section's own `coding_agents.md` L7 states 85–95% on SWE-bench Verified (Fable 5 at 95.0%) and introduces SWE-bench Pro. Two files, one section, 24 points apart on the same benchmark. | internal cross-file conflict |
| `agentic_frameworks/strands_aws.md` | L375 | "**As of 2025**, considered stable for production" | Last un-refreshed "as of 2025" statement in the frameworks cluster. | — |
| `voice_agents/voice_agents.md` | L208; L5 | code sample uses `gpt-4o-realtime-preview-2024-10-01`; "Anthropic **forthcoming**" for native audio | A dated 2024 preview snapshot string shipped as the copy-paste example in a 2026 file. | — |
| `browser_agents_deep_dive/browser_agents_deep_dive.md` | L292-296, L449-451, L979 | Every benchmark figure is WebArena (2023) with "Claude Sonnet 4" | The section's own `agent_evaluation_and_benchmarking.md` L69-76 carries **Online-Mind2Web, OSWorld 2.0 (2026, 108 tasks), BrowseComp (1,266 questions), Gaia2 (2025, 800)** — none of which appear here, and no 2026-era browser product (Operator, Project Mariner) is named. The deep-dive is behind its own parent module. | internal cross-file conflict |
| `vllm_deep_dive/vllm_deep_dive.md` | L46 | "Version this document targets: vLLM v0.26.0 (released 2026-07-25)" | **v0.27.1, 2026-08-11.** Not an error — the file states its pin honestly — but it is a one-release refresh, and the quantization-registry and KV-dtype enumerations are exactly the things a minor bump changes. | https://pypi.org/project/vllm/ |
| `mixture_of_experts/mixture_of_experts.md` | L11-16 model table | Mixtral 8x7B, DeepSeek-V3, Qwen3-235B-A22B, Switch-C | **Llama 4 (Scout/Maverick) is absent** from a table of open MoE flagships, and `gpt-oss` — already covered as an MXFP4 example in `optimization_and_quantization.md` — is not cross-linked here as an MoE model. | internal gap |
| `embeddings_and_similarity_search/embeddings_and_similarity_search.md` | L45-64, L730-735 | Model roster is all-MiniLM, GTE, BGE, E5, `text-embedding-3-*` | Roster and pricing table are 2023–24 vintage; no Voyage, no Cohere embed v4, no Gemini embedding, no 2025–26 MTEB leader. This is the one module page (rather than sub-file) with a visibly frozen product list. | — |

**Verified NOT stale — do not re-flag.** `llm_ecosystem_and_landscape.md` (GPT-5.6 July 2026, Gemini 3.6 Flash July 2026, Claude Opus 5 $5/$25, correct note that OpenAI *raised* flagship price at GPT-5.5 in April 2026); `token_economics_and_cost_optimization.md` (GPT-4o rows are explicitly labelled "legacy, still served" and used deliberately as a fixed arithmetic anchor); `evaluation_and_benchmarks.md` (Claude 3.5 Sonnet figures are sourced citations from that model's card, not current-model claims); `cross_cutting/opentelemetry_for_llm_apps.md` (already knows the conventions split to `semantic-conventions-genai` and that `gen_ai.system` → `gen_ai.provider.name`); `faiss_deep_dive.md` (1.15.0, wheels 2026-08-03); the whole `agents_and_tool_use/` cluster (July-2026 list prices, τ³-bench, Firecracker advisories from Jan and Apr 2026).

---

## 3. Reordering — SEQUENCE and TIER changes

The **sequence** is sound. Phases 1→6 (Foundations → Training → Using → Agents → Serve/Operate/Quality-Gate → Advanced) read correctly, and no module is obviously out of place. I propose **one** sequence change and a set of tier changes.

### The single most important change

**`coding_agents` and `code_generation` are in NEITHER study tier**, and `agentic_frameworks` is **principal-only**. Both invert the section's own definition of the tiers (senior = craft, principal = judgment). Coding agents and agent frameworks are the two most craft-heavy, most-interviewed topics in 2026 applied-AI hiring — the job-spec survey names "multi-agent orchestration" and "tool calling design" as core screen items, and the Wave-2 case study `design_autonomous_swe_agent` is already senior+principal while the module that teaches its mechanics is on no path at all. A learner following the Senior path today can finish 32 modules without ever reading about Cursor, Claude Code, SWE-bench, LangGraph state, or checkpointing.

| Module / file | Current | Proposed | Why |
|---|---|---|---|
| `coding_agents` | neither | **senior** | Most-asked applied-AI topic of 2026; `design_autonomous_swe_agent` (senior) depends on it. |
| `code_generation` | neither | **senior** | FIM, repo-level context, SWE-bench literacy — pure craft. |
| `agentic_frameworks` (module page) | principal | **senior, principal** | Framework choice is judgment (principal) but framework *use* is craft; senior currently gets zero framework coverage. |
| `agentic_frameworks/langgraph.md` | `-` | **senior** | The de-facto agent runtime; checkpointing and interrupt/resume are senior-interview staples. Fix the file first (§2). |
| `agentic_frameworks/langchain_and_lcel.md` | `-` | **senior** | Already refreshed to 1.3.x; the one framework file safe to promote today. |
| `agentic_frameworks/structured_outputs_and_instructor.md` | `-` | **senior** | Structured output is in every production LLM path. |
| `agentic_frameworks/litellm_routing.md` | `-` | **principal** | Provider abstraction / fallback / virtual keys is a judgment call; pairs with `llm_routing_and_model_selection` (principal). |
| `prompt_management_and_promptops` | principal | **senior, principal** | Versioning, registries, eval-gated CI is craft first. |
| `multimodal_models` | neither | **senior** | 2026 applications are multimodal by default; the module page at least should be on the senior path. |
| `agents_and_tool_use/agent_ux_patterns.md` | `-` | **senior** | Streaming, interrupt/resume, approval gates, confidence signalling — named directly in 2026 applied-AI job specs. |
| `training_infrastructure` | neither | **principal** | Build-vs-buy on training capacity is a principal judgment; `pre_training` is already senior. |
| `llm_ops_platforms` | neither | **principal** | Platform selection (MLflow / W&B / LangSmith / Langfuse / Braintrust) is a buy decision, not craft. |
| `agents_and_tool_use/computer_use_and_browser_agents.md` | `-` | **senior** *(only after the merge in §4)* | Post-merge this becomes the single browser/computer-use page. |
| New: `agent_skills_and_progressive_disclosure.md` | — | **senior, principal** | Peer standard to MCP, which is already senior+principal. |
| New: `microsoft_agent_framework.md` | — | `-` | Same tier the two files it replaces had. |
| New: `rl_environments_and_agentic_rl.md` | — | **principal** | Post-training strategy, not day-to-day craft. |

### Proposed `STUDY_ORDER["llm"]`

Only **one** positional move: `mcp_model_context_protocol` currently sits at #21, *after* `agentic_frameworks` and `multi_agent_systems`. MCP is a protocol the frameworks consume; both `agentic_frameworks` and `multi_agent_systems` reference it. Move it to the head of the Agents phase, immediately after `agentic_workflow_patterns`. If the Agent Skills sub-file lands under `agents_and_tool_use` it needs no `STUDY_ORDER` entry.

```js
  llm: [
    "llm/foundations_and_architecture","llm/tokenization_and_embeddings","llm/embeddings_and_similarity_search","llm/faiss_deep_dive",
    "llm/pre_training","llm/training_infrastructure","llm/synthetic_data_generation","llm/fine_tuning","llm/alignment_and_rlhf","llm/constitutional_ai",
    "llm/prompt_engineering","llm/rag_fundamentals","llm/advanced_rag","llm/context_engineering","llm/reasoning_models","llm/code_generation",
    "llm/agents_and_tool_use","llm/agentic_workflow_patterns","llm/mcp_model_context_protocol","llm/agentic_frameworks","llm/multi_agent_systems","llm/coding_agents","llm/voice_agents","llm/browser_agents_deep_dive",
    "llm/inference_and_decoding","llm/context_windows_and_long_context","llm/inference_engines","llm/vllm_deep_dive","llm/optimization_and_quantization","llm/knowledge_distillation_and_model_merging",
    "llm/deployment_and_mlops","llm/llm_caching","llm/llm_observability_and_monitoring","llm/llm_ops_platforms","llm/token_economics_and_cost_optimization","llm/llm_routing_and_model_selection","llm/prompt_management_and_promptops",
    "llm/evaluation_and_benchmarks","llm/error_analysis_and_eval_design","llm/llm_testing_strategies","llm/guardrails_and_content_safety",
    "llm/safety_and_alignment","llm/mechanistic_interpretability","llm/llm_security","llm/ai_regulations_and_compliance","llm/multimodal_models","llm/vision_language_models","llm/vla_and_robotics_foundation_models","llm/small_language_models_and_edge_ai","llm/mixture_of_experts","llm/diffusion_language_models","llm/ai_applications","llm/llm_ecosystem_and_landscape","llm/data_flywheels_and_continuous_learning",
  ],
```

(Only `llm/mcp_model_context_protocol` moved, from position 21 to 19. If `browser_agents_deep_dive` is retired per §4, drop it and the array falls to 53.)

---

## 4. Depth — THIN modules and BLOAT

**No module fails the Q&A floor** — the thinnest file in the section has exactly 15 §12 Q&As and there are zero below. The depth problems are elsewhere.

### Thin

| Module / file | Lines | §12 Q&As | What is missing | Fix |
|---|---|---|---|---|
| `code_generation/code_generation.md` | 688 | 15 | Two "as of 2025" statements; SWE-bench figure 24 points behind its own sibling `coding_agents.md`; nothing on repo-scale retrieval or agentic patch generation. | Refresh against `coding_agents.md`, +150 lines on repo-level context and FIM-vs-agent tradeoffs. |
| `agentic_frameworks/smolagents.md` · `pydantic_ai.md` · `strands_aws.md` · `mastra_typescript.md` | 437 / 455 / 467 / 485 | 15 each | Shortest files in the section; generic prose, thin code. Defensible as short vendor surveys, but they are 20% of the section's sub-file count for 5% of its value. | Leave as-is; do **not** expand. Note them as intentionally short in `CLAUDE.md` so a future audit stops re-flagging. |
| `small_language_models_and_edge_ai` | 1,197 | 15 | No broken→fix code pair (prose "Fix:" only); no MLX; no QAT. | Add a broken→fix block + the on-device-runtimes sub-file (§1). |
| `agents_and_tool_use/{agent_memory, computer_use_and_browser_agents, plan_and_execute, react_and_reasoning_patterns}.md` | 917 / 991 / 862 / 871 | 16 each | **§10 is prose bullets with no paired broken→fix code**, against the repo's "show broken code, then the fix" bar. The other 12 sub-files in the module all comply. | One broken→fix block each. ~1h per file. |
| `case_studies/cross_cutting/training_loop_internals.md` | 881 | 15 | **Referenced by exactly 1 of 29 case studies** (`llm_eval_harness_in_production` and `opentelemetry_for_llm_apps` are each referenced by 24). It is effectively an orphan under `case_studies/`. | Either wire it into `design_llm_fine_tuning_platform` + `design_synthetic_data_platform` (which need links anyway, see below), or promote it to a `training_infrastructure/` sub-file where it belongs. |
| Wave-1 case studies (13) | 810–1,625 | 8–18 (fine) | **They never adopted the 11-section principal template.** They run 10–21 differently-named sections — `## 7. Cost Impact Analysis`, `## 8. Failure Modes and Mitigations`, `## 11. System Evolution and Future Considerations`, an unnumbered bare `## Operational Playbook`, even a `## 8a.` — and are missing §2 Scale Estimation, §6 Real-World Implementations, §7 Technologies & Tools and §10 Capacity Planning outright. Interview lands at §9 or §10, never §11. Separately, 9 are under the 4+ `cross_cutting/` link bar (two at **zero**: `design_llm_fine_tuning_platform`, `design_ai_coding_assistant`), and `design_ai_content_moderation` is 1,625 lines against a 900–1,100 bar. | Retemplate to 11 sections; add the missing links. See wave 3. |
| Q&A `Q: ` prefix in 12 case studies | — | — | **Not prefixed.** `design_{ai_code_review, ai_coding_assistant, ai_content_moderation, ai_data_analyst, ai_search_engine, copilot, customer_support_bot, llm_fine_tuning_platform, notion_ai, real_time_translation}` have **0 of their 8–11** questions prefixed; `design_chatgpt` 2/10, `design_llm_gateway` 8/15, `design_rag_pipeline` 12/18. Root `CLAUDE.md` standardised `**Q: <question>?**` repo-wide in ae92f65. | Mechanical, display-only (they still parse as fully-bold questions). ~1h for all 12. |
| `multimodal_models` §14 ↔ `vision_language_models` §14 | — | — | **The same case study, twice.** "Multimodal Visual-Language Search at E-Commerce Scale" and "Visual Product Search at 50M Product Scale" solve the identical problem — CLIP retrieval + LLaVA description + HNSW/IVF-PQ at ~50M scale, same PQ-compression math, same latency budget — with different numbers. | Replace one. This is the cleanest duplicate in the section. |

### Bloat / merge

| Merge | Reasoning |
|---|---|
| `browser_agents_deep_dive/` (top-level module, 1,058 lines) **into** `agents_and_tool_use/computer_use_and_browser_agents.md` | Genuine duplication: both cover DOM-vs-vision extraction, the same tool roster (Browser Use, Stagehand, Browserbase, Skyvern, Playwright MCP), and both quote WebArena — **with different numbers and no reconciliation**. The parent already links out to the deep-dive as "an extended treatment"; the deep-dive is the staler of the two (WebArena-only, "Claude Sonnet 4"). Merging removes a whole `STUDY_ORDER` entry, kills the benchmark contradiction in §2, and loses nothing. |
| `agentic_frameworks/autogen.md` + `semantic_kernel.md` **into** `microsoft_agent_framework.md` | Both products merged upstream on 2026-04-03. Keeping two 2024-pinned files that teach the predecessors as live is worse than one accurate successor file with migration sections. Net −700 lines. |
| `agents_and_tool_use/subagents_and_delegation.md` ↔ `multi_agent_systems/orchestrator_worker_pattern.md` | Same pattern from two vantage points, citing the *same* Anthropic multi-agent-research numbers near-verbatim (90.2%, 3–5 subagents, 15× token burn) and the same Claude Code dispatch example. **Do not merge** — they sit in different modules — but state the shared citation once, in `orchestrator_worker_pattern.md`, and have `subagents_and_delegation.md` link to it while scoping itself explicitly to the single-agent ad-hoc case. |
| `optimization_and_quantization` §Pruning ↔ `knowledge_distillation_and_model_merging` §4.3 | Both independently teach "unstructured pruning gets no GPU speedup; 2:4 sparsity on A100+ gives 2×", each with its own near-duplicate interview Q&A, neither cross-linking the other. Keep the full treatment in `knowledge_distillation…`, cut `optimization_and_quantization` down to a pointer. |
| `inference_engines` §6 ↔ `vllm_deep_dive` §19 | Same bandwidth-over-weight-bytes roofline derived twice for the same LLaMA-3-8B/A100-80GB config (~125 vs ~127 tok/s/slot). Keep both (the summary-vs-deep-dive split is legitimate) but cross-link so the two numbers are visibly the same calculation. |
| `multimodal_models` ↔ `vision_language_models` | The *conceptual* split is legitimate — breadth (vision + image diffusion + speech + video) vs depth (CLIP/BLIP-2/LLaVA to parameter and latency granularity), and each cross-links the other. But **their §14 case studies are the same case study twice** (see above), and `multimodal_models` carries **no benchmark scores at all** while `vision_language_models` holds them. Fold `vision_language_models` in as a sub-file of `multimodal_models`, promote the survey to senior (§3), replace one case study. Saves a `STUDY_ORDER` entry. |
| `agentic_frameworks/framework_observability.md` **into** `llm_observability_and_monitoring` | The 2024-stamped file re-explains traces/spans/correlation-IDs/cost-tracking in structurally identical prose to its maintained 2026 sibling. Either merge it in as an "agent trace tree" subsection, or scope it hard to framework-specific instrumentation mechanics (LangGraph callback wiring, `run_id` propagation) — which is the only thing it currently has that the sibling does not. |
| `evaluation_and_benchmarks` ↔ `llm_testing_strategies` | Both independently derive the same LLM-as-judge bias mechanics from Zheng et al. 2023 (arXiv 2306.05685) with the **same two numbers** — 65.0% pairwise-verdict stability after position swap, >80% human agreement. Compare `evaluation_and_benchmarks.md` L1521/L1561 with `llm_testing_strategies.md` L792/L893. Keep the mechanics canonical in `evaluation_and_benchmarks` (it has the deeper flip-rate methodology); `llm_testing_strategies` keeps only the CI application and links. |
| `advanced_rag.md` ↔ its own sub-files | Two pockets of independently-derived shared math: the multi-query fan-out dedup/marginal-yield table (`query_transformation.md` §6.3 vs `advanced_rag.md` §4.1) and the `d^h` hop-fan-out growth table (`graph_rag.md` §6.2 vs `advanced_rag.md` §4.3). Derive once in the parent, extend in the sub-file. Otherwise the RAG split is coherent — `self_rag` / `corrective_rag` / `agentic_rag` are genuinely distinct (internal-statement-level vs external-doc-level vs external-loop) and none is padding. |

The 85 sub-files are, on the whole, **coherent and not bloated**: every sub-file is linked from its parent page (verified — zero orphans), and the `fine_tuning` / `rag_fundamentals` / `mcp` / `inference_and_decoding` splits are all clean. The one genuinely lumpy module is `agentic_frameworks` at 19 files, which is really a *vendor catalogue* rather than a topic — but that is the right shape for a catalogue, and after the AutoGen/SK merge it drops to 18.

---

## 5. Cross-section notes

- **`devops/`** — the proposed `inference_engines/serving_fleet_orchestration.md` (llm-d, Dynamo, prefix-cache-aware fleet routing) overlaps `devops/ml_platform_and_gpu_infrastructure/`. Keep the *mechanism* here and cross-link to devops for the cluster/K8s operations side, matching the existing pattern in the section's cross-reference map.
- **`technologies/`** — `technologies/temporal_durable_execution/` already exists and `agents_and_tool_use/durable_long_running_agents.md` teaches Temporal, Inngest and Restate. Add a reciprocal cross-link; today only the LLM side knows.
- **`technologies/nvidia_triton_inference_server/`** — NVIDIA positions **Dynamo as Triton's successor** for distributed generative serving. If the Triton page does not say so, that is a `technologies/` finding, not an LLM one, but it was surfaced by this audit's Dynamo research.
- **`ml/`** — the proposed reasoning-distillation sub-file borders `ml/model_compression_and_efficiency/`. LLM should own reasoning-trace distillation (it is CoT-specific); ML keeps the general theory.
- **`database/vector_databases/`** — the frozen embedding-model roster in `embeddings_and_similarity_search` (§2) may be mirrored there. Worth one grep by that section's auditor.
- **Repo-level: the whole "Known divergences still to be adapted" block in `llm/CLAUDE.md` is dead and should be deleted.** It names 15 files across `fine_tuning/`, `advanced_rag/` and `agents_and_tool_use/` as sharing three deviations (unnumbered `## Intuition`, absent §4, Diagrams/How-It-Works inverted). Spot-checked this pass: `advanced_rag/agentic_rag.md` has `## 2. Intuition`, `## 4. Types / Architectures / Strategies`, `## 5. Architecture Diagrams`, `## 6. How It Works` — all three deviations gone. `instruction_tuning.md` and `domain_adaptation.md` likewise conformant. And the separately-noted claim that `optimization_and_quantization.md` is "**missing Best Practices entirely** — Interview sits at 13 and Case Study at 14" is false: it has `## 12. Interview` → `## 13. Best Practices` → `## 14. Case Study`. A stale worklist in an agent-rules file is worse than no worklist — it will send the next audit to re-do finished work.
- **Do NOT "fix" the four-way safety boundary.** `guardrails` (the filter bolted around the model) / `safety_and_alignment` (what the model itself does wrong) / `llm_security` (how the system around the model is exploited) / `constitutional_ai` (how the model was trained to need less of the first two) is a deliberate, self-documenting split — `llm_security` L19 explicitly disclaims model-behaviour concerns, and all four cross-link. The only seam is that prompt injection is taught in three of them at three depths, which reads as depth-by-audience rather than duplication. Worth stating in `llm/CLAUDE.md` so it stops being re-flagged.
- **`training_loop_internals.md` may belong in `ml/` or `devops/`, not `case_studies/cross_cutting/`.** It is referenced by 1 of 29 case studies while its siblings are referenced by 24. Whoever moves it should check `ml/distributed_training/` first for overlap.

---

## 6. Execution plan

### Wave 1 — corrections (fully parallel, no shared files) · ~3 days

1. **`ai_regulations_and_compliance.md` — propagate the Digital Omnibus.** L1041 already has the right dates; six other places do not. Fix the L26-29 timeline, the §5 risk-tier Mermaid label (L198), §6 (L335), the tracker outputs (L397, L1282), and the entire §14 insurance case study, whose driving deadline is gone. Add the 2026-07-27 entry-into-force and what actually applied 2026-08-02 (Art. 50, Art. 4). **Every changed answer's `**Short:**` line must be updated in the same edit** — this is exactly the case root `CLAUDE.md` warns about, where a stale summary ships as the *correct* MCQ option. ~6h. *(Highest impact single item.)*
2. **MCP: flip `2026-07-28` from RC to released.** Six files: the module page (L17 and every downstream lifecycle passage), `mcp_transports_and_jsonrpc.md` (L378-395 becomes the *current* description, L525/L553/L561-565/L646 lose their future tense), `mcp_server_building.md` L302/L528, `mcp_registries_and_ecosystem.md` L381. Add the **worked MRTR example** the transports file currently only describes in prose. ~8h.
3. **The 2024-pinned framework files.** `langgraph.md` (0.1.x → 1.2.11 — the largest job, effectively a rewrite of a 1,006-line file), `dspy.md` (`dspy-ai` 2.4.x → `dspy` 3.3.1, package rename + every import path), `crewai.md` (0.60.x → 1.x, a 0.x→1.x API-stability boundary), `haystack.md` (2.x → 3.1.0, a major version). ~3.5 days total; `langgraph.md` alone is ~1.5 days.
4. **Small verified fixes**, one commit: `constitutional_ai.md` L394-397 Claude roster; `code_generation.md` L422/L458 "as of 2025"; `strands_aws.md` L375; `voice_agents.md` L208 model string; `agent_to_agent_protocols.md` v1.0.0 → v1.0.1 + the 150-org / one-year milestone; `mixture_of_experts.md` model table (+Llama 4, cross-link gpt-oss); `embeddings_and_similarity_search.md` model roster + pricing; `vision_language_models.md` + `multimodal_models.md` model rosters; `prompt_management_and_promptops.md` L385/L504 legacy caveat; `data_flywheels` frontier-model naming. ~7h.
5. **`llm_observability_and_monitoring.md` pricing table (L91-96)** — either re-anchor on the 2026 ladder or tag the gpt-4o rows "(legacy)" the way `token_economics` does. The current label is a lie by omission and it is the module the section points at for cost attribution. ~2h.
6. **`vllm_deep_dive.md` v0.26.0 → v0.27.1** — re-verify the quantization registry and KV-dtype enumerations, which are exactly what a minor bump changes. ~3h.
7. **The 12 case studies missing the `Q: ` prefix.** Mechanical, display-only. ~1h.

*Wave 1 changes only content; no `STUDY_ORDER`, no `<!-- study-paths -->` edits.*

### Wave 2 — mergers and structure · ~3 days, after Wave 1

8. **AutoGen + Semantic Kernel → `microsoft_agent_framework.md`.** Requires: delete two files and add one in the README's `<!-- study-paths -->` block, update the `agentic_frameworks` row in `llm/README.md` and its Sub-Files Index, update the Sub-files column in root `CLAUDE.md`, then `extract.py --write-paths` and `--strict`. ~1 day.
9. **Merge `browser_agents_deep_dive/` into `computer_use_and_browser_agents.md`;** reconcile the WebArena numbers against `agent_evaluation_and_benchmarking.md` and add Online-Mind2Web / OSWorld 2.0. Requires: **remove a `STUDY_ORDER` entry**, delete a module from the study-paths block, update `llm/README.md`'s All-Topics table (renumbering the `#` column), root `README.md`, and root `CLAUDE.md`. ~1 day.
10. **Fold `vision_language_models` under `multimodal_models`** and replace one of the two identical §14 case studies. Same `STUDY_ORDER` / study-paths / README surface as item 9. ~1 day.
11. **Scope or merge `framework_observability.md`** into `llm_observability_and_monitoring` (it is the seventh 2024-stamped file; fixing it in place costs the same as merging it). ~4h.
12. **De-duplicate** three pairs, each a pointer-not-a-rewrite: the pruning treatment (`optimization_and_quantization` → pointer at `knowledge_distillation…`), the Zheng-et-al. judge-bias numbers (`llm_testing_strategies` → pointer at `evaluation_and_benchmarks`), the Anthropic multi-agent-research citation (`subagents_and_delegation` → pointer at `orchestrator_worker_pattern`), plus the two `advanced_rag` math duplications and the `inference_engines`↔`vllm_deep_dive` roofline cross-link. ~5h.

### Wave 3 — Wave-1 case-study remediation (13 files, parallelisable 4-wide) · ~6 days

13. **Retemplate each of the 13 to the 11-section principal template** — this is a restructure, not a top-up: §2 Scale Estimation, §6 Real-World Implementations, §7 Technologies & Tools and §10 Capacity Planning have to be *authored*, and the existing "Cost Impact Analysis" / "Failure Modes" / "System Evolution" sections mapped in or dropped. Then bring links to 4+. Start with the two worst (`design_llm_fine_tuning_platform` and `design_ai_coding_assistant`, both at 0 links). Wire `training_loop_internals.md` into the fine-tuning and synthetic-data studies while you are there. Trim `design_ai_content_moderation` from 1,625 lines toward the bar. Update `case_studies/case_studies.md` in the same commits. ~6 days.
14. The four `agents_and_tool_use` sub-files missing a broken→fix block, plus `small_language_models_and_edge_ai`. ~5h, parallel with the above.

### Wave 4 — new content (parallel; each independent) · ~7 days

15. `agent_skills_and_progressive_disclosure.md` (P0) — ~1.5 days.
16. `rl_environments_and_agentic_rl.md` (P0) — ~1.5 days.
17. **Reasoning-token economics + reasoning-mode-aware routing** as sections inside `token_economics_and_cost_optimization` and `llm_routing_and_model_selection` — ~1 day, and the highest value-per-hour item in this wave.
18. `design_agent_platform.md` case study (P1) — ~2 days; must land with a `case_studies.md` index update in the same commit.
19. `reasoning_distillation.md`, `optimizers_and_hyperparameter_transfer.md`, `serving_fleet_orchestration.md` (P1) — ~1 day each.
20. The remaining in-file P1 additions (PromptOps prompt-compilers, diffusion-LM post-training, DPO-from-traces in data flywheels, OTel tail sampling). ~1.5 days total.
21. P2 sub-files (`reasoning_models_vs_scaffolds`, `agent_identity_and_authorization`, `on_device_runtimes`) and the P2 in-file additions — defer.

### Wave 5 — tier and order · ~half a day, LAST

22. Apply the §3 tier changes to the `<!-- study-paths -->` block and the one `STUDY_ORDER` move (`mcp_model_context_protocol` 21 → 19). Then `python3 game/extract.py --write-paths` (regenerates both tier tables — **never hand-edit them**) and `python3 game/extract.py --strict`. Finally run the whole of `scripts/build_banks.sh`, since `--strict` passing is not the same as CI passing.
23. Update `llm/CLAUDE.md`: delete the now-false "known divergences" entries for `instruction_tuning.md` / `domain_adaptation.md` **and the `advanced_rag` group** (all re-verified conformant this pass), record the four short-by-design framework files so a future audit stops re-flagging them, and record the three mergers.

**What must be touched outside content files:** `game/app.js` (`STUDY_ORDER.llm` — items 9, 10, 22 only), `llm/README.md` (`<!-- study-paths -->` block + All-Topics table + Sub-Files Index — items 8–11, 15–19, 22), root `README.md` (module and case-study counts — items 8–10, 15–19), root `CLAUDE.md` (the LLM row's sub-file count moves off 85), `llm/CLAUDE.md` (item 23), `case_studies/case_studies.md` (items 13, 18).

**Total: ~20 working days.** Wave 1 alone is ~3 days and is worth shipping on its own even if nothing else does — it is entirely corrections to things that are wrong today.

---

## 7. Confidence and gaps

**High confidence** (web-verified with a cited source, quoted against a specific line): the MCP 2026-07-28 release status, the EU AI Act omnibus deferral, LangGraph 1.2.11, DSPy 3.3.1, CrewAI 1.x, the Microsoft Agent Framework merger, A2A v1.0.1, vLLM 0.27.1, the Agent Skills adoption numbers, and the RL-environments gap. Also high confidence on everything measured directly from disk: Q&A counts (zero files below the floor), cross-`cross_cutting`-link counts per case study, sub-file orphan check (zero), tier membership, and `extract.py` output.

**Medium confidence.** The tier proposals in §3 are editorial judgment, not measurement — reasonable people would disagree about `multimodal_models` and `training_infrastructure`. The `browser_agents_deep_dive` merge assumes the two files' overlap is as large as two independent profiles reported; someone should read both end-to-end before deleting a module.

**One measurement I got wrong mid-audit, and corrected.** My first pass counted case-study Q&As
with `grep -c '^\*\*Q'` and reported that five Wave-1 case studies had *zero* §11 Q&As. That was a
grep artifact: those files carry 8–11 questions each, written as fully-bold `**Question?**` without
the `Q: ` prefix, which still parses. The real defects there are the missing prefix and the
un-adopted 11-section template — both verified by reading the files. Anyone re-running this audit
should count bold lines inside the interview-heading span, not lines starting `**Q`.

**What I could not verify.**
- **Whether GPT-4o and GPT-4o-mini are still served in August 2026.** `token_economics_and_cost_optimization` depends on it ("legacy, still served") and the whole module's arithmetic is anchored to those two rate pairs. If they were retired, that file needs a new anchor, not a price edit. **Check the OpenAI deprecations page before touching it.**
- **Whether Anthropic shipped a native audio model.** `voice_agents.md` L5 says "forthcoming"; I did not confirm either way, and it changes whether that file needs a paragraph or a section.
- **CrewAI's exact current version.** Two sources disagreed (1.14.3 on 2026-04-24 vs a 1.3.0 figure on pypistats). The 0.x→1.x boundary is certain; the minor is not.
- **Whether `multimodal_models` and `vision_language_models` should merge or just deduplicate.** The identical §14 case studies are confirmed; whether the conceptual overlap justifies collapsing a `STUDY_ORDER` entry is a judgment someone should make after reading both end to end.
- I did **not** re-verify the ~30 GPU price and SKU figures in `training_infrastructure.md` L540-550, `pre_training.md` L981 ("Lambda A100 80GB SXM at $3.99/GPU-hour on 30 July 2026") or `optimization_and_quantization.md`. They are honestly dated and were correct three weeks ago; GPU pricing moves faster than an audit cycle and re-pinning it monthly is not a good use of effort. The *pattern* is right — a dated price claim carries its as-of — so I am flagging the pattern as healthy rather than the values as suspect.
- **What I deliberately did not treat as a finding.** `token_economics`, `evaluation_and_benchmarks`, `llm_routing_and_model_selection` and `llm_testing_strategies` all use 2024-era model names, but each labels them explicitly as historical anchors or legacy-still-served, with instructions to check a live leaderboard. That is the correct handling of a fast-moving fact and should not be "fixed". The files I did flag (`llm_observability_and_monitoring`, `prompt_management_and_promptops`, `data_flywheels`) differ precisely in *not* doing that — the same models presented as current advice.
