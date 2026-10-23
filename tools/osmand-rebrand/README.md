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

## Restyle: the NavOS "Signal" look (`restyle.py`)

Runs after `rebrand.py`. It changes OsmAnd's shared design tokens, so
every screen follows without editing screens one by one. Values come from
the NavOS app's Signal palette (`app/src/main/res/values*/colors.xml`).

- **Surfaces and text:** white and black, with Signal greys in both themes.
  The night theme is pure black.
- **App bar and status bar:** Signal ink (near-black) instead of orange.
- **Accent:** NavOS navy `#14284B` in the light theme, NavOS blue `#1CB0F6`
  at night.
- **Buttons:** primary buttons are ink (black on light, white at night).
  Secondary buttons are the field grey.
- **Map buttons:** solid instead of 50% see-through, and all round,
  including the menu and route buttons. They're white in the light theme
  and `#1C1C1C` at night, like the NavOS floating buttons.
- **Type:** Atkinson Hyperlegible for all UI text. XML styles get it from
  the base themes. Code-drawn text gets it through `FontCache`, which
  loads it once at app start.
- **Kept as they are:** data colours (charts, markers, profile colours,
  speed tolerance), because they carry meaning.
