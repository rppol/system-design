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
Strategy: scope to the interview-questions section(s) (every heading matching
INTERVIEW_HDR is unioned in file order), then treat any fully-bold line (or
**Q...:** line) as a question and the plain paragraph(s) beneath it as the
answer. This keys on the template's structure, not on the inconsistent
surface "Q" convention.

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

# Sections to skip entirely (the game app; book IS extracted).
SKIP_SECTIONS = {"game"}

# Path components that exclude a README from the bank (e.g. case studies).
SKIP_PATH_PARTS = {"case_studies"}

# Bounds for the SHORT answer shown as an MCQ option. A first sentence longer
# than SHORT_MAX is TRIMMED to a clean clause boundary (see make_short) rather
# than dropped, so no question is ever excluded from the bank on length alone.
# The full answer is always preserved for the post-answer reveal.
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
# Case studies (11-section principal template) hold their Q&As under "Interview
# Discussion Points" and/or "Additional Interview Questions" — match both. Used
# ONLY for the separate case-study reader-quiz pool, never the main bank.
CASE_HDR = re.compile(r"^##\s+.*interview\s+(?:q|discussion)", re.IGNORECASE)


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


def md_inline(text, keep_bold):
    """Markdown-preserving reduction: keep inline `code` (and optionally **bold**),
    unwrap links to their text, collapse whitespace. The reader renders these."""
    text = text.replace("\n", " ")
    text = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", text)  # [t](url) -> t
    if not keep_bold:
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


def clean_question_md(line):
    """Display variant of a Q line: drop the bold wrapper + Qn label, but keep
    inline `code`. (Question bold is just the Q&A wrapper, so it is not preserved.)"""
    line = line.strip().replace("**", "")
    line = re.sub(r"^Q\s*\d*\s*[:.]\s*", "", line)
    return md_inline(line, keep_bold=False)


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


def make_short(text):
    """Bounded MCQ option derived from the answer's first sentence. Never drops on
    length: an over-long first sentence is trimmed to a clean boundary at or below
    SHORT_MAX (prefer a clause delimiter; fall back to a word boundary + ellipsis).
    Short first sentences are returned as-is. Returns "" only for empty input."""
    s = first_sentence(text).strip()
    if len(s) <= SHORT_MAX:
        return s
    cut = s[:SHORT_MAX]
    for delim in ("; ", " — ", " – ", ": ", ", "):
        idx = cut.rfind(delim)
        if idx >= 80:                      # keep a substantive, self-contained clause
            return cut[:idx].rstrip(" ,;:—–")
    idx = cut.rfind(" ")
    return (cut[:idx] if idx >= 80 else cut).rstrip() + "…"


def parse_md(path, section, module, hdr=INTERVIEW_HDR):
    """Yield per-question dicts from one .md file (a module README or deep-dive
    sub-file). Each dict carries both the stripped text (used for ids, options,
    and distractors) and, when they differ, markdown-preserving display variants."""
    source_file = os.path.basename(path)
    with open(path, encoding="utf-8") as fh:
        lines = fh.readlines()
    n = len(lines)

    # Scope to the interview-questions section(s): a file may contain more than
    # one heading matching INTERVIEW_HDR (e.g. an answer-less "Common Interview
    # Questions" list before the real "## 12. Interview Q&As") -- collect Q&As
    # from ALL of them, in order. Sections with no bold Q lines contribute nothing.
    spans = []
    idx = 0
    while idx < n:
        if hdr.match(lines[idx]):
            start = idx + 1
            end = n
            for j in range(start, n):
                if lines[j].startswith("## "):
                    end = j
                    break
            spans.append((start, end))
            idx = end          # a matching "## " terminator is re-tested as a new span
        else:
            idx += 1
    if not spans:
        return []

    results = []
    running = 0
    for start, end in spans:
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
            q_joined = " ".join(p.strip() for p in q_parts)
            question = clean_question(q_joined)
            question_md = clean_question_md(q_joined)

            # Collect answer lines (after the question's closing line) until the next boundary.
            j = k + 1
            answer_lines = [answer_first] if answer_first else []
            while j < end:
                if is_question_line(lines[j].strip()) or lines[j].strip() == "---":
                    break
                answer_lines.append(lines[j])
                j += 1
            answer_raw = " ".join(answer_lines)
            answer_full = re.sub(r"^A\s*[:.]\s*", "", strip_markdown(answer_raw))  # drop a leading "A:" label
            answer_full_md = re.sub(r"^\*{0,2}A\s*[:.]\*{0,2}\s*", "", md_inline(answer_raw, keep_bold=True))
            i = j

            if not question or not answer_full:
                continue
            short = make_short(answer_full)
            if not short:
                continue
            # Display variants: emitted only when they differ from the stripped text.
            short_md = first_sentence(answer_full_md)
            if strip_markdown(short_md) != short:        # sentence split disagreed -> can't align
                short_md = None
            results.append({
                "section": section,
                "module": module,
                "sourceFile": source_file,
                "qIndex": q_index,
                "question": question,
                "questionMd": question_md if question_md != question else None,
                "answerFull": answer_full,
                "answerFullMd": answer_full_md if answer_full_md != answer_full else None,
                "answerShort": short,
                "answerShortMd": short_md if (short_md is not None and short_md != short) else None,
            })
    return results


def difficulty(q_index):
    if q_index <= 5:
        return "core"
    if q_index <= 10:
        return "intermediate"
    return "advanced"


def qid(module, question):
    """Content-stable id: hashes module + STRIPPED question text, so re-extracting
    after unrelated content edits does NOT orphan spaced-repetition state. Used for
    both a question's own id and the distractorIds pointing back to source questions."""
    h = hashlib.md5(f"{module}|{question}".encode("utf-8")).hexdigest()[:12]
    return f"{module}#{h}"


# --- wiring guard: STUDY_ORDER / STUDY_PATHS in app.js must cover the bank ---
STUDY_ORDER_RE = re.compile(r"const STUDY_ORDER = \{(.*?)\n\};", re.S)
STUDY_PATHS_RE = re.compile(r"const STUDY_PATHS = \{(.*?)\n\};", re.S)
SLUG_RE = re.compile(r'"([a-z0-9_]+(?:/[a-z0-9_]+)+)"')


def _section_arrays(body):
    """{'cuda': [slug, ...]} from a STUDY_ORDER/STUDY_PATHS object body.
    Section keys sit at 2-space indent ('  cuda: [' / '  cuda: {')."""
    out = {}
    for m in re.finditer(r"\n  ([a-z_]+): ([\[{])", body):
        open_ch, close_ch = m.group(2), ("]" if m.group(2) == "[" else "}")
        depth, i = 1, m.end()
        while depth and i < len(body):
            if body[i] == open_ch: depth += 1
            elif body[i] == close_ch: depth -= 1
            i += 1
        out[m.group(1)] = SLUG_RE.findall(body[m.end():i])
    return out


def check_wiring(questions, strict):
    """Fail (under --strict) if a bank module is missing from STUDY_ORDER, or a
    STUDY_PATHS array stops being an ordered subset of its section's STUDY_ORDER.
    Warn-only without --strict. Reads app.js as text (stdlib only)."""
    app = open(os.path.join(GAME_DIR, "app.js"), encoding="utf-8").read()
    errors, warns = [], []
    mo = STUDY_ORDER_RE.search(app)
    if not mo:
        errors.append("cannot locate the STUDY_ORDER literal in app.js -- guard cannot run")
    else:
        order = _section_arrays(mo.group(1))
        wired = {s for arr in order.values() for s in arr}
        counts = {}
        for q in questions:
            counts[q["module"]] = counts.get(q["module"], 0) + 1
        for mod in sorted(counts):
            if mod not in wired:
                errors.append(f"STUDY_ORDER gap: {mod} has {counts[mod]} questions but no entry (falls to the 9999 sort -- dead-last in Study)")
        for slug in sorted(wired):
            if counts.get(slug, 0) == 0:
                warns.append(f"STUDY_ORDER dead entry: {slug} extracted 0 questions (Q&A format broken?)")
        mp = STUDY_PATHS_RE.search(app)
        if mp:
            for sec, arr in _section_arrays(mp.group(1)).items():
                o = order.get(sec, [])
                idxs = [o.index(x) if x in o else -1 for x in arr]
                missing = [x for x, i in zip(arr, idxs) if i < 0]
                if missing:
                    errors.append(f"STUDY_PATHS.{sec} not a subset of STUDY_ORDER: {missing}")
                elif idxs != sorted(idxs):
                    errors.append(f"STUDY_PATHS.{sec} order deviates from STUDY_ORDER")
    for w in warns:  print(f"WIRING WARNING: {w}", file=sys.stderr)
    for e in errors: print(f"WIRING ERROR: {e}", file=sys.stderr)
    if errors and strict: sys.exit(1)
    if not errors: print("wiring check: OK")


# Order a module's deep-dive files by the LEARNING sequence, not alphabetically.
# The parent README links its sub-files in pedagogical order (e.g. dsa_patterns'
# numbered 1..25 pattern table), so first-link-appearance in the README is the
# curated order. README.md always leads; any file the README never links falls
# back to the alphabetical tail so nothing is dropped.
_SUBLINK_RE = re.compile(r"\(\.?/?([a-z0-9_]+\.md)(?:#[^)]*)?\)")
def order_md_files(module_root, md_files):
    readme = next((f for f in md_files if f.lower() == "readme.md"), None)
    rest = [f for f in md_files if f != readme]
    ordered, seen = [], set()
    if readme:
        try:
            text = open(os.path.join(module_root, readme), encoding="utf-8").read()
        except OSError:
            text = ""
        for m in _SUBLINK_RE.finditer(text):
            fn = m.group(1)
            if fn in rest and fn not in seen:
                seen.add(fn)
                ordered.append(fn)
    tail = sorted(f for f in rest if f not in seen)   # unlinked files: stable alpha tail
    return ([readme] if readme else []) + ordered + tail


# Case studies live in <section>/case_studies/ and are EXCLUDED from the Q&A bank
# (SKIP_PATH_PARTS) and from file_tree. This separate pass indexes them so the game
# can surface a READ-ONLY "Case Studies" study track (a third path beside Full and
# Interview). Two shapes coexist: a flat page (design_x.md) and a per-study directory
# (design_x/README.md, linked in the README as `design_x/`). Order + display names
# come from the case_studies/README.md "Full Learning Path" links (README-curated,
# same philosophy as order_md_files); orphans not linked there are an alpha tail so
# nothing is dropped. cross_cutting/ reference notes and dot-dirs are not case studies.
CS_LINK_RE = re.compile(r"\[([^\]]+)\]\((?:\./)?([a-z0-9_]+)(?:\.md|/(?:README\.md)?)(?:#[^)]*)?\)")
CS_EXCLUDE_DIRS = {"cross_cutting"}


def collect_case_studies(section, base_dir):
    cs_root = os.path.join(base_dir, section, "case_studies")
    if not os.path.isdir(cs_root):
        return []
    universe = {}   # slug -> reader path (relative to base_dir, forward slashes)
    for name in sorted(os.listdir(cs_root)):
        if name.startswith("."):
            continue
        full = os.path.join(cs_root, name)
        if os.path.isdir(full):
            if name not in CS_EXCLUDE_DIRS and os.path.isfile(os.path.join(full, "README.md")):
                universe[name] = f"{section}/case_studies/{name}/README.md"
        elif name.endswith(".md") and name.lower() != "readme.md":
            universe[name[:-3]] = f"{section}/case_studies/{name}"
    if not universe:
        return []
    # Order + names from the README's "Full Learning Path" section (fall back to the
    # whole README if that heading is absent).
    text = ""
    readme = os.path.join(cs_root, "README.md")
    if os.path.isfile(readme):
        try:
            text = open(readme, encoding="utf-8").read()
        except OSError:
            text = ""
    scope = text
    m = re.search(r"^#{2,}\s+.*Full Learning Path.*$", text, re.M | re.I)
    if m:
        nxt = re.search(r"^##\s", text[m.end():], re.M)
        scope = text[m.end(): m.end() + nxt.start()] if nxt else text[m.end():]
    ordered, names, seen = [], {}, set()
    for lm in CS_LINK_RE.finditer(scope):
        slug = lm.group(2)
        if slug in universe and slug not in seen:
            seen.add(slug)
            ordered.append(slug)
            txt = lm.group(1).strip()
            # Some READMEs (e.g. llm) use the filename itself as link text; fall
            # back to a titleized slug so the track shows readable names.
            if txt.lower().endswith(".md") or txt.lower() == slug:
                txt = slug.replace("_", " ").title()
            names[slug] = txt
    for slug in sorted(universe):                     # orphans -> alpha tail (nothing dropped)
        if slug not in seen:
            ordered.append(slug)
            names[slug] = slug.replace("_", " ").title()
    return [{"file": universe[s], "name": names[s]} for s in ordered]


def build_questions(raw, rng):
    """raw parsed Q&As -> MCQ entries (dedup + IDF-related distractors). Called
    for BOTH the main bank and the separate case-study reader-quiz pool; each pool
    computes its own IDF and draws distractors only from within itself."""
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

    # Map a distractor TEXT back to its source question (first occurrence within
    # the section, deterministic), so every distractor can carry a distractorId and
    # a markdown display variant — including last-resort pool fills.
    text2j = {}
    for j, q in enumerate(raw):
        text2j.setdefault(q["section"], {}).setdefault(q["answerShort"], j)

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
            scored.append((score, text, j))
        scored.sort(key=lambda x: (-x[0], x[1]))  # tie-break on text -> fully deterministic

        related = [t for s, t, j in scored if s > 0]
        if len(related) >= 3:
            # sample from the most-related band so replays vary but stay on-topic
            distractors = rng.sample(related[: max(3, min(8, len(related)))], 3)
        else:
            distractors = related[:]
            for _, t, j in scored:                 # fill from any same-pool candidate
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

        # Per-distractor: id of the source question and its markdown variant (if any),
        # aligned with the distractors list order.
        sec_map = text2j.get(q["section"], {})
        distractor_ids, distractors_md = [], []
        for d in distractors:
            sj = sec_map.get(d)
            if sj is None:
                distractor_ids.append(None)
                distractors_md.append(None)
                continue
            src = raw[sj]
            distractor_ids.append(qid(src["module"], src["question"]))
            smd = src.get("answerShortMd")
            distractors_md.append(smd if (smd and smd != d) else None)

        # Top concepts: question + answer tokens ranked by IDF, ties alphabetical.
        concepts = sorted(q_toks[idx] | ans_toks[idx], key=lambda t: (-idf.get(t, 1.0), t))[:6]

        entry = {
            "id": qid(q["module"], q["question"]),
            "section": q["section"],
            "module": q["module"],
            "moduleName": module_name,
            "sourceFile": q["sourceFile"],
            "difficulty": difficulty(q["qIndex"]),
            "question": q["question"],
            "answerFull": q["answerFull"],
            "correct": correct,
            "distractors": distractors,
            "distractorIds": distractor_ids,
            "concepts": concepts,
        }
        # Markdown display variants — emitted only when they differ (size discipline).
        if q.get("questionMd"):
            entry["questionMd"] = q["questionMd"]
        if q.get("answerShortMd"):
            entry["correctMd"] = q["answerShortMd"]
        if q.get("answerFullMd"):
            entry["answerFullMd"] = q["answerFullMd"]
        if any(x is not None for x in distractors_md):
            entry["distractorsMd"] = distractors_md
        questions.append(entry)
    return questions


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
        md_files = order_md_files(root, [fn for fn in files if fn.endswith(".md") and fn != "CLAUDE.md"])
        if md_files and len(parts) >= 2:      # skip section root dirs (depth==1)
            file_tree[module] = md_files
        for fn in md_files:
            raw.extend(parse_md(os.path.join(root, fn), section, module))

    questions = build_questions(raw, rng)
    if not questions:
        print("ERROR: no questions parsed", file=sys.stderr)
        sys.exit(1)

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

    mod_counts = {}
    for q in questions:
        mod_counts[q["module"]] = mod_counts.get(q["module"], 0) + 1
    # Case-studies index (read-only "Case Studies" study track). Separate walk of
    # the top-level section dirs; excluded from the bank + file_tree above.
    case_studies_map = {}
    for section in sorted(os.listdir(BASE_DIR)):
        if section in SKIP_SECTIONS or not os.path.isdir(os.path.join(BASE_DIR, section)):
            continue
        cs = collect_case_studies(section, BASE_DIR)
        if cs:
            case_studies_map[section] = cs

    # Separate case-study Q&A pool: parse each indexed case study's "Interview
    # Discussion Points"/"Questions" (CASE_HDR) with module = its parent dir, build
    # MCQs from WITHIN the case pool (distractors drawn from other case studies), and
    # write questions/case_studies/<section>.json. This feeds ONLY the reader's
    # bottom-of-file "Quiz this topic" on a case-study page — it is NEVER merged into
    # the main bank, so case studies stay out of Study blitzes/flashcards/gauntlet.
    case_raw = []
    for section, entries in case_studies_map.items():
        for e in entries:
            module = os.path.dirname(e["file"])       # forward-slash relative parent dir
            case_raw.extend(parse_md(os.path.join(BASE_DIR, e["file"]), section, module, CASE_HDR))
    case_questions = build_questions(case_raw, rng)
    # Sibling of questions/ (NOT under it) so questions/*.json stays exactly the
    # section banks + index — the APK smoke test globs questions/*.json.
    cs_out = os.path.join(GAME_DIR, "case_questions")
    os.makedirs(cs_out, exist_ok=True)
    for name in os.listdir(cs_out):                   # clear stale pools
        if name.endswith(".json"):
            os.remove(os.path.join(cs_out, name))
    cs_per_section = {}
    for q in case_questions:
        cs_per_section.setdefault(q["section"], []).append(q)
    for sec, qlist in cs_per_section.items():
        with open(os.path.join(cs_out, f"{sec}.json"), "w", encoding="utf-8") as fh:
            json.dump(qlist, fh, ensure_ascii=False, separators=(",", ":"))
    print(f"Wrote {len(case_questions)} case-study questions -> {cs_out}/<section>.json")

    index = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "total": len(questions),
        "sections": {s: len(per_section[s]) for s in sorted(per_section)},
        "files": file_tree,
        # additive: per-module bank counts (0 for reader-only dirs); consumed by the
        # game to scope the Codex/quests to capturable modules only.
        "moduleCounts": {m: mod_counts.get(m, 0) for m in sorted(set(file_tree) | set(mod_counts))},
        # additive: per-section ordered case studies (reader path + display name) for
        # the read-only Case Studies track; NOT in the bank/file_tree/moduleCounts.
        "caseStudies": case_studies_map,
    }
    with open(os.path.join(OUT_DIR, "index.json"), "w", encoding="utf-8") as fh:
        json.dump(index, fh, ensure_ascii=False, indent=2)

    print(f"Wrote {len(questions)} questions -> {OUT_DIR}/<section>.json")
    print("Per-section counts:")
    for sec, cnt in index["sections"].items():
        print(f"  {sec:16s} {cnt}")
    check_wiring(questions, "--strict" in sys.argv[1:])


if __name__ == "__main__":
    main()
