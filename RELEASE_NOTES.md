# Rebound 1.4.0

Closer classic difficulty and clean sound at high speed.

- **Difficulty checked against original footage:** rows can now contain one to six blocks, including occasional crowded rows. Block strength equals the round number, with a one-in-six chance of double strength. Average incoming damage is about 12% higher than before.
- **Opening ball supply corrected:** start with one ball and no opening pickup. One extra-ball collectible appears with each later row. Existing earned balls are kept.
- **Sound stays controlled at 6x:** the same soft UI pop now plays at a fixed level. Dense impacts share regularly spaced pops instead of stacking louder samples into a distorted buzz. Earlier tails finish; sound does not build a long queue.
- **Saved runs preserved:** current boards and progress remain intact; future rows use the revised balance.
- Pull-back aiming, short guide, speed slider, temporary Speed up, and board geometry are unchanged.

## Install

Download **Rebound-1.4.0.apk** on your Android phone and open it. Install it over the existing version; do not uninstall if you want to keep your progress. Android may ask you to allow installation from your browser or file manager. Android 8.0 or newer is required.

The accompanying SHA-256 file verifies the APK download. Source ZIP/TAR archives are for developers, not for installing the game.

## Tested and limitations

Difficulty probabilities are fitted to 47 readable fresh rows in a 2017 Ballz recording, not recovered from Ketchapp's code. The observed range, block strength rules, and initial ball supply replace earlier estimates.

Tests cover every speed setting, real dense-volley audio at 1x/3.5x/6x, Android playback, row distribution, saved games, and existing controls. Full counts and signed-release checks are in the repository's validation notes.

Device-specific sound/vibration feel and long-session difficulty still need real-phone feedback. This release is intended for that playtest; no leaderboard or cloud sync is included.