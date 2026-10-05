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

The sample is decoded ahead of time into PCM and trimmed to about 71ms. No substitute oscillator or musical beep is synthesized. The original sample, pitch, and attribution remain unchanged.

The previous mixer stacked up to 96 voices, increased gain with collision count, and applied `tanh` to the sum. That prevented integer clipping but did not prevent harsh waveform distortion. At 6x, a real 250-ball collision trace produced an average signal level substantially higher than at 1x.

Audio now groups dense collisions into fixed-level feedback, using the output sample clock rather than accelerated game time. The first hit starts immediately; further hits share one pending onset, at most 60ms later for the bundled sample. Complete sample tails overlap, with at most two voices and no hit-count gain multiplier or nonlinear limiter. This intentionally does not promise one separately audible pop per collision. Game hit counts remain exact. With the bundled sample, normal output gain is 0.5 and preview gain is 0.6; a one-time input peak bound guarantees headroom even for a full-scale test asset. Lifecycle stop clears pending feedback; a natural burst ends within one sample plus one onset interval. Media volume and Sound remain authoritative; wall bounces stay silent.

## Difficulty Reference

The first releases used estimated difficulty settings, not verified Ballz settings. Version 1.4.0 uses direct observation of [Richard's Corner's 2017 Ballz recording](https://www.youtube.com/watch?v=SziYPhcseTU). The [official description](https://apps.apple.com/us/app/ballz/id1139609950) confirms round-based progression but does not publish generation probabilities. Unofficial clones were not treated as Ketchapp's implementation.

Counted 47 readable fresh rows from rounds 1-50, checking frames before their blocks were damaged. Excluded the edited/ambiguous transition around rounds 14-15 and the already-active round 19. [The observation table](design/ballz-observations.csv) records timestamps, counts, doubles, and ball pickups. Yellow currency rings are not extra balls.

| Measurement | Observed Original | Rebound Before | Rebound 1.4.0 |
| --- | --- | --- | --- |
| Fresh blocks per row | 1-6; mean 3.234 | Uniform 2-4; mean 3 | 1-6; expected mean 3.249 |
| Five- or six-block rows | 8/47 (17.0%) | Never | About 15.2% |
| Fresh block strength | Round or double round | 70-130% of round, sometimes doubled | Round or double round |
| Double-strength blocks | 24/152 (15.8%) | 12.5%, only from round 4 | One in six, including round 1 |
| Opening ball supply | One ball, no pickup | One ball plus a pickup | One ball, no pickup |
| Later ball supply | One collectible per observed row | One collectible per row | Unchanged |

For each of six available block positions, a 54% trial determines row population, with a minimum of one block. All seven columns are shuffled, so the reserved extra-ball lane is not fixed. Expected incoming damage is about 3.79 times the round number, versus 3.375 previously: approximately 12% more on average, with crowded rows now possible. At least one column remains free of blocks, even when it contains a collectible.

These probabilities are an approximation fitted to one short run, not recovered source code or statistically exact rates. Classic rules are the target, not unverified changes in newer Ballz releases. The observed footage supports one pickup per later row, not an exact published lifetime guarantee. The existing geometry, speed controls, launch spacing, and safety deadline remain Rebound's own behavior. Existing boards and earned balls are preserved; only newly generated rows use the revised rules.

## References

- [Mini Metro](https://dinopoloclub.com/games/mini-metro/): useful shape/color language and uncluttered gameplay.
- [Holedown](https://holedown.com/): readable numbered targets and focused impact feedback.
- [Anthropic frontend-design discussion](https://claude.com/blog/improving-frontend-design-through-skills): models can substitute one familiar aesthetic for another after a ban. A source to critique, not a recipe to follow.
- [NN/g aesthetic/minimalist design](https://www.nngroup.com/articles/aesthetic-minimalist-design/): remove unnecessary information, not useful controls.

Reference artwork and branding are not bundled. Familiar visual elements are not proof of AI authorship; decisions should serve the game rather than a checklist.