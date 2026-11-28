#!/usr/bin/env python3
"""B10: health and safety report for a published Extension Market index.

Usage: check_extension_health.py <index.json or URL> [report.json]
Downloads every listed extension file (12 at a time), runs extension_vetting.vet on it and prints how many are
alive, dead, broken, unsafe or hostile, with the reasons. Exit code 1 when any listed extension would be removed by
the market's rules (so it can gate a published index). Nothing is executed; files are only read.
"""
import concurrent.futures as cf
import collections, json, os, sys, urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from extension_vetting import Rejected, vet  # noqa: E402

UA = "Yumeiro-extension-health/1.0 (+https://github.com/siamform/yumeiro)"
MAX = 15 << 20


def get(url, limit=MAX):
    with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=30) as r:
        data = r.read(limit + 1)
        if len(data) > limit:
            raise Rejected("broken", "file larger than %d bytes" % limit)
        return data


def check(e):
    if e.get("status") == 0:
        return e, Rejected("dead", "marked down by its author (status 0)")
    try:
        data = get(e["url"])
    except Rejected as r:
        return e, r
    except Exception as x:
        return e, Rejected("dead", "download failed: %s" % str(x)[:120])
    try:
        # Same rule as the market build: only curated (mirrored) files must match their listed hash.
        vet(data, e.get("sha256") if e.get("origin") == "curated" else None)
        return e, None
    except Rejected as r:
        return e, r


def main():
    src = sys.argv[1]
    index = json.loads(get(src, 64 << 20) if src.startswith("https://") else open(src, "rb").read())
    exts = index.get("extensions", [])
    out = {"checked": len(exts), "ok": 0, "removed": collections.Counter(), "items": []}
    with cf.ThreadPoolExecutor(12) as pool:
        for e, r in pool.map(check, exts):
            if r is None:
                out["ok"] += 1
                continue
            out["removed"][r.kind] += 1
            out["items"].append({"name": e.get("name"), "internalName": e.get("internalName"), "repo": e.get("repoName"),
                                 "url": e.get("url"), "kind": r.kind, "reason": r.reason})
    out["removed"] = dict(out["removed"])
    print("checked %d: %d ok, removed %s" % (out["checked"], out["ok"], out["removed"] or "none"))
    reasons = collections.Counter("%s: %s" % (i["kind"], i["reason"].split(":")[0]) for i in out["items"])
    for k, n in reasons.most_common(25):
        print("  %5d  %s" % (n, k))
    if len(sys.argv) > 2:
        with open(sys.argv[2], "w") as f:
            json.dump(out, f, ensure_ascii=False, indent=1)
    sys.exit(1 if out["items"] else 0)


if __name__ == "__main__":
    main()
