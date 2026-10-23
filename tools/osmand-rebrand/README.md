# NavOS Maps: OsmAnd rebranded as NavOS

Turns the official **OsmAnd~ 5.4.10** APK from F-Droid into **NavOS Maps**:
NavOS name, icon, splash screen and colours on top of OsmAnd's offline maps,
routing and navigation. Built from the APK alone, by decompiling it with
apktool, patching it and rebuilding.

```bash
tools/osmand-rebrand/rebrand.sh             # arm64-v8a, nearly every phone
tools/osmand-rebrand/rebrand.sh x86_64      # Android emulator
```

Or from the GitHub app: **Actions → "NavOS Maps (rebranded OsmAnd)" → Run
workflow**. The APK lands on the `navos-maps-<abi>` release.

| | |
|---|---|
| App name | NavOS |
| Package id | `com.excellentmindblow.navos.maps` (set `NAVOS_APP_ID` to change). It installs next to OsmAnd and next to the NavOS app. |
| Size | ~160 MB (OsmAnd's native map engine and resources) |

## Signing

Set `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` (and `KEY_PASSWORD` if
it differs) to sign with your release key. The workflow uses the same four
secrets as "Release APK (signed)". Without them the script makes a throwaway
test key. **Always sign with the same key**, or phones refuse to install the
next version over the old one.

## What gets changed, and why

Found by reading the decompiled code (smali) and resources.

**Package id** (`AndroidManifest.xml`). The new id needs these to move with it,
or the APK won't install next to OsmAnd:

- the `SERVICE_PERMISSION` and `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`
  signature permissions
- the `fileprovider`, `androidx-startup` and `picasso` provider authorities.
  The code builds the file provider's name from `getPackageName()`, so it
  still matches.
- `android:process` of the navigation and Android Auto services. It's set to
  the package name so they run in the app's main process. Left alone, they'd
  move into a separate process.

**Code** (`net/osmand/plus/Version.smali`):

- `isFullVersion()` turns on the full edition (unlimited map downloads) only
  when the package is exactly `net.osmand.plus`. It's patched to the new id.
  Otherwise, renaming the package would quietly turn the app into the
  limited edition.
- `isDeveloperBuild()` and `isDeveloperVersion()` check for `~` in the app
  name. The F-Droid build is called "OsmAnd~", but "NavOS" has no `~`. The
  check is changed to `""`, so both stay true, as they are in the original.
- `CarAppNotification`: the Android Auto notification title.

Code strings that work as identifiers are left alone: log tags, the
`Mozilla/5.0 (OsmAnd; …)` user agent its servers expect, the GPX `creator`,
the `Music/OsmAnd` media folder, settings keys, and AIDL interface names, so
OsmAnd plugins and companion apps still work.

**Text**: every "OsmAnd", "OsmAnd+" and "OsmAnd~" in the strings of all
locales becomes "NavOS" (about 7,600 replacements). URLs and paths that
contain the name stay as they are, so links to osmand.net and GitHub still
work.

**Colours**: OsmAnd's orange (app bar, logo tint, dark-theme accents)
becomes NavOS navy `#14284B` and blue `#1CB0F6`. The splash background
becomes black, like the NavOS splash. The light theme's blue accents stay
as they are, because they already read well on white.

**Artwork** (made by `brand_assets.py` from the NavOS app's own art in
`app/src/main/res`):

- launcher icons, including the adaptive and themed (monochrome) layers
- splash logos
- the 24dp in-app and notification logo, traced to a vector
- the wordmark and the 38dp logo

Every patch checks that its target still exists. A future OsmAnd release
that moves something stops the build with a message instead of shipping a
half-branded APK. To update OsmAnd, change the version codes and hashes in
`rebrand.sh` (they come from F-Droid's signed index) and run it again.

## License

OsmAnd is GPLv3, and so is this build. If you distribute the APK, you must
also offer its source: OsmAnd's source (github.com/osmandapp/OsmAnd, tag
for 5.4.10) plus this folder. The in-app links to OsmAnd's GitHub, and its
credits and licence screens, are kept on purpose.

The OsmAnd name and logo are OsmAnd's trademarks. That's why this build
removes them rather than presenting itself as OsmAnd. The app still
downloads maps from OsmAnd's servers, so heavy public use should be
discussed with OsmAnd.

## Restyle: the NavOS app design (`restyle.py`, `mapstyle.py`)

Run after `rebrand.py`. They follow the NavOS app design: light and airy,
white rounded cards, one bright blue (`#1278D8`), Poppins type, a light
grey map. Night mode is the design's deep navy. Both scripts change
OsmAnd's shared tokens, so every screen follows without editing screens
one by one.

**`restyle.py`, the app:**

- **Palette:** a light grey-blue screen background, white cards and sheets,
  ink text `#1B2433` and grey secondary text. The app bar and status bar
  are brand blue.
- **Night palette:** navy surfaces (`#0A1630`, `#11203D`) and a lighter
  blue `#4DA3FF`.
- **Buttons and chips:** primary buttons are blue. Secondary buttons and
  outlines are soft grey. Selected chips and rows get a light blue tint.
- **Shapes:** corners are rounder everywhere, about 12–16dp on cards,
  buttons and dialogs. Small corners in shape drawables get rounded up too.
- **Map buttons:** solid white rounded squares (12dp corners) with ink
  icons, navy at night.
- **Type:** Poppins (in `fonts/`, OFL) for all UI text. It reaches XML
  styles through the base themes and code-drawn text through `FontCache`.
  Scripts Poppins doesn't cover fall back to the system font.
- **Kept as they are:** data colours (charts, markers, profile colours,
  speed tolerance), because they carry meaning. The orange
  context-menu pin is recoloured blue.

**`mapstyle.py`, the map:** recolours OsmAnd's default map style. It
patches both copies: the Java one, and the Qt-compressed one the native
renderer uses.

- **Day:** the land is a cool light grey `#EEF1F5`. Roads are white with
  soft grey edges, water is a soft blue, and parks and woods are muted
  green. Everything else (buildings, labels, landuse) goes to cool greys
  of the same brightness.
- **Night:** deep navy land `#0E1A2E` and slate roads.
- **Route:** the brand blue, or the lighter blue at night.
- **Kept as they are:** colours that carry information, like route and
  track colours, subway lines, and hiking, surface and access overlays.

## No OsmAnd name on screen

`rebrand.py` replaces the name everywhere you can see it:

- **All 100+ languages:** case endings ("OsmAndin", "OsmAnd는"), caps
  ("OSMAND"), Arabic `أوسماند` and Serbian `ОсмАнд`.
- **On-screen text in the code:** the map source name, the routing
  service name, the print title, the feedback subject and error messages.
- **The `Music/OsmAnd` folder:** it's now `Music/NavOS`.
- **Images:** the wordmarks ("OsmAnd Pro", "OsmAnd+"…) become a NavOS
  wordmark in Poppins, and the pin logos become the NavOS glyph.

Links stay as they are, so osmand.net and GitHub links still work. So do
identifiers nobody sees: the user agent OsmAnd's servers check, the GPX
creator tag, log tags and resource names.

## NavOS's own UI code (`java/`, `navui.py`)

`rebrand.sh` compiles `java/src` with the Android SDK's `d8` and adds it to
the APK as an extra dex. `java/stubs` holds compile-only stand-ins for the
OsmAnd classes it calls; they aren't packaged. `navui.py` hooks the code in.

- **Main menu (`NavMenuSheet`):** the main menu is a bottom sheet instead
  of the side drawer. Profile chips (Car, Walk, Bike…) sit on top, and
  OsmAnd's menu items follow as a grid of rounded icon tiles, grouped as
  in the drawer. Items press-scale for feedback. Every item still runs
  OsmAnd's own action. The old drawer stays locked closed, so an edge
  swipe can't open it.

## Smaller APK (`slim.py`)

From about 160 MB to about 90 MB, without touching the map engine or
OsmAnd's data formats:

- **World overview map (40 MB):** removed from the APK. It's downloadable
  in Maps & Resources like any other map.
- **Translations:** only English, Malay, Chinese and Tamil stay (the text
  table drops from 34 MB to 6 MB). The script stops if a removed language
  folder held anything not defined elsewhere.
- **Images:** one xxhdpi bitmap per image instead of five densities.
  Launcher icons keep every size.
- **Signature:** v2/v3 only (minSdk 24), without the old v1 per-file hashes.
