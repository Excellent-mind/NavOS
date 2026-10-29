# NavOS — working rules

## App version
Every change that adds or changes something the user can see or use bumps the
app version in `app/build.gradle.kts`, in the same commit:

- `versionCode` + 1 every time.
- `versionName` (MAJOR.MINOR.PATCH):
  - new feature or screen → MINOR + 1, PATCH back to 0 (0.7.0 → 0.8.0)
  - fix or small tweak only → PATCH + 1 (0.7.0 → 0.7.1)

Name the version in the commit message ("v0.8.0: ...") and when sending the APK.
The version shows in Settings → About.

History: 0.7.0 (code 7) = Compose UI phase 6 (petrol/EV/parking/train pins,
station card, live traffic, faster-route prompt).
0.8.0 (code 8) = new NavOS logo: launcher icon (adaptive + themed), animated
opening screen and logo loading animation.
0.9.0 (code 9) = map logic: smart drive camera (zoom by speed/turns, free/overview,
Re-centre), long-press pin + tap places on the home map, compass and locate,
route bubbles and tap-to-pick on route choice, zoom-staged place icons, 3D
buildings, one-way arrows, house numbers, live traffic on roads around you.
0.9.1 (code 10) = drive fixes: arrow snapped onto the route line (navigation
arrow while driving), drive sheet slides up/down and tucks itself while moving.
0.10.0 (code 11) = AES / AwAS speed-camera warnings (bundled OSM cameras,
tools/cameras/build.py -> assets/cameras.json), camera count on route cards,
camera pins; home screen without the bottom sheet.
0.10.1 (code 12) = sign and line polish: JKR-style turn glyphs (ManeuverGlyph),
exit-number tag and destinations on the sign (TurnSign), green sign on
expressway exits, bigger driving arrow, driven part of the route hidden,
turn arrow painted on the route before each junction.
0.11.0 (code 13) = Google Maps-style turn banner (TurnBanner): green banner,
big icon with distance under it, road name large + "towards ...", exit and
shield chips, lane band, "Then" tab only for back-to-back turns; roundabout
icons trace the clockwise path round a faded ring.
0.11.1 (code 14) = roundabout icons redrawn (bigger ring, slim strokes, head on a short stub).
0.11.2 (code 15) = old React Native app (legacy-expo-rn) removed, README rewritten; merged to main.
0.12.0 (code 16) = drive fixes: route line cut at the car (no gradient), turn
arrowhead sized to stay inside the line, Petrol/EV/Parking/Train chips answer
under them (nearest places, none on the way, or failed + Try again), equal
chips fit 360 dp, Overpass mirror fallback, "onto the road" dropped from the sign.
0.13.0 (code 17) = turn list (tap the banner), unnamed links take the next road's
name, map style: kerbs drawn before all asphalt (no kerb lines across joins), lane
paint only on big roads, fewer one-way arrows (no service roads / ramps).
0.13.1 (code 18) = route line cut where the arrow comes from (last fix, 3 m back), so it
always starts under the arrow instead of ahead of it while the arrow glides.
0.14.0 (code 19) = Waze logic: report kinds jam / speed trap / road closed (worker
TTLs), "Still there?" vote after passing a report, traffic-ahead warning with
delay, speeding chime, voice modes (On / Alerts only / Off), Share ETA,
parked-car memory on the home map.
0.14.1 (code 20) = recent-places clock icon redrawn (the arc was wrong and drew scribbles).

## Traffic server
Cloudflare Worker `navos-traffic` (server/traffic-worker). Deploy with the
Cloudflare MCP: multipart PUT of worker.js keeping the D1 binding `DB` and the
`IP_SALT` secret (keep_bindings: secret_text); apply schema changes to D1 first.
