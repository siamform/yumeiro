"""Draws docs/assets/social/00-changelog-card.png for the version in version.properties."""
from PIL import Image, ImageDraw, ImageFont
F = "/usr/share/fonts/truetype/dejavu/DejaVuSans"
VER = next(l.split("=", 1)[1].strip() for l in open("version.properties") if l.startswith("VERSION_NAME="))
W, H = 1200, 900
im = Image.new("RGB", (W, H), (19, 18, 24)); d = ImageDraw.Draw(im)
d.text((60, 50), f"Yumeiro {VER}", font=ImageFont.truetype(F + "-Bold.ttf", 64), fill=(242, 199, 92))
d.text((60, 140), "2026-11-20  -  the 1.9.0 features, without the crash", font=ImageFont.truetype(F + ".ttf", 28), fill=(225, 222, 230))
items = ["Fixed: opening a video crashed 1.9.0 - update now",
         "Profiles (up to 8) with PIN / fingerprint lock",
         "Settings organised: You, Video, Music, Downloads, Sources",
         "Private downloads and stealth mode",
         "Your statistics: week, streak, activity map",
         "Telegram public channels (no login), TeraBox / DiskWala links",
         "Hold for 2x, volume boost to 200 %, Shorts player",
         "Faster start: feed and catalog show at once",
         "Live TV switches to backup links",
         "Vega script repos, your own proxy, DNS fallback"]
f = ImageFont.truetype(F + ".ttf", 27)
for i, t in enumerate(items):
    y = 222 + i * 56; col = (242, 199, 92) if i == 0 else (236, 234, 240)
    d.text((80, y), "\u2022", font=f, fill=(225, 222, 230)); d.text((114, y), t, font=f, fill=col)
d.text((60, 838), "Phone and Android TV. Full notes and known limits: CHANGELOG.md on GitHub.",
       font=ImageFont.truetype(F + ".ttf", 20), fill=(160, 158, 168))
im.save("docs/assets/social/00-changelog-card.png")
