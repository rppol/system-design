# Game Visual Overhaul — Design Spec

Date: 2026-07-02
Scope: `src/main/java/com/rutik/systemdesign/game/` (style.css rewrite, app.js additions, index.html tweaks)
Constraints honored: vanilla JS, no build step, no frameworks, stdlib server, all existing features preserved.

## Brief

Comprehensive audit of the game engine + a visual overhaul: Apple-grade CSS, themes,
transparency/glass effects, interactive diagrams, "beautify to the nth degree."

## Subject & voice

System Design Daily is a solo engineer's daily discipline engine — 5 minutes of
interview drills built from the engineer's own notes. Its world is architecture
diagrams, terminals, One Dark IDEs, latency numbers, streaks. The design voice is
"Apple hardware page meets engineering notebook": luminous glass surfaces, precise
type, and a monospace *data voice* for every number and label.

## Signature element: the living backdrop

Glass is invisible on flat `#000` (the current background). The signature is a
fixed, two-layer ambient backdrop that every surface floats above:

1. **Aurora mesh** — 3 huge radial-gradient blobs (theme-colored, ~18% alpha,
   blurred), drifting on a 60–90s loop. Paused under `prefers-reduced-motion`.
2. **Blueprint grid** — an ultra-faint 32px line grid (≤5% alpha), the "graph
   paper" of system design. Static.

Every card, bar, and overlay becomes translucent glass (`backdrop-filter: blur +
saturate`) whose character changes with the theme behind it. This single device
delivers the requested transparency effects and makes themes feel dramatic.

## Themes

Applied as `data-theme` on `<html>`, persisted to `localStorage.sd_theme`,
switchable from a glass popover in the top bar (swatch previews, keyboard
accessible). All colors flow from tokens; no hardcoded hex outside `:root`/theme
blocks (audited).

| Theme | Base | Mesh | Accent | Feel |
|-------|------|------|--------|------|
| Midnight (default) | `#06070d` near-black blue | blue / violet / cyan | `#7cabff` → `#9d8cff` | current identity, elevated |
| Aurora | `#040f0d` deep teal-black | emerald / teal / sky | `#34d399` → `#2dd4bf` | northern-lights calm |
| Ember | `#0e0709` plum-black | coral / amber / magenta | `#fb923c` → `#f472b6` | warm evening focus |
| Daylight | `#eef1f7` fog white | pastel blue / pink / mint | `#3b82f6` → `#8b5cf6` | Apple light-mode glass |

Reader: prose/chrome tokens are theme-scoped (One Dark on dark themes, One
Light-style prose on Daylight), but **code blocks, ASCII diagrams, and Mermaid
canvases stay dark in every theme** (Stripe-docs pattern) — keeps the hand-rolled
One Dark tokenizer palette and the Mermaid dark init correct everywhere with zero
re-render complexity.

## Type system

- **Display**: system SF (`-apple-system`) — hero `clamp(30px…40px)`, weight 750,
  tracking −0.022em; card titles 24/700/−0.015em. Apple's own face is the right
  tool for "like Apple's website" on a Mac-local app.
- **Body**: same family, 16/1.6.
- **Data voice**: `ui-monospace` for eyebrows (11px, caps, +0.12em), stat numbers,
  counts, kbd chips, heatmap legend. Everything measurable reads as instrument.

## Materials & depth

- Glass tokens: `--glass` fill (white 5.5% on dark / 60% on light),
  `--glass-brd` hairline (white 9%), `blur(18–28px) saturate(140–180%)`,
  inset top highlight `0 1px 0 rgba(255,255,255,.06)`.
- Elevation: layered soft shadows; hover = lift 1px + border brighten (no color
  flips). Radii: 20 hero / 16 cards / 12 controls.
- `@supports not (backdrop-filter)` fallback: higher-alpha solid fills.

## Motion

- Screen enter: 360ms fade + 10px rise, `cubic-bezier(.22,1,.36,1)`; cards stagger
  40ms via nth-child delays.
- Micro: buttons compress `scale(.98)`; correct-answer pulse; combo chip pop; goal
  ring keeps its stroke animation and gains a real SVG gradient stroke.
- All motion (incl. mesh drift, confetti) gated by `prefers-reduced-motion`.

## Interactive diagrams

- **Unified lightbox** for Mermaid *and* ASCII diagrams: click to open; wheel-zoom
  toward cursor; drag-to-pan (pointer capture, grab cursor); double-click zoom;
  `+ / − / 0 / arrows / Esc` keys; % readout; glass control bar.
- **Inline Mermaid**: node hover glow (stroke brighten + subtle scale), zoom-in
  cursor affordance kept.
- **Code blocks**: hover copy button in the reader.
- Existing global Mermaid post-processing (rounded corners, blue arrowheads,
  transparent edge labels) is preserved untouched.

## Component unification

All buttons restyled from one recipe (primary gradient w/ glow, glass ghost,
success/danger tints) while keeping existing class names (`cta`, `ghost`, `skip`,
`next`, `grade`, `reader-nav`) so app.js markup churn stays minimal.

## Engine fixes folded in (from the multi-agent audit)

Confirmed low-risk fixes ship with the overhaul; the full audit report (all
dimensions, confirmed + refuted) is delivered in the session summary. Known
already: `wireGrips()`/`wireSidebarGrips()` query `el("readerGrip")` etc. without
`#`, so all drag-resize grips are dead — fix to id selectors.

## Out of scope

Framework/build changes, server rewrites, question-bank format changes, copy
rewrites beyond empty/error states, multi-user anything.
