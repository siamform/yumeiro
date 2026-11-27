#!/usr/bin/env python3
# Yumeiro — Copyright (C) 2026 siamform. GPL-3.0-or-later.
"""
F2 (Beta Group S): moves user-visible text out of Kotlin into res/values/strings.xml.

Handled call shapes (all are composable call sites, so stringResource() is legal there):
  Text("literal")                      -> Text(stringResource(R.string.key))
  Text(text = "literal")               -> Text(text = stringResource(R.string.key))
  Text("Hi $name, ${n} left")          -> Text(stringResource(R.string.key, name, n))   (%1$s, %2$s)
  Icon(..., "content description")     -> Icon(..., stringResource(R.string.key))
  Icon(..., contentDescription = "x")  -> same
Glance widgets and third-party packages are skipped. Re-running is idempotent: existing keys with the same English
text are reused; keys are stable slugs of the text.

Usage: tools/l10n/extract_strings.py [--dry-run]
"""
import os
import re
import sys
import xml.sax.saxutils as su

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, "app/src/main/java/dev/asfakul/yumeiro")
RES = os.path.join(ROOT, "app/src/main/res/values/strings.xml")
DRY = "--dry-run" in sys.argv

# A Kotlin string literal without escapes we cannot map (backslashes other than \" are left alone).
LIT = r'"((?:[^"\\$]|\$(?:[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*|\{[^{}"]*\}))*)"'
TEXT_CALL = re.compile(r'(?<![A-Za-z0-9_])(Text\(\s*(?:text\s*=\s*)?)' + LIT + r'(?=\s*[,)])')
ICON_POS = re.compile(r'(?<![A-Za-z0-9_])(Icon\(\s*[^(),"]+(?:\([^()]*\))?[^(),"]*,\s*)' + LIT + r'(?=\s*[,)])')
ICON_NAMED = re.compile(r'(?<![A-Za-z0-9_])(Icon\([^()"]*?contentDescription\s*=\s*)' + LIT + r'(?=\s*[,)])')
TEMPLATE = re.compile(r'\$(?:\{([^{}"]*)\}|([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*))')
EXISTING = re.compile(r'<string name="([a-z0-9_]+)"(?: formatted="false")?>(.*?)</string>', re.S)


def has_letters(s):
    return any(c.isalpha() for c in s)


def slug(text):
    words = re.findall(r"[a-z0-9]+", text.lower().replace("'", ""))
    s = "_".join(words[:6]) or "text"
    if s[0].isdigit():
        s = "t_" + s
    return s[:48].rstrip("_")


def xml_escape(s):
    s = su.escape(s)
    s = s.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n")
    if s[:1] in ("@", "?"):
        s = "\\" + s
    return s


def xml_unescape(s):
    out, i = [], 0
    while i < len(s):
        c = s[i]
        if c == "\\" and i + 1 < len(s):
            n = s[i + 1]
            out.append({"n": "\n", "t": "\t"}.get(n, n)); i += 2; continue
        out.append(c); i += 1
    return su.unescape("".join(out))


def load_existing():
    keys, by_text = {}, {}
    if os.path.exists(RES):
        for k, v in EXISTING.findall(open(RES, encoding="utf-8").read()):
            keys[k] = xml_unescape(v)
            by_text.setdefault(xml_unescape(v), k)
    return keys, by_text


keys, by_text = load_existing()
added = []


JAVA_KEYWORDS = set("abstract assert boolean break byte case catch char class const continue default do double else enum "
                    "extends final finally float for goto if implements import instanceof int interface long native new "
                    "package private protected public return short static strictfp super switch synchronized this throw "
                    "throws transient try void volatile while true false null".split())


def key_for(fmt_text):
    if fmt_text in by_text:
        return by_text[fmt_text]
    base = slug(re.sub(r"%\d+\$s", " ", fmt_text))
    if base in JAVA_KEYWORDS:  # aapt2 rejects resource names that are Java keywords
        base = "label_" + base
    k, n = base, 2
    while k in keys:
        k = f"{base}_{n}"; n += 1
    keys[k] = fmt_text; by_text[fmt_text] = k; added.append(k)
    return k


def convert(lit):
    """Returns (format text, [args]) or None when the literal is not user text."""
    args = []

    def rep(m):
        expr = (m.group(1) or m.group(2)).strip()
        if not expr:
            raise ValueError
        args.append(expr)
        return f"%{len(args)}$s"
    templated = TEMPLATE.search(lit) is not None
    try:
        fmt = TEMPLATE.sub(rep, lit.replace("%", "%%") if templated else lit)
    except ValueError:
        return None
    plain = TEMPLATE.sub("", lit)
    if not has_letters(plain) or len(plain.strip()) < 2:
        return None
    return fmt, args


changed_files = 0
total = 0
for dirpath, _, files in os.walk(SRC):
    if "/widget" in dirpath.replace(os.sep, "/"):
        continue
    for name in files:
        if not name.endswith(".kt"):
            continue
        path = os.path.join(dirpath, name)
        src = open(path, encoding="utf-8").read()
        if "androidx.glance" in src:
            continue
        count = 0

        def sub(m):
            global total
            nonlocal_count[0] += 1
            c = convert(m.group(2))
            if c is None:
                nonlocal_count[0] -= 1
                return m.group(0)
            fmt, args = c
            k = key_for(fmt)
            call = f"stringResource(R.string.{k}" + "".join(", " + a for a in args) + ")"
            return m.group(1) + call

        nonlocal_count = [0]
        out = src
        for rx in (TEXT_CALL, ICON_NAMED, ICON_POS):
            out = rx.sub(sub, out)
        if out != src:
            imports = []
            if "import androidx.compose.ui.res.stringResource" not in out:
                imports.append("import androidx.compose.ui.res.stringResource")
            if not out.startswith("package dev.asfakul.yumeiro\n") and "import dev.asfakul.yumeiro.R\n" not in out:
                imports.append("import dev.asfakul.yumeiro.R")
            if imports:
                m = re.search(r"^import .*$", out, re.M)
                pos = m.start() if m else out.index("\n", out.index("package ")) + 1
                out = out[:pos] + "\n".join(imports) + "\n" + out[pos:]
            changed_files += 1
            total += nonlocal_count[0]
            if not DRY:
                open(path, "w", encoding="utf-8").write(out)

lines = ["<?xml version=\"1.0\" encoding=\"utf-8\"?>", "<resources>"]
for k, v in keys.items():
    attr = ' formatted="false"' if "%" in v and not re.search(r"%\d+\$s", v) else ""
    lines.append(f'    <string name="{k}"{attr}>{xml_escape(v)}</string>')
lines.append("</resources>")
if not DRY:
    open(RES, "w", encoding="utf-8").write("\n".join(lines) + "\n")
print(f"files changed: {changed_files}, call sites: {total}, new keys: {len(added)}, total keys: {len(keys)}")
