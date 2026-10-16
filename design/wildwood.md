# The Wildwood - a hero-only jungle (concept)

lovish (Oct 16) asked for a landscape with design and reason: beautiful forests, intricate foliage, and a jungle,
dungeon or maze that **only heroes can enter and see**, with mobs for XP and loot, items levelling can't give,
mercenary huts with smart AI, and dodgeable attacks. Map: `design/wildwood_layout.png` (Verdant Valley, 2-slot cut). This
builds on hero_mode.md (slices 1-3) and maps.md, and keeps the decisions already made: 1-4 RTS + 0-6 heroes per team,
heroes spawn with their commander, rune towers, reclaim shops, neutral tiers, heroes never autocast, the chess lens.

---

## 1. The pitch

The **Wildwood** is a walled old-growth jungle on each seam between the teams. A living hedge, the **Thornveil**,
lets only heroes in, and only heroes see inside. Inside are camps, guardians, a turning maze, a dungeon, mercenaries
and a sleeping apex. Heroes come out stronger and carry home what a commander cannot get any other way: relics,
intel and shrine buffs. The commander does not watch the jungle; his heroes tell him about it. That makes the hero
player the team's eyes and hands where the RTS player can't reach, and gives both seats a reason to talk.

## 2. The Thornveil rule: hero-only entry and sight

**A "hero" here** is a `HeroUnit` that is currently **possessed**, by a Hero-role player or by its own commander. An
unpossessed hero is an ordinary RTS unit and the veil treats it as one.

| Viewer | Inside the veil they see |
|---|---|
| Hero-role player (observer view or possessing) | only what the team's **heroes and wards** see; sight capped at 24 in foliage, full in clearings |
| Commander, RTS view | the **canopy as it stood at match start** (a dark textured mass), the gates, the bird pulse; nothing that moves |
| Commander possessing his hero inside | the hero view; blind again the moment he returns to command |
| Enemy commander / enemy hero | the same dark canopy / his own team's heroes and wards (thicket concealment applies) |
| Real spectator | everything, with the canopy faded |

**Bird pulse:** when heroes fight inside, birds burst from the canopy over that 24 x 24 cell: 3 s late, at most once
per 6 s per cell, seen by **every** commander (minimap ripple + particles). It says "something is fighting there",
never who or how many. Recommended: a commander who gets no signal at all can only wait, and a public, coarse signal
gives both commanders something to react to.

**Entry and the edge**
- **The veil:** a 3-block thorn hedge, 8-12 high, with a closed canopy roof. **Four Thornways (gates)** per wood: each
  team has a **home gate** facing its base and a **field gate** facing the centre. Entry is through gates only. Gate
  mouths are on the outside, so everyone sees who goes in.
- **Exit:** through any gate, or anywhere after a 1.5 s "part the thorns" channel. Damage breaks the channel, and the
  veil shakes visibly at that spot. Hard to get in, easy to get out.
- **Combat lock:** a hero who dealt or took non-hero damage in the last 4 s can't pass a gate, so the jungle is no
  free escape from an army.
- **RTS units** collide with the veil like a wall: the pathfinder treats it as solid, and flyers treat the roof as a
  no-fly ceiling. A once-a-second sweep pushes out any non-hero that ends up inside (knockback, blinks, bugs).
- **Mobility abilities** (leap, blink, grapple, Root Walk) cannot cross the veil either way.
- **Projectiles and hit-scan** stop at the veil both ways.
- **Artillery: recommend no.** Shells burst harmlessly in the canopy. Blind shelling would let a commander farm the
  jungle without heroes, and a kill would leak information. The only fire from outside is a hero's *Beacon Seed*
  (section 6).
- **Unpossessed inside:** a hero who loses its possessor holds still for 10 s (a window to come back), then walks out
  of the nearest gate under AI.

**Intel economy: a commander knows what his heroes tell him**
- **Pings** from a ping wheel while possessed ("enemy hero", "camp down", "apex awake", "help", "coming out at field
  gate"). They show on his minimap for 20 s and are rate limited.
- **Camp ledger:** every camp, hut and guardian a hero has seen stays on the commander's minimap as a ghost with its
  state and age ("Tusker - dead - 40 s ago"), like BAR's last-seen buildings.
- **Reports:** a hero on an **Overlook** (a climbable giant tree) sends a 3 s snapshot of everything within 40 blocks
  as ghost icons. Cooldown 60 s per Overlook per team.
- **Hero cards** on the commander's HUD show HP, level and "in the Wildwood", never position.
- The commander's orders and pings still become objectives (hero_mode.md section 3), and contracts set bounties
  (section 9).

**Technical shape (mapped to the code)**
- **Zone mask:** `WildwoodZones`, built from a new `jungle` flag on `MapLayout.sample()`. Stored per chunk as 16 x 16
  column bitmasks (the fog's own format) and saved with `MatchState`.
- **Fog filter** in `FogOfWarServerEvents.updatePlayerBrightChunks`: two passes.
  - Pass one draws all team units into `outside`.
  - Pass two draws possessed team heroes and wards into `inside`.
  - Hero viewers (`HeroRoles.isHeroRole`, or a commander while possessing) get `(outside & ~zone) | (inside & zone)`;
    everyone else gets `outside & ~zone`.
  - Cost: one extra AND per touched chunk, and the second pass covers 6 heroes at most.
  - Without this, a unit standing at the veil would see 64 blocks in (`MAX_SIGHT_BLOCKS`).
- **Tracking:** `TrackedEntityMixin` already drops entity packets for columns that are not bright. Add a hard rule
  that an entity in a zone column is never sent to a non-hero viewer. Block changes in zone chunks (maze walls turning,
  chests opened) reach non-heroes only through the fogged-chunk snapshot.
- **Barrier:** a `ThornveilBlock` whose `getCollisionShape(state, level, pos, ctx)` is empty for an
  `EntityCollisionContext` of a possessed, unlocked `HeroUnit` and a full cube otherwise. This is vanilla's own pattern
  for powder snow and scaffolding. `PossessionPredictionClient` evaluates the same rule for the locally possessed unit.
- **Damage gate:** on the `LivingHurtEvent` path (`LivingEntityMixin.actuallyHurt`), cancel any hurt across the veil,
  AoE and explosions included, unless it is a Beacon Seed strike.

## 3. Layout and beauty: a forest with reasons

**Why the forest looks like this.** Water made the Wildwood:
- A spring under the **Heart Tree** feeds a stream that leaves through a water gate to the ring river.
- Trees crowd thickest along the water.
- Every clearing has a cause: a ruin, a rock outcrop, a fallen giant, a pool.
- The paths are the broken processional roads of whoever built the ruins: straight stretches that roots have bent.
- The Heart Tree's roots dug the Rootdeep dungeon.

**Rings, outside in**
- **Fringe** (by the gates): small camps, wide paths (4-5 blocks), thinner canopy. Each team's safer home jungle.
- **Tangle:** winding paths 2-3 wide, loops, elite packs, huts, hoard pockets, sinkholes. Where the ambushes happen.
- **Heart:** a large clearing with the apex lair and the Rootdeep entrance.
- **Rim** (against the wall): the Turning Maze and the stronghold rune-tower spots.

**Path rules**
- Every path is part of a loop; hoard pockets are the only dead ends, at 12 blocks or shorter.
- A path turns at least every 16 blocks, which breaks sightlines and makes ambushes possible.
- There are at least two routes between any two features.
- The wood is mirror-symmetric about the seam, so each team owns the half nearer its base.

**Vertical layers**
- **Canopy walkways** at +10-14: the fastest route, with no concealment.
- **Ground** paths.
- **Sinkholes and root hollows** at -3 to -6: ambush pits.
- **Ruins:** a round colonnade shrine (the overgrown round-temple reference), broken arches.

**Size and lobbies**
- On Verdant Valley's 1v1 ring (start ring 150, wall 236) each wood spans 88-226 blocks from the centre: about
  21,000 blocks², roughly 80 chunks.
- It holds **no metal**, so the RTS economy and the per-slot metal fairness test are untouched.
- At 8 slots, today's ring leaves seams about 116 blocks wide, too narrow for a wood. Recommend a **seam frame** in
  `MapLayout`: hero-mode lobbies widen each team seam to the 1v1 geometry, and the fairness test becomes per team.
- A hero-only **Waystone** at every capitol (4 s channel to your home gate) evens out walking distance for heroes
  whose commander sits mid-team.

| Preset | The Wildwood becomes | Veil / Heart |
|---|---|---|
| Verdant Valley (slice 1) | old-growth temperate jungle: giant oaks, moss, ferns, a stream | thorn hedge / spring under the Heart Tree |
| Archipelago | mangrove: roots over shallows, stilt walkways | hanging-root curtain / a drowned temple on a lagoon islet |
| Highlands | cloud-forest gorge cut into a ridge, rope bridges | gorse on the rims (ridges are free walls) / a waterfall pool |
| Nether Rift | Ashroot: a fungus labyrinth with basalt pillars as maze walls | weeping vines + shroomlight / a magma cavern |
| End Spires | Hollow Choir: chorus thickets on linked islets, hero-only bridges | shimmering lattice / an obsidian ring garden |
| Desert Mesa | Sunken City: an oasis jungle over a ruined street maze | cactus-thorn wall / a temple courtyard |
| Soulfire Deltas | Bone Thicket: a soul-sand swamp of dark stems and soul lanterns | weeping vines / a crypt; the Forsaken Garrison moves to the field gate, so armies fight at the jungle's door |
| Chorus Isles | Drifting Grove: high islets on thread bridges; rails still apply | lattice / the highest islet; garrison islets stay at the field gates |

**Art direction**
- **Five foliage layers:**
  - ground cover: moss carpet, grass, ferns, flowers;
  - shrubs, 1-2 high: azalea;
  - understory, 2-6 high: bushes, hanging vines;
  - mid canopy at 8-14;
  - emergent giants at 18-30, with 3 x 3 to 5 x 5 trunks and buttress roots.
- **Light shafts** fall through canopy holes over every clearing, from our own fog and particle layer.
- **Value plan:** the canopy is darkest (deep blue-green), the understory a step lighter, and paths lightest and warm,
  at least two value steps above the foliage and edged with ferns.
- **Reserved hues:**
  - amber = jungle currency, gates and huts;
  - cyan = rune towers;
  - hot orange-red = creature telegraphs, never used on a plant.
- **Landmarks:** one warm-crowned giant per half (the one-warm-tree-in-a-cold-scene rule), plus the Heart Tree and
  the ruin. Each shows through canopy gaps, so a hero always knows roughly where he is.

**Cheap to render**
- Decoration is static blocks only: no entities or block entities for decor.
- Leaves use `persistent=true` (no decay ticks); vines are a non-spreading variant (no random ticks).
- Glow comes from emissive textures, not many light blocks.
- Ambient particles (fireflies, falling leaves) are spawned client-side, only round a possessing hero's camera, and
  capped. This is the ground focus maps.md said the RTS camera lacks.
- `MapBuilder` stamps both woods (about 350k blocks) without neighbour updates inside its 35 ms budget. The target is
  15 s or less of extra build time.
- The canopy fade on roadmap item 14 stays **off** over the zone for non-hero viewers.

## 4. Dungeons and the maze

**A. The Turning Maze** (Rim, one per wood)
- **Shape:** a hedge labyrinth 32 blocks across, made of three concentric rings whose gaps **rotate one step every
  45 s**, like a combination lock.
- **Rules inside:** no radar, sight 12, and the **Bellwether** hunts by sound.
- **Entry:** two doors, one facing each team's half, so the enemy can be in there with you.
- **Run time:** 2-3 min.
- **Reward:** a Storied chest (a choice of 2 augments) or the Bellwether's Crown.
- **Cost:** rotating about 300 hedge blocks, spread over 10 ticks.

**B. The Rootdeep** (a descent under the Heart)
- **Entry:** a sinkhole in the Heart Tree's roots, guarded by the Mossgate Sentinel. Killing it drops a Root Key
  that lets 3 heroes in.
- **Floors** (40-70 s each):
  1. **Roots:** a creature gauntlet.
  2. **Drowned Hall:** levers drain the water channel by channel; mercs cannot swim it.
  3. **The Vault:** a guardian fight.
- **Exit:** a root lift back up.
- **Run time:** 3-4 min.
- **Risk:** the door stays open for 20 s, so enemy heroes can follow you down.
- **Reward:** a Heartwood unique, or an instant ability rank (a rune-tower charge).
- **Placement:** it sits under zone columns, so the column-based fog hides it for free. A dungeon under the open
  centre would need a Y-aware entity filter.

**C. The Hush Reliquary** (a sphinx gate - folklore, our own guardian)
- **Opening:** one team must hold **three glyph stones** spread across the wood at the same time for 10 s. That takes
  three heroes coordinating.
- **Run time:** about 2 min.
- **Reward:** a team relic for the commander.

## 5. Jungle creatures

**Tiers:**
- **small camps:** 4 per wood, respawn 75 s;
- **elite packs:** 2 per wood, guarding the huts, respawn 2 min;
- **guardians:** fixed spots, respawn 3 min;
- **the apex:** wakes at 10:00 in one Heart, alternates seams after each kill, back 6 min after it dies.

**Leash:** creatures are tied to their clearing with a 14-block leash (the field neutrals use 30). On reset a creature
walks home regenerating 25% a second, immune for 3 s. Creatures never pass the veil, so they can't be dragged onto the
field.

**Scaling:** from minute 4, creature health and damage rise 5% every 2 min, capped at +50%; XP and amber scale with
them. An uncleared camp **grows a brood** (+1 creature, +25% loot, at most 2). That is the garrison bank applied to
the jungle: a camp you leave alone grows for whoever takes it.

| Creature (tier) | Telegraphed behaviour | XP | Loot |
|---|---|---|---|
| Bramblehog x3 (small) | paws the ground for 0.8 s while a dust line marks the charge lane; sidestep it | 20 each | Sap Draught |
| Mirewight x2 (small) | a 0.6 s arm raise, then a slow mud ball (14 b/s) that leaves a slowing puddle; splits in two on death | 25 | Thornbomb |
| Glimmerwing x4 (small, moths) | wings brighten for 1 s, then a dust ring cuts sight to 8 for 4 s; step out of the ring | 12 each | Glimmer Dust |
| Reedstilt x2 (small, waterside) | head cocks back for 0.5 s, then a 4-block straight peck | 25 | Wild trinkets |
| Veilhound x3 (elite) | one howls and marks you with a glint; two flank 1 s later; the only creatures that juke | 50 each | Assassin and Ranger augments |
| Ironbark Tusker + 2 calves (elite) | rears for 1.0 s, then a 5-block stomp ring; a rear tail-sweep cone (0.6 s) | 120 | Warden augments, Rootbound pieces |
| Loomspinner (elite, canopy) | drops on a thread while its shadow grows for 1.2 s; a web shot (12 b/s) roots for 1 s; hatchlings | 140 | Oracle and Warlock items |
| Mossgate Sentinel (guardian) | a two-handed slam: 1.2 s raise, then a line decal; a glowing sap-knot on its back takes double damage, so flank it | 300 | Root Key |
| The Bellwether (guardian) | hears sprinting and casting; its bell rings 0.9 s before a charge; stunned 2 s if it hits a wall | 300 | Crown of the Bellwether |
| Hush Sphinx (guardian) | floor glyphs fill over 1.5 s; the wrong glyph costs 1 s of silence and damage | 300 | team relic |
| **Gloamhydra** (apex) | five heads, five breaths (fire cone, acid line, roar ring, bite lunge, spit arc) and a 2.5 s sweeping tide; a cut head regrows in 12 s unless its stump is **burned** (fire, the Kindler merc, a Sap Torch), so it needs several heroes | 400 to every team hero within 30 | the Heartseed + 1 Storied per hero who fought |
| Gleamfox (rare) | no attack; a treasure runner every 4 min that flees along the paths and burrows after 20 s | 30 | hoard key, amber |

**The level-5 cap: change it for the jungle.** Today `MAX_NEUTRAL_EXP_LEVEL = 5` stops all neutral XP at level 5
(`HeroServerEvents`, `HeroExperienceOrb`). Keep that rule for field neutrals. Wildwood creatures get their own table:
- **100%** XP to level 5, **60%** at levels 6-7, **35%** at 8-9;
- XP is **split** between team heroes within 20 blocks, +10% for each extra hero. Today every hero in orb range gets
  the full amount, which would let six heroes farm one camp six times over.

Why: capped at 5, the jungle is pointless after minute 10; uncapped, a hero can skip the war entirely. With the taper,
the jungle alone carries a hero to about level 8 in a long match, enemy kills stay the fastest late XP, and the
tier-2.5 dip survives.

## 6. Loot that levelling can't give

**Kinds:**
- **Augments** change *how* an ability works, never how much. One per ability, at most 2 equipped.
- **Trinkets:** passives.
- **Consumables:** charges, refilled at an owned hut.
- **Set pieces:** weak alone, strong together.
- **Heartwood uniques:** one per match.

**Rarity:** Wild < Hallowed < Storied < Heartwood.

The class tags map onto today's heroes until the open roster exists:
- Warden = Dawn Paladin
- Warlock = Gravecaller
- Assassin = Warchief or Corsair
- Oracle = Druid or Storm Witch
- Engineer = Arch-Engineer
- Ranger = the first roster hero

| Item | Kind | Effect |
|---|---|---|
| Echo Sandals | augment, Assassin | your dash, leap or blink leaves a 2 s decoy that creatures and mercs attack first and enemy heroes see as you |
| Bramble Mantle | augment, Warden | when your shield or charge ends, melee attackers take back 25% of what they dealt for 3 s |
| Lantern-Eye | augment, Oracle | your reveal or heal also plants a 45 s sight ward (radius 16, at most 2) |
| Tithe Bell | augment, Warlock | your summons burst for 8 magic when they die; summon cap +1 |
| Spring Mount | augment, Engineer | pick up and re-place your turret once; turrets last 50% longer inside |
| Stillwater Fletching | augment, Ranger | after 2 s still, your next shot gains +30% range and pierces one target |
| Second Wind Ember | augment, utility 4th | each creature or hero kill takes 3 s off its cooldown |
| Moss-Heart | trinket | +2 HP/s inside the Wildwood after 4 s out of combat |
| Pathfinder's Knot | trinket | +12% speed off the paths; enemy footprints show for 4 s |
| Quiet Clapper | trinket | silent sprints and casts: the Bellwether can't hear you and you don't trigger the pulse |
| Sap Draught (3) | consumable | heal 40 over 4 s; damage from a hero breaks it |
| Glimmer Dust (2) | consumable | reveals a 12-block circle for 6 s, concealed heroes included |
| Thornbomb (2) | consumable | a 4-block ring that roots for 1 s (counts toward the 1.5 s cap) |
| Sap Torch (1) | consumable | burns a hydra stump; +6 sight in the maze for 20 s |
| Beacon Seed (1, Hallowed) | consumable | plant it: your commander sees a 20-block circle for 30 s and may fire **one** artillery strike into it |
| Rootbound Hood, Greaves, Gauntlets | set | 2 pieces: +1 merc cap. 3 pieces: your mercs react in 0.15 s and heal 1 HP/s near you |
| Crown of the Bellwether | Heartwood | enemy heroes who sprint or cast within 30 ping on your radar; drops on death |
| The Heartseed | Heartwood, objective | takes 2 slots, slows its carrier 25%, glows for everyone outside the veil; at your capitol it becomes a team relic for 8 min |

**Drops**
- **Consumables and Wild items:** personal loot. Everyone who dealt damage rolls separately.
- **Hallowed and better:** go into the camp's **Hoard chest**, which anyone opens with a 3 s channel (damage breaks
  it). The killing team has a 10 s head start, then the chest is open to all.
- **Inventory:** the existing 6 `UnitInventory` slots. A consumable stack takes one slot; at most 2 augments.
- **Death:** equipped items are kept (today's revive rule). Half the carried amber drops, and objectives drop where
  the carrier fell.

**Anti-snowball**
- Augments are sidegrades.
- **Wildwood's mercy:** the team behind in total hero levels gets +25% Hallowed chance and +15% creature XP.
- A hero on a 3-kill streak inside carries an amber **bounty**.
- At most 2 team relics can be active.

**For the commander:** relics and trophies become team passives at the capitol; spare items sell for amber to send
home (lovish's reclaim rule); a Beacon Seed gives him a strike.

## 7. Mercenary huts

**Where and how they are taken**
- Two per wood, one in each team's half of the Tangle.
- A new capture kind, **HUT**, in the CAMP family (`CapturePointServerEvents`), with the same rule: within 6 blocks,
  uncontested, for 20 s.
- Only heroes can capture, and the hut's elite pack must die first, which makes it the first guarded site.
- In hero mode, Verdant Valley's boundary Mercenary Camps move into the Wildwood as huts. Field camps keep hiring
  wolves for commanders.

**Hiring**
- Press Use at the hut for a menu of 2-3 types; the offer rotates every 4 min.
- **Cost** in amber, the jungle's look for hero reclaim (one currency, not two): Green 40, Veteran 80, Elite 140.
  A small camp drops about 20.
- **Contract:** 4 min, renewable at any owned hut. That is the whole upkeep: no pop and no drain on the commander.
- **Caps:** 2 per hero (3 with the set), 6 per team.

**Sworn to the wood:** mercs never leave the veil. When their hero goes out, they **hold the gate he left by**. This
keeps the jungle a separate game, keeps the RTS unit count down and keeps the fog rules simple.

**Smart UI (while possessed)**
- Hold the **Merc key** (default R, rebindable) for a radial menu: Follow, Hold, Focus my target (under the
  crosshair), Guard me, Use ability, Back to hut.
- Tap R to focus the crosshair target; double-tap R for Follow.
- Each merc gets a HUD card (HP, cooldown, current order) and its own radar colour.

**MercBrain** scores options 5 times a second, on top of the existing AutoCaster, Kite, Brace and Engage slots:
- **Dodge:** it reads the shared `Telegraph` registry and steps to the nearest safe edge when the impact comes later
  than its reaction time. For projectiles it checks the closest approach and sidesteps.
- **Peel:** tanks take an Engage slot between attacker and hero; ranged mercs slow the attacker.
- **Ability timing:** heal below 50% of the hero's HP, interrupt channels, save the shield for a telegraph aimed at the
  hero, and chain roots only after the hero's stun ends (the 1.5 s cap).
- **Combo sense:** on the hero's CAST event, a follow-up fires within 0.3 s at the same spot.
- **Retreat** below 25% HP; come back at 60%.
- **Tier = reaction time:** Green 0.45 s (misses 30% of dodges), Veteran 0.30 s, Elite 0.18 s.

| Mercenary | Role | Ability, and how the AI uses it |
|---|---|---|
| Thornshield | tank | *Interpose*: jumps beside the hero and blocks the next projectile when a telegraph or volley targets him |
| Reed Archer | kiting ranged | *Pinning Shot*: slows the hero's focus target |
| Sapwitch | healer | *Mend*: heal over time and cleanse a root, held until the hero drops below 50% |
| Snarewright | trapper | *Net Line*: a 1 s root on the hero's retreat path, or right after his stun ends |
| Kindler | fire | *Firebrand*: burns hydra stumps and thickets; the worst dodger |
| Owlet Scout | flyer | *Perch*: sits on the canopy and shows enemy heroes within 24 to the team |

**Enemy counterplay**
- **Steal the hut:** a capture flips it. Your mercs finish their contracts but can't be renewed there.
- **Bribe:** stand next to an enemy merc for 2 s and pay twice its price; it walks off neutral. It never swaps sides,
  so there is no flip-flop snowball. Mercs are immune for 45 s after hire.
- **Kill them:** each pays a small amber bounty.

## 8. Dodging and readable combat

- **Telegraphs:**
  - ground decals (a ring, cone or line that fills over the windup);
  - weapon glints 0.3 s before a fast hit;
  - windup poses.
  - Minimums: light 0.4 s, heavy 0.8-1.2 s, apex 1.5 s.
  - One `Telegraph` registry (shape, owner, time to impact). The client draws it; creature and merc AI read it.
- **Projectiles:** creature shots fly at 12-20 b/s, which gives about 0.5 s or more from 10 blocks. Arrows keep
  today's no-lead aim, so moving already dodges them; shells keep their 60% lead.
- **Melee must become dodgeable.** Today `MeleeWindupAttackUnitGoal` lands at the end of the windup whatever the
  distance.
  - Change: at windup end, hit only if the target is still within reach + 0.6 and inside a 120-degree front arc;
    otherwise whiff, with a sound.
  - Enable it first in fights with a hero, creature or merc. Extend it to all units only if the duel CI's `[Melee]`
    numbers hold; Engage ring slots should keep ordinary RTS melee in reach.
- **Hero dodge:** the Dodge key (default C, or double-tap a direction):
  - a 3.5-block dash in 0.25 s with 0.3 s of i-frames (lingering ground effects still apply);
  - 5 s cooldown, no mana, refused while rooted or stunned;
  - server-driven like leaps (SYNC_SUSPEND);
  - "favour the dodger": the server honours a dodge started up to 2 ticks before a hit resolves, for players
    100-200 ms away.
- **The AI plays by the same rules.** Creatures and mercs use exactly these telegraph and dodge rules; HeroBot heroes
  get the dodge too.

## 9. More concepts (the best ten)

1. **Night canopy:** 2 min of every 10. Sight inside drops to 12, moths and the Loomspinner get stronger, paths glow.
   An ambush window.
2. **Weather:** rain masks sound, so the Bellwether hunts by sight; mist cuts sight in the Tangle to 10.
3. **Contracts:** the commander pays metal for a bounty on a camp, guardian or enemy hero his heroes have reported.
   It shows as an objective, and the hero earns amber and XP on top.
4. **Smuggling:** the Heartseed and trophies must leave by a gate and be carried home. Outside the veil they are
   visible to all and any unit can pick them up, so armies escort or intercept.
5. **Encroachment:** every 5 min the veil grows about 10 blocks outward (twice at most) unless constructors cut it
   back for wood at half rate. Ignore the jungle and your field shrinks (zugzwang).
6. **Shrines:** a hero channels for 5 s and his army outside gets 90 s of +8% speed or +8 sight. 3 min cooldown per
   team.
7. **Ambush:** a hero who stands still in dense foliage for 2 s is concealed from enemy heroes beyond 6 blocks (the
   existing Concealment rule). The first hit from concealment deals +20% and slows for 0.5 s.
8. **Overlooks:** climbable giant trees with Report, linked by canopy walkways.
9. **Challenge stones:** two enemy heroes may accept a 30 s duel in a ring nobody else can enter.
10. **Spectator cut-away:** the canopy fades, and an "inside camera" follows the biggest jungle fight.

## 10. The two roles together

**From the jungle to the RTS war:** shrine buffs; team relics; amber sent home; intel (pings, the ledger, reports,
Beacon Seeds); hero power from rune towers (the stronghold towers sit inside, guarded); Beacon strikes.

**From the RTS war to the jungle**
- Gates are visible, so armies can **camp the enemy's gates** and catch heroes coming out.
- Escort or intercept smugglers.
- Cut back encroachment.
- Buy hero upgrades (already decided).
- Research **Wildwood Lore**, the scouting upgrade: it reveals rune-tower spots (already decided) and sharpens the
  pulse to the named camp.
- Post contracts and build the Waystone.

**The commander is never just waiting:** pulses, reports, contracts and gate fights all ask him for decisions.

**Ignoring the jungle is not an auto-loss**
- There is no metal inside.
- A relic is worth one T2 upgrade at most, and at most 2 are active.
- Jungle XP tapers.
- Gates can be camped.
- The Heartseed can be stolen outside.
- With no hero players, the map keeps its normal forest bands: hero mode off means no Wildwood.

**Balance levers:** camp XP and respawn times; drop rates; relic strength and duration; merc caps and prices; veil
growth; apex timing; the pulse delay; the combat-lock time.

## 11. Phasing

| Slice | Content | GameTest | Performance check |
|---|---|---|---|
| **J1** smallest playable | one Wildwood on Verdant Valley 1v1; zone mask, fog filter, tracking rule; Thornveil + gates; 3 camps (Bramblehog, Mirewight, Glimmerwing); 4 items (Sap Draught, Glimmer Dust, Moss-Heart, Echo Sandals); split, tapering XP | `wildwood_commander_cannot_see_inside` (zone columns never bright for him; an entity inside tracked for the hero only); `thornveil_blocks_units_and_arrows_admits_possessed_hero`; `jungle_xp_splits_and_tapers` | mask cost in `updatePlayerBrightChunks` (+0.1 ms per update or less); extra build time; at most 30 creatures per wood |
| **J2** | `Telegraph` registry and decals; melee reach check; hero dodge | `melee_whiffs_when_target_steps_out`; `dodge_iframes_ignore_one_hit` | decals client-side, at most 24 at once; duel CI `[Melee]` before and after |
| **J3** | guarded HUT sites, hire menu, MercBrain, 3 mercs, radial UI | `hut_hire_respects_caps_and_contract`; `merc_steps_out_of_a_telegraph`; `bribe_frees_a_merc_without_flipping` | MercBrain at 5 Hz, at most 12 mercs per map, a `[Stress]` notice |
| **J4** | rarity, hoards, augments, sets; Beacon Seed; pings, ledger, reports; bird pulse | `hoard_priority_then_open`; `beacon_seed_is_the_only_cross_veil_damage`; `pulse_is_coarse_and_delayed` | one pulse packet per cell per 6 s |
| **J5** | Turning Maze + Bellwether; Rootdeep + Sentinel; Gloamhydra + Heartseed; elites; stronghold rune towers | `maze_rings_rotate_and_stay_solvable`; `rootdeep_key_opens_for_20s`; `heartseed_relic_applies_at_capitol` | maze rotation 3 ms per tick or less, over 10 ticks |
| **J6** | all 8 presets; seam frame; Waystone; night and weather; contracts; encroachment; bots in the jungle | `wildwoods_are_fair_per_team_for_every_lobby`; ground-connectivity test extended to the gates | 1+6 vs 1+6 soak, server tick at or under 50 ms |

## 12. Open questions for lovish (recommended answer first)

1. Can mercs leave the Wildwood? **No, they hold your gate**; letting them out turns heroes into small armies.
2. Does a commander who possesses his hero see inside? **Yes, only while he is in that body.**
3. Can artillery hit inside? **No; only through a hero's Beacon Seed.**
4. Should jungle XP flow past level 5? **Yes, tapering (60%, then 35%) and split between heroes.**
5. Should commanders see the bird pulse? **Yes: coarse, 3 s late, the same for both teams.**
6. Lobbies with several RTS players: widen the team seams so the wood stays full size? **Yes, the seam frame plus a
   Waystone.**
7. Dodgeable melee for all units or heroes, creatures and mercs first? **Heroes, creatures and mercs first; all units
   after the duel CI numbers.**

## 13. Skill expression and peak power (lovish, Oct 16 05:33)

His words: "I want heros to have alot of skill expression and at peak capaibilities can help turn the tides of battle
a bit". Two rules come out of it: **the skill ceiling is high**, and **a peak hero tips a close fight but cannot
win a lost one alone**.

**Where the skill lives (each one a thing a better player does better)**
- **Aim:** hero abilities are aimed at the crosshair, not locked on. Skillshots travel and can miss; creatures and
  some units have weak points (the Sentinel's sap knot) that take bonus damage from the side or back.
- **Reading and dodging:** every heavy attack is telegraphed (section 8). A hero who reads it dashes through with the
  i-frames; one who doesn't eats it. A **parry window** (combat-reactions Round B) on melee heroes: block in the last
  0.2 s of a windup, stagger the attacker.
- **Cancels and combos:** an ability can be cancelled into the dodge in its first frames; abilities interact (a root
  makes the next strike detonate, a knock-up lets a slam hit twice, fire on a thicket makes a smoke screen). Combos
  are discoverable, never listed in a tooltip as one button.
- **Charge and timing:** hold-to-charge abilities (a longer charge = more power, a visible tell for the enemy); a
  perfect-timing bonus on some (release at the glint).
- **Positioning and terrain:** canopy walkways, ambush pits and concealment (section 9) reward a hero who plays the
  ground; flanking pays (back hits).
- **Builds:** augments change HOW an ability works (section 6), so two heroes of one class can play differently.
- **Commanding mercs** from the radial menu while fighting is its own skill.
- **The floor stays low:** a new player still contributes - basic attacks, a safe ability, following pings.

**Peak power: tips the tide "a bit"**
- A level 8-10 hero with two augments is worth roughly **one T2 squad** in a fight - enough to swing a close fight
  (about 10-15% of an army's value), never to beat a whole army: army units already deal bonus damage to heroes
  (the "heroes beat small squads, lose to blobs" rule from the hero-mode brainstorm).
- **The ultimate is the tide-turner, and its size is earned by skill:** one per hero, a 90-150 s cooldown, unlocked at
  level 6. Examples: a sun banner planted in the line (+15% damage and morale for 8 s to every allied unit around it),
  a wall of light that stops projectiles for 4 s, a charge that breaks a formation and staggers what it hits, a
  storm that slows a whole push. Well aimed it hits twelve units at the decisive moment; badly aimed it hits two.
- **The commander can amplify it:** the RTS player's T3 tech upgrades the ultimates of his heroes (already in the
  brainstorm), so the moment is a team play: commander builds, hero lands it.
- **Counter-play:** ultimates have a visible wind-up or a planted object enemies can break or step out of; the enemy
  commander can bait them with cheap units; a dead hero is out for a level-scaled respawn.
- **Balance is fluid; competitive integrity is the bar (lovish, Oct 16 07:02: "if we focus super hard on the flat
  numbers of balance you lose the nuance of how player controlled units can be used in ways which make them appear a
  lot stronger then having them just straight up fight each other").** Numbers are guardrails, not the design:
  - A hero is judged **as played**, never in a stand-still stat duel: the test bots that measure heroes kite, dodge
    telegraphs, focus the right target and land skillshots (the MercBrain rules, section 7), so a hero's strength
    includes what control adds.
  - **Kiting is a core skill:** ranged heroes have a short attack windup and may move between shots without losing
    damage (attack, step, attack - the marksman's dance); melee heroes answer with gap-closers, slows and dashes. A
    good marksman beats a melee hero who walks straight at him; a good melee player who closes the gap at the right
    moment beats a careless marksman. (lovish, 07:03-07:04: "Even in Minecraft people with a bow and knockback can
    kite someone with a diamond sword" - given as an EXAMPLE of the principle, not a mechanic to add: control lets a
    weaker kit beat a stronger one. Knockback is optional; if a hero ever gets it, it comes with diminishing returns
    and a melee answer.)
  - **Skill must show in the results:** the check that matters is the spread between a skilled and an unskilled bot
    playing the same hero - a big spread is good. The guardrail is only that nothing is unbeatable: every strength has
    a visible counter, the same dodge and telegraph rules apply to everyone, and no hidden dice decide fights (little
    or no random crit).
  - Tuning after playtests is normal and welcome; the rules above stay fixed while the numbers move.

**Ultimates come in kinds; not all of them are long (lovish, Oct 16 07:00: "Ultimates can be shorter if it makes
sense use league of legends and the abilities of the champions some champions ultimates let them switch from jaguar
form to panther")** - League's form-swap champions are the behaviour reference (look only, our heroes are original).
Each hero has ONE ultimate slot, of one of these kinds; the cooldown follows the kind, and the impact follows the
cooldown (a short ult never swings a fight on its own; the long ones still can):

| Kind | Cooldown | What it is | Where the skill is |
|---|---|---|---|
| **Form swap** | 3-6 s | two whole kits: the swap trades the 3 basic abilities for another 3 (each form's cooldowns keep ticking while you are in the other); the swap itself does a little (a pounce, a roar) | weaving the two kits: cast in one form, swap, cast the other's while the first cools - the highest skill ceiling of all |
| **Stance / weapon swap** | 4-8 s | one body, two weapons or stances (hammer and cannon; shield-wall and charge) | reading the fight: which stance this second |
| **Meter transform** | fills in combat | a meter (fury, sunlight, hunger) builds from dealing and taking hits; full = a 10-15 s transformed state, bigger and stronger, then back | timing the fill, spending it at the moment that matters, not when it happens to be full |
| **Short ult with resets** | 20-40 s | a strong single-target or mobility ult whose cooldown refunds on a takedown | chaining resets through a fight |
| **Tide-turner** | 90-150 s | the big team moment (rally banner, wall of light, formation-breaking charge) | landing it on twelve units at the decisive second |

Example (original): **the Wildshaper**, an open-roster hero - in **human form** a spear-thrower and healer (a long
skillshot spear, a trap, a self-heal); in **panther form** a hunter (a pounce that resets when it lands on a marked
target, a rake, an execute below 30% health); the swap is the ultimate on a 4 s cooldown from level 1, and level 6
adds a stronger swap (the first ability after it is empowered). Others: a hammer-and-cannon engineer (stance swap), a
hero who grows into a giant at full fury (meter), an assassin whose dash resets on takedowns (short ult).
Rule for the roster: a mix of kinds, so a team of six does not have six 120-second buttons, and the tide-turning
moments stay rare and readable.
