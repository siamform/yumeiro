# Live handover (updated with every push; read this first after AGENT-START-HERE.md)

Owner rule 2026-11-21 16:30 +06: agents can run out of limit at any time, so this file is rewritten with every push:
what was just done, what runs are pending, and the exact next step. Every bug and feature on the lists must be done
AND verified. Mandatory focus now: feed loading, extension loading, load speed, search speed.

## Last updated
2026-11-21 16:50 +06, branch `r15-stability` head after commit "docs: live handover" (see `git log -1`).

## Done today (2026-11-21), newest first
- SPEED-1/2: cached homes and searches skip the permit queue; Home and search fastest-first (ledger row SPEED-1 / SPEED-2).
- E4 part 1 `b640f68d`: ExtensionSpeed (fastest-first Yumix home order, quarantine 3 fails / 6 h), flag `e4_speed_order`.
- B-31 seek fix `e451b783`: abandoned window's deadlines dropped on seek (finding B31-SEEK from device 44879268223).
- E2 `18917728`: `.github/scripts/curate_live.py` curated probed Live TV (ADR-055), switch repo var `YUMEIRO_CURATED_LIVE`.
- E3 `23fad75d`: ABI splits behind `-Pyumeiro.abiSplits=true` / repo var `YUMEIRO_ABI_SPLITS`, one versionCode (ADR-054).
- B-31 privacy notice `787a7901`: engine plays only after the Settings notice is accepted.
- F-22 `706dee41`, F-02 round trip `e0c4e7b8`, B-31 speed `2b286d6c`, F-21 `60e8d6df`, B-32 earlier.

## Verified (run IDs)
- Pass-after CI 44879288541 (@2b286d6c): B-32 EmptyHomeTest, F-21 ExtensionLabelsTest, B-31 TorrentBlocksTest.
- Fail-before verified: E2 44880384185, B-31 seek 44880511385 (branches deleted).
- Fail-before verified: F-21 44878962117, B-31 speed 44879233183, F-22 44879571787, E3 44879928117 (branches deleted).
- Device Pass: F-02 round trip 44879417411 (api36). B-31 first 64 KB Pass 44879268223 (2632 / 5783 ms), seek Fail B31-SEEK.

## Pending runs (check each, record in the ledger, then delete the ab/ branch)
- CI 44881206020 ab/speed12-before (expect compile fail: cachedHome unresolved).
- CI 44880750191 ab/e4-before (expect compile fail: ExtensionSpeed unresolved).
- Device 44880554878 BuiltinTorrentDeviceTest both phones @e451b783 (seek fix + notice gate).
- Device 44880575223 BuiltinTorrentDeviceTest TV api24 @e451b783.
- CI 44881247581 r15-stability @4a58cb22 with abi_splits=true: pass-after for F-22, B-31 notice, E2, E3, B-31 seek,
  E4, SPEED-1/2. Do not dispatch another r15-stability CI while it runs (it would cancel it).

## Next steps, in order
1. Speed work (owner mandatory): feed loading, extension loading (start-up of the extension engine), search
   (ordering by ExtensionSpeed, per-extension timeout, results streamed as they arrive). Measure first, fix, test.
2. E4 part 2: visible-rows scheduler, low-RAM / heat throttle, Explore guide delay.
3. Device checks for every "Built, not tested" row; UNVERIFIED-QUEUE / INVENTORY; G-rows in GOAL-TOP-OF-CLASS.md.
4. E3 website ABI detector: Blocked (the website is not in this repo). E1 master extension: large, not started.
5. Release only on the owner's explicit go.
