# Bone Dragon flight - phase tables (r13)

Why: lovish's match, Oct 22: "the dragon animation was bad", "the model needs fine tuning". His frame (night, Minecraft
view, behind/above): the neck a stiff straight rod, the purple sail one flat slab, the silhouette lost on a dark sky.
Bar: The Isle / Day of Dragons flight (design/REFERENCES.md items 10-21). Rig: tools/gen_dragon.py (body),
unit/modelling/animations/Dragon.java (motion), tools/dragon_poses.py (its Python twin, the offline frames).

Body numbers at 1x (drawn x1.4): thorax 12 px tall (BH), wing span ~130 px, neck 8 links, tail 10 links.
All times in ticks (20/s). Phase = share of the cycle. "Leads / follows" = which part moves first.

## 1. Flap (cruise and climb) - T = 52 ticks (2.6 s): a 6 % hold, the power stroke 28 % (eases off the top, strikes INTO the bottom), the recovery 66 % (review rounds 1-2: the stroke must read asymmetric)

| phase | ticks | wing (arm / fore / hand / fingers) | sail | body | neck (S) | tail |
|---|---|---|---|---|---|---|
| top hold 0-.06 | 3 | arm +38 up, elbow open, hand swept back, fingers trailing, tips still rising | slack, cupped down | lowest (-6 px = -0.5 BH), nose down 5 | base flexed up, head level | tip low |
| downstroke .06-.36 | 16 | arm leads (snaps off the top then loads), fore 3 ticks behind, hand 0.15 T behind, finger tips 0.08 T more: tips trail UP | taut, billowed UP behind the bones (trailing strips 10-25 deg above), chord-wise wave root->hem | rises 10 px (0.8 BH) 4 ticks late, nose up 9 (round 2: the review saw no weight at 6) | base pitches down against the lift (-5 / link at base), head counters 85 %, stays level | wave starts down the chain, 3 ticks / link |
| bottom ~.36 | 1 | arm -42, tips curled under, fore bent down | belly full, tips flick | top of the rise | S tightest | tip up |
| recovery .36-1 | 33 | wrist folds first (hand back 60), elbow back 35, fingers close 18-50 toward the leading one, arm rises last and slowly; wing comes up NARROW | slack, cups DOWN, flutters on the trailing strips | sinks | S relaxes, base lifts | tip settles |

Lateral: the neck holds an S from above (base and tip curve opposite, 6-9 deg / link, a 140-tick wander) and the tail
answers it on the other side (counterweight); both take the bank.

## 2. Glide between flap bursts (cruise)

Cruise is a rhythm, not a constant beat: 3 beats (156 ticks) then a glide of 90 ticks; at full speed 1 beat then 160.
Entry: the last recovery ends with the arm at the glide angle (+6, slight dihedral), not at the top. Exit: the first
beat starts from the glide pose (the arm rises over a quarter beat first - anticipation).
Held: fore -3, hand -6 (a soft bend), fingers spread; the sail billows slowly (a 30-tick ripple, 4 deg, travelling
root->tip and lead->hem); the tail streams straight with a 90-tick lateral undulation; the neck reaches forward, the
S shallower; legs tucked tight. Never dead still: the arm drifts +-2.5.

## 3. Banked turn

Body roll to the turn rate (max 50, critically damped, no overshoot - Dragon.bankStep, unchanged). The WHOLE body tilts
(the wings are its children). Inner wing: beats 30 % shallower, dropped 16, elbow and wrist folded 22 / 20 (it tucks); outer wing
held out and beats 10 % deeper. Neck: counter-rolls so the skull is near level, and curves INTO the turn (yaw grows
toward the head, up to 0.2 * bank per link, the head 0.18 more) - the head leads. Tail: curls into the turn on two lagged copies of the bank
(rudder), settling after the body.

## 4. Hover / attack (breath)

Hover (speed under 0.02): T x 0.85, the stroke plane tilted - the arm sweeps FORWARD 18 on the downstroke and back
14 on the recovery (beating forward and down), body pitched up 18, hind legs hang and reach, tail hangs low (+12
down) and swings slowly as a counterweight, neck up in a tall S, head pitched down to look at the ground.
Attack (the shot is tick 0): wind-up -8..0 - the neck coils back (S compressed, base up, tip back), the wings rise to
the top and hold, the jaw opens; strike 0..6 - the neck uncoils forward and down, a full downstroke lands with it,
the body lurches forward 4 px; hold 6..10 - jaw at its gape; recovery 10..22 - jaw snaps shut, the S re-forms.

## 5. Take-off and landing (distinct from cruise)

Take-off (the first 60 ticks of life, or a climb from slow speed): T x 0.8, amplitude x1.25, body pitched up 25, the
legs dangling and kicking back, the tail hanging, the neck stretched forward. Landing / flare (decelerating into a
hover): wings flared wide and forward (arm y +22, z +16), the hands spread, body pitched up 26, the hind legs reaching
forward and down, the tail dropped; beats shallow and fast-ish (air brakes) until the hover takes over.

## 6. Idle (hovering over the ground)

The hover beat at 0.8 amplitude, breath 90 ticks (ribs swell 10 %), a slow look round (neck tip leads, head finishes),
a yawn every ~10 s, a head shake every ~13 s, the tail tip flicks; the wings keep the hover beat - they are what holds
it up.

## Checks (offline, tools/dragon_poses.py frames): the RTS top-down and a chase camera (behind, 25 deg above)
- Neck reads as a curve from above in every frame (lateral S >= 15 deg base-to-tip).
- The sail shows at least three shades / facets from above (no single flat slab).
- Wingtips trail the arm (tip still rising at the start of the downstroke).
- Silhouette on a night sky: the bone rim and the pale hem keep the outline.
