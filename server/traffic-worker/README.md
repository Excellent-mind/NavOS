# navos-traffic

Live traffic for NavOS: a Cloudflare Worker (free plan) with a D1 database.

- Deployed as `navos-traffic` on the Synnamer Cloudflare account:
  https://navos-traffic.synnamer.workers.dev
- D1 database `navos-traffic`, binding `DB` (schema in `schema.sql`).
- Cron `17 * * * *` deletes rows older than a day.

Phones send anonymous speeds per OSM road piece ("nodeA-nodeB") during a
drive (`/v1/probe`) and ask for the current speed of the pieces on their
route (`/v1/flow`). A piece reports only once 3+ different drives measured
it in the last 10 minutes. See `worker.js` for the contract.
