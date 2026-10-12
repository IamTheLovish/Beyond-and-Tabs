# Big units and game enders (branches `big-units`, `beasts-2`)

lovish (Oct 11-12): "in tier 2 and tier 3 certain units should be larger than bipedal units ... with their respective
effects and stats reflecting their nature; they should have special attacks that make them win conditions if not
countered" - "they don't have to be withers and horses with knights, they can be anything" - "endgame buildings and
units that act as a win condition that can be countered if scouted early, but largely win due to their massive
resource investment and vulnerable status during long construction".

This doc designs **one large T2 beast/war-machine per faction** and **one endgame "game ender" building per faction**,
with the counter for each. Round 1 (`big-units`) built four beasts, the framework, the Sun Spire and the Necropolis;
round 2 (`beasts-2`) built the other three beasts and five game enders, refining each design first (below: "as built").
Everything is now **[built]**.
Paths are relative to `bar/src/main/java/com/solegendary/reignofnether/`.

---

## 0. The lens (why these and not bigger T3s)

**BAR.** BAR's late game has two kinds of game-changers: *experimentals* (Juggernaut, Krogoth, Ragnarok-class units)
that are concentrated metal and lose to the same metal in focused T2, and *game enders* - the nuke silo, the
Ragnarok/Vulcan/Calamity super-cannons, the Starfall. A game ender is a **building**, it costs as much as an army, takes
minutes to build *and* minutes to charge or stockpile, and every one of them has a direct answer: the anti-nuke
(an interceptor with its own stockpile), a raid on the silo while it charges, or a commando strike on the energy that
feeds it. A player who scouts it early wins the race; a player who doesn't loses their base.

**Chess.** A game ender is a *threat with a clock*. The opponent needs three things to answer it, and the design gives
each one on purpose:
1. **Information** - the start is announced to every player with its position (an alert you can jump the camera to).
   Hiding it would make scouting irrelevant and the game ender a coin flip; announcing it makes it a *tempo* question.
2. **Time** - long construction (minutes, even with T2 builders) and then three charge stages. Each stage is
   announced. Every second the owner spends here is metal not in the army: the opponent can *attack elsewhere*
   (the counter-attack is the classic answer to a slow plan), *raid the charging building* (stages are raidable),
   or *build the counter* (prophylaxis).
3. **A cheap, specific answer** - the Ward Spire (BAR's anti-nuke): ~12% of the game ender's price, protects a radius
   around itself, and must have finished charging its own interception before the strike lands. A player who builds
   it early is safe from one strike; a greedy player who skipped it is not. A second strike needs a second charge,
   so the ward does not end the threat, it buys time.

**T2 beasts** are the mid-game version of the same idea: a big, expensive body with one mechanic that wins the fight
if the opponent ignores it, and an obvious way to answer it (kill the aura carrier, reclaim the wrecks before it eats
them, step out of the charge lane, shoot the carrier before it unloads). They follow the T3 rules scaled down: cost
~2-2.5x a faction's T2 heavy, a clear weakness, no instant win.

Bodies: the big Alex's Mobs animals (elephant, rhinoceros, bison) and vanilla mobs, scaled, with **render layers**
built from block models (the Leviathan's fortress trick) so each reads as a war-beast from the RTS camera without new
model files. A unit-skins pass can repaint them later; the layers carry the faction look meanwhile.

---

## 1. T2 beasts

| Faction | Beast | Body | Signature | Weakness / counter | Status |
|---|---|---|---|---|---|
| Sunforged | **Oriflamme Elephant** | AM elephant, tusked, white-and-gold caparison, sun banner + bell howdah layer | *Oriflamme* aura | slow, modest damage, the aura dies with it - focus it | **[built]** |
| Ironhide Horde | **Gorehorn Rhino** | AM rhinoceros x1.35, rust iron plate + spike rack layer | *Breach Charge* lane through units **and buildings** | 25 s cooldown, telegraphed lane, short reach once spent | **[built]** |
| Gravebound | **Ossuary Behemoth** | vanilla zoglin, grows x1.5 -> x2.1, bone-heap layer | *Grave Feast*: eats wrecks to grow | starts weak; reclaim the wrecks first; blunt/holy | **[built]** |
| Verdant Court | **Mossback Bison** | AM bison x1.5, living grove on its back | *Hollow Grove*: carries 4 infantry, heals them | carriers die = riders spill out hurt; AA irrelevant, slow-ish | **[built]** |
| Tidewrought | **Shellback Siege-Turtle** | vanilla sea turtle x2.4, timber fort + deck gun + bell layer | *Moor*: anchors into a fort - half damage, tidepool, deck battery | can't move anchored (2 s either way); battery dead zone at its shell; drain the pool | **[built]** |
| Stormcallers | **Jade Qilin** | vanilla white horse x1.5, jade scale + shrine layer | *a walking Mana Well* + *Mana Spring* energy | Ink Scribe Seal silences it; no ranged attack; focus it | **[built]** |
| Brassworks | **Steam Hauler** | vanilla sniffer x1.25, boiler + smokestack + crane layer | *Field Foundry*: area repair of machines + Drop Turret | repairs heat it into vents; Sappers/Burrowers behind the line | **[built]** |

### 1.1 Oriflamme Elephant (Sunforged, Arcane Tower) **[built]**
`unit/units/villagers/OriflammeElephantUnit` (extends AM `EntityElephant`, Unit boilerplate from the Stag Lancer).
- 520 HP, 9 dmg, 0.6 APS, speed 0.20, 25% ranged resist, knockback-proof. 780 metal: ~1.9x the Siege Ox.
- **Oriflamme** (passive aura, radius 8, refreshed every second): friendly and allied units in it deal **+20%
  damage** and take **-15% damage**. One elephant's aura; two never stack (BAR rule: no multiplicative buff piles).
  The Formation Banner faction gets its banner as a beast: a Sunforged army fighting under the elephant beats an
  equal-metal army that ignores it, so it is a *win condition if not countered*.
- Counter: it is the priority target (big, slow, 9 damage); artillery and snipers outrange the aura; separating the
  army from it (pulls, slows) halves its value.
- Look: tusked, yellow carpet (AM's own caparison layer), a gilded howdah with a sun banner pole and a bell (block
  layer).

### 1.2 Gorehorn Rhino (Ironhide Horde, Flame Sanctuary) **[built]**
`unit/units/piglins/GorehornRhinoUnit` (AM `EntityRhinoceros`).
- 300 HP, 10 dmg, 0.6 APS, speed 0.24, 2.5x building damage. ~2.2x the Wither Skeleton.
- **Breach Charge** (aimed, 25 s cooldown): the rhino charges up to 14 blocks along a straight lane (one block a
  tick). Every enemy unit it passes within 1.8 blocks takes 16 damage and is flung aside; the first **building** in its
  way stops it and takes **120 damage** (a gate-breaker - Momentum's identity as a siege tool). A 0.5 s wind-up shows
  the lane first (BAR rule: danger is shown).
- Different from the War Mammoth's Trample: Trample is an instant cone in front of a T3 that never touches buildings;
  Breach is travel - it moves the rhino, so it is also an engage/escape tool, and it is the Horde's T2 answer to walls.
- Counter: the lane is telegraphed and straight - spread out or step aside; after the charge it is deep in your army
  with 25 s to wait. Static defences behind a wall make it charge the wall, not the army.

### 1.3 Ossuary Behemoth (Gravebound, Dungeon) **[built]**
`unit/units/monsters/OssuaryBehemothUnit` (extends `ZoglinUnit`).
- Starts at 260 HP, 7 dmg, 0.7 APS, speed 0.22; ~1.9x the Warden.
- **Grave Feast** (passive): every second it eats the nearest wreck within 6 blocks (any faction's - wrecks belong to
  nobody, `resources/WreckServerEvents`). Each wreck is a **Bone Stack** (max 6): +45 max HP (and heals 45), +1.5 damage,
  and the body grows (x1.5 -> x2.1). The eaten wreck's metal is gone - nobody can reclaim it.
- "Every wreck is an army": in a long war of attrition it becomes a 530 HP, 16 damage brute - a win condition if the
  opponent leaves the dead lying around.
- Counter: kill it early (it is a below-par T2 body at 0 stacks), **reclaim the wrecks first** (the race for the
  battlefield's metal is the chess here), and blunt / holy damage as for every Gravebound bone body.
- Stacks are lost on death; they are kept across save/load.

### 1.4 Mossback Bison (Verdant Court, Circle of Elders) **[built]**
`unit/units/verdant/MossbackBisonUnit` (AM `EntityBison`).
- 280 HP, 7 dmg, 0.7 APS, speed 0.27 (faster than any T2 infantry), 20% ranged resist. ~2x the Stag Lancer.
- **Hollow Grove** - carries up to **4** friendly infantry (any unit at most 1 block wide and 2.5 tall that isn't a
  vehicle): *Embark* loads every eligible friend within 6 blocks; *Eject* unloads them all. Riders sit in the grove on
  its back, take **50% less damage**, heal **2 HP/s**, and ranged riders keep shooting from up there. If the bison dies
  its riders spill out where it fell.
- The Court's mobility signature at T2: four Thornbows or Shade Rangers moved across the map at cavalry speed and
  dropped behind a line - a raid that wins if not scouted.
- Counter: kill the carrier before it unloads (the riders land hurt and scattered), anti-raid towers at the back of the
  base, and spotting it on the way (it is big).

### 1.5 Shellback Siege-Turtle (Tidewrought, Drydock) **[built]**
`unit/units/tide/ShellbackTurtleUnit` (vanilla `Turtle`), `ability/abilities/Moor`, `ShellbackTurtleRenderer`.
- **Refined.** The first draft put a mortar on an alligator snapping turtle - but the Mortar Galley already *is* that
  (the faction's artillery). The Shellback is instead the fleet's **mobile harbour**: the island-turtle of sailors'
  tales, a vanilla sea turtle x2.4 with a timber fort, a deck gun, a tide bell and lanterns on its shell.
- 560 HP, 9 dmg bite, 0.6 APS, speed 0.15, 2 armour, 20% ranged resist; 545M/470E (2.1x the Mortar Galley).
- **Moor** (toggle): anchoring takes 2 s and the turtle can't move from the first tick until it has fully weighed
  anchor again (2 s more). Moored it takes **half damage**, stops biting, raises a radius-3 **tidepool** round itself
  (the Tides bonus for every Tidewrought unit fighting there, and wet ground for the Galleys; re-raised every 10 s if
  drained, drained when it leaves) and fires its **deck battery** every 2 s: 12 at the nearest enemy unit 4-16 blocks
  away, else 30 at the nearest enemy building in that band - the "siege" in its name.
- Counter: it is a sitting target once anchored and needs 2 s to leave; the battery can't hit the 4-block **dead zone**
  at its shell, so melee that reaches it is safe from the gun; artillery out-ranges it; any constructor drains its pool.
- Bot: moors when an enemy is in the band and nobody at its shell; weighs anchor when nothing is in range or melee
  reaches it (`BotGameEnders.turtleWantsMoor`).

### 1.6 Jade Qilin (Stormcallers, Celestial Observatory) **[built]**
`unit/units/storm/JadeQilinUnit` (vanilla `Horse`), `JadeQilinRenderer` (vanilla's HorseRenderer is final: its body
re-done on `AbstractHorseRenderer`, white coat only).
- **Refined.** A panda is not a qilin and the mod already has a neutral Panda unit; the qilin is a hooved, scaled
  beast, so the body is the vanilla horse (white coat) under jade scale plates, cloud puffs and a little shrine.
  *Spell Echo* was dropped: repeating "the next spell" needs a hook in every spell, and the Stormcallers already have
  a location rule that does the job - the Mana Well. The qilin **is a walking Mana Well**.
- 420 HP, 8 dmg, 0.7 APS, speed 0.30, 30% magic resist; 610M/520E (2x the Earthshaker).
- **Auspice**: a spell its side casts within 10 blocks of a live, unsealed qilin counts as cast inside a Mana Well
  (x1.25 power for x0.8 cost; `ManaServerEvents.insideWell` asks `BigUnitServerEvents.inQilinWell`). Never stacks with
  a real well or a second qilin. **Mana Spring**: +6 energy a second to its owner (storage-capped).
- Counter: an Ink Scribe's **Seal** switches both off; no ranged attack, light hooves; the obvious focus/raid target.

### 1.7 Steam Hauler (Brassworks, Assembly Works) **[built]**
`unit/units/brass/SteamHaulerUnit` (vanilla `Sniffer`, a `brass.Machine`), `ability/abilities/DropTurret`,
`SteamHaulerRenderer`.
- **Refined.** The sniffer's plodding walk sells a traction engine better than a golem on treads; the "stacks with
  Engineer-Captains up to 6 HP/s" rule became simpler and stronger in a different way: the hauler mends **every**
  machine round it (a Tinker mends one at a time) but two haulers never mend the same machine in the same second.
- 480 HP, 6 dmg ram, 0.6 APS, speed 0.20, 4 armour, 30% ranged resist; 630M/480E (2x the Steam Juggernaut).
- **Field Foundry**: once a second every damaged friendly machine within 8 blocks mends 3 HP; a second of repairs adds
  7 heat (it cools 4/s), so a long repair run ends in a vent. **Drop Turret** (aimed, 6 blocks, 25 s cooldown): a free
  Deployed Turret, +35 heat. At 100 heat it vents 6 s (brass/Heat): no repairs, no turrets, 25% more damage taken. It
  is a machine itself: Tinkers plate and mend it, another hauler mends it.
- Counter: hit it while it vents; Sappers and Drill Burrowers reach it behind the machine line; it is slow and big.

---

## 2. Game enders

### 2.1 Shared rules **[built: `building/gameender/`]**
- **Building, not unit.** Very expensive (~3x a T3: 5500 metal / 12000 energy class), built only by T2 constructors
  (`T2Workers.needsT2Builder`), needs Tier 3 research and the faction's T3 lab (build button gate).
- **Long construction** (`buildTimeBlocks` 1200: ~4.2 min with six T2 builders' worth of build power), vulnerable
  the whole time. 2400 HP once built.
- **Announced when placed** - every player gets a chat line with the owner, the game ender and its x/z, and every
  enemy gets an attack-warning ping at the site (their alert hotkey jumps the camera there). Allies get the same line.
- **Charging stages.** Once built it charges through **3 stages** of 50 s each; each stage is announced. While its
  owner lacks Tier 3 it does not charge.
- **Raidable stages.** Every 10% of max HP it loses while charging knocks it back one stage (to a minimum of 0).
  A raid that does 30% damage resets a full charge.
- **The strike.** At full charge it picks its target - the nearest enemy capitol, else the nearest enemy building -
  announces it to the victim, telegraphs it for 5 s (a column of particles at the spot), then resolves. Then it
  starts charging again from stage 0 (BAR silos stockpile; this one recharges).
- **The counter: Ward Spire** (every faction, one shared building with a faction variant). 650 metal / 2500 energy,
  needs Tier 2; any constructor builds it (the answer must be in reach of whoever scouted the threat). Once built it charges one **interception** over 90 s (stockpile 1). A strike
  aimed within **40 blocks** of a charged Ward Spire owned by the target or an ally is **intercepted**: the ward spends
  its charge, the strike is announced as stopped and does nothing. A second strike needs a second ward or a recharged
  one - the ward buys time, it doesn't end the threat.
- **What the bot does** (`bot/BotGameEnders`): a hard bot that is floating metal with its T3 lab up builds its game
  ender near home - every faction now has one (`gameEnderFor`); any bot (medium/hard) that learns of an enemy game
  ender builds a Ward Spire next to its capitol. In fights the bot casts the beasts' abilities (Breach Charge, Hollow
  Grove, Moor, Drop Turret).

### 2.2 The seven

| Faction | Game ender | Strike | Faction-specific counter-play (besides the Ward Spire) | Status |
|---|---|---|---|---|
| Sunforged | **Sun Spire** | orbital solar lance: 10-block radius, 400 to buildings, 150 to units | the beam's 5 s column is visible: units walk out; buildings can't | **[built]** |
| Gravebound | **Necropolis** | mass raise: 10 undead (+1 per wreck nearby, max +8) rise at the target for 45 s | reclaim wrecks near your base; the risen crumble after 45 s | **[built]** |
| Ironhide Horde | **Doom Forge** | meteor: 8-block radius, 500 to buildings, 120 to units (thrown out), then a crater that burns 15 s | *Stoke*: each stage burns 350 metal up front - raid the extractors and it waits | **[built]** |
| Verdant Court | **World Seed** | a Heartroot bursts up at the target; 12 blocks round it are grove while it lives (20 s max): buildings 25/s, foes 4/s + slowed, Court units heal | kill the Heartroot (400 HP, rooted) and the grove dies at once | **[built]** |
| Tidewrought | **Maelstrom Engine** | whirlpool 8 s, radius 9: foes dragged in and swirled, 10/s; buildings 45/s | it charges only as fast as its ring of 3 tidepools is full - drain them (reclaim, fire) | **[built]** |
| Stormcallers | **Heaven's Bell** | 15 s storm, radius 10: a bolt every 0.5 s - a random foe unit (22), else the nearest building (60) | each stage costs 400 energy up front - kill the winds and it falls silent | **[built]** |
| Brassworks | **Earthcracker** | a fault 32 long through the target on the drill's bearing, racing end to end in 3 s: 450 to buildings it crosses, 110 to units on it | it vents 12 s after each stage (announced): no charge, raids count double | **[built]** |

### 2.3 Sun Spire (Sunforged) **[built]**
`building/gameender/SunSpire` + `SunSpirePlacement`: a white-and-gold quartz spire with a beacon crown
(`sun_spire.nbt`, tools/gen_structures.py). Strike: after the 5 s column, every enemy building with its centre within
10 blocks takes 400 (block damage spread like any hit), every enemy unit within 10 blocks takes 150 (not magic, armour
soaks it). Friends never.

### 2.4 Necropolis (Gravebound) **[built]**
`building/gameender/Necropolis` + `NecropolisPlacement`: a black-stone and bone mausoleum with soul-fire braziers
(`necropolis.nbt`). Strike: 10 risen (zombies and skeletons, alternating) appear around the target for its owner,
plus one for every wreck within 16 blocks of the target (eaten; at most +8). Risen units leave no wreck, count no
kills and crumble after 45 s (`DragonRaiseServerEvents.TAG`). An army raised inside the enemy base is the win
condition; a base with a charged Ward Spire, or one that reclaimed its wrecks and kept an army home, survives it.

### 2.5-2.9 Round 2 as built (`beasts-2`)
**Framework hooks** (`GameEnderPlacement`): `payForStage` (once per stage before it charges, retried each second while
it can't pay; a stage a raid knocks the charge back into is never paid twice), `chargeRate` (0..1 per tick, a
fractional accumulator), `onStageComplete`, and an `aftermath` counter with `tickAftermath` for strikes that linger
(they run on during the recharge; destroying the game ender ends them). Shared helpers `enemyBuildingsNear` /
`enemyUnitsNear`, and `payUpfront` (an owner without a pool pays nothing). Every strike still goes through the same
announce - stages - telegraph - **Ward Spire intercept** path: an intercepted strike leaves no aftermath.

**Why these twists, not the first drafts.** Every faction twist had to be something a scout can *act on* and the
server can check cheaply: the Doom Forge's destructible bellows would have been four extra buildings (or random block
damage nobody could aim); the World Seed's "fire triples damage" has no hook (buildings take block damage, not damage
types); the Earthcracker's pylon rail across the map is a second building game. They became an economy raid (Forge,
Bell - the "commando strike on the economy that feeds it" from BAR), a target you can kill (the Heartroot), a pool ring
you drain with the counterplay the Tides already have (Maelstrom), and a scheduled raid window (Earthcracker vents).

- **Doom Forge** (Horde; `DoomForge(Placement)`, `doom_forge.nbt`: blackstone forge-ziggurat, four lit blast-furnace
  bellows with smoking chimneys, magma heart). 4500M/12000E + 3 x 350 metal stokes. Meteor as in the table; the crater
  burns enemy units (6/s) and buildings (20/s) for 15 s - particles only, never fire blocks.
- **World Seed** (Verdant; `WorldSeed(Placement)`, `world_seed.nbt`: a glowing seedling on a mossy mound with azalea
  seed-leaves). 5500M/12000E. The Heartroot is a rooted Sentinel Treant body (speed 0, 400 HP) of the seed's owner with
  the risen tag (no wreck, no kill credit), crumbling when the grove's 20 s end. Fully grown the grove does 500 to a
  building - more than the Sun Spire - and every second the defender spends killing the Heartroot takes 25 off that.
- **Maelstrom Engine** (Tidewrought; `MaelstromEngine(Placement)`, `maelstrom_engine.nbt`: prismarine pump house, copper
  drum, the conduit on top). 5500M/12000E. Its 3 radius-1 pools (TidepoolServerEvents.raiseForBuilding - their own
  cap, never evicting the owner's priest pools) are re-raised every 15 s, so a raider who keeps draining keeps it slow.
- **Heaven's Bell** (Stormcallers; `HeavensBell(Placement)`, `heavens_bell.nbt`: jade-roofed red tower, a belfry with
  the bell, a lightning rod). 5500M/10800E + 3 x 400 energy. Bolts are visual-only lightning; the damage is ours.
  Units first: an army standing in the base shields its buildings with its bodies; spreading out saves the army.
- **Earthcracker** (Brassworks; `Earthcracker(Placement)`, `earthcracker.nbt`: deepslate engine house, iron derrick
  banded in brass, a drill piston, smokestacks). 5500M/12000E. The telegraph draws the fault itself, so units can step
  off the line; a building beside the line is untouched.

---

## 3. Balance (tools/balance_audit.py, gametest/DuelGameTests)
- Beasts are listed in `tools/unit_stats.py` as T2 with the role `beast`: exempt from the tier medians (they pay for a
  signature the per-target columns can't see) and never a faction's "T2 heavy" for the T3 cost rule.
- The duel matrix adds a "Beast" row: each beast alone against the equal-metal T2 counter mix the ground T3s meet.
  - First numbers (scaled off the War Mammoth: 480-620 HP, 9-14 damage) won every duel 1.7x-25x. Two causes: T2
    bodies are 100-380 HP, and AM's own attack animations dealt damage on a tick of the animation on top of RoN's attack
    goal (the rhino's fling, the bison's gore, the elephant's stomp). Those animations are now blocked
    (`setAnimation` lets only cosmetic idles through; grazing, which eats grass and heals, too).
  - Second pass (CI run 38211418440): Gorehorn Rhino 300 HP beat golem + warden 1.1x (even - plus Breach Charge in a
    real army); Ossuary Behemoth 260 HP lost 1.2x at zero stacks (by design: it has to feed); Mossback Bison 280 HP beat
    a lone golem 1.6x (the mix could only fit 265 of its 465 metal); Oriflamme Elephant 450 HP lost 6.9x to golem +
    warden + Wither Skeleton (stacking wither is the answer to one big body). The elephant is now 520 HP / 9 damage at
    780 metal: an aura carrier may lose alone, not that badly. Watch it in playtests with an army under it.
- Round 2 beasts (545-630M, ~2-2.1x their T2 heavy) have duel rows too; a duel never moors the turtle, gives the
  qilin no spells to bless or the hauler no machines to mend, so losses there are expected - watch them in playtests
  with an army round them.
- Game enders are priced against the T3s: 5500M/12000E vs 1840M for an experimental - one strike can delete a capitol,
  but three T3s on the field for the same metal can delete a whole base and defend their own.

## 4. Open questions for lovish
- Should a game ender be aimable by the player (rally-point style) instead of auto-targeting the nearest capitol?
- Should game enders end the game outright (capitol destroyed = loss in 1v1, BAR-style), or only cripple the base?
- One shared Ward Spire, or a faction-flavoured counter per faction (a Sunforged "Sanctuary Bell", a Gravebound
  "Bone Ward")? The code supports variants; the art is shared for now.
