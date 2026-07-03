# CLAUDE.md — Game (browser app, template-exempt)

The `game/` directory is an application, not study content — see
[README.md](README.md) for architecture, features, and how to run it.

## Hard limits (user-set — do NOT change these, in any theme or redesign)

1. **The reader background is pitch black `#000000`, opaque, in EVERY theme.**
   Never translucent, never glass, never theme-tinted. The `--rd-bg` token in
   `style.css` must stay `#000000`, and no `[data-theme=...]` block may
   override the reader surface tokens.
2. **Reader prose text is `#e6e6e6`** — exactly 10% below full white
   (`--rd-text`). Code blocks, ASCII diagrams, and Mermaid canvases keep the
   One Dark palette on their dark islands.

## Other invariants

- Vanilla JS + CSS, no build step, no frameworks, no external deps (the reader's
  mermaid CDN import is the only exception).
- **Pages-only (2026-07-03): `localStorage` `sd_progress` is the single source of
  truth for all progress.** There is no server, no `/api`, no local scheduling
  stack. The reader fetches content at `../<path>`; export/import from the Progress
  screen moves progress between browsers.
- All app colors flow from tokens in `:root` / `[data-theme=...]` blocks in
  `style.css`; themes are set via `data-theme` on `<html>` (`?theme=` URL
  param previews without persisting).
- Question ids are content-stable hashes (`module#md5(module|question)[:12]`);
  never revert to position-based ids — they orphan spaced-repetition state.
- Re-run `python3 extract.py` after editing any module's Q&A.
