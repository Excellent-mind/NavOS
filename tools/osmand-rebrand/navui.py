"""Hooks NavOS's own UI code (java/src, compiled into the APK by rebrand.sh)
into OsmAnd, in place.

Usage: python3 navui.py DECODED_DIR

- The main menu opens as NavMenuSheet, a bottom sheet, instead of the side
  drawer; the old drawer stays locked closed, so an edge swipe can't open it.
"""
import sys
from pathlib import Path

from rebrand import fail, smali, method_body

SHEET = "Lcom/excellentmindblow/navos/ui/NavMenuSheet;"
ACTIVITY = "Lnet/osmand/plus/activities/MapActivity;"

OPEN_MENU = f""".method public openDrawer()V
    .locals 1

    invoke-virtual {{p0}}, {ACTIVITY}->isDrawerAvailable()Z
    move-result v0
    if-eqz v0, :navos_no_menu
    invoke-static {{p0}}, {SHEET}->show({ACTIVITY})V
    :navos_no_menu
    return-void
"""

LOCK_UNLOCK = "    invoke-virtual {v1, v0}, Landroidx/drawerlayout/widget/DrawerLayout;->setDrawerLockMode(I)V\n"
LOCK_CLOSED = ("    const/4 v0, 0x1\n\n"  # DrawerLayout.LOCK_MODE_LOCKED_CLOSED
               "    invoke-virtual {v1, v0}, Landroidx/drawerlayout/widget/DrawerLayout;->setDrawerLockMode(I)V\n")


def main():
    if len(sys.argv) != 2:
        fail("usage: navui.py DECODED_DIR")
    root = Path(sys.argv[1])
    if not list(root.glob("smali*/com/excellentmindblow/navos/ui/NavMenuSheet.smali")):
        fail("NavMenuSheet isn't in the decoded tree; rebrand.sh compiles it first")
    activity = smali(root, "net/osmand/plus/activities/MapActivity.smali")

    text, start, end = method_body(activity, "public openDrawer()V")
    activity.write_text(text[:start] + OPEN_MENU + text[end:], encoding="utf-8")

    text, start, end = method_body(activity, "public enableDrawer()V")
    body = text[start:end]
    if body.count(LOCK_UNLOCK) != 1:
        fail("MapActivity.enableDrawer changed")
    activity.write_text(text[:start] + body.replace(LOCK_UNLOCK, LOCK_CLOSED) + text[end:], encoding="utf-8")
    print("navui: main menu is a bottom sheet")


if __name__ == "__main__":
    main()
