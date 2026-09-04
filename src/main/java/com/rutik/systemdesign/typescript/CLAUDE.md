# TypeScript Section — CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/typescript/`
Global conventions (formatting, templates, Q&A rules): see root `CLAUDE.md`.
Build manifest / status tracker: `README.md` §8 — check it before starting a new module.

---

## Why this section exists

The repository claimed to be a one-stop destination for AI plus full-stack engineering
while containing **no TypeScript language module at all** — `npm` appeared only as a
Dockerfile layer-caching example, and the only TypeScript-first page in 1,248 content files
was an LLM agent-framework page. This section is the language half of a three-section
front-end track:

- **`typescript/`** (here) — the language and its type system
- **`../frontend_platform/`** — the browser, the network from the client, runtimes, build
- **`../react/`** — the library, Server Components, and front-end system design

The split is deliberate and enforced. Material that is *JavaScript* rather than
*TypeScript* — prototypes, closures, `this`, `Proxy`/`Reflect`, the event loop — lives in
`frontend_platform/javascript_language_semantics/`. Toolchain material — ESLint, Prettier,
bundlers, pnpm, Turborepo — lives in `frontend_platform/` Phase 3. Both were moved out
because keeping them here would have meant three modules across two sections teaching
overlapping runtime semantics.

---

## PRIMARY TARGET VERSION — TypeScript 7.0

GA **2026-07-08**. The compiler is a native Go binary (internally "Project Corsa"): 7.7-11.9x
faster full builds, memory down 6-26%, `--checkers` / `--builders` / `--singleThreaded`
controlling parallelism. Releases now land **every 3-4 months**, so this pin goes stale
several times a year — verify against the TypeScript devblog before writing anything
version-gated, never from memory.

**TypeScript 6.0** (2026-03-23) was the last JavaScript-codebase release and an explicit
deprecation bridge. Get this distinction right, because it is the easiest thing in the
section to state wrongly:

- **Removed (hard error, the build stops):** `--moduleResolution classic`, `--outFile`,
  AMD/UMD/SystemJS module emit
- **Deprecated (warns, still compiles exactly as before):** `esModuleInterop: false` and
  `allowSyntheticDefaultImports: false`, `--baseUrl` as a resolution root,
  `--alwaysStrict false`, `--moduleResolution node` (i.e. `node10`), `target: es5`,
  `--downlevelIteration`

The deprecated list is the long one, and that is the part most sources get wrong: 6.0 is a
bridge, so almost everything it disapproves of still builds. Only three things actually
fail. Do not write "removed" for anything in the second list.

**The practical TS 7.0 caveat.** There is no stable programmatic Compiler API until 7.1.
typescript-eslint (8.69.0), ts-jest, ts-morph and framework template checkers still run on
`typescript@npm:@typescript/typescript6` (binary `tsc6`). Any module that discusses tooling
must say this; it is the single most consequential fact for a team on TS 7 today.

| Tag | What it gates |
|-----|---------------|
| `[7.0]` | Native Go compiler; `--checkers` / `--builders` / `--singleThreaded` |
| `[6.0]` | New strict defaults; ES2025 target; `Map`/`WeakMap` `getOrInsert`; Temporal types |
| `[5.8]` | `--erasableSyntaxOnly` |
| `[5.5]` | Inferred type predicates; `isolatedDeclarations` |
| `[5.4]` | `NoInfer` |
| `[5.2]` | `using` / `await using` |
| `[5.0]` | Stage-3-shaped decorators; `const` type parameters; `--moduleResolution bundler` |
| `[4.9]` | `satisfies` |

Version tags are **sparse** here by design — tag a feature where its availability is the
point, not on every mention.

**REPLACE, DO NOT ANNOTATE.** Never write "`foo()` is deprecated, use `bar()`" — write
`bar()`. The three exceptions live in root `CLAUDE.md`.

### Spec facts this section will get asked about

Check `github.com/tc39/proposals` before repeating any of these; several are commonly
stated wrongly in blog posts.

- **Decorators are at Stage 2.7, not Stage 3.** They reached Stage 3 in 2022; TC39 later
  inserted 2.7 as an intermediate checkpoint and re-slotted existing proposals into it.
  Write it as a **process change, not a rejection on the merits** — and never write
  "Stage 3", which most sources still say.
- **Temporal is Stage 4 and shipped in ES2026**, but MDN marks it "Limited availability —
  not Baseline". Do not write that it ships everywhere.
- **Records & Tuples were withdrawn**; the repo was archived 2025-04-15.
- **The Type Annotations proposal is Stage 1 and dormant** — its own README says it is "not
  updated regularly".
- **ECMAScript 2026 was ratified 2026-06-30** (Ecma 131st GA, ECMA-262 17th edition).

---

## Module List — 16 Modules (4 Phases)

| # | Module Directory | Phase | Key Concepts |
|---|-----------------|-------|-------------|
| 1 | `typescript_fundamentals_and_tsconfig/` | 1 — Type System Core | Erasure vs emit, `tsconfig` anatomy, the TS 6.0 strict defaults, `strict` flag-by-flag |
| 2 | `structural_typing_and_assignability/` | 1 | Structural vs nominal, assignability, excess property checks and freshness, widening, `satisfies` `[4.9]` |
| 3 | `unions_narrowing_and_exhaustiveness/` | 1 | Discriminated unions, control-flow analysis, predicates, inferred predicates `[5.5]`, `unknown`/`any`/`never` |
| 4 | `generics_and_type_parameters/` | 1 | Constraints, inference sites, `const` type params, `NoInfer` `[5.4]`, overload resolution, variance, `strictFunctionTypes` |
| 5 | `conditional_and_mapped_types/` | 1 | `infer`, distributive conditionals, mapped modifiers, key remapping, utility types by implementation, recursion depth |
| 6 | `template_literal_types_and_type_level_programming/` | 1 | Template literal types, tuple manipulation, type-level arithmetic, when to stop |
| 7 | `classes_decorators_and_resource_management/` | 2 — The Runtime Boundary | Class typing, `this` types, decorators `[5.0]` vs `experimentalDecorators`, enums vs unions, `using` `[5.2]` |
| 8 | `modules_resolution_and_package_exports/` | 2 | ESM vs CJS, `moduleResolution`, `exports` maps, the dual-package hazard, `verbatimModuleSyntax`, `arethetypeswrong` |
| 9 | `declaration_files_and_library_authoring/` | 2 | `.d.ts`, declaration merging, module augmentation, `isolatedDeclarations` `[5.5]`, versioning a type surface |
| 10 | `runtime_validation_and_parse_dont_validate/` | 2 | The type/runtime gap, Zod v4, Standard Schema v1, branded types, trust boundaries |
| 11 | `typescript_at_runtime_node_deno_bun/` | 2 | Node type stripping, `--erasableSyntaxOnly` `[5.8]`, Deno's type-check, Bun's strip-only |
| 12 | `the_typescript_compiler_and_type_performance/` | 3 — Compiler, Scale & Server | The pipeline, the TS 7 Go port, `--checkers`, union blowup, `skipLibCheck`, `--generateTrace` |
| 13 | `typescript_for_node_and_backend_services/` | 3 | Typed HTTP servers, tRPC and OpenAPI codegen, typed env, error types across the wire |
| 14 | `typescript_in_large_codebases/` | 3 | Project references, composite builds, enforced module boundaries, incremental CI, JS to TS migration |
| 15 | `testing_typed_code/` | 4 — Practice & Judgment | `expectTypeOf`, `tsd`, `@ts-expect-error` as an assertion, typing mocks, testing inference |
| 16 | `typescript_patterns_and_antipatterns/` | 4 | Branded types over `as`, containing `any`, HKT workarounds, over-abstraction, when to stop |

## Sub-Files (0)

None. A sub-file exists only to carry a **different study tier from its parent page** —
that is the one thing a sub-file expresses that a longer module page cannot. None is
justified at this depth.

**If you add one, never name it `*_deep_dive.md` or `*_internals.md`.** `DEEP_NAME_RE` in
`game/build_tech.py` makes any such page a FATAL `--strict` error unless it is hand-listed
in `PRODUCT_DEEP_DIVES` / `CONCEPT_DEEP_DIVES`.

## Case Studies — 0, and that is a GAP, not a policy

There is no `typescript/case_studies/` yet. It is unwritten work, not an exclusion.

Do not repeat the old justification ("a pure-language section, following the `../python/`
precedent") — it was false in both halves. `../python/case_studies/` holds **six** studies
plus an index, and `../go/case_studies/` holds six plus an index; both are pure-language
sections, and their indexes frame the studies as language/runtime work that "stops at the
process boundary". That is exactly the shape this section needs.

The six proposed studies, with what each teaches that no module does, are recorded in
`README.md` §"Known Gaps & Planned Work". Read that before starting one.

---

## Learning Paths (Full + Senior + Principal)

`README.md` documents the **Full Path** (all 16 modules, the "4-Phase Learning Path") plus
two curated tiers. They are **different cuts, not nested depths** — senior is the craft
(can you build it, debug it, say why it broke), principal is the judgment (which approach
at what cost, what you tell a team *not* to do). Principal is the smaller list, and roughly
half of it is material senior never sees.

Membership is declared ONCE, in the `<!-- study-paths -->` block in this section's
`README.md`, naming every module, every file it owns, and the tiers each file takes.
Listing a tier joins it; omitting it opts out; the module page must always be listed and
must carry every tier the module is in. **Order is never declared** — it comes from
`STUDY_ORDER.typescript` in `game/app.js`, so a tier is an ordered subset by construction.
**There is no path array in `app.js` to edit**: `extract.py` walks the markers and emits the
gitignored `questions/paths.json`, which the game fetches at boot.

The tier tables in `README.md` sit between `<!-- study-path-table <tier> -->` markers and
are **generated** — regenerate with `python3 game/extract.py --write-paths`. A hand-edited
or stale block fails `--strict` and takes the Pages deploy red. **`--write-paths` only
fills marker pairs that already exist**; paste the empty pair by hand once, at the
editorial position you want.

Intended tier shape once modules land — senior is the craft of using the type system,
principal is the judgment about what it costs:

- **Senior:** 1, 2, 3, 4, 5, 7, 8, 10, 11, 15
- **Principal:** 8, 9, 12, 13, 14, 16

---

## Q&A Minimums

- **15+** per module page — the hard floor from root `CLAUDE.md`, not a target
- **18+** for the high-frequency modules: `structural_typing_and_assignability`,
  `generics_and_type_parameters`, `conditional_and_mapped_types`,
  `modules_resolution_and_package_exports`,
  `the_typescript_compiler_and_type_performance`
- Order by interview frequency: **gotchas and traps first**, then internals, then edge cases
- Every answer opens with its `**Short:**` line — 15-220 characters, self-contained, no code
  fence, no list. `--strict` fails the build outside that range
- **Any pass that edits an answer MUST update the `**Short:**` above it in the same edit**,
  or a stale summary ships as the correct MCQ option

**A distractor warning specific to this section.** TypeScript Q&A answers cluster hard on
the words "type", "infer", "generic" and "assignable". Distractors are drawn from sibling
answers' first sentences and ranked by IDF, so near-identical openers produce a question
where all four options read the same. **Keep first sentences lexically distinct.**

---

## Code Style in This Section

- Fences are tagged ` ```ts ` and ` ```tsx `, **never** ` ```typescript ` — the reader's
  `LANG_ALIAS` map in `app.js` aliases `ts`/`tsx`/`jsx` to JavaScript highlighting but not
  the long form, so ` ```typescript ` renders unhighlighted. No `--strict` check catches it.
- Every non-trivial example type-checks under `strict`, or is explicitly marked a fragment.
- Show the compiler's actual error text when the error *is* the lesson — `Type 'X' is not
  assignable to type 'Y'` with the real elaboration, not a paraphrase.
- At least one **`// BROKEN` → `// FIX`** pair in §10 and one in §14 of every module.
- Concrete numbers everywhere. Never "a few", "some", "significant".
- `X | undefined`, never `Optional<X>`-style paraphrase; modern syntax throughout.

---

## Boundary with the neighbouring sections — who owns what

| Topic | Owner | Rule |
|-------|-------|------|
| Prototypes, closures, `this`, `Proxy`/`Reflect`, coercion | `frontend_platform/javascript_language_semantics/` | Do not re-teach. Cross-link |
| Event loop, microtasks | `frontend_platform/the_event_loop_and_scheduling/` | Module 11 may reference it; it does not explain it |
| ESLint, Prettier, Biome, bundlers, pnpm, Turborepo, Nx | `frontend_platform/` Phase 3 | Only typescript-eslint's *type-aware rule cost* is discussed here, inside module 12 |
| Typing React components, hooks, props | `react/typing_react_components/` | React-specific typing is React's |
| Pydantic, "parse don't validate" in Python | `fastapi/pydantic_v2_deep_dive/` | Module 10 states the parallel and cross-links; it does not re-derive the idea |
| REST/GraphQL/gRPC contract design | `backend/api_contracts_and_schema_governance/`, `backend/rest_api_design/` | Those own the contract; module 13 owns typing the TypeScript side |
| Test runners, RTL, Playwright, MSW | `react/testing_and_frontend_ci/` | Module 15 owns type-level assertions only; it does not re-teach Vitest |

---

## Section §11 stays `## 11. Technologies & Tools`

Unlike `soft_skills/`, this section **wants** to feed the technology index. Do not retitle
§11. Per `technologies/CLAUDE.md`, a §11 row's first cell must be a bare product name,
under 42 characters, at most 5 spaces. A **spaced** ` / ` is split correctly into two
records by `NAME_SPLIT_RE`; an **unspaced** slash (`Vite/Rolldown`) is kept as one literal
name — that form becomes a single garbled record. Use two rows.

Almost every tool this section names is **new** to the bank — React, Vite, TypeScript,
Zod, tRPC, ESLint, Prettier, Biome, pnpm, esbuild and Rollup are not currently banked at
all — so expect new records rather than merges. One live collision: **`yarn` in the bank is
already YaRN**, the RoPE rescaling method (`tech_bank/llm-apps.md`, `Kind: concept`). A
bare "Yarn" citation key-collides with it and needs a `TECH_HOMONYMS` entry keyed by
section.

---

## Diagram Traps

Diagrams are **appeal-first** (root `CLAUDE.md`): pick the form whose topology matches the
concept. Mermaid is the default — `flowchart LR` for directed flows, `stateDiagram-v2` for
lifecycles, `xychart-beta` for magnitudes. Run `/mermaid-diagrams` before authoring one,
colour every node with the One-Dark `classDef` palette, keep labels short (`<br/>`), use
rounded shapes.

**A fence that does not parse FAILS THE BUILD** — `scripts/build_diagrams.mjs` exits 1 and
takes both Pages and the APK red. These parse failures are silent in review, because the
source reads perfectly:

| Trap | Breaks | Fix |
|------|--------|-----|
| `;` in a `sequenceDiagram` `Note` or message body | statement separator cuts the text | use `,` or `.` |
| `call` as a node id | reserved (`click X call fn()`) | rename |
| Extra spaces **after** an edge label's closing `\|` | flowchart parse error | single space |
| `\"` inside a node label | no backslash escape exists | `#quot;` |
| A generic in a label — `Array<T>` | `<` starts a tag with `htmlLabels: false` | quote the label |
| `:` or `()` in a `quadrant-N` label | `:` is the data separator | quote it |

**Validate anything you touch:** `node scripts/check_render.mjs <absolute-file.md>`
(exit 0 = every fence renders).

**ASCII remains** for constraint grids, alignment-critical layout maps, and vector
geometry — for this section, a type-lattice or assignability grid is genuinely better as
ASCII. Use `/visual-intuition-diagrams`; conventions (no tabs, no emojis, widest line
<= 100 cols, caption every diagram) are in root `CLAUDE.md`.

---

## Adding a New TypeScript Module

1. Create `<module_name>/<module_name>.md` — the 14-section template. The page is named for
   its folder; a `README.md` here is invisible to `extract.py`
2. Target TypeScript 7.0; verify anything version-gated against the devblog before writing it
3. Meet the Q&A minimum above, every answer opening with its `**Short:**` line
4. Add the module dir to `STUDY_ORDER.typescript` in `game/app.js` at its phase position — a
   module missing from it falls to the 9999 sort (dead-last in Study) and fails `--strict`
5. Add the module and EVERY file it owns to the `<!-- study-paths -->` block in this
   section's `README.md`, tagging each file with its tiers (`-` for Full path only; the
   module page must carry every tier the module is in). **Do NOT put a block in the module
   page — content files hold only content.** Then run
   `python3 game/extract.py --write-paths` to regenerate the README's tier tables
6. Flip the module's Status from `pending` to `done` in `README.md` §8, filling in the
   measured Q&A and line counts
7. Update this file's Module List table and `README.md` §2 Module Table + §3 phase table
8. Update root `README.md` phase table under the TypeScript section
9. Update root `CLAUDE.md` TypeScript section row

---

## HARD RULE — structure lives in `README.md`, content files hold only content

**The section `README.md` is the single source of truth for this section's file inventory
and study-tier membership.** Its `<!-- study-paths -->` block lists EVERY module, EVERY file
that module owns (the module page AND every deep-dive sub-file), each tagged with the tiers
it belongs to — `-` means Full path only. Reading that one block tells you every file in the
section and which paths it is on.

**A content file carries NO structural metadata.** A module page or a deep-dive sub-file
holds the content of its topic and nothing else — no `<!-- study-paths -->` block, no tier
declaration, no path membership.

**Adding a file? Add its line to the section README's block in the same commit.** A file on
disk that is missing from the block — or listed there and absent from disk — FAILS
`python3 game/extract.py --strict` and takes the Pages deploy red. Both directions are
fatal, which is why the block is grown file-by-file rather than pre-populated.

Order is never declared in the block: it comes from `STUDY_ORDER` in `game/app.js`. The
tier TABLES in the README are GENERATED from the block by
`python3 game/extract.py --write-paths` — never hand-edit them.
