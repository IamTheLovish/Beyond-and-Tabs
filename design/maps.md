# Maps: pre-made battlefields, the start-position phase, capture states

lovish (Oct 11): "Create pre-made maps with different biomes and intricate layouts and terrain, different mex spawn
points with varying metal increase. Create different neutral capture points specific to maps that create diverse
gameplay and different strategic advantages." Plus: configurable starting map (nether, plains, biomes, the End),
fair for every player, trees/caves as resources but never hiding the view, geothermal only on lava. Inspiration:
BAR's 8v8 island map with its start-position mode, and ZeroSpace plateau maps (ramps, chokes, neutral zones that
read "Neutralizing" / "Captured" on the map).

Code: `maps/` (MapPreset, MapLayout, MapBuilder, MapMatch, StartPhase, packets, client), hooked into
`MultiplayerLobby`, `SkirmishServerboundPacket`, `BattlefieldSetup`, `StartAreaClearing`, `MetalPatches`,
`CapturePointServerEvents`.

## 1. How a map is made

A map is **not** a saved structure. It is a deterministic layout spec in code (`MapLayout`), stamped into the
skirmish world around the lobby centre by `MapBuilder` when the host presses Start:

* **Slots.** `n` start slots (2-16) sit on a ring of radius `S` at angles `pi + 2*pi*i/n`, teammates adjacent
  (seats sorted by team, like BAR's team sides). `S` grows with `n` so neighbours stay >= ~116 blocks apart;
  the arena radius is `R = S + margin` (wall at `R`).
* **Wedge frame.** Every terrain query `(dx, dz)` is folded into the frame of its nearest slot: `u` = distance
  along that slot's axis (centre outwards), `v = |lateral offset|`. Terrain, forests and decor are functions of
  `(u, v)` only, so the map has **n-fold rotational symmetry and a mirror line through every slot**: every slot
  plays the same map, whatever the player count. Mexes, capture sites and vents are listed once in slot frame
  and rotated to every slot. This is the fairness guarantee (tested, see section 6).
* **Heightmap + surface.** Each preset returns a height (relative to the centre's ground) and a surface kind
  (land, sand, cliff, water, lava, void, bridge) per column, plus a forest flag. Steep columns become the
  preset's cliff block. The builder writes only blocks that differ, a column at a time, chunk by chunk from the
  centre outwards, inside a ~35 ms per-tick budget (players watch the map rise; a progress line ticks in chat).
  Every column is filled solid 4 blocks under its new surface, so no cave mouth opens under a base.
* **Trees only in reclaim lanes.** Forests grow only where the preset flags them (never within ~70 blocks of a
  start), as short vanilla trees, so they are reclaimable wood and never a canopy over a base or a main lane. The
  realm maps grow their own instead (section 4.7, 4.8): short crimson / warped fungi (stem 3-5, a 3x3 wart cap, on
  nylium so TreeClearing fells them as trees) at 3% of grove columns and dead-flowered chorus at 8% (its islets are
  small scenery), inside the build's per-tick budget; their shape comes from the folded slot frame, so every wedge
  grows the same wood (`MapLayout.groveDensity`). Ambient particles were left out: the RTS camera has no cheap
  ground focus to spawn them round, and the fog tint carries each realm's mood.
* **Guard rails** (railed maps, `MapPreset.railedVoid`: Chorus Isles). Every walkable column with void among its 8
  neighbours carries an end-stone-brick wall, its sides joined to the neighbouring rails (worked out from the sampled
  grid, no neighbour updates). The 8-neighbourhood makes the rail line 4-connected round any shoreline, so with the
  walls joined there is no diagonal gap to push a unit through. A rail is 1.5 high: no ground unit steps or is
  pathed over it; only air crosses the void.
* **Then the economy** is stamped: mexes with their value, the map's own capture sites in the map's style (plain
  ruin, nether-brick fortress ruin, End obsidian pillar), lava vents.

### Nether / End without a dimension switch

Nether Rift, Soulfire Deltas, End Spires and Chorus Isles are built **in the Overworld** with nether/end blocks plus
a client fog tint (`ViewportEvent.ComputeFogColor`, dark red / crimson ash / violet-black / pale violet). This is how
lovish's #16 "map choice: biomes, nether, the end" is met. A real dimension switch was rejected: the whole
match stack (fog of war, battlefield setup, capture sites, metal patches, the bots, the wall) reads
`server.overworld()`, the world is created before the host picks the map, and moving every player + their
camera across dimensions mid-lobby is the kind of change that breaks saves. Overworld stamping gets 95% of the
look for none of that risk. (Vanilla's sky colour cannot be overridden without a mixin; the fog tint carries it.)

## 2. Metal values

`MetalPatches` now stores a value per patch (saved with the world, synced to clients). An extractor's income is
`base * value` (T1 2.0/s, T2 4.5/s - 7.0 until Oct 24). Rich patches show it in the ground: **1.0x** raw iron plus; **1.5x** raw
iron plus with a raw-gold heart; **2.0x** a gold-block heart. The minimap draws 1.5x patches gold, 2.0x bright
gold. Typical layout per slot: a home ring at 1.0x (safe, slow), forward/contested patches at 1.5x (take ground
to earn more), and the centre cluster at 2.0x (one per slot, the prize everyone fights over - chess's control of
the centre).

**Metal over the open ground (Oct 16, lovish: "Metal extractors ... should be all over the map I feel like I have
alot of the map that is empty that doesnt need to be contested"):** after its template, every pre-made map runs
`MapLayout.spread` - up to 6 more patches per slot from a 12-block grid of candidates, farthest from every patch
first (weighted toward the ground between the bases), each 30+ from another patch, 46+ from a start, clear of sites,
vents and pillars, on flat dry ground with no void / lava / deep water within 7, and with a walkable path back to
its start. Values by distance from the centre: **0.75x lean** behind the bases, **1.0x** on the flanks, **1.5x** in
the inner half. Mean distance from open ground to the nearest patch: 54-95 -> 25-49 blocks (GameTest
premade_maps_spread_metal_over_the_open_ground). Every patch now also has **BAR's mex circle** on the ground
(MetalPatchMarkerClientEvents): pale gold when free, a double ring when rich, the owner's colour when taken and in
sight (grey in the fog), and the floating gem is bigger.

**Metal placement rules (Oct 21, maps spec M1; `MapLayout`, `MetalOptions`, GameTests in `MapMetalGameTests`):**
- *Reach by flood.* An open-ground patch is valid only on a column the 4-connected flood from its slot's start reaches
  (steps of one block, no void / lava / deep water / tree stem; `MapLayout.reachFrom`). This replaced the straight line
  back to the start, which refused every patch behind a cliff or ridge (Highlands at 1v1: 256 of 270 candidates).
- *Level ground under the whole pad.* Every patch, template or open-ground, needs its 7x7 pad (what
  `MetalPatches.stamp` levels) to be land within one block of the centre's height, checked at every slot's rounded copy.
  A template patch that fails moves up to 6 blocks toward its start, else it is dropped for every slot alike.
- *The lobby's metal richness applies to pre-made maps.* Lean 0.65 / normal 1.0 / rich 1.5 scale the open-ground fill
  (14 patches a slot at normal -> 9 / 21); templates are untouched. Random keeps the normal map. The lobby preview and
  the server build the same layout (`SkirmishServerboundPacket.premadeMetal`).
- *Metal reaches the wall (Oct 22, lovish: "edgeline means maybe enough blocks away that the extractor doesn't get
  blocked by the wall").* The fill may place a patch as close to the wall as `ExtractorClearance.blocks()` = **11
  blocks**, derived in code: the biggest extractor structure of any faction and tier (all fourteen `metal_extractor*`
  files, 5x5, read from the registry), the 1-block placement gap, the 7x7 pad, a worker lane, the wall's widest piece
  (the 5x5 gatehouse) and 1.5 blocks of rounding, as a square, times root 2. `MapRimGameTests` lays the gatehouse on
  every ring column of every map and an extractor ghost on every rim patch, and in a world places all 14 extractors on
  patches stamped exactly that far from a real ring wall.
- *Home metal is standard (Oct 22, lovish: "starting mexes around the base can be standardized").* `HomeMetal`: 6 patches
  a seat, 1.0 each, in a fan 18 blocks from the start (11 to 24 after a nudge), on every pre-made map and player count.
  A spot the ground refuses moves toward the start, then to the next angle or radius: count and yield never change.
  The extra-metal level and the lobby's richness never touch it.
- *Extra metal, spread over the whole map, with a bonus for the outskirts (lovish Oct 22: "we can add more mexes than we
  have to the game", "spread all over the map", "bonuses to players that reach to the outskirts", "he does want massive
  armies").* `MapLayout.spread` runs three passes over one candidate list: the core fill (the map as it was: <= 14 a slot,
  richness-scaled, not nearer the wall than 22), the rim fill (<= 4 a slot between the clearance and 22) and the spread
  (<= 8 a slot anywhere with room, 16 apart), each farthest-from-every-patch first. The core fill never loses a patch to
  the others. A patch yields `1 + level x (0.5 x distance-from-bases + 0.6 x rim)` (`MapLayout.openValue`): 1.0 at the
  bases, rising to ~1.5 mid-map and ~2.0 at the clearance line on a land map at the normal level. Maps whose open ground is
  cut by water or void (Archipelago, End Spires, Chorus Isles) have little room, so little extra: the flood rule stands.
- *Extra-metal level* `MetalOptions.Extra`: LOW (half the extra patches and premium), NORMAL (default), HIGH (twice the
  extra patches, 1.25x the premium); NONE is the old map (tests). Not in the lobby UI yet; tune it from the bot ladder.
- *Metal reaches the wall (Oct 22, lovish: "edgeline means maybe enough blocks away that the extractor doesn't get
  blocked by the wall").* The fill may place a patch as close to the wall as `ExtractorClearance.blocks()` = **11
  blocks**, derived in code: the biggest extractor structure of any faction and tier (all fourteen `metal_extractor*`
  files, 5x5, read from the registry), the 1-block placement gap, the 7x7 pad, a worker lane, the wall's widest piece
  (the 5x5 gatehouse) and 1.5 blocks of rounding, as a square, times root 2. `MapRimGameTests` lays the gatehouse on
  every ring column of every map and an extractor ghost on every rim patch, and in a world places all 14 extractors on
  patches stamped exactly that far from a real ring wall.
- *Other knobs on `MetalOptions`*: `rimInset` (default `AUTO_RIM` = the clearance; a larger number keeps the fill further
  in, a smaller one is lifted to the clearance) and `reserved` (areas the fill must leave alone - the hook for the hero
  jungle; nothing reserved today). The arena's edge is asked of one helper, `MapLayout.distanceToArenaEdge`, a circle
  today.

## 3. Map-specific capture sites

Existing kinds kept: **Ancient Mine** (+3 metal/s), **Ley Shrine / Ley Nexus** (+30 energy/s), **Mercenary
Camp** (a hired wolf every 60 s, max 3). New kinds, each with one clear strategic job:

| Kind | Effect while held | Why it changes play |
|---|---|---|
| **Sunken Bell** | owner sees 40 blocks around it through the fog | scouting post on islands; tempo for raids |
| **Watchtower** | owner sees 64 blocks around it | prophylaxis: see the push before it lands |
| **Ancient Forge** | research and upgrades (non-unit production) cost 25% less | a race to tech (T2 sooner) |
| **Lava Vent** | +45 energy/s (geothermal) | nether energy economy without wind |
| **Oasis** | heals the owner's units within 12 blocks, 2 hp/s | a forward repair point in open desert |
| **Forsaken Garrison** | musters the map's recruit for its holder (below) | free units at the front: tempo for whoever holds space |

Capture rules unchanged (20 s uncontested, enemy drains first). New: every site shows its state on the map -
a floating label over the site ("Watchtower - Neutral", "Capturing 45% - name", "Captured by name",
"Neutralizing 30%") and an owner-coloured marker on the minimap (claimant colour ring while being taken).

### Forsaken Garrison (realm-maps, lovish's vision #12)

#12 asks for "reasons to explore: resources across the map, hireable special units, abandoned structures that give
units over time while held - but stay BAR-like". The trickle kinds (Mine, Ley Nexus, Vent) already pay resources
across the map, and the Mercenary Camp hires generic wolves; what the maps lacked was a site whose *pieces* are the
prize, that rewards taking ground early and punishes ignoring it. Rules (`CapturePointServerEvents.muster`):

* **Held and uncontested:** one recruit every **90 s** (paced like production, `EconomyPace`), at most **2** of this
  garrison's recruits alive for the holder; at the cap the next one waits, ready, and walks out the moment a place
  frees. Recruits are ordinary units of the holder (they keep fighting if the site is lost).
* **An enemy on the site** - fighting the holder for it, or draining it alone - **holds the muster**.
* **Neutral, it banks** a recruit every 90 s, up to **2**, for whoever takes it next: the capture releases them at
  once. This is design-principles.md's zugzwang line, "neutral camps grow": an unclaimed garrison costs you more the
  longer you leave it to your neighbour.
* **The recruit is the map's own**, chosen by the site's style and kept on the marker: a **wither skeleton** from a
  Nether fortress ruin (Soulfire Deltas), an **enderman** (a neutral unit, made to serve any side) from an End
  obsidian pillar (Chorus Isles), a vindicator from a plain ruin. Two melee T1/T2-weight units are a forward squad,
  not an army: the site wins skirmishes, not games.
* Saved with the world: muster, bank and recruit type on the marker; each recruit carries a scoreboard tag and its
  garrison's UUID, so a reload re-counts it against the cap (and onJoin touches no other entity's ForgeData, R2-10).
* Readable: the label adds "2 waiting" while neutral and "next recruit 40%" while held (both synced with the site
  list); bots weigh it 1.1 before Tier 3, 0.8 after (`BotSites.worth`).

Chess reasoning. **Space**: every garrison sits on a boundary at least 60 blocks from both neighbouring starts
(tested), so holding it is a forward commitment that the enemy can punish. **Tempo**: free pieces are development
without spending build power or metal, and they appear where the fight is, not at your factory; the bank makes the
first capture a tempo swing worth racing for. **Counterplay**: any enemy unit on the site pauses it, 20 s alone
neutralizes it, the cap bounds the snowball, and on Soulfire Deltas the garrison stands in a soul-sand moat that
slows the holder's reinforcements as much as the raiders. **BAR feel**: BAR has no free units, but it has
Control Victory capture points and the Lazarus/Necro resurrection of wrecks into units - a garrison "raising its old
guard" is that idea tied to map control, with no new resource. References weighed: C&C Generals' Reinforcement Pad
(a tech building that gives a vehicle now and then) and Warcraft III's creep and mercenary camps (special units on
neutral ground) for the shape; AoE relics (a trickle that rewards holding) for the alternative we did not take
(a trickle would have been a fourth Mine). A paid "hire" menu (Warcraft's mercenary camp, RoN's own capturable
End Portal building) needs UI and is a proposal (realm-maps-progress.md).

## 4. The maps

Legend: `H` start slot (team box shaded on the minimap), `m` 1.0x mex, `M` 1.5x, `$` 2.0x, `~` water/lava,
`^` cliff/ridge, `/` ramp, `T` forest (reclaim lane), `[X]` capture site. Sketches show a 2-player cut; with more
players the same wedge repeats around the ring.

### 4.1 Archipelago (islands, fords; Tidewrought's home)
Plains islands in a shallow sea. Home island per slot, a centre island, stepping-stone islets between
neighbours. Shallow sand fords (1 deep, walkable) link home -> centre and home -> islets; everything else is
water for navies and amphibians. Beach rim at the wall.
```
 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 ~~~  m m       [Bell]       m m   ~~~
 ~~ m  H  m====( M M )====m  H  m  ~~
 ~~~  m m  ford         ford m m  ~~~
 ~~~~~~~~=====( $ [Tower] $ )=====~~~~
 ~~~~~~~~~~~~~~ centre island ~~~~~~~~
```
Mex per slot: 5 home 1.0x, 1 ford sandbank 1.0x, 2 islet 1.5x (one each side), 1 centre 2.0x.
Capture: **Sunken Bell** on every islet (vision over the sea lanes), **Watchtower** (lighthouse) on the centre.
Players: 2-16.

### 4.2 Highlands (ZeroSpace plateaus, ramps and chokes)
Each base on a raised plateau (+9) with two ramps: a front ramp down to the lowland and flank ramps to the passes.
Cliff ridges (+14) run along every boundary between neighbours, broken by one pass at base distance: the choke.
A forward hill (+6) on each lane holds 1.5x metal; the centre is a low basin with the Ancient Forge.
```
   ^^^^^^^^^^^^^^^^^ ridge ^^^^^^^^^^^^^^^^
   ^   m m m    [Tower] pass   m m m     ^
   ^  m (H) /---  / M M \  ---\ (H) m   ^
   ^   m m m  plateau  hill  plateau     ^
   ^^^^^^^^^^^^^ ( $ [Forge] $ ) ^^^^^^^^^
```
Mex per slot: 6 home 1.0x (on the plateau), 2 forward hill 1.5x, 1 lowland flank 1.0x, 1 centre 2.0x.
Capture: **Watchtower** in every pass (prophylaxis on the flank route), **Ancient Forge** in the basin.
Players: 2-16.

### 4.3 Nether Rift (netherrack, lava, basalt pillars)
A ring of lava (the rift) cuts the centre off; basalt bridges on every lane are the only crossings (chokes).
Thin basalt pillars dot the lanes (never near bases). Lava vents between neighbours are capturable geothermal
sites; each base also has two free 3x3 lava pools for a geothermal building.
```
  basalt |   m m                         m m   | basalt
         | m  H  m   vent[~]   vent[~] m  H  m |
         |   m m    M ~~~~bridge~~~~ M   m m   |
         |        ~~~~ ( $ [Mine] $ ) ~~~~     |
```
Mex per slot: 5 home 1.0x, 2 rift-edge 1.5x, 1 centre 2.0x. Capture: **Lava Vent** on every boundary,
**Ancient Mine** (debris) in the centre. Fog: dark red. Players: 2-16.

### 4.4 End Spires (end stone islands, void, bridges)
Floating end-stone islands over a dark void. Home island, a spire island on each lane, the centre island.
Purpur bridges (5 wide) join home -> spire -> centre and home -> boundary island (to your neighbour).
Obsidian spires stand on the lane islands' rims as landmarks.
```
   void  void   [Spire]   void   void
     (H)====( M M )====( $ [Nexus] $ )
   void  boundary isle  void  void
```
Mex per slot: 5 home 1.0x, 2 spire island 1.5x, 1 centre 2.0x. Capture: **Watchtower** (obsidian spire) on the
boundary islands, **Ley Nexus** (shrine) in the centre. Fog: violet-black. Players: 2-16.

### 4.5 Verdant Valley (rolling forest, river)
Gentle hills, a ring-shaped river around the centre with shallow fords on every lane and every boundary.
Forest bands run along the boundaries between neighbours: reclaim lanes for wood, with Mercenary Camps in
clearings. Bases sit on open meadows.
```
   TTTTT  [Camp]  TTTTT          TTTTT
   T  m m m            ~~~~~ford~~~~~  m m m
   T m (H)  m  M ~~ ( $ [Nexus] $ ) ~~ M  (H) m
   T  m m m       ford ~~~~~~~~~~      m m m
```
Mex per slot: 6 home 1.0x, 2 inner-bank 1.5x, 1 centre 2.0x. Capture: **Mercenary Camp** in the boundary
clearings, **Ley Nexus** in the centre. Players: 2-16.

### 4.6 Desert Mesa (open dunes, mesas)
Open rolling dunes (fast, nowhere to hide) broken by terracotta mesas (+16) with one ramp each; mesa tops hold
1.5x metal and are high ground. Oases between neighbours heal units; the centre is an Ancient Mine.
```
   dunes   [Oasis]    dunes
   ##mesa##/       \##mesa##
   m m (H) m   ( $ [Mine] $ )   m (H) m m
```
Mex per slot: 5 home 1.0x, 2 mesa-top 1.5x, 1 centre 2.0x. Capture: **Oasis** on every boundary, **Ancient
Mine** in the centre. Players: 2-16.

### 4.7 Soulfire Deltas (the Nether as a battlefield; realm-maps)
Low netherrack wastes drifted with crimson nylium, every base on a flat nylium terrace with a polished-blackstone
causeway to a paved plaza. Along **every boundary** runs a **soul-sand valley** (floor 2-3 below the wastes, banks
of one-block steps): the short way to a neighbour at vanilla's 40% speed. In each valley sits a **lava lake**, its
surface two below the valley floor (more below the wastes), so a Geothermal Plant fits only on a **vent** - two on
every lake's shore, on the boundary line itself, plus two private pools behind every base (the geothermal spots,
BAR-style: you take a spot, you do not line a lake with plants). Further out each valley holds the **Forsaken
Garrison**: a nether-brick fortress
ruin on a blackstone islet in a soul-sand moat, raising wither skeletons. Crimson fungus groves line the valley
banks and warped stands dot the warped-nylium backfield (reclaim). Basalt columns mark the garrisons and the lakes.
2-player cut (the valley runs between the two players, through the plaza):
```
                      |  soul-sand valley  |
    T  T  (warped)    |  [Garrison] + moat |    (warped)  T  T
                M     |      vent          |     M
                      |  ( lava lake )     |
   m  m               |      vent          |               m  m
 m (H) ===causeway====| ($ [Forge] $) plaza|====causeway=== (H) m
   m  m               |      vent          |               m  m
                      |  ( lava lake )     |
                M     |      vent          |     M
    T  T              |  [Garrison] + moat |              T  T
```
**The valley is the terrain choice with a counter.** Crossing it is the shortest route to a neighbour (and the only
way onto the garrison), but at 40% speed an army is exposed to the banks above it (high ground, artillery) and
arrives late; the fast route is the causeways through the plaza - longer and a single choke. Air ignores it. Taking
the garrison means holding a slow-to-reinforce position: the moat slows the holder's reinforcements as much as the
raiders'. (Vanilla pathfinding does not weigh soul sand: units take the short way unless given waypoints - see
Proposals in realm-maps-progress.md.) Piglins build on every block here, as Tidewrought do on Archipelago's water.
Mex per slot: 5 home 1.0x, 2 rich 1.5x just past the lakes, 2 backfield 1.0x among the warped groves, 1 centre
2.0x (12.0). Capture: **Forsaken Garrison** in every valley (at least 60 blocks from both starts), the **Ancient
Forge** in the plaza (a bastion forge: blackstone and anvil in a nether-brick ruin). Fog: crimson ash. Players: 2-16.

### 4.8 Chorus Isles (the End as a battlefield; realm-maps)
End-stone islands over a void 14 deep. Each base has its island; a **land neck** (15 wide) runs to a lane island with
the rich pair and on to the centre island, whose raised dais carries a **Watchtower** spire. Railed purpur **bridges**
(9 wide, ~6 walkable between the rails) join every home island to the **garrison islets** on both its boundaries
(the islet keeps 80 blocks from both starts: at the perpendicular's foot when the wedge is wide, moved in between the
two lanes in a dense ring). Small chorus islets out by the wall are scenery. Every edge of walkable ground is
**guard-railed** (section 1): no void eats a unit - a ground unit cannot be pathed or pushed over a 1.5-high wall,
and the void floor is 14 down so nothing ever falls out of the world. Documented residue: an explosion that lifts a
unit over a rail would leave it stranded below (Proposals: a void tether).
2-player cut:
```
                 void        [Garrison islet]        void
                          //                  \\
 ( H island )==neck==( lane: M M )==neck==( centre: $ $ [Spire] )==neck==( lane )==neck==( H island )
                          \\                  //
                 void        [Garrison islet]        void
          * chorus islets by the wall (air-only scenery)
```
**Air units get real value**: the void is impassable to ground but open to air, so every ground route is a neck or
a bridge (a choke you can defend) while flyers cross straight over - raids on island economies, shortcuts between
islands, and support for the garrisons that ground armies must reach single-file. The watchtower's 64-block sight
from the centre is the counter: see the air coming (prophylaxis). Mex per slot: 5 home 1.0x, 2 lane-island 1.5x,
1 centre 2.0x, plus one 1.0x per garrison islet that has room (up to 4 players, and 16) - 12.0 or 10.0 per slot,
equal for every slot at a given count. Capture: **Forsaken Garrison** (obsidian pillar, endermen) on every
boundary, **Watchtower** (obsidian spire) in the centre. Fog: pale violet. Players: 2-16.

### Starting on ground (all pre-made maps)
`MapLayout.startGround`: the start-position phase refuses a start whose capitol square (sampled 3x3 at +-7) touches
void, lava or water deeper than a ford (StartPhase.inBox; bots' picks use the same test). Before this a click in a
start box could found a base in End Spires' void or Archipelago's sea.

### Random terrain
The old behaviour: the Battlefield world preset's own terrain with the procedural ring of patches and the three
generic capture kinds. Lobby option `Map: Random terrain`.

## 5. Start-position phase (BAR's "start position mode")

After Start (and after a pre-made map has finished rising), a phase with a **120 s** AFK clock (45 s before Ready):

* Each team's **start box** is an annular sector round its slots (teams' boxes separated by a gap), drawn on the
  minimap (team-coloured outline + hatching) and in the world (a coloured outline on the ground).
* Each human **left-clicks the ground** (or right-clicks the minimap) inside their team's box to place their
  commander; the spot snaps clear of metal patches and capture sites. They may move it until the phase ends.
  Their name floats over the spot (world + minimap) for everyone; teammates already have Alt-drag drawings
  (arrows/lines) and label pings to plan.
* **Simultaneous**, not turn order: 16 players taking turns would cost minutes, and BAR's own turn order exists
  only to stop teammates stacking - here every slot owns its own mexes, so stacking only hurts the staller.
  Simultaneous is quicker and nobody waits; the "await their turn" feel is kept by the countdown banner.
* **Bots** auto-place at their slot instantly. When the clock runs out, anyone unplaced is placed at their slot
  (equally fair) and the match starts, with a chat line naming who was not ready.
* **Ready** (tester Joisco, Oct 26; BAR's own ready check): placing starts nothing. Each human places, then presses
  **Ready** in the panel on the right (refused until placed); the panel lists every side as placing / placed /
  READY / bot / left with "Ready n/m" on top, so everyone sees who the match waits for. A ready player un-readies
  to move. When every human still in the phase is placed and ready (bots always are), the phase ends 3 s later; an
  un-ready in those 3 s stops it. A player who logs out drops out of the count and everyone is told (back in,
  unready, on a rejoin). The host (singleplayer owner / op on a dedicated server) has **Force start**: the first
  click warns, naming who is not ready, the second starts; non-hosts are refused. Ready is not saved: a reloaded
  phase asks every human again. Tests: gametest/SpawnReadyGameTests.java (batch spawn_ready).
* Then the match starts exactly as before (capitol, commander, workers, alliances), the battlefield forms
  (base clearing, wall at the map's radius in the map's material) - a pre-made map skips the procedural patch
  and capture stamping because the map already placed its own.
* Lobby option **Start positions: Choose / Auto** (host). Auto skips the phase (slot positions). The phase also
  skips itself for a bots-only match. Singleplayer quick start on Random terrain with Auto is the old instant path.
* **Saved with the world** (rewrite 5, code_review CR-13; `match.MatchState`): a save-and-quit or a server restart
  while the map rises or starts are placed **resumes** on reload. The map build runs again from the saved setup (it
  only writes blocks that differ; an economy already stamped is not stamped twice); the phase comes back with every
  pick and its clock, held until a player is back and then at least 20 s, and ends early only once every human has
  returned and pressed Ready again. Rejoining players get the RTS view and their camera on their pick. A setup that can't resume is cancelled
  cleanly. Details: design/ron_rewrite_plan.md "Rewrite 5", design/persistence.md.

## 6. Tests (gametest/SkirmishGameTests.java, end of file)

* `premade_maps_are_fair_for_every_slot`: for every pre-made map and n = 2, 3, 4, 6, 8, 16: every slot's mex
  value (patches in its wedge) is equal within 5%, every slot is the same distance from the centre (within 2),
  and the terrain height at every slot is equal.
* `map_capture_sites_spawn_with_their_effect`: every map's site kinds are the ones it advertises; a captured
  Lava Vent pays energy, a captured Ancient Forge discounts research, an Oasis heals.
* `start_phase_places_everyone_in_their_box`: a click outside the box is refused, a click inside is kept,
  bots are placed at once, and the timeout places the rest inside their boxes and fires the start; placing alone
  does not end the phase, placing and Ready does.

Realm maps and the garrison (gametest/RealmMapGameTests.java; MapLayout was also checked offline with a harness that
renders every map to PNG):
* `realm_maps_are_fair_for_every_player_count`: n = 2, 3, 4, 6, 8, 16 - equal metal per slot and per team, equal
  start distances (centre and next start), heights, boxes, start ground; every start's nearest garrison equally far
  and at least 60 away; on Soulfire Deltas two private vents per start and equal distance to the contested ones.
* `realm_maps_connect_every_start_by_ground`: a ground flood (rails, lava, void, groves, pillars, vents and site posts
  blocked, steps of one) reaches every start, patch, site and vent; patches keep 5 from void/lava/rail, sites 4,
  vents 3; every lava-lake bank stands two above the lava.
* `realm_maps_raise_in_their_arena`: MapBuilder raises 16x16 windows of both maps inside the test's footprint (a lake
  shore with its vent, a garrison islet, a grove; a bridge, an islet, a chorus islet): every column shows its
  palette block, rails stand exactly where railAt says, groves grow, a Geothermal Plant is accepted on a vent and
  refused over a lake's edge.
* `garrison_musters_for_its_holder_and_stops`: banks while neutral (to the cap), releases the bank on capture as
  the style's recruit, musters to the cap and no further, refills a freed place, pauses while contested, gives the
  old holder nothing once neutralized; a recruit saved and loaded counts once.
* `realm_maps_and_the_garrison_have_their_names`: every map has its lang name and blurb, every capture kind its
  long and short name.

## 7. Generated maps (MapPreset.GENERATED, maps/gen; lovish Oct 21 22:28)

> "you should be able to choose / add a way to automatically generate maps that are intricate and sometimes changes
> rng with different buffs and bonuses. The random map can give one side more neutral objectives with the other side
> giving more mexes."

A new natural battlefield from every seed, built on lovish's Oct 21 map decisions: **balanced asymmetry** (the sides
differ on purpose and are evened out by a measured per-seat score within a tolerance, default 5%), **square maps as
well as round**, room kept for the **hero jungle** (MetalOptions.reserved), more metal along the edges and in empty
ground, bots never cheat (the generator only shapes the map), 4v4 the design standard. Phase 1 (offline prototype and
experiments, 200 seeds per cell) chose the defaults below; every number, what did not work and the open questions
are in `design/generated-maps-report.md` (PR #131).

**Determinism.** `MapLayout.build(GENERATED, teams, seed)` calls `MapGenerator.generate`; the same seats and seed draw
the same map on every JVM (SplitMix64 streams per stage, StrictMath trig, `sqrt` not `hypot`), so the lobby preview is
the map built, and a save resumes the same map. The lobby options ride the seed's top byte (`GenSeed`: shape any /
round / square, modifiers on / off / surprise); the low 56 bits are the landscape. The lobby shows the last four hex
digits ("#1A2B"), the skirmish "New" button and the multiplayer host's "New" roll another seed (the multiplayer lobby
now carries a seed for every stamped map; before, it always played the unseeded one).

**One build** (`GenPlan` -> `GenTerrain` -> `GenEconomy` -> checks -> `Fairness`):
* *Arena*: round (radius = ring + 90) or square (same area; seats in the corners, or between corners and sides at 4v4).
  `distanceToArenaEdge` / `insideArena` / `outerRadius` answer for both; the wall raises straight sides with corner
  towers (`BattlefieldWall.raise(..., square)`), the scout and the clients cover the corners.
* *Ground*: a warped fractal tier field, mapped by percentile (5% lakes, 11% T-1 valleys at 1, 44% lowland at 4, 25% T1
  at 8, 11.5% T2 at 12, 3.5% crests at 16; water at 0, fords and shores -1, deep -3), terraced by a "cliffiness" field:
  where it is high the step between tiers is a cliff, where low a walkable slope. Base pads flat (52); a plaza (30) in
  the middle; the rim shelf raises about half the edge to T1. Choke spurs across most main roads (a 14-20 gap); a
  meandering river with fords on some maps (always on River maps); lakes; forests by noise kept off roads, pads, sites
  and bases; the jungle grows thick.
* *Roads*: every seat's main road to the plaza and a seam road to each neighbour, held to a slope of one block per 3.3
  through anchors (pads, plaza, water crossings at 0 - fords -, roads already laid): **every road is walkable**, so a
  bastion's plateau has exactly its ramps.
* *Seat characters* (`Archetype`), one pair per map: **Prospector** (denser metal) vs **Warden** (two own capture sites on
  its forward ground, less metal); **Open** (no cliffs round the base, denser metal) vs **Bastion** (a plateau a tier up
  ringed by cliffs, two ramps, leaner metal); **Moated** (a river 70-90 in front, fords only) vs **Banks** (more land);
  **Mixed** (each seat its own); **Mirror** (point-symmetric terrain and economy: the "tournament" control).
* *Economy*, laid out as Beyond All Reason lays it (lovish Oct 27: "design them like beyond all reason and put clumps
  in strategic positions all around the map including the back rear sides ... clump them, have stranded ones alone.
  Maybe a path or cluster."; it replaces the Oct 27 21:37 random spread, keeping its rule that nothing stands round the
  town hall):
  * **Home clumps, the same for every seat**: a triangle of three straight behind the base (a row of three where the
    wall is close, as on a square arena's side seats) and a pair on each rear flank, 72 degrees off straight back, 48-72
    blocks out - past the 46-block build ground. One template in the seat's frame: the first distance and turn (0-50
    degrees) that every seat's ground takes is everyone's, and the ground is levelled for it (`GenTerrain.homePads`: a
    small level pad at the seat's height under each planned patch), so every lobby map in the production check has all
    seven for every seat. Standard patches, untouched by the lobby's richness, extra metal or the balancer.
  * One 2.0x patch per seat round the plaza (the middle's cluster), then **contested clumps of three**: one on every
    front seam (both neighbours walk about as far), two more on the front line in team games.
  * Then **groups over the open map**, seat by seat (always the seat furthest short of its allowance, so the dice never
    pile one seat's ground): the free spot of its ground farthest from all metal (a seeded jitter, a nudge toward roads
    and the front line and away from ground past the reach rule's 160 blocks' walk; anchors 1.25x the archetype's hole target apart - Prospector 29, Open 31, Banks 34, Plain 37,
    Moated 40, Bastion 43, Warden 46 blocks - 20% tighter in the rim band) becomes a **lone stranded patch** (likelier on
    exposed ground, the front line and the rim; at least one a map, at most 18% of the spread), a **path** of 3-5 along
    the nearest road ("metal along natural routes", 45% of the groups by a road), or a **clump** of 2-4 (2/3/4 at
    20/45/35%). A group's patches stand 9.5-14 apart; every patch keeps 26 from any other group. Farthest-first spreads
    the groups all round the map, the rim and the ground behind the bases included.
  * Counts: 28 / 20 / 15 patches a seat at 1v1 / 2v2 / 4v4 (home clumps and plaza patch included), times the
    archetype's density (its square root in team games) and the square root of the lobby's richness; the lobby's
    **extra metal** scales it from NORMAL: NONE 0.75x, LOW 0.875x, HIGH 1.25x. Values by exposure (rich on the front
    line, lean behind the base). Every patch: a level 7x7 pad of land, no deep water within 7, reachable from its owner,
    off the roads, at least 46 from any start (the build ground), at least the extractor clearance from the wall
    (`ExtractorClearance`, 11 today, the same rule as the pre-made maps), never in a reserved zone (section 2's rules).
    `MapLayout.homeEnd()` marks the home clumps (the first patches of the list).
* *Objectives*: a site on the plaza, a contested site on every front seam (where both neighbours walk about as far),
  two more on the front line in team games, a warden's own two; **modifiers** add theirs.
* *Jungle* (reserved for the hero slice, M12): one wild area per side (two in big team games) between the roads, with
  an entry and an exit that differ every game and data-only creep camps; the metal leaves it alone.
* *Modifiers* (`Modifier`), 1-2 per match when on (2-3 on surprise): Rich vein, Ley line (Ley Shrines along a line),
  Geothermal rift (vents + a Lava Vent site), Gold rush, Mercenary road, Garrison ruins, Watch ridge - all built from
  existing mechanics. Not rolled in the lobby until their mechanic exists: Hero relic, Hydra lair (the Oct 20 "hydras
  in the middle that buff the hero who kills them": a lair replaces the centre gold), Fog valleys. Named in chat when
  the map has risen; under the preview unless Surprise.

**Fairness, measured.** `Fairness` prices a seat in metal per second averaged over 15 minutes: its patches (T1 2.0/s
then T2 4.5/s after a 75 s refit, main's numbers since 0e223c88; built nearest first, shared with rivals by walk
distance, front-line ones partly lost to raids), its objectives
(income at 35 energy a metal, or an estimated worth), its defence (directions an enemy walks straight in), tempo (walk
to the middle) and space. Spread = (best seat - worst seat) / mean. The balancer (`SWAP_ROBUST`, chosen in Phase 1):
the same landscape with the seats turned round the ring and the sides' characters swapped, the most even of up to
eight builds, then small metal edits (a standard patch made rich on the poorest seat, a crowding patch removed from the
richest...) down to 80% of the tolerance, each edit judged under the central weights and under eight seeded draws of
the weights (the guesses in the score, priced within their ranges), going on while those draws' mean is still outside
the tolerance; a draw still outside it gives way to a fresh draw (up to four). The nudge's new patch is drawn at random
from the poorest seat's ground among the spots that join one of its clumps (9.5-14 from a clump of at most three, so it
stays 2-4), and a patch is only taken away when that strands no neighbour - the balancer keeps the layout. The
data-only camps count zero on lobby maps.

**The reach rule** (beside the score): a balanced draw is refused, and drawn again,
when a start's nearest patch is over 110 blocks' walk or its metal within 160 blocks' walk is under 0.6 of the best
start's (`MapGenerator.metalReach`).

**What Phase 1 measured** (200 seeds a cell, 1v1 and 4v4, round and square, five contrasts, six balancers; scored
again under 40 separate weight draws, and a crude 15-minute economy sim as a cross-check):
* Unbalanced, 1v1 seeds already land within 5% three times in four (median 3.1%); 4v4 never does (median 21%: the
  contrasts compound over four seats a side). Re-rolling the landscape alone barely helps at 4v4 (14%); turning the
  seats and swapping the sides gets 16%; small metal edits are what close the gap, to 3.5% in about 0.2 s.
* Swap + nudge gives the best central spread (1v1 median 1.1%); the robust nudge adds 2-17 points of "within 5% under
  other weights" (Prospector/Warden 1v1 0.51 -> 0.68; 3-11 at 4v4) for at most 7% more time. Under weight draws a 1v1
  lobby map stays within 5% about 80% of the time; a 4v4 map's *teams* about 98%, its *seats* only about 45% (median
  5.3%).
* That 4v4 seat limit is geometry, not the asymmetry: the point-symmetric Mirror control's seats already spread
  7.5% round and 11% square (front seats beside an enemy against back seats), its teams 0.3%. Team fairness is the
  bar that matters there.
* The sim agrees with the score on direction (correlation 0.36-0.58 on raw 1v1 maps) and after balancing finds no
  side that wins on average (4v4 team gap, archetype side A - B, means within +-0.5%), but it is too noisy to judge
  single 4v4 seats.
* A modifier moves the raw spread by under 2 points (most under 1); the balancer absorbs it.

**Tests** (`gametest/MapGenGameTests.java`, batch `maps_gen`): the same map from the same seed; every seat reaches
every seat, the middle and its whole economy by ground; level pads, no deep water near a patch, nothing nearer the
wall than the extractor clearance; reserved zones (the jungle and a caller's zone) free of metal, jungle doors on its
edge; seats and teams within the tolerance, starts on capitol ground inside their boxes; square edge helpers and the
square wall's length; the options ride the seed (off rolls nothing, the lobby only live modifiers). The symmetric preset tests sweep
`MapPreset.handmade()` only. JUnit (`bar/src/test/.../maps/gen`; headless: `tools/mapgen_sim/run.sh junit`):
`MetalClumpsTest` (2/4/8 seats, six seeds, round and square: metal in groups of at most 6, a 2-4 group compact, lone
patches at most 30% of the spread and at least one a map, paths on half the maps; a clump behind every base and
max(2, seats / 2) in the midfield; the home clumps the same for every seat; fairness and buildable pads) and
`GeneratedMetalTest` (tier 2 at main's numbers, nothing within 40 of a start, extra metal, the reach rule).

**Cost.** A 4v4 build is ~150 ms on one core; the balancer builds up to eight, so a lobby map takes about 0.5-0.7 s at
1v1, 1.1-1.2 s at 2v2 and 1.6-1.7 s at 4v4 (median; p95 1.9 s, worst seen 3.3 s; 240 lobby seeds, all valid, 17 needing a
fallback draw, mostly for the reach rule in the draws checked: `design/generated-maps-report.md`, "Oct 27 (later)"). The lobby
preview draws it on a worker thread ("Generating..."); the server builds it once at Start.

## 8. A new match on a running server (issue #35, Joisco)

"An option to reset the map, or move to a new location, so a server can keep hosting new games." The arena is not
regenerated and the world is not reloaded: `maps.NewMatch` moves the next match to **fresh ground one slot further out**
and everything anchored to the arena moves with it.

* **Slots** (`maps.ArenaSlots`, pure arithmetic, JUnit `ArenaSlotsTest`): a square grid, one arena per cell, slot 0 the
  arena the first match was played on, then a square spiral outwards (nearest unused ground first). The pitch is the
  widest arena a lobby can build (half-extent 1024: the 560 wall, a random-size wall, a full 16-seat ring) plus a 512
  margin, so two arenas never touch. The cursor is saved with the world (`maps.ArenaOrigins`, overworld data
  `bt-arena-origins`) the moment a slot is taken, so a restart or a crash never hands the same ground out twice.
* **The move** (`NewMatch.start(server, hint, force)`, one tick): refuse while a battlefield is being prepared (and
  while a match is in play unless forced); reset like `/rts-hard-reset`, but the old units and buildings are
  unregistered at once, the units removed at once (one left queued in a chunk that unloaded came back ownerless) and the
  buildings demolished in their own level over the next ticks (`maps.ArenaRetirement`, the defeat cleanup's rate - no
  wrecks, no effects, buildings ownerless first so a demolition can never defeat a player of the next match); take the next
  slot; move every player to it; recentre the world border (size and "unbounded" borders untouched); set the world
  spawn (where people land and join, i.e. the lobby's return point); forget the fog of war's view of the old arena (the
  fog snapshot went with the reset; the first side to start captures the new one, so nothing hidden is ever served);
  abandon the battlefield formation and navmesh warming meant for the old arena; generate the lobby's surroundings in
  the background.
* **The next match** needs no map code: the multiplayer lobby and the skirmish start take their centre from
  `ArenaOrigins.centreFor(level, hostPosition)` (the slot once a world has moved, the host's ground before), and the
  layout, start slots, wall, bot seats and capitol sites are all built round that centre already.
* **Callers**: `/rts-newmatch` (op level 2; `/rts-newmatch force` also during a match) for hosts, and for the lobby's
  end-of-match button (Lane C): `NewMatch.start(server, null, false)` on the server after the host check; show the
  result's message. `hint` only anchors the grid the first time (null finds the arena from its metal patches, the map in
  play or the world spawn).
* **Not moved**: hand-placed classic start blocks (`StartPosServerEvents.startPoses`) and authored map data stay where
  their author put them (hand-made data stays hand-made); nothing generated by a lobby uses them.
* **Tests**: `NewMatchRefusalTest`, `ArenaSlotsTest` (JUnit); `NewMatchGameTests` (`new_match_*`): the whole move
  (border, spawn, map and start slots at the new origin, old match gone, fog forgotten, a second new match = another
  slot), slots surviving a save, the op command.
