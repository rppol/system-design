#!/usr/bin/env python3
"""
pick_today.py - Deterministic "today's topic" picker (no LLM required).

Mirrors the coach's `main`-mode selection so the daily habit works even when
Claude Code is not running. Reads state/progress.json + questions/index.json,
chooses a section, and writes state/today.json. Prints the chosen message to
stdout so a shell wrapper can use it for a desktop notification.

Selection priority:
  1. a section never played (seen == 0), rotating through them
  2. else the lowest-accuracy section (correct/seen)
  3. else the least-seen section
Avoids repeating yesterday's section unless it is the only option.
"""

import json
import os
from datetime import date

GAME_DIR = os.path.dirname(os.path.abspath(__file__))
STATE_DIR = os.path.join(GAME_DIR, "state")
INDEX = os.path.join(GAME_DIR, "questions", "index.json")
PROGRESS = os.path.join(STATE_DIR, "progress.json")
TODAY = os.path.join(STATE_DIR, "today.json")

LABELS = {
    "backend": "Backend Engineering", "book": "Book Summaries",
    "cs_fundamentals": "CS Fundamentals", "database": "Databases",
    "devops": "DevOps & Cloud", "hld": "High-Level Design", "java": "Java",
    "lld": "Low-Level Design", "llm": "LLM Engineering", "ml": "Machine Learning",
    "python": "Python", "spring": "Spring",
}


def load(path, fallback):
    try:
        with open(path, encoding="utf-8") as fh:
            return json.load(fh)
    except (FileNotFoundError, json.JSONDecodeError):
        return fallback


def choose(sections, progress):
    seen_map = progress.get("sections", {})
    last_section = (progress.get("history") or [{}])[-1].get("section")

    def acc(s):
        st = seen_map.get(s)
        return (st["correct"] / st["seen"]) if st and st["seen"] else 1.0

    def seen(s):
        st = seen_map.get(s)
        return st["seen"] if st else 0

    names = list(sections.keys())
    candidates = [s for s in names if s != last_section] or names

    unplayed = sorted([s for s in candidates if seen(s) == 0])
    if unplayed:
        return unplayed[0]
    return sorted(candidates, key=lambda s: (acc(s), seen(s)))[0]


def main():
    index = load(INDEX, {"sections": {}})
    progress = load(PROGRESS, {})
    sections = index.get("sections", {})
    if not sections:
        print("No question bank. Run extract.py first.")
        return

    section = choose(sections, progress)
    streak = progress.get("streak", 0)
    label = LABELS.get(section, section)
    st = progress.get("sections", {}).get(section)
    if st and st["seen"]:
        why = f"your {label} accuracy is {round(100 * st['correct'] / st['seen'])}% - time to sharpen it"
    else:
        why = f"a fresh section you haven't touched yet"
    streak_bit = f"Day {streak} streak. " if streak else "Start your streak. "
    message = f"{streak_bit}Today: {label} - {why}. 10 questions, ~5 minutes."

    os.makedirs(STATE_DIR, exist_ok=True)
    with open(TODAY, "w", encoding="utf-8") as fh:
        json.dump({"date": date.today().isoformat(), "section": section, "message": message},
                  fh, ensure_ascii=False, indent=2)
    print(message)


if __name__ == "__main__":
    main()
