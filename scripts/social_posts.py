"""Builds docs/assets/social/*.png (1080x1350) from real emulator captures (SocialShotsDeviceTest)."""
import sys, os
from PIL import Image, ImageDraw, ImageFont, ImageFilter
SRC = sys.argv[1]; TV = sys.argv[2] if len(sys.argv) > 2 else None
OUT = "docs/assets/social"
VER = next(l.split("=", 1)[1].strip() for l in open("version.properties") if l.startswith("VERSION_NAME="))
B = "/usr/share/fonts/truetype/noto/NotoSans-Bold.ttf"; R = "/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf"
CJK = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"
GOLD = (242, 199, 92); INK = (246, 244, 250); MUTE = (178, 174, 190); BG1 = (24, 20, 34); BG2 = (52, 34, 46)
W, H = 1080, 1350

def bg():
    im = Image.new("RGB", (W, H), BG1); d = ImageDraw.Draw(im)
    for y in range(H):
        t = y / H; d.line([(0, y), (W, y)], fill=tuple(int(BG1[i] * (1 - t) + BG2[i] * t) for i in range(3)))
    return im

def wrap(d, text, font, width):
    out, line = [], ""
    for w in text.split():
        t = (line + " " + w).strip()
        if d.textlength(t, font=font) <= width: line = t
        else: out.append(line); line = w
    if line: out.append(line)
    return out

def frame(shot, h):
    s = Image.open(shot).convert("RGB"); r = h / s.height; s = s.resize((int(s.width * r), h), Image.LANCZOS)
    pad = 14; f = Image.new("RGBA", (s.width + pad * 2, s.height + pad * 2), (0, 0, 0, 0))
    m = Image.new("L", f.size, 0); ImageDraw.Draw(m).rounded_rectangle([0, 0, f.width - 1, f.height - 1], 46, fill=255)
    f.paste((14, 13, 18, 255), (0, 0), m)
    sm = Image.new("L", s.size, 0); ImageDraw.Draw(sm).rounded_rectangle([0, 0, s.width - 1, s.height - 1], 34, fill=255)
    f.paste(s, (pad, pad), sm); return f

def tvframe(shot, w):
    s = Image.open(shot).convert("RGB"); r = w / s.width; s = s.resize((w, int(s.height * r)), Image.LANCZOS)
    f = Image.new("RGBA", (w + 24, s.height + 24 + 40), (0, 0, 0, 0)); d = ImageDraw.Draw(f)
    d.rounded_rectangle([0, 0, w + 23, s.height + 23], 18, fill=(14, 13, 18, 255)); f.paste(s, (12, 12))
    d.rectangle([(w + 24) // 2 - 90, s.height + 24, (w + 24) // 2 + 90, s.height + 34], fill=(40, 38, 48, 255))
    return f

def post(name, kicker, title, bullets, shot, tv=False, note="Real capture on the Android emulator, fresh install"):
    im = bg(); d = ImageDraw.Draw(im)
    d.rounded_rectangle([60, 56, 330, 104], 24, fill=GOLD); d.text((84, 62), f"Yumeiro {VER}", font=ImageFont.truetype(B, 28), fill=BG1)
    d.text((350, 64), kicker.upper(), font=ImageFont.truetype(B, 24), fill=GOLD)
    d.text((W - 150, 50), "夢色", font=ImageFont.truetype(CJK, 46), fill=(255, 255, 255, 60) if False else (90, 78, 104))
    tf = ImageFont.truetype(B, 58); y = 130
    for l in wrap(d, title, tf, W - 120): d.text((60, y), l, font=tf, fill=INK); y += 72
    if tv:
        f = tvframe(shot, W - 120); im.paste(f, (60, y + 24), f); by = y + 24 + f.height + 20
        bf = ImageFont.truetype(R, 30)
        for b in bullets:
            for i, l in enumerate(wrap(d, b, bf, W - 170)):
                if i == 0: d.ellipse([66, by + 14, 78, by + 26], fill=GOLD)
                d.text((100, by), l, font=bf, fill=INK); by += 42
            by += 10
    else:
        fh = H - y - 150; f = frame(shot, fh); fx = W - f.width - 50; im.paste(f, (fx, y + 20), f)
        bf = ImageFont.truetype(R, 30); bw = fx - 110; by = y + 60
        for b in bullets:
            for i, l in enumerate(wrap(d, b, bf, bw)):
                if i == 0: d.ellipse([66, by + 14, 78, by + 26], fill=GOLD)
                d.text((96, by), l, font=bf, fill=INK); by += 42
            by += 22
    d.line([(60, H - 100), (W - 60, H - 100)], fill=(70, 62, 84), width=2)
    d.text((60, H - 82), note, font=ImageFont.truetype(R, 22), fill=MUTE)
    d.text((60, H - 50), "github.com/siamform/yumeiro  ·  yumeiro.click  ·  Made in Bangladesh", font=ImageFont.truetype(R, 22), fill=MUTE)
    im.save(f"{OUT}/{name}.png"); print(name)

S = lambda n: os.path.join(SRC, f"social_{n}.png")
post("01-extensions", "Installed extensions", "Your sources, ready on first launch",
     ["Starter extensions install from the real market on first run", "Rows show at once; extensions finish loading behind", "Upcoming, Spotlight and live rows on the Videos tab"], S("extensions_home"))
post("02-video", "Real playback", "A film plays, and remembers where you stopped",
     ["Big Buck Bunny (Blender Foundation, CC BY 3.0) streaming in the player", "\"Resumed at 01:02\" with Start over", "Hold for 2x, volume boost to 200 %, external players"], S("video_controls"))
post("03-music", "Real music", "Hi-Res music player, lyrics, queue and EQ",
     ["SoundHelix Song 1 playing from the phone's Music folder", "Lyrics, Up next, Sleep timer and EQ one tap away", "Mini player follows you across the app"], S("music_playing"))
post("04-whos-watching", "Profiles", "Who's watching?",
     ["Up to 8 profiles, each with its own history, lists and 18+ setting", "Lock one with a 4–8 digit PIN or fingerprint", "Updating keeps your data as the first profile"], S("whos_watching"))
post("05-profiles-page", "New in Settings", "Profiles now have their own page",
     ["Settings › You › Profiles", "Add, rename, lock and colour each profile", "18+ content is set per profile, right here"], S("profiles"))
post("06-settings", "Organised by subject", "Settings, sorted the way you think",
     ["You · Video · Music · Downloads & library", "Sources & accounts · System · About", "Each subject has exactly one page"], S("settings_home"))
post("07-search", "Settings search", "Type it, find it",
     ["Search finds every page: Telegram, Profiles, Statistics, Vega…", "Shown here: \"telegram\" › Telegram & web sites", "Works with the TV remote too"], S("settings_search"))
post("08-telegram", "Telegram, no login", "Public Telegram channels inside Yumeiro",
     ["Settings › Sources & accounts › Telegram & web sites", "Type @channel or paste a t.me link, tap Add", "Public posts load with no sign-in; some open only in the Telegram app"], S("telegram_channel"))
post("09-stats", "Your statistics", "See how you watch and listen",
     ["Settings › You › Your statistics", "Week, streak, best day and activity map", "Counted on this device only; never while incognito"], S("stats"))
post("10-sources", "Sources", "Aniyomi, Mihon, Stremio and script repos",
     ["Discover repositories ranked by stars and updates", "Vega script repos run in a sandbox", "Source status shows which sources respond now"], S("sources"))
post("11-music-settings", "Music & sound", "Quality you choose, per network",
     ["Hi-Res Max, Lossless, CD or data saver", "Different quality on Wi-Fi and mobile data", "Equalizer, lyrics and Last.fm on the same page"], S("music_settings"))
post("12-downloads", "Downloads", "Downloads that stay organised",
     ["Settings › Downloads & library › Downloads", "Auto-delete watched, permanent by default, episode quality", "Private downloads: hidden from lists and the gallery"], S("downloads"))
if TV:
    T = lambda n: os.path.join(TV, f"social_{n}.png")
    for n, k, t, b in [
        ("whos_watching", "Android TV", "Who's watching? on the big screen", ["Pick a profile with the remote", "PIN pad works with the D-pad", "Nothing behind it is reachable until you choose"]),
        ("video_controls", "Android TV", "Real playback on Yumeiro TV", ["Big Buck Bunny (CC BY 3.0) streaming", "Resume where you stopped", "Every control reachable with the remote"]),
        ("settings_home", "Android TV", "The new Settings on TV", ["Same subjects as the phone: You, Video, Music…", "Side rail navigation", "Search with the remote"]),
        ("extensions_home", "Android TV", "Your extensions on TV", ["Starter extensions install on first run", "Big posters, made for the remote", "Live, Upcoming and Spotlight rows"]),
        ("music_playing", "Android TV", "Music on the TV", ["Real song, full-screen player", "Lyrics, queue and EQ", "Background play while you browse"]),
    ]:
        if os.path.exists(T(n)): post(f"tv-{n.replace('_','-')}", k, t, b, T(n), tv=True, note="Real capture on the Android TV 14 emulator, fresh install")
