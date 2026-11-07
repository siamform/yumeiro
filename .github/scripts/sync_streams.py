#!/usr/bin/env python3
"""Mirror the default stream playlists into ./out (the 'streams' branch).

For every source in .github/streams/sources.json:
  * download it (following redirects),
  * validate it is an M3U channel list (#EXTINF + URL lines),
  * write it to out/playlists/<id>.m3u only when valid (otherwise keep the previous copy),
  * record channel count, groups, sha256 and timestamps in out/index.json.
The app downloads index.json when the user taps "Sync" on the Stream page.
"""
import hashlib, json, os, re, sys, time, urllib.request
from datetime import datetime, timezone

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SOURCES = os.path.join(ROOT, ".github", "streams", "sources.json")
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "out")
REPO = os.environ.get("GITHUB_REPOSITORY", "siamform/yumeiro")
BRANCH = os.environ.get("STREAMS_BRANCH", "streams")
RAW = f"https://raw.githubusercontent.com/{REPO}/{BRANCH}"
MAX_BYTES = 40 * 1024 * 1024
URL_LINE = re.compile(r"^[a-zA-Z][a-zA-Z0-9+.-]*://")


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Yumeiro-stream-sync/1.0"})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                data = r.read(MAX_BYTES + 1)
                if len(data) > MAX_BYTES:
                    raise ValueError("playlist too large")
                return r.geturl(), r.status, r.headers.get("Content-Type", ""), data
        except Exception as e:  # noqa: BLE001
            if attempt == 2:
                raise
            print(f"  retry {attempt + 1}: {e}")
            time.sleep(5 * (attempt + 1))


def analyse(text):
    channels, groups, pending = 0, set(), False
    for raw in text.splitlines():
        line = raw.strip().lstrip("\ufeff")
        if line.upper().startswith("#EXTINF"):
            pending = True
            m = re.search(r'group-title="([^"]*)"', line)
            if m and m.group(1).strip():
                groups.add(m.group(1).strip())
        elif line and not line.startswith("#") and URL_LINE.match(line):
            if pending:
                channels += 1
            pending = False
    return channels, sorted(groups)


def main():
    with open(SOURCES, encoding="utf-8") as f:
        sources = json.load(f)["sources"]
    os.makedirs(os.path.join(OUT, "playlists"), exist_ok=True)
    old_index = {}
    try:
        with open(os.path.join(OUT, "index.json"), encoding="utf-8") as f:
            old_index = {p["id"]: p for p in json.load(f).get("playlists", [])}
    except Exception:  # noqa: BLE001
        pass

    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    entries, changed, failed = [], 0, 0
    for s in sources:
        sid = s["id"]
        path = os.path.join(OUT, "playlists", f"{sid}.m3u")
        prev = old_index.get(sid, {})
        entry = {k: s[k] for k in ("id", "name", "category", "description", "repo") if k in s}
        entry["source"] = s["url"]
        entry["url"] = f"{RAW}/playlists/{sid}.m3u"
        print(f"- {sid}: {s['url']}")
        try:
            final_url, status, ctype, data = fetch(s["url"])
            text = data.decode("utf-8", errors="replace")
            count, groups = analyse(text)
            if count == 0:
                raise ValueError("no channels (#EXTINF + URL) found")
            digest = hashlib.sha256(data).hexdigest()
            if digest != prev.get("sha256"):
                changed += 1
                with open(path, "wb") as f:
                    f.write(data)
                entry["updated"] = now
            else:
                entry["updated"] = prev.get("updated", now)
            entry.update(sha256=digest, channels=count, groups=len(groups), bytes=len(data),
                         resolved=final_url, contentType=ctype, checked=now, ok=True)
            print(f"  ok: {count} channels, {len(groups)} groups, {len(data)} bytes")
        except Exception as e:  # noqa: BLE001
            failed += 1
            print(f"  FAILED: {e}")
            if os.path.exists(path) and prev:
                # Keep the last good copy so the app still has something to load.
                entry.update({k: prev[k] for k in ("sha256", "channels", "groups", "bytes", "updated") if k in prev})
                entry.update(ok=False, error=str(e)[:200], checked=now)
            else:
                entry.update(url=s["url"], ok=False, error=str(e)[:200], checked=now, channels=0)
        entries.append(entry)

    index = {"version": 1, "generated": now, "repo": REPO, "branch": BRANCH, "playlists": entries}
    with open(os.path.join(OUT, "index.json"), "w", encoding="utf-8") as f:
        json.dump(index, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"changed={changed} failed={failed} total={len(entries)}")
    if failed == len(entries):
        sys.exit("all sources failed")


if __name__ == "__main__":
    main()
