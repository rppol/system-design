/* System Design Daily - 5-minute blitz. Vanilla JS, no build step.
   Pages-only as of 2026-07-03: no server, no /api. localStorage sd_progress is
   the single source of truth for all progress. */

const QUESTIONS_PER_BLITZ = 10;
const DAILY_XP_GOAL = 100;
// True only inside the offline Android APK WebView (assets served from the
// virtual appassets host). Every APK-specific branch is feature-detected off
// this so the GitHub Pages build stays byte-for-byte on its original paths.
const IS_APK = location.hostname === "appassets.androidplatform.net";
// [E1] session length: a 5/10/20 segmented control on the topic picker,
// persisted as sd_deck_len. deckLen() replaces QUESTIONS_PER_BLITZ at every
// deck-building/copy use site EXCEPT the Gauntlet (always 10 — a fixed daily
// recipe) and the Interviewer (always 12 — core/intermediate/advanced escalation).
function deckLen() {
  const v = parseInt(localStorage.getItem("sd_deck_len"), 10);
  return v === 5 || v === 10 || v === 20 ? v : QUESTIONS_PER_BLITZ;
}
const estMinutes = (n) => Math.max(1, Math.round(n * 0.5));   // ~30s/question, matches the original 10q/~5min ratio
const SECTION_LABELS = {
  backend: "Backend Engineering", book: "Book Summaries",
  cs_fundamentals: "CS Fundamentals", cuda: "CUDA / GPGPU", database: "Databases", devops: "DevOps & Cloud",
  hld: "High-Level Design", java: "Java", lld: "Low-Level Design",
  llm: "LLM Engineering", ml: "Machine Learning", python: "Python", fastapi: "FastAPI", spring: "Spring",
  technologies: "Technologies",
};

// Per-section visual identity for the reader: a One-Dark-band accent (readable on
// the pitch-black reader surface) + a short glyph monogram. Consumed as the
// `--sec-accent` custom property set on #reader per opened path — every accent
// consumer falls back to var(--accent), so unknown sections degrade cleanly.
const SECTION_IDENTITY = {
  lld:             { accent: "#c678dd", glyph: "{ }" },
  hld:             { accent: "#61afef", glyph: "◇" },
  backend:         { accent: "#56b6c2", glyph: "≋" },
  database:        { accent: "#98c379", glyph: "▤" },
  java:            { accent: "#e06c75", glyph: "J" },
  spring:          { accent: "#7ee787", glyph: "S" },
  python:          { accent: "#e5c07b", glyph: "λ" },
  fastapi:         { accent: "#4ec9b0", glyph: "F" },
  ml:              { accent: "#d19a66", glyph: "Σ" },
  llm:             { accent: "#b180f0", glyph: "Ψ" },
  devops:          { accent: "#6cb6ff", glyph: "∞" },
  cuda:            { accent: "#9ece6a", glyph: "▦" },
  cs_fundamentals: { accent: "#f2917e", glyph: "∴" },
  book:            { accent: "#c0a36e", glyph: "¶" },
  technologies:    { accent: "#ff7eb6", glyph: "⌗" },
};
const sectionIdentity = (path) => SECTION_IDENTITY[(path || "").split("/")[0]] || null;

// The book section nests one level deeper than every other section: module ids are
// book/<book_slug>/<chapter>. These labels drive the per-book Study picker
// (#/study/book -> one node per book -> that book's own chapter graph) and the
// book group headers in the reader's module sidebar.
const BOOK_LABELS = {
  designing_data_intensive_applications: { name: "Designing Data-Intensive Applications", author: "Kleppmann", short: "DDIA" },
  system_design_interview_vol_1: { name: "System Design Interview — Vol 1", author: "Xu", short: "SDI Vol 1" },
  system_design_interview_vol_2: { name: "System Design Interview — Vol 2", author: "Xu & Lam", short: "SDI Vol 2" },
  machine_learning_system_design_interview: { name: "ML System Design Interview", author: "Aminian & Xu", short: "ML SDI" },
  designing_machine_learning_systems: { name: "Designing Machine Learning Systems", author: "Huyen", short: "DMLS" },
  understanding_distributed_systems: { name: "Understanding Distributed Systems", author: "Vitillo", short: "UDS" },
};
// book slug of a module/path id, or null when it isn't a nested book id.
const bookOf = (id) => {
  const seg = (id || "").split("/");
  return seg[0] === "book" && seg.length >= 3 ? seg[1] : null;
};
const bookLabel = (slug) => (BOOK_LABELS[slug] && BOOK_LABELS[slug].name) || titleize(slug);

// Module ids are directory slugs (cap_theorem, grpc_and_protobuf), and a slug
// knows nothing about capitalisation. Rendering them with CSS
// `text-transform: capitalize` produced "Cap Theorem", "Osi Model", "Grpc" —
// the single loudest tell that these labels were machine-made. Casing is data,
// not presentation, so it happens here and the CSS rule is gone.
// Only terms that actually appear in this repo's slugs are listed; a word that
// is not a known acronym keeps ordinary title case.
const ACRONYMS = new Map(Object.entries({
  api: "API", apis: "APIs", cdn: "CDN", cpu: "CPU", gpu: "GPU", gpus: "GPUs",
  sql: "SQL", nosql: "NoSQL", jvm: "JVM", jwt: "JWT", http: "HTTP", https: "HTTPS",
  tcp: "TCP", udp: "UDP", ip: "IP", dns: "DNS", osi: "OSI", cap: "CAP",
  cqrs: "CQRS", mvc: "MVC", aop: "AOP", jpa: "JPA", jdbc: "JDBC", ioc: "IoC",
  orm: "ORM", rest: "REST", grpc: "gRPC", json: "JSON", yaml: "YAML", xml: "XML",
  html: "HTML", css: "CSS", ui: "UI", ux: "UX", ml: "ML", mlops: "MLOps",
  llm: "LLM", llms: "LLMs", rag: "RAG", gil: "GIL", asgi: "ASGI", wsgi: "WSGI",
  aws: "AWS", gcp: "GCP", sre: "SRE", slo: "SLO", slos: "SLOs", sla: "SLA",
  ci: "CI", cd: "CD", cuda: "CUDA", simt: "SIMT", simd: "SIMD", nccl: "NCCL",
  rlhf: "RLHF", dnn: "DNN", faas: "FaaS", iac: "IaC", oauth: "OAuth",
  owasp: "OWASP", otel: "OTel", quic: "QUIC", sse: "SSE", stomp: "STOMP",
  sycl: "SYCL", hip: "HIP", vllm: "vLLM", dsa: "DSA", dsls: "DSLs",
  junit: "JUnit", mysql: "MySQL", oci: "OCI", idp: "IdP", crds: "CRDs",
  url: "URL", uri: "URI", os: "OS", io: "I/O", id: "ID", ids: "IDs",
  lcel: "LCEL", ab: "A/B", saas: "SaaS", paas: "PaaS", iaas: "IaaS",
  mcp: "MCP", vla: "VLA", solid: "SOLID", gof: "GoF", tls: "TLS", ssl: "SSL",
  xss: "XSS", csrf: "CSRF", ddd: "DDD", tdd: "TDD", etl: "ETL", kv: "KV",
  fastapi: "FastAPI", graphql: "GraphQL", postgresql: "PostgreSQL",
  javascript: "JavaScript", devops: "DevOps", github: "GitHub", openai: "OpenAI",
  pytorch: "PyTorch", tensorflow: "TensorFlow", numpy: "NumPy", k8s: "K8s",
}));
// Touch layouts drop the hover-only prerequisite chords, and narrow ones drop
// the multi-column serpentine, so copy describing rows, hovering, or drawn
// links is false there and reads as a half-finished screen. The column
// threshold must match layoutPath()'s `W < 520` test — a landscape phone or a
// tablet really does get rows, and should be told so.
const coarsePointer = () => window.matchMedia("(pointer: coarse)").matches;
const singleColumnPath = () => window.innerWidth < 520;

// Small words stay lowercase inside a title, but never as the first word.
const TITLE_STOPWORDS = new Set(["a", "an", "and", "as", "at", "by", "for", "from",
  "in", "of", "on", "or", "the", "to", "vs", "via", "with"]);
function titleize(s) {
  return String(s).replace(/_/g, " ").replace(/[^\s/]+/g, (w, at, whole) => {
    const lower = w.toLowerCase();
    const hit = ACRONYMS.get(lower);
    if (hit) return hit;
    if (at > 0 && TITLE_STOPWORDS.has(lower)) return lower;
    return w.charAt(0).toUpperCase() + w.slice(1);
  }).replace(/\bTCP IP\b/g, "TCP/IP").replace(/\bCI CD\b/g, "CI/CD");
}

// Phase-order for the Study browser. Derived from each section's README learning path.
// Modules not listed here sort to the end (alphabetically by JS Map insertion order).
// extract.py --strict parses this literal (and STUDY_PATHS) — keep the "const STUDY_ORDER = {" ... "};" shape.
// NOTE: the guard unions EVERY slug array inside a STUDY_PATHS section — adding a second array
// (e.g. `full:`) beside `interview:` would false-fail the ordered-subset check and block deploys;
// generalize extract.py's check_wiring first.
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
  book: [
    "book/designing_data_intensive_applications/00_preface_and_book_map",
    "book/designing_data_intensive_applications/01_reliable_scalable_maintainable",
    "book/designing_data_intensive_applications/02_data_models_and_query_languages",
    "book/designing_data_intensive_applications/03_storage_and_retrieval",
    "book/designing_data_intensive_applications/04_encoding_and_evolution",
    "book/designing_data_intensive_applications/05_replication",
    "book/designing_data_intensive_applications/06_partitioning",
    "book/designing_data_intensive_applications/07_transactions",
    "book/designing_data_intensive_applications/08_trouble_with_distributed_systems",
    "book/designing_data_intensive_applications/09_consistency_and_consensus",
    "book/designing_data_intensive_applications/10_batch_processing",
    "book/designing_data_intensive_applications/11_stream_processing",
    "book/designing_data_intensive_applications/12_future_of_data_systems",
    "book/system_design_interview_vol_1/01_scale_from_zero_to_millions_of_users",
    "book/system_design_interview_vol_1/02_back_of_the_envelope_estimation",
    "book/system_design_interview_vol_1/03_a_framework_for_system_design_interviews",
    "book/system_design_interview_vol_1/04_design_a_rate_limiter",
    "book/system_design_interview_vol_1/05_design_consistent_hashing",
    "book/system_design_interview_vol_1/06_design_a_key_value_store",
    "book/system_design_interview_vol_1/07_design_a_unique_id_generator",
    "book/system_design_interview_vol_1/08_design_a_url_shortener",
    "book/system_design_interview_vol_1/09_design_a_web_crawler",
    "book/system_design_interview_vol_1/10_design_a_notification_system",
    "book/system_design_interview_vol_1/11_design_a_news_feed_system",
    "book/system_design_interview_vol_1/12_design_a_chat_system",
    "book/system_design_interview_vol_1/13_design_a_search_autocomplete_system",
    "book/system_design_interview_vol_1/14_design_youtube",
    "book/system_design_interview_vol_1/15_design_google_drive",
    "book/system_design_interview_vol_1/16_the_learning_continues",
    "book/system_design_interview_vol_2/01_proximity_service",
    "book/system_design_interview_vol_2/02_nearby_friends",
    "book/system_design_interview_vol_2/03_google_maps",
    "book/system_design_interview_vol_2/04_distributed_message_queue",
    "book/system_design_interview_vol_2/05_metrics_monitoring_and_alerting",
    "book/system_design_interview_vol_2/06_ad_click_event_aggregation",
    "book/system_design_interview_vol_2/07_hotel_reservation_system",
    "book/system_design_interview_vol_2/08_distributed_email_service",
    "book/system_design_interview_vol_2/09_s3_like_object_storage",
    "book/system_design_interview_vol_2/10_real_time_gaming_leaderboard",
    "book/system_design_interview_vol_2/11_payment_system",
    "book/system_design_interview_vol_2/12_digital_wallet",
    "book/system_design_interview_vol_2/13_stock_exchange",
    "book/designing_machine_learning_systems/01_overview_of_machine_learning_systems",
    "book/designing_machine_learning_systems/02_introduction_to_machine_learning_systems_design",
    "book/designing_machine_learning_systems/03_data_engineering_fundamentals",
    "book/designing_machine_learning_systems/04_training_data",
    "book/designing_machine_learning_systems/05_feature_engineering",
    "book/designing_machine_learning_systems/06_model_development_and_offline_evaluation",
    "book/designing_machine_learning_systems/07_model_deployment_and_prediction_service",
    "book/designing_machine_learning_systems/08_data_distribution_shifts_and_monitoring",
    "book/designing_machine_learning_systems/09_continual_learning_and_test_in_production",
    "book/designing_machine_learning_systems/10_infrastructure_and_tooling_for_mlops",
    "book/designing_machine_learning_systems/11_the_human_side_of_machine_learning",
    "book/machine_learning_system_design_interview/01_introduction_and_overview",
    "book/machine_learning_system_design_interview/02_visual_search_system",
    "book/machine_learning_system_design_interview/03_google_street_view_blurring_system",
    "book/machine_learning_system_design_interview/04_youtube_video_search",
    "book/machine_learning_system_design_interview/05_harmful_content_detection",
    "book/machine_learning_system_design_interview/06_video_recommendation_system",
    "book/machine_learning_system_design_interview/07_event_recommendation_system",
    "book/machine_learning_system_design_interview/08_ad_click_prediction_on_social_platforms",
    "book/machine_learning_system_design_interview/09_similar_listings_on_vacation_rental_platforms",
    "book/machine_learning_system_design_interview/10_personalized_news_feed",
    "book/machine_learning_system_design_interview/11_people_you_may_know",
    "book/understanding_distributed_systems/01_communication",
    "book/understanding_distributed_systems/02_coordination",
    "book/understanding_distributed_systems/03_scalability",
    "book/understanding_distributed_systems/04_resiliency",
    "book/understanding_distributed_systems/05_maintainability",
  ],
  cs_fundamentals: [
    "cs_fundamentals/complexity_analysis_and_big_o","cs_fundamentals/discrete_math_for_engineers","cs_fundamentals/number_systems_and_bit_manipulation","cs_fundamentals/character_encoding_deep_dive","cs_fundamentals/recursion_and_problem_solving_patterns",
    "cs_fundamentals/arrays_strings_and_hashing","cs_fundamentals/linked_lists_stacks_and_queues","cs_fundamentals/trees_and_binary_search_trees","cs_fundamentals/heaps_and_priority_queues","cs_fundamentals/graphs_tries_and_advanced_structures",
    "cs_fundamentals/sorting_and_searching","cs_fundamentals/dynamic_programming","cs_fundamentals/greedy_and_divide_and_conquer","cs_fundamentals/graph_and_string_algorithms","cs_fundamentals/dsa_patterns",
    "cs_fundamentals/processes_threads_and_context_switching","cs_fundamentals/cpu_scheduling_algorithms","cs_fundamentals/memory_management_and_virtual_memory","cs_fundamentals/deadlocks_and_synchronization",
    "cs_fundamentals/computer_architecture_and_memory_hierarchy","cs_fundamentals/networking_fundamentals","cs_fundamentals/database_and_storage_fundamentals","cs_fundamentals/cryptography_fundamentals","cs_fundamentals/theory_of_computation","cs_fundamentals/how_code_runs_compilers_and_interpreters",
  ],
  cuda: [
    "cuda/gpu_computing_foundations","cuda/gpu_hardware_architecture","cuda/cuda_toolkit_and_compilation",
    "cuda/cuda_programming_model_and_kernels","cuda/warps_and_simt_execution","cuda/cuda_memory_model_and_hierarchy","cuda/memory_management_and_data_transfer",
    "cuda/memory_coalescing_and_access_patterns","cuda/shared_memory_and_bank_conflicts","cuda/occupancy_and_launch_configuration","cuda/synchronization_and_atomics","cuda/parallel_patterns_reduction_scan_histogram","cuda/warp_level_primitives_and_cooperative_groups",
    "cuda/streams_events_and_concurrency","cuda/cuda_graphs","cuda/multi_gpu_programming_and_nccl","cuda/dynamic_parallelism_and_advanced_kernels",
    "cuda/tensor_cores_and_mixed_precision","cuda/cuda_math_and_dnn_libraries","cuda/python_gpu_ecosystem","cuda/triton_and_kernel_dsls",
    "cuda/profiling_and_performance_analysis","cuda/debugging_correctness_and_numerics","cuda/gpu_portability_hip_sycl_and_beyond",
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
    "java/core_language","java/strings_and_text","java/generics_and_type_system","java/exceptions_and_io","java/json_processing_jackson",
    "java/java8_features","java/java_time_datetime","java/java_streams","java/functional_programming","java/java9_to_21_features","java/java_platform_module_system",
    "java/jvm_internals","java/bytecode_and_classfile","java/reference_types_and_cleaners","java/graalvm_native_image",
    "java/concurrency","java/collections_internals","java/design_patterns_in_java",
    "java/performance_and_tuning","java/java_memory_model",
    "java/java_interview_patterns","java/testing_junit_mockito","java/logging","java/annotation_processing","java/build_tools_maven_gradle",
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
    "ml/supervised_learning","ml/ensemble_methods","ml/unsupervised_learning","ml/feature_engineering","ml/model_evaluation_and_selection","ml/imbalanced_data_and_leakage_traps",
    "ml/neural_network_fundamentals","ml/convolutional_neural_networks","ml/recurrent_neural_networks","ml/training_deep_networks","ml/generative_models",
    "ml/computer_vision","ml/natural_language_processing","ml/recommender_systems","ml/information_retrieval_and_search","ml/speech_and_audio_ml","ml/multi_task_and_multi_objective_learning","ml/time_series_forecasting","ml/anomaly_detection","ml/reinforcement_learning",
    "ml/ml_system_design","ml/data_pipelines_and_processing","ml/distributed_training","ml/experiment_tracking_and_versioning","ml/gpu_and_hardware_optimization","ml/active_learning_and_weak_supervision",
    "ml/model_serving_and_inference","ml/model_compression_and_efficiency","ml/monitoring_and_drift_detection","ml/mlops_and_ci_cd",
    "ml/graph_neural_networks","ml/self_supervised_and_contrastive_learning","ml/meta_learning_and_few_shot","ml/causal_inference_and_ml","ml/adversarial_ml_and_robustness","ml/privacy_preserving_ml","ml/interpretability_and_explainability","ml/fairness_and_responsible_ai","ml/uncertainty_quantification_and_conformal_prediction",
    "ml/ml_interview_patterns","ml/model_selection_and_algorithm_choice",
  ],
  python: [
    "python/data_model_and_objects","python/core_language_idioms","python/iterators_and_generators","python/decorators_and_closures","python/context_managers_and_exceptions","python/collections_and_data_structures","python/strings_bytes_encoding_and_regex","python/file_io_and_serialization",
    "python/cpython_memory_model","python/the_gil_and_free_threading","python/metaclasses_and_metaprogramming","python/the_type_system_and_typing","python/performance_and_profiling","python/functional_programming",
    "python/threading_and_multiprocessing","python/asyncio_and_event_loop","python/async_patterns_and_pitfalls","python/design_patterns_in_python","python/stdlib_datetime_and_logging","python/testing_with_pytest","python/packaging_and_project_tooling",
  ],
  fastapi: [
    "fastapi/fastapi_fundamentals_asgi","fastapi/pydantic_v2_deep_dive","fastapi/routing_and_request_handling","fastapi/dependency_injection_in_fastapi","fastapi/middleware_and_lifecycle","fastapi/configuration_and_settings_management",
    "fastapi/async_database_sqlalchemy","fastapi/authentication_and_security","fastapi/error_handling_and_validation","fastapi/websockets_sse_and_streaming","fastapi/background_jobs_and_task_queues","fastapi/testing_fastapi","fastapi/http_clients_and_external_apis","fastapi/message_queues_and_event_driven",
    "fastapi/production_deployment_and_scaling","fastapi/observability_and_monitoring","fastapi/caching_and_performance","fastapi/api_design_and_versioning","fastapi/security_hardening_and_owasp",
  ],
  spring: [
    "spring/ioc_container","spring/bean_lifecycle","spring/dependency_injection","spring/spring_configuration",
    "spring/spring_proxies","spring/spring_aop",
    "spring/spring_boot_autoconfiguration","spring/spring_boot_configuration","spring/spring_boot_actuator","spring/spring_modulith",
    "spring/spring_mvc_architecture","spring/request_handling","spring/filters_and_interceptors","spring/spring_webflux","spring/spring_graphql","spring/spring_hateoas_rest_maturity","spring/spring_http_clients","spring/spring_grpc","spring/validation_and_error_handling",
    "spring/spring_data_jpa","spring/spring_data_nosql","spring/database_migrations","spring/spring_transactions","spring/spring_caching",
    "spring/spring_security_architecture","spring/spring_security_jwt_oauth","spring/spring_session",
    "spring/spring_cloud_config","spring/spring_cloud_patterns","spring/spring_messaging","spring/spring_websocket_stomp","spring/spring_batch","spring/spring_events_and_scheduling","spring/spring_ai","spring/spring_integration",
    "spring/spring_testing","spring/spring_performance","spring/observability_and_tracing","spring/spring_native_graalvm",
  ],
  technologies: [
    "technologies/apache_airflow","technologies/nvidia_triton_inference_server",
  ],
};

// Curated "Interview-Specific" learning paths — an ordered SUBSET of STUDY_ORDER
// per section, focused on what a senior interview actually probes. Only sections
// with an entry here show the Full/Interview toggle in the Study view; all others
// behave exactly as before (Full only). Slugs are the same "<section>/<module>"
// form as STUDY_ORDER and MUST stay in sync with that section's README
// "Learning Paths" list (LLM was the pilot; ML, Java, Spring now live too).
const STUDY_PATHS = {
  cuda: {
    interview: [
      "cuda/gpu_computing_foundations","cuda/gpu_hardware_architecture",
      "cuda/cuda_programming_model_and_kernels","cuda/warps_and_simt_execution","cuda/cuda_memory_model_and_hierarchy","cuda/memory_management_and_data_transfer",
      "cuda/memory_coalescing_and_access_patterns","cuda/shared_memory_and_bank_conflicts","cuda/occupancy_and_launch_configuration","cuda/synchronization_and_atomics","cuda/parallel_patterns_reduction_scan_histogram","cuda/warp_level_primitives_and_cooperative_groups",
      "cuda/streams_events_and_concurrency","cuda/tensor_cores_and_mixed_precision",
      "cuda/profiling_and_performance_analysis","cuda/debugging_correctness_and_numerics",
    ],
  },
  llm: {
    interview: [
      "llm/foundations_and_architecture","llm/tokenization_and_embeddings","llm/embeddings_and_similarity_search","llm/pre_training",
      "llm/fine_tuning","llm/alignment_and_rlhf",
      "llm/prompt_engineering","llm/rag_fundamentals","llm/advanced_rag","llm/context_engineering","llm/reasoning_models",
      "llm/agents_and_tool_use","llm/agentic_workflow_patterns","llm/multi_agent_systems","llm/mcp_model_context_protocol",
      "llm/inference_and_decoding","llm/context_windows_and_long_context","llm/inference_engines","llm/vllm_deep_dive","llm/optimization_and_quantization",
      "llm/deployment_and_mlops","llm/llm_caching","llm/token_economics_and_cost_optimization","llm/llm_routing_and_model_selection",
      "llm/evaluation_and_benchmarks","llm/llm_testing_strategies","llm/guardrails_and_content_safety",
      "llm/safety_and_alignment","llm/llm_security","llm/mixture_of_experts",
    ],
  },
  ml: {
    interview: [
      "ml/probability_and_statistics","ml/optimization_theory","ml/information_theory",
      "ml/supervised_learning","ml/ensemble_methods","ml/unsupervised_learning","ml/feature_engineering","ml/model_evaluation_and_selection","ml/imbalanced_data_and_leakage_traps",
      "ml/neural_network_fundamentals","ml/convolutional_neural_networks","ml/recurrent_neural_networks","ml/training_deep_networks",
      "ml/natural_language_processing","ml/recommender_systems",
      "ml/ml_system_design","ml/data_pipelines_and_processing","ml/experiment_tracking_and_versioning","ml/model_serving_and_inference","ml/model_compression_and_efficiency","ml/monitoring_and_drift_detection","ml/mlops_and_ci_cd",
      "ml/interpretability_and_explainability",
      "ml/ml_interview_patterns","ml/model_selection_and_algorithm_choice",
    ],
  },
  java: {
    interview: [
      "java/core_language","java/strings_and_text","java/generics_and_type_system","java/exceptions_and_io","java/json_processing_jackson",
      "java/java8_features","java/java_streams","java/java9_to_21_features",
      "java/jvm_internals","java/concurrency","java/collections_internals","java/design_patterns_in_java","java/java_memory_model",
      "java/java_interview_patterns","java/testing_junit_mockito",
      "java/structured_concurrency_and_loom","java/jdbc_and_database",
    ],
  },
  spring: {
    interview: [
      "spring/ioc_container","spring/bean_lifecycle","spring/dependency_injection","spring/spring_configuration",
      "spring/spring_proxies","spring/spring_aop",
      "spring/spring_boot_autoconfiguration","spring/spring_boot_actuator",
      "spring/spring_mvc_architecture","spring/request_handling","spring/filters_and_interceptors","spring/spring_webflux","spring/validation_and_error_handling",
      "spring/spring_data_jpa","spring/spring_transactions","spring/spring_caching",
      "spring/spring_security_architecture","spring/spring_security_jwt_oauth",
      "spring/spring_cloud_patterns","spring/spring_messaging",
      "spring/spring_testing",
    ],
  },
  lld: {
    interview: [
      "lld/solid_principles","lld/creational","lld/structural","lld/behavioral","lld/pattern_comparisons","lld/anti_patterns","lld/system_design_problems",
    ],
  },
  hld: {
    interview: [
      "hld/scalability","hld/load_balancing","hld/caching","hld/database_design","hld/cap_theorem","hld/api_design","hld/message_queues","hld/rate_limiting",
      "hld/cdn","hld/consistent_hashing","hld/database_sharding","hld/microservices","hld/distributed_transactions","hld/resilience_patterns",
    ],
  },
  backend: {
    interview: [
      "backend/http_protocols","backend/rest_api_design","backend/grpc_and_protobuf",
      "backend/connection_pooling_deep_dive","backend/caching_strategies_deep_dive","backend/async_and_concurrency_patterns",
      "backend/database_internals_and_indexing","backend/query_optimization","backend/distributed_transactions_and_consistency",
      "backend/fault_tolerance_patterns","backend/rate_limiting_in_depth","backend/observability_and_monitoring",
      "backend/backend_security_owasp","backend/auth_and_authorization_systems",
      "backend/event_driven_fundamentals","backend/kafka_deep_dive","backend/microservices_fundamentals","backend/api_gateway_patterns",
    ],
  },
  database: {
    interview: [
      "database/database_fundamentals","database/storage_engines_internals","database/indexing_deep_dive","database/concurrency_control_and_locking",
      "database/postgresql_internals","database/sql_query_optimization","database/schema_design_and_normalization",
      "database/document_databases","database/key_value_stores","database/vector_databases",
      "database/replication_and_high_availability","database/sharding_and_partitioning","database/distributed_transactions","database/consistency_models_and_consensus",
      "database/database_caching_patterns","database/database_selection_framework",
    ],
  },
  python: {
    interview: [
      "python/data_model_and_objects","python/core_language_idioms","python/iterators_and_generators","python/decorators_and_closures","python/context_managers_and_exceptions","python/collections_and_data_structures",
      "python/cpython_memory_model","python/the_gil_and_free_threading","python/the_type_system_and_typing","python/functional_programming",
      "python/asyncio_and_event_loop","python/async_patterns_and_pitfalls","python/testing_with_pytest",
    ],
  },
  fastapi: {
    interview: [
      "fastapi/fastapi_fundamentals_asgi","fastapi/pydantic_v2_deep_dive","fastapi/dependency_injection_in_fastapi","fastapi/async_database_sqlalchemy","fastapi/authentication_and_security","fastapi/error_handling_and_validation",
      "fastapi/production_deployment_and_scaling","fastapi/observability_and_monitoring","fastapi/caching_and_performance",
    ],
  },
  devops: {
    interview: [
      "devops/linux_and_os_fundamentals","devops/networking_for_devops",
      "devops/containers_and_docker","devops/kubernetes_architecture","devops/kubernetes_workloads_and_objects","devops/kubernetes_networking","devops/kubernetes_scheduling_and_autoscaling","devops/kubernetes_security",
      "devops/ci_cd_fundamentals","devops/ci_cd_platforms","devops/deployment_strategies","devops/gitops_argocd_flux",
      "devops/infrastructure_as_code_terraform","devops/secrets_management",
      "devops/cloud_fundamentals_and_aws","devops/serverless_and_faas","devops/cloud_networking_and_cdn",
      "devops/observability_metrics_prometheus","devops/observability_logging","devops/observability_tracing_and_otel","devops/sre_principles_and_slos","devops/incident_management_and_oncall",
    ],
  },
  cs_fundamentals: {
    interview: [
      "cs_fundamentals/complexity_analysis_and_big_o","cs_fundamentals/number_systems_and_bit_manipulation","cs_fundamentals/recursion_and_problem_solving_patterns",
      "cs_fundamentals/arrays_strings_and_hashing","cs_fundamentals/linked_lists_stacks_and_queues","cs_fundamentals/trees_and_binary_search_trees","cs_fundamentals/heaps_and_priority_queues","cs_fundamentals/graphs_tries_and_advanced_structures",
      "cs_fundamentals/sorting_and_searching","cs_fundamentals/dynamic_programming","cs_fundamentals/greedy_and_divide_and_conquer","cs_fundamentals/graph_and_string_algorithms","cs_fundamentals/dsa_patterns",
      "cs_fundamentals/processes_threads_and_context_switching","cs_fundamentals/cpu_scheduling_algorithms","cs_fundamentals/memory_management_and_virtual_memory","cs_fundamentals/deadlocks_and_synchronization",
    ],
  },
};

// Which path the learner picked for a section, persisted as a JSON map keyed by
// section: { llm: "interview" } etc. Defaults to "full" (backward-compatible).
function getStudyPath(section) {
  try { return (JSON.parse(localStorage.getItem("sd_study_path") || "{}")[section]) || "full"; }
  catch { return "full"; }
}
function setStudyPath(section, path) {
  let m = {};
  try { m = JSON.parse(localStorage.getItem("sd_study_path") || "{}"); } catch { }
  m[section] = path;
  safeSet("sd_study_path", JSON.stringify(m));
}

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
  if (save) safeSet("sd_theme", id);
  document.querySelectorAll(".theme-opt").forEach((b) =>
    b.setAttribute("aria-checked", b.dataset.theme === id ? "true" : "false"));
}

function closeThemePop() {
  const pop = document.getElementById("themePop");
  if (pop) { pop._release?.(); pop.remove(); }
  const tb = document.getElementById("themeBtn");
  if (tb) tb.setAttribute("aria-expanded", "false");
}

// Shared theme-radio option markup — single source used by both the topbar
// popover and the <=640px More sheet, so the two stay identical.
function themeOptionsHTML() {
  return THEMES.map((t) =>
    `<button class="theme-opt" role="radio" data-theme="${t.id}" aria-checked="${curTheme() === t.id}">
       <span class="swatch sw-${t.id}" aria-hidden="true"></span>${t.name}<span class="tcheck">✓</span>
     </button>`).join("");
}

function toggleThemePop() {
  if (document.getElementById("themePop")) { closeThemePop(); return; }
  const pop = document.createElement("div");
  pop.className = "theme-pop"; pop.id = "themePop";
  pop.setAttribute("role", "radiogroup"); pop.setAttribute("aria-label", "Theme");
  pop.innerHTML = `<div class="tp-h">Theme</div>` + themeOptionsHTML();
  document.body.appendChild(pop);
  const tb = document.getElementById("themeBtn");
  if (tb) tb.setAttribute("aria-expanded", "true");
  pop.querySelectorAll(".theme-opt").forEach((b) => b.addEventListener("click", () => {
    applyTheme(b.dataset.theme);
    pop._radioSync?.();
  }));
  wireRadioGroup(pop);
  pop._release = trapFocus(pop, { initial: '[aria-checked="true"]', restoreTo: tb });
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

/* ---------- [E1] streak detail popover ---------- */
// Same glass-popover pattern as the theme picker, anchored under the topbar
// flame chip instead of fixed to a corner (the chip isn't in the same spot in
// every layout width).
function closeStreakPop() {
  const pop = document.getElementById("streakPop");
  if (pop) { pop._release?.(); pop.remove(); }
  const chip = document.getElementById("streakChip");
  if (chip) chip.setAttribute("aria-expanded", "false");
}
function toggleStreakPop() {
  if (document.getElementById("streakPop")) { closeStreakPop(); return; }
  const anchor = document.getElementById("streakChip");
  if (!anchor) return;
  const p = state.progress || {};
  const lastFreezes = (p.freezeUsedOn || []).slice(-3).reverse();
  const pop = document.createElement("div");
  pop.className = "streak-pop"; pop.id = "streakPop";
  pop.setAttribute("role", "dialog"); pop.setAttribute("aria-label", "Streak detail");
  pop.innerHTML = `
    <div class="tp-h">Streak</div>
    <div class="sp-row"><span>Current</span><b>${p.streak || 0} day${(p.streak || 0) === 1 ? "" : "s"}</b></div>
    <div class="sp-row"><span>Longest</span><b>${p.longestStreak || 0} day${(p.longestStreak || 0) === 1 ? "" : "s"}</b></div>
    <div class="sp-row"><span>Freezes held</span><b>${ICON("snow", "i-snow")} ${p.freezes || 0}</b></div>
    <div class="sp-rule">Earn one freeze every 7-day streak milestone, up to 3. A freeze auto-covers a single missed day.</div>
    ${lastFreezes.length ? `<div class="sp-sub">Last used</div><div class="sp-dates">${lastFreezes.map((d) => `<span>${fmtDate(d)}</span>`).join("")}</div>` : ""}
  `;
  document.body.appendChild(pop);
  anchor.setAttribute("aria-expanded", "true");
  const r = anchor.getBoundingClientRect();
  pop.style.top = (r.bottom + 8) + "px";
  pop.style.left = Math.min(r.left, window.innerWidth - pop.offsetWidth - 12) + "px";
  pop.tabIndex = -1;
  pop._release = trapFocus(pop, { restoreTo: anchor });
  const dismiss = (e) => {
    if (e.type === "keydown") { if (e.key !== "Escape") return; }
    else if (pop.contains(e.target) || e.target.closest?.("#streakChip")) return;
    closeStreakPop();
    document.removeEventListener("pointerdown", dismiss, true);
    document.removeEventListener("keydown", dismiss, true);
  };
  document.addEventListener("pointerdown", dismiss, true);
  document.addEventListener("keydown", dismiss, true);
}

const app = document.getElementById("app");
const state = {
  index: null, today: null, progress: null,
  deck: [], queue: [], cursor: 0, section: null, modules: null, sourceFiles: null, limit: null,
  combo: 0, maxCombo: 0, sessionXp: 0, inQuiz: false, answered: false,
  curOptsLen: 0, replayFn: null,
  hard: false, awaitingConf: false, pendingPick: null,
  // [B] per-deck session trackers — reset by resetPhaseBState() in startDeck/resumeDeck.
  _bossIntroShown: false, _doubleDown: null, _doubleDownIdx: null, _doubleDownWager: 0,
  _missStreak: 0, _maxMissStreak: 0, _answerLog: [], _cardStreak: 0, _capsuleReturns: [],
  _orphanToastShown: false,   // [E1] session-scoped (not persisted) — one orphan-cleanup toast per page load
};

/* ---------- helpers ---------- */
// [D] QA seam: a ?qa_date=YYYY-MM-DD URL param overrides "today" everywhere date
// math flows through this function (coach pick, streaks, debrief week bounds,
// due reviews). Only honored when present and well-formed — normal play is
// untouched. See qa_phaseD.mjs for how tests use it to force date-dependent
// scenarios without changing the system clock.
const todayISO = () => {
  const qp = new URLSearchParams(location.search).get("qa_date");
  if (qp && /^\d{4}-\d{2}-\d{2}$/.test(qp)) return qp;
  return new Date().toLocaleDateString("en-CA"); // YYYY-MM-DD, local
};
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
    star: '<polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>',
    copy: '<rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>',
  };
  return `<svg class="icon ${cls}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths[name]}</svg>`;
};

// Screen-reader announcements (aria-live region in index.html).
const announce = (msg) => { const n = el("#live"); if (n) n.textContent = msg; };

// Focus containment for modal overlays. Focuses opts.initial (selector or
// element; default first focusable), cycles Tab/Shift+Tab inside `container`,
// and on release() restores focus to opts.restoreTo (default: the element
// focused when the trap was armed). Esc/close semantics stay with the caller.
const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]), textarea:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])';
function trapFocus(container, opts = {}) {
  const prev = opts.restoreTo || document.activeElement;
  const focusables = () => [...container.querySelectorAll(FOCUSABLE)].filter((n) => n.offsetParent !== null || n === document.activeElement);
  const first = opts.initial
    ? (typeof opts.initial === "string" ? container.querySelector(opts.initial) : opts.initial)
    : focusables()[0];
  (first || container).focus({ preventScroll: true });
  const onKey = (e) => {
    if (e.key !== "Tab") return;
    const f = focusables();
    if (!f.length) { e.preventDefault(); return; }
    const i = f.indexOf(document.activeElement);
    if (e.shiftKey && (i <= 0)) { e.preventDefault(); f[f.length - 1].focus(); }
    else if (!e.shiftKey && (i === -1 || i === f.length - 1)) { e.preventDefault(); f[0].focus(); }
  };
  document.addEventListener("keydown", onKey, true);
  return function release(restore = true) {
    document.removeEventListener("keydown", onKey, true);
    if (restore && prev && prev.isConnected) prev.focus({ preventScroll: true });
  };
}

// Roving-tabindex keyboard nav for an ARIA radio group: tabindex 0 on the
// aria-checked radio (else the first), -1 on the rest; Arrow keys move+click()
// the neighbor (wrapping), Home/End jump to the ends. click() lets the caller's
// existing handler drive the state change. (Wired into call sites in a later wave.)
function wireRadioGroup(container) {
  if (!container) return;
  const radios = () => [...container.querySelectorAll('[role=radio]')];
  const sync = () => {
    const r = radios();
    const checked = r.find((n) => n.getAttribute("aria-checked") === "true") || r[0];
    r.forEach((n) => n.setAttribute("tabindex", n === checked ? "0" : "-1"));
  };
  container._radioSync = sync;
  sync();
  if (!container.dataset.radioWired) {
    container.dataset.radioWired = "1";
    container.addEventListener("keydown", (e) => {
      const r = radios();
      if (!r.length) return;
      const i = r.indexOf(document.activeElement);
      let j;
      if (e.key === "ArrowRight" || e.key === "ArrowDown") j = (i + 1) % r.length;
      else if (e.key === "ArrowLeft" || e.key === "ArrowUp") j = (i - 1 + r.length) % r.length;
      else if (e.key === "Home") j = 0;
      else if (e.key === "End") j = r.length - 1;
      else return;
      e.preventDefault();
      r[j].focus(); r[j].click(); sync();
    });
  }
}

const REDUCED = () => window.matchMedia("(prefers-reduced-motion: reduce)").matches;

// Cross-fade between screens via the View Transitions API where available.
function vt(fn, cls) {
  if (document.startViewTransition && !REDUCED()) {
    // [E2] optional scope class on <html> so ::view-transition keyframes can be
    // directional (e.g. a forward question swap slides left, not just cross-fades).
    if (cls) document.documentElement.classList.add(cls);
    const t = document.startViewTransition(fn);
    /* [C] rapid successive navigations skip a transition; the ready/finished
       promises then reject with a benign AbortError — swallow it. */
    t.ready?.catch(() => {}); t.finished?.catch(() => {});
    if (cls) { const done = () => document.documentElement.classList.remove(cls); t.finished?.then(done, done); }
  } else fn();
}

/* ---------- ambient graphics: spotlight, parallax, scroll reveals ---------- */
// Pointer spotlight: glass cards get a specular highlight that follows the
// cursor (CSS paints a radial gradient at --mx/--my; see style.css §16).
const SPOT_SEL = ".tile,.topic-card,.review-card,.badge,.opt,.modrow,.sectiontile,.miss-item,.pathnode";
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
  // [PERF] Drop the previous render's targets before observing this one. app.innerHTML
  // has already detached them; without this, any tile removed before it scrolled into
  // view (the safety-net timeout adds "in" but never unobserved) stayed in the target
  // set forever — a detached-node leak that grew with every Home/Study/picker render.
  _revealObs.disconnect();
  // NOTE: utility class is "rise", NOT "reveal" — .reveal is the quiz answer panel.
  document.querySelectorAll(".grid .tile, .sectiontile, .modrow, .miss-item").forEach((n, i) => {
    n.classList.add("rise");
    n.style.transitionDelay = (i % 8) * 30 + "ms";
    _revealObs.observe(n);
    // Safety net: if the observer never fires (never scrolled into view, or a
    // browser quirk), reveal anyway so content is never stuck invisible. Idempotent.
    setTimeout(() => n.classList.add("in"), 1400 + (i % 8) * 30);
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

/* ---------- [E1] generic toast ---------- */
// Ephemeral auto-dismissing status bar, bottom of viewport — visual language
// borrowed from the [D] same-day nudge, but for one-shot confirmations (no
// dismiss button; it clears itself). REDUCED-gated via the .toast media rule.
function showToast(msg, ms = 3600) {
  document.querySelectorAll(".toast").forEach((t) => t.remove());
  const o = document.createElement("div");
  o.className = "toast"; o.textContent = msg;
  document.body.appendChild(o);
  announce(msg);
  setTimeout(() => {
    o.classList.add("out");
    setTimeout(() => o.remove(), REDUCED() ? 0 : 200);
  }, ms);
}

/* ---------- [E1] first-run coach marks ---------- */
// A single glass tooltip anchored under/near an element, shown once ever per
// id (localStorage sd_cm_<id>), dismissed by any click/keydown anywhere.
// First-run is detected as an empty progress.history at boot; veterans get
// every id pre-seeded "seen" (see seedCoachMarksIfVeteran, called from boot())
// so a long-time player never sees onboarding chrome.
const COACH_MARK_IDS = ["first_question", "first_combo", "first_results", "first_cards"];
function seedCoachMarksIfVeteran() {
  if ((state.progress.history || []).length === 0) return;   // this IS a first-run: let marks fire
  for (const id of COACH_MARK_IDS) {
    const k = "sd_cm_" + id;
    if (!localStorage.getItem(k)) safeSet(k, "1");
  }
}
function coachMark(anchorSel, text, id) {
  const key = "sd_cm_" + id;
  if (localStorage.getItem(key) === "1") return;
  const anchor = el(anchorSel);
  if (!anchor || el(".coach-mark")) return;
  safeSet(key, "1");                    // mark seen immediately — no re-show race
  const tip = document.createElement("div");
  tip.className = "coach-mark" + (REDUCED() ? " reduced" : "");
  tip.innerHTML = `<div class="cm-arrow"></div><div class="cm-body">${esc(text)}</div>`;
  document.body.appendChild(tip);
  const r = anchor.getBoundingClientRect();
  const w = tip.offsetWidth;
  let left = r.left + r.width / 2 - w / 2;
  left = Math.min(Math.max(12, left), window.innerWidth - w - 12);
  tip.style.left = left + "px";
  tip.style.top = (r.bottom + 12) + "px";
  const arrow = tip.querySelector(".cm-arrow");
  arrow.style.left = Math.min(Math.max(14, r.left + r.width / 2 - left - 6), Math.max(14, w - 20)) + "px";
  announce(text);
  const dismiss = () => {
    tip.remove();
    document.removeEventListener("pointerdown", dismiss, true);
    document.removeEventListener("keydown", dismiss, true);
  };
  // Deferred so the click/keypress that led here doesn't also dismiss it.
  setTimeout(() => {
    document.addEventListener("pointerdown", dismiss, true);
    document.addEventListener("keydown", dismiss, true);
  }, 0);
}

/* ---------- keyboard-shortcuts overlay ---------- */
function toggleHelp() {
  const ex = el("#helpOverlay");
  if (ex) { ex._close(); return; }
  const row = (k, d) => `<div class="hk"><span>${d}</span><span class="keys">${k.split(" ").map((x) => `<kbd>${x}</kbd>`).join("")}</span></div>`;
  const o = document.createElement("div");
  o.className = "help-overlay"; o.id = "helpOverlay";
  o.setAttribute("role", "dialog"); o.setAttribute("aria-modal", "true"); o.setAttribute("aria-label", "Keyboard shortcuts");
  o.innerHTML = `<div class="help-card">
    <h2>Keyboard shortcuts</h2>
    <div class="help-cols">
      <div><h3>Quiz</h3>${row("1 2 3 4", "Answer")}${row("↵", "Next")}${row("S", "Skip for now")}${row("D", "Double down")}</div>
      <div><h3>Cards</h3>${row("Space", "Reveal")}${row("1", "Missed it")}${row("2", "Got it")}</div>
      <div><h3>Reader</h3>${row("F", "Fullscreen")}${row("Esc", "Exit / close")}</div>
      <div><h3>Diagram zoom</h3>${row("+ −", "Zoom")}${row("0", "Fit")}${row("← →", "Pan")}</div>
      <div><h3>Everywhere</h3>${row("Cmd/Ctrl+K /", "Search everything")}${row("?", "This help")}</div>
    </div>
    <p class="help-hint">Press <kbd>?</kbd> anytime &middot; mouse back/forward buttons navigate too<span class="touch-hint"> &middot; more options live behind the &#8943; button on phones</span></p>
    <button class="ghost" id="helpClose">Close <span class="key-hint">(Esc)</span></button>
  </div>`;
  document.body.appendChild(o);
  const release = trapFocus(o, { initial: "#helpClose" });
  const close = () => { release(); o.remove(); };
  o._close = close;
  el("#helpClose").addEventListener("click", close);
  o.addEventListener("click", (e) => { if (e.target === o) close(); });
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
/* [C] NOTE: seededShuffle lives beside the [D] quest helpers — it accepts an
   rng function ([D] callers) or a plain seed string ([C] callers). */

// Fetch a static JSON file (question banks, index, graph); returns `fallback`
// on any error. Pages serves these with normal caching; "default" lets a 304
// revalidate the multi-MB banks instead of re-downloading them every boot.
async function fetchJSON(path, fallback, cache = "no-store") {
  const ctl = new AbortController();
  const t = setTimeout(() => ctl.abort(), 20000);
  try { const r = await fetch(path, { cache, signal: ctl.signal }); if (!r.ok) throw 0; return await r.json(); }
  catch { return fallback; }
  finally { clearTimeout(t); }
}

/* ---------- [A2] shared learning helpers ---------- */
// [PERF] Bounded insert for the section/path caches. In a never-reloaded Android
// WebView these otherwise grow for the whole app lifetime — every section bank is
// several MB as a live object graph, every reader page a retained markdown string
// — which is the platform-specific "heavier the more you use it". FIFO by
// insertion order (string-keyed objects preserve it); evicting just forces a
// cheap re-fetch (HTTP/SW-cached) on the next visit. Every read site already
// tolerates a cache miss, so eviction degrades gracefully.
function capInsert(obj, key, val, cap, onEvict) {
  obj[key] = val;
  const keys = Object.keys(obj);
  for (let i = 0; keys.length - i > cap; i++) {
    const old = keys[i];
    if (old === key) continue;                       // never evict the just-inserted entry
    delete obj[old];
    if (onEvict) onEvict(old);
  }
  return val;
}

// Knowledge-graph cache — mirrors bankCache; a missing file tolerantly -> null.
const graphCache = {};
const GRAPH_CACHE_CAP = 6;
async function loadGraph(section) {
  if (!(section in graphCache)) capInsert(graphCache, section, await fetchJSON(`graph/${section}.json`, null, "default"), GRAPH_CACHE_CAP);
  return graphCache[section];
}

// Tokenizer ported from extract.py (lowercase, split on non-alphanumerics, drop
// <3-char tokens and the stopword set) — applied to the USER's explain-back text
// only; the question side uses the bank's precomputed `concepts`.
const A2_STOP = new Set(("a an and the of to in for on with is are be by as at it its this that these those or " +
  "not no if then else when while do does done can could should would may might will you your they them their " +
  "we our he she his her from into over under than so such each per via use used using uses what why how which " +
  "who whom where whose between within across about after before during because both either neither only also " +
  "more most less least very much many few some any all one two three first second data system systems model " +
  "models value values case cases example examples type types").split(/\s+/));
function a2Tokenize(text) {
  return (String(text).toLowerCase().match(/[a-z0-9]+/g) || []).filter((t) => t.length > 2 && !A2_STOP.has(t));
}

// Greedy reorder so no two consecutive items share a module: among modules that
// differ from the previous one, take the one with the MOST remaining (minimizes
// forced same-module adjacencies); graph-connectedness breaks ties, so the
// A-B-A'-C texture shows when counts are equal. Accepts raw questions or deck items.
function orderInterleaved(items, pairs) {
  if (items.length < 3) return items.slice();
  const modOf = (it) => it.module || (it.q && it.q.module);
  const adj = new Map();
  for (const p of pairs || []) {
    if (!adj.has(p.a)) adj.set(p.a, new Set());
    if (!adj.has(p.b)) adj.set(p.b, new Set());
    adj.get(p.a).add(p.b); adj.get(p.b).add(p.a);
  }
  const connected = (m1, m2) => m1 && m2 && m1 !== m2 && adj.has(m1) && adj.get(m1).has(m2);
  const pool = items.slice(), out = [];
  const count = new Map();
  pool.forEach((it) => count.set(modOf(it), (count.get(modOf(it)) || 0) + 1));
  let prevMod = null;
  while (pool.length) {
    let best = 0, bestScore = -1;
    for (let i = 0; i < pool.length; i++) {
      const m = modOf(pool[i]);
      const score = (m === prevMod ? 0 : 1000) + count.get(m) * 2 + (connected(m, prevMod) ? 1 : 0);
      if (score > bestScore) { bestScore = score; best = i; }
    }
    const chosen = pool.splice(best, 1)[0], cm = modOf(chosen);
    out.push(chosen);
    count.set(cm, count.get(cm) - 1);
    prevMod = cm;
  }
  return out;
}

// Aggregate spaced-repetition records per module for the Memory Map.
// retention = mean exp(-daysSinceScheduled / max(1, interval*ease)), capped 0..1.
function moduleStats(progress) {
  const today = new Date(todayISO() + "T00:00:00"), tISO = todayISO();
  const acc = {};
  for (const rv of Object.values((progress && progress.reviews) || {})) {
    const mod = rv.module;
    if (!mod) continue;
    const s = acc[mod] || (acc[mod] = { count: 0, held: 0, overdue: 0, easeSum: 0, retSum: 0 });
    s.count++;
    s.easeSum += rv.ease || 2.5;
    const overdue = !!(rv.due && rv.due < tISO);
    if (overdue) s.overdue++;
    if ((rv.reps || 0) >= 1 && !overdue) s.held++;
    let ret = 0;
    if (rv.due) {
      const interval = rv.interval || 1;
      const from = new Date(rv.due + "T00:00:00");
      from.setDate(from.getDate() - interval);         // the day it was last scheduled
      const days = Math.max(0, Math.round((today - from) / 86400000));
      ret = Math.exp(-days / Math.max(1, interval * (rv.ease || 2.5)));
    }
    s.retSum += Math.max(0, Math.min(1, ret));
  }
  const out = {};
  for (const [mod, s] of Object.entries(acc)) {
    out[mod] = { count: s.count, held: s.held, overdue: s.overdue, avgEase: s.easeSum / s.count, retention: s.retSum / s.count };
  }
  return out;
}

// Display name for a module key ("hld/caching" -> "caching") — matches extract.py.
const modDisplay = (mod) => titleize(String(mod).split("/").pop());

// Confusion tally: increment "<a>|<b>" (canonical order set by caller), cap 50 keys.
function tallyConfusion(p, key) {
  const c = (p.confusions = p.confusions || {});
  c[key] = (c[key] || 0) + 1;
  const keys = Object.keys(c);
  if (keys.length > 50) {
    let minK = keys[0];
    for (const k of keys) if (c[k] < c[minK]) minK = k;
    if (minK !== key) delete c[minK];
  }
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
    // --- [B] moment/feedback tones ---
    bossSting() { tone(110, 0.4, "sawtooth", 0.05); tone(165, 0.4, "sawtooth", 0.04, 0.05); },
    recovered() { tone(440, 0.14, "sine", 0.055); tone(660, 0.16, "sine", 0.05, 0.08); },
    isOn: on,
    toggle() { const wasOn = on(); safeSet("sd_mute", wasOn ? "1" : "0"); return !wasOn; },
  };
})();

// Haptics: a short buzz paired with the same beats as sfx. Gated on vibrate
// support + a coarse pointer (phones/tablets only — desktops never buzz) + the
// sound mute (one "quiet" toggle governs both) + reduced-motion. Silently no-ops
// everywhere else, so call sites don't need their own guards.
const haptic = (() => {
  let coarse = false;
  try { coarse = window.matchMedia && window.matchMedia("(pointer: coarse)").matches; } catch { /* pre-matchMedia */ }
  const canBuzz = () => coarse && ("vibrate" in navigator) && sfx.isOn() && !REDUCED();
  const P = { correct: 8, wrong: [0, 26], combo: [0, 10, 40, 10], levelup: [0, 18, 55, 18, 55], tier: [0, 20, 40, 20], seal: [0, 30, 40, 12] };
  return (kind) => { if (canBuzz()) { try { navigator.vibrate(P[kind] || 8); } catch { /* blocked */ } } };
})();

function confetti() {
  if (REDUCED()) return;
  const cs = getComputedStyle(document.documentElement);
  const colors = ["--accent", "--accent-2", "--good", "--warn", "--bad"]
    .map((v) => cs.getPropertyValue(v).trim() || "#7cabff");
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
  if (REDUCED()) { node.textContent = "+" + Math.round(to); return; }   // no tween — land on the final value
  const dur = 600, start = performance.now();
  function step(now) {
    const k = Math.min(1, (now - start) / dur);
    node.textContent = "+" + Math.round(to * (1 - Math.pow(1 - k, 3)));
    if (k < 1) requestAnimationFrame(step);
  }
  requestAnimationFrame(step);
}

/* ---------- persistence ---------- */
// Quota-safe localStorage write. Never throws. Returns true on success.
// One toast per session on first failure, nudging the export path
// (Progress screen is the only backup path — game/CLAUDE.md).
let _quotaWarned = false;
function safeSet(key, val) {
  try { localStorage.setItem(key, val); return true; }
  catch {
    if (!_quotaWarned) {
      _quotaWarned = true;
      showToast("Storage is full — progress may not save. Export a backup from Progress.", 6000);
    }
    return false;
  }
}

// [PERF] `reviews` is the ONLY unbounded store in sd_progress: one entry (~190 B)
// per question ever first-attempted. Ungoverned it climbs toward the ~8,800-question
// bank ceiling (~1.7 MB), and the whole blob is JSON-parsed on every boot and
// re-serialized on every save — so boot/save cost grows linearly with use (the
// "heavier to load" symptom, identical on Pages and APK). Cap it, evicting the
// entries LEAST valuable to spaced repetition: the most stable, furthest-future
// items. NEVER drop a user-flagged leech, a planted time capsule, or anything due
// now/overdue. Only the entry COUNT changes — the map shape is untouched, so the
// additive-field invariant holds and old exports still import (the next save just
// re-applies the cap). The cap sits well above any realistic active review set.
const REVIEWS_CAP = 6000;
function pruneReviews(p) {
  const r = p && p.reviews;
  if (!r) return 0;
  const keys = Object.keys(r);
  if (keys.length <= REVIEWS_CAP) return 0;
  const today = todayISO();
  const evictable = keys.filter((id) => {
    const v = r[id];
    return v && !v.flagged && !v.capsule && !(v.due && v.due <= today);
  });
  // Most-evictable first: highest interval (most stable), then furthest-future due,
  // then most reps. Deleting from the front sheds the lowest-marginal-loss items.
  evictable.sort((a, b) => (r[b].interval || 0) - (r[a].interval || 0)
    || String(r[b].due || "").localeCompare(String(r[a].due || ""))
    || (r[b].reps || 0) - (r[a].reps || 0));
  let over = keys.length - REVIEWS_CAP, removed = 0;
  for (let i = 0; i < evictable.length && over > 0; i++, over--) { delete r[evictable[i]]; removed++; }
  return removed;
}

// localStorage sd_progress is the single source of truth (Pages-only).
function loadProgress() {
  const fill = (p) => { if (!p.reviews) p.reviews = {}; if (p.freezes == null) p.freezes = 2; if (!p.freezeUsedOn) p.freezeUsedOn = []; if (!p.awards) p.awards = {}; if (p.deepReads == null) p.deepReads = 0; if (!p.readModules) p.readModules = {}; if (!p.reading) p.reading = { day: null, count: 0, streak: 0, longest: 0, todayKeys: {} }; return p; };  /* [C] awards + deepReads; reading-tracker backfill */
  // Corrupt sd_progress: keep the raw copy (recoverable via export/import) and
  // flag so boot() can surface a one-time toast, instead of silently reseeding.
  let ls = null, corrupt = false;
  const raw = localStorage.getItem("sd_progress");
  try { ls = raw == null ? null : JSON.parse(raw); } catch { corrupt = true; }
  if (corrupt && raw) { safeSet("sd_progress_corrupt", raw); state._progressCorrupt = true; }
  if (ls) {                                          // [PERF] one-time shrink of an oversized store on boot (idempotent)
    const p = fill(ls);
    if (pruneReviews(p)) safeSet("sd_progress", JSON.stringify(p));   // persist the smaller blob so future boots parse less
    return p;
  }
  return { streak: 0, longestStreak: 0, lastPlayed: null, totalXP: 0, sections: {}, history: [], reviews: {}, freezes: 2, freezeUsedOn: [], awards: {}, deepReads: 0 };
}

// Reading feeds the game: mark a module read the first time its page is scrolled
// through (>=90%) or dwelt on when it fits without scrolling. Additive to
// sd_progress (readModules map + a daily reading tracker with its own streak);
// never touches blitz XP/streak, so the quiz economy is unaffected. Returns true
// on the first-ever read of that path (so callers can celebrate lightly if they want).
function markModuleRead(path) {
  const p = state.progress; if (!p || !path) return false;
  if (!p.readModules) p.readModules = {};
  if (!p.reading) p.reading = { day: null, count: 0, streak: 0, longest: 0, todayKeys: {} };
  const t = todayISO(), r = p.reading;
  if (r.day !== t) {                                 // roll the reading day + streak
    const y = new Date(t + "T00:00:00"); y.setDate(y.getDate() - 1);
    r.streak = r.day === y.toLocaleDateString("en-CA") ? (r.streak || 0) + 1 : 1;
    r.longest = Math.max(r.longest || 0, r.streak);
    r.day = t; r.count = 0; r.todayKeys = {};
  }
  const firstEver = !p.readModules[path];
  if (firstEver) p.readModules[path] = t;
  if (!r.todayKeys) r.todayKeys = {};
  if (!r.todayKeys[path]) { r.todayKeys[path] = 1; r.count = (r.count || 0) + 1; }
  safeSet("sd_progress", JSON.stringify(p));
  return firstEver;
}
// Called from the reader-body scroll handler (and once after render for pages
// that fit without scrolling). Fires markModuleRead at most once per page open.
function maybeMarkRead(path, body) {
  if (!path || reader._read) return;
  const ratio = body.scrollHeight > 0 ? (body.scrollTop + body.clientHeight) / body.scrollHeight : 1;
  if (ratio >= 0.9) { reader._read = true; markModuleRead(path); }
}
function isModuleRead(path) {
  return !!(state.progress && state.progress.readModules && state.progress.readModules[path]);
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
// [A2] opts.quiet (prime pretest): write ONLY review records — no section
// tallies, XP, streak, or history side effects.
function saveSessionLocal(session, opts = {}) {
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
    if (!opts.quiet) {
      const sec = (p.sections[res.section] = p.sections[res.section] || { seen: 0, correct: 0 });
      sec.seen += 1;
      sec.lastPlayed = session.date;
      if (res.status === "correct") { sec.correct += 1; correct += 1; }
      // Confidence calibration tallies (A4) — only first-attempt picks carry conf.
      if (res.conf === "high") { sec.sureSeen = (sec.sureSeen || 0) + 1; if (res.status === "correct") sec.sureCorrect = (sec.sureCorrect || 0) + 1; }
      else if (res.conf === "low") { sec.unsureSeen = (sec.unsureSeen || 0) + 1; if (res.status === "correct") sec.unsureCorrect = (sec.unsureCorrect || 0) + 1; }
    }
    if (res.id) {
      // [W1] quest fix: capture due-state BEFORE scheduleReview rewrites rv.due.
      const prior = reviews[res.id];
      const wasDue = !opts.quiet && prior && prior.due && prior.due <= session.date;
      const rv = prior || { ease: 2.5, interval: 0, reps: 0, lapses: 0 };
      rv.section = res.section; rv.module = res.module;
      // Slow correct -> schedule like low confidence; never double-shrink.
      let eff = res.conf;
      if (res.status === "correct" && medCorrect && res.ms > 2 * medCorrect) eff = "low";
      scheduleReview(rv, res.status, session.date, res.ms, eff);
      reviews[res.id] = rv;
      // A due review answered correctly counts toward this week's reviews quest.
      if (wasDue && res.status === "correct") {
        const wk = mostRecentFriday(session.date);
        if (!p.questClears || p.questClears.week !== wk) p.questClears = { week: wk, count: 0 };
        p.questClears.count++;
      }
    }
    if (res.confusion) tallyConfusion(p, res.confusion);   // [A2] confusion-pair tally (cap 50)
  }
  pruneReviews(p);                                        // [PERF] keep the review map under its cap as it grows
  if (opts.quiet) {                                        // [A2] side-effect-free save (prime)
    const saved = safeSet("sd_progress", JSON.stringify(p));
    return { xp: 0, freezeUsed: false, saved };
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
    comeback: !!session.comeback,                  // [B] comeback engine — see queueMoments/finish()
  });
  p.history = p.history.slice(-365);               // rolling one-year history cap
  const saved = safeSet("sd_progress", JSON.stringify(p));
  return { xp, freezeUsed, saved };
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
    // [E1] manually flagged items sort first, then oldest-due first.
    .sort((a, b) => (b[1].flagged ? 1 : 0) - (a[1].flagged ? 1 : 0) || (a[1].due < b[1].due ? -1 : 1));
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

/* ---------- [B] career ladder ---------- */
// Level bands -> a job title, cosmetic on top of levelFromXP. Bands repeat the
// same title until the next threshold (e.g. levels 6-9 are all "Mid-level Engineer").
const LEVEL_TITLES = [
  { at: 1, title: "Intern" }, { at: 3, title: "Junior Engineer" },
  { at: 6, title: "Mid-level Engineer" }, { at: 10, title: "Senior Engineer" },
  { at: 15, title: "Staff Engineer" }, { at: 21, title: "Senior Staff" },
  { at: 28, title: "Principal" }, { at: 36, title: "Distinguished" }, { at: 45, title: "Fellow" },
];
function titleForLevel(lvl) {
  let t = LEVEL_TITLES[0].title;
  for (const b of LEVEL_TITLES) { if (lvl >= b.at) t = b.title; else break; }
  return t;
}
// Progress ring in the topbar Lv chip: fraction of the way through the current level.
function updateLevelRing() {
  const ring = el("#lvlRingFg");
  if (!ring) return;
  const pct = ((state.progress.totalXP || 0) % 250) / 250;
  const r = 9, circ = 2 * Math.PI * r;
  ring.setAttribute("stroke-dasharray", circ.toFixed(1));
  ring.setAttribute("stroke-dashoffset", (circ * (1 - pct)).toFixed(1));
  // Give the chip a spoken meaning: the ring's fill is otherwise tooltip-only.
  const chip = el("#lvlChip");
  if (chip) {
    const lvl = levelFromXP(state.progress.totalXP);
    chip.setAttribute("role", "img");
    chip.setAttribute("aria-label", `Level ${lvl} — ${titleForLevel(lvl)}. ${Math.round(pct * 100)}% to next level`);
  }
}

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
  set("#lvlTitle", titleForLevel(levelFromXP(state.progress.totalXP)));  // [B] career ladder
  updateLevelRing();
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
  const rd = state.progress.reading || {};
  const readToday = rd.day === todayISO() ? (rd.count || 0) : 0;
  const readChip = readToday > 0
    ? ` <span class="read-chip" title="Modules you've read today${rd.streak > 1 ? ` · ${rd.streak}-day reading streak` : ""}">${ICON("scroll", "i-scroll")} ${readToday} read${rd.streak > 1 ? ` &middot; ${rd.streak}d` : ""}</span>` : "";
  const streakLine = (streak > 0
    ? `You're on a <b>${streak}-day</b> streak. Keep it alive.${freezeBit}`
    : `Start your streak today &mdash; just 5 minutes.${freezeBit}`) + readChip;
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
  /* [D] coach: reboarding protocol — a >2-day gap replaces the suggested card
     and suppresses the separate due-review card below (its CTA already covers it). */
  const reboard = reboardingInfo();
  // [E1] review backlog plan: a rounds-to-clear estimate + up to 3 per-section
  // chips + a "+N due tomorrow" whisper, so the backlog reads as a plan, not
  // just a number.
  let reviewCard = "";
  if (due.length && !reboard) {
    const rounds = Math.ceil(due.length / deckLen());
    const bySec = {};
    due.forEach(([, r]) => { bySec[r.section] = (bySec[r.section] || 0) + 1; });
    const chips = Object.entries(bySec).sort((a, b) => b[1] - a[1]).slice(0, 3)
      .map(([s, n]) => `<span class="due-chip">${esc(label(s))} <b>${n}</b></span>`).join("");
    const tomorrow = toISO(isoAdd(new Date(todayISO() + "T00:00:00"), 1));
    const dueTomorrow = Object.values(state.progress.reviews || {}).filter((r) => r.due === tomorrow).length;
    reviewCard = `<button class="review-card" id="reviewBtn">
         <div><div class="eyebrow good">Spaced repetition</div>
         <h2>${due.length} due &mdash; ${rounds} round${rounds === 1 ? "" : "s"} clears it</h2>
         <p class="msg">Resurface what you've missed before it fades.</p>
         ${chips ? `<div class="due-chips">${chips}</div>` : ""}
         ${dueTomorrow ? `<p class="due-tomorrow">+${dueTomorrow} due tomorrow</p>` : ""}</div>
         <span class="review-go">Review &rarr;</span>
       </button>`;
  }
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
  /* [A2] confusion drill card: the pair you mix up most (count >= 3) */
  const conf = state.progress.confusions || {};
  let topPair = null;
  for (const [k, n] of Object.entries(conf)) if (n >= 3 && (!topPair || n > topPair.n)) topPair = { k, n };
  let confCard = "";
  if (topPair) {
    const [ma, mb] = topPair.k.split("|");
    confCard = `<button class="review-card confuse" id="confuseBtn">
         <div><div class="eyebrow warn">Confusion spotted</div>
         <h2>You keep mixing up ${esc(modDisplay(ma))} and ${esc(modDisplay(mb))}</h2>
         <p class="msg">Missed ${topPair.n}&times; by picking one when the other was right &mdash; drill the pair.</p></div>
         <span class="review-go warn">Drill &rarr;</span>
       </button>`;
  }
  /* [A2] fading-this-week card: >=5 questions due within 7 days whose module retention < 0.5 */
  const mstats = moduleStats(state.progress);
  const t7 = (() => { const d = new Date(todayISO() + "T00:00:00"); d.setDate(d.getDate() + 7); return d.toLocaleDateString("en-CA"); })();
  const fadingIds = [], fadingMods = new Set();
  for (const [id, rv] of Object.entries(state.progress.reviews || {})) {
    if (!rv.due || rv.due > t7) continue;
    const ms = mstats[rv.module];
    if (ms && ms.retention < 0.5) { fadingIds.push(id); fadingMods.add(rv.module); }
  }
  const fadingCard = fadingIds.length >= 5
    ? `<button class="review-card fading" id="fadingBtn">
         <div><div class="eyebrow good">Spaced repetition</div>
         <h2>Fading this week: ${fadingIds.length} questions across ${fadingMods.size} modules</h2>
         <p class="msg">Retention is slipping on these. Refresh them before they're gone.</p></div>
         <span class="review-go">Refresh &rarr;</span>
       </button>`
    : "";
  const secs = state.index.sections, p = state.progress;
  // [E2] mastery-delta shine: compare each section's accuracy to the snapshot
  // written on the previous Home render; a changed tile runs the shine once.
  let lastMastery = {};
  try { lastMastery = JSON.parse(localStorage.getItem("sd_last_mastery")) || {}; } catch { /* first visit */ }
  const curMastery = {};
  const tiles = Object.keys(secs).sort().map((s) => {
    const st = (p.sections && p.sections[s]) || { seen: 0, correct: 0 };
    const acc = st.seen ? Math.round((st.correct / st.seen) * 100) : null;
    if (acc !== null) curMastery[s] = acc;
    const deltaClass = (acc !== null && lastMastery[s] != null && lastMastery[s] !== acc) ? " mastery-delta" : "";
    const bar = acc === null ? "" : `<span class="tbar"><i style="width:${acc}%"></i></span>`;
    const passChip = (p.awards && (p.awards["interview_" + s] || p.awards["panel_" + s])) ? `<span class="c-chip pass sm">Passed</span>` : "";  // [C] interviewer plaque
    return `<button class="tile ${s === section ? "suggested" : ""}${deltaClass}" data-section="${s}">
        <span class="tname">${esc(label(s))}${passChip}</span>
        <span class="tmeta">${secs[s]} Qs &middot; ${acc === null ? "new" : acc + "% mastery"}</span>
        ${bar}
      </button>`;
  }).join("");
  safeSet("sd_last_mastery", JSON.stringify(curMastery));
  const dateLine = new Date().toLocaleDateString("en-US", { weekday: "long", month: "long", day: "numeric" });
  /* [D] coach: the suggested card becomes either the reboarding card (calm,
     non-red — "the reviews kept your place") or the normal coach-voice card
     (eyebrow "Coach · <why-chip>", message = state.today.message). */
  const whyChip = state.today && WHY_CHIP[state.today.why];
  const topicCardHTML = reboard ? `
    <div class="topic-card reboard">
      <div class="eyebrow">Coach</div>
      <h2>It&rsquo;s been ${reboard.days} days.</h2>
      <p class="msg">Doesn&rsquo;t matter &mdash; the reviews kept your place. ${reboard.dueCount
        ? `${reboard.dueCount} ${reboard.dueCount === 1 ? "is" : "are"} waiting. The oldest ${deckLen()} take about ${estMinutes(deckLen())} minutes.`
        : `Nothing waiting &mdash; ${esc(label(section))} picks up the rotation.`}</p>
      <button class="cta" id="startBtn">${reboard.dueCount ? "Start review" : `Start &mdash; ${deckLen()} questions`}<small>${reboard.dueCount ? `${Math.min(reboard.dueCount, deckLen())} questions &middot; ~${estMinutes(deckLen())} min` : `~${estMinutes(deckLen())} min &middot; ${deckMode() === "flash" ? "flashcards" : "multiple choice"}`}</small></button>
    </div>` : `
    <div class="topic-card" data-coach-tpl="${state.today ? esc(state.today.templateId || "") : ""}">
      <div class="eyebrow">${whyChip ? `Coach &middot; ${esc(whyChip)}` : "Suggested for today"}</div>
      <h2>${esc(label(section))}</h2>
      <p class="msg">${esc(coachMsg || `${deckLen()} questions pulled from your ${label(section)} notes.`)}</p>
      <button class="cta" id="startBtn">Start &mdash; ${deckLen()} questions<small>~${estMinutes(deckLen())} min &middot; ${deckMode() === "flash" ? "flashcards" : "multiple choice"}</small></button>
    </div>`;
  /* [D] coach: Debrief-ready card + live quest chips */
  const debriefCard = debriefReady() ? `
    <button class="review-card debrief" id="debriefBtn">
      <div><div class="eyebrow">Coach</div>
      <h2>Debrief ready</h2>
      <p class="msg">Your week, summarized &mdash; deltas, a held-memory highlight, three quests for next week.</p></div>
      <span class="review-go">Open &rarr;</span>
    </button>` : "";
  const questRow = questChipsHTML(ensureQuests());
  app.innerHTML = `
    <div class="hero">
      <div class="hero-row">${goalRing()}<div>
        <div class="eyebrow date-eyebrow">${esc(dateLine)}</div>
        <h1>Today's 5-minute blitz</h1><p>${streakLine}</p></div></div>
    </div>
    ${skylineSVG(p)}
    ${gauntletCardHTML()}
    ${topicCardHTML}
    ${questRow}
    ${resumeCard}
    ${debriefCard}
    ${reviewCard}
    ${confCard}
    ${fadingCard}
    ${weakCard}
    ${rustyNote}
    <div class="section-head-row">
      <h2 class="section-h">Or pick a section &mdash; then choose sub-topics</h2>
      <button class="c-codex-link" id="codexLink">The Codex &rarr;</button>
    </div>
    <div class="grid">${tiles}</div>`;
  el("#startBtn").addEventListener("click", () => (reboard && reboard.dueCount ? startReview() : startBlitz(section)));
  /* [C] gauntlet + codex entry points */
  const gauntBtn = el("#gauntBtn"); if (gauntBtn) gauntBtn.addEventListener("click", () => go("#/gauntlet"));
  el("#codexLink").addEventListener("click", () => go("#/codex"));
  if (resume) el("#resumeBtn").addEventListener("click", resumeDeck);
  if (due.length && !reboard) el("#reviewBtn").addEventListener("click", startReview);
  if (topPair) el("#confuseBtn").addEventListener("click", () => startConfusionDrill(topPair.k));   // [A2]
  if (fadingCard) el("#fadingBtn").addEventListener("click", () => startRefresh(fadingIds));        // [A2]
  if (worst) el("#weakBtn").addEventListener("click", startWeakSpots);
  if (rusty) el("#rustyBtn").addEventListener("click", () => startBlitz(rusty.s));
  if (debriefCard) el("#debriefBtn").addEventListener("click", () => go("#/debrief"));
  document.querySelectorAll(".tile").forEach((b) => {
    b.addEventListener("click", () => go("#/topics/" + b.dataset.section));
    // [E1] warm the bank cache on intent (hover/focus), before the click that needs it.
    b.addEventListener("pointerenter", () => prefetchBank(b.dataset.section), { once: true });
    b.addEventListener("focus", () => prefetchBank(b.dataset.section), { once: true });
  });
  wireReveals();
}

/* ---------- [E1] friendly error screens + loading skeletons ---------- */
// Full-app dead-end replacement for a bare `.error` div: a welcoming headline,
// [Try again] (re-invokes the exact loader closure that failed) and [Home];
// any developer command is demoted into a collapsed <details> so the message
// stays readable for a non-maintainer.
const devDetail = (innerHTML) => `<details class="error-detail"><summary>For the maintainer</summary>${innerHTML}</details>`;
function errorScreen(title, hint, retryFn) {
  app.innerHTML = `<div class="error-screen">
      <div class="error-title">${esc(title)}</div>
      <p class="error-hint">${hint}</p>
      <div class="row">
        <button class="primary" id="errRetry">Try again</button>
        <button class="ghost" id="errHome">Home</button>
      </div>
    </div>`;
  el("#errRetry").addEventListener("click", retryFn);
  el("#errHome").addEventListener("click", () => go("#/home"));
}

// Glass placeholder blocks with a shimmer pulse (static under reduced motion),
// replacing the bare ".loading" spinner on quiz/topics/study entry points —
// the loads a slow/cold bank fetch actually delays.
function skeletonHTML(kind) {
  const st = REDUCED() ? " static" : "";
  const block = (cls) => `<div class="sk-block ${cls}${st}"></div>`;
  if (kind === "quiz") {
    return `<div class="skeleton sk-quiz">${block("sk-head")}${block("sk-qtext")}${block("sk-opt")}${block("sk-opt")}${block("sk-opt")}${block("sk-opt")}</div>`;
  }
  if (kind === "topics") {
    return `<div class="skeleton sk-topics">${block("sk-hero")}${Array.from({ length: 6 }, () => block("sk-row")).join("")}</div>`;
  }
  return `<div class="skeleton sk-study">${block("sk-hero")}<div class="sk-grid">${Array.from({ length: 8 }, () => block("sk-tile")).join("")}</div></div>`;
}

/* ---------- [E1] bank prefetch ---------- */
// Fire-and-forget loadBank() on Home tile hover/focus, once per section per
// session; plus an idle-time prefetch of today's suggested section right
// after boot. Skipped entirely on a metered connection (Save-Data).
const _prefetched = new Set();
function prefetchBank(section) {
  if (!section || _prefetched.has(section) || navigator.connection?.saveData) return;
  _prefetched.add(section);
  loadBank(section);
}

/* ---------- [E1] no-repeat sampling ---------- */
// Per-section ring buffer of the last 30 served question ids (sd_recent_<section>)
// so back-to-back blitzes in the same section don't keep re-serving the same
// handful. Exempt: gauntlet/review/weak/drill/refresh — their selection is
// already meaningful (spaced repetition, confusion pairs, weak spots).
const recentKey = (section) => "sd_recent_" + section;
function loadRecent(section) {
  try { return JSON.parse(localStorage.getItem(recentKey(section))) || []; } catch { return []; }
}
function pushRecent(section, ids) {
  const next = [...loadRecent(section), ...ids].slice(-30);
  safeSet(recentKey(section), JSON.stringify(next));
}

/* ---------- bank loading / sub-topic picker ---------- */
const bankCache = {};
const BANK_CACHE_CAP = 8;   // [PERF] parsed banks are multi-MB; cap retained sections (bounded at 15, heavy on Android)
async function loadBank(section) {
  // "default" cache lets a 304 revalidate these multi-MB files instead of
  // re-downloading them on every boot.
  if (!bankCache[section]) {
    const bank = await fetchJSON(`questions/${section}.json`, null, "default");
    // extract.py emits moduleName straight from the directory slug, so it
    // arrives lowercase ("cap theorem"). Case it once here rather than at the
    // ~18 places that render it. The stored JSON is untouched, so question ids
    // and spaced-repetition state are unaffected.
    if (Array.isArray(bank)) for (const q of bank) if (q.moduleName) q.moduleName = titleize(q.moduleName);
    capInsert(bankCache, section, bank, BANK_CACHE_CAP, (s) => { delete _bankById[s]; });   // [PERF] evict the paired id-map too
  }
  return bankCache[section];
}

// [CS] Separate case-study Q&A pool (game/case_questions/<section>.json), NEVER
// merged into the main bank — it feeds ONLY the reader's bottom-of-file "Quiz this
// topic" on a case-study page. A missing pool caches as [] (no refetch).
const caseBankCache = {};
async function loadCaseBank(section) {
  if (!caseBankCache[section]) {
    const bank = await fetchJSON(`case_questions/${section}.json`, null, "default");
    if (Array.isArray(bank)) for (const q of bank) if (q.moduleName) q.moduleName = titleize(q.moduleName);
    capInsert(caseBankCache, section, Array.isArray(bank) ? bank : [], 4);
  }
  return caseBankCache[section];
}
// A case-study module id is "<section>/case_studies[/<study>]" — used to route a
// quiz to the case pool instead of the main bank.
const isCaseModule = (m) => /(^|\/)case_studies(\/|$)/.test(m || "");

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

// [PERF] Same shape as modulesOf, but built from the boot-loaded index
// (moduleCounts) instead of the multi-MB question bank — so the Study skill tree
// can render without downloading the bank. Display name is titleize(slug), byte-
// identical to the bank's moduleName (extract.py sets moduleName = slug too).
function modulesFromIndex(section) {
  const counts = (state.index && state.index.moduleCounts) || {};
  const order = STUDY_ORDER[section] || [];
  return Object.keys(counts)
    .filter((m) => m.split("/")[0] === section)
    .map((m) => ({ module: m, name: titleize(m.split("/").pop()), count: counts[m] }))
    .sort((a, b) => {
      const ai = order.indexOf(a.module), bi = order.indexOf(b.module);
      return (ai === -1 ? 9999 : ai) - (bi === -1 ? 9999 : bi);
    });
}

// [CS] Ordered case studies for a section from index.caseStudies (extract.py,
// README-curated). Each entry is { file: reader path, name: display title }.
// Powers the read-only "Case Studies" study track (third path beside Full/Interview).
function caseStudiesFromIndex(section) {
  return ((state.index && state.index.caseStudies) || {})[section] || [];
}

async function openTopics(section) {
  app.innerHTML = skeletonHTML("topics");
  const bank = await loadBank(section);
  if (!bank || !bank.length) {
    errorScreen(`Couldn't load ${label(section)}`, `Check your connection and try again.${devDetail(`Run <code>python3 extract.py</code>.`)}`, () => openTopics(section));
    return;
  }
  const mods = modulesOf(bank);
  // [SF] Per-module sub-file breakdown, derived straight from the bank: a module
  // qualifies for a sub-file selector only when its questions come from more than
  // one file (README + deep-dives). No extract.py change — sourceFile and the counts
  // already live in the bank and module ids are never re-keyed, so spaced-repetition
  // state is untouched. Serves BOTH quiz and flashcard mode (one shared picker).
  const subLabel = (f) => titleize(f.replace(/\.md$/i, ""));
  const subMap = new Map();                          // module -> [{ file, name, count }] (README first)
  {
    const per = new Map();                            // module -> Map<file, count>
    for (const q of bank) {
      const f = q.sourceFile || "README.md";
      let mm = per.get(q.module); if (!mm) { mm = new Map(); per.set(q.module, mm); }
      mm.set(f, (mm.get(f) || 0) + 1);
    }
    const fileTree = (state.index && state.index.files) || {};
    for (const [mod, mm] of per) {
      if (mm.size <= 1) continue;                     // only one file -> no selector needed
      // Order sub-files by the curated learning sequence (file-tree order from the
      // parent README's links), not alphabetically — matches the reader/Study order.
      const order = fileTree[mod] || [];
      const rank = (f) => { const i = order.indexOf(f); return i === -1 ? 9999 : i; };
      subMap.set(mod, [...mm.entries()].map(([file, count]) => ({ file, count, name: subLabel(file) }))
        .sort((a, b) => rank(a.file) - rank(b.file) || a.name.localeCompare(b.name)));
    }
  }
  const rows = mods.map((m) => {
    const subs = subMap.get(m.module);
    if (!subs) {
      return `<label class="modrow filterrow"><input type="checkbox" class="modcheck" value="${esc(m.module)}" checked />
       <span class="mname">${esc(m.name)}</span><span class="mcount">${m.count}</span></label>`;
    }
    const subRows = subs.map((s) =>
      `<label class="subrow"><input type="checkbox" class="subcheck" data-mod="${esc(m.module)}" value="${esc(s.file)}" checked />
         <span class="sname">${esc(s.name)}</span><span class="scount">${s.count}</span></label>`).join("");
    return `<div class="modrow-wrap filterrow" data-mod="${esc(m.module)}">
        <div class="modrow modrow-parent">
          <label class="modrow-lbl"><input type="checkbox" class="modcheck" value="${esc(m.module)}" checked />
            <span class="mname">${esc(m.name)}</span></label>
          <button type="button" class="subtoggle" aria-expanded="false" aria-label="Show ${subs.length} sub-files of ${esc(m.name)}"><span class="subtoggle-count">${m.count}</span><span class="subchev" aria-hidden="true">&#9662;</span></button>
        </div>
        <div class="sublist" hidden>${subRows}</div>
      </div>`;
  }).join("");
  // [E1] session length: 5/10/20 segmented control, persisted as sd_deck_len.
  const lenOpts = [5, 10, 20].map((n) =>
    `<button class="lenopt${deckLen() === n ? " on" : ""}" role="radio" aria-checked="${deckLen() === n}" data-len="${n}">${n}</button>`).join("");
  app.innerHTML = `
    <div class="hero"><h1>${esc(label(section))}</h1><p>Pick the sub-topics to drill &mdash; or keep them all.</p></div>
    <div class="topicbar">
      <button class="ghost" id="allBtn">Select all</button>
      <button class="ghost" id="noneBtn">Clear</button>
      <input type="search" class="filter" id="modFilter" placeholder="Filter topics" aria-label="Filter topics" />
      <span class="selcount" id="selCount"></span>
    </div>
    <div class="lenbar" role="radiogroup" aria-label="Session length">
      <span class="lenbar-label">Session length</span>${lenOpts}
    </div>
    <div class="modlist">${rows}</div>
    <div class="qactions">
      <button class="ghost" id="backBtn">&larr; Back</button>
      <button class="cta inline" id="startSel">Start blitz</button>
    </div>`;
  const checks = () => [...document.querySelectorAll(".modcheck")];
  const selected = () => checks().filter((c) => c.checked).map((c) => c.value);
  const subBoxes = (mod) => [...document.querySelectorAll(`.subcheck[data-mod="${mod}"]`)];
  // [SF] whole-module UNLESS a module is partially selected -> then its checked
  // sub-files become the "<module>|<file>" allow-list. Every sub-file checked ⇒
  // contributes nothing (overall null), so an untouched picker behaves exactly as before.
  const selectedSourceFiles = () => {
    const out = [];
    for (const [mod, subs] of subMap) {
      const parent = document.querySelector(`.modcheck[value="${mod}"]`);
      if (!parent || !parent.checked) continue;
      const on = subBoxes(mod).filter((b) => b.checked);
      if (on.length && on.length < subs.length) for (const b of on) out.push(mod + "|" + b.value);
    }
    return out.length ? out : null;
  };
  const syncParent = (mod) => {                       // reflect child checkbox state onto the parent
    const parent = document.querySelector(`.modcheck[value="${mod}"]`);
    const boxes = subBoxes(mod);
    if (!parent || !boxes.length) return;
    const on = boxes.filter((b) => b.checked).length;
    parent.indeterminate = on > 0 && on < boxes.length;
    parent.checked = on > 0;
  };
  const updateCount = () => {
    const sel = selected();
    let n = 0;
    for (const m of mods) {
      if (!sel.includes(m.module)) continue;
      const subs = subMap.get(m.module);
      if (!subs) { n += m.count; continue; }
      const on = new Set(subBoxes(m.module).filter((b) => b.checked).map((b) => b.value));
      for (const s of subs) if (on.has(s.file)) n += s.count;
    }
    el("#selCount").textContent = `${sel.length} topic${sel.length === 1 ? "" : "s"} · ${n} questions`;
    el("#startSel").disabled = sel.length === 0 || n === 0;
  };
  checks().forEach((c) => c.addEventListener("change", () => {
    if (subMap.has(c.value)) {                         // parent toggles every sub-file under it
      subBoxes(c.value).forEach((b) => (b.checked = c.checked));
      c.indeterminate = false;
    }
    updateCount();
  }));
  document.querySelectorAll(".subcheck").forEach((b) =>
    b.addEventListener("change", () => { syncParent(b.dataset.mod); updateCount(); }));
  document.querySelectorAll(".subtoggle").forEach((btn) => btn.addEventListener("click", () => {
    const wrap = btn.closest(".modrow-wrap");
    const list = wrap.querySelector(".sublist");
    const opening = list.hidden;
    list.hidden = !opening;
    btn.setAttribute("aria-expanded", opening ? "true" : "false");
    wrap.classList.toggle("open", opening);
  }));
  const setAll = (val) => {
    checks().forEach((c) => { c.checked = val; c.indeterminate = false; });
    document.querySelectorAll(".subcheck").forEach((b) => (b.checked = val));
    updateCount();
  };
  el("#allBtn").addEventListener("click", () => setAll(true));
  el("#noneBtn").addEventListener("click", () => setAll(false));
  el("#modFilter").addEventListener("input", () => {
    const f = el("#modFilter").value.trim().toLowerCase();
    document.querySelectorAll(".filterrow").forEach((r) =>
      (r.style.display = r.querySelector(".mname").textContent.toLowerCase().includes(f) ? "" : "none"));
  });
  document.querySelectorAll(".lenopt").forEach((b) => b.addEventListener("click", () => {
    safeSet("sd_deck_len", b.dataset.len);
    document.querySelectorAll(".lenopt").forEach((x) => { x.classList.toggle("on", x === b); x.setAttribute("aria-checked", x === b ? "true" : "false"); });
    el(".lenbar")?._radioSync?.();   // re-sync roving tabindex to the new checked option
    announce(`Session length set to ${b.dataset.len} questions.`);
  }));
  wireRadioGroup(el(".lenbar"));
  el("#backBtn").addEventListener("click", () => go("#/home"));
  el("#startSel").addEventListener("click", () => startBlitz(section, selected(), selectedSourceFiles()));
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
// [A2] Flip-round options: the prompt becomes the ANSWER and the options are
// QUESTION texts — the item's own question (correct) plus the 3 questions behind
// its distractorIds. Same src semantics as optionsFor (0 = correct, i+1 =
// distractorIds[i]) so snapshot optOrder replay works unchanged. Returns null
// unless all 3 resolve to distinct real questions.
function flipOptsFor(q) {
  const byId = bankById(q.section);
  if (!byId || !Array.isArray(q.distractorIds) || q.distractorIds.length !== 3) return null;
  const srcs = q.distractorIds.map((id) => byId.get(id));
  if (!srcs.every((s) => s && s.id !== q.id)) return null;
  const texts = new Set([q.question, ...srcs.map((s) => s.question)]);
  if (texts.size !== 4) return null;                 // duplicate question text -> unusable
  return [{ src: 0, t: q.question, md: q.questionMd, ok: true },
    ...srcs.map((s, i) => ({ src: i + 1, t: s.question, md: s.questionMd, ok: false }))];
}
// optOrder (from a resume snapshot) rebuilds the same option order without a
// reshuffle; a stale/mismatched order falls back to a fresh shuffle.
function makeItem(q, optOrder, flip) {
  const fbase = flip ? flipOptsFor(q) : null;        // [A2] unresolvable flip -> normal MCQ
  const base = fbase || optionsFor(q);
  let opts;
  if (optOrder && optOrder.length === base.length) {
    const bySrc = new Map(base.map((o) => [o.src, o]));
    opts = optOrder.map((s) => bySrc.get(s));
    if (opts.some((o) => !o)) opts = shuffle(base);
  } else {
    opts = shuffle(base);
  }
  return { q, opts, optOrder: opts.map((o) => o.src), status: "pending", boss: false, flip: !!fbase };
}

// Quiz vs flashcard is a global, persisted preference toggled from the top bar.
function deckMode() { return localStorage.getItem("sd_mode") === "flash" ? "flash" : "quiz"; }

// [B] Reset every per-deck Phase B tracker (boss intro, double-down, comeback
// engine, flashcard recall streak, time-capsule returns). Called at the start
// of every fresh deck and on resume — a resumed deck restarts these counters,
// which only cost a little mid-deck polish, never persisted progress.
function resetPhaseBState() {
  state._bossIntroShown = false;
  state._doubleDown = null; state._doubleDownIdx = null; state._doubleDownWager = 0;
  state._missStreak = 0; state._maxMissStreak = 0; state._answerLog = [];
  state._cardStreak = 0;
  state._capsuleReturns = [];
}

function startDeck(questions, replayFn, opts = {}) {
  state.prime = !!opts.prime;                    // [A2] pretest deck: no XP/combo/snapshot/confidence
  state.mode = opts.prime ? "quiz" : deckMode(); // [A2] prime always uses the MCQ path
  if (opts.keepOrder) state.mode = "quiz";       // [C] gauntlet/interview always run the MCQ engine
  state.hard = !!opts.hard;
  state.awaitingConf = false;
  state._medMs = medianReviewMs();
  resetPhaseBState();
  const flipSet = state.mode === "flash" ? null : opts.flip;   // [A2] Set of qids to flip
  const ilv = opts.keepOrder ? null : opts.interleave;         // [A2] graph pairs — [C] never reorder a gauntlet/interview recipe
  const items = questions.map((q) => makeItem(q, null, flipSet && flipSet.has(q.id)));
  if (opts.keepOrder) {
    state.deck = items;                          // [C] the recipe IS the arc — no shuffle, no interleave, no boss partition
  } else if (state.mode === "flash") {
    state.deck = shuffle(items);                 // no boss ordering for self-grade cards
    if (ilv) state.deck = orderInterleaved(state.deck, ilv);
  } else if (state.hard) {
    state.deck = shuffle(items);                 // recall-first review: plain shuffle, no boss
    if (ilv) state.deck = orderInterleaved(state.deck, ilv);
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
    let normal = items.filter((it) => !bossSet.has(it));
    const boss = items.filter((it) => bossSet.has(it));
    boss.forEach((it) => (it.boss = true));
    // [A2] confusion-aware interleave on the NON-BOSS prefix only — boss items
    // stay last, so pulling them out can't re-cluster same-module questions.
    if (ilv) normal = orderInterleaved(normal, ilv);
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

// [SF] sourceFiles: optional allow-list of "<module>|<sourceFile>" composite keys
// that narrows a whole-module selection down to specific deep-dive sub-files. Only
// modules that appear in the allow-list are constrained; every other selected module
// passes through wholesale — so a mixed selection (some modules whole, one module
// scoped to a sub-file) resolves correctly. null/empty ⇒ classic whole-module behavior.
// [SF] limit: optional deck-size cap that overrides the 5/10/20 deckLen() pref.
// The per-file quiz uses it to run ALL of a sub-file's questions (limit = pool size)
// vs. a quick random subset (limit = null ⇒ deckLen()). null everywhere else.
async function startBlitz(section, modules, sourceFiles = null, limit = null) {
  app.innerHTML = skeletonHTML("quiz");
  // [CS] Quizzing a case study (reader "Quiz this topic") sources from the separate
  // case pool, never the main bank — so case studies stay out of every other path.
  const fromCases = (modules || []).some(isCaseModule);
  let bank = await (fromCases ? loadCaseBank(section) : loadBank(section));
  if (!bank || !bank.length) {
    errorScreen(`Couldn't load ${label(section)}`, `Check your connection and try again.${devDetail(`Run <code>python3 extract.py</code>.`)}`, () => startBlitz(section, modules, sourceFiles, limit));
    return;
  }
  if (modules && modules.length) bank = bank.filter((q) => modules.includes(q.module));
  if (sourceFiles && sourceFiles.length) {
    const sf = new Set(sourceFiles);
    const constrained = new Set(sourceFiles.map((k) => k.slice(0, k.lastIndexOf("|"))));
    bank = bank.filter((q) => !constrained.has(q.module) || sf.has(q.module + "|" + (q.sourceFile || "README.md")));
  }
  state.section = section;
  state.modules = modules && modules.length ? modules : null;
  state.sourceFiles = sourceFiles && sourceFiles.length ? sourceFiles : null;
  state.limit = limit || null;
  // [E1] no-repeat sampling: prefer questions NOT in the last-30-served ring
  // buffer for this section; only top up from the recent set if the fresh
  // pool runs short (small module selections, tiny sections).
  const recent = new Set(loadRecent(section));
  const fresh = shuffle(bank.filter((q) => !recent.has(q.id)));
  const stale = shuffle(bank.filter((q) => recent.has(q.id)));
  const picked = [...fresh, ...stale].slice(0, limit || deckLen());
  pushRecent(section, picked.map((q) => q.id));
  // [A2] confusion-aware interleaving for multi-module decks: no two consecutive
  // questions share a module; graph-connected topics sit adjacent (A-B-A'-C).
  let interleave;
  if (new Set(picked.map((q) => q.module)).size > 1) {
    const g = await loadGraph(section);
    interleave = (g && g.pairs) || [];
  }
  startDeck(picked, () => startBlitz(section, state.modules, state.sourceFiles, state.limit), interleave ? { interleave } : {});
}

async function startReview() {
  app.innerHTML = skeletonHTML("quiz");
  const due = dueReviews().slice(0, deckLen() + 4);
  const bySec = {};
  due.forEach(([id, r]) => (bySec[r.section] = bySec[r.section] || []).push(id));
  const items = [];
  let orphaned = 0, loadFailed = false;
  for (const sec of Object.keys(bySec)) {
    const bank = await loadBank(sec);
    if (!bank) { loadFailed = true; continue; }
    const byId = new Map(bank.map((q) => [q.id, q]));
    if (bySec[sec].some((id) => id.includes("case_studies"))) {   // [CS] resolve case-study review misses
      for (const q of await loadCaseBank(sec)) byId.set(q.id, q);
    }
    for (const id of bySec[sec]) {
      const q = byId.get(id);
      if (q) items.push(q);
      // Orphaned review (question no longer in the bank): self-heal so the due
      // count stops advertising questions that can never be served again.
      else { delete state.progress.reviews[id]; orphaned++; }
    }
  }
  // [E1] the self-heal above used to delete silently — persist it and surface a
  // one-time-per-session toast so the count isn't a mystery drop.
  if (orphaned) {
    safeSet("sd_progress", JSON.stringify(state.progress));
    if (!state._orphanToastShown) {
      state._orphanToastShown = true;
      showToast(`${orphaned} retired question${orphaned === 1 ? "" : "s"} removed from your queue.`);
    }
  }
  if (!items.length) {
    showToast(loadFailed ? "Couldn't load questions — check your connection." : "Nothing due right now.");
    renderHome(); return;
  }
  state.section = "review"; state.modules = null;
  const deck = items.slice(0, deckLen());
  // [A2] flip rounds: ~30% of review items (deterministic per day+qid) whose 3
  // distractorIds all resolve become answer->question flips. Works with the
  // hard-deck recall gate and the confidence step unchanged.
  const dateSeed = todayISO();
  const flipP = window.__a2ForceFlip ? 1 : 0.3;      // QA can force all-eligible via ?qa=1
  const flip = new Set();
  for (const q of deck) {
    if (mulberry32(cyrb53(dateSeed + q.id))() < flipP && flipOptsFor(q)) flip.add(q.id);
  }
  // [A2] cross-section interleave (by module only — no single graph spans sections).
  startDeck(deck, startReview, { hard: true, flip, interleave: [] });
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
  app.innerHTML = skeletonHTML("quiz");
  const weak = weakSections().filter((x) => x.acc < 0.7).slice(0, 4);
  const pool = (weak.length ? weak : weakSections().slice(0, 3));
  if (!pool.length) {
    showToast("Not enough data for weak spots yet — play a few rounds first.");
    renderHome(); return;
  }
  const reviews = state.progress.reviews || {};
  const banks = {}, byId = {};
  let loadFailed = false;
  for (const p of pool) {
    const b = await loadBank(p.s);
    if (b) { banks[p.s] = b; byId[p.s] = new Map(b.map((q) => [q.id, q])); }
    else loadFailed = true;
  }
  const items = [], seen = new Set();
  const add = (q) => { if (q && !seen.has(q.id)) { items.push(q); seen.add(q.id); } };
  // 1) known trouble questions in weak sections: most lapses first
  Object.entries(reviews)
    .filter(([, r]) => (r.lapses || 0) > 0 && byId[r.section])
    .sort((a, b) => (b[1].lapses || 0) - (a[1].lapses || 0))
    .forEach(([id, r]) => { if (items.length < deckLen()) add(byId[r.section].get(id)); });
  // 2) fill with random questions from the weak sections
  const filler = [];
  for (const p of pool) if (banks[p.s]) filler.push(...banks[p.s]);
  shuffle(filler).forEach((q) => { if (items.length < deckLen()) add(q); });
  if (!items.length) {
    showToast(loadFailed ? "Couldn't load questions — check your connection." : "Not enough data for weak spots yet — play a few rounds first.");
    renderHome(); return;
  }
  state.section = "weakspots"; state.modules = null;
  startDeck(items.slice(0, deckLen()), startWeakSpots, { hard: true });
}

/* ---------- [A2] confusion drill + fading refresh decks ---------- */
// Mixed deck of two mixed-up modules (module keys carry their section prefix, so
// the pair may span sections). Alternate the two modules so the contrast is
// front-and-center.
async function startConfusionDrill(key) {
  app.innerHTML = skeletonHTML("quiz");
  const items = [];
  let loadFailed = false;
  for (const mod of key.split("|")) {
    const bank = await loadBank(mod.split("/")[0]);
    if (bank) items.push(...bank.filter((q) => q.module === mod));
    else loadFailed = true;
  }
  if (!items.length) {
    showToast(loadFailed ? "Couldn't load questions — check your connection." : "No confusion pairs tracked yet.");
    renderHome(); return;
  }
  const picked = shuffle(items).slice(0, deckLen());
  state.section = "drill"; state.modules = null;
  startDeck(picked, () => startConfusionDrill(key), { interleave: [] });
}

// Exactly the fading question ids, capped at the session length; hard mode OFF.
async function startRefresh(ids) {
  app.innerHTML = skeletonHTML("quiz");
  const bySec = {};
  for (const id of ids) {
    const rv = (state.progress.reviews || {})[id];
    if (rv && rv.section) (bySec[rv.section] = bySec[rv.section] || []).push(id);
  }
  const items = [];
  let loadFailed = false;
  for (const sec of Object.keys(bySec)) {
    const bank = await loadBank(sec);
    if (!bank) { loadFailed = true; continue; }
    let byId = bankById(sec);
    if (bySec[sec].some((id) => id.includes("case_studies"))) {   // [CS] resolve case-study ids (clone: don't pollute the cached map)
      byId = new Map(byId);
      for (const q of await loadCaseBank(sec)) byId.set(q.id, q);
    }
    for (const id of bySec[sec]) { const q = byId.get(id); if (q) items.push(q); }
  }
  if (!items.length) {
    showToast(loadFailed ? "Couldn't load questions — check your connection." : "Nothing is fading right now.");
    renderHome(); return;
  }
  state.section = "refresh"; state.modules = null;
  startDeck(items.slice(0, deckLen()), () => startRefresh(ids), { interleave: [] });
}

/* ---------- [A2] prime: pretest before reading ---------- */
function primeEligible(m, bank) {
  if (Object.values(state.progress.reviews || {}).some((r) => r.module === m.module)) return false;  // already quizzed
  if (bank.filter((q) => q.module === m.module).length < 3) return false;
  let opt = 0; try { opt = +localStorage.getItem("sd_prime_opt") || 0; } catch { }
  return opt < 3;                                    // 3 "Just read" in a row -> stop offering
}
// Returns true if it took over navigation (showed the sheet); false -> caller reads now.
function maybePrime(section, m, bank, justRead) {
  if (!primeEligible(m, bank)) return false;
  showPrimeSheet(section, m, justRead);
  return true;
}
function showPrimeSheet(section, m, justRead) {
  if (el("#primeSheet")) return;
  const o = document.createElement("div");
  o.className = "pause-sheet"; o.id = "primeSheet";  // same glass-confirm pattern as the pause sheet
  o.setAttribute("role", "dialog"); o.setAttribute("aria-modal", "true"); o.setAttribute("aria-label", "Prime your brain");
  o.innerHTML = `<div class="pause-card">
      <h2>Prime your brain</h2>
      <p>3 quick guesses before you read. Nothing counts.</p>
      <div class="pause-btns">
        <button class="primary" id="primeGo">Prime me</button>
        <button class="ghost" id="primeSkip">Just read</button>
      </div>
    </div>`;
  document.body.appendChild(o);
  const release = trapFocus(o, { initial: "#primeGo" });
  const close = () => { release(false); o.remove(); };   // every prime close path navigates away
  el("#primeGo").addEventListener("click", () => {
    safeSet("sd_prime_opt", "0");   // engagement resets the opt-out
    close(); startPrime(section, m.module, m.name);
  });
  el("#primeSkip").addEventListener("click", () => {
    let opt = 0; try { opt = +localStorage.getItem("sd_prime_opt") || 0; } catch { }
    safeSet("sd_prime_opt", String(opt + 1));
    close(); justRead();
  });
  o.addEventListener("click", (e) => { if (e.target === o) { close(); justRead(); } });   // backdrop = just read
}
async function startPrime(section, module, moduleName) {
  const bank = await loadBank(section);
  if (!bank) { openReader(module, moduleName); return; }
  let pool = bank.filter((q) => q.module === module && q.difficulty === "core");
  if (pool.length < 3) pool = bank.filter((q) => q.module === module);   // fall back to any difficulty
  const picked = shuffle(pool.slice()).slice(0, 3);
  if (picked.length < 3) { openReader(module, moduleName); return; }
  state.section = "prime"; state.modules = null;
  startDeck(picked, null, { prime: true });
}
// After Q3: record the three as "learned" review seeds (quiet — no XP/streak/
// history/section tallies), then land in the reader at the module.
function finishPrime() {
  state.inQuiz = false; state.prime = false;
  const first = state.deck[0].q;
  const results = state.deck.map((d) => ({ id: d.q.id, section: d.q.section, module: d.q.module, status: "learned" }));
  saveSessionLocal({ date: todayISO(), section: "prime", results, bonusXp: 0 }, { quiet: true });
  refreshStats();
  history.replaceState(null, "", "#/study/" + first.section);   // Back from the reader lands on the path, not #/quiz/prime
  // Render the real underlayer before the reader opens: leaving the stale
  // prime screen mounted let its leftover Finish button re-record the pretest
  // as a graded session (XP/streak/SM-2 corruption) after the reader closed.
  openStudySection(first.section);
  openReader(first.module, first.moduleName);
}

/* ---------- [A2] explain-back ---------- */
// Optional "say it in your own words" on wrong reveals and flashcard reveals.
// The question side uses the bank's precomputed `concepts`; only the user's text
// runs through the ported tokenizer. Nothing is persisted.
const a2Explained = new Set();                       // qids that earned the bonus this session
function explainBackHTML(item) {
  if (!(item.q.concepts || []).length) return "";    // nothing to match against
  return `<details class="explain-back" data-qid="${esc(item.q.id)}">
      <summary>Say it in your own words (E)</summary>
      <div class="eb-body">
        <textarea class="eb-input" rows="3" placeholder="Explain the idea from memory&hellip;"></textarea>
        <button class="ghost eb-submit">Check my words</button>
        <div class="eb-result" aria-live="polite"></div>
      </div>
    </details>`;
}
function wireExplainBack(root, item) {
  const det = root.querySelector(".explain-back");
  if (!det) return;
  det.querySelector(".eb-submit").addEventListener("click", () => runExplainBack(det, item));
}
function runExplainBack(det, item) {
  const text = (det.querySelector(".eb-input").value || "").trim();
  const resEl = det.querySelector(".eb-result");
  const words = text.split(/\s+/).filter(Boolean);
  const concepts = (item.q.concepts || []).map((c) => String(c).toLowerCase());
  const userToks = new Set(a2Tokenize(text));
  const hit = concepts.filter((c) => userToks.has(c));
  const conceptSet = new Set(concepts);
  // Echo the user's own words with matched concept tokens glowing.
  const echoed = esc(text).replace(/[A-Za-z0-9]+/g, (w) =>
    conceptSet.has(w.toLowerCase()) ? `<span class="hit">${w}</span>` : w);
  // Show the model answer with the concept tokens the user MISSED glowing.
  const missed = new Set(concepts.filter((c) => !userToks.has(c)));
  const ans = esc(item.q.answerFull).replace(/[A-Za-z0-9]+/g, (w) =>
    missed.has(w.toLowerCase()) ? `<span class="missterm">${w}</span>` : w);
  let bonus = "";
  if (words.length >= 8 && !a2Explained.has(item.q.id)) {   // +5 XP once per question per session
    a2Explained.add(item.q.id);
    state.sessionXp += 5;
    bonus = ` &middot; <b>+5 XP</b>`;
    floatXP(5, det);
  }
  resEl.innerHTML = `<div class="eb-score">Key terms covered: ${hit.length}/${concepts.length}${bonus}</div>
      <div class="eb-echo">${echoed || "&mdash;"}</div>
      <div class="eb-ans"><b>Model answer:</b> ${ans}</div>`;
}

/* ---------- [E1] flag-for-review + copy question (reveal panel) ---------- */
// Two small icon buttons pinned to the top of any reveal panel (quiz + card).
function revealActionsHTML(item) {
  const flagged = !!(state.progress.reviews || {})[item.q.id]?.flagged;
  return `<div class="reveal-actions">
      <button class="ra-btn ra-flag${flagged ? " on" : ""}" ${flagged ? "disabled" : ""} title="${flagged ? "Flagged for review" : "Flag for review"}" aria-label="Flag for review">${ICON("star", "i-star")}</button>
      <button class="ra-btn ra-copy" title="Copy question and answer" aria-label="Copy question and answer">${ICON("copy", "i-copy")}</button>
    </div>`;
}
function wireRevealActions(root, item) {
  const flagBtn = root.querySelector(".ra-flag");
  if (flagBtn && !flagBtn.disabled) flagBtn.addEventListener("click", () => flagForReview(item, flagBtn));
  const copyBtn = root.querySelector(".ra-copy");
  if (copyBtn) copyBtn.addEventListener("click", () => copyQuestion(item, copyBtn));
}
// Sets (or creates, with normal SM-2 defaults) the review record's due date to
// today and flags it — dueReviews()/startReview() sort flagged items first.
function flagForReview(item, btn) {
  const p = state.progress;
  const reviews = (p.reviews = p.reviews || {});
  const rv = reviews[item.q.id] || { ease: 2.5, interval: 0, reps: 0, lapses: 0 };
  rv.due = todayISO(); rv.flagged = 1; rv.section = item.q.section; rv.module = item.q.module;
  reviews[item.q.id] = rv;
  safeSet("sd_progress", JSON.stringify(p));
  btn.classList.add("on"); btn.disabled = true; btn.title = "Flagged for review";
  announce("Flagged — it'll appear in your next review.");
}
function copyQuestion(item, btn) {
  const text = `Q: ${item.q.question}\nA: ${item.q.answerFull}`;
  navigator.clipboard?.writeText(text).then(() => {
    btn.classList.add("ok");
    setTimeout(() => btn.classList.remove("ok"), 1400);
  }).catch(() => announce("Couldn't copy — clipboard is blocked."));
}

/* ---------- session guard: pause / resume ---------- */
// A live deck is snapshotted to localStorage after every answer/skip/grade so a
// refresh (or navigating away) can resume the exact same blitz. optOrder makes
// the option arrangement pixel-identical; queue/cursor/combo/XP restore progress.
function saveDeckSnapshot() {
  if (!state.inQuiz || !state.deck.length) return;
  if (state.section === "interview") return;       // [C] an interview can't pause — leaving reschedules it
  if (state.prime) return;                           // [A2] prime pretests never snapshot
  const snap = {
    date: todayISO(), section: state.section, modules: state.modules, sourceFiles: state.sourceFiles, limit: state.limit, mode: state.mode, hard: state.hard,
    items: state.deck.map((d) => ({
      id: d.q.id, optOrder: d.optOrder, status: d.status, boss: d.boss, flip: d.flip || undefined,
      retry: d.retry, retried: d.retried, redeemed: d.redeemed, taught: d.taught, revealed: d.revealed, conf: d.conf, picked: d.picked,
    })),
    queue: state.queue, cursor: state.cursor,
    combo: state.combo, maxCombo: state.maxCombo, sessionXp: state.sessionXp,
    startedAt: state.startedAt,
  };
  safeSet("sd_active_deck", JSON.stringify(snap));
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
  app.innerHTML = skeletonHTML("quiz");
  // Gather every bank the snapshot's questions live in (a review deck spans sections).
  const sections = new Set();
  const secOf = (id) => id.split("/")[0];
  snap.items.forEach((it) => sections.add(secOf(it.id)));
  const byId = new Map();
  for (const sec of sections) {
    const bank = await loadBank(sec);
    if (bank) for (const q of bank) byId.set(q.id, q);
    for (const q of await loadCaseBank(sec)) byId.set(q.id, q);   // [CS] resolve case-study ids on resume
  }
  const deck = [], idxMap = new Map();
  snap.items.forEach((it, oldIdx) => {
    const q = byId.get(it.id);
    if (!q) return;                                // orphaned question: drop gracefully
    const item = makeItem(q, it.optOrder, !!it.flip);   // [A2] flip survives resume
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
  state.hard = !!snap.hard; state.prime = false; state.awaitingConf = false; state._medMs = medianReviewMs();
  resetPhaseBState();                              // [B] a resumed deck restarts these session counters
  state.mode = snap.mode === "flash" ? "flash" : "quiz";
  state.deck = deck; state.queue = queue; state.cursor = cursor;
  state.combo = snap.combo || 0; state.maxCombo = snap.maxCombo || 0;
  state.sessionXp = snap.sessionXp || 0;
  state.section = snap.section; state.modules = snap.modules || null;
  state.sourceFiles = snap.sourceFiles || null;      // [SF] additive: old snapshots read undefined -> null -> whole-module
  state.limit = snap.limit || null;                  // [SF] additive: old snapshots -> null -> deckLen()
  state.startedAt = snap.startedAt || Date.now();
  state.replayFn = snap.section === "review" ? startReview
    : snap.section === "weakspots" ? startWeakSpots
    : () => startBlitz(snap.section, snap.modules, snap.sourceFiles, snap.limit);
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

/* ---------- [B] combo HUD slot ---------- */
// Lives in .qhead's right cluster (reserved min-width — see style.css) so the
// question text itself never reflows when a combo starts/ends. Shows the
// multiplier the NEXT correct answer would pay, same math as the old inline chip.
function comboChipHTML(combo) {
  if (combo < 2) return "";
  const nextMult = combo + 1 >= 5 ? 3 : combo + 1 >= 3 ? 2 : 1;
  return `<span class="combo${combo >= 5 ? " hot" : ""}">${ICON("flame", "i-flame")} ${combo} combo &middot; ${nextMult}&times; XP</span>`;
}
function refreshComboSlot() {
  const slot = el("#comboSlot");
  if (slot) slot.innerHTML = comboChipHTML(state.combo);
  if (state.combo >= 2) coachMark("#comboSlot", "Streak bonus. One more correct doubles the XP.", "first_combo");   // [E1]
}

/* ---------- [B] boss staging ---------- */
// 1.2s interstitial the first time the cursor reaches a boss item in a deck.
// Tap/keypress skips ahead; body.boss-mode dims the backdrop toward warn/gold
// for the duration of the boss zone (toggled per-render in renderQuestion).
function renderBossIntro(bossCount, cb) {
  document.body.classList.add("boss-mode");
  const comboLine = state.combo >= 3
    ? `<div class="boss-intro-combo">Your x${comboMult()} combo carries in.</div>` : "";
  // Appended to <body> (like moment()/the pause sheet), NOT app.innerHTML — a
  // direct child of #app inherits the base screen-transition animation, which
  // (ID beats classes) would silently mask anything this overlay declares.
  const o = document.createElement("div");
  o.className = "boss-intro"; o.id = "bossIntro";
  o.innerHTML = `<div class="boss-intro-card">
      <div class="boss-intro-title">${ICON("bolt")} BOSS ROUND</div>
      <div class="boss-intro-sub">${bossCount} question${bossCount === 1 ? "" : "s"} &middot; 2&times; XP</div>
      ${comboLine}
      <div class="boss-intro-hint">tap or press any key to continue</div>
    </div>`;
  document.body.appendChild(o);
  sfx.bossSting();
  announce(`Boss round. ${bossCount} questions, double XP.`);
  let done = false;
  const go = () => {
    if (done) return; done = true;
    clearTimeout(timer);
    document.removeEventListener("keydown", onKey, true);
    o.remove();
    /* [C] guard: the deck may have advanced or ended under the interstitial
       (programmatic drivers can reach the buttons beneath the overlay). */
    if (state.inQuiz && state.deck[state.queue[state.cursor]]) cb();
  };
  // stopImmediatePropagation: the dismiss key must never leak through to the
  // quiz handler and answer the just-revealed boss question (keys 1-4).
  const onKey = (e) => { e.preventDefault(); e.stopImmediatePropagation(); go(); };
  o.addEventListener("click", go);
  document.addEventListener("keydown", onKey, true);
  const timer = setTimeout(go, 1200);
}

/* ---------- [B] double-down ---------- */
// Session accuracy so far, over genuinely graded MCQ answers (correct/wrong —
// "learned" lock-ins aren't a first-attempt pick and don't count toward this).
function sessionAccuracySoFar() {
  const graded = state.deck.filter((d) => d.status === "correct" || d.status === "wrong");
  return { acc: graded.length ? graded.filter((d) => d.status === "correct").length / graded.length : 0, n: graded.length };
}
// Bonus XP earned so far this session (everything beyond the flat 10/correct) —
// the same definition finish() uses, so "your bonus rides on this" is honest.
function bonusXpSoFar() {
  const correctSoFar = state.deck.filter((d) => d.status === "correct").length;
  return Math.max(0, state.sessionXp - correctSoFar * 10);
}

function renderQuestion() {
  const idx = state.queue[state.cursor];
  const item = state.deck[idx];
  const { q } = item;
  // A skipped question is taught first (concept card), then tested later.
  if (item.status === "skipped" && !item.taught) { renderTeach(item); return; }
  const testView = item.status === "skipped" && item.taught;                    // A5 lock-it-in test
  const retryView = item.status === "wrong" && item.retry && !item.retried;     // A1 redemption re-test
  // [B] boss staging: 1.2s interstitial the first time the cursor reaches a boss
  // item; body.boss-mode stays on for the whole boss zone, off outside it.
  document.body.classList.toggle("boss-mode", !!item.boss && !testView && !retryView);
  if (item.boss && !testView && !retryView && !state._bossIntroShown) {
    state._bossIntroShown = true;
    renderBossIntro(state.deck.filter((d) => d.boss).length, () => renderQuestion());
    return;
  }
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
  // [B] combo HUD slot lives in .qhead (reserved width), not inline in .qtext.
  const comboSlotHTML = !testView && !retryView ? comboChipHTML(state.combo) : "";
  // [B] double-down: one-time offer before the LAST fresh question of a quiz deck.
  if (!state.hard && item.status === "pending" && isLastInQueue() && state._doubleDown == null) {
    const { acc, n } = sessionAccuracySoFar();
    if (n >= 3 && acc >= 0.7) { state._doubleDown = "offered"; state._doubleDownIdx = idx; state._doubleDownWager = bonusXpSoFar(); }
  }
  const ddBar = (state._doubleDown === "offered" && state._doubleDownIdx === idx && state._doubleDownWager > 0)
    ? `<div class="dd-bar" id="ddBar">Double down &mdash; your bonus XP (<b>${state._doubleDownWager}</b>) rides on this one.
         <button class="dd-yes" id="ddYes">Double down <kbd>D</kbd></button>
         <button class="dd-no" id="ddNo">No thanks</button>
       </div>` : "";
  // Prefer the *Md display variant per option (src 0 = correct, i+1 = distractors[i]).
  // [A2] flip items carry their own option texts (questions), each with its md.
  const optText = (o) => item.flip ? (o.md || o.t)
    : o.src === 0 ? (q.correctMd || q.correct)
    : (q.distractorsMd && q.distractorsMd[o.src - 1]) || o.t;
  // [A2] flip prompt: the ANSWER is shown; the options are candidate questions.
  // (Combo lives in the [B] #comboSlot in .qhead, not inline in .qtext.)
  const prompt = item.flip
    ? `<div class="flip-eyebrow">Which question does this answer?</div>
       <div class="qtext flip-item">${qInline(q.correctMd || q.correct)} ${chip}</div>`
    : `<div class="qtext">${qInline(q.questionMd || q.question)} ${chip}</div>`;
  const skippable = item.status === "pending" && !item.flip && !state.prime;   // [A2] no skip-teach on flips/prime
  app.innerHTML = `
    <div class="qhead">
      <span class="module">${esc(label(q.section))} &middot; ${esc(q.moduleName)} ${diffChip}</span>
      <span class="qright"><span class="combo-slot" id="comboSlot">${comboSlotHTML}</span><button class="qpause" id="qpauseBtn" title="Pause this blitz" aria-label="Pause this blitz">II</button><span class="dots" role="img" aria-label="Question ${state.cursor + 1} of ${state.queue.length}">${dotsHTML(idx)}</span><span class="qnum">${deckProgressCounter()}</span></span>
    </div>
    ${bossBanner}
    ${prompt}
    ${ddBar}
    ${gated ? `<button class="showopts" id="showOptsBtn">Show options <kbd>Space</kbd></button>` : ""}
    <div class="options${gated ? " gated" : ""}">
      ${opts.map((o, i) => `<button class="opt" data-i="${i}"><kbd>${i + 1}</kbd>${qInline(optText(o))}<span class="mark"></span></button>`).join("")}
    </div>
    <div class="reveal" id="reveal"></div>
    <div class="qactions">
      ${skippable ? `<button class="skip" id="skipBtn">Skip for now (S) &rarr;</button>` : "<span></span>"}
      <button class="next" id="nextBtn">${isLastInQueue() ? "Finish" : "Next (↵)"}</button>
    </div>`;
  document.querySelectorAll(".opt").forEach((b) =>
    b.addEventListener("click", () => answer(parseInt(b.dataset.i, 10))));
  if (gated) el("#showOptsBtn").addEventListener("click", revealHardOptions);
  if (skippable) el("#skipBtn").addEventListener("click", skipQuestion);
  if (ddBar) {
    el("#ddYes").addEventListener("click", () => { state._doubleDown = "accepted"; el("#ddBar")?.remove(); announce("Double down accepted."); });
    el("#ddNo").addEventListener("click", () => { state._doubleDown = "declined"; el("#ddBar")?.remove(); });
  }
  el("#nextBtn").addEventListener("click", nextQuestion);
  el("#qpauseBtn").addEventListener("click", () => openPauseSheet(null));
  /* [C] interviewer stage (avatar + HP bar) / gauntlet practice banner */
  if (state.interview) renderInterviewStage();
  else if (state.gauntlet && state.gauntlet.practice) renderPracticeBanner();
  app.focus({ preventScroll: true });              // keep keyboard + SR context on the new question
  coachMark(".options", "Tap or press 1-4. Not sure? Skip — it returns as a lesson.", "first_question");   // [E1]
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
  document.body.classList.remove("boss-mode");     // [B] teach card is never a boss screen
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
  if (state.prime) { gradeAnswer(i, null); return; }   // [A2] prime: instant reveal, no confidence step
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
  const idx = state.queue[state.cursor];
  const item = state.deck[idx];
  item.ms = Math.max(0, Math.round(performance.now() - (state.qShownAt || performance.now())));
  const { opts, q } = item;
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

  // [A2] confusion recording: a wrong pick whose provenance resolves to ANOTHER
  // module logs the pair (canonical order) — tallied in saveSessionLocal.
  // Skipped for flip items: their options ARE questions, not answers.
  if (!right && !item.flip) {
    const csrc = distractorSource(item.q, opts[i]);
    if (csrc && csrc.module !== item.q.module) item.confusion = [item.q.module, csrc.module].sort().join("|");
  }
  // [B] time capsules: this question was buried on a past flawless run and its
  // 60-day due date has arrived. Clear the flag either way; only a correct
  // answer earns the "it kept" moment (queued, played from finish()).
  const rv0 = (state.progress.reviews || {})[q.id];
  if (rv0 && rv0.capsule && todayISO() >= rv0.due) {
    const plantedOn = rv0.capsule;
    delete rv0.capsule;
    if (right) state._capsuleReturns.push({ plantedOn, moduleName: q.moduleName });
  }

  if (state.prime) {
    // [A2] prime pretest: reveal-only grading — no XP, combo, or redemption loop.
    item.status = right ? "correct" : "wrong";
    if (!right) { item.picked = i; item.pickedOpt = opts[i]; }
    if (right) { sfx.correct(); haptic("correct"); } else { sfx.wrong(); haptic("wrong"); }
  } else if (retryMode) {
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
      let gain = Math.round(10 * comboMult() * (item.boss ? 2 : 1) * (state.hard ? 1.5 : 1));            // A3 recall pays 1.5x
      // [B] comeback engine: a correct answer after >=2 consecutive misses recovers.
      const recovered = (state._missStreak || 0) >= 2;
      if (recovered) { gain += 5; item._recovered = true; }
      state.sessionXp += gain;
      floatXP(gain, optBtns[i]);
      state._missStreak = 0;
      state._answerLog.push("correct");
      if (state.combo === 3 || state.combo === 5 || state.combo >= 7) { sfx.combo(); ripple(optBtns[i]); haptic("combo"); }
      else if (recovered) { sfx.recovered(); haptic("correct"); }
      else { sfx.correct(); haptic("correct"); }
    } else {
      item.status = "wrong"; item.picked = i; item.pickedOpt = opts[i];
      state.combo = 0; sfx.wrong(); haptic("wrong");
      state._missStreak = (state._missStreak || 0) + 1;
      state._maxMissStreak = Math.max(state._maxMissStreak || 0, state._missStreak);
      state._answerLog.push("wrong");
      if (!item.retry && !state.interview) {         // A1 miss loop: one in-session redemption re-test ([C] the Interviewer probes instead)
        item.retry = true;
        const at = Math.min(state.cursor + 3, state.queue.length);
        state.queue.splice(at, 0, state.queue[state.cursor]);
      }
    }
    refreshComboSlot();                              // [B] update the HUD slot immediately, not next render
  }

  // [B] double-down payout — applies once, to the exact item it was wagered on.
  if (state._doubleDown === "accepted" && idx === state._doubleDownIdx && !item._ddApplied) {
    item._ddApplied = true;
    if (right) { const amt = state._doubleDownWager; state.sessionXp += amt; item._ddOutcome = { win: true, amount: amt }; }
    else { const amt = Math.min(state._doubleDownWager, state.sessionXp); state.sessionXp -= amt; item._ddOutcome = { win: false, amount: amt }; }
  }

  const sk = el("#skipBtn"); if (sk) sk.remove();
  const ddBar = el("#ddBar"); if (ddBar) ddBar.remove();
  buildReveal(item, i, right, { testMode, retryMode, conf });
  el("#nextBtn").classList.add("show");
  /* [C] ledger tracking (first attempts only, never prime pretests) + interviewer HP / follow-up */
  if (!testMode && !retryMode && !state.prime) {
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
  // [A2] provenance is skipped for flip items — their options ARE questions.
  if (!right && !item.flip) {
    const src = distractorSource(q, opts[pickIdx]);
    if (src) prov = `<div class="prov">You picked the answer to: <span class="prov-q">${qInline(src.questionMd || src.question)}</span> &mdash; from ${esc(src.moduleName)}.
      <button class="deeper prov-read" data-mod="${esc(src.module)}" data-src="${esc(src.sourceFile || "README.md")}" data-name="${esc(src.moduleName)}">Read that instead &rarr;</button></div>`;
  }
  rev.className = "reveal show" + (hyper ? " hyper" : "");
  // [B] comeback + double-down outcome chips, above the usual reveal content.
  const recoveredChip = item._recovered ? `<span class="recovered-chip">Recovered.</span>` : "";
  const ddNote = item._ddOutcome
    ? `<div class="dd-outcome ${item._ddOutcome.win ? "win" : "lose"}">Double-down ${item._ddOutcome.win ? "paid" : "lost"}: ${item._ddOutcome.win ? "+" : "−"}${item._ddOutcome.amount}</div>` : "";
  rev.innerHTML = `${recoveredChip}${ddNote}${hyper ? `<div class="hyper-lead">High-confidence miss &mdash; worth a careful read.</div>` : ""}<b>Full answer:</b> ${qInline(q.answerFullMd || q.answerFull)}${prov}
    <button class="deeper" id="deeperBtn">Dive deeper into ${esc(q.moduleName)} &rarr;</button>`;
  // [E1] flag-for-review + copy-question icons, pinned to the top of the panel.
  rev.insertAdjacentHTML("afterbegin", revealActionsHTML(item));
  wireRevealActions(rev, item);
  // [A2] explain-back on any wrong reveal (first attempt and failed redemption);
  // not on flips (prompt is the answer already) and not in prime (nothing counts).
  if (!right && !item.flip && !state.prime) {
    rev.insertAdjacentHTML("beforeend", explainBackHTML(item));
    wireExplainBack(rev, item);
  }
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
  // [E2] beauty: the next question slides in from the right (directional vt);
  // vt() falls back to an instant swap when view transitions are unsupported or
  // reduced-motion is on.
  if (state.cursor < state.queue.length) vt(() => renderQuestion(), "vt-qnext");
  else if (state.prime) finishPrime();               // [A2] prime: quiet save, then open the reader
  else finish();
}

/* ---------- flashcard (self-grade) mode ---------- */
function renderCard() {
  const idx = state.queue[state.cursor];
  const item = state.deck[idx];
  const { q } = item;
  document.body.classList.remove("boss-mode");     // [B] flashcards never boss-stage
  state.inQuiz = true; state.answered = false; state.curOptsLen = 0;
  state.qShownAt = performance.now();
  const DONE = ["correct", "wrong", "learned"];
  const dots = state.deck.map((it, i) =>
    `<span class="dot ${DONE.includes(it.status) ? "done" : ""} ${i === idx ? "cur" : ""}"></span>`).join("");
  // [B] recall-streak chip — accumulated Got-it/Easy grades in a row this deck.
  const streakChip = state._cardStreak >= 2 ? ` <span class="card-streak">${state._cardStreak} in a row</span>` : "";
  app.innerHTML = `
    <div class="qhead">
      <span class="module">${esc(label(q.section))} &middot; ${esc(q.moduleName)}</span>
      <span class="qright"><button class="qpause" id="qpauseBtn" title="Pause this blitz" aria-label="Pause this blitz">II</button><span class="dots" role="img" aria-label="Card ${state.cursor + 1} of ${state.queue.length}">${dots}</span><span class="qnum">${state.cursor + 1}/${state.queue.length}</span></span>
    </div>
    <div class="flash-label">Flashcard &middot; recall it, then grade yourself${streakChip}</div>
    <div class="qtext">${esc(q.question)}</div>
    <div class="reveal" id="reveal"></div>
    <div class="qactions" id="cardActions">
      <span></span>
      <button class="next show" id="revealBtn">Reveal answer <span class="key-hint">(Space)</span></button>
    </div>`;
  el("#revealBtn").addEventListener("click", revealCard);
  el("#qpauseBtn").addEventListener("click", () => openPauseSheet(null));
  app.focus({ preventScroll: true });
}

function revealCard() {
  if (state.answered) return;
  state.answered = true;
  const item = state.deck[state.queue[state.cursor]];
  const { q } = item;
  const rev = el("#reveal");
  rev.innerHTML = `<b>Answer:</b> ${qInline(q.answerFullMd || q.answerFull)}
    <button class="deeper" id="deeperBtn">Dive deeper into ${esc(q.moduleName)} &rarr;</button>`;
  rev.insertAdjacentHTML("afterbegin", revealActionsHTML(item));   // [E1] flag + copy
  wireRevealActions(rev, item);
  rev.insertAdjacentHTML("beforeend", explainBackHTML(item));   // [A2] explain-back on card reveal
  wireExplainBack(rev, item);
  rev.classList.add("show");
  if (!REDUCED()) rev.classList.add("card-flip");  // [B] 3D flip; instant swap when reduced-motion
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
  if (got) {
    item.status = "correct"; item.conf = conf || null; state.sessionXp += 10;
    // [B] recall-streak: sfx.combo() at 3/5/7+, matching the quiz-mode milestone feel.
    state._cardStreak = (state._cardStreak || 0) + 1;
    if (state._cardStreak === 3 || state._cardStreak === 5 || state._cardStreak >= 7) sfx.combo();
    else sfx.correct();
    floatXP(10, el("#easyBtn") || el("#hardBtn"));
  } else {
    item.status = "wrong"; state._cardStreak = 0; sfx.wrong();
  }
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
  document.body.classList.remove("boss-mode");     // [B] the deck is ending; drop the boss dim
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
  // [B] comeback engine: a deep miss streak (>=3) recovered by the last 5 in a row.
  const last5 = (state._answerLog || []).slice(-5);
  const comeback = (state._maxMissStreak || 0) >= 3 && last5.length === 5 && last5.every((x) => x === "correct");
  const results = recorded.map((d) => ({ id: d.q.id, section: d.q.section, module: d.q.module, status: d.status, ms: d.ms || 0, conf: d.conf || null, confusion: d.confusion }));   // [A2] confusion pair
  const durationSec = Math.max(0, Math.round((Date.now() - (state.startedAt || Date.now())) / 1000));
  const pre = progressSnapshot();                  // for the moments engine (before the save)
  // [E1] Ghost: this section's previous-best run (highest correct, tie-break
  // fastest), read from history BEFORE this session is pushed onto it. Only
  // for real content sections — review/weak/drill/refresh/gauntlet/interview
  // selection isn't a fair apples-to-apples rematch. Older entries without a
  // recorded durationSec are skipped silently (nothing to compare).
  let ghostBest = null;
  if (state.index.sections && Object.prototype.hasOwnProperty.call(state.index.sections, state.section)) {
    for (const h of state.progress.history || []) {
      if (h.section !== state.section || !(h.durationSec > 0) || !(h.answered > 0)) continue;
      if (!ghostBest || h.correct > ghostBest.correct || (h.correct === ghostBest.correct && h.durationSec < ghostBest.durationSec)) ghostBest = h;
    }
  }
  const { xp, freezeUsed, saved } = saveSessionLocal({ date: todayISO(), section: state.section, results, bonusXp, durationSec, comeback });
  // Only drop the resume snapshot once the save is confirmed — a quota failure
  // keeps the run resumable instead of losing it silently.
  if (saved !== false) clearDeckSnapshot();
  else showToast("Couldn't save this session — storage is full. Your run is kept for resume; export a backup.", 8000);
  const cExtra = cAfterSave(cCtx, { correct, total });   // [C] seal gauntlet · resolve interview · detect ledger awards
  const post = progressSnapshot();                 // (after the save)
  // [B] time-capsule returns: correct answers on questions whose 60-day capsule
  // came due this session, celebrated before the general milestone moments.
  for (const cr of state._capsuleReturns || [])
    await moment({ tier: "capsule", icon: ICON("clock"), title: "Capsule recovered", sub: `You planted this on ${fmtDate(cr.plantedOn)}. It kept.`, play: () => sfx.chime() });
  await queueMoments(pre, post, cExtra);           // celebrate milestones before the results ([C] extras lead)
  const pct = total ? Math.round((correct / total) * 100) : 0;
  const flawless = pct === 100 && total > 0;
  // [B] bury a new time capsule on a flawless, full-length, non-flash/non-hard blitz.
  if (flawless && total === deckLen() && !opts.early && state.mode !== "flash" && !state.hard
      && state.section !== "review" && state.section !== "weakspots" && !state.deck.some((d) => d.retry)) {
    maybeBuryCapsule();
    if (state._capsuleBuried) {
      await moment({ tier: "capsule", icon: ICON("clock"), title: "Time capsule buried", sub: `${state._capsuleBuried.moduleName}. Returns ${fmtDate(state._capsuleBuried.due)}.`, play: () => sfx.chime() });
    }
  }
  refreshStats();
  if (flawless) { confetti(); sfx.finish(); }
  const cheer = flawless ? "Flawless! " : pct >= 70 ? "Strong work. " : pct >= 40 ? "Good progress. " : "Every rep counts. ";
  announce(`Blitz finished. ${correct} of ${total} correct. ${xp} XP earned.`);
  const freezeNote = freezeUsed
    ? `<div class="freeze-saved">${ICON("snow", "i-snow")} Streak saved &mdash; 1 freeze used (${state.progress.freezes || 0} left)</div>` : "";
  const backupNote = backupNudgeHTML();
  // [B] boss/comeback badges alongside the existing learned/combo ones.
  const bossItems = recorded.filter((d) => d.boss);
  const bossCorrect = bossItems.filter((d) => d.status === "correct").length;
  const extraBadges =
    (learned ? `<div class="badge"><div class="n">${learned}</div><div class="l">Learned</div></div>` : "") +
    (state.maxCombo >= 2 ? `<div class="badge"><div class="n">${state.maxCombo}&times;</div><div class="l">Best combo</div></div>` : "") +
    (bossItems.length ? `<div class="badge"><div class="n">${bossCorrect}/${bossItems.length}</div><div class="l">Boss cleared</div></div>` : "") +
    (comeback ? `<div class="badge"><div class="n">&#8635;</div><div class="l">Comeback</div></div>` : "");
  // [B] results reward moments: daily goal ring, elapsed stopwatch, streak-advance line.
  const goalRemain = Math.max(0, DAILY_XP_GOAL - post.todaysXp);
  const goalLineTxt = post.todaysXp >= DAILY_XP_GOAL
    ? `Daily goal: complete &mdash; ${post.todaysXp} XP today`
    : `Daily goal: ${post.todaysXp}/${DAILY_XP_GOAL} &mdash; ${goalRemain} to go`;
  const mm = Math.floor(durationSec / 60), ss = durationSec % 60;
  const elapsedStr = `${mm}:${String(ss).padStart(2, "0")}`;
  const fastChip = durationSec > 0 && durationSec < 300 ? `<span class="fast-chip">under 5 minutes</span>` : "";
  const streakAdvanceLine = post.streak > pre.streak
    ? `<p class="streak-advance">+${post.streak - pre.streak} day &mdash; ${post.streak}-day streak</p>` : "";
  // [E1] Ghost: "vs you, <date>: 6/10 in 5:40 -> 9/10 in 4:12."
  const ghostLine = (ghostBest && durationSec > 0)
    ? `<p class="ghost-line">vs you, ${fmtDate(ghostBest.date)}: ${ghostBest.correct}/${ghostBest.answered} in ${Math.floor(ghostBest.durationSec / 60)}:${String(ghostBest.durationSec % 60).padStart(2, "0")} &rarr; ${correct}/${total} in ${elapsedStr}</p>`
    : "";
  const resultsMeta = `
    <div class="results-meta">
      <div class="goal-line">${goalRing()}<span>${goalLineTxt}</span></div>
      <div class="elapsed-line">Finished in <b>${elapsedStr}</b> ${fastChip}</div>
    </div>
    ${streakAdvanceLine}${ghostLine}`;
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
  // [E1] no-repeat sampling only reshuffles a real content-section blitz —
  // review/weak/drill/refresh/gauntlet/interview replays aren't "a fresh mix".
  const isContentSection = !!(state.index.sections && Object.prototype.hasOwnProperty.call(state.index.sections, state.section));
  app.innerHTML = `
    <div class="result staged">
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
      ${resultsMeta}
      ${freezeNote}${backupNote}
      <div class="badges">
        <div class="badge"><div class="n" id="xpCount">+0</div><div class="l">XP</div></div>
        ${extraBadges}
        <div class="badge"><div class="n">${state.progress.streak || 0}</div><div class="l">Day streak</div></div>
        <div class="badge"><div class="n">${state.progress.totalXP || 0}</div><div class="l">Total XP</div></div>
      </div>
      <div class="row">
        <button class="primary" id="againBtn">${isContentSection ? "Another round &middot; same mix" : "Play another"}</button>
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
  coachMark(".result .sub", "Missed questions return on a schedule. Miss a day? A freeze covers you.", "first_results");   // [E1]
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
    // celebratory buzz on phones, keyed by tier (paired with the moment's own sfx)
    const HAP = { title: "levelup", level: "levelup", gold: "tier", foil: "tier", silver: "tier", bronze: "tier", streak: "tier", gauntlet: "seal", ledger: "tier" };
    if (HAP[tier]) haptic(HAP[tier]);
    const o = document.createElement("div");
    o.className = "moment" + (tier ? " m-" + tier : "") + (reduced ? " reduced" : "");
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
  if (post.level > pre.level) {
    // [B] career ladder: crossing a title band gets a bigger celebration than a plain level-up.
    const preTitle = titleForLevel(pre.level), postTitle = titleForLevel(post.level);
    if (postTitle !== preTitle) {
      list.push({ tier: "title", icon: ICON("bolt"), title: `Level ${post.level} — ${postTitle}`, sub: `You made ${postTitle}.`, play: () => { sfx.levelup(); confetti(); } });
    } else {
      list.push({ tier: "level", icon: ICON("bolt"), title: `Level ${post.level}`, sub: "New level reached.", play: () => sfx.levelup() });
    }
  }
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

/* ---------- [B] time capsules ---------- */
const fmtDate = (iso) => new Date(iso + "T00:00:00").toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });

// On a flawless (10/10, no redemptions) quiz-mode blitz, bury the session's
// hardest question 60 days out: due/interval jump ahead and rv.capsule marks
// it. Guarded to at most one new capsule per day. Sets state._capsuleBuried
// for finish() to celebrate; returns nothing (reviews are mutated in place —
// saveSessionLocal has already persisted them by the time this runs).
function maybeBuryCapsule() {
  const today = todayISO();
  const reviews = state.progress.reviews || {};
  if (Object.values(reviews).some((rv) => rv.capsule === today)) return;   // 1/day guard
  if (!state.deck.length) return;
  const medMs = state._medMs != null ? state._medMs : medianReviewMs();
  const scored = state.deck.map((d) => ({ d, pd: personalDifficulty(d.q, reviews[d.q.id], medMs) }));
  const best = scored.filter((s) => s.pd != null).sort((a, b) => b.pd - a.pd)[0];
  const pick = (best ? best.d : state.deck[Math.floor(Math.random() * state.deck.length)]).q;
  const rv = reviews[pick.id] || { ease: 2.5, interval: 0, reps: 0, lapses: 0 };
  rv.section = pick.section; rv.module = pick.module;
  rv.interval = 60;
  const due = new Date(today + "T00:00:00"); due.setDate(due.getDate() + 60);
  rv.due = due.toLocaleDateString("en-CA");
  rv.capsule = today;
  reviews[pick.id] = rv;
  state.progress.reviews = reviews;
  safeSet("sd_progress", JSON.stringify(state.progress));
  state._capsuleBuried = { moduleName: pick.moduleName, due: rv.due };
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

// Keys that make up a full save. Excludes transient/derived state: sd_active_deck
// (same-day resume snapshot), sd_recent_* (no-repeat ring), sd_progress_corrupt
// (recovery artifact — never re-imported).
const BACKUP_KEYS = [
  "sd_progress", "sd_gauntlet", "sd_coach", "sd_study_path",
  "sd_theme", "sd_mode", "sd_mute", "sd_deck_len", "sd_prime_opt",
  "sd_reader_w", "sd_modules_w", "sd_toc_w", "sd_reader_fs", "sd_reader_full",
  "sd_reader_toc", "sd_reader_modules", "sd_reader_scroll", "sd_last_read",
  "sd_reader_font", "sd_reader_measure", "sd_reader_dropcap", "sd_reader_recall",
  "sd_last_mastery", "sd_last_export",
  "sd_cm_first_question", "sd_cm_first_combo", "sd_cm_first_results", "sd_cm_first_cards",
];

function exportProgress() {
  const blob = { version: 2, exportedAt: new Date().toISOString(), data: {} };
  for (const k of BACKUP_KEYS) { const v = localStorage.getItem(k); if (v != null) blob.data[k] = v; }
  const filename = `sysdesign-daily-backup-${todayISO()}.json`;
  const json = JSON.stringify(blob, null, 2);
  // APK: no browser download chrome — hand the same filename + pretty JSON to the
  // native bridge, which writes it to shared storage, then run the shared tail.
  if (window.SDAndroid && typeof window.SDAndroid.saveBackup === "function") {
    // The bridge returns success synchronously; only record/announce a real
    // export — a failed write must not suppress the 30-day backup nudge (the
    // native side already toasts the failure reason).
    if (window.SDAndroid.saveBackup(filename, json)) {
      safeSet("sd_last_export", todayISO());
      announce("Backup exported.");
    }
    return;
  }
  const a = document.createElement("a");
  a.href = URL.createObjectURL(new Blob([json], { type: "application/json" }));
  a.download = filename;
  document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 2000);
  safeSet("sd_last_export", todayISO());
  announce("Backup exported.");
}

function importProgress(file) {
  const reader = new FileReader();
  reader.onload = () => {
    let blob;
    try { blob = JSON.parse(reader.result); } catch { alert("That file isn't a valid backup."); return; }
    if (!blob || typeof blob !== "object" || !blob.data || typeof blob.data !== "object" || blob.data.sd_progress == null) {
      alert("That file isn't a valid LORA backup."); return;
    }
    if (!confirm("Import this backup? It replaces all current progress in this browser.")) return;
    try {
      for (const k of BACKUP_KEYS) { if (blob.data[k] != null) localStorage.setItem(k, blob.data[k]); }
    } catch { alert("Import failed — your browser storage may be full. Free up space and try again."); return; }
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
  if (state.prime) { state.inQuiz = false; state.prime = false; fn(); return; }   // [A2] prime never blocks
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
  o.setAttribute("role", "dialog"); o.setAttribute("aria-modal", "true"); o.setAttribute("aria-label", "Pause this blitz");
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
  const release = trapFocus(o, { initial: "#pauseKeep" });
  const closeSheet = (restore = true) => { release(restore); o.remove(); };
  o._close = closeSheet;
  el("#pauseKeep").addEventListener("click", () => closeSheet());
  el("#pauseLeave").addEventListener("click", () => { saveDeckSnapshot(); state.inQuiz = false; closeSheet(false); leave(); });
  el("#pauseFinish").addEventListener("click", () => { closeSheet(false); finish({ early: true }); });
  o.addEventListener("click", (e) => { if (e.target === o) closeSheet(); });
}

/* ---------- [W3] mobile "More" options sheet (<=640px topbar overflow) ---------- */
// The <=640px topbar hides mode/theme/mute; this glass sheet (same pattern as the
// pause sheet) surfaces them plus Search + Help. Reuses trapFocus + wireRadioGroup.
function openMoreSheet() {
  if (el("#moreSheet")) return;
  const flash = deckMode() === "flash";
  const o = document.createElement("div");
  o.className = "pause-sheet"; o.id = "moreSheet";
  o.setAttribute("role", "dialog"); o.setAttribute("aria-modal", "true"); o.setAttribute("aria-label", "Options");
  o.innerHTML = `<div class="pause-card more-card">
      <h2>Options</h2>
      <div class="more-row">
        <span class="more-lbl">Mode</span>
        <div class="more-seg" id="moreMode" role="radiogroup" aria-label="Deck mode">
          <button role="radio" data-mode="quiz" aria-checked="${!flash}">Quiz</button>
          <button role="radio" data-mode="flash" aria-checked="${flash}">Cards</button>
        </div>
      </div>
      <div class="more-row more-row-col">
        <span class="more-lbl">Theme</span>
        <div class="more-theme" id="moreTheme" role="radiogroup" aria-label="Theme">${themeOptionsHTML()}</div>
      </div>
      <div class="more-row">
        <span class="more-lbl">Sound</span>
        <button class="ghost" id="moreSound"></button>
      </div>
      <div class="more-actions">
        <button class="ghost" id="moreSearch">Search&hellip;</button>
        <button class="ghost" id="moreHelp">Keyboard shortcuts</button>
        <button class="primary" id="moreClose">Done</button>
      </div>
    </div>`;
  document.body.appendChild(o);
  const release = trapFocus(o, { initial: "#moreClose", restoreTo: el("#moreBtn") });
  const close = (restore = true) => { release(restore); o.remove(); };
  o._close = close;

  // Mode: segmented Quiz|Cards radiogroup — same effect as the #modeBtn handler.
  const modeGrp = el("#moreMode");
  modeGrp.querySelectorAll("[role=radio]").forEach((b) => b.addEventListener("click", () => {
    safeSet("sd_mode", b.dataset.mode === "flash" ? "flash" : "quiz");
    syncModeBtn();
    modeGrp.querySelectorAll("[role=radio]").forEach((r) => r.setAttribute("aria-checked", r === b ? "true" : "false"));
    modeGrp._radioSync?.();
    if (!state.inQuiz) renderHome();
  }));
  wireRadioGroup(modeGrp);

  // Theme: the shared theme-radio markup + the same click handler as the popover.
  const themeGrp = el("#moreTheme");
  themeGrp.querySelectorAll(".theme-opt").forEach((b) => b.addEventListener("click", () => {
    applyTheme(b.dataset.theme);
    themeGrp._radioSync?.();
  }));
  wireRadioGroup(themeGrp);

  // Sound: mirrors #muteBtn.
  const snd = el("#moreSound");
  const syncSnd = () => { snd.textContent = sfx.isOn() ? "Sound on" : "Sound off"; snd.setAttribute("aria-pressed", sfx.isOn() ? "true" : "false"); };
  syncSnd();
  snd.addEventListener("click", () => { sfx.toggle(); syncMuteBtn(); syncSnd(); });

  el("#moreSearch").addEventListener("click", () => { close(false); openPalette(); });
  el("#moreHelp").addEventListener("click", () => { close(false); toggleHelp(); });
  el("#moreClose").addEventListener("click", () => close());
  o.addEventListener("click", (e) => { if (e.target === o) close(); });
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
  let activeDays = 0, totalXp = 0;
  for (let i = 0; i < WEEKS * 7; i++) {
    const d = new Date(start); d.setDate(d.getDate() + i);   // setDate is DST-safe; raw ms math is not
    const iso = d.toLocaleDateString("en-CA");
    const xp = xpByDay.get(iso) || 0;
    if (d > today) { cells += `<span class="hmcell hm-future" aria-hidden="true"></span>`; continue; }
    if (xp > 0) { activeDays++; totalXp += xp; }
    const lvl = xp === 0 ? 0 : xp < 30 ? 1 : xp < 70 ? 2 : xp < 120 ? 3 : 4;
    const gaunt = gauntDays.has(iso) ? " hm-gaunt" : "";                       // [C] gold-dot overlay
    cells += `<span class="hmcell hm-l${lvl}${gaunt}" style="animation-delay:${i * 3}ms" title="${iso}: ${xp} XP${gaunt ? " · gauntlet" : ""}" aria-hidden="true"></span>`;
  }
  const empty = !(history || []).length
    ? `<p class="hm-empty">No activity yet &mdash; your first blitz lights up this grid.</p>` : "";
  const summary = `Activity, last ${WEEKS} weeks: ${activeDays} active day${activeDays === 1 ? "" : "s"}, ${totalXp} XP`;
  return `<h2 class="section-h">Activity</h2>
    <div class="heatmap-scroll"><div class="heatmap" role="img" aria-label="${esc(summary)}">${cells}</div></div>
    <div class="hmlegend">Less
      <span class="hmcell hm-l0" aria-hidden="true"></span><span class="hmcell hm-l1" aria-hidden="true"></span><span class="hmcell hm-l2" aria-hidden="true"></span><span class="hmcell hm-l3" aria-hidden="true"></span><span class="hmcell hm-l4" aria-hidden="true"></span>
      More<span class="hmlegend-gaunt"><span class="hmcell hm-l2 hm-gaunt" aria-hidden="true"></span> gauntlet</span></div>${empty}`;
}

/* ============================================================================
   [E2] PHASE E2 — Insights · Command palette · Mobile · Beauty
   All additive; every block below derives from the already-persisted sd_progress
   (reviews / history / sections). No new persisted fields except sd_last_mastery
   (a Home-render snapshot used only to detect an accuracy delta for a shine).
   ========================================================================== */

/* ---------- [E2] Insights: analytics on the Progress screen ---------- */

// 14-day review-due forecast. Overdue (due <= today) rolls into today's bar so
// the "clear it now" backlog is visible rather than hidden in the past.
function forecastData() {
  const today = todayISO();
  const base = new Date(today + "T00:00:00");
  const days = [];
  for (let i = 0; i < 14; i++) { const d = new Date(base); d.setDate(d.getDate() + i); days.push({ iso: d.toLocaleDateString("en-CA"), n: 0 }); }
  const last = days[13].iso, idxOf = {};
  days.forEach((d, i) => (idxOf[d.iso] = i));
  for (const rv of Object.values(state.progress.reviews || {})) {
    if (!rv.due) continue;
    if (rv.due <= today) days[0].n++;
    else if (rv.due <= last) days[idxOf[rv.due]].n++;
  }
  return days;
}
function forecastHTML() {
  const days = forecastData();
  const total = days.reduce((s, d) => s + d.n, 0);
  const max = Math.max(1, ...days.map((d) => d.n));
  const today = todayISO();
  const bars = days.map((d) => {
    const h = d.n ? Math.max(8, Math.round((d.n / max) * 100)) : 0;
    const dd = new Date(d.iso + "T00:00:00");
    const wd = dd.toLocaleDateString("en-US", { weekday: "narrow" });
    return `<div class="fc-col${d.iso === today ? " today" : ""}" title="${d.iso}: ${d.n} due">
        <span class="fc-n">${d.n || ""}</span>
        <div class="fc-track"><div class="fc-bar" style="height:${h}%"></div></div>
        <span class="fc-d">${esc(wd)}</span></div>`;
  }).join("");
  return `<div class="ins-card">
      <h3 class="ins-h">Memory forecast</h3>
      <div class="forecast">${bars}</div>
      <p class="ins-cap">${total} due over the next two weeks.</p></div>`;
}

// Per-module accuracy proxy from spaced-repetition state: reps are graded passes,
// lapses are failed recalls, so reps/(reps+lapses) reads as a rolling accuracy.
function moduleAccStats(progress) {
  const acc = {};
  for (const rv of Object.values((progress && progress.reviews) || {})) {
    const mod = rv.module;
    if (!mod) continue;
    const s = acc[mod] || (acc[mod] = { count: 0, reps: 0, lapses: 0, easeSum: 0, section: rv.section || mod.split("/")[0] });
    s.count++; s.reps += rv.reps || 0; s.lapses += rv.lapses || 0; s.easeSum += rv.ease || 2.5;
  }
  return Object.entries(acc).map(([mod, s]) => {
    const denom = s.reps + s.lapses;
    // denom 0 (only brand-new records) -> fall back to a normalized ease (1.3..2.5).
    const a = denom ? s.reps / denom : Math.max(0, Math.min(1, (s.easeSum / s.count - 1.3) / 1.2));
    return { module: mod, section: s.section, count: s.count, lapses: s.lapses, acc: Math.max(0, Math.min(1, a)) };
  });
}
function modRow(m) {
  const pct = Math.round(m.acc * 100);
  return `<div class="mod-row">
      <span class="mr-name">${esc(modDisplay(m.module))}</span>
      <span class="mr-bar"><i style="width:${pct}%"></i></span>
      <span class="mr-seen">${m.count}</span>
      <button class="ghost sm ins-drill" data-sec="${esc(m.section)}" data-mod="${esc(m.module)}">Drill</button>
    </div>`;
}
function strongestShakiestHTML() {
  const all = moduleAccStats(state.progress).filter((m) => m.count >= 1);
  if (all.length < 2) return "";
  const strong = [...all].sort((a, b) => b.acc - a.acc || b.count - a.count).slice(0, 5);
  const shaky = [...all].sort((a, b) => a.acc - b.acc || b.lapses - a.lapses).slice(0, 5);
  return `<div class="ins-card">
      <h3 class="ins-h">Strongest modules</h3>
      <div class="mod-list">${strong.map(modRow).join("")}</div></div>
    <div class="ins-card">
      <h3 class="ins-h">Shakiest modules</h3>
      <div class="mod-list">${shaky.map(modRow).join("")}</div></div>`;
}

// Leeches: questions missed 3+ times. Question text is looked up lazily (banks
// are multi-MB) — fillLeeches() runs only when the <details> is first expanded.
function leechIds() {
  return Object.entries(state.progress.reviews || {})
    .filter(([, r]) => (r.lapses || 0) >= 3)
    .sort((a, b) => (b[1].lapses || 0) - (a[1].lapses || 0))
    .slice(0, 7).map(([id]) => id);
}
function leechesHTML() {
  const ids = leechIds();
  if (!ids.length) return "";
  return `<div class="ins-card">
      <h3 class="ins-h">Your hardest questions</h3>
      <details class="leeches" id="leechDetails">
        <summary>${ids.length} question${ids.length === 1 ? "" : "s"} missed 3+ times &mdash; expand to see them</summary>
        <div class="leech-list" id="leechList"><div class="leech-note">Loading&hellip;</div></div>
        <button class="ghost" id="leechDrill">Drill these &rarr;</button>
      </details></div>`;
}
async function fillLeeches() {
  const ids = leechIds();
  const secs = new Set(ids.map((id) => (state.progress.reviews[id] || {}).section).filter(Boolean));
  const byId = new Map();
  for (const s of secs) { const b = await loadBank(s); if (b) for (const q of b) byId.set(q.id, q); }
  const list = el("#leechList");
  if (!list) return;
  const rows = ids.map((id) => {
    const rv = state.progress.reviews[id] || {}, q = byId.get(id);
    if (!q) return "";
    return `<button class="leech-row" data-path="${esc(q.module + "/" + (q.sourceFile || "README.md"))}" data-name="${esc(q.moduleName)}">
        <span class="leech-q">${qInline(q.questionMd || q.question)}</span>
        <span class="leech-meta">${rv.lapses}&times; missed &middot; ${esc(q.moduleName)}</span></button>`;
  }).join("");
  list.innerHTML = rows || `<div class="leech-note">Those questions are no longer in the bank.</div>`;
  list.querySelectorAll(".leech-row").forEach((b) => b.addEventListener("click", () => openReaderPath(b.dataset.path, b.dataset.name)));
}

// Per-section confidence calibration. Only sections with >= 10 confidence-tagged
// answers qualify; a section where "sure" underperforms "unsure" is overconfident.
function calibrationHTML() {
  const rows = [];
  for (const [s, st] of Object.entries(state.progress.sections || {})) {
    const tagged = (st.sureSeen || 0) + (st.unsureSeen || 0);
    if (tagged < 10) continue;
    const sureAcc = st.sureSeen ? Math.round((st.sureCorrect || 0) / st.sureSeen * 100) : null;
    const unsureAcc = st.unsureSeen ? Math.round((st.unsureCorrect || 0) / st.unsureSeen * 100) : null;
    const over = sureAcc != null && unsureAcc != null && sureAcc < unsureAcc;
    rows.push(`<div class="cal-row">
        <span class="cal-sec">${esc(label(s))}${over ? ` <span class="warn-chip">overconfident</span>` : ""}</span>
        <span class="cal-val">When sure: <b>${sureAcc == null ? "&mdash;" : sureAcc + "%"}</b> &middot; When unsure: <b>${unsureAcc == null ? "&mdash;" : unsureAcc + "%"}</b></span>
      </div>`);
  }
  if (!rows.length) return "";
  return `<div class="ins-card"><h3 class="ins-h">Calibration</h3>${rows.join("")}</div>`;
}

// 30-day trend: XP per day (bars) + a 7-day rolling accuracy line, both from history.
function trendHTML() {
  const base = new Date(todayISO() + "T00:00:00");
  const days = [];
  for (let i = 29; i >= 0; i--) { const d = new Date(base); d.setDate(d.getDate() - i); days.push({ iso: d.toLocaleDateString("en-CA"), xp: 0, answered: 0, correct: 0 }); }
  const idxOf = {};
  days.forEach((d, i) => (idxOf[d.iso] = i));
  for (const h of state.progress.history || []) {
    const i = idxOf[h.date];
    if (i == null) continue;
    days[i].xp += h.xp || 0; days[i].answered += h.answered || 0; days[i].correct += h.correct || 0;
  }
  const roll = days.map((_, i) => {
    let a = 0, c = 0;
    for (let j = Math.max(0, i - 6); j <= i; j++) { a += days[j].answered; c += days[j].correct; }
    return a ? c / a : null;
  });
  const W = 320, H = 92, pad = 3, plot = H - 16, bw = (W - pad * 2) / 30;
  const maxXp = Math.max(1, ...days.map((d) => d.xp));
  const bars = days.map((d, i) => {
    const bh = d.xp ? Math.max(1.5, (d.xp / maxXp) * plot) : 0;
    const x = pad + i * bw;
    return bh ? `<rect x="${(x + 0.8).toFixed(1)}" y="${(plot - bh + 2).toFixed(1)}" width="${(bw - 1.6).toFixed(1)}" height="${bh.toFixed(1)}" class="tr-bar" rx="1"/>` : "";
  }).join("");
  const pts = [];
  roll.forEach((r, i) => { if (r == null) return; const x = pad + i * bw + bw / 2; const y = (plot + 2) - r * plot; pts.push(`${x.toFixed(1)},${y.toFixed(1)}`); });
  const line = pts.length > 1 ? `<polyline class="tr-line" points="${pts.join(" ")}" fill="none" vector-effect="non-scaling-stroke"/>` : "";
  const totalXp = days.reduce((s, d) => s + d.xp, 0);
  return `<div class="ins-card">
      <h3 class="ins-h">30-day trend</h3>
      <svg class="trend" viewBox="0 0 ${W} ${H}" role="img" aria-label="Daily XP bars with a 7-day rolling accuracy line">${bars}${line}</svg>
      <p class="ins-cap"><b>${totalXp}</b> XP over 30 days &middot; line is 7-day rolling accuracy.</p></div>`;
}

// Session log: the last 10 recorded runs, newest first.
function sessionLogHTML() {
  const h = (state.progress.history || []).slice(-10).reverse();
  if (!h.length) return "";
  const rows = h.map((e) => {
    const secs = e.durationSec || 0, mm = Math.floor(secs / 60), ss = secs % 60;
    const dur = secs > 0 ? `${mm}:${String(ss).padStart(2, "0")}` : "&mdash;";
    return `<div class="log-row">
        <span class="log-date">${fmtDate(e.date)}</span>
        <span class="log-sec">${esc(label(e.section))}</span>
        <span class="log-score">${e.correct}/${e.answered}</span>
        <span class="log-xp">+${e.xp} XP</span>
        <span class="log-dur">${dur}</span></div>`;
  }).join("");
  return `<div class="ins-card"><h3 class="ins-h">Session log</h3><div class="log-list">${rows}</div></div>`;
}

// The whole Insights region, ordered what's-due -> what's-weak -> how-am-I-doing.
function insightsHTML() {
  if (!(state.progress.history || []).length && !Object.keys(state.progress.reviews || {}).length) return "";
  return `<div class="ins-region">
      <h2 class="section-h">Insights</h2>
      ${forecastHTML()}
      <div class="ins-two">${strongestShakiestHTML()}</div>
      ${leechesHTML()}
      ${trendHTML()}
      ${calibrationHTML()}
      ${sessionLogHTML()}
    </div>`;
}
function wireInsights() {
  document.querySelectorAll(".ins-drill").forEach((b) =>
    b.addEventListener("click", () => startBlitz(b.dataset.sec, [b.dataset.mod])));
  const ld = el("#leechDetails");
  if (ld) ld.addEventListener("toggle", () => { if (ld.open && !ld.dataset.filled) { ld.dataset.filled = "1"; fillLeeches(); } });
  const drill = el("#leechDrill");
  if (drill) drill.addEventListener("click", (e) => { e.preventDefault(); const ids = leechIds(); if (ids.length) startRefresh(ids); });
  const hs = el(".heatmap-scroll");
  if (hs) hs.scrollLeft = hs.scrollWidth;             // [E2] mobile: land on today (right edge)
}

/* ---------- [E2] Cmd+K command palette ---------- */
// Subsequence fuzzy match. Returns -1 for a non-match; higher = tighter. Rewards
// consecutive runs (tightness), word-boundary starts, and an early first hit.
function fuzzyScore(query, text) {
  const q = query.toLowerCase(), t = text.toLowerCase();
  if (!q) return 0;
  let qi = 0, score = 0, run = 0, first = -1;
  for (let ti = 0; ti < t.length && qi < q.length; ti++) {
    if (t[ti] === q[qi]) {
      if (first < 0) first = ti;
      run++; score += 1 + run;                                   // consecutive matches compound
      if (ti === 0 || /[\s/·—-]/.test(t[ti - 1])) score += 3;    // prefix / word-boundary bonus
      qi++;
    } else run = 0;
  }
  if (qi < q.length) return -1;                                  // not a subsequence -> drop
  return score + Math.max(0, 12 - first);                        // earlier first hit -> better
}

// Static command set (sections + every module's Read/Quiz). Built once — ~340
// modules -> ~680 entries — and cached; verbs are rebuilt per open (state-aware).
let _palStatic = null;
function palStaticCommands() {
  if (_palStatic) return _palStatic;
  const out = [];
  for (const s of Object.keys(state.index.sections || {}).sort())
    out.push({ label: `Start ${label(s)} blitz`, hint: "blitz", run: () => startBlitz(s) });
  for (const [mod, list] of Object.entries(state.index.files || {})) {
    const name = titleize(mod.split("/").pop()), sec = mod.split("/")[0];
    const file = (list && list[0]) || "README.md";
    out.push({ label: `Read ${name}`, hint: label(sec), run: () => { reader.back = []; openReaderPath(`${mod}/${file}`, name); } });
    out.push({ label: `Quiz ${name}`, hint: label(sec), run: () => startBlitz(sec, [mod]) });
  }
  _palStatic = out;
  return out;
}
function palVerbs() {
  const out = [];
  if (readDeckSnapshot()) out.push({ label: "Resume blitz", hint: "verb", run: () => resumeDeck() });
  const due = dueReviews().length;
  out.push({ label: `Start review${due ? ` (${due} due)` : ""}`, hint: "verb", run: () => startReview() });
  out.push({ label: "Weak spots", hint: "verb", run: () => startWeakSpots() });
  out.push({ label: "Today's gauntlet", hint: "verb", run: () => go("#/gauntlet") });
  out.push({ label: "Codex", hint: "verb", run: () => go("#/codex") });
  out.push({ label: "Insights", hint: "verb", run: () => go("#/progress") });
  out.push({ label: "Debrief", hint: "verb", run: () => go("#/debrief") });
  const flash = deckMode() === "flash";
  out.push({ label: flash ? "Quiz mode (from flashcards)" : "Flashcards mode (from quiz)", hint: "verb",
    run: () => { safeSet("sd_mode", flash ? "quiz" : "flash"); syncModeBtn(); if (!state.inQuiz) renderHome(); } });
  for (const t of ["midnight", "orchid", "ember", "daylight"]) out.push({ label: `Theme: ${t}`, hint: "theme", run: () => applyTheme(t) });
  out.push({ label: "Export progress", hint: "verb", run: () => exportProgress() });
  return out;
}
// A cached section bank lets us offer full-text question search without a fetch.
function palSearchSection(query) {
  const cached = Object.keys(bankCache).filter((k) => bankCache[k] && bankCache[k].length);
  if (!cached.length) return null;
  const ql = query.toLowerCase();
  const named = cached.find((s) => label(s).toLowerCase().split(/\s+/).some((w) => w.length > 2 && ql.includes(w)) || ql.includes(s));
  return named || cached[cached.length - 1];
}

let _palette = null, _palKey = null;
function closePalette() {
  if (!_palette) return;
  _palette._release?.();
  _palette.remove();
  _palette = null;
  if (_palKey) { document.removeEventListener("keydown", _palKey, true); _palKey = null; }
}
function openPalette() {
  if (_palette) return;
  const o = document.createElement("div");
  o.className = "palette-overlay"; o.id = "paletteOverlay";
  o.innerHTML = `<div class="palette" role="dialog" aria-label="Command palette" aria-modal="true">
      <input type="text" class="pal-input" id="palInput" placeholder="Jump to a section, module, or command…" autocomplete="off" autocapitalize="off" spellcheck="false" role="combobox" aria-expanded="true" aria-autocomplete="list" aria-label="Command palette search" aria-controls="palList" />
      <ul class="pal-list" id="palList" role="listbox"></ul></div>`;
  document.body.appendChild(o);
  _palette = o;
  const input = el("#palInput"), list = el("#palList");
  let results = [], sel = 0, search = null;                      // search = {section} sub-mode

  function topResults(query) {
    const cmds = query ? [] : palVerbs().slice();                // empty query -> verbs first (calm default)
    if (query) {
      const all = [...palVerbs(), ...palStaticCommands()], scored = [];
      for (const c of all) { const sc = fuzzyScore(query, c.label); if (sc >= 0) scored.push({ c, sc }); }
      scored.sort((a, b) => b.sc - a.sc);
      for (const x of scored.slice(0, 40)) cmds.push(x.c);
    }
    // Final "search questions" affordance when a section bank is already loaded.
    const sec = query.length >= 2 ? palSearchSection(query) : null;
    if (sec) cmds.push({ label: `Search questions for “${query}” in ${label(sec)}…`, hint: "search",
      run: () => enterSearch(sec, query) });
    return cmds;
  }
  function questionResults(query) {
    const bank = bankCache[search.section] || [];
    const toks = query.toLowerCase().split(/\s+/).filter(Boolean);
    const hits = bank.filter((q) => { const t = (q.question || "").toLowerCase(); return toks.every((tk) => t.includes(tk)); }).slice(0, 8);
    return hits.map((q) => ({ label: q.question, hint: q.moduleName, run: () => openReaderPath(`${q.module}/${q.sourceFile || "README.md"}`, q.moduleName) }));
  }
  function enterSearch(section, query) {
    search = { section };
    input.value = query;
    render();
  }
  const setActiveDesc = () => input.setAttribute("aria-activedescendant", results.length ? "pal-opt-" + sel : "");
  function render() {
    results = search ? questionResults(input.value.trim()) : topResults(input.value.trim());
    if (sel >= results.length) sel = Math.max(0, results.length - 1);
    list.innerHTML = results.length
      ? results.map((r, i) => `<li class="pal-item${i === sel ? " sel" : ""}" id="pal-opt-${i}" role="option" aria-selected="${i === sel}" data-i="${i}">
          <span class="pal-label">${esc(r.label)}</span>${r.hint ? `<span class="pal-hint">${esc(r.hint)}</span>` : ""}</li>`).join("")
      : `<li class="pal-empty">${search ? "No questions match." : "No matches."}</li>`;
    list.querySelectorAll(".pal-item").forEach((li) => {
      li.addEventListener("mousemove", () => { sel = +li.dataset.i; markSel(); });
      li.addEventListener("click", () => activate(+li.dataset.i));
    });
    setActiveDesc();
  }
  function markSel() {
    list.querySelectorAll(".pal-item").forEach((li, i) => { const on = i === sel; li.classList.toggle("sel", on); li.setAttribute("aria-selected", on); });
    list.querySelector(".pal-item.sel")?.scrollIntoView({ block: "nearest" });
    setActiveDesc();
  }
  function activate(i) {
    const r = results[i];
    if (!r) return;
    if (r.hint === "search") { r.run(); return; }               // stay open: switch into search sub-mode
    closePalette();
    guardedNav(r.run);                                          // leaving a live blitz raises the pause sheet
  }
  input.addEventListener("input", () => { if (search) search = null; sel = 0; render(); });
  _palKey = (e) => {
    if (!_palette) return;
    if (e.key === "Escape") { e.preventDefault(); e.stopPropagation(); if (search) { search = null; sel = 0; render(); } else closePalette(); return; }
    if (e.key === "ArrowDown") { e.preventDefault(); e.stopPropagation(); if (results.length) { sel = (sel + 1) % results.length; markSel(); } return; }
    if (e.key === "ArrowUp") { e.preventDefault(); e.stopPropagation(); if (results.length) { sel = (sel - 1 + results.length) % results.length; markSel(); } return; }
    if (e.key === "Enter") { e.preventDefault(); e.stopPropagation(); activate(sel); return; }
    // any other key: let it reach the input (typing)
  };
  document.addEventListener("keydown", _palKey, true);
  o.addEventListener("pointerdown", (e) => { if (e.target === o) closePalette(); });
  render();
  _palette._release = trapFocus(o, { initial: "#palInput" });
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
    ${insightsHTML()}
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
  wireInsights();                                   // [E2] Insights: drill buttons, lazy leeches, heatmap scroll-to-today
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
    <div class="study-head"><button class="ghost" id="studySearch" aria-label="Search or jump to a topic" title="Search (press /)"><svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="11" cy="11" r="7"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg><span>Search</span></button></div>
    ${contCard}
    <h2 class="section-h">Pick a section to browse its topics</h2>
    <div class="grid">${tiles}</div>
    <div class="row" style="margin-top:18px"><button class="ghost" id="studyHome">&larr; Home</button></div>`;
  document.querySelectorAll(".tile").forEach((b) => b.addEventListener("click", () => go("#/study/" + b.dataset.section)));
  if (contCard) el("#contBtn").addEventListener("click", () => { reader.back = []; openReaderPath(lastRead.path, lastRead.title, null); });
  el("#studySearch").addEventListener("click", () => openPalette());
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

// Book picker — the extra navigation level for the book section: one node per
// book (reading order = STUDY_ORDER order), each opening that book's own chapter
// graph at #/study/book/<book_slug>. Reuses the section-tile look so it reads as
// "pick a shelf" rather than a 60-node flat snake.
function renderBookPicker(section, mods) {
  const groups = [];
  const byBook = new Map();
  mods.forEach((m) => {
    const slug = bookOf(m.module) || m.module.split("/")[1] || m.module;
    if (!byBook.has(slug)) { byBook.set(slug, { slug, chapters: 0, qs: 0 }); groups.push(byBook.get(slug)); }
    const g = byBook.get(slug);
    g.chapters++; g.qs += m.count;
  });
  const tiles = groups.map((g, i) => {
    const meta = BOOK_LABELS[g.slug] || {};
    return `<button class="tile bookcard" data-book="${esc(g.slug)}">
      <span class="bk-num">${String(i + 1).padStart(2, "0")}</span>
      <span class="bk-body">
        <span class="tname">${esc(bookLabel(g.slug))}</span>
        <span class="tmeta">${meta.author ? esc(meta.author) + " &middot; " : ""}${g.chapters} chapters &middot; ${g.qs} Qs</span>
      </span>
      <span class="bk-go" aria-hidden="true">&rarr;</span>
    </button>`;
  }).join("");
  app.innerHTML = `
    <div class="hero"><h1>${esc(label(section))}</h1>
      <p>${groups.length} books &middot; ${mods.length} chapters &middot; pick a book to open its chapter path.</p></div>
    <div class="grid bookshelf">${tiles}</div>
    <div class="row" style="margin-top:18px"><button class="ghost" id="studyBack">&larr; Sections</button></div>`;
  document.querySelectorAll(".bookcard").forEach((b) =>
    b.addEventListener("click", () => go("#/study/book/" + b.dataset.book)));
  el("#studyBack").addEventListener("click", () => go("#/study"));
  wireReveals();
}

// [W5] the study path's resize listener, tracked module-level so re-entering the
// screen removes the prior one instead of stacking a new listener each visit.
let _pathResize = null;
async function openStudySection(sectionPath) {
  // The book section adds one navigation level: "#/study/book" shows one node per
  // book; "#/study/book/<book_slug>" is that book's own chapter graph. All other
  // sections pass through unchanged (sectionPath === section).
  const [section, ...scopeRest] = (sectionPath || "").split("/");
  const bookScope = section === "book" && scopeRest.length ? scopeRest.join("/") : null;
  app.innerHTML = skeletonHTML("study");
  // [PERF] Draw the skill tree from the boot-loaded index (module list + counts),
  // NOT the multi-MB question bank. Clicking a section used to download the whole
  // bank (cs_fundamentals ~1.4MB, llm ~3.9MB) just to render nodes — a multi-second
  // stall on mobile. The bank now loads only when a quiz starts; we warm it in the
  // background so the first node-tap stays instant.
  // [CS] Case Studies track (a third path beside Full/Interview): nodes come from
  // index.caseStudies (README-curated), open the reader read-only, and are NOT in
  // the bank. Guard a stale stored "cases" value on a section with <2 case studies.
  const casesList = caseStudiesFromIndex(section);
  const hasCases = casesList.length >= 2;
  const interviewPath = STUDY_PATHS[section] && STUDY_PATHS[section].interview;
  let studyPath = getStudyPath(section);                // "full" | "interview" | "cases"
  if (studyPath === "cases" && !hasCases) studyPath = "full";
  const casesMode = studyPath === "cases" && hasCases;

  let mods;
  if (casesMode) {
    // module = the reader path itself (unique; never collides with a real module id).
    mods = casesList.map((c) => ({ module: c.file, name: c.name, count: 0 }));
  } else {
    mods = modulesFromIndex(section);
    if (!mods.length) {                                // index unavailable (pre-boot / cold offline) — fall back to the bank
      const bank = await loadBank(section);
      if (!bank || !bank.length) {
        errorScreen(`Couldn't load ${label(section)}`, `Check your connection and try again.${devDetail(`Run <code>python3 extract.py</code>.`)}`, () => openStudySection(sectionPath));
        return;
      }
      mods = modulesOf(bank);
    } else {
      loadBank(section).catch(() => {});               // non-blocking warm-up for the first quiz
    }
    if (section === "book" && !bookScope) { renderBookPicker(section, mods); return; }
    if (bookScope) {
      mods = mods.filter((m) => bookOf(m.module) === bookScope);
      if (!mods.length) { go("#/study/book"); return; }   // unknown/renamed book slug
    }
    // Interview-Specific path: restrict the skill tree to the curated subset (mods is
    // already in STUDY_ORDER == interview-list order, so a filter preserves order).
    if (studyPath === "interview" && interviewPath) {
      const keep = new Set(interviewPath);
      mods = mods.filter((m) => keep.has(m.module));
    }
  }
  // v2: weighted prerequisite edges from graph/<section>.json (real repo
  // cross-links + lexical Q&A overlap). Pairs are undirected; orient each one
  // forward along the path order. Missing/failed file -> plain v1 path.
  const graph = casesMode ? null : await fetchJSON(`graph/${section}.json`, null, "default");   // [CS] no cross-link graph for case studies
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
  const list = casesMode
    ? mods.map((m) => ({ path: m.module, title: m.name }))            // [CS] module IS the reader path
    : mods.map((m) => ({ path: `${m.module}/README.md`, title: m.name }));
  const files = (state.index && state.index.files) || {};
  // Practiced = any spaced-repetition entry from this module (real history only).
  const practiced = new Set(Object.values(state.progress.reviews || {}).map((r) => r.module).filter(Boolean));
  // [CS] Case studies are read-only: a node's "done" state is whether it's been READ.
  const read = casesMode ? new Set(mods.filter((m) => isModuleRead(m.module)).map((m) => m.module)) : null;
  const mstats = moduleStats(state.progress);          // [A2] Module Memory Map retention tints
  // "You are here" = last page opened in the reader, if it lives in this section.
  let lastRead = null;
  try { lastRead = JSON.parse(localStorage.getItem("sd_last_read")); } catch { }
  const herePath = lastRead && lastRead.path && lastRead.path.startsWith(section + "/") ? lastRead.path : null;
  // Resolve the module by prefix against the real module list — book modules are
  // three segments deep (book/<book>/<chapter>), so a fixed slice(0, 2) mislabels them.
  const hereMod = herePath
    ? (casesMode
        ? (mods.find((m) => herePath === m.module) || {}).module
        : (mods.find((m) => herePath === `${m.module}/README.md` || herePath.startsWith(m.module + "/")) || {}).module) || null
    : null;
  const openFans = new Set();
  if (herePath && hereMod && !/\/README\.md$/i.test(herePath)) openFans.add(hereMod);  // reveal the "here" leaf

  const leafLabel = (fn) => (fn === "README.md" ? "Readme" : titleize(fn.replace(/\.md$/i, "")));
  const steps = mods.map((m, i) => {
    const mFiles = files[m.module] || ["README.md"];
    const multi = mFiles.length > 1;
    const isHere = m.module === hereMod;
    const isOpen = openFans.has(m.module);
    // [A2] Memory Map: tint each node by estimated retention (+ amber pulse when overdue).
    // [CS] Case-study nodes: "done" = read (no retention data / no Q count).
    const done = casesMode ? read.has(m.module) : practiced.has(m.module);
    const ms = casesMode ? null : mstats[m.module];
    let mmCls = casesMode ? "" : " mm-cold", retTxt = casesMode ? (done ? "read" : "case study") : "no review data yet";
    if (ms) {
      mmCls = ms.retention >= 0.75 ? " mm-fresh" : ms.retention >= 0.5 ? " mm-warm" : " mm-fading";
      retTxt = `est. retention ${Math.round(ms.retention * 100)}%, ${ms.overdue} due`;
      if (ms.overdue > 0) mmCls += " mm-overdue";
    }
    const metaTxt = casesMode ? "Case study" : `${m.count} Qs`;
    const ariaCount = casesMode ? "case study" : `${m.count} questions`;
    const leaves = multi ? mFiles.map((fn, k) => {
      const p = `${m.module}/${fn}`;
      return `<button class="pathleaf${p === herePath ? " here" : ""}" data-idx="${i}" data-path="${esc(p)}" style="animation-delay:${k * 30}ms">${esc(leafLabel(fn))}</button>`;
    }).join("") : "";
    return `<div class="pathstep${isOpen ? " open" : ""}">
      <div class="pathnode${done ? " practiced" : ""}${isHere ? " here" : ""}${mmCls}" title="${esc(retTxt)}">
        <button class="pn-main" data-idx="${i}" aria-label="Step ${i + 1} of ${mods.length}: ${esc(m.name)}, ${ariaCount}, ${retTxt}${peers[i].size ? `, connects to ${peers[i].size} other topics` : ""}">
          <span class="pn-num">${String(i + 1).padStart(2, "0")}</span>
          <span class="pn-body">
            <span class="pn-name">${esc(m.name)}</span>
            <span class="pn-meta">${metaTxt}${isHere ? ` &middot; <b class="pn-here">you are here</b>` : ""}</span>
          </span>
          ${done ? `<span class="pn-check" title="${casesMode ? "Read" : "Practiced"}">✓</span>` : ""}
        </button>
        ${multi ? `<button class="pn-fan" aria-expanded="${isOpen}" aria-controls="fan-${i}" aria-label="${mFiles.length} files in ${esc(m.name)}"><span class="pn-arrow">&#9656;</span>&nbsp;${mFiles.length} files</button>` : ""}
      </div>
      ${multi ? `<div class="leaf-fan" id="fan-${i}"${isOpen ? "" : " hidden"}>${leaves}</div>` : ""}
    </div>`;
  }).join("");

  const onInterview = studyPath === "interview" && interviewPath;
  // [CS] Path toggle: Full always; Interview iff a curated subset exists; Case
  // Studies iff the section has >=2. Rendered only when >=2 options exist.
  const pathOpts = [
    { v: "full", label: "Full" },
    interviewPath ? { v: "interview", label: "Interview" } : null,
    hasCases ? { v: "cases", label: "Case Studies" } : null,
  ].filter(Boolean);
  const pathSwitchHtml = pathOpts.length >= 2 ? `
      <div class="pathswitch" role="radiogroup" aria-label="Learning path">
        <span class="pathswitch-label">Path</span>
        ${pathOpts.map((o) => `<button class="pathopt${studyPath === o.v ? " on" : ""}" role="radio" aria-checked="${studyPath === o.v}" data-path="${o.v}">${o.label}</button>`).join("")}
      </div>` : "";
  const bookMeta = bookScope ? (BOOK_LABELS[bookScope] || {}) : null;
  app.innerHTML = `
    <div class="path-screen">
    <div class="hero">${bookScope ? `<p class="eyebrow">${esc(label(section))}${bookMeta.author ? " &middot; " + esc(bookMeta.author) : ""}</p>` : ""}<h1>${esc(bookScope ? bookLabel(bookScope) : label(section))}</h1>
      <p>${casesMode
        ? `${mods.length} case studies &middot; read-only &mdash; ${singleColumnPath() ? "work through them top to bottom" : "the path snakes across each row"} in the section's suggested order.`
        : `${mods.length} ${bookScope ? "chapters" : "topics"}${onInterview ? " &middot; interview-specific path" : ""} &middot; start at 01 &mdash; ${singleColumnPath()
          ? `follow them top to bottom in the ${bookScope ? "book's chapter order" : "section's learning order"}.`
          : `the path snakes across each row in the ${bookScope ? "book's chapter order" : "section's learning order"}.`}`}</p>
      ${graph && !coarsePointer() ? `<p class="path-legend">${crossLinks
        ? `strongest prerequisite links drawn &middot; hover a topic to see all its connections &middot; ${crossLinks} cross-links mapped`
        : "no cross-link data yet &mdash; path order shown"}</p>` : ""}</div>
    <div class="topicbar">
      ${pathSwitchHtml}
      <input type="search" class="filter" id="studyFilter" placeholder="Filter topics" aria-label="Filter topics" />
      <span class="selcount" id="pathCount" role="status"></span>
    </div>
    <div class="path-wrap" id="pathWrap">
      <svg class="path-svg" id="pathSvg" aria-hidden="true">
        <!-- userSpaceOnUse, not the default objectBoundingBox: on a phone the
             layout is a single column, so every spine segment is a straight
             vertical line and the path's bounding box is ZERO WIDTH. SVG does
             not paint an objectBoundingBox gradient over a degenerate box, so
             the whole connecting path silently vanished and the cards read as
             floating in dead space. y2 is refreshed in layoutPath(). -->
        <defs><linearGradient id="lpGrad" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="0" y2="600">
          <stop offset="0" style="stop-color:var(--accent)"/>
          <stop offset="1" style="stop-color:var(--accent-2)"/>
        </linearGradient></defs>
        <g class="lp-chords" id="lpChords"></g>
        <path class="lp-spine" id="lpSpine" d=""/>
        <path class="lp-leaves" id="lpLeaves" d=""/>
      </svg>
      ${steps}
    </div>
    <div class="row" style="margin-top:18px"><button class="ghost" id="studyBack">&larr; ${bookScope ? "Books" : "Sections"}</button></div>
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
    // [E2] mobile: prerequisite chords are a hover-only affordance — dead weight on
    // touch. On coarse pointers the chord SVG is hidden (CSS) and the layout drops
    // the wide gutters that only existed to route those lines, reclaiming the width.
    const coarse = window.matchMedia("(pointer: coarse)").matches;
    const hasEdges = chords.length > 0 && !coarse;
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
    el("#lpGrad")?.setAttribute("y2", H);          // userSpaceOnUse gradient spans the laid-out height

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
    const openIt = () => { reader.back = []; openReaderPath(list[idx].path, list[idx].title, { list, idx }); };
    // [A2] Prime: a 3-question pretest before reading a never-quizzed module. Only
    // for real bank-backed modules — the bank is warmed in the background, so read it
    // from cache and skip prime if it isn't loaded yet or in the read-only case track.
    const bank = bankCache[section];
    if (!casesMode && bank && maybePrime(section, mods[idx], bank, openIt)) return;
    openIt();
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
  document.querySelectorAll(".pathopt").forEach((b) => b.addEventListener("click", () => {
    if (b.classList.contains("on")) return;            // already active
    setStudyPath(section, b.dataset.path);
    announce(b.dataset.path === "interview" ? "Interview-specific path" : b.dataset.path === "cases" ? "Case Studies — read-only" : "Full path");
    openStudySection(section);                         // re-render with the new subset
  }));
  wireRadioGroup(el(".pathswitch"));
  let rzT = 0;
  const onResize = () => {                         // debounced; self-removes once the screen is gone
    if (!document.body.contains(wrap)) { window.removeEventListener("resize", onResize); return; }
    clearTimeout(rzT);
    rzT = setTimeout(layoutPath, 160);
  };
  if (_pathResize) window.removeEventListener("resize", _pathResize);  // [W5] don't stack across visits
  _pathResize = onResize;
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
  cpp: { block: 1, line: ["//"], str: '"\'', num: 1, capType: 1, fn: 1,
    kw: set("alignas alignof asm auto break case catch class concept const consteval constexpr constinit const_cast continue co_await co_return co_yield decltype default delete do dynamic_cast else enum explicit export extern for friend goto if inline mutable namespace new noexcept operator override private protected public register reinterpret_cast requires return sizeof static static_assert static_cast struct switch template this thread_local throw try typedef typeid typename union using virtual volatile while __global__ __device__ __host__ __shared__ __constant__ __restrict__ __forceinline__ __launch_bounds__ __managed__ __noinline__ __align__"),
    bi: set("void bool char short int long float double signed unsigned wchar_t char8_t char16_t char32_t size_t ptrdiff_t intptr_t uintptr_t nullptr NULL true false int8_t int16_t int32_t int64_t uint8_t uint16_t uint32_t uint64_t uint half float2 float3 float4 double2 double4 int2 int3 int4 uint2 uint3 uint4 char4 uchar4 dim3 cudaError_t cudaStream_t cudaEvent_t threadIdx blockIdx blockDim gridDim warpSize std string vector cout cin cerr endl") },
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
  cuda: "cpp", cu: "cpp", cuh: "cpp", c: "cpp", cc: "cpp", cxx: "cpp",
  "c++": "cpp", h: "cpp", hpp: "cpp", ptx: "cpp",
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

// Which way the *reading column* runs, not the device: a phone in portrait and
// a narrow reader pane beside two sidebars have the same problem, and a phone
// rotated to landscape has the laptop's. Rotating re-runs the choice through
// the ResizeObserver below, so the same diagram follows the device.
const mmPortrait = (avail) => window.innerHeight > window.innerWidth && avail <= 560;

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
    delete n.dataset.mmFit;                          // back to auto sizing (floors apply)
    const avail = mmAvailWide(n);
    mmLayout(n, sv, Math.min(+sv.dataset.natw || avail, Math.max(avail, +sv.dataset.minw || 0)));
  });
}

// "Fit width" pill — visible (via .h-scroll CSS) only while the diagram
// overflows its column. Click scales the svg down to the live available
// width between the sidebars; the choice sticks on the container
// (data-mm-fit) so later resizes and sidebar collapses keep re-fitting.
function mmAddFit(n, sv) {
  const b = document.createElement("button");
  b.className = "mm-fit";
  b.textContent = "↔ fit";
  b.title = "Scale diagram to fit the available width";
  b.setAttribute("aria-label", "Fit diagram to available width");
  b.addEventListener("click", (e) => {
    e.stopPropagation();
    n.dataset.mmFit = "1";
    delete sv.dataset.custom;                        // fit overrides a manual drag size
    const avail = mmAvailWide(n);
    mmLayout(n, sv, Math.min(+sv.dataset.natw || avail, avail));
  });
  n.appendChild(b);
}

// The orientation choice depends on the column width, so it is re-evaluated
// live: widen the reader (drag grip / fullscreen) and tall TD diagrams flip
// to LR the moment the horizontal room makes that the better read. Small
// width changes just re-clamp; >15% changes re-render and re-choose.
// Diagrams the user resized by hand are left alone entirely.
const _mmSrc = new WeakMap();     // .mermaid container -> raw diagram source
let _mmRO = null;
let _mmROTimer = null;
let _mmIO = null;                 // [PERF] lazy-render IntersectionObserver for off-screen diagrams
function mmObserve(n) {
  if (!_mmRO) {
    _mmRO = new ResizeObserver(() => {
      clearTimeout(_mmROTimer);
      _mmROTimer = setTimeout(() => {
        document.querySelectorAll(".md-body .mermaid").forEach((el) => {
          const sv = el.querySelector("svg");
          if (!sv || sv.dataset.custom) return;
          const avail = mmAvailWide(el);
          const was = +el.dataset.mmAvail || avail;
          const asset = _mmAsset.get(el);
          if (Math.abs(avail - was) / was > 0.15) {            // width moved enough to re-choose orientation
            if (asset) {                                        // [SF] pre-rendered: engine-free re-orient
              const pick = mmChooseVariant(asset, avail);
              if (pick.variant !== el.dataset.mmVariant) injectPrerendered(el, asset);   // orientation flipped -> swap the baked SVG
              else { el.dataset.mmAvail = avail; mmLayout(el, sv, Math.min(+sv.dataset.natw || avail, Math.max(avail, +sv.dataset.minw || 0))); }
            } else {
              const src = _mmSrc.get(el);
              if (src) mmRenderNode(el, src);                   // live fallback: re-render to re-choose orientation
            }
          } else if (el.dataset.mmFit) {
            mmLayout(el, sv, Math.min(+sv.dataset.natw || avail, avail));   // track live width, no floors
          } else {
            mmLayout(el, sv, Math.min(+sv.dataset.natw || avail, Math.max(avail, +sv.dataset.minw || 0)));  // re-clamp, honor floor
          }
        });
      }, 250);
    });
  }
  _mmRO.observe(n);
  // Sidebar collapse can change only the GUTTERS (mmExtra) while the prose
  // column stays at its cap — the container never resizes, so watch the
  // sidebars too. Re-observing the same element is a no-op.
  n.closest(".reader-body")?.querySelectorAll(".reader-modules, .reader-toc")
    .forEach((s) => _mmRO.observe(s));
}

// Render one diagram into its container: pick the orientation that reads best
// at the container's current width, size it, and wire grip + zoom.
// [SF] Bake all appearance polish INTO the SVG content — chart post-process,
// sequence-note widening, rounded boxes, blue arrowheads, viewBox fix, plain-node
// tint. Extracted verbatim from mmRenderNode's tail so the build-time pre-renderer
// produces SVGs byte-identical to live output. Sizing/interaction stay runtime-only.
function mmPolishSvg(n, ctype) {
  const sv = n.querySelector("svg");
  // Sequence notes/actors: even with matched measure/display fonts, mermaid
  // under-measures long monospace lines by a few percent (same failure class
  // mmFixViewBox patches at canvas level), so wrapped text can poke past its
  // rect onto the black canvas where the dark text becomes unreadable. The
  // rect is decorative background — widen it to cover its own text.
  n.querySelectorAll("rect.note, rect.actor").forEach(r => {
    const rb = r.getBoundingClientRect();
    if (!rb.width) return;
    const k = r.width.baseVal.value / rb.width;            // screen px -> svg units
    let over = 0;
    r.parentElement.querySelectorAll("text").forEach(t => {
      const tb = t.getBoundingClientRect();
      const cy = (tb.top + tb.bottom) / 2;
      if (cy < rb.top || cy > rb.bottom) return;           // different row of the group
      if (tb.right < rb.left || tb.left > rb.right) return; // unrelated column
      over = Math.max(over, rb.left - tb.left, tb.right - rb.right);
    });
    if (over > 0.5) {
      const pad = (over + 4) * k;
      r.setAttribute("x", r.x.baseVal.value - pad);
      r.setAttribute("width", r.width.baseVal.value + 2 * pad);
    }
  });
  // Post-process SVG: round EVERY box — flowchart nodes, sequence actors and
  // notes, alt/opt frames, timeline periods — not just .node rects. Chart data
  // marks (xychart bars, pie slices, quadrant fills) keep square corners: rx
  // there reshapes the mark itself. Tiny rects (<12px tall) are left alone.
  if (!["xychart", "pie", "quadrant"].includes(ctype)) {
    n.querySelectorAll("svg rect").forEach(r => {
      const h = r.height?.baseVal?.value || 0;
      if (h < 12) return;
      const rx = Math.min(8, Math.round(h / 3));
      r.setAttribute("rx", rx); r.setAttribute("ry", rx);
    });
  }
  n.querySelectorAll(".cluster rect").forEach(r => { r.setAttribute("rx", "12"); r.setAttribute("ry", "12"); });
  // Color arrowhead markers (marker fill is independent of lineColor themeVariable)
  n.querySelectorAll("marker path, marker polygon").forEach(m => { m.setAttribute("fill", "#61afef"); m.removeAttribute("stroke"); });
  if (sv) mmFixViewBox(sv);                              // widened rects may poke past the canvas
  mmTintPlain(n);
}

// [SF] Build-time hook: render BOTH orientations of one fence through the LIVE
// pipeline (same engine, config, and mmPolishSvg) at a canonical width, and
// return finished SVG strings + measured dims for each. The Puppeteer build
// (scripts/build_diagrams.mjs) calls this per fence — so pre-rendered assets are
// produced by the exact runtime code, guaranteeing byte-identity. svg0 = authored
// orientation, svg1 = flipped alt (null when the diagram has no meaningful flip).
async function __mmBuildVariants(src, width) {
  const mermaid = await _mermaidReady;
  const one = async (source) => {
    const n = document.createElement("div");
    n.className = "mermaid md-body";
    Object.assign(n.style, { position: "absolute", left: "-99999px", top: "0", width: width + "px" });
    document.body.appendChild(n);
    try {
      const ctype = mmChartType(source);
      const svg = (await mermaid.render("mmb" + (++_mmSeq), mmChartDirective(ctype, source, width) + source)).svg;
      n.innerHTML = svg;
      const sv = n.querySelector("svg");
      if (ctype) mmChartPostProcess(ctype, sv);
      if (sv) mmFixViewBox(sv);
      mmPolishSvg(n, ctype);
      const out = n.querySelector("svg").outerHTML;
      return { svg: out, dims: mmDims(out) };
    } finally { n.remove(); }
  };
  const base = await one(src);
  const altSrc = mmAltOrientation(src);
  const alt = altSrc ? await one(altSrc) : null;
  return {
    v: 1,
    ctype: mmChartType(src) || null,                  // timeline/xychart/pie/quadrant sizing on the runtime side
    svg0: base.svg, d0: base.dims ? [Math.round(base.dims.w), Math.round(base.dims.h)] : null,
    svg1: alt ? alt.svg : null, d1: alt && alt.dims ? [Math.round(alt.dims.w), Math.round(alt.dims.h)] : null,
  };
}
if (typeof window !== "undefined") window.__mmBuildVariants = __mmBuildVariants;

async function mmRenderNode(n, src) {
  const mermaid = await ensureMermaid(n);
  if (!mermaid) return;
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
          // shrinking further.
          const s1 = Math.min(1, Math.max(avail / d1.w, 0.7));
          const visible = Math.min(1, avail / (d1.w * s1));
          if (mmPortrait(avail)) {
            // A tall, narrow column (phone held upright, reader pane on a
            // split screen) is the mirror image of the widescreen case: here a
            // wide LR diagram is squeezed to a microscopic strip while height
            // is the plentiful axis. The height test above never fires for it —
            // a shrunken diagram is short by definition — so compare how large
            // each orientation can actually be drawn and take the legible one.
            // The 1.25x margin keeps a near-tie from oscillating on resize.
            const s0 = Math.min(1, avail / d0.w);
            const s1fit = Math.min(1, avail / d1.w);
            // Refuse a swap that trades a squeezed diagram for an endless one.
            const sane = d1.h * s1fit <= window.innerHeight * 3.5;
            if (s1fit > s0 * 1.25 && sane) { svg = altSvg; flipScale = s1fit; }
          } else if (d1.h * s1 < dispH(d0) * 0.7 && visible >= 0.55) {
            // Widescreen: flip when it cuts the on-screen height by >=30% and
            // at least ~55% of the flipped diagram is visible at once.
            svg = altSvg; flipScale = s1;
          }
        }
      } catch { /* flipped source failed to parse — keep the original */ }
    }
  } catch (err) {
    document.getElementById("dmm" + _mmSeq)?.remove();   // mermaid's temp scratch div
    console.warn("Mermaid render failed:", err);         // raw source stays visible
    if (!n.querySelector(".mm-fail")) n.insertAdjacentHTML("afterbegin", `<div class="mm-fail">Diagram couldn't render — showing its source instead.</div>`);
    return;
  }
  n.innerHTML = svg;                                     // replaces old svg + grip
  const sv = n.querySelector("svg");
  let d = mmDims(svg);
  if (sv && ctype) mmChartPostProcess(ctype, sv);        // may grow the bbox (rotated ticks)
  if (sv) { const fixed = mmFixViewBox(sv); if (fixed) d = fixed; }
  if (sv && d) {
    sv.dataset.natw = Math.round(d.w);
    if (n.dataset.mmFit) {
      mmLayout(n, sv, Math.min(d.w, avail));         // user chose fit-to-width: no floors
    } else if (flipScale) {
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
    mmAddFit(n, sv);
  }
  mmPolishSvg(n, ctype);                                 // [SF] appearance baking, shared with the build-time pre-renderer
  if (!n.dataset.mmWired) {                              // once per container, not per render
    n.dataset.mmWired = "1";
    n.tabIndex = 0;
    n.setAttribute("role", "button");
    n.setAttribute("aria-label", "Diagram — open zoom view");
    n.addEventListener("click", () => openMermaidZoom(n));
    n.addEventListener("keydown", (e) => {
      if (e.key === "Enter" || e.key === " ") { e.preventDefault(); openMermaidZoom(n); }
    });
  }
}

// Consistency fallback: stateDiagrams (and any flowchart authored without the
// classDef palette) render every node in flat mainBkg gray, which reads
// broken next to fully-colored diagrams. Tint each node still at the default
// mainBkg fill from the One Dark palette in encounter order; nodes that carry
// an authored color are skipped, so partial colouring degrades per node (your
// colours kept, the gaps filled) rather than all-or-nothing.
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
// True content bounds in the root svg's user space, unioned from LEAF shapes.
//
// Never ask a <g> (or the root <svg>) for its own bbox here: the Android
// WebView answers that with garbage. A six-node flowchart whose every drawable
// leaf fits inside 256px reported a 16699px-tall group, and simple flowcharts
// report ~2048 regardless of content. Leaf bboxes measure correctly on both
// engines, so union those instead. Each leaf's box is in its OWN user space,
// so it is mapped through the element's CTM into the root's space before
// unioning — mermaid transforms nearly every group.
function mmContentBounds(sv) {
  const rootCTM = sv.getScreenCTM();
  if (!rootCTM) return null;
  let inv;
  try { inv = rootCTM.inverse(); } catch { return null; }
  let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity, seen = 0;
  sv.querySelectorAll("path,rect,circle,ellipse,polygon,polyline,line,text,image,foreignObject").forEach((el) => {
    if (el.closest("defs,marker,clipPath,mask")) return;   // never painted in place
    let b, m;
    try { b = el.getBBox(); m = el.getScreenCTM(); } catch { return; }
    if (!b || !m || (!b.width && !b.height)) return;
    const t = inv.multiply(m);                             // element space -> root user space
    for (const [px, py] of [[b.x, b.y], [b.x + b.width, b.y], [b.x, b.y + b.height], [b.x + b.width, b.y + b.height]]) {
      const X = t.a * px + t.c * py + t.e, Y = t.b * px + t.d * py + t.f;
      if (X < x0) x0 = X; if (X > x1) x1 = X;
      if (Y < y0) y0 = Y; if (Y > y1) y1 = Y;
    }
    seen++;
  });
  if (!seen || !isFinite(x0) || x1 <= x0 || y1 <= y0) return null;
  return { x: x0, y: y0, width: x1 - x0, height: y1 - y0 };
}

function mmFixViewBox(sv) {
  try {
    const bb = mmContentBounds(sv) || sv.getBBox();
    const vb = sv.viewBox.baseVal;
    const over = bb.x < vb.x - 2 || bb.y < vb.y - 2 ||
                 bb.x + bb.width > vb.x + vb.width + 2 ||
                 bb.y + bb.height > vb.y + vb.height + 2;
    if (over) {
      const x = Math.floor(Math.min(bb.x, vb.x)) - 8;
      const y = Math.floor(Math.min(bb.y, vb.y)) - 8;
      const w = Math.ceil(Math.max(bb.x + bb.width, vb.x + vb.width)) - x + 8;
      const h = Math.ceil(Math.max(bb.y + bb.height, vb.y + vb.height)) - y + 8;
      sv.setAttribute("viewBox", `${x} ${y} ${w} ${h}`);
      return { w, h };
    }
    // The opposite failure: a canvas far LARGER than the diagram it holds.
    // Mermaid's dagre-based renderers (flowchart, stateDiagram-v2) size their
    // viewBox from a DOM measurement taken while the svg still sits in
    // mermaid's temporary render container, and the Android WebView answers
    // that with ~2048 instead of the real content size — so a 514x62 flowchart
    // ships a 2088x2043 canvas (134x too big) and gets scaled to ~1/4 of a
    // legible size inside a vast empty box. The geometry itself is correct, and
    // once the svg is in the live document getBBox() reports it truthfully, so
    // re-derive the canvas from the content. Chart families size their canvas
    // deliberately and measure 1.0-1.4x here, well under the threshold; on a
    // desktop browser every family measures ~1.0x, so this never fires there
    // and the two surfaces stay identical by construction.
    if (bb.width > 0 && bb.height > 0 &&
        (vb.width * vb.height) / (bb.width * bb.height) > 2.5) {
      const x = Math.floor(bb.x) - 8, y = Math.floor(bb.y) - 8;
      const w = Math.ceil(bb.width) + 16, h = Math.ceil(bb.height) + 16;
      sv.setAttribute("viewBox", `${x} ${y} ${w} ${h}`);
      return { w, h };
    }
    return null;
  } catch { return null; }
}

// Resolves the mermaid module object (exposing initialize()/render()/run()) that
// the shared initialize block below configures. On the APK origin the jsDelivr
// ESM import is unreachable and the app runs fully offline, so inject the UMD
// build shipped in vendor/ (relative to the game dir) and resolve the
// window.mermaid global it defines; the script's onerror rejects so the caller's
// existing failure/retry path still fires. On the web it is the exact original
// pinned ESM import, resolving its .default. Both paths yield the same-shaped API.
function _loadMermaidModule() {
  if (IS_APK) {
    return new Promise((resolve, reject) => {
      if (window.mermaid) { resolve(window.mermaid); return; }
      const s = document.createElement("script");
      s.src = "vendor/mermaid.min.js";
      s.onload = () => window.mermaid
        ? resolve(window.mermaid)
        : reject(new Error("mermaid UMD loaded but window.mermaid is undefined"));
      s.onerror = () => reject(new Error("mermaid UMD script failed to load"));
      document.head.appendChild(s);
    });
  }
  // [W5] pinned to the exact latest 11.x (11.16.0, via `npm view mermaid version`)
  // instead of the floating mermaid@11 tag, so a CDN-side minor bump can't change
  // rendering under us; bump deliberately when validating a newer release.
  return import("https://cdn.jsdelivr.net/npm/mermaid@11.16.0/dist/mermaid.esm.min.mjs")
    .then(m => m.default);
}

// [SF] Lazily load + initialize the Mermaid engine (CDN ESM on Pages / vendored
// UMD in the APK). Only reached now when a fence has NO pre-rendered asset (added
// or edited before the build ran) — the common path is engine-free. Returns the
// module, or null on failure (shows a source-fallback notice on failNode).
async function ensureMermaid(failNode) {
  try {
    if (!_mermaidReady) {
      _mermaidReady = _loadMermaidModule()
        .then(m => {
          // One stack for BOTH measurement and display. themeVariables.fontFamily
          // only styles the rendered SVG via CSS; the sequence renderer sizes its
          // actor/note/message boxes with its own font settings (Open Sans /
          // Trebuchet defaults — far narrower than monospace), so text drawn in
          // mono spilled outside boxes sized for proportional metrics.
          const mmFont = "ui-monospace, SFMono-Regular, Menlo, monospace";
          m.initialize({
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
              fontFamily:          mmFont,
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
            // htmlLabels:false renders labels as native SVG <text> instead of
            // HTML inside <foreignObject>. Mermaid measures a foreignObject
            // label by laying out its HTML, and the Android WebView answers
            // that measurement with garbage — which wrecks CLUSTER layout in
            // particular: a two-subgraph flowchart came out 16446x16420, so
            // its content was scaled to ~2% and read as an empty box. Native
            // text measures correctly on every engine (same source ->
            // 2062x2036, which mmFixViewBox then trims to the real content).
            // Set for BOTH surfaces, not behind IS_APK: Pages and the APK must
            // render identically, and this is not a fourth APK fork.
            // <br/> still wraps (mermaid splits it into tspans).
            flowchart: { curve: "basis", padding: 20, nodeSpacing: 45, rankSpacing: 55, htmlLabels: false },
            // Sequence text rendered oversized relative to prose and long
            // notes overflowed their boxes; wrap + smaller fonts fix both.
            // The font families MUST match themeVariables.fontFamily — these
            // are what the renderer measures box widths with (defaults are
            // proportional Open Sans/Trebuchet; the SVG displays monospace).
            sequence: {
              wrap: true,
              actorFontSize: 14, messageFontSize: 13, noteFontSize: 13,
              actorFontFamily: mmFont, messageFontFamily: mmFont, noteFontFamily: mmFont,
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
          return m;
        });
    }
    return await _mermaidReady;
  } catch (err) {
    // CDN unavailable or offline — raw source stays visible as text, nothing
    // crashes, and the import is retried on the next page open (a cached
    // rejected promise would otherwise disable diagrams for the whole session).
    _mermaidReady = null;
    console.warn("Mermaid load failed:", err);
    if (failNode && !document.querySelector(".mm-fail")) failNode.insertAdjacentHTML("afterbegin", `<div class="mm-fail">Diagrams need a network connection — showing source.</div>`);
    return null;
  }
}

// [SF] Pre-rendered diagram assets (built by scripts/build_diagrams.mjs into
// game/diagrams/<key>.json, keyed by the SAME cyrb53(source) a fence hashes to).
// Cached per session; a miss (null) is cached too so an un-baked fence isn't
// re-fetched on every scroll. Fetched relative to the game dir -> resolves on
// Pages AND in the offline APK WebView.
const _mmAsset = new WeakMap();                    // node -> asset (for engine-free resize swaps)
const _diagramCache = new Map();                   // key -> asset | null
async function loadDiagramAsset(src) {
  const key = cyrb53(src).toString(36);
  if (_diagramCache.has(key)) return _diagramCache.get(key);
  let asset = null;
  try {
    const r = await fetch(`diagrams/${key}.mmz`, { cache: "force-cache" });   // gzipped precision-reduced asset
    if (r.ok && "DecompressionStream" in window) {
      const ds = new DecompressionStream("gzip");
      const text = await new Response(r.body.pipeThrough(ds)).text();
      asset = JSON.parse(text);
    }
  } catch { /* offline / missing / no DecompressionStream -> live-render fallback */ }
  _diagramCache.set(key, asset);
  return asset;
}

// [SF] Engine-free orientation choice — reproduces mmRenderNode's decision
// (widescreen height test + portrait legibility test) from the BAKED dims d0/d1
// instead of re-rendering. This is what lets phone/tablet/rotation re-orient a
// pre-rendered diagram with no engine, offline.
function mmChooseVariant(asset, avail) {
  const d0 = { w: asset.d0[0], h: asset.d0[1] };
  const dispH = (d) => d.h * Math.min(1, avail / d.w);
  let svg = asset.svg0, d = d0, flipScale = 0, variant = "0";
  if (asset.svg1 && asset.d1) {
    const d1 = { w: asset.d1[0], h: asset.d1[1] };
    const s1 = Math.min(1, Math.max(avail / d1.w, 0.7));
    const visible = Math.min(1, avail / (d1.w * s1));
    if (mmPortrait(avail)) {
      const s0 = Math.min(1, avail / d0.w);
      const s1fit = Math.min(1, avail / d1.w);
      const sane = d1.h * s1fit <= window.innerHeight * 3.5;
      if (s1fit > s0 * 1.25 && sane) { svg = asset.svg1; d = d1; flipScale = s1fit; variant = "1"; }
    } else if (d1.h * s1 < dispH(d0) * 0.7 && visible >= 0.55) {
      svg = asset.svg1; d = d1; flipScale = s1; variant = "1";
    }
  }
  return { svg, d, flipScale, variant };
}

// [SF] Mount a pre-rendered asset into a container: pick the variant for the
// current width, size it, and wire grip/fit/zoom + the resize observer — the same
// tail mmRenderNode runs, but with no engine and no render.
function injectPrerendered(n, asset) {
  _mmAsset.set(n, asset);
  const avail = mmAvailWide(n);
  n.dataset.mmAvail = avail;
  const pick = mmChooseVariant(asset, avail);
  n.dataset.mmVariant = pick.variant;
  n.innerHTML = pick.svg;
  const sv = n.querySelector("svg");
  const d = pick.d;
  if (sv && d) {
    sv.dataset.natw = Math.round(d.w);
    if (n.dataset.mmFit) {
      mmLayout(n, sv, Math.min(d.w, avail));
    } else if (pick.flipScale) {
      sv.dataset.minw = Math.round(d.w * 0.7);
      mmLayout(n, sv, Math.round(d.w * pick.flipScale));
    } else if (asset.ctype === "timeline" && d.w > avail) {
      const w = Math.max(avail, Math.round(d.w * 0.75));
      if (w > avail) sv.dataset.minw = w;
      mmLayout(n, sv, w);
    } else {
      mmLayout(n, sv, Math.min(d.w, avail));
    }
    mmAddGrip(n, sv);
    mmAddFit(n, sv);
  }
  if (!n.dataset.mmWired) {
    n.dataset.mmWired = "1";
    n.tabIndex = 0;
    n.setAttribute("role", "button");
    n.setAttribute("aria-label", "Diagram — open zoom view");
    n.addEventListener("click", () => openMermaidZoom(n));
    n.addEventListener("keydown", (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); openMermaidZoom(n); } });
  }
  mmObserve(n);
}

// Render ONE diagram container: pre-rendered asset first (engine-free), else the
// live engine as a fallback. Shared by the lazy IntersectionObserver and by
// flushPendingDiagrams (TOC jumps need every diagram's height final first).
async function mmRenderOne(n) {
  if (n.querySelector("svg")) { mmObserve(n); return; }   // already rendered this visit
  const src = n.textContent.trim();
  if (!src) return;
  _mmSrc.set(n, src);
  const asset = await loadDiagramAsset(src);       // [SF] pre-rendered SVGs from the build (engine-free, offline)
  if (asset && asset.svg0) { injectPrerendered(n, asset); return; }
  const mermaid = await ensureMermaid(n);          // fallback: un-baked fence -> live render
  if (!mermaid) return;
  await mmRenderNode(n, src);
  mmObserve(n);
}

// Render every still-unrendered diagram in `root` now (not lazily). Called before a
// TOC jump: lazy diagrams that render DURING a smooth scroll shift the target
// heading (measured ~1400px on a diagram-dense page), so the scroll lands on the
// wrong section. Rendering them first makes every heading's position final.
async function flushPendingDiagrams(root) {
  for (const n of [...root.querySelectorAll(".mermaid")]) {
    if (!n.querySelector("svg")) await mmRenderOne(n);
  }
}

async function renderMermaid(root) {
  const nodes = [...root.querySelectorAll(".mermaid")];
  if (!nodes.length) return;                       // no mermaid on this page — skip
  if (_mmRO) _mmRO.disconnect();                   // observe only the live page's diagrams
  if (_mmIO) _mmIO.disconnect();                   // drop the previous page's lazy-render targets (now detached)
  const renderOne = mmRenderOne;
  // [PERF] Lazy render: a diagram is mounted only as it nears the viewport;
  // already-rendered ones just re-attach the resize observer. rootMargin mounts a
  // screen ahead so scrolling never reveals a blank.
  nodes.filter((n) => n.querySelector("svg")).forEach(mmObserve);
  const pending = nodes.filter((n) => !n.querySelector("svg"));
  if (!pending.length) return;
  if (!("IntersectionObserver" in window)) { for (const n of pending) await renderOne(n); return; }
  _mmIO = new IntersectionObserver((entries) => {
    for (const e of entries) if (e.isIntersecting) { _mmIO.unobserve(e.target); renderOne(e.target); }
  }, { rootMargin: "800px 0px" });
  pending.forEach((n) => _mmIO.observe(n));
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
  ctrl.innerHTML = `<button class="mz-out" title="Zoom out (−)" aria-label="Zoom out">−</button><span class="mz-pct">100%</span><button class="mz-in" title="Zoom in (+)" aria-label="Zoom in">+</button><button class="mz-reset" title="Fit (0)" aria-label="Fit to view">↺</button><span class="mz-hint">drag to pan · scroll to zoom · esc closes</span><button class="mz-close" title="Close (Esc)" aria-label="Close diagram viewer">✕</button>`;

  const overlay = document.createElement("div");
  overlay.className = "mermaid-overlay";
  overlay.setAttribute("role", "dialog");
  overlay.setAttribute("aria-modal", "true");
  overlay.setAttribute("aria-label", "Diagram viewer");
  overlay.appendChild(ctrl);
  overlay.appendChild(box);
  document.body.appendChild(overlay);
  const release = trapFocus(overlay, { initial: ".mz-close" });

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

  const close = () => { release(); overlay.remove(); document.removeEventListener("keydown", onKey, true); };
  overlay._close = close;                             // let a screen change (e.g. closing the reader) tear this down cleanly
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
      }).catch(() => { btn.textContent = "blocked"; setTimeout(() => { btn.textContent = "copy"; }, 1400); });
    });
    pre.appendChild(btn);
    if (code.classList.contains("diagram")) {
      pre.title = "Click to zoom";
      pre.tabIndex = 0;
      pre.setAttribute("role", "button");
      pre.setAttribute("aria-label", "ASCII diagram — open zoom view");
      const openAscii = () => { const p = document.createElement("pre"); p.innerHTML = code.innerHTML; openDiagramZoom(p); };
      pre.addEventListener("click", (e) => {
        if (e.target.closest(".codecopy")) return;
        openAscii();
      });
      pre.addEventListener("keydown", (e) => {
        if (e.target.closest(".codecopy")) return;
        if (e.key === "Enter" || e.key === " ") { e.preventDefault(); openAscii(); }
      });
    }
  });
}

// Wrap each rendered table in a horizontal-scroll container so wide tables
// scroll inside their own box instead of forcing the whole reader body sideways.
function wireTables(main) {
  main.querySelectorAll("table").forEach((t) => {
    if (t.parentElement && t.parentElement.classList.contains("md-tablewrap")) return;   // already wrapped
    const wrap = document.createElement("div");
    wrap.className = "md-tablewrap";
    t.parentNode.insertBefore(wrap, t);
    wrap.appendChild(t);
  });
}

// Callout (admonition) blocks: a blockquote is promoted to a coloured "dark
// island" when its first line is a GFM alert (`> [!NOTE]`) or a known bold label
// (`> **Warning:** ...`). Anything else stays a plain <blockquote> — arbitrary
// quotes are never restyled. Colour lives only inside the bordered island; prose
// stays pure-white (reader invariant), same as code/diagram islands.
const CALLOUT_TYPES = {
  "note":        { type: "note",     label: "Note",        icon: "ℹ" },
  "info":        { type: "note",     label: "Note",        icon: "ℹ" },
  "important":   { type: "important", label: "Important",  icon: "★" },
  "tip":         { type: "tip",      label: "Tip",         icon: "✓" },
  "warning":     { type: "warning",  label: "Warning",     icon: "▲" },
  "caution":     { type: "warning",  label: "Caution",     icon: "▲" },
  "gotcha":      { type: "warning",  label: "Gotcha",      icon: "▲" },
  "insight":     { type: "insight",  label: "Insight",     icon: "◆" },
  "key insight": { type: "insight",  label: "Key Insight", icon: "◆" },
  "war story":   { type: "warstory", label: "War Story",   icon: "⚑" },
  "example":     { type: "example",  label: "Example",     icon: "▸" },
};
function calloutOf(bodyLines) {
  if (!bodyLines.length) return null;
  const first = bodyLines[0].trim();
  const finish = (spec, rest, tail) => ({ ...spec, text: (rest ? [rest] : []).concat(tail).join(" ").trim() });
  let m = first.match(/^\[!(\w+)\]\s*(.*)$/);                 // GFM alert: [!NOTE] ...
  if (m) { const s = CALLOUT_TYPES[m[1].toLowerCase()]; return s ? finish(s, m[2], bodyLines.slice(1)) : null; }
  m = first.match(/^\*\*([^*]+?):\*\*\s*(.*)$/);             // bold label: **Warning:** ...
  if (m) { const s = CALLOUT_TYPES[m[1].trim().toLowerCase()]; if (s) return finish(s, m[2], bodyLines.slice(1)); }
  return null;
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
    const fence = line.match(/^(\s*)```(.*)$/);
    if (fence) {                                   // fenced code / ASCII diagram
      const indent = fence[1].length;              // fences indented under a list marker (§10/§14 BROKEN→FIX)
      const lang = fence[2].trim();
      // Close only on a fence indented no more than the opener. For column-0 openers
      // this is identical to the old `^```` rule, so an ASCII diagram that *shows*
      // indented ```lang fences as literal content is never split early.
      const isClose = (l) => { const m = l.match(/^(\s*)```/); return !!m && m[1].length <= indent; };
      const body = [];
      i++;
      while (i < lines.length && !isClose(lines[i])) { body.push(lines[i]); i++; }
      i++;                                         // skip closing fence
      const strip = new RegExp(`^ {0,${indent}}`); // drop the opener's indent, keep relative indentation
      const raw = body.map((l) => l.replace(strip, "")).join("\n");
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
      const co = calloutOf(body);
      if (co) {
        out.push(`<div class="callout callout-${co.type}"><p class="callout-label"><span class="callout-icon">${co.icon}</span>${esc(co.label)}</p><div class="callout-body">${mdInline(co.text)}</div></div>`);
      } else {
        out.push(`<blockquote>${mdInline(body.join(" "))}</blockquote>`);
      }
      qaPending = false; continue;
    }
    const listOpen = line.match(/^\s*(\d+)\.\s+/) || line.match(/^\s*([-*+])\s+/);
    if (listOpen) {
      const ordered = /\d/.test(listOpen[1]), tag = ordered ? "ol" : "ul";
      const startNum = ordered ? parseInt(listOpen[1], 10) : 1;  // resume numbering after an interrupting code block
      let items = "";
      while (i < lines.length && /^\s*([-*+]|\d+\.)\s+/.test(lines[i])) {
        const parts = [lines[i].replace(/^\s*([-*+]|\d+\.)\s+/, "")]; i++;
        // absorb wrapped continuation lines (indented, non-blank, not a new marker/fence)
        while (i < lines.length && /^\s+\S/.test(lines[i]) &&
               !/^\s*([-*+]|\d+\.)\s+/.test(lines[i]) && !/^\s*```/.test(lines[i])) {
          parts.push(lines[i].trim()); i++;
        }
        items += `<li>${mdInline(parts.join(" "))}</li>`;
      }
      const startAttr = (ordered && startNum !== 1) ? ` start="${startNum}"` : "";
      out.push(`<${tag}${startAttr}>${items}</${tag}>`);
      qaPending = false; continue;
    }
    if (/^\s*$/.test(line)) { i++; continue; }
    const para = [];
    while (i < lines.length && !/^\s*$/.test(lines[i]) && !/^(#{1,6})\s/.test(lines[i]) &&
           !/^\s*```/.test(lines[i]) && !/^>\s?/.test(lines[i]) &&
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
const READER_CACHE_CAP = 30;                       // [PERF] bound retained markdown strings (bounded at ~700 paths otherwise)
const reader = { path: null, titleText: "", back: [], nav: null, full: false, toc: false, modules: false };
const readerExpanded = new Set();   // module keys expanded in the left sidebar (session-persistent)
const readerBooksOpen = new Set();  // book slugs expanded in the sidebar's per-book groups (session-persistent)
let _readerInvoker = null;          // element focused when the reader was opened; focus returns here on close

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

// Human title from a content path: ".../module/README.md" -> "Module";
// ".../module/sub_file.md" -> "Sub File".
function titleFromPath(path) {
  const parts = path.split("/");
  let name = parts.pop();
  if (/^readme\.md$/i.test(name)) name = parts.pop() || name;
  return titleize(name.replace(/\.md$/i, "").replace(/-+/g, " "));
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
    () => { const v = getComputedStyle(document.documentElement).getPropertyValue("--reader-w").trim(); if (v.endsWith("px")) safeSet("sd_reader_w", v); }
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
    () => { const v = getComputedStyle(document.documentElement).getPropertyValue("--modules-w").trim(); if (v.endsWith("px")) safeSet("sd_modules_w", v); }
  );
  // Right TOC sidebar grip
  attachGrip(el("#tocGrip"),
    ev => {
      const right = el("#reader").getBoundingClientRect().right;
      css.setProperty("--toc-w", Math.round(Math.min(360, Math.max(120, right - ev.clientX))) + "px");
    },
    () => { const v = getComputedStyle(document.documentElement).getPropertyValue("--toc-w").trim(); if (v.endsWith("px")) safeSet("sd_toc_w", v); }
  );
}

function restoreReaderWidth() {
  const css = document.documentElement.style;
  const rw = localStorage.getItem("sd_reader_w");   if (rw) css.setProperty("--reader-w", rw);
  const mw = localStorage.getItem("sd_modules_w");  if (mw) css.setProperty("--modules-w", mw);
  const tw = localStorage.getItem("sd_toc_w");      if (tw) css.setProperty("--toc-w", tw);
  reader.full    = localStorage.getItem("sd_reader_full")    === "1";
  // First visit on this origin (no saved prefs): default the Contents index on
  // for wide viewports and the module tree on for very wide ones — a blank
  // gutter reads as broken. A saved "0"/"1" pref always wins.
  const tocPref = localStorage.getItem("sd_reader_toc");
  reader.toc     = tocPref == null ? window.innerWidth >= 1100 : tocPref === "1";
  const modPref = localStorage.getItem("sd_reader_modules");
  reader.modules = modPref == null ? window.innerWidth >= 1400 : modPref === "1";
  applyReaderFont();
  applyReaderTypography();
}

// Reader font size: A− / A+ in the reader head, persisted, clamped 12–19px.
function applyReaderFont(delta = 0) {
  let fs = +(localStorage.getItem("sd_reader_fs") || 14.5) + delta;
  fs = Math.min(19, Math.max(12, fs));
  safeSet("sd_reader_fs", fs);
  document.documentElement.style.setProperty("--rd-fs", fs + "px");
}

// Reader typography prefs (serif / measure / drop-cap). Applied to <html> — which
// survives reader navigations — so they persist without re-applying per page.
// Colour is never touched (prose stays pure-white, hard invariant); only the
// typeface, line measure, and a drop-cap on the lead paragraph change.
const READER_MEASURE = { narrow: "620px", cozy: "760px", wide: "960px" };
function applyReaderTypography() {
  const de = document.documentElement;
  de.classList.toggle("rd-serif", localStorage.getItem("sd_reader_font") === "serif");
  de.classList.toggle("rd-dropcap", localStorage.getItem("sd_reader_dropcap") !== "0");  // default on
  const mw = READER_MEASURE[localStorage.getItem("sd_reader_measure")];
  if (mw) de.style.setProperty("--reader-measure", mw);
  else de.style.removeProperty("--reader-measure");
}
// The "Aa" popover: three segmented controls. Toggling this button again (or an
// outside click / Esc) closes it. Choices persist and apply live.
function openReaderTypeMenu(anchorBtn) {
  const panel = el("#reader"); if (!panel) return;
  const existing = el("#readerTypePop");
  if (existing) { existing._release?.(); existing.remove(); return; }
  const font = localStorage.getItem("sd_reader_font") || "sans";
  const measure = localStorage.getItem("sd_reader_measure") || "default";
  const dropcap = localStorage.getItem("sd_reader_dropcap") !== "0";
  const recall = localStorage.getItem("sd_reader_recall") !== "0";
  const seg = (k, opts, cur) => `<div class="rtp-seg" data-k="${k}">` +
    opts.map(([v, lbl]) => `<button data-v="${v}" class="${v === cur ? "on" : ""}">${lbl}</button>`).join("") + `</div>`;
  const pop = document.createElement("div");
  pop.id = "readerTypePop"; pop.className = "reader-typepop"; pop.setAttribute("role", "menu");
  pop.innerHTML =
    `<div class="rtp-row"><span class="rtp-lbl">Font</span>${seg("sd_reader_font", [["sans", "Sans"], ["serif", "Serif"]], font)}</div>` +
    `<div class="rtp-row"><span class="rtp-lbl">Width</span>${seg("sd_reader_measure", [["narrow", "Narrow"], ["cozy", "Cozy"], ["wide", "Wide"], ["default", "Auto"]], measure)}</div>` +
    `<div class="rtp-row"><span class="rtp-lbl">Drop-cap</span>${seg("sd_reader_dropcap", [["1", "On"], ["0", "Off"]], dropcap ? "1" : "0")}</div>` +
    `<div class="rtp-row"><span class="rtp-lbl">Answers</span>${seg("sd_reader_recall", [["1", "Hidden"], ["0", "Shown"]], recall ? "1" : "0")}</div>`;
  panel.appendChild(pop);
  pop.querySelectorAll(".rtp-seg").forEach((s) => s.querySelectorAll("button").forEach((b) => b.addEventListener("click", () => {
    safeSet(s.dataset.k, b.dataset.v);
    s.querySelectorAll("button").forEach((x) => x.classList.toggle("on", x === b));
    if (s.dataset.k === "sd_reader_recall") applyRecallPref(); else applyReaderTypography();
  })));
  const release = trapFocus(pop, { restoreTo: anchorBtn });
  pop._release = release;
  setTimeout(() => {
    const done = () => { release(); pop.remove(); document.removeEventListener("mousedown", off); document.removeEventListener("keydown", esc, true); };
    const off = (ev) => { if (!pop.contains(ev.target) && ev.target !== anchorBtn) done(); };
    // capture + stopPropagation so Esc closes only the menu, not the reader behind it
    const esc = (ev) => { if (ev.key === "Escape") { ev.preventDefault(); ev.stopPropagation(); done(); } };
    document.addEventListener("mousedown", off); document.addEventListener("keydown", esc, true);
  }, 0);
}

// Populate the always-accessible sidebar index from the rendered headings (ids
// assigned by mdRender, so anchors always match). Returns the heading count so the
// caller can hide the Index toggle when there's nothing to index.
// Reliably scroll the reader to a heading — always lands ON the clicked heading,
// never the next. The trap: lazy diagrams between here and the target render (and
// then ResizeObserver-reclamp ~250ms later), shifting the target by ~1400px. A
// SMOOTH scroll animates toward a position that then moves, overshooting to the
// next heading — hence the "index is a next pointer" bug. Fix: (1) flush pending
// diagrams so heading positions are final, (2) scroll INSTANTLY (no animation
// window for a late reflow to corrupt), (3) one instant re-snap past the reclamp
// debounce in case a diagram still nudged heights.
async function scrollToHeading(root, id) {
  if (!id) return;
  await flushPendingDiagrams(root);
  const find = () => root.querySelector("#" + CSS.escape(id));
  const scroller = root.closest(".reader-body") || root.parentElement || root;
  const offsetOf = (el) => el.getBoundingClientRect().top - scroller.getBoundingClientRect().top;
  const t = find();
  if (!t) return;
  t.scrollIntoView({ block: "start" });              // instant — no overshoot animation
  clearTimeout(reader._tocSnap);
  reader._tocSnap = setTimeout(() => {
    const t2 = find();
    if (t2 && Math.abs(offsetOf(t2)) > 4) t2.scrollIntoView({ block: "start" });   // re-clamp nudged it -> re-align
  }, 320);
}

function buildToc(tocEl, main) {
  const heads = [...main.querySelectorAll("h2[id], h3[id]")];
  if (!heads.length) { tocEl.innerHTML = ""; return 0; }
  const items = heads.map((h) =>
    `<li class="${h.tagName === "H3" ? "lvl3" : ""}"><a href="#" data-tid="${esc(h.id)}" title="${esc(h.textContent)}">${esc(h.textContent)}</a></li>`).join("");
  tocEl.innerHTML = `<div class="toc-h">Contents<span class="toc-pos"></span></div><ul>${items}</ul>`;
  tocEl.querySelectorAll("a[data-tid]").forEach((a) =>
    a.addEventListener("click", (e) => { e.preventDefault(); scrollToHeading(main, a.dataset.tid); }));
  return heads.length;
}

// Masthead: the designed opening of every page — section badge (glyph + label in
// the section hue), the lead h1 (moved in, not cloned, so find/TOC keep the live
// node), an ornament rule, and the "~N min read · M sections" meta (rendered text
// at ~200wpm so code/diagram noise is included at roughly the right weight).
function buildMasthead(main, path) {
  const words = (main.textContent.trim().match(/\S+/g) || []).length;
  const mins = Math.max(1, Math.round(words / 200));
  const secs = main.querySelectorAll("h2[id]").length;
  const ident = sectionIdentity(path);
  const label = SECTION_LABELS[(path || "").split("/")[0]];
  let h1 = null;
  for (const ch of main.children) {
    if (ch.tagName === "H1") { h1 = ch; break; }
    if (/^H[2-6]$/.test(ch.tagName)) break;           // body starts at a subheading — no lead h1
  }
  const head = document.createElement("header");
  head.className = "rd-masthead";
  head.innerHTML =
    (ident && label ? `<div class="rd-mast-badge"><span class="rd-mast-glyph" aria-hidden="true">${esc(ident.glyph)}</span><span>${esc(label)}</span></div>` : "") +
    (h1 ? "" : `<h1>${esc(reader.titleText)}</h1>`) +
    `<div class="rd-mast-rule" aria-hidden="true"></div>
    <div class="rd-mast-meta"><span>~${mins} min read</span>${secs ? `<span class="rm-dot">·</span><span>${secs} section${secs > 1 ? "s" : ""}</span>` : ""}</div>`;
  if (h1) head.insertBefore(h1, head.querySelector(".rd-mast-rule"));
  main.insertBefore(head, main.firstChild);
}

// Scroll-spy: highlight the section currently at the top of the viewport in the
// TOC, and update the "§X / M" counter in the TOC header. Stored on `reader._spy`
// and driven by the reader-body scroll handler (which already runs on scroll), so
// there is no second scroll listener to leak across navigations.
function wireScrollSpy(main, scrollBox) {
  const heads = [...main.querySelectorAll("h2[id], h3[id]")];
  const toc = el("#readerToc");
  const links = toc ? new Map([...toc.querySelectorAll("a[data-tid]")].map((a) => [a.dataset.tid, a])) : new Map();
  const h2count = main.querySelectorAll("h2[id]").length;
  const posEl = toc ? toc.querySelector(".toc-pos") : null;
  if (!heads.length) { reader._spy = null; return; }
  let activeId = null, lastPos = "";
  // Heading offsets are cached and refreshed only when the document height
  // changes (Mermaid/images finishing layout, font-size change): reading
  // offsetTop for every heading on every scroll frame forces a full reflow
  // per frame on long docs — the reader's biggest scroll-jank source.
  let offs = null, cachedSH = -1;
  reader._spy = () => {
    const sh = scrollBox.scrollHeight;
    if (sh !== cachedSH) { cachedSH = sh; offs = heads.map((h) => h.offsetTop); }
    const top = scrollBox.scrollTop + 90;               // "reading line" ~90px below the fold top
    let cur = heads[0], h2seen = 0, h2idx = 0;
    for (let i = 0; i < heads.length; i++) {
      const h = heads[i];
      if (h.tagName === "H2") h2seen++;
      if (offs[i] <= top) { cur = h; if (h.tagName === "H2") h2idx = h2seen; }
      else break;
    }
    if (cur.id !== activeId) {
      if (activeId && links.get(activeId)) links.get(activeId).classList.remove("toc-active");
      const a = links.get(cur.id);
      if (a) { a.classList.add("toc-active"); a.scrollIntoView({ block: "nearest" }); }
      activeId = cur.id;
    }
    if (posEl && h2count) {
      const pos = `§ ${Math.max(1, h2idx)} / ${h2count}`;
      // Write only on change — an unconditional textContent write dirties
      // layout every frame and turns the next offset read into a reflow.
      if (pos !== lastPos) { lastPos = pos; posEl.textContent = pos; }
    }
  };
  reader._spy();
}

// Reading-position memory: remember the scroll offset per path so revisiting a
// long module resumes where you left off (first-ever open still starts at top).
// Debounced to avoid a localStorage write on every scroll tick.
let _scrollSaveT = null;
function scheduleScrollSave(path, offset) {
  if (!path) return;
  clearTimeout(_scrollSaveT);
  _scrollSaveT = setTimeout(() => {
    try {
      const m = JSON.parse(localStorage.getItem("sd_reader_scroll") || "{}");
      if (offset > 40) m[path] = Math.round(offset); else delete m[path];
      const keys = Object.keys(m);
      if (keys.length > 60) delete m[keys[0]];          // cap the map
      safeSet("sd_reader_scroll", JSON.stringify(m));
    } catch { /* quota / parse — non-critical */ }
  }, 400);
}
function savedScrollFor(path) {
  try { return (JSON.parse(localStorage.getItem("sd_reader_scroll") || "{}"))[path] || 0; }
  catch { return 0; }
}

// ---------- reader in-page find ----------
// Reader-scoped find (Ctrl/Cmd+F while the reader is open, or the head button).
// Wraps matches in <mark> by walking TEXT NODES only — never innerHTML — so code
// and ASCII-diagram DOM survive intact; clearing unwraps + normalize()s back to
// the original tree. Mermaid SVG subtrees are skipped (can't wrap text there).
const _find = { open: false, matches: [], idx: -1 };
function readerFindClear(main) {
  main.querySelectorAll("mark.rd-find").forEach((m) => m.replaceWith(document.createTextNode(m.textContent)));
  main.normalize();
  _find.matches = []; _find.idx = -1;
}
function readerFindRun(main, q) {
  readerFindClear(main);
  const needle = (q || "").toLowerCase();
  if (needle) {
    const walker = document.createTreeWalker(main, NodeFilter.SHOW_TEXT, {
      acceptNode(node) {
        if (!node.nodeValue) return NodeFilter.FILTER_REJECT;
        for (let p = node.parentElement; p && p !== main; p = p.parentElement) {
          const tag = (p.tagName || "").toLowerCase();
          if (tag === "script" || tag === "style" || tag === "svg") return NodeFilter.FILTER_REJECT;
          // skip injected chrome: mermaid SVG, recall/reveal buttons, heading anchors
          if (p.classList && (p.classList.contains("mermaid") || p.classList.contains("recall-btn") ||
              p.classList.contains("recall-all") || p.classList.contains("h-anchor"))) return NodeFilter.FILTER_REJECT;
        }
        return node.nodeValue.toLowerCase().includes(needle) ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT;
      },
    });
    const targets = []; let n; while ((n = walker.nextNode())) targets.push(n);
    for (const node of targets) {
      const text = node.nodeValue, low = text.toLowerCase();
      const frag = document.createDocumentFragment();
      let last = 0, hit;
      while ((hit = low.indexOf(needle, last)) !== -1) {
        if (hit > last) frag.appendChild(document.createTextNode(text.slice(last, hit)));
        const mk = document.createElement("mark"); mk.className = "rd-find";
        mk.textContent = text.slice(hit, hit + needle.length);
        frag.appendChild(mk);
        last = hit + needle.length;
      }
      if (last < text.length) frag.appendChild(document.createTextNode(text.slice(last)));
      node.parentNode.replaceChild(frag, node);
    }
    _find.matches = [...main.querySelectorAll("mark.rd-find")];
    _find.idx = _find.matches.length ? 0 : -1;
    // Think-first answers: reveal any collapsed answer that contains a match, so
    // the "k / N" counter stays honest and scrollIntoView can reach every match
    // (mirrors the browser's hidden=until-found behavior). Stays revealed on clear.
    main.querySelectorAll(".recall-body mark.rd-find").forEach((mk) => {
      const r = mk.closest(".recall");
      if (r && !r.classList.contains("open")) openRecall(r);
    });
  }
  readerFindFocus();
}
function readerFindFocus() {
  _find.matches.forEach((m, k) => m.classList.toggle("rd-find-cur", k === _find.idx));
  const cur = _find.matches[_find.idx];
  if (cur) cur.scrollIntoView({ block: "center", behavior: REDUCED() ? "auto" : "smooth" });
  const c = el(".rd-find-count");
  if (c) c.textContent = _find.matches.length ? `${_find.idx + 1} / ${_find.matches.length}` : "0 / 0";
}
function readerFindStep(d) {
  if (!_find.matches.length) return;
  _find.idx = (_find.idx + d + _find.matches.length) % _find.matches.length;
  readerFindFocus();
}
function openReaderFind() {
  const panel = el("#reader"); if (!panel) return;
  let bar = el("#readerFind");
  if (!bar) {
    bar = document.createElement("div");
    bar.id = "readerFind"; bar.className = "reader-find";
    bar.innerHTML = `<span class="rd-find-ic" aria-hidden="true">&#8981;</span>
      <input type="text" class="rd-find-input" placeholder="Find in page" aria-label="Find in page">
      <span class="rd-find-count">0 / 0</span>
      <button class="rd-find-btn" data-d="-1" title="Previous (Shift+Enter)" aria-label="Previous match">&lsaquo;</button>
      <button class="rd-find-btn" data-d="1" title="Next (Enter)" aria-label="Next match">&rsaquo;</button>
      <button class="rd-find-btn rd-find-x" title="Close (Esc)" aria-label="Close find">&times;</button>`;
    panel.appendChild(bar);
    const input = bar.querySelector(".rd-find-input");
    input.addEventListener("input", () => { const m = el("#readerMain"); if (m) readerFindRun(m, input.value); });
    input.addEventListener("keydown", (e) => {
      if (e.key === "Enter") { e.preventDefault(); readerFindStep(e.shiftKey ? -1 : 1); }
      else if (e.key === "Escape") { e.preventDefault(); e.stopPropagation(); closeReaderFind(); }
    });
    bar.querySelectorAll(".rd-find-btn[data-d]").forEach((b) => b.addEventListener("click", () => readerFindStep(+b.dataset.d)));
    bar.querySelector(".rd-find-x").addEventListener("click", closeReaderFind);
  }
  _find.open = true; bar.classList.add("show");
  const input = bar.querySelector(".rd-find-input");
  input.focus(); input.select();
  const m = el("#readerMain");
  if (m && input.value) readerFindRun(m, input.value);
}
function closeReaderFind() {
  const bar = el("#readerFind"); if (bar) bar.classList.remove("show");
  _find.open = false;
  const m = el("#readerMain"); if (m) readerFindClear(m);
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

  const itemHtml = navCtx.list.map((m, i) => {
    const mKey = m.path.replace("/README.md", "");
    const mFiles = files[mKey] || ["README.md"];

    if (mFiles.length <= 1) {
      const isActive = m.path === currentPath;
      return `<li><a href="#" class="mod-item${isActive ? " active" : ""}${isModuleRead(m.path) ? " read" : ""}" data-midx="${i}" title="${esc(m.title)}">${esc(m.title)}</a></li>`;
    }

    // Multi-file module: collapsible folder
    const isOpen = readerExpanded.has(mKey);
    const subItems = mFiles.map((fn) => {
      const filePath = `${mKey}/${fn}`;
      const isFileCurrent = filePath === currentPath;
      const label = fn === "README.md" ? "Readme" : titleize(fn.replace(".md", ""));
      return `<li><a href="#" class="mod-file${isFileCurrent ? " active" : ""}${isModuleRead(filePath) ? " read" : ""}" data-path="${esc(filePath)}" title="${esc(label)}">${esc(label)}</a></li>`;
    }).join("");

    return `<li class="mod-group${isOpen ? " open" : ""}">
      <button class="mod-folder" data-midx="${i}" title="${esc(m.title)}" aria-expanded="${isOpen}" aria-controls="modsub-${i}"><span class="mod-arrow">&#9654;</span><span class="mod-fname">${esc(m.title)}</span></button>
      <ul class="mod-subfiles" id="modsub-${i}">${subItems}</ul>
    </li>`;
  });

  // Book grouping: when the list spans nested book ids (book/<slug>/<chapter>),
  // wrap each book's run of chapters in a collapsible group headed by the book's
  // name — a flat 60-chapter list spanning five books is unreadable. Other
  // sections (2-segment module ids) render exactly as before.
  let items;
  const curBook = bookOf(currentPath);
  if (navCtx.list.some((m) => bookOf(m.path))) {
    if (curBook) readerBooksOpen.add(curBook);
    const runs = [];
    navCtx.list.forEach((m, i) => {
      const b = bookOf(m.path) || "";
      const last = runs[runs.length - 1];
      if (last && last.b === b) last.idx.push(i); else runs.push({ b, idx: [i] });
    });
    if (!curBook && !readerBooksOpen.size && runs[0] && runs[0].b) readerBooksOpen.add(runs[0].b);
    items = runs.map((r, k) => {
      if (!r.b) return r.idx.map((i) => itemHtml[i]).join("");
      const open = readerBooksOpen.has(r.b);
      const meta = BOOK_LABELS[r.b] || {};
      return `<li class="mod-book${open ? " open" : ""}">
        <button class="mod-bookh" data-book="${esc(r.b)}" aria-expanded="${open}" aria-controls="modbook-${k}" title="${esc(bookLabel(r.b))}${meta.author ? " — " + esc(meta.author) : ""}"><span class="mod-arrow">&#9654;</span><span class="mod-bkname">${esc(meta.short || bookLabel(r.b))}</span><span class="mod-bkn">${r.idx.length}</span></button>
        <ul class="mod-bookul" id="modbook-${k}">${r.idx.map((i) => itemHtml[i]).join("")}</ul>
      </li>`;
    }).join("");
  } else items = itemHtml.join("");

  modEl.innerHTML = `<div class="mod-h">Modules</div><ul>${items}</ul>`;

  modEl.querySelectorAll(".mod-bookh").forEach((h) => h.addEventListener("click", () => {
    const li = h.closest(".mod-book");
    const willOpen = !li.classList.contains("open");
    li.classList.toggle("open", willOpen);
    h.setAttribute("aria-expanded", String(willOpen));
    if (willOpen) readerBooksOpen.add(h.dataset.book); else readerBooksOpen.delete(h.dataset.book);
  }));

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
      folder.setAttribute("aria-expanded", String(willOpen));
      if (willOpen) readerExpanded.add(mKey); else readerExpanded.delete(mKey);
    });
  });

  modEl.querySelectorAll("a.mod-file").forEach((a) => a.addEventListener("click", (e) => {
    e.preventDefault();
    // Match by module-prefix against the real list — book modules are three
    // segments deep, so a fixed two-segment key would never find them.
    const midx = navCtx.list.findIndex((m) => a.dataset.path.startsWith(m.path.replace("/README.md", "") + "/"));
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

// Native Fullscreen API — best-effort, with the old webkit spelling for Safari.
// Requested on #reader (already position:fixed full-viewport) so the FS layer is
// exactly the reading surface. All calls are guarded/try-catch: if the browser
// blocks fullscreen (no user gesture, iframe policy, unsupported) the in-app
// immersive layer still applies, so the toggle always does something.
function enterNativeFS() {
  const t = el("#reader"); if (!t) return;
  const req = t.requestFullscreen || t.webkitRequestFullscreen;
  if (!req) return;
  try { const r = req.call(t); if (r && r.catch) r.catch(() => {}); } catch { /* blocked — in-app immersive still applied */ }
}
function exitNativeFS() {
  const fsEl = document.fullscreenElement || document.webkitFullscreenElement;
  if (!fsEl) return;
  const ex = document.exitFullscreen || document.webkitExitFullscreen;
  if (!ex) return;
  try { const r = ex.call(document); if (r && r.catch) r.catch(() => {}); } catch { /* ignore */ }
}
// Single entry point for immersive on/off: persist, reflect to DOM, and layer
// native fullscreen on top. `reader.full` is the in-app immersive pref (restored
// at boot); native FS is ephemeral (browsers require a fresh gesture to re-enter).
function setReaderFull(on) {
  reader.full = on;
  safeSet("sd_reader_full", on ? "1" : "0");
  applyReaderModes();
  if (on) enterNativeFS(); else exitNativeFS();
}
// Keep a body class in sync with native FS so CSS can react (e.g. drop the pane
// shadow at the screen edge). Fires on OS/Esc-driven exit too, not just our calls.
["fullscreenchange", "webkitfullscreenchange"].forEach((ev) =>
  document.addEventListener(ev, () => {
    const fsEl = document.fullscreenElement || document.webkitFullscreenElement;
    document.body.classList.toggle("native-fs", !!fsEl);
  }));

// Wire in-body links: relative repo links open in the reader (with back-stack);
// in-page anchors scroll within the pane.
function wireReaderBody(body) {
  body.querySelectorAll("a.md-link").forEach((a) => a.addEventListener("click", (e) => {
    e.preventDefault();
    const target = resolvePath(reader.path, a.dataset.rel || "");
    reader.back.push({ path: reader.path, title: reader.titleText, nav: reader.nav });
    openReaderPath(target, null, null, a.dataset.frag);
  }));
  body.querySelectorAll("a.md-anchor").forEach((a) =>
    a.addEventListener("click", (e) => { e.preventDefault(); scrollToHeading(body, a.dataset.frag || ""); }));
}

// ---------- think-first recall (interview Q&As) ----------
// Collapse each interview answer behind a "Show answer" button so §12 becomes
// active recall instead of passive scanning. Scoped strictly to the interview-
// questions h2 region (mirrors extract.py's heading regex) — bold paragraphs
// elsewhere (§10 gotcha labels etc.) stay untouched. An answer is ALL siblings
// between one .md-q and the next question/heading (.md-a only tags the first
// paragraph), so multi-paragraph answers, lists, and code fences collapse whole.
function openRecall(r, open = true) {
  r.classList.toggle("open", open);
  const b = r.querySelector(".recall-btn");
  if (!b) return;
  b.setAttribute("aria-expanded", open ? "true" : "false");
  b.textContent = open ? "Hide answer" : "Show answer";
}
function wireRecallPrompts(main) {
  const off = localStorage.getItem("sd_reader_recall") === "0";
  const panel = el("#reader");
  if (panel) panel.classList.toggle("recall-off", off);
  const h2 = [...main.querySelectorAll("h2[id]")].find((h) => /interview\s+q/i.test(h.textContent));
  if (!h2) return;
  let n = 0;
  for (let node = h2.nextElementSibling; node && node.tagName !== "H2";) {
    if (!node.matches("p.md-q, p.md-qa")) { node = node.nextElementSibling; continue; }
    const q = node, group = [];
    // Inline form (.md-qa): question and answer share one paragraph — split the
    // remainder after the bold question into its own answer paragraph.
    if (q.matches("p.md-qa")) {
      const strong = q.querySelector(":scope > strong:first-child");
      if (strong) {
        const rest = document.createElement("p");
        rest.className = "md-a";
        while (strong.nextSibling) rest.appendChild(strong.nextSibling);
        if (rest.textContent.trim()) group.push(rest);
      }
    }
    let cur = q.nextElementSibling;
    while (cur && cur.tagName !== "H2" && cur.tagName !== "H3" && !cur.matches("p.md-q, p.md-qa")) {
      group.push(cur); cur = cur.nextElementSibling;
    }
    node = cur;
    if (!group.length) continue;
    const wrap = document.createElement("div");
    wrap.className = "recall";
    const id = `recall-a-${++n}`;                    // page-local; regenerated per render
    const btn = document.createElement("button");
    btn.type = "button"; btn.className = "recall-btn";
    btn.setAttribute("aria-expanded", "false"); btn.setAttribute("aria-controls", id);
    btn.textContent = "Show answer";
    const bodyEl = document.createElement("div");
    bodyEl.className = "recall-body"; bodyEl.id = id;
    group.forEach((g) => bodyEl.appendChild(g));
    wrap.appendChild(btn); wrap.appendChild(bodyEl);
    q.after(wrap);
    if (off) openRecall(wrap, true);
    btn.addEventListener("click", () => openRecall(wrap, !wrap.classList.contains("open")));
  }
  if (!n) return;
  // "Reveal all" pill on the §12 heading. Safe to append here: buildToc has
  // already read the heading labels, and find rejects .recall-all text.
  const all = document.createElement("button");
  all.type = "button"; all.className = "recall-all";
  const sync = () => {
    const rs = [...main.querySelectorAll(".recall")];
    all.textContent = rs.every((r) => r.classList.contains("open")) ? "Hide all" : "Reveal all";
  };
  sync();
  all.addEventListener("click", () => {
    const rs = [...main.querySelectorAll(".recall")];
    const open = !rs.every((r) => r.classList.contains("open"));
    rs.forEach((r) => openRecall(r, open)); sync();
  });
  main.addEventListener("click", (e) => { if (e.target.closest(".recall-btn")) sync(); });
  h2.appendChild(all);
}
// Applies the Aa-popover "Answers" pref live: Shown opens every answer and hides
// the buttons via #reader.recall-off (no unwrap needed); Hidden re-collapses.
function applyRecallPref() {
  const off = localStorage.getItem("sd_reader_recall") === "0";
  const panel = el("#reader"), main = el("#readerMain");
  if (panel) panel.classList.toggle("recall-off", off);
  if (main) main.querySelectorAll(".recall").forEach((r) => openRecall(r, off));
}

// "Evaluate me": every topic page ends with a one-click quiz scoped to its
// module. Hidden mid-quiz (starting one would destroy the live deck) and on
// pages that aren't a bank module (section roots, case studies).
function appendEvalBlock(main, path) {
  if (state.inQuiz) return;
  const dir = path.replace(/\/[^/]+$/, "");
  const base = path.slice(dir.length + 1);         // this page's file basename (e.g. instruction_tuning.md)
  const files = (state.index && state.index.files) || {};
  // [CS] Case-study pages are quizzable too, but from the SEPARATE case pool
  // (loadCaseBank) — never the main bank. Everything else below is shared.
  const isCase = /\/case_studies\//.test(path);
  if (!isCase && !files[dir]) return;              // module page OR case-study page only
  const section = dir.split("/")[0];
  const loadPool = isCase ? loadCaseBank : loadBank;
  // [SF] On a deep-dive sub-file, scope the quiz to THAT file only (its own
  // sourceFile); on the module README, keep the whole-module quiz. Every question
  // carries sourceFile, so this is a pure filter — module ids/SR state untouched.
  const isReadme = /^readme\.md$/i.test(base);
  const csName = isCase ? (caseStudiesFromIndex(section).find((c) => c.file === path) || {}).name : null;
  const name = csName || (isReadme ? titleize(dir.split("/").pop()) : titleize(base.replace(/\.md$/i, "")));
  const inScope = isReadme
    ? (q) => q.module === dir
    : (q) => q.module === dir && (q.sourceFile || "README.md") === base;
  // [SF] "quick" = a random subset capped at the user's deckLen() pref; "all" runs
  // every question in scope (limit = pool size). README stays whole-module + deckLen.
  const launch = isReadme
    ? () => startBlitz(section, [dir])
    : (limit) => startBlitz(section, [dir], [dir + "|" + base], limit);
  const block = document.createElement("div");
  block.className = "eval-block";
  block.innerHTML = `<div class="eval-h">Evaluate yourself</div>
    <p class="eval-sub" id="evalSub">Quick check on ${esc(name)} &mdash; misses feed your review deck.</p>
    <div class="eval-actions"><button class="eval-btn" id="evalBtn">Quiz this ${isCase ? "case study" : isReadme ? "topic" : "sub-topic"}</button></div>`;
  main.appendChild(block);
  block.querySelector("#evalBtn").addEventListener("click", async () => {
    const bank = await loadPool(section);
    const pool = (bank || []).filter(inScope);
    if (!pool.length) { announce("No questions extracted for this page yet."); return; }
    closeReader();
    launch(null);                                    // quick: deckLen()-capped random subset
  });
  // Fill the question count in the background (also warms the bank cache for
  // an instant quiz start); hide the block for 0-question pages. Skips the
  // multi-MB fetch on data-saver connections.
  if (!(navigator.connection && navigator.connection.saveData)) {
    loadPool(section).then((bank) => {
      const n = (bank || []).filter(inScope).length;
      if (!n) { block.remove(); return; }
      const sub = block.querySelector("#evalSub");
      if (sub) sub.textContent = `${n} questions on ${name} — misses feed your review deck.`;
      // [SF] On a sub-file with more questions than a quick deck holds, offer an
      // "all N" run alongside the quick one. When N <= deckLen() the two are
      // identical, so keep the single button (today's behavior).
      if (!isReadme && n > deckLen()) {
        const actions = block.querySelector(".eval-actions");
        block.querySelector("#evalBtn").textContent = `Quick quiz (${deckLen()})`;
        const allBtn = document.createElement("button");
        allBtn.className = "eval-btn secondary";
        allBtn.textContent = `Quiz all ${n}`;
        allBtn.addEventListener("click", () => { closeReader(); launch(n); });
        actions.appendChild(allBtn);
      }
    }).catch(() => {});
  }
}

// "Continue your path": up to three cards at the end of every module page — the
// next unread module in the study order, the strongest graph-related modules,
// and a resume-last-read card — so reading flows into more reading without a
// trip back to Study. Each card carries its TARGET's section hue (resume may be
// cross-section). Quietly animated in the first time the page is read to the
// end — see revealClosure().
async function appendWhatNext(main, path) {
  if (state.inQuiz) return;
  const dir = path.replace(/\/[^/]+$/, "");
  const files = (state.index && state.index.files) || {};
  if (!files[dir] || !reader.nav) return;            // needs a module page + nav context
  // sd_last_read still holds the PREVIOUS page here (this render pass overwrites
  // it after us) — but read it before the graph await can lose that race.
  let last = null;
  try { last = JSON.parse(localStorage.getItem("sd_last_read") || "null"); } catch { /* corrupt */ }
  const section = dir.split("/")[0];
  const myPath = path;
  const g = await loadGraph(section);                // cached; null offline / no graph file
  if (reader.path !== myPath) return;                // user navigated away during the fetch
  const cards = [], seen = new Set([dir]);
  const push = (p, kind, title) => {
    const d = p.replace(/\/[^/]+$/, "");
    if (seen.has(d) || cards.length >= 3) return;
    seen.add(d);
    cards.push({ path: p, kind, title: title || titleize(d.split("/").pop()) });
  };
  const list = reader.nav.list || [];
  for (let i = reader.nav.idx + 1; i < list.length; i++) {
    if (!isModuleRead(list[i].path)) { push(list[i].path, "Up next", list[i].title); break; }
  }
  if (g && Array.isArray(g.pairs)) {
    const rel = g.pairs.filter((pr) => pr.a === dir || pr.b === dir)
      .sort((x, y) => (y.w || 0) - (x.w || 0))
      .map((pr) => (pr.a === dir ? pr.b : pr.a));
    const unread = rel.filter((m) => !isModuleRead(m + "/README.md"));
    for (const m of unread.concat(rel.filter((m) => isModuleRead(m + "/README.md")))) {
      if (cards.length >= 2) break;                  // related takes at most 2; leave room for resume
      push(m + "/README.md", "Related");
    }
  }
  if (last && last.path && last.path !== path) push(last.path, "Resume", last.title);
  if (!cards.length) return;
  const block = document.createElement("div");
  block.className = "whatnext";
  block.innerHTML = `<div class="wn-rule" aria-hidden="true"></div>
    <div class="wn-h">Continue your path</div><div class="wn-cards"></div>`;
  const wrap = block.querySelector(".wn-cards");
  for (const c of cards) {
    const tdir = c.path.replace(/\/[^/]+$/, "");
    const tid = sectionIdentity(tdir);
    const read = isModuleRead(c.path);
    const b = document.createElement("button");
    b.type = "button"; b.className = "wn-card";
    if (tid) b.style.setProperty("--sec-accent", tid.accent);
    b.setAttribute("aria-label", `${c.kind}: ${c.title}${read ? " (read)" : ""}`);
    b.innerHTML = `<span class="wn-kind">${esc(c.kind)}</span>
      <span class="wn-title">${esc(c.title)}</span>
      <span class="wn-sec"><span class="wn-glyph" aria-hidden="true">${esc(tid ? tid.glyph : "§")}</span>${esc(SECTION_LABELS[tdir.split("/")[0]] || tdir.split("/")[0])}${read ? `<span class="wn-read">✓ read</span>` : ""}</span>`;
    b.addEventListener("click", () => {
      reader.back.push({ path: reader.path, title: reader.titleText, nav: reader.nav });
      openReaderPath(c.path, c.title, null);         // navFromIndex synthesizes the target's nav
    });
    wrap.appendChild(b);
  }
  main.appendChild(block);
}

// Heading anchors: hovering a section heading reveals a "#" that copies the
// section's #/reader/<path>@<frag> deep link. Wired AFTER buildToc (which reads
// heading textContent for labels); find's TreeWalker rejects .h-anchor text.
function wireHeadingAnchors(main, path) {
  main.querySelectorAll("h2[id], h3[id]").forEach((h) => {
    const b = document.createElement("button");
    b.type = "button"; b.className = "h-anchor";
    b.setAttribute("aria-label", "Copy link to section");
    b.textContent = "#";
    b.addEventListener("click", () => {
      const url = location.href.split("#")[0] + readerHash(path, h.id);
      // The tick used to render unconditionally, and writeText rejects
      // asynchronously so the surrounding try never caught a denial: a blocked
      // clipboard still claimed success. Only confirm what actually happened.
      navigator.clipboard?.writeText(url).then(() => {
        b.textContent = "✓"; b.classList.add("ok");
        setTimeout(() => { b.textContent = "#"; b.classList.remove("ok"); }, 1200);
      }).catch(() => announce("Couldn't copy the link — clipboard is blocked."));
    });
    h.appendChild(b);
  });
}

// Quiet end-of-read closure: the first time a page is read to the end, the
// what-next rule draws itself and the cards rise in. Deliberately below the
// moments-engine celebration bar — no confetti, no sound, one soft haptic.
function revealClosure() {
  const block = el("#readerBody .whatnext");
  if (!block || block.classList.contains("wn-live")) return;
  block.classList.add("wn-live");
  haptic("correct");
}

// Open any repo content file by path. Pushing onto the back-stack is the caller's
// job (cross-links push; Back/Prev/Next do not), keeping history clean.
// Build a Study-equivalent nav context for a path from the boot-time index
// (state.index.files: "section/module" -> [md files]) — zero bank fetches.
function navFromIndex(path) {
  const section = path.split("/")[0];
  // [CS] A case study reached by deep link / cross-link gets case-track nav (from
  // index.caseStudies), not the section's module nav — so Prev/Next and the sidebar
  // walk the case studies, and its 3-segment path resolves correctly.
  if (path.includes("/case_studies/")) {
    const cs = caseStudiesFromIndex(section);
    if (cs.length) {
      const list = cs.map((c) => ({ path: c.file, title: c.name }));
      const idx = list.findIndex((m) => m.path === path);
      return { list, idx: idx === -1 ? 0 : idx };
    }
  }
  const files = (state.index && state.index.files) || {};
  const mods = Object.keys(files).filter((k) => k.startsWith(section + "/"));
  if (!mods.length) return null;
  const order = STUDY_ORDER[section] || [];
  mods.sort((a, b) => {
    const ai = order.indexOf(a), bi = order.indexOf(b);
    return (ai === -1 ? 9999 : ai) - (bi === -1 ? 9999 : bi);
  });
  const list = mods.map((m) => ({ path: `${m}/README.md`, title: titleize(m.split("/").pop()) }));
  const dir = path.replace(/\/[^/]+$/, "");
  const idx = list.findIndex((m) => m.path.replace("/README.md", "") === dir || m.path === path);
  return { list, idx: idx === -1 ? 0 : idx };
}

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
  // Deep links and dive-deeper opens arrive without Study's nav context;
  // synthesize one from the boot-time index so the module tree and Prev/Next
  // are always available, not only when entered through Study.
  reader.nav = navCtx || navFromIndex(path);
  reader.titleText = title || titleFromPath(path);
  // Soft-focus: remember who opened the reader so focus can return on close.
  // In-reader nav (Prev/Next) keeps the reader-open class, so the invoker is
  // captured only on the first open, never overwritten while browsing.
  if (!document.body.classList.contains("reader-open")) _readerInvoker = document.activeElement;
  let panel = el("#reader");
  if (!panel) { panel = document.createElement("aside"); panel.id = "reader"; document.body.appendChild(panel); }
  // Section identity: one JS-set property scopes every accent consumer (progress
  // bar, TOC, drop-cap, masthead, what-next cards) to the section's hue.
  const ident = sectionIdentity(path);
  panel.style.setProperty("--sec-accent", ident ? ident.accent : "var(--accent)");
  const nav = reader.nav;
  const backBtn = reader.back.length
    ? `<button class="reader-nav" id="readerBack" title="Back">&lsaquo; Back</button>` : "";
  const modBtn = nav
    ? `<button class="reader-nav reader-icon" id="readerMod" title="Module list">&#9776;</button>` : "";
  const navBtns = nav
    ? `<button class="reader-nav" id="readerPrev" title="Previous topic" ${nav.idx <= 0 ? "disabled" : ""}>&lsaquo; Prev</button>
       <button class="reader-nav" id="readerNext" title="Next topic" ${nav.idx >= nav.list.length - 1 ? "disabled" : ""}>Next &rsaquo;</button>` : "";
  panel.innerHTML = `<div class="reader-grip" id="readerGrip"></div>
    <div class="reader-peek" aria-hidden="true"></div>
    <div class="reader-head">
      ${backBtn}${modBtn}
      <span class="reader-title">${esc(reader.titleText)}</span>
      ${navBtns}
      <button class="reader-nav reader-icon rfs" id="readerFsDn" title="Smaller text">A&#8722;</button>
      <button class="reader-nav reader-icon rfs" id="readerFsUp" title="Larger text">A+</button>
      <button class="reader-nav reader-icon" id="readerType" title="Reading options (font, width, drop-cap)">Aa</button>
      <button class="reader-nav reader-icon" id="readerFindBtn" title="Find in page (Ctrl/Cmd+F)">&#8981;</button>
      <button class="reader-nav reader-icon" id="readerIdx" title="Contents">&#8801;</button>
      <button class="reader-nav reader-icon" id="readerFull" title="Immersive reading (F)">&#11036;</button>
      <button class="reader-close" id="readerClose" title="Close (Esc)">&times;</button>
    </div>
    <div class="reader-progress" aria-hidden="true"><i id="readerProg"></i></div>
    <button class="reader-exit" id="readerExit" title="Exit immersive reading (Esc)" aria-label="Exit immersive reading">&#10530; Exit</button>
    <div class="reader-body" id="readerBody"><div class="loading">Loading&hellip;</div></div>
    <button class="reader-top" id="readerTop" title="Back to top" aria-label="Back to top">&uarr;</button>`;
  document.body.classList.add("reader-open");
  panel.tabIndex = -1;
  panel.focus({ preventScroll: true });          // soft-focus the panel (page behind stays interactive ≥900px)
  applyReaderModes();
  wireGrips();
  el("#readerClose").addEventListener("click", closeReader);
  el("#readerFsDn").addEventListener("click", () => applyReaderFont(-1));
  el("#readerFsUp").addEventListener("click", () => applyReaderFont(1));
  el("#readerType").addEventListener("click", (e) => openReaderTypeMenu(e.currentTarget));
  // Reading progress bar + back-to-top, driven by the body's scroll position.
  {
    const body = el("#readerBody"), prog = el("#readerProg"), top = el("#readerTop");
    // Coalesced to one update per frame, with every layout READ done before any
    // style WRITE: the old handler wrote the progress width and then let the
    // scrollspy read heading offsets, forcing a synchronous reflow per scroll
    // event — visible jitter on phones. Progress moves via transform (scaleX),
    // which the compositor animates without touching layout at all.
    let scrollRaf = 0;
    body.addEventListener("scroll", () => {
      if (scrollRaf) return;
      scrollRaf = requestAnimationFrame(() => {
        scrollRaf = 0;
        const st = body.scrollTop;
        const max = body.scrollHeight - body.clientHeight;
        if (reader._spy) reader._spy();
        maybeMarkRead(reader.path, body);            // reading feeds the game (Phase 6)
        prog.style.transform = `scaleX(${max > 0 ? st / max : 0})`;
        top.classList.toggle("show", st > 600);
        if (panel.classList.contains("head-peek")) panel.classList.remove("head-peek");
        scheduleScrollSave(reader.path, st);
        if (reader._read && !reader._closed) { reader._closed = true; revealClosure(); }
      });
    }, { passive: true });
    top.addEventListener("click", () => body.scrollTo({ top: 0, behavior: REDUCED() ? "auto" : "smooth" }));
  }
  if (nav) {
    el("#readerMod").addEventListener("click", () => {
      reader.modules = !reader.modules;
      safeSet("sd_reader_modules", reader.modules ? "1" : "0");
      applyReaderModes();
    });
  }
  el("#readerIdx").addEventListener("click", () => {
    reader.toc = !reader.toc; safeSet("sd_reader_toc", reader.toc ? "1" : "0"); applyReaderModes();
  });
  el("#readerFull").addEventListener("click", () => setReaderFull(!reader.full));
  el("#readerExit").addEventListener("click", () => setReaderFull(false));
  el("#readerFindBtn").addEventListener("click", openReaderFind);
  // Touch has no hover: tapping the top peek strip momentarily reveals the head
  // (so font/nav/close stay reachable without leaving immersive). Auto-hides on
  // the next scroll or a second tap.
  {
    const peek = el(".reader-peek");
    if (peek) peek.addEventListener("click", () => panel.classList.toggle("head-peek"));
  }
  if (backBtn) el("#readerBack").addEventListener("click", () => { const p = reader.back.pop(); if (p) openReaderPath(p.path, p.title, p.nav); });
  if (nav) {
    el("#readerPrev").addEventListener("click", () => { if (nav.idx > 0) openReaderPath(nav.list[nav.idx - 1].path, nav.list[nav.idx - 1].title, { list: nav.list, idx: nav.idx - 1 }); });
    el("#readerNext").addEventListener("click", () => { if (nav.idx < nav.list.length - 1) openReaderPath(nav.list[nav.idx + 1].path, nav.list[nav.idx + 1].title, { list: nav.list, idx: nav.idx + 1 }); });
  }
  try {
    if (readerCache[path] == null) {
      const r = await fetch(`../${path}`, { cache: "no-store" });
      if (!r.ok) throw { missing: r.status === 404 };
      capInsert(readerCache, path, await r.text(), READER_CACHE_CAP);   // [PERF] FIFO-bounded
    }
    if (reader.path !== path) return;              // user navigated away during the fetch
    const b = el("#readerBody");
    b.innerHTML = `<nav class="reader-modules" id="readerModules"></nav><div class="modules-grip" id="modulesGrip"></div><div class="md-body" id="readerMain">${mdRender(readerCache[path])}</div><div class="toc-grip" id="tocGrip"></div><nav class="reader-toc" id="readerToc"></nav>`;
    wireSidebarGrips();
    const main = el("#readerMain");
    buildModuleNav(el("#readerModules"), reader.nav, path);
    const headCount = buildToc(el("#readerToc"), main);
    el("#readerIdx").style.display = headCount >= 3 ? "" : "none";   // nothing to index -> hide toggle
    buildMasthead(main, path);                     // badge + title + rule + "~N min read"
    wireReaderBody(main);
    wireRecallPrompts(main);                       // think-first: collapse §12 answers
    wireDiagramsAndCopy(main);                     // copy buttons + ASCII-diagram zoom
    wireTables(main);                              // wrap wide tables in a horizontal-scroll box
    renderMermaid(main);                           // no-op when page has no mermaid fences
    wireHeadingAnchors(main, path);                // hover-to-copy section deep links
    appendEvalBlock(main, path);                   // "Evaluate me" quiz launcher
    appendWhatNext(main, path);                    // "Continue your path" cards (async, appends when ready)
    wireScrollSpy(main, b);                        // active-section highlight + §X/M
    reader._read = false;                          // reset per-page read-completion latch (Phase 6)
    reader._closed = false;                        // reset per-page closure-reveal latch
    // Restore scroll: an explicit #frag wins; else resume the saved offset; else top.
    if (frag) { b.scrollTop = 0; const t = main.querySelector("#" + CSS.escape(frag)); if (t) t.scrollIntoView({ block: "start" }); }
    else { b.scrollTop = savedScrollFor(path); if (reader._spy) reader._spy(); }
    // Pages that fit without scrolling still count as read after a short dwell.
    setTimeout(() => {
      if (reader.path !== path) return;
      if (!reader._read) maybeMarkRead(path, b);
      if (reader._read && !reader._closed) { reader._closed = true; revealClosure(); }
    }, 1600);
    safeSet("sd_last_read", JSON.stringify({ path, title: reader.titleText }));   // Study's "Continue reading"
  } catch (e) {
    // [E1] reader failure keeps its own in-panel error (not the full errorScreen
    // — the underlying screen is still live behind the pane). A 404 is a moved/
    // missing page (offer the module home); anything else is treated as an
    // offline/transport failure and keeps the retry.
    const b = el("#readerBody");
    if (b && e && e.missing) {
      const home = path.replace(/\/[^/]+$/, "") + "/README.md";
      const isReadme = /README\.md$/i.test(path);
      b.innerHTML = `<div class="error">This page isn't in the repo — it may have moved.
          <div class="row" style="margin-top:10px">
            ${isReadme ? "" : `<button class="ghost" id="readerHome">Open module home</button>`}
            <button class="ghost" id="readerCloseErr">Close</button>
          </div>
        </div>`;
      const hb = el("#readerHome");
      if (hb) hb.addEventListener("click", () => openReaderPath(home, null, null));
      el("#readerCloseErr").addEventListener("click", closeReader);
    } else if (b) {
      b.innerHTML = `<div class="error">Couldn't load this page. Check your connection and try again.
          <div class="row" style="margin-top:10px"><button class="ghost" id="readerRetry">Try again</button></div>
        </div>`;
      el("#readerRetry").addEventListener("click", () => openReaderPath(path, title, navCtx, frag));
    }
  }
}

// Entry point from a quiz/flashcard reveal: a module README, fresh history, no prev/next.
function openReader(module, moduleName) {
  reader.back = [];
  return openReaderPath(`${module}/README.md`, moduleName, null);
}

// Remove the reader overlay only; the underlying screen DOM is untouched.
// A diagram zoom overlay (openDiagramZoom) is appended to <body>, a sibling of
// #reader — so removing the reader does NOT reclaim it. Any open viewer must be
// torn down explicitly, or it strands over whatever screen comes next (e.g. a
// quiz launched from the reader's "Quiz this topic" button).
function closeDiagramZoom() {
  document.querySelectorAll(".mermaid-overlay").forEach((o) => (o._close ? o._close() : o.remove()));
}

function closeReaderDom() {
  closeDiagramZoom();                                // never let a zoom overlay outlive the reader that spawned it
  document.body.classList.remove("reader-open", "reader-full");
  const p = el("#reader"); if (p) p.remove();
  reader.path = null; reader.back = []; reader.nav = null;
  if (_readerInvoker && _readerInvoker.isConnected) _readerInvoker.focus({ preventScroll: true });
  _readerInvoker = null;
}

// User-initiated close (X / Esc): drop the overlay and restore the underlying
// screen's hash. (Browser Back is handled in the router, which calls closeReaderDom.)
function closeReader() {
  closeReaderDom();
  if (location.hash.startsWith("#/reader")) history.replaceState(null, "", state.underHash || "#/home");
}

/* ============================================================================
   ---------- [D] Coach ----------
   Everything below, up to the matching end marker, is the in-app coach
   (Phase D): daily pick, the message bank + true-specifics voice, the
   reboarding protocol, quest generation, the Friday Debrief, and the same-day
   streak nudge. Persists to `sd_coach` (additive to `sd_progress`). Touches
   the rest of app.js only via small blocks marked with a [D] comment tag
   inside boot(), dispatch(), and renderHome() — everything else here is new,
   self-contained code to keep this phase's merge surface small.
   ========================================================================== */

/* ---------- [D] date/time helpers ---------- */
function toISO(d) { return d.toLocaleDateString("en-CA"); }
function isoAdd(d, days) { const n = new Date(d); n.setDate(n.getDate() + days); return n; }
function daysBetweenISO(aISO, bISO) {
  return Math.round((new Date(bISO + "T00:00:00") - new Date(aISO + "T00:00:00")) / 86400000);
}
// Monday of the week containing `iso` (Date object).
function weekStart(iso) {
  const d = new Date(iso + "T00:00:00");
  const day = d.getDay();                          // 0 Sun .. 6 Sat
  return isoAdd(d, day === 0 ? -6 : 1 - day);
}
// ISO date of the most recent Friday on/before `iso` — the Debrief's week key.
function mostRecentFriday(iso) {
  const d = new Date(iso + "T00:00:00");
  return toISO(isoAdd(d, -((d.getDay() + 2) % 7)));
}
function dayOfYear(iso) {
  const d = new Date(iso + "T00:00:00");
  return Math.floor((d - new Date(d.getFullYear(), 0, 0)) / 86400000);
}
function ordinal(n) {
  const s = ["th", "st", "nd", "rd"], v = n % 100;
  return n + (s[(v - 20) % 10] || s[v] || s[0]);
}
function fmtDateShort(iso) { return `the ${ordinal(new Date(iso + "T00:00:00").getDate())}`; }
// QA seam twin of todayISO(): a ?qa_time=HH:MM URL param overrides "now" for the
// same-day nudge's local-time gate. Only honored when present and well-formed.
function nowHM() {
  const qp = new URLSearchParams(location.search).get("qa_time");
  if (qp && /^\d{2}:\d{2}$/.test(qp)) return qp;
  const d = new Date();
  return String(d.getHours()).padStart(2, "0") + ":" + String(d.getMinutes()).padStart(2, "0");
}

/* ---------- [D] coach memory (sd_coach) ---------- */
// Additive to sd_progress. Shape: lastTemplates (cap 3, message-bank variety),
// observations [{date, note}] (cap 12, general audit trail), lastComebackDate,
// praisedSections {section: dateISO} (once-per-week-per-section gate for the
// accuracy-delta observation), todayTemplate {date, id} (same-day stability),
// lastDebrief (Friday key of the last-opened debrief), quests, nudgedOn.
function loadCoach() {
  let c = null;
  try { c = JSON.parse(localStorage.getItem("sd_coach")); } catch { /* corrupt -> reseed */ }
  return (c && typeof c === "object") ? c : {
    lastTemplates: [], observations: [], lastComebackDate: null, praisedSections: {},
    todayTemplate: null, lastDebrief: null, quests: [], nudgedOn: null,
  };
}
function saveCoach(c) { safeSet("sd_coach", JSON.stringify(c)); }
function pushObservation(coach, note) {
  coach.observations = [{ date: todayISO(), note }, ...(coach.observations || [])].slice(0, 12);
}
function recentlyPraised(coach, section) {
  const d = coach.praisedSections && coach.praisedSections[section];
  return !!d && daysBetweenISO(d, todayISO()) < 7;
}

/* ---------- [D] daily pick — port of the old pick_today.py logic ---------- */
// Priority: (a) never-played sections, alphabetical rotation by day; (b) lowest
// accuracy among sections with seen >= 5; (c) least-seen. Never repeats
// yesterday's section unless it is the only candidate left, in which case the
// forced repeat is tagged why:"rotation" instead of whatever tier chose it.
function coachPick() {
  const avail = Object.keys((state.index && state.index.sections) || {}).sort();
  if (!avail.length) return { section: null, why: "new" };
  const seenMap = state.progress.sections || {};
  const history = state.progress.history || [];
  const lastSection = history.length ? history[history.length - 1].section : null;
  const seen = (s) => (seenMap[s] && seenMap[s].seen) || 0;
  const acc = (s) => (seenMap[s] && seenMap[s].seen ? seenMap[s].correct / seenMap[s].seen : null);
  const day = dayOfYear(todayISO());

  const rank = (pool, why) => {
    if (!pool.length) return null;
    let chosen;
    if (why === "new") chosen = pool.slice().sort()[day % pool.length];
    else if (why === "weak") chosen = pool.slice().sort((a, b) => acc(a) - acc(b) || seen(a) - seen(b))[0];
    else chosen = pool.slice().sort((a, b) => seen(a) - seen(b) || a.localeCompare(b))[0];
    return { section: chosen, why };
  };
  const tryPick = (excludeLast) => {
    const base = excludeLast && lastSection ? avail.filter((s) => s !== lastSection) : avail;
    if (!base.length) return null;
    const unplayed = base.filter((s) => seen(s) === 0);
    if (unplayed.length) return rank(unplayed, "new");
    const weakPool = base.filter((s) => seen(s) >= 5);
    if (weakPool.length) return rank(weakPool, "weak");
    return rank(base, "least");
  };

  let pick = tryPick(true) || tryPick(false);
  if (pick && pick.section === lastSection) pick = { section: pick.section, why: "rotation" };
  return pick || { section: avail[0], why: "least" };
}

/* ---------- [D] true specifics computed from live progress ---------- */
// Cumulative accuracy for a section using only direct-blitz history entries up
// to and including dateISO (review/weak-spot sessions tag section as
// "review"/"weakspots" and mix content sections, so they're not attributable
// per-section here — an accepted approximation, same one the Debrief's
// per-section deltas make).
function accAsOf(section, dateISO) {
  const rows = (state.progress.history || []).filter((h) => h.section === section && h.date <= dateISO);
  const answered = rows.reduce((s, h) => s + (h.answered || 0), 0);
  const correct = rows.reduce((s, h) => s + (h.correct || 0), 0);
  return answered ? { acc: correct / answered, seen: answered } : null;
}
// >=10-point, 7-day accuracy climb for a section, or null if there isn't a
// large-enough, clean-enough signal to say something true about it.
function weeklyAccDelta(section) {
  const today = todayISO();
  const cutoffISO = toISO(isoAdd(new Date(today + "T00:00:00"), -7));
  const now = accAsOf(section, today), then = accAsOf(section, cutoffISO);
  if (!now || !then || then.seen < 5 || now.seen - then.seen < 3) return null;
  return { deltaPts: Math.round((now.acc - then.acc) * 100), sinceLabel: fmtDateShort(cutoffISO) };
}
// Consecutive days (ending today if already met, else yesterday) at/above the
// daily XP goal.
function goalStreakDays() {
  const map = {};
  for (const h of state.progress.history || []) map[h.date] = (map[h.date] || 0) + (h.xp || 0);
  const today = todayISO();
  let d = new Date(today + "T00:00:00");
  if (!(map[today] >= DAILY_XP_GOAL)) d = isoAdd(d, -1);
  let count = 0;
  for (let guard = 0; guard < 3650; guard++) {
    const iso = toISO(d);
    if ((map[iso] || 0) < DAILY_XP_GOAL) break;
    count++; d = isoAdd(d, -1);
  }
  return count;
}
// The day right after a >2-day gap ends (compares the two most recent history
// entries) — purely derived from history, so it needs no hook elsewhere.
function detectComeback() {
  const h = state.progress.history || [];
  if (h.length < 2) return null;
  const last = h[h.length - 1], prev = h[h.length - 2];
  const gap = daysBetweenISO(prev.date, last.date);
  const sinceLast = daysBetweenISO(last.date, todayISO());
  if (gap > 2 && sinceLast === 1) {
    return { gapDays: gap, yesterdayPct: last.answered ? Math.round((last.correct / last.answered) * 100) : null, date: last.date };
  }
  return null;
}
// Today, before playing: gap since lastPlayed > 2 days -> reboarding protocol.
function reboardingInfo() {
  const p = state.progress;
  if (!p.lastPlayed) return null;
  const gap = daysBetweenISO(p.lastPlayed, todayISO());
  return gap > 2 ? { days: gap, dueCount: dueReviews().length } : null;
}

/* ---------- [D] the voice: ~30 message templates, true specifics only ---------- */
// Selection: build the day's context, find the highest-priority group with an
// eligible template, then seed-pick within that group (mulberry32(cyrb53(date
// + streak))) so the choice is stable across reloads the same day but varies
// day to day. Never repeats a template in sd_coach.lastTemplates when another
// eligible one exists. NO exclamation marks; terse, specific, remembers.
const WHY_CHIP = { new: "new territory", weak: "weak spot", least: "least practiced", rotation: "rotation" };
const COACH_PRIORITY = ["firstEver", "comeback", "dueHigh", "streakMilestone", "goalStreak", "accDelta", "weekend", "byWhy"];
const COACH_LINES = [
  { id: "first1", group: "firstEver", when: (c) => c.isFirstEver, text: (c) => `First one. Ten questions, five minutes — start with ${label(c.section)}.` },
  { id: "first2", group: "firstEver", when: (c) => c.isFirstEver, text: () => `Nothing recorded yet. No baseline to protect, so just answer.` },
  { id: "first3", group: "firstEver", when: (c) => c.isFirstEver, text: (c) => `Day one. ${label(c.section)} is as good a place to start as any.` },

  { id: "cb1", group: "comeback", when: (c) => !!c.comeback, text: (c) => `Back ${c.comeback.gapDays} days now. Yesterday: ${c.comeback.yesterdayPct}% cold. Memory held better than you feared.` },
  { id: "cb2", group: "comeback", when: (c) => !!c.comeback, text: (c) => `${c.comeback.gapDays} days off, then ${c.comeback.yesterdayPct}% on the first try back. The spaced reviews did their job.` },
  { id: "cb3", group: "comeback", when: (c) => !!c.comeback, text: (c) => `You came back after ${c.comeback.gapDays} days and still landed ${c.comeback.yesterdayPct}%. Keep going.` },

  { id: "due1", group: "dueHigh", when: (c) => c.due >= 15, text: (c) => `${c.due} reviews stacked up. Clear the oldest ten before they compound further.` },
  { id: "due2", group: "dueHigh", when: (c) => c.due >= 15, text: (c) => `${c.due} due for review. That backlog doesn't shrink on its own.` },
  { id: "due3", group: "dueHigh", when: (c) => c.due >= 15, text: (c) => `${c.due} questions waiting on review. Ten minutes there clears most of it.` },

  { id: "sm1", group: "streakMilestone", when: (c) => c.freezeAway, text: (c) => `Day ${c.streak} — one more for a freeze token.` },
  { id: "sm2", group: "streakMilestone", when: (c) => c.milesAway === 1, text: (c) => `One more day puts you at a ${c.nextMile}-day streak.` },
  { id: "sm3", group: "streakMilestone", when: (c) => c.milesAway != null && c.milesAway > 1 && c.milesAway <= 2, text: (c) => `${c.milesAway} days from a ${c.nextMile}-day streak.` },

  { id: "gs1", group: "goalStreak", when: (c) => c.goalStreak >= 3, text: (c) => `${c.goalStreak} straight days at the XP goal. ${label(c.section)} keeps the run going.` },
  { id: "gs2", group: "goalStreak", when: (c) => c.goalStreak >= 3, text: (c) => `${c.goalStreak} days hitting goal in a row. Numbers don't lie.` },

  { id: "ad1", group: "accDelta", when: (c) => !!c.accDelta, text: (c) => `Your ${label(c.accDelta.section)} accuracy climbed ${c.accDelta.deltaPts} points since ${c.accDelta.sinceLabel}.` },
  { id: "ad2", group: "accDelta", when: (c) => !!c.accDelta, text: (c) => `${label(c.accDelta.section)} is up ${c.accDelta.deltaPts} points since ${c.accDelta.sinceLabel}. That's real progress.` },

  { id: "we1", group: "weekend", when: (c) => c.isWeekend, text: (c) => `Weekend or not, ten questions still count — ${label(c.section)}.` },
  { id: "we2", group: "weekend", when: (c) => c.isWeekend, text: (c) => `Weekend pace is fine. ${label(c.section)}, whenever you get to it.` },
  { id: "we3", group: "weekend", when: (c) => c.isWeekend, text: (c) => `No rush today. ${label(c.section)} will still be there in five minutes.` },

  { id: "new1", group: "byWhy", why: "new", when: () => true, text: (c) => `Untouched section. ${label(c.section)} has nothing recorded yet.` },
  { id: "new2", group: "byWhy", why: "new", when: () => true, text: (c) => `${label(c.section)} hasn't been touched. Today's as good a day as any.` },
  { id: "new3", group: "byWhy", why: "new", when: () => true, text: (c) => `Fresh ground: ${label(c.section)}. No baseline yet, so anything right is a start.` },
  { id: "new4", group: "byWhy", why: "new", when: () => true, text: (c) => `${label(c.section)} is new territory. Ten questions sets the first baseline.` },

  { id: "wk1", group: "byWhy", why: "weak", when: () => true, text: (c) => `${label(c.section)} sits at ${c.acc}% over ${c.seen} questions — the softest spot right now.` },
  { id: "wk2", group: "byWhy", why: "weak", when: () => true, text: (c) => `Lowest accuracy: ${label(c.section)} at ${c.acc}%. Ten more questions moves that number.` },
  { id: "wk3", group: "byWhy", why: "weak", when: () => true, text: (c) => `${label(c.section)} is dragging at ${c.acc}%. Time to close the gap.` },
  { id: "wk4", group: "byWhy", why: "weak", when: () => true, text: (c) => `${c.acc}% in ${label(c.section)} after ${c.seen} questions. Worth the ten minutes.` },

  { id: "ls1", group: "byWhy", why: "least", when: () => true, text: (c) => `${label(c.section)} has the fewest reps of anything you've touched — ${c.seen} so far.` },
  { id: "ls2", group: "byWhy", why: "least", when: () => true, text: (c) => `Least practiced: ${label(c.section)}, ${c.seen} questions in. Building the baseline.` },
  { id: "ls3", group: "byWhy", why: "least", when: () => true, text: (c) => `${label(c.section)} trails everything else on reps. Ten more closes some of that.` },

  { id: "rot1", group: "byWhy", why: "rotation", when: () => true, text: (c) => `Back to ${label(c.section)} — every other section was touched more recently.` },
  { id: "rot2", group: "byWhy", why: "rotation", when: () => true, text: (c) => `${label(c.section)} again. The rotation loops here until something else opens up.` },
];

function buildCoachCtx(pick, coach) {
  const p = state.progress, streak = p.streak || 0;
  const history = p.history || [];
  const today = todayISO();
  const weekday = new Date(today + "T00:00:00").getDay();
  const nextMile = STREAK_MILES.find((m) => m > streak) || null;
  let accDelta = null;
  for (const s of Object.keys((state.index && state.index.sections) || {})) {
    if (recentlyPraised(coach, s)) continue;
    const d = weeklyAccDelta(s);
    if (d && d.deltaPts >= 10 && (!accDelta || d.deltaPts > accDelta.deltaPts)) accDelta = { section: s, ...d };
  }
  const st = p.sections && p.sections[pick.section];
  return {
    section: pick.section, why: pick.why, streak,
    due: dueReviews().length,
    isWeekend: weekday === 0 || weekday === 6,
    isFirstEver: !p.lastPlayed && history.length === 0,
    nextMile, milesAway: nextMile ? nextMile - streak : null,
    freezeAway: streak > 0 && streak % 7 === 6,
    goalStreak: goalStreakDays(),
    comeback: detectComeback(),
    accDelta,
    acc: st && st.seen ? Math.round((st.correct / st.seen) * 100) : null,
    seen: (st && st.seen) || 0,
  };
}

// Returns { templateId, group, text }. Writes sd_coach (lastTemplates,
// todayTemplate, and — only when that group is actually chosen — the
// accDelta/comeback observation) exactly once per call.
function coachMessage(pick) {
  const coach = loadCoach();
  const today = todayISO();
  const ctx = buildCoachCtx(pick, coach);

  // Same-day reload: reuse the exact template already chosen so the card is
  // stable across reloads, rather than re-rolling the seeded pick.
  if (coach.todayTemplate && coach.todayTemplate.date === today) {
    const found = COACH_LINES.find((t) => t.id === coach.todayTemplate.id);
    if (found && found.when(ctx) && (found.group !== "byWhy" || found.why === pick.why)) {
      return { templateId: found.id, group: found.group, text: found.text(ctx) };
    }
  }

  const groups = {};
  for (const t of COACH_LINES) if (t.when(ctx)) (groups[t.group] = groups[t.group] || []).push(t);
  const chosenGroup = COACH_PRIORITY.find((g) => groups[g] && groups[g].length) || "byWhy";
  const pool = chosenGroup === "byWhy"
    ? COACH_LINES.filter((t) => t.group === "byWhy" && t.why === pick.why)
    : groups[chosenGroup];

  const avoid = new Set(coach.lastTemplates || []);
  const candidates = pool.filter((t) => !avoid.has(t.id));
  const finalPool = candidates.length ? candidates : pool;   // everything eligible was used recently -> allow repeats
  const rng = mulberry32(cyrb53(today + ":" + ctx.streak));
  const chosen = finalPool[Math.floor(rng() * finalPool.length)];
  const text = chosen.text(ctx);

  if (chosenGroup === "accDelta" && ctx.accDelta) {
    coach.praisedSections = { ...(coach.praisedSections || {}), [ctx.accDelta.section]: today };
    pushObservation(coach, text);
  } else if (chosenGroup === "comeback" && ctx.comeback) {
    coach.lastComebackDate = ctx.comeback.date;
    pushObservation(coach, text);
  }
  coach.lastTemplates = [chosen.id, ...(coach.lastTemplates || []).filter((id) => id !== chosen.id)].slice(0, 3);
  coach.todayTemplate = { date: today, id: chosen.id };
  saveCoach(coach);
  return { templateId: chosen.id, group: chosenGroup, text };
}

/* ---------- [D] quests (generated on/after each Friday, expire the next) ---------- */
const TIER_NEED = { Bronze: { seen: 8, acc: 0.50 }, Silver: { seen: 20, acc: 0.70 }, Gold: { seen: 40, acc: 0.85 } };
const TIER_ORDER = [null, "Bronze", "Silver", "Gold"];

// Deterministic Fisher-Yates. Accepts a ready rng function ([D] quests) or any
// non-function seed, hashed via cyrb53 -> mulberry32 ([C] gauntlet/interviewer).
function seededShuffle(arr, rngOrSeed) {
  const rng = typeof rngOrSeed === "function" ? rngOrSeed : mulberry32(cyrb53(String(rngOrSeed)) >>> 0);
  const a = arr.slice();
  for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(rng() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; }
  return a;
}
// Section closest to its next mastery-tier boundary (smallest remaining `seen` gap).
function pickTierQuestSection() {
  const secs = state.progress.sections || {};
  let best = null;
  for (const s of Object.keys((state.index && state.index.sections) || {})) {
    const st = secs[s] || { seen: 0, correct: 0 };
    const idx = TIER_ORDER.indexOf(sectionTier(st));
    const next = idx >= 0 && idx < TIER_ORDER.length - 1 ? TIER_ORDER[idx + 1] : null;
    if (!next) continue;
    const need = TIER_NEED[next], gap = Math.max(0, need.seen - st.seen);
    if (!best || gap < best.gap) best = { section: s, nextTier: next, need, gap };
  }
  return best;
}
function generateQuests(friKey) {
  const rng = mulberry32(cyrb53("quests-" + friKey));
  const due = dueReviews().length;
  // Fresh profile (nothing due) can't "clear reviews" — its progress would read
  // pre-completed. Offer a first-run answering quest instead; keep the reviews
  // quest (at the real due count) only when there is actually something due.
  const quests = [due > 0 ? {
    id: "q-reviews-" + friKey, type: "reviews", target: due, weekKey: friKey,
    text: `Clear ${due} review${due === 1 ? "" : "s"}`,
  } : {
    id: "q-answers-" + friKey, type: "answers", target: 15, weekKey: friKey,
    text: "Answer 15 fresh questions",
  }];
  const tierPick = pickTierQuestSection();
  if (tierPick) {
    quests.push({
      id: "q-tier-" + friKey, type: "tier", section: tierPick.section, nextTier: tierPick.nextTier,
      target: tierPick.need.seen, weekKey: friKey, text: `Push ${label(tierPick.section)} to ${tierPick.nextTier}`,
    });
  }
  const touched = new Set(Object.values(state.progress.reviews || {}).map((r) => r.module).filter(Boolean));
  const untouched = capturableModules().filter((m) => !touched.has(m));
  if (untouched.length) {
    const n = Math.min(3, untouched.length);
    const modules = seededShuffle(untouched, rng).slice(0, n);
    quests.push({
      id: "q-modules-" + friKey, type: "modules", modules, target: n, weekKey: friKey,
      text: `Touch ${n} untouched module${n === 1 ? "" : "s"}`,
    });
  }
  return quests;
}
// Regenerates on/after each Friday; lives until the next Friday replaces it.
function ensureQuests() {
  const coach = loadCoach();
  const friKey = mostRecentFriday(todayISO());
  if (!coach.quests || !coach.quests.length || coach.quests[0].weekKey !== friKey) {
    coach.quests = generateQuests(friKey);
    saveCoach(coach);
  }
  // One-time migration: a this-week reviews quest minted before the answers-quest
  // fix regenerates once (deterministic seed -> tier/module quests are identical).
  const rq = coach.quests.find((q) => q.type === "reviews" && q.weekKey === friKey);
  if (rq && !coach.questsV2) { coach.quests = generateQuests(friKey); coach.questsV2 = friKey; saveCoach(coach); }
  return coach.quests;
}
// Progress is always recomputed live from current state — quests store only
// {type, target, section?, modules?}, never a serialized function.
function questProgress(q) {
  if (q.type === "reviews") {
    // Count real clears this week (due-review-answered-correctly), not the
    // target-minus-current heuristic that read 5/5 on a fresh profile.
    const qc = state.progress.questClears;
    const done = (qc && qc.week === q.weekKey) ? Math.min(q.target, qc.count) : 0;
    return { done, target: q.target };
  }
  if (q.type === "answers") {
    const done = (state.progress.history || []).reduce((s, h) =>
      (h.date >= q.weekKey && daysBetweenISO(q.weekKey, h.date) <= 6) ? s + (h.answered || 0) : s, 0);
    return { done: Math.min(q.target, done), target: q.target };
  }
  if (q.type === "tier") {
    const st = (state.progress.sections && state.progress.sections[q.section]) || { seen: 0, correct: 0 };
    return { done: Math.min(q.target, st.seen), target: q.target };
  }
  if (q.type === "modules") {
    const touched = new Set(Object.values(state.progress.reviews || {}).map((r) => r.module).filter(Boolean));
    return { done: (q.modules || []).filter((m) => touched.has(m)).length, target: q.target };
  }
  return { done: 0, target: q.target || 1 };
}
function questChipsHTML(quests) {
  if (!quests || !quests.length) return "";
  const rows = quests.map((q) => {
    const { done, target } = questProgress(q);
    const pct = target ? Math.min(100, Math.round((done / target) * 100)) : 0;
    return `<div class="quest-chip${done >= target ? " done" : ""}" data-qid="${esc(q.id)}">
        <span class="qc-text">${esc(q.text)}</span>
        <span class="qc-bar"><i style="width:${pct}%"></i></span>
        <span class="qc-count">${done}/${target}</span>
      </div>`;
  }).join("");
  return `<div class="quest-row">${rows}</div>`;
}

/* ---------- [D] Friday Debrief ---------- */
function moduleLabel(moduleKey) {
  const parts = (moduleKey || "").split("/");
  return titleize(parts[1] || parts[0] || moduleKey || "");
}
function weekAgg(history, startISO, endISO, section) {
  const rows = history.filter((h) => h.date >= startISO && h.date <= endISO && h.section === section);
  const answered = rows.reduce((s, h) => s + (h.answered || 0), 0);
  const correct = rows.reduce((s, h) => s + (h.correct || 0), 0);
  return { answered, correct, acc: answered ? correct / answered : null };
}
function bestSessionThisWeek(startISO, endISO) {
  const rows = (state.progress.history || []).filter((h) => h.date >= startISO && h.date <= endISO && h.answered > 0);
  if (!rows.length) return null;
  return rows.slice().sort((a, b) => (b.correct / b.answered) - (a.correct / a.answered) || b.xp - a.xp)[0];
}
// Reviews whose most recent correct answer (approximated as due - interval,
// since review records don't log a per-event timestamp) fell inside the range.
function reviewsAnsweredInRange(startISO, endISO) {
  const out = [];
  for (const rv of Object.values(state.progress.reviews || {})) {
    if (!rv.due || !rv.interval) continue;
    const answeredISO = toISO(isoAdd(new Date(rv.due + "T00:00:00"), -rv.interval));
    if (answeredISO >= startISO && answeredISO <= endISO) out.push({ module: rv.module, section: rv.section, interval: rv.interval, answeredISO });
  }
  return out.sort((a, b) => b.interval - a.interval);
}
function debriefHeadline(deltas, held) {
  const bestUp = deltas.find((d) => d.delta >= 5);
  if (bestUp) return `${label(bestUp.section)} climbed ${bestUp.delta} points this week. That's the number that moved.`;
  if (held.length) return `You held ${moduleLabel(held[0].module)} across a ${held[0].interval}-day gap. Spaced repetition, working as designed.`;
  const worseDown = deltas.find((d) => d.delta <= -8);
  if (worseDown) return `${label(worseDown.section)} slipped ${Math.abs(worseDown.delta)} points. Worth a look next week.`;
  return `Quiet week by the numbers. Still logged, still counts.`;
}
// Home shows a "Debrief ready" card once a new week's debrief is available and
// hasn't been opened yet — this naturally covers Fri-Sun of the current week
// and every day after if a previous week's was never opened.
function debriefReady() {
  if (!(state.progress.history || []).length) return false;
  return loadCoach().lastDebrief !== mostRecentFriday(todayISO());
}

function renderDebrief() {
  state.inQuiz = false;
  refreshStats();
  const coach = loadCoach();
  const today = todayISO();
  coach.lastDebrief = mostRecentFriday(today);
  saveCoach(coach);

  const thisStart = weekStart(today), thisStartISO = toISO(thisStart), thisEndISO = toISO(isoAdd(thisStart, 6));
  const lastStart = isoAdd(thisStart, -7), lastStartISO = toISO(lastStart), lastEndISO = toISO(isoAdd(lastStart, 6));
  const history = state.progress.history || [];
  const todayDate = new Date(today + "T00:00:00");

  const dayCells = [];
  for (let i = 0; i < 7; i++) {
    const d = isoAdd(thisStart, i), iso = toISO(d);
    const xp = history.filter((h) => h.date === iso).reduce((s, h) => s + (h.xp || 0), 0);
    const future = d > todayDate;
    const lvl = future ? -1 : xp === 0 ? 0 : xp < 30 ? 1 : xp < 70 ? 2 : xp < 120 ? 3 : 4;
    dayCells.push({ iso, xp, lvl, label: d.toLocaleDateString("en-US", { weekday: "short" }) });
  }

  const deltas = Object.keys((state.index && state.index.sections) || {}).map((s) => {
    const thisWk = weekAgg(history, thisStartISO, thisEndISO, s), lastWk = weekAgg(history, lastStartISO, lastEndISO, s);
    if (thisWk.answered < 5 && lastWk.answered < 5) return null;
    if (thisWk.acc == null || lastWk.acc == null) return null;
    return { section: s, delta: Math.round((thisWk.acc - lastWk.acc) * 100), thisAcc: Math.round(thisWk.acc * 100), lastAcc: Math.round(lastWk.acc * 100) };
  }).filter(Boolean).sort((a, b) => b.delta - a.delta);

  const held = reviewsAnsweredInRange(thisStartISO, thisEndISO).filter((r) => r.interval >= 14);
  const momentHTML = held.length
    ? `You held <b>${esc(moduleLabel(held[0].module))}</b> across a ${held[0].interval}-day gap.`
    : (() => {
        const best = bestSessionThisWeek(thisStartISO, thisEndISO);
        return best
          ? `Best session this week: <b>${Math.round((best.correct / best.answered) * 100)}%</b> in ${esc(label(best.section))} (${best.correct}/${best.answered}).`
          : `Quiet week &mdash; no sessions recorded yet.`;
      })();

  const headline = debriefHeadline(deltas, held);
  const quests = ensureQuests();

  const stripHTML = `<div class="week-strip">${dayCells.map((c) => `
      <div class="week-cell ${c.lvl < 0 ? "wc-future" : "wc-l" + c.lvl}" title="${c.iso}: ${c.xp} XP">
        <span class="wc-day">${c.label}</span>
      </div>`).join("")}</div>`;
  const deltaHTML = deltas.length ? deltas.map((d) => `
      <div class="delta-row ${d.delta >= 0 ? "up" : "down"}">
        <span class="dr-name">${esc(label(d.section))}</span>
        <span class="dr-num">${d.delta >= 0 ? "+" : ""}${d.delta} pts</span>
        <span class="dr-sub">${d.lastAcc}% &rarr; ${d.thisAcc}%</span>
      </div>`).join("") : `<p class="hm-empty">Not enough volume in any section this week or last (5+ answers needed) to compare.</p>`;

  app.innerHTML = `
    <div class="hero"><h1>Friday Debrief</h1><p>${esc(headline)}</p></div>
    <h2 class="section-h">This week</h2>
    ${stripHTML}
    <h2 class="section-h">Section deltas &mdash; this week vs last</h2>
    <div class="delta-list">${deltaHTML}</div>
    <h2 class="section-h">Moment of the week</h2>
    <p class="moment-week">${momentHTML}</p>
    <h2 class="section-h">Next week</h2>
    ${questChipsHTML(quests)}
    <div class="row" style="margin-top:18px"><button class="primary" id="debriefHome">Back to today</button></div>`;
  el("#debriefHome").addEventListener("click", () => go("#/home"));
  wireReveals();
}

/* ---------- [D] same-day streak nudge (tab-open only) ---------- */
function maybeShowNudge() {
  if (state.inQuiz || el("#nudgeToast")) return;
  const coach = loadCoach();
  const today = todayISO();
  if (coach.nudgedOn === today) return;
  const streak = state.progress.streak || 0;
  if (streak < 3) return;
  if ((state.progress.history || []).some((h) => h.date === today)) return;   // already played today
  if (nowHM() < "16:00") return;
  const o = document.createElement("div");
  o.className = "nudge-toast"; o.id = "nudgeToast";
  o.setAttribute("role", "status");
  o.innerHTML = `<span>Your ${streak}-day streak ends at midnight. Ten questions cover it.</span>
    <button class="nudge-dismiss" id="nudgeDismiss" aria-label="Dismiss">&times;</button>`;
  document.body.appendChild(o);
  el("#nudgeDismiss").addEventListener("click", () => {
    const c = loadCoach(); c.nudgedOn = todayISO(); saveCoach(c);
    o.classList.add("out");
    setTimeout(() => o.remove(), REDUCED() ? 0 : 200);
  });
}
/* ---------- end [D] Coach ---------- */

/* ---------- hash router ---------- */
// Screens are hash routes so browser Back/Forward, refresh, and shareable URLs
// all work without a build step. go() sets the hash; a single hashchange
// listener resolves the route -> screen render. _navLock swallows the one
// programmatic hash write we make when restoring the quiz hash on a blocked Back.
// Live (Phase C): #/gauntlet #/codex #/interview/<sec>. #/debrief is implemented
// — see [D] Coach above (renderDebrief). Reserved for later phases: #/insights.
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

// [E2] mobile bottom tab bar: reflect the current route as the active tab.
function updateTabbar() {
  const bar = el("#tabbar");
  if (!bar) return;
  const h = location.hash || "#/home";
  const active = h.startsWith("#/study") ? "study" : h === "#/progress" ? "progress" : h.startsWith("#/quiz") || h.startsWith("#/reader") ? null : "home";
  bar.querySelectorAll(".tab").forEach((t) => {
    const on = t.dataset.tab === active;
    t.classList.toggle("active", on);
    t.setAttribute("aria-current", on ? "page" : "false");
  });
}

function onHashChange() {
  updateTabbar();                                    // [E2] keep the mobile tab bar in sync with the route
  if (_navLock) { _navLock = false; return; }       // swallow our own hash restore
  const route = location.hash || "#/home";
  const isReaderRoute = route.startsWith("#/reader/");

  // Reader is an overlay: browser Back onto the live underlayer just closes it
  // (the screen DOM is still mounted underneath). A code-driven jump to a
  // DIFFERENT route must fall through so the quiz guard + dispatch complete it
  // — returning early stranded the user on a stale screen with a new hash.
  if (document.body.classList.contains("reader-open") && !isReaderRoute) {
    closeReaderDom();
    if (route === (state.underHash || "#/home")) return;
  }
  // Leaving a live blitz: 0 answered -> leave silently; else restore the quiz
  // hash and raise the pause sheet with this route as the pending destination.
  if (state.inQuiz && !route.startsWith("#/quiz") && !isReaderRoute) {
    if (state.prime || answeredCount() === 0) { clearDeckSnapshot(); state.inQuiz = false; state.prime = false; }   // [A2] prime never blocks
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
  if (route === "#/debrief") { renderDebrief(); return; }   /* [D] Friday Debrief */
  renderHome();                                     // #/home and any unknown route
}

/* ---------- keyboard ---------- */
document.addEventListener("keydown", (e) => {
  const tag = (e.target.tagName || "").toLowerCase();
  const typing = tag === "input" || tag === "textarea";   // [A2] textarea: explain-back input
  // [A2] toggle explain-back with E (guarded against typing into its own textarea)
  const toggleEB = () => { const det = el(".explain-back"); if (det) { det.open = !det.open; if (det.open) det.querySelector(".eb-input")?.focus(); } };
  if (e.key === "Escape" && el("#helpOverlay")) { el("#helpOverlay")._close(); return; }
  if (e.key === "Escape" && el("#moreSheet")) { el("#moreSheet")._close(); return; }
  if (e.key === "Escape" && el("#pauseSheet")) { el("#pauseSheet")._close(); return; }
  // Any other key while a modal sheet is up must not fall through to the quiz
  // handler below — it would lock/grade the question hidden behind the overlay.
  if (el("#helpOverlay") || el("#pauseSheet") || el("#moreSheet") || el("#primeSheet")) return;
  // [E2] command palette: Cmd/Ctrl+K toggles anywhere; "/" opens when not typing.
  if ((e.key === "k" || e.key === "K") && (e.metaKey || e.ctrlKey)) { e.preventDefault(); _palette ? closePalette() : openPalette(); return; }
  if (e.key === "/" && !typing && !_palette) { e.preventDefault(); openPalette(); return; }
  if (e.key === "?" && !typing) { e.preventDefault(); toggleHelp(); return; }
  if (document.body.classList.contains("reader-open")) {
    if ((e.ctrlKey || e.metaKey) && (e.key === "f" || e.key === "F")) {   // find-in-page
      e.preventDefault();
      openReaderFind();
      return;
    }
    if (_find.open && e.key === "Escape") {          // close find before touching the reader
      e.preventDefault();
      closeReaderFind();
      return;
    }
    if (e.key === "Escape") {                        // exit immersive first, then close
      e.preventDefault();
      if (reader.full) setReaderFull(false);
      else closeReader();
      return;
    }
    if ((e.key === "f" || e.key === "F") && (e.target.tagName || "").toLowerCase() !== "input") {
      e.preventDefault();
      setReaderFull(!reader.full);
      return;
    }
    return;   // reader is open: never let keys drive the quiz hidden behind it
  }
  if (!state.inQuiz) return;
  if (e.repeat) return;   // holding a key must not auto-advance/grade a deck
  if (typing) return;
  if (state.mode === "flash") {
    if (!state.answered) {
      if (e.key === " " || e.key === "Enter") { e.preventDefault(); revealCard(); }
    } else if (e.key === "1") { e.preventDefault(); gradeCard(false, null); }      // Missed
    else if (e.key === "2") { e.preventDefault(); gradeCard(true, "low"); }         // Hard
    else if (e.key === "3" || e.key === "Enter") { e.preventDefault(); gradeCard(true, "high"); }  // Easy
    else if (e.key === "e" || e.key === "E") { e.preventDefault(); toggleEB(); }    // [A2]
    return;
  }
  const cur = state.deck[state.queue[state.cursor]];
  // [B] double-down: D accepts the one-time offer whenever it's live.
  if ((e.key === "d" || e.key === "D") && state._doubleDown === "offered") {
    e.preventDefault(); el("#ddYes")?.click(); return;
  }
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
    else if (e.key === "e" || e.key === "E") { e.preventDefault(); toggleEB(); }   // [A2]
    return;
  }
  if (/^[1-4]$/.test(e.key)) {
    const i = +e.key - 1;
    if (i < state.curOptsLen) { e.preventDefault(); answer(i); }
  } else if (e.key.toLowerCase() === "s") {
    if (cur && cur.status === "pending" && !cur.flip && !state.prime) { e.preventDefault(); skipQuestion(); }   // [A2] no skip on flips/prime
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
const prettyMod = (mod) => titleize(String(mod).split("/")[1] || String(mod));
function tierOf(section) {
  const t = sectionTier((state.progress.sections || {})[section]);
  return t ? t.toLowerCase() : null;               // null | bronze | silver | gold
}
function bumpDeepReads() {
  const p = state.progress;
  p.deepReads = (p.deepReads || 0) + 1;
  safeSet("sd_progress", JSON.stringify(p));   // persist now; deep_habit is detected at the next finish()
}

/* ---------- [C] codex model (100% derived from review records) ---------- */
// needed = 5 captures, or the module's whole bank when it holds fewer than 5.
function moduleNeeded(mod) {
  const sec = mod.split("/")[0], bank = bankCache[sec];
  if (bank) { const n = bank.filter((q) => q.module === mod).length; if (n && n < 5) return n; }
  return 5;
}
// Modules that can actually be captured: >=1 bank question. Reader-only dirs
// (nested lld pattern folders, the DDIA master index) are excluded. Falls back
// to "every file_tree module" when a cached index.json predates moduleCounts.
function capturableModules() {
  const files = (state.index && state.index.files) || {};
  const mc = state.index && state.index.moduleCounts;
  const all = Object.keys(files);
  return mc ? all.filter((m) => (mc[m] || 0) > 0) : all;
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
function saveGauntlet(g) { safeSet("sd_gauntlet", JSON.stringify(g)); }

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
  try {
    if (g && g.qids && g.qids.length) {
      questions = await questionsByIds(g.qids);    // frozen at first open: same qids all day
      // Some frozen qids failed to load (bank churn / partial fetch): run short
      // rather than bailing — the seal's identity is the day, not the count.
      if (questions.length && questions.length < g.qids.length)
        showToast("Some questions couldn't load — running a short gauntlet.");
    } else {
      questions = await buildGauntletDeck();
      g = { date: todayISO(), qids: questions.map((q) => q.id), sealed: false, score: null, attempt: [] };
      saveGauntlet(g);
    }
  } catch {
    errorScreen("Couldn't seal today's gauntlet", "The question banks didn't load. Check your connection and try again.", startGauntlet);
    return;
  }
  if (!questions.length) {
    errorScreen("Couldn't seal today's gauntlet", "The question banks didn't load. Check your connection and try again.", startGauntlet);
    return;
  }
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
  const order = STUDY_ORDER[sec] || [];
  return capturableModules().filter((k) => k.split("/")[0] === sec)
    .sort((a, b) => (order.indexOf(a) === -1 ? 9999 : order.indexOf(a)) - (order.indexOf(b) === -1 ? 9999 : order.indexOf(b)));
}
function renderCodex() {
  state.inQuiz = false;
  refreshStats();
  const cs = codexState(state.progress, capturableModules());
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
      const cardLbl = `${esc(prettyMod(mod))} — ${v.held}/${v.needed} held${v.foil ? " · foil" : ""}${v.tarnished ? " · fading" : ""}`;
      return `<button class="cx-card ${cls}" data-mod="${esc(mod)}" title="${cardLbl}" aria-label="${cardLbl}">
          <span class="cx-name">${esc(prettyMod(mod))}</span>
          <span class="cx-dots">${dots}</span>
        </button>`;
    }).join("");
    return `<div class="cx-shelf"><h2 class="section-h">${esc(label(sec))}</h2><div class="cx-grid">${cards}</div></div>`;
  }).join("");
  app.innerHTML = `
    <div class="hero"><h1>The Codex</h1>
      <p><b>${captured}</b>/${cs.size} captured &middot; <b>${foil}</b> foil</p>
      <div class="cx-legend">
        <span class="cx-legkey captured"><span class="cx-legdot"></span>captured</span>
        <span class="cx-legkey foil"><span class="cx-legdot"></span>foil</span>
        <span class="cx-legkey tarnished"><span class="cx-legdot"></span>fading</span>
        <span class="cx-legkey dim"><span class="cx-legdot"></span>unexplored</span>
      </div></div>
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
    `<svg viewBox="0 0 ${x + PAD} ${H}" width="100%" height="${H}" preserveAspectRatio="xMinYMax meet" role="img" aria-hidden="true">${buildings}</svg>` +
    `<p class="ins-cap">Skyline — one building per captured module; lit windows show current retention.</p></div>`;
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
  for (const sec of new Set(ctx.mods.map((m) => m.split("/")[0]))) {
    const secMods = capturableModules().filter((k) => k.split("/")[0] === sec);
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
  if (tier !== "silver" && tier !== "gold") {   // locked: path header shows the chip
    showToast(`Interview unlocks at Silver tier for ${label(section)}.`);
    redirect("#/study/" + section); return;
  }
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
  safeSet("sd_progress", JSON.stringify(p));   // persist awards written above
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
  /* additive: B's and A2's ?qa=1 handles merge into the same object below */
  Object.assign(window.__qa = window.__qa || {}, {
    state,
    progress() { return state.progress; },
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
  });
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
  b.setAttribute("aria-label", flash ? "Switch to multiple-choice mode" : "Switch to flashcards mode");
  b.setAttribute("aria-pressed", flash ? "true" : "false");
  b.classList.toggle("on", flash);
}

// [B] QA-only debug handle, gated behind ?qa=1 — never exposed otherwise. Lets
// an automated driver find the currently-correct option index (and other
// internals) without guessing from the shuffled DOM. See qa_phaseB.mjs.
if (new URLSearchParams(location.search).get("qa") === "1") {
  /* additive with the [C]/[A2] handles — NOTE: the `state` key is the state
     OBJECT (set by [C]/[A2]); use __qa.state directly, not __qa.state(). */
  Object.assign(window.__qa = window.__qa || {}, {
    correctIdx: () => { const it = state.deck[state.queue[state.cursor]]; return it ? it.opts.findIndex((o) => o.ok) : -1; },
  });
}

async function boot() {
  // [W5] perf: on Save-Data / data-saver connections, mark the root .low-power so
  // CSS drops the aurora drift animation (mesh visuals stay). Mirrors the other
  // navigator.connection.saveData guards below.
  if (navigator.connection && navigator.connection.saveData) document.documentElement.classList.add("low-power");
  state.index = await fetchJSON("questions/index.json", null);
  if (!state.index) {
    errorScreen("No question bank found", `Check your connection, or the question bank hasn't been built yet.${devDetail(`Run <code>python3 extract.py</code> then reload.`)}`, () => location.reload());
    return;
  }
  el("#bankInfo").textContent = `${state.index.total} questions across ${Object.keys(state.index.sections).length} sections`;
  state.progress = loadProgress();
  seedCoachMarksIfVeteran();                       // [E1] a non-empty history means every coach mark is pre-seen
  /* [D] coach: daily pick + message, computed once per boot */
  const todayPick = coachPick();
  const todayMsg = coachMessage(todayPick);
  state.today = { date: todayISO(), ...todayPick, message: todayMsg.text, templateId: todayMsg.templateId };
  const brandB = el("#brandHome");                 // brand logo -> home (guarded so a live blitz pauses first)
  if (brandB) {
    const goHome = () => guardedNav(() => go("#/home"));
    brandB.addEventListener("click", goHome);
    brandB.addEventListener("keydown", (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); goHome(); } });
  }
  el("#navProgress").addEventListener("click", () => guardedNav(() => go("#/progress")));
  const studyB = el("#navStudy");
  if (studyB) studyB.addEventListener("click", () => guardedNav(() => go("#/study")));
  // [E2] mobile bottom tab bar (shown only < 640px). Routes through guardedNav so
  // leaving a live blitz raises the pause sheet, same as the topbar nav.
  document.querySelectorAll("#tabbar .tab").forEach((t) => t.addEventListener("click", () => {
    const dest = t.dataset.tab === "study" ? "#/study" : t.dataset.tab === "progress" ? "#/progress" : "#/home";
    guardedNav(() => go(dest));
  }));
  const helpB = el("#helpBtn");
  if (helpB) helpB.addEventListener("click", toggleHelp);
  restoreReaderWidth();
  applyTheme(curTheme(), false);   // don't persist a ?theme= URL override
  const tb = el("#themeBtn");
  if (tb) tb.addEventListener("click", toggleThemePop);
  const skb = el("#streakChip");                    // [E1] streak detail popover
  if (skb) skb.addEventListener("click", toggleStreakPop);
  const mb = el("#muteBtn");
  if (mb) mb.addEventListener("click", () => { sfx.toggle(); syncMuteBtn(); });
  syncMuteBtn();
  const moreB = el("#moreBtn");                     // [W3] <=640px topbar overflow sheet
  if (moreB) moreB.addEventListener("click", openMoreSheet);
  const modeB = el("#modeBtn");
  if (modeB) {
    modeB.addEventListener("click", () => {
      safeSet("sd_mode", deckMode() === "flash" ? "quiz" : "flash");
      syncModeBtn();
      if (!state.inQuiz) renderHome();        // refresh the CTA caption
      // [E1] first-run coach mark (d): only on the switch INTO flashcards.
      if (deckMode() === "flash") coachMark("#modeBtn", "Flashcards: recall, then self-grade. Flat 10 XP, no combos or bosses.", "first_cards");
    });
  }
  syncModeBtn();
  // [A2] QA-only debug handle (guarded by ?qa=1): read state + call helpers headlessly.
  // Additive: merges with the [C] and [B] handles instead of clobbering them.
  if (new URLSearchParams(location.search).get("qa") === "1") {
    Object.assign(window.__qa = window.__qa || {}, { state, moduleStats, orderInterleaved, distractorSource, flipOptsFor, loadGraph, loadBank, bankById, bankCache });
    // [E1] additive QA surface for friction-kill features.
    Object.assign(window.__qa, { deckLen, loadRecent, errorScreen, showToast, coachMarkSeen: (id) => localStorage.getItem("sd_cm_" + id) === "1" });
    // [E2] additive QA surface: insights derivations + command palette + tab bar.
    Object.assign(window.__qa, { openPalette, closePalette, forecastData, leechIds, moduleAccStats, fuzzyScore, updateTabbar });
  }
  discardStaleDeck();                              // drop a previous-day resume snapshot
  registerServiceWorker();
  window.addEventListener("hashchange", onHashChange);
  // Normalize an empty hash to #/home (replace, not push) so the very first
  // history entry carries a real route and Back never lands on a hashless URL.
  if (!location.hash) history.replaceState(null, "", "#/home");
  onHashChange();                                  // dispatch the initial route
  // [W1] corrupt sd_progress detected at loadProgress: the raw copy was kept
  // under sd_progress_corrupt — tell the user once, after the first render.
  if (state._progressCorrupt) showToast("Saved progress couldn't be read — a copy was kept. Import a backup from Progress.", 9000);
  /* [D] coach: same-day streak nudge — check now (tab already visible at load)
     and again whenever the tab regains visibility (no OS scheduler on Pages). */
  document.addEventListener("visibilitychange", () => { if (document.visibilityState === "visible") maybeShowNudge(); });
  maybeShowNudge();
  // [E1] idle-time prefetch of today's suggested section (skip on Save-Data).
  if (state.today && state.today.section && !navigator.connection?.saveData) {
    const ric = window.requestIdleCallback || ((cb) => setTimeout(cb, 300));
    ric(() => prefetchBank(state.today.section));
  }
}

// PWA: register the offline shell/bank cache. Only in secure contexts (https or
// localhost); a file:// or plain-http origin has no serviceWorker and must not throw.
function registerServiceWorker() {
  const secure = location.protocol === "https:" ||
    ["localhost", "127.0.0.1"].includes(location.hostname);
  // APK: SW script fetches bypass the WebView's shouldInterceptRequest, so
  // registration rejects noisily — and every asset is already local, so skip it.
  if (!secure || IS_APK || !navigator.serviceWorker) return;
  try { navigator.serviceWorker.register("sw.js"); } catch { /* unsupported */ }
}

boot();
