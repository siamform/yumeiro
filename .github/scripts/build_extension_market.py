#!/usr/bin/env python3
"""Builds the in-app Extension Market: finds CloudStream extension repositories, ranks every
extension and publishes index.json to the `extensions` branch.

Where repositories come from (all merged and de-duplicated by repo.json URL):
  1. curated   - .github/extensions/sources.json (files are downloaded, verified and mirrored)
  2. community - CloudStream's community list (recloudstream/cs-repos repos-db.json)
  3. discovered- GitHub search: repositories with CloudStream topics/keywords and repo.json files
                 that contain "pluginLists" (code search), plus "seeds" from sources.json

For every repository: repo.json -> pluginLists -> plugins.json. Extensions are normalised (18+ ones are kept and tagged "18+"; the app hides them unless the
user opts in), and each gets a score from repository stars, how recently the repository was
updated, plugin status and version. When the same extension (internalName) is published by more
than one repository, the best copy (working, newest version, most recently updated) is marked
"primary" and the others are kept as alternatives.

Only curated files are mirrored; community/discovered extensions are installed from their source
(the app checks the published fileHash when there is one).

Usage: build_extension_market.py <out-dir>
Env: EXT_CONFIG (default .github/extensions/sources.json), GITHUB_TOKEN, GITHUB_REPOSITORY,
     DISCOVERY=0 to skip GitHub search (curated + community only).
"""
import concurrent.futures as cf
import hashlib, io, json, math, os, re, sys, threading, time, urllib.parse, urllib.request, zipfile
from datetime import datetime, timezone

OUT = sys.argv[1] if len(sys.argv) > 1 else "out-ext"
CONFIG = os.environ.get("EXT_CONFIG", ".github/extensions/sources.json")
# Download URL -> why the file won't load. Produced by a dex-level audit of every listed file
# against the app's CloudStream layer (and CloudStream itself); the app shows it before install.
COMPAT = os.environ.get("EXT_COMPAT", ".github/extensions/compat.json")
FOREIGN_PREFIXES = ("com.popcornplay.",)
REPO = os.environ.get("GITHUB_REPOSITORY", "siamform/yumeiro")
TOKEN = os.environ.get("GITHUB_TOKEN", "")
DISCOVERY = os.environ.get("DISCOVERY", "1") != "0"
BRANCH = "extensions"
MIRROR = f"https://raw.githubusercontent.com/{REPO}/{BRANCH}"
COMMUNITY_DB = "https://raw.githubusercontent.com/recloudstream/cs-repos/master/repos-db.json"
NAME_RE = re.compile(r"^[A-Za-z0-9_.\-]{1,120}$")
UA = "Yumeiro-extension-market/2.0 (+https://github.com/%s)" % REPO
NOW = datetime.now(timezone.utc)
now = lambda: NOW.strftime("%Y-%m-%dT%H:%M:%SZ")
GH_LOCK = threading.Lock()
TOPICS = ["cloudstream", "cloudstream-extension", "cloudstream-extensions", "cloudstream-plugin",
          "cloudstream-plugins", "cloudstream3", "cloudstream-repo", "cloudstream-repository", "cloudstream-providers"]
KEYWORDS = ["cloudstream extensions", "cloudstream plugins", "cloudstream providers", "cloudstream repo", "cs3 extensions"]


def log(*a):
    print(*a, flush=True)


def fetch(url, limit, accept="*/*", gh=False, tries=3):
    headers = {"User-Agent": UA, "Accept": accept}
    if gh and TOKEN:
        headers["Authorization"] = "Bearer " + TOKEN
    last = None
    for attempt in range(tries):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=30) as r:
                data = r.read(limit + 1)
                if len(data) > limit:
                    raise ValueError("file larger than %d bytes" % limit)
                return data
        except urllib.error.HTTPError as e:
            last = e
            if e.code == 403 and gh and "rate limit" in str(e.read()[:300]).lower():
                reset = int(e.headers.get("x-ratelimit-reset", "0") or 0)
                wait = max(5, min(70, reset - int(time.time()) + 2)) if reset else 30
                log("  rate limited, waiting %ds" % wait)
                time.sleep(wait)
                continue
            if e.code in (401, 403, 404, 410, 451):
                break
        except Exception as e:  # network hiccup: retry
            last = e
        time.sleep(1.5 * (attempt + 1))
    raise RuntimeError("%s: %s" % (url, last))


def fetch_json(url, limit=4 << 20, gh=False):
    return json.loads(fetch(url, limit, "application/vnd.github+json" if gh else "application/json", gh=gh).decode("utf-8-sig"))


def gh_api(path):
    with GH_LOCK:  # keep API calls sequential and gentle
        time.sleep(0.15)
        if os.environ.get("USE_GH_CLI") == "1":  # local runs: authenticate through the gh CLI
            import subprocess
            r = subprocess.run(["gh", "api", path.lstrip("/")], capture_output=True, text=True, timeout=60)
            if r.returncode != 0:
                raise RuntimeError(r.stderr.strip()[:200])
            return json.loads(r.stdout)
        return fetch_json("https://api.github.com/" + path.lstrip("/"), 8 << 20, gh=True)


def resolve(base, link):
    return link if re.match(r"^https?://", link or "") else urllib.parse.urljoin(base, link or "")


def slug(s):
    return re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-")[:60] or "repo"


def norm_url(u):
    u = u.strip()
    m = re.match(r"^https?://raw\.githubusercontent\.com/([^/]+)/([^/]+)/(?:refs/heads/)?([^/]+)/(.*)$", u)
    if m:
        return "https://raw.githubusercontent.com/%s/%s/%s/%s" % (m.group(1), m.group(2), m.group(3), m.group(4))
    return u


def github_of(url):
    m = re.match(r"^https?://raw\.githubusercontent\.com/([^/]+)/([^/]+)/", url) or re.match(r"^https?://github\.com/([^/]+)/([^/#?]+)", url)
    return "%s/%s" % (m.group(1), m.group(2).removesuffix(".git")) if m else None


def parse_time(s):
    try:
        return datetime.strptime(s, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
    except Exception:
        return None


def inspect_cs3(data):
    """Returns the plugin manifest of a .cs3 or raises."""
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        names = set(z.namelist())
        if "classes.dex" not in names:
            raise ValueError("no classes.dex")
        if "manifest.json" not in names:
            raise ValueError("no manifest.json")
        m = json.loads(z.read("manifest.json").decode("utf-8", "replace"))
        if not (m.get("pluginClassName") or "").strip():
            raise ValueError("manifest has no pluginClassName")
        return m


# ---------------------------------------------------------------- candidate discovery

def community_candidates():
    out = []
    try:
        for e in fetch_json(COMMUNITY_DB):
            url = e.get("url") if isinstance(e, dict) else e
            if isinstance(url, str) and url.startswith("https://"):
                out.append({"url": norm_url(url), "origin": "community", "verified": bool(isinstance(e, dict) and e.get("verified"))})
    except Exception as ex:
        log("community list failed:", ex)
    log("community repositories:", len(out))
    return out


def github_search_repos():
    """owner/name -> GitHub repository JSON, from topic and keyword searches."""
    found = {}
    queries = ["topic:%s" % t for t in TOPICS] + ['"%s" in:name,description,readme' % k for k in KEYWORDS]
    for q in queries:
        for page in range(1, 6):
            try:
                res = gh_api("search/repositories?q=%s&sort=updated&order=desc&per_page=100&page=%d" % (urllib.parse.quote(q + " archived:false"), page))
            except Exception as ex:
                log("  search failed:", q, ex); break
            items = res.get("items", [])
            for it in items:
                found[it["full_name"].lower()] = it
            if len(items) < 100:
                break
    log("repositories from GitHub search:", len(found))
    return found


def github_code_search():
    """owner/name -> list of repo.json paths that mention pluginLists."""
    paths = {}
    if os.environ.get("CODE_SEARCH", "1") == "0":
        log("code search disabled (CODE_SEARCH=0)")
        return paths
    if not TOKEN and os.environ.get("USE_GH_CLI") != "1":
        return paths
    for q in ['"pluginLists" filename:repo.json', '"pluginLists" extension:json']:
        for page in range(1, 11):
            try:
                res = gh_api("search/code?q=%s&per_page=100&page=%d" % (urllib.parse.quote(q), page))
            except Exception as ex:
                log("  code search stopped:", ex); break
            items = res.get("items", [])
            for it in items:
                full = it["repository"]["full_name"].lower()
                if it.get("name", "").lower().endswith(".json"):
                    paths.setdefault(full, set()).add(it["path"])
            if len(items) < 100:
                break
            time.sleep(6)  # code search allows ~10 requests per minute
    log("repositories from code search:", len(paths))
    return paths


def repo_json_urls(full, meta, paths):
    """Likely repo.json locations for a GitHub repository."""
    owner, name = full.split("/", 1)
    branches = []
    for b in [meta.get("default_branch") if meta else None, "builds", "master", "main"]:
        if b and b not in branches:
            branches.append(b)
    files = sorted(paths or [], key=lambda p: (p.count("/"), p != "repo.json")) or []
    files = [f for f in files if f.lower().endswith("repo.json")] + ["repo.json"]
    urls = []
    for b in branches:
        for f in files[:3]:
            u = "https://raw.githubusercontent.com/%s/%s/%s/%s" % (owner, name, b, f)
            if u not in urls:
                urls.append(u)
    return urls[:8]


# ---------------------------------------------------------------- repository loading

def load_repo(url):
    repo = fetch_json(url, 1 << 20)
    if not isinstance(repo, dict):
        raise ValueError("not a repository")
    lists = [resolve(url, l) for l in repo.get("pluginLists", []) if isinstance(l, str)]
    lists = [l for l in lists if l.startswith("https://")]
    if not lists:
        raise ValueError("no pluginLists")
    plugins = []
    for l in lists[:8]:
        try:
            data = fetch_json(l, 6 << 20)
            if isinstance(data, list):
                plugins += [(l, p) for p in data if isinstance(p, dict)]
        except Exception as e:
            log("  list failed:", l, e)
    if not plugins:
        raise ValueError("plugin lists are empty or unreachable")
    return repo, plugins


def find_repo(full, meta, paths):
    for u in repo_json_urls(full, meta, paths):
        try:
            repo, plugins = load_repo(u)
            return u, repo, plugins
        except Exception:
            continue
    return None


def normalise_plugin(list_url, p):
    internal = str(p.get("internalName", "")).strip()
    if not NAME_RE.match(internal):
        return None
    types = [str(t) for t in p.get("tvTypes") or [] if isinstance(t, (str, int))][:12]
    try:
        api = int(p.get("apiVersion", 1) or 1)
    except Exception:
        api = 1
    if api > 1:
        return None
    src = resolve(list_url, str(p.get("url", "")))
    if not src.startswith("https://"):
        return None
    try:
        version = int(p.get("version", 0) or 0)
    except Exception:
        version = 0
    try:
        status = int(p.get("status", 1) if p.get("status") is not None else 1)
    except Exception:
        status = 1
    sha = str(p.get("fileHash") or "").lower().replace("sha256-", "")
    size = p.get("fileSize")
    lang = p.get("language")
    return {
        "internalName": internal,
        "name": str(p.get("name") or internal).strip()[:80],
        "version": version,
        "apiVersion": api,
        "status": status,
        "description": (str(p.get("description") or "").strip()[:400]) or None,
        "authors": [str(a)[:40] for a in p.get("authors") or [] if isinstance(a, str)][:6],
        "tvTypes": types,
        "language": lang.strip()[:12] if isinstance(lang, str) and lang.strip() else None,
        "iconUrl": (str(p.get("iconUrl") or "").replace("%size%", "128")) or None,
        "url": src,
        "sha256": sha if re.fullmatch(r"[0-9a-f]{64}", sha) else None,
        "size": int(size) if isinstance(size, (int, float)) and size > 0 else None,
    }


CATEGORY = {
    "anime": "Anime", "animemovie": "Anime", "ova": "Anime",
    "movie": "Movies", "tvseries": "TV", "asiandrama": "Asian drama", "cartoon": "Cartoon",
    "documentary": "Documentary", "live": "Live", "torrent": "Torrent", "music": "Music",
    "audiobook": "Audio", "audio": "Audio", "podcast": "Audio", "others": "Other", "nsfw": "18+",
}


def categories(types):
    out = []
    for t in types:
        c = CATEGORY.get(t.lower().replace("_", ""), "Other")
        if c not in out:
            out.append(c)
    return out or ["Other"]


def score(e, repo):
    stars = repo.get("stars") or 0
    s = math.log10(stars + 1) * 3.0
    pushed = parse_time(repo.get("pushed") or "")
    if pushed:
        days = (NOW - pushed).days
        s += 4 if days <= 14 else 3 if days <= 45 else 1.5 if days <= 120 else 0 if days <= 365 else -3
    s += {1: 1.5, 3: 0.5, 2: 0.2, 0: -6}.get(e["status"], 0)
    s += min(math.log2(max(int(e["version"] or 0), 0) + 1), 7) * 0.35
    if repo.get("verified"):
        s += 2
    if repo.get("origin") == "curated":
        s += 3
    if not e.get("iconUrl"):
        s -= 0.3
    return round(s, 3)


# ---------------------------------------------------------------- main

def main():
    cfg = json.load(open(CONFIG))
    limits = cfg.get("limits", {})
    max_bytes = int(limits.get("maxFileBytes", 15 << 20))
    max_per_repo = int(limits.get("maxPerRepo", 250))
    max_repos = int(limits.get("maxRepositories", 400))
    blocked = {b.lower() for b in cfg.get("blocked", [])}
    os.makedirs(OUT, exist_ok=True)
    prev_exts, prev_repos = {}, {}
    try:
        old = json.load(open(os.path.join(OUT, "index.json")))
        prev_exts = {(e["repoUrl"], e["internalName"]): e for e in old.get("extensions", [])}
        prev_repos = {r["url"]: r for r in old.get("repositories", [])}
    except Exception:
        pass

    # 1. candidates -------------------------------------------------------
    cands = {}  # repo.json url -> info

    def add(url, **info):
        url = norm_url(url)
        gh = github_of(url)
        if gh and gh.lower() in blocked:
            return
        cur = cands.setdefault(url, {"url": url, "github": gh, "origin": info.get("origin", "discovered")})
        rank = {"curated": 0, "community": 1, "discovered": 2}
        if rank[info.get("origin", "discovered")] < rank[cur["origin"]]:
            cur["origin"] = info["origin"]
        for k, v in info.items():
            if k != "origin" and v is not None and cur.get(k) in (None, False, ""):
                cur[k] = v

    for src in cfg.get("repositories", []):
        add(src["url"], origin="curated", verified=True, note=src.get("note"), github=src.get("github"))
    for c in community_candidates():
        add(c["url"], origin="community", verified=c["verified"])

    gh_meta = {}
    if DISCOVERY:
        gh_meta = github_search_repos()
        code_paths = github_code_search()
        seeds = {s.lower(): None for s in cfg.get("seeds", [])}
        names = set(gh_meta) | set(code_paths) | set(seeds)
        names = {n for n in names if n not in blocked}
        known = {(c.get("github") or "").lower() for c in cands.values()}
        todo = [n for n in names if n not in known]
        # Skip forks without stars and long-abandoned repositories to keep the market useful.
        def keep(n):
            m = gh_meta.get(n)
            if not m:
                return True
            if m.get("archived") or m.get("disabled"):
                return False
            pushed = parse_time(m.get("pushed_at") or "")
            if pushed and (NOW - pushed).days > 900:
                return False
            if m.get("fork") and (m.get("stargazers_count") or 0) < 2:
                return False
            return True
        todo = sorted([n for n in todo if keep(n)], key=lambda n: -((gh_meta.get(n) or {}).get("stargazers_count") or 0))[:max_repos]
        log("probing %d GitHub repositories for repo.json" % len(todo))
        with cf.ThreadPoolExecutor(12) as pool:
            futs = {pool.submit(find_repo, n, gh_meta.get(n), code_paths.get(n)): n for n in todo}
            for f in cf.as_completed(futs):
                r = f.result()
                if r:
                    add(r[0], origin="discovered", github=futs[f])
    log("candidate repositories:", len(cands))

    # 2. load every repository --------------------------------------------
    def stars_meta(c):
        gh = c.get("github")
        m = gh_meta.get((gh or "").lower())
        if not m and gh and (TOKEN or os.environ.get("USE_GH_CLI") == "1"):
            try:
                m = gh_api("repos/" + gh)
                gh_meta[gh.lower()] = m
            except Exception:
                m = None
        return m

    def load(c):
        try:
            repo, plugins = load_repo(c["url"])
            return c, repo, plugins, None
        except Exception as e:
            return c, None, None, e

    loaded = []
    with cf.ThreadPoolExecutor(12) as pool:
        loaded = list(pool.map(load, cands.values()))

    repos_out, all_exts, keep_files = [], [], set()
    seen_lists = {}
    for c, repo, plugins, err in loaded:
        url = c["url"]
        m = stars_meta(c)
        stars = m.get("stargazers_count") if m else None
        pushed = m.get("pushed_at") if m else None
        if err is not None:
            log("== %s failed: %s" % (url, err))
            p = prev_repos.get(url)
            if p and c["origin"] == "curated":
                r = dict(p); r["ok"] = False; r["checked"] = now(); repos_out.append(r)
                for (ru, _), e2 in prev_exts.items():
                    if ru == url:
                        all_exts.append(dict(e2)); keep_files.add(e2.get("file"))
            continue
        # Same plugin list behind two repo.json URLs (e.g. master + builds): keep the first.
        sig = hashlib.sha1(json.dumps(sorted(str(p.get("internalName")) + str(p.get("version")) for _, p in plugins)).encode()).hexdigest()
        if sig in seen_lists and c["origin"] != "curated":
            continue
        seen_lists[sig] = url
        name = str(repo.get("name") or c.get("github") or url).strip()[:60]
        info = {
            "url": url, "name": name, "description": (str(repo.get("description") or c.get("note") or "").strip()[:300]) or None,
            "github": c.get("github"), "stars": stars, "pushed": pushed, "origin": c["origin"],
            "verified": bool(c.get("verified")), "ok": True, "checked": now(),
        }
        rid = slug(c.get("github") or url)
        count, seen = 0, set()
        for list_url, p in plugins:
            if count >= max_per_repo:
                break
            e = normalise_plugin(list_url, p)
            if not e or e["internalName"] in seen:
                continue
            seen.add(e["internalName"])
            e.update({"repoUrl": url, "repoName": name, "stars": stars, "origin": c["origin"], "verified": info["verified"],
                      "categories": categories(e["tvTypes"])})
            old = prev_exts.get((url, e["internalName"]))
            changed = not old or old.get("version") != e["version"] or (e["sha256"] and old.get("sha256") != e["sha256"])
            e["updated"] = (old or {}).get("updated") if not changed else (pushed if not old and pushed else now())
            e["updated"] = e["updated"] or pushed or now()
            if c["origin"] == "curated":
                rel = "files/%s/%s.cs3" % (rid, e["internalName"])
                try:
                    data = fetch(e["url"], max_bytes)
                    sha = hashlib.sha256(data).hexdigest()
                    if e["sha256"] and e["sha256"] != sha:
                        raise ValueError("fileHash mismatch")
                    manifest = inspect_cs3(data)
                    path = os.path.join(OUT, rel)
                    os.makedirs(os.path.dirname(path), exist_ok=True)
                    with open(path, "wb") as f:
                        f.write(data)
                    e.update({"source": e["url"], "url": "%s/%s" % (MIRROR, rel), "file": rel, "sha256": sha, "size": len(data),
                              "pluginClassName": manifest.get("pluginClassName")})
                    keep_files.add(rel)
                except Exception as ex:
                    log("  %s mirror failed: %s" % (e["internalName"], ex))
                    if old and old.get("file") and os.path.exists(os.path.join(OUT, old["file"])):
                        e = dict(old); e["ok"] = False; keep_files.add(old["file"])
                    else:
                        continue
            all_exts.append(e); count += 1
        info["count"] = count
        repos_out.append(info)
        log("== %-60s %-10s %4s stars %3d extensions" % (url[:60], c["origin"], stars if stars is not None else "-", count))

    # 3. compatibility notes, rank + de-duplicate ------------------------------
    try:
        compat = json.load(open(COMPAT))
    except Exception:
        compat = {}
    for e in all_exts:
        note = compat.get(e.get("source") or e["url"]) or compat.get(e["url"])
        if not note and (e.get("pluginClassName") or "").startswith(FOREIGN_PREFIXES):
            note = "Made for a different app, not CloudStream: it won't load."
        if note:
            e["compat"] = note
        else:
            e.pop("compat", None)
    repo_by_url = {r["url"]: r for r in repos_out}
    for e in all_exts:
        e["score"] = score(e, repo_by_url.get(e["repoUrl"], {}))
        if e.get("compat"):
            e["score"] = round(e["score"] - 5, 3)  # known not to load: never ahead of working ones
    groups = {}
    for e in all_exts:
        groups.setdefault(e["internalName"].lower(), []).append(e)
    for g in groups.values():
        g.sort(key=lambda e: (e["status"] != 0, e["origin"] == "curated", e["version"], e.get("updated") or "", e.get("stars") or 0), reverse=True)
        for i, e in enumerate(g):
            e["primary"] = i == 0
            e["copies"] = len(g)
        # The best copy carries the group's popularity.
        g[0]["score"] = round(g[0]["score"] + math.log2(len(g)) * 0.8, 3)
    all_exts.sort(key=lambda e: (-e["score"], e["name"].lower()))
    for i, e in enumerate([x for x in all_exts if x["primary"]]):
        e["rank"] = i + 1
    repos_out.sort(key=lambda r: ({"curated": 0, "community": 1, "discovered": 2}[r["origin"]], -(r.get("stars") or 0)))

    # Drop mirrored files no longer listed.
    for root, _, files in os.walk(os.path.join(OUT, "files")):
        for f in files:
            rel = os.path.relpath(os.path.join(root, f), OUT).replace(os.sep, "/")
            if rel not in keep_files:
                os.remove(os.path.join(root, f))

    index = {"version": 2, "generated": now(), "repo": REPO, "branch": BRANCH,
             "repositories": repos_out, "extensions": all_exts}
    with open(os.path.join(OUT, "index.json"), "w") as f:
        json.dump(index, f, ensure_ascii=False, separators=(",", ":"))
    primary = [e for e in all_exts if e["primary"]]
    log("Published %d extensions (%d unique) from %d repositories" % (len(all_exts), len(primary), len(repos_out)))
    with open(os.path.join(OUT, "summary.md"), "w") as f:
        f.write("Published %d extensions (%d unique) from %d repositories\n\n" % (len(all_exts), len(primary), len(repos_out)))
        f.write("| # | Extension | Version | Types | Repository | Stars |\n|---|---|---|---|---|---|\n")
        for e in primary[:60]:
            f.write("| %d | %s | %d | %s | %s | %s |\n" % (e["rank"], e["name"], e["version"], ", ".join(e["categories"]), e["repoName"], e.get("stars") or "-"))
    if not all_exts:
        sys.exit("No extensions published")


if __name__ == "__main__":
    main()
