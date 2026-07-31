// Render-validation gate for Mermaid fences. Extracts every ```mermaid fence from a
// file and renders each through the pinned engine WITH the logos icon pack registered
// (the exact runtime path). Exit 0 = all render; exit 1 = prints the failing fences.
//
// Run this on any file whose diagrams you touched, BEFORE committing. A fence that
// does not parse fails the build (scripts/build_diagrams.mjs exits 1), and GitHub
// renders a broken fence as a silent error box — so "it looked fine in the diff"
// proves nothing. Every one of the 20 broken diagrams found on 2026-07-30 read
// perfectly as source.
//
// Needs `npm ci` first (Puppeteer + the pinned mermaid are CI-only build tooling,
// gitignored under node_modules/).
//
//   node scripts/check_render.mjs <absolute-file.md>
import fs from "node:fs";
import puppeteer from "puppeteer";
import logos from "@iconify-json/logos/icons.json" with { type: "json" };

const file = process.argv[2];
if (!file || !fs.existsSync(file)) { console.error("usage: check_render.mjs <file.md>"); process.exit(2); }
const md = fs.readFileSync(file, "utf8");
const fences = [...md.matchAll(/```mermaid\n([\s\S]*?)```/g)].map((m) => m[1]);
if (!fences.length) { console.log("no mermaid fences"); process.exit(0); }

setTimeout(() => { console.error("HARD TIMEOUT"); process.exit(3); }, 120000).unref();
const MERMAID_UMD = fs.readFileSync("node_modules/mermaid/dist/mermaid.min.js", "utf8");
const browser = await puppeteer.launch({ headless: true, args: ["--no-sandbox", "--disable-gpu"] });
let failed = 0;
try {
  const page = await browser.newPage();
  page.on("pageerror", () => {});
  await page.setContent("<!doctype html><html><body></body></html>");
  await page.addScriptTag({ content: MERMAID_UMD });
  const results = await page.evaluate(async (logosJson, fences) => {
    const m = window.mermaid;
    m.initialize({ startOnLoad: false, theme: "dark", securityLevel: "loose", flowchart: { htmlLabels: false } });
    m.registerIconPacks([{ name: "logos", icons: logosJson }]);
    const out = [];
    for (let i = 0; i < fences.length; i++) {
      try { await m.render("chk" + i, fences[i]); out.push({ i, ok: true }); }
      catch (e) { out.push({ i, ok: false, err: String((e && e.message) || e).split("\n")[0].slice(0, 200) }); }
    }
    return out;
  }, logos, fences);
  for (const r of results) {
    if (!r.ok) {
      failed++;
      console.error(`\nFENCE #${r.i + 1} FAILED: ${r.err}`);
      console.error(fences[r.i].split("\n").slice(0, 4).join("\n"));
    }
  }
} finally { await browser.close(); }
if (failed) { console.error(`\n${failed}/${fences.length} fence(s) failed to render.`); process.exit(1); }
console.log(`OK: ${fences.length} mermaid fence(s) render.`);
process.exit(0);
