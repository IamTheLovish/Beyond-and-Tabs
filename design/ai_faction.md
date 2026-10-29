# The AI faction (replaces Tidewrought) - the Light Grid, keep/drop list, caster, first slice

**Status: design only. lovish picked the theme on Oct 29; this revision is for his OK before any code.**
Working name: **Lumen** (placeholder, lovish: "placeholder is fine").

## 0. Decisions

- Oct 28 (via Command Center Rem; `design/faction-change-tidewrought.md` on branch `claude/vehicle-labs`, PR 306): drop
  Tidewrought and add an AI faction: *"drones and robots, kinda inspired by Beyond All Reason and the neon faction from
  T.A.B.S"*, *"kinda like a Tron Legacy faction with AI robots and mecha"*. Glossy black and dark-grey bodies, clean
  hard geometry, thin glowing light lines on every edge, light as the only ornament. Inspired by Tron, never copied: no
  logos, characters, light cycles or other recognisable designs. Not Brassworks. The Corsair Captain moves to the
  neutral hero pool. Keep the faction playable while it is replaced.
- Oct 28 (PR 310 `design/proposals/vehicles.md` 3.8): the futuristic vehicle slot is Hover Skiff, Line Tank, Rail
  Lancer, Flak Tank, Lifter Drone. Capitol direction: ring-gate towers (`design/LANES.md`); look reference ref37.
- Oct 29: *"tidewrought is getting replaced by an ai faction"*, *"there isnt water"*. Then, via Command Center Rust:
  1. **Theme: Light Grid** (option A of the first revision).
  2. **Tidewrought content: "keep what makes sense"**: keep and reskin the mechanics that fit the Light Grid, drop the
     rest. The choices are in section 3.
  3. **Switch-over: convert in place, slice by slice**, so the faction stays playable.
  4. **Caster: "the ai caster could be a terminator that evolves"**: a relentless machine hunter that upgrades itself.
     It replaces the earlier Rez Weaver (section 4).
  5. Name: placeholder.

The two options not picked (Nexus link network, Merge Frames) are in the PR's first commit (`7bd2cda0`). Merge could
still come back later as a T3 trick.

### Rules the faction keeps

| Rule | Why |
|---|---|
| Clean, digital, lit: glossy black/grey + one or two team light colours. No gears, steam, rivets, brass, Tesla coils. | Brassworks owns steam machines, the Tesla Walker and the Shield Projector. |
| Machines, not creatures. The grid is a buff tile, never creep you must build on. No eggs, no organic swarm. | Hivebound (planned) is the alien swarm with a creep placement rule. |
| Nothing needs water, and water gives no bonus. | The maps are dry. |
| Same roles and prices as the other factions at each tier. | BAR readability; `tools/unit_stats.py` stays comparable. |
| The shared defence set (`design/defences.md`) in this look, plus one own T2 tower. | One shape and one price per job. |
| Death style: units **derez** into falling light shards and a fading outline. | Replaces "dissolve into seawater" (the unit-animation lane's per-faction deaths). |
| Light lines come from an emissive render layer (like vanilla's spider-eyes layer) over a dark glossy skin. | Cheap, readable from the RTS camera by day and night, works on any body. |

---

## 1. The Light Grid

**Identity:** programs made of light that draw their own battlefield. Glowing grid lines spread across the ground
wherever they go, and on the grid they are fast, shielded and hard to hide from.

**Grid tiles** are flat light panels on the ground: thin, no collision, walkable by everyone, never placed on
buildings, metal patches or other tiles. They are today's tidepools (`TidepoolBlock` + `TidepoolServerEvents`) with a
new look and one new rule.
- The Builder Drone, Grid Weaver, Architect Drone and commander draw temporary patches (radius 2-3, about 30 s), up to
  4 per player. The Titan leaves a capped trail. These are today's raise / great raise / trail calls.
- *(Slice 3+)* Buildings hold a small permanent grid round themselves while powered (radius 3-5 by size).
- On its own grid a Lumen unit gets **+20% speed and 2 HP/s regen**, both per-unit tunable (the Tides buff).
- **New: enemies standing on your grid lose their cover** (thicket, Fog Bank / Cloak Field, Shade Ranger cloak) every
  second they stand on it. The grid is a sensor.
- Counterplay (as today): any constructor's reclaim order derezzes a whole patch in 2 s, and fire and explosions break
  the cells they hit.

**Economy twist: energy-led.** Units and buildings cost about 20% more energy and 20% less metal than the other
factions (light is energy), and drawing a patch costs energy. The faction wants wind/solar early, and energy raids
hurt it most. Numbers are set in the slice that reprices each unit, not in slice 1.

---

## 2. Roster

| Role | Unit | Job and mechanic | From (section 3) |
|---|---|---|---|
| Commander | **Prime Program** (commander mech) | D-gun **Derez Beam**: a line of 6 light impacts, entity damage only. Ability **Grid Pulse**: a cone shove, x2 on foes on its grid; leaves a patch. | Admiral: Broadside + Riptide |
| T1 worker | **Builder Drone** | hover constructor; draws grid patches. | Shipwright |
| T1 scout | **Seeker Drone** | fast low flyer, sight 26, no attack. | Gull Spotter |
| T1 raider | **Light Blade** | energy-sword bot; *Phase Strike*: stepping off the grid primes +40% on the next hit within 3 s. | Cutlass Raider (Ambush) |
| T1 line | **Bulwark Frame** | shield bot, ranged resist; 3 HP/s on the grid. | Reef Guard |
| T1 ranged | **Pulse Gunner** | energy-pistol trooper. | new |
| T1 puller | **Grav Tether** | every 3rd shot pulls the target 6 blocks (TABS Neon's gravity unit). | Harpooner |
| T1 support | **Grid Weaver** | follows the army, lays a patch under it before a fight. | Tide Priest |
| T1 AA | **Flak Drone** | x3 vs flyers, x0.5 vs ground. | Flak Swivel |
| T1 siege | **Arc Mortar** | plasma lob, min range, splash, dents buildings. | Bombard Crew |
| T1 vehicle | **Hover Skiff** | fast hover raider, +60% speed on the grid. | Mudskipper Sloop |
| T2 worker | **Architect Drone** | 2x build power; big patch (r3). | Harbourmaster |
| T2 control | **Gravity Lancer** | tractor beam: drag 4 + root 2 s; stronger if either side is on the grid. | Kraken Caller |
| T2 artillery | **Rail Lancer** | deploys; one piercing shot at 28. | Mortar Galley's role |
| T2 bomber | **Glider Bomber** | 3 charges in a line. | Sky Corsair |
| T2 heavy | **Bastion Walker** | deploys into a battery: half damage, a grid round it, can't move. | Shellback Turtle (Moor) |
| T2 beamer | **Beam Walker** | long continuous beam (TABS Neon beamer), weak up close. | new |
| T2 caster | **Hunter Frame** | the evolving essence caster (section 4). | replaces Tide Seer |
| T3 | **Titan Frame** | giant mech; grid trail; *Shockwave* arc slam; *Phase Shift* from its grid to a spot behind the front. Weak: no AA, slow, x1.75 from siege. | Leviathan |
| Hero | **Blade Master** | energy-sword master (TABS Neon), dashes along the grid. | new (Corsair goes neutral) |
| Combat engineer | **Patch Drone** | field repair and field defences. | Salvager |
| Game ender | **Mainframe Upload** | charges only while its ring of grid nodes is lit: break nodes to slow it, all to stop it. | Maelstrom Engine |

**Buildings:** capitol **Core Ring** (ring-gate towers round a glass spire), T1 lab **Compiler**, T2 lab **Foundry
Matrix**, T3 lab **Titan Gate**, vehicle lab **Hover Bay** (with the vehicle-lab lane), economy in a `"_grid"`
palette (black concrete, polished deepslate, tinted glass, the block pack's emissive strips).

**Defence line:** Light Wall, Light Gate, Feedback Mine + Stasis Snare, Pulse Turret (anti-raid), Lance Battery
(anti-T3), Sky Watch, Flak and the anti-siege tower (PR 359), all in the Lumen look. Own T2 tower **Scanner Spire**: a
turning sensor cone that strips cover plus a light gun. It is the Lighthouse Battery's code.

---

## 3. Keep / drop (lovish: "keep what makes sense")

**Rule:** keep a mechanic if it works on dry land and reads as light or machine. Drop anything that exists only for
water or for a sea-creature body. Bodies always change (none of Tidewrought's bodies reads as a robot).

### Kept and reskinned

| Tidewrought piece | Becomes | Why it stays |
|---|---|---|
| Tides core: tidepool block, registry, caps, fail-safe ticks, drain/evaporate, `KIND_TIDEPOOL` pathing, the speed+regen pass | **Grid tiles** and the grid buff | It is already a dry-land temporary buff tile with 6 tests. The grid is the faction's mechanic. |
| Raise Tidepool / Great Tidepool / Leviathan trail | patch / big patch / Titan trail | Same calls, same caps. |
| Admiral's Broadside (line walk) and Riptide (cone, x2 on a pool) | Derez Beam, Grid Pulse | Land mechanics; "x2 in a pool" becomes "x2 on its grid". |
| Cutlass Ambush (off-the-pool step) | Light Blade *Phase Strike* | Same hook (`TidalUnit.onLeftWater`, renamed later). |
| Reef Guard wet regen | Bulwark Frame grid regen | Per-unit regen bonus. |
| Harpooner pull every 3rd throw | Grav Tether | Pure land mechanic; matches TABS Neon's gravity unit. |
| Flak Swivel x3 vs flyers | Flak Drone | Role. |
| Bombard Crew + `BombardShell` | Arc Mortar | Role. The shell is shared code (Brassworks, Stormcallers) and moves to a shared package. |
| Mudskipper +speed when wet | Hover Skiff +speed on the grid | Role and hook. |
| Kraken Caller Tentacle Grab (drag, root, stronger when wet) | Gravity Lancer tractor beam | Land mechanic; "wet" becomes "on the grid". |
| Sky Corsair line bombing | Glider Bomber | Mechanic, not the ghast body. |
| Shellback Turtle Moor (deploy: half damage, a pool round it) | Bastion Walker deploy | A deploy mode is in the vehicles plan anyway. |
| Leviathan trail, Tidal Slam, big-body exits, siege weakness | Titan Frame | Land mechanics. |
| Deep Dive (sink at water/pool, surface further on) | Titan *Phase Shift* (from its own grid) | The "start from a pool" rule becomes "start from the grid". |
| Fog Bank (a ThicketCover cover source) | **Cloak Field** (commander or Grid Weaver, slice 2) | Cover is machine-like as a cloaking field. |
| Lighthouse Battery reveal cone + gun | Scanner Spire | A radar tower is BAR. |
| Maelstrom Engine ring of flood pools | Mainframe Upload ring of grid nodes | The counterplay ring is the good part. |
| Salvager (combat engineer) | Patch Drone | Role. |
| Harbourmaster as T2 worker / Abyssal Shipyard as T3 lab | Architect Drone / Titan Gate | Roles, tier gates, tests. |
| Wreck Harbour, Slipway, Drydock (+ their production lists, hotkey slots, tier checks) | Core Ring, Compiler, Foundry Matrix | Roles and wiring; structures are redrawn. |
| Bot habits (`tideHabits`, `tideKrakens`, `tideLeviathans`, `tideFogBank`, `turtleMoor`, Riptide aiming, AA queue) | grid-weaving, lancers, Titan, cloak, deploy | The behaviour fits; names change in the cleanup slice. |
| Tide Seer's reusable parts (PR 368: `TideSeerServerEvents.shove`, `BotPlayer.unitValue`, the Empower habit) | the Hunter Frame PR | Shared helpers. |

### Dropped

| Tidewrought piece | Why |
|---|---|
| Real water counting as "wet" (`isWet` checks `FluidTags.WATER`) | No water on the maps; the bonus must come only from the grid. |
| Pools putting out burning entities (`TidepoolBlock` `clearFire`) | Sea flavour; light doesn't douse fire. Fire still breaks tiles (counterplay kept). |
| `MobilityClass.TIDAL`'s cheap water cost, water breathing, never-drown and lobster air overrides, Alex's Mobs water-navigator swaps | Water only. The class itself stays until slice 5 (then `HOVER` for the Skiff, or removed). |
| Every Alex's Mobs and vanilla body (seagull, mudskipper, snapping turtle, lobster, Vindicator/Pillager/Witch crews, ghast balloon) | They don't read as robots. |
| Tide Seer (Brine Bolt, Drowned Tithe, Rising Tide) | Replaced by the Hunter Frame. |
| Corsair Captain from the faction | Goes to the neutral hero pool (decided Oct 28). |
| Sea names, teal/brass palette, `"_tide"` structures and textures, calm theme music, `gen_tide_skins.py`, `eco_tidewrought.py`, `mex_tide.py` | Replaced by the Lumen look; deleted in slice 5. |
| Unbuilt Tidewrought plans: Land-Galleon, Clockwork Diver, Boarding Crew capture, water Docks, Coral Crab, Manta Ferry, coral-palace capitol, Fitting Shed lab | Sea-only or superseded by the Lumen roster and vehicles. |

---

## 4. Essence caster: the Hunter Frame (lovish: "a terminator that evolves")

A relentless machine hunter that picks a target, chases it down and rebuilds itself stronger from what it gathers.
The film is inspiration only: no chrome skull or endoskeleton copied from it. It is a black lattice frame with a
single visor slit and the team's light lines.

**Unit:** T2 lab (Foundry Matrix), the casters' usual price shifted toward energy (Tide Seer was 120 food / 80
energy / 100 metal, 40 s, 3 pop), essence carrier (holds 30, gathers within 8, 12 on its own grid). It is a normal
unit, not a summon: veterancy and Empower work on it, and it drops its held essence when it dies (the shared rule).

**Slots: two abilities + Evolve + Empower.** Evolve takes the big (20) slot. The other casters' big spells are 60 s
power spikes; the Hunter's spike is permanent growth, bought in four steps over a match (80 essence plus kills in
all, about four big casts). That keeps it at three essence abilities like every other caster.

| Slot | Ability | What it does |
|---|---|---|
| Cheap (5), cd 12 s | **Lock-On** | Marks one enemy within 16 for 10 s. The mark strips its cover; the Hunter deals +30% to it and moves +20% faster while closing on it. If the marked target dies within the mark, refund 3 essence and +1 hunt point. |
| Mid (10), cd 25 s after it triggers | **Last Stand Protocol** | Arms for 45 s. If a hit would kill the Hunter while armed, it survives at 30% HP and **sheds one stage**: its outer parts fall off and it drops to the stage below. At stage 0 it survives once at 20 HP. "It gets back up," but at a price the enemy can see. |
| Big (20), cd 60 s | **Evolve** | Needs 20 essence **and** enough hunt points since the last stage. 3 s channel (1.5 s on its own grid); it can't move or attack while the light lines draw its new parts. Advances one stage. |
| Shared (15) | **Empower** | As every caster: +1 veterancy rank on an ally within 6 (never itself, heroes, commanders, experimentals or summons; 20 s cd, one rank per target per 60 s). |

**Hunt points:** +2 for a killing blow on an enemy non-summon, +1 for an assist (it hurt that enemy in the last 5 s).
Summons give nothing (the shared rule). Stages need 2 / 4 / 6 / 8 points since the stage before.

**Stages** (each a visible body change; numbers are starting constants, like the Grave Adept's):

| Stage | Name | Body | Stats and weapon |
|---|---|---|---|
| 0 | Frame | thin black lattice, visor slit | 50 HP, pulse shot 4 dmg / 1.5 s at 10 |
| 1 | Chassis | dark armour plates bolt over the frame | 90 HP, armour 2, +10% speed |
| 2 | Weapon | an arm-mounted beam emitter with lit barrel rings | beam 9 dmg / 1.5 s at 14 |
| 3 | Shielding | shoulder emitters, a shimmering hardlight shell | 120 HP + a 60-point shield that regenerates 3/s after 4 s out of combat |
| 4 | Final Form | taller (x1.2), light-fin crest, glowing core | 160 HP; beam chains to a second target; Lock-On marks two and its cooldown halves; immune to Hex and roots while Lock-On is active |

**Growth compared with the Grave Adept:** the Adept is a summon that levels from XP orbs (120 / 300 / 540 / 840 /
1200) and bursts its XP to the killer's heroes on death. The Hunter is a real unit, so it pays for each stage with
essence it gathered and kills it made. Killing a Final Form also drops 5 extra wisps per stage (its parts), so hunting
the hunter pays. One Hunter per player may be at Final Form at a time, so a deathball of five Final Forms can't
happen.

**Bots** (bots build and use every caster):
- Lock-On autocasts on its attack target, priciest first and commanders first (the Kraken rule).
- Last Stand arms in combat below 60% HP.
- Evolve fires when it can pay and no enemy is within 12 (a safe channel). From stage 2 the bot keeps 10 essence in
  reserve for Last Stand.
- Empower uses the shared habit.

**Art:** one rig. The stages are ModelPart groups shown by a synced stage byte, so there are five looks without five
models. Evolve and Last Stand get a "lines draw / parts fall" effect from `BarFx`.

**lovish (Oct 29): keep the defaults.** Last Stand costs a stage, one Final Form per player, and hunt points come from
kills and assists.

---

## 5. Migration: convert in place, slice by slice

The faction slot stays live and bot-playable after every merge. Class and package renames (`tide` -> `lumen`) wait for
the cleanup slice, so open branches that touch `tide/` don't conflict on every slice.

| Slice | Delivers | Tidewrought pieces it turns over |
|---|---|---|
| **1a Grid core** (section 6) | grid rules + tile look on the existing pool system | Tides core |
| 1b Identity + T1 core | Lumen name/lang, `FactionTraits` (black/glow palette, derez FX in the `F_TIDE` slot), commander Derez Beam / Grid Pulse, Core Ring + Compiler + `"_grid"` economy structures, Builder Drone, Seeker Drone, Light Blade, Bulwark Frame, Grid Weaver bodies; bot kit renamed. Concept sheets first (CI `concepts.yml`, 3 models a run). | Admiral, Shipwright, Gull Spotter, Cutlass, Reef Guard, Tide Priest, Wreck Harbour, Slipway, eco set |
| 2 Rest of T1 | Pulse Gunner (new), Grav Tether, Flak Drone, Arc Mortar, Hover Skiff, Cloak Field; `BombardShell` to a shared package first | Harpooner, Flak Swivel, Bombard Crew, Mudskipper, Fog Bank |
| **1c Hunter Frame** (lovish: right after 1b) | the evolving essence caster in the Drydock's caster slot, its 3 abilities, stages and bot habits | Tide Seer (PR 368 closes) |
| 3 T2 | Foundry Matrix, Architect, Gravity Lancer, Rail Lancer, Glider Bomber, Bastion Walker, Beam Walker, Scanner Spire, building grid | Drydock, Harbourmaster, Kraken, Mortar Galley, Sky Corsair, Shellback, Lighthouse |
| 4 T3 + enders | Titan Gate, Titan Frame, Mainframe Upload, Ward Spire variant, Blade Master hero, Patch Drone | Abyssal Shipyard, Leviathan, Deep Dive, Maelstrom, Salvager |
| 5 Cleanup | delete sea assets/tools/lang/music, rename `tide` -> `lumen` (classes, tests, habits), registry path `tidewrought` -> `lumen` with an alias in `SkirmishServerboundPacket.CODE_PATHS` and the faction lookup for old lobby settings and `MatchStateSaveData`; Forge missing-mapping handling for removed entities and blocks; `MobilityClass.TIDAL` -> `HOVER` or gone | everything left |

The Corsair Captain's move to the neutral pool is its own small PR, at any time. The Hunter Frame comes right after 1b
(lovish, Oct 29). It only needs the T2 lab's caster slot, which the Drydock already has.

**Tests per slice:** keep each test where its mechanic stays and change bodies and numbers only. Renames wait for
slice 5. Delete only the water-only checks (the never-drown half of
`tidewrought_leviathan_never_drowns_on_land_and_fears_siege`). Every slice is red then green, with flake-stress on
GitHub runners at lovish's minimums (red 3, green 8), dispatched after the PR build compiles. Never run locally.

---

## 6. First build slice: 1a Grid core (test-first plan)

**Goal:** the smallest step that makes the Light Grid real and keeps everything green. Today's tidepools play by the
Light Grid's rules and look like light. No unit, building, bot or name changes yet: the Tide Priest, Harbourmaster,
Leviathan and Maelstrom keep raising them, and bots keep playing.

### Changes
1. **Grid only, never water:** `TidesServerEvents.isWet` returns true only on a `TidepoolBlock` (drop the
   `FluidTags.WATER` check). Riptide's x2 and Tentacle Grab's wet bonus follow automatically (they call `isWet`).
2. **No fire dousing:** `TidepoolBlock` stops calling `clearFire` on entities inside it. Fire and lava next to a cell
   still evaporate it.
3. **The sensor rule:** once a second (in the existing 1 Hz expiry pass of `TidepoolServerEvents`), for each active
   pool, any unit that is an enemy of the pool's owner and stands on one of its cells gets
   `Concealment.revealFor(le, 40)`. Units come from the grid query round the pool's centre, so there is no entity
   search and no allocation, like the Lighthouse. The owner's and allies' units are never touched.
4. **Look:** the tile model becomes a dark glossy panel with a thin emissive grid line (render type stays
   translucent; `emissiveRendering` true so the lines glow at night). Light emission stays 0, so no light updates and
   no lighting cost. One neutral light colour for now; team-coloured lines come later (they need the owner on the
   client).
5. **Words:** lang only: "Raise Tidepool" -> "Draw Grid", "Raise Great Tidepool" -> "Draw Great Grid", the
   too-many-tidepools refusal -> "too much grid". No code renames.

### Tests (red commit first: tests + stubs that keep today's behaviour, so the new ones fail)
- `grid_strips_cover_from_an_enemy_on_it_not_a_friend`: an owner's pool. An enemy Shade Ranger cloaked on it is
  revealed within 25 ticks. A friendly cloaked unit on the same pool, and an enemy on its own side's pool, are not.
- `grid_real_water_gives_no_bonus`: a tidal unit standing in real water gets no speed modifier and no regen. The same
  unit on a tile gets both.
- `grid_does_not_put_out_fire`: a burning enemy on a tile is still burning 10 ticks later. Fire next to a cell still
  evaporates it (the existing `tidepool_fire_evaporates_and_drain_dries` covers the second half).
- The regression net stays unchanged and must stay green: the six `tidepool*` / `tides_buff*` tests, Riptide
  (`tidewrought_riptide_knocks_foes_back_harder_in_a_pool`, which already uses a pool), Kraken
  (`tidewrought_kraken_caller_grabs_a_foe_not_a_friend`, pool too), Fog Bank, Lighthouse, Mudskipper, Maelstrom, and
  `tide_bot_starts_building_on_plain_land`.
- Checked on main `b04b9d20`: the two tests that assert wetness (Riptide, Kraken) put the unit on a pool, and the
  only other water in `SkirmishGameTests` is a metal-patch river-bank test with no tidal unit. No existing test leans
  on water-as-wet. The builder re-greps before the red commit in case main moved.

### Runs and size
Red: tests + stubs, flake-stress `test:grid_` on 3 runners once the PR build compiles. Green: changes 1-5,
flake-stress on 8 runners over `grid_`, `tidepool`, `tides_` and `tidewrought_` (the regression net). About 6 files
plus lang and one block model/texture. Size S. One builder session.

---

## 7. Open Tidewrought PRs and branches

| PR / branch | What it is | What happens |
|---|---|---|
| PR 368 `claude/caster-tidewrought` (Tide Seer, head 6978661b, paused) | the Tidewrought caster | Never merged. Its helpers go into the Hunter Frame PR (slice 3, or right after 1b), then it closes with a link. |
| PR 355 `claude/siege-t1` | T1 lobber for every faction | Unaffected; its Tidewrought row becomes the Arc Mortar in slice 2. |
| PR 359 `claude/anti-siege-tower` | anti-siege tower (`siege_guard_tide`) | Unaffected; reskinned in 1b/5. |
| PR 306 `claude/vehicle-labs` | vehicle labs design | The Tidewrought Fitting Shed is cancelled; the Hover Bay is designed with this faction. |
| PR 310 `claude/design-vehicles-labs-capitols` | vehicles, capitols, creep | Tidewrought rows superseded; its 3.8 futuristic vehicles are this faction's. |
| PR 361 `claude/energy-tiers` | per-faction energy tiers | Its Tidewrought row becomes Lumen's (energy-led, so it matters more here). |
| PR 299 `claude/flake-bombard-dead-zone` | Bombard Crew flake diagnostics | Keep; carries over to the Arc Mortar. |
| PR 294 `claude/flake-tidewrought-t2` | Mortar Galley diagnostics (cause fixed) | Close, now or at slice 3. |
| PR 211 / 227 (`joisco/`) | second T3 frame (Abyssal Hydra), Tier 4 | Not ours; tell joisco the Tidewrought second-T3 slot moves to Lumen. |
| Anim PRs 373 / 375 / 377 | per-faction deaths | Tidewrought's death becomes **derez** (light shards). |
| Branches `tidewrought-plan`, `tidewrought-slice-1..4`, `tidewrought-tidepools`, `ci-tide-bot-stall` | merged history | Leave. |
| `design/tidewrought_plan.md` | the Tides core API doc | Keep until slice 5, then mark historical. |

---

## 8. lovish's answers on revision 2 (Oct 29, via Command Center Rust)

1. **Slice 1a: yes**, as section 6 says. It is PR 380 (`claude/ai-grid-core`), test-first, red on 3 runners and
   green on 8.
2. **Hunter Frame defaults kept** (section 4).
3. **Hunter Frame right after 1b** (slice 1c in section 5).

Slice 1a as built differs from section 6 in one way: Deep Dive (the Leviathan's) still starts from real water or a
pool. The Leviathan checks water itself, and slice 4 replaces it with Phase Shift.
