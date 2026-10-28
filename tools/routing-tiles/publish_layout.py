"""Turns a Valhalla tile directory into the static files NavOS fetches.

Usage: python3 publish_layout.py TILE_DIR OUT_DIR

Each .gph tile is gzipped under its own name (Valhalla's tile_url fetcher
asks for "{level}/000/000/000.gph" and inflates it when tile_url_gz is on;
NavOS's own fetcher does the same). index.json lists every tile with its
level, id, bounding box and size, so the app can work out which tiles a trip
needs before routing.
"""
import gzip
import json
import shutil
import sys
from pathlib import Path

# Valhalla's tile hierarchy: level -> tile size in degrees, on a grid from (-180, -90).
TILE_DEGREES = {0: 4.0, 1: 1.0, 2: 0.25}


def main():
    src, out = Path(sys.argv[1]), Path(sys.argv[2])
    tiles = []
    for f in sorted(src.glob("[0-9]/**/*.gph")):
        rel = f.relative_to(src)
        level = int(rel.parts[0])
        tile_id = int("".join(rel.parts[1:]).removesuffix(".gph"))
        size = TILE_DEGREES[level]
        row, col = divmod(tile_id, int(360 / size))
        dest = out / rel
        dest.parent.mkdir(parents=True, exist_ok=True)
        with f.open("rb") as raw, gzip.open(dest, "wb", compresslevel=9) as gz:
            shutil.copyfileobj(raw, gz)
        tiles.append({
            "level": level, "id": tile_id, "path": str(rel),
            "bbox": [-180 + col * size, -90 + row * size, -180 + (col + 1) * size, -90 + (row + 1) * size],
            "bytes": dest.stat().st_size,
        })
    if not tiles:
        sys.exit(f"no .gph tiles under {src}")
    shutil.copy(src / "admins.sqlite", out / "admins.sqlite")
    (out / "index.json").write_text(json.dumps({"tiles": tiles}, separators=(",", ":")))
    total = sum(t["bytes"] for t in tiles)
    print(f"publish_layout: {len(tiles)} tiles, {total / 1e6:.1f} MB gzipped")


if __name__ == "__main__":
    main()
