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
const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
const label = (s) => SECTION_LABELS[s] || s;

function shuffle(a) {
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

async function apiGet(path, fallback) {
  try { const r = await fetch(path, { cache: "no-store" }); if (!r.ok) throw 0; return await r.json(); }
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
  const colors = ["#6ea8fe", "#8b7bff", "#34d399", "#fbbf24", "#f87171"];
  const c = document.createElement("div");
  c.className = "confetti";
  for (let i = 0; i < 90; i++) {
    const p = document.createElement("i");
    p.style.left = Math.random() * 100 + "vw";
    p.style.background = colors[i % colors.length];
    p.style.animationDelay = (Math.random() * 0.4).toFixed(2) + "s";
    p.style.setProperty("--rot", (Math.random() * 720 - 360) + "deg");
    c.appendChild(p);
  }
  document.body.appendChild(c);
  setTimeout(() => c.remove(), 2400);
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
    const p = await apiGet("/api/progress", null);
    if (p) return fill(p);
  }
  const ls = localStorage.getItem("sd_progress");
  return ls ? fill(JSON.parse(ls))
    : { streak: 0, longestStreak: 0, lastPlayed: null, totalXP: 0, sections: {}, history: [], reviews: {}, freezes: 2, freezeUsedOn: [] };
}

async function saveSession(session) {
  if (IS_STATIC) return saveSessionLocal(session);
  try {
    const r = await fetch("/api/progress", {
      method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(session),
    });
    if (!r.ok) throw 0;
    const data = await r.json();
    state.progress = data.progress;
    return { xp: data.xpEarned, freezeUsed: !!data.freezeUsed };
  } catch {
    return saveSessionLocal(session);
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
  (p.history = p.history || []).push({ date: session.date, correct, xp });
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
  el("#streakVal").textContent = state.progress.streak || 0;
  el("#xpVal").textContent = state.progress.totalXP || 0;
  const lv = el("#lvlVal");
  if (lv) lv.textContent = levelFromXP(state.progress.totalXP);
}

/* ---------- home ---------- */
function goalRing() {
  const xp = todaysXp(), pct = Math.min(1, xp / DAILY_XP_GOAL);
  const r = 26, circ = 2 * Math.PI * r, off = circ * (1 - pct);
  const done = xp >= DAILY_XP_GOAL;
  return `<div class="goal" title="Daily goal">
      <svg width="64" height="64" viewBox="0 0 64 64">
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
    ? ` <span class="freeze-chip" title="Streak freezes auto-cover a single missed day">&#10052;&#65039; ${freezes}</span>` : "";
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
    ? `<button class="rusty-note" id="rustyBtn">&#9203; <b>${esc(label(rusty.s))}</b> is getting rusty &mdash; ${rusty.days} days since you practiced it. Refresh it &rarr;</button>`
    : "";
  const secs = state.index.sections, p = state.progress;
  const tiles = Object.keys(secs).sort().map((s) => {
    const st = (p.sections && p.sections[s]) || { seen: 0, correct: 0 };
    const acc = st.seen ? Math.round((st.correct / st.seen) * 100) : null;
    return `<button class="tile ${s === section ? "suggested" : ""}" data-section="${s}">
        <span class="tname">${esc(label(s))}</span>
        <span class="tmeta">${secs[s]} Qs &middot; ${acc === null ? "new" : acc + "% mastery"}</span>
      </button>`;
  }).join("");
  app.innerHTML = `
    <div class="hero">
      <div class="hero-row">${goalRing()}<div><h1>Today's 5-minute blitz</h1><p>${streakLine}</p></div></div>
    </div>
    ${reviewCard}
    ${weakCard}
    ${rustyNote}
    <div class="topic-card">
      <div class="eyebrow">Suggested for today</div>
      <h2>${esc(label(section))}</h2>
      <p class="msg">${esc(coachMsg || `${QUESTIONS_PER_BLITZ} questions pulled from your ${label(section)} notes.`)}</p>
      <button class="cta" id="startBtn">Start &mdash; ${QUESTIONS_PER_BLITZ} questions<small>~5 min &middot; ${deckMode() === "flash" ? "flashcards" : "multiple choice"}</small></button>
    </div>
    <div class="section-h">Or pick a section &mdash; then choose sub-topics</div>
    <div class="grid">${tiles}</div>`;
  el("#startBtn").addEventListener("click", () => startBlitz(section));
  if (due.length) el("#reviewBtn").addEventListener("click", startReview);
  if (worst) el("#weakBtn").addEventListener("click", startWeakSpots);
  if (rusty) el("#rustyBtn").addEventListener("click", () => startBlitz(rusty.s));
  document.querySelectorAll(".tile").forEach((b) =>
    b.addEventListener("click", () => openTopics(b.dataset.section)));
}

/* ---------- bank loading / sub-topic picker ---------- */
const bankCache = {};
async function loadBank(section) {
  if (!bankCache[section]) bankCache[section] = await apiGet(`questions/${section}.json`, null);
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
  const rows = mods.map((m) =>
    `<label class="modrow"><input type="checkbox" class="modcheck" value="${esc(m.module)}" checked />
       <span class="mname">${esc(m.name)}</span><span class="mcount">${m.count}</span></label>`).join("");
  app.innerHTML = `
    <div class="hero"><h1>${esc(label(section))}</h1><p>Pick the sub-topics to drill &mdash; or keep them all.</p></div>
    <div class="topicbar">
      <button class="ghost" id="allBtn">Select all</button>
      <button class="ghost" id="noneBtn">Clear</button>
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
  el("#backBtn").addEventListener("click", renderHome);
  el("#startSel").addEventListener("click", () => startBlitz(section, selected()));
  updateCount();
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
    for (const id of bySec[sec]) { const q = byId.get(id); if (q) items.push(q); }
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
  const DONE = ["correct", "wrong", "learned"];
  const dots = state.deck.map((it, i) =>
    `<span class="dot ${DONE.includes(it.status) ? "done" : ""} ${it.boss ? "boss" : ""} ${i === idx ? "cur" : ""}"></span>`).join("");
  const bossBanner = item.boss && !teach
    ? `<div class="boss-banner">&#9889; BOSS QUESTION &middot; 2&times; XP</div>` : "";
  const teachBlock = teach
    ? `<div class="teach-banner">Concept review &middot; you skipped this earlier. Learn it, then lock it in.</div>
       <div class="reveal concept show"><b>Concept:</b> ${esc(q.answerFull)}</div>` : "";
  const comboChip = state.combo >= 2 ? `<span class="combo">&#128293; ${state.combo} combo &middot; ${comboMult()}&times;</span>` : "";
  app.innerHTML = `
    <div class="qhead">
      <span class="module">${esc(label(q.section))} &middot; ${esc(q.moduleName)}</span>
      <span class="dots">${dots}</span>
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
}

function answer(i) {
  if (state.answered) return;
  state.answered = true;
  const item = state.deck[state.queue[state.cursor]];
  const { q, opts } = item;
  const teach = item.status === "skipped";
  document.querySelectorAll(".opt").forEach((b, k) => {
    b.disabled = true;
    if (opts[k].ok) { b.classList.add("correct"); b.querySelector(".mark").textContent = "✓"; }
    if (k === i && !opts[k].ok) { b.classList.add("wrong"); b.querySelector(".mark").textContent = "✗"; }
  });
  const right = opts[i].ok;
  if (teach) {
    item.status = "learned";
  } else if (right) {
    item.status = "correct";
    state.combo += 1; state.maxCombo = Math.max(state.maxCombo, state.combo);
    const gain = 10 * comboMult() * (item.boss ? 2 : 1);
    state.sessionXp += gain;
    if (state.combo === 3 || state.combo === 5 || state.combo >= 7) sfx.combo(); else sfx.correct();
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
  const DONE = ["correct", "wrong", "learned"];
  const dots = state.deck.map((it, i) =>
    `<span class="dot ${DONE.includes(it.status) ? "done" : ""} ${i === idx ? "cur" : ""}"></span>`).join("");
  app.innerHTML = `
    <div class="qhead">
      <span class="module">${esc(label(q.section))} &middot; ${esc(q.moduleName)}</span>
      <span class="dots">${dots}</span>
    </div>
    <div class="flash-label">Flashcard &middot; recall it, then grade yourself</div>
    <div class="qtext">${esc(q.question)}</div>
    <div class="reveal" id="reveal"></div>
    <div class="qactions" id="cardActions">
      <span></span>
      <button class="next show" id="revealBtn">Reveal answer (Space)</button>
    </div>`;
  el("#revealBtn").addEventListener("click", revealCard);
}

function revealCard() {
  if (state.answered) return;
  state.answered = true;
  const { q } = state.deck[state.queue[state.cursor]];
  const rev = el("#reveal");
  rev.innerHTML = `<b>Answer:</b> ${esc(q.answerFull)}
    <button class="deeper" id="deeperBtn">Dive deeper into ${esc(q.moduleName)} &rarr;</button>`;
  rev.classList.add("show");
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
  if (got) { item.status = "correct"; state.sessionXp += 10; sfx.correct(); }
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
  const freezeNote = freezeUsed
    ? `<div class="freeze-saved">&#10052;&#65039; Streak saved &mdash; 1 freeze used (${state.progress.freezes || 0} left)</div>` : "";
  const extraBadges =
    (learned ? `<div class="badge"><div class="n">${learned}</div><div class="l">Learned</div></div>` : "") +
    (state.maxCombo >= 2 ? `<div class="badge"><div class="n">${state.maxCombo}&times;</div><div class="l">Best combo</div></div>` : "");
  app.innerHTML = `
    <div class="result">
      <div class="scorering">${correct}<small>/${total}</small></div>
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
    </div>`;
  countUp(el("#xpCount"), xp);
  el("#againBtn").addEventListener("click", () => (state.replayFn ? state.replayFn() : renderHome()));
  el("#homeBtn").addEventListener("click", renderHome);
  el("#progBtn").addEventListener("click", renderProgress);
}

/* ---------- progress ---------- */
// GitHub-style contribution grid from the (already persisted) history array.
// Columns are weeks (start aligns to a Sunday); cells are coloured by XP bucket.
function heatmapHTML(history) {
  const xpByDay = new Map();
  for (const h of history || []) xpByDay.set(h.date, (xpByDay.get(h.date) || 0) + (h.xp || 0));
  const WEEKS = 17, dayMs = 86400000;
  const today = new Date(todayISO() + "T00:00:00");
  const end = new Date(today); end.setDate(end.getDate() + (6 - today.getDay())); // Sat of this week
  const start = new Date(end.getTime() - (WEEKS * 7 - 1) * dayMs);                 // a Sunday
  let cells = "";
  for (let i = 0; i < WEEKS * 7; i++) {
    const d = new Date(start.getTime() + i * dayMs);
    const iso = d.toLocaleDateString("en-CA");
    const xp = xpByDay.get(iso) || 0;
    if (d > today) { cells += `<span class="hmcell hm-future"></span>`; continue; }
    const lvl = xp === 0 ? 0 : xp < 30 ? 1 : xp < 70 ? 2 : xp < 120 ? 3 : 4;
    cells += `<span class="hmcell hm-l${lvl}" title="${iso}: ${xp} XP"></span>`;
  }
  return `<div class="section-h">Activity</div>
    <div class="heatmap">${cells}</div>
    <div class="hmlegend">Less
      <span class="hmcell hm-l0"></span><span class="hmcell hm-l1"></span><span class="hmcell hm-l2"></span><span class="hmcell hm-l3"></span><span class="hmcell hm-l4"></span>
      More</div>`;
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
      <div class="badge"><div class="n">&#10052;&#65039; ${p.freezes || 0}</div><div class="l">Freezes</div></div>
      <div class="badge"><div class="n">${p.totalXP || 0}</div><div class="l">Total XP</div></div>
      <div class="badge"><div class="n">${due}</div><div class="l">Due review</div></div>
    </div>
    ${heatmapHTML(p.history)}
    <div class="section-h">Mastery by section</div>
    ${tiles}
    <div class="row" style="margin-top:18px"><button class="primary" id="backHome">Back to today</button></div>`;
  el("#backHome").addEventListener("click", renderHome);
}

/* ---------- study mode (pure reading) ---------- */
function renderStudy() {
  state.inQuiz = false;
  refreshStats();
  const secs = state.index.sections;
  const tiles = Object.keys(secs).sort().map((s) =>
    `<button class="tile" data-section="${s}">
       <span class="tname">${esc(label(s))}</span>
       <span class="tmeta">${secs[s]} Qs &middot; read the notes</span>
     </button>`).join("");
  app.innerHTML = `
    <div class="hero"><h1>Study</h1><p>Read your notes in a focused reader &mdash; no quiz, no clock.</p></div>
    <div class="section-h">Pick a section to browse its topics</div>
    <div class="grid">${tiles}</div>
    <div class="row" style="margin-top:18px"><button class="ghost" id="studyHome">&larr; Home</button></div>`;
  document.querySelectorAll(".tile").forEach((b) => b.addEventListener("click", () => openStudySection(b.dataset.section)));
  el("#studyHome").addEventListener("click", renderHome);
}

async function openStudySection(section) {
  app.innerHTML = `<div class="loading">Loading ${esc(label(section))}&hellip;</div>`;
  const bank = await loadBank(section);
  if (!bank || !bank.length) {
    app.innerHTML = `<div class="error">Couldn't load ${esc(section)}. Run <code>python3 extract.py</code>.</div>`;
    return;
  }
  const mods = modulesOf(bank);
  const list = mods.map((m) => ({ path: `${m.module}/README.md`, title: m.name }));
  const rows = mods.map((m, idx) =>
    `<button class="studyrow" data-idx="${idx}"><span class="mname">${esc(m.name)}</span><span class="mcount">${m.count} Qs</span></button>`).join("");
  app.innerHTML = `
    <div class="hero"><h1>${esc(label(section))}</h1><p>${mods.length} topics &mdash; open one to read it. Prev/Next walks the list.</p></div>
    <div class="modlist">${rows}</div>
    <div class="row" style="margin-top:18px"><button class="ghost" id="studyBack">&larr; Sections</button></div>`;
  document.querySelectorAll(".studyrow").forEach((b) => b.addEventListener("click", () => {
    const idx = +b.dataset.idx;
    reader.back = [];                              // a fresh reading session
    openReaderPath(list[idx].path, list[idx].title, { list, idx });
  }));
  el("#studyBack").addEventListener("click", renderStudy);
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
async function renderMermaid(root) {
  const nodes = [...root.querySelectorAll(".mermaid")];
  if (!nodes.length) return;                       // no mermaid on this page — skip
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
              nodeBorder:          "#4b5263",
              lineColor:           "#61afef",   // bright blue edges — visible on black
              textColor:           "#abb2bf",
              edgeLabelBackground: "#000000",
              clusterBkg:          "#0d0d0d",   // subgraph fill (very dark)
              clusterBorder:       "#3b4048",
              titleColor:          "#e5c07b",   // subgraph titles gold
              labelBackground:     "#000000",
              fontFamily:          "ui-monospace, SFMono-Regular, Menlo, monospace",
            },
          });
          return m.default;
        });
    }
    const mermaid = await _mermaidReady;
    // Un-mark any nodes already rendered so mermaid re-processes them on navigation.
    nodes.forEach(n => n.removeAttribute("data-processed"));
    await mermaid.run({ nodes });
  } catch (err) {
    // CDN unavailable or offline — raw source stays visible as text, nothing crashes.
    console.warn("Mermaid render failed:", err);
  }
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
    const p = para.join(" ").trim();
    const isQ = /^\*\*[\s\S]+\*\*$/.test(p) && (p.match(/\*\*/g) || []).length === 2;
    let pcls = "";
    if (isQ) { pcls = ' class="md-q"'; qaPending = true; }       // bold question on its own line
    else if (qaPending) { pcls = ' class="md-a"'; qaPending = false; }  // its answer (blank-separated)
    else if (/^\*\*[^*]+?\*\*[^*]/.test(p)) { pcls = ' class="md-qa"'; }  // inline "**Question?** Answer." on one line
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
function attachGrip(grip) {
  if (!grip) return;
  grip.addEventListener("pointerdown", (e) => {
    e.preventDefault();
    grip.setPointerCapture(e.pointerId);
    document.body.classList.add("reader-resizing");
    const onMove = (ev) => {
      const w = Math.min(window.innerWidth * 0.92, Math.max(360, window.innerWidth - ev.clientX));
      document.documentElement.style.setProperty("--reader-w", Math.round(w) + "px");
    };
    const onUp = () => {
      document.body.classList.remove("reader-resizing");
      grip.removeEventListener("pointermove", onMove);
      grip.removeEventListener("pointerup", onUp);
      const w = getComputedStyle(document.documentElement).getPropertyValue("--reader-w").trim();
      if (w.endsWith("px")) localStorage.setItem("sd_reader_w", w);
    };
    grip.addEventListener("pointermove", onMove);
    grip.addEventListener("pointerup", onUp);
  });
}

function restoreReaderWidth() {
  const w = localStorage.getItem("sd_reader_w");
  if (w) document.documentElement.style.setProperty("--reader-w", w);
  reader.full = localStorage.getItem("sd_reader_full") === "1";
  reader.toc = localStorage.getItem("sd_reader_toc") === "1";
  reader.modules = localStorage.getItem("sd_reader_modules") === "1";
}

// Populate the always-accessible sidebar index from the rendered headings (ids
// assigned by mdRender, so anchors always match). Returns the heading count so the
// caller can hide the Index toggle when there's nothing to index.
function buildToc(tocEl, main) {
  const heads = [...main.querySelectorAll("h2[id], h3[id]")];
  if (!heads.length) { tocEl.innerHTML = ""; return 0; }
  const items = heads.map((h) =>
    `<li class="${h.tagName === "H3" ? "lvl3" : ""}"><a href="#" data-tid="${esc(h.id)}">${esc(h.textContent)}</a></li>`).join("");
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
      return `<li><a href="#" class="mod-item${isActive ? " active" : ""}" data-midx="${i}">${esc(m.title)}</a></li>`;
    }

    // Multi-file module: collapsible folder
    const isOpen = readerExpanded.has(mKey);
    const subItems = mFiles.map((fn) => {
      const filePath = `${mKey}/${fn}`;
      const isFileCurrent = filePath === currentPath;
      const label = fn === "README.md" ? "readme" : fn.replace(".md", "").replace(/_/g, " ");
      return `<li><a href="#" class="mod-file${isFileCurrent ? " active" : ""}" data-path="${esc(filePath)}">${esc(label)}</a></li>`;
    }).join("");

    return `<li class="mod-group${isOpen ? " open" : ""}">
      <div class="mod-folder" data-midx="${i}"><span class="mod-arrow">&#9654;</span><span class="mod-fname">${esc(m.title)}</span></div>
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
      <button class="reader-nav reader-icon" id="readerIdx" title="Contents">&#8801;</button>
      <button class="reader-nav reader-icon" id="readerFull" title="Fullscreen (F)">&#11036;</button>
      <button class="reader-close" id="readerClose" title="Close (Esc)">&times;</button>
    </div>
    <div class="reader-body" id="readerBody"><div class="loading">Loading&hellip;</div></div>`;
  document.body.classList.add("reader-open");
  applyReaderModes();
  attachGrip(el("#readerGrip"));
  el("#readerClose").addEventListener("click", closeReader);
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
    b.innerHTML = `<nav class="reader-modules" id="readerModules"></nav><div class="md-body" id="readerMain">${mdRender(readerCache[path])}</div><nav class="reader-toc" id="readerToc"></nav>`;
    const main = el("#readerMain");
    buildModuleNav(el("#readerModules"), reader.nav, path);
    const headCount = buildToc(el("#readerToc"), main);
    el("#readerIdx").style.display = headCount >= 3 ? "" : "none";   // nothing to index -> hide toggle
    wireReaderBody(main);
    renderMermaid(main);                           // no-op when page has no mermaid fences
    b.scrollTop = 0;
    if (frag) { const t = main.querySelector("#" + CSS.escape(frag)); if (t) t.scrollIntoView({ block: "start" }); }
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
  }
  if (!state.inQuiz) return;
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
  if (b) b.textContent = sfx.isOn() ? "🔊" : "🔇";
}

function syncModeBtn() {
  const b = el("#modeBtn");
  if (!b) return;
  const flash = deckMode() === "flash";
  b.textContent = flash ? "Cards" : "Quiz";
  b.title = flash ? "Flashcards mode (click for multiple-choice)" : "Multiple-choice mode (click for flashcards)";
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
  el("#navProgress").addEventListener("click", renderProgress);
  const studyB = el("#navStudy");
  if (studyB) studyB.addEventListener("click", renderStudy);
  restoreReaderWidth();
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
