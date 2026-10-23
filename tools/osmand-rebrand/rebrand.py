"""Rebrands an apktool-decoded OsmAnd APK as NavOS, in place.

Usage: python3 rebrand.py DECODED_DIR [APPLICATION_ID]

Each step fails loudly when the thing it patches is missing, so a new OsmAnd
release that moves code around stops the build instead of shipping a
half-branded or broken APK. See README.md for why each patch exists.
"""
import re
import sys
from pathlib import Path

import brand_assets as art

OSMAND_ID = "net.osmand.plus"
APP_NAME = "NavOS"


def fail(msg: str):
    sys.exit(f"rebrand: {msg}")


def replace_once(path: Path, old: str, new: str, count: int = 1):
    text = path.read_text(encoding="utf-8")
    found = text.count(old)
    if found != count:
        fail(f"{path.name}: expected {count} x {old!r}, found {found}")
    path.write_text(text.replace(old, new), encoding="utf-8")


def smali(root: Path, rel: str) -> Path:
    hits = list(root.glob(f"smali*/{rel}"))
    if len(hits) != 1:
        fail(f"{rel}: expected one smali file, found {len(hits)}")
    return hits[0]


def method_body(path: Path, signature: str) -> tuple[str, int, int]:
    text = path.read_text(encoding="utf-8")
    start = text.find(f".method {signature}")
    if start < 0:
        fail(f"{path.name}: method {signature} not found")
    end = text.index(".end method", start)
    return text, start, end


# --- Package id ------------------------------------------------------------

def patch_manifest(root: Path, app_id: str):
    """New application id, plus every non-class name derived from the old one.

    Permissions and provider authorities must move with the id, or the app
    can't be installed next to OsmAnd (Android refuses duplicate authorities
    and signature permissions defined by a differently signed app). The
    navigation and Android Auto services run in a process named after the
    package; left as-is they'd split off into a separate process.
    """
    manifest = root / "AndroidManifest.xml"
    replace_once(manifest, f'package="{OSMAND_ID}"', f'package="{app_id}"')
    replace_once(manifest, f'android:process="{OSMAND_ID}"', f'android:process="{app_id}"', count=2)
    for suffix, count in [
        ("SERVICE_PERMISSION", 3),
        ("DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION", 2),
        ("fileprovider", 1),
        ("androidx-startup", 1),
        ("com.squareup.picasso", 1),
    ]:
        replace_once(manifest, f'"{OSMAND_ID}.{suffix}"', f'"{app_id}.{suffix}"', count=count)


def patch_code(root: Path, app_id: str):
    """Keep the F-Droid build's feature set under the new name.

    Version.isFullVersion() unlocks the full (unlimited downloads) edition only
    when the package is exactly net.osmand.plus. isDeveloperBuild() and
    isDeveloperVersion() key off a "~" in app_name, which the F-Droid build's
    "OsmAnd~" has and "NavOS" doesn't; matching "" keeps them true.
    """
    version = smali(root, "net/osmand/plus/Version.smali")
    text, start, end = method_body(version, "public static isFullVersion(")
    body = text[start:end]
    if f'"{OSMAND_ID}"' not in body:
        fail("Version.isFullVersion no longer compares the package name")
    version.write_text(text[:start] + body.replace(f'"{OSMAND_ID}"', f'"{app_id}"') + text[end:], encoding="utf-8")

    for name in ("isDeveloperBuild(", "isDeveloperVersion("):
        text, start, end = method_body(version, f"public static {name}")
        body = text[start:end]
        if body.count('"~"') != 1:
            fail(f"Version.{name}) no longer checks app_name for '~'")
        version.write_text(text[:start] + body.replace('"~"', '""') + text[end:], encoding="utf-8")

    replace_once(
        smali(root, "net/osmand/plus/auto/CarAppNotification.smali"),
        '"OsmAnd Android Auto"', f'"{APP_NAME} Android Auto"',
    )


# --- Text ------------------------------------------------------------------

# "OsmAnd" as a word in UI text. Not when it's part of a URL or path
# (github.com/osmandapp/OsmAnd/issues) or of a longer identifier.
BRAND_WORD = re.compile(r"(?<![\w/.@-])Osm[Aa]nd(?:\+|~)?(?![\w/]|\.\w)")


def patch_strings(root: Path):
    total = 0
    for f in sorted((root / "res").glob("values*/*.xml")):
        if f.name not in ("strings.xml", "plurals.xml", "arrays.xml"):
            continue
        text = f.read_text(encoding="utf-8")
        text, n = BRAND_WORD.subn(APP_NAME, text)
        # The OsmAnd acronym's expansion doesn't describe NavOS.
        text = text.replace(f"{APP_NAME} (OSM Automated Navigation Directions)", APP_NAME)
        text, m = re.subn(r'(<string name="app_name"[^>]*>)[^<]*(</string>)', rf"\g<1>{APP_NAME}\g<2>", text)
        if n or m:
            f.write_text(text, encoding="utf-8")
            total += n
    if total < 100:
        fail(f"only {total} 'OsmAnd' mentions replaced; string resources changed shape")
    return total


# --- Colours ---------------------------------------------------------------

# OsmAnd's orange becomes the NavOS navy / blue. Light-theme blues (#237bff)
# stay: they're already close to the brand and pass contrast on white.
COLORS = {
    "app_bar_main_light": "#14284b",
    "osmand_orange": "#1cb0f6",
    "osmand_orange_dark": "#1599d6",
    "icon_color_osmand_light": "#14284b",
    "icon_color_osmand_dark": "#1cb0f6",
    "active_color_primary_dark": "#1cb0f6",
    "active_color_primary_dark_v2": "#1cb0f6",
    "icon_color_active_dark": "#1cb0f6",
    "icon_color_active_dark_v2": "#1cb0f6",
    "splash_screen_background_color": "#000000",  # NavOS splash: logo on black
}


def patch_colors(root: Path):
    seen = set()
    for f in sorted((root / "res").glob("values*/colors.xml")):
        text = f.read_text(encoding="utf-8")
        for name, value in COLORS.items():
            text, n = re.subn(rf'(<color name="{name}">)[^<]*(</color>)', rf"\g<1>{value}\g<2>", text)
            if n:
                seen.add(name)
        f.write_text(text, encoding="utf-8")
    missing = set(COLORS) - seen
    if missing:
        fail(f"colours not found: {sorted(missing)}")


# --- Artwork ---------------------------------------------------------------

def save(img, path: Path):
    if not path.exists():
        fail(f"{path.relative_to(path.parents[2])} not in the APK; artwork moved")
    if path.suffix == ".webp":
        img.save(path, "WEBP", lossless=True)
    else:
        img.save(path, optimize=True)


ADAPTIVE_ICON = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/adaptive_icon_osmand_background" />
    <foreground android:drawable="@mipmap/adaptive_icon_osmand_logo" />
    <monochrome android:drawable="@drawable/ic_launcher_osmand_monochrome" />
</adaptive-icon>
"""


def patch_artwork(root: Path):
    res = root / "res"
    for density, scale in art.DENSITIES.items():
        mip = res / f"mipmap-{density}"
        for name in ("icon", "icon_free", "icon_nightly"):
            save(art.round_icon(round(48 * scale)), mip / f"{name}.png")
        save(art.adaptive_background(round(108 * scale)), mip / "adaptive_icon_osmand_background.png")
        for name in ("adaptive_icon_osmand_logo", "adaptive_icon_osmand_plus_logo"):
            save(art.adaptive_foreground(round(108 * scale)), mip / f"{name}.png")

    # Same artwork for every edition (OsmAnd's free / plus / nightly icons).
    for name in ("icon", "icon_free", "icon_nightly"):
        save_text(res / f"mipmap-anydpi-v26/{name}.xml", ADAPTIVE_ICON)
    for name in ("ic_launcher_osmand_monochrome", "ic_launcher_osmand_plus_monochrome"):
        save_text(res / f"drawable/{name}.xml", art.vector_xml(108, 108, art.ADAPTIVE_GLYPH_SHARE, "#ffffff"))
    # In-app and notification logo; tinted at runtime, so the colour is OsmAnd's neutral.
    save_text(res / "drawable/ic_action_osmand_logo.xml", art.vector_xml(24, 24, 0.84, "#727272"))

    for f in res.glob("drawable-*/ic_logo_splash_osmand*.png"):
        with art.Image.open(f) as old:
            size = old.size[0]
        save(art.splash_logo(size), f)
    for f in res.glob("drawable-*/osmand_logo.png"):
        with art.Image.open(f) as old:
            size = old.size
        save(art.wordmark(*size), f)
    for f in res.glob("drawable-*/img_logo_38dp_osmand.png"):
        with art.Image.open(f) as old:
            size = old.size[0]
        save(art.round_icon(size), f)


def save_text(path: Path, text: str):
    if not path.exists():
        fail(f"{path.name} not in the APK; artwork moved")
    path.write_text(text, encoding="utf-8")


def main():
    if len(sys.argv) not in (2, 3):
        fail("usage: rebrand.py DECODED_DIR [APPLICATION_ID]")
    root = Path(sys.argv[1])
    app_id = sys.argv[2] if len(sys.argv) == 3 else "com.excellentmindblow.navos.maps"
    patch_manifest(root, app_id)
    patch_code(root, app_id)
    n = patch_strings(root)
    patch_colors(root)
    patch_artwork(root)
    print(f"rebrand: {app_id}, {n} text mentions renamed, colours and artwork replaced")


if __name__ == "__main__":
    main()
