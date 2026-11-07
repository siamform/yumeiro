#!/usr/bin/env python3
"""Builds the in-app notification feed (feed.json) for the 'feed' branch.

Usage: build_feed.py <out_dir>

<out_dir> holds the previous feed.json/state.json (if any). Sources, read with the
workflow's read-only GITHUB_TOKEN:
  - extensions/index.json on the 'extensions' branch -> "N new extensions"
  - index.json on the 'market' branch                -> "N new stream playlists"
  - published GitHub releases                        -> "Yumeiro vX is out"
  - .github/feed/announcements.json on main          -> hand-written news
The first run only records a baseline (plus the latest release and announcements),
so users are never flooded. Only the newest MAX_ITEMS items are kept.
"""
import datetime as dt
import hashlib
import json
import os
import re
import sys
import urllib.request

MAX_ITEMS = 50
REPO = os.environ.get("GITHUB_REPOSITORY", "dev-asfakul/yumeiro")
TOKEN = os.environ.get("GITHUB_TOKEN", "")
ACTIONS = {"update", "extensions", "market", "streams", "support"}


def api(path, raw=False):
    req = urllib.request.Request(f"https://api.github.com/repos/{REPO}/{path}")
    req.add_header("Accept", "application/vnd.github.raw" if raw else "application/vnd.github+json")
    req.add_header("X-GitHub-Api-Version", "2022-11-28")
    if TOKEN:
        req.add_header("Authorization", f"Bearer {TOKEN}")
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.loads(r.read().decode("utf-8"))


def safe(fn, default):
    try:
        return fn()
    except Exception as e:  # a missing source must never break the feed
        print(f"::warning::{e}")
        return default


def now_iso():
    return dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def to_ms(v):
    if isinstance(v, (int, float)):
        return int(v)
    try:
        return int(dt.datetime.fromisoformat(str(v).replace("Z", "+00:00")).timestamp() * 1000)
    except ValueError:
        return 0


def clean(text, n):
    text = re.sub(r"<!--.*?-->", "", text or "", flags=re.S)
    text = re.sub(r"[#*_`>\[\]]", "", text)
    text = re.sub(r"\(https?://[^)]+\)", "", text)
    text = " ".join(text.split())
    return text[:n].rstrip()


def safe_link(v):
    v = (v or "").strip()
    return v if v in ACTIONS or (v.startswith("https://") and len(v) < 400) else None


def label(name):
    """Plugin/playlist name without leading status emoji or symbols."""
    return re.sub(r"^[^\w]+", "", str(name)).strip() or str(name)


def names_body(names, noun):
    shown = ", ".join(names[:5])
    more = len(names) - 5
    return f"{shown} and {more} more {noun}" if more > 0 else shown


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    state_path, feed_path = os.path.join(out, "state.json"), os.path.join(out, "feed.json")
    state = safe(lambda: json.load(open(state_path)), None) if os.path.exists(state_path) else None
    first = state is None
    state = state or {}
    items = safe(lambda: json.load(open(feed_path)).get("items", []), []) if os.path.exists(feed_path) else []
    known = {i["id"] for i in items}
    stamp = now_iso()
    new = []

    def add(item):
        if item["id"] not in known:
            known.add(item["id"])
            new.append(item)

    # Extensions (skip 18+ and duplicates of the same plugin from other repos).
    ext = safe(lambda: api("contents/index.json?ref=extensions", raw=True), {}).get("extensions", [])
    ext_now = {}
    for e in ext:
        if e.get("primary", True) and "NSFW" not in [t.upper() for t in e.get("tvTypes") or []] and e.get("status", 1) != 0:
            ext_now.setdefault(e.get("internalName") or e.get("name"), label(e.get("name") or e.get("internalName")))
    if ext_now:
        before = set(state.get("extensions", []))
        added = sorted(ext_now[k] for k in ext_now if k not in before)
        if added and not first:
            h = hashlib.sha1("|".join(added).encode()).hexdigest()[:10]
            add({"id": f"ext-{h}", "kind": "extension",
                 "title": f"{added[0]} is in the extension market" if len(added) == 1 else f"{len(added)} new extensions",
                 "body": names_body(added, "extensions"), "at": stamp, "link": "market"})
        state["extensions"] = sorted(ext_now)

    # Stream playlists.
    pls = safe(lambda: api("contents/index.json?ref=market", raw=True), {}).get("playlists", [])
    pl_now = {p["id"]: label(p.get("name", p["id"])) for p in pls if p.get("id")}
    if pl_now:
        before = set(state.get("playlists", []))
        added = sorted(pl_now[k] for k in pl_now if k not in before)
        if added and not first:
            h = hashlib.sha1("|".join(added).encode()).hexdigest()[:10]
            add({"id": f"pl-{h}", "kind": "playlist",
                 "title": f"New stream playlist: {added[0]}" if len(added) == 1 else f"{len(added)} new stream playlists",
                 "body": names_body(added, "playlists"), "at": stamp, "link": "streams"})
        state["playlists"] = sorted(pl_now)

    # Releases (newest only on the first run).
    rels = safe(lambda: api("releases?per_page=5"), []) or [safe(lambda: api("releases/latest"), None)]
    rels = [r for r in rels if r and not r.get("draft") and not r.get("prerelease")]
    for r in (rels[:1] if first else rels):
        tag = r.get("tag_name", "")
        if tag and tag not in state.get("releases", []):
            add({"id": f"release-{tag}", "kind": "release", "title": f"Yumeiro {tag} is out",
                 "body": clean(r.get("body") or r.get("name"), 280) or "A new version is ready.",
                 "at": r.get("published_at") or stamp, "link": "update"})
    state["releases"] = sorted({*state.get("releases", []), *[r.get("tag_name") for r in rels if r.get("tag_name")]})[-30:]

    # Hand-written announcements.
    ann = safe(lambda: json.load(open(".github/feed/announcements.json")).get("items", []), [])
    for a in ann:
        aid, title = str(a.get("id", "")).strip(), str(a.get("title", "")).strip()
        if not aid or not title:
            continue
        item = {"id": f"news-{aid}"[:120], "kind": a.get("kind", "news"), "title": title[:120],
                "body": str(a.get("body", ""))[:400], "at": a.get("at") or stamp}
        link = safe_link(a.get("link"))
        if link:
            item["link"] = link
        add(item)

    items = sorted(items + new, key=lambda i: to_ms(i.get("at")), reverse=True)[:MAX_ITEMS]
    feed = {"version": 1, "generated": stamp, "items": items}
    json.dump(feed, open(feed_path, "w"), indent=1, ensure_ascii=False)
    json.dump(state, open(state_path, "w"), indent=1, ensure_ascii=False)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a") as f:
            f.write(f"Feed: {len(items)} items, {len(new)} new{' (baseline run)' if first else ''}\n")
            for i in new:
                f.write(f"- {i['kind']}: {i['title']}\n")
    print(f"{len(new)} new, {len(items)} total")


if __name__ == "__main__":
    main()
