#!/usr/bin/env bash
set -euo pipefail

# Regenerates the game's question banks and module-relatedness graphs, then
# verifies the result is complete.
#
# Single source of truth for bank generation: the Pages workflow, the Android
# APK asset build, and a local checkout all call THIS script, so the three can
# never drift apart. Both banks (game/questions/*.json) and graphs
# (game/graph/*.json) are gitignored build artifacts — nothing works without
# running this first.
#
# Deliberately NOT here: the `sed __BUILD__` service-worker stamp. That is a
# Pages-only cache-busting step (the APK skips service-worker registration
# entirely and its smoke test asserts the literal __BUILD__ still ships), so it
# stays inline in pages.yml where it cannot leak into the APK payload.

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GAME="$REPO_ROOT/src/main/java/com/rutik/systemdesign/game"

cd "$GAME"

echo "Generating question banks (extract.py --strict)"
python3 extract.py --strict

# `sections` is a dict {section: questionCount}; iterate its KEYS.
SECTIONS=$(python3 -c "import json;print(' '.join(json.load(open('questions/index.json'))['sections']))")
echo "Building relatedness graphs for: $SECTIONS"
for sec in $SECTIONS; do
  python3 build_graph.py "$sec" > /dev/null
done

# Verify the payload is complete. A missing or empty bank does not break the
# reader, so a silent gap here ships an APK whose entire quiz is empty — the
# failure this check exists to make loud.
python3 - <<'PY'
import json, os, sys

fail = []
with open("questions/index.json") as f:
    index = json.load(f)
sections = list(index.get("sections") or [])
if not sections:
    sys.exit("FATAL: questions/index.json lists no sections")

total = 0
for sec in sections:
    bank = f"questions/{sec}.json"
    if not os.path.exists(bank):
        fail.append(f"missing bank {bank}")
        continue
    try:
        with open(bank) as f:
            qs = json.load(f)
    except json.JSONDecodeError as e:
        fail.append(f"unparseable bank {bank}: {e}")
        continue
    if not qs:
        fail.append(f"empty bank {bank}")
    # index.json already records the expected count per section, so a truncated
    # bank (1 question where 537 belong) is catchable for free — "non-empty" on
    # its own would wave it through.
    expected = index["sections"].get(sec)
    if isinstance(expected, int) and len(qs) != expected:
        fail.append(f"{bank} has {len(qs)} questions, index.json says {expected}")
    total += len(qs)

    graph = f"graph/{sec}.json"
    if not os.path.exists(graph):
        fail.append(f"missing graph {graph}")
        continue
    try:
        with open(graph) as f:
            g = json.load(f)
    except json.JSONDecodeError as e:
        fail.append(f"unparseable graph {graph}: {e}")
        continue
    if "pairs" not in g:
        fail.append(f"graph {graph} has no 'pairs' key")

# paths.json drives the Study tier tabs (app.js fetches it at boot into
# STUDY_PATHS). Missing or unparseable, it degrades to {} — every section
# silently loses its Senior/Principal tabs and falls back to Full. No visible
# error, so check it here.
try:
    with open("questions/paths.json") as f:
        tiers = json.load(f)
    if not tiers:
        fail.append("questions/paths.json is empty — Study tier tabs would vanish")
except FileNotFoundError:
    fail.append("missing questions/paths.json — Study tier tabs would vanish")
except json.JSONDecodeError as e:
    fail.append(f"unparseable questions/paths.json: {e}")

# tech.json drives the Technologies screen (app.js fetches it at boot into
# TECH_INDEX). Missing, the screen shows its empty state instead of the repo-wide
# technology index — quiet enough to ship unnoticed, so check it here.
try:
    with open("questions/tech.json") as f:
        techidx = json.load(f)
    if not (techidx.get("tech") and techidx.get("modules")):
        fail.append("questions/tech.json has no technologies — the Technologies screen would be empty")
except FileNotFoundError:
    fail.append("missing questions/tech.json — the Technologies screen would be empty")
except json.JSONDecodeError as e:
    fail.append(f"unparseable questions/tech.json: {e}")

# tech_index.json — the technology KNOWLEDGE BANK (what each tool IS), generated from
# technologies/tech_bank/*.md. It lives at the GAME ROOT, not in questions/, so the
# stale-artifact guard below cannot see it and neither can the APK smoke test's
# `BANKS == SEC + 3` count (which stays correct precisely because this file is not in
# questions/ — do not touch it). Broken, the Technologies screen silently degrades to a
# flat provenance list with no summaries, chips, tiers or kinds: exactly the quiet
# failure the tech.json check above exists to prevent.
try:
    with open("tech_index.json") as f:
        bank = json.load(f)
except FileNotFoundError:
    fail.append("missing tech_index.json — the Technologies screen would lose every "
                "summary, role chip and facet")
except json.JSONDecodeError as e:
    fail.append(f"unparseable tech_index.json: {e}")
else:
    tools = bank.get("tools") or {}
    if not tools:
        fail.append("tech_index.json has no tools — the technology bank is empty")
    if len(bank.get("tiers") or []) < 18:
        fail.append(f"tech_index.json has {len(bank.get('tiers') or [])} tiers, expected >= 18 "
                    "— the tier shelf and every facet chip come from this list")
    if not (bank.get("kinds") and bank.get("langs")):
        fail.append("tech_index.json is missing its kinds/langs vocabulary")
    # The permanent regression test for the markdown migration: every field that existed
    # before the source moved into technologies/tech_bank/ must still parse out. `d` (the
    # authored description) is the one key that is new and per-record optional, so it is
    # excluded — the other four must never drift, whatever a description wave touches.
    bad = [n for n, v in tools.items()
           if not (isinstance(v.get("s"), str) and 15 <= len(v["s"]) <= 220)
           or not v.get("r") or not v.get("l") or not v.get("k")
           or set(v) - {"k", "r", "l", "s", "d"}]
    if bad:
        fail.append(f"{len(bad)} tech_index.json record(s) lost a migrated field "
                    f"(k/r/l/s) or grew an unknown key, e.g. {bad[:3]}")

# Stale files from a removed/renamed section would rsync into the APK payload
# and confuse the loaders, so demand an exact match both ways.
# index.json, paths.json and tech.json are the section-INDEPENDENT artifacts
# extract.py emits into questions/. Everything else in there must name a real
# section, so a new non-section artifact has to be declared here or it reads as
# stale junk. Adding one here means bumping the APK smoke test's BANKS count too
# (.github/workflows/android-apk.yml) — they are two halves of the same guard.
want_banks = {f"{s}.json" for s in sections} | {"index.json", "paths.json", "tech.json"}
have_banks = {f for f in os.listdir("questions") if f.endswith(".json")}
for extra in sorted(have_banks - want_banks):
    fail.append(f"stale bank questions/{extra} (no such section) — delete it: rm questions/{extra}")
for extra in sorted({f for f in os.listdir("graph") if f.endswith(".json")} - {f"{s}.json" for s in sections}):
    fail.append(f"stale graph graph/{extra} (no such section) — delete it: rm graph/{extra}")

if fail:
    print("BANK VERIFICATION FAILED:", file=sys.stderr)
    for f_ in fail:
        print("  - " + f_, file=sys.stderr)
    sys.exit(1)

print(f"OK: sections={len(sections)} questions={total} graphs={len(sections)}")
PY
