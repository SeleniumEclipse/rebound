# Design decisions

The user approved the **Clear multicolor** board and allowed bolder menus, headers, and footers for the finished game.

## Keep the playfield calm

- Light neutral field `#F2F3F4`, dark ink/ball `#243036`.
- Real square collision targets. Numbers remain the authoritative remaining-hit count.
- Coral `#F27C7C` below 10 hits; orange `#F5AD65` below 20; yellow `#E9D363` below 30; green `#9ACC8B` below 40; teal `#62C4B9` below 60; violet `#AB97D8` below 100; deep violet `#7354A2` above that.
- Dark digits on the six light colors; white digits on deep violet. No random color assignment.
- Hollow plus circles are extra-ball pickups, never targets. Balls and the aiming guide are dark.
- The guide ends at the first radius-correct collision. No decorative curves or false wall markings.
- A short impact ring is feedback, not an ambient effect. Animation can be disabled.

## Give the menus a stronger identity

Archivo Black headings, dark score/controls bands, flat color-block trim, and original ball-path artwork. One large action per menu. Broad rectangular buttons, with real text labels. Best round stays on the home/result screens, not in the playfield.

No gradient, glow, glass blur, cream/serif fallback, highlighted headline word, decorative technical labels, emoji icons, card-dashboard layout, fake statistics, or extra subtitles. Help and license documents can scroll; the game cannot.

## References

- [Mini Metro](https://dinopoloclub.com/games/mini-metro/): useful shape/color language and uncluttered gameplay.
- [Holedown](https://holedown.com/): readable numbered targets and focused impact feedback.
- [Anthropic frontend-design discussion](https://claude.com/blog/improving-frontend-design-through-skills): models can substitute one familiar aesthetic for another after a ban. A source to critique, not a recipe to follow.
- [NN/g aesthetic/minimalist design](https://www.nngroup.com/articles/aesthetic-minimalist-design/): remove unnecessary information, not useful controls.

Reference artwork and branding are not bundled. Familiar visual elements are not proof of AI authorship; decisions should serve the game rather than a checklist.