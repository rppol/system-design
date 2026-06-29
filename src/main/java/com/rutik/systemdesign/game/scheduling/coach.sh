#!/usr/bin/env bash
# coach.sh <main|checkin|weekly>
# Robust, Claude-independent daily driver for the System Design Daily game.
# Started by launchd (see the .plist files in this folder). Picks today's topic,
# ensures the server is up, opens the browser, and posts a macOS notification.
#
# Set USE_CLAUDE=1 to let `claude -p` write the smart message (uses tokens);
# default is the deterministic pick_today.py so your streak never depends on
# Claude running.

set -euo pipefail
MODE="${1:-main}"
GAME_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STATE_DIR="$GAME_DIR/state"
PORT="${PORT:-8777}"
URL="http://127.0.0.1:$PORT/"

notify() {  # notify "<message>" "<title>"
  /usr/bin/osascript -e "display notification \"${1//\"/\'}\" with title \"${2//\"/\'}\" sound name \"Glass\"" || true
}

ensure_server() {
  if ! /usr/bin/pgrep -f "server.py --port $PORT" >/dev/null 2>&1; then
    (cd "$GAME_DIR" && /usr/bin/nohup python3 server.py --port "$PORT" >/dev/null 2>&1 &)
    sleep 1
  fi
}

today_iso() { date +%F; }
last_played() { /usr/bin/python3 -c "import json,sys;print(json.load(open('$STATE_DIR/progress.json')).get('lastPlayed') or '')" 2>/dev/null || echo ""; }

case "$MODE" in
  main)
    if [[ "${USE_CLAUDE:-0}" == "1" ]] && command -v claude >/dev/null 2>&1; then
      MSG="$(cd "$GAME_DIR/.." && claude -p "Run the System Design Daily coach in main mode per src/main/java/com/rutik/systemdesign/game/claude_coach_prompt.md; write game/state/today.json and print only the one-line message." 2>/dev/null | tail -1)"
      [[ -z "$MSG" ]] && MSG="$(cd "$GAME_DIR" && python3 pick_today.py | tail -1)"
    else
      MSG="$(cd "$GAME_DIR" && python3 pick_today.py | tail -1)"
    fi
    ensure_server
    /usr/bin/open "$URL"
    notify "$MSG" "SysDesign Daily"
    ;;
  checkin)
    if [[ "$(last_played)" != "$(today_iso)" ]]; then
      ensure_server
      /usr/bin/open "$URL"
      notify "Your streak ends at midnight - 5 minutes saves it. Today's topic is queued." "SysDesign Daily - streak at risk"
    fi
    ;;
  weekly)
    SUMMARY="$(/usr/bin/python3 - "$STATE_DIR/progress.json" <<'PY'
import json,sys
try:
    p=json.load(open(sys.argv[1]))
except Exception:
    print("No progress yet - play your first blitz to start a streak.");sys.exit()
secs=p.get("sections",{})
worst=sorted([(s,d["correct"]/d["seen"]) for s,d in secs.items() if d.get("seen")],key=lambda x:x[1])[:3]
weak=", ".join(s for s,_ in worst) or "all sections still open"
print(f"Streak {p.get('streak',0)} (best {p.get('longestStreak',0)}), {p.get('totalXP',0)} XP. Focus next week: {weak}.")
PY
)"
    notify "$SUMMARY" "SysDesign Daily - weekly report"
    ;;
  *)
    echo "usage: coach.sh <main|checkin|weekly>" >&2; exit 1;;
esac
