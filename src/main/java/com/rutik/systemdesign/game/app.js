/* System Design Daily - 5-minute blitz. Vanilla JS, no build step. */

// True when served as a static site (GitHub Pages); false on local server.py.
// On GitHub Pages there is no /api/ — progress lives in localStorage only.
const IS_STATIC = !["localhost", "127.0.0.1", "0.0.0.0"].includes(location.hostname)
  && !location.hostname.match(/^192\.168\.|^10\.|^172\.(1[6-9]|2\d|3[01])\./);

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
    "java/java8_features","java/java_streams","java/functional_programming","java/java9_to_21_features",
    "java/jvm_internals",
    "java/concurrency","java/collections_internals","java/design_patterns_in_java",
    "java/performance_and_tuning","java/java_memory_model",
    "java/java_interview_patterns","java/testing_junit_mockito","java/annotation_processing",
    "java/structured_concurrency_and_loom","java/foreign_function_and_memory_api","java/reactive_programming",
    "java/networking_and_http_client","java/jdbc_and_database","java/grpc_protobuf","java/microservices_patterns",
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
    "ml/computer_vision","ml/natural_language_processing","ml/recommender_systems","ml/time_series_forecasting","ml/reinforcement_learning",
    "ml/ml_system_design","ml/data_pipelines_and_processing","ml/distributed_training","ml/experiment_tracking_and_versioning","ml/gpu_and_hardware_optimization","ml/active_learning_and_weak_supervision",
    "ml/model_serving_and_inference","ml/model_compression_and_efficiency","ml/monitoring_and_drift_detection","ml/mlops_and_ci_cd",
    "ml/graph_neural_networks","ml/self_supervised_and_contrastive_learning","ml/causal_inference_and_ml","ml/adversarial_ml_and_robustness","ml/uncertainty_quantification_and_conformal_prediction",
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
    "spring/spring_mvc_architecture","spring/request_handling","spring/filters_and_interceptors","spring/spring_webflux","spring/spring_graphql","spring/validation_and_error_handling",
    "spring/spring_data_jpa","spring/spring_transactions","spring/spring_caching",
    "spring/spring_security_architecture","spring/spring_security_jwt_oauth",
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
  if (document.startViewTransition && !REDUCED()) document.startViewTransition(fn);
  else fn();
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
  if (e.button === 3 && typeof state.screenBack === "function") vt(state.screenBack);
});
document.addEventListener("mousedown", (e) => {   // suppress browser history nav
  if (e.button === 3 || e.button === 4) e.preventDefault();
});
const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
const label = (s) => SECTION_LABELS[s] || s;

function shuffle(a) {
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

async function apiGet(path, fallback, cache = "no-store") {
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
async function loadProgress() {
  const fill = (p) => { if (!p.reviews) p.reviews = {}; if (p.freezes == null) p.freezes = 2; if (!p.freezeUsedOn) p.freezeUsedOn = []; return p; };
  if (!IS_STATIC) {
    await flushPendingSessions();                  // replay sessions saved while the server was down
    const p = await apiGet("/api/progress", null);
    if (p) return fill(p);
  }
  let ls = null;
  try { ls = JSON.parse(localStorage.getItem("sd_progress")); } catch { /* corrupt -> reseed */ }
  return ls ? fill(ls)
    : { streak: 0, longestStreak: 0, lastPlayed: null, totalXP: 0, sections: {}, history: [], reviews: {}, freezes: 2, freezeUsedOn: [] };
}

// Sessions that failed to POST are queued and replayed on next boot, so an
// offline server never silently loses a play (which could burn a freeze or
// reset the streak days later).
async function postSession(session) {
  const r = await fetch("/api/progress", {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(session),
  });
  if (!r.ok) throw 0;
  return r.json();
}

async function flushPendingSessions() {
  let queue = [];
  try { queue = JSON.parse(localStorage.getItem("sd_pending_sessions")) || []; } catch { }
  if (!queue.length) return;
  const remaining = [];
  for (const s of queue) {
    try { await postSession(s); } catch { remaining.push(s); }
  }
  localStorage.setItem("sd_pending_sessions", JSON.stringify(remaining));
}

async function saveSession(session) {
  if (IS_STATIC) return saveSessionLocal(session);
  try {
    const data = await postSession(session);
    state.progress = data.progress;
    return { xp: data.xpEarned, freezeUsed: !!data.freezeUsed };
  } catch {
    let queue = [];
    try { queue = JSON.parse(localStorage.getItem("sd_pending_sessions")) || []; } catch { }
    queue.push(session);
    localStorage.setItem("sd_pending_sessions", JSON.stringify(queue.slice(-30)));
    return saveSessionLocal(session);              // optimistic local view until the replay lands
  }
}

// SM-2-lite: mirrors schedule_review() in server.py exactly.
function scheduleReview(rv, status, today) {
  if (status === "correct") {
    rv.reps = (rv.reps || 0) + 1;
    rv.ease = Math.min(3.0, (rv.ease || 2.5) + 0.1);
    const r = rv.reps;
    rv.interval = r === 1 ? 1 : r === 2 ? 3 : Math.max(1, Math.round((rv.interval || 1) * rv.ease));
  } else {
    rv.reps = 0;
    rv.lapses = (rv.lapses || 0) + (status === "wrong" ? 1 : 0);
    rv.ease = Math.max(1.3, (rv.ease || 2.5) - (status === "wrong" ? 0.2 : 0.05));
    rv.interval = 1;
  }
  const due = new Date(today + "T00:00:00");
  due.setDate(due.getDate() + rv.interval);
  rv.due = due.toLocaleDateString("en-CA");
  return rv;
}

// Mirrors record_session's streak-freeze + SM-2 logic in server.py.
// Used for static hosting (GitHub Pages) and as the offline server fallback.
function saveSessionLocal(session) {
  const p = state.progress;
  if (p.freezes == null) p.freezes = 2;
  if (!p.freezeUsedOn) p.freezeUsedOn = [];
  const reviews = (p.reviews = p.reviews || {});
  let correct = 0;
  for (const res of session.results || []) {
    const sec = (p.sections[res.section] = p.sections[res.section] || { seen: 0, correct: 0 });
    sec.seen += 1;
    sec.lastPlayed = session.date;
    if (res.status === "correct") { sec.correct += 1; correct += 1; }
    if (res.id) {
      const rv = reviews[res.id] || { ease: 2.5, interval: 0, reps: 0, lapses: 0 };
      rv.section = res.section; rv.module = res.module;
      scheduleReview(rv, res.status, session.date);
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
    section: session.section || "unknown",
  });
  p.history = p.history.slice(-365);               // same cap as server.py
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
    return `<button class="tile ${s === section ? "suggested" : ""}" data-section="${s}">
        <span class="tname">${esc(label(s))}</span>
        <span class="tmeta">${secs[s]} Qs &middot; ${acc === null ? "new" : acc + "% mastery"}</span>
        ${bar}
      </button>`;
  }).join("");
  const dateLine = new Date().toLocaleDateString("en-US", { weekday: "long", month: "long", day: "numeric" });
  state.screenBack = null;
  app.innerHTML = `
    <div class="hero">
      <div class="hero-row">${goalRing()}<div>
        <div class="eyebrow date-eyebrow">${esc(dateLine)}</div>
        <h1>Today's 5-minute blitz</h1><p>${streakLine}</p></div></div>
    </div>
    <div class="topic-card">
      <div class="eyebrow">Suggested for today</div>
      <h2>${esc(label(section))}</h2>
      <p class="msg">${esc(coachMsg || `${QUESTIONS_PER_BLITZ} questions pulled from your ${label(section)} notes.`)}</p>
      <button class="cta" id="startBtn">Start &mdash; ${QUESTIONS_PER_BLITZ} questions<small>~5 min &middot; ${deckMode() === "flash" ? "flashcards" : "multiple choice"}</small></button>
    </div>
    ${reviewCard}
    ${weakCard}
    ${rustyNote}
    <h2 class="section-h">Or pick a section &mdash; then choose sub-topics</h2>
    <div class="grid">${tiles}</div>`;
  el("#startBtn").addEventListener("click", () => startBlitz(section));
  if (due.length) el("#reviewBtn").addEventListener("click", startReview);
  if (worst) el("#weakBtn").addEventListener("click", startWeakSpots);
  if (rusty) el("#rustyBtn").addEventListener("click", () => startBlitz(rusty.s));
  document.querySelectorAll(".tile").forEach((b) =>
    b.addEventListener("click", () => openTopics(b.dataset.section)));
  wireReveals();
}

/* ---------- bank loading / sub-topic picker ---------- */
const bankCache = {};
async function loadBank(section) {
  // "default" lets the server's no-cache header drive revalidation (304s) —
  // these files are multi-MB; no-store would re-download them on every boot.
  if (!bankCache[section]) bankCache[section] = await apiGet(`questions/${section}.json`, null, "default");
  return bankCache[section];
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
  state.screenBack = renderHome;
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
  el("#backBtn").addEventListener("click", () => vt(renderHome));
  el("#startSel").addEventListener("click", () => startBlitz(section, selected()));
  updateCount();
  wireReveals();
}

/* ---------- deck building ---------- */
function makeItem(q) {
  const opts = shuffle([{ t: q.correct, ok: true }, ...q.distractors.map((d) => ({ t: d, ok: false }))]);
  return { q, opts, status: "pending", boss: false };
}

// Quiz vs flashcard is a global, persisted preference toggled from the top bar.
function deckMode() { return localStorage.getItem("sd_mode") === "flash" ? "flash" : "quiz"; }

function startDeck(questions, replayFn) {
  state.mode = deckMode();
  const items = questions.map(makeItem);
  if (state.mode === "flash") {
    state.deck = shuffle(items);                 // no boss ordering for self-grade cards
  } else {
    // boss round: advanced-difficulty questions go last and are worth 2x
    const normal = items.filter((it) => it.q.difficulty !== "advanced");
    const boss = items.filter((it) => it.q.difficulty === "advanced");
    boss.forEach((it) => (it.boss = true));
    state.deck = [...normal, ...boss];
  }
  state.queue = state.deck.map((_, i) => i);
  state.cursor = 0;
  state.combo = 0; state.maxCombo = 0; state.sessionXp = 0;
  state.replayFn = replayFn;
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
  startDeck(items.slice(0, QUESTIONS_PER_BLITZ), startReview);
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
  startDeck(items.slice(0, QUESTIONS_PER_BLITZ), startWeakSpots);
}

/* ---------- quiz ---------- */
function isLastInQueue() { return state.cursor >= state.queue.length - 1; }

function comboMult() { return state.combo >= 5 ? 3 : state.combo >= 3 ? 2 : 1; }

function renderQuestion() {
  const idx = state.queue[state.cursor];
  const item = state.deck[idx];
  const { q, opts } = item;
  const teach = item.status === "skipped";
  state.inQuiz = true; state.answered = false; state.curOptsLen = opts.length;
  state.screenBack = null;                         // mouse-back never aborts a live deck
  const DONE = ["correct", "wrong", "learned"];
  const dots = state.deck.map((it, i) =>
    `<span class="dot ${DONE.includes(it.status) ? "done" : ""} ${it.boss ? "boss" : ""} ${i === idx ? "cur" : ""}"></span>`).join("");
  const bossBanner = item.boss && !teach
    ? `<div class="boss-banner">&#9889; BOSS QUESTION &middot; 2&times; XP</div>` : "";
  const teachBlock = teach
    ? `<div class="teach-banner">Concept review &middot; you skipped this earlier. Learn it, then lock it in.</div>
       <div class="reveal concept show"><b>Concept:</b> ${esc(q.answerFull)}</div>` : "";
  // Show what the NEXT correct answer pays (gain is computed after combo+1).
  const nextMult = state.combo + 1 >= 5 ? 3 : state.combo + 1 >= 3 ? 2 : 1;
  const comboChip = state.combo >= 2 ? `<span class="combo">${ICON("flame", "i-flame")} ${state.combo} combo &middot; ${nextMult}&times; XP</span>` : "";
  app.innerHTML = `
    <div class="qhead">
      <span class="module">${esc(label(q.section))} &middot; ${esc(q.moduleName)} <span class="diff d-${esc(q.difficulty)}">${esc(q.difficulty)}</span></span>
      <span class="qright"><span class="dots" role="img" aria-label="Question ${state.cursor + 1} of ${state.queue.length}">${dots}</span><span class="qnum">${state.cursor + 1}/${state.queue.length}</span></span>
    </div>
    ${bossBanner}${teachBlock}
    <div class="qtext">${esc(q.question)} ${comboChip}</div>
    <div class="options">
      ${opts.map((o, i) => `<button class="opt" data-i="${i}"><kbd>${i + 1}</kbd>${esc(o.t)}<span class="mark"></span></button>`).join("")}
    </div>
    <div class="reveal" id="reveal"></div>
    <div class="qactions">
      ${item.status === "pending" ? `<button class="skip" id="skipBtn">Skip for now (S) &rarr;</button>` : "<span></span>"}
      <button class="next" id="nextBtn">${isLastInQueue() ? "Finish" : "Next (↵)"}</button>
    </div>`;
  document.querySelectorAll(".opt").forEach((b) =>
    b.addEventListener("click", () => answer(parseInt(b.dataset.i, 10))));
  if (item.status === "pending") el("#skipBtn").addEventListener("click", skipQuestion);
  el("#nextBtn").addEventListener("click", nextQuestion);
  app.focus({ preventScroll: true });              // keep keyboard + SR context on the new question
}

function answer(i) {
  if (state.answered) return;
  state.answered = true;
  const item = state.deck[state.queue[state.cursor]];
  const { q, opts } = item;
  const teach = item.status === "skipped";
  const optBtns = document.querySelectorAll(".opt");
  optBtns.forEach((b, k) => {
    b.disabled = true;
    if (opts[k].ok) { b.classList.add("correct"); b.querySelector(".mark").textContent = "✓"; }
    if (k === i && !opts[k].ok) { b.classList.add("wrong"); b.querySelector(".mark").textContent = "✗"; }
  });
  const right = opts[i].ok;
  announce(teach
    ? "Locked in."
    : right ? "Correct." : `Incorrect. The answer is: ${opts.find((o) => o.ok).t}`);
  if (teach) {
    item.status = "learned";
  } else if (right) {
    item.status = "correct";
    state.combo += 1; state.maxCombo = Math.max(state.maxCombo, state.combo);
    const gain = 10 * comboMult() * (item.boss ? 2 : 1);
    state.sessionXp += gain;
    floatXP(gain, optBtns[i]);
    if (state.combo === 3 || state.combo === 5 || state.combo >= 7) { sfx.combo(); ripple(optBtns[i]); }
    else sfx.correct();
  } else {
    item.status = "wrong";
    state.combo = 0;
    sfx.wrong();
  }
  const sk = el("#skipBtn"); if (sk) sk.remove();
  if (!teach) {
    const rev = el("#reveal");
    rev.innerHTML = `<b>Full answer:</b> ${esc(q.answerFull)}
      <button class="deeper" id="deeperBtn">Dive deeper into ${esc(q.moduleName)} &rarr;</button>`;
    rev.classList.add("show");
    el("#deeperBtn").addEventListener("click", () => openReader(q.module, q.moduleName));
  }
  el("#nextBtn").classList.add("show");
}

function skipQuestion() {
  const idx = state.queue[state.cursor];
  if (state.deck[idx].status !== "pending") return;  // double-click guard
  state.deck[idx].status = "skipped";
  state.queue.push(idx); // returns at the end in teach mode
  nextQuestion();
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
  state.screenBack = null;
  const DONE = ["correct", "wrong", "learned"];
  const dots = state.deck.map((it, i) =>
    `<span class="dot ${DONE.includes(it.status) ? "done" : ""} ${i === idx ? "cur" : ""}"></span>`).join("");
  app.innerHTML = `
    <div class="qhead">
      <span class="module">${esc(label(q.section))} &middot; ${esc(q.moduleName)}</span>
      <span class="qright"><span class="dots" role="img" aria-label="Card ${state.cursor + 1} of ${state.queue.length}">${dots}</span><span class="qnum">${state.cursor + 1}/${state.queue.length}</span></span>
    </div>
    <div class="flash-label">Flashcard &middot; recall it, then grade yourself</div>
    <div class="qtext">${esc(q.question)}</div>
    <div class="reveal" id="reveal"></div>
    <div class="qactions" id="cardActions">
      <span></span>
      <button class="next show" id="revealBtn">Reveal answer (Space)</button>
    </div>`;
  el("#revealBtn").addEventListener("click", revealCard);
  app.focus({ preventScroll: true });
}

function revealCard() {
  if (state.answered) return;
  state.answered = true;
  const { q } = state.deck[state.queue[state.cursor]];
  const rev = el("#reveal");
  rev.innerHTML = `<b>Answer:</b> ${esc(q.answerFull)}
    <button class="deeper" id="deeperBtn">Dive deeper into ${esc(q.moduleName)} &rarr;</button>`;
  rev.classList.add("show");
  announce(`Answer: ${q.answerFull}`);
  el("#deeperBtn").addEventListener("click", () => openReader(q.module, q.moduleName));
  el("#cardActions").innerHTML = `
    <button class="grade miss" id="missBtn"><kbd>1</kbd> Missed it</button>
    <button class="grade got" id="gotBtn"><kbd>2</kbd> Got it</button>`;
  el("#missBtn").addEventListener("click", () => gradeCard(false));
  el("#gotBtn").addEventListener("click", () => gradeCard(true));
}

// Self-grade feeds the SAME results pipeline as the MCQ blitz, so it drives the
// existing SM-2 schedule. XP is flat (no combo/boss) so self-grading can't inflate
// score versus the verifiable multiple-choice path.
function gradeCard(got) {
  if (!state.answered) return;
  const item = state.deck[state.queue[state.cursor]];
  if (got) { item.status = "correct"; state.sessionXp += 10; sfx.correct(); floatXP(10, el("#gotBtn")); }
  else { item.status = "wrong"; sfx.wrong(); }
  state.cursor++;
  if (state.cursor < state.queue.length) renderCard();
  else finish();
}

async function finish() {
  state.inQuiz = false;
  app.innerHTML = `<div class="loading">Saving your progress&hellip;</div>`;
  const total = state.deck.length;
  const correct = state.deck.filter((d) => d.status === "correct").length;
  const learned = state.deck.filter((d) => d.status === "learned").length;
  const bonusXp = Math.max(0, state.sessionXp - correct * 10);
  const results = state.deck.map((d) => ({ id: d.q.id, section: d.q.section, module: d.q.module, status: d.status }));
  const { xp, freezeUsed } = await saveSession({ date: todayISO(), section: state.section, results, bonusXp });
  refreshStats();
  const pct = Math.round((correct / total) * 100);
  const flawless = pct === 100 && total > 0;
  if (flawless) { confetti(); sfx.finish(); }
  const cheer = flawless ? "Flawless! " : pct >= 70 ? "Strong work. " : pct >= 40 ? "Good progress. " : "Every rep counts. ";
  announce(`Blitz finished. ${correct} of ${total} correct. ${xp} XP earned.`);
  const freezeNote = freezeUsed
    ? `<div class="freeze-saved">${ICON("snow", "i-snow")} Streak saved &mdash; 1 freeze used (${state.progress.freezes || 0} left)</div>` : "";
  const extraBadges =
    (learned ? `<div class="badge"><div class="n">${learned}</div><div class="l">Learned</div></div>` : "") +
    (state.maxCombo >= 2 ? `<div class="badge"><div class="n">${state.maxCombo}&times;</div><div class="l">Best combo</div></div>` : "");
  // Post-round review: every miss (and teach-mode learn) with its correct answer.
  const misses = state.deck.filter((d) => d.status === "wrong" || d.status === "learned");
  const missList = misses.length ? `
    <div class="miss-wrap">
      <h2 class="section-h">Review this round</h2>
      ${misses.map((m, k) => `<div class="miss-item ${m.status}">
        <div class="miss-q">${esc(m.q.question)}</div>
        <div class="miss-a">${esc(m.q.correct)}</div>
        <button class="deeper miss-deeper" data-k="${k}">Dive deeper into ${esc(m.q.moduleName)} &rarr;</button>
      </div>`).join("")}
    </div>` : "";
  const R = 56, CIRC = +(2 * Math.PI * R).toFixed(1);
  state.screenBack = renderHome;
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
      ${freezeNote}
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
  el("#againBtn").addEventListener("click", () => (state.replayFn ? state.replayFn() : renderHome()));
  el("#homeBtn").addEventListener("click", () => vt(renderHome));
  el("#progBtn").addEventListener("click", () => vt(renderProgress));
  document.querySelectorAll(".miss-deeper").forEach((b) =>
    b.addEventListener("click", () => { const m = misses[+b.dataset.k]; openReader(m.q.module, m.q.moduleName); }));
  wireReveals();
  app.focus({ preventScroll: true });
}

/* ---------- progress ---------- */
// GitHub-style contribution grid from the (already persisted) history array.
// Columns are weeks (start aligns to a Sunday); cells are coloured by XP bucket.
function heatmapHTML(history) {
  const xpByDay = new Map();
  for (const h of history || []) xpByDay.set(h.date, (xpByDay.get(h.date) || 0) + (h.xp || 0));
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
    cells += `<span class="hmcell hm-l${lvl}" style="animation-delay:${i * 3}ms" title="${iso}: ${xp} XP"></span>`;
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
  state.screenBack = renderHome;
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
    <h2 class="section-h">Mastery by section</h2>
    ${tiles}
    <div class="row" style="margin-top:18px"><button class="primary" id="backHome">Back to today</button></div>`;
  el("#backHome").addEventListener("click", () => vt(renderHome));
  wireReveals();
}

/* ---------- study mode (pure reading) ---------- */
function renderStudy() {
  state.inQuiz = false;
  state.screenBack = renderHome;
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
  document.querySelectorAll(".tile").forEach((b) => b.addEventListener("click", () => openStudySection(b.dataset.section)));
  if (contCard) el("#contBtn").addEventListener("click", () => { reader.back = []; openReaderPath(lastRead.path, lastRead.title, null); });
  el("#studyHome").addEventListener("click", () => vt(renderHome));
  wireReveals();
}

/* ---------- learning path (Study section graph) ---------- */
// Serpentine skill-tree replacing the flat Study topic list. The order IS
// modulesOf() (STUDY_ORDER + appended unlisted modules) — nothing is invented.
// Glass node chips are absolutely-positioned buttons over a single SVG spine of
// cubic beziers; vertical scroll is the navigation (no pan/zoom machinery).
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
  const graph = await apiGet(`graph/${section}.json`, null, "default");
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
  state.screenBack = renderStudy;
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
    <div class="hero"><h1>${esc(label(section))}</h1>
      <p>${mods.length} topics &middot; start at 01 &mdash; the path follows the section's learning order.</p>
      ${graph ? `<p class="path-legend">${crossLinks
        ? `thick edge = strong prerequisite link &middot; hover a topic to see its connections &middot; ${crossLinks} cross-links mapped`
        : "no cross-link data yet &mdash; path order shown"}</p>` : ""}</div>
    <div class="topicbar"><input type="search" class="filter" id="studyFilter" placeholder="Filter topics" aria-label="Filter topics" /></div>
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
    <div class="row" style="margin-top:18px"><button class="ghost" id="studyBack">&larr; Sections</button></div>`;

  const wrap = el("#pathWrap"), svg = el("#pathSvg");
  const stepEls = [...wrap.querySelectorAll(".pathstep")];
  // One <path> per chord, created once; layoutPath() only rewrites the d attr.
  // Weight -> stroke width via a --sw custom prop so CSS can thicken on highlight.
  const chordG = el("#lpChords");
  chords.forEach((c) => {
    c.el = document.createElementNS("http://www.w3.org/2000/svg", "path");
    c.el.setAttribute("class", "lp-chord" + (c.lex ? " lex" : ""));
    c.el.style.setProperty("--sw", (1 + 4 * Math.min(1, c.w)).toFixed(2) + "px");
    chordG.appendChild(c.el);
  });

  // Measure-and-place: two columns on wide screens, one centered column below.
  // Positions in px; container height set to fit; SVG underlay redrawn to match.
  function layoutPath() {
    const W = wrap.clientWidth;
    if (!W || !stepEls.length) return;
    const two = W >= 700;
    // Chord lane: outer margins reserved beside the columns so prerequisite
    // arcs never pass under chip text (zero when the section has no edges).
    const lane = two && chords.length ? Math.min(64, Math.round(W * 0.08)) : 0;
    const nodeW = two ? Math.min(330, Math.round((W - 2 * lane) * 0.46)) : Math.min(440, W);
    const gap = two ? 52 : 40;
    let y = 6;
    const pts = [];
    stepEls.forEach((s, i) => {
      s.style.width = nodeW + "px";
      const left = two ? (i % 2 === 0 ? lane : W - nodeW - lane) : Math.round((W - nodeW) / 2);
      s.style.left = left + "px";
      s.style.top = y + "px";
      const h = s.offsetHeight;                    // includes an open leaf fan
      pts.push({ left, top: y, w: nodeW, h, chipH: s.firstElementChild.offsetHeight, s });
      y += h + gap;
    });
    const H = y - gap + 10;
    wrap.style.height = H + "px";
    svg.setAttribute("width", W);
    svg.setAttribute("height", H);
    svg.setAttribute("viewBox", `0 0 ${W} ${H}`);
    let d = "";                                    // spine: bottom of step i -> top of chip i+1
    for (let i = 0; i < pts.length - 1; i++) {
      const a = pts[i], b = pts[i + 1];
      const x1 = a.left + a.w / 2, y1 = a.top + a.h + 1;
      const x2 = b.left + b.w / 2, y2 = b.top - 1;
      const dy = Math.max(18, (y2 - y1) * 0.55);
      d += `M ${x1} ${y1} C ${x1} ${y1 + dy}, ${x2} ${y2 - dy}, ${x2} ${y2} `;
    }
    el("#lpSpine").setAttribute("d", d.trim());
    let ld = "";                                   // leaf connectors: rail + rounded elbows
    pts.forEach((p) => {
      if (!p.s.classList.contains("open")) return;
      const railX = p.left + 20;
      p.s.querySelectorAll(".pathleaf").forEach((leaf) => {
        const lx = p.left + leaf.offsetLeft, ly = p.top + leaf.offsetTop + leaf.offsetHeight / 2;
        ld += `M ${railX} ${p.top + p.chipH - 4} L ${railX} ${ly - 9} Q ${railX} ${ly}, ${railX + 9} ${ly} L ${lx} ${ly} `;
      });
    });
    el("#lpLeaves").setAttribute("d", ld.trim());
    // Prerequisite chords: same-column pairs bracket out through their outer
    // lane; cross-column pairs take a gentle arc through the chip-free middle
    // gap (mid-x jittered so stacked centre arcs don't collapse into one line).
    chords.forEach((c) => {
      const a = pts[c.from], b = pts[c.to];
      const ay = a.top + a.chipH / 2, by = b.top + b.chipH / 2;
      if (two && a.left !== b.left) {
        const ax = a.left < b.left ? a.left + a.w : a.left;
        const bx = a.left < b.left ? b.left : b.left + b.w;
        const spread = Math.max(6, (W - 2 * lane - 2 * nodeW) / 2 - 10);
        const mx = W / 2 + ((c.from + c.to) % 7 - 3) / 3 * spread;
        c.el.setAttribute("d", `M ${ax} ${ay} C ${mx} ${ay}, ${mx} ${by}, ${bx} ${by}`);
      } else {
        const leftSide = two ? a.left < W / 2 : c.from % 2 === 0;
        const ax = leftSide ? a.left : a.left + a.w;
        const bx = leftSide ? b.left : b.left + b.w;
        const room = leftSide ? Math.min(ax, bx) : W - Math.max(ax, bx);
        const bow = Math.max(10, Math.min(room - 6, 18 + (by - ay) * 0.05)) * (leftSide ? -1 : 1);
        c.el.setAttribute("d", `M ${ax} ${ay} C ${ax + bow} ${ay}, ${bx + bow} ${by}, ${bx} ${by}`);
      }
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
  // Filter dims non-matching nodes (path shape stays intact).
  el("#studyFilter").addEventListener("input", () => {
    const f = el("#studyFilter").value.trim().toLowerCase();
    stepEls.forEach((s, i) => s.classList.toggle("dim", !!f && !mods[i].name.toLowerCase().includes(f)));
  });
  el("#studyBack").addEventListener("click", () => vt(renderStudy));
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
function mmLayout(n, sv, w) {
  mmApplyWidth(sv, w);
  const spill = Math.min(Math.max(0, w - mmAvail(n)), mmExtra(n));
  n.style.marginLeft = n.style.marginRight = spill > 8 ? `${-spill / 2}px` : "";
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
  let svg, flipScale = 0;
  try {
    svg = (await mermaid.render("mm" + (++_mmSeq), src)).svg;
    const alt = mmAltOrientation(src);
    const d0 = mmDims(svg);
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
  const d = mmDims(svg);
  if (sv && d) {
    sv.dataset.natw = Math.round(d.w);
    if (flipScale) {
      sv.dataset.minw = Math.round(d.w * 0.7);           // readability floor for re-clamps
      mmLayout(n, sv, Math.round(d.w * flipScale));      // may exceed the column -> gutters, then h-scroll
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
  if (!n.dataset.mmWired) {                              // once per container, not per render
    n.dataset.mmWired = "1";
    n.addEventListener("click", () => openMermaidZoom(n));
  }
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
            },
            flowchart: { curve: "basis", padding: 20, nodeSpacing: 45, rankSpacing: 55 },
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
      const r = await fetch(IS_STATIC ? `../${path}` : `/content/${path}`, { cache: "no-store" });
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
    const b = el("#readerBody"); if (b) b.innerHTML = `<div class="error">Couldn't load this page &mdash; is <code>server.py</code> running?</div>`;
  }
}

// Entry point from a quiz/flashcard reveal: a module README, fresh history, no prev/next.
function openReader(module, moduleName) {
  reader.back = [];
  return openReaderPath(`${module}/README.md`, moduleName, null);
}

function closeReader() {
  document.body.classList.remove("reader-open", "reader-full");
  const p = el("#reader"); if (p) p.remove();
  reader.path = null; reader.back = []; reader.nav = null;
}

/* ---------- keyboard ---------- */
document.addEventListener("keydown", (e) => {
  const typing = (e.target.tagName || "").toLowerCase() === "input";
  if (e.key === "Escape" && el("#helpOverlay")) { el("#helpOverlay").remove(); return; }
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
    } else if (e.key === "1") { e.preventDefault(); gradeCard(false); }
    else if (e.key === "2" || e.key === "Enter") { e.preventDefault(); gradeCard(true); }
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
    const item = state.deck[state.queue[state.cursor]];
    if (item && item.status === "pending") { e.preventDefault(); skipQuestion(); }
  }
});

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
  state.index = await apiGet("questions/index.json", null);
  if (!state.index) {
    app.innerHTML = `<div class="error">No question bank found. Run <code>python3 extract.py</code> then reload.</div>`;
    return;
  }
  el("#bankInfo").textContent = `${state.index.total} questions across ${Object.keys(state.index.sections).length} sections`;
  state.progress = await loadProgress();
  state.today = IS_STATIC ? {} : await apiGet("/api/today", {});
  el("#navProgress").addEventListener("click", () => vt(renderProgress));
  const studyB = el("#navStudy");
  if (studyB) studyB.addEventListener("click", () => vt(renderStudy));
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
  renderHome();
}

boot();
