# Study: lovish's part references, measured (Oct 28, session E)

lovish sent ref39-ref44 (16:58-17:00) after "more block variety ... more dimensions and architecture details", then
asked to study while round 1 builds (18:14). Each picture measured on its pixels where its resolution allows
(`tools/colour_shares.py`, autocorrelation of row / column gradients for rhythm), read by eye where it is a thumbnail.
ref18 stays the Engine Shed's primary; these numbers say HOW to get relief and variety at T1 size.

## What each picture measures
| Ref | Measured | Reading |
|---|---|---|
| ref44 layered gothic front (963 x 768, signed: study only) | block ~5 px; vertical members repeat ~60-64 px (a ~12-block bay); horizontal courses ~33 px (every 6-7 blocks), finer 14-19 px; luma p10/50/90 = 8 / 63 / 140 | One material family. The value range comes from VOIDS (deep black windows, the open gallery) and shadow under courses, not from changing material. Piers are bundles: a broad pilaster with a 1-block inset shaft and narrower flanking shafts = 3 depth planes on one pier. At every course the pier swells 1 block each side (a "knot", like a capital). Windows 2 wide, very tall, pointed, set 2 deep in a double frame. Pinnacles on every pier top. |
| ref41 nave (1920 x 1280) | column shafts luma median 64-78; dark brick ribs 35-47; vault web 85 | Frame vs body by VALUE (a ~0.12 step on 0-1) AND texture (ribs are rough bricks, wider p10-p90 spread; shafts smooth). Pointed arches stepped from stairs. One warm light source per bay (chandeliers on chains). |
| ref43 dark castle (145 x 120 thumb) | warm lit pixels 4.6 % of the building | Light from tall lit openings only; towers of different heights with steep spired caps; paler trim on dark stone. |
| ref42 pagoda (75 x 135 thumb) | warm 20 % (sunset light on wood, not glow) | Each tier: a deep eave overhang on a bracket course, a railing line, then the next tier set back. A tall brass finial. |
| ref39 temple (182 x 182 thumb) | - | Columns = base + shaft + capital, standing proud of a recessed wall; a frieze band; a pediment with a relief emblem; a stepped base. |
| ref40 gothic front (164 x 180 thumb) | - | Buttresses capped with pinnacles; clustered spires of different heights; every face in 3-4 depth planes. |

## Translated to the Engine Shed T1 (17 x 25, h 26)
ref44 is palace scale (~150 blocks tall); T1 is 26. Halve its rhythms, keep its ratios.
1. **Courses every 3-4 rows** on the tower and wing faces (ref44: 6-7 at palace scale): y 6 (the lintel), then about
   y 10, 13-14, 17, 20-21 on the tower. A course projects 1 (a slab or upside-down stair) with a corbel row under it.
2. **Piers as bundles, 3 planes**: the pier face at x 0, the bay frame at x 1, the bay back at x 2. Base (stair) and
   capital (upside-down stair) at the course lines - the "knot".
3. **Value from voids**: openings 2 deep and dark (the backs cracked blackstone bricks), not dark paint on a flat wall.
   The front's dark share (ref18: 35 %) should come mostly from recesses and shadow lines.
4. **Frame vs body step >= 0.10 luma**: polished blackstone bricks (0.19) framing red terracotta (0.29) or mangrove
   (0.30) is the nave's two-tone in our palette; grey trim (polished andesite 0.49) only on caps and courses.
5. **Texture carries variety inside a role**: rough frames (bricks), smooth fields (terracotta, polished), grained roofs
   (mangrove) - so a role keeps 3+ materials without speckle (`tools/facade_audit.py` counts materials and speckle).
6. **Glow <= ~5 % of the front, from openings**: the door head, one lit slot in the tower, small lights over bays.
7. **Every roof tier overhangs 1 on a bracket row** (upside-down stairs under the drip); a brass finial on the tower
   point and the wing ridge ends.

## For the critic (round 1) and builder (round 2)
These are not new rubric items (the checklist is fixed for the round); they are how to FIX a region the checklist marks
low on relief or variety. Measure before claiming: course spacing by counting rows on the render, value steps with
`tools/colour_shares.py IMAGE X0,Y0,X1,Y1` (medians), glow share as the warm-lit pixel share on the in-game still.
