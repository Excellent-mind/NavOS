# LLM toll fares

`scrape2.py` fetches car (class 1) fares from Lembaga Lebuhraya Malaysia's
Kalkulator Tol API (`https://www.llm.gov.my/Calculator/...`), 4 requests at a
time. It resumes from `rates.jsonl` in the working directory and needs
`enters.json` / `exits.json` (copies in `data/tolls/`).

    cd somewhere && cp tools/tolls/*.py data/tolls/enters.json data/tolls/exits.json .
    python3 scrape2.py        # appends to rates.jsonl

Then build the app's table (all 31 highways, car class, in sen):

    python3 tools/tolls/build_rates.py rates.jsonl   # -> app/src/main/assets/tolls/llm_rates.json

Fetched 2026-10-23: 5,321 of 5,324 pairs (3 have no car fare in LLM's data).
`data/tolls/llm_car_fares.json` is the older PLUS-only partial fetch.
