# SRE Principles & SLOs

> Phase 6 — Observability & SRE · Difficulty: Advanced

Site Reliability Engineering (SRE) is **what you get when you treat operations as a software problem** — Google's discipline for running reliable systems at scale by setting explicit reliability targets (SLOs), measuring them with SLIs, spending the resulting **error budget** to balance reliability against feature velocity, capping operational drudgery (**toil**), and automating relentlessly. The core insight: **100% reliability is the wrong target** — it's impossible, ruinously expensive, and unnecessary, because users can't tell the difference between 100% and 99.99%. This module covers SLI/SLO/SLA, error budgets and burn rate, toil, capacity planning, and the Google SRE practices that the industry standardized on.

---

## 1. Concept Overview

SRE rests on a small set of interlocking ideas:

- **SLI (Service Level Indicator)** — a quantitative measure of a service aspect, expressed as a *ratio of good events to total events*: e.g. `proportion of HTTP requests served in < 300ms`, or `proportion of requests that return non-5xx`. SLIs are measured from real telemetry (usually PromQL — see [observability_metrics_prometheus](../observability_metrics_prometheus/)).
- **SLO (Service Level Objective)** — a target value for an SLI over a window: e.g. `99.9% of requests succeed over 30 days`. It's an *internal* goal, deliberately set below 100%.
- **SLA (Service Level Agreement)** — a *contractual* promise to customers with financial consequences (refunds/credits) if breached. The SLA is always *looser* than the SLO (e.g. SLA 99.5%, SLO 99.9%) so you have an internal safety margin before contractual penalties.
- **Error budget** — the allowed unreliability: `error budget = 1 - SLO`. A 99.9% SLO over 30 days permits `0.1% × 30d = 43.2 minutes` of downtime/errors. This budget is *spent* by incidents, risky deploys, and experiments — and when it's exhausted, you stop shipping risk and focus on reliability.
- **Burn rate** — how fast you're consuming the budget relative to "evenly over the window." Burn rate `1x` exactly exhausts the budget at window end; `14.4x` exhausts it in ~1/14.4 of the window. Burn rate drives alerting (see [visualization_and_alerting](../visualization_and_alerting/)).
- **Toil** — manual, repetitive, automatable, reactive operational work that scales linearly with the service and produces no lasting value. SRE caps toil (Google's guidance: ≤ 50% of an SRE's time) so engineers have time to build automation.

The error budget is the keystone: it turns "how reliable should we be?" from an argument between dev (ship fast) and ops (stay stable) into a **shared number**. If budget remains, ship features and take risks. If it's exhausted, the policy freezes risky launches until reliability recovers. Reliability becomes a measurable, negotiable resource, not a vibe.

Other SRE pillars: **eliminating toil through automation**, **blameless postmortems** (see [incident_management_and_oncall](../incident_management_and_oncall/)), **capacity planning** based on demand forecasts and headroom, **release engineering** (canaries, progressive rollout), and **monitoring on the four golden signals** (latency, traffic, errors, saturation).

---

## 2. Intuition

> **One-line analogy**: An error budget is a monthly spending allowance. You don't aim to spend $0 (that means never leaving the house — no features shipped); you get a budget of "acceptable failure" to spend on launches and risk. Spend it wisely and you ship boldly; blow through it early and you're grounded until next month.

**Mental model**: Reliability is a dial, not a switch. Every nine you add (99% → 99.9% → 99.99%) costs exponentially more (redundancy, testing, on-call, slower releases). You pick the *cheapest* number of nines users can't tell apart from perfect, then manage the gap between that target and reality as a budget you spend on velocity.

**Why it matters**: Without SLOs, "is it reliable enough?" is an endless, political argument with no answer, and teams either over-invest in reliability nobody needs (killing velocity) or under-invest and ship outages. SLOs + error budgets make reliability an explicit, data-driven decision and give dev and ops a shared incentive instead of an adversarial one.

**Key insight**: **100% is the wrong reliability target.** Chasing it is infinitely expensive and pointless because the user's own network, device, and ISP already inject more unreliability than the last few nines you'd add. The right target is the *lowest* reliability your users tolerate — and the gap below 100% is a *budget you should actively spend* on shipping features, not a number to minimize.

---

## 3. Core Principles

1. **Set SLOs below 100%.** Pick the reliability users can't distinguish from perfect; the gap is your error budget.
2. **Error budget = (1 − SLO).** It's a shared currency: spend it on velocity when full, freeze risk when empty.
3. **Measure SLIs as good/total ratios** from real telemetry; SLAs are looser, contractual versions of SLOs.
4. **Cap toil (≤ 50%).** Automate repetitive ops work; toil that scales with the service is a bug to fix.
5. **Blameless culture.** Postmortems target systems and processes, not people (see [incident_management_and_oncall](../incident_management_and_oncall/)).
6. **Monitor the four golden signals:** latency, traffic, errors, saturation.
7. **Capacity-plan on forecasts + headroom**, not on last week's peak; plan for organic growth and failover.
8. **Reliability is a feature** with a cost; trade it explicitly against velocity via the error-budget policy.

---

## 4. Types / Architectures / Strategies

### SLI types and how they're measured

| SLI category | Measures | Example | Formula (good/total) |
|--------------|----------|---------|----------------------|
| Availability | Successful requests | non-5xx ratio | `good = non-5xx`, `total = all requests` |
| Latency | Fast-enough requests | p99 < 300ms | `good = requests under threshold` |
| Quality | Correct/full responses | non-degraded | `good = full-quality responses` |
| Freshness | Data recency | pipeline < 5m stale | `good = fresh reads` |
| Coverage | Processed share | 99.9% of records | `good = processed records` |
| Durability | Data not lost | objects intact | `good = retrievable objects` |

### Nines, downtime, and cost

| SLO | Error budget | Downtime / 30 days | Downtime / year | Rough relative cost |
|-----|--------------|--------------------|-----------------| --------------------|
| 99% (two nines) | 1% | 7h 12m | 3.65 days | 1x |
| 99.9% (three nines) | 0.1% | 43.2 min | 8.76 hours | ~3–5x |
| 99.95% | 0.05% | 21.6 min | 4.38 hours | ~6x |
| 99.99% (four nines) | 0.01% | 4.32 min | 52.6 min | ~10x+ |
| 99.999% (five nines) | 0.001% | 25.9 sec | 5.26 min | massive |

### Burn-rate alert windows (Google SRE Workbook, 99.9% SLO)

| Burn rate | Budget consumed | Long window | Short window | Action |
|-----------|-----------------|-------------|--------------|--------|
| 14.4x | ~2% in 1h | 1h | 5m | Page (fast) |
| 6x | ~5% in 6h | 6h | 30m | Page/ticket |
| 1x | ~10% in 3d | 3d | 6h | Ticket (slow) |

### Toil reduction ladder

| Level | State |
|-------|-------|
| Manual | Human does it every time (pure toil) |
| Documented | Runbook exists; still manual |
| Scripted | One command runs it |
| Self-service | Anyone triggers it safely without SRE |
| Automated | System does it; humans only on exception |

---

## 5. Architecture Diagrams

```
The error-budget loop (the heart of SRE)

  define SLI (good/total ratio) --> set SLO (e.g. 99.9% / 30d)
                                         |
                          error budget = 1 - SLO = 0.1% = 43.2 min/30d
                                         |
            measure SLI continuously (PromQL) -> compute budget consumed
                                         |
              +-------------- budget remaining? --------------+
              | YES                                           | NO (exhausted)
              v                                               v
        ship features / take risk                  freeze risky launches;
        (spend the budget)                          all hands on reliability
                                         |
              burn rate alerts page when spend is too fast (14.4x / 6x)


Nines vs cost (why 100% is wrong)

  cost
   ^                                           * 99.999%
   |                                  * 99.99%
   |                       * 99.95%
   |            * 99.9%
   |     * 99%
   +------------------------------------------> reliability
   user-perceptible gap shrinks to ~0 long before cost stops exploding


Toil budget

  SRE time:  [#### project/automation 50%+ ####][~~ toil <=50% ~~][on-call]
  if toil > 50% sustained -> hire/automate; toil scales with service = bug
```

---

## 6. How It Works — Detailed Mechanics

### Defining an SLI in PromQL

```promql
# Availability SLI: proportion of successful (non-5xx) requests over 30 days.
sum(rate(http_requests_total{job="api", status!~"5.."}[30d]))
  /
sum(rate(http_requests_total{job="api"}[30d]))
# -> e.g. 0.9994  => 99.94% availability over the window

# Latency SLI: proportion of requests faster than 300ms (good = under-threshold bucket).
sum(rate(http_request_duration_seconds_bucket{job="api", le="0.3"}[30d]))
  /
sum(rate(http_request_duration_seconds_count{job="api"}[30d]))
```

### Error budget and budget remaining

```
SLO          = 99.9%  (0.999)
error budget = 1 - SLO = 0.001 (0.1%)
window       = 30 days = 43,200 minutes
budget time  = 0.001 * 43,200 = 43.2 minutes of allowed downtime/errors

budget consumed = (1 - actual_SLI) / (1 - SLO)
  e.g. actual SLI = 99.94% -> (1 - 0.9994)/(1 - 0.999) = 0.0006/0.001 = 0.6 = 60% consumed
budget remaining = 40%   ->  ship with care; you're more than half through.
```

```promql
# Error budget consumed as a fraction, in PromQL (for a budget dashboard panel).
(
  1 -
  ( sum(rate(http_requests_total{job="api", status!~"5.."}[30d]))
    / sum(rate(http_requests_total{job="api"}[30d])) )
) / (1 - 0.999)
# 0.6 = 60% of the 30-day error budget burned.
```

### Burn rate and where 14.4x comes from

```
burn rate = (observed error ratio) / (error budget fraction)
          = (1 - SLI_window) / (1 - SLO)

A burn rate of 1x consumes the WHOLE budget exactly over the SLO window (30d).
We want to PAGE if a 30-day budget would be gone in ~2 days:
   30 days / 2 days = 15x  ~~  Google uses 14.4x as the canonical fast threshold
   (14.4x over 1h consumes 14.4/720 = 2% of a 30-day budget in that hour).
```

```yaml
# Multi-window, multi-burn-rate alert (canonical SRE pattern). SLO 99.9% -> budget 0.001.
groups:
  - name: slo-burn
    rules:
      - alert: BudgetBurnFast      # page: severe, act now
        expr: |
          (slo:errors:ratio_rate1h > (14.4 * 0.001))
          and (slo:errors:ratio_rate5m > (14.4 * 0.001))
        for: 2m
        labels: { severity: page }
      - alert: BudgetBurnSlow      # ticket: real but slower
        expr: |
          (slo:errors:ratio_rate6h > (6 * 0.001))
          and (slo:errors:ratio_rate30m > (6 * 0.001))
        for: 15m
        labels: { severity: ticket }
```

The short window confirms the burn is *current* (and lets the alert resolve quickly); the long window confirms it's *real* (not a transient blip). Routing of these alerts is owned by [visualization_and_alerting](../visualization_and_alerting/).

### Error-budget policy (the document that gives SLOs teeth)

```
ERROR BUDGET POLICY (signed by eng + product leadership)
  - If budget remaining > 0:  feature work proceeds; risky changes allowed with canary.
  - If budget exhausted (or <10%):
      * freeze non-critical feature launches
      * all reliability bugs become P1
      * postmortem action items take priority over roadmap
      * resume normal velocity only when budget recovers (rolling 30d window)
  - Repeated exhaustion -> revisit the SLO (too strict?) or invest in reliability (too fragile).
```

### Capacity planning math

```
Plan capacity from forecast demand + headroom + failover, not last week's peak.

  required_capacity = peak_demand_forecast / target_utilization  + N+1 failover
  example: forecast peak = 80,000 rps; target utilization = 60% (headroom for spikes/GC)
           per-instance capacity = 2,000 rps
           instances = 80,000 / (2,000 * 0.60) = ~67 instances
           + N+1 (lose one AZ of 3) -> provision 3/2 -> ~100 instances across 3 AZs
  reassess against organic growth (e.g. +30%/yr) and seasonal peaks (Black Friday x3).
```

### Toil measurement

```
Track toil as a % of team time (timesheets/ticket tags). Google guidance: keep < 50%.
  toil = manual + repetitive + automatable + reactive + no-lasting-value + O(service growth)
  if a task is done > a few times/month and is automatable -> it's a candidate to kill.
  Goal: every quarter, automate the top toil source so toil trends DOWN as the service grows.
```

---

## 7. Real-World Examples

- **Google SRE**: originated the discipline; the SRE Book and SRE Workbook codified SLIs/SLOs, error budgets, the 50% toil cap, blameless postmortems, and burn-rate alerting that the industry adopted wholesale.
- **Error-budget freeze in practice**: teams that exhaust their budget enact a launch freeze — Google famously halts feature launches for a service until its rolling SLO recovers, which realigns dev incentives toward reliability without a manager mandate.
- **Netflix / chaos + reliability**: pairs SLO-driven reliability with chaos engineering (deliberately injecting failure) to validate that the system meets its SLO under real fault conditions (see [../../backend/chaos_engineering](../../backend/chaos_engineering)).
- **Amazon / two-pizza + operational ownership**: "you build it, you run it" pushes SRE-like ownership to product teams, with on-call and SLOs owned by the service team rather than a separate ops silo.
- **SLO tooling adoption**: companies generate SLOs and burn-rate alerts declaratively with Sloth/Pyrra/OpenSLO, turning an SLO spec into the recording rules + multi-window alert rules automatically.

---

## 8. Tradeoffs

| Decision | Option A | Option B | Key factor |
|----------|----------|----------|-----------|
| SLO target | More nines | Fewer nines | Cost/velocity vs perceived reliability |
| Reliability vs velocity | Freeze on budget exhaustion | Keep shipping | Stability vs feature speed (policy decides) |
| SLI source | Server-side metrics | Client-side / synthetic | Easy/complete vs true user experience |
| Window | Rolling 30d | Calendar month | Smooth signal vs simpler reporting |
| Toil | Tolerate (cheaper now) | Automate (invest) | Short-term cost vs long-term scale |
| Capacity | Just-in-time | Generous headroom | Cost vs spike/failover safety |
| Ownership | Central SRE team | Embedded/you-run-it | Consistency vs team autonomy |

---

## 9. When to Use / When NOT to Use

**Adopt SRE/SLO practices when:** you run user-facing services where reliability matters and is debated, you have enough traffic to measure SLIs meaningfully, and you want a data-driven way to balance reliability against feature velocity. Error budgets are most valuable where dev and ops tension exists and reliability decisions need an objective arbiter.

**Scope it down / reconsider when:** you have so little traffic that ratios are statistically noisy (a handful of requests can't produce a meaningful 99.9% — use coarser windows or simpler health checks); an early-stage product where shipping fast matters far more than a defined reliability target (premature SLOs add ceremony); or internal tools where downtime has trivial cost. Also avoid SLO theater — defining SLOs nobody enforces with an error-budget policy is worse than none, because it signals reliability is measured when it isn't. And never chase five nines on a service whose users would never notice three; that's burning money on unperceivable reliability.

---

## 10. Common Pitfalls

**Pitfall 1 — Targeting 100% (or too many nines).**

```
# BROKEN: "our SLO is 100% availability."
#  -> impossible (deploys, dependencies, the user's own network fail), so the budget is 0,
#     which means you can NEVER ship a risky change and every blip is an SLO breach.
#     Teams either lie about the number or burn out chasing it.
```

```
# FIX: set the SLO to the lowest reliability users can't distinguish from perfect.
#   SLO = 99.9% (43.2 min/30d budget) -> a real budget to spend on velocity.
#   Add nines only where data shows users actually feel the gap.
```

**Pitfall 2 — SLIs that don't reflect user experience.** Measuring availability as "the load balancer health check passed" can read 100% while users get 500s, because the health check doesn't exercise the real path. FIX: measure the SLI on actual user request outcomes (status codes, latency of real traffic), ideally as close to the user as possible (server edge or client/synthetic probes).

**Pitfall 3 — SLOs without an error-budget policy (no teeth).** Defining a 99.9% SLO but never acting when it's breached means the SLO is decoration; nothing changes when reliability degrades. FIX: write and get leadership to sign an error-budget policy that *automatically* triggers a launch freeze and reprioritization when the budget is exhausted — the policy, not a manager, enforces the tradeoff.

**Pitfall 4 — Letting toil grow with the service.** Treating manual operational work (restarts, manual scaling, ticket-driven provisioning) as "just the job" means ops load scales linearly with growth until the team is 100% firefighting and never automates. FIX: measure toil as a % of time, cap it (≤ 50%), and dedicate each quarter to automating the largest toil source so toil trends down as the service grows.

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| Prometheus | Measure SLIs; recording rules for budget/burn (see [observability_metrics_prometheus](../observability_metrics_prometheus/)) |
| Sloth / Pyrra | Generate SLO recording + multi-window burn-rate alert rules from a spec |
| OpenSLO | Vendor-neutral SLO specification format |
| Grafana | SLO/error-budget dashboards (see [visualization_and_alerting](../visualization_and_alerting/)) |
| Nobl9 | Managed SLO platform (multi-source SLIs, budgets) |
| Alertmanager / PagerDuty | Route burn-rate alerts to on-call |
| Blackbox exporter / synthetics | Client-side / probe SLIs for true user experience |
| Chaos tools (Gremlin, Chaos Mesh) | Validate SLOs under fault (see [../../backend/chaos_engineering](../../backend/chaos_engineering)) |
| Capacity/forecast tools | Demand forecasting for capacity planning |

---

## 12. Interview Questions with Answers

**Q1: Define SLI, SLO, and SLA and how they relate.**
An SLI is a measured indicator of service quality expressed as a good/total ratio (e.g. fraction of requests under 300ms); an SLO is an internal target for that SLI over a window (e.g. 99.9% over 30 days); an SLA is a contractual promise to customers with financial penalties if breached. They nest: the SLA is set looser than the SLO (e.g. SLA 99.5% vs SLO 99.9%) so you have an internal safety margin and detect trouble before you owe customers refunds. SLIs measure, SLOs target, SLAs commit.

**Q2: What is an error budget and why is it powerful?**
An error budget is the allowed unreliability, computed as `1 − SLO` (a 99.9% SLO gives a 0.1% budget = 43.2 min/30 days). It's powerful because it converts the perennial reliability-vs-velocity argument into a shared number: if budget remains, dev ships features and takes risks; if it's exhausted, the policy freezes risky launches and everyone focuses on reliability. Reliability becomes a measurable, spendable resource that both dev and ops are jointly accountable for, removing the political tug-of-war.

**Q3: Why is 100% reliability the wrong target?**
Because it's effectively impossible (deploys, dependencies, and the user's own device/network all fail), infinitely expensive to approach, and imperceptible to users — they can't tell 100% from 99.99% since their own connection injects more failure than your last nines would remove. Targeting 100% also leaves a zero error budget, so you can never ship a risky change and every transient blip is a "breach." The right target is the lowest reliability your users tolerate, leaving a budget to spend on velocity.

**Q4: How do you choose the right SLO target?**
Start from what users actually perceive and what the business needs: pick the lowest number of nines where users can't distinguish your service from perfect, informed by historical performance and competitor/contract expectations. Validate with data — if dropping from 99.99% to 99.9% produces no measurable change in user behavior or complaints, the extra nine is wasted money. Set the SLO slightly above the SLA for safety margin, and revisit it if you repeatedly exhaust the budget (too strict) or never come close (too loose).

**Q5: What is burn rate, and where does the 14.4x number come from?**
Burn rate is how fast you're consuming the error budget relative to "evenly over the window" — a burn rate of 1x exactly exhausts the budget at the window's end, 2x in half the time, and so on. The 14.4x fast-page threshold comes from wanting to alert when a 30-day budget would be gone in about 2 days (30/2 ≈ 15, rounded to Google's canonical 14.4x), which means in one hour you'd consume ~2% of the budget. You alert on high burn rates over short windows because that's the only way to detect "we'll breach the SLO soon" before it actually happens.

**Q6: Why use multi-window, multi-burn-rate alerts instead of a single threshold?**
A single short window flaps on transient spikes; a single long window detects severe burns far too slowly. Pairing a long window (confirms the burn is real and sustained) with a short confirmation window (ensures it's still happening and lets the alert clear quickly) gives both fast detection on severe burns and low noise. You then set multiple tiers — 14.4x → page fast, 6x → page/ticket, 1x → slow ticket — so severity matches how urgently the budget is being threatened.

**Q7: What is toil and why cap it at 50%?**
Toil is manual, repetitive, automatable, reactive operational work that scales linearly with the service and produces no lasting value — restarts, manual scaling, ticket-driven provisioning. Google caps it at 50% of an SRE's time because if toil consumes everything, the team never builds the automation that would reduce future toil, and ops load grows unbounded with the service. Capping it forces continuous investment in automation so the team's capacity scales sub-linearly with the system.

**Q8: How does the error-budget policy align dev and ops incentives?**
It makes the consequence of unreliability automatic and shared: when the budget is healthy, dev is free to ship fast and take risks (they "own" the budget to spend); when it's exhausted, the policy freezes feature launches and makes reliability bugs top priority for *everyone*, including dev. This removes the adversarial dynamic where ops wants to slow down and dev wants to speed up — both now optimize the same number, and the policy (signed by leadership) enforces the tradeoff without a manager arbitrating each time.

**Q9: What are the four golden signals?**
Latency (how long requests take, split by success/failure since failed-fast differs from succeeded-slow), traffic (demand on the system, e.g. requests/sec), errors (rate of failed requests), and saturation (how full the system is — the resource closest to its limit). Google's SRE book recommends them as the minimal monitoring set for any user-facing service because together they capture user experience and impending capacity problems. They map naturally onto SLIs (latency/errors) and capacity planning (saturation).

**Q10: How do you do capacity planning the SRE way?**
You plan from forecast demand plus headroom plus failover capacity, not from last week's peak: estimate future peak demand (organic growth, seasonal spikes), divide by a target utilization that leaves headroom for bursts/GC (e.g. 60%), and add N+1 redundancy so losing a zone/instance doesn't breach the SLO. For example, an 80k-rps forecast at 60% utilization on 2k-rps instances needs ~67 instances, provisioned to ~100 across three AZs for failover. You reassess regularly against growth and validate with load tests (see [../../backend/load_and_performance_testing](../../backend/load_and_performance_testing)).

**Q11: What's a blameless postmortem and why does it matter for reliability?**
A blameless postmortem analyzes an incident focusing on the systemic and process causes — what made the failure possible and what controls were missing — rather than blaming the individual who triggered it. It matters because blame drives people to hide mistakes and withhold information, which prevents the organization from learning, whereas a blameless culture surfaces the real contributing factors and produces durable fixes. The output is concrete, owned, tracked action items that reduce the chance of recurrence (covered in [incident_management_and_oncall](../incident_management_and_oncall/)).

**Q12: How do you measure an availability SLI in PromQL, and what's a common mistake?**
You compute the ratio of good events to total over the window — e.g. `sum(rate(http_requests_total{status!~"5.."}[30d])) / sum(rate(http_requests_total[30d]))` for request success. The common mistake is measuring something that isn't the user's experience, like a load-balancer health check that passes while real requests fail, which reports a falsely perfect SLI. Measure on actual user-facing request outcomes as close to the user as feasible (edge metrics or synthetic/client probes), and define "good" precisely (which status codes, which latency threshold).

**Q13: When should you NOT define formal SLOs?**
When traffic is too low for ratios to be statistically meaningful (a few requests can't sustain a 99.9% measurement — use coarser windows or simple health checks), when the product is early-stage and shipping speed dwarfs reliability concerns (premature SLOs add ceremony), or for internal/throwaway tools whose downtime costs nothing. Also avoid "SLO theater" — defining SLOs with no enforcing error-budget policy is worse than none because it implies reliability is managed when it isn't. SLOs are worth the overhead only where reliability is genuinely contested and measurable.

**Q14: How do you handle a service that keeps exhausting its error budget?**
First check whether the SLO is realistic — if the service architecturally can't hit it, the SLO may be too strict and should be renegotiated with stakeholders, or the architecture needs reliability investment (redundancy, dependency hardening). Enforce the error-budget policy: freeze risky launches, make reliability bugs P1, and prioritize postmortem action items over the roadmap until the budget recovers. Repeated exhaustion is a signal to either invest in reliability or consciously lower the target — the data forces an explicit decision rather than chronic firefighting.

**Q15: How does SRE differ from traditional ops/DevOps?**
Traditional ops is largely manual and reactive, with reliability as a vague aspiration and a hard org boundary between dev (ship) and ops (stabilize). SRE treats operations as a software problem: it sets explicit reliability targets (SLOs), measures them, manages an error budget that quantifies the velocity-vs-reliability tradeoff, caps manual toil so engineers automate, and runs blameless postmortems. DevOps is the broader cultural movement to break the dev/ops wall; SRE is a specific, prescriptive *implementation* of those principles with concrete practices (error budgets, the 50% toil cap, golden signals) — "class SRE implements interface DevOps."

---

## 13. Best Practices

- **Set SLOs below 100%** at the lowest reliability users can't distinguish from perfect; treat the gap as a budget to spend.
- **Compute error budget = (1 − SLO)** and track *budget remaining* on a dashboard; alert on burn rate, not raw thresholds.
- **Write and enforce an error-budget policy** signed by leadership — freeze risk when the budget is exhausted.
- **Measure SLIs on real user experience** (request outcomes, ideally edge/synthetic), defined precisely (which codes/thresholds).
- **Cap toil ≤ 50%;** measure it and automate the largest source every quarter so it trends down with growth.
- **Monitor the four golden signals;** capacity-plan from forecasts + headroom + N+1 failover, validated by load tests.
- **Run blameless postmortems** with owned, tracked action items (see [incident_management_and_oncall](../incident_management_and_oncall/)).
- **Generate SLOs/alerts as code** (Sloth/Pyrra/OpenSLO) and revisit targets when budgets are chronically over- or under-spent.

---

## 14. Case Study

### Scenario: A team chases 100%, burns out on toil, and still misses its real reliability goal

A payments team set an informal goal of "zero downtime." In practice this meant: no error budget, so every deploy was a fight; on-call manually restarted stuck pods, manually scaled for traffic spikes, and processed provisioning tickets — toil consumed ~80% of the team's time. Despite the heroics, the service still had outages (the real availability was ~99.7%), and dev and ops were in constant conflict over release pace. Nobody could say whether the service was "reliable enough" because there was no defined target.

```
# BROKEN: "100% / zero downtime" goal.
#  - error budget = 0  -> every risky deploy is forbidden in theory, done anyway in practice
#  - toil ~80% (manual restarts/scaling/tickets) -> no time to automate -> toil grows
#  - SLI measured by LB health check (reads 100% while users get 500s)
#  - no error-budget policy -> dev vs ops fight every release; reliability is a vibe, not a number
```

```promql
# FIX 1: define a real, user-facing SLI and a 99.9% SLO (budget = 43.2 min / 30d).
sum(rate(http_requests_total{job="payments", status!~"5.."}[30d]))
  / sum(rate(http_requests_total{job="payments"}[30d]))    # measured on real requests, not LB checks
```

```yaml
# FIX 2: burn-rate alerts replace manual watching (SLO 99.9% -> budget 0.001).
- alert: PaymentsBudgetBurnFast
  expr: |
    (slo:payments:errors_rate1h > (14.4 * 0.001))
    and (slo:payments:errors_rate5m > (14.4 * 0.001))
  for: 2m
  labels: { severity: page }
```

```
# FIX 3: error-budget policy (signed) + toil program.
#  - budget healthy -> ship features with canary; budget exhausted -> freeze launches, P1 reliability.
#  - automate the top toil source each quarter: HPA for autoscaling (kills manual scaling),
#    self-healing/liveness probes (kills manual restarts), self-service provisioning (kills tickets).
#    target: toil from 80% -> under 50% within two quarters.
```

After adoption: the SLI revealed the *true* 99.7% availability (the LB-check metric had been lying), so the team had a concrete gap to close. The error-budget policy ended the release fights — when the budget was healthy dev shipped freely, and when an incident burned it, launches froze automatically and reliability work took priority. Automating the top toil sources (autoscaling, self-healing, self-service provisioning) cut toil from ~80% to ~40%, freeing engineers to build more reliability. Within two quarters real availability rose to 99.92%, comfortably inside the 99.9% SLO with budget to spare for velocity.

**Outcome:** reliability became a measured number instead of a vibe, the dev/ops conflict was resolved by the shared error budget, toil was halved through automation, and the service got *more* reliable while shipping *faster* — the opposite of the "100%" approach's outcome.

**Discussion questions:**
1. Why did chasing "100% / zero downtime" produce *worse* reliability and constant conflict than a 99.9% SLO with an error budget?
2. How did the LB-health-check SLI hide the real reliability problem, and what should the SLI measure instead?
3. How does the error-budget policy resolve the dev-vs-ops tension without a manager arbitrating each release?

---

**Cross-references:** [observability_metrics_prometheus](../observability_metrics_prometheus/) (measure SLIs and compute budget/burn in PromQL), [visualization_and_alerting](../visualization_and_alerting/) (route burn-rate alerts; SLO/error-budget dashboards), [incident_management_and_oncall](../incident_management_and_oncall/) (blameless postmortems, on-call, MTTR/MTTD), [disaster_recovery_and_resilience](../disaster_recovery_and_resilience/) (availability targets drive DR/RTO/RPO), [../../backend/chaos_engineering](../../backend/chaos_engineering) (validate SLOs under injected failure), [../../backend/load_and_performance_testing](../../backend/load_and_performance_testing) (validate capacity plans), [kubernetes_scheduling_and_autoscaling](../kubernetes_scheduling_and_autoscaling/) (autoscaling as toil reduction).
