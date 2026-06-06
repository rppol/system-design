# Performance & Load Testing

> Phase 8 — Specialized Platforms & Performance · Difficulty: Intermediate · Q&A target: 12

Validating that a system meets its performance and reliability targets *before* real users do: load, stress, spike, soak, and capacity tests with k6 and Locust; distributed load generation; percentile-correct latency measurement; and wiring performance gates into CI/CD so regressions fail the build. This module closes the SRE loop — it proves the SLOs from [sre_principles_and_slos](../sre_principles_and_slos/) hold under load and feeds the capacity numbers used in [cloud_cost_optimization_finops](../cloud_cost_optimization_finops/) and [kubernetes_scheduling_and_autoscaling](../kubernetes_scheduling_and_autoscaling/). For *application-level profiling* (flame graphs, code hot paths) cross-reference [`../../java/performance_and_tuning/`](../../java/performance_and_tuning/) and [`../../python/performance_and_profiling/`](../../python/performance_and_profiling/); this module is about *system-level* black-box load.

---

## 1. Concept Overview

Performance testing answers questions you cannot answer by reading code: *How many requests per second can this service sustain before latency degrades? What's the p99 at expected peak? Does it leak memory over 8 hours? What happens when traffic doubles in 10 seconds? At what load does it fall over, and how does it fail?*

It splits into distinct test **types**, each answering a different question:

- **Load test** — sustained expected/peak traffic; verifies SLOs (latency, error rate) hold.
- **Stress test** — push past expected peak until something breaks; finds the breaking point and how it fails (graceful degradation vs cascading collapse).
- **Spike test** — sudden sharp traffic jump; verifies autoscaling reacts and the system survives the transient.
- **Soak / endurance test** — moderate load for hours/days; surfaces memory leaks, connection-pool exhaustion, disk fill, and slow resource creep invisible in short runs.
- **Capacity test** — ramp load in steps to find max sustainable throughput per replica, which feeds autoscaling and cost models.

The two dominant open-source tools: **k6** (Grafana Labs; Go engine, JavaScript test scripts, low resource footprint, excellent CI integration) and **Locust** (Python; tests written as Python, easy distributed mode, web UI). For protocol breadth there's also **Gatling** (Scala/JVM), **JMeter** (Java, GUI-heavy, legacy), and **Vegeta** (Go, HTTP constant-rate).

The non-obvious hard parts are (1) **the load generator is usually the bottleneck** — one machine saturates its own CPU/file-descriptors/network before stressing a real backend, so realistic tests need distributed generation; and (2) **measuring percentiles correctly** — naive timing under-reports latency badly (coordinated omission), so you must use open-model / constant-arrival-rate load.

---

## 2. Intuition

> Load testing is a **wind tunnel for software**. You don't wait for a hurricane (Black Friday) to discover the roof flies off — you blast the system with controlled, increasing wind and watch exactly where and how it fails, with instruments measuring every surface. A soak test is *leaving the fan on for two days* to see if a bolt slowly works loose. A spike test is *a sudden gust* to see if the structure flexes or shatters.

**Mental model:** Two ways to drive load. **Closed model** (fixed number of virtual users, each does request → wait for response → next request) — throughput *depends on* the system's latency, so a slow system gets *less* load, hiding the problem. **Open model** (constant arrival rate — N new requests/sec regardless of whether old ones finished) — this is how real users behave (they keep arriving even when you're slow), and it's the only model that measures latency honestly under saturation.

**Why it matters:** Capacity surprises are the most expensive kind of outage because they happen at your busiest, highest-revenue moment. A load test that costs $50 in cloud time can prevent a Black Friday outage that costs millions. And without a measured "requests/sec per replica" number, autoscaling targets and capacity budgets are guesses.

**Key insight:** **Coordinated omission** is the silent killer of load-test validity. If your tool waits for each response before sending the next (closed model), then when the system stalls, the tool *also* stalls and simply doesn't send the requests that would have piled up — so it never measures the long latencies real users experience. The reported p99 looks great while real users are timing out. Always use a constant-arrival-rate (open model) executor for latency-truth.

---

## 3. Core Principles

1. **Test type follows the question.** Don't run "a load test" — decide whether you're verifying SLOs (load), finding the breaking point (stress), validating autoscaling (spike), or hunting leaks (soak). Each needs a different load profile.
2. **Measure percentiles, never averages.** Average latency hides the tail; p95/p99/p99.9 are what users feel. A 50ms average with a 4s p99 is a bad service. Report and gate on percentiles.
3. **Use an open model (constant arrival rate) for latency truth.** Avoid coordinated omission; drive a fixed RPS independent of response time.
4. **The load generator must out-scale the target.** Verify the generator isn't the bottleneck (CPU, file descriptors, ephemeral ports, network). If it is, distribute it across multiple machines/pods.
5. **Test against production-like infra, isolated from prod traffic.** Same instance types, replica counts, and dependencies (or realistic mocks). Testing a 1-replica staging box tells you nothing about a 20-replica prod deployment.
6. **Baseline, then gate.** Establish a known-good performance baseline; in CI, fail the build when a change regresses p95/error-rate/throughput beyond a threshold. A test that doesn't gate is just a dashboard.
7. **Warm up before measuring.** JIT compilation, cache fills, connection pools, and autoscaler ramp all distort the first minute. Discard warm-up; measure steady state.

---

## 4. Types / Architectures / Strategies

**Test types and their load profiles:**

| Type | Load profile | Question answered | Duration |
|------|-------------|-------------------|----------|
| Smoke | 1–5 VUs | Does it work at all? (CI gate, cheap) | < 1 min |
| Load | Ramp to expected peak, hold | Do SLOs hold at peak? | 10–30 min |
| Stress | Ramp past peak until failure | Where/how does it break? | 20–60 min |
| Spike | Instant jump (e.g., 100→5000 RPS) | Does autoscaling survive a surge? | 5–15 min |
| Soak | Moderate, hold for hours/days | Leaks, pool exhaustion, creep? | 2–48 h |
| Capacity | Step-ramp, measure per step | Max RPS/replica → autoscale targets | 30–90 min |

**Load model:**

| Model | k6 executor | Locust | Latency validity |
|-------|-------------|--------|------------------|
| Closed (VU-based) | `ramping-vus`, `constant-vus` | default | Hides tail under saturation (coordinated omission) |
| Open (arrival-rate) | `constant-arrival-rate`, `ramping-arrival-rate` | `--constant-rate` shapes | Correct — measures true tail latency |

**Tool comparison:**

| Tool | Language | Engine | Distributed | Best for |
|------|----------|--------|-------------|----------|
| k6 | JS scripts | Go | k6-operator on K8s / k6 Cloud | CI gates, low footprint, modern DX |
| Locust | Python | Python (gevent) | Built-in master/worker | Python shops, complex logic, live UI |
| Gatling | Scala DSL | JVM/Akka | Gatling Enterprise | High throughput per node, JVM stacks |
| JMeter | XML/GUI | JVM | Distributed mode | Legacy, broad protocol support |
| Vegeta | CLI/Go | Go | Manual fan-out | Simple constant-rate HTTP |

---

## 5. Architecture Diagrams

Distributed load generation against a K8s-hosted service:

```
        ┌──────────────────────────────────────────────────────────┐
        │   k6-operator (or Locust master)  — orchestrates the run    │
        └───────────────┬──────────────┬──────────────┬──────────────┘
                        │              │              │  split N VUs / arrival rate
                        v              v              v
                 ┌───────────┐  ┌───────────┐  ┌───────────┐
                 │ runner-1  │  │ runner-2  │  │ runner-3  │   (load generator pods,
                 │ 1/3 load  │  │ 1/3 load  │  │ 1/3 load  │    each well under its
                 └─────┬─────┘  └─────┬─────┘  └─────┬─────┘    own CPU/FD/port limits)
                       └──────────────┼──────────────┘
                                      │ constant arrival rate (open model)
                                      v
                        ┌──────────────────────────────┐
                        │  System Under Test (SUT)       │
                        │  20× app replicas behind ALB   │
                        │  HPA scales on CPU/RPS          │
                        │  prod-like instances + deps     │
                        └───────────────┬────────────────┘
                                        │ metrics
                                        v
                          Prometheus / Grafana  ── p50/p95/p99, error rate,
                                                    saturation, autoscale events
   Results (k6) ──> Prometheus remote write / CSV / threshold pass-fail ──> CI gate
```

Closed vs open model under saturation (why it matters):

```
  CLOSED (VU) model:                          OPEN (arrival-rate) model:
  100 VUs, each: send -> wait -> send         5000 req/s sent regardless of responses
  system slows to 2s/req                       system slows to 2s/req
   -> VUs naturally send FEWER requests         -> requests PILE UP in the queue
   -> reported p99 stays ~2s (looks fine!)      -> reported p99 climbs to 30s (the TRUTH)
   -> COORDINATED OMISSION hides the tail        -> real user experience captured
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 A k6 load test with SLO thresholds (open model)

```javascript
// load-test.js — constant arrival rate so latency is measured honestly
import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    peak_load: {
      executor: 'ramping-arrival-rate',   // OPEN model: drive RPS, not VUs
      startRate: 100,                       // req/s
      timeUnit: '1s',
      preAllocatedVUs: 500,                 // pool of VUs to send the arrivals
      maxVUs: 2000,
      stages: [
        { target: 100,  duration: '2m' },   // warm-up (discard from analysis)
        { target: 3000, duration: '5m' },   // ramp to peak
        { target: 3000, duration: '15m' },  // hold at peak — this is the measurement window
        { target: 0,    duration: '2m' },   // ramp down
      ],
    },
  },
  thresholds: {                             // <-- these turn the test into a CI GATE
    http_req_duration: ['p(95)<300', 'p(99)<800'],  // p95<300ms, p99<800ms
    http_req_failed:   ['rate<0.001'],               // <0.1% errors
  },
};

export default function () {
  const res = http.get('https://sut.staging.internal/api/orders');
  check(res, { 'status 200': (r) => r.status === 200 });
}
// Exit code is non-zero if ANY threshold fails -> CI build fails.
```

Run and gate:
```bash
k6 run load-test.js          # exit 0 = pass, non-zero = SLO violated -> fails the pipeline
```

### 6.2 Distributed k6 on Kubernetes (when one runner isn't enough)

A single k6 process saturates ~1 CPU and a finite number of sockets; to generate 50k+ RPS you split the run across pods with the **k6-operator**:

```yaml
apiVersion: k6.io/v1alpha1
kind: TestRun
metadata:
  name: orders-peak
spec:
  parallelism: 8                 # 8 runner pods, load split evenly
  script:
    configMap:
      name: load-test
      file: load-test.js
  runner:
    resources:
      requests: { cpu: "2", memory: 2Gi }
# Total arrival rate is divided across the 8 pods; results are aggregated.
```

### 6.3 Locust master/worker for the same job

```python
# locustfile.py
from locust import HttpUser, task, constant_throughput

class OrdersUser(HttpUser):
    wait_time = constant_throughput(1)   # 1 req/s per user (approx open model)
    @task
    def get_orders(self):
        with self.client.get("/api/orders", catch_response=True) as r:
            if r.status_code != 200:
                r.failure(f"status {r.status_code}")
```
```bash
# master coordinates, workers generate load (scale workers to out-scale the SUT):
locust -f locustfile.py --master &
locust -f locustfile.py --worker --master-host=localhost   # x N workers
```

### 6.4 Capacity test — finding max RPS per replica

Step-ramp and watch where p99 breaks the SLO; that step is your per-replica ceiling:

```
  Replicas pinned to 1 (HPA disabled), step the arrival rate:
   step  arrival   p99      errors   verdict
   1     200 RPS   85 ms    0%       ok
   2     400 RPS   140 ms   0%       ok
   3     600 RPS   310 ms   0.2%     near SLO edge (p99 SLO=300ms)
   4     800 RPS   1.9 s    4%       BROKEN — knee of the curve
  => sustainable ~550 RPS/replica. To serve 11k RPS peak: 11000/550 ≈ 20 replicas + headroom.
  => set HPA target so a replica runs at ~70% of 550 ≈ 385 RPS before adding a pod.
```

### 6.5 Wiring the gate into CI

```yaml
# GitHub Actions — perf gate on every PR to main
perf-test:
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v4
    - name: Deploy to ephemeral env
      run: ./deploy-preview.sh
    - name: Run k6 load test
      uses: grafana/k6-action@v0.3
      with:
        filename: load-test.js
      # Non-zero exit (threshold breach) fails this step -> PR cannot merge.
    - name: Compare to baseline
      run: ./compare-baseline.sh results.json baseline.json --max-p95-regression 10%
```

---

## 7. Real-World Examples

- **Grafana Labs** builds and dogfoods k6; their guidance on the open vs closed model and coordinated omission is the canonical reference, and k6 Cloud runs distributed tests for large customers.
- **Netflix** runs continuous performance testing and chaos/load in production-like conditions; they pioneered "squeeze testing" — steadily increasing traffic to a single instance/cluster to find its breaking point and set autoscaling limits.
- **Shopify** publicly load-tests for Black Friday/Cyber Monday at enormous scale, running game-day simulations of peak flash-sale traffic months ahead to validate capacity and autoscaling.
- **Locust** is widely used (originated at ESN/used at companies like Battlelog/DICE); its Python-native model makes it popular where test logic is complex.
- **Google's SRE practice** formalizes load testing as part of capacity planning and "stress testing to find breaking points" — the SRE book treats knowing your breaking point as a reliability prerequisite.

---

## 8. Tradeoffs

| Decision | Option A | Option B | Guidance |
|----------|----------|----------|----------|
| Tool | k6 | Locust | k6 for CI/low-footprint/modern DX; Locust for Python logic + live UI |
| Load model | Closed (VU) | Open (arrival-rate) | Open for latency truth; closed only to mimic fixed-concurrency clients |
| Where | Staging/ephemeral | Production (with care) | Staging for CI gates; prod load (squeeze/dark) for true behavior |
| Distribution | Single runner | Distributed (operator/master-worker) | Distribute once you can't generate enough load from one box |
| Cadence | Pre-release only | Continuous in CI | Continuous catches regressions early; pre-release misses gradual creep |

| Average latency | Percentile latency |
|-----------------|--------------------|
| Hides tail; one slow 1% invisible | p95/p99/p99.9 show what users feel |
| Misleading under saturation | Reveals the knee of the curve |
| Easy to game | The number SLOs are written against |

---

## 9. When to Use / When NOT to Use

**Run performance/load tests when:**
- Before any high-traffic event (sale, launch, marketing spike) — validate capacity and autoscaling.
- Defining or verifying SLOs and autoscaling targets — you need a measured RPS/replica.
- After architectural changes (new dependency, caching layer, DB migration) that could shift performance.
- Continuously in CI to catch regressions on every change.
- Hunting elusive production issues (leaks, pool exhaustion) reproducible only under sustained load.

**Don't over-invest when:**
- A trivial low-traffic internal tool — a smoke test is enough; full capacity testing is wasted effort.
- You can't make the environment production-like — results from a 1-replica staging box mislead more than they inform; fix the environment first.
- The bottleneck is clearly algorithmic — profile the code ([`../../java/performance_and_tuning/`](../../java/performance_and_tuning/)) instead of black-box load testing.
- You'd test against shared prod dependencies you'd overload — isolate or mock first.

---

## 10. Common Pitfalls

**Pitfall 1: Closed-model load test hides the latency tail (coordinated omission).**

```javascript
// BROKEN: VU-based closed model. When the SUT slows, VUs naturally send fewer
// requests, so the tool never issues the requests that would have queued.
// Reported p99 looks healthy while real users time out.
export const options = {
  vus: 100,
  duration: '10m',     // 100 users, each waits for a response before the next
};
```

```javascript
// FIX: constant arrival rate (open model) keeps sending at a fixed RPS
// regardless of how slow responses are -> requests pile up -> the TRUE p99 is measured.
export const options = {
  scenarios: { peak: {
    executor: 'constant-arrival-rate',
    rate: 3000, timeUnit: '1s',
    duration: '10m',
    preAllocatedVUs: 500, maxVUs: 2000,
  }},
};
```
The broken version once reported a p99 of 180ms while production users saw 6-second timeouts — the test was lying because of coordinated omission.

**Pitfall 2: The load generator is the bottleneck, not the SUT.** A single runner maxes its CPU, exhausts ephemeral ports (~28k by default), or saturates its NIC, so throughput plateaus — and the team concludes the *service* can't go faster. Always monitor the generator's own CPU/FD/ports; distribute when it saturates.

**Pitfall 3: Reporting averages.** "Average latency 60ms" with a p99 of 3s ships a service that's broken for 1% of users (millions of requests/day). Always report and gate on percentiles.

**Pitfall 4: No warm-up window.** Measuring from t=0 includes JIT compilation, cold caches, empty connection pools, and the autoscaler still ramping — inflating latency and hiding steady-state truth. Discard the warm-up; measure the hold phase.

**Pitfall 5: Testing a non-production-like environment.** A 1-replica, undersized staging box with mocked dependencies tells you nothing about a 20-replica prod deployment hitting a real database. Either match prod or clearly caveat the results.

**Pitfall 6: Tests that don't gate.** A beautiful load-test dashboard nobody acts on lets regressions ship. Wire thresholds into CI so a regression *fails the build* — otherwise the test is theater.

---

## 11. Technologies & Tools

| Tool | Category | Notes |
|------|----------|-------|
| k6 | Load testing | Go engine, JS scripts, thresholds-as-gates, Prometheus output, k6-operator |
| Locust | Load testing | Python, gevent, master/worker distributed, live web UI |
| Gatling | Load testing | Scala DSL, high per-node throughput, JVM stacks |
| JMeter | Load testing | Java, GUI, broad protocols; legacy but ubiquitous |
| Vegeta | Load testing | Go CLI, constant-rate HTTP, scriptable |
| k6-operator | Distributed runs | Splits a k6 test across N runner pods on K8s |
| Prometheus + Grafana | Result/system metrics | Correlate generated load with SUT saturation/latency |
| Grafana k6 Cloud | Managed load | Large distributed runs, geo-distributed generators |
| Argo Rollouts / Flagger | Perf-gated delivery | Use load-test metrics as canary analysis gates |

**Cloud-native load test infra:**

| Capability | AWS | GCP | Azure |
|-----------|-----|-----|-------|
| Managed load testing | Distributed Load Testing solution (Fargate) | Cloud Load Testing (via tools) | Azure Load Testing (managed JMeter/k6) |
| Run generators on | ECS/Fargate, EKS | GKE, Cloud Run | AKS, Container Apps |
| Result metrics | CloudWatch / AMP | Cloud Monitoring | Azure Monitor |

---

## 12. Interview Questions with Answers

**What's the difference between load, stress, spike, and soak tests?**
Each answers a different question. A **load test** drives expected/peak traffic and verifies SLOs (latency, error rate) hold — "are we good for normal peak?" A **stress test** pushes *past* peak until something breaks, to find the breaking point and observe *how* it fails (graceful degradation vs cascading collapse). A **spike test** applies a sudden sharp jump (e.g., 100→5000 RPS in seconds) to verify autoscaling reacts in time and the system survives the transient. A **soak (endurance) test** holds moderate load for hours or days to surface slow problems — memory leaks, connection-pool exhaustion, disk fill — that short tests never reveal. Running "a load test" without picking the type is a common mistake; the load profile must match the question.

**Why measure percentiles instead of average latency?**
Because users experience the tail, not the mean. An average of 60ms can hide a p99 of 3 seconds — meaning 1% of requests (often millions/day, and disproportionately your power users with the most data) get a terrible experience. Averages are also easily skewed: a handful of fast cache hits drag the mean down while real failures hide in the tail. SLOs are written against percentiles (p95/p99/p99.9) precisely because they describe the worst experience a defined fraction of users tolerate. You report and gate on percentiles; the average is nearly useless for reliability decisions.

**Explain coordinated omission and how you avoid it.**
Coordinated omission is a measurement bug in closed-model (VU-based) load tests: each virtual user sends a request, waits for the response, then sends the next. When the system stalls, the VUs *also* stall — so the tool simply never issues the backlog of requests that would have piled up, and therefore never measures the long latencies those requests would have suffered. The reported p99 looks fine while real users (who keep arriving regardless) are timing out. You avoid it by using an **open model** — a constant arrival rate executor (`constant-arrival-rate`/`ramping-arrival-rate` in k6) that sends a fixed RPS independent of response time, so requests queue up and the true tail latency is captured. It's the single most important correctness concept in load testing.

**The team says "our service maxes out at 2000 RPS" but you suspect the test is wrong. How do you check?**
First suspect the **load generator**, not the service. A single runner box hits its own limits — CPU saturation, ephemeral port exhaustion (~28k ports by default), open file descriptors, or NIC bandwidth — and plateaus, making it *look* like the service can't go faster. I'd monitor the generator's own CPU, FDs, socket count, and network during the test; if any is maxed, the generator is the bottleneck. The fix is distributed load generation (k6-operator or Locust master/worker across multiple pods/machines), each well under its own limits, with load split across them. Only once the generators have clear headroom can you trust that 2000 RPS is the *service's* ceiling.

**Open model vs closed model — when would you actually want closed?**
The open model (constant arrival rate) is the default for latency truth because it mimics real users who keep arriving regardless of how slow you are. But the closed model (fixed virtual users) is correct when you're modeling a system with genuinely *fixed concurrency* — e.g., a fixed pool of backend worker threads, a batch job with a set number of parallel workers, or a client with a hard connection-pool cap. In those cases the real world *does* throttle itself to N in-flight, and a closed model with N VUs accurately represents it. The mistake is using closed-model to measure user-facing API latency under saturation, where it produces coordinated omission.

**How do you derive autoscaling targets from a load test?**
Run a **capacity test**: pin replicas to 1 (disable the HPA), step the arrival rate up, and record p99 and error rate at each step. Find the "knee" — the load just before p99 breaks the SLO; that's your max sustainable RPS per replica. Then set the HPA target so a replica runs at ~60–70% of that ceiling, leaving headroom for the scale-up lag (an HPA takes ~15s to sync plus pod start time). For peak capacity, divide peak RPS by the per-replica sustainable rate and add headroom: 11,000 RPS ÷ 550/replica ≈ 20 replicas, provision ~25. This turns autoscaling from a guess into a measured number, and feeds directly into cost models.

**How do you wire a performance test into CI as a gate?**
Define **thresholds** in the test (k6: `http_req_duration: ['p(95)<300']`, `http_req_failed: ['rate<0.001']`) so the tool exits non-zero when an SLO is breached, which fails the pipeline step. The flow: PR opens → deploy to an ephemeral/preview environment → run the load test against it → thresholds pass/fail the build → optionally compare against a stored baseline and fail on regression beyond a percentage (e.g., p95 worse by >10%). The key principle is that a test that doesn't gate is just a dashboard — to prevent regressions it must be able to *block the merge*. You keep the gate test short (a few minutes, focused) and run longer soak/stress tests on a schedule rather than per-PR.

**Why is a warm-up period important, and what do you do with it?**
The first minute or so of a test is unrepresentative: JIT compilers haven't optimized hot paths, caches are cold, connection pools are empty and filling, and the autoscaler is still ramping replicas. Measuring from t=0 inflates latency and pollutes your percentiles with transient cold-start behavior that doesn't reflect steady state. So you include an explicit warm-up stage (ramp to load, hold briefly) and then **discard it from analysis**, measuring only the steady-state "hold at peak" window. This is also why a 30-second test is nearly worthless — it's almost all warm-up.

**What does a soak test catch that a load test doesn't?**
Slow, cumulative problems that only manifest over time: memory leaks (heap creeping up until OOM after 6 hours), connection-pool or file-descriptor leaks (the pool slowly exhausts and requests start failing), disk fill (logs/temp files accumulating), cache unbounded growth, and database connection churn. A 15-minute load test passes cleanly because the leak hasn't accumulated yet; the 24-hour soak reveals the heap graph climbing steadily toward a cliff. Soak tests are how you catch the "it works fine in load tests but falls over after 2 days in prod" class of bug, which is otherwise found by customers.

**How would you load-test safely against production?**
With great care, because you can hurt real users. Techniques: **dark traffic / shadowing** (mirror real requests to a parallel test stack that doesn't affect users), **squeeze testing** (slowly raise real or synthetic traffic to a single instance/canary to find its limit while able to abort instantly), strict **blast-radius limits** (rate caps, circuit breakers, a kill switch), running during low-traffic windows, isolating test data so you don't corrupt real records, and tagging synthetic traffic so it's excluded from business metrics. Netflix's squeeze testing is the canonical pattern. The default should still be a production-*like* staging environment; prod testing is for behaviors you genuinely can't reproduce elsewhere.

**k6 vs Locust — how do you choose?**
k6 has a Go engine with JavaScript test scripts: very low resource footprint (high RPS per runner), first-class thresholds-as-CI-gates, Prometheus output, and a k6-operator for distributed K8s runs — it's the modern default for CI-integrated performance gates. Locust is pure Python with a gevent engine: tests are Python so you can express complex logic and reuse app code, it has built-in master/worker distribution and a live web UI, and it suits Python shops. Choose k6 for lightweight, scriptable, CI-gated tests and when footprint matters; choose Locust when your team is Python-centric, your test logic is complex, or you want the interactive UI to explore behavior live. Both support distributed generation; k6 generally needs fewer machines for the same load.

**A load test passes in CI but the service still falls over in production at the same RPS. What could explain the gap?**
Several common causes: (1) the **test environment wasn't production-like** — fewer replicas, smaller instances, or mocked dependencies that hid the real bottleneck (often the database); (2) **coordinated omission** in a closed-model test reported a falsely good p99; (3) the test hit a **cache that's cold or absent in prod** (or vice versa), or used non-representative data/keys so it didn't exercise the slow paths; (4) **stateful effects** the short test didn't trigger — connection-pool exhaustion or a leak that only appears under sustained real traffic (a soak issue); (5) **traffic shape** — real traffic is bursty and multi-endpoint while the test was a smooth single-endpoint stream. The fix is making the test environment, data, traffic shape, and load model faithfully represent production, and adding soak coverage for time-dependent failures.

**Why does test-data realism matter so much, and what goes wrong with naive synthetic data?**
Because the system's performance is data-dependent in ways that synthetic data hides. Hitting the same record or key repeatedly makes every request a cache hit, so the test reports latency that production (with a cold, diverse working set) never achieves. A uniform key distribution misses **hot keys** — the celebrity user or popular product that creates a partition/lock/shard hotspot in production. Sequential or unrealistic IDs can produce index access patterns that don't match real queries. Tiny test datasets fit in memory while production tables spill to disk. The fixes: replay anonymized production traffic where possible, or generate data with realistic cardinality and skew (Zipfian, not uniform), size the dataset to production scale, and exercise the full breadth of endpoints in production-like proportions. A load test against unrealistic data is precise but inaccurate — it measures something, just not the thing you'll ship.

---

## 13. Best Practices

- **Pick the test type deliberately** (load / stress / spike / soak / capacity) — match the load profile to the question.
- **Always use an open model (constant arrival rate)** for user-facing latency truth; avoid coordinated omission.
- **Report and gate on percentiles** (p95/p99/p99.9), never averages.
- **Verify the load generator has headroom**; distribute (k6-operator / Locust workers) before concluding the SUT is the limit.
- **Test against production-like infra and data**, isolated from real traffic.
- **Warm up, then measure steady state**; discard the ramp/JIT/cache-fill window.
- **Derive autoscaling targets from a capacity test** (knee of the p99 curve), not from guesses.
- **Wire thresholds into CI so regressions fail the build**; compare against a stored baseline.
- **Run soak tests on a schedule** to catch leaks; run quick smoke/load gates per-PR.
- **Correlate load with SUT metrics** (Prometheus) so you see *where* it saturated (CPU, DB, pool), not just that it did.

---

## 14. Case Study

**Scenario:** A ticketing platform expects a 50× flash-sale spike when a major concert goes on sale at 10:00 a.m. Their CI had a "load test" that always passed, yet the last on-sale event collapsed: 70% error rate for 18 minutes, ~$400k in lost sales and refunds. The platform team is told to make the next on-sale survivable.

**Diagnosis:**
- The existing CI test used a **closed VU model** (100 VUs, 5-min run) and reported p99 = 150ms — but this was coordinated omission; under real saturation the true p99 was multiple seconds.
- The test ran against a **2-replica staging** environment with a mocked database; prod runs 30 replicas against a real Aurora cluster whose connection pool was the actual bottleneck.
- There was **no spike test** — autoscaling (HPA, 15s sync + ~40s pod start) couldn't add replicas fast enough for an instant 50× jump, so the existing pods saturated and the DB connection pool exhausted.

```
BEFORE (broken):                          AFTER (fixed):
closed model, 100 VUs, mocked DB          open model (arrival-rate), real Aurora
2-replica staging                          prod-like 30-replica + read replicas
p99 "150ms" (a lie)                        true p99 measured: knee at 550 RPS/replica
no spike test, HPA too slow                spike test + pre-scaling + queue/waiting-room
on-sale: 70% errors, 18 min, $400k lost    on-sale: <0.5% errors, SLO held
```

**Fix 1 — honest open-model capacity test (real deps):**
```javascript
export const options = {
  scenarios: { capacity: {
    executor: 'ramping-arrival-rate',
    startRate: 200, timeUnit: '1s',
    preAllocatedVUs: 1000, maxVUs: 5000,
    stages: [
      { target: 200,  duration: '2m' },   // warm-up (discarded)
      { target: 600,  duration: '5m' },   // found the knee at ~550 RPS/replica
      { target: 12000,duration: '5m' },   // ramp to flash-sale peak
      { target: 12000,duration: '15m' },  // hold
    ],
  }},
  thresholds: { http_req_duration: ['p(99)<800'], http_req_failed: ['rate<0.005'] },
};
// Run distributed via k6-operator (parallelism: 12) so generators aren't the bottleneck.
```

**Fix 2 — spike test exposed the autoscaling gap; pre-scale + waiting room:**
```
Spike test result: 100 -> 12000 RPS instant.
 - HPA reaction (15s sync + 40s pod start) too slow; existing pods saturate first.
 - Aurora connection pool (default) exhausted -> the real failure.
Fixes:
 - PRE-SCALE: schedule 30 replicas at 09:50 (CronJob/HPA minReplicas bump) before on-sale.
 - Add a waiting-room/queue (e.g., a token-bucket admission gate) to flatten the 50x spike.
 - Tune DB connection pool + add read replicas; cap per-pod pool so 30 pods don't exceed DB max.
```

**Broken DB pool config (the actual root cause) and the fix:**
```yaml
# BROKEN: each of 30 pods opens up to 50 DB connections = 1500, but Aurora
# max_connections was 1000 -> connection refused under load -> cascading 500s.
env:
  - { name: DB_POOL_MAX, value: "50" }   # 30 x 50 = 1500 > 1000 DB cap
```
```yaml
# FIX: bound total connections below the DB cap, add a proxy (RDS Proxy) to multiplex.
env:
  - { name: DB_POOL_MAX, value: "25" }   # 30 x 25 = 750 < 1000, with RDS Proxy pooling
```

**Result:** The capacity test revealed the real per-replica ceiling (~550 RPS) and the DB-pool root cause that the old mocked test had completely hidden. With pre-scaling, a waiting-room admission gate to flatten the spike, RDS Proxy + a bounded connection pool, and an honest open-model test gating CI, the next on-sale handled the 50× spike with <0.5% errors and the latency SLO held throughout — versus 70% errors and $400k lost previously. The soak test added afterward also caught a connection leak that would have degraded the system after ~5 hours of sustained sale traffic.

**Discussion questions:**
1. The old test "passed" for months while hiding a fatal flaw. What review process would have caught that the test used a closed model against mocked dependencies?
2. Pre-scaling wastes money before the spike. How would you balance pre-scale cost against the autoscaler's reaction-time gap?
3. A waiting room improves survivability but hurts UX (users wait). When is admission control the right tradeoff, and how do you tune it?
4. How would you run this capacity test continuously without it becoming flaky or so expensive that the team disables it?

---

**See also:** [sre_principles_and_slos](../sre_principles_and_slos/) · [kubernetes_scheduling_and_autoscaling](../kubernetes_scheduling_and_autoscaling/) · [cloud_cost_optimization_finops](../cloud_cost_optimization_finops/) · [deployment_strategies](../deployment_strategies/) · [observability_metrics_prometheus](../observability_metrics_prometheus/) · [`../../java/performance_and_tuning/`](../../java/performance_and_tuning/) · [`../../python/performance_and_profiling/`](../../python/performance_and_profiling/) · [`../../hld/`](../../hld/)
