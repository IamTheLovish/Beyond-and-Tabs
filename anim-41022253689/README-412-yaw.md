# #412 smooth turning: the real client's yaw per frame

Eyes run 41022253689. Capture build 41012370648 = claude/anim-captures a22a5aa1 (#412 1e16c403 + the eyes viewer).
Four units walk a three-leg route (east, north, east) and are filmed from a world-fixed `rts` camera.
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
- **Peak turn rate falls on 3 of 4.** The Blade Dancer's rises (14.5→19.2 deg/tick) on one frame, at t≈319. The drawn body was still catching up with a turn the server had already finished, so the drawn yaw moved further in that one 2.8-tick interval than vanilla did.
- **The server's body yaw is not the 90°/tick snap TurnTest models.** In the real game it turns at 15-40°/tick, and it also *wobbles* by 40-130° within about 15 ticks while walking. Examples:
  - grunt: -90 → -158 → -139 → -176 → -147
  - grunt: -87 → -223 → -105
  
  The spring smooths that wobble but doesn't remove it. The wobble comes from the server (pathing, or the body following the head) and is a separate cause of "isnt fluid". It's a candidate for the gait PR or its own fix.

Next for the gait PR: the fixed-step 60 fps capture. It will show the per-frame steps at 3 samples a tick, which the CI client's ~10 fps cannot.
