# React Section — CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/react/`
Global conventions (formatting, templates, Q&A rules): see root `CLAUDE.md`.
Build manifest / status tracker: `README.md` §7 — check it before starting a new module.

---

## Why this section exists

Before this section, genuine React mentions across 1,248 content files numbered about six —
the 325 files matching `\bReact\b` were almost entirely "Reactor", "reactive" and "ReAct",
the LLM agent pattern. There was no module on components, hooks, rendering, state
management, client hydration or front-end testing anywhere in the repository.

This is the library third of a three-section front-end track:

- **`../typescript/`** — the language (prerequisite for module 3 onward)
- **`../frontend_platform/`** — the browser, network, runtimes, build (**Phase 1 is a
  prerequisite for this entire section**)
- **`react/`** (here) — the library, Server Components, and front-end system design

**Meta-frameworks live here, not in a separate section.** Unlike `python/` to `fastapi/`,
Next.js and React Router are not split out, because in 2026 you cannot teach Server
Components honestly without the framework that implements them.

---

## PRIMARY TARGET VERSION — React 19.3

Released **2026-09-09**. **There is no React 20.** Latest patches are **19.3.1 / 19.2.9 /
19.1.10 / 19.0.9** (2026-10-14). Verified 2026-10-20 against the npm registry — check
`react.dev/versions` before writing a version number, never memory.

| Tag | What it gates |
|-----|---------------|
| `[19.3]` | A Server Component renders a `"use client"` context directly (`<UserContext value={...}>`), `use(browser())` for client-only subtrees, unrelated concurrent transitions render independently, StrictMode double-invokes effects on `hydrateRoot` roots |
| `[19.2]` | `<Activity>`, `useEffectEvent`, `cacheSignal`, React Performance Tracks, `useId` prefix changed to `_r_` for View Transitions compatibility |
| `[19]` | Actions and `useActionState`, `useFormStatus`, `useOptimistic`, `use()`, ref as an ordinary prop, `<Context>` as provider, document metadata hoisting, `preload`/`preinit`, stable Server Components |

**Removed in 19.0 — never present these as available:** `propTypes` and `defaultProps` on
function components, string refs, legacy context, `ReactDOM.render`, `ReactDOM.hydrate`,
`react-test-renderer/shallow`, `findDOMNode`, the `react-dom/test-utils` helpers (`act`
moved to the `react` package, the rest throw), and the **UMD builds** (use an ESM CDN such
as esm.sh).

**REPLACE, DO NOT ANNOTATE.** Never write "`forwardRef` is deprecated, use a ref prop" —
write the ref prop. The three exceptions are in root `CLAUDE.md`. The one place this bites
hardest: `forwardRef` still *works*, so a module may show it only in a migration context,
never as the way to write new code.

### The three facts a stale React curriculum gets wrong

1. **React Compiler 1.0 is stable** (2025-10-07, `babel-plugin-react-compiler`, ESLint
   rules inside `eslint-plugin-react-hooks`, works back to React 17). Auto-memoization is
   the default story; `useMemo`, `useCallback` and `React.memo` are **escape hatches**.
   Any module that teaches manual memoization as the *primary* optimization technique is
   teaching 2023. Meta reports up to 12% faster loads and ~2.5x on some interactions.
2. **Remix v3 is not React.** It is a separate framework with its own UI runtime, release
   candidate 2026-08-31, `remix@3.0.0` on npm 2026-10-01. What would have been
   Remix v3 shipped as **React Router v7**; React Router is
   now at **v8** (2026-06-17). Never present Remix v3 as a React upgrade path — this is the
   easiest factual error in the section.
3. **Next.js 16** (2025-10-21, current 16.4.x) made Turbopack default, replaced
   `experimental.ppr` / `dynamicIO` with **Cache Components** and the `"use cache"`
   directive, and renamed `middleware.ts` to **`proxy.ts`** on the Node runtime.
   `revalidateTag()` now requires a `cacheLife` profile as its second argument.

**Governance:** since **2026-02-24** React, React Native and JSX are stewarded by the
**React Foundation** under the Linux Foundation. This comes up in questions about
ecosystem risk and is worth one line in §1 of module 1.

**Security:** the `react-server-dom-*` packages carried **ten** separate advisories between
2025-12-03 and 2026-10-14, routinely conflated. Two are remote code execution:
**CVE-2025-55182** (CVSS 10.0, 2025-12-03) and **GHSA-xqc2-xf4c-x3h3** (CVSS 4.0 score 9.5,
2026-10-14, no CVE). The rest are source exposure (CVE-2025-55183) and denial of service.
Each patch line supersedes the last; the floor is **19.3.1 / 19.2.9 / 19.1.10 / 19.0.9**
(2026-10-14). Next.js ships its own compiled copy, so its floor is **16.4.1** (or **15.5.28**
on 15.5). Never merge the advisories into one, and never quote an older patch as safe. The
full table lives in `server_components_and_server_functions` §12 and §10.

### Ecosystem pins — verified 2026-10-20

| Area | Pin | Note |
|------|-----|------|
| React Router | **v8** (2026-06-17), current 8.4 | v7 was the Remix merge; "framework mode" is the former Remix |
| TanStack Start | **1.0**, stable since 2026-10-13 | Vite plus Nitro; RSC opt-in |
| Astro | **7** (2026-06-22), current 7.4 | Rust compiler, on Vite 8 |
| RSC outside Next | `@vitejs/plugin-rsc`, Waku, Parcel | a framework-agnostic Vite RSC story now exists |
| TanStack Query / Zustand | **v5** / **v5** | Zustand v5 uses native `useSyncExternalStore`, no polyfill dep |
| Tailwind CSS | **v4** | Oxide engine, CSS-first `@theme`; no `tailwind.config.js` required |
| shadcn/ui | defaults to **Base UI** since 2026-07 | Radix is **not** deprecated; `radix-ui` is now a single package. React Aria is also first-class |
| styled-components | **maintenance mode** (since Jan 2024) | runtime injection is fundamentally incompatible with RSC — a good teaching point, not a slur |
| MUI | **v9** (2026-04-08), current 9.5 — **still defaults to Emotion** | There is no v8: v7 jumped to v9 to re-align with MUI X. Pigment CSS is opt-in since v6, repo alpha/on-hold. **Do not write that MUI "moved to" Pigment** |
| Testing | RTL **16.3.3**, Vitest **5.0.5**, Jest **30.5.2**, MSW **3.0.3**, Storybook **10** (ESM-only) | Vitest 5 (2026-09-03) needs Node 22.12+ and Vite 6.4+ |
| Forms | React Hook Form, TanStack Form v1, Conform | Zod v4 via `@hookform/resolvers` v5 / Standard Schema |

---

## Module List — 21 Modules (5 Phases)

| # | Module Directory | Phase | Key Concepts |
|---|-----------------|-------|-------------|
| 1 | `react_fundamentals_and_the_render_model/` | 1 — The Component Model | Elements vs components, the JSX transform, why re-renders happen, state as a snapshot, `useReducer`, `useId`, keys and the index-as-key bug, a signals-vs-re-render landscape note |
| 2 | `hooks_rules_and_the_hook_model/` | 1 | The hook dispatcher and its linked list, why order matters, closure capture, dependency arrays, custom hooks |
| 3 | `typing_react_components/` | 1 | `ReactNode` vs `ReactElement` vs `JSX.Element`, generic components, discriminated prop unions, polymorphic `as`, typing context, refs, Server Function args |
| 4 | `effects_and_synchronization/` | 1 | Effects as synchronization not lifecycle, cleanup, StrictMode double-invoke, `useEffectEvent` `[19.2]`, you-might-not-need-an-effect |
| 5 | `refs_and_focus_management/` | 1 | `useRef` vs state, ref as a prop `[19]`, `useImperativeHandle`, portals, focus across routes and Suspense boundaries |
| 6 | `context_and_component_communication/` | 1 | `<Context>` as provider `[19]`, context re-render cost, tearing, `useSyncExternalStore`, composition over prop drilling |
| 7 | `fiber_and_reconciliation/` | 2 — Internals & Performance | The fiber node, current/workInProgress double buffering, the work loop, render vs commit, diffing heuristics, lanes |
| 8 | `concurrent_rendering_and_transitions/` | 2 | Interruptible rendering, `startTransition`, `useDeferredValue`, priority lanes, how concurrency changed effect timing |
| 9 | `suspense_and_error_boundaries/` | 2 | Boundary placement, `use()` `[19]`, streaming SSR, request waterfalls (named here, solved in 12), what boundaries do not catch |
| 10 | `react_performance_and_profiling/` | 2 | Profiler API, React Performance Tracks `[19.2]`, `<Activity>` `[19.2]`, virtualization, `React.lazy`, measuring first |
| 11 | `the_react_compiler/` | 2 | Compiler 1.0, Babel AST to HIR, mutability analysis, conditional memoization, directives, when to keep manual memo |
| 12 | `server_state_and_data_fetching/` | 3 — Data, Forms & A11y | Server vs client state, TanStack Query v5, GraphQL normalized caches and fragment colocation, tRPC, codegen, prefetch, contract drift |
| 13 | `client_state_management/` | 3 | Zustand v5, Jotai, Valtio, Redux Toolkit and when it is still right, XState, URL as state |
| 14 | `forms_and_validation/` | 3 | Controlled vs uncontrolled, validation timing, field arrays, error summaries and focus-to-first-error, RHF vs TanStack Form vs Conform |
| 15 | `accessible_component_patterns/` | 3 | Headless primitive a11y contracts, live regions under concurrent rendering, focus trap, roving tabindex, combobox and dialog patterns |
| 16 | `server_components_and_server_functions/` | 4 — Server React | RSC payload, `"use client"`/`"use server"`, serialization rules, Server Functions, `useActionState`, CSRF |
| 17 | `rendering_strategies_hydration_and_seo/` | 4 | CSR/SSR/SSG/ISR/PPR on numbers, client hydration and mismatches, selective hydration, Cache Components, SEO and metadata |
| 18 | `meta_frameworks_and_routing/` | 4 | Next.js 16 App Router, React Router v8, TanStack Start, Astro 7, file-based routing, nested layouts, i18n routing |
| 19 | `styling_and_design_systems/` | 5 — Application Engineering | Tailwind v4, CSS Modules, vanilla-extract, Panda, why runtime CSS-in-JS struggles with RSC, shadcn and Base UI, tokens |
| 20 | `testing_and_frontend_ci/` | 5 | RTL, role queries, `act()`, Vitest browser mode, Playwright, MSW 3, Storybook 10, visual regression, the UI test pyramid |
| 21 | `react_architecture_and_antipatterns/` | 5 | Feature-sliced structure, the client/server seam, micro-frontends and Module Federation, migration, the antipattern catalogue |

**Two ordering decisions that are deliberate, not accidents:**

- **Module 11 (compiler) follows module 10 (profiling)**, not the reverse. The compiler's
  whole justification is that manual memoization was unverifiable, and module 10 owns the
  Profiler and Performance Tracks you use to verify the compiler helped and to decide
  `"use no memo"`. Do not "fix" this ordering.
- **Keys and the index-as-key bug live in module 1**, not module 7. A learner writes a list
  in their first hour; the fiber double-buffering internals can wait until Phase 2.

## Sub-Files (0)

None. **Never name a file `*_deep_dive.md` or `*_internals.md`** — `DEEP_NAME_RE` in
`game/build_tech.py` makes any such page a FATAL `--strict` error unless hand-listed in
`PRODUCT_DEEP_DIVES` / `CONCEPT_DEEP_DIVES`. The two most tempting names in this section —
`fiber_internals.md` and `react_compiler_deep_dive.md` — are both forbidden;
`fiber_and_reconciliation.md` and `the_react_compiler.md` are named around the rule.

---

## Case Studies — 7

11-section principal template, **900-1100 lines each**, 10-16 Q&As in §11.

| File | Concern |
|------|---------|
| `case_studies/design_a_news_feed_with_infinite_scroll.md` | Cursor pagination, virtualization plus scroll restoration on back-navigation, optimistic likes, media prefetch, memory growth over a long session |
| `case_studies/design_a_checkout_and_payment_flow.md` | Multi-step form state, client-supplied idempotency keys, 3DS redirect, the cross-origin card iframe and PCI scope, double-submit prevention |
| `case_studies/design_a_search_and_typeahead_experience.md` | Debounce vs throttle, out-of-order response races, `AbortController`, ARIA combobox and keyboard, empty and error states |
| `case_studies/design_a_realtime_collaborative_editor.md` | CRDT vs OT, optimistic local state, presence, reconnect and backoff, conflict UI |
| `case_studies/design_a_dashboard_with_server_components.md` | The client/server boundary in a real app, streaming a slow widget, per-widget Suspense, `"use cache"` lifetimes |
| `case_studies/design_a_micro_frontend_platform.md` | Module Federation vs Multi-Zones vs iframes, shared-dependency versioning, independent deploys, runtime failure isolation |
| `case_studies/design_an_ai_chat_streaming_ui.md` | Token streaming over SSE, backpressure into the DOM, optimistic user turn, cancel and regenerate, markdown-safe rendering |

Quality bar: executable code in §4; at least one `// BROKEN` to `// FIX` pair in §4; §6 names
**real companies with real published numbers**, never "a large e-commerce site"; §9
quantifies the impact of each war story; §11 carries 10+ Q&As.

**Case-study Q&As never enter the quiz bank** — `extract.py` skips every `case_studies/`
path outright. That is the rule working, not a bug. Do not "rescue" them by moving them
under an interview heading in a module.

There is **no `cross_cutting/` directory** here, following the `fastapi/` precedent — shared
primitives live as modules in the section, and `case_studies/case_studies.md` maps them in
its Cross-Cutting table.

`case_studies/case_studies.md` is mandatory and has five sections: Quick Start (3 files),
Full Learning Path grouped by concern, Cross-Cutting / Shared Primitives, a Mermaid
Dependency Map, and Interview Prep Shortcuts. **Update it in the same commit as any new
case study, no exceptions.**

---

## Learning Paths (Full + Senior + Principal)

`README.md` documents the **Full Path** (all 21 modules) plus two curated tiers, which are
**different cuts, not nested depths**. Membership is declared ONCE, in the
`<!-- study-paths -->` block in this section's `README.md`, with case studies under a
`case_studies` pseudo-module. Order comes from `STUDY_ORDER.react` in `game/app.js`.
**There is no path array in `app.js` to edit.** The tier tables between
`<!-- study-path-table <tier> -->` markers are **generated** by
`python3 game/extract.py --write-paths` — never hand-edit them, and note that
`--write-paths` only fills marker pairs that already exist.

Intended tier shape once modules land:

- **Senior:** 1, 2, 3, 4, 5, 6, 7, 9, 10, 12, 14, 16, 17, 20
- **Principal:** 7, 11, 12, 13, 17, 18, 21

---

## Q&A Minimums

- **15+** per module page — the hard floor from root `CLAUDE.md`
- **18+** for: `hooks_rules_and_the_hook_model`, `effects_and_synchronization`,
  `fiber_and_reconciliation`, `the_react_compiler`,
  `server_components_and_server_functions`, `rendering_strategies_hydration_and_seo`
- Order by interview frequency: gotchas and traps first, then internals, then edge cases
- Every answer opens with its `**Short:**` line — 15-220 characters, self-contained
- **Any pass that edits an answer MUST update the `**Short:**` above it in the same edit**

**A distractor warning specific to this section.** React answers cluster on "component",
"render", "hook", "state" and "effect" more tightly than any other section's vocabulary.
Distractors are drawn from sibling answers' first sentences and IDF-ranked, so
near-identical openers produce questions where all four options read the same. **Keep first
sentences lexically distinct** — lead with the mechanism, not with "The component...".

---

## The "hydration" term collision — MANDATORY

**`hydration` already means *feed-object hydration*** in 14 files across `backend/` and
`hld/` case studies. **Always write "client hydration" or "SSR hydration" on first use in
every module**, or the distractor pool will pair a hydration-mismatch question with a feed
fan-out answer. Stated in `frontend_platform/CLAUDE.md` too, for the same reason.

---

## Code Style in This Section

- Fences are ` ```tsx ` and ` ```ts `, **never** ` ```typescript ` — the reader's
  `LANG_ALIAS` in `app.js` aliases `ts`/`tsx`/`jsx` but not the long form, so it renders
  unhighlighted. No `--strict` check catches this.
- Every component example is TypeScript. This section assumes `../typescript/` Phase 1.
- At least one **`// BROKEN` → `// FIX`** pair in §10 and one in §14 of every module.
- Concrete numbers everywhere — real render counts, real bundle sizes, real millisecond
  budgets. Never "a few", "some", "significant".
- Show the actual React warning or error text when the error *is* the lesson (the hydration
  mismatch diff, the "Rendered more hooks than during the previous render" message).

---

## Boundary with the neighbouring sections — who owns what

| Topic | Owner | Rule |
|-------|-------|------|
| DOM, CSS, rendering pipeline, event loop | `frontend_platform/` Phase 1 | Prerequisite. Modules 10, 17 and 19 assume it and must not re-teach it |
| Core Web Vitals definitions, WCAG, bundling | `frontend_platform/` Phases 3-4 | Those own the metric and standard; modules 10, 15 and 20 own the React-level technique |
| TypeScript generics, conditional types | `typescript/` | That owns the type system; module 3 owns applying it to components |
| Micro-frontends, BFF, UI decomposition | `book/building_microservices/14_user_interfaces/` | That owns the architectural argument; module 21 owns the React mechanics |
| WebSocket/SSE protocol, server scaling | `backend/websockets_and_sse/`, `hld/realtime_updates/` | Those own the server; the case studies own the client |
| REST/GraphQL/gRPC contracts | `backend/rest_api_design/`, `backend/graphql/`, `backend/grpc_and_protobuf/connect_and_browser_rpc.md` | Those own the contract; module 12 owns consuming it |
| LLM streaming, token economics | `llm/` | That owns the model; the AI-chat case study owns the UI |

---

## Section §11 stays `## 11. Technologies & Tools`

This section **wants** to feed the technology index. Do not retitle §11. A §11 row's first
cell is a bare product name, under 42 characters, at most 5 spaces. A **spaced** ` / ` is
split into two records correctly by `NAME_SPLIT_RE`; an **unspaced** slash
(`Next.js/Remix`) is deliberately kept as one literal name — so `TCP/IP` and `HTTP/2`
survive — and becomes one garbled record. **Use two rows.**

Nearly every tool here is **new** to the bank: React, Next.js, Vite, Redux, Zustand,
TanStack Query, Tailwind, Jest, Vitest, Storybook, Cypress, Remix and tRPC are not
currently banked. Watch two live collisions: **`yarn` is already YaRN**, the RoPE rescaling
method, and needs a `TECH_HOMONYMS` entry. **`Astro` does not** — the bank's record is
headed `### Astronomer Astro` (the Airflow product) and keys separately, so a new `astro`
record stays distinct.

---

## Diagram Traps

Diagrams are **appeal-first**. Mermaid is the default; run `/mermaid-diagrams` first,
colour every node with the One-Dark `classDef` palette, keep labels short (`<br/>`), use
rounded shapes. **A fence that does not parse FAILS THE BUILD** —
`scripts/build_diagrams.mjs` exits 1 and takes Pages and the APK red.

| Trap | Breaks | Fix |
|------|--------|-----|
| JSX written raw in a node label — `<Foo />` | `<` opens a tag with `htmlLabels: false` | quote the label, or write `Foo` without brackets |
| `"use client"` in a label with the quotes | quote nesting | write `use client` unquoted inside a quoted label |
| `;` in a `sequenceDiagram` `Note` or message | statement separator cuts the text | `,` or `.` |
| `call` as a node id | reserved word | rename |
| Extra spaces after an edge label's closing `\|` | parse error | single space |
| `\"` in a node label | no escape exists | `#quot;` |

The first two rows are this section's most likely failures and are unique to it — a
component tree diagram is the natural thing to draw and the natural thing to get wrong.
**Validate anything you touch:** `node scripts/check_render.mjs <absolute-file.md>`.

**ASCII stays** for the fiber tree with its `child`/`sibling`/`return` pointers, the
render-vs-commit timeline, and the RSC payload layout — all alignment-critical. Use
`/visual-intuition-diagrams`.

---

## Adding a New React Module

1. Create `<module_name>/<module_name>.md` — the 14-section template, named for its folder.
   Never `*_deep_dive.md` or `*_internals.md`
2. Target React 19.3; verify anything version-gated against `react.dev` before writing it
3. Meet the Q&A minimum, every answer opening with its `**Short:**` line
4. Add the module dir to `STUDY_ORDER.react` in `game/app.js` at its phase position — a
   module missing from it falls to the 9999 sort and fails `--strict`
5. Add the module and EVERY file it owns to the `<!-- study-paths -->` block in this
   section's `README.md`, tagging tiers. **Do NOT put a block in the module page.** Then run
   `python3 game/extract.py --write-paths`
6. Flip Status `pending` to `done` in `README.md` §7 with measured counts
7. Update this file's Module List and `README.md` §2 and §3
8. Update root `README.md` phase table and root `CLAUDE.md` React row

### Adding a case study

- Write it under `case_studies/<name>.md` — 11-section principal template, 900-1100 lines
- **Update `case_studies/case_studies.md`** in the same commit: correct phase, dependency
  map, interview-prep row
- Add it to the `case_studies` pseudo-module in the README's `<!-- study-paths -->` block
- Update `README.md` §5, this file's case-study table, and root `README.md`

---

## HARD RULE — structure lives in `README.md`, content files hold only content

**The section `README.md` is the single source of truth for this section's file inventory
and study-tier membership.** Its `<!-- study-paths -->` block lists EVERY module, EVERY file
that module owns, and EVERY case study, each tagged with its tiers — `-` means Full path only.

**A content file carries NO structural metadata** — no block, no tier declaration, no path
membership.

**Adding a file? Add its line to the block in the same commit.** A file on disk missing from
the block — or listed there and absent from disk — FAILS
`python3 game/extract.py --strict` and takes the Pages deploy red. Both directions are
fatal, which is why the block is grown file-by-file rather than pre-populated.

Order comes from `STUDY_ORDER` in `game/app.js`. The tier TABLES in the README are
GENERATED by `python3 game/extract.py --write-paths` — never hand-edit them.
