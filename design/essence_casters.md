# Essence casters

lovish's ask (Oct 29 2026): casters (mage, necromancer, healer, voodoo) that consume something left on the ground after
fights, a different effect per race, **floating essence** instead of metal wrecks, and no BAR-style rez bots.
Approved as draft 2 (~05:45Z): "Approved, start the build: essence wisps and Empower first, then one caster per race."
Names are placeholders; numbers are tuned later.

## What already existed (main f1a9ba5e, `bar/`)
- Wrecks: units costing 30+ metal leave a wreck worth 50% of their metal (`resources/WreckServerEvents.java`); workers
  reclaim them. Gravebound already eats wrecks (Lich Regent Raise Dead, Ossuary Behemoth, Necropolis). Essence stays
  separate from wrecks, so the economy is untouched.
- XP orbs: on a death, orbs fly to enemy heroes within 20 blocks (`hero/HeroExperienceOrb`). Veterancy: kills give
  ranks (+10% HP and damage per rank, max 3; `unit/VeterancyServerEvents`).
- Casters per race already exist (Witch, Evoker, Necromancer hero, Wraith, Bloom Priestess, Tide Priest, Frost Adept,
  Ink Scribe, Shield Projector...). Abilities use energy as mana; `SupportBrain` runs healer AI; `BarFx` has
  dome/beam/shot/impact effects.

## Essence
- Every unit that dies (either side) drops a floating essence wisp: 1 essence per 25 metal of its cost, at least 1
  (a 50-metal villager 2, a 95-metal vindicator 3, a 300-metal T2 12). Summoned and temporary units drop none, so
  summons cannot farm essence: risen, awakened treants, decoys, summoned zombies and bees, every hero summon (Raise the
  Fallen's skeletons included), deployed turrets, Iron Leviathan Engine drones, an infested building's limited-life
  silverfish, and later the casters' own summons. Free units that are not temporary (capture-camp wolves, bought
  totems) do drop: lovish's call if that should change.
- Wisps float in place for 45 s, then fade. At most 200 on the map (the oldest go first).
- A caster gathers the wisps within 8 blocks automatically (they fly to it) and holds up to 30 essence. A full caster
  leaves wisps where they float; a wisp worth more than the room left keeps the rest. A carrier's essence is lost
  when it dies (it is not dropped again as a wisp).

## lovish's answers (~05:25Z)
New caster per race (7, T2 lab); essence drops on every death (both sides); each caster has 3 distinct abilities;
essence can also buy veterancy XP.

## Abilities, draft 2 (cheap 5 / mid 10 / big 20 essence; cooldowns 12 / 25 / 60 s)
Sunforged was respecified by lovish (Oct 29): "the angels casters can summon three different angels that help: one that heals, one that is a flying archer and another that is a melee engager that can fly". One angel of each kind per caster: a recast while it lives is refused (nothing spent, no cooldown).

Shared by every caster: **Empower** (15 essence): an ally unit within 6 gains 1 veterancy XP (one rank, +10% HP and
damage, max rank 3).

| Race | Caster | Cheap (5) | Mid (10) | Big (20) |
|---|---|---|---|---|
| Sunforged | Angel Caller | Mender Angel: a flying healer for 20 s (5 HP every 2 s to a hurt friend within 8) | Seraph Archer: a flying archer for 25 s (6 dmg, 0.6/s, range 12) | Sword Seraph: a flying melee engager for 30 s (70 HP, 9 dmg, 0.8/s) |
| Gravebound | Soulbinder | Wail: enemies in a cone flee for 2 s | Soul Harvest: 2 spectral skeletons for 20 s | Soul Chains: up to 4 enemies linked 10 s, 50% of damage to one is shared to the others |
| Horde | Voodoo Shaman | Hex: one enemy (T1-T2) cannot attack or cast for 4 s | Blood Totem: 15 s, allies within 8 +25% attack speed, enemies -15% speed | Spirit Puppet: take control of one enemy T1 unit for 15 s |
| Verdant | Thornweaver | Entangling Roots: enemies within 3 rooted 3 s | Seed of Renewal: sapling heals allies within 6 for 6 HP/s, 12 s | Sprout Guardian: a sapling treant fights for you 25 s |
| Tidewrought | Tide Seer | Brine Bolt: 50 damage + knockback | Drowned Tithe: whirlpool radius 5, 8 s, enemies -40% speed and 8 damage/s | Rising Tide: wave pushes enemies 6 blocks back and soaks fire |
| Stormcallers | Spark Sage | Static Step: blink 8 blocks | Captured Spark: chain lightning 120 damage, 4 jumps | Thunderhead: storm cloud 10 s, strikes an enemy every second for 40 |
| Brassworks | Soul Engineer | Patch Kit: repair a machine or building 120 HP | Soul Boiler: a building +50% build/production speed, 20 s | Ghost Furnace: spirit-steam turret for 20 s |

## Build order
1. **PR 1 (this one):** essence wisps (drop, float 45 s, cap 200), carriers (gather within 8, hold 30, saved with the
   unit) and Empower, test-first (`gametest/EssenceGameTests`).
2. One PR per race caster: the unit, its T2 lab button, its 3 abilities, autocast rules and tests, built on the API
   below.

## Revision 1 (Oct 29 design review; base fixes in the PR stacked on PR 1, branch claude/essence-base-r1)
The design review of Oct 29 (Fable, ~07:50Z) found these base-level holes; the caster-specific findings go to the caster PRs.
- **Summons give nothing.** `unit/Summons` is the one rule (`Summons.mark` / `isSummoned`; `Essence.markSummoned`
  delegates): a summon's death drops no essence wisp, sends no hero XP orbs and gives its killer no veterancy
  experience. Existing markers (`bt_risen`, awakened, decoys, summoned zombies and bees, turrets, drones) are read, not
  replaced, since `bt_risen` also means no wreck and an 8-per-owner cap. A fallen summoned hero still gives the
  decisive-kill XP burst. Wrecks keep their own older list.
- **Empower is limited.** 20 s cooldown; one rank per target per 60 s (`bt_empowered_at` on the target); never on a
  hero, commander, experimental (`DefenceTower.isExperimental`) or summon. `Empower.setTargetHook(type, hook)` replaces
  the rank for one unit type and its own `accepts` replaces those refusals - the Grave Adept's 120 XP in its PR.
- **A dead carrier drops what it held** as a second wisp (a summoned carrier drops nothing).
- **Bots:** `Ability.autocastEssence` (the AutoCaster casts only when the caster holds it) and `AutoRule.BUILDING_HURT`
  (the most hurt friendly building under 70% health whose footprint is within reach).
- Not in this base PR (they belong to the caster PRs): the damage and heal numbers, the renames, Spirit Puppet's
  channel, the totem and sapling HP, lobbers vs tombstones, Soul Boiler's 24-block reach.

## The API for the caster PRs (PR 1)
`com.solegendary.reignofnether.essence`:
- `EssenceCarrier` - marker interface: a caster unit class implements it and gathers/holds essence; no other wiring.
  `Essence.setCarrier(entity, true)` flags any other unit at run time (a tag, saved with it).
- `Essence.get(unit)`, `Essence.room(unit)`, `Essence.CAPACITY` (30).
- `Essence.trySpend(unit, n)` - true and spent, or false with nothing changed (short of essence or not a carrier).
  Check every other condition of a cast first, spend last, so a refusal never costs essence.
- `Essence.add(unit, n)` - adds up to the room left and returns what it took (0 for a non-carrier).
- `Essence.markSummoned(entity)` - call on every summon a caster makes (Soul Harvest's skeletons, Sprout Guardian,
  Ghost Furnace...) so its death drops no wisp; `Essence.isSummoned(entity)` is the full rule.
- `EssenceServerEvents.spawnWisp(level, x, y, z, value)`, `getWisps()`, `valueOf(wisp)` - for effects that make or
  read essence directly.
- `ability/abilities/Empower` - add `new Empower()` to every caster's abilities; `Empower.cast(level, caster, owner,
  target, quiet)` is the server rule (bots and tests).

Storage: a carrier's essence is an int in its persistent data (`bt_essence`), like veterancy XP, so it is saved with
the unit and needs no packet of its own; a caster PR that shows essence on the HUD adds the sync it needs. A wisp is a
vanilla block display (like a wreck) carrying its value and birth tick in its persistent data, saved with the world.
