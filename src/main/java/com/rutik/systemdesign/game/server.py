#!/usr/bin/env python3
"""
server.py - Tiny stdlib server for the learning game. No third-party deps.

Responsibilities (deliberately dumb):
  - Serve the static SPA (index.html, app.js, style.css, questions.json).
  - GET  /api/progress  -> state/progress.json  (seeded if missing)
  - GET  /api/today     -> state/today.json      ({} if the coach hasn't run)
  - POST /api/progress  -> record a finished session; recompute the streak.

All "intelligence" (which topic to study, the nudge wording) lives in the
Claude coach task, which writes state/today.json. This server never decides
content; it only persists results and serves files.

Run:  python3 server.py [--port 8777]
"""

import argparse
import json
import os
import tempfile
from datetime import date, timedelta
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import unquote

GAME_DIR = os.path.dirname(os.path.abspath(__file__))
BASE_DIR = os.path.dirname(GAME_DIR)  # repo content root (sibling sections), for "dive deeper"
STATE_DIR = os.path.join(GAME_DIR, "state")
PROGRESS_PATH = os.path.join(STATE_DIR, "progress.json")
TODAY_PATH = os.path.join(STATE_DIR, "today.json")

XP_PER_CORRECT = 10
XP_STREAK_BONUS = 5  # per day of streak, added once per session


FREEZE_CAP = 3            # most streak-freezes a player can bank
FREEZE_GRANT_EVERY = 7    # earn one freeze back at each N-day streak milestone


def default_progress():
    return {
        "streak": 0,
        "longestStreak": 0,
        "lastPlayed": None,
        "totalXP": 0,
        "sections": {},          # name -> {"seen": int, "correct": int}
        "history": [],           # list of session dicts
        "reviews": {},           # question id -> spaced-repetition state
        "freezes": 2,            # streak-freeze tokens (start with a small buffer)
        "freezeUsedOn": [],      # ISO dates auto-covered by a spent freeze
    }


def schedule_review(rv, status, today):
    """SM-2-lite update for one question's spaced-repetition state."""
    if status == "correct":
        rv["reps"] = rv.get("reps", 0) + 1
        rv["ease"] = min(3.0, rv.get("ease", 2.5) + 0.1)
        reps = rv["reps"]
        interval = 1 if reps == 1 else 3 if reps == 2 else max(1, round(rv.get("interval", 1) * rv["ease"]))
    else:  # wrong (or learned-after-skip) -> see again soon
        rv["reps"] = 0
        rv["lapses"] = rv.get("lapses", 0) + (1 if status == "wrong" else 0)
        rv["ease"] = max(1.3, rv.get("ease", 2.5) - (0.2 if status == "wrong" else 0.05))
        interval = 1
    rv["interval"] = interval
    rv["due"] = (date.fromisoformat(today) + timedelta(days=interval)).isoformat()
    return rv


def ensure_fields(progress):
    """Backfill fields added after a progress.json was first written, so existing
    players pick up new state (e.g. the streak-freeze buffer) on next load."""
    progress.setdefault("reviews", {})
    progress.setdefault("freezes", 2)
    progress.setdefault("freezeUsedOn", [])
    return progress


def load_json(path, fallback):
    try:
        with open(path, encoding="utf-8") as fh:
            return json.load(fh)
    except (FileNotFoundError, json.JSONDecodeError):
        return fallback


def save_json_atomic(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    fd, tmp = tempfile.mkstemp(dir=os.path.dirname(path), suffix=".tmp")
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as fh:
            json.dump(data, fh, ensure_ascii=False, indent=2)
        os.replace(tmp, path)
    finally:
        if os.path.exists(tmp):
            os.remove(tmp)


def record_session(progress, session):
    """Merge one finished session into progress: per-question results feed the
    section tallies and spaced-repetition schedule; recompute the streak/XP."""
    today = session.get("date") or date.today().isoformat()
    reviews = progress.setdefault("reviews", {})
    results = session.get("results")
    answered = correct = 0

    if results:
        for res in results:
            sec = res.get("section", "unknown")
            status = res.get("status")
            answered += 1
            tally = progress["sections"].setdefault(sec, {"seen": 0, "correct": 0})
            tally["seen"] += 1
            tally["lastPlayed"] = today      # drives the home "getting rusty" nudge
            if status == "correct":
                tally["correct"] += 1
                correct += 1
            qid = res.get("id")
            if qid:
                rv = reviews.get(qid) or {"ease": 2.5, "interval": 0, "reps": 0, "lapses": 0}
                rv["section"] = sec
                rv["module"] = res.get("module")
                schedule_review(rv, status, today)
                reviews[qid] = rv
    else:  # legacy summary payload
        section = session.get("section", "unknown")
        answered = int(session.get("answered", 0))
        correct = int(session.get("correct", 0))
        tally = progress["sections"].setdefault(section, {"seen": 0, "correct": 0})
        tally["seen"] += answered
        tally["lastPlayed"] = today
        tally["correct"] += correct

    # Streak: only advance once per calendar day. A single missed day (gap of
    # exactly 2) is auto-covered by a streak-freeze token if one is banked, so a
    # lone slip doesn't reset a long streak. Larger gaps still reset.
    last = progress.get("lastPlayed")
    freeze_used = False
    advanced = False
    if last is None:
        progress["streak"] = 1
        advanced = True
    else:
        gap = (date.fromisoformat(today) - date.fromisoformat(last)).days
        if gap <= 0:
            pass  # same calendar day (or clock skew): streak unchanged
        elif gap == 1:
            progress["streak"] = progress.get("streak", 0) + 1
            advanced = True
        elif gap == 2 and progress.get("freezes", 0) > 0:
            progress["freezes"] -= 1
            missed = (date.fromisoformat(today) - timedelta(days=1)).isoformat()
            progress.setdefault("freezeUsedOn", []).append(missed)
            progress["freezeUsedOn"] = progress["freezeUsedOn"][-60:]
            progress["streak"] = progress.get("streak", 0) + 1
            advanced = True
            freeze_used = True
        else:
            progress["streak"] = 1
            advanced = True

    # Earn a freeze back at each streak milestone (capped). Gated on `advanced`
    # so multiple same-day sessions can't farm freezes off an unchanged streak.
    if advanced and progress["streak"] > 0 and progress["streak"] % FREEZE_GRANT_EVERY == 0:
        progress["freezes"] = min(FREEZE_CAP, progress.get("freezes", 0) + 1)

    progress["longestStreak"] = max(progress.get("longestStreak", 0), progress["streak"])
    progress["lastPlayed"] = today

    bonus = int(session.get("bonusXp", 0))
    xp = correct * XP_PER_CORRECT + progress["streak"] * XP_STREAK_BONUS + bonus
    progress["totalXP"] = progress.get("totalXP", 0) + xp

    progress["history"].append({
        "date": today, "answered": answered, "correct": correct, "xp": xp,
    })
    progress["history"] = progress["history"][-365:]  # cap growth
    return progress, xp, freeze_used


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=GAME_DIR, **kwargs)

    def end_headers(self):
        # Never let the browser serve a stale SPA/asset for this zero-build tool.
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def _send_json(self, obj, status=200):
        body = json.dumps(obj).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = self.path.split("?")[0]
        if path == "/api/progress":
            return self._send_json(ensure_fields(load_json(PROGRESS_PATH, default_progress())))
        if path == "/api/today":
            return self._send_json(load_json(TODAY_PATH, {}))
        if path.startswith("/content/"):
            return self._serve_content(path[len("/content/"):])
        return super().do_GET()

    def _serve_content(self, rel):
        """Serve a repo content file (e.g. a module README) for 'dive deeper'.
        Read-only, confined to BASE_DIR."""
        rel = unquote(rel)
        safe = os.path.normpath(rel)
        full = os.path.join(BASE_DIR, safe)
        if safe.startswith("..") or os.path.isabs(safe) or not os.path.abspath(full).startswith(BASE_DIR):
            return self._send_json({"error": "forbidden"}, status=403)
        if not os.path.isfile(full):
            return self._send_json({"error": "not found"}, status=404)
        with open(full, "rb") as fh:
            body = fh.read()
        self.send_response(200)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        if self.path.split("?")[0] != "/api/progress":
            return self._send_json({"error": "not found"}, status=404)
        length = int(self.headers.get("Content-Length", 0))
        try:
            session = json.loads(self.rfile.read(length) or b"{}")
        except json.JSONDecodeError:
            return self._send_json({"error": "bad json"}, status=400)
        progress = ensure_fields(load_json(PROGRESS_PATH, default_progress()))
        progress, xp, freeze_used = record_session(progress, session)
        save_json_atomic(PROGRESS_PATH, progress)
        return self._send_json({
            "ok": True, "xpEarned": xp, "freezeUsed": freeze_used,
            "freezesLeft": progress.get("freezes", 0), "progress": progress,
        })

    def log_message(self, *args):  # quiet console
        pass


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=8777)
    args = ap.parse_args()
    os.makedirs(STATE_DIR, exist_ok=True)
    if not os.path.exists(PROGRESS_PATH):
        save_json_atomic(PROGRESS_PATH, default_progress())
    srv = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    print(f"Learning game at http://127.0.0.1:{args.port}  (Ctrl-C to stop)")
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        print("\nstopped")


if __name__ == "__main__":
    main()
