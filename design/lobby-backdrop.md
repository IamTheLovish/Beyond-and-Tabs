# Lobby backdrop: "the moonlit cathedral and the crimson river"

lovish (Oct 21): "I want the lobby art to be different ... Can you animate this in the lobby", from two mood references
(a black gothic cathedral with a glowing rose window over a blood-red sky and a field of red and ivory flowers; a pale
full moon behind the spires and a crimson river running from the doorway). Later the same evening: drawn "with a pen or
pencil or colored pencil tool", "the castle is breathing with wind and water moving", and gothic, bold, blood-dripping
lettering on the title screen buttons and the lobby. **Original art only**: nothing is traced, sampled or embedded from
the references; every layer is drawn from code by `tools/gen_lobby_art.py` (our facade plan, tracery, glass, trees and
flowers), keeping only the mood, palette and the idea of the composition.

Where: `matchstart/SkirmishSetupScreen` and `matchstart/MultiplayerLobbyScreen` call `hud/LobbyBackdrop.render` in place
of `renderBackground`, then `LobbyBackdrop.scrim` (a feathered dark scrim behind the panel, title and footer so all
lobby text stays readable). Layouts, buttons and logic of those screens are unchanged. If any layer cannot be read the
screens keep the vanilla background.

Previews (generated, committed): `design/lobby_preview_16x9.png`, `design/lobby_preview_21x9.png`,
`design/lobby_preview_16x9_lobby.png` (scrim + mock panel + the gothic heading and buttons),
`design/title_preview_16x9.png` (the title screen: the same scene in its title layout, design/splash.md).

## Depth rules (round 6, Oct 22) - read these before touching any layer

lovish (Oct 22): "I really want you to imitate the depths of those drawings" (his two reference drawings: the cathedral
over a field of red and ivory flowers, and the moon behind the spires with a blood river). Only their depth, density and
value structure are taken - never their composition, objects or shapes (no skull in the river, no copied towers).

1. **Many overlapping planes, each on its own parallax layer** (back to front): sky + stars + moon (0.003-0.005) -> far
   cloud banks (0.007) -> far spires (0.011) -> far hills and the forest edge (`hills_far`, 0.0125) -> near cloud wisps
   (0.012) -> the cathedral's eight layers (0.013-0.040) -> the ground plane with the river (0.02-0.062, by row) -> the
   flower fields (riding the ground) -> the framing trees (0.055), near trunks / gravestones / railing / outcrop
   (0.065), canopy (0.075) on **both** sides -> the extreme foreground (0.11-0.13). Near-black framing on both sides
   makes a tunnel; on the right it stays low or at the very edge so the cathedral is never covered (title layout too).
2. **A value ladder**: the nearest planes are the darkest, with the finest, hardest line work (`LADDER` in
   `tools/lobby_depth.py`: `frame`, `near`); each plane back is lighter, lower in contrast and lifted into a dark-red
   haze (`ground`, `mid`, `hills`, `far`); the far spires dissolve into it (`spires_grade`). The moon is the brightest
   light; the darkest darks sit next to the lit glass.
3. **Linear perspective**: the river widens toward the viewer and zig-zags back to the fall (`RIVER`, mirrored in
   `LobbyBackdrop.java`); the ground's level lines and grass tufts spread with nearness (`depth_s`); flowers grow from
   specks at the horizon to big blooms in the bottom corners, in drifts with bare ground between, crowding the banks.
4. **Engraving everywhere** (`etch()`): every surface is lines along the form whose width carries the tone, a
   second set across them in the light (cross-hatching), stipple where the tone just rises from black, every line
   wobbling and broken by the paper's tooth. No flat colour areas. Foliage is cut in leaf-sized patches, each in its own
   direction; the sky's lines ring the moon and follow the glow's contours (never level scanlines).
5. **Light**: the moon sits behind the towers (so the spires cut its disc) and a blood-red glow rises behind the
   cathedral, which reads as a dark, intricate silhouette (`cathedral_grade`) with the red glass as the focal point;
   rims of light on the edges facing the moon. **Red is the only saturated colour**; everything else is near-monochrome
   bone, grey and black.

## Layout (round 4: natural, asymmetric, in depth)

lovish (Oct 21, 22:33): "less AI-looking, natural, asymmetric" and "give the castle an ability to be 3D ... so it pops
out and comes to life". The cathedral is now **built in 3D** (world X across the facade, Y height, Z into the building)
and drawn in two-point perspective from the front-left (`proj()` in the generator: yaw 31 deg, eye just below the
gable): the near (north-west) tower is whole and bigger with its spire, the far tower foreshortened and never finished
(a jagged top, a stair turret, a medieval treadwheel crane, one belfry opening with its tracery fallen out); the north
flank recedes to the left - aisle bays of different widths, buttress piers with niches, gargoyles and pinnacles (one
snapped, one fallen and lying in the grass), flyers (one broken), a transept with its own rose and portal, the crossing
fleche and the chevet. It stands on the right third, on a rise of irregular ground; the moon sits on the upper-left
third with a far abbey's spire across it. Walls carry their own stone coursing drawn in each wall's plane.

The scene round it: a hollow on the left where mist pools, a churchyard of leaning gravestones of every shape and a
broken wall on the left slope, a scarp of rock ledges under the cathedral, the cascade from the door down the steps
and the scarp into a meandering river (uneven banks, a gravel sandbar, a fallen log, the half-sunk skull, the moon's
reflection where it bends under the moon), flowers in drifts with bare ground between (red and ivory in their own
drifts), a ruined arch and wall framing the left edge, a lone oak with a one-sided crown and a dead bough on the right.
(Round 6 redrew the ground, river, flowers, framing and foreground - see "Depth rules"; the half-sunk skull and the moon
column on the water are gone, the moon moved behind the towers, the river zig-zags back to the fall.)

Artboard: A = the cover-scaled screen height (`max(h, w / ART_W)`, ART_W = 2560/1100), x from the screen centre, y
from the top, bottom-anchored. Frames and foreground pop-outs are pinned to the screen edges.

## Depth

Every layer has a parallax depth z (A of shift per unit of camera offset). The camera drifts on a slow lissajous
(26 s across, 19 s up-down) and leans toward the mouse (`LobbyBackdrop.draw`); a constant 5 % overscan keeps the edges
covered. `design/lobby_parallax_3frames.png` shows three camera positions side by side.

| depth | layers |
| --- | --- |
| 0.003-0.012 | sky, stars, moon, far clouds, far spires and hills, near clouds |
| 0.013-0.040 | the cathedral: `cath_back` (chevet), `cath_nave`, `cath_aisle` (aisle, piers, flyers, transept), `cath_tfar`, `cath_facade`, `cath_gable` (rose plane), `cath_tnear`, `cath_portal` (archivolts in receding planes, steps, statues); each with `_rim` (moon) and `_glow` (glass) |
| between | mist sheets after the nave and the far tower; light shafts from the rose (`shafts`) |
| 0.02-0.062 | the ground as a plane: 12 strips sheared by `zg(y)` (equal to the facade at its foot, growing toward the viewer); the cascade, the river strip, the rings and both flower fields ride the same `zg` |
| 0.055-0.075 | frame trees, ruins, vines; petals, leaves and embers at their own depths |
| 0.11-0.13 | out-of-focus `fg_briar`, `fg_thorns` (bottom corners) and `fg_branch` (top left) |

Each cathedral layer is painted whole (the parts nearer layers hide are drawn too), cropped to its art; the generator
writes the rects, depths, river rocks, rose and door into `LobbyBackdrop.java` between the `<gen:cath>` markers.

Motion: the 7 s breath swells the cathedral 0.6 % about its foot, swells its rim light, glass, shafts and portal haze,
sends a wave of glass light outward from the rose and the door on each exhale and thickens the mist; a gust crosses
every 9 s and everything wind-driven reads it at its own x; cascade, river glints, moon reflection, rings, embers from
the door, bats, stars as before. `splash_reduce_motion` freezes it all (no drift, no parallax, no overscan).

## The hand-drawn look

Each painted layer is re-rendered by `hand_drawn()` as ink and coloured pencil on warm dark paper: coloured-pencil
strokes laid along the form (a structure-tensor direction field of the painting, falling back to a per-layer default
direction) that only catch the paper's raised tooth; light grey pencil hatching on the dark stone; ink hatching and
cross-hatching in 2-4 directions that layer up as the value darkens; stipple; an ink contour; white gel-pen strokes on
the brightest lit edges; the glass and the moon burnished. The painting underneath supplies the light logic: the moon
behind = silver rim on every edge facing it, the red sky = crimson fill on side-facing edges, the glass = amber spill on
the stone around it and the steps below, occlusion in every recess, soft drop shadows under ledges. "Boiling" the ink
lines (cycling stroke variants) was left out: it would double the biggest textures for little gain.

Round 5 (Oct 22): the cloud banks are drawn shapes (`drawn_clouds`): each bank laid down back to front like a pen
drawing - big domes, cauliflower knobs on their tops, low lobes along a flat belly - every lobe's upper arc inked where
it shows in front of the ones behind, the bellies hatched, the edges toward the moon caught pale, the old smoky billow
faint underneath. The moon's path on the river is a column of wider silver dashes over a soft sheen (`streak`, half
width 0.065 A, full strength), the ripples a little brighter. The ivory drifts are clumps of open anemones (ink-outlined
petals lit from the moon side, black hearts ringed with gold stamens, lance leaves, buds); the near red blooms have
ink-outlined petals.

Textured layers are saved as 256-colour paletted PNGs (Floyd-Steinberg; the pencil grain hides it; Minecraft's
NativeImage reads them as RGBA), the smooth glows stay RGBA: about 7.6 MB in all (cathedral layers included).

## Gothic lettering (`hud/GothicText.java`)

- Fonts (SIL OFL 1.1, licences beside them in `assets/reignofnether/font/`): **UnifrakturCook Bold** (unmodified,
  blackletter) and a static **Black** (wght 900) instance of **Cinzel** (Cinzel has no Reserved Font Name), from the
  google/fonts repository. Providers: `lobby_title.json` (Cinzel 15), `lobby_button.json` (blackletter 12, unused by the buttons now),
  `lobby_caps.json` (Cinzel 9.5), oversample 6 so they stay crisp at GUI scales 2-4.
- Treatment: blood-red fill in three bands (light at the top, deep crimson below), a thin gold rim, a dark shadow;
  strands of blood grow from the bottom of a few letters, swell, let a drop fall and start again on their own timing (a
  drop falls quickly while a button is hovered). Reduce motion leaves short, still strands.
- Title screen: every text button (Singleplayer, Multiplayer, Skirmish, Mods, Options, Quit...) through
  `SplashArt.restyleButtons` - Cinzel Black capitals on carved stone plates; hit areas, sizes and positions
  unchanged; the opaque stone hides the vanilla label.
- Lobby screens: the heading ("SKIRMISH", "MULTIPLAYER LOBBY") in Cinzel Black capitals (`lobby_title.json`, size 15 - the
  blackletter "k" read as "t" at that size) with a soft pulsing red glow and drips;
  the main buttons (`GothicText.LOBBY_MAIN`) in Cinzel capitals; names, tooltips, stats and the small buttons stay in
  the vanilla font.

## Motion (round 6 additions)

Candle-flicker behind every window layer (`LobbyBackdrop.candle`: three incommensurate tremors per layer and a rare
gutter, so no two windows pulse together); moonlight glints carried down the river by the current (`glints`, ten sparks,
each on its own period and lane, swelling with nearness); the cloud banks drift (wrapping textures, seamless); the
flowers, grass and trees sway in gusts that cross the scene (each strip at its own phase); bats and embers as before.
The light-shaft cone from the rose is gone (it read as a flat triangle). `splash_reduce_motion` freezes everything.

## Regenerate

`python3 tools/gen_lobby_art.py` (deterministic, about 15 minutes on two cores; the round-6 planes come from
`tools/lobby_depth.py`, which runs alone too: `python3 tools/lobby_depth.py sky clouds hills ground fields frames fg`; `--only=ground,fields` repaints some layers, `--layout`
draws a quick flat check of the cathedral geometry) paints every layer, runs the hand-drawn pass, palettes
the textured layers and writes the previews; `--previews-only` re-composes the previews. Change a rect, the river or
the cascade here and in `LobbyBackdrop.java` together.

## Licences

| asset | origin | licence |
| --- | --- | --- |
| `textures/gui/lobby/*.png`, `design/lobby_*preview*.png` | drawn procedurally by `tools/gen_lobby_art.py`; no source images | this repository's licence (NOTICE.md) |
| `font/unifrakturcook_bold.ttf` | UnifrakturCook Bold, (c) 2010 j. 'mach' wust (Reserved Font Name UnifrakturCook), (c) 2009 Peter Wiegel; unmodified | SIL OFL 1.1 (`font/unifrakturcook_OFL.txt`) |
| `font/cinzel_black.ttf` | Cinzel, (c) 2020 The Cinzel Project Authors; static instance of the variable font at weight 900 | SIL OFL 1.1 (`font/cinzel_OFL.txt`) |
