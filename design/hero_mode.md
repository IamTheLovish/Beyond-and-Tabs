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
- Hero-role players watch with the observer view in slice 1 (see "known gaps").

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

**Slice 3**: Hero-role players get their team's fog of war instead of the observer view; client-side movement prediction
(removes the ~100 ms input delay); hero-specific animations and sounds; possession aim for abilities that need a
building target.

**Slice 4**: hero item slots (RoN `UnitInventory` already exists), a hero shop at the capitol, Deadlock-style currency.

## Known gaps after slice 2
- Not verified in a real client (CI is headless): the skins on their bodies, the leap arcs, the HUD banner.
- The skins are re-dyes of the body units' textures with a crown/rim mark, not hand-drawn art.
- Abilities use vanilla particles and sounds; no cast animations of their own.
- The Storm Witch's flight is the Windcaller's toggle: while flying she uses the Windcaller's flying speed and range.

## Known gaps in slice 1
- Hero-role players watch with the observer view (fog off) until they possess; team fog for them is slice 2.
- Movement is server-authoritative with no client prediction: ~1 server round trip of input delay.
- Abilities that need a unit-specific targeting mode (e.g. a building target) fall back to the crosshair block.
- Flyers use the unit's own `travel`; units with unusual movement (slimes, mounts) may handle oddly.
