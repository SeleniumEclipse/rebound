# Design decisions

The user approved the **Clear multicolor** board and allowed bolder menus, headers, and footers for the finished game.

## Keep the playfield calm

- Light neutral field `#F2F3F4`, dark ink/ball `#243036`.
- Real square collision targets. Numbers remain the authoritative remaining-hit count.
- Coral `#F27C7C` below 10 hits; orange `#F5AD65` below 20; yellow `#E9D363` below 30; green `#9ACC8B` below 40; teal `#62C4B9` below 60; violet `#AB97D8` below 100; deep violet `#7354A2` above that.
- Dark digits on the six light colors; white digits on deep violet. No random color assignment.
- Hollow plus circles are extra-ball pickups, never targets. Balls and the aiming guide are dark.
- Pull-back aiming uses displacement from the initial touch. Pull down to shoot up; a small dead zone prevents tap shots, and returning to the origin cancels the gesture.
- The dotted guide is capped at one-third of the board height, or a closer radius-correct collision. No distant endpoint ring: it hints at direction instead of solving the shot.
- Ceiling and side boundaries are drawn exactly at the collision surfaces. The next-shot marker moves on the first landing without moving the current volley's emitter.
- Side walls are flush with the unchanged first and last block columns: x=20 and x=338. There is no playable side lane outside the spawn area. The 4-unit interior block spacing is unchanged.
- The ceiling is flush with the first row at y=20 as well. There is no lane above the spawning area.
- A short impact ring is feedback, not an ambient effect. Animation can be disabled.

## Give the menus a stronger identity

Archivo Black headings, dark score/controls bands, flat color-block trim, and original ball-path artwork. One large action per menu. Broad rectangular buttons, with real text labels. Best round stays on the home/result screens, not in the playfield.

All secondary actions have visible outlines, including New game, navigation, in-game Settings, and footer controls. Settings contains the saved normal-speed slider (1–6×, tenths). The footer's Speed up is a highlighted one-volley override, not a way to edit the saved preference. Settings pauses play and returns to where it was opened.

No gradient, glow, glass blur, cream/serif fallback, highlighted headline word, decorative technical labels, emoji icons, card-dashboard layout, fake statistics, or extra subtitles. Help and license documents can scroll; the game cannot.

## Audio

Use a short, soft UI pop—not a physical gum-bubble snap. The credited **UI_POP_UP.mp3 by Marevnik** was selected from an existing interface-sound listing with approximately 9,600 downloads, 247 ratings, and comments including “Exactly what I needed! Thanks.” These are observed page figures, not proof that every player will prefer it.

The sample is decoded ahead of time into PCM, trimmed to about 71ms, and slightly attenuated. No substitute oscillator or musical beep is synthesized. A persistent AudioTrack mixes overlapping pops, and the game passes the actual number of hits rather than collapsing a frame to one event. Small same-frame groups get closely spaced onsets; extreme hit storms combine weighted onsets instead of building a long delayed queue. A soft limiter avoids hard clipping. Media volume and the player's Sound setting remain authoritative. Wall bounces are still silent; pops correspond to block hits.

## References

- [Mini Metro](https://dinopoloclub.com/games/mini-metro/): useful shape/color language and uncluttered gameplay.
- [Holedown](https://holedown.com/): readable numbered targets and focused impact feedback.
- [Anthropic frontend-design discussion](https://claude.com/blog/improving-frontend-design-through-skills): models can substitute one familiar aesthetic for another after a ban. A source to critique, not a recipe to follow.
- [NN/g aesthetic/minimalist design](https://www.nngroup.com/articles/aesthetic-minimalist-design/): remove unnecessary information, not useful controls.

Reference artwork and branding are not bundled. Familiar visual elements are not proof of AI authorship; decisions should serve the game rather than a checklist.