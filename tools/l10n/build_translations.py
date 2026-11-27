#!/usr/bin/env python3
"""F2: turn reviewed translation tables into Android string resources.

tools/l10n/translations/<lang>.tsv holds one `key<TAB>text` row per English
string, in plain text (no XML escaping; a literal \\n stands for a line break). This script writes
app/src/main/res/values-<lang>/strings.xml. It refuses to write when a row is
missing, a key is unknown, or the format placeholders differ from English,
so a broken translation never reaches a build. No machine translation is
involved: every row is written and reviewed by a person.

Usage: python3 tools/l10n/build_translations.py [lang ...]
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res"
TABLES = Path(__file__).resolve().parent / "translations"
PH = re.compile(r"%(?:\d+\$)?[sd]|%%")


def unescape(v: str) -> str:
    v = v.strip()
    if len(v) >= 2 and v[0] == '"' and v[-1] == '"':
        v = v[1:-1]
    out, i = [], 0
    while i < len(v):
        c = v[i]
        if c == "\\" and i + 1 < len(v):
            n = v[i + 1]
            out.append({"n": "\n", "t": "\t"}.get(n, n))
            i += 2
            continue
        out.append(c)
        i += 1
    return "".join(out)


def escape(v: str) -> str:
    s = (v.replace("\\", "\\\\").replace("&", "&amp;").replace("<", "&lt;")
         .replace(">", "&gt;").replace('"', '\\"').replace("'", "\\'")
         .replace("\n", "\\n"))
    if s[:1] in ("@", "?"):
        s = "\\" + s
    if v != v.strip() or "  " in v:
        s = '"' + s + '"'
    return s


def english():
    tree = ET.parse(RES / "values/strings.xml")
    return {e.get("name"): unescape(e.text or "") for e in tree.getroot().iter("string")
            if e.get("translatable") != "false"}


def placeholders(v: str):
    return sorted(PH.findall(v))


def build(lang: str, en: dict) -> int:
    rows = {}
    for n, line in enumerate((TABLES / f"{lang}.tsv").read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        key, _, text = line.partition("\t")
        if key in rows:
            sys.exit(f"{lang}.tsv:{n}: duplicate key {key}")
        # A literal backslash-n in a table row stands for a line break.
        rows[key] = text.replace("\\n", "\n")
    errors = []
    for key in en:
        if key not in rows:
            errors.append(f"missing {key}")
        elif placeholders(rows[key]) != placeholders(en[key]):
            errors.append(f"placeholders differ in {key}: {placeholders(en[key])} vs {placeholders(rows[key])}")
    errors += [f"unknown key {k}" for k in rows if k not in en]
    if errors:
        print("\n".join(f"{lang}: {e}" for e in errors))
        return 1
    out = RES / f"values-{lang}/strings.xml"
    out.parent.mkdir(parents=True, exist_ok=True)
    body = "\n".join(f'    <string name="{k}">{escape(rows[k])}</string>' for k in en)
    out.write_text('<?xml version="1.0" encoding="utf-8"?>\n'
                   f"<!-- Generated from tools/l10n/translations/{lang}.tsv by build_translations.py. Edit the table, not this file. -->\n"
                   f"<resources>\n{body}\n</resources>\n", encoding="utf-8")
    print(f"{lang}: {len(en)} strings -> {out.relative_to(ROOT)}")
    return 0


def main():
    en = english()
    langs = sys.argv[1:] or sorted(p.stem for p in TABLES.glob("*.tsv"))
    sys.exit(max((build(l, en) for l in langs), default=0))


if __name__ == "__main__":
    main()
