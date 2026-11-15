# Group 5 audit: Library and personal data (R16)

Scope (INVENTORY L1–L10): the Library screen and shortcuts, My list and saved titles, new-episode
tracking, online and YouTube history, the downloads view inside Library, liked songs and
playlists, listening time and play counts, the reading (manga) library, the "real data only" rule
and the device videos tab.

Commits: 53420b2, 1c6b946, 535d763 (branch `r15-stability`).

## Tested

- Code read: every store that keeps personal data on the phone (OnlineBookmarks, OnlineHistory,
  OnlineDownloads, Inbox, MangaLibrary, YtHistory, PlayCounts, ListeningTime, MediaRepository
  playlists and favourites), the Library, Video library and Reading library screens, the device
  videos tab sort, and the video thumbnail fetcher.
- Failure paths checked on purpose: a half-written file after a crash or power loss, one bad
  entry inside an otherwise good list, a provider answering with a short episode list, the app
  screen closed with Back while music keeps playing, rotation with a playlist open.
- Environment: JVM unit tests (Robolectric) only for this group's fixes. No device run was needed
  for the new behaviour beyond the regular suite (see Verification).

## Problems found and fixed

| # | Problem | Root cause | Fix (files) | Verified by |
|---|---|---|---|---|
| a | A crash or power loss while saving could lose My list, history, downloads list, the inbox or the manga library completely | Each store read its file with `runCatching { parse }.getOrNull() ?: return` and still marked itself loaded, so the next save wrote an empty list over the only copy. The write fallback `f.delete(); tmp.renameTo(f)` could leave only the `.tmp` file, which nothing read back | New `data/SafeJsonFile.kt`: read tries the file, then the `.tmp`; an unreadable file is kept as `.bad` before anything is saved over it; write is temp file + fsync + rename. Used by `OnlineBookmarks`, `OnlineHistory`, `online/download/OnlineDownloads`, `notify/Inbox`, `sources/manga/MangaLibrary`. `backup/Backup.kt` skips `.tmp` / `.bad` files | `SafeJsonFileTest` (7) |
| b | The manga reader saved the whole library on the main thread on every page turn | `MangaLibrary` called `writeText` directly from the UI call | Coalesced background writer ("yu-manga-library"), `@Synchronized` load, `flush()` before backup export (`MangaLibrary.kt`, `Backup.kt`) | `SafeJsonFileTest`, existing manga and backup tests (`BackupV2Test` 56/56) |
| c | One bad playlist entry reset every playlist to the defaults; the next save made it permanent | `loadPlaylists` parsed the whole array in one `runCatching` and fell back to `defaultPlaylists()` | Bad entries are skipped one by one; playlist changes use `update {}` (`data/repository/MediaRepository.kt`) | `LibraryRound2Test#oneBadPlaylistKeepsTheOthers` |
| d | False "N new episodes" notifications | A short provider answer (e.g. 2 of 12 episodes) lowered the saved count; the next full answer then looked like 10 new episodes | The saved count never goes down; `checked()` returns 0 without writing on a short or equal answer; `NewEpisodesWorker` skips answers with 0 episodes (`online/OnlineBookmarks.kt`, `online/NewEpisodesWorker.kt`) | `OnlineExtrasTest#aShortAnswerNeverLooksLikeNewEpisodes` |
| e | One bad entry emptied the YouTube history, and the next play saved the empty list | `YtHistory.decode` used `getJSONObject` / `getString` for every entry; one throw failed the whole load | Bad entries are skipped (`youtube/YtHistory.kt`) | `RevampLogicTest#ytHistoryRoundTrips` (extended) |
| f | Listening time stopped counting after leaving the app with Back while music kept playing | The clock ran on the screen's ViewModel scope; the music runs in `PlaybackService` and outlives it | The shared player reports play/pause to `ListeningTime.playerIsPlaying` (process scope, started once); the ViewModel only loads the saved total (`service/PlaybackService.kt`, `player/ListeningTime.kt`, `ui/viewmodel/MainViewModel.kt`) | `LibraryRound2Test#clockRunsOnlyWhilePlaying` (accumulator unchanged); background counting: OMT-12 |
| g | Video and Reading library tabs read three to four files on the main thread when opened | `LaunchedEffect` called the loaders directly | Loads run on `Dispatchers.IO`; all loaders are `@Synchronized` (`VideoLibraryScreen.kt`, `LibraryDesign.kt`) | compile + unit suite |
| h | Song and video sort order were forgotten on every app start; filtering and sorting the whole library ran on the main thread on every keystroke | Plain in-memory `MutableStateFlow` defaults; `combine` without `flowOn` | Saved in prefs `yu_sort` (added to `PrefsWarmup`); `flowOn(Dispatchers.Default)` (`MainViewModel.kt`, `startup/PrefsWarmup.kt`) | compile + unit suite |
| i | An open playlist closed on rotation | `remember` instead of `rememberSaveable` (`LibraryScreen.kt`) | `rememberSaveable` | compile |

Checked and not a bug:

- `PlayCounts` already skips bad entries, never writes in incognito, and caps at 200.
- `YtHistory.add` and `PlayCounts.record` respect incognito.
- L9 (real data only): no sample or demo items are left in Library. The seeded demo playlists are
  removed unless the user added songs to them (`LibraryRound2Test#freshInstallHasNoDemoPlaylists`).
  The `MockTile` items in `UserGuide.kt` are drawings inside the help guide, not library data.
- Video thumbnails are loaded on IO, scaled to 512 px, and the retriever is always released.

Known, not fixed (low risk, documented):

- `OnlineBookmarks` saves on the caller thread. Saves happen only on taps (add, status change,
  notify switch), so this is rare and small.
- Playlist IDs are `playlist_<milliseconds>`; two playlists in the same millisecond are not
  possible from the UI.
- `PlaylistDetailScreen.kt:162` shows "not found" instead of the error if the store fails to
  start. It is a Live TV page and is handled in Group 7.

## Verification

| Kind | Run / command | Result |
|---|---|---|
| unit (local) | `:app:testMobileDebugUnitTest :app:compileMobileDebugAndroidTestKotlin` at 535d763 | 153 classes, 889 tests, 0 failures, 11 skipped (live-network tests) |
| CI device | Group 5 fixes run in the next full suite (see R15-STATUS / device-runs.md) | DEVICE_PENDING |

Kinds of evidence: a–e are unit-tested. f–i are compile- and unit-suite checked only; f's
background case needs a real phone (OMT-12) because CI emulators cannot leave music playing after
the activity is finished in a way the test harness can observe reliably.

Cancelled runs in this group: none.

## Research

- Atomic file writes on Android: write to a temp file, `fsync`, rename; keep the old copy until the
  new one is safe (the same idea as `androidx.core.util.AtomicFile`). SafeJsonFile keeps the
  existing JSON format, so no migration is needed.
- Media3: `Player.Listener.onIsPlayingChanged` is the signal for "audio really playing" (it is
  false while buffering or paused), which is what listening time needs.
- CloudStream keeps bookmarks and resume data in its DataStore with per-key values, so one bad
  entry cannot break the others; Yumeiro now gets the same per-entry tolerance without a format
  change.

## Proposals (not implemented; need owner approval)

1. Move personal stores to one Room or DataStore database with schema versions (big change; better
   for 200 k users, but needs a migration and its own test round).
2. Show a small "We recovered your list from a backup copy" notice when SafeJsonFile used the
   `.tmp` or kept a `.bad` copy.
3. Let the user sort and filter My list by date added, title and status.

## Limits and owner tests

- OMT-12 (new): listening time keeps counting after Back-exit with music playing (real phone).
- Device evidence for Group 5 fixes comes from the next full suite; nothing here is marked
  verified on device yet.
