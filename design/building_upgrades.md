# Building upgrades, annexes and reclaim

lovish (Oct 11): *"Make buildings able to be upgraded and show variable statistics of HP, cost, and what it does.
Upgrading tier 1 buildings to help produce units faster, to replace the con turret in Beyond All Reason ... Building
the same building over and over takes up a lot of space and makes the game feel cluttered. I want buildings and
spacing to have reason, and to be able to reclaim buildings that aren't necessary to use resources elsewhere.
Upgraded buildings can change in shape or fundamental design ... and make upgraded buildings stand out to the
opponent."* Asked how an upgrade should work, he answered **both**: levels in place **and** add-on modules.

Code: `building/BuildingLevels.java` (numbers + level logic), `building/BuildingStats.java` (what the HUD shows),
`building/BuildingReclaim.java` (decommission), `building/buildings/shared/LabAnnex.java` (the modules),
`research/researchItems/UpgradeLabLevel.java` (the upgrade order), structures from `tools/gen_lab_levels.py`.

## The problem, through BAR and chess

In BAR a player who wants more production has three answers: another lab (a full factory: a second queue, a second
target, a lot of ground), **nano turrets** (cheap assist build power, spammed in rows around the lab) or a
constructor guarding the lab. Late-game bases become carpets of labs and nanos. It works, but it reads as clutter,
and every one of those pieces is the same decision made again.

The chess lens gives the trade we want players to feel:

- **Concentration vs. spread.** One strong lab is a juicy target (a queen on an open file): if it dies, all of that
  investment dies with it, and every unit it makes walks out of one door. Three labs spread over the base are
  three targets, three queues and three rally points, but they eat ground and builder time. Neither answer may be
  strictly better.
- **Tempo.** An upgrade in place spends *production time* (the lab stops making units while it rebuilds itself) to
  buy production speed. It is a developing move: strong when you are safe, a mistake under pressure.
- **Exchanges.** Selling a piece should be possible but never free, or the board stops meaning anything.

## Options considered

1. **More labs only (BAR as-is).** Rejected: exactly the clutter lovish wants gone.
2. **Nano-turret units ("units that act as buildings").** A turret entity with build power, like BAR. Rejected as the
   primary answer: it IS the con turret, and units next to labs are hard to read from the RTS camera. Its good part
   (assist build power near the lab, assisting construction) survives in the annex.
3. **Levels only.** Clean, but a lab is then a single number and every upgraded lab looks the same.
4. **Modules only (SC2 add-ons).** Readable and destroyable, but nothing changes the lab itself.
5. **Levels + annexes (chosen).** Levels are the big, slow, in-place investment that changes the structure's shape;
   annexes are the cheap, flexible, separately-destroyable add-on that replaces the nano turret.

## Levels (every T1 army lab)

Sunforged Barracks, Gravebound Graveyard, Horde Military Portal, Verdant Grove, Tidewrought Slipway, Stormcallers
Lotus Academy, Brassworks Machine Shop. `Building.maxLevel = 3` marks a levelled building.

| Level | Production build power | HP (x base) | Upgrade cost (metal / energy) | Upgrade time at the current speed |
|---|---|---|---|---|
| 1 | 100% | x1.0 | - | - |
| 2 | 175% | x1.5 | 90 / 360 | 40 s at L1 speed (40 s) |
| 3 | 250% | x2.0 | 180 / 720 | 60 s at L2 speed (~34 s) |

- **The upgrade is a production order** in the lab's own queue (hotkey `'`), flow-paid like everything else in the
  flow economy. While it runs the lab **makes nothing** (decided: pause, not slow - the queue is serial, the cost is
  honest tempo, and a lab caught mid-upgrade is a visible, punishable moment). Cancelling refunds what was spent.
- **Cost reasoning.** A new T1 lab is cheap in resources (45 m / 225 e) but expensive in builder time (hundreds of
  blocks: minutes of a worker) and ground. L2 costs about 1.3 labs of resources for +75% and costs no builder time
  or ground; L3 costs about 2.6 labs for another +75%. Per point of production, L2 is about as efficient as a second
  lab and L3 a little less: concentration is bought with a premium, paid in a single-target risk. A full L3 lab
  (250%) has 2x HP but one queue and one door, against 2.5 labs' worth of spread.
- **Structures.** Each level is its own structure (`<lab>_l2`, `<lab>_l3`, both `assets/` and `data/`): the same
  footprint (an in-place upgrade must never collide with neighbours), taller, with faction flourishes - L2 grows a
  crown of corner pillars and battlements, L3 adds a central spire with a glowing top. The current level is read
  from the structure name the placement wears (`BuildingPlacement.currentStructureName`), so saves (`upgradeLevel`)
  and late joiners (the place-building packet) restore it through the existing upgrade path, with no new packet.
  The Graveyard's L2/L3 are drawn on the Overflowing Graveyard, so a levelled Graveyard is also overflowing.
- **Readable to the opponent.** The shape change is visible in the world, and the strategic-zoom plate of a levelled
  building carries gold level pips under its role glyph (everyone who has explored it sees them). The hover card
  shows the level to anyone hovering it.

## Annexes (the in-faction nano turret)

One annex type per faction for now. A small 3x3 structure a worker builds **within 4 blocks of an own built army
lab**; it attaches to the nearest such lab. At most **2 annexes per lab** count.

| | |
|---|---|
| Cost | 60 metal / 300 energy, built by workers (30-block build time) |
| HP | 80 (its own building: targeted, damaged and destroyed separately - kill the arms, keep the lab) |
| Effect | +40% of the lab's base production build power each (L3 + 2 annexes = 330%) |
| Assist | 1.0 build power to the nearest own construction site within 12 blocks (BAR nano: helps build the base) |

Names: Sunforged **Masons' Annex**, Gravebound **Bone Loom**, Horde **Forge Pit**, Verdant **Rootweaver Arbor**,
Tidewrought **Rigging Crane**, Stormcallers **Prayer Wheel**, Brassworks **Assembly Arm**.

An annex is cheaper per point of production than a level (60 m for +40%) but fragile and spread out: the cheap,
flexible move. A second specialisation (an Armoury: units leave with bonus HP / veterancy) is designed but deferred.

## Stats display

`BuildingStats` is the one data provider: level / max level, HP, production speed (levels + annexes), income,
next-level cost and effect, annex count, and "Reclaim: +N metal". It feeds:
- the hover card (any building you hover, yours or the enemy's): the level in the title and one stats line;
- the portrait title of a selected building (level suffix);
- the tooltips of the Upgrade order and the Decommission button (full detail: current and next level).

## Reclaiming your own buildings (Decommission)

A built building you own (not a capitol, not your last building) has a **Decommission** button. It is
irreversible (a second click within 3 s confirms) - otherwise "decommission, cancel, repair for free, decommission"
would print metal.

- **Refund: 60% of the metal invested** (its cost plus every completed level; annexes are reclaimed on their own).
  Energy is not returned (BAR returns metal only). Why 60%: a building killed by the enemy leaves nothing (units
  leave 50% wrecks); selling must beat losing it, or players let doomed buildings die instead of re-investing, but
  BAR's 100% would make relocation free and erase the cost of a bad placement. 60% makes "this lab is in the wrong
  place" a real but survivable decision, and the lost energy is the tempo tax.
- **Over time.** Work needed = max(15 s, refund / 2.5 metal/s) at 1 build power. The structure dismantles itself at
  0.5 build power and every own worker ordered onto it adds its own (right-click the building with workers = a
  build/repair order, which becomes dismantling on a decommissioning building; an area reclaim circle also picks up
  decommissioning buildings). Metal is paid as the work advances; if the enemy kills it first, the rest is lost.
- **Disassembly** removes blocks from the top down (no explosions, no wreck) and finishes by quietly removing the
  building. Ownership is checked server-side (`PacketAuth.mayActFor`) on the `DECOMMISSION` building action.

## Bots

Once a bot has 2 army labs it stops adding labs while one of them can still level up, and instead queues the next
level on its lowest-level lab (one upgrade at a time, not while starved of metal). A lab at L2+ gets an annex when
the bot floats (one at L2, two at L3).

## Deferred

- Levels for T2/T3 labs (the code is generic: set `maxLevel = 3` and generate `_l2` / `_l3` structures).
- Economy levels beyond the existing extractor T1 -> T2 refit.
- A second annex type per faction (Armoury), and annex-specific art per level.
- Syncing a building's decommission progress to other clients (the owner sees it through the shrinking structure).
