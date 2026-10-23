"""NavOS artwork for the rebranded OsmAnd build.

Everything is derived from the NavOS art already in this repo, so the rebrand
follows the app's icon whenever that changes:

  app/src/main/res/drawable-nodpi/ic_splash_logo.png   white glyph, transparent
  app/src/main/res/mipmap-*/ic_launcher_round.png      round launcher icon
  app/src/main/res/font/saira_semicondensed_bold.ttf   wordmark font

The glyph is also traced into a vector path for OsmAnd's vector logos (the
themed-icon monochrome layer and the 24dp in-app / notification logo).
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont
from skimage import measure

REPO = Path(__file__).resolve().parents[2]
RES = REPO / "app/src/main/res"
GLYPH_PNG = RES / "drawable-nodpi/ic_splash_logo.png"
WORDMARK_FONT = RES / "font/saira_semicondensed_bold.ttf"

INK = (0, 0, 0, 255)          # icon background (matches the NavOS launcher)
NAVY = (20, 40, 75, 255)      # navos_navy, for the wordmark on light screens

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}

# Share of the canvas the glyph's width takes, tuned to match NavOS's own
# launcher icon (glyph is ~69% of the visible 72dp circle on a 108dp canvas).
ADAPTIVE_GLYPH_SHARE = 0.46
SPLASH_GLYPH_SHARE = 0.55


def glyph() -> Image.Image:
    """The white NavOS glyph, cropped to its visible pixels."""
    im = Image.open(GLYPH_PNG).convert("RGBA")
    return im.crop(im.getchannel("A").getbbox())


def _centered(canvas_px: int, share: float, background=(0, 0, 0, 0)) -> Image.Image:
    g = glyph()
    w = round(canvas_px * share)
    h = round(g.height * w / g.width)
    g = g.resize((w, h), Image.LANCZOS)
    out = Image.new("RGBA", (canvas_px, canvas_px), background)
    out.alpha_composite(g, ((canvas_px - w) // 2, (canvas_px - h) // 2))
    return out


def adaptive_foreground(px: int) -> Image.Image:
    return _centered(px, ADAPTIVE_GLYPH_SHARE)


def adaptive_background(px: int) -> Image.Image:
    return Image.new("RGB", (px, px), INK[:3])


def splash_logo(px: int) -> Image.Image:
    return _centered(px, SPLASH_GLYPH_SHARE)


def round_icon(px: int) -> Image.Image:
    """NavOS's round launcher icon at [px], from the closest larger source."""
    src = RES / "mipmap-xxxhdpi/ic_launcher_round.png"
    return Image.open(src).convert("RGBA").resize((px, px), Image.LANCZOS)


def wordmark(width: int, height: int) -> Image.Image:
    """Glyph + "NavOS" in navy, replacing OsmAnd's orange wordmark."""
    out = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    mark_h = round(height * 0.8)
    disc = round_icon(mark_h)
    out.alpha_composite(disc, (0, (height - mark_h) // 2))
    font = ImageFont.truetype(str(WORDMARK_FONT), round(height * 0.62))
    draw = ImageDraw.Draw(out)
    x = mark_h + round(height * 0.12)
    box = draw.textbbox((0, 0), "NavOS", font=font)
    y = (height - (box[3] - box[1])) // 2 - box[1]
    draw.text((x, y), "NavOS", font=font, fill=NAVY)
    return out


def glyph_path(viewport: float, share: float) -> str:
    """The glyph traced to an even-odd vector path, centred in [viewport]."""
    alpha = np.asarray(glyph().getchannel("A"), dtype=float) / 255.0
    alpha = np.pad(alpha, 2)
    scale = viewport * share / (alpha.shape[1] - 4)
    off_x = (viewport - (alpha.shape[1] - 4) * scale) / 2 - 2 * scale
    off_y = (viewport - (alpha.shape[0] - 4) * scale) / 2 - 2 * scale
    parts = []
    for contour in measure.find_contours(alpha, 0.5):
        poly = measure.approximate_polygon(contour, tolerance=1.0)  # source pixels
        if len(poly) < 3:
            continue
        pts = [f"{off_x + c * scale:.2f},{off_y + r * scale:.2f}" for r, c in poly[:-1]]
        parts.append("M" + "L".join(pts) + "Z")
    return "".join(parts)


def vector_xml(size_dp: int, viewport: float, share: float, color: str) -> str:
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f'<vector xmlns:android="http://schemas.android.com/apk/res/android"'
        f' android:width="{size_dp}dp" android:height="{size_dp}dp"'
        f' android:viewportWidth="{viewport:g}" android:viewportHeight="{viewport:g}">\n'
        f'    <path android:fillColor="{color}" android:fillType="evenOdd"\n'
        f'        android:pathData="{glyph_path(viewport, share)}" />\n'
        "</vector>\n"
    )
