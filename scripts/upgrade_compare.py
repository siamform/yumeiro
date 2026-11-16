#!/usr/bin/env python3
"""G12 X10: parity check between app data before and after an in-place upgrade.

Usage: upgrade_compare.py before.tar after.tar   (tars of shared_prefs/ and files/)

Fails (exit 1) when anything the old version stored is gone:
  - a file that existed before is missing after;
  - a SharedPreferences key is missing after;
  - a JSON file lost an element: every old object key / list item must still be contained in the
    new document (new keys, new items and reordering are allowed: the new version may add fields).
Value changes of a preference are listed as CHANGED (a launch counter or timestamp may move); they
are reviewed in the run, not failed automatically.
"""
import io
import json
import sys
import tarfile
import xml.etree.ElementTree as ET


def load(path):
    out = {}
    with tarfile.open(path) as t:
        for m in t.getmembers():
            if m.isfile():
                out[m.name.lstrip("./")] = t.extractfile(m).read()
    return out


def prefs(blob):
    try:
        root = ET.fromstring(blob)
    except ET.ParseError:
        return None
    d = {}
    for e in root:
        name = e.get("name")
        if e.tag == "set":
            d[name] = ("set", sorted(x.text or "" for x in e))
        elif e.tag == "string":
            d[name] = ("string", e.text or "")
        else:
            d[name] = (e.tag, e.get("value"))
    return d


def contains(old, new):
    if isinstance(old, dict):
        return isinstance(new, dict) and all(k in new and contains(v, new[k]) for k, v in old.items())
    if isinstance(old, list):
        return isinstance(new, list) and all(any(contains(o, n) for n in new) for o in old)
    return old == new


def missing_paths(old, new, path="$"):
    """The first few places where old is not contained in new (for the report)."""
    if contains(old, new):
        return []
    if isinstance(old, dict) and isinstance(new, dict):
        out = []
        for k, v in old.items():
            if k not in new:
                out.append(f"{path}.{k} (key gone)")
            else:
                out += missing_paths(v, new[k], f"{path}.{k}")
        return out[:5]
    if isinstance(old, list) and isinstance(new, list):
        gone = [i for i, o in enumerate(old) if not any(contains(o, n) for n in new)]
        return [f"{path}[{i}] (item gone or changed: {json.dumps(old[i])[:160]})" for i in gone[:5]]
    return [f"{path}: {json.dumps(old)[:80]} -> {json.dumps(new)[:80]}"]


def as_json(blob):
    try:
        return json.loads(blob.decode("utf-8"))
    except Exception:
        return None


def main():
    before, after = load(sys.argv[1]), load(sys.argv[2])
    fails, changed = [], []
    print(f"UPGRADE_COMPARE files before={len(before)} after={len(after)}")
    for name, blob in sorted(before.items()):
        if name not in after:
            fails.append(f"MISSING file {name} ({len(blob)} B)")
            continue
        nb = after[name]
        if name.startswith("shared_prefs/") and name.endswith(".xml"):
            po, pn = prefs(blob), prefs(nb)
            if po is None or pn is None:
                fails.append(f"UNREADABLE prefs {name}")
                continue
            for k, v in po.items():
                if k not in pn:
                    fails.append(f"MISSING pref {name} :: {k}")
                elif pn[k] != v:
                    changed.append(f"CHANGED pref {name} :: {k}: {str(v[1])[:60]} -> {str(pn[k][1])[:60]}")
            print(f"UPGRADE_COMPARE prefs {name}: {len(po)} keys before, {len(pn)} after")
            continue
        jo = as_json(blob)
        if jo is not None:
            jn = as_json(nb)
            if jn is None:
                fails.append(f"UNREADABLE json after {name}")
            elif not contains(jo, jn):
                fails += [f"LOST json {name} {p}" for p in missing_paths(jo, jn)]
            else:
                print(f"UPGRADE_COMPARE json {name}: contained ({len(blob)} B -> {len(nb)} B)")
            continue
        print(f"UPGRADE_COMPARE other {name}: {len(blob)} B -> {len(nb)} B")
    for n in sorted(set(after) - set(before)):
        print(f"UPGRADE_COMPARE new file {n} ({len(after[n])} B)")
    for c in changed:
        print("UPGRADE_COMPARE " + c)
    for f in fails:
        print("UPGRADE_COMPARE FAIL " + f)
    print(f"UPGRADE_COMPARE result: {len(fails)} lost, {len(changed)} changed values")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
