#!/usr/bin/env python3
"""
Builds app/src/main/assets/smartlanes.json: PLUS Smart Lanes that open on a
daily schedule (the emergency lane opened as an extra lane at peak hours).

Each stretch's line is the expressway carriageway between two interchanges,
from the OSRM route between their toll plazas (only the mainline step, ref
E..), driving direction = the way the Smart Lane runs. Festive / one-off
activations are not here: they live in Supabase (smart_lane_windows) and the
app downloads them.

Sources: PLUS announcements (paultan.org 2022-01-14: NKVE Setia Alam to
Shah Alam, KM3.2-KM9.2, 6.30-9.30 am weekdays, 60 km/h; malaymail 2023:
NKVE Setia Alam-Shah Alam and ELITE USJ-Seafield run on working-day
mornings - ELITE hours not published, so marked unconfirmed).

Usage: build.py OUT.json
"""
import json
import subprocess
import sys

LANES = [
    {
        "id": "nkve-setia-alam-shah-alam",
        "name": "NKVE Setia Alam to Shah Alam",
        "name_ms": "NKVE Setia Alam ke Shah Alam",
        "highway": "E1",
        "from": (101.4865, 3.0918),
        "to": (101.5452, 3.0978),
        "speed_kmh": 60,
        "schedule": [{"days": [1, 2, 3, 4, 5], "from": "06:30", "to": "09:30"}],
        "confirmed": True,
    },
    {
        "id": "elite-usj-seafield",
        "name": "ELITE USJ to Seafield",
        "name_ms": "ELITE USJ ke Seafield",
        "highway": "E6",
        "from": (101.5791, 3.0255),
        "to": (101.5683, 3.0535),
        "speed_kmh": 60,
        "schedule": [{"days": [1, 2, 3, 4, 5], "from": "06:30", "to": "09:30"}],
        "confirmed": False,
    },
]


def mainline(a, b, ref):
    url = (f"https://router.project-osrm.org/route/v1/driving/{a[0]},{a[1]};{b[0]},{b[1]}"
           "?overview=false&geometries=geojson&steps=true")
    d = json.loads(subprocess.check_output(["curl", "-sS", "-m", "30", url]))
    for s in d["routes"][0]["legs"][0]["steps"]:
        if s.get("ref") and ref in s["ref"].split(";"):
            return [[round(x, 6), round(y, 6)] for x, y in s["geometry"]["coordinates"]]
    raise SystemExit(f"no {ref} step between {a} and {b}")


def main(out):
    lanes = []
    for l in LANES:
        line = mainline(l["from"], l["to"], l["highway"])
        lanes.append({k: v for k, v in l.items() if k not in ("from", "to")} | {"line": line})
        print(l["id"], len(line), "points")
    with open(out, "w") as fh:
        json.dump({"version": 1, "lanes": lanes}, fh, separators=(",", ":"))


if __name__ == "__main__":
    main(sys.argv[1])
