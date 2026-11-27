#!/usr/bin/env python3
"""Builds the animated README artwork in .github/readme/ (hero-dark.svg, hero-light.svg, divider-*.svg, features-*.svg).

All text is converted to outlines with fontTools, so the images look the same on every system and GitHub needs no
web fonts. Fonts: Unbounded and Manrope from app/src/main/res/font (SIL OFL 1.1, see licenses/), and 夢色 from
Shippori Mincho (SIL OFL 1.1), passed with --kanji-font. Animations are SMIL and CSS inside the SVG; GitHub renders
both in <img>. Run: python3 .github/readme/src/make_art.py --kanji-font <shippori-mincho.woff2|ttf>
"""
from __future__ import annotations

import argparse
import base64
import io
from pathlib import Path

from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / ".github" / "readme"
FONTS = ROOT / "app" / "src" / "main" / "res" / "font"

GOLD, GOLD_HI, VIOLET, ROSE, CYAN = "#F5C46B", "#FFE3A3", "#A78BFA", "#F472B6", "#5EEAD4"


class Face:
    def __init__(self, path: Path):
        self.font = TTFont(str(path))
        self.cmap = self.font.getBestCmap()
        self.gs = self.font.getGlyphSet()
        self.upm = self.font["head"].unitsPerEm
        self.hmtx = self.font["hmtx"]

    def width(self, text: str, size: float, track: float = 0) -> float:
        s = size / self.upm
        w = 0.0
        for ch in text:
            g = self.cmap.get(ord(ch))
            if g is None:
                g = self.cmap.get(ord(" "))
            w += self.hmtx[g][0] * s + track
        return w - track if text else 0

    def path(self, text: str, x: float, y: float, size: float, track: float = 0, anchor: str = "start") -> str:
        """SVG path data for text with its baseline at y."""
        if anchor == "middle":
            x -= self.width(text, size, track) / 2
        elif anchor == "end":
            x -= self.width(text, size, track)
        s = size / self.upm
        pen = SVGPathPen(self.gs, ntos=lambda v: f"{v:.1f}".rstrip("0").rstrip("."))
        cx = x
        for ch in text:
            g = self.cmap.get(ord(ch)) or self.cmap.get(ord(" "))
            tp = TransformPen(pen, (s, 0, 0, -s, cx, y))
            self.gs[g].draw(tp)
            cx += self.hmtx[g][0] * s + track
        return pen.getCommands()


def logo_data(size: int = 168) -> str:
    im = Image.open(OUT / "logo.png").convert("RGBA").resize((size, size), Image.LANCZOS)
    # Round the square icon so it sits well on both themes.
    mask = Image.new("L", (size * 4, size * 4), 0)
    from PIL import ImageDraw

    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size * 4 - 1, size * 4 - 1), radius=size * 4 // 4.4, fill=255)
    im.putalpha(mask.resize((size, size), Image.LANCZOS))
    buf = io.BytesIO()
    im.save(buf, "PNG", optimize=True)
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()


def hero(dark: bool, kanji: Face, display: Face, body: Face, bold: Face) -> str:
    W, H = 1280, 460
    bg = "#0A0A0F" if dark else "#FBF7EE"
    ink = "#F4EFE6" if dark else "#17151C"
    sub = "#B9B2C7" if dark else "#4A4458"
    faint = 0.07 if dark else 0.06
    blob_op = 0.55 if dark else 0.32
    gold = GOLD if dark else "#C98A12"
    chip_bg = "rgba(255,255,255,0.06)" if dark else "rgba(23,21,28,0.05)"
    chip_line = "rgba(245,196,107,0.45)" if dark else "rgba(201,138,18,0.45)"

    title = display.path("Yumeiro", 0, 0, 92, track=1)
    tw = display.width("Yumeiro", 92, 1)
    k_big = kanji.path("夢色", W - 40, 400, 360, anchor="end")
    k_small = kanji.path("夢色", 0, 0, 34)

    rot = [
        "Anime from CloudStream, Aniyomi and your own sources",
        "Music, synced lyrics, equalizer and YouTube Music",
        "Live TV and IPTV playlists, made for the remote",
        "One app for phones, tablets, Android TV and Fire TV",
    ]
    rot_paths = [body.path(t, 0, 0, 26) for t in rot]
    n = len(rot)
    per = 3.2
    total = per * n

    def rot_anim(i: int) -> str:
        a = i / n
        b = (i + 0.08) / n
        c = (i + 0.92) / n
        d = (i + 1) / n
        kt = sorted({0.0, a, b, c, d, 1.0})
        vals = []
        for k in kt:
            vals.append("1" if b <= k <= c else "0")
        return (
            f'<animate attributeName="opacity" dur="{total}s" repeatCount="indefinite" '
            f'keyTimes="{";".join(f"{k:.4f}" for k in kt)}" values="{";".join(vals)}"/>'
        )

    chips = ["Music", "Video", "Anime", "Manga", "Novels", "Live TV", "Android TV"]
    chip_svg = []
    cx = 0.0
    for c in chips:
        w = bold.width(c, 17) + 34
        chip_svg.append(
            f'<g transform="translate({cx:.1f},0)"><rect width="{w:.1f}" height="36" rx="18" fill="{chip_bg}" '
            f'stroke="{chip_line}"/><path transform="translate(17,24)" d="{bold.path(c, 0, 0, 17)}" fill="{ink}"/></g>'
        )
        cx += w + 10

    stars = []
    import random

    rnd = random.Random(7)
    for i in range(46):
        x, y = rnd.uniform(0, W), rnd.uniform(0, H)
        r = rnd.uniform(0.6, 1.8)
        d = rnd.uniform(2.5, 6)
        b = rnd.uniform(0, 4)
        col = rnd.choice([GOLD_HI, "#FFFFFF", VIOLET]) if dark else rnd.choice([gold, VIOLET, ROSE])
        stars.append(
            f'<circle cx="{x:.0f}" cy="{y:.0f}" r="{r:.1f}" fill="{col}" opacity="0">'
            f'<animate attributeName="opacity" values="0;{0.9 if dark else 0.5};0" dur="{d:.1f}s" begin="{b:.1f}s" repeatCount="indefinite"/></circle>'
        )

    logo = logo_data()
    return f'''<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="{W}" height="{H}" viewBox="0 0 {W} {H}" role="img" aria-label="Yumeiro: one app for music, video, anime, manga, novels and live TV on Android phones and TVs">
  <title>Yumeiro 夢色</title>
  <defs>
    <clipPath id="card"><rect width="{W}" height="{H}" rx="28"/></clipPath>
    <filter id="blur" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="70"/></filter>
    <filter id="glow" x="-20%" y="-50%" width="140%" height="200%"><feGaussianBlur stdDeviation="10" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter>
    <linearGradient id="shine" x1="0" y1="0" x2="1" y2="0" gradientUnits="objectBoundingBox">
      <stop offset="0" stop-color="{gold}"/>
      <stop offset="0.35" stop-color="{GOLD_HI if dark else '#E7A93A'}"/>
      <stop offset="0.5" stop-color="{'#FFFFFF' if dark else '#FFF4D6'}"/>
      <stop offset="0.65" stop-color="{GOLD_HI if dark else '#E7A93A'}"/>
      <stop offset="1" stop-color="{gold}"/>
      <animate attributeName="x1" values="-1;1" dur="5s" repeatCount="indefinite"/>
      <animate attributeName="x2" values="0;2" dur="5s" repeatCount="indefinite"/>
    </linearGradient>
    <linearGradient id="kanjiFill" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="{gold}" stop-opacity="{faint * 2.2}"/>
      <stop offset="1" stop-color="{VIOLET}" stop-opacity="{faint}"/>
    </linearGradient>
    <linearGradient id="edge" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="{gold}" stop-opacity="0.8"/>
      <stop offset="0.5" stop-color="{VIOLET}" stop-opacity="0.35"/>
      <stop offset="1" stop-color="{ROSE}" stop-opacity="0.7"/>
    </linearGradient>
  </defs>
  <g clip-path="url(#card)">
    <rect width="{W}" height="{H}" fill="{bg}"/>
    <g filter="url(#blur)" opacity="{blob_op}">
      <circle cx="260" cy="120" r="210" fill="{gold}">
        <animateTransform attributeName="transform" type="translate" values="0 0;120 60;-40 90;0 0" dur="18s" repeatCount="indefinite"/>
      </circle>
      <circle cx="980" cy="330" r="240" fill="{VIOLET}">
        <animateTransform attributeName="transform" type="translate" values="0 0;-140 -50;60 -90;0 0" dur="22s" repeatCount="indefinite"/>
      </circle>
      <circle cx="720" cy="40" r="170" fill="{ROSE}" opacity="0.75">
        <animateTransform attributeName="transform" type="translate" values="0 0;80 120;-120 40;0 0" dur="26s" repeatCount="indefinite"/>
      </circle>
      <circle cx="120" cy="440" r="160" fill="{CYAN}" opacity="0.45">
        <animateTransform attributeName="transform" type="translate" values="0 0;160 -40;40 -100;0 0" dur="30s" repeatCount="indefinite"/>
      </circle>
    </g>
    <path d="{k_big}" fill="url(#kanjiFill)">
      <animateTransform attributeName="transform" type="translate" values="0 0;0 -10;0 0" dur="9s" repeatCount="indefinite"/>
    </path>
    {''.join(stars)}
    <g transform="translate(84,92)">
      <image href="{logo}" xlink:href="{logo}" width="132" height="132">
        <animateTransform attributeName="transform" type="translate" values="0 0;0 -6;0 0" dur="6s" repeatCount="indefinite"/>
      </image>
    </g>
    <g transform="translate(250,186)">
      <path d="{title}" fill="url(#shine)"{' filter="url(#glow)"' if dark else ''}/>
      <path transform="translate({tw + 24:.0f},-6)" d="{k_small}" fill="{gold}" opacity="0.9"/>
    </g>
    <path transform="translate(252,236)" d="{body.path('Everything you watch and hear, in one beautiful app.', 0, 0, 30)}" fill="{ink}"/>
    <g transform="translate(252,282)" fill="{sub}">
      {''.join(f'<path d="{p}" opacity="0">{rot_anim(i)}</path>' for i, p in enumerate(rot_paths))}
    </g>
    <g transform="translate(252,322)">{''.join(chip_svg)}</g>
    <path transform="translate(252,410)" d="{bold.path('FREE  ·  OPEN SOURCE  ·  NO ACCOUNT  ·  NO ADS  ·  NO TRACKING  ·  MADE IN BANGLADESH', 0, 0, 14, track=1.6)}" fill="{gold}" opacity="0.9"/>
  </g>
  <rect x="1" y="1" width="{W - 2}" height="{H - 2}" rx="27" fill="none" stroke="url(#edge)" stroke-width="2"/>
</svg>
'''


def divider(dark: bool) -> str:
    W, H = 1280, 28
    a = GOLD if dark else "#C98A12"
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" role="img" aria-label="">
  <defs>
    <linearGradient id="g" x1="0" x2="1">
      <stop offset="0" stop-color="{a}" stop-opacity="0"/>
      <stop offset="0.3" stop-color="{a}"/>
      <stop offset="0.5" stop-color="{VIOLET}"/>
      <stop offset="0.7" stop-color="{ROSE}"/>
      <stop offset="1" stop-color="{ROSE}" stop-opacity="0"/>
    </linearGradient>
    <radialGradient id="dot"><stop offset="0" stop-color="#FFFFFF"/><stop offset="1" stop-color="{a}" stop-opacity="0"/></radialGradient>
  </defs>
  <rect x="0" y="13" width="{W}" height="2" fill="url(#g)" opacity="0.8"/>
  <circle cy="14" r="9" fill="url(#dot)">
    <animate attributeName="cx" values="120;1160;120" dur="9s" repeatCount="indefinite"/>
  </circle>
</svg>
'''


FEATURES = [
    ("Anime & manga", "CloudStream, Aniyomi, Mihon, Stremio and JS sources in one place", GOLD, "M12 3 L14 10 L21 12 L14 14 L12 21 L10 14 L3 12 L10 10 Z"),
    ("Music", "Local library, synced lyrics, 10-band EQ and YouTube Music", ROSE, "M9 18 a3 3 0 1 1 0-.1 V6 l9-2 v11 a3 3 0 1 1 0-.1 V8 l-9 2"),
    ("Video player", "Auto decoder, compressor, Anime4K, subtitles in any encoding", VIOLET, "M5 5 h14 v14 h-14 Z M10 9 v6 l5-3 Z"),
    ("Live TV", "IPTV playlists, a playlist market and auto-unlock for HTTP 403", CYAN, "M4 7 h16 v11 h-16 Z M9 3 l3 4 l3-4"),
    ("Made for TV", "A remote-first player, focus ring on everything, Android 7+", GOLD, "M3 6 h18 v11 h-18 Z M8 20 h8"),
    ("Private", "No account, no ads, no tracking. Your data stays on your device", ROSE, "M12 3 l7 3 v6 c0 4-3 7-7 9 c-4-2-7-5-7-9 v-6 Z"),
]


def features(dark: bool, display: Face, body: Face) -> str:
    W, H = 1280, 440
    cols, rows = 3, 2
    gap = 20
    cw = (W - gap * (cols - 1)) / cols
    ch = (H - gap * (rows - 1)) / rows
    card = "#121219" if dark else "#FFFFFF"
    ink = "#F4EFE6" if dark else "#17151C"
    sub = "#A9A2B8" if dark else "#5A546A"
    line = "rgba(255,255,255,0.08)" if dark else "rgba(23,21,28,0.10)"
    out = []
    for i, (t, d, col, icon) in enumerate(FEATURES):
        x = (i % cols) * (cw + gap)
        y = (i // cols) * (ch + gap)
        # Wrap the description to two lines.
        words, l1, l2 = d.split(), "", ""
        for w in words:
            if not l2 and body.width((l1 + " " + w).strip(), 19) < cw - 64:
                l1 = (l1 + " " + w).strip()
            else:
                l2 = (l2 + " " + w).strip()
        delay = i * 0.35
        out.append(f'''<g transform="translate({x:.1f},{y:.1f})">
    <rect width="{cw:.1f}" height="{ch:.1f}" rx="22" fill="{card}" stroke="{line}"/>
    <rect width="{cw:.1f}" height="{ch:.1f}" rx="22" fill="url(#glow{i})" opacity="0.0">
      <animate attributeName="opacity" values="0;0.9;0" dur="6s" begin="{delay:.2f}s" repeatCount="indefinite"/>
    </rect>
    <g transform="translate(32,30)">
      <rect width="48" height="48" rx="14" fill="{col}" opacity="0.16"/>
      <path transform="translate(12,12)" d="{icon}" fill="none" stroke="{col}" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"/>
    </g>
    <path transform="translate(32,116)" d="{display.path(t, 0, 0, 22)}" fill="{ink}"/>
    <path transform="translate(32,146)" d="{body.path(l1, 0, 0, 17)}" fill="{sub}"/>
    <path transform="translate(32,168)" d="{body.path(l2, 0, 0, 17)}" fill="{sub}"/>
  </g>''')
    defs = "".join(
        f'<radialGradient id="glow{i}" cx="0.15" cy="0.1" r="0.9"><stop offset="0" stop-color="{c[2]}" stop-opacity="0.28"/>'
        f'<stop offset="1" stop-color="{c[2]}" stop-opacity="0"/></radialGradient>'
        for i, c in enumerate(FEATURES)
    )
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" role="img" aria-label="Anime and manga, music, video player, live TV, made for TV, private">
  <defs>{defs}</defs>
  {''.join(out)}
</svg>
'''


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--kanji-font", required=True)
    a = ap.parse_args()
    kanji = Face(Path(a.kanji_font))
    display = Face(FONTS / "unbounded_800.ttf")
    disp6 = Face(FONTS / "unbounded_600.ttf")
    body = Face(FONTS / "manrope_medium.ttf")
    bold = Face(FONTS / "manrope_bold.ttf")
    for dark, name in ((True, "dark"), (False, "light")):
        (OUT / f"hero-{name}.svg").write_text(hero(dark, kanji, display, body, bold))
        (OUT / f"divider-{name}.svg").write_text(divider(dark))
        (OUT / f"features-{name}.svg").write_text(features(dark, disp6, body))
    print("wrote", sorted(p.name for p in OUT.glob("*.svg")))


if __name__ == "__main__":
    main()
