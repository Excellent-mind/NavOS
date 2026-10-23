"""Recolours the map in the NavOS app design, in place: a cool light-grey
map with white roads, soft blue water and green parks by day, deep navy at
night, and the route in the design's blue.

Usage: python3 mapstyle.py DECODED_DIR

Works on OsmAnd's default style, which ships twice: as a Java resource
(unknown/net/osmand/render/default.render.xml) and Qt-compressed for the
native renderer (assets/OsmAndCore_ResourcesBundle/map/styles/*.qz).
Colours that carry information - route and track colours, subway lines,
hiking / surface / access overlays - keep theirs.
"""
import colorsys
import re
import struct
import sys
import zlib
from pathlib import Path

from rebrand import fail

JAVA_STYLE = "unknown/net/osmand/render/default.render.xml"
CORE_STYLE = "assets/OsmAndCore_ResourcesBundle/map/styles/default.render.xml.qz"

BACKGROUND = {False: "#eef1f5", True: "#0e1a2e"}
ROAD = {False: "#ffffff", True: "#2c3f5e"}
ROAD_CASING = {False: "#d5dbe4", True: "#0a1322"}

# Blocks whose colours mean something; they keep them.
KEEP = re.compile(
    r"^(routeColor|gpxColor|ruler\w*|measureDistanceLine|marker\w*|route|gpx|publicTransportLine"
    r"|\w*WalkingRouteLine|walkingRouteLine|whitewater\w*|viaFerrata\w*|\w*Scale\w*|surface\w*"
    r"|routeInfo_\w*|tracktypeGrade\w*|subway\w*|(white|red|orange|brown|darkyellow|yellow|lightgreen"
    r"|green|lightblue|blue|purple|gray|black)Color|access\w*|toll\w*|piste\w*|lit\w*|iceroad\w*"
    r"|sacScale\w*|surfaceGrade\w*)$"
)
ROAD_FILL = re.compile(r"^(motorway|trunk|primary|secondary|tertiary|residential|service|serviceDriveway"
                       r"|road|pedestrian|busway)Road(LowZoom)?Color$|^(primaryResidential|service)HighwayAreaColor$")
ROAD_SHADOW = re.compile(r"Road\w*ShadowColor$|HighwayAreaShadowColor$")

COLOR = re.compile(r"#(?:[0-9a-fA-F]{8}|[0-9a-fA-F]{6})\b")


def recolor(hexcolor: str, night: bool) -> str:
    h = hexcolor[1:]
    alpha, rgb = (h[:2], h[2:]) if len(h) == 8 else ("", h)
    r, g, b = (int(rgb[i:i + 2], 16) / 255 for i in (0, 2, 4))
    hue, light, sat = colorsys.rgb_to_hls(r, g, b)
    if 0.52 <= hue <= 0.66 and sat > 0.2:                  # water
        hue, sat = 0.585, min(sat, 0.55 if night else 0.62)
        if night:
            light = 0.14 + light * 0.25 if light < 0.5 else light
        elif light > 0.6:
            light = 0.86 + (light - 0.6) * 0.3
    elif 0.17 <= hue <= 0.47 and sat > 0.12:               # parks, woods, grass
        sat *= 0.4 if night else 0.5
        if night:
            light = min(light, 0.22)
        elif light > 0.55:
            light = 0.87 + (light - 0.55) * 0.25
    else:                                                  # everything else: cool grey
        y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        if night:
            hue, sat, light = 0.6, 0.28, min(0.92, y * 0.9 + 0.04)
        else:
            hue, sat, light = 0.6, 0.14, (0.88 + (y - 0.6) * 0.2) if y > 0.6 else y
    r, g, b = colorsys.hls_to_rgb(hue, max(0.0, min(1.0, light)), max(0.0, min(1.0, sat)))
    return "#" + alpha + "".join(f"{round(c * 255):02x}" for c in (r, g, b))


TAG = re.compile(r"<(/?)([\w:]+)([^>]*?)(/?)>|<!--.*?-->", re.S)


def restyle(xml: str) -> tuple[str, int]:
    """Walks the tags, tracking which renderingAttribute and night/day branch
    each colour sits in."""
    out, pos, changed = [], 0, 0
    stack = []  # (tag, night, block)
    for m in TAG.finditer(xml):
        out.append(xml[pos:m.start()])
        pos = m.end()
        if m.group(0).startswith("<!--"):
            out.append(m.group(0))
            continue
        closing, tag, attrs, selfclose = m.groups()
        if closing:
            while stack and stack.pop()[0] != tag:
                pass
            out.append(m.group(0))
            continue
        parent_night, block = (stack[-1][1], stack[-1][2]) if stack else (False, None)
        night = parent_night
        if 'nightMode="true"' in attrs:
            night = True
        elif 'nightMode="false"' in attrs:
            night = False
        name = re.search(r'\bname="([^"]*)"', attrs)
        if tag == "renderingAttribute" and name:
            block = name.group(1)
        if tag == "renderingConstant" and name:
            cname = name.group(1)
            const_night = bool(re.search(r"(?i)night", cname))
            if not KEEP.match(cname):
                attrs, n = COLOR.subn(lambda c: recolor(c.group(0), const_night), attrs)
                changed += n
        elif block == "defaultColor":
            attrs = COLOR.sub(lambda c: ("#00" + BACKGROUND[night][1:]) if c.group(0).startswith("#00")
                              else BACKGROUND[night], attrs)
        elif block == "route" and tag == "case":
            attrs = re.sub(r'\bcolor="[^"]*"', 'color="#e61278d8"', attrs)
            attrs = re.sub(r'\bcolor_2="[^"]*"', 'color_2="#ffffff"', attrs)
        elif block == "route" and tag == "apply_if" and night:
            attrs = re.sub(r'\bcolor="[^"]*"', 'color="#4da3ff"', attrs)
            attrs = re.sub(r'\bcolor_2="[^"]*"', 'color_2="#0e1a2e"', attrs)
            attrs = re.sub(r'\bcolor_0="[^"]*"', 'color_0="#cc0a2a52"', attrs)
        elif block and KEEP.match(block):
            pass
        elif block and ROAD_FILL.match(block):
            attrs = re.sub(r'(attrColorValue=")#[0-9a-fA-F]{6,8}', rf"\g<1>{ROAD[night]}", attrs)
        elif block and ROAD_SHADOW.search(block):
            attrs = re.sub(r'(attrColorValue=")#[0-9a-fA-F]{6,8}', rf"\g<1>{ROAD_CASING[night]}", attrs)
        else:
            attrs, n = COLOR.subn(lambda c: recolor(c.group(0), night), attrs)
            changed += n
        out.append(f"<{tag}{attrs}{selfclose}>")
        if not selfclose:
            stack.append((tag, night, block))
    out.append(xml[pos:])
    return "".join(out), changed


def main():
    if len(sys.argv) != 2:
        fail("usage: mapstyle.py DECODED_DIR")
    root = Path(sys.argv[1])
    java = root / JAVA_STYLE
    core = root / CORE_STYLE
    xml = java.read_text(encoding="utf-8")
    raw = core.read_bytes()
    if zlib.decompress(raw[4:]) != xml.encode("utf-8"):
        fail("the two copies of default.render.xml differ; update mapstyle.py")
    new, changed = restyle(xml)
    if changed < 800:
        fail(f"only {changed} map colours changed; style file changed shape")
    java.write_text(new, encoding="utf-8")
    data = new.encode("utf-8")
    core.write_bytes(struct.pack(">I", len(data)) + zlib.compress(data, 9))
    print(f"mapstyle: {changed} map colours recoloured, route in brand blue")


if __name__ == "__main__":
    main()
