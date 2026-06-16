#!/usr/bin/env python3
"""
diagram_tools.py — validate and preview ASCII "visual intuition" diagrams.

This repo is pure Markdown with no runnable app. The thing a future agent
needs to "interact with" is a diagram it just wrote: does it render cleanly
in a monospace terminal? This script is that handle.

A *diagram block* is a fenced code block with NO language info string
(```), matching the repo convention (```python / ```sql etc. are CODE,
not diagrams, and are skipped). The checks catch the things that silently
break ASCII alignment or violate repo rules:

  - TAB characters        -> render at different widths everywhere; banned
  - trailing whitespace   -> invisible drift, diff noise
  - emoji                 -> repo rule: no emojis (box-drawing/arrows/checks
                             like | - > <- v ✓ ✗ are allowed and NOT flagged)
  - over-wide lines       -> warn past 100 cols (terminal wrap risk)

Commands:
  list    <paths...>            enumerate diagram blocks (index, line, size, width)
  check   <paths...>            lint diagram blocks; exit 1 if any errors
  preview <file.md> <index>     print one diagram block verbatim for eyeballing

Paths may be files or directories (directories are scanned for *.md).
"""
from __future__ import annotations

import sys
from dataclasses import dataclass
from pathlib import Path


# Emoji / pictograph detection. We deliberately do NOT flag the BMP symbol
# ranges that legitimate diagrams use: box-drawing (U+2500..U+257F), block
# elements/shades (U+2580..U+259F), arrows (U+2190..U+21FF), math operators
# (U+2200..U+22FF), and the text-presentation check marks U+2713/U+2717.
# We flag the emoji planes and the emoji-presentation selector.
def _is_emoji(ch: str) -> bool:
    cp = ord(ch)
    if cp >= 0x1F000:                 # emoji & pictograph planes
        return True
    if cp == 0xFE0F:                  # emoji variation selector
        return True
    if 0x2600 <= cp <= 0x26FF:        # misc symbols (☀ ☂ ⚡ ...) — decorative
        return True
    # common BMP emoji that are NOT in the ranges above
    return cp in {0x2705, 0x274C, 0x2728, 0x2B50, 0x2757, 0x2753}


@dataclass
class Block:
    index: int
    start_line: int        # 1-based line of the opening fence
    lines: list[str]       # body lines (without the fences)

    @property
    def max_width(self) -> int:
        return max((len(ln) for ln in self.lines), default=0)


def extract_diagram_blocks(text: str) -> list[Block]:
    """Return untagged fenced code blocks (the diagram convention)."""
    blocks: list[Block] = []
    in_block = False
    info = ""
    body: list[str] = []
    start = 0
    idx = 0
    for n, raw in enumerate(text.splitlines(), start=1):
        stripped = raw.lstrip()
        if stripped.startswith("```"):
            if not in_block:
                in_block = True
                info = stripped[3:].strip()
                body = []
                start = n
            else:
                # closing fence
                if info == "":                       # untagged == diagram
                    blocks.append(Block(idx, start, body))
                    idx += 1
                in_block = False
        elif in_block:
            body.append(raw)
    return blocks


def _iter_paths(paths: list[str]):
    for p in paths:
        path = Path(p)
        if path.is_dir():
            yield from sorted(path.rglob("*.md"))
        else:
            yield path


def cmd_list(paths: list[str]) -> int:
    for path in _iter_paths(paths):
        blocks = extract_diagram_blocks(path.read_text(encoding="utf-8"))
        if not blocks:
            continue
        print(f"\n{path}  ({len(blocks)} diagram block(s))")
        for b in blocks:
            print(f"  [{b.index:>2}] line {b.start_line:<5} "
                  f"{len(b.lines):>3} lines  max width {b.max_width}")
    return 0


def cmd_check(paths: list[str]) -> int:
    errors = 0
    warnings = 0
    for path in _iter_paths(paths):
        blocks = extract_diagram_blocks(path.read_text(encoding="utf-8"))
        for b in blocks:
            for off, ln in enumerate(b.lines):
                lineno = b.start_line + 1 + off
                if "\t" in ln:
                    print(f"ERROR {path}:{lineno}: tab character in diagram")
                    errors += 1
                if ln != ln.rstrip():
                    print(f"ERROR {path}:{lineno}: trailing whitespace")
                    errors += 1
                emojis = sorted({c for c in ln if _is_emoji(c)})
                if emojis:
                    shown = " ".join(f"{c!r}(U+{ord(c):04X})" for c in emojis)
                    print(f"ERROR {path}:{lineno}: emoji not allowed: {shown}")
                    errors += 1
            if b.max_width > 100:
                print(f"WARN  {path}:{b.start_line}: block [{b.index}] is "
                      f"{b.max_width} cols wide (>100, may wrap in terminals)")
                warnings += 1
    print(f"\n{errors} error(s), {warnings} warning(s)")
    return 1 if errors else 0


def cmd_preview(args: list[str]) -> int:
    if len(args) != 2:
        print("usage: diagram_tools.py preview <file.md> <index>", file=sys.stderr)
        return 2
    path = Path(args[0])
    try:
        target = int(args[1])
    except ValueError:
        print("index must be an integer", file=sys.stderr)
        return 2
    blocks = extract_diagram_blocks(path.read_text(encoding="utf-8"))
    match = next((b for b in blocks if b.index == target), None)
    if match is None:
        print(f"no diagram block [{target}] in {path} "
              f"(has {len(blocks)})", file=sys.stderr)
        return 1
    ruler = "         1         2         3         4         5         6" \
            "         7         8"
    tens = "123456789012345678901234567890123456789012345678901234567890" \
           "12345678901234567890"
    print(ruler)
    print(tens)
    print("-" * 80)
    for ln in match.lines:
        print(ln)
    print("-" * 80)
    print(f"block [{target}] @ {path}:{match.start_line}  "
          f"{len(match.lines)} lines, max width {match.max_width}")
    return 0


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print(__doc__)
        return 2
    cmd, rest = argv[1], argv[2:]
    if cmd == "list":
        return cmd_list(rest or ["."])
    if cmd == "check":
        return cmd_check(rest or ["."])
    if cmd == "preview":
        return cmd_preview(rest)
    print(f"unknown command: {cmd}\n{__doc__}", file=sys.stderr)
    return 2


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
