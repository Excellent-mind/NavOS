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
