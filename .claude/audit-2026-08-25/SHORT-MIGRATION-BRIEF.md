# `book/` **Short:** migration — the binding contract

Repo: `/Users/rutikp/Prism/rutik46/systemdesign`
**PATH TRAP:** `extract.py` is at `src/main/java/com/rutik/systemdesign/game/extract.py`. `cd
src/main/java/com/rutik/systemdesign` before running it; from the repo root it fails with "No
such file or directory" **while the shell still reports EXIT=0**.

## What a `**Short:**` line is and why it exists

Without one, the MCQ's correct option is DERIVED by taking the answer's first sentence and
trimming it at a clause boundary past 220 chars. That ships a mangled option whenever the first
sentence is long, opens with a code fence, or is not self-contained. An authored line replaces
the derivation. **The full answer is untouched and is still what the quiz reveals after
answering.**

## The format — exact

```
**Q: <question>?**
**Short:** <one self-contained sentence>

<the full answer, UNCHANGED>
```

The line goes **directly under the question**, as the **FIRST non-blank line of the answer**.
Anywhere else it is treated as ordinary prose and silently ignored — which is also what stops
the parser from deleting a sentence that happens to start that way.

## Hard rules

1. **15-220 characters.** `extract.py --strict` **FAILS THE BUILD** outside that range. Measure
   the text AFTER `**Short:** `, not the whole line.
2. **NEVER edit, trim, reword or delete any part of the existing answer.** `answerFull` must not
   lose a word. You are ADDING a line, never carving one out. If the answer's first sentence
   already does the job, the Short may restate it more tightly — but the original stays.
3. **It must stand alone.** It is read as a bare option beside three distractors, with no
   surrounding context. No "see below", no "as above", no pronoun with no referent.
4. **Do not restate the question.** "Q: What is a Bloom filter?" -> not "A Bloom filter is a
   thing that...". Give the ANSWER.
5. **No code fence, no list, no newline.** One sentence. Prefer the claim over the hedge.
6. **No emojis.**

## THE RULE THAT IS SPECIAL TO `book/` — read this twice

**A summary is answerable to its SOURCE, not to 2026 reality.** The repo-wide
REPLACE-DO-NOT-ANNOTATE modernization rule is **INVERTED here**.

Your `**Short:**` must state **what the book says**, not what is true today. If a 2017 chapter
says single-leader replication is the default in most relational databases, the Short says that —
it does NOT "correct" it to 2026. Modernizing a book summary makes it wrong *about the book* and
attributes a claim to a named author who never made it. When the answer body itself is written as
"the book argues X", the Short should carry that framing too.

If an answer sits under a `## Beyond the book` heading, it is explicitly post-publication material
and the Short states that content normally.

## Method

Work file by file. For each Q&A that lacks a Short:
- read the full answer,
- write ONE sentence that a reader could pick out of four options and be right,
- insert it directly under the `**Q:` line.

Use `sonnet` subagents (pass `model: "sonnet"` explicitly) to parallelise across files, but
**spot-check their output yourself** — a wrong Short ships as the graded-CORRECT MCQ option, which
is worse than no Short at all.

## Verify before reporting

```bash
cd src/main/java/com/rutik/systemdesign
# every Q&A in your books must now have a Short as its first non-blank answer line:
python3 - <<'PY'
import os,re
for base in ["book/<your_book_1>","book/<your_book_2>"]:
    for r,_,fs in os.walk(base):
        for f in fs:
            if not f.endswith('.md'): continue
            p=os.path.join(r,f); t=open(p).read().split('\n'); span=False; miss=0; bad=0
            for i,l in enumerate(t):
                if re.match(r'^##\s+.*interview\s+q',l,re.I): span=True; continue
                if span and l.startswith('## '): span=False
                if span and l.startswith('**Q'):
                    j=i+1
                    while j<len(t) and not t[j].strip(): j+=1
                    if j<len(t) and t[j].startswith('**Short:**'):
                        n=len(t[j][len('**Short:** '):])
                        if n>220 or n<15: bad+=1
                    else: miss+=1
            if miss or bad: print(f"{p}: missing={miss} out-of-range={bad}")
PY
```
Report the output. It must be empty.

Do NOT run `--strict` (other agents are mid-flight), `--write-paths`, `npm ci`, or
`check_render.mjs`. Do NOT touch `game/app.js`, any `<!-- study-paths -->` block, any README or
CLAUDE.md. No `git add`, no `git commit`.
