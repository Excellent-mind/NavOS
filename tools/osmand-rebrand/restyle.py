"""Restyles an apktool-decoded (and rebranded) OsmAnd APK in the NavOS
"Signal" design language, in place.

Usage: python3 restyle.py DECODED_DIR

Everything goes through OsmAnd's shared tokens - colour resources, base
themes, the font helper, map button defaults - so every screen follows
without per-screen edits. Values come from the NavOS app
(app/src/main/res/values/colors.xml, values-night/colors.xml, font/).
"""
import re
import shutil
import sys
from pathlib import Path

from rebrand import fail, smali, method_body, replace_once

REPO = Path(__file__).resolve().parents[2]
NAVOS_RES = REPO / "app/src/main/res"

# Signal tokens (NavOS app), light / dark.
INK, ON_INK = "#0a0a0a", "#ffffff"
NIGHT_INK, NIGHT_ON_INK = "#ffffff", "#0a0a0a"
TEXT_QUIET, NIGHT_TEXT_QUIET = "#5c5c5c", "#a6a6a6"
SHEET, NIGHT_SHEET = "#ffffff", "#0b0b0b"
FIELD, NIGHT_FIELD = "#f2f2f2", "#1c1c1c"
FLOAT, NIGHT_FLOAT = "#ffffff", "#1c1c1c"
OUTLINE, NIGHT_OUTLINE = "#e0e0e0", "#333333"
DIVIDER, NIGHT_DIVIDER = "#ededed", "#1c1c1c"
# Accent: the NavOS brand pair - navy on light, blue at night.
NAVY, BLUE = "#14284b", "#1cb0f6"
NAVY_PRESSED, BLUE_PRESSED = "#0e1d38", "#1599d6"
SELECTED, NIGHT_SELECTED = "#e3f4fe", "#1f3a57"

COLORS = {
    # Surfaces
    "activity_background_color_light": FIELD,
    "activity_background_color_dark": "#000000",
    "list_background_color_light": SHEET,
    "list_background_color_dark": NIGHT_SHEET,
    "card_and_list_background_light": SHEET,
    "card_and_list_background_dark": NIGHT_SHEET,
    # App bar and status bar: Signal ink
    "app_bar_main_light": INK,
    "app_bar_main_dark": NIGHT_SHEET,
    "app_bar_secondary_dark": NIGHT_SHEET,
    "status_bar_main_light": INK,
    "status_bar_main_dark": NIGHT_SHEET,
    "status_bar_secondary_light": FIELD,
    "status_bar_secondary_dark": NIGHT_SHEET,
    "status_bar_dim_light": "#000000",
    # Text
    "text_color_primary_light": INK,
    "text_color_primary_dark": NIGHT_INK,
    "text_color_secondary_light": TEXT_QUIET,
    "text_color_secondary_dark": NIGHT_TEXT_QUIET,
    # Lines
    "divider_color_light": DIVIDER,
    "divider_color_dark": NIGHT_DIVIDER,
    "stroked_buttons_and_links_outline_light": OUTLINE,
    "stroked_buttons_and_links_outline_dark": NIGHT_OUTLINE,
    # Accent
    "active_color_primary_light": NAVY,
    "active_color_primary_dark": BLUE,
    "active_color_primary_light_v2": NAVY,
    "active_color_primary_dark_v2": BLUE,
    "icon_color_active_light": NAVY,
    "icon_color_active_light_v2": NAVY,
    "icon_color_active_dark": BLUE,
    "icon_color_active_dark_v2": BLUE,
    "active_color_secondary_light": SELECTED,
    "active_color_secondary_dark": NIGHT_SELECTED,
    "active_buttons_and_links_bg_pressed_light": NAVY_PRESSED,
    "active_buttons_and_links_bg_pressed_dark": BLUE_PRESSED,
    "active_buttons_and_links_trans_light": "#6614284b",
    "active_buttons_and_links_trans_dark": "#661cb0f6",
    "switch_button_active_light": "#1a14284b",
    "switch_button_active_dark": "#1a1cb0f6",
    "switch_button_active_stroke_dark": "#801cb0f6",
    "material_switch_outline_dark": BLUE_PRESSED,
    # Buttons: primary is Signal ink, secondary is the field grey
    "btn_bg_primary_light": INK,
    "btn_bg_primary_dark": NIGHT_INK,
    "btn_text_on_primary_light": ON_INK,
    "btn_text_on_primary_dark": NIGHT_ON_INK,
    "btn_bg_secondary_light": FIELD,
    "btn_bg_secondary_dark": NIGHT_FIELD,
    "btn_text_on_secondary_light": INK,
    "btn_text_on_secondary_dark": NIGHT_INK,
    "btn_icon_on_secondary_light": INK,
    "btn_icon_on_secondary_dark": NIGHT_INK,
    # Map buttons and widgets: the Signal floating button
    "map_button_background_color_light": FLOAT,
    "map_button_background_color_dark": NIGHT_FLOAT,
    "map_button_icon_color_light": INK,
    "map_button_icon_color_dark": NIGHT_INK,
    "widget_background_color_light": FLOAT,
    "widget_background_color_dark": NIGHT_FLOAT,
    "map_widget_icon_color": BLUE,
    # OsmAnd orange left in UI chrome (data colours - charts, markers, profile
    # colours, speed tolerance - keep their meaning and stay)
    "banner_downloads_remaining_light": NAVY,
    "banner_downloads_remaining_dark": BLUE,
    "coordinate_input_edit_text_activated": BLUE,
    "ctx_menu_bottom_buttons_text_color_dark": BLUE,
    "ctx_menu_controller_button_bg_color_dark_p": BLUE_PRESSED,
    "ctx_menu_controller_button_outline_color_dark_p": BLUE,
    "ctx_menu_controller_button_text_color_dark_n": BLUE,
    "download_pressed_light": "#8c14284b",
    "fab_color": NAVY,
    "fab_color_pressed": NAVY_PRESSED,
    "multi_selection_menu_close_btn_dark": BLUE,
    "region_backuped": "#551cb0f6",
    "route_info_go_btn_bg_dark": BLUE_PRESSED,
    "route_info_go_btn_bg_dark_p": BLUE,
    "route_info_go_btn_inking_dark": BLUE,
    "tool_bar_orange_color_light": "#b414284b",
}

BASE_THEMES = ("OsmandDarkTheme", "OsmandLightTheme", "OsmandMaterialDarkTheme", "OsmandMaterialLightTheme")


def patch_colors(root: Path):
    f = root / "res/values/colors.xml"
    text = f.read_text(encoding="utf-8")
    for name, value in COLORS.items():
        text, n = re.subn(rf'(<color name="{name}">)[^<]*(</color>)', rf"\g<1>{value}\g<2>", text)
        if n != 1:
            fail(f"colour {name}: found {n}")
    f.write_text(text, encoding="utf-8")


FONT_FAMILY = """<?xml version="1.0" encoding="utf-8"?>
<font-family xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">
    <font android:font="@font/navos_text_regular" android:fontStyle="normal" android:fontWeight="400"
        app:font="@font/navos_text_regular" app:fontStyle="normal" app:fontWeight="400" />
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
    """Atkinson Hyperlegible (the NavOS text font) for all UI text."""
    font_dir = root / "res/font"
    font_dir.mkdir(exist_ok=True)
    shutil.copy(NAVOS_RES / "font/atkinson_hyperlegible_regular.ttf", font_dir / "navos_text_regular.ttf")
    shutil.copy(NAVOS_RES / "font/atkinson_hyperlegible_bold.ttf", font_dir / "navos_text_bold.ttf")
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
    """Signal floating buttons: solid, and all round."""
    base = "net/osmand/plus/views/mapwidgets/configure/buttons/"
    set_const(smali(root, base + "MapButtonState.smali"), "public getDefaultOpacity()F",
              "const/high16 v0, 0x3f000000    # 0.5f", "const/high16 v0, 0x3f800000    # 1.0f")
    for name in ("DrawerMenuButtonState", "NavigationMenuButtonState"):
        set_const(smali(root, base + f"{name}.smali"), "public getDefaultCornerRadius()I",
                  "const/4 v0, 0x2", "const/16 v0, 0x24")


def main():
    if len(sys.argv) != 2:
        fail("usage: restyle.py DECODED_DIR")
    root = Path(sys.argv[1])
    patch_colors(root)
    patch_type(root)
    patch_map_buttons(root)
    print(f"restyle: {len(COLORS)} colour tokens, Atkinson Hyperlegible UI type, Signal map buttons")


if __name__ == "__main__":
    main()
