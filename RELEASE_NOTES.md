# Rebound 1.3.0

Softer UI audio, improved playback, and a closed ceiling gap.

- **Soft UI pop:** uses Marevnik's existing, positively reviewed UI Pop Up sound under CC BY 4.0, not the hard physical gum-bubble recording.
- **Playback rebuilt:** short decoded audio, individual hit counts, and overlap mixing replace recycled sound slots. Silence trimmed; no hit cooldown. Large simultaneous groups share weighted onsets rather than a delayed audio queue.
- **Ceiling gap removed:** the ceiling now meets the top row, just as the side walls meet the outer columns.
- **Saved runs preserved:** older saves are migrated safely to the tighter ceiling. A ball trapped above a block is returned without awarding extra damage.
- Pull-back aiming, short guide, speed slider, temporary Speed up, and other controls are unchanged.

## Install

Download **Rebound-1.3.0.apk** on your Android phone and open it. Install it over the existing version; do not uninstall if you want to keep your progress. Android may ask you to allow installation from your browser or file manager. Android 8.0 or newer is required.

The accompanying SHA-256 file verifies the APK download. Source ZIP/TAR archives are for developers, not for installing the game.

## Tested and limitations

Tests include ceiling collision/migration, PCM mixing, simultaneous hits, and Android playback-head advancement after actual nonzero audio writes. Full test counts and release checks are recorded in the repository's validation notes.

Device-specific sound/vibration feel and long-session difficulty still need real-phone feedback. This release is intended for that playtest; no leaderboard or cloud sync is included.