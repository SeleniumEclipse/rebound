# Rebound 1.1.0

Controls and play-screen improvements based on playtest feedback.

- **Pull back to shoot forward.** Touch anywhere, pull down-left to shoot up-right, then release. Taps do not shoot; returning to your starting point cancels the shot.
- **Shorter aiming guide:** about one-third of the board height, with no distant hit marker.
- **Saved speed slider** in Settings, from 1× to 6× in tenths.
- **Settings during play and pause:** the game waits while you adjust it; Back returns to the same run.
- **Speed up:** temporarily use maximum speed until this volley ends, then return to your preference. Hidden if you already chose maximum speed.
- **Immediate next-shot position:** the bottom marker moves when the first ball lands, even while others are still moving.
- **Visible ceiling** matching the actual bounce surface.
- **Clearly outlined buttons**, including New game and secondary navigation.
- **Clearer block-hit audio** and a Test sound button in Settings. The phone's volume buttons control media volume while in the app; no system volume is changed automatically.
- Existing saved games, best round, and other settings remain compatible. Offline, without ads or accounts.

## Install

Download **Rebound-1.1.0.apk** on your Android phone and open it. Install it over 1.0.0; do not uninstall if you want to keep your progress. Android may ask you to allow installation from your browser or file manager. Android 8.0 or newer is required.

The accompanying SHA-256 file verifies the APK download. Source ZIP/TAR archives are for developers, not for installing the game.

## Tested and limitations

Automated physics and Android interaction tests, release lint, and an actual signed-APK startup/gameplay/save-restore smoke check. Full details are in the repository's validation notes.

Device-specific sound/vibration feel and long-session difficulty still need real-phone feedback. This release is intended for that playtest; no leaderboard or cloud sync is included.