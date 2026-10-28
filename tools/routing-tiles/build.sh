#!/usr/bin/env bash
# Builds Valhalla routing tiles for Malaysia (+ Singapore, Brunei) from
# OpenStreetMap, for routing on the phone with no routing server.
#
#   tools/routing-tiles/build.sh [OUT_DIR]
#
# Output in OUT_DIR/public/ - static files any web host can serve:
#   {level}/{000}/{000}/{000}.gph   gzipped Valhalla graph tiles (Valhalla's own
#                                   layout, the names its tile_url fetcher asks for)
#   admins.sqlite                   country/state boundaries; Valhalla reads
#                                   driving side (Malaysia: left) from these
#   index.json                      every tile: level, id, bounding box, bytes
#
# Car-only: walking/cycling paths, platforms and construction are left out.
# Spike results (Oct 2026 data): build ~3 min on 4 cores; 714 tiles, 217 MB
# gzipped - highways (level 0) 13 MB, main roads (level 1) 22 MB, local
# streets (level 2) 182 MB, of which the Klang Valley is 34 MB.
#
# Needs: Python 3.10+, curl. Installs pyvalhalla (Valhalla's official PyPI
# build) into OUT_DIR/venv.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$(mkdir -p "${1:-$HERE/build}" && cd "${1:-$HERE/build}" && pwd)"
PBF_URL="${PBF_URL:-https://download.geofabrik.de/asia/malaysia-singapore-brunei-latest.osm.pbf}"
PBF_MIRROR="https://download.openstreetmap.fr/extracts/asia/malaysia-latest.osm.pbf"
PYVALHALLA_VERSION=3.9.1

cd "$OUT"
if [ ! -x venv/bin/python ]; then
  python3 -m venv venv
  venv/bin/pip install -q "pyvalhalla==$PYVALHALLA_VERSION"
fi
SITE="$(venv/bin/python -c 'import valhalla, os; print(os.path.dirname(os.path.dirname(valhalla.__file__)))')"
BIN="$SITE/valhalla/bin"
chmod +x "$BIN"/*  # wheel installs can drop the executable bit
export LD_LIBRARY_PATH="$SITE/pyvalhalla.libs"

echo "== OpenStreetMap extract"
curl -fsSL --retry 3 -o malaysia.osm.pbf "$PBF_URL" || curl -fsSL --retry 3 -o malaysia.osm.pbf "$PBF_MIRROR"
ls -l malaysia.osm.pbf

echo "== Valhalla config"
rm -rf tiles public && mkdir -p tiles public
venv/bin/python -m valhalla.valhalla_build_config \
  --mjolnir-tile-dir "$OUT/tiles" --mjolnir-admin "$OUT/tiles/admins.sqlite" \
  --mjolnir-concurrency "$(nproc)" > valhalla.json
python3 - <<'EOF'
import json
c = json.load(open("valhalla.json"))
m = c["mjolnir"]
m.update(include_pedestrian=False, include_bicycle=False, include_platforms=False, include_construction=False)
json.dump(c, open("valhalla.json", "w"), indent=1)
EOF

echo "== Boundaries and routing tiles"
"$BIN/valhalla_build_admins" -c valhalla.json malaysia.osm.pbf
"$BIN/valhalla_build_tiles" -c valhalla.json malaysia.osm.pbf

echo "== Static files"
python3 "$HERE/publish_layout.py" tiles public
du -sh public
