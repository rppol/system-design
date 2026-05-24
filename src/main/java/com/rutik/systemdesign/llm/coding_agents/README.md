# Coding Agents

## 1. Concept Overview

Coding agents are LLM agents specialized for software engineering tasks: reading and understanding codebases, writing new code, editing existing code, running tests, debugging, generating patches, and submitting PRs. They differ from generic agents in tool design (file editors, bash shells, test runners, language servers), evaluation frameworks (SWE-bench, HumanEval, LiveCodeBench), and the central engineering challenge of operating reliably on large, real-world codebases where ground truth is "tests pass and humans approve".

The 2024-2025 coding-agent landscape spans IDE-integrated (Cursor Composer, Claude Code, Continue, Cline), autonomous platforms (Devin, OpenHands, SWE-agent), and library-style agents (Aider, Plandex). Best-in-class scores on SWE-bench Verified (real GitHub issues): Claude 3.5 Sonnet at ~49%, SWE-agent + GPT-4 at ~18% (2024 baseline; rapidly evolving). The frontier is moving toward multi-hour autonomous coding tasks (Cursor Background Agents, Devin sessions, Anthropic's Claude Code subagents) that span dozens of files.

---

## Intuition

**One-line analogy**: A coding agent is like an off-shore developer who can read your codebase, ask clarifying questions, write code, run your tests, and submit a PR — but operates at sub-second cost and millisecond latency.

**Mental model**: The agent's "IDE" is its toolbox: `read_file` is its file explorer, `bash` is its terminal, `edit_file` is its editor, `run_tests` is its test runner. Each tool call is one action; the agent's loop is the development loop: explore → understand → modify → test → fix → repeat. Modern agents add navigation primitives (grep, symbol search, type lookup via LSP) so they can find relevant code without reading entire files.

**Why it matters**: Software engineering is the #1 commercial application of LLMs today. The market is moving from "AI helps write code one line at a time" (Copilot) toward "AI completes whole tasks autonomously" (Devin, Claude Code). The pivot creates new architectural challenges: navigation at scale, test-driven loops, mistake recovery, multi-file coherence, human collaboration patterns.

**Key insight**: Reliable coding agents are not just LLMs with file tools — they require careful Agent-Computer Interface (ACI) design. SWE-agent's contribution was demonstrating that specialized commands (`scroll_up/down`, `goto_line`, `edit <start>:<end>`) dramatically outperform raw bash. The interface between agent and code matters as much as the model.

---

## 2. Core Principles

- **Navigate before reading**: grep/symbol-search/LSP to find relevant code; don't dump whole files into context.
- **Test-driven loops**: write tests first when possible; run tests after every change; let test failures guide fixes.
- **Patch over rewrite**: unified diff / search-replace patches preserve intent and reviewability.
- **Bounded blast radius**: gate destructive operations (git reset, force-push, rm) behind approval.
- **Stateful sessions**: agent maintains context across multi-turn sessions (file edits, test results, error history).
- **Multi-file coherence**: when edits span files, validate the whole codebase compiles/tests pass, not each file in isolation.
- **Human collaboration**: approval gates, diff previews, clear escalation paths.

---

## 3. Types / Architectures / Strategies

### 3.1 SWE-agent Style (Agent-Computer Interface, ACI)

Custom shell-like commands optimized for LLM use: `find_file`, `goto`, `scroll_window`, `edit`, `submit`. Each command is precisely defined to fit LLM strengths. SWE-agent paper (Yang et al. 2024) showed ACI dramatically outperforms raw bash.

### 3.2 OpenHands (formerly OpenDevin)

Open-source autonomous coding platform. Event stream architecture: agent emits actions, runtime executes, results flow back as observations. Supports multiple agent backends (CodeActAgent, BrowsingAgent). Docker sandbox per session.

### 3.3 Aider

Library-based coding agent (Python). Integrates with git — commits each agent change as a separate git commit. Uses repository "map" (file summaries + class/function signatures) for context-efficient navigation. Configurable model (OpenAI, Anthropic, local).

### 3.4 Cursor Composer / Claude Code / Continue / Cline

IDE-integrated agents. Tight UX: inline diff previews, accept/reject per hunk, integrated terminal, file tree awareness. Often subagent dispatch (parallel file edits) for multi-file changes.

### 3.5 Devin

Autonomous "AI software engineer". Long-running sessions (hours), full VM access, browser + terminal + IDE, plans tasks, asks clarifying questions, posts PRs. Targets enterprise dev orgs as a teammate.

### 3.6 Plandex

Open-source autonomous coding agent. Plan-first architecture: agent produces detailed plan before executing; user approves/edits plan; agent executes with checkpointing.

---

## 4. Architecture Diagrams

```
SWE-agent ACI Loop
===================

  Issue: "Tests for foo are failing"
       |
       v
  +-----------------+
  | LLM (GPT-4/    |  thinks about problem
  | Claude)        |  decides next ACI command
  +-----------------+
       |
       v
  +-----------------+
  | ACI commands:  |
  | find_file foo  |
  | goto 142       |
  | edit 142:145   |
  | run_tests      |
  | submit         |
  +-----------------+
       |
       v
  Sandbox runtime executes
       |
       v
  Observation back to LLM
       |
       v
  Loop until submit or max iterations


Claude Code Subagent Pattern
=============================

  Parent CLI agent
       |
       +-- Explore subagent (read-only)
       |     - file_search, grep, glob
       |     - returns: "relevant files: x.py, y.py"
       |
       +-- Code subagent (write)
       |     - read_file, edit_file, bash
       |     - returns: "made edit; tests pass"
       |
       +-- Review subagent
             - read_file, git diff
             - returns: "looks good / issues found"


Cursor Composer Multi-File Edit
================================

  User: "Refactor auth to use JWT"
       |
       v
  Model identifies 8 files needing changes
       |
       v
  +--+--+--+--+--+--+--+--+
  |  parallel file editors |
  +-----------+------------+
              |
              v
  Diff preview UI for each file
              |
              v
  User accepts/rejects per hunk
              |
              v
  All changes applied; tests run
```

---

## 5. How It Works — Detailed Mechanics

### Simplified SWE-agent ACI

```python
from typing import Literal
from dataclasses import dataclass

@dataclass
class ACIState:
    open_file: str | None = None
    window_start: int = 0
    window_size: int = 100

class ACI:
    """Agent-Computer Interface — domain-specific commands."""
    
    def find_file(self, name: str) -> str:
        """Search for files matching name pattern."""
        import subprocess
        result = subprocess.run(["find", ".", "-name", name], capture_output=True, text=True)
        return result.stdout
    
    def goto(self, line: int) -> str:
        """Move window to specific line in open file."""
        ...
    
    def scroll_down(self) -> str:
        """Move window down."""
        ...
    
    def edit(self, start: int, end: int, new_content: str) -> str:
        """Replace lines [start, end] with new_content."""
        ...
    
    def run_tests(self, path: str = ".") -> str:
        """Run tests; return summary."""
        result = subprocess.run(["pytest", path], capture_output=True, text=True, timeout=300)
        # Truncate output to fit context
        return result.stdout[-10_000:] + result.stderr[-5_000:]


# LLM is prompted with the ACI command spec; emits commands per turn
# Runtime parses commands, executes via ACI, returns observation
```

### Aider's Repository Map (Context Efficient)

```python
# Aider strategy: don't dump whole files; build a "map" of the repo
# - class/function signatures, docstrings, file headers
# - 1-5KB per file instead of 20-100KB
# - LLM uses map to navigate, only opens specific files when needed

def build_repo_map(root: str) -> str:
    """Walk codebase; extract signatures + docstrings."""
    map_lines = []
    for path in find_python_files(root):
        tree = ast.parse(open(path).read())
        for node in ast.walk(tree):
            if isinstance(node, (ast.FunctionDef, ast.ClassDef)):
                sig = extract_signature(node)
                doc = ast.get_docstring(node, clean=True) or ""
                map_lines.append(f"{path}:{node.lineno} {sig} — {doc[:80]}")
    return "\n".join(map_lines)


# Inject into system prompt:
# "Here is the repository map:\n{repo_map}\n
# Use open_file to view full contents of specific files."
```

### Test-Driven Agent Loop

```python
async def test_driven_loop(task: str, max_iterations: int = 15) -> dict:
    """Standard test-driven loop: write test, implement, run, fix, repeat."""
    
    # Phase 1: write failing test
    test_code = await llm_call(
        f"Write a failing test for this task: {task}\n"
        "Output as a complete pytest file."
    )
    write_file("test_new_feature.py", test_code)
    
    # Phase 2: implementation loop
    for i in range(max_iterations):
        # Run tests
        result = run_command(["pytest", "test_new_feature.py", "-v"])
        if "passed" in result.stdout and "failed" not in result.stdout:
            return {"status": "success", "iterations": i + 1}
        
        # Get failure context
        failure = result.stdout[-5000:]
        
        # Ask LLM to fix
        fix = await llm_call(
            f"Tests failed:\n{failure}\n\n"
            f"Current implementation:\n{read_relevant_files()}\n\n"
            "Provide the diff to fix this."
        )
        apply_diff(fix)
    
    return {"status": "max_iterations", "iterations": max_iterations}
```

---

## 6. Real-World Examples

**Cursor Composer / Cursor Background Agents** — IDE-integrated; Composer handles multi-file edits; Background Agents run hours-long tasks autonomously.

**Claude Code** — CLI agent with subagent dispatch (Explore, Implementation, Review subagents); used by Anthropic engineers and shipped to customers.

**Devin (Cognition AI)** — autonomous agent with full VM; targets enterprise dev teams as "an AI engineer".

**OpenHands** — open-source autonomous platform; popular for research and self-hosted deployments.

**Aider** — terminal-based; commits each change as separate git commit for clean history; loved by power users.

**SWE-agent** — research framework demonstrating the ACI principle.

**GitHub Copilot Workspace** — multi-step task planning + execution embedded in GitHub PR flow.

---

## 7. Tradeoffs

| System | Autonomy Level | Best Use Case | Cost per Task | SWE-bench (approx) |
|---|---|---|---|---|
| Cursor Composer | Medium (user approves diffs) | IDE-bound multi-file edits | $0.05-0.50 | N/A direct |
| Claude Code | Medium-High (subagents) | CLI, large codebases, automation | $0.20-2.00 | ~49% (Claude 3.5 Sonnet) |
| Devin | High (autonomous sessions) | Whole tickets, hours-long tasks | $1-20 | ~14% (early) - rising |
| OpenHands | High (configurable) | Self-hosted, research | varies | ~32% (early) - rising |
| Aider | Medium (interactive) | Terminal users, git-clean history | $0.10-1.00 | ~27% (Claude 3.5) |
| SWE-agent | High (research) | Benchmarking, ACI study | varies | ~18% (GPT-4) |
| Copilot (inline) | Low (per-completion) | Continuous coding assistance | per-token | N/A direct |

---

## 8. When to Use / When NOT to Use

**Use coding agents when:**
- Repetitive code changes across many files (refactors, migrations)
- Test-driven tasks where pass/fail is well-defined
- Codebase exploration for unfamiliar areas
- Bug fixing with reproducible failure
- Boilerplate generation (CRUD, schemas)

**Use with caution / human review when:**
- Security-critical code (auth, crypto, IAM)
- Performance-critical hot paths
- Public APIs (breaking changes)
- Database migrations
- Code touching billing or financial logic

**Don't expect full autonomy when:**
- Requirements are ambiguous (clarify first)
- Codebase has poor test coverage (agent can't validate)
- Decisions involve cross-team coordination

---

## 9. Common Pitfalls

### Pitfall 1: Reading whole files into context

```python
# BROKEN: Dumps entire 80KB file into context, eats 60K tokens
content = read_file("src/main.py")  # 80KB
messages.append({"role": "user", "content": f"Here's the file:\n{content}"})
# After 5 such reads: 400K tokens, context limit exceeded
```

```python
# FIXED: Targeted extraction
relevant = grep("class UserAuth", "src/main.py")  # ~2KB
lines = read_file_lines("src/main.py", start=145, end=200)  # ~3KB
# Same task, 20x less context
```

### Pitfall 2: No test validation after edits

```python
# BROKEN: Agent edits file, claims success, doesn't run tests
agent_edit("src/foo.py", patch)
return "Done!"
# User discovers next day: tests fail; the "fix" broke other things
```

```python
# FIXED: Always run tests after edits
agent_edit("src/foo.py", patch)
test_result = run_command(["pytest", "tests/test_foo.py"])
if "failed" in test_result.stdout:
    return f"Edit applied but tests fail:\n{test_result.stdout[-2000:]}\nNeeds fixing."
return "Done. Tests pass."
```

### Pitfall 3: Unbounded git operations

```python
# BROKEN: agent runs git reset --hard, destroys user's uncommitted work
bash_tool("git reset --hard HEAD~3")
```

```python
# FIXED: gate destructive git ops via approval
DESTRUCTIVE_GIT = [r"git reset --hard", r"git push --force", r"git checkout \.", r"git clean -f"]

if any(re.search(p, command) for p in DESTRUCTIVE_GIT):
    approved = await get_user_approval("destructive git op", {"command": command})
    if not approved:
        return "User denied destructive git operation"
```

**War story**: A Devin-like agent at a startup attempted to "clean up the repo" — ran `git clean -fdx` and `rm -rf node_modules tmp build` "to ensure a fresh state". Wiped 3 days of an engineer's local-only WIP and prototype branches not yet pushed. Recovery took 6 hours of digging through backups. Fix: hard approval gate on any rm/git clean/git reset; allowlist only of pre-approved cleanup patterns.

---

## 10. Technologies & Tools

| Tool | Type | Strength |
|---|---|---|
| Cursor IDE | IDE-integrated | UX, composer, background agents |
| Claude Code | CLI agent | Subagents, prompt caching, MCP |
| Devin | Autonomous platform | Long sessions, full VM |
| OpenHands | Open-source platform | Self-host, research |
| Aider | Library / CLI | Git integration, clean commits |
| SWE-agent | Research framework | ACI baseline |
| Cline (VS Code) | VS Code agent | Free, configurable models |
| Continue | VS Code agent | Open-source, model-agnostic |
| Plandex | Terminal agent | Plan-first, checkpointing |
| Copilot Workspace | GitHub-integrated | PR-flow native |

---

## 11. Interview Questions with Answers

**What is the Agent-Computer Interface (ACI) and why does it matter?**
ACI is the set of commands/tools the agent uses to interact with a codebase. SWE-agent's contribution was showing that purpose-designed commands (find_file, goto, edit ranges) outperform raw bash because they reduce mistakes (clearer semantics, scoped operations) and match LLM strengths. A good ACI lifts SWE-bench scores by 10+ percentage points.

**Why is targeted file reading better than whole-file reading for coding agents?**
Whole-file reading bloats context (a 100KB file = ~25K tokens). With grep/symbol search, agent reads ~2KB relevant chunks. Cost per call drops 10-50×; context limits don't get hit; model focuses on relevant code. Industry production agents universally use this pattern.

**How does Claude Code's subagent dispatch help with large codebases?**
Parent CLI agent spawns subagents with focused tools and isolated context. Explore subagent does navigation (read-only); Implementation subagent does edits (write tools); Review subagent verifies. Each subagent's context stays lean (doesn't see other subagents' work); parent synthesizes results. Wall-clock 2-3× faster than monolithic agent on multi-file tasks.

**What is SWE-bench and why is it the standard benchmark?**
SWE-bench (Princeton, 2024): 2294 real GitHub issues from 12 popular Python repos; agent must produce a patch that passes the issue's test cases. SWE-bench Verified: 500-issue human-validated subset. Standard because: real-world distribution (not synthetic), tests as ground truth (no LLM judges), spans multiple repos and patterns.

**What's the difference between IDE-integrated agents (Cursor) and autonomous agents (Devin)?**
IDE-integrated agents work within a developer's flow — engineer reviews and accepts each suggestion. Autonomous agents run end-to-end tasks (hours), then deliver a PR for review. IDE agents: faster iteration, easier course-correction. Autonomous: less developer time, harder to course-correct mid-task. Convergence: Cursor Background Agents add autonomy to IDE; Devin adds clarification dialog to autonomy.

**Why is test-driven looping the dominant pattern for coding agents?**
Tests are the only available oracle — without them, the agent can't verify its work. Test-driven loops: write/find test → implement → run → on failure, fix → repeat. This is mechanical and well-suited to agentic loops. Bonus: when the agent fixes the test failure, you have proof of progress. Without tests, the agent can hallucinate "fixed" without verification.

**How do agents handle multi-file refactors?**
(1) Identify all affected files via symbol search / grep. (2) Generate edits per file (parallel via subagents in Claude Code-style). (3) Validate: compile/typecheck the project; run tests. (4) On failure, fix and re-validate. The hard part is invariant preservation across files — modern agents use language servers (LSP) for cross-file symbol awareness.

**What is the role of Language Server Protocol (LSP) in coding agents?**
LSP provides: find references, go to definition, completions, diagnostics — exactly what agents need to navigate large codebases. Agents like SWE-agent's recent versions integrate LSP for accurate cross-file symbol resolution. Avoids the "what does this function do?" needing a full file read.

**How do coding agents handle the case where an edit breaks unrelated code?**
After edits, run the full test suite (not just tests for changed files). If unrelated tests fail, the agent must: (1) read the failing test, (2) trace why the edit affected it, (3) either undo + try different approach OR also fix the broken area. This is where multi-file coherence matters most.

**What's the cost difference between Cursor and Devin per task?**
Cursor: $0.05-$0.50 per multi-file edit (single Claude/GPT call burst). Devin: $1-$20 per autonomous session (hours of compute, many LLM calls). Cursor's lower cost reflects shorter loops; Devin's higher cost reflects more autonomous exploration and longer task scope.

**How do you measure coding agent quality beyond SWE-bench?**
Production metrics: PR acceptance rate (% of agent-generated PRs merged without major revisions), code review feedback rate (how often human reviewers request changes), test pass rate at PR submission time, time-to-PR vs human baseline, cost per merged PR. SWE-bench measures capability; production metrics measure usefulness.

**Why is Aider's git-per-change pattern useful?**
Each agent edit becomes a separate git commit with a meaningful message. Benefits: easy to revert individual changes (git revert), clean PR review (reviewer sees each logical step), bisect-friendly history. Counter: noisy history if agent does many small edits.

**How do you keep an agent from "going off the rails" on a complex task?**
(1) Plan-first architecture (agent produces explicit plan, user approves before execution). (2) Frequent checkpointing (test or compile after every change). (3) Bounded autonomy (max iterations, cost cap). (4) Approval gates on destructive operations. (5) Human-in-the-loop signals (user can interject mid-task).

**What's the right tool granularity for a coding agent?**
Coarse-grained: bash (powerful but error-prone, leaks context). Fine-grained: 20+ specialized commands (precise but more for model to learn). Sweet spot: 8-12 well-designed commands covering: search, read, edit, run, git, test. SWE-agent's ACI hits this sweet spot.

**How do coding agents handle ambiguous requirements?**
Best-in-class agents ask clarifying questions before coding. Devin posts questions in Slack; Cursor Composer prompts in IDE; Claude Code uses CLI prompts. Worst pattern: agent guesses, ships code that solves the wrong problem. Always design for "agent can ask".

**What's the security model for coding agents?**
Sandboxed bash (E2B, Docker), no production credential access, approval gates on destructive ops, separate dev/prod environments (agent runs in dev), audit logs of all tool calls. Treat agent like an intern with potentially poor judgment — they're well-meaning but can make costly mistakes without guardrails.

---

## 12. Best Practices

1. Use targeted file extraction (grep, line ranges, LSP) instead of whole-file reads — 10-50× cost reduction.
2. Always validate edits with tests/typecheck before reporting success — no silent failures.
3. Gate destructive operations (rm, git reset, force-push) behind explicit user approval.
4. Use subagent dispatch for multi-file changes (parallel + isolated context).
5. Test-driven loop: write failing test → implement → run → fix until pass.
6. Prefer search-replace patches over whole-file rewrites — preserves intent, easier to review.
7. Maintain a repo map (file/symbol index) for context-efficient navigation.
8. Cap autonomy with max_iterations + cost budget — prevent runaway loops.
9. Use git per logical change (Aider pattern) for clean PR history.
10. Run agent in dev environment with no production credentials — security-by-isolation.

---

## 13. Best Practices Continued

**Cursor Composer's Production Architecture**

**Context**: Cursor is the most-used AI-powered IDE; Composer is its multi-file edit feature handling refactors, feature additions, bug fixes across many files in one session.

**Architecture**:
- Frontend: Cursor IDE (VS Code fork) sends file context + user request
- Backend: Claude Sonnet (and others) with custom system prompt + ACI tools (`read_file`, `edit_file`, `run_terminal`, `codebase_search`, `grep_search`)
- Diff UI: inline preview with per-hunk accept/reject
- Approval pattern: agent applies edits speculatively; user reviews diffs before commit
- Background Agents (newer): autonomous longer-running tasks; agent runs in cloud VM, posts results

**Key design choices**:
1. **Lightweight ACI**: ~10 well-designed tools — composability over expressiveness
2. **Speculative edits with diff preview**: user retains veto power per hunk
3. **codebase_search semantic search**: embedding-based search over entire codebase (not just grep) — finds conceptually related code
4. **Token streaming with diff updates**: as model generates, IDE updates diff preview in real-time

**Results**:
- ~1M+ developers use Cursor (2024-2025)
- Composer multi-file edits: 70%+ acceptance rate at the hunk level
- Background Agents: hour-long tasks complete unattended for many users
- Pricing: $20/month Pro tier; cost per Composer session typically $0.10-$2

**Lessons** (publicly known):
1. Diff UI is the killer feature — without per-hunk review, users wouldn't trust large changes.
2. Semantic codebase search outperforms grep for finding "the place that handles X" when naming doesn't match.
3. Background Agents required maturing the approval/checkpoint flow — autonomous tasks fail more without gates.

---


## 14. Case Study

**Scenario:** A fintech startup (200-person engineering org, 180k LOC Python monorepo) uses SWE-agent integrated with GitHub Actions to automatically resolve GitHub issues tagged `ai-solvable`. Current state: engineers spend 35% of their time on routine bugs (off-by-one errors, missing null checks, deprecated API calls, test coverage gaps). Goal: automatically close 40%+ of tagged issues within 15 minutes, p99 cost under $2.50 per issue, maintain <0.5% regression rate (patches that pass CI but break production).

**Architecture:**

```
  GitHub Issue created (tagged: ai-solvable)
            |
            v
  ┌─────────────────────────────────────────────────────────┐
  │  GitHub Actions Workflow                                 │
  │  trigger: issues labeled "ai-solvable"                  │
  │  runner: ubuntu-latest (4 vCPU, 16 GB)                  │
  └──────────────────────────┬──────────────────────────────┘
                             │
                             v
  ┌─────────────────────────────────────────────────────────┐
  │  Issue Triage Step                                       │
  │  - Parse issue title + body                              │
  │  - Classify: bug / feature / test / refactor             │
  │  - Extract: reproduction steps, file hints, error msgs   │
  │  - Check: issue too ambiguous? → comment + close         │
  └──────────────────────────┬──────────────────────────────┘
                             │ structured issue context
                             v
  ┌─────────────────────────────────────────────────────────┐
  │  SWE-agent (Claude claude-sonnet-4-6 backend)            │
  │  Sandbox: Docker container (exact CI environment)        │
  │  Tools: bash, find_file, goto, scroll, edit, run_tests   │
  │  Context: repo map (10k tokens) + issue context          │
  │  Budget: max 30 iterations, $2.00 LLM spend cap          │
  │                                                          │
  │  Agent loop:                                             │
  │    1. Read issue → formulate exploration plan            │
  │    2. Navigate repo (grep/find/LSP) to find fault        │
  │    3. Read relevant files (windowed view)                │
  │    4. Write failing test first (TDD)                     │
  │    5. Implement fix                                       │
  │    6. Run tests — iterate until pass                     │
  │    7. Generate patch + PR description                    │
  └──────────────────────────┬──────────────────────────────┘
                             │ unified diff patch
                             v
  ┌─────────────────────────────────────────────────────────┐
  │  Validation Pipeline                                     │
  │  - Apply patch to fresh Docker container                 │
  │  - Run full test suite (pytest -x, 5 min timeout)        │
  │  - Run type checker (mypy --strict)                      │
  │  - Run linter (ruff check)                               │
  │  - Security scan (bandit -ll)                            │
  │  All pass → open draft PR                               │
  │  Any fail → comment on issue with failure output         │
  └──────────────────────────┬──────────────────────────────┘
                             │
                             v
  ┌─────────────────────────────────────────────────────────┐
  │  Human Review (required — no auto-merge)                 │
  │  PR contains: patch, test added, agent reasoning summary │
  │  Reviewer approves/requests changes                      │
  │  Auto-merge disabled for fintech compliance              │
  └─────────────────────────────────────────────────────────┘

Repo Map (context optimization):
  Full repo: 180k LOC → ~9M tokens (too large for context)
  Repo map: file tree + class/function signatures = 10k tokens
  Agent navigates from map → finds relevant files → reads windows
  Window size: 100 lines — agent scrolls as needed
```

**Key implementation — 3 Python code blocks:**

Block 1 — SWE-agent integration wrapper:

```python
from __future__ import annotations
import subprocess
import json
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass
class IssueContext:
    number: int
    title: str
    body: str
    labels: list[str]
    repository: str           # "org/repo"


@dataclass
class AgentResult:
    success: bool
    patch: str                # unified diff
    test_added: str           # new test code
    reasoning: str            # agent's explanation
    iterations_used: int
    cost_usd: float
    failure_reason: str = ""


def run_swe_agent(
    issue: IssueContext,
    repo_path: Path,
    model: str = "claude-sonnet-4-6",
    max_iterations: int = 30,
    cost_limit_usd: float = 2.00,
) -> AgentResult:
    """
    Invoke SWE-agent on a GitHub issue within an isolated Docker container.
    Returns the generated patch and metadata.
    """
    env = {
        **os.environ,
        "ANTHROPIC_API_KEY": os.environ["ANTHROPIC_API_KEY"],
        "SWE_AGENT_MODEL": model,
        "SWE_AGENT_MAX_ITERATIONS": str(max_iterations),
        "SWE_AGENT_COST_LIMIT": str(cost_limit_usd),
    }

    problem_statement = _format_problem_statement(issue)

    cmd = [
        "python", "-m", "sweagent.run",
        "--model", model,
        "--repo-path", str(repo_path),
        "--problem-statement", problem_statement,
        "--max-iterations", str(max_iterations),
        "--output-format", "json",
        "--sandbox-type", "docker",
        "--docker-image", "myco/ci-python:3.11",  # exact CI environment
    ]

    result = subprocess.run(
        cmd, capture_output=True, text=True, timeout=900, env=env
    )

    if result.returncode != 0:
        return AgentResult(
            success=False,
            patch="",
            test_added="",
            reasoning="",
            iterations_used=0,
            cost_usd=0.0,
            failure_reason=result.stderr[:1000],
        )

    output = json.loads(result.stdout)
    return AgentResult(
        success=output.get("resolved", False),
        patch=output.get("patch", ""),
        test_added=output.get("test_patch", ""),
        reasoning=output.get("info", {}).get("reasoning", ""),
        iterations_used=output.get("info", {}).get("num_actions", 0),
        cost_usd=output.get("info", {}).get("cost", 0.0),
    )


def _format_problem_statement(issue: IssueContext) -> str:
    return f"""GitHub Issue #{issue.number}: {issue.title}

{issue.body}

Repository: {issue.repository}
Labels: {', '.join(issue.labels)}

Task: Analyze the issue, write a failing test that demonstrates the bug,
implement the fix, verify tests pass. Generate a minimal, focused patch."""
```

Block 2 — Repo map generation for context-efficient navigation (production concern):

```python
from __future__ import annotations
import ast
import os
from pathlib import Path
from typing import Generator


def generate_repo_map(
    repo_path: Path,
    max_tokens: int = 10_000,
    include_patterns: list[str] | None = None,
) -> str:
    """
    Generate a compact repository map: file tree + class/function signatures.
    Target: 10,000 tokens so it fits in context without dominating it.
    Aider's "repo map" approach — proven on SWE-bench.
    """
    include_patterns = include_patterns or ["*.py"]
    lines: list[str] = []
    total_chars = 0
    char_limit = max_tokens * 4  # rough 4 chars/token estimate

    for py_file in _iter_python_files(repo_path, include_patterns):
        rel_path = py_file.relative_to(repo_path)
        signatures = _extract_signatures(py_file)
        if not signatures:
            continue

        entry = f"\n{rel_path}:\n" + "\n".join(f"  {s}" for s in signatures)
        if total_chars + len(entry) > char_limit:
            lines.append("\n... (repo map truncated for context limit)")
            break
        lines.append(entry)
        total_chars += len(entry)

    return "# Repository Map\n" + "".join(lines)


def _iter_python_files(
    root: Path, patterns: list[str]
) -> Generator[Path, None, None]:
    skip_dirs = {".git", "__pycache__", ".venv", "node_modules", "dist", "build"}
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in skip_dirs]
        for fname in filenames:
            if any(Path(fname).match(p) for p in patterns):
                yield Path(dirpath) / fname


def _extract_signatures(path: Path) -> list[str]:
    """Extract class/function signatures without method bodies."""
    try:
        tree = ast.parse(path.read_text(encoding="utf-8", errors="ignore"))
    except SyntaxError:
        return []

    sigs: list[str] = []
    for node in ast.walk(tree):
        if isinstance(node, ast.ClassDef):
            sigs.append(f"class {node.name}:")
        elif isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
            args = [a.arg for a in node.args.args]
            ret = ""
            if node.returns:
                ret = f" -> {ast.unparse(node.returns)}"
            sigs.append(f"  def {node.name}({', '.join(args)}){ret}")
    return sigs
```

Block 3 — BROKEN -> FIX: window overflow and silent edit failure:

```python
from __future__ import annotations
from pathlib import Path


# BROKEN: Agent reads entire file into context when searching for a bug.
# A 2000-line file costs ~15,000 tokens — quickly exhausts context window.
# Agent hits token limit mid-reasoning, produces truncated/hallucinated patch.
def broken_read_file(path: Path) -> str:
    return path.read_text()   # entire file, no windowing


# FIX: Use windowed reads — agent requests specific line ranges.
# Standard window: 100 lines. Agent scrolls up/down as needed.
# Reduces token cost 20-200× for large files.
def fixed_read_file_windowed(
    path: Path,
    start_line: int = 1,
    window_size: int = 100,
) -> str:
    lines = path.read_text().splitlines()
    total = len(lines)
    end_line = min(start_line + window_size - 1, total)
    selected = lines[start_line - 1 : end_line]
    header = f"[File: {path.name} ({total} lines total), showing lines {start_line}-{end_line}]"
    numbered = [f"{start_line + i:4d}  {line}" for i, line in enumerate(selected)]
    return header + "\n" + "\n".join(numbered)


# BROKEN: Apply edit as a full file rewrite.
# LLM rewrites entire file — often introduces whitespace changes,
# removes comments, reformats unrelated code. Diff is 2000 lines wide.
def broken_apply_edit(path: Path, new_content: str) -> None:
    path.write_text(new_content)   # nukes entire file


# FIX: Apply targeted search-replace or line-range edit.
# Only the changed lines appear in the diff — easy to review.
def fixed_apply_edit(
    path: Path,
    old_text: str,
    new_text: str,
) -> tuple[bool, str]:
    """
    Replace exact old_text with new_text in file.
    Returns (success, error_message).
    """
    content = path.read_text()
    if old_text not in content:
        return False, f"old_text not found in {path.name} — agent hallucinated content"
    new_content = content.replace(old_text, new_text, 1)
    path.write_text(new_content)

    # Verify edit took effect
    verification = path.read_text()
    if new_text not in verification:
        return False, "edit did not persist — check file permissions"
    return True, ""


# BROKEN: Agent claims tests pass without actually running them.
# Common pattern: agent generates patch, then generates a fake
# "pytest output: 15 passed" without executing pytest.
async def broken_verify_tests() -> bool:
    return True  # assumed


# FIX: Always run pytest in the actual sandbox; parse exit code.
import subprocess
def fixed_run_tests(repo_path: Path, timeout: int = 300) -> dict[str, object]:
    result = subprocess.run(
        ["python", "-m", "pytest", "-x", "-q", "--tb=short"],
        cwd=repo_path,
        capture_output=True,
        text=True,
        timeout=timeout,
    )
    return {
        "passed": result.returncode == 0,
        "output": result.stdout[-3000:],   # last 3000 chars (test summary)
        "returncode": result.returncode,
    }
```

**Pitfall 1 — Agent loops on a bug it cannot reproduce:**

```python
# BROKEN: Issue says "fails on Python 3.9" but agent runs in Python 3.11 container.
# Agent cannot reproduce, generates a guess-fix, tests pass (wrong Python),
# patch merged — bug still present in production.
# No version mismatch detection.

# FIX: Capture the Python version from the issue, validate sandbox matches.
# Add a pre-check: if issue mentions specific Python/OS version,
# fail fast and comment "Cannot reproduce: requires Python 3.9 environment."
import sys
def check_env_match(required_python: str) -> bool:
    actual = f"{sys.version_info.major}.{sys.version_info.minor}"
    return actual == required_python
```

**Pitfall 2 — Patch applies but breaks unrelated module (hidden dependency):**

```python
# BROKEN: Validate only the test file associated with the changed module.
# "tests/test_payments.py" passes but "tests/test_billing.py" breaks
# because billing imports from payments — cross-module coupling not caught.
import subprocess
def broken_validate(patch_dir: str) -> bool:
    r = subprocess.run(["pytest", "tests/test_payments.py"], cwd=patch_dir)
    return r.returncode == 0

# FIX: Always run the full test suite (or at minimum, all tests in
# packages that import the changed module — computed via import graph).
def fixed_validate(patch_dir: str) -> dict[str, object]:
    r = subprocess.run(
        ["pytest", "-x", "--timeout=120", "-q"],  # full suite, fail fast
        cwd=patch_dir, capture_output=True, text=True,
    )
    return {"passed": r.returncode == 0, "output": r.stdout[-2000:]}
```

**Pitfall 3 — Cost overrun from large diffs and repeated retries:**

```python
# BROKEN: No cost cap — agent retries 50× on a complex bug, spending $15.
# No budget monitoring during execution.
config = {"max_iterations": 50, "no_cost_limit": True}

# FIX: Hard cost cap ($2.00) + iteration limit (30) + budget warning at 50%.
# If cost exceeds $1.00 (50% of cap), switch to a cheaper model for remaining steps.
def cost_aware_config() -> dict[str, object]:
    return {
        "max_iterations": 30,
        "cost_limit_usd": 2.00,
        "cost_warning_threshold_usd": 1.00,
        "fallback_model_on_warning": "claude-haiku-4-5",
    }
```

**Metrics:**

| Metric | Before (manual only) | After (agent + human review) |
|--------|---------------------|------------------------------|
| Tagged issues resolved/week | 80 (engineer time) | 180 (agent + engineer) |
| Avg time to PR from issue | 3.5 days | 14 min (agent) + 45 min (review) |
| First-attempt success rate | 100% (human) | 53% auto-closed |
| With 1 retry | — | 67% auto-closed |
| Regression rate (agent PRs) | 0.3% baseline | 0.4% (acceptable) |
| Avg cost per issue (LLM) | — | $1.12 |
| Avg iterations to solution | — | 18 |
| Engineer time freed/week | — | ~28 person-hours |
| SWE-bench Verified (claude-sonnet-4-6) | N/A | ~49% (public benchmark) |

**Interview Q&As:**

**Q: What is SWE-bench and why is it the key benchmark for coding agents?**
SWE-bench (Software Engineering Benchmark) tests coding agents on real GitHub issues from popular open-source Python repositories (Django, Scikit-learn, etc.). Each task gives the agent a repository and an issue description; the agent must produce a patch that makes the test suite pass. SWE-bench Verified is a curated subset of 500 tasks confirmed reproducible and correctly specified. It is the industry standard because the tasks are real-world (not toy problems) and the evaluation is objective (tests either pass or fail). Leading scores as of mid-2025: Claude claude-sonnet-4-6 ~49%, GPT-4o ~30%, o3 ~70%.

**Q: Why is the Agent-Computer Interface (ACI) design as important as model choice for coding agents?**
The ACI defines how the LLM interacts with the codebase — what commands are available, how file content is presented, how errors are formatted. A poorly designed interface forces the agent to parse complex bash output, guess file locations, and hold large file contents in context. SWE-agent showed that domain-specific commands (`goto`, `scroll_down`, `edit start:end`) achieve 12% higher SWE-bench solve rates than raw bash with GPT-4 — the same model, different interface. Good ACI: small precise commands, windowed file views, structured error feedback.

**Q: How does repo map generation enable context-efficient navigation of large codebases?**
A 180k LOC codebase at ~75 characters/line is ~13.5M characters — far exceeding any context window. The repo map compresses this to a ~40KB index of file paths and function/class signatures, allowing the agent to understand the codebase structure and navigate to relevant files without reading them all. The agent uses the map to identify candidate files, then reads only those files (windowed) to understand the specific code. This reduces token usage by 20-200× compared to naive whole-file reading.

**Q: Why require human review for AI-generated PRs even when all CI checks pass?**
CI checks validate mechanical correctness (tests pass, types check, linter passes) but not semantic correctness or business logic. An agent might fix the immediate bug while introducing a subtly incorrect behavior that only manifests under unusual conditions not covered by tests. In fintech, an incorrect fix to a payment calculation or access control check could cause financial loss or security breach — costs far exceeding the time saved by autonomous merging. Human review provides the semantic validation layer that CI cannot. The goal is 40% issue auto-resolution, not 100% — human judgment is intentionally kept in the loop for all merges.

**Q: How do you prevent coding agents from generating patches that pass tests locally but fail in production?**
Three strategies: (1) Identical environments — run the agent in a Docker container that exactly matches the CI environment (same Python version, same dependencies, same environment variables); environment mismatch is the most common source of "passes locally, fails in prod." (2) Full test suite — never validate on a subset; cross-module dependencies mean a patch to module A can break module B's tests. (3) Staged rollout — merge AI PRs to a staging branch first; run integration tests (not just unit tests) before promoting to main.

**Q: What are the signs that an issue is NOT suitable for a coding agent?**
Five signals: (1) Ambiguous requirements — "improve performance" without a specific bottleneck; agents need a concrete, verifiable goal. (2) Missing reproduction steps — agent cannot confirm it fixed the bug if it cannot reproduce it. (3) Cross-service changes — if the fix requires changing an external API contract or database schema, agent scope must include those systems. (4) Design decisions — issues requiring architectural trade-off discussions should stay with humans. (5) Security-sensitive code — authentication, authorization, cryptography changes need human security review regardless of test coverage.
