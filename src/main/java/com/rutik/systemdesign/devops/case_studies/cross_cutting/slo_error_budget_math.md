# SLO and Error Budget Math

> Cross-Cutting Primitive — DevOps Case Studies · Difficulty: Advanced

---

## 1. Concept Overview

An SLO (Service Level Objective) is a target for the reliability of a service, expressed as a percentage of "good" events over a window — for example, 99.9% of HTTP requests succeed over 30 days. It is built on an SLI (Service Level Indicator), the actual measured ratio of good events to total events: `good / total`. An SLA (Service Level Agreement) is the contractual, customer-facing promise — usually set looser than the internal SLO so engineering has headroom before a contract breach.

The error budget is the mathematical complement of the SLO: `error_budget = (1 - SLO) × total_events`. If the SLO is 99.9%, then 0.1% of events are allowed to fail. Over a 30-day month of `2,592,000` seconds, a 99.9% availability SLO permits exactly **43.2 minutes** of downtime; 99.99% permits **4.32 minutes**; 99.999% permits **25.9 seconds**. The error budget reframes reliability from a binary "is it up?" into a quantity you spend: every failed request, every minute of downtime, draws down a finite, replenishing balance.

This framing is the heart of SRE. The error budget turns reliability into an explicit tradeoff against velocity. If the budget is healthy, the team ships fast and takes risks; if the budget is exhausted, the error budget policy kicks in — typically freezing feature releases and redirecting effort to reliability work until the budget recovers. Burn rate — how fast the budget is being consumed relative to the rate that would exactly exhaust it over the window — drives alerting: instead of paging on every error spike, you page on burn rates that threaten the budget, using multi-window multi-burn-rate alerts to balance fast detection against alert noise.

This file is the shared reference for SLI/SLO/SLA definitions, error budget arithmetic, burn-rate alerting math, and error budget policy. It is linked from DevOps case studies covering SRE and reliability. See also [sre_principles_and_slos](../../sre_principles_and_slos/README.md).

---

## 2. Intuition

> **One-line analogy**: An error budget is a monthly data plan — you have a fixed allowance of failures to spend, and burn rate is the megabytes-per-minute gauge that warns you before you blow through it mid-month.

**Mental model**: Reliability is not "perfect or broken" — it is a bank account that refills each window. The SLO sets the balance (99.9% → 43.2 min/month), every failure is a withdrawal, and burn rate is the withdrawal speed. You only panic when the speed would empty the account before the window resets.

**Why it matters**: Paging on every error is how you burn out an on-call rotation and train people to ignore alerts. Error budgets let you ignore harmless blips and page only when reliability is genuinely at risk, and they give product and engineering a shared, numeric language for "should we ship or stabilize?"

**Key insight**: **100% is the wrong reliability target — the right target is one that leaves a budget large enough to ship features.** A budget of zero means no room for deploys, experiments, or planned maintenance. The SLO is deliberately set below 100% so that the error budget becomes the currency that funds velocity.

---

## 3. Core Principles

1. **SLI is a ratio of good to total.** `SLI = good_events / valid_events`. Define "good" precisely (e.g., HTTP status not in 5xx AND latency < 300ms) and define "valid" (exclude health checks, exclude requests you don't control).

2. **SLO is a target over a window.** Always pair the percentage with a window: "99.9% over rolling 28 days." The window determines the budget size and the alert math.

3. **Error budget = (1 − SLO) × total.** It is a quantity, replenished each window, that you are free to spend on risk, deploys, and experiments.

4. **Burn rate normalizes consumption.** A burn rate of `1x` exhausts the entire budget exactly at the end of the window. `14.4x` exhausts it in `1/14.4` of the window. Burn rate = `(errors_in_window / requests_in_window) / (1 - SLO)`.

5. **Alert on burn rate, not raw errors.** Multi-window multi-burn-rate alerts page on fast burns (imminent danger) and slow burns (sustained degradation) while suppressing transient noise.

6. **Tighter SLOs cost exponentially more.** Each added nine roughly 10x's the engineering cost (redundancy, testing, on-call). Set the SLO from what users actually need, not from vanity.

7. **The error budget policy is enforced, not advisory.** When the budget is exhausted, agreed consequences trigger automatically — release freeze, reliability sprint — negotiated in advance between product and SRE.

8. **Measure the SLI the way the user experiences it.** Prefer request-level success ratios over host-level uptime; a server can be "up" while returning errors.

---

## 4. Types / Architectures / Strategies

**SLI types:**
- **Availability SLI** — fraction of requests that succeed: `1 - (5xx / total)`.
- **Latency SLI** — fraction of requests faster than a threshold: `requests_under_300ms / total`. Usually expressed as a percentile target (p99 < 300ms).
- **Quality / correctness SLI** — fraction of responses that are correct (e.g., non-degraded).
- **Freshness SLI** — fraction of data served within a staleness bound (pipelines).
- **Throughput / coverage SLIs** — for batch and data systems.

**SLI measurement methods:**
- **Event-based (request-based)**: count good vs total events directly. `sum(rate(good[5m])) / sum(rate(total[5m]))`. Preferred — matches user experience.
- **Time-based (window-based)**: count "good minutes" where a probe succeeded. Simpler but coarser; a 1-second outage and a 59-second outage both cost one bad minute.

**Burn-rate alerting strategies:**
- **Single-window threshold** — alert if SLI < SLO over one window. Too noisy or too slow.
- **Multi-window multi-burn-rate** (Google SRE) — pair a fast-burn alert (e.g., 14.4x over 1h + 5m short window) with a slow-burn alert (e.g., 6x over 6h + 30m short window). The short window confirms the burn is current and resolves the alert quickly when it stops.

**Error budget policy stances:**
- **Hard freeze** — no feature releases while exhausted; only reliability fixes.
- **Graduated** — slow releases / require extra review at 50% spent.
- **Silver-bullet** — a small number of override deploys per quarter for urgent business needs.

---

## 5. Architecture Diagrams

Error budget as a depleting balance:

```
budget (99.9% / 30d) = 43.2 min/month  =========================  100%
                                          \
            incident 1 (8 min)             \--------------------   81.5%
            steady trickle of 5xx            \------------------   62%
            incident 2 (15 min)               \---------------     27.3%
                                                \
            >>> POLICY TRIGGER at 0% <<<         \------------      0%   FREEZE
            window resets (day 31)  ============================   100% (refill)
```

Multi-window multi-burn-rate alert decision:

```
              long window         short window        page?
fast burn:    14.4x over 1h   AND  14.4x over 5m   ->  PAGE (urgent)
slow burn:     6.0x over 6h   AND   6.0x over 30m  ->  PAGE (ticket)
              both windows must agree -> suppresses transient blips
                                       -> short window auto-resolves alert
```

SLI computation pipeline:

```
   requests --> instrumentation (http_requests_total{status})
                       |
                       v
     PromQL SLI = sum(rate(...5xx...[5m])) / sum(rate(...all...[5m]))
                       |
        +--------------+--------------+
        v                             v
   recording rule:               burn-rate alert rules
   slo:sli_error:ratio_rate5m    (multi-window multi-burn)
        |                             |
        v                             v
   Grafana SLO panel            Alertmanager -> PagerDuty / ticket
```

The SLI/SLO/SLA nesting:

```
   100% --------------------------------------------------- perfect
     |   internal SLO  (99.9%, 43.2 min/mo budget)   <- engineering aims here
     |        |  headroom (the safety margin)
     |        v
     |   customer SLA (99.5%, contractual)           <- credits owed below here
     v
   measured SLI (actual: 99.94% this window)         <- what really happened
```

Burn rate over an incident timeline (30-day budget, 99.9% SLO):

```
   error ratio
   1.44% |####            <- 14.4x burn: fast-burn page fires (2% in 1h)
   0.60% |    ######      <- 6x burn:   slow-burn ticket (5% in 6h)
   0.10% |----------====  <- 1x burn:   nominal, no alert (budget on track)
   0.00% +-----------------------------------------> time
         ^page    ^ticket   ^recovered (short window clears alert in ~5m)
```

---

## 6. How It Works — Detailed Mechanics

**The budget table** (30-day window, 2,592,000 seconds):

| SLO | Allowed failure | Downtime / 30 days | Downtime / week | Downtime / day |
|---|---|---|---|---|
| 99% | 1% | 7.2 hours | 1.68 hours | 14.4 min |
| 99.9% | 0.1% | 43.2 min | 10.1 min | 1.44 min |
| 99.95% | 0.05% | 21.6 min | 5.04 min | 43.2 sec |
| 99.99% | 0.01% | 4.32 min | 1.01 min | 8.64 sec |
| 99.999% | 0.001% | 25.9 sec | 6.05 sec | 0.86 sec |

These come straight from `(1 - SLO) × 2,592,000 seconds`. For 99.9%: `0.001 × 2,592,000 = 2,592 s = 43.2 min`.

**Burn rate derivation.** A burn rate of `1` means consuming the budget at exactly the rate that empties it at the window's end. If the SLO is 99.9% (budget 0.1%) and the current error ratio is 1.44%, the burn rate is `0.0144 / 0.001 = 14.4x` — at that pace the entire 30-day budget is gone in `30 days / 14.4 = 50 hours`. The standard thresholds and their time-to-exhaustion for a 30-day budget:

| Burn rate | Error ratio (99.9% SLO) | Budget consumed in | Typical alert |
|---|---|---|---|
| 1x | 0.1% | 30 days | none (nominal) |
| 6x | 0.6% | 5 days | slow burn (ticket) |
| 14.4x | 1.44% | ~2 days (50h) | fast burn (page) |

The famous Google SRE numbers: **14.4x over 1 hour burns 2% of a 30-day budget** (`14.4 × (1h / 720h) = 2%`), and **6x over 6 hours burns 5%** (`6 × (6h / 720h) = 5%`). These are the alert thresholds because catching a 2% or 5% spend gives time to respond before a large fraction is gone.

**Measuring the SLI in PromQL:**

```promql
# availability SLI error ratio (5xx / total) over 5m
sum(rate(http_requests_total{status=~"5.."}[5m]))
  /
sum(rate(http_requests_total[5m]))
```

**Recording rules for SLI at multiple windows:**

```yaml
groups:
  - name: slo_sli_rules
    rules:
      - record: slo:sli_error:ratio_rate5m
        expr: |
          sum(rate(http_requests_total{job="api",status=~"5.."}[5m]))
          /
          sum(rate(http_requests_total{job="api"}[5m]))
      - record: slo:sli_error:ratio_rate1h
        expr: |
          sum(rate(http_requests_total{job="api",status=~"5.."}[1h]))
          /
          sum(rate(http_requests_total{job="api"}[1h]))
      - record: slo:sli_error:ratio_rate6h
        expr: |
          sum(rate(http_requests_total{job="api",status=~"5.."}[6h]))
          /
          sum(rate(http_requests_total{job="api"}[6h]))
      - record: slo:sli_error:ratio_rate30m
        expr: |
          sum(rate(http_requests_total{job="api",status=~"5.."}[30m]))
          /
          sum(rate(http_requests_total{job="api"}[30m]))
```

**Multi-window multi-burn-rate alert rules** (SLO 99.9%, so error budget = 0.001):

```yaml
groups:
  - name: slo_burn_alerts
    rules:
      # FAST burn: 14.4x over 1h, confirmed by 5m short window. Pages immediately.
      - alert: ErrorBudgetFastBurn
        expr: |
          slo:sli_error:ratio_rate1h  > (14.4 * 0.001)
          and
          slo:sli_error:ratio_rate5m  > (14.4 * 0.001)
        for: 2m
        labels: {severity: page}
        annotations:
          summary: "Fast burn: 2% of 30d budget consumed in 1h"

      # SLOW burn: 6x over 6h, confirmed by 30m short window. Opens a ticket.
      - alert: ErrorBudgetSlowBurn
        expr: |
          slo:sli_error:ratio_rate6h  > (6 * 0.001)
          and
          slo:sli_error:ratio_rate30m > (6 * 0.001)
        for: 15m
        labels: {severity: ticket}
        annotations:
          summary: "Slow burn: 5% of 30d budget consumed in 6h"
```

**Latency SLI** (fraction faster than 300ms, from a histogram):

```promql
# good = requests served under 0.3s; SLI error ratio = 1 - good/total
1 - (
  sum(rate(http_request_duration_seconds_bucket{le="0.3"}[5m]))
  /
  sum(rate(http_request_duration_seconds_count[5m]))
)
```

The short window in each pair (`5m`, `30m`) is what makes the alert resolve quickly: when the burn stops, the short-window expression drops below threshold within minutes and the alert clears, even though the long window is still elevated.

**The full four-tier Google SRE alert table.** The Workbook actually recommends four burn-rate tiers for a 30-day budget, not two, to cover the full severity spectrum:

| Burn rate | Long window | Short window | Budget consumed | Severity |
|---|---|---|---|---|
| 14.4x | 1h | 5m | 2% in 1h | Page (critical) |
| 6x | 6h | 30m | 5% in 6h | Page (critical) |
| 3x | 24h | 2h | 10% in 24h | Ticket |
| 1x | 72h | 6h | 10% in 3 days | Ticket |

The two fast tiers page; the two slow tiers open tickets. The window lengths are chosen so each tier's "budget consumed" is a meaningful, actionable fraction — you never want to page on a burn so small the budget is fine, nor wait so long that the budget is gone before you notice.

**Deriving budget remaining in PromQL.** Beyond alerting, teams render the remaining budget as a gauge. Over a 28-day window with SLO 99.9%:

```promql
# fraction of error budget remaining (1.0 = full, 0 = exhausted, <0 = over)
1 - (
  ( sum(increase(http_requests_total{job="api",status=~"5.."}[28d]))
    / sum(increase(http_requests_total{job="api"}[28d])) )
  / 0.001
)
```

If the trailing-28-day error ratio is 0.0004 (0.04%), then `0.0004 / 0.001 = 0.4`, so `1 - 0.4 = 0.6` — 60% of the budget remains. This single number, on a dashboard, is what drives the "ship or stabilize" conversation.

---

## 7. Real-World Examples

- **Google SRE** originated the error budget and the multi-window multi-burn-rate alerting pattern documented in the SRE Workbook. The canonical 99.9% example with 14.4x/1h and 6x/6h thresholds comes directly from that work, with the explicit goal of capping alert volume while keeping detection fast.

- **Spotify** publishes per-service SLOs and uses error budgets to gate releases; their squads negotiate the SLO with product and freeze risky launches when budget is spent, replacing "is everyone okay with this deploy?" with a numeric check.

- **The Sloth project** (and Pyrra, OpenSLO) generate the full set of recording rules and multi-burn-rate alert rules from a short SLO spec, encoding exactly the math above so teams don't hand-write the thresholds.

- **A common postmortem pattern**: a team sets a 99.99% SLO (4.32 min/month) on a service whose dependency only offers a 99.9% SLA. The internal SLO is mathematically unachievable — the dependency alone can spend 43.2 minutes — so the budget is permanently negative and the freeze never lifts, demoralizing the team until the SLO is corrected to reflect the dependency chain.

- **Amazon / AWS** publishes customer-facing SLAs (e.g., S3 at 99.9% monthly with service credits) that sit deliberately looser than their internal availability targets, the textbook SLA-looser-than-SLO relationship that gives engineering headroom before a contractual breach.

- **A multi-dependency budget allocation example**: a checkout flow depends on auth (99.95%), inventory (99.9%), and payments (99.9%). If all three must succeed serially, the achievable availability is the product `0.9995 × 0.999 × 0.999 ≈ 0.9975`, i.e. ~99.75% — so an SLO of 99.9% on checkout is impossible without redundancy or graceful degradation. Teams that skip this multiplication ship an SLO they can never hit and live in a permanent freeze.

- **Low-traffic trap, observed widely**: a backoffice admin API doing 80 requests/day sets a 99.9% SLO. A single failed request is `1/80 = 1.25%` error ratio for that day — 12.5x the budget — so the SLO is statistical noise. The correct pattern for low-traffic services is an absolute threshold ("no more than 3 failures/day") or a much longer window, not a fine-grained availability ratio.

---

## 8. Tradeoffs

| Choice | Pro | Con | Use when |
|---|---|---|---|
| Event-based SLI | Matches user experience exactly | Needs per-request instrumentation | You have request metrics |
| Time-based SLI | Simple, works with probes only | Coarse; 1s and 59s outage cost the same minute | Black-box / probe-only monitoring |
| Single-window alert | Trivial to write | Too noisy (short window) or too slow (long window) | Never, for production SLOs |
| Multi-window multi-burn | Fast detection + low noise + auto-resolve | More rules, more complex | Production SLO alerting |
| Tighter SLO (more nines) | Stronger reliability promise | ~10x cost per nine; tiny budget | User truly needs it |
| Looser SLO | Big budget, fast velocity | Weaker promise; risk of user pain | Internal / non-critical services |
| Hard freeze policy | Forces reliability investment | Blocks business if mis-tuned | Budget genuinely exhausted |
| Rolling window (28d) | Smooths spikes, no calendar cliff | Slightly harder to reason about | Most production SLOs |

---

## 9. When to Use / When NOT to Use

**Use error budgets and burn-rate alerts when:**
- You have a measurable per-request or per-probe success signal.
- The service has enough traffic for ratios to be statistically meaningful (a 99.9% SLO on 50 requests/day is noise).
- Product and engineering can agree on, and enforce, an error budget policy.
- You want to reduce alert fatigue by paging on user-impact, not raw error counts.

**Use a latency SLO (percentile) when:**
- Users care about speed, not just success — p99 < 300ms captures the tail experience that averages hide.

**Do NOT:**
- Set an SLO tighter than your dependencies allow — the budget will be permanently negative.
- Chase 100% — it leaves zero budget for deploys and is exponentially expensive.
- Use error budgets on extremely low-traffic services — single events swing the ratio wildly; use absolute thresholds instead.
- Page on a single-window raw-error alert — it is either too noisy or too slow; use multi-window multi-burn-rate.
- Treat the SLA and SLO as the same number — keep the SLA looser so a near-miss SLO is not a contract breach.

---

## 10. Common Pitfalls

1. **Single-window SLO alert that is too noisy (or too slow).**

```yaml
# BROKEN: a single 5m-window alert at the raw budget threshold. Every transient
# 5xx blip (a redeploy, a single slow pod) trips it -> constant pages, alert
# fatigue, and it tells you nothing about whether the BUDGET is at risk.
- alert: HighErrorRate
  expr: |
    sum(rate(http_requests_total{status=~"5.."}[5m]))
      / sum(rate(http_requests_total[5m])) > 0.001
  for: 1m
  labels: {severity: page}
```

```yaml
# FIX: multi-window multi-burn-rate. Page only when a fast burn (14.4x over 1h)
# is CONFIRMED by the 5m short window -> ignores blips, auto-resolves when the
# burn stops, and is tied to actual budget consumption (2% of 30d in 1h).
- alert: ErrorBudgetFastBurn
  expr: |
    slo:sli_error:ratio_rate1h > (14.4 * 0.001)
    and
    slo:sli_error:ratio_rate5m > (14.4 * 0.001)
  for: 2m
  labels: {severity: page}
```

2. **SLO tighter than dependencies.** A 99.99% SLO over a dependency that promises 99.9% is unachievable; the budget is negative from day one.

3. **Averaging latency instead of percentiles.** A mean of 120ms can hide a p99 of 4s. SLOs on latency must use percentiles, computed from histogram buckets, not from `_sum / _count`.

4. **Counting health checks and bots as valid events.** They inflate or deflate the SLI and don't reflect real user experience. Exclude them in the SLI definition.

5. **No short window in the burn alert.** Without the short-window confirmation, the alert stays firing for an hour after the incident ends because the long window is still elevated. The short window is what makes it auto-resolve.

6. **Calendar-month windows with a reset cliff.** A bad first-of-month incident dominates the whole month; a rolling 28-day window avoids the artificial reset.

---

## 11. Technologies & Tools

| Tool | Role | SLI source | Burn-rate alerts | Notes |
|---|---|---|---|---|
| Prometheus + Alertmanager | Metrics, recording + alert rules | PromQL ratios | Hand-written multi-burn | The base layer everything builds on |
| Sloth | Generates SLO rules from spec | Prometheus | Auto-generates multi-burn | YAML spec -> full rule set |
| Pyrra | SLO UI + rule generation | Prometheus | Auto multi-burn | Kubernetes-native, dashboards |
| OpenSLO | Vendor-neutral SLO spec format | Any | Spec only | Standardizes the SLO definition |
| Grafana SLO / Grafana Cloud | SLO dashboards + alerting | Prometheus / Loki | Built-in burn rate | Managed, visual budget tracking |
| Nobl9 | Commercial SLO platform | Many backends | Built-in | Multi-source SLO management |

A typical stack: define the SLO in an OpenSLO/Sloth spec, generate the recording and multi-burn-rate alert rules into Prometheus, visualize the budget in Grafana, and route fast-burn pages to PagerDuty and slow-burn to a ticket queue.

---

## 12. Interview Questions with Answers

**Q: Define SLI, SLO, and SLA and the relationship between them.**
An SLI is the measured ratio of good events to valid events (e.g., non-5xx requests / total requests). An SLO is the internal target for that SLI over a window (e.g., 99.9% over 28 days). An SLA is the contractual, customer-facing promise, deliberately set looser than the SLO so engineering has headroom before a real breach. The chain is: SLI is what you measure, SLO is what you aim for, SLA is what you promise.

**Q: How do you compute an error budget, and what is the budget for 99.9%?**
The error budget is `(1 - SLO) × total_events`, the complement of the SLO. For 99.9% the allowed failure fraction is 0.1%, which over a 30-day month of 2,592,000 seconds is `0.001 × 2,592,000 = 2,592 seconds = 43.2 minutes` of downtime. For 99.99% it is 4.32 minutes, and for 99.999% it is 25.9 seconds. Each additional nine cuts the budget by 10x.

**Q: What is burn rate and what does a burn rate of 1 mean?**
Burn rate is how fast you are consuming the error budget relative to the rate that would exhaust it exactly at the end of the window. A burn rate of 1 means you spend the entire budget precisely at the window boundary; a burn rate of 14.4 means you'd exhaust it in 1/14.4 of the window. Numerically, `burn_rate = current_error_ratio / (1 - SLO)`, so a 1.44% error ratio against a 99.9% SLO is `0.0144 / 0.001 = 14.4x`.

**Q: Explain the 14.4x over 1h and 6x over 6h thresholds.**
They are the Google SRE multi-burn-rate thresholds for a 30-day budget. A 14.4x burn over 1 hour consumes `14.4 × (1/720) = 2%` of the budget — fast enough to be urgent, so it pages. A 6x burn over 6 hours consumes `6 × (6/720) = 5%` — a sustained, slower degradation worth a ticket. Each is paired with a short window (5m and 30m) to confirm the burn is current and to auto-resolve the alert quickly.

**Q: Why use multi-window multi-burn-rate alerts instead of a single threshold?**
A single short-window alert pages on every transient blip (noise), while a single long-window alert detects slowly and stays firing long after the incident ends. Multi-window pairs a long window (decides severity / budget impact) with a short window (confirms the burn is happening right now and resolves fast). The result is fast detection of real budget threats, low false-positive rate, and automatic alert resolution.

**Q: What is the role of the short window in a burn-rate alert?**
The short window confirms the burn is current and makes the alert auto-resolve. Without it, the long window stays elevated for its full duration after the incident clears — a 1h window keeps a fast-burn alert firing for nearly an hour past resolution. Requiring both the long and short windows to exceed the threshold means the alert clears within minutes of the burn stopping, because the short window reacts fast.

**Q: What is the difference between event-based and time-based SLIs?**
Event-based (request-based) SLIs count good vs total individual events — `good_requests / total_requests` — and match user experience precisely. Time-based SLIs count "good minutes" where a probe succeeded, which is simpler but coarse: a 1-second outage and a 59-second outage both cost one bad minute. Prefer event-based when you have per-request metrics; use time-based only for black-box probe-only monitoring.

**Q: Why are latency SLOs expressed as percentiles like p99 rather than averages?**
Because averages hide the tail experience that users actually feel. A mean latency of 120ms can coexist with a p99 of 4 seconds, meaning 1% of requests are painfully slow while the average looks fine. A p99 SLO ("99% of requests under 300ms") directly bounds the worst-case experience for nearly all users. Computing it requires histogram buckets, not `_sum / _count`.

**Q: Why is 100% the wrong reliability target?**
Because 100% leaves zero error budget, which means no room for deploys, experiments, planned maintenance, or the inevitable dependency failures — every change becomes a potential breach. It is also exponentially expensive: each added nine roughly 10x's the cost in redundancy, testing, and on-call. The right target is the lowest reliability users won't notice, which leaves a budget big enough to fund feature velocity.

**Q: What is an error budget policy and what triggers it?**
An error budget policy is a pre-agreed set of consequences that trigger when the budget is consumed, negotiated in advance between product and SRE. Typically, exhausting the budget triggers a feature freeze — only reliability fixes ship until the budget recovers — while crossing 50% might require extra review. The point is that it is enforced and automatic, not a discussion held during every incident, giving both sides a clear numeric contract.

**Q: Write the PromQL for an availability SLI.**
`sum(rate(http_requests_total{status=~"5.."}[5m])) / sum(rate(http_requests_total[5m]))` gives the error ratio over 5 minutes. The good-event ratio is `1 -` that expression. You typically wrap it in a recording rule like `slo:sli_error:ratio_rate5m` and compute parallel versions at 1h, 6h, and 30m windows so the multi-burn alert rules can reference them cheaply. Exclude health checks and bots from `http_requests_total` in the SLI definition.

**Q: What happens if you set an SLO tighter than your dependency's SLA?**
The SLO becomes mathematically unachievable and the budget is permanently negative. If you promise 99.99% (4.32 min/month) but your database dependency only offers 99.9% (43.2 min/month), the dependency alone can spend 10x your entire budget. The freeze never lifts and the team is demoralized; the fix is to set the SLO consistent with the realistic availability of the dependency chain, or improve the dependency.

**Q: How do you choose the right SLO target?**
Start from what users actually need and tolerate, measured from real data — look at historical SLI and at the point where users start complaining or churning. Set the SLO just above that pain threshold so it leaves a meaningful budget for velocity, and check it against your dependency chain so it is achievable. Avoid vanity nines; an extra nine you don't need costs ~10x and buys nothing users notice.

**Q: Why use a rolling window instead of a calendar month?**
A calendar-month window has a reset cliff: a bad incident on the 1st dominates the whole month, and the budget snaps back to full at midnight on the 1st regardless of recent reliability. A rolling 28-day window smooths this — the budget reflects the trailing 28 days continuously, so there is no artificial cliff and the alerting math stays stable. Most mature SRE setups use rolling windows for exactly this reason.

**Q: A team is getting paged every time a single pod restarts. How do you fix the SLO alerting?**
The symptom is a single-window alert firing on transient blips, so replace it with multi-window multi-burn-rate. Define recording rules for the SLI at 5m, 1h, 30m, and 6h, then page only when a fast burn (14.4x over 1h) is confirmed by the 5m short window, and open a ticket for a slow burn (6x over 6h confirmed by 30m). This ties pages to real budget consumption, ignores momentary blips like a pod restart, and auto-resolves when the burn stops.

---

## 13. Best Practices

1. **Set SLOs from user need, not vanity.** Derive the target from where users actually feel pain; never default to "as many nines as possible."

2. **Keep the SLA looser than the SLO.** The internal target should have headroom over the contractual promise so a near-miss is not a breach.

3. **Define "good" and "valid" precisely.** Spell out the success condition (status and latency) and exclude health checks, bots, and traffic you don't control from the denominator.

4. **Always use multi-window multi-burn-rate alerts.** Pair a fast burn (14.4x/1h + 5m) with a slow burn (6x/6h + 30m); never page on a single-window raw-error threshold.

5. **Use percentiles for latency SLOs.** Compute p99 from histogram buckets; never average latency.

6. **Generate rules from a spec.** Use Sloth/Pyrra/OpenSLO so the burn-rate math is consistent and not hand-maintained.

7. **Verify SLOs are achievable against dependencies.** Sum the dependency budgets before committing; an SLO tighter than your stack is permanently red.

8. **Write and enforce the error budget policy.** Agree the freeze/consequence rules with product in advance; make exhaustion trigger action automatically.

9. **Use rolling windows.** Prefer a rolling 28-day window over calendar months to avoid reset cliffs.

10. **Track the budget visibly.** A Grafana panel showing remaining budget and current burn rate keeps the tradeoff in front of the whole team.

---

## 14. Case Study

**Scenario**: A checkout API has an internal SLO of 99.9% availability over a rolling 28-day window — a budget of about 40.3 minutes of total request-failure time. On-call is miserable: they get paged roughly 30 times a week, almost always for blips that self-resolve in under two minutes (a pod restart, a single slow upstream call). People have started acknowledging pages without looking. Meanwhile, a genuine 25-minute partial outage three weeks ago went under-investigated because it was buried in the noise.

The alert rule looked like this:

```yaml
# BROKEN: single 5m window at the raw budget threshold. Any momentary 5xx spike
# above 0.1% error ratio pages instantly. A single pod restart pushes the 5m
# ratio over 0.001 for 30-60s -> page. ~30 pages/week, all noise, no link to
# whether the 28-day error BUDGET is actually threatened.
groups:
  - name: checkout_slo
    rules:
      - alert: CheckoutErrorRate
        expr: |
          sum(rate(http_requests_total{job="checkout",status=~"5.."}[5m]))
            / sum(rate(http_requests_total{job="checkout"}[5m])) > 0.001
        for: 1m
        labels: {severity: page}
```

The fix introduces SLI recording rules at four windows and replaces the single alert with a multi-window multi-burn-rate pair. The fast-burn alert pages only when the 1h burn rate hits 14.4x (consuming 2% of the 28-day budget in an hour) AND the 5m short window agrees; the slow-burn alert opens a ticket at 6x over 6h confirmed by 30m:

```yaml
# FIX: SLI recorded at 5m/30m/1h/6h, then multi-window multi-burn alerts.
groups:
  - name: checkout_sli
    rules:
      - record: slo:sli_error:ratio_rate5m
        expr: sum(rate(http_requests_total{job="checkout",status=~"5.."}[5m]))
              / sum(rate(http_requests_total{job="checkout"}[5m]))
      - record: slo:sli_error:ratio_rate1h
        expr: sum(rate(http_requests_total{job="checkout",status=~"5.."}[1h]))
              / sum(rate(http_requests_total{job="checkout"}[1h]))
      - record: slo:sli_error:ratio_rate30m
        expr: sum(rate(http_requests_total{job="checkout",status=~"5.."}[30m]))
              / sum(rate(http_requests_total{job="checkout"}[30m]))
      - record: slo:sli_error:ratio_rate6h
        expr: sum(rate(http_requests_total{job="checkout",status=~"5.."}[6h]))
              / sum(rate(http_requests_total{job="checkout"}[6h]))

  - name: checkout_burn_alerts
    rules:
      - alert: CheckoutFastBurn      # 2% of 28d budget in 1h -> page
        expr: |
          slo:sli_error:ratio_rate1h > (14.4 * 0.001)
          and
          slo:sli_error:ratio_rate5m > (14.4 * 0.001)
        for: 2m
        labels: {severity: page}
      - alert: CheckoutSlowBurn      # 5% of 28d budget in 6h -> ticket
        expr: |
          slo:sli_error:ratio_rate6h > (6 * 0.001)
          and
          slo:sli_error:ratio_rate30m > (6 * 0.001)
        for: 15m
        labels: {severity: ticket}
```

After deploying the change, pages dropped from ~30/week to ~2/week, and both surviving pages corresponded to real, budget-threatening events. The 25-minute outage class now fires a fast-burn page within minutes (a 25-minute total outage is a burn far above 14.4x), and the short windows auto-resolve the alert once the burn stops, so on-call isn't held by a stale page. The team also added a Grafana panel showing remaining budget (currently 71% of 40.3 minutes) and the live 1h burn rate, plus an error budget policy: at 0% remaining, feature deploys to checkout freeze until the budget recovers above 25%.

**Lesson**: The original alert paged on raw error rate, which is noise; the correct signal is burn rate tied to the error budget. Multi-window multi-burn-rate alerting converts reliability monitoring from "page on every blip" into "page only when the budget is genuinely at risk," cutting alert fatigue ~15x while catching real incidents faster. See [sre_principles_and_slos](../../sre_principles_and_slos/README.md) for the surrounding SRE practice.
