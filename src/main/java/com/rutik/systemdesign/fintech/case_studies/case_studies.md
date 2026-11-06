# Fintech Case Studies - Learning Path

Six case studies about systems that move, record or risk-manage money. Five of them start where an existing study in another section stops. No study in the repo covers subscription billing. [hld/](../../hld/README.md) owns the payment system, the digital wallet and the stock exchange end to end. [database/](../../database/README.md) owns the Postgres banking ledger. [backend/](../../backend/README.md) owns the saga-orchestrated payment processor. The studies here cover what those pages assume and do not design: matching three records that disagree, routing one charge across four PSPs, billing on an anchor date, approving a card authorization as the issuer, running a retail broker's order management system, and liquidating a perpetual futures position.

Each study names the existing study it must not duplicate in its `## Intuition` and links it. The scale figures in every study are design assumptions, labelled as such in the study. Dated rules and company figures come from the fact pins in [fintech/CLAUDE.md](../CLAUDE.md).

---

## 1. Quick Start

| Case Study | Why this one first |
|-----------|--------------------|
| [Design a Payment Reconciliation Platform](design_a_payment_reconciliation_platform.md) | Every other money study ends in reconciliation. It shows the section's rule on authority: the PSP or network record proves the card event, the ledger proves the obligation, the bank statement proves cash, and reconciliation resolves the disagreements. |
| [Design a Payment Orchestration Layer](design_a_payment_orchestration_layer.md) | Takes the single-PSP payment system from `hld/` to four PSPs and acquirers in 30 countries, and adds one idempotency chain across PSPs, soft-decline cascades and the per-PSP reconciliation hand-off. |
| [Design a Perpetual Futures Risk Engine](design_a_perpetual_futures_risk_engine.md) | Mark price, funding, liquidation, the insurance fund and ADL ranking in one engine that sits beside the sequencer and replays to the same state. |

---

## 2. Full Learning Path

Grouped by engineering concern. Within a group, read in the order given.

### Moving money

| Case Study | Concern | Summary |
|-----------|---------|---------|
| [Design a Payment Orchestration Layer](design_a_payment_orchestration_layer.md) | One charge across four PSPs | A merchant with $20B a year of card volume, 4 PSPs and acquirers, 30 countries (assumptions). A PCI vault with network tokens, routing on BIN, cost, approval rate and PSP health, soft-decline cascades with one idempotency chain across PSPs, 3DS orchestration, failover and per-PSP reconciliation hand-off. |
| [Design a Card Issuing Platform](design_a_card_issuing_platform.md) | Issuer-side authorization, clearing and disputes | 5M cards through a BIN sponsor (assumption). The authorization decision inside the network's time budget, JIT funding, spend controls, holds and incremental authorizations, clearing-to-authorization matching, issuer-side disputes and stand-in processing. The `hld/` digital wallet lists card issuing as out of scope, and this study designs it. |

### Elsewhere in the repo

[Design a Payment System](../../hld/case_studies/design_payment_system.md), [Design a Payment Processor](../../backend/case_studies/design_payment_processor/design_payment_processor.md), [Design an Idempotent Payment API](../../spring/case_studies/design_idempotent_payment_api.md), [Design a Checkout and Payment Flow](../../react/case_studies/design_a_checkout_and_payment_flow.md) and [Agentic Commerce and Payments](../../llm/multi_agent_systems/agentic_commerce_and_payments.md).

### Recording money

| Case Study | Concern | Summary |
|-----------|---------|---------|
| [Design a Payment Reconciliation Platform](design_a_payment_reconciliation_platform.md) | Three-way match of ledger, PSP and bank records | 40M ledger transfers a day across 6 PSPs and acquirers, 14 bank accounts in 5 currencies, a 99.95% auto-match target (assumptions). Three-way match, fee netting, a break taxonomy with aging, suspense accounts, provisional T+0 versus final T+1, and an ops case UI. |
| [Design a Subscription Billing Engine](design_a_subscription_billing_engine.md) | Usage metering, invoicing and dunning | 3M subscriptions and 2B usage events a day (assumptions). Idempotent metering, rating, anchor-date invoicing, proration, credits, the tax call, dunning, revenue-schedule export, and invoices and credit notes that never change after finalization. No existing study covers billing. |

### Elsewhere in the repo

[Design a Banking Ledger](../../database/case_studies/design_banking_ledger/design_banking_ledger.md) and [Design a Digital Wallet](../../hld/case_studies/design_digital_wallet.md).

### Markets

| Case Study | Concern | Summary |
|-----------|---------|---------|
| [Design a Broker Order Management System](design_a_broker_order_management_system.md) | Broker-side order lifecycle and routing | A retail broker with 5M accounts and 20,000 orders/s at the open (assumptions). The OMS state machine, buying-power checks, smart order routing to venues and wholesalers over FIX, fractional shares, drop-copy reconciliation, T+1 through a clearing firm, and best-execution reports. |
| [Design a Perpetual Futures Risk Engine](design_a_perpetual_futures_risk_engine.md) | Mark price, liquidation and loss allocation | A crypto derivatives venue with 500 markets and 2M open positions (assumptions). Mark price, funding each interval, the liquidation engine, the insurance fund, ADL ranking, and deterministic replay beside the sequencer. |

### Elsewhere in the repo

[Design a Stock Exchange](../../hld/case_studies/design_stock_exchange.md), [Order Matching Engine](../../lld/system_design_problems/OrderMatchingEngine.md) and [Design a Low-Latency Market Data Feed Handler](../../java/case_studies/design_low_latency_market_data_handler.md).

---

## 3. Cross-Cutting / Shared Primitives

Two shared references that the case studies cite and do not restate. Both live under `cross_cutting/`, which is in `CS_EXCLUDE_DIRS`. They are not listed in the section README's `<!-- study-paths -->` block and never enter the question bank.

| File | Becomes relevant when | Cited by |
|------|----------------------|----------|
| [money_movement_invariants](cross_cutting/money_movement_invariants.md) | A design writes a ledger entry or sends money out. The six invariants (conservation per currency, the available-balance floor, idempotent effect, append-only with reversals, one internal transfer per external movement, an end-to-end trace id), each mapped to a database constraint, a runtime assertion, a property test and a production signal. Each study's §8 uses its checklist. | All six case studies |
| [business_days_cutoffs_and_value_dates](cross_cutting/business_days_cutoffs_and_value_dates.md) | A design promises a date, counts a deadline or crosses a rail, a market or a time zone. Fedwire, FedNow, NYSE and T2 calendars, cut-offs, value versus booking versus settlement date, DST, month end, roll conventions, and T+n across holidays. | All six case studies: reconciliation (expected settlement date), billing (anchor dates and DST), broker OMS (settlement date and Reg T), card issuing (clearing file timing), orchestration (authorization expiry) and the perpetual risk engine (funding boundaries) |

---

## 4. Dependency Map

Every study stands alone. An arrow from A to B means that B links A for an idea it does not repeat. Leaves tagged with a section name are out-of-section prerequisites.

```
 money_movement_invariants              business_days_cutoffs_and_value_dates
 (I1 to I6, constraint per invariant)   (calendars, cut-offs, value dates)
              |                                       |
              +-------------------+-------------------+
                                  |
                                  v
                       cited by all six studies

 Money side                                   Markets side

 design_a_payment_                            design_a_broker_order_
 orchestration_layer                          management_system
 (multi-PSP routing, cascades)                (OMS state machine, T+1, drop copy)
      |            |
      |            v                          design_a_perpetual_futures_
      |   design_a_payment_                   risk_engine
      |   reconciliation_platform             (mark price, funding, liquidation, ADL)
      |   (three-way match, suspense)
      |            |
      v            v
 design_a_subscription_
 billing_engine
 (metering, anchors, immutable invoices)

 design_a_card_issuing_platform
 (issuer auth, holds, clearing match, disputes)

 Out-of-section prerequisites:
   design_payment_system [hld] -----------------> orchestration, reconciliation
   design_payment_processor [backend] ----------> orchestration, reconciliation
   design_a_checkout_and_payment_flow [react] --> orchestration
   design_banking_ledger [database] ------------> reconciliation
   design_digital_wallet [hld] -----------------> card issuing
   design_stock_exchange [hld] -----------------> broker OMS, perpetual risk engine
   OrderMatchingEngine [lld] -------------------> broker OMS
```

Reconciliation follows orchestration because it joins PSP lines to ledger transfers on the merchant reference that orchestration puts on every attempt. Billing comes last on the money side because it hands charging to orchestration and payout matching to reconciliation. Card issuing, the broker OMS and the perpetual risk engine have no in-section prerequisite.

---

## 5. Interview Prep Shortcuts

| "Design X" question | Best case study | What it gives you |
|---------------------|-----------------|-------------------|
| "Design a payment system" | [hld payment system](../../hld/case_studies/design_payment_system.md) | Idempotency key, double entry, saga, outbox, a recon job |
| "Design Stripe" | [hld payment system](../../hld/case_studies/design_payment_system.md), then [Payment Orchestration Layer](design_a_payment_orchestration_layer.md) | One PSP end to end, then multi-PSP routing, cascades and the vault |
| "Design a ledger" | [database banking ledger](../../database/case_studies/design_banking_ledger/design_banking_ledger.md) and [double-entry ledger design](../double_entry_ledger_design/double_entry_ledger_design.md) | The Postgres schema, then the accounting model and its invariants |
| "Design payment reconciliation" | [Payment Reconciliation Platform](design_a_payment_reconciliation_platform.md) | Three-way match, break taxonomy, suspense and aging, auto-match rate |
| "Design Venmo" | [hld digital wallet](../../hld/case_studies/design_digital_wallet.md) | P2P transfers, optimistic locking, KYC tiers |
| "Design subscription billing" | [Subscription Billing Engine](design_a_subscription_billing_engine.md) | Metering, rating, anchors, proration, dunning, credit notes |
| "Design Marqeta" or "design a card issuing platform" | [Card Issuing Platform](design_a_card_issuing_platform.md) | Issuer authorization in the time budget, JIT funding, holds, stand-in |
| "Design a stock exchange" | [hld stock exchange](../../hld/case_studies/design_stock_exchange.md) | Sequencer, single writer per symbol, matching, market data |
| "Design Robinhood" | [Broker Order Management System](design_a_broker_order_management_system.md) | Buying power, smart order routing, fractional shares, T+1 |
| "Design a liquidation engine" | [Perpetual Futures Risk Engine](design_a_perpetual_futures_risk_engine.md) | Mark price, funding, liquidation, insurance fund, ADL |
| "Design an idempotent payment API" | [spring idempotent payment API](../../spring/case_studies/design_idempotent_payment_api.md) | Advisory-lock idempotency and the outbox poller |
| "Design checkout" | [react checkout and payment flow](../../react/case_studies/design_a_checkout_and_payment_flow.md) | Card-field iframe, 3DS round trip, minor units in the client |
| "Design sanctions screening" | [KYC, AML and sanctions screening](../kyc_aml_and_sanctions_screening/kyc_aml_and_sanctions_screening.md) | Screening points, thresholds by risk tier, block versus hold versus alert |
| "Design cross-border payouts" | [wallets, stored value and payouts](../wallets_stored_value_and_payouts/wallets_stored_value_and_payouts.md) and [treasury, liquidity and FX operations](../treasury_liquidity_and_fx_operations/treasury_liquidity_and_fx_operations.md) | Instant versus standard payout, prefunding, quote-and-lock FX |
| "Why did we pay a seller twice?" | [money_movement_invariants](cross_cutting/money_movement_invariants.md) | The idempotent-effect and external-match invariants, and the retry-key bug |
| "Why does recon break at month end?" | [business_days_cutoffs_and_value_dates](cross_cutting/business_days_cutoffs_and_value_dates.md) | Value date versus booking date, weekend settlement in transit |

---

## Related studies in other sections

Outbound links only. These pages design one payment or trading system end to end. The fintech modules teach the domain they assume, and the case studies above start where they stop.

| Page | Section | What it owns |
|------|---------|--------------|
| [Design a Payment System](../../hld/case_studies/design_payment_system.md) | hld | One PSP end to end: idempotency key, double entry, saga, outbox, a nightly recon job |
| [Design a Digital Wallet](../../hld/case_studies/design_digital_wallet.md) | hld | Stored-value P2P transfers, optimistic locking, KYC tiers, the instant-rail table |
| [Design a Stock Exchange](../../hld/case_studies/design_stock_exchange.md) | hld | The venue: sequencer, single writer per symbol, snapshot plus delta, pre-trade risk, halts |
| [Design a Banking Ledger](../../database/case_studies/design_banking_ledger/design_banking_ledger.md) | database | The Postgres ledger: SERIALIZABLE, append-only enforcement, RLS, the CQRS balance read model |
| [Design a Payment Processor](../../backend/case_studies/design_payment_processor/design_payment_processor.md) | backend | Saga-orchestrated charge, PSP timeout handling, a three-way match with a break enum |
| [Order Matching Engine](../../lld/system_design_problems/OrderMatchingEngine.md) | lld | The price-time `TreeMap` book, FOK dry run, ticks as `long`, trade at the resting price |
| [Design an Idempotent Payment API](../../spring/case_studies/design_idempotent_payment_api.md) | spring | Advisory-lock idempotency and the outbox poller in Spring Boot |
| [Design a Checkout and Payment Flow](../../react/case_studies/design_a_checkout_and_payment_flow.md) | react | The client: card-field iframe, the 3DS round trip, minor units in the UI |
| [Agentic Commerce and Payments](../../llm/multi_agent_systems/agentic_commerce_and_payments.md) | llm | AP2, x402, ACP and mandates for agent-initiated payments |
| [Design a Real-Time Fraud Detection System](../../ml/case_studies/design_fraud_detection.md) | ml | Fraud model internals: features, class imbalance, drift, streaming scoring |
| [Design a Credit Risk Scoring System](../../ml/case_studies/design_credit_risk_scoring.md) | ml | Lending and credit underwriting, which are out of scope for fintech |
| [Design a Low-Latency Market Data Feed Handler](../../java/case_studies/design_low_latency_market_data_handler.md) | java | Off-heap ring buffer, padded cursors, a GC-free feed handler |
| [Chapter 11: Payment System](../../book/system_design_interview_vol_2/11_payment_system/11_payment_system.md) | book | Summary of System Design Interview Vol. 2, chapter 11 |
| [Chapter 12: Digital Wallet](../../book/system_design_interview_vol_2/12_digital_wallet/12_digital_wallet.md) | book | Summary of System Design Interview Vol. 2, chapter 12 |
| [Chapter 13: Stock Exchange](../../book/system_design_interview_vol_2/13_stock_exchange/13_stock_exchange.md) | book | Summary of System Design Interview Vol. 2, chapter 13 |

---

## Maintenance

Adding a case study means updating all five numbered sections above in the same commit: Quick Start if it displaces an entry, the Full Learning Path group it belongs to, the Cross-Cutting table if it cites a new shared primitive, the dependency map, and an Interview Prep row. Also add it to the `<!-- study-paths -->` block in [the section README](../README.md) with its tiers, unless it lives under `cross_cutting/`, which is excluded.
