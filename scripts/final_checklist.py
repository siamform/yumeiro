#!/usr/bin/env python3
"""Builds docs/MASTER-CHECKLIST-FINAL.md from INVENTORY.md, SettingsCatalog.kt and the androidTest classes,
and fills result cells from device-check REPEAT_RESULT logs (docs/evidence/final/<target>-<chunk>.txt).
Usage: final_checklist.py [--fill]   (cells stay "Not run" until a log names the class)."""
import re, os, sys, glob, json
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
inv = open(f"{ROOT}/docs/INVENTORY.md").read().splitlines()
tests_dir = f"{ROOT}/app/src/androidTest/java/dev/asfakul/yumeiro"
classes = sorted(f[:-3] for f in os.listdir(tests_dir) if f.endswith(".kt") and "@Test" in open(f"{tests_dir}/{f}").read()
                 and f != "ExampleInstrumentedTest.kt")
# Extra coverage the inventory text does not name (class -> row IDs).
EXTRA = {
 "H1":["HomeNavigationDeviceTest","FeedPositionDeviceTest","HeroCarouselLifecycleDeviceTest","FeatureSmokeDeviceTest"],
 "H4":["HomeNavigationDeviceTest","StabilityStressDeviceTest"], "H6":["BackNavigationDeviceTest"],
 "H7":["HomeNavigationDeviceTest","FeatureSmokeDeviceTest"], "H8":["ResumeAcrossProcessDeviceTest","Bug1YumixRestoreDeviceTest"],
 "H12":["WidgetUpdatesDeviceTest"], "H13":["InboxMainThreadDeviceTest"],
 "E10":["UniversalAddEcosystemsDeviceTest","ExtensionCompatDeviceTest","Nbug16ExtensionVersionDeviceTest"],
 "E12":["AdultSearchFilterDeviceTest","Nbug11MostPlayedAdultDeviceTest"], "E13":["JsTimeLimitDeviceTest"],
 "E14":["UniversalAddEcosystemsDeviceTest","VegaProviderDeviceTest"], "E8":["ExtensionPopupsDeviceTest","ExtensionSharedFileDeviceTest"],
 "P3":["PlayerSwitchDeviceTest","NewPipeCompatDeviceTest","YtQualityDeviceTest","YtQualityOfflineDeviceTest"],
 "P8":["LocalPlayerDeviceTest","ReleaseFeaturesDeviceTest"], "P13":["PlayerSettingsEffectDeviceTest","PlayerExtrasSettingsEffectDeviceTest"],
 "P14":["DecoderFaultDeviceTest","UpscaleFreezeDeviceTest","StreamFormatMismatchDeviceTest","PlaybackSessionDeviceTest"],
 "P15":["AniSkipDeviceTest"], "P16":["Anime4kGlDeviceTest","UpscaleFreezeDeviceTest"], "P17":["LocalPlayerDeviceTest"],
 "P10":["SurfaceModeDeviceTest"], "P19":["HeatGuardDeviceTest"],
 "Y1":["YumixDeviceTest","FeedPositionDeviceTest"], "Y2":["YumixDeviceTest"], "Y5":["PinnedExtensionsDeviceTest"],
 "Y7":["EpisodeStillsDeviceTest","DramaPlaybackDeviceTest","DubCountProbeDeviceTest"],
 "Y8":["YtChannelUiDeviceTest","YtChannelProbeDeviceTest"],
 "L1":["LibraryDeviceTest","LibraryTabsMainThreadDeviceTest","LibraryRotationDeviceTest"], "L3":["PosterActionsDeviceTest","LibraryPersistDeviceTest"],
 "L4":["LibraryDeviceTest","Nbug15RecentsDeviceTest"], "L5":["DownloadsScreenDeviceTest"], "L6":["LibraryDeviceTest","LibraryPersistDeviceTest"],
 "L8":["MangaLibraryMainThreadDeviceTest"],
 "M1":["LibraryPersistDeviceTest","LargeLibraryScanDeviceTest","MusicAccessDeviceTest"], "M2":["MusicModeNetworkDeviceTest","MusicTapThroughDeviceTest"],
 "M3":["MusicEndlessNetworkDeviceTest","YmPagingNetworkDeviceTest"], "M5":["MusicQueueDeviceTest","QueuePersistenceDeviceTest","MusicNotificationControlsDeviceTest","MusicAudioFocusDeviceTest","MusicWifiLockDeviceTest","SleepTimerWithoutScreenDeviceTest","MiniPlayerDeviceTest"],
 "M7":["ScrobbleFlushDeviceTest"],
 "T1":["MangaDuplicateDeviceTest","MangaRetryDeviceTest"], "T2":["MangaTallPageDeviceTest","MangaTranslateDeviceTest","ReleaseFeaturesDeviceTest"],
 "T3":["PlaylistIndexRecoveryDeviceTest","PlaylistRetryDeviceTest","LargePlaylistMemoryDeviceTest","RemotePlaylistMemoryDeviceTest","Nbug14ChannelLogoDeviceTest","Nbug15RedesignDeviceTest","FeatureSmokeDeviceTest"],
 "T4":["HeaderStreamDeviceTest","Nbug12RefusedMediaPageDeviceTest","FeatureSmokeDeviceTest"],
 "D1":["DownloadResumeDeviceTest","DownloadCleanupDeviceTest","DownloadKillDeviceTest","DownloadQualityDeviceTest","DownloadThroughputDeviceTest"],
 "D2":["ForceDownloadDeviceTest","DownloadG8DeviceTest","StreamWatchDownloadDeviceTest"], "D3":["TorrentDownloadDeviceTest"],
 "D5":["ExternalDownloaderDeviceTest"], "D6":["YtDownloadPipelineDeviceTest"], "D8":["DownloadsScreenDeviceTest","StorageMainThreadDeviceTest","LowStorageDeviceTest"],
 "S23":["SettingsWalkDeviceTest"], "S24":["SettingsResetDeviceTest"], "S25":["BackupDeviceTest","RestoreAfterClearDeviceTest"], "S26":["BackdropImportDeviceTest"],
 "S27":["SettingsWalkDeviceTest","ChoiceSettingsEffectDeviceTest","MoreSettingsEffectDeviceTest","OnlineSwitchesEffectDeviceTest","BackgroundSettingsEffectDeviceTest","NotifySettingsEffectDeviceTest","PushAlertsEffectDeviceTest"],
 "A1":["MessagesDeviceTest","FloatingMessageDeviceTest"], "A2":["DialogsDeviceTest","RetryActionsDeviceTest"], "A3":["RetryActionsDeviceTest"],
 "A4":["NetworkStateDeviceTest","LowStorageDeviceTest","PermissionFlowDeviceTest","Nbug9DnsProbeDeviceTest"], "A5":["BrowserCheckCooldownDeviceTest"],
 "I1":["TrackerSignInDeviceTest","AnimeTrackersDeviceTest"], "I2":["TrackerSignInDeviceTest","AnimeTrackersDeviceTest"], "I3":["AnimeTrackersDeviceTest","AnimeSignInMessageDeviceTest"],
 "I4":["EpisodeStillsDeviceTest"], "I5":["OnlineSubtitlesFallbackDeviceTest"], "I7":["ScrobbleFlushDeviceTest"],
 "I8":["CloudAlertsLookupDeviceTest","FcmAiringWakeDeviceTest","PushAlertsEffectDeviceTest","ClosedAppAlertDeviceTest"], "I11":["UpdateMainThreadDeviceTest"],
 "X2":["StabilityStressDeviceTest","LargePlaylistMemoryDeviceTest"], "X3":["StabilityStressDeviceTest","StorageMainThreadDeviceTest","InboxMainThreadDeviceTest","UpdateMainThreadDeviceTest","HomeTraceDeviceTest"],
 "X4":["HeatGuardDeviceTest"], "X5":["ClosedAppAlertDeviceTest","FcmAiringWakeDeviceTest"], "X6":["CrashUiDeviceTest"], "X8":["AccessibilityDeviceTest","RtlLayoutDeviceTest"],
 "X9":["TvBug5ReproDeviceTest","SheetFocusTvDeviceTest","HomeNavigationDeviceTest"], "X11":["Nbug9DnsProbeDeviceTest","Nbug6EngineCostDeviceTest","ProxyDeviceTest"],
 "N2":["SiteSignOutDeviceTest","HeaderStreamDeviceTest","FeatureSmokeDeviceTest"], "N3":["UpdateMainThreadDeviceTest"], "N6":["AppLockDeviceTest","ProfilesDeviceTest"],
 "N7":["InboxMainThreadDeviceTest"], "N10":["ProfilesDeviceTest","ReleaseFeaturesDeviceTest"],
}
ENTRY = {"G1":"Home tab / nav bar / rail","G2":"Settings → Sources → Extensions; Extension market","G3":"any title → Play (player)",
 "G4":"Videos → Yumix","G5":"Library tab","G6":"Music tab","G7":"Manga / Live TV (Videos → Live TV)","G8":"Downloads (top bar)",
 "G9":"Settings","G10":"app-wide messages and dialogs","G11":"Settings → Tracking / Notifications / Music","G12":"whole app","N":"see row"}
STATES = "happy · empty · offline · error · slow network · permission denied · process death"
rows = []; group = None; grp_id = None
for line in inv:
    m = re.match(r"## (G\d+|New groups)", line)
    if m: grp_id = m.group(1) if m.group(1) != "New groups" else "N"; group = line[3:]; continue
    if line.startswith("## Proposed") or line.startswith("## Not covered"): grp_id = None
    if not grp_id: continue
    c = [x.strip() for x in line.strip().strip("|").split("|")]
    if len(c) < 5 or not re.match(r"^[A-Z]+\d+(–S\d+)?$", c[0]): continue
    rid, item, code, status, cov = c[0], c[1], c[2], c[3], c[4]
    named = set(re.findall(r"`?([A-Z][A-Za-z0-9]+DeviceTest)", cov)) & set(classes)
    cls = sorted(named | set(EXTRA.get(rid, [])))
    rows.append(dict(id=rid if rid != "S1–S22" else "S0", item=item, entry=ENTRY.get(grp_id,""), code=code, classes=cls))
# Settings categories, one row each, with the switches and choices they hold.
cat = open(f"{ROOT}/app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/SettingsCatalog.kt").read()
rows = [r for r in rows if r["id"] != "S0"]
SET_CLASSES = ["SettingsWalkDeviceTest","ChoiceSettingsEffectDeviceTest","SettingsResetDeviceTest"]
starts = list(re.finditer(r'^\s+(\w+)\(Group\.\w+, "([^"]+)", "([^"]+)"', cat, re.M))
for i, m in enumerate(starts, 1):
    body = cat[m.end(): starts[i].start() if i < len(starts) else len(cat)]
    sm = re.search(r'settings = listOf\((.*?)\)', body, re.S)
    names = re.findall(r'"([^"]+)"', sm.group(1)) if sm else []
    rows.append(dict(id=f"S{i}", item=f"Settings: {m.group(2)}", entry=f"Settings → {m.group(2)}", code=m.group(3) + ("; options: " + ", ".join(names) if names else ""), classes=SET_CLASSES))
NEW = [
 ("F-02","Telegram public channels and post links (no login)","Live TV / paste a t.me link; Sites → Telegram",["TelegramPublicDeviceTest","TelegramPlaybackDeviceTest"]),
 ("F-03","TeraBox / DiskWala public links, re-capture of expired streams","paste a share link",["CloudRecaptureDeviceTest"]),
 ("F-05","Script repos (Vega runs; Nuvio, SkyStream recognised)","Settings → Sources → Script repos",["VegaProviderDeviceTest","UniversalAddEcosystemsDeviceTest","JsTimeLimitDeviceTest"]),
 ("F-11","Manga auto-translation","Manga reader → Translate",["MangaTranslateDeviceTest"]),
 ("F-AIR","Airing alerts through push with the app closed","Follow a show; Notifications",["FcmAiringWakeDeviceTest","ClosedAppAlertDeviceTest"]),
 ("NBUG-13","Own HTTP / SOCKS5 proxy","Settings → Network → Proxy",["ProxyDeviceTest"]),
 ("NBUG-6","Automatic DNS fallback (default Off)","Settings → Network → Automatic DNS fallback",["Nbug6EngineCostDeviceTest","Nbug9DnsProbeDeviceTest"]),
 ("F-REL","1.9.0 features: Watch Later/Favourite, Upcoming, Shorts, booster, external player, glass, offline manga, Live TV failover, Now/Next","various",["ReleaseFeaturesDeviceTest","PosterActionsDeviceTest","ProfilesDeviceTest"]),
]
for rid, item, entry, cls in NEW: rows.append(dict(id=rid, item=item, entry=entry, code="", classes=cls))
# Results.
TARGETS = [("api33-lowram","Low-RAM"),("tv","TV"),("api36","Phone")]
res = {}  # (class, target, pass) -> (verdict, run)
for f in glob.glob(f"{ROOT}/docs/evidence/final/*.txt"):
    for l in open(f):
        m = re.match(r"(\S+) (\S+) (\S+) (P[12]) (PASS|FAIL|SKIPPED) tests=(\S+) skipped=(\d+)", l.strip())
        if m:
            run, dev, cls, p, v, nt, sk = m.groups()
            tgt = "tv" if dev.startswith("tv") else dev
            res.setdefault((cls, tgt, p), []).append((dev, v, run, int(sk)))
# A cell shows the latest run per class, device and pass; earlier runs stay in docs/evidence/final/ and the
# finding that changed the result is named in the Finding column.
for k, lst in res.items():
    last = {}
    for dev, v, run, sk in lst:
        if dev not in last or int(run) > int(last[dev][2]): last[dev] = (dev, v, run, sk)
    res[k] = list(last.values())
# Findings raised by the final passes, per device class (ledger in docs/reports/2026-11-19-tenth-handover-report.md).
FINDINGS = {
    "TelegramPlaybackDeviceTest": "FINAL-1, FINAL-2",
    "CloudRecaptureDeviceTest": "FINAL-2, FINAL-7",
    "TelegramPublicDeviceTest": "FINAL-3, FINAL-5",
    "MangaTranslateDeviceTest": "FINAL-4, FINAL-6",
    "AnimeSignInMessageDeviceTest": "FINAL-8",
}
def cell(cls, tgt, p):
    if not cls: return "Not run (no device class; see note)"
    got = [res.get((c, tgt, p)) for c in cls]
    if all(g is None for g in got): return "Not run"
    flat = [x for g in got if g for x in g]
    bad = [x for x in flat if x[1] == "FAIL"]
    skipped = [x for x in flat if x[1] == "SKIPPED"]
    runs = sorted({x[2] for x in flat})
    tvs = sorted({x[0] for x in flat}) if tgt == "tv" else []
    missing = sum(1 for g in got if g is None) + (sum(1 for g in got if g and len({x[0] for x in g}) < 2) if tgt == "tv" else 0)
    v = "Fail" if bad else ("Blocked" if skipped and len(skipped) == len(flat) else ("Pass" if not missing and not skipped else "Partial"))
    part = sum(x[3] for x in flat if x[1] == "PASS")
    return f"{v} ({len(flat)-len(bad)-len(skipped)}/{len(flat)}{', ' + str(len(skipped)) + ' skipped' if skipped else ''}{', ' + str(part) + ' test(s) skipped by an assumption' if part else ''}{' ' + '+'.join(tvs) if tvs else ''}; runs {', '.join(runs)})"
out = ["# Master checklist, final test (1.9.0 release candidate)", "",
"Single file that proves the release (master prompt \"THE FINAL TEST AND RELEASE PREPARATION\", 2026-11-20).",
"Generated by `scripts/final_checklist.py` from docs/INVENTORY.md, SettingsCatalog.kt and every androidTest class;",
"result cells are filled only from device-check logs saved in docs/evidence/final/ (REPEAT_RESULT lines). A cell",
"never says Pass without a run ID. A cell shows the latest run per class, device and pass; earlier runs stay in",
"docs/evidence/final/ and the finding that changed the result is in the Finding column.", "",
"How a pass is run: `device-check.yml` with `passes: true`. P1 = cleared app data and a cold start before every class",
"(fresh install state); P2 = the same classes on the same emulator with P1's data kept, the app killed and reopened",
"(warm start, populated data). Targets: Low-RAM = api33-lowram (2 GB, 192 MB heap), Phone = api36 (Pixel 6),",
"TV = tv-api24 and tv-api34 (both must pass for a TV cell to say Pass). Labels: emulator / TV emulator; no real hardware.", "",
f"States each row is checked for: {STATES}. Which of them a class exercises is in its KDoc; states that",
"only real hardware, a personal account or a home network can show go to OWNER-MANUAL-TESTS.md (OMT-*).", "",
"Status words: Pass / Fail / Partial (some classes not run yet) / Blocked (reason) / Not run.", "",
"| ID | Feature | Entry point | Options / code | Device classes | Low-RAM P1 | Low-RAM P2 | TV P1 | TV P2 | Phone P1 | Phone P2 | Finding |",
"|---|---|---|---|---|---|---|---|---|---|---|---|"]
for r in rows:
    cs = ", ".join(c.replace("DeviceTest","") for c in r["classes"]) or "—"
    cells = [cell(r["classes"], t, p) for t,_ in TARGETS for p in ("P1","P2")]
    code = r["code"].replace("|","/")[:220]
    out.append(f"| {r['id']} | {r['item'][:120]} | {r['entry']} | {code} | {cs} | " + " | ".join(cells) + f" | {', '.join(dict.fromkeys(x for c in r['classes'] if c in FINDINGS for x in FINDINGS[c].split(', ')))} |")
used = sorted({c for r in rows for c in r["classes"]})
unused = [c for c in classes if c not in used]
out += ["", f"Rows: {len(rows)}. Device classes: {len(classes)}; mapped to a row: {len(used)}.",
        "Classes not mapped to a row (they still run in every pass): " + (", ".join(unused) or "none") + "."]
# Written reasons for every skip (assumption messages from the inst files of the runs named).
SKIP_REASONS = [
    ("tv-api24", "AdultSearchFilterDeviceTest", "\"penaltyListener needs API 28\": the main-thread StrictMode check uses StrictMode.penaltyListener (API 28+); the same class runs fully on tv-api34 and the phones (see their cells)."),
    ("tv-api24", "AniSkipDeviceTest", "\"no switchable network on this TV image\": the Android 7 TV emulator has no network the test can switch off; the same class runs fully on tv-api34 and the phones (see their cells)."),
    ("tv-api24", "AppLockDeviceTest", "b_pinOpensTheAppAndTheLockStaysOn: \"locksettings needs API 26\" (the test sets a device PIN with `cmd lock_settings`, missing on Android 7); a_ passes."),
]
out += ["", "## Skips with written reasons (§5)", "", "| Device | Class | Reason |", "|---|---|---|"]
out += [f"| {d} | {c.replace('DeviceTest','')} | {r} |" for d, c, r in SKIP_REASONS]
open(f"{ROOT}/docs/MASTER-CHECKLIST-FINAL.md","w").write("\n".join(out) + "\n")
json.dump(classes, open("/tmp/final_classes.json","w"))
print(len(rows), len(classes), len(used), unused)
