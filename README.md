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

This is a fresh start on the native side - currently just the map screen,
rebuilding confidence that the core (map rendering) works cleanly on the
target device before porting over:

- [ ] Destination search (Nominatim)
- [ ] Route calculation + turn-by-turn steps (OSRM)
- [ ] Live GPS tracking
- [ ] Spoken turn announcements (Android `TextToSpeech`)
- [ ] Off-route detection / re-routing

## Known limits / next steps

- **Routing engine**: OSRM's free public demo server - rate-limited, not
  meant for production traffic. Fine for development; a real launch should
  move to a self-hosted OSRM instance or a paid routing API.
- **Destination search**: Nominatim's public instance, same caveat.
- **Lane guidance / speed limits / offline maps**: not built yet.
- Placeholder launcher icon (a simple generated compass glyph) - swap for
  real Excellent Mindblow branding whenever ready.
