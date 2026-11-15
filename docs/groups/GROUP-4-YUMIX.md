# Group 4 audit: Yumix (R16)

Branch `r15-stability`, commits `2a79dbe`, `678903d`, `9e10315`, `65e50e0`. Devices: CI emulators
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
   `online_home_list`.
j. **Test: the HeatGuard device test failed in full suite 43714635144 (api36, Group 3 P19).** The
   system itself still reported `isPowerSaveMode=false` after `cmd power set-mode 1`, so HeatGuard
   was right to stay COOL. Fix (`65e50e0`): retry the precondition up to 3 times. If the system
   won't turn battery saver on, log `SAVER_NOT_ENABLED` and test only the thermal half.

Files: `ui/screens/online/OnlineHomeScreen.kt`, `YumixFeed.kt`, `OnlineRowScreen.kt`,
`OnlineDetailScreen.kt`, `sources/drama/OnShortCatalog.kt`; tests `YumixRefreshTest.kt`,
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
| unit | `:app:testMobileDebugUnitTest`, 151 classes | 874 tests, 0 failures, 11 skipped (live-network tests) | |
| CI device | 43714635144 full suite on 6e57d9e (the milestone rerun after the Group 3 timing fix) | every class pass | every class except LocalPlayer d (j, test) |
| CI device | 43715245923 YumixOffline + FeedPosition#yumix on 2a79dbe | FeedPos pass; offline skipped (4 cards < old threshold 6) | pass, 8/8 cards kept offline |
| CI device | 43716649966 YumixDeviceTest on 9e10315 | see all 78 → 694; offline skipped (i) | see all 26 → 468; offline skipped (i) |
| CI device | 43717950021 YumixDeviceTest + LocalPlayer#d on 65e50e0 | RESULT_PENDING | RESULT_PENDING |
| CI device | G4 milestone full suite | MILESTONE_PENDING | MILESTONE_PENDING |

Cancelled runs in this group: none.

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

## Limits and owner tests

- If an extension is missing for a moment during an engine update, its titles can leave "More to
  explore" and come back (g). Not seen on device.
- Only the built-in drama source runs on CI. Third-party extensions with many sections were tested
  in unit tests only.
- Owner tests: OMT-1 (YouTube feed signed in), OMT-4 (TV D-pad in Yumix), OMT-5 (offline refresh on a
  real phone with mobile data), OMT-9 (scroll feel, extension bar).
