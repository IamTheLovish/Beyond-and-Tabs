# The AI faction (replaces Tidewrought) - theme options and migration plan

**Status: design only, for lovish's pick. No game code until he chooses.** Names are placeholders.

## 0. What lovish has said so far

- Oct 28 (via Command Center Rem; recorded in `design/faction-change-tidewrought.md` on branch `claude/vehicle-labs`,
  PR 306): drop Tidewrought and add an AI faction: *"drones and robots, kinda inspired by Beyond All Reason and the
  neon faction from T.A.B.S"*, *"kinda like a Tron Legacy faction with AI robots and mecha"*. Look: glossy black and
  dark-grey bodies, clean hard geometry, thin glowing light lines on every edge, light as the only ornament (inspired
  by Tron, never copied: no logos, characters, light cycles or other recognisable designs). Units: BAR-style walking
  bots, vehicles, a commander mech, constructor drones, small bots up to giant mechs; TABS Neon energy weapons (energy
  pistols and swords, a gravity unit, a long-range beamer, an energy-sword master). Not Brassworks: no gears, smoke,
  rivets or brass. The Corsair Captain hero moves to the **neutral** hero pool. Keep Tidewrought playable until the
  replacement can be picked.
- Oct 28 (vehicles design, PR 310 `design/proposals/vehicles.md` 3.8): the futuristic faction's vehicle slot is reserved:
  Hover Skiff (T1 raider), Line Tank (T1 assault), Rail Lancer (T2 siege, deploys), Flak Tank (T2 AA), Lifter Drone
  (T2 transport). Capitol direction: ring-gate towers (`design/LANES.md`). First look reference: ref37, a futuristic
  glass spire.
- Oct 29: *"tidewrought is getting replaced by an ai faction"*, *"there isnt water"*. The maps are dry, so nothing in
  this faction may depend on water.

So all three options below share lovish's look and unit types. They differ in **the faction's one mechanic**: what it
does that no other faction does, which shapes every unit and the bots.

### Rules every option keeps

| Rule | Why |
|---|---|
| Clean, digital, lit: glossy black/grey + one or two team light colours. No gears, steam, rivets, brass, Tesla coils. | Brassworks already owns steam machines, the Tesla Walker and the Shield Projector. |
| Machines, not creatures: no creep you must build on, no eggs, no organic swarm. | Hivebound (planned) is the alien insect swarm with a creep placement rule. |
| Nothing needs water. Hover units cross water as a bonus only. | The maps are dry. |
| Same roles and prices as the other factions at each tier (worker, scout, raider, line, ranged, siege, support, AA; T2 worker, caster, heavy, bomber, beast; T3 experimental). | BAR readability; balance tables (`tools/unit_stats.py`) stay comparable. |
| The shared defence set (`design/defences.md`) in this faction's look, plus one own T2 tower. | "One shape and one price per job across all factions." |
| Death style: units **derez** into falling light shards and a fading outline (the unit-animation lane's per-faction deaths). | Replaces Tidewrought's "dissolve into seawater". |
| Light lines are drawn by an emissive render layer (like vanilla's spider/enderman eyes layer) over a dark glossy skin. | Cheap, reads from the RTS camera at night and day, works on any body. |

### Bodies (the real cost of this faction)

Tidewrought sat on Vindicator, Pillager, Witch, seagull, mudskipper, turtle and lobster bodies. None of them reads as a
robot. Each option needs about 12-16 new robot bodies through the existing cube-model pipeline (`design/models/*.json`
-> `concepts.yml` concept sheets -> `dsl_to_game` -> renderer). Humanoid bots can share one rig with swapped heads and
weapons, and drones can share one hover rig, which keeps the count closer to 6 rigs. This is the same for all three
options, so it is not a reason to pick one over another.

---

## 1. Option A - the Light Grid (recommended)

**Identity:** programs made of light that draw their own battlefield: glowing grid lines spread across the ground
wherever they go, and on the grid they are fast, shielded and hard to hide from.

**The mechanic: the Grid.** Grid tiles are flat light panels on the ground (thin, no collision, walkable by everyone,
never placed on buildings or metal patches).
- Buildings keep a small permanent grid round themselves while powered (radius 3-5 by building size).
- Constructor drones and the support unit draw temporary grid patches (radius 2-3, about 30 s), up to 4 patches per
  player, like today's tidepools.
- On the grid, own units get +20% speed and regenerate a light shield (2 HP/s). Constructors build 25% faster on it.
- **Enemies standing on your grid are revealed to you** (the grid is a sensor), even inside cover.
- Counterplay: any constructor's reclaim order derezzes a patch in 2 s; explosions and lightning break the cells they
  hit. Grid is a buff, never a wall, and never a building rule (that is Hivebound's creep).

**Economy twist: energy-led.** Units and buildings cost about 20% more energy and 20% less metal than the others (light
is energy). Drawing a patch costs 40 energy. So the AI wants wind/solar early and is hurt by energy raids, not metal
raids. No other faction leans this way except the Stormcallers' spells, which spend energy live; this one spends it on
production.

**Why A fits best:** the Tides core (`tide/TidepoolServerEvents`, `TidepoolBlock`, `TidesServerEvents`, the
`KIND_TIDEPOOL` pathing kind) is already a temporary walkable buff-tile system with caps, fail-safes, fog behaviour,
counterplay and 6 GameTests. The grid **is** that system with a new look and a reveal rule added. The light lines on
the ground are the Tron look itself and show from the RTS camera which ground belongs to whom.

| Role | Unit | Job and mechanic | Comes from |
|---|---|---|---|
| Commander | **Prime Program** (commander mech) | D-gun **Derez Beam**: a straight line of light, entity damage only. Ability **Grid Pulse**: a cone shove (x2 on foes on its grid) that leaves a grid patch. | Admiral: Broadside line walk + Riptide cone |
| T1 worker | **Builder Drone** | hover constructor; draws grid patches. | Shipwright |
| T1 scout | **Seeker Drone** | fast low flyer, sight 26, no attack. | Gull Spotter |
| T1 raider | **Light Blade** | energy-sword bot; *Phase Strike*: stepping off the grid primes +40% on the next hit within 3 s. | Cutlass Raider (Ambush) |
| T1 line | **Bulwark Frame** | shield bot, ranged resist; regenerates 3 HP/s on the grid. | Reef Guard |
| T1 ranged | **Pulse Gunner** | energy-pistol trooper, the plain ranged line. | new (thin ranged attacker) |
| T1 puller | **Grav Tether** | every 3rd shot pulls the target 6 blocks (TABS Neon's gravity unit). | Harpooner |
| T1 support | **Grid Weaver** | follows the army and lays a grid patch under it before a fight. | Tide Priest (Raise Tidepool) |
| T1 AA | **Flak Drone** | x3 vs flyers, x0.5 vs ground. | Flak Swivel |
| T1 siege | **Arc Mortar** | plasma lob, min range, splash, dents buildings. | Bombard Crew (+ PR 355 lobber row) |
| T1 vehicle | **Hover Skiff** | fast hover raider (vehicles 3.8), +60% speed on the grid. | Mudskipper Sloop |
| T2 worker | **Architect Drone** | 2x build power; draws a big patch (r3). | Harbourmaster (Great Tidepool) |
| T2 control | **Gravity Lancer** | tractor beam: drag 4 blocks + root 2 s; stronger if either side is on the grid. | Kraken Caller (Tentacle Grab) |
| T2 artillery | **Rail Lancer** | deploys, one straight piercing shot at 28 (vehicles 3.8). | Mortar Galley's role |
| T2 bomber | **Glider Bomber** | drops 3 charges in a line. | Sky Corsair |
| T2 heavy / deploy | **Bastion Walker** | walks, then deploys into a long-range battery (no move while deployed). | Shellback Turtle (Moor) |
| T2 beamer | **Beam Walker** | long-range continuous beam (TABS Neon beamer), weak up close. | new |
| T2 caster | **Rez Weaver** | essence caster, below. | replaces Tide Seer |
| T3 experimental | **Titan Frame** | giant mech; leaves grid patches as it walks (its escorts run fast); *Shockwave* arc slam; *Phase Shift*: from its own grid it fades out and reappears up to N blocks away behind the front. Weak: no anti-air, slow, x1.75 from siege. | Leviathan (trail pools, Tidal Slam, Deep Dive from a tidepool) |
| Hero | **Blade Master** | energy-sword master (TABS Neon): dashes along the grid, duelist. | new (Corsair goes neutral) |
| Combat engineer | **Patch Drone** | field repair and field defences. | Salvager |
| Game ender | **Mainframe Upload** | charges only while a ring of grid nodes round it is lit; break the nodes to slow it, break all to stop it. | Maelstrom Engine (flood-pool ring) |

**Buildings:** capitol **Core Ring** (ring-gate towers round a glass spire, ref37), T1 bot lab **Compiler**, T2 lab
**Foundry Matrix**, T3 lab **Titan Gate**, vehicle lab **Hover Bay** (when the vehicle-lab lane reaches it), and the
economy set in a new `"_grid"` palette (black concrete, polished deepslate, tinted glass, sea lanterns or the block
pack's emissive strips for the lines).

**Defence line:** Light Wall, Light Gate, Feedback Mine + Stasis Snare (traps), Pulse Turret (anti-raid), Lance
Battery (anti-T3), Sky Watch and Flak in the AI look, the anti-siege tower (PR 359). Own T2 tower: **Scanner Spire**,
a turning sensor cone that lifts cover from hidden enemies and a light gun. It is the Lighthouse Battery's code.

**Essence caster - Rez Weaver** (T2 lab; essence is "data" the dead leave behind):
- Cheap (5): **Decoy**: two hologram copies of a friendly unit for 12 s. They draw fire and deal no damage.
- Mid (10): **Rollback**: friends within 6 return, after 4 s, to the HP they had when it was cast. It undoes a burst.
- Big (20): **Recompile**: rebuilds the costliest friendly T1/T2 that died within 12 blocks in the last 15 s, at 50%
  HP, marked summoned (no essence or XP when it dies again). One per 60 s. *lovish said "no BAR-style rez bots". This
  is a limited spell, not a rez unit. If he wants no revival at all, swap it for **Light Cage**: enemies in r3 can't
  leave the cage for 5 s.*
- Shared Empower (15). None of the three repeats another caster (blink, chain lightning, repair, turret, whirlpool and
  angels are taken).

---

## 2. Option B - the Nexus (one mind, linked drones)

**Identity:** a single machine mind; every drone is one of its hands, and the whole army fights better while the
links between them hold.

**The mechanic: Link.** The capitol and cheap **Relay** pylons (30 metal) project link range (24 blocks), and the
Relay Drone carries a mobile link (12). Visible light lines join linked units to their relay.
- Linked units: +15% attack speed and shared vision. Their first hit **marks** a target: marked targets take +15% from
  every linked unit (machine focus fire).
- Cut off: -15% speed and no marks. Killing relays and Relay Drones is the counter, and it is visible from the camera.

**Economy twist: recycle.** Any own unit standing at a Relay can be recycled for 60% of its metal (BAR's own-unit
reclaim), so the army can turn itself into what the fight needs. Units are cheap and fragile (about 85% of the usual
HP and cost).

| Role | Unit | Job and mechanic |
|---|---|---|
| Commander | **Nexus Prime** | D-gun **Overload Lance**; ability **Uplink Burst**: linked units within 16 get +30% speed for 5 s. |
| T1 worker / T2 worker | **Assembler Drone** / **Fabricator** | constructors; the Fabricator also carries a link. |
| T1 scout | **Eye Drone** | flyer with a small link (vision spotter). |
| T1 raider | **Interceptor** | fast blade bot; double damage to marked targets. |
| T1 line | **Sentinel** | hardlight shield bot. |
| T1 ranged | **Pulse Trooper** | energy pistols. |
| T1 support | **Relay Drone** | mobile link + slow repair beam on machines. |
| T1 AA / siege / vehicle | **Flak Drone** / **Arc Mortar** / **Hover Skiff** | as option A. |
| T2 | **Gravity Lancer**, **Rail Lancer**, **Glider Bomber**, **Bastion Walker**, **Carrier Drone** (launches 4 micro-drones that die when unlinked) | |
| T2 caster | **Intrusion Node** | below. |
| T3 | **Overmind Colossus** | giant walker; its link covers 40 blocks and all its linked units mark on every hit; beam weapon. Weak: no AA, slow. |
| Hero | **Proxy** | support hero who commands drones (takes over a relay's link, redirects marks). |
| Combat engineer / game ender | **Patch Drone** / **Singularity Uplink** (charges while relays round it stand) | |

**Buildings:** Core Ring capitol, Relay pylon (new cheap building), Compiler / Foundry Matrix / Titan Gate labs, vehicle
lab, `"_grid"` economy. **Defence line:** the shared set; own T2 tower **Firewall Node** (a tower that is also a big
relay and shoots).

**Essence caster - Intrusion Node:** Cheap (5) **Ping**: reveal r12 for 6 s, cover too, and mark everything revealed.
Mid (10) **System Crash**: EMP r4, enemies can't cast for 5 s and attack 30% slower (machines also move 30% slower).
Big (20) **Drone Swarm**: 6 micro-drones for 20 s (summons, no population).

**Against A:** the light lines between units are very readable, but Link is new aura code with per-unit state for every
unit in range at 8v8. It touches Hivebound's "hive mind" fantasy more than A does. It reuses less Tidewrought code (the
Tides system goes, the Harpoon/Kraken/Lighthouse/turtle mechanics stay).

---

## 3. Option C - Merge Frames (modular mecha)

**Identity:** a mecha war-factory: small bots that lock together into bigger frames on the battlefield, BAR's scale
from little bots to giant mechs made literal.

**The mechanic: Merge.** Three bots of the same line standing together merge (5 s, they can't act while merging) into
one T2 frame of that line, keeping veterancy. A frame can **split** back into three bots at 70% HP (an escape). Merging
needs Tier 2 researched. T1 bots never go out of date.

**Economy twist:** merging is free (the three bots are the price), so the faction spends less on T2 labs and more on
T1 production. Its T2 lab makes only the units that can't be merged (bomber, caster, gravity unit).

| T1 bot (lab: Compiler) | Merges into (3 -> 1) |
|---|---|
| **Dash Bot** (energy-blade raider) | **Duelist Frame** (dashing melee) |
| **Guard Bot** (shield line) | **Aegis Frame** (front-line tank that projects a short hardlight wall ahead) |
| **Pulse Bot** (ranged) | **Beam Frame** (long-range beamer) |
| **Lobber Bot** (siege) | **Siege Frame** (deploying artillery) |
| **Flak Bot** (AA) | **Skyguard Frame** |

Also T1: Constructor Bot, Spotter Drone, Repair Drone (support), Hover Skiff. T2 lab: Fabricator (T2 worker), Gravity
Lancer, Glider Bomber, caster. T3: **Titan** (a giant mech; option: five T2 frames combine into it instead of a T3 lab
build). Commander **Command Frame** (D-gun Plasma Cannon; ability **Jump Jets**: leap and stomp). Hero **Blade
Master**. Game ender **Assembly Gate** (builds a world-ending mech that walks out when charged).

**Essence caster - Frame Architect:** Cheap (5) **Jump Jets**: an allied bot leaps up to 10 blocks. Mid (10) **Weapon
Module**: an ally gets +30% range or a flak module for 20 s. Big (20) **Orbital Drop**: a combat frame falls from the
sky (25 damage r3 on landing) and fights for 30 s.

**Against A:** the most original mechanic and the most "mecha", but it is the riskiest code: merging and splitting
entities touches selection, control groups, veterancy, essence carriers, population, save data and every bot rule. It
needs the most new bodies (5 frames on top of the bots), and it reuses the least Tidewrought code.

---

## 4. Side by side

| | A Light Grid | B Nexus | C Merge Frames |
|---|---|---|---|
| Reads from the RTS camera | lit ground under the army | light lines between units | big frames forming |
| New engine work | small (grid = Tides core + reveal) | medium (Link aura at 8v8, Relay building) | large (merge/split entities) |
| Tidewrought code reused | most: Tides core, Harpoon, Kraken grab, Fog Bank, Lighthouse, turtle deploy, Leviathan trail/slam, Maelstrom ring, Broadside/Riptide | about half | least |
| Tests that carry over | ~20 of 31 renamed, the rest replaced | ~12 | ~6 |
| Distance from Brassworks | far (light, energy-led) | far | medium (both are machines that build up) |
| Distance from Hivebound | far (a buff tile, not creep you must build on) | close-ish (hive mind) | far |
| Recommendation | **yes** | second | later idea (Merge could be one T3 trick inside A) |

A mix is possible: A's Grid as the faction mechanic, with C's merge kept only for the T3 (three T2 frames combine into
the Titan) if lovish wants the mecha moment.

---

## 5. What Tidewrought owns today (inventory, main `b04b9d20`)

Paths under `bar/src/main/java/com/solegendary/reignofnether/`. 96 Java files reference Tidewrought.

- **Faction:** `Factions.TIDEWROUGHT` (registry path `tidewrought`, skirmish code 5 in `SkirmishServerboundPacket` and
  `SkirmishSetupScreen`), `FactionTraits.TIDEWROUGHT` (teal/brass livery, banner, scaffold, `BarFx.N_TIDE`/`F_TIDE`,
  commander Broadside + Riptide, the `tidal` flag), calm theme music, lobby icon.
- **Units** (`unit/units/tide/`, 16 + prods): Shipwright (worker, the Admiral commander), Gull Spotter, Cutlass Raider,
  Reef Guard, Bombard Crew (+ `BombardShell`), Tide Priest, Harpooner, Flak Swivel, Mudskipper Sloop; T2 Harbourmaster,
  Kraken Caller, Mortar Galley, Sky Corsair, Shellback Turtle; T3 Leviathan. `TideProd`, `TideT2Prod`. Renderers in
  `unit/modelling/renderers/` (11) and `SkyCorsairModel`.
- **Hero / engineer:** Corsair Captain (`unit/units/heroes/CorsairCaptainUnit`, `HeroKits.CORSAIR_CAPTAIN`, revive
  prod); Salvager (`unit/units/engineers/SalvagerUnit`).
- **Buildings** (`building/buildings/tide/`): Wreck Harbour (capitol), Slipway (T1 lab, L1-L3), Drydock (T2), Abyssal
  Shipyard (T3), Lighthouse Battery (T2 tower). Shared-class variants: metal extractor T1/T2, wind, energy converter,
  storage vault, lab annex, geothermal, solar + advanced solar (`_TIDE`), Ward Spire `_tide`, Maelstrom Engine (game
  ender, `building/gameender/`), and the defence set `_tide` (walls, gate, mine/snare traps and enhanced, raid tower and
  heavy, Sky Watch and heavy, flak, anti-T3). 27 structure NBTs.
- **Systems:** `tide/` (TidepoolServerEvents, TidesServerEvents, FogBankServerEvents, TidalUnit),
  `blocks/TidepoolBlock`, `MobilityClass.TIDAL`, `WalkabilityBuilder.KIND_TIDEPOOL`, `PathConverter`; abilities
  RaiseTidepool, RaiseGreatTidepool, FogBank, TentacleGrab, TidalSlam, DeepDive, Moor, CommanderAbility RIPTIDE,
  CommanderDGun BROADSIDE; hooks in ThicketCover, FrostSheetBlock, Concealment, AreaCommands (drain), UnitActionItem,
  UnitUnstick, T2Workers, RaidTuning (T1 vehicles), CommanderAntiAir, MatchOutcome, persistence.
- **Bots:** `BotPlayer.kitFor` branch, `tideHabits`, `tideFogBank`, `tideKrakens`, `tideLeviathans`, Riptide aiming,
  `queueAntiAir` (Flak Swivel), `t2TowerFor` (Lighthouse), storage vault; `BotRoles` (scout, siege, artillery checks),
  `BotCombatEngineers` (Salvager), `BotGameEnders` (Maelstrom, Ward Spire, `turtleMoor`), `BotEconomy` (geothermal),
  `/rts bot ... tidewrought|tide`.
- **Shared code other factions use (must move, not be deleted):** `BombardShell` (Brassworks Mortar Crawler and Iron
  Leviathan Engine, Stormcallers Rocket Battery, `BuildingDamage`, `FieldArtillery`); the Fog Bank as a ThicketCover
  cover source; the big-body exit fix (`ProductionPlacement.safeSpawnPoint`, already shared); `HeroKits.tidepool`
  (Corsair's kit, goes neutral with him).
- **Tests:** 31 GameTests named `tide*`/`tidewrought_*`/`tidepool*` (mostly `SkirmishGameTests`, `BotGameTests`;
  375 lines mention Tidewrought in SkirmishGameTests), plus Tidewrought rows in 12 more test files (Artillery,
  BigUnit, Blueprint, BuildGroup, CombatEngineer, ExperimentalAbilityTooltip, Hero, Ladder, Solar, Steward, T3Role,
  UpgradeVisuals). The ladder matrix picks factions up from the registry by itself.
- **Art and assets:** 59 textures (skins, portraits, icons), 27 NBTs, ~200 lang lines, `tools/gen_tide_skins.py`,
  `tools/eco_tidewrought.py`, `tools/mex_tide.py`, the `"_tide"` palette in `tools/gen_structures.py`,
  `design/tidewrought_plan.md`, `design/building_preview_economy_tide.png`, `design/buildings/tide_*`.

---

## 6. Migration plan (for option A; B and C change only the "reused" column)

### 6.1 Recommended way: convert in place, one slice at a time

The faction slot stays live the whole time (lovish's Oct 28 rule). Each slice turns a group of Tidewrought pieces into
their AI roles, with their tests, so a bot can play the faction after every merge.

| Slice | What changes | Tidewrought pieces it turns over |
|---|---|---|
| 0 (this PR) | design only | - |
| 1 Identity + T1 core | new registry name and lang, `FactionTraits` (black/glow palette, derez FX in the F_TIDE slot, new commander kinds), grid look on the Tides core + the reveal rule, capitol + T1 lab, economy `"_grid"`, worker, scout, raider, line, support, bot kit | Shipwright/Admiral, Gull Spotter, Cutlass, Reef Guard, Tide Priest, Wreck Harbour, Slipway, eco set, tidepool tests |
| 2 Rest of T1 | ranged, puller, AA, siege, Hover Skiff, Fog Bank -> **Cloak Field** (same ThicketCover source) | Harpooner, Flak Swivel, Bombard Crew, Mudskipper Sloop |
| 3 T2 | T2 lab, Architect, Gravity Lancer, Rail Lancer, Glider Bomber, Bastion Walker, Beam Walker, Scanner Spire, Rez Weaver caster | Drydock, Harbourmaster, Kraken, Mortar Galley, Sky Corsair, Shellback Turtle, Lighthouse Battery, Tide Seer branch |
| 4 T3 + enders | Titan Gate, Titan Frame, Mainframe Upload, Ward Spire variant, hero Blade Master, Patch Drone | Abyssal Shipyard, Leviathan, Maelstrom Engine, Salvager |
| 5 Cleanup | delete what is left of the sea theme (skins, NBTs, lang, tools, music), move `BombardShell` to a shared package, decide whether `MobilityClass.TIDAL` becomes `HOVER` | - |

The Corsair Captain moves to the neutral hero pool in its own small PR at any time (already decided).

**Per piece, the default is "keep the mechanic, replace the body and name".** Mechanics that only made sense in water go:
the Leviathan's never-drown and lobster-air overrides, AM water-navigator swaps, real-water bonuses, the `bt_tidal`
water-breathing. Mechanics that were already land mechanics stay: pulls, roots, cones, cover, reveal cone, deploy mode,
trail tiles, ring-charged game ender.

### 6.2 Alternatives (lovish to choose)
- **Build beside, then swap:** register the AI faction as a preview (`setPreview()`, hidden from Random and bots), build
  T1, then swap it into Tidewrought's lobby slot and skirmish code 5 and delete Tidewrought in one PR. The look is never
  mixed, but there are two factions to keep green for weeks and a big delete PR at the end.
- **Remove Tidewrought now:** 6 factions until the AI's T1 is ready. Fastest to reason about, but it breaks the
  "keep it playable" rule and every 7-faction test and ladder pairing changes twice.

### 6.3 Technical notes for whoever builds it
- **Registry ids:** factions are registered in order "so no existing registry id moves". Converting in place keeps the
  slot, so Stormcallers and Brassworks don't move. The registry *path* changes from `tidewrought` to the new name once:
  add an alias in `SkirmishServerboundPacket.CODE_PATHS` and the faction lookup so old lobby settings and match saves
  (`MatchStateSaveData`) map to the new faction. Removed entity types and blocks need Forge missing-mapping handling
  (or are kept registered until slice 5) so a saved world doesn't log errors.
- **Shared code first:** move `BombardShell` to a shared package before its Tidewrought users go. It is a plain refactor
  PR, and the Brassworks/Stormcallers tests prove it.
- **Grid = Tides core:** rename `TidepoolBlock` -> grid tile (model: a thin dark panel with an emissive line texture in
  team colour), keep `KIND_TIDEPOOL` as a walkable kind (renamed), keep the caps, fail-safe ticks and drain/evaporate
  counterplay. New: enemies on the owner's tiles go into the owner's reveal set (`Concealment.revealFor`, the
  Lighthouse's path). Building grid: a permanent radius while the building is powered, which is a new owner kind in
  the registry (no lifetime, removed with the building).
- **Tests:** keep the test where the mechanic stays, rename it by role (for example
  `tidewrought_harpooner_pulls_a_foe_not_a_friend` -> `<faction>_grav_tether_pulls_a_foe_not_a_friend`), and change
  only bodies and numbers. Delete the water-only tests (`tidewrought_leviathan_never_drowns_on_land_and_fears_siege`
  keeps only its siege half; `tidewrought_mudskipper_is_faster_on_a_tidepool` becomes the Hover Skiff on the grid).
  Every slice runs the usual red/green flake-stress on GitHub runners, never locally.
- **Bots:** `kitFor` never returns null for the slot during the migration. Each slice swaps its kit entries and renames
  its habit (`tideHabits` -> grid weaving, `tideKrakens` -> gravity lancers, `tideLeviathans` -> Titan shockwave,
  `tideFogBank` -> cloak field, `turtleMoor` -> Bastion deploy), plus `BotRoles`, `BotCombatEngineers`,
  `BotGameEnders`, `BotEconomy`, `queueAntiAir`, `t2TowerFor`. Bots build and use the caster, like the other six.
- **Art:** concept sheets first (3 models or fewer per `concepts.yml` run), one rig per family (humanoid bot, drone,
  walker, tank), light lines as an emissive layer. Structures in a `"_grid"` palette through `tools/gen_structures.py`.

---

## 7. Open Tidewrought PRs and branches, and what happens to each

| PR / branch | What it is | Proposed |
|---|---|---|
| PR 368 `claude/caster-tidewrought` (Tide Seer, head 6978661b, paused) | the Tidewrought essence caster | Keep paused, never merge. Lift its reusable parts into the AI caster PR (`TideSeerServerEvents.shove` deterministic knockback, `BotPlayer.unitValue`, the Empower habit and bot test pattern), then close it with a link. |
| PR 355 `claude/siege-t1` | T1 lobber for every faction (touches Slipway, BombardShell) | Unaffected. Its Tidewrought row becomes the Arc Mortar in slice 2. |
| PR 359 `claude/anti-siege-tower` | anti-siege tower for 7 factions (`siege_guard_tide`) | Unaffected. Its tide structure is re-skinned in slice 1/5. |
| PR 306 `claude/vehicle-labs` | vehicle labs design (+ `faction-change-tidewrought.md`) | Its Tidewrought "Fitting Shed" is cancelled (it says so). The AI's vehicle lab (Hover Bay) is designed with this faction. |
| PR 310 `claude/design-vehicles-labs-capitols` | vehicles, capitols, creep proposals | Tidewrought rows (coral palace capitol 6.5, Coral Crab, Manta Ferry) are superseded. Its 3.8 futuristic vehicles become this faction's. |
| PR 361 `claude/energy-tiers` | higher-tier energy per faction | Its one Tidewrought row moves to the AI faction (energy-led, so this matters more for option A). |
| PR 299 `claude/flake-bombard-dead-zone` | Bombard Crew flake diagnostics | Still useful while the Bombard Crew exists, then it carries over to the Arc Mortar. Keep. |
| PR 294 `claude/flake-tidewrought-t2` | Mortar Galley flake diagnostics (cause already fixed) | Close when slice 3 replaces the galley, or now. |
| PR 211 / 227 (`joisco/` second T3 frame, Abyssal Hydra, Tier 4) | joisco's branches | Not ours to touch. Tell joisco that the Tidewrought second T3 slot (Abyssal Hydra) moves to the AI faction. |
| Anim PRs 373/375/377 (`claude/anim-*`) | per-faction death poses | Tidewrought's "seawater and foam" death becomes **derez** (light shards). Tell the unit-animation session. |
| Branches `tidewrought-plan`, `tidewrought-slice-1..4`, `tidewrought-tidepools`, `ci-tide-bot-stall` | already merged history | Leave them. They are the record of how the Tides core was built. |
| `design/tidewrought_plan.md` | the old plan | Keep until slice 5 (it documents the Tides core API the grid reuses), then mark it historical. |

---

## 8. Questions for lovish

1. **Theme:** A Light Grid (recommended) / B Nexus link network / C Merge Frames / A with C's merge only for the T3?
2. **Name:** a placeholder for now ("Lumen" for A, "Nexus" for B, "Vector" for C), or a name of his own?
3. **Tidewrought's content:** keep the mechanics and swap bodies and names (recommended), or delete it all and design
   fresh?
4. **How to start:** convert in place slice by slice so the faction stays playable (recommended), build the new one
   beside and swap, or remove Tidewrought now?
5. **Rez Weaver's big spell** (option A): a limited Recompile (rebuild one recent dead unit), or no revival at all
   (Light Cage)?
6. **Reference pictures:** he said he'd shape the faction from his own pictures. Send them now, or start from the Oct 28
   brief + ref37 and show concept sheets first?
