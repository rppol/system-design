#!/usr/bin/env python3
"""
build_graph.py - Derive a WEIGHTED module-relatedness graph for a section from
measurable signals only (no authored/hallucinated edges):

  1. Cross-links: actual markdown links from one module's .md files to another
     module in the same section (each link is a hard prerequisite/related vote).
  2. Lexical overlap: IDF-weighted token overlap between the modules' own
     question+answer corpus in questions/<section>.json (same lexical model
     family as extract.py's distractor ranking).

Edge DIRECTION is not stored here — the app orients every edge A->B by the
curated STUDY_ORDER (A earlier than B), which keeps the graph an acyclic DAG
grounded in the existing curation. This script stores undirected pair weights.

Output: graph/<section>.json  {"section": s, "pairs": [{"a","b","w","links","lex"}]}
Run:    python3 build_graph.py llm
"""

import json
import math
import os
import re
import sys

GAME_DIR = os.path.dirname(os.path.abspath(__file__))
BASE_DIR = os.path.dirname(GAME_DIR)
OUT_DIR = os.path.join(GAME_DIR, "graph")

TOKEN_RE = re.compile(r"[a-z0-9]+")
STOPWORDS = frozenset("""
a an and the of to in for on with is are be by as at it its this that these those or
not no if then else when while do does done can could should would may might will
you your they them their we our he she his her from into over under than so such
each per via use used using uses what why how which who whom where whose
between within across about after before during because both either neither only also
more most less least very much many few some any all one two three first second
data system systems model models value values case cases example examples type types
llm llms token tokens
""".split())

MD_LINK = re.compile(r"\]\(([^)\s#]+\.md)")

KEEP_PER_NODE = 4          # strongest edges kept per module (readability cap)
LEX_FLOOR_PCT = 80         # lexical-only pairs must beat this percentile


def tokenize(text):
    return [t for t in TOKEN_RE.findall(text.lower()) if len(t) > 2 and t not in STOPWORDS]


def module_of(path, section):
    """'llm/advanced_rag/graph_rag.md' -> 'llm/advanced_rag' (or None).
    Section-root files (llm/README.md) are NOT modules — and their links are
    navigation indexes, not dependency votes, so they are excluded entirely."""
    parts = path.split("/")
    if len(parts) >= 3 and parts[0] == section and "case_studies" not in parts \
            and not parts[1].endswith(".md"):
        return f"{parts[0]}/{parts[1]}"
    return None


def main():
    section = sys.argv[1] if len(sys.argv) > 1 else "llm"
    sec_dir = os.path.join(BASE_DIR, section)
    bank_path = os.path.join(GAME_DIR, "questions", f"{section}.json")
    if not os.path.isdir(sec_dir) or not os.path.exists(bank_path):
        sys.exit(f"missing {sec_dir} or {bank_path}")

    # ---- signal 1: real cross-links between modules -------------------------
    links = {}  # (a, b) sorted tuple -> count
    modules = set()
    for root, dirs, files in os.walk(sec_dir):
        dirs.sort()
        if "case_studies" in root:
            continue
        rel_root = os.path.relpath(root, BASE_DIR).replace(os.sep, "/")
        src_mod = module_of(rel_root + "/x.md", section)
        if not src_mod:
            continue
        modules.add(src_mod)
        for fn in sorted(files):
            if not fn.endswith(".md") or fn == "CLAUDE.md":
                continue
            text = open(os.path.join(root, fn), encoding="utf-8").read()
            for target in MD_LINK.findall(text):
                # resolve relative link against this file's directory
                stack = rel_root.split("/")
                for part in target.split("/"):
                    if part in ("", "."):
                        continue
                    if part == "..":
                        if stack:
                            stack.pop()
                    else:
                        stack.append(part)
                dst_mod = module_of("/".join(stack), section)
                if dst_mod and dst_mod != src_mod:
                    key = tuple(sorted((src_mod, dst_mod)))
                    links[key] = links.get(key, 0) + 1

    # ---- signal 2: IDF-weighted lexical overlap of module Q&A corpora -------
    bank = json.load(open(bank_path, encoding="utf-8"))
    corpus = {}
    for q in bank:
        corpus.setdefault(q["module"], []).append(q["question"] + " " + q["correct"])
        modules.add(q["module"])
    mod_toks = {m: set(tokenize(" ".join(texts))) for m, texts in corpus.items()}
    df = {}
    for ts in mod_toks.values():
        for t in ts:
            df[t] = df.get(t, 0) + 1
    n_docs = max(1, len(mod_toks))
    idf = {t: math.log(n_docs / (1 + c)) + 1.0 for t, c in df.items()}

    def lex(a, b):
        ta, tb = mod_toks.get(a), mod_toks.get(b)
        if not ta or not tb:
            return 0.0
        inter = sum(idf.get(t, 1.0) for t in (ta & tb))
        denom = math.sqrt(sum(idf.get(t, 1.0) for t in ta) * sum(idf.get(t, 1.0) for t in tb))
        return inter / denom if denom else 0.0

    mods = sorted(modules)
    pair_lex = {}
    for i, a in enumerate(mods):
        for b in mods[i + 1:]:
            s = lex(a, b)
            if s > 0:
                pair_lex[(a, b)] = s

    lex_vals = sorted(pair_lex.values())
    floor = lex_vals[int(len(lex_vals) * LEX_FLOOR_PCT / 100)] if lex_vals else 1.0
    max_links = max(links.values(), default=1)
    max_lex = lex_vals[-1] if lex_vals else 1.0

    # ---- combine: link-backed pairs always kept; lexical-only above floor ---
    pairs = []
    for key in set(links) | {k for k, v in pair_lex.items() if v >= floor}:
        a, b = key
        ln = links.get(key, 0)
        lx = pair_lex.get(key, 0.0)
        # weight: links dominate (explicit curation votes), lexical refines
        w = 0.7 * min(1.0, ln / max_links) + 0.3 * (lx / max_lex)
        if ln == 0:
            w = 0.3 * (lx / max_lex)
        pairs.append({"a": a, "b": b, "w": round(w, 4), "links": ln, "lex": round(lx, 4)})

    # readability cap: keep each module's KEEP_PER_NODE strongest pairs
    pairs.sort(key=lambda p: -p["w"])
    per_node = {}
    kept = []
    for p in pairs:
        ka, kb = per_node.get(p["a"], 0), per_node.get(p["b"], 0)
        if ka >= KEEP_PER_NODE and kb >= KEEP_PER_NODE and p["links"] == 0:
            continue
        kept.append(p)
        per_node[p["a"]] = ka + 1
        per_node[p["b"]] = kb + 1

    os.makedirs(OUT_DIR, exist_ok=True)
    out = os.path.join(OUT_DIR, f"{section}.json")
    json.dump({"section": section, "pairs": kept}, open(out, "w", encoding="utf-8"), indent=1)
    print(f"{section}: {len(mods)} modules, {len(links)} linked pairs, kept {len(kept)} edges -> {out}")
    top = sorted(kept, key=lambda p: -p["w"])[:8]
    for p in top:
        print(f"  {p['w']:.2f}  {p['a'].split('/')[1]} <-> {p['b'].split('/')[1]}  (links {p['links']}, lex {p['lex']:.2f})")


if __name__ == "__main__":
    main()
