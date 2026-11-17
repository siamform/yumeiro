# F-06 200 % volume booster (proposal only; gated)

Status: **proposal, not built.** Part 8 gate not passed (4.4). The owner approves before any code.

## 1. Current code (read 2026-11-18, head a73a748)

- `ui/screens/video/VideoPlayerModal.kt:275, 344, 1769–1775`: the right-edge swipe changes the system
  `STREAM_MUSIC` volume through `AudioManager.setStreamVolume`, capped at `getStreamMaxVolume`; the HUD bar
  (`VideoPlayerHUD.kt:118–130`) shows level / max, clamped to 1.0.
- `player/equalizer/EqualizerManager.kt`: Equalizer, BassBoost, Virtualizer on a dedicated thread, because
  binding audio effects took 7.2 s of the main thread once (comment at line ~102). Any new effect must follow
  the same rule.
- `player/VideoPrefs.kt:68` `AUDIO_BOOST` info field shows bass boost only.
- No `LoudnessEnhancer` anywhere. Quiet sources at full system volume stay quiet.

## 2. Competitor (ideas and contracts only; no code copied)

Next Player `anilbeesetti/nextplayer` at commit 08845f882dfd3b296342cd2044f96ead9d621faa,
`feature/player/src/main/java/dev/anilbeesetti/nextplayer/feature/player/service/PlayerService.kt`:
creates `android.media.audiofx.LoudnessEnhancer(audioSessionId)` in `onAudioSessionIdChanged` (lines
476–488) when the user preference is on, re-applies the current gain, releases the old one; target gain set in
millibels with `enabled = gain > 0` (lines 493–499); the UI asks the service whether the effect exists
(`IS_LOUDNESS_GAIN_SUPPORTED`) before showing the boost range.

## 3. Design (least risk)

- Swipe past 100 % continues into a boost range 100–200 % only when the system volume is already at max.
  Boost = `LoudnessEnhancer` target gain, 0 to about +6 dB (≈ 2× perceived loudness ceiling is not linear;
  the label says "Boost", the bar shows a second colour for the boosted part — Yumeiro's own palette).
- The effect lives on the existing audio-effects thread (`yu-audio-fx`), never on main; created per audio
  session id, released on session change and on player release (no leak, ST-7 rule).
- Off by default after each video (boost resets to 0 when a new video starts) to protect ears and speakers;
  a Settings switch "Allow volume boost" (default on for phones, off on TV where the AV receiver handles it).
- If the effect is unsupported (some TVs, some vendors) the range does not appear.

## 4. Action–reaction

| Action | Reaction |
|---|---|
| Swipe up at max system volume | Bar continues 100→200 %, second colour; gain rises |
| Swipe down below 100 % | Gain to 0 first, then system volume |
| New video | Boost back to 0 |
| Headphones unplugged (becoming noisy) | Playback pauses as today; boost back to 0 |
| Effect unsupported | No boost range; bar stops at 100 % |
| Equalizer on | Both effects active on the same session (order set by the audio framework) |

## 5. Pros and cons

- Pros: quiet anime/IPTV sources become usable on phone speakers; small, contained change.
- Cons: clipping/distortion at high gain on cheap speakers; hearing risk (mitigated by reset per video and a
  cap); one more audio effect on low-RAM phones (bound off main).

## 6. Tests (when approved)

- Unit: swipe → (system volume, gain) mapping, pure.
- Device: `LoudnessEnhancer` created on the effects thread (StrictMode / main-thread watchdog clean), gain
  applied and released on player release; fail-before = no effect exists.

## 7. Owner decision

Approve the cap (+6 dB suggested), the per-video reset and the TV default (off).
