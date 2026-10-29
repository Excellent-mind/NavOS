#!/usr/bin/env python3
"""
Builds app/src/main/assets/cameras.json: Malaysia's speed and red-light
cameras (AES / AwAS) from OpenStreetMap, for drive warnings that work offline.

  highway=speed_camera nodes, plus enforcement=maxspeed relations (their
  "device" member is the camera; the relation may carry the limit).

Output: {"fetched": "YYYY-MM-DD", "cameras": [[lat, lon, limit|0, direction|-1, kind, name], ...]}
  kind: "speed" | "redlight"; direction: compass degrees the camera faces traffic from, -1 unknown.

Usage: build.py [OUT]   (Overpass mirrors are tried in turn.)
"""
import datetime
import json
import sys
import urllib.parse
import urllib.request

MIRRORS = [
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass-api.de/api/interpreter",
    "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
]
QUERY = """[out:json][timeout:120];
area["ISO3166-1"="MY"]->.my;
(node["highway"="speed_camera"](area.my);
 node["enforcement"~"maxspeed|traffic_signals"](area.my);
 relation["enforcement"~"maxspeed|traffic_signals"](area.my););
out body;"""


def fetch():
    body = urllib.parse.urlencode({"data": QUERY}).encode()
    last = None
    for url in MIRRORS:
        try:
            req = urllib.request.Request(url, data=body, headers={"User-Agent": "NavOS/0.9 (camera data build)"})
            return json.load(urllib.request.urlopen(req, timeout=150))
        except Exception as e:  # try the next mirror
            last = e
    raise SystemExit(f"all Overpass mirrors failed: {last}")


def limit_of(v):
    try:
        return int(str(v).split()[0].split(";")[0])
    except Exception:
        return 0


def direction_of(v):
    try:
        return int(float(str(v).split(";")[0])) % 360
    except Exception:
        return -1


def main(out):
    els = fetch()["elements"]
    nodes = {e["id"]: e for e in els if e["type"] == "node"}
    # Limit / kind from enforcement relations onto their device nodes.
    extra = {}
    for r in (e for e in els if e["type"] == "relation"):
        t = r.get("tags", {})
        for m in r.get("members", []):
            if m.get("type") == "node" and m.get("role") == "device":
                extra[m["ref"]] = t
    cams = []
    for nid, n in nodes.items():
        t = dict(extra.get(nid, {}))
        t.update(n.get("tags", {}))
        enf = t.get("enforcement", "")
        if t.get("highway") != "speed_camera" and "maxspeed" not in enf and "traffic_signals" not in enf:
            continue
        kind = "redlight" if ("traffic_signals" in enf or t.get("enforcement") == "red_light_camera") and "maxspeed" not in enf else "speed"
        cams.append([round(n["lat"], 6), round(n["lon"], 6), limit_of(t.get("maxspeed", 0)), direction_of(t.get("direction", -1)), kind,
                     t.get("name", "")[:60]])
    cams.sort()
    doc = {"fetched": datetime.date.today().isoformat(), "source": "OpenStreetMap contributors (ODbL)", "cameras": cams}
    with open(out, "w") as fh:
        json.dump(doc, fh, ensure_ascii=False, separators=(",", ":"))
    print(len(cams), "cameras,", sum(1 for c in cams if c[2]), "with a limit,", sum(1 for c in cams if c[3] >= 0), "with a direction")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/cameras.json")
