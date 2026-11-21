#!/usr/bin/env python3
"""E2 / ADR-055: curated, probed Live TV playlists (about 20 working channels per country).

Data: the iptv-org database API (public domain). For every country in .github/streams/curated.json:
  * candidates = streams whose channel is from that country, not NSFW, not closed, not on the
    iptv-org blocklist (DMCA / NSFW), and playable without extra headers;
  * ranked: 24/7 and not geo-blocked first, then https, then higher quality;
  * probed in parallel (HTTP 200 + an HLS playlist; a master playlist must also have a live
    first variant); one stream per channel; the first `per_country` that answer are kept.
Writes <out>/curated/<cc>.m3u and <out>/curated/index.json. Countries whose probe yields nothing
keep their previous file. Usage: curate_live.py <out dir> [--api <dir with the four json files>]
"""
import concurrent.futures as cf
import json, os, re, sys, urllib.parse, urllib.request
from datetime import datetime, timezone

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CONFIG = os.path.join(ROOT, ".github", "streams", "curated.json")
API = "https://iptv-org.github.io/api"
UA = "Mozilla/5.0 (Linux; Android 14) Yumeiro-live-probe/1.0"
PROBE_TIMEOUT = 8
PROBE_BYTES = 64 * 1024
QUALITY = re.compile(r"(\d+)p")
VARIANT = re.compile(r"#EXT-X-STREAM-INF[^\n]*\n\s*([^#\s][^\n]*)")


def load_api(local=None):
    out = {}
    for name in ("streams", "channels", "blocklist", "logos"):
        if local:
            with open(os.path.join(local, f"{name}.json"), encoding="utf-8") as f:
                out[name] = json.load(f)
        else:
            req = urllib.request.Request(f"{API}/{name}.json", headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=120) as r:
                out[name] = json.load(r)
    return out


def rank(stream):
    labels = set(stream.get("labels") or [])
    q = QUALITY.search(stream.get("quality") or "")
    return ("Geo-blocked" in labels, "Not 24/7" in labels,
            not stream["url"].startswith("https://"), -(int(q.group(1)) if q else 0))


def candidates(api, country):
    """Ranked, filtered streams for one country (pure; unit-tested)."""
    blocked = {b["channel"] for b in api["blocklist"]}
    channels = {c["id"]: c for c in api["channels"]}
    picked = []
    for s in api["streams"]:
        c = channels.get(s.get("channel"))
        if not c or c.get("country") != country:
            continue
        if c.get("is_nsfw") or c.get("closed") or c["id"] in blocked:
            continue
        if s.get("user_agent") or s.get("referrer"):
            continue
        if not s.get("url", "").startswith(("http://", "https://")):
            continue
        picked.append(s)
    return sorted(picked, key=rank)


def http_get(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=PROBE_TIMEOUT) as r:
        return r.status, r.geturl(), r.read(PROBE_BYTES).decode("utf-8", errors="replace")


def alive(url, get=http_get):
    """True when the URL serves an HLS playlist (and a master playlist's first variant does too)."""
    try:
        status, final, body = get(url)
        if status != 200 or not body.lstrip("\ufeff \r\n").startswith("#EXTM3U"):
            return False
        m = VARIANT.search(body)
        if not m:
            return "#EXTINF" in body or "#EXT-X-TARGETDURATION" in body
        status, _, sub = get(urllib.parse.urljoin(final, m.group(1).strip()))
        return status == 200 and sub.lstrip("\ufeff \r\n").startswith("#EXTM3U")
    except Exception:  # noqa: BLE001 - any network failure means "dead"
        return False


def choose(ranked, limit, is_alive, workers=16):
    """First `limit` live streams in rank order, one per channel (pure apart from is_alive)."""
    seen, firsts = set(), []
    for s in ranked:
        if s["channel"] not in seen:
            seen.add(s["channel"]); firsts.append(s)
    kept = []
    with cf.ThreadPoolExecutor(workers) as pool:
        for i in range(0, len(firsts), workers):
            batch = firsts[i:i + workers]
            for s, ok in zip(batch, pool.map(lambda x: is_alive(x["url"]), batch)):
                if ok:
                    kept.append(s)
            if len(kept) >= limit:
                break
    return kept[:limit]


def m3u(chosen, api):
    channels = {c["id"]: c for c in api["channels"]}
    logos = {}
    for lg in api["logos"]:
        if lg.get("feed") is None and lg.get("in_use", True):
            logos.setdefault(lg["channel"], lg["url"])
    lines = ["#EXTM3U"]
    for s in chosen:
        c = channels[s["channel"]]
        name = (c.get("name") or s.get("title") or s["channel"]).replace(",", " ")
        group = ((c.get("categories") or ["general"])[0]).title()
        logo = logos.get(c["id"], "")
        lines.append(f'#EXTINF:-1 tvg-id="{c["id"]}" tvg-logo="{logo}" group-title="{group}",{name}')
        lines.append(s["url"])
    return "\n".join(lines) + "\n"


def main(argv):
    out = argv[1] if len(argv) > 1 else os.path.join(ROOT, "out")
    local = argv[argv.index("--api") + 1] if "--api" in argv else None
    with open(CONFIG, encoding="utf-8") as f:
        cfg = json.load(f)
    api = load_api(local)
    os.makedirs(os.path.join(out, "curated"), exist_ok=True)
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    entries = []
    for cc in cfg["countries"]:
        ranked = candidates(api, cc)
        chosen = choose(ranked, cfg["per_country"], alive)
        path = os.path.join(out, "curated", f"{cc.lower()}.m3u")
        entry = {"country": cc, "file": f"curated/{cc.lower()}.m3u", "candidates": len(ranked),
                 "channels": len(chosen), "checked": now}
        if chosen:
            with open(path, "w", encoding="utf-8") as f:
                f.write(m3u(chosen, api))
            entry["ok"] = True
        else:
            entry["ok"] = False  # keep any previous file
        print(f"{cc}: {len(chosen)}/{cfg['per_country']} alive from {len(ranked)} candidates")
        entries.append(entry)
    with open(os.path.join(out, "curated", "index.json"), "w", encoding="utf-8") as f:
        json.dump({"version": 1, "generated": now, "source": "iptv-org (public domain)", "countries": entries}, f, indent=2)
        f.write("\n")
    if not any(e["ok"] for e in entries):
        sys.exit("no country produced a curated list")


if __name__ == "__main__":
    main(sys.argv)
