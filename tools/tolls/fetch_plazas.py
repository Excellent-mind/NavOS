"""Downloads every toll plaza / booth in Malaysia from OpenStreetMap (Overpass)
for the bundled table app/src/main/assets/tolls/plazas.json:
  [[lat, lon, "name", "highway ref or ''"], ...]
Tries a few public Overpass servers. Data (c) OpenStreetMap contributors, ODbL.

    python3 tools/tolls/fetch_plazas.py out.json
"""
import json, sys, urllib.request, urllib.parse

Q = """[out:json][timeout:180];
area["ISO3166-1"="MY"][admin_level=2]->.my;
(node["barrier"="toll_booth"](area.my);way["barrier"="toll_booth"](area.my);
 node["highway"="toll_gantry"](area.my););
out center tags;"""
SERVERS = [
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
]
for url in SERVERS:
    try:
        req = urllib.request.Request(url, data=urllib.parse.urlencode({"data": Q}).encode(),
                                     headers={"User-Agent": "NavOS toll table (github.com/Excellent-mind/NavOS)"})
        elements = json.loads(urllib.request.urlopen(req, timeout=240).read())["elements"]
        break
    except Exception as e:
        print("failed", url, e, file=sys.stderr)
else:
    sys.exit(1)

out = []
for e in elements:
    t = e.get("tags", {})
    lat = e.get("lat", e.get("center", {}).get("lat"))
    lon = e.get("lon", e.get("center", {}).get("lon"))
    name = t.get("name") or t.get("name:ms") or t.get("name:en") or ""
    if lat is None or not name:
        continue
    out.append([round(lat, 6), round(lon, 6), name, t.get("ref", "")])
out.sort()
json.dump(out, open(sys.argv[1] if len(sys.argv) > 1 else "plazas.json", "w"), ensure_ascii=False, separators=(",", ":"))
print(len(elements), "elements,", len(out), "named plazas")
