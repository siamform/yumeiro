#!/usr/bin/env python3
"""Builds the Yumeiro 2.1.0 changelog PDF with image proof.

Usage: python3 scripts/release_pdf_210.py <proof-shots-dir> <kit-images-dir> <out.pdf>
Reads CHANGELOG.md (v2.1.0 section), docs/MAJOR-BUGS-AND-FEATURES-2.x.md and docs/BUGS-AND-FIXES-2.0.0.md.
Proof images and their runs are listed in PROOF below (each from a CI device-test run on the Android emulator).
"""
import os, re, sys, html, subprocess
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.lib import colors
from reportlab.lib.styles import ParagraphStyle
from reportlab.platypus import (SimpleDocTemplate, Paragraph, Spacer, Image, Table, TableStyle, PageBreak, KeepTogether)
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont

SHOTS, KIT, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
FD = "app/src/main/res/font"
pdfmetrics.registerFont(TTFont("Man", "/usr/share/fonts/truetype/roboto/unhinted/RobotoTTF/Roboto-Regular.ttf"))
pdfmetrics.registerFont(TTFont("ManB", "/usr/share/fonts/truetype/roboto/unhinted/RobotoTTF/Roboto-Bold.ttf"))
pdfmetrics.registerFont(TTFont("Unb", f"{FD}/unbounded_700.ttf"))
from reportlab.lib.fonts import addMapping
addMapping("Man", 0, 0, "Man"); addMapping("Man", 1, 0, "ManB"); addMapping("Man", 0, 1, "Man"); addMapping("Man", 1, 1, "ManB")

INK = colors.HexColor("#16161A"); GOLD = colors.HexColor("#B8862B"); MUTE = colors.HexColor("#5C5866"); LINE = colors.HexColor("#E4DED2")
st = {
    "h1": ParagraphStyle("h1", fontName="Unb", fontSize=24, leading=30, textColor=INK, spaceAfter=8),
    "h2": ParagraphStyle("h2", fontName="Unb", fontSize=15, leading=20, textColor=INK, spaceBefore=10, spaceAfter=6),
    "h3": ParagraphStyle("h3", fontName="ManB", fontSize=11.5, leading=15, textColor=GOLD, spaceBefore=8, spaceAfter=3),
    "p": ParagraphStyle("p", fontName="Man", fontSize=9.6, leading=13.6, textColor=INK, spaceAfter=4),
    "li": ParagraphStyle("li", fontName="Man", fontSize=9.6, leading=13.6, textColor=INK, leftIndent=12, bulletIndent=2, spaceAfter=2),
    "small": ParagraphStyle("small", fontName="Man", fontSize=7.6, leading=10, textColor=MUTE),
    "cell": ParagraphStyle("cell", fontName="Man", fontSize=7.2, leading=9.4, textColor=INK),
    "cellb": ParagraphStyle("cellb", fontName="ManB", fontSize=7.2, leading=9.4, textColor=INK),
    "cap": ParagraphStyle("cap", fontName="Man", fontSize=7.6, leading=10, textColor=INK),
}
RUN = re.compile(r"\b(4[3-5]\d{9})\b")


def esc(t):
    t = html.escape(t, quote=False)
    t = re.sub(r"`([^`]+)`", r"<font face='Man'>\1</font>", t)
    t = re.sub(r"\*\*([^*]+)\*\*", r"<b>\1</b>", t)
    return RUN.sub(lambda m: f"<a href='https://github.com/siamform/yumeiro/actions/runs/{m.group(1)}' color='#1B5FA8'>{m.group(1)}</a>", t)


def trim(t, n):
    return t if len(t) <= n else t[:n].rsplit(" ", 1)[0] + " … (full text in CHANGELOG-FULL.md)"


def rows(path):
    out = []; hdr = None; sec = ""
    for line in open(path):
        if line.startswith("#"): sec = line.strip("# \n")
        if not line.startswith("|"): continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if set(cells[0]) <= set("-: "): continue
        if cells[0] in ("ID", "Status", "#"): hdr = ["ID"] + cells[1:]; continue
        if hdr: d = dict(zip(hdr, cells)); d["_sec"] = sec; out.append(d)
    return out


PROOF = [  # (file in SHOTS, feature, test, run)
    ("dl_popup", "New download popup (OL-5, OL-6): Offline / Permanent, real qualities", "PopupQualityDeviceTest, api36", "44929260054"),
    ("home_shortcuts", "More Home shortcuts (OL-9): Telegram, Profiles, Your stats, Airing soon", "MoreDestinationsDeviceTest, api36", "44926513936"),
    ("nav_pages", "Upcoming page opened from the nav bar (OL-10)", "MoreDestinationsDeviceTest, api36", "44926513936"),
    ("telegram_page", "Separate Telegram page (F-02 split)", "F02SplitDeviceTest, api36", "44929260054"),
    ("websites_page", "Separate Websites page (F-02 split)", "F02SplitDeviceTest, api36", "44929260054"),
    ("live_country", "Live TV country filter (sample playlist)", "LiveCountryDeviceTest, api36", "44929260054"),
    ("website_panel", "Website stream panel (OL-1 to OL-4, test page)", "StreamWatchDownloadDeviceTest, api36", "44926513936"),
    ("website_prompt", "Website download asks Offline / Permanent first (OL-4)", "StreamWatchDownloadDeviceTest, api36", "44929260054"),
    ("manga_next", "Manga previous / next chapter (F-18)", "F18MangaChaptersDeviceTest, api36", "44929260054"),
    ("shorts_swipe", "Vertical Shorts player swipe (F-12)", "F12ShortsSwipeDeviceTest, api36", "44912687672"),
    ("stats", "Your statistics (F-17)", "ReleaseFeaturesDeviceTest, api36", "44912687672"),
    ("site_search", "Website list search (F-04)", "ReleaseFeaturesDeviceTest, api36", "44912687672"),
]


def page_deco(c, d):
    c.saveState(); c.setFont("Man", 7.5); c.setFillColor(MUTE)
    c.drawString(18 * mm, 10 * mm, "Yumeiro 2.1.0 changelog with image proof  ·  github.com/siamform/yumeiro  ·  Made in Bangladesh")
    c.drawRightString(A4[0] - 18 * mm, 10 * mm, str(d.page)); c.restoreState()


def build():
    head = subprocess.run(["git", "rev-parse", "--short=8", "HEAD"], capture_output=True, text=True).stdout.strip()
    doc = SimpleDocTemplate(OUT, pagesize=A4, leftMargin=18 * mm, rightMargin=18 * mm, topMargin=16 * mm, bottomMargin=18 * mm,
                            title="Yumeiro 2.1.0 changelog with image proof", author="Perplexity Computer",
                            subject="Yumeiro 2.0.0 to 2.1.0: changes, tests and run IDs")
    W = A4[0] - 36 * mm; s = []
    s.append(Paragraph("Yumeiro 2.1.0", st["h1"]))
    s.append(Paragraph("Changelog from 2.0.0 to 2.1.0, with image proof and test runs", st["h2"]))
    s.append(Paragraph(f"Soft-test build from branch r15-stability at {head}; not released yet. Every run ID links to its GitHub "
                       "Actions run. Images are real app screens captured by the automatic device tests on the Android emulator.", st["p"]))
    hero = os.path.join(KIT, "hero-readme-1600x800.png")
    if os.path.exists(hero): s += [Spacer(1, 6), Image(hero, W, W / 2)]
    s.append(PageBreak())
    # Part 1: user changelog
    txt = open("CHANGELOG.md").read(); a = txt.index("## v2.1.0"); b = txt.index("## v2.0.0")
    s.append(Paragraph("1. What changed (user notes)", st["h2"]))
    para = []
    for line in txt[a:b].split("\n")[1:]:
        if line.startswith("### "):
            if para: s.append(Paragraph(esc(" ".join(para)), st["p"])); para = []
            s.append(Paragraph(esc(line[4:]), st["h3"]))
        elif line.startswith("- "):
            if para: s.append(Paragraph(esc(" ".join(para)), st["p"] if not para[0].startswith("•") else st["li"])); para = []
            para = ["•" + line[1:]]
        elif line.startswith("  ") and para:
            para.append(line.strip())
        elif not line.strip():
            if para: s.append(Paragraph(esc(" ".join(para)), st["li"] if para[0].startswith("•") else st["p"])); para = []
        else:
            para.append(line.strip())
    if para: s.append(Paragraph(esc(" ".join(para)), st["p"]))
    s.append(PageBreak())
    # Part 2: image proof
    s.append(Paragraph("2. Image proof", st["h2"]))
    s.append(Paragraph("Each screen below was captured by the named device test in the named run (Android 16 phone emulator, "
                       "fresh install, test data where noted).", st["p"]))
    cells = []; iw = W / 4; ih = iw * 2400 / 1080
    for f, feat, test, run in PROOF:
        p = os.path.join(SHOTS, f + ".png")
        if not os.path.exists(p): continue
        cells.append([Image(p, iw - 8, ih - 8 * 2400 / 1080), Paragraph(f"<b>{esc(feat)}</b><br/>{esc(test)}, run {esc(run)}", st["cap"])])
    grid = []
    for i in range(0, len(cells), 4):
        row = cells[i:i + 4] + [["", ""]] * (4 - len(cells[i:i + 4]))
        grid.append([r[0] for r in row]); grid.append([r[1] for r in row])
    if grid:
        t = Table(grid, colWidths=[iw] * 4)
        t.setStyle(TableStyle([("VALIGN", (0, 0), (-1, -1), "TOP"), ("ALIGN", (0, 0), (-1, -1), "CENTER"), ("BOTTOMPADDING", (0, 0), (-1, -1), 6)]))
        s.append(t)
    s.append(PageBreak())
    # Part 3: must-do list
    s.append(Paragraph("3. The 2.x must-do list: status of every item", st["h2"]))
    cur = None; data = []
    def flush():
        if data:
            t = Table([[Paragraph("<b>ID</b>", st["cellb"]), Paragraph("<b>Item</b>", st["cellb"]), Paragraph("<b>Status and proof</b>", st["cellb"])]] + data,
                      colWidths=[24 * mm, 52 * mm, W - 76 * mm], repeatRows=1)
            t.setStyle(TableStyle([("VALIGN", (0, 0), (-1, -1), "TOP"), ("LINEBELOW", (0, 0), (-1, -1), 0.3, LINE),
                                   ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#F6F1E6"))]))
            s.append(t)
    for r in rows("docs/MAJOR-BUGS-AND-FEATURES-2.x.md"):
        if "Meaning" in r: continue
        if r["_sec"] != cur:
            flush(); data = []; cur = r["_sec"]; s.append(Paragraph(esc(cur), st["h3"]))
        item = r.get("Problem") or r.get("Feature") or r.get("Bug") or r.get("Epic") or r.get("Bug / feature", "")
        stt = r.get("Status", "")
        ev = r.get("Evidence") or r.get("What exists") or ""
        if ev and ev not in ("—", "-"): stt += f" — evidence: {ev}"
        miss = r.get("Missing (must do)", "")
        if miss and miss not in ("—", "-"): stt += f" — still to do: {miss}"
        data.append([Paragraph(esc(r["ID"]), st["cellb"]), Paragraph(esc(trim(item, 300)), st["cell"]), Paragraph(esc(trim(stt, 700)), st["cell"])])
    flush(); s.append(PageBreak())
    # Part 4: ledger
    s.append(Paragraph("4. Every bug and fix since 2.0.0 (ledger)", st["h2"]))
    s.append(Paragraph("Fail-before: the new test failed on the old code. Pass-after: it passed on the fix. Long rows are shortened; "
                       "full text in docs/release-2.1/CHANGELOG-FULL.md and docs/BUGS-AND-FIXES-2.0.0.md.", st["p"]))
    data = []
    for r in rows("docs/BUGS-AND-FIXES-2.0.0.md"):
        vals = [v for k, v in r.items() if k not in ("ID", "_sec")]
        bug = r.get("Bug", vals[0] if vals else ""); stt = r.get("Status", "") or " ".join(vals[1:])
        data.append([Paragraph(esc(r["ID"]), st["cellb"]), Paragraph(esc(trim(bug, 320)), st["cell"]),
                     Paragraph(esc(r.get("Fix commit", "")[:40]), st["cell"]), Paragraph(esc(trim(stt, 650)), st["cell"])])
    t = Table([[Paragraph(f"<b>{h}</b>", st["cellb"]) for h in ("ID", "Bug or finding", "Commit", "Status and proof")]] + data,
              colWidths=[24 * mm, 56 * mm, 18 * mm, W - 98 * mm], repeatRows=1)
    t.setStyle(TableStyle([("VALIGN", (0, 0), (-1, -1), "TOP"), ("LINEBELOW", (0, 0), (-1, -1), 0.3, LINE),
                           ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#F6F1E6"))]))
    s.append(t)
    doc.build(s, onFirstPage=page_deco, onLaterPages=page_deco)
    print(OUT)


build()
