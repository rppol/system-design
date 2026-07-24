#!/usr/bin/env bash
set -euo pipefail

# Rebuilds the Android WebView asset payload.
#
# Generates the question banks, mirrors the entire study repo (every Markdown
# section + the game SPA) into the APK's assets/www, then vendors Mermaid
# locally so diagrams render fully offline (the reader falls back to this file
# when the CDN is unreachable). Run before any Gradle assemble; the assets/ tree
# is gitignored and always regenerated.

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$REPO_ROOT/src/main/java/com/rutik/systemdesign/"
DEST="$REPO_ROOT/android/app/src/main/assets/www"

echo "Repo root : $REPO_ROOT"
echo "Source    : $SRC"
echo "Dest      : $DEST"

# Banks and graphs are gitignored build artifacts, so a fresh clone has none.
# Generating them HERE (rather than expecting the caller to remember) is what
# stops a local build from shipping an APK whose reader works but whose entire
# quiz is empty.
bash "$REPO_ROOT/scripts/build_banks.sh"

# Pre-render Mermaid diagrams to static SVG (game/diagrams/*.mmz) so they render
# fully offline in the WebView — no engine, no CDN. Drives the REAL app in headless
# Chromium (Puppeteer) and reuses its render pipeline, so the SVGs are byte-identical
# to live output. Gitignored + regenerated here every build. The vendored mermaid
# engine (below) stays as the offline fallback for any un-baked/invalid fence.
echo "Pre-rendering Mermaid diagrams..."
( cd "$REPO_ROOT" && npm ci --no-audit --no-fund )
( cd "$REPO_ROOT" && node scripts/build_icons.mjs )   # bundle iconify logo pack into game/vendor/ so icon nodes bake + ship offline
( cd "$REPO_ROOT" && npx --yes puppeteer browsers install chrome >/dev/null )
python3 -m http.server 8901 --directory "$REPO_ROOT" >/tmp/lora_diag_http.log 2>&1 &
DIAG_SERVER=$!
sleep 2
( cd "$REPO_ROOT" && node scripts/build_diagrams.mjs --base http://localhost:8901 )
kill "$DIAG_SERVER" 2>/dev/null || true

# Recreate the payload from scratch so deleted content never lingers.
rm -rf "$DEST"
mkdir -p "$DEST"

# Repo tooling stays out of the distributable payload: python/shell scripts and
# hidden files (.impeccable/, .claude/, .gitignore) are dev-side only. Markdown
# content, the game SPA, and the .java LLD examples (reader-linkable) all ship.
rsync -a \
  --exclude='__pycache__' --exclude='*.pyc' \
  --exclude='*.py' --exclude='*.sh' --exclude='.*' \
  "$SRC" "$DEST/"

# Vendor Mermaid for offline diagram rendering.
#
# The version is DERIVED from app.js's CDN import rather than pinned a second
# time here: two independent pins drift silently, and a drifted pair means the
# APK and Pages render diagrams on different engines. Bump the version in
# app.js and the APK re-vendors to match automatically.
VENDOR_DIR="$DEST/game/vendor"
mkdir -p "$VENDOR_DIR"
# `|| true`: under `set -e` a no-match grep would kill the script here, and the
# zero-match branch below (the useful error message) could never run.
MERMAID_VERSION=$(grep -oE 'mermaid@[0-9]+\.[0-9]+\.[0-9]+' "$SRC/game/app.js" | sort -u || true)
if [ "$(printf '%s\n' "$MERMAID_VERSION" | grep -c .)" -ne 1 ]; then
  echo "ERROR: expected exactly one pinned mermaid@x.y.z in game/app.js, found:" >&2
  printf '  %s\n' ${MERMAID_VERSION:-"(none)"} >&2
  exit 1
fi
MERMAID_URL="https://cdn.jsdelivr.net/npm/${MERMAID_VERSION}/dist/mermaid.min.js"
MERMAID_OUT="$VENDOR_DIR/mermaid.min.js"
echo "Fetching  : $MERMAID_URL"
# A transient CDN blip must not fail the build of an app whose whole point is
# working offline.
curl -fSL --retry 3 --retry-delay 2 --retry-all-errors "$MERMAID_URL" -o "$MERMAID_OUT"

# Sanity: the real minified bundle is ~3.4 MB (3,565,102 bytes for 11.16.0);
# anything under 2.5 MB means a 404 page, a redirect stub, or a truncated
# download slipped through. Floor kept loose so a patch bump that shrinks the
# bundle slightly doesn't false-fail CI.
SIZE=$(wc -c < "$MERMAID_OUT")
MIN_BYTES=2621440  # 2.5 MiB
if [ "$SIZE" -lt "$MIN_BYTES" ]; then
  echo "ERROR: mermaid.min.js is $SIZE bytes (< 3 MB) — download looks wrong or truncated." >&2
  exit 1
fi

FILE_COUNT=$(find "$DEST" -type f | wc -l | tr -d ' ')
echo "OK: www files=$FILE_COUNT  mermaid=$MERMAID_VERSION $SIZE bytes"
