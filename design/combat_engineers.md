# Combat engineers - one per faction

lovish's vision list #18: "Metal reclaim from dead units by workers or **combat-engineer support units**". Wrecks and
reclaim exist (`resources/WreckServerEvents`); this adds the second half: a sturdy, quick builder in every faction that
marches with the army, strips the battlefield, mends what is hurt while the fight is still on, and throws up field
defences. Branch `combat-engineers`.

## What was referenced, and what it decided

| Source | What it does | What we took |
|---|---|---|
| **BAR, Consul** (Armada T2 vehicle lab, "Combat Engineer", beyondallreason.info/unit/armconsul) | 250 M / 4300 E, **1080 HP**, speed 78, **build power 150**, **no weapon**; builds mexes, solars, a lab, radar/jammer, walls, mines, light defences | T2 lab, sturdy, quick, unarmed in spirit, BP between a T1 and a T2 constructor |
| **BAR, Twitcher** (Cortex T2 bot lab, "Combat Engineer", unit page corfast) | 210 M / 3800 E, 830 HP, speed 90, BP 125, no weapon; "brings build power wherever it's needed", lays mines on flanks | same; mines = our traps |
| **BAR rules for every builder** | any constructor may **assist** any build and **repair** any unit or structure for free; reclaim pays metal; idle builders on "manoeuvre" repair what is near; fortification walls can be reclaimed, the enemy's too | free field repair of units, assist-anything, auto-repair when idle, the Horde's Tear Down |
| **SC2, SCV** | autocast repair, costs resources, mechanical only | auto-repair when idle (not paid: BAR wins over SC2 for a BAR game); repair works on every unit, one shared rule |
| **AoE II villagers** | repair buildings at a resource cost | rejected (BAR-first; building repair keeps RoN's existing path) |
| **Zero-K constructors** | every builder repairs, reclaims, terraforms; fights are won by keeping builders alive at the front | sturdiness is the engineer's identity, not damage |
| **Chess lens** (claude/design-principles.md) | tempo, piece activity, counterplay | each ability buys tempo and names its counter (table below) |

Our own docs read first: design/defences.md (the field-defence set: one shape and price per job), design/balance.md
(stat bands), design/big_units.md, the four faction plans, design/unit_skins.md, design/code_review.md,
design/ron_rewrite_plan.md and design/playtest_oct12.md (nothing there touches builders).

## Roster audit - who already does what

| Faction | T1 worker | T2 constructor | Reclaim / repair / builder-ish units already there | Gap the engineer fills |
|---|---|---|---|---|
| Sunforged | Villager (Master Mason) | Royal Architect (Holy Bell) | - | a builder that survives the front |
| Gravebound | Zombie Villager (x2 reclaim) | Embalmer (Soul Wisps: +50% reclaim speed nearby) | commander Raise Dead (wrecks -> Ghouls), Ossuary Behemoth eats wrecks | repair under fire; turning wrecks into HP (not more reclaim speed - the Embalmer has that) |
| Horde | Grunt (x1.5 reclaim) | Bonewright (Totem of the Pack) | - | a way through walls for Momentum charges |
| Verdant | Seedshaper (Vine Snare, Thicket) | Elder Druid (Awaken Thicket) | healers: Moonwell Bearer, Bloom Priestess | builder mobility (not terrain - the Seedshaper owns that) |
| Tidewrought | Shipwright | Harbourmaster (Great Tidepool) | Tide Priest heals in pools | contested-wreck reclaim (the Wreck Harbour faction) |
| Stormcallers | Geomancer (Ley Link) | Ley Master (Mana Well) | - | turning mana into repair |
| Brassworks | Tinker (Field Upgrade, mends machines 2 HP/s) | Engineer-Captain (mends machines 4 HP/s) | Sapper (charges on buildings), Repair Crawler (machine aura), Steam Hauler | burst build power (not machine repair or demolition - three units have those) |

## Shared rules (every combat engineer)

- **Lab: the faction's T2 lab, all seven.** BAR puts both of its combat engineers in T2 labs. T1 already has the
  commander and the T1 constructor for forward building, and a sturdy forward builder at T1 would let a player creep
  anti-raid towers across the map before four of the seven factions (Sunforged, Gravebound, Horde, Verdant) have any
  artillery to answer them - a tempo play with no counter. At T2 the wrecks are worth reclaiming (150-450 M units leave
  75-225 M heaps) and armies are big enough to need a mender. Gated by Tier 2 on the server like the other T2 items.
- **Build power 1.5** for all: more than a worker (1.0), less than the T2 constructor (2.0). Per 100 metal it is the
  dearest build power in the game (1.07 vs worker 2.0, T2 constructor 1.25): it pays for HP, speed and the field kit,
  so it never replaces workers for the economy.
- **Builds field defences only**: the faction's wall, gate, both traps and the anti-raid tower (design/defences.md).
  Not economy (that is the workers' job and keeps the T2 constructor relevant), not labs (a forward lab is a proxy
  rush with no build-up to scout), not the anti-T3 emplacement (a 5x5, 700 HP base commitment behind the T3 lab: thrown
  up next to an experimental it would make T3s unanswerable the other way round), not T2 towers. Server rule
  (`CombatEngineers.buildersMayPlace`): a placement whose builders are **all** engineers must be a field defence.
  **Assist is free for all** (BAR): in a mixed selection the workers place the building and the engineers help, and an
  engineer may repair or help finish any friendly site.
- **Reclaim**: they are workers, so wreck reclaim (5 metal/s per build power -> 7.5/s) and every faction's reclaim
  trait apply (Gravebound x2 -> 15/s, Horde x1.5 -> 11.25/s); area reclaim (Ctrl+right-drag) and RECLAIM orders work.
- **Field repair**: an idle engineer mends the most damaged friendly unit (own or allied, not itself) within 5 blocks
  for 2 HP/s per build power (3 HP/s), free, one target at a time, with a nanolathe beam. With no unit to mend it puts
  itself on the nearest damaged **own** building within 6 (the existing repair path: RoN's repair loop only counts a
  builder on its own side's building, so an ally's building would never get repaired - it is not offered). Idle = no
  order, not building, reclaiming, gathering or moving - so it works "under fire" but never overrides an order. An
  engineer standing on a wreck reclaims it instead (one job at a time, as BAR builders do).
- **Body and look**: illager body for six (the faction's worker body, an HD skin with a team region), the Horde's on
  the piglin body like its Grunt. One shared base class (`CombatEngineerUnit`) and the Horde's Grunt subclass both
  delegate to `unit/CombatEngineers`, so the rules live in one place.
- **Weapon**: a tool swing (2 damage, 0.5/s) - BAR's are unarmed; ours can at least finish a crippled raider.
  Retaliate off, never auto-acquires: it is not a fighter.
- **Ability on V** (hotkey1) for every engineer: free on all seven build menus, so one key means "engineer trick".

## The seven

| Faction | Unit | Lab (key) | Cost | HP | Speed | Body / look |
|---|---|---|---|---|---|---|
| Sunforged | **Field Mason** | Arcane Tower (I) | 145 M / 210 E, 32 s | 110 | 0.29 | illager in white plate with gold, a pavise on the back, team tabard |
| Gravebound | **Bonesetter** | Dungeon (Y) | 130 M / 200 E, 28 s | 90 | 0.30 | grey-skinned undead in a bone-plated apron, bone saw, team shroud |
| Horde | **Wrecker** | Flame Sanctuary (I) | 150 M / 220 E, 32 s | 120 | 0.29 | piglin in a riveted rust apron, scrap pack and crowbar, team sash |
| Verdant | **Rootwarden** | Circle of Elders (I) | 140 M / 210 E, 30 s | 90 | 0.32 | elf in bark mail with moss pauldrons, root crook, team hood |
| Tidewrought | **Salvager** | Drydock (Y) | 140 M / 210 E, 30 s | 100 | 0.31 | diver with a brass helmet ring, coiled hook line, team scarf |
| Stormcallers | **Jade Mender** | Celestial Observatory (P) | 135 M / 230 E, 30 s | 90 | 0.30 | monk in lacquer and jade plates, mallet, team stole |
| Brassworks | **Pioneer** | Assembly Works (O) | 150 M / 200 E, 32 s | 120 | 0.28 | armoured dwarf in a riveted cuirass, steam rig on the back, team cap |

The bands follow the faction lines: plate and dwarves are sturdy and slow, the Court is fast and light, the
Gravebound cheap (numbers, and their x2 reclaim is their edge), the Stormcallers pay energy (mana). Population 2.

### Abilities (all on V)

| Unit | Ability | What it does | Tempo it buys | Readable counter |
|---|---|---|---|---|
| Field Mason | **Bulwark** | a friendly building within 8 takes **half damage from attackers** for 12 s while the Mason stays alive and within 12 of it; 35 s cooldown | holds a gate or tower through one push while it is repaired | gold shimmer and a beam to the Mason: kill or chase off the Mason (the tether breaks), outlast it, or hit another building. Game-ender strikes and com-blasts are not attacks and ignore it |
| Bonesetter | **Bone Graft** | rips the metal out of a wreck within 8 to mend a friendly unit within 8: 2 HP per metal, at most 50 metal (100 HP) per cast; 20 s cooldown; refused without a wreck | saves a key unit mid-fight | needs a wreck: reclaim or hook the field first, wrecks decay; one burst on one unit (switch target); the metal is gone from both sides' economy |
| Wrecker | **Tear Down** | rips down an **enemy wall segment, gate or revealed trap** within 4 at once, and reclaims half its metal; 25 s cooldown | opens a breach for a Momentum charge | one segment per 25 s (a long wall still costs time), it must stand at the wall under the towers' fire, hidden traps can't be targeted, towers and labs are out of reach |
| Rootwarden | **Rootwalk** | sinks for 2 s, then rises beside an own building within 40; hurt while sinking and it fails (half cooldown); 40 s cooldown | moves repair power to a raided outpost, or saves the engineer | the 2 s sink is telegraphed with roots at both ends: hit it to cancel, or meet it where it rises |
| Salvager | **Salvage Hook** | hooks a wreck within 16 and reels it to its feet over 1.5 s; 18 s cooldown | takes contested wrecks out of the enemy's reach | the wreck crawls in a straight visible line and can still be reclaimed on the way; kill the Salvager and the hook drops |
| Jade Mender | **Mending Rite** | channels into a friendly unit within 8: 8 HP/s (x spell power: Overcharge, Mana Well) for up to 6 s, draining 15 energy/s; 25 s cooldown | turns mana into a fast heal on the front | a jade beam you can see: hurt the Mender and it breaks, the Ink Scribe's Seal stops it, it stalls the caster's economy like every spell |
| Pioneer | **Overdrive** | x3 build power for 8 s (construction, reclaim and field repair), then a 4 s vent with no work at all; 30 s cooldown | a tower up before the raiders arrive, a wreck field gone in seconds | the steam plume says when; the vent is the window to hit the half-built site or the crew |

## Code

| Piece | Where |
|---|---|
| Shared rules (build list, placement gate, field repair, constants) | `unit/CombatEngineers.java` |
| Marker interface (the unit's ability for bots and tests) | `unit/interfaces/CombatEngineer.java` |
| Illager-body base + six units | `unit/units/engineers/CombatEngineerUnit.java`, `FieldMasonUnit`, `BonesetterUnit`, `RootwardenUnit`, `SalvagerUnit`, `JadeMenderUnit`, `PioneerUnit` |
| Horde (piglin body) | `unit/units/engineers/WreckerUnit.java` (a `GruntUnit`, the Bonewright pattern) |
| Production items | `unit/units/engineers/CombatEngineerProd.java` (+ seven one-line subclasses: EntityRegistrar's switch needs each `itemName` constant) |
| Client build menu (field defences only) | `unit/CombatEngineerButtons.java` (cached per faction, built once) |
| Abilities | `ability/abilities/Bulwark`, `BoneGraft`, `TearDown`, `Rootwalk`, `SalvageHook`, `MendingRite`, `Overdrive` (UnitAction values appended at the end) |
| Renderers | `unit/modelling/renderers/CombatEngineerRenderer.java` (illager), `WreckerRenderer.java` (piglin) |
| Bot | `bot/BotCombatEngineers.java`, one hook line in `BotPlayer.think` |
| Tests | `gametest/CombatEngineerGameTests.java` |
| Skins, portraits | `tools/gen_hd_skins.py` (seven painters), `textures/entities/*_unit(_team).png`, `textures/mobheads/*.png` |
| Registration lines | EntityRegistrar, CommonModEvents (attributes), ClientModEvents (renderers), ReignOfNetherCommonConfigs + ResourceCosts (costs), ProductionItems, Factions, the seven T2 labs, `BuildingServerEvents.placeBuilding` (placement gate), `BuildingPlacement.destroyRandomBlocks(amount, attacker)` (Bulwark), lang |

Hot-path budget: field repair runs once a second per idle engineer (one UnitGrid query, the reused scratch list, no
allocation); the building fallback scans the building list only when no unit needs mending. Bulwark's damage hook is
an empty-map check unless a Bulwark is up. Hook and Rite records are short lists ticked by the server.

## Bots

`bot/BotCombatEngineers.think`, called from one line in `BotPlayer.think` right after it has sorted its units and
buildings (branch bots-4 edits `bot/` too: the footprint there is that line):

- **Out of the worker pool**, always: the economy rules never hand an engineer an extractor or a lab to place (the
  server would refuse it), and the worker count stays a count of workers.
- **Training** at the first built T2 lab once Tier 2 is in and the army has 6 units: medium 1, hard 2, one more per 12
  fighters, at most 3 (trained plus queued); easy none.
- **Orders**, every 2 s: an idle engineer reclaims the nearest wreck within 14 of itself and of the army's centre (a
  RECLAIM order, as the workers' `sendReclaimer`); otherwise one that has fallen 10 behind walks to a point 6 blocks
  behind the army's centre (toward home) - one MOVE, not repeated while it walks. Its own field repair does the mending.
- **A forward anti-raid tower** at the rally point while the army gathers there (BUILD_UP): hard every 2 min, medium
  every 4, one per rally point, never with metal under 15% or before the enemy's direction is known.
- **Abilities**, cheap tests first, scans only for an engineer off cooldown: Bulwark on an own building under 85% with
  an enemy within 16; Bone Graft on a friend under 60% with a wreck in reach; Tear Down on an enemy wall, gate or
  revealed trap in reach (while attacking, the Wrecker walks up to one within 12 first); Rootwalk out of trouble (under
  45% with an enemy within 8: the own building in reach farthest away) or, while the army gathers, to the worst-hurt own
  building 16-40 blocks off; Salvage Hook on the richest wreck 6-16 blocks out; Mending Rite on a friend under 50% with
  the energy pool over 25%; Overdrive while building or reclaiming.

## Tests (`gametest/CombatEngineerGameTests`)

- `combat_engineers_train_at_their_t2_labs`: every faction's engineer item is at its T2 lab (the bot kit's too), is a
  `CombatEngineerProd`, names an entity of its faction, is refused without Tier 2 and queued with it; completing it
  (the real `onComplete -> produceUnit` path) makes an owned engineer worker with 1.5 build power, its cost, its health
  and its ability on V.
- `combat_engineer_skins_portraits_and_lang_ship_in_the_mod`: skins, team textures, portraits, names, tooltips and every
  HUD message the gate and the abilities use.
- `combat_engineers_reclaim_wrecks_at_their_build_power`: all seven, one reclaim step each, against the formula (faction
  trait and pace included) and faster than a 1-BP worker.
- `combat_engineers_field_repair_friends_not_foes_or_themselves`: through the engineer's own repair step.
- `combat_engineers_start_field_defences_only`: every field defence allowed, the anti-T3 emplacement, extractor, wind,
  army lab and its own T2 lab refused (also by the real `placeBuilding`), all allowed with a worker among the builders.
- `combat_engineers_build_their_anti_raid_towers`: each engineer alone places its faction's anti-raid tower through the
  real placement path (on pads high in the test's column), then flow construction advances on its build power.
- One per ability with its counter: `bulwark_halves_damage_and_survives_a_tear_down` (also the Wrecker: the unbraced wall
  falls and refunds half its metal; refusals; the tether), `bone_graft_turns_a_wreck_into_health`,
  `rootwalk_rises_beside_a_building_unless_hurt`, `salvage_hook_reels_a_wreck_to_the_salvager`,
  `mending_rite_heals_until_the_mender_is_hurt`, `overdrive_triples_build_power_then_vents` (and reclaim reads it).
- `bots_pull_engineers_from_workers_and_train_a_few`: the bot pieces (pool split, training numbers per difficulty).

The duplicate-hotkey, icon, ability-leak, reload and spawn-at-every-lab tests already scan every unit type and cover the
seven. `BrassworksGameTests.assembly_works_trains_every_brassworks_t2_unit` counts the Works' eight T2 units and asks
each for the `BrassT2Prod` gate: it now skips the Pioneer (one line), which has the engineers' gate and its own tests.

## Skins

Seven painters in `tools/gen_hd_skins.py` (section "COMBAT ENGINEERS"), run with
`python3 tools/gen_hd_skins.py --only field_mason,bonesetter,wrecker,rootwarden,salvager,jade_mender,pioneer` (writes
`textures/entities/<id>_unit.png`, `<id>_unit_team.png` and `textures/mobheads/<id>.png`; a partial run leaves the
contact sheets alone). Team regions: the Mason's chest tabard, helm cover and pauldron edges; the Bonesetter's shroud
hood; the Wrecker's sash; the Rootwarden's hood; the Salvager's scarf; the Mender's stole and headband; the Pioneer's
cap. As with every worker of the three core factions, the livery (UnitDressServerEvents gives workers a chest piece)
covers the Field Mason's, Bonesetter's and Wrecker's torso in game - design/unit_skins.md finding 2 applies.

## Balance

`tools/unit_stats.py` lists them as tier `engineer` (role builder, excluded from the medians); `balance_audit.py`
prints them in their own table. Suggested duel rows for `balance-2` (owner of DuelGameTests this round): 2 engineers
behind 4 T1 lines vs 6 T1 lines (does field repair win an equal-metal line fight?), Bone Graft with a wreck field, and
Bulwark on an anti-raid tower against 6 raiders.

## Not done / proposals

- Building repair speed still ignores build power (RoN's builder-count loop in `BuildingPlacement.handleServerTick`);
  BAR repairs at build power. Out of this lane - proposal for the building-core rewrite (ron_rewrite_plan #6).
- Unit repair is free (BAR). If 8v8 playtests show menders out-healing T1 lines, cap menders per target or cost energy.
- The UnitInfoCard role reads "Worker" for engineers (the worker rule); a role "Engineer" needs a lang key and a HUD
  pass.
- The core factions' livery hides most of three engineers' skins (above). Proposal: skip the worker chest piece for
  combat engineers in `UnitDressServerEvents` (or adopt unit_skins.md's "commander + boots only" recommendation).
- Not verified in game (no client in CI): the renderers, the skins on the models, the build menu of field defences,
  the beams and particles, the ability buttons' icons.
