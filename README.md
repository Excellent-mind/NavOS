# NavOS

A Malaysia-focused turn-by-turn navigation app, built as a **native Android
app in Kotlin**.

## Why native, not React Native

The original build was React Native + Expo (still archived in
`legacy-expo-rn/` for reference — the routing/navigation/voice logic there
is pure TypeScript and worth porting over). It crashed instantly on launch
on the target device with no error dialog and no logs available (no PC, no
`adb`). Device bisection across many EAS builds eventually traced it to
`react-native-safe-area-context` colliding with Android 15+'s mandatory
edge-to-edge enforcement on that device's Android build — but by the time
that was confirmed and fixed, a second, not-yet-isolated crash source
turned up and the EAS free-tier monthly Android build quota ran out mid
diagnosis. Going native sidesteps both: no EAS quota (GitHub Actions'
Gradle builds are free and unlimited), and no JS bridge between the app and
MapLibre's native map engine.

## Stack

- **Kotlin**, native Android views (no React Native, no JS bridge)
- **MapLibre Android SDK** (`org.maplibre.gl:android-sdk-opengl`) — the
  exact same open-source map engine the old `@maplibre/maplibre-react-native`
  build wrapped, used directly. Tiles from [OpenFreeMap](https://openfreemap.org)
  (free, no API key, no rate limit, production-safe)
- Everything else (OSRM routing, Nominatim search, GPS, TTS voice
  announcements) is being ported over from the archived React Native app -
  see **Status** below.

## What's here

```
app/src/main/java/com/excellentmindblow/navos/MainActivity.kt   entry point + map screen
app/src/main/res/                                                layout, strings, launcher icon
legacy-expo-rn/                                                  archived RN app (reference only, not built)
tools/osmand-rebrand/                                            "NavOS Maps": the OsmAnd APK rebranded as NavOS
```

## Building — no computer needed

GitHub Actions does the build, same as before, but through plain Gradle
instead of EAS — **no account, no token, no monthly build quota**:

1. From the GitHub app: **Actions tab → "Native Android Build" → Run
   workflow**.
2. When it finishes, open the run and download the `navos-debug-apk`
   artifact (or Claude can fetch and send it directly in chat).

This is a **debug** build (self-signed with Android's default debug key) -
fine for installing and testing on your own phone. Release signing for a
real launch is a later step.

## Status

Built: map, destination search (Photon + Nominatim), OSRM routes with
alternatives, turn-by-turn with lane guidance, live GPS, spoken guidance
(ducks other audio), off-route rerouting, background navigation service,
speed-camera alerts, toll plaza markers (no prices), petrol / EV pins,
posted speed limit + over-limit warning (OpenStreetMap data), km / miles,
avoid tolls / highways, Share ETA, **multi-stop trips** (up to 8 stops: add /
remove / reorder / optimize order in the route preview, add or skip a stop
while driving, numbered map pins, next-stop ETA, stops survive rerouting).

Deliberately not built (no free, serverless source): live traffic and
crowdsourced incident reports. ETAs are "typical time, no live traffic".

## Known limits / next steps

- **Routing engine**: OSRM's free public demo server - rate-limited, not
  meant for production traffic. Fine for development; a real launch should
  move to a self-hosted OSRM instance or a paid routing API.
- **Destination search**: Nominatim's public instance, same caveat.
- **Speed limits** only appear where OpenStreetMap has a `maxspeed` tag.
- **Avoid tolls / highways** needs a routing server built with OSRM's `exclude` support; the public demo server may refuse it (the app says so).
- **Multi-stop trips** come back as one route through the stops in the order
  listed - OSRM only offers alternative routes for plain A -> B trips. "Optimize
  order" and the automatic spot for a newly added stop use straight-line
  distances, not road distances.
- **Offline maps, depart-at, motorcycle mode, Android Auto**: not built yet.
- Placeholder launcher icon (a simple generated compass glyph) - swap for
  real Excellent Mindblow branding whenever ready.

## Continuous integration and release signing

- **Android CI** runs unit tests, Android lint, and a debug build on pushes to
  `main` / `master`, pull requests, and manual dispatch. Pull-request jobs use
  read-only repository permissions.
- **Native Android Build** is the manual debug-APK convenience workflow. The
  APK build runs read-only; a separate publication job receives only the APK
  and has release-write permission.
- **Release APK (signed)** uses the four Actions secrets documented in
  [`docs/ANDROID_SIGNING.md`](docs/ANDROID_SIGNING.md). Never upload signing
  credentials or keystores as workflow artifacts. Keep the existing release
  keystore backed up securely; creating a different key can prevent existing
  installs from accepting future updates.

Location and navigation behavior should still be tested on a physical device
before release, including GPS disabled/enabled transitions, permission
revocation, screen-off navigation, network loss, and rerouting.

## License

NavOS is free software under the [GNU General Public License v3.0](LICENSE):
you may use, study, change and share it, but apps built from it must also be
released as open source under the same license.

Map data © OpenStreetMap contributors (ODbL). Places from Overture Maps
Foundation. Icons: Lucide (ISC). Fonts: Poppins, Saira and Atkinson Hyperlegible
(SIL Open Font License). Toll fares are from the public Kalkulator Tol at
llm.gov.my (Lembaga Lebuhraya Malaysia).
