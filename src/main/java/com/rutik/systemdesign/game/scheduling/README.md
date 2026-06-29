# Scheduling — the durable daily trigger

Two ways to fire the daily coach. Use **launchd** for a real habit; the in-session
Claude jobs are a bonus when you're already working in Claude Code.

## Why launchd (recommended)

Claude Code scheduled tasks (`CronCreate`) only fire **while Claude Code is open and
idle**, and they auto-expire after 7 days. That can't reliably nudge you at 11am.
macOS `launchd` runs independently of Claude, wakes on schedule, and never expires.

The driver (`coach.sh` + `pick_today.py`) is **Claude-independent**: topic selection
and the notification work with plain Python. Set `USE_CLAUDE=1` in `coach.sh`'s
environment if you want `claude -p` to write a richer message (uses tokens).

### Install (run these yourself — they register a persistent background agent)

```bash
cd src/main/java/com/rutik/systemdesign/game/scheduling
cp com.sysdesigndaily.*.plist ~/Library/LaunchAgents/
launchctl load ~/Library/LaunchAgents/com.sysdesigndaily.main.plist
launchctl load ~/Library/LaunchAgents/com.sysdesigndaily.checkin.plist
launchctl load ~/Library/LaunchAgents/com.sysdesigndaily.weekly.plist
```

Verify / test / remove:

```bash
launchctl list | grep sysdesigndaily            # confirm loaded
bash coach.sh main                               # fire once now (opens game + notifies)
launchctl unload ~/Library/LaunchAgents/com.sysdesigndaily.main.plist   # stop it
```

### Schedule

| Plist | When | Mode |
|-------|------|------|
| `com.sysdesigndaily.main.plist` | Mon-Fri 11:03 | open game + curated topic + nudge |
| `com.sysdesigndaily.checkin.plist` | Mon-Fri 16:28 | streak-rescue if you haven't played |
| `com.sysdesigndaily.weekly.plist` | Fri 17:04 | weekly accuracy report |

> The plists hardcode this repo's absolute path. If you move the repo, update the
> `<string>` paths (script path + `StandardOutPath`) in all three files and reload.

> First time the notification fires, macOS may ask you to allow notifications for
> the script runner (Terminal/launchd). Allow it once.

## Bonus: in-session Claude jobs

While this Claude Code session is open, three `CronCreate` jobs (11:03 / 16:28 / Fri
17:04) run `claude_coach_prompt.md` for an LLM-written nudge. They die when the
session ends — `launchd` is the durable path.
