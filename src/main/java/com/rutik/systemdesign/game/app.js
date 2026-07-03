/* System Design Daily - 5-minute blitz. Vanilla JS, no build step.
   Pages-only as of 2026-07-03: no server, no /api. localStorage sd_progress is
   the single source of truth for all progress. */

const QUESTIONS_PER_BLITZ = 10;
const DAILY_XP_GOAL = 100;
const SECTION_LABELS = {
  backend: "Backend Engineering", book: "Book Summaries",
  cs_fundamentals: "CS Fundamentals", database: "Databases", devops: "DevOps & Cloud",
  hld: "High-Level Design", java: "Java", lld: "Low-Level Design",
  llm: "LLM Engineering", ml: "Machine Learning", python: "Python", spring: "Spring",
};

// Phase-order for the Study browser. Derived from each section's README learning path.
// Modules not listed here sort to the end (alphabetically by JS Map insertion order).
const STUDY_ORDER = {
  backend: [
    "backend/osi_model_and_networking","backend/tcp_ip_deep_dive","backend/udp_and_quic","backend/http_protocols",
    "backend/rest_api_design","backend/grpc_and_protobuf","backend/graphql","backend/websockets_and_sse",
    "backend/performance_profiling","backend/connection_pooling_deep_dive","backend/caching_strategies_deep_dive","backend/async_and_concurrency_patterns",
    "backend/database_internals_and_indexing","backend/query_optimization","backend/database_migrations","backend/distributed_transactions_and_consistency","backend/database_types_deep_dive",
    "backend/fault_tolerance_patterns","backend/rate_limiting_in_depth","backend/observability_and_monitoring",
    "backend/backend_security_owasp","backend/auth_and_authorization_systems",
    "backend/backend_testing_strategies","backend/load_and_performance_testing","backend/chaos_engineering",
    "backend/event_driven_fundamentals","backend/kafka_deep_dive","backend/event_sourcing_and_cqrs","backend/messaging_patterns",
    "backend/microservices_fundamentals","backend/api_gateway_patterns","backend/service_mesh_and_service_discovery","backend/distributed_system_operational_patterns","backend/container_and_deployment_patterns",
  ],
  cs_fundamentals: [
    "cs_fundamentals/complexity_analysis_and_big_o","cs_fundamentals/number_systems_and_bit_manipulation","cs_fundamentals/recursion_and_problem_solving_patterns",
    "cs_fundamentals/arrays_strings_and_hashing","cs_fundamentals/linked_lists_stacks_and_queues","cs_fundamentals/trees_and_binary_search_trees","cs_fundamentals/heaps_and_priority_queues","cs_fundamentals/graphs_tries_and_advanced_structures",
    "cs_fundamentals/sorting_and_searching","cs_fundamentals/dynamic_programming","cs_fundamentals/greedy_and_divide_and_conquer","cs_fundamentals/graph_and_string_algorithms",
    "cs_fundamentals/processes_threads_and_context_switching","cs_fundamentals/cpu_scheduling_algorithms","cs_fundamentals/memory_management_and_virtual_memory","cs_fundamentals/deadlocks_and_synchronization",
    "cs_fundamentals/computer_architecture_and_memory_hierarchy","cs_fundamentals/networking_fundamentals","cs_fundamentals/database_and_storage_fundamentals","cs_fundamentals/cryptography_fundamentals",
  ],
  database: [
    "database/database_fundamentals","database/storage_engines_internals","database/indexing_deep_dive","database/concurrency_control_and_locking",
    "database/postgresql_internals","database/mysql_innodb_internals","database/sql_query_optimization","database/schema_design_and_normalization","database/database_migrations_zero_downtime",
    "database/document_databases","database/key_value_stores","database/wide_column_databases","database/search_engines","database/graph_databases","database/time_series_databases",
    "database/vector_databases","database/newsql_and_distributed_sql","database/in_memory_databases",
    "database/replication_and_high_availability","database/sharding_and_partitioning","database/distributed_transactions","database/consistency_models_and_consensus","database/database_caching_patterns",
    "database/connection_pool_management","database/database_performance_tuning","database/backup_recovery_and_disaster_recovery","database/database_security_and_compliance",
    "database/database_selection_framework","database/polyglot_persistence_patterns",
  ],
  devops: [
    "devops/linux_and_os_fundamentals","devops/shell_scripting_and_automation","devops/networking_for_devops","devops/version_control_and_git_workflows",
    "devops/containers_and_docker","devops/container_runtimes_and_oci","devops/kubernetes_architecture","devops/kubernetes_workloads_and_objects","devops/kubernetes_networking","devops/kubernetes_storage_and_state","devops/kubernetes_scheduling_and_autoscaling","devops/kubernetes_security","devops/helm_and_package_management","devops/kubernetes_operators_and_crds",
    "devops/ci_cd_fundamentals","devops/ci_cd_platforms","devops/deployment_strategies","devops/gitops_argocd_flux","devops/artifact_and_registry_management",
    "devops/infrastructure_as_code_terraform","devops/terraform_advanced_and_alternatives","devops/configuration_management","devops/secrets_management",
    "devops/cloud_fundamentals_and_aws","devops/gcp_and_azure_essentials","devops/serverless_and_faas","devops/cloud_networking_and_cdn","devops/cloud_cost_optimization_finops",
    "devops/observability_metrics_prometheus","devops/observability_logging","devops/observability_tracing_and_otel","devops/visualization_and_alerting","devops/sre_principles_and_slos","devops/incident_management_and_oncall",
    "devops/devsecops_and_supply_chain_security","devops/policy_as_code_and_compliance","devops/disaster_recovery_and_resilience","devops/platform_engineering_and_idp",
    "devops/ml_platform_and_gpu_infrastructure","devops/event_streaming_operations","devops/performance_and_load_testing",
  ],
  hld: [
    "hld/scalability","hld/load_balancing","hld/caching","hld/database_design",
    "hld/cap_theorem","hld/api_design","hld/message_queues","hld/rate_limiting",
    "hld/cdn","hld/consistent_hashing","hld/database_sharding","hld/microservices","hld/distributed_transactions","hld/observability","hld/security_and_auth","hld/resilience_patterns","hld/consensus_algorithms","hld/event_sourcing_cqrs",
  ],
  java: [
    "java/core_language","java/strings_and_text","java/generics_and_type_system","java/exceptions_and_io",
    "java/java8_features","java/java_time_datetime","java/java_streams","java/functional_programming","java/java9_to_21_features",
    "java/jvm_internals","java/bytecode_and_classfile",
    "java/concurrency","java/collections_internals","java/design_patterns_in_java",
    "java/performance_and_tuning","java/java_memory_model",
    "java/java_interview_patterns","java/testing_junit_mockito","java/annotation_processing",
    "java/structured_concurrency_and_loom","java/foreign_function_and_memory_api","java/reactive_programming",
    "java/networking_and_http_client","java/jdbc_and_database","java/security_and_cryptography","java/grpc_protobuf","java/microservices_patterns",
  ],
  lld: [
    "lld/design_principles","lld/solid_principles",
    "lld/creational","lld/structural","lld/behavioral",
    "lld/pattern_comparisons","lld/anti_patterns","lld/concurrency_patterns","lld/system_design_problems",
  ],
  llm: [
    "llm/foundations_and_architecture","llm/tokenization_and_embeddings","llm/embeddings_and_similarity_search",
    "llm/pre_training","llm/training_infrastructure","llm/synthetic_data_generation","llm/fine_tuning","llm/alignment_and_rlhf","llm/constitutional_ai",
    "llm/prompt_engineering","llm/rag_fundamentals","llm/advanced_rag","llm/context_engineering","llm/reasoning_models","llm/code_generation",
    "llm/agents_and_tool_use","llm/agentic_workflow_patterns","llm/agentic_frameworks","llm/multi_agent_systems","llm/mcp_model_context_protocol","llm/coding_agents","llm/voice_agents","llm/browser_agents_deep_dive",
    "llm/inference_and_decoding","llm/context_windows_and_long_context","llm/inference_engines","llm/vllm_deep_dive","llm/optimization_and_quantization","llm/knowledge_distillation_and_model_merging",
    "llm/deployment_and_mlops","llm/llm_caching","llm/llm_observability_and_monitoring","llm/llm_ops_platforms","llm/token_economics_and_cost_optimization","llm/llm_routing_and_model_selection","llm/prompt_management_and_promptops",
    "llm/evaluation_and_benchmarks","llm/llm_testing_strategies","llm/guardrails_and_content_safety",
    "llm/safety_and_alignment","llm/mechanistic_interpretability","llm/llm_security","llm/ai_regulations_and_compliance","llm/multimodal_models","llm/vision_language_models","llm/vla_and_robotics_foundation_models","llm/small_language_models_and_edge_ai","llm/mixture_of_experts","llm/diffusion_language_models","llm/ai_applications","llm/llm_ecosystem_and_landscape","llm/data_flywheels_and_continuous_learning",
  ],
  ml: [
    "ml/linear_algebra_and_calculus","ml/probability_and_statistics","ml/optimization_theory","ml/information_theory",
    "ml/supervised_learning","ml/ensemble_methods","ml/unsupervised_learning","ml/feature_engineering","ml/model_evaluation_and_selection",
    "ml/neural_network_fundamentals","ml/convolutional_neural_networks","ml/recurrent_neural_networks","ml/training_deep_networks","ml/generative_models",
    "ml/computer_vision","ml/natural_language_processing","ml/recommender_systems","ml/multi_task_and_multi_objective_learning","ml/time_series_forecasting","ml/anomaly_detection","ml/reinforcement_learning",
    "ml/ml_system_design","ml/data_pipelines_and_processing","ml/distributed_training","ml/experiment_tracking_and_versioning","ml/gpu_and_hardware_optimization","ml/active_learning_and_weak_supervision",
    "ml/model_serving_and_inference","ml/model_compression_and_efficiency","ml/monitoring_and_drift_detection","ml/mlops_and_ci_cd",
    "ml/graph_neural_networks","ml/self_supervised_and_contrastive_learning","ml/causal_inference_and_ml","ml/adversarial_ml_and_robustness","ml/privacy_preserving_ml","ml/interpretability_and_explainability","ml/uncertainty_quantification_and_conformal_prediction",
    "ml/ml_interview_patterns","ml/model_selection_and_algorithm_choice",
  ],
  python: [
    "python/data_model_and_objects","python/core_language_idioms","python/iterators_and_generators","python/decorators_and_closures","python/context_managers_and_exceptions","python/collections_and_data_structures","python/strings_bytes_encoding_and_regex","python/file_io_and_serialization",
    "python/cpython_memory_model","python/the_gil_and_free_threading","python/metaclasses_and_metaprogramming","python/the_type_system_and_typing","python/performance_and_profiling","python/functional_programming",
    "python/threading_and_multiprocessing","python/asyncio_and_event_loop","python/async_patterns_and_pitfalls","python/design_patterns_in_python","python/stdlib_datetime_and_logging","python/testing_with_pytest","python/packaging_and_project_tooling",
    "python/fastapi_fundamentals_asgi","python/pydantic_v2_deep_dive","python/routing_and_request_handling","python/dependency_injection_in_fastapi","python/middleware_and_lifecycle","python/configuration_and_settings_management",
    "python/async_database_sqlalchemy","python/authentication_and_security","python/error_handling_and_validation","python/websockets_sse_and_streaming","python/background_jobs_and_task_queues","python/testing_fastapi","python/http_clients_and_external_apis","python/message_queues_and_event_driven",
    "python/production_deployment_and_scaling","python/observability_and_monitoring","python/caching_and_performance","python/api_design_and_versioning","python/security_hardening_and_owasp",
  ],
  spring: [
    "spring/ioc_container","spring/bean_lifecycle","spring/dependency_injection","spring/spring_configuration",
    "spring/spring_proxies","spring/spring_aop",
    "spring/spring_boot_autoconfiguration","spring/spring_boot_configuration","spring/spring_boot_actuator","spring/spring_modulith",
    "spring/spring_mvc_architecture","spring/request_handling","spring/filters_and_interceptors","spring/spring_webflux","spring/spring_graphql","spring/spring_hateoas_rest_maturity","spring/spring_grpc","spring/validation_and_error_handling",
    "spring/spring_data_jpa","spring/spring_transactions","spring/spring_caching",
    "spring/spring_security_architecture","spring/spring_security_jwt_oauth","spring/spring_session",
    "spring/spring_cloud_config","spring/spring_cloud_patterns","spring/spring_messaging","spring/spring_batch","spring/spring_events_and_scheduling","spring/spring_ai","spring/spring_integration",
    "spring/spring_testing","spring/spring_performance","spring/observability_and_tracing","spring/spring_native_graalvm",
  ],
};

/* ---------- themes ---------- */
// data-theme on <html> drives every color token in style.css. The inline script
// in index.html applies the saved theme before first paint (no flash).
const THEMES = [
  { id: "midnight", name: "Midnight" },
  { id: "orchid", name: "Orchid" },
  { id: "ember", name: "Ember" },
  { id: "daylight", name: "Daylight" },
];
// A ?theme= URL override wins for this session; picking from the popover saves.
// Unknown/retired ids (e.g. a saved "aurora") fall back to midnight.
const curTheme = () => {
  const t = new URLSearchParams(location.search).get("theme") ||
    localStorage.getItem("sd_theme") || "midnight";
  return THEMES.some((x) => x.id === t) ? t : "midnight";
};

function applyTheme(id, save = true) {
  document.documentElement.dataset.theme = id;
  if (save) localStorage.setItem("sd_theme", id);
  document.querySelectorAll(".theme-opt").forEach((b) =>
    b.setAttribute("aria-checked", b.dataset.theme === id ? "true" : "false"));
}

function closeThemePop() {
  const pop = document.getElementById("themePop");
  if (pop) pop.remove();
  const tb = document.getElementById("themeBtn");
  if (tb) tb.setAttribute("aria-expanded", "false");
}

function toggleThemePop() {
  if (document.getElementById("themePop")) { closeThemePop(); return; }
  const pop = document.createElement("div");
  pop.className = "theme-pop"; pop.id = "themePop";
  pop.setAttribute("role", "radiogroup"); pop.setAttribute("aria-label", "Theme");
  pop.innerHTML = `<div class="tp-h">Theme</div>` + THEMES.map((t) =>
    `<button class="theme-opt" role="radio" data-theme="${t.id}" aria-checked="${curTheme() === t.id}">
       <span class="swatch sw-${t.id}" aria-hidden="true"></span>${t.name}<span class="tcheck">✓</span>
     </button>`).join("");
  document.body.appendChild(pop);
  const tb = document.getElementById("themeBtn");
  if (tb) tb.setAttribute("aria-expanded", "true");
  pop.querySelectorAll(".theme-opt").forEach((b) => b.addEventListener("click", () => applyTheme(b.dataset.theme)));
  const dismiss = (e) => {
    if (e.type === "keydown") {
      if (e.key !== "Escape") return;
      e.stopPropagation();                         // don't also close the reader
    } else if (pop.contains(e.target) || e.target.closest?.("#themeBtn")) return;
    closeThemePop();
    document.removeEventListener("pointerdown", dismiss, true);
    document.removeEventListener("keydown", dismiss, true);
  };
  document.addEventListener("pointerdown", dismiss, true);
  document.addEventListener("keydown", dismiss, true);
}

const app = document.getElementById("app");
const state = {
  index: null, today: null, progress: null,
  deck: [], queue: [], cursor: 0, section: null, modules: null,
  combo: 0, maxCombo: 0, sessionXp: 0, inQuiz: false, answered: false,
  curOptsLen: 0, replayFn: null,
  hard: false, awaitingConf: false, pendingPick: null,
};

/* ---------- helpers ---------- */
const todayISO = () => new Date().toLocaleDateString("en-CA"); // YYYY-MM-DD, local
const el = (sel) => document.querySelector(sel);

// Inline SVG icons (lucide-style paths) — consistent weight in every theme,
// unlike platform emoji.
const ICON = (name, cls = "") => {
  const paths = {
    flame: '<path d="M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.072-2.143-.224-4.054 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.153.433-2.294 1-3a2.5 2.5 0 0 0 2.5 2.5z"/>',
    bolt: '<polygon points="13 2 3 14 12 14 11 22 21 10 12 10"/>',
    snow: '<line x1="2" y1="12" x2="22" y2="12"/><line x1="12" y1="2" x2="12" y2="22"/><path d="m20 16-4-4 4-4"/><path d="m4 8 4 4-4 4"/><path d="m16 4-4 4-4-4"/><path d="m8 20 4-4 4 4"/>',
    clock: '<circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>',
    soundOn: '<polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/><path d="M15.54 8.46a5 5 0 0 1 0 7.07"/><path d="M19.07 4.93a10 10 0 0 1 0 14.14"/>',
    soundOff: '<polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/><line x1="23" y1="9" x2="17" y2="15"/><line x1="17" y1="9" x2="23" y2="15"/>',
  };
  return `<svg class="icon ${cls}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths[name]}</svg>`;
};

// Screen-reader announcements (aria-live region in index.html).
const announce = (msg) => { const n = el("#live"); if (n) n.textContent = msg; };

const REDUCED = () => window.matchMedia("(prefers-reduced-motion: reduce)").matches;

// Cross-fade between screens via the View Transitions API where available.
function vt(fn) {
  if (document.startViewTransition && !REDUCED()) {
    const t = document.startViewTransition(fn);
    /* [C] rapid successive navigations skip a transition; the ready/finished
       promises then reject with a benign AbortError — swallow it. */
    t.ready?.catch(() => {}); t.finished?.catch(() => {});
  } else fn();
}

/* ---------- ambient graphics: spotlight, parallax, scroll reveals ---------- */
// Pointer spotlight: glass cards get a specular highlight that follows the
// cursor (CSS paints a radial gradient at --mx/--my; see style.css §16).
const SPOT_SEL = ".tile,.topic-card,.review-card,.badge,.opt,.studyrow,.modrow,.sectiontile,.miss-item,.pathnode";
let _spotEv = null, _spotRaf = 0;
document.addEventListener("pointermove", (e) => {
  _spotEv = e;
  if (_spotRaf) return;
  _spotRaf = requestAnimationFrame(() => {
    _spotRaf = 0;
    const t = _spotEv.target.closest?.(SPOT_SEL);
    if (!t) return;
    const r = t.getBoundingClientRect();
    t.style.setProperty("--mx", (((_spotEv.clientX - r.left) / r.width) * 100).toFixed(1) + "%");
    t.style.setProperty("--my", (((_spotEv.clientY - r.top) / r.height) * 100).toFixed(1) + "%");
  });
});

// Aurora-mesh pointer parallax (fine pointers only; keyframes consume --par-*).
if (window.matchMedia("(pointer: fine)").matches) {
  let _parEv = null, _parRaf = 0;
  document.addEventListener("pointermove", (e) => {
    _parEv = e;
    if (_parRaf || REDUCED()) return;
    _parRaf = requestAnimationFrame(() => {
      _parRaf = 0;
      const s = document.documentElement.style;
      s.setProperty("--par-x", ((_parEv.clientX / innerWidth - 0.5) * 14).toFixed(1) + "px");
      s.setProperty("--par-y", ((_parEv.clientY / innerHeight - 0.5) * 10).toFixed(1) + "px");
    });
  });
}

// Scroll-driven reveals: list items rise in as they enter the viewport.
const _revealObs = "IntersectionObserver" in window
  ? new IntersectionObserver((entries) => entries.forEach((x) => {
      if (x.isIntersecting) { x.target.classList.add("in"); _revealObs.unobserve(x.target); }
    }), { threshold: 0.12 })
  : null;

function wireReveals() {
  if (!_revealObs || REDUCED()) return;
  // NOTE: utility class is "rise", NOT "reveal" — .reveal is the quiz answer panel.
  document.querySelectorAll(".grid .tile, .sectiontile, .studyrow, .modrow, .miss-item").forEach((n, i) => {
    n.classList.add("rise");
    n.style.transitionDelay = (i % 8) * 30 + "ms";
    _revealObs.observe(n);
  });
}

// Floating "+N XP" particle at the answered option.
function floatXP(amount, anchor) {
  if (REDUCED() || !anchor) return;
  const f = document.createElement("span");
  f.className = "xp-float";
  f.textContent = "+" + amount + " XP";
  const r = anchor.getBoundingClientRect();
  f.style.left = Math.max(12, r.right - 86) + "px";
  f.style.top = (r.top - 4) + "px";
  document.body.appendChild(f);
  setTimeout(() => f.remove(), 1000);
}

/* ---------- keyboard-shortcuts overlay ---------- */
function toggleHelp() {
  const ex = el("#helpOverlay");
  if (ex) { ex.remove(); return; }
  const row = (k, d) => `<div class="hk"><span>${d}</span><span class="keys">${k.split(" ").map((x) => `<kbd>${x}</kbd>`).join("")}</span></div>`;
  const o = document.createElement("div");
  o.className = "help-overlay"; o.id = "helpOverlay";
  o.setAttribute("role", "dialog"); o.setAttribute("aria-label", "Keyboard shortcuts");
  o.innerHTML = `<div class="help-card">
    <h2>Keyboard shortcuts</h2>
    <div class="help-cols">
      <div><h3>Quiz</h3>${row("1 2 3 4", "Answer")}${row("↵", "Next")}${row("S", "Skip for now")}</div>
      <div><h3>Cards</h3>${row("Space", "Reveal")}${row("1", "Missed it")}${row("2", "Got it")}</div>
      <div><h3>Reader</h3>${row("F", "Fullscreen")}${row("Esc", "Exit / close")}</div>
      <div><h3>Diagram zoom</h3>${row("+ −", "Zoom")}${row("0", "Fit")}${row("← →", "Pan")}</div>
    </div>
    <p class="help-hint">Press <kbd>?</kbd> anytime &middot; mouse back/forward buttons navigate too</p>
    <button class="ghost" id="helpClose">Close (Esc)</button>
  </div>`;
  document.body.appendChild(o);
  el("#helpClose").addEventListener("click", () => o.remove());
  o.addEventListener("click", (e) => { if (e.target === o) o.remove(); });
}

/* ---------- mouse back/forward buttons ---------- */
// Back (button 3) / forward (button 4): navigate the reader's history and
// topic list when it's open; otherwise back returns to the previous screen.
document.addEventListener("mouseup", (e) => {
  if (e.button !== 3 && e.button !== 4) return;
  e.preventDefault();
  const nav = reader.nav;
  if (document.body.classList.contains("reader-open")) {
    if (e.button === 3) {
      if (reader.back.length) { const p = reader.back.pop(); openReaderPath(p.path, p.title, p.nav); }
      else if (nav && nav.idx > 0) openReaderPath(nav.list[nav.idx - 1].path, nav.list[nav.idx - 1].title, { list: nav.list, idx: nav.idx - 1 });
      else closeReader();
    } else if (nav && nav.idx < nav.list.length - 1) {
      openReaderPath(nav.list[nav.idx + 1].path, nav.list[nav.idx + 1].title, { list: nav.list, idx: nav.idx + 1 });
    }
    return;
  }
  if (e.button === 3) history.back();              // outside the reader: walk the hash history
});
document.addEventListener("mousedown", (e) => {   // suppress browser history nav
  if (e.button === 3 || e.button === 4) e.preventDefault();
});
const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
const label = (s) => SECTION_LABELS[s] || s;

// Inline markdown for quiz surfaces: escape first, then render only `code` and
// **bold** (the two constructs the *Md display variants use). Callers pass the
// *Md field when present, falling back to the plain field.
function qInline(t) {
  return esc(t == null ? "" : t)
    .replace(/`([^`]+)`/g, "<code>$1</code>")
    .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
}

function shuffle(a) {
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

// Deterministic hashing + PRNG for future date-seeded features (daily pick,
// stable "shuffles" that survive a reload). cyrb53 -> 53-bit hash of a string;
// mulberry32 -> fast seeded generator returning floats in [0, 1).
function cyrb53(str, seed = 0) {
  let h1 = 0xdeadbeef ^ seed, h2 = 0x41c6ce57 ^ seed;
  for (let i = 0; i < str.length; i++) {
    const ch = str.charCodeAt(i);
    h1 = Math.imul(h1 ^ ch, 2654435761);
    h2 = Math.imul(h2 ^ ch, 1597334677);
  }
  h1 = Math.imul(h1 ^ (h1 >>> 16), 2246822507) ^ Math.imul(h2 ^ (h2 >>> 13), 3266489909);
  h2 = Math.imul(h2 ^ (h2 >>> 16), 2246822507) ^ Math.imul(h1 ^ (h1 >>> 13), 3266489909);
  return 4294967296 * (2097151 & h2) + (h1 >>> 0);
}
function mulberry32(seed) {
  let a = seed >>> 0;
  return function () {
    a |= 0; a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
/* [C] Fisher-Yates with a string-seeded PRNG: the same seed always yields the
   same order (the Gauntlet's frozen daily deck, Interviewer escalation). */
function seededShuffle(arr, seedStr) {
  const a = arr.slice(), rnd = mulberry32(cyrb53(String(seedStr)) >>> 0);
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(rnd() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

// Fetch a static JSON file (question banks, index, graph); returns `fallback`
// on any error. Pages serves these with normal caching; "default" lets a 304
// revalidate the multi-MB banks instead of re-downloading them every boot.
async function fetchJSON(path, fallback, cache = "no-store") {
  try { const r = await fetch(path, { cache }); if (!r.ok) throw 0; return await r.json(); }
  catch { return fallback; }
}

/* ---------- sound (zero-asset Web Audio) ---------- */
const sfx = (() => {
  let ctx;
  const on = () => localStorage.getItem("sd_mute") !== "1";
  function tone(freq, dur, type = "sine", gain = 0.06, delay = 0) {
    if (!on()) return;
    try {
      ctx = ctx || new (window.AudioContext || window.webkitAudioContext)();
      if (ctx.state === "suspended") ctx.resume();
      const t = ctx.currentTime + delay;
      const o = ctx.createOscillator(), g = ctx.createGain();
      o.type = type; o.frequency.value = freq;
      o.connect(g); g.connect(ctx.destination);
      g.gain.setValueAtTime(gain, t);
      g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
      o.start(t); o.stop(t + dur);
    } catch { /* audio unavailable */ }
  }
  return {
    correct() { tone(660, 0.12); tone(880, 0.14, "sine", 0.06, 0.08); },
    wrong() { tone(190, 0.22, "sawtooth", 0.05); },
    combo() { tone(880, 0.08); tone(1175, 0.1, "triangle", 0.05, 0.06); tone(1568, 0.12, "triangle", 0.05, 0.12); },
    finish() { [523, 659, 784, 1047].forEach((f, i) => tone(f, 0.2, "triangle", 0.06, i * 0.1)); },
    // --- moment tones (0.5) ---
    levelup() { [523, 659, 784, 988, 1319].forEach((f, i) => tone(f, 0.18, "triangle", 0.06, i * 0.07)); },
    tier() { [523, 659, 784].forEach((f, i) => tone(f, 0.55, "sine", 0.05, i * 0.02)); },
    gold() { [523, 659, 784, 1047].forEach((f, i) => tone(f, 0.7, "sine", 0.055, i * 0.03)); },
    bell() { tone(1047, 1.3, "sine", 0.06); tone(2094, 1.1, "sine", 0.02, 0.01); },
    chime() { tone(784, 0.4, "sine", 0.045); tone(1175, 0.5, "sine", 0.035, 0.06); },
    /* [C] gauntlet wax-seal thud + codex card-flip */
    seal() { tone(120, 0.3, "sawtooth", 0.06); tone(90, 0.4, "sine", 0.05, 0.05); tone(1568, 0.5, "sine", 0.03, 0.28); },
    capture() { tone(660, 0.12, "triangle", 0.05); tone(990, 0.16, "triangle", 0.05, 0.08); tone(1320, 0.2, "sine", 0.04, 0.16); },
    isOn: on,
    toggle() { const wasOn = on(); localStorage.setItem("sd_mute", wasOn ? "1" : "0"); return !wasOn; },
  };
})();

function confetti() {
  if (REDUCED()) return;
  const cs = getComputedStyle(document.documentElement);
  const colors = ["--accent", "--accent-2", "--good", "--warn", "--bad"]
    .map((v) => cs.getPropertyValue(v).trim() || "#6ea8fe");
  const c = document.createElement("div");
  c.className = "confetti";
  for (let i = 0; i < 110; i++) {
    const p = document.createElement("i");
    const s = 5 + Math.random() * 7;                       // mixed sizes
    p.style.width = s + "px"; p.style.height = s + "px";
    if (i % 3 === 0) p.style.borderRadius = "50%";         // mixed shapes
    p.style.left = Math.random() * 100 + "vw";
    p.style.background = colors[i % colors.length];
    p.style.animationDelay = (Math.random() * 0.5).toFixed(2) + "s";
    p.style.animationDuration = (1.8 + Math.random() * 1.2).toFixed(2) + "s";
    p.style.setProperty("--rot", (Math.random() * 720 - 360) + "deg");
    p.style.setProperty("--dx", (Math.random() * 160 - 80).toFixed(0) + "px");  // sideways drift
    c.appendChild(p);
  }
  document.body.appendChild(c);
  setTimeout(() => c.remove(), 3400);
}

// Radial shockwave at combo milestones.
function ripple(anchor) {
  if (REDUCED() || !anchor) return;
  const r = anchor.getBoundingClientRect();
  const w = document.createElement("span");
  w.className = "combo-ripple";
  w.style.left = (r.left + r.width / 2) + "px";
  w.style.top = (r.top + r.height / 2) + "px";
  document.body.appendChild(w);
  setTimeout(() => w.remove(), 700);
}

function countUp(node, to) {
  const dur = 600, start = performance.now();
  function step(now) {
    const k = Math.min(1, (now - start) / dur);
    node.textContent = "+" + Math.round(to * (1 - Math.pow(1 - k, 3)));
    if (k < 1) requestAnimationFrame(step);
  }
  requestAnimationFrame(step);
}

/* ---------- persistence ---------- */
// localStorage sd_progress is the single source of truth (Pages-only).
function loadProgress() {
  const fill = (p) => { if (!p.reviews) p.reviews = {}; if (p.freezes == null) p.freezes = 2; if (!p.freezeUsedOn) p.freezeUsedOn = []; if (!p.awards) p.awards = {}; if (p.deepReads == null) p.deepReads = 0; return p; };  /* [C] awards + deepReads backfill */
  let ls = null;
  try { ls = JSON.parse(localStorage.getItem("sd_progress")); } catch { /* corrupt -> reseed */ }
  return ls ? fill(ls)
    : { streak: 0, longestStreak: 0, lastPlayed: null, totalXP: 0, sections: {}, history: [], reviews: {}, freezes: 2, freezeUsedOn: [], awards: {}, deepReads: 0 };
}

// SM-2-lite spaced-repetition scheduler. `ms` (time-to-answer, optional) is
// tracked as an EMA so future difficulty tuning has a per-question latency signal.
function scheduleReview(rv, status, today, ms, conf) {
  if (status === "correct") {
    rv.reps = (rv.reps || 0) + 1;
    rv.ease = Math.min(3.0, (rv.ease || 2.5) + 0.1);
    const r = rv.reps;
    // Desirable difficulty: a low-confidence (or slow) correct grows slower —
    // the 0.75 factor applies only inside the growth branch (reps >= 3).
    rv.interval = r === 1 ? 1 : r === 2 ? 3
      : Math.max(1, Math.round((rv.interval || 1) * rv.ease * (conf === "low" ? 0.75 : 1)));
  } else {
    rv.reps = 0;
    rv.lapses = (rv.lapses || 0) + (status === "wrong" ? 1 : 0);
    // Hypercorrection: a high-confidence miss cuts ease harder than a low one.
    const drop = status === "wrong" ? (conf === "high" ? 0.3 : 0.2) : 0.05;
    rv.ease = Math.max(1.3, (rv.ease || 2.5) - drop);
    rv.interval = 1;
  }
  if (ms > 0) rv.ms = rv.ms ? Math.round(0.7 * rv.ms + 0.3 * ms) : Math.round(ms);
  const due = new Date(today + "T00:00:00");
  due.setDate(due.getDate() + rv.interval);
  rv.due = due.toLocaleDateString("en-CA");
  return rv;
}

// Median of the stored per-question answer-time EMAs across all reviews with
// telemetry — the reference "typical" latency for the slowness signal below.
function medianReviewMs() {
  const arr = Object.values(state.progress.reviews || {}).map((r) => r.ms).filter((m) => m > 0).sort((a, b) => a - b);
  return arr.length ? arr[arr.length >> 1] : 0;
}

// 0..1 "hard for you" score from spaced-repetition telemetry, or null when a
// question has no review record yet. Combines lapses (0.5), inverse ease (0.3),
// and slowness vs the typical answer time (0.2).
function personalDifficulty(q, rv, medMs) {
  if (!rv) return null;
  const lapses = Math.min(1, (rv.lapses || 0) / 3);
  const invEase = Math.min(1, Math.max(0, (3.0 - (rv.ease || 2.5)) / 1.7));   // ease 1.3..3.0 -> 1..0
  const slow = medMs && rv.ms && rv.ms > 2 * medMs ? 1 : 0;
  return Math.min(1, 0.5 * lapses + 0.3 * invEase + 0.2 * slow);
}

// Streak-freeze + SM-2 progress update. Writes localStorage sd_progress.
function saveSessionLocal(session) {
  const p = state.progress;
  if (p.freezes == null) p.freezes = 2;
  if (!p.freezeUsedOn) p.freezeUsedOn = [];
  const reviews = (p.reviews = p.reviews || {});
  // Session median of correct-answer times: a much-slower-than-typical correct
  // schedules like a low-confidence one (A6 slow-correct desirable difficulty).
  const cms = (session.results || []).filter((r) => r.status === "correct" && r.ms > 0).map((r) => r.ms).sort((a, b) => a - b);
  const medCorrect = cms.length ? cms[cms.length >> 1] : 0;
  let correct = 0;
  for (const res of session.results || []) {
    const sec = (p.sections[res.section] = p.sections[res.section] || { seen: 0, correct: 0 });
    sec.seen += 1;
    sec.lastPlayed = session.date;
    if (res.status === "correct") { sec.correct += 1; correct += 1; }
    // Confidence calibration tallies (A4) — only first-attempt picks carry conf.
    if (res.conf === "high") { sec.sureSeen = (sec.sureSeen || 0) + 1; if (res.status === "correct") sec.sureCorrect = (sec.sureCorrect || 0) + 1; }
    else if (res.conf === "low") { sec.unsureSeen = (sec.unsureSeen || 0) + 1; if (res.status === "correct") sec.unsureCorrect = (sec.unsureCorrect || 0) + 1; }
    if (res.id) {
      const rv = reviews[res.id] || { ease: 2.5, interval: 0, reps: 0, lapses: 0 };
      rv.section = res.section; rv.module = res.module;
      // Slow correct -> schedule like low confidence; never double-shrink.
      let eff = res.conf;
      if (res.status === "correct" && medCorrect && res.ms > 2 * medCorrect) eff = "low";
      scheduleReview(rv, res.status, session.date, res.ms, eff);
      reviews[res.id] = rv;
    }
  }
  const dayMs = 86400000, atMidnight = (iso) => new Date(iso + "T00:00:00");
  let freezeUsed = false, advanced = false;
  if (!p.lastPlayed) { p.streak = 1; advanced = true; }
  else {
    const gap = Math.round((atMidnight(session.date) - atMidnight(p.lastPlayed)) / dayMs);
    if (gap <= 0) { /* same calendar day: streak unchanged */ }
    else if (gap === 1) { p.streak = (p.streak || 0) + 1; advanced = true; }
    else if (gap === 2 && (p.freezes || 0) > 0) {
      p.freezes -= 1;
      const missed = new Date(atMidnight(session.date) - dayMs).toLocaleDateString("en-CA");
      p.freezeUsedOn.push(missed); p.freezeUsedOn = p.freezeUsedOn.slice(-60);
      p.streak = (p.streak || 0) + 1; advanced = true; freezeUsed = true;
    } else { p.streak = 1; advanced = true; }
  }
  if (advanced && p.streak > 0 && p.streak % 7 === 0) p.freezes = Math.min(3, (p.freezes || 0) + 1);
  p.longestStreak = Math.max(p.longestStreak || 0, p.streak);
  p.lastPlayed = session.date;
  const xp = correct * 10 + p.streak * 5 + (session.bonusXp || 0);
  p.totalXP = (p.totalXP || 0) + xp;
  (p.history = p.history || []).push({
    date: session.date, answered: (session.results || []).length, correct, xp,
    section: session.section || "unknown", durationSec: session.durationSec || 0,
  });
  p.history = p.history.slice(-365);               // rolling one-year history cap
  localStorage.setItem("sd_progress", JSON.stringify(p));
  return { xp, freezeUsed };
}

/* ---------- selection ---------- */
function pickSection() {
  const avail = Object.keys(state.index.sections);
  if (state.today && avail.includes(state.today.section)) return state.today.section;
  const seen = state.progress.sections || {};
  const unplayed = avail.filter((s) => !seen[s]);
  if (unplayed.length) return shuffle(unplayed)[0];
  return avail.sort((a, b) => (seen[a]?.seen || 0) - (seen[b]?.seen || 0))[0];
}

function dueReviews() {
  const t = todayISO();
  return Object.entries(state.progress.reviews || {})
    .filter(([, r]) => r.due && r.due <= t)
    .sort((a, b) => (a[1].due < b[1].due ? -1 : 1));
}

function todaysXp() {
  const t = todayISO();
  return (state.progress.history || []).filter((h) => h.date === t).reduce((a, h) => a + (h.xp || 0), 0);
}

/* ---------- mastery tiers, level, decay ---------- */
// Tier needs both volume (seen) and accuracy, so a tier reflects durable mastery
// rather than a lucky short run.
function sectionTier(st) {
  if (!st || !st.seen) return null;
  const acc = st.correct / st.seen;
  if (st.seen >= 40 && acc >= 0.85) return "Gold";
  if (st.seen >= 20 && acc >= 0.70) return "Silver";
  if (st.seen >= 8 && acc >= 0.50) return "Bronze";
  return null;
}
const levelFromXP = (xp) => Math.floor((xp || 0) / 250) + 1;

// Most-invested section not practiced in a week, to nudge a refresh.
function rustiestSection() {
  const secs = state.progress.sections || {};
  const today = new Date(todayISO() + "T00:00:00");
  let best = null;
  for (const [s, st] of Object.entries(secs)) {
    if (!st.lastPlayed || !st.seen) continue;
    const days = Math.round((today - new Date(st.lastPlayed + "T00:00:00")) / 86400000);
    if (days >= 7 && (!best || days > best.days || (days === best.days && st.seen > best.seen)))
      best = { s, days, seen: st.seen };
  }
  return best;
}

function refreshStats() {
  // Pop-animate a stat when its value actually changes.
  const set = (sel, val) => {
    const n = el(sel);
    if (!n) return;
    const s = String(val);
    if (n.textContent === s) return;
    n.textContent = s;
    n.classList.remove("pop"); void n.offsetWidth;   // restart the animation
    n.classList.add("pop");
  };
  set("#streakVal", state.progress.streak || 0);
  set("#xpVal", state.progress.totalXP || 0);
  set("#lvlVal", levelFromXP(state.progress.totalXP));
}

/* ---------- home ---------- */
function goalRing() {
  const xp = todaysXp(), pct = Math.min(1, xp / DAILY_XP_GOAL);
  const r = 26, circ = 2 * Math.PI * r, off = circ * (1 - pct);
  const done = xp >= DAILY_XP_GOAL;
  return `<div class="goal" title="Daily goal">
      <svg width="72" height="72" viewBox="0 0 64 64">
        <defs><linearGradient id="ringGrad" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" style="stop-color:var(--accent)"/>
          <stop offset="1" style="stop-color:var(--accent-2)"/>
        </linearGradient></defs>
        <circle cx="32" cy="32" r="${r}" class="ring-bg"/>
        <circle cx="32" cy="32" r="${r}" class="ring-fg ${done ? "done" : ""}"
          stroke-dasharray="${circ.toFixed(1)}" stroke-dashoffset="${off.toFixed(1)}"/>
      </svg>
      <div class="goal-txt">${done ? "✓" : xp}<span>/${DAILY_XP_GOAL}</span></div>
    </div>`;
}

function renderHome() {
  state.inQuiz = false;
  refreshStats();
  const section = pickSection();
  const coachMsg = state.today && state.today.message;
  const streak = state.progress.streak || 0;
  const freezes = state.progress.freezes || 0;
  const freezeBit = freezes > 0
    ? ` <span class="freeze-chip" title="Streak freezes auto-cover a single missed day">${ICON("snow", "i-snow")} ${freezes}</span>` : "";
  const streakLine = streak > 0
    ? `You're on a <b>${streak}-day</b> streak. Keep it alive.${freezeBit}`
    : `Start your streak today &mdash; just 5 minutes.${freezeBit}`;
  // Resume card: a same-day snapshot of an interrupted blitz (above review).
  const resume = resumeSummary();
  const resumeCard = resume
    ? `<button class="review-card resume" id="resumeBtn">
         <div><div class="eyebrow">Unfinished blitz</div>
         <h2>Resume your blitz</h2>
         <p class="msg">${resume.done}/${resume.total} done${resume.combo >= 2 ? ` &middot; ${resume.combo}x combo alive` : ""}. Pick up where you left off.</p></div>
         <span class="review-go">Resume &rarr;</span>
       </button>`
    : "";
  const due = dueReviews();
  const reviewCard = due.length
    ? `<button class="review-card" id="reviewBtn">
         <div><div class="eyebrow good">Spaced repetition</div>
         <h2>${due.length} question${due.length === 1 ? "" : "s"} due for review</h2>
         <p class="msg">Resurface what you've missed before it fades. ~${Math.min(due.length, QUESTIONS_PER_BLITZ)} now.</p></div>
         <span class="review-go">Review &rarr;</span>
       </button>`
    : "";
  const worst = weakSections().filter((x) => x.acc < 0.7)[0];
  const weakCard = worst
    ? `<button class="review-card weak" id="weakBtn">
         <div><div class="eyebrow warn">Targeted practice</div>
         <h2>Drill your weak spots</h2>
         <p class="msg">Lowest mastery: <b>${esc(label(worst.s))}</b> at ${Math.round(worst.acc * 100)}%. Focus a round there.</p></div>
         <span class="review-go warn">Drill &rarr;</span>
       </button>`
    : "";
  const rusty = rustiestSection();
  const rustyNote = rusty
    ? `<button class="rusty-note" id="rustyBtn">${ICON("clock")} <b>${esc(label(rusty.s))}</b> is getting rusty &mdash; ${rusty.days} days since you practiced it. Refresh it &rarr;</button>`
    : "";
  const secs = state.index.sections, p = state.progress;
  const tiles = Object.keys(secs).sort().map((s) => {
    const st = (p.sections && p.sections[s]) || { seen: 0, correct: 0 };
    const acc = st.seen ? Math.round((st.correct / st.seen) * 100) : null;
    const bar = acc === null ? "" : `<span class="tbar"><i style="width:${acc}%"></i></span>`;
    const passChip = (p.awards && (p.awards["interview_" + s] || p.awards["panel_" + s])) ? `<span class="c-chip pass sm">Passed</span>` : "";  // [C] interviewer plaque
    return `<button class="tile ${s === section ? "suggested" : ""}" data-section="${s}">
        <span class="tname">${esc(label(s))}${passChip}</span>
        <span class="tmeta">${secs[s]} Qs &middot; ${acc === null ? "new" : acc + "% mastery"}</span>
        ${bar}
      </button>`;
  }).join("");
  const dateLine = new Date().toLocaleDateString("en-US", { weekday: "long", month: "long", day: "numeric" });
  app.innerHTML = `
    <div class="hero">
      <div class="hero-row">${goalRing()}<div>
        <div class="eyebrow date-eyebrow">${esc(dateLine)}</div>
        <h1>Today's 5-minute blitz</h1><p>${streakLine}</p></div></div>
    </div>
    ${skylineSVG(p)}
    ${gauntletCardHTML()}
    <div class="topic-card">
      <div class="eyebrow">Suggested for today</div>
      <h2>${esc(label(section))}</h2>
      <p class="msg">${esc(coachMsg || `${QUESTIONS_PER_BLITZ} questions pulled from your ${label(section)} notes.`)}</p>
      <button class="cta" id="startBtn">Start &mdash; ${QUESTIONS_PER_BLITZ} questions<small>~5 min &middot; ${deckMode() === "flash" ? "flashcards" : "multiple choice"}</small></button>
    </div>
    ${resumeCard}
    ${reviewCard}
    ${weakCard}
    ${rustyNote}
    <div class="section-head-row">
      <h2 class="section-h">Or pick a section &mdash; then choose sub-topics</h2>
      <button class="c-codex-link" id="codexLink">The Codex &rarr;</button>
    </div>
    <div class="grid">${tiles}</div>`;
  el("#startBtn").addEventListener("click", () => startBlitz(section));
  /* [C] gauntlet + codex entry points */
  const gauntBtn = el("#gauntBtn"); if (gauntBtn) gauntBtn.addEventListener("click", () => go("#/gauntlet"));
  el("#codexLink").addEventListener("click", () => go("#/codex"));
  if (resume) el("#resumeBtn").addEventListener("click", resumeDeck);
  if (due.length) el("#reviewBtn").addEventListener("click", startReview);
  if (worst) el("#weakBtn").addEventListener("click", startWeakSpots);
  if (rusty) el("#rustyBtn").addEventListener("click", () => startBlitz(rusty.s));
  document.querySelectorAll(".tile").forEach((b) =>
    b.addEventListener("click", () => go("#/topics/" + b.dataset.section)));
  wireReveals();
}

/* ---------- bank loading / sub-topic picker ---------- */
const bankCache = {};
async function loadBank(section) {
  // "default" cache lets a 304 revalidate these multi-MB files instead of
  // re-downloading them on every boot.
  if (!bankCache[section]) bankCache[section] = await fetchJSON(`questions/${section}.json`, null, "default");
  return bankCache[section];
}

// id -> question map per section, built lazily from the cached bank. Lets a
// picked distractor be traced back to the question its answer came from (A2).
const _bankById = {};
function bankById(section) {
  const bank = bankCache[section];
  if (!bank) return null;
  if (!_bankById[section] || _bankById[section].size !== bank.length)
    _bankById[section] = new Map(bank.map((q) => [q.id, q]));
  return _bankById[section];
}
// The question whose answer became this (wrong) option, or null if the id is no
// longer in the bank. distractorIds are aligned with distractors and same-section.
function distractorSource(q, opt) {
  if (!opt || opt.src <= 0) return null;             // src 0 = the correct answer
  const id = (q.distractorIds || [])[opt.src - 1];
  if (!id) return null;
  const byId = bankById(q.section);
  return (byId && byId.get(id)) || null;
}

function modulesOf(bank) {
  const map = new Map();
  for (const q of bank) {
    const m = map.get(q.module) || { module: q.module, name: q.moduleName, count: 0 };
    m.count++; map.set(q.module, m);
  }
  const section = bank[0]?.section;
  const order = STUDY_ORDER[section] || [];
  return [...map.values()].sort((a, b) => {
    const ai = order.indexOf(a.module), bi = order.indexOf(b.module);
    return (ai === -1 ? 9999 : ai) - (bi === -1 ? 9999 : bi);
  });
}

async function openTopics(section) {
  app.innerHTML = `<div class="loading">Loading ${esc(label(section))}&hellip;</div>`;
  const bank = await loadBank(section);
  if (!bank || !bank.length) {
    app.innerHTML = `<div class="error">Couldn't load questions for ${esc(section)}. Run <code>python3 extract.py</code>.</div>`;
    return;
  }
  const mods = modulesOf(bank);
  const rows = mods.map((m) =>
    `<label class="modrow"><input type="checkbox" class="modcheck" value="${esc(m.module)}" checked />
       <span class="mname">${esc(m.name)}</span><span class="mcount">${m.count}</span></label>`).join("");
  app.innerHTML = `
    <div class="hero"><h1>${esc(label(section))}</h1><p>Pick the sub-topics to drill &mdash; or keep them all.</p></div>
    <div class="topicbar">
      <button class="ghost" id="allBtn">Select all</button>
      <button class="ghost" id="noneBtn">Clear</button>
      <input type="search" class="filter" id="modFilter" placeholder="Filter topics" aria-label="Filter topics" />
      <span class="selcount" id="selCount"></span>
    </div>
    <div class="modlist">${rows}</div>
    <div class="qactions">
      <button class="ghost" id="backBtn">&larr; Back</button>
      <button class="cta inline" id="startSel">Start blitz</button>
    </div>`;
  const checks = () => [...document.querySelectorAll(".modcheck")];
  const selected = () => checks().filter((c) => c.checked).map((c) => c.value);
  const updateCount = () => {
    const sel = selected();
    const n = mods.filter((m) => sel.includes(m.module)).reduce((a, m) => a + m.count, 0);
    el("#selCount").textContent = `${sel.length} topic${sel.length === 1 ? "" : "s"} · ${n} questions`;
    el("#startSel").disabled = sel.length === 0;
  };
  checks().forEach((c) => c.addEventListener("change", updateCount));
  el("#allBtn").addEventListener("click", () => { checks().forEach((c) => (c.checked = true)); updateCount(); });
  el("#noneBtn").addEventListener("click", () => { checks().forEach((c) => (c.checked = false)); updateCount(); });
  el("#modFilter").addEventListener("input", () => {
    const f = el("#modFilter").value.trim().toLowerCase();
    document.querySelectorAll(".modrow").forEach((r) =>
      (r.style.display = r.querySelector(".mname").textContent.toLowerCase().includes(f) ? "" : "none"));
  });
  el("#backBtn").addEventListener("click", () => go("#/home"));
  el("#startSel").addEventListener("click", () => startBlitz(section, selected()));
  updateCount();
  wireReveals();
}

/* ---------- deck building ---------- */
// Option "src" tags let a snapshot replay the exact arrangement: 0 = correct
// answer, i+1 = distractors[i].
function optionsFor(q) {
  return [{ src: 0, t: q.correct, ok: true },
    ...q.distractors.map((d, i) => ({ src: i + 1, t: d, ok: false }))];
}
// optOrder (from a resume snapshot) rebuilds the same option order without a
// reshuffle; a stale/mismatched order falls back to a fresh shuffle.
function makeItem(q, optOrder) {
  const base = optionsFor(q);
  let opts;
  if (optOrder && optOrder.length === base.length) {
    const bySrc = new Map(base.map((o) => [o.src, o]));
    opts = optOrder.map((s) => bySrc.get(s));
    if (opts.some((o) => !o)) opts = shuffle(base);
  } else {
    opts = shuffle(base);
  }
  return { q, opts, optOrder: opts.map((o) => o.src), status: "pending", boss: false };
}

// Quiz vs flashcard is a global, persisted preference toggled from the top bar.
function deckMode() { return localStorage.getItem("sd_mode") === "flash" ? "flash" : "quiz"; }

function startDeck(questions, replayFn, opts = {}) {
  state.mode = deckMode();
  if (opts.keepOrder) state.mode = "quiz";       // [C] gauntlet/interview always run the MCQ engine
  state.hard = !!opts.hard;
  state.awaitingConf = false;
  state._medMs = medianReviewMs();
  const items = questions.map(makeItem);
  if (opts.keepOrder) {
    state.deck = items;                          // [C] the recipe IS the arc — no shuffle, no boss partition
  } else if (state.mode === "flash") {
    state.deck = shuffle(items);                 // no boss ordering for self-grade cards
  } else if (state.hard) {
    state.deck = shuffle(items);                 // recall-first review: plain shuffle, no boss
  } else {
    // Boss round: the hardest questions go last (2x XP). "Hardest" = calibrated
    // personal difficulty >= 0.55, or advanced-tagged when there's no telemetry
    // yet. Capped at the 3 hardest so a deck can't be all-boss.
    const reviews = state.progress.reviews || {};
    const scored = items.map((it) => {
      const pd = personalDifficulty(it.q, reviews[it.q.id], state._medMs);
      return { it, boss: (pd != null && pd >= 0.55) || (pd == null && it.q.difficulty === "advanced"), rank: pd == null ? -1 : pd };
    });
    const bossSet = new Set(scored.filter((s) => s.boss).sort((a, b) => b.rank - a.rank).slice(0, 3).map((s) => s.it));
    const normal = items.filter((it) => !bossSet.has(it));
    const boss = items.filter((it) => bossSet.has(it));
    boss.forEach((it) => (it.boss = true));
    state.deck = [...normal, ...boss];
  }
  state.queue = state.deck.map((_, i) => i);
  state.cursor = 0;
  state.combo = 0; state.maxCombo = 0; state.sessionXp = 0;
  state.sessSeq = []; state.sessMaxInterval = 0; state.sessRestored = false;   // [C] ledger award tracking
  state.replayFn = replayFn;
  state.startedAt = Date.now();
  setQuizHash(state.section);
  state.mode === "flash" ? renderCard() : renderQuestion();
}

async function startBlitz(section, modules) {
  app.innerHTML = `<div class="loading">Loading ${esc(label(section))}&hellip;</div>`;
  let bank = await loadBank(section);
  if (!bank || !bank.length) {
    app.innerHTML = `<div class="error">Couldn't load questions for ${esc(section)}. Run <code>python3 extract.py</code>.</div>`;
    return;
  }
  if (modules && modules.length) bank = bank.filter((q) => modules.includes(q.module));
  state.section = section;
  state.modules = modules && modules.length ? modules : null;
  const picked = shuffle(bank.slice()).slice(0, QUESTIONS_PER_BLITZ);
  startDeck(picked, () => startBlitz(section, state.modules));
}

async function startReview() {
  app.innerHTML = `<div class="loading">Gathering your review deck&hellip;</div>`;
  const due = dueReviews().slice(0, QUESTIONS_PER_BLITZ + 4);
  const bySec = {};
  due.forEach(([id, r]) => (bySec[r.section] = bySec[r.section] || []).push(id));
  const items = [];
  for (const sec of Object.keys(bySec)) {
    const bank = await loadBank(sec);
    if (!bank) continue;
    const byId = new Map(bank.map((q) => [q.id, q]));
    for (const id of bySec[sec]) {
      const q = byId.get(id);
      if (q) items.push(q);
      // Orphaned review (question no longer in the bank): self-heal so the due
      // count stops advertising questions that can never be served again.
      else delete state.progress.reviews[id];
    }
  }
  if (!items.length) { renderHome(); return; }
  state.section = "review"; state.modules = null;
  startDeck(items.slice(0, QUESTIONS_PER_BLITZ), startReview, { hard: true });
}

/* ---------- weak spots ---------- */
// Sections with enough data, ranked worst-accuracy first.
function weakSections() {
  const secs = state.progress.sections || {};
  return Object.entries(secs)
    .filter(([, st]) => (st.seen || 0) >= 5)
    .map(([s, st]) => ({ s, acc: st.correct / st.seen, seen: st.seen }))
    .sort((a, b) => a.acc - b.acc);
}

async function startWeakSpots() {
  app.innerHTML = `<div class="loading">Finding your weak spots&hellip;</div>`;
  const weak = weakSections().filter((x) => x.acc < 0.7).slice(0, 4);
  const pool = (weak.length ? weak : weakSections().slice(0, 3));
  if (!pool.length) { renderHome(); return; }
  const reviews = state.progress.reviews || {};
  const banks = {}, byId = {};
  for (const p of pool) {
    const b = await loadBank(p.s);
    if (b) { banks[p.s] = b; byId[p.s] = new Map(b.map((q) => [q.id, q])); }
  }
  const items = [], seen = new Set();
  const add = (q) => { if (q && !seen.has(q.id)) { items.push(q); seen.add(q.id); } };
  // 1) known trouble questions in weak sections: most lapses first
  Object.entries(reviews)
    .filter(([, r]) => (r.lapses || 0) > 0 && byId[r.section])
    .sort((a, b) => (b[1].lapses || 0) - (a[1].lapses || 0))
    .forEach(([id, r]) => { if (items.length < QUESTIONS_PER_BLITZ) add(byId[r.section].get(id)); });
  // 2) fill with random questions from the weak sections
  const filler = [];
  for (const p of pool) if (banks[p.s]) filler.push(...banks[p.s]);
  shuffle(filler).forEach((q) => { if (items.length < QUESTIONS_PER_BLITZ) add(q); });
  if (!items.length) { renderHome(); return; }
  state.section = "weakspots"; state.modules = null;
  startDeck(items.slice(0, QUESTIONS_PER_BLITZ), startWeakSpots, { hard: true });
}

/* ---------- session guard: pause / resume ---------- */
// A live deck is snapshotted to localStorage after every answer/skip/grade so a
// refresh (or navigating away) can resume the exact same blitz. optOrder makes
// the option arrangement pixel-identical; queue/cursor/combo/XP restore progress.
function saveDeckSnapshot() {
  if (!state.inQuiz || !state.deck.length) return;
  if (state.section === "interview") return;       // [C] an interview can't pause — leaving reschedules it
  const snap = {
    date: todayISO(), section: state.section, modules: state.modules, mode: state.mode, hard: state.hard,
    items: state.deck.map((d) => ({
      id: d.q.id, optOrder: d.optOrder, status: d.status, boss: d.boss,
      retry: d.retry, retried: d.retried, redeemed: d.redeemed, taught: d.taught, revealed: d.revealed, conf: d.conf, picked: d.picked,
    })),
    queue: state.queue, cursor: state.cursor,
    combo: state.combo, maxCombo: state.maxCombo, sessionXp: state.sessionXp,
    startedAt: state.startedAt,
  };
  try { localStorage.setItem("sd_active_deck", JSON.stringify(snap)); } catch { /* quota */ }
}

function readDeckSnapshot() {
  let snap = null;
  try { snap = JSON.parse(localStorage.getItem("sd_active_deck")); } catch { /* corrupt */ }
  return snap && Array.isArray(snap.items) && snap.items.length ? snap : null;
}

function clearDeckSnapshot() { localStorage.removeItem("sd_active_deck"); }

// Previous-day snapshots are discarded silently on boot — a resume must be same-day.
function discardStaleDeck() {
  const snap = readDeckSnapshot();
  if (snap && snap.date !== todayISO()) clearDeckSnapshot();
}

function resumeSummary() {
  const snap = readDeckSnapshot();
  if (!snap || snap.date !== todayISO()) return null;
  const DONE = ["correct", "wrong", "learned"];
  const done = snap.items.filter((it) => DONE.includes(it.status)).length;
  return { snap, done, total: snap.items.length, combo: snap.combo || 0 };
}

// Rebuild state.deck from a snapshot: re-fetch the bank(s), map ids -> questions,
// restore the option order and per-item status, then render the current card.
async function resumeDeck() {
  const snap = readDeckSnapshot();
  if (!snap) { renderHome(); return; }
  app.innerHTML = `<div class="loading">Resuming your blitz&hellip;</div>`;
  // Gather every bank the snapshot's questions live in (a review deck spans sections).
  const sections = new Set();
  const secOf = (id) => id.split("/")[0];
  snap.items.forEach((it) => sections.add(secOf(it.id)));
  const byId = new Map();
  for (const sec of sections) {
    const bank = await loadBank(sec);
    if (bank) for (const q of bank) byId.set(q.id, q);
  }
  const deck = [], idxMap = new Map();
  snap.items.forEach((it, oldIdx) => {
    const q = byId.get(it.id);
    if (!q) return;                                // orphaned question: drop gracefully
    const item = makeItem(q, it.optOrder);
    item.status = it.status; item.boss = !!it.boss;
    item.retry = !!it.retry; item.retried = !!it.retried; item.redeemed = !!it.redeemed;
    item.taught = !!it.taught; item.revealed = !!it.revealed;
    if (it.conf) item.conf = it.conf;
    if (it.picked != null) { item.picked = it.picked; item.pickedOpt = item.opts[it.picked]; }
    idxMap.set(oldIdx, deck.length);
    deck.push(item);
  });
  if (!deck.length) { clearDeckSnapshot(); renderHome(); return; }
  const queue = (snap.queue || []).map((i) => idxMap.get(i)).filter((i) => i !== undefined);
  if (!queue.length) { clearDeckSnapshot(); renderHome(); return; }
  let cursor = 0;                                  // count surviving pre-cursor queue slots
  for (let k = 0; k < (snap.cursor || 0) && k < (snap.queue || []).length; k++)
    if (idxMap.has(snap.queue[k])) cursor++;
  const DONE = ["correct", "wrong", "learned"];
  // A pending redemption re-test (wrong + retry) and a skipped teach/test slot
  // aren't "resolved" even though the item carries a terminal-ish status, so the
  // leading-skip must stop on them.
  const resolved = (it) => it.status !== "skipped" && !(it.status === "wrong" && it.retry && !it.retried) && DONE.includes(it.status);
  while (cursor < queue.length && resolved(deck[queue[cursor]])) cursor++;
  state.hard = !!snap.hard; state.awaitingConf = false; state._medMs = medianReviewMs();
  state.mode = snap.mode === "flash" ? "flash" : "quiz";
  state.deck = deck; state.queue = queue; state.cursor = cursor;
  state.combo = snap.combo || 0; state.maxCombo = snap.maxCombo || 0;
  state.sessionXp = snap.sessionXp || 0;
  state.section = snap.section; state.modules = snap.modules || null;
  state.startedAt = snap.startedAt || Date.now();
  state.replayFn = snap.section === "review" ? startReview
    : snap.section === "weakspots" ? startWeakSpots
    : () => startBlitz(snap.section, snap.modules);
  /* [C] gauntlet snapshots: restore the flags + replay; the rebuilt deck keeps
     the frozen qids/order (ids + optOrder round-trip, no reshuffle). */
  if (snap.section === "gauntlet") { state.gauntlet = { scored: true, practice: false }; state.replayFn = startGauntlet; }
  else if (snap.section === "gauntlet-practice") { state.gauntlet = { scored: false, practice: true }; state.replayFn = startGauntlet; }
  state.sessSeq = []; state.sessMaxInterval = 0; state.sessRestored = false;   // [C]
  state.inQuiz = true;
  setQuizHash(state.section);
  if (cursor >= queue.length) { finish(); return; } // every card answered: go straight to results
  state.mode === "flash" ? renderCard() : renderQuestion();
}

/* ---------- quiz ---------- */
function isLastInQueue() { return state.cursor >= state.queue.length - 1; }

function comboMult() { return state.combo >= 5 ? 3 : state.combo >= 3 ? 2 : 1; }

// Progress counter over the stable deck (queue length lies once items requeue
// for redemption / teach-then-test): answered-of-total, plus pending redos.
function deckProgressCounter() {
  const DONE = ["correct", "wrong", "learned"];
  const answered = state.deck.filter((it) => DONE.includes(it.status)).length;
  const redo = state.deck.filter((it) => it.retry && !it.retried).length;
  return `${answered}/${state.deck.length}${redo ? ` &middot; ${redo} redo` : ""}`;
}
function dotsHTML(idx) {
  const DONE = ["correct", "wrong", "learned"];
  return state.deck.map((it, i) =>
    `<span class="dot ${DONE.includes(it.status) ? "done" : ""} ${it.boss ? "boss" : ""} ${i === idx ? "cur" : ""}"></span>`).join("");
}

function renderQuestion() {
  const idx = state.queue[state.cursor];
  const item = state.deck[idx];
  const { q } = item;
  // A skipped question is taught first (concept card), then tested later.
  if (item.status === "skipped" && !item.taught) { renderTeach(item); return; }
  const testView = item.status === "skipped" && item.taught;                    // A5 lock-it-in test
  const retryView = item.status === "wrong" && item.retry && !item.retried;     // A1 redemption re-test
  if (retryView) { shuffle(item.opts); item.optOrder = item.opts.map((o) => o.src); }  // fresh arrangement
  const opts = item.opts;
  state.inQuiz = true; state.answered = false; state.awaitingConf = false; state.curOptsLen = opts.length;
  state.qShownAt = performance.now();
  const gated = state.hard && !item.revealed;                                    // A3 recall-first reveal gate
  const bossBanner = item.boss && !testView && !retryView
    ? `<div class="boss-banner">&#9889; BOSS QUESTION &middot; 2&times; XP</div>` : "";
  const chip = retryView ? `<span class="redo-chip">Redemption round</span>`
    : testView ? `<span class="lockin-chip">Lock it in</span>` : "";
  // "hard for you" (calibrated) overrides the positional difficulty label.
  if (state._medMs == null) state._medMs = medianReviewMs();
  const pd = personalDifficulty(q, (state.progress.reviews || {})[q.id], state._medMs);
  const diffChip = pd != null && pd >= 0.55
    ? `<span class="diff d-personal">hard for you</span>`
    : `<span class="diff d-${esc(q.difficulty)}">${esc(q.difficulty)}</span>`;
  const nextMult = state.combo + 1 >= 5 ? 3 : state.combo + 1 >= 3 ? 2 : 1;
  const comboChip = !testView && !retryView && state.combo >= 2
    ? `<span class="combo">${ICON("flame", "i-flame")} ${state.combo} combo &middot; ${nextMult}&times; XP</span>` : "";
  // Prefer the *Md display variant per option (src 0 = correct, i+1 = distractors[i]).
  const optText = (o) => o.src === 0 ? (q.correctMd || q.correct)
    : (q.distractorsMd && q.distractorsMd[o.src - 1]) || o.t;
  app.innerHTML = `
    <div class="qhead">
      <span class="module">${esc(label(q.section))} &middot; ${esc(q.moduleName)} ${diffChip}</span>
      <span class="qright"><button class="qpause" id="qpauseBtn" title="Pause this blitz" aria-label="Pause this blitz">II</button><span class="dots" role="img" aria-label="Question ${state.cursor + 1} of ${state.queue.length}">${dotsHTML(idx)}</span><span class="qnum">${deckProgressCounter()}</span></span>
    </div>
    ${bossBanner}
    <div class="qtext">${qInline(q.questionMd || q.question)} ${chip}${comboChip}</div>
    ${gated ? `<button class="showopts" id="showOptsBtn">Show options <kbd>Space</kbd></button>` : ""}
    <div class="options${gated ? " gated" : ""}">
      ${opts.map((o, i) => `<button class="opt" data-i="${i}"><kbd>${i + 1}</kbd>${qInline(optText(o))}<span class="mark"></span></button>`).join("")}
    </div>
    <div class="reveal" id="reveal"></div>
    <div class="qactions">
      ${item.status === "pending" ? `<button class="skip" id="skipBtn">Skip for now (S) &rarr;</button>` : "<span></span>"}
      <button class="next" id="nextBtn">${isLastInQueue() ? "Finish" : "Next (↵)"}</button>
    </div>`;
  document.querySelectorAll(".opt").forEach((b) =>
    b.addEventListener("click", () => answer(parseInt(b.dataset.i, 10))));
  if (gated) el("#showOptsBtn").addEventListener("click", revealHardOptions);
  if (item.status === "pending") el("#skipBtn").addEventListener("click", skipQuestion);
  el("#nextBtn").addEventListener("click", nextQuestion);
  el("#qpauseBtn").addEventListener("click", () => openPauseSheet(null));
  /* [C] interviewer stage (avatar + HP bar) / gauntlet practice banner */
  if (state.interview) renderInterviewStage();
  else if (state.gauntlet && state.gauntlet.practice) renderPracticeBanner();
  app.focus({ preventScroll: true });              // keep keyboard + SR context on the new question
}

// A3: reveal the hidden options for a recall-first (hard-deck) question. Leave
// qShownAt untouched so the recorded time spans think + answer as one number.
function revealHardOptions() {
  const item = state.deck[state.queue[state.cursor]];
  if (!item || item.revealed) return;
  item.revealed = true;
  const wrap = el(".options"); if (wrap) wrap.classList.remove("gated");
  const btn = el("#showOptsBtn"); if (btn) btn.remove();
  const first = document.querySelector(".opt"); if (first) first.focus();
  announce("Options shown.");
}

// A5: full-width concept card for a skipped question. Its key concept tokens
// become blurred cloze chips the learner taps to reveal (first occurrence each,
// max 5, word-boundary, case-insensitive).
function clozeHTML(text, concepts) {
  let html = qInline(text);
  const seen = new Set();
  let made = 0;
  for (const raw of concepts || []) {
    if (made >= 5) break;
    const tok = String(raw || "").trim();
    if (tok.length < 3 || seen.has(tok.toLowerCase())) continue;
    seen.add(tok.toLowerCase());
    const re = new RegExp(`\\b(${tok.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")})\\b`, "i");
    let hit = false;
    html = html.replace(re, (m, g1, off, str) => {
      // Skip matches that fall inside an html tag or entity we just emitted.
      const before = str.slice(0, off);
      if (/<[^>]*$/.test(before) || /&[^;]*$/.test(before)) return m;
      hit = true;
      return `<button class="cloze" type="button">${g1}</button>`;
    });
    if (hit) made++;
  }
  return html;
}
function renderTeach(item) {
  const { q } = item;
  const idx = state.queue[state.cursor];
  state.inQuiz = true; state.answered = false; state.awaitingConf = false; state.curOptsLen = 0;
  state.qShownAt = performance.now();
  const hasCloze = (q.concepts || []).length > 0;
  app.innerHTML = `
    <div class="qhead">
      <span class="module">${esc(label(q.section))} &middot; ${esc(q.moduleName)}</span>
      <span class="qright"><button class="qpause" id="qpauseBtn" title="Pause this blitz" aria-label="Pause this blitz">II</button><span class="dots" role="img" aria-label="Question ${state.cursor + 1} of ${state.queue.length}">${dotsHTML(idx)}</span><span class="qnum">${deckProgressCounter()}</span></span>
    </div>
    <div class="teach-chip">Concept preview &middot; you skipped this &mdash; learn it, we quiz you shortly</div>
    <div class="qtext teach-head">${qInline(q.questionMd || q.question)}</div>
    <div class="reveal concept show teach-concept">${clozeHTML(q.answerFullMd || q.answerFull, q.concepts)}${hasCloze ? `<div class="cloze-hint">Tap the blurred terms to reveal them</div>` : ""}</div>
    <div class="qactions">
      <span></span>
      <button class="next show" id="gotItBtn">Got it &mdash; quiz me later (↵)</button>
    </div>`;
  document.querySelectorAll(".cloze").forEach((c) => c.addEventListener("click", () => c.classList.add("shown")));
  el("#gotItBtn").addEventListener("click", teachDone);
  el("#qpauseBtn").addEventListener("click", () => openPauseSheet(null));
  /* [C] keep the interviewer stage / practice banner across the teach card */
  if (state.interview) renderInterviewStage();
  else if (state.gauntlet && state.gauntlet.practice) renderPracticeBanner();
  app.focus({ preventScroll: true });
}
function teachDone() {
  const item = state.deck[state.queue[state.cursor]];
  if (!item || item.status !== "skipped" || item.taught) return;
  item.taught = true;                              // the next skipped encounter is the test
  nextQuestion();
  saveDeckSnapshot();                              // teach records nothing beyond "taught"
}

// A4: first tap on an option locks the pick and asks for confidence; grading
// waits until the learner says how sure they were. testMode / retryMode grade
// immediately (no calibration on lock-ins or redemption re-tests).
function answer(i) {
  if (state.answered || state.awaitingConf) return;
  const item = state.deck[state.queue[state.cursor]];
  const testMode = item.status === "skipped";
  const retryMode = item.status === "wrong" && item.retry && !item.retried;
  if (!testMode && !retryMode) { lockChoice(i); return; }
  gradeAnswer(i, null);
}
function lockChoice(i) {
  state.awaitingConf = true; state.pendingPick = i;
  document.querySelectorAll(".opt").forEach((b, k) => {
    b.disabled = true;
    b.classList.add(k === i ? "picked" : "dim");
  });
  const bar = document.createElement("div");
  bar.className = "confbar"; bar.id = "confBar";
  bar.innerHTML = `<span class="conf-q">How sure?</span>
    <button class="conf-btn" id="confSure"><kbd>1</kbd> Sure</button>
    <button class="conf-btn" id="confUnsure"><kbd>2</kbd> Not sure</button>`;
  el(".options").insertAdjacentElement("afterend", bar);
  el("#confSure").addEventListener("click", () => pickConfidence("high"));
  el("#confUnsure").addEventListener("click", () => pickConfidence("low"));
  announce("Choice locked. How sure are you?");
}
function pickConfidence(conf) {
  if (!state.awaitingConf) return;
  state.awaitingConf = false;
  const bar = el("#confBar"); if (bar) bar.remove();
  gradeAnswer(state.pendingPick, conf);
}

function gradeAnswer(i, conf) {
  if (state.answered) return;
  state.answered = true;
  const item = state.deck[state.queue[state.cursor]];
  item.ms = Math.max(0, Math.round(performance.now() - (state.qShownAt || performance.now())));
  const { opts } = item;
  const testMode = item.status === "skipped";
  const retryMode = item.status === "wrong" && item.retry && !item.retried;
  const right = opts[i].ok;
  const optBtns = document.querySelectorAll(".opt");
  optBtns.forEach((b, k) => {
    b.disabled = true; b.classList.remove("dim", "picked");
    if (opts[k].ok) { b.classList.add("correct"); b.querySelector(".mark").textContent = "✓"; }
    if (k === i && !opts[k].ok) { b.classList.add("wrong"); b.querySelector(".mark").textContent = "✗"; }
  });
  announce(right ? "Correct." : `Incorrect. The answer is: ${opts.find((o) => o.ok).t}`);

  if (retryMode) {
    item.retried = true;
    if (right) { item.redeemed = true; state.sessionXp += 5; floatXP(5, optBtns[i]); sfx.correct(); }  // flat bonus, no combo
    else sfx.wrong();                                                                                   // still shaky
  } else if (testMode) {
    if (right) { item.status = "learned"; sfx.correct(); }                                              // no XP for a lock-in
    else { item.status = "wrong"; item.picked = i; item.pickedOpt = opts[i]; sfx.wrong(); }
  } else {
    item.conf = conf;
    if (right) {
      item.status = "correct";
      state.combo += 1; state.maxCombo = Math.max(state.maxCombo, state.combo);
      const gain = Math.round(10 * comboMult() * (item.boss ? 2 : 1) * (state.hard ? 1.5 : 1));         // A3 recall pays 1.5x
      state.sessionXp += gain;
      floatXP(gain, optBtns[i]);
      if (state.combo === 3 || state.combo === 5 || state.combo >= 7) { sfx.combo(); ripple(optBtns[i]); }
      else sfx.correct();
    } else {
      item.status = "wrong"; item.picked = i; item.pickedOpt = opts[i];
      state.combo = 0; sfx.wrong();
      if (!item.retry && !state.interview) {         // A1 miss loop: one in-session redemption re-test ([C] the Interviewer probes instead)
        item.retry = true;
        const at = Math.min(state.cursor + 3, state.queue.length);
        state.queue.splice(at, 0, state.queue[state.cursor]);
      }
    }
  }
  const sk = el("#skipBtn"); if (sk) sk.remove();
  buildReveal(item, i, right, { testMode, retryMode, conf });
  el("#nextBtn").classList.add("show");
  /* [C] ledger tracking (first attempts only) + interviewer HP / follow-up */
  if (!testMode && !retryMode) {
    if (right) {
      const preRv = (state.progress.reviews || {})[item.q.id];
      if (preRv) {
        state.sessMaxInterval = Math.max(state.sessMaxInterval || 0, preRv.interval || 0);   // long_memory
        if ((preRv.lapses || 0) >= 3 && (preRv.ease || 2.5) >= 2.4) state.sessRestored = true;  // restored (ease lands >= 2.5)
      }
    }
    (state.sessSeq = state.sessSeq || []).push(right ? "c" : "w");                           // comeback
    if (state.interview) interviewAfterAnswer(item, right);
  }
  saveDeckSnapshot();
}

// The reveal panel: full answer (A2 honest provenance for a wrong pick, plus a
// hypercorrection lead on a high-confidence miss), and dive-deeper to the exact
// source file the Q&A came from.
function buildReveal(item, pickIdx, right, ctx) {
  const { q, opts } = item;
  const rev = el("#reveal");
  const hyper = !right && ctx.conf === "high";                     // wrong + sure
  let prov = "";
  if (!right) {
    const src = distractorSource(q, opts[pickIdx]);
    if (src) prov = `<div class="prov">You picked the answer to: <span class="prov-q">${qInline(src.questionMd || src.question)}</span> &mdash; from ${esc(src.moduleName)}.
      <button class="deeper prov-read" data-mod="${esc(src.module)}" data-src="${esc(src.sourceFile || "README.md")}" data-name="${esc(src.moduleName)}">Read that instead &rarr;</button></div>`;
  }
  rev.className = "reveal show" + (hyper ? " hyper" : "");
  rev.innerHTML = `${hyper ? `<div class="hyper-lead">High-confidence miss &mdash; worth a careful read.</div>` : ""}<b>Full answer:</b> ${qInline(q.answerFullMd || q.answerFull)}${prov}
    <button class="deeper" id="deeperBtn">Dive deeper into ${esc(q.moduleName)} &rarr;</button>`;
  el("#deeperBtn").addEventListener("click", () => openReaderPath(`${q.module}/${q.sourceFile || "README.md"}`, q.moduleName));
  const pr = rev.querySelector(".prov-read");
  if (pr) pr.addEventListener("click", () => openReaderPath(`${pr.dataset.mod}/${pr.dataset.src}`, pr.dataset.name));
  /* [C] deep_habit: dive-deeper opens from a MISS reveal bump the persisted counter */
  if (!right) {
    el("#deeperBtn").addEventListener("click", bumpDeepReads);
    if (pr) pr.addEventListener("click", bumpDeepReads);
  }
}

// A5: skip now teaches then re-tests. The teach card is rendered next; the test
// (a normal MCQ with no answer shown) returns at least 3 items later.
function skipQuestion() {
  const idx = state.queue[state.cursor];
  const item = state.deck[idx];
  if (item.status !== "pending") return;             // double-click guard
  item.status = "skipped"; item.taught = false;
  state.queue.splice(state.cursor + 1, 0, idx);      // TEACH: render next
  const testAt = Math.min(state.cursor + 4, state.queue.length);
  state.queue.splice(testAt, 0, idx);                // TEST: at least 3 items later (else end)
  nextQuestion();
  saveDeckSnapshot();                                // reflects the advanced cursor + re-queued teach/test
}

function nextQuestion() {
  state.cursor++;
  if (state.cursor < state.queue.length) renderQuestion();
  else finish();
}

/* ---------- flashcard (self-grade) mode ---------- */
function renderCard() {
  const idx = state.queue[state.cursor];
  const item = state.deck[idx];
  const { q } = item;
  state.inQuiz = true; state.answered = false; state.curOptsLen = 0;
  state.qShownAt = performance.now();
  const DONE = ["correct", "wrong", "learned"];
  const dots = state.deck.map((it, i) =>
    `<span class="dot ${DONE.includes(it.status) ? "done" : ""} ${i === idx ? "cur" : ""}"></span>`).join("");
  app.innerHTML = `
    <div class="qhead">
      <span class="module">${esc(label(q.section))} &middot; ${esc(q.moduleName)}</span>
      <span class="qright"><button class="qpause" id="qpauseBtn" title="Pause this blitz" aria-label="Pause this blitz">II</button><span class="dots" role="img" aria-label="Card ${state.cursor + 1} of ${state.queue.length}">${dots}</span><span class="qnum">${state.cursor + 1}/${state.queue.length}</span></span>
    </div>
    <div class="flash-label">Flashcard &middot; recall it, then grade yourself</div>
    <div class="qtext">${esc(q.question)}</div>
    <div class="reveal" id="reveal"></div>
    <div class="qactions" id="cardActions">
      <span></span>
      <button class="next show" id="revealBtn">Reveal answer (Space)</button>
    </div>`;
  el("#revealBtn").addEventListener("click", revealCard);
  el("#qpauseBtn").addEventListener("click", () => openPauseSheet(null));
  app.focus({ preventScroll: true });
}

function revealCard() {
  if (state.answered) return;
  state.answered = true;
  const { q } = state.deck[state.queue[state.cursor]];
  const rev = el("#reveal");
  rev.innerHTML = `<b>Answer:</b> ${qInline(q.answerFullMd || q.answerFull)}
    <button class="deeper" id="deeperBtn">Dive deeper into ${esc(q.moduleName)} &rarr;</button>`;
  rev.classList.add("show");
  announce(`Answer: ${q.answerFull}`);
  el("#deeperBtn").addEventListener("click", () => openReaderPath(`${q.module}/${q.sourceFile || "README.md"}`, q.moduleName));
  // A4: three-way self-grade folds into the confidence signal — Hard/Easy both
  // count correct, but record how sure the recall felt.
  el("#cardActions").innerHTML = `
    <button class="grade miss" id="missBtn"><kbd>1</kbd> Missed</button>
    <button class="grade hard" id="hardBtn"><kbd>2</kbd> Hard</button>
    <button class="grade got" id="easyBtn"><kbd>3</kbd> Easy</button>`;
  el("#missBtn").addEventListener("click", () => gradeCard(false, null));
  el("#hardBtn").addEventListener("click", () => gradeCard(true, "low"));
  el("#easyBtn").addEventListener("click", () => gradeCard(true, "high"));
}

// Self-grade feeds the SAME results pipeline as the MCQ blitz, so it drives the
// existing SM-2 schedule. XP is flat (no combo/boss) so self-grading can't inflate
// score versus the verifiable multiple-choice path.
function gradeCard(got, conf) {
  if (!state.answered) return;
  const item = state.deck[state.queue[state.cursor]];
  item.ms = Math.max(0, Math.round(performance.now() - (state.qShownAt || performance.now())));
  if (got) { item.status = "correct"; item.conf = conf || null; state.sessionXp += 10; sfx.correct(); floatXP(10, el("#easyBtn") || el("#hardBtn")); }
  else { item.status = "wrong"; sfx.wrong(); }
  /* [C] ledger tracking (flashcard path) */
  if (got) {
    const preRv = (state.progress.reviews || {})[item.q.id];
    if (preRv) {
      state.sessMaxInterval = Math.max(state.sessMaxInterval || 0, preRv.interval || 0);
      if ((preRv.lapses || 0) >= 3 && (preRv.ease || 2.5) >= 2.4) state.sessRestored = true;
    }
  }
  (state.sessSeq = state.sessSeq || []).push(got ? "c" : "w");
  state.cursor++;
  if (state.cursor < state.queue.length) renderCard();
  else finish();
  saveDeckSnapshot();                              // guarded: no-op once finish() ends the deck
}

async function finish(opts = {}) {
  state.inQuiz = false;
  clearDeckSnapshot();                             // the deck is resolved; no resume
  app.innerHTML = `<div class="loading">Saving your progress&hellip;</div>`;
  const DONE = ["correct", "wrong", "learned"];
  // Early finish ("Finish now") records only attempted cards; a normal finish
  // records the whole deck (every card has a terminal status by then).
  const recorded = opts.early ? state.deck.filter((d) => DONE.includes(d.status)) : state.deck;
  const total = recorded.length;
  const correct = recorded.filter((d) => d.status === "correct").length;
  const learned = recorded.filter((d) => d.status === "learned").length;
  const cCtx = cBeforeFinish();                    // [C] seal bonus (mutates sessionXp) + pre-save context
  const bonusXp = Math.max(0, state.sessionXp - correct * 10);
  const results = recorded.map((d) => ({ id: d.q.id, section: d.q.section, module: d.q.module, status: d.status, ms: d.ms || 0, conf: d.conf || null }));
  const durationSec = Math.max(0, Math.round((Date.now() - (state.startedAt || Date.now())) / 1000));
  const pre = progressSnapshot();                  // for the moments engine (before the save)
  const { xp, freezeUsed } = saveSessionLocal({ date: todayISO(), section: state.section, results, bonusXp, durationSec });
  const cExtra = cAfterSave(cCtx, { correct, total });   // [C] seal gauntlet · resolve interview · detect ledger awards
  await queueMoments(pre, progressSnapshot(), cExtra);   // celebrate milestones before the results ([C] extras lead)
  refreshStats();
  const pct = total ? Math.round((correct / total) * 100) : 0;
  const flawless = pct === 100 && total > 0;
  if (flawless) { confetti(); sfx.finish(); }
  const cheer = flawless ? "Flawless! " : pct >= 70 ? "Strong work. " : pct >= 40 ? "Good progress. " : "Every rep counts. ";
  announce(`Blitz finished. ${correct} of ${total} correct. ${xp} XP earned.`);
  const freezeNote = freezeUsed
    ? `<div class="freeze-saved">${ICON("snow", "i-snow")} Streak saved &mdash; 1 freeze used (${state.progress.freezes || 0} left)</div>` : "";
  const backupNote = backupNudgeHTML();
  const extraBadges =
    (learned ? `<div class="badge"><div class="n">${learned}</div><div class="l">Learned</div></div>` : "") +
    (state.maxCombo >= 2 ? `<div class="badge"><div class="n">${state.maxCombo}&times;</div><div class="l">Best combo</div></div>` : "");
  // Post-round review: misses split into Redeemed (retry-correct) and Still
  // shaky, plus questions learned from a skip. Each is a <details> — summary is
  // the correct sentence, expanding reveals the full answer (+ honest provenance
  // for a wrong pick). Redeemed items still recorded their first-attempt wrong.
  const wrongs = recorded.filter((d) => d.status === "wrong");
  const redeemed = wrongs.filter((d) => d.redeemed);
  const shaky = wrongs.filter((d) => !d.redeemed);
  const learnedItems = recorded.filter((d) => d.status === "learned");
  const missItem = (m) => {
    let body = qInline(m.q.answerFullMd || m.q.answerFull);
    if (m.status === "wrong" && m.pickedOpt) {
      const src = distractorSource(m.q, m.pickedOpt);
      if (src) body += `<div class="prov">You picked the answer to: <span class="prov-q">${qInline(src.questionMd || src.question)}</span> &mdash; from ${esc(src.moduleName)}.</div>`;
    }
    return `<details class="miss-item ${m.status}${m.redeemed ? " redeemed" : ""}">
        <summary class="miss-q">${qInline(m.q.correctMd || m.q.correct)}</summary>
        <div class="miss-a">${body}</div>
        <button class="deeper miss-deeper" data-mod="${esc(m.q.module)}" data-src="${esc(m.q.sourceFile || "README.md")}" data-name="${esc(m.q.moduleName)}">Dive deeper into ${esc(m.q.moduleName)} &rarr;</button>
      </details>`;
  };
  const group = (title, cls, arr) => arr.length ? `<h3 class="miss-group ${cls}">${title}</h3>${arr.map(missItem).join("")}` : "";
  const missList = (wrongs.length || learnedItems.length) ? `
    <div class="miss-wrap">
      <h2 class="section-h">Review this round</h2>
      ${group("Redeemed", "good", redeemed)}
      ${group("Still shaky", "warn", shaky)}
      ${group("Learned from a skip", "", learnedItems)}
    </div>` : "";
  const R = 56, CIRC = +(2 * Math.PI * R).toFixed(1);
  app.innerHTML = `
    <div class="result">
      <div class="score-wrap">
        <svg class="score-ring" viewBox="0 0 128 128" aria-hidden="true">
          <defs><linearGradient id="scoreGrad" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" style="stop-color:var(--accent)"/>
            <stop offset="1" style="stop-color:var(--accent-2)"/>
          </linearGradient></defs>
          <circle class="sr-bg" cx="64" cy="64" r="${R}"/>
          <circle class="sr-fg" cx="64" cy="64" r="${R}" stroke-dasharray="${CIRC}" stroke-dashoffset="${CIRC}"/>
        </svg>
        <div class="scorering">${correct}<small>/${total}</small></div>
      </div>
      <p class="sub">${cheer}${pct}% known${learned ? ` &middot; ${learned} learned` : ""}</p>
      ${freezeNote}${backupNote}
      <div class="badges">
        <div class="badge"><div class="n" id="xpCount">+0</div><div class="l">XP</div></div>
        ${extraBadges}
        <div class="badge"><div class="n">${state.progress.streak || 0}</div><div class="l">Day streak</div></div>
        <div class="badge"><div class="n">${state.progress.totalXP || 0}</div><div class="l">Total XP</div></div>
      </div>
      <div class="row">
        <button class="primary" id="againBtn">Play another</button>
        <button class="ghost" id="homeBtn">Home</button>
        <button class="ghost" id="progBtn">View progress</button>
      </div>
      ${missList}
    </div>`;
  countUp(el("#xpCount"), xp);
  requestAnimationFrame(() => {                    // animate the score arc in
    const f = el(".sr-fg");
    if (f) f.style.strokeDashoffset = (CIRC * (1 - correct / Math.max(1, total))).toFixed(1);
  });
  el("#againBtn").addEventListener("click", () => (state.replayFn ? state.replayFn() : go("#/home")));
  el("#homeBtn").addEventListener("click", () => go("#/home"));
  el("#progBtn").addEventListener("click", () => go("#/progress"));
  document.querySelectorAll(".miss-deeper").forEach((b) =>
    b.addEventListener("click", (e) => { e.preventDefault(); e.stopPropagation(); openReaderPath(`${b.dataset.mod}/${b.dataset.src}`, b.dataset.name); }));
  wireReveals();
  app.focus({ preventScroll: true });
  /* [C] gauntlet / interviewer result banners, then drop the deck-scoped state */
  cApplyResults(cCtx, { correct });
  document.body.classList.remove("interview-mode");
  state.interview = null; state.gauntlet = null;
}

/* ---------- moments engine ---------- */
// A moment is a full-screen glass overlay celebrating a milestone. Moments are
// awaitable so finish() can play them one after another before the results.
const TIER_RANK = { Bronze: 1, Silver: 2, Gold: 3 };
const STREAK_MILES = [7, 14, 30, 50, 100];

// Snapshot the milestone-bearing fields of progress so a before/after diff can
// detect a level-up, tier promotion, streak milestone, cleared backlog, or goal.
function progressSnapshot() {
  const p = state.progress, tiers = {};
  for (const s of Object.keys(p.sections || {})) tiers[s] = sectionTier(p.sections[s]);
  return {
    level: levelFromXP(p.totalXP), streak: p.streak || 0,
    due: dueReviews().length, todaysXp: todaysXp(), tiers,
    anyGold: Object.values(tiers).some((t) => t === "Gold"),
    codex: cDeckCodex(),   /* [C] capture/foil state of only the modules the live deck touched */
  };
}

function moment({ tier = "", title, sub = "", icon = "" }) {
  return new Promise((resolve) => {
    const reduced = REDUCED();
    const o = document.createElement("div");
    o.className = "moment" + (tier ? " m-" + tier : "") + (reduced ? " reduced" : "");
    o.setAttribute("role", "status");
    o.innerHTML = `${reduced ? "" : `<span class="moment-burst"></span>`}
      <div class="moment-card">
        ${icon ? `<div class="moment-icon">${icon}</div>` : ""}
        <div class="moment-title">${esc(title)}</div>
        ${sub ? `<div class="moment-sub">${esc(sub)}</div>` : ""}
      </div>`;
    document.body.appendChild(o);
    announce(title + (sub ? ". " + sub : ""));
    let done = false;
    const close = () => {
      if (done) return; done = true;
      clearTimeout(timer);
      document.removeEventListener("keydown", onKey, true);
      o.classList.add("out");
      setTimeout(() => { o.remove(); resolve(); }, reduced ? 0 : 180);
    };
    const onKey = (e) => { e.preventDefault(); e.stopPropagation(); close(); };
    o.addEventListener("click", close);
    document.addEventListener("keydown", onKey, true);
    const timer = setTimeout(close, 2500);
  });
}

// Diff pre/post progress snapshots into an ordered moment list, then play them
// sequentially. Later phases add more moment types; the diff shape stays.
// [C] extra: pre-built deck-headline moments (gauntlet seal, interview verdict,
// ledger awards) that lead the queue.
async function queueMoments(pre, post, extra) {
  const list = [];
  if (post.level > pre.level)
    list.push({ tier: "level", icon: ICON("bolt"), title: `Level ${post.level}`, sub: "New level reached.", play: () => sfx.levelup() });
  let firstGold = post.anyGold && !pre.anyGold;
  for (const s of Object.keys(post.tiers)) {
    const before = TIER_RANK[pre.tiers[s]] || 0, after = TIER_RANK[post.tiers[s]] || 0;
    if (after <= before) continue;
    const t = post.tiers[s];
    if (t === "Gold" && firstGold) {
      firstGold = false;
      list.push({ tier: "gold", icon: `<span class="moment-tier gold">Gold</span>`, title: "First Gold", sub: `${label(s)} is your first Gold-tier section.`, play: () => sfx.gold() });
    } else {
      list.push({ tier: t.toLowerCase(), icon: `<span class="moment-tier ${t.toLowerCase()}">${t}</span>`, title: `${t}: ${label(s)}`, sub: "Mastery tier promoted.", play: () => sfx.tier() });
    }
  }
  const mile = STREAK_MILES.find((m) => pre.streak < m && post.streak >= m);
  if (mile)
    list.push({ tier: "streak", icon: ICON("flame", "i-flame"), title: `${mile}-day streak`, sub: "Consistency compounds.", play: () => sfx.finish() });
  if (pre.due > 0 && post.due === 0)
    list.push({ tier: "backlog", icon: ICON("clock"), title: "Backlog cleared", sub: "Nothing due. Your memory is current.", play: () => sfx.bell() });
  if (pre.todaysXp < DAILY_XP_GOAL && post.todaysXp >= DAILY_XP_GOAL)
    list.push({ tier: "goal", icon: ICON("bolt"), title: "Daily goal met", sub: `${DAILY_XP_GOAL} XP today.`, play: () => sfx.chime() });
  /* [C] codex captures/foils crossed by this deck (diffed from the snapshots) */
  for (const mod of Object.keys(post.codex || {})) {
    const a = (pre.codex || {})[mod] || {}, b = post.codex[mod];
    if (b.captured && !a.captured)
      list.push({ tier: "capture", icon: `<span class="c-flipcard"></span>`, title: `Captured: ${b.name}`, sub: "Added to your Codex.", play: () => sfx.capture() });
    if (b.foil && !a.foil)
      list.push({ tier: "foil", icon: `<span class="c-flipcard foil"></span>`, title: `Foil: ${b.name}`, sub: "Proven over 21 days.", play: () => sfx.gold() });
  }
  if (extra && extra.length) list.unshift(...extra);   /* [C] deck-headline moments lead */
  for (const m of list) {
    m.play?.();
    await moment(m);
  }
}

/* ---------- progress durability ---------- */
// The whole game lives in this browser's localStorage — a backup is the only
// safety net. Nudge on results every 25 sessions if no export in 30 days.
function backupNudgeHTML() {
  const n = (state.progress.history || []).length;
  if (!(n > 0 && n % 25 === 0)) return "";
  const last = localStorage.getItem("sd_last_export");
  const recent = last && (Date.now() - new Date(last + "T00:00:00").getTime()) < 30 * 86400000;
  if (recent) return "";
  return `<div class="backup-note">Your progress lives in this browser. Export a backup from Progress.</div>`;
}

// Keys that make up a full save (future gauntlet/codex keys join this list).
const BACKUP_KEYS = ["sd_progress", "sd_gauntlet"];

function exportProgress() {
  const blob = { version: 1, exportedAt: new Date().toISOString(), data: {} };
  for (const k of BACKUP_KEYS) { const v = localStorage.getItem(k); if (v != null) blob.data[k] = v; }
  const a = document.createElement("a");
  a.href = URL.createObjectURL(new Blob([JSON.stringify(blob, null, 2)], { type: "application/json" }));
  a.download = `sysdesign-daily-backup-${todayISO()}.json`;
  document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 2000);
  localStorage.setItem("sd_last_export", todayISO());
  announce("Backup exported.");
}

function importProgress(file) {
  const reader = new FileReader();
  reader.onload = () => {
    let blob;
    try { blob = JSON.parse(reader.result); } catch { alert("That file isn't a valid backup."); return; }
    if (!blob || typeof blob !== "object" || !blob.data || typeof blob.data !== "object" || blob.data.sd_progress == null) {
      alert("That file isn't a valid System Design Daily backup."); return;
    }
    if (!confirm("Import this backup? It replaces all current progress in this browser.")) return;
    for (const k of BACKUP_KEYS) { if (blob.data[k] != null) localStorage.setItem(k, blob.data[k]); }
    location.reload();
  };
  reader.readAsText(file);
}

/* ---------- session guard: pause sheet + guarded navigation ---------- */
// Any attempt to leave a live blitz with at least one answered card raises this
// glass confirm sheet. Zero answered -> leave silently, discard the snapshot.
function answeredCount() {
  const DONE = ["correct", "wrong", "learned"];
  return state.deck.filter((d) => DONE.includes(d.status)).length;
}

// guardedNav(fn): run fn now if it's safe; otherwise open the pause sheet with
// fn as the pending destination. Used by the router and the topbar nav handlers.
function guardedNav(fn) {
  if (!state.inQuiz) { fn(); return; }
  if (answeredCount() === 0) { clearDeckSnapshot(); state.inQuiz = false; fn(); return; }
  openPauseSheet(fn);
}

// pending: a function to run on leave (Pause & leave / Finish now). null -> Home.
function openPauseSheet(pending) {
  if (el("#pauseSheet")) return;
  const leave = pending || (() => go("#/home"));
  const done = answeredCount(), total = state.deck.length;
  const o = document.createElement("div");
  o.className = "pause-sheet"; o.id = "pauseSheet";
  o.setAttribute("role", "dialog"); o.setAttribute("aria-label", "Pause this blitz");
  o.innerHTML = `<div class="pause-card">
      <h2>Pause this blitz?</h2>
      <p>Your progress is saved &mdash; resume from Home. ${done}/${total} answered.</p>
      <div class="pause-btns">
        <button class="primary" id="pauseKeep">Keep playing</button>
        <button class="ghost" id="pauseLeave">Pause &amp; leave</button>
        <button class="ghost" id="pauseFinish">Finish now</button>
      </div>
    </div>`;
  document.body.appendChild(o);
  const closeSheet = () => { o.remove(); };
  el("#pauseKeep").addEventListener("click", closeSheet);
  el("#pauseLeave").addEventListener("click", () => { saveDeckSnapshot(); state.inQuiz = false; closeSheet(); leave(); });
  el("#pauseFinish").addEventListener("click", () => { closeSheet(); finish({ early: true }); });
  o.addEventListener("click", (e) => { if (e.target === o) closeSheet(); });
  el("#pauseKeep").focus();
}

/* ---------- progress ---------- */
// GitHub-style contribution grid from the (already persisted) history array.
// Columns are weeks (start aligns to a Sunday); cells are coloured by XP bucket.
function heatmapHTML(history) {
  const xpByDay = new Map();
  for (const h of history || []) xpByDay.set(h.date, (xpByDay.get(h.date) || 0) + (h.xp || 0));
  const gauntDays = new Set((history || []).filter((h) => h.section === "gauntlet").map((h) => h.date));  // [C] sealed-gauntlet days
  const WEEKS = 17;
  const today = new Date(todayISO() + "T00:00:00");
  const end = new Date(today); end.setDate(end.getDate() + (6 - today.getDay())); // Sat of this week
  const start = new Date(end); start.setDate(start.getDate() - (WEEKS * 7 - 1));   // a Sunday
  let cells = "";
  for (let i = 0; i < WEEKS * 7; i++) {
    const d = new Date(start); d.setDate(d.getDate() + i);   // setDate is DST-safe; raw ms math is not
    const iso = d.toLocaleDateString("en-CA");
    const xp = xpByDay.get(iso) || 0;
    if (d > today) { cells += `<span class="hmcell hm-future"></span>`; continue; }
    const lvl = xp === 0 ? 0 : xp < 30 ? 1 : xp < 70 ? 2 : xp < 120 ? 3 : 4;
    const gaunt = gauntDays.has(iso) ? " hm-gaunt" : "";                       // [C] gold-dot overlay
    cells += `<span class="hmcell hm-l${lvl}${gaunt}" style="animation-delay:${i * 3}ms" title="${iso}: ${xp} XP${gaunt ? " · gauntlet" : ""}"></span>`;
  }
  const empty = !(history || []).length
    ? `<p class="hm-empty">No activity yet &mdash; your first blitz lights up this grid.</p>` : "";
  return `<h2 class="section-h">Activity</h2>
    <div class="heatmap">${cells}</div>
    <div class="hmlegend">Less
      <span class="hmcell hm-l0"></span><span class="hmcell hm-l1"></span><span class="hmcell hm-l2"></span><span class="hmcell hm-l3"></span><span class="hmcell hm-l4"></span>
      More</div>${empty}`;
}

function renderProgress() {
  state.inQuiz = false;
  refreshStats();
  const p = state.progress, secs = state.index.sections;
  const tiles = Object.keys(secs).sort().map((s) => {
    const st = (p.sections && p.sections[s]) || { seen: 0, correct: 0 };
    const acc = st.seen ? Math.round((st.correct / st.seen) * 100) : 0;
    const tier = sectionTier(st);
    const tierChip = tier ? `<span class="tier ${tier.toLowerCase()}">${tier}</span>` : "";
    return `<div class="sectiontile">
        <div class="top"><span class="name">${esc(label(s))}${tierChip}</span>
        <span class="pct">${st.seen ? acc + "% &middot; " + st.seen + " seen" : "not started"}</span></div>
        <div class="bar"><span style="width:${acc}%"></span></div>
      </div>`;
  }).join("");
  const due = dueReviews().length;
  app.innerHTML = `
    <div class="hero"><h1>Your progress</h1></div>
    <div class="badges">
      <div class="badge"><div class="n">${p.streak || 0}</div><div class="l">Streak</div></div>
      <div class="badge"><div class="n">${p.longestStreak || 0}</div><div class="l">Longest</div></div>
      <div class="badge"><div class="n">${ICON("snow", "i-snow")} ${p.freezes || 0}</div><div class="l">Freezes</div></div>
      <div class="badge"><div class="n">${p.totalXP || 0}</div><div class="l">Total XP</div></div>
      <div class="badge"><div class="n">${due}</div><div class="l">Due review</div></div>
    </div>
    ${heatmapHTML(p.history)}
    ${ledgerStripHTML()}
    <h2 class="section-h">Mastery by section</h2>
    ${tiles}
    <div class="backup-row">
      <div class="backup-copy">Your progress lives only in this browser. Keep a backup.</div>
      <div class="backup-actions">
        <button class="ghost" id="exportBtn">Export backup</button>
        <button class="ghost" id="importBtn">Import backup</button>
        <input type="file" id="importFile" accept="application/json,.json" hidden />
      </div>
    </div>
    <div class="row" style="margin-top:18px"><button class="primary" id="backHome">Back to today</button></div>`;
  el("#backHome").addEventListener("click", () => go("#/home"));
  el("#exportBtn").addEventListener("click", exportProgress);
  el("#importBtn").addEventListener("click", () => el("#importFile").click());
  el("#importFile").addEventListener("change", (e) => { if (e.target.files[0]) importProgress(e.target.files[0]); });
  wireReveals();
}

/* ---------- study mode (pure reading) ---------- */
function renderStudy() {
  state.inQuiz = false;
  refreshStats();
  const secs = state.index.sections;
  let lastRead = null;
  try { lastRead = JSON.parse(localStorage.getItem("sd_last_read")); } catch { }
  const contCard = lastRead && lastRead.path
    ? `<button class="review-card" id="contBtn">
         <div><div class="eyebrow">Continue reading</div><h2>${esc(lastRead.title || lastRead.path)}</h2></div>
         <span class="review-go">Open &rarr;</span>
       </button>` : "";
  const tiles = Object.keys(secs).sort().map((s) =>
    `<button class="tile" data-section="${s}">
       <span class="tname">${esc(label(s))}</span>
       <span class="tmeta">${secs[s]} Qs &middot; read the notes</span>
     </button>`).join("");
  app.innerHTML = `
    <div class="hero"><h1>Study</h1><p>Read your notes in a focused reader &mdash; no quiz, no clock.</p></div>
    ${contCard}
    <h2 class="section-h">Pick a section to browse its topics</h2>
    <div class="grid">${tiles}</div>
    <div class="row" style="margin-top:18px"><button class="ghost" id="studyHome">&larr; Home</button></div>`;
  document.querySelectorAll(".tile").forEach((b) => b.addEventListener("click", () => go("#/study/" + b.dataset.section)));
  if (contCard) el("#contBtn").addEventListener("click", () => { reader.back = []; openReaderPath(lastRead.path, lastRead.title, null); });
  el("#studyHome").addEventListener("click", () => go("#/home"));
  wireReveals();
}

/* ---------- learning path (Study section graph) ---------- */
// Width-adaptive serpentine (boustrophedon) skill-tree replacing the flat Study
// topic list. The order IS modulesOf() (STUDY_ORDER + appended unlisted
// modules) — nothing is invented. Glass node chips are absolutely-positioned
// buttons over an SVG underlay: N columns adapt to the container width, the
// path snakes row 1 left->right, row 2 right->left, and prerequisite chords are
// routed orthogonally through the chip-free row gutters and column gaps.

// Orthogonal polyline -> SVG path with rounded corners (subway-map routing for
// the prerequisite chords). Collapses duplicate/collinear points first.
function orthPath(raw, r = 10) {
  const pts = [];
  for (const q of raw) {
    const b = pts[pts.length - 1], a = pts[pts.length - 2];
    if (b && Math.abs(b.x - q.x) < 0.5 && Math.abs(b.y - q.y) < 0.5) continue;
    if (a && b && ((Math.abs(a.x - b.x) < 0.5 && Math.abs(b.x - q.x) < 0.5) ||
      (Math.abs(a.y - b.y) < 0.5 && Math.abs(b.y - q.y) < 0.5))) pts.pop();
    pts.push(q);
  }
  if (pts.length < 2) return "";
  const f = (v) => Math.round(v * 10) / 10;
  let d = `M ${f(pts[0].x)} ${f(pts[0].y)}`;
  for (let i = 1; i < pts.length - 1; i++) {
    const p = pts[i], prev = pts[i - 1], next = pts[i + 1];
    const rr = Math.min(r, Math.hypot(p.x - prev.x, p.y - prev.y) / 2, Math.hypot(next.x - p.x, next.y - p.y) / 2);
    const u1x = Math.sign(p.x - prev.x), u1y = Math.sign(p.y - prev.y);
    const u2x = Math.sign(next.x - p.x), u2y = Math.sign(next.y - p.y);
    d += ` L ${f(p.x - u1x * rr)} ${f(p.y - u1y * rr)} Q ${f(p.x)} ${f(p.y)} ${f(p.x + u2x * rr)} ${f(p.y + u2y * rr)}`;
  }
  d += ` L ${f(pts[pts.length - 1].x)} ${f(pts[pts.length - 1].y)}`;
  return d;
}

async function openStudySection(section) {
  app.innerHTML = `<div class="loading">Loading ${esc(label(section))}&hellip;</div>`;
  const bank = await loadBank(section);
  if (!bank || !bank.length) {
    app.innerHTML = `<div class="error">Couldn't load ${esc(section)}. Run <code>python3 extract.py</code>.</div>`;
    return;
  }
  const mods = modulesOf(bank);
  // v2: weighted prerequisite edges from graph/<section>.json (real repo
  // cross-links + lexical Q&A overlap). Pairs are undirected; orient each one
  // forward along the path order. Missing/failed file -> plain v1 path.
  const graph = await fetchJSON(`graph/${section}.json`, null, "default");
  const modIdx = new Map(mods.map((m, i) => [m.module, i]));
  const chords = [];
  let crossLinks = 0;
  for (const p of (graph && graph.pairs) || []) {
    const ia = modIdx.get(p.a), ib = modIdx.get(p.b);
    if (ia === undefined || ib === undefined) continue;  // module not on this path
    if (p.links > 0) crossLinks += p.links;
    const from = Math.min(ia, ib), to = Math.max(ia, ib);
    if (to - from <= 1) continue;                  // consecutive: the spine implies it
    chords.push({ from, to, w: p.w, lex: !(p.links > 0) });
  }
  const peers = mods.map(() => new Set());         // link-backed neighbours per node
  chords.forEach((c) => {
    if (c.lex) return;
    peers[c.from].add(c.to); peers[c.to].add(c.from);
  });
  const list = mods.map((m) => ({ path: `${m.module}/README.md`, title: m.name }));
  const files = (state.index && state.index.files) || {};
  // Practiced = any spaced-repetition entry from this module (real history only).
  const practiced = new Set(Object.values(state.progress.reviews || {}).map((r) => r.module).filter(Boolean));
  // "You are here" = last page opened in the reader, if it lives in this section.
  let lastRead = null;
  try { lastRead = JSON.parse(localStorage.getItem("sd_last_read")); } catch { }
  const herePath = lastRead && lastRead.path && lastRead.path.startsWith(section + "/") ? lastRead.path : null;
  const hereMod = herePath ? herePath.split("/").slice(0, 2).join("/") : null;
  const openFans = new Set();
  if (herePath && hereMod && !/\/README\.md$/i.test(herePath)) openFans.add(hereMod);  // reveal the "here" leaf

  const leafLabel = (fn) => (fn === "README.md" ? "readme" : fn.replace(/\.md$/i, "").replace(/_/g, " "));
  const steps = mods.map((m, i) => {
    const mFiles = files[m.module] || ["README.md"];
    const multi = mFiles.length > 1;
    const isHere = m.module === hereMod;
    const isOpen = openFans.has(m.module);
    const leaves = multi ? mFiles.map((fn, k) => {
      const p = `${m.module}/${fn}`;
      return `<button class="pathleaf${p === herePath ? " here" : ""}" data-idx="${i}" data-path="${esc(p)}" style="animation-delay:${k * 30}ms">${esc(leafLabel(fn))}</button>`;
    }).join("") : "";
    return `<div class="pathstep${isOpen ? " open" : ""}">
      <div class="pathnode${practiced.has(m.module) ? " practiced" : ""}${isHere ? " here" : ""}">
        <button class="pn-main" data-idx="${i}" aria-label="Step ${i + 1} of ${mods.length}: ${esc(m.name)}, ${m.count} questions${peers[i].size ? `, connects to ${peers[i].size} other topics` : ""}">
          <span class="pn-num">${String(i + 1).padStart(2, "0")}</span>
          <span class="pn-body">
            <span class="pn-name">${esc(m.name)}</span>
            <span class="pn-meta">${m.count} Qs${isHere ? ` &middot; <b class="pn-here">you are here</b>` : ""}</span>
          </span>
          ${practiced.has(m.module) ? `<span class="pn-check" title="Practiced">✓</span>` : ""}
        </button>
        ${multi ? `<button class="pn-fan" aria-expanded="${isOpen}" aria-controls="fan-${i}" aria-label="${mFiles.length} files in ${esc(m.name)}"><span class="pn-arrow">&#9656;</span>&nbsp;${mFiles.length} files</button>` : ""}
      </div>
      ${multi ? `<div class="leaf-fan" id="fan-${i}"${isOpen ? "" : " hidden"}>${leaves}</div>` : ""}
    </div>`;
  }).join("");

  app.innerHTML = `
    <div class="path-screen">
    <div class="hero"><h1>${esc(label(section))}</h1>
      <p>${mods.length} topics &middot; start at 01 &mdash; the path snakes across each row in the section's learning order.</p>
      ${graph ? `<p class="path-legend">${crossLinks
        ? `strongest prerequisite links drawn &middot; hover a topic to see all its connections &middot; ${crossLinks} cross-links mapped`
        : "no cross-link data yet &mdash; path order shown"}</p>` : ""}</div>
    <div class="topicbar">
      <input type="search" class="filter" id="studyFilter" placeholder="Filter topics" aria-label="Filter topics" />
      <span class="selcount" id="pathCount" role="status"></span>
    </div>
    <div class="path-wrap" id="pathWrap">
      <svg class="path-svg" id="pathSvg" aria-hidden="true">
        <defs><linearGradient id="lpGrad" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" style="stop-color:var(--accent)"/>
          <stop offset="1" style="stop-color:var(--accent-2)"/>
        </linearGradient></defs>
        <g class="lp-chords" id="lpChords"></g>
        <path class="lp-spine" id="lpSpine" d=""/>
        <path class="lp-leaves" id="lpLeaves" d=""/>
      </svg>
      ${steps}
    </div>
    <div class="row" style="margin-top:18px"><button class="ghost" id="studyBack">&larr; Sections</button></div>
    </div>`;

  insertInterviewControl(section);                 // [C] Face the Interviewer / Panel / lock chip on the path header

  const wrap = el("#pathWrap"), svg = el("#pathSvg");
  const stepEls = [...wrap.querySelectorAll(".pathstep")];
  // One <path> per chord, created once; layoutPath() only rewrites the d attr.
  // Weight -> stroke width via a --sw custom prop so CSS can thicken on highlight.
  // Link-backed chords that stay local (route within one gutter) draw by
  // default; long-haul ones get .wk in layoutPath() and appear on hover/focus —
  // drawing every multi-row staircase would wallpaper a dense section.
  const chordG = el("#lpChords");
  chords.forEach((c) => {
    c.el = document.createElementNS("http://www.w3.org/2000/svg", "path");
    c.el.setAttribute("class", "lp-chord" + (c.lex ? " lex" : ""));
    c.el.style.setProperty("--sw", (1 + 4 * Math.min(1, c.w)).toFixed(2) + "px");
    chordG.appendChild(c.el);
  });

  // Ordered global indices of the steps currently visible (filter hides some).
  let visIdx = mods.map((_, i) => i);

  // Measure-and-place: width-adaptive boustrophedon grid. Column count adapts
  // to the container (~330px per column, 1 on mobile, up to 4 on wide laptops);
  // the path snakes row 1 left->right, row 2 right->left, so step numbers keep
  // the learning order obvious. Positions in px; SVG underlay redrawn to match.
  function layoutPath() {
    const W = wrap.clientWidth;
    if (!W || !stepEls.length) return;
    const vis = visIdx;
    const hasEdges = chords.length > 0;
    const cols = W < 520 ? 1 : Math.min(4, Math.max(2, Math.floor(W / 330)));
    const sideM = cols === 1 ? (hasEdges ? 42 : 4) : 8;
    const colGap = cols === 1 ? 0 : (hasEdges ? 48 : 28);
    const rowGap = hasEdges ? 62 : 46;
    const colW = Math.floor((W - 2 * sideM - (cols - 1) * colGap) / cols);
    const rows = Math.ceil(vis.length / cols);

    stepEls.forEach((s) => { s.style.width = colW + "px"; });
    const pts = new Array(stepEls.length).fill(null);
    const rowH = new Array(rows).fill(0);
    const meta = vis.map((gi, vi) => {
      const s = stepEls[gi];
      const r = (vi / cols) | 0, k = vi % cols;
      return { gi, s, r, c: r % 2 === 0 ? k : cols - 1 - k, h: s.offsetHeight, chipH: s.firstElementChild.offsetHeight };
    });
    meta.forEach((m) => { rowH[m.r] = Math.max(rowH[m.r], m.h); });
    const rowTop = [6];
    for (let r = 1; r < rows; r++) rowTop[r] = rowTop[r - 1] + rowH[r - 1] + rowGap;
    meta.forEach((m) => {
      const left = sideM + m.c * (colW + colGap);
      m.s.style.left = left + "px";
      m.s.style.top = rowTop[m.r] + "px";
      pts[m.gi] = { left, top: rowTop[m.r], w: colW, h: m.h, chipH: m.chipH, r: m.r, s: m.s };
    });
    const H = rows ? rowTop[rows - 1] + rowH[rows - 1] + (hasEdges ? rowGap : 12) : 24;
    wrap.style.height = H + "px";
    svg.setAttribute("width", W);
    svg.setAttribute("height", H);
    svg.setAttribute("viewBox", `0 0 ${W} ${H}`);

    let d = "";                                    // spine: adjacent-slot connectors
    for (let v = 0; v < vis.length - 1; v++) {
      const a = pts[vis[v]], b = pts[vis[v + 1]];
      if (a.r === b.r) {                           // along the row, through the column gap
        const y1 = a.top + a.chipH / 2, y2 = b.top + b.chipH / 2;
        const right = b.left > a.left;
        const x1 = right ? a.left + a.w + 1 : a.left - 1;
        const x2 = right ? b.left - 1 : b.left + b.w + 1;
        const dx = Math.max(8, Math.abs(x2 - x1) * 0.5) * (right ? 1 : -1);
        d += `M ${x1} ${y1} C ${x1 + dx} ${y1}, ${x2 - dx} ${y2}, ${x2} ${y2} `;
      } else {                                     // serpentine turn: drop to the row below
        const x1 = a.left + a.w / 2, y1 = a.top + a.h + 1;
        const x2 = b.left + b.w / 2, y2 = b.top - 1;
        const dy = Math.max(14, (y2 - y1) * 0.5);
        d += `M ${x1} ${y1} C ${x1} ${y1 + dy}, ${x2} ${y2 - dy}, ${x2} ${y2} `;
      }
    }
    el("#lpSpine").setAttribute("d", d.trim());
    let ld = "";                                   // leaf connectors: rail + rounded elbows
    meta.forEach((m) => {
      const p = pts[m.gi];
      if (!p.s.classList.contains("open")) return;
      const railX = p.left + 20;
      p.s.querySelectorAll(".pathleaf").forEach((leaf) => {
        const lx = p.left + leaf.offsetLeft, ly = p.top + leaf.offsetTop + leaf.offsetHeight / 2;
        ld += `M ${railX} ${p.top + p.chipH - 4} L ${railX} ${ly - 9} Q ${railX} ${ly}, ${railX + 9} ${ly} L ${lx} ${ly} `;
      });
    });
    el("#lpLeaves").setAttribute("d", ld.trim());

    // Prerequisite chords: orthogonal subway routing. Every run travels inside
    // a horizontal row gutter or a vertical column gap, so no chord ever
    // crosses chip text. Runs sharing a gutter/gap are interval-colored into
    // parallel lanes (link-backed and lexical-only families kept separate so
    // the always-visible linked edges get generous spacing).
    const gutY = [];                               // gutter r = below row r
    for (let r = 0; r < rows; r++) gutY[r] = rowTop[r] + rowH[r] + rowGap / 2;
    const gapX = [];                               // gap g = between col g and g+1
    for (let g = 0; g < cols - 1; g++) gapX[g] = sideM + (g + 1) * colW + (g + 0.5) * colGap;
    if (!gapX.length) gapX.push(Math.max(16, sideM * 0.45)); // single column: left margin lane

    const live = [];
    chords.forEach((c) => {
      const ok = !!(pts[c.from] && pts[c.to]);     // path order => pts[from].r <= pts[to].r
      c.el.classList.toggle("off", !ok);
      // local hop (one gutter) draws by default; multi-row staircases are hover-only
      if (ok && !c.lex) c.el.classList.toggle("wk", pts[c.to].r - pts[c.from].r > 1);
      if (ok) live.push(c);
    });
    // 1. spread chord mouths across each chip edge so lines never stack there
    const mouths = new Map();                      // "chipIdx:side" -> endpoint list
    live.forEach((c) => {
      const A = pts[c.from], B = pts[c.to];
      const add = (gi, side, key, other) => {
        const k = gi + ":" + side;
        if (!mouths.has(k)) mouths.set(k, []);
        mouths.get(k).push({ c, key, other });
      };
      add(c.from, "b", "_ax", B.left + B.w / 2);
      add(c.to, A.r === B.r ? "b" : "t", "_bx", A.left + A.w / 2);
    });
    mouths.forEach((list, k) => {
      const p = pts[+k.split(":")[0]];
      list.sort((m, n) => m.other - n.other);
      const span = Math.min(p.w * 0.55, 26 * (list.length - 1));
      list.forEach((m, i) => {
        m.c[m.key] = p.left + p.w / 2 + (list.length > 1 ? (i / (list.length - 1) - 0.5) * span : 0);
      });
    });
    // 2. plan runs: which gutters/gap each chord occupies
    const hseg = gutY.map(() => []), vseg = gapX.map(() => []);
    live.forEach((c) => {
      const A = pts[c.from], B = pts[c.to];
      if (B.r <= A.r + 1) {                        // same row or adjacent rows: one gutter
        hseg[A.r].push({ c, part: "a", lo: Math.min(c._ax, c._bx), hi: Math.max(c._ax, c._bx), lex: c.lex });
      } else {                                     // distant rows: gutter -> gap lane -> gutter
        let g = 0, best = Infinity;
        const target = (c._ax + c._bx) / 2;
        gapX.forEach((x, i) => { const dd = Math.abs(x - target); if (dd < best) { best = dd; g = i; } });
        c._g = g;
        hseg[A.r].push({ c, part: "a", lo: Math.min(c._ax, gapX[g]), hi: Math.max(c._ax, gapX[g]), lex: c.lex });
        hseg[B.r - 1].push({ c, part: "b", lo: Math.min(gapX[g], c._bx), hi: Math.max(gapX[g], c._bx), lex: c.lex });
        vseg[g].push({ c, lo: A.r, hi: B.r - 1, lex: c.lex });
      }
    });
    // 3. greedy interval coloring -> parallel lanes inside each gutter/gap
    const color = (items, pad) => {
      items.sort((m, n) => m.lo - n.lo || m.hi - n.hi);
      const ends = [];
      items.forEach((it) => {
        let l = ends.findIndex((e) => e < it.lo - pad);
        if (l < 0) { l = ends.length; ends.push(-Infinity); }
        ends[l] = it.hi;
        it.lane = l;
      });
      return ends.length;
    };
    hseg.forEach((list, r) => {
      [0, 1].forEach((isLex) => {
        const items = list.filter((s) => +s.lex === isLex);
        const n = color(items, 12);
        const spread = n > 1 ? Math.min(8, (rowGap - 26) / (n - 1)) : 0;
        items.forEach((s) => { s.c["_gy" + s.part] = gutY[r] + (s.lane - (n - 1) / 2) * spread + (isLex ? 3 : 0); });
      });
    });
    vseg.forEach((list, g) => {
      const room = (cols > 1 ? colGap : sideM) - 14;
      [0, 1].forEach((isLex) => {
        const items = list.filter((s) => +s.lex === isLex);
        const n = color(items, 0.5);
        const spread = n > 1 ? Math.min(8, room / (n - 1)) : 0;
        items.forEach((s) => { s.c._lx = gapX[g] + (s.lane - (n - 1) / 2) * spread + (isLex ? 3 : 0); });
      });
    });
    // 4. emit rounded orthogonal paths
    live.forEach((c) => {
      const A = pts[c.from], B = pts[c.to];
      const p = [{ x: c._ax, y: A.top + A.h }, { x: c._ax, y: c._gya }];
      if (A.r === B.r) {                           // U through the gutter below the row
        p.push({ x: c._bx, y: c._gya }, { x: c._bx, y: B.top + B.h });
      } else if (B.r === A.r + 1) {                // S through the shared gutter
        p.push({ x: c._bx, y: c._gya }, { x: c._bx, y: B.top });
      } else {                                     // down a column-gap lane between gutters
        p.push({ x: c._lx, y: c._gya }, { x: c._lx, y: c._gyb }, { x: c._bx, y: c._gyb }, { x: c._bx, y: B.top });
      }
      c.el.setAttribute("d", orthPath(p));
    });
  }

  wrap.querySelectorAll(".pn-main").forEach((b) => b.addEventListener("click", () => {
    const idx = +b.dataset.idx;
    reader.back = [];                              // a fresh reading session
    openReaderPath(list[idx].path, list[idx].title, { list, idx });
  }));
  wrap.querySelectorAll(".pathleaf").forEach((b) => b.addEventListener("click", () => {
    const idx = +b.dataset.idx;
    reader.back = [];
    openReaderPath(b.dataset.path, null, { list, idx });
  }));
  wrap.querySelectorAll(".pn-fan").forEach((b) => b.addEventListener("click", (e) => {
    e.stopPropagation();
    const step = b.closest(".pathstep");
    const fan = step.querySelector(".leaf-fan");
    const willOpen = fan.hidden;
    fan.hidden = !willOpen;                        // display toggle restarts the leaf-in animation
    step.classList.toggle("open", willOpen);
    b.setAttribute("aria-expanded", willOpen ? "true" : "false");
    layoutPath();                                  // steps below shift smoothly (top transition)
  }));
  // Hover / keyboard focus on a chip spotlights its prerequisite edges: its
  // chords go full opacity (lexical-only ones appear faint dashed on demand),
  // everything unconnected dims. Pure class toggles — no per-frame JS, so the
  // global reduced-motion rule makes the change instant.
  const nodeEls = stepEls.map((s) => s.querySelector(".pathnode"));
  const touching = mods.map(() => []);
  chords.forEach((c) => { touching[c.from].push(c); touching[c.to].push(c); });
  let hlAt = -1;
  const clearHL = () => {
    if (hlAt < 0) return;
    touching[hlAt].forEach((c) => { c.el.classList.remove("on"); stepEls[c.from].classList.remove("elink"); stepEls[c.to].classList.remove("elink"); });
    stepEls[hlAt].classList.remove("elink");
    wrap.classList.remove("edgehl");
    hlAt = -1;
  };
  const applyHL = (i) => {
    if (hlAt === i) return;
    clearHL();
    if (!touching[i].length) return;               // nothing to spotlight
    hlAt = i;
    wrap.classList.add("edgehl");
    stepEls[i].classList.add("elink");
    touching[i].forEach((c) => { c.el.classList.add("on"); stepEls[c.from].classList.add("elink"); stepEls[c.to].classList.add("elink"); });
  };
  nodeEls.forEach((n, i) => {
    n.addEventListener("pointerenter", () => applyHL(i));
    n.addEventListener("pointerleave", clearHL);
  });
  wrap.addEventListener("focusin", (e) => {
    const pn = e.target.closest(".pathnode");
    pn ? applyHL(nodeEls.indexOf(pn)) : clearHL();
  });
  wrap.addEventListener("focusout", (e) => {
    if (!e.relatedTarget || !wrap.contains(e.relatedTarget)) clearHL();
  });
  // Filter hides non-matching nodes and re-flows the grid so matches gather
  // compactly at the top; Enter opens the first match; clearing restores.
  const filterIn = el("#studyFilter"), countEl = el("#pathCount");
  const applyFilter = () => {
    const f = filterIn.value.trim().toLowerCase();
    visIdx = [];
    stepEls.forEach((s, i) => {
      const hit = !f || mods[i].name.toLowerCase().includes(f);
      s.classList.toggle("fhide", !hit);
      if (hit) visIdx.push(i);
    });
    wrap.classList.toggle("filtering", !!f);
    countEl.textContent = f ? `${visIdx.length} of ${mods.length} topics` : "";
    if (f) announce(`${visIdx.length} of ${mods.length} topics match`);
    layoutPath();
  };
  filterIn.addEventListener("input", applyFilter);
  filterIn.addEventListener("search", applyFilter);   // native x button / Esc clear
  filterIn.addEventListener("keydown", (e) => {
    if (e.key !== "Enter" || !filterIn.value.trim() || !visIdx.length) return;
    const idx = visIdx[0];
    reader.back = [];
    openReaderPath(list[idx].path, list[idx].title, { list, idx });
  });
  el("#studyBack").addEventListener("click", () => go("#/study"));
  let rzT = 0;
  const onResize = () => {                         // debounced; self-removes once the screen is gone
    if (!document.body.contains(wrap)) { window.removeEventListener("resize", onResize); return; }
    clearTimeout(rzT);
    rzT = setTimeout(layoutPath, 160);
  };
  window.addEventListener("resize", onResize);

  layoutPath();
  stepEls.forEach((s, i) => s.style.setProperty("--d", Math.min(i, 12) * 40 + "ms"));
  wrap.classList.add("pathlaid");                  // arms the entrance stagger + top transitions
}

/* ---------- code syntax highlighting (hand-rolled, One Dark) ---------- */
// Single-pass, sticky-regex tokenizer. Only fences with a RECOGNIZED language
// tag are highlighted; untagged fences (the §5 ASCII diagrams) stay verbatim, so
// their alignment is never touched. Shared regex literals are re-positioned via
// lastIndex each iteration, so they can be reused across languages and calls.
const TOK = {
  block: /\/\*[\s\S]*?\*\//y,                                   // /* ... */
  triple: /"""[\s\S]*?"""|'''[\s\S]*?'''/y,                     // python triple strings
  num: /(?:0[xX][0-9a-fA-F]+|\d[\d_]*\.?\d*(?:[eE][+-]?\d+)?)[a-zA-Z]*/y,
  ann: /@[\w.]+/y,                                              // @Override / @app.get
  bashVar: /\$\{[^}]*\}|\$\w+/y,
  id: /[A-Za-z_$][\w$]*/y,
};
const LINE_CMT = { "//": /\/\/.*/y, "#": /#.*/y, "--": /--.*/y, "!": /!.*/y };
const STR_DELIM = {
  '"': /"(?:\\.|[^"\\\n])*"/y,
  "'": /'(?:\\.|[^'\\\n])*'/y,
  "`": /`(?:\\.|[^`\\])*`/y,
};
const set = (s) => new Set(s.split(/\s+/).filter(Boolean));
const LANG_DEFS = {
  java: { block: 1, line: ["//"], str: '"\'', num: 1, annot: 1, capType: 1, fn: 1,
    kw: set("abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for goto if implements import instanceof int interface long native new package private protected public return short static strictfp super switch synchronized this throw throws transient try void volatile while var record yield sealed permits"),
    bi: set("true false null") },
  javascript: { block: 1, line: ["//"], str: '"\'`', num: 1, capType: 1, fn: 1,
    kw: set("async await break case catch class const continue debugger default delete do else export extends finally for function if import in instanceof let new return static super switch this throw try typeof var void while yield of get set"),
    bi: set("true false null undefined NaN Infinity console window document Promise") },
  python: { line: ["#"], triple: 1, str: '"\'', num: 1, decorator: 1, capType: 1, fn: 1,
    kw: set("and as assert async await break class continue def del elif else except finally for from global if import in is lambda nonlocal not or pass raise return try while with yield match case"),
    bi: set("True False None self cls print len range int str float list dict set tuple bool open super isinstance enumerate zip map filter") },
  sql: { block: 1, line: ["--"], str: "'", num: 1, ciKw: 1, fn: 1,
    kw: set("select from where insert into values update set delete create table drop alter add column index view join inner left right outer full on group by order having limit offset union all distinct as and or not null primary key foreign references default unique check constraint cascade begin commit rollback transaction with returning case when then else end exists in between like ilike is asc desc count sum avg min max coalesce nullif cast over partition row_number rank dense_rank"),
    bi: set("int integer varchar text boolean timestamp timestamptz date serial bigserial bigint smallint numeric decimal real double float char uuid jsonb json bytea interval") },
  yaml: { line: ["#"], str: '"\'', num: 1, bi: set("true false null yes no on off") },
  bash: { line: ["#"], str: '"\'', num: 1, bashVar: 1,
    kw: set("if then else elif fi for while do done case esac in function select until return break continue export local readonly declare set unset shift source exit trap"),
    bi: set("echo printf cd pwd ls cat grep sed awk curl wget python python3 pip docker kubectl git make true false") },
  json: { str: '"', num: 1, bi: set("true false null") },
  properties: { line: ["#", "!"], num: 0 },
  dockerfile: { line: ["#"], str: '"\'', num: 1, ciKw: 1,
    kw: set("from run cmd label expose env add copy entrypoint volume user workdir arg onbuild stopsignal healthcheck shell maintainer as"),
    bi: set("") },
};
const LANG_ALIAS = {
  py: "python", js: "javascript", ts: "javascript", jsx: "javascript", tsx: "javascript",
  sh: "bash", shell: "bash", zsh: "bash", console: "bash", yml: "yaml",
  postgres: "sql", postgresql: "sql", mysql: "sql", plsql: "sql",
  jsonc: "json", docker: "dockerfile", props: "properties",
};
const langKey = (t) => { const k = (t || "").trim().toLowerCase(); return LANG_ALIAS[k] || k; };
const isHighlightable = (t) => !!LANG_DEFS[langKey(t)];

function matchersFor(cfg) {
  if (cfg._m) return cfg._m;
  const m = [];
  if (cfg.block) m.push([TOK.block, "com"]);
  for (const lc of cfg.line || []) m.push([LINE_CMT[lc], "com"]);
  if (cfg.triple) m.push([TOK.triple, "str"]);
  for (const d of cfg.str || "") m.push([STR_DELIM[d], "str"]);
  if (cfg.annot || cfg.decorator) m.push([TOK.ann, "ann"]);
  if (cfg.bashVar) m.push([TOK.bashVar, "bi"]);
  if (cfg.num) m.push([TOK.num, "num"]);
  m.push([TOK.id, "id"]);
  cfg._m = m;
  return m;
}

function classifyId(word, code, end, cfg) {
  const key = cfg.ciKw ? word.toLowerCase() : word;
  if (cfg.kw && cfg.kw.has(key)) return "kw";
  if (cfg.bi && cfg.bi.has(word)) return "bi";
  if (cfg.capType && /^[A-Z][A-Za-z0-9_]*$/.test(word)) return "type";
  if (cfg.fn) { let j = end; while (j < code.length && (code[j] === " " || code[j] === "\t")) j++; if (code[j] === "(") return "fn"; }
  return "";
}

function highlightCode(code, lang) {
  const cfg = LANG_DEFS[langKey(lang)];
  if (!cfg) return esc(code);                 // unknown language -> verbatim (guarded by caller too)
  const matchers = matchersFor(cfg);
  let i = 0, out = "";
  const n = code.length;
  while (i < n) {
    let hit = false;
    for (const [re, cls] of matchers) {
      re.lastIndex = i;
      const mm = re.exec(code);
      if (mm && mm.index === i && mm[0].length) {
        const text = mm[0];
        const klass = cls === "id" ? classifyId(text, code, re.lastIndex, cfg) : cls;
        out += klass ? `<span class="tok-${klass}">${esc(text)}</span>` : esc(text);
        i = re.lastIndex; hit = true; break;
      }
    }
    if (!hit) { out += esc(code[i]); i++; }   // operators / punctuation / whitespace
  }
  return out;
}

/* ---------- ASCII-diagram highlighting (alignment-safe) ---------- */
// Colours structure WITHOUT touching layout: every character is preserved, only
// wrapped in <span>s, so monospace column alignment is byte-for-byte unchanged.
// Scaffolding (box/connector chars) is muted; arrows, [labels] and numbers pop.
const DIA = [
  [/\[[^\]\n]*\]/y, "dlabel"],                                  // [Component] boxes
  [/[✓✔]/y, "dok"],                                   // check marks
  [/[✗✘✕]/y, "dno"],                             // cross marks
  [/<=>|<->|-+>|<-+|=+>|<=+|~+>|\.{2,}>|\|>|<\|/y, "darrow"],   // ASCII arrows / flow
  [/[←-⇿⟰-⟿▲▼▶◀▸◂▸▾]/y, "darrow"], // unicode arrows/triangles
  [/[─-╿▀-▟]+|\|+|\++|-{3,}|_{3,}|={3,}/y, "dbox"],  // box-drawing + ASCII rules
  [/\d+(?:\.\d+)?/y, "dnum"],                                   // dims / step numbers
];
// Only treat a fence as a diagram when it actually looks like one, so plain
// command output or text stays verbatim (uncoloured).
function looksLikeDiagram(raw) {
  return /[─-╿←-⇿▲▼▶◀]/.test(raw) ||
    /-+>|<-+|=+>/.test(raw) || /^\s*[|+]/m.test(raw) ||
    (raw.match(/\[[^\]\n]+\]/g) || []).length >= 2;
}
function highlightDiagram(code) {
  let i = 0, out = "";
  const n = code.length;
  while (i < n) {
    let hit = false;
    for (const [re, cls] of DIA) {
      re.lastIndex = i;
      const mm = re.exec(code);
      if (mm && mm.index === i && mm[0].length) {
        out += `<span class="tok-${cls}">${esc(mm[0])}</span>`;
        i = re.lastIndex; hit = true; break;
      }
    }
    if (!hit) { out += esc(code[i]); i++; }
  }
  return out;
}

/* ---------- in-app reader: minimal zero-dep markdown renderer ---------- */
// Covers the constructs this repo actually uses. Fenced blocks render verbatim
// in <pre> so the §5 ASCII diagrams and code keep their alignment.
// Heading-id slug (drop inline markdown, keep it URL-ish and stable).
const stripMd = (t) => t.replace(/\[([^\]]+)\]\([^)]*\)/g, "$1").replace(/[`*_]/g, "");
const slug = (t) => stripMd(t).toLowerCase().replace(/[^\w\s-]/g, "").trim().replace(/\s+/g, "-").slice(0, 80) || "section";

function mdInline(t) {
  return esc(t)
    .replace(/`([^`]+)`/g, (m, c) => `<code>${c}</code>`)
    .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
    .replace(/(^|[^*])\*([^*\n]+)\*/g, "$1<em>$2</em>")
    .replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, (m, txt, url) => {
      if (/^(https?:|mailto:)/i.test(url)) return `<a href="${esc(url)}" target="_blank" rel="noopener">${txt}</a>`;
      if (url[0] === "#") return `<a class="md-anchor" data-frag="${esc(url.slice(1))}" href="#">${txt}</a>`;
      const [path, frag] = url.split("#");                       // relative repo link -> open in reader
      return `<a class="md-link" data-rel="${esc(path)}"${frag ? ` data-frag="${esc(frag)}"` : ""} href="#">${txt}</a>`;
    });
}

// Lazy Mermaid renderer. Only fetches mermaid.js the first time a page with a
// .mermaid div is opened; all other pages incur zero network cost.
let _mermaidReady = null;   // module-scoped promise; null = not started
let _mmSeq = 0;             // unique ids for manual mermaid.render() calls

// Laptop screens are landscape while most authored diagrams grow downward (TD
// flowcharts, stateDiagram lifecycles). Each directional diagram is rendered
// in BOTH orientations off-screen and the reader keeps whichever reads better
// on a wide screen. Render-time choice only — source files stay untouched, so
// GitHub still shows the authored orientation.
const MM_DIR_RE = /^([ \t]*(?:flowchart|graph)\s+)(TB|TD|LR|RL)\b/m;   // m: header may follow %%{init}%% lines
const MM_FLIP = { TB: "LR", TD: "LR", LR: "TD", RL: "TD" };

function mmAltOrientation(src) {
  if (MM_DIR_RE.test(src)) return src.replace(MM_DIR_RE, (_, pre, dir) => pre + MM_FLIP[dir]);
  const lines = src.split("\n");
  const sd = lines.findIndex((l) => /^\s*stateDiagram(?:-v2)?\s*$/.test(l));
  if (sd >= 0) {
    if (/^\s*direction\s+(?:LR|RL)[ \t]*$/m.test(src)) return null;          // author chose horizontal
    if (/^\s*direction\s+(?:TB|TD)[ \t]*$/m.test(src))
      return src.replace(/^(\s*direction\s+)(?:TB|TD)([ \t]*)$/m, "$1LR$2");
    lines.splice(sd + 1, 0, "  direction LR");                               // default is TB — try LR
    return lines.join("\n");
  }
  return null;
}

function mmDims(svgText) {
  const m = svgText.match(/viewBox="[-\d.]+ [-\d.]+ ([\d.]+) ([\d.]+)"/);
  return m ? { w: +m[1] || 1, h: +m[2] || 1 } : null;
}

// Chart-family diagrams (xychart / pie / quadrant / timeline) draw on fixed
// canvases with fixed font sizes, then get scaled to the column — long titles
// or many periods used to shrink their text far below the prose size. They
// are handled per-type: xychart/quadrant re-render AT the column width via an
// injected init directive (text keeps true size); pie re-renders with text
// boosted by the measured shrink factor; timeline gets a 0.75x text-scale
// floor and h-scrolls past the column like a film strip.
function mmChartType(src) {
  const first = src.split("\n").find((l) => {
    const s = l.trim();
    return s && !s.startsWith("%%") && s !== "---";
  }) || "";
  const w = first.trim().split(/[\s:]/)[0];
  return { "xychart-beta": "xychart", pie: "pie", quadrantChart: "quadrant", timeline: "timeline" }[w] || null;
}

function mmChartDirective(type, src, avail) {
  if (type === "xychart") {
    const w = Math.round(Math.min(Math.max(avail, 380), 760));
    const h = Math.round(Math.min(Math.max(w * 0.62, 300), 440));
    // Fit the title into the chart width (monospace ≈ 0.63em per char) so a
    // long title cannot inflate the svg and shrink every axis label with it.
    const t = /^\s*title\s+"?(.+?)"?\s*$/m.exec(src);
    const fs = t ? Math.max(11, Math.min(16, Math.floor((w * 0.96) / (0.63 * t[1].length)))) : 16;
    return `%%{init: {"xyChart": {"width": ${w}, "height": ${h}, "titleFontSize": ${fs}}}}%%\n`;
  }
  if (type === "quadrant") {
    const w = Math.round(Math.min(Math.max(avail, 380), 620));
    return `%%{init: {"quadrantChart": {"chartWidth": ${w}, "chartHeight": ${w}}}}%%\n`;
  }
  return "";
}

// Post-render polish that themeVariables cannot express. Labels in these
// renderers are positioned by transform="translate(x, y) rotate(0)" with
// x/y attributes left at 0 — edits must rewrite the transform, not x/y.
function mmChartPostProcess(type, sv) {
  if (type === "xychart") {
    // Rotate x tick labels when neighbors collide (e.g. one long category
    // between short ones) — the d3-style slanted-tick treatment.
    const labs = [...sv.querySelectorAll("g.bottom-axis g.label text")];
    const rects = labs.map((t) => t.getBoundingClientRect());
    if (rects.some((r, i) => i && r.left < rects[i - 1].right + 2)) {
      labs.forEach((t) => {
        const tr = t.getAttribute("transform") || "";
        t.setAttribute("transform", /rotate/.test(tr) ? tr.replace(/rotate\([^)]*\)/, "rotate(-28)") : tr + " rotate(-28)");
        t.setAttribute("text-anchor", "end");
      });
    }
  }
  if (type === "timeline") {
    // The timeline title renders 35px near-white with no class (font-size
    // attr is "4ex" — use the computed px value); gold-tint it to match
    // every other diagram title.
    sv.querySelectorAll("text").forEach((t) => {
      if (parseFloat(getComputedStyle(t).fontSize) >= 30) t.style.fill = "#e5c07b";
    });
    // The horizontal axis line inherits mainBkg (#1a1a1a) — invisible on the
    // dark canvas.
    sv.querySelectorAll("g.lineWrapper line").forEach((l) => { l.style.stroke = "#4b5263"; });
  }
  if (type === "quadrant") {
    // Authored points can sit close together; when a label (hanging below
    // its point) collides with an already-placed one, move it above the
    // point instead: rewrite its translate and drop the hanging baseline.
    // Quadrant-name labels are seeded as obstacles so point labels dodge
    // them too.
    const placed = [...sv.querySelectorAll("g.quadrant text")].map((t) => t.getBoundingClientRect());
    const hits = (r) => placed.some((p) => r.left < p.right + 2 && r.right > p.left - 2 && r.top < p.bottom + 1 && r.bottom > p.top - 1);
    sv.querySelectorAll("g.data-point").forEach((g) => {
      const t = g.querySelector("text"), c = g.querySelector("circle");
      if (!t || !c) return;
      let r = t.getBoundingClientRect();
      if (hits(r)) {
        const cx = +c.getAttribute("cx"), cy = +c.getAttribute("cy"), cr = +c.getAttribute("r") || 5;
        for (const dy of [cy - cr - 4, cy - cr - 18, cy + cr + 26]) {   // above, higher, or a row below
          t.setAttribute("transform", `translate(${cx}, ${dy})`);
          t.setAttribute("dominant-baseline", "alphabetic");
          r = t.getBoundingClientRect();
          if (!hits(r)) break;
        }
      }
      placed.push(r);
    });
  }
  if (type === "pie") {
    // Legend rows sit on a fixed 22px grid with an 18px swatch; boosted
    // legend text overlaps rows. Re-space rows and scale swatches to the
    // actual font size.
    const rows = [...sv.querySelectorAll("g.legend")];
    const trs = rows.map((g) => /translate\(\s*([-\d.]+)\s*,\s*([-\d.]+)\s*\)/.exec(g.getAttribute("transform") || ""));
    if (rows.length > 1 && trs.every(Boolean)) {
      const fs = parseFloat(getComputedStyle(rows[0].querySelector("text") || rows[0]).fontSize) || 17;
      const sw = Math.round(fs);                     // swatch tracks text size
      const step = Math.round(fs * 1.5);
      const ys = trs.map((m) => +m[2]);
      const y0 = (Math.min(...ys) + Math.max(...ys) + 18) / 2 - (step * rows.length) / 2;
      rows.forEach((g, i) => {
        g.setAttribute("transform", `translate(${trs[i][1]}, ${Math.round(y0 + i * step)})`);
        const r = g.querySelector("rect");
        if (r) { r.setAttribute("width", sw); r.setAttribute("height", sw); }
        const t = g.querySelector("text");
        if (t) { t.setAttribute("x", sw + 8); t.setAttribute("y", Math.round(sw * 0.85)); }
      });
    }
  }
}

// One number controls display size: svg width in px, height follows the
// aspect ratio. max-width stays unset so a user can drag wider than the
// column (the .mermaid container scrolls horizontally past that point).
function mmApplyWidth(sv, w) {
  sv.style.width = Math.round(w) + "px";
  sv.style.maxWidth = "none";
  sv.style.height = "auto";
}

// Column width measured on the PARENT (.md-body) so a breakout margin on the
// container never feeds back into its own measurement.
function mmAvail(n) {
  const col = n.parentElement || n;
  const cs = getComputedStyle(n);
  return Math.max(260, col.clientWidth - parseFloat(cs.paddingLeft || 0) - parseFloat(cs.paddingRight || 0) - 2);
}

// Extra symmetric room in the side gutters: fullscreen caps the prose column
// at 860px for readability, but diagrams may spill past it toward the
// sidebars — that unused space is exactly what a widescreen laptop has.
function mmExtra(n) {
  const main = n.closest("#readerMain");
  const body = n.closest(".reader-body");
  if (!main || !body) return 0;
  const mr = main.getBoundingClientRect(), br = body.getBoundingClientRect();
  const cs = getComputedStyle(body);
  let leftEdge = br.left + parseFloat(cs.paddingLeft || 0);
  let rightEdge = br.right - parseFloat(cs.paddingRight || 0) - (body.offsetWidth - body.clientWidth);
  const mods = body.querySelector(".reader-modules");
  const toc = body.querySelector(".reader-toc");
  if (mods && mods.offsetWidth) leftEdge = Math.max(leftEdge, mods.getBoundingClientRect().right + 20);
  if (toc && toc.offsetWidth) rightEdge = Math.min(rightEdge, toc.getBoundingClientRect().left - 20);
  return Math.max(0, Math.floor(Math.min(mr.left - leftEdge, rightEdge - mr.right))) * 2;
}

function mmAvailWide(n) { return mmAvail(n) + mmExtra(n); }

// Set svg width and center-breakout the container into the gutters when the
// diagram wants more than the prose column; past the gutters it h-scrolls.
// Scrollable diagrams get an .h-scroll class -> edge glow + "⇢ scroll" hint,
// so the hidden part reads as scrollable instead of clipped.
function mmLayout(n, sv, w) {
  mmApplyWidth(sv, w);
  const spill = Math.min(Math.max(0, w - mmAvail(n)), mmExtra(n));
  n.style.marginLeft = n.style.marginRight = spill > 8 ? `${-spill / 2}px` : "";
  requestAnimationFrame(() => n.classList.toggle("h-scroll", n.scrollWidth > n.clientWidth + 4));
}

// Bottom-right drag grip: resize the diagram freely (no upper cap — beyond
// the column width the container scrolls). Double-click resets to auto fit.
function mmAddGrip(n, sv) {
  const grip = document.createElement("button");
  grip.className = "mm-grip";
  grip.title = "Drag to resize · double-click resets";
  grip.setAttribute("aria-label", "Resize diagram");
  n.appendChild(grip);
  let start = null;
  grip.addEventListener("pointerdown", (e) => {
    e.preventDefault(); e.stopPropagation();
    start = { x: e.clientX, w: sv.getBoundingClientRect().width };
    try { grip.setPointerCapture(e.pointerId); } catch { /* synthetic events */ }
    n.classList.add("resizing");
  });
  grip.addEventListener("pointermove", (e) => {
    if (!start) return;
    sv.dataset.custom = "1";
    mmLayout(n, sv, Math.max(240, start.w + (e.clientX - start.x)));
  });
  const end = () => { start = null; n.classList.remove("resizing"); };
  grip.addEventListener("pointerup", end);
  grip.addEventListener("pointercancel", end);
  grip.addEventListener("click", (e) => e.stopPropagation());
  grip.addEventListener("dblclick", (e) => {
    e.stopPropagation();
    delete sv.dataset.custom;
    const avail = mmAvailWide(n);
    mmLayout(n, sv, Math.min(+sv.dataset.natw || avail, Math.max(avail, +sv.dataset.minw || 0)));
  });
}

// The orientation choice depends on the column width, so it is re-evaluated
// live: widen the reader (drag grip / fullscreen) and tall TD diagrams flip
// to LR the moment the horizontal room makes that the better read. Small
// width changes just re-clamp; >15% changes re-render and re-choose.
// Diagrams the user resized by hand are left alone entirely.
const _mmSrc = new WeakMap();     // .mermaid container -> raw diagram source
let _mmRO = null;
let _mmROTimer = null;
function mmObserve(n) {
  if (!_mmRO) {
    _mmRO = new ResizeObserver(() => {
      clearTimeout(_mmROTimer);
      _mmROTimer = setTimeout(() => {
        document.querySelectorAll(".md-body .mermaid").forEach((el) => {
          const src = _mmSrc.get(el);
          const sv = el.querySelector("svg");
          if (!src || !sv || sv.dataset.custom) return;
          const avail = mmAvailWide(el);
          const was = +el.dataset.mmAvail || avail;
          if (Math.abs(avail - was) / was > 0.15) mmRenderNode(el, src);       // re-choose orientation
          else mmLayout(el, sv, Math.min(+sv.dataset.natw || avail,
                                         Math.max(avail, +sv.dataset.minw || 0)));  // re-clamp, honor floor
        });
      }, 250);
    });
  }
  _mmRO.observe(n);
}

// Render one diagram into its container: pick the orientation that reads best
// at the container's current width, size it, and wire grip + zoom.
async function mmRenderNode(n, src) {
  const mermaid = await _mermaidReady;
  const avail = mmAvailWide(n);
  n.dataset.mmAvail = avail;
  const dispH = (d) => d.h * Math.min(1, avail / d.w);   // on-screen height at column width
  const ctype = mmChartType(src);
  let svg, flipScale = 0;
  try {
    svg = (await mermaid.render("mm" + (++_mmSeq), mmChartDirective(ctype, src, avail) + src)).svg;
    const alt = mmAltOrientation(src);
    let d0 = mmDims(svg);
    if (ctype === "pie" && d0) {
      // A pie always draws on a fixed 450px-high canvas; when legend + title
      // force a downscale, re-render with text boosted toward a ~13px
      // displayed size (measure -> boost -> re-measure, max two passes; the
      // boost widens the legend, so one refinement pass tightens the result).
      let k = 1;
      for (let pass = 0; pass < 2; pass++) {
        const s = Math.min(1, avail / d0.w);
        const eff = 14 * k * s;                        // displayed legend/label px
        if (eff >= 12.5) break;
        k = Math.min(2.4, k * (13 / eff));
        // Title tracks the boost (16/14 ratio) so title and legend keep the
        // same displayed proportion no matter which of them drives the width.
        const tfs = Math.max(17, Math.min(34, Math.round(16 * k)));
        const boost = `%%{init: {"themeVariables": {"pieSectionTextSize": "${Math.round(14 * k)}px", "pieLegendTextSize": "${Math.round(14 * k)}px", "pieTitleTextSize": "${tfs}px"}}}%%\n`;
        try {
          svg = (await mermaid.render("mm" + (++_mmSeq), boost + src)).svg;
          d0 = mmDims(svg);
        } catch { document.getElementById("dmm" + _mmSeq)?.remove(); break; }
      }
    }
    if (alt && d0) {
      try {
        const altSvg = (await mermaid.render("mm" + (++_mmSeq), alt)).svg;
        const d1 = mmDims(altSvg);
        if (d1) {
          // Flipped text never drops below 0.7x (stays readable); past the
          // column width the container scrolls horizontally instead of
          // shrinking further. Flip when that cuts the on-screen height by
          // ≥30% and at least ~55% of the flipped diagram is visible at once.
          const s1 = Math.min(1, Math.max(avail / d1.w, 0.7));
          const visible = Math.min(1, avail / (d1.w * s1));
          if (d1.h * s1 < dispH(d0) * 0.7 && visible >= 0.55) { svg = altSvg; flipScale = s1; }
        }
      } catch { /* flipped source failed to parse — keep the original */ }
    }
  } catch (err) {
    document.getElementById("dmm" + _mmSeq)?.remove();   // mermaid's temp scratch div
    console.warn("Mermaid render failed:", err);         // raw source stays visible
    return;
  }
  n.innerHTML = svg;                                     // replaces old svg + grip
  const sv = n.querySelector("svg");
  let d = mmDims(svg);
  if (sv && ctype) mmChartPostProcess(ctype, sv);        // may grow the bbox (rotated ticks)
  if (sv) { const fixed = mmFixViewBox(sv); if (fixed) d = fixed; }
  if (sv && d) {
    sv.dataset.natw = Math.round(d.w);
    if (flipScale) {
      sv.dataset.minw = Math.round(d.w * 0.7);           // readability floor for re-clamps
      mmLayout(n, sv, Math.round(d.w * flipScale));      // may exceed the column -> gutters, then h-scroll
    } else if (ctype === "timeline" && d.w > avail) {
      // Timelines are film strips: never shrink text below 0.75x — keep the
      // width and let the container h-scroll (edge glow + hint appear).
      const w = Math.max(avail, Math.round(d.w * 0.75));
      if (w > avail) sv.dataset.minw = w;                // survives ResizeObserver re-clamps
      mmLayout(n, sv, w);
    } else {
      mmLayout(n, sv, Math.min(d.w, avail));
    }
    mmAddGrip(n, sv);
  }
  // Post-process SVG: round node corners + color arrowhead markers
  // (Mermaid sets marker fill independently of lineColor themeVariable)
  n.querySelectorAll(".node rect").forEach(r => { r.setAttribute("rx", "8"); r.setAttribute("ry", "8"); });
  n.querySelectorAll(".cluster rect").forEach(r => { r.setAttribute("rx", "12"); r.setAttribute("ry", "12"); });
  n.querySelectorAll("marker path, marker polygon").forEach(m => { m.setAttribute("fill", "#61afef"); m.removeAttribute("stroke"); });
  mmTintPlain(n);
  if (!n.dataset.mmWired) {                              // once per container, not per render
    n.dataset.mmWired = "1";
    n.addEventListener("click", () => openMermaidZoom(n));
  }
}

// Consistency fallback: stateDiagrams (and any flowchart authored without the
// classDef palette) render every node in flat mainBkg gray, which reads
// broken next to fully-colored diagrams. When a rendered diagram has nodes
// and NONE carries a color, tint them from the One Dark palette in definition
// order. Diagrams with even one authored color are left untouched.
const MM_TINTS = [
  { f: "#61afef", s: "#2e86c1", t: "#1a1a1a" },   // blue
  { f: "#98c379", s: "#27ae60", t: "#1a1a1a" },   // green
  { f: "#e5c07b", s: "#f39c12", t: "#1a1a1a" },   // gold
  { f: "#c678dd", s: "#9b59b6", t: "#ffffff" },   // purple
  { f: "#56b6c2", s: "#0097a7", t: "#1a1a1a" },   // teal
  { f: "#d19a66", s: "#e67e22", t: "#1a1a1a" },   // orange
  { f: "#e06c75", s: "#c0392b", t: "#ffffff" },   // red
];
function mmTintPlain(n) {
  let i = 0;
  n.querySelectorAll("svg .node").forEach((g) => {
    if (g.classList.contains("statediagram-note")) return;   // notes are gold-themed already
    const shape = g.querySelector("rect, polygon, circle, path");
    if (!shape || getComputedStyle(shape).fill !== "rgb(26, 26, 26)") return;   // mainBkg #1a1a1a = unstyled
    const c = MM_TINTS[i++ % MM_TINTS.length];
    g.querySelectorAll("rect, polygon, circle, path").forEach((s) => {
      s.style.fill = c.f; s.style.stroke = c.s;
    });
    g.querySelectorAll("foreignObject div").forEach((d) => { d.style.color = c.t; });
    g.querySelectorAll("text").forEach((t) => { t.style.fill = c.t; });
  });
}

// Mermaid under-measures long monospace lines in some layouts (state-diagram
// notes especially), computing a viewBox smaller than the drawn content — the
// overflow is then clipped at the canvas edge. Expand the viewBox to the true
// bounding box after render; returns corrected dims when a fix was needed.
function mmFixViewBox(sv) {
  try {
    const bb = sv.getBBox();
    const vb = sv.viewBox.baseVal;
    const over = bb.x < vb.x - 2 || bb.y < vb.y - 2 ||
                 bb.x + bb.width > vb.x + vb.width + 2 ||
                 bb.y + bb.height > vb.y + vb.height + 2;
    if (!over) return null;
    const x = Math.floor(Math.min(bb.x, vb.x)) - 8;
    const y = Math.floor(Math.min(bb.y, vb.y)) - 8;
    const w = Math.ceil(Math.max(bb.x + bb.width, vb.x + vb.width)) - x + 8;
    const h = Math.ceil(Math.max(bb.y + bb.height, vb.y + vb.height)) - y + 8;
    sv.setAttribute("viewBox", `${x} ${y} ${w} ${h}`);
    return { w, h };
  } catch { return null; }
}

async function renderMermaid(root) {
  const nodes = [...root.querySelectorAll(".mermaid")];
  if (!nodes.length) return;                       // no mermaid on this page — skip
  let mermaid;
  try {
    if (!_mermaidReady) {
      _mermaidReady = import("https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.esm.min.mjs")
        .then(m => {
          m.default.initialize({
            startOnLoad: false,
            theme: "dark",
            themeVariables: {
              background:          "#000000",
              mainBkg:             "#1a1a1a",
              nodeBorder:          "#3b4048",
              lineColor:           "#61afef",
              textColor:           "#abb2bf",
              edgeLabelBackground: "transparent",  // no black pill around edge labels
              clusterBkg:          "rgba(97,175,239,0.05)",
              clusterBorder:       "rgba(97,175,239,0.35)",
              titleColor:          "#e5c07b",
              labelBackground:     "#000000",
              fontFamily:          "ui-monospace, SFMono-Regular, Menlo, monospace",
              // Sequence diagrams can't use flowchart classDefs, so they sat
              // flat gray next to fully-colored flowcharts. Theme them from
              // the same One Dark palette: blue actors, gold notes/labels.
              actorBkg:              "#61afef",
              actorBorder:           "#2e86c1",
              actorTextColor:        "#1a1a1a",
              actorLineColor:        "#4b5263",
              signalColor:           "#61afef",
              signalTextColor:       "#e5c07b",
              noteBkgColor:          "#e5c07b",
              noteBorderColor:       "#f39c12",
              noteTextColor:         "#1a1a1a",
              activationBkgColor:    "#3b4048",
              activationBorderColor: "#61afef",
              labelBoxBkgColor:      "rgba(198,120,221,0.12)",
              labelBoxBorderColor:   "#c678dd",
              labelTextColor:        "#c678dd",
              loopTextColor:         "#c678dd",
              // Pie: the dark theme's default slices are near-black on our
              // dark canvas (an 80% slice was literally invisible). One Dark
              // slices, dark % labels ON the light slices, gold title.
              pie1: "#61afef", pie2: "#98c379", pie3: "#e5c07b", pie4: "#c678dd",
              pie5: "#56b6c2", pie6: "#d19a66", pie7: "#e06c75", pie8: "#8ab8e0",
              pie9: "#b5d99c", pie10: "#ecd399", pie11: "#d9a3e8", pie12: "#89cdd6",
              pieTitleTextSize:    "17px", pieTitleTextColor:   "#e5c07b",
              pieSectionTextSize:  "14px", pieSectionTextColor: "#1a1a1a",
              pieLegendTextSize:   "14px", pieLegendTextColor:  "#abb2bf",
              pieStrokeColor:      "#0a0d13", pieStrokeWidth:     "1.5px",
              pieOuterStrokeColor: "#3b4048", pieOuterStrokeWidth: "2px",
              pieOpacity: "1",
              // Quadrant: default is a flat gray ramp with pale gray points.
              // Dark One-Dark-tinted quadrants, hue-matched quadrant labels,
              // blue points.
              quadrant1Fill: "#14263c", quadrant2Fill: "#1b2a1a",
              quadrant3Fill: "#2a2417", quadrant4Fill: "#2b181b",
              quadrant1TextFill: "#61afef", quadrant2TextFill: "#98c379",
              quadrant3TextFill: "#e5c07b", quadrant4TextFill: "#e06c75",
              quadrantPointFill: "#61afef", quadrantPointTextFill: "#abb2bf",
              quadrantXAxisTextFill: "#abb2bf", quadrantYAxisTextFill: "#abb2bf",
              quadrantInternalBorderStrokeFill: "#3b4048",
              quadrantExternalBorderStrokeFill: "#4b5263",
              quadrantTitleFill: "#e5c07b",
              // Timeline consumes cScale0-11 (default: muted near-black hues
              // — the 2017/2018 period boxes were invisible). Saturated One
              // Dark cycle with dark labels on every fill.
              cScale0: "#61afef", cScale1: "#98c379", cScale2: "#e5c07b",
              cScale3: "#c678dd", cScale4: "#56b6c2", cScale5: "#d19a66",
              cScale6: "#e06c75", cScale7: "#8ab8e0", cScale8: "#b5d99c",
              cScale9: "#ecd399", cScale10: "#d9a3e8", cScale11: "#89cdd6",
              cScaleLabel0: "#1a1a1a", cScaleLabel1: "#1a1a1a", cScaleLabel2: "#1a1a1a",
              cScaleLabel3: "#1a1a1a", cScaleLabel4: "#1a1a1a", cScaleLabel5: "#1a1a1a",
              cScaleLabel6: "#1a1a1a", cScaleLabel7: "#1a1a1a", cScaleLabel8: "#1a1a1a",
              cScaleLabel9: "#1a1a1a", cScaleLabel10: "#1a1a1a", cScaleLabel11: "#1a1a1a",
              // xychart: kill the black plot slab, One Dark series palette
              // (blue bars, green line, then gold/purple/teal/orange/red).
              xyChart: {
                backgroundColor: "transparent",
                titleColor: "#e5c07b",
                xAxisLabelColor: "#abb2bf", xAxisTitleColor: "#abb2bf",
                xAxisLineColor: "#4b5263", xAxisTickColor: "#4b5263",
                yAxisLabelColor: "#abb2bf", yAxisTitleColor: "#abb2bf",
                yAxisLineColor: "#4b5263", yAxisTickColor: "#4b5263",
                plotColorPalette: "#61afef,#98c379,#e5c07b,#c678dd,#56b6c2,#d19a66,#e06c75",
              },
            },
            flowchart: { curve: "basis", padding: 20, nodeSpacing: 45, rankSpacing: 55 },
            // Sequence text rendered oversized relative to prose and long
            // notes overflowed their boxes; wrap + smaller fonts fix both.
            sequence: {
              wrap: true,
              actorFontSize: 14, messageFontSize: 13, noteFontSize: 13,
              actorMargin: 60, noteMargin: 12, boxMargin: 8,
            },
            // Chart-family fonts sized to sit next to 14.5px prose. Widths
            // are injected per-render (mmChartDirective) at the column width.
            xyChart: {
              titleFontSize: 16,
              xAxis: { labelFontSize: 13, titleFontSize: 14 },
              yAxis: { labelFontSize: 13, titleFontSize: 14 },
            },
            quadrantChart: { pointLabelFontSize: 12, pointRadius: 5, pointTextPadding: 4 },
          });
          return m.default;
        });
    }
    mermaid = await _mermaidReady;
  } catch (err) {
    // CDN unavailable or offline — raw source stays visible as text, nothing
    // crashes, and the import is retried on the next page open (a cached
    // rejected promise would otherwise disable diagrams for the whole session).
    _mermaidReady = null;
    console.warn("Mermaid load failed:", err);
    return;
  }
  if (_mmRO) _mmRO.disconnect();                   // observe only the live page's diagrams
  for (const n of nodes) {
    if (!n.querySelector("svg")) {                 // not yet rendered this visit
      const src = n.textContent.trim();
      if (!src) continue;
      _mmSrc.set(n, src);
      await mmRenderNode(n, src);
    }
    mmObserve(n);
  }
}

// Unified diagram lightbox: drag-to-pan + zoom-toward-cursor for both Mermaid
// SVGs and ASCII-diagram <pre>s. Wheel zooms at the pointer, drag pans (pointer
// capture), double-click zooms in / resets, and + − 0 arrows Esc work from the
// keyboard. Opens fitted-to-viewport and centred.
function openDiagramZoom(contentEl) {
  let scale = 1, tx = 0, ty = 0, panning = null, moved = false;

  const inner = document.createElement("div");
  inner.className = "mermaid-zoom-inner md-body";     // md-body -> token colours apply
  inner.appendChild(contentEl);

  const box = document.createElement("div");
  box.className = "mermaid-zoom-box";
  box.appendChild(inner);

  const ctrl = document.createElement("div");
  ctrl.className = "mermaid-zoom-ctrl";
  ctrl.innerHTML = `<button class="mz-out" title="Zoom out (−)">−</button><span class="mz-pct">100%</span><button class="mz-in" title="Zoom in (+)">+</button><button class="mz-reset" title="Fit (0)">↺</button><span class="mz-hint">drag to pan · scroll to zoom · esc closes</span><button class="mz-close" title="Close (Esc)">✕</button>`;

  const overlay = document.createElement("div");
  overlay.className = "mermaid-overlay";
  overlay.appendChild(ctrl);
  overlay.appendChild(box);
  document.body.appendChild(overlay);

  const apply = () => {
    inner.style.transform = `translate(${tx}px, ${ty}px) scale(${scale})`;
    ctrl.querySelector(".mz-pct").textContent = Math.round(scale * 100) + "%";
  };
  // Zoom keeping the point under (cx, cy) — box-local coords — stationary.
  const zoomAt = (next, cx, cy) => {
    next = Math.min(6, Math.max(0.15, next));
    const k = next / scale;
    tx = cx - k * (cx - tx);
    ty = cy - k * (cy - ty);
    scale = next;
    apply();
  };
  const fit = () => {                                 // fit-to-viewport, centred (cap 1x)
    const b = box.getBoundingClientRect();
    const w = inner.offsetWidth || 1, h = inner.offsetHeight || 1;
    scale = Math.max(0.15, Math.min(1, (b.width - 56) / w, (b.height - 56) / h));
    tx = (b.width - w * scale) / 2;
    ty = Math.max(24, (b.height - h * scale) / 2);
    apply();
  };
  requestAnimationFrame(fit);

  const close = () => { overlay.remove(); document.removeEventListener("keydown", onKey, true); };
  const onKey = (e) => {
    if (e.key === "Escape") { e.stopPropagation(); close(); return; }   // don't also close the reader
    const r = box.getBoundingClientRect();
    if (e.key === "+" || e.key === "=") zoomAt(scale * 1.2, r.width / 2, r.height / 2);
    else if (e.key === "-" || e.key === "_") zoomAt(scale / 1.2, r.width / 2, r.height / 2);
    else if (e.key === "0") fit();
    else if (e.key === "ArrowLeft") { tx += 60; apply(); }
    else if (e.key === "ArrowRight") { tx -= 60; apply(); }
    else if (e.key === "ArrowUp") { ty += 60; apply(); }
    else if (e.key === "ArrowDown") { ty -= 60; apply(); }
    else return;
    e.preventDefault();
  };
  document.addEventListener("keydown", onKey, true);

  ctrl.querySelector(".mz-in").addEventListener("click", () => { const r = box.getBoundingClientRect(); zoomAt(scale * 1.25, r.width / 2, r.height / 2); });
  ctrl.querySelector(".mz-out").addEventListener("click", () => { const r = box.getBoundingClientRect(); zoomAt(scale / 1.25, r.width / 2, r.height / 2); });
  ctrl.querySelector(".mz-reset").addEventListener("click", fit);
  ctrl.querySelector(".mz-close").addEventListener("click", close);

  box.addEventListener("pointerdown", (e) => {
    if (e.button !== 0) return;
    panning = { x: e.clientX, y: e.clientY, tx, ty };
    moved = false;
    box.setPointerCapture(e.pointerId);
    box.classList.add("panning");
  });
  box.addEventListener("pointermove", (e) => {
    if (!panning) return;
    const dx = e.clientX - panning.x, dy = e.clientY - panning.y;
    if (Math.abs(dx) + Math.abs(dy) > 4) moved = true;
    tx = panning.tx + dx; ty = panning.ty + dy;
    apply();
  });
  const endPan = () => { panning = null; box.classList.remove("panning"); };
  box.addEventListener("pointerup", endPan);
  box.addEventListener("pointercancel", endPan);
  box.addEventListener("click", (e) => { if (!moved && e.target === box) close(); });
  box.addEventListener("dblclick", (e) => {
    const r = box.getBoundingClientRect();
    if (scale < 2.5) zoomAt(scale * 1.6, e.clientX - r.left, e.clientY - r.top); else fit();
  });
  box.addEventListener("wheel", (e) => {
    e.preventDefault();
    const r = box.getBoundingClientRect();
    zoomAt(scale * Math.exp(-e.deltaY * 0.0015), e.clientX - r.left, e.clientY - r.top);
  }, { passive: false });
}

function openMermaidZoom(node) {
  const svg = node.querySelector("svg");
  if (!svg) return;
  const clone = svg.cloneNode(true);
  // Give the clone explicit pixel size from its viewBox so fit/zoom math is exact.
  const vb = svg.viewBox && svg.viewBox.baseVal;
  const r = svg.getBoundingClientRect();
  const w = (vb && vb.width) || r.width || 800;
  const h = (vb && vb.height) || r.height || 500;
  clone.setAttribute("width", w);
  clone.setAttribute("height", h);
  clone.style.cssText = "display:block;max-width:none;";
  openDiagramZoom(clone);
}

// Copy buttons on every code fence + click-to-zoom on ASCII diagrams.
function wireDiagramsAndCopy(root) {
  root.querySelectorAll("pre").forEach((pre) => {
    const code = pre.querySelector("code");
    if (!code) return;
    const btn = document.createElement("button");
    btn.className = "codecopy";
    btn.textContent = "copy";
    btn.title = "Copy to clipboard";
    btn.addEventListener("click", (e) => {
      e.stopPropagation();
      navigator.clipboard?.writeText(code.textContent).then(() => {
        btn.textContent = "copied ✓"; btn.classList.add("ok");
        setTimeout(() => { btn.textContent = "copy"; btn.classList.remove("ok"); }, 1400);
      });
    });
    pre.appendChild(btn);
    if (code.classList.contains("diagram")) {
      pre.title = "Click to zoom";
      pre.addEventListener("click", (e) => {
        if (e.target.closest(".codecopy")) return;
        const p = document.createElement("pre");
        p.innerHTML = code.innerHTML;
        openDiagramZoom(p);
      });
    }
  });
}

function mdRender(src) {
  const lines = src.replace(/\r\n/g, "\n").split("\n");
  const out = [];
  const usedIds = {};
  const headingId = (text) => { const b = slug(text); let id = b, n = 1; while (usedIds[id]) id = `${b}-${++n}`; usedIds[id] = 1; return id; };
  let i = 0;
  let qaPending = false;                            // true right after a bold question paragraph
  while (i < lines.length) {
    const line = lines[i];
    const fence = line.match(/^```(.*)$/);
    if (fence) {                                   // fenced code / ASCII diagram
      const lang = fence[1].trim();
      const body = [];
      i++;
      while (i < lines.length && !/^```/.test(lines[i])) { body.push(lines[i]); i++; }
      i++;                                         // skip closing fence
      const raw = body.join("\n");
      // Mermaid fences: wrap in a div that mermaid.js will render after page load.
      if (lang === "mermaid") { out.push(`<div class="mermaid">${esc(raw)}</div>`); qaPending = false; continue; }
      // Known languages -> code highlighter; otherwise diagram highlighter when it
      // looks like a diagram (colour only, alignment preserved); else verbatim.
      let inner, cls;
      if (isHighlightable(lang)) { inner = highlightCode(raw, lang); cls = ` class="lang-${esc(langKey(lang))}"`; }
      else if (looksLikeDiagram(raw)) { inner = highlightDiagram(raw); cls = ` class="diagram"`; }
      else { inner = esc(raw); cls = ""; }
      out.push(`<pre><code${cls}>${inner}</code></pre>`);
      qaPending = false; continue;
    }
    if (/^\s*\|.*\|\s*$/.test(line) && i + 1 < lines.length &&
        /^\s*\|?[\s:|-]+\|?\s*$/.test(lines[i + 1]) && lines[i + 1].includes("-")) {
      const rows = [line]; i += 2;                 // header + separator consumed
      while (i < lines.length && /^\s*\|.*\|\s*$/.test(lines[i])) { rows.push(lines[i]); i++; }
      const cells = (r) => r.trim().replace(/^\|/, "").replace(/\|$/, "").split("|").map((c) => c.trim());
      let t = "<table><thead><tr>" + cells(rows[0]).map((c) => `<th>${mdInline(c)}</th>`).join("") + "</tr></thead><tbody>";
      for (const r of rows.slice(1)) t += "<tr>" + cells(r).map((c) => `<td>${mdInline(c)}</td>`).join("") + "</tr>";
      out.push(t + "</tbody></table>");
      qaPending = false; continue;
    }
    const h = line.match(/^(#{1,6})\s+(.*)$/);
    if (h) { out.push(`<h${h[1].length} id="${headingId(h[2])}">${mdInline(h[2])}</h${h[1].length}>`); qaPending = false; i++; continue; }
    if (/^(---+|\*\*\*+|___+)\s*$/.test(line)) { out.push("<hr>"); qaPending = false; i++; continue; }
    if (/^>\s?/.test(line)) {
      const body = [];
      while (i < lines.length && /^>\s?/.test(lines[i])) { body.push(lines[i].replace(/^>\s?/, "")); i++; }
      out.push(`<blockquote>${mdInline(body.join(" "))}</blockquote>`);
      qaPending = false; continue;
    }
    if (/^\s*([-*+]|\d+\.)\s+/.test(line)) {
      const ordered = /^\s*\d+\.\s+/.test(line), tag = ordered ? "ol" : "ul";
      let items = "";
      while (i < lines.length && /^\s*([-*+]|\d+\.)\s+/.test(lines[i])) {
        items += `<li>${mdInline(lines[i].replace(/^\s*([-*+]|\d+\.)\s+/, ""))}</li>`; i++;
      }
      out.push(`<${tag}>${items}</${tag}>`);
      qaPending = false; continue;
    }
    if (/^\s*$/.test(line)) { i++; continue; }
    const para = [];
    while (i < lines.length && !/^\s*$/.test(lines[i]) && !/^(#{1,6})\s/.test(lines[i]) &&
           !/^```/.test(lines[i]) && !/^>\s?/.test(lines[i]) &&
           !/^\s*([-*+]|\d+\.)\s+/.test(lines[i]) && !/^(---+|\*\*\*+)\s*$/.test(lines[i])) {
      para.push(lines[i]); i++;
    }
    // A fully-bold paragraph is an interview question (CLAUDE.md §12 format); the
    // paragraph right after it is its answer. Colour them distinctly.
    let p = para.join(" ").trim();
    const isQ = /^\*\*[\s\S]+\*\*$/.test(p) && (p.match(/\*\*/g) || []).length === 2;
    let pcls = "";
    if (isQ) { pcls = ' class="md-q"'; qaPending = true; }
    else if (qaPending) {
      pcls = ' class="md-a"'; qaPending = false;
      p = p.replace(/^:\s*/, "");   // strip markdown definition-list ": answer" prefix
    }
    else if (/^\*\*[^*]+?\*\*[^*]/.test(p)) {
      pcls = ' class="md-qa"';
      p = p.replace(/^(\*\*[^*]+?\*\*):/, "$1");  // strip ": " label separator — display:block already gives the line break
    }
    out.push(`<p${pcls}>${mdInline(p)}</p>`);
  }
  return out.join("\n");
}

const readerCache = {};                            // content path -> raw markdown
const reader = { path: null, titleText: "", back: [], nav: null, full: false, toc: false, modules: false };
const readerExpanded = new Set();   // module keys expanded in the left sidebar (session-persistent)

// Normalise a relative link (../x/y.md) against the directory of the current file.
function resolvePath(baseFile, rel) {
  const stack = baseFile.split("/").slice(0, -1);
  for (const part of rel.split("/")) {
    if (part === "" || part === ".") continue;
    if (part === "..") stack.pop();
    else stack.push(part);
  }
  return stack.join("/");
}

// Human title from a content path: ".../module/README.md" -> "module";
// ".../module/sub_file.md" -> "sub file".
function titleFromPath(path) {
  const parts = path.split("/");
  let name = parts.pop();
  if (/^readme\.md$/i.test(name)) name = parts.pop() || name;
  return name.replace(/\.md$/i, "").replace(/[_-]+/g, " ");
}

// Drag-to-resize: pointer-capture on the grip so move/up fire even off-element.
// onMove(ev) — called on every pointermove; onDone() — called once on pointerup.
function attachGrip(grip, onMove, onDone) {
  if (!grip) return;
  grip.addEventListener("pointerdown", (e) => {
    e.preventDefault();
    grip.setPointerCapture(e.pointerId);
    document.body.classList.add("reader-resizing");
    const done = () => {
      document.body.classList.remove("reader-resizing");
      grip.removeEventListener("pointermove", onMove);
      grip.removeEventListener("pointerup", done);
      onDone?.();
    };
    grip.addEventListener("pointermove", onMove);
    grip.addEventListener("pointerup", done);
  });
}

function wireGrips() {
  const css = document.documentElement.style;
  // Main reader pane grip (outer shell, wired on every openReaderPath call)
  attachGrip(el("#readerGrip"),
    ev => css.setProperty("--reader-w", Math.round(Math.min(window.innerWidth * 0.92, Math.max(360, window.innerWidth - ev.clientX))) + "px"),
    () => { const v = getComputedStyle(document.documentElement).getPropertyValue("--reader-w").trim(); if (v.endsWith("px")) localStorage.setItem("sd_reader_w", v); }
  );
}

function wireSidebarGrips() {
  const css = document.documentElement.style;
  // Left modules sidebar grip (injected with body HTML — wired after b.innerHTML is set)
  attachGrip(el("#modulesGrip"),
    ev => {
      const left = el("#reader").getBoundingClientRect().left;
      css.setProperty("--modules-w", Math.round(Math.min(320, Math.max(100, ev.clientX - left))) + "px");
    },
    () => { const v = getComputedStyle(document.documentElement).getPropertyValue("--modules-w").trim(); if (v.endsWith("px")) localStorage.setItem("sd_modules_w", v); }
  );
  // Right TOC sidebar grip
  attachGrip(el("#tocGrip"),
    ev => {
      const right = el("#reader").getBoundingClientRect().right;
      css.setProperty("--toc-w", Math.round(Math.min(360, Math.max(120, right - ev.clientX))) + "px");
    },
    () => { const v = getComputedStyle(document.documentElement).getPropertyValue("--toc-w").trim(); if (v.endsWith("px")) localStorage.setItem("sd_toc_w", v); }
  );
}

function restoreReaderWidth() {
  const css = document.documentElement.style;
  const rw = localStorage.getItem("sd_reader_w");   if (rw) css.setProperty("--reader-w", rw);
  const mw = localStorage.getItem("sd_modules_w");  if (mw) css.setProperty("--modules-w", mw);
  const tw = localStorage.getItem("sd_toc_w");      if (tw) css.setProperty("--toc-w", tw);
  reader.full    = localStorage.getItem("sd_reader_full")    === "1";
  reader.toc     = localStorage.getItem("sd_reader_toc")     === "1";
  reader.modules = localStorage.getItem("sd_reader_modules") === "1";
  applyReaderFont();
}

// Reader font size: A− / A+ in the reader head, persisted, clamped 12–19px.
function applyReaderFont(delta = 0) {
  let fs = +(localStorage.getItem("sd_reader_fs") || 14.5) + delta;
  fs = Math.min(19, Math.max(12, fs));
  localStorage.setItem("sd_reader_fs", fs);
  document.documentElement.style.setProperty("--rd-fs", fs + "px");
}

// Populate the always-accessible sidebar index from the rendered headings (ids
// assigned by mdRender, so anchors always match). Returns the heading count so the
// caller can hide the Index toggle when there's nothing to index.
function buildToc(tocEl, main) {
  const heads = [...main.querySelectorAll("h2[id], h3[id]")];
  if (!heads.length) { tocEl.innerHTML = ""; return 0; }
  const items = heads.map((h) =>
    `<li class="${h.tagName === "H3" ? "lvl3" : ""}"><a href="#" data-tid="${esc(h.id)}" title="${esc(h.textContent)}">${esc(h.textContent)}</a></li>`).join("");
  tocEl.innerHTML = `<div class="toc-h">Contents</div><ul>${items}</ul>`;
  tocEl.querySelectorAll("a[data-tid]").forEach((a) => a.addEventListener("click", (e) => {
    e.preventDefault();
    const t = main.querySelector("#" + CSS.escape(a.dataset.tid));
    if (t) t.scrollIntoView({ behavior: "smooth", block: "start" });
  }));
  return heads.length;
}

// Populate the left module-list sidebar from the current navCtx.
// Renders a VS Code-style file tree: single-file modules are plain links;
// multi-file modules show a collapsible folder with each file listed beneath.
function buildModuleNav(modEl, navCtx, currentPath) {
  if (!navCtx || !navCtx.list.length) { modEl.innerHTML = ""; return; }
  const files = (state.index && state.index.files) || {};

  // Auto-expand any folder that contains the current path.
  navCtx.list.forEach((m) => {
    const mKey = m.path.replace("/README.md", "");
    const mFiles = files[mKey] || [];
    if (mFiles.length > 1 && mFiles.some((fn) => `${mKey}/${fn}` === currentPath))
      readerExpanded.add(mKey);
  });

  const items = navCtx.list.map((m, i) => {
    const mKey = m.path.replace("/README.md", "");
    const mFiles = files[mKey] || ["README.md"];

    if (mFiles.length <= 1) {
      const isActive = m.path === currentPath;
      return `<li><a href="#" class="mod-item${isActive ? " active" : ""}" data-midx="${i}" title="${esc(m.title)}">${esc(m.title)}</a></li>`;
    }

    // Multi-file module: collapsible folder
    const isOpen = readerExpanded.has(mKey);
    const subItems = mFiles.map((fn) => {
      const filePath = `${mKey}/${fn}`;
      const isFileCurrent = filePath === currentPath;
      const label = fn === "README.md" ? "readme" : fn.replace(".md", "").replace(/_/g, " ");
      return `<li><a href="#" class="mod-file${isFileCurrent ? " active" : ""}" data-path="${esc(filePath)}" title="${esc(label)}">${esc(label)}</a></li>`;
    }).join("");

    return `<li class="mod-group${isOpen ? " open" : ""}">
      <div class="mod-folder" data-midx="${i}" title="${esc(m.title)}"><span class="mod-arrow">&#9654;</span><span class="mod-fname">${esc(m.title)}</span></div>
      <ul class="mod-subfiles">${subItems}</ul>
    </li>`;
  }).join("");

  modEl.innerHTML = `<div class="mod-h">Modules</div><ul>${items}</ul>`;

  modEl.querySelectorAll("a.mod-item").forEach((a) => a.addEventListener("click", (e) => {
    e.preventDefault();
    const i = +a.dataset.midx;
    openReaderPath(navCtx.list[i].path, navCtx.list[i].title, { list: navCtx.list, idx: i });
  }));

  modEl.querySelectorAll(".mod-folder").forEach((folder) => {
    folder.addEventListener("click", () => {
      const li = folder.closest(".mod-group");
      const mKey = navCtx.list[+folder.dataset.midx].path.replace("/README.md", "");
      const willOpen = !li.classList.contains("open");
      li.classList.toggle("open", willOpen);
      if (willOpen) readerExpanded.add(mKey); else readerExpanded.delete(mKey);
    });
  });

  modEl.querySelectorAll("a.mod-file").forEach((a) => a.addEventListener("click", (e) => {
    e.preventDefault();
    const pathParts = a.dataset.path.split("/");
    const mKey = pathParts.slice(0, 2).join("/");
    const midx = navCtx.list.findIndex((m) => m.path.replace("/README.md", "") === mKey);
    openReaderPath(a.dataset.path, null, midx >= 0 ? { list: navCtx.list, idx: midx } : reader.nav);
  }));

  const active = modEl.querySelector(".active");
  if (active) active.scrollIntoView({ block: "nearest" });
}

// Reflect fullscreen / index-open state onto the DOM.
function applyReaderModes() {
  const p = el("#reader"); if (!p) return;
  p.classList.toggle("full", reader.full);
  p.classList.toggle("toc-open", reader.toc);
  p.classList.toggle("modules-open", reader.modules);
  document.body.classList.toggle("reader-full", reader.full);
  const fb = el("#readerFull"); if (fb) fb.classList.toggle("on", reader.full);
  const ib = el("#readerIdx"); if (ib) ib.classList.toggle("on", reader.toc);
  const mb = el("#readerMod"); if (mb) mb.classList.toggle("on", reader.modules);
}

// Wire in-body links: relative repo links open in the reader (with back-stack);
// in-page anchors scroll within the pane.
function wireReaderBody(body) {
  body.querySelectorAll("a.md-link").forEach((a) => a.addEventListener("click", (e) => {
    e.preventDefault();
    const target = resolvePath(reader.path, a.dataset.rel || "");
    reader.back.push({ path: reader.path, title: reader.titleText, nav: reader.nav });
    openReaderPath(target, null, null, a.dataset.frag);
  }));
  body.querySelectorAll("a.md-anchor").forEach((a) => a.addEventListener("click", (e) => {
    e.preventDefault();
    const t = body.querySelector("#" + CSS.escape(a.dataset.frag || ""));
    if (t) t.scrollIntoView({ behavior: "smooth", block: "start" });
  }));
}

// Open any repo content file by path. Pushing onto the back-stack is the caller's
// job (cross-links push; Back/Prev/Next do not), keeping history clean.
async function openReaderPath(path, title, navCtx, frag) {
  // Route history: first open pushes a #/reader entry (so browser Back closes the
  // reader onto the underlying screen); navigating within the reader replaces it.
  // _readerRouting is set when the router itself drove the open (don't re-write).
  if (!state._readerRouting) {
    const h = readerHash(path, frag);
    if (document.body.classList.contains("reader-open")) history.replaceState(null, "", h);
    else { state.underHash = location.hash || "#/home"; history.pushState(null, "", h); }
  }
  reader.path = path;
  reader.nav = navCtx || null;
  reader.titleText = title || titleFromPath(path);
  let panel = el("#reader");
  if (!panel) { panel = document.createElement("aside"); panel.id = "reader"; document.body.appendChild(panel); }
  const nav = reader.nav;
  const backBtn = reader.back.length
    ? `<button class="reader-nav" id="readerBack" title="Back">&lsaquo; Back</button>` : "";
  const modBtn = nav
    ? `<button class="reader-nav reader-icon" id="readerMod" title="Module list">&#9776;</button>` : "";
  const navBtns = nav
    ? `<button class="reader-nav" id="readerPrev" title="Previous topic" ${nav.idx <= 0 ? "disabled" : ""}>&lsaquo; Prev</button>
       <button class="reader-nav" id="readerNext" title="Next topic" ${nav.idx >= nav.list.length - 1 ? "disabled" : ""}>Next &rsaquo;</button>` : "";
  panel.innerHTML = `<div class="reader-grip" id="readerGrip"></div>
    <div class="reader-head">
      ${backBtn}${modBtn}
      <span class="reader-title">${esc(reader.titleText)}</span>
      ${navBtns}
      <button class="reader-nav reader-icon rfs" id="readerFsDn" title="Smaller text">A&#8722;</button>
      <button class="reader-nav reader-icon rfs" id="readerFsUp" title="Larger text">A+</button>
      <button class="reader-nav reader-icon" id="readerIdx" title="Contents">&#8801;</button>
      <button class="reader-nav reader-icon" id="readerFull" title="Fullscreen (F)">&#11036;</button>
      <button class="reader-close" id="readerClose" title="Close (Esc)">&times;</button>
    </div>
    <div class="reader-progress" aria-hidden="true"><i id="readerProg"></i></div>
    <div class="reader-body" id="readerBody"><div class="loading">Loading&hellip;</div></div>
    <button class="reader-top" id="readerTop" title="Back to top" aria-label="Back to top">&uarr;</button>`;
  document.body.classList.add("reader-open");
  applyReaderModes();
  wireGrips();
  el("#readerClose").addEventListener("click", closeReader);
  el("#readerFsDn").addEventListener("click", () => applyReaderFont(-1));
  el("#readerFsUp").addEventListener("click", () => applyReaderFont(1));
  // Reading progress bar + back-to-top, driven by the body's scroll position.
  {
    const body = el("#readerBody"), prog = el("#readerProg"), top = el("#readerTop");
    body.addEventListener("scroll", () => {
      const max = body.scrollHeight - body.clientHeight;
      prog.style.width = max > 0 ? (body.scrollTop / max) * 100 + "%" : "0";
      top.classList.toggle("show", body.scrollTop > 600);
    }, { passive: true });
    top.addEventListener("click", () => body.scrollTo({ top: 0, behavior: REDUCED() ? "auto" : "smooth" }));
  }
  if (nav) {
    el("#readerMod").addEventListener("click", () => {
      reader.modules = !reader.modules;
      localStorage.setItem("sd_reader_modules", reader.modules ? "1" : "0");
      applyReaderModes();
    });
  }
  el("#readerIdx").addEventListener("click", () => {
    reader.toc = !reader.toc; localStorage.setItem("sd_reader_toc", reader.toc ? "1" : "0"); applyReaderModes();
  });
  el("#readerFull").addEventListener("click", () => {
    reader.full = !reader.full;
    if (reader.full) reader.toc = true;            // entering fullscreen reveals the index
    localStorage.setItem("sd_reader_full", reader.full ? "1" : "0");
    localStorage.setItem("sd_reader_toc", reader.toc ? "1" : "0");
    applyReaderModes();
  });
  if (backBtn) el("#readerBack").addEventListener("click", () => { const p = reader.back.pop(); if (p) openReaderPath(p.path, p.title, p.nav); });
  if (nav) {
    el("#readerPrev").addEventListener("click", () => { if (nav.idx > 0) openReaderPath(nav.list[nav.idx - 1].path, nav.list[nav.idx - 1].title, { list: nav.list, idx: nav.idx - 1 }); });
    el("#readerNext").addEventListener("click", () => { if (nav.idx < nav.list.length - 1) openReaderPath(nav.list[nav.idx + 1].path, nav.list[nav.idx + 1].title, { list: nav.list, idx: nav.idx + 1 }); });
  }
  try {
    if (readerCache[path] == null) {
      const r = await fetch(`../${path}`, { cache: "no-store" });
      if (!r.ok) throw 0;
      readerCache[path] = await r.text();
    }
    if (reader.path !== path) return;              // user navigated away during the fetch
    const b = el("#readerBody");
    b.innerHTML = `<nav class="reader-modules" id="readerModules"></nav><div class="modules-grip" id="modulesGrip"></div><div class="md-body" id="readerMain">${mdRender(readerCache[path])}</div><div class="toc-grip" id="tocGrip"></div><nav class="reader-toc" id="readerToc"></nav>`;
    wireSidebarGrips();
    const main = el("#readerMain");
    buildModuleNav(el("#readerModules"), reader.nav, path);
    const headCount = buildToc(el("#readerToc"), main);
    el("#readerIdx").style.display = headCount >= 3 ? "" : "none";   // nothing to index -> hide toggle
    wireReaderBody(main);
    wireDiagramsAndCopy(main);                     // copy buttons + ASCII-diagram zoom
    renderMermaid(main);                           // no-op when page has no mermaid fences
    b.scrollTop = 0;
    if (frag) { const t = main.querySelector("#" + CSS.escape(frag)); if (t) t.scrollIntoView({ block: "start" }); }
    localStorage.setItem("sd_last_read", JSON.stringify({ path, title: reader.titleText }));   // Study's "Continue reading"
  } catch {
    const b = el("#readerBody"); if (b) b.innerHTML = `<div class="error">Couldn't load this page. Check your connection and try again.</div>`;
  }
}

// Entry point from a quiz/flashcard reveal: a module README, fresh history, no prev/next.
function openReader(module, moduleName) {
  reader.back = [];
  return openReaderPath(`${module}/README.md`, moduleName, null);
}

// Remove the reader overlay only; the underlying screen DOM is untouched.
function closeReaderDom() {
  document.body.classList.remove("reader-open", "reader-full");
  const p = el("#reader"); if (p) p.remove();
  reader.path = null; reader.back = []; reader.nav = null;
}

// User-initiated close (X / Esc): drop the overlay and restore the underlying
// screen's hash. (Browser Back is handled in the router, which calls closeReaderDom.)
function closeReader() {
  closeReaderDom();
  if (location.hash.startsWith("#/reader")) history.replaceState(null, "", state.underHash || "#/home");
}

/* ---------- hash router ---------- */
// Screens are hash routes so browser Back/Forward, refresh, and shareable URLs
// all work without a build step. go() sets the hash; a single hashchange
// listener resolves the route -> screen render. _navLock swallows the one
// programmatic hash write we make when restoring the quiz hash on a blocked Back.
// Live (Phase C): #/gauntlet #/codex #/interview/<sec>. Reserved for later phases: #/insights #/debrief.
let _navLock = false;

const readerHash = (path, frag) => "#/reader/" + encodeURIComponent(path) + (frag ? "@" + frag : "");
const quizRoute = (section) =>
  section === "review" ? "#/quiz/review" : section === "weakspots" ? "#/quiz/weak" : "#/quiz/" + section;
function setQuizHash(section) {
  state.quizHash = quizRoute(section);
  history.replaceState(null, "", state.quizHash);   // silent: no dispatch during play
}

// Navigate to a route (adds a history entry). The hashchange listener dispatches.
function go(route) {
  const r = route.startsWith("#") ? route : "#" + route;
  if (location.hash === r) { onHashChange(); return; }   // same hash fires no event -> dispatch by hand
  location.hash = r;
}
// Redirect without adding a history entry (fallbacks), then dispatch.
function redirect(route) {
  const r = route.startsWith("#") ? route : "#" + route;
  history.replaceState(null, "", r);
  onHashChange();
}

function onHashChange() {
  if (_navLock) { _navLock = false; return; }       // swallow our own hash restore
  const route = location.hash || "#/home";
  const isReaderRoute = route.startsWith("#/reader/");

  // Reader is an overlay: any non-reader route while it's open just closes it
  // (Back-to-close); the underlying screen DOM is still mounted underneath.
  if (document.body.classList.contains("reader-open") && !isReaderRoute) {
    closeReaderDom();
    return;
  }
  // Leaving a live blitz: 0 answered -> leave silently; else restore the quiz
  // hash and raise the pause sheet with this route as the pending destination.
  if (state.inQuiz && !route.startsWith("#/quiz") && !isReaderRoute) {
    if (answeredCount() === 0) { clearDeckSnapshot(); state.inQuiz = false; }
    else {
      _navLock = true;
      location.hash = state.quizHash || quizRoute(state.section);
      openPauseSheet(() => go(route));
      return;
    }
  }
  vt(() => dispatch(route));
}

function dispatch(route) {
  if (route.startsWith("#/reader/")) {
    const enc = route.slice("#/reader/".length);
    const at = enc.indexOf("@");
    const path = decodeURIComponent(at >= 0 ? enc.slice(0, at) : enc);
    const frag = at >= 0 ? enc.slice(at + 1) : null;
    if (reader.path === path) return;               // already showing it
    state._readerRouting = true;                    // suppress openReaderPath's own history write
    if (!document.body.classList.contains("reader-open")) { state.underHash = "#/home"; renderHome(); }
    openReaderPath(path, null, reader.nav || null, frag);
    state._readerRouting = false;
    return;
  }
  if (route.startsWith("#/quiz/")) {
    if (state.inQuiz) return;                        // deck already live and rendered
    const sec = route.slice("#/quiz/".length);       // a live deck can't survive refresh -> fall back
    if (sec !== "review" && sec !== "weak" && state.index.sections[sec]) { redirect("#/topics/" + sec); return; }
    redirect("#/home"); return;
  }
  /* [C] tentpole routes (previously reserved) — and leaving the quiz drops any
     lingering interviewer/gauntlet skin before the next screen paints. */
  if (!state.inQuiz) { document.body.classList.remove("interview-mode"); state.interview = null; state.gauntlet = null; }
  if (route === "#/gauntlet") { startGauntlet(); return; }
  if (route === "#/codex") { renderCodex(); return; }
  if (route.startsWith("#/interview/")) { startInterview(route.slice("#/interview/".length)); return; }
  if (route.startsWith("#/topics/")) { openTopics(route.slice("#/topics/".length)); return; }
  if (route.startsWith("#/study/")) { openStudySection(route.slice("#/study/".length)); return; }
  if (route === "#/study") { renderStudy(); return; }
  if (route === "#/progress") { renderProgress(); return; }
  renderHome();                                     // #/home and any unknown route
}

/* ---------- keyboard ---------- */
document.addEventListener("keydown", (e) => {
  const typing = (e.target.tagName || "").toLowerCase() === "input";
  if (e.key === "Escape" && el("#helpOverlay")) { el("#helpOverlay").remove(); return; }
  if (e.key === "Escape" && el("#pauseSheet")) { el("#pauseSheet").remove(); return; }
  if (e.key === "?" && !typing) { e.preventDefault(); toggleHelp(); return; }
  if (document.body.classList.contains("reader-open")) {
    if (e.key === "Escape") {                       // exit fullscreen first, then close
      e.preventDefault();
      if (reader.full) { reader.full = false; localStorage.setItem("sd_reader_full", "0"); applyReaderModes(); }
      else closeReader();
      return;
    }
    if ((e.key === "f" || e.key === "F") && (e.target.tagName || "").toLowerCase() !== "input") {
      e.preventDefault();
      reader.full = !reader.full;
      if (reader.full) reader.toc = true;
      localStorage.setItem("sd_reader_full", reader.full ? "1" : "0");
      localStorage.setItem("sd_reader_toc", reader.toc ? "1" : "0");
      applyReaderModes();
      return;
    }
    return;   // reader is open: never let keys drive the quiz hidden behind it
  }
  if (!state.inQuiz) return;
  if (e.repeat) return;   // holding a key must not auto-advance/grade a deck
  if ((e.target.tagName || "").toLowerCase() === "input") return;
  if (state.mode === "flash") {
    if (!state.answered) {
      if (e.key === " " || e.key === "Enter") { e.preventDefault(); revealCard(); }
    } else if (e.key === "1") { e.preventDefault(); gradeCard(false, null); }      // Missed
    else if (e.key === "2") { e.preventDefault(); gradeCard(true, "low"); }         // Hard
    else if (e.key === "3" || e.key === "Enter") { e.preventDefault(); gradeCard(true, "high"); }  // Easy
    return;
  }
  const cur = state.deck[state.queue[state.cursor]];
  // A5 teach concept card: Enter/Space moves on (no answer to give here).
  if (cur && cur.status === "skipped" && !cur.taught) {
    if (e.key === "Enter" || e.key === " ") { e.preventDefault(); teachDone(); }
    return;
  }
  // A3 recall-first gate: Space reveals the hidden options; block option keys
  // until then so a blind 1-4 can't answer an unseen list.
  if (state.hard && cur && !cur.revealed && !state.answered) {
    if (e.key === " ") { e.preventDefault(); revealHardOptions(); }
    return;
  }
  // A4 confidence step: after locking a pick, 1 = sure, 2 = not sure.
  if (state.awaitingConf) {
    if (e.key === "1") { e.preventDefault(); pickConfidence("high"); }
    else if (e.key === "2") { e.preventDefault(); pickConfidence("low"); }
    return;
  }
  if (state.answered) {
    if (e.key === "Enter") { e.preventDefault(); nextQuestion(); }
    return;
  }
  if (/^[1-4]$/.test(e.key)) {
    const i = +e.key - 1;
    if (i < state.curOptsLen) { e.preventDefault(); answer(i); }
  } else if (e.key.toLowerCase() === "s") {
    if (cur && cur.status === "pending") { e.preventDefault(); skipQuestion(); }
  }
});

/* ============================================================================
   [C] PHASE C — Gauntlet · Codex · Skyline · Ledger · Interviewer
   Built on the Phase 0/A1 architecture: real cyrb53/mulberry32 + seededShuffle,
   the moments engine (extra moments feed queueMoments), the hash router's
   previously-reserved routes, and the two-step confidence answer flow (hooks
   live in gradeAnswer). Persisted additions (all additive): progress.awards,
   progress.deepReads, localStorage sd_gauntlet. Deterministic randomness only
   (seeded on todayISO) for anything that must survive a reload.
   ========================================================================== */

/* ---------- [C] shared helpers ---------- */
const prettyMod = (mod) => (String(mod).split("/")[1] || String(mod)).replace(/_/g, " ");
function tierOf(section) {
  const t = sectionTier((state.progress.sections || {})[section]);
  return t ? t.toLowerCase() : null;               // null | bronze | silver | gold
}
function bumpDeepReads() {
  const p = state.progress;
  p.deepReads = (p.deepReads || 0) + 1;
  localStorage.setItem("sd_progress", JSON.stringify(p));   // persist now; deep_habit is detected at the next finish()
}

/* ---------- [C] codex model (100% derived from review records) ---------- */
// needed = 5 captures, or the module's whole bank when it holds fewer than 5.
function moduleNeeded(mod) {
  const sec = mod.split("/")[0], bank = bankCache[sec];
  if (bank) { const n = bank.filter((q) => q.module === mod).length; if (n && n < 5) return n; }
  return 5;
}
// held = review records with reps >= 1 AND not overdue; captured = held >= needed;
// foil = any record proven over a 21-day interval; tarnished = would still be
// captured ignoring overdue, but decay dropped it below the bar.
function codexState(progress, onlyMods) {
  const reviews = (progress && progress.reviews) || {};
  const files = (state.index && state.index.files) || {};
  const today = todayISO();
  const byMod = new Map();
  for (const r of Object.values(reviews)) {
    if (!r.module) continue;
    let a = byMod.get(r.module); if (!a) byMod.set(r.module, a = []);
    a.push(r);
  }
  const out = new Map();
  for (const mod of onlyMods || Object.keys(files)) {
    const recs = byMod.get(mod) || [];
    const withRep = recs.filter((r) => (r.reps || 0) >= 1);
    const held = withRep.filter((r) => r.due && r.due > today).length;
    const heldEver = withRep.length;
    const needed = moduleNeeded(mod);
    const captured = held >= needed;
    out.set(mod, {
      held, heldEver, needed, captured,
      foil: recs.some((r) => (r.interval || 0) >= 21),
      tarnished: !captured && heldEver >= needed,
    });
  }
  return out;
}
// Snapshot piece for progressSnapshot(): capture/foil of only the modules the
// live deck touched (cheap), so queueMoments can diff pre/post in one pass.
function cDeckCodex() {
  if (!state.deck || !state.deck.length) return {};
  const names = {};
  for (const d of state.deck) names[d.q.module] = d.q.moduleName;
  const cs = codexState(state.progress, Object.keys(names));
  const out = {};
  cs.forEach((v, mod) => { out[mod] = { captured: v.captured, foil: v.foil, name: names[mod] || prettyMod(mod) }; });
  return out;
}

/* ---------- [C] 1. THE GAUNTLET — daily sealed run ---------- */
function loadGauntlet() {
  let g = null;
  try { g = JSON.parse(localStorage.getItem("sd_gauntlet")); } catch { /* corrupt */ }
  return (g && g.date === todayISO()) ? g : null;
}
function saveGauntlet(g) { localStorage.setItem("sd_gauntlet", JSON.stringify(g)); }

async function questionsByIds(ids) {
  const secs = new Set(ids.map((id) => id.split("/")[0]));
  for (const s of secs) await loadBank(s);
  const out = [];
  for (const id of ids) { const byId = bankById(id.split("/")[0]); const q = byId && byId.get(id); if (q) out.push(q); }
  return out;
}

// Deterministic 10-question recipe for today: Q1-3 oldest due reviews · Q4-7
// suggested section (core/intermediate first) · Q8-9 weakest section
// (intermediate first) · Q10 an advanced from the weakest module by lapses.
async function buildGauntletDeck() {
  const seed = todayISO();
  const used = new Set(), picks = [];
  const push = (q) => { if (q && !used.has(q.id)) { used.add(q.id); picks.push(q); return true; } return false; };

  const due = dueReviews().slice(0, 8), bySec = {};
  due.forEach(([id, r]) => (bySec[r.section] = bySec[r.section] || []).push(id));
  for (const sec of Object.keys(bySec)) {
    await loadBank(sec);
    const byId = bankById(sec); if (!byId) continue;
    for (const id of bySec[sec]) { if (picks.length >= 3) break; push(byId.get(id)); }
    if (picks.length >= 3) break;
  }

  const sSec = (state.today && state.today.section) || pickSection();
  const sBank = (await loadBank(sSec)) || [];
  const sShuf = seededShuffle(sBank, seed + "|sug|" + sSec);
  const sPool = [...sShuf.filter((q) => q.difficulty !== "advanced"), ...sShuf];
  while (picks.length < 7) { const q = sPool.shift(); if (!q) break; push(q); }

  const weak = weakSections()[0];
  const wSec = weak ? weak.s : pickSection();
  const wBank = (await loadBank(wSec)) || [];
  const wShuf = seededShuffle(wBank, seed + "|weak|" + wSec);
  const wPool = [...wShuf.filter((q) => q.difficulty === "intermediate"), ...wShuf];
  while (picks.length < 9) { const q = wPool.shift(); if (!q) break; push(q); }

  // The Final Question
  let weakRec = null;
  for (const r of Object.values(state.progress.reviews || {}))
    if (r.module && (r.lapses || 0) > (weakRec ? weakRec.lapses : 0)) weakRec = r;
  let finalQ = null;
  if (weakRec && weakRec.module) {
    const mb = (await loadBank(weakRec.section)) || [];
    finalQ = seededShuffle(mb.filter((q) => q.module === weakRec.module && q.difficulty === "advanced"), seed + "|fin1").find((q) => !used.has(q.id));
  }
  if (!finalQ) finalQ = seededShuffle(wBank.filter((q) => q.difficulty === "advanced"), seed + "|fin2").find((q) => !used.has(q.id));
  if (!finalQ) finalQ = seededShuffle(sBank.filter((q) => q.difficulty === "advanced"), seed + "|fin3").find((q) => !used.has(q.id));
  push(finalQ);

  const filler = [...sPool, ...wPool];
  for (let i = 0; i < filler.length && picks.length < 10; i++) push(filler[i]);
  return picks.slice(0, 10);
}

async function startGauntlet() {
  app.innerHTML = `<div class="loading">Sealing today's gauntlet&hellip;</div>`;
  let g = loadGauntlet(), questions;
  if (g && g.qids && g.qids.length) {
    questions = await questionsByIds(g.qids);      // frozen at first open: same qids all day
  } else {
    questions = await buildGauntletDeck();
    g = { date: todayISO(), qids: questions.map((q) => q.id), sealed: false, score: null, attempt: [] };
    saveGauntlet(g);
  }
  if (!questions.length) { redirect("#/home"); return; }
  const practice = !!g.sealed;                     // one scored attempt/day; then unscored practice
  state.section = practice ? "gauntlet-practice" : "gauntlet";
  state.modules = null;
  state.gauntlet = { scored: !practice, practice };
  state.interview = null;
  if (practice) startDeck(questions, startGauntlet);                          // normal mixed blitz of the same qids
  else startDeck(questions, startGauntlet, { keepOrder: true });              // sealed run: the recipe IS the arc
}

function gauntletCardHTML() {
  const g = loadGauntlet();
  if (g && g.sealed) {
    const n = (state.progress.history || []).filter((h) => h.section === "gauntlet").length;
    return `<button class="c-gaunt-card sealed" id="gauntBtn">
        <div class="c-gaunt-seal broken" aria-hidden="true"></div>
        <div class="c-gaunt-body">
          <div class="eyebrow gold">Daily Gauntlet</div>
          <h2>Gauntlet #${n} &middot; ${g.score}/${(g.qids || []).length}</h2>
          <p class="msg">Sealed for today. Replay as unscored practice.</p>
        </div>
        <span class="c-gaunt-go">Practice &rarr;</span>
      </button>`;
  }
  return `<button class="c-gaunt-card" id="gauntBtn">
      <div class="c-gaunt-seal" aria-hidden="true"></div>
      <div class="c-gaunt-body">
        <div class="eyebrow gold">Daily Gauntlet</div>
        <h2>Today's Gauntlet</h2>
        <p class="msg">Sealed &middot; 10 questions, one attempt.</p>
      </div>
      <span class="c-gaunt-go">Enter &rarr;</span>
    </button>`;
}
function renderPracticeBanner() {
  if (el("#cPracticeBanner")) return;
  const b = document.createElement("div");
  b.className = "c-practice-banner"; b.id = "cPracticeBanner";
  b.innerHTML = `<span class="c-chip practice">practice</span> today's gauntlet is sealed`;
  app.prepend(b);
}

/* ---------- [C] 2. THE CODEX — collection, 100% derived ---------- */
function codexOrderedSections() {
  const files = (state.index && state.index.files) || {};
  const secOrder = Object.keys(STUDY_ORDER);
  const all = [...new Set(Object.keys(files).map((k) => k.split("/")[0]))];
  return [...secOrder.filter((s) => all.includes(s)), ...all.filter((s) => !secOrder.includes(s))];
}
function codexModulesOf(sec) {
  const files = (state.index && state.index.files) || {};
  const order = STUDY_ORDER[sec] || [];
  return Object.keys(files).filter((k) => k.split("/")[0] === sec)
    .sort((a, b) => (order.indexOf(a) === -1 ? 9999 : order.indexOf(a)) - (order.indexOf(b) === -1 ? 9999 : order.indexOf(b)));
}
function renderCodex() {
  state.inQuiz = false;
  refreshStats();
  const cs = codexState(state.progress);
  let captured = 0, foil = 0;
  cs.forEach((v) => { if (v.captured) captured++; if (v.foil) foil++; });
  const shelves = codexOrderedSections().map((sec) => {
    const mods = codexModulesOf(sec);
    if (!mods.length) return "";
    const cards = mods.map((mod) => {
      const v = cs.get(mod) || { held: 0, needed: 5, captured: false, foil: false, tarnished: false };
      const cls = [v.captured ? "captured" : "", v.foil ? "foil" : "", v.tarnished ? "tarnished" : "",
        (!v.captured && !v.tarnished) ? "dim" : ""].filter(Boolean).join(" ");
      const dots = Array.from({ length: v.needed }, (_, i) => `<span class="cx-dot ${i < v.held ? "on" : ""}"></span>`).join("");
      return `<button class="cx-card ${cls}" data-mod="${esc(mod)}" title="${esc(prettyMod(mod))} — ${v.held}/${v.needed} held${v.foil ? " · foil" : ""}${v.tarnished ? " · fading" : ""}">
          <span class="cx-name">${esc(prettyMod(mod))}</span>
          <span class="cx-dots">${dots}</span>
        </button>`;
    }).join("");
    return `<div class="cx-shelf"><h2 class="section-h">${esc(label(sec))}</h2><div class="cx-grid">${cards}</div></div>`;
  }).join("");
  app.innerHTML = `
    <div class="hero"><h1>The Codex</h1>
      <p><b>${captured}</b>/${cs.size} captured &middot; <b>${foil}</b> foil</p></div>
    ${shelves}
    <div class="row" style="margin-top:18px"><button class="ghost" id="cxHome">&larr; Home</button></div>`;
  el("#cxHome").addEventListener("click", () => go("#/home"));
  document.querySelectorAll(".cx-card").forEach((b) =>
    b.addEventListener("click", () => { reader.back = []; openReaderPath(b.dataset.mod + "/README.md", prettyMod(b.dataset.mod)); }));
  wireReveals();
}

/* ---------- [C] 3. THE SKYLINE — ambient retention city ---------- */
// One building per CAPTURED module, grouped into section districts in
// STUDY_ORDER order. Height tracks held count (3 tiers); windows lit in
// proportion to current retention (dark = overdue). Neglect darkens windows,
// never removes buildings. Hidden entirely at 0 captured.
function skylineSVG(p) {
  const cs = codexState(p);
  const captured = [];
  cs.forEach((v, mod) => { if (v.captured) captured.push({ mod, v }); });
  if (!captured.length) return "";
  const secOrder = Object.keys(STUDY_ORDER);
  const key = (mod) => {
    const sec = mod.split("/")[0], order = STUDY_ORDER[sec] || [];
    return [(secOrder.indexOf(sec) === -1 ? 99 : secOrder.indexOf(sec)), (order.indexOf(mod) === -1 ? 999 : order.indexOf(mod))];
  };
  captured.sort((a, b) => { const ka = key(a.mod), kb = key(b.mod); return ka[0] - kb[0] || ka[1] - kb[1]; });
  const BW = 13, GAP = 4, DGAP = 12, H = 72, PAD = 6;
  const rnd = mulberry32(cyrb53("skyline|" + todayISO()) >>> 0);   // seeded flicker picks
  let x = PAD, prevSec = null, buildings = "";
  for (const { mod, v } of captured) {
    const sec = mod.split("/")[0];
    if (prevSec !== null) x += (sec !== prevSec ? DGAP : GAP);
    prevSec = sec;
    const bh = v.held >= 12 ? 56 : v.held >= 8 ? 42 : 30;
    const by = H - bh - 2;
    const winTotal = Math.max(2, Math.min(6, v.heldEver || v.needed));
    const lit = Math.max(0, Math.min(winTotal, Math.round(winTotal * (v.held / Math.max(1, v.heldEver)))));
    let wins = "";
    for (let i = 0; i < winTotal; i++) {
      const col = i % 2, row = (i / 2) | 0;
      const wx = x + 3 + col * 5, wy = by + 4 + row * 5, on = i < lit;
      const flick = (!REDUCED() && on && rnd() < 0.18) ? ' class="sky-flicker"' : "";
      wins += `<rect x="${wx}" y="${wy}" width="3" height="3" fill="${on ? "var(--warn)" : "var(--faint)"}" fill-opacity="${on ? 0.9 : 0.15}"${flick}/>`;
    }
    buildings += `<g><title>${esc(prettyMod(mod))} — ${lit}/${winTotal} lit</title>` +
      `<rect x="${x}" y="${by}" width="${BW}" height="${bh}" rx="2" fill="var(--accent)" fill-opacity="0.14" stroke="var(--accent)" stroke-opacity="0.22"/>${wins}</g>`;
    x += BW;
  }
  return `<div class="skyline" aria-label="Retention skyline: ${captured.length} captured modules">` +
    `<svg viewBox="0 0 ${x + PAD} ${H}" width="100%" height="${H}" preserveAspectRatio="xMinYMax meet" role="img">${buildings}</svg></div>`;
}

/* ---------- [C] 4. THE LEDGER — achievements certifying learning events ---------- */
const AWARDS = [
  { id: "clean_slate", title: "Clean Slate", hint: "Clear every due review with 10+ tracked." },
  { id: "long_memory", title: "Long Memory", hint: "Answer right at a 30-day interval." },
  { id: "comeback", title: "Comeback", hint: "3+ misses, then 7 straight correct in a session." },
  { id: "cold_open", title: "Cold Open", hint: "8/10+ on a gauntlet after a 7-day gap." },
  { id: "cartographer", title: "Cartographer", hint: "Touch every module of a section once." },
  { id: "triple_gold", title: "Triple Gold", hint: "Reach Gold tier in three sections." },
  { id: "restored", title: "Restored", hint: "Revive a 3x-lapsed question to full ease." },
  { id: "deep_habit", title: "Deep Habit", hint: "Open 10 dive-deepers from misses." },
  { id: "foil_row", title: "Foil Row", hint: "Hold a 21-day interval in every module of a section." },
];
function comebackHit(seq) {
  let misses = 0, run = 0;
  for (const s of seq || []) {
    if (s === "w") { misses++; run = 0; }
    else if (s === "c") { run++; if (misses >= 3 && run >= 7) return true; }
  }
  return false;
}
// Detect newly earned awards (never un-earn), write them into progress.awards,
// return the definitions of the fresh ones. Runs post-save inside finish().
function checkAwards(ctx, stats) {
  const p = state.progress;
  if (!p.awards) p.awards = {};
  const reviews = p.reviews || {}, out = [];
  const grant = (id) => {
    if (p.awards[id]) return;
    const def = AWARDS.find((a) => a.id === id); if (!def) return;
    p.awards[id] = ctx.today; out.push(def);
  };
  if (Object.keys(reviews).length >= 10 && dueReviews().length === 0) grant("clean_slate");
  if ((state.sessMaxInterval || 0) >= 30) grant("long_memory");
  if (comebackHit(state.sessSeq)) grant("comeback");
  if (state.section === "gauntlet" && stats.correct >= 8 && stats.total >= 10 && ctx.daysSinceLast >= 7) grant("cold_open");
  if (state.sessRestored) grant("restored");
  if ((p.deepReads || 0) >= 10) grant("deep_habit");
  const golds = Object.keys(p.sections || {}).filter((s) => sectionTier(p.sections[s]) === "Gold").length;
  if (golds >= 3) grant("triple_gold");
  const files = (state.index && state.index.files) || {};
  for (const sec of new Set(ctx.mods.map((m) => m.split("/")[0]))) {
    const secMods = Object.keys(files).filter((k) => k.split("/")[0] === sec);
    if (!secMods.length) continue;
    const haveRec = new Set(Object.values(reviews).filter((r) => r.section === sec && r.module).map((r) => r.module));
    if (secMods.every((m) => haveRec.has(m))) grant("cartographer");
    const foilMods = new Set(Object.values(reviews).filter((r) => r.section === sec && (r.interval || 0) >= 21 && r.module).map((r) => r.module));
    if (secMods.every((m) => foilMods.has(m))) grant("foil_row");
  }
  return out;
}
function ledgerStripHTML() {
  const awards = (state.progress && state.progress.awards) || {};
  const chips = AWARDS.map((a) => awards[a.id]
    ? `<div class="ledger-chip earned" title="${esc(a.title)}"><span class="lg-t">${esc(a.title)}</span><span class="lg-d">${esc(awards[a.id])}</span></div>`
    : `<div class="ledger-chip" title="${esc(a.hint)}"><span class="lg-t">${esc(a.title)}</span><span class="lg-h">${esc(a.hint)}</span></div>`).join("");
  const passes = Object.keys(awards).filter((k) => k.startsWith("interview_") || k.startsWith("panel_")).map((k) => {
    const sec = k.replace(/^(interview|panel)_/, ""), kind = k.startsWith("panel_") ? "Panel" : "Interview";
    return `<div class="ledger-chip earned pass"><span class="lg-t">${kind}: ${esc(label(sec))}</span><span class="lg-d">${esc(awards[k])}</span></div>`;
  }).join("");
  return `<h2 class="section-h">Ledger</h2><div class="ledger-strip">${chips}${passes}</div>`;
}

/* ---------- [C] 5. THE INTERVIEWER — graph-driven boss battle ---------- */
const IV_AVATAR_SVG = `<svg viewBox="0 0 64 64" fill="none" aria-hidden="true">
    <defs><linearGradient id="ivg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="var(--accent)"/><stop offset="1" stop-color="var(--accent-2)"/></linearGradient></defs>
    <circle cx="32" cy="22" r="12" fill="url(#ivg)" fill-opacity="0.5"/>
    <path d="M12 58c0-13 9-20 20-20s20 7 20 20z" fill="url(#ivg)" fill-opacity="0.42"/>
    <rect x="20" y="46" width="24" height="12" rx="2" fill="var(--bg-2)" fill-opacity="0.55"/>
  </svg>`;

// "He probes the weakness": highest-w graph edge from the missed module whose
// pool still has questions; fall back to the same module, then any pool.
function pickFollowUp(module, pairs, iv) {
  let best = null, bw = -1;
  for (const p of pairs || []) {
    let other = null;
    if (p.a === module) other = p.b; else if (p.b === module) other = p.a;
    if (!other) continue;
    const arr = iv.pool.get(other);
    if (arr && arr.length && p.w > bw) { bw = p.w; best = other; }
  }
  const mod = best || module;
  let arr = iv.pool.get(mod);
  if (arr && arr.length) { const q = arr.shift(); iv.usedIds.add(q.id); return { q, mod }; }
  for (const [m, a] of iv.pool) { if (a.length) { const q = a.shift(); iv.usedIds.add(q.id); return { q, mod: m }; } }
  return null;                                     // pool exhausted: continue the planned sequence
}
// Swap the next still-pending planned question for the follow-up (swap the deck
// entry in place: no orphaned pending items at finish, dots/total stay stable).
function injectFollowUp(item) {
  const iv = state.interview;
  let at = -1;
  for (let k = state.cursor + 1; k < state.queue.length; k++) {
    const cand = state.deck[state.queue[k]];
    if (cand && cand.status === "pending") { at = k; break; }
  }
  if (at < 0) return null;                          // nothing left to probe
  const res = pickFollowUp(item.q.module, iv.pairs, iv);
  if (!res) return null;
  state.deck[state.queue[at]] = makeItem(res.q);
  iv.lastFollowUp = res;
  return res;
}
function interviewAfterAnswer(item, right) {
  const iv = state.interview; if (!iv) return;
  if (right) {
    const crit = state.combo >= 3;                 // combo crit: 2 damage
    iv.hp = Math.max(0, iv.hp - (crit ? 2 : 1));
    if (iv.hp <= 0) iv.won = true;
    updateInterviewHP(crit);
  } else {
    const res = injectFollowUp(item);
    if (res) {
      const rev = el("#reveal");
      if (rev) {
        const line = document.createElement("div");
        line.className = "iv-followup";
        line.textContent = `He follows up on ${prettyMod(res.mod)}.`;
        rev.appendChild(line);
      }
    }
    updateInterviewHP(false);
  }
}
function updateInterviewHP(crit) {
  const iv = state.interview, f = el("#ivHpFill"); if (!iv || !f) return;
  f.style.width = (100 * iv.hp / iv.maxHp).toFixed(1) + "%";
  if (crit && !REDUCED()) { const s = el("#ivStage"); if (s) { s.classList.remove("hit"); void s.offsetWidth; s.classList.add("hit"); } }
}
function renderInterviewStage() {
  const iv = state.interview; if (!iv) return;
  if (!el("#ivStage")) {
    const stage = document.createElement("div");
    stage.className = "iv-stage"; stage.id = "ivStage";
    stage.innerHTML = `<div class="iv-avatar">${IV_AVATAR_SVG}</div>
      <div class="iv-hpwrap">
        <div class="iv-hplabel">Interviewer${iv.panel ? " · panel" : ""}</div>
        <div class="iv-hpbar"><i id="ivHpFill"></i></div>
      </div>`;
    app.prepend(stage);
  }
  updateInterviewHP(false);
}
function typewriter(node, text) {
  if (REDUCED()) { node.textContent = text; return; }
  node.textContent = ""; let i = 0;
  const tick = () => { node.textContent = text.slice(0, ++i); if (i < text.length) setTimeout(tick, 26); };
  tick();
}
function renderInterviewIntro(section, panel, deckQ) {
  state.inQuiz = false;
  const line = panel
    ? `A panel today. ${deckQ.length} hard questions on ${label(section)}. No warm-up.`
    : `Let's talk about ${label(section)}. ${deckQ.length} questions. Take your time — I won't.`;
  app.innerHTML = `<div class="iv-intro">
      <div class="iv-avatar big" aria-hidden="true">${IV_AVATAR_SVG}</div>
      <div class="iv-type" id="ivType" role="status"></div>
      <button class="cta inline" id="ivBegin">Begin</button>
    </div>`;
  typewriter(el("#ivType"), line);
  el("#ivBegin").addEventListener("click", () => {
    state.section = "interview"; state.modules = null;
    startDeck(deckQ, () => startInterview(section, panel), { keepOrder: true });
  });
  app.focus({ preventScroll: true });
}
// panel: undefined -> derived from tier (Gold runs the all-advanced Panel).
async function startInterview(section, panel) {
  const tier = tierOf(section);
  if (tier !== "silver" && tier !== "gold") { redirect("#/study/" + section); return; }   // locked: path header shows the chip
  if (panel === undefined) panel = tier === "gold";
  app.innerHTML = `<div class="loading">Preparing the interview&hellip;</div>`;
  const bank = (await loadBank(section)) || [];
  const graph = await fetchJSON(`graph/${section}.json`, null, "default");
  const pairs = (graph && graph.pairs) || [];
  const seed = todayISO() + "|iv|" + section + (panel ? "|panel" : "");
  let deckQ;
  if (panel) {
    deckQ = seededShuffle(bank.filter((q) => q.difficulty === "advanced"), seed).slice(0, 12);
  } else {
    const core = seededShuffle(bank.filter((q) => q.difficulty === "core"), seed + "c").slice(0, 4);
    const inter = seededShuffle(bank.filter((q) => q.difficulty === "intermediate"), seed + "i").slice(0, 4);
    const adv = seededShuffle(bank.filter((q) => q.difficulty === "advanced"), seed + "a").slice(0, 4);
    deckQ = [...core, ...inter, ...adv];             // core -> intermediate -> advanced escalation
  }
  if (deckQ.length < 12) {                            // small sections: backfill toward 12
    const seen = new Set(deckQ.map((q) => q.id));
    for (const q of seededShuffle(bank, seed + "fill")) { if (deckQ.length >= 12) break; if (!seen.has(q.id)) { seen.add(q.id); deckQ.push(q); } }
  }
  deckQ = deckQ.slice(0, 12);
  if (deckQ.length < 3) { redirect("#/home"); return; }
  const usedIds = new Set(deckQ.map((q) => q.id)), pool = new Map();
  for (const q of seededShuffle(bank, seed + "pool")) {   // per-module follow-up pools
    if (usedIds.has(q.id)) continue;
    let a = pool.get(q.module); if (!a) pool.set(q.module, a = []); a.push(q);
  }
  state.interview = { section, panel, hp: deckQ.length, maxHp: deckQ.length, pairs, pool, usedIds, lastFollowUp: null, won: false };
  state.gauntlet = null;
  document.body.classList.add("interview-mode");
  renderInterviewIntro(section, panel, deckQ);
}
function insertInterviewControl(section) {
  const hero = el(".path-screen .hero"); if (!hero) return;
  const tier = tierOf(section), awards = state.progress.awards || {};
  const passed = awards["interview_" + section] || awards["panel_" + section];
  const wrap = document.createElement("div"); wrap.className = "iv-cta-wrap";
  let html = passed ? `<span class="c-chip pass">Passed</span>` : "";
  if (tier === "gold") html += `<button class="ghost iv-cta" id="ivGoBtn">${awards["panel_" + section] ? "Panel again" : "Panel"} &rarr;</button>`;
  else if (tier === "silver") html += `<button class="ghost iv-cta" id="ivGoBtn">Face the Interviewer &rarr;</button>`;
  else html += `<span class="c-chip lock">Silver unlocks the Interviewer</span>`;
  wrap.innerHTML = html; hero.appendChild(wrap);
  const b = el("#ivGoBtn"); if (b) b.addEventListener("click", () => go("#/interview/" + section));
}

/* ---------- [C] finish() hooks: seal · verdict · awards ---------- */
// Pre-save: apply the gauntlet seal bonus (folds into bonusXp) and capture the
// context that the save will overwrite (lastPlayed gap, touched modules).
function cBeforeFinish() {
  const p = state.progress, today = todayISO();
  const daysSinceLast = p.lastPlayed
    ? Math.round((new Date(today + "T00:00:00") - new Date(p.lastPlayed + "T00:00:00")) / 86400000)
    : Infinity;
  const mods = [...new Set(state.deck.map((d) => d.q.module))];
  if (state.section === "gauntlet") {
    const g = loadGauntlet();
    if (g && !g.sealed) state.sessionXp += 40;      // seal bonus (one scored attempt/day)
  }
  return { today, daysSinceLast, mods, interviewWon: false };
}
// Post-save: seal the gauntlet, resolve the interview, detect ledger awards.
// Returns moment entries for queueMoments' extra slot.
function cAfterSave(ctx, stats) {
  const p = state.progress;
  if (!p.awards) p.awards = {};
  const extra = [];
  if (state.section === "gauntlet") {
    const g = loadGauntlet();
    if (g && !g.sealed) {
      g.sealed = true; g.score = stats.correct; g.attempt = state.deck.map((d) => d.status);
      saveGauntlet(g);
      const n = (p.history || []).filter((h) => h.section === "gauntlet").length;   // includes today's entry
      extra.push({ tier: "gauntlet", icon: `<span class="c-seal-stamp"></span>`, title: `Gauntlet #${n} — sealed`, sub: `${stats.correct}/${state.deck.length}`, play: () => sfx.seal() });
    }
  }
  if (state.interview) {
    const iv = state.interview;
    ctx.interviewWon = iv.hp <= 0;
    if (ctx.interviewWon) {
      const id = iv.panel ? ("panel_" + iv.section) : ("interview_" + iv.section);
      if (!p.awards[id]) p.awards[id] = ctx.today;
      extra.push({ tier: "gold", icon: `<span class="moment-tier gold">Passed</span>`, title: `Passed: ${label(iv.section)}`, sub: iv.panel ? "Panel cleared." : "The interviewer is satisfied.", play: () => sfx.gold() });
    }
  }
  for (const a of checkAwards(ctx, stats))
    extra.push({ tier: "ledger", icon: `<span class="moment-tier">Ledger</span>`, title: `Ledger: ${a.title}`, sub: a.hint, play: () => sfx.chime() });
  localStorage.setItem("sd_progress", JSON.stringify(p));   // persist awards written above
  return extra;
}
// Results-screen banners for the two special decks (called at the end of finish).
function cApplyResults(ctx, stats) {
  const result = el(".result"); if (!result) return;
  if (state.section === "gauntlet" || state.section === "gauntlet-practice") {
    const b = document.createElement("div"); b.className = "c-gaunt-banner";
    if (state.section === "gauntlet-practice") b.innerHTML = `<span class="c-chip practice">practice</span> today's gauntlet is sealed`;
    else {
      const g = loadGauntlet(), n = (state.progress.history || []).filter((h) => h.section === "gauntlet").length;
      b.innerHTML = `<span class="c-seal-mini"></span> Gauntlet #${n} sealed &middot; ${g ? g.score : stats.correct}/${state.deck.length}`;
    }
    result.insertBefore(b, result.firstChild);
  }
  if (state.interview) {
    const b = document.createElement("div"); b.className = "c-iv-banner " + (ctx.interviewWon ? "won" : "resched");
    b.innerHTML = ctx.interviewWon
      ? `<span class="c-chip pass">Passed</span> ${esc(label(state.interview.section))} &middot; the interviewer is satisfied.`
      : `We'll continue another day. Review what we covered.`;
    result.insertBefore(b, result.firstChild);
  }
}

/* ---------- [C] ?qa=1 debug handle (documented in qa_phaseC.mjs) ---------- */
// Non-mutating preview of which module a follow-up would draw from.
function pickFollowUpPreview(module, iv) {
  let best = null, bw = -1;
  for (const p of iv.pairs || []) {
    let other = null; if (p.a === module) other = p.b; else if (p.b === module) other = p.a;
    if (!other) continue;
    const arr = iv.pool.get(other);
    if (arr && arr.length && p.w > bw) { bw = p.w; best = other; }
  }
  return best || module;
}
if (new URLSearchParams(location.search).get("qa") === "1") {
  window.__qa = {
    state,
    get progress() { return state.progress; },
    correctIndex() { const it = state.deck[state.queue[state.cursor]]; return it ? it.opts.findIndex((o) => o.ok) : -1; },
    wrongIndex() { const it = state.deck[state.queue[state.cursor]]; return it ? it.opts.findIndex((o) => !o.ok) : -1; },
    curModule() { const it = state.deck[state.queue[state.cursor]]; return it ? it.q.module : null; },
    nextModule() { const i = state.queue[state.cursor + 1]; return (i != null && state.deck[i]) ? state.deck[i].q.module : null; },
    gauntlet() { try { return JSON.parse(localStorage.getItem("sd_gauntlet")); } catch { return null; } },
    interview() { return state.interview; },
    interviewHp() { return state.interview ? state.interview.hp : null; },
    expectFollowUp(mod) { return state.interview ? pickFollowUpPreview(mod, state.interview) : null; },
    lastFollowUp() { return state.interview && state.interview.lastFollowUp ? state.interview.lastFollowUp.mod : null; },
    codex(mods) { return Object.fromEntries(codexState(state.progress, mods)); },
    moments() { return document.querySelectorAll(".moment").length; },
    tierOf,
  };
}

/* ---------- boot ---------- */
function syncMuteBtn() {
  const b = el("#muteBtn");
  if (!b) return;
  const on = sfx.isOn();
  b.innerHTML = ICON(on ? "soundOn" : "soundOff");
  b.setAttribute("aria-pressed", on ? "true" : "false");
  b.setAttribute("aria-label", on ? "Sound on" : "Sound off");
}

function syncModeBtn() {
  const b = el("#modeBtn");
  if (!b) return;
  const flash = deckMode() === "flash";
  b.textContent = flash ? "Cards" : "Quiz";
  b.title = flash ? "Flashcards mode (click for multiple-choice)" : "Multiple-choice mode (click for flashcards)";
  b.setAttribute("aria-pressed", flash ? "true" : "false");
  b.classList.toggle("on", flash);
}

async function boot() {
  state.index = await fetchJSON("questions/index.json", null);
  if (!state.index) {
    app.innerHTML = `<div class="error">No question bank found. Run <code>python3 extract.py</code> then reload.</div>`;
    return;
  }
  el("#bankInfo").textContent = `${state.index.total} questions across ${Object.keys(state.index.sections).length} sections`;
  state.progress = loadProgress();
  state.today = {};                                // reserved for the in-app coach (later phase)
  el("#navProgress").addEventListener("click", () => guardedNav(() => go("#/progress")));
  const studyB = el("#navStudy");
  if (studyB) studyB.addEventListener("click", () => guardedNav(() => go("#/study")));
  const helpB = el("#helpBtn");
  if (helpB) helpB.addEventListener("click", toggleHelp);
  restoreReaderWidth();
  applyTheme(curTheme(), false);   // don't persist a ?theme= URL override
  const tb = el("#themeBtn");
  if (tb) tb.addEventListener("click", toggleThemePop);
  const mb = el("#muteBtn");
  if (mb) mb.addEventListener("click", () => { sfx.toggle(); syncMuteBtn(); });
  syncMuteBtn();
  const modeB = el("#modeBtn");
  if (modeB) {
    modeB.addEventListener("click", () => {
      localStorage.setItem("sd_mode", deckMode() === "flash" ? "quiz" : "flash");
      syncModeBtn();
      if (!state.inQuiz) renderHome();        // refresh the CTA caption
    });
  }
  syncModeBtn();
  discardStaleDeck();                              // drop a previous-day resume snapshot
  registerServiceWorker();
  window.addEventListener("hashchange", onHashChange);
  // Normalize an empty hash to #/home (replace, not push) so the very first
  // history entry carries a real route and Back never lands on a hashless URL.
  if (!location.hash) history.replaceState(null, "", "#/home");
  onHashChange();                                  // dispatch the initial route
}

// PWA: register the offline shell/bank cache. Only in secure contexts (https or
// localhost); a file:// or plain-http origin has no serviceWorker and must not throw.
function registerServiceWorker() {
  const secure = location.protocol === "https:" ||
    ["localhost", "127.0.0.1"].includes(location.hostname);
  if (!secure || !navigator.serviceWorker) return;
  try { navigator.serviceWorker.register("sw.js"); } catch { /* unsupported */ }
}

boot();
