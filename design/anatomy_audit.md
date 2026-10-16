# Sunforged bodies: the figure under the costume (anatomy audit, Oct 16)

For lovish. This is the next pass after today's hands, faces, n-gon forms and elbows. It answers "The units need more
realism and texture, look at human anatomy could help" by measuring the five figures the way a figure-drawing
teacher would, then listing the fixes. The repo was read only; nothing in it was edited.

- Sheet: `anatomy_sheet.png` (this folder). Per body: rest front and side silhouettes, the figure stand front and
  side (every bone rotation zeroed, so the arms hang and the legs are straight), the walk (contact key over the
  passing key), and a **proposal mock** front and side. Every cell sits on Loomis's 8-head ladder, scaled to that
  body's stature. The bottom row shows the five at one stature, and the RTS far read (~36 px) of each, now and
  proposed.
- Scripts (scratch, re-runnable): `fig.py` (game_preview's own renderer at a known px mapping, with a bind-pose
  option), `measure.py`, `legs.py`, `contour.py` (the numbers), `targets.py` (the proposal and its mock), `sheet.py`.

## 1. Verdict: the three biggest problems

1. **The heads are too big, and they take the space from the legs.** Measured as read (chin to the top of the
   headgear, no plume), the heads are 18-22% of stature. The canon for a helmeted figure is about 15%. The heads
   are also as wide as the chests. Because the stature is fixed (the gate and lineup heights), the extra head height
   comes out of the legs: the hip joints sit at 0.445-0.49 of stature against a canon of 0.53. The bodies read
   **4.5-5.6 heads tall**. Minecraft's vanilla humanoid is 4 heads (32 px tall, 8 px head), so they read as toys,
   not soldiers. Two exceptions run the other way: the Medic's head reads small but very wide (a wide hood), and
   the Squire has a correctly sized face under a helm twice as tall as his skull needs.
2. **The four T1 bodies have tube legs with the taper upside down and no calf.** Each leg is one constant prism.
   The thigh is 61-74% of canon width; the ankle or boot is as wide as the thigh (the Medic's is wider); the knee
   joint is often the widest point; nothing swells behind the shin. Thigh-to-ankle taper is 0.81-1.09, where canon
   is about 2.4. This is the "blocky from the side" lovish saw on the Paladin. The Paladin's lofted leg fixed it
   (taper 1.96, a calf 0.6 px behind the shin line), and it is the model for the other four.
3. **Box shoulders and no neck, plus elbows set too high.** On the Halberdier and the Paladin the pauldron tops
   rise 1.5-2.5 px above the shoulder joint, which puts them *above the chin*. The Levy's shoulder caps are drums
   0.75 px over the joint, the Squire's shoulder line is level, and the Medic's mantle reaches her chin. The
   trapezius line, which should fall about 20 degrees from the neck to the shoulder, is level or rises, so the head
   sinks into a box. Separately, on every body the elbow sits at 51-52% of the shoulder-to-wrist length (canon 56%,
   upper arm to forearm 1.27 to 1). It also sits 2-3 px above the body's own waist or belt line, where it should be
   at it. This is the measurable root of "the elbows don't match human anatomy": today's elbow pass fixed the
   joint's shape, but not where the joint is on the arm.

Also shared, though less visible: every T1 trunk is a straight column in profile. The torso bands are centred at
z = -0.06 x depth, the lumbar curve is at most 0.2 px (about 0.8-1 px expected at this scale), and no body shows
buttocks or a forward chest. Every head sits dead centre over the shoulder joints on a vertical neck. The walks are
symmetric straight-legged pendulums (see section 6).

## 2. Canon, units and method

**Units.** S is stature: sole (y 0) to the top of the headgear, plume, crest and knob excluded. I report two heads:

- *Head as read*: chin to the top of the headgear. This is what the eye counts as "heads tall".
- *Implied skull*: 2 x (eye line - chin), since the eyes sit halfway up the skull. For closed helms the eye line is
  the slit, so the implied skull equals the helm.

**Canon**

- Andrew Loomis, *Figure Drawing for All It's Worth* (1943): the 8-head ideal male. From the top, the head lines
  fall at chin, nipples, navel, crotch, mid-thigh, below the knee, below the calf, and the sole. The ideal male's
  shoulders are about 2 1/3 heads wide; the heroic figure runs 8.5-9 heads. These are the dashed lines on the sheet.
- D. A. Winter, *Biomechanics and Motor Control of Human Movement* (2009), after Drillis and Contini (1966): joint
  heights as fractions of stature.
  - Heights: chin .870, shoulder .818, elbow .630, wrist .485, fingertip .377, hip joint .530, knee .285,
    ankle .039.
  - Segments: upper arm .186, forearm .146, hand .108, thigh .245, shank .246, foot .152, biacromial width .259.
  - These are realistic (about 7.5 heads). Loomis is the idealised version. A helmet adds about 0.2-0.3 heads on
    top, so "7.5 to 8 heads with the helmet" means a helmed head of about 15% of stature.
- Leg girths (approximate adult anthropometry, front width as a fraction of S): upper thigh ~.097, knee ~.060,
  calf ~.068, ankle ~.040. Armour adds about 0.3-0.5 px. The calf's peak sits a third of the way from the knee to
  the ankle, behind a straight shin line.
- Walk: Perry and Burnfield, *Gait Analysis* (2010). The hip goes from about 25-30 degrees of flexion at heel strike
  to about 10-15 degrees of extension at toe-off. The knee is about 35-40 degrees flexed at toe-off, peaks at about
  60-65 degrees in early swing, and is near straight at heel strike.

**Method.** All measurements come from the exported JSON. Landmarks the cubes cannot name (eye line, chin, the
headgear's top) come from each generator's constants and are listed in `fig.SPEC`. Joint positions are the bone
pivots in the bind pose. Widths, depths and contours come from game_preview's own renderer (`faces_of` + `render`)
at about 22 px per model px, in the bind pose with props hidden, and with the arms hidden for trunk and leg
measurements.

## 3. RTS stylisation: what is fine and what is a mistake

**Fine (and wanted).**

- The RTS camera (tilt 52 degrees) shrinks vertical forms to cos 52 = 0.62 of their height but shows top surfaces at
  sin 52 = 0.79. The helm's crown, the brim and the shoulder tops face the camera, so a head of 16-17% of stature
  already *looks* about a quarter of the figure from above. A head slightly bigger than canon (16% for soldiers,
  17.5% for the young Squire, 15% for the hero) reads well, and art-theory section 7 says to set head size per unit
  on purpose.
- Hands at 1.0-1.2x canon read the grip. Ours are 1.0-1.16x (the Halberdier's gauntlet is 1.84 px across against
  1.6 canon), so **the hands are not a problem in this pass.**
- Props at 1.5-2x (the existing signature-prop rule) are fine.
- The far-read tiles on the sheet confirm it: at ~36 px the proposed bodies keep the plume, brim, hood, halo and
  props, and read taller and more human. The squeeze costs nothing at RTS zoom.

**Mistakes.**

1. Bigger heads paid for with leg length: at 4.5-5.6 heads the stature is fixed, so every extra px of head is a px
   of leg lost. That reads as a toy at every zoom.
2. Heads as wide as the chest (Halberdier 0.98 of his chest; the Levy's brim is 1.4x his chest). The body becomes
   the head's pedestal.
3. Headgear oversized against the face it frames: the Squire's dome is 2x his cranium and the Medic's hood is 1.5x
   wider than tall. That reads as a child in an adult's clothes.
4. Shoulder masses at chin height: the notch between head and shoulders, which is what says "person" from far
   away, closes, and the head and shoulders merge into one blob.
5. Leg tubes and straight trunks: invisible at 36 px, but they are exactly what lovish sees close up ("blocky",
   "needs depth").

The WC3 Reforged lesson in the research inbox (re-proportioning toward realism lost unit recognition) is why the
proposal is a *stylised* canon. Every class keeps its silhouette element, and only the figure moves toward canon.

## 4. The numbers

Fractions are of S. "Canon" is Winter's realistic figure; the proposed target per body is in section 5.

| | Halberdier | Squire | Levy | Medic | Paladin | canon |
|---|---|---|---|---|---|---|
| stature S (px) | 31.4 | 27.55 | 29.24 | 28.1 | 44.75 | - |
| head as read, tall x wide (px) | 6.95 x 7.22 | 5.57 x 4.66 | 5.64 x 9.0 (brim; coif 4.6) | 3.9 x 5.95 (hood) | 8.0 x 7.82 | - |
| head as read, % of S tall / wide | 22 / 23 | 20 / 17 | 19 / 31 | 14 / 21 | 18 / 17 | ~15 / ~11 |
| heads tall (as read) | **4.5** | **4.9** | **5.2** | 7.2 (but wide) | **5.6** | 6.3-6.7 helmed |
| implied skull (px), skull heads | 6.76, 4.6 | 3.58, 7.7 | 4.24, 6.9 | 3.06, 9.2 | 6.64, 6.7 | 7.5-8 |
| head width / chest width | 0.98 | 0.80 | 0.72 (brim 1.41) | 0.92 | 0.72 | ~0.5-0.55 |
| chin | .779 | .798 | .807 | .873 | .821 | .870 |
| shoulder joint | .745 | .748 | .759 | .804 | .791 | .818 |
| elbow | .580 | .588 | .595 | .626 | .601 | .630 |
| wrist | .427 | .443 | .445 | .463 | **.422** | .485 |
| hip joint | **.446** | .472 | **.445** | .463 | .492 | .530 |
| knee | .223 | .254 | .233 | .249 | .268 | .285 |
| knee as % of hip height | 50% | 54% | 52% | 54% | 55% | 54% |
| torso (hip joint to shoulder joint) | .299 | .276 | .315 | **.342** | .299 | .288 |
| upper arm / forearm | .166 / .153 | .160 / .145 | .164 / .150 | .178 / .164 | .190 / **.179** | .186 / .146 |
| elbow along the arm (upper / (upper + fore)) | 52% | 52% | 52% | 52% | 52% | 56% |
| elbow above own belt (px) | 2.2 | 2.4 | 2.4 | 3.2 | 1.6 | ~0 |
| thigh / shank | .223 / .192 | .218 / .226 | .212 / .200 | .214 / .214 | .223 / .221 | .245 / .246 |
| shoulder joints apart | .293 | .247 | .267 | **.299** | .286 | ~.24 (joint centres) |
| front breadth at the shoulders, arms included | 14-15 px (.45-.48) | 9.4-11.3 (.34-.41) | 10.2-11.1 (.35-.38) | 10.4-10.8 (.37-.38) | 20.8-21.6 (.47-.48) | .27-.29 bare, ~.32-.36 in plate |
| shoulder-mass top vs joint / vs chin (px) | +1.47 / **+0.42** | +0.6 / -0.8 | +0.75 / -0.65 | +1.7-2.0 / ~0 | +2.46 / **+1.11** | ~+0.9 / -1.6 |
| trapezius line, front contour from neck to arm | rises to a pauldron hump | level (0 deg) | drum caps | falls 25-33 deg (mantle) | rises to a hump | falls 15-25 deg |
| neck: chin to shoulder joint (px) | 1.05 | 1.38 | 1.40 | 1.92 | 1.35 | .052 S |
| leg front width (px): thigh top / knee / calf / ankle | 2.27 / 2.49 / 2.06 / 2.16 | 1.91 / 1.56 / 1.50 / 1.76 | 1.79 / 1.79 / 1.55 / 1.72 | 1.66 / 1.66 / 1.66 / 2.05 | 4.54 / 3.33 / 3.33 / 2.32 | see next row |
| canon for that S | 3.05 / 1.88 / 2.14 / 1.26 | 2.67 / 1.65 / 1.87 / 1.10 | 2.84 / 1.75 / 1.99 / 1.17 | 2.73 / 1.69 / 1.91 / 1.12 | 4.34 / 2.69 / 3.04 / 1.79 | |
| taper, thigh / ankle | **1.05** | **1.09** | **1.04** | **0.81** | 1.96 | ~2.4 |
| calf behind the shin line | none (boot sits further back) | none | none | none | +0.6 px | ~+0.5 px |
| foot length / width | .159 / .076 | .163 / .073 | .162 / .068 | .171 / .082 | .167 / .056 | .152 / .055 |
| ankle height | .031 | .028 | .033 | .035 | .048 | .039 |
| lumbar curve in profile (px) | ~0.2 | ~0.15 | ~0.2 | robe | ~1.0 (partly cloth) | ~0.8-1 |

Notes on the table:

- **Knee placement inside the leg is correct everywhere.** The knee is 50-55% of the way up to the hip; the legs are
  just short overall.
- **Feet are fine in length** (slightly long helps them read as grounded) but wide on the T1s.
- **Joints inside their volumes:**
  - Knees: the knee-cop or poleyn axle is centred on the knee pivot on every body. Good.
  - Shoulders: the pivot sits at the very top of the upper-arm tube (Halberdier 0.2 px below the top, Squire 0.3,
    Paladin 0.6), and on the Medic the sleeve starts 0.5 px *below* the pivot. A person's shoulder joint sits about
    0.9 px under the top of the deltoid, with the deltoid wrapped round it.
  - Hips: on the T1s the thigh tube starts exactly at the hip pivot, so the thigh's top corners swing out of the
    hip in the stride and a notch opens at the back. The Paladin's cuisse rises 2 px above its pivot, as a real
    thigh and buttock do.

## 5. Fix list per body (top 5, most visible first)

The proposal keeps every stature (the gate and lineup heights). As fractions of S:

| | chin | shoulder joint | elbow | wrist | hip | knee | helmed head |
|---|---|---|---|---|---|---|---|
| line soldiers (Halberdier, Levy) | .84 | .80 | .62 | .475 | .52 | .28 | 16% |
| Squire | .825 | .785 | .612 | .468 | .51 | .275 | 17.5% (young, by choice) |
| Paladin | .85 | .812 | .63 | .485 | .53 | .285 | 15% (hero dial: smaller head) |
| Medic | keeps her chin; the rest as soldiers | | | | | | |

Green ticks on the sheet mark these heights. The "PROPOSAL mock" columns approximate them with game_preview's
`_scale` and `_move` (head scaled about the neck, body raised, legs lengthened from the hip, arm segments rescaled).
The mock only stretches; the real fix is in the generators.

**Do fixes 1-3 of each body in one re-proportion pass**: head, hips and torso move together. After it:

- re-run the arm solves (`ELBOW_AT` and the grip points move with the torso: Halberdier `grip_y` 16.6 -> ~18.3,
  Levy +1.2, Squire +1.05, Paladin +0.9; the Medic's shoulders stay, so her grip stays);
- re-export (`tools/export_bodies.py`) and re-run `tools/gen_body_icons.py`;
- check `game_preview --art-check` and `contact_check`.

Scaled-down cubes under 0.6 px^3 drop into the "lod" detail part, so look at the head with `--no-detail` too.

### Halberdier (`tools/gen_halberdier.py`), S 31.4

1. **Head x0.72** (helm 6.95 x 7.22 -> 5.0 x 5.2 px; 22% -> 16% of S). Now the helm is as wide as the chest
   (7.22 vs 7.4) and he reads 4.5 heads.
   - In `helm()`, scale every head cube about the head pivot by 0.72: R 2.8 -> 2.0; the shells' radii (VIS 3.2/3.04,
     BEV 3.34/3.02, CHIN 2.7/3.34, the tail's 3.3/2.84) and every `hy + k` offset times 0.72.
   - Plume segment lengths x0.8, not 0.72, so the team colour keeps its area.
   - New seat: `hy = SHOULDER_Y + 1.6` with the new SHOULDER_Y, giving chin ~26.3 and top ~31.3.
2. **Hips and legs up.** He is the worst pair with the Levy: hip .446 -> .52 of S, knee .223 -> .28.
   - `HIP` 14.0 -> 16.3. In `legs()`: the shin pivot 7.0 -> 8.8, cuisse 7.45 -> 9.25 up to the new HIP, the knee cop
     to 8.8, the greave top 6.5 -> 8.3, the greave's ridge bar y 3.65 -> 4.75 and length 3.9 -> 5.6.
   - `torso()` and `tabard()`: move every y up 1.7 px, and the waist band's lower edge up 2.3 (14.0-17.0 becomes
     16.3-18.7). The torso shortens 9.4 -> 8.8 px (.28 S).
   - `SHOULDER_Y` 23.4 -> 25.1. The tabard hem 8.0 -> ~10.0, so it still ends above the new knee. Tassets
     13.0 -> 15.3.
3. **Shoulder line.** The pauldron tops sit 1.47 px over the joint and 0.42 above the chin; the front contour rises
   from the gorget into a pauldron hump. That is the square-box read.
   - In `pauldron()`: P y `SHOULDER_Y - 0.75` -> `SHOULDER_Y - 1.9` (all lames down 1.15 px); PAULDRON droops
     12/33/55 -> 20/38/58, so the top lame falls away from the gorget.
   - `SHOULDER_X` 4.6 -> 4.0: joints 9.2 -> 8.0 apart (.255 S); breadth across the pauldrons ~15 -> ~12.5 px.
   - Gorget (23.8-25.4, octagon 4.2): a turned cone 4.6 at the base -> 3.6 at the top, 0.6 px taller, so a neck
     shows above the pauldrons.
   - Rule: the pauldron top at most joint + 0.3 px, and at least 1.2 px below the chin; the contour falls about
     20 degrees from the gorget to the pauldron's edge.
4. **Leg girth and calf** (the same complaint lovish made about the Paladin; that fix applies here). Now the thigh
   is 2.27 (= the ankle's 2.16), the knee cop 2.5-2.7 is the widest point, and there is no calf. Use
   `gen_paladin.loft()` with its CUISSE/GREAVE sections scaled about 0.68:
   - Thigh 3.0 wide at the top, starting 0.8 px *above* the HIP pivot so it wraps the joint, narrowing to 2.2 above
     the knee.
   - Knee cop `octagon_axle` 2.5 x 2.6 -> 2.2 x 2.3.
   - Greave: straight front edge, the calf swelling to 2.3 wide and 0.45 px behind the knee-to-ankle line a third of
     the way down, the ankle 1.5. Sabaton width 2.2 -> 2.0.
5. **Elbow lower on the arm.** `UPPER` 5.2 -> 5.65, `FORE` 4.8 -> 4.55 (elbow at 55% of the arm, not 52%). Then
   re-solve with `ELBOW_AT` raised by the torso shift (y 18.8 -> ~19.9).
   - Vambrace (`octagon_pole` 2.06 straight): 2.3 under the couter -> 1.85 at the wrist (the forearm's taper).
   - Rerebrace top `sy + 0.2` -> `sy + 1.0`, so the joint sits inside the deltoid and pauldron mass.

Also:

- Profile: move the waist band's back edge 0.4 px forward (zf) and the mail skirt's back 0.3 px back, for a lumbar
  curve of about 0.7 px.
- Tilt the gorget about 12 degrees with its top forward (DSL x -12 about its foot); head pivot z 0 -> -0.4.
- Walk: see section 6.

### Squire Runner (`tools/gen_squire.py`), S 27.55: young, so the biggest head of the line on purpose

1. **The helm is too tall for the face.** His face is on canon: eye to chin is 1.79 px, which implies a 3.6 px
   skull and a 7.7-head figure. But the dome rises 3.78 px above the eye line, and a cranium needs 1.8. Lower the
   dome 0.8 px and keep the face: head 5.57 -> ~4.8 (20% -> 17.5% of S).
   - In `head()`: `ngon` skull band (hy+2.6, hy+3.9) -> (hy+2.5, hy+3.3).
   - `lathe` rings (hy+3.85 -> hy+4.85) and (hy+4.8 -> hy+5.4) -> (hy+3.25 -> hy+4.1) and (hy+4.05 -> hy+4.6).
   - Crown cap hy+5.0..5.45 -> hy+4.2..4.65; knob hy+5.65 / 6.15 -> hy+4.85 / 5.35.
   - Arming cap `oct_at` top hy+3.4 -> hy+2.8; brow band 4.5 -> 4.2 wide; skull 4.25 -> 4.0.
2. **Hips up 1.05 px.** `HIP` 13.0 -> 14.05, `knee` 7.0 -> 7.6, `SHOULDER_Y` 20.6 -> 21.65 (torso y's +1.05;
   HEAD_Y follows). His torso length (.276 S) is already on canon.
3. **Inverted leg taper.** The thigh is 1.91, the calf 1.50, and the boot at the ankle 1.76: the leg widens toward
   the foot.
   - Thigh `oct_at`: 2.5 x 2.6 at HIP+0.6 -> 1.8 at knee+2.6.
   - Knee boot (`oct_at` 1.75 x 1.95 from 1.1 to knee-2.0) in two pieces: the calf 1.9 x 2.1, centred 0.15 px back,
     from knee-2.0 to knee-3.6; then 1.45 x 1.6 down to 1.1. The fold-down cuff stays.
   - Foot width 1.8 -> 1.65.
4. **The run is a scissor.** At full stride (run_A, run_C) both shins are within 4 degrees of straight. Hip
   extension is acceptable thanks to the 18 degree forward lean. In `SquireRunnerUnitModel.setupAnim`, keep both
   knees bent through the stride: shin flexion at least 20 degrees, 40-45 at the rear kick, 20-30 on the reaching
   leg. See section 6.
5. **Level shoulders** (the trapezius contour is flat at ~21.0 from the collar to the spaulder).
   - SPAULDER droops 14/36/58 -> 24/42/60, P y `sy - 1.0` -> `sy - 1.4`.
   - The brigandine's top band (19.4-20.9, 4.9 wide) becomes a cone, 5.4 at 19.4 -> 4.2 at 20.9 (about a
     20-degree trapezius).
   - `UPPER` 4.4 -> 4.75; FORE 4.0 stays.

### Crossbow Levy (`tools/gen_levy.py`), S 29.24

1. **The torso is a box.** One octagon 6.4 x 4.4 runs from 15.8 to 21.4, as wide at the shoulder line as at the
   chest: no rib taper and no V, a rectangle from the front. It is also long (.315 S against .288).
   - In `torso()` replace it with ribs (6.2 x 4.3), chest (6.6 x 4.6, front 0.2 px further forward), shoulders
     (5.6 x 4.0) and a 3.4 px gambeson collar (a padded coat makes a soft V, but a V).
   - Shorten the trunk 1 px: hip joint to shoulder joint 9.2 -> 8.2.
2. **Hips up 2.2 px; thicker thighs.** `HIP` 13.0 -> 15.2, the shin pivot 6.8 -> 8.2, `SHOULDER_Y` 22.2 -> 23.4.
   - The thigh is 1.79 wide, 63% of canon: the thinnest legs under the deepest torso of the line.
   - `CUISSE_W` 1.82 hexagon -> a loft 2.7 at HIP+0.6 -> 2.0 at `CUISSE_HEM` (re-placed under the new knee, ~7.6).
     The skirt (9.4-13.6) moves +1.2.
3. **The hat.** The brim is 9.0 px across: 31% of stature and 1.4x his chest.
   - Brim `ngon` 8.6 -> 7.2, lip 9.0 -> 7.6.
   - Dome `lathe` radius 2.45 -> 2.3; hat band 5.1 -> 4.8; coif `oct_pole` 4.6 -> 4.2. The face stays.
   - Trade-off: the brim is his team read from above. At 7.6 it is still 1.15x his chest and the largest team-colour
     plane at 52 degrees, but check `--art-check`'s team share (mark: 20%).
4. **Shoulder caps are drums.** `oct_pole(sy-1.9 .. sy+0.75, 3.0)`, tilted 14 degrees, stands 0.75 px over the
   joint as a pair of box epaulettes.
   - Width 2.6, top `sy + 0.3`, tilt 24 degrees; the gold hem 3.12 -> 2.72.
   - `SHOULDER_X` 3.9 -> 3.6 (joints .246 S).
5. **No calf; elbow too high.**
   - Hose `HOSE_W` 1.6, straight: give the calf 1.95 a third of the way below the knee, 0.3 px back, and narrow to
     1.35 at the cuff. `CUFF_W` 1.94 -> 1.7.
   - `UPPER` 4.8 -> 5.25, `FORE` 4.4 -> 4.25.
   - Also: the aventail (`oct_pole` 5.0, hy-0.8 to hy+0.65) as a cone flaring 5.0 -> 5.6 onto the shoulders, so the
     neck-to-shoulder line slopes instead of stepping.

### Field Medic, the Dawn Sister (`tools/gen_medic.py`), S 28.1

1. **Hood against face.** Hers is the smallest face of the line: eye to chin is 1.53 px, implying a 3.06 px skull
   and a 9-head figure. It sits inside a hood 5.95 wide and 3.9 tall, so the head reads as a dome 1.5x wider than
   tall that runs straight into the mantle as one bell.
   - `HOOD_R` 2.7 -> 2.25 (hood ~5.0 wide); crown top hy+4.5 -> hy+4.2. She has about 1.1 px of cranium above the
     brow, plus cloth.
   - For line consistency, also scale `face()` x1.1 about (0, hy+2.0, fz): eye to chin 1.53 -> 1.7, about an
     8-head woman.
2. **Shoulders: the widest-set of the line.** Her joints are 8.4 apart (.30 S), wider than the hero's .286. A
   woman's are about .21-.22 (Loomis: the female figure has narrower shoulders, and hips as wide or wider).
   - `SHOULDER_X` 4.2 -> 3.4.
   - Bodice cone top factor 1.24 -> 1.05.
   - Mantle: `A1` 5.05 -> 4.1, `B1` 3.25 -> 2.9, `A0` 2.35 -> 2.0. It now spans 10.4-10.8 px (.37-.38 S) and drops
     at the edge as a vertical shelf; this gives ~9.
3. **No waist.** `cone(b, 0, CZ, 2.62, 1.9, 13.6, 1.0, 21.2, 1.24, ...)` is narrowest at its foot (13.6 = .48 S) and
   widens straight up: a male V. A woman's natural waist is at about .62 S (17.4 px).
   - Two cones: 13.6 -> 17.2 narrowing (1.0 -> 0.88), then 17.2 -> 20.4 to the bust (0.88 -> 1.05).
   - The belt stays a hip belt, raised with the hips (13.8-15.0 -> 15.4-16.6).
4. **Legs under the robe.** The hip is at .463 S.
   - `HIP` 13.0 -> 14.6, knee 7.0 -> 7.9.
   - The thigh is a 1.7 square box and the boot `ngon` is 2.0-2.05, wider than the thigh; it shows every time the
     robe parts in the walk. Thigh: a taper 2.4 -> 1.7. Boot: 1.75 at its top -> 1.4 at the ankle.
5. **Feet and forearm.**
   - `foot(m, pre, hx, 0.0, 2.1, 4.6, 1.3, ...)` gives 4.8 x 2.3 (.171 x .082 S), the longest and widest feet of the
     line on the only woman. Change to w 1.75, length 4.2, back 1.1.
   - `FORE` 4.6 -> 4.25 (upper to forearm 1.09 -> 1.18).

### Paladin, Radiant winged (`tools/gen_paladin.py`), S 44.75: the best figure of the five

1. **Pauldrons.** The top lames reach 37.86: 2.46 px over the shoulder joint and 1.11 above the chin. They are
   flat-topped shelves level with the gorget, 21.5 px across (.48 S; heroic plate is about .36).
   - In `pauldrons()`: P y `TOP - 1.55` -> `TOP - 3.0`.
   - PAULDRON radii and lengths x0.85 (r 3.15/2.95/2.72 -> 2.7/2.5/2.3; L 3.6/3.0/2.5 -> 3.1/2.55/2.1).
   - `SHOULDER_X` 6.4 -> 5.6 (joints .25 S). Gives about 17 px across (.38 S), still heroic.
2. **The forearm is too long** (8.0 against an upper arm of 8.5: 0.94, canon 0.78). Hanging, the wrist sits 2.8 px
   below canon and the fingertips reach the knee: ape arms.
   - `FORE` 8.0 -> 6.5, `UPPER` 8.5 -> 8.1.
   - The grip solve (`hold()`) then bends the elbows less for the same pommel, or the sword's grip comes up 1.5 px.
3. **Head x0.84** (8.0 -> 6.7 px; 18% -> 15% of S). The Sunforged human dial asks for "smaller head" on him.
   - In `helm()`: R 3.1 -> 2.6 and every hy offset x0.84; the crest the same. Halo pivot unchanged.
4. **Hips up 1.7 px.** `HIP` 22 -> 23.7, `TH` 10 -> 11.0, `SH` 10 -> 10.7. The ankle stays ~2.0. CUISSE and GREAVE
   follow because they are HIP/TH/SH relative.
   - PLATES y +1.7 with the waist band compressed 0.8, so TOP is 36.9 and the trunk shortens 13.4 -> 12.6.
5. **Head carriage.** In the bind profile the visor sits 2.1 px behind the chest's front and the helm's back sits
   0.5-1 px (the crest 1.7) behind the shoulder-blade line: the head is pulled back over the spine.
   - Head pivot z 0 -> -0.6; tilt the gorget `ngon` about 12 degrees with its top forward. The rest pose's
     head -14 then needs less bow.

Also:

- Keep the leg loft. It is the only leg of the five with a taper (1.96) and a calf (+0.6 px), and the model for the
  others.
- Ankle 2.32 -> 1.9 (canon 1.79).
- Chest to waist 10.8 / 6.6 = 1.64 is a superhero V. It is acceptable on the hero but at the edge.

## 6. The walk (all four walkers) and the Squire's run

The keys are in the `*UnitModel.setupAnim` classes, captured in `design/poses/*.json` walk_A to walk_D.

- **The thighs swing symmetrically:** +-31.5 degrees (Halberdier, `cos(ph) * 0.55`), +-34.4 (Levy), +-28.6 (Medic),
  +-25.8 (Paladin).
- **The knee flexes only during the forward swing** (`lift = max(0, sin(ph))`), so at both stride extremes both legs
  are straight. The rear leg is extended 26-31 degrees behind an upright torso with a straight knee: a compass or
  scissor walk, which is vanilla Minecraft's humanoid walk.
- **Fix, in DSL degrees** (mind the game's -x sign):
  - Bias the swing forward: about 28 degrees of flexion and 12-15 of extension, an amplitude of ~20 around +8.
  - Phase-lead the knee: `max(0, sin(ph + 0.6))`, so it is about 30-35 degrees flexed at toe-off and near straight
    at heel strike.
  - Add 8-10 degrees of stance flexion after contact.
  - The vertical bob exists (`body.y`, lowest at contact). It moves the trunk but not the thighs, which are root
    bones, so the hip joint slides 0.25 px against the pelvis. Move the thighs' y with it.
- **The Squire's run:** same pattern at +-43 degrees, with the shins near straight at the extremes. Keep both knees
  bent; see his fix 4.

## 7. Cross-unit inconsistencies

1. **Head size has no rule.** As read: Halberdier 22% of S, Squire 20%, Levy 19% (brim 31% wide), Paladin 18%,
   Medic 14% tall but 21% wide. The line soldier has a head 22% bigger than the hero's, relative to stature, and
   bigger than the young Squire's. The intent in art-theory section 7 is the reverse (small head = heroic, bigger =
   young).
2. **Face scale.** The implied skull as a share of stature is 10.9% (Medic), 13.0% (Squire) and 14.5% (Levy): the
   Medic's face is 25% smaller than the Levy's. The closed helms imply 21.5% (Halberdier) and 14.8% (Paladin).
3. **Headgear against skull.** The Squire's helm is 1.56x his skull's height, the Levy's coif and hat 1.33x, and the
   Medic's hood 1.94x her face in width. The closed helms are about 1.0-1.2x.
4. **Shoulder joint spacing** runs from .247 S (Squire) through .267 (Levy), .286 (Paladin) and .293 (Halberdier) to
   .299 (Medic). The female healer has the widest-set shoulders, the opposite of intent.
5. **Upper arm to forearm** is 1.06-1.10 on all five against a canon of 1.27. It is a shared habit (UPPER is about
   FORE in every generator), and the same fix applies to all.
6. **Legs.** The Paladin has a lofted, tapered leg with a calf; the four T1s have tubes (taper 0.81-1.09). Hip
   heights run .445 (Halberdier, Levy) to .492 (Paladin).
7. **Torso length** runs from .276 S (Squire) to .342 (Medic), against .288 canon. The Medic and Levy trunks are
   long, which shortens their legs further.
8. **Feet:** the lengths are consistent (.159-.171 S), but the widths run from .056 (Paladin) to .082 (Medic).

**Systemic fix: one rule rather than five sets of hand numbers.**

- Put a `FIGURE` table in `gen_models.py`, with per-role variants (soldier, young, hero, female): fractions of S for
  chin, shoulder, elbow, wrist, hip and knee; the upper-to-forearm ratio; girth fractions for thigh, knee, calf and
  ankle; and the shoulder-mass rule.
- Derive `HIP`, `SHOULDER_Y`, `UPPER`, `FORE` and the knee from it in every generator.
- Add a "proportions" section to `tools/limb_check.py`, so lovish's "look at human anatomy" gets a metric the way
  "the arms look bent" got the elbow check. It should warn when:
  - the head as read is over 18% of S (20% for "young");
  - the hip joint is below .50 S;
  - upper to forearm is below 1.18;
  - thigh-to-ankle taper is below 1.6;
  - the shoulder mass rises above the chin.

## 8. Feedback mapped to findings

- *"elbows don't match human anatomy"*: today fixed the joint's shape. Its position on the arm (52%, and 2-3 px above
  the belt) is still off; fix 5 on most bodies.
- *"fingers look like a block mesh"*: the hands are now the right size (1.0-1.16x canon), so not this pass.
- *"sleeker, more detail, and depth"* and *"the dawn paladin's leg looks really blocky from the side"*: the leg
  tubes, the missing calf and the straight-column trunks are what reads "blocky" and "no depth" in profile. The
  Paladin's loft is the fix to carry over.
- *"more realism ... look at human anatomy"*: the head-to-leg proportion is the one change that most moves the five
  from "Minecraft figure" toward "soldier", and the far reads show it costs nothing at RTS zoom.
