# Daily Learning Coach — scheduled-task prompt

This file is the instruction set the Claude Code scheduled tasks run. Each cron
trigger invokes Claude with a one-line prompt that points here and names a **mode**:
`main`, `checkin`, or `weekly`. Keep all coaching logic here so the cron jobs stay
thin and the behavior is version-controlled.

Paths (absolute):
- Game dir:   `src/main/java/com/rutik/systemdesign/game/`
- Progress:   `game/state/progress.json`   (read; written by the game, not you)
- Today pick: `game/state/today.json`       (you WRITE this)
- Server:     `game/server.py`              (port 8777)
- Bank index: `game/questions/index.json`   (section -> question count)

Do everything non-interactively. Never modify `progress.json` yourself — it is the
game's source of truth. You only ever write `today.json` and send notifications.

---

## Mode: `main`  (cron: 0 11 * * 1-5)

1. Read `state/progress.json` (treat missing/empty as a brand-new learner) and
   `questions/index.json`.
2. **Pick today's section** using this priority:
   - any section with `sections[x].seen == 0` (never played) — rotate through these first;
   - else the section with the **lowest accuracy** (`correct/seen`), needing review;
   - else the section with the fewest `seen` (least covered).
   Avoid repeating yesterday's section (check the last `history` entry) unless it's
   the only sensible choice.
3. Write `state/today.json`:
   ```json
   { "date": "<YYYY-MM-DD>", "section": "<picked>", "message": "<1-2 sentence coach note>" }
   ```
   The message should be specific and motivating: reference the streak, why this
   section was chosen, and what it covers. Example:
   "Day 6 streak. Your database accuracy is 54% — lowest of any section — so today
   we drill storage engines and replication. 10 questions, ~5 minutes."
4. **Ensure the server is running**, then open the game:
   ```bash
   pgrep -f "server.py --port 8777" >/dev/null || (cd <game dir> && nohup python3 server.py --port 8777 >/dev/null 2>&1 &)
   sleep 1 && open "http://127.0.0.1:8777/"
   ```
5. **Send the nudge** (Claude push notification if available; else macOS fallback):
   ```bash
   osascript -e 'display notification "<message>" with title "SysDesign Daily — Day <N> streak" sound name "Glass"'
   ```

## Mode: `checkin`  (cron: 30 16 * * 1-5)

1. Read `state/progress.json`.
2. If `lastPlayed` == today: do nothing (already played — don't nag).
3. Else send a gentle streak-rescue nudge naming the current streak at risk, e.g.
   "Your <N>-day streak ends at midnight. 5 minutes saves it — today's topic is
   already queued." Open the game again (same server check as above).

## Mode: `weekly`  (cron: 0 17 * * 5)

1. Read `state/progress.json`. Summarize the last 7 days from `history`:
   - days played this week / streak / longest streak / total XP gained this week;
   - accuracy per section (sorted worst-first) from `sections`;
   - the 2-3 weakest sections to prioritize next week.
2. Deliver as a notification and/or a short written summary. Keep it encouraging and
   concrete. Do not modify any files.

---

## Notes

- All three modes are read-only on `progress.json`; only `main` writes `today.json`.
- The game has its own fallback section-picker, so if a `main` run is missed the game
  still works when opened manually.
- If `open`/`osascript` are unavailable (non-macOS), substitute the platform's
  browser-open and notification commands.
