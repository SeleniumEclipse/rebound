# Rebound 1.2.0

Recorded pop audio and tighter board edges based on playtest feedback.

- **Recorded bubble pop:** replaced the synthesized hit with “Bubble Pop” by Mafon2, a CC0 recording of a gum bubble popping.
- **More consistent hit audio:** removed the short cooldown that discarded closely spaced hits and increased overlapping sound capacity. Simultaneous hits within one frame share a pop.
- **No side corridors:** walls now meet the outer edges of the first and last block columns, preventing balls from slipping into the gaps beside edge blocks.
- **Saved runs preserved:** older saves are migrated safely to the tighter board. A ball trapped in a removed side lane is returned if there is no room to move it inward, without awarding extra damage.
- Pull-back aiming, short guide, speed slider, temporary Speed up, and other controls are unchanged.

## Install

Download **Rebound-1.2.0.apk** on your Android phone and open it. Install it over the existing version; do not uninstall if you want to keep your progress. Android may ask you to allow installation from your browser or file manager. Android 8.0 or newer is required.

The accompanying SHA-256 file verifies the APK download. Source ZIP/TAR archives are for developers, not for installing the game.

## Tested and limitations

118 JVM tests and 33 Android tests passed, including tight-wall collision regressions, old-save migration, MP3 decoding, rapid-hit playback, and rendered wall alignment. Release lint and the actual signed-APK gameplay/save-restore check also passed. Full details are in the repository's validation notes.

Device-specific sound/vibration feel and long-session difficulty still need real-phone feedback. This release is intended for that playtest; no leaderboard or cloud sync is included.