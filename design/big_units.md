# Big units and game enders (branch `big-units`)

lovish (Oct 11-12): "in tier 2 and tier 3 certain units should be larger than bipedal units ... with their respective
effects and stats reflecting their nature; they should have special attacks that make them win conditions if not
countered" - "they don't have to be withers and horses with knights, they can be anything" - "endgame buildings and
units that act as a win condition that can be countered if scouted early, but largely win due to their massive
resource investment and vulnerable status during long construction".

This doc designs **one large T2 beast/war-machine per faction** and **one endgame "game ender" building per faction**,
with the counter for each. What this branch builds is marked **[built]**; the rest is the spec the next slice follows.
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
| Tidewrought | **Shellback Siege-Turtle** | AM alligator snapping turtle x2.2, deck + mortar layer | *Moor*: anchors into a tidepool battery | immobile while moored; flank it | designed |
| Stormcallers | **Jade Qilin** | vanilla panda x2 (jade-and-gold), pagoda shrine layer | *Mana Spring*: energy battery + spell echo | an energy sink; Ink Scribes seal it | designed |
| Brassworks | **Steam Hauler** | AM tusklin-shaped boiler crawler (or iron golem on treads layer) | *Field Foundry*: mobile repair + turret drop | vents heat; Sappers | designed |

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

### 1.5 Shellback Siege-Turtle (Tidewrought, Drydock) - designed
- AM `EntityAlligatorSnappingTurtle` x2.2 with a deck, a mortar and a tide-bell (block layer). 700 HP, slow (0.15).
- **Moor** (toggle): it sinks its legs and becomes a static battery: 40% damage reduction, its mortar fires every
  3 s at range 24 with splash, and it **raises a permanent 5x5 tidepool** around itself while moored (the faction's
  Tides bonus for every escort). Unmooring takes 2 s.
- Counter: while moored it cannot move - flank it, bomb it (no AA), or drain the tidepool (the enemy-side counter
  already in the Tides mechanic).

### 1.6 Jade Qilin (Stormcallers, Celestial Observatory) - designed
- Vanilla panda x2 painted jade and gold with a pagoda shrine on its back. 450 HP, light melee.
- **Mana Spring**: +6 energy/s to its owner while alive, and friendly casters within 10 blocks pay 25% less mana.
- **Spell Echo** (passive, 30 s cooldown): the next spell a friendly caster within 10 blocks casts is repeated 1 s
  later at the same spot for half effect.
- Counter: Ink Scribe *Seal* silences it (no echo, no spring), it has no ranged attack, and killing it hurts the whole
  mana economy - a high-value raid target.

### 1.7 Steam Hauler (Brassworks, Assembly Works) - designed
- A boiler-crawler on six treads (iron golem body under a block-layer chassis, or AM tusklin scaled).
- **Field Foundry**: repairs machines within 8 blocks (3 HP/s, stacks with Engineer-Captains only up to 6 HP/s) and
  can **drop a Deployed Turret** every 40 s (the Guildmaster's turret, without the Guildmaster).
- Heat: each turret drop or 10 s of repairs adds heat; at 100 it vents 6 s (no repairs, no drops).
- Counter: Sappers and burrowers; kill it before the turret line forms.

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
  needs Tier 2 and a T2 constructor. Once built it charges one **interception** over 90 s (stockpile 1). A strike
  aimed within **40 blocks** of a charged Ward Spire owned by the target or an ally is **intercepted**: the ward spends
  its charge, the strike is announced as stopped and does nothing. A second strike needs a second ward or a recharged
  one - the ward buys time, it doesn't end the threat.
- **What the bot does** (`bot/BotGameEnders`): a hard bot that is floating metal with its T3 lab up builds its game
  ender near home; any bot (medium/hard) that learns of an enemy game ender builds a Ward Spire next to its capitol.

### 2.2 The seven

| Faction | Game ender | Strike | Faction-specific counter-play (besides the Ward Spire) | Status |
|---|---|---|---|---|
| Sunforged | **Sun Spire** | orbital solar lance: 10-block radius, 400 to buildings, 150 to units | the beam's 5 s column is visible: units walk out; buildings can't | **[built]** |
| Gravebound | **Necropolis** | mass raise: 10 undead (+1 per wreck nearby, max +8) rise at the target for 45 s | reclaim wrecks near your base; the risen crumble after 45 s | **[built]** |
| Ironhide Horde | **Doom Forge** | a meteor: 8-block crater, 500 to buildings, leaves fire | the forge needs 4 lava-fed bellows around it (destructible) | designed |
| Verdant Court | **World Seed** | converts a 16-block radius into grove: enemy buildings take 30/s for 20 s, Court units heal | burn it: fire does triple damage to the seedling | designed |
| Tidewrought | **Maelstrom Engine** | a whirlpool at the target: units pulled to the centre and battered, buildings flooded | the engine floods its own surroundings while charging - drain the tidepools to slow the charge | designed |
| Stormcallers | **Heaven's Bell** | a lightning storm over the target for 15 s | every stage drains 400 energy - E-stall the owner (kill winds) and it pauses | designed |
| Brassworks | **Earthcracker** | a drill line: a 3-wide fault 30 blocks long from the engine toward the target | it must stay connected by a rail of pylons; cut a pylon, it stops | designed |

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

### 2.5 The rest (next slice)
- **Doom Forge** (Horde): blackstone forge with four bellows (destructible sub-buildings: each one destroyed adds a
  stage of delay). Meteor: 8-block crater, 500 building damage, sets fire.
- **World Seed** (Verdant): a huge glowing seedling that grows a block taller every stage. Strike converts terrain
  (moss + azalea) in 16 blocks for 20 s: enemy buildings take 30/s, Court units heal 4/s. Fire triples damage to it.
- **Maelstrom Engine** (Tidewrought): a prismarine-and-copper pump that floods a ring of tidepools around itself
  while charging; draining them pauses the charge. Strike: whirlpool pull (Gravity Monk maths) + 40/s for 8 s.
- **Heaven's Bell** (Stormcallers): a jade bell tower; each stage costs 400 energy up front and pauses while the owner
  is E-stalled. Strike: 15 s storm (the Celestial Dragon's Tempest at triple size).
- **Earthcracker** (Brassworks): a steam drill tower that needs a rail of pylons (every 12 blocks) toward its target;
  strike opens a fault line that damages everything on it. Cut a pylon and it stops charging.

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
- Game enders are priced against the T3s: 5500M/12000E vs 1840M for an experimental - one strike can delete a capitol,
  but three T3s on the field for the same metal can delete a whole base and defend their own.

## 4. Open questions for lovish
- Should a game ender be aimable by the player (rally-point style) instead of auto-targeting the nearest capitol?
- Should game enders end the game outright (capitol destroyed = loss in 1v1, BAR-style), or only cripple the base?
- One shared Ward Spire, or a faction-flavoured counter per faction (a Sunforged "Sanctuary Bell", a Gravebound
  "Bone Ward")? The code supports variants; the art is shared for now.
