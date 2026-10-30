# #441 walk line: before / after (eyes turn route, 4 units, rts view)

- **Before:** eyes run 41050396210, capture 869dd32e (#412 at 1bb8230e plus the yaw logging, no path smoothing).
- **After:** eyes run 41099666798, capture cddf3545 (the same tree plus #441's 4 files at **e3821a40**, the first fix).
  - Round 1 of the review changed the line's ground rules and end points after this capture (613406df).
  - This plot will be redone on the green head.
- **Plots:** [walk-before.svg](walk-before.svg), [walk-after.svg](walk-after.svg). Each draws, per frame, vanilla's lerp and the drawn yaw (#412's spring).

## How much the body turns on the route

The route is east, then north, then east, with two corners. Units are re-ordered about 6 blocks before each corner, so the middle leg is an off-axis line (about 10 east, 32 north): the staircase case. Measured from the per-tick server body yaw, `yBodyRot`, over the clip's 240 ticks:
- "body turned" is all the turning;
- "excess" is that turning minus 180, a rough floor for the two corners (it goes negative when the corners are cut);
- "reversals>5" counts swings of more than 5 degrees that turn back.

```
before unit                   ticks body turned  excess reversals>5 drawn turned  excess
before blade_dancer_unit        240       360.9   180.9           6        352.0   172.0
before grunt_unit               240       335.6   155.6           8        306.5   126.5
before rifle_squad_unit         240       264.7    84.7           7        236.7    56.7
before windcaller_unit          240       312.0   132.0           7        289.3   109.3
after  blade_dancer_unit        240       398.9   218.9           5        335.6   155.6
after  grunt_unit               240       105.5   -74.5           1        105.5   -74.5
after  rifle_squad_unit         240       138.3   -41.7           1        133.7   -46.3
after  windcaller_unit          240       102.6   -77.4           1        102.6   -77.4
```

- **Grunt, rifle squad, windcaller:** they go from 7-8 swings to 1. Total turning falls from 265-336 to 103-138 degrees.
- **Blade dancer:** its first diagonal leg is now one straight line, with the body holding -167 for about 70 ticks; before, it swung between -135 and -183. It still swings at the second corner, where the ground steps down a block (y 71 to 70). There, the same-height rule hands the walk back to the nodes.
