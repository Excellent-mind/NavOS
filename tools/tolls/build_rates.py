"""Builds the app's toll table, app/src/main/assets/tolls/llm_rates.json,
from what scrape2.py fetched (rates.jsonl) and the plaza lists
(data/tolls/enters.json, exits.json). See TollPricer.fromJson for the format:

  {"source": ..., "fetched": ..., "highways": [
     {"id": "1", "name": ..., "plazas": [[id, name], ...],
      "open": {"<plaza>": [sen]},           # open system: flat fare per plaza
      "pairs": {"<entry>><exit>": [sen]}}]} # closed system: entry -> exit

Fares are car class (Kelas 1; Jambatan Pulau Pinang's class 2) in sen.

    python3 tools/tolls/build_rates.py path/to/rates.jsonl
"""
import json, sys, os, datetime

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
rates_path = sys.argv[1] if len(sys.argv) > 1 else "rates.jsonl"
D = json.load(open(os.path.join(ROOT, "data/tolls/enters.json")))
EX = json.load(open(os.path.join(ROOT, "data/tolls/exits.json")))

names = {}
for h in D["highways"]:
    hw = str(h["id"])
    for e in D["enters"].get(hw, []):
        names.setdefault(hw, {})[str(e["id"])] = e["name"]
for key, exits in EX.items():
    hw = key.split("/")[0]
    for x in exits:
        names.setdefault(hw, {})[str(x["id"])] = x["name"]

open_fares, pairs = {}, {}
for line in open(rates_path):
    r = json.loads(line)
    if r.get("car") is None:
        continue
    sen = int(round(r["car"] * 100))
    hw, en, ex = str(r["hw"]), str(r["enter"]), r["exit"]
    if ex in (None, "null"):
        open_fares.setdefault(hw, {})[en] = [sen]
    else:
        pairs.setdefault(hw, {})[f"{en}>{ex}"] = [sen]

out = {
    "source": "Lembaga Lebuhraya Malaysia, Kalkulator Tol (www.llm.gov.my/Calculator)",
    "fetched": datetime.date.today().isoformat(),
    "vehicleClass": "Kelas 1 (car); Jambatan Pulau Pinang class 2 = car",
    "highways": [],
}
n_open = n_pairs = 0
for h in D["highways"]:
    hw = str(h["id"])
    entry = {"id": hw, "name": h["name"], "plazas": [[k, v] for k, v in sorted(names.get(hw, {}).items())]}
    if hw in open_fares:
        entry["open"] = open_fares[hw]
        n_open += len(open_fares[hw])
    if hw in pairs:
        entry["pairs"] = pairs[hw]
        n_pairs += len(pairs[hw])
    if "open" in entry or "pairs" in entry:
        out["highways"].append(entry)

dest = os.path.join(ROOT, "app/src/main/assets/tolls/llm_rates.json")
os.makedirs(os.path.dirname(dest), exist_ok=True)
json.dump(out, open(dest, "w"), ensure_ascii=False, separators=(",", ":"))
print(f"{len(out['highways'])} highways, {n_open} open plazas, {n_pairs} entry/exit pairs -> {dest} ({os.path.getsize(dest)//1024} KB)")
