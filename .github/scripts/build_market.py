#!/usr/bin/env python3
"""Build the Playlist Market catalog into ./out (published to the 'market' branch).

Sources (.github/market/sources.json):
  * "playlists": curated single playlist files,
  * "repos":     repositories scanned for .m3u/.m3u8 files (e.g. iptv-org/iptv per country),
  * "search":    GitHub repository searches that discover new, active IPTV playlist repos.

Every candidate is downloaded, *repaired* (BOM / CRLF / missing #EXTM3U header, broken or
missing #EXTINF lines, blank titles, stray comments, URLs with spaces, duplicate streams) and
validated (must be an M3U channel list, not a single HLS stream or a web page). A small sample of
each list's streams is probed so the app can show how many respond. Output:
  out/index.json              catalog read by the app (id, name, category, channels, sha256, ...)
  out/playlists/<id>.m3u      repaired copy of each playlist
A playlist that fails to download keeps its last good copy, so installs never break.
"""
import concurrent.futures as cf
import hashlib
import json
import os
import random
import re
import sys
import time
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CONFIG = os.environ.get('MARKET_CONFIG') or os.path.join(ROOT, '.github', 'market', 'sources.json')
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "out-market")
REPO = os.environ.get("GITHUB_REPOSITORY", "siamform/yumeiro")
BRANCH = os.environ.get("MARKET_BRANCH", "market")
RAW = f"https://raw.githubusercontent.com/{REPO}/{BRANCH}"
TOKEN = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
UA = "Yumeiro-playlist-market/1.0 (+https://github.com/siamform/yumeiro)"
URL_RE = re.compile(r"^[a-zA-Z][a-zA-Z0-9+.-]*://\S+$")
KEEP_TAGS = ("#EXTVLCOPT", "#KODIPROP", "#EXTGRP", "#EXTHTTP", "#EXT-X-SESSION-KEY")
SKIP_PATH = re.compile(r"(^|/)(\.github|test|tests|backup|backups|old|archive|epg|sample|examples?)(/|$)", re.I)
NOW = datetime.now(timezone.utc)
STAMP = NOW.strftime("%Y-%m-%dT%H:%M:%SZ")


# ---------------------------------------------------------------------------------------------
# HTTP helpers

def http(url, *, api=False, timeout=60, max_bytes=None, headers=None):
    h = {"User-Agent": UA}
    if api:
        h["Accept"] = "application/vnd.github+json"
        if TOKEN:
            h["Authorization"] = f"Bearer {TOKEN}"
    if headers:
        h.update(headers)
    req = urllib.request.Request(url, headers=h)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        data = r.read((max_bytes + 1) if max_bytes else -1)
        if max_bytes and len(data) > max_bytes:
            raise ValueError("file too large")
        return r.status, r.geturl(), data


def api(path):
    for attempt in range(3):
        try:
            _, _, data = http("https://api.github.com/" + path.lstrip("/"), api=True, timeout=40)
            return json.loads(data)
        except urllib.error.HTTPError as e:
            if e.code in (403, 429) and attempt < 2:
                wait = int(e.headers.get("Retry-After") or 20)
                print(f"  rate limited, waiting {wait}s")
                time.sleep(min(wait, 60))
                continue
            raise
        except Exception:  # noqa: BLE001
            if attempt == 2:
                raise
            time.sleep(3 * (attempt + 1))


def fetch(url, max_bytes):
    last = None
    for attempt in range(3):
        try:
            return http(url, timeout=60, max_bytes=max_bytes)
        except urllib.error.HTTPError as e:
            if e.code in (401, 403, 404, 410, 451):
                raise
            last = e
        except Exception as e:  # noqa: BLE001
            last = e
        time.sleep(3 * (attempt + 1))
    raise last


# ---------------------------------------------------------------------------------------------
# Repair & analysis

ATTR = re.compile(r'([\w-]+)="([^"]*)"')


def _title_of(extinf):
    # Title = text after the last comma that is outside quotes.
    in_q, cut = False, -1
    for i, ch in enumerate(extinf):
        if ch == '"':
            in_q = not in_q
        elif ch == "," and not in_q:
            cut = i
    return (extinf[cut + 1:] if cut >= 0 else "").strip(), cut


def fix_extinf(line, fallback):
    body = re.sub(r"^#EXTINF\s*:?", "", line, flags=re.I).strip()
    if not re.match(r"^-?\d", body):
        body = "-1 " + body if body and not body.startswith(",") else "-1" + body
    line = "#EXTINF:" + body
    title, cut = _title_of(line)
    if cut < 0:
        line += "," + fallback
    elif not title:
        attrs = dict(ATTR.findall(line))
        line = line[:cut + 1] + (attrs.get("tvg-name") or fallback)
    return line


def name_from_url(url):
    p = urllib.parse.urlparse(url.split("|")[0])
    stem = os.path.basename(p.path.rstrip("/")) or p.netloc
    return re.sub(r"\.(m3u8?|ts|mp4|mpd)$", "", stem, flags=re.I) or "Channel"


def repair(raw):
    """Returns (fixed_text, channels, groups, issues_fixed). Raises ValueError for non-lists."""
    text = raw.decode("utf-8", errors="replace").lstrip("\ufeff").replace("\r\n", "\n").replace("\r", "\n")
    head = text.lstrip()[:512].lower()
    if head.startswith("<!doctype") or head.startswith("<html") or head.startswith("<?xml"):
        raise ValueError("not a playlist (web page / XML)")
    if "#EXT-X-TARGETDURATION" in text[:20000] or "#EXT-X-STREAM-INF" in text[:20000]:
        raise ValueError("single HLS stream, not a channel list")
    header = "#EXTM3U"
    fixed = 0
    entries, seen, groups = [], set(), {}
    extinf, extras = None, []
    lines = text.split("\n")
    first = next((l.strip() for l in lines if l.strip()), "")
    if first.upper().startswith("#EXTM3U"):
        header = first
    else:
        fixed += 1
    for raw_line in lines:
        line = raw_line.strip()
        if not line:
            continue
        up = line.upper()
        if up.startswith("#EXTM3U"):
            continue
        if up.startswith("#EXTINF"):
            if extinf is not None:
                fixed += 1  # previous #EXTINF had no URL
            extinf, extras = line, []
            continue
        if line.startswith("#"):
            if up.startswith(KEEP_TAGS):
                extras.append(line)
            continue
        url = line
        if " " in url and URL_RE.match(url.replace(" ", "%20")):
            url, fixed = url.replace(" ", "%20"), fixed + 1
        if not URL_RE.match(url):
            fixed += 1
            extinf, extras = None, []
            continue
        title, _ = _title_of(extinf or "")
        key = (url.split("|")[0], title.lower())
        if key in seen:
            fixed += 1
            extinf, extras = None, []
            continue
        seen.add(key)
        fallback = name_from_url(url)
        if extinf is None:
            fixed += 1
            line_inf = f"#EXTINF:-1,{fallback}"
        else:
            line_inf = fix_extinf(extinf, fallback)
            if line_inf != extinf:
                fixed += 1
        g = dict(ATTR.findall(line_inf)).get("group-title", "").strip()
        if not g:
            g = next((e.split(":", 1)[1].strip() for e in extras if e.upper().startswith("#EXTGRP:")), "")
        if g:
            groups[g] = groups.get(g, 0) + 1
        entries.append((line_inf, extras, url))
        extinf, extras = None, []
    out = [header]
    for inf, ex, url in entries:
        out.append(inf)
        out.extend(ex)
        out.append(url)
    return "\n".join(out) + "\n", entries, groups, fixed


def probe(url):
    """True when a stream URL answers (status < 400) within a few seconds."""
    target, _, pipe = url.partition("|")
    headers = {"Range": "bytes=0-2047"}
    for part in pipe.split("&"):
        k, _, v = part.partition("=")
        if k and v:
            headers[k.strip()] = urllib.parse.unquote(v.strip())
    if not target.lower().startswith(("http://", "https://")):
        return None
    try:
        req = urllib.request.Request(target, headers={"User-Agent": headers.pop("User-Agent", "Mozilla/5.0"), **headers})
        with urllib.request.urlopen(req, timeout=7) as r:
            r.read(64)
            return r.status < 400
    except urllib.error.HTTPError as e:
        return e.code < 400
    except Exception:  # noqa: BLE001
        return False


# ---------------------------------------------------------------------------------------------
# Discovery

def slug(*parts):
    s = "-".join(p for p in parts if p)
    s = re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-")
    return s[:64] or "playlist"


def guess_category(text):
    t = text.lower()
    for words, cat in (
        (("bdix", "bangla", "bangladesh", "toffee", "tsports", "t-sports"), "Bangladesh"),
        (("sport", "cricket", "football", "soccer", "f1", "nba", "ufc"), "Sports"),
        (("movie", "film", "cinema", "vod"), "Movies"),
        (("india", "hindi", "tamil", "telugu", "jio", "yupp"), "Indian TV"),
        (("kids", "cartoon", "anime"), "Kids"),
        (("news",), "News"),
        (("music", "radio"), "Music & radio"),
        (("pluto", "samsung", "plex", "roku", "tubi", "xumo", "fast"), "Global TV"),
    ):
        if any(w in t for w in words):
            return cat
    return "Community"


def countries():
    try:
        _, _, data = http("https://iptv-org.github.io/api/countries.json", timeout=30)
        return {c["code"].lower(): c["name"] for c in json.loads(data)}
    except Exception as e:  # noqa: BLE001
        print(f"  (country names unavailable: {e})")
        return {}


def repo_files(full, branch, paths=None):
    tree = api(f"repos/{full}/git/trees/{urllib.parse.quote(branch)}?recursive=1")
    files = []
    for t in tree.get("tree", []):
        p = t.get("path", "")
        if t.get("type") != "blob" or not re.search(r"\.m3u8?$", p, re.I):
            continue
        if paths and not any(p.startswith(x) for x in paths):
            continue
        if SKIP_PATH.search(p):
            continue
        files.append((p, t.get("size", 0)))
    return files


def rank_file(path, size):
    name = os.path.basename(path).lower()
    score = -path.count("/") * 3
    if re.search(r"(playlist|index|combined|all|main|live|channels|tv)", name):
        score += 4
    if size < 2048:
        score -= 5
    return score + min(size, 2_000_000) / 1_000_000


def discover(cfg, limits):
    cands = []
    for p in cfg.get("playlists", []):
        cands.append(dict(p, source=p["url"], origin="curated", stars=None))
    meta_cache = {}

    def repo_meta(full):
        if full not in meta_cache:
            try:
                meta_cache[full] = api(f"repos/{full}")
            except Exception as e:  # noqa: BLE001
                print(f"  repo {full}: {e}")
                meta_cache[full] = None
        return meta_cache[full]

    for p in cands:
        m = repo_meta(p["repo"]) if p.get("repo") else None
        if m:
            p["stars"] = m.get("stargazers_count")

    names = None
    for r in cfg.get("repos", []):
        meta = repo_meta(r["repo"])
        if not meta:
            continue
        branch = meta["default_branch"]
        files = repo_files(r["repo"], branch, r.get("paths"))
        if r.get("countryFiles") and names is None:
            names = countries()
        for path, size in sorted(files)[: r.get("max", 100)]:
            stem = os.path.splitext(os.path.basename(path))[0]
            code, _, variant = stem.partition("_")
            if r.get("countryFiles") and names and code.lower() in names:
                nm = names[code.lower()] + (f" · {variant.replace('_', ' ').title()}" if variant else "")
                cat = "Country"
                country = code.upper()
            else:
                nm, cat, country = stem.replace("_", " ").replace("-", " ").title(), r.get("category", "Community"), None
            cands.append({
                "id": slug(r.get("namePrefix", r["repo"]), stem), "name": nm, "category": cat, "country": country,
                "description": f"{nm} channels from {r['repo']}", "repo": r["repo"], "stars": meta.get("stargazers_count"),
                "source": f"https://raw.githubusercontent.com/{r['repo']}/{branch}/{path}", "origin": "repo", "size": size,
            })

    s = cfg.get("search") or {}
    if s.get("queries"):
        since = (NOW - timedelta(days=s.get("pushedWithinDays", 45))).strftime("%Y-%m-%d")
        known = {c["repo"].lower() for c in cands if c.get("repo")} | {x.lower() for x in s.get("exclude", [])}
        found = {}
        for q in s["queries"]:
            query = f"{q} stars:>={s.get('minStars', 10)} pushed:>={since} fork:false archived:false"
            try:
                res = api("search/repositories?" + urllib.parse.urlencode({"q": query, "sort": "stars", "per_page": 30}))
            except Exception as e:  # noqa: BLE001
                print(f"  search '{q}': {e}")
                continue
            for it in res.get("items", []):
                if it["full_name"].lower() not in known:
                    found[it["full_name"]] = it
            time.sleep(2.5)  # search API: 30 requests/minute
        repos = sorted(found.values(), key=lambda x: -x.get("stargazers_count", 0))[: s.get("maxRepos", 40)]
        print(f"search: {len(found)} new repos, scanning {len(repos)}")
        for it in repos:
            full = it["full_name"]
            try:
                files = repo_files(full, it["default_branch"])
            except Exception as e:  # noqa: BLE001
                print(f"  {full}: {e}")
                continue
            files = [f for f in files if 1024 <= f[1] <= limits["maxFileBytes"]]
            files.sort(key=lambda f: -rank_file(*f))
            take = files[: s.get("filesPerRepo", 3)]
            for path, size in take:
                stem = os.path.splitext(os.path.basename(path))[0]
                title = it["name"].replace("-", " ").replace("_", " ").strip()
                nm = title if len(take) == 1 else f"{title} · {stem.replace('_', ' ').replace('-', ' ')}"
                desc = (it.get("description") or "").strip()[:200]
                cands.append({
                    "id": slug(full.replace("/", "-"), stem if len(take) > 1 else ""), "name": nm[:80],
                    "category": guess_category(" ".join([full, desc, path, " ".join(it.get("topics") or [])])),
                    "description": desc or f"Playlist from {full}", "repo": full, "stars": it.get("stargazers_count"),
                    "source": f"https://raw.githubusercontent.com/{full}/{it['default_branch']}/{urllib.parse.quote(path)}",
                    "origin": "search", "size": size,
                })
    # Unique ids, curated first.
    out, ids = [], set()
    for c in cands:
        if c["id"] in ids:
            continue
        ids.add(c["id"])
        out.append(c)
    return out


# ---------------------------------------------------------------------------------------------

def main():
    with open(CONFIG, encoding="utf-8") as f:
        cfg = json.load(f)
    limits = {"maxEntries": 400, "maxFileBytes": 25_000_000, "minChannels": 3, "healthSamples": 8}
    limits.update(cfg.get("limits", {}))
    only = set(filter(None, os.environ.get("MARKET_ONLY", "").split(",")))
    os.makedirs(os.path.join(OUT, "playlists"), exist_ok=True)
    old = {}
    try:
        with open(os.path.join(OUT, "index.json"), encoding="utf-8") as f:
            old = {p["id"]: p for p in json.load(f).get("playlists", [])}
    except Exception:  # noqa: BLE001
        pass

    cands = discover(cfg, limits)
    if only:
        cands = [c for c in cands if c["id"] in only or c.get("origin") in only]
    print(f"{len(cands)} candidates")

    def build(c):
        try:
            _, final, data = fetch(c["source"], limits["maxFileBytes"])
            text, entries, groups, fixed = repair(data)
            if len(entries) < limits["minChannels"]:
                raise ValueError(f"only {len(entries)} streams")
            return c, (text, entries, groups, fixed, final), None
        except Exception as e:  # noqa: BLE001
            return c, None, str(e)[:200]

    results = []
    with cf.ThreadPoolExecutor(16) as ex:
        for c, ok, err in ex.map(build, cands):
            results.append((c, ok, err))

    # Health: probe a few streams per list (from GitHub's servers — region-locked lists such as
    # BDIX look "down" here even when they work in their country, so this is informational only).
    jobs = {}
    rnd = random.Random(42)
    for c, ok, _ in results:
        if ok:
            urls = [u for _, _, u in ok[1]]
            n = min(limits["healthSamples"], len(urls))
            jobs[c["id"]] = rnd.sample(urls, n) if n else []
    health = {}
    with cf.ThreadPoolExecutor(48) as ex:
        flat = [(i, u) for i, us in jobs.items() for u in us]
        for (i, _), alive in zip(flat, ex.map(lambda t: probe(t[1]), flat)):
            if alive is None:
                continue
            h = health.setdefault(i, [0, 0])
            h[1] += 1
            h[0] += 1 if alive else 0

    entries, seen_sha, failed = [], {}, 0
    for c, ok, err in results:
        sid = c["id"]
        path = os.path.join(OUT, "playlists", f"{sid}.m3u")
        prev = old.get(sid, {})
        e = {k: c[k] for k in ("id", "name", "category", "country", "description", "repo", "stars", "origin") if c.get(k) is not None}
        e["source"] = c["source"]
        e["url"] = f"{RAW}/playlists/{sid}.m3u"
        if ok:
            text, chans, groups, fixed, final = ok
            data = text.encode("utf-8")
            digest = hashlib.sha256(data).hexdigest()
            if digest in seen_sha:
                print(f"- {sid}: duplicate of {seen_sha[digest]}, skipped")
                continue
            seen_sha[digest] = sid
            if digest != prev.get("sha256") or not os.path.exists(path):
                with open(path, "wb") as f:
                    f.write(data)
                e["updated"] = STAMP
            else:
                e["updated"] = prev.get("updated", STAMP)
            top = sorted(groups.items(), key=lambda kv: -kv[1])[:8]
            e.update(sha256=digest, channels=len(chans), groups=len(groups), topGroups=[g for g, _ in top],
                     bytes=len(data), fixed=fixed, checked=STAMP, ok=True)
            if sid in health:
                e["alive"], e["sampled"] = health[sid]
            print(f"- {sid}: {len(chans)} streams, {len(groups)} groups, {fixed} fixes, health {health.get(sid)}")
        elif prev and os.path.exists(path):
            failed += 1
            e.update({k: prev[k] for k in ("sha256", "channels", "groups", "topGroups", "bytes", "updated", "fixed", "alive", "sampled") if k in prev})
            e.update(ok=False, error=err, checked=STAMP)
            print(f"- {sid}: FAILED ({err}), keeping last good copy")
        else:
            failed += 1
            print(f"- {sid}: FAILED ({err}), not listed")
            continue
        entries.append(e)

    origin_rank = {"curated": 0, "repo": 1, "search": 2}
    curated = [e for e in entries if e.get("origin") == "curated"]
    rest = sorted((e for e in entries if e.get("origin") != "curated"),
                  key=lambda e: (origin_rank.get(e.get("origin"), 3), -(e.get("stars") or 0), e["name"].lower()))
    entries = (curated + rest)[: limits["maxEntries"]]
    keep = {f"{e['id']}.m3u" for e in entries}
    for name in os.listdir(os.path.join(OUT, "playlists")):
        if name not in keep:
            os.remove(os.path.join(OUT, "playlists", name))

    cats = {}
    for e in entries:
        cats[e["category"]] = cats.get(e["category"], 0) + 1
    index = {"version": 1, "generated": STAMP, "repo": REPO, "branch": BRANCH,
             "categories": sorted(cats, key=lambda c: (-cats[c], c)), "playlists": entries}
    with open(os.path.join(OUT, "index.json"), "w", encoding="utf-8") as f:
        json.dump(index, f, indent=1, ensure_ascii=False)
        f.write("\n")
    print(f"published={len(entries)} failed={failed}")
    if not entries:
        sys.exit("no playlists could be built")


if __name__ == "__main__":
    main()
