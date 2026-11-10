#!/usr/bin/env python3
# Yumeiro — Copyright (C) 2026 siamform
# Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
# Do not remove or alter this notice.
"""Builds the in-app markets for Aniyomi, Mihon / Tachiyomi, Mangayomi (JavaScript) and Stremio.

Output: <out>/sources.json, published on the same `extensions` branch as the CloudStream market.
The app reads https://raw.githubusercontent.com/<repo>/extensions/sources.json. No server involved.

Only links are listed. Nothing is downloaded, mirrored or hosted, and nothing is added for the user:
they pick a repository / add-on and the app adds it the same way as a pasted link.

Discovery
  Aniyomi / Mihon / Mangayomi:
    1. GitHub repository search (topics and keywords, sorted by stars),
    2. "Add repo" links (aniyomi:// tachiyomi:// mihon:// mangayomi://, or raw index URLs) in each
       repository's README, which usually point at the published index,
    3. the usual publish locations (branch `repo` or the default branch, index.min.json / index.json),
    4. extra seeds and the `blocked` list from .github/extensions/sources.json.
    Each index is downloaded and parsed; repos are ranked by stars, last update and extension count,
    identical indexes (forks, mirrors) are merged, 18+ entries are counted so the app can hide them.
  Stremio:
    Stremio's own add-on collection (featured) + the stremio-addons.net community catalog. Every
    manifest is fetched; add-ons that don't answer, or need torrents only, are left out.

Usage: build_source_market.py <out-dir>
Env:   GITHUB_TOKEN (search + repo metadata), EXT_CONFIG (default .github/extensions/sources.json)
"""
import concurrent.futures as cf
import hashlib, json, math, os, re, subprocess, sys, threading, time, urllib.error, urllib.parse, urllib.request
from datetime import datetime, timezone

OUT = sys.argv[1] if len(sys.argv) > 1 else "out-src"
CONFIG = os.environ.get("EXT_CONFIG", ".github/extensions/sources.json")
REPO = os.environ.get("GITHUB_REPOSITORY", "siamform/yumeiro")
TOKEN = os.environ.get("GITHUB_TOKEN", "")
UA = "Yumeiro-source-market/1.0 (+https://github.com/%s)" % REPO
NOW = datetime.now(timezone.utc)
STALE_DAYS = 730          # repos not updated for 2 years are left out
PER_KIND = 80             # repositories kept per kind
CANDIDATES = 160          # GitHub repos inspected per kind
GH_LOCK = threading.Lock()

SEARCH = {
    "aniyomi": ["topic:aniyomi-extensions", "topic:aniyomi", "aniyomi extensions in:name,description",
                "aniyomi repo in:name,description", "anime extensions tachiyomi in:description"],
    "mihon": ["topic:mihon", "topic:tachiyomi-extensions", "topic:mihon-extensions", "topic:tachiyomi",
              "mihon extensions in:name,description", "tachiyomi extensions in:name,description"],
    "mangayomi": ["topic:mangayomi", "topic:mangayomi-extensions", "mangayomi extensions in:name,description",
                  "mangayomi sources in:name,description"],
}
ADD_LINK = re.compile(r"""(?:aniyomi|tachiyomi|mihon|mangayomi)://add-repo\?[^\s"'<>)\]]+""", re.I)
RAW_INDEX = re.compile(r"""https://[^\s"'<>)\]]+?/(?:index\.min\.json|index\.json|anime_index\.json)""", re.I)


def log(*a):
    print(*a, flush=True)


def fetch(url, limit=8 << 20, gh=False, tries=2, timeout=25):
    headers = {"User-Agent": UA, "Accept": "application/vnd.github+json" if gh else "*/*"}
    if gh and TOKEN:
        headers["Authorization"] = "Bearer " + TOKEN
    last = None
    for attempt in range(tries):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=timeout) as r:
                data = r.read(limit + 1)
                if len(data) > limit:
                    raise ValueError("larger than %d bytes" % limit)
                return data
        except urllib.error.HTTPError as e:
            last = e
            if e.code in (403, 429) and gh:
                reset = int(e.headers.get("x-ratelimit-reset", "0") or 0)
                wait = max(5, min(65, reset - int(time.time()) + 2)) if reset else 30
                log("  rate limited, waiting %ds" % wait)
                time.sleep(wait)
                continue
            if e.code in (400, 401, 403, 404, 410, 451):
                break
        except Exception as e:  # network hiccup
            last = e
        time.sleep(1 + attempt)
    raise RuntimeError("%s: %s" % (url, last))


def fetch_json(url, **kw):
    return json.loads(fetch(url, **kw).decode("utf-8", "replace"))


def gh_api(path):
    with GH_LOCK:  # search allows 30 requests a minute; keep GitHub calls sequential and spaced
        time.sleep(2.1 if path.startswith("search/") else 0.05)
        if os.environ.get("USE_GH_CLI"):  # local testing: let the gh CLI authenticate
            out = subprocess.run(["gh", "api", path], capture_output=True, timeout=60)
            if out.returncode:
                raise RuntimeError("gh api %s: %s" % (path, out.stderr.decode()[:200]))
            return json.loads(out.stdout)
        return fetch_json("https://api.github.com/" + path, gh=True, limit=4 << 20)


def days_since(iso):
    try:
        return max(0.0, (NOW - datetime.strptime(iso, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)).days)
    except Exception:
        return 9999.0


def github_of(url):
    m = re.match(r"https://raw\.githubusercontent\.com/([^/]+)/([^/]+)/", url) or re.match(r"https://github\.com/([^/]+)/([^/]+)", url)
    return ("%s/%s" % (m.group(1), m.group(2).removesuffix(".git"))).lower() if m else None


def to_raw(url):
    """github.com blob/raw links -> raw.githubusercontent.com."""
    m = re.match(r"https://github\.com/([^/]+)/([^/]+)/(?:blob|raw)/(.+)$", url)
    return "https://raw.githubusercontent.com/%s/%s/%s" % m.groups() if m else url


# ---------- index parsing ----------

# Repos that moved to a newer format (e.g. Mihon's index.pb) leave placeholder entries in index.min.json.
STUB = re.compile(r"^(?:outdated app|update to |please update)", re.I)


def parse_tachiyomi(data):
    """index.min.json -> (kind, entries) or None. Kind from package names: '.animeextension.' = Aniyomi."""
    if not isinstance(data, list) or not data:
        return None
    rows = [e for e in data if isinstance(e, dict) and isinstance(e.get("pkg"), str) and isinstance(e.get("apk"), str)
            and not STUB.match(str(e.get("name", "")))]
    if not rows:
        return None
    anime = sum(".animeextension." in e["pkg"] for e in rows)
    kind = "aniyomi" if anime * 2 >= len(rows) else "mihon"
    out = [{"name": str(e.get("name", ""))[:80].removeprefix("Aniyomi: ").removeprefix("Tachiyomi: "),
            "lang": str(e.get("lang", ""))[:8], "nsfw": bool(e.get("nsfw"))} for e in rows]
    return kind, out


V2_WARN = {"CONTENT_WARNING_NSFW": True, 3: True, "3": True}


def parse_tachiyomi_v2(data):
    """TachiyomiX v2 index.json (Keiyoushi, extensions-lib 1.6) -> ("mihon", entries) or None."""
    if not isinstance(data, dict):
        return None
    lst = data.get("extensionList")
    rows = (lst or {}).get("extensions") if isinstance(lst, dict) else data.get("extensions")
    if not isinstance(rows, list):
        return None
    out = []
    for e in rows:
        if not isinstance(e, dict) or not isinstance(e.get("packageName"), str):
            continue
        if not isinstance((e.get("resources") or {}).get("apkUrl"), str) or STUB.match(str(e.get("name", ""))):
            continue
        langs = sorted({str(s.get("language", ""))[:8] for s in e.get("sources") or [] if isinstance(s, dict)} - {""})
        out.append({"name": str(e.get("name", ""))[:80], "lang": langs[0] if len(langs) == 1 else "all",
                    "nsfw": bool(V2_WARN.get(e.get("contentWarning")))})
    if not out:
        return None
    anime = sum(".animeextension." in e["packageName"] for e in rows if isinstance(e, dict) and isinstance(e.get("packageName"), str))
    return ("aniyomi" if anime * 2 >= len(out) else "mihon"), out


def parse_mangayomi(data):
    if not isinstance(data, list) or not data:
        return None
    rows = [e for e in data if isinstance(e, dict) and "sourceCodeUrl" in e and e.get("sourceCodeLanguage", 1) == 1
            and e.get("itemType", 0) in (0, 1)]
    if not rows:
        return None
    return "mangayomi", [{"name": str(e.get("name", ""))[:80], "lang": str(e.get("lang", ""))[:8],
                          "nsfw": bool(e.get("isNsfw"))} for e in rows]


def load_index(url):
    try:
        data = fetch_json(url, limit=12 << 20)
    except Exception:
        return None
    got = parse_tachiyomi(data) or parse_tachiyomi_v2(data) or parse_mangayomi(data)
    if not got:
        return None
    digest = hashlib.sha256(json.dumps(sorted(e["name"] + e["lang"] for e in got[1])).encode()).hexdigest()[:16]
    return {"url": url, "kind": got[0], "entries": got[1], "hash": digest}


# ---------- discovery ----------

def search(kind):
    seen = {}
    for q in SEARCH[kind]:
        for page in (1, 2):
            try:
                r = gh_api("search/repositories?" + urllib.parse.urlencode({"q": q, "sort": "stars", "per_page": 50, "page": page}))
            except Exception as e:
                log("  search failed:", q, e)
                break
            for it in r.get("items", []):
                seen.setdefault(it["full_name"].lower(), it)
            if len(r.get("items", [])) < 50:
                break
    ranked = sorted(seen.values(), key=lambda it: -it.get("stargazers_count", 0))
    return [it for it in ranked if not it.get("archived") or it.get("stargazers_count", 0) >= 50][:CANDIDATES]


def index_urls_for(meta):
    """Places a repo's index may live: README "Add repo" links first, then common publish paths."""
    full = meta["full_name"]
    branch = meta.get("default_branch") or "main"
    urls = []
    try:
        readme = fetch("https://raw.githubusercontent.com/%s/%s/README.md" % (full, branch), limit=1 << 20).decode("utf-8", "replace")
    except Exception:
        readme = ""
    for link in ADD_LINK.findall(readme):
        q = urllib.parse.parse_qs(urllib.parse.urlparse(link).query)
        for key in ("url", "anime", "manga"):
            for v in q.get(key, []):
                urls.append(v)
    urls += RAW_INDEX.findall(readme)
    for b in ("repo", branch):
        urls += ["https://raw.githubusercontent.com/%s/%s/index.min.json" % (full, b)]
    # v2 stores (extensions-lib 1.6): index.min.json only holds update stubs; index.json is the v2 list.
    urls += ["https://raw.githubusercontent.com/%s/repo/index.json" % full]
    urls += ["https://raw.githubusercontent.com/%s/%s/%s" % (full, branch, f) for f in ("index.json", "anime_index.json")]
    clean = []
    for u in urls:
        u = to_raw(u.strip().rstrip("/"))
        if not u.startswith("https://"):
            continue
        if not re.search(r"\.json$", u):
            u += "/index.min.json"
        if u not in clean:
            clean.append(u)
    return clean[:12]


def repo_meta(full, cache):
    if full not in cache:
        try:
            cache[full] = gh_api("repos/" + full)
        except Exception:
            cache[full] = None
    return cache[full]


def score(stars, updated_days, count):
    fresh = 30 * math.exp(-updated_days / 180)
    return round(25 * math.log10(stars + 1) + fresh + 6 * math.log2(count + 1), 2)


def build_repos(blocked, seeds):
    metas = {}
    candidates = {}
    for kind in SEARCH:
        log("search:", kind)
        for it in search(kind):
            metas[it["full_name"].lower()] = it
            candidates[it["full_name"].lower()] = it
    for s in seeds:
        m = repo_meta(s.lower(), metas)
        if m:
            candidates[s.lower()] = m
    log("candidates:", len(candidates))

    found = {}
    lock = threading.Lock()

    def probe(meta):
        for u in index_urls_for(meta):
            idx = load_index(u)
            if idx:
                with lock:
                    found.setdefault(u, idx)

    with cf.ThreadPoolExecutor(12) as pool:
        list(pool.map(probe, [m for f, m in candidates.items() if f not in blocked]))
    log("indexes:", len(found))

    rows = []
    for u, idx in found.items():
        full = github_of(u)
        if not full or full in blocked:
            continue
        meta = repo_meta(full, metas)
        if not meta:
            continue
        updated = meta.get("pushed_at") or meta.get("updated_at") or ""
        age = days_since(updated)
        if age > STALE_DAYS:
            continue
        ents = idx["entries"]
        safe = [e for e in ents if not e["nsfw"]]
        langs = {}
        for e in safe:
            langs[e["lang"]] = langs.get(e["lang"], 0) + 1
        rows.append({
            "kind": idx["kind"], "url": u, "github": meta["full_name"],
            "name": meta["full_name"].split("/")[1], "owner": meta["full_name"].split("/")[0],
            "desc": (meta.get("description") or "")[:200], "stars": meta.get("stargazers_count", 0),
            "updated": updated, "count": len(safe), "nsfw": len(ents) - len(safe),
            "langs": [k for k, _ in sorted(langs.items(), key=lambda kv: -kv[1])][:8],
            "sample": [e["name"] for e in safe[:6]], "score": score(meta.get("stargazers_count", 0), age, len(safe)),
            "_hash": idx["hash"],
        })
    # Forks and mirrors publish the same index: keep the best-ranked copy.
    best = {}
    for r in sorted(rows, key=lambda r: -r["score"]):
        best.setdefault((r["kind"], r["_hash"]), r)
    out = {}
    for r in best.values():
        r.pop("_hash")
        out.setdefault(r["kind"], []).append(r)
    for kind in out:
        out[kind] = sorted(out[kind], key=lambda r: -r["score"])[:PER_KIND]
        log(kind, len(out[kind]), [r["github"] for r in out[kind][:5]])
    return out


# ---------- Stremio ----------

STREMIO_FEATURED = "https://api.strem.io/addonscollection.json"
STREMIO_COMMUNITY = "https://stremio-addons.net/api/addon_catalog/all/stremio-addons.net.json"


def stremio():
    lists = []
    for url, featured in ((STREMIO_FEATURED, True), (STREMIO_COMMUNITY, False)):
        try:
            data = fetch_json(url, limit=16 << 20)
            items = data["addons"] if isinstance(data, dict) else data
            lists += [(it, featured) for it in items if isinstance(it, dict)]
        except Exception as e:
            log("  stremio list failed:", url, e)
    seen = {}
    for it, featured in lists:
        url = str(it.get("transportUrl", ""))
        if url.startswith("https://") and url.endswith("/manifest.json") and url not in seen:
            seen[url] = featured
        elif url in seen and featured:
            seen[url] = True

    def check(item):
        url, featured = item
        try:
            m = fetch_json(url, limit=1 << 20, tries=1, timeout=12)
        except Exception:
            return None
        if not isinstance(m, dict) or not m.get("name"):
            return None
        hints = m.get("behaviorHints") or {}
        if hints.get("configurationRequired"):
            return None  # needs a setup page first; the user can still paste their configured link
        res = [r if isinstance(r, str) else (r or {}).get("name", "") for r in m.get("resources", [])]
        types = [t for t in m.get("types", []) if isinstance(t, str)][:6]
        return {
            "kind": "stremio", "url": url, "name": str(m["name"])[:60], "desc": str(m.get("description", ""))[:200],
            "version": str(m.get("version", ""))[:20], "types": types, "resources": [r for r in res if r][:6],
            "catalogs": len(m.get("catalogs") or []), "featured": featured, "adult": bool(hints.get("adult")),
        }

    with cf.ThreadPoolExecutor(24) as pool:
        rows = [r for r in pool.map(check, seen.items()) if r]
    rows.sort(key=lambda r: (not r["featured"], -r["catalogs"], r["name"].lower()))
    log("stremio:", len(rows), "of", len(seen), "answer")
    return rows[:300]


def main():
    cfg = json.load(open(CONFIG)) if os.path.exists(CONFIG) else {}
    blocked = {b.lower() for b in cfg.get("blocked", [])}
    seeds = cfg.get("sourceSeeds", [])
    repos = build_repos(blocked, seeds)
    out = {
        "version": 1, "generated": NOW.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "aniyomi": repos.get("aniyomi", []), "mihon": repos.get("mihon", []),
        "mangayomi": repos.get("mangayomi", []), "stremio": stremio(),
    }
    if sum(len(out[k]) for k in ("aniyomi", "mihon", "mangayomi", "stremio")) == 0:
        sys.exit("nothing found; keeping the previous sources.json")
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, "sources.json"), "w") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
    log("wrote", os.path.join(OUT, "sources.json"), os.path.getsize(os.path.join(OUT, "sources.json")), "bytes")


if __name__ == "__main__":
    main()
