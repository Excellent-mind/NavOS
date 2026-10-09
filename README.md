# NavOS

A Malaysia-focused turn-by-turn navigation app, built with React Native + Expo.

## Stack

- **Expo (managed)** — the whole app is buildable from the cloud via EAS Build, no computer needed
- **react-native-maps** — the map itself (Google Maps on Android, Apple Maps on iOS)
- **OSRM** (`router.project-osrm.org`, free public demo server) — route calculation + turn-by-turn steps
- **Nominatim** (OpenStreetMap, free) — destination search
- **expo-location** — live GPS tracking
- **expo-speech** — spoken turn announcements

## What's here

```
App.tsx                        entry point
src/screens/MapScreen.tsx      main screen: map, search, navigation UI
src/components/                SearchBar, TurnCard (next-turn), TripSummaryBar (ETA/stop)
src/services/
  geocoding.ts                 destination search (Nominatim)
  routing.ts                   route + turn-by-turn steps (OSRM)
  navigationEngine.ts          tracks current step, distance, off-route detection
  useLiveLocation.ts           GPS permission + live position hook
  voice.ts                     spoken instructions (expo-speech)
  geo.ts, polyline.ts          distance/bearing math, polyline decoding
```

## Before this runs for real

1. **Google Maps API key** (Android only — iOS uses Apple Maps for free).
   Get one free at [console.cloud.google.com](https://console.cloud.google.com)
   (enable "Maps SDK for Android"), then put it in `app.json` under
   `expo.android.config.googleMaps.apiKey`, replacing the placeholder.

2. **Install dependencies.** This repo has no `node_modules` committed (normal —
   see `.gitignore`). Nothing here needed to be installed to write the code,
   so this step happens the first time anyone actually runs or builds it:
   - Locally (if you ever get a computer): `npm install`
   - From your phone: GitHub Actions already does this automatically on
     every push (see **CI** below) — you don't have to do anything.

## Building an installable app from your phone

This project is set up so you never need a computer:

1. Create a free account at [expo.dev](https://expo.dev), then run
   `npx eas login` once *anywhere you have a browser* (or ask me to set this
   up differently if that's not possible from your phone) to get an
   `EXPO_TOKEN`.
2. Add that token as a GitHub Actions secret: repo **Settings → Secrets and
   variables → Actions → New repository secret**, name it `EXPO_TOKEN`.
3. From the GitHub app: **Actions tab → "EAS Build (Android APK)" → Run
   workflow**. Expo's cloud builds the APK; when it's done, the build page
   on expo.dev gives you a download link and a QR code to install directly
   on an Android phone.

## CI

`.github/workflows/ci.yml` runs on every push: installs dependencies and
type-checks the project on GitHub's servers. This is how you'll know if
something's broken, since this can't be verified from the sandbox that
wrote the code.

## Known limits / next steps

- **Routing engine**: using OSRM's free public demo server. It's rate-limited
  and not meant for production traffic — fine for development, but before a
  real launch this should move to a self-hosted OSRM instance or a paid
  routing API (Mapbox Directions).
- **Destination search**: Nominatim's public instance, same caveat (rate
  limited, ~1 request/sec) — fine for now.
- **Re-routing**: the app currently detects when you've drifted off the
  route (`navigationEngine.ts`, `offRoute` flag) but doesn't yet
  automatically recalculate — that's the next thing to build.
- **Lane guidance / speed limits / offline maps**: not built yet — Pintaz-level
  parity is a longer roadmap, this is the MVP skeleton.
- Placeholder icon/splash images are in `assets/` — swap for your own
  Excellent Mindblow branding whenever you're ready.
