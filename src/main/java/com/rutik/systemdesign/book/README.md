# Book Summaries — Read the Section, Skip the Book

<!-- study-paths
     THE source of truth for this section's file inventory and study-tier membership.
     Every module, every file it owns (module page AND deep-dive sub-files), and every
     case study is listed here with the tiers it belongs to; `-` means Full path only.
     Content files carry NO structural metadata -- a deep dive holds its topic, nothing
     else. Order is never declared here: it comes from STUDY_ORDER in game/app.js.
     The tier TABLES lower in this file are generated from this block by
     `python3 game/extract.py --write-paths`. A file on disk that is missing from this
     block, or listed here and absent from disk, FAILS `extract.py --strict`.
designing_data_intensive_applications/00_preface_and_book_map
  00_preface_and_book_map.md  -
designing_data_intensive_applications/01_reliable_scalable_maintainable
  01_reliable_scalable_maintainable.md  -
designing_data_intensive_applications/02_data_models_and_query_languages
  02_data_models_and_query_languages.md  -
designing_data_intensive_applications/03_storage_and_retrieval
  03_storage_and_retrieval.md  -
designing_data_intensive_applications/04_encoding_and_evolution
  04_encoding_and_evolution.md  -
designing_data_intensive_applications/05_replication
  05_replication.md  -
designing_data_intensive_applications/06_partitioning
  06_partitioning.md  -
designing_data_intensive_applications/07_transactions
  07_transactions.md  -
designing_data_intensive_applications/08_trouble_with_distributed_systems
  08_trouble_with_distributed_systems.md  -
designing_data_intensive_applications/09_consistency_and_consensus
  09_consistency_and_consensus.md  -
designing_data_intensive_applications/10_batch_processing
  10_batch_processing.md  -
designing_data_intensive_applications/11_stream_processing
  11_stream_processing.md  -
designing_data_intensive_applications/12_future_of_data_systems
  12_future_of_data_systems.md  -
system_design_interview_vol_1/01_scale_from_zero_to_millions_of_users
  01_scale_from_zero_to_millions_of_users.md  -
system_design_interview_vol_1/02_back_of_the_envelope_estimation
  02_back_of_the_envelope_estimation.md  -
system_design_interview_vol_1/03_a_framework_for_system_design_interviews
  03_a_framework_for_system_design_interviews.md  -
system_design_interview_vol_1/04_design_a_rate_limiter
  04_design_a_rate_limiter.md  -
system_design_interview_vol_1/05_design_consistent_hashing
  05_design_consistent_hashing.md  -
system_design_interview_vol_1/06_design_a_key_value_store
  06_design_a_key_value_store.md  -
system_design_interview_vol_1/07_design_a_unique_id_generator
  07_design_a_unique_id_generator.md  -
system_design_interview_vol_1/08_design_a_url_shortener
  08_design_a_url_shortener.md  -
system_design_interview_vol_1/09_design_a_web_crawler
  09_design_a_web_crawler.md  -
system_design_interview_vol_1/10_design_a_notification_system
  10_design_a_notification_system.md  -
system_design_interview_vol_1/11_design_a_news_feed_system
  11_design_a_news_feed_system.md  -
system_design_interview_vol_1/12_design_a_chat_system
  12_design_a_chat_system.md  -
system_design_interview_vol_1/13_design_a_search_autocomplete_system
  13_design_a_search_autocomplete_system.md  -
system_design_interview_vol_1/14_design_youtube
  14_design_youtube.md  -
system_design_interview_vol_1/15_design_google_drive
  15_design_google_drive.md  -
system_design_interview_vol_1/16_the_learning_continues
  16_the_learning_continues.md  -
system_design_interview_vol_2/01_proximity_service
  01_proximity_service.md  -
system_design_interview_vol_2/02_nearby_friends
  02_nearby_friends.md  -
system_design_interview_vol_2/03_google_maps
  03_google_maps.md  -
system_design_interview_vol_2/04_distributed_message_queue
  04_distributed_message_queue.md  -
system_design_interview_vol_2/05_metrics_monitoring_and_alerting
  05_metrics_monitoring_and_alerting.md  -
system_design_interview_vol_2/06_ad_click_event_aggregation
  06_ad_click_event_aggregation.md  -
system_design_interview_vol_2/07_hotel_reservation_system
  07_hotel_reservation_system.md  -
system_design_interview_vol_2/08_distributed_email_service
  08_distributed_email_service.md  -
system_design_interview_vol_2/09_s3_like_object_storage
  09_s3_like_object_storage.md  -
system_design_interview_vol_2/10_real_time_gaming_leaderboard
  10_real_time_gaming_leaderboard.md  -
system_design_interview_vol_2/11_payment_system
  11_payment_system.md  -
system_design_interview_vol_2/12_digital_wallet
  12_digital_wallet.md  -
system_design_interview_vol_2/13_stock_exchange
  13_stock_exchange.md  -
designing_machine_learning_systems/01_overview_of_machine_learning_systems
  01_overview_of_machine_learning_systems.md  -
designing_machine_learning_systems/02_introduction_to_machine_learning_systems_design
  02_introduction_to_machine_learning_systems_design.md  -
designing_machine_learning_systems/03_data_engineering_fundamentals
  03_data_engineering_fundamentals.md  -
designing_machine_learning_systems/04_training_data
  04_training_data.md  -
designing_machine_learning_systems/05_feature_engineering
  05_feature_engineering.md  -
designing_machine_learning_systems/06_model_development_and_offline_evaluation
  06_model_development_and_offline_evaluation.md  -
designing_machine_learning_systems/07_model_deployment_and_prediction_service
  07_model_deployment_and_prediction_service.md  -
designing_machine_learning_systems/08_data_distribution_shifts_and_monitoring
  08_data_distribution_shifts_and_monitoring.md  -
designing_machine_learning_systems/09_continual_learning_and_test_in_production
  09_continual_learning_and_test_in_production.md  -
designing_machine_learning_systems/10_infrastructure_and_tooling_for_mlops
  10_infrastructure_and_tooling_for_mlops.md  -
designing_machine_learning_systems/11_the_human_side_of_machine_learning
  11_the_human_side_of_machine_learning.md  -
machine_learning_system_design_interview/01_introduction_and_overview
  01_introduction_and_overview.md  -
machine_learning_system_design_interview/02_visual_search_system
  02_visual_search_system.md  -
machine_learning_system_design_interview/03_google_street_view_blurring_system
  03_google_street_view_blurring_system.md  -
machine_learning_system_design_interview/04_youtube_video_search
  04_youtube_video_search.md  -
machine_learning_system_design_interview/05_harmful_content_detection
  05_harmful_content_detection.md  -
machine_learning_system_design_interview/06_video_recommendation_system
  06_video_recommendation_system.md  -
machine_learning_system_design_interview/07_event_recommendation_system
  07_event_recommendation_system.md  -
machine_learning_system_design_interview/08_ad_click_prediction_on_social_platforms
  08_ad_click_prediction_on_social_platforms.md  -
machine_learning_system_design_interview/09_similar_listings_on_vacation_rental_platforms
  09_similar_listings_on_vacation_rental_platforms.md  -
machine_learning_system_design_interview/10_personalized_news_feed
  10_personalized_news_feed.md  -
machine_learning_system_design_interview/11_people_you_may_know
  11_people_you_may_know.md  -
understanding_distributed_systems/01_communication
  01_communication.md  -
understanding_distributed_systems/02_coordination
  02_coordination.md  -
understanding_distributed_systems/03_scalability
  03_scalability.md  -
understanding_distributed_systems/04_resiliency
  04_resiliency.md  -
understanding_distributed_systems/05_maintainability
  05_maintainability.md  -
ddia_2e/01_tradeoffs_in_data_systems_architecture
  01_tradeoffs_in_data_systems_architecture.md  -
ddia_2e/02_defining_nonfunctional_requirements
  02_defining_nonfunctional_requirements.md  -
ddia_2e/03_data_models_and_query_languages
  03_data_models_and_query_languages.md  -
ddia_2e/04_storage_and_retrieval
  04_storage_and_retrieval.md  -
ddia_2e/05_encoding_and_evolution
  05_encoding_and_evolution.md  -
ddia_2e/06_replication
  06_replication.md  -
ddia_2e/07_sharding
  07_sharding.md  -
ddia_2e/08_transactions
  08_transactions.md  -
ddia_2e/09_trouble_with_distributed_systems
  09_trouble_with_distributed_systems.md  -
ddia_2e/10_consistency_and_consensus
  10_consistency_and_consensus.md  -
ddia_2e/11_batch_processing
  11_batch_processing.md  -
ddia_2e/12_stream_processing
  12_stream_processing.md  -
ddia_2e/13_philosophy_of_streaming_systems
  13_philosophy_of_streaming_systems.md  -
ddia_2e/14_doing_the_right_thing
  14_doing_the_right_thing.md  -
the_staff_engineers_path/01_what_would_you_say_you_do_here
  01_what_would_you_say_you_do_here.md  -
the_staff_engineers_path/02_three_maps
  02_three_maps.md  -
the_staff_engineers_path/03_creating_the_big_picture
  03_creating_the_big_picture.md  -
the_staff_engineers_path/04_finite_time
  04_finite_time.md  -
the_staff_engineers_path/05_leading_big_projects
  05_leading_big_projects.md  -
the_staff_engineers_path/06_why_have_we_stopped
  06_why_have_we_stopped.md  -
the_staff_engineers_path/07_youre_a_role_model_now
  07_youre_a_role_model_now.md  -
the_staff_engineers_path/08_good_influence_at_scale
  08_good_influence_at_scale.md  -
the_staff_engineers_path/09_whats_next
  09_whats_next.md  -
staff_engineer_larson/01_overview
  01_overview.md  -
staff_engineer_larson/02_operating_at_staff
  02_operating_at_staff.md  -
staff_engineer_larson/03_getting_the_title_where_you_are
  03_getting_the_title_where_you_are.md  -
staff_engineer_larson/04_switching_companies
  04_switching_companies.md  -
staff_engineer_larson/05_stories_and_resources
  05_stories_and_resources.md  -
database_internals/01_introduction_and_overview
  01_introduction_and_overview.md  -
database_internals/02_btree_basics
  02_btree_basics.md  -
database_internals/03_file_formats
  03_file_formats.md  -
database_internals/04_implementing_btrees
  04_implementing_btrees.md  -
database_internals/05_transaction_processing_and_recovery
  05_transaction_processing_and_recovery.md  -
database_internals/06_btree_variants
  06_btree_variants.md  -
database_internals/07_log_structured_storage
  07_log_structured_storage.md  -
database_internals/08_distributed_systems_introduction
  08_distributed_systems_introduction.md  -
database_internals/09_failure_detection
  09_failure_detection.md  -
database_internals/10_leader_election
  10_leader_election.md  -
database_internals/11_replication_and_consistency
  11_replication_and_consistency.md  -
database_internals/12_anti_entropy_and_dissemination
  12_anti_entropy_and_dissemination.md  -
database_internals/13_distributed_transactions
  13_distributed_transactions.md  -
database_internals/14_consensus
  14_consensus.md  -
site_reliability_engineering/01_introduction
  01_introduction.md  -
site_reliability_engineering/03_embracing_risk
  03_embracing_risk.md  -
site_reliability_engineering/04_service_level_objectives
  04_service_level_objectives.md  -
site_reliability_engineering/05_eliminating_toil
  05_eliminating_toil.md  -
site_reliability_engineering/06_monitoring_distributed_systems
  06_monitoring_distributed_systems.md  -
site_reliability_engineering/08_release_engineering
  08_release_engineering.md  -
site_reliability_engineering/09_simplicity
  09_simplicity.md  -
site_reliability_engineering/11_being_on_call
  11_being_on_call.md  -
site_reliability_engineering/14_managing_incidents
  14_managing_incidents.md  -
site_reliability_engineering/15_postmortem_culture
  15_postmortem_culture.md  -
site_reliability_engineering/22_addressing_cascading_failures
  22_addressing_cascading_failures.md  -
building_microservices/01_what_are_microservices
  01_what_are_microservices.md  -
building_microservices/02_how_to_model_microservices
  02_how_to_model_microservices.md  -
building_microservices/03_splitting_the_monolith
  03_splitting_the_monolith.md  -
building_microservices/04_microservice_communication_styles
  04_microservice_communication_styles.md  -
building_microservices/05_implementing_microservice_communication
  05_implementing_microservice_communication.md  -
building_microservices/06_workflow
  06_workflow.md  -
building_microservices/07_build
  07_build.md  -
building_microservices/08_deployment
  08_deployment.md  -
building_microservices/09_testing
  09_testing.md  -
building_microservices/10_from_monitoring_to_observability
  10_from_monitoring_to_observability.md  -
building_microservices/11_security
  11_security.md  -
building_microservices/12_resiliency
  12_resiliency.md  -
building_microservices/13_scaling
  13_scaling.md  -
building_microservices/14_user_interfaces
  14_user_interfaces.md  -
building_microservices/15_organizational_structures
  15_organizational_structures.md  -
building_microservices/16_the_evolutionary_architect
  16_the_evolutionary_architect.md  -
ai_engineering/01_introduction_to_building_ai_applications
  01_introduction_to_building_ai_applications.md  -
ai_engineering/02_understanding_foundation_models
  02_understanding_foundation_models.md  -
ai_engineering/03_evaluation_methodology
  03_evaluation_methodology.md  -
ai_engineering/04_evaluate_ai_systems
  04_evaluate_ai_systems.md  -
ai_engineering/05_prompt_engineering
  05_prompt_engineering.md  -
ai_engineering/06_rag_and_agents
  06_rag_and_agents.md  -
ai_engineering/07_finetuning
  07_finetuning.md  -
ai_engineering/08_dataset_engineering
  08_dataset_engineering.md  -
ai_engineering/09_inference_optimization
  09_inference_optimization.md  -
ai_engineering/10_ai_engineering_architecture_and_user_feedback
  10_ai_engineering_architecture_and_user_feedback.md  -
generative_ai_system_design_interview/01_introduction_and_overview
  01_introduction_and_overview.md  -
generative_ai_system_design_interview/02_gmail_smart_compose
  02_gmail_smart_compose.md  -
generative_ai_system_design_interview/03_google_translate
  03_google_translate.md  -
generative_ai_system_design_interview/04_chatgpt_personal_assistant_chatbot
  04_chatgpt_personal_assistant_chatbot.md  -
generative_ai_system_design_interview/05_image_captioning
  05_image_captioning.md  -
generative_ai_system_design_interview/06_retrieval_augmented_generation
  06_retrieval_augmented_generation.md  -
generative_ai_system_design_interview/07_realistic_face_generation
  07_realistic_face_generation.md  -
generative_ai_system_design_interview/08_high_resolution_image_synthesis
  08_high_resolution_image_synthesis.md  -
generative_ai_system_design_interview/09_text_to_image_generation
  09_text_to_image_generation.md  -
generative_ai_system_design_interview/10_personalized_headshot_generation
  10_personalized_headshot_generation.md  -
generative_ai_system_design_interview/11_text_to_video_generation
  11_text_to_video_generation.md  -
-->
> In-depth, chapter-by-chapter summaries of foundational engineering books — written so
> that reading the summary is as close as possible to reading the book itself, then mapped
> back into the deep-dive modules elsewhere in this repository.

> **PERMANENTLY EXCLUDED from the repo-wide factual-currency audit — not parked, and the
> exclusion does not expire (owner-set 2026-07-28, reaffirmed 2026-08-04).** `devops/`,
> `cs_fundamentals/` and `cuda/` were parked and later unparked; this section was not parked.
> A book summary is answerable only to its source, so "is this claim current today" is the
> wrong test for it: "correcting" a 2017 DDIA claim to 2026 reality makes the summary *wrong
> about the book*, and REPLACE-DO-NOT-ANNOTATE inverts into fabrication attributed to a named
> author. The test that *does* apply here is **fidelity** — does the book actually say this,
> is the chapter attribution right, is the summarizer's own material disclosed as such. See
> [CLAUDE.md](CLAUDE.md) and the `## Beyond the book` rule it mandates.
>
> The `**Short:**` MCQ-summary migration is a **separate, fidelity-neutral** question and was
> answered separately: a targeted pass over the ~310 Q&As whose derived option ships broken
> was run on 2026-08-25. The remaining ~1,090 derive a perfectly good option and are
> deliberately left unmigrated — a blanket 1,402-line migration is not justified here.

---

## What This Section Is

Most sections in this repo are organized by *topic* (caching, replication, indexing). This
section is organized by *book*. Each book gets its own folder, and inside it every chapter
gets its own in-depth write-up that **follows the book's own narrative and section order**.

The goal is twofold:

1. **Standalone:** you can read a book's folder cover-to-cover and walk away with the
   author's full argument — every concept, example, tradeoff, and pitfall — without owning
   the book.
2. **A map back into the repo:** every chapter ends with cross-links into the deeper
   "how to build it" modules (`database/`, `hld/`, `backend/`, `devops/`) so you can drill
   from the book's framing into production-grade detail.

This is deliberately different from the topic sections: it preserves *the author's lens*.
Kleppmann's chapter on transactions teaches isolation levels through anomalies (lost
update, write skew, phantoms); our `database/concurrency_control_and_locking/` module
teaches the same locks through PostgreSQL internals. Both are valuable; this section is the
former.

---

## Books

| Book | Author | Folder | Chapters | Status |
|------|--------|--------|----------|--------|
| Designing Data-Intensive Applications **(1st ed., 2017)** | Martin Kleppmann | [designing_data_intensive_applications/](designing_data_intensive_applications/designing_data_intensive_applications.md) | 12 (+ preface) | Complete |
| Designing Data-Intensive Applications **(2nd ed., 2026)** | Martin Kleppmann & Chris Riccomini | [ddia_2e/](ddia_2e/ddia_2e.md) | 14 | Complete |
| The Staff Engineer's Path | Tanya Reilly | [the_staff_engineers_path/](the_staff_engineers_path/the_staff_engineers_path.md) | 9 | Complete |
| Staff Engineer: Leadership Beyond the Management Track | Will Larson | [staff_engineer_larson/](staff_engineer_larson/staff_engineer_larson.md) | 5 parts (6 book sections) | Complete |
| Database Internals | Alex Petrov | [database_internals/](database_internals/database_internals.md) | 14 | Complete |
| Site Reliability Engineering | Google (Beyer, Jones, Petoff & Murphy, eds.) | [site_reliability_engineering/](site_reliability_engineering/site_reliability_engineering.md) | 11 of 34 (selected) | Complete |
| Building Microservices **(2nd ed., 2021)** | Sam Newman | [building_microservices/](building_microservices/building_microservices.md) | 16 | Complete |
| AI Engineering | Chip Huyen | [ai_engineering/](ai_engineering/ai_engineering.md) | 10 | Complete |
| Generative AI System Design Interview | Ali Aminian & Hao Sheng | [generative_ai_system_design_interview/](generative_ai_system_design_interview/generative_ai_system_design_interview.md) | 11 | Complete |
| System Design Interview — Vol 1 | Alex Xu | [system_design_interview_vol_1/](system_design_interview_vol_1/system_design_interview_vol_1.md) | 16 | Complete |
| System Design Interview — Vol 2 | Alex Xu & Sahn Lam | [system_design_interview_vol_2/](system_design_interview_vol_2/system_design_interview_vol_2.md) | 13 | Complete |
| Machine Learning System Design Interview | Ali Aminian & Alex Xu | [machine_learning_system_design_interview/](machine_learning_system_design_interview/machine_learning_system_design_interview.md) | 11 | Complete |
| Designing Machine Learning Systems | Chip Huyen | [designing_machine_learning_systems/](designing_machine_learning_systems/designing_machine_learning_systems.md) | 11 | Complete |
| Understanding Distributed Systems | Roberto Vitillo | [understanding_distributed_systems/](understanding_distributed_systems/understanding_distributed_systems.md) | 33 (as 5 parts) | Complete |

The two *System Design Interview* volumes are one series split across two folders (each
volume numbers its chapters independently); with both DDIA editions, MLSDI, DMLS, UDS and the seven added in 2026-08 the section
covers **fourteen books across fourteen folders**. More may be added over time (e.g. *Database
Internals*, *Streaming Systems*). New books follow the folder-per-chapter convention —
with one sanctioned exception: **UDS uses folder-per-PART** (its ~33 chapters are 3–8
pages each; a part is the multi-concept narrative the chapter template was designed for) —
see [CLAUDE.md](CLAUDE.md).

**Every book folder states which edition it summarizes, and that is not optional.** A
summary that does not identify its source edition is unfalsifiable. It became load-bearing
for DDIA in March 2026, when the **2nd edition** (Kleppmann & Riccomini) shipped with a
different chapter structure — 14 chapters against 1e's 12 — so a 2026 reader following a
`## 7.3` heading against a 2e copy lands in the wrong chapter. **This folder summarizes the
1st edition (O'Reilly, 2017)** and every one of its 14 files now says so in its byline. The
1e claims are deliberately left as 1e claims; covering 2e is a separate decision (a
chapter-mapped 1e→2e delta page, or a sibling `..._2e/` folder) and is not this folder's
job. UDS likewise states "(2nd ed.)" throughout.

**In the learning game**, the book section gets an extra navigation level: the Study view
shows **one node per book** (`#/study/book`), and clicking a book opens that book's own
chapter path; the reader's module sidebar likewise groups chapters under collapsible
per-book headers. Adding a book = a `BOOK_LABELS` entry + `STUDY_ORDER.book` entries in
`game/app.js` (see [game/CLAUDE.md](../game/CLAUDE.md)).

---

## How to Read a Book Folder

Open the book's own index page first — `<book_slug>/<book_slug>.md`, e.g.
[designing_data_intensive_applications/designing_data_intensive_applications.md](designing_data_intensive_applications/designing_data_intensive_applications.md).
**There is no `README.md` in any book folder**; the 2026-07-30 rename made every content
page named for the folder that contains it, and `book/README.md` is the only README in the
section. The index carries the book's thesis, a part map, a chapter
table, and a recommended reading path. Then read chapters in order — each one opens with a
**Chapter Map** (where it sits, what it builds on) and a **Big Question** (the problem it
exists to answer), so you always know why you're reading it.

---

## Interview-Priority Reading Order

`book/` IS wired into the learning game: every chapter folder is a Study topic, ordered by
the `STUDY_ORDER.book` array in `game/app.js` — all fourteen books, in `STUDY_ORDER.book` order — the seven originals
(**DDIA 1e → DDIA 2e → SDI Vol 1 → SDI Vol 2 → DMLS → MLSDI → UDS**) followed by
**The Staff Engineer's Path → Staff Engineer → Database Internals → Google SRE →
Building Microservices → AI Engineering → GenAI System Design Interview**. The
section is **Full-only** — it has no `STUDY_PATHS` interview subset, because a
chapter-by-chapter book summary has no meaningful "interview cut"; instead, use these
prose priorities:

- **DDIA:** prioritize **Replication**, **Partitioning**, **Transactions**,
  **Consistency & Consensus**, and **The Trouble with Distributed Systems** over a linear
  front-to-back read; the remaining chapters are context.
- **SDI Vol 1:** Ch 3 (framework) first, then Ch 4–6 (rate limiter, consistent hashing,
  key-value store) — the primitives every other design question borrows.
- **SDI Vol 2:** pick the track matching the role — geo (Ch 1–3), data infra (Ch 4–6, 9),
  or fintech (Ch 7, 11–13).
- **DMLS:** Ch 2, 4, 5, 7, 8, 9 map 1:1 onto ML system design interview rounds.
- **MLSDI:** Ch 1 (framework) + Ch 6 (two-stage recsys) + Ch 8 (CTR) are the three
  most-asked ML design questions; the recsys arc (6 → 8 → 10 → 7 → 9 → 11) covers the rest.
- **UDS:** Parts II–IV (coordination, scalability, resiliency) are the interview core;
  Part I is the networking on-ramp, Part V the ops/SRE closer.

---

## Cross-Reference Map

| When the book discusses… | Drill deeper in… |
|--------------------------|------------------|
| Replication, leaders/followers, quorums | [database/replication_and_high_availability/](../database/replication_and_high_availability/replication_and_high_availability.md) |
| Partitioning / sharding | [database/sharding_and_partitioning/](../database/sharding_and_partitioning/sharding_and_partitioning.md) |
| Transactions, isolation, MVCC | [database/concurrency_control_and_locking/](../database/concurrency_control_and_locking/concurrency_control_and_locking.md) |
| Storage engines (B-tree / LSM) | [database/storage_engines_internals/](../database/storage_engines_internals/storage_engines_internals.md) |
| Consensus, linearizability | [database/consistency_models_and_consensus/](../database/consistency_models_and_consensus/consistency_models_and_consensus.md) |
| Distributed-system theory (CAP, etc.) | [hld/](../hld/README.md) |
| Streaming / messaging / Kafka | [backend/](../backend/README.md), [devops/](../devops/README.md) |

---

## Related Sections

- [Database Engineering](../database/README.md) — production-depth on storage, replication, sharding, transactions
- [High-Level Design](../hld/README.md) — distributed-systems concepts and the interview framework
- [Backend Engineering](../backend/README.md) — networking, messaging, microservices, resilience
