---
title: Beyond and Tabs - faction-themed vehicles (design doc for approval)
type: design
project: Beyond and Tabs
status: draft v2 for lovish (revised Oct 28, see Revision notes) - no code until approved
created: 2026-10-28
source: lovish's words in [[20261028-0250-bat-infantry-buildings-vehicle-shapes]]; repo main read-only (AGENTS.md, design/LANES.md, design/balance.md, design/big_units.md, the faction plans, raid/RaidTuning.java, PR #146)
---

# Faction-themed vehicles

## Revision notes (Oct 28, design revision pass before anything is committed)

What changed in this pass and why. Open questions from all three docs now live in one file:
[[open-questions-2026-10-28]]; section 9 below only records lovish's answers.

- **Names.** The faction is called **Ashborn** everywhere (main's code still says Horde / PIGLINS; the art sheets,
  the Oct 19 brief and design/building-revamp-progress.md already say Ashborn). "The AI faction" is now "the
  futuristic faction (later)", LANES.md's ring-gate-towers faction, per lovish's answer: Brassworks keeps steam now.
- **Sizes made buildable.** Minecraft hitboxes are square (the same width on x and z), so "1x2.5" and "1.5x2.5"
  footprints cannot exist. Rule now: T1 hitbox up to 2x2 and 3 tall, T2 up to 3x3 and 4.5 tall; the drawn body may
  be longer than the hitbox (up to 3 long at T1, 4 at T2). Every size in section 3 was rewritten to that rule, and
  the lab doc's gate (5 wide x 8 tall) passes all of them with room.
- **No vanilla-mob or Reign of Nether look-alikes** (LANES.md "originals only"). The Hoglin, the zoglin boar, the
  strider and the tinted Iron Golem were all vanilla bodies. Each keeps its stats and (where it exists) its code base
  for animation, but gets an original drawn body: the Hoglin becomes the **Cinder Boar**, the Carrion Hauler's boar
  is a bone-plated grave boar, the Warcrawler is an original basalt lava-crawler, the Rooted Treant gets bark, crown
  and root layers so no golem shows.
- **Simplified mechanics that cost engine work for little play:** the Soulfire Wheel's roll-up speed and wide
  turning (one speed; spear lines and towers are its counter), the Gale Kite's land-when-still rule (it is a plain
  low flyer), the Seedcaster's real thicket blocks (now a slow-and-cover zone, no block changes, no pathing rebuilds),
  the Sanctified Ram's pusher statue (one more figure to sculpt; the ram now rides on rings of light, which is the
  Sunforged theme).
- **Cut a contradiction:** the Carrion Hauler carried 3 riders in its coffins AND spawned 3 free Zombies from the
  same coffins on death. It is now a plain armoured transport (3 riders); its wreck feeds Raise Dead like any wreck.
- **Filled gaps:** the Bombard Crew (an existing Tidewrought T1 siege vehicle that moves to the Fitting Shed) was
  missing from this doc; the Steam Tractor is fixed at T2 (every other transport is T2); the Flak Cart is listed as
  moving to the Engine Shed (it is a vehicle in every other table; lovish can veto in the lab doc).
- Answered questions (AI faction, where vehicles are built, infantry vs buildings) were removed from the open list.

## 0. What lovish decided (his words)

- "they dont necessarily have to be vechs like m1 abrams or modern day tank" - "maybe a tank will make sense for the
  ai faction". Each faction's vehicle slot fits its theme. Only the futuristic faction (later) gets real machines
  such as tanks; Brassworks keeps 1800s steam machines (his Oct 28 answer).
- His examples: "a mythological light wielder riding a cloud"; "mobile ents that rip their roots into the ground and
  damage things, almost like in lurker format" (they root in place, then attack along the ground).
- "infantry should be able to destroy buildings" - answered Oct 28: real damage, slower than vehicles. This revises
  the Oct 22 raid-roles rule ("light infantry hunts workers; vehicles carry the building-damage bonus").
- Design doc first; lovish approves before any code.

## 1. Where the game stands today (read from main)

- Seven live factions: Sunforged, Gravebound, Ashborn (Horde / PIGLINS in code), Verdant, Tidewrought, Stormcallers,
  Brassworks. Hivebound is planned; a "futuristic faction (later)" with ring-gate towers is named in LANES.md and is
  the one lovish calls "the AI faction". It is not in the code yet.
- Labs: each faction has one T1 lab, one T2 lab and one T3 lab. The "Oct 16 lab split" (infantry / vehicle / air labs)
  appears in design/moving_buildings.md but is not built. lovish decided Oct 28: a separate vehicle lab per faction,
  T2 vehicles from an in-place T2 refit of it ([[vehicle-labs-design-2026-10-28]]).
- What the code calls a T1 vehicle today (`RaidTuning.isT1Vehicle`, PR #146): the melee beasts Hoglin (Ashborn),
  Sentinel Treant (Verdant), Mudskipper Sloop (Tidewrought), and the T1 siege Bombard Crew, Rocket Battery, Mortar
  Crawler. Each melee vehicle gets a `BUILDING_DAMAGE_BONUS` so it does 3.7 building HP/s; an infantry raider does
  1.5-1.8. Sunforged and Gravebound have no T1 vehicle at all (a gap PR #146 flagged).
- T2 beasts (one per faction, built): Oriflamme Elephant, Gorehorn Rhino, Ossuary Behemoth, Mossback Bison (carries
  4 infantry), Shellback Turtle (Moor: anchors in 2 s), Jade Qilin, Steam Hauler. These stay as they are; the
  vehicle slot sits beside them, not on top of them.
- Economy: a T1 extractor gives 2.0 metal/s, a T2 extractor 4.5. T1 combat units cost about 65-145 metal; the T1
  vehicles that exist are metal-heavy and energy-light (Hoglin 155m/110e, Sentinel Treant 170m/90e) while ranged
  infantry are energy-heavy (Pillager 80m/190e). T1 extractor 150 HP, windmill 110 HP, converter 50 HP.
- Rules this doc must obey (LANES.md): design from strategy and feel, not stat sheets; a weakness never ruins a unit
  (no ground vehicle slower than the army it marches with unless its job is to stay back); every unit has one
  oversized signature prop that reads at strategic zoom; originals only.

## 2. Rules for every faction's vehicle slot

### 2.1 What a "vehicle" is in this game

A vehicle is any big, non-humanoid war body: a creature, a construct, a ridden thing, a steam contraption
(Brassworks) or (futuristic faction only) a modern machine.
What makes it a vehicle is the job, not the look:

- **More health per metal than infantry, less agile.** Vehicles turn and accelerate slower, cannot garrison, and
  cannot climb 1-block steps faster than infantry. Infantry win in rough ground, forests, thickets and chokes.
- **Metal-heavy, energy-light.** Vehicles cost about 1.3-1.8x the metal of an infantry unit of the same tier and
  about 0.5-0.7x the energy. A player on a metal-rich map leans vehicles; a player with lots of wind leans infantry
  and spells. This keeps the BAR feel where the bot lab and the vehicle plant are two different economies.
- **Building breakers.** Vehicles keep the building-damage bonus (about 2x an infantry raider's building HP/s per
  unit). Damage against units is never raised by it.
- **Big enough to read, small enough to path.** Minecraft hitboxes are square, so the rule is in hitboxes: **T1
  hitbox at most 2x2 and 3 tall; T2 hitbox at most 3x3 and 4.5 tall.** The drawn body may be longer than the hitbox
  (a sled, a tractor and wagon): up to 3 blocks long at T1, 4 at T2, never wider than the hitbox. Every walkable gap
  in our buildings is at least 4 wide x 8 tall and the vehicle lab's gate is 5 x 8, so all fit with a block to spare.
- **Original bodies only** (LANES.md). No vehicle may read as a vanilla mob or a Reign of Nether unit: a vanilla
  entity may be the animation base, but what the player sees is our own boxes and textures.

### 2.2 Movement types (shared across factions, so players learn them once)

| Type | What it means | Hit by |
|---|---|---|
| Ground | normal pathing; wades at half speed like infantry | everything |
| Hover | floats a half block over ground and water; full speed on water and tidepools; cannot cross walls | everything; melee normally |
| Low flying | flies 2-4 blocks up; crosses water, cliffs and units, NOT walls or gates (open: questions file Q9) | ranged ground units and AA; not melee (except jumpers and spears with reach 3+) |
| Rooted / deployed | a mode switch: the unit stops, plants itself, and gets a stronger attack or armour; cannot move until it uproots | everything; usually more armour while rooted |

Mode switches are always telegraphed (a visible animation, a ground ring in team colour) and take 1-3 s each way.
The Shellback Turtle's Moor (2 s each way) is the reference: same feel, same HUD button, same hotkey.

### 2.3 Role mix

The full BAR vehicle-plant menu is: raider, assault (front-line heavy), siege (out-ranges towers), anti-air,
transport, and utility (radar, repair, area denial). No faction gets all of them. What a faction is missing is part
of its identity and is covered by its infantry, beasts or air.

Tier in brackets: T1 trains at the vehicle lab, T2 after its T2 refit.

| Faction | Raider | Assault | Siege | Anti-air | Transport | Signature mode |
|---|---|---|---|---|---|---|
| Sunforged | Dawnrider (T1, cloud) | Sanctified Ram (T1) | Sunlens Engine (T2) | - (Windcaller) | Cloud Barge (T2) | Sunlens deploys |
| Gravebound | Soulfire Wheel (T1) | Carrion Hauler (T1) | Skullflinger (T2) | - (Bone Dragon late) | Carrion Hauler (T1) | Skullflinger deploys |
| Ashborn | Cinder Boar (T1, the Hoglin reskinned) | War Sled (T1) | Magma Pot Wagon (T1) | - (Blaze) | Warcrawler (T2) | Warcrawler drum |
| Verdant | Bramble Roller (T1) | Rooted Treant (T1, rework) | Seedcaster (T2) | - (Wisp Choir) | - (Mossback Bison) | Take Root |
| Tidewrought | Mudskipper Sloop (T1, exists) | Coral Crab (T1) | Bombard Crew (T1, exists), Mortar Galley (T2, exists) | - (Flak Swivel) | Manta Ferry (T2) | Crab burrows in water |
| Stormcallers | Gale Kite (T1) | Temple Lion (T2) | Rocket Battery (T1, exists) | Gale Kite (dual) | - | Lion turns to stone |
| Brassworks | Boiler Trike (T1) | Steam Juggernaut (T2, exists) | Mortar Crawler (T1, exists) | Flak Cart (T1, exists) | Steam Tractor (T2) | Trike vents Heat |
| Futuristic (later) | Hover Skiff | Line Tank | Rail Lancer | Flak Tank | Lifter Drone | Rail Lancer deploys |

"(exists)" units keep their stats and move into the vehicle slot; the doc only adds a look note where useful. The
Flak Cart moves with the rest (it is a vehicle in every table here; the lab doc notes lovish may keep it in the
Machine Shop instead). Sunforged and Gravebound have no T1 vehicle today, so their lab ships with its first one.

### 2.4 Infantry vs buildings (the revised raid rule)

lovish's answer: infantry deal real damage to buildings, slower than vehicles. Today (PR #146 numbers) 6 infantry
raiders kill a T1 extractor in about 9-11 s and 4 melee T1 vehicles in about 6-7 s, so infantry already do real
damage. The change is mostly the rule's wording and two fixes:

- New rule: **infantry break buildings at about half a vehicle's rate per unit; vehicles still break them about 1.5x
  faster per metal.** Infantry still hunt workers best (they are faster and cheaper to lose).
- Starting targets (lovish's answer: today's PR #146 numbers stay): one infantry raider 1.5-2.0 building HP/s; one
  T1 vehicle 3.7; 6 raiders vs T1 extractor 10-12 s; 4 T1 vehicles 6-7 s; 6 raiders vs a tower 27-31 s (towers stay
  tough). The capitol doc uses the same band (its table scales these to capitol HP).
- Fix the outlier: the Ashborn Marauder (about half the rate of other raiders, 20 s on a T1 extractor) comes up to
  the infantry band.
- Line and ranged infantry use the same building rate as raiders per DPS; no unit does zero building damage.

## 3. Factions

Sizes are given against one infantry soldier: 1 block wide, 2 blocks tall. Costs are starting points (est.) for
`tools/balance_audit.py`, not tuned numbers. "Block model" means the body is built from boxes (cuboids) like our
render layers (the Leviathan fortress trick in design/big_units.md), on a vanilla or Alex's Mobs base where one fits.

---

### 3.1 Sunforged (gilded knights, sun magic)

Theme: **mythological light wielders and blessed constructs.** Gold, white stone, halos, light beams. Their vehicles
are carried by light rather than wheels. They fill the gap PR #146 found: Sunforged have no fast raider and no T1
vehicle.

**Dawnrider** - T1 raider (lovish's "light wielder riding a cloud")
- Silhouette: a white-gold rider with a big halo ring and a long sun-lance, standing on a flat, puffy cloud.
  Cloud = 5-7 overlapping white wool-like boxes, 2x2 blocks, glowing gold underside. The halo is the oversized prop.
- Size: 2x2 hitbox, about 3 blocks tall (cloud 1 block + rider 2). 1.5x infantry height.
- Movement: low flying (2 blocks up), fast (about 0.34, raider speed). Crosses water and gaps; stopped by walls.
- Weapon feel: a short sunbeam lance (range 4): a 0.5 s charge glow, then a bright hit. Sets buildings "sun-scorched"
  (the building-damage bonus). Leaves a faint gold trail so the player sees where it raided.
- Economy: about 150m / 100e (est.). Fast enough to snipe outer extractors and windmills.
- Counters: Dawnriders raze undefended eco fast and ignore water and slopes. Countered by ranged infantry and towers
  (light body, about 60 HP), anti-air units, and walls (cannot cross them).
- Infantry: Dawnriders cannot be hit by plain melee, so melee raiders cannot chase them; every faction's skirmishers
  can. Sunforged Pillagers escort them as the cheap ranged screen.

**Sanctified Ram** - T1 assault and building breaker
- Silhouette: a covered battering ram with a gilded, pointed roof (a small chapel on runners), a big **sun-disc ram
  head** (the prop) and no crew: it floats a half block up on two rings of gold light where wheels would be. Carried
  by light, like the Dawnrider's cloud. (Revised: the earlier pusher statue was one more sculpted figure for no play
  value, and crude faces are an ART-FAULTS line.)
- Size: 2x2 hitbox, body 3 long, 2.5 tall. Wider and longer than infantry, about infantry height.
- Movement: ground pathing (it hovers a half block but does not cross water or walls), line-army speed (0.25), so it
  marches with Vindicators.
- Weapon feel: slow, heavy ram swing every 2.5 s; a bell rings on each hit. Big bonus on buildings and walls (it
  counts as heavy siege for walls). Weak against units (low DPS).
- Economy: about 180m / 80e (est.).
- Counters: cracks towers and gates behind an infantry screen. Countered by anything that hits it while its screen
  is busy: skirmishers, the Hoglin, the Soulfire Wheel. Its roof gives 30% ranged resist, not melee.
- Infantry: needs them. Alone it dies; with a Vindicator line in front it ends towers.

**Sunlens Engine** - T2 siege, deploy mode
- Silhouette: a tall wheeled frame holding a huge round gold mirror (3 blocks across), a crystal lens in the middle.
  The mirror is the prop; it tilts toward its target.
- Size: 3x3 hitbox, 4 tall. Twice infantry height.
- Movement: ground (0.22). Deploy 2.5 s (mirror unfolds, legs plant), pack 2 s. Cannot fire while moving.
- Weapon feel: a 1.5 s gathering glow on the target, then a steady beam for 3 s that burns a short line (range 26,
  out-ranging towers at 16). It is a "hold the beam on it" weapon, not a shell.
- Economy: about 340m / 260e (est.). Energy-heavier than other vehicles because it is a sun weapon.
- Counters: towers, static defence, slow T2 tanks. Countered by fast raiders and flyers that reach it while deployed
  (2.5 s to pack), and by breaking line of sight (the beam needs a clear line).
- Infantry: a Halberdier screen protects it; enemy infantry that slip past win easily (it has a 5-block dead zone).

**Cloud Barge** - T2 transport
- Silhouette: a big long cloud (4 long, 3 wide) with a white-and-gold balustrade on top and a banner pole.
- Size: 3x3 hitbox, body 4 long, 3 tall.
- Movement: low flying, medium speed (0.28). Loads 4 infantry (same Embark / Eject buttons as the Mossback Bison).
- Weapon feel: none. Riders inside take 50% less ranged damage; if it dies they drop to the ground hurt (like the
  Bison).
- Economy: about 260m / 180e (est.).
- Counters: moves 4 infantry across water, cliffs and gaps an army has to walk around (it does not cross walls,
  questions file Q9). Countered by AA and focused ranged fire before it unloads.

---

### 3.2 Gravebound (undead, soul-fire, the pale haunted world-tree)

Theme: **bone constructs, haunted carts and things that roll.** Pale bone, grey wood, green-blue soul-fire. Their
vehicles feed on wrecks like the rest of the faction (Raise Dead, reclaim x2).

**Soulfire Wheel** - T1 raider
- Silhouette: a 2.5-block-tall wheel of ribs and spine with soul-fire burning at the hub and a skull at the axle.
  Nothing rides it; it rolls itself. The wheel is the whole silhouette and reads at any zoom.
- Size: 2x2 hitbox (hitboxes are square; the drawn disc is 1 wide and 2.5 across), 2.5 tall. About infantry height,
  much thinner from the front.
- Movement: ground, fast (0.34, raider speed). (Revised: the roll-up speed and wide-turning rules were cut; the
  path grid cannot show a wide turn and the feel comes from the body, not a second speed value.)
- Weapon feel: it hits by rolling into things: a crunch and a soul-fire splash on contact, then it rolls through and
  circles back. Bonus on buildings; leaves a short soul-fire scorch.
- Economy: about 120m / 80e (est.).
- Counters: workers and eco lines in the open. Countered by spear infantry (Halberdier, Reef Guard, Jade Guard) who
  stop it dead, by walls, and by towers (light body, about 70 HP).
- Infantry: zombies follow behind to finish what the wheel breaks open.

**Carrion Hauler** - T1 assault and transport
- Silhouette: a big **bone-plated grave boar** (our own body: a boxy skull-faced boar with rib armour and soul-fire
  eyes; PR #146's zoglin idea is the animation base only, nothing of it shows) pulling a bone sled with three upright
  coffins. The coffins are the prop.
- Size: 2x2 hitbox, body 3 long (boar plus sled), 2.5 tall.
- Movement: ground, line speed (0.25). Carries 3 infantry inside the coffins (Embark / Eject like the Mossback Bison);
  riders take 50% less ranged damage and spill out hurt if it dies.
- Weapon feel: the boar gores (slow, heavy; bonus vs buildings). (Revised: the earlier "three free Zombies climb out
  when it dies" was cut: the coffins cannot hold riders and Zombies at once, and its wreck already feeds Raise Dead.)
- Economy: about 160m / 90e (est.).
- Counters: pushes into infantry lines and drops riders on top of them. Countered by burst before it reaches you,
  and by holy/blunt damage.
- Infantry: it is half infantry. It carries skeletons safely across open ground.

**Skullflinger** - T2 siege, deploy mode
- Silhouette: a catapult made of a giant ribcage and a spine arm, with a basket of glowing skulls.
- Size: 3x3 hitbox, 3.5 tall.
- Movement: ground (0.22). Deploy 2 s (ribs dig in), pack 2 s.
- Weapon feel: a long creak as the spine arm pulls back, then a lobbed burning skull (range 24); the skull bursts
  into soul-fire on landing. Each wreck within 8 blocks gives it one empowered skull (bigger splash), so it loves a
  fight that has already happened.
- Economy: about 300m / 200e (est.).
- Counters: towers, packed eco, clumped infantry. Countered by raiders and flyers while deployed, and by reclaiming
  wrecks before it can use them.
- Infantry: needs a screen; its minimum range (6) leaves it open to anything that walks up.

---

### 3.3 Ashborn (Horde in code: brutes, war paint, Momentum)

Theme: **war beasts and crude war-rigs** in the Ashborn kit (basalt, tuff, obsidian, bronze, magma cracks, fire
bowls; nothing of Reign of Nether's piglins). Their vehicles charge in straight lines (Momentum: +50% damage on
straight charges).

**Cinder Boar** - T1 raider (the existing Hoglin, reskinned)
- Keep its stats and code. New body (revised: the Hoglin is a vanilla mob and LANES.md forbids look-alikes): a
  black-ash boar with ember-cracked hide, bronze tusk plates, a bronze war-saddle with a team-colour stripe. The
  tusk plates are the prop.

**War Sled** - T1 assault and building breaker
- Silhouette: a heavy bronze-plated sled on basalt runners with a spiked log ram on the front and two Brute crew
  (the existing Brute model, posed pushing) on the back. The log ram is the prop. (Revised: one crew option, not
  "striders or Brutes"; striders are vanilla mobs.)
- Size: 2x2 hitbox, body 3 long, 2 tall.
- Movement: ground, line speed (0.25), Momentum-boosted on straight charges.
- Weapon feel: a charge, a crunch, a shower of sparks. Biggest bonus vs buildings in T1 when it charges straight.
- Economy: about 170m / 70e (est.).
- Counters: walls, gates, towers at the end of a straight run. Countered by kiting ranged units, side attacks, and
  terrain that breaks the straight line (turns lose Momentum).
- Infantry: Brutes walk beside it; Headhunters shoot over it.

**Magma Pot Wagon** - T1 siege (Ashborn has no T1 artillery today)
- Silhouette: a two-wheeled cart with a big black cauldron of glowing magma and a crude throwing arm.
- Size: 2x2 hitbox, 2.5 tall.
- Movement: ground (0.23). No deploy; it throws from where it stops (0.5 s settle).
- Weapon feel: a lobbed blob of magma (range 20) that leaves a 3 s burning patch. Lower burst than other T1
  artillery, more area denial.
- Economy: about 130m / 150e (est.).
- Counters: clumps, choke points, eco. Countered by raiders, and by anything faster than its blob.
- Infantry: burning patches block both sides; Ashborn infantry must not walk through their own fire.

**Warcrawler** - T2 transport and support
- Silhouette: an original **lava-crawler**: a long six-legged beast of basalt plates with magma glowing in the joints,
  carrying a war-drum tower with banners and a 4-seat platform on its back. The drum tower is the prop. (Revised:
  the earlier "giant strider" was a vanilla mob at 2.5x scale.)
- Size: 3x3 hitbox, body 4 long, 4.5 tall (the T2 ceiling; the gate is 8 tall).
- Movement: ground; walks across lava and water at full speed. 0.27.
- Weapon feel: Headhunters on the platform shoot from it (garrison fire). Its drum gives nearby Ashborn units +20%
  Momentum range while it beats (an aura, toggle).
- Economy: about 320m / 220e (est.).
- Counters: crosses lava and rivers where nobody expects. Countered by focus fire (it is the aura carrier) and AA
  does nothing to it (ground).
- Infantry: carries them and buffs them; it is the Ashborn's infantry booster.

---

### 3.4 Verdant Court (living forest)

Theme: **walking trees, rolling briars and seeds.** Bark, moss, leaves, glowing sap. lovish's rooted ent lives here.

**Rooted Treant** - T1 assault, Take Root mode (a rework of the existing Sentinel Treant)
- Silhouette: the Sentinel Treant's code and animation base kept, but the body is ours: bark-plate boxes over the
  whole golem, a mossy antler crown (the prop; it echoes the Grove's stag-antler boughs) and long root-feet, so no
  Iron Golem shows (LANES.md: no vanilla look-alikes). When it roots, the feet split into thick roots that dig into
  the ground (block-model root boxes grow down and out).
- Size: 2x2 hitbox, 2.7 tall. A bit taller and much wider than infantry.
- Movement: ground (0.22, same as today). **Take Root: 1.5 s to root, 1 s to uproot** (open; see the questions file).
  While rooted it cannot move, takes 30% less damage and cannot be knocked back.
- Weapon feel, walking: the slow branch slam it has today.
- Weapon feel, rooted (the "lurker" attack): every 2 s, a line of root spikes bursts out of the ground from the Treant
  toward its target, up to 8 blocks long, hitting everything on the line (reuses the existing `SetFangsLine` fangs
  line, reskinned as bark spikes). A ground rumble and a dirt trail telegraph the line 0.3 s before it hits.
  Strong against buildings and lines of infantry; it can hit units it cannot reach.
- Economy: unchanged (about 170m / 90e). The mode is free.
- Counters: holds a choke or an expansion against infantry; roots near an enemy extractor and breaks it from 8 blocks.
  Countered by ranged units out of its 8-block line, artillery (it cannot dodge while rooted), fire (Verdant burns),
  and flyers.
- Infantry: Thornbows stand behind it while it roots. Enemy infantry learn to spread out (the line punishes a column).

**Bramble Roller** - T1 raider
- Silhouette: a 2-block ball of tangled thorn vines with leaves and a few red berries, rolling.
- Size: 2x2 hitbox, 2 tall. About infantry height, twice as wide.
- Movement: ground, fast (0.34). Rolls through thickets and Seedcaster bramble zones without slowing.
- Weapon feel: thorny contact hits; it leaves a short trail of slowing briars (3 s) behind it.
- Economy: about 120m / 80e (est.).
- Counters: workers and eco; escaping chasers (the briar trail). Countered by fire, ranged units, and towers.
- Infantry: Leafblades and Bramble Rollers raid together: the Roller breaks the building, the Leafblades kill the
  fleeing workers.

**Seedcaster** - T2 siege and area denial, Take Root mode
- Silhouette: a short, wide tree with a huge seed pod sack hanging from one branch like a sling.
- Size: 3x3 hitbox, 3.5 tall.
- Movement: ground (0.2). Roots in 2 s, uproots 1.5 s. Fires only while rooted.
- Weapon feel: lobs a seed pod (range 24) that bursts into a **bramble zone** 5 wide for 12 s: a drawn tangle of
  thorns and leaves (an area effect like the Treant's root line, no real blocks). Enemy units inside move at 40%
  speed and take small thorn damage; Verdant units inside take 30% less ranged damage. (Revised: the earlier version
  placed real 3x3 thicket blocks for 12 s; real block changes rebuild pathing, resend fog and can trap or leave
  stray blocks, the moving_buildings.md rule. The zone gives the same play - it stalls a push and covers an ambush -
  without a block change.)
- Economy: about 280m / 220e (est.).
- Counters: stalls a push at a gate or choke, breaks a Rhino charge lane, covers a Verdant ambush. Countered by fire
  (burns the zone out early), raiders that reach it while rooted, and walking round the zone.
- Infantry: shapes where enemy infantry want to walk; Verdant infantry fight from inside its brambles.

(Verdant transport stays the Mossback Bison.)

---

### 3.5 Tidewrought (amphibious, coral and tridents, tidepools)

Theme: **sea creatures and coral constructs that carry the sea with them.** Coral, kelp, teal, wet sand. Their
vehicles are faster and regenerate on water and tidepools, like the rest of the faction.

**Mudskipper Sloop** - T1 raider (exists)
- Keep its stats. Look note: a coral-crusted hull on fins with a small teal sail so it reads as a vehicle.

**Coral Crab** - T1 assault, burrow mode
- Silhouette: a wide giant crab of coral blocks (pink, orange, teal) with one oversized crusher claw.
- Size: 2x2 hitbox, 1.8 tall (wide and low; lower than infantry).
- Movement: ground and wading, sideways scuttle (0.26, full speed in water). **Burrow: 1 s in water or a tidepool,
  1 s out.** Burrowed: hidden from enemies more than 6 blocks away, heals, cannot attack.
- Weapon feel: a big claw crush every 1.8 s with a crack of shell; bonus vs buildings. Coming out of a burrow, its
  first hit deals +40% (the Tidewrought Ambush feel).
- Economy: about 150m / 90e (est.).
- Counters: ambushes at rivers and tidepools; holds shorelines. Countered by fighting away from water, scouting
  (Seer, spotters) and artillery on the pool.
- Infantry: Cutlass Raiders and Crabs both ambush from water; Harpooners pin targets for it.

**Bombard Crew** - T1 siege (exists, at the Slipway today)
- Keep its stats. It moves to the Fitting Shed with the Sloop. Look note: a coral-crusted bombard on a driftwood
  carriage so it reads as the faction's vehicle.

**Mortar Galley** - T2 siege (exists, at the Drydock today)
- Keep. It is the faction's T2 siege vehicle and moves to the Fitting Shed with the T2 refit.

**Manta Ferry** - T2 transport
- Silhouette: a big flat ray (manta) of teal and white boxes with a small coral howdah on its back.
- Size: 3x3 hitbox, 1.5 tall plus howdah (2.5).
- Movement: low flying over land (1.5 blocks up), full speed and slightly faster over water (0.3). Carries 4 infantry.
- Weapon feel: none; can drop a tidepool where it unloads (one per 30 s) so riders land on their home ground.
- Economy: about 260m / 170e (est.).
- Counters: moves an army across water and drops it on a tidepool. Countered by AA and focus fire before unloading.

---

### 3.6 Stormcallers (monks, lightning, wind, Mana)

Theme: **paper, lacquer and bronze constructs moved by wind and spirit.** Red lacquer, jade, gold, paper. Energy is
mana; their vehicles draw on it.

**Gale Kite** - T1 raider and light anti-air
- Silhouette: a big red-and-gold paper kite (a 3x2 diamond) with a monk standing on its crossbar; long tail ribbons.
  Different from the Sunforged cloud: it is flat, angular and must keep moving.
- Size: 2x2 hitbox (the drawn kite is 3 across the crossbar), 3 tall. Wide and thin.
- Movement: low flying (3 blocks up), fast (0.35). (Revised: the land-when-still rule was cut; it was a third
  movement state for one unit. The Kite is a plain low flyer like the Dawnrider; its weakness is its light body.)
- Weapon feel: lightning darts flicked down at ground targets (range 10), and it can also hit enemy flyers (light AA).
  Bonus vs buildings small; it is a worker and flyer hunter.
- Economy: about 130m / 140e (est.). Energy-heavier than other raiders (Mana faction).
- Counters: workers, scouts, enemy low flyers and air scouts. Countered by AA, ranged infantry, and towers (about
  55 HP, the lightest vehicle).
- Infantry: Blade Dancers handle buildings; the Kite handles the workers and the air.

**Temple Lion** - T2 assault, stone mode
- Silhouette: a bronze-and-jade guardian lion statue (a shishi) on a short plinth, with a carved mane and a bell
  collar. The mane is the prop.
- Size: 3x3 hitbox, body 3 long, 3 tall.
- Movement: ground, army speed (0.26). **Turn to Stone: 2 s on, 1.5 s off.** In stone mode it cannot move, takes 60%
  less damage, and roars every 4 s: a shockwave cone (6 blocks) that knocks back and staggers.
- Weapon feel: walking, a heavy paw swipe. Stone, the roar: a deep bell sound and a ring of dust.
- Economy: about 300m / 260e (est.); stone mode drains 2 energy/s (Mana).
- Counters: holds a choke or guards Rocket Batteries; pushes back melee waves. Countered by artillery and long-range
  units (stone does not reach them), and by draining the player's energy (stone mode stops at empty mana).
- Infantry: a wall for Fire Adepts to cast behind.

**Rocket Battery** - T1 siege (exists)
- Keep. Look note: a lacquered rocket rack on a two-wheeled handcart, so it reads as the faction's vehicle.

(Stormcallers have no vehicle transport; the Jade Qilin is their beast.)

---

### 3.7 Brassworks (steam machines, Heat)

Theme: **steam-era contraptions** - boilers, pistons, rivets, smokestacks. They are machines, but 1800s ones, not
modern tanks; that is how they stay apart from the futuristic faction. Most of their slot already exists.

**Boiler Trike** - T1 raider
- Silhouette: one huge front wheel (2 blocks), a brass boiler behind it with a tall smokestack, a small driver
  seat, two small back wheels. The smokestack is the prop.
- Size: 2x2 hitbox, body 2.5 long, 2.5 tall.
- Movement: ground, fast (0.34). **Vent: spends its Heat for 3 s of +40% speed** (smoke burst), then must cool 8 s.
- Weapon feel: a front-mounted steam piston punch; scalding steam puff on buildings (bonus vs buildings).
- Economy: about 130m / 70e (est.).
- Counters: workers and eco; escapes after the vent. Countered by Sappers, towers, and catching it while it cools.
- Infantry: Steam Runners and Trikes raid together.

**Steam Tractor** - T2 transport (revised: fixed at T2 like every other 4-seat transport; it comes with the refit)
- Silhouette: a squat traction engine (big back wheels, boiler, smokestack) pulling an open wagon with benches.
- Size: 3x3 hitbox, body 4 long (tractor plus wagon), 2.5 tall.
- Movement: ground (0.27). Carries 4 infantry.
- Weapon feel: none; Rifle Squads can fire from the wagon (garrison fire).
- Economy: about 240m / 120e (est.).
- Counters: fast, protected infantry moves. Countered by focus fire; riders spill out if it dies.

**Mortar Crawler** (T1 siege) and **Flak Cart** (T1 anti-air) exist and move to the Engine Shed; **Steam Juggernaut**
(T2 assault) exists and moves there with the T2 refit. Keep their stats.

---

### 3.8 The futuristic faction (later; lovish's "AI faction"; real machines)

lovish: "maybe a tank will make sense for the ai faction". This is LANES.md's ring-gate-towers faction and the only
one with modern-looking machines: white and dark-grey armour panels, cyan light-lines, clean right angles. Sketched
here only so the slot is reserved and no other faction drifts into tanks; it is designed properly when the faction is.

**Hover Skiff** - T1 raider: a flat wedge on two glowing hover pads with a small pulse gun. 2x2 hitbox, 1.5 tall.
Hover (crosses water). Fast (0.36). Pulse shots, bonus vs buildings. About 120m / 70e. Countered by towers and ranged
infantry; light armour.

**Line Tank** - T1 assault: a compact tracked tank, boxy hull, one turret with a single barrel; tracks are box strips.
2x2 hitbox, body 2.5 long, 2 tall. Ground (0.25). Turret turns independently of the hull, so it fires while moving.
Shell hit every 1.5 s. About 180m / 80e. Countered by artillery, swarms of melee that get under the barrel, and
spear units.

**Rail Lancer** - T2 siege, deploy mode: a long-barrel rail gun on a tracked base; when deployed, two side legs drop
and the barrel extends. 3x3, 3 tall. Deploy 2.5 s, pack 2 s. A charging hum, then one straight piercing shot (range
28) through every unit on the line. About 340m / 260e. Countered by flanking raiders and flyers while deployed.

**Flak Tank** - T2 anti-air: the Line Tank hull with twin quad-barrel flak. Same size. Ground-attack weak.

**Lifter Drone** - T2 transport: a four-rotor drone that lifts one vehicle or 4 infantry. Low flying.

## 4. How each fits the economy

- **Metal per unit**: T1 vehicles 120-180 metal (one T1 extractor pays for one every 60-90 s); T2 vehicles 260-340
  metal (one T2 extractor pays for one every 60-75 s). Energy 70-150 for T1, 170-260 for T2.
- **Energy where the theme is magic**: Sunlens Engine, Gale Kite and Temple Lion cost more energy (the sun and
  Mana factions). Ashborn, Brassworks and Gravebound vehicles are the most metal-heavy.
- **Build power**: vehicles take about 1.3x the build time of infantry of the same metal (BAR's plant is slower),
  so a vehicle opening is a commitment, not a free swap.
- **Wrecks**: every vehicle leaves a wreck worth half its metal (all are over the 30-metal line). Gravebound's
  Skullflinger turns nearby wrecks into ammo; the Carrion Hauler's wreck raises its own Zombies.
- **No upkeep**, except the Temple Lion's stone mode (2 energy/s) because Stormcallers spells already drain energy
  live; nothing else runs a meter.
- **Raiding maths**: a 4-vehicle raid (about 600 metal) that kills two T1 extractors (300 metal plus 4 metal/s of
  income) and gets away pays back in about 1-2 minutes. A tower (about the price of 2 vehicles) at an expansion must
  still win against 4 T1 vehicles alone; the current `defence_anti_raid_tower_out_trades_its_cost_vs_raiders` test
  gets a vehicle row.

## 5. Counters in one table

| Vehicle type | Beats | Loses to |
|---|---|---|
| Raider (Dawnrider, Wheel, Roller, Kite, Trike, Skiff, Cinder Boar, Sloop) | workers, bare eco, slow siege | towers, ranged infantry, walls, spear lines |
| Assault (Ram, Sled, Hauler, Treant, Crab, Lion, Line Tank) | buildings, gates, melee lines | kiting ranged units, artillery, being caught without a screen |
| Siege with deploy (Sunlens, Skullflinger, Seedcaster, Rail Lancer) | towers, static lines, clumps | raiders and flyers while deployed, minimum-range rush |
| Siege without deploy (Magma Pot Wagon, Bombard Crew, Rocket Battery, Mortar Crawler, Mortar Galley) | clumps, eco, towers at range | raiders, flyers, anything that closes the gap |
| Transport (Barge, Hauler, Warcrawler, Manta, Tractor, Lifter) | crossing water, cliffs and gaps | AA and focus fire before unloading |
| Low flyers (Dawnrider, Barge, Kite, Manta, Lifter) | melee, water and cliffs | AA, any ranged unit, walls |

## 6. How infantry relate to vehicles (all factions)

- Infantry are the screen; vehicles are the hammer. A vehicle without infantry should lose to cheaper infantry that
  kite it; infantry without vehicles break buildings at half speed.
- Spear and halberd infantry stop charges and rolling raiders (Halberdier, Reef Guard, Jade Guard).
- Ranged infantry are every faction's answer to low flyers; nobody needs AA just to stop a Dawnrider.
- Transports carry infantry, never vehicles (except the futuristic Lifter Drone, one vehicle).
- Infantry still hunt workers best; the bot's infantry raid and vehicle raid (PR #146) keep their separate targets.

## 7. Building them as block models

- Each body is a set of boxes: a base box, 3-8 detail boxes, one oversized signature prop (halo, mirror, smokestack,
  claw, mane, drum tower, crown). Textures are 16x16-per-face painted like our other units.
- Where a vanilla or Alex's Mobs body fits as an **animation base**, use it and cover it completely with block-model
  render layers (as big_units.md's Leviathan does), so the vanilla mob never shows: Rooted Treant (golem base under
  bark plates and an antler crown), Cinder Boar and the Carrion Hauler's grave boar (hoglin / zoglin base under ash
  hide or bone plates), Warcrawler (strider base for the lava walk, under a six-legged basalt body), Coral Crab
  (turtle base under a crab shell), Temple Lion (scaled base plus layers). The ART-FAULTS "Reign of Nether look"
  line applies to vanilla mobs too.
- New all-box bodies: Dawnrider, Cloud Barge, Soulfire Wheel, Sanctified Ram, War Sled, Magma Pot Wagon, Bramble
  Roller, Seedcaster, Sunlens Engine, Skullflinger, Gale Kite, Manta Ferry, Boiler Trike, Steam Tractor, and the
  futuristic set. Wheels and rolling bodies spin as one part (no per-spoke animation).
- Hitboxes are square (2x2 or 3x3); long bodies (sled, tractor, barge, crawler) are drawn past the hitbox along the
  facing axis, never sideways, so a vehicle never clips a gate it can path through.
- Mode switches are one animation each (root, deploy, burrow, stone, land): a 1-3 s pose blend, plus a team-coloured
  ground ring. Every concept passes the eyes close-up and the ART-FAULTS list before lovish sees it.

## 8. Recommended first prototype: the Rooted Treant (Verdant)

Why this one first:
1. **It is lovish's own example** (the ent that roots and attacks along the ground like a lurker), so the first thing
   he plays is the idea he asked for.
2. **Cheapest to build.** The Sentinel Treant already exists (body, stats, building bonus, bot use, raid tests), its
   "takes root" toggle was already planned and deferred in verdant_court_plan.md, and the ground-line attack can reuse
   the existing `SetFangsLine` fangs line reskinned as root spikes. No new movement type, no new pathing.
3. **It builds the shared mode-switch** (stop, telegraph, plant, stronger attack, uproot) that five other concepts
   reuse: Sunlens Engine, Skullflinger, Seedcaster, Coral Crab burrow, Temple Lion stone, Rail Lancer. Getting the
   feel and the HUD button right once pays off seven times.
4. **It tests the revised raid rule directly**: a rooted Treant breaking an extractor from 8 blocks versus 6 infantry
   gives a clean vehicle-vs-infantry building-damage comparison in the existing `RaidGameTests`.

What done looks like: a Take Root button and hotkey on the Sentinel Treant; 1.5 s root / 1 s uproot with a ground
ring; while rooted, root-spike lines every 2 s up to 8 blocks, 30% less damage, no knockback; GameTests written first
(roots and cannot move, line hits every unit on it, building damage within the vehicle band, uproot time); eyes
stills of both poses. It trains at the Verdant Treant Nursery once that lab exists (lab doc, built in the same
order), at the Grove until then. The Dawnrider (Sunforged) is the recommended second prototype: it fills Sunforged's
missing T1 raider and vehicle, but it needs the new low-flying movement type, which is more engine work.

## 9. Questions and answers

Open questions from all three docs are merged in [[open-questions-2026-10-28]] (low flyers vs walls, the Rooted
Treant's root time and rooted damage cut, the Flak Cart, and the rest).

lovish's answers so far (Oct 28, in Command Center Vertos):
- AI faction: Brassworks keeps steam machines now; a futuristic faction gets real tanks later.
- Where vehicles are built: a separate vehicle lab per faction (BAR's vehicle plant, the Oct 16 split).
- Infantry vs buildings: half as fast as vehicles per unit (today's PR #146 numbers stay).

## Related
- [[20261028-0250-bat-infantry-buildings-vehicle-shapes]] (lovish's words and answers)
- [[vehicle-labs-design-2026-10-28]] (where these vehicles are built) and [[capitols-extractors-design-2026-10-28]]
- [[Game Design - raid roles - infantry hunts workers, vehicles break buildings]] (the Oct 22 rule this revises)
- [[Game Design - gate tier 2 with economy, not switches]]
