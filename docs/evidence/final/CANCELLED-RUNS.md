# Cancelled runs (final test, 1.9.0 release candidate)

Every run cancelled by the agent, with the reason (handover §8).

| Run | Workflow | Head | Cancelled (UTC) | Reason |
|---|---|---|---|---|
| 44669586493 | CI | d91f052 | 2026-11-20 04:09 | superseded: code head moved to df4ba9d / 4d125b3 (FINAL-42, FINAL-43); CI 44672545816 runs on 4d125b3 |
| 44674855781 | CI | 9ecf1bf | 2026-11-20 | wrong diagnosis: 9ecf1bf changed AppMessages' Handler; the JUnit XML of 44672545816 showed the real cause (the test compared thread names, which coroutine debug suffixes with " @coroutine#N"); 9ecf1bf reverted |
| 44674984195 | CI | 753b9dc | 2026-11-20 04:33 | replaced by the same CI with review_apk=true on the same code (one run gives unit, lint, gitleaks and the review APK) |
| 44691180981 | CI | 671285b | 2026-11-20 06:55 | superseded: owner asked for the whole Settings to be reorganised (Profiles, Downloads, Video, Music pages), code changes again |
| 44703382685 | CI | 574fdc8 | 2026-11-20 08:33 | superseded: owner asked for the fix to ship as 2.0.0; version bump, WhatsNew 2.0.x and CHANGELOG added on top, CI re-run on the new head |
