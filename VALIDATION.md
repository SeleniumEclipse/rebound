# Rebound 1.0.0 validation

Validated locally on 9 September 2026. Claims below distinguish simulated fixtures from actual release play.

## Automated checks

**79 JVM tests passed** through Gradle, zero failures/errors:

- 15 collision geometry tests: face and rounded-corner contacts, speed conservation, wall corners, sweeps, and non-allocating collision-query equivalence.
- 45 game-engine tests: hit counts/destruction, first landing, pickup-once behavior, queued launches, row timing, loss, recall, input validation, deterministic rows, and chronological shared-block contacts.
- 15 snapshot tests: deep copies, malformed state rejection, JSON round trips, and resumed-versus-original continuation.
- 4 longer simulation tests: bounded repeated rounds and 200/999-ball workloads. These prove simulation correctness, **not physical-device frame-rate guarantees**.

**14 Android instrumentation tests passed** on the SetTest Android 14/API 34 emulator, 1080 × 2340 pixels:

- Actual down/move/up gesture aims before release and launches after release.
- Cancelled gestures do not shoot.
- A volley moves, emits its queue, and responds to pause/resume.
- Home/continue and system-back behavior preserve the run.
- Valid near-loss fixture reaches results; replay starts fresh.
- Help, new-game confirmation, persistent settings/best score, and fresh-model restoration.
- 48dp-class game/menu touch targets, board accessibility actions, block/control contrast.
- **Rendered-image regression:** pixels in the airborne-ball area change between frames, independently of checking stored positions.

Release lint: **zero errors**. Remaining warnings are newer-library notices, the intentionally portrait activity, and an unnecessary but harmless minimum-SDK resource qualifier. No warning baseline was used to hide errors.

## Actual signed APK

The non-debug `com.nicgames.rebound` release was installed on the emulator and exercised by [tools/release-smoke.cjs](tools/release-smoke.cjs), without injected fixtures:

1. Launch home and start/continue a run.
2. Send an actual touch shot; wait for completion and round advancement.
3. Open pause and verify its controls.
4. Background and force-stop the process; relaunch and continue the saved round.
5. Open settings and check the expected controls.
6. Confirm the release process stays alive with no fatal Android runtime entries.

APK package inspection confirms version 1.0.0, minimum API 26, target API 35, no debuggable flag, and **no Internet permission**. Signature verification uses the Android SDK's APK verifier. The only requested platform permission is vibration; AndroidX also declares its package-scoped non-exported-receiver signature permission.

Final downloadable APK: **1,001,196 bytes**, SHA-256 **63572ac2a2b6c2cdcad1f3464feca1048fc67671ee9f6c61c4a9c9d1a8b6156d**. All six black-box checks passed again on a separate ReboundTest Android 14/API 34 emulator. A prior check was interrupted by another project's test taking the foreground on a shared emulator; the smoke helper now explicitly detects this condition. Independent first-boot Android services also delayed one attempt; the completed run passed without changing gameplay code.

Screenshots under [screenshots](screenshots) are actual Android renders. The home/paused/result/all-color board examples use deterministic test fixtures; release-prefixed captures come from black-box release play. Images were inspected for clipping, hierarchy, and readable numbers.

## Fixed during review

- The volley deadline now allows every queued ball to launch, plus 24 seconds of return time. It cannot silently discard the tail of a 999-ball volley.
- Shared-block hits are processed chronologically instead of by ball-list order.
- The Canvas drawing phase explicitly observes simulation updates; mutable engine identity cannot suppress redraws.
- The Android 8 theme no longer uses an Android 8.1-only attribute.

## Known limits

- Long-session difficulty, sound/haptic feel, and frame pacing on physical phones still need player feedback.
- Designed for portrait phones; not a separately designed landscape/tablet interface.
- More than 99 remaining hits share the deep-violet band; the printed number remains exact. Ball count is capped at 999.
- Aiming guide shows the first collision, not an entire predicted volley. Balls change the board as they hit it.
- The 24-second post-launch safety deadline and Collect finish remaining travel without awarding hypothetical hits.
- Animation setting disables impact/rising-row effects, not essential ball motion.
- Labeled controls and keyboard/TalkBack aiming actions are included, but full nonvisual gameplay is not claimed.
- No cloud sync. Uninstalling clears local progress. Periodic in-foreground snapshots occur roughly every two seconds; ordinary backgrounding takes an immediate checkpoint.
- This is a sideloadable Android release, not a Google Play submission or store-policy certification.