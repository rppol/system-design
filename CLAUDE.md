# CLAUDE.md — System Design Repository

## What This Repo Is

A comprehensive system design study repository. All content is Markdown — no runnable application.

| Section | Coverage | Sub-CLAUDE |
|---------|---------|-----------|
| **LLD** | Design patterns (GoF), SOLID, anti-patterns | [lld/CLAUDE.md](src/main/java/com/rutik/systemdesign/lld/CLAUDE.md) |
| **HLD** | Distributed system concepts (CAP, caching, queues, sharding) | [hld/CLAUDE.md](src/main/java/com/rutik/systemdesign/hld/CLAUDE.md) |
| **Backend** | Networking, API design, performance, resilience, security, microservices — 34 modules, 5 case studies | [backend/CLAUDE.md](src/main/java/com/rutik/systemdesign/backend/CLAUDE.md) |
| **Database** | Relational, NoSQL, distributed DB, production ops — 29 modules, 6 case studies | [database/CLAUDE.md](src/main/java/com/rutik/systemdesign/database/CLAUDE.md) |
| **Java** | Pure Java senior-engineer guide — 20 modules, 8 case studies | [java/CLAUDE.md](src/main/java/com/rutik/systemdesign/java/CLAUDE.md) |
| **Spring** | Spring Framework guide — 27 modules, 9 case studies | [spring/CLAUDE.md](src/main/java/com/rutik/systemdesign/spring/CLAUDE.md) |
| **Python** | Python + FastAPI guide — 40 modules, 6 case studies | [python/CLAUDE.md](src/main/java/com/rutik/systemdesign/python/CLAUDE.md) |
| **ML** | Machine Learning guide — 33 modules, 22 case studies | [ml/CLAUDE.md](src/main/java/com/rutik/systemdesign/ml/CLAUDE.md) |
| **LLM** | LLM engineering guide — 52 modules, 29 case studies, 82 deep-dive sub-files | [llm/CLAUDE.md](src/main/java/com/rutik/systemdesign/llm/CLAUDE.md) |
| **DevOps** | DevOps / Cloud / Platform guide — 41 modules, 13 case studies | [devops/CLAUDE.md](src/main/java/com/rutik/systemdesign/devops/CLAUDE.md) |
| **CS Fundamentals** | Language-agnostic CS spine — 20 modules, 6 case studies + DSA pattern playbooks sub-section (25-pattern recognition engine, interview execution playbook, Blind 75/NeetCode 150 study plans — complete) | [cs_fundamentals/CLAUDE.md](src/main/java/com/rutik/systemdesign/cs_fundamentals/CLAUDE.md) |

---

## Repository Structure

```
src/main/java/com/rutik/systemdesign/
├── lld/          ← Design patterns + CLAUDE.md
├── hld/          ← System design concepts + CLAUDE.md
├── backend/      ← Backend engineering + CLAUDE.md
├── database/     ← Database engineering + CLAUDE.md
├── java/         ← Pure Java guide + CLAUDE.md
├── spring/       ← Spring Framework guide + CLAUDE.md
├── python/       ← Python + FastAPI guide + CLAUDE.md
├── ml/               ← Machine Learning guide + CLAUDE.md
├── llm/              ← LLM engineering guide + CLAUDE.md
├── devops/           ← DevOps / Cloud / Platform guide + CLAUDE.md
└── cs_fundamentals/  ← CS Fundamentals guide + CLAUDE.md
```

---

## The 14-Section Module Template

Every module README must follow this exact structure:

```
## 1. Concept Overview
## 2. Intuition           (one-line analogy, mental model, why it matters, key insight)
## 3. Core Principles
## 4. Types / Architectures / Strategies
## 5. Architecture Diagrams    (ASCII art — no image files)
## 6. How It Works — Detailed Mechanics   (code, pseudocode, concrete numbers)
## 7. Real-World Examples
## 8. Tradeoffs            (comparison tables)
## 9. When to Use / When NOT to Use
## 10. Common Pitfalls     (production war stories)
## 11. Technologies & Tools
## 12. Interview Questions with Answers   (15+ Q&As, ordered by interview frequency — gotchas and traps first, then internals, then edge cases; bold Q, plain A)
## 13. Best Practices
## 14. Case Study
```

---

## Case Study Templates

### 11-Section Principal Template — Standard for ALL case studies
```
## Intuition
## 1. Requirements Clarification
## 2. Scale Estimation
## 3. High-Level Architecture
## 4. Component Deep Dives
## 5. Design Decisions & Tradeoffs
## 6. Real-World Implementations
## 7. Technologies & Tools
## 8. Operational Playbook
## 9. Common Pitfalls & War Stories
## 10. Capacity Planning
## 11. Interview Discussion Points
```

**Principal case study quality bar:** 900–1100 lines; 4+ cross_cutting/ references; executable code in §4; concrete numbers everywhere; broken→fix example in §4; named companies in §6; quantified impact in §9; 10+ Q&As in §11.

---

## Case Study Learning Path (case_studies/README.md)

Every `case_studies/` directory MUST contain a `README.md` with these 5 sections:

1. **Quick Start** — 3 files to read first, with one-sentence justification each
2. **Full Learning Path** — all case studies grouped by engineering concern; file link + concern phrase + 1–2 sentence summary
3. **Cross-Cutting / Shared Primitives** — table mapping each cross_cutting/ sub-file to the phase where it becomes relevant
4. **Dependency Map** — ASCII tree showing which case studies build on others
5. **Interview Prep Shortcuts** — table mapping "design X" questions to best case study file

**Maintenance rule:** Update this README every time a new case study is added — same commit, no exceptions.

---

## Interview Q&A Rules

- **Bold the question**, plain text the answer
- First sentence = direct answer; following sentences = mechanism / example / gotcha; final sentence = practical guidance
- **Minimum 15 Q&As per module** — this is a hard floor; see section CLAUDE.md for modules that require 18+
- **Order by interview importance**: highest-frequency gotchas and traps first, then internal mechanics, then edge cases and advanced scenarios last

---

## Content Quality Standards

- **Show broken code, then the fix** — DCL without volatile, HashMap concurrency, self-invocation, N+1
- **Concrete numbers everywhere** — virtual thread ~few KB stack; platform thread ~1MB; HashMap capacity 16; load factor 0.75; ArrayList grows 1.5×; G1 pause 200ms; ZGC sub-1ms; HikariCP default pool 10; Tomcat default threads 200; BCrypt cost 10–12
- **Production war stories** in Common Pitfalls — real incident patterns, not toy examples
- **No emojis** in any file
- **Effective Java item references** where applicable (Java section only)

---

## How to Add a New Module

1. Create `<section>/<module_name>/README.md` — 14-section template
2. Meet the Q&A minimum (see section CLAUDE.md for specifics)
3. Update the section's master `README.md`
4. Update root `README.md` table
5. See the section's `CLAUDE.md` for section-specific steps

### Adding a case study
- Write the file following the section's template
- Update the section's master `README.md` case study table
- **Update `case_studies/README.md`** — add to correct phase, update dependency map, add interview prep row
- Update root `README.md` and the section's `CLAUDE.md` case study list

---

## Reference Files

| File | Purpose |
|------|---------|
| `llm/foundations_and_architecture/README.md` | Gold standard 14-section format |
| `llm/case_studies/design_gpu_inference_platform.md` | Gold standard 11-section principal case study |
| `java/concurrency/README.md` | Example of 15+ Q&A deep module |
| `spring/spring_transactions/README.md` | Example of 18+ Q&A deep Spring module |
| `llm/agentic_frameworks/langchain_and_lcel.md` | Example deep-dive sub-file (15+ Q&As) |

---

## Formatting Rules

- All diagrams use **ASCII art only** — no Mermaid, no image files
- Tables use standard Markdown pipe syntax
- Code blocks use triple backticks with language tag (` ```java `, ` ```sql `, ` ```yaml `, etc.)
- Section headers follow exact numbering: `## 1.`, `## 2.`, ... `## 14.`
- Use `---` horizontal rules to separate major sections
- Links between modules: use relative paths, e.g., `[Concurrency](../concurrency/README.md)`

---

## Visual Intuition Diagrams

Section 5 of every module (Architecture Diagrams) — and any place a concept is
hard to picture — should use a **visual intuition diagram**: ASCII art that makes
an abstract relationship *physically visible*. The gold standard is the causal-mask
grid and the sliding-window before/after pair in
`llm/foundations_and_architecture/README.md`.

**Skill:** run `/visual-intuition-diagrams` (at
`.claude/skills/visual-intuition-diagrams/`) to generate or validate these. It
ships a validator/previewer — author a diagram, then run it through the driver
before committing:

```bash
# Lint diagram blocks (tabs, trailing whitespace, emoji, >100-col width); accepts files or dirs
python3 .claude/skills/visual-intuition-diagrams/diagram_tools.py check <path-or-dir>
# Print one block under a column ruler to eyeball alignment
python3 .claude/skills/visual-intuition-diagrams/diagram_tools.py preview <file.md> <index>
```

**Pick the archetype that matches the concept's shape:**

| Archetype | Use when the concept is… | Examples |
|-----------|--------------------------|----------|
| Constraint grid | a relationship across two axes (X×Y) | causal mask, ALiBi bias, sliding window |
| Before/after + delta | a quantified win | KV-cache reduction, MLA compression |
| Side-by-side / stacked flow | a placement or phase difference | Pre-LN vs Post-LN, prefill vs decode |
| Routing / fan-out | one input selecting among many paths | MoE experts, router/cascade |
| Bar chart | comparing magnitudes | softmax temperature, attention-sink weights |
| Curve / vector sketch | a trend or geometric intuition | "lost in the middle", embedding arithmetic |

**Conventions (enforced by the validator):** ASCII only, fenced block with **no
language tag**; spaces not tabs; no trailing whitespace; **no emojis** (use `✓`/`✗`,
not `✅`/`❌`); widest line ≤ 100 cols (prefer vertical stacking over wide
side-by-side); caption every diagram with 1–2 sentences tying it to the insight and
reuse numbers already in the surrounding text.
