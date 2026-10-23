// NavOS live traffic - Cloudflare Worker (free plan) + D1.
//
// POST /v1/probe  {"s": "<random per-drive id>", "p": [["<osmNodeA>-<osmNodeB>", kmh], ...]}
//   Anonymous speeds a phone measured on road pieces it just drove. No account,
//   no location trace: only road-piece ids and speeds, timestamped by the server.
// POST /v1/flow   {"segs": ["<osmNodeA>-<osmNodeB>", ...]}
//   -> {"f": {"<seg>": [avgKmh, drivers], ...}} for pieces with at least
//   MIN_DRIVERS different drivers in the last WINDOW_S seconds.
// GET  /v1/health
//
// A drive counts once per road piece (primary key seg+sess), so one phone
// can't paint a road red on its own. Rows older than a day are deleted hourly.

const MIN_DRIVERS = 3;
const WINDOW_S = 600;
const KEEP_S = 86400;
const MAX_BODY = 32768;
const MAX_PROBES = 150;
const MAX_FLOW_SEGS = 1500;
const SEG = /^\d{1,12}-\d{1,12}$/;
const SESS = /^[A-Za-z0-9]{16,64}$/;

const now = () => Math.floor(Date.now() / 1000);
const json = (body, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json", "cache-control": "no-store" } });

async function readBody(req) {
  const len = Number(req.headers.get("content-length") || "0");
  if (len > MAX_BODY) throw new Error("too big");
  const text = await req.text();
  if (text.length > MAX_BODY) throw new Error("too big");
  return JSON.parse(text);
}

async function probe(req, env) {
  const b = await readBody(req);
  if (typeof b.s !== "string" || !SESS.test(b.s) || !Array.isArray(b.p) || b.p.length > MAX_PROBES) return json({ error: "bad request" }, 400);
  const t = now();
  const stmt = env.DB.prepare("INSERT OR REPLACE INTO probes (seg, sess, kmh, t) VALUES (?, ?, ?, ?)");
  const rows = [];
  for (const item of b.p) {
    if (!Array.isArray(item) || item.length !== 2) continue;
    const [seg, kmh] = item;
    if (typeof seg !== "string" || !SEG.test(seg) || typeof kmh !== "number" || !(kmh >= 0 && kmh <= 200)) continue;
    rows.push(stmt.bind(seg, b.s, Math.round(kmh * 10) / 10, t));
  }
  if (rows.length) await env.DB.batch(rows);
  return json({ ok: rows.length });
}

async function flow(req, env) {
  const b = await readBody(req);
  if (!Array.isArray(b.segs)) return json({ error: "bad request" }, 400);
  const segs = [...new Set(b.segs.filter((s) => typeof s === "string" && SEG.test(s)))].slice(0, MAX_FLOW_SEGS);
  const since = now() - WINDOW_S;
  const f = {};
  for (let i = 0; i < segs.length; i += 90) {
    const chunk = segs.slice(i, i + 90);
    const marks = chunk.map(() => "?").join(",");
    const res = await env.DB.prepare(
      `SELECT seg, AVG(kmh) AS kmh, COUNT(*) AS n FROM probes WHERE seg IN (${marks}) AND t > ? GROUP BY seg HAVING n >= ${MIN_DRIVERS}`,
    ).bind(...chunk, since).all();
    for (const r of res.results) f[r.seg] = [Math.round(r.kmh * 10) / 10, r.n];
  }
  return json({ f });
}

export default {
  async fetch(req, env) {
    const path = new URL(req.url).pathname;
    try {
      if (req.method === "POST" && path === "/v1/probe") return await probe(req, env);
      if (req.method === "POST" && path === "/v1/flow") return await flow(req, env);
      if (req.method === "GET" && path === "/v1/health") return json({ ok: true });
    } catch (e) {
      return json({ error: "bad request" }, 400);
    }
    return json({ error: "not found" }, 404);
  },
  async scheduled(event, env) {
    await env.DB.prepare("DELETE FROM probes WHERE t < ?").bind(now() - KEEP_S).run();
  },
};
