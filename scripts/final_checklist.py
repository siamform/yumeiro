#!/usr/bin/env python3
"""Builds docs/MASTER-CHECKLIST-FINAL.md from INVENTORY.md, SettingsCatalog.kt and the androidTest classes,
and fills result cells from device-check REPEAT_RESULT logs (docs/evidence/final/<target>-<chunk>.txt).
Usage: final_checklist.py [--fill]   (cells stay "Not run" until a log names the class)."""
import re, os, sys, glob, json
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
inv = open(f"{ROOT}/docs/INVENTORY.md").read().splitlines()
tests_dir = f"{ROOT}/app/src/androidTest/java/dev/asfakul/yumeiro"
classes = sorted(f[:-3] for f in os.listdir(tests_dir) if f.endswith(".kt") and "@Test" in open(f"{tests_dir}/{f}").read()
                 and f not in ("ExampleInstrumentedTest.kt", "ReleaseShotsDeviceTest.kt"))  # release images only
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
 "T1":["MangaDuplicateDeviceTest","MangaRetryDeviceTest","MangaOfflineDeviceTest"], "T2":["MangaTallPageDeviceTest","MangaTranslateDeviceTest","ReleaseFeaturesDeviceTest"],
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
 # Rows whose classes exist but were not named in INVENTORY (mapped 2026-11-20 from the test names; FinalCoverage is new).
 "H2":["HomeNavigationDeviceTest","ResumeAcrossProcessDeviceTest"], "H3":["HomeNavigationDeviceTest"],
 "H5":["HomeNavigationDeviceTest","TvBug5ReproDeviceTest"], "H9":["FinalCoverageDeviceTest"],
 "H10":["HomeNavigationDeviceTest","ReleaseFeaturesDeviceTest","TvBug5ReproDeviceTest"],
 "E2":["ExtensionsDeviceTest"], "E3":["ExtensionsDeviceTest"], "E4":["ExtensionsDeviceTest","ExtensionSharedFileDeviceTest"],
 "E6":["ExtensionsDeviceTest"], "E7":["ExtensionsDeviceTest"], "E9":["ExtensionsDeviceTest"], "E11":["ExtensionsDeviceTest"],
 "Y3":["YumixDeviceTest"], "Y4":["DramaPlaybackDeviceTest"], "L2":["BackgroundSettingsEffectDeviceTest"],
 "L9":["LibraryEmptyProfileDeviceTest","YumixDeviceTest"], "L10":["LocalPlayerDeviceTest","ChoiceSettingsEffectDeviceTest"],
 "M4":["OnlineSwitchesEffectDeviceTest"], "M6":["FinalCoverageDeviceTest"], "M8":["YtDownloadPipelineDeviceTest"],
 "M9":["FinalCoverageDeviceTest"], "T5":["FinalCoverageDeviceTest"], "D7":["SurfaceModeDeviceTest","StorageMainThreadDeviceTest"],
 "I6":["OnlineSwitchesEffectDeviceTest"], "I9":["FinalCoverageDeviceTest"], "I10":["FinalCoverageDeviceTest"],
 "X12":["FinalCoverageDeviceTest","Nbug14ChannelLogoDeviceTest"], "X13":["FinalCoverageDeviceTest","NewPipeCompatDeviceTest"],
 "X15":["FeedPositionDeviceTest","YumixDeviceTest"], "N1":["FinalCoverageDeviceTest"], "N4":["RetryActionsDeviceTest"],
 "N8":["HomeNavigationDeviceTest"], "N9":["FinalCoverageDeviceTest"],
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
 ("F-REL","1.9.0 features: Watch Later/Favourite, Upcoming, Shorts, booster, external player, glass, offline manga, Live TV failover, Now/Next","various",["ReleaseFeaturesDeviceTest","PosterActionsDeviceTest","ProfilesDeviceTest","MangaOfflineDeviceTest","FinalCoverageDeviceTest","LocalPlayerDeviceTest"]),
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
    "LocalPlayerDeviceTest": "FINAL-16, FINAL-16c, FINAL-25",
    "LargeLibraryScanDeviceTest": "FINAL-24",
    "LibraryEmptyProfileDeviceTest": "FINAL-24",
    "MiniPlayerDeviceTest": "FINAL-21, FINAL-21b",
    "DramaPlaybackDeviceTest": "FINAL-22",
    "PlaybackSessionDeviceTest": "FINAL-23, FINAL-23b",
    "NotifySettingsEffectDeviceTest": "FINAL-29",
    "MusicAudioFocusDeviceTest": "FINAL-24c",
    "MusicNotificationControlsDeviceTest": "FINAL-24c",
    "SleepTimerWithoutScreenDeviceTest": "FINAL-24c",
    "QueuePersistenceDeviceTest": "FINAL-24c, FINAL-32",
    "ChoiceSettingsEffectDeviceTest": "FINAL-27",
    "PlayerExtrasSettingsEffectDeviceTest": "FINAL-27",
    "PlayerSettingsEffectDeviceTest": "FINAL-27",
    "ResumeAcrossProcessDeviceTest": "FINAL-28, FINAL-28b, FINAL-32",
    "TvBug5ReproDeviceTest": "FINAL-31",
    "QualityListDeviceTest": "FINAL-32",
    "YmPagingNetworkDeviceTest": "FINAL-33",
    "MusicModeNetworkDeviceTest": "FINAL-34",
    "MusicEndlessNetworkDeviceTest": "FINAL-34",
    "SurfaceModeDeviceTest": "FINAL-34",
    "MessagesDeviceTest": "FINAL-34",
    "StabilityStressDeviceTest": "FINAL-35",
    "FinalCoverageDeviceTest": "FINAL-36",
    "RtlLayoutDeviceTest": "FINAL-37",
    "RetryActionsDeviceTest": "FINAL-34, FINAL-38, FINAL-42",
    "PermissionFlowDeviceTest": "FINAL-39, FINAL-43",
    "FeedPositionDeviceTest": "TV-FP-1",
    "MiniPlayerDeviceTest": "FINAL-44",
    "UpscaleFreezeDeviceTest": "FINAL-45",
    "MusicAccessDeviceTest": "FINAL-44",
    "YtChannelUiDeviceTest": "YT-WALL (OMT-29)",
    "YtQualityDeviceTest": "YT-WALL (OMT-29)",
}
# Written reasons for every class that was skipped, in whole or in part, on a device (assumption messages from the
# logcat of the run named in the cell; collected 2026-11-20). kind "na": the feature or check does not exist on that
# device by design, so the cell can still Pass; kind "blocked": the check could not run there; the cell says Blocked
# and the item is in docs/UNVERIFIED-QUEUE.md (FT-*) with its owner test.
TV = ("tv-api24", "tv-api34"); PH = ("api33-lowram", "api36"); ALL = TV + PH
def _r(devs, classes, kind, code, why):
    return {(c, d): (kind, code, why) for c in classes for d in devs}
SKIP = {}
SKIP.update(_r(("tv-api24",), ["AdultSearchFilterDeviceTest","InboxMainThreadDeviceTest","LibraryTabsMainThreadDeviceTest",
    "MangaLibraryMainThreadDeviceTest","StorageMainThreadDeviceTest","UpdateMainThreadDeviceTest"], "blocked", "FT-B1",
    "the main-thread checker needs StrictMode.penaltyListener (API 28); Android 7 TV cannot run it"))
SKIP.update(_r(TV, ["AniSkipDeviceTest","NetworkStateDeviceTest","YmPagingNetworkDeviceTest"], "blocked", "FINAL-33",
    "TV emulators are on emulated Ethernet, which a test cannot switch off (OMT-54)"))
SKIP.update(_r(TV, ["MusicModeNetworkDeviceTest"], "blocked", "FINAL-33",
    "TV emulator Ethernet stays up, the app never sees the drop (OMT-54; tv-api34: 'network toggle not seen by the app', run 44671880872)"))
SKIP.update(_r(TV, ["YumixDeviceTest"], "blocked", "FINAL-33",
    "offline refresh: TV Ethernet stays up (OMT-54); see-all paging: the CI row had a single page (FT-B3)"))
SKIP.update(_r(PH, ["YumixDeviceTest"], "blocked", "FT-B3",
    "see-all paging: the row the CI machine got had a single page (30-40 titles), so a second page never loaded"))
SKIP[("AppLockDeviceTest","tv-api24")] = ("blocked","FT-B2","b_: the test sets a device PIN with locksettings (API 26+) (OMT-55)")
SKIP[("AppLockDeviceTest","tv-api34")] = ("blocked","FT-B2","b_: locksettings set-pin leaves the TV image without a secure lock (OMT-55)")
SKIP.update(_r(ALL, ["YtQualityDeviceTest","PlayerSwitchDeviceTest","MusicEndlessNetworkDeviceTest"], "blocked", "YT-WALL",
    "YouTube refuses CI addresses (bot wall, OMT-29)"))
SKIP.update(_r(TV, ["YtChannelUiDeviceTest"], "blocked", "YT-WALL", "YouTube watch page never opens from CI (bot wall, OMT-29)"))
SKIP[("FeedPositionDeviceTest","tv-api24")] = ("blocked","TV-FP-1","test fault: the test taps the phone nav bar, absent on TV")
SKIP[("HeatGuardDeviceTest","tv-api24")] = ("na","","thermal status exists from Android 10; the guard is off below it")
SKIP.update(_r(TV, ["LocalPlayerDeviceTest"], "na", "", "no PiP on TV images, no Cast sender, no fullscreen toggle on the TV player"))
SKIP.update(_r(TV, ["PlayerExtrasSettingsEffectDeviceTest"], "na", "", "auto-PiP: the TV images have no picture-in-picture"))
SKIP[("MoreSettingsEffectDeviceTest","tv-api24")] = ("na","","YouTube-to-phone uses MediaStore publishing (Android 10+)")
SKIP[("MusicAccessDeviceTest","tv-api24")] = ("na","","the separate music permission exists from Android 13")
SKIP[("PermissionFlowDeviceTest","tv-api24")] = ("na","","the notification permission exists from Android 13")
SKIP[("PermissionFlowDeviceTest","tv-api34")] = ("blocked","FINAL-43","Android TV 14 shows the system dialog again after every 'Don't allow' (5 in a row, no don't-ask-again state), so the blocked -> Open settings step cannot occur; the 'Tap again' message did show after each deny (run 44672818277); owner test OMT-58")
SKIP[("RtlLayoutDeviceTest","tv-api24")] = ("na","","per-app language exists from Android 13 (API 33)")
SKIP.update(_r(PH, ["TvBug5ReproDeviceTest"], "na", "", "TV-only checks (landscape TV window); Phone Layout = TV in portrait is FINAL-31, not verified"))
SKIP.update(_r(ALL, ["MangaRetryDeviceTest"], "na", "",
    "double Retry: Retry hides while the first retry loads, so a second tap cannot be made (the race is closed in the UI)"))
SKIP[("MangaRetryDeviceTest","tv-api24")] = ("blocked","FT-B1","library load: needs StrictMode.penaltyListener (API 28); double Retry: Retry hides while loading")
# Failures that are accepted Blocked cells (owner's list, sec. 2 of the handover), not findings to fix now.
FAIL_BLOCK = {("FeedPositionDeviceTest","tv-api24"), ("YtChannelUiDeviceTest","tv-api24"), ("YtChannelUiDeviceTest","tv-api34")}
P1_ONLY = "Not run (owner's decision 2026-11-20: P1 only)"
def cell(cls, tgt, p):
    if not cls: return "Not run (no device class: checked off-device, see the note for this row)"
    if p == "P2":
        # The owner cut the final test to one pass on 2026-11-20 (§6). Earlier P2 runs on older code stay in evidence.
        return P1_ONLY
    devs = TV if tgt == "tv" else (tgt,)
    fails, blocks, nas, notrun, runs, passed, total = [], [], [], [], set(), 0, 0
    for c in cls:
        for d in devs:
            g = [x for x in (res.get((c, tgt, p)) or []) if x[0] == d]
            total += 1
            if not g: notrun.append(f"{c.replace('DeviceTest','')}@{d}"); continue
            dev, v, run, sk = g[0]; runs.add(run); short = c.replace("DeviceTest", "")
            k = SKIP.get((c, d))
            if v == "FAIL":
                if (c, d) in FAIL_BLOCK: blocks.append(f"{short}@{d} {k[1]}: {k[2]}")
                else: fails.append(f"{short}@{d}")
                continue
            if v == "SKIPPED" or sk:
                if not k: fails.append(f"{short}@{d} (skip without a written reason)"); continue
                (blocks if k[0] == "blocked" else nas).append(f"{short}@{d}{' ' + k[1] if k[1] else ''}: {k[2]}")
                if v == "PASS": passed += 1
                continue
            passed += 1
    rs = ", ".join(sorted(runs))
    if fails: return f"Fail ({passed}/{total}; failing: {'; '.join(fails)}; runs {rs})"
    if notrun: return f"Not run ({'; '.join(notrun)}: run in progress or queued; runs so far {rs or 'none'})"
    note = (f"; n/a: {'; '.join(nas)}" if nas else "")
    if blocks: return f"Blocked ({'; '.join(blocks)}{note}; {passed}/{total} classes passed; runs {rs})"
    return f"Pass ({passed}/{total}{note}; runs {rs})"
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
"Status words: Pass (run IDs) / Fail (finding ID) / Blocked (written reason; queue item or owner test) / Not run (written reason).",
"There is no Partial. A class skipped on a device for a written reason is either n/a there by design (the cell can still pass;",
"the reason is in the cell) or Blocked (the reason and its FT-*/OMT item are in the cell and in docs/UNVERIFIED-QUEUE.md).", "",
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
# Rows that are not app features a device can run; where each is checked instead (§5 written reason).
NON_DEVICE = [
    ("X1", "Architecture and layering: a code property, checked in docs/CODE-AUDIT-FINAL.md (Architecture row, AUD-ARCH-1/2)."),
    ("X7", "Security: secrets in the APK, storage, network, integrity: ApkSecretsTest in CI plus the review-APK string scan, gitleaks and the virus scan in docs/RELEASE-SUMMARY-FINAL.md; the integrity check itself runs on devices in FinalCoverage g_ (row N9)."),
    ("X10", "Build, CI, release, signing: proved by the CI runs on the head and the review APK checks (apksigner, ABIs, R8/X10-1) in docs/RELEASE-SUMMARY-FINAL.md."),
    ("X16", "Cloud Worker: server code, tested in docs/SERVER-TEST-FINAL.md (40/40 + Last.fm 5/5, failure cases); its device side is row I8/F-AIR (FcmAiringWake, ClosedAppAlert)."),
]
out += ["", "## Rows that are not device features (§5 written reasons)", "", "| ID | Reason |", "|---|---|"]
out += [f"| {i} | {r} |" for i, r in NON_DEVICE]
missing_reason = [r["id"] for r in rows if not r["classes"] and r["id"] not in dict(NON_DEVICE)]
assert not missing_reason, f"rows without a device class or a written reason: {missing_reason}"
# Every skip and accepted Blocked cell with its written reason (§5), from the SKIP table above.
out += ["", "## Skips and Blocked cells with written reasons (§5)", "", "| Class | Devices | Kind | Code | Reason |", "|---|---|---|---|---|"]
grp = {}
for (c, d), (k, code, why) in SKIP.items(): grp.setdefault((c, k, code, why), []).append(d)
for (c, k, code, why), ds in sorted(grp.items()):
    out.append(f"| {c.replace('DeviceTest','')} | {', '.join(sorted(ds))} | {'n/a by design' if k == 'na' else 'Blocked'} | {code or '—'} | {why} |")
out += ["", "P2 (second pass, warm start with data kept): not run in this final test, by the owner's decision of 2026-11-20",
        "(P1 only, §6). P2 runs made earlier on older code stay in docs/evidence/final/ as history, not as results for this build."]
open(f"{ROOT}/docs/MASTER-CHECKLIST-FINAL.md","w").write("\n".join(out) + "\n")
json.dump(classes, open("/tmp/final_classes.json","w"))
print(len(rows), len(classes), len(used), unused)
