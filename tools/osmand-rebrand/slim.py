"""Makes the APK smaller without touching OsmAnd's map data or engine, in
place.

Usage: python3 slim.py DECODED_DIR

- The bundled world overview map (40 MB) goes; it's in the in-app map
  downloads like any other map.
- Translations: only the languages NavOS serves (English, Malay, Chinese,
  Tamil) are kept. The text table carried ~100 languages (34 MB).
- Images: one bitmap per image (xxhdpi, the most common phone density)
  instead of five; Android scales it for other screens.
"""
import json
import re
import shutil
import sys
from pathlib import Path

from rebrand import fail

KEEP_LANGUAGES = {"en", "ms", "zh", "ta"}
# Qualifiers that look like a 2-3 letter language code but aren't one.
NOT_LANGUAGES = {"car", "land", "port", "night", "notnight", "watch", "large", "small", "normal", "long",
                 "round", "ldrtl", "ldltr", "xlarge", "desk", "vrheadset", "appliance", "television"}
DENSITIES = ["ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"]
KEEP_DENSITY = "xxhdpi"


def language_of(folder: str):
    """Language a resource folder is for (values-ms, values-zh-rTW, values-b+sr+Latn), or None."""
    parts = folder.split("-")[1:]
    if not parts:
        return None
    first = parts[0]
    if first.startswith("b+"):
        return first.split("+")[1]
    if re.fullmatch(r"[a-z]{2,3}", first) and first not in NOT_LANGUAGES:
        return first
    return None


VALUE_NAME = re.compile(r'<(?:string|plurals|string-array|array|integer-array|item|color|dimen|bool|integer|style)'
                        r' name="([^"]+)"')


def resource_names(folder: Path) -> set:
    if folder.name.startswith("values"):
        return {n for f in folder.glob("*.xml") for n in VALUE_NAME.findall(f.read_text(encoding="utf-8"))}
    return {f.name.split(".")[0] for f in folder.iterdir()}


def drop_world_map(root: Path):
    obf = root / "assets/World_basemap_mini.obf"
    listing = root / "assets/bundled_assets.json"
    data = json.loads(listing.read_text(encoding="utf-8"))
    before = len(data["assets"])
    data["assets"] = [a for a in data["assets"] if a.get("source") != "World_basemap_mini.obf"]
    if not obf.exists() or len(data["assets"]) != before - 1:
        fail("bundled world map not found")
    obf.unlink()
    listing.write_text(json.dumps(data, indent=4), encoding="utf-8")


def drop_languages(root: Path):
    res = root / "res"
    dropped = [d for d in res.iterdir() if d.is_dir() and language_of(d.name) not in (None, *KEEP_LANGUAGES)]
    # Nothing may live only in a dropped folder, or the build can't resolve it.
    kept_names = {}
    for k in res.iterdir():
        if k.is_dir() and k not in dropped:
            kept_names.setdefault(k.name.split("-")[0], set()).update(resource_names(k))
    for d in dropped:
        orphans = resource_names(d) - kept_names.get(d.name.split("-")[0], set())
        if orphans:
            fail(f"{d.name} defines {len(orphans)} resources found nowhere else, e.g. {sorted(orphans)[:3]}")
    for d in dropped:
        shutil.rmtree(d)
    config = res / "xml/locales_config.xml"
    text = config.read_text(encoding="utf-8")
    text = re.sub(r'\s*<locale android:name="([^"]+)" />',
                  lambda m: m.group(0) if re.split(r"[-_]", m.group(1))[0] in KEEP_LANGUAGES else "", text)
    config.write_text(text, encoding="utf-8")
    return len(dropped)


def drop_extra_densities(root: Path):
    """For each bitmap that has an xxhdpi version, drop the other densities'
    copies. Vector and XML drawables, and bitmaps with no xxhdpi copy, stay."""
    res = root / "res"
    groups = {}
    for d in res.iterdir():
        if not d.is_dir() or not d.name.startswith(("drawable", "mipmap")):
            continue
        quals = d.name.split("-")
        density = next((q for q in quals if q in DENSITIES), None)
        if density is None:
            continue
        key = "-".join(q for q in quals if q != density)
        groups.setdefault(key, {})[density] = d
    removed = 0
    for key, by_density in groups.items():
        keep = by_density.get(KEEP_DENSITY)
        if keep is None or key.startswith("mipmap"):  # launcher icons keep every size
            continue
        kept = {f.name for f in keep.iterdir()}
        for density, d in by_density.items():
            if density == KEEP_DENSITY:
                continue
            for f in d.iterdir():
                if f.suffix in (".png", ".webp", ".jpg") and not f.name.endswith(".9.png") and f.name in kept:
                    f.unlink()
                    removed += 1
    return removed


def main():
    if len(sys.argv) != 2:
        fail("usage: slim.py DECODED_DIR")
    root = Path(sys.argv[1])
    drop_world_map(root)
    langs = drop_languages(root)
    images = drop_extra_densities(root)
    print(f"slim: world map removed, {langs} language folders removed, {images} duplicate bitmaps removed")


if __name__ == "__main__":
    main()
