"""Constants and helpers shared by extract.py (the question bank) and build_tech.py
(the technology bank + index).

This file exists to hold the handful of things BOTH builders must agree on. Each one is
here because a second copy would be a silent divergence, not merely duplication:

  SKIP_SECTIONS / SKIP_PATH_PARTS   if the two walks disagree about what to exclude, the
                                    technology index cites a page the question bank never
                                    saw -- a reader link into content that has no quiz.
  SHORT_MIN / SHORT_MAX             the bank record's `**Short:**` contract is deliberately
                                    the Q&A `**Short:**` contract, reused rather than
                                    re-declared, because it is the always-visible line on
                                    both surfaces and a bad value ships either way.
  STUDY_ORDER_RE / _section_arrays  both builders read STUDY_ORDER out of app.js, which is
                                    the single source of module ORDER for the whole app.

Nothing that belongs to only one builder goes here. OUT_DIR stays in extract.py; every
TECH_*/TB_* symbol stays in build_tech.py.
"""

import os
import re

GAME_DIR = os.path.dirname(os.path.abspath(__file__))
BASE_DIR = os.path.dirname(GAME_DIR)  # .../systemdesign/

# Sections to skip entirely (the game app; book IS extracted).
SKIP_SECTIONS = {"game"}

# Path components that exclude a page from the bank (e.g. case studies).
SKIP_PATH_PARTS = {"case_studies"}

# Bounds for the SHORT answer shown as an MCQ option. A first sentence longer
# than SHORT_MAX is TRIMMED to a clean clause boundary (see make_short) rather
# than dropped, so no question is ever excluded from the bank on length alone.
# The full answer is always preserved for the post-answer reveal.
SHORT_MIN = 15
SHORT_MAX = 220

STUDY_ORDER_RE = re.compile(r"const STUDY_ORDER = \{(.*?)\n\};", re.S)
STUDY_PATHS_RE = re.compile(r"const STUDY_PATHS = \{(.*?)\n\};", re.S)
SLUG_RE = re.compile(r'"([a-z0-9_]+(?:/[a-z0-9_]+)+)"')


def _section_arrays(body, inner=None):
    """{'cuda': [slug, ...]} from a STUDY_ORDER/STUDY_PATHS object body.
    Section keys sit at 2-space indent ('  cuda: [' / '  cuda: {').

    With `inner` set, return only that named array inside each section object -- e.g.
    _section_arrays(body, "senior") reads `senior: [...]` and IGNORES `principal: [...]`
    and the `*Files`/`cases` objects. Without it the whole section blob is scanned, which
    is right for STUDY_ORDER (a bare array) but would union every tier in STUDY_PATHS --
    precisely the false-fail the NOTE above STUDY_ORDER in app.js warns about.
    """
    out = {}
    for m in re.finditer(r"\n  ([a-z_]+): ([\[{])", body):
        open_ch, close_ch = m.group(2), ("]" if m.group(2) == "[" else "}")
        depth, i = 1, m.end()
        while depth and i < len(body):
            if body[i] == open_ch: depth += 1
            elif body[i] == close_ch: depth -= 1
            i += 1
        blob = body[m.end():i]
        if inner is not None:
            im = re.search(r"\b" + inner + r":\s*\[", blob)
            if not im:
                continue
            d, k = 1, im.end()
            while d and k < len(blob):
                if blob[k] == "[": d += 1
                elif blob[k] == "]": d -= 1
                k += 1
            blob = blob[im.end():k]
        out[m.group(1)] = SLUG_RE.findall(blob)
    return out
