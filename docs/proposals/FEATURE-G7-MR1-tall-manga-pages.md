# G7-MR1: tall manga / webtoon pages (reader memory)

Status: Verified (emulator, both phones). device fail-before 44238006080 (old code + test, temp branch deleted): b red on api33-lowram (107 MB page blank, red share 0.0), a passed on both, b passed on api36; fix fad077a; pass-after 44239024442 MangaTallPageDeviceTest OK 2 on both phones (a: red top, blue after scrolling; b: drawn); shots mt_a_low/mt_b checked. Unit MangaPagePlanTest: CI pending. Group G7 Manga (reader: image loading, memory, paging).

## Problem (code read, 2026-11-18)

`ui/screens/manga/MangaScreens.kt` `loadPage` decoded every page as one bitmap and capped only its
width (halved while wider than 1600 px). Webtoon pages are tall strips, so the height was unbounded:

- 1440 × 18500 decodes to 107 MB, over Android's 100 MB `RecordingCanvas` limit.
- Observed on the emulators (44238006080, old code): on api33-lowram (pixel_3a, low-RAM) the page is
  **blank** (screen all black under the reader bar, no error, no crash); on api36 it is drawn. The crash
  expected from the 100 MB limit was not seen. A 1000 × 12000 page (48 MB) drew on both.
- One such page also takes most of the 64 MB page cache, and is one GPU texture far past the 2048 px safe
  texture limit (GPU-dependent; real phones/TVs not tested → owner).

## Competitors (ideas only)

| App, version | File | What it does |
|---|---|---|
| Mihon v0.21.3 | `app/.../reader/viewer/webtoon/WebtoonStripImageView.kt` | "Draws a webtoon page as a stack of strips, so a very tall page never becomes one huge GPU texture"; frees large strips when off screen |
| Mihon v0.21.3 | `core/common/.../GLUtil.kt`, `ReaderPageImageView.kt` | `SAFE_TEXTURE_LIMIT = 2048`; paged mode uses SubsamplingScaleImageView with max tile = texture limit |
| Kotatsu v9.4.1 | `reader/ui/pager/webtoon/WebtoonImageView.kt` | webtoon page is a SubsamplingScaleImageView (tiled decoding) |

## Fix (least risk)

- `sources/manga/MangaPagePlan.kt` (pure): keep the 1600 px width cap; sample by powers of two until the
  page fits a budget (96 MB, 48 MB on low-RAM devices); a page taller than 2048 decoded rows is split
  into bands of at most 2048 decoded rows.
- `loadPage` decodes bands with `BitmapRegionDecoder` (JPEG, PNG, WebP, HEIF). A format it can't split
  (GIF) is decoded whole as before, but within the budget, so under the draw limit.
- `PageImage` draws one bitmap exactly as before for ordinary pages; strips are drawn in one Canvas
  sized by the page's aspect ratio (fill width in vertical mode, fit in paged mode). Content description
  "Page N" kept. `CancellationException` from a page load is no longer turned into a "failed" page.

No tiling/zoom-quality change: zoom stays the graphicsLayer scale it was (a tiled zoom is a feature).

## Action → reaction

| Action | Before | After |
|---|---|---|
| Open a 800 × 1200 page | one bitmap | same |
| Open a 1000 × 12000 webtoon page | one 48 MB bitmap, one oversized texture | 6 strips at full resolution, same 48 MB |
| Open a 1440 × 18500 page | blank on the low-RAM phone (107 MB, over the draw limit) | sampled to 720 × 9250 (27 MB), 5 strips, drawn |
| Low-RAM phone, page over 48 MB | one bitmap as large as the source | halved until under 48 MB |
| GIF taller than 2048 decoded rows | one bitmap, crash over 100 MB | one bitmap within the budget |

## Pros / cons

- Pros: huge pages are drawn on low-RAM phones; every bitmap stays under the texture limit; memory per page bounded; ordinary
  pages untouched.
- Cons: a page over the budget loses resolution (only pages over 96 MB, or over 48 MB on low-RAM devices). A tall GIF is still one bitmap (texture limit may leave it blank on some GPUs).

## Tests

- Unit `MangaPagePlanTest` (new class, no fail-before possible): strips, budget, width cap unchanged.
- Device `MangaTallPageDeviceTest` (fake source, PNG made row by row): a 1000 × 12000 top red / bottom blue
  after scrolling; b 1440 × 18500 drawn (red at the top). Fail-before on the old code, then pass-after.
