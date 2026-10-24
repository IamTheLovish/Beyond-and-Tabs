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
- WASD move, mouse look, **Space** jump (flyers: climb), **Shift** (flyers: descend), **Ctrl** or double-tap **W**
  sprint (ground units, section 4c);
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
- **Decisive-kill burst** (Oct 24, "winning pays"): when a **commander** or a **hero** dies, every enemy hero within
  **28 blocks** (`HeroProgression.BURST_RANGE`) gains a one-time extra **400 XP** (commander) or **150 + 50 x the
  victim's level** (hero), straight through `addExperience` (so level 10 stays level 10). It sits on top of the orbs
  above, which are unchanged; own and allied heroes get nothing. Numbers are picks for lovish. The same two deaths also
  leave a big wreck (`WreckServerEvents`: commander 600 metal, hero 150 + 50 x level, never below the normal half-cost
  heap), so the winner can reclaim it and tech - see design/hud.md "Reclaim labels".
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

## 4c. Possession feels like Minecraft (Oct 20 2026)

lovish after playing: "first person mode feels very clunky and hard to control units, doesn't feel like minecraft pov
that allows players to sprint and move around freely". What was wrong, and what changed:
- **No sprint at all.** Now vanilla's rules (`PossessionClientEvents.updateSprint`, after `LocalPlayer.aiStep`): the
  sprint key (Ctrl by default, toggle mode respected) or a double-tap of forward starts it while moving forward;
  letting go of forward, running into a wall, entering water or swinging ends it. Ground units only. Sprint is 1.3x the
  unit's drive speed, applied by one shared `PossessionServer.applyKeys` on the server (`drive`) and in the client's
  prediction, carried as a bit in the existing key flags (no wire change). The unit wears vanilla's sprinting flag
  (`mixin/EntitySharedFlagInvoker`, without vanilla's +30% attribute modifier), so vanilla adds the sprint-jump push
  and sprint dust, seen by everyone. The view widens 15% like a player's (`ViewportEvent.ComputeFov`, Options' FOV
  effects scale). Server check: `walkPerTick(speed, sprinting)` scales by 1.3; a sprint-jump's first tick may also
  carry vanilla's 0.2 push (it rides in the shove allowance, decaying at air drag). Teleports are still refused, and
  a claim now covers at most the server ticks really elapsed since the last one + 2 (`CLAIM_JITTER_TICKS`): before,
  an honest-looking client kept the 10-tick bucket full and could spend it in one jump of 3-4 blocks.
- **Jumps were "thrown", with a third of a player's air control.** A mob steers in the air with 0.02 x its input,
  and a unit's input is MoveControl's keys x speed (~0.3). Off the ground the input is now widened to a player's
  air-to-ground ratio (`AIR_CONTROL`); in the air the unit settles at its own ground pace, never faster.
- **The unit kept its RTS life.** Claim/release called the per-class `resetBehaviours()`, a no-op for most units:
  the last move order, patrol, guard and shift-queue survived and ran during an ability's 3 s AI window and after
  release. Now `dropOrders` does the full reset and clears patrol, guard and the command queue; `Unit.tick` skips the
  anchor retreat, UnitUnstick (a rescue teleport), StuckNudge, Kite and Brace for a possessed unit; a formation order
  queued before possession is not dispatched to it. (Separation, PR #26, needs the same skip when it lands.)
- **Auto-Jump**: if the player has Minecraft's Auto-Jump on, the unit hops a 1-block step it runs into, like a player.
- Checked and fine: held keys are fed every tick (prediction) or on change + heartbeat (fallback), release stops in a
  few ticks (vanilla ground friction, same as a player); the body follows the mouse every frame (no turn lag);
  the predicted copy steps 0.6 like a player (a 1-block step is a jump, or Auto-Jump).

GameTests (`HeroGameTests`): `possession_sprint_covers_more_ground_than_walking`,
`possession_stops_when_the_keys_are_let_go`, `possession_is_not_steered_by_rts_systems`,
`possession_accepts_sprint_claims_and_refuses_a_teleport`.

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

**Slice 4**: hero item slots (RoN `UnitInventory` already exists), a hero shop at the capitol, Deadlock-style currency.

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
