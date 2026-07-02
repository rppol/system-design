#!/usr/bin/env python3
"""
extract.py - Build the game's question bank from the repo's Markdown.

Walks every sibling content section (java/, hld/, llm/, ...), parses the
"## 12. Interview Questions" Q&A blocks out of every module README AND its
deep-dive sub-files (e.g. llm/advanced_rag/graph_rag.md), and emits per-section
question files the static game loads. Case studies are excluded. No third-party deps.

Sub-files are grouped under their parent directory's module, so all of
advanced_rag/*.md count toward the "Advanced Rag" topic in the picker.

Q&A format handled (all four variants seen in the repo):
    **Q1: full question text?**          <- numbered, question inside the bold
    **Q1:** full question text?          <- numbered, only the label is bold
    **Q: full question text?**           <- unnumbered "Q:" label (ml/)
    **full question text?**              <- no Q marker, whole question bold (database/, backend/, llm/)
Strategy: scope to the "## 12. Interview Questions" section, then treat any
fully-bold line (or **Q...:** line) as a question and the plain paragraph(s)
beneath it as the answer. This keys on the template's structure, not on the
inconsistent surface "Q" convention.

MCQ construction:
    correct option   = first sentence of the answer (CLAUDE.md guarantees the
                       first sentence is the "direct answer")
    3 distractors    = first sentences of OTHER questions in the same top-level
                       section (repo-wide fallback if the section is too small)

Run from anywhere; paths are resolved relative to this file.
"""

import hashlib
import json
import math
import os
import random
import re
import sys
from datetime import datetime, timezone

GAME_DIR = os.path.dirname(os.path.abspath(__file__))
BASE_DIR = os.path.dirname(GAME_DIR)  # .../systemdesign/
OUT_DIR = os.path.join(GAME_DIR, "questions")  # per-section files + index.json

# Sections to skip entirely (the game itself; book uses a different template).
SKIP_SECTIONS = {"game"}

# Path components that exclude a README from the bank (e.g. case studies).
SKIP_PATH_PARTS = {"case_studies"}

# Quality filters for the SHORT answer used as an MCQ option.
SHORT_MIN = 15
SHORT_MAX = 220

# --- lexical model for picking RELATED distractors (no ML deps) ---
TOKEN_RE = re.compile(r"[a-z0-9]+")
STOPWORDS = frozenset("""
a an and the of to in for on with is are be by as at it its this that these those or
not no if then else when while do does done can could should would may might will
you your they them their we our he she his her from into over under than so such
each per via use used using uses what why how which who whom where whose
between within across about after before during because both either neither only also
more most less least very much many few some any all one two three first second
data system systems model models value values case cases example examples type types
""".split())


def tokenize(text):
    return [t for t in TOKEN_RE.findall(text.lower()) if len(t) > 2 and t not in STOPWORDS]

# Abbreviations that must NOT end a sentence during first-sentence splitting.
ABBREV = {
    "e.g.", "i.e.", "etc.", "vs.", "approx.", "cf.", "al.", "Inc.", "Ltd.",
    "Dr.", "Mr.", "Ms.", "no.", "No.", "fig.", "Fig.", "eq.", "Eq.",
}

# A numbered Q label at the very start, e.g. "**Q1:", "**Q12.", "**Q:".
Q_LABEL = re.compile(r"^\*\*Q\s*(\d*)\s*[:.]")
# A line that is entirely wrapped in bold, e.g. "**...question?**" (optional trailing :).
FULLY_BOLD = re.compile(r"^\*\*.+\*\*[:.]?$")
# Detects the interview-questions section header (number may vary; match by title).
INTERVIEW_HDR = re.compile(r"^##\s+.*interview\s+q", re.IGNORECASE)


def is_question_line(stripped):
    """True if a line inside the interview section starts a question heading
    (single-line bold, Q-label, or an opening bold that wraps to later lines)."""
    if not stripped.startswith("**"):
        return False
    if Q_LABEL.match(stripped) or FULLY_BOLD.match(stripped):
        return True
    return stripped.count("**") == 1  # opening bold not closed on this line -> wraps


def strip_markdown(text):
    """Reduce inline Markdown to readable plain text for options/answers."""
    text = text.replace("\n", " ")
    text = re.sub(r"`([^`]*)`", r"\1", text)          # `code` -> code
    text = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", text)  # [t](url) -> t
    text = text.replace("**", "").replace("__", "")
    text = re.sub(r"\s+", " ", text).strip()
    return text


def clean_question(line):
    """Turn a raw Q line into just the question text."""
    line = line.strip()
    # Remove the bold markers, then the leading "Qn:" / "Qn." label.
    line = line.replace("**", "")
    line = re.sub(r"^Q\s*\d*\s*[:.]\s*", "", line)  # strip "Qn:" / "Q:" label if present
    return strip_markdown(line)


def first_sentence(text):
    """Return the first sentence, guarding against abbreviation false splits."""
    # Candidate sentence ends: . ! ? followed by space + capital/backtick, or EOL.
    pos = 0
    while True:
        m = re.search(r"[.!?](?=\s|$)", text[pos:])
        if not m:
            return text.strip()
        end = pos + m.start() + 1
        candidate = text[:end].strip()
        # Did we stop right after a known abbreviation? If so, keep going.
        # Strip leading brackets/quotes so "(e.g." still matches "e.g.".
        last_word = candidate.split()[-1] if candidate.split() else ""
        last_word = last_word.lstrip("([{\"'“‘")
        if last_word in ABBREV or last_word.lower() in ABBREV or len(candidate) < 25:
            pos = end + 1
            if pos >= len(text):
                return text.strip()
            continue
        return candidate


def parse_md(path, section, module):
    """Yield dicts {question, answerFull, answerShort, qIndex} from one .md file
    (a module README or one of its deep-dive sub-files)."""
    with open(path, encoding="utf-8") as fh:
        lines = fh.readlines()
    n = len(lines)

    # Scope to the interview-questions section: from its header to the next "## ".
    start = None
    for idx, ln in enumerate(lines):
        if INTERVIEW_HDR.match(ln):
            start = idx + 1
            break
    if start is None:
        return []
    end = n
    for idx in range(start, n):
        if lines[idx].startswith("## "):
            end = idx
            break

    results = []
    running = 0
    i = start
    while i < end:
        stripped = lines[i].strip()
        if not is_question_line(stripped):
            i += 1
            continue
        running += 1
        m = Q_LABEL.match(stripped)
        q_index = int(m.group(1)) if (m and m.group(1)) else running

        # Gather the question text, joining lines if the bold question wraps.
        q_parts = [stripped]
        answer_first = ""        # any answer text trailing the closing ** on its line
        k = i                    # last line consumed by the question
        if stripped.count("**") < 2:   # opening ** not closed on this line -> wraps
            k = i + 1
            while k < end:
                ln = lines[k].rstrip("\n")
                if "**" in ln:           # this line closes the bold question
                    cut = ln.rfind("**")
                    q_parts.append(ln[:cut])
                    answer_first = ln[cut + 2:].strip()
                    break
                q_parts.append(ln.strip())
                k += 1
        question = clean_question(" ".join(p.strip() for p in q_parts))

        # Collect answer lines (after the question's closing line) until the next boundary.
        j = k + 1
        answer_lines = [answer_first] if answer_first else []
        while j < end:
            if is_question_line(lines[j].strip()) or lines[j].strip() == "---":
                break
            answer_lines.append(lines[j])
            j += 1
        answer_full = strip_markdown(" ".join(answer_lines))
        answer_full = re.sub(r"^A\s*[:.]\s*", "", answer_full)  # drop a leading "A:" label
        i = j

        if not question or not answer_full:
            continue
        short = first_sentence(answer_full)
        if not (SHORT_MIN <= len(short) <= SHORT_MAX):
            continue
        results.append({
            "section": section,
            "module": module,
            "qIndex": q_index,
            "question": question,
            "answerFull": answer_full,
            "answerShort": short,
        })
    return results


def difficulty(q_index):
    if q_index <= 5:
        return "core"
    if q_index <= 10:
        return "intermediate"
    return "advanced"


def main():
    rng = random.Random(42)  # reproducible distractor choices
    raw = []
    file_tree = {}   # "section/module" -> sorted list of .md filenames (for the sidebar tree)
    for root, dirs, files in os.walk(BASE_DIR):
        dirs.sort()  # deterministic traversal on every filesystem -> stable output
        rel = os.path.relpath(root, BASE_DIR)
        if rel == ".":
            continue
        parts = rel.split(os.sep)
        section = parts[0]
        if section in SKIP_SECTIONS:
            continue
        if SKIP_PATH_PARTS.intersection(parts):
            continue  # exclude case studies etc.
        module = rel.replace(os.sep, "/")  # parent dir -> README + its deep-dive sub-files share a module
        md_files = sorted(fn for fn in files if fn.endswith(".md") and fn != "CLAUDE.md")
        if md_files and len(parts) >= 2:      # skip section root dirs (depth==1)
            file_tree[module] = md_files
        for fn in md_files:
            raw.extend(parse_md(os.path.join(root, fn), section, module))

    if not raw:
        print("ERROR: no questions parsed", file=sys.stderr)
        sys.exit(1)

    # Drop exact repeats of the same question within a module (README vs sub-file
    # overlap) so the bank and the spaced-repetition ids stay collision-free.
    seen_q = set()
    deduped = []
    for q in raw:
        key = (q["module"], q["question"].strip().lower())
        if key in seen_q:
            continue
        seen_q.add(key)
        deduped.append(q)
    if len(deduped) != len(raw):
        print(f"Deduped {len(raw) - len(deduped)} repeated questions")
    raw = deduped

    # ---- lexical model: pick distractors that are topically RELATED ----
    ans_toks = [set(tokenize(q["answerShort"])) for q in raw]
    q_toks = [set(tokenize(q["question"])) for q in raw]
    n_docs = len(raw)
    df = {}
    for ts in ans_toks:
        for t in ts:
            df[t] = df.get(t, 0) + 1
    idf = {t: math.log(n_docs / (1 + c)) + 1.0 for t, c in df.items()}
    # vague lead-in answers make poor MCQ options -> skip them as distractors.
    JUNK_LEAD = re.compile(r"^(yes|no|sure|correct|right|exactly|true|false|maybe|both|it depends)\b", re.I)

    by_module, by_section = {}, {}
    for i, q in enumerate(raw):
        by_module.setdefault(q["module"], []).append(i)
        by_section.setdefault(q["section"], []).append(i)

    def jaccard(a, b):
        return len(a & b) / len(a | b) if (a or b) else 0.0

    questions = []
    for idx, q in enumerate(raw):
        correct = q["answerShort"]
        signature = q_toks[idx] | ans_toks[idx]  # the question's topic fingerprint

        # Candidates from the SAME MODULE first (tightest topical match); widen to
        # the section only if the module is too small. (Whole-section ranking was
        # tested and diluted relatedness, so we keep the module-first pool.)
        cand_ids = [j for j in by_module[q["module"]] if j != idx]
        if len({raw[j]["answerShort"] for j in cand_ids}) < 6:
            cand_ids = list(dict.fromkeys(cand_ids + [j for j in by_section[q["section"]] if j != idx]))

        # score each distinct candidate by IDF-weighted overlap with the signature
        scored, seen = [], {correct}
        for j in cand_ids:
            text = raw[j]["answerShort"]
            if text in seen:
                continue
            seen.add(text)
            if JUNK_LEAD.match(text):
                continue  # vague "Yes, ..." style answers make poor options
            if jaccard(ans_toks[idx], ans_toks[j]) > 0.7:
                continue  # too close to the correct answer -> could read as also-correct
            score = sum(idf.get(t, 1.0) for t in (signature & ans_toks[j]))
            scored.append((score, text))
        scored.sort(key=lambda x: (-x[0], x[1]))  # tie-break on text -> fully deterministic

        related = [t for s, t in scored if s > 0]
        if len(related) >= 3:
            # sample from the most-related band so replays vary but stay on-topic
            distractors = rng.sample(related[: max(3, min(8, len(related)))], 3)
        else:
            distractors = related[:]
            for _, t in scored:                    # fill from any same-pool candidate
                if len(distractors) >= 3:
                    break
                if t not in distractors:
                    distractors.append(t)
            pool = [raw[j]["answerShort"] for j in by_section[q["section"]] if j != idx]
            rng.shuffle(pool)
            for t in pool:                          # last resort: keep it a valid 4-option MCQ
                if len(distractors) >= 3:
                    break
                if t != correct and t not in distractors:
                    distractors.append(t)

        if len(distractors) < 3:
            continue  # cannot form a clean 4-option MCQ
        module_name = q["module"].split("/")[-1].replace("_", " ")
        # Content-stable id: hashes module + question text, so re-extracting after
        # unrelated content edits does NOT orphan spaced-repetition state (a
        # position-based id shifted for every question after any edit repo-wide).
        qhash = hashlib.md5(f"{q['module']}|{q['question']}".encode("utf-8")).hexdigest()[:12]
        questions.append({
            "id": f"{q['module']}#{qhash}",
            "section": q["section"],
            "module": q["module"],
            "moduleName": module_name,
            "qIndex": q["qIndex"],
            "difficulty": difficulty(q["qIndex"]),
            "question": q["question"],
            "answerFull": q["answerFull"],
            "correct": correct,
            "distractors": distractors,
        })

    # Group per section and write one file per section + a small index manifest.
    per_section = {}
    for q in questions:
        per_section.setdefault(q["section"], []).append(q)

    os.makedirs(OUT_DIR, exist_ok=True)
    # Clear stale section files so a removed section never lingers.
    for name in os.listdir(OUT_DIR):
        if name.endswith(".json"):
            os.remove(os.path.join(OUT_DIR, name))

    for sec, qlist in per_section.items():
        with open(os.path.join(OUT_DIR, f"{sec}.json"), "w", encoding="utf-8") as fh:
            json.dump(qlist, fh, ensure_ascii=False, separators=(",", ":"))

    index = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "total": len(questions),
        "sections": {s: len(per_section[s]) for s in sorted(per_section)},
        "files": file_tree,
    }
    with open(os.path.join(OUT_DIR, "index.json"), "w", encoding="utf-8") as fh:
        json.dump(index, fh, ensure_ascii=False, indent=2)

    print(f"Wrote {len(questions)} questions -> {OUT_DIR}/<section>.json")
    print("Per-section counts:")
    for sec, cnt in index["sections"].items():
        print(f"  {sec:16s} {cnt}")


if __name__ == "__main__":
    main()
