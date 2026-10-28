package com.solegendary.reignofnether.unit.modelling.animations;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * Client only. ONE articulated dragon rig for the winged wyrms - the Bone Dragon first; the Celestial and Thunder
 * dragons join by building a body on the same bones (tools/gen_dragon.py) and handing this class their
 * {@link Params}. lovish, Oct 21: animations "realistic, alive, and detailed"; dragons should "look like they're
 * breathing and move articulated" (The Isle / Day of Dragons are the feel).
 *
 * r13 (lovish after a match, Oct 22: "the dragon animation was bad"): rewritten to the phase tables in
 * design/anims/bone_dragon_flight.md. tools/dragon_poses.py is this file's Python twin (the same formulas and numbers,
 * drawn offline) - keep the two in step.
 *
 * Bones (DSL names; a body carries the ones it has, the rest are skipped - the Bone Dragon has no crest or ears, so its
 * idle ear flick is a no-op):
 * body (root) > ribs; body > neck_1..neck_N > head > jaw, horn_left/right, crest, ear_left/right;
 * body > spine_1 > spine_2 > tail_1..tail_M; body > front_{left,right}_upper > _lower > _foot;
 * spine_2 > hind_{left,right}_upper > _lower > _foot;
 * body > wing_{left,right}_arm > wing_*_fore > wing_*_hand > wing_*_finger_2..4;
 * the sail: mem_*_0 (the flank), mem_*_1 > 1b > 1c (under the arm), mem_*_2 > 2b > 2c (under the forearm) - three
 * chord strips each hinged on the one ahead - and web_*_2..5 > web_*_Nb between the fingers.
 *
 * Every frame, in order:
 * - the beat runs on a per-entity PHASE (in beats) that advances dt / the current beat length, so a hover's quicker
 *   beat or a take-off's never jumps the wings: a short hold at the top, a HEAVY downstroke (~34 % of the beat: it
 *   snaps off the top and loads) and a slow recovery (60 %) in which the wrist folds first, the elbow follows and the
 *   arm rises last - the wing comes up narrow; the elbow, the wrist and the fingers each trail the one before (3
 *   ticks, 0.15 beat, 0.08 beat more), so the tips are still rising as the downstroke begins;
 * - cruising, the beat comes in BURSTS: Params.burstBeats beats, then a glide of Params.glideBeats beat-lengths (the
 *   last recovery ends at the glide angle; the first beat after rises out of it); at speed the glide holds with one
 *   corrective beat every ~8 s; a hover, a climb, a take-off and a flare beat on;
 * - the sail: each strip billows UP behind the power stroke and cups DOWN behind the recovery (the load is the
 *   stroke's speed a tick or two late per strip, so the curl runs from the bone to the hem), the hem flutters on the
 *   recovery, a glide holds it lifted with a slow ripple running lead-to-hem and root-to-tip;
 * - the body rises on the downstroke a quarter beat late and sinks through the recovery, its nose up at the bottom;
 *   a climb, a hover, a take-off and a flare pitch it up; the bank (r12, {@link #bankStep}) rolls the WHOLE body;
 * - the neck (eight links): the heave lags down it (the base flexes as the body rises; the head counters it and
 *   stays level), a lateral wave about one wavelength long travels down it so from above it is always an S, the bank
 *   curves it into the turn toward the head (the head leads) while it counter-rolls;
 * - the tail (ten links): a slower lateral wave, its base swinging against the neck's (the counterweight), curled
 *   into a bank on two lagged copies of it (the rudder), streaming level in a glide, hanging low in a hover;
 * - the legs: tucked in flight, dangling in a hover and a take-off, reaching forward in a flare;
 * - breath always; idle (hovering) behaviours: a head shake, a yawn, a slow look round, a tail flick;
 * - the bite: the neck coils and the wings rise and hold through the wind-up, the neck uncoils as a downstroke lands;
 *   the hit flinch and the death fold from the entity's own state.
 *
 * All angles are in the concept DSL's vocabulary (degrees; x > 0 lifts a forward-pointing part's nose and drops a
 * backward one's tip; for the left wing z > 0 lifts the tip and y > 0 sweeps it forward; design/models/README.md)
 * and go onto the parts through {@link #dsl}. The right wing's y and z are mirrored.
 */
@OnlyIn(Dist.CLIENT)
public final class Dragon {

    /** What differs between dragons on the one rig. */
    public static final class Params {
        /** Ticks per wing beat; the hold at the top and the end of the downstroke, as shares of the beat. A big
         *  wyrm beats slowly; the power stroke is short and heavy, the recovery long (design/anims/bone_dragon_flight.md). */
        public float beatTicks = 52f, hold = 0.06f, down = 0.34f;
        /** The arm's angle at the top and the bottom of the stroke (degrees, left wing: + is up), and in a glide. */
        public float flapUp = 38f, flapDown = -42f, glideArm = 6f;
        /** The cruise rhythm: this many beats, then a glide this many beat-lengths long. */
        public float burstBeats = 3f, glideBeats = 2f;
        /** The beat length's factor while hovering / taking off. */
        public float hoverRate = 0.85f, takeoffRate = 0.8f;
        /** The horizontal speed (blocks per tick) where the speed glide begins / is full. */
        public float glideFrom = 0.16f, glideFull = 0.34f;
        /** The breath's period at rest (ticks); it quickens on the wing. */
        public float breathTicks = 90f;
        /** The chains: ticks of lag per link (the heave down the neck, the tail's vertical wave), the neck's lateral
         *  sway (deg per link) and the tail's lateral wave (deg per link at the root). */
        public float neckLag = 2.5f, tailLag = 5f, neckSway = 12f, tailWave = 6.5f;
        /** How far the body rises on a downstroke and drops through the top (px); the nose up at the bottom / down at the top (degrees).
         *  lovish (#145): "small heave, about 1/3 of now" - r13 bobbed ~a block a beat (10 / 6 px x1.4). */
        public float riseP = 3.3f, dropPx = 2f, pitchUp = 9f, pitchDown = 6f;
        /** The sail's billow (degrees per strip at the power stroke's peak speed). */
        public float billow = 13f;
        /** The bite clip's length (ticks) from the moment the entity's swing begins (the shot, for a breather). */
        public float attackTicks = 22f;
        /** The bite's wind-up (ticks): the neck coils, the wings rise and the jaw opens this long BEFORE the swing
         *  moment, when the swing can be foreseen (a breather's next shot: see animate's shotPeriod). */
        public float attackWindup = 8f;
        /** How often (ticks) the idle head shake and the yawn come round. */
        public float shakeEvery = 260f, yawnEvery = 210f;
        /** The bank: the roll's ceiling in flight (degrees), degrees of roll per degree-per-tick of yaw rate, the
         *  critically damped follow's natural rate (per tick: ~95% settled in 4.7 / bankOmega ticks), the heading
         *  filter's time constant (ticks: a snapped yaw becomes a turn this long), and the tail's lag per stage. */
        public float bankMax = 50f, bankPerDeg = 5f, bankOmega = 0.5f, headingTau = 5f, tailBankLag = 4f;
        /** How much of the flight bank the body keeps while hovering in place, and on the ground. */
        public float bankHover = 0.45f, bankGround = 0.12f;
        /** The climb: the vertical speed (blocks per tick) that counts as a full climb / dive. */
        public float climbSpeed = 0.06f;
        /** The take-off: how many ticks after it appears a dragon is still taking off. */
        public float takeoffTicks = 60f;
    }

    /** The resolved parts of one rig (null where the body has no such bone). */
    public static final class Rig {
        public final ModelPart body, ribs, head, jaw, hornL, hornR, crest, earL, earR, spine1, spine2;
        public final ModelPart[] neck, tail;
        /** [left, right]. */
        public final ModelPart[] arm = new ModelPart[2], fore = new ModelPart[2], hand = new ModelPart[2];
        /** [left, right][finger_2, finger_3, finger_4]. */
        public final ModelPart[][] finger = new ModelPart[2][3];
        /** The sail: [left, right][span: 0 under the arm, 1 under the fore][strip: 0 on the bone, 1 (b), 2 (c)]. */
        public final ModelPart[][][] mem = new ModelPart[2][2][3];
        /** The finger webs: [left, right][web_2..5] and their outer halves (the "b" bones). */
        public final ModelPart[][] web = new ModelPart[2][4], webB = new ModelPart[2][4];
        /** The body-bound strip from the shoulder back to the hip that bridges the arm sail to the flank. */
        public final ModelPart[] mem0 = new ModelPart[2];
        /** [front_left, front_right, hind_left, hind_right]. */
        public final ModelPart[] upper = new ModelPart[4], lower = new ModelPart[4], foot = new ModelPart[4];

        public Rig(Function<String, ModelPart> bone) {
            body = bone.apply("body");
            ribs = bone.apply("ribs");
            head = bone.apply("head");
            jaw = bone.apply("jaw");
            hornL = bone.apply("horn_left");
            hornR = bone.apply("horn_right");
            crest = bone.apply("crest");
            earL = bone.apply("ear_left");
            earR = bone.apply("ear_right");
            spine1 = bone.apply("spine_1");
            spine2 = bone.apply("spine_2");
            neck = chain(bone, "neck_");
            tail = chain(bone, "tail_");
            String[] sides = {"left", "right"};
            String[] strips = {"", "b", "c"};
            for (int i = 0; i < 2; i++) {
                arm[i] = bone.apply("wing_" + sides[i] + "_arm");
                fore[i] = bone.apply("wing_" + sides[i] + "_fore");
                hand[i] = bone.apply("wing_" + sides[i] + "_hand");
                for (int k = 0; k < 3; k++)
                    finger[i][k] = bone.apply("wing_" + sides[i] + "_finger_" + (k + 2));
                for (int k = 0; k < 4; k++) {
                    web[i][k] = bone.apply("web_" + sides[i] + "_" + (k + 2));
                    webB[i][k] = bone.apply("web_" + sides[i] + "_" + (k + 2) + "b");
                }
                mem0[i] = bone.apply("mem_" + sides[i] + "_0");
                for (int sp = 0; sp < 2; sp++)
                    for (int j = 0; j < 3; j++)
                        mem[i][sp][j] = bone.apply("mem_" + sides[i] + "_" + (sp + 1) + strips[j]);
            }
            String[] legs = {"front_left", "front_right", "hind_left", "hind_right"};
            for (int i = 0; i < 4; i++) {
                upper[i] = bone.apply(legs[i] + "_upper");
                lower[i] = bone.apply(legs[i] + "_lower");
                foot[i] = bone.apply(legs[i] + "_foot");
            }
        }

        private static ModelPart[] chain(Function<String, ModelPart> bone, String prefix) {
            int n = 0;
            while (n < 16 && bone.apply(prefix + (n + 1)) != null)
                n++;
            ModelPart[] parts = new ModelPart[n];
            for (int i = 0; i < n; i++)
                parts[i] = bone.apply(prefix + (i + 1));
            return parts;
        }
    }

    /** Per-entity smoothed state: [glide 0..1, bank deg, last age, the bite's SWING moment (age), last attackTime
     *  seen, hover, the last swing actually seen (age), whether the running bite was foreseen (1) or not (0),
     *  the filtered heading (deg), the bank's velocity (deg/tick), the tail's two lagged banks, heading-seeded flag,
     *  climb, the beat phase (beats), flare, take-off, the smoothed speed, the smoothed braking]. */
    private static final Map<LivingEntity, float[]> STATE = new WeakHashMap<>();
    public static final int ST_BANK = 1, ST_HEADING = 8, ST_BANK_VEL = 9, ST_TAIL_A = 10, ST_TAIL_B = 11, ST_SEEDED = 12,
        ST_CLIMB = 13, ST_PHASE = 14, ST_FLARE = 15, ST_TAKEOFF = 16, ST_SPEED = 17, ST_BRAKE = 18, ST_LEN = 19;

    /**
     * The bite's keys, in TICKS relative to the swing moment (0 = the fireball leaves / the blow lands): the wind-up
     * runs from -attackWindup to 0, the clip to attackTicks. The jaw is OPEN at the moment of the shot (r10), held
     * open ~6 ticks after it, then shut with a follow-through dip of the head.
     */
    private static final float[] KEY_T = {-8f, -2f, 0f, 6f, 10f, 22f};
    // {neck x (per link, spread by STRIKE), head x, jaw open, body forward px, body x, the wings (+ up to the top and
    //  held, - a downstroke landing with the bite)}
    private static final float[][] ATTACK = {
        {0, 0, 0, 0, 0, 0},              // at rest (the wind-up begins)
        {-5, -16, 30, -2.0f, 4, 1.0f},   // anticipation: the neck coils back, the head rears, the jaw opening, wings up
        {7, 10, 44, 4.0f, -4, -1.0f},    // the swing: the neck uncoils forward and down, the jaw at its gape, a downstroke
        {7, 16, 44, 4.0f, -4, -0.6f},    // the hold: open through the shot, the nose lifting so the RTS camera looks in
        {5, 8, 2, 3.0f, -2, 0f},         // the snap: the jaw shuts hard, the head dips through with it
        {0, 0, 0, 0, 0, 0},              // recovery
    };

    /** How the bite's neck key spreads down the links: the base rises, the far links drop, so the S unrolls forward. */
    private static final float[] STRIKE = {0.8f, 0.6f, 0.35f, 0.1f, -0.2f, -0.5f, -0.75f, -0.9f};
    /** The neck's lateral S: the wave's phase step per link (radians) - about one wavelength over eight links, so
     *  a travelling wave always leaves an S from above, never r12's straight rod. */
    private static final float NECK_K = 0.8f;
    /** How much of the billow each chord strip's hinge takes (they add: the hem curls the most). */
    private static final float[] STRIP_W = {0.45f, 0.8f, 1.0f};

    private Dragon() {}

    private static float frac(float p) {
        return p - (float) Math.floor(p);
    }

    /**
     * The stroke over one beat, -1 (top) .. 1 (bottom), p in beats (only its fraction counts): a short hold at the
     * top (a little more rise - the anticipation), the HEAVY downstroke (snaps off the top and loads), a slow recovery.
     */
    public static float shape(Params P, float p) {
        p = frac(p);
        if (p < P.hold)
            return -1f - 0.08f * Mth.sin(p / P.hold * (float) Math.PI);
        if (p < P.down) {
            float u = (p - P.hold) / (P.down - P.hold);
            return -1f + 2f * (u * u * (2.2f - 1.2f * u));     // eases off the top, strikes INTO the bottom
        }
        float u = (p - P.down) / (1f - P.down);
        return 1f - 2f * Life.smooth(u);
    }

    /** 0..1 through the recovery (the folds), 0 on the downstroke. */
    private static float upOf(Params P, float p) {
        p = frac(p);
        return p >= P.down ? Mth.sin((p - P.down) / (1f - P.down) * (float) Math.PI) : 0f;
    }

    /** 0..1 through the power stroke. */
    private static float downOf(Params P, float p) {
        p = frac(p);
        return p >= P.hold && p < P.down ? Mth.sin((p - P.hold) / (P.down - P.hold) * (float) Math.PI) : 0f;
    }

    /**
     * The cruise's glide weight at beat phase ph: burstBeats beats of flapping, then a glide glideBeats beat-lengths
     * long. It fades in over the last recovery (the wing comes up only to the glide angle) and out over the last 0.3
     * beat (the arm rises from the glide to the top: the anticipation of the first beat).
     */
    public static float rhythm(Params P, float ph) {
        float B = P.burstBeats, G = P.glideBeats, cyc = B + G;
        float c = ph - (float) Math.floor(ph / cyc) * cyc;
        if (c < B - 0.45f)
            return 0f;
        if (c < B)
            return Life.smooth((c - (B - 0.45f)) / 0.45f);
        if (c < B + G - 0.3f)
            return 1f;
        return 1f - Life.smooth((c - (B + G - 0.3f)) / 0.3f);
    }

    /** A DSL rotation (degrees, y up, facing -z) added to a part (MC: -x, +y, -z radians). */
    public static void dsl(ModelPart p, float x, float y, float z) {
        if (p == null)
            return;
        p.xRot -= x * Mth.DEG_TO_RAD;
        p.yRot += y * Mth.DEG_TO_RAD;
        p.zRot -= z * Mth.DEG_TO_RAD;
    }

    /** A move in px (DSL axes: y up) on a part's offset. */
    public static void move(ModelPart p, float x, float y, float z) {
        if (p == null)
            return;
        p.x += x;
        p.y -= y;
        p.z += z;
    }

    /**
     * Whether the dragon is taking off: just raised and still slow, or climbing hard from (nearly) a standstill. The
     * age alone is not enough - a client's tickCount starts at 0 whenever the entity comes into tracking range, so a
     * dragon flying into view used to play the take-off (legs dangling, body pitched up 25) for three seconds in
     * mid-cruise. climbWant -1..1 (a full climb is Params.climbSpeed), speed in blocks per tick.
     */
    public static boolean wantsTakeoff(Params P, int tickCount, float speed, float climbWant) {
        return (tickCount < P.takeoffTicks && speed < 0.08f) || (climbWant > 0.5f && speed < 0.08f);
    }

    /**
     * Whether the wings flare (held forward as air brakes): while the dragon BRAKES at mid speed (DragonFlight's ramp
     * into its point: brake in blocks per tick per tick), or just stopped with the hover still coming in. r13 only had
     * the second, so the flare came after the braking it is meant to show.
     */
    public static boolean wantsFlare(float speed, float brake, float hover) {
        return (brake > 0.002f && speed < 0.13f && speed > 0.02f) || (speed < 0.02f && hover < 0.8f);
    }

    /** A fresh per-entity state (see STATE). */
    public static float[] newState(float age) {
        float[] st = new float[ST_LEN];
        st[2] = age;
        st[3] = -1000f;
        st[5] = 1f;
        st[6] = -1000f;
        return st;
    }

    /**
     * One step of the bank (r12). The heading filter: st[ST_HEADING] follows the entity's body yaw with an exponential
     * lag of P.headingTau ticks, so a yaw that the server set in one tick (GhastUnit.updateRotation snaps it to the
     * target) still reads as a turn a few ticks long, and its rate is the yaw rate the roll answers. The roll wants
     * P.bankPerDeg degrees per degree-per-tick of that rate, clamped to +-P.bankMax and scaled (less hovering, next to
     * none on the ground), and FOLLOWS that want as a critically damped spring (rate P.bankOmega): no overshoot, the
     * roll's velocity continuous through the start and the end of a turn - no snap at either. The tail's two lagged
     * copies (ST_TAIL_A, ST_TAIL_B) trail the bank by P.tailBankLag ticks each for the counter-swing down the chain.
     * Sign: MC yaw grows clockwise from above (a RIGHT turn), and DSL z > 0 lifts the +x (left) side, so bank > 0 is a
     * right bank: the right wing drops into a right turn. Call with dt in ticks (0 is a no-op); yaw in degrees.
     */
    public static void bankStep(float[] st, Params P, float yaw, float dt, float scale) {
        if (st[ST_SEEDED] == 0f) {
            st[ST_HEADING] = yaw;
            st[ST_SEEDED] = 1f;
        }
        if (dt <= 0f)
            return;
        float d = Mth.wrapDegrees(yaw - st[ST_HEADING]);
        float a = 1f - (float) Math.exp(-dt / P.headingTau);
        st[ST_HEADING] = Mth.wrapDegrees(st[ST_HEADING] + d * a);
        float rate = d * a / dt;                                               // deg per tick, this frame
        float want = Mth.clamp(rate * P.bankPerDeg, -P.bankMax, P.bankMax) * scale;
        int n = Math.max(1, (int) Math.ceil(dt / 0.5f));                     // sub-steps keep the spring stable
        float h = dt / n, w = P.bankOmega;
        float x = st[ST_BANK], v = st[ST_BANK_VEL];
        for (int i = 0; i < n; i++) {
            v += (w * w * (want - x) - 2f * w * v) * h;
            x += v * h;
        }
        st[ST_BANK] = x;
        st[ST_BANK_VEL] = v;
        float b = 1f - (float) Math.exp(-dt / P.tailBankLag);
        st[ST_TAIL_A] += (x - st[ST_TAIL_A]) * b;
        st[ST_TAIL_B] += (st[ST_TAIL_A] - st[ST_TAIL_B]) * b;
    }

    /**
     * Pose the rig for this frame, on top of the model's rest pose (call restPose() first). The entity's own state
     * drives everything: its speed over the ground, its turn rate, its climb, its swing (attackTime), hurtTime,
     * deathTime, age and id.
     */
    public static void animate(Rig r, Params P, LivingEntity e, float age, float netHeadYaw, float headPitch,
                               float attackTime) {
        animate(r, P, e, age, netHeadYaw, headPitch, attackTime, 0f);
    }

    /**
     * As above, with the swing FORESEEN: shotPeriod > 0 is the ticks between this entity's swings while it keeps
     * attacking (a breather's attack cooldown - deterministic on the server, and the attribute it comes from is
     * synced), so once one swing has been seen the next is expected shotPeriod later and the bite's wind-up starts
     * Params.attackWindup ticks BEFORE it. When the real swing arrives the clip re-syncs to it if the guess was off by
     * more than a few ticks; if no swing comes, the foreseen bite plays out once (a snarl at nothing) and nothing more
     * is foreseen until a real swing is seen again.
     */
    public static void animate(Rig r, Params P, LivingEntity e, float age, float netHeadYaw, float headPitch,
                               float attackTime, float shotPeriod) {
        int id = e.getId();
        float[] st = STATE.get(e);
        if (st == null) {
            STATE.put(e, st = newState(age));
            st[ST_PHASE] = Life.offset(id) / P.beatTicks;
            double dx0 = e.getX() - e.xo, dz0 = e.getZ() - e.zo;
            st[ST_SPEED] = (float) Math.sqrt(dx0 * dx0 + dz0 * dz0);
            st[ST_TAKEOFF] = wantsTakeoff(P, e.tickCount, st[ST_SPEED], 0f) ? 1f : 0f;
        }
        float dt = Mth.clamp(age - st[2], 0f, 20f);
        st[2] = age;
        // speed over the ground from the client's own tick positions (a flier has no walk distance worth the name)
        double dx = e.getX() - e.xo, dz = e.getZ() - e.zo;
        float speed = (float) Math.sqrt(dx * dx + dz * dz);
        float wantGlide = Life.smooth((speed - P.glideFrom) / (P.glideFull - P.glideFrom));
        st[0] += (wantGlide - st[0]) * Math.min(1f, dt / (wantGlide > st[0] ? 12f : 25f));
        float glide = st[0];
        float wantHover = speed < 0.02f ? 1f : 0f;
        // the braking (blocks per tick per tick, smoothed: the speed only changes once a tick, the frames between see none)
        float lastSpeed = st[ST_SPEED];
        st[ST_SPEED] += (speed - st[ST_SPEED]) * Math.min(1f, dt / 3f);
        if (dt > 0f)
            st[ST_BRAKE] += ((lastSpeed - st[ST_SPEED]) / dt - st[ST_BRAKE]) * Math.min(1f, dt / 4f);
        // the flare: braking into a hover - the wings are air brakes while it slows, and until the hover has come in
        float wantFlare = wantsFlare(speed, st[ST_BRAKE], st[5]) ? 1f : 0f;
        st[ST_FLARE] += (wantFlare - st[ST_FLARE]) * Math.min(1f, dt / (wantFlare > st[ST_FLARE] ? 5f : 14f));
        st[5] += (wantHover - st[5]) * Math.min(1f, dt / 25f);
        float idle = st[5];
        // the climb / dive, -1..1 (r12): from the client's own vertical speed, smoothed
        float wantClimb = Mth.clamp((float) (e.getY() - e.yo) / P.climbSpeed, -1f, 1f);
        st[ST_CLIMB] += (wantClimb - st[ST_CLIMB]) * Math.min(1f, dt / 10f);
        float climb = st[ST_CLIMB];
        // the take-off: just appeared, or climbing hard from (nearly) a standstill
        float wantTakeoff = wantsTakeoff(P, e.tickCount, speed, wantClimb) ? 1f : 0f;
        st[ST_TAKEOFF] += (wantTakeoff - st[ST_TAKEOFF]) * Math.min(1f, dt / (wantTakeoff > st[ST_TAKEOFF] ? 6f : 20f));
        float takeoff = st[ST_TAKEOFF];
        // the beat's phase advances at the CURRENT rate (a hover beats quicker, a take-off quicker still)
        float T = P.beatTicks * (1f + (P.hoverRate - 1f) * idle) * (1f + (P.takeoffRate - 1f) * takeoff);
        st[ST_PHASE] += dt / T;
        if (st[ST_PHASE] > 4096f)
            st[ST_PHASE] -= 4096f - 4096f % (P.burstBeats + P.glideBeats);   // keep float precision; same rhythm
        // the bank: the body rolls into a turn (r12, lovish: "tilt sideways when turning")
        float bankScale = e.onGround() ? P.bankGround : Mth.lerp(idle, 1f, P.bankHover);
        float yawNow = Mth.rotLerp(age - (float) Math.floor(age), e.yBodyRotO, e.yBodyRot);
        bankStep(st, P, yawNow, dt, bankScale);
        float bank = st[ST_BANK];
        if (attackTime > 0f && (st[4] <= 0f || attackTime < st[4])) {
            // the swing began this frame: start our own, slower clip - or, if a foreseen bite is already running
            // within a few ticks of this moment, let it carry on (its wind-up was the point)
            if (!(st[7] > 0f && Math.abs(age - st[3]) <= 4f))
                st[3] = age;
            st[6] = age;
            st[7] = 0f;
        } else if (shotPeriod > 0f && st[6] > -999f && st[7] == 0f && age - st[3] >= P.attackTicks
                   && age >= st[6] + shotPeriod - P.attackWindup) {
            st[3] = st[6] + shotPeriod;               // the next swing is expected here: begin the wind-up now
            st[6] = -1000f;                           // ... and foresee no more until a real swing is seen
            st[7] = 1f;
        }
        st[4] = attackTime;

        pose(r, P, id, st[ST_PHASE], age, glide, idle, bank, st[ST_TAIL_A], st[ST_TAIL_B], climb, st[ST_FLARE], takeoff,
            age - st[3], e.hurtTime, e.deathTime, netHeadYaw, headPitch);
    }

    /**
     * The pose itself from the resolved drivers (tools/dragon_poses.py's pose() takes the same arguments): ph the beat
     * phase (beats), glide / idle (hover) / flare / takeoff 0..1, bank in degrees with the tail's two lagged banks,
     * climb -1..1, attackT the ticks since the bite's swing moment - a bite runs from -Params.attackWindup to
     * Params.attackTicks (outside that range: none).
     */
    public static void pose(Rig r, Params P, int id, float ph, float age, float glide, float idle, float bank,
                            float tailA, float tailB, float climb, float flare, float takeoff, float attackT,
                            int hurtTime, int deathTime, float netHeadYaw, float headPitch) {
        float off = Life.offset(id);
        float T = P.beatTicks * (1f + (P.hoverRate - 1f) * idle) * (1f + (P.takeoffRate - 1f) * takeoff);
        // the glide weight: the cruise rhythm (only cruising), and the speed's own glide with a corrective beat
        float cruise = (1f - idle) * (1f - Math.max(0f, climb)) * (1f - takeoff) * (1f - flare);
        float corrective = Life.bump(age, id, 6, 160f, 44f);
        float gs = glide * (1f - corrective);
        float gw0 = Math.max(rhythm(P, ph) * cruise, gs);
        float amp = (1f + 0.3f * Math.max(0f, climb)) * (1f + 0.25f * takeoff) * (1f - 0.15f * idle) * (1f - 0.35f * flare);
        float s0 = shape(P, ph);
        float bankF = Math.min(1f, Math.abs(bank) / P.bankMax);
        float rip = (age + off) / 26f * Mth.TWO_PI;                 // the glide's slow billow

        // the bite's wing override (wind-up: up to the top and held; strike: a downstroke lands with the bite)
        float atkW = 0f, atkArm = 0f;
        boolean biting = attackT >= -P.attackWindup && attackT < P.attackTicks;
        int bk = 0;
        float bf = 0f;
        if (biting) {
            float t = attackT < 0f ? attackT * (-KEY_T[0] / P.attackWindup) : attackT * (KEY_T[KEY_T.length - 1] / P.attackTicks);
            while (bk < KEY_T.length - 2 && t > KEY_T[bk + 1])
                bk++;
            bf = Life.smooth((t - KEY_T[bk]) / (KEY_T[bk + 1] - KEY_T[bk]));
            float wkey = key(5, bk, bf);
            atkW = Math.min(1f, Math.abs(wkey) * 1.5f) * 0.8f;
            atkArm = wkey > 0f ? P.flapUp : P.flapDown;
        }

        // ---- the wings ----------------------------------------------------------------------------------------------
        float phF = ph - 4f / T, phH = ph - 0.18f, phT = ph - 0.28f;
        float sA = s0, sF = shape(P, phF), sH = shape(P, phH), sT = shape(P, phT);
        float gF = gw(P, phF, cruise, gs), gH = gw(P, phH, cruise, gs), gT = gw(P, phT, cruise, gs);
        float up0 = upOf(P, ph), upF = upOf(P, phF), upH = upOf(P, phH), upT = upOf(P, phT), dn0 = downOf(P, ph);
        for (int i = 0; i < 2; i++) {
            float s = i == 0 ? 1f : -1f;                           // the right wing mirrors y and z
            float inner = s * bank < 0f ? bankF : 0f, outer = s * bank > 0f ? bankF : 0f;
            float a = amp * (1f - 0.30f * inner + 0.10f * outer);
            float flapZ = Mth.lerp((sA + 1f) * 0.5f, P.flapUp, P.flapDown) * a;
            float armZ = flapZ * (1f - gw0) + (P.glideArm + 4.0f * Mth.sin((age + off) / 50f)) * gw0;
            armZ = armZ * (1f - atkW) + atkArm * atkW;
            armZ += -16f * inner + 6f * outer + 16f * flare + 4f * gw0 * Mth.sin((age + off) / 70f + 0.6f) * s;
            // the stroke plane: cruising the arm sweeps back a little on the recovery; hovering it beats FORWARD and
            // down and comes back on the recovery; a flare holds the wings forward as brakes
            float armY = (-8f * up0 * (1f - idle) + idle * (18f * dn0 - 14f * up0)) * a * (1f - gw0) + 22f * flare;
            // the elbow bends against the stroke (it trails the arm by 3 ticks) and folds back through the recovery
            float foreZ = (40f * (sA - sF) - 7f * upF) * a * (1f - gF) - 3f * gF - 7f * inner + 4f * outer;
            float foreY = -22f * upF * a * (1f - gF) - 3f * gF - 22f * inner + 2f * outer + 10f * flare;
            // the wrist trails the elbow (the tips still rising as the downstroke begins), folds back on the recovery -
            // HALF a fold (lovish, #145: the wing stays broad from the RTS camera; r13's full tuck left sticks) - and
            // opens again at the top
            float handZ = (40f * (sF - sH) - 4f * upH) * a * (1f - gH) - (6f - 4f * Mth.sin((age + off) / 50f - 1.2f)) * gH - 5f * inner + 3f * outer + 6f * flare;
            float handY = -36f * upH * a * (1f - gH) - 5f * gH - 20f * inner + 3f * outer + 12f * flare;
            // the twist about the wing's long axis: pronation on the power stroke, supination on the recovery
            float foreX = -15f * (sA - sF) * a * (1f - gF);
            float handX = -20f * (sF - sH) * a * (1f - gH);
            dsl(r.arm[i], 0, s * armY, s * armZ + bank * 0.15f);
            dsl(r.fore[i], foreX, s * foreY, s * foreZ);
            dsl(r.hand[i], handX, s * handY, s * handZ);
            // the fingers: closed toward the leading one on the recovery (the latest of all), spread in a flare; each
            // trailing finger lags its tip a little more (the fan ripples open)
            for (int k = 0; k < 3; k++) {
                float spread = (9f + 8f * k) * upT * a * (1f - gT) - (4f + 3f * k) * flare;
                float lift = 12f * (sH - sT) * a * (1f - gT) * (1f + 0.4f * k);
                dsl(r.finger[i][k], 0, s * spread, s * lift);
            }
            // the sail: the strips of each span hinge on the one ahead (x > 0 tips a strip's far edge down)
            for (int sp = 0; sp < 2; sp++) {
                float lead = sp == 0 ? ph : phF, wbase = sp == 0 ? 0.9f : 1f;
                for (int j = 0; j < 3; j++) {
                    float p1 = lead - (1f + 1.5f * j) / T, p0 = lead - (3f + 1.5f * j) / T;
                    float v = (shape(P, p1) - shape(P, p0)) / 0.30f;      // ~ +1 at the power stroke's peak speed
                    float g = gw(P, p1, cruise, gs);
                    float x = (-P.billow * v * a * (1f - g) - 8f * g + 6f * g * Mth.sin(rip - 0.9f * j - 0.7f * sp)
                        + 7f * flare - 3f * (1f - g) * (1f - idle)) * STRIP_W[j] * wbase;
                    if (j == 2)                                            // the hem flutters on the recovery
                        x += 4f * upOf(P, p1) * (1f - g) * Mth.sin((age + off) * 1.9f + j + sp);
                    dsl(r.mem[i][sp][j], x, 0, 0);
                }
            }
            for (int k = 0; k < 4; k++) {
                float p1 = ph - 0.18f - (2f + k) / T, p0 = ph - 0.18f - (4f + k) / T;
                float v = (shape(P, p1) - shape(P, p0)) / 0.30f;
                float g = gw(P, p1, cruise, gs);
                dsl(r.web[i][k], -0.9f * P.billow * v * a * (1f - g) - 3f * g + 3f * g * Mth.sin(rip - 0.9f - 0.7f * (k + 2))
                    + 5f * flare, 0, 0);
                dsl(r.webB[i][k], -1.4f * P.billow * v * a * (1f - g) - 6f * g + 6f * g * Mth.sin(rip - 1.8f - 0.7f * (k + 2))
                    + 7f * flare + 4f * upOf(P, p1) * (1f - g) * Mth.sin((age + off) * 1.9f + 2.2f + 0.8f * k), 0, 0);
            }
            float v1 = (shape(P, ph - 2f / T) - shape(P, ph - 4f / T)) / 0.30f;
            dsl(r.mem0[i], -0.5f * P.billow * v1 * a * (1f - gw0) - 3f * gw0 + 3f * gw0 * Mth.sin(rip), 0, 0);
        }

        // ---- the body: rises on the downstroke a quarter beat late, sinks through the recovery, nose up at the
        // bottom; the states pitch it up; the bank rolls it (the wings add 0.15 of the bank on their own)
        float phB = ph - 4f / T;
        float heave = shape(P, phB) * amp * (1f - gw(P, phB, cruise, gs));
        move(r.body, 0, heave > 0f ? P.riseP * heave : P.dropPx * heave, 0);
        float pitch0 = (s0 > 0f ? P.pitchUp : P.pitchDown) * s0 * amp * (1f - gw0);
        float roll = bank * 0.85f + 4f * gw0 * Mth.sin((age + off) / 70f);   // a glide rocks: small roll corrections
        float statePitch = 10f * climb + 16f * idle + 25f * takeoff * (1f - idle) + 26f * flare + 2f * gw0;
        dsl(r.body, pitch0 + statePitch, 0, roll);

        // ---- breath, always: the ribcage swells, the neck base lifts, the head stays level -------------------------
        float br = Life.breath(age, id, P.breathTicks * (1f - 0.3f * glide));
        float swell = 0.5f + 0.5f * br;
        ModelPart ribs = r.ribs != null ? r.ribs : r.body;
        if (ribs != null) {
            ribs.xScale = 1f + 0.10f * swell;                      // big enough to read from the RTS camera
            ribs.yScale = 1f + 0.08f * swell;
            ribs.zScale = 1f + 0.02f * swell;
        }

        // ---- the neck: the heave lags down it, a lateral S travels down it, the bank curves it; the head counters --
        float neckX = 0f, neckY = 0f;
        int nn = r.neck.length;
        float neckRoll = nn > 0 ? -roll * 0.6f / nn : 0f;
        float t0 = (age + off) / 120f * Mth.TWO_PI;
        for (int k = 0; k < nn; k++) {
            float f = 1f - 0.06f * k;
            float pk = ph - P.neckLag * (k + 1) / T;
            float x = -2.2f * f * shape(P, pk) * amp * (1f - gw(P, pk, cruise, gs))
                + (k < 3 ? 2.0f * br - 0.3f * pitch0 + 3.0f * idle : -2.0f * idle)
                - 1.0f * gw0 - 0.6f * statePitch / nn * (k < 4 ? 1.6f : 0.4f);
            float y = P.neckSway * (1f - 0.4f * gw0) * Mth.sin(t0 - NECK_K * k) + bank * 0.20f * (k + 1) / nn;
            dsl(r.neck[k], x, y, neckRoll);
            neckX += x;
            neckY += y;
        }
        dsl(r.head, -0.65f * neckX, -0.85f * neckY, 0);           // the head leads and stays steady
        dsl(r.head, -0.6f * br - 8f * idle, bank * 0.18f, -roll * 0.3f);

        // ---- the tail: the heave a quarter beat late running down it, a slow lateral wave against the neck's, the
        // rudder in a bank; streams level in a glide, hangs low in a hover / climb / take-off / flare
        int nt = r.tail.length;
        float t1 = t0 * 120f / 150f + (float) Math.PI;
        float gTail = gw(P, ph - 0.25f, cruise, gs);
        float hangT = 1.8f * idle + 2.2f * flare + 1.5f * takeoff + 2.0f * Math.max(0f, climb);
        for (int k = 0; k < nt; k++) {
            float f = 0.5f + 0.08f * k;
            float x = 6.0f * f * shape(P, ph - 0.25f - P.tailLag * k / T) * amp * (1f - gTail)
                + 1.2f * Life.chain(age, id, 90f, k, 6f) * (1f - 0.5f * gw0)
                - 2.0f * gw0 * (k < 4 ? 1f : 0.5f)
                + hangT * (k < 4 ? 1.2f : 0.6f)
                - 0.12f * pitch0;
            float tb = k < nt / 2 ? tailA : tailB;
            float y = (P.tailWave + 0.4f * k) * (1f - 0.5f * gw0) * Mth.sin(t1 - 0.45f * k) - tb * 0.45f * (k + 1) / nt;
            dsl(r.tail[k], x, y, -roll * 0.05f);
        }
        dsl(r.spine1, -0.6f * shape(P, phF) * amp * (1f - gw0), 1.2f * Mth.sin(t1 + 0.45f), 0);
        dsl(r.spine2, -0.8f * shape(P, ph - 6f / T) * amp * (1f - gw0), 1.4f * Mth.sin(t1 + 0.2f), 0);

        // ---- the legs: tucked, trailing the heave; dangling in a hover / take-off, reaching forward in a flare ------
        float hang = Math.max(idle, Math.max(takeoff, flare));
        float d4 = shape(P, ph - 4f / T) * amp * (1f - gw0), d7 = shape(P, ph - 7f / T) * amp * (1f - gw0);
        float swingG = 4f * Life.wander(age, id, 160f) * gw0;
        for (int i = 0; i < 4; i++) {
            float sway = (4f * Life.wander(age - 9f * i, id, 70f) + 12f * shape(P, ph - (6f + 2f * i) / T)) * hang;
            float paw = 3f * Life.wander(age - 5f * i, id, 90f);
            if (i < 2) {
                dsl(r.upper[i], -4f * d4 - 6f * gw0 + swingG + 40f * hang + sway, 0, 0);
                dsl(r.lower[i], -5f * d7 - 70f * hang, 0, 0);
                dsl(r.foot[i], 6f * d4 + paw - 10f * hang, 0, 0);
            } else {
                dsl(r.upper[i], -4f * d4 + 6f * gw0 + swingG + 55f * hang + 15f * flare + sway, 0, 0);
                dsl(r.lower[i], 5f * d7 + 25f * hang, 0, 0);
                dsl(r.foot[i], 6f * d4 + paw - 55f * hang, 0, 0);
            }
        }

        // ---- idle behaviours (fade with speed) ------------------------------------------------------------------
        if (idle > 0.02f) {
            float shake = Life.shake(age, id, 2, P.shakeEvery, 16f, 2.5f) * idle;
            float shake1 = Life.shake(age - 1f, id, 2, P.shakeEvery, 16f, 2.5f) * idle;   // the neck a tick behind
            dsl(r.head, 0, 25f * shake, 6f * shake);
            for (int q = 1; q <= 3 && nn - q >= 0; q++)
                dsl(r.neck[nn - q], 0, (8f - 2f * q) * shake1, 0);
            float yawn = Life.bump(age, id, 1, P.yawnEvery, 34f) * idle;
            dsl(r.jaw, -50f * yawn, 0, 0);                                                 // the jaw wide
            dsl(r.head, 12f * yawn, 0, 0);                                                 // the head tips up
            float look = Life.wander(age, id, 200f) * idle;
            for (int k = nn / 2; k < nn; k++)
                dsl(r.neck[k], 3f * yawn, 3.5f * look, 0);                                // stretches, looks round
            dsl(r.head, 0, 6f * look, 0);
            float flick = Life.shake(age, id, 5, 170f, 24f, 1.5f) * idle;
            for (int k = nt / 2; k < nt; k++)
                dsl(r.tail[k], 0, 5f * flick, 0);
            dsl(r.earL, 0, 0, -20f * Life.bump(age, id, 3, 120f, 8f) * idle);
            dsl(r.earR, 0, 0, 20f * Life.bump(age, id, 4, 150f, 8f) * idle);
        }

        // ---- the look: the last links of the neck turn first, the head finishes it ---------------------------------
        float yaw = Mth.clamp(netHeadYaw, -55f, 55f), pitch = Mth.clamp(headPitch, -35f, 35f);
        for (int k = Math.max(0, nn - 4); k < nn; k++)
            dsl(r.neck[k], -pitch * 0.09f, -yaw * 0.11f, 0);
        dsl(r.head, -pitch * 0.45f, -yaw * 0.5f, 0);

        // ---- the bite ---------------------------------------------------------------------------------------------
        if (biting) {
            for (int i = 0; i < nn; i++)       // the base lifts, the far links drop: the S unrolls into a lunge
                dsl(r.neck[i], key(0, bk, bf) * STRIKE[Math.min(i, STRIKE.length - 1)], 0, 0);
            dsl(r.head, key(1, bk, bf), 0, 0);
            dsl(r.jaw, -key(2, bk, bf), 0, 0);
            move(r.body, 0, 0, -key(3, bk, bf));
            dsl(r.body, key(4, bk, bf), 0, 0);
        }

        // ---- the hit flinch: the head jerks away, the body rolls -----------------------------------------------------
        if (hurtTime > 0 && deathTime == 0) {
            float h = Mth.sin(hurtTime / 10f * (float) Math.PI);
            dsl(r.head, 4f * h, -14f * h, 6f * h);
            if (nn > 0)
                dsl(r.neck[0], -5f * h, -4f * h, 0);
            dsl(r.body, 0, 0, 5f * h);
        }

        // ---- the death fold: the wings crumple, the neck drops, the legs let go; the renderer lays it down ----------
        if (deathTime > 0) {
            float d = Life.smooth(Math.min(1f, (deathTime + 1f) / 14f));
            for (int i = 0; i < 2; i++) {
                float s = i == 0 ? 1f : -1f;
                dsl(r.arm[i], 0, 0, s * -30f * d);
                dsl(r.fore[i], 0, s * -60f * d, s * 20f * d);
                dsl(r.hand[i], 0, s * -70f * d, 0);
                for (int k = 0; k < 3; k++)
                    dsl(r.finger[i][k], 0, s * (20f + 10f * k) * d, 0);
            }
            for (int k = 0; k < nn; k++)
                dsl(r.neck[k], -6f * d, 2f * d, 0);
            dsl(r.head, 10f * d, 0, 0);
            dsl(r.jaw, -18f * d, 0, 0);
            for (int k = 0; k < nt; k++)
                dsl(r.tail[k], 2f * d, 0, 0);
            for (int i = 0; i < 4; i++) {
                boolean front = i < 2;
                dsl(r.upper[i], front ? 30f * d : -25f * d, 0, 0);
                dsl(r.lower[i], front ? -50f * d : 50f * d, 0, 0);
            }
        }
    }

    /** The glide weight at phase p (the rhythm while cruising, at least the speed's glide gs). */
    private static float gw(Params P, float p, float cruise, float gs) {
        return Math.max(rhythm(P, p) * cruise, gs);
    }

    private static float key(int col, int k, float f) {
        return Mth.lerp(f, ATTACK[k][col], ATTACK[k + 1][col]);
    }
}
