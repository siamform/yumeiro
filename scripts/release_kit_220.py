#!/usr/bin/env python3
"""Yumeiro 2.2.0 release images from real app captures.

Usage: python3 scripts/release_kit_220.py <shots-dir> [out-dir]   (run from the repo root)
Same style and helpers as release_kit_210.py (its drawing functions are loaded from that file, not copied).
<shots-dir> holds: home_day, home_night, live_tv, themes, library (.png, 1080x2400 field-probe captures; sources in
docs/assets/release-2.2/README.md).
"""
import os, sys

_kit = open(os.path.join(os.path.dirname(__file__), "release_kit_210.py"), encoding="utf-8").read()
_helpers = _kit[: _kit.index("\nNOTE = ")]  # fonts, colours, bg / phone / pill / post / banner / card helpers end here
sys.argv = [sys.argv[0], sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else "docs/assets/release-2.2"]
exec(compile(_helpers, "release_kit_210.py", "exec"))
VER = "2.2.0"
NOTE = "Real app screens from automatic tests on the Android emulator"
_kit_rest = _kit[_kit.index("\nNOTE = "):]
for fn in ("post", "banner", "card"):  # the drawing functions, without 2.1's calls
    start = _kit_rest.index(f"\ndef {fn}(")
    end = _kit_rest.index("\n\n\n", start + 1)
    exec(compile(_kit_rest[start:end], "release_kit_210.py", "exec"))

LINES = ["Online right after install", "Live TV keeps its channels", "One Back, the same everywhere", "Solid themes, glass gone"]
banner("hero-readme-1600x800", 1600, 800, ["home_day", "live_tv", "themes"], LINES)
banner("banner-github-1280x640", 1280, 640, ["home_day", "live_tv", "themes"], LINES)
banner("og-website-1200x630", 1200, 630, ["home_day", "live_tv"], LINES)
banner("x-1600x900", 1600, 900, ["home_night", "live_tv"], LINES)  # three phones cover the title at 16:9


def card220(name, w, h, sections):
    im = bg(w, h); d = ImageDraw.Draw(im); s = w / 1080
    x = pill(d, (int(60 * s), int(56 * s)), f"Yumeiro {VER}", int(26 * s))
    d.text((x + int(20 * s), int(66 * s)), "WHAT'S NEW", font=F(MANB, int(24 * s)), fill=GOLD)
    brand(d, w, h, s)
    d.text((int(60 * s), int(140 * s)), "Online at once,", font=F(UNB, int(56 * s)), fill=INK)
    d.text((int(60 * s), int(212 * s)), "one Back, solid", font=F(UNB, int(56 * s)), fill=INK)
    y = int(320 * s); hf = F(UNB, int(30 * s)); bf = F(MAN, int(29 * s))
    for title, items in sections:
        d.text((int(60 * s), y), title, font=hf, fill=GOLD); y += int(52 * s)
        for it in items:
            for i, l in enumerate(wrap(d, it, bf, w - int(170 * s))):
                if i == 0: d.ellipse([int(66 * s), y + int(13 * s), int(78 * s), y + int(25 * s)], fill=GOLD)
                d.text((int(98 * s), y), l, font=bf, fill=INK); y += int(40 * s)
            y += int(10 * s)
        y += int(22 * s)
    footer(d, w, h, "Checked with automatic tests and emulator runs; details in CHANGELOG.md", s)
    im.save(f"{OUT}/{name}.png"); print(name)


card220("changelog-card-1080x1350", 1080, 1350, [
    ("Fixed", ["Online pages work right after an install or update", "Live TV shows its saved list at once, even on a slow link",
               "Back key, button bar and swipe close pages like the arrow"]),
    ("Changed", ["Glass effects removed: solid, readable themes", "Unsupported extensions say what is missing and what to do",
                 "Market cards open full details; clearer Telegram page"]),
    ("Faster", ["No start-up wait on a slow DNS server", "Nothing redrawn while the app sits still"]),
    ("Security", ["All 22 code-scanning alerts reviewed; 9 fixed in code"]),
])
P = [
    ("01-online", "Online at once", "Install, open, it's online",
     ["No more 3 to 5 minute wait after an install or update", "Market, YouTube, Yumix and Live TV ready from the start",
      "Notifications and Cloudflare DNS kept"], "home_day"),
    ("02-live-tv", "Live TV", "Live TV keeps its channels",
     ["The saved list shows at once, even on a slow link", "7,555 channels back 5 s after a relaunch",
      "Lists no longer wait 3 minutes for IPv4"], "live_tv"),
    ("03-themes", "Themes", "Solid themes, nothing frosted",
     ["Glass effects removed from every theme", "Dock, sheets and cards stay readable on any poster",
      "Kasumi users move to Tsuki Night"], "themes"),
    ("04-back", "Navigation", "One Back, the same everywhere",
     ["Key, button bar, swipe and edge swipe slide like the arrow", "No blank page during the slide",
      "Pages keep their place after a quick Back"], "home_night"),
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
    p1 = phone("live_tv", 820); p2 = phone("home_day", 860)  # both end above the call-to-action bar
    im.paste(p1, (w - p1.width + 10, 800), p1); im.paste(p2, (20, 760), p2)
    d.rounded_rectangle([60, h - 170, w - 60, h - 70], 50, fill=GOLD)
    t = "Get it: yumeiro.click"; ft = F(UNB, 40)
    d.text(((w - d.textlength(t, font=ft)) / 2, h - 146), t, font=ft, fill=BG1)
    im.save(f"{OUT}/{name}.png"); print(name)


story("story-1080x1920")
