# PRODUCT.md — System Design Daily

A personal daily-learning PWA built over the owner's own study repository: the repo is the content, the game makes showing up every day the easy part.

---

## What It Is

A static single-page application deployed on GitHub Pages. The repository itself is the CMS: every module README and deep-dive sub-file feeds the app, and `game/extract.py` is the build step that turns their interview Q&As — ~8,400+ across 14 sections — into a multiple-choice question bank. On top of that bank sit a 5-minute MCQ blitz, an SM-2 spaced-repetition review deck, and a full-content reader, so studying the repo and playing the game are the same activity.

---

## Audience & Register

Single user — the repo owner. This is a product/tool for one person, not a service: no accounts, no social features, no telemetry. Every design decision optimizes for the owner's daily loop, not for growth.

---

## Problem

The problem is consistency, not content. The repo already holds more study material than anyone could exhaust; what fails is the habit around it:

- **Nudge** — nothing chooses what to study today, so nothing gets studied.
- **Boredom** — re-reading long files does not survive contact with a tired evening.
- **Friction** — any setup step (server, install, build) is a reason to skip a day.
- **Invisible progress** — without a ledger, weeks of effort feel like nothing happened.

---

## Surfaces

| Surface | Route | What it does |
|---------|-------|--------------|
| Home | `#/home` | Coach pick of the day, blitz launcher, review-due count, weak spots, drills, section skyline, streak display |
| Quiz | `#/quiz` | 5-minute blitz; redemption round, Skip-then-teach, card flip, confidence rating |
| Gauntlet | `#/gauntlet` | Daily sealed 10-question run — same 10 for the day, one attempt |
| Study | `#/study` | Full / Interview path toggle; ordered learning path per section |
| Reader | `#/reader/<path>` | One-Dark full-content reader with think-first recall prompts and continue-your-path navigation |
| Progress | `#/progress` | Insights, activity ledger, export/import of all state |
| Codex | `#/codex` | Collected/browsable question codex |
| Debrief | `#/debrief` | Post-session summary and misses review |
| Command palette | Cmd/Ctrl+K | Jump to any surface or section from anywhere |

---

## Hard Limits

These are constraints the product treats as non-negotiable:

- **Reader is pitch black** — `#000000` background with `#ffffff` prose in every theme; no theme may tint it.
- **Vanilla JS/CSS, no build, no frameworks** — the Mermaid CDN import is the sole external dependency.
- **Pages-only** — no server, no API, no database, no scheduler. If it cannot run as static files, it does not ship.
- **`localStorage` (`sd_progress`) is the single source of truth** — all streaks, mastery, and review state live there.
- **Export/import is the only backup** — there is no cloud sync to fall back on.
- **Persisted fields are additive-only** — new code must read old saves; existing fields are never repurposed or removed.

---

## Out of Scope

Accounts and login, any server component, cloud sync, JS frameworks, a native app, hearts/lives-style punishment mechanics, and social features (leaderboards against other people, sharing, feeds). These are permanent non-goals, not backlog items.

---

## Pointers

- `src/main/java/com/rutik/systemdesign/game/README.md` — architecture and feature inventory
- `src/main/java/com/rutik/systemdesign/game/CLAUDE.md` — authoring contract and maintenance rules
