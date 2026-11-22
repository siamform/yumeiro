#!/usr/bin/env python3
"""E3 / ADR-054: verify ABI-split outputs. Usage: check_abi_splits.py <apk dir> [<apk dir> ...]

Each dir must hold one APK per ABI plus a universal APK. A split may contain only its own
lib/<abi>/, the universal one all four, and every APK must share one versionCode (read with
aapt2 when AAPT2 is set, else skipped with a note)."""
import os, re, subprocess, sys, zipfile

ABIS = ["arm64-v8a", "armeabi-v7a", "x86", "x86_64"]


def libs(path):
    return {n.split("/")[1] for n in zipfile.ZipFile(path).namelist() if n.startswith("lib/") and n.count("/") >= 2}


def version_code(path):
    aapt2 = os.environ.get("AAPT2")
    if not aapt2:
        return None
    out = subprocess.run([aapt2, "dump", "badging", path], capture_output=True, text=True, check=True).stdout
    return re.search(r"versionCode='(\d+)'", out).group(1)


def check(d):
    apks = sorted(f for f in os.listdir(d) if f.endswith(".apk"))
    errors, codes = [], {}
    for abi in ABIS + ["universal"]:
        hit = [f for f in apks if re.search(r"-" + re.escape(abi) + r"-", f)]
        if len(hit) != 1:
            errors.append(f"{d}: expected one {abi} APK, found {hit}")
            continue
        p = os.path.join(d, hit[0])
        got = libs(p)
        want = set(ABIS) if abi == "universal" else {abi}
        if got != want:
            errors.append(f"{p}: lib/ ABIs {sorted(got)}, expected {sorted(want)}")
        codes[hit[0]] = version_code(p)
        print(f"{hit[0]}: {os.path.getsize(p) // 1024} KB, libs {sorted(got)}, versionCode {codes[hit[0]]}")
    known = {c for c in codes.values() if c is not None}
    if len(known) > 1:
        errors.append(f"{d}: versionCodes differ {codes}")
    if not known:
        print("note: AAPT2 not set, versionCode not compared")
    return errors


if __name__ == "__main__":
    errs = [e for d in sys.argv[1:] for e in check(d)]
    for e in errs:
        print("ERROR:", e)
    sys.exit(1 if errs or len(sys.argv) < 2 else 0)
