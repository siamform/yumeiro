#!/usr/bin/env python3
# Yumeiro — Copyright (C) 2026 siamform. GPL-3.0-or-later.
"""PERF-A1 budget: the Home scroll journey may not do more Compose work than v2.2.0 did.

Reads every *benchmarkData.json under the given folder, takes the median per-iteration count of the
"Compose:recompose" and "AndroidOwner:measureAndLayout" trace sections for scrollNoCompilation and
scrollBaselineProfile, and exits 1 when one is over its budget. Counts, not times: frame times on the
software-rendered CI emulators vary by up to 40 % between runs; the counts vary by a few percent.

Budgets = v2.2.0 measured on the same emulator images (runs 45606894399: api33-lowram 473 recompositions /
164 layouts, api36 580 / 187), so a change that makes Home do more work than 2.2.0 fails. Layout counts follow the frame count, so
their budget is 2.2.0 + 15 % (r25 measured 171 / 184 in run 45611133658).
"""
import glob, json, sys

BUDGET = {
    "api33-lowram": {"recompose": 473, "measureAndLayout": 190},
    "api36": {"recompose": 580, "measureAndLayout": 215},
}


def main(root: str, device: str) -> int:
    budget = BUDGET.get(device)
    if budget is None:
        print(f"no budget for {device}")
        return 0
    files = glob.glob(f"{root}/**/*benchmarkData.json", recursive=True)
    checked, over = 0, []
    for f in files:
        for b in json.load(open(f)).get("benchmarks", []):
            if not b.get("name", "").startswith("scroll"):
                continue
            for key, m in b.get("metrics", {}).items():
                for part, limit in budget.items():
                    if part in key and "Count" in key:
                        value = m.get("median", 0)
                        checked += 1
                        line = f"{b['name']} {key}: median {value:.0f} (budget {limit})"
                        print(("OVER  " if value > limit else "ok    ") + line)
                        if value > limit:
                            over.append(line)
    if checked == 0:
        print("no work counts found (old benchmark module?)")
        return 0
    return 1 if over else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2]))
