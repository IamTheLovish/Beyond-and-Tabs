# References - what Beyond and Tabs is aiming at (shared with every lane)

Lane A keeps the full notes in lovish's claude.ai Project, which other accounts can't see; this file is the copy for every
lane's AI (Oct 21 2026). Study the references only - never copy their assets, models or builds.

## The north star (lovish, Oct 21) - every piece of work is judged against this
- **A living army:** every soldier breathes, shifts its weight and fights with real technique. A dragon's ribs rise and
  fall, and its neck rolls like a wave when it turns. Watching a single unit should feel like **The Isle**.
- **A base that becomes a city:** a capitol at the heart, every building matching its faction's style, doves circling
  the domes as the town grows. A late-game base should be worth screenshotting.
- **A world that remembers:** scorched ground, trampled grass and broken walls stay where the fight happened. Rain
  darkens the stone, and dawn fog sits in the valleys.
- **Two games in one:** command the whole war from above like **Beyond All Reason**, then drop into a hero and fight
  with combat as fluid as **Direland**'s.
- **Readable at any zoom:** one glance from the RTS camera tells you who's winning (like **Dungeons II**), while up close
  it holds up as hyper-real.
How we work because of it: per piece, ask "would this hold up next to the best game in its category?" ("pretty good for
Minecraft" fails); reviews put our stills/clips beside the best reference; if a foundation can't reach the bar, propose
rebuilding it; score honestly (1-10 vs the reference), low until earned. Aim first: animation. Standing rule beside it:
build the setting first (world, lighting, materials), then tune what lives in it.

## Best-in-category references
| Pillar | Reference |
|---|---|
| Creature / unit animation | The Isle (also Path of Titans); Day of Dragons for dragons |
| RTS command, economy, readability from above | Beyond All Reason; who's-winning-at-a-glance: Dungeons II |
| Hero melee / combat feel | Direland (note: Epic Fight / Better Combat only animate the Player entity; our possession drives a Mob, so build our own) |
| White Angel capitol | "Emberheart Castle" by SixWings (learn from its tutorials; never import the schematic; credited on the capitol's dedication stone) |
| Angel statue | Bernini's angels on the Ponte Sant'Angelo, Winged Victory of Samothrace, Augustus of Prima Porta |
| World scars / weather | per feature (Total War / Company of Heroes for battle scars; Complementary shaders for wet stone, puddles, fog) |

## Animation references (lovish's clips, Oct 21) - the checklist, points 1-28
### Part 1 - The Isle: "The Life of a Prime Elder T-Rex"
lovish sent a 97 s screen recording as THE animation reference - the north star's "living army... watching a single unit should feel like The Isle". Frames studied: the forest walk (~0:52-0:55) and the charge-and-kill on a stegosaurus (~1:06-1:09), third-person camera behind and above the rex.

#### What makes it read as alive (the checklist for every creature)
1. **Weight first.** Every step the body visibly drops and rises (vertical bob), the hips roll side to side over the planted leg, and the whole mass lags a beat behind the legs. Big = slow cadence, long stride, heavy settle. Nothing floats.
2. **Feet stay planted.** A foot on the ground does not slide while the body moves over it (foot IK / locking). On slopes and rocks the legs adapt to the ground height.
3. **Head is stabilised and leads.** The head stays steadier than the body (bird-like stabilisation) and turns *first*; the neck, then the spine, then the hips follow it into a turn - a wave travelling down the body (north star: "its neck rolls like a wave when it turns").
4. **Tail is the counterweight.** The tail lags and counter-swings: opposite to the hips each step, overshooting and settling after a turn or stop (follow-through, secondary motion on springs). It balances the head - head down, tail up.
5. **Spine curves through turns.** The body bends into an arc on a turn instead of rotating as one block.
6. **Anticipation and commitment in attacks.** Before the charge the body lowers and the head drops (anticipation); the lunge is fast and long with the head thrust forward; on contact the head clamps, shakes, and the body braces, the legs widening; the prey reacts (the stegosaurus's tail swings back in defence).
7. **Idle is never still.** Breathing (ribs/flank swell), small weight shifts between legs, head looks around, tail tip twitches.
8. **The world answers.** Grass and plants bend around the body, dust kicks up from footfalls, shadows are crisp - the creature is *in* the world.
9. **Readable silhouette from above.** From the high third-person camera (close to our RTS angle) the creature reads instantly by its outline: big head, mass at the hips, long counterweight tail.

### Part 2 - Dragons (16:42): Day of Dragons gameplay ("I Became The Largest Solo Dragon", "Becoming The LARGEST Dragon"), a fan-made "DROGON flying through the sky", and the fan-made "Battle at Rook's Rest | Dance of Dragons"
Frames studied: the red dragon's flight over the valley (~0:25), the large black dragon banking (~0:56), Drogon in flight (~1:43), plus ground poses, take-off from cliffs and the army battle.

#### Dragon checklist (adds to Part 1)
10. **Wingbeat with a real power stroke.** A big, slow downstroke with the wing fully spread and the membrane taut; on the upstroke the wrist bends and the membrane folds/cups to cut drag, the fingers trailing. The body rises on the downstroke and sinks on the upstroke (bob opposite to the wings). Large dragons beat slowly and glide between beats.
11. **Membrane is alive.** It billows and ripples in the glide, catches light at the edges (thin, back-lit), and the finger bones splay at the tip - the wing is cloth on bones, not a rigid plate.
12. **Banking turns.** The body rolls into the turn; the head stays level and looks into the turn first; the long neck makes an S-curve; the inner wing dips, the outer rises; the tail streams behind and swings as a rudder.
13. **Legs and tail in flight.** Hind legs tucked back (or hanging, ready to grab), forelegs folded; the tail trails straight in a glide and undulates gently.
14. **Ground: a wyvern stance.** On the ground the dragon walks on its hind legs and folded wing-wrists (four-point gait), wings folded tight along the body, the neck low and swinging with the stride; it rears up with wings half-spread to roar or threaten.
15. **Take-off and landing.** Take-off: a crouch (anticipation), a leap, then the heaviest downstrokes of the clip with dust thrown up; landing: wings flared wide as air-brakes, legs reaching forward, a heavy settle.
16. **Fire over an army (Rook's Rest - the most RTS-relevant shot).** A low strafing pass drawing a long line of fire across the ranks, the head sweeping sideways while the body flies straight; the fire lingers on the ground after the pass; the army scatters. Readable from above as a line, not a point.

### How it maps onto our work
- **Lane B (creature models/animation):** the Bone Dragon (#79) and Mossback Bison (#76) rigs, the Halberdier (#83). Use these lists as the review checklist. Biggest gaps to check first: creatures - weight/bob, head-leads-the-wave, tail counter-swing; dragons - the power stroke with the folding upstroke, banking roll, the four-point ground stance, and the flight state set (Part 3).
- **Lane A (engine/code director):** procedural layers on top of keyframes - foot locking/IK to ground height, head stabilisation, a spring-damper chain for neck->spine->tail (follow-through and overshoot), lean/bank into turns, hit reactions/brace, flight bob tied to the wingbeat phase, a flight-state machine (Part 3) - cheap enough for hundreds of units (LOD past the units' detail zoom). Also the dragon breath as a swept line of lingering ground fire (ties to "a world that remembers": scorch from #89). See the animation perf proof (#84).
- **Proof per change:** eyes stills / a short clip beside these references, honest score vs The Isle / Day of Dragons.

### Part 3 - Flight cycles and rigging (lovish, 16:44): "Dragon Flight Cycles" (Sophoria Academy) and "The Tyrant Dragon - the hardest rig I've ever made"
The most practical reference: a labelled set of flight cycles with the animation rig shown, plus a full dragon rig breakdown and turntable. It is effectively the list of flight states our dragons need.

#### The flight state set to build (blend between them, never snap)
17. **Normal flight:** steady flapping (Part 2's power stroke and folding upstroke), body bobbing against the wings.
18. **Glide:** wings held out and still, the membrane slightly billowed; every few seconds one corrective beat; the tail straight and the head level. Used for cruising and long distance - big dragons spend most of the air time here.
19. **Climb:** the body pitched steeply up, fast deep beats, the tail hanging low as a counterweight, the legs dangling.
20. **Left/right turn:** the body rolls into the turn; the outer wing lifts high and beats while the inner wing tucks/cups; the tail curls toward the turn side. Mirror for the other side.
21. Also needed for a game (not in the clip but implied by the set): dive (wings folded back, tail streaming), hover/flare (beating forward and down, legs reaching, as before a landing or a breath attack), take-off and landing (Part 2).

#### What the rig shows (how good dragons are built)
22. **Wings as chains with fingers:** arm (shoulder, elbow, wrist) then 3-4 finger bones per wing, the membrane skinned between them so folding the fingers folds the sail. The upstroke fold and the glide billow come from these.
23. **A long spine and neck chain plus a tail chain** with many small segments, so curves (the S-neck, the arcing spine, the curling tail) are smooth - our "wave" depends on enough segments.
24. **Separate controls per layer:** body motion (bob/roll/pitch), wing cycle, neck/head aim, tail - layered so a turn, a breath and a wingbeat can play at once. This matches our procedural-layers plan (Lane A).

#### For our Bone Dragon (#79)
The checklist to hold it to: does it have the glide, climb and turn states and blend between them; do the wings fold on the upstroke through finger bones; are the neck and tail chains long enough to curve; can head aim / breath play over flight. Engine side (Lane A): a flight-state machine driven by the unit's movement (speed, climb rate, turn rate) choosing and blending the cycles, plus the body bob/roll layers.
### Part 4 - How the pros build cycles: Sophoria Academy "Dinosaur (Irex) Walk Cycle" (slowed, frame-numbered), "Advanced Dragon Rig for Maya", "Spider Walk Cycle Tutorial"
Tutorial-level references: how good walk cycles and rigs are made, not just what they look like.

25. **Walk cycles are built from four key poses per step:** contact (heel strikes, stride widest), down (weight lands, body lowest, knee bends), passing (the free leg swings under, body rising), up (push-off, body highest) - then mirrored for the other leg. The Irex clip numbers the frames: a big theropod step takes ~12+ frames at 24 fps (about half a second or more) - slow and heavy. Our cycles should be checked pose by pose against this, not just "legs move".
26. **Smooth curves and offsets, not linear keys.** The graph editor shots show eased curves, and each body part offset in time (hips first, then chest, then neck, then head; the tail later still) - that offset IS the wave/follow-through from Part 1. Linear or in-sync motion reads robotic.
27. **Feet grip the terrain.** The dragon rig clip shows claws planted and wrapping over rock edges (foot IK per toe group) - creatures adapt to uneven ground instead of floating or clipping.
28. **Many-legged gaits use phase offsets.** The spider moves in alternating sets (left 1 + right 2 + left 3 + right 4 together, then the other four), the body gently bobbing and swaying with each set; legs lift high in an arc, reach, plant. This is the template for any multi-legged unit - e.g. the Hivebound swarm (Zerg-like) faction's spiders/crawlers.

#### Engine note (Lane A)
A generic gait system can drive all of this from data: per-leg phase offsets, a stride length/time from the unit's speed, foot planting via IK to the ground height, body bob/sway from the leg phases, and part-by-part time offsets for the follow-through - one system for bipeds, quadrupeds, dragons (four-point ground stance) and many-legged swarm units.

## Buildings: White Angel direction
- Gothic spired castle-cathedral on a wild rock crag; warm light grey stone (lovish's pick), warm brick spires, slate
  roofs; every bay framed by a buttress pier with a pinnacle, tall pointed lancets under small gables, two storeys of
  bays; towers that look real (taper, battered base, quoins, corbelled crowns); lit windows on every storey.
- Buildings grow over six levels into a reserved footprint (L1 small -> L6 large and intricate). Interiors allowed;
  every walkable space at least 4 wide x 8 tall or sealed.
- Custom White Angel blocks (ashlar, tracery windows, finials, the dedication stone) where vanilla shapes fall short.
- The angel statue is a smooth sculpted mesh (sculpt -> .bmesh import, statue mesh drawing; PR #86), facing the
  capitol's front.

## A world that remembers (spec: slices, in order)
1 persistent scorch and craters (PR #89); 2 routes worn by armies; 3 ruins - rubble and wall stumps until rebuilt;
4 rain wetness and puddles; 5 dawn valley fog + a Dawn start time; 6 faction stains. Defaults lovish accepted: scars
last the whole match (fading to ~30%), purely visual, walkable; subtle blood with an Off setting; lobby weather
option Clear / Changing / Stormy. Complementary ignores LabPBR porosity and ships with puddles off.

## Tooling every lane uses now (on main)
`tools/claim.py` + issue #91 (claim before starting), `tools/ci_wait.py --pr N` (wait for CI quietly),
`bash tools/prepush.sh` (CI's fast checks in ~35 s), `.github/workflows/flake-stress.yml` (run chosen tests on N
runners for a real fail rate). See the agent checklist in AGENTS.md.

## How we make animations and textures (the process, Oct 21)
Every art change follows: **reference -> build -> render offline -> critique against the reference and
design/ART-FAULTS.md -> fix -> repeat -> prove in the real game (eyes) -> lovish judges taste.** Rounds keep going until a
full critique cycle finds nothing significant; each round is scored 1-10 against the reference in the PR body.

**Unit and creature models** (details: `design/models/README.md`, AGENTS.md repo map)
1. Concept: a model in our own cube DSL (`design/models/*.json`, shape helpers in `tools/gen_models.py`); Blender 4.2 on CI
   renders concept sheets (`concepts.yml`); lovish sees the sheet first.
2. Concept -> game: `tools/dsl_to_game.py` generates the ModelPart Java (`unit/modelling/models/gen/`) and a painted atlas
   (realism pass: materials.REALISM, baked light/occlusion from `tools/unit_light.py`); `tools/game_preview.py` draws the
   game's look offline.
3. Smooth/sculpted bodies: the mesh path (PR #6 + #86) - a sculpt (SDF/Blender/OBJ) is imported to `.bmesh` and drawn
   through the same ModelPart tree (team colour, glow, animation, mipmaps, Oculus LabPBR keep working), with LODs and the
   cube model as fallback.
4. Rigs: articulated rigs per body type - quadruped (#76: legs, spine, neck), dragon (#79: neck, legs, wings, tail),
   shared human base clips (#99).

**Animation** (Lane B owns the clips; Lane A the engine layers)
1. Keys as data: `design/anims/<unit>.json` (clips with ticks, hit point, loop, phase; held poses). `tools/anim_lab.py`
   plays the same data the game plays: `--motion` measures every clip (stops, snaps, broken joints; exit 1 on a FAIL),
   `--play` writes frame strips/GIFs per view, `--blend` shows the game's blends, `--gen-java` writes the model's key
   table and `--check` (CI) proves the table equals the data. Interpolation: monotone cubic Hermite (no overshoot).
2. Procedural layers on top of the keys: the shared Life layer (#72: breathing, idle life); planned engine layers - foot
   locking/IK to the ground, head stabilisation, a spring chain neck->spine->tail, lean/bank into turns, a brace on hit,
   a flight-state machine (normal/glide/climb/turn/dive/hover) for dragons, one data-driven gait system for 2-, 4- and
   many-legged units. Budget: hundreds of units (the animation perf proof, #84; LOD past the units' detail zoom).
3. Judge against the animation checklist above (points 1-28), side by side with the reference frames.

**Textures** (the realistic block pack, Lane B)
1. Procedural, never copied: `tools/gen_blocks.py` generates 128x (256x for leaves, bark, grass top) textures with
   LabPBR 1.3 normal/specular maps (`_n` / `_s`) into `resourcepacks/BeyondAndTabs-Realistic` (generated by CI, not in
   git); `tools/materials.py` is the one LabPBR encoder and `check_labpbr` gate. Rules: new textures at the END of
   TARGETS and SEED_ORDER; cut-out blocks need `cutout_mipped` overrides; keep rotated textures isotropic.
2. Judge at the RTS scale, not flat: `rts_view` (~36 px per block) and `face_census.py` (what the RTS camera actually sees
   on our buildings); calm large surfaces, detail on edges and focal points, no "pepper".
3. Prove in game: the eyes `structure` lane stamps buildings on a pad and shoots them four ways (RTS, RTS close,
   first-person 3/4, dusk), plain and with shaders (Oculus + Complementary, LabPBR); a fresh reviewer checks the stills.
4. Never copy, extract or recolour other mods' textures/models (Macaw's, Chipped are All Rights Reserved; the repo is
   public).

**Proof and review for any visual change:** eyes stills (and 10 s clips) from the real client on CI (`eyes.yml`), a
reviewer pass against `design/ART-FAULTS.md`, the honest score vs the reference, then lovish's taste call. Every fault he
catches is added to ART-FAULTS so it never comes back.
