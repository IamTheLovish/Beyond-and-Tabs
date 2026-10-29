# Static defences - walls, gates, traps, anti-raid towers, anti-T3 emplacements

lovish's vision list: "towers you can build to cheaply prevent raids, and raiding units that necessitate a reaction";
"you should be able to build walls and traps"; "buildings specialised to defend against large T3 units, expensive but
slightly more economical than T3 mobile units - a T3 defence ~10-20% of the most expensive T3 unit's cost, limited by
range and size".

BAR reference: dragon's teeth / fortification walls (cheap, block pathing, reclaimable), LLT / HLLT (cheap anti-raid),
T2 heavy defences, anti-T3. Rule kept throughout: **one shape and one price per job across all seven factions** (BAR
readability - a player reads "that is a wall / a tower" at a glance), each faction only changes the look.

Code: `building/buildings/defence/` (Defences is the registry of all 42 buildings), `bot/BotDefences.java`,
structures `tools/defence_structures.py` (run by `tools/gen_structures.py`), icons `tools/defence_icons.py` (run by
`tools/gen_hud_icons.py`), tests `gametest/DefenceGameTests.java`.

## The set (every faction)

| Job | Footprint | Cost (m / e) | HP | Tier gate | Mechanic |
|---|---|---|---|---|---|
| Wall segment | 2x3x2 | 15 / 0 | 400 | capitol | blocks pathing; Shift+drag lays a contiguous line; ordinary units walk round, siege / heavies (x3) and experimentals (x6) break it; reclaimable |
| Gate | 2x4x2 | 40 / 20 | 320 | capitol | fence gates: open for own side + allies, shut while an enemy is within 6 blocks |
| Trap (2 of 3 kinds) | 3x1x3 flat | 30-50 / 40-60 | 60 | capitol | hidden from the enemy until it fires; spikes / snare rearm by repair, mine is one shot |
| Anti-raid tower (T1) | 3x5x3 | 110 / 150 | 260 | capitol | 7 dmg/s at 14 range, x1.5 vs light (<=70 HP); ground only |
| Anti-T3 emplacement | 5x6x5 | 15% of the priciest T3 (275 / 260 today) | 700 | T3 lab + Tier 3 | 25 dmg / 3 s at 16 range, x3 vs experimentals (targets them first); hits flyers |
| Enhanced anti-raid tower | 3x6x3 | 260 / 400 | 600 | a T1 lab | 18 dmg/s at 16 range, x1.5 vs light; ground only |
| Sky Watch (T1 anti-air) | 3x6x3 | 120 / 160 | 280 | capitol | 15 dmg/s at 20 range; **air only** |
| Enhanced Sky Watch | 3x7x3 | 260 / 380 | 550 | a T1 lab | 35 dmg/s at 22 range; air only |
| Fortified wall | 2x3x2 | 40 / 20 | 1100 | a T1 lab | a wall with 2.75x the health, same footprint (mixes into a Shift+drag line) |
| Enhanced trap (each kind) | 3x1x3 | 2x its trap | 120 | a T1 lab | radius x1.4, damage / root / slow x1.5, one spare charge: the first spring keeps every block and rearms itself after 5 s |
| Flak (T2 anti-air) | 4x6x4 | 280 / 350 | 650 | Tier 2 + T2 builder | 12 dmg / 0.75 s at 24 range, the burst hits every flyer within 3 blocks of the target, x1.5 vs experimentals; air only |
| Anti-siege tower (BAR's HLT) | 3x7x3 | 450 / 650 | 1000 | a T1 lab | 45 dmg / 2.5 s at 20 range (past the T1 siege lobber's 15, short of the long artillery's 22-24), x2 vs siege units and shoots them first; ground only; build time x1.2 |

**Oct 21 towers** (lovish 02:47: "flying units ran through everything which makes sense since they're fast... anti air
defenses should be added"; 02:51: "tier 1 constructors can make enhanced version of tier 1 defenses - kinda like in
bar"; 02:53: "we can adjust numbers to fit balance, keep them flexible"). Before them nothing below T3 shot a flyer, so
fast flyers skipped the army and sniped his commander. An enhanced tower is ~2.2x its base tower's price for ~2.3x the
weapon (BAR's LLT -> HLLT): a better rate, more eggs in one basket. Every number is in `DefenceTower.Kind` and
`DefenceCosts` (traps: `Trap.ENHANCED_*`) - one table to tune. Combat engineers may raise the capitol-tier defences
(walls, gates, traps, raid tower, Sky Watch) in the field; everything behind a lab or a tier (the enhanced walls, traps
and towers, the flak, the anti-T3) is a base commitment. The bot raises a Sky Watch between home and the first enemy
flyer it sees within 48 blocks (up to 3). Structures and icons are placeholders in the faction palettes until Lane B
draws them. Tests: `gametest/AntiAirGameTests.java`.

Trap kinds (shared mechanics): **spikes** 30 dmg + slow in r 1.8; **snare** roots (Slowness X) 4 s in r 2.2, 4 dmg;
**mine** 70 dmg in r 3, then gone. A trap fires when an enemy ground unit steps within 1.5 blocks of the plate; flyers
never set one off; the owner's and allies' units walk over freely.

## Per faction

| Faction | Wall | Gate | Traps | Anti-raid tower | Anti-T3 |
|---|---|---|---|---|---|
| Sunforged (villagers) | Stone Rampart | Portcullis Gate | Caltrop Field (spikes), Snare Net (snare) | Crossbow Turret | Sunlance Ballista (Castle) |
| Gravebound (monsters) | Bone Palisade | Ossuary Gate | Grave Spikes, Bone Snare | Soulfire Spire | Grave Ballista (Stronghold) |
| Horde (piglins) | Skull Palisade | War Gate | Spike Pit, Fire Pot (mine) | Spear Tower | Gutbreaker Ballista (Fortress) |
| Verdant Court | Thorn Hedge (living azalea) | Living Arch | Root Snare, Bramble Patch | Thorn Sentinel | Heartspear Oak (Heart of the Wild) |
| Tidewrought | Coral Bulwark | Sea Gate | Powder Keg (mine), Net Trap | Swivel Gun Post | Harpoon Battery (Abyssal Shipyard) |
| Stormcallers | Jade Wall | Moon Gate | Fire Glyph (mine), Binding Seal | Spirit Lantern | Heaven's Crossbow (Heaven's Gate) |
| Brassworks | Brass Plate Wall | Steam Gate | Clockwork Mine, Bear Trap | Gatling Post | Breaker Cannon (Assembly Works + Tier 3 until a T3 lab ships) |

Existing defences stay as they are: the garrison Watchtower / Dark Watchtower / Bastion (T1, need units inside), and the
T2 self-firing Storm Oak, Lighthouse Battery and Rail Battery. Before this the Stormcallers had no defence at all and
only three factions had a self-firing one; now every faction has a T1 anti-raid tower that fights alone.

## How it works

- **Walls block pathing** because they are real blocks: two solid courses and a wall/fence course on top
  (WalkabilityBuilder.isFenceLike - never standable, never jumpable). Shift+drag places a 4-connected cell-by-cell
  line with no gap (BuildingLineClientEvents.wallLine), so a diagonal drag gives a staircase without corner gaps.
  Units never auto-target walls (MiscUtil.isBuildingAutoAttackable) unless they are siege / heavy / experimental
  (WallSegment.drawsAggroFrom), and those hit walls x3 / x6 (WallPlacement.destroyRandomBlocks).
- **Gates** are fence gates under a lintel. Open gates have no collision and the pathfinder walks through them, so the
  owner's paths go straight through; a GatePlacement checks the UnitGrid every 5 ticks and shuts the gates while any
  enemy is within 6 blocks (closed gates are fences to the pathfinder). The swing is a real block change: the
  walkability grid patches the chunk like any edit. BuildingBlock.isPlaced treats open/shut (and a pressed trap
  plate) as the same block, so swinging is never "damage".
- **Traps are hidden** by never sending the placement to enemy RTS players (FogOfWarServerEvents.canPlayerSeeBuilding
  asks Concealment.isBuildingHiddenFrom - fog on or off; spectators see everything): no outline, health bar, minimap
  mark or hover card. Hidden traps are not attackable (TrapPlacement.isAttackable). The world blocks are flat
  faction ground cover round a pressure plate - camouflage, not invisibility. Firing reveals the trap for good
  (BuildingServerEvents.replaceClientBuilding). A rearmable trap fires by knocking out its plate: it shows as damaged,
  and a worker repairing it puts the plate back - repairing is rearming.
- **Towers** are one weapon class (DefenceTower.Kind RAID / ANTI_T3): instant hit with a faction tracer particle, generic
  damage like the Rail Battery, one grid query per tower every 10 ticks while idle.
- **Anti-T3 price** is computed, not typed: DefenceCosts.bakeAntiT3 takes 15% of the priciest experimental in
  DefenceCosts.T3_UNITS after the config costs bake. A new experimental joins that list; the GameTest also checks every
  bot kit's T3.
- **Piglin ground rule** does not apply to defences (BuildingValidators): they go where the fight is.
- **Hotkeys**: none - every faction's build-menu keys are taken; the six buttons are appended to each build menu.

## Bots

`bot/BotDefences.think` is called once per think from BotPlayer (one line): an anti-raid tower beside every own extractor
more than 28 blocks from home without one within 12 (up to 6), and from minute 6, once, a 7-cell wall line with a gate
in the middle 22 blocks out toward the nearest enemy. `expansionWithoutTower`, `wallLine`, `placeAt` and the
`*For(faction)` lookups are public for the maps/bots work to reuse (a real choke finder can replace `wallLine`).

## Tests (gametest/DefenceGameTests.java)

- `defence_wall_blocks_pathing_and_the_gate_opens_only_for_its_side` - a wall line across a floating platform: shut gate
  = no way across (by the pathfinder's own cell rule), an enemy near keeps it shut, no enemy = open and walkable; open
  gate is not damage; plain units ignore walls, a Sun Colossus breaks them.
- `defence_trap_is_hidden_then_springs_on_an_enemy` - hidden from the enemy, not the owner; a friend doesn't trigger it;
  an enemy does (hurt, revealed, plate out); putting the plate back rearms; a mine is gone after one shot.
- `defence_anti_raid_tower_out_trades_its_cost_vs_raiders` - for all seven towers, raiders worth its metal die before
  they could raze it; it never shoots a flyer.
- `defence_anti_t3_costs_ten_to_twenty_percent_of_a_t3` - every emplacement 10-20% of the priciest T3 (metal and
  energy), 5x5+, range <= 20, dormant without Tier 3, x3 vs experimentals; every live faction has its set.

## Not done / next

- Traps re-hide after a save/load (revealed is not saved) - harmless, noted.
- No per-faction mechanical twist yet (e.g. the Verdant hedge regrowing, Brass gates venting steam); prices and stats
  are shared on purpose until playtests ask otherwise.
- Bots' wall line is a straight cut across the approach, not a real choke search.

### The Oct 21 towers per faction
| Faction | Enhanced anti-raid | Sky Watch | Enhanced Sky Watch | Flak | T1 lab (unlocks the enhanced) |
|---|---|---|---|---|---|
| Sunforged | Great Crossbow Turret | Skyward Ballista | Great Skyward Ballista | Sunburst Spire | Barracks |
| Gravebound | Great Soulfire Spire | Bone Harpoon Spire | Great Bone Harpoon Spire | Bat Roost | Graveyard |
| Horde | Great Spear Tower | Harpoon Totem | Great Harpoon Totem | Fire-Pot Mortar | Basic or Military Portal |
| Verdant Court | Elder Thorn Sentinel | Thornspitter Pine | Elder Thornspitter Pine | Seed-Pod Burster | Grove |
| Tidewrought | Twin Swivel Gun Post | Swivel Mast | Twin Swivel Mast | Deck Flak | Slipway |
| Stormcallers | Great Spirit Lantern | Kite Lantern | Great Kite Lantern | Thunder Kites | Lotus Academy |
| Brassworks | Heavy Gatling Post | Sky Gun Post | Twin Sky Gun Post | Steam Flak | Machine Shop |

### Enhanced walls and traps per faction
| Faction | Fortified wall | Enhanced trap A | Enhanced trap B |
|---|---|---|---|
| Sunforged | Fortified Rampart | Iron Caltrop Field (spikes) | Chain Snare Net (snare) |
| Gravebound | Bone Bastion Wall | Barrow Spikes (spikes) | Grasping Grave (snare) |
| Horde | Iron Skull Palisade | Deep Spike Pit (spikes) | Great Fire Pot (mine) |
| Verdant Court | Ironbark Hedge | Strangling Roots (snare) | Thornbramble Thicket (spikes) |
| Tidewrought | Reef Bulwark | Twin Powder Keg (mine) | Kraken Net (snare) |
| Stormcallers | Dragon Jade Wall | Inferno Glyph (mine) | Heavenly Seal (snare) |
| Brassworks | Riveted Bulkhead | Double Clockwork Mine (mine) | Steam Bear Trap (snare) |
Tests: `gametest/EnhancedDefenceGameTests.java`.

### The anti-siege tower per faction (Oct 29)
lovish, Oct 29 (design/siege_t1.md, "Siege lobber revision 2"): an HLT-like tower so a base has an answer to massed T1
siege lobbers - one lobber loses to it, six die before it falls, artillery (22-24) still outranges it. Kind
`DefenceTower.Kind.SIEGE_GUARD`, price `DefenceCosts.SIEGE_GUARD`, ids `siege_guard_<suffix>`, registered after every
FortSet (no id moves), gated by the same T1 labs as the enhanced towers. Names are placeholders: Warden's Arbalest
(Sunforged), Bonecleaver Spire (Gravebound), Skullsplitter Tower (Horde), Ironbark Sentinel (Verdant Court), Long Nine
Post (Tidewrought), Dragon-Eye Lantern (Stormcallers), Long Tom Post (Brassworks). Tests:
`gametest/AntiSiegeTowerGameTests.java` (numbers, reach past a range-15 attacker, double damage on siege units and
shooting them first, ground only; the lobber duel follows the lobber PR). Not yet: the bot raising one against lobbers
(needs the lobber).

Balance pass, lovish Oct 29: the first cut (45 every 1.5 s, 30 dps) made the enhanced anti-raid tower pointless and
closed the lobber's window too early. Now 45 every 2.5 s (18 dps, the Beamer's rate) and x2 (90) against siege units -
`DefenceTower.isSiege`: the bot's siege role (`BotRoles.isSiege`, which takes the lobber when it merges) or any
`FieldArtillery` - which it shoots before nearer units. One shot still kills an 80 hp lobber; HP, range, cost, gate and
ground-only are unchanged.
