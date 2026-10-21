# T3 rework - progress

Spec: the project doc `claude/t3-roles.md` (lovish, Oct 21: "every tier 3 unit I used was extremely slow and almost
useless", "dont let weaknesses ruin unit design"; BAR's Marauder as the flanker model). Owner's decisions:
`design/LANES.md` (Oct 21). Order of work: 1. feel fixes for every big unit, 2. the War Mammoth as the flanker with its
strategy tests, 3. the other six roles, one PR each.

## PR 1 - big-unit feel fixes and the Mammoth flanker (branch `claude/t3-feel-mammoth`)

### Done
- **Never stuck (gaps)** - `pathfinding/GridNeighbors.footprintBox`: the RTS grid used to path every body wider than a
  block as 2x2 (node + one clear quadrant), so a 3.3-wide War Mammoth was routed through 2-wide gaps between trees and
  buildings it could not fit, and ground on them. A body of footprint radius r now needs an (r+1)x(r+1) box of
  standable cells round its node (vanilla's floor(width+1)): 3x3 for the elephant / turtle / rhino / bison, 4x4 for the
  Mammoth, Colossus, Walker, Leviathan and Engine. `PathConverter` sits the body inside the box the pathfinder found
  (clamped as near the cell centre as its width allows) instead of a fixed half-cell quadrant offset. 2x2 bodies are
  untouched. Vanilla chase paths already did this (WalkNodeEvaluator's entityWidth box); only orders through the RTS
  grid were wrong. Test: `BigUnitFeelGameTests.a_body_wider_than_a_gap_paths_round_it` (GridAStar on a real pad).
- **Mass / right of way** - `unit/BigUnits`: a heavy body (2+ wide, knockback-proof, walks) is never pushed by the
  bodies it overlaps (`LivingEntityMixin.isPushable`); a crowd of T1 bodies pushed a walking titan back 0.05 a contact a
  tick before. A moving heavy body **ploughs**: the small bodies its front overlaps are shoved to the side (friend or
  foe), so it walks through a line instead of wading through it. Two standing heavy bodies still step apart. Test:
  `a_heavy_body_has_right_of_way_and_ploughs_through_a_line`.
- **Marches with the army** - War Mammoth 0.17 -> 0.30 (its sheet lied: `RavagerUnit.aiStep` reset every ravager body
  to the Siege Ox's 0.25 each tick; `designedMovementSpeed()` now lets a subclass own its speed), Sun Colossus
  0.16 -> 0.25 (the Sunforged line's pace). Test: `ground_t3s_march_with_their_army`.
- **Responsive** - the Mammoth bites on the move (`isImmobile` without the ravager's 10-tick freeze after each attack).
- **Hits land on groups** - the Mammoth's cleave: half of each blow on every enemy within 3 blocks of the target, with
  a dust ring (`BarFx.heavyImpact`) the RTS camera reads; the Colossus and Walker already had theirs.
- **The Mammoth as the flanker** - Trample is now an aimed charge (`WarMammothUnit.startCharge` / `tickCharge`,
  `ability/abilities/Trample`): 8-tick wind-up with the lane drawn, then 16 blocks at 1 block a tick, 20 damage and a
  fling to every enemy it runs through, a wall or building stops it (no building damage - the Rhino breaks gates, the
  wall line is the Mammoth's counter). 30 s cooldown (was a 60 s instant lunge). Autocast CROWD (3+ enemies clumped in
  the lane's reach). Strategy tests (`MammothFlankerGameTests`): the charge itself; a flank past a thin Brute front
  that razes the wind mill behind it with the front still standing; the same Mammoth into a fortified wall line with
  three anti-T3 emplacements behind it (cheaper than the Mammoth) dies without the mill taking a hit.

### Numbers changed (every one)
| What | Was | Now | Why |
|---|---|---|---|
| War Mammoth movementSpeed | 0.17 (played at 0.25) | 0.30 | flanker: the Horde raiders' pace, ahead of every line |
| Sun Colossus movementSpeed | 0.16 | 0.25 | marches with its line (its role pass comes later) |
| Trample cooldown | 60 s | 30 s | once per engagement: in, through, out |
| Trample shape | instant lane 10 x 5, 20 dmg, +speed burst | aimed charge 16 long, body + 1.2 each side, 20 dmg, 8-tick wind-up | runs through a group, telegraphed |
| Mammoth cleave | none | 50% within 3.0 | big swings hit everything round it |
| Mammoth bite freeze | 10 ticks (vanilla ravager) | none | responsive |
| Heavy-body plough | none | 0.35 sideways every 2 ticks while moving | mass |

### Not done (honest list)
- A melee attack wind-up with a ground telegraph for the T3 titans: not without eyes on the animation; the charge has
  one, the swings land as before.
- A capped turn rate for big bodies ("a turn rate that fits its size"): a feel change nobody can judge headless.
- The Leviathan's land speed (0.12): tied to its x2.5 wet bonus; its amphibious-flanker pass sets both.
- The other six roles (Bone Dragon, Leviathan, Celestial Dragon, Sun Colossus, World Tree Walker, Engine): one PR each.
- The Mammoth's archer platform crew and fire panic (design-factions.md) are still open.
- The Rhino's Breach Charge lands its 16 damage through `mobAttack`, which `LivingEntityMixin.actuallyHurt` rewrites to
  the rhino's 10 attack damage - the Mammoth's charge uses `UnitDamage.hurtExplicit` instead; the rhino should too
  (its own PR, a T2 number).

### Open questions for lovish
- Should friendly bodies be ploughed too (today yes: the Mammoth has right of way through its own line)?
- Autocast on the charge: a Mammoth charges a clump by itself - fine for a flanker in the open, risky under towers.
  Off by right click as every ability; should it default off for T3 abilities?
