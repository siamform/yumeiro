#!/usr/bin/env python3
"""NBUG-14 (NB14-1, NB14-3): the market's iptv-org country lists get the logos and categories that
iptv-org adds only when it publishes (ADR-026).

The repository's raw `streams/<cc>.m3u` files carry `tvg-id` only. `build_market.prepare(c, data, meta)`
must fill in a missing `tvg-logo` (feed match first, then the channel's own logo, raster formats only)
and a missing `group-title` (the channel's first category) for candidates marked `fillFromIptvOrg`,
keep what a list already has, and leave other candidates alone. Run: python3 this_file (exit 1 on a
failure). No network.
"""
import importlib.util
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("build_market", os.path.join(HERE, "..", "build_market.py"))
bm = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bm)

failures = []


def check(name, cond, detail=""):
    print(("PASS " if cond else "FAIL ") + name + (f" · {detail}" if detail and not cond else ""))
    if not cond:
        failures.append(name)


LOGOS = [
    {"channel": "AnandaTV.bd", "feed": None, "in_use": True, "format": "PNG", "url": "https://i.imgur.com/ananda.png"},
    {"channel": "News24.bd", "feed": None, "in_use": True, "format": "SVG", "url": "https://example.org/news24.svg"},
    {"channel": "News24.bd", "feed": None, "in_use": True, "format": "PNG", "url": "https://example.org/news24.png"},
    {"channel": "AE.us", "feed": None, "in_use": True, "format": "PNG", "url": "https://example.org/ae.png"},
    {"channel": "AE.us", "feed": "East", "in_use": True, "format": "JPEG", "url": "https://example.org/ae-east.jpg"},
    {"channel": "Old.us", "feed": None, "in_use": False, "format": "PNG", "url": "https://example.org/old-unused.png"},
    {"channel": "Old.us", "feed": None, "in_use": True, "format": "PNG", "url": "https://example.org/old.png"},
    {"channel": "OnlySvg.us", "feed": None, "in_use": True, "format": "SVG", "url": "https://example.org/only.svg"},
]
CHANNELS = [
    {"id": "AnandaTV.bd", "categories": ["entertainment"]},
    {"id": "News24.bd", "categories": ["news", "general"]},
    {"id": "AE.us", "categories": ["entertainment"]},
    {"id": "Old.us", "categories": []},
    {"id": "OnlySvg.us", "categories": ["kids"]},
]
CATEGORIES = [{"id": "entertainment", "name": "Entertainment"}, {"id": "news", "name": "News"}, {"id": "kids", "name": "Kids"}]

RAW = (
    "#EXTM3U\r\n"
    '#EXTINF:-1 tvg-id="AnandaTV.bd@SD",Ananda TV (720p)\r\n'
    "https://example.org/ananda.m3u8\r\n"
    '#EXTINF:-1 tvg-id="News24.bd@HD",News 24\r\n'
    "https://example.org/news24.m3u8\r\n"
    '#EXTINF:-1 tvg-id="AE.us@East",A&E East\r\n'
    "https://example.org/ae-east.m3u8\r\n"
    '#EXTINF:-1 tvg-id="AE.us@West",A&E West\r\n'
    "https://example.org/ae-west.m3u8\r\n"
    '#EXTINF:-1 tvg-id="Old.us@SD" tvg-logo="https://keep.example/own.png" group-title="Mine",Old, with its own logo\r\n'
    "https://example.org/old.m3u8\r\n"
    '#EXTINF:-1 tvg-id="OnlySvg.us@SD",Only SVG\r\n'
    "https://example.org/svg.m3u8\r\n"
    '#EXTINF:-1 tvg-id="Unknown.zz@SD",Unknown\r\n'
    "https://example.org/unknown.m3u8\r\n"
    "#EXTINF:-1,No id\r\n"
    "https://example.org/noid.m3u8\r\n"
).encode("utf-8")


def extinf_of(text, title):
    return next((l for l in text.split("\n") if l.startswith("#EXTINF") and l.endswith("," + title)), None)


def attrs(line):
    return dict(bm.ATTR.findall(line or ""))


meta_from = getattr(bm, "meta_from", None)
prepare = getattr(bm, "prepare", None)
check("build_market has meta_from(logos, channels, categories)", callable(meta_from))
check("build_market has prepare(candidate, data, meta)", callable(prepare))

if callable(meta_from) and callable(prepare):
    meta = meta_from(LOGOS, CHANNELS, CATEGORIES)
    c = {"id": "iptv-org-bd", "fillFromIptvOrg": True}
    text, entries, groups, _ = prepare(c, RAW, meta)
    check("same channel count", len(entries) == 8, str(len(entries)))

    a = attrs(extinf_of(text, "Ananda TV (720p)"))
    check("NB14-1 channel logo filled", a.get("tvg-logo") == "https://i.imgur.com/ananda.png", str(a))
    check("NB14-3 category filled", a.get("group-title") == "Entertainment", str(a))

    n = attrs(extinf_of(text, "News 24"))
    check("NB14-1 raster logo chosen over SVG", n.get("tvg-logo") == "https://example.org/news24.png", str(n))
    check("NB14-3 first category", n.get("group-title") == "News", str(n))

    e = attrs(extinf_of(text, "A&E East"))
    check("NB14-1 feed logo first", e.get("tvg-logo") == "https://example.org/ae-east.jpg", str(e))
    w = attrs(extinf_of(text, "A&E West"))
    check("NB14-1 channel logo when the feed has none", w.get("tvg-logo") == "https://example.org/ae.png", str(w))

    o = attrs(extinf_of(text, "Old, with its own logo"))
    check("own logo and group kept", o.get("tvg-logo") == "https://keep.example/own.png" and o.get("group-title") == "Mine", str(o))

    s = attrs(extinf_of(text, "Only SVG"))
    check("no SVG logo added (no decoder in the app)", "tvg-logo" not in s, str(s))
    check("category added even without a logo", s.get("group-title") == "Kids", str(s))

    u = extinf_of(text, "Unknown")
    check("unknown id unchanged", u == '#EXTINF:-1 tvg-id="Unknown.zz@SD",Unknown', str(u))
    check("no-id line unchanged", extinf_of(text, "No id") == "#EXTINF:-1,No id")
    check("groups counted after the fill", groups.get("Entertainment") == 3 and groups.get("News") == 1, str(groups))
    check("old logo of an unused row not chosen", meta["logo"].get(("Old.us", None)) == "https://example.org/old.png", str(meta["logo"].get(("Old.us", None))))

    other = {"id": "free_tv"}
    t2, _, _, _ = prepare(other, RAW, meta)
    check("candidates without fillFromIptvOrg untouched", "i.imgur.com/ananda.png" not in t2)
    t3, _, _, _ = prepare(c, RAW, None)
    check("no API data: list published as before", "i.imgur.com/ananda.png" not in t3 and len(t3) > 0)

    import json
    cfg = json.load(open(os.path.join(HERE, "..", "..", "market", "sources.json"), encoding="utf-8"))
    iptv = [r for r in cfg.get("repos", []) if r.get("repo") == "iptv-org/iptv"]
    check("sources.json marks iptv-org/iptv fillFromIptvOrg", bool(iptv) and iptv[0].get("fillFromIptvOrg") is True, str(iptv))

print(f"{len(failures)} failure(s)")
sys.exit(1 if failures else 0)
