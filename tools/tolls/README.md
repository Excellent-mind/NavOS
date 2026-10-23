# LLM toll fares

`scrape2.py` fetches car (class 1) fares from Lembaga Lebuhraya Malaysia's
Kalkulator Tol API (`https://www.llm.gov.my/Calculator/...`), 4 requests at a
time. It resumes from `rates.jsonl` in the working directory and needs
`enters.json` / `exits.json` (copies in `data/tolls/`).

    cd somewhere && cp tools/tolls/*.py data/tolls/enters.json data/tolls/exits.json .
    python3 scrape2.py        # appends to rates.jsonl

Fetched so far: `data/tolls/llm_car_fares.json` (PLUS only, 3002 pairs).
