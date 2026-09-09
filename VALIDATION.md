# Rebound validation

## Version 1.3.0 — 9 September 2026

**142 JVM tests passed** and **36 Android tests passed**, zero failures. This includes the earlier engine/control suite, 14 ceiling/migration regression methods, ten PCM mixer tests, and real Android audio-write/playback checks. Release lint and editor diagnostics report **zero errors**.

### Softer sound and stronger evidence

Selected the existing **UI_POP_UP.mp3 by Marevnik** (CC BY 4.0), not another physical pop recording or generated substitute. The inspected source page showed approximately 9,600 downloads, 247 ratings, and positive comments including “Exactly what I needed! Thanks.” These are observations of the source page, not a universal quality claim. Attribution, source, license link, and modifications are documented in the app and third-party notices.

Measured decoded source audio:
- Previous gum-bubble recording: 223.85ms total; first sample above 0.02 amplitude at 55.71ms; peak 1.021 (MP3 reconstruction overshoot).
- New prepared UI sound: 70.57ms total; first sample above 0.02 at 8.30ms; peak 0.65. It is mono PCM with silence trimmed, slight attenuation, and 1ms edge fades. Tone and pitch retained.

One confirmed logical loss in the old pipeline was a boolean per-frame trigger: multiple block hits in the same frame became one playback request. The new callback passes the hit count to the mixer. PCM voices overlap without restarting earlier samples; small simultaneous groups have 2ms-spaced starts. Extreme groups are weighted into bounded onsets with a soft limiter, not promised as hundreds of separately distinguishable pops. A persistent nonblocking AudioTrack replaces SoundPool, with partial-write handling, interruptible backpressure, lifecycle flush generations, and bounded invalidated-output recovery. A trailing silent buffer ensures a single short sound fills a device's start threshold.

Tests now verify **nonzero PCM actually written and Android's playback head advancing**, not just accepted stream IDs. Eight separately played pops, each followed by complete output drain, produced:
- 56,992 written frames;
- 56,992 played frames;
- 15,312 nonzero frames above the diagnostic threshold;
- zero write failures.

Also tested same-frame hits, rapid successive hits, fresh audio after an acknowledged stop/flush, Sound off, and no changes to system media volume. These checks establish output through Android's audio path; **they do not establish audibility or subjective tone through the user's particular phone speaker/Bluetooth route**. The emulator's host sound was disabled. The source timing and coalesced-hit behavior are measured facts; a specific handset's entire intermittent-sound cause has not been proven.

### Closed top gap and saved-game safety

Ceiling is now y=20, exactly the first row's top; minimum ball-center y=24. Existing side walls remain x=20/338. No upper corridor remains; interior tile spacing is unchanged. Collision tests cover under-block hits, exposed corners and ceiling/wall combinations. Rendered-pixel tests confirm the ceiling moved to the row edge and the old upper line is absent.

Snapshots v1/v2 migrate to v3 after validation against their original bounds. A clear-space ball moves inward and reflects only an outward velocity; a ball that would overlap a block is returned without damage or new pickup awards. Other balls/queues continue; already-earned pickups commit once if the volley finishes. Tests cover implicit old version fields, combined side/top migration, round trips, corrupt-save rejection, and actual app relaunch from an old upper-lane save.

### Signed release

Installed over the prior signed release. All **seven black-box smoke checks passed**, including the real speed slider, pull-back shot/round advancement, pause, process termination, and restored run/preference. The closed-ceiling Android screenshot was visually inspected.

APK **1,024,448 bytes**, SHA-256 **d7fedd47439d99f761cd76e3b5e358be344e393ac4aa324e3d951199cc56b237**. Package unchanged, versionCode 4, versionName 1.3.0.

## Version 1.2.0 — 9 September 2026

**118 JVM tests passed**, zero failures: the previous 98 plus five board-edge test methods (many left/right/angle/corner combinations) and 15 legacy-save migration tests. The edge tests confirm walls exactly at the seven-column grid boundaries, circle-aware aiming, one underside hit rather than a side-lane hit storm, finite motion, conserved velocity, and correct corner/wall behavior.

Migration tests cover both old edges, first-return markers, pending launch queues, forced returns without damage/rewards, earned-pickup commitment exactly once, no mutation of saved objects, rejection of corrupt states, and version-2 JSON round trips. Missing engine version in old JSON still means version 1; new snapshots always encode version 2. The outer preference-save version stays compatible.

**33 Android tests passed** on the isolated ReboundTest Android 14 device. Additions prove:
- Visible walls and floor end at the actual outer block columns, not the old padded positions.
- A real old-format app save containing a ball in a removed side lane loads, resumes, and survives another restart.
- Three close successive hit frames each reach sound playback; three immediate SoundPool calls are no longer filtered by a 55ms gate.
- The exact bundled MP3 matches its licensed source hash and decodes on Android into non-silent PCM. Sound mute and media-volume behavior remain tested.

**Signed-release smoke: seven checks passed** after installing over the earlier app, including real pull-back input, slider setting to 3.2×, round completion, pause, and process restart with run/preference restoration. Release lint and editor diagnostics: **zero errors**. The new wall screenshot was visually inspected.

APK: **1,026,484 bytes**, SHA-256 **9ec69602e49a85a440735872208befc1dcfafa091f9eb19ccb3982955e3331d0**. Package unchanged; versionCode 3 and versionName 1.2.0.

Audio source: CC0 “Bubble Pop” by Mafon2, an author-described gum-bubble recording. The public high-quality preview is bundled unchanged and credited; the synthesized WAV is removed. Audio is one pop per hit-bearing render frame with up to 12 overlapping voices, not a promise of individually distinguishable pops for hundreds of simultaneous collisions. Tests prove decoding and playback requests, not subjective sound quality on the user's handset. Host audio was disabled in the emulator.

## Version 1.1.0 — 9 September 2026

**98 JVM tests passed**, including all previous engine tests plus seven first-landing/queued-emitter tests, eight pull-back aiming tests, and four short-guide geometry tests.

**29 Android tests passed** on the isolated ReboundTest Android 14 device (1080 × 2340, 440dpi). These include:

- Real pull-back touch input and opposite shot direction; taps, cancelled gestures, and returning to the gesture origin do not fire.
- Pixel checks prove the guide stops around one-third of the board height, the ceiling is drawn, and the next-shot marker moves on the first landing while other balls remain active.
- Real slider dragging selects intermediate speeds, and the preference survives restart and new games.
- Settings opens from play and pause, freezes the simulation, and returns to its origin; backgrounding while in Settings returns safely to pause.
- Temporary maximum speed survives pause but ends with the volley or Collect; the saved preference is unchanged. The boost button is hidden when the preference is already maximum.
- Existing 1.0.0 saves without new fields still load.
- Visible New game outline, non-overlapping controls, labeled actions, minimum target sizes, and contrast.
- Audio sample has nonzero PCM energy; SoundPool reports successful real playback on a physics hit and on Test sound. Sound off disables the test, and no system media-volume setting is changed.

The 13 control/rendering tests also passed at **320 × 640dp** on the same isolated device. This is a second layout run, not 13 additional unique tests.

**Signed-release smoke: seven checks passed**, including a real slider drag to 3.3×, returning from in-game Settings, an actual pull-back shot completing a round, pause, forced process restart with run/speed restoration, and no fatal runtime error. Release lint: **zero errors**.

Final APK: **1,025,276 bytes**, SHA-256 **ec6050916baec3a340212375efac91e5bd72655a8a8a34936a944fadc21f292f**. Release certificate matches 1.0.0 for in-place upgrades. Package remains `com.nicgames.rebound`, versionCode 2, versionName 1.1.0.

Audio limits: the test proves the bundled signal and Android playback path, **not audibility through the user's physical phone speaker**. The old sound was a 24ms system beep at a low gain; 1.1.0 uses an original 85ms preloaded impact sample. Test sound and media-volume routing make handset diagnosis possible. No music or wall-bounce sound is claimed. The emulator was started with host audio disabled.

Test correction: the slider's accessibility rectangle extends outside its visible track. The first test began in that margin and did not drag the thumb. Both the corrected continuous gesture and an independent signed-APK input swipe now verify the actual slider; this was not fixed by bypassing the slider with model changes.

## Version 1.0.0 — historical evidence

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