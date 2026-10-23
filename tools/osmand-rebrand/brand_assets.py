"""NavOS artwork for the rebranded OsmAnd build.

Everything is derived from the NavOS art already in this repo, so the rebrand
follows the app's icon whenever that changes:

  app/src/main/res/drawable-nodpi/ic_splash_logo.png   white glyph, transparent
  app/src/main/res/mipmap-*/ic_launcher_round.png      round launcher icon
  tools/osmand-rebrand/fonts/Poppins-SemiBold.ttf      wordmark font (OFL)

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
WORDMARK_FONT = Path(__file__).resolve().parent / "fonts/Poppins-SemiBold.ttf"

INK = (0, 0, 0, 255)          # icon background (matches the NavOS launcher)
NAVY = (27, 36, 51, 255)      # design ink, for the wordmark on light screens

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


def text_path(text: str, viewport_w: float, viewport_h: float, height_share: float = 0.62) -> str:
    """[text] in the wordmark font as one vector path, left-aligned and
    vertically centred in the viewport, capital height [height_share] of it."""
    from fontTools.pens.svgPathPen import SVGPathPen
    from fontTools.pens.transformPen import TransformPen
    from fontTools.ttLib import TTFont

    font = TTFont(str(WORDMARK_FONT))
    glyphs, cmap = font.getGlyphSet(), font.getBestCmap()
    cap = font["OS/2"].sCapHeight or 700
    names = [cmap[ord(c)] for c in text]
    advance = sum(font["hmtx"][n][0] for n in names)
    scale = min(viewport_h * height_share / cap, viewport_w * 0.98 / advance)
    baseline = (viewport_h + cap * scale) / 2
    pen = SVGPathPen(glyphs, ntos=lambda v: f"{v:.2f}")
    x = 0.0
    for n in names:
        # Font units are y-up; vector drawables are y-down.
        glyphs[n].draw(TransformPen(pen, (scale, 0, 0, -scale, x * scale, baseline)))
        x += font["hmtx"][n][0]
    return pen.getCommands()

BRAND_BLUE = "#1278d8"  # primary blue of the NavOS app design
