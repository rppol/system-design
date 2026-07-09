---
name: verify
description: How to launch and drive the game (browser SPA) to verify reader/quiz changes end-to-end
---

# Verifying game/ changes

The only runnable surface in this repo is the learning game at
`src/main/java/com/rutik/systemdesign/game/` (static SPA, no build step).

## Launch

Serve the **repo root** (the reader fetches content at `../<section>/…`):

```bash
cd <repo-root>
python3 -m http.server 8901   # background it
# open http://localhost:8901/src/main/java/com/rutik/systemdesign/game/index.html
```

Question banks/graphs are gitignored but usually present locally (`game/questions/index.json`,
`game/graph/*.json`). If missing: `python3 game/extract.py && python3 game/build_graph.py`.

## Gotchas

- **Service worker serves stale app.js.** Before trusting anything, unregister + reload:
  `const rs = await navigator.serviceWorker.getRegistrations(); for (const r of rs) await r.unregister(); location.reload();`
- Reader deep links: `#/reader/<encodeURIComponent(path)>@<frag>`. Heading slugs are
  non-obvious (e.g. `6-how-it-works---detailed-mechanics`, triple dash) — read real ids from
  `[...document.querySelectorAll("#readerMain h2[id]")].map(h => h.id)` instead of guessing.
- Module paths are non-obvious too (e.g. `fastapi/dependency_injection_in_fastapi`, book
  chapters under `book/designing_data_intensive_applications/…`). List real ones from
  `Object.keys((await fetch("questions/index.json").then(r=>r.json())).files)`. A wrong path
  shows the reader's in-panel error, which reads like a feature failure.
- Theme preview without persisting: `?theme=daylight` (or orchid/ember) in the URL.
- Reader invariants to re-check after any reader change: `#reader` computed background
  `rgb(0,0,0)` and `#readerMain` color `rgb(255,255,255)` in every theme.
- `graph/fastapi.json` does not exist — graph consumers must tolerate null; use fastapi to
  test that path.

## Flows worth driving

- Open a module from 2–3 different sections via `#/reader/…` deep links.
- Scroll to the bottom of a module (marks it read at >=90%, fires end-of-read closure).
- §12 recall prompts: both Q&A DOM forms exist — `.md-q`+siblings (question on its own line)
  and `.md-qa` (inline question+answer in one paragraph); test a page of each.
- In-page find: `openReaderFind()` then set `.rd-find-input` value + dispatch `input` event.
- Aa popover: `#readerType` button; segments carry `data-k="sd_<pref>"`.
