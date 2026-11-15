# Group 4 audit: Yumix (R16)

Branch `r15-stability`, commits `2a79dbe`, `678903d`, `9e10315`, `65e50e0`, `e2a1114`, `8a38eed`, `6267099`, `52a8c42`. Devices: CI emulators
only. **api33-lowram** is Android 13 on a Pixel 3a profile (2 GB RAM, 192 MB heap). **api36** is
Android 16 on a Pixel 6 profile. Both are `google_apis` images. On both, Yumix has one source, the
built-in short-drama source (`YumeiroDrama`), because CI does not install third-party extensions.
Real phones, a real TV and real networks are in `docs/OWNER-MANUAL-TESTS.md` (OMT-4, OMT-5).

Each result is labelled as one of:
- unit: JVM test
- device: real Compose UI test on the named emulator
- CI: a green workflow run
- hardware: a real phone or TV. Nothing in this group is hardware-verified yet.

## Tested

| Inventory | What | How | Result |
|---|---|---|---|
| Y1 | Feed and sections, refresh, "More to explore" | Unit: `YumixFeedTest` 7, `YumixRefreshTest` 4 (new), `YumixGoneTest` 1 (new), `YumixShowcaseTest` 4, `OnlinePagesTest` 3. Device: `FeedPositionDeviceTest#yumix` (scroll 16 cards down and back, a card must not change under the finger); `YumixDeviceTest#offlineRefreshKeepsTheFeed` (new: Wi-Fi and data off, pull to refresh, every card must stay and the "Couldn't refresh" message must show) | unit pass. FeedPosition 43715245923: api33 32 cards, api36 39 cards, 0 problems, 16/16 moved. Offline: see Verification |
| Y2 | Yumix search | `YumixFilterSearchTest` 4, `SearchRankerTest` 4, `SearchSuggestTest` 4. Code read: the search runs in a `LaunchedEffect` keyed on the submitted words, so a new query cancels the old collection before it can write; typing waits 450 ms before submitting | unit pass; no device test of its own (the top-bar search is `HomeNavigationDeviceTest h10`, a different screen) |
| Y3 | Extension bar hides and shows on scroll | Code read of `HideOnScroll` (a threshold, resets at the top, never hides while the list cannot scroll) | no bug found; no dedicated test. Feel on a phone: OMT-9 |
| Y4 | Drama as default, built-in drama card | `PinnedExtensionsTest`, `YumeiroDramaTest` 9, `DramaResilienceTest` 16, `DramaHealthTest` 4; on both emulators Yumix opens on the drama source with no setup (every device run above) | unit pass; device (indirect) |
| Y5 | "Show videos from" filter | `YumixFilterSearchTest` 4 (folded names, accents), `PinnedExtensionsDeviceTest` (pin, select, clear) | unit pass; 43705847970 pass on both |
| Y6 | Hero, picks, "See all" row pages | `YumixDeviceTest#seeAllKeepsPagingWhileSearching` (new): open "See all", page on, type in the row search while pages load, the list must keep growing with no "Couldn't load more") | 43716649966: api36 26 → 468 titles, api33 78 → 694 titles, 0 error checks |
| Y7 | Detail page | Code read + fix (h below); `OnlineExtrasTest` 11, `EpisodeCarouselTest` | unit pass; detail opening on device is part of `DramaPlaybackDeviceTest` (43701056608 pass) |
| Y8 | YouTube in Videos | `YtDiscoveryTest` 9, `YtHomeOrderTest` 5, `YouTubePickVideoTest` 5, `YtAccountTest` 7; playback `PlayerSwitchDeviceTest c_` (43701056608) | unit pass; the YouTube feed itself needs a signed-in Google account and a real network: OMT-1 |

## Problems found and fixed

All of these are app bugs unless the entry says test. The first of each pair is the cause, the
second is the fix.

a. **Offline refresh emptied Yumix (Y1, critical).** `OnlineRepository.home(force = true)` returns
   an empty list when every row fails. `loadOne` treated that as success and replaced the
   sections, so pulling to refresh with no network wiped the feed and reset "More to explore".
   Fix (`YumixFeed.homeAfterFetch`): an empty answer keeps the old rows. A partial answer keeps the
   missing old rows after the fresh ones. A first load that brings nothing back is now an error
   with Retry ("Nothing came back from this extension"). `loadAll` counts the extensions that
   answered. Explore resets only when at least one did; otherwise the screen says "Couldn't
   refresh. Showing what was already loaded."
b. **"More to explore" never started (Y1)** when a ready extension had no rows: the check
   `groups.size >= ready.size` could not be true. Fix: `YumixFeed.sectionsDone`.
c. **A cancelled load wrote "Couldn't load" over the newer one (Y1).** A refresh cancels the old
   `loadOne`, and its catch block still stored a failure. Fix: return early when the coroutine is
   no longer active; `inflight` is now `Deferred<Boolean>`.
d. **A stale "More to explore" page landed after a refresh (Y1).** A page fetched before the reset
   was appended to the new list. Fix: `YumixMemory.generation`, bumped by `resetExplore` and
   checked before `pageExplore` writes.
e. **"See all" could stop paging (Y6).** The page fetch ran inside a `LaunchedEffect` keyed on the
   request. A second request cancelled it, and `runCatching` turned the cancellation into "Couldn't
   load more", which stopped paging. Found by reading the code; not reproduced before the fix. Fix:
   one job in `rememberCoroutineScope`. A timeout is an error; any other cancellation is rethrown.
   The new device test pages through 400+ titles while searching.
f. **Drama feed calls waited up to 20 s behind a background probe (Y1, Y4).** The `OnShortGate`
   slot check counted background probes against the FEED and INTERACTIVE lanes (seen in the
   logcat of earlier stress runs). Fix: `fits()` leaves out `backgroundRunning` for those lanes.
   `GateLaneTest#feedLoadsDontWaitForARunningProbe` (new).
g. **Titles from an extension that was turned off, or hidden as 18+, stayed in "More to explore"
   until a refresh (Y1).** Fix: `YumixFeed.goneFrom`, applied in `syncExplore`. `YumixGoneTest`.
h. **The detail page lost its content on a failed refresh, and a cancelled load briefly showed
   "Couldn't open this title" (Y7).** Fix: keep the Ready page and show a message; check
   `isActive` before writing an error.
i. **Test: the offline device test measured nothing (43716649966).** Its card count matched Home's
   cards while the tab was switching, so the wait ended at once, and it then measured a Yumix page
   that was still loading (0 cards, assumption skip). Fix (`65e50e0`): count only cards inside
   `online_home_list`, wait for a stable count, and log every step with each section's state.
   Those logs led to k.
j. **Test: the HeatGuard device test failed in full suite 43714635144 (api36, Group 3 P19).** The
   system itself still reported `isPowerSaveMode=false` after `cmd power set-mode 1`, so HeatGuard
   was right to stay COOL. Fix (`65e50e0`): retry the precondition up to 3 times. If the system
   won't turn battery saver on, log `SAVER_NOT_ENABLED` and test only the thermal half.

k. **An Internet Archive the user had just added was removed without a word (Y1, extensions).**
   In 1.8.0 Internet Archive was installed automatically, and a one-time clean-up removes it. That
   clean-up ran at the first `ExtensionManager.sync`, which is right after the first launch. So on
   a new install, or before the first sync after an update, an Internet Archive the user had added
   was uninstalled. Its Yumix section showed and then vanished.
   Found by `YumixDeviceTest`. In runs 43719277646, 43720589678 and 43721853978 the row appeared
   (4–7 cards), then dropped to 0. The new unload log reads "Unloaded …InternetArchiveProvider from
   uninstall" and "sections dropped (extension gone)".
   Fix (`ExtensionManager.legacyDefaultMigrationDue`): decide once, from the saved state at load. A
   new install has nothing to migrate. An installed Internet Archive is removed only if it was
   there before this version. Adding Internet Archive marks the clean-up as done.
   Test: `LegacyDefaultMigrationTest` 3.
l. **Offline, a refresh "answered" from the drama catalog's own cache (Y1).** `OnShortCatalog`
   serves stale shelves when a fetch fails, so offline the drama source brought back exactly what
   was on screen. The screen counted that as an answer: it reset "More to explore", which moved the
   cards on screen (run 43719277646, api36: kept 5 of 8), and it never said the refresh failed.
   Fix (`YumixFeed.broughtNew`): an extension has answered only if it brought a title not already
   shown. With no answer, the message now says why: "Couldn't refresh: you're offline", or "Nothing
   new yet" when the network is up.
m. **A partial refresh moved the shelves (Y1).** `homeAfterFetch` put every row the refresh did
   not bring back after the fresh rows. Now each fresh row replaces its old copy in place, and only
   new rows go last. `YumixRefreshTest` covers both (now 5 tests).
n. **A studio whose video host is gone stayed in the feed (default drama).** On 2026-11-15 DramaBox's
   CDN host `hwztakavideo.dramaboxdb.com` stopped resolving (NXDOMAIN on 1.1.1.1 and 8.8.8.8,
   checked from the sandbox too). ONShort still lists DramaBox and still answers its episodes with
   that host. The studio health probe read the `UnknownHostException` as "the phone is offline" and
   left the shelf, so every DramaBox title failed on tap (run 43724760853, both devices: "Every
   source refused playback" / "took too long"). Fix (`DramaHealth.defaultReach`): while the phone is
   online, an unknown stream host is `StreamHostMissing`. The probe hides the studio and rechecks in
   5 minutes, and `playable()` returns false so the mirror search runs. Real offline (no
   connectivity) stays inconclusive. `DramaHealthTest` +2 (studioWhoseVideoHostIsGoneIsDown,
   phoneOfflineDuringProbeKeepsTheShelf).
o. **Test: offline cards counted off screen.** In the full suite, a Continue watching row (left by
   the drama tests) plus the offline notice push the Yumix rows below the screen. 0 cards were
   composed while all 26 titles were held and the message was shown (43724760853, both devices).
   The test now scrolls the rows into view before counting.
p. **Test: HomeNavigation h2 expected the player for every Resume.** Resume of an online title opens
   its detail page with "Continue S1E1" by design (`MTPlayerApp`: `openOnline`). It passed before
   only because a device video had been watched last. The test now accepts the detail page for an
   online title. Opening the player straight from Home for online titles is a UX choice for the
   owner (proposal 5), not a bug fix.
q. **Test: DramaPlayback tried only the first row.** All four titles were DramaBox, so one studio's
   outage failed "a drama plays". It now takes one title per studio row first.

Files: `ui/screens/online/OnlineHomeScreen.kt`, `YumixFeed.kt`, `OnlineRowScreen.kt`,
`OnlineDetailScreen.kt`, `sources/drama/OnShortCatalog.kt`, `online/ExtensionManager.kt`; tests
`YumixRefreshTest.kt`, `online/LegacyDefaultMigrationTest.kt`,
`GateLaneTest.kt`, `androidTest/YumixDeviceTest.kt`, `androidTest/LocalPlayerDeviceTest.kt`;
`scripts/device-check.sh` (YumixDeviceTest added to the full suite).

### "Cached rows must never replace live rows while scrolling"

Every path that writes rows during a scroll was checked:

| Path | Guard | Evidence |
|---|---|---|
| Section refresh (`loadOne`) | a cancelled or empty fetch never writes (a, c) | `YumixRefreshTest`; offline device test |
| "More to explore" paging | generation check (d); no new page starts while one is loading (`exploreBusy`) | `YumixRefreshTest` |
| Hue pages (`LivePaged`) | generation guard, existing | `LiveFeedTest` 6 |
| Disk cache → live (`OnlineRepository.diskServed`) | live replaces the disk copy once, then pages append; item keys are stable, so the list diff moves no card | FeedPosition 43715245923: 0 problems on both |
| "See all" | single job (e) | `seeAllKeepsPagingWhileSearching` |

## Verification

| Kind | Run / command | api33-lowram | api36 |
|---|---|---|---|
| unit | `:app:testMobileDebugUnitTest`, 152 classes (at bc68326) | 880 tests, 0 failures, 11 skipped (live-network tests) | |
| CI device | 43714635144 full suite on 6e57d9e (the milestone rerun after the Group 3 timing fix) | every class pass | every class except LocalPlayer d (j, test) |
| CI device | 43715245923 YumixOffline + FeedPosition#yumix on 2a79dbe | FeedPos pass; offline skipped (4 cards < old threshold 6) | pass, 8/8 cards kept offline |
| CI device | 43716649966 YumixDeviceTest on 9e10315 | see all 78 → 694; offline skipped (i) | see all 26 → 468; offline skipped (i) |
| CI device | 43717950021 YumixDeviceTest + LocalPlayer#d on 65e50e0 | see all 78 → 592; offline skipped; LocalPlayer d pass | see all 26 → 593; offline skipped; LocalPlayer d pass |
| CI device | 43719277646 Yumix on 678903d | fail: cards vanished, Internet Archive removed by the legacy migration (bug, fixed in e2a1114) | same |
| CI device | 43720589678 diagnostic run (feed logging) | diagnostic only | diagnostic only |
| CI device | 43721853978 diagnostic run (unload log) on 6267099 | pass (diagnostic) | pass (diagnostic) |
| CI device | 43723079397 Extensions + Yumix on 52a8c42 | Extensions 7/7; Yumix 2/2 (4/4 kept + message); see all 52 → 973 | Extensions 7/7; Yumix fail on the old "kept" check (test: the offline notice shrinks the viewport, o); see all 76 → 644 |
| CI device | 43724760853 full suite on 8769ef8 | fail: Drama (DramaBox CDN NXDOMAIN, bug n + test q), HomeNav h2 (test p), Yumix (test o: 0 cards on screen, 26/26 held, message shown) | same three |
| CI device | 43732060154 Drama + Yumix + HomeNav on 3c29282 | HomeNav 11/11; Drama pass; Yumix fail: 2 unknown drama cards, drama section still Loading at hold time (test, fixed in bc68326) | HomeNav 11/11; Yumix 2/2 (276 held, 0 lost, 0 unknown); Drama played but in 55 s against a 10 s budget (52 s inside one drama streams call, no gate wait; the onshort shelf call held its slot > 76 s). Classed as upstream slowness, not confirmed; slow-answer log added in bc68326 |
| CI device | 43733844682 G4 milestone full suite on bc68326 (16 classes) | 15/16 classes pass, incl. Yumix 2/2, HomeNav 11/11, Extensions 7/7, LocalPlayer 5/5, Stress 3/3. Drama fail (and its retry): all 4 titles "server took too long"; the new slow-answer log shows every onshort.net call in that window timing out at 20 s (SocketTimeout, one ConnectException / UnknownHost). Environment / upstream, not app | all 16 classes pass |
| CI device | 43738288853 Drama + LibraryDeviceTest on a119186 (rerun of the api33 Drama failure, plus the new Group 5/6 test) | see GROUP-5-LIBRARY.md | see GROUP-5-LIBRARY.md |

Cancelled runs in this group: none.

DramaBox moved its video CDN to `hwztakavideoto.dramaboxdb.com`; the old host no longer resolves (NXDOMAIN), which is what bug n handles.

## Research

- Android Paging 3 keeps the cached list when a refresh fails and reports the error beside it
  ([Android Developers: network and database paging](https://developer.android.com/topic/libraries/architecture/paging/v3-network-db),
  [Paging load states guide](https://rakesh-lakshmipathy.github.io/android-master-to-architect/docs/data/paging-loadstates),
  [Paging 3 failure matrix](https://dev.to/raylabs/android-paging-3-failure-matrix-l2e)). Fix a
  follows the same rule.
- CloudStream's
  [HomeViewModel](https://raw.githubusercontent.com/recloudstream/cloudstream/master/app/src/main/java/com/lagradost/cloudstream3/ui/home/HomeViewModel.kt)
  does the following:
  - A failed main page replaces the whole page with an error (Yumeiro now keeps the old rows).
  - A failed row page sets `hasNext = false` (Yumeiro keeps Retry).
  - A per-row lock prevents duplicate expansion (Yumeiro guards section loads per extension through `inflight`).
  - A new load cancels the old one, as Yumeiro does.
  - Its `distinctBy` result is discarded, so duplicates can appear. Yumeiro's merge keeps
    first-seen items by key.

## Proposals (not implemented; need owner approval)

1. The same title can appear in picks, latest and a group shelf at once. This is by design: each
   shelf comes from the extension. An option could hide repeats across shelves.
2. `HuePage` does not add live page-1 items to rows that were already shown from the disk copy; they
   appear on the next visit. Small. A merge could add them at the end without moving cards.
3. The same `LaunchedEffect` + `runCatching` pattern as bug e exists in `MangaScreens.kt` (lines
   379 and 496) and `PlaylistDetailScreen.kt` (line 162). They will be checked in Groups 7 and 5.

4. Drama first load: show each studio's shelf as soon as it arrives (today the section waits for
   the whole home), and consider one more FEED slot for onshort.net after checking its rate limits.

5. Home "Resume" on an online title could start the episode directly (as device and YouTube
   videos do) instead of opening its detail page.

## Limits and owner tests

- If an extension is missing for a moment during an engine update, its titles can leave "More to
  explore" and come back (g). Not seen on device.
- On CI the first drama load takes 80–130 s (runs 43719277646 … 43723079397). Each onshort.net
  shelf call answers in about 18–23 s from the CI network, and the gate's FEED lane allows only a
  few at a time, so later calls wait behind earlier feed calls (logged `YuGate waited … held by
  feed …`). After fix f they no longer wait behind probes. The cap protects onshort.net (R15).
  Raising it, or showing the drama shelves one by one as they arrive, is proposal 4. Load time on a
  home connection: OMT-1/OMT-9 timing notes.
- Only the built-in drama source runs on CI. Third-party extensions with many sections were tested
  in unit tests only.
- Owner tests: OMT-1 (YouTube feed signed in), OMT-4 (TV D-pad in Yumix), OMT-5 (offline refresh on a
  real phone with mobile data), OMT-9 (scroll feel, extension bar).
