"""Builds the R&R icons (ic_rnr, ic_rnr_badge + preview.html) from Saira Semi Condensed Bold outlines.

Usage: python rnr_icons.py app/src/main/res/font/saira_semicondensed_bold.ttf OUT_DIR  (needs fonttools)
"""
import sys
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.boundsPen import BoundsPen

font_path, out_dir = sys.argv[1], sys.argv[2]
font = TTFont(font_path)
gs = font.getGlyphSet()
cmap = font.getBestCmap()

def text_path(text, x0, x1, base_y, cap_h, track=0.0):
    """Path for [text] fitted into x0..x1, baseline base_y, cap height cap_h (y down)."""
    names = [cmap[ord(c)] for c in text]
    cap = font["OS/2"].sCapHeight or 700
    s = cap_h / cap
    adv = sum(gs[n].width for n in names) * s + track * (len(names) - 1)
    # tight bounds of the whole run, for exact horizontal fit
    x = 0.0
    lo, hi = None, None
    for n in names:
        bp = BoundsPen(gs)
        gs[n].draw(bp)
        if bp.bounds:
            a, _, b, _ = bp.bounds
            lo = x + a * s if lo is None else lo
            hi = x + b * s
        x += gs[n].width * s + track
    width = hi - lo
    scale_fit = min(1.0, (x1 - x0) / width)
    s2 = s * scale_fit
    start = (x0 + x1) / 2 - (width * scale_fit) / 2 - lo * scale_fit
    pen = SVGPathPen(gs, ntos=lambda v: ("%.2f" % v).rstrip("0").rstrip("."))
    x = start
    for n in names:
        tp = TransformPen(pen, (s2, 0, 0, -s2, x, base_y))
        gs[n].draw(tp)
        x += (gs[n].width * s + track) * scale_fit
    return pen.getCommands()

GREEN = "#00703C"
canopy = [
    "M12 3.4L4.4 9.2H8.7L11.3 4.1Z",          # left panel
    "M12 3.4L10.1 9.2H13.9Z",                 # middle panel
    "M12 3.4L19.6 9.2H15.3L12.7 4.1Z",        # right panel
    "M4.4 9.2H19.6V10.6H4.4Z",                # eave
]
letters = text_path("R&R", 3.6, 20.4, 17.8, 7.0, track=0.35)
bar = "M4.4 19.3H19.6V20.6H4.4Z"

def vector(paths, comment):
    out = ['<?xml version="1.0" encoding="utf-8"?>', f"<!-- {comment} -->",
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    android:width="24dp"', '    android:height="24dp"',
           '    android:viewportWidth="24"', '    android:viewportHeight="24">']
    for p in paths:
        attrs = "".join(f'\n        android:{k}="{v}"' for k, v in p.items())
        out.append(f"    <path{attrs} />")
    out.append("</vector>")
    return "\n".join(out) + "\n"

# 1. Badge: the Malaysian R&R road sign, simplified for 24-40 dp.
badge = [{"pathData": "M4.5 0.5H19.5A4 4 0 0 1 23.5 4.5V19.5A4 4 0 0 1 19.5 23.5H4.5A4 4 0 0 1 0.5 19.5V4.5A4 4 0 0 1 4.5 0.5Z", "fillColor": GREEN}]
badge += [{"pathData": p, "fillColor": "#FFFFFF"} for p in canopy + [letters, bar]]
open(f"{out_dir}/ic_rnr_badge.xml", "w").write(vector(badge, "R&R: Malaysian rest-and-service sign (green canopy badge). Letters from Saira Semi Condensed Bold."))

# 2. Line icon: canopy outline + letters, one colour (tinted in code), Lucide weight.
line_canopy = "M3.5 9.5L12 3.5L20.5 9.5H3.5Z"
line_letters = text_path("R&R", 3.2, 20.8, 18.9, 7.2, track=0.35)
line = [
    {"pathData": "M3.5 9.5L12 3.2L20.5 9.5Z", "strokeColor": "@android:color/white", "strokeWidth": "1.6", "strokeLineJoin": "round", "strokeLineCap": "round"},
    {"pathData": "M12 3.2V9.5M12 3.2L8.2 9.5M12 3.2L15.8 9.5", "strokeColor": "@android:color/white", "strokeWidth": "1.2", "strokeLineCap": "round"},
    {"pathData": line_letters, "fillColor": "@android:color/white"},
    {"pathData": "M3.5 21.3H20.5", "strokeColor": "@android:color/white", "strokeWidth": "1.6", "strokeLineCap": "round"},
]
open(f"{out_dir}/ic_rnr.xml", "w").write(vector(line, "R&R, Lucide style (24 grid). Canopy from the Malaysian R&R sign; letters from Saira Semi Condensed Bold."))

# SVG preview of both, several sizes, day and night.
def svg(paths, size, fg):
    body = []
    for p in paths:
        d = p["pathData"]
        fill = p.get("fillColor"); stroke = p.get("strokeColor")
        fill = fg if fill == "@android:color/white" else (fill or "none")
        stroke = fg if stroke == "@android:color/white" else (stroke or "none")
        body.append(f'<path d="{d}" fill="{fill}" stroke="{stroke}" stroke-width="{p.get("strokeWidth", 0)}" stroke-linecap="round" stroke-linejoin="round"/>')
    return f'<svg width="{size}" height="{size}" viewBox="0 0 24 24">{"".join(body)}</svg>'
rows = []
for bg, fg in (("#FFFFFF", "#0A0A0A"), ("#0B0B0B", "#FFFFFF")):
    cells = "".join(f'<div style="display:flex;flex-direction:column;align-items:center;gap:6px">{svg(badge, s, fg)}{svg(line, s, fg)}<span style="font:12px sans-serif;color:{fg}">{s}px</span></div>' for s in (20, 28, 40, 96))
    rows.append(f'<div style="background:{bg};padding:24px;display:flex;gap:28px;align-items:flex-end">{cells}</div>')
open(f"{out_dir}/preview.html", "w").write("<html><body style='margin:0'>" + "".join(rows) + "</body></html>")
print("ok")
