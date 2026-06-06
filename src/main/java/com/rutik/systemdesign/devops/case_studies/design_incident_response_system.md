# Design an Incident Response System

> An incident response platform is a fire alarm wired to a phone tree: it must be louder than the fire, never cry wolf, and always reach a human who can act — even when the building it watches is burning down.

**Key insight**: The paging path must be strictly MORE available and MORE independent than every system it monitors — a pager that shares fate with the thing it watches is a pager that goes silent exactly when you need it.

---

## Intuition

> Think of it as an emergency dispatch center (911): thousands of raw 911 calls get triaged, deduplicated ("12 people reporting the same car crash = 1 incident"), routed to the nearest available responder, and escalated up the chain if nobody picks up within minutes.

**Key insight (restated)**: An incident response system is a *signal-reduction and human-routing* engine, not a monitoring system. Monitoring (Prometheus, Datadog) produces signal. The IR platform's job is to turn 10,000 raw firing alerts/day into ~50 actionable pages/day delivered to the *one* correct human in under 60 seconds — and to keep working when the monitored infrastructure is down.

**Mental model.** Picture a 4-stage funnel:

1. **Detect** — rules evaluate metrics/logs/traces and fire alerts (signal generation).
2. **Reduce** — dedup, group, inhibit, and correlate raw alerts into a small number of incidents (noise reduction; 200:1 typical).
3. **Route & page** — map an incident to an on-call schedule, find who is on call *right now*, and deliver via push → SMS → voice with escalation if unacked.
4. **Coordinate & learn** — open a ChatOps incident channel, assign an Incident Commander, track timeline, and feed a blameless postmortem with MTTD/MTTR analytics.

**Why this system exists.** Without it, a 2000-engineer org drowns. A single bad deploy fires 8,000 alerts in 3 minutes; engineers get paged 50 times for one root cause; the right expert is asleep and nobody escalates; and three weeks later nobody can reconstruct what happened. The IR platform exists to (a) protect human attention (alert fatigue is the #1 cause of missed real pages), (b) guarantee a deterministic, audited path from "something broke" to "the right human is awake and acting," and (c) turn every incident into measurable learning (MTTD, MTTR, error-budget burn). It is the connective tissue between observability (which produces signal) and humans (who fix things). The organizational practices that wrap this platform — severity taxonomy, IC roles, blameless culture — are covered in `../incident_management_and_oncall/README.md`; this case study designs the *platform* those practices run on, and the alert-generation layer it consumes is covered in `../visualization_and_alerting/README.md`.

**The three properties that make it hard.** (1) *Independence* — it must survive the failure of everything it watches, which forces a physically separate delivery plane. (2) *Precision under load* — it must stay quiet on noise and loud on real incidents, and the cost of getting this wrong is asymmetric: one missed page can cost more than a thousand spurious ones, yet a thousand spurious pages cause the missed one. (3) *Determinism* — every page, ack, and escalation must be auditable and reproducible, because incidents are litigated (internally in postmortems, sometimes externally with regulators). A system that is merely "usually reliable" is unacceptable here; reliability is the product.

---

## 1. Requirements Clarification

### Functional Requirements

| # | Requirement | Concrete behavior |
|---|-------------|-------------------|
| F1 | Alert ingestion | Accept alerts from Prometheus Alertmanager, Datadog, CloudWatch, custom webhooks; normalize to a common event schema. |
| F2 | Dedup & grouping | Collapse 10,000 raw alerts/hour into a small set of actionable incidents (target ≤50 pages/hour); group by `alertname + cluster + service`. |
| F3 | Inhibition / correlation | Suppress dependent alerts (node-down should inhibit the 40 pod-down alerts on that node). |
| F4 | On-call schedules | Support 300 rotations with daily/weekly handoffs, overrides, follow-the-sun, holiday calendars. |
| F5 | Escalation policies | If a page is unacked within a configurable window (default 5 min), escalate to L2, then L3, then the manager. |
| F6 | Multi-channel notification | Deliver via mobile push, SMS, voice call, email, Slack/Teams — in priority order with fallback. |
| F7 | Incident lifecycle | Open → ack → mitigate → resolve states; assign Incident Commander (IC) and roles; severity SEV1–SEV4. |
| F8 | ChatOps | Auto-create a Slack/Teams channel + bridge; run remediation commands from chat; bidirectional sync with incident state. |
| F9 | Postmortems & runbooks | Auto-generate a timeline; attach runbooks to alerts; blameless postmortem template with action items. |
| F10 | Analytics | MTTD, MTTA, MTTR, page volume per service, alert precision, error-budget burn per SLO. |
| F11 | Audit | Immutable log of every page, ack, escalation, override, and config change. |

### Non-Functional Requirements

| # | Requirement | Target (concrete) |
|---|-------------|-------------------|
| N1 | Page latency | Critical alert firing → first notification delivered in **< 60s** at p99 (Alertmanager group_wait 30s + routing/notify ≤ 30s). |
| N2 | Paging-path availability | **99.99%** (≤ 52.6 min downtime/year) for the ingest→route→notify path — higher than the 99.9% of monitored services. |
| N3 | Dedup throughput | Sustain **10,000 alerts/hour** ingest (peak 8,000 in 3 min during a major incident) and reduce to ≤ 50 actionable incidents/hour. |
| N4 | Escalation timeliness | Unacked page escalates within **5 min** (± 5s jitter); zero missed escalations. |
| N5 | Notification durability | Every accepted page is delivered or escalated; **no silent drops** — at-least-once with idempotent dedup keys. |
| N6 | Audit completeness | 100% of paging events persisted with ≤ 1s write lag; 18-month retention. |
| N7 | Provider redundancy | Survive a single SMS/voice provider outage with automatic failover (Twilio → Bandwidth) in < 10s. |

### Out of Scope

- The monitoring/metric-collection layer itself (Prometheus scrape, Datadog agents) — see `../visualization_and_alerting/README.md`.
- Log aggregation pipeline — see the companion `design_log_aggregation_pipeline.md`.
- Auto-remediation engines beyond simple ChatOps runbooks (full closed-loop remediation is a separate platform).
- Status-page / customer comms (Statuspage.io) — mentioned but not designed here.
- The SLO definition framework itself — math lives in `cross_cutting/slo_error_budget_math.md`.

---

## 2. Scale Estimation

**Org shape.** 2,000 engineers, 300 on-call rotations, ~1,200 microservices across 8 Kubernetes clusters in 3 regions.

### Alert volume

- Each cluster runs ~3,000 alerting rules. 8 clusters → 24,000 rules evaluated every 30s.
- Steady-state firing rate: ~0.3% of rules firing at any time → ~72 alerts active steady-state.
- Daily churn: ~10,000 raw alert *transitions* (firing/resolved) per day across the fleet ≈ **417 alerts/hour** steady-state.
- **Incident-storm peak**: a bad deploy or AZ partition fires **8,000 alerts in 180 seconds** = 44 alerts/sec burst. The system must absorb this without dropping or paging 8,000 times.

### Signal-to-noise reduction (the core math)

```
10,000 raw alerts/day
  → grouping by (alertname, cluster, service): ~200:1 collapse  → ~50 alert groups/day
  → inhibition (node-down suppresses dependent pod alerts):  -30%  → ~35 groups/day
  → routing: ~70% map to ticket/Slack-only (SEV3/4), ~30% page → ~50 pages/day
Target: ~50 actionable PAGES/day across 300 rotations
      = 0.17 pages/rotation/day = 1 page per rotation per ~6 days
```

A healthy target is **< 2 pages per on-call shift per week**. Above that, alert fatigue sets in and real pages get missed (PagerDuty's own research: responders ignoring > 5 pages/day miss ~25% of subsequent real pages).

### Notification fan-out

Per page, worst case (full escalation through 4 levels):

- L1: push + (if unacked 2 min) SMS + (if unacked 4 min) voice = 3 messages.
- L2, L3, manager: same → up to 4 × 3 = **12 notification sends per fully-escalated page**.
- 50 pages/day, assume 20% escalate one level → ~50×(3) + 10×(3) ≈ **180 notification sends/day** steady-state.
- During a major incident: 1 incident, but a "fan-out broadcast" to a 30-person response team = 30 × 3 = 90 sends in 60s. Notification subsystem sized for **100 sends/sec peak**.

### Storage

| Data | Volume | Retention | Total |
|------|--------|-----------|-------|
| Alert events | 10k/day × 2KB | 18 mo | ~11 GB |
| Notification audit | 180/day × 1KB | 18 mo | ~100 MB |
| Incidents + timelines | 50/day × 50KB (chat, events) | 5 yr | ~4.6 GB |
| Postmortems | 50/day × 200KB | 7 yr | ~26 GB |
| Schedule/escalation config | 300 rotations × 10KB | live | ~3 MB |

Total hot+warm storage ≈ **45 GB** — trivially small; this is a *latency and availability* problem, not a storage problem.

### Latency budget (the 60s SLA, decomposed)

```
Alert fires (rule eval)          : t=0
Alertmanager group_wait          : +30s   (batch correlated alerts)
Routing + escalation resolve     : +2s    (who is on-call now?)
Notification dispatch (queue)    : +1s
Provider delivery (push/SMS)     : +5–15s
                                  --------
p99 first-touch                  : ~48s   (under the 60s budget)
```

The `group_wait` of 30s dominates and is intentional — it trades 30s of latency for massive noise reduction. SEV1 routes can use `group_wait: 5s` to trade noise for speed.

### Compute sizing

- **Alertmanager**: 8 clusters × 3 HA replicas = 24 pods, each evaluating notification dedup over ~8,000 peak active alerts (~16 MB RAM). CPU is bursty during storms; 500m steady, 1 vCPU limit.
- **Ingest API**: 100 alerts/sec peak (storm) × ~2ms per normalize = trivial; sized for HA (3 pods × 2 vCPU) not throughput.
- **Incident Engine**: 50 incidents/day, each ~20 state transitions = 1,000 writes/day to Postgres — a single `db.r6g.large` with a read replica is 100x over-provisioned, chosen for availability not load.
- **Notification Dispatcher**: 16 workers (derived in §10) for 100 sends/sec broadcast peak.
- **Analytics (ClickHouse)**: 10k events/day ingest, sub-second MTTx aggregation queries — single 8-vCPU node.

The recurring theme: this system is sized for **availability and burst absorption**, not steady-state throughput. The steady-state load (417 alerts/hour, 50 pages/day) would fit on a laptop; the engineering cost is entirely in surviving the 44 alerts/sec storm and the 99.99% paging SLA.

---

## 3. High-Level Architecture

The paging path is split into a **signal plane** (lives next to the monitored infra, can fail with it) and a **delivery plane** (deliberately isolated, runs in a separate region/account, more available than anything it watches).

```
                          SIGNAL PLANE (per cluster, shares fate w/ infra)
  +-----------------------------------------------------------------------+
  |  Prometheus (per cluster)        Datadog / CloudWatch / custom        |
  |       | rules eval 30s                |  webhooks                      |
  |       v                               v                               |
  |  Alertmanager (HA, 3 replicas, gossip)                                |
  |    - dedup / group_by / inhibit / silence                             |
  |       |  (webhook, signed)                                            |
  +-------|---------------------------------------------------------------+
          |  cross-region, mTLS, idempotency-key
          v
  ====================== DELIVERY PLANE (isolated region/account) =========
  |                                                                       |
  |   [Ingest API]  --(99.99%, multi-AZ, behind anycast LB)               |
  |        |  normalize -> common Event schema, dedup_key                 |
  |        v                                                              |
  |   [Incident Engine] -- state machine: open/ack/mitigate/resolve       |
  |        |   correlation, severity, SLO burn-rate tagging               |
  |        +--> [Schedule Service] "who is on-call for service X now?"     |
  |        +--> [Escalation Engine] timers, policy graph, jitter          |
  |        |                                                              |
  |        v                                                              |
  |   [Notification Dispatcher]  (queue: SQS/Kafka, idempotent)           |
  |        |        |          |          |          |                    |
  |        v        v          v          v          v                    |
  |     Push     SMS(Twilio) Voice(Twilio) Email   Slack/Teams            |
  |              SMS(Bandwidth-failover) Voice(Bandwidth)                 |
  |        |                                                              |
  |        v   <-- ack callbacks (push tap / SMS reply / DTMF) -->        |
  |   [Ack/State Sync]                                                    |
  |        |                                                              |
  |        +--> [ChatOps Bot] auto-create channel, bridge, runbooks       |
  |        +--> [Postmortem Store] timeline, action items                 |
  |        +--> [Analytics] MTTD/MTTA/MTTR, page precision, EB burn       |
  |        +--> [Audit Log] immutable, append-only (Kinesis -> S3)        |
  =======================================================================
```

### Component inventory

| Component | Responsibility | Tech (default) |
|-----------|----------------|----------------|
| Alertmanager | dedup, grouping, inhibition, silences | Prometheus Alertmanager (HA gossip) |
| Ingest API | normalize multi-source events, idempotency | Go service, anycast LB |
| Incident Engine | lifecycle state machine, severity, correlation | Go + Postgres (state) |
| Schedule Service | resolve current on-call per rotation | Go + Postgres, cached in Redis |
| Escalation Engine | durable timers, policy graph traversal | Go + a durable timer wheel (Temporal/SQS-delay) |
| Notification Dispatcher | multi-channel fan-out, provider failover | Go + Kafka/SQS, provider SDKs |
| ChatOps Bot | channel creation, command exec, bidirectional sync | Slack/Teams app |
| Postmortem Store | timeline assembly, templates, action-item tracking | Postgres + object store |
| Analytics | MTTx, page precision, error-budget burn | ClickHouse + Grafana |
| Audit Log | immutable event ledger | Kinesis → S3 (object-lock) |

### Data flow narrative

1. A Prometheus rule fires → Alertmanager groups/dedups/inhibits → posts a **signed webhook** to the Ingest API across regions (mTLS).
2. Ingest normalizes to a common `Event` with a deterministic `dedup_key = hash(alertname, service, cluster, severity)` and persists to the Incident Engine.
3. Incident Engine either opens a new incident or appends to an existing one (within the same dedup window), tags severity and SLO burn-rate, and asks the Schedule Service "who is on call for `service=checkout` right now?"
4. Escalation Engine arms a durable timer; Notification Dispatcher fans out push → SMS → voice with provider failover.
5. On ack (push tap / SMS reply `4` / DTMF), Ack/State Sync cancels escalation timers and transitions the incident to `acknowledged`.
6. ChatOps opens a war-room channel; Postmortem Store records the timeline; Analytics and Audit consume the same event stream.

### Why the delivery plane is isolated (multi-region)

The delivery plane runs in a **separate cloud account and region** from production, with its own DNS, its own database, and no dependency on the monitored services. If `us-east-1` (where production lives) goes fully dark, the paging path in `us-west-2` still pages the on-call engineer to tell them about it. See `cross_cutting/multi_cluster_networking.md` for cross-region webhook delivery and failover routing, and `cross_cutting/kubernetes_production_hardening.md` for running the delivery plane itself with anti-affinity and PodDisruptionBudgets.

---

## 4. Component Deep Dives

### 4.1 Alertmanager — routing, grouping, inhibition

```
   firing alerts ──> [ route tree ] ──> matched receiver
                          |
              group_by (alertname,cluster,service)
              group_wait 30s / group_interval 5m / repeat_interval 4h
                          |
              [ inhibition rules ] node-down  =====> suppress pod-down
                          |
              [ silences ]  (maintenance windows)
                          v
                    webhook -> Ingest API
```

The single most common production failure is **a pager storm**: one root cause (a node dies) fires 50 dependent alerts, each becomes a separate page. The fix is grouping + inhibition.

```yaml
# BROKEN: no grouping, no inhibition. A single node failure pages 50 times.
route:
  receiver: oncall-pager
  group_by: ['...']          # '...' means GROUP BY NOTHING -> every alert is its own page
  group_wait: 0s             # page instantly, no time to correlate
  repeat_interval: 1m        # re-page every minute -> fatigue
# (no inhibit_rules block at all -> node-down + 40 pod-down = 41 pages)
```

```yaml
# FIX: group correlated alerts, wait 30s to batch, inhibit dependents.
route:
  receiver: default-ticket
  group_by: ['alertname', 'cluster', 'service']
  group_wait: 30s            # batch alerts that fire within 30s of each other
  group_interval: 5m         # min gap before a new batch for the same group
  repeat_interval: 4h        # only re-page every 4h if still unacked-and-firing
  routes:
    - matchers: [ 'severity="critical"', 'slo_burn="fast"' ]
      receiver: oncall-pager
      group_wait: 5s         # SEV1 fast-burn: trade noise for speed
      continue: false
    - matchers: [ 'severity="warning"' ]
      receiver: slack-only    # warnings never page a human

inhibit_rules:
  # A node being down inhibits every pod-down/target-down alert ON that node.
  - source_matchers: [ 'alertname="NodeDown"' ]
    target_matchers: [ 'alertname=~"(KubePodNotReady|TargetDown|ContainerKilled)"' ]
    equal: ['cluster', 'node']     # same node => one page instead of 41
  # A whole-cluster outage inhibits per-service alerts in that cluster.
  - source_matchers: [ 'alertname="ClusterAPIDown"' ]
    target_matchers: [ 'severity=~"warning|critical"' ]
    equal: ['cluster']
```

Result: the node failure now produces **1 page** (`NodeDown`, grouped) instead of 41. The 40 dependent alerts are recorded in the incident timeline but never page.

### 4.2 Multi-window, multi-burn-rate SLO alerting

Static-threshold alerts ("error rate > 1% for 5 min") are the #2 cause of pager storms and missed incidents: too sensitive → flapping; too lax → slow detection. The Google SRE recommended approach is **multi-window multi-burn-rate** alerting on the error budget. See the full math in `cross_cutting/slo_error_budget_math.md`.

```
  SLO: 99.9% availability => error budget = 0.1% of requests over 30 days.
  Burn rate = (observed error ratio) / (1 - SLO).
  Fast burn (14.4x): burns 2% of 30-day budget in 1 hour  -> PAGE
  Slow burn (1x)   : burns the whole budget over 30 days   -> TICKET
```

```yaml
# BROKEN: single-window static threshold. Flaps on a 90-second blip,
#         and a slow steady 0.5% error leak never fires until it's huge.
- alert: HighErrorRate
  expr: rate(http_requests_total{code=~"5.."}[5m])
        / rate(http_requests_total[5m]) > 0.01
  for: 5m
  labels: { severity: critical }   # pages on every transient blip -> storm
```

```yaml
# FIX: multi-window multi-burn-rate. Fast burn pages; needs BOTH a long
#      and a short window to fire (short window confirms it's still happening).
- alert: ErrorBudgetFastBurn
  expr: |
    (
      job:slo_error_ratio:rate1h{service="checkout"} > (14.4 * 0.001)
      and
      job:slo_error_ratio:rate5m{service="checkout"}  > (14.4 * 0.001)
    )
  for: 2m
  labels: { severity: critical, slo_burn: fast }
  annotations:
    summary: "checkout burning error budget 14.4x (pages on-call)"
    runbook: "https://runbooks.example.com/checkout-error-budget"

- alert: ErrorBudgetSlowBurn
  expr: |
    (
      job:slo_error_ratio:rate6h{service="checkout"} > (1 * 0.001)
      and
      job:slo_error_ratio:rate30m{service="checkout"} > (1 * 0.001)
    )
  for: 15m
  labels: { severity: warning, slo_burn: slow }   # ticket, not a page
```

The long window (1h) prevents flapping; the short window (5m) ensures the problem is still active before paging. A 14.4x burn rate means: at this rate, you'll exhaust a month's error budget in ~2 hours — that warrants waking someone. A 1x slow burn is a tomorrow-morning ticket.

### 4.3 Escalation policy engine

The escalation engine is a durable timer + policy-graph traversal. The hard requirement: **zero missed escalations** even if the engine process restarts mid-timer. Timers must be persisted, not in-memory.

```
  page sent (t=0)
     |-- arm timer(L1, 5m) persisted to durable store
     v
  ack? --yes--> cancel all timers, incident=acknowledged
     |
     no, t=5m fires
     v
  L2 page --> arm timer(L2, 5m)
     |
     no, t=10m
     v
  L3 page --> arm timer(L3, 5m) --> manager --> #incident-broadcast
```

```go
// Escalation step evaluation. Timers persisted in a durable store (e.g. Temporal
// workflow or SQS delayed messages) so an engine restart never drops a timer.
type EscalationStep struct {
    Level        int
    Targets      []OnCallRef   // resolved from Schedule Service at fire time, not at config time
    DelaySeconds int           // wait before escalating to NEXT level
}

func (e *Engine) onTimerFire(ctx context.Context, inc *Incident, step EscalationStep) error {
    // Re-check ack state atomically; a late ack must win over a racing escalation.
    cur, err := e.store.GetIncident(ctx, inc.ID)
    if err != nil {
        return err
    }
    if cur.State == Acknowledged || cur.State == Resolved {
        return nil // someone picked it up; do not escalate
    }
    // Resolve on-call FRESH (handoff may have happened since the page was created).
    targets, err := e.schedule.ResolveOnCall(ctx, inc.RotationID, time.Now())
    if err != nil {
        // BROKEN behavior would be: return err and silently stop escalating.
        // FIX: degrade to the rotation's static fallback target, never go silent.
        targets = e.schedule.FallbackTargets(inc.RotationID)
        e.metrics.IncEscalationFallback(inc.RotationID)
    }
    if err := e.dispatcher.Notify(ctx, inc, targets, step.Level); err != nil {
        return err // dispatcher retries with its own durable queue
    }
    next, ok := e.policy.NextStep(inc.PolicyID, step.Level)
    if !ok {
        // Final level reached: broadcast to the incident channel + page the manager.
        return e.dispatcher.BroadcastManager(ctx, inc)
    }
    // Arm the NEXT timer durably (jittered ±5s to avoid thundering herd).
    return e.store.ArmTimer(ctx, inc.ID, next, time.Duration(next.DelaySeconds)*time.Second+jitter())
}
```

Two correctness rules baked in above: (1) on-call is resolved *fresh* at each escalation (a handoff between page time and escalation must route to the new on-call), and (2) a schedule-resolution failure degrades to a static fallback target rather than silently halting — a missed escalation is worse than over-paging.

### 4.4 Notification delivery with provider redundancy

Notification is at-least-once with idempotent dedup keys, channel laddering (push → SMS → voice), and **multi-provider failover** so a single SMS provider outage doesn't black-hole pages.

```
  page -> [dispatch queue (Kafka/SQS)] -> worker
                                            |
                  channel ladder per target preference:
                  push(1s) -> if no-ack 2m -> SMS -> if no-ack 4m -> VOICE
                                            |
                  provider ring per channel:
                  SMS: Twilio  --health--> [fail] --> Bandwidth (failover < 10s)
                  Voice: Twilio --> Bandwidth
```

```go
// Send with provider failover. Idempotency key prevents double-paging on retry.
func (d *Dispatcher) sendSMS(ctx context.Context, n Notification) error {
    key := n.IdempotencyKey() // hash(incidentID, targetID, channel, attempt)
    if d.seen.Exists(ctx, key) {
        return nil // already delivered this exact notification; skip (at-least-once)
    }
    providers := []SMSProvider{d.twilio, d.bandwidth} // ordered ring
    var lastErr error
    for _, p := range providers {
        if !d.health.Healthy(p.Name()) {
            continue // circuit open: skip this provider entirely
        }
        sendCtx, cancel := context.WithTimeout(ctx, 8*time.Second)
        err := p.Send(sendCtx, n.To, n.Body, key)
        cancel()
        if err == nil {
            d.seen.Set(ctx, key, 24*time.Hour)
            d.metrics.IncDelivered(p.Name(), "sms")
            return nil
        }
        lastErr = err
        d.health.RecordFailure(p.Name()) // trips circuit after N failures
        d.metrics.IncFailover(p.Name(), "sms")
    }
    return fmt.Errorf("all SMS providers failed: %w", lastErr) // -> queue retry + alert on the pager-path itself
}
```

Health checks ping each provider every 15s; 3 consecutive failures open the circuit and route 100% to the failover provider in **< 10s** (N7). The dispatcher's own failures emit a meta-alert routed to a *different* paging path (e.g., a dead-man's-switch — see §8).

---

## 5. Design Decisions & Tradeoffs

### D1 — Build vs buy (PagerDuty/Opsgenie)

**Decision:** Buy a managed paging core (PagerDuty/Opsgenie) for the delivery plane; build thin in-house glue (Alertmanager config, ChatOps bot, analytics) on top.
**Alternatives:** Fully build (Grafana OnCall / incident.io self-host); fully buy (everything in PagerDuty).
**Rationale:** The 99.99% multi-region notification fabric (telco peering for SMS/voice, push infra, on-call resolver) is brutally hard to build and operate; PagerDuty has 13+ years of telco redundancy. Building it in-house means *you* own the pager outage at 3am.
**Consequences:** Vendor cost (~$21–41/user/month) and lock-in on schedules/escalation; you still own alert quality (Alertmanager) and analytics. For a 2000-eng org, ~$500k–1M/yr — far cheaper than a 6-engineer team building telco failover.

### D2 — Symptom-based vs cause-based alerting

**Decision:** Page on **symptoms** (SLO violations users feel: latency, error rate, availability); alert (ticket) on causes.
**Alternatives:** Page on causes (high CPU, disk 90%, pod restarts).
**Rationale:** Cause-based alerting produces 10x more pages, most non-actionable (high CPU that users never feel). Symptom-based alerting pages only when users hurt. Google SRE's core principle.
**Consequences:** Fewer, higher-precision pages; requires well-defined SLOs (`cross_cutting/slo_error_budget_math.md`). Risk: a novel cause with no symptom yet goes undetected until it surfaces — mitigated by slow-burn warning tickets.

### D3 — Multi-burn-rate vs static thresholds

**Decision:** Multi-window multi-burn-rate on error budget (see §4.2).
**Alternatives:** Static `> 1% for 5m`; single-window burn rate.
**Rationale:** Static thresholds force a bad tradeoff between flapping and slow detection; multi-burn-rate gives fast detection for fast burns and patient detection for slow leaks, with a short confirmation window to kill flaps.
**Consequences:** More complex rules (4 alerts per SLO instead of 1) and requires recording rules for multiple windows. Higher Prometheus rule cardinality — see `cross_cutting/prometheus_cardinality_and_scale.md`.

### D4 — ChatOps vs ticket-first coordination

**Decision:** ChatOps-first (Slack/Teams war room) with an auto-synced incident ticket behind it.
**Alternatives:** Ticket-first (Jira/ServiceNow as the source of truth); email.
**Rationale:** Real-time incidents need real-time, low-friction coordination; chat is where engineers already live, and bots can run remediation and capture the timeline automatically. Tickets are too high-latency for SEV1.
**Consequences:** Chat history must be persisted into the postmortem store (chat retention ≠ audit retention). Risk of decisions lost in scrollback — mitigated by a bot that pins decisions and auto-builds the timeline.

### D5 — Auto-remediation vs human-in-the-loop

**Decision:** Human-in-the-loop with *suggested* runbook actions executable from chat (one-click `/runbook restart-checkout`), not fully autonomous remediation.
**Alternatives:** Fully autonomous closed-loop remediation; pure manual.
**Rationale:** Autonomous remediation that misfires can amplify an incident (auto-rollback that rolls back the fix). Suggested + one-click keeps a human accountable while removing toil.
**Consequences:** Slightly slower MTTR than full automation for well-understood incidents; far safer. Graduate the safest, highest-frequency runbooks to auto over time.

### D6 — group_wait latency vs noise

**Decision:** Default `group_wait: 30s`; SEV1 fast-burn routes use `group_wait: 5s`.
**Alternatives:** 0s (instant, noisy); 60s+ (quiet, slow).
**Rationale:** 30s batches correlated alerts (huge noise reduction) at acceptable latency cost; SEV1 trades noise for the 60s SLA.
**Consequences:** Non-SEV1 pages arrive up to 30s later — acceptable within the 60s budget.

### D7 — Delivery plane isolation (separate account/region)

**Decision:** Run the delivery plane in a separate cloud account + region from production.
**Alternatives:** Co-locate with production for simplicity.
**Rationale:** A pager that shares fate with production goes dark during the exact outage it must report. Isolation is the whole point.
**Consequences:** Cross-region webhook latency (~30–80ms), duplicated infra, and an independent on-call for the paging path itself. Worth it.

| Decision | Chosen | Rejected | Primary win | Primary cost |
|----------|--------|----------|-------------|--------------|
| Build vs buy | Buy core, build glue | Fully build | telco-grade reliability | $0.5–1M/yr, lock-in |
| Alert philosophy | Symptom-based | Cause-based | 10x fewer pages | needs good SLOs |
| Threshold | Multi-burn-rate | Static | fast+slow detection | rule complexity |
| Coordination | ChatOps-first | Ticket-first | real-time speed | scrollback loss |
| Remediation | Human + one-click | Autonomous | safety | slightly higher MTTR |
| group_wait | 30s (5s SEV1) | 0s / 60s | noise reduction | +30s latency |
| Topology | Isolated plane | Co-located | survives prod outage | duplicated infra |

---

## 6. Real-World Implementations

**Google (SRE).** Codified symptom-based, multi-window multi-burn-rate alerting in the SRE Workbook. Concrete recommendation: page on a **2% budget burn in 1 hour (14.4x)** and a **5% burn in 6 hours (6x)**, ticket on slow burns. Their philosophy: every page must be "actionable, novel, and requiring human intelligence" — if a machine can fix it, a machine should. Google explicitly caps on-call load at **≤ 2 incidents per 12-hour shift**; exceeding it triggers a reliability investment review.

**PagerDuty (their own architecture).** Runs a multi-region, multi-cloud notification fabric with independent telco providers (multiple SMS/voice carriers) for failover; their pipeline is built for at-least-once delivery with idempotency. They publicly describe a "dead-man's-switch" pattern and emphasize that their event-ingestion and notification paths are isolated so an outage in one customer's region can't black-hole global paging. They process billions of events; deduplication and event-rule routing happen before any human is paged.

**Netflix.** Built internal tooling (e.g., "Dispatch") for incident *coordination* — auto-creating Slack channels, incident docs, and Zoom bridges, and assigning roles (IC, scribe). Dispatch automates the bureaucratic overhead so responders focus on the fix; it auto-generates the timeline and feeds postmortems. Netflix pairs this with chaos engineering (FIT/ChAP) so many failures are caught before they page.

**Stripe.** Practices a strict severity taxonomy (SEV0–SEV3) with documented response-time expectations per severity, a dedicated Incident Commander role rotation independent of the responding team, and blameless postmortems with tracked action items. Stripe is public about treating reliability as a product feature with explicit SLOs and error budgets gating launches.

**GitLab (handbook).** Fully public incident-management process: defined severities (S1–S4 with concrete user-impact criteria), an EOC (Engineer On Call) and IMOC (Incident Manager On Call) role split, Slack-driven workflow with a `/incident` slash command that spins up the war room and a tracking issue, and mandatory blameless reviews. Their handbook specifies escalation timing and the exact roles' responsibilities — a template many orgs copy.

---

## 7. Technologies & Tools

| Tool | Type | Strengths | Weaknesses | Best for |
|------|------|-----------|------------|----------|
| **Prometheus Alertmanager** | OSS dedup/route/inhibit | Free, gossip HA, powerful inhibition, native PromQL | No on-call/escalation/UI; config is YAML-heavy | Signal-plane dedup/grouping in front of any pager |
| **PagerDuty** | SaaS paging | Telco-grade reliability, mature escalation, huge integration catalog, analytics | $21–41/user/mo; can be pricey at 2000 users | Enterprise delivery plane, 99.99% paging |
| **Opsgenie (Atlassian)** | SaaS paging | Tight Jira/Atlassian integration, flexible routing rules, cheaper | Roadmap uncertainty post-acquisition; fewer telco regions | Atlassian-shop orgs |
| **Grafana OnCall** | OSS/SaaS paging | Free OSS, integrates with Grafana alerting, escalation chains | Younger; self-host telco redundancy is on you; OSS being de-prioritized | Grafana-centric, cost-sensitive |
| **incident.io** | SaaS IR/coordination | Excellent Slack-native incident lifecycle, postmortems, on-call (newer) | Coordination-first; paging is newer than PD's | Slack-first incident coordination + postmortems |
| **FireHydrant** | SaaS IR/coordination | Strong runbooks, retrospectives, ServiceNow integration | Coordination-first, pairs with a pager | Process/compliance-heavy orgs |

**Typical stack for this design:** Alertmanager (signal-plane dedup/inhibit) → PagerDuty (delivery plane, escalation, telco failover) → incident.io or Slack bot (coordination + postmortems) → ClickHouse + Grafana (MTTx analytics).

---

## 8. Operational Playbook

### (a) Alert-quality eval gate

Treat alert rules like code: every new/changed alert passes a CI gate before merge.

- **Page precision** = pages that led to action / total pages. Target **≥ 70%**. A rule whose 30-day precision drops below 50% is auto-flagged for review and demoted from page → ticket.
- **Actionability check** — every alert MUST have a linked runbook annotation; CI fails the PR if `annotations.runbook` is missing.
- **Backtest** — replay the last 30 days of metrics against the proposed rule; reject if it would have fired > 20 times or flapped > 5 times/day.
- **Self-resolve rate** — alerts that resolve themselves within 2 min without human action are noise; if > 30%, the threshold is too tight → auto-ticket to tune.

```yaml
# CI gate (pseudo): fail the PR if a paging alert lacks a runbook or backtests noisy.
alert_lint:
  require_annotations: [runbook, summary]
  paging_severities: [critical]
  backtest_window: 30d
  max_fires_per_day: 20
  max_flaps_per_day: 5
```

### (b) Observability of the paging path itself (meta-monitoring)

The paging path is monitored by an **independent** system — you cannot monitor the pager with the pager.

- **Dead-man's switch**: a synthetic `Watchdog` alert fires *always* (every 30s) and is expected to reach a heartbeat endpoint (e.g., healthchecks.io / Grafana Cloud). If the heartbeat *stops*, an external service pages — this catches a fully-dead Alertmanager.
- **OTel spans** across the paging path: `ingest → incident.open → schedule.resolve → escalation.arm → dispatch → provider.send → ack`. SLI: p99 `ingest→provider.send` < 45s.
- **Prometheus SLIs** for the paging path: `alertmanager_notifications_failed_total`, `dispatcher_provider_failover_total`, `escalation_fallback_total`, `paging_path_end_to_end_seconds`. Keep cardinality bounded — see `cross_cutting/prometheus_cardinality_and_scale.md` (don't label by `incident_id`).
- **Error-budget for the pager**: the paging path has its own 99.99% SLO with its own burn-rate alerting — math in `cross_cutting/slo_error_budget_math.md`.

### (c) Named runbooks

**Runbook 1 — Pager storm (> 20 pages in 5 min).**
Symptom: on-call phones blowing up; many pages, often one root cause. Diagnosis: check Alertmanager `/#/alerts` for the dominant `group_by` key; confirm a missing inhibition rule (e.g., `NodeDown` not inhibiting `KubePodNotReady`). Mitigation: apply a broad **silence** matching the storm's labels (e.g., `cluster="us-east-1a"`) for 30 min to stop the bleeding; declare a SEV via one page. Resolution: add/repair the inhibition rule (§4.1), backtest it, and add a postmortem action item to alert-lint CI.

**Runbook 2 — Missed page (page fired, nobody acked, no escalation).**
Symptom: incident found via dashboard/customer, no ack in the audit log. Diagnosis: query the audit log for the `dedup_key`; check whether (a) the page was never dispatched (escalation engine down → dead-man's switch should have fired), (b) dispatched but all providers failed (provider outage), or (c) escalation timer never armed (durable-timer bug). Mitigation: manually page the on-call + manager via the backup channel; re-arm escalation. Resolution: fix the gap (timer persistence, provider failover); add a synthetic end-to-end paging test that fires hourly and verifies ack-path liveness.

**Runbook 3 — Escalation loop / runaway escalation.**
Symptom: the same incident escalating in a loop, paging the whole org. Diagnosis: an ack callback isn't cancelling timers (race between late ack and escalation), or `repeat_interval` too short combined with a non-resolving firing alert. Mitigation: resolve/silence the underlying alert; manually mark the incident acknowledged to cancel all timers. Resolution: fix the ack→timer-cancel atomicity (§4.3 GetIncident re-check); cap max escalation levels and add a circuit breaker that broadcasts once then stops re-paging.

**Runbook 4 — Notification provider outage (Twilio down).**
Symptom: `dispatcher_provider_failover_total` spiking, SMS/voice delivery failing. Diagnosis: check provider status page + health-probe metrics; confirm circuit breaker tripped to Bandwidth. Mitigation: failover should be automatic (< 10s, N7); if not, manually pin the provider ring to the healthy provider via feature flag. Resolution: confirm both providers' webhooks/numbers are warm; add the affected provider to the meta-alert allowlist so its outage is itself a SEV2.

---

## 9. Common Pitfalls & War Stories

**P1 — The pager that shared fate with production.** An org ran its alerting stack *inside* the same Kubernetes cluster it monitored. When the cluster's control plane failed (etcd quorum loss), Alertmanager went down *with it* and **no page was ever sent**. The outage ran **47 minutes** before a customer tweet surfaced it — MTTD blown from a target of 2 min to 47 min, breaching a 99.9% monthly SLO (which allows only ~43 min/month total). Root cause: no delivery-plane isolation and no dead-man's switch. Fix: moved the delivery plane to a separate account/region + added a Watchdog heartbeat (§8). See `cross_cutting/slo_error_budget_math.md` for how a single 47-min event consumes a full month's budget.

**P2 — Pager storm: 41 pages for one dead node.** A node failed; with no inhibition rule, `NodeDown` plus 40 `KubePodNotReady` alerts each paged. The on-call silenced their phone in frustration and **missed the next, unrelated SEV1** 20 minutes later — a checkout outage that ran **18 minutes** undetected at an estimated **$140k** in lost transactions ($7.8k/min). Root cause: missing `inhibit_rules`. Fix: §4.1 inhibition + group_by; per-shift page cap alerting.

**P3 — Static threshold slow-leak.** A 0.4% error rate from a bad config crept in just under a static `> 1%` page threshold. It never paged; the error budget bled out over **6 days** until a 99.95% SLO was breached for the quarter, blocking a launch tied to the error budget. Root cause: single static threshold can't catch slow burns. Fix: multi-window slow-burn warning (§4.2) that tickets at 1x burn.

**P4 — Stale on-call after handoff.** An escalation resolved the on-call target *at page creation time*, but the rotation handed off 4 minutes later. The L2 escalation paged the *previous* on-call (now asleep), delaying response by **11 minutes** on a SEV1 — enough to breach the customer-facing 99.9% availability SLA and trigger a contractual credit of **$25k**. Root cause: on-call resolved once, not fresh per escalation. Fix: resolve on-call at each escalation step (§4.3).

**P5 — Single SMS provider outage.** Twilio had a regional SMS degradation for **22 minutes**. With no failover provider, pages queued and arrived **18 minutes late**; one SEV1 was effectively unhandled until voice (also Twilio) finally connected. Estimated impact: **$90k** in SLA credits across affected customers. Root cause: single-provider dependency. Fix: multi-provider ring with < 10s failover (§4.4) + provider health probes.

**P6 — Audit gap killed the postmortem.** A SEV1 was resolved, but chat retention (90 days) expired before the regulator's postmortem request (130 days later). The timeline couldn't be reconstructed, resulting in a **compliance finding** and a remediation mandate. Root cause: conflating chat retention with audit retention. Fix: persist the full timeline + every paging event to an object-locked audit store with 18-month retention, independent of chat (§3).

---

## 10. Capacity Planning

### Notification throughput formula

```
peak_sends_per_sec = (broadcast_team_size × channels_per_target) / target_delivery_window_s
worker_count       = ceil(peak_sends_per_sec × avg_provider_latency_s / sends_per_worker_inflight)
```

**Worked example.** Major-incident broadcast to a 30-person response team, each via push+SMS+voice (3 channels), delivered within a 60s window:

```
peak_sends_per_sec = (30 × 3) / 60 = 1.5 sends/sec (broadcast)
plus steady-state pages: 50/day fully-escalated worst case ~180 sends/day ≈ 0.002/sec
design headroom: size for 100 sends/sec (covers a fleet-wide AZ event broadcasting to many teams)

avg provider latency = 8s (SMS), inflight per worker = 50
worker_count = ceil(100 × 8 / 50) = 16 dispatcher workers
```

16 dispatcher workers (Go, ~50 in-flight each) on 4 × `c6i.large` (2 vCPU, 4 GB) handle 100 sends/sec with 2x headroom. Across 3 AZs for HA.

### Alertmanager HA gossip sizing

Alertmanager HA uses a gossip mesh (memberlist) to dedup notifications across replicas (each replica independently could notify; gossip ensures only one does).

```
replicas        = 3 (tolerates 1 loss, quorum not required but 3 is the standard floor)
gossip_traffic  ≈ replicas × (replicas-1) × alert_churn_rate × alert_size
                = 3 × 2 × (417 alerts/hr ÷ 3600) × 2KB ≈ ~1.4 KB/s   (trivial)
peak (storm)    = 3 × 2 × 44 alerts/s × 2KB ≈ ~530 KB/s              (still trivial)
```

Gossip bandwidth is negligible; the constraint is **memory** (active alerts + silences in RAM) and **notification log replication latency** (set `--cluster.peer-timeout=15s`). For 8,000 peak active alerts: ~8,000 × 2KB ≈ 16 MB — fits comfortably in a 1 GB-limit pod.

**Sizing (per cluster):** 3 Alertmanager replicas, each `500m CPU / 1Gi RAM`, with anti-affinity across nodes and a PodDisruptionBudget of `minAvailable: 2` so a node drain never drops below 2 replicas — see `cross_cutting/kubernetes_production_hardening.md` for the PDB/anti-affinity/resource-limit patterns and `cross_cutting/multi_cluster_networking.md` for cross-region webhook delivery from these replicas to the isolated delivery plane.

### Cost estimate

| Item | Spec | Monthly |
|------|------|---------|
| Alertmanager HA (8 clusters × 3 pods) | 24 × (0.5 vCPU, 1Gi) | ~$350 |
| Delivery plane (16 dispatcher + engine + DB) | ~12 × c6i.large + RDS | ~$2,800 |
| PagerDuty (managed delivery) | 2000 users @ ~$25/user (Business) | ~$50,000 |
| SMS/voice (Twilio + Bandwidth) | ~180 sends/day + storms | ~$400 |
| ClickHouse analytics + S3 audit | 45 GB hot + object-lock | ~$300 |
| **Total** | | **~$54,000/mo (~$650k/yr)** |

PagerDuty dominates cost. The build-vs-buy break-even (D1): an in-house telco-grade delivery plane needs ~6 senior engineers (~$1.8M/yr fully loaded) plus carrier contracts — so buying is ~3x cheaper *and* lower risk for this org size.

---

## 11. Interview Discussion Points

**Why must the paging path be more available than the systems it monitors?**
Because a pager that shares fate with production goes silent during the exact outage it must report. If your alerting stack runs in the same cluster/region/account as production and that cluster dies, no page is sent (war story P1: 47-min undetected outage). The delivery plane therefore runs in a separate cloud account and region with independent DNS, database, and on-call, targeting 99.99% vs production's 99.9% — and is backstopped by a dead-man's-switch heartbeat so a fully-dead alerting stack still triggers an external page.

**How do you reduce 10,000 raw alerts/day to ~50 actionable pages without dropping real incidents?**
Three stacked reductions: grouping (`group_by: [alertname, cluster, service]` with a 30s `group_wait` collapses correlated alerts ~200:1), inhibition (a `NodeDown` rule suppresses the 40 dependent pod-down alerts → 1 page instead of 41), and routing by severity (warnings go to Slack/tickets, only symptom-based criticals page). The key is symptom-based alerting (page on what users feel) plus multi-burn-rate SLO alerts so you page on real budget burn, not on transient blips. Dependent alerts are still recorded in the incident timeline — they're suppressed from paging, not deleted.

**Explain multi-window multi-burn-rate alerting and why it beats a static threshold.**
A static `error rate > 1% for 5m` forces a bad tradeoff: tight enough to catch problems means it flaps on 90-second blips; loose enough to avoid flaps means slow leaks bleed your budget for days. Multi-burn-rate fires when the error-budget burn rate exceeds a multiple of normal — a fast 14.4x burn (2% of a 30-day budget in 1 hour) pages immediately, while a 1x slow burn tickets. The "multi-window" part requires BOTH a long window (1h, prevents flapping) and a short window (5m, confirms it's still happening) to fire, killing false alarms. Full math: `cross_cutting/slo_error_budget_math.md`.

**How does escalation guarantee zero missed escalations across process restarts?**
Timers are persisted durably (Temporal workflows or SQS delayed messages), never held only in process memory — a dispatcher restart re-loads pending timers. Each escalation step re-checks ack state atomically before firing (a late ack must cancel a racing escalation) and re-resolves the current on-call freshly (handoffs between page time and escalation must route to the new person — war story P4). If schedule resolution fails, the engine degrades to a static fallback target rather than going silent, because over-paging is strictly better than a missed escalation.

**What's the difference between symptom-based and cause-based alerting, and which pages?**
Symptoms are what users experience (latency, error rate, availability/SLO violations); causes are internal states (CPU 90%, disk filling, pod restarts). You page on symptoms and ticket on causes. Cause-based paging produces ~10x more pages, most non-actionable (high CPU users never notice), which drives alert fatigue and missed real pages. The risk — a novel cause with no symptom yet — is covered by slow-burn warning tickets that surface degradation before it becomes user-visible.

**How do you handle a notification-provider (Twilio) outage?**
A multi-provider ring per channel with automatic failover in < 10s: health probes ping each provider every 15s, and 3 consecutive failures trip a circuit breaker routing 100% to the failover provider (e.g., Bandwidth). Each send carries an idempotency key (`hash(incidentID, target, channel, attempt)`) so retries across providers never double-page. War story P5 ($90k in SLA credits from an 18-min delay) is exactly the single-provider failure this prevents. The dispatcher's own failures emit a meta-alert on a *separate* paging path.

**Why ChatOps-first instead of ticket-first incident coordination?**
Incidents need real-time, low-friction coordination and chat is where engineers already work; a bot can auto-create a war-room channel, run one-click runbook commands, and capture the timeline automatically — ticketing systems are too high-latency for SEV1. The ticket still exists behind the scenes as the durable record. The pitfall is losing decisions in scrollback and conflating chat retention (often 90 days) with audit retention (18 months, war story P6) — solved by persisting the full timeline to an independent object-locked store.

**Should incidents auto-remediate? Where's the line?**
Default to human-in-the-loop with one-click suggested runbook actions from chat, not full autonomy, because misfiring automation can amplify an incident (an auto-rollback that reverts the actual fix). Graduate only the safest, highest-frequency, well-understood runbooks to fully automatic over time, each behind a circuit breaker. The slight MTTR cost versus full automation is worth the safety; keep a human accountable until a runbook has proven itself across many incidents.

**How do you keep page latency under 60 seconds?**
Decompose the budget: `group_wait` 30s (the dominant, intentional cost — batches correlated alerts), routing/on-call resolution ~2s, dispatch queue ~1s, and provider delivery 5–15s → ~48s p99. SEV1 fast-burn routes drop `group_wait` to 5s to trade noise for speed. The whole path is traced with OpenTelemetry (`ingest → schedule.resolve → dispatch → provider.send`) with an SLI of p99 < 45s, and it has its own error budget and burn-rate alert.

**How do you measure whether your alerting is actually good?**
Treat alerts as code with a CI eval gate: page precision (pages that led to action / total pages, target ≥ 70%; auto-demote rules below 50%), mandatory runbook annotations (CI fails the PR without one), a 30-day backtest rejecting rules that would fire > 20×/day or flap > 5×/day, and a self-resolve rate (alerts resolving in < 2 min without human action are noise). Operationally, track MTTD/MTTA/MTTR and per-shift page volume, capping on-call at ≤ 2 incidents per 12-hour shift (Google's bar) and triggering a reliability investment when exceeded.

**How do you size the notification subsystem for a major-incident broadcast?**
Use `peak_sends_per_sec = (team_size × channels_per_target) / delivery_window`. A 30-person broadcast over 3 channels in 60s is only 1.5 sends/sec, but you design for ~100 sends/sec to cover fleet-wide AZ events broadcasting to many teams. With 8s average SMS latency and 50 in-flight per worker, `ceil(100 × 8 / 50) = 16` dispatcher workers across 3 AZs. Alertmanager HA gossip is trivial bandwidth (~530 KB/s at storm peak); the real constraint is memory for active alerts (~16 MB for 8,000 alerts) and notification-log replication latency.

**How do you prevent a single bad deploy from paging the entire org in a loop?**
Cap maximum escalation levels with a circuit breaker that broadcasts once then stops re-paging; ensure ack callbacks atomically cancel all timers (the GetIncident re-check in §4.3 prevents a late-ack/escalation race — war story Runbook 3); set a sane `repeat_interval` (4h, not 1m) so a still-firing non-resolving alert doesn't re-page constantly; and use inhibition so the deploy's root-cause alert suppresses dependents. A storm runbook also lets on-call apply a broad label-matched silence to stop the bleeding while the inhibition rule is fixed.

**How do you handle on-call for the paging system itself?**
The delivery plane has its own independent on-call rotation, its own 99.99% SLO with burn-rate alerting, and is meta-monitored by an external dead-man's-switch (a Watchdog alert fires every 30s to an external heartbeat service; if heartbeats stop, that external service pages). You cannot monitor the pager with the pager — the meta-monitoring must be a fully separate vendor/path. This rotation is staffed by the platform team that owns the IR system, kept deliberately small and high-skill.
