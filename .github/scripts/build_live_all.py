#!/usr/bin/env python3
"""E2-ALL (owner 2026-11-22): "Yumeiro Live", one all-in-one Live TV playlist.

Data: the iptv-org database API (public domain; it carries NSFW flags and a DMCA / NSFW blocklist).
Market playlists are NOT merged: some of them carry pirated sports streams (legal-only rule).

  * E2-ALL-A merge: every country, NSFW / closed / blocklisted channels dropped, streams that need
    extra headers dropped, adult-looking groups and names dropped, one stream per channel
    (dedupe by channel id, then by normalized name + country), grouped by country.
  * E2-ALL-B checker: each candidate is probed (HLS playlist answers). State lives in
    <out>/live/state.json: a stream that fails 3 runs in a row is removed; it comes back as soon
    as it passes again. A stream that never passed is not shipped.

Writes <out>/playlists/yumeiro_live.m3u, <out>/live/state.json, <out>/live/report.json and adds or
replaces the "yumeiro_live" entry in <out>/index.json (the file the app reads on Sync).
Usage: build_live_all.py <out dir> [--api <dir with the four json files>] [--no-probe]
"""
import concurrent.futures as cf
import hashlib, json, os, re, sys
from datetime import datetime, timezone

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import curate_live as cl  # noqa: E402  same probe and API loader as the curated lists

ID = "yumeiro_live"
STRIKES = 3
PER_CHANNEL = 2  # streams tried per channel, best ranked first
WORKERS = 48
MIRROR = "https://raw.githubusercontent.com/siamform/yumeiro/streams"
ADULT = re.compile(r"\b(xxx|adult|porn\w*|erotic\w*|18\+|sex\w*|hentai|playboy|nsfw)\b", re.I)
SPACES = re.compile(r"[^a-z0-9]+")
# Xtream-Codes style panel links carry a reseller's user + password in the path: paid IPTV resold, not free TV.
XTREAM_LIVE = re.compile(r"/live/[^/]+/[^/]+/\d+")


def norm(name):
    return SPACES.sub("", (name or "").lower())


def merged(api):
    """Ranked candidate streams per channel for all countries (pure; unit-tested)."""
    blocked = {b["channel"] for b in api["blocklist"]}
    channels = {c["id"]: c for c in api["channels"]}
    by_channel = {}
    for s in api["streams"]:
        c = channels.get(s.get("channel"))
        if not c or c.get("is_nsfw") or c.get("closed") or c["id"] in blocked:
            continue
        if s.get("user_agent") or s.get("referrer"):
            continue
        if not s.get("url", "").startswith(("http://", "https://")):
            continue
        if XTREAM_LIVE.search(s["url"]):
            continue
        cats = " ".join(c.get("categories") or [])
        if ADULT.search(c.get("name") or "") or ADULT.search(s.get("title") or "") or "xxx" in cats:
            continue
        by_channel.setdefault(c["id"], []).append(s)
    out, names = {}, set()
    for cid in sorted(by_channel):
        c = channels[cid]
        key = (norm(c.get("name")), c.get("country"))
        if key in names:
            continue  # same channel listed twice under different ids
        names.add(key)
        out[cid] = sorted(by_channel[cid], key=cl.rank)[:PER_CHANNEL]
    return out


def update_state(state, results, now):
    """results: url -> alive. Returns the new state (pure; unit-tested)."""
    new = {}
    for url, ok in results.items():
        old = state.get(url, {})
        if ok:
            new[url] = {"fails": 0, "passed": now}
        else:
            new[url] = {"fails": old.get("fails", 0) + 1, "passed": old.get("passed")}
    return new


def shippable(entry):
    return bool(entry and entry.get("passed")) and entry.get("fails", 0) < STRIKES


def pick(cands, state):
    """One stream per channel: the best ranked one that is shippable (pure; unit-tested)."""
    chosen = []
    for cid, streams in cands.items():
        for s in streams:
            if shippable(state.get(s["url"])):
                chosen.append(s)
                break
    return chosen


def m3u(chosen, api):
    channels = {c["id"]: c for c in api["channels"]}
    countries = {c["code"]: c["name"] for c in api.get("countries", [])}
    logos = {}
    for lg in api["logos"]:
        if lg.get("feed") is None and lg.get("in_use", True):
            logos.setdefault(lg["channel"], lg["url"])
    rows = []
    for s in chosen:
        c = channels[s["channel"]]
        cc = c.get("country") or ""
        group = countries.get(cc, cc or "International")
        cat = ((c.get("categories") or ["general"])[0]).title()
        name = (c.get("name") or s.get("title") or s["channel"]).replace(",", " ")
        rows.append((group, name.lower(), f'#EXTINF:-1 tvg-id="{c["id"]}" tvg-country="{cc}" tvg-logo="{logos.get(c["id"], "")}" '
                     f'group-title="{group}" tvg-category="{cat}",{name}', s["url"]))
    rows.sort()
    lines = ["#EXTM3U"]
    for _, _, inf, url in rows:
        lines += [inf, url]
    return "\n".join(lines) + "\n"


def load_api(local):
    api = cl.load_api(local)
    try:
        if local:
            with open(os.path.join(local, "countries.json"), encoding="utf-8") as f:
                api["countries"] = json.load(f)
        else:
            import urllib.request
            req = urllib.request.Request(f"{cl.API}/countries.json", headers={"User-Agent": cl.UA})
            with urllib.request.urlopen(req, timeout=60) as r:
                api["countries"] = json.load(r)
    except Exception:  # noqa: BLE001 - country names are cosmetic; codes are used instead
        api["countries"] = []
    return api


def main(argv):
    out = argv[1] if len(argv) > 1 else "out"
    local = argv[argv.index("--api") + 1] if "--api" in argv else None
    probe = "--no-probe" not in argv
    api = load_api(local)
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    os.makedirs(os.path.join(out, "live"), exist_ok=True)
    os.makedirs(os.path.join(out, "playlists"), exist_ok=True)
    state_path = os.path.join(out, "live", "state.json")
    try:
        with open(state_path, encoding="utf-8") as f:
            state = json.load(f).get("streams", {})
    except (OSError, ValueError):
        state = {}
    cands = merged(api)
    urls = [s["url"] for streams in cands.values() for s in streams]
    if probe:
        with cf.ThreadPoolExecutor(WORKERS) as pool:
            results = dict(zip(urls, pool.map(cl.alive, urls)))
    else:
        results = {u: True for u in urls}
    state = update_state(state, results, now)
    chosen = pick(cands, state)
    passed = sum(1 for ok in results.values() if ok)
    removed = sum(1 for e in state.values() if e.get("passed") and e["fails"] >= STRIKES)
    print(f"Yumeiro Live: {len(cands)} channels, {len(urls)} streams probed, {passed} alive, "
          f"{len(chosen)} shipped, {removed} removed after {STRIKES} strikes")
    if len(chosen) < 100:
        sys.exit(f"only {len(chosen)} channels alive; keeping the previous list")
    body = m3u(chosen, api)
    with open(os.path.join(out, "playlists", f"{ID}.m3u"), "w", encoding="utf-8") as f:
        f.write(body)
    with open(state_path, "w", encoding="utf-8") as f:
        json.dump({"version": 1, "checked": now, "streams": state}, f, separators=(",", ":"))
    report = {"checked": now, "channels": len(cands), "probed": len(urls), "alive": passed,
              "shipped": len(chosen), "removed": removed}
    with open(os.path.join(out, "live", "report.json"), "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)
    raw = body.encode("utf-8")
    ch = {c["id"]: c for c in api["channels"]}
    entry = {"id": ID, "builder": "build_live_all", "name": "Yumeiro Live", "category": "Live TV",
             "description": "All-in-one free Live TV: every country, checked every run, dead channels removed",
             "repo": "iptv-org/iptv", "source": "https://iptv-org.github.io/iptv/index.m3u",
             "url": f"{MIRROR}/playlists/{ID}.m3u", "updated": now, "sha256": hashlib.sha256(raw).hexdigest(),
             "channels": len(chosen), "groups": len({ch[s["channel"]].get("country") for s in chosen}),
             "bytes": len(raw), "checked": now, "ok": True}
    index_path = os.path.join(out, "index.json")
    try:
        with open(index_path, encoding="utf-8") as f:
            index = json.load(f)
    except (OSError, ValueError):
        index = {"version": 1, "repo": "siamform/yumeiro", "branch": "streams", "playlists": []}
    index["playlists"] = [p for p in index.get("playlists", []) if p.get("id") != ID] + [entry]
    index["generated"] = now
    with open(index_path, "w", encoding="utf-8") as f:
        json.dump(index, f, indent=2)
        f.write("\n")


if __name__ == "__main__":
    main(sys.argv)
