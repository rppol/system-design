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

# Stale files from a removed/renamed section would rsync into the APK payload
# and confuse the loaders, so demand an exact match both ways.
want_banks = {f"{s}.json" for s in sections} | {"index.json"}
have_banks = {f for f in os.listdir("questions") if f.endswith(".json")}
for extra in sorted(have_banks - want_banks):
    fail.append(f"stale bank questions/{extra} (no such section)")
for extra in sorted({f for f in os.listdir("graph") if f.endswith(".json")} - {f"{s}.json" for s in sections}):
    fail.append(f"stale graph graph/{extra} (no such section)")

if fail:
    print("BANK VERIFICATION FAILED:", file=sys.stderr)
    for f_ in fail:
        print("  - " + f_, file=sys.stderr)
    sys.exit(1)

print(f"OK: sections={len(sections)} questions={total} graphs={len(sections)}")
PY
