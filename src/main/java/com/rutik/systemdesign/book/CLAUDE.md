# Book Section — CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/book/`
Global conventions (formatting, Q&A rules, diagram rules): see root `CLAUDE.md`.

This section is **organized by book**, not by topic. Each book is a folder; each chapter is
a sub-folder containing a `README.md` that follows the book's own section order.

---

## Books & Chapters

| Book | Folder | Chapters |
|------|--------|----------|
| Designing Data-Intensive Applications (Kleppmann) | `designing_data_intensive_applications/` | preface + Ch 1–12 |

Per-chapter build status lives in each book's `README.md` **Build Manifest** table.

---

## The Book-Faithful Chapter Template

This section does NOT use the standard 14-section module template. A book chapter is a
multi-concept narrative, so each chapter `README.md` uses this structure instead. The
middle block — **one `##` per the chapter's own book sections, in book order** — is the
completeness guarantee: a missing topic is structurally visible.

```
# Chapter N: <Title>
> Part <I/II/III> · DDIA (Kleppmann) · builds on Ch <x>, leads to Ch <y>

## Chapter Map            — 1-paragraph placement + 3-bullet TL;DR of the chapter's argument
## The Big Question       — the driving problem the chapter answers (analogy / intuition)

## N.1 <book section title>   one ## per real book section, IN ORDER.
## N.2 <book section title>   ### sub-headings for sub-concepts. Each explained in depth:
## N.3 <book section title>   mechanism, concrete numbers, broken->fix, inline ASCII.
   ...

## Visual Intuition       — grouped diagrams (Mermaid preferred; ASCII for grids/geometry) for the chapter's hardest mechanics
## Key Concepts Glossary  — EVERY term the chapter defines, one line each (completeness net)
## Tradeoffs & Decision Tables — comparison tables
## Common Pitfalls / War Stories — production failure patterns tied to the chapter
## Real-World Systems Referenced — the systems the author names
## Summary                — the author's own end-of-chapter summary, expanded
## Interview Questions    — 15+ Q&As (bold Q, plain A; gotchas first)
## Cross-links in this repo — pointers to database/, hld/, backend/, devops/ deep dives
## Further Reading        — the key references the chapter cites
```

**Quality bars** (inherited from root `CLAUDE.md`):
- Diagrams appeal-first (see root `CLAUDE.md`): Mermaid preferred for topological diagrams (flow/sequence/state); ASCII kept only for grids, byte-layout maps, and geometry Mermaid can't draw; no image files
- Concrete numbers everywhere (latencies, sizes, amplification factors)
- Show broken design/code, then the fix
- No emojis (use `✓`/`✗`, not emoji check marks)
- **≥15 Q&As per chapter**, ordered by interview frequency (gotchas/traps first)
- Tables in Markdown pipe syntax; `---` rules between major sections
- Target depth: ~700–1200 lines per chapter ("comprehensive — don't miss anything")

---

## Adding a New Chapter

1. Create `<book_folder>/NN_<slug>/README.md` using the chapter template above.
2. Map **every** book section to a `## NN.x` heading, in order — this is non-negotiable; it
   is how completeness is guaranteed.
3. Add a glossary entry for every term the chapter defines.
4. Add ≥15 Q&As.
5. Run the diagram validator (see below) on the file.
6. Flip the chapter's row in the book `README.md` **Build Manifest** to done.
7. Update the chapter table + learning path in the book `README.md` if needed.

## Adding a New Book

1. Create `book/<book_slug>/README.md` (thesis, part map, chapter table, learning path,
   build manifest).
2. Add a row to the **Books** table in `book/README.md` and in this file.
3. Add a row to the root `README.md` Book Summaries section and the root `CLAUDE.md` table.
4. Build chapters per "Adding a New Chapter".

---

## Visual Intuition Diagrams

Follow the repo skill at `.claude/skills/visual-intuition-diagrams/`. Author a diagram,
then lint it before considering a file done:

```bash
python3 .claude/skills/visual-intuition-diagrams/diagram_tools.py check \
  src/main/java/com/rutik/systemdesign/book/
```

ASCII only; fenced block with no language tag; spaces not tabs; no trailing whitespace; no
emojis; widest line ≤ 100 cols; caption each diagram with 1–2 sentences tying it to the
insight.
