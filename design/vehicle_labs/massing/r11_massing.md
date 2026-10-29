# Engine Shed (Brassworks T1 vehicle lab) - r11 massing options (Oct 29 2026)

lovish, 16:55: "looks blocky and toy", "doesnt necessarily look like a factory". 16:58: rework the shape into a
recognisable factory - sawtooth north-light roofs, chimney stacks as the landmark, big hangar doors; the stepped copper
tower (it read as a church / palace) shrinks to a clock / water tower. Greybox only (role blocks, no detail):
`python3 tools/engine_shed_massing.py` writes `r11_{a,b,c}.nbt` here; `tools/test_engine_shed_massing.py` holds them to
the envelope (17 x 25 x <= 26), lane x 0..8 / z 10..14 / y 1..8 and spawn column (8, *, 12) clear, a framed hangar door
>= 5 x 6 on the lane, a stack as the tallest non-tower element (>= 1.6 x the main eave, tower lower), sawtooth teeth
with exposed glass where claimed, a base course under every wall, three different silhouettes.

Roles: bricks body, polished andesite base course / plinths, deepslate tiles slate roofs, glass north lights and
clerestory, polished blackstone the door's iron frame and stack caps, stone-brick stacks, waxed oxidized copper the one
accent (clock, tank), dark oak the smoke-vent louvre. The front (door) face is the west face, x 0; the camera sees
west and south.

## What the studies taught
- **Reference builds (schem_study, study only).** Victorian Factory (63 x 58 x 84): hall roofs top out ~29-51, its
  three cut-deepslate stacks are 3 x 4 and reach 57: stacks ~1.1-1.2 x the highest roof, ~2 x the median roof, and the
  slender shaft (3-4 wide at 60+ wide) is what reads. Its section shows a pitched slate hall flanked by lower bays and
  a glazed clerestory. Steampunk Factory (63 wide): SIX 4 x 4 stacks in a row at 40 over a hall at ~26 (1.5 x) - the
  row of stacks IS the facade. Industrial Area: flat roofs, the stack groups at 28-44 over a 2-8 median - the read is
  stacks against a flat, low mass. 1890 Station: stepped roofs, one tower at 30 over halls at 12 (2.5 x).
- **Precedents.** Weaving sheds (Wikipedia): north-light roofs ~30 deg, glass on the north side, slate on the other,
  a 3 x 6 m bay on iron columns, single storey, an engine / boiler house and stack attached - from above, a field of
  parallel saw ridges. Midland Railway loco shed (Northants HER): 68 x 13 m single storey, a wide segmental-arched
  door on an iron lintel at the gable end, polychrome brick in a blind arcade of pilasters, dentilled eave - the long
  narrow gabled shed with a door in the end is the shape. GWR Swindon pattern store: a whole riveted iron WATER TANK
  as the building's top, red brick with engineering-brick pilasters - the tank is a landmark in its own right. Frostpunk
  / Anno (design/research/rts-buildings-frostpunk-anno-2026-10-29.md): the roof is 40-80 % of the pixels; identity =
  primary mass + roof feature + one signature >= 3 blocks; smoking chimneys signal "producing".
- **So:** from the RTS camera "factory" = (1) a slender stack clearly taller than everything, (2) a roof that is a
  repeated industrial form (saw teeth, a vent ridge, a clerestory), (3) one big dark opening with a track to it. The
  church read came from a centred, symmetric, pointed tower over the door; every option below puts the tallest thing
  at the BACK corner, off-centre, and makes it thin.

## (a) North-light works - sawtooth shops wrapping a gabled erecting bay
The plot's whole back and flanks are one north-light shed: six saw teeth along z (each 4 long, glass face 3 tall facing
south to the camera - the RTS view looks straight into the glazing), eave 6, peaks 10. Cutting into the front of it,
the dominant mass: a gabled erecting bay (x 0..9, z 8..16, eave 10, ridge 14) whose west gable holds the hangar door
(5 x 8, iron frame). Two stacks at the back corners - 25 (north, the landmark) and 22 (south) on andesite plinths to
11. The old tower becomes a 3 x 3 brick clock tower at the front-north corner, copper clock stage at 16, cap to 18.
Hierarchy: stacks (landmark) > erecting bay > saw field > clock tower. Reads like a Lancashire mill / loco works.

Heights: saw eave 6 / peaks 10; bay eave 10 / ridge 14; clock tower 18; stacks 25 and 22.

```
     z 0123456789012345678901234      (rows = x, front x 0 at the top; digit = top y, a-p = 10-25)
x  0   ........bcdefedcb........
x  1   ...ghg..bcdefedcb........
x  2   ...hih..bcdefedcb........      ghi = clock tower 16-18
x  3   ...ghg..bcdefedcb........
x  4   6789a789bcdefedcb789a789a      6789a = one saw tooth (glass in the 'a' column)
x  5   6789a789bcdefedcb789a789a
x  6   6789a789bcdefedcb789a789a
x  7   6789a789bcdefedcb789a789a
x  8   6789a789bcde.edcb789a789a      . = the spawn / smoke-vent shaft
x  9   6789a789bcdefedcb789a789a
x 10   6789a789a789a789a789a789a
x 11   6789a789a789a789a789a789a
x 12   6789a789a789a789a789a789a
x 13   bbbba789a789a789a789abbbb      bbbb = stack plinths (11)
x 14   bppba789a789a789a789abmmb      pp = stack 25, mm = stack 22
x 15   bppba789a789a789a789abmmb
x 16   bbbba789a789a789a789abbbb
```

## (b) Engine shed - long gabled shed, door in the gable, boiler house + one stack, tank on legs
The railway engine-shed type: one long gabled shed along x (x 2..16, z 7..17, eave 9, ridge 14) with a smoke-vent
lantern down the ridge (dark louvre, capped at 17), the hangar door in its west gable with a segmental (raised-centre)
iron head. On the south (camera) side a lower gabled boiler house (eave 6, ridge 9) carrying the ONE landmark stack to
25. Stores on the north (eave 5). Front-north, the water tower: a 5 x 5 copper tank (y 10..13, slate lid 14) on four
andesite legs - the old tower's role as the front's vertical accent, but plainly a tank. Hierarchy: stack > engine shed
> tank > boiler house > stores. Most legible as "vehicles come out of here"; the least roof interest (no saw teeth).

Heights: shed eave 9 / ridge 14 / vent 17; boiler house 9; stores 8; tank 14; stack 25.

```
     z 0123456789012345678901234
x  0   .........................      (the apron in front of the door)
x  1   .........................
x  2   .......abcdefedcba.......      the door gable
x  3   .ddddd.abcdefedcba.......      ddddd / eee = the tank on legs (13-14)
x  4   .deeed.abcdhhhdcba.......      hhh = the smoke-vent lantern (17)
x  5   .deeed.abcdhhhdcba.......
x  6   .deeed.abcdhhhdcba.......
x  7   .ddddd.abcdhhhdcba789987.      789987 = boiler house
x  8   .......abcdh.hdcba789987.
x  9   .678876abcdhhhdcba789987.      678876 = stores (north)
x 10   .678876abcdhhhdcba789987.
x 11   .678876abcdhhhdcba789987.
x 12   .678876abcdhhhdcba7aaaa7.      aaaa = stack plinth (10)
x 13   .678876abcdhhhdcba7appa7.      pp = the stack, 25
x 14   .678876abcdhhhdcba7appa7.
x 15   .678876abcdefedcba7aaaa7.
x 16   .678876abcdefedcba.......
```

## (c) Locomotive works - clerestory nave with the gate, sawtooth aisles, twin stacks, clock turret (my pick)
A basilica of industry: the tall gabled nave (x 0..16, z 9..15, eave 11, ridge 14) runs the full depth with the gate in
its west gable and a glazed clerestory band (y 9-10) over the aisles; on each side a north-light aisle (eave 4) whose
three saw teeth run along x with their glass faces turned WEST to the camera; the back bay is a low slate lean-off. Two
equal stacks (24) stand at the back corners of the nave, framing it - the pair is the landmark, the eye runs down the
nave to the door. The tower is reduced to a clock turret on the gate gable (to 20). Hierarchy: stacks > nave > aisles
> turret; symmetric about the lane, so the door is the axis - "something big drives out of here".

Heights: aisle eave 4 / saw peaks 8; nave eave 11 / ridge 14; clock turret 20; stacks 24 and 24.

```
     z 0123456789012345678901234
x  0   .........cdjjjdc.........      jjj/k = clock turret (19-20) on the gate gable
x  1   .........cdjkjdc.........
x  2   .........cdjjjdc.........
x  3   ..4444444cdefedc4444444..      cdefedc = the nave
x  4   ..8888888cdefedc8888888..      8 = a saw peak (glass faces west, toward the camera)
x  5   ..7777777cdefedc7777777..
x  6   ..6666666cdefedc6666666..
x  7   ..5555555cdefedc5555555..
x  8   ..8888888cde.edc8888888..
x  9   ..7777777cdefedc7777777..
x 10   ..6666666cdefedc6666666..
x 11   ..5555555cdefedc5555555..
x 12   ..8888888cdefedc8888888..
x 13   ..7779999cdefedc9999777..      9999 = stack plinths
x 14   ..6669oo9cdefedc9oo9666..      oo = stacks, 24
x 15   ..5559oo9cdefedc9oo9555..
x 16   ..5559999cdefedc9999555..
```

Stills (eyes, plain): branch eyes-structures, `bat_eyes_15_r11_{a,b,c}_{rts,rts_close,34}.png`. Not yet judged - a
fresh critic will pick.

## (c2) Locomotive works, revised - the critic's pick (Oct 29)
A fresh critic picked (c): twin stacks lead to the door and the clerestory, and it scales to T2-T4. They rejected (a) as a
parish-church silhouette moved sideways and (b) as a barn / chapel with a chimney. Changes, each tested first:
- **No turret over the gate** (lovish had rejected a copper-topped block over the door). A 3 x 3 clock stands at the
  front-south corner instead: brick to 13, a copper stage at 14-15, cap 16.
- **Asymmetric stacks.** The landmark is 22, at the back-north corner on its plinth (24 cropped at the RTS frame top).
  The second is 19 and rises from a low gabled boiler house (x 6..11, z 20..24, eave 6, ridge 9) mid-depth on the south
  flank.
- **The gate** is 7 wide x 8 tall under a segmental iron arch (crown 9, edges 8), with a glazed fan-light in the gable
  above, so the gable is no longer a blank brick face. The nave is eave 10, ridge 15. The aisle saw teeth are 5 rows
  (eave 5, peaks 9-10) and read as saw teeth, not lean-tos.

```
     z 0123456789012345678901234
x  0   ........bcdefedcb....fff.      fff / g = the clock (15-16), front-south
x  1   ........bcdefedcb....fgf.
x  2   ........bcdefedcb....fff.
x  3   .aaaaaaabcdefedcbaaaaaaa.      a = a saw peak (10), glass facing west
x  4   .9999999bcdefedcb9999999.
x  5   .8888888bcdefedcb8888888.
x  6   .7777777bcdefedcb77778987      78987 = the boiler house
x  7   .6666666bcdefedcb66678987
x  8   .aaaaaaabcde.edcbaaa78jj7      jj = the boiler stack, 19
x  9   .9999999bcdefedcb99978jj7
x 10   .8888888bcdefedcb88878987
x 11   .7777777bcdefedcb77778987
x 12   .6666666bcdefedcb6666666.
x 13   .9999aaabcdefedcbaaaaaaa.
x 14   .9mm9999bcdefedcb9999999.      mm = the landmark stack, 22
x 15   .9mm9888bcdefedcb8888888.
x 16   .9999777bcdefedcb7777777.
```
