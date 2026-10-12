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
  start), as short vanilla trees, so they are reclaimable wood and never a canopy over a base or a main lane.
* **Then the economy** is stamped: mexes with their value, the map's own capture sites, lava vents.

### Nether / End without a dimension switch

Nether Rift and End Spires are built **in the Overworld** with nether/end blocks plus a client fog tint
(`ViewportEvent.ComputeFogColor`, dark red / violet-black). A real dimension switch was rejected: the whole
match stack (fog of war, battlefield setup, capture sites, metal patches, the bots, the wall) reads
`server.overworld()`, the world is created before the host picks the map, and moving every player + their
camera across dimensions mid-lobby is the kind of change that breaks saves. Overworld stamping gets 95% of the
look for none of that risk. (Vanilla's sky colour cannot be overridden without a mixin; the fog tint carries it.)

## 2. Metal values

`MetalPatches` now stores a value per patch (saved with the world, synced to clients). An extractor's income is
`base * value` (T1 2.0/s, T2 7.0/s). Rich patches show it in the ground: **1.0x** raw iron plus; **1.5x** raw
iron plus with a raw-gold heart; **2.0x** a gold-block heart. The minimap draws 1.5x patches gold, 2.0x bright
gold. Typical layout per slot: a home ring at 1.0x (safe, slow), forward/contested patches at 1.5x (take ground
to earn more), and the centre cluster at 2.0x (one per slot, the prize everyone fights over - chess's control of
the centre).

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

Capture rules unchanged (20 s uncontested, enemy drains first). New: every site shows its state on the map -
a floating label over the site ("Watchtower - Neutral", "Capturing 45% - name", "Captured by name",
"Neutralizing 30%") and an owner-coloured marker on the minimap (claimant colour ring while being taken).

## 4. The six maps

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

### Random terrain
The old behaviour: the Battlefield world preset's own terrain with the procedural ring of patches and the three
generic capture kinds. Lobby option `Map: Random terrain`.

## 5. Start-position phase (BAR's "start position mode")

After Start (and after a pre-made map has finished rising), a **45 s** phase:

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
  (equally fair). If every human has placed, the phase ends 3 s later.
* Then the match starts exactly as before (capitol, commander, workers, alliances), the battlefield forms
  (base clearing, wall at the map's radius in the map's material) - a pre-made map skips the procedural patch
  and capture stamping because the map already placed its own.
* Lobby option **Start positions: Choose / Auto** (host). Auto skips the phase (slot positions). The phase also
  skips itself for a bots-only match. Singleplayer quick start on Random terrain with Auto is the old instant path.

## 6. Tests (gametest/SkirmishGameTests.java, end of file)

* `premade_maps_are_fair_for_every_slot`: for every pre-made map and n = 2, 3, 4, 6, 8, 16: every slot's mex
  value (patches in its wedge) is equal within 5%, every slot is the same distance from the centre (within 2),
  and the terrain height at every slot is equal.
* `map_capture_sites_spawn_with_their_effect`: every map's site kinds are the ones it advertises; a captured
  Lava Vent pays energy, a captured Ancient Forge discounts research, an Oasis heals.
* `start_phase_places_everyone_in_their_box`: a click outside the box is refused, a click inside is kept,
  bots are placed at once, and the timeout places the rest inside their boxes and fires the start.
