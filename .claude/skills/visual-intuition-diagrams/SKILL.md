---
name: visual-intuition-diagrams
description: Generate, add, create, or validate ASCII "visual intuition" diagrams for technical concepts in this Markdown study repo. Use when adding diagrams to a module README or sub-file, when asked to "make this concept visual" / "add a diagram" / "draw the architecture", or to check/preview existing ASCII diagrams for alignment.
---

# Visual Intuition Diagrams

This repo is pure Markdown — no runnable app. The "diagrams" are ASCII art in
fenced code blocks (repo rule: no Mermaid, no image files). A good diagram makes
an *abstract relationship physically visible* — the causal-mask grid and the
sliding-window before/after pair in
`llm/foundations_and_architecture/README.md` are the gold standard.

The driver for this skill is a validator+previewer:
**`.claude/skills/visual-intuition-diagrams/diagram_tools.py`**. You author a
diagram, then run it through the driver to confirm it renders cleanly in a
monospace terminal before committing. Paths below are relative to the repo root.

> **Box-drawing chars are single Unicode codepoints**, so `len(line)` equals the
> visual column width. That is *why* the width check is meaningful — trust it.

## Run (agent path) — validate & preview

```bash
# 1. List every diagram block in one or more files (index, line, size, width)
python3 .claude/skills/visual-intuition-diagrams/diagram_tools.py list \
  src/main/java/com/rutik/systemdesign/llm/foundations_and_architecture/README.md

# 2. Lint diagram blocks: tabs, trailing whitespace, emoji, >100-col width.
#    Exit code 1 if any ERROR. Accepts files OR directories (recurses *.md).
python3 .claude/skills/visual-intuition-diagrams/diagram_tools.py check \
  src/main/java/com/rutik/systemdesign/llm/foundations_and_architecture/

# 3. Print one block verbatim under a column ruler — eyeball the alignment.
python3 .claude/skills/visual-intuition-diagrams/diagram_tools.py preview \
  src/main/java/com/rutik/systemdesign/llm/foundations_and_architecture/README.md 3
```

A "diagram block" is a fenced block with **no language tag** (just ```` ``` ````).
Tagged blocks (```` ```python ````, ```` ```sql ````) are code and are skipped.

## The authoring loop

1. **Pick the archetype** that matches the concept's *shape* (catalog below).
2. **Draft** the block. Reuse numbers already in the surrounding prose. Don't
   invent *factual* figures (benchmark scores, costs, sizes). The exception is a
   purely *mechanical* illustration — e.g. a similarity matrix showing how MaxSim
   takes a per-row max — where small clearly-illustrative cell values are fine, the
   same way the temperature bars use example probabilities. The numbers demonstrate
   the operation, they don't assert a fact.
3. **`check`** the file → fix any ERROR (tabs/whitespace/emoji) and any WARN
   (width > 100; prefer vertical stacking over wide side-by-side).
4. **`preview`** the block → confirm columns line up against the ruler.
5. **Place** it next to the prose/table/code it explains, under a `### Title`
   subhead inside `## 5. Architecture Diagrams` (or `## 14. Case Study` when
   it illustrates a case-study mechanic). Add a 1–2 sentence caption tying the
   picture to the insight.

## When a diagram earns its place (especially in already-dense files)

Mature modules are often *already* full of diagrams — but nearly all of them are
**pipeline / data-flow** pictures. When auditing such a file, don't add another flow
diagram. The real gaps are the **math and decision mechanics still trapped in
formulas, prose, or code**: an arithmetic formula (RRF `1/(k+rank)`, ColBERT
`Σ max_j(q_i·d_j)`), a threshold rule (CRAG's 0.3/0.7 buckets), a scale mismatch
(cosine `[-1,1]` vs unbounded BM25), or a hierarchy/dependency structure (Leiden
levels, a decomposition DAG). Those are where a picture changes understanding.

A diagram must **earn** its place. Skip it if it would merely restate a two-number
table — a bar chart of "Full FT 56 GB vs LoRA 28 GB" is fine (it shows a magnitude
*ratio*), but a bar of two arbitrary numbers that the sentence already states adds
nothing. Favor concepts that are math-heavy, spatial, or branching.

## Archetype catalog — match the concept's shape

**1. Constraint grid** — a relationship across two axes (token×token, head×head).
Use for masks, attention spans, bias matrices.
```
Token:    T1   T2   T3   T4
T1:       ✓    ✗    ✗    ✗
T2:       ✓    ✓    ✗    ✗
T3:       ✓    ✓    ✓    ✗
(✓ = can attend, ✗ = masked)
```
Real uses: causal mask, sliding-window mask, ALiBi bias grid. The cells need not be
boolean: a **value grid** holds numbers (e.g. a query-token × doc-token similarity
matrix) and an extra "row max (kept)" column shows a reduction like ColBERT MaxSim.

**2. Before / after with a concrete delta** — show the *win*, don't just state it.
Two grids or two stat blocks side by side, ending in a quantified reduction.
```
Standard cache: 32,768 values/token   →   MLA cache: 512 values/token
At 128K ctx:    253 GB                 →                7.9 GB   (~32× smaller)
```
Real uses: sliding-window cell count, MLA compression, FP8 vs BF16 KV cache.

**3. Side-by-side / stacked flow** — placement or phase differences.
Two vertical data-flow columns, or stacked phase rows, annotated where they diverge.
```
Post-LN                       Pre-LN (residual highway stays clean)
x ─┐                          x ─────────────┐
[sublayer] │ residual          [LayerNorm]    │
[   +   ]◄─┘                    [sublayer] ... │
[LayerNorm]                     [   +   ]◄─────┘
```
Real uses: Pre-LN vs Post-LN, prefill vs decode timeline.

**4. Routing / fan-out** — one input selecting among many paths.
```
token ─► [Router] ─top-K─► [E0]* [E1]* [E2] [E3] [E4] ...   (* = active)
                          total = N×FFN   active = K×FFN
```
Real uses: MoE expert routing, LLM router/cascade selection. Two close relatives live
here: a **hierarchy tree** (Leiden community levels L0→L1→L2) and a **dependency DAG**
(query decomposition: parallel legs Q1‖Q2 that join at a dependent Q3). Draw both as
indented `├─ └─` trees, not top-down trees — see Gotchas for why.

**5. Bar chart** — comparing magnitudes (probabilities, weights). Reuse real numbers.
```
T=0.5  A │████████████████████████ 0.879
       B │████                     0.119
T=2.0  A │████████████             0.516
       B │██████                   0.260
```
Real uses: softmax temperature distributions, attention-sink weight bars.

**6. Curve / vector sketch** — a trend or a geometric intuition.
```
acc 100% ┤▓▓▓▓                              ▓▓▓▓
     50% ┤▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓
         └start────────middle────────────end→
```
Real uses: "lost in the middle" U-curve, embedding vector arithmetic. Two strong
variants: a **vector/angle sketch** (two vectors at a small vs ~90° angle to show
cosine similarity = direction, after L2-normalization) and a **1-D threshold
number-line** (a score axis `0 ── 0.3 ── 0.7 ── 1.0` partitioned into labeled bands,
each routing to an action — CRAG's correct/ambiguous/incorrect buckets).

## Conventions (enforced by `check`)

- **ASCII art only**, fenced block with **no language tag**.
- **Spaces only, never tabs**; no trailing whitespace.
- **No emojis.** Allowed glyphs: box-drawing `│─┌┐└┘├┤┬┴┼`, blocks `▓█░`,
  arrows `→←↑↓`, math `×≈√⊙`, bullets `•`, text checks `✓✗`. (The emoji check
  mark `✅` U+2705 *is* flagged — use `✓` U+2713.)
- **Keep the widest line ≤ 100 cols** (hard WARN). Aim ≤ 90. If a side-by-side
  layout overflows, **stack vertically** instead.
- **Caption every diagram** with 1–2 sentences linking it to the insight; reuse
  numbers from the surrounding text.

## Gotchas

- **Side-by-side blows past 100 cols fast.** Three labeled columns (e.g. MHA |
  GQA | MQA header row) hit 107 cols on the first draft here — `check` flagged
  it, and the fix was to stack the three variants vertically. Default to
  vertical when in doubt.
- **`✓`/`✗` vs `✅`/`❌`.** The text-presentation check marks (U+2713/U+2717)
  are intentional diagram glyphs and pass. The emoji versions are flagged by
  the no-emoji rule. Easy to paste the wrong one.
- **Tagged vs untagged fences.** If you tag a diagram ```` ```text ````, the
  validator treats it as code and skips it — and the repo style wants untagged.
  Leave the info string empty.
- **Alignment drifts with proportional fonts in your head.** Always `preview`;
  the column ruler is the only reliable check that `└─┬─┘` connectors land under
  the right boxes.
- **Em dash `—` is one column** in the validator (correct) but reads "wide".
  Don't pad around it expecting two columns.
- **Indented trees align; top-down trees fight you.** A top-down tree (parent
  centered over children, `▲`/`│` connectors rising to it) needs every level's
  widths to add up perfectly or the connectors miss — painful past two levels. For a
  hierarchy or DAG, prefer an **indented `├─ └─` tree** (root flush-left, children
  one indent in). The Leiden community diagram was redrawn this way and aligned on
  the first `preview`. Reserve top-down only for a shallow fan-out/join (e.g. a 2-leg
  decomposition DAG) where one `┌──┴──┐` split is easy to keep balanced.

## Troubleshooting

- `check` exits 1 with `tab character in diagram` → your editor inserted a tab;
  re-indent with spaces.
- `WARN ... cols wide (>100)` → restructure to vertical stacking or shorten labels.
- `preview` says `no diagram block [N]` → run `list` first to get valid indices
  (they are per-file, 0-based, in document order, untagged blocks only).
