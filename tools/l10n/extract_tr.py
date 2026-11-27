#!/usr/bin/env python3
# Yumeiro — Copyright (C) 2026 siamform. GPL-3.0-or-later.
"""
F19: moves the remaining user-facing text out of Kotlin into res/values/strings.xml.

F2's extract_strings.py handled Text("…") and icon descriptions in composables. This pass handles everything else
that people read: labels passed to components, messages built in view models and resolvers, notification text,
error messages, defaults and constant message objects. Each literal becomes

    tr(R.string.key, "English")                    plain text
    tr(R.string.key, "Saved to %1$s", target.name)   text with values (Kotlin templates become %1$s, %2$s …)

and a `const val X = "…"` message becomes `val X: String get() = tr(…)`, so it follows the language too.

Literals that are data rather than text stay as they are: comparisons and `when` branches, map keys, preference
and JSON keys, HTML and script snippets, log lines, test tags, identifiers, URLs, MIME types, and code passed to
`contains`/`startsWith`/`split`/`Regex`. Single words are converted only at a UI argument (`label = "…"`, …),
because a lone word is often an id ("Telegram" as a group name).

Usage: tools/l10n/extract_tr.py [--dry-run] [--report out.tsv]
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, "app/src/main/java/dev/asfakul/yumeiro")
RES = os.path.join(ROOT, "app/src/main/res/values/strings.xml")
DRY = "--dry-run" in sys.argv
REPORT = sys.argv[sys.argv.index("--report") + 1] if "--report" in sys.argv else None

SKIP_DIRS = ("/widget",)
SKIP_FILES = {"l10n/AppLanguage.kt", "l10n/Tr.kt",
              # Genre names are also the match keys for providers' genre text; display labels are translated in GenreLabels.
              "sources/hub/GenreCatalog.kt"}

EXISTING = re.compile(r'<string name="([a-z0-9_]+)"(?: formatted="false")?>(.*?)</string>', re.S)
JAVA_KEYWORDS = set("abstract assert boolean break byte case catch char class const continue default do double else enum "
                    "extends final finally float for goto if implements import instanceof int interface long native new "
                    "package private protected public return short static strictfp super switch synchronized this throw "
                    "throws transient try void volatile while true false null".split())

LINE_SKIP = re.compile(
    r'^\s*(//|\*|/\*|import |package |@)|\bLog\.[a-z]\(|\blog\(|\blogI\(|println|\.testTag\(|testTag\s*=|'
    r'\brequire\(|\bcheck\(|\berror\(|\bassert|const val TAG\b|Regex\(|toRegex|javascript|evaluateJavascript|'
    r'document\.|window\.|function\s*\(|querySelector|\bheader\(|addHeader|"User-Agent"|putExtra|getStringExtra|'
    r'SharedPreferences|\.edit\(\)|getSharedPreferences|JSONObject|JSONArray|optString|optInt|\.put\(|Intent\(|'
    r'Uri\.parse|\.setAction|ACTION_|MediaStore|\bSQL\b|rawQuery|execSQL|@Query|Pattern\.|matches\(')

BEFORE_SKIP = re.compile(
    r'(==|!=|\bcontains\(|\bstartsWith\(|\bendsWith\(|\bequals\(|\bindexOf\(|\blastIndexOf\(|\bsplit\(|\breplace\(|'
    r'\bremovePrefix\(|\bremoveSuffix\(|\bsubstringBefore\(|\bsubstringAfter\(|\bsubstringBeforeLast\(|'
    r'\bsubstringAfterLast\(|getString\(|putString\(|getBoolean\(|getInt\(|getLong\(|\bget\(|\[|\bkey\s*=|\bid\s*=|'
    r'\btag\s*=|\broute\s*=|\bname\s*=\s*$|\bprefix\s*=|\bpackageName\s*=|\bmime\w*\s*=|\btype\s*=|setOf\(|'
    r'mapOf\(|\b(?:getValue|getOrDefault|getOrElse|containsKey|remove|method|has|field|find\w*|any|all|none|count|first\w*|matches|filter\w*)\(\s*$|listOf\(\s*$|(?:Exception|Error|Throwable)\(|\bin\s+$|\bis\s+$)\s*$')
AFTER_SKIP = re.compile(r'^\s*(->|to\b|in\b|==|!=|\.equals|\.toRegex|\.split|\.toUri|\.toPath|\]|\.lowercase|\.uppercase|\.trim)')

UI_ARG = re.compile(
    r'\b(label|title|text|subtitle|description|hint|placeholder|message|summary|caption|headline|body|action|'
    r'actionLabel|confirmText|dismissText|confirmLabel|dismissLabel|contentDescription|cd|heading|note|tip|'
    r'emptyText|emptyTitle|emptyMessage|buttonText|button|primaryLabel|secondaryLabel|tooltip|status|detail|'
    r'chip|badge|overline|eyebrow|prompt|question|answer|reason|explanation|warning)\s*=\s*$')
UI_CALL = re.compile(r'\b(Text|SectionHeader|Chip|SettingsRow|ToggleRow|ActionRow|Toast\.makeText|showSnackbar|'
                     r'setContentTitle|setContentText|setTicker|setSubText|bigText|addAction|LiveTool|Button|Header|'
                     r'Pill|Badge|Hint|Tip|Row|Item|Card|Tile|Option|Entry|Choice)\w*\(\s*([^()"]*,\s*)?$')

TEMPLATE = re.compile(r'(?<!\\)\$(?:\{([^{}"]*)\}|([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*))')
CODEY = re.compile(r'(://|\b(?:AND|OR|NOT) \w+:|\w:\(|=;|;\s*[A-Z][\w-]*=|\\r|\r|[A-Za-z]/[A-Za-z]|^[A-Z][\w-]+: |\w/\d|\(\w+;|\bHTTP/|\\u|<[a-zA-Z/!]|[{}]|=\s|^\s*[a-z0-9_.\-]+\s*$|\.kt\b|\.json\b|\.m3u8?\b|\.mp4\b|%[0-9.]*[dfx]|'
                   r'^[A-Z0-9_]+$|[a-z][A-Z][a-z]+[A-Z]|\b(val|fun|return|null|true|false)\b\s|\(\)|&&|\|\||\\\\|^(?=.*(?:MMM|yyyy|dd|HH|EEE))[EMdyHhmsazZX ,.:/\x27-]+$)')


def slug(text):
    words = re.findall(r"[a-z0-9]+", text.lower().replace("'", ""))
    s = "_".join(words[:6]) or "text"
    if s[0].isdigit():
        s = "t_" + s
    return s[:48].rstrip("_")


def xml_escape(s):
    import xml.sax.saxutils as su
    s = su.escape(s)
    s = s.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n").replace("\t", "\\t").replace("\r", "\\r")
    if s[:1] in ("@", "?"):
        s = "\\" + s
    if s != s.strip() or "  " in s:
        s = '"' + s + '"'
    return s


def xml_unescape(s):
    import xml.sax.saxutils as su
    if len(s) >= 2 and s[0] == '"' and s[-1] == '"' and s[-2] != "\\":
        s = s[1:-1]
    out, i = [], 0
    while i < len(s):
        c = s[i]
        if c == "\\" and i + 1 < len(s):
            n = s[i + 1]
            out.append({"n": "\n", "t": "\t"}.get(n, n)); i += 2; continue
        out.append(c); i += 1
    return su.unescape("".join(out))


def kotlin_unescape(s):
    out, i = [], 0
    while i < len(s):
        c = s[i]
        if c == "\\" and i + 1 < len(s):
            n = s[i + 1]
            if n == "u" and i + 5 < len(s):
                out.append(chr(int(s[i + 2:i + 6], 16))); i += 6; continue
            out.append({"n": "\n", "t": "\t", "r": "\r", "b": "\b"}.get(n, n)); i += 2; continue
        out.append(c); i += 1
    return "".join(out)


def kotlin_escape(s):
    return s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$").replace("\n", "\\n").replace("\t", "\\t")


def literals(line):
    """Top-level "…" string literals in [line] as (start, end, body, simple), simple = no nested quotes."""
    out, i, n = [], 0, len(line)
    while i < n:
        c = line[i]
        if c == '/' and line.startswith('//', i):
            break
        if c == "'":  # char literal
            j = line.find("'", i + 2 if i + 1 < n and line[i + 1] == '\\' else i + 1)
            i = (j + 1) if j > 0 else n
            continue
        if c == '"':
            if line.startswith('"""', i):
                return out  # raw strings: leave the line alone
            j, simple, depth = i + 1, True, 0
            while j < n:
                d = line[j]
                if d == '\\':
                    j += 2; continue
                if d == '$' and j + 1 < n and line[j + 1] == '{':
                    depth += 1; j += 2
                    while j < n and depth > 0:
                        e = line[j]
                        if e == '{': depth += 1
                        elif e == '}': depth -= 1
                        elif e == '"': simple = False
                        j += 1
                    continue
                if d == '"':
                    break
                j += 1
            if j >= n:
                return out  # unterminated (multi-line): leave it
            out.append((i, j + 1, line[i + 1:j], simple))
            i = j + 1
            continue
        i += 1
    return out


def as_text(body):
    """(format text for strings.xml, [args], code fallback) or None."""
    args = []

    def rep(m):
        expr = (m.group(1) or m.group(2)).strip()
        if not expr:
            raise ValueError
        args.append(expr)
        return "\x00%d\x00" % len(args)
    try:
        marked = TEMPLATE.sub(rep, body)
    except ValueError:
        return None
    plain = kotlin_unescape(TEMPLATE.sub(" ", body))
    if CODEY.search(plain) or CODEY.search(body):
        return None
    letters = re.findall(r"[^\W\d_]{2,}", plain)
    if not letters:
        return None
    text = kotlin_unescape(marked)
    if args:
        text = text.replace("%", "%%")
    fmt = re.sub("\x00(\\d+)\x00", lambda m: "%" + m.group(1) + "$s", text)
    return fmt, args


def human(fmt, ui_context):
    plain = re.sub(r"%\d+\$s", " ", fmt).strip()
    words = re.findall(r"[^\W\d_]+", plain)
    if not words:
        return False
    first = next((ch for ch in plain if ch.isalpha()), "")
    if len(words) >= 2 and " " in plain:
        if first.isupper() or fmt.startswith("%") or not first.isascii():
            return True
        return ui_context and len(words) >= 2
    # One word: only at a UI argument, Title-case, at least 3 letters.
    w = words[0]
    return ui_context and len(w) >= 3 and w[0].isupper() and (w[1:].islower() or not w.isascii())


def main():
    keys, by_text = {}, {}
    for k, v in EXISTING.findall(open(RES, encoding="utf-8").read()):
        keys[k] = xml_unescape(v)
        by_text.setdefault(xml_unescape(v), k)
    added = []

    def key_for(fmt):
        if fmt in by_text:
            return by_text[fmt]
        base = slug(re.sub(r"%\d+\$s", " ", fmt))
        if base in JAVA_KEYWORDS:
            base = "label_" + base
        k, n = base, 2
        while k in keys:
            k = f"{base}_{n}"; n += 1
        keys[k] = fmt; by_text[fmt] = k; added.append(k)
        return k

    # Texts the code compares, matches or maps anywhere: keep them English everywhere, since a
    # translated copy would silently stop matching (category == "Short drama", "Most played" -> …).
    compared = set()
    CMP_BEFORE = re.compile(r'(==|!=|\bequals\(|\bcontains\(|\bstartsWith\(|\bendsWith\(|\bin\s+setOf\(|\bwhen\s*\([^)]*\)\s*\{\s*)\s*$')
    CMP_AFTER = re.compile(r'^\s*(->|==|!=|in\b|,\s*(true|ignoreCase)\b|\.equals)')
    for dirpath, _, names in os.walk(SRC):
        for name in names:
            if name.endswith(".kt"):
                for line in open(os.path.join(dirpath, name), encoding="utf-8"):
                    for (a, b, body, simple) in literals(line):
                        if simple and (CMP_BEFORE.search(line[:a]) or CMP_AFTER.search(line[b:])):
                            t = as_text(body)
                            if t:
                                compared.add(t[0])

    report = []
    files = sites = 0
    for dirpath, _, names in os.walk(SRC):
        if any(s in dirpath.replace(os.sep, "/") for s in SKIP_DIRS):
            continue
        for name in names:
            if not name.endswith(".kt"):
                continue
            path = os.path.join(dirpath, name)
            rel = os.path.relpath(path, SRC).replace(os.sep, "/")
            if rel in SKIP_FILES:
                continue
            src = open(path, encoding="utf-8").read()
            # Glance widgets have their own resources; small HTTP servers speak protocol text, not UI text.
            if "ServerSocket" in src or "HTTP/1." in src:
                continue
            if "androidx.glance" in src:
                continue
            lines = src.split("\n")
            changed = False
            for li, line in enumerate(lines):
                if LINE_SKIP.search(line) or "tr(R.string." in line and False:
                    continue
                lits = literals(line)
                if not lits:
                    continue
                # A data table line ("Action", "Adventure", …): values, not text.
                if line.strip().startswith('"') and len(lits) >= 2:
                    continue
                new, last = [], 0
                const = re.match(r'^(\s*)((?:private |internal |public )?)const val (\w+)\s*=\s*"', line)
                prev_const = li > 0 and re.match(r'^(\s*)((?:private |internal |public )?)const val (\w+)\s*=\s*$', lines[li - 1])
                picks, blocked = [], False
                for (a, b, body, simple) in lits:
                    # Only a word that looks like a name or label ("Action", "Drama") marks a data row; keys and tags don't.
                    letters = re.fullmatch(r"[A-Z][A-Za-z'&\- ]*", body) is not None
                    before = line[:a]
                    after = line[b:]
                    if not simple or BEFORE_SKIP.search(before) or AFTER_SKIP.search(after):
                        blocked = blocked or (letters and not re.search(r'\btr\(R\.string\.\w+,\s*$', line[:a]))
                        continue
                    if re.search(r'\btr\(R\.string\.\w+,\s*$', before) or re.search(r'stringResource\(\s*$', before):
                        blocked = blocked or (letters and not re.search(r'\btr\(R\.string\.\w+,\s*$', line[:a]))
                        continue
                    t = as_text(body)
                    if t is None:
                        blocked = blocked or (letters and not re.search(r'\btr\(R\.string\.\w+,\s*$', line[:a]))
                        continue
                    fmt, args = t
                    if fmt in compared:
                        blocked = blocked or (letters and not re.search(r'\btr\(R\.string\.\w+,\s*$', line[:a]))
                        continue
                    ui = bool(UI_ARG.search(before) or UI_CALL.search(before))
                    if not human(fmt, ui):
                        blocked = blocked or (letters and not re.search(r'\btr\(R\.string\.\w+,\s*$', line[:a]))
                        continue
                    picks.append((a, b, fmt, args))
                # A row of data (28 to "Action", 878 to "Science Fiction"; g("scifi", "Science Fiction")): a value
                # stays code, so its neighbours do too, or the row is half translated and stops matching.
                if blocked and len(lits) >= 2:
                    picks = []
                for (a, b, fmt, args) in picks:
                    k = key_for(fmt)
                    fallback = kotlin_escape(fmt)
                    call = f'tr(R.string.{k}, "{fallback}"' + "".join(", " + x for x in args) + ")"
                    new.append(line[last:a]); new.append(call); last = b
                    report.append((rel, li + 1, k, fmt))
                    sites += 1
                if new:
                    new.append(line[last:])
                    out = "".join(new)
                    if const and len(lits) == 1:
                        out = re.sub(r'^(\s*)((?:private |internal |public )?)const val (\w+)\s*=\s*', r'\1\2val \3: String get() = ', out)
                    elif const:
                        continue
                    if prev_const:
                        if len(lits) != 1:
                            continue
                        lines[li - 1] = re.sub(r'const val (\w+)\s*=\s*$', r'val \1: String get() =', lines[li - 1])
                    lines[li] = out
                    changed = True
            if changed:
                out = "\n".join(lines)
                imports = []
                if "import dev.asfakul.yumeiro.l10n.tr\n" not in out and not rel.startswith("l10n/"):
                    imports.append("import dev.asfakul.yumeiro.l10n.tr")
                if "import dev.asfakul.yumeiro.R\n" not in out and not out.startswith("package dev.asfakul.yumeiro\n"):
                    imports.append("import dev.asfakul.yumeiro.R")
                if imports:
                    m = re.search(r"^import .*$", out, re.M)
                    pos = m.start() if m else out.index("\n", out.index("package ")) + 1
                    if not m:
                        out = out[:pos] + "\n" + "\n".join(imports) + "\n" + out[pos:]
                    else:
                        out = out[:pos] + "\n".join(imports) + "\n" + out[pos:]
                files += 1
                if not DRY:
                    open(path, "w", encoding="utf-8").write(out)
    if not DRY:
        res_lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
        raw = open(RES, encoding="utf-8").read()
        plurals = re.findall(r"\s*<plurals.*?</plurals>", raw, re.S)
        for k, v in keys.items():
            attr = ' formatted="false"' if "%" in v and not re.search(r"%\d+\$[sd]", v) else ""
            res_lines.append(f'    <string name="{k}"{attr}>{xml_escape(v)}</string>')
        for p in plurals:
            res_lines.append(p.rstrip("\n").lstrip("\n"))
        res_lines.append("</resources>")
        open(RES, "w", encoding="utf-8").write("\n".join(res_lines) + "\n")
    if REPORT:
        with open(REPORT, "w", encoding="utf-8") as f:
            for r in report:
                f.write("\t".join(str(x) for x in r).replace("\n", "\\n") + "\n")
    print(f"files changed: {files}, call sites: {sites}, new keys: {len(added)}, total keys: {len(keys)}")


if __name__ == "__main__":
    main()
