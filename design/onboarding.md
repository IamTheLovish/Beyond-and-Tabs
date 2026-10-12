# Onboarding: First Battle, first-time tips, controls sheet, faction cards

lovish is sending the game to remote testers: some have never played Beyond All Reason, one plays on a Mac with a
mouse (likely no middle button). Everything here is designed from that player's point of view: what do I press, what
is this thing, why did nothing happen. Code: `onboarding/` (new package), with small hooks listed at the end.

## RoN's `tutorial/` package: replaced, not reused

RoN's tutorial is bound to a downloadable world (`reign_of_nether_tutorial`, fixed seed and coordinates), to RoN's
villager economy (wood / food / ore, houses, town centre) and to its old camera. None of that exists in a BAR match:
metal patches, energy, labs, a commander that loses you the game, the Spring camera. Its stage machine is client-side
(the server only stores the stage), so completion can be faked and is untestable headless. Kept untouched (it only
switches on inside its own world), and replaced by a server-authoritative step machine. What was taken from it: the
idea of a saved stage for resume, and a help panel instead of chat spam.

## 1. First Battle (title screen -> First Battle)

**Entry.** "First Battle" takes the left half of the Skirmish row on the gothic title screen (Skirmish keeps the right
half; nothing goes above it, so the lettering stays clear - design/splash.md). The First Battle screen has one short
paragraph, a faction chooser with that faction's info card (the Sunforged Kingdom preselected and marked "recommended
for a first game"), **Start**, **Resume** (only when an unfinished tutorial world exists), **Controls** and **Back**.

**Scenario** (`OnboardingServer.start`): a fresh world per tutorial (like Skirmish, so an old one never bleeds in), the
student's readied start (capitol foundations, commander, workers), a small arena (radius 230), fast economy pace, and
a **passive sparring bot** ("Rival", Gravebound - or the Kingdom against a Gravebound student) 80-110 blocks away on
dry flat ground, with its own capitol. The bot is a real RTS bot (it can be beaten, it is in the match) whose brain is
`PassiveBot`: `think()` does nothing. The bot loop only rebuilds missing brains, so the passive one is re-attached at
server start and checked every tick after a reload.

**Steps** (`OnboardingStep`), each finishing on the real action; the panel names the player's actual keys and their
faction's building names:

| # | Step | Finishes when | Notes for the human |
|---|---|---|---|
| - | Welcome | Continue | what the panel is, Skip / Quit / fold |
| 1 | Zoom | zoom changed by 35 % (wheel or PgUp/PgDn) | zooms toward the cursor; trackpad two-finger scroll |
| 2 | Pan | camera moved 12 blocks while the zoom held still | middle-drag; **no middle button: WASD / arrows / edges** |
| 3 | Rotate | 25 degrees of yaw + pitch | Alt (Option on Mac) + middle-drag, or Alt + A / D; Right Ctrl resets |
| 4 | Overview | Tab overview opened | Tab again or click to fly back in |
| 5 | Select | exactly one own unit selected | commander marked by a ring of light |
| 6 | Box select | two or more own units selected | Shift-click, double-click mentioned |
| 7 | Move | the server receives a move order | Shift queues |
| 8 | Line formation | two single-unit orders to different spots within 2 ticks (what a right-drag sends) | a plain right click (one order for the group) does not count |
| 9 | Extractor | the student's metal building is finished | nearest metal patch marked; "building..." while it goes up |
| 10 | Energy | an energy building is finished | the top bar explained |
| 11 | Lab | the faction's lab is finished | several workers build faster |
| 12 | Train | two more fighters than when the step began | rally point; Horde: refit the portal |
| 13 | Attack-move | an attack-move order | A then click (or right-click) |
| 14 | Area reclaim | a student worker is reclaiming a wreck | six fresh wrecks spawned 14 blocks toward the enemy and marked |
| 15 | D-gun | the commander's D-gun is on cooldown (a real shot) | energy topped up first |
| 16 | Kill the commander | the enemy commander was seen alive and is now dead (or the bot is out of the match) | the BAR rule: commander death loses a 1v1 |
| - | Victory | Finish | stats table, possession, Esc -> Controls |

The economy comes before attack-move on purpose: attack-move is learnt with real fighters, not workers.

**Skippable**: every step has Skip step (the request names the step the player saw, so a double click can't skip two).
**Resumable**: the step is saved per player in `OnboardingSaveData`; Resume reloads the world and the server sends the
step back on login. **Quit** ends the lesson; the match stays as a normal game. If the student's commander dies, the
lesson ends with a message pointing back to the title screen.

**Resources**: the steps top the student up (never take away) so nobody stalls mid-lesson: 400/400 at the extractor,
700/700 for energy and the lab, 800/800 to train, 450 energy for the D-gun.

**World highlights**: a turning ring of light (END_ROD particles) with a rising mote at the marked spot - readable from
straight above in the ortho camera, cheap (a dozen client particles every fourth tick, only during a tutorial).

**Panel**: top left under the control groups (x 4, y 44, 150-214 px wide): "FIRST BATTLE  n / 16", the step title, a
gold rule, the wrapped text (wrapped once per step), a "building..." line when relevant, then Continue/Skip step and
Quit. The "-" in its corner folds it to two lines. Clicks on it never reach the battlefield.

## 2. First-time tips (normal matches)

`OnboardingTips`: one line above the command card, gothic plate, "TIP" in gold, 14 s with a fade, a click dismisses it.
Each tip shows **once ever** (ids remembered in `onboarding_seen_tips`), never during the First Battle, at most one per
40 s, and **off** with "First-time tips" in Mod Settings -> HUD... or on the Controls sheet (which also has Reset tips).

| Tip | Moment |
|---|---|
| Esc -> Controls lists every key | 8 s into a match |
| Your Commander is under attack - press Space to look | own commander's health drops |
| Overflowing - build converters, another lab or more builders | > 95 % storage with a positive net for 5 s (the economy bar's rule) |
| Backspace with one unit selected to take control | one unit selected for 3 s |
| Right-drag to spread a group along a line | five or more units selected |
| Press I for the stats table | 3 minutes in |
| Press = for a clean view | 8 minutes in |

Keys in the tips are the player's current bindings. Cost: one check a second while any tip is unseen; nothing once all
are seen or tips are off.

## 3. Controls sheet

`ControlsReferenceScreen`, from the title screen ("?" left of the First Battle / Skirmish row), the First Battle screen
and the Match Menu (Controls). Built from `keybinds/Keybindings` every time it opens, so rebinds show (in green,
"(changed)"); "Change keys..." opens Minecraft's Controls page and coming back refreshes the sheet. Sections:
Essentials (possession, I stats, = clean view, F1 hide HUD, Space last alert, Tab overview, idle worker), Camera
(including a Mac mouse / trackpad row), Selecting, Orders, Building (the ten ability slots and twelve menu slots on one
line each), Other. Scrolls with the wheel. Also holds the tips switch and Reset tips.

## 4. Faction info card

`FactionInfoCard`: identity, strengths, weaknesses, signature mechanic, T3 experimental and "for new players"
(difficulty), from the lang file (written from the project's faction design doc and the bot kits' real T3s). Shown
beside the faction picker in **both lobbies** (the skirmish setup and the multiplayer lobby share `FactionPicker`, and
so does the match-start screen) for the hovered or keyboard-highlighted faction - left of the list when there is room,
else right, else not at all - and on the First Battle screen.

## Look

The title screen's palette (`hud/SplashArt.restyleButtons`): dark stone plates with a lit upper face, gold frames,
parchment text, a gold rule with a lozenge. The First Battle and Controls screens draw the cathedral rose window behind
them when opened from the title screen, and repaint their buttons the same way.

## Hooks outside `onboarding/`

- `unit/packets/UnitActionServerboundPacket.accept`: one call to `OnboardingServer.onOrder` (returns at once when no
  tutorial runs).
- `matchstart/FactionPicker`: remembers the screen size and draws the card beside the open list.
- `matchstart/MatchMenuScreen`: a Controls button.
- `config/ReignOfNetherClientConfigs`: `onboarding_tips`, `onboarding_seen_tips`, `onboarding_world`;
  `config/HudSettingsScreen`: the tips checkbox.
- `registrars/PacketHandler` (two packets), `ServerEventRegistrar`, `ClientEventRegistrar`.
- Title screen: no mixin change - `OnboardingClient.onTitleInit` (after QuickBattle's) shares the Skirmish row.

## Tests

`gametest/OnboardingGameTests`: the step machine (client reports only for client steps, skip, save/load), order
detection (move, the formation burst vs a plain click or two slow clicks, attack-move), building detection for every
faction's extractor / energy / lab (capitol never counts, unfinished = in progress, another player's never counts),
train (two new fighters, workers excluded), reclaim, D-gun cooldown, the enemy commander's death, and the scenario
pieces that need no battlefield (passive brain, site distance, wreck field). Not covered headless: the full
`start()` (it forms a battlefield, which would join other tests' arenas) and everything client-side.
