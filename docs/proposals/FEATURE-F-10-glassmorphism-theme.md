# F-10 Glassmorphism theme (proposal only; gated; ADR-017 proposed)

Status: **proposal, not built.** Part 8 gate not passed (4.4). ADR-017 (docs/SYSTEM-DESIGN.md) is
"Proposed", not accepted. The owner approves before any code.

## 1. Current code (read 2026-11-18, head df53d1e)

- `ui/theme/ThemeSettings.kt:14-21` `ThemeMode`: SYSTEM, LIGHT ("Washi Day"), DARK ("Tsuki Night"), DYNAMIC
  (Material You), TIME (day-part moods), AFTERGLOW. `ui/theme/Theme.kt:73-78` maps modes to `Palettes`.
- No blur, `RenderEffect` or backdrop code anywhere in `app/src/main/java` (search for `blur(`,
  `RenderEffect`, `haze`: none).
- `DeviceClass.isLowEnd` (`DeviceClass.kt:27-28`): low-RAM flag, memory class ≤ 192 MB, ≤ 3 GB RAM or ≤ 4
  cores. Phone minSdk 28, TV minSdk 24 (`app/build.gradle.kts:116`).
- Yumeiro's vibe: Japanese paper/ink names (Washi, Tsuki, Sumi), warm amber / moonlight-gold accents.

## 2. Sources read (ideas and contracts only)

- chrisbanes/haze at 30528aa2cecfe2cc0cddac081a1b5c41ae2ae378 (Apache-2.0):
  `haze/src/commonMain/.../HazePerformanceMode.kt` (Fixed quality 0..1; Balanced 0.5 default; Adaptive
  deprecated), `HazeEffect.android.kt` (below API 31 no `RenderEffect`; forced invalidation on pre-draw for
  API < 32), `TrimMemoryCallback.kt` (drops buffers on trim memory). Source/effect modifier pair:
  `hazeSource` on content, `hazeEffect` on the glass surface.
- styropyr0/PrismalAGSL at 0da7d9c0676bd9a9eb999d2b0905de880378da9d (MIT): `Platform.kt` three pipelines,
  Legacy (API 25–30, sampled frosted surface), Standard (API 31–32, `RenderEffect` blur), Liquid (API 33+,
  AGSL refraction); `pipelineOverride` to force a lower tier; `PrismalShaderCache.kt`.
- ui-ux-pro-max (local skill data, commit e59ff376): style "Glassmorphism": backdrop blur 10–20 px,
  translucent 10–30 %, 1 px light border; **avoid for** "low-contrast backgrounds, critical accessibility,
  performance-limited"; requires contrast 4.5:1, visible focus, reduced motion. Rules `blur-purpose`
  (blur to show what is behind a sheet, not decoration) and `effects-match-style`.

## 3. Design

- A seventh `ThemeMode.GLASS` ("Kasumi", mist), opt-in; the existing palettes stay the default. Kasumi
  uses Tsuki Night colours with glass **only on chrome**: bottom bar, top bars, sheets, dialogs, player
  overlays. Content (posters, lists, text blocks) stays opaque, per `blur-purpose`.
- Tiers, chosen once per process (single source of truth `GlassTier`):
  - Full: API 31+ and not `isLowEnd` → backdrop blur (haze-style source/effect, quality 0.5), 1 px border.
  - Tint: API 28–30, `isLowEnd`, TV, battery saver, or "Reduce transparency" (accessibility) → no blur,
    translucent tint 85–92 % opacity with the border. Same look family, no GPU cost.
  - Off: kill switch → Tsuki Night.
- No AGSL refraction in the first step (cost on mid phones; one tier less to test).
- Text on glass: contrast checked against the worst background (≥ 4.5:1); scrim under text when needed.
- Library choice: haze (Apache-2.0, maintained, Compose-native) after the owner approves the dependency;
  APK cost to be measured (budget ≤ 300 KB).

## 4. Action–reaction

| Action / event | Reaction |
|---|---|
| Pick Kasumi in Appearance | Chrome turns to glass (or tint on low-end); preview first |
| Scroll Home under the glass bar | Blur follows content; frame budget unchanged on Full tier |
| Low memory (trim memory) | Blur buffers dropped; tint until memory is back |
| Battery saver on | Tint tier |
| Accessibility: high contrast / reduce transparency | Tint tier at 92 % |
| TV | Tint tier; focus ring stays fully opaque |
| Rotate / kill / restore | Same mode; tier recomputed |
| Restore an old backup | Unknown mode → SYSTEM (existing `getOrDefault`) |

## 5. Pros and cons

- Chrome-only glass: + keeps content legible and the vibe; − less dramatic than full-screen glass.
- Tint tier on low-end/TV: + no jank for 2 GB phones and TV boxes; − the look differs by device.
- haze dependency: + tested library; − one more dependency and APK bytes.
- Risk at 200k users: GPU-driver blur bugs (API 31–32 invalidation, noted in haze); mitigated by tier
  override and the kill switch.

## 6. Tests (when approved)

Unit (pure): `GlassTier.choose(api, lowEnd, tv, saver, a11y)`. Device: FrameTimingBenchmark jank on Home
with Kasumi on api33-lowram and api36 (budget: P90 frame time within 5 % of Tsuki Night), screenshot contrast
check, TV focus test on tv-api34. Fail-before: no GLASS mode (test cannot select it).

## 7. Owner decision

Approve ADR-017, the name, chrome-only scope and the haze dependency.
