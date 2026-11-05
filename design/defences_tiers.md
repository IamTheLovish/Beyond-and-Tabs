# Defences by tier - the ladder, its counters and each defence's weakness

lovish, Nov 5 (the Defenses lane): "I want defenses to be less spamable and more powerful as well as intricate"; "we
can have the basic weaker anti raiding tower but I would like each tech to have better and better defesnes with tier 4
having experimental defenses that are different for each faction". On how to keep them in check: "defenses should be
able to beat units of the same pricing because they dont move ... if we make defesnses overpowered people will do a
tactic called creep walking ... So we need counter units that can deal with these threats"; "I want defenses to fit
their niche and be able to stop powerful units, but they arent all powerful and certain weaknesses of each defense can
be exploted through the use of good decisoin making and gameplay". No tier-armour multiplier: "higher tier troops
should have more hp and stronger stats that should stat check defenses".

This builds on design/defences.md (today's set) and design/siege_t1.md (the T1 siege lobber).

## The three rules

1. **Price parity.** A defence beats units of the same price in its own spot; that is what standing still buys. It
   never beats more than that (the T1 test `defence_anti_raid_tower_out_trades_its_cost_vs_raiders` is the pattern).
2. **A counter at every stage.** For every tower of tier N, every faction has a unit of tier N or below that can hurt
   it from where it cannot answer: more reach than its range (the lobber's 15 against the anti-raid tower's 14, BAR's
   Sheldon against a T1 gauntlet), or a flyer against a ground-only tower. Overbuilding defences loses to the player
   who spent the same on tech and economy. `gametest/DefenceCounterGameTests` reads every faction's units off the code
   and fails on a gap.
3. **Every defence has a designed weakness** a good player can exploit (BAR's Pulsar: a huge beam on a cooldown, eaten
   by T1 chaff while the T3s walk in). It is part of the design, written in the tables below, and it never gets
   patched away by a later balance pass without lovish.

## The ladder (shared roles; every faction's own look and mechanic)

| Tier | Gate | Roles | Counter at this stage |
|---|---|---|---|
| T1 | capitol | basic anti-raid tower (weak on purpose), T1 anti-air, walls, traps | T1 siege lobber (out-ranges the tower by 1), any ground raid vs anti-air |
| T2 | Tier 2 + T2 worker | today's enhanced anti-raid, enhanced anti-air and anti-siege tower move up here, one mechanic each; a T1 tower can be refit in place | a T2 artillery that out-ranges r16-20 (r22-24), flyers vs ground-only towers, chaff vs cooldown weapons |
| T3 | Tier 3 | the faction's fortress line (long-range, heavy, branch towers), anti-T3 emplacement | T3 long siege (r30+), experimentals out-stat them, swarms vs single-target beams |
| T4 | Ascension | one experimental defence per faction, unique | game enders, the ascended commander, mass T3 baiting the big cooldown |

## Gravebound (the pilot set)

| Tier | Defence | Role | Mechanic | Weakness (designed) |
|---|---|---|---|---|
| T1 | Gargoyle | anti-raid | a dormant statue takes half damage until it wakes; wakes at r12 and spits bone shards | the lobber out-ranges it (15 vs 14); short sight, so a spotted gargoyle is shelled from the dark |
| T1 | Bone Ballista | anti-air | black-steel-tipped shafts that pierce a line (two flyers) | slow reload: a spread flock beats it; never shoots ground |
| T1-T2 | Graveyard Shrine | support aura, no weapon | blight creeps out slowly (r4 to r14 over about 3 min, the FactionAura creep), own undead on it regenerate and attack 10% faster, enemies on it heal at half rate | no weapon at all; a new shrine is nearly useless for its first minute; the blight recedes when it falls |
| T2 | Gargoyle upgrade 1: Eye of the Gargoyle | anti-heavy | green eye-laser that ramps from x1 to x2.5 over 4 s on one target | ramp resets on every new target: chaff in front of the heavies wastes it |
| T3 | Gargoyle upgrade 2: Waking Gargoyle (stacked on the eye) | mobile defender | tears off its plinth and fights within r10 of it, returns to perch to heal | leashed: kite it past r10 and it turns back; off the perch it loses the stone skin |
| T2 | Death-Mist Mage | flak anti-air | mist bursts that hang in the air 3 s, damage over time to every flyer in them | slow bursts: fast single flyers fly through; never shoots ground |
| T2 | Cauldron Catalyst | air + ground caster | the player picks the brew: Plague (poison pool), Grave Frost (slow), Soul Burn (anti-air bolt) | one brew at a time, 5 s to switch: scout the brew and send what it is bad against |
| T3 | Eye Monolith | long-range air + ground | r28 sweeping eye beam after a 1.5 s charge | dead zone under r6 (melee divers), the charge telegraphs the shot |
| T3 | Giant Tower (one building, branch picked on build) | heavy | Death: kills near it raise skeletons (cap 6). Vampire: its beam heals own buildings and units by the damage dealt. Zombie: what it kills rises as your zombie (cap 6). Banshee: a wail every 20 s makes enemies within r8 flee for 2 s | Death and Zombie are slow single shots (swarms); Vampire heals nothing without a target (siege it from range); Banshee does little damage (heavies tank the wail) |
| T4 | Death Fortress (one per player) | experimental | death aura r6: enemy T1-T2 units inside die (one pulse every 2 s); death-rune artillery to r40; Death Ray kills any single unit, T4 included, every 90 s after a 3 s visible rune telegraph | the ray's 90 s cooldown and telegraph (bait it with one unit, then commit); the r6-r12 ring is inside the artillery's minimum range and outside the aura |

### Gravebound's T2 counter unit: the Soul Pyre

The counter audit found Gravebound the one faction with no unit that out-ranges its T1-lab / T2 towers (its longest
building-hitting reach was the Skull Hurler lobber's 15, against the Heavy anti-raid tower's 16 and the Siege Guard's
20). lovish (Nov 5): "we can do moving massive flame pit that conjurs magic and souls that can be a tier 2 seige
unit". Trained at the T2 lab: range 22, minimum range 6, sight 12 (needs a spotter); a conjured soul lobbed every
~4.5 s, strong on buildings, weak on units, never at air; slow, sets up for ~1.5 s after moving. Weakness (designed):
slow, blind inside 6, the soul rises ~1 s before the throw, and it shoots the nearest target, so chaff eats its shots.
Its body, animations and effects come from the animation lane; the mechanics do not wait on them.

lovish's picks (Nov 5): gargoyle upgrades stacked (eye at T2, then the mobile statue at T3); Death Ray every 90 s with
a 3 s telegraph; the Giant Tower is one building with a branch picked on build (Banshee added). Today's Gravebound
towers map in: Soulfire Spire becomes the Gargoyle, Bone Harpoon Spire the Bone Ballista, Bat Roost the Death-Mist
Mage; the Grave Ballista stays the anti-T3 emplacement.

## Build order (one PR each, tests first)

A. This ladder, the counter audit, and each faction's missing stage counter (Gravebound: the Soul Pyre).
B. Gravebound T1: Gargoyle (dormant stone skin), Bone Ballista (pierce), Graveyard Shrine on FactionAura.
C. Gravebound T2: the eye-laser upgrade, Death-Mist Mage, Cauldron Catalyst; today's enhanced towers move to T2.
D. Gravebound T3: Eye Monolith, Giant Tower branches, the waking gargoyle.
E. Gravebound T4: Death Fortress behind Ascension.
Then the other factions on the same ladder with their own mechanic. The T1 siege lobber's per-faction model, animation
and particles belong to the art lane (mechanics stay shared).
