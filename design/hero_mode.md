# Hero mode - design

lovish's goal, in his words: "I want first person POV to have a good experience ... players can launch in a hero
position to spawn in first person Minecraft to act in a hero sort of way, gaining exp by killing units and
unlocking different abilities to help the player in the RTS position." The hero "can either be the player in first
person or switch to 3rd-person RTS mode, controlled like a normal unit. A player without an RTS role can take over
the abilities and stats of the hero unit to stay relevant as the game goes on."

His answer on control: **either, switchable** - any teammate can take the hero; when nobody does, it is an RTS unit
like any other.

Reference clips: **Silica** (an FPS player drives an RTS-built unit; "UNIT FEED: COMBAT TANK" top right; the
commander's orders become on-screen objectives with a waypoint; circular radar) and **Deadlock** (third-person hero,
4 abilities on a bar, a currency from kills, "Boon received: ability point, power increase").

Everything below builds on what Reign of Nether already has: hero units (`HeroUnit`, levels 1-10, XP orbs from
kills within 16 blocks, skill points, 4 `HeroAbility` ranks), `HeroProductionItem` (train one) and
`ReviveHeroProductionItem` (revive the fallen one at its level), the hero packets, and `PacketAuth` for who may do
what. Forge means every player has the mod: "a player without an RTS role" is a modded teammate who picked the Hero
role in the lobby.

---

## 1. Roles in the lobby

Each human seat on a team picks a role (`MultiplayerLobby.Seat.heroRole`, the Role button under Ready/Spectate):

| Role | Base | Plays as |
|---|---|---|
| **Commander** (default) | own capitol, commander, workers, resources - as today | the RTS view; may also possess any of their own units or their hero at any moment |
| **Hero** | **none** - no seat on the start ring, no resources, not a match participant (can't be "defeated") | spectates their team until they possess the team's hero (or a unit a teammate lent them) |

Rules (server-enforced in `MultiplayerLobby.whyNotStartable` / `startMatch`):
- A team needs at least one Commander; a Hero on a team with no Commander can't start the match.
- Hero seats need no colour and don't count as a side for "at least two sides".
- At start, each Hero-role player is attached to their team (`HeroRoles`): the team's Commanders' names. Possession
  rights flow from that.
- Hero-role players watch with the observer view under **their team's fog** (slice 3, section 4b) - never the whole map.

## 2. Possession

Anyone with rights may **possess** a unit: the camera goes into that mob (first person, or over the shoulder),
the unit drops out of RTS control, and the player drives it.

**Who may possess what** (`PossessionServer.mayPossess`, checked on every claim):
- the unit's owner, or a player the owner gave allied control, or a sandbox player (`PacketAuth.mayActFor`);
- a Hero-role player attached to the owner's team: the team's **hero units** always; **any** unit of a Commander who
  switched on "allow possession of my units" (`/rts-possession allow`);
- never an enemy unit, never a unit somebody else already possesses, never a dead/removed one, never a building.

**Controls while possessed** (the vanilla Minecraft bindings, so they follow the player's own Controls setup):
- WASD move, mouse look, **Space** jump (flyers: climb), **Shift** (flyers: descend);
- **left click**: the unit's own attack at whatever is under the crosshair (melee: in reach; ranged: its own
  projectile at the target, or at the ground point);
- **1-4**: the unit's first four abilities, cast at the crosshair (entity or ground);
- **F5** (or **V** on a 60% board): first person <-> over-the-shoulder third person;
- **Backspace** (the RTS/Minecraft toggle) or **Esc -> Return to command**: back to the RTS view.

**Entering**: Backspace in the RTS view with exactly one unit selected possesses it; a Hero-role player with nothing
selected possesses the team's hero. With nothing selected a Commander's Backspace keeps its old meaning (Minecraft
view).

**HUD while possessed** (`PossessionClientEvents`): "UNIT FEED: <unit name>" top right; a compact HP bar, the hero's
level/XP bar and the four ability slots with cooldown sweep at the bottom; a crosshair; a circular radar top left
(own/allied/enemy units within 48 blocks, rotated with the view, the objective marker clamped to its rim); the current
objective as a banner top centre with distance and direction.

**What the server does**: the unit's AI is suspended (a mixin cancels `Mob.serverAiStep` for possessed mobs only -
one map lookup, an empty-map early-out for everybody else) and the latest input drives `xxa/zza/yya`, jump and
rotation through vanilla `travel`, so the unit walks at **its own** speed with its own collision. Abilities briefly
hand the AI back (3 s) so cast goals that walk into range and channel still work.

**Skill bonus while possessed** (small on purpose - a human aiming should feel better, not dominate): **+10% damage**
(any damage the possessed unit causes, projectiles included) and **15% shorter attack cooldown** (the "wind-up").

**Server validation** (`PossessionServerboundPacket`): every packet is rate limited (`PacketAuth.allow`); a claim is
checked with `mayPossess`; an input/attack/ability packet is dropped unless the sender is the unit's current
possessor; analogue values are clamped (move -1..1, pitch -90..90); the attack target is found by the server along
the possessor's look ray within the unit's own range - the client never names a target.

**Leaving** - any of: Backspace / Return to command; the unit dies (the Hero-role player spectates until the hero is
revived with the existing revive item, then can possess it again); the possessor logs out; the unit is removed.
The unit is reset to idle and is an ordinary RTS unit again.

**Allies see it**: a possessed unit wears a "★ <player>" tag for its owner and allies, so a commander knows not to
count on ordering it.

## 3. Orders become objectives

A possessed unit is excluded from RTS orders (`UnitActionServerboundPacket.accept` -> `PossessionServer.divertOrders`),
but the order is not lost - it becomes the possessing player's **objective**:

| RTS order on the possessed unit | Objective shown |
|---|---|
| Move / attack-move / patrol | MOVE TO THE TARGET LOCATION (waypoint at the point) |
| Attack a unit | ATTACK THE TARGET (waypoint follows it) |
| Attack a building | DESTROY THE BUILDING |
| Guard / follow | PROTECT THE TARGET |
| Stop / hold | objective cleared |
| A map ping by the owner or an ally | PING: GO HERE |

The waypoint is a beam of particles in the world, a marker on the radar and an arrow + distance on the banner. A
move objective clears itself when the unit gets within 4 blocks; any objective expires after 60 s.

## 4. Hero progression (slice 2, built)

Code: `hero/HeroProgression.java` (curve, boons, capture XP), `hero/FactionHero.java` (stats per level).

- **XP from kills**: Reign of Nether's orbs, unchanged - a unit dying within 20 blocks of an enemy hero drops orbs worth
  (pop + 1) x 5 (+5 per level of a killed hero) that fly to the hero.
- **XP from capture points**: taking an Ancient Mine / Ley Shrine / Mercenary Camp (any capture site) pays every hero
  of the taker's team within 24 blocks **100 XP**; holding it pays **1 XP a second** to the team's heroes within 24
  blocks (CapturePointServerEvents.setOwner / reward -> HeroProgression.onCapture / onHold). An early capture is a third
  of a level: "capture points give hero levels".
- **The curve** is Reign of Nether's: each level costs 160 XP more than the last - 320 XP for level 2, 1600 for level 10,
  8640 in all.
- **Boons** are the levels, announced Deadlock style: "Boon received: ability point, power increase" in chat for the
  owner and the possessor, a totem burst on the hero, and a banner over the possessor's crosshair ("BOON RECEIVED /
  Level N - ability point, power increase") for 4 s. Each boon gives **one ability point** and the level's **stat
  growth**, and heals the health it added. A new hero starts with one point.
- **Ability ranks**: four abilities x three ranks = 12 ranks for 10 points, so a build is a choice. Abilities 1-3: rank r
  needs level 2r-1 (1/3/5). The utility 4th: rank r needs level 6/8/10.
- **The tier-2.5 dip**: stat growth per boon is +8% health and damage to level 5, +5% to level 8, +2% after (x1.51 at
  level 10). With the XP per level climbing, 1% of power costs 60 XP at level 3 and 720 XP at level 9 - twelve times
  dearer. A hero races ahead early, keeps pace through tier 2 and flattens out while the RTS side's T3 keeps growing;
  late, what it adds is the team-utility 4th ability.
- **Mana**: heroes keep Reign of Nether's mana (each kit 100-160, +8-14 a level, regenerating ~1 a second); abilities
  cost 20-80. Cooldowns and mana are only spent when an ability actually lands.

Ranking: the commander uses Reign of Nether's rank-up menu on the command card (U); a possessing player presses
**Alt + 1-4** (PossessionServerboundPacket RANK_UP, the same server rules). The possession HUD shows three rank pips
under each ability slot, a gold edge on a slot a point could go into, the hero's mana bar and "N ability point(s): Alt +
1-4 to rank up".

## 4b. Slice 3: team fog, movement prediction, combat feel (built)

**Team fog for Hero-role players** (code_review CR-06). `FogOfWarServerEvents.fogAppliesTo(name)` is "an RTS player
or a Hero-role player"; `teamOwnersFor(name, owners)` is the set whose vision a player shares - a player and their
allies; a Hero their commanders and the commanders' allies, i.e. exactly what any of those commanders sees. A Hero's
spectator body adds no vision bubble of its own. Observer view and possessed are the same rule (the possessed unit is
a commander's unit, so it is a viewer of the team). Real spectators keep the full view. The client mirrors it: the
ROLE packet carries the commanders ("hero|a,b"), `FogOfWarClientEvents.refreshLocalIsRTSPlayer` turns the client fog
on for a Hero, and `PossessionClientEvents.isTeamOwner` keeps the team's units from ever self-hiding. HeroRoles
changes drop the fog's per-player cache; a Hero who relogs gets ROLE again; enemy traps are hidden from Heroes too.

**Client-side movement prediction** (`PossessionPredictionClient`, `PossessionPredictionMixin`, `PossessionServer.applyClaim`).
The friend lovish plays over e4mc sits 100-200 ms away; WASD used to wait one round trip.
- *Predict*: the client's copy of the possessed unit counts as "controlled by this client" - the switch vanilla flips
  for a horse the local player rides (`Entity.isControlledByLocalInstance`, client copies only). Vanilla then ignores
  the server's position packets for it and runs the unit's real travel physics locally (its speed, collision, steps,
  jumps, water, a flyer's own travel). The keys are fed in exactly as the server's `drive` does.
- *Claim*: after each client tick the reached position goes to the server with the keys and a sequence number
  (serverbound MOVE, every tick, in the existing 30/s input rate limit).
- *Server authoritative*: the server moves the real unit to the claim with `Entity.move` (vanilla collision, steps,
  fall damage), at most what the unit could have walked since the last claim: 3.2 x speed^2 + 0.06 blocks a tick for
  walkers (vanilla's limit is ~2.86 x speed^2 on any block), a generous bound for flyers, plus any shove it is riding;
  the ticks come from a move budget refilled one per server tick (max 10), so inflated sequence numbers buy nothing.
  Vertically: a step or a jump, never hovering - a walker in the air for 2 s whose claims don't fall is dropped. If
  the unit ends up away from the claim (> 0.35 blocks) the possessor gets SYNC with the true position (at most one per
  4 ticks).
- *Reconcile smoothly*: the correction names the claim it answers; the error is measured against where the client was
  at that claim (a 64-entry history), so movement made since is kept, and it is blended in at 35% a tick - no rubber
  band. Only an error over 3 blocks (a teleport) snaps. An honest client under ~150 ms gets no corrections at all.
- *Shoves*: knockback and blasts reach the client as a motion packet (sent explicitly to the possessor, since vanilla
  only sends it when the unit ticks before its attacker) and are predicted; the periodic velocity echo is ignored.
- *Hand-back*: while the server moves the unit itself (an ability's 3 s AI window, a hero's leap / dash / grapple arc)
  it says SYNC_SUSPEND and the unit is interpolated from the server as before; at the end it resumes from the server's
  position.

**Combat feel** (`PossessionHudClient`; server: `PossessionServer.onLivingDamage`, `castCheck`). Silica's reticle and
Deadlock's ability feedback, readable in first person:
- *Hit marker*: every hurt the possessed unit deals (melee, its projectiles, its abilities) comes back as HIT with the
  final amount: an X round the crosshair and a tick sound; a kill is red, longer and chimes. Capped at 6 per tick.
- *Damage numbers* (optional: Mod config `hero_damage_numbers`, or the button on the Esc menu while possessing):
  float up from the target, gold for 10+, red on a kill, a little larger with distance.
- *Cast feedback*: the slot flashes the moment 1-4 is pressed (no round trip); the server answers CAST - a gold edge
  and a cast sound when it took the cast, a red slot and the reason under the crosshair when not ("ON COOLDOWN",
  "NOT ENOUGH MANA", "NOT LEARNED - ALT + N TO RANK UP", "STILL CASTING"). A refused key no longer hands the AI back.
- *Cooldown ready*: a slot whose cooldown runs out pulses and chimes.

**First-person polish**:
- *Crosshair* adapts: melee brackets that close in on a hostile within reach; a ranged ring with "distance / range"
  that opens up when the target is out of range. Red = hostile in reach, pink = out of reach, blue = friendly. One aim
  ray a tick with the server's own reach rules.
- *Compass*: a strip along the top (N/E/S/W, 15-degree ticks) with the objective's bearing on it; an objective off to
  the side also gets a chevron round the crosshair. The objective banner moved under the strip.
- *Radar terrain*: cliffs (2+ blocks between 2-block cells) and water outlined under the unit dots, from the client's
  height map, rebuilt every half second.

**CR-07**: the server no longer prints "press Backspace"; the client prints the Hero message on the ROLE packet with
whatever the camera toggle is bound to (the lobby's Role tooltip too). **CR-17**: `HeroRoles.clearAll` (reset, lobby
close, server stop) revokes each Hero, sending ROLE off, so the client's Backspace and fog stop with the server's.

GameTests (`gametest/HeroGameTests.java`): `hero_role_fog_equals_the_teams`, `possession_corrects_an_impossible_client_position`,
`hero_role_off_is_sent_on_reset`.

## 4c. Slice 4: Glory, items and the hero shop (branch `hero-slice-4`)

**What we looked at, and what we took.** *Deadlock*: souls from last hits on troopers (a share to heroes nearby),
from hero kills and objectives; a share of your unsecured souls goes to whoever kills you; shops in base plus "secret
shops" out on the map; items in four price tiers (500 / 1250 / 3000 / 6200), three colour-coded categories (weapon,
vitality, spirit), each item owned once, lower-tier items that are *components* of higher ones (their price counts),
sell-back at half. *Dota 2 / Warcraft III*: six hero slots, a full refund for a few seconds after a buy. *Silica*: the
FPS player is one more piece of the RTS team, not a separate game. *ZeroSpace*: in-match hero progression and neutral
capture zones that feed it. *BAR*: a trade is read in metal; the enemy's economy and tech are private. The chess lens:
going home to shop costs **tempo**, a forward shop is **space** you must hold, and every strong item has a readable
answer.

### Glory - the hero's currency
**Per hero**, not per team: it lives on the hero (`HeroCore.glory`), is saved with it (the hero NBT's `charges`
field), survives death less a toll and comes back with a revive. Why per hero: the hero earns it by being in the fight
(proximity and last hits) - it is the Hero-role player's own progression; a team purse would let eight commanders
funnel one hero; and it keeps the RTS economy apart (balance rule 5): metal never buys items, Glory never buys units.

| Source | Glory |
|---|---|
| **Last hit** - the hero, its ability or its summon lands the killing blow | 20% of the victim's metal value |
| **Assist** - an enemy dies within 20 blocks of the hero (the XP-orb radius) | 8% of its metal value, to each such hero |
| Neutral creatures | half of the above |
| **Hero kill** | as any unit (its 300 metal) + 25 a level, and the victim's **death toll** - 20% of its unspent Glory - shared by the heroes credited |
| **Taking a capture site** | 100 to each of the taker's heroes within 24 blocks (with slice 2's 100 XP) |
| **Holding sites (tribute)** | 12 a minute per held site, shared by the holder side's living heroes wherever they are |
| Starting purse | 250 for a newly trained hero (its first T1 item); a revived hero keeps what it had |

Why metal value: it is BAR's own measure of a trade (piece value), and it makes Glory grow with the RTS pace by
construction - bigger armies, more metal destroyed near the hero, more Glory - which is what "the hero grows with the
RTS" asks for. The tribute is where the commander's map play pays the hero player.

### Where and who
**Shop points**: any built capitol of the hero's side (within 12 blocks) and any capture site its side holds that no
enemy unit stands on (within 10 blocks). **Out of combat only**: no damage dealt or taken for 5 s. The capitol is the
safe home shop; a held site is the forward "secret shop" - holding sites now matters to the hero (vision #3), and the
enemy's counterplay is to contest the site, which closes the shop. Not "anywhere out of combat": a free mid-map refit
removes the tempo cost; not in combat: no unseen panic-buys that swing a fight.

**Who buys**: whoever drives the hero decides - while it is possessed, only the possessor; otherwise anyone who may
order it (owner, allies with shared control, sandbox editors, `PacketAuth.mayActFor`) and the Hero-role players serving
its owner. Bots buy through the same server path (`HeroBot`: a short wish list per role, at a shop point; bots-4 may
route heroes home to shop).

### Items
Six slots (Reign of Nether's `UnitInventory`). Three tiers, six items per tier per hero: T1 six generic, T2 five
generic plus one **faction** signature item, T3 six generic. Colour = what it does: **Might** (orange: damage),
**Vigor** (green: survival), **Arcana** (violet: mana, cooldowns, vision). Each item once per hero; four T2 items are
**upgrades** of a T1 (the T1 is consumed and its full price counts, so the slot is freed and nothing is lost).
Actives: keys 5-9 and 0 in first person, the item hotkeys (T Y F G C B) in the RTS view. Items are bound to their hero:
they can't be dropped, given or sold at a market.

| Tier | Item | Kind | Glory | Effect |
|---|---|---|---|---|
| T1 | Vigor Charm | Vigor | 250 | +45 max health |
| T1 | Keen Edge | Might | 250 | +1.5 attack damage |
| T1 | Quickhand Gauntlets | Might | 250 | +12% attack speed |
| T1 | Traveller's Boots | Vigor | 200 | +0.025 movement speed (~9%) |
| T1 | Mana Shard | Arcana | 225 | +40 max mana, +0.6 mana a second |
| T1 | Healing Salve | Vigor, active 30 s | 200 | heal 40 |
| T2 | Windstride Boots (from Traveller's Boots) | Vigor, active 25 s | 550 | +0.04 speed; sprint: Speed II 4 s, sheds slows |
| T2 | Bloodthirst Blade (from Keen Edge) | Might | 650 | +2.5 attack damage, 15% lifesteal (attacks and abilities) |
| T2 | Wardstone Mail (from Vigor Charm) | Vigor | 600 | +70 max health, +3 armour |
| T2 | Focus Crystal (from Mana Shard) | Arcana | 600 | +60 max mana, +1 mana a second, hero abilities recharge 15% faster |
| T2 | Cleansing Draught | Arcana, active 40 s | 500 | purge slows, roots, stuns and other harm; Resistance I 3 s |
| T2 | Sunforged: Dawn Censer | Arcana, active 45 s | 600 | friends within 7 heal 25; undead enemies within 7 take 15 magic |
| T2 | Gravebound: Soul Lantern | Arcana | 600 | each enemy dying within 12 restores 4% health and 6 mana |
| T2 | Horde: Bloodrage Totem | Might | 600 | below half health: +25% attack speed, +10% lifesteal |
| T2 | Verdant Court: Heartwood Charm | Vigor | 600 | the hero and friends within 6 regenerate 1 health a second |
| T2 | Tidewrought: Kraken's Conch | Might, active 40 s | 600 | drags enemies within 7 toward the hero and slows them 2 s |
| T2 | Stormcallers: Stormglass Orb | Arcana | 600 | abilities cost 20% less mana; each cast sparks the nearest enemy within 8 for 6 magic |
| T2 | Brassworks: Clockwork Overdrive | Might, active 45 s | 600 | the hero and friendly machines within 8 attack 30% faster for 6 s |
| T3 | War Banner | Might, aura | 1300 | friendly units within 8 deal 8% more damage |
| T3 | Warden's Standard | Vigor, aura | 1300 | friendly units within 8 take 10% less damage |
| T3 | Oracle's Lantern | Arcana | 1100 | +10 sight; every 8 s enemies within 22 are revealed to the side for 4 s and outlined |
| T3 | Rally Horn | Vigor, active 70 s | 1200 | friendly units within 14 heal 15 and gain Speed I and +8% damage for 6 s |
| T3 | Siegebreaker Maul | Might | 1100 | +150% damage against buildings |
| T3 | Phoenix Ember | Vigor | 1400 | once every 180 s a killing blow leaves the hero at 30% health, invulnerable 2 s |

**Sell-back** (at a shop point, out of combat): the full price within 10 s of buying (undo a misclick), half after.
An upgraded item sells on its full price (the component's share included).

**Keeping the dip.** T1 items are *flat* stats - +45 health is a quarter of a level-1 hero and a rounding error next
to a T3 - so they matter in the hero's strong early phase and fade as the armies grow. T2 buys sustain and mobility to
pick fights through tier 2. T3 adds **no stat to the hero itself**: auras, vision, a rally, objectives and staying
alive - force multipliers for the army, exactly the utility the late hero is for. Items are unique, the two auras and
the rally never stack with their own kind, and the hero's raw growth still tapers (section 4). Measured, not assumed:
`HeroShopGameTests.hero_duels_slice_4` fights a level-5 hero with a full T1+T2 build against three Iron Golems - it
should still lose or trade evenly (section 6, rule 2).

**Privacy** (code_review R2-09, network/Audience): a faction hero's Glory and inventory go to its **side** (TEAM):
`ItemClientboundPacket` and the batched unit sync send a faction hero's inventory only to its side and spectators, Glory
travels as the hero's charges (TEAM in `AudienceResync`) and in the shop STATE packet (TEAM). Enemies see what an item
*does* - an active's burst, an aura's particles - never the build or the purse, as BAR hides the enemy economy and tech.

**UI.** First person: **B** opens the shop (a screen: the mouse is free, the hero stands still); the HUD shows Glory,
the six slots with their keys and cooldowns, "+N" on every gain and whether a shop is open. RTS view: the **Shop**
button on the hero's card (Space) opens the same panel over the HUD. One panel: three tier columns (cost under each
icon, greyed when unaffordable, a gold edge on an upgrade you can afford), the hero's six slots (right-click sells), the
purse and the shop status ("Shop open: Capitol", "No shop here", "In combat 3 s").

**Server authority.** `HeroShopServerboundPacket` (BUY item, SELL slot, USE slot while possessed): rate limited
(`PacketAuth.allow`), the sender must be allowed to shop for that hero (above), and the server re-checks the shop
point, combat, price, slots, faction and ownership; the client never names a price. `HeroShopClientboundPacket`: STATE
(purse, shop open, combat) to the hero's side, RESULT (why a buy failed) to the requester alone.

### Weak heroes: abilities, not stats
Slice 2's duel rows (level 5, two Iron Golems) left three heroes short, at the stat cap: Gravecaller 0.6x, Warchief
0.7x, Storm Witch 0.8x (the Dawn Paladin and Thornheart Druid beat two Golems at 1.8x and 2.0x). Each gets what its
kit was missing:

| Hero | Ability | Before | After |
|---|---|---|---|
| Gravecaller | Bone Spear | 8-16 magic along a line | **Soul Siphon**: heals for 35% of the damage it deals |
| Gravecaller | Grave Step | blink; slows enemies left behind; bots never used it | the grave **roots** enemies left behind for 1 s, then chills them; bots blink **away** when melee reaches them |
| Gravecaller | Raise the Fallen | cooldown 40/35/30 s | 32/28/24 s |
| Warchief | Frenzy | +40-60% attack speed, takes +20% damage | also heals for 25/30/35% of the damage he deals while frenzied |
| Warchief | Cleave | 8-16 to the front arc, shove | no shove (it threw his targets out of reach); **Sunders** them: +15% physical damage taken for 4 s |
| Warchief | Savage Leap | landing hits and slows | the landing also stuns 0.5 s; bots leap back in from 3 blocks |
| Storm Witch | Chain Lightning | 2-4 jumps x0.75, never the same target twice | jumps x0.8 and may arc back (never twice in a row): a pair takes every jump |
| Storm Witch | Frost Nova | 5-11 magic, Slowness III | also **Chills** (attacks 29% slower) for 4/5/6 s and shoves the ring outward |

The Storm Witch's flight was not given to the bots as an escape: melee can't strike a flyer, so a witch who lifts off
whenever a Golem arrives would beat any melee army - the counter to her flight is anti-air, which a duel has none of.

### Capture points (vision #3)
Slice 2 paid XP (100 on taking a site, 1 a second while held, to heroes within 24 blocks). The gap: holding paid only
a hero parked on the site, and a site gave a hero nothing it could *use*. Slice 4 adds the Glory bounty on taking a
site, the tribute (every held site pays its side's heroes wherever they are) and the forward shop at every held,
uncontested site.

### Slice 3's known gaps
- *A Hero client keeping pre-match chunk data*: checked, not a leak. Before the match the map is public (BAR shows the
  whole map) and holds no bases; from the moment `HeroRoles.assign` runs the server serves fogged chunks from the
  pre-match snapshot (`ChunkMapInitialSendMixin`) and the client freezes fogged columns (`ClientChunkCacheMixin`),
  exactly as for an RTS player. Nothing to fix.
- *Vehicle riders not predicted*: not cheap. Predicting a rider means predicting its vehicle - the client would have to
  run the mount's travel with the rider's keys and claim the mount's position - which needs per-mount work (jockeys,
  the bison's own steering). Left server-driven; written down under Known gaps.

## 5. One hero per faction (built)

Each faction has one hero (`hero/HeroKits.java`; bodies in `unit/units/heroes/`), trained at its **T1 army lab** on
the I slot (with its revive on the same slot while it is dead): Barracks, Graveyard, Military Portal, Grove, Slipway,
Lotus Academy, Machine Shop. Reign of Nether's hero rules apply: one of each hero, the allowedHeroes gamerule (2), only
one hero until a castle-tier building stands - so the Sunforged choose between the Royal Guard, the Enchanter and the
Dawn Paladin, and so on. Cost 300 metal, 300 energy, 45 s, 4 pop; revive at Reign of Nether's level-scaled price.

Each hero is a body - an existing unit's model, renderer, goals and attack, a tenth bigger, in its own skin
(`tools/gen_hero_skins.py`) - plus a kit. Each has its own kind of movement, and its 4th ability is team utility:

| Faction | Hero (role, body) | Movement | 1 | 2 | 3 | 4 - utility (level 6) |
|---|---|---|---|---|---|---|
| Sunforged | **Dawn Paladin** (vanguard, Halberdier) | charge | *Shield Charge* - dash to a spot, stun (0.5-1 s) and shove enemies there | *Judgement* - 10-20 magic on one enemy, +50% on the undead | *Sun Banner* - Resistance to friends in 8 (Regeneration at rank 3) | *Aegis of Dawn* - an 8-16 HP shield on every friend in 14 |
| Gravebound | **Gravecaller** (caster, Stray) | blink | *Bone Spear* - 14-block piercing line, 8-16 magic | *Grave Step* - blink 8-14 blocks, slows enemies left behind | *Raise the Fallen* - 2-4 skeletons for 20-30 s | *Grave Sight* - every enemy in 24-36 revealed to the team and outlined |
| Horde | **Warchief** (raider, Brute) | leap | *Savage Leap* - leap 10-14 blocks, landing hits and slows | *Frenzy* - +40-60% attack speed, Speed, takes +20% damage | *Cleave* - 8-16 to the front arc, shove | *War Drums* - Strength and Speed to friends in 14-16 |
| Verdant Court | **Thornheart Druid** (support, Thornbow) | root walk (blink) | *Thorn Volley* - 5-11 and a 1-1.5 s root round a spot | *Root Walk* - rise 10-18 blocks away, root enemies there | *Rejuvenate* - heal 8-16 + Regeneration round a spot | *Grove Seed* - 1-5 thicket patches that hide the army |
| Tidewrought | **Corsair Captain** (raider, Cutlass Raider) | grapple | *Pistol Shot* - 10-18 to one enemy, shove | *Grapple Hook* - swing 10-16 blocks, landing slows (rank 2+) | *Riptide* - wave: 6-12, hard shove, slow | *Tidepool* - a tidepool (Tides bonus) for 30-60 s |
| Stormcallers | **Storm Witch** (caster, Windcaller) | **flies** (Cloud Ride) | *Chain Lightning* - 10-18 magic, 2-4 jumps x0.75 | *Cloud Ride* - the Windcaller's flight toggle | *Frost Nova* - 5-11 and a heavy slow round her | *Mana Font* - +100-250 energy, friends' cooldowns -4-8 s |
| Brassworks | **Arch-Engineer** (skirmisher, Rifle Squad) | rocket jump | *Rivet Burst* - 3 rivets, ricochets for half | *Rocket Jump* - jump 8-14 blocks, landing jolts | *Deploy Turret* - 1-2 Guildmaster turrets (20 s) | *Field Repair* - machines +30-60 HP and plated, crews +10-20 |

How they cast: one order per slot (`UnitAction.HERO_ABILITY_1..4`). From the command card (Q/Z/E/R), a bot or a
possessing player's keys 1-4, an aimed ability hands the hero's cast goal the target; the hero walks into range, winds
up and the effect lands. Leaps, dashes and grapples fly a scripted arc (HeroCore) and land; blinks teleport to the top
block at the spot (never up a cliff more than 12 blocks).

Bots (`hero/HeroBot.java`): from minute 5 with Tier 2 the bot trains its faction hero at a lab that offers it, revives
it when it falls, spends its points (the utility 4th as soon as it can, then the lowest-ranked of the others) and casts
simply - strikes at the nearest enemy in range, gap closers for the melee heroes, self-centred abilities when enemies
are within reach. Never while a player possesses it.

Unit card: a faction hero's role line reads "Hero - Vanguard / Raider / Caster / Support / Skirmisher".

## 6. Balance rules

1. Possession is a **control** upgrade, not a stat upgrade: +10% damage and -15% attack cooldown, nothing else.
2. A hero never beats an equal-metal T2 squad alone after tier 2.5; it wins by picking fights.
3. No ability is map-wide; no ability is permanent; every stun is <= 1.5 s.
4. A possessed unit is still a unit: it dies, it costs pop, its death gives XP to enemy heroes as normal.
5. A Hero-role player gives the team no extra income or production - only a human behind the team's hero.
6. Everything server-validated (`PacketAuth`), every serverbound packet rate limited.

## 7. Slices

**Slice 1 (this branch, `hero-possession`)**
- Lobby Hero role per seat (no base, attached to their team).
- Possession of own units / the team hero / lent units, server-validated; AI suspended by mixin; first person and
  over-the-shoulder; WASD/mouse/jump/attack/abilities 1-4.
- UNIT FEED label, HP/XP/ability HUD, crosshair, circular radar.
- +10% damage, faster attack cooldown while possessed.
- Possessed units excluded from RTS orders; orders and pings become objectives with a waypoint.
- Death / logout / Backspace / Esc button release; ally marker.
- `/rts-possession allow|deny` for lending your units to Hero-role teammates.
- GameTests: claim, enemy refused, non-possessor input refused, death releases, Hero seat gets no base.

**Slice 2 (branch `hero-slice-2`)**: the seven faction heroes and their kits, capture-point XP, boons with the HUD
banner, rank pips and Alt + 1-4 ranking while possessed, the tapering growth (the dip), bots training and using their
hero, the unit-card hero role. GameTests in `gametest/HeroGameTests.java`.

**Slice 3 (branch `hero-slice-3`)**: Hero-role players get their team's fog of war instead of the observer view;
client-side movement prediction with server validation and smooth reconciliation; hit markers, damage numbers, cast
feedback and cooldown cues; adaptive crosshair, compass and radar terrain; CR-07 and CR-17 (section 4b).

**Later**: hero-specific animations and sounds; possession aim for abilities that need a building target.

**Slice 4 (branch `hero-slice-4`)**: Glory (per hero: last hits, assists, hero kills, capture sites, tribute), the hero
shop at capitols and held sites with 24 items in three tiers (six slots, components, faction items, bound to the hero),
sell-back, the shop UI in first person (B) and the RTS view, team-only Glory and inventory, ability fixes for the
Gravecaller, Warchief and Storm Witch, bots buying (section 4c). GameTests in `gametest/HeroShopGameTests.java`.

## Known gaps after slice 3
- Not verified in a real client (CI is headless): the feel of prediction over a real 100-200 ms link, the HUD layout
  (compass, crosshair, damage numbers), the sounds.
- Prediction relies on client and server running the same travel code; a unit whose travel reads server-only state
  (none known) would be corrected often rather than mispredicted.
- A Hero's client keeps whatever chunks it had before the match started under the fog tint (as an RTS player's does);
  only enemies and fresh changes are gated.
- Units riding a vehicle are not predicted (server-driven as before).

## Known gaps after slice 2
- Not verified in a real client (CI is headless): the skins on their bodies, the leap arcs, the HUD banner.
- The skins are re-dyes of the body units' textures with a crown/rim mark, not hand-drawn art.
- Abilities use vanilla particles and sounds; no cast animations of their own.
- The Storm Witch's flight is the Windcaller's toggle: while flying she uses the Windcaller's flying speed and range.

## Known gaps in slice 1
- (fixed in slice 3) Hero-role players watched with the observer view (fog off).
- (fixed in slice 3) Movement had no client prediction: ~1 server round trip of input delay.
- Abilities that need a unit-specific targeting mode (e.g. a building target) fall back to the crosshair block.
- Flyers use the unit's own `travel`; units with unusual movement (slimes, mounts) may handle oddly.
