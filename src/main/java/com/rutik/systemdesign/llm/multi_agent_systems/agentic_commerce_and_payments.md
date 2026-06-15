# Agentic Commerce and Payments: x402, AP2, ACP, and Agent-Authorized Spend

> Builds on [Agent-to-Agent Protocols](agent_to_agent_protocols.md) — A2A/MCP provide the
> *transport* for agent communication; this module covers the protocols layered on top that let
> agents *pay* for things (each other's services, third-party APIs, real-world goods) with
> structured, auditable authorization. For the security failure modes when an agent's payment
> authority is exploited, see [Multi-Agent Security](multi_agent_security.md).

---

## 1. Concept Overview

**Agentic commerce** is the set of protocols and mechanisms that let an AI agent **initiate,
authorize, and settle payments** — for itself (paying per-API-call for tools it uses), on behalf
of a user (autonomous shopping/procurement), or to another agent (agent-to-agent service
marketplaces). 2025 saw a cluster of competing and complementary protocols emerge to standardize
this: **x402** (Coinbase — revives the long-dormant HTTP `402 Payment Required` status code for
stablecoin micropayments), **AP2** (Agent Payments Protocol — Google-led, 60+ partners including
Mastercard, PayPal, and Coinbase, built on [A2A](agent_to_agent_protocols.md) and
[MCP](../mcp_model_context_protocol/README.md), using cryptographically signed **mandates** as
verifiable proof of user authorization), **ACP** (Agentic Commerce Protocol — OpenAI + Stripe,
powering ChatGPT's "Instant Checkout"), and card-network programs — **Visa Intelligent Commerce**
and **Mastercard Agent Pay** — which extend existing tokenization infrastructure with
agent-specific, programmably-constrained tokens. **Skyfire** addresses a layer underneath all of
these: **agent identity** ("Know Your Agent," KYA) plus a payment network for agent-to-agent and
agent-to-API transactions.

The common thread, regardless of protocol: **separate "what was authorized" from "what the agent
decided to do" from "how money actually moves"** — three concerns that, in a world of autonomous
agents acting at machine speed, can no longer be collapsed into "the agent has my credit card
number."

---

## 2. Intuition

> **One-line analogy**: giving an agent your raw payment credentials is handing an employee your
> personal credit card with no statement, no limit, and no way to know what they bought until the
> bill arrives. Agentic commerce protocols are the digital equivalent of a **corporate card with a
> programmable spending policy attached to each transaction** — the policy travels *with* the
> payment, not in a separate document someone has to remember to check.

**Mental model — three layers, separately swappable**:

1. **Authorization layer** — what did a human (or upstream agent) actually agree to? AP2's
   **mandates** make this a cryptographically signed, verifiable artifact (§3.1) rather than an
   implicit assumption baked into a system prompt.
2. **Decision layer** — given that authorization, what specific cart/transaction did the agent
   construct? This is where the agent's reasoning lives — and where prompt injection (§10.1,
   [Multi-Agent Security](multi_agent_security.md)) could attempt to manipulate the outcome.
3. **Settlement layer** — how does money actually move? Card-network token (Visa/Mastercard,
   ACP's Shared Payment Token), stablecoin transfer (x402), or bank transfer — this is a rail
   choice, and AP2 is explicitly **rail-agnostic**, supporting any of these underneath the same
   mandate structure.

**Why it matters**: an agent with tool access that includes "make purchases" is, structurally, an
agent with **a blast radius measured in dollars**, not just tokens or API quota. Every pitfall
pattern from [Agent Reliability](../agents_and_tool_use/agent_reliability.md) (runaway loops, tool
misuse, hallucinated parameters) has a financial-loss analogue here — a hallucinated quantity
field isn't "wrong output," it's "bought 1,000 units instead of 10."

**Key insight**: these protocols are best understood as **"OAuth for money, plus a shopping cart
that's itself a signed claim."** OAuth lets a user grant a third-party app *scoped, revocable*
access to their data without handing over their password; AP2's Cart Mandate lets a user grant an
agent *scoped, auditable* authority to spend, where the specific cart contents are part of what
gets cryptographically signed — so "the agent bought something I never agreed to" becomes a
verifiable claim, not a he-said-she-said dispute.

---

## 3. Core Principles

### 3.1 Mandates as Verifiable Credentials (AP2)

AP2 defines a **chain of mandates**, each a cryptographically signed Verifiable Credential (VC):

- **Intent Mandate** — captures what the user authorized *in general terms* — e.g., "buy running
  shoes, size 9, under $150, from a reputable retailer" — signed by the user (or their device),
  before the agent does any shopping.
- **Cart Mandate** — once the shopping agent assembles a *specific* cart (exact items, exact
  price, exact merchant), this concrete cart is presented back for signature — in a
  **human-present** flow, the user signs it directly; in a **human-not-present** flow (§3.2), the
  Intent Mandate's pre-authorized constraints stand in for a live signature, provided the Cart
  Mandate's contents fall within those constraints.
- **Payment Mandate** — a signal sent to the payment network/issuer indicating *an AI agent
  initiated this transaction* — distinct from the authorization itself, this exists so issuers can
  apply agent-aware risk scoring (a $2,000 charge initiated by a known shopping agent vs. the same
  charge with no such signal may be scored very differently for fraud purposes).

### 3.2 Human-Present vs. Human-Not-Present Authorization

**Human-present**: the agent assembles a cart and the *actual human* reviews and signs the Cart
Mandate in real time — analogous to a checkout confirmation page, but the "page" is a structured,
signable artifact rather than just a UI the human trusts. **Human-not-present**: the user signs an
Intent Mandate *once*, with bounded constraints (price ceiling, category, merchant allowlist,
expiry), and the agent can execute *multiple* transactions against it without further human
interaction — provided every Cart Mandate it generates is verifiably within those bounds. The
tradeoff is the central one in this entire module: **friction vs. autonomy**, and it's not binary
— constraints can be tuned per use case (§9).

### 3.3 Payment Rails Are a Choice Underneath the Authorization Layer

A signed Cart Mandate doesn't move money by itself — it authorizes a transaction on **some rail**:
a card-network token (Visa/Mastercard, or ACP's Shared Payment Token, §4.3), a stablecoin transfer
(x402, typically USDC on a low-fee L2 like Base), or a bank-to-bank transfer (open banking rails).
AP2 is explicitly designed so the **same mandate structure** can authorize a transaction on any of
these — including x402 as **one of AP2's supported payment-rail extensions** for the
machine-to-machine micropayment case.

### 3.4 Scoped, Ephemeral Credentials — Least Privilege Applied to Money

Every modern agentic-payment mechanism converges on the same principle that
[MCP Security](../mcp_model_context_protocol/mcp_security.md) and
[Agent-to-Agent Protocols §10.2](agent_to_agent_protocols.md) apply to tokens generally: **credentials
should be scoped to the minimum needed and short-lived**. ACP's **Shared Payment Token (SPT)** is
single-transaction (or narrowly-scoped) and merchant-specific — it is *not* the user's underlying
card number, and cannot be reused for an unrelated purchase. Visa Intelligent Commerce and
Mastercard Agent Pay issue **agent-specific tokens** distinct from the cardholder's primary card
token, each carrying its own programmable controls (spend caps, merchant-category restrictions,
expiry) — so a compromised agent token has a bounded blast radius independent of the user's actual
card.

### 3.5 HTTP 402 Revived: Machine-Native Payment Negotiation

HTTP status code 402 ("Payment Required") has existed in the spec since HTTP/1.1 but was never
standardized for actual use — until **x402**. The flow (§5.2, §6.2): a client (often an AI agent)
requests a resource; the server responds `402` with **payment requirements** in the response body
(amount, currency — typically USDC, recipient address, network); the client constructs and signs
a payment authorization, retries the request with the payment proof attached as a header; the
server (often via a **facilitator** service that verifies and submits the on-chain transaction)
validates payment and returns the resource with `200 OK`. This is designed for **per-call API
monetization** — an agent paying $0.001 per inference call to a tool-provider, where card-network
interchange fees (often $0.30 + a percentage) would make such micropayments uneconomical.

### 3.6 Identity as a Separate Layer (Skyfire's KYA)

**Skyfire**'s "Know Your Agent" (KYA) addresses a question none of the above protocols fully
solve: **is the entity on the other end of this transaction actually the agent it claims to be,
operated by the party it claims to represent?** KYA issues verifiable identity credentials to
agents (distinct from the *payment* authorization mandates of §3.1) — a counterparty can check
"is this a known, registered agent with a verifiable operator" *before* even getting to "is this
specific transaction authorized." Skyfire then layers a payment network (agent wallets, often
stablecoin-denominated) on top of this identity layer for agent-to-agent and agent-to-API
payments.

### 3.7 The Dispute/Refund/Liability Chain

When a card-present human buys the wrong item, return/refund policies and chargeback rights are
well-established. When **an agent** buys the wrong item — because of a hallucinated parameter, a
prompt injection (§10.1), or a legitimate-but-undesired interpretation of a vague Intent Mandate —
**who is liable, and how is it proven?** This is precisely why mandates are *signed, verifiable
artifacts*: the Cart Mandate is evidence of exactly what was authorized vs. what was purchased,
and the Payment Mandate's "an AI agent initiated this" signal gives issuers a basis for
agent-specific dispute-handling policies that doesn't yet have settled industry-wide norms as of
2026 — an active area where protocol design and financial regulation are still converging.

---

## 4. Types / Architectures / Strategies

| Protocol / System | Origin | Payment Rail | Primary Use Case | Authorization Model |
|---|---|---|---|---|
| **x402** | Coinbase (2025) | Stablecoin (USDC), typically on Base L2 | Machine-to-machine micropayments — pay-per-API-call, agent tool monetization | Per-request signed payment authorization (HTTP 402 flow, §3.5) |
| **AP2 (Agent Payments Protocol)** | Google + 60+ partners (Mastercard, PayPal, Coinbase, etc.) | Rail-agnostic — cards, bank transfer, stablecoins (x402 as an extension) | General agentic shopping/procurement across any payment method | Mandate chain — Intent → Cart → Payment (§3.1), built on A2A/MCP |
| **ACP (Agentic Commerce Protocol)** | OpenAI + Stripe (2025) | Card networks via Stripe, Shared Payment Token | Conversational checkout — ChatGPT "Instant Checkout" with merchant catalogs | Merchant product feed + single-use/scoped SPT (§3.4) |
| **Visa Intelligent Commerce** | Visa, with OpenAI/Microsoft/Anthropic/Perplexity/Mistral as partners | Visa network, agent-specific tokens | Card-present-equivalent agent purchases within the Visa network | Agent-specific tokens with programmable controls (spend cap, MCC restrictions) |
| **Mastercard Agent Pay** | Mastercard, with Microsoft/Stripe partnerships | Mastercard network, "Agentic Tokens" | Card-network tokenization extended to agent-initiated transactions | Agentic Tokens — extension of existing Mastercard tokenization |
| **Skyfire** | Skyfire (startup) | Agent wallets, often stablecoin | Agent identity (KYA) + agent-to-agent / agent-to-API payments | Identity credentials (KYA, §3.6) layered under payment authorization |

---

## 5. Architecture Diagrams

### 5.1 AP2 Mandate Chain (Human-Not-Present Flow)

```
  User                    Shopping Agent              Merchant / Payment Processor
   |                            |                              |
   | 1. Sign INTENT MANDATE     |                              |
   |    "shoes, size 9,         |                              |
   |     < $150, expires 7d" -->|                              |
   |                            |                              |
   |                       2. Agent searches, finds item,      |
   |                          assembles CART MANDATE            |
   |                          (exact item, $129.99, MerchantX) |
   |                            |                              |
   |                       3. Verify Cart Mandate falls         |
   |                          within Intent Mandate bounds      |
   |                          (price <= $150, category match)   |
   |                          --> bounds OK, no human prompt    |
   |                            |                              |
   |                            | 4. Generate PAYMENT MANDATE  |
   |                            |    ("AI agent-initiated")    |
   |                            |    + Cart Mandate ---------->|
   |                            |                       5. Issuer applies
   |                            |                          agent-aware risk
   |                            |                          scoring, settles
   |                            |<------ 6. Confirmation -------|
```

### 5.2 x402 Request Flow

```
  Agent (client)                      API Server                  Facilitator
       |                                   |                            |
       | 1. GET /premium-data              |                            |
       |---------------------------------->|                            |
       |                                   |                            |
       | 2. 402 Payment Required           |                            |
       |    { amount: "0.01 USDC",         |                            |
       |      network: "base",             |                            |
       |      recipient: "0xABC..." }      |                            |
       |<----------------------------------|                            |
       |                                   |                            |
       | 3. Sign payment authorization     |                            |
       |    (EIP-3009 transferWithAuth)    |                            |
       |                                   |                            |
       | 4. GET /premium-data              |                            |
       |    X-Payment: <signed auth>       |                            |
       |---------------------------------->|                            |
       |                                   | 5. Verify + settle ------->|
       |                                   |    (submit on-chain)       |
       |                                   |<---- 6. Settled -----------|
       | 7. 200 OK + data                  |                            |
       |<----------------------------------|                            |
```

### 5.3 Human-Present vs. Human-Not-Present

```
  HUMAN-PRESENT                       HUMAN-NOT-PRESENT
  -------------                       ------------------
  Agent assembles cart                User signs Intent Mandate ONCE
        |                                    (price ceiling, category,
        v                                     merchant allowlist, expiry)
  Cart Mandate shown to user                  |
  human reviews, SIGNS in real time           v
        |                              Agent runs autonomously:
        v                              for each opportunity:
  Payment proceeds                       assemble Cart Mandate
                                          check: within Intent bounds?
  (latency: seconds-to-minutes,             yes -> proceed, no human call
   per transaction)                         no  -> escalate to human
                                       (latency: near-zero per transaction,
                                        bounded by how tight the Intent
                                        Mandate's constraints are)
```

### 5.4 Layered Architecture

```
  +--------------------------------------------------------------+
  | Agent reasoning / decision layer (what to buy)                |
  +--------------------------------------------------------------+
  | Transport: A2A / MCP (how agents and tools communicate)       |
  +--------------------------------------------------------------+
  | Authorization: AP2 mandates (Intent -> Cart -> Payment)        |
  |   OR: ACP product feed + Shared Payment Token                  |
  +--------------------------------------------------------------+
  | Identity (optional layer): Skyfire KYA -- "is this a real,    |
  |   registered agent operated by who it claims?"                 |
  +--------------------------------------------------------------+
  | Settlement rail: card network (Visa/Mastercard agent tokens)   |
  |   OR stablecoin (x402, USDC) OR bank transfer                  |
  +--------------------------------------------------------------+
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 AP2-Style Mandate Chain

```python
import hashlib
import hmac
import time
from dataclasses import dataclass, field


@dataclass
class IntentMandate:
    """User-signed, bounds within which an agent can act WITHOUT further approval."""
    user_id: str
    max_price_usd: float
    category: str
    merchant_allowlist: list[str]
    expires_at: float            # unix timestamp
    signature: str = ""

    def sign(self, user_secret: bytes) -> None:
        payload = f"{self.user_id}|{self.max_price_usd}|{self.category}|{self.expires_at}"
        self.signature = hmac.new(user_secret, payload.encode(), hashlib.sha256).hexdigest()

    def is_valid(self) -> bool:
        return time.time() < self.expires_at


@dataclass
class CartMandate:
    """Agent-assembled, SPECIFIC transaction -- must be checked against an IntentMandate."""
    item_description: str
    price_usd: float
    merchant: str
    intent_mandate_ref: str       # hash/ID of the IntentMandate it claims to satisfy

    def within_bounds(self, intent: IntentMandate) -> tuple[bool, str]:
        if not intent.is_valid():
            return False, "intent mandate expired"
        if self.price_usd > intent.max_price_usd:
            return False, f"price ${self.price_usd} exceeds cap ${intent.max_price_usd}"
        if self.merchant not in intent.merchant_allowlist:
            return False, f"merchant {self.merchant} not in allowlist"
        return True, "within bounds"


@dataclass
class PaymentMandate:
    """Signal to the payment network: an AI agent initiated this, here's the cart it relied on."""
    cart: CartMandate
    agent_id: str
    initiated_by: str = "ai_agent"   # distinct risk-scoring path vs. "human_present"
    created_at: float = field(default_factory=time.time)
```

### 6.2 x402 Client Flow (Simplified)

```python
import requests
from dataclasses import dataclass


@dataclass
class X402PaymentRequirements:
    amount: str            # e.g. "0.01"
    currency: str          # e.g. "USDC"
    network: str           # e.g. "base"
    recipient: str         # on-chain address


def fetch_with_x402(url: str, wallet) -> requests.Response:
    """Fetch a resource, handling the 402 Payment Required negotiation."""
    response = requests.get(url)
    if response.status_code != 402:
        return response               # no payment required, or already paid

    requirements = X402PaymentRequirements(**response.json()["accepts"][0])

    # Sign an on-chain payment authorization (e.g., EIP-3009 transferWithAuthorization)
    # WITHOUT broadcasting it yet -- the facilitator submits it on verification.
    signed_payment = wallet.sign_transfer_authorization(
        amount=requirements.amount,
        currency=requirements.currency,
        recipient=requirements.recipient,
        network=requirements.network,
    )

    # Retry with payment proof attached
    return requests.get(url, headers={"X-Payment": signed_payment})
```

### 6.3 BROKEN -> FIX: Unscoped Payment Credential vs. Mandate-Bounded Spend

```python
# BROKEN: a procurement agent holds a single, unscoped API key that grants
# FULL access to the company's payment processor account -- any tool call
# the agent's reasoning produces can spend ANY amount, on ANYTHING. If a
# prompt injection (e.g., a malicious product description scraped during
# research, see Multi-Agent Security) causes the agent to "decide" to
# purchase 500 units of an unrelated item at $200 each, NOTHING in this
# code stops it.
class ProcurementAgentBroken:
    def __init__(self, payment_api_key: str):
        self.payment_api_key = payment_api_key   # full account access

    def purchase(self, item: str, quantity: int, unit_price_usd: float) -> dict:
        total = quantity * unit_price_usd
        return self._call_payment_api(self.payment_api_key, item, quantity, total)
        # No check against any pre-authorized bound. No human escalation.
        # $100,000 erroneous order goes through exactly like a $10 one.

    def _call_payment_api(self, key: str, item: str, qty: int, total: float) -> dict: ...
```

```python
# FIXED: the agent holds NO direct payment credential -- only the ability
# to construct CartMandates against a pre-signed IntentMandate (§6.1).
# A SpendLimitGuard enforces per-transaction AND cumulative bounds, and
# escalates to a human for anything outside the Intent Mandate's scope --
# REGARDLESS of what the agent's reasoning concluded.
class SpendLimitGuard:
    def __init__(self, intent: IntentMandate, cumulative_cap_usd: float):
        self.intent = intent
        self.cumulative_cap_usd = cumulative_cap_usd
        self.spent_so_far_usd: float = 0.0

    def authorize(self, cart: CartMandate) -> tuple[bool, str]:
        ok, reason = cart.within_bounds(self.intent)
        if not ok:
            return False, f"escalate_to_human: {reason}"
        if self.spent_so_far_usd + cart.price_usd > self.cumulative_cap_usd:
            return False, (
                f"escalate_to_human: cumulative spend "
                f"${self.spent_so_far_usd + cart.price_usd} would exceed "
                f"cap ${self.cumulative_cap_usd}"
            )
        return True, "authorized"


class ProcurementAgentFixed:
    def __init__(self, guard: SpendLimitGuard, payment_processor):
        self.guard = guard
        self.payment_processor = payment_processor   # scoped to THIS guard's mandate

    def purchase(self, cart: CartMandate) -> dict:
        authorized, reason = self.guard.authorize(cart)
        if not authorized:
            return {"status": "escalated", "reason": reason}   # human reviews

        payment_mandate = PaymentMandate(cart=cart, agent_id="procurement-agent-01")
        self.guard.spent_so_far_usd += cart.price_usd
        return self.payment_processor.settle(payment_mandate)

# A hallucinated "500 units at $200" cart now hits within_bounds() — price
# $100,000 > max_price_usd from the Intent Mandate — and is REJECTED before
# any payment API call is even attempted, escalated to a human instead.
```

---

## 7. Real-World Examples

- **Coinbase x402 (2025)** — launched as an open spec with reference facilitator
  implementations; rapidly adopted by AI infrastructure providers for **per-call API monetization**
  — an agent paying fractions of a cent per tool invocation, economically viable only because
  stablecoin transfer fees on L2s like Base are a small fraction of a cent, unlike card-network
  interchange.
- **AP2 (Google-led, 2025)** — launched with 60+ partner organizations spanning payment networks
  (Mastercard, PayPal), crypto infrastructure (Coinbase), and AI platforms — explicitly positioned
  as **payment-method-agnostic**, with x402 as one of its supported rails for the
  machine-to-machine case, and card/bank rails for consumer shopping.
- **OpenAI + Stripe ACP / ChatGPT Instant Checkout (2025)** — merchants (early partners included
  Etsy and Shopify-integrated stores) publish a product feed consumable by ChatGPT; a user can
  complete a purchase **without leaving the chat**, with Stripe issuing a Shared Payment Token
  scoped to that specific transaction and merchant.
- **Visa Intelligent Commerce** — Visa's program partners with OpenAI, Microsoft, Anthropic,
  Perplexity, and Mistral to let AI agents initiate Visa-network transactions using
  **agent-specific tokens** distinct from the cardholder's primary card credentials, with
  issuer-configurable controls.
- **Mastercard Agent Pay** — extends Mastercard's existing tokenization infrastructure (the same
  underlying system that powers tokenized mobile-wallet payments) with **Agentic Tokens**,
  partnering with Microsoft (Azure-hosted agents) and Stripe for merchant-side integration.
- **Skyfire** — positions its KYA identity layer and agent-wallet payment network as
  infrastructure for **agent-to-agent API marketplaces** — e.g., a research agent paying a
  specialized data-provider agent per query, where both sides need to verify "is the counterparty
  a legitimate, registered agent" before any payment logic runs at all.

---

## 8. Tradeoffs

### Settlement Rail: Stablecoin (x402/Skyfire) vs. Card Network (ACP/Visa/Mastercard)

| | Stablecoin (x402, Skyfire) | Card Network (ACP, Visa IC, Mastercard Agent Pay) |
|---|---|---|
| Per-transaction fee at small amounts (<$1) | Near-zero (L2 gas fees) — viable for micropayments | Interchange fees (often $0.30 + %) make sub-$1 transactions uneconomical |
| Settlement speed | Seconds (on-chain finality on L2s) | Typically T+1 or T+2 for merchant settlement, though authorization is real-time |
| Regulatory clarity (as of 2026) | Evolving — stablecoin regulation varies by jurisdiction | Mature — decades of card-network regulatory frameworks |
| Merchant acceptance | Limited to merchants/APIs integrating x402 or Skyfire directly | Near-universal — leverages existing card-accepting merchant base |
| Dispute/chargeback mechanisms | Limited — on-chain transactions are largely final | Mature chargeback/dispute processes, now extended with agent-initiated signals (§3.7) |

### Authorization Granularity: Mandate Chain (AP2) vs. Single Scoped Token (ACP/Visa/Mastercard)

| | AP2 Mandate Chain | Single Scoped Token (SPT, Agentic Token) |
|---|---|---|
| Auditability | Each mandate (Intent/Cart/Payment) is independently verifiable evidence (§3.7) | Token scope is defined at issuance; less granular per-transaction evidence trail |
| Cross-rail portability | Same mandate structure works across cards, bank transfer, stablecoins | Tied to the issuing network's token format |
| Implementation complexity | Higher — requires VC infrastructure, mandate verification logic | Lower — closer to existing tokenization flows merchants/issuers already support |
| Best fit | Multi-rail platforms, complex authorization policies (category + price + merchant + time bounds) | Single-rail checkout flows (e.g., conversational commerce within one card network) |

### Human-Present vs. Human-Not-Present (§3.2)

| | Human-Present | Human-Not-Present |
|---|---|---|
| Per-transaction friction | High — real-time review/signature | Near-zero, within Intent Mandate bounds |
| Autonomy / scale | Low — bounded by human availability | High — agent can transact continuously |
| Risk if bounds are too loose | Limited — human catches it at review | Higher — errors execute before any human sees them (mitigated by §6.3's guard) |
| Appropriate for | High-value, infrequent, or novel-merchant purchases | Recurring, bounded, well-understood categories (subscription renewals, routine restocking, per-call API payments) |

---

## 9. When to Use / When NOT to Use

**Use x402 when:**

- The transaction is a **machine-to-machine micropayment** — an agent paying per API call, per
  inference, or per data query — where card-network fees would exceed or dominate the transaction
  amount.
- Both sides of the transaction are comfortable with **stablecoin settlement** and the regulatory
  environment for the relevant jurisdictions supports it.

**Use AP2 when:**

- The system needs to support **multiple payment rails** under one authorization model, or needs
  **fine-grained, auditable authorization policies** (price ceilings, category restrictions,
  merchant allowlists, time bounds) that a single token's scope can't expressively capture.
- The use case spans both **human-present** (occasional high-value purchases) and
  **human-not-present** (routine recurring purchases) flows for the same user/agent.

**Use ACP when:**

- The integration target is specifically **conversational commerce within an existing assistant
  platform** (e.g., ChatGPT) and the merchant already has a Stripe relationship — ACP's
  product-feed + SPT model is purpose-built for this checkout-in-chat pattern.

**Use Visa Intelligent Commerce / Mastercard Agent Pay when:**

- The organization already operates within that **card network's existing merchant/issuer
  relationships** and wants agent-specific controls layered onto infrastructure it already trusts
  and has compliance processes for.

**Do NOT, regardless of protocol:**

- **Give an agent direct, unscoped access to a payment processor account or raw card credentials**
  (§6.3 BROKEN) — every protocol in this module exists specifically to avoid this pattern; reverting
  to it defeats the purpose of adopting any of them.
- **Rely on prompt-based spending instructions as the only safeguard** ("don't spend more than
  $X") — this is a suggestion to a probabilistic system, not an enforcement mechanism; the
  enforcement must be structural (mandate bounds, `SpendLimitGuard`, token-level caps) and
  independent of what the agent's reasoning concludes (§6.3 FIX).
- **Set human-in-the-loop thresholds without revisiting them** — a cumulative cap set once and
  never re-evaluated either becomes an operational bottleneck (too low, defeating automation
  value) or a stale, overly-permissive ceiling (too high relative to current transaction patterns)
  — see Pitfall 10.4.

---

## 10. Common Pitfalls

**10.1 Prompt Injection Leading to Unauthorized Spend**

If an agent's purchasing decisions are influenced by content it reads during research — a
malicious product description, a compromised price-comparison page — and that content can
manipulate the agent's tool-call parameters, an unscoped payment credential turns a **content
manipulation attack into a financial loss** (§6.3 BROKEN). The fix is the same principle as
[Multi-Agent Security](multi_agent_security.md)'s broader guidance: **never let externally-sourced
content directly determine the parameters of a privileged action** — the `SpendLimitGuard`'s
bounds-check (§6.3 FIXED) is independent of *why* the agent decided on a given cart, only *whether
that cart falls within pre-authorized, human-set bounds*.

**10.2 Conflating Identity Verification with Spend Authorization**

Skyfire's KYA (§3.6) answers "is this a legitimate, registered agent?" — it does **not** answer
"is THIS SPECIFIC TRANSACTION authorized?" A system that checks KYA and then proceeds to execute
arbitrary-amount transactions has solved counterparty-identity risk while leaving
transaction-authorization risk completely open. These are independent layers (§5.4) — both are
needed, and neither substitutes for the other.

**10.3 Treating Mandate Signatures as Sufficient Without Considering Key Compromise**

A signed Cart Mandate is strong evidence of *what was authorized*, provided the signing key itself
is secure. If an agent's signing key (used to generate Cart Mandates within Intent Mandate bounds,
§3.1) is compromised, an attacker can generate *validly-signed* Cart Mandates for any transaction
within those bounds — the signature proves the mandate came from that key, not that the legitimate
agent's *reasoning* produced it. Key management for agent-held signing keys deserves the same
rigor as [MCP Security](../mcp_model_context_protocol/mcp_security.md)'s treatment of
service-to-service credentials — short rotation periods, hardware-backed storage where possible,
and monitoring for anomalous mandate-generation patterns.

**10.4 Static Human-in-the-Loop Thresholds**

A cumulative spend cap or per-transaction threshold set at system launch, based on initial
estimates of "typical" transaction sizes, becomes miscalibrated as usage patterns evolve — too
restrictive as legitimate use scales (every transaction escalates, defeating automation), or too
permissive if the agent's task scope expands beyond what the original threshold anticipated.
Thresholds should be **reviewed against actual transaction-size distributions** on a recurring
basis, the same operational discipline [Safety & Alignment](../safety_and_alignment/README.md)'s
"one-time red teaming" pitfall warns against applied to financial controls.

**10.5 Ignoring the Liability Question Until a Dispute Occurs**

Teams adopt agentic-commerce protocols for their *authorization* benefits but don't pre-establish
**who is liable when an agent-initiated transaction is disputed** — the user, the platform
operating the agent, or the merchant. The Payment Mandate's "AI agent-initiated" signal (§3.1)
exists partly to give issuers a basis for agent-aware dispute policies, but those policies are
still maturing industry-wide as of 2026 (§3.7) — organizations deploying agentic commerce should
have an explicit internal policy for this scenario *before* the first dispute, not after.

---

## 11. Technologies & Tools

| Tool / Protocol | Role |
|---|---|
| **x402** | Open spec (Coinbase) for HTTP 402-based stablecoin micropayments; reference facilitator implementations for verification/settlement |
| **AP2 (Agent Payments Protocol)** | Google-led open protocol; mandate (Intent/Cart/Payment) chain, built on A2A/MCP, rail-agnostic |
| **ACP (Agentic Commerce Protocol)** | OpenAI + Stripe; product feed + Shared Payment Token; powers ChatGPT Instant Checkout |
| **Visa Intelligent Commerce** | Visa's agent-payment program; agent-specific tokens with programmable controls |
| **Mastercard Agent Pay** | Mastercard's Agentic Tokens, extending existing tokenization for agent-initiated transactions |
| **Skyfire** | Agent identity (KYA) + agent-wallet payment network for agent-to-agent / agent-to-API payments |
| **Stablecoins (USDC on Base, etc.)** | Settlement asset for x402 and similar micropayment rails |
| **EIP-3009 (`transferWithAuthorization`)** | Ethereum token standard enabling the "sign now, settle later via facilitator" pattern x402 relies on |

---

## 12. Interview Questions with Answers

**Q1: What problem do agentic commerce protocols solve that simply giving an agent your payment credentials does not?**
Giving an agent raw payment credentials means any tool call the agent's reasoning produces can spend any amount on anything — there's no structural limit, only the hope that the agent "behaves." Agentic commerce protocols (AP2's mandates, ACP/Visa/Mastercard's scoped tokens) make authorization a **separate, structural layer** — a signed artifact defining bounds (price, category, merchant, time) that exists independently of, and constrains, whatever the agent's reasoning concludes. The practical difference: a hallucinated or injected "buy 500 units at $200 each" either gets rejected by a bounds-check (§6.3 FIXED) or never becomes possible because the credential itself can't authorize it (§3.4) — versus an unscoped credential where it executes exactly like a legitimate $10 purchase (§6.3 BROKEN).

**Q2: Walk through AP2's three-mandate chain — Intent, Cart, Payment — and explain why they're separate rather than one combined authorization.**
The Intent Mandate (§3.1) captures *general, advance authorization* — "shoes, size 9, under $150" — signed once, often well before any specific purchase opportunity exists. The Cart Mandate captures the *specific transaction* an agent later assembles — exact item, exact price, exact merchant — checked against the Intent Mandate's bounds. The Payment Mandate is a *signal to the payment network* that an AI agent initiated this, separate from authorization itself, enabling agent-aware risk scoring. Separating these lets the Intent Mandate be signed once and reused for many Cart Mandates (enabling human-not-present flows, §3.2) while still producing, for each individual transaction, a specific auditable record of exactly what was bought and at what price — collapsing them into one mandate would either require re-authorization per transaction (defeating automation) or lose the per-transaction audit trail.

**Q3: Why does x402 use stablecoins rather than existing card-network rails for machine-to-machine micropayments?**
Card-network interchange fees are typically a fixed component (often around $0.30) plus a percentage — for a $0.001 API call, the fee would be orders of magnitude larger than the transaction itself, making such micropayments economically impossible on card rails. Stablecoin transfers on low-fee L2 networks (e.g., Base) have per-transaction costs that are a small fraction of a cent, making sub-cent payments viable. This is the core economic argument for x402's design choice — it's not about avoiding card networks generally, but specifically about a transaction-size regime (sub-dollar, often sub-cent, machine-initiated, high-frequency) where card economics simply don't work.

**Q4: How does AP2 relate to x402 — are they competitors?**
They're complementary, not competitors — AP2 is explicitly designed as **rail-agnostic**, with x402 as one of its supported payment-rail extensions specifically for the machine-to-machine micropayment case (§3.3). A system could use AP2's mandate chain for the authorization layer (Intent → Cart → Payment Mandate, capturing what was authorized and by whom) while settling the actual transaction via x402 (for an agent-to-agent API payment) or via a card-network token (for a consumer purchase) — same authorization structure, different settlement rail depending on the transaction's economics. This layered design (§5.4) is precisely what lets AP2 partner with both card networks (Mastercard, PayPal) and crypto infrastructure (Coinbase) simultaneously.

**Q5: What's the difference between ACP's Shared Payment Token and the cardholder's actual card number?**
The Shared Payment Token (SPT, §3.4) is a **scoped, often single-transaction credential** issued by Stripe for a specific checkout — it authorizes a specific transaction (or narrowly-defined set of transactions) with a specific merchant, and cannot be reused for unrelated purchases or extracted to make charges elsewhere. The cardholder's actual card number, by contrast, if exposed, could be used for any transaction anywhere that accepts it. This is the same principle as OAuth access tokens vs. a user's actual password — the SPT is a delegated, bounded credential, not the underlying credential itself. If an SPT is somehow exposed or logged, the blast radius is bounded to what that specific token authorizes.

**Q6: A team wants to deploy an autonomous shopping agent with no human review for purchases under $50. How would you implement the bound, and why shouldn't it be a prompt instruction?**
Implement it as an Intent Mandate (§6.1) with `max_price_usd=50`, enforced by a `SpendLimitGuard.authorize()` check (§6.3) that runs on every Cart Mandate **before** any payment API call — this is a structural check independent of the agent's reasoning. A prompt instruction ("don't approve purchases over $50 without asking") is a *request* to a probabilistic system; if the agent's context is manipulated (prompt injection from a scraped page, §10.1) or the model simply makes an error, there's nothing preventing a $5,000 "decision" from reaching the payment API — the instruction was advisory, not enforced. The structural check rejects the Cart Mandate regardless of *how* the agent arrived at a $5,000 cart, because $5,000 > $50 is checked in code, not in the model's adherence to instructions.

**Q7: How do Visa Intelligent Commerce and Mastercard Agent Pay differ from AP2, conceptually?**
Visa Intelligent Commerce and Mastercard Agent Pay (§4) are **card-network-specific token programs** — they extend each network's existing tokenization infrastructure with agent-aware tokens and controls, operating *within* that network's existing merchant/issuer relationships and rails. AP2 is a **cross-network, cross-rail protocol** — its mandate structure (§3.1) doesn't belong to any single payment network and is designed to work whether the underlying settlement happens via Visa, Mastercard, a bank transfer, or a stablecoin. In practice, an AP2-based system's Payment Mandate could ultimately settle *through* a Visa Intelligent Commerce token — the protocols can compose (§Q4's point generalizes beyond just x402).

**Q8: What does Skyfire's "Know Your Agent" (KYA) actually verify, and what does it NOT verify?**
KYA verifies **agent identity and operator legitimacy** — that the entity initiating a request or transaction is a registered, identifiable agent operated by a known party, analogous to how KYC (Know Your Customer) verifies human identity in financial services. It does **not** verify that any *specific transaction* from that agent is authorized — a legitimately-identified agent could still attempt an unauthorized transaction if its authorization layer (mandates, tokens) is separately compromised or misconfigured (§10.2). KYA and transaction authorization are independent layers (§5.4) that must both be correctly implemented — verifying one does not imply the other.

**Q9: In the human-present vs. human-not-present tradeoff (§3.2, §8), what determines where the line should be drawn for a given application?**
The line should be drawn based on (a) **transaction value relative to the user's risk tolerance** — high-value or rare purchases favor human-present review even if it adds friction; (b) **predictability of the transaction category** — recurring, well-bounded categories (subscription renewals, routine restocking within known SKUs) are good human-not-present candidates because an Intent Mandate's bounds can tightly characterize "normal" for that category; and (c) **reversibility** — categories with strong refund/dispute mechanisms (most card-network purchases) tolerate human-not-present errors better than largely-irreversible settlement (some stablecoin transfers, §8's dispute-mechanism row). There's no universal threshold — a $20 grocery restock and a $20 one-off purchase from an unfamiliar merchant carry very different risk profiles despite identical dollar amounts, because of (b) and (c).

**Q10: Why is the "Payment Mandate" — the signal that an AI agent initiated a transaction — useful to a card issuer, given that the transaction is already authorized by the Cart Mandate?**
Authorization (Cart Mandate, within Intent Mandate bounds) establishes that the transaction is *legitimate per the user's prior consent*. The Payment Mandate's "AI-agent-initiated" signal is a separate, *risk-scoring* input — issuers' fraud-detection models are typically trained on patterns of human-initiated transactions (timing, sequence, typical categories per user); an AI agent operating autonomously may produce transaction patterns (e.g., many small purchases in rapid succession, or purchases at unusual hours) that would look anomalous for a human but are normal for an agent operating within its Intent Mandate. Flagging "this is agent-initiated" lets the issuer apply a different risk model rather than either false-flagging legitimate agent activity as fraud, or — the opposite failure — missing genuinely fraudulent activity because it superficially resembles "normal" agent patterns.

**Q11: How does the dispute/liability problem (§3.7, §10.5) differ between a human-present and a human-not-present agentic purchase?**
In a human-present flow, the human directly signed the Cart Mandate for that specific transaction — disputing it requires arguing the signed cart didn't match what was delivered/described, similar to traditional purchase disputes. In a human-not-present flow, the user signed only the *general* Intent Mandate, and the dispute question becomes "did this specific Cart Mandate genuinely fall within the bounds I authorized, and was the bounds-check correctly implemented?" — shifting some of the evidentiary burden toward the *system's* mandate-verification logic rather than purely the transaction record. This is why §10.5 emphasizes establishing liability policy in advance — human-not-present flows introduce a new category of dispute ("the system's interpretation of my Intent Mandate was wrong") that doesn't map cleanly onto pre-agentic dispute categories.

**Q12: Could an agent operating under AP2 use x402 to pay another agent for a sub-task, within a single end-to-end user-facing transaction? Sketch how the mandate chain would look.**
Yes — this is exactly the layered-composition AP2 is designed for (§Q4). The end-user signs an Intent Mandate for the overall task (e.g., "plan and book a trip under $2,000"). The orchestrating agent assembles a Cart Mandate for the overall trip cost. As part of fulfilling this, the orchestrating agent might pay a specialized flight-search agent $0.05 per query via x402 (§6.2) — this sub-payment is a machine-to-machine micropayment, settled independently, and would itself need to be accounted for within the overall Cart Mandate's total or treated as an operating cost the orchestrating agent absorbs (a system-design decision, not dictated by either protocol). The key point: x402's per-call micropayments and AP2's user-facing mandate chain operate at different layers and granularities, and a well-designed system keeps the user-facing authorization (Intent/Cart Mandate totals) accurate regardless of how many internal x402 micropayments the orchestrating agent makes to accomplish the task.

**Q13: What's the relationship between this module and Agent-to-Agent Protocols' confused-deputy pitfall (Pitfall 5, "forwarding tokens between agents")?**
The confused-deputy pattern in [Agent-to-Agent Protocols §10, Pitfall 5](agent_to_agent_protocols.md) — an orchestrator forwarding the caller's token to a specialist agent, giving the specialist whatever permissions the caller's token carries — has a direct financial analogue here: an orchestrating agent forwarding its **payment mandate or token** to a sub-agent it delegates to, giving that sub-agent the same spending authority as the orchestrator, regardless of whether the sub-task actually requires it. The fix is the same principle: the sub-agent should operate under its **own**, narrowly-scoped mandate/token (e.g., an x402 micropayment budget specific to its sub-task) rather than inheriting the orchestrator's full Cart/Intent Mandate bounds — least privilege applies to delegated spending authority exactly as it does to delegated data access.

**Q14: If an agentic-commerce system has both a `SpendLimitGuard` (§6.3) AND issuer-side agent-specific token controls (Visa/Mastercard, §3.4), is one of these redundant?**
No — they're defense-in-depth at different layers and different points of failure. The `SpendLimitGuard` is **application-side**, enforced by the agent operator's own code, and can encode business-specific logic (category restrictions, per-merchant rules, cumulative caps tied to a specific Intent Mandate) that an issuer's token controls may not express as granularly. The issuer-side token controls are **independent of the application's code being correct** — if the `SpendLimitGuard` has a bug, is bypassed, or the agent's code is compromised entirely, the issuer-side cap on the agent-specific token is a second, independently-enforced ceiling that doesn't rely on the application behaving correctly. This mirrors the general security principle (also seen in §8.3 of [Automated Jailbreak Algorithms](../safety_and_alignment/automated_jailbreak_algorithms.md)) that a single defense layer, however well-implemented, is a single point of failure.

**Q15: How would you design the Intent Mandate's expiry and scope for a recurring-purchase agent (e.g., automatically reordering office supplies) versus a one-off research-and-purchase agent (e.g., "find and buy the best-reviewed ergonomic chair under $400")?**
The recurring agent's Intent Mandate should have a **longer expiry** (e.g., 90 days, renewed periodically) with a **tight category/merchant allowlist** (specific office-supply vendors, specific product categories) and a **per-transaction cap** close to historical typical order sizes — because the transaction pattern is predictable, bounds can be tight without causing false escalations. The one-off agent's Intent Mandate should have a **short expiry** (e.g., 24-48 hours — just enough for the research-and-purchase task) with a **single price ceiling** ($400) but a **broad or empty merchant allowlist** (since "best-reviewed" might point to any retailer) — here, the bound that matters most is the price ceiling and the short time window limiting how long that authorization remains exploitable, since category/merchant constraints would be too restrictive for an open-ended research task. The general principle: **tighten the dimensions where the task is predictable, and rely on time-bound + price-ceiling as the safety net for dimensions where it isn't.**

**Q16: What's a concrete way "context rot" or long-running-agent issues (from other modules) could manifest specifically as a financial risk in an agentic-commerce system?**
A long-running procurement agent operating under a human-not-present Intent Mandate (§3.2) across many tool calls and a growing context could, late in a long session, lose track of *how much of its cumulative cap it has already used* if that tracking relies on the agent's own context rather than the external `SpendLimitGuard`'s state (§6.3) — the agent might "believe" (based on degraded recall of earlier context) that it has more remaining budget than it does, and attempt a transaction that should be rejected. This is precisely why §6.3's FIXED design keeps `spent_so_far_usd` in the **guard's own state**, external to the agent's context — the enforcement must not depend on the agent's own (potentially degraded) recall of its transaction history, the same architectural lesson as keeping authentication/authorization state external to an LLM's context window rather than trusting the model to "remember" what it's allowed to do.

---

## 13. Best Practices

1. **Never grant agents direct, unscoped payment credentials** (§6.3, §9) — every protocol in this module exists to provide a scoped, structural alternative.
2. **Enforce spend bounds as code (`SpendLimitGuard`), not as prompt instructions** (§Q6) — structural enforcement is independent of the model's adherence to instructions.
3. **Keep cumulative-spend tracking external to the agent's context** (§Q16) — don't rely on the agent's own recall of "how much have I spent so far."
4. **Layer identity verification (KYA) and transaction authorization (mandates/tokens) independently** (§10.2) — neither substitutes for the other.
5. **Choose Intent Mandate bounds based on predictability and reversibility of the transaction category** (§Q15), not a one-size-fits-all threshold.
6. **Treat agent signing-key compromise as a first-class threat model** (§10.3) — apply the same key-rotation rigor as service-to-service credentials.
7. **Use x402 (or similar micropayment rails) specifically for sub-dollar machine-to-machine transactions** — card-network economics don't work at that scale (§Q3).
8. **Apply least-privilege to delegated spending authority across agent hierarchies** (§Q13) — a sub-agent's mandate/token should be scoped to its sub-task, not inherited wholesale from the orchestrator.
9. **Establish liability and dispute-handling policy for agent-initiated transactions BEFORE the first dispute** (§10.5) — this is still an evolving area industry-wide, and ad hoc resolution is worse than a pre-defined policy.
10. **Re-evaluate human-in-the-loop thresholds against actual transaction-size distributions on a recurring cadence** (§10.4) — static thresholds drift out of calibration as usage evolves.

---

## 14. Case Study

**Scenario**: A mid-size manufacturing company deploys an autonomous procurement agent to handle
routine restocking of shop-floor consumables (fasteners, lubricants, safety equipment) across
12 approved suppliers.

**Design**: The procurement team signs a single Intent Mandate per quarter: category =
"shop-floor consumables," merchant allowlist = the 12 approved suppliers, `max_price_usd=2000`
per transaction, `cumulative_cap_usd=50000` per quarter, expiry = end of quarter. The agent runs
continuously, monitoring inventory levels and generating Cart Mandates for restock orders;
each Cart Mandate passes through `SpendLimitGuard.authorize()` (§6.3) before any payment API call.

**Incident and response**: in week 6, a supplier's product catalog (scraped by the agent during a
routine price-comparison check) contained a manipulated entry listing a $50,000 "bulk pallet"
SKU at what appeared to be a routine consumables price due to a unit-of-measure parsing error on
the agent's part (1 pallet = 500 units, agent computed per-unit price incorrectly, producing a
Cart Mandate for $50,000 against what should have been a ~$300 order). The `SpendLimitGuard`
rejected the Cart Mandate — $50,000 exceeded both the $2,000 per-transaction cap and would have
exhausted the entire quarterly cumulative cap in one transaction — escalating to a human reviewer,
who identified the unit-of-measure error and corrected the agent's product-catalog parser.

**Quantified outcome**: zero erroneous charges reached the payment processor; the escalation rate
for the quarter was 3 out of 1,140 total restock transactions (0.26%) — all 3 were genuine
parsing/data-quality issues caught before any payment, not false positives from overly-tight
bounds. The $2,000 per-transaction cap, set based on the largest historical single restock order
($1,400) plus margin, proved well-calibrated: tight enough to catch the $50,000 anomaly, loose
enough that zero legitimate transactions were escalated.

**Transferable lesson**: the value of structural bounds isn't that they prevent the agent from
making mistakes — the agent's unit-of-measure parsing error still happened — it's that **the
mistake's financial consequence was contained to "wasted agent compute and one human review,"
not "$50,000 erroneous charge,"** because the enforcement layer (§6.3) is independent of, and
doesn't trust, the agent's own computation.

---

## Related

- [Agent-to-Agent Protocols](agent_to_agent_protocols.md) — the A2A/MCP transport layer this module's protocols build on; confused-deputy pattern (§Q13) generalizes from tokens to payment mandates
- [Multi-Agent Security](multi_agent_security.md) — prompt injection and trust-boundary failures that make unscoped payment credentials dangerous (§10.1)
- [Agent Reliability](../agents_and_tool_use/agent_reliability.md) — the broader reliability patterns (runaway loops, hallucinated parameters) whose financial analogues this module addresses
- [MCP Security](../mcp_model_context_protocol/mcp_security.md) — credential scoping and key-management principles applied to payment signing keys (§10.3)
- [Safety & Alignment](../safety_and_alignment/README.md) — "one-time red teaming" pitfall, generalized to static spend-threshold calibration (§10.4)
- [Multi-Agent Systems README](README.md) — parent module: orchestration patterns this module's agents participate in
