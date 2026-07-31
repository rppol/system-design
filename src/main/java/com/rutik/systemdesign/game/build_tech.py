"""Technology bank + technology index — everything behind the game's Technologies screen.

Split out of extract.py on 2026-07-31. extract.py's job is the QUESTION BANK; this file's
job is "what is each tool, and which modules teach it". They shared a file only because
they happened to be written by the same pass, and the coupling turned out to be one-way:
nothing here is called from the question extractor except three entry points in main().

Two artifacts come out of here, both GENERATED and gitignored, both rebuilt by CI on every
push exactly like questions/*.json:

  tech_index.json        the BANK — what each tool IS. Source: technologies/tech_bank/*.md
                         (a taxonomy file plus 18 tier shards), authored and committed.
  questions/tech.json    the INDEX — which module teaches which tool, with that module's
                         one-line purpose for it, plus a per-module tradeoff digest and the
                         precomputed search haystack the Technologies screen renders from.

The bank directory is excluded from BOTH repo walks by exact path (see TECH_BANK_DIR /
in_tech_bank, imported by extract.py for its own walk). That exclusion is load-bearing and
silent by design — as a plain module dir the bank becomes a phantom module that --strict
waves through. See technologies/CLAUDE.md for the full authoring contract.
"""

import os
import re
from datetime import datetime, timezone

from build_common import (
    BASE_DIR,
    GAME_DIR,
    SHORT_MAX,
    SHORT_MIN,
    SKIP_PATH_PARTS,
    SKIP_SECTIONS,
    STUDY_ORDER_RE,
    _section_arrays,
)

# The technology KNOWLEDGE BANK: authored DATA, not study content. It lives inside a real
# bank section (technologies/ has STUDY_ORDER entries), so it must be excluded by EXACT
# relative path from BOTH walks -- main()'s Q&A/file-tree walk and build_tech_index()'s
# §11/§8 walk. Three hazards, all measured, all silent:
#   * as a plain module dir it passes --strict yet lands in index.files with
#     moduleCounts 0 -> a PHANTOM module in the reader tree, prev/next chain, command
#     palette and Study skill tree;
#   * a `## 11. Technologies & Tools` table inside it makes build_tech_index() index the
#     bank INTO ITSELF (3809/613 -> 3810/614, no error);
#   * at the section ROOT instead, one stray `## 12. Interview Questions` heading collapses
#     `module` to the 1-segment "technologies" and check_wiring() kills the deploy.
# An exact path, NOT a name in SKIP_PATH_PARTS: a name rule would silently swallow a
# directory called tech_bank in any of the 16 sections.
TECH_BANK_DIR = os.path.join("technologies", "tech_bank")


def in_tech_bank(rel):
    """True for the tech_bank directory itself and anything nested under it."""
    return rel == TECH_BANK_DIR or rel.startswith(TECH_BANK_DIR + os.sep)

# ---- technology KNOWLEDGE BANK (tech_index.json) ---------------------------
#
# What each tool IS and what problems it solves, independent of which module happens to
# teach it. The SOURCE is markdown -- technologies/tech_bank/*.md -- and tech_index.json
# is generated from it, gitignored, and rebuilt by CI exactly like the question banks,
# paths.json and the pre-rendered Mermaid assets. It used to be a committed blob because
# deriving a tool's roles needs a judgement pass; that judgement now lives in the markdown,
# so the blob is an artifact again.
#
# The record contract deliberately mirrors the Q&A `**Short:**` contract, bound included:
# the short line is the always-visible row text (so it must stand alone at 15-220 chars),
# and the prose beneath it is the reveal. Both are per-record opt-in, so the description
# pass can run shard by shard for as long as it takes without any intermediate state
# being broken.
TB_H2 = re.compile(r"^##\s+(.+?)\s*$")
TB_REC = re.compile(r"^###\s+(.+?)\s*$")
TB_SUB = re.compile(r"^####\s+(.+?)\s*$")
TB_FLD = re.compile(r"^\*\*([A-Za-z]+):\*\*\s*(.*?)\s*$")
TB_ROLE = re.compile(r"^(\S+/\S+)\s*@([123])$")
# A `## NN.` section-template heading is the signature of someone mistaking this data
# directory for study content. The exclusion from both walks is silent by design, which
# is exactly what makes it a trap -- so say so loudly instead.
TB_TEMPLATE_HDR = re.compile(r"^##\s+\d+[.)]\s")
# Descriptions are ESCAPED and rendered as <p>, with one backtick->code pass. Anything
# else would leak as literal characters, so reject it at build time rather than ship it.
# The `#{1,6} ` arm catches a markdown heading a bulk authoring pass drops in to organise
# a long description: `###` opens a new RECORD so it can never reach here, but `##` and
# below survive the parser and ship as a literal "## Overview" in the reader.
TB_BAD_DESC = re.compile(r"(?:```|~~~)|^\s*(?:[-*+]|\d+[.)])\s|^\s*\||\]\(|^\s*#{1,6}\s")
# Repo-wide rule: no emojis in any file. Descriptions are the one place a bulk authoring
# pass is tempted to add them, and the reader escapes rather than strips, so they ship.
# U+2713..U+2718 is carved OUT of the dingbats range on purpose: the repo rule is "use
# the/x marks, not the emoji ones", so banning the whole block would fail the build on
# content the style guide asks for. Typographic marks and box-drawing stay out entirely.
TB_EMOJI = re.compile("[\U0001F300-\U0001FAFF☀-✒✙-➿⬀-⯿️]")


def _tb_paragraphs(lines):
    """Hard-wrapped markdown prose -> the paragraph list app.js splits on "\\n\\n".
    Wrapped lines inside one paragraph are joined with a space; a blank line starts a
    new paragraph. Without the join the literal newlines survive into the escaped HTML
    and into the search haystack."""
    paras, cur = [], []
    for line in list(lines) + [""]:
        if line.strip():
            cur.append(line.strip())
        elif cur:
            paras.append(" ".join(cur))
            cur = []
    return paras


def _tb_taxonomy(path, errs):
    """tech_bank.md -> (langs, tiers, kinds). Order is file order, so the tier shelf and
    the kind/language selects render in the sequence the taxonomy was authored in."""
    kinds, langs, tiers = [], [], []
    sec, cur, role = None, None, None
    for n, line in enumerate(open(path, encoding="utf-8").read().split("\n"), 1):
        if TB_TEMPLATE_HDR.match(line):
            errs.append(f"tech_bank.md:{n} `## NN.` section-template heading -- "
                        f"tech_bank/ is DATA, not a study module")
        m = TB_SUB.match(line)
        if m:                                   # a role, under the open tier
            if sec == "Tiers" and cur is not None:
                role = {"id": m.group(1), "label": "", "def": "", "seeds": []}
                cur["roles"].append(role)
            continue
        m = TB_REC.match(line)
        if m:
            role = None
            if sec == "Kinds":
                cur = {"id": m.group(1), "label": "", "def": "", "default": False, "examples": []}
                kinds.append(cur)
            elif sec == "Languages":
                cur = {"id": m.group(1), "label": ""}
                langs.append(cur)
            elif sec == "Tiers":
                cur = {"id": m.group(1), "label": "", "blurb": "", "roles": []}
                tiers.append(cur)
            else:
                cur = None
            continue
        m = TB_H2.match(line)
        if m:
            sec, cur, role = m.group(1).strip(), None, None
            continue
        m = TB_FLD.match(line)
        if not m or cur is None:
            continue
        key, val = m.group(1).lower(), m.group(2)
        tgt = role if role is not None else cur
        if key == "label":
            tgt["label"] = val
        elif key == "blurb" and "blurb" in tgt:
            tgt["blurb"] = val
        elif key == "def" and "def" in tgt:
            tgt["def"] = val
        elif key == "default":
            tgt["default"] = val.strip().lower() in ("yes", "true")
        elif key == "examples" and "examples" in tgt:
            tgt["examples"] = [x.strip() for x in val.split(",") if x.strip()]
        elif key == "seeds" and role is not None:
            role["seeds"] = [x.strip() for x in val.split(",") if x.strip()]
        elif key in ("merged", "note") and role is not None:
            role[key] = val
    for t in tiers:
        if not t["roles"]:
            errs.append(f"tech_bank.md: tier '{t['id']}' declares no roles")
    if not kinds or not tiers or not langs:
        errs.append("tech_bank.md: taxonomy is missing a Kinds, Languages or Tiers section")
    return langs, tiers, kinds


def build_tech_bank():
    """technologies/tech_bank/*.md -> the tech_index.json payload, plus the validation
    errors that --strict turns into a failed build and the never-fatal warnings.
    Returns (bank, errors, warnings); (None, [...], []) when the directory is absent."""
    root = os.path.join(BASE_DIR, TECH_BANK_DIR)
    errs, warns = [], []
    if not os.path.isdir(root):
        return None, [f"missing {TECH_BANK_DIR}/ -- the technology bank has no source"], []
    tax = os.path.join(root, "tech_bank.md")
    if not os.path.isfile(tax):
        return None, [f"missing {TECH_BANK_DIR}/tech_bank.md -- no taxonomy"], []
    langs, tiers, kinds = _tb_taxonomy(tax, errs)
    role_ids = {f"{t['id']}/{r['id']}" for t in tiers for r in t["roles"]}
    kind_ids = {k["id"] for k in kinds}
    lang_ids = {L["id"] for L in langs}

    # RECURSIVE, so a shard someone files under a sub-directory is parsed and validated
    # rather than silently ignored. Both extract.py walks already treat everything under
    # tech_bank/ as inert; this pass is the only thing that reads it, so it has to be the
    # thing that notices.
    shard_files = []
    for r, dn, files in os.walk(root):
        dn.sort()
        for fn in sorted(files):
            if not fn.endswith(".md") or fn == "CLAUDE.md":
                continue
            rel = os.path.relpath(os.path.join(r, fn), root)
            if rel != "tech_bank.md":
                shard_files.append(rel)

    tools, where = {}, {}
    for fn in shard_files:
        shard_tier = os.path.basename(fn)[:-3]
        lines = open(os.path.join(root, fn), encoding="utf-8").read().split("\n")

        # The preamble -- `# <Tier> — technology bank`, the tier marker, and the paragraph
        # saying what the shard holds -- is the only thing telling a human reader what this
        # file is. `shard_tier` comes from the FILENAME, so losing the preamble does not
        # even downgrade the tier check to warnings: it is completely silent.
        #
        # It nearly went. A bulk description pass that rebuilds a shard from its `### `
        # records drops everything above the first one, and one agent did exactly that
        # before catching it in `git diff --stat`. The next one may not look.
        if not (lines and lines[0].startswith("# ")):
            errs.append(f"{fn}:1 lost its `# <Tier> — technology bank` heading -- a bulk "
                        f"pass that rebuilds from `### ` records drops the preamble")
        if not any(l.startswith("<!-- tech-bank tier:") for l in lines[:12]):
            errs.append(f"{fn}:1 lost its `<!-- tech-bank tier: -->` marker")

        i, cur, cur_line = 0, None, 0

        def close(cur, cur_line, desc):
            if cur is None:
                return
            name = cur["n"]
            paras = _tb_paragraphs(desc)
            if "s" not in cur:
                # The short line IS the row. A description with nothing above it renders
                # as a bare name, so this is fatal whether or not prose follows.
                errs.append(f"{fn}:{cur_line} '{name}' has no **Short:** line")
            elif not (SHORT_MIN <= len(cur["s"]) <= SHORT_MAX):
                errs.append(f"{fn}:{cur_line} '{name}' short line {len(cur['s'])} chars "
                            f"({SHORT_MIN}-{SHORT_MAX})")
            if cur.get("k") not in kind_ids:
                errs.append(f"{fn}:{cur_line} '{name}' unknown **Kind:** {cur.get('k')!r}")
            for tok in cur.get("l") or []:
                if tok not in lang_ids:
                    errs.append(f"{fn}:{cur_line} '{name}' unknown language token {tok!r}")
            if not cur.get("l"):
                errs.append(f"{fn}:{cur_line} '{name}' has no **Lang:** token")
            if not cur.get("r"):
                # facetWeight() returns null with no roles -> invisible under every facet.
                errs.append(f"{fn}:{cur_line} '{name}' has no roles")
            for rid, _w in cur.get("r") or []:
                if rid not in role_ids:
                    errs.append(f"{fn}:{cur_line} '{name}' unknown role {rid!r}")
            if name in where:
                errs.append(f"{fn}:{cur_line} duplicate record '{name}' "
                            f"(already in {where[name]}) -- last write would win silently")
            where[name] = fn
            if cur.get("r") and cur["r"][0][0].split("/")[0] != shard_tier:
                warns.append(f"{fn}: '{name}' primary role is "
                             f"{cur['r'][0][0].split('/')[0]}, not this shard's {shard_tier}")
            for ln in desc:
                if TB_BAD_DESC.search(ln):
                    errs.append(f"{fn}:{cur_line} '{name}' description carries a fence, "
                                f"list, table, link or heading -- it is escaped, not rendered")
                    break
            for ln in desc:
                if TB_EMOJI.search(ln):
                    errs.append(f"{fn}:{cur_line} '{name}' description carries an emoji")
                    break
            # NOT checked here, deliberately: whether the description merely restates the
            # short line above it. Word-overlap between the short line and the first
            # paragraph was tried at several thresholds and does not separate -- the house
            # style opens by recapping the tool and then expands, so every record scores
            # like the one genuine offender. It stayed silent as a warning nobody could act
            # on, which is worse than no check. Catch it in review, not here.
            out = {"k": cur.get("k", ""), "r": cur.get("r", []),
                   "l": cur.get("l", []), "s": cur.get("s", "")}
            if paras:
                out["d"] = "\n\n".join(paras)
            tools[name] = out

        desc = []
        while i < len(lines):
            line = lines[i]
            if TB_TEMPLATE_HDR.match(line):
                errs.append(f"{fn}:{i + 1} `## NN.` section-template heading -- "
                            f"tech_bank/ is DATA, not a study module")
            m = TB_REC.match(line)
            if not m:
                if cur is not None:
                    desc.append(line)
                i += 1
                continue
            close(cur, cur_line, desc)
            cur, cur_line, desc = {"n": m.group(1)}, i + 1, []
            i += 1
            # The field block is CONTIGUOUS and starts on the very next line, so a bolded
            # sentence inside a description can never be mistaken for a field.
            while i < len(lines):
                f = TB_FLD.match(lines[i])
                if not f:
                    break
                key, val = f.group(1).lower(), f.group(2)
                if key == "short":
                    cur["s"] = val
                elif key == "kind":
                    cur["k"] = val
                elif key == "lang":
                    cur["l"] = [x.strip() for x in val.split(",") if x.strip()]
                elif key == "roles":
                    rs = []
                    for part in val.split(","):
                        rm = TB_ROLE.match(part.strip())
                        if not rm:
                            errs.append(f"{fn}:{i + 1} '{cur['n']}' bad role "
                                        f"{part.strip()!r} (want 'tier/role @1|2|3')")
                            continue
                        rs.append([rm.group(1), int(rm.group(2))])
                    cur["r"] = rs
                else:
                    errs.append(f"{fn}:{i + 1} '{cur['n']}' unknown field **{f.group(1)}:**")
                i += 1
        close(cur, cur_line, desc)

    if not tools:
        errs.append(f"{TECH_BANK_DIR}/ parsed zero tools")
    bank = {"generatedAt": datetime.now(timezone.utc).isoformat(),
            "langs": langs, "tiers": tiers, "kinds": kinds, "tools": tools}
    return bank, errs, warns


# ---- technologies index (questions/tech.json) ------------------------------
#
# Two sections of the 14-section module template are pure REFERENCE material that
# the Q&A bank throws away: "## 11. Technologies & Tools" (a table of the tools a
# module teaches) and "## 8. Tradeoffs" (comparison tables). Read inside a single
# module they answer "what does this page use"; read across the whole repo they
# answer a question no screen could previously ask -- "where is Kafka taught, and
# what is the decision each of those pages makes me weigh".
#
# So this pass inverts them: an index BY TECHNOLOGY (name -> every module that
# covers it, with that module's one-line purpose for it) plus a per-module
# tradeoff digest. Both deep-link into the reader at the exact heading, using the
# same slug() the reader's mdRender computes for heading ids.
#
# Emitted as questions/tech.json -- a section-INDEPENDENT artifact like index.json
# and paths.json, so it must also be declared in scripts/build_banks.sh's
# `want_banks` and counted in the APK smoke test, or both deploys go red.
TECH_HDR = re.compile(r"^##\s+(?:\d+[.)]\s*)?.*\btechnolog\w*\b.*\btools?\b.*$", re.I)
TRADE_HDR = re.compile(r"^##\s+(?:\d+[.)]\s*)?trade[\s-]?offs?\b.*$", re.I)

# A §11 table whose FIRST column is a grouping label, not a product: the tools then
# live in the second column as a comma-separated list ("| Async messaging | Kafka,
# RabbitMQ, SQS |"). Taking column 0 there would index "Async messaging" as a
# technology and lose all three real ones.
CATEGORY_HEADS = {"category", "concern", "dimension", "capability", "layer", "area",
                  "aspect", "phase", "stage", "use case", "need", "task", "problem",
                  "requirement", "when", "scenario", "goal", "purpose", "job", "role"}

# The TRANSPOSED case, and the reason the set above is not enough on its own. A feature
# matrix puts the comparison axis in column 0 and the PRODUCTS across the header row:
#
#     | Capability          | AWS              | GCP            | Azure             |
#     | Run generators on   | ECS/Fargate, EKS | GKE, Cloud Run | AKS, Container Apps |
#
# "Capability" is already a CATEGORY_HEAD, but there is no tool-list column for the
# grouped branch to read, so it fell through to "column 0 is the tool" and indexed
# `Run generators on`, `gRPC support`, `Production readiness` and `Explicit in signature`
# as technologies. Six such tables exist; between them they were the bulk of the records
# whose own short line has to say "comparison-table cell, not a product".
#
# The >= 4 column floor (axis + at least three alternatives) is doing real work, not
# tidiness: `technologies/intel_openvino` has `| Feature | Hardware | Accelerates |`,
# whose column 0 is AVX-512 VNNI / AMX / XMX / NPU -- genuine tools. Three columns is a
# normal table that happens to start with a comparison word; you do not build a matrix
# to compare two things. A two-product matrix is therefore missed, which is the safe
# direction to be wrong in.
COMPARISON_HEADS = {"comparison", "capability", "feature", "criterion", "criteria",
                    "dimension", "aspect", "metric", "property", "attribute", "factor",
                    "characteristic"}
TOOLLIST_HEADS = {"tools", "tool", "options", "option", "technologies", "technology",
                  "examples", "example", "libraries", "library", "choices", "products",
                  "stack", "tooling", "tool / library"}

# Row labels that are table furniture, not products.
TECH_NOISE = re.compile(
    r"^(n/?a|none|-+|—|–|\.{2,}|tbd|various|others?|etc\.?|notes?|purpose|example|"
    r"typical use case|use case|when to use|best for|pros|cons|summary|total|"
    r"description|comments?)$", re.I)

# Split a name cell into the products it actually names. Spaced " / " and " + "
# are compounds ("htop / top", "Micrometer + Actuator"); an unspaced slash is part
# of the name and must survive ("TCP/IP", "AWS SQS/SNS", "HTTP/2").
NAME_SPLIT_RE = re.compile(r"\s+(?:/|\+|&amp;)\s+")
# How much of a module's own description of a tool to keep. This was a bare 90, which
# put 1,015 of 5,825 blurbs (17%) at EXACTLY the cap -- a cliff, not a distribution --
# cutting them mid-word ("... Single-threaded Lua execution guarante"). The blurb is the
# richest signal in the index: it is the citing module's own statement of what the tool is
# FOR, and it is what any role/'"'"'what problem does this solve'"'"' derivation reads. Truncating it
# was silently degrading that input. 220 matches the MCQ short-answer ceiling used elsewhere.
BLURB_MAX = 220

LIST_SPLIT_RE = re.compile(r"\s*(?:,|;)\s+")
MD_TABLE_SEP = re.compile(r"^\|[\s:|-]+\|?\s*$")


# Only REAL html tags may be stripped. A blanket <[^>]+> also ate placeholders that
# are part of the content -- "/proc/<pid>/sched" came out as "/proc//sched".
HTML_TAG_RE = re.compile(
    r"</?(?:br|b|i|u|em|strong|sub|sup|code|kbd|samp|var|span|div|p|a|img|small|"
    r"ul|ol|li|table|thead|tbody|tr|td|th|details|summary|hr|pre|blockquote)\b[^>]*/?>",
    re.I)


def tech_strip_md(text):
    """Table cells hold links, code spans, bold and <br> -- reduce to plain text."""
    text = re.sub(r"<br\s*/?>", " ", text, flags=re.I)
    text = HTML_TAG_RE.sub("", text)
    text = re.sub(r"!\[[^\]]*\]\([^)]*\)", "", text)
    text = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", text)
    text = text.replace("`", "").replace("**", "").replace("__", "")
    text = text.replace("&amp;", "&").replace("\\|", "|")
    return re.sub(r"\s+", " ", text).strip()


def reader_slug(text):
    """Port of the reader's `slug()`/`stripMd()` pair in app.js, so the anchor we emit
    is the id mdRender() will actually put on that heading. Diverge and every deep
    link lands at the top of the page instead of the section, silently. Kept
    character-for-character equivalent -- note it strips ` * _ like the JS does,
    which `tech_strip_md` deliberately does not.

    Not modelled: mdRender's `-2` suffix for a second heading that slugs the same
    within one file. No module has two §11/§8 headings, and if one ever does the
    link still lands on the first of the pair."""
    base = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", text)
    base = re.sub(r"[`*_]", "", base).lower()
    base = re.sub(r"[^\w\s-]", "", base).strip()
    return re.sub(r"\s+", "-", base)[:80] or "section"


def md_tables(body_lines):
    """[(header_cells, [row_cells, ...]), ...] for every pipe table in a block."""
    out, cur = [], []
    for line in list(body_lines) + [""]:
        s = line.strip()
        if s.startswith("|"):
            cur.append(s)
            continue
        if cur:
            if len(cur) >= 3 and MD_TABLE_SEP.match(cur[1]):
                cells = lambda r: [c.strip() for c in r.strip().strip("|").split("|")]
                out.append((cells(cur[0]), [cells(r) for r in cur[2:]]))
            cur = []
    return out


# A bare version or model-size token ("3.5", "3.6)", "3B", "9B", "1.x", "v2"). These
# fall out of a split inside a cell and are never a product on their own.
VERSION_TOKEN_RE = re.compile(r"^v?\d+(?:\.\d+)*(?:\.x)?\s*[BbMmKk]?[)\].,;:]?$")
# A name may start with any Unicode letter or digit -- "2captcha" and the Greek-lead
# "τ²-bench" are both real -- or with @ / . _ when a letter follows: "@Bean",
# "@modelcontextprotocol/sdk", "/actuator/beans", ".NET" and Elasticsearch's
# "_cat/indices" are all real API names in this repo. A blanket ban on a leading @ or
# / would delete ~95 genuine Spring annotations and scoped npm packages to remove
# three fragments. Anything else leading IS a fragment ("$ tracking)", "(m - 1) * T").
NAME_START_RE = re.compile(r"^(?:[^\W_]|[@/._][^\W_])")


def clean_tech_name(raw):
    """A product name, or None when the cell is prose/furniture rather than a tool."""
    name = tech_strip_md(raw)
    name = re.sub(r"\s*\([^()]*\)\s*$", "", name).strip()   # drop a trailing parenthetical
    name = re.sub(r"[\s,]+etc\.?$", "", name, flags=re.I)   # "@ai-sdk/anthropic etc"
    name = name.strip(" .,:;*\"'")
    # No ASCII LETTER at all -> a number, a percentage or an expression ("<1%", "3.5",
    # "(m - 1) * T"), never a product.
    if not name or TECH_NOISE.match(name) or not re.search(r"[A-Za-z]", name):
        return None
    # Deliberately NOT a 3-character minimum: the two-character names in this repo are
    # overwhelmingly real (k6, jq, yq, ss, nc, nm, uv, Nx, Z3, H2, S3, TF, RQ, E5, re,
    # ip) and only "3B"/"9B" were junk -- VERSION_TOKEN_RE removes those precisely,
    # without costing 16 genuine tools.
    if not (2 <= len(name) <= 42) or name.count(" ") > 5:
        return None
    if VERSION_TOKEN_RE.match(name):        # bare "3.5" / "3B" / "1.x" / "Boot 3)"-style tail
        return None
    if re.search(r"\.\s+[A-Z]", name):                      # a sentence, not a name
        return None
    # An unmatched closing bracket means a split landed mid-parenthetical, so this is
    # the tail of a cell rather than a name ("Boot 3)", "reactive streams)", "ICU4J)").
    if (name.endswith(")") and "(" not in name) or (name.endswith("]") and "[" not in name):
        return None
    # ...and the symmetric case. An unmatched OPENER means a split landed mid-parenthetical
    # and this is the HEAD of the cell ("ICU (ICU4C", "Gemini 3.x (3.1"). Only the closer
    # was guarded, so every such head shipped as a mangled name while its tail was dropped.
    if ("(" in name and ")" not in name) or ("[" in name and "]" not in name):
        return None
    # A CLI flag is documentation of a tool, not a tool. Triton's §11 has a whole
    # flags-reference table; indexing "--model-repository" as a technology buries the
    # products the same section actually names.
    if name.startswith("-") or not NAME_START_RE.match(name):
        return None
    return name


# Two hand-kept exception tables. Both exist because vendor naming is not a rule you can
# derive: stripping a leading vendor word would fold "Grafana Loki" into "Loki" correctly
# but "Google Cloud Storage" into "Cloud Storage" wrongly, and no suffix rule separates
# "Redis Cluster" (deliberately its own entry) from "Envoy Proxy" (the same product).
# So these are enumerated, small, and reviewed -- not inferred.
TECH_ALIASES = {                       # written this way -> indexed as this
    "envoy proxy": "envoy",
    "grafana loki": "loki",
    "huggingface transformers": "transformers",
    "hugging face transformers": "transformers",
    # Triton is TWO unrelated NVIDIA/OpenAI products and the repo says so in
    # technologies/CLAUDE.md, but modules still wrote the bare word for both. A homonym
    # entry cannot separate them -- both products are taught inside ml/ -- so the two
    # ml/ tables that meant the inference server now say "NVIDIA Triton" outright, and
    # the bare word is aliased to the kernel DSL, which is what every remaining
    # citation means.
    "triton": "openai triton",
    "triton inference server": "nvidia triton",
}
# The reverse problem: ONE name, two unrelated products. tech_key merges by name, so a
# homonym silently fuses them and the entry describes neither. Disambiguated by the section
# that teaches it. Medusa is the Cassandra backup tool in database/, and the
# speculative-decoding method in llm/.
TECH_HOMONYMS = {
    "medusa": {"llm": "Medusa (speculative decoding)"},
    # Stanford's HELM benchmark vs Helm the Kubernetes package manager. tech_key lowercases,
    # so `| **HELM** | Holistic evaluation | Stanford; multi-scenario |` in
    # llm/evaluation_and_benchmarks landed on the k8s tool's row -- which then shipped titled
    # "Helm", described as a package manager, carrying a Stanford-eval citation. Verified
    # section-separable before adding this: the only llm/ mentions of Helm-the-k8s-tool are
    # prose in a bullet and two case studies, none of which the §11 walk indexes.
    "helm": {"llm": "HELM (Stanford)"},
}
# The DISPLAY name is normally voted on from the surface forms the modules actually wrote.
# Two cases need that vote overridden, and both fail the same way without it: the row keeps
# the ambiguous name AND, because the bank lookup is keyed on display name, it collects the
# wrong record -- a wrong answer wearing the costume of a duplicate.
#   1. A homonym, where both halves were written identically ("Medusa").
#   2. A merge whose winning form is the ambiguous one: five modules wrote bare "Triton"
#      for the kernel DSL, so the vote would re-elect the very word being disambiguated.
# Whatever is forced here MUST match a `###` record name in the bank, or the record orphans
# and its description silently stops rendering. The coverage line catches that.
TECH_LABELS = {
    "openai triton": "OpenAI Triton",
    "nvidia triton": "NVIDIA Triton",
}
TECH_LABELS.update({v.lower(): v for m in TECH_HOMONYMS.values() for v in m.values()})


def tech_key(name, section=None):
    """Merge key. "Apache Kafka"/"Kafka" and "Pydantic v2"/"Pydantic" are one tool;
    "Redis"/"Redis Cluster" are deliberately not. `section` disambiguates homonyms."""
    key = re.sub(r"^apache\s+", "", name.lower())
    key = re.sub(r"\s+v\d[\d.]*$", "", key)
    key = re.sub(r"\s+", " ", key).strip(" .")
    key = TECH_ALIASES.get(key, key)
    if section and key in TECH_HOMONYMS:
        return TECH_HOMONYMS[key].get(section, key).lower()
    return key


def _split_outside_parens(cell):
    """Split on the separators, but NEVER inside brackets.

    `ICU (ICU4C / ICU4J)` is ONE tool whose parenthetical happens to contain a slash.
    Splitting blind produced 'ICU (ICU4C' (kept -- the guard below only caught an
    unmatched CLOSER) and 'ICU4J)' (dropped), i.e. one real tool turned into one
    mangled name. Same shape for 'Gemini 3.x (3.1, 3.0)' and
    'MongoDB Java Driver (sync / reactive)'. 20 entries in the shipped index."""
    depth, buf, out = 0, [], []
    i = 0
    while i < len(cell):
        ch = cell[i]
        if ch in "([":
            depth += 1
        elif ch in ")]":
            depth = max(0, depth - 1)
        if depth == 0:
            mo = NAME_SPLIT_RE.match(cell, i)
            if mo:
                out.append("".join(buf)); buf = []
                i = mo.end(); continue
        buf.append(ch); i += 1
    out.append("".join(buf))
    return out


def _tech_names(cell):
    for part in _split_outside_parens(cell):
        got = clean_tech_name(part)
        if got:
            yield got


# Not every §11 is a table. Two bold-lead bullet shapes carry real products, and the
# three hand-written technologies/ deep dives use them almost exclusively -- the
# Airflow card read "0 tools" until this pass existed.
#
#   labelled:  - **Metadata DB:** **PostgreSQL** (recommended for HA) or **MySQL 8+**
#              -> the first bold is the LABEL (it ends in a colon); the tools follow
#   dashed:    - **perf_analyzer** — sweeps concurrency, reporting **throughput** ...
#              -> the first bold IS the tool; later bolds are prose emphasis, not tools
#
# A bullet matching NEITHER shape is prose that happens to contain bold, and is
# skipped: taking its bold spans indexes sentence fragments ("Always front Triton
# with a gateway") as if they were products.
BULLET_RE = re.compile(r"^\s*[-*+]\s+(.*)$")
BOLD_SPAN_RE = re.compile(r"\*\*([^*]+)\*\*")
DASH_LEAD_RE = re.compile(r"^\s*[—–-]\s")


def tech_from_bullets(body):
    out = []
    for line in body:
        m = BULLET_RE.match(line)
        if not m:
            continue
        text = m.group(1)
        spans = BOLD_SPAN_RE.findall(text)
        if not spans:
            continue
        first = spans[0].rstrip()
        if first.endswith(":"):
            names, blurb = spans[1:], first.rstrip(":").strip()
        elif DASH_LEAD_RE.match(text[text.index("**" + spans[0] + "**") + len(spans[0]) + 4:]):
            names, blurb = spans[:1], tech_strip_md(re.sub(r"^\s*[—–-]\s*", "", text.split("**", 2)[-1]))
        else:
            continue
        for span in names:
            for name in _tech_names(span):
                out.append((name, blurb[:BLURB_MAX]))
    return out


def _study_order():
    """{section: [module slug, ...]} read out of app.js's STUDY_ORDER literal, in file
    order -- so a Python dict's insertion order matches JS `Object.keys(STUDY_ORDER)`.
    Returns {} if app.js cannot be read; callers must degrade, never crash."""
    try:
        app_src = open(os.path.join(GAME_DIR, "app.js"), encoding="utf-8").read()
        return _section_arrays(STUDY_ORDER_RE.search(app_src).group(1))
    except Exception:
        return {}


def build_tech_index(bank=None):
    """{anchors, modules, tech, trade, secs, deep} -- the whole-repo technology +
    tradeoff index, with everything the Technologies screen used to recompute on EVERY
    render precomputed here instead (see the `h`/`s`/`secs`/`deep` notes at the bottom).
    `bank` is the parsed tech_bank, folded into the search haystack so the authored
    summary and description are searchable without the browser touching them."""
    anchors, anchor_ids = [], {}
    modules, module_ids = [], {}
    tech = {}            # key -> {"forms": Counter-ish dict, "uses": [(modIdx, blurb)]}
    trade = []

    def anchor_idx(heading):
        a = reader_slug(heading)
        if a not in anchor_ids:
            anchor_ids[a] = len(anchors)
            anchors.append(a)
        return anchor_ids[a]

    def module_idx(module, source_file, title):
        key = (module, source_file)
        if key not in module_ids:
            module_ids[key] = len(modules)
            modules.append({"m": module, "f": source_file, "t": title, "a": [-1, -1]})
        return module_ids[key]

    for root, dirs, files in os.walk(BASE_DIR):
        dirs.sort()
        rel = os.path.relpath(root, BASE_DIR)
        if rel == ".":
            continue
        parts = rel.split(os.sep)
        section = parts[0]
        if section in SKIP_SECTIONS or SKIP_PATH_PARTS.intersection(parts) or in_tech_bank(rel):
            continue
        # Same module-id rule as the bank: 2 segments, 3 for book. A file deeper than
        # that folds into its parent module and carries the rest in source_file.
        depth = 3 if section == "book" else 2
        if len(parts) < depth:
            continue
        module = "/".join(parts[:depth])
        sub = "/".join(parts[depth:])
        for fn in sorted(files):
            if not fn.endswith(".md") or fn == "CLAUDE.md":
                continue
            source_file = f"{sub}/{fn}" if sub else fn
            try:
                lines = open(os.path.join(root, fn), encoding="utf-8").read().split("\n")
            except OSError:
                continue
            i11 = next((i for i, l in enumerate(lines) if TECH_HDR.match(l)), None)
            i8 = next((i for i, l in enumerate(lines) if TRADE_HDR.match(l)), None)
            if i11 is None and i8 is None:
                continue
            title = next((tech_strip_md(l[2:]) for l in lines[:40] if l.startswith("# ")), None)
            mi = module_idx(module, source_file, title)

            if i11 is not None:
                modules[mi]["a"][0] = anchor_idx(lines[i11][3:])
                body = []
                for l in lines[i11 + 1:]:
                    if l.startswith("## "):
                        break
                    body.append(l)
                found = []
                for hdr, rows in md_tables(body):
                    h0 = tech_strip_md(hdr[0]).lower() if hdr else ""
                    h1 = tech_strip_md(hdr[1]).lower() if len(hdr) > 1 else ""
                    grouped = h0 in CATEGORY_HEADS and h1 in TOOLLIST_HEADS
                    # A transposed feature matrix: the axis is in column 0 and the
                    # products are the remaining HEADER cells. Skip it outright rather
                    # than harvesting those header products -- they are already indexed
                    # from the module's real tool table, and reading them here would
                    # invent index entries ("gunicorn + uvicorn-worker", "uvicorn 0.51+")
                    # that no bank record covers. Dropping a matrix costs nothing; the
                    # only thing in its first column is the comparison axis.
                    if not grouped and h0 in COMPARISON_HEADS and len(hdr) >= 4:
                        continue
                    for row in rows:
                        if not row or not row[0].strip():
                            continue
                        if grouped:
                            listing = re.sub(r"\([^)]*\)", "", tech_strip_md(row[1] if len(row) > 1 else ""))
                            names = [n for part in LIST_SPLIT_RE.split(listing) for n in _tech_names(part)]
                            blurb = tech_strip_md(row[0])
                        else:
                            names = list(_tech_names(row[0]))
                            blurb = " — ".join(tech_strip_md(c) for c in row[1:3] if c.strip())
                        found += [(name, blurb[:BLURB_MAX]) for name in names]
                # Merged, not fallback: the technologies/ deep dives carry BOTH a flags
                # table and the bullet prose that names the actual products, so a
                # table-first-wins rule indexed Triton as 19 CLI flags and no tools.
                found += tech_from_bullets(body)
                for name, blurb in found:
                    # section-aware so a homonym taught in two worlds stays two entries
                    entry = tech.setdefault(tech_key(name, module.split("/")[0]), {"forms": {}, "uses": []})
                    entry["forms"][name] = entry["forms"].get(name, 0) + 1
                    entry["uses"].append([mi, blurb])

            if i8 is not None:
                modules[mi]["a"][1] = anchor_idx(lines[i8][3:])
                body = []
                for l in lines[i8 + 1:]:
                    if l.startswith("## "):
                        break
                    body.append(l)
                tabs = md_tables(body)
                if tabs:
                    # The FIRST table is the module's headline decision table; the rest
                    # are drill-downs the reader already renders. Digest, don't mirror.
                    hdr, rows = tabs[0]
                    head = [tech_strip_md(c)[:36] for c in hdr][:3]
                    digest = []
                    for row in rows[:5]:
                        cells = [tech_strip_md(c)[:72] for c in row][:3]
                        while cells and not cells[-1]:
                            cells.pop()
                        if cells and cells[0]:
                            digest.append(cells)
                    if digest:
                        trade.append({"i": mi, "h": head, "r": digest})

    # ---- everything below here is PRECOMPUTE, not indexing ----------------------
    # The Technologies screen used to rebuild all of this inside renderTech(), on every
    # render, from data that never depends on user state: a 574,532-character lowercase
    # haystack across 3,809 rows, a Set of sections per row, a whole-index pass to count
    # the tools in each hand-written deep dive, and a 607-row map+sort for the tradeoff
    # tab. None of it can change between renders, so none of it belongs in the browser --
    # the same reason scripts/build_diagrams.mjs pre-renders Mermaid to static SVG.
    order = _study_order()
    sec_pos = {s: i for i, s in enumerate(order)}
    def sec_rank(s):
        return sec_pos.get(s, 998) + 1

    # techName(m) in app.js = m.t || fileLabel(m.f) || prettyMod(m.m). All 613 modules
    # carry a title today; the fallback exists so a future untitled page still gets a
    # searchable string rather than "None".
    def mod_title(m):
        return m["t"] or os.path.basename(m["f"])[:-3].replace("_", " ") or m["m"]

    # Display name = the most common surface form (shortest wins a tie), so a tool
    # written five ways in five sections still reads as one entry.
    tech_out = []
    for key, entry in tech.items():
        name = max(entry["forms"].items(), key=lambda kv: (kv[1], -len(kv[0]), kv[0]))[0]
        if key in TECH_LABELS:
            name = TECH_LABELS[key]
        # One module may list the same tool in two tables — keep the first blurb only.
        seen, uses = set(), []
        for mi, blurb in entry["uses"]:
            if mi in seen:
                continue
            seen.add(mi)
            uses.append([mi, blurb] if blurb else [mi])
        tech_out.append({"n": name, "u": uses})
    # Broadest coverage first: the tools worth learning are the ones many modules reach for.
    tech_out.sort(key=lambda t: (-len(t["u"]), t["n"].lower()))

    # `h` -- the SEARCH HAYSTACK, lowercased and pre-joined. It carries the tool name,
    # every citing module's title and that module's own blurb for it, PLUS the bank's
    # authored short line and description. The short line was never searchable before
    # (renderTech built the haystack from name + provenance only), so 404,755 characters
    # of the best text in the index were displayed and invisible to the search box.
    # Folding the description in here is also what makes a description-only artifact
    # split pointless: a copy of the prose has to live in the haystack either way.
    # `s` -- the row's sections, in first-appearance order, for the section filter.
    btools = (bank or {}).get("tools") or {}
    for t in tech_out:
        secs, seen_s = [], set()
        for u in t["u"]:
            s = modules[u[0]]["m"].split("/")[0]
            if s not in seen_s:
                seen_s.add(s)
                secs.append(s)
        b = btools.get(t["n"]) or {}
        parts = [t["n"]]
        for u in t["u"]:
            parts.append(mod_title(modules[u[0]]))
            parts.append(u[1] if len(u) > 1 else "")
        parts.append(b.get("s", ""))
        parts.append(b.get("d", ""))
        t["s"] = secs
        t["h"] = " ".join(p for p in parts if p).lower()

    # Tradeoff cards come out in the order you LEARN the material: section order first,
    # then the module's STUDY_ORDER position. app.js applied the same comparator on every
    # render; emitting the array already sorted makes that sort a no-op (Array.sort is
    # stable), so it stays correct whether or not the runtime keeps sorting.
    def so_index(mod_id):
        sec = mod_id.split("/")[0]
        for i, slug in enumerate(order.get(sec, [])):
            if mod_id == slug or mod_id.startswith(slug + "/"):
                return i
        return 9999
    # NOT precomputed, deliberately: the tradeoff tab's own search haystack. It was
    # measured at +53,531 bytes GZIP -- gzip cannot dedupe it against the cells it copies
    # because the copy is lowercased -- to save one pass over 607 rows / ~220 K chars,
    # about 1.7 ms per render of a tab that is not even the default. The per-tool haystack
    # is 3,809 rows and carries text that exists nowhere else in the file; this one is
    # neither. Leaving it at runtime is the cheaper side of the trade.
    trade.sort(key=lambda t: (sec_rank(modules[t["i"]]["m"].split("/")[0]),
                              so_index(modules[t["i"]]["m"]),
                              modules[t["i"]]["m"], modules[t["i"]]["f"]))

    # Sections present in the index, already in the app's canonical order.
    secs_present = sorted({m["m"].split("/")[0] for m in modules}, key=sec_rank)
    # The hand-written technologies/ deep dives, pinned above the derived index, each with
    # the number of indexed tools in its stack. That count used to cost a full pass over
    # every tool's every use (~5,800 pairs) to label three cards.
    tools_in = {}
    for t in tech_out:
        for u in t["u"]:
            tools_in[u[0]] = tools_in.get(u[0], 0) + 1
    deep = [[i, tools_in.get(i, 0)] for i, m in enumerate(modules)
            if m["m"].startswith("technologies/")]
    return {"generatedAt": datetime.now(timezone.utc).isoformat(),
            "anchors": anchors, "modules": modules, "tech": tech_out, "trade": trade,
            "secs": secs_present, "deep": deep}

