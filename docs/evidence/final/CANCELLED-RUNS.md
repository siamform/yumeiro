# Cancelled runs (final test, 1.9.0 release candidate)

Every run cancelled by the agent, with the reason (handover §8).

| Run | Workflow | Head | Cancelled (UTC) | Reason |
|---|---|---|---|---|
| 44669586493 | CI | d91f052 | 2026-11-20 04:09 | superseded: code head moved to df4ba9d / 4d125b3 (FINAL-42, FINAL-43); CI 44672545816 runs on 4d125b3 |
| 44674855781 | CI | 9ecf1bf | 2026-11-20 | wrong diagnosis: 9ecf1bf changed AppMessages' Handler; the JUnit XML of 44672545816 showed the real cause (the test compared thread names, which coroutine debug suffixes with " @coroutine#N"); 9ecf1bf reverted |
| 44674984195 | CI | 753b9dc | 2026-11-20 04:33 | replaced by the same CI with review_apk=true on the same code (one run gives unit, lint, gitleaks and the review APK) |
| 44691180981 | CI | 671285b | 2026-11-20 06:55 | superseded: owner asked for the whole Settings to be reorganised (Profiles, Downloads, Video, Music pages), code changes again |
| 44703382685 | CI | 574fdc8 | 2026-11-20 08:33 | superseded: owner asked for the fix to ship as 2.0.0; version bump, WhatsNew 2.0.x and CHANGELOG added on top, CI re-run on the new head |
| 44710581956 | device-check (TV upgrade v1.8.1→2.0.0) | ed9fbe88 | 2026-11-20 09:26 | owner asked to stop re-runs and release the crash fix now; TV upgrade re-check deferred |
| 44710594011 | device-check (phone upgrade v1.9.0→2.0.0) | ed9fbe88 | 2026-11-20 09:26 | owner asked to stop re-runs and release the crash fix now; deferred |
| 44771338763 | CI | 23558a22 | 2026-11-20 16:20 | superseded: FREEZE-2 committed on top; one CI run on the new head (44771583525) covers FREEZE-1, PROF-1, T-1 and FREEZE-2 |
| 44793632261 | CI (review APK job only; build, unit tests and lint passed) | c561e5a2 | 2026-11-20 19:00 | cancelled by CI concurrency when 44797801824 started on the newer head 6fd31251, which passed every job |
| 44881581791 | device-check (phones, BuiltinTorrentDeviceTest) | 5c031c10 | 2026-11-21 13:52 +06 | cancelled by the agent: dispatched with the wrong class name `dev.asfakul.yumeiro.stream.torrent.BuiltinTorrentDeviceTest` (real: `dev.asfakul.yumeiro.BuiltinTorrentDeviceTest`); its TV twin 44881606204 failed with ClassNotFoundException, no test ran (not a result). Re-run 44882300017 (phones) / 44882301935 (TV) |
| 44885911171 | CI (ab/b31seek4-before, fail-before) | ab/b31seek4-before | 2026-11-21 22:50 +06 | cancelled by the agent: the B31-SEEK4 test changed with B31-SEEK5 (lowram run 44884969625); one new fail-before branch covers both |
| 44886144958 | CI (ab/b31seek45-before) | ab/b31seek45-before | 2026-11-21 22:55 +06 | cancelled by the agent: branch was cut from the head that already had the B31-SEEK4 fix; new branch ab/b31seek45b-before cut from 2206f81d~1 (before both fixes) |
| 44886605109 | CI (r15-stability pass-after, abi_splits) | 59ac5467 | 2026-11-22 00:00 +06 | cancelled by the agent: PauseWhilePlayingTest expected 4 running jobs on a 3-permit gate (finding FE-P-T, seen on 44885736016), so this run could not be green; re-dispatched on the head with the test fix |
| 44901007792 | CI (r15-stability, abi_splits) | 63a20756 | 2026-11-21 18:03 +06 | cancelled by the agent: the head's androidTest did not compile (DEV-CHK-21-C2, duplicate `rows` in Nf1EpisodeStatusDeviceTest); re-dispatched on the fix |
| 44904413951 | device-check (phones, ExternalPlayerDeviceTest) | b8381ce0 | 2026-11-21 18:32 +06 | cancelled by the agent: dispatched before the F07-T4 test fix was pushed (would test the old head); re-dispatched after the push |
| 44904444994 | device-check (phones, ExternalPlayerDeviceTest) | a7bbd80e | 2026-11-21 18:33 +06 | cancelled by GitHub concurrency while pending: same group as 44904447255 dispatched right after (the cancelled 44904413951 still held the group); re-dispatched on lane l1 |
| 44926459256 | CI (ab/ol4-before fail-before) | 3f4a6a4e | 2026-11-21 22:47 +06 | cancelled by the agent: the fail-before stub added Type.LINK without its tipsFor branch, so main would not compile (no valid fail-before); re-dispatched as 44926496585 with the stub branch |
