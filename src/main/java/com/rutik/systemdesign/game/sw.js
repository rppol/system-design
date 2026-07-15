/* System Design Daily - service worker (Pages-only PWA).
   The cache name embeds a per-deploy build stamp: pages.yml rewrites the BUILD
   placeholder below on every deploy so a new deploy always busts the app-shell
   cache. Bump the "v1" when you change the caching STRATEGY (not needed for content
   edits — banks are stale-while-revalidate, so fresh data arrives on the next load).
   Bump the "vN" prefix (v2 -> v3, ...) when you change the caching STRATEGY. */
const BUILD = "__BUILD__";
const CACHE = "sd-daily-v2-" + BUILD;
// [W5] Content .md pages live in their own capped bucket so lazily-fetched repo
// content can't grow the shell cache unbounded. FIFO-trimmed to MD_CAP on write.
const MD_CACHE = CACHE + "-md";
const MD_CAP = 80;

// App shell precached on install. Content .md files and section banks are cached
// lazily on first fetch (see the fetch handler) so we never precache the whole repo.
const SHELL = [
  "./", "index.html", "app.js", "style.css",
  "manifest.webmanifest", "questions/index.json",
  "apple-touch-icon.png", "icon-512.png", "icon-512-maskable.png",
];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE && k !== MD_CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

// Stale-while-revalidate: serve the cached copy immediately, refresh it in the
// background. Used for question/graph JSON so a redeploy's new banks land on the
// next load without a version bump.
function staleWhileRevalidate(req) {
  return caches.open(CACHE).then((cache) =>
    cache.match(req).then((hit) => {
      const net = fetch(req).then((res) => {
        if (res && res.ok) cache.put(req, res.clone());
        return res;
      }).catch(() => hit);
      return hit || net;
    })
  );
}

// Network-first with a cache fallback: for content .md pages and everything else,
// prefer fresh but survive offline.
function networkFirst(req) {
  return caches.open(CACHE).then((cache) =>
    fetch(req).then((res) => {
      if (res && res.ok) cache.put(req, res.clone());
      return res;
    }).catch(() => cache.match(req))
  );
}

// [W5] Network-first for content .md pages, cached in the capped MD_CACHE bucket.
// On a successful fetch, store then FIFO-trim to MD_CAP (cache.keys() returns
// insertion order, so keys[0..] are the oldest). Offline -> serve the cached copy.
function networkFirstMd(req) {
  return caches.open(MD_CACHE).then((cache) =>
    fetch(req).then((res) => {
      if (res && res.ok) {
        cache.put(req, res.clone()).then(() =>
          cache.keys().then((keys) => {
            for (let i = 0; i < keys.length - MD_CAP; i++) cache.delete(keys[i]);
          })
        );
      }
      return res;
    }).catch(() => cache.match(req))
  );
}

self.addEventListener("fetch", (e) => {
  const req = e.request;
  if (req.method !== "GET") return;
  const url = new URL(req.url);
  if (url.origin !== location.origin) return;   // let cross-origin (none expected) pass through
  if (/\/(questions|graph)\/[^/]+\.json$/.test(url.pathname)) {
    e.respondWith(staleWhileRevalidate(req));
  } else if (/\.md$/i.test(url.pathname)) {
    e.respondWith(networkFirstMd(req));           // [W5] capped content-page bucket
  } else {
    e.respondWith(networkFirst(req));
  }
});
