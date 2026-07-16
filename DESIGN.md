# DESIGN.md — LORA (Learn Often, Recall Always)

The visual identity of the game and the study content it renders. Companion to `PRODUCT.md`; token source of truth is `src/main/java/com/rutik/systemdesign/game/style.css`.

---

## Identity

**Wordmark:** LORA — Learn Often, Recall Always. The topbar lockup reads "LORA
by Rutik"; the "by Rutik" byline appears in lockups (topbar, splash, store
listings) but never inside the launcher icon itself.

**Mark — "The Expert Gate":** a mixture-of-experts router diamond — the
branching decision point of a token being routed to one of several experts —
rendered as a single gold-gated path threading an aurora gradient on a
midnight base; every other path stays unlit. Master file: `game/logo.svg`
(`src/main/java/com/rutik/systemdesign/game/logo.svg`); derived app icons and
favicons are exported from it, not redrawn.

A living aurora-mesh: three blurred 110px drift blobs on 64-88s alternating loops, floating over a blueprint grid. Glass materials (translucent panels, soft borders, layered shadows) float above the mesh. The scene should feel alive but never busy — motion is ambient, not attention-seeking.

---

## Token Architecture

Source of truth: `game/style.css` (approx. lines 15-137). The families:

| Family | Tokens |
|--------|--------|
| Surfaces | `--bg`, `--bg-2` |
| Text ladder | `--text`, `--muted`, `--faint` |
| Accent | accent pair + `--accent-ink` |
| Semantics | `--good`, `--bad`, `--warn` |
| Mesh | `--mesh-1`, `--mesh-2`, `--mesh-3`, `--mesh-o` (opacity) |
| Materials | glass stack, shadows |
| Geometry | radii 12 / 16 / 20 |
| Type | system font stack + `--mono` |
| Motion | `--ease-out: cubic-bezier(0.22, 1, 0.36, 1)` |

**Rule:** every new color is a token or a `color-mix()` of tokens, and must read on both midnight and daylight.

---

## Themes

Set via `data-theme` on `<html>`; `?theme=` query previews a theme without persisting.

| Theme | Background | Accent pair | Notes |
|-------|-----------|-------------|-------|
| midnight | `#06070d` | `#7cabff` + `#9d8cff` | default |
| orchid | `#0c0611` | `#c084fc` + `#f472b6` | |
| ember | `#0e0709` | `#fb923c` + `#f87171` | |
| daylight | `#eef1f7` | (light scheme) | mesh opacity 0.46 |

---

## One-Dark Reader Island

**HARD LIMIT:** the reader is an island — `--rd-bg` is `#000000` opaque and `--rd-text` is `#ffffff` in EVERY theme. No `[data-theme]` override may touch reader tokens.

| Element | Color |
|---------|-------|
| Heading hues (rotating) | `#e06c75`, `#61afef`, `#c678dd`, `#56b6c2` |
| Strong / Q markers | `#e5c07b` |
| Code | `#98c379` |

---

## Type, Spacing, Motion

- Base 16px / 1.6 line-height on the system font stack; tabular numerals for stats.
- Touch targets are 44px minimum below 640px width; the tab bar reserves `--tabbar-h`.
- ALL animation is gated behind `prefers-reduced-motion`.
- The moments engine is the single celebration channel — no surface fires its own confetti/sound.

---

## Section Identity

`SECTION_IDENTITY` in `game/app.js` maps each section to a `--sec-accent`, which tints the masthead, progress ring, TOC markers, and drop-cap in that section's reader and study views. One hue per section, applied consistently.

---

## Study-Content Visual Language

Mermaid diagrams in study files use the One-Dark `classDef` palette, semantic-by-role:

| Class | Hex | Role |
|-------|-----|------|
| io | `#61afef` | inputs/outputs, interfaces |
| frozen | `#c678dd` | fixed/external components |
| train | `#98c379` | mutable/trainable/happy path |
| mathOp | `#d19a66` | computation/transform steps |
| lossN | `#e06c75` | losses, failures, hot paths |
| req | `#56b6c2` | requests, clients, queries |
| base | `#e5c07b` | storage, foundations, hubs |

Four owner rules for every Mermaid diagram: (1) colour every node, (2) horizontal-first (`flowchart LR` default), (3) no spillover text — short labels, wrap with `<br/>`, (4) rounded shapes.

ASCII visual-intuition diagrams (grids, before/after, alignment-critical layouts) stay ASCII: max 100 columns, always captioned. No emojis anywhere in the repo.
