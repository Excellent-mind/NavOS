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

## Setup

- Secret: `IP_SALT` (random, 32+ bytes) - `wrangler secret put IP_SALT`.
- Schema: `schema.sql`. Databases made before senders existed also need
  `ALTER TABLE probes ADD COLUMN sender TEXT;` (done on the live database).
