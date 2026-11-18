# G6-MO1 — Music tab stayed on "On device" after the connection came back, although the user picked Online

Status: Verified (CI unit, Robolectric UI) — fail-before 44225572082 red on target only; fix 7d1486a; pass-after CI 44227662448 (MusicModeReconnectTest 1/1, Round13LogicTest 13/13). Emulator network toggle: next G6 device run.
Found by code read, 2026-11-18.

## 1. Code read (head f8564b8)

| Step | Code | What it does |
|---|---|---|
| Default | `MTPlayerApp` `musicModeKey` = `MusicMode.defaultFor(isOnline)` | Online with internet, On device without |
| Connection changes | `Connectivity.online.collect` → `MusicMode.follow(current, online, picked: Boolean)` | follows the connection until the user picks; offline always leaves Online |
| User picks | source menu `onSelect` → `musicModePicked = true` | only a yes/no flag; the picked mode is not kept |
| **Pick Online → offline → online** | offline: `current == ONLINE` → DEVICE; online: `picked` → `current` (DEVICE) | **stays on On device for good**; the user must open the menu and pick Online again after every drop (lifts, trains, Wi-Fi hand-over) |

The rule written above the code says "once the user picks a mode it sticks"; the forced offline exit was meant to
be temporary.

## 2. Competitors (ideas only, nothing copied)

- **Metrolist v14.0.0** `androidApp/.../ui/GlobalPlaybackNetworkMonitor.kt`: the network is a separate live
  state object (available / lost / blocked per network) that screens read; it holds no user choice, so a drop
  cannot overwrite one.
- **Yumeiro Videos source** (`videoSourcePicked`, same file): once picked, the source is never rewritten by
  the connection — the pick is the source itself.
- InnerTune v0.5.10 has no online/on-device mode switch (single online library + local songs), so no idea to take.

Idea taken: keep the user's choice as a value; the connection only overrides it while it cannot work.

## 3. Options

| Option | Pros | Cons |
|---|---|---|
| **A keep the picked mode (key, saveable); offline shows On device only while offline** | the pick returns by itself; one pure function | none found |
| B never leave Online when offline | the pick never changes | Online shows nothing playable offline (the reason for the rule) |
| C ask "Back online — switch to Online?" | explicit | a prompt on every reconnect |

## 4. Action–reaction

| Action | Reaction |
|---|---|
| No pick, connection drops / returns | On device / Online (unchanged) |
| Pick Online, connection drops | On device (unchanged) |
| … connection returns | **Online again** (was: stays On device) |
| Pick On device or Mix, connection changes | stays as picked (unchanged) |

## 5. Tests

Robolectric UI `ui/MusicModeReconnectTest` (taps the real source menu, toggles `Connectivity.overrideForTests`);
unit `round13/Round13LogicTest.music_mode_follows_connection` updated to the new `picked: MusicMode?` and a
reconnect case. Device: the same flow with the emulator network toggled.
