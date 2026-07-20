# Load and Performance Testing

## 1. Concept Overview

Performance testing is the practice of applying synthetic load to a system to measure behavior under known conditions — throughput, latency percentiles, error rate, and resource utilization. Load testing validates behavior under expected production load. Stress testing finds the breaking point. Soak testing reveals slow degradation over hours. Spike testing measures resilience to sudden traffic bursts. Performance tests answer "how does the system behave?" while functional tests answer "does the system behave correctly?"

---

## 2. Intuition

You would not launch a bridge without testing it under load. You would apply increasing weight, watch where it flexes, and find the failure mode before it happens with real traffic. The same principle applies to APIs. A service that handles 100 requests per second in dev may collapse at 1000 in production because of an unindexed database query that scales O(n) with data size. Performance testing finds these cliffs before users do.

Key insight: performance problems are almost always caused by a small number of bottlenecks — a missing index, connection pool exhaustion, an O(n²) loop on a critical path. The goal is to find and fix these rather than over-provision hardware.

---

## 3. Core Principles

- **Test in isolation**: performance test a specific service or endpoint, not the entire system, to identify the specific bottleneck
- **Warm up the JVM**: first 1-2 minutes of a JVM process involve JIT compilation — do not include ramp-up period in measurements
- **Use realistic data**: production-representative data sizes, access patterns, and distribution (Pareto: 20% of users generate 80% of traffic)
- **Measure percentiles**: p50, p95, p99, p999 — averages hide outliers that affect 1 in 100 or 1 in 1000 users
- **Automate in CI**: catch performance regressions before they merge

---

## 4. Types / Architectures / Strategies

| Test Type | Load Pattern | Goal | Duration |
|-----------|-------------|------|---------|
| Load | Gradual ramp to expected peak | Verify behavior at normal load | 30 min - 2 hours |
| Stress | Ramp past capacity | Find breaking point, failure mode | 1-4 hours |
| Soak | Sustained expected load | Memory leaks, slow degradation | 8-24 hours |
| Spike | Sudden 10x traffic burst | Resilience, auto-scaling behavior | 30 min |
| Volume | Normal RPS with huge data sets | Query performance at scale | Varies |
| Breakpoint | Ramp until error rate > 1% | Find maximum sustainable RPS | 1-2 hours |

---

## 5. Architecture Diagrams

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    k6("k6 engine") --> vus(["Virtual Users<br/>VUs"])
    k6 --> scen["Scenarios<br/>arrival patterns"]
    k6 --> thr{"Thresholds<br/>pass / fail"}
    k6 --> chk["Checks<br/>per-request asserts"]
    k6 --> met["Metrics<br/>duration · failed ·<br/>vus · iterations"]

    class k6 mathOp
    class vus io
    class scen,chk req
    class thr lossN
    class met base
```

*k6 separates who arrives (VUs) and how (Scenarios) from what passes: Checks count per-request assertions without failing fast, while Thresholds are the actual pass/fail gate. Both read from the same Metrics stream — http_req_duration, http_req_failed, vus, iterations.*

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    gen(["k6 / Gatling<br/>VUs drive load"]) --> lb{"Load Balancer"}
    lb --> i1["Instance 1"]
    lb --> i2["Instance 2"]
    i1 --> pg["PostgreSQL"]
    i1 --> rc["Redis"]
    i2 --> pg
    i2 --> rc

    class gen req
    class lb mathOp
    class i1,i2 train
    class pg,rc base
```

*The load generator's VUs drive traffic through the load balancer to both instances, which share a single PostgreSQL and Redis. Monitor four layers together during the run: API (p50/p95/p99/p999, error rate, RPS), JVM (GC pause, heap, thread count), DB (connections, slow query log, lock waits), and System (CPU%, memory, network/disk I/O).*

---

## 6. How It Works — Detailed Mechanics

### k6 Load Test Script

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
const orderCreateRate = new Rate('order_create_success');
const orderCreateDuration = new Trend('order_create_duration');

export const options = {
  // Scenario: ramp up, steady state, ramp down
  stages: [
    { duration: '2m', target: 50 },   // ramp up to 50 VUs
    { duration: '10m', target: 50 },  // stay at 50 VUs
    { duration: '2m', target: 100 },  // spike to 100 VUs
    { duration: '5m', target: 100 },  // stay at 100 VUs
    { duration: '2m', target: 0 },    // ramp down
  ],

  // Pass/fail criteria — build fails if violated
  thresholds: {
    'http_req_duration': ['p(99)<200', 'p(95)<100'],  // p99 < 200ms, p95 < 100ms
    'http_req_failed': ['rate<0.01'],                  // error rate < 1%
    'order_create_success': ['rate>0.99'],             // 99% of orders succeed
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Setup: runs once before all VUs, returns data shared across VUs
export function setup() {
  const loginRes = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({
    username: 'testuser',
    password: 'testpass',
  }), { headers: { 'Content-Type': 'application/json' } });
  return { token: loginRes.json('token') };
}

// Default function: runs for each VU iteration
export default function(data) {
  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${data.token}`,
  };

  // Create order
  const createStart = new Date();
  const createRes = http.post(`${BASE_URL}/api/orders`, JSON.stringify({
    userId: `user-${__VU}`,  // __VU = virtual user number
    items: [{ sku: 'SKU-001', quantity: 1 }],
  }), { headers });

  orderCreateDuration.add(new Date() - createStart);

  const createOk = check(createRes, {
    'order created': (r) => r.status === 201,
    'has order id': (r) => r.json('id') !== null,
  });
  orderCreateRate.add(createOk);

  if (createOk) {
    const orderId = createRes.json('id');

    // Get order
    const getRes = http.get(`${BASE_URL}/api/orders/${orderId}`, { headers });
    check(getRes, {
      'order retrieved': (r) => r.status === 200,
      'order status correct': (r) => r.json('status') === 'PENDING',
    });
  }

  sleep(1); // think time between iterations
}
```

### k6 Constant Arrival Rate (avoids coordinated omission)

```javascript
export const options = {
  scenarios: {
    constant_rate: {
      executor: 'constant-arrival-rate',
      rate: 100,          // 100 iterations per second
      timeUnit: '1s',
      duration: '10m',
      preAllocatedVUs: 150,  // pre-allocate VUs for requests
      maxVUs: 300,           // max VUs if preAllocated exhausted
    },
  },
};
// Unlike constant-vus, this sends 100 req/s regardless of response time
// When system slows, k6 will spin up more VUs to maintain the rate
// This reflects real-world traffic (arrivals don't wait for prior responses)
```

### Gatling Simulation

```scala
class OrderSimulation extends Simulation {

  val httpProtocol = http
    .baseUrl("http://localhost:8080")
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")

  val feeder = csv("orders.csv").random  // CSV with userId, sku columns

  val createOrderScenario = scenario("Create Order")
    .feed(feeder)
    .exec(
      http("Create Order")
        .post("/api/orders")
        .body(StringBody("""{"userId":"${userId}","items":[{"sku":"${sku}","quantity":1}]}"""))
        .check(status.is(201))
        .check(jsonPath("$.id").saveAs("orderId"))
    )
    .pause(1)  // think time
    .exec(
      http("Get Order")
        .get("/api/orders/${orderId}")
        .check(status.is(200))
        .check(jsonPath("$.status").is("PENDING"))
    )

  setUp(
    createOrderScenario.inject(
      rampUsersPerSec(10).to(100).during(2.minutes),
      constantUsersPerSec(100).during(10.minutes)
    )
  ).protocols(httpProtocol)
    .assertions(
      global.responseTime.percentile3.lt(200),  // p99 < 200ms
      global.failedRequests.percent.lt(1)        // error rate < 1%
    )
}
```

### Percentile Analysis

```mermaid
xychart-beta
    title "Response Time Distribution — 100K Requests"
    x-axis [p50, p75, p90, p95, p99, p999, avg]
    y-axis "Latency (ms)" 0 --> 2200
    bar [45, 78, 120, 180, 450, 2100, 62]
```

*Latency climbs non-linearly toward the tail: p50-p90 stay under 120ms while p99 (450ms) and especially p999 (2100ms) expose the 1-in-100 and 1-in-1000 worst cases that the 62ms average hides entirely — both p95 and p99 still clear their 200ms / 500ms SLOs.*

```
Rule: NEVER alert or SLO on averages.
      A p999 of 2100ms means 100 users/second (6,000/minute) at 100K RPS experience
      > 2 second latency.

Coordinated omission check:
  If test tool used 50 VUs and each waited for response before next request:
  - When system slows to 450ms p99, 50 VUs * (1000ms/450ms) = ~111 RPS (not 1000 RPS)
  - Latency histogram only has samples at those 111 req/s, missing the queued-up requests
  - Fix: use constant-arrival-rate in k6 or open-model injection in Gatling
```

**What this actually says.** "A closed-model load generator cannot push harder than the
server lets it, so the moment the server slows down the test quietly stops testing the load
you asked for." The throughput a VU pool can produce is fixed by Little's Law rearranged as
`throughput = VUs / response_time` — response time is in the denominator, so it is the server,
not your script, that decides the arrival rate.

| Symbol | What it is |
|--------|------------|
| `VUs` | Virtual users. Each one holds a single in-flight request and blocks until it returns |
| `response_time` | Server-side time per request, in seconds. The thing under test |
| `VUs / response_time` | Achieved RPS. What the tool actually generated, not what you configured |
| target RPS | What you *intended* to send. Only equals the achieved rate when the server is fast |

**Walk one example.** Same 50-VU pool, one healthy server and one degraded server:

```
                     VUs   response time   achieved RPS = VUs / time    vs 1000 target
  healthy server      50       50 ms           50 / 0.050 = 1000            met
  degraded server     50      450 ms           50 / 0.450 =  111            11% of target

  Load actually applied dropped 9x -- but the config file never changed.

  Percentile effect: the histogram now holds ~111 samples/s instead of ~1000/s,
  and every request that production WOULD have sent during the slow window
  simply never exists as a sample. The tail you most need to measure is the
  exact tail you stopped recording.
```

That is coordinated omission: the load generator "coordinates" with the server's slowness
instead of ignoring it the way real users do. Real internet traffic is open-model — a user
clicking Buy does not wait for someone else's request to finish first — so an arrival-rate
executor keeps firing at 1000 RPS and lets the queue build, which is what production does.

**Read it like this.** The p999 rule above says "0.1% of requests are worse than 2100ms" —
multiply that fraction by real traffic to turn a percentile into a headcount.

| Symbol | What it is |
|--------|------------|
| `p999` | The latency 99.9% of requests beat. 1 request in 1,000 is slower |
| `1 - 0.999` | The affected fraction, `0.001` |
| `RPS x 0.001` | How many users per second land in that tail |

**Walk one example.** Turning the 2100ms p999 into affected users at 100K RPS:

```
  affected fraction = 1 - 0.999          = 0.001
  affected per second = 100,000 x 0.001  = 100 requests/s
  affected per minute = 100 x 60         = 6,000 requests/min

  Compare against the average, which reports 62 ms:
    p999 / avg = 2100 / 62 = 33.9x

  The average is 34x more optimistic than what 6,000 users a minute experience.
```

This is why an SLO written on the average passes while the service is visibly broken for
thousands of people a minute. The percentile is not a statistical nicety — it is a headcount.

### Identifying Bottlenecks

```bash
# During load test, correlate these signals:

# 1. CPU-bound: flame graph shows compute (sorting, serialization, regex)
java -agentpath:/async-profiler/libasyncProfiler.so=start,event=cpu,file=/tmp/profile.html \
  -jar service.jar
# View /tmp/profile.html — wide frames at the top = hot code paths

# 2. DB-bound: check slow query log
# PostgreSQL: log queries > 100ms
SET log_min_duration_statement = 100;
SELECT query, calls, mean_exec_time, stddev_exec_time
FROM pg_stat_statements
ORDER BY mean_exec_time DESC LIMIT 20;

# 3. Connection pool exhaustion: HikariCP metrics
# management.metrics.enable.hikaricp=true
# Alert: hikaricp.connections.pending > 0 for > 5 seconds

# 4. GC pressure: JVM flags during load test
-Xlog:gc*:file=/tmp/gc.log:time,uptime:filecount=5,filesize=20m
# Look for: long GC pauses (> 200ms), frequent full GCs, allocation rate > 500MB/s
```

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    start(["p99 latency<br/>climbs under load"]) --> c1{"Flame graph:<br/>wide hot frames?"}
    c1 -->|"yes"| cpu["CPU-bound<br/>hot code path"]
    c1 -->|"no"| c2{"Slow query log /<br/>pg_stat_statements hot?"}
    c2 -->|"yes"| db["DB-bound<br/>missing index / seq scan"]
    c2 -->|"no"| c3{"HikariCP pending<br/>connections above zero?"}
    c3 -->|"yes"| pool["Pool exhaustion<br/>too few connections"]
    c3 -->|"no"| c4{"GC pause over 200ms<br/>or frequent full GCs?"}
    c4 -->|"yes"| gc["GC-bound<br/>allocation pressure"]

    class start io
    class c1,c2,c3,c4 mathOp
    class cpu,db,pool,gc lossN
```

*Each "no" pushes the search to the next signal — flame graph, then slow-query log, then HikariCP pool metrics, then GC log — the same order as the four checks above; the first "yes" is usually the real bottleneck.*

---

## 7. Real-World Examples

- **Twitter**: migrated from Ruby to Java partly after performance testing showed the JVM handles 10x more requests per server at same latency; now handles 600K TPS at peak
- **LinkedIn**: performance regression testing catches p99 regressions > 10% before merge; reduced production incidents by 40%
- **Stripe**: runs 24/7 soak tests in a shadow environment; found a memory leak in a JSON parser that only appeared after 6 hours under load

---

## 8. Tradeoffs

| Tool | Language | Strengths | Weaknesses |
|------|----------|-----------|------------|
| k6 | JavaScript | Modern, CI-friendly, scenarios, cloud execution | No Java DSL |
| Gatling | Scala | Excellent reports, Scala DSL expressive, open-model | Scala learning curve |
| JMeter | Java/XML | Mature, GUI, wide plugin ecosystem | XML config verbose, GUI-driven |
| wrk2 | C | Extremely high RPS, constant rate, low overhead | Limited scripting |
| Locust | Python | Python scripting, distributed, real-time UI | Higher resource usage |

---

## 9. When to Use / When NOT to Use

Run load tests before: any major launch, after significant architectural changes, when adding new high-traffic endpoints, after database schema changes on hot tables.

Do NOT run load tests against production without traffic shadowing or canary isolation. Do NOT use load test results from a developer laptop — JVM on underpowered hardware will show different bottlenecks than production. Do NOT use a single-instance test environment for soak tests — horizontal scaling behavior will differ.

Use soak tests specifically for detecting: memory leaks (heap growing 1MB/hour × 168 hours = 168MB), connection leaks (connection pool fills over time), thread leaks, and database query plan degradation as tables grow.

---

## 10. Common Pitfalls

**Testing without warmup**: A team ran a 5-minute load test and reported p99 latency of 350ms. Production p99 was 80ms. The test included the first 2 minutes of JVM startup where the JIT had not yet compiled hot methods. Fix: run a 2-minute ramp-up period before collecting measurements, or use `--no-thresholds` for the ramp-up phase in k6.

**Using averages in SLOs**: An SLO defined as "average response time < 100ms" passed while p99 was 2 seconds. The average was pulled down by the 95% of fast requests, masking the 1-in-20 slow requests. Fix: always define SLOs on percentiles (p95 < 200ms, p99 < 500ms).

**Not reproducing production data distribution**: A load test used 100 users with clean databases. In production with 10 million orders, ORDER BY created_at on a 100M-row table caused a 5-second query. The load test never caught it. Fix: run performance tests with production-scale data volumes, ideally restored from a production snapshot.

**Capacity planning from single-instance tests**: A team saw 1 instance handled 1000 RPS at 60% CPU and concluded they needed 2 instances for 1500 RPS. In production, 2 instances handled only 1200 RPS because the database (a single instance) was the bottleneck at 600 QPS per app instance. Fix: performance test the entire stack under realistic architecture.

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| k6 | Modern load testing, scripted in JavaScript, CI-native |
| Gatling | Scala-based simulation, detailed HTML reports |
| JMeter | Traditional load testing, GUI + distributed mode |
| async-profiler | CPU/allocation flamegraphs during load tests |
| JFR (Java Flight Recorder) | JVM-level event recording during load tests |
| Prometheus + Grafana | Real-time metrics dashboards during load tests |
| pg_stat_statements | PostgreSQL query performance statistics |
| k6 Cloud | Distributed k6 execution and results storage |

---

## 12. Interview Questions with Answers

**Q: What is the difference between load testing and stress testing?**
Load testing validates system behavior at expected production load — you simulate the peak RPS you expect on a busy day and verify that latency and error rate meet SLOs. Stress testing pushes beyond capacity to find the breaking point and understand failure mode. Does the service fail fast (circuit breaker opens, returns 503 quickly), fail slowly (timeout after 30 seconds), or fail silently (returns incorrect data)? Stress tests inform capacity planning: if the service breaks at 3000 RPS and you expect 1000 RPS, you have a 3x headroom — healthy. If it breaks at 1200 RPS, you need more capacity.

**Q: What is the coordinated omission problem?**
When a load test uses a virtual-user (VU) model, each VU sends one request and waits for the response before sending the next. If the server slows down and takes 1 second per request, a 100-VU test only sends 100 RPS instead of the intended 1000 RPS. The slow period is under-sampled because fewer requests arrive. The latency histogram looks better than real production behavior where arrivals continue regardless of server speed. The fix is to use a constant arrival rate model (k6's `constant-arrival-rate` executor, Gatling's `constantUsersPerSec`) which maintains the target RPS even when the server is slow.

**Q: How do you identify a database bottleneck during a load test?**
Monitor connection pool metrics (active connections, pending connections) — if pending connections exceed 0 for sustained periods, the pool is exhausted. Check `pg_stat_activity` for long-running queries. Enable `pg_stat_statements` and query it for highest mean execution time. Check `pg_locks` for lock contention. Correlate GC pauses (JVM) vs DB query time (application timer) to determine if latency spikes are JVM-side or DB-side. A definitive signal: if adding more application instances does not improve throughput (RPS stays flat), the bottleneck is shared infrastructure (DB, Redis, external API).

**Q: What metrics should you track during a performance test?**
Application layer: request rate (RPS), latency percentiles (p50, p95, p99, p999), error rate by type (4xx vs 5xx), active concurrent connections. JVM layer: heap usage, GC pause duration and frequency, GC allocation rate (MB/s), thread states (blocked threads indicate lock contention). Database: active connections, query execution time, lock wait events, index hit rate (should be > 99%), I/O wait. Operating system: CPU utilization (> 80% = CPU bound), memory (swap = bad), network bandwidth, disk I/O latency. The combination tells you where the bottleneck is.

**Q: How do you implement performance regression detection in CI?**
Establish a baseline: run a 10-minute load test at fixed RPS (e.g., 500 RPS) on the main branch and record p50/p95/p99. On each PR, run the same test and compare. Fail the build if p99 regresses more than 10% vs baseline, or error rate increases by more than 0.1%. Store results in a time-series database (InfluxDB) with build metadata (commit SHA, branch, date). Gatling's CI plugin outputs a baseline comparison report. k6 threshold assertions fail the exit code so CI pipelines can detect failures natively. Run performance tests in a separate, dedicated environment with production-equivalent resources.

**Q: What is a soak test and what does it detect?**
A soak test runs a sustained load (typically 60-80% of peak capacity) for an extended period (8-24 hours). It detects slow degradation that only becomes visible over time: memory leaks (heap grows by 10MB/hour, eventually causes OOM after 10 hours), connection leaks (connection pool gradually fills as connections are not returned to pool), thread leaks (thread count grows monotonically), database file handle leaks, and performance cliffs where query plans degrade as table statistics change. Load tests miss these because they are too short. A service that passes a 30-minute load test may fail after 12 hours in production.

**Q: How do you interpret a flamegraph from a performance profile?**
A flamegraph shows the call stack of CPU samples. The x-axis is time (proportional width = % of CPU time spent in that method and its callees). The y-axis is stack depth. Wide frames at the top of the stack are hot code paths — the wider, the more CPU time spent. Find the widest frames that are application code (not JVM internals). If `JsonSerializer.serialize()` is 30% wide, JSON serialization is a bottleneck — consider a faster library (Jackson streaming API vs ObjectMapper). If `DatabaseConnectionPool.acquire()` is 20% wide, connection pool contention is the issue. If GC-related frames are wide, memory allocation is the problem.

**Q: What is your process for capacity planning from load test results?**
Run the breakpoint test to find the maximum sustainable RPS (where error rate first exceeds 1%). Calculate required headroom: if you expect 10K RPS peak and one instance handles 3K RPS at 60% CPU, you need ceil(10000/3000) = 4 instances, plus 20% spare = 5 instances. Account for: gradual traffic ramp (HPA needs 2-3 minutes to add instances), traffic skew (some instances may receive 2x average), headroom for unexpected spikes (provision for 150% of expected peak). Validate by running the load test against the capacity-planned cluster. Re-run quarterly or when traffic doubles.

**Q: What is the difference between an open workload model and a closed workload model in load testing?**
A closed model caps concurrency at a fixed number of virtual users that each wait for a response before sending the next request, while an open model generates arrivals at a fixed rate independent of response time. Real internet traffic is open — thousands of independent users each send a request whenever they want, with no coordination and no waiting for anyone else's response — which is why an open model, k6's `constant-arrival-rate` or Gatling's `constantUsersPerSec` from §6, more accurately simulates production. A closed model, k6's default `constant-vus` executor or JMeter's classic thread-group model, is easier to reason about and cheaper to run, but as the coordinated omission answer explains, it silently reduces effective throughput whenever the system under test slows down, because each VU blocks waiting rather than generating a new arrival. Use closed models for simple smoke tests, and open models any time the result will inform a real capacity or SLO decision.

**Q: How do preAllocatedVUs and maxVUs work together in k6's constant-arrival-rate executor?**
`preAllocatedVUs` reserves a starting pool of virtual users before the test begins, and `maxVUs` is the hard ceiling k6 will grow that pool to if the system under test slows down. In the scenario from §6 targeting 100 iterations per second, `preAllocatedVUs: 150` spins up 150 VUs upfront, sized above the naive 100 because if average response time creeps above 1 second, more than 100 concurrent VUs are needed to sustain 100 new iterations every second. If response times degrade further, k6 draws from the same pool up to `maxVUs: 300`; VUs beyond that ceiling cannot be created, and k6 reports dropped iterations rather than silently under-reporting the target rate the way a closed model would. Sizing `preAllocatedVUs` too low reintroduces a self-inflicted coordinated-omission artifact, so set `maxVUs` at 2-3x the naive VU estimate to leave headroom for a slow run.

**Q: How does an HDR histogram let a load testing tool compute accurate percentiles without storing every latency sample?**
An HDR histogram buckets latency values into a fixed set of ranges at a configurable precision, representing millions of samples with a bounded, tiny memory footprint instead of storing every raw value. Instead of appending every measured latency to a growing array, which needs real memory and a full sort to compute p99 across a long high-RPS run, a High Dynamic Range histogram pre-allocates buckets across the expected value range at a fixed number of significant digits, typically 3, and increments a bucket counter in constant time as each sample arrives. Computing any percentile is then a linear scan of a fixed-size bucket array rather than a sort of raw data, which is why Gatling and k6 both use histogram-based percentile computation internally. The tradeoff is that HDR histograms report a value within the bucket's precision band rather than the exact microsecond, which for SLO purposes like p99 under 200ms is more than accurate enough.

**Q: How do you apply Little's Law to size the number of virtual users needed for a load test?**
Little's Law states that concurrency equals arrival rate multiplied by average time in system, giving a direct formula for how many VUs a closed-model test needs. The formula is L = lambda times W, where L is concurrency (VUs needed), lambda is the target arrival rate, and W is average response time — to sustain 500 RPS against an endpoint with a 200ms average response time, you need 500 times 0.2, or 100 concurrent VUs continuously issuing requests, and under-provisioning below that number means the closed model cannot physically generate 500 RPS. This is also the fastest sanity check for a hidden coordinated-omission bug: if response time degrades from 200ms to 2s mid-test but VU count stayed fixed at 100, Little's Law says achieved throughput silently dropped from 500 RPS to roughly 50 RPS. That drop is exactly the artifact the constant-arrival-rate executor exists to prevent.

**Q: Why does JVM warmup take roughly one to two minutes, and what is actually happening during that window?**
The JIT compiler runs cold bytecode through the interpreter first and only compiles a method to optimized native code after it crosses an invocation threshold, so early requests run far slower than steady state. HotSpot's tiered compilation profiles each method as it interprets it, promoting hot methods first to C1, fast to compile and moderately optimized, and then, once a method crosses roughly 10,000-15,000 invocations, to C2, slow to compile but heavily optimized with inlining and escape analysis — a checkout endpoint's core path typically needs a few thousand real invocations before C2 engages, which at 50-100 RPS in a load test takes about 1-2 minutes to accumulate. The pitfall in §10, p99 of 350ms in test versus 80ms in production, is this exact effect: the test window included the interpreted and C1 phases and dragged the percentiles up. Beyond JIT, the same warmup window lets connection pools reach steady-state size and G1GC settle into its regular collection rhythm, which is why the first 1-2 minutes of any measurement window should be discarded.

**Q: How can an unrealistic cache hit rate in test data make a load test miss a production database overload?**
A load test that reuses a small set of test keys drives the cache hit rate artificially high, hiding how often production traffic actually falls through to the database. If a test script always requests the same 50 product IDs, Redis serves nearly every request from cache after the first few iterations, a 98-99% hit rate, while production traffic spans millions of distinct SKUs with a long tail where the real hit rate might be 70-80%. The test then reports excellent p99 latency and low DB load, but production sees 20-30% of requests fall through to Postgres, and if that miss traffic alone exceeds what the connection pool or query planner can sustain, the service degrades in a pattern the load test never surfaced. Fix: generate test key distributions that match production's actual access pattern, sampling real key frequencies rather than a small fixed set, and explicitly assert on cache hit rate as a test metric, not just latency.

**Q: How should k6 or Gatling threshold definitions map to a service's real SLOs, and what does abortOnFail add?**
Thresholds should mirror the exact SLO the service is held to in production, not a looser number picked just to make the test pass. If the production SLO is p99 under 200ms and error rate under 1%, the k6 `thresholds` block should assert `p(99)<200` and `rate<0.01` verbatim — a common mistake is setting the CI threshold looser to reduce flaky failures, which quietly lets a multi-fold regression merge because it still passes a diluted gate. Adding `abortOnFail: true` on a threshold makes k6 stop the run immediately once that threshold is breached rather than running the full configured duration, which matters for a fast-feedback CI pipeline since an obviously failing build does not need to burn the remaining minutes of a scripted run. The exit code from a failed threshold is what actually blocks the pipeline, so wiring the process exit code into the deploy gate is what turns a load test script into an enforced quality gate rather than a report nobody reads.

**Q: How can Little's Law reveal that a database connection pool, not application code, is capping load test throughput?**
If measured throughput plateaus below what Little's Law predicts for a given concurrency and latency, the bottleneck is a fixed-size resource pool, not the code path. Rearranging L = lambda times W to lambda = L divided by W: a HikariCP pool sized at 20 connections with each query taking 10ms supports at most 20 divided by 0.01, or 2000 queries per second from that pool alone, regardless of how many application instances or VUs are generating load. If a load test with 200 VUs and a measured 15ms average response time only achieves 1300 RPS instead of the much higher rate Little's Law would predict for the application tier alone, the gap points straight at a shared, fixed-capacity resource, almost always the DB connection pool or a downstream rate limiter, capping concurrency well below what the VU count suggests. This is the same diagnostic logic as the `hikaricp.connections.pending` metric in §6, just derived analytically first so you know what number to look for before opening a dashboard.

---

## 13. Best Practices

- Always use constant arrival rate scenarios (not VU-based) for production-representative load tests
- Include a 2-minute warmup in load test scripts before starting SLO measurement
- Parameterize test data with feeders (CSV, JSON) to avoid cache artifacts skewing results
- Run load tests from a separate machine or cloud environment — the test tool itself consumes CPU
- Monitor the load generator's resource usage — if k6 is CPU-bound, it throttles itself and the results are invalid
- Co-locate load test execution with CI: k6 Cloud, Gatling Enterprise, or self-hosted k6 with InfluxDB output
- Set `--out influxdb=http://influxdb:8086/k6` in k6 to stream results to Grafana in real time
- Keep load test scripts in the same repository as the service code — reviewed and version-controlled
- Annotate Grafana dashboards with deployment markers (vertical lines when a deploy happened)

---

## 14. Case Study

**Problem**: A checkout service handled 500 RPS in production without issues. After adding a promotional discount feature, load testing at 500 RPS showed p99 latency jumping from 120ms to 2800ms.

**Investigation during load test**:
1. HikariCP metrics showed `hikaricp.connections.pending` spiking to 8 during the load test
2. `pg_stat_statements` showed a new query: `SELECT * FROM promotions WHERE user_id = ? AND active = true ORDER BY created_at` — mean exec time 240ms
3. `EXPLAIN ANALYZE` showed a sequential scan on the `promotions` table (500K rows)
4. The promotions query was executed once per checkout request but was never profiled in unit tests

**Fix**: Added composite index `(user_id, active, created_at)`. Re-ran load test: p99 dropped to 95ms. Breakpoint test: service now handles 2200 RPS before degradation.

```mermaid
xychart-beta
    title "Checkout p99 Latency Before/After Index Fix"
    x-axis [Baseline, "With promo bug", "After index fix"]
    y-axis "p99 latency (ms)" 0 --> 3000
    bar [120, 2800, 95]
```

*One missing index turned a 120ms p99 into 2800ms at the same 500 RPS — a 23x collapse — the composite index brought it back to 95ms and raised breakpoint capacity to 2200 RPS.*

**Lesson**: One missing index on a new query path collapsed p99 by 23x. Performance test caught it before production launch. The fix took 30 minutes; production impact would have been a complete checkout outage during peak.
