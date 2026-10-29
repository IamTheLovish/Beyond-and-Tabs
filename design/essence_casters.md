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
Shared by every caster: **Empower** (15 essence): an ally unit within 6 gains 1 veterancy XP (one rank, +10% HP and
damage, max rank 3).

| Race | Caster | Cheap (5) | Mid (10) | Big (20) |
|---|---|---|---|---|
| Sunforged | Sun Cantor | Dawn Lance: beam, 60 damage (x2 vs undead) | Radiant Tithe: allies within 6 get an 80-damage golden shield, 10 s | Solar Ascension: 8 s, allies within 8 regen 5 HP/s and +15% damage |
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

## Horde: Voodoo Shaman (caster PR, branch claude/caster-horde)
`unit/units/piglins/VoodooShamanUnit`, trained at the Flame Sanctuary (the Horde's T2 lab, button 10 / P, as the
Celestial Observatory's tenth). Placeholder body: the Brute's piglin plumbing with the Spear Thrower's skin and a bone
fetish for a staff (3 damage, 0.6 hits/s).
- **Cost and health (this PR's pick, for lovish to tune):** 175 metal / 220 energy, 3 pop, 35 s (the Bloom Priestess's
  price: a T2 support caster sits between the Blaze, 135 / 90, and the Wither Skeleton, 245 / 160); 60 HP, between the
  Ghast (50) and the Wither Skeleton (100), sturdier than the other races' casters (40-45) as Horde units are.
- **Hex** (5, 12 s, reach 8): DISARM + SEALED for 4 s - no attacks (the attack cooldown never runs out, and
  VoodooServerEvents cancels any blow a hexed unit still lands) and no abilities (every ability gate already refuses
  a sealed unit). Tier 1-2 only; refused inside the target's own Ward, as Seal is. Autocast on the nearest valid enemy.
  Not Seal: the Ink Scribe's Seal (25 energy, 20 s, reach 10) silences any enemy for 6 s and it still fights; Hex
  also stops attacks, for 4 s, on T1-T2 only, and is paid in essence.
- **Blood Totem** (10, 25 s, planted within 8): a 100 HP totem, no resists, that enemies target like any unit
  (AbstractTotem body; Revision 1) for 15 s; every half second units
  within 8 get a 1 s effect - allies BLOOD_FRENZY (x1.25 attacks per second, in AttackerUnit's attack cooldown like
  Bloodlust), anyone else BLOOD_TOTEM_SLOW (-15% move speed). Marked summoned. Autocast while fighting, at its feet.
- **Spirit Puppet** (20, 60 s, reach 8): an enemy T1 unit is the caster's owner's for 15 s (`SpiritPuppets`). A
  channel (Revision 1): it goes back at once if the shaman is hit, stunned or frozen, dies, or is more than 16 blocks
  from it. The original owner, the hand-back tick and the shaman live in the puppet's persistent data, so a save cannot keep it; each hand-over
  drops its orders and makes its new side's attackers let go (the Wraith's Possess hand-over), the server refuses the
  other side's orders by owner, and the client drops it from any selection when its owner flips
  (`SelectionKeep.ownerFlipped`). Not a summon: a puppet that dies drops its wisp; its record goes with it. Manual.
- **Tiers** (`unit/UnitTier`): by where a unit is trained - a T2 lab's unit is T2, the faction's T3 building's (or the
  bot kit's T3 unit) is T3, any other trained unit T1; heroes and commanders count as T3; an untrained unit falls back
  to its cost (BarFx.tierOf: T2 from 120 metal, T3 from 500).
- Every refusal (bad target, wrong tier, out of reach, short of essence) spends nothing and sets no cooldown.
- Not in this PR: the bots do not train it yet, and its essence is not on the HUD yet.
