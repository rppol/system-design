# Serverless & FaaS

> Phase 5 — Cloud Platforms · Difficulty: Advanced

Serverless lets you run code without provisioning or managing servers — the platform handles scaling (including to zero), patching, and capacity, and you pay only for actual execution. **Function-as-a-Service (FaaS)** — AWS Lambda, Google Cloud Functions, Azure Functions, and the Kubernetes-native **Knative** — is the purest form. This module covers the FaaS execution model, the **cold-start** problem, **event-driven** architectures, **API Gateway** as the HTTP front door, and **Step Functions** for orchestrating multi-step workflows, with concrete limits (Lambda's 15-minute max timeout, ~100ms-1s cold starts, 10 GB memory ceiling) that shape real designs.

---

## 1. Concept Overview

Serverless is a spectrum: FaaS (Lambda) at one end, serverless containers (Fargate, Cloud Run) and managed backends (DynamoDB, S3, SQS) along the way. The defining traits: no server management, automatic fine-grained scaling, scale-to-zero, and per-use billing (e.g., Lambda bills per millisecond of execution times allocated memory, plus per-request).

- **FaaS** runs a function in response to an **event** — an HTTP request via API Gateway, an S3 upload, an SQS/Kinesis message, a cron schedule, a DynamoDB stream. The platform spins up an execution environment (a micro-VM like Firecracker for Lambda), runs your handler, and may reuse or freeze it.
- **Cold start** is the latency when no warm environment exists: the platform must initialize a new one (download code, start runtime, run init). Typically ~100ms-1s; worse for large packages, VPC-attached functions, or heavy init.
- **Event-driven** architecture composes functions and managed services into pipelines, decoupled by queues/streams/event buses (see [../../hld/](../../hld/) for messaging patterns).
- **API Gateway** turns HTTP requests into function invocations, handling auth, throttling, and routing.
- **Step Functions** (AWS) / Workflows (GCP) / Durable Functions (Azure) orchestrate multi-step, stateful workflows with retries, branching, and parallelism — solving the problem that individual functions are stateless and time-limited.

Concrete Lambda limits: 15-minute max timeout, 128 MB-10 GB memory (CPU scales with memory), 6 MB synchronous payload, 250 MB unzipped deployment (or 10 GB container image), 1000 default concurrent executions per account (soft limit).

---

## 2. Intuition

> **One-line analogy**: FaaS is a taxi vs owning a car. You don't buy, insure, park, and maintain a vehicle (a server) that sits idle 95% of the time — you summon a ride exactly when you need it and pay for the trip. The "cold start" is the few seconds you wait for the taxi to arrive when none is nearby.

**Mental model**: Your code is a stateless function the cloud invokes on an event. The platform owns the lifecycle — it creates execution environments on demand, freezes them between invocations, reuses warm ones, and destroys idle ones. You think in events and handlers, not servers and processes. State lives in external managed services (DynamoDB, S3, Redis), never in the function.

**Why it matters**: Serverless collapses operational overhead and matches cost to actual usage — ideal for spiky, event-driven, or unpredictable workloads. But the execution model imposes hard constraints (statelessness, timeouts, cold starts, concurrency limits) that, if ignored, produce slow APIs, dropped messages, and surprise bills. Designing *for* the model — not fighting it — is the skill.

**Key insight**: **The function is ephemeral and stateless; durability and orchestration live elsewhere.** Long-running logic belongs in Step Functions, state in managed stores, and reliability in the event source's retry/DLQ semantics. Treating a Lambda like a long-lived server is the single most common and costly mistake.

---

## 3. Core Principles

1. **Stateless handlers.** No in-memory state survives reliably between invocations; persist to external stores.
2. **Event-driven by design.** Functions react to events; decouple with queues/streams/event buses.
3. **Idempotency is mandatory.** At-least-once delivery means handlers must tolerate duplicate events.
4. **Mind the limits.** Timeout (15 min Lambda), memory/CPU coupling, payload size, concurrency caps.
5. **Optimize the cold path.** Minimize package size and init work; use provisioned concurrency for latency-critical paths.
6. **Orchestrate with state machines** (Step Functions), not by chaining functions that poll each other.
7. **Least-privilege per function.** Each function gets its own narrowly scoped execution role.

---

## 4. Types / Architectures / Strategies

### FaaS platform comparison

| Feature | AWS Lambda | GCP Cloud Functions (2nd gen) | Azure Functions | Knative (K8s) |
|---------|-----------|-------------------------------|-----------------|---------------|
| Max timeout | 15 min | 60 min (HTTP) / 9 min (event) | 5-10 min (longer on Premium) | Configurable |
| Memory | 128 MB-10 GB | up to 32 GB | up to 14 GB | Pod limits |
| Scale to zero | Yes | Yes | Yes (Consumption) | Yes |
| Cold start | ~100ms-1s | ~100ms-2s | ~100ms-2s | Depends on image |
| Packaging | Zip / container | Source / container | Zip / container | Container |
| Concurrency | 1 req/instance | configurable | configurable | configurable |

### Event source patterns

| Source | Invocation | Retry/durability |
|--------|-----------|------------------|
| API Gateway (sync) | Request/response | Client retries; no built-in DLQ |
| SQS (async poll) | Batch poll | Redrive to DLQ after N failures |
| Kinesis/DynamoDB streams | Ordered batch | Retries per shard; can block on poison record |
| S3 / EventBridge (async) | Event push | Async retry + on-failure DLQ |
| Schedule (cron) | Timer | At-least-once |

### Scaling/concurrency controls (Lambda)

| Control | Effect |
|---------|--------|
| Reserved concurrency | Caps a function's max concurrent executions (also guarantees that floor) |
| Provisioned concurrency | Pre-warms N environments -> no cold start on those |
| Account concurrency | Default 1000 (soft); shared across functions unless reserved |
| SnapStart (Java) | Snapshots initialized state to cut cold starts ~10x |

---

## 5. Architecture Diagrams

```
Event-driven serverless pipeline (image processing)

  Client --HTTPS--> API Gateway --invoke--> [Lambda: presign upload]
                                                  |
  Client --PUT--> S3 (uploads/) ---ObjectCreated event---> [Lambda: thumbnail]
                                                  |  on error
                                                  v
                                            DLQ (SQS)  --> alarm/replay
                                                  |  success
                                                  v
                                        S3 (thumbnails/) + DynamoDB (metadata)

Synchronous request path + cold start

  request --> API Gateway --> Lambda
                                |--- WARM: reuse env (~1-5ms overhead) -> handler
                                |--- COLD: create micro-VM -> download code ->
                                          start runtime -> run init -> handler
                                          (~100ms-1s added latency)

Step Functions orchestration (order workflow)

  [Start] -> ValidateOrder -> (Choice: in stock?)
              |yes-> ChargePayment -> (Parallel: ReserveStock | NotifyUser) -> Ship -> [End]
              |no -> Backorder -> [End]
              (each task = a Lambda; retries/catch/timeout defined in the state machine)
```

---

## 6. How It Works — Detailed Mechanics

### A Lambda handler (Python) with idempotency

```python
import boto3, os
ddb = boto3.resource("dynamodb").Table(os.environ["IDEMPOTENCY_TABLE"])

def handler(event, context):
    # SQS delivers at-least-once -> dedupe on a stable key
    for record in event["Records"]:
        msg_id = record["messageId"]
        try:
            ddb.put_item(
                Item={"id": msg_id, "ttl": int(context.get_remaining_time_in_millis())},
                ConditionExpression="attribute_not_exists(id)")  # first time only
        except ddb.meta.client.exceptions.ConditionalCheckFailedException:
            continue  # already processed -> skip duplicate
        process(record["body"])
    return {"statusCode": 200}
```

### Defining a Lambda + API Gateway (Terraform)

```hcl
resource "aws_lambda_function" "api" {
  function_name = "orders-api"
  runtime       = "python3.12"
  handler       = "app.handler"
  memory_size   = 512          # CPU scales with memory; 512MB ~= 0.33 vCPU
  timeout       = 10           # synchronous API path -> keep short
  filename      = "build.zip"
  role          = aws_iam_role.lambda.arn   # least-privilege per function
}

resource "aws_lambda_provisioned_concurrency_config" "warm" {
  function_name                     = aws_lambda_function.api.function_name
  provisioned_concurrent_executions = 5     # 5 pre-warmed envs -> no cold start
  qualifier                         = aws_lambda_function.api.version
}

resource "aws_apigatewayv2_api" "http" {
  name          = "orders"
  protocol_type = "HTTP"     # HTTP API: cheaper/faster than REST API
}
```

### SQS event source with DLQ (poison-message handling)

```hcl
resource "aws_lambda_event_source_mapping" "from_sqs" {
  event_source_arn = aws_sqs_queue.work.arn
  function_name    = aws_lambda_function.worker.arn
  batch_size       = 10
  function_response_types = ["ReportBatchItemFailures"]  # partial-batch retry
}

resource "aws_sqs_queue" "work" {
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlq.arn
    maxReceiveCount     = 5    # after 5 failed receives -> DLQ
  })
}
```

### Step Functions state machine (Amazon States Language)

```json
{
  "StartAt": "ChargePayment",
  "States": {
    "ChargePayment": {
      "Type": "Task",
      "Resource": "arn:aws:lambda:...:function:charge",
      "Retry": [{"ErrorEquals": ["TransientError"], "MaxAttempts": 3, "BackoffRate": 2.0, "IntervalSeconds": 2}],
      "Catch": [{"ErrorEquals": ["States.ALL"], "Next": "RefundAndFail"}],
      "Next": "Fulfill"
    },
    "Fulfill": { "Type": "Task", "Resource": "arn:aws:lambda:...:function:fulfill", "End": true },
    "RefundAndFail": { "Type": "Task", "Resource": "arn:aws:lambda:...:function:refund", "End": true }
  }
}
```

### Cold-start anatomy and mitigation

```text
Init phase (billed differently): runtime bootstrap + your global/module-level code
  -> heavy SDK clients, large dependencies, VPC ENI attach all extend this
Mitigations:
  - keep package small; lazy-import infrequently used modules
  - reuse clients across invocations (define boto3 client at module scope)
  - provisioned concurrency for latency-critical sync paths
  - Java: SnapStart (~10x faster cold start via Firecracker snapshot)
  - avoid unnecessary VPC attachment (modern Hyperplane ENIs reduced this penalty)
```

---

## 7. Real-World Examples

- **Netflix** uses Lambda extensively for event-driven operations — encoding triggers on S3 uploads, security automation, and ops tooling — rather than for the core streaming hot path.
- **Coca-Cola** rebuilt vending/loyalty backends on Lambda + API Gateway + DynamoDB, paying per transaction and scaling automatically to seasonal spikes, cutting infrastructure cost dramatically vs always-on servers.
- **iRobot** runs its IoT backend serverless (Lambda + IoT Core + DynamoDB) to handle millions of robots reporting intermittently — a textbook spiky, event-driven workload.
- **Step Functions for ETL/ML**: teams orchestrate multi-step data pipelines (validate -> transform -> load -> notify) as state machines, getting built-in retries, visual execution history, and per-step error handling instead of brittle function chains.

---

## 8. Tradeoffs

| Decision | Option A | Option B | Key factor |
|----------|----------|----------|-----------|
| Compute model | FaaS (Lambda) | Containers/VMs | Spiky/event-driven vs steady high-throughput |
| Cold start | Scale to zero (cheap) | Provisioned concurrency (fast) | Cost vs tail latency |
| Orchestration | Chain functions manually | Step Functions state machine | Simplicity vs reliability/visibility |
| API front door | API Gateway (managed) | ALB + Lambda | Features (auth/throttle) vs cost at high volume |
| Event delivery | SQS (decoupled, DLQ) | Direct sync invoke | Resilience vs latency |
| Packaging | Zip (fast) | Container image (10 GB, custom) | Simplicity vs large deps/custom runtime |
| Vendor lock-in | Native FaaS | Knative (portable) | Managed convenience vs portability |

---

## 9. When to Use / When NOT to Use

**Use serverless/FaaS when:** workloads are spiky, event-driven, or unpredictable; you want zero ops and pay-per-use; tasks are short and stateless (image processing, webhooks, cron jobs, glue code, ETL steps); or you're prototyping and want to move fast. It shines for "glue" between managed services.

**Reconsider when:** workloads are steady and high-throughput (a constantly busy service is often cheaper on reserved/containers — see [cloud_cost_optimization_finops](../cloud_cost_optimization_finops/)); tasks exceed timeout limits (15 min Lambda) or need persistent connections/state; you need predictable sub-10ms tail latency (cold starts hurt); or you require GPUs/specialized hardware. Very chatty, latency-sensitive microservices can suffer from cold starts and per-invocation overhead — containers on [kubernetes_architecture](../kubernetes_architecture/) may fit better.

---

## 10. Common Pitfalls

**Pitfall 1 — Treating a Lambda as a long-lived server (in-memory state, long tasks).**

```python
# BROKEN: accumulating state in memory and running a long job in one invocation
CACHE = {}                      # NOT durable: lost when the env is recycled; not shared across concurrent envs
def handler(event, context):
    CACHE[event["id"]] = event["data"]   # vanishes; different invocations hit different envs
    for item in range(10_000_000):       # may exceed the 15-min timeout -> killed mid-work
        slow_process(item)
```

```python
# FIX: externalize state; offload long work to Step Functions / batched async
import boto3
ddb = boto3.resource("dynamodb").Table("state")   # client reused across warm invocations
def handler(event, context):
    ddb.put_item(Item={"id": event["id"], "data": event["data"]})  # durable, shared
    # long work -> emit to SQS/Step Functions to process in bounded chunks
    boto3.client("stepfunctions").start_execution(stateMachineArn=ARN, input=event["body"])
```

**Pitfall 2 — Ignoring at-least-once delivery (duplicate processing).** SQS/Kinesis/EventBridge deliver at least once; a non-idempotent handler double-charges or double-inserts. FIX: dedupe on a stable key with a conditional write (see §6) or idempotency keys.

**Pitfall 3 — No DLQ on async/event sources.** Failures retry then silently vanish, losing data. FIX: configure a DLQ (SQS) with `maxReceiveCount` and alarm on its depth; replay after fixing the bug.

**Pitfall 4 — Runaway concurrency / recursive triggers.** A Lambda writing to the same S3 bucket/prefix that triggers it creates an infinite loop and a huge bill; or unbounded fan-out exhausts account concurrency (default 1000) and throttles everything. FIX: scope triggers to distinct prefixes, set reserved concurrency caps, and add concurrency alarms.

---

## 11. Technologies & Tools

| Tool/Service | Purpose |
|--------------|---------|
| AWS Lambda / Cloud Functions / Azure Functions | FaaS |
| Knative | Kubernetes-native serverless ([kubernetes_architecture](../kubernetes_architecture/)) |
| API Gateway (HTTP/REST) / Cloud Endpoints | HTTP front door, auth, throttling |
| Step Functions / Workflows / Durable Functions | Stateful workflow orchestration |
| SQS / Pub/Sub / EventBridge | Event/message delivery ([../../hld/](../../hld/)) |
| DynamoDB / Firestore | Serverless state store ([../../database/](../../database/)) |
| AWS SAM / Serverless Framework | Serverless IaC/deploy ([infrastructure_as_code_terraform](../infrastructure_as_code_terraform/)) |
| Provisioned Concurrency / SnapStart | Cold-start mitigation |
| Lambda Powertools | Idempotency, tracing, structured logging |

---

## 12. Interview Questions with Answers

**Q1: What is a cold start, what causes it, and how do you mitigate it?**
A cold start is the added latency (~100ms-1s) when the platform must initialize a new execution environment — downloading code, starting the runtime, and running your init code — because no warm one is available. It's worsened by large deployment packages, heavy module-level initialization, and (historically) VPC ENI attachment. Mitigate by minimizing package size, reusing SDK clients at module scope, using provisioned concurrency for latency-critical synchronous paths, and SnapStart for Java; for truly latency-sensitive endpoints, keep instances warm.

**Q2: Why must serverless functions be idempotent?**
Because event sources like SQS, Kinesis, and EventBridge guarantee at-least-once delivery, so the same event can invoke your function more than once. A non-idempotent handler would double-charge a card or double-insert a record. Make handlers idempotent by deduplicating on a stable key with a conditional write to DynamoDB or by using idempotency keys (e.g., Lambda Powertools), so reprocessing a duplicate is a no-op.

**Q3: When is serverless the wrong choice?**
When the workload is steady and high-throughput, an always-on container or reserved instance is usually cheaper than per-invocation billing; when tasks exceed the timeout (15 minutes for Lambda) or need persistent connections; when you need predictable single-digit-millisecond tail latency that cold starts violate; or when you need GPUs/specialized hardware. Serverless excels at spiky, event-driven, short, stateless work — not at constant heavy load or long-running stateful processes.

**Q4: How does Lambda's memory setting affect performance and cost?**
Memory and CPU are coupled — increasing memory proportionally increases allocated vCPU, so a CPU-bound function can run faster (and sometimes cheaper) at higher memory because it finishes sooner. Billing is memory-GB times execution-milliseconds plus per-request, so the cost-optimal setting is found by tuning (e.g., AWS Lambda Power Tuning). Don't assume the smallest memory is cheapest; faster execution at higher memory can lower total cost.

**Q5: What problem do Step Functions solve that plain function chaining doesn't?**
Step Functions provide stateful orchestration — sequencing, branching, parallelism, retries with backoff, error catching, and timeouts — across multiple stateless, time-limited functions, with a durable execution history. Chaining functions that invoke or poll each other is brittle: there's no central state, retries and error handling are hand-rolled, and debugging is hard. Use a state machine for any multi-step workflow that needs reliability and visibility, like order processing or ETL.

**Q6: How do you handle failures in an event-driven serverless pipeline?**
Configure dead-letter queues (DLQs) on async invocations and SQS event sources with a `maxReceiveCount` so poison messages move to the DLQ after N failed attempts instead of vanishing, and alarm on DLQ depth. Use partial batch failure reporting for SQS so one bad record doesn't fail the whole batch, and make handlers idempotent so retries are safe. After fixing the root cause, replay messages from the DLQ.

**Q7: API Gateway vs ALB in front of Lambda — how do you choose?**
API Gateway provides rich API features — request validation, throttling, API keys, usage plans, authorizers (JWT/IAM/Cognito), and caching — and is the default for public APIs, but costs more per request at high volume. An ALB invoking Lambda is cheaper at very high request volumes and integrates with existing VPC/WAF setups but lacks API-management features. Use API Gateway (HTTP API for lower cost) for managed public APIs and ALB-to-Lambda when you need cheap, high-volume routing alongside other targets.

**Q8: What is provisioned concurrency and when do you use it?**
Provisioned concurrency keeps a configured number of execution environments initialized and ready, so requests hitting them incur no cold start. You use it for latency-sensitive synchronous endpoints (user-facing APIs) where the occasional ~500ms cold start is unacceptable, accepting that you pay for the warm capacity even when idle. Reserved concurrency, by contrast, caps maximum concurrency (and guarantees a floor) but doesn't pre-warm.

**Q9: How does scale-to-zero work and what's the tradeoff?**
When there are no events, the platform destroys idle execution environments so you pay nothing for compute — ideal for intermittent workloads. The tradeoff is that the next request after a quiet period hits a cold start. Platforms like Cloud Run and Lambda let you set a minimum/provisioned instance count above zero to trade some cost for guaranteed warm capacity, balancing cost against tail latency.

**Q10: What is Knative and why might you choose it over Lambda?**
Knative is a Kubernetes-based serverless framework that brings scale-to-zero, request-driven autoscaling, and eventing to your own cluster, so functions/containers run portably on any Kubernetes (any cloud or on-prem). You'd choose it to avoid vendor lock-in, to run serverless alongside existing Kubernetes workloads, or to meet on-prem/regulatory constraints — at the cost of operating the cluster yourself, which managed FaaS abstracts away.

**Q11: How do you prevent a runaway Lambda from causing a huge bill or outage?**
Set reserved concurrency to cap a function's maximum concurrent executions, avoid recursive triggers (e.g., a function writing to the same S3 prefix that invokes it), and add CloudWatch alarms on concurrency, error rate, and DLQ depth. Account concurrency defaults to 1000 (soft), shared across functions, so one runaway function can throttle everything unless capped. Test fan-out behavior and put guardrails in before going to production.

**Q12: How do you give a Lambda least-privilege access to other AWS services?**
Each function gets its own IAM execution role with a policy scoped to exactly the actions and resources it needs — for example, `dynamodb:PutItem` on one table and `s3:GetObject` on one bucket prefix — and nothing more. Avoid sharing a broad role across many functions, because that widens the blast radius if one function is compromised. Use IAM Access Analyzer and per-function roles, and reference [secrets_management](../secrets_management/) for any credentials the function needs.

---

## 13. Best Practices

- **Keep handlers stateless and idempotent**; externalize state to DynamoDB/S3/Redis.
- **Right-size memory** via power tuning — faster execution can be cheaper.
- **Always configure DLQs** on async/event sources and alarm on their depth.
- **Orchestrate multi-step work with Step Functions**, not hand-chained functions.
- **Mitigate cold starts** with small packages, reused clients, and provisioned concurrency on latency-critical paths.
- **One least-privilege role per function**; never a shared wildcard role.
- **Cap concurrency** (reserved) and add alarms to prevent runaway cost/throttling.
- **Use HTTP API over REST API** on API Gateway for lower cost/latency when you don't need REST-only features.

---

## 14. Case Study

### Scenario: A synchronous "do everything in one Lambda" API times out under load

A team built an order-submission endpoint as a single Lambda behind API Gateway: it validated the order, charged the card via a slow third-party API, reserved inventory, and emailed the customer — all synchronously in one invocation. Under a flash sale, the third-party charge API slowed to 8 seconds, invocations piled up, the function hit its 15-second timeout, customers were charged but got errors, and concurrency maxed out at 1000, throttling unrelated functions.

```python
# BROKEN: monolithic synchronous Lambda doing everything inline
def handler(event, context):
    validate(event)              # fast
    charge_card(event)           # slow 3rd-party (can take 8s+), no retry isolation
    reserve_inventory(event)     # if this fails after charge -> charged but no order
    send_email(event)            # blocks the response
    return {"statusCode": 200}   # times out under load; partial failures corrupt state
```

```json
// FIX: decompose into a Step Functions workflow; API just accepts + enqueues
// API Gateway -> tiny Lambda: validate + start execution, return 202 immediately
{
  "StartAt": "ChargePayment",
  "States": {
    "ChargePayment": {
      "Type": "Task", "Resource": "arn:...:charge",
      "Retry": [{"ErrorEquals": ["Transient"], "MaxAttempts": 4, "BackoffRate": 2.0, "IntervalSeconds": 2}],
      "Catch": [{"ErrorEquals": ["States.ALL"], "Next": "Refund"}],
      "Next": "ReserveAndNotify"
    },
    "ReserveAndNotify": {
      "Type": "Parallel",
      "Branches": [
        {"StartAt": "Reserve", "States": {"Reserve": {"Type": "Task", "Resource": "arn:...:reserve", "End": true}}},
        {"StartAt": "Email",   "States": {"Email":   {"Type": "Task", "Resource": "arn:...:email",   "End": true}}}
      ],
      "End": true
    },
    "Refund": {"Type": "Task", "Resource": "arn:...:refund", "End": true}
  }
}
```

The redesign split the monolith: API Gateway invokes a tiny Lambda that validates and starts a Step Functions execution, returning `202 Accepted` instantly. The state machine handles the charge with retries/backoff, reserves inventory and emails in parallel, and on charge failure runs a compensating refund — so the system never ends up "charged but no order." Each task got its own least-privilege role, idempotency keys deduplicated retries, and reserved concurrency capped the charge function so it couldn't starve others.

**Outcome:** the API responded in ~50ms instead of timing out, the slow third-party call no longer blocked users, partial failures were handled by compensation instead of corrupting state, and the throttling cascade disappeared because concurrency was capped per function. The lesson: **keep the synchronous path tiny, push multi-step and slow work into an orchestrated, idempotent, durable workflow.**

**Discussion questions:**
1. Why did doing everything synchronously in one Lambda cause both correctness (charged-but-no-order) and availability (throttling) failures?
2. How does the Step Functions retry/catch/compensate model prevent the "charged but no order" bug?
3. When would you use SQS + worker Lambdas instead of Step Functions for this workflow?

---

**Cross-references:** [cloud_fundamentals_and_aws](../cloud_fundamentals_and_aws/) (Lambda IAM roles, VPC), [gcp_and_azure_essentials](../gcp_and_azure_essentials/) (Cloud Functions/Cloud Run/Azure Functions equivalents), [cloud_cost_optimization_finops](../cloud_cost_optimization_finops/) (per-invocation vs always-on cost), [kubernetes_architecture](../kubernetes_architecture/) (Knative on K8s), [../../hld/](../../hld/) (event-driven/messaging patterns), [../../database/](../../database/) (DynamoDB as serverless state), [secrets_management](../secrets_management/) (function secrets).
