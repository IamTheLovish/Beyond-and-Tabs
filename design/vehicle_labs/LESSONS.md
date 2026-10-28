# Vehicle lab concepts - what went wrong (Oct 28, after rounds 1-2)

lovish, 04:09-04:11: "disappointing we used a lot of usage for little progress", "we really need to learn from this,
I also sent reference art". Seven labs, two rounds, scores stuck at 3-4/10. Round 2 is cleaner (Brassworks, Sunforged,
Tidewrought now read as buildings) but none of them looks like ANY of his 26 references.

## Why (most important first)

1. **We blended references instead of imitating one.** The White Angel v4 capitol worked because it imitated ONE
   picture closely (its script studies the picture region by region). The labs were told to take "principles" from
   6-10 refs each plus a long rule list, and came out as a generic average that resembles nothing. Lesson: one primary
   reference per building, imitated as closely as blocks allow; other refs only for specific parts.
2. **Proportions were wrong for the references we chose.** His refs are tall and vertical (gothic fronts, towers,
   castles on crags). A 17 x 17 base under a 20-block cap makes a squat box, and no amount of surface detail turns a
   squat box into a cathedral. Lesson: pick a primary reference whose proportions fit the plot, or shape the plot to the
   reference (narrow, tall main mass + an open yard), before any detailing. Several refs are lab-scale already:
   ref11 (small mossy tower), ref12 (Victorian house), ref15 (lit spired mansion), ref17 (gallery towers), ref19 (vine
   tower), ref21 (gothic tower with bartizans), ref27 (armillary tower).
3. **We judged at the wrong size.** Every render was at 18 px/block; tools/structure_render.py says 24-36 is the game's
   RTS zoom. Builders sculpted figures and critics judged gates they could barely see. Lesson: --px 32 always, plus a
   close crop of any sculpted part, and the reference placed BESIDE the render at a similar size.
4. **Open briefs, then a growing rule list.** "Make a landmark" produced pillars twice; the amendment grew to 36 items,
   so builders spent their effort satisfying rules instead of matching a picture. Lesson: the art director writes a
   concrete massing plan (tiers, masses, gate, landmark, with coordinates) from the chosen reference BEFORE building; the
   standing rules stay short (lane, spawn column, pockets, height, footprint).
5. **Seven in parallel before the method was proven.** Every mistake was paid for seven times. Lesson: one pilot lab
   to 7+/10, then reuse the method.
6. **Restarts threw work away** (model switch, stopping near-finished builders). Lesson: let a running round finish
   and learn from it; change course between rounds, not inside them.
7. **Flat renders vs shaded screenshots.** Our renderer has no lighting, shadows or shaders; his refs are shader
   screenshots. Lesson: the critic scores form, proportion, silhouette and detail against the reference, not lighting.

## What is kept
- design/refs/vehicle_labs/ - the 26 references and what each teaches (README).
- Seven faction part libraries (tools/<faction>_lab_parts.py) and round-1/2 concepts.
- SCORES.md - every critic's score and fault list.
- Roles: Opus builds, Fable critiques (lovish, 04:09).

## The method from here (proposal)
1. Pick ONE lab as the pilot and ONE primary reference for it whose proportions fit.
2. Art director writes the massing plan from that reference (region by region, like capitol_white_angel_v4.py's
   "THE PICTURE, STUDIED").
3. Opus builds; render at --px 32 beside the reference; one Fable critic scores resemblance AND quality.
4. Repeat until 7+, then roll the method to the other six, one or two at a time.
