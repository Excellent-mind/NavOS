# navos-traffic

Live traffic for NavOS: a Cloudflare Worker (free plan) with a D1 database.

- Deployed as `navos-traffic` on the Synnamer Cloudflare account:
  https://navos-traffic.synnamer.workers.dev
- D1 database `navos-traffic`, binding `DB` (schema in `schema.sql`).
- Cron `17 * * * *` deletes rows older than a day.

Phones send anonymous speeds per OSM road piece ("nodeA-nodeB") during a
drive (`/v1/probe`) and ask for the current speed of the pieces on their
route (`/v1/flow`). See `worker.js` for the contract.

## Against fake jams

The code is public, so anyone can call the server. To keep one person from
painting a road red:

- A piece is reported only once **3 different senders** measured it in the
  last 10 minutes. A sender is a one-way code of the caller's IP address,
  keyed with the secret `IP_SALT` and the day: the address is never stored
  and codes from different days can't be linked. Making up drive ids adds
  no senders.
- Each sender may start at most 20 drives and send 3,000 road pieces per
  10 minutes (generous, since phones on one mobile network often share an
  address); more gets HTTP 429.
- The **median** speed is reported (one value per sender), and speeds over
  160 km/h are dropped.

Limit: someone with many IP addresses (a proxy pool) can still act as many
senders. Signed-in contributors (live traffic step 2) close that gap.

## Typical traffic ("Leave at ... to save ...")

Every hour the cron folds the past hour's probes into `typical`: one row per
road piece and time-of-week bucket (weekend*24 + hour, Malaysia time), a
running average capped at 100 samples so older weeks fade. `POST /v1/typical`
returns the usual speed for up to 4 buckets; the app compares leaving now with
leaving in 1-3 hours. It needs weeks of drives before it has much to say.

## Setup

- Secret: `IP_SALT` (random, 32+ bytes) - `wrangler secret put IP_SALT`.
- Schema: `schema.sql`. Databases made before senders existed also need
  `ALTER TABLE probes ADD COLUMN sender TEXT;` (done on the live database).

## Road reports (accident, police, flood, hazard)

Drivers report from the drive screen (`/v1/report`); phones fetch the live
reports in a box around the route (`/v1/reports`) and ask a driver who just
passed one "Still there?" (`/v1/vote`). No account, the same day-keyed
sender codes as above.

- A report lives 1 h (police), 1.5 h (accident), 2 h (hazard) or 6 h
  (flood); each "still there" from someone else renews that.
- The same kind reported within 250 m, same direction, by another sender
  counts as a "still there" instead of a second report.
- Two more "not there" than "still there" removes it.
- One vote per sender per report; the reporter can't vote on their own.
  At most 6 reports and 40 votes per sender per hour.
- Only places inside Malaysia are accepted. Old reports are deleted by the
  hourly cron a day after they end.

## Area traffic (v0.9.0)
`/v1/probe` may carry `"g"`: where each piece is (two OpenStreetMap points and
its normal speed). It is stored per piece in `segpos`, with no drive or sender,
and `/v1/area {"box": [s, w, n, e]}` returns the slow and jammed pieces in a box
(at most 0.35 degrees a side) for the home map. Same 3-sender, 10-minute rules
as `/v1/flow`.
