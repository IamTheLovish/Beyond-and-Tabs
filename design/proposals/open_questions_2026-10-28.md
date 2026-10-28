---
title: Beyond and Tabs - open questions for lovish (vehicles, vehicle labs, capitols and creep), Oct 28
type: decision-list
project: Beyond and Tabs
status: waiting for lovish - pick one option per question
created: 2026-10-28
source: merged from [[vehicles-design-2026-10-28]], [[vehicle-labs-design-2026-10-28]] and [[capitols-extractors-design-2026-10-28]] after the Oct 28 revision pass
---

# Open questions, ordered by how much each blocks building

One line per answer is enough ("1A, 2A, 3C ..."). The recommendation is marked **(rec)**. Questions lovish has
already answered (the AI faction, where vehicles are built, infantry vs buildings at half a vehicle's rate, T2
vehicles by lab refit, four tech levels, no nether) are not repeated here.

## Blocks the creep and the capitol levels (capitol doc build order steps 1-2)

**1. What is "tech 4"?** It drives the capitol's fourth level, the fourth creep radius and the fourth tech step on
every lab. Nothing above tech 3 exists in main.
- A) a new late research at the capitol after Tier 3 ("Ascension"), which also opens the faction's game ender **(rec)**
- B) no new research: L1 the start, L2 the first lab built, L3 Tier 2, L4 Tier 3
- C) building the faction's first Tier 3 experimental

**2. Which races must build on their creep?** (The others still ooze for looks and the small home-ground bonus.)
- A) only Ashborn and the Hivebound **(rec)** - the spreading races; the rest build anywhere
- B) every race
- C) none; creep is looks and bonus for everyone

**3. Pre-made maps put home metal 11-24 blocks from the start, so a growing capitol cannot fit there.**
(Generated maps are fine: 7 patches at 48-72, as you answered.)
- A) move pre-made home metal out to 48-72 like the generated maps **(rec)**
- B) on pre-made maps the capitol grows only upward (stays 15x15)
- C) cap growth at L2 (21x21) there and move only the closest patches

**4. How is the capitol's growth space kept free?** (The revised L4 box is 33x33, a 9-block ring round the L1
capitol; the old 39x39 was cut because it left too little room for the 11x13 vehicle lab - please look at that in the
capitol doc's revision notes.)
- A) the full L4 square (33x33) is reserved from the start, shown as a faint outline **(rec)**
- B) only the next level's square is reserved, when its tech starts
- C) nothing reserved; the capitol grows round whatever stands there

**5. What is the Ashborn creep made of?** (You said "end blocks or something else".)
- A) our own ash blocks (ash soil, cinder rock, ember cracks) with tuff and basalt; End blocks go to the alien Hivebound **(rec)**
- B) End blocks (end stone, purpur) for the Ashborn too
- C) vanilla only (tuff, coarse dirt, gravel, deepslate)

## Blocks the first vehicle lab (lab doc build order step 1)

**6. Can a player open with the vehicle lab?**
- A) yes, it needs only the capitol, like BAR's vehicle plant **(rec)**
- B) no, the infantry lab must be built first

**7. Sunforged and Gravebound have no vehicles yet. When do they get their lab?**
- A) together with their first vehicle (Dawnrider, Soulfire Wheel); the button stays hidden until then **(rec)**
- B) now, empty, so every faction has the building from the first PR

**8. Tech steps on labs and other buildings: drawn or real blocks?** (The capitol grows in real blocks either way.)
- A) drawn parts, glow and particles on every building at once, no block changes **(rec)** - no pathing or HP change, no 24 structure files per faction
- B) real blocks: every lab swaps to a richer structure at each tech level

## Blocks single vehicles (can be decided when each is built)

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
- The capitol L4 box is 33x33x56, not 39x39x64 (question 4 above).
- The Hoglin keeps its stats but gets an original body (the Cinder Boar); no vanilla-mob or Reign of Nether
  look-alikes in the vehicle slot.
