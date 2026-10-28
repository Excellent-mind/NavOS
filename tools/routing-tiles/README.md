# Routing tiles: routing on the phone, no routing server

`build.sh` turns Malaysia's OpenStreetMap data into **Valhalla** routing
tiles: static files that any web host can serve. Valhalla, an open-source
routing engine, then calculates routes on the phone from those tiles.
`.github/workflows/routing-tiles.yml` runs the build on demand.

## Spike results (October 2026)

Measured in this repo's dev container with Valhalla 3.9.1 (pyvalhalla) and
the `valhalla-mobile` 0.6.4 Android library.

| Question | Result |
|---|---|
| Build time | About 3 min for the tiles, about 8 min including setup and download |
| Size | 714 tiles, 217 MB gzipped. Highways (level 0) are 13 MB, main roads (level 1) 22 MB, local streets (level 2) 182 MB. The Klang Valley's local streets are 34 MB. |
| Fits static hosting? | Yes. The largest gzipped tile is 13 MB, and 714 files is well within free static hosting limits. |
| Routing from static tiles, fetched on demand | **Works** with desktop Valhalla (`tile_url` + `tile_url_gz`, `loki.use_connectivity: false`). KLCC → Mid Valley gave 9.9 km and 16 maneuvers. KLCC → Penang Bridge gave 361 km. A repeat trip used 0 MB and was instant. |
| Data per first trip in a new area | High: 27 MB for a 10 km KL trip and 25 MB more for KL → Penang (gzipped). Valhalla needs the tiles **along the whole route**, at every level. |
| Android | `io.github.rallista:valhalla-mobile` adds 7.5 MB (arm64). It can return OSRM-format responses, which is what NavOS already parses. **It isn't built with curl**, so it can't fetch tiles itself. NavOS would have to download them into Valhalla's tile folder before routing. |

## Open decisions before wiring this into the app

- How tiles reach the phone: per-trip download along the route corridor
  (heavy for the first trip in an area), or region packs, e.g. highways and
  main roads for all of Malaysia (35 MB) plus the local streets of the user's
  area (Klang Valley: 34 MB).
- Where tiles are hosted (Cloudflare Pages/R2, as for the place tiles).
