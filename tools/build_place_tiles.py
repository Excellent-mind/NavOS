#!/usr/bin/env python3
"""Builds NavOS's Overture place tiles for Malaysia.

Output: <out>/v1/{cell}.json.gz (0.1 degree cells, "floor(lat*10)_floor(lon*10)")
and <out>/v1/index.json. Each tile: {"v":1,"cell":..,"places":[[name,category,lat,lon,address],...]}.

Data: Overture Maps "places" (mostly Meta, CDLA-Permissive-2.0; also Foursquare
Apache-2.0, Microsoft, AllThePlaces CC0). Keep the attribution in the app.

    pip install duckdb
    env -u AWS_ACCESS_KEY_ID -u AWS_SECRET_ACCESS_KEY python3 tools/build_place_tiles.py 2026-10-21.0 out/
"""
import collections, gzip, json, os, sys

import duckdb

release, out = sys.argv[1], sys.argv[2]
os.makedirs(f"{out}/v1", exist_ok=True)
con = duckdb.connect()
con.sql("LOAD httpfs; LOAD spatial;")
con.sql("CREATE SECRET anon (TYPE s3, PROVIDER config, KEY_ID '', SECRET '', REGION 'us-west-2');")
rows = con.sql(f"""
SELECT names."primary", basic_category, ST_Y(geometry), ST_X(geometry),
       coalesce(addresses[1].freeform, ''), coalesce(addresses[1].locality, '')
FROM read_parquet('s3://overturemaps-us-west-2/release/{release}/theme=places/type=place/*', hive_partitioning=1)
WHERE bbox.xmin BETWEEN 99.6 AND 119.4 AND bbox.ymin BETWEEN 0.85 AND 7.45
  AND (addresses[1].country = 'MY' OR addresses[1].country IS NULL)
  AND confidence >= 0.7 AND names."primary" IS NOT NULL AND length(trim(names."primary")) >= 2
""").fetchall()
cells = collections.defaultdict(list)
for name, cat, lat, lon, addr, city in rows:
    key = f"{int(lat * 10 // 1)}_{int(lon * 10 // 1)}"
    cells[key].append([name.strip(), cat or "", round(lat, 6), round(lon, 6), ", ".join(x for x in (addr, city) if x)])
for key, places in cells.items():
    places.sort(key=lambda p: p[0].lower())
    body = json.dumps({"v": 1, "cell": key, "places": places}, ensure_ascii=False, separators=(",", ":")).encode()
    with open(f"{out}/v1/{key}.json.gz", "wb") as f:
        f.write(gzip.compress(body, 9))
with open(f"{out}/v1/index.json", "w") as f:
    json.dump({"v": 1, "release": release, "cellDeg": 0.1, "count": len(rows), "cells": sorted(cells)}, f)
print(len(rows), "places in", len(cells), "tiles")
