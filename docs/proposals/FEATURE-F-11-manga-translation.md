# F-11 Manga auto-translation (proposal only; gated; cloud path is an owner call, OMT-25)

Status: **proposal, not built.** Part 8 gate not passed (4.4). The owner approves before any code.

## 1. Current code (read 2026-11-18, head 3ed2d14)

- `ui/screens/manga/MangaScreens.kt:490` `MangaReaderScreen`: pages from `provider.pages(chapterUrl)`,
  webtoon (`LazyColumn`) and paged (`HorizontalPager`) modes, `ZoomBox` pinch zoom, resume page via
  `MangaLibrary` (`lastChapterUrl`, `lastPage`).
- `sources/manga/MangaProvider.kt`, `MangaLibrary.kt`; Mihon/Mangayomi sources feed it.
- No OCR, no on-device ML runtime (no ML Kit / TFLite / ONNX in `gradle/libs.versions.toml`), no
  translation code.

## 2. Sources read (ideas and contracts only; **neither licence lets us copy into Yumeiro without the owner**)

- jedzqer/manga-translator-android at c980b45b3c806b678829307b68fd57d23c0892c7 (**AGPL-3.0**, LICENSE line
  4): pipeline detection → OCR → translate → render. `detection/BubbleDetector.kt`, `TfliteDualModel.kt`,
  `TextBlockMerger.kt` with a 21.4 MB `assets/models/detection/mixed-dual-s-e5_float16.tflite`;
  `ocr/OcrEngine.kt` (`recognize(bitmap)`, scored by region), `PaddleOcrBase.kt`, `PPOcrV6SmallRec.kt`,
  `KoreanOcr.kt`, `EnglishLineDetector.kt`, `LocalOcrConcurrency.kt`; translation through an LLM client
  (`network/LlmClient.kt`, `RequestTimeoutBudget.kt`, `RetryHandler.kt`); `detection/LocalModelMemoryManager.kt`
  unloads models; floating overlay over other apps via MediaProjection.
- Kala9527/ImmersiveComicReadingTranslation at 54867de006effe299e987317adc06c7c35efd935 (the brief's
  "Kotlin9527" repo is 404; this is the matching repo; **no licence file**, so nothing may be reused):
  screen capture overlay (`capture/ScreenshotCapturer.java`, `overlay/OverlayTranslationService.java`), page
  sent as a base64 JPEG to an OpenAI-compatible `/chat/completions` endpoint
  (`ai/OpenAiCompatibleClient.java:71-73, 318`), API key in AndroidKeyStore AES-GCM (`settings/SecretStore.java`).

Weaknesses: the first adds 20+ MB of models and heavy RAM (not for 2 GB phones by default); the second
sends whole pages to a third-party cloud with the user's key (privacy, cost, terms) and needs
screen-capture permission.

## 3. Design (inside Yumeiro's own reader; no overlay, no screen capture)

- Entry: reader menu "Translate pages" (off by default), per series language pair.
- Pipeline in a `MangaTranslate` service, one page at a time ahead of the reader, off main:
  1. Text-region detection + OCR **on device** as a downloadable model pack (not in the APK), only on
     non-low-end devices; size and model licence (e.g. PaddleOCR Apache-2.0) to be approved.
  2. Translation: on-device model pack where available; **cloud only if the owner approves**, with the
     user's own key, stored in AndroidKeyStore, and only text (never the page image) sent.
  3. Render: text boxes drawn over the page image in Compose (original kept; toggle to compare).
- Cache translations per page hash in `cache/manga-tl/` (bounded, cleared by Clear caches).
- Memory: model loaded on first use, unloaded on trim memory and when the reader closes.
- Feature flag `manga_translate`, kill switch; not offered on `DeviceClass.isLowEnd` or TV unless the user
  forces it.

## 4. Action–reaction

| Action / event | Reaction |
|---|---|
| Turn on Translate pages | Asks to download the model pack (size shown); Wi-Fi only by default |
| Page scrolls in | Translated overlay appears when ready; original shown meanwhile |
| Toggle original | Overlay hidden, same page |
| Low memory | Model unloaded, translation paused with a note |
| Offline (cloud mode) | On-device result if present, otherwise original pages and a note |
| Back / close reader | Work cancelled, model unloaded |
| Rotate | Overlay re-laid out from cached boxes |
| Bad OCR | Long-press a box to edit or hide it |

## 5. Pros and cons

- On-device first: + private, works offline; − large download, slower on mid phones, quality varies.
- Cloud (owner call): + quality; − privacy, cost, terms, key handling.
- Inside our reader vs overlay: + no MediaProjection or overlay permission; − only works in Yumeiro.

## 6. Tests (when approved)

Unit (pure): box merge and layout from fixture OCR output; cache key. Device: fixture chapter with known
text on api36 (OCR + overlay), memory check on api33-lowram (feature hidden), Back during work.

## 7. Owner decision (OMT-25)

On-device only, or also cloud with the user's own key? Model pack size limit? Licences of any model.
