# Handoff: Itachi 2, session B (vehicle labs + art method), Oct 28 ~04:40 ET

The next session starts here. Repo `IamTheLovish/Beyond-and-Tabs`, branch **`claude/vehicle-labs`** (everything below is
pushed there). First message to lovish: the runner command only (`~/start-runners.sh`), per his standing rule.

## Read first
1. `design/vehicle_labs/LESSONS.md` (also Project doc `claude/vehicle-labs-lessons-2026-10-28.md`) - why this
   session's art stalled and the method that replaces it. lovish: "disappointing we used a lot of usage for little
   progress", "I really want you to improve your abilities in making these buildings".
2. The art-director skill: a rewritten `bat-art-director` was proposed to lovish as a card (a "STOP - the building
   method that works" checklist at the top). Check whether he saved it; if not, the same content is in LESSONS.md.
3. `design/refs/vehicle_labs/` - lovish's 38 reference images + `README.md` (what each teaches, which faction it is the
   PRIMARY reference for).

## Decisions lovish made this session (all recorded on the branch)
- **The method** (LESSONS.md): ONE primary reference per building, imitated region by region (like
  `tools/capitol_white_angel_v4.py`); proportion before detail; a concrete massing plan with coordinates first; a
  GREYBOX stage lovish approves before any detail; short rules; one pilot to 7+/10 before rolling out; max two heavy
  agents at once; never restart a round mid-way; **Opus builds, a fresh Fable critic scores** each round.
- **Size ladder**: "tier 1 buildings should be small and get larger and larger per tier and tech level", "bigger AND
  more intricate as they level", "not oversized". Starting numbers (his to adjust): T1 up to ~13x21, L1 <= 12, top <= 22;
  T2 ~21x27, <= 32; T3 ~29x29, <= 45; T4 larger (unsized); capitol v4 59x59, ~100 at L6.
- **Four building tiers**; T1 and T2 buildings can be **refit** later into the higher tiers' architecture so a late base
  reads as one city (a refit = another superset level on the same footprint).
- **Tidewrought is DROPPED** (water is hard in Minecraft and was only a costume). A new **AI faction** replaces it:
  drones, robots and mecha, inspired by Beyond All Reason and the TABS Neon faction, "like a Tron Legacy faction" (glossy
  black, glowing light lines; inspiration only), distinct from Brassworks. Its name, roster, look references and start
  are NOT decided. Record: `design/faction-change-tidewrought.md` (+ Project doc). Keep Tidewrought playable until the
  replacement can be selected. The Careening Hard vehicle lab is cancelled.
- Spawn rule (from code): units spawn at the building box centre and drop down
  (`ProductionBuilding.getIndoorSpawnPoint`) - the column over the spawn cell must be air at every level.

## Open questions for lovish (ask, don't assume)
1. What is tier 4 (a new tech level, or the game-ender buildings)?
2. What triggers a T1/T2 refit: the tech research itself, or a paid upgrade per building?
3. The AI faction: reference pictures, name, roster/roles, when its lane starts.
4. The vehicle-labs design questions in `design/vehicle_labs.md` section 5 (T2 vehicle plants, opening with the vehicle
   lab, which T1 lab unlocks T2, Sunforged/Gravebound vehicles, which units count as vehicles) - still unanswered.

## Work that was running at handoff (check, then continue or re-run)
| What | Model | Brief | Output | Next |
|---|---|---|---|---|
| **Pilot**: Brassworks Engine Shed imitating **ref18** (red-brick hall + central tower), T1 size | Opus builder | `design/vehicle_labs/briefs/brass_pilot.md` (incl. the 3 changes: two levels, T1 size 13x21 / L3 <= 22, greybox first) | `design/vehicle_labs/brass_l3_greybox.nbt` + `brass_greybox_vs_ref.png`, then brass_l1/l3 | It was told to STOP at the greybox. Show lovish the greybox comparison; only after his OK, detail it, then one fresh Fable critique. If the files are not on the branch, the old session ended first: re-run the brief with an Opus builder. |
| **Tools** (DONE, committed d625e9d7): `tools/structure_render.py --light` (AO + low sun shadows, eyes-rig sun; ~2 s per building), `tools/ref_compare.py` now lit by default (`--flat` for the old look), `tools/shape_kit.py` (SDF domes/spires/cones/tori/lofts/curved roofs/ribs, CSG, stair/slab edge pass, hollow shells; `build()` into a gothic_parts Part or a Structure) + tests | Opus builder | `design/vehicle_labs/briefs/tools_light_shapekit.md` | committed | Known limits: shallow curves still show stair teeth, domes blocky at 32 px, ribbed dome ribs patchy, no outer-corner stair shapes until `to_rig()`. Worth a follow-up pass before the domed labs. |

Nothing else is running. (At 04:5x the pilot had written `brass_l3_greybox.nbt`, uncommitted.) The other labs are paused until the pilot reaches 7+.

## Agreed next improvements (lovish: "sounds good, add this")
1. Greybox stage approved by lovish (in the pilot now).
2. Lighting in the offline renderer (tools brief) - and final checks in game via the eyes lane with shaders.
3. Shape kit for curves (tools brief).
4. Polished reusable parts first (windows, arches, spires to ref34's standard), reviewed close up.
5. Cheap automatic checks before any critic (squint-shape similarity vs the reference, flat-wall and scattered-light
   detectors).
6. A palette picker (sample a reference's colours -> nearest blocks in our pack).
lovish then asked "what else can we do" - answer that next (ideas not yet given: lovish/Joisco build a hero piece by
hand in creative and Claude turns their own build into the structure script; Macaw's/Chipped shapes for more detail;
higher-resolution references).

## State of the seven lab concepts (scores in `design/vehicle_labs/SCORES.md`)
All 3-4/10 from fresh critics after rounds 1-2. Primary references for the roll-out after the pilot: Brassworks ref18;
Sunforged ref05 + ref10; Gravebound ref14 (alt ref32); Ashborn ref30 at L1 growing to ref29; Verdant ref06;
Stormcallers ref07 + ref08; (Tidewrought cancelled). Fix lists for the next round of each are in
`design/vehicle_labs/briefs/` (written for the old 17x17 method - rewrite them to the new method and the T1 size).
Gravebound/Verdant part libraries may hold half-finished round-2 edits (they compile).

## Tools added this session
- `tools/ref_compare.py`: building at --px 32 beside its reference at the same height + a blurred greyscale "squint"
  row. Run it every pass.

## Untouched this session - carried over from `claude/handoff-2026-10-28-itachi2.md`
Items 2-6 there (#295 save hitch stress run 40626428525, T2 site-search flake waiting on #203, #299 bombard dead-zone,
#131 generated maps Mode A review, #303 breath strobe) were NOT looked at in this session. Check their live state with
`gh api` before acting. Note: the Horde vehicle lab (Ember Pens) drops the Hoglin Stables' nether-ground rule, one
cause of the T2 site-search flake.

## LEARN
- Imitate ONE reference; blending many makes a generic box.
- Judge at --px 32 beside the reference; squint before detail.
- A greybox lovish approves costs minutes and saves rounds.
- Seven parallel builders multiply every mistake; pilot first, max two heavy agents.
- Don't stop a near-finished round: the spend is sunk, finishing it is cheap; change course between rounds.
- Subagents cannot spawn critics; the main session runs them. Check critic suggestions against the rules
  (`preview_structure --rts` mirrors x, which confused critics about which face the gate was on).
