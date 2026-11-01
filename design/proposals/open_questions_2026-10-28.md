---
title: Beyond and Tabs - open questions for lovish (vehicles, vehicle labs, capitols and creep), Oct 28
type: decision-list
project: Beyond and Tabs
status: 1-10 answered Oct 28 (plus Rootbound); capitol size superseded Oct 28 night (6 levels into 59x59, ~100 tall); new questions C1-C3 open
created: 2026-10-28
source: merged from [[vehicles-design-2026-10-28]], [[vehicle-labs-design-2026-10-28]] and [[capitols-extractors-design-2026-10-28]] after the Oct 28 revision pass
---

# Open questions, ordered by how much each blocks building

One line per answer is enough ("5A, 6A, 7B ..."). The recommendation is marked **(rec)**. Numbers 1-4 are kept
as answered below so earlier references stay valid.

## Answered Oct 28 (lovish, via the Command Center coordinator)

1. **Tech 4** = A: a new late research at the capitol after Tier 3, **"Ascension"**, which also opens the faction's
   game ender. (Capitol doc section 1 and 4.1; est. 2500 metal / 25000 energy / 240 s, slot 11.)
2. **Creep placement rule** = A: only **Ashborn and Hivebound** must build on their creep; the other six ooze for
   looks and the small home-ground bonus.
3. **Pre-made maps' home metal** = A: moved out to the same band as generated maps.
4. **Capitol growth space** = the full L4 square reserved from the start, and **"even bigger: push metal further out
   and go larger than 39"**. Applied as: capitol levels **15 / 23 / 31 / 41 wide, 22 / 40 / 60 / 84 tall** (height
   carries the size, per his nine reference images), **home metal 52-76** (was 48-72), no patch within 48 (was 40),
   build ground 50 (was 46), creep capitol radius 44 / 56 / 68 / 80. The earlier cut to 33x33 is withdrawn.
   **Superseded Oct 28 night** by the six-level capitol below.

Earlier answers not repeated: the AI faction (Brassworks keeps steam; a futuristic faction gets tanks later), a
separate vehicle lab per faction, infantry vs buildings at half a vehicle's rate, T2 vehicles by lab refit, four
tech levels, no nether.

5. **Ashborn creep** = A: our own ash blocks (ash soil, cinder rock, ember cracks) with tuff and basalt; End blocks go
   to the alien Hivebound.
6. **Opening with the vehicle lab** = A: yes, it needs only the capitol, like BAR's vehicle plant.
7. **Sunforged and Gravebound lab timing** = A: together with their first vehicle (Dawnrider, Soulfire Wheel); the
   button stays hidden until then.
8. **Tech steps on labs and other buildings** = A: drawn parts, glow and particles, no block changes (the capitol
   still grows in real blocks).
9. **Low flyers vs walls** = A: walls stop them; ranged units and AA hit them.
10. **Rooted Treant** = A: 1.5 s root / 1 s uproot, 30% less damage while fully rooted.

**New: "Rootbound"** stacking effect from the Treant's spike line (lovish's rule: "rooted champs get slowed and damaged
over time, stacking"): 3 stacks max, each hit refreshes the effect to 4 s, 12% slow and 1 magic damage per second per
stack. lovish: keep these numbers.

**Capitol L4** = 41 wide x 84 tall confirmed ("go tall, not wider"). **Superseded Oct 28 night:**

**Capitol: six levels into 59x59, about 100 tall** (lovish, Oct 28 night; Itachi 2's ladder from the White Angel v4
capitol). Levels 21 / 29 / 37 / 45 / 53 / 59 wide, 20 / 30 / 42 / 60 / 80 / ~100 tall (at most 104); the 59x59
square reserved from the start, checked with 7x7 samples at +-29. Capitol levels and tech levels are separate:
L1 / L3 / L4 / L6 come with tech 1 / 2 / 3 / 4 (start, Tier 2, Tier 3, Ascension), L2 and L5 with a capitol-only
"Raise the Capitol" research. The same margins on a 59-wide square give **home patches 65-89, none within 61, build
ground 63** (corner reach 41.0 + 15 + 4.2 = 60.2; +13 on the four-level numbers, not +9, because the corner grows
12.7). Capitol creep 56 / 61 / 67 / 72 / 78 / 84 from its edge by level (reach 66 ... 113). Capitol doc section 4.

## Open: the six-level capitol (Oct 28 night)

C1. **L2 and L5**: (A, rec) a paid capitol research "Raise the Capitol" (slot 12; est. 400m / 2000e / 60 s in tech 1,
    1500m / 15000e / 150 s in tech 3); (B) free after a time at that tech (e.g. 4 min); (C) tie all six to techs some
    other way (say how).
C2. **Maps and seat counts**: starts now need to be 196 apart (was 170); no pre-made map fits 5+ starts (6+ already
    failed with the four-level band), and End Spires / Chorus Isles home islands (radius 40-56) cannot hold the 65-89
    band. (A, rec) widen the start ring for 5-8 seats (`MIN_NEIGHBOUR` 116 -> 196, 8 seats on a ring of 257, about
    2x the area) and give island maps metal islets; (B) cap pre-made maps at 4 starts; (C) accept a tighter band
    (the +9 version: none within 57, 61-85, build ground 59) and lose the corner margin.
C3. **White Angel's crag at L1**: v4 fills the whole 59x59 from L1 (the crag), but the ladder makes L1 21x21. (A, rec)
    re-stage v4 so the crag grows with the levels; (B) let White Angel keep the full crag from L1 (the reserved
    square is empty ground anyway).

## Decided in the revision pass (veto if you disagree)

- The Flak Cart moves to the Brassworks Engine Shed with the other Brassworks vehicles (it stays out of the
  building-bonus list). Say so if you want it to stay in the Machine Shop as anti-air support.
- (Superseded Oct 28 night: the capitol is now 59x59 at L6, about 100 tall, with the metal moved out by 13.)
- Ascension's cost (2500m / 25000e / 240 s), Raise the Capitol's costs and the walk-time note (65-89 is about +3 s
  each way to the first patch against 52-76, +4 s against PR #131's 48-72) are estimates to playtest, not decisions.
- The Hoglin keeps its stats but gets an original body (the Cinder Boar); no vanilla-mob or Reign of Nether
  look-alikes in the vehicle slot.
