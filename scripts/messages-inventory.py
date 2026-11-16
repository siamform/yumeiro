#!/usr/bin/env python3
# Yumeiro — Copyright (C) 2026 siamform. GPL-3.0-or-later.
"""G10: lists every user-facing in-app message in app/src/main (AppMessages.* and Toast.makeText).

Writes docs/evidence/round3/messages-inventory.md (one row per call site) and
app/src/androidTest/assets/messages.tsv (kind, site, sample text, action label) for
MessagesDeviceTest, which shows each text through the real message host on an emulator.
Template parts (${...} / $name) become "Example item name". Non-literal texts are listed as
"(computed)" and are covered by the mapper unit tests (ErrorTaxonomyTest) instead.
Run from the repo root: python3 scripts/messages-inventory.py
"""
import os, re

ROOT = "app/src/main/java/dev/asfakul/yumeiro"
CALL = re.compile(r"AppMessages\.(show|error|success|info|warning)\(|Toast\.makeText\(")


def close_paren(s, i):
    """Index of the ')' matching the '(' at s[i-1], skipping strings and ${...}."""
    depth, j, n = 1, i, len(s)
    while j < n:
        c = s[j]
        if c == '"':
            j = skip_string(s, j)
            continue
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return j
        j += 1
    return n


def skip_string(s, j):
    """s[j] is '"'; returns the index after the string literal (handles ${...} with nesting)."""
    if s.startswith('"""', j):
        k = s.find('"""', j + 3)
        return n_or(k + 3, s)
    j += 1
    while j < len(s):
        c = s[j]
        if c == "\\":
            j += 2
            continue
        if c == "$" and j + 1 < len(s) and s[j + 1] == "{":
            depth, j = 1, j + 2
            while j < len(s) and depth:
                if s[j] == '"':
                    j = skip_string(s, j)
                    continue
                depth += {"{": 1, "}": -1}.get(s[j], 0)
                j += 1
            continue
        if c == '"':
            return j + 1
        j += 1
    return j


def n_or(k, s):
    return k if k > 2 else len(s)


def split_args(a):
    out, depth, cur, j = [], 0, "", 0
    while j < len(a):
        c = a[j]
        if c == '"':
            k = skip_string(a, j)
            cur += a[j:k]
            j = k
            continue
        if c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
        if c == "," and depth == 0:
            out.append(cur.strip())
            cur = ""
        else:
            cur += c
        j += 1
    if cur.strip():
        out.append(cur.strip())
    return out


def literal(arg):
    """Sample text for a string-literal argument (or concatenation of literals), else None."""
    parts = [p.strip() for p in split_args_plus(arg)]
    if not parts or not all(p.startswith('"') and p.endswith('"') for p in parts):
        return None
    txt = ""
    for p in parts:
        body = p[1:-1]
        body = re.sub(r"\$\{(?:[^{}]|\{[^{}]*\})*\}", "Example item name", body)
        body = re.sub(r"\$[A-Za-z_][A-Za-z0-9_.]*", "Example item name", body)
        body = body.replace('\\"', '"').replace("\\n", " ").replace("\\$", "$")
        txt += body
    return txt


def split_args_plus(arg):
    out, depth, cur, j = [], 0, "", 0
    while j < len(arg):
        c = arg[j]
        if c == '"':
            k = skip_string(arg, j)
            cur += arg[j:k]
            j = k
            continue
        if c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
        if c == "+" and depth == 0:
            out.append(cur)
            cur = ""
        else:
            cur += c
        j += 1
    out.append(cur)
    return out


rows = []
for d, _, files in os.walk(ROOT):
    for f in sorted(files):
        if not f.endswith(".kt"):
            continue
        p = os.path.join(d, f)
        s = open(p, encoding="utf-8").read()
        for m in CALL.finditer(s):
            end = close_paren(s, m.end())
            args = split_args(s[m.end():end])
            line = s.count("\n", 0, m.start()) + 1
            site = f"{os.path.relpath(p, ROOT)}:{line}"
            if m.group(1) is None:
                kind, text_arg = "toast", (args[1] if len(args) > 1 else "")
            else:
                kind, text_arg = m.group(1), (args[1] if len(args) > 1 else "")
            if site.startswith("ui/components/StateViews.kt"):
                continue  # the host's own fallback Toast
            if kind == "show":
                k = next((a for a in args[2:] if "Kind." in a), "")
                kind = k.rsplit(".", 1)[-1].lower() if k else "info"
                if kind not in ("info", "success", "error", "warning"):
                    kind = "info"
            action = next((a.split("=", 1)[1].strip().strip('"') for a in args if a.startswith("actionLabel")), "")
            if not action and kind == "error" and len(args) > 2 and args[2].startswith('"'):
                action = args[2].strip('"')
            rows.append((kind, site, literal(text_arg) if text_arg else None, action, text_arg))

os.makedirs("docs/evidence/round3", exist_ok=True)
os.makedirs("app/src/androidTest/assets", exist_ok=True)
with open("app/src/androidTest/assets/messages.tsv", "w", encoding="utf-8") as t:
    for kind, site, txt, action, _ in rows:
        if txt:
            t.write(f"{kind}\t{site}\t{txt}\t{action}\n")
lit = sum(1 for r in rows if r[2])
with open("docs/evidence/round3/messages-inventory.md", "w", encoding="utf-8") as o:
    o.write("# In-app messages inventory (G10)\n\n")
    o.write("Generated by `scripts/messages-inventory.py` from app/src/main. ")
    o.write(f"{len(rows)} call sites; {lit} with literal text (shown on an emulator by "
            "`MessagesDeviceTest` from `androidTest/assets/messages.tsv`); the rest are computed "
            "(exception text through `OnlineErrors.friendly` / ErrorHelp, covered by ErrorTaxonomyTest).\n\n")
    o.write("| Kind | Site | Text (template parts = Example item name) | Action |\n|---|---|---|---|\n")
    for kind, site, txt, action, raw in rows:
        shown = txt if txt else "(computed) `" + raw.replace("|", "\\|")[:90] + "`"
        o.write(f"| {kind} | {site} | {shown.replace('|', chr(92) + '|')} | {action} |\n")
print(len(rows), "sites,", lit, "literal")
