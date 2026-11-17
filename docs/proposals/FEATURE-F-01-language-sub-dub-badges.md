# F-01 Language / Sub / Dub badges on search posters (proposal only; gated)

Status: **proposal, not built.** Part 8 gate not passed (4.4). The owner approves before any code.

## 1. Current code (read 2026-11-18, head c9428d2) — most of F-01 already exists

- `online/OnlineRepository.kt:48–62` `OnlineItem.dub` (anime dub/sub status from the extension) and lazy
  `tags = ReleaseTags.parse(name, quality, type, dub)`; computed on the extension thread in `toItem()`
  (line ~507–516), never while a card composes.
- `online/ReleaseTags.kt`: `Kind` LANGUAGE / SOURCE / RESOLUTION / TYPE; audio rule "multi > dual > languages
  named in the title (Bengali, Hindi, …), else anime Dub · Sub / Dub / Sub" (lines ~127–142). Unit
  `ReleaseTagsTest` (CI).
- `ui/screens/online/OnlineComponents.kt:119` `PosterTags`: type + release top-left, audio chip bottom-left,
  glass style; used by `PosterCard` (line 161), which `OnlineSearchScreen.kt:258` uses for results. Cached
  results keep the dub set (`cache/OnlineCodec.kt:25`, key `d`).
- Shipped in ff7cb19 (R1–R11b redesign).

## 2. Gaps found in the read

| Gap | Effect |
|---|---|
| Only CloudStream `AnimeSearchResponse.dubStatus` feeds `dub`; JS sources (`sources/js/JsProviders.kt:111`) and Aniyomi (`sources/tachiyomi/AniyomiProvider.kt:91`) pass `DubStatus.None` | No Sub/Dub chip for those sources unless the title names a language |
| Versions learned on the detail page (`OnlineDetail.episodes` keys "Sub"/"Dub") are not written back to the poster | Search shows no chip; detail page shows both versions |
| No device test asserts the chip on a search result | Regression risk |

## 3. Competitor (ideas and contracts only; no code copied)

CloudStream `recloudstream/cloudstream` at 2dcc25c3945c61dfa95d1710ced7c74acfe84baa: search results carry
`dubStatus: EnumSet<DubStatus>` (the same contract Yumeiro already reads) and the result card shows Dub/Sub
labels from it (`ui/search/SearchResultBuilder.kt`). Nothing more to take: Yumeiro already exceeds it with title-language
parsing.

## 4. Design (least risk)

- Write-back: when a detail page loads with "Sub" and/or "Dub" episode keys, update the in-memory
  `previews[item.key]` dub set and the search cache entry (off main), so the next render of that poster shows
  the chip. No new network call.
- JS / Aniyomi: map their own dub hints only where the source format has a field for it; otherwise keep title
  parsing (no guessing).
- Device test: search a local test provider whose result has `dubStatus = {Dubbed, Subbed}`; assert the
  `poster_tags` chip "Dub · Sub" on the result card.

## 5. Action–reaction

| Action | Reaction |
|---|---|
| Search an anime with dub status | "Dub · Sub" chip bottom-left (today) |
| Open the detail page of a title without status, it has Sub+Dub episodes, go back | Chip appears on that poster |
| Title says "Hindi Dubbed" | "Hindi" chip (today) |
| Non-anime without language info | No chip (today) |

## 6. Pros and cons

- Pros: the feature mostly exists; the gaps are small and local; no layout change.
- Cons: write-back adds one cache update per detail open.

## 7. Owner decision

Confirm the feature as shipped is what was meant, and whether the write-back gap is worth building.
