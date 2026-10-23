"""Restyles an apktool-decoded (and rebranded) OsmAnd APK in the NavOS app
design, in place: light and airy, white rounded cards, one bright blue,
Poppins type, rounded-square map buttons.

Usage: python3 restyle.py DECODED_DIR

Everything goes through OsmAnd's shared tokens - colour resources, corner
radii, base themes, the font helper, map button defaults - so every screen
follows without per-screen edits.
"""
import re
import shutil
import sys
from pathlib import Path

from PIL import Image

from rebrand import fail, smali, method_body, replace_once

HERE = Path(__file__).resolve().parent

# Design tokens, light / night. Night is the design's deep navy.
INK, NIGHT_INK = "#1b2433", "#ffffff"                    # text
QUIET, NIGHT_QUIET = "#8a94a6", "#9fb0cc"                # secondary text
BG, NIGHT_BG = "#f4f6fa", "#0a1630"                      # screen background
SHEET, NIGHT_SHEET = "#ffffff", "#11203d"                # cards, sheets, lists
FIELD, NIGHT_FIELD = "#f0f3f8", "#1a2c4d"                # inputs, secondary buttons
OUTLINE, NIGHT_OUTLINE = "#dce3ee", "#24395e"
DIVIDER, NIGHT_DIVIDER = "#edf1f6", "#1a2c4d"
BLUE, NIGHT_BLUE = "#1278d8", "#4da3ff"                  # the one accent
BLUE_PRESSED, NIGHT_BLUE_PRESSED = "#0e63b5", "#2f8ef0"
SELECTED, NIGHT_SELECTED = "#e4f0fc", "#17345c"          # selected chip / row tint

COLORS = {
    # Surfaces
    "activity_background_color_light": BG,
    "activity_background_color_dark": NIGHT_BG,
    "list_background_color_light": SHEET,
    "list_background_color_dark": NIGHT_SHEET,
    "card_and_list_background_light": SHEET,
    "card_and_list_background_dark": NIGHT_SHEET,
    # App bar and status bar: the brand blue by day, navy at night
    "app_bar_main_light": BLUE,
    "app_bar_main_dark": NIGHT_SHEET,
    "app_bar_secondary_dark": NIGHT_SHEET,
    "status_bar_main_light": BLUE,
    "status_bar_main_dark": NIGHT_BG,
    "status_bar_secondary_light": BG,
    "status_bar_secondary_dark": NIGHT_BG,
    "status_bar_dim_light": BLUE_PRESSED,
    # Text
    "text_color_primary_light": INK,
    "text_color_primary_dark": NIGHT_INK,
    "text_color_secondary_light": QUIET,
    "text_color_secondary_dark": NIGHT_QUIET,
    # Lines
    "divider_color_light": DIVIDER,
    "divider_color_dark": NIGHT_DIVIDER,
    "stroked_buttons_and_links_outline_light": OUTLINE,
    "stroked_buttons_and_links_outline_dark": NIGHT_OUTLINE,
    # Accent
    "active_color_primary_light": BLUE,
    "active_color_primary_dark": NIGHT_BLUE,
    "active_color_primary_light_v2": BLUE,
    "active_color_primary_dark_v2": NIGHT_BLUE,
    "icon_color_active_light": BLUE,
    "icon_color_active_light_v2": BLUE,
    "icon_color_active_dark": NIGHT_BLUE,
    "icon_color_active_dark_v2": NIGHT_BLUE,
    "active_color_secondary_light": SELECTED,
    "active_color_secondary_dark": NIGHT_SELECTED,
    "active_buttons_and_links_bg_pressed_light": BLUE_PRESSED,
    "active_buttons_and_links_bg_pressed_dark": NIGHT_BLUE_PRESSED,
    "active_buttons_and_links_trans_light": "#661278d8",
    "active_buttons_and_links_trans_dark": "#664da3ff",
    "switch_button_active_light": "#1a1278d8",
    "switch_button_active_dark": "#1a4da3ff",
    "switch_button_active_stroke_dark": "#804da3ff",
    "material_switch_outline_dark": NIGHT_BLUE_PRESSED,
    # Buttons: primary blue, secondary outlined on white
    "btn_bg_primary_light": BLUE,
    "btn_bg_primary_dark": NIGHT_BLUE,
    "btn_text_on_primary_light": "#ffffff",
    "btn_text_on_primary_dark": "#0a1630",
    "btn_bg_secondary_light": FIELD,
    "btn_bg_secondary_dark": NIGHT_FIELD,
    "btn_text_on_secondary_light": BLUE,
    "btn_text_on_secondary_dark": NIGHT_BLUE,
    "btn_icon_on_secondary_light": BLUE,
    "btn_icon_on_secondary_dark": NIGHT_BLUE,
    # Map buttons and widgets: white rounded squares, navy at night
    "map_button_background_color_light": SHEET,
    "map_button_background_color_dark": NIGHT_SHEET,
    "map_button_icon_color_light": INK,
    "map_button_icon_color_dark": NIGHT_INK,
    "widget_background_color_light": SHEET,
    "widget_background_color_dark": NIGHT_SHEET,
    "map_widget_icon_color": BLUE,
    # OsmAnd orange left in UI chrome (data colours - charts, markers, profile
    # colours, speed tolerance - keep their meaning and stay)
    "banner_downloads_remaining_light": BLUE,
    "banner_downloads_remaining_dark": NIGHT_BLUE,
    "coordinate_input_edit_text_activated": BLUE,
    "ctx_menu_bottom_buttons_text_color_dark": NIGHT_BLUE,
    "ctx_menu_controller_button_bg_color_dark_p": NIGHT_BLUE_PRESSED,
    "ctx_menu_controller_button_outline_color_dark_p": NIGHT_BLUE,
    "ctx_menu_controller_button_text_color_dark_n": NIGHT_BLUE,
    "download_pressed_light": "#8c1278d8",
    "fab_color": BLUE,
    "fab_color_pressed": BLUE_PRESSED,
    "multi_selection_menu_close_btn_dark": NIGHT_BLUE,
    "region_backuped": "#554da3ff",
    "route_info_go_btn_bg_dark": NIGHT_BLUE_PRESSED,
    "route_info_go_btn_bg_dark_p": NIGHT_BLUE,
    "route_info_go_btn_inking_dark": NIGHT_BLUE,
    "tool_bar_orange_color_light": "#b41278d8",
}

# Softer, rounder shapes: the design's cards and buttons sit around 12-16dp.
DIMENS = {
    "corner_radius": "12.0dp",
    "radius_small": "8.0dp",
    "radius_normal": "12.0dp",
    "radius_large": "16.0dp",
    "radius_double_large": "24.0dp",
    "cardview_default_radius": "16.0dp",
    "dlg_button_rect_rad": "12.0dp",
    "map_button_rect_rad": "12.0dp",
}
# Literal corners in shape drawables, small to round.
LITERAL_CORNERS = {"2.0dp": "8.0dp", "3.0dp": "10.0dp", "4.0dp": "12.0dp", "5.0dp": "12.0dp", "6.0dp": "14.0dp"}
MAP_BUTTON_RADIUS_DP = 12

BASE_THEMES = ("OsmandDarkTheme", "OsmandLightTheme", "OsmandMaterialDarkTheme", "OsmandMaterialLightTheme")


def patch_colors(root: Path):
    f = root / "res/values/colors.xml"
    text = f.read_text(encoding="utf-8")
    for name, value in COLORS.items():
        text, n = re.subn(rf'(<color name="{name}">)[^<]*(</color>)', rf"\g<1>{value}\g<2>", text)
        if n != 1:
            fail(f"colour {name}: found {n}")
    f.write_text(text, encoding="utf-8")


def patch_shapes(root: Path):
    f = root / "res/values/dimens.xml"
    text = f.read_text(encoding="utf-8")
    for name, value in DIMENS.items():
        text, n = re.subn(rf'(<dimen name="{name}">)[^<]*(</dimen>)', rf"\g<1>{value}\g<2>", text)
        if n != 1:
            fail(f"dimen {name}: found {n}")
    f.write_text(text, encoding="utf-8")
    changed = 0
    for d in (root / "res").glob("drawable*/*.xml"):
        text = d.read_text(encoding="utf-8")
        if "<corners" not in text:
            continue
        new = re.sub(r'(<corners[^>]*android:radius=")([\d.]+dp)(")',
                     lambda m: m.group(1) + LITERAL_CORNERS.get(m.group(2), m.group(2)) + m.group(3), text)
        if new != text:
            d.write_text(new, encoding="utf-8")
            changed += 1
    if changed < 50:
        fail(f"only {changed} shape drawables rounded")


def patch_pins(root: Path):
    """The orange context-menu pin, in the design's blue."""
    found = 0
    for f in (root / "res").glob("drawable-*/map_pin_context_menu.png"):
        im = Image.open(f).convert("RGBA")
        px = im.load()
        for y in range(im.height):
            for x in range(im.width):
                r, g, b, a = px[x, y]
                if a and r > g > b and r - b > 60:      # orange family -> blue, same lightness
                    lum = (r * 0.3 + g * 0.59 + b * 0.11) / 255
                    k = lum / 0.62
                    px[x, y] = (min(255, int(0x12 * k)), min(255, int(0x78 * k)), min(255, int(0xd8 * k)), a)
        im.save(f)
        found += 1
    if not found:
        fail("map_pin_context_menu.png not found")


FONT_FAMILY = """<?xml version="1.0" encoding="utf-8"?>
<font-family xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">
    <font android:font="@font/navos_text_regular" android:fontStyle="normal" android:fontWeight="400"
        app:font="@font/navos_text_regular" app:fontStyle="normal" app:fontWeight="400" />
    <font android:font="@font/navos_text_medium" android:fontStyle="normal" android:fontWeight="500"
        app:font="@font/navos_text_medium" app:fontStyle="normal" app:fontWeight="500" />
    <font android:font="@font/navos_text_semibold" android:fontStyle="normal" android:fontWeight="600"
        app:font="@font/navos_text_semibold" app:fontStyle="normal" app:fontWeight="600" />
    <font android:font="@font/navos_text_bold" android:fontStyle="normal" android:fontWeight="700"
        app:font="@font/navos_text_bold" app:fontStyle="normal" app:fontWeight="700" />
</font-family>
"""

# Typeface FontCache uses when a view has none of its own. Set once at startup.
FONT_CACHE_FIELD = ".field private static navosFont:Landroid/graphics/Typeface;\n"
FONT_CACHE_INIT = """
.method public static init(Landroid/content/Context;)V
    .locals 4

    :try_start_navos
    invoke-virtual {p0}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;
    move-result-object v0
    const-string v1, "navos_text"
    const-string v2, "font"
    invoke-virtual {p0}, Landroid/content/Context;->getPackageName()Ljava/lang/String;
    move-result-object v3
    invoke-virtual {v0, v1, v2, v3}, Landroid/content/res/Resources;->getIdentifier(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I
    move-result v0
    if-eqz v0, :done_navos
    invoke-static {p0, v0}, Landroidx/core/content/res/ResourcesCompat;->getFont(Landroid/content/Context;I)Landroid/graphics/Typeface;
    move-result-object v0
    sput-object v0, Lnet/osmand/plus/utils/FontCache;->navosFont:Landroid/graphics/Typeface;
    :try_end_navos
    .catch Ljava/lang/Exception; {:try_start_navos .. :try_end_navos} :done_navos

    :done_navos
    return-void
.end method
"""


def patch_type(root: Path):
    """Poppins (the design's typeface, OFL) for all UI text. Scripts it doesn't
    cover fall back to the system font glyph by glyph."""
    font_dir = root / "res/font"
    font_dir.mkdir(exist_ok=True)
    for weight in ("Regular", "Medium", "SemiBold", "Bold"):
        shutil.copy(HERE / f"fonts/Poppins-{weight}.ttf", font_dir / f"navos_text_{weight.lower()}.ttf")
    (font_dir / "navos_text.xml").write_text(FONT_FAMILY, encoding="utf-8")

    # XML-styled text: the base themes, and styles that name the system sans.
    styles = root / "res/values/styles.xml"
    text = styles.read_text(encoding="utf-8")
    for theme in BASE_THEMES:
        head = re.search(rf'<style name="{theme}" parent="[^"]*">\n', text)
        if not head:
            fail(f"theme {theme} not found")
        items = ('        <item name="android:fontFamily">@font/navos_text</item>\n'
                 '        <item name="fontFamily">@font/navos_text</item>\n')
        text = text[:head.end()] + items + text[head.end():]
    text, n = re.subn(r'(<item name="(?:android:)?fontFamily">)sans-serif(?:-medium)?(</item>)',
                      r"\g<1>@font/navos_text\g<2>", text)
    if n < 10:
        fail(f"only {n} sans-serif styles found")
    styles.write_text(text, encoding="utf-8")

    # Code-styled text: FontCache builds every medium / normal typeface from a
    # base that's null (the system default) for most views.
    font_cache = smali(root, "net/osmand/plus/utils/FontCache.smali")
    text = font_cache.read_text(encoding="utf-8")
    anchor = "# direct methods\n"
    if anchor not in text:
        fail("FontCache layout changed")
    text = text.replace(anchor, FONT_CACHE_FIELD + "\n" + anchor + FONT_CACHE_INIT, 1)
    font_cache.write_text(text, encoding="utf-8")
    text, start, end = method_body(font_cache, "public static getFont(Landroid/graphics/Typeface;I)")
    body = text[start:end]
    locals_line = "    .locals 6\n"
    if locals_line not in body:
        fail("FontCache.getFont changed")
    body = body.replace(locals_line, locals_line + (
        "\n    if-nez p0, :has_base_navos\n"
        "    sget-object p0, Lnet/osmand/plus/utils/FontCache;->navosFont:Landroid/graphics/Typeface;\n"
        "    :has_base_navos\n"), 1)
    font_cache.write_text(text[:start] + body + text[end:], encoding="utf-8")

    app = smali(root, "net/osmand/plus/OsmandApplication.smali")
    replace_once(app, ".method public onCreate()V\n    .locals 8\n",
                 ".method public onCreate()V\n    .locals 8\n\n"
                 "    invoke-static {p0}, Lnet/osmand/plus/utils/FontCache;->init(Landroid/content/Context;)V\n")


def set_const(path: Path, method: str, old: str, new: str):
    text, start, end = method_body(path, method)
    body = text[start:end]
    if body.count(old) != 1:
        fail(f"{path.name} {method}: expected one {old!r}")
    path.write_text(text[:start] + body.replace(old, new) + text[end:], encoding="utf-8")


def patch_map_buttons(root: Path):
    """The design's map buttons: solid white rounded squares."""
    base = "net/osmand/plus/views/mapwidgets/configure/buttons/"
    state = smali(root, base + "MapButtonState.smali")
    set_const(state, "public getDefaultOpacity()F",
              "const/high16 v0, 0x3f000000    # 0.5f", "const/high16 v0, 0x3f800000    # 1.0f")
    radius = f"const/16 v0, {hex(MAP_BUTTON_RADIUS_DP)}"
    set_const(state, "public getDefaultCornerRadius()I", "const/16 v0, 0x24", radius)
    for name in ("DrawerMenuButtonState", "NavigationMenuButtonState"):
        set_const(smali(root, base + f"{name}.smali"), "public getDefaultCornerRadius()I",
                  "const/4 v0, 0x2", radius)


def main():
    if len(sys.argv) != 2:
        fail("usage: restyle.py DECODED_DIR")
    root = Path(sys.argv[1])
    patch_colors(root)
    patch_shapes(root)
    patch_type(root)
    patch_map_buttons(root)
    patch_pins(root)
    print(f"restyle: {len(COLORS)} colour tokens, rounded shapes, Poppins UI type, rounded-square map buttons")


if __name__ == "__main__":
    main()
