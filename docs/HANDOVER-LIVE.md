# Live handover (updated with every push; read this first after AGENT-START-HERE.md)

Owner rule 2026-11-21 16:30 +06: agents can run out of limit at any time, so this file is rewritten with every push:
what was just done, what runs are pending, and the exact next step. Every bug and feature on the lists must be done
AND verified. Mandatory focus now: feed loading, extension loading, load speed, search speed.

## Last updated
2026-11-21 18:25 +06, branch `r15-stability` head after commit "docs: fail-befores SPEED-3/4 and B31-SEEK2 verified".

## Owner rule 2026-11-21 17:15 +06
No hurry, nothing unverified. Before each fix: research how competitors handle the case (cite URLs in the ledger),
then build Yumeiro's own better version. All bugs mandatory; features one by one. 2.1.0 must be the most stable build.

## Done today (2026-11-21), newest first
- SPEED-T: device test `SpeedDeviceTest` (extension relaunch load, Home first row, search first result; tag SPEED).
- B31-SEEK2: request_queue_time 1, seek target alone at 0 ms, read-ahead from 500 ms (research: libtorrent_flutter, VortX).
- SPEED-3/4: extensions load fastest-first; search starts with loaded extensions and late ones join (no restart).
- SPEED-1/2: cached homes and searches skip the permit queue; Home and search fastest-first (ledger row SPEED-1 / SPEED-2).
- E4 part 1 `b640f68d`: ExtensionSpeed (fastest-first Yumix home order, quarantine 3 fails / 6 h), flag `e4_speed_order`.
- B-31 seek fix `e451b783`: abandoned window's deadlines dropped on seek (finding B31-SEEK from device 44879268223).
- E2 `18917728`: `.github/scripts/curate_live.py` curated probed Live TV (ADR-055), switch repo var `YUMEIRO_CURATED_LIVE`.
- E3 `23fad75d`: ABI splits behind `-Pyumeiro.abiSplits=true` / repo var `YUMEIRO_ABI_SPLITS`, one versionCode (ADR-054).
- B-31 privacy notice `787a7901`: engine plays only after the Settings notice is accepted.
- F-22 `706dee41`, F-02 round trip `e0c4e7b8`, B-31 speed `2b286d6c`, F-21 `60e8d6df`, B-32 earlier.

## Verified (run IDs)
- Fail-before verified: E4 44880750191, SPEED-1/2 44881206020, SPEED-3/4 44881403329, B31-SEEK2 44881530280
  (branches deleted).
- CI 44881247581 (@4a58cb22): failure, finding SPEED-T1 (test used an ERROR-level deprecated constructor); fixed.
- Device 44880554878 (@e451b783, phones): notice gate Pass, first 64 KB Pass 8033 / 3192 ms, seek Fail 7566 / 10744 ms
  (B31-SEEK2). TV 44880575223 (api24): first 64 KB 5238 ms Pass, seek 12732 ms Fail (B31-SEEK2).
- Pass-after CI 44879288541 (@2b286d6c): B-32 EmptyHomeTest, F-21 ExtensionLabelsTest, B-31 TorrentBlocksTest.
- Fail-before verified: E2 44880384185, B-31 seek 44880511385 (branches deleted).
- Fail-before verified: F-21 44878962117, B-31 speed 44879233183, F-22 44879571787, E3 44879928117 (branches deleted).
- Device Pass: F-02 round trip 44879417411 (api36). B-31 first 64 KB Pass 44879268223 (2632 / 5783 ms), seek Fail B31-SEEK.

## Pending runs (check each, record in the ledger, then delete the ab/ branch)
- Device 44882300017 (phones) + 44882301935 (TV api24) `dev.asfakul.yumeiro.BuiltinTorrentDeviceTest` = B31-SEEK2 device
  check (44881581791 cancelled / 44881606204 no test ran: wrong class name, see CANCELLED-RUNS.md).
- Pass-after CI 44882251682 (r15-stability, abi_splits=true): F-22, B-31 notice, E2, E3, B-31 seek, B31-SEEK2, E4,
  SPEED-1/2, SPEED-3/4. Do not dispatch another r15-stability CI while it runs.
- SpeedDeviceTest baseline (`dev.asfakul.yumeiro.SpeedDeviceTest`): dispatch phones=both when a device slot frees (max two device runs).
- B31-SEEK2 pass-after: next r15-stability CI. Device re-run BuiltinTorrentDeviceTest phones=both + TV api24 on head.

## Next steps, in order
0. SpeedDeviceTest baseline -> budgets in GOAL-TOP-OF-CLASS.md -> fixes (parallel extension load like CloudStream amap / Mihon
   async, but keep provider ownership exact; drop the double disk read in OnlineRepository.searchOne on a miss).
1. Speed work (owner mandatory): feed loading, extension loading (start-up of the extension engine), search
   (ordering by ExtensionSpeed, per-extension timeout, results streamed as they arrive). Measure first, fix, test.
2. E4 part 2: visible-rows scheduler, low-RAM / heat throttle, Explore guide delay.
3. Device checks for every "Built, not tested" row; UNVERIFIED-QUEUE / INVENTORY; G-rows in GOAL-TOP-OF-CLASS.md.
4. E3 website ABI detector: Blocked (the website is not in this repo). E1 master extension: large, not started.
5. Release only on the owner's explicit go.
