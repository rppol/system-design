#!/usr/bin/env node
// Build-time Mermaid pre-renderer for the LORA game.
//
// Strategy: drive the REAL app in headless Chromium (Puppeteer) and call its own
// window.__mmBuildVariants() per fence. That reuses the exact runtime engine
// (the pinned mermaid@11.16.0 the app imports), config, and mmPolishSvg polish,
// so pre-rendered SVGs are byte-identical to live output with ZERO duplicated
// render logic. For each unique fence it emits both orientations (TD + flipped
// LR) plus their measured dims, so the client can re-choose orientation for the
// current width — engine-free, offline, on phone/tablet/rotation.
//
// Assets: game/diagrams/<key>.json keyed by cyrb53(fenceSource) (the SAME hash +
// normalization the runtime computes from a .mermaid node's textContent.trim()).
// Content-hashed => edits invalidate, unchanged diagrams are stable/cacheable, and
// the build is incremental (renders only missing keys, prunes orphans).
//
// Usage: node scripts/build_diagrams.mjs [--base http://localhost:8901] [--full]
//   --full re-renders everything (ignores existing assets); default is incremental.

import { readFileSync, writeFileSync, readdirSync, mkdirSync, existsSync, statSync, rmSync } from "node:fs";
import { join, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { gzipSync } from "node:zlib";
import puppeteer from "puppeteer";

// Coordinate precision: mermaid emits ~6 decimals; 1 dp is visually identical and
// roughly halves the raw SVG. Combined with gzip it takes the whole tree from
// ~650MB to ~55MB. Assets are stored gzipped (.mmz) and gunzipped at runtime via
// DecompressionStream — small enough for the offline APK and CI regeneration.
const _numRe = /-?\d+\.\d{2,}/g;
const shrink = (svg) => (svg ? svg.replace(_numRe, (m) => (+m).toFixed(1)) : svg);

const ROOT = join(fileURLToPath(import.meta.url), "..", "..");
const SECTION_DIR = join(ROOT, "src/main/java/com/rutik/systemdesign");
const GAME_REL = "src/main/java/com/rutik/systemdesign/game";
const DIAGRAMS_DIR = join(ROOT, GAME_REL, "diagrams");
const RENDER_WIDTH = 720;                       // canonical column width (charts render at max, then scale down)
const SKIP_PARTS = new Set(["node_modules", ".git"]);

const args = process.argv.slice(2);
const BASE = (args.includes("--base") ? args[args.indexOf("--base") + 1] : "") || process.env.LORA_BASE || "http://localhost:8901";
const FULL = args.includes("--full");
const APP_URL = `${BASE}/${GAME_REL}/index.html`;

// ---- enumerate every markdown file under the study sections ----
function walk(dir, out = []) {
  for (const name of readdirSync(dir)) {
    if (SKIP_PARTS.has(name)) continue;
    const p = join(dir, name);
    const st = statSync(p);
    if (st.isDirectory()) walk(p, out);
    else if (name.endsWith(".md")) out.push(p);
  }
  return out;
}

async function main() {
  const mdFiles = walk(SECTION_DIR);
  console.log(`[diagrams] scanning ${mdFiles.length} markdown files under ${relative(ROOT, SECTION_DIR)}`);

  const browser = await puppeteer.launch({
    headless: "new",
    args: ["--no-sandbox", "--disable-setuid-sandbox", "--disable-dev-shm-usage"],
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1100, height: 900 });
  page.on("pageerror", (e) => console.warn("[page error]", String(e).slice(0, 200)));
  await page.goto(APP_URL, { waitUntil: "domcontentloaded", timeout: 60000 });
  // Wait for the app's build hook + markdown renderer, then warm the mermaid engine.
  await page.waitForFunction("typeof window.__mmBuildVariants === 'function' && typeof mdRender === 'function' && typeof cyrb53 === 'function'", { timeout: 60000 });
  // Register the bundled iconify icon packs on the SAME engine the pre-renderer
  // uses, so icon nodes (`@{ icon: "logos:aws-s3" }`) and architecture-beta
  // services bake their real product logo into the .mmz. Mirrors ensureMermaid's
  // registration; registerIconPacks works without initialize() (verified).
  await page.evaluate(async () => {
    await _loadMermaidModule().then(async (m) => {
      if (typeof _mmRegisterIcons === "function") await _mmRegisterIcons(m);
      _mermaidReady = Promise.resolve(m);
    });
  });
  console.log("[diagrams] app + mermaid engine ready");

  // ---- collect unique fences (hash + source) using the app's OWN extraction ----
  const uniq = new Map();   // key -> src
  for (const file of mdFiles) {
    const md = readFileSync(file, "utf8");
    const fences = await page.evaluate((mdText) => {
      const div = document.createElement("div");
      div.innerHTML = mdRender(mdText);
      return [...div.querySelectorAll(".mermaid")].map((n) => {
        const src = n.textContent.trim();
        return { key: cyrb53(src).toString(36), src };
      });
    }, md);
    for (const f of fences) if (!uniq.has(f.key)) uniq.set(f.key, f.src);
  }
  console.log(`[diagrams] ${uniq.size} unique fences`);

  mkdirSync(DIAGRAMS_DIR, { recursive: true });
  const liveKeys = new Set(uniq.keys());

  // ---- render each unique fence's variants (incremental unless --full) ----
  let rendered = 0, cached = 0, failed = 0, n = 0;
  for (const [key, src] of uniq) {
    n++;
    const dest = join(DIAGRAMS_DIR, `${key}.mmz`);
    if (!FULL && existsSync(dest)) { cached++; continue; }
    try {
      const asset = await page.evaluate((s, w) => window.__mmBuildVariants(s, w), src, RENDER_WIDTH);
      if (!asset || !asset.svg0) { failed++; console.warn(`[diagrams] empty render for ${key}`); continue; }
      asset.svg0 = shrink(asset.svg0);
      asset.svg1 = shrink(asset.svg1);
      writeFileSync(dest, gzipSync(Buffer.from(JSON.stringify(asset)), { level: 9 }));
      rendered++;
      if (rendered % 100 === 0) console.log(`[diagrams] rendered ${rendered} (of ${uniq.size - cached} to do)…`);
    } catch (e) {
      failed++;
      console.warn(`[diagrams] render failed for ${key}: ${String(e).slice(0, 160)}`);
    }
  }

  // ---- prune orphaned assets (fences that were edited/removed) ----
  let pruned = 0;
  for (const f of readdirSync(DIAGRAMS_DIR)) {
    if (!f.endsWith(".mmz")) continue;
    if (!liveKeys.has(f.replace(/\.mmz$/, ""))) { rmSync(join(DIAGRAMS_DIR, f)); pruned++; }
  }

  await browser.close();
  console.log(`[diagrams] done: ${rendered} rendered, ${cached} cached, ${pruned} pruned, ${failed} failed; ${liveKeys.size} live assets`);
  if (failed) process.exitCode = 0;   // a failed fence falls back to live render at runtime; don't fail the build
}

main().catch((e) => { console.error(e); process.exit(1); });
