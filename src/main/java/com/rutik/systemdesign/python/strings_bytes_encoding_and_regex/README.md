# Strings, Bytes, Encoding & Regex

## 1. Concept Overview

Python 3 draws a hard line between text and binary data. `str` represents a sequence of Unicode
code points — abstract characters with no inherent byte representation. `bytes` and `bytearray`
represent raw binary data — a sequence of integers in the range 0–255. Moving between the two
worlds requires an explicit encoding step. Getting that step wrong is responsible for a large class
of production bugs: `UnicodeDecodeError` in file ingestion, garbled content in HTTP responses,
silent data corruption when the wrong codec silently accepts every byte.

Key topics covered in this module:

- `str` internals: PEP 393 compact representation (Latin-1 / UCS-2 / UCS-4), code points vs bytes
- `bytes` and `bytearray`: immutable vs mutable binary sequences
- Unicode, UTF-8, UTF-16, BOM, and common codec pitfalls
- `encode()` / `decode()` with error handlers (`strict`, `replace`, `ignore`, `backslashreplace`)
- `memoryview`: zero-copy slicing of buffer-protocol objects
- String formatting: `%`, `.format()`, f-strings — performance and expressiveness
- The `re` module: `compile`, `match`, `search`, `fullmatch`, `findall`, `finditer`, `sub`
- Named groups, non-capturing groups, lookahead and lookbehind assertions
- Catastrophic backtracking (ReDoS) and how to detect and fix it
- `re.compile()` for performance; the `regex` third-party library for advanced features
- FastAPI context: parsing headers, validating path parameters, decoding request bodies

---

## 2. Intuition

> Python's `str` is a postcard written in a universal alphabet; `bytes` is the same postcard
> after it has been folded into an envelope and stamped — you need to know the folding rule
> (encoding) to unfold it correctly.

**Mental model:** Think of Unicode code points as the logical meaning of a character — the abstract
idea of the letter "A", the Chinese character "你", or the emoji snowman. An encoding (UTF-8, Latin-1,
Shift-JIS) is a concrete recipe that maps each code point to a specific sequence of bytes. The same
character can have completely different byte sequences in different encodings. When you read a file,
a socket, or an HTTP body, you receive bytes; you must know the encoding to produce the correct text.

**Why it matters:** Network sockets, file I/O, databases, and HTTP all operate on bytes. FastAPI
routes receive bytes from the ASGI server and emit bytes to the client. Pydantic validators work on
Python `str` objects after JSON decoding. Every boundary crossing — HTTP body in, JSON out, database
read, file write — involves an encode/decode step. A missing or wrong encoding at any boundary
produces bugs that are difficult to reproduce because they are data-dependent.

**Key insight:** UTF-8 is not the only encoding in the wild. Windows systems frequently produce
files in `cp1252` (Windows-1252). CSV exports from Excel may include a UTF-8 BOM. APIs from the
1990s may use `latin-1`. Python's default `open()` uses the locale encoding, which varies by
platform — meaning code that works on macOS can silently corrupt data on a Windows server.

---

## 3. Core Principles

1. **`str` is Unicode, `bytes` is binary** — they are completely separate types. Python 3 does not
   implicitly coerce between them. Attempting `"hello" + b"world"` raises `TypeError`.

2. **Encoding is always explicit** — call `.encode(encoding)` or `.decode(encoding)` at every
   boundary. Never rely on defaults in production code that processes external data.

3. **UTF-8 is the universal default for new systems** — it is ASCII-compatible (the first 128 code
   points map to single bytes), self-synchronizing, and supported everywhere. Use it unless you have
   a specific reason not to.

4. **`memoryview` for zero-copy** — slicing a `bytes` object allocates a new `bytes` object.
   `memoryview` slicing creates a view into the same buffer. For large payloads (binary files,
   network frames, audio), this matters for both performance and memory usage.

5. **Compile regexes that are called repeatedly** — `re.match(pattern, text)` recompiles the
   pattern on every call (the internal cache has a fixed size of 512 entries in CPython 3.11, but
   cache misses still cost microseconds each). `re.compile()` makes the intent explicit and
   eliminates repeated compilation.

6. **Never apply untrusted regex patterns to untrusted input without bounds** — a crafted regex
   against a crafted input can consume CPU exponentially (ReDoS). Even a crafted pattern alone,
   compiled inside your service, can hang a thread.

7. **f-strings are the preferred formatting style in Python 3.6+** — they are faster than `%` and
   `.format()`, their expressions are evaluated at definition time (avoiding late-binding
   surprises), and they support the full format spec mini-language.

---

## 4. Types / Architectures / Strategies

### 4.1 `str` Representation — PEP 393 Compact Layout

CPython 3 stores `str` internally using the minimum width needed for all characters:

| Internal kind | Width | Condition |
|--------------|-------|-----------|
| Latin-1 (KIND=1) | 1 byte/char | All code points <= U+00FF |
| UCS-2 (KIND=2) | 2 bytes/char | All code points <= U+FFFF (Basic Multilingual Plane) |
| UCS-4 (KIND=4) | 4 bytes/char | Any code point > U+FFFF (supplementary planes) |

`len()` always returns the number of code points, never the number of bytes.

### 4.2 `bytes`, `bytearray`, `memoryview`

| Type | Mutable | Buffer protocol | Typical use |
|------|---------|----------------|-------------|
| `bytes` | No | Yes | Network I/O, hashing, immutable binary constants |
| `bytearray` | Yes | Yes | In-place binary editing, building packets |
| `memoryview` | Depends on base | Yes | Zero-copy slicing of large buffers |

### 4.3 Encoding Strategies

| Encoding | Width | BOM | ASCII-compatible | Common source |
|----------|-------|-----|-----------------|---------------|
| UTF-8 | 1–4 bytes | Optional (U+FEFF) | Yes | Web, Linux, macOS, modern APIs |
| UTF-8-SIG | 1–4 bytes | Always prepended | Yes | Windows Notepad exports |
| UTF-16-LE | 2–4 bytes | Optional | No | Windows COM, .NET internal strings |
| Latin-1 (ISO-8859-1) | 1 byte | None | Yes (subset) | Legacy Western European systems |
| cp1252 | 1 byte | None | Yes (subset) | Windows default Western |

### 4.4 Regex Strategy Patterns

| Strategy | Use case | Example |
|----------|----------|---------|
| `re.match()` | Match from string start | `re.match(r"\d+", "42abc")` |
| `re.search()` | Find first match anywhere | `re.search(r"\d+", "abc42")` |
| `re.fullmatch()` | Entire string must match | Input validation |
| `re.findall()` | All non-overlapping matches as list | Extracting all tokens |
| `re.finditer()` | All matches as iterator of `Match` objects | Large text, need spans |
| `re.sub()` | Replace matches | Sanitizing user content |
| `re.compile()` | Pre-compile for reuse | Hot paths, module-level |

### 4.5 String Formatting Strategies

| Method | Python version | Speed (relative) | Dynamic width support | Self-documenting |
|--------|---------------|-----------------|----------------------|-----------------|
| `%` formatting | All | 1x (baseline) | No | No |
| `.format()` | 2.6+ / 3.0+ | ~0.8x (slower) | Yes | No |
| f-strings | 3.6+ | ~3x faster | Yes | `=` specifier [3.8] |
| `Template` strings | 2.4+ | slowest | No | No |

---

## 5. Architecture Diagrams

```
Text / Binary Boundary
======================
External world                    Python application
(file, socket, HTTP body)         (str objects)
  bytes                               str
[0x48 0x65 0x6C 0x6C 0x6F]  <-->   "Hello"
                               decode("utf-8") / encode("utf-8")

PEP 393 str memory layout:
+-----------+----------+----------+----...----+
| PyObject  | kind     | length   |  data     |
| header    | (1/2/4)  |  (5)     | H e l l o |
+-----------+----------+----------+----...----+
  Latin-1: 1 byte/char; UCS-2: 2 bytes/char; UCS-4: 4 bytes/char

memoryview zero-copy:
+-------------------------------------------+
| bytes object  b"....PAYLOAD.............." |  (heap)
+------------------+---+--------------------+
                   |   |
  mv[offset:end]  <- new view, no data copy
                   v   v
               [PAYLOAD]  <- same memory

re NFA (catastrophic backtracking):
Pattern: (a+)+b   Input: "aaaaa" (no 'b')
  branch 1: (aaaaa)+b -> fail
  branch 2: (aaaa)(a)+b -> fail
  ...  2^n combinations  -> O(2^n) states explored
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 `str` Internals

```python
import sys

s1 = "hello"
s2 = "你好"

print(len(s1))                      # 5  — five code points
print(len(s2))                      # 2  — two code points (not bytes)
print(len(s1.encode("utf-8")))      # 5  — ASCII: 1 byte/char
print(len(s2.encode("utf-8")))      # 6  — CJK: 3 bytes/char in UTF-8

# PEP 393 internal width — 1 / 2 / 4 bytes per code point
print(sys.getsizeof("a" * 100))            # 149  — 1 byte/char + header
print(sys.getsizeof("中" * 100))           # 250  — 2 bytes/char + header
print(sys.getsizeof("\U0001F600" * 100))   # 448  — 4 bytes/char + header
```

### 6.2 `encode()` / `decode()` and Error Handlers

```python
text = "Café"

utf8_bytes   = text.encode("utf-8")    # b'Caf\xc3\xa9'  — é = 2 bytes
latin1_bytes = text.encode("latin-1")  # b'Caf\xe9'       — é = 1 byte

print(utf8_bytes.decode("utf-8"))      # "Café"
print(latin1_bytes.decode("latin-1")) # "Café"

# Error handlers on bad bytes
messy = b"Caf\xe9 is great"           # valid latin-1, invalid UTF-8
print(messy.decode("utf-8", errors="replace"))           # "Caf? is great"
print(messy.decode("utf-8", errors="ignore"))            # "Caf is great"
print(messy.decode("utf-8", errors="backslashreplace"))  # "Caf\\xe9 is great"

# latin-1 silently accepts all bytes — masks encoding bugs
everything = bytes(range(256))
everything.decode("latin-1")  # never raises; correct for latin-1, corrupts UTF-8 data
```

### 6.3 BOM and UTF-8-SIG

```python
# UTF-8 BOM: three bytes \xef\xbb\xbf prepended by some Windows tools
bom_bytes = b"\xef\xbb\xbfhello"

# Wrong: treating as plain UTF-8 leaves the BOM character in the string
wrong = bom_bytes.decode("utf-8")
print(repr(wrong))   # '﻿hello'  — BOM is U+FEFF ZERO WIDTH NO-BREAK SPACE

# Correct: use utf-8-sig codec, which strips/adds BOM automatically
correct = bom_bytes.decode("utf-8-sig")
print(repr(correct))  # 'hello'

# Writing with BOM for Windows Excel compatibility
with open("output.csv", "w", encoding="utf-8-sig") as f:
    f.write("name,value\n")
```

### 6.4 `memoryview` — Zero-Copy Slicing

```python
# bytes slicing creates a copy each time
data = b"A" * (10 * 1024 * 1024)  # 10 MB
chunk = data[0:4096]              # allocates 4096 new bytes

# memoryview slicing creates a view — no allocation
mv = memoryview(data)
chunk_view = mv[0:4096]          # zero-copy view into the same buffer

# Practical use: reading frames without copying
def process_frames(buffer: bytes, frame_size: int = 4096) -> list[memoryview]:
    mv = memoryview(buffer)
    return [mv[i : i + frame_size] for i in range(0, len(mv), frame_size)]

# Works with mutable bytearray too
ba = bytearray(b"hello world")
mv2 = memoryview(ba)
mv2[0:5] = b"HELLO"        # writes back to ba in-place
print(ba)                   # bytearray(b'HELLO world')
```

### 6.5 f-string Formatting

```python
from decimal import Decimal

price = Decimal("19.99")
name, width = "widget", 10

print(f"Item: {name}, Price: {price:.2f}")   # "Item: widget, Price: 19.99"
print(f"{name:<{width}}")    # "widget    " — left-aligned, dynamic width
print(f"{name:>{width}}")    # "    widget" — right-aligned
print(f"{'café'!r}")         # "'café'"  — repr conversion
print(f"{'café'!a}")         # "'caf\\xe9'" — ASCII-safe repr

x = 42
print(f"{x = }")             # "x = 42"  — self-documenting [3.8+]

precision = 3
print(f"{3.14159265:{width}.{precision}f}")  # "     3.142" — nested format spec
```

### 6.6 The `re` Module in Depth

```python
import re
from typing import Optional

# Module-level compile — one-time cost, zero overhead per call
EMAIL_RE = re.compile(
    r"^[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}$",
    re.IGNORECASE,
)

def validate_email(addr: str) -> bool:
    return EMAIL_RE.fullmatch(addr) is not None  # fullmatch, not match

# Named groups
DATE_RE = re.compile(r"(?P<year>\d{4})-(?P<month>\d{2})-(?P<day>\d{2})")

def parse_date(text: str) -> Optional[dict[str, str]]:
    m = DATE_RE.search(text)
    return m.groupdict() if m else None  # {"year": "2024", "month": "03", "day": "15"}

# Non-capturing groups — alternation without capture overhead
IP_RE = re.compile(r"(?:\d{1,3}\.){3}\d{1,3}")
print(IP_RE.findall("192.168.1.1 and 10.0.0.5"))  # ['192.168.1.1', '10.0.0.5']

# Lookbehind: extract amounts after '$'
AMOUNT_RE = re.compile(r"(?<=\$)\d+(?:\.\d{2})?")
print(AMOUNT_RE.findall("Total: $19.99 and $5.00"))  # ['19.99', '5.00']

# Negative lookahead
print(re.findall(r"foo(?!bar)", "foobar foobaz"))  # ['foo'] — only in foobaz

# VERBOSE pattern — inline documentation for complex regex
EMAIL_VERBOSE = re.compile(r"""
    ^                       # start of string
    (?P<user>[^@]+)         # local part
    @                       # separator
    (?P<domain>[^@]+)       # domain
    $                       # end of string
""", re.VERBOSE)

# finditer — memory-efficient over large text (iterator, not full list)
def extract_urls(text: str) -> list[str]:
    _URL_RE = re.compile(r"https?://[^\s\"'<>]+")
    return [m.group() for m in _URL_RE.finditer(text)]
```

### 6.7 Catastrophic Backtracking (ReDoS)

```python
import re

# DANGEROUS: (a+)+b — each partition of 'a' chars is tried; O(2^n) paths
dangerous = re.compile(r"(a+)+b")
dangerous.match("aaab")       # fine — matches quickly
# dangerous.match("a" * 30)  # DO NOT RUN — hangs for minutes (exponential)

# SAFE: rewrite without nested quantifiers
safe = re.compile(r"a+b")    # linear NFA — single pass

# Third-party `regex` library: atomic groups prevent backtracking
# import regex
# safe_atomic = regex.compile(r"(?>a+)+b")  # committed match, no backtrack

# Another common ReDoS structure: r"(\w+\s+)+" on long word sequences
# Fix: r"\w+(?:\s+\w+)*" — unambiguous, linear
```

---

## 7. Real-World Examples

### 7.1 FastAPI Route: Parsing a CSV Upload with Encoding Detection

```python
from fastapi import FastAPI, UploadFile, HTTPException
import csv
import io

app = FastAPI()

ALLOWED_ENCODINGS = ("utf-8", "utf-8-sig", "latin-1", "cp1252")

@app.post("/upload/csv")
async def upload_csv(file: UploadFile) -> dict[str, int]:
    raw: bytes = await file.read()

    # Try encodings in order of likelihood
    text: str | None = None
    for enc in ALLOWED_ENCODINGS:
        try:
            text = raw.decode(enc)
            break
        except (UnicodeDecodeError, LookupError):
            continue

    if text is None:
        raise HTTPException(status_code=400, detail="Could not decode file with known encodings")

    reader = csv.DictReader(io.StringIO(text))
    rows = list(reader)
    return {"rows": len(rows)}
```

### 7.2 Header Parsing with Named Groups

```python
import re

# RFC 7230 Content-Type: text/html; charset=UTF-8
CONTENT_TYPE_RE = re.compile(
    r"^(?P<mime>[^;]+?)(?:\s*;\s*charset=(?P<charset>[^\s;]+))?$",
    re.IGNORECASE,
)

def parse_content_type(header: str) -> tuple[str, str]:
    m = CONTENT_TYPE_RE.fullmatch(header.strip())
    if not m:
        return "application/octet-stream", "utf-8"
    mime    = m.group("mime").strip().lower()
    charset = (m.group("charset") or "utf-8").lower().replace("-", "_")
    return mime, charset

print(parse_content_type("text/html; charset=UTF-8"))   # ('text/html', 'utf_8')
print(parse_content_type("application/json"))            # ('application/json', 'utf-8')
```

---

## 8. Tradeoffs

### 8.1 `bytes` Slicing vs `memoryview`

| Concern | `bytes` slicing | `memoryview` slicing |
|---------|-----------------|---------------------|
| Memory allocation | New `bytes` object per slice | Zero — view into same buffer |
| Garbage collector pressure | High for many slices | Negligible |
| API compatibility | Accepted everywhere | Requires buffer-protocol support |
| Code clarity | Simple `data[a:b]` | `mv = memoryview(data); mv[a:b]` |
| Break-even point | Small buffers (<1 KB) | Large buffers (>100 KB) |

### 8.2 `re` vs `regex` Third-Party Library

| Feature | `re` (stdlib) | `regex` (third-party) |
|---------|--------------|----------------------|
| Possessive quantifiers (`a++`) | No | Yes |
| Atomic groups (`(?>...)`) | No | Yes |
| Unicode categories (`\p{Lu}`) | No (`\w` is Unicode-aware) | Yes |
| Variable-length lookbehind | No | Yes |
| ReDoS protection mechanisms | None built-in | Atomic groups help |
| Installation | None | `pip install regex` |
| Speed | Fast | Comparable or faster on complex patterns |

### 8.3 String Formatting

| Method | Readability | Runtime safety | Dynamic spec | Performance |
|--------|-------------|---------------|-------------|------------|
| `%` | Low | No (key errors at runtime) | No | Baseline |
| `.format()` | Medium | No (key errors at runtime) | Yes | ~80% of baseline |
| f-strings | High | Compile-time (syntax errors caught) | Yes | ~300% of baseline |
| `Template` | Medium | Safe (no arbitrary expressions) | No | Slowest |

---

## 9. When to Use / When NOT to Use

### Use `str` when:
- Representing human-readable text in any language
- Logging, generating API responses, building SQL strings (via parameterization)
- Any operation that is logically about characters: splitting on punctuation, case folding, searching for words

### Use `bytes` / `bytearray` when:
- Reading from or writing to network sockets, files opened in binary mode, or subprocess pipes
- Hashing, encryption, serialization (struct, protobuf, msgpack)
- Any data that is inherently binary: images, audio, compressed archives

### Use `memoryview` when:
- Slicing large binary buffers repeatedly (video frames, audio samples, large file chunks)
- Passing sub-slices to C extensions or socket `send()` without copying
- Implementing zero-copy parsers for binary protocols

### Use `re.compile()` when:
- The same pattern is used more than once in the lifetime of the process
- The pattern is used inside a function called in a hot loop
- The pattern is complex enough that the intent should be documented with `re.VERBOSE`

### Do NOT use `re` when:
- Simple prefix/suffix checks: use `str.startswith()`, `str.endswith()` (faster)
- Fixed substring search: use `in` operator or `str.find()` (no backtracking overhead)
- Parsing nested structures: use a proper parser (e.g., `pyparsing`, `lark`, `antlr4`)
- Parsing HTML or XML: never parse HTML with regex; use `lxml`, `html.parser`, or `BeautifulSoup`

### Do NOT use f-strings when:
- The template comes from user input or a database — eval-safety concern; use `.format_map()` with a restricted mapping
- Generating SQL or shell commands — use parameterized queries and `shlex.quote()` instead

---

## 10. Common Pitfalls

### Pitfall 1: Default File Encoding on Windows

BROKEN — relies on locale encoding, silently corrupts UTF-8 content on Windows (cp1252 default):

```python
# BROKEN
def read_config(path: str) -> str:
    with open(path) as f:       # encoding defaults to locale — cp1252 on Windows
        return f.read()         # silently replaces multibyte UTF-8 sequences
```

FIX — always specify encoding explicitly:

```python
# FIXED
def read_config(path: str) -> str:
    with open(path, encoding="utf-8") as f:
        return f.read()
```

For Python 3.10+, set `PYTHONUTF8=1` environment variable as a belt-and-suspenders measure, but
always set encoding explicitly in `open()` — never rely on the environment alone.

---

### Pitfall 2: String Concatenation in a Loop (O(n²) Copies)

BROKEN — each `+` creates a new `str` object, copying all previous content. For n iterations and
average length L, total bytes copied = L + 2L + 3L + ... + nL = O(n²·L):

```python
# BROKEN — O(n²) string copies
def build_report(records: list[dict]) -> str:
    result = ""
    for rec in records:
        result += f"ID={rec['id']} NAME={rec['name']}\n"  # new str object each time
    return result
```

FIX — collect into a list and join once. `"".join()` internally computes the total length in one
pass, allocates once, and fills in O(n):

```python
# FIXED — O(n) single allocation
def build_report(records: list[dict]) -> str:
    parts: list[str] = []
    for rec in records:
        parts.append(f"ID={rec['id']} NAME={rec['name']}\n")
    return "".join(parts)
```

Benchmark: for 10,000 records of 50 chars each, the join approach is approximately 40x faster,
allocating a single 500 KB string instead of accumulating ~2.5 GB of intermediate allocation work.

---

### Pitfall 3: `re.match()` Without `re.compile()` in a Hot Loop

BROKEN — `re.match()` recompiles (or searches a bounded cache) on every call:

```python
# BROKEN — recompiles or cache-looks-up pattern on every call
def count_valid_emails(emails: list[str]) -> int:
    pattern = r"^[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}$"
    return sum(1 for e in emails if re.match(pattern, e))
```

FIX — compile once at module level:

```python
# FIXED — compiled once, O(1) lookup on every call
_EMAIL_RE = re.compile(
    r"^[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}$",
    re.IGNORECASE,
)

def count_valid_emails(emails: list[str]) -> int:
    return sum(1 for e in emails if _EMAIL_RE.match(e))
```

For a batch of 100,000 emails, pre-compilation saves roughly 15–25 ms on CPython 3.11 (varies by
pattern complexity and cache hit rate).

---

### Pitfall 4: Using `re.match()` When `re.fullmatch()` Is Needed for Validation

```python
import re

# BROKEN — re.match() only anchors at the START; trailing garbage passes
pattern = re.compile(r"\d{4}-\d{2}-\d{2}")
print(pattern.match("2024-01-15 extra stuff"))  # Match! Should be rejected.

# FIXED — re.fullmatch() requires the entire string to match
print(pattern.fullmatch("2024-01-15 extra stuff"))  # None — correctly rejected
print(pattern.fullmatch("2024-01-15"))              # Match object — accepted
```

---

### Pitfall 5: Applying `latin-1` as a "Safe" Fallback

```python
# DANGEROUS: latin-1 decodes every byte without error, masking real encoding problems
raw = b"\xe4\xb8\xad\xe6\x96\x87"  # "中文" in UTF-8
wrong = raw.decode("latin-1")       # No error, but produces "ä¸­æ–‡" — garbage
correct = raw.decode("utf-8")       # "中文"
```

Use `errors="replace"` with UTF-8 when you expect mostly UTF-8 but may have isolated bad bytes,
and always log or alert when replacements occur.

---

## 11. Technologies & Tools

| Tool | Type | ReDoS safe | Unicode categories | Speed | Best use |
|------|------|-----------|-------------------|-------|----------|
| `re` | stdlib | No | Limited (`\w` is Unicode) | Fast | Standard text patterns, input validation |
| `regex` | third-party pip | Atomic groups help | Full `\p{Lu}` etc. | Comparable | Complex Unicode, possessive quantifiers |
| `fnmatch` | stdlib | N/A (glob only) | No | Very fast | File glob matching (`*.py`) |
| `pyparsing` | third-party pip | N/A (PEG parser) | Via Python | Slower | Grammar-based parsing, DSLs |
| `lark` | third-party pip | N/A (Earley/LALR) | Via Python | Fast on compiled | Complex grammars, language parsing |
| `hypothesis` | third-party pip | N/A | N/A | N/A | Property-based testing of regex |

**Python version notes:**
- `re.fullmatch()` added in Python 3.4.
- `re.Pattern` and `re.Match` as proper generic types for annotations: Python 3.8+.
- `re.compile()` cache size: 512 entries (CPython 3.11+, up from 100 in earlier versions).
- f-strings: Python 3.6; `f"{x = }"` self-doc: Python 3.8; multi-line f-string with backslash in expression: Python 3.12 (PEP 701).

---

## 12. Interview Questions with Answers

**Q1: What is the difference between `str` and `bytes` in Python 3, and why does the distinction matter for a FastAPI service?**
`str` is a sequence of Unicode code points; `bytes` is a sequence of raw octets. The distinction
matters because the ASGI interface delivers HTTP bodies as `bytes`. FastAPI (via Starlette) decodes
the body to `str` only for JSON and form data endpoints. A file upload or a streaming endpoint
works with raw `bytes`. Confusing the two causes `TypeError` at runtime and can silently corrupt
data if an implicit coercion somehow occurs (it never does in Python 3, which raises immediately).

**Q2: What does `len("你好")` return and why?**
It returns 2. `len()` on a `str` counts Unicode code points, not bytes. "你" is U+4F60 and "好" is
U+597D — two code points. Their UTF-8 encoding is 3 bytes each (6 bytes total), but `len()` does
not know or care about the encoding. Use `len(s.encode("utf-8"))` to get the byte count.

**Q3: What is PEP 393 and why does it matter for memory efficiency?**
PEP 393 (CPython 3.3) introduced "compact" string storage: CPython picks the narrowest width (1, 2,
or 4 bytes per code point) based on the highest code point in the string. A pure-ASCII 1 000-char
string uses 1 000 bytes of data; adding a single emoji widens every character to 4 bytes, costing
4 000 bytes. A corpus of ASCII product names is 4x smaller than if stored as UCS-4.

**Q4: What is the UTF-8 BOM and how do you handle it in Python?**
The BOM is U+FEFF, encoded as `\xef\xbb\xbf` in UTF-8. Windows tools (Notepad, Excel) prepend it.
Decoding with `"utf-8"` leaves a `﻿` character at position 0; `"utf-8-sig"` strips it on read
and adds it on write. Use `"utf-8-sig"` when consuming Windows CSV files or producing Excel-compatible
output.

**Q5: Explain `memoryview` and when you would use it instead of slicing a `bytes` object.**
`memoryview` is a zero-copy view into any buffer-protocol object (`bytes`, `bytearray`,
`array.array`, NumPy). Slicing a `memoryview` creates a new view into the same memory — no
allocation. Slicing `bytes` allocates and copies each time. Use it when making many slices of a
large buffer: binary protocol parsing, video frames, `socket.send()` of sub-ranges. Break-even is
roughly 1 KB; below that, copy overhead is negligible.

**Q6: What is the difference between `re.match()`, `re.search()`, and `re.fullmatch()`?**
`re.match()` anchors only at the start but does not require consuming the full string. `re.search()`
scans for the first match anywhere in the string. `re.fullmatch()` requires the pattern to span the
entire string. For input validation always use `re.fullmatch()` — `re.match()` silently accepts
trailing garbage after a valid prefix.

**Q7: What are named groups in regex and how do you use them?**
Named groups use the syntax `(?P<name>...)`. After a match, `m.group("name")` and `m.groupdict()`
return the captured text by name. Named groups make patterns self-documenting and protect code from
breaking when groups are reordered. Example: `r"(?P<year>\d{4})-(?P<month>\d{2})-(?P<day>\d{2})"`.
In `re.sub()`, named back-references are written as `\g<name>`.

**Q8: What is catastrophic backtracking and how do you prevent it?**
Catastrophic backtracking occurs when an NFA regex engine explores O(2^n) paths on a non-matching
input, caused by ambiguous nested quantifiers like `(a+)+`. Prevention: (1) rewrite the pattern —
`(a+)+` becomes `a+`; (2) use the `regex` library's atomic groups `(?>a+)+` to prevent backtracking
into a committed match; (3) for user-supplied patterns, run matching in a subprocess with a
hard wall-clock timeout and kill the process if it exceeds it.

**Q9: Why should `re.compile()` be called at module level, and what is the internal cache in the `re` module?**
`re.compile()` translates the pattern into a compiled `re.Pattern` object once. Without it,
`re.match(pattern_str, text)` checks an internal LRU cache (512 entries in CPython 3.11); a cache
hit still pays a dict-lookup cost, and a miss triggers full recompilation. Module-level
`re.compile()` is zero overhead at call time, enables `re.VERBOSE` for inline documentation, and
makes the intent explicit to readers.

**Q10: What are non-capturing groups and why use them over capturing groups?**
`(?:...)` groups sub-expressions without allocating a numbered back-reference or storing the match.
Use them for alternation or repetition when the captured text is not needed: `(?:jpg|png)+`.
They are slightly faster (no capture bookkeeping), keep `m.groups()` uncluttered, and prevent
consumers from accidentally depending on a specific group number.

**Q11: What does the `re.DOTALL` flag do and when is it needed?**
By default `.` matches any character except `\n`. With `re.DOTALL` (alias `re.S`), `.` also
matches newlines. Use it when the match target spans multiple lines — extracting a multi-line block
from HTML, or capturing a JSON value that contains embedded newlines. Without it, `.+` truncates at
the first `\n`.

**Q12: How does f-string performance compare to `.format()` and `%` formatting?**
f-strings are the fastest of the three. On CPython 3.11, micro-benchmarks show f-strings are
roughly 3x faster than `.format()` and 1.5–2x faster than `%`. f-strings compile to
`FORMAT_VALUE` / `BUILD_STRING` bytecode opcodes; `.format()` requires a method call plus runtime
format-string parsing; `%` scans the format string at runtime. For most web services the delta is
negligible, but in tight loops generating thousands of log lines or CSV rows per second it adds up.

---

## 13. Best Practices

1. **Always specify `encoding=` in `open()` calls.** Default locale encoding varies by platform.
   Set `encoding="utf-8"` unless you have a specific reason to use another encoding. For consuming
   Windows-generated CSV, use `encoding="utf-8-sig"`.

2. **Use UTF-8 as the canonical encoding for all new files, APIs, and database columns.** Set
   `character set utf8mb4` in MySQL; `UTF8` in PostgreSQL default. Return `Content-Type:
   application/json; charset=utf-8` from all FastAPI endpoints.

3. **Use `re.fullmatch()` for input validation, never `re.match()`.** Add the pattern to a
   module-level compiled constant with `re.compile()` and a descriptive name.

4. **Validate or sandbox user-supplied regex patterns.** At minimum: cap pattern length (e.g.,
   200 characters), run in a thread or process with a timeout (100–500 ms), and reject patterns
   with known dangerous constructs (`(.*)+`, `(\w+)+`, etc.) via a static pre-check.

5. **Prefer `"".join(parts)` over `result += fragment` in loops.** This applies to both `str`
   and `bytes` building. The join idiom is O(n); repeated concatenation is O(n²).

6. **Use `memoryview` for binary data that is sliced repeatedly.** Wrap large byte buffers with
   `memoryview()` before entering a parsing loop. Release the view with `mv.release()` or use a
   `with` block when finished to return the buffer to the GC.

7. **Use `errors="replace"` or `errors="backslashreplace"` when decoding untrusted bytes** and log
   or alert when replacements occur. Never silently swallow encoding errors in data pipelines —
   they indicate upstream data quality issues.

8. **Use named groups `(?P<name>...)` in complex patterns.** Named groups make code resilient to
   group-number changes when the pattern is modified, and they produce self-documenting
   `groupdict()` results.

9. **Apply `re.VERBOSE` for patterns longer than one line.** Inline comments and whitespace make
   the intent clear, reducing the maintenance burden for future reviewers.

10. **Test regex patterns with `hypothesis`.** Property-based testing discovers inputs that match
    unexpectedly, inputs that should match but do not, and — critically — inputs that trigger
    catastrophically slow matching.

---

## 14. Case Study

### Building a Safe User-Input Regex Validator in FastAPI

**Context:** A developer tooling platform lets users define text extraction rules as regex patterns.
A malicious or careless user can submit `(.*)+$`, which takes exponential time on a moderately long
document. The endpoint must validate syntax, check for ReDoS-prone structures, and enforce a hard
runtime limit before returning match results.

---

#### BROKEN Implementation (Vulnerable to ReDoS)

```python
# BROKEN — do not deploy this
import re
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

app = FastAPI()

class RuleRequest(BaseModel):
    pattern: str
    test_input: str

@app.post("/rules/validate")
def validate_rule(req: RuleRequest) -> dict:
    # PROBLEM 1: no length check — user can submit a 10 MB pattern
    # PROBLEM 2: re.compile raises re.error for bad syntax, but succeeds for ReDoS patterns
    try:
        compiled = re.compile(req.pattern)          # compiles; (a+)+b is valid syntax
    except re.error as exc:
        raise HTTPException(status_code=400, detail=str(exc))

    # PROBLEM 3: applying (a+)+b to a long string of 'a' chars hangs the event loop thread
    match = compiled.search(req.test_input)         # blocks forever on crafted input

    return {"matched": match is not None, "span": match.span() if match else None}
```

Why this is dangerous: `re.compile(r"(a+)+b")` succeeds — ReDoS patterns are syntactically valid.
`compiled.search("a" * 50)` blocks for minutes to hours. In Uvicorn's sync worker thread, one
such request stalls all other requests handled by that thread.

---

#### FIX — Safe Implementation with Process Isolation and Timeout

```python
# FIXED — production-safe user-regex endpoint
import re
import signal
import multiprocessing
from typing import Any
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

app = FastAPI()

# Safety limits
MAX_PATTERN_LEN     = 200    # characters
MAX_INPUT_LEN       = 2_000  # characters
MATCH_TIMEOUT_SECS  = 0.5    # 500 ms wall-clock limit

# Static blocklist of structural ReDoS indicators (heuristic, not exhaustive)
REDOS_INDICATORS = re.compile(
    r"""
    (\(.*?[+*]\))+  |   # nested quantifier on a group
    (\.\*){2,}          # two or more .* in sequence
    """,
    re.VERBOSE,
)

class RuleRequest(BaseModel):
    pattern: str
    test_input: str

def _apply_regex(pattern: str, text: str, result_queue: "multiprocessing.Queue[Any]") -> None:
    """Worker function executed in a child process."""
    try:
        compiled = re.compile(pattern)
        m = compiled.search(text)
        if m:
            result_queue.put({"matched": True, "span": list(m.span()), "group": m.group()})
        else:
            result_queue.put({"matched": False, "span": None, "group": None})
    except Exception as exc:
        result_queue.put({"error": str(exc)})

def safe_regex_apply(pattern: str, text: str) -> dict:
    """
    Run regex matching in a child process with a hard wall-clock timeout.
    If the child does not return within MATCH_TIMEOUT_SECS, it is killed and
    we raise HTTPException 400.
    """
    ctx = multiprocessing.get_context("spawn")   # fork-safe on all platforms
    q: multiprocessing.Queue = ctx.Queue()
    proc = ctx.Process(target=_apply_regex, args=(pattern, text, q))
    proc.start()
    proc.join(timeout=MATCH_TIMEOUT_SECS)

    if proc.is_alive():
        proc.kill()
        proc.join()
        raise HTTPException(
            status_code=400,
            detail=(
                f"Pattern timed out after {MATCH_TIMEOUT_SECS}s. "
                "The pattern may be vulnerable to catastrophic backtracking."
            ),
        )

    if q.empty():
        raise HTTPException(status_code=500, detail="Regex worker returned no result")

    result = q.get_nowait()
    if "error" in result:
        raise HTTPException(status_code=400, detail=f"Regex error: {result['error']}")

    return result

@app.post("/rules/validate")
def validate_rule(req: RuleRequest) -> dict:
    # Step 1: length guards
    if len(req.pattern) > MAX_PATTERN_LEN:
        raise HTTPException(
            status_code=400,
            detail=f"Pattern too long: {len(req.pattern)} chars (max {MAX_PATTERN_LEN})",
        )
    if len(req.test_input) > MAX_INPUT_LEN:
        raise HTTPException(
            status_code=400,
            detail=f"Input too long: {len(req.test_input)} chars (max {MAX_INPUT_LEN})",
        )

    # Step 2: static heuristic check for obvious ReDoS structures
    if REDOS_INDICATORS.search(req.pattern):
        raise HTTPException(
            status_code=400,
            detail="Pattern contains potentially unsafe nested quantifiers.",
        )

    # Step 3: syntax check
    try:
        re.compile(req.pattern)
    except re.error as exc:
        raise HTTPException(status_code=400, detail=f"Invalid regex: {exc}")

    # Step 4: apply with process isolation and timeout
    result = safe_regex_apply(req.pattern, req.test_input)
    return result
```

#### Operational Notes

| Concern | Mitigation |
|---------|-----------|
| Pattern length | Hard cap at 200 chars; return 400 immediately |
| Structural ReDoS | Heuristic blocklist on nested quantifiers (first line of defense) |
| Algorithmic ReDoS | Process isolation + 500 ms `join()` timeout (second line of defense) |
| Worker startup cost | `multiprocessing.spawn` ~30–80 ms; acceptable for a validation endpoint |
| Production alternative | Use the `regex` library's atomic groups in the worker to eliminate backtracking for known patterns |

**Why `multiprocessing` and not `threading`?** A CPU-spinning regex in a thread holds the GIL and
blocks all other threads in the same process. A subprocess has its own GIL and is killed cleanly
by `proc.kill()`. The 30–80 ms spawn cost on `"spawn"` context is acceptable for a validation
endpoint. In high-traffic services, use a pre-warmed `ProcessPoolExecutor` and
`submit(...).result(timeout=0.5)` to reduce per-request overhead to under 5 ms.
