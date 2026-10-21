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
 * breathing and move articulated" (The Isle's creatures are the feel).
 *
 * Bones (DSL names; a body carries the ones it has, the rest are skipped):
 * body (root) > ribs; body > neck_1..neck_N > head > jaw, horn_left/right, crest, ear_left/right;
 * body > spine_1 > spine_2 > tail_1..tail_M; body > front_{left,right}_upper > _lower > _foot;
 * spine_2 > hind_{left,right}_upper > _lower > _foot;
 * body > wing_{left,right}_arm > wing_*_fore > wing_*_hand > wing_*_finger_2..4 (the membrane panels ride these).
 *
 * What it does every frame, in the animation standard's order:
 * - breath always: the ribcage alone swells (its own bone, so the neck and tail are not scaled), the neck lifts a
 *   little on the in-breath and the head stays level; slow and deep for a big body;
 * - the beat: a power downstroke and a quicker folded upstroke ({@link Life#wingBeat}); the forearm bends against the
 *   stroke a few ticks behind the arm, the hand behind that, and on the upstroke the elbow, the hand and then the
 *   fingers fold back in stages (arm first, the fore 2 ticks later, the hand 2 more, the fingers last) so the wing
 *   comes up narrow and opens again at the top; the body drops through the top of the stroke and rises on the
 *   downstroke, its nose dipping with it;
 * - at speed the beat fades into a glide: the wings held flat with a slight dihedral, the tail lifted to stream, the
 *   legs tucked tighter with a slow swing; at cruise the beat rests into a short glide every ~9 s, and a glide gets one
 *   corrective beat every ~8 s; a climb pitches the nose up, deepens the beat and drops the tail;
 * - the bank (r12, lovish: "tilt sideways when turning"): the body rolls into a turn in proportion to its yaw rate
 *   ({@link #bankStep}: a filtered heading, a critically damped roll to +-Params.bankMax, less hovering, next to none
 *   on the ground); the wings roll with it and the inner wing beats lower, shorter and part-folded while the outer
 *   beats higher and held out; the neck counter-rolls so the skull stays near level and yaws into the turn so the head
 *   leads; the tail curls into the turn (a rudder) on two lagged copies of the bank, so it trails and settles after
 *   the body;
 * - the neck and the tail are chains: a wave travels down them a couple of ticks per link (the neck's a nod, the
 *   tail's lateral), and the heave of the beat lags down them too; the head counters what the neck does so it leads
 *   and stays steady;
 * - the legs hang tucked (knees and ankles folded, the hind feet under the tail base) and trail a beat behind the
 *   body on the downstroke;
 * - idle, offset per entity: a head shake (the neck a tick behind), a yawn (the jaw wide, the head up, the neck
 *   stretched), a slow look around, a tail flick; the wings never take part in an idle;
 * - a bite (the swing): the neck extends forward over the first keys, the jaw gapes then snaps; a hit flinch and the
 *   death fold from the entity's own state.
 *
 * All angles here are in the concept DSL's vocabulary (degrees; x > 0 lifts a forward-pointing part's nose and drops
 * a backward one's tip; for the left wing z > 0 lifts the tip and y > 0 sweeps it forward; design/models/README.md)
 * and go onto the parts through {@link #dsl}, so a pose keyed here can be drawn by tools/pose_preview.py with the
 * same numbers (tools/dragon_poses.py is this file's Python twin). The right wing's y and z are mirrored.
 */
@OnlyIn(Dist.CLIENT)
public final class Dragon {

    /** What differs between dragons on the one rig. */
    public static final class Params {
        /** Ticks per wing beat and the downstroke's share of it. A big wyrm beats slowly. */
        public float beatTicks = 36f, down = 0.64f;
        /** The arm's angle at the top and the bottom of the stroke (degrees, left wing: + is up). */
        public float flapUp = 36f, flapDown = -46f;   // r12: deeper, so the beat reads from the RTS camera
        /** The glide: the arm's held angle, and the horizontal speed (blocks per tick) where the glide begins / is full. */
        public float glideArm = 7f, glideFrom = 0.16f, glideFull = 0.34f;   // r12: cruise (~0.15 at speed 0.3) keeps the beat; the glides are the rest bumps
        /** The breath's period at rest (ticks); it quickens on the wing. */
        public float breathTicks = 90f;
        /** The chain lag: ticks per link down the neck and the tail. */
        public float neckLag = 2f, tailLag = 3f;
        /** The chains' travelling waves: degrees per link (the neck nods, the tail sways sideways). */
        public float neckWave = 6f, tailWave = 7f;
        /** How far the body rises on a downstroke and drops through the top (px); the nose up at the bottom / down at the top (degrees). */
        public float riseP = 9f, dropPx = 6f, pitchUp = 10f, pitchDown = 6f;
        /** The bite clip's length (ticks) from the moment the entity's swing begins (the shot, for a breather). */
        public float attackTicks = 16f;
        /** The bite's wind-up (ticks): the head rears back and the jaw starts to open this long BEFORE the swing
         *  moment, when the swing can be foreseen (a breather's next shot: see animate's shotPeriod). */
        public float attackWindup = 6f;
        /** How often (ticks) the idle head shake and the yawn come round. */
        public float shakeEvery = 260f, yawnEvery = 210f;
        /** The bank: the roll's ceiling in flight (degrees), degrees of roll per degree-per-tick of yaw rate, the
         *  critically damped follow's natural rate (per tick: ~95% settled in 4.7 / bankOmega ticks), the heading
         *  filter's time constant (ticks: a snapped yaw becomes a turn this long), and the tail's lag per stage. */
        public float bankMax = 30f, bankPerDeg = 5f, bankOmega = 0.5f, headingTau = 5f, tailBankLag = 4f;
        /** How much of the flight bank the body keeps while hovering in place, and on the ground. */
        public float bankHover = 0.45f, bankGround = 0.12f;
        /** The climb: the vertical speed (blocks per tick) that counts as a full climb / dive. */
        public float climbSpeed = 0.06f;
    }

    /** The resolved parts of one rig (null where the body has no such bone). */
    public static final class Rig {
        public final ModelPart body, ribs, head, jaw, hornL, hornR, crest, earL, earR, spine1, spine2;
        public final ModelPart[] neck, tail;
        /** [left, right]. */
        public final ModelPart[] arm = new ModelPart[2], fore = new ModelPart[2], hand = new ModelPart[2];
        /** [left, right][finger_2, finger_3, finger_4]. */
        public final ModelPart[][] finger = new ModelPart[2][3];
        /** The sail: [left, right][mem_1 (under the arm), mem_2 (under the fore)] and [left, right][web_2..4]. */
        public final ModelPart[][] mem = new ModelPart[2][2], web = new ModelPart[2][4];
        /** The body-bound strip from the shoulder back to the hip that bridges the arm sail to the flank. */
        public final ModelPart[] mem0 = new ModelPart[2];
        /** The outer chord strip of each span / web, hinged half way back (null on a body without them). */
        public final ModelPart[][] memB = new ModelPart[2][2], webB = new ModelPart[2][4];
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
                mem[i][0] = bone.apply("mem_" + sides[i] + "_1");
                mem[i][1] = bone.apply("mem_" + sides[i] + "_2");
                memB[i][0] = bone.apply("mem_" + sides[i] + "_1b");
                memB[i][1] = bone.apply("mem_" + sides[i] + "_2b");
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
     *  the filtered heading (deg), the bank's velocity (deg/tick), the tail's two lagged banks, heading-seeded flag]. */
    private static final Map<LivingEntity, float[]> STATE = new WeakHashMap<>();
    public static final int ST_BANK = 1, ST_HEADING = 8, ST_BANK_VEL = 9, ST_TAIL_A = 10, ST_TAIL_B = 11, ST_SEEDED = 12,
        ST_CLIMB = 13, ST_LEN = 14;

    /**
     * The bite's keys, in TICKS relative to the swing moment (0 = the fireball leaves / the blow lands): the wind-up
     * runs from -attackWindup to 0, the clip to attackTicks. r10 (the r9 eyes tour: no open jaw in any frame - the
     * clip only began when the fireball appeared, and the sprite covered the head): the jaw is OPEN at the moment of
     * the shot, held open ~6 ticks after it, then shut with a follow-through dip of the head.
     */
    private static final float[] KEY_T = {-6f, -1f, 0f, 6f, 10f, 22f};
    // {neck x (each link), head x, jaw open, body forward px, body x}
    private static final float[][] ATTACK = {
        {0, 0, 0, 0, 0},                // at rest (the wind-up begins)
        {-4, -14, 32, -1.5f, 2},        // anticipation: the neck draws back, the head rears up, the jaw already opening
        {6, 10, 44, 4.0f, -3},          // the swing: the neck extends ~30 forward and down over the links, the jaw at
                                        // its gape (44 deg, the review's 35-45; the mouth's interior is painted black)
        {6, 16, 44, 4.0f, -3},          // the hold: open through the shot, the nose lifting so the RTS camera looks
                                        // into the mouth
        {5, 8, 2, 3.0f, -2},            // the snap: the jaw shuts hard, the head dips through with it
        {0, 0, 0, 0, 0},                // recovery
    };

    /** How the bite's neck key spreads down the links: the base rises, the far links drop, so the S unrolls forward. */
    private static final float[] STRIKE = {1.0f, 0.6f, 0f, -0.9f, -1.3f};

    /** The hold at the top of the stroke, as a share of the beat (the power stroke runs from here to Params.down). */
    private static final float HOLD = 0.08f;

    /**
     * The stroke, -1 (top) .. 1 (bottom): a brief pause at the top (with a little anticipation rise), a downstroke
     * that snaps off the top and then loads - the long, heavy power phase, ~55% of the beat - and a quicker
     * recovery - not a sine and not a symmetric smoothstep ({@link Life#wingBeat} is the symmetric one the other
     * fliers use).
     */
    public static float stroke(float age, int id, float periodTicks, float down) {
        float p = ((age + Life.offset(id)) / periodTicks) % 1f;
        if (p < 0f)
            p += 1f;
        if (p < HOLD)                                                     // the hold is not dead: a slow extra rise,
            return -1f - 0.10f * Mth.sin(p / HOLD * (float) Math.PI);     // the anticipation before the snap
        if (p < down) {
            float u = (p - HOLD) / (down - HOLD);
            return -1f + 2f * (1f - (float) Math.pow(1f - u, 2.4));         // snaps off the top, then loads: the long heavy part
        }
        float u = (p - down) / (1f - down);
        return 1f - 2f * Life.smooth(u);
    }

    private Dragon() {}

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

    private static float key(int col, int k, float f) {
        return Mth.lerp(f, ATTACK[k][col], ATTACK[k + 1][col]);
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
     * drives everything: its speed over the ground, its turn rate, its swing (attackTime), hurtTime, deathTime, age
     * and id.
     */
    public static void animate(Rig r, Params P, LivingEntity e, float age, float netHeadYaw, float headPitch,
                               float attackTime) {
        animate(r, P, e, age, netHeadYaw, headPitch, attackTime, 0f);
    }

    /**
     * As above, with the swing FORESEEN: shotPeriod > 0 is the ticks between this entity's swings while it keeps
     * attacking (a breather's attack cooldown - deterministic on the server, and the attribute it comes from is
     * synced), so once one swing has been seen the next is expected shotPeriod later and the bite's wind-up (the
     * head rearing back, the jaw opening) starts Params.attackWindup ticks BEFORE it. When the real swing arrives
     * the clip re-syncs to it if the guess was off by more than a couple of ticks; if no swing comes, the foreseen
     * bite plays out once (a snarl at nothing) and nothing more is foreseen until a real swing is seen again.
     */
    public static void animate(Rig r, Params P, LivingEntity e, float age, float netHeadYaw, float headPitch,
                               float attackTime, float shotPeriod) {
        int id = e.getId();
        float[] st = STATE.get(e);
        if (st == null)
            STATE.put(e, st = newState(age));
        float dt = Mth.clamp(age - st[2], 0f, 20f);
        st[2] = age;
        // speed over the ground from the client's own tick positions (a flier has no walk distance worth the name)
        double dx = e.getX() - e.xo, dz = e.getZ() - e.zo;
        float speed = (float) Math.sqrt(dx * dx + dz * dz);
        float wantGlide = Life.smooth((speed - P.glideFrom) / (P.glideFull - P.glideFrom));
        st[0] += (wantGlide - st[0]) * Math.min(1f, dt / (wantGlide > st[0] ? 12f : 25f));
        float glide = st[0];
        float wantHover = speed < 0.02f ? 1f : 0f;
        st[5] += (wantHover - st[5]) * Math.min(1f, dt / 15f);
        float idle = st[5];
        // the climb / dive, -1..1 (r12): from the client's own vertical speed, smoothed; it pitches the body, deepens
        // the beat on the way up and drops the tail
        float wantClimb = Mth.clamp((float) (e.getY() - e.yo) / P.climbSpeed, -1f, 1f);
        st[ST_CLIMB] += (wantClimb - st[ST_CLIMB]) * Math.min(1f, dt / 10f);
        float climb = st[ST_CLIMB];
        // the bank: the body rolls into a turn (r12, lovish: "tilt sideways when turning"); the heading is filtered
        // so a yaw the server snapped reads as a turn, and the roll follows its rate critically damped
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

        pose(r, P, id, age, glide, idle, bank, st[ST_TAIL_A], st[ST_TAIL_B], climb, age - st[3], e.hurtTime, e.deathTime,
            netHeadYaw, headPitch);
    }

    /**
     * The pose itself from the resolved drivers (the Python twin calls this with the same arguments): glide and idle
     * 0..1, bank in degrees, attackT the ticks since the bite's swing moment - a bite runs from -Params.attackWindup
     * to Params.attackTicks (outside that range: none).
     */
    public static void pose(Rig r, Params P, int id, float age, float glide, float idle, float bank, float attackT,
                            int hurtTime, int deathTime, float netHeadYaw, float headPitch) {
        pose(r, P, id, age, glide, idle, bank, bank, bank, 0f, attackT, hurtTime, deathTime, netHeadYaw, headPitch);
    }

    /** As above with the tail's two lagged banks (tailA trails bank, tailB trails tailA: the counter-swing's wave)
     *  and the climb (-1 dive .. 1 climb). */
    public static void pose(Rig r, Params P, int id, float age, float glide, float idle, float bank, float tailA,
                            float tailB, float climb, float attackT, int hurtTime, int deathTime, float netHeadYaw,
                            float headPitch) {
        // ---- the beat -----------------------------------------------------------------------------------------------
        float T = P.beatTicks, D = P.down;
        float beat = stroke(age, id, T, D);                 // -1 top .. 1 bottom
        float beat3 = stroke(age - 3f, id, T, D);                  // the elbow, 3 ticks behind the shoulder
        float beat6 = stroke(age - 6f, id, T, D);
        float lagH = T * 0.25f;                                    // the wrist a quarter cycle behind the elbow
        float beatH = stroke(age - lagH, id, T, D);
        float beatH3 = stroke(age - lagH - 3f, id, T, D);
        float up0 = Life.upstroke(age, id, T, D);                  // 0..1 through the recovery: the arm's fold
        float up2 = Life.upstroke(age - 2f, id, T, D);             // the fore, 2 ticks later
        float upH = Life.upstroke(age - lagH, id, T, D);           // the hand, with its lag
        float upF = Life.upstroke(age - lagH - 2f, id, T, D);      // the fingers last
        // the glide stills the wings - but never dead: one corrective beat comes round every ~8 s (r12, the flight
        // reference: "glide: still wings, one corrective beat every few seconds"); a climb deepens the beat
        float corrective = Life.bump(age, id, 6, 160f, 40f);
        // ... and on the wing at cruise (not hovering) the beat rests now and then: a 2.5 s glide every ~9 s, the
        // wings held out (the reference's "glide between beats"); the glide and the hover never snap - bumps and
        // smoothed drivers only
        float rest = Life.bump(age, id, 7, 180f, 50f) * (1f - idle) * (1f - glide);
        float amp0 = (1f - 0.85f * glide * (1f - corrective)) * (1f - 0.75f * rest) * (1f + 0.25f * Math.max(0f, climb));
        glide = Math.min(1f, glide + 0.6f * rest);                 // the rest holds the wings as a glide does
        float stroke = (beat + 1f) * 0.5f;
        // the bank's share, 0..1, for the wing on the INSIDE of the turn (it drops: bank > 0 is a right bank and the
        // right wing is the inner one) and for the outer wing (it rises)
        float bankF = Math.min(1f, Math.abs(bank) / P.bankMax);
        for (int i = 0; i < 2; i++) {
            float s = i == 0 ? 1f : -1f;                           // the right wing mirrors y and z
            float inner = s * bank < 0f ? bankF : 0f, outer = s * bank > 0f ? bankF : 0f;
            // in a bank the inner wing beats lower and shorter, the outer higher and a little longer (r12)
            float amp = amp0 * (1f - 0.30f * inner + 0.10f * outer);
            float armZ = Mth.lerp(stroke, P.flapUp, P.flapDown) * amp + P.glideArm * glide
                + 2.5f * glide * Mth.sin((age + Life.offset(id)) / 50f)           // a glide is never dead still
                - 9f * inner + 6f * outer;
            float armY = -8f * up0 * amp;                                           // the arm sweeps back a little on the recovery
            // the forearm bends against the stroke (up on the way down, down on the way up), the hand more and later
            float foreZ = 55f * (beat - beat3) * amp - 16f * up2 * amp - 3f * glide - 7f * inner + 4f * outer;
            // the wrist trails the elbow by a quarter cycle: the hand is still driving down as the elbow turns up
            float handZ = 60f * (beatH - beatH3) * amp - 10f * upH * amp - 5f * glide - 5f * inner + 3f * outer;
            // the twist about the wing's long axis: the leading edge turns down through the power stroke (pronation)
            // and up through the recovery (supination), the hand on its own lag; x is not mirrored
            float foreX = -34f * (beat - beat3) * amp;
            float handX = -45f * (beatH - beatH3) * amp;
            // the folds: the elbow, the hand and then the fingers sweep back on the upstroke so the wing comes up
            // narrow; they open again at the top. The inner wing of a bank folds a little (elbow and wrist swept
            // back), the outer wing is held out
            float foreY = -35f * up2 * amp - 3f * glide - 12f * inner + 2f * outer;
            float handY = -60f * upH * amp - 6f * glide - 10f * inner + 3f * outer;
            // the wings roll with the body (they are its children) and a touch further: the dihedral tips into the turn
            dsl(r.arm[i], 0, s * armY, s * armZ + bank * 0.15f);
            dsl(r.fore[i], foreX, s * foreY, s * foreZ);
            dsl(r.hand[i], handX, s * handY, s * handZ);
            // the sail fills and empties: each span's trailing edge lags its leading-edge bone by 2-3 ticks, so it
            // billows up behind the power stroke and sags behind the recovery (x > 0 tips a +z panel's far edge down)
            // Each span is two chord strips: the inner lags its bone, the outer lags the inner again and carries a
            // belly term with the stroke itself, so at the bottom the sail bellies down (~20% of the chord) and at
            // the top it cups up. The hand's webs are driven from the wrist, on its lag, not from the shoulder.
            float fill1 = stroke(age - 2f, id, T, D) - beat, fill1b = stroke(age - 4f, id, T, D) - stroke(age - 2f, id, T, D);
            float fill2 = stroke(age - 3f, id, T, D) - beat3, fill2b = stroke(age - 6f, id, T, D) - stroke(age - 3f, id, T, D);
            float fill3 = stroke(age - lagH - 3f, id, T, D) - beatH, fill3b = stroke(age - lagH - 6f, id, T, D) - stroke(age - lagH - 3f, id, T, D);
            float belly = 9f * beat * amp, bellyH = 9f * beatH * amp;
            // the flutter: a small quick ripple on the trailing strips through the recovery, each strip off-phase
            float flut = 4f * up0 * amp, w0 = (age + Life.offset(id)) * 1.9f;
            dsl(r.mem[i][0], 28f * fill1 * amp + 0.5f * belly - 4f * glide, 0, 0);
            dsl(r.memB[i][0], 30f * fill1b * amp + belly + flut * Mth.sin(w0), 0, 0);
            dsl(r.mem[i][1], 34f * fill2 * amp + 0.5f * belly - 5f * glide, 0, 0);
            dsl(r.memB[i][1], 34f * fill2b * amp + belly + flut * Mth.sin(w0 + 1.1f), 0, 0);
            for (int k = 0; k < 4; k++) {
                dsl(r.web[i][k], 36f * fill3 * amp + 0.5f * bellyH - 5f * glide, 0, 0);
                dsl(r.webB[i][k], 36f * fill3b * amp + bellyH + flut * Mth.sin(w0 + 2.2f + 0.8f * k), 0, 0);
            }
            dsl(r.mem0[i], 14f * fill1 * amp + 0.4f * belly - 3f * glide, 0, 0);   // the flank strip follows the arm sail, damped
            for (int k = 0; k < 3; k++)                            // the trailing fingers close toward the leading one
                dsl(r.finger[i][k], 0, s * (18f + 14f * k) * upF * amp, 0);
        }
        // the body: dropped through the top of the stroke, carried up by the downstroke a few ticks late, its nose
        // dipping with the stroke; a glide pitches it nose-up a touch; the bank rolls it
        float amp = amp0;
        float heave = beat3 * amp;
        move(r.body, 0, heave > 0f ? P.riseP * heave : P.dropPx * heave, 0);
        // the body flies: nose up at the bottom of the stroke, down at the top; a glide holds it nose-up a touch
        float pitch0 = (beat > 0f ? P.pitchUp : P.pitchDown) * beat * amp;
        float roll = bank * 0.85f;                                 // the body's share of the bank (the wings add 0.15)
        dsl(r.body, pitch0 + 3f * glide + 10f * climb, 0, roll);   // a climb pitches it nose-up, a dive nose-down

        // ---- breath, always: the ribcage swells, the neck lifts, the head stays level --------------------------------
        float breathTicks = Mth.lerp(glide, P.breathTicks, P.breathTicks * 0.7f);
        float br = Life.breath(age, id, breathTicks);              // -1 out .. 1 in
        float swell = 0.5f + 0.5f * br;
        ModelPart ribs = r.ribs != null ? r.ribs : r.body;
        if (ribs != null) {
            ribs.xScale = 1f + 0.10f * swell;                      // big enough to read from the RTS camera
            ribs.yScale = 1f + 0.08f * swell;
            ribs.zScale = 1f + 0.02f * swell;
        }
        if (r.ribs == null)
            move(r.body, 0, 0.3f * br, 0);

        // ---- the neck: a nod travels down it two ticks a link, the heave lags down it; the head counters it all -----
        float neckX = 0f, neckY = 0f;
        int nn = r.neck.length;
        // the bank down the neck (r12): each link counter-rolls a share of the body's roll so the skull stays near
        // level (the head takes the rest), and yaws INTO the turn a little so the head leads it (DSL y > 0 turns a
        // forward part to the unit's right; bank > 0 is a right turn)
        float neckRoll = nn > 0 ? -roll * 0.6f / nn : 0f, neckLead = nn > 0 ? bank * 0.42f / nn : 0f;
        for (int k = 0; k < nn; k++) {
            float f = 1f - 0.1f * k;
            float x = -2.6f * f * stroke(age - P.neckLag * (k + 1), id, T, D) * amp          // the heave, lagging
                + P.neckWave * Life.chain(age, id, 64f, k, P.neckLag) * (1f - 0.5f * glide)           // the travelling nod
                + (k < 2 ? 2.5f * br : 0f)                                                            // the in-breath lifts the base
                - (k < 2 ? 0.35f * pitch0 : 0f)                                                       // counters the body's pitch (~4 over two links)
                - 1.2f * glide;                                                                       // stretched out in a glide
            float y = 2.4f * Life.chain(age, id, 110f, k, 7f) * (1f - 0.6f * glide);                  // a slow side sway
            dsl(r.neck[k], x, y + neckLead, neckRoll);
            neckX += x;
            neckY += y;
        }
        dsl(r.head, -0.85f * neckX, -0.85f * neckY, 0);           // the head leads and stays steady
        dsl(r.head, -0.6f * br, bank * 0.12f, -roll * 0.3f);      // ... looks a little further into the turn, and stays level

        // ---- the tail: a lateral wave three ticks a link, the heave lagging down it; it lifts to stream in a glide --
        int nt = r.tail.length;
        for (int k = 0; k < nt; k++) {
            float f = 0.55f + 0.1f * k;                            // the tip swings most
            float x = 5f * f * stroke(age - T * 0.25f - P.tailLag * k, id, T, D) * amp   // trails the heave a quarter beat, then link by link
                + 1.0f * Life.chain(age, id, 80f, k, 6f) * (1f - 0.5f * glide)
                - 2.5f * glide * (k < 3 ? 1f : 0.5f);                                        // lifts level to stream
            // the bank's rudder (r12, the flight reference: "the tail curls toward the turn side"): the tail curls
            // INTO the turn (a backward part's tip goes to the unit's right for y < 0, and bank > 0 is a right turn),
            // on the lagged banks so the curl travels down the chain a beat behind the body and settles after it;
            // the links also give back a share of the roll so the tail trails flatter than the body
            float tb = k < nt / 2 ? tailA : tailB;
            float y = (P.tailWave + 0.6f * k) * Life.chain(age, id, 72f, k, P.tailLag) * (1f - 0.6f * glide)   // the wave
                - tb * 0.28f * (k + 1) / nt;
            x -= 0.12f * pitch0;                                                               // counters the body's pitch
            x += 2.5f * climb * (k + 1) / nt;                                                  // the tail hangs low in a climb
            dsl(r.tail[k], x, y, -roll * 0.07f);
        }
        dsl(r.spine1, -0.6f * beat3 * amp, 1.0f * Life.chain(age, id, 72f, 0, 0f), 0);
        dsl(r.spine2, -0.8f * beat6 * amp, 1.4f * Life.chain(age, id, 72f, 1, P.tailLag), 0);

        // ---- the legs: tucked, trailing a beat behind the body on the stroke; a slow swing in a glide ----------------
        float dangle4 = stroke(age - 4f, id, T, D) * amp;
        float dangle7 = stroke(age - 7f, id, T, D) * amp;
        float dangle10 = stroke(age - 10f, id, T, D) * amp;
        float swingG = 5f * Life.wander(age, id, 160f) * glide;
        for (int i = 0; i < 4; i++) {
            boolean front = i < 2;
            float dir = front ? 1f : -1f;
            dsl(r.upper[i], -4f * dangle4 - 6f * glide * dir + swingG, 0, 0);              // trails back as the body rises
            dsl(r.lower[i], -5f * dangle7 * dir, 0, 0);                                    // the fold gives a little
            dsl(r.foot[i], 6f * dangle10 + 3f * Life.wander(age - 5f * i, id, 90f), 0, 0);
        }

        // ---- idle behaviours (fade with speed; the wings take no part) ---------------------------------------------
        if (idle > 0.02f) {
            float shake = Life.shake(age, id, 2, P.shakeEvery, 16f, 2.5f) * idle;
            float shake1 = Life.shake(age - 1f, id, 2, P.shakeEvery, 16f, 2.5f) * idle;   // the neck a tick behind
            dsl(r.head, 0, 25f * shake, 6f * shake);
            if (nn > 0)
                dsl(r.neck[nn - 1], 0, 8f * shake1, 0);
            if (nn > 1)
                dsl(r.neck[nn - 2], 0, 4f * shake1, 0);
            float yawn = Life.bump(age, id, 1, P.yawnEvery, 34f) * idle;
            dsl(r.jaw, -50f * yawn, 0, 0);                                                 // the jaw wide
            dsl(r.head, 12f * yawn, 0, 0);                                                 // the head tips up
            for (int k = nn / 2; k < nn; k++)
                dsl(r.neck[k], 4f * yawn, 0, 0);                                           // the neck stretches
            float look = Life.wander(age, id, 200f) * idle;
            for (int k = nn / 2; k < nn; k++)
                dsl(r.neck[k], 0, 5f * look, 0);
            dsl(r.head, 0, 6f * look, 0);
            float flick = Life.shake(age, id, 5, 170f, 24f, 1.5f) * idle;
            for (int k = nt / 2; k < nt; k++)
                dsl(r.tail[k], 0, 7f * flick, 0);
            dsl(r.earL, 0, 0, -20f * Life.bump(age, id, 3, 120f, 8f) * idle);
            dsl(r.earR, 0, 0, 20f * Life.bump(age, id, 4, 150f, 8f) * idle);
        }

        // ---- the look: the last links of the neck turn first, the head finishes it ---------------------------------
        float yaw = Mth.clamp(netHeadYaw, -55f, 55f), pitch = Mth.clamp(headPitch, -35f, 35f);
        for (int k = Math.max(0, nn - 3); k < nn; k++)
            dsl(r.neck[k], -pitch * 0.12f, -yaw * 0.15f, 0);
        dsl(r.head, -pitch * 0.45f, -yaw * 0.5f, 0);

        // ---- the bite: from P.attackWindup ticks before the swing moment to P.attackTicks after it --------------------
        if (attackT >= -P.attackWindup && attackT < P.attackTicks) {
            // the key times stretch with the params: the wind-up keys over attackWindup, the rest over attackTicks
            float t = attackT < 0f ? attackT * (-KEY_T[0] / P.attackWindup) : attackT * (KEY_T[KEY_T.length - 1] / P.attackTicks);
            int k = 0;
            while (k < KEY_T.length - 2 && t > KEY_T[k + 1])
                k++;
            float f = Life.smooth((t - KEY_T[k]) / (KEY_T[k + 1] - KEY_T[k]));
            for (int i = 0; i < nn; i++)       // the base lifts, the far links drop: the S straightens into a lunge
                dsl(r.neck[i], key(0, k, f) * STRIKE[Math.min(i, STRIKE.length - 1)], 0, 0);
            dsl(r.head, key(1, k, f), 0, 0);
            dsl(r.jaw, -key(2, k, f), 0, 0);
            move(r.body, 0, 0, -key(3, k, f));
            dsl(r.body, key(4, k, f), 0, 0);
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
                dsl(r.neck[k], -9f * d, 3f * d, 0);
            dsl(r.head, 10f * d, 0, 0);
            dsl(r.jaw, -18f * d, 0, 0);
            for (int k = 0; k < nt; k++)
                dsl(r.tail[k], 3f * d, 0, 0);
            for (int i = 0; i < 4; i++) {
                boolean front = i < 2;
                dsl(r.upper[i], front ? 30f * d : -25f * d, 0, 0);
                dsl(r.lower[i], front ? -50f * d : 50f * d, 0, 0);
            }
        }
    }
}
