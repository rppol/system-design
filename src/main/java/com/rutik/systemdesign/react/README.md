# React — Senior Engineer & Interview Prep Guide

The library half of the front-end track. The language lives in
[../typescript/](../typescript/README.md); the browser, build pipeline and toolchain live in
[../frontend_platform/](../frontend_platform/README.md).

A comprehensive reference for **React as it exists in 2026** — the component and render
model, the fiber architecture and concurrent rendering, the compiler that has replaced hand
memoization, Server Components and the client/server boundary, and the application-level
concerns (data, forms, accessibility, styling, testing, architecture) that a senior React
engineer owns. Includes seven front-end **system design** case studies.

> **No runnable application** — all content is Markdown with runnable-shaped TypeScript and TSX blocks.

---

## 1. Section Overview

This section covers:

- **The component model** — elements vs components, why a re-render happens, the hook
  dispatcher, typing React, effects as synchronization, refs and focus, context and tearing
- **Internals, concurrency and performance** — fiber and reconciliation, interruptible
  rendering and transitions, Suspense and error boundaries, profiling, and the React
  Compiler
- **Data, forms and accessible components** — server state vs client state, the data-fetching
  layer (TanStack Query, GraphQL clients, tRPC), forms and validation, and the accessibility
  contracts a component library owes its users
- **Server React and frameworks** — Server Components and Server Functions, rendering
  strategies and client hydration, SEO, and the meta-framework landscape
- **Application engineering** — styling and design systems, testing and front-end CI, and
  architecture including micro-frontends

**Primary target: React 19.3.** Meta-frameworks (Next.js 16, React Router v8, TanStack
Start, Astro 7) are covered **inside this section** rather than split out, because in 2026
you cannot teach Server Components honestly without the framework that implements them.

---

## 2. Module Table

| # | Module Directory | Phase | Difficulty | Key Topics |
|---|-----------------|-------|-----------|-----------|
| 1 | [react_fundamentals_and_the_render_model](react_fundamentals_and_the_render_model/react_fundamentals_and_the_render_model.md) | 1 — The Component Model | Beginner | Elements vs components, the JSX transform, why re-renders happen, state as a snapshot, `useReducer`, keys and the index-as-key bug |
| 2 | [hooks_rules_and_the_hook_model](hooks_rules_and_the_hook_model/hooks_rules_and_the_hook_model.md) | 1 — The Component Model | Intermediate | The hook dispatcher and its linked list, why order matters, closure capture, dependency arrays, custom hooks |
| 3 | [typing_react_components](typing_react_components/typing_react_components.md) | 1 — The Component Model | Advanced | `ReactNode` vs `ReactElement`, generic components, discriminated prop unions, polymorphic `as`, typing context, refs and Server Function args |
| 4 | [effects_and_synchronization](effects_and_synchronization/effects_and_synchronization.md) | 1 — The Component Model | Advanced | Effects as synchronization not lifecycle, cleanup, StrictMode double-invoke, `useEffectEvent` `[19.2]`, you-might-not-need-an-effect |
| 5 | [refs_and_focus_management](refs_and_focus_management/refs_and_focus_management.md) | 1 — The Component Model | Intermediate | `useRef` vs state, ref as a prop `[19]`, `useImperativeHandle`, portals, focus across routes and Suspense boundaries |
| 6 | [context_and_component_communication](context_and_component_communication/context_and_component_communication.md) | 1 — The Component Model | Intermediate | `<Context>` as provider `[19]`, context re-render cost, tearing, `useSyncExternalStore`, composition over prop drilling |
| 7 | [fiber_and_reconciliation](fiber_and_reconciliation/fiber_and_reconciliation.md) | 2 — Internals & Performance | Advanced | The fiber node, current/workInProgress double buffering, the work loop, render vs commit, diffing heuristics, lanes |
| 8 | [concurrent_rendering_and_transitions](concurrent_rendering_and_transitions/concurrent_rendering_and_transitions.md) | 2 — Internals & Performance | Advanced | Interruptible rendering, `startTransition`, `useDeferredValue`, priority lanes, how concurrency changed effect timing |
| 9 | [suspense_and_error_boundaries](suspense_and_error_boundaries/suspense_and_error_boundaries.md) | 2 — Internals & Performance | Advanced | Boundary placement, the `use()` hook `[19]`, streaming SSR, request waterfalls, what error boundaries do not catch |
| 10 | [react_performance_and_profiling](react_performance_and_profiling/react_performance_and_profiling.md) | 2 — Internals & Performance | Advanced | Profiler API, React Performance Tracks `[19.2]`, `<Activity>` `[19.2]`, virtualization, `React.lazy`, measuring first |
| 11 | [the_react_compiler](the_react_compiler/the_react_compiler.md) | 2 — Internals & Performance | Advanced | Compiler 1.0, Babel AST to HIR, mutability analysis, conditional memoization, the directives, when to keep manual memo |
| 12 | [server_state_and_data_fetching](server_state_and_data_fetching/server_state_and_data_fetching.md) | 3 — Data, Forms & A11y | Advanced | Server vs client state, TanStack Query v5, GraphQL normalized caches, tRPC, codegen, prefetch, contract drift |
| 13 | [client_state_management](client_state_management/client_state_management.md) | 3 — Data, Forms & A11y | Intermediate | Zustand v5, Jotai, Valtio, Redux Toolkit and when it is still right, XState, URL as state |
| 14 | [forms_and_validation](forms_and_validation/forms_and_validation.md) | 3 — Data, Forms & A11y | Intermediate | Controlled vs uncontrolled, validation timing, field arrays, error summaries and focus, React Hook Form vs TanStack Form vs Conform |
| 15 | [accessible_component_patterns](accessible_component_patterns/accessible_component_patterns.md) | 3 — Data, Forms & A11y | Advanced | Headless primitive a11y contracts, live regions under concurrent rendering, focus trap, roving tabindex, combobox and dialog patterns |
| 16 | [server_components_and_server_functions](server_components_and_server_functions/server_components_and_server_functions.md) | 4 — Server React | Advanced | RSC payload, `"use client"` and `"use server"`, serialization rules, Server Functions, `useActionState`, CSRF |
| 17 | [rendering_strategies_hydration_and_seo](rendering_strategies_hydration_and_seo/rendering_strategies_hydration_and_seo.md) | 4 — Server React | Advanced | CSR/SSR/SSG/ISR/PPR on numbers, client hydration and mismatches, selective hydration, Cache Components, SEO and metadata |
| 18 | [meta_frameworks_and_routing](meta_frameworks_and_routing/meta_frameworks_and_routing.md) | 4 — Server React | Advanced | Next.js 16 App Router, React Router v8, TanStack Start, Astro 7, file-based routing, nested layouts, i18n routing |
| 19 | [styling_and_design_systems](styling_and_design_systems/styling_and_design_systems.md) | 5 — Application Engineering | Intermediate | Tailwind v4, CSS Modules, vanilla-extract, Panda, why runtime CSS-in-JS struggles with RSC, shadcn and Base UI, tokens |
| 20 | [testing_and_frontend_ci](testing_and_frontend_ci/testing_and_frontend_ci.md) | 5 — Application Engineering | Intermediate | React Testing Library, role queries, `act()`, Vitest browser mode, Playwright, MSW 3, Storybook 10, visual regression |
| 21 | [react_architecture_and_antipatterns](react_architecture_and_antipatterns/react_architecture_and_antipatterns.md) | 5 — Application Engineering | Advanced | Feature-sliced structure, the client/server seam, micro-frontends and Module Federation, migration, the antipattern catalogue |

---

## 3. 5-Phase Learning Path

```mermaid
flowchart LR
    p1(["Phase 1<br/>The Component<br/>Model<br/>6 modules"]) --> p2(["Phase 2<br/>Internals and<br/>Performance<br/>5 modules"])
    p2 --> p3(["Phase 3<br/>Data, Forms<br/>and A11y<br/>4 modules"])
    p3 --> p4(["Phase 4<br/>Server React<br/>3 modules"])
    p4 --> p5(["Phase 5<br/>Application<br/>Engineering<br/>3 modules"])
    p5 --> cs(["7 Case Studies"])
    classDef comp fill:#2c313a,stroke:#61afef,color:#abb2bf
    classDef intr fill:#2c313a,stroke:#c678dd,color:#abb2bf
    classDef data fill:#2c313a,stroke:#98c379,color:#abb2bf
    classDef serv fill:#2c313a,stroke:#e5c07b,color:#abb2bf
    classDef appl fill:#2c313a,stroke:#56b6c2,color:#abb2bf
    classDef cstu fill:#2c313a,stroke:#e06c75,color:#abb2bf
    class p1 comp
    class p2 intr
    class p3 data
    class p4 serv
    class p5 appl
    class cs cstu
```

### Phase 1 — The Component Model (6 modules)

| # | Module | Why it is here |
|---|--------|----------------|
| 1 | [React Fundamentals & the Render Model](react_fundamentals_and_the_render_model/react_fundamentals_and_the_render_model.md) | Everything downstream is an answer to "why did this re-render" |
| 2 | [Hooks Rules & the Hook Model](hooks_rules_and_the_hook_model/hooks_rules_and_the_hook_model.md) | The rules stop being arbitrary once you have seen the linked list |
| 3 | [Typing React Components](typing_react_components/typing_react_components.md) | The highest-frequency TypeScript-and-React interview ground |
| 4 | [Effects & Synchronization](effects_and_synchronization/effects_and_synchronization.md) | The most misused hook in React, and the catalogue of when not to use it |
| 5 | [Refs & Focus Management](refs_and_focus_management/refs_and_focus_management.md) | The escape hatch, and the accessibility obligation that comes with it |
| 6 | [Context & Component Communication](context_and_component_communication/context_and_component_communication.md) | What context costs, and the external-store answer to tearing |

### Phase 2 — Internals, Concurrency & Performance (5 modules)

| # | Module | Why it is here |
|---|--------|----------------|
| 7 | [Fiber & Reconciliation](fiber_and_reconciliation/fiber_and_reconciliation.md) | The data structure that makes interruptible rendering possible |
| 8 | [Concurrent Rendering & Transitions](concurrent_rendering_and_transitions/concurrent_rendering_and_transitions.md) | Priority, interruption, and why effects fire when they do |
| 9 | [Suspense & Error Boundaries](suspense_and_error_boundaries/suspense_and_error_boundaries.md) | Boundary placement is an architecture decision, not a detail |
| 10 | [React Performance & Profiling](react_performance_and_profiling/react_performance_and_profiling.md) | Measure first — and this is the module that teaches the measuring |
| 11 | [The React Compiler](the_react_compiler/the_react_compiler.md) | Placed **after** profiling on purpose: the profiler is how you verify the compiler helped and decide `"use no memo"` |

### Phase 3 — Data, Forms & Accessible Components (4 modules)

| # | Module | Why it is here |
|---|--------|----------------|
| 12 | [Server State & Data Fetching](server_state_and_data_fetching/server_state_and_data_fetching.md) | Most "state management" problems are cache problems in disguise |
| 13 | [Client State Management](client_state_management/client_state_management.md) | What is left once the server state has been taken away |
| 14 | [Forms & Validation](forms_and_validation/forms_and_validation.md) | The most common real screen, and the one with the most accessibility obligations |
| 15 | [Accessible Component Patterns](accessible_component_patterns/accessible_component_patterns.md) | The contracts a component library owes, and the ones it silently breaks |

### Phase 4 — Server React & Frameworks (3 modules)

| # | Module | Why it is here |
|---|--------|----------------|
| 16 | [Server Components & Server Functions](server_components_and_server_functions/server_components_and_server_functions.md) | The largest change to the React model since hooks |
| 17 | [Rendering Strategies, Hydration & SEO](rendering_strategies_hydration_and_seo/rendering_strategies_hydration_and_seo.md) | Choosing a strategy on latency *and* on the business reason SSR exists |
| 18 | [Meta-Frameworks & Routing](meta_frameworks_and_routing/meta_frameworks_and_routing.md) | Where RSC actually ships, and how to choose between them |

### Phase 5 — Application Engineering (3 modules)

| # | Module | Why it is here |
|---|--------|----------------|
| 19 | [Styling & Design Systems](styling_and_design_systems/styling_and_design_systems.md) | Including why the CSS-in-JS you know is in maintenance mode |
| 20 | [Testing & Frontend CI](testing_and_frontend_ci/testing_and_frontend_ci.md) | The test pyramid for UI, and keeping it out of the flake pit |
| 21 | [React Architecture & Antipatterns](react_architecture_and_antipatterns/react_architecture_and_antipatterns.md) | The capstone: structure, seams, and what to tell a team not to do |

**Dependencies to note:**

- **[`../frontend_platform/`](../frontend_platform/README.md) Phase 1 is a prerequisite for
  this whole section**, and [`../typescript/`](../typescript/README.md) Phase 1 for module 3
  onward. Every example here is TSX, and modules 10, 17 and 19 assume the rendering
  pipeline and the cascade.
- Module 11 (compiler) deliberately follows module 10 (profiling), not the reverse.
- Module 9 *names* request waterfalls; module 12 *solves* them. Read them in order.
- Modules 16-18 assume Phase 2 — streaming SSR is a concurrent-rendering feature before it
  is a framework feature.

---

## Learning Paths

This section is exhaustive by design. That is right for a reference and wrong for someone
two weeks from an interview, so there are **three ways through it**; the game's Study view
surfaces them as a Full / Senior / Principal toggle.

### Full Path (21 modules)

The complete curriculum in the order above — see
[5-Phase Learning Path](#3-5-phase-learning-path). Nothing is dropped.

The two curated tiers below are **different cuts, not nested depths**. Senior is the craft:
can you build it, debug it at 3am, and say why it broke. Principal is the judgment: which
approach at what cost, what the failure modes are, and what you tell a team *not* to do.
Principal is the smaller list, and roughly half of it is material Senior never sees.

<!-- study-path-table senior -->
### Senior Path (14 modules)

| # | Module | Files |
|---|--------|-------|
| 1 | [react_fundamentals_and_the_render_model](react_fundamentals_and_the_render_model/react_fundamentals_and_the_render_model.md) | module page only |
| 2 | [hooks_rules_and_the_hook_model](hooks_rules_and_the_hook_model/hooks_rules_and_the_hook_model.md) | module page only |
| 3 | [typing_react_components](typing_react_components/typing_react_components.md) | module page only |
| 4 | [effects_and_synchronization](effects_and_synchronization/effects_and_synchronization.md) | module page only |
| 5 | [refs_and_focus_management](refs_and_focus_management/refs_and_focus_management.md) | module page only |
| 6 | [context_and_component_communication](context_and_component_communication/context_and_component_communication.md) | module page only |
| 7 | [fiber_and_reconciliation](fiber_and_reconciliation/fiber_and_reconciliation.md) | module page only |
| 9 | [suspense_and_error_boundaries](suspense_and_error_boundaries/suspense_and_error_boundaries.md) | module page only |
| 10 | [react_performance_and_profiling](react_performance_and_profiling/react_performance_and_profiling.md) | module page only |
| 12 | [server_state_and_data_fetching](server_state_and_data_fetching/server_state_and_data_fetching.md) | module page only |
| 14 | [forms_and_validation](forms_and_validation/forms_and_validation.md) | module page only |
| 16 | [server_components_and_server_functions](server_components_and_server_functions/server_components_and_server_functions.md) | module page only |
| 17 | [rendering_strategies_hydration_and_seo](rendering_strategies_hydration_and_seo/rendering_strategies_hydration_and_seo.md) | module page only |
| 20 | [testing_and_frontend_ci](testing_and_frontend_ci/testing_and_frontend_ci.md) | module page only |

**Not in this path** (7 of 21, Full Path only): `concurrent_rendering_and_transitions`, `the_react_compiler`, `client_state_management`, `accessible_component_patterns`, `meta_frameworks_and_routing`, `styling_and_design_systems`, `react_architecture_and_antipatterns`
<!-- /study-path-table -->

<!-- study-path-table principal -->
### Principal Path (7 modules)

| # | Module | Files |
|---|--------|-------|
| 7 | [fiber_and_reconciliation](fiber_and_reconciliation/fiber_and_reconciliation.md) | module page only |
| 11 | [the_react_compiler](the_react_compiler/the_react_compiler.md) | module page only |
| 12 | [server_state_and_data_fetching](server_state_and_data_fetching/server_state_and_data_fetching.md) | module page only |
| 13 | [client_state_management](client_state_management/client_state_management.md) | module page only |
| 17 | [rendering_strategies_hydration_and_seo](rendering_strategies_hydration_and_seo/rendering_strategies_hydration_and_seo.md) | module page only |
| 18 | [meta_frameworks_and_routing](meta_frameworks_and_routing/meta_frameworks_and_routing.md) | module page only |
| 21 | [react_architecture_and_antipatterns](react_architecture_and_antipatterns/react_architecture_and_antipatterns.md) | module page only |

**Not in this path** (14 of 21, Full Path only): `react_fundamentals_and_the_render_model`, `hooks_rules_and_the_hook_model`, `typing_react_components`, `effects_and_synchronization`, `refs_and_focus_management`, `context_and_component_communication`, `concurrent_rendering_and_transitions`, `suspense_and_error_boundaries`, `react_performance_and_profiling`, `forms_and_validation`, `accessible_component_patterns`, `server_components_and_server_functions`, `styling_and_design_systems`, `testing_and_frontend_ci`
<!-- /study-path-table -->

---

## 4. Version Notes

**Primary target: React 19.3** (released 2026-09-09). **There is no React 20.** Latest
patches are **19.3.1 / 19.2.9 / 19.1.10 / 19.0.9** (2026-10-14). Verified 2026-10-20 — re-check
`react.dev/versions` before writing a version number into prose.

| Tag | What it gates |
|-----|---------------|
| `[19.3]` | A Server Component renders a `"use client"` context directly (`<UserContext value={...}>`), `use(browser())` for client-only subtrees, unrelated concurrent transitions render independently, StrictMode double-invokes effects on `hydrateRoot` roots |
| `[19.2]` | `<Activity>`, `useEffectEvent`, `cacheSignal`, React Performance Tracks, `useId` prefix changed to `_r_` for View Transitions compatibility |
| `[19]` | Actions and `useActionState`, `useFormStatus`, `useOptimistic`, `use()`, ref as an ordinary prop, `<Context>` as provider, document metadata hoisting, `preload`/`preinit`, stable Server Components |

**Removed in 19.0** — do not teach these as available: `propTypes` and `defaultProps` on
function components, string refs, legacy context, `ReactDOM.render`, `ReactDOM.hydrate`,
`react-test-renderer/shallow`, `findDOMNode`, the `react-dom/test-utils` helpers (`act`
moved to the `react` package, the rest throw), and the **UMD builds** (use an ESM CDN such
as esm.sh).

**React Compiler 1.0** is stable (2025-10-07), `babel-plugin-react-compiler`, with its
ESLint rules shipping inside `eslint-plugin-react-hooks`. It works back to React 17. This
changes the pedagogy: `useMemo`, `useCallback` and `React.memo` are **escape hatches**, not
the primary optimization story.

**Governance:** since **2026-02-24**, React, React Native and JSX are stewarded by the
**React Foundation** under the Linux Foundation (platinum founders include Amazon,
Callstack, Expo, Huawei, Meta, Microsoft, Software Mansion and Vercel).

**Security:** the `react-server-dom-*` packages carried **ten** separate advisories between
2025-12-03 and 2026-10-14, routinely conflated. Two are remote code execution:
**CVE-2025-55182** (CVSS 10.0, 2025-12-03) and **GHSA-xqc2-xf4c-x3h3** (CVSS 4.0 score 9.5,
2026-10-14, no CVE). The rest are source exposure (CVE-2025-55183) and denial of service.
Each patch line supersedes the last; the floor is **19.3.1 / 19.2.9 / 19.1.10 / 19.0.9**
(2026-10-14). Next.js ships its own compiled copy, so its floor is **16.4.1** (or **15.5.28**
on 15.5). Never merge the advisories into one, and never quote an older patch as safe. The
full table is in [Server Components and Server Functions](server_components_and_server_functions/server_components_and_server_functions.md).

### Ecosystem pins — verified 2026-10-20

| Area | Pin | Note |
|------|-----|------|
| Next.js | **16** (2025-10-21), current **16.4.x** (security floor 16.4.1) | Turbopack is default; Cache Components and `"use cache"` replace `experimental.ppr`/`dynamicIO`; `middleware.ts` is now **`proxy.ts`** on the Node runtime |
| React Router | **v8** (2026-06-17), current 8.5 | v7 was the Remix merge — "framework mode" is the former Remix |
| Remix v3 | **not React** | a separate framework on a Preact fork; **beta preview 2026-04-30** (the July press coverage is not the release date). Never present it as a React upgrade path |
| TanStack Start | **1.0**, stable since 2026-10-13 | Vite plus Nitro; RSC opt-in |
| Astro | **7** (2026-06-22), current 7.4 | Rust compiler, on Vite 8 |
| RSC outside Next | `@vitejs/plugin-rsc`, Waku, Parcel | a framework-agnostic Vite RSC story now exists |
| TanStack Query / Zustand | **v5** / **v5** | Zustand v5 uses native `useSyncExternalStore` |
| Tailwind CSS | **v4** | Oxide engine, CSS-first `@theme`; no `tailwind.config.js` required |
| shadcn/ui | defaults to **Base UI** since 2026-07 | Radix is not deprecated; `radix-ui` is now a single package. React Aria is also a first-class option |
| styled-components | **maintenance mode** (since Jan 2024) | its runtime injection model is fundamentally incompatible with RSC |
| MUI | **v9** (2026-04-08), current 9.5 — **still defaults to Emotion** | There is no v8: v7 jumped to v9 to re-align with MUI X. Pigment CSS is opt-in since v6 and its repo is alpha/on-hold. Do **not** write that MUI "moved to" Pigment |
| Testing | RTL **16.3.3**, Vitest **5.0.5**, Jest **30.5.2**, MSW **3.0.3**, Storybook **10** (ESM-only) | Vitest 5 (2026-09-03) needs Node 22.12+ and Vite 6.4+ |

**REPLACE, DO NOT ANNOTATE.** An answer names the current API, never "`foo()` is
deprecated, use `bar()`". The three exceptions are in root `CLAUDE.md`.

---

## 5. Case Studies

Seven front-end **system design** studies using the repository's 11-section principal
template, 900-1100 lines each. The index, learning path and dependency map live in
[case_studies/case_studies.md](case_studies/case_studies.md).

| Case Study | Primary engineering concern |
|-----------|----------------------------|
| [Design a News Feed with Infinite Scroll](case_studies/design_a_news_feed_with_infinite_scroll.md) | Cursor pagination, virtualization plus scroll restoration on back-navigation, optimistic likes, media prefetch, memory growth over a long session |
| [Design a Checkout & Payment Flow](case_studies/design_a_checkout_and_payment_flow.md) | Multi-step form state, client-supplied idempotency keys, 3DS redirect, the cross-origin card iframe and PCI scope, double-submit prevention |
| [Design a Search & Typeahead Experience](case_studies/design_a_search_and_typeahead_experience.md) | Debounce vs throttle, out-of-order response races, `AbortController`, ARIA combobox and keyboard interaction, empty and error states |
| [Design a Realtime Collaborative Editor](case_studies/design_a_realtime_collaborative_editor.md) | CRDT vs OT, optimistic local state, presence, reconnect and backoff, conflict UI |
| [Design a Dashboard with Server Components](case_studies/design_a_dashboard_with_server_components.md) | The client/server boundary in a real app, streaming a slow widget, per-widget Suspense, `"use cache"` lifetimes |
| [Design a Micro-Frontend Platform](case_studies/design_a_micro_frontend_platform.md) | Module Federation vs Multi-Zones vs iframes, shared-dependency versioning, independent deploys, runtime failure isolation |
| [Design an AI Chat Streaming UI](case_studies/design_an_ai_chat_streaming_ui.md) | Token streaming over SSE, backpressure into the DOM, optimistic user turn, cancel and regenerate, markdown-safe rendering |

**Case-study Q&As never enter the quiz bank** — `extract.py` skips every `case_studies/`
path outright. That is the rule working, not a bug.

---

## 6. Cross-Section Links

| Topic | Owner | This section's boundary |
|-------|-------|------------------------|
| The DOM, CSS, rendering pipeline, event loop | [`../frontend_platform/`](../frontend_platform/README.md) Phase 1 | prerequisite. Modules 10, 17 and 19 assume it and do not re-teach it |
| Core Web Vitals, accessibility standards, bundling | [`../frontend_platform/`](../frontend_platform/README.md) Phases 3-4 | those own the metric definitions and WCAG. Modules 10, 15 and 20 own the React-level technique |
| TypeScript the language | [`../typescript/`](../typescript/README.md) | that owns generics and conditional types; module 3 owns applying them to components |
| Micro-frontends, BFF, UI decomposition | [`../book/building_microservices/14_user_interfaces/`](../book/building_microservices/14_user_interfaces/14_user_interfaces.md) | that owns the architectural argument; module 21 owns the React-level mechanics |
| WebSocket and SSE protocol, server scaling | [`../backend/websockets_and_sse/`](../backend/websockets_and_sse/websockets_and_sse.md), [`../hld/realtime_updates/`](../hld/realtime_updates/realtime_updates.md) | those own the server; the collaborative-editor and AI-chat case studies own the client |
| REST, GraphQL and gRPC contracts | [`../backend/rest_api_design/`](../backend/rest_api_design/rest_api_design.md), [`../backend/graphql/`](../backend/graphql/graphql.md), [`../backend/grpc_and_protobuf/connect_and_browser_rpc.md`](../backend/grpc_and_protobuf/connect_and_browser_rpc.md) | those own the server contract; module 12 owns consuming it |
| CDN and asset caching | [`../hld/cdn/`](../hld/cdn/cdn.md), [`../devops/cloud_networking_and_cdn/`](../devops/cloud_networking_and_cdn/cloud_networking_and_cdn.md) | those own CDN mechanics; `frontend_platform` owns invalidation |
| LLM streaming and token economics | [`../llm/`](../llm/README.md) | that owns the model side; the AI-chat case study owns the UI |

---

## Known Gaps & Planned Work

Recorded 2026-09-04 from a gap review of all 28 files. Nothing here is a defect in what is
written; every entry is something the section does not yet cover, sized so it can be picked
up cold. Counts are measured over the 28 content files (module pages and case studies),
excluding this README and `CLAUDE.md`.

### Planned module — `view_transitions_and_animation` (Phase 2)

Sits beside `concurrent_rendering_and_transitions`. It is the one first-class React concern
with no home in the section: **`<ViewTransition>`, `addTransitionType` and
`startViewTransition` have zero mentions across all 28 files**, even though this section's
own `CLAUDE.md` cites the React 19.2 `useId` prefix change as being *"for View Transitions
compatibility"* — a topic set up and never paid off.

Also uncovered and belonging here: exit animations and why unmounting fights React; animating
a list under concurrent rendering; layout animation and FLIP inside a reconciled tree;
`useDeferredValue`-driven crossfades; and `prefers-reduced-motion` treated as a React-level
concern rather than a CSS one.

`frontend_platform/css_animation_and_motion` owns the CSS and the browser API and should keep
owning them — it is linked exactly twice from this entire section. It should not own the React
mechanics.

### Planned expansion — static generation and the `react-dom` APIs

Probably a **§6 expansion of module 17 (`rendering_strategies_hydration_and_seo`)** rather than
a new module. Module 17 teaches the strategies and never the APIs that implement them, so a
reader finishes it unable to answer "what does your framework actually call".

Measured across the 28 files: `react-dom/static` and `prerenderToNodeStream` appear **zero**
times; `renderToReadableStream` twice in one file; `hydrateRoot` and `createRoot` in two files
each. Uncovered: `prerender` and `prerenderToNodeStream`, the options bag on
`renderToPipeableStream`, and the `onRecoverableError` / `onUncaughtError` / `onCaughtError`
callbacks on `hydrateRoot` and `createRoot`.

### Open scope decision — React Native

Only 5 of the 28 files mention React Native, all in passing, yet the section twice notes that
the React Foundation stewards React **and** React Native. Two acceptable resolutions, and the
current state is neither:

- write `react_native_and_cross_platform` as a Phase 5 module, or
- state web-only scope explicitly in the README boundary table.

As it stands it reads as an omission rather than a decision.

### Planned §-level expansion — the section's main depth gap

Mean lines per section, per module page, against three peer sections:

| Template section | react | go (peer) | ml | backend | deficit |
|---|---|---|---|---|---|
| §6 How It Works | 280 | 377 | 361 | 287 | -26% vs go |
| §10 Common Pitfalls | 48 | 143 | 39 | 39 | **-66% vs go** |
| §12 Interview Q&As | 98 | 231 | 92 | 92 | **-58% vs go** |
| §14 Case Study | 71 | 167 | 192 | 61 | **-58% vs go** |
| §4 Types/Strategies | 46 | 79 | 79 | 61 | -42% |
| §8 Tradeoffs | 17 | 42 | 30 | 29 | -60% |

**Expand §10, §12, §14, §8 and §6 in that order. Do NOT expand §1, §2, §3, §7, §11 or §13** —
they are already at or above every peer section's depth, and that density is what makes this
section readable.

**Priority modules for §10 expansion** (target ~90 lines each, 3-4 broken to fixed pairs),
highest interview value first:

- **`react_fundamentals_and_the_render_model`** — §10 is 35 lines, the thinnest in the section,
  inside the module that owns the classic pitfalls. Missing as code pairs: the three
  `setCount(count + 1)` snapshot bug (asserted in §2 prose, never shown), `useState(expensive())`
  versus the lazy initializer with a measured cost, a mutating-props example, a `key` on a
  fragment, `setState` during render, and an object-literal prop defeating `memo` on 800
  children. Separately, §6.8 "Keys and list reconciliation" and §6.11 on signals have **zero
  code fences**.
- **`effects_and_synchronization`** — §10 is 36 lines with 8 pitfalls but only **one** broken to
  fixed pair. Add: the `AbortError` catch that renders an error banner on every keystroke; the
  object-in-deps infinite loop with the real `Maximum update depth exceeded` text;
  `useLayoutEffect` promotion with a measured dropped frame; the `didInit` StrictMode guard that
  hides a leak. §6.7 and §6.8 have no fences, and cleanup ordering is unteachable in prose.
- **`hooks_rules_and_the_hook_model`** — §6 and §12 here are the section's best; **§10 alone is
  the gap** at 40 lines, 8 pitfalls, 1 code pair. Add the loop-over-array hook count, a custom
  hook renamed to `getX` so the linter stops checking it, a `ref.current` write during render,
  and the two-copies-of-React `npm ls react` transcript.
- **`concurrent_rendering_and_transitions`** — §6 is 216 lines, the thinnest §6. §6.1 has one
  fence and is the module's physical foundation: it needs the `MessageChannel` macrotask handoff,
  the 5 ms `shouldYield()` check, and a frame-budget diagram. Transition **entanglement** is set
  up by `fiber_and_reconciliation` §6.8 and never paid off.

**Also recorded, module by module:**

- `fiber_and_reconciliation` has the section's thinnest §9 (17 lines) and §11 (14 lines, fewest
  tools named — it should list React DevTools fiber inspection, `react-devtools-core`, the
  Performance Tracks scheduler row, `why-did-you-render`, `react-scan`, and
  `bippy` / `__REACT_DEVTOOLS_GLOBAL_HOOK__`). It compresses `completeWork` and the effect-flag
  bitmask into one subsection, and never explains the React 18 removal of the effect list — a
  top-tier internals question. It also has only **1 outbound sibling link**, the fewest in the
  section, in the module every Phase 2 module depends on.
- `the_react_compiler` §6 never covers HIR, SSA, mutability aliasing or reactive-scope inference
  despite its own §1 promising them, and it has no bail-out catalogue.
- `react_performance_and_profiling` §6.2 (Performance Tracks, a `[19.2]` selling point) has
  **zero code fences**, and the `<Profiler>` `onRender` argument list is never given in full.
- `typing_react_components` §14 is 44 lines, the thinnest case study in the section.

### Q&A parity

Seven modules sit at 16 Q&As against this README's own 18 target: `refs_and_focus_management`,
`context_and_component_communication`, `client_state_management`, `accessible_component_patterns`,
`styling_and_design_systems`, `testing_and_frontend_ci`, `react_architecture_and_antipatterns`.
All clear the repo-wide 15 floor, so this is parity work, not a build failure.

### Planned eighth case study (optional)

**Design a Data-Grid / Spreadsheet at 100k Cells** — the one front-end system-design prompt with
no home here, and the natural capstone: virtualization, `useSyncExternalStore`, concurrent
rendering, keyboard accessibility, clipboard and paste, and undo. It sits beside `news_feed`
under Concern 1.

### Planned cross-links

`typescript/` is linked from only 3 of 21 modules despite being a declared Phase 1 prerequisite,
and seven `frontend_platform/` modules this section leans on have zero inbound links.

| Missing link | Where it belongs |
|---|---|
| `frontend_platform/the_event_loop_and_scheduling` | `concurrent_rendering_and_transitions` §1, §6.1 — the most glaring gap; that module explains `MessageChannel`, 5 ms yields and frame budgets from first principles |
| `frontend_platform/client_memory_and_performance_debugging` | `react_performance_and_profiling` §6, `refs_and_focus_management` §7, `case_studies/news_feed` |
| `typescript/unions_narrowing_and_exhaustiveness` | `typing_react_components` §6, §13 |
| `typescript/conditional_and_mapped_types` | `typing_react_components` §6 (polymorphic `as`) |
| `typescript/runtime_validation_and_parse_dont_validate` | `forms_and_validation` §6.3, `server_components_and_server_functions` §13 |
| `frontend_platform/linting_formatting_and_code_quality` | `hooks_rules_and_the_hook_model` §6.10, `the_react_compiler` §13 |
| `frontend_platform/the_node_and_edge_runtimes` | `meta_frameworks_and_routing` §6.6, §8 |
| `frontend_platform/auth_in_the_browser` | `server_components_and_server_functions` §6.7, `case_studies/checkout` |
| `frontend_platform/css_animation_and_motion` | `styling_and_design_systems`, `suspense_and_error_boundaries` — linked twice section-wide |

---

## 7. Build Manifest

Per-file status tracker. **This is the resume point** — the first `pending` row is the next
unit of work. Flip `pending` to `done` in the same commit as the file, filling in the
measured counts; a `done` row with no counts is not trustworthy.

Target per module page: **700-900 lines, 6-8 Mermaid fences, 15-18 Q&As** (every one
carrying an authored `**Short:**` line), all 14 template sections. Case studies: **900-1100
lines**, 11-section principal template, 10-16 Q&As.

| # | File | Phase | Status | Q&As | Lines |
|---|------|-------|--------|-----:|------:|
| 1 | `react_fundamentals_and_the_render_model/react_fundamentals_and_the_render_model.md` | 1 | **done** | 18 | 744 |
| 2 | `hooks_rules_and_the_hook_model/hooks_rules_and_the_hook_model.md` | 1 | **done** | 18 | 807 |
| 3 | `typing_react_components/typing_react_components.md` | 1 | **done** | 18 | 749 |
| 4 | `effects_and_synchronization/effects_and_synchronization.md` | 1 | **done** | 18 | 753 |
| 5 | `refs_and_focus_management/refs_and_focus_management.md` | 1 | **done** | 16 | 823 |
| 6 | `context_and_component_communication/context_and_component_communication.md` | 1 | **done** | 16 | 785 |
| 7 | `fiber_and_reconciliation/fiber_and_reconciliation.md` | 2 | **done** | 18 | 850 |
| 8 | `concurrent_rendering_and_transitions/concurrent_rendering_and_transitions.md` | 2 | **done** | 18 | 751 |
| 9 | `suspense_and_error_boundaries/suspense_and_error_boundaries.md` | 2 | **done** | 18 | 774 |
| 10 | `react_performance_and_profiling/react_performance_and_profiling.md` | 2 | **done** | 18 | 796 |
| 11 | `the_react_compiler/the_react_compiler.md` | 2 | **done** | 18 | 756 |
| 12 | `server_state_and_data_fetching/server_state_and_data_fetching.md` | 3 | **done** | 18 | 788 |
| 13 | `client_state_management/client_state_management.md` | 3 | **done** | 16 | 755 |
| 14 | `forms_and_validation/forms_and_validation.md` | 3 | **done** | 18 | 880 |
| 15 | `accessible_component_patterns/accessible_component_patterns.md` | 3 | **done** | 16 | 880 |
| 16 | `server_components_and_server_functions/server_components_and_server_functions.md` | 4 | **done** | 18 | 804 |
| 17 | `rendering_strategies_hydration_and_seo/rendering_strategies_hydration_and_seo.md` | 4 | **done** | 18 | 782 |
| 18 | `meta_frameworks_and_routing/meta_frameworks_and_routing.md` | 4 | **done** | 18 | 816 |
| 19 | `styling_and_design_systems/styling_and_design_systems.md` | 5 | **done** | 16 | 788 |
| 20 | `testing_and_frontend_ci/testing_and_frontend_ci.md` | 5 | **done** | 16 | 814 |
| 21 | `react_architecture_and_antipatterns/react_architecture_and_antipatterns.md` | 5 | **done** | 16 | 750 |
| C0 | `case_studies/case_studies.md` (index) | CS | **done** | n/a | 123 |
| C1 | `case_studies/design_a_news_feed_with_infinite_scroll.md` | CS | **done** | 14 | 929 |
| C2 | `case_studies/design_a_checkout_and_payment_flow.md` | CS | **done** | 14 | 937 |
| C3 | `case_studies/design_a_search_and_typeahead_experience.md` | CS | **done** | 14 | 919 |
| C4 | `case_studies/design_a_realtime_collaborative_editor.md` | CS | **done** | 14 | 923 |
| C5 | `case_studies/design_a_dashboard_with_server_components.md` | CS | **done** | 16 | 1010 |
| C6 | `case_studies/design_a_micro_frontend_platform.md` | CS | **done** | 15 | 901 |
| C7 | `case_studies/design_an_ai_chat_streaming_ui.md` | CS | **done** | 14 | 974 |

### Conventions reminder (for future batch agents)

- The page is named for its folder: `<module>/<module>.md`.
- **Never** name a file `*_deep_dive.md` or `*_internals.md` — FATAL under `--strict`.
  `fiber_and_reconciliation.md` and `the_react_compiler.md` are named around this rule.
- Q&As go under `## 12. Interview Questions with Answers`, each with a `**Short:**` line of
  15-220 characters as the first non-blank line of the answer.
- Tag code fences ` ```tsx ` / ` ```ts `, not ` ```typescript `.
- Say **"client hydration"**, never bare "hydration" — the word already means feed-object
  hydration in 14 files across `backend/` and `hld/`, and the MCQ distractor pool will mix
  them.
- In a `## 11. Technologies & Tools` table, the first cell is one bare product name. Never
  an unspaced `Next.js/Remix` — that becomes a single garbled record. Use two rows.
- Adding a file means adding its line to the `<!-- study-paths -->` block below **in the
  same commit**, then running `python3 game/extract.py --write-paths`. Case studies go
  under a `case_studies` pseudo-module in that block.

<!-- study-paths
     THE source of truth for this section's file inventory and study-tier membership.
     Every module, every file it owns, and every case study is listed here with the tiers
     it belongs to; `-` means Full path only. Content files carry NO structural metadata.
     Order is never declared here: it comes from STUDY_ORDER in game/app.js. The tier
     TABLES in this file are generated from this block by
     `python3 game/extract.py --write-paths`. A file on disk missing from this block, or
     listed here and absent from disk, FAILS `extract.py --strict`.

     Entries are added AS EACH FILE LANDS -- listing a not-yet-written file is fatal.

react_fundamentals_and_the_render_model
  react_fundamentals_and_the_render_model.md  senior
hooks_rules_and_the_hook_model
  hooks_rules_and_the_hook_model.md  senior
typing_react_components
  typing_react_components.md  senior
refs_and_focus_management
  refs_and_focus_management.md  senior
context_and_component_communication
  context_and_component_communication.md  senior
effects_and_synchronization
  effects_and_synchronization.md  senior
fiber_and_reconciliation
  fiber_and_reconciliation.md  senior, principal
concurrent_rendering_and_transitions
  concurrent_rendering_and_transitions.md  -
suspense_and_error_boundaries
  suspense_and_error_boundaries.md  senior
react_performance_and_profiling
  react_performance_and_profiling.md  senior
server_state_and_data_fetching
  server_state_and_data_fetching.md  senior, principal
the_react_compiler
  the_react_compiler.md  principal
client_state_management
  client_state_management.md  principal
server_components_and_server_functions
  server_components_and_server_functions.md  senior
accessible_component_patterns
  accessible_component_patterns.md  -
forms_and_validation
  forms_and_validation.md  senior
rendering_strategies_hydration_and_seo
  rendering_strategies_hydration_and_seo.md  senior, principal
meta_frameworks_and_routing
  meta_frameworks_and_routing.md  principal
styling_and_design_systems
  styling_and_design_systems.md  -
testing_and_frontend_ci
  testing_and_frontend_ci.md  senior
react_architecture_and_antipatterns
  react_architecture_and_antipatterns.md  principal
case_studies
  design_a_micro_frontend_platform.md  principal
  design_a_dashboard_with_server_components.md  senior, principal
  design_a_realtime_collaborative_editor.md  senior, principal
  design_a_checkout_and_payment_flow.md  senior, principal
  design_an_ai_chat_streaming_ui.md  senior, principal
  design_a_news_feed_with_infinite_scroll.md  senior, principal
  design_a_search_and_typeahead_experience.md  senior, principal
-->
