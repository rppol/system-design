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
-->
> In-depth, chapter-by-chapter summaries of foundational engineering books — written so
> that reading the summary is as close as possible to reading the book itself, then mapped
> back into the deep-dive modules elsewhere in this repository.

> **PARKED — to be done later (owner-set 2026-07-28).** This section is out of scope for the
> repo-wide factual audit and the `**Short:**` MCQ-summary migration until the owner
> re-opens it. **Parked, not cancelled**: all 6 books are complete, pushed, and still ship in
> the game. Paused: **1,402 Q&As awaiting one-line MCQ summaries**. The audit exclusion is
> permanent rather than temporary — a book summary is faithful to its source, so "is this
> claim current today" is the wrong test for it. Scope table: root `CLAUDE.md` ->
> "Deferred / To Be Planned".

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
| Designing Data-Intensive Applications | Martin Kleppmann | [designing_data_intensive_applications/](designing_data_intensive_applications/designing_data_intensive_applications.md) | 12 (+ preface) | Complete |
| System Design Interview — Vol 1 | Alex Xu | [system_design_interview_vol_1/](system_design_interview_vol_1/system_design_interview_vol_1.md) | 16 | Complete |
| System Design Interview — Vol 2 | Alex Xu & Sahn Lam | [system_design_interview_vol_2/](system_design_interview_vol_2/system_design_interview_vol_2.md) | 13 | Complete |
| Machine Learning System Design Interview | Ali Aminian & Alex Xu | [machine_learning_system_design_interview/](machine_learning_system_design_interview/machine_learning_system_design_interview.md) | 11 | Complete |
| Designing Machine Learning Systems | Chip Huyen | [designing_machine_learning_systems/](designing_machine_learning_systems/designing_machine_learning_systems.md) | 11 | Complete |
| Understanding Distributed Systems | Roberto Vitillo | [understanding_distributed_systems/](understanding_distributed_systems/understanding_distributed_systems.md) | 33 (as 5 parts) | Complete |

The two *System Design Interview* volumes are one series split across two folders (each
volume numbers its chapters independently); with DDIA, MLSDI, DMLS, and UDS the section
covers five books across six folders. More may be added over time (e.g. *Database
Internals*, *Streaming Systems*). New books follow the folder-per-chapter convention —
with one sanctioned exception: **UDS uses folder-per-PART** (its ~33 chapters are 3–8
pages each; a part is the multi-concept narrative the chapter template was designed for) —
see [CLAUDE.md](CLAUDE.md).

**In the learning game**, the book section gets an extra navigation level: the Study view
shows **one node per book** (`#/study/book`), and clicking a book opens that book's own
chapter path; the reader's module sidebar likewise groups chapters under collapsible
per-book headers. Adding a book = a `BOOK_LABELS` entry + `STUDY_ORDER.book` entries in
`game/app.js` (see [game/CLAUDE.md](../game/CLAUDE.md)).

---

## How to Read a Book Folder

Open the book's `README.md` first: it carries the book's thesis, a part map, a chapter
table, and a recommended reading path. Then read chapters in order — each one opens with a
**Chapter Map** (where it sits, what it builds on) and a **Big Question** (the problem it
exists to answer), so you always know why you're reading it.

---

## Interview-Priority Reading Order

`book/` IS wired into the learning game: every chapter folder is a Study topic, ordered by
the `STUDY_ORDER.book` array in `game/app.js` (DDIA → SDI Vol 1 → SDI Vol 2 → DMLS). The
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
| Streaming / messaging / Kafka | [backend/](../backend/CLAUDE.md), [devops/](../devops/README.md) |

---

## Related Sections

- [Database Engineering](../database/README.md) — production-depth on storage, replication, sharding, transactions
- [High-Level Design](../hld/README.md) — distributed-systems concepts and the interview framework
- [Backend Engineering](../backend/CLAUDE.md) — networking, messaging, microservices, resilience
