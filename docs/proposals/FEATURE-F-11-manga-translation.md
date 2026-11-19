# F-11 Manga page translation, on the device (ADR-044)

Status: **Built, not tested** (batch B of PROGRAM-2026-11-19). Flag `f11_manga_translate` (kill switch).
Owner decisions used: "size never matters for me, just do what ever is best" (2026-11-19), so the OCR
models are bundled. There's no cloud key and no page upload. OMT-25 (cloud with the user's own key) is
closed: **not built**, because on-device needs no key and sends nothing.

## 1. Current code (read 2026-11-19, head cf124d38)

- `ui/screens/manga/MangaScreens.kt` `MangaReaderScreen`: webtoon (`LazyColumn`, fill width) and paged
  (`HorizontalPager`, Fit) modes, `ZoomBox`, `PageImage` (tall pages split into strips, G7-MR1).
- No OCR, ML runtime or translation code before this item.

## 2. Competitors (ideas only; nothing copied)

| Project | Commit | Files read | Notes |
|---|---|---|---|
| [jedzqer/manga-translator-android](https://github.com/jedzqer/manga-translator-android) (AGPL-3.0) | c980b45b3c806b678829307b68fd57d23c0892c7 | `detection/BubbleDetector.kt`, `TfliteDualModel.kt`, `TextBlockMerger.kt`, `LocalModelMemoryManager.kt`; `ocr/OcrEngine.kt`, `PaddleOcrBase.kt`, `PPOcrV6SmallRec.kt`, `KoreanOcr.kt`, `EnglishLineDetector.kt`, `LocalOcrConcurrency.kt`; `network/LlmClient.kt`, `RequestTimeoutBudget.kt`, `RetryHandler.kt` | Detection → OCR → LLM translation; floating overlay over other apps (MediaProjection); 21.4 MB detector model |
| [Kala9527/ImmersiveComicReadingTranslation](https://github.com/Kala9527/ImmersiveComicReadingTranslation) (no licence) | 54867de006effe299e987317adc06c7c35efd935 | `capture/ScreenshotCapturer.java`, `overlay/OverlayTranslationService.java`, `ai/OpenAiCompatibleClient.java`, `settings/SecretStore.java` | Sends the whole page as JPEG to an OpenAI-compatible endpoint with the user's key |

Both need screen capture or an overlay permission. One of them also sends pages to a cloud service.
Yumeiro translates inside its own reader, so it needs no permission and sends no page. It also caches
results per page and releases the models under memory pressure.

## 3. Design (built)

- `sources/manga/MangaTranslate.kt`:
  - `MangaTranslate`:
    - settings `tl_on/tl_src/tl_dst/tl_wifi_only` in the `manga_reader` prefs;
    - `prepare` downloads the language pack (Wi-Fi only if the user chooses it, 300 s limit);
    - `page()` works one page at a time (mutex), memory LRU 48 → disk `cacheDir/manga-tl` (8 MB cap, oldest out) → OCR + translate;
    - `release()` on trim memory / reader close;
    - `clear()` for Clear caches.
  - `MlKitTlEngine`: [ML Kit](https://developers.google.com/ml-kit/terms) text recognition (bundled
    Japanese / Chinese / Korean / Latin models) and on-device Translation. OCR reads at most 2048 px on
    the long side and scales the boxes back.
  - `MangaTlPlan` (pure):
    - line join per script, with "-" mending for Latin;
    - filtering of page numbers, punctuation, specks and single Latin letters;
    - tall (vertical-text) bubbles widened around their centre, up to 45 % of the page;
    - image-fit mapping (Fit vs fill width);
    - cache key = SHA-256 of engine + source + target + page id;
    - JSON encode/decode.
- Reader UI:
  - Translate icon (`reader_translate`);
  - a sheet with on/off, source (ja/zh/ko/latin), 14 targets (the default is the device language when supported) and Wi-Fi only;
  - a status chip (`tl_status`, tap to retry);
  - translations drawn as white boxes over each bubble (`tl_box_<page>_<i>`, the content description is the translation). Long-press hides a box, and the original page always stays underneath.
- `MlKitInitProvider` is removed from the manifest, so ML Kit starts only when the user turns this on.

## 4. Feature profile

| Field | Value |
|---|---|
| Purpose | Read raw Japanese / Chinese / Korean / Latin-script manga in the user's language |
| Entry points | Manga reader top bar → Translate |
| Storage / migration | 4 keys in `manga_reader`; cache files in `cacheDir/manga-tl`; ML Kit language packs in app files (managed by ML Kit); no migration |
| Permissions | None new |
| Settings | Translate sheet; flag `f11_manga_translate` |
| Backup / restore | `manga_reader` prefs go with the existing backup (choices restored); cache and packs are not backed up |
| Reset | Clear caches → `MangaTranslate.clear` (unit-tested); app data reset removes the packs |
| Errors / offline | Pack missing → chip "Getting the Japanese → English language pack…"; failure or offline → chip "Couldn't get the … language pack. Check the connection, then tap to try again." (Wi-Fi only on mobile data ends the same way after 300 s); a page whose OCR/translation fails stays as the original page (no box, no crash); cached pages work offline |
| Accessibility | Each box carries its translation as content description; buttons labelled; contrast black on white |
| TV | Icon and sheet are focusable; same reader on TV; checked on both TV images |
| Low-end | Off by default; OCR capped at 2048 px; one page at a time; models released on trim memory |
| Performance budget | 0 ms at cold start (provider removed); per page ≤ 3 s on a mid phone after the pack is present (owner test); emulator assertion ≤ 20 s; disk ≤ 8 MB |
| Inventory | INVENTORY rows F-11 Translate sheet, F-11 overlay, F-11 cache/reset |

## 5. Side effects

- APK size grows by the bundled OCR models (about 30–40 MB across ABIs; accepted by the owner).
- Startup: none (the init provider is removed). Memory: models only while translating.
- Existing reader: with the setting off nothing new runs, and `MangaTallPageDeviceTest` runs as regression.
- Network: only the one-time language pack download from Google, after the user turns translation on.

## 6. Service terms

Translation runs on the device under the [ML Kit terms](https://developers.google.com/ml-kit/terms).
Pages aren't uploaded anywhere, and DRM, sign-in and paywalls aren't touched. It translates only pages
the reader already shows.

## 7. Tests

- Unit `MangaTranslateTest` (fail-before = compile failure): join, filtering, fit, layout, key,
  encode/decode, page pipeline with a fake engine (strip offsets, cache, release, clear), settings.
- Device `MangaTranslateDeviceTest`:
  - a: real ML Kit reads the fixture `tl_page.png` (こんにちは / ありがとう, a page number and a face) → "hello" / "thank";
  - b: in the reader the boxes appear, and turning it off removes them.
  - Runs on both phones and both TVs, with `MangaTallPageDeviceTest` as regression.
- Owner/community test: quality on real chapters (OWNER-MANUAL-TESTS, Yumeiro users section).
