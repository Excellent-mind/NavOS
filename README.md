# NavOS

A Malaysia-first turn-by-turn navigation app: native Android, Kotlin and
Jetpack Compose on the MapLibre map engine, built only on free and open data.
English and everyday Malay.

## What it does

- **Search** places and addresses (Photon / Nominatim, bundled place tiles),
  Home / Work, recents, and a learned "where you're probably going".
- **Route choice**: alternatives with time, distance, toll price (LLM fares,
  refreshed weekly from Supabase), AES camera count, and a strip of what's on
  the way. Tap a route's bubble or line on the map to pick it.
- **Drive**:
  - Google Maps-style turn banner (exit number, road shields, lanes, "Then").
  - Smart camera that zooms with speed and at turns, with Re-centre and Overview.
  - The arrow snapped to the route, the driven part hidden, a turn arrow
    painted at each junction.
  - Voice guidance and rerouting with a faster-route offer.
  - Speed limit, AES / AwAS camera warnings, and drivers' road reports.
  - PLUS Smart Lanes, petrol / EV stations on your side of the road, parking
    and train stations at the destination.
  - Live traffic on the route and on roads around you.
- **Map** ("Jalan" style, day and night): roads at their real width with
  lane markings, JKR route shields, place icons staged by zoom, 3D buildings,
  one-way arrows, house numbers.

## Layout

```
app/src/main/java/com/excellentmindblow/navos/
  ui/            Jetpack Compose UI: theme, map, home, search, route, drive, settings, brand
  navigation/    navigation engine and service, drive camera, voice, trip strip
  routing/       OSRM routing, tolls, road points
  search/        geocoding, saved / recent places, habits
  traffic/       live traffic, road reports, Smart Lanes, speed cameras
  poi/  signs/  location/
app/src/main/assets/   map styles, toll tables, Smart Lanes, speed cameras
server/traffic-worker/ Cloudflare Worker + D1 for live traffic and reports
tools/                 builders for the map style, tolls, cameras, logos, place tiles
```

## Building

- **No computer needed**: in the GitHub app open **Actions → Native Android
  Build → Run workflow**, then download the `navos-debug-apk` artifact.
- **Locally**: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`.
- **Versioning**: every change you can see bumps the version, as described in
  `CLAUDE.md`. The current version shows in Settings → About.

## Data sources

All of these are free:

- Map: OpenFreeMap tiles (OpenMapTiles schema).
- Routing: OSRM's public demo server. It is rate-limited, so a real launch
  should self-host OSRM.
- Search: Photon and Nominatim.
- Points of interest and speed cameras: OpenStreetMap (Overpass).
- Toll fares: Lembaga Lebuhraya Malaysia.

## License

NavOS is free software under the [GNU General Public License v3.0](LICENSE).

Credits:

- Map and camera data © OpenStreetMap contributors (ODbL).
- Places from Overture Maps Foundation.
- Icons: Lucide (ISC).
- Font: Overpass (SIL Open Font License).
- Petrol company logos and JKR signs: Wikimedia Commons (see the licences in
  the app). The logos are trademarks of their owners.
