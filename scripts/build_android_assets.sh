#!/usr/bin/env bash
set -euo pipefail

# Rebuilds the Android WebView asset payload.
#
# Mirrors the entire study repo (every Markdown section + the game SPA) into the
# APK's assets/www, then vendors Mermaid locally so diagrams render fully offline
# (the reader falls back to this file when the CDN is unreachable). Run before
# any Gradle assemble; the assets/ tree is gitignored and always regenerated.

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$REPO_ROOT/src/main/java/com/rutik/systemdesign/"
DEST="$REPO_ROOT/android/app/src/main/assets/www"

echo "Repo root : $REPO_ROOT"
echo "Source    : $SRC"
echo "Dest      : $DEST"

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
VENDOR_DIR="$DEST/game/vendor"
mkdir -p "$VENDOR_DIR"
MERMAID_URL="https://cdn.jsdelivr.net/npm/mermaid@11.16.0/dist/mermaid.min.js"
MERMAID_OUT="$VENDOR_DIR/mermaid.min.js"
echo "Fetching  : $MERMAID_URL"
curl -fSL "$MERMAID_URL" -o "$MERMAID_OUT"

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
echo "OK: www files=$FILE_COUNT  mermaid=$SIZE bytes"
