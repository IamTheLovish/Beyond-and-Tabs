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
`design/lobby_text_preview_title.png` (the title screen's buttons in the new lettering).

## Layout

One artboard in A units: A = the cover-scaled screen height (`max(h, w / ART_W)`, ART_W = 2560/1100 = 2.327, about
21:9), x from the screen centre, y from the top, bottom-anchored (an ultra-wide screen loses a sliver of sky, never the
ground). The framing layers are pinned to the screen's left and right edges instead, so 16:9 and 21:9 both keep their
framing. Every rect, the river centreline, the cascade and the rocks in the river are mirrored in `LobbyBackdrop.java`.

## Layers (back to front) - `assets/reignofnether/textures/gui/lobby/`

| layer | what | motion |
| --- | --- | --- |
| `sky` | near-black top to a blood-red horizon, cool blue-grey low at the sides, faint stars, warm haze round the moon | mouse parallax |
| `sparkle` x14 | brighter stars | twinkle |
| `glow` + `moon` | bone-white moon with maria, craters and engraved hatching, a warm halo | halo breathes |
| `clouds_far`, `clouds_near` | engraved red cloud banks, periodic in x | drift at two speeds, faster in gusts |
| `spires_far` | distant cool-grey churches and needles in the haze | parallax |
| `cathedral` | the facade: towers (the right spire broken), needles, gables with crockets and trefoils, flying buttresses, gargoyles, corbel tables, statue niches, gallery of kings, rose window, three lancets with a haloed figure, a sculpted tympanum over the red-lit door, ivy | swells 0.6 % on the breath |
| `cathedral_rim` (add) | the silver moon rim light alone | swells and fades on the breath |
| `glass_rose`, `glass_lancets` (add) | the glass's glow | pulse out of step, swell on the breath |
| `glow` (add) | warm haze breathing out of the portal | breath |
| `mist` | rolling mist at the cathedral's foot | drifts, thickens on the breath, streams in gusts |
| `ground` | the plinth's steps, stepped rock ledges, the S-curved crimson river with wet rocks, foam, the moon sheen, a half-sunk skull, glass light on the steps, contact shadows | - |
| `cascade` | the red fall from the doorway down the steps (wraps in v) | falls continuously |
| `ripples`, `streak` (add) | river overlays on a 32-quad strip along the river: glints flowing toward the viewer (perspective-mapped), the moon's reflection fixed in a column under the moon | flow; reflection shimmers |
| `glow`, `ring` (add) | foam at the foot of the fall; rings spreading from the rocks in the stream | churn; rings now and then |
| `field_far`, `field_near` | red blooms (lighter rims, dark hearts) and ivory umbels lining both banks, big single blooms in front, tiny far off, briars in the front corners | 16 strips each: a gentle travelling sway and a wave behind each gust |
| `bats` (3 frames) | 2-4 bats crossing the sky every 26 s | flap |
| `frame_*_tree` | gnarled etched trees with clumped copper foliage | lean with the wind (anchored at the root) |
| `frame_left`, `frame_right` | chipped masonry cliff, a broken pointed arch on a clustered pier, a ruined wall with an empty lancet, rubble, flowers | still |
| `frame_*_vines` | vines hanging from the overhang and the arch | swing (anchored at the top), trailing the gust |
| `petal`, `leaf` x16 | petals and copper leaves blowing across | gusts lift and hurry them |
| `vignette`, `grain` | dark edges; paper grain at screen resolution (tiled) | - |

Motion clocks: the **breath** is a 7 s inhale/exhale (`BREATH_S`); a **gust** front crosses left to right every 9 s
(`GUST_S`) and everything wind-driven reads `gust(x)` at its own x, so the trees, vines, flowers and petals move in a
wave as it passes; the very slow 48 s push-in of the whole scene and the mouse parallax by depth sit on top. All of it
is derived from the clock (no per-frame state besides the smoothed mouse), with no allocation per frame: ~70 textured
quads through the shared Tesselator. `splash_reduce_motion = true` (config/reignofnether-client.toml) freezes it all at a
fixed moment (no bats, petals, rings or parallax).

## The hand-drawn look

Each painted layer is re-rendered by `hand_drawn()` as ink and coloured pencil on warm dark paper: coloured-pencil
strokes laid along the form (a structure-tensor direction field of the painting, falling back to a per-layer default
direction) that only catch the paper's raised tooth; light grey pencil hatching on the dark stone; ink hatching and
cross-hatching in 2-4 directions that layer up as the value darkens; stipple; an ink contour; white gel-pen strokes on
the brightest lit edges; the glass and the moon burnished. The painting underneath supplies the light logic: the moon
behind = silver rim on every edge facing it, the red sky = crimson fill on side-facing edges, the glass = amber spill on
the stone around it and the steps below, occlusion in every recess, soft drop shadows under ledges. "Boiling" the ink
lines (cycling stroke variants) was left out: it would double the biggest textures for little gain.

Textured layers are saved as 256-colour paletted PNGs (Floyd-Steinberg; the pencil grain hides it; Minecraft's
NativeImage reads them as RGBA), the smooth glows stay RGBA: about 3.6 MB in all.

## Gothic lettering (`hud/GothicText.java`)

- Fonts (SIL OFL 1.1, licences beside them in `assets/reignofnether/font/`): **UnifrakturCook Bold** (unmodified,
  blackletter) and a static **Black** (wght 900) instance of **Cinzel** (Cinzel has no Reserved Font Name), from the
  google/fonts repository. Providers: `lobby_title.json` (blackletter 16), `lobby_button.json` (blackletter 12),
  `lobby_caps.json` (Cinzel 9.5), oversample 6 so they stay crisp at GUI scales 2-4.
- Treatment: blood-red fill in three bands (light at the top, deep crimson below), a thin gold rim, a dark shadow;
  strands of blood grow from the bottom of a few letters, swell, let a drop fall and start again on their own timing (a
  drop falls quickly while a button is hovered). Reduce motion leaves short, still strands.
- Title screen: every text button (Singleplayer, Multiplayer, Skirmish, Mods, Options, Quit...) through
  `SplashArt.restyleButtons` - blackletter when it fits, Cinzel capitals otherwise; hit areas, sizes and positions
  unchanged; the opaque stone hides the vanilla label.
- Lobby screens: the heading ("Skirmish", "Multiplayer Lobby") in blackletter with a soft pulsing red glow and drips;
  the main buttons (`GothicText.LOBBY_MAIN`) in Cinzel capitals; names, tooltips, stats and the small buttons stay in
  the vanilla font.

## Regenerate

`python3 tools/gen_lobby_art.py` (deterministic, about 4 minutes) paints every layer, runs the hand-drawn pass, palettes
the textured layers and writes the previews; `--previews-only` re-composes the previews. Change a rect, the river or
the cascade here and in `LobbyBackdrop.java` together.

## Licences

| asset | origin | licence |
| --- | --- | --- |
| `textures/gui/lobby/*.png`, `design/lobby_*preview*.png` | drawn procedurally by `tools/gen_lobby_art.py`; no source images | this repository's licence (NOTICE.md) |
| `font/unifrakturcook_bold.ttf` | UnifrakturCook Bold, (c) 2010 j. 'mach' wust (Reserved Font Name UnifrakturCook), (c) 2009 Peter Wiegel; unmodified | SIL OFL 1.1 (`font/unifrakturcook_OFL.txt`) |
| `font/cinzel_black.ttf` | Cinzel, (c) 2020 The Cinzel Project Authors; static instance of the variable font at weight 900 | SIL OFL 1.1 (`font/cinzel_OFL.txt`) |
