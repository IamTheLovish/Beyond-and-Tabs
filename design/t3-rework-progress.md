# T3 rework - progress

Spec: the project doc `claude/t3-roles.md` (lovish, Oct 21: "every tier 3 unit I used was extremely slow and almost
useless", "dont let weaknesses ruin unit design"; BAR's Marauder as the flanker model). Owner's decisions:
`design/LANES.md` (Oct 21). Two sessions built the same thing in parallel - #81 (`claude/t3-roles`: the seven roles)
and #87 (`claude/t3-feel-mammoth`: feel fixes and the Mammoth flanker); #87 was folded into #81 on Oct 21, this note
is the merged state. The PR body of #81 carries the numbers table and the honest scores.

## Done (PR #81)

- **Every T3 has a job and a moment** (`unit/units/*`): Mammoth = flanker (aimed Trample charge, cleave, bites on the
  move, resumes its order after a charge); Sun Colossus = anchor (Dawn Aegis x0.8 for friends within 7, marches with
  its line); Bone Dragon = air striker (soul-breath cone, x1.3 at night, fireball kept for buildings); World Tree
  Walker = summoner (Grow Grove: 2 treants a cast, 4 standing, 45 s, bloom heals 12); Leviathan = amphibious flanker
  (Deep Dive: 28 blocks under the water, surfaces with a Tidal Slam); Celestial Dragon = air caster (the bolt chains to
  3 within 5 for half, 15 energy a bolt); Iron Leviathan Engine = siege artillery (range 32, lobbed, sight 24).
- **Never stuck (gaps)** - `pathfinding/GridNeighbors.footprintBox` (from #87): the RTS grid pathed every body wider
  than a block as 2x2, so a 3.3-wide Mammoth was routed through 2-wide gaps it could not fit and ground on them. A body
  of footprint radius r needs an (r+1)x(r+1) box of standable cells (vanilla's floor(width+1)): 3x3 for the elephant,
  turtle, rhino and bison, 4x4 for the five ground T3s. `PathConverter` sits the body inside the box the pathfinder
  found. 2x2 bodies untouched. Test: `BigUnitFeelGameTests.a_body_wider_than_a_gap_paths_round_it`.
- **Mass / right of way** - `unit/TitanMass`: a ground T3 is never pushed by other bodies and shoves bodies narrower
  than 60% of its width aside (0.12 a tick, sideways off its heading) in its own push pass; never into lava or off a
  ledge; two standing titans on one spot step apart (`TitanMass.tick`). Tests: `T3RoleGameTests.t3_titans_have_the_
  right_of_way` (the rule), `BigUnitFeelGameTests.a_heavy_body_has_right_of_way_and_ploughs_through_a_line` (a Mammoth
  through a held Brute line, end to end).
- **Marches with the army** - Mammoth 0.30 (the Horde raiders' pace; it really walked at 0.25 before: `RavagerUnit.
  aiStep` reset every ravager body's speed each tick - `designedMovementSpeed()` lets a subclass own it), Colossus 0.25
  (the Sunforged line's), Leviathan 0.22 on land, Bone Dragon 0.30; the Walker (0.18) and the Engine (0.15) stay back by
  role.
- **Hits land on groups**: cleave on the Mammoth, Colossus, Walker and Leviathan; the breath cone; the chain; the
  Engine's splash shell.

## Not done (honest list)

- The strategy scenarios of section 7 as bot-vs-bot tests. #87 wrote two for the Mammoth (a flank past a thin front
  that razes a mill; a wall line with anti-T3 emplacements that kills it) - the flank one was red on #87's CI: the
  Mammoth dropped its building order to fight the front (the retaliation / forced-order rules of AttackerUnit.tick, a
  general question, not the charge's). They stay out until that is settled; the mechanics are tested per unit.
- A melee attack wind-up with a ground telegraph for the titans' swings; a turn rate that fits a big body: feel changes
  nobody can judge headless - after eyes clips of each T3 taking orders.
- T2 beasts (elephant, turtle, rhino, bison) have the pathing fix but not the right of way - their own pass, with the
  Rhino's Breach Charge landing its 16 through `mobAttack` (rewritten to its 10 by `LivingEntityMixin.actuallyHurt`;
  the Mammoth's charge uses `UnitDamage.hurtExplicit`).
- The Mammoth's archer platform crew and fire panic; vision-gated artillery targeting (a spotter) for every artillery
  piece; the commander check of section 6.

## Open questions for lovish

- Friendly bodies are shoved aside too (an army parts for its titan) - keep?
- Autocast on the charge: a Mammoth charges a clump (3+ enemies within 4 of the nearest in reach) by itself and then
  resumes its order - fine in the open, risky under towers. Off by right click as every ability; default off for T3
  abilities?
