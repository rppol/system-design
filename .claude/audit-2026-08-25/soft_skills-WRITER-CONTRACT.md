# `soft_skills` writer contract — READ ALL OF IT BEFORE WRITING

You are writing ONE Markdown file for a serious engineering study repository. Not a blog
post. Not LinkedIn. The house style is **concrete specificity**: named companies, named
books with authors and years, real numbers, verbatim scripts and transcripts.

**The single biggest failure mode for this section is generic advice.** "Communicate
clearly." "Build trust." "Be a good listener." If a sentence would survive being pasted
into any article on the internet, delete it and replace it with something only someone who
has actually done the job could write.

---

## 1. File shape — the 14-section template, EXACT heading numbers

Copy these headings verbatim, including the numbers and the titles:

```
# <Module Title>

## 1. Concept Overview
## 2. Intuition
## 3. Core Principles
## 4. Types / Levels / Modes
## 5. Models & Frameworks Diagrammed
## 6. How It Works — Detailed Mechanics
## 7. Real-World Examples
## 8. Tradeoffs
## 9. When to Use / When NOT to Use
## 10. Common Pitfalls
## 11. Frameworks, Books & Further Reading
## 12. Interview Questions with Answers
## 13. Best Practices
## 14. Case Study
```

Rules on the headings:

- `## 12. Interview Questions with Answers` is **LOAD-BEARING and must be byte-exact**.
  The build matches `^##\s+.*interview\s+q`. "Interview Tips" yields ZERO questions,
  silently, and the module vanishes from the quiz.
- **§11 must NEVER contain both a `technolog*` word and the word `tool`/`tools`.** The
  build scrapes every `## N. Technologies & Tools` heading repo-wide into the game's
  technology index; a soft-skills §11 titled that way would inject RACI and Radical
  Candor into a list of software products. The retitle is the fix — do not "restore" it.
- Put a `---` horizontal rule between every top-level section.
- `## 2. Intuition` opens with a `> **One-line analogy**: …` blockquote, then
  **Mental model**, **Why it matters**, **Key insight** paragraphs. Match the repo style.

## 2. The Q&A contract — this is where builds break

Under `## 12. Interview Questions with Answers`, **15 minimum** (aim 16-18), ordered by
interview frequency: the gotchas and traps interviewers actually open with first, then
mechanics, then edge cases.

Each one looks EXACTLY like this:

```
**Q: How do you disagree with a decision your manager has already committed to?**
**Short:** Disagree in private with a concrete alternative and a cost, then commit publicly and say out loud that you are committing.

Raise it privately first, and bring the alternative rather than the objection — "I think
we should do X instead, here is what it costs and what it buys" beats "I don't think Y
works". … [3-6 more sentences: mechanism, a named example, the gotcha, then practical
guidance]
```

Hard rules, each of which fails the build or silently deletes content:

1. **Every question line starts with `**` at column zero** and is `**Q: <question>?**`.
2. **NEVER number Q&As as a Markdown list.** `1. **"question?"**` does not start with
   `**`, so the parser never sees a question boundary and the ENTIRE section is silently
   lost. This has cost this repo hundreds of questions before.
3. **Every Q&A has a `**Short:**` line as the FIRST non-blank line of its answer.**
   - 15 to 220 characters. Outside that range **fails the build**. Count them.
   - One self-contained sentence that answers the question with no surrounding context —
     it is shown as a bare multiple-choice option among three distractors.
   - No code fence, no list, no "see below", no restating the question.
   - Anywhere but the first line and it is treated as ordinary prose.
4. The full answer below it must still stand alone: first sentence = direct answer, then
   mechanism/example/gotcha, last sentence = practical guidance. Never write "as the
   Short says".
5. Distractors are built from OTHER answers' first sentences in the same module, so keep
   every `**Short:**` line crisp and distinct from its siblings.

## 3. The `Weak version -> Strong version` requirement

This section's equivalent of the repo's "show broken code, then the fix". **Every module
needs at least two**, and more is better. Format:

```
**Weak version**

> We should probably think about maybe improving the reliability of the ingest service at
> some point, since there have been some issues.

**Strong version**

> Ingest dropped 0.4% of events last month, which is 1.2M records and three customer
> escalations. Two engineers for three weeks fixes it. I need a decision by Friday to make
> the Q3 cut.

**What changed:** a number, a named cost, a named ask, and a date. The weak version can be
agreed with and ignored; the strong one cannot.
```

Put these in §6 and §10 primarily. They are the most valuable content in the file.

## 4. Diagrams — Mermaid, 2 or 3 per module, EVERY fence must parse

At least two ```` ```mermaid ```` fences (§5 owns most of them; one elsewhere is good).

- **Colour EVERY node** with a One-Dark `classDef` palette. Use these:
  ```
  classDef good   fill:#98c379,stroke:#27ae60,color:#1a1a1a
  classDef bad    fill:#e06c75,stroke:#c0392b,color:#fff
  classDef info   fill:#61afef,stroke:#2e86c1,color:#1a1a1a
  classDef warn   fill:#e5c07b,stroke:#f39c12,color:#1a1a1a
  classDef accent fill:#c678dd,stroke:#9b59b6,color:#fff
  classDef calm   fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
  ```
  and assign every node with `class a,b,c info` lines. An uncoloured node gets an
  arbitrary auto-tint that can collide with your authored colours.
- **`flowchart LR` by default.** `TD`/`TB` only for a genuinely vertical hierarchy or
  lifecycle. `stateDiagram-v2` for lifecycles, `sequenceDiagram` for actor chains,
  `quadrantChart` for two-axis models (Radical Candor, Eisenhower), `xychart-beta` for
  magnitudes, `timeline` for evolution.
- **Rounded shapes**: `n(Label)` or `n([Label])`, never bare `n[Label]` where avoidable.
- **Short labels**, wrap with `<br/>`.
- **Caption every diagram** with one or two sentences tying it to the insight.

**Parse traps that silently break the build — all of these have happened here:**

| Trap | Why |
|------|-----|
| a `;` anywhere in a sequenceDiagram `Note` or message | `;` is a statement separator; the rest re-parses as a new statement. Use `,` or `.` |
| `call` as a bare node id | reserved word. Rename to `doCall` / `entry` |
| extra spaces after an edge label's closing pipe | `a -->\|"x"\|   b` fails. Exactly one space |
| `\"` inside a node label | no backslash escape exists. Use `#quot;` |
| `:` or `()` in a `quadrant-N` label | quote the whole label: `quadrant-1 "Ideal: fast"` |
| a quadrantChart point without brackets | `"P": [0.2, 0.8]`, never `"P": 0.2, 0.8` |
| a quadrant coordinate of exactly `1.0` | must be `< 1.0`; use `0.98` |
| padding spaces to line up a column of edges | Mermaid is not ASCII art. Do not pad |

Also: never hand-set a light background — the reader surface is pitch black.

## 5. Content rules

- **No emojis, anywhere.** Use the plain check and cross marks if you need them, never
  the emoji ones.
- Tables in standard Markdown pipe syntax; code/script blocks in triple backticks with a
  language tag (```text``` is fine for a transcript).
- **Name real things.** Books with author and year. Companies by name. Frameworks by
  their exact published names — get quadrant names, level names and acronym expansions
  RIGHT rather than approximately. If you are not sure a framework has four named
  quadrants, do not invent their names; describe the axes instead.
- **Real numbers.** A specific figure beats "significant". Where you cite a study or a
  survey, name it and give the number.
- **Verbatim scripts.** When the module is about a conversation, write the actual words —
  a transcript with speaker labels beats a description of the conversation.
- §7 Real-World Examples must name actual companies and actual practices.
- §10 Common Pitfalls must be war stories with consequences, not warnings.
- §14 Case Study is a compact worked scenario (60-120 lines) inside the module page — it
  is NOT one of the separate `case_studies/` files, and its Q&As, if any, live under §14
  and are correctly excluded from the quiz. Do not add a Q&A heading inside §14.
- Cross-link other modules in this section with relative paths to the PAGE, e.g.
  `[Executive Communication](../executive_communication/executive_communication.md)` —
  never to a bare directory. Only link files you are told exist.

## 6. Length

**700-1100 lines.** Under 700 means you were vague. Over 1100 means you padded.

## 7. Honesty

If you do not know a fact — a level name, a quadrant name, a study's number — either
leave it out or state the thing you do know at the level of confidence you have. **Do not
fabricate a citation, a statistic, or a framework's internal names.** A module with fewer
named sources that are all real is worth more than one with twenty that are half invented.
