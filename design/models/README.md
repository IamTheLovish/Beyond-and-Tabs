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
- `cubes[].from` is the min corner, `size` is `[w, h, d]`, so the box spans `from .. from + size`.
- `cubes[].inflate` grows the box by that many px on every side (Minecraft's `CubeDeformation`).
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

## Check it without Blender
`python3 tools/render_concept.py --check design/models/*.json` validates every file against the rules above and
prints bone/cube counts and the bounding box in blocks (unrotated). The `concepts` workflow runs the same check
before it downloads Blender, so a typo fails in seconds, not minutes.

## Worked example: the two Sunforged T1 silhouettes
`sunforged_halberdier.json` is the tallest T1 shape: a narrow upright biped whose halberd (bone `halberd`,
parented to `right_arm`, pivot at the fist) is twice the body height with the blade up, so its black shape is a
thin pole with a hook at the top. `sunforged_paladin.json` is the broadest: square pauldrons and a tower shield
(bone `shield`, parented to `body`, held in front of the chest) that is taller than the body, so its black shape
is a slab. Both share the plate / gold / sun-disc motif and the blue tabard placeholder for the team colour.
