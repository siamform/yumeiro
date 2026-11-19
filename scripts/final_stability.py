#!/usr/bin/env python3
"""Stability scan for the final passes (CODE-AUDIT-FINAL "Stability" row).

Usage: final_stability.py <artifact dir>...   (dirs from `gh run download <run>`)

Counts, for the app's processes only (pids from "Start proc <pid>:dev.asfakul.yumeiro"):
crashes (FATAL EXCEPTION with Process: dev.asfakul.yumeiro), ANRs ("ANR in dev.asfakul.yumeiro"),
YuStall lines, and StrictMode violations grouped by kind and first app frame.
Prints a Markdown summary. Read-only; never edits evidence.
"""
import collections, os, re, sys

APP = "dev.asfakul.yumeiro"
START = re.compile(r"Start proc (\d+):(" + re.escape(APP) + r"[^/ ]*)")
PID = re.compile(r"^\S+ \S+\s+(?:\w/[^(]*\(\s*(\d+)\)|\s*(\d+)\s+\d+ \w )")

def pid_of(line):
    m = PID.match(line)
    if not m: return None
    return m.group(1) or m.group(2)

def scan(path):
    pids, crashes, anrs, stalls = set(), [], [], []
    strict = collections.Counter()
    lines = open(path, errors="replace").read().splitlines()
    for l in lines:
        m = START.search(l)
        if m: pids.add(m.group(1))
    i = 0
    while i < len(lines):
        l = lines[i]
        if "FATAL EXCEPTION" in l and i + 1 < len(lines) and APP in lines[i + 1]:
            crashes.append(" | ".join(x.split("): ", 1)[-1] for x in lines[i + 1:i + 4]))
        elif "ANR in " + APP in l:
            anrs.append(l.split("ANR in ", 1)[1][:120])
        elif "YuStall" in l and "main thread stalled" in l:
            stalls.append(l.split("): ", 1)[-1][:160])
        elif "StrictMode policy violation" in l and pid_of(l) in pids:
            km = re.search(r"strictmode\.(\w+)", l) or re.search(r"policy violation[;:]?\s*(\S+)", l)
            kind = km.group(1) if km else "?"
            frame = "?"
            for x in lines[i + 1:i + 40]:
                if "at " + APP in x:
                    frame = x.split("at ", 1)[1].strip()[:140]
                    break
            strict[(kind, frame)] += 1
        i += 1
    return crashes, anrs, stalls, strict

def main(dirs):
    tot = collections.Counter(); allstrict = collections.Counter(); notes = []
    for d in dirs:
        for root, _, files in os.walk(d):
            if "logcat.txt" not in files: continue
            c, a, s, st = scan(os.path.join(root, "logcat.txt"))
            label = os.path.relpath(root, os.path.dirname(d.rstrip("/")))
            tot["logs"] += 1; tot["crash"] += len(c); tot["anr"] += len(a); tot["stall"] += len(s)
            allstrict.update(st)
            for x in c:
                tag = " (intentional: CrashUiDeviceTest marker)" if "YuCrashUi marker" in x else ""
                notes.append(f"- crash `{label}`{tag}: {x}")
            for x in a: notes.append(f"- ANR `{label}`: {x}")
            for x in s:
                if "framework only" not in x: notes.append(f"- stall with app frames `{label}`: {x}")
            tot["stall_app"] += sum("framework only" not in x for x in s)
    print(f"logcats {tot['logs']}, app crashes {tot['crash']}, app ANRs {tot['anr']}, main-thread stalls {tot['stall']} (with app frames {tot['stall_app']}), "
          f"app StrictMode violations {sum(allstrict.values())}")
    print("\n".join(notes))
    print("\n| StrictMode kind | first app frame | count |\n|---|---|---|")
    for (k, f), n in allstrict.most_common(25):
        print(f"| {k} | `{f}` | {n} |")

if __name__ == "__main__":
    main(sys.argv[1:])
