# G7-MB1: a title or chapter listed twice closes the app

Status: Verified (CI unit) — fail-before CI 44241940033 (ab/mb1-before): only the 2 MangaBrowseDuplicateTest cases red with `IllegalArgumentException: Key "yumb://t/1" was already used` (thrown in layout, so the app closes), of 1175 tests; pass-after CI pending. Group G7 Manga (sources, browse, library).

## Problem (code read, 2026-11-18)

- `ui/screens/manga/MangaScreens.kt` `MangaGrid.more()` added a page's titles after removing those
  already in the grid (`items.none { it.url == n.url }`), but a title repeated inside the same page
  passed the filter twice. The grid is keyed by URL (`items(items, key = { it.url })`); a key used twice
  throws `IllegalArgumentException: Key … was already used` during layout, which closes the app.
- `MangaDetailScreen` showed `provider.chapters(url)` as is, keyed by chapter URL: a chapter listed twice
  by the source does the same.
- Sources do repeat entries (a featured title in its own list, a chapter uploaded twice under one URL).

## Competitors (ideas only)

| App, version | File | What it does |
|---|---|---|
| Mihon v0.21.3 | `data/.../source/SourcePagingSource.kt` | `seenManga = hashSetOf<String>()`; each page `.filter { seenManga.add(it.url) }` |
| Mihon v0.21.3 | `domain/chapter/interactor/SyncChaptersWithSource.kt` | source chapters `.distinctBy { it.url }` |
| Kotatsu v9.4.1 | `remotelist/ui/RemoteListViewModel.kt` | `(prevList + list).distinctById()` |

## Fix

- Browse: one set of URLs already shown, `filter { seen.add(it.url) }` (also drops repeats inside a page,
  and is O(n) instead of O(n²) per page).
- Title page: `chapters(url).distinctBy { it.url }`.

## Action → reaction

| Action | Before | After |
|---|---|---|
| Source page lists a title twice | app closes (duplicate key) | title shown once |
| Title page lists a chapter twice | app closes (duplicate key) | chapter shown once |
| Next page repeats a title of page 1 | dropped | dropped (same) |

## Pros / cons

- Pros: no crash; same rule as Mihon/Kotatsu; cheaper filter.
- Cons: two different chapters a source gives the same URL show as one (they open the same page anyway).

## Tests

Robolectric `MangaBrowseDuplicateTest` (fake source in SourceHub): title repeated in one page shown once;
chapter repeated shown once. Fail-before on the old code, pass-after.
