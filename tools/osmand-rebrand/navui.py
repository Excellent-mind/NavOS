"""Hooks NavOS's own UI code (java/src, compiled into the APK by rebrand.sh)
into OsmAnd, in place.

Usage: python3 navui.py DECODED_DIR

- Before anything is hooked, every method and field the NavOS code uses is
  looked up in the app's real classes. OsmAnd's build strips unused library
  code (BottomSheetDialog(Context) is gone, for one), so code that compiles
  against the published API can still crash; this stops the build instead.
- The main menu opens as NavMenuSheet, a bottom sheet, instead of the side
  drawer; the old drawer stays locked closed, so an edge swipe can't open it.
"""
import re
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


PLATFORM = ("android/", "java/", "javax/", "dalvik/", "org/json/", "org/w3c/", "org/xml/")
MEMBER = re.compile(r"(?:invoke-[\w/-]+ \{[^}]*\}|[is](?:get|put)[\w-]* [vp]\d+(?:, [vp]\d+)?), L([\w/$]+);->([^\s(:]+)([(:]\S*)")


class Classes:
    """The decoded app's classes: methods, fields, superclass, interfaces."""

    def __init__(self, root: Path):
        self.root = root
        self.cache = {}

    def get(self, name: str):
        if name not in self.cache:
            hits = list(self.root.glob(f"smali*/{name}.smali"))
            if not hits:
                self.cache[name] = None
            else:
                text = hits[0].read_text(encoding="utf-8")
                members = set(re.findall(r"^\.method [^\n]*?(\S+\(\S*)$", text, re.M))
                members |= {n + ":" + t for n, t in re.findall(r"^\.field [^\n]*?(\S+):(\S+?)(?: =.*)?$", text, re.M)}
                parents = re.findall(r"^\.(?:super|implements) L([\w/$]+);", text, re.M)
                self.cache[name] = (members, parents)
        return self.cache[name]

    def resolves(self, cls: str, member: str) -> bool:
        if cls.startswith(PLATFORM):
            return True  # the Android framework: always there
        info = self.get(cls)
        if info is None:
            return False
        members, parents = info
        if member in members:
            return True
        # Constructors aren't inherited; methods and fields resolve up the chain.
        return not member.startswith("<init>(") and any(self.resolves(p, member) for p in parents)


def check_links(root: Path, ours: list):
    classes = Classes(root)
    missing, checked = set(), 0
    for f in ours:
        for cls, name, rest in MEMBER.findall(f.read_text(encoding="utf-8")):
            if cls.startswith("com/excellentmindblow/"):
                continue
            checked += 1
            if not classes.resolves(cls, name + rest):
                missing.add(f"{cls}->{name}{rest}")
    if checked < 20:
        fail(f"link check only saw {checked} references; its pattern no longer matches baksmali output")
    if missing:
        fail("NavOS code calls members the app doesn't have:\n  " + "\n  ".join(sorted(missing)))
    return checked


def main():
    if len(sys.argv) != 2:
        fail("usage: navui.py DECODED_DIR")
    root = Path(sys.argv[1])
    ours = list(root.glob("smali*/com/excellentmindblow/**/*.smali"))
    if not any(f.name == "NavMenuSheet.smali" for f in ours):
        fail("NavMenuSheet isn't in the decoded tree; rebrand.sh compiles it first")
    checked = check_links(root, ours)
    activity = smali(root, "net/osmand/plus/activities/MapActivity.smali")

    text, start, end = method_body(activity, "public openDrawer()V")
    activity.write_text(text[:start] + OPEN_MENU + text[end:], encoding="utf-8")

    text, start, end = method_body(activity, "public enableDrawer()V")
    body = text[start:end]
    if body.count(LOCK_UNLOCK) != 1:
        fail("MapActivity.enableDrawer changed")
    activity.write_text(text[:start] + body.replace(LOCK_UNLOCK, LOCK_CLOSED) + text[end:], encoding="utf-8")
    print(f"navui: {checked} calls into the app checked; main menu is a bottom sheet")


if __name__ == "__main__":
    main()
