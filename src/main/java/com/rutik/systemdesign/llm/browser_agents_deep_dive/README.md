# Browser Agents — Deep Dive

## 1. Concept Overview

Browser agents are LLM agents that navigate and interact with web pages — clicking, typing, scrolling, filling forms, extracting data — to accomplish tasks on sites without official APIs. They complement the more general "computer use" pattern by specializing on web interfaces, where DOM structure provides richer context than raw pixels and where browser automation tools (Playwright, Puppeteer) provide reliable primitives.

The 2024-2025 browser-agent ecosystem includes Browser Use (Python, LLM-controlled Playwright with hybrid DOM+vision extraction), Stagehand (TypeScript, Browserbase-backed, vision+DOM), Playwright MCP (Anthropic's official MCP server for Playwright control), Browserbase (cloud browser infrastructure), and Skyvern (form-heavy automation). On the WebArena benchmark of real-world web tasks, leading agents achieve ~58% success (Browser Use with Claude Sonnet 4 + DOM extraction); pure-screenshot approaches hover around 30%.

The fundamental design choice — DOM extraction vs vision vs hybrid — drives reliability, latency, and cost.

---

## Intuition

**One-line analogy**: A browser agent is like a Selenium test suite where the test cases are written by the LLM at runtime — flexible enough to handle never-seen sites, but as fragile as any DOM-based automation.

**Mental model**: Three extraction strategies in a spectrum. **DOM-based** (accessibility tree, semantic selectors): fast (~50-200ms per page), 95%+ accuracy on well-built sites, fails on Canvas/PDF/dynamic visuals. **Vision-based** (screenshot + multimodal LLM): slow (~1-3s), handles any visual UI, expensive, error-prone on small text. **Hybrid** (DOM first, screenshot for ambiguity): best of both, current state of the art.

**Why it matters**: Many tasks have no API: research a competitor's pricing page, fill an expense report in a legacy system, monitor a job listing site, complete a vendor portal workflow. Browser agents unlock automation for the 95% of web that lacks programmatic access.

**Key insight**: The accessibility tree is dramatically better than raw HTML for LLM consumption. It's smaller (10-100× fewer tokens), structured for assistive technology (labels, roles, states), and gives the LLM a cleaner mental model of the page. The shift from HTML scraping to accessibility tree extraction is what made browser agents practical.

---

## 2. Core Principles

- **Accessibility tree first**: cleaner, smaller than HTML; designed for semantic interpretation.
- **Vision as fallback**: when DOM is opaque (Canvas, custom widgets), screenshot + multimodal LLM.
- **Semantic selectors**: locate by role+name, not CSS/XPath — survives DOM changes.
- **Action verification**: after click/type, verify the page changed as expected.
- **Auth via session persistence**: save browser state (cookies, localStorage) to JSON, reuse.
- **Captcha as escalation**: detect → solve via 2captcha/CapSolver OR escalate to human.
- **Site-specific tuning**: hard-to-automate sites get custom recipes (DOM hints, wait conditions).

---

## 3. Types / Architectures / Strategies

### 3.1 Browser Use (Python)

Open-source library. Wraps Playwright. Default flow: extract accessibility tree → LLM picks action → execute → re-extract. Strong with Claude Sonnet 4 (~58% WebArena). Modular: swap models, customize extraction.

### 3.2 Stagehand (TypeScript)

Browserbase team. Three primitives: `page.act("click sign up")`, `page.extract({schema, instruction})`, `page.observe()`. Vision+DOM hybrid. Tight Browserbase integration for cloud browser infrastructure.

### 3.3 Playwright MCP (Anthropic)

Official MCP server exposing Playwright operations as tools. Standard tool interface; works with Claude, Claude Code, any MCP client. Lowest-friction integration for agent systems already on MCP.

### 3.4 Browserbase (Cloud Infrastructure)

Managed browser-as-a-service: stealth-mode browsers running in cloud, session persistence, captcha solving, debugging UI. Used as the runtime by Stagehand and others.

### 3.5 Skyvern

Form-filling specialist. Combines vision + LLM for complex multi-step web workflows (insurance claims, government forms).

### 3.6 Anthropic Computer Use

Generic computer control (screenshot + click/type/key) — works on browsers but not browser-specialized. Lower accuracy on web tasks than DOM-aware approaches.

---

## 4. Architecture Diagrams

```
Browser Agent Loop
===================

  User task: "Find a flight from NYC to SF next Friday under $300"
        |
        v
  Open browser, navigate to search site
        |
        v
  +--------------------+
  | Extract DOM /      |
  | accessibility tree |
  +--------------------+
        |
        v
  LLM sees:
    "Heading: Search Flights"
    "Input role=textbox label='From'"
    "Input role=textbox label='To'"
    "Input role=textbox label='Date'"
    "Button role=button text='Search'"
        |
        v
  LLM decides next action:
    type 'NYC' in From input
        |
        v
  Execute action via Playwright
        |
        v
  Verify (new DOM state)
        |
        v (loop)
  Continue until task complete


DOM Extraction Strategies
==========================

  Raw HTML:
    <div class="x-3kj"><button class="btn-prim _f8" data-id="..">Sign Up</button></div>
    (200KB; meaningless class names; useless to LLM)

  Accessibility Tree:
    button "Sign Up" [role=button, focusable=true]
    (5-50KB; semantic; LLM-friendly)

  Vision-only (screenshot):
    PNG image
    (10-100KB image, ~500-2000 tokens for analysis; slower)

  Hybrid (typical):
    Accessibility tree
    + screenshot ONLY if action target ambiguous


Semantic Selector Robustness
=============================

  DOM:
    <button id="btn-1234" class="x-shr-7"></button>  <-- changes every deploy
    role: button
    accessible name: "Submit Order"               <-- stable

  CSS selector:  "#btn-1234"                       BREAKS on next deploy
  XPath:         "//button[contains(@class,'x-shr-7')]"  BREAKS
  Semantic:      "button with text 'Submit Order'"  STABLE
```

---

## 5. How It Works — Detailed Mechanics

### Browser Use Example

```python
import asyncio
from browser_use import Agent
from browser_use.llm import ChatAnthropic

async def main():
    agent = Agent(
        task="Find the cheapest flight from JFK to SFO on December 15, 2025 on Google Flights and report the airline and price",
        llm=ChatAnthropic(model="claude-sonnet-4-6"),
        max_actions_per_step=3,  # Allow chained actions
        max_failures=5,
        use_vision=True,  # Fall back to vision when DOM ambiguous
    )
    
    result = await agent.run()
    print(result.final_result())

asyncio.run(main())
```

### Stagehand (TypeScript) Example

```typescript
import { Stagehand } from "@browserbasehq/stagehand";
import { z } from "zod";

const stagehand = new Stagehand({
  env: "BROWSERBASE",  // or "LOCAL"
  apiKey: process.env.BROWSERBASE_API_KEY!,
  projectId: process.env.BROWSERBASE_PROJECT_ID!,
  modelName: "claude-sonnet-4-6",
  modelClientOptions: { apiKey: process.env.ANTHROPIC_API_KEY! },
});

await stagehand.init();
await stagehand.page.goto("https://news.ycombinator.com");

// Natural-language action
await stagehand.page.act("Click on the first story link");

// Structured extraction
const result = await stagehand.page.extract({
  instruction: "extract the article title and first paragraph",
  schema: z.object({
    title: z.string(),
    firstParagraph: z.string(),
  }),
});

console.log(result);
await stagehand.close();
```

### Playwright MCP via Claude

```bash
# Run the Playwright MCP server
npx @anthropic/mcp-server-playwright
```

```python
# Claude (any MCP client) connects; Playwright tools available:
# - browser_navigate(url)
# - browser_click(selector)
# - browser_type(selector, text)
# - browser_screenshot()
# - browser_evaluate(js)
# Agent calls these like any other MCP tool
```

### Session Persistence for Auth

```python
# Save authenticated session
context = await browser.new_context()
page = await context.new_page()
await page.goto("https://internal-portal.company.com/login")
await page.fill("#username", "user@example.com")
await page.fill("#password", PASSWORD)
await page.click("button[type=submit]")
await context.storage_state(path="auth.json")  # Save cookies + localStorage

# Reuse in agent
context = await browser.new_context(storage_state="auth.json")
# Now logged in; agent starts at authenticated state
```

---

## 6. Real-World Examples

**Devin** uses browser automation for web research, form filling, and integration testing during coding tasks.

**Claude Computer Use demos** include browser navigation for shopping, booking, research.

**Skyvern** automates insurance enrollment forms; widely used by health-tech companies.

**Browser Use community** has built agents for: job application automation, e-commerce price monitoring, real estate listing aggregation, university course enrollment, vendor portal data entry.

**Stagehand customers** include LangChain's web research backend, AI sales tools, BrowserOS, etc.

---

## 7. Tradeoffs

| Approach | Speed | Accuracy | Cost | Site Compatibility |
|---|---|---|---|---|
| DOM-only | Fastest (50-200ms/page) | 95%+ on standard sites | Cheapest | Poor on Canvas/PDF/custom widgets |
| Vision-only (screenshot) | Slowest (1-3s) | ~70-80% on standard sites | Most expensive (image tokens) | Universal (any visual UI) |
| Hybrid DOM+Vision | Medium (~500ms) | 95% standard + ~80% on hard sites | Mid | Best overall |
| Computer Use (generic) | Slow (multi-second per action) | ~60-70% on web | High | Universal |
| Stagehand semantic actions | Fast (DOM-first) | 90%+ | Mid | Excellent for natural language |

WebArena benchmark scores (approximate, 2024-2025):
- Browser Use + Claude Sonnet 4 + DOM: **~58%**
- Stagehand + Claude Sonnet 4: ~50%
- GPT-4 + Computer Use: ~30-40%
- SeeAct + GPT-4V (screenshot): ~30%

---

## 8. When to Use / When NOT to Use

**Use browser agents when:**
- Target site has no API
- Workflow spans multi-step UI (login → search → fill form → submit)
- Site is mostly text-based or accessible (DOM-rich)
- One-off or low-frequency automation
- Research/scraping tasks at human-scale (not industrial scraping)

**Don't use when:**
- API available (use it — orders of magnitude faster, cheaper, more reliable)
- High-frequency automation (DOM brittleness causes pain; investigate official integration)
- Sites with aggressive bot protection (Cloudflare BOT-management, captchas every request)
- Heavy-Canvas applications (Google Sheets, Figma) where DOM is opaque

---

## 9. Common Pitfalls

### Pitfall 1: CSS selectors that break on next deploy

```python
# BROKEN: brittle CSS selector
await page.click(".btn-primary._x-7f3kj")
# Next deploy changes class hash; agent fails silently
```

```python
# FIXED: semantic locator
await page.get_by_role("button", name="Sign Up").click()
# Or via accessible name — survives CSS refactors
```

### Pitfall 2: No wait for dynamic content

```python
# BROKEN: agent clicks then reads page before async load completes
await page.click("Search")
title = await page.locator("h1").text_content()  # Returns old page's title
```

```python
# FIXED: wait for specific state
await page.click("Search")
await page.wait_for_selector("h1:has-text('Results')")  # Wait for new state
title = await page.locator("h1").text_content()
```

### Pitfall 3: Storing credentials in agent context

```python
# BROKEN: password leaks into LLM context (and logs)
await page.fill("#password", "SuperSecret123!")  # In code, LLM sees it
```

```python
# FIXED: special protected actions; LLM sees placeholder
await secure_input_tool.fill("#password", credential_ref="user_pwd")
# Tool retrieves from secret manager; never goes through LLM context
```

**War story**: A team built a job-application agent. Worked great in dev. In production, the agent's actions started failing on a popular jobs site. Investigation: the site was A/B-testing new layouts; class names changed every 30 minutes. Migration from CSS selectors to accessibility-tree-based semantic selectors fixed reliability — 90%+ success across A/B variants.

---

## 10. Technologies & Tools

| Tool | Purpose |
|---|---|
| Browser Use | Python LLM-controlled Playwright |
| Stagehand | TypeScript browser agent SDK |
| Playwright MCP | Official Anthropic MCP for Playwright |
| Browserbase | Cloud browser infrastructure |
| Playwright | Browser automation library |
| Puppeteer | Chrome-only browser automation |
| Selenium | Older browser automation |
| Skyvern | Form-filling specialist |
| 2captcha / CapSolver | Captcha solving APIs |
| Krisp / Stealth plugin | Bot detection evasion |
| Browser MCP (Smithery) | Community MCP servers |

---

## 12. Interview Questions with Answers

**Why is accessibility tree extraction better than HTML for browser agents?**
Accessibility tree is built for assistive technology — it surfaces semantic info (labels, roles, states) and filters out presentational noise. Smaller (10-100× fewer tokens than raw HTML), structured for interpretation, more stable across CSS refactors. LLMs reason over it more accurately than over raw HTML.

**What's the difference between Browser Use, Stagehand, and Playwright MCP?**
Browser Use: Python library, Playwright-based, DOM+vision hybrid, default to Claude. Stagehand: TypeScript SDK from Browserbase team, three high-level primitives (act/extract/observe), tightly integrated with Browserbase cloud. Playwright MCP: Anthropic's official MCP server — protocol-based, works with any MCP client.

**Why are semantic selectors more reliable than CSS/XPath?**
CSS selectors (`#btn-1234`) and XPath (`//button[@data-test='5']`) reference implementation details that change between deploys. Semantic selectors (`get_by_role("button", name="Submit")`) reference the user-visible semantics — text, role, ARIA labels — which are far more stable.

**How do browser agents handle authentication?**
Two patterns: (1) Session persistence — manually log in once, save cookies+localStorage to JSON via Playwright's `context.storage_state()`, reuse in agent runs. (2) Credential injection — agent navigates to login page, dedicated "fill credential" tool (not LLM-visible) retrieves password from secret manager.

**How do you handle captchas?**
Three options: (1) Avoid by using residential proxies / stealth browsers (Browserbase has this) to reduce captcha triggering. (2) Solve via 2captcha or CapSolver API — agent detects captcha, sends image, gets solution back. (3) Escalate to human — pause agent, request user to solve in real browser, resume.

**What's WebArena and how do agents perform on it?**
WebArena (CMU 2024) is a benchmark of real web tasks across 6 sites (Shopping, GitLab, Reddit, OpenStreetMap, etc). Tests are realistic multi-step tasks. Leading agents: Browser Use + Claude Sonnet 4 ~58%, Stagehand ~50%, vision-only approaches ~30%. Benchmark drives architectural progress.

**When should you use vision vs DOM extraction?**
Use DOM when site is standard (HTML/React/Vue with semantic markup). Use vision as fallback when: Canvas-based UI (Figma, Google Sheets), PDF viewers, custom-rendered widgets, or when DOM extraction returns ambiguous results. Hybrid agents try DOM first, fall back to screenshot only when needed.

**How do browser agents handle pagination and infinite scroll?**
Detect end-of-page via DOM signal (specific selector) or absence of "next" button. For infinite scroll: scroll down, wait for new content, repeat until task complete OR new content stops appearing OR max-scroll cap reached. Cost grows linearly with scroll depth.

**What's Browserbase and why is it popular?**
Browserbase provides cloud-hosted browsers (Chromium with stealth-mode patches) as a service. Benefits: don't run browsers in your infra (memory-heavy), session persistence built in, captcha solving integrated, live debugging UI (watch agent work in real-time). Pay-per-session model.

**How do you debug a failing browser agent?**
Capture: (1) screenshot at each step, (2) DOM snapshot, (3) action taken, (4) LLM reasoning. Browser Use and Stagehand have built-in debuggers; Browserbase has session replay UI. Common failures: stale element, missed wait condition, semantic locator that should have worked but didn't.

**What about anti-bot defenses (Cloudflare, etc)?**
Modern bot detection (Cloudflare, DataDome, PerimeterX) profiles browser fingerprints — User-Agent, screen resolution, WebGL renderer, JavaScript timing patterns, mouse movement. Vanilla Playwright is fingerprintable. Counter: stealth plugins (puppeteer-stealth, Playwright-stealth), residential proxies, human-like mouse paths. Cat-and-mouse game.

**How do you cap cost on browser agents?**
Per-task budget (terminate if cost exceeds $X), per-domain rate limits (don't hammer one site), max-pages-per-task limit (cap navigation depth), DOM truncation (cap accessibility tree to 50KB), screenshot size limits (low-res screenshots are cheaper).

**How are browser agents different from Computer Use?**
Computer Use is generic — screenshot + click/type/key, works on any GUI. Browser agents specialize on web pages — leverage DOM/accessibility tree for cleaner extraction, use semantic selectors, handle browser-specific lifecycle (page loads, navigation). Browser agents are typically 2-3× more accurate on web tasks at lower cost.

**What's the role of MCP in the browser agent ecosystem?**
Playwright MCP (and community variants like Browser MCP) expose browser operations as standardized tools. Any MCP client (Claude Code, custom agents, etc) can use them without proprietary SDK integration. Standardizing on MCP enables interoperability.

**Can browser agents run unattended at scale?**
Yes but with constraints. For 100s of concurrent browser sessions, use Browserbase or similar (don't try to run 100 headless Chrome on one host — memory dies). Implement rate limiting per target site (politeness), captcha handling, session refresh on auth expiry, retry/failure handling.

---

## 13. Best Practices

1. Always use accessibility tree extraction first; vision as fallback only.
2. Use semantic locators (get_by_role, get_by_text) — avoid CSS/XPath where possible.
3. Wait for explicit state changes (specific selector visible) before reading page content.
4. Persist authenticated sessions via storage_state for repeated automation against same site.
5. Never put credentials into LLM context — use protected tools that pull from secret managers.
6. Cap per-task cost AND per-task navigation depth — prevent runaway agents.
7. Use Browserbase or similar cloud infra for production scale — don't self-host headless Chrome at scale.
8. Implement site-specific recipes for hard-to-automate sites (custom waits, custom DOM hints).
9. Test on multiple browser versions / A/B variants — sites change frequently.
10. Respect robots.txt and rate-limit politely; treat browser agents as web citizens.

---


## 14. Case Study

**Scenario:** An e-commerce operations team (50-person company, 200 orders/day) needs to automate checkout flows across 12 supplier portals. Each portal requires login, product search by SKU, quantity entry, and order confirmation. Current state: 4 full-time ops staff spend 60% of their time on manual portal entry. Goal: automate 90%+ of orders with <2% error rate, p99 task completion under 90 seconds per order, monthly LLM + infrastructure cost under $800.

**Architecture:**

```
          ┌──────────────────────────────────────────────────┐
          │             Order Management System (OMS)         │
          │   PostgreSQL: pending_orders, portal_credentials  │
          └──────────────────┬───────────────────────────────┘
                             │  poll every 60s for new orders
                             v
          ┌──────────────────────────────────────────────────┐
          │          Browser Agent Orchestrator (Python)      │
          │   - Dequeue orders by portal                      │
          │   - Load portal recipe (JSON config)              │
          │   - Dispatch BrowserAgentWorker per order         │
          │   - Collect result: success | retry | escalate    │
          └──────────────────┬───────────────────────────────┘
                             │  parallel (max 6 concurrent)
          ┌──────────────────┼───────────────────────────────┐
          │                  │                               │
    ┌─────▼──────┐    ┌──────▼─────┐              ┌─────────▼──────┐
    │  Worker 0  │    │  Worker 1  │   ...         │  Worker 5      │
    │  Portal A  │    │  Portal B  │               │  Portal C      │
    │ Browserbase│    │ Browserbase│               │ Browserbase    │
    │  session   │    │  session   │               │  session       │
    └─────┬──────┘    └──────┬─────┘              └────────┬───────┘
          │                  │                             │
          └──────────────────┼─────────────────────────────┘
                             │
          ┌──────────────────▼───────────────────────────────┐
          │   Claude Sonnet 4 (Anthropic API)                 │
          │   Input: accessibility tree + recipe hints        │
          │   Output: action sequence (click/type/select)     │
          └──────────────────────────────────────────────────┘
                             │
          ┌──────────────────▼───────────────────────────────┐
          │   Audit Log + Screenshot Archive (S3)             │
          │   Every step: action taken, DOM snapshot, screenshot│
          │   Verification: confirmation-page screenshot →     │
          │     Claude verifies order number visible           │
          └──────────────────────────────────────────────────┘

Portal Recipe (JSON per supplier):
  {
    "portal_id": "supplier_acme",
    "login_url": "https://portal.acme-supply.com/login",
    "username_selector": "input[label='Username']",
    "password_vault_key": "acme/portal_password",
    "search_flow": "type SKU in search bar, click first result",
    "quantity_selector": "input[role='spinbutton', name='Quantity']",
    "submit_hint": "button named 'Place Order' or 'Confirm'",
    "success_signal": "text containing 'Order #' OR 'Confirmation'",
    "known_quirks": ["wait 3s after login for SSO redirect",
                     "quantity field resets on blur — tab away last"]
  }
```

**Key implementation — 3 Python code blocks:**

Block 1 — Portal agent worker with accessibility-tree extraction:

```python
from __future__ import annotations
import asyncio
import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from browser_use import Agent, BrowserConfig
from browser_use.llm import ChatAnthropic
from playwright.async_api import async_playwright, Page


@dataclass
class PortalRecipe:
    portal_id: str
    login_url: str
    username_selector: str
    password_vault_key: str
    search_flow: str
    quantity_selector: str
    submit_hint: str
    success_signal: str
    known_quirks: list[str] = field(default_factory=list)

    @classmethod
    def from_json(cls, path: Path) -> "PortalRecipe":
        data = json.loads(path.read_text())
        return cls(**data)


@dataclass
class OrderEntry:
    order_id: str
    sku: str
    quantity: int
    portal_id: str


async def run_portal_agent(
    order: OrderEntry,
    recipe: PortalRecipe,
    session_state_path: Path,
    llm_model: str = "claude-sonnet-4-6",
) -> dict[str, Any]:
    """
    Execute a single order entry on a supplier portal.
    Returns result dict with status, confirmation_number, cost_usd.
    """
    quirks_block = "\n".join(f"- {q}" for q in recipe.known_quirks)
    task = f"""
You are placing a purchase order on a supplier portal.

Steps:
1. Navigate to {recipe.login_url} if not already there.
2. Log in using the stored session (credentials already loaded).
3. Search for SKU "{order.sku}" using: {recipe.search_flow}
4. Enter quantity {order.quantity} in: {recipe.quantity_selector}
5. Submit order using: {recipe.submit_hint}
6. Verify success — look for: {recipe.success_signal}
7. Extract and report the order/confirmation number.

Known quirks to handle:
{quirks_block}

IMPORTANT: If you see a CAPTCHA, report "CAPTCHA_REQUIRED" immediately.
If you cannot find the SKU after 2 searches, report "SKU_NOT_FOUND".
    """.strip()

    agent = Agent(
        task=task,
        llm=ChatAnthropic(model=llm_model),
        browser_config=BrowserConfig(
            headless=True,
            storage_state=str(session_state_path),  # pre-authenticated session
        ),
        use_vision=False,          # DOM extraction preferred; vision only on fallback
        max_actions_per_step=4,
        max_failures=3,
        save_conversation_path=f"/tmp/agent_runs/{order.order_id}",
    )

    result = await agent.run(max_steps=25)
    final = result.final_result() or ""

    if "CAPTCHA_REQUIRED" in final:
        return {"status": "captcha_escalation", "order_id": order.order_id}
    if "SKU_NOT_FOUND" in final:
        return {"status": "sku_not_found", "order_id": order.order_id}

    # Extract confirmation number via structured extraction
    conf_num = _extract_confirmation(final)
    return {
        "status": "success",
        "order_id": order.order_id,
        "confirmation_number": conf_num,
        "llm_cost_usd": _estimate_cost(result),
    }


def _extract_confirmation(text: str) -> str:
    import re
    match = re.search(r"(?:Order|Confirmation|Ref)[\s#:]+([A-Z0-9\-]{6,20})", text, re.I)
    return match.group(1) if match else "UNKNOWN"


def _estimate_cost(result: Any) -> float:
    # ~800 input tokens + 200 output tokens per step, 15 steps avg
    # claude-sonnet-4-6: $3/$15 per 1M in/out
    avg_steps = 15
    return round((800 * avg_steps * 3 + 200 * avg_steps * 15) / 1e9, 4)
```

Block 2 — Session refresh and orchestrator (production concern):

```python
from __future__ import annotations
import asyncio
import time
from pathlib import Path
from typing import Any

import boto3
from playwright.async_api import async_playwright


class SessionManager:
    """
    Manages per-portal browser sessions. Sessions expire after 8 hours
    (portal SSO timeout). Refresh automatically before expiry.
    Credentials pulled from AWS Secrets Manager — never from LLM context.
    """

    SESSION_TTL_SECONDS = 7 * 3600  # refresh 1h before 8h portal timeout

    def __init__(self, session_dir: Path) -> None:
        self._session_dir = session_dir
        self._session_dir.mkdir(parents=True, exist_ok=True)
        self._timestamps: dict[str, float] = {}
        self._sm = boto3.client("secretsmanager", region_name="us-east-1")

    def session_path(self, portal_id: str) -> Path:
        return self._session_dir / f"{portal_id}.json"

    def needs_refresh(self, portal_id: str) -> bool:
        ts = self._timestamps.get(portal_id, 0.0)
        return (time.monotonic() - ts) > self.SESSION_TTL_SECONDS

    async def refresh(self, portal_id: str, recipe: Any) -> None:
        creds = self._get_creds(recipe.password_vault_key)
        async with async_playwright() as pw:
            browser = await pw.chromium.launch(headless=True)
            ctx = await browser.new_context()
            page = await ctx.new_page()

            await page.goto(recipe.login_url)
            await page.wait_for_load_state("networkidle")

            # quirk: SSO redirect takes up to 3s
            await asyncio.sleep(3)

            await page.fill(recipe.username_selector, creds["username"])
            await page.fill("input[type='password']", creds["password"])
            await page.keyboard.press("Enter")
            await page.wait_for_load_state("networkidle")

            # Verify login succeeded
            if "login" in page.url.lower():
                raise RuntimeError(f"Login failed for portal {portal_id}")

            await ctx.storage_state(path=str(self.session_path(portal_id)))
            await browser.close()

        self._timestamps[portal_id] = time.monotonic()

    def _get_creds(self, secret_key: str) -> dict[str, str]:
        resp = self._sm.get_secret_value(SecretId=secret_key)
        import json
        return json.loads(resp["SecretString"])


async def orchestrate_orders(
    orders: list[Any],
    recipes: dict[str, Any],
    session_mgr: SessionManager,
    max_concurrent: int = 6,
) -> list[dict[str, Any]]:
    sem = asyncio.Semaphore(max_concurrent)
    results: list[dict[str, Any]] = []

    async def process(order: Any) -> None:
        recipe = recipes[order.portal_id]
        if session_mgr.needs_refresh(order.portal_id):
            await session_mgr.refresh(order.portal_id, recipe)
        async with sem:
            result = await run_portal_agent(
                order=order,
                recipe=recipe,
                session_state_path=session_mgr.session_path(order.portal_id),
            )
            results.append(result)

    await asyncio.gather(*[process(o) for o in orders])
    return results
```

Block 3 — BROKEN -> FIX: flaky DOM selection and verification gap:

```python
from __future__ import annotations
from playwright.async_api import Page


# BROKEN: Hard-coded CSS selector for quantity field.
# Works on day 1; breaks when portal redeploys with new class names.
# No verification after fill — agent proceeds even if quantity was rejected.
async def broken_fill_quantity(page: Page, quantity: int) -> None:
    await page.fill("#qty-input-1234", str(quantity))
    # No wait, no verification — silent failure if input rejected


# FIX 1: Use accessible name selector (stable across deploys).
# FIX 2: Verify quantity was accepted by reading the field value back.
# FIX 3: Handle the known quirk where blur resets the field — use Tab, not click-away.
async def fixed_fill_quantity(page: Page, quantity: int, selector_hint: str) -> bool:
    """
    Fill quantity field and verify acceptance.
    selector_hint from recipe: "input[role='spinbutton', name='Quantity']"
    Returns True if quantity was accepted, False if field rejected input.
    """
    # Locate by accessible role + name — survives CSS refactors
    qty_input = page.get_by_role("spinbutton", name="Quantity").first

    await qty_input.click()
    await qty_input.triple_click()          # select all existing content
    await qty_input.type(str(quantity))     # type character by character

    # Tab away to trigger validation (known quirk: click-away resets some portals)
    await qty_input.press("Tab")
    await page.wait_for_timeout(500)       # allow field validation JS to run

    # Verify accepted
    actual = await qty_input.input_value()
    if actual.strip() != str(quantity):
        # Retry once with keyboard shortcut Ctrl+A then overtype
        await qty_input.focus()
        await page.keyboard.press("Control+A")
        await page.keyboard.type(str(quantity))
        await qty_input.press("Tab")
        await page.wait_for_timeout(300)
        actual = await qty_input.input_value()

    return actual.strip() == str(quantity)


# BROKEN: No post-order verification — assume submit click = success.
# Silent failures: button click acknowledged but order not placed
# (session expired, form validation failed server-side, JS error).
async def broken_submit(page: Page) -> str:
    await page.click("button:has-text('Place Order')")
    return "assumed_success"


# FIX: After submit, wait for and verify the success signal explicitly.
# Screenshot the confirmation page and send to Claude for OCR verification.
async def fixed_submit_and_verify(
    page: Page,
    success_signal: str,
    timeout_ms: int = 30_000,
) -> dict[str, str]:
    await page.click("button:has-text('Place Order')")

    try:
        # Wait for success indicator
        await page.wait_for_selector(
            f"text={success_signal.split(' OR ')[0]}",
            timeout=timeout_ms,
        )
    except Exception:
        # Take screenshot for debugging even on failure
        screenshot = await page.screenshot(full_page=True)
        return {"status": "timeout", "screenshot_bytes": screenshot.hex()}

    confirmation_text = await page.text_content("body") or ""
    import re
    match = re.search(r"(?:Order|Confirmation)[\s#:]+([A-Z0-9\-]{6,20})", confirmation_text, re.I)
    return {
        "status": "success",
        "confirmation_number": match.group(1) if match else "PARSE_ERROR",
    }
```

**Pitfall 1 — Session expiry mid-task causing silent re-login loop:**

```python
# BROKEN: Agent navigates to portal, session expired, portal silently redirects
# to login page. Agent tries to "place order" but is on login page.
# No error raised — agent eventually times out after 25 steps.
async def broken_no_session_check(agent: Any) -> None:
    result = await agent.run(max_steps=25)
    # May have spent 25 steps fighting login page

# FIX: Before dispatching agent, verify session is valid by checking
# that the portal home page loads (not redirected to /login).
async def fixed_verify_session(page: Page, expected_path_fragment: str) -> bool:
    await page.goto(expected_path_fragment)
    await page.wait_for_load_state("networkidle")
    # If redirected to login, session is expired
    return "login" not in page.url.lower() and "signin" not in page.url.lower()
```

**Pitfall 2 — LLM hallucinates confirmation number when page is ambiguous:**

```python
# BROKEN: rely solely on LLM text output to extract confirmation number.
# LLM may hallucinate "Order #789123" when confirmation page shows
# a generic "Thank you" without a clear order number.
def broken_extract(llm_output: str) -> str:
    return llm_output  # trust everything the LLM says

# FIX: Always regex-extract from actual DOM text, not LLM summary.
# If pattern not found, mark as PARSE_ERROR and escalate for human review.
import re
def fixed_extract(dom_text: str) -> str:
    match = re.search(r"(?:Order|PO|Ref|Confirmation)[\s#:]+([A-Z0-9\-]{5,20})", dom_text, re.I)
    return match.group(1) if match else "PARSE_ERROR"
```

**Pitfall 3 — Parallel agents hammering same portal, triggering rate limiting:**

```python
# BROKEN: dispatch all 200 orders against Portal A simultaneously.
# Portal A rate-limits at 10 req/min, returns 429, all agents fail.
async def broken_parallel_all(orders: list[Any]) -> list[Any]:
    return await asyncio.gather(*[run_portal_agent(o, ...) for o in orders])

# FIX: per-portal semaphore + inter-request delay.
import asyncio
async def fixed_rate_limited(orders: list[Any], per_portal_limit: int = 3) -> list[Any]:
    portal_sems: dict[str, asyncio.Semaphore] = {}
    results = []
    async def run(order: Any) -> None:
        pid = order.portal_id
        if pid not in portal_sems:
            portal_sems[pid] = asyncio.Semaphore(per_portal_limit)
        async with portal_sems[pid]:
            r = await run_portal_agent(order, ...)
            results.append(r)
            await asyncio.sleep(2)  # 2s between requests per portal
    await asyncio.gather(*[run(o) for o in orders])
    return results
```

**Metrics:**

| Metric | Before (manual) | After (browser agent) |
|--------|-----------------|----------------------|
| Orders processed/day | 200 (4 FTE × 50 orders) | 200 fully automated |
| Avg time per order | 4 min manual | 38 sec (p50) / 82 sec (p99) |
| First-try success rate | 99% (human catches errors) | 93.1% |
| Retry success rate | — | 5.2% (1 retry) |
| Human escalation rate | 1% (edge cases) | 1.7% (CAPTCHA + SKU issues) |
| Monthly labor cost | $18,400 (4 FTE × 60% time) | $640 (LLM API + Browserbase) |
| Errors causing incorrect orders | ~0.3%/month | 0.05%/month (verification catches) |
| WebArena benchmark (12-portal subset) | N/A | 61% zero-shot success |

**Interview Q&As:**

**Q: Why use accessibility tree extraction rather than raw HTML for browser agents?**
Accessibility trees are 10-100x smaller than raw HTML, containing only semantic information (roles, labels, states) that assistive technologies and LLMs need. Raw HTML includes thousands of lines of CSS classes, data attributes, and framework artifacts that add noise without meaning. A typical React page's raw HTML might be 150KB; its accessibility tree is 5-10KB of structured, interpretable content. The LLM reasons more accurately and uses fewer tokens when given accessibility tree input.

**Q: How do you handle CAPTCHA in a production browser agent pipeline?**
Three-tier strategy: (1) Prevention — use Browserbase's stealth-mode browsers and residential proxy rotation to reduce CAPTCHA triggering by 70-80%; (2) Automated solving — integrate 2captcha or CapSolver APIs when CAPTCHAs do appear; the agent pauses, sends the CAPTCHA image, gets the solution token, injects it; (3) Human escalation — if automated solving fails or the CAPTCHA type is unsupported, surface to a human operator via a notification queue; the human solves it in a live session, the agent resumes. Costs: 2captcha charges ~$2/1000 CAPTCHAs; human escalation costs ~$1-2 per incident.

**Q: What is the trade-off between DOM extraction and vision-based navigation?**
DOM extraction is faster (50-200ms per page vs 1-3s for vision), uses fewer tokens (5KB text vs 500-2000 tokens for screenshot analysis), and achieves higher accuracy on standard HTML/React sites (90-95% vs 70-80%). Vision handles opaque UIs — Canvas-based apps, PDF viewers, custom-rendered widgets — where DOM extraction returns empty or meaningless content. Hybrid agents use DOM first and fall back to vision only when DOM content is insufficient, achieving the best of both while minimizing vision usage to 10-20% of steps.

**Q: How do you make browser agents resilient to portal UI changes between deploys?**
Three techniques: (1) Semantic selectors — use `get_by_role("button", name="Place Order")` instead of `#btn-1234`; accessible names are far more stable than CSS IDs. (2) Recipe hints — store site-specific wait conditions and selector hints in JSON recipes, update when the portal changes. (3) Verification after every action — if the page state after an action does not match expectations, the agent retries with a different approach rather than silently proceeding. Monitoring the agent run logs and reviewing screenshots for failing sessions catches UI drift within hours.

**Q: Why cap concurrent browser sessions per portal rather than maximizing parallelism?**
Supplier portals implement rate limiting (typically 5-20 requests per minute per IP or session). Exceeding these limits triggers 429 responses, IP blocks, or CAPTCHA walls that affect all concurrent sessions for that portal simultaneously. Per-portal semaphores (2-4 concurrent sessions) with inter-request delays (1-3 seconds) stay within portal rate limits while still achieving meaningful throughput — 200 orders across 12 portals at 3 concurrent each processes all orders in under 20 minutes.

**Q: How do you verify that an order was actually placed versus the agent mistakenly thinking it succeeded?**
Two verification layers: (1) DOM verification — regex-search the actual page DOM text for the confirmation pattern (order number, PO number) rather than trusting the LLM's text summary; if no match found, status is PARSE_ERROR requiring human review. (2) Screenshot verification — take a full-page screenshot of the confirmation page and store in S3 with the order record; a daily reconciliation job cross-references confirmed orders in the portal's email confirmations against the screenshot archive, catching any cases where the agent misread the page.
