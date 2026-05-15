# Case Study: Design an LLM Fine-Tuning Platform

## Intuition

> **Design intuition**: An LLM fine-tuning platform is a GPU-orchestrated training pipeline with a self-serve data management layer -- like a CI/CD system for model weights instead of application code. Users push data, configure training jobs, watch metrics converge in real time, evaluate results against baselines, and promote models through staging to production. The platform abstracts GPU cluster complexity behind a simple API.

**Key insight for this design**: The data pipeline is the quality bottleneck, not the training loop. A fine-tune on garbage data produces a garbage model regardless of hyperparameters. The platform must invest heavily in data validation (format correctness, quality scoring, PII detection, deduplication) before a single GPU cycle is spent. Second, LoRA is the default -- full fine-tuning is the exception for <5% of jobs, reserved for users who demonstrate they have enough data (>100K examples) and budget (>$10K per run).

---

## 1. Requirements Clarification

### Functional Requirements
- Self-serve upload of training datasets (CSV, JSONL) up to 50GB per file
- Data validation: schema checks, quality scoring (perplexity, diversity), PII detection, deduplication
- Training job configuration: model selection, LoRA vs full fine-tune, hyperparameter presets
- Real-time training monitoring: loss curves, eval metrics, ETA, GPU utilization
- Automated evaluation: before/after comparison on golden datasets, LLM-as-judge scoring
- Model registry: versioned model artifacts with full lineage (data hash, config, eval scores)
- Promotion workflow: staging -> production with approval gates
- Inference endpoint provisioning for deployed fine-tuned models
- Cost estimation before job submission; per-job cost tracking and billing

### Non-Functional Requirements
- **Multi-tenancy**: 2,000 enterprise tenants with isolated data, models, and GPU quotas
- **Concurrent jobs**: 500 training jobs running simultaneously across the GPU cluster
- **GPU utilization target**: >85% cluster utilization (GPUs cost $2-3/hour each; idle GPUs burn money)
- **Job start latency**: <5 minutes from submission to first training step (queue time + setup)
- **Data pipeline throughput**: Process 1TB of uploaded data per hour across all tenants
- **Model storage**: 200TB total (fine-tuned adapters average 500MB for LoRA, 30GB for full models)
- **Availability**: 99.9% for API and dashboard; training jobs tolerant to node failures via checkpointing
- **Compliance**: SOC 2, GDPR (data deletion on request), optional HIPAA for healthcare tenants

### Out of Scope
- Base model pre-training (platform only fine-tunes existing base models)
- Custom model architecture design
- Inference auto-scaling (handled by separate serving platform)
- Synthetic data generation (available as a separate service, can feed into this platform)

---

## 2. Scale Estimation

### Training Job Volume
```
Active tenants: 2,000
Jobs per tenant per month: 10 average (power users: 50+, casual: 2-3)
Total jobs per month: 20,000
Concurrent running jobs (peak): 500
Average job duration: 4 hours (LoRA), 48 hours (full fine-tune)
Job mix: 92% LoRA, 5% QLoRA, 3% full fine-tune

GPU requirements per job:
  LoRA (7B model): 1x A100 80GB
  LoRA (70B model): 4x A100 80GB (tensor parallelism)
  QLoRA (70B model): 1x A100 80GB (4-bit quantized base)
  Full fine-tune (7B): 8x A100 80GB (FSDP)
  Full fine-tune (70B): 32x A100 80GB (FSDP + tensor parallelism)

Peak GPU demand: ~1,200 A100 GPUs
  (460 LoRA jobs x 1.5 avg GPUs + 25 QLoRA x 1 + 15 full x 12 avg)
Cluster size: 1,600 A100 GPUs (25% headroom for scheduling + failures)
```

### Data Volume
```
Dataset uploads per day: ~700
Average dataset size: 200MB (JSONL with prompt-completion pairs)
Daily upload volume: 700 x 200MB = 140GB/day
Peak upload rate: 50 concurrent uploads

Validation pipeline:
  PII scan throughput: 10GB/hour per worker
  Quality scoring: 5GB/hour per worker (requires embedding + perplexity computation)
  Workers needed at peak: 20 validation workers

Storage:
  Raw datasets (90-day retention): 140GB/day x 90 = 12.6TB
  Processed datasets (tokenized, ready for training): ~2x raw = 25TB
  Model artifacts (LoRA adapters): 20,000 jobs/month x 500MB avg = 10TB/month
  Model artifacts (full models): 600 jobs/month x 30GB = 18TB/month
  Total model storage (12-month retention): 336TB
  Checkpoints (7-day retention): 500 concurrent x 2GB avg = 1TB active
```

### Cost Baseline
```
GPU cluster: 1,600 A100s x $2.50/hr = $4,000/hr = $2.88M/month
Storage (S3): 400TB x $0.023/GB = $9,200/month
Compute (validation, API, orchestration): ~$50,000/month
Networking (data transfer): ~$15,000/month
Total infrastructure: ~$3M/month

Revenue target: charge 2-3x infrastructure cost
  LoRA job (7B, 4 hours, 1 GPU): $10 GPU + $2 platform fee = $12
  LoRA job (70B, 8 hours, 4 GPUs): $80 GPU + $10 platform fee = $90
  Full fine-tune (7B, 48 hours, 8 GPUs): $960 GPU + $100 platform fee = $1,060
```

---

## 3. High-Level Architecture

```
                            User (Dashboard / CLI / API)
                                       |
                                       v
                              [API Gateway + Auth]
                              (rate limit, tenant ID)
                                       |
                    +------------------+-------------------+
                    |                  |                   |
                    v                  v                   v
           [Data Service]     [Training Service]   [Model Registry]
                    |                  |                   |
                    v                  v                   v
         +-------------------+  +-------------------+  +------------------+
         | Upload Handler    |  | Job Scheduler     |  | Artifact Store   |
         | Format Validator  |  | GPU Allocator     |  | (S3 + metadata)  |
         | PII Scanner       |  | Checkpoint Mgr    |  | Version Control  |
         | Quality Scorer    |  | Metrics Collector  |  | Promotion Gates  |
         | Deduplicator      |  | Cost Tracker      |  | Lineage Tracker  |
         | Tokenizer         |  +-------------------+  +------------------+
         +-------------------+         |
                    |                  v
                    |         [GPU Cluster]
                    |         +---------------------------------+
                    |         | Node 1: 8x A100 80GB            |
                    |         | Node 2: 8x A100 80GB            |
                    |         | ...                             |
                    |         | Node 200: 8x A100 80GB          |
                    |         | (1,600 GPUs total)              |
                    |         | Orchestrator: Kubernetes + SLURM|
                    |         +---------------------------------+
                    |                  |
                    v                  v
            [Object Store (S3)]   [Metrics Store]
            - Raw datasets        - Training loss
            - Tokenized data      - Eval metrics
            - LoRA adapters       - GPU utilization
            - Full model weights  - Cost per job
            - Checkpoints
                    |                  |
                    +--------+---------+
                             |
                             v
                    [Evaluation Service]
                    - Golden dataset comparison
                    - LLM-as-judge scoring
                    - Regression detection
                    - Before/after reports
                             |
                             v
                    [Deployment Service]
                    - Provision inference endpoint
                    - Load LoRA adapter onto base model
                    - Health check + smoke test
                    - Traffic shifting (canary)
```

---

## 4. Component Deep Dives

### 4.1 Data Pipeline

```
Upload flow (user perspective):
  POST /v1/datasets with multipart JSONL file
     |
     v
  [Upload Handler]
  - Presigned S3 URL for large files (>100MB bypass API gateway)
  - Chunked upload with resumption (tus.io protocol)
  - Max file size: 50GB
  - Accepted formats: JSONL (primary), CSV (converted to JSONL)
     |
     v
  [Format Validator] (Stage 1: fast, <30 seconds for 1GB)
  - Schema check: every line valid JSON
  - Required fields: "messages" array with role/content pairs
  - Role validation: system (optional), user (required), assistant (required)
  - Token count per example: warn if >4096, reject if >model context length
  - Minimum examples: 10 (LoRA), 100 (recommended), 100K (full fine-tune)
  - Maximum examples: 10M
  - Encoding: UTF-8 only, reject binary content
     |
     v
  [PII Scanner] (Stage 2: ~10GB/hour per worker)
  - Named Entity Recognition (NER) for: SSN, credit card, phone, email, address
  - Regex patterns for structured PII (SSN: XXX-XX-XXXX, CC: 16-digit patterns)
  - Configurable: block (reject dataset), redact (replace with [REDACTED]), warn (flag only)
  - HIPAA mode: scan for PHI (Protected Health Information) -- medical record numbers,
    diagnosis codes, patient names cross-referenced with medical terms
  - Output: PII report with line numbers and entity types found
     |
     v
  [Quality Scorer] (Stage 3: ~5GB/hour per worker)
  - Perplexity check: compute perplexity of assistant responses against base model
    If perplexity is extremely low (<2): likely copied from base model (no learning signal)
    If perplexity is extremely high (>50): likely garbage or wrong language
  - Diversity analysis: embedding-based clustering of prompts
    Flag if >30% of prompts cluster within cosine similarity 0.95 (near-duplicates)
  - Length analysis: histogram of prompt/response lengths
    Flag if >50% of responses are <20 tokens (too short to be useful)
  - Instruction-following check: sample 100 examples, use LLM-as-judge to score
    whether assistant response actually follows the user instruction (1-5 scale)
    Flag if average score <3.0
  - Output: quality report with overall score (A/B/C/D/F) and per-metric breakdown
     |
     v
  [Deduplicator] (Stage 4)
  - Exact match: SHA-256 hash of (user_message + assistant_message)
  - Near-duplicate: MinHash with Jaccard similarity >0.85
  - Cross-dataset: check against tenant's other datasets (optional)
  - Action: remove duplicates, report count
     |
     v
  [Tokenizer] (Stage 5: final preparation)
  - Apply prompt template for target model (ChatML, Llama, Mistral format)
  - Tokenize with model-specific tokenizer
  - Pack examples into training sequences (bin-packing for efficiency)
  - Shuffle and split: 90% train, 10% validation (or user-specified split)
  - Write tokenized dataset to S3 in optimized format (memory-mapped Arrow)
  - Output: tokenized dataset path + statistics (total tokens, sequence count)

Validation dataset record in PostgreSQL:
  {
    dataset_id: uuid,
    tenant_id: uuid,
    name: "customer_support_v3",
    status: "validated",             // uploading | validating | validated | failed
    raw_path: "s3://datasets/raw/{tenant}/{dataset_id}.jsonl",
    processed_path: "s3://datasets/processed/{tenant}/{dataset_id}/",
    format: "jsonl",
    example_count: 15420,
    total_tokens: 8_234_000,
    quality_score: "B+",
    pii_report: {ssn: 0, email: 12, phone: 3},
    pii_action: "redacted",
    duplicate_count: 234,
    created_at: timestamp,
    expires_at: timestamp            // 90-day retention default
  }
```

### 4.2 Training Orchestration

```
Job submission flow:
  POST /v1/training/jobs
  {
    "dataset_id": "ds_abc123",
    "base_model": "meta-llama/Llama-3-70B-Instruct",
    "method": "lora",               // lora | qlora | full
    "hyperparameters": "preset:quality",  // preset or custom
    "epochs": 3,
    "eval_dataset_id": "ds_eval456"  // optional golden dataset
  }
     |
     v
  [Pre-flight Checks]
  - Dataset exists and status = "validated"
  - Base model available in model catalog
  - Tenant has sufficient GPU quota remaining
  - Cost estimate generated and within tenant budget
     |
     v
  [Cost Estimator]
  Estimated cost = GPU_count x estimated_hours x GPU_rate + platform_fee
  Calculation:
    total_tokens = dataset.total_tokens x epochs
    tokens_per_second = model_benchmark[base_model][method]
      (Llama-3-70B LoRA: ~8,000 tokens/sec on 4x A100)
    estimated_seconds = total_tokens / tokens_per_second
    estimated_hours = estimated_seconds / 3600
    GPU_count = hardware_requirements[base_model][method]
    estimated_cost = GPU_count x estimated_hours x $2.50 + platform_fee
  Return estimate to user before charging
     |
     v
  [Job Queue] (Redis sorted set, priority = submission_time + tier_boost)
  Priority tiers:
    Enterprise tier: +0 (highest priority, submitted time is effective priority)
    Pro tier: +300 seconds penalty
    Free tier: +1800 seconds penalty (30-minute delay)
  Fair scheduling:
    Max concurrent jobs per tenant: 20 (enterprise), 5 (pro), 1 (free)
    If tenant at limit: job waits regardless of priority
     |
     v
  [GPU Allocator] (runs every 10 seconds)
  Scheduling algorithm: bin-packing with affinity
    1. Sort pending jobs by priority (highest first)
    2. For each job, find nodes with enough free GPUs
    3. Prefer nodes where base model weights are already cached (saves 5-15 min load time)
    4. Prefer co-locating multi-GPU jobs on same node (NVLink > network)
    5. If no capacity: job stays queued, tenant notified with ETA

  GPU allocation examples:
    LoRA 7B:   1x A100 on any node
    LoRA 70B:  4x A100 on same node (NVLink)
    Full 7B:   8x A100 on same node (FSDP, all-reduce)
    Full 70B:  32x A100 across 4 nodes (FSDP + tensor parallel, NCCL RDMA)
     |
     v
  [Training Worker] (Kubernetes pod on allocated GPUs)
  Setup (2-5 minutes):
    1. Pull base model weights from model cache (NVMe SSD on each node)
       If not cached: download from S3 (70B model = 140GB at 10Gbps = ~2 min)
    2. Pull tokenized dataset from S3
    3. Initialize training framework (PyTorch + Hugging Face Trainer)
    4. Load LoRA config or FSDP config

  Training loop:
    - Forward pass -> loss computation -> backward pass -> optimizer step
    - Log metrics every 10 steps: train_loss, learning_rate, grad_norm, tokens_per_sec
    - Evaluate on validation set every 100 steps (or every epoch for small datasets)
    - Checkpoint every 30 minutes to S3 (LoRA checkpoint: ~500MB, full: ~30GB)
    - Heartbeat every 60 seconds to orchestrator

  Failure recovery:
    - If heartbeat missed for 3 minutes: mark node unhealthy
    - Reschedule job on new GPUs, resume from last checkpoint
    - Maximum retries: 3 (then mark job as failed, notify user)
    - Checkpoint retention: 7 days after job completion
```

### 4.3 Hyperparameter Management

```
Presets (users pick one, or override individual values):

lora_quality:
  learning_rate: 2e-4
  lora_rank: 64
  lora_alpha: 128
  lora_dropout: 0.05
  target_modules: ["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"]
  batch_size: auto (maximize GPU memory utilization)
  gradient_accumulation_steps: 4
  warmup_ratio: 0.03
  weight_decay: 0.01
  lr_scheduler: cosine
  bf16: true
  epochs: 3

lora_fast:
  learning_rate: 5e-4
  lora_rank: 16
  lora_alpha: 32
  lora_dropout: 0.1
  target_modules: ["q_proj", "v_proj"]
  epochs: 1

qlora_memory_efficient:
  quantization: 4-bit (NF4)
  double_quant: true
  lora_rank: 32
  lora_alpha: 64
  paged_adamw: true
  gradient_checkpointing: true

full_finetune:
  learning_rate: 2e-5
  batch_size: auto
  fsdp: "full_shard auto_wrap"
  fsdp_transformer_layer_cls: "LlamaDecoderLayer"
  gradient_checkpointing: true
  bf16: true
  epochs: 2-3

Auto batch size:
  Available GPU memory: 80GB per A100
  Model memory (70B bf16): ~140GB (spread across GPUs)
  LoRA overhead: ~2GB
  Activation memory per sample: varies by sequence length
  Binary search: start at batch_size=1, double until OOM, then fine-tune
  Typical results: LoRA 7B = batch 16, LoRA 70B = batch 4, Full 7B = batch 8

Early stopping:
  Monitor validation loss
  Patience: 3 evaluations without improvement
  Restore best checkpoint
  Notify user: "Training stopped early at epoch 2.3 (val_loss plateaued at 1.42)"
```

### 4.4 Evaluation Service

```
Evaluation runs automatically after training completes (and optionally during training).

Three evaluation modes:

Mode 1: Automated Metrics (runs in <5 minutes)
  - Validation loss: final val_loss from training (already computed)
  - Perplexity: exp(val_loss) on held-out validation set
  - Token accuracy: % of tokens in assistant response predicted correctly
  - Comparison: base model perplexity vs fine-tuned perplexity on same eval set
    Healthy: fine-tuned perplexity 10-30% lower than base
    Warning: fine-tuned perplexity higher than base (possible overfitting or bad data)

Mode 2: Golden Dataset Comparison (runs in 10-30 minutes)
  - User provides golden dataset: {input, expected_output} pairs
  - Run inference with base model on golden set -> base_predictions
  - Run inference with fine-tuned model on golden set -> finetuned_predictions
  - Compute:
    Exact match rate: % where finetuned_prediction == expected_output
    ROUGE-L: overlap between prediction and expected output
    Task-specific metrics:
      Classification: accuracy, F1, confusion matrix
      Extraction: precision, recall of extracted entities
      Generation: BLEU, ROUGE, BERTScore
  - Report: side-by-side comparison table
    +------------------+------------+-----------+--------+
    | Metric           | Base Model | Fine-Tuned| Delta  |
    +------------------+------------+-----------+--------+
    | Exact Match      | 42%        | 78%       | +36%   |
    | ROUGE-L          | 0.51       | 0.74      | +0.23  |
    | Avg Latency      | 1.2s       | 1.2s      | +0ms   |
    | Token Cost/Query | $0.015     | $0.015    | $0     |
    +------------------+------------+-----------+--------+

Mode 3: LLM-as-Judge Quality Scoring (runs in 15-45 minutes)
  - Sample 200 examples from golden dataset
  - For each example, generate response with both base and fine-tuned model
  - Submit to judge LLM (GPT-4o or Claude 3.5 Sonnet):
    Prompt template:
      "You are an expert evaluator. Given the instruction and two responses (A and B,
       order randomized), rate each on: accuracy (1-5), helpfulness (1-5),
       safety (1-5), instruction following (1-5). Then pick the better response."
  - Randomize A/B position to avoid position bias
  - Aggregate: win rate of fine-tuned vs base, average score improvement
  - Cost: 200 examples x ~2000 tokens x $0.01/1K = $4 per evaluation run

Regression detection:
  - If fine-tuned model scores worse than base on any metric by >5%: flag as regression
  - If fine-tuned model produces toxic/harmful content not present in base: block deployment
  - Safety check: run 50 adversarial prompts from red-team dataset
    If any new safety failures: require manual review before promotion

Evaluation record:
  {
    eval_id: uuid,
    job_id: uuid,
    model_version: "ft:llama-3-70b:acme:v3",
    base_model: "meta-llama/Llama-3-70B-Instruct",
    metrics: {
      val_loss: 1.42,
      perplexity: 4.14,
      golden_exact_match: 0.78,
      golden_rouge_l: 0.74,
      judge_win_rate: 0.72,
      judge_avg_score: 4.1,
      safety_pass: true,
      regression_detected: false
    },
    comparison_table: [...],
    created_at: timestamp
  }
```

### 4.5 Model Registry

```
The model registry is the system of record for all fine-tuned model artifacts.

Model versioning scheme:
  ft:{base_model}:{tenant}:{suffix}:{version}
  Example: ft:llama-3-70b:acme:customer-support:v3

Model artifact stored in S3:
  s3://models/{tenant_id}/{model_id}/
    adapter_config.json        (LoRA hyperparameters)
    adapter_model.safetensors  (LoRA weights, ~500MB for rank-64 on 70B)
    tokenizer/                 (if modified; usually inherited from base)
    training_config.json       (full training configuration)
    eval_report.json           (evaluation results)
    README.md                  (auto-generated model card)

Model metadata in PostgreSQL:
  {
    model_id: uuid,
    tenant_id: uuid,
    display_name: "customer-support-v3",
    base_model: "meta-llama/Llama-3-70B-Instruct",
    method: "lora",
    status: "evaluated",    // training | evaluated | staging | production | archived
    version: 3,
    training_job_id: uuid,
    dataset_id: uuid,
    dataset_hash: "sha256:abc...",   // exact data used
    hyperparameters: {...},
    eval_scores: {
      val_loss: 1.42,
      golden_exact_match: 0.78,
      judge_win_rate: 0.72
    },
    artifact_path: "s3://models/acme/ft-abc123/",
    artifact_size_bytes: 524_288_000,
    created_at: timestamp,
    promoted_at: timestamp,          // null until promoted
    promoted_by: "user@acme.com"
  }

Lineage tracking:
  Every model records:
    - Exact dataset version (hash) used for training
    - Base model version (hash of weights)
    - Training configuration (all hyperparameters)
    - Evaluation scores
    - Parent model (if iteratively fine-tuned)

  Query: "What data was used to train the model currently in production?"
    model(production) -> training_job -> dataset -> raw upload file
    Full audit trail for compliance

Promotion workflow:
  [evaluated] --manual approve--> [staging] --smoke test--> [production]

  Staging:
    - Deploy to staging inference endpoint
    - Run smoke test suite (20 canonical inputs, check outputs are reasonable)
    - Run latency benchmark (p50, p95, p99 must meet SLA)
    - Optional: shadow traffic (mirror 5% of production traffic, compare outputs)
    - Duration: 1-24 hours (configurable)

  Production promotion:
    - Requires explicit approval (API call or dashboard button)
    - Canary deployment: 5% traffic for 1 hour, then 25%, 50%, 100%
    - Automatic rollback if error rate >1% or latency p95 >2x baseline
    - Previous production model kept warm for 24 hours (instant rollback)

Model lifecycle:
  Training -> Evaluated -> Staging -> Production -> Archived
  Retention:
    Production models: kept indefinitely
    Staging models: 30 days after demotion
    Evaluated models never promoted: 90 days
    Archived: metadata kept forever, artifacts deleted after 1 year
```

### 4.6 Multi-Tenancy and Isolation

```
Isolation model: shared-infrastructure, isolated-data

Data isolation:
  - S3: separate prefix per tenant (s3://datasets/{tenant_id}/...)
  - PostgreSQL: tenant_id column on every table, row-level security (RLS)
    CREATE POLICY tenant_isolation ON datasets
      USING (tenant_id = current_setting('app.current_tenant')::uuid);
  - No cross-tenant data access possible at storage layer
  - Encryption: per-tenant KMS keys for data at rest
    Tenant can bring own key (BYOK) for HIPAA compliance

Compute isolation:
  - Training jobs run in isolated Kubernetes pods
  - Each pod: dedicated network namespace, no inter-pod communication
  - GPU allocation: dedicated GPUs per job (no GPU sharing between tenants)
  - Memory: pod memory limits enforced (OOM kills job, not neighbor)
  - Base model weights: shared read-only cache (not tenant-specific)

GPU quota management:
  Tier-based GPU quotas:
    Free tier: 1 concurrent GPU, 10 GPU-hours/month
    Pro tier: 8 concurrent GPUs, 200 GPU-hours/month
    Enterprise tier: 64 concurrent GPUs, 5,000 GPU-hours/month
    Custom tier: negotiated limits

  Quota enforcement:
    Before job submission: check remaining GPU-hours
    During job: decrement quota every minute (Redis atomic counter)
    If quota exhausted mid-training: checkpoint and pause (not kill)
    User can purchase additional GPU-hours to resume

Fair scheduling across tenants:
  Problem: one tenant submitting 100 jobs should not starve others
  Solution: weighted fair queuing
    Each tenant gets a "virtual clock" that advances proportional to GPU-hours consumed
    Scheduler picks the job from the tenant with the lowest virtual clock
    Effect: heavy users get throttled, light users get priority
    Starvation prevention: no tenant waits more than 30 minutes if GPUs are available
```

### 4.7 Cost Tracking and Billing

```
Cost components per training job:
  1. GPU compute:
     GPU-seconds = num_GPUs x wall_clock_seconds
     Cost = GPU-seconds x rate_per_GPU_second
     Rate: A100 80GB = $2.50/hr = $0.000694/sec
     Example: 4x A100 for 6 hours = 4 x 21,600 x $0.000694 = $60.00

  2. Data processing:
     Validation compute: $0.10 per GB processed
     Tokenization: $0.05 per GB
     Example: 2GB dataset = $0.30

  3. Evaluation:
     Automated metrics: free (computed during training)
     Golden dataset inference: charged at inference rate ($0.01/1K tokens)
     LLM-as-judge: charged at judge model rate (pass-through)
     Example: 200 golden examples = ~$4.00

  4. Storage:
     Dataset storage: $0.023/GB/month (S3 standard)
     Model artifacts: $0.023/GB/month
     Example: 500MB LoRA adapter for 6 months = $0.07

  5. Platform fee:
     Fixed per-job fee: $2 (LoRA), $10 (QLoRA), $50 (full fine-tune)
     Covers: scheduling, monitoring, API overhead

Cost estimation API (called before job submission):
  POST /v1/training/estimate
  Request: {dataset_id, base_model, method, epochs, hyperparameters}
  Response:
  {
    "estimated_cost": {
      "gpu_compute": "$60.00",
      "data_processing": "$0.30",
      "evaluation": "$4.00",
      "storage_monthly": "$0.07",
      "platform_fee": "$2.00",
      "total_one_time": "$66.30",
      "confidence_interval": "$55-$80"
    },
    "estimated_duration": "5h 20m",
    "gpu_allocation": "4x A100 80GB",
    "estimated_start_time": "2024-03-15T14:30:00Z"
  }

Real-time cost tracking during training:
  - Cost meter updates every 60 seconds
  - Dashboard shows: elapsed cost, burn rate, projected total
  - Alert if projected cost exceeds estimate by >50%
  - User can set hard budget cap: job killed if cost exceeds cap

Billing integration:
  - Monthly invoice per tenant
  - Line items: per-job breakdown with job ID, model, duration, cost
  - Usage dashboard: cost by model, by dataset, by time period
  - Spend anomaly detection: alert if daily spend >2x 7-day average
```

---

## 5. API Design

```
Base URL: https://api.finetune.example.com/v1

Authentication: Bearer token (API key per tenant)
Headers:
  Authorization: Bearer ft_key_abc123
  Content-Type: application/json

--- Dataset Management ---

POST /v1/datasets
  Upload a new dataset for fine-tuning.
  Body: multipart/form-data with "file" (JSONL) + "purpose" (fine-tune)
  For files >100MB: returns presigned upload URL
  Response:
  {
    "id": "ds_abc123",
    "status": "uploading",
    "filename": "support_data_v3.jsonl",
    "bytes": 215_000_000,
    "created_at": "2024-03-15T10:00:00Z"
  }

GET /v1/datasets/{dataset_id}
  Returns dataset metadata, validation status, quality report.
  Response includes: status, example_count, total_tokens, quality_score, pii_report

GET /v1/datasets/{dataset_id}/quality-report
  Detailed quality analysis: perplexity distribution, diversity score,
  length histogram, flagged examples, duplicate count.

DELETE /v1/datasets/{dataset_id}
  Deletes dataset and all processed artifacts. Irreversible.
  Active training jobs using this dataset are not affected (data already copied).

--- Training Jobs ---

POST /v1/training/jobs
  Create a new fine-tuning job.
  Body:
  {
    "dataset_id": "ds_abc123",
    "base_model": "meta-llama/Llama-3-70B-Instruct",
    "method": "lora",
    "hyperparameters": {
      "preset": "quality",
      "epochs": 3,
      "learning_rate": 2e-4      // optional override
    },
    "eval_dataset_id": "ds_eval456",
    "budget_cap_usd": 100.0,
    "suffix": "customer-support"
  }
  Response:
  {
    "id": "ftjob_xyz789",
    "status": "queued",
    "estimated_cost": "$66.30",
    "estimated_duration": "5h 20m",
    "model_name": "ft:llama-3-70b:acme:customer-support:v1",
    "created_at": "2024-03-15T10:05:00Z"
  }

GET /v1/training/jobs/{job_id}
  Returns job status, progress, metrics, cost so far.
  Response:
  {
    "id": "ftjob_xyz789",
    "status": "running",          // queued | running | evaluating | completed | failed
    "progress": {
      "current_step": 1240,
      "total_steps": 3600,
      "current_epoch": 1.03,
      "total_epochs": 3,
      "elapsed_time": "1h 42m",
      "eta": "3h 38m"
    },
    "metrics": {
      "train_loss": 1.58,
      "val_loss": 1.62,
      "learning_rate": 1.8e-4,
      "tokens_per_second": 8200,
      "gpu_utilization": 0.92
    },
    "cost": {
      "elapsed_usd": 18.40,
      "projected_total_usd": 62.50,
      "budget_cap_usd": 100.0
    }
  }

GET /v1/training/jobs/{job_id}/metrics
  Returns full training metric history (for plotting loss curves).
  Response: array of {step, train_loss, val_loss, lr, grad_norm, timestamp}

POST /v1/training/jobs/{job_id}/cancel
  Cancel a running job. Saves last checkpoint. Partial billing applies.

POST /v1/training/estimate
  Cost and duration estimate without creating a job. (See 4.7 above.)

--- Model Registry ---

GET /v1/models
  List all fine-tuned models for this tenant.
  Filter by: status, base_model, created_after

GET /v1/models/{model_id}
  Full model metadata: config, eval scores, lineage, status.

POST /v1/models/{model_id}/promote
  Promote model to next stage: evaluated -> staging -> production.
  Body: {"target_stage": "staging", "approval_note": "Passed QA review"}

POST /v1/models/{model_id}/rollback
  Rollback to previous production model version. Instant (<30 seconds).

DELETE /v1/models/{model_id}
  Archive model. Artifacts deleted after retention period.

--- Evaluation ---

POST /v1/evaluations
  Trigger an evaluation of a fine-tuned model against a golden dataset.
  Body:
  {
    "model_id": "ft_model_abc",
    "eval_dataset_id": "ds_golden",
    "modes": ["metrics", "golden_comparison", "llm_judge"],
    "judge_model": "gpt-4o"
  }

GET /v1/evaluations/{eval_id}
  Returns evaluation results, comparison tables, judge scores.
```

---

## 6. Trade-offs and Design Decisions

| Decision | Chosen | Alternative | Reason |
|----------|--------|-------------|--------|
| Default fine-tune method | LoRA | Full fine-tune | 95% of use cases need LoRA; 10-50x cheaper; comparable quality for most tasks |
| GPU scheduling | Kubernetes + custom scheduler | SLURM only | K8s for pod lifecycle + custom bin-packer for GPU affinity; SLURM alone lacks multi-tenant isolation |
| Checkpoint storage | S3 with 7-day retention | Persistent NVMe on nodes | S3 survives node failures; NVMe is faster but ephemeral; 7 days balances cost vs resume ability |
| Data validation | Multi-stage pipeline (5 stages) | Single-pass validation | Fail fast on format errors (cheap) before expensive PII/quality checks; each stage can scale independently |
| Evaluation approach | Three modes (metrics + golden + judge) | Metrics only | Automated metrics miss quality nuances; LLM-as-judge catches subtle regressions human evals would find |
| Model promotion | Manual approval gates | Auto-promote on metric threshold | Fine-tuned models can have subtle quality issues metrics miss; human-in-the-loop reduces risk |
| Multi-tenancy | Shared infra + data isolation | Dedicated clusters per tenant | Cost-effective at 2,000 tenants; dedicated clusters only for top-tier enterprise (custom pricing) |
| Hyperparameters | Preset-based with overrides | Full manual configuration | Most users are not ML engineers; presets encode best practices; power users can still override |
| Cost model | Per-GPU-second + platform fee | Flat per-job pricing | Fair billing proportional to resource usage; platform fee covers fixed overhead |
| Base model caching | NVMe SSD on each GPU node | Download from S3 every time | 70B model = 140GB; downloading every job wastes 2-5 min; NVMe cache serves in seconds |

---

## 7. Cost Impact Analysis

```
Platform economics at scale (monthly):

Revenue:
  20,000 jobs/month
  Job mix: 18,400 LoRA ($12 avg) + 1,000 QLoRA ($25 avg) + 600 full ($1,060 avg)
  LoRA revenue:  18,400 x $12 = $220,800
  QLoRA revenue:  1,000 x $25 = $25,000
  Full revenue:     600 x $1,060 = $636,000
  Evaluation fees: 20,000 x $4 = $80,000
  Storage fees: ~$10,000
  Total monthly revenue: ~$971,800

Infrastructure cost:
  GPU cluster (1,600 A100s): $2,880,000
  BUT: not all GPUs busy 24/7
  Average utilization target: 85%
  Effective GPU cost: $2,880,000 x 0.85 = $2,448,000 (cost of utilized GPUs)
  15% idle cost: $432,000 (unavoidable headroom for scheduling)

  Storage (S3, 400TB): $9,200
  Compute (API, validation, evaluation): $50,000
  Networking: $15,000
  Operations team (10 engineers): $250,000

  Total monthly cost: ~$3,204,200

Gross margin at current scale: ~$971,800 / $3,204,200 = 30% (negative margin)

Path to profitability:
  1. Scale to 5,000 tenants (60,000 jobs/month): revenue triples, GPU cost grows 2x
     Revenue: ~$2.9M, Cost: ~$5.6M -> still negative but improving
  2. Spot/preemptible GPUs for non-urgent LoRA jobs (60% cheaper):
     Savings: $1.2M/month (if 70% of LoRA jobs tolerate preemption)
  3. GPU utilization improvements:
     Time-slicing: run small LoRA jobs on same GPU (non-overlapping memory)
     Packing: fill gaps between large jobs with small ones
     Target: 92% utilization -> saves $200K/month vs 85%
  4. Higher-value jobs (enterprise custom pricing):
     Dedicated clusters: $50K+/month per enterprise tenant
     10 enterprise tenants = $500K/month

Break-even projection: ~8,000 tenants with spot GPU usage and 92% utilization
  Revenue: ~$3.8M, Cost: ~$3.5M -> profitable

Key cost levers:
  GPU utilization (biggest lever): every 1% improvement = $28,800/month saved
  Spot instance mix: 60% spot adoption = $1.2M/month saved
  Training efficiency: better hyperparameter defaults reduce average job time 15%
  Cache base model weights: saves 5 min/job x 20,000 jobs = 1,667 GPU-hours = $4,167/month
```

---

## 8. Interview Discussion Points

**Why default to LoRA instead of full fine-tuning?** LoRA trains only 0.1-1% of model parameters (low-rank adapters injected into attention layers), reducing GPU memory by 60-70% and training time by 5-10x. For a 70B model, full fine-tuning requires 32 A100 GPUs and costs $1,000+; LoRA achieves 90-95% of the quality improvement on 4 GPUs for $60-90. Full fine-tuning is only justified when the domain shift is extreme (e.g., English model to Japanese medical) and the dataset exceeds 100K examples. The platform should steer users toward LoRA by default and require explicit justification (minimum dataset size, budget confirmation) for full fine-tuning jobs.

**How do you handle the noisy neighbor problem on a shared GPU cluster?** Three mechanisms work together. First, GPU isolation: each training job gets dedicated GPU(s) with no time-sharing -- one job per GPU eliminates memory contention and CUDA context switching. Second, network bandwidth isolation: NCCL traffic for distributed training jobs uses dedicated RDMA lanes; data loading traffic uses separate NICs. Third, fair scheduling with virtual clocks prevents any single tenant from monopolizing the queue. The remaining risk is CPU and disk I/O contention on shared nodes -- mitigated by pod resource limits (CPU requests/limits, ephemeral storage limits) and by scheduling memory-intensive jobs on dedicated high-memory nodes.

**What happens when a GPU fails mid-training?** The platform checkpoints every 30 minutes to S3, storing optimizer state, model weights (or LoRA adapter weights), learning rate schedule position, data loader position, and random seed state. When a GPU failure is detected (heartbeat timeout after 3 minutes, CUDA error, or ECC memory error), the orchestrator marks the node unhealthy, evicts all jobs on that node, and reschedules each job on healthy GPUs starting from the last checkpoint. Maximum data loss is 30 minutes of training. For multi-GPU FSDP jobs, all GPUs must be replaced since FSDP state is sharded across them. The checkpoint-resume adds 2-5 minutes of overhead (download checkpoint, reinitialize training state). After 3 failed retries, the job is marked failed and the user is notified with a partial refund.

**How do you prevent users from fine-tuning models on harmful data?** The data pipeline has multiple defense layers. PII detection catches personal data (SSN, medical records) before it enters training. Content safety scoring samples 500 examples and runs them through a safety classifier (Llama Guard) to detect hate speech, violence instructions, or CSAM. If >2% of examples are flagged, the dataset is rejected with a detailed report. During training, the evaluation service runs adversarial safety prompts against the fine-tuned model -- if it produces harmful outputs not present in the base model (regression), deployment is blocked. Post-deployment, inference endpoints include output safety filters. These layers are defense-in-depth; no single layer is foolproof, but together they raise the bar significantly.

**Why not let users bring arbitrary PyTorch training scripts instead of a managed config?** Arbitrary code execution on GPU clusters creates severe security and operational risks: users could mine cryptocurrency, exfiltrate data from shared storage, crash nodes with OOM or infinite loops, or install malicious packages. The managed approach constrains the training loop to well-tested configurations (LoRA/QLoRA/FSDP with Hugging Face Trainer), which allows the platform to accurately estimate cost, predict GPU memory usage, guarantee checkpoint/resume works, and provide meaningful training metrics. Power users who need custom training loops are offered a "dedicated cluster" tier where they get isolated VMs with SSH access -- at 5-10x the cost of managed jobs.

**How does the cost estimator work, and how accurate is it?** The estimator uses benchmarked throughput tables: for each (base_model, method, GPU_type) combination, the platform has measured tokens-per-second under typical conditions (e.g., Llama-3-70B LoRA on 4xA100: 8,000 tokens/sec). Estimated time = (total_tokens x epochs) / throughput. Estimated cost = time x GPU_count x rate. The confidence interval accounts for variance in sequence length distribution (longer sequences are slower), batch size effects, and queue wait time. In production, the estimator achieves within 20% accuracy for 80% of jobs. The remaining 20% are outliers from unusual sequence length distributions or unexpected early stopping. Users can set a hard budget cap -- the platform kills the job if cost exceeds the cap, ensuring no surprise bills.

**How would you implement A/B testing between a base model and a fine-tuned model in production?** The deployment service supports traffic splitting at the inference endpoint level. When promoting a fine-tuned model, the user configures a canary percentage (e.g., 5%). The load balancer routes 5% of requests to the fine-tuned model and 95% to the current production model (base or previous fine-tune). Both responses are logged with a variant tag. The evaluation service computes online metrics: latency comparison, user feedback scores (thumbs up/down), task-specific KPIs (e.g., customer support ticket resolution rate). After the configured observation period (1-24 hours), the user reviews metrics and decides to promote (shift to 100%) or rollback. Automatic rollback triggers if fine-tuned model error rate exceeds base by >1% or latency p95 exceeds 2x.

**What is the model lineage problem and why does it matter for enterprises?** Regulated industries (finance, healthcare) require audit trails: "Which data trained the model that made this decision?" The model registry stores cryptographic hashes of the exact dataset, training configuration, and base model weights used for each fine-tuned version. Given any production prediction, you can trace: prediction -> model version -> training job -> dataset version -> raw upload file. This chain is immutable -- even if the dataset or model is later deleted, the metadata and hashes persist. For GDPR compliance, if a user requests data deletion, the platform can identify which models were trained on that data, flag them for retraining, and prove the data was removed. Without lineage tracking, an enterprise cannot answer "was this customer's data used in model training?" -- a compliance failure.

**How do you handle the cold start problem for base model weights?** A 70B parameter model in bf16 is approximately 140GB. Downloading from S3 at 10Gbps takes about 2 minutes. If every training job downloads fresh, that is 2 minutes of wasted GPU time (4 GPUs idle = $0.09 wasted). The solution is a two-tier cache: each GPU node has 2TB NVMe SSD that caches the 3-4 most popular base models (covering 90% of jobs). A background daemon pre-fetches models based on the job queue -- if a Llama-3-70B job is queued for node 7 and node 7 does not have Llama-3-70B cached, the daemon starts downloading before the job is scheduled. Cache eviction uses LFU (least frequently used) with a 7-day minimum retention. For the 10% of jobs using rare models, the 2-minute download is acceptable and included in the cost estimate.

**How would you evolve this platform to support continual learning / online fine-tuning?** The current design is batch-oriented: upload data, train, evaluate, deploy. Continual learning requires three additions. First, a streaming data pipeline that accepts real-time feedback (user corrections, thumbs-down signals) and accumulates them into training batches automatically. Second, scheduled retraining triggers: "retrain every Sunday using the last 7 days of feedback data, merged with the original training set." Third, catastrophic forgetting prevention: when retraining on new data, evaluate on the original golden set to ensure old capabilities are preserved. The model registry already supports versioning, so each retrained model becomes a new version with full lineage. The key architectural change is the data pipeline: it must support append-mode datasets that grow over time, rather than static uploads.