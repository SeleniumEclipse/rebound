# Third-party notices

- **Archivo** — Copyright 2020 The Archivo Project Authors. [Project](https://github.com/Omnibus-Type/Archivo). SIL Open Font License 1.1.
- **Archivo Black** — Copyright 2017 The Archivo Black Project Authors. [Project](https://github.com/Omnibus-Type/ArchivoBlack). SIL Open Font License 1.1.
- Full font licenses are packaged in [app/src/main/res/raw/font_licenses.txt](app/src/main/res/raw/font_licenses.txt), also accessible through Settings → About & licenses.
- **AndroidX / Jetpack Compose / Material icons** — Android Open Source Project and contributors, Apache License 2.0.
- **Kotlin / kotlinx.serialization** — JetBrains and contributors, Apache License 2.0.
- **Gradle wrapper** — Gradle, Inc. and contributors, Apache License 2.0.
- **JUnit 4** (tests only) — Eclipse Public License 1.0.

## UI pop sound — required attribution

**“UI_POP_UP.mp3” by Marevnik**, Freesound, 3 November 2023, licensed **Creative Commons Attribution 4.0 International**.

- [Creator and sound page](https://freesound.org/people/Marevnik/sounds/708605/)
- [License terms](https://creativecommons.org/licenses/by/4.0/) and [legal code](https://creativecommons.org/licenses/by/4.0/legalcode)
- [Public high-quality preview used](https://cdn.freesound.org/previews/708/708605_13515726-hq.mp3)
- Source SHA-256: `76e68aee31611cc35ee5c12c7e1f90766d1c149765ad3acc444747e20f4d1677`.
- Adapted asset: [app/src/main/res/raw/ui_pop.wav](app/src/main/res/raw/ui_pop.wav), 6,268 bytes, SHA-256 `d2e5adfec812a575e242ba69507a11e1e6f336ec940bbe971a4bfe39b9082919`.
- **Changes:** decoded to mono PCM, leading/trailing silence trimmed, amplitude reduced slightly, 1ms edge fades. Pitch and tone retained. Playback mixing includes gain/headroom protection. No endorsement by the creator is implied.
- Preparation and timing analysis: [tools/prepare-ui-pop.cjs](tools/prepare-ui-pop.cjs). This utility also downloads the previous CC0 recording only for comparison; that recording is no longer bundled.
- The same attribution and license link are accessible in Settings → About & licenses and [app/src/main/res/raw/audio_credit.txt](app/src/main/res/raw/audio_credit.txt).

Original game graphics are drawn in code. No other game's proprietary sound has been extracted. The hard gum-bubble recording and former synthesized hit are removed from the app.