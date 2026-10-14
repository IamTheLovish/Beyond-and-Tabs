# Concept models (the box DSL)

One JSON file per unit body. The shape language is Minecraft's: axis-aligned boxes in pixels, 16 px = 1 block,
grouped under bones that rotate about a pivot. That is deliberately the same vocabulary as `ModelPart` /
Blockbench, so a model that reads well here can be typed into `bar/` as Java later without re-thinking it.
Nothing in this folder ships in the jar: these files feed `tools/render_concept.py` (Blender on GitHub Actions,
workflow `concepts`) which turns them into concept sheets on the `concepts` branch.

## Space
- Units are pixels, **y up**, ground plane at `y = 0`, the unit centred on `x = 0, z = 0`.
- The unit **faces -Z** (Blockbench "north"). The front camera therefore sits at -Z looking +Z; "side" looks from
  +X (the unit's left side) toward -X; "three-quarter" is front-left, raised.
- `x` positive is the unit's **left** (as in `HumanoidModel`, whose `rightArm` sits at negative x).
- Cube coordinates are **absolute** (not relative to the bone pivot), again as in Blockbench.

## File
(annotated; the `//` notes are not valid JSON, the real files have none)
```json
{
  "name": "Halberdier",          // caption on the sheet
  "faction": "sunforged",        // tag only; the rim light colour comes from the palette (below)
  "role": "anti-cavalry",        // caption on the sheet
  "scale_px_per_block": 16,      // optional, default 16
  "palette": { "plate": "#e9e4d8", "sun": "#ffd96a", ... },   // name -> #rrggbb, referenced by cubes
  "emissive": ["sun"],           // optional: palette names rendered as glowing (default: ["sun"])
  "bones": [
    { "name": "body", "pivot": [0, 12, 0],
      "cubes": [ { "from": [-4, 12, -2], "size": [8, 12, 4], "color": "plate" } ] },
    { "name": "head", "parent": "body", "pivot": [0, 24, 0], "rotation": [0, 0, 0],
      "cubes": [ { "from": [-4, 24, -4], "size": [8, 8, 8], "color": "plate", "inflate": 0.5 } ] }
  ],
  "pose": { "head": [-10, 0, 0] }   // optional idle pose: extra degrees per bone, added to "rotation"
}
```
- `bones[].pivot` `[x, y, z]` px: the point the bone rotates about. `parent` names another bone (any order in the
  list is fine); a bone without a parent hangs from the model root, which the turntable spins.
- `bones[].rotation` `[x, y, z]` degrees, applied in Minecraft's order (x first, then y, then z about the pivot).
  Right-handed about the axes above, so with y up a **positive x rotation swings a hanging part forward**
  (an arm at `[90, 0, 0]` points straight ahead) and a negative one tilts a body's top forward; a positive y
  rotation turns a forward-pointing part toward the unit's right (-x). A child's rotation is applied inside its
  parent's rotated frame, so a child with the opposite angle cancels the parent (a forearm that stays level
  under a swung upper arm, a lantern that hangs plumb from a tilted pole); mind that `--check` reports the
  unrotated box, so a cancelled child's cubes sit where the rest pose puts them, not where they render.
- `cubes[].from` is the min corner, `size` is `[w, h, d]`, so the box spans `from .. from + size`.
- `cubes[].inflate` grows the box by that many px on every side (Minecraft's `CubeDeformation`).
- `cubes[].rotation` `[x, y, z]` degrees and `cubes[].pivot` `[x, y, z]` px (default: the cube's centre): a cube of
  its own angle, rotated like a bone (x, then y, then z) inside its bone's frame. In the game this is a one-cube
  child `ModelPart` with that rotation - the way every articulated Minecraft creature is really built - so a fanned
  pauldron lame, a halo segment, a tilted skirt plate or a diamond blade tip costs nothing new in the engine. The
  unrotated box still counts for `--check`'s bounding box.
- `cubes[].color` must name a palette entry. Flat colours for now; textures come later.
- `palette.rim`, if present, colours the rim light; otherwise `palette.tabard` (the team colour), else white.
- `cubes[].upgrade` / `bones[].upgrade`: `"<name>"` tags the part as a researched upgrade (below). A bone's tag
  covers its cubes and its child bones; a cube's own tag wins.

## Upgrade tags (what research changes on the body)
lovish's rule: a unit upgrade changes how the unit looks - a different shape, a glowing weapon, whatever fits the
faction. In the mod (`unit/UpgradeVisuals`) every unit carries a one-byte mask of the researched looks it shows,
and the SHAPE kind means "the parts this body tagged `upgrade`". So a model may carry parts that only exist once
the research is done: tag them `"upgrade": "<name>"`, where `<name>` is the upgrade as the body knows it (one
name may cover several cubes - a crest and a sun disc are one upgrade). Base renders hide every tagged part;
a model with any tag also gets an `upgraded.png` still (the three-quarter camera, all tagged parts shown) and a
fifth panel, "upgraded", on its sheet, so the before/after sits beside the silhouette. `--check` lists the
upgrade names and how many cubes they cover. Keep the tagged parts legible at RTS zoom (>= 2 px) and let a
glowing one use an `emissive` colour, as `sunforged_halberdier.json` does with its gilded crest and the sun disc
on the blade (`sun_blessed`).

## Generate, don't type (the UNIT BAR)
`tools/gen_models.py` writes the bigger bodies from code: a tiny model library (bones, cubes, forward kinematics)
plus shape helpers - `pauldron` (fanned lames), `fauld` (flared skirt plates), `ring` (a halo of n segments),
`rays` (a sun emblem), `disc` (a round plate from stacked slabs with a rim), `blade` (a tapered blade with a
diamond tip) - and `Model.solve_arm`, which turns the upper arm and bends the forearm until the hand's rest point
lands on a target (a grip, a pommel), and `Model.ground`, which stands the feet on y = 0 after the legs are posed.
That is how a 100-cube body with real joints stays a few dozen lines, and how two hands meet on one hilt without
guessing angles. The three Sunforged Paladin directions (`sunforged_paladin_templar / _radiant / _sentinel.json`)
are its first output; the hand-typed files stay as they are.

## Preview it without Blender
`python3 tools/preview_model.py design/models/<model>.json --out /tmp/x.png` draws the posed model (every bone and
cube rotation applied) as flat-shaded panels - front, side, three-quarter, silhouette, upgraded - with PIL in a
second. It is not the concept sheet (no lighting, no rim, painter's order can misdraw overlaps) but it catches a
floating hand, a plate inside the body or a rotation with the wrong sign before the concepts workflow spends
fifteen minutes on it. Look at it before every `gh workflow run concepts.yml`.

## Check it without Blender
`python3 tools/render_concept.py --check design/models/*.json` validates every file against the rules above and
prints bone/cube counts and the bounding box in blocks (unrotated). The `concepts` workflow runs the same check
before it downloads Blender, so a typo fails in seconds, not minutes.

## Worked example: the Sunforged T1 silhouettes
`sunforged_halberdier.json` is the tallest T1 shape: a narrow upright biped whose halberd is twice the body
height with the blade up, so its black shape is a thin pole with a hook at the top. Both arms are swung about two
axes (`right_arm` `[45, -30, 0]`, `left_arm` `[80, 50, 0]`) so the fists meet in front of the chest; a two-axis
swing has no clean child counter-rotation, so the halberd hangs from `body` at the world position where both
gloves land (x -2.25, z -5.3) and the haft runs through both. `sunforged_paladin.json` is the broadest: square
pauldrons and a tower shield taller than the body, so its black shape is a slab. The shield is strapped, not
held: `left_arm` swings forward 35, `left_forearm` (its child) cancels that so the forearm lies level across the
belly, and `shield` is the forearm's child with its back face on the forearm. The legs are split at the knee for
the braced stance: a thigh bone rotated at the hip, and a shin that is a root bone standing where the knee lands,
with a knee cop hiding the joint (a counter-rotated shin would dip below `y = 0` in the unrotated box).
The other six T1 bodies keep the ladder readable at a glance: Halberdier (pole, 78 px) > Paladin (slab, 38) >
Falconer / Levy / Medic (33-35) > Squire Runner (29, leaning lower); Mounted Scout and Culverin Team are the two
horizontal outlines. Every unit carries one small sun disc (crest, shield boss, pennant, clasp, helm badge,
staff, lantern, barrel) and one `upgrade`-tagged variant of 3-4 cubes. All share the plate / gold / sun-disc
motif and the blue tabard placeholder for the team colour.
