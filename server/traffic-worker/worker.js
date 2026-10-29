// NavOS live traffic - Cloudflare Worker (free plan) + D1.
//
// POST /v1/probe  {"s": "<random per-drive id>", "p": [["<osmNodeA>-<osmNodeB>", kmh], ...]}
//   Anonymous speeds a phone measured on road pieces it just drove. No account,
//   no location trace: only road-piece ids and speeds, timestamped by the server.
//   Optional "g": [["<seg>", latA, lonA, latB, lonB, freeFlowKmh], ...] says where
//   those pieces are on the map (public OpenStreetMap geometry, stored per piece
//   with no drive or sender attached), so /v1/area can colour them.
// POST /v1/area   {"box": [south, west, north, east]}  (at most MAX_AREA_DEG a side)
//   -> {"a": [[latA, lonA, latB, lonB, level], ...]} slow (1) and jammed (2)
//   pieces in the box, same rules as /v1/flow (MIN_SENDERS, last WINDOW_S).
// POST /v1/flow   {"segs": ["<osmNodeA>-<osmNodeB>", ...]}
//   -> {"f": {"<seg>": [medianKmh, senders], ...}} for pieces measured by at
//   least MIN_SENDERS different senders in the last WINDOW_S seconds.
// POST /v1/typical {"segs": [...], "buckets": [b, ...]}
//   -> {"t": {"<bucket>": {"<seg>": [kmh, samples]}}} the usual speed of each
//   piece at that time of the week, learned from past probes. A bucket is
//   weekend*24 + hour, Malaysia time (hour 0-23; weekend = Sat/Sun).
// POST /v1/report {"k": "accident|police|flood|hazard|smartlane|jam|camera|closed", "lat": .., "lon": .., "h": heading}
//   -> {"id": n} a road report from a driver; the same kind reported again
//   within REPORT_MERGE_M by someone else confirms the first one instead.
// POST /v1/reports {"box": [south, west, north, east]}
//   -> {"r": [[id, kind, lat, lon, heading, ageS, up, down], ...]} live reports.
// POST /v1/vote   {"id": n, "v": 1 | -1}  "still there" / "not there".
//   A "still there" keeps it alive for another of its life spans; two more
//   "not there" than "still there" removes it. One vote per sender per report.
// GET  /v1/health
//
// Against fake jams (the code is public, so anyone can call this):
// - A road piece needs MIN_SENDERS different *senders*, not drives. A sender is
//   a one-way code of the caller's IP address, keyed with a secret (IP_SALT)
//   that changes every day: the address itself is never stored, and codes from
//   different days can't be linked. Making up drive ids doesn't add senders.
// - Each sender may start at most MAX_DRIVES drives and send at most
//   MAX_PIECES road pieces per WINDOW_S (HTTP 429 beyond that).
// - The median speed is reported, so one odd value can't swing it; speeds
//   over MAX_KMH are dropped.
// Rows older than a day are deleted hourly.

const MIN_SENDERS = 3;
const WINDOW_S = 600;
const KEEP_S = 86400;
const MAX_BODY = 32768;
const MAX_PROBES = 150;
const MAX_FLOW_SEGS = 1500;
const MAX_KMH = 160;
const MAX_DRIVES = 20; // generous: phones on one mobile network often share an IP address
const MAX_PIECES = 3000;
const MAX_BUCKETS = 4;
const TYPICAL_MIN_SAMPLES = 3;
const TYPICAL_MAX_WEIGHT = 100; // older weeks fade as new ones come in
// Road reports: how long each kind lives without a "still there".
// smartlane: a driver saw the PLUS Smart Lane (emergency lane) open.
// jam: stop-and-go traffic; camera: a mobile speed trap; closed: road closed.
const REPORT_TTL = { accident: 5400, police: 3600, flood: 21600, hazard: 7200, smartlane: 10800, jam: 2700, camera: 3600, closed: 21600 };
const REPORT_MERGE_M = 250;
const MAX_REPORTS_PER_HOUR = 6;
const MAX_VOTES_PER_HOUR = 40;
const MAX_BOX_DEG = 2.5;
const MAX_REPORTS_OUT = 300;
const SEG = /^\d{1,12}-\d{1,12}$/;
const MAX_AREA_DEG = 0.35;
const MAX_AREA_ROWS = 20000;
const SEGPOS_KEEP_S = 30 * 86400;
const SLOW_RATIO = 0.8;
const JAM_RATIO = 0.5;
const MIN_FREE_FLOW_KMH = 14.4;
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

/** One-way, day-keyed code of the caller's IP address (16 hex chars). */
async function senderOf(req, env) {
  const ip = req.headers.get("cf-connecting-ip") || "unknown";
  const day = Math.floor(now() / 86400);
  const key = await crypto.subtle.importKey(
    "raw", new TextEncoder().encode(`${env.IP_SALT}:${day}`), { name: "HMAC", hash: "SHA-256" }, false, ["sign"],
  );
  const mac = new Uint8Array(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(ip)));
  return [...mac.slice(0, 8)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

async function probe(req, env) {
  const b = await readBody(req);
  if (typeof b.s !== "string" || !SESS.test(b.s) || !Array.isArray(b.p) || b.p.length > MAX_PROBES) return json({ error: "bad request" }, 400);
  const t = now();
  const sender = await senderOf(req, env);

  // Quota for this sender in the current window.
  const w = Math.floor(t / WINDOW_S);
  const q = await env.DB.prepare("SELECT pieces, drives FROM quota WHERE sender = ? AND w = ?").bind(sender, w).first();
  const seen = await env.DB.prepare("SELECT 1 FROM probes WHERE sess = ? AND sender = ? LIMIT 1").bind(b.s, sender).first();
  const newDrive = !seen;
  if (q && ((newDrive && q.drives >= MAX_DRIVES) || q.pieces >= MAX_PIECES)) return json({ error: "slow down" }, 429);

  const stmt = env.DB.prepare("INSERT OR REPLACE INTO probes (seg, sess, sender, kmh, t) VALUES (?, ?, ?, ?, ?)");
  const rows = [];
  for (const item of b.p) {
    if (!Array.isArray(item) || item.length !== 2) continue;
    const [seg, kmh] = item;
    if (typeof seg !== "string" || !SEG.test(seg) || typeof kmh !== "number" || !(kmh >= 0 && kmh <= MAX_KMH)) continue;
    rows.push(stmt.bind(seg, b.s, sender, Math.round(kmh * 10) / 10, t));
  }
  const pieces = rows.length;
  // Where the pieces are (no drive, no sender: just the road).
  if (Array.isArray(b.g)) {
    const pos = env.DB.prepare(
      "INSERT INTO segpos (seg, alat, alon, blat, blon, ff, t) VALUES (?, ?, ?, ?, ?, ?, ?) " +
        "ON CONFLICT (seg) DO UPDATE SET ff = excluded.ff, t = excluded.t",
    );
    for (const g of b.g.slice(0, MAX_PROBES)) {
      if (!Array.isArray(g) || g.length !== 6) continue;
      const [seg, alat, alon, blat, blon, ff] = g;
      if (typeof seg !== "string" || !SEG.test(seg) || !inMalaysia(alat, alon) || !inMalaysia(blat, blon)) continue;
      if (typeof ff !== "number" || !(ff > 0 && ff <= MAX_KMH)) continue;
      if (metersBetween(alat, alon, blat, blon) > 2000) continue;
      const r5 = (v) => Math.round(v * 1e5) / 1e5;
      rows.push(pos.bind(seg, r5(alat), r5(alon), r5(blat), r5(blon), Math.round(ff * 10) / 10, t));
    }
  }
  rows.push(
    env.DB.prepare(
      "INSERT INTO quota (sender, w, pieces, drives) VALUES (?, ?, ?, ?) " +
        "ON CONFLICT (sender, w) DO UPDATE SET pieces = pieces + excluded.pieces, drives = drives + excluded.drives",
    ).bind(sender, w, pieces, newDrive ? 1 : 0),
  );
  await env.DB.batch(rows);
  return json({ ok: pieces });
}

function median(xs) {
  const s = [...xs].sort((a, b) => a - b);
  const m = s.length >> 1;
  return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2;
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
    // One speed per sender per piece (their latest), then the median of those.
    const res = await env.DB.prepare(
      `SELECT seg, sender, kmh FROM probes WHERE seg IN (${marks}) AND t > ? ORDER BY t`,
    ).bind(...chunk, since).all();
    const bySeg = new Map();
    for (const r of res.results) {
      if (!bySeg.has(r.seg)) bySeg.set(r.seg, new Map());
      bySeg.get(r.seg).set(r.sender, r.kmh);
    }
    for (const [seg, senders] of bySeg) {
      if (senders.size >= MIN_SENDERS) f[seg] = [Math.round(median(senders.values()) * 10) / 10, senders.size];
    }
  }
  return json({ f });
}

async function typical(req, env) {
  const b = await readBody(req);
  if (!Array.isArray(b.segs) || !Array.isArray(b.buckets)) return json({ error: "bad request" }, 400);
  const segs = [...new Set(b.segs.filter((s) => typeof s === "string" && SEG.test(s)))].slice(0, MAX_FLOW_SEGS);
  const buckets = [...new Set(b.buckets.filter((x) => Number.isInteger(x) && x >= 0 && x < 48))].slice(0, MAX_BUCKETS);
  const t = {};
  for (const bucket of buckets) {
    const f = {};
    for (let i = 0; i < segs.length; i += 90) {
      const chunk = segs.slice(i, i + 90);
      const marks = chunk.map(() => "?").join(",");
      const res = await env.DB.prepare(
        `SELECT seg, kmh, n FROM typical WHERE bucket = ? AND n >= ${TYPICAL_MIN_SAMPLES} AND seg IN (${marks})`,
      ).bind(bucket, ...chunk).all();
      for (const r of res.results) f[r.seg] = [Math.round(r.kmh * 10) / 10, r.n];
    }
    t[bucket] = f;
  }
  return json({ t });
}

// Folds the probes since the last run into the typical speeds (one row per
// piece and time-of-week bucket, a running average that slowly forgets).
async function learnTypical(env) {
  const last = await env.DB.prepare("SELECT v FROM meta WHERE k = 'typical_upto'").first();
  const from = last ? Number(last.v) : now() - 3600;
  const upto = now() - 60;
  if (upto <= from) return;
  await env.DB.batch([
    env.DB.prepare(
      `INSERT INTO typical (seg, bucket, kmh, n)
       SELECT seg,
              (CASE WHEN strftime('%w', t + 28800, 'unixepoch') IN ('0','6') THEN 24 ELSE 0 END) + CAST(strftime('%H', t + 28800, 'unixepoch') AS INTEGER) AS bucket,
              AVG(kmh), COUNT(*)
       FROM probes WHERE t > ? AND t <= ? GROUP BY seg, bucket
       ON CONFLICT (seg, bucket) DO UPDATE SET
         kmh = (typical.kmh * typical.n + excluded.kmh * excluded.n) / (typical.n + excluded.n),
         n = MIN(typical.n + excluded.n, ${TYPICAL_MAX_WEIGHT})`,
    ).bind(from, upto),
    env.DB.prepare("INSERT INTO meta (k, v) VALUES ('typical_upto', ?) ON CONFLICT (k) DO UPDATE SET v = excluded.v").bind(String(upto)),
  ]);
}

const inMalaysia = (lat, lon) => typeof lat === "number" && typeof lon === "number" && lat > 0.5 && lat < 7.6 && lon > 99 && lon < 120;

function metersBetween(lat1, lon1, lat2, lon2) {
  const k = Math.PI / 180;
  const x = (lon2 - lon1) * k * Math.cos(((lat1 + lat2) / 2) * k);
  const y = (lat2 - lat1) * k;
  return Math.sqrt(x * x + y * y) * 6371000;
}

/** Headings within 60 degrees: the same side of the road. */
const sameWay = (a, b) => a < 0 || b < 0 || Math.abs(((a - b + 540) % 360) - 180) <= 60;

async function report(req, env) {
  const b = await readBody(req);
  const ttl = REPORT_TTL[b.k];
  if (!ttl || !inMalaysia(b.lat, b.lon)) return json({ error: "bad request" }, 400);
  const h = Number.isInteger(b.h) && b.h >= 0 && b.h < 360 ? b.h : -1;
  const t = now();
  const sender = await senderOf(req, env);
  const mine = await env.DB.prepare("SELECT COUNT(*) AS n FROM reports WHERE sender = ? AND t > ?").bind(sender, t - 3600).first();
  if (mine.n >= MAX_REPORTS_PER_HOUR) return json({ error: "slow down" }, 429);

  // Already reported nearby? Then this is a "still there" for that one.
  const d = REPORT_MERGE_M / 111000;
  const near = await env.DB.prepare(
    "SELECT id, lat, lon, h, sender FROM reports WHERE kind = ? AND expires > ? AND lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?",
  ).bind(b.k, t, b.lat - d, b.lat + d, b.lon - d * 1.01, b.lon + d * 1.01).all();
  const same = near.results.find((r) => metersBetween(r.lat, r.lon, b.lat, b.lon) <= REPORT_MERGE_M && sameWay(r.h, h));
  if (same) {
    if (same.sender !== sender) await vote1(env, same.id, sender, 1, t);
    return json({ id: same.id, merged: true });
  }
  const res = await env.DB.prepare(
    "INSERT INTO reports (kind, lat, lon, h, sender, t, expires, up, down) VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0) RETURNING id",
  ).bind(b.k, Math.round(b.lat * 1e5) / 1e5, Math.round(b.lon * 1e5) / 1e5, h, sender, t, t + ttl).first();
  return json({ id: res.id });
}

async function vote1(env, id, sender, v, t) {
  const r = await env.DB.prepare("SELECT kind, sender, expires, up, down FROM reports WHERE id = ?").bind(id).first();
  if (!r || r.expires <= t || r.sender === sender) return false;
  const done = await env.DB.prepare("SELECT 1 FROM votes WHERE id = ? AND sender = ?").bind(id, sender).first();
  if (done) return false;
  const up = r.up + (v > 0 ? 1 : 0);
  const down = r.down + (v < 0 ? 1 : 0);
  let expires = v > 0 ? Math.max(r.expires, t + REPORT_TTL[r.kind]) : r.expires;
  if (down - up >= 2) expires = t; // gone
  await env.DB.batch([
    env.DB.prepare("INSERT INTO votes (id, sender, v, t) VALUES (?, ?, ?, ?)").bind(id, sender, v, t),
    env.DB.prepare("UPDATE reports SET up = ?, down = ?, expires = ? WHERE id = ?").bind(up, down, expires, id),
  ]);
  return true;
}

async function vote(req, env) {
  const b = await readBody(req);
  if (!Number.isInteger(b.id) || (b.v !== 1 && b.v !== -1)) return json({ error: "bad request" }, 400);
  const t = now();
  const sender = await senderOf(req, env);
  const mine = await env.DB.prepare("SELECT COUNT(*) AS n FROM votes WHERE sender = ? AND t > ?").bind(sender, t - 3600).first();
  if (mine.n >= MAX_VOTES_PER_HOUR) return json({ error: "slow down" }, 429);
  return json({ ok: await vote1(env, b.id, sender, b.v, t) });
}

async function area(req, env) {
  const b = await readBody(req);
  const box = b.box;
  if (!Array.isArray(box) || box.length !== 4 || box.some((v) => typeof v !== "number")) return json({ error: "bad request" }, 400);
  const [s, w, n, e] = box;
  if (!(n > s && e > w) || n - s > MAX_AREA_DEG || e - w > MAX_AREA_DEG) return json({ error: "box too big" }, 400);
  const since = now() - WINDOW_S;
  const res = await env.DB.prepare(
    `SELECT p.seg, p.sender, p.kmh, s.alat, s.alon, s.blat, s.blon, s.ff FROM segpos s JOIN probes p ON p.seg = s.seg
     WHERE s.alat BETWEEN ? AND ? AND s.alon BETWEEN ? AND ? AND p.t > ? ORDER BY p.t LIMIT ${MAX_AREA_ROWS}`,
  ).bind(s, n, w, e, since).all();
  const bySeg = new Map();
  for (const r of res.results) {
    if (!bySeg.has(r.seg)) bySeg.set(r.seg, { r, senders: new Map() });
    bySeg.get(r.seg).senders.set(r.sender, r.kmh);
  }
  const a = [];
  for (const { r, senders } of bySeg.values()) {
    if (senders.size < MIN_SENDERS || !(r.ff >= MIN_FREE_FLOW_KMH)) continue;
    const ratio = median(senders.values()) / r.ff;
    const level = ratio < JAM_RATIO ? 2 : ratio < SLOW_RATIO ? 1 : 0;
    if (level) a.push([r.alat, r.alon, r.blat, r.blon, level]);
  }
  return json({ a });
}

async function reports(req, env) {
  const b = await readBody(req);
  const box = b.box;
  if (!Array.isArray(box) || box.length !== 4 || !box.every((x) => typeof x === "number")) return json({ error: "bad request" }, 400);
  const [s, w, n, e] = box;
  if (!(n > s && e > w && n - s <= MAX_BOX_DEG && e - w <= MAX_BOX_DEG)) return json({ error: "bad request" }, 400);
  const t = now();
  const res = await env.DB.prepare(
    `SELECT id, kind, lat, lon, h, t, up, down FROM reports WHERE expires > ? AND lat BETWEEN ? AND ? AND lon BETWEEN ? AND ? ORDER BY t DESC LIMIT ${MAX_REPORTS_OUT}`,
  ).bind(t, s, n, w, e).all();
  return json({ r: res.results.map((x) => [x.id, x.kind, x.lat, x.lon, x.h, t - x.t, x.up, x.down]) });
}

export default {
  async fetch(req, env) {
    const path = new URL(req.url).pathname;
    try {
      if (req.method === "POST" && path === "/v1/probe") return await probe(req, env);
      if (req.method === "POST" && path === "/v1/flow") return await flow(req, env);
      if (req.method === "POST" && path === "/v1/area") return await area(req, env);
      if (req.method === "POST" && path === "/v1/typical") return await typical(req, env);
      if (req.method === "POST" && path === "/v1/report") return await report(req, env);
      if (req.method === "POST" && path === "/v1/reports") return await reports(req, env);
      if (req.method === "POST" && path === "/v1/vote") return await vote(req, env);
      if (req.method === "GET" && path === "/v1/health") return json({ ok: true });
    } catch (e) {
      return json({ error: "bad request" }, 400);
    }
    return json({ error: "not found" }, 404);
  },
  async scheduled(event, env) {
    await learnTypical(env);
    await env.DB.batch([
      env.DB.prepare("DELETE FROM probes WHERE t < ?").bind(now() - KEEP_S),
      env.DB.prepare("DELETE FROM quota WHERE w < ?").bind(Math.floor((now() - KEEP_S) / WINDOW_S)),
      env.DB.prepare("DELETE FROM votes WHERE id IN (SELECT id FROM reports WHERE expires < ?)").bind(now() - KEEP_S),
      env.DB.prepare("DELETE FROM reports WHERE expires < ?").bind(now() - KEEP_S),
      env.DB.prepare("DELETE FROM segpos WHERE t < ?").bind(now() - SEGPOS_KEEP_S),
    ]);
  },
};
