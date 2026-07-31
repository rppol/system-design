#!/usr/bin/env bash
set -euo pipefail

# Pre-renders every Mermaid fence in the repo to game/diagrams/<hash>.mmz.
#
# Single source of truth for the diagram pre-render, shared by the Pages workflow
# and the Android APK asset build for exactly the reason build_banks.sh is shared:
# this was five duplicated steps in two files, and two copies of a sequence drift.
# A drifted pair means Pages and the APK render diagrams on different inputs — the
# one failure neither surface's smoke test can see, because each is internally
# consistent.
#
# It drives the REAL app in headless Chromium and reuses its render pipeline, so the
# SVGs are byte-identical to live output. A fence that fails to parse FAILS THE BUILD
# (build_diagrams.mjs exits 1, owner-set 2026-07-30) — it was non-blocking once, which
# let 20 broken fences accumulate unseen, each shipping as a raw-source blob to anyone
# offline or on the APK.
#
# Deliberately NOT here: `rm -rf node_modules`. That is Pages-only — its artifact step
# uploads the whole tree with `path: .`, so node_modules would ship to the CDN. The APK
# build rsyncs a filtered subtree and never sees it.

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT=8901

cd "$REPO_ROOT"

npm ci --no-audit --no-fund

# Bundle the iconify logo pack into game/vendor/ BEFORE the render, so Mermaid icon
# nodes (product logos) bake into the .mmz and ship offline. Registered from a
# relative fetch on both surfaces, which is why this adds no IS_APK seam.
node scripts/build_icons.mjs

npx --yes puppeteer browsers install chrome >/dev/null

# build_diagrams.mjs loads the app over http:// (module imports and relative fetches
# do not work from file://), so serve the repo root for the duration of the render.
python3 -m http.server "$PORT" --directory "$REPO_ROOT" >/tmp/lora_diag_http.log 2>&1 &
DIAG_SERVER=$!
# A trap, not a trailing `kill`: under `set -e` a failing render exits immediately and
# the trailing form never runs, orphaning the server on the port. That is invisible in
# CI (the runner is torn down) and reliably wedges the NEXT local build.
trap 'kill "$DIAG_SERVER" 2>/dev/null || true' EXIT
sleep 2

node scripts/build_diagrams.mjs --base "http://localhost:$PORT"
