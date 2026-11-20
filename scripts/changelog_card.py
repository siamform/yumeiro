"""Draws docs/assets/final/changelog-card-1.9.0.png from the 1.9.0 headline list (CHANGELOG.md)."""
from PIL import Image, ImageDraw, ImageFont
F = "/usr/share/fonts/truetype/dejavu/DejaVuSans"
W, H = 1200, 900
im = Image.new("RGB", (W, H), (19, 18, 24)); d = ImageDraw.Draw(im)
d.text((60, 50), "Yumeiro 1.9.0", font=ImageFont.truetype(F + "-Bold.ttf", 64), fill=(242, 199, 92))
d.text((60, 140), "release candidate  -  yours, and fast", font=ImageFont.truetype(F + ".ttf", 28), fill=(225, 222, 230))
items = ["Profiles (up to 8) with PIN / fingerprint lock", "Private downloads and stealth mode",
         "Accent colours and Kasumi glass theme", "Hold for 2x, volume boost to 200 %, Shorts player",
         "Faster start: feed and catalog show at once", "Offline manga chapters, auto-translation (on device)",
         "Live TV switches to backup links", "Telegram public channels, TeraBox / DiskWala links",
         "Vega script repos, your own proxy, DNS fallback", "TV: search sheets no longer pop the keyboard"]
f = ImageFont.truetype(F + ".ttf", 27)
for i, t in enumerate(items):
    y = 222 + i * 56; d.text((80, y), "\u2022", font=f, fill=(225, 222, 230)); d.text((114, y), t, font=f, fill=(236, 234, 240))
d.text((60, 838), "Release candidate for the owner's review, not published. Known limits: CHANGELOG.md.",
       font=ImageFont.truetype(F + ".ttf", 20), fill=(160, 158, 168))
im.save("docs/assets/final/changelog-card-1.9.0.png")
