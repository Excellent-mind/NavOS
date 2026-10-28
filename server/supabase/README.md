# NavOS on Supabase

Project `NavOS` (ref `mksfocirdngualkahuqi`, region ap-southeast-1).

- Toll tables (public read): `toll_highways`, `toll_plaza_codes`, `toll_rates`,
  `toll_flat_rates`, `toll_plaza_points`, `data_sources`.
- `rpc/toll_fares_doc` returns the whole table in the same JSON shape as
  `app/src/main/assets/tolls/llm_rates.json`; the app (`routing/TollFares.kt`)
  downloads it about once a week and keeps the built-in copy as fallback.
- `project_notes` is private (no RLS policies): project memory for future sessions.

To publish new LLM fares: update `toll_rates` / `toll_flat_rates`, then set
`data_sources.fetched` for `toll_rates` to the new date. Phones only accept a
table whose date is not older than the one they have.

Schema: `schema.sql`.
