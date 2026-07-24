// Build-time icon-pack bundler for the LORA reader's Mermaid icon nodes.
//
// The reader renders Mermaid diagrams that can use iconify icon nodes
// (`n@{ icon: "logos:aws-s3" }`) and architecture-beta services so a real
// product logo (the actual S3 bucket, Lambda lambda, Kafka, Redis, ...) shows
// instead of a plain labeled box. Mermaid resolves those ids from icon packs
// registered via `mermaid.registerIconPacks()`. To stay OFFLINE (Pages PWA +
// Android WebView) the pack must be a BUNDLED local JSON, never fetched from
// iconify's CDN — this script copies the pack(s) out of the CI-only
// @iconify-json devDependency into game/vendor/, where both the runtime
// (ensureMermaid) and the build-time pre-renderer (build_diagrams.mjs) load it.
//
// CI-only, like build_diagrams.mjs / build_banks.sh. The output is gitignored
// and regenerated on every deploy. The full pack is bundled (not a curated
// subset) so an author can reference ANY `logos:` id without maintaining a
// registry — "don't miss". At runtime the vendor JSON is only fetched on the
// engine-fallback path (an un-baked/edited fence); the normal path is the
// pre-rendered .mmz, which already has the icon baked in.
//
// Add a pack: append to PACKS. Each must be an installed @iconify-json/<name>.
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const REPO = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const VENDOR = path.join(REPO, "src/main/java/com/rutik/systemdesign/game/vendor");

// name = the prefix authors reference (`logos:aws-s3`). Order = registration
// order; `logos` (full colour) is primary. simple-icons/devicon can be added
// here for even broader coverage (monochrome — they inherit currentColor).
const PACKS = [
  { name: "logos", pkg: "@iconify-json/logos" },
];

fs.mkdirSync(VENDOR, { recursive: true });

for (const { name, pkg } of PACKS) {
  const src = path.join(REPO, "node_modules", pkg, "icons.json");
  if (!fs.existsSync(src)) {
    console.error(`[icons] MISSING ${pkg} — is it in devDependencies and installed?`);
    process.exitCode = 1;
    continue;
  }
  const pack = JSON.parse(fs.readFileSync(src, "utf8"));
  // The pack JSON is already the exact shape registerIconPacks() wants
  // ({ prefix, icons, width, height, ... }). Re-stringify minified to drop any
  // stray whitespace; keep it byte-stable across runs (sorted keys not needed —
  // the source order is stable).
  const out = path.join(VENDOR, `icons-${name}.json`);
  fs.writeFileSync(out, JSON.stringify(pack));
  const kb = (fs.statSync(out).size / 1024).toFixed(0);
  console.log(`[icons] ${name}: ${Object.keys(pack.icons).length} icons -> ${path.relative(REPO, out)} (${kb} KB)`);
}
console.log("[icons] done");
