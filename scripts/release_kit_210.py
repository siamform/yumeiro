#!/usr/bin/env python3
"""Yumeiro 2.1.0 release images from real app captures.

Usage: python3 scripts/release_kit_210.py <shots-dir> [out-dir]
<shots-dir> holds PNG captures named as in SHOTS below (from SocialShotsDeviceTest and the feature device tests).
Writes docs/assets/release-2.1/*.png: README hero, GitHub / website banner (1280x640, 1200x630), X (1600x900),
square posts (1080x1080), portrait posts (1080x1350), a story (1080x1920) and the changelog card.
Style: Yumeiro night (ink to plum gradient), moonlight-gold accent, Unbounded titles, Manrope text, 夢色 mark.
"""
import os, sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter

SRC = sys.argv[1]
OUT = sys.argv[2] if len(sys.argv) > 2 else "docs/assets/release-2.1"
os.makedirs(OUT, exist_ok=True)
FONT = "app/src/main/res/font"
UNB = f"{FONT}/unbounded_700.ttf"; UNB8 = f"{FONT}/unbounded_800.ttf"
MAN = f"{FONT}/manrope_medium.ttf"; MANB = f"{FONT}/manrope_bold.ttf"
CJK = next((p for p in ["/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc",
                        "/usr/share/fonts/opentype/noto/NotoSerifCJK-Bold.ttc"] if os.path.exists(p)))
GOLD = (245, 196, 107); INK = (246, 244, 250); MUTE = (182, 176, 196)
BG1 = (18, 16, 26); BG2 = (54, 32, 52); BG3 = (30, 24, 44)
VER = "2.1.0"
F = lambda p, s: ImageFont.truetype(p, s)


def bg(w, h):
    im = Image.new("RGB", (w, h), BG1); d = ImageDraw.Draw(im)
    for y in range(h):
        t = y / h
        c = tuple(int(BG1[i] * (1 - t) + BG2[i] * t) for i in range(3))
        d.line([(0, y), (w, y)], fill=c)
    glow = Image.new("RGB", (w, h), (0, 0, 0)); g = ImageDraw.Draw(glow)
    r = int(min(w, h) * 0.55)
    g.ellipse([w - r * 1.3, -r * 0.6, w + r * 0.7, r * 1.2], fill=(120, 84, 40))
    glow = glow.filter(ImageFilter.GaussianBlur(r // 3))
    im = Image.blend(im, Image.composite(glow, im, glow.convert("L")), 0.45)
    d = ImageDraw.Draw(im)
    for i, (x, y, s) in enumerate([(0.12, 0.08, 3), (0.32, 0.16, 2), (0.71, 0.06, 2), (0.9, 0.3, 3), (0.55, 0.12, 2)]):
        d.ellipse([w * x - s, h * y - s, w * x + s, h * y + s], fill=(232, 220, 190))
    return im


def wrap(d, text, font, width):
    out, line = [], ""
    for w in text.split():
        t = (line + " " + w).strip()
        if d.textlength(t, font=font) <= width: line = t
        else: out.append(line); line = w
    if line: out.append(line)
    return out


def phone(name, h):
    s = Image.open(os.path.join(SRC, name + ".png")).convert("RGB")
    r = h / s.height; s = s.resize((int(s.width * r), h), Image.LANCZOS)
    pad = max(8, h // 90); rad = h // 18
    f = Image.new("RGBA", (s.width + pad * 2, s.height + pad * 2), (0, 0, 0, 0))
    m = Image.new("L", f.size, 0); ImageDraw.Draw(m).rounded_rectangle([0, 0, f.width - 1, f.height - 1], rad + pad, fill=255)
    f.paste((12, 11, 16, 255), (0, 0), m)
    sm = Image.new("L", s.size, 0); ImageDraw.Draw(sm).rounded_rectangle([0, 0, s.width - 1, s.height - 1], rad, fill=255)
    f.paste(s, (pad, pad), sm)
    sh = Image.new("RGBA", (f.width + 80, f.height + 80), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle([40, 52, f.width + 40, f.height + 52], rad + pad, fill=(0, 0, 0, 150))
    sh = sh.filter(ImageFilter.GaussianBlur(22)); sh.paste(f, (40, 40), f)
    return sh


def pill(d, xy, text, size):
    x, y = xy; ft = F(MANB, size); w = d.textlength(text, font=ft)
    d.rounded_rectangle([x, y, x + w + size * 1.4, y + size * 1.9], size, fill=GOLD)
    d.text((x + size * 0.7, y + size * 0.38), text, font=ft, fill=BG1)
    return x + w + size * 1.4


def brand(d, w, h, s=1.0):
    d.text((w - int(150 * s), int(34 * s)), "夢色", font=F(CJK, int(52 * s)), fill=(120, 104, 132))


def footer(d, w, h, note, s=1.0):
    y = h - int(92 * s)
    d.line([(int(60 * s), y), (w - int(60 * s), y)], fill=(78, 68, 92), width=2)
    d.text((int(60 * s), y + int(14 * s)), note, font=F(MAN, int(22 * s)), fill=MUTE)
    d.text((int(60 * s), y + int(46 * s)), "yumeiro.click  ·  github.com/siamform/yumeiro  ·  @officialyumeiro  ·  Made in Bangladesh",
           font=F(MAN, int(22 * s)), fill=MUTE)


NOTE = "Real app screens from automatic tests on the Android emulator"
SHOTS = {  # name -> what it shows (README of the kit lists the source run of each)
    "extensions_home": "Videos tab with rows from the starter extensions",
    "video_controls": "A film playing, resumed (Big Buck Bunny, CC BY 3.0)",
    "music_playing": "A song in Now Playing (SoundHelix Song 1)",
    "whos_watching": "Who's watching? with three profiles",
    "dl_popup": "The new download popup (test stream)",
    "home_shortcuts": "Home shortcuts: Telegram, Profiles, Your stats, Airing soon",
    "telegram_page": "The separate Telegram page",
    "live_country": "Live TV country filter (sample playlist)",
    "stats": "Your statistics",
    "website_panel": "Website stream panel (test page)",
}


def post(name, w, h, kicker, title, bullets, shot, note=NOTE):
    im = bg(w, h); d = ImageDraw.Draw(im); s = w / 1080
    x = pill(d, (int(60 * s), int(56 * s)), f"Yumeiro {VER}", int(26 * s))
    d.text((x + int(20 * s), int(66 * s)), kicker.upper(), font=F(MANB, int(24 * s)), fill=GOLD)
    brand(d, w, h, s)
    tf = F(UNB, int(54 * s)); y = int(140 * s)
    for l in wrap(d, title, tf, w - int(120 * s)):
        d.text((int(60 * s), y), l, font=tf, fill=INK); y += int(70 * s)
    ph = h - y - int(150 * s)
    p = phone(shot, ph); px = w - p.width - int(10 * s); im.paste(p, (px, y - int(10 * s)), p)
    bf = F(MAN, int(31 * s)); bw = px + int(40 * s) - int(110 * s); by = y + int(40 * s)
    for b in bullets:
        for i, l in enumerate(wrap(d, b, bf, bw)):
            if i == 0: d.ellipse([int(64 * s), by + int(14 * s), int(78 * s), by + int(28 * s)], fill=GOLD)
            d.text((int(96 * s), by), l, font=bf, fill=INK); by += int(43 * s)
        by += int(24 * s)
    footer(d, w, h, note, s)
    im.save(f"{OUT}/{name}.png"); print(name)


def banner(name, w, h, shots, lines):
    im = bg(w, h); d = ImageDraw.Draw(im); s = h / 640
    pill(d, (int(56 * s), int(52 * s)), "New version", int(22 * s))
    d.text((int(56 * s), int(112 * s)), "Yumeiro", font=F(UNB8, int(92 * s)), fill=INK)
    d.text((int(56 * s), int(226 * s)), VER, font=F(UNB8, int(92 * s)), fill=GOLD)
    d.text((int(56 * s) + int(d.textlength(VER, font=F(UNB8, int(92 * s)))) + int(24 * s), int(250 * s)), "夢色",
           font=F(CJK, int(56 * s)), fill=(150, 132, 160))
    bf = F(MAN, int(26 * s)); y = int(360 * s)
    for l in lines:
        d.ellipse([int(60 * s), y + int(11 * s), int(72 * s), y + int(23 * s)], fill=GOLD)
        d.text((int(88 * s), y), l, font=bf, fill=INK); y += int(40 * s)
    d.text((int(56 * s), h - int(48 * s)), "Free and open source  ·  yumeiro.click  ·  Made in Bangladesh", font=F(MAN, int(20 * s)), fill=MUTE)
    ph = int(h * 0.86); x = w - int(30 * s)
    for i, sh in enumerate(reversed(shots)):
        p = phone(sh, ph - i * int(40 * s)); x -= p.width - int(70 * s)
        im.paste(p, (x, (h - p.height) // 2 + int(20 * s)), p)
    im.save(f"{OUT}/{name}.png"); print(name)


def card(name, w, h, sections):
    im = bg(w, h); d = ImageDraw.Draw(im); s = w / 1080
    x = pill(d, (int(60 * s), int(56 * s)), f"Yumeiro {VER}", int(26 * s))
    d.text((x + int(20 * s), int(66 * s)), "WHAT'S NEW", font=F(MANB, int(24 * s)), fill=GOLD)
    brand(d, w, h, s)
    d.text((int(60 * s), int(140 * s)), "Faster feeds, more", font=F(UNB, int(56 * s)), fill=INK)
    d.text((int(60 * s), int(212 * s)), "sources, no freezes", font=F(UNB, int(56 * s)), fill=INK)
    y = int(320 * s); hf = F(UNB, int(30 * s)); bf = F(MAN, int(29 * s))
    for title, items in sections:
        d.text((int(60 * s), y), title, font=hf, fill=GOLD); y += int(52 * s)
        for it in items:
            for i, l in enumerate(wrap(d, it, bf, w - int(170 * s))):
                if i == 0: d.ellipse([int(66 * s), y + int(13 * s), int(78 * s), y + int(25 * s)], fill=GOLD)
                d.text((int(98 * s), y), l, font=bf, fill=INK); y += int(40 * s)
            y += int(10 * s)
        y += int(22 * s)
    footer(d, w, h, "Every item checked with automatic tests; details in CHANGELOG.md", s)
    im.save(f"{OUT}/{name}.png"); print(name)


LINES = ["No more freezes on touch", "Feeds show at once", "A friendlier downloader", "More sources, profiles kept apart"]
banner("hero-readme-1600x800", 1600, 800, ["extensions_home", "dl_popup", "home_shortcuts"], LINES)
banner("banner-github-1280x640", 1280, 640, ["extensions_home", "dl_popup", "home_shortcuts"], LINES)
banner("og-website-1200x630", 1200, 630, ["extensions_home", "dl_popup"], LINES)
banner("x-1600x900", 1600, 900, ["extensions_home", "dl_popup", "home_shortcuts"], LINES)
card("changelog-card-1080x1350", 1080, 1350, [
    ("Fixed", ["No freeze when you touch the screen while a video plays", "TeraBox full files when signed in; DiskWala hands off to its app",
               "Reading, extensions and pins kept per profile"]),
    ("Faster", ["Yumix and Home show the saved feed at once", "Fastest extensions first, dead sites set aside"]),
    ("New", ["Download popup: Offline / Permanent, quality, audio, subtitles, ranges, playlists",
             "Website tab stays open while a stream plays", "Nuvio sources, vertical player, Live TV by country"]),
])
P = [
    ("01-no-freeze", "No more freezes", "Touch, scroll, switch: it keeps up",
     ["The 2.0.0 freeze on touch is fixed", "A new check fails any build with a frame over 700 ms on a 2 GB phone", "Feeds show their saved page at once, then refresh"], "extensions_home"),
    ("02-downloader", "New downloader", "Download anything, your way",
     ["Offline or Permanent in one switch", "Real quality, audio and subtitle choices", "One episode, a range, or a whole playlist"], "dl_popup"),
    ("03-shortcuts", "Home", "More shortcuts, more nav-bar pages",
     ["Telegram, Profiles, Your stats, Airing soon, Reading, Inbox", "Pin them to the navigation bar too"], "home_shortcuts"),
    ("04-telegram", "Telegram", "Telegram has its own page",
     ["Add a public channel with @name or a t.me link", "TeraBox and DiskWala links in posts open in Yumeiro"], "telegram_page"),
    ("05-live-country", "Live TV", "Live TV by country",
     ["Country chips when a playlist has two or more", "Pick a country, see only its channels"], "live_country"),
    ("06-profiles", "Profiles", "Every profile, really its own",
     ["Extensions, repositories, pins and reading per profile", "\"Forgot PIN\" uses security questions"], "whos_watching"),
    ("07-video", "Player", "Vertical player and a cleaner top bar",
     ["Swipe through Shorts and any portrait video", "Quality, audio and more sit at the edge"], "video_controls"),
    ("08-websites", "Websites", "Websites that stay open",
     ["One website tab stays open while a stream plays", "The stream bar follows the video playing now"], "website_panel"),
]
for n, k, t, b, sh in P:
    post(f"post-{n}-1080x1350", 1080, 1350, k, t, b, sh)
    post(f"square-{n}-1080x1080", 1080, 1080, k, t, b[:2], sh)


def story(name):
    w, h = 1080, 1920; im = bg(w, h); d = ImageDraw.Draw(im)
    pill(d, (60, 90), "New version", 28)
    d.text((60, 170), "Yumeiro", font=F(UNB8, 120), fill=INK)
    d.text((60, 310), VER, font=F(UNB8, 120), fill=GOLD)
    d.text((60 + d.textlength(VER, font=F(UNB8, 120)) + 30, 345), "夢色", font=F(CJK, 72), fill=(150, 132, 160))
    y = 500
    for l in LINES:
        d.ellipse([66, y + 16, 82, y + 32], fill=GOLD); d.text((104, y), l, font=F(MAN, 38), fill=INK); y += 62
    p1 = phone("dl_popup", 1000); p2 = phone("extensions_home", 1080)
    im.paste(p1, (w - p1.width + 10, 800), p1); im.paste(p2, (-20, 760), p2)
    d.rounded_rectangle([60, h - 170, w - 60, h - 70], 50, fill=GOLD)
    t = "Test it: yumeiro.click"; ft = F(UNB, 40)
    d.text(((w - d.textlength(t, font=ft)) / 2, h - 146), t, font=ft, fill=BG1)
    im.save(f"{OUT}/{name}.png"); print(name)


story("story-1080x1920")
