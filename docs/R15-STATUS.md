# R15 status: every item in the master prompt, with its evidence

## Resume here (updated 2026-11-18 16:55 UTC, v1.8.2 cycle, G8 reopen regression + G9 TV regression)

- **Orders:** master prompt + owner handover prompt of 2026-11-18 + §13a. Order: G8 → G9 → G10 → G11 → v1.8.2
  (owner approval) → stability-v2. Full G8 evidence: superseded notes below and GROUP-8 (close docs pending).
- **G8 reopen (G8-9, G8-10, ADR-022):** register 30ad033; fixes c239a83 (media3-muxer), 2fc2113 (media3-inspector),
  Media3 only below API 28. CI 44336335642: 1194 unit, 0 failed, 11 skipped. Pass-after tv-api24 ForceDownload
  44339775707 OK (3 tests). Regression on the fix: phones A 44342030478 and B 44346504202 all PASS, no skips
  (rxOverSize 1.099/1.030). TV T1, T2a, T2b (throughput alone) on both TVs: next.
- **G9 TV regression:** first try 44335814445/44335820792 = my dispatch fault (no package in class names, nothing
  ran). tv-api34 batch 1 44339783655: 6 PASS; SettingsWalk, Bug1YumixRestore, Extensions = phone-only navigation in
  the tests → test fixes c4d08b1 + next commit; reruns 44343884669 (SettingsWalk, Extensions PASS) and 44347442782
  (Bug1YumixRestore PASS). PinnedExtensions FAIL on TV = real UI issue: the "Show videos from" sheet opens
  half-height on TV, rows below the first are off-screen (screenshot in 44339783655) → queue row (Yumix/G4,
  possible cause of NBUG-10). Phones batch 1 44323642049, batch 2 44328532155 PASS.
- **Next three actions:** (1) TV G8 T1/T2a/T2b + TV G9 tv-api24 batch 1, both TVs batch 2 (≤ 2 at once, one per TV
  API: same-API runs share a concurrency group); (2) G8 close docs; (3) G9 close docs.
- **UNVERIFIED-QUEUE.md:** 70 rows (G8/G9 additions pending with the close docs).

### Previous resume note — superseded (14:40 UTC, G8 step 9 regression + G9 step 5)

- **Orders:** master prompt "HARD RULES, GROUP COMPLETION, v1.8.2 RELEASE" + owner handover prompt of 2026-11-18
  (new agent) + §13a. Order: G8 → G9 → G10 → G11 → v1.8.2 (owner approval) → stability-v2.
- **G8 results read by the new agent (all at head 3680464 = fix 877413b + docs):**
  - Fail-before DL-11 p2 baseline: CI 44283506177 (f2a7c73): 1193 tests, 1 failed = exactly
    `Dl11PaceTest#aFastLinkAtItsOwnSpeedDoesNotTrip` (Dl11PaceTest.kt:68). Proven.
  - Pass-after CI 44286276997: 1216 tests, 0 failures, 11 skipped (G8DownloadsBatchTest 7/7, Dl11PaceTest 8/8,
    DownloadThroughputTest 8/8).
  - Pass-after device 44286280482 (`DownloadThroughputDeviceTest` a + d ×3): all PASS. Case a rxOverSize
    api33-lowram 1.056/1.050/1.038 (median 1.050), api36 1.030/1.036/1.030 (median 1.030): **budget met**
    (each ≤ 1.10, median ≤ 1.06). Case d 1.048/1.050/1.043 and 1.059/1.044/1.044.
  - Phone regression (both phones, all PASS): 44289731618 (Cleanup, Kill, Quality, Resume, Throughput,
    DownloadsScreen, ExternalDownloader) and 44289743979 (DownloadG8, ForceDownload, LowStorage, StorageMainThread,
    StreamWatchDownload, TorrentDownload, YtDownloadPipeline) = all 14 download device classes. Case c (per-request
    capped local host, restarts intended, no rx budget) read 1.103 on api33-lowram.
- **TV regression batch 1 read (head 3680464):** tv-api24 44299923467: G8, Resume, Cleanup, Quality PASS; LowStorage a
  PASS, b SKIPPED (no fallocate on Android 7); DownloadsScreen SKIPPED (API 28 gate); DownloadKill FAIL = test fault
  (~50 MB/s link finished the file in the fixed 1.5 s before the kill) → test fixes 45d6763, 0a4bd0b.
  tv-api34 44299928223: DownloadsScreen, G8, Kill, Cleanup, Quality PASS; LowStorage b SKIPPED (32-bit fallocate
  filled 1.1 GB of 5.2 GB; dd fallback 0a4bd0b); **DownloadResume b FAIL = new finding G8-8** (Retry during the
  failing worker's last moment dropped by ExistingWorkPolicy.KEEP → item "Queued" forever). Test 22fc1da
  (DownloadG8DeviceTest#d), fix 8a4cfa0.
- **G8-8 proof:** fail-before 44305926621 (throwaway ab/g88-before = old code + final test, branch deleted): d FAIL
  on both phones, `status=QUEUED` (proven). Earlier attempts were test faults: 44302259076 lowram (hold enqueued
  while first run RUNNING → d699d96), 44304053909 api36 passed on old code (MTApplication start-up resumeQueued at
  +8 s rescued it → 78caf5e waits until the process is up 20 s). CI 44302429664 on f6e26b0 (fix 8a4cfa0): success;
  only androidTest changed since.
- **G8-8 pass-after:** device 44307672019 (78caf5e): DownloadG8DeviceTest OK (4 tests) on both phones.
- **G8 regression (code = 78caf5e; 34d99b8 docs only), all PASS, no skips:** phones A 44309347790, B 44309363764
  (14 classes, both phones; throughput rxOverSize 1.099/1.030); TV batch 1 tv-api24 44310073153, tv-api34 44312157796.
  Running: TV batch 2 tv-api24 44314925557 (ref ab/g8-close = 34d99b8, throwaway), then tv-api34. Probe NBUG-9
  44312163084 (Nbug9DnsProbeDeviceTest, phones) running.
- **G9:** steps 1–3 34d99b8 (register + ADR-021). Tests b51424b. Fail-before CI 44312331848 (ab/g9-before = b51424b):
  1194 tests, 1 failed = exactly G9SettingsBatchTest#g9_1 (proven); device fail-before (RestoreAfterClear,
  ExtensionSharedFile, phones) not yet run. Fixes pushed: 30c4641 G9-1, 6219b4a G9-2 (backup/RepairRetry), d9b01cb
  G9-5, c056ff4 G9-7.
- **Next three actions:** (1) device fail-before on ab/g9-before; read NBUG-9 probe (add finding + test if
  reproduced); (2) TV batch 2 tv-api34, then G8 close docs in one push (gaps listed in the superseded note below
  are still open); (3) G9 pass-after CI + device, then G9 group regression.
- **UNVERIFIED-QUEUE.md:** 70 rows.

### Previous resume note — superseded (v1.8.2 cycle, G6 closed, G8 batch)

- **Orders:** master prompt "HARD RULES, GROUP COMPLETION, v1.8.2 RELEASE" (2026-11-18) + owner rule §13a
  (batch CI by group, docs/DEVELOPMENT.md). Order: G6-QP → G8 → G9 → G10 → G11 → v1.8.2 (owner approval) → stability-v2.
- **G6:** closed. G6-QP Verified (CI unit + emulator, both phones): 44266530216 → 44266534566, CI 44268336205,
  regression 44272600174 (7 classes PASS both phones). Report docs/reports/2026-11-18-v1.8.2-cycle-report.md.
- **Current group / step:** G8 Downloads, §13a step 6 (pass-after). Doc + ADR-020 bb09ea7; tests 0d3e53d.
- **Fail-before done (old code + tests, temp branch ab/g8-before = 0d3e53d, now deleted):** CI 44275576843: 1192
  tests, 8 failures = exactly the 8 new unit tests (G8DownloadsBatchTest ×7, DownloadThroughputTest
  #aSingleStallIsRiddenOutNotRestarted), 1184 others green. Device 44275580304: G8D_RESULT a/b/c FAIL on api33-lowram
  and api36. Nothing went to the queue (no test passed on old code).
- **Fix commits (one per finding, test order):** ab27bc1 G8-1, 34d6560 G8-2, 43c1c11 G8-3, dcfd9c0 G8-4, 3185faf G8-5,
  b099802 G8-6, e8f450c G8-7, 1b45fc2 DL-11 part 2.
- **Pass-after so far:** CI 44278620756 (1b45fc2): 1215 tests, 0 failures, all 8 new green. Device 44278624792:
  G8D_RESULT a/b/c PASS on both phones. Throughput device 44280021547 (a + d ×3, both phones): all PASS, but the
  DL-11 p2 real-host budget is MISSED (rxOverSize 1.075/1.127/1.081 api33-lowram, 1.051/1.047/1.030 api36; median
  1.063 > 1.06, one > 1.10). Second cause found (baseline over 1 ms from the socket buffer); DL-11 p2 isolated
  (§13a step 7): test f2a7c73, fix 877413b (BASELINE_MIN_NS 50 ms).
- **Runs in progress:** CI 44283506177 on f2a7c73 (fail-before of Dl11PaceTest#aFastLinkAtItsOwnSpeedDoesNotTrip).
- **Next three actions:** (1) read 44283506177 (the new test must be red, all else green); (2) pass-after CI on 877413b
  + DownloadThroughputDeviceTest a + d ×3 both phones (budget each ≤ 1.10, median ≤ 1.06); (3) group regression campaign
  (≤10 classes per run) on the close commit, then the close docs in one push.
- **UNVERIFIED-QUEUE.md:** 70 rows.

### Previous resume note — superseded (v1.8.2 cycle, G6-QP close)

- **Orders:** master prompt "HARD RULES, GROUP COMPLETION, v1.8.2 RELEASE" (2026-11-18) + owner rule §13a
  (batch CI by group, docs/DEVELOPMENT.md). Order: G6-QP → G8 → G9 → G10 → G11 → v1.8.2 (owner approval) → stability-v2.
- **Current group / step:** G6-QP close regression. CI 44268336205 at d272a1f green (unit: 1207 tests, 0 failures,
  11 skipped; QueuePersistenceTest 9/9). Device fail-before 44266530216 (ab/qp-before), pass-after 44266534566.
- **Runs in progress:** device 44272600174 (both phones, head 6a7d8fb): QueuePersistence, ResumeAcrossProcess,
  Bug1YumixRestore, LibraryEmptyProfile, MusicWifiLock, MusicAudioFocus, MusicNotificationControls.
- **Next three actions:** (1) check 44272600174 markers + no "DiskReadViolation LastPlayed"; (2) G6 close docs
  (ledger, checklist, inventory, proposal §8, ADR-019, GROUP-6) + delete ab/qp-before; (3) G8 §13a step 1: read all G8 code.
- **UNVERIFIED-QUEUE.md:** 70 rows.

### Previous resume note — superseded (updated 2026-11-18, eighth cycle, session 2, step 10)

- **Orders:** eighth-cycle master handover prompt of 2026-11-18, plus the owner's message of 2026-11-18: finish G7
  and its docs, then **stop and wait**. Do **not** start G8, G9, G10 or any later item until the owner says so.
  Report: docs/reports/2026-11-18-eighth-handover-report.md. Index: docs/CHECKLIST-FEATURES-BUGS.md.
- **Current group / step:** paused after L2 G7. G5, G6, G7 done for agent scope.
  - G6 verified: ST1, WL, AF, NC, PG1, MO1, SP1 (CI unit and/or emulator, both phones); LF, LY (CI unit, servers
    mocked); PG2 (CI unit; emulator end-to-end Blocked: owner OMT-29, YouTube bot wall on CI IPs, runs 44242208629,
    44243818628). G6-QP Blocked: owner (GROUP-6 proposal 1). Unplug → OMT-28; Last.fm real account → OMT-10.
  - G7 verified: MR1 tall pages (CI unit 44245720453 + emulator 44239024442), MB1 duplicate keys (CI 44241940033 →
    44245720453; emulator 44245822405 → 44245826911). Scope audit in GROUP-7 "Eighth cycle audit"; offline
    chapters, automatic failover, EPG guide = features (Part 8 gate). Real sources → OMT-11.
- **Runs in progress:** none. Temp branches: none (ab/mb1-before, ab/mb1-dev-before deleted).
- **Next three actions (only when the owner resumes):** (1) G8 Downloads per master prompt §8 (queue/concurrency,
  ranges, resume after kill, network change, disk full, duplicates, cancellation, HLS/DASH merge, scoped storage;
  includes DL-11 part 2 and NBUG-1); (2) G9 Settings; (3) G10 Errors, then L3 G11, L4, L5.
- **L5 notes kept:** Music "1 tracks" plural; GROUP-7 minor notes (single-quoted M3U attributes, relative channel
  URLs, tiny prefs reads on main).

### Previous resume note — superseded (updated 2026-11-18, eighth cycle, session 2, step 9)

- **Orders:** eighth-cycle master handover prompt of 2026-11-18. Report: docs/reports/2026-11-18-eighth-handover-report.md.
  Index: docs/CHECKLIST-FEATURES-BUGS.md.
- **Current group / step:** L2 G6 Music. Verified: ST1, WL, AF, NC, PG1, SP1 (emulator both phones); MO1, LF, LY (CI unit).
  G6-PG2 (endless Home: offline scrolls closed it; cap ~40 not 80) fix written locally; fail-before CI on ab/pg2-before.
- **Runs in progress:** CI 44235994633 (ab/pg2-before, YmEndlessCapTest fail-before).
- **Next three actions:** (1) PG2 commit + CI pass-after; (2) G6 device run: MO1 with the emulator network toggled;
  (3) queue persistence audit (GROUP-6 proposal 1 = owner approval), then G7 Manga & Live TV.

### Previous resume note — superseded (updated 2026-11-18, eighth cycle, session 2, step 8)

- **Orders:** eighth-cycle master handover prompt of 2026-11-18. Report: docs/reports/2026-11-18-eighth-handover-report.md.
  Index: docs/CHECKLIST-FEATURES-BUGS.md.
- **Current group / step:** L2 G6 Music. ST1, WL, AF, NC verified (emulator, both phones). PG1 verified
  (CI unit 44223304640 + emulator both phones 44225724090). MO1 fix 7d1486a (fail-before 44225572082). Mocked
  tests committed: LRCLIB f3879dd, Last.fm queue 3128a1b. G6-SP1 found: no music-access prompt when music
  access is denied (proposal docs/proposals/FEATURE-G6-SP1-music-access-prompt.md); fail-before test on ab/sp1-before.
- **Runs in progress:** CI 44227662448 at 7d1486a (MO1 pass-after + LrcLibMockedTest + ScrobbleFlushMockedTest).
- **Next three actions:** (1) read 44227662448, record MO1/LY/LF; (2) CI fail-before on ab/sp1-before, then the
  SP1 fix; (3) queue persistence audit, then G7.

### Previous resume note — superseded (updated 2026-11-18, eighth cycle, session 2, step 7)

- **Orders:** eighth-cycle master handover prompt of 2026-11-18. Report: docs/reports/2026-11-18-eighth-handover-report.md.
  Index: docs/CHECKLIST-FEATURES-BUGS.md.
- **Current group / step:** L2 G6 Music. G6-ST1, G6-WL, G6-AF, G6-NC verified (emulator, both phones; 44219681816).
  Headphones unplugged → OMT-28. G6-PG1 verified (CI unit, 44223304640). G6-MO1 (picked Online lost after a
  connection drop) found; fix written locally, fail-before on ab/mo1-before.
- **Runs in progress:** CI 44225572082 (ab/mo1-before, fail-before of MusicModeReconnectTest).
- **Next three actions:** (1) G6-MO1 fix commit + CI pass-after; (2) device paging past page 1 with the network
  toggled (G6-PG1 device); (3) lyrics with LRCLIB mocked, Last.fm rules/queue/retries mocked, Android 13+ storage.

### Previous resume note — superseded (updated 2026-11-18, eighth cycle, session 2, step 6)

- **Orders:** eighth-cycle master handover prompt of 2026-11-18. Report: docs/reports/2026-11-18-eighth-handover-report.md.
  Index: docs/CHECKLIST-FEATURES-BUGS.md.
- **Current group / step:** NBUG-5 signal D verified (emulator api33-lowram; CI 44211141941 green). G5 large-library scan
  measured (44211556974, no fix needed). G5 complete on the emulator except real hardware.
- **Runs in progress:** none.
- **Next three actions:** (1) upgrade test `-f upgrade_from=v1.8.0` at head; (2) RtlLayoutDeviceTest + WidgetUpdatesDeviceTest
  re-run at head; (3) L2 G6 Music (service lifecycle, focus, headset, notification, sleep timer, queue persistence, …).

### Previous resume note — superseded (updated 2026-11-18, eighth cycle, session 2, step 5)

- **Orders:** eighth-cycle master handover prompt of 2026-11-18. Report: docs/reports/2026-11-18-eighth-handover-report.md.
  Index: docs/CHECKLIST-FEATURES-BUGS.md.
- **Current group / step:** NBUG-5 signal D (mixed language runs in one Dub list; Yumeiro's number sort interleaved
  them). Fix written (OnlineSession.labelledEpisodes + tile caption + count), note §4b/§5b. G5-PC1/PC2 CI green; reopen timing PASS.
- **Runs in progress:** CI 44206044268 (fail-before on ab/nb5-before).
- **Next three actions:** (1) fail-before red on the 3 target tests → commit fix on r15-stability, CI pass-after;
  (2) device re-run DubCountProbeDeviceTest (shown12 run by run); (3) upgrade from v1.8.0, RtlLayout + WidgetUpdates re-run, G6.

### Previous resume note (step 4)


- **Orders:** eighth-cycle master handover prompt of 2026-11-18. Report: docs/reports/2026-11-18-eighth-handover-report.md.
  Index: docs/CHECKLIST-FEATURES-BUGS.md.
- **Current group / step:** L2 G5 (Library). G5-PC1 (227c203) and G5-PC2 (a96db17) verified on the emulator (both phones);
  empty-profile device test `LibraryEmptyProfileDeviceTest` passes. ST-18 verified (emulator) for the structural change; TV-H5-T fixed.
- **Runs in progress:** CI 44203248895 (pass-after unit); device 44203529014 (reopen timing split).
- **Next three actions:** (1) 44203248895 green → drop "(running)" in CHECKLIST/ledger; (2) read reopen timing → if Library
  Jump back in waits for the full MediaStore scan, measure it with a large library and write a G5 note before any fix;
  (3) rest of G5 (playlist 10,000+ items, sort/filter, backup inclusion), then NBUG-5 probe 2, upgrade from v1.8.0, G6.

### Previous resume note (eighth cycle, session 2, step 3)

- **Orders:** eighth-cycle master handover prompt of 2026-11-18. Report: docs/reports/2026-11-18-eighth-handover-report.md.
  Index: docs/CHECKLIST-FEATURES-BUGS.md.
- **Current group / step:** L1 G12 ST-18 (two deferral commits cbad735, 77d387a; structural pass on api33-lowram);
  EXT-T1 e13 test fault fixed (c6ff72c); NBUG-6 unit timing test fixed (c6f06ff).
- **Runs in progress:** device 44190759388 (Home/Back/HeroCarousel both phones),
  CI 44192309663 (unit).
- **Next three actions:** (1) done: 44189135829 e13 20/20 PASS → EXT-T1 Cannot reproduce, OMT-26;
  (2) 44190759388 green → ST-18 Verified (emulator) for the structural change, then TV TvBug5ReproDeviceTest;
  (3) done: NBUG-6 measured, gate not passed (OMT-27); NBUG-5 second probe (repeated Dub numbers), then upgrade from v1.8.0 at head.

### Previous resume note (eighth cycle, session 2, step 2)

- **Orders:** eighth-cycle master handover prompt of 2026-11-18. Report: docs/reports/2026-11-18-eighth-handover-report.md.
  Index: docs/CHECKLIST-FEATURES-BUGS.md.
- **Current group / step:** L0 agent items. DL-11 part 2: option G rejected by reading (note §8.3), cause logging
  6ec4868 (`trips=[…]` in SEGMENTED_STATS). EXT-T1: e13 test seeds five timing-out studio feeds while Source status is open.
- **Runs in progress:** CI 44180839496 (unit, 6ec4868); device 44180844505 (DownloadThroughputDeviceTest a + d ×3, both phones).
- **Next three actions:** (1) read trip logs of 44180844505 → choose J / K / L, fail-before unit test, fix, CI, device a + d;
  (2) EXT-T1 e13 campaign ≥ 20 on api33-lowram (after the open runs); none reproduce → OWNER-MANUAL-TESTS, Blocked: owner;
  (3) NBUG-5 note and NBUG-6 research note (six answers).

### Previous resume note (eighth cycle, session 2, step 1)


- **Orders:** eighth-cycle master handover prompt of 2026-11-18 (section 17 first actions, then L0→L5). Repo head
  at session start 063b552 (the prompt said 912093b; 47 newer commits). Report:
  docs/reports/2026-11-18-eighth-handover-report.md. Index: docs/CHECKLIST-FEATURES-BUGS.md (batch two added).
- **Current group / step:** first job done (raw report file, checklist, report). Next: DL-11 part 2 option G
  (soft handover) per note §8.2 → ADR-016 amendment → fail-before unit test → fix → CI → device a + d.
- **Verified (emulator) carried over:** N6-2, BUG-1, BUG-2, BUG-4 (phones + TV), TV-3; spot-check 2026-11-18 holds.
- **Runs in progress:** none. 44162062169 read: EXT-T1 10/10 PASS, condition not seeded (test fault EXT-T1-C).
- **Next three actions:** (1) DL-11 G: read `Segmented` piece ownership, write ADR-016 amendment, fail-before unit
  test; (2) EXT-T1 campaign with five failing drama feeds seeded while Source status is open (≥ 20 runs,
  api33-lowram); (3) NBUG-5 note (code read + competitors) and NBUG-6 research note (six answers).

### Previous resume note (eighth cycle, 6)


- **Orders:** eighth-cycle master continuation prompt. Order: 4.1 (DL-11 p2 → X2-1 → N6-2 → ST-17 → BUG-4 → BUG-1 → BUG-2 → BUG-3 emulator) → 4.2 owner file → 4.3 NBUG register → 4.4 proposals only.
- **Current group:** 4.1 tail + 4.4 proposals. Verified (emulator): N6-2, BUG-1, BUG-2, BUG-4 (phones + TV), TV-3. DL-11 p2 option F: medians met, one download 1.1004× (not claimed; next sample with the next download change). X2-1 emulator part done (no gain claimed). ST-17 campaign 44145301927: 20/20, 0 spins; stays open. 4.2 done; 4.3 done or Blocked: owner; Group 13 closed for agent work (6bb9dcb).
- **4.4:** proposals F-01…F-12 all written (docs/proposals/FEATURE-F-*.md; ADR-017 and ADR-018 proposed). Gate **not passed**: DL-11 budget not claimed and EXT-T1 pending. Nothing built.
- **Runs in progress:** 44162062169 (EXT-T1: ExtensionsDeviceTest repeat 10, api33-lowram).
- **Next three actions:** (1) read 44162062169 (artifact logcat: E11 cleanup and any failure); (2) second EXT-T1 campaign of 10 for ≥ 20 runs; if none reproduce, OWNER-MANUAL-TESTS entry Blocked: owner; (3) DL-11 next measurement with the next download change (case d every time), then the 4.4 gate check.
- Index: docs/CHECKLIST-FEATURES-BUGS.md. Report: docs/reports/2026-11-18-eighth-handover-report.md.

### Previous resume note

- **Orders in force:** the seventh master resume handover. Order: finish G8 → Group 11 → G9–G11 leftovers →
  re-audit G1–4 → final sweep → gated feature phase. No PR/merge/release without the owner.
- **Sandbox:** no JDK/Android SDK; every compile and unit test goes to CI. Secrets live only in the git-ignored
  `.env` and `secrets/` of the agent's checkout.
- **Earlier (Group 12 start):** G9 and G10 closed for agent work 2026-11-17 (G9: qa_gpu_tier → OMT-16;
  G10: VPN → OMT-18). Close-out findings fixed and verified on device (emulator): ST-21 (message over the floating
  window; 43967676359 → 43974089878), ER-19 (form errors behind their dialog: home server, Kitsu; 43985787392 →
  43987451529); ER-12 Retry verified (43987451529); X15-2 fail-before in CI (43976334697).
- **Owner bugs:**
  - BUG-5 TV UI: TV-5/TV-2/TV-4 (focus+answer) verified on tv-api34 (43935810414) and tv-api24 (43949571344,
    43957029647); TV-3 PASS tv-api24. Open: TV-4 cross-source search (G7 proposal 3, owner call).
  - BUG-6 verified on device (emulator, both phones).
  - BUG-7: 7a, 7b, 7c verified on device (emulator); 7d not reproduced on emulator → OMT-16; gallery OMT-17.
- **Done since:** campaign 43988958313 read (20/20, FATAL 0, app ANR 0, ST-17 0 spins; stalls same stacks as
  43705867413, no proven change; ST-7 stays open). G12-DF status reconciled to verified (43857809658).
  New finding **X2-4** (first recorded as X2-2, an ID already in use): the macrobenchmark journeys never measured the app. First the notification permission
  prompt took the swipes (trace of 43993294720), then the welcome guide stayed up because its button reads
  "Skip for now" and the journeys matched "Skip" (screenshots of 43999288738). The shipped baseline profile
  (fef01c6) therefore covers startup and the welcome screen only. Fixes pushed (grantPermissions, passWelcome).
  Frame numbers of 43993294720 / 43996103813 / 43999288738 are void.
- **Done since (2):** FrameTimingBenchmark 44002112195 is the first valid frame run (Home on screen, 139–171
  frames/iteration); no profile gain on the software-rendered emulators (ST-7; the real-phone check stays OMT-12).
  X7-5 MASVS re-review: no drift. LB-3/LB-8 verified on device across a real process death (44002497794).
  LB-7 rotation: fail-before 44004098352 → pass 44005396278. LB-2 finding: MangaLibrary ran filesDir+mkdirs on
  main on every page turn; fail-before 44007087901 (40 violations, both phones), fix e059aed.
- **Done since (3):** LB-2 verified (44008161115). LB-7 tabs: fail-before 44009339786 → pass 44010510836.
  MU-4 queue kept while playing: fail-before 44013418634 → pass 44014503279. X2-4 profile regenerated
  (44005414732, 4989344); CI 44010598355 green. Startup with the new profile 44013773673 (TTID median,
  profile vs no-compile): api36 2116 vs 2957 ms, api33-lowram 1756 vs 2465 ms. LT-3 verified (44015384846).
  LT-4 (remote list held whole in memory): fix 8b747ca; hardened test c318e9a (12 s settle, lower of two
  peaks) fail-before 44018872516 (lowram add 132 MB > 96) → pass 44018869252 (lowram 42/65, api36 32/27 MB).
- **Done since (4):** LT-4 verified (CI 44020023897). LT-2: the 4cee17d fix never worked (case a failed on head
  44020227401 and fail-before 44020231250); fixes bd18c0b + 1060ea7 → pass 44021837402, 44023164202, CI
  44023167122. MG-1 library read off main: fail-before 44025493021 (5 reads) → 44025490015 (0). G7-L1 (QuickIPTV
  failure shown as a friendly error): CI 44026316670, fail-before CI 44028308675. LT-1 44026779856. G7 done.
- **Seventh handover, done:** 44043535616 read: G8 "Downloads fixes" verified (emulator; YouTube part unit only);
  BUG-3 case d 6/6 PASS (stays open for the owner's phone). Docs 56f0526.
- **Current group / step:** Group 11 Integrations (`docs/groups/GROUP-11-INTEGRATIONS.md`). Line-by-line read done for
  every G11 file (app + server/); action–reaction table and competitor notes (Mihon, checked in source) filled.
  - IG-1 verified (emulator): 44049036117 (fail-before) → 44049031398.
  - IG-2…IG-5 after-fix on api33-lowram: 44050521996 (14a367f) PASS every case (AnimeTrackers a/b/c, ScrobbleFlush, Stills a/b).
  - IG-6 (CloudAlerts cached a failed AniList lookup as "not anime") 9ecd280; IG-7 (subtitle hash match lost when the
    title search failed) dc00672; IG-8 (failed AniList/MAL re-sign-in said "Signed in") 0997e45. Tests:
    `CloudAlertsLookupDeviceTest`, `OnlineSubtitlesFallbackDeviceTest`, `AnimeSignInMessageDeviceTest`; their
    fail-before copies are on `scratch/ig2-fail-before`.
- **Done since (seventh handover, 2):** IG-2…IG-5 verified (emulator): fail-before 44050574003 → 44050521996
  (api33-lowram) + 44051808801 (api36, all six G11 tests PASS). IG-6…IG-8 fail-before 44052308373 (api33-lowram):
  CLOUD cached=0, SUBS fallback lost the hash hit, SIGNIN rejected said "Signed in to AniList as OldUser"; the
  control cases (accepted, titleOnly) pass on old code as expected. G8: D6 test `YtDownloadPipelineDeviceTest`
  (abf49da); **DL-12** (Downloads page did disk work on the main thread: list load, every tap's save + fsync,
  folder delete per episode) fixed a501d8e with `DownloadsScreenDeviceTest`; fail-before branch
  `scratch/d8-fail-before` (7182011, old code + no-op flushForTests seam). Skills cloned to
  `/home/user/workspace/agent-skills` (sandbox only; architecture-designer SKILL.md read first).
- **Done since (seventh handover, 3):** G9 SE-1 device test `SettingsResetDeviceTest` (6e96b90). G10 A5 finding
  **ER-20** (a failed browser check was never remembered, so every request reopened a hidden WebView) fixed 065f455
  with unit `BrowserCheckTest#failedCheckWaitsBeforeTheNextWebView` + device `BrowserCheckCooldownDeviceTest`;
  fail-before branch `scratch/a5-fail-before` (dfb48ef). Device run 44053272178 (a501d8e) did not compile
  (`import androidx.compose.ui.test.and` in my new test); fixed ee1808c, so a501d8e…065f455 had an androidTest
  that did not build (main code built). 44053590474 cancelled (same file, would fail the same way).
  DONE-LEDGER layout repaired: 28 rows had shifted cells (ST-21/SE-6 status column showed "—"/old text); no status changed.
- **Done since (seventh handover, 4):** 44054360131 (api33-lowram) 8/9 PASS → IG-6…IG-8 verified (emulator, both
  phones); SE-1 device reset walk PASS; D3/D5/D6 device tests PASS; ER-20 after-fix PASS. DL-12 fail-before
  44054365230 FAILED as expected (6 main-thread disk calls); after-fix a_open 0 calls, then the test timed out opening
  a folder (rows below the fold on the small screen; test fault) → fixed e71a041. G2 E10 re-audit: new
  `UniversalAddEcosystemsDeviceTest` (Aniyomi link, Mihon folder, Stremio search+stream, JS list+install, empty link).
- **Current group / step:** re-audit G1–4 (G2 E10 device test written); G8 DL-12 and G10 ER-20 close-out.
- **Also done:** ST-22 found while re-auditing G2 E12 (clearing the page cache deleted up to 128 MB on the main
  thread from the adult switch and ~15 other UI actions) → fixed daab2ab (`DataCache.invalidateAll`), unit test +
  `AdultSearchFilterDeviceTest`, fail-before branch `scratch/st22-fail-before`. G1 H11 gap (G6 never took it) →
  `MiniPlayerDeviceTest` cbe2e47. 44056147165 (a5 fail-before) did not compile (old bad import) → fixed on the branch.
- **Done since (seventh handover, 5):** ST-17 re-audit note (upstream searched, no matching fix; stays open, no
  symptom fix). P16: `Anime4kShaderDeviceTest` (08991d6) duplicated `Anime4kGlDeviceTest` (G12-A4K, 43849088080)
  → removed 85c50c8; GROUP-3 P16 rows were stale and are corrected. Final-sweep coverage check (script over
  INVENTORY): 32 rows said "open"; M3/M4/M5/M7, T3, X1–X10, X13, X14 reconciled with the ledger (no status
  invented). **Real gaps:** M2, M9, N1–N9 (no audit, no dedicated test); M1, M6, M8 code read only → GROUP-13.
- **Done since (seventh handover, 6):** 44056138637: LocalPlayer#j and Y3 PASS (emulator api33-lowram); DownloadsScreen
  and UniversalAdd hit two test faults (fixed 3e6b7de). ER-20 fail-before 44057033821 FAILED as expected (60.6 s) →
  ER-20 verified (emulator, api33-lowram). **G13 started** (`docs/groups/GROUP-13-NEW-GROUPS.md`): N6 app lock
  N6-1 (no screen lock → locked for good) and N6-3 (Android 9 with no fingerprint → locked for good) fixed 734e9f6
  with `AppLockDeviceTest`; fail-before branch `scratch/n6-fail-before`; N6-2 suspected (API 29, no CI image).
- **Done since (seventh handover, 7):** N3 self-update N3-1 (folder work, DownloadManager enqueue and queries on the
  main thread) fixed 13e8136 with `UpdateMainThreadDeviceTest`; fail-before `scratch/n3-fail-before`.
  **44058588776 (head 3e6b7de) did not build**: androidTest compile errors in AppLockDeviceTest:44 and
  UniversalAddEcosystemsDeviceTest:115 (my 3e6b7de); no test ran, nothing verified. Fixed 4eb6245; the same fix is on
  `scratch/n6-fail-before` (6bcc0bb) and `scratch/n3-fail-before` (c722c99). G13 N4/N5 finding **N4-1 / N5-1**: the
  home-server, Stremio, JS-source and Tachiyomi-repo lists were read as "empty" on any damaged file and written in
  place, so the next save wiped them; Stremio also deleted every add-on that was offline at start; JS/Tachiyomi
  changes before the start-up read saved lists without the old entries. Fixed 595305f (SafeJsonFile everywhere,
  Stremio keeps its link list, changes wait for the saved lists, removes off main). Unit test
  `SourceStoresSafetyTest` (8 cases); fail-before branch `scratch/n45-fail-before`.
- **Done since (seventh handover, 8):** N4/N5 audit written (GROUP-13). N2 web sites read: finding **N2-1** (site
  sign-out left Domain=/Secure cookies; suspected from code) fixed 83d7818 + `SiteSignOutDeviceTest`, fail-before
  `scratch/n2-fail-before`. **ST-22 evidence void:** fail-before 44058593326 PASSED (0 calls on old code) because
  `AdultSearchFilterDeviceTest` restored StrictMode in the same main message (AOSP StrictMode reads the listener
  after the message); helper fixed 6b2bca6 on head and the scratch branch. The other six StrictMode tests checked:
  they restore in a separate message after idle (sound).
- **Done since (seventh handover, 9):** **N7-1** fixed ac13d92 (inbox saves were on the main thread and
  could run at the same time; now one lock and a background writer) + `InboxMainThreadDeviceTest`, fail-before
  `scratch/n7-fail-before`. **N8-1** fixed 5be755e (links did nothing with no browser, common on TV; now a message)
  + unit `LinksOpenTest`. N9 read: no finding. GROUP-13 N2/N7/N8/N9 sections and INVENTORY rows written.
  One unit fail-before branch `scratch/unit-fail-before` (old N4/N5 stores, old SiteLogins, old Links; seams only).
- **Done since (seventh handover, 10):** run 44059862262 read (4 pass, UniversalAdd → **N4-2** app bug, MiniPlayer →
  MINI-T1 test fault). ST-22 fail-before 44060561160 FAILED as expected (20 main-thread disk calls). **N4-2** fixed
  7c69091 (CloudStream fixUrl rewrote bridge ids: Stremio, Plex/Jellyfin episodes, file servers, Aniyomi, JS).
  N1 Watch Together read: **N1-1/N1-2** fixed 0040d98. MINI-T1 fixed c09cdc8. CI 44061310566 cancelled (stale).
  Fail-before branches: `scratch/unit-fail-before` (f7783b4, unit: N4/N5, N4-2 history, N2, N8, N1) and
  `scratch/device-fail-before` (N2, N3, N6, N7, N4-2 Stremio; device).
- **Done since (seventh handover, 11):** 44062811776 (head c09cdc8, api33-lowram, emulator): AdultSearchFilter (fixed
  helper), SiteSignOut, InboxMainThread, UniversalAddEcosystems PASS → ST-22 verified (fail-before 44060561160), N2-1,
  N7-1 after-fix PASS, N4-2 after-fix PASS (fail-before 44059862262). MiniPlayer failed again: second test fault
  (swipe at 60 % of the bar sat on the Previous button) → `mini_player_swipe_area` tag + swipe inside it (aa3489e).
  Music rows (GROUP-13): **M9-1** Mix search matched device songs on the main thread per keystroke → fixed 0dce4ae +
  unit `MusicMixSearchTest`; **M2-1** a YouTube account change kept the previous account's YouTube Music Home (API
  cache + on-screen store) → fixed cf4d650 + f3d1d38 + unit `YtAccountMusicCacheTest`; **M1-1** two or three full
  library scans in parallel at every cold start → fixed 841b085 + unit `LibraryScanOnceTest`. M6, M8: read, no finding.
  `scratch/unit-fail-before` now also holds old M9/M2/M1 code (seams only).
- **Done since (seventh handover, 12):** CI 44062718477 and 44067269319 green (unit: WatchTogetherHost, SourceStoresSafety,
  BridgeIds, LinksOpen, SiteAccess, ResumePoints, MusicMixSearch, YtAccountMusicCache 3/3, LibraryScanOnce 2/2).
  Fail-before 44062815556 FAILED 5/5 as expected → N2-1, N7-1, N6-1, N3-1, N4-2 verified (emulator api33-lowram).
  MiniPlayer PASS 44067244782 (MINI-T1 closed, emulator). AppLock api28: case a PASS, case b found **N6-4** (right PIN,
  app stayed locked; 44069102667) → fixed 3f3fbd8. n6 fail-before 44069107057 FAILED a+b as expected.
- **Done since (seventh handover, 13):** N6-3 + N6-4 verified (emulator api28, 44071192093 OK 2 tests). Unit fail-before
  CI 44070875412 FAILED exactly on the expected tests → N4-1/N5-1, N1-1/N1-2, N8-1, N4-2, M9-1, M2-1, M1-1 verified (CI).
- **Done since (seventh handover, 14):** api36 44073375083: 10/11 new-test classes PASS (ER-20, DL-12, MINI-T1, N2-1, N3-1,
  N6-1, N7-1, ST-22, N4-2 Stremio, LB rows now both phones). LocalPlayer#g (PL-18 one Back) failed once on api36.
- **LP-T1:** 44075789457 g ×3 PASS alone → order-dependent; cause: AppLock keys left the display out of touch mode (test fault). Fix d2f80d3.
- **Done since (seventh handover, 15):** milestone 44079680572: api36 23/24 (Yumix → YX-T1, a real network timeout counted
  as the old race; test fixed 02334a4); api33-lowram 14/14 that ran PASS, then the emulator went offline (FS-INFRA-1, the
  last 10 classes never ran).
- **Done since (seventh handover, 16):** 44089151495 api33-lowram 10/10 PASS → milestone suite 24/24 on api33-lowram
  (with 44079680572). api36 23/24 + Yumix: 44089166780 seeAll inconclusive (onshort.net timeouts), assumption added d7f2b2a.
  Void: 44088190688/44088205006 did not build (02334a4 import; fixed b345cab).
- **Runs in progress:** 44091851873 (api36, Yumix on d7f2b2a).
- **Next three actions:**
  1. Read 44091851873; then H8 `-f bench=restore` (one heavy run at a time).
  2. Final coverage check over INVENTORY.
  3. Final report, handover prompt and the gated feature list for the owner (Part 8 stays gated).
- **Waiting on the owner:** X3-TL, X13-6 and TV-4 cross-source search.

Branch `r15-stability`. "Device" means an instrumented test on both CI emulators: a low-RAM
API 33 Pixel 3a (2 GB RAM, 192 MB heap, low-end path) and an API 36 Pixel 6. The run IDs are
in `docs/evidence/round3/device-runs.md`. "Unit" means a JVM or Robolectric test only. Items
marked **not device-verified** are not claimed as fixed on a phone.

Earlier "done" reports (STATUS-v1.8.1) were Robolectric-only. Each item below was re-checked.

## Phase 1: OOM and ANR

| Item | Status | Evidence |
|---|---|---|
| No regex compile on main | Fixed | `RegexGuardTest` (none inside functions; all 91 holder classes warmed on `yu-startup`). Device: `StabilityStressDeviceTest` samples main 42,000–44,500 times per run and fails on app-origin `Pattern.compile`. Clean since run 43645273428. The only left-over is the library cost of `androidx.navigation` `NavDeepLink` route patterns |
| No expensive work in pager prefetch | Fixed | `Kana.forTitle` has no regex and is LRU-cached and remembered; item tags are computed once. Device: pager and fling stress |
| No OOM under stress on low-RAM Android 13 | Fixed | Device: api33-lowram (192 MB heap) passes stress, playback, switching and downloads with no OutOfMemory or FATAL in logcat |
| No ANR on focus or pager | Fixed | Device: max main stall 461–1,542 ms across runs; the failure threshold is 4 s. The endless recomposition after focus moves came from the tab pager taking a focused card's bring-into-view as nested scroll (`docs/STABILITY-R15.md`). Fixed in MTPlayerApp. The stress test now fails on any "Compose never idle" and has passed with 0 spins since 43653623319. `dpadFocusSettles` and `swipeThenDpadSettles`: 0 frames per 2 s after D-pad moves |

## Phase 2: player

| Item | Status | Evidence |
|---|---|---|
| Quality switch uses the downloader's working stream data | Fixed in code | `YouTubeStreams.videoRequest` on IO, codec-aware `pickVideo` (`YouTubePickVideoTest`). Device `c_youTubeQualityListOpensEveryHeight` **skips on CI**: Google answers "Sign in to confirm you're not a bot" for data-centre IPs. **Not device-verified for YouTube** |
| Same full quality list as the downloader (Video, YouTube, Yumix) | Fixed in code | `QualityParityTest` (unit). Device: DASH rendition ladder switching (`b_everyDashRenditionHasPictureAndSound`) |
| Engine-generated levels for single-source streams | Fixed | Device `a_engineLevelsSwitchWithoutTimeout` |
| No audio with multiple sources | Fixed | Device `d_everyAudioRenditionIsAudible` (AAC, AC3), `e_separateAudioIsMerged`, `StarvationSafeLoadControl` |
| A manual pick of a dead source rotates again | Fixed | `RecoveryStateTest` (unit). Device `d_manualPickOfADeadSourceRotatesAgain`, run 43652020450, both devices: the dead pick was tried, then playback rotated to the working MP4, and the dead link was marked failed |
| Drama banner plays; dramas appear in the feed | Fixed in code | `DramaResilienceTest` (16, unit). Live network check b21 24/24 (`docs/evidence/round3/drama-live-b21.txt`); the b14 run was invalid (onshort.net outage). Device: `DramaPlaybackDeviceTest` PASS on API 33 and 36 (runs 43657727593, 43657729675): feed rows DramaBox/ReelShort/NetShort/DramaWave = 18 each; "Daddy, Save Me" reached first frame at 1080x1920. The first device attempt (43656137755) failed only because the test did not release the first-run gate (`OnboardingGate`), which holds every source until the welcome guide is skipped |
| 00:00 instead of "--:--" | Fixed (this round; before, the row showed a pulsing pill or nothing) | `DurationLabelTest` (Robolectric). Device `c_uiFlowPlays`, run 43652020450: labels while opening were `[00:00, 1:31:44]` on both devices |

## Phase 3: live scroll never replaced by stale data

| Item | Status | Evidence |
|---|---|---|
| Home, YouTube, online music, Yumix and Live TV feeds | Fixed in code | `LivePagedTest` (append-only, generation guard, a failed page keeps the cursor). Device: fling stress on each feed with no stall or recovery. Yumix now has device assertions (R16 G4): `FeedPositionDeviceTest#yumix` and `YumixDeviceTest` (offline refresh keeps every held title; See all keeps paging), milestone 43733844682 pass on both. Other feeds: the guard is the store design in ADR-008, no device assertion |

## Phase 4: downloads and the floating player

| Item | Status | Evidence |
|---|---|---|
| "View downloads" moves the player to PiP and opens Downloads; a new video expands it | Fixed | Device `SurfaceModeDeviceTest`, `PlayerSurfaceRulesTest` |

## Phase 5: extensions, forced downloads, crash logs

| Item | Status | Evidence |
|---|---|---|
| Pin favourite extensions in "show videos from" | Fixed | `PinnedExtensionsTest` (unit) |
| Forced downloading (IDM-style) for sources that block downloads | Fixed | Device `ForceDownloadDeviceTest`: DASH into one MP4 (3,944,838 bytes, video/avc and audio/mp4a); segmented DASH (36,632,975 bytes, 634 s) |
| Crash log exported as a file automatically | Fixed | Device `crashReportIsExportedAsAFileRightAway`: 192,627 bytes (api33) and 192,638 bytes (api36) |

## Phase 6: proposal

`docs/proposals/OPEN-SOURCE-COMPARISON.md` (cs-repos and hikari). Nothing implemented, as asked.

## Phase 7: audit, plan and design

- System design with diagrams: `docs/SYSTEM-DESIGN.md`. Architecture, threading, memory, feeds,
  the playback state machine, failure modes and ADR-001 to ADR-011.
- Root causes found on device: `docs/STABILITY-R15.md`.
- Roadmap: `docs/ROADMAP.md` §R15.
- Skills used: ui-ux-pro-max (carousel rule `auto-rotation-controls` and the time-label states),
  kotlin-agent-skills, rea (reverse-engineering the spin and the crash-export size), and
  architecture-designer from jeffallan/claude-skills for the system design.

## R16 updates (2026-11-15)

- Done ledger: `docs/DONE-LEDGER.md` (every commit on every branch, re-checked claims, status per item).
- Drama start, play to first frame, 5 runs per device (run 43676961108, db40142, 10/10 pass):
  median 3.8 s api33-lowram (2.8–7.1 s) and 3.5 s api36 (3.2–4.7 s). Before (43661715830):
  9.7 s and 34.0 s. Cause: taps waited behind health probes in one FIFO gate; fixed with lanes.
- Quality list on device: `QUALITY_RESULT PASS heights=[1080,720,480,288,184]` (43668984393, both).
- Pinned extensions on device: `PIN_RESULT PASS`, order kept after recreate (43671202859, both).
- Main-thread pauses: **not yet a small fraction (ST-7 open).** 40-run campaign 43671209244: max
  per run 395–1,391 ms. Later campaigns after 50e734d, dfe9234, d6ff111, 9bbd223, a5b8f60, 18dda67:
  steady-state app stalls ≥700 ms per run 0.2–0.5 (api33-lowram) and 0.1–0.4 (api36); the latest
  repeat 43705867413 (97bb255, 20/20 pass) gives 0.4 / 0.4 steady, 0.8 / 0.3 cold. No measurable
  drop; next is a baseline profile.
- Feed position (Home, Yumix): fixed and verified on device (FEED-1): 43683520404 and 43687398312;
  the earlier failures were a test error (duplicate titles).
- Group audits (Step 3): `docs/groups/GROUP-1-HOME-NAV.md`, `GROUP-2-EXTENSIONS.md`, `GROUP-3-PLAYER.md`, `GROUP-4-YUMIX.md`. App bugs
  fixed and verified on device in R16 include stepped Back after a swipe (89f7553), TV rail focus
  (a53effb), Back in extension selection (bfde23d), readable network errors (9be5afb), starter
  extension retry (97bb255), Cast start (b5d2d5a), PiP refusal message (5bfa07c). Device runs are
  listed in `docs/evidence/round3/device-runs.md`.

- R16 group status: G1–G4 complete (agent work; owner tests pending). G5 Library and G6 Music:
  fixes in, `LibraryDeviceTest` 43738288853 pass on both emulators; full suite at next milestone.
  G7 in progress.

## Still open (not claimed)

- YouTube playback and quality switching on a device on a home network (CI IPs are bot-checked).
- A real Xiaomi with Android 13 and a TV device; the emulators cover API 33 low-RAM and API 36.
