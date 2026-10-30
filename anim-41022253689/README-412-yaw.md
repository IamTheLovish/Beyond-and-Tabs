# #412 smooth turning: the real client's yaw per frame

Eyes run 41022253689. Capture build 41012370648 = claude/anim-captures a22a5aa1 (#412 1e16c403 + the eyes viewer).
Four units walk a three-leg route (east, north, east). The `rts` camera follows each unit's position, with its yaw fixed at -45°.
Every drawn frame logs two yaws (`[Eyes] anim-yaw`):
- **vanilla:** vanilla's lerp of yBodyRotO..yBodyRot, which is what main draws.
- **drawn:** MotionPass.bodyYaw, which is what this PR draws.

Both come from the same client, the same frame and the same partial tick, so before and after are on the same footing. Plot: yaw-412.svg.

The CI client draws about 10 frames per second of game time, roughly one frame every 2 ticks. A step per frame there mostly measures how far apart the frames are. Turn acceleration (the change of turn rate between frames, in degrees per tick²) is the jolt of a turn starting, stopping or reversing, and the frame spacing does not set it. Acceleration is the fair measure at this frame rate.

| Run | Unit | Frames | Frames per s of game time | Turn acceleration max, deg/tick^2, vanilla / drawn | ... RMS | Largest turn rate deg/tick | Largest step per frame |
|---|---|---|---|---|---|---|---|
| 0 | blade_dancer_unit | 98 | 8.2 | 11.2 / 6.8 | 1.69 / 1.50 | 14.5 / 19.2 | 39.2 / 51.6 |
| 1 | grunt_unit | 124 | 10.3 | 18.5 / 8.3 | 3.09 / 1.79 | 31.2 / 23.1 | 56.8 / 63.9 |
| 2 | rifle_squad_unit | 129 | 10.7 | 21.7 / 6.2 | 2.98 / 1.40 | 38.5 / 18.2 | 67.7 / 43.3 |
| 3 | windcaller_unit | 131 | 10.8 | 14.2 / 4.6 | 2.68 / 1.04 | 23.2 / 11.6 | 42.7 / 19.0 |

What it shows:
- **Jolt is cut on all four units.** Peak acceleration falls by 1.6x to 3.5x (11.2→6.8, 18.5→8.3, 21.7→6.2, 14.2→4.6). RMS falls on all four, from about 11% lower (Blade Dancer) to about 2.6x lower (Windcaller).
- **Peak turn rate falls on 3 of 4.** The Blade Dancer's rises (14.5→19.2 deg/tick) on two frames:
  - t 229.9→232.6: 19.2 deg/tick during a server reversal.
  - t≈319: the drawn yaw runs 13° ahead of vanilla, and its 17.3 deg/tick there also beats vanilla's peak.
- **Largest step per frame rises on two units:** Blade Dancer 39.2→51.6 and Grunt 56.8→63.9. At this frame spacing, step per frame mostly measures where the frames fall.
- **The drawn yaw leads vanilla by 7-13° at some turn starts** (e.g. grunt t=516.02: vanilla -90.45, drawn -97.36). A spring stepped once per tick on the same yBodyRot can only lag.
  - I ran Life.spring itself through the vanilla curve rebuilt from these frames. The logged drawn yaw fits best when the spring's input is about 1-1.5 ticks ahead of what vanilla's lerp draws, or about as well when it steps twice per tick.
  - Code reading rules out a second listener: AnimStates is registered once and steps at ClientTick END, after the entities tick.
  - Frames about 2 ticks apart can't tell these apart, so the next capture logs every tick's yBodyRotO, yBodyRot and the spring's previous value, value and velocity (`[Eyes] anim-tick`).
- **The server's body yaw is not the 90°/tick snap TurnTest models.** In the real game it turns at 15-40°/tick, and it also *wobbles* by 40-130° within about 15 ticks while walking. Examples:
  - grunt: -90 → -158 → -139 → -176 → -147
  - grunt: -87 → -223 → -105
  
  The spring smooths that wobble but doesn't remove it. The wobble comes from the server (pathing, or the body following the head) and is a separate cause of "isnt fluid". It's a candidate for the gait PR or its own fix.

Correction, Oct 30: Eris.4's fresh-look review recomputed every cell from the 482 lines (all match) and corrected the Blade Dancer note above.

Next for the gait PR: the fixed-step 60 fps capture. It will show the per-frame steps at 3 samples a tick, which the CI client's ~10 fps cannot.
