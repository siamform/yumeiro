# Owner's report, 2026-11-18 (verbatim)

Source: the owner's eighth-cycle master handover prompt of 2026-11-18, sections 6 and 7, copied word for word.
No separate raw message was supplied; this is the owner's own text. Credentials that came with the prompt are
**not** reproduced here (they live only in the git-ignored `.env` and `secrets/`). Status and evidence for each
item live in docs/CHECKLIST-FEATURES-BUGS.md.

## 6. BUG REGISTRY

Follow-through still required:
- **BUG-1** Watch progress not restored across sessions (Home → Jump In, YumeMix Continue Watching, YumeMix banners). Fix partly in 97c99df. Test: play, seek, kill process, reopen, tap from all three entry points, assert position; cross-check Trakt/Simkl. G5, touches G3.
- **BUG-2** YouTube channels inaccessible. Test: open, list, scroll, open video, back to channel, both phones. Blocked-network path uses the safe route already documented; never break terms. G3, touches G1.
- **BUG-3** Download speed collapse (~50–70%). Emulator case d passes (3.4–5.2 s vs 19.99 s on 44037723917; rule: total ≤ 10 s, last 10% ≤ 3.5 s, run 44041714032). Stays open until the owner confirms on a phone. G8.
- **BUG-4** Upscaling and quality selection broken. Fix partly in 9e1c6c4. Test each resolution, verify applied track, both phones and TV image. G3.
- **BUG-5 / TV-4** Cross-source search on TV. Owner decision, G7 proposal 3. Write proposal with options and recommendation.

Batch one:
- **NBUG-1** Download drop at ~50–70% — same as BUG-3. G8.
- **NBUG-2a** TV cannot reach fullscreen from top row. G3/G1.
- **NBUG-2b** TV horizontal scroll on Home triggers YouTube Music. G1.
- **NBUG-2c** TV search returns nothing. G1.
- **NBUG-2d** TV player opens mobile UI, landscape fails. G3.
- **NBUG-2e** TV landscape does not load. G3.
- **NBUG-3** Music full-screen: taps hit elements underneath. G6. Emulator + UI Automator.
- **NBUG-4a** Web URL playback: download button missing. G3/G8.
- **NBUG-4b** IPTV Play Link download hidden from gallery. G8. MediaStore check.
- **NBUG-4c** App freeze on stream-format mismatch (DASH vs HLS). G3. Inject mismatch.
- **NBUG-4d** App freeze on upscaling. G3. Inject decoder failure.

Batch two:
- **NBUG-5** Dub count mismatch and silent fallback: 7 Sub episodes still show 7 on Dub filter; tapping Episode 7 silently plays Episode 1. Fix: filter list to exact available dubbed episodes; a missing dub must show an accurate message with recovery, never substitute. G3, touches the source-metadata path used by F-01.
- **NBUG-6** ISP blocking; per-feature DNS routing with DoH. Not a single app-wide fallback. Each feature or request declares its DNS; the engine serves it, so two features can use different resolvers simultaneously and the frontend loads normally. On failure, that feature tries remaining DNS one by one, sequentially — never in parallel storms — until one succeeds or the list is exhausted; then a clear error or the existing message. No silent substitution, no infinite retry. **Owner gate: if it can be built without any side effect, only then we build it.** Any measurable cost to stability, battery, startup, memory or existing features → rejected, propose the cheaper sequential single-engine fallback. G12, touches G11. Must not regress SmartDnsResilienceTest, DnsWireClientTest, DataCacheBoundsTest (all verified CI 44067269319).

NBUG-6 research note must answer before any code: (1) Is per-feature routing possible without forking the system resolver? OkHttp allows a Dns per client; can our clients be split, does any stack bypass OkHttp? (2) Cost of concurrent engines: CPU wake-ups, radio, memory per resolver, socket contention — the no-side-effect proof. (3) DoH bootstrap: a DoH endpoint is itself a hostname; what resolves cloudflare-dns.com before DoH is up? If it is system DNS, a blocking ISP blocks the bootstrap too. Answer with evidence. (4) Sequential exhaustion timing — measure, do not guess. (5) Per-feature DNS policy source (settings screen, per-integration defaults, hard-coded map) — owner's call. (6) In-flight requests on DNS switch: cancel, retry or wait; any race?

## 7. FEATURE REGISTRY

Gated (Part 8), not started, gate + owner approval required:
- **F-01** Language/Sub/Dub badges on search posters; tap opens player with that source/sub/dub preselected.
- **F-02** Telegram option.
- **F-03** Paste Terabox and Diskwala links to play videos.
- **F-04** User-added WebView site list (Hikari-style).
- **F-05** Nuvio, Skystream, Vega repositories. **Vega is a Fire TV OS, not a repo — confirm with the owner.**
- **F-06** 200% volume booster.
- **F-07** External video player support.
- **F-08** Upcoming Movies and Series.
- **F-09** Watch Later and Favorite by tapping posters.
- **F-10** Glassmorphism theme.
- **F-11** Manga auto-translation.
- **F-12** Dedicated vertical video player: portrait content, swipe-up next, bounded preload, auto-hide controls, Back returns to the normal player.

Batch two:
- **NF-1** Episode watch status: Watched badge; In Progress progress bar with exact position; Fresh/Unwatched state. Same single source of truth as BUG-1. G5, builds on 97c99df.
- **NF-2** (a) Storage type labels in the download manager: Offline cache vs Permanent (public gallery/storage). (b) Universal stream downloading: FFmpeg/HLS/DASH muxing so any playable stream can be downloaded without a provider button. G8, links to NBUG-4a/4b.
- **NF-3** Extension pop-up and alert blocker for CNCVerse, phisher98, etc. Already decided by **ADR-014** — this request confirms it, no new design. Finish ADR-014, including the ST-20 extension-threading guard.

Build order after gate and approval: F-09 → F-08 → F-06 + F-07 → F-10 → F-04 → F-05 → F-02 + F-03 → F-11 → F-01 → F-12 → NF-1, NF-2, NF-3 once their bugs close. Adjust only with evidence and explain why.

## Also listed by the owner (section 8, agent-only items)

- **DL-11 part 2** over-read 2–36%. Read the DL-11 ledger row and ADR-016 file first — this prompt's summary is not the source. Implement the bound, regression test, prove within budget on api33-lowram and api36.
- **X2-1** baseline profile not proven on hardware. Extend to the open tab after H8 evidence; re-measure FrameTimingBenchmark; no gain claimed without numbers.
- **N6-2** suspected Android 10 credential re-lock. Reproduce if an API 29 image exists; else apply the N6-4 guard and mark unit only with the reason.
- **ST-7** jank on software-rendered emulators; real-phone check is OMT-12.
