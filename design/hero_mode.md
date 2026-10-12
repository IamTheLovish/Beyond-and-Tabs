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

## 4. Hero progression (slice 2+)

Heroes level as RoN heroes already do (kills within 16 blocks drop XP orbs; levels 1-10; one ability point per
level), with three additions:

- **Capture points give XP**: holding an Ancient Mine / Ley Shrine / Mercenary Camp pays the team's hero(es) 40 XP on
  capture and 5 XP/10 s while held (only to a hero within 24 blocks, so it rewards the hero being there).
- **Boons** at XP thresholds, Deadlock style: "Boon received: ability point, power increase" banner and sound, +1
  ability point, +4% HP/damage. The thresholds are the existing level curve (200, 300, 400 ... XP), so boons and
  levels are the same event, just announced.
- **The tier-2.5 power dip**: lovish wants heroes strong early and less dominant late, with utility valued more. The
  XP curve already steepens (each level costs 100 XP more than the last); slice 2 adds: per-level stat growth falls
  from +8% (levels 1-5) to +3% (levels 6-10), and each hero's 4th ability (unlocked at level 6) is a **team utility**
  ability (vision, a shield, a resource or production effect) rather than more damage. Late game a T3 experimental
  out-trades a level-10 hero 1:1; what the hero adds is reach and timing.

Growth must keep pace with the RTS side: a hero's power at minute N should roughly match what the same metal buys in
units at minute N. Revive cost scales with level (already in `HeroUnit.getReviveCost`).

## 5. One hero per faction (4 abilities each)

Each faction gets one hero, trained at its T1 lab (RoN's hero production), built on the existing hero unit where one
fits. Ability 1-3 unlock at levels 1/3/5 (ranks up to 3), ability 4 (utility) at level 6.

| Faction | Hero | 1 | 2 | 3 | 4 (utility, lvl 6) |
|---|---|---|---|---|---|
| Sunforged | **Paladin** (RoN Royal Guard) | *Shield Bash* - stun the target 1.5 s | *Sun Banner* - plant a banner: allies +15% armour 10 s | *Consecrate* - holy ground damages undead/enemies | *Rally Point* - commander can produce units at the paladin's banner for 30 s |
| Gravebound | **Necromancer** (RoN Necromancer) | *Bone Spear* - piercing line | *Raise Dead* - 3 skeletons from nearby wrecks | *Soul Siphon* - drain HP from a target | *Grave Sight* - reveals all enemy units near wrecks for 15 s |
| Horde | **Berserker** (RoN Wildfire slot) | *Leap* - jump onto a target | *Frenzy* - +40% attack speed, takes +20% damage | *Cleave* - wide arc | *War Drums* - nearby units build Momentum twice as fast 15 s |
| Verdant Court | **Druid** (flyer form) | *Thorn Volley* | *Owl Form* - fly, faster, can't attack | *Entangle* - roots a group | *Grove Seed* - plants a Thicket that hides allied units |
| Tidewrought | **Corsair** | *Pistol Shot* | *Grapple* - pull self to a block | *Riptide* - wave knocks back | *Tidepool* - raises a tidepool (Tidewrought bonus for all nearby) |
| Stormcallers | **Storm Witch** (flyer) | *Chain Lightning* | *Blink* | *Frost Nova* - freezes the ground | *Mana Font* - refunds energy to nearby spellcasters |
| Brassworks | **Engineer** | *Rivet Gun* | *Deploy Turret* - temporary auto-turret | *Overclock* - nearby machines +40% fire rate | *Field Repair* - repairs machines/buildings in an aura |

Specialised support: the 4th abilities are deliberately what a commander can't do from above (spot, rally, refund,
repair at the front), so a Hero-role player stays useful when the RTS side outgrows any single unit.

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

**Slice 2**: capture-point XP, boon banners, the per-level growth change (power dip), Hero-role players get their
team's fog of war instead of the observer view, client-side movement prediction (removes the ~100 ms input delay).

**Slice 3**: the seven faction heroes and their ability kits (section 5), utility 4th abilities.

**Slice 4**: hero item slots (RoN `UnitInventory` already exists), a hero shop at the capitol, Deadlock-style currency.

## Known gaps in slice 1
- Hero-role players watch with the observer view (fog off) until they possess; team fog for them is slice 2.
- Movement is server-authoritative with no client prediction: ~1 server round trip of input delay.
- Abilities that need a unit-specific targeting mode (e.g. a building target) fall back to the crosshair block.
- Flyers use the unit's own `travel`; units with unusual movement (slimes, mounts) may handle oddly.
