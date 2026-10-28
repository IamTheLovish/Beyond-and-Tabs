---
title: Beyond and Tabs - open questions for lovish (vehicles, vehicle labs, capitols and creep), Oct 28
type: decision-list
project: Beyond and Tabs
status: 1-4 answered Oct 28; 5-10 waiting for lovish - pick one option per question
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

Earlier answers not repeated: the AI faction (Brassworks keeps steam; a futuristic faction gets tanks later), a
separate vehicle lab per faction, infantry vs buildings at half a vehicle's rate, T2 vehicles by lab refit, four
tech levels, no nether.

## Still open - blocks the Ashborn creep task (capitol doc build order step 1)

**5. What is the Ashborn creep made of?** (You said "end blocks or something else".)
- A) our own ash blocks (ash soil, cinder rock, ember cracks) with tuff and basalt; End blocks go to the alien Hivebound **(rec)**
- B) End blocks (end stone, purpur) for the Ashborn too
- C) vanilla only (tuff, coarse dirt, gravel, deepslate)

## Still open - blocks the first vehicle lab (lab doc build order step 1)

**6. Can a player open with the vehicle lab?**
- A) yes, it needs only the capitol, like BAR's vehicle plant **(rec)**
- B) no, the infantry lab must be built first

**7. Sunforged and Gravebound have no vehicles yet. When do they get their lab?**
- A) together with their first vehicle (Dawnrider, Soulfire Wheel); the button stays hidden until then **(rec)**
- B) now, empty, so every faction has the building from the first PR

**8. Tech steps on labs and other buildings: drawn or real blocks?** (The capitol grows in real blocks either way.)
- A) drawn parts, glow and particles on every building at once, no block changes **(rec)** - no pathing or HP change, no 24 structure files per faction
- B) real blocks: every lab swaps to a richer structure at each tech level

## Still open - blocks single vehicles (can be decided when each is built)

**9. Can low-flying vehicles (Dawnrider, Gale Kite, Manta Ferry, Cloud Barge) cross walls?**
- A) no, walls stop them; ranged units and AA hit them **(rec)** - walls stay the answer to raids
- B) yes, they fly over; ranged and AA hit them
- C) yes, and they count as true air (only AA and hits-air units can hit them)

**10. The Rooted Treant: how long to root / uproot, and does it take 30% less damage while rooted?**
- A) 1.5 s / 1 s and 30% less damage **(rec)** - committed enough to telegraph, quick enough to reposition
- B) 1 s / 0.5 s and 20% less damage (snappy)
- C) 2.5 s / 2 s and 40% less damage (a real deploy, like the Sunlens Engine)

## Decided in the revision pass (veto if you disagree)

- The Flak Cart moves to the Brassworks Engine Shed with the other Brassworks vehicles (it stays out of the
  building-bonus list). Say so if you want it to stay in the Machine Shop as anti-air support.
- The capitol L4 is 41x41x84 (just past his "larger than 39", with the size going into height); if he wants it
  wider still, every extra block of width costs a block of lab room on each side, so the recommendation is to go
  taller instead.
- Ascension's cost (2500m / 25000e / 240 s) and the walk-time note (+1 s each way to the first patch) are
  estimates to playtest, not decisions.
- The Hoglin keeps its stats but gets an original body (the Cinder Boar); no vanilla-mob or Reign of Nether
  look-alikes in the vehicle slot.
