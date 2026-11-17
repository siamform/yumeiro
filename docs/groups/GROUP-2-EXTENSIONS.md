# R16 Step 3 · Group 2: extensions and market

Branch `r15-stability`. Emulators: **api33-lowram** (Android 13, Pixel 3a profile, 2 GB RAM,
192 MB heap) and **api36** (Android 16, Pixel 6 profile). These are emulators, not real phones.
The market and extension downloads are real network calls from the CI runner.

## Tested

| ID | Item | How | Result |
|---|---|---|---|
| E1 | Market browse and search | `ExtensionsDeviceTest.e1_e2`: open the market from the nav shortcut, search "Internet", find InternetArchiveProvider | pass on both (43704211410) |
| E2 | Install with review / scanner / guard | same test: install opens the safety review; Cancel keeps it out; Install puts it in and the provider loads | pass on both |
| E3 | Repair a missing file (after restore) | `e3`: delete the plugin file, `missingFiles` lists it, `repairMissing` downloads it again | pass on both, `ok=1 failed=0` |
| E4 | Per-extension switch, settings | `e4_e6_e7`: switch turns one extension on; the website-address override is E12 | pass on both |
| E5 | Pin favourites | `PinnedExtensionsDeviceTest` | pass on both (43671202859, again in 43704211410) |
| E6 | Select all, single selection, bulk actions | `e4_e6_e7`: select all → 2 selected → bulk off; long-press the title row selects one; bulk remove asks first and removes only that one; Back ends selection and stays on the screen | pass on both after the fixes below |
| E7 | Search of installed extensions | same test: search narrows the list; select all acts only on what is shown; bulk on turns on only the shown one | pass on both |
| E8 | Broken extension link | `e8`: add link `https://yumeiro-test.invalid/Broken.cs3`; the dialog shows a readable error, no crash, no stack text | pass on both; text: "Couldn't open that link: This site can't be reached on your network. It may be blocked — try again, or turn a VPN on or off" |
| E9 | Starter extensions on first run | `e9` (new) plus `StarterExtensionsTest` 3/3 | unit pass; device 43705847970 7/7 both emulators |
| E10 | Universal add (CloudStream, Aniyomi, Mihon, Stremio, JS) | `e8` covers the link path; `UniversalAddTest`, `UniversalSourcesLogicTest`, `SourceMarketTest` cover detection of each kind | unit pass; each ecosystem against a live repository on a device is not run (see Status) |
| E11 | Sources hub and source health | `e11` (new): Source status lists the installed sources, no false empty state, "Check all sources" finishes with "N of M working" | pass on both |
| E12 | Provider overrides, adult filter | `e12` (new): the override is normalised (`https://`, no trailing slash), applied at once, kept, cleared back to the shipped address; adult search filter checked in code review | override pass on both; adult filter: see Status |

## Problems found

1. **Select all and bulk actions did not exist (E6, missing feature).** The inventory listed them
   and the UI had no selection at all.
2. **Back in selection mode left the Extensions screen (app bug, found by the new device test,
   43701058752).** The selection `BackHandler` was declared before `SubPageHeader`, whose own
   handler is composed later and so wins. A grep of every screen that has both found no other case.
3. **"Update all" on the Extensions screen started one download per extension at once (app).**
   Each download can be up to 25 MB, and each one showed its own toast. On a 2 GB phone with 20
   extensions this means 20 parallel downloads and 20 toasts.
4. **Market "Update all" hid failures (app).** It always showed a success toast ("Updated x of y").
5. **Network errors showed raw text (app, cross-cutting).** `OnlineErrors.friendly` looked only at
   the last exception in the cause chain. On Android, `UnknownHostException` has a `GaiException`
   as its cause, so the "no internet / blocked site" mapping never matched, and users saw
   "Unable to resolve host … No address associated with hostname" (43702633505). This affects
   every screen that uses `friendly` (online home, search, details, source status).
6. **Universal add put the raw exception message in brackets** ("Couldn't open that link (…)").
7. **18+ off still searched adult extensions (app, E12).** The market hides NSFW extensions when 18+
   is off, but an adult extension installed while 18+ was on stayed in Online search after it was
   turned off, and NSFW-typed items from mixed sources were shown.
8. **Source status could say "No sources installed yet" (app, E11).** The source list was read
   once when the screen opened. If the extension engine had not finished loading, the screen stayed
   empty for the whole visit.
9. **Source status ran extension search on the main thread (app, E11).** For sources without a
   home page, `api.search` (blocking network inside extension code) ran on the UI dispatcher. It
   also swallowed cancellation.
10. **Starter extensions: a partial first run never retried (app, E9).** The comment said "failed
    downloads retry next launch", but one success marked the run done, so the other starters
    were never tried again. It also swallowed cancellation.
11. **`ProviderOverrides.defaults` was a plain `HashMap`** written from the loader (IO) and the
    Extensions screen (main). Now a `ConcurrentHashMap`.
12. **Test errors along the way:** `e8` pressed Back once, which only hides the keyboard (standard
    Android); the test now taps Cancel. The `e6` long-press hit the card centre, which is the chip
    row (Auto-update, Check for update), and the chips take the press; the test now presses the
    title row, as a person would.
13. Minor, not fixed: `InstalledCard` reads `SiteLogins.has` during composition (a small prefs read
    per card). Listed for Group 12.

## Fixes

| Commit | Fix | Verified |
|---|---|---|
| 5b1263e | `online/ExtensionBulk.kt` (sequential bulk runner: a declined review counts as skipped, cancellation is rethrown, one summary); `ExtensionsScreen` selection mode: header button, long-press, per-card checkbox, `SelectionBar` with select all, on, off, update, remove (with confirmation); "Update all" is sequential with one summary toast; market "Update all" names the failures; `ExtensionBulkTest` 4 tests | device 43704211410 |
| bfde23d | Back ends selection through the header's handler; 18+ off keeps adult extensions and NSFW items out of search; Source status follows the engine, checks on IO, rethrows cancellation, has a `source_health_run` tag; readable universal-add errors; `ConcurrentHashMap` for override defaults; tests `e11`, `e12` | device 43704211410 |
| 9be5afb | `OnlineErrors.friendly` checks the whole cause chain; `OnlineErrorsChainTest` 3 tests; `e6` long-press on the title row | device 43704211410 (E8 text above) |
| 97bb255 | Starter extensions: only missing picks install, retry up to 3 launches, cancellation rethrown; `e9` | device 43705847970 |

## Verification

- Unit suite: 147 classes, 865 tests, 0 failures, 11 skipped (after 9be5afb).
- `ExtensionsDeviceTest` + `PinnedExtensionsDeviceTest`: 43701058752 (5b1263e) found problems 2
  and 12; 43702633505 (bfde23d) found problem 5 and the long-press test error; **43704211410
  (9be5afb): 6/6 and 1/1 on both emulators.**
- **43705847970 (97bb255, adds `e9` starters): `ExtensionsDeviceTest` 7/7 and `PinnedExtensionsDeviceTest`
  1/1 on api33-lowram and api36.**
- Unit suite after the Group 3 Cast fix: 148 classes, 866 tests, 0 failures, 11 skipped.
- Full device suite on 5b1263e: 43701056608, every class passed on both emulators.

## Research

- UI/UX Pro Max (ux domain): "Bulk Actions: multi-select with an action bar" and "Confirmation
  Messages: confirm destructive actions". Yumeiro follows both: a selection bar with a count, and
  a confirmation before bulk remove.
- Android back handling: "the callbacks are invoked in the reverse order in which they are
  added—the callback added last is the first given a chance to handle the Back button event"
  ([Provide custom back navigation](https://developer.android.com/guide/navigation/custom-back)).
  The header's handler was added after the selection handler, which is the cause of problem 2.
- Competitors (CloudStream, Hikari) bulk-update and selection behaviour is not verified in this
  round; Step 4 compares it release by release against their changelogs.

## Enhancements (proposed, not built)

1. Long-press anywhere on a card, including the chip row (move the chips into a menu while
   selecting, or use a detector that also fires over child clickables).
2. One "health" badge per extension, taken from the last Source status run, shown in the list.
3. Bulk "Update" should show progress ("3 of 12") in the selection bar.
4. Show which repository each failed update came from in the summary toast.

## Code quality and system design

- Bulk logic is a pure object (`ExtensionBulk`) with JVM tests; the screen only wires it.
- `ExtensionsScreen.kt` is large (about 650 lines). Proposal: move `InstalledCard` and
  `SelectionBar` to their own file in Group 12.
- Error text has one source (`OnlineErrors.friendly`); `PlaylistMarket.friendlyError` still has
  its own copy and checks only the top exception. Listed for Group 10.

## Status

| ID | Status | Reason / next step |
|---|---|---|
| E1 | verified on device | 43704211410 |
| E2 | verified on device | 43704211410 |
| E3 | verified on device | 43704211410 |
| E4 | verified on device | 43704211410 |
| E5 | verified on device | 43671202859, 43704211410 |
| E6 | verified on device | 43704211410 |
| E7 | verified on device | 43704211410 |
| E8 | verified on device | 43704211410 |
| E9 | verified on device | 43705847970 |
| E10 | partly verified (device test pending) | the link path is verified on device (E8). Re-audit 2026-11-17: instead of pinning live public repositories (they move and rate-limit CI), `UniversalAddEcosystemsDeviceTest` (e71a041) serves each real format from a local server to `UniversalAdd.add` on the device: Aniyomi via the `aniyomi://add-repo?url=` link (repo.json + index.min.json), Mihon via a folder link, Stremio manifest (then its search `/catalog/movie/yu/search=…` and `/stream/…` links), Mangayomi JS index (listed, installed), and an empty link (readable failure, nothing saved). First run 44056138637 (api33-lowram) pending. A live public repository remains a real-network check (unchanged) |
| E11 | verified on device | 43704211410 |
| E12 | override verified on device; adult search filter unit/code only | an adult extension would have to be installed on CI to test it. Next: a fake NSFW provider in a test build |
