# G6-PG1 — Album / playlist / artist page stopped for good after one failed "load more"

Status: **Verified (CI unit + emulator, both phones)** — fail-before CI 44220997354 (ab/pg1-before): YmDetailPagingTest 2 of 1149 failed, nothing else; fix f8564b8; pass-after CI 44223304640 green. Device 44225724090 (YmPagingNetworkDeviceTest, both phones): PG_RESULT PASS — page1=100, offline "load more" failed (UnknownHostException), connection back → same cursor retried, 5 pages / 499 rows on api33-lowram and api36. Group L2 G6 (Music, infinite feed
pagination). Found by code read, 2026-11-18.

## 1. Code read (head c4abe23, `ui/screens/youtube/YtMusicBrowse.kt`)

| Screen | "load more" failure | Effect |
|---|---|---|
| Home (`moreHome`) | keeps the continuation, `fails + 1`, backoff 1.5–12 s, gives up after `MAX_MORE_FAILS` (R15) | retried |
| Search (`moreResults`) | same as Home (R15) | retried |
| **Album / playlist / artist (`YmDetail.more`)** | `cur.copy(more = false, page = cur.page.copy(continuation = null))` | **one network blip past page 1 ends the list for good**; a 500-song playlist stops at the first page until the page is reopened |

The R15 comment on `YmStore` says this exact bug was fixed for Home ("one failed load more cleared the
continuation so the feed stopped for good"); the detail page kept the old code.

Also noted (not changed, low impact): `YmEndless.nextSeed` stops at `used.size >= MAX_SHELVES`, but `use()` adds two
keys per shelf (id + artist), so the endless Home grows by about 40 shelves, not the 80 the constant names; the
screen's own guard uses `MAX_SHELVES * 2`. Recorded for the owner; 40 shelves × 20 songs is already 800 songs.

## 2. Competitors (ideas only, nothing copied)

- **InnerTune v0.5.10** `viewmodels/ArtistItemsViewModel.loadMore`: on failure only reports the exception; the
  page and its continuation stay, so the next scroll retries.
- **Metrolist v14.0.0** `shared/.../viewmodels/PlaylistPageLoader.loadMore`: the cursor page is replaced only on
  success; a failure leaves it, so the next call retries.

Idea taken: a failed page never drops the cursor.

## 3. Options

| Option | Pros | Cons |
|---|---|---|
| **A same rule as Home/search: keep continuation, count fails, backoff, stop after 3** | one rule for every Music list; bounded retries (no hammering on a dead link) | none found |
| B keep continuation, retry forever | simplest | a permanently broken token retries on every scroll |
| C "Retry" button at the list end | user control | new UI; Home/search do not have it |

## 4. Action–reaction

| Action | Reaction |
|---|---|
| Scroll a long playlist past page 1, network drops for a moment | the end spinner stays for the backoff (1.5 s), then `YtEndlessTrigger` (enabled again, still near the end) asks for page 2 again by itself |
| The page keeps failing | after 3 tries (1.5 s, 3 s, 6 s backoff) the list stops asking; reopening the page starts fresh |
| Page loads | rows appended, fail count back to 0 |

## 5. Tests

Unit `ui/youtube/YmDetailPagingTest` on `YmState.afterDetailMoreFailed` (the step `YmDetail.more` uses). Device
paging past page 1 with the network toggled needs YouTube Music from the CI emulator (reachable from CI runners
for Home in earlier runs); recorded in the report.
