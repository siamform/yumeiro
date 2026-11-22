# GATE-1 on real hardware (owner step 1, 2026-11-22)

GATE-1 = `GateOnePrepareDeviceTest` → `MainThreadStallRealClockDeviceTest` → `GateOneExitCheckDeviceTest`,
limit 700 ms, never raised. It fails only on the CI emulators because GitHub runners have no GPU: about
190 ms of every frame goes to `eglSwapBuffers` in the software renderer (finding GATE-1-EMU in the ledger).
So the gate has to be measured on a real GPU. Three routes, in order. Each records the walk twice
(GATE-1-AB): `current` (every GATE-1 fix on) and `baseline` (the five GATE-1 fix flags off = the app
before the GATE-1 work), on the same build, test and device, so the app's own gain is visible.

GATE-1 fix flags (GateOneVariant.FLAGS): `gate1_tab_next_frame`, `gate1_shader_prewarm`,
`gate1_feed_staged_reveal`, `gate1_poster_plain_image`, `gate1_extension_bg_priority`. The variant
only works since FF-LOAD (before it, `FeatureFlags.isOn` ignored the saved file).

## Route A: Firebase Test Lab (preferred, free quota)

Workflow `.github/workflows/ftl-gate.yml` (manual), body `scripts/ftl-gate.sh`, target
`GateOneFtlSuite` with the test orchestrator and `clearPackageData=false`. Spark plan: 5 physical-device
runs a day (https://firebase.google.com/docs/test-lab/usage-quotas-pricing); the default run uses 4
(one budget phone + one Pixel, current + baseline).

State 2026-11-22: the repo secret `FIREBASE_SERVICE_ACCOUNT_JSON` (firebase-adminsdk-fbsvc@yumeiro-ab29e)
signs in, but Test Lab answers `403 Not authorized for project yumeiro-ab29e`. Checked with
`testIamPermissions`: the account has only `firebase.projects.get` and `storage.buckets.create`, and
cannot enable APIs or change its own roles. Firebase's CI guide asks for the Editor role and two APIs
(https://firebase.google.com/docs/test-lab/android/continuous). Owner steps (about 3 minutes):

1. Open https://console.cloud.google.com/apis/library/testing.googleapis.com?project=yumeiro-ab29e → Enable.
2. Open https://console.cloud.google.com/apis/library/toolresults.googleapis.com?project=yumeiro-ab29e → Enable.
3. Open https://console.cloud.google.com/iam-admin/iam?project=yumeiro-ab29e → find
   `firebase-adminsdk-fbsvc@yumeiro-ab29e.iam.gserviceaccount.com` → Edit (pencil) → Add role → **Editor**
   → Save. (Narrower option: "Firebase Test Lab Admin" + "Storage Object Admin".)
4. Tell the agent "done". The agent runs `gh workflow run ftl-gate.yml --ref r15-stability` and records the
   result in the ledger rows GATE-1, GATE-1-EMU, GATE-1-AB.

## Route B: own runner with a USB phone or a GPU emulator

Workflow `.github/workflows/gate-usb.yml` (manual, `runs-on: [self-hosted, yumeiro-device]`), same body as
the CI device check (`scripts/device-check.sh`). It installs the debug build (`dev.asfakul.yumeiro.debug`,
separate from the normal app) and sets `settings global hide_error_dialogs 1` on the device.

1. On a Linux or Windows PC: install JDK 21, the Android SDK platform-tools (adb) and, for an emulator,
   the Android emulator with a hardware-GPU AVD (`-gpu host`).
2. Phone: Settings → About → tap Build number 7 times → Developer options → USB debugging on → plug in →
   allow the PC. `adb devices` must list exactly one device.
3. GitHub → siamform/yumeiro → Settings → Actions → Runners → New self-hosted runner → follow the commands
   shown; when asked for labels, add `yumeiro-device`. Start it (`./run.sh` or `run.cmd`).
4. `gh workflow run gate-usb.yml --ref r15-stability` then again with `-f gate1_baseline=true`.

## Route C: community test

Only if A and B are both not possible: OMT-72 in docs/OWNER-MANUAL-TESTS.md, with every failed attempt
written in the ledger row GATE-1.

## Emulator A/B (no quota, done by the agent)

`gh workflow run device-check.yml --ref r15-stability -f test=<the three classes>` with and without
`-f gate1_baseline=true`. Shows the app's own gain on the same software GPU; it cannot make the gate pass
there (GATE-1-EMU).
