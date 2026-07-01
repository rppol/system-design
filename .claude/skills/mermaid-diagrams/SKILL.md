---
name: mermaid-diagrams
description: Create, convert, or validate Mermaid flowchart diagrams in this study repo. Use when a concept is a directed process, pipeline, or architecture with branching flows that would be unreadable as ASCII. Also use when asked to "convert this ASCII to Mermaid", "make this diagram colorful", or "add a Mermaid diagram". Invoke BEFORE writing any mermaid fences.
---

# Mermaid Diagrams

This repo is ASCII-first by default (root `CLAUDE.md`: "ASCII only — no Mermaid,
no image files"). Mermaid is a **deliberate exception** for diagrams whose shape
is a directed flowchart with many branches or crossing arrows — the type that
produces unreadable ASCII art. The first exception was `llm/fine_tuning/lora.md`.

The game reader (`game/app.js`) already renders Mermaid via lazy CDN import.
GitHub renders mermaid fences natively. Both surfaces work; no build step needed.

---

## Decision: Mermaid vs ASCII

Run this check before touching any diagram.

### Use Mermaid when ALL of the following are true:
1. The concept is a **directed flow** (process steps, branching pipelines, forward/backward passes)
2. The diagram would require **crossing arrows** or **>3 fan-out branches** in ASCII
3. The nodes carry **distinct semantic roles** that color-coding would clarify
4. The diagram is inside a study section file (not CLAUDE.md, skill files, or tooling docs)

### Keep ASCII when ANY of the following is true:
- The diagram is a **constraint grid** (matrix, token×token mask, value table) — Mermaid cannot do grids
- The diagram is a **bar chart** (comparing magnitudes) — Mermaid's gantt/xychart are too heavy
- The diagram is a **before/after delta** (two stat blocks side by side) — a table or stacked pair works
- The diagram is a **number-line / threshold axis** (e.g. CRAG 0.3/0.7 bands) — a simple axis sketch
- The diagram is a **vector/angle sketch** — geometry, not flowcharts
- The file already has many diagrams and the new one would merely restate a sentence

In the `lora.md` experiment, the §3 matrix-sliver and singular-value bar chart
were left as ASCII; only the 4 true flowcharts were converted. That line is the
right call to repeat.

---

## Color System

Every Mermaid flowchart in this repo uses this One-Dark palette. Copy the
classDef block verbatim at the top of every new diagram and assign the semantic
class that matches the node's role.

```
classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a
```

| Class | Color | Semantic Role |
|-------|-------|---------------|
| `io` | Blue `#61afef` | Input/output tensors, data endpoints, user requests |
| `frozen` | Purple `#c678dd` | Frozen/locked parameters, external dependencies, no-gradient paths |
| `train` | Green `#98c379` | Trainable weights, active adapters, learnable components |
| `mathOp` | Orange `#d19a66` | Math operations, summation nodes, transformations |
| `lossN` | Red `#e06c75` | Loss functions, errors, backward-pass sources, critical path |
| `req` | Teal `#56b6c2` | Requests, tagged inputs, consumers |
| `base` | Gold `#e5c07b` | Base models, foundation components, shared resources |

Assign every node exactly one class. If none fits, use the closest analogy
(e.g. a decoder block is `frozen` if it's shared, `train` if it's updated).

### Edge style conventions
- Solid arrow `-->` — forward pass, data flow, normal call
- Dotted arrow `-.->` — backward pass, gradient signal, soft dependency
- Label on edge `-->|"× alpha/r"|` — use for scale factors or transformation names

---

## Diagram Types

Use exactly one of these types per diagram. Pick the one that matches the
concept's *topology*, not its name.

### flowchart LR — left-to-right pipeline
Best for: sequential processes, adapter bypass paths, request routing.
Fan-out naturally expands downward without overlap.
```mermaid
flowchart LR
    classDef io fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    ...
    x([input]) --> proc["Processing"] --> y([output])
    class x,y io
```

### flowchart TD — top-to-bottom hierarchy
Best for: pipeline stages with subgraph boxes (Data → Training → Serving →
Eval). Subgraphs group phases clearly; avoid when the chart is wide (>5 nodes
per level).
```mermaid
flowchart TD
    subgraph Phase1["Data Pipeline"]
        A["step 1"] --> B["step 2"]
    end
    Phase1 --> Phase2
```

### sequenceDiagram — request/response across actors
Best for: gRPC call chains, OAuth flows, multi-service request paths.
Do NOT use for ML model internals — they are not actor-based.

---

## Authoring Loop

1. **Decide** — run the Mermaid vs ASCII checklist above. Stop if ASCII wins.
2. **Pick type** — `flowchart LR`, `flowchart TD`, or `sequenceDiagram`.
3. **Draft** — write the mermaid fence. Copy the full `classDef` block at the top. Assign a class to every node via `class nodeId className`.
4. **Verify in reader** — open the file in the game reader (`http://127.0.0.1:8777`), navigate to the file; the diagram renders as SVG with colors. Confirm node colors match semantic roles.
5. **Check classDef is applied** — open browser console, run: `[...document.querySelectorAll(".mermaid .node")].map(n => ({id: n.id, cls: n.className?.baseVal}))`. Every node should have a class like `"node default io"`, not just `"node default"`.
6. **Caption** — add a 1–2 sentence caption below the fence tying the diagram to the insight.
7. **Don't touch** the adjacent ASCII visual-intuition diagrams (grids, charts) in the same file.

### Common failure: stale readerCache
If colors show correctly in the JS test but not in the reader, the reader has
cached the pre-edit version of the file. Fix:
```js
// In browser console while reader is open:
Object.keys(readerCache).filter(k => k.includes("lora")).forEach(k => delete readerCache[k]);
_mermaidReady = null;
openReaderPath("llm/fine_tuning/lora.md", "LoRA", null);
```

---

## ASCII → Mermaid Conversion Guide

When converting an existing ASCII flowchart to Mermaid:

### Step 1 — Classify the ASCII block
Run the decision checklist. If the block is a grid, bar chart, or number-line,
stop: it stays ASCII.

### Step 2 — Extract topology
From the ASCII art, identify:
- **Nodes** (boxes, circles, labels) — record their text content
- **Edges** (arrows `-->`, `->`, `|`) — record source → target + any label
- **Groups** (dashed box, label above section) — these become `subgraph`

### Step 3 — Assign semantic classes
For each node, decide: is it I/O, frozen, trainable, a math op, loss, request,
or base? Look at what the surrounding text says about that component.
- Weight matrix that is not updated → `frozen`
- Adapter matrix being trained → `train`
- Summation point (`+`) → `mathOp`
- Input or output token → `io`

### Step 4 — Write the mermaid block
```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    %% nodes (topology from ASCII)
    x([x]) --> W["W frozen"]
    x --> A["A · r×k"]
    A --> B["B · d×r"]
    B --> plus((" + "))
    W --> plus
    plus --> h([h])

    %% semantic coloring
    class x,h io
    class W frozen
    class A,B train
    class plus mathOp
```

### Step 5 — Delete the ASCII block
Replace the fenced block (`` ``` `` ... `` ``` ``) in the file with the mermaid
fence. Preserve the surrounding heading and caption sentence; do not rewrite them.

### Step 6 — Keep the visual-intuition ASCII blocks in the same file
Only convert blocks that are flowcharts. Leave constraint grids, bar charts,
and before/after pairs untouched even if they are in the same section.

---

## Node Shape Reference

| Syntax | Shape | Use for |
|--------|-------|---------|
| `id["label"]` | Rectangle | Weight matrices, model components, processing steps |
| `id([label])` | Stadium/pill | Inputs, outputs, data nodes |
| `id{label}` | Diamond | Decision points, routing conditions |
| `id((" + "))` | Circle with label | Summation / math operation nodes |
| `id[["label"]]` | Double-border rect | Sub-routines, external services |

---

## Reader Rendering Architecture

The game reader's `renderMermaid()` in `game/app.js` does three things:

1. **Lazy CDN import** — `import("mermaid@11/dist/mermaid.esm.min.mjs")` is only
   fetched when a page has `.mermaid` divs; zero cost for non-mermaid pages.
2. **`mermaid.initialize()`** — called once with `theme:"dark"` and `themeVariables`
   plus `flowchart: { curve:"basis", padding:20, nodeSpacing:45, rankSpacing:55 }`.
3. **SVG post-processing after `mermaid.run()`** — required for three things that
   Mermaid's themeVariables cannot reach:

```js
nodes.forEach(n => {
  // Round node corners — no themeVariable exposes border-radius
  n.querySelectorAll(".node rect").forEach(r => {
    r.setAttribute("rx", "8"); r.setAttribute("ry", "8");
  });
  // Round subgraph cluster corners
  n.querySelectorAll(".cluster rect").forEach(r => {
    r.setAttribute("rx", "12"); r.setAttribute("ry", "12");
  });
  // Color arrowhead markers — <marker> elements in SVG <defs> are separate
  // from the edge path and ignore lineColor themeVariable entirely; only
  // setAttribute("fill") after render reaches them
  n.querySelectorAll("marker path, marker polygon").forEach(m => {
    m.setAttribute("fill", "#61afef"); m.removeAttribute("stroke");
  });
});
```

CSS in `game/style.css` adds the remaining polish:
```css
.md-body .mermaid svg .edgePath .path { stroke: #61afef !important; stroke-width: 2px !important; }
.md-body .mermaid svg .edgeLabel .label rect { fill: transparent !important; stroke: none !important; }
.md-body .mermaid svg .edgeLabel foreignObject > div { background: transparent !important; color: #e5c07b !important; }
.md-body .mermaid svg .cluster rect { stroke-dasharray: 5 3 !important; stroke-width: 1.5px !important; }
```

**Why `themeVariables.edgeLabelBackground:"transparent"`** — the default `"#000000"`
paints a black rect behind every edge label, visible as an ugly pill on the dark
diagram background. Setting it to `"transparent"` removes the rect; the CSS rule
above adds belt-and-suspenders to also nullify the SVG rect fill.

---

## Gotchas

- **Grey arrowheads even with `lineColor` set** — `lineColor` only colors the edge
  path stroke; arrowhead `<marker>` elements in SVG `<defs>` are separate and get
  their fill from SVG attributes, not CSS or themeVariables. Fix: the JS
  post-processing block above (already wired in `renderMermaid()`).
- **Black pill around edge labels** — caused by `edgeLabelBackground:"#000000"` in
  themeVariables. Already fixed to `"transparent"` in the reader. If you see it
  reappear, check that `_mermaidReady` was reset (stale init from a previous page
  load may have the old value cached).
- **Square boxy nodes** — Mermaid has no corner-radius themeVariable; JS
  post-processing sets `rx=8` on `.node rect` after every `mermaid.run()` call.
  Already in `renderMermaid()`; does not need to be added per-diagram.
- **`classDef` must come before the first node definition** in the block. Mermaid
  parses classDef declarations top-down; placing them after node definitions causes
  silent failures where nodes get no class.
- **`class` assignments must be at the bottom** of the diagram, after `end` for all
  subgraphs. A `class` line inside a subgraph body is sometimes ignored.
- **`\n` in node labels** — use `\n` (backslash-n) for a line break inside a label:
  `["W\nfrozen"]`. This works in Mermaid and passes through `esc()` in the reader
  correctly (no entity-encoding issues).
- **`" + "` in circle nodes** — `((" + "))` (circle with space-padded label) renders
  as a small circle. The space padding is intentional for readability.
- **Subgraph title quotes** — `subgraph tr["During Training"]` — the title must be
  double-quoted if it contains spaces. After `esc()` in the reader, `"` becomes
  `&quot;`; the browser decodes this back before Mermaid reads `textContent`. Safe.
- **`data-processed` attribute** — Mermaid sets this after rendering. The reader's
  `renderMermaid()` removes it before re-calling `mermaid.run()` on nav. If you
  call `mermaid.run()` from the console on an already-rendered node, remove the
  attribute first: `node.removeAttribute("data-processed")`.
- **Em dash `—` in labels** is one Mermaid "character" and renders fine in SVG.
  Middle dot `·` and times `×` also render fine.
