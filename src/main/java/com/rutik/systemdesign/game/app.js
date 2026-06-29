/* System Design Daily - 5-minute blitz. Vanilla JS, no build step. */

const QUESTIONS_PER_BLITZ = 10;
const DAILY_XP_GOAL = 100;
const SECTION_LABELS = {
  backend: "Backend Engineering", book: "Book Summaries",
  cs_fundamentals: "CS Fundamentals", database: "Databases", devops: "DevOps & Cloud",
  hld: "High-Level Design", java: "Java", lld: "Low-Level Design",
  llm: "LLM Engineering", ml: "Machine Learning", python: "Python", spring: "Spring",
};

const app = document.getElementById("app");
const state = {
  index: null, today: null, progress: null,
  deck: [], queue: [], cursor: 0, section: null, modules: null,
  combo: 0, maxCombo: 0, sessionXp: 0, inQuiz: false, answered: false,
  curOptsLen: 0, replayFn: null,
};

/* ---------- helpers ---------- */
const todayISO = () => new Date().toLocaleDateString("en-CA"); // YYYY-MM-DD, local
const el = (sel) => document.querySelector(sel);
const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
const label = (s) => SECTION_LABELS[s] || s;

function shuffle(a) {
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

async function apiGet(path, fallback) {
  try { const r = await fetch(path, { cache: "no-store" }); if (!r.ok) throw 0; return await r.json(); }
  catch { return fallback; }
}

/* ---------- sound (zero-asset Web Audio) ---------- */
const sfx = (() => {
  let ctx;
  const on = () => localStorage.getItem("sd_mute") !== "1";
  function tone(freq, dur, type = "sine", gain = 0.06, delay = 0) {
    if (!on()) return;
    try {
      ctx = ctx || new (window.AudioContext || window.webkitAudioContext)();
      if (ctx.state === "suspended") ctx.resume();
      const t = ctx.currentTime + delay;
      const o = ctx.createOscillator(), g = ctx.createGain();
      o.type = type; o.frequency.value = freq;
      o.connect(g); g.connect(ctx.destination);
      g.gain.setValueAtTime(gain, t);
      g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
      o.start(t); o.stop(t + dur);
    } catch { /* audio unavailable */ }
  }
  return {
    correct() { tone(660, 0.12); tone(880, 0.14, "sine", 0.06, 0.08); },
    wrong() { tone(190, 0.22, "sawtooth", 0.05); },
    combo() { tone(880, 0.08); tone(1175, 0.1, "triangle", 0.05, 0.06); tone(1568, 0.12, "triangle", 0.05, 0.12); },
    finish() { [523, 659, 784, 1047].forEach((f, i) => tone(f, 0.2, "triangle", 0.06, i * 0.1)); },
    isOn: on,
    toggle() { const wasOn = on(); localStorage.setItem("sd_mute", wasOn ? "1" : "0"); return !wasOn; },
  };
})();

function confetti() {
  const colors = ["#6ea8fe", "#8b7bff", "#34d399", "#fbbf24", "#f87171"];
  const c = document.createElement("div");
  c.className = "confetti";
  for (let i = 0; i < 90; i++) {
    const p = document.createElement("i");
    p.style.left = Math.random() * 100 + "vw";
    p.style.background = colors[i % colors.length];
    p.style.animationDelay = (Math.random() * 0.4).toFixed(2) + "s";
    p.style.setProperty("--rot", (Math.random() * 720 - 360) + "deg");
    c.appendChild(p);
  }
  document.body.appendChild(c);
  setTimeout(() => c.remove(), 2400);
}

function countUp(node, to) {
  const dur = 600, start = performance.now();
  function step(now) {
    const k = Math.min(1, (now - start) / dur);
    node.textContent = "+" + Math.round(to * (1 - Math.pow(1 - k, 3)));
    if (k < 1) requestAnimationFrame(step);
  }
  requestAnimationFrame(step);
}

/* ---------- persistence ---------- */
async function loadProgress() {
  const fill = (p) => { if (!p.reviews) p.reviews = {}; if (p.freezes == null) p.freezes = 2; if (!p.freezeUsedOn) p.freezeUsedOn = []; return p; };
  const p = await apiGet("/api/progress", null);
  if (p) return fill(p);
  const ls = localStorage.getItem("sd_progress");
  return ls ? fill(JSON.parse(ls))
    : { streak: 0, longestStreak: 0, lastPlayed: null, totalXP: 0, sections: {}, history: [], reviews: {}, freezes: 2, freezeUsedOn: [] };
}

async function saveSession(session) {
  try {
    const r = await fetch("/api/progress", {
      method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(session),
    });
    if (!r.ok) throw 0;
    const data = await r.json();
    state.progress = data.progress;
    return { xp: data.xpEarned, freezeUsed: !!data.freezeUsed };
  } catch {
    return saveSessionLocal(session); // offline fallback (no spaced-rep persistence)
  }
}

// Mirrors record_session's streak-freeze logic in server.py so the offline
// fallback agrees with the server: a single missed day is covered by a freeze.
function saveSessionLocal(session) {
  const p = state.progress;
  if (p.freezes == null) p.freezes = 2;
  if (!p.freezeUsedOn) p.freezeUsedOn = [];
  let correct = 0;
  for (const res of session.results || []) {
    const sec = (p.sections[res.section] = p.sections[res.section] || { seen: 0, correct: 0 });
    sec.seen += 1;
    sec.lastPlayed = session.date;
    if (res.status === "correct") { sec.correct += 1; correct += 1; }
  }
  const dayMs = 86400000, atMidnight = (iso) => new Date(iso + "T00:00:00");
  let freezeUsed = false, advanced = false;
  if (!p.lastPlayed) { p.streak = 1; advanced = true; }
  else {
    const gap = Math.round((atMidnight(session.date) - atMidnight(p.lastPlayed)) / dayMs);
    if (gap <= 0) { /* same calendar day: streak unchanged */ }
    else if (gap === 1) { p.streak = (p.streak || 0) + 1; advanced = true; }
    else if (gap === 2 && (p.freezes || 0) > 0) {
      p.freezes -= 1;
      const missed = new Date(atMidnight(session.date) - dayMs).toLocaleDateString("en-CA");
      p.freezeUsedOn.push(missed); p.freezeUsedOn = p.freezeUsedOn.slice(-60);
      p.streak = (p.streak || 0) + 1; advanced = true; freezeUsed = true;
    } else { p.streak = 1; advanced = true; }
  }
  if (advanced && p.streak > 0 && p.streak % 7 === 0) p.freezes = Math.min(3, (p.freezes || 0) + 1);
  p.longestStreak = Math.max(p.longestStreak || 0, p.streak);
  p.lastPlayed = session.date;
  const xp = correct * 10 + p.streak * 5 + (session.bonusXp || 0);
  p.totalXP = (p.totalXP || 0) + xp;
  (p.history = p.history || []).push({ date: session.date, correct, xp });
  localStorage.setItem("sd_progress", JSON.stringify(p));
  return { xp, freezeUsed };
}

/* ---------- selection ---------- */
function pickSection() {
  const avail = Object.keys(state.index.sections);
  if (state.today && avail.includes(state.today.section)) return state.today.section;
  const seen = state.progress.sections || {};
  const unplayed = avail.filter((s) => !seen[s]);
  if (unplayed.length) return shuffle(unplayed)[0];
  return avail.sort((a, b) => (seen[a]?.seen || 0) - (seen[b]?.seen || 0))[0];
}

function dueReviews() {
  const t = todayISO();
  return Object.entries(state.progress.reviews || {})
    .filter(([, r]) => r.due && r.due <= t)
    .sort((a, b) => (a[1].due < b[1].due ? -1 : 1));
}

function todaysXp() {
  const t = todayISO();
  return (state.progress.history || []).filter((h) => h.date === t).reduce((a, h) => a + (h.xp || 0), 0);
}

/* ---------- mastery tiers, level, decay ---------- */
// Tier needs both volume (seen) and accuracy, so a tier reflects durable mastery
// rather than a lucky short run.
function sectionTier(st) {
  if (!st || !st.seen) return null;
  const acc = st.correct / st.seen;
  if (st.seen >= 40 && acc >= 0.85) return "Gold";
  if (st.seen >= 20 && acc >= 0.70) return "Silver";
  if (st.seen >= 8 && acc >= 0.50) return "Bronze";
  return null;
}
const levelFromXP = (xp) => Math.floor((xp || 0) / 250) + 1;

// Most-invested section not practiced in a week, to nudge a refresh.
function rustiestSection() {
  const secs = state.progress.sections || {};
  const today = new Date(todayISO() + "T00:00:00");
  let best = null;
  for (const [s, st] of Object.entries(secs)) {
    if (!st.lastPlayed || !st.seen) continue;
    const days = Math.round((today - new Date(st.lastPlayed + "T00:00:00")) / 86400000);
    if (days >= 7 && (!best || days > best.days || (days === best.days && st.seen > best.seen)))
      best = { s, days, seen: st.seen };
  }
  return best;
}

function refreshStats() {
  el("#streakVal").textContent = state.progress.streak || 0;
  el("#xpVal").textContent = state.progress.totalXP || 0;
  const lv = el("#lvlVal");
  if (lv) lv.textContent = levelFromXP(state.progress.totalXP);
}

/* ---------- home ---------- */
function goalRing() {
  const xp = todaysXp(), pct = Math.min(1, xp / DAILY_XP_GOAL);
  const r = 26, circ = 2 * Math.PI * r, off = circ * (1 - pct);
  const done = xp >= DAILY_XP_GOAL;
  return `<div class="goal" title="Daily goal">
      <svg width="64" height="64" viewBox="0 0 64 64">
        <circle cx="32" cy="32" r="${r}" class="ring-bg"/>
        <circle cx="32" cy="32" r="${r}" class="ring-fg ${done ? "done" : ""}"
          stroke-dasharray="${circ.toFixed(1)}" stroke-dashoffset="${off.toFixed(1)}"/>
      </svg>
      <div class="goal-txt">${done ? "✓" : xp}<span>/${DAILY_XP_GOAL}</span></div>
    </div>`;
}

function renderHome() {
  state.inQuiz = false;
  refreshStats();
  const section = pickSection();
  const coachMsg = state.today && state.today.message;
  const streak = state.progress.streak || 0;
  const freezes = state.progress.freezes || 0;
  const freezeBit = freezes > 0
    ? ` <span class="freeze-chip" title="Streak freezes auto-cover a single missed day">&#10052;&#65039; ${freezes}</span>` : "";
  const streakLine = streak > 0
    ? `You're on a <b>${streak}-day</b> streak. Keep it alive.${freezeBit}`
    : `Start your streak today &mdash; just 5 minutes.${freezeBit}`;
  const due = dueReviews();
  const reviewCard = due.length
    ? `<button class="review-card" id="reviewBtn">
         <div><div class="eyebrow good">Spaced repetition</div>
         <h2>${due.length} question${due.length === 1 ? "" : "s"} due for review</h2>
         <p class="msg">Resurface what you've missed before it fades. ~${Math.min(due.length, QUESTIONS_PER_BLITZ)} now.</p></div>
         <span class="review-go">Review &rarr;</span>
       </button>`
    : "";
  const worst = weakSections().filter((x) => x.acc < 0.7)[0];
  const weakCard = worst
    ? `<button class="review-card weak" id="weakBtn">
         <div><div class="eyebrow warn">Targeted practice</div>
         <h2>Drill your weak spots</h2>
         <p class="msg">Lowest mastery: <b>${esc(label(worst.s))}</b> at ${Math.round(worst.acc * 100)}%. Focus a round there.</p></div>
         <span class="review-go warn">Drill &rarr;</span>
       </button>`
    : "";
  const rusty = rustiestSection();
  const rustyNote = rusty
    ? `<button class="rusty-note" id="rustyBtn">&#9203; <b>${esc(label(rusty.s))}</b> is getting rusty &mdash; ${rusty.days} days since you practiced it. Refresh it &rarr;</button>`
    : "";
  const secs = state.index.sections, p = state.progress;
  const tiles = Object.keys(secs).sort().map((s) => {
    const st = (p.sections && p.sections[s]) || { seen: 0, correct: 0 };
    const acc = st.seen ? Math.round((st.correct / st.seen) * 100) : null;
    return `<button class="tile ${s === section ? "suggested" : ""}" data-section="${s}">
        <span class="tname">${esc(label(s))}</span>
        <span class="tmeta">${secs[s]} Qs &middot; ${acc === null ? "new" : acc + "% mastery"}</span>
      </button>`;
  }).join("");
  app.innerHTML = `
    <div class="hero">
      <div class="hero-row">${goalRing()}<div><h1>Today's 5-minute blitz</h1><p>${streakLine}</p></div></div>
    </div>
    ${reviewCard}
    ${weakCard}
    ${rustyNote}
    <div class="topic-card">
      <div class="eyebrow">Suggested for today</div>
      <h2>${esc(label(section))}</h2>
      <p class="msg">${esc(coachMsg || `${QUESTIONS_PER_BLITZ} questions pulled from your ${label(section)} notes.`)}</p>
      <button class="cta" id="startBtn">Start &mdash; ${QUESTIONS_PER_BLITZ} questions<small>~5 min &middot; ${deckMode() === "flash" ? "flashcards" : "multiple choice"}</small></button>
    </div>
    <div class="section-h">Or pick a section &mdash; then choose sub-topics</div>
    <div class="grid">${tiles}</div>`;
  el("#startBtn").addEventListener("click", () => startBlitz(section));
  if (due.length) el("#reviewBtn").addEventListener("click", startReview);
  if (worst) el("#weakBtn").addEventListener("click", startWeakSpots);
  if (rusty) el("#rustyBtn").addEventListener("click", () => startBlitz(rusty.s));
  document.querySelectorAll(".tile").forEach((b) =>
    b.addEventListener("click", () => openTopics(b.dataset.section)));
}

/* ---------- bank loading / sub-topic picker ---------- */
const bankCache = {};
async function loadBank(section) {
  if (!bankCache[section]) bankCache[section] = await apiGet(`questions/${section}.json`, null);
  return bankCache[section];
}

function modulesOf(bank) {
  const map = new Map();
  for (const q of bank) {
    const m = map.get(q.module) || { module: q.module, name: q.moduleName, count: 0 };
    m.count++; map.set(q.module, m);
  }
  return [...map.values()].sort((a, b) => a.name.localeCompare(b.name));
}

async function openTopics(section) {
  app.innerHTML = `<div class="loading">Loading ${esc(label(section))}&hellip;</div>`;
  const bank = await loadBank(section);
  if (!bank || !bank.length) {
    app.innerHTML = `<div class="error">Couldn't load questions for ${esc(section)}. Run <code>python3 extract.py</code>.</div>`;
    return;
  }
  const mods = modulesOf(bank);
  const rows = mods.map((m) =>
    `<label class="modrow"><input type="checkbox" class="modcheck" value="${esc(m.module)}" checked />
       <span class="mname">${esc(m.name)}</span><span class="mcount">${m.count}</span></label>`).join("");
  app.innerHTML = `
    <div class="hero"><h1>${esc(label(section))}</h1><p>Pick the sub-topics to drill &mdash; or keep them all.</p></div>
    <div class="topicbar">
      <button class="ghost" id="allBtn">Select all</button>
      <button class="ghost" id="noneBtn">Clear</button>
      <span class="selcount" id="selCount"></span>
    </div>
    <div class="modlist">${rows}</div>
    <div class="qactions">
      <button class="ghost" id="backBtn">&larr; Back</button>
      <button class="cta inline" id="startSel">Start blitz</button>
    </div>`;
  const checks = () => [...document.querySelectorAll(".modcheck")];
  const selected = () => checks().filter((c) => c.checked).map((c) => c.value);
  const updateCount = () => {
    const sel = selected();
    const n = mods.filter((m) => sel.includes(m.module)).reduce((a, m) => a + m.count, 0);
    el("#selCount").textContent = `${sel.length} topic${sel.length === 1 ? "" : "s"} · ${n} questions`;
    el("#startSel").disabled = sel.length === 0;
  };
  checks().forEach((c) => c.addEventListener("change", updateCount));
  el("#allBtn").addEventListener("click", () => { checks().forEach((c) => (c.checked = true)); updateCount(); });
  el("#noneBtn").addEventListener("click", () => { checks().forEach((c) => (c.checked = false)); updateCount(); });
  el("#backBtn").addEventListener("click", renderHome);
  el("#startSel").addEventListener("click", () => startBlitz(section, selected()));
  updateCount();
}

/* ---------- deck building ---------- */
function makeItem(q) {
  const opts = shuffle([{ t: q.correct, ok: true }, ...q.distractors.map((d) => ({ t: d, ok: false }))]);
  return { q, opts, status: "pending", boss: false };
}

// Quiz vs flashcard is a global, persisted preference toggled from the top bar.
function deckMode() { return localStorage.getItem("sd_mode") === "flash" ? "flash" : "quiz"; }

function startDeck(questions, replayFn) {
  state.mode = deckMode();
  const items = questions.map(makeItem);
  if (state.mode === "flash") {
    state.deck = shuffle(items);                 // no boss ordering for self-grade cards
  } else {
    // boss round: advanced-difficulty questions go last and are worth 2x
    const normal = items.filter((it) => it.q.difficulty !== "advanced");
    const boss = items.filter((it) => it.q.difficulty === "advanced");
    boss.forEach((it) => (it.boss = true));
    state.deck = [...normal, ...boss];
  }
  state.queue = state.deck.map((_, i) => i);
  state.cursor = 0;
  state.combo = 0; state.maxCombo = 0; state.sessionXp = 0;
  state.replayFn = replayFn;
  state.mode === "flash" ? renderCard() : renderQuestion();
}

async function startBlitz(section, modules) {
  app.innerHTML = `<div class="loading">Loading ${esc(label(section))}&hellip;</div>`;
  let bank = await loadBank(section);
  if (!bank || !bank.length) {
    app.innerHTML = `<div class="error">Couldn't load questions for ${esc(section)}. Run <code>python3 extract.py</code>.</div>`;
    return;
  }
  if (modules && modules.length) bank = bank.filter((q) => modules.includes(q.module));
  state.section = section;
  state.modules = modules && modules.length ? modules : null;
  const picked = shuffle(bank.slice()).slice(0, QUESTIONS_PER_BLITZ);
  startDeck(picked, () => startBlitz(section, state.modules));
}

async function startReview() {
  app.innerHTML = `<div class="loading">Gathering your review deck&hellip;</div>`;
  const due = dueReviews().slice(0, QUESTIONS_PER_BLITZ + 4);
  const bySec = {};
  due.forEach(([id, r]) => (bySec[r.section] = bySec[r.section] || []).push(id));
  const items = [];
  for (const sec of Object.keys(bySec)) {
    const bank = await loadBank(sec);
    if (!bank) continue;
    const byId = new Map(bank.map((q) => [q.id, q]));
    for (const id of bySec[sec]) { const q = byId.get(id); if (q) items.push(q); }
  }
  if (!items.length) { renderHome(); return; }
  state.section = "review"; state.modules = null;
  startDeck(items.slice(0, QUESTIONS_PER_BLITZ), startReview);
}

/* ---------- weak spots ---------- */
// Sections with enough data, ranked worst-accuracy first.
function weakSections() {
  const secs = state.progress.sections || {};
  return Object.entries(secs)
    .filter(([, st]) => (st.seen || 0) >= 5)
    .map(([s, st]) => ({ s, acc: st.correct / st.seen, seen: st.seen }))
    .sort((a, b) => a.acc - b.acc);
}

async function startWeakSpots() {
  app.innerHTML = `<div class="loading">Finding your weak spots&hellip;</div>`;
  const weak = weakSections().filter((x) => x.acc < 0.7).slice(0, 4);
  const pool = (weak.length ? weak : weakSections().slice(0, 3));
  if (!pool.length) { renderHome(); return; }
  const reviews = state.progress.reviews || {};
  const banks = {}, byId = {};
  for (const p of pool) {
    const b = await loadBank(p.s);
    if (b) { banks[p.s] = b; byId[p.s] = new Map(b.map((q) => [q.id, q])); }
  }
  const items = [], seen = new Set();
  const add = (q) => { if (q && !seen.has(q.id)) { items.push(q); seen.add(q.id); } };
  // 1) known trouble questions in weak sections: most lapses first
  Object.entries(reviews)
    .filter(([, r]) => (r.lapses || 0) > 0 && byId[r.section])
    .sort((a, b) => (b[1].lapses || 0) - (a[1].lapses || 0))
    .forEach(([id, r]) => { if (items.length < QUESTIONS_PER_BLITZ) add(byId[r.section].get(id)); });
  // 2) fill with random questions from the weak sections
  const filler = [];
  for (const p of pool) if (banks[p.s]) filler.push(...banks[p.s]);
  shuffle(filler).forEach((q) => { if (items.length < QUESTIONS_PER_BLITZ) add(q); });
  if (!items.length) { renderHome(); return; }
  state.section = "weakspots"; state.modules = null;
  startDeck(items.slice(0, QUESTIONS_PER_BLITZ), startWeakSpots);
}

/* ---------- quiz ---------- */
function isLastInQueue() { return state.cursor >= state.queue.length - 1; }

function comboMult() { return state.combo >= 5 ? 3 : state.combo >= 3 ? 2 : 1; }

function renderQuestion() {
  const idx = state.queue[state.cursor];
  const item = state.deck[idx];
  const { q, opts } = item;
  const teach = item.status === "skipped";
  state.inQuiz = true; state.answered = false; state.curOptsLen = opts.length;
  const DONE = ["correct", "wrong", "learned"];
  const dots = state.deck.map((it, i) =>
    `<span class="dot ${DONE.includes(it.status) ? "done" : ""} ${it.boss ? "boss" : ""} ${i === idx ? "cur" : ""}"></span>`).join("");
  const bossBanner = item.boss && !teach
    ? `<div class="boss-banner">&#9889; BOSS QUESTION &middot; 2&times; XP</div>` : "";
  const teachBlock = teach
    ? `<div class="teach-banner">Concept review &middot; you skipped this earlier. Learn it, then lock it in.</div>
       <div class="reveal concept show"><b>Concept:</b> ${esc(q.answerFull)}</div>` : "";
  const comboChip = state.combo >= 2 ? `<span class="combo">&#128293; ${state.combo} combo &middot; ${comboMult()}&times;</span>` : "";
  app.innerHTML = `
    <div class="qhead">
      <span class="module">${esc(label(q.section))} &middot; ${esc(q.moduleName)}</span>
      <span class="dots">${dots}</span>
    </div>
    ${bossBanner}${teachBlock}
    <div class="qtext">${esc(q.question)} ${comboChip}</div>
    <div class="options">
      ${opts.map((o, i) => `<button class="opt" data-i="${i}"><kbd>${i + 1}</kbd>${esc(o.t)}<span class="mark"></span></button>`).join("")}
    </div>
    <div class="reveal" id="reveal"></div>
    <div class="qactions">
      ${item.status === "pending" ? `<button class="skip" id="skipBtn">Skip for now (S) &rarr;</button>` : "<span></span>"}
      <button class="next" id="nextBtn">${isLastInQueue() ? "Finish" : "Next (↵)"}</button>
    </div>`;
  document.querySelectorAll(".opt").forEach((b) =>
    b.addEventListener("click", () => answer(parseInt(b.dataset.i, 10))));
  if (item.status === "pending") el("#skipBtn").addEventListener("click", skipQuestion);
  el("#nextBtn").addEventListener("click", nextQuestion);
}

function answer(i) {
  if (state.answered) return;
  state.answered = true;
  const item = state.deck[state.queue[state.cursor]];
  const { q, opts } = item;
  const teach = item.status === "skipped";
  document.querySelectorAll(".opt").forEach((b, k) => {
    b.disabled = true;
    if (opts[k].ok) { b.classList.add("correct"); b.querySelector(".mark").textContent = "✓"; }
    if (k === i && !opts[k].ok) { b.classList.add("wrong"); b.querySelector(".mark").textContent = "✗"; }
  });
  const right = opts[i].ok;
  if (teach) {
    item.status = "learned";
  } else if (right) {
    item.status = "correct";
    state.combo += 1; state.maxCombo = Math.max(state.maxCombo, state.combo);
    const gain = 10 * comboMult() * (item.boss ? 2 : 1);
    state.sessionXp += gain;
    if (state.combo === 3 || state.combo === 5 || state.combo >= 7) sfx.combo(); else sfx.correct();
  } else {
    item.status = "wrong";
    state.combo = 0;
    sfx.wrong();
  }
  const sk = el("#skipBtn"); if (sk) sk.remove();
  if (!teach) {
    const rev = el("#reveal");
    rev.innerHTML = `<b>Full answer:</b> ${esc(q.answerFull)}
      <button class="deeper" id="deeperBtn">Dive deeper into ${esc(q.moduleName)} &rarr;</button>`;
    rev.classList.add("show");
    el("#deeperBtn").addEventListener("click", () => openReader(q.module, q.moduleName));
  }
  el("#nextBtn").classList.add("show");
}

function skipQuestion() {
  const idx = state.queue[state.cursor];
  state.deck[idx].status = "skipped";
  state.queue.push(idx); // returns at the end in teach mode
  nextQuestion();
}

function nextQuestion() {
  state.cursor++;
  if (state.cursor < state.queue.length) renderQuestion();
  else finish();
}

/* ---------- flashcard (self-grade) mode ---------- */
function renderCard() {
  const idx = state.queue[state.cursor];
  const item = state.deck[idx];
  const { q } = item;
  state.inQuiz = true; state.answered = false; state.curOptsLen = 0;
  const DONE = ["correct", "wrong", "learned"];
  const dots = state.deck.map((it, i) =>
    `<span class="dot ${DONE.includes(it.status) ? "done" : ""} ${i === idx ? "cur" : ""}"></span>`).join("");
  app.innerHTML = `
    <div class="qhead">
      <span class="module">${esc(label(q.section))} &middot; ${esc(q.moduleName)}</span>
      <span class="dots">${dots}</span>
    </div>
    <div class="flash-label">Flashcard &middot; recall it, then grade yourself</div>
    <div class="qtext">${esc(q.question)}</div>
    <div class="reveal" id="reveal"></div>
    <div class="qactions" id="cardActions">
      <span></span>
      <button class="next show" id="revealBtn">Reveal answer (Space)</button>
    </div>`;
  el("#revealBtn").addEventListener("click", revealCard);
}

function revealCard() {
  if (state.answered) return;
  state.answered = true;
  const { q } = state.deck[state.queue[state.cursor]];
  const rev = el("#reveal");
  rev.innerHTML = `<b>Answer:</b> ${esc(q.answerFull)}
    <button class="deeper" id="deeperBtn">Dive deeper into ${esc(q.moduleName)} &rarr;</button>`;
  rev.classList.add("show");
  el("#deeperBtn").addEventListener("click", () => openReader(q.module, q.moduleName));
  el("#cardActions").innerHTML = `
    <button class="grade miss" id="missBtn"><kbd>1</kbd> Missed it</button>
    <button class="grade got" id="gotBtn"><kbd>2</kbd> Got it</button>`;
  el("#missBtn").addEventListener("click", () => gradeCard(false));
  el("#gotBtn").addEventListener("click", () => gradeCard(true));
}

// Self-grade feeds the SAME results pipeline as the MCQ blitz, so it drives the
// existing SM-2 schedule. XP is flat (no combo/boss) so self-grading can't inflate
// score versus the verifiable multiple-choice path.
function gradeCard(got) {
  if (!state.answered) return;
  const item = state.deck[state.queue[state.cursor]];
  if (got) { item.status = "correct"; state.sessionXp += 10; sfx.correct(); }
  else { item.status = "wrong"; sfx.wrong(); }
  state.cursor++;
  if (state.cursor < state.queue.length) renderCard();
  else finish();
}

async function finish() {
  state.inQuiz = false;
  app.innerHTML = `<div class="loading">Saving your progress&hellip;</div>`;
  const total = state.deck.length;
  const correct = state.deck.filter((d) => d.status === "correct").length;
  const learned = state.deck.filter((d) => d.status === "learned").length;
  const bonusXp = Math.max(0, state.sessionXp - correct * 10);
  const results = state.deck.map((d) => ({ id: d.q.id, section: d.q.section, module: d.q.module, status: d.status }));
  const { xp, freezeUsed } = await saveSession({ date: todayISO(), section: state.section, results, bonusXp });
  refreshStats();
  const pct = Math.round((correct / total) * 100);
  const flawless = pct === 100 && total > 0;
  if (flawless) { confetti(); sfx.finish(); }
  const cheer = flawless ? "Flawless! " : pct >= 70 ? "Strong work. " : pct >= 40 ? "Good progress. " : "Every rep counts. ";
  const freezeNote = freezeUsed
    ? `<div class="freeze-saved">&#10052;&#65039; Streak saved &mdash; 1 freeze used (${state.progress.freezes || 0} left)</div>` : "";
  const extraBadges =
    (learned ? `<div class="badge"><div class="n">${learned}</div><div class="l">Learned</div></div>` : "") +
    (state.maxCombo >= 2 ? `<div class="badge"><div class="n">${state.maxCombo}&times;</div><div class="l">Best combo</div></div>` : "");
  app.innerHTML = `
    <div class="result">
      <div class="scorering">${correct}<small>/${total}</small></div>
      <p class="sub">${cheer}${pct}% known${learned ? ` &middot; ${learned} learned` : ""}</p>
      ${freezeNote}
      <div class="badges">
        <div class="badge"><div class="n" id="xpCount">+0</div><div class="l">XP</div></div>
        ${extraBadges}
        <div class="badge"><div class="n">${state.progress.streak || 0}</div><div class="l">Day streak</div></div>
        <div class="badge"><div class="n">${state.progress.totalXP || 0}</div><div class="l">Total XP</div></div>
      </div>
      <div class="row">
        <button class="primary" id="againBtn">Play another</button>
        <button class="ghost" id="homeBtn">Home</button>
        <button class="ghost" id="progBtn">View progress</button>
      </div>
    </div>`;
  countUp(el("#xpCount"), xp);
  el("#againBtn").addEventListener("click", () => (state.replayFn ? state.replayFn() : renderHome()));
  el("#homeBtn").addEventListener("click", renderHome);
  el("#progBtn").addEventListener("click", renderProgress);
}

/* ---------- progress ---------- */
// GitHub-style contribution grid from the (already persisted) history array.
// Columns are weeks (start aligns to a Sunday); cells are coloured by XP bucket.
function heatmapHTML(history) {
  const xpByDay = new Map();
  for (const h of history || []) xpByDay.set(h.date, (xpByDay.get(h.date) || 0) + (h.xp || 0));
  const WEEKS = 17, dayMs = 86400000;
  const today = new Date(todayISO() + "T00:00:00");
  const end = new Date(today); end.setDate(end.getDate() + (6 - today.getDay())); // Sat of this week
  const start = new Date(end.getTime() - (WEEKS * 7 - 1) * dayMs);                 // a Sunday
  let cells = "";
  for (let i = 0; i < WEEKS * 7; i++) {
    const d = new Date(start.getTime() + i * dayMs);
    const iso = d.toLocaleDateString("en-CA");
    const xp = xpByDay.get(iso) || 0;
    if (d > today) { cells += `<span class="hmcell hm-future"></span>`; continue; }
    const lvl = xp === 0 ? 0 : xp < 30 ? 1 : xp < 70 ? 2 : xp < 120 ? 3 : 4;
    cells += `<span class="hmcell hm-l${lvl}" title="${iso}: ${xp} XP"></span>`;
  }
  return `<div class="section-h">Activity</div>
    <div class="heatmap">${cells}</div>
    <div class="hmlegend">Less
      <span class="hmcell hm-l0"></span><span class="hmcell hm-l1"></span><span class="hmcell hm-l2"></span><span class="hmcell hm-l3"></span><span class="hmcell hm-l4"></span>
      More</div>`;
}

function renderProgress() {
  state.inQuiz = false;
  refreshStats();
  const p = state.progress, secs = state.index.sections;
  const tiles = Object.keys(secs).sort().map((s) => {
    const st = (p.sections && p.sections[s]) || { seen: 0, correct: 0 };
    const acc = st.seen ? Math.round((st.correct / st.seen) * 100) : 0;
    const tier = sectionTier(st);
    const tierChip = tier ? `<span class="tier ${tier.toLowerCase()}">${tier}</span>` : "";
    return `<div class="sectiontile">
        <div class="top"><span class="name">${esc(label(s))}${tierChip}</span>
        <span class="pct">${st.seen ? acc + "% &middot; " + st.seen + " seen" : "not started"}</span></div>
        <div class="bar"><span style="width:${acc}%"></span></div>
      </div>`;
  }).join("");
  const due = dueReviews().length;
  app.innerHTML = `
    <div class="hero"><h1>Your progress</h1></div>
    <div class="badges">
      <div class="badge"><div class="n">${p.streak || 0}</div><div class="l">Streak</div></div>
      <div class="badge"><div class="n">${p.longestStreak || 0}</div><div class="l">Longest</div></div>
      <div class="badge"><div class="n">&#10052;&#65039; ${p.freezes || 0}</div><div class="l">Freezes</div></div>
      <div class="badge"><div class="n">${p.totalXP || 0}</div><div class="l">Total XP</div></div>
      <div class="badge"><div class="n">${due}</div><div class="l">Due review</div></div>
    </div>
    ${heatmapHTML(p.history)}
    <div class="section-h">Mastery by section</div>
    ${tiles}
    <div class="row" style="margin-top:18px"><button class="primary" id="backHome">Back to today</button></div>`;
  el("#backHome").addEventListener("click", renderHome);
}

/* ---------- in-app reader: minimal zero-dep markdown renderer ---------- */
// Covers the constructs this repo actually uses. Fenced blocks render verbatim
// in <pre> so the §5 ASCII diagrams and code keep their alignment.
function mdInline(t) {
  return esc(t)
    .replace(/`([^`]+)`/g, (m, c) => `<code>${c}</code>`)
    .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
    .replace(/(^|[^*])\*([^*\n]+)\*/g, "$1<em>$2</em>")
    .replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, (m, txt, url) => `<a href="${esc(url)}" target="_blank" rel="noopener">${txt}</a>`);
}

function mdRender(src) {
  const lines = src.replace(/\r\n/g, "\n").split("\n");
  const out = [];
  let i = 0;
  while (i < lines.length) {
    const line = lines[i];
    const fence = line.match(/^```/);
    if (fence) {                                   // fenced code / ASCII diagram
      const body = [];
      i++;
      while (i < lines.length && !/^```/.test(lines[i])) { body.push(lines[i]); i++; }
      i++;                                         // skip closing fence
      out.push(`<pre><code>${esc(body.join("\n"))}</code></pre>`);
      continue;
    }
    if (/^\s*\|.*\|\s*$/.test(line) && i + 1 < lines.length &&
        /^\s*\|?[\s:|-]+\|?\s*$/.test(lines[i + 1]) && lines[i + 1].includes("-")) {
      const rows = [line]; i += 2;                 // header + separator consumed
      while (i < lines.length && /^\s*\|.*\|\s*$/.test(lines[i])) { rows.push(lines[i]); i++; }
      const cells = (r) => r.trim().replace(/^\|/, "").replace(/\|$/, "").split("|").map((c) => c.trim());
      let t = "<table><thead><tr>" + cells(rows[0]).map((c) => `<th>${mdInline(c)}</th>`).join("") + "</tr></thead><tbody>";
      for (const r of rows.slice(1)) t += "<tr>" + cells(r).map((c) => `<td>${mdInline(c)}</td>`).join("") + "</tr>";
      out.push(t + "</tbody></table>");
      continue;
    }
    const h = line.match(/^(#{1,6})\s+(.*)$/);
    if (h) { out.push(`<h${h[1].length}>${mdInline(h[2])}</h${h[1].length}>`); i++; continue; }
    if (/^(---+|\*\*\*+|___+)\s*$/.test(line)) { out.push("<hr>"); i++; continue; }
    if (/^>\s?/.test(line)) {
      const body = [];
      while (i < lines.length && /^>\s?/.test(lines[i])) { body.push(lines[i].replace(/^>\s?/, "")); i++; }
      out.push(`<blockquote>${mdInline(body.join(" "))}</blockquote>`);
      continue;
    }
    if (/^\s*([-*+]|\d+\.)\s+/.test(line)) {
      const ordered = /^\s*\d+\.\s+/.test(line), tag = ordered ? "ol" : "ul";
      let items = "";
      while (i < lines.length && /^\s*([-*+]|\d+\.)\s+/.test(lines[i])) {
        items += `<li>${mdInline(lines[i].replace(/^\s*([-*+]|\d+\.)\s+/, ""))}</li>`; i++;
      }
      out.push(`<${tag}>${items}</${tag}>`);
      continue;
    }
    if (/^\s*$/.test(line)) { i++; continue; }
    const para = [];
    while (i < lines.length && !/^\s*$/.test(lines[i]) && !/^(#{1,6})\s/.test(lines[i]) &&
           !/^```/.test(lines[i]) && !/^>\s?/.test(lines[i]) &&
           !/^\s*([-*+]|\d+\.)\s+/.test(lines[i]) && !/^(---+|\*\*\*+)\s*$/.test(lines[i])) {
      para.push(lines[i]); i++;
    }
    out.push(`<p>${mdInline(para.join(" "))}</p>`);
  }
  return out.join("\n");
}

const readerCache = {};
async function openReader(module, moduleName) {
  let panel = el("#reader");
  if (!panel) { panel = document.createElement("aside"); panel.id = "reader"; document.body.appendChild(panel); }
  panel.innerHTML = `<div class="reader-head"><span class="reader-title">${esc(moduleName)}</span>
      <button class="reader-close" id="readerClose" title="Close (Esc)">&times;</button></div>
    <div class="reader-body md-body" id="readerBody"><div class="loading">Loading&hellip;</div></div>`;
  document.body.classList.add("reader-open");
  el("#readerClose").addEventListener("click", closeReader);
  try {
    if (!readerCache[module]) {
      const r = await fetch(`/content/${module}/README.md`, { cache: "no-store" });
      if (!r.ok) throw 0;
      readerCache[module] = await r.text();
    }
    const b = el("#readerBody");
    b.innerHTML = mdRender(readerCache[module]);
    b.scrollTop = 0;
  } catch {
    el("#readerBody").innerHTML = `<div class="error">Couldn't load this module &mdash; is <code>server.py</code> running?</div>`;
  }
}
function closeReader() {
  document.body.classList.remove("reader-open");
  const p = el("#reader"); if (p) p.remove();
}

/* ---------- keyboard ---------- */
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape" && document.body.classList.contains("reader-open")) { e.preventDefault(); closeReader(); return; }
  if (!state.inQuiz) return;
  if ((e.target.tagName || "").toLowerCase() === "input") return;
  if (state.mode === "flash") {
    if (!state.answered) {
      if (e.key === " " || e.key === "Enter") { e.preventDefault(); revealCard(); }
    } else if (e.key === "1") { e.preventDefault(); gradeCard(false); }
    else if (e.key === "2" || e.key === "Enter") { e.preventDefault(); gradeCard(true); }
    return;
  }
  if (state.answered) {
    if (e.key === "Enter") { e.preventDefault(); nextQuestion(); }
    return;
  }
  if (/^[1-4]$/.test(e.key)) {
    const i = +e.key - 1;
    if (i < state.curOptsLen) { e.preventDefault(); answer(i); }
  } else if (e.key.toLowerCase() === "s") {
    const item = state.deck[state.queue[state.cursor]];
    if (item && item.status === "pending") { e.preventDefault(); skipQuestion(); }
  }
});

/* ---------- boot ---------- */
function syncMuteBtn() {
  const b = el("#muteBtn");
  if (b) b.textContent = sfx.isOn() ? "🔊" : "🔇";
}

function syncModeBtn() {
  const b = el("#modeBtn");
  if (!b) return;
  const flash = deckMode() === "flash";
  b.textContent = flash ? "Cards" : "Quiz";
  b.title = flash ? "Flashcards mode (click for multiple-choice)" : "Multiple-choice mode (click for flashcards)";
  b.classList.toggle("on", flash);
}

async function boot() {
  state.index = await apiGet("questions/index.json", null);
  if (!state.index) {
    app.innerHTML = `<div class="error">No question bank found. Run <code>python3 extract.py</code> then reload.</div>`;
    return;
  }
  el("#bankInfo").textContent = `${state.index.total} questions across ${Object.keys(state.index.sections).length} sections`;
  state.progress = await loadProgress();
  state.today = await apiGet("/api/today", {});
  el("#navProgress").addEventListener("click", renderProgress);
  const mb = el("#muteBtn");
  if (mb) mb.addEventListener("click", () => { sfx.toggle(); syncMuteBtn(); });
  syncMuteBtn();
  const modeB = el("#modeBtn");
  if (modeB) {
    modeB.addEventListener("click", () => {
      localStorage.setItem("sd_mode", deckMode() === "flash" ? "quiz" : "flash");
      syncModeBtn();
      if (!state.inQuiz) renderHome();        // refresh the CTA caption
    });
  }
  syncModeBtn();
  renderHome();
}

boot();
