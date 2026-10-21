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
 * Client only. ONE articulated quadruped rig for the big four-legged units - the Mossback Bison first; the Oriflamme
 * Elephant, the Gorehorn Rhino and the War Mammoth join by building a body on the same bones (tools/gen_quadruped.py)
 * and handing this class their {@link Params}. lovish, Oct 21: "extremely realistic and cohesive"; animals should
 * "look like they're breathing and move articulated".
 *
 * Bones (DSL names; a body carries the ones it has, the rest are skipped):
 * body (root) > hump, neck > head > jaw, ear_left/right, horn_left/right, tusk_left/right, trunk_1 > trunk_2 > trunk_3;
 * body > hips > tail > tail_tip; body > front_{left,right}_upper > _lower > _foot; hips > hind_{left,right}_upper >
 * _lower > _foot.
 *
 * What it does every frame, in the standard's order:
 * - breath always: the ribcage swells, the neck lifts on the in-breath, faster after a run then settling;
 * - the walk is Muybridge's four-beat lateral sequence, the run blends to a trot ({@link Gait}); every leg folds at
 *   shoulder/elbow/wrist or hip/stifle/hock, planted feet sweep back at the body's speed and stay flat;
 * - the body dips when a foot lands (heavier in front), the hips roll with the hind legs, the shoulders with the front,
 *   the spine sways and the neck rolls that sway out a beat later; the head leads and stays level while the body bobs;
 * - the tail is a two-link chain lagging the hips, a counterweight to the sway;
 * - idle behaviours offset per entity: a grazing dip, a head shake, ear flicks, a tail swish, a slow weight shift the
 *   legs compensate so the feet never slide;
 * - attack (a horn toss / tusk drive), hit flinch and the death fall from the entity's own state.
 *
 * All angles here are in the concept DSL's vocabulary (degrees; x > 0 swings a hanging part's foot forward and lifts a
 * forward-pointing part's nose; design/models/README.md) and go onto the parts through {@link #dsl}, so a pose keyed
 * here can be drawn by tools/pose_preview.py with the same numbers.
 */
@OnlyIn(Dist.CLIENT)
public final class Quadruped {

    /** What differs between a bison, an elephant, a rhino and a mammoth on the one rig. */
    public static final class Params {
        /** Leg length shoulder-to-ground in model px, and the renderer's scale: together the stride in blocks. */
        public float legPx = 27f, scale = 1.3f;

        public float legBlocks() {
            return legPx * scale / 16f;
        }
        /** Upper-leg swing at a walk, degrees each way; a run adds {@link #runAmp}. */
        public float walkAmp = 28f, runAmp = 8f;
        /** A touchdown's give (px, on top of the compass bob) and the breath's period. Bigger = slower, heavier. */
        public float footDipPx = 0.2f, breathTicks = 90f;
        /** The head carried low (bison, rhino) or high (elephant): the neck's grazing dip and the attack keys scale. */
        public float neckDrop = 22f;
        /** Attack clip length in ticks (the entity's swing is 6 ticks; a heavy beast takes longer to toss). */
        public float attackTicks = 16f;
        /** Head yaw/pitch follow: how much of the look goes into the neck, the rest into the head. */
        public float neckLook = 0.4f;
        /** The grazing dip: how often (ticks), how long, and how far the neck drops (degrees). */
        public float grazeEvery = 320f, grazeTicks = 90f, grazeDeg = 32f;
    }

    /** The resolved parts of one rig (null where the body has no such bone). */
    public static final class Rig {
        public final ModelPart body, hump, neck, head, jaw, hips, tail, tailTip;
        public final ModelPart earL, earR, hornL, hornR, tuskL, tuskR, trunk1, trunk2, trunk3;
        /** [FRONT_LEFT, FRONT_RIGHT, HIND_LEFT, HIND_RIGHT] per {@link Gait}. */
        public final ModelPart[] upper = new ModelPart[4], lower = new ModelPart[4], foot = new ModelPart[4];

        public Rig(Function<String, ModelPart> bone) {
            body = bone.apply("body");
            hump = bone.apply("hump");
            neck = bone.apply("neck");
            head = bone.apply("head");
            jaw = bone.apply("jaw");
            hips = bone.apply("hips");
            tail = bone.apply("tail");
            tailTip = bone.apply("tail_tip");
            earL = bone.apply("ear_left");
            earR = bone.apply("ear_right");
            hornL = bone.apply("horn_left");
            hornR = bone.apply("horn_right");
            tuskL = bone.apply("tusk_left");
            tuskR = bone.apply("tusk_right");
            trunk1 = bone.apply("trunk_1");
            trunk2 = bone.apply("trunk_2");
            trunk3 = bone.apply("trunk_3");
            String[] names = {"front_left", "front_right", "hind_left", "hind_right"};
            for (int i = 0; i < 4; i++) {
                upper[i] = bone.apply(names[i] + "_upper");
                lower[i] = bone.apply(names[i] + "_lower");
                foot[i] = bone.apply(names[i] + "_foot");
            }
        }
    }

    /** Per-entity smoothed state: [exertion 0..1, last age, attack clip start age, last attackTime seen]. */
    private static final Map<LivingEntity, float[]> STATE = new WeakHashMap<>();

    private static final float[] KEY_P = {0f, 0.3f, 0.55f, 0.75f, 1f};
    // the horn toss, keys at KEY_P: {body x, body dip px, body forward px, neck x, head x, head y,
    //                                 hind upper x, hind lower x, front upper x, front lower x}
    private static final float[][] ATTACK = {
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
        {6, 3.0f, 3.0f, -20, 10, 6, 14, -25, 6, -30},     // anticipation: drops 15%, the weight back onto folded hocks, the front hooves lift light, head low
        {-8, 2.5f, -14f, 24, 16, -30, -16, 34, 14, -16},  // the drive: a half-body lunge, the torso rocks forward onto the planted forelegs, hind legs straight, the head arcs 40 up and across
        {-3, 1.2f, -4f, -10, 4, -8, 6, -6, -6, 4},        // recovery: the weight comes down over the front, the head settles low
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
    };

    /** Scratch per frame (the render thread only): no allocation per unit per frame. */
    private static final float[] PH = new float[4], LIFT = new float[4], SW = new float[4];

    private Quadruped() {}

    /** A DSL rotation (degrees, y up, facing -z) added to a part (MC: -x, +y, -z radians), scaled by k. */
    public static void dsl(ModelPart p, float x, float y, float z, float k) {
        if (p == null || k == 0f)
            return;
        p.xRot -= x * Mth.DEG_TO_RAD * k;
        p.yRot += y * Mth.DEG_TO_RAD * k;
        p.zRot -= z * Mth.DEG_TO_RAD * k;
    }

    /** A move in px (DSL axes: y up) on a part's offset. */
    public static void move(ModelPart p, float x, float y, float z) {
        if (p == null)
            return;
        p.x += x;
        p.y -= y;
        p.z += z;
    }

    private static float key(float[][] keys, int col, int k, float f) {
        return Mth.lerp(f, keys[k][col], keys[k + 1][col]);
    }

    /**
     * Pose the rig for this frame, on top of the model's rest pose (call restPose() first). The entity's own state
     * drives everything: walk distance and speed, swing (attackTime), hurtTime, deathTime, age and id.
     */
    public static void animate(Rig r, Params P, LivingEntity e, float limbSwing, float limbSwingAmount, float age,
                               float netHeadYaw, float headPitch, float attackTime) {
        int id = e.getId();
        float[] st = STATE.get(e);
        if (st == null)
            STATE.put(e, st = new float[] {0f, age, -1000f, 0f});
        float dt = Mth.clamp(age - st[1], 0f, 20f);
        st[1] = age;
        float amt = Math.min(1f, limbSwingAmount);
        float run = Gait.run(limbSwingAmount);
        // exertion: climbs in ~2 s of running, settles over ~10 s (the breath quickens after a run, then eases)
        float want = amt > 0.5f ? 1f : 0f;
        st[0] += (want - st[0]) * Math.min(1f, dt / (want > st[0] ? 40f : 200f));
        if (attackTime > 0f && (st[3] <= 0f || attackTime < st[3]))
            st[2] = age;                              // the swing began this frame: start our own, slower clip
        st[3] = attackTime;
        float exert = st[0];
        float idle = 1f - amt;

        // ---- the gait ---------------------------------------------------------------------------------------------
        float duty = Gait.duty(run);
        float stride = Gait.strideUnits(P.legBlocks(), P.walkAmp, Gait.WALK_DUTY);
        float amp = P.walkAmp + P.runAmp * run;
        float[] ph = PH, lift = LIFT, sw = SW;
        float dip = 0f, shoulderRoll = 0f, hipRoll = 0f, loss = Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            ph[i] = Gait.phase(limbSwing, stride, i, run);
            sw[i] = Gait.swing(ph[i], duty);
            lift[i] = Gait.lift(ph[i], duty);
            boolean front = i < 2;
            float w = (front ? 1f : 0.7f) * P.footDipPx * amt;
            dip += Gait.impact(ph[i], 0.14f) * w;
            if (ph[i] < duty)      // a planted leg at angle a reaches L cos a down: the body must drop the difference
                loss = Math.min(loss, P.legPx * (1f - Mth.cos(sw[i] * amp * amt * Mth.DEG_TO_RAD)));
            float side = (i & 1) == 0 ? 1f : -1f;      // left +1
            if (front) shoulderRoll += side * lift[i];
            else hipRoll += side * lift[i];
        }
        if (loss == Float.MAX_VALUE)
            loss = 0f;                                 // flight: no leg on the ground
        dip += loss;                                   // the compass bob: lowest at each footfall, highest mid-stance
        for (int i = 0; i < 4; i++) {
            boolean front = i < 2;
            ModelPart up = r.upper[i], lo = r.lower[i], ft = r.foot[i];
            float a = sw[i] * amp * amt;                          // upper: + forward
            float push = Gait.stance(ph[i], duty);
            float fold, hoof;
            if (front) {
                // the elbow folds the forearm back under the body through the swing, gives a little at touchdown
                fold = -lift[i] * (34f + 10f * run) - Gait.impact(ph[i], 0.2f) * 5f;
                hoof = -lift[i] * 12f;                            // the hoof trails in the air
            } else {
                // stifle flexes (lower back), hock flexes (foot forward) in the swing; the leg extends to push off
                fold = -lift[i] * (30f + 12f * run) + (push > 0.5f ? (push - 0.5f) * 2f * (8f + 10f * run) : 0f);
                hoof = lift[i] * 22f;
            }
            fold *= amt;
            hoof *= amt;
            dsl(up, a, 0, 0, 1f);
            dsl(lo, fold, 0, 0, 1f);
            // planted: the hoof stays flat on the ground whatever the leg above it does
            float flat = -(a + fold) * (1f - lift[i]);
            dsl(ft, hoof + flat, 0, 0, 1f);
        }
        // the body: the dips of the footfalls, the shoulders and hips rolling toward the lifted leg, a trot's double
        // bounce, the spine's sway rolled out through the hips, neck and tail with lag
        float cycle = ph[Gait.HIND_LEFT];
        float sway = Mth.sin(cycle * Mth.TWO_PI) * amt;
        float c = Math.max(0f, Mth.cos((cycle - 0.45f) * 2f * Mth.TWO_PI));
        float rise = run * amt * 1.5f * c * c * c;     // the trot's flight, in a tight window round the suspension
        // the bob: the whole body rises through each pair of stances and sinks into each pair of footfalls, 1.8 px
        // either way (round 11: the body's top line was pinned; a 1.3x bison makes it ~0.15 of a block in game)
        float bob = 1.8f * amt * Mth.cos((cycle - 0.1f) * 2f * Mth.TWO_PI);
        move(r.body, 0, -dip + rise + bob, 0);
        dsl(r.body, -2f * run * amt * Mth.sin(cycle * 2f * Mth.TWO_PI), 2.5f * sway, -2.5f * shoulderRoll * amt, 1f);
        dsl(r.hips, 0, -4f * Mth.sin((cycle - 0.08f) * Mth.TWO_PI) * amt, -7f * hipRoll * amt + 2.5f * shoulderRoll * amt, 1f);
        dsl(r.hump, (1.5f + 1.2f * run) * amt * Mth.sin((cycle - 0.06f) * 2f * Mth.TWO_PI), 0, 0, 1f);   // the hump pitches with every footfall
        float neckSway = -Mth.sin((cycle - 0.12f) * Mth.TWO_PI) * amt;
        dsl(r.neck, 3f * amt * Mth.cos(cycle * 2f * Mth.TWO_PI) - 6f * run * amt, 3f * neckSway, 0, 1f);
        // the head leads and stays steady: it counters the neck's pitch and most of the body's dip
        move(r.neck, 0, dip * 0.6f, 0);
        move(r.head, 0, dip * 0.3f, 0);
        dsl(r.head, -4f * amt * Mth.cos(cycle * 2f * Mth.TWO_PI) + 4f * run * amt, -3f * neckSway, 0, 1f);   // the counter-nod: 4 degrees against the neck, 3 of yaw against the sway
        // the head carries its own weight: a slow wander of a few degrees in yaw and pitch, never dead on the body's axis
        dsl(r.head, 3f * Life.wander(age, id, 160f), 3f * Life.wander(age + 70f, id, 230f), 0, 1f);
        // the tail: a chain, each link a beat behind, a counterweight to the sway
        dsl(r.tail, 8f * amt * Mth.sin((cycle - 0.1f) * Mth.TWO_PI) + 10f * run * amt,
            14f * Mth.sin((cycle - 0.18f) * Mth.TWO_PI) * amt, 0, 1f);
        dsl(r.tailTip, 3f * amt * Mth.sin((cycle - 0.2f) * 2f * Mth.TWO_PI),
            16f * Mth.sin((cycle - 0.3f) * Mth.TWO_PI) * amt, 0, 1f);

        // ---- breath, always -----------------------------------------------------------------------------------------
        float breathTicks = Mth.lerp(exert, P.breathTicks, P.breathTicks * 0.5f);
        float br = Life.breath(age, id, breathTicks);          // -1 out .. 1 in
        float swell = 0.5f + 0.5f * br;
        if (r.body != null) {
            r.body.xScale = 1f + 0.018f * swell * (1f + 0.6f * exert);
            move(r.body, 0, 0.25f * br * (1f + exert), 0);
        }
        dsl(r.neck, 1.6f * br * (1f + exert), 0, 0, 1f);       // the neck lifts on the in-breath
        dsl(r.head, -0.8f * br, 0, 0, 1f);                     // the head stays level
        move(r.hump, 0, 0.3f * br, 0);
        // after a run the whole chest heaves: the head nods with it a little
        dsl(r.head, 1.2f * br * exert, 0, 0, 1f);

        // ---- idle behaviours (fade with motion) --------------------------------------------------------------------
        if (idle > 0.02f) {
            float graze = Life.bump(age, id, 1, P.grazeEvery, P.grazeTicks) * idle;
            dsl(r.neck, -P.grazeDeg * graze, 0, 0, 1f);
            dsl(r.head, -P.grazeDeg * 0.5f * graze, 0, 0, 1f);
            dsl(r.body, -3f * graze, 0, 0, 1f);                        // the chest leans over the fore legs
            for (int i = 0; i < 2; i++) {                              // the fore legs splay and give at the knee
                float side = i == 0 ? 1f : -1f;
                dsl(r.upper[i], 0, 0, 8f * side * graze, 1f);
                dsl(r.lower[i], -8f * graze, 0, 0, 1f);
                dsl(r.foot[i], 8f * graze, 0, -8f * side * graze, 1f);
            }
            dsl(r.jaw, -6f * Life.shake(age, id, 1, P.grazeEvery, P.grazeTicks, 5f) * idle, 0, 0, 1f);
            float shake = Life.shake(age, id, 2, 260f, 14f, 2.5f) * idle;
            dsl(r.head, 0, 10f * shake, 5f * shake, 1f);
            dsl(r.neck, 0, 3f * shake, 0, 1f);
            float earL = Life.bump(age, id, 3, 110f, 8f) * idle, earR = Life.bump(age, id, 4, 140f, 8f) * idle;
            dsl(r.earL, 0, 0, -25f * earL, 1f);
            dsl(r.earR, 0, 0, 25f * earR, 1f);
            float swish = Life.shake(age, id, 5, 150f, 30f, 1.5f) * idle;
            dsl(r.tail, 0, 22f * swish, 0, 1f);
            dsl(r.tailTip, 0, 18f * Life.shake(age - 3f, id, 5, 150f, 30f, 1.5f) * idle, 0, 1f);
            // the slow weight shift: the body moves a little to one side, every leg leans so the feet stay put
            float shift = 0.6f * Life.wander(age, id, 240f) * idle;
            move(r.body, shift, 0, 0);
            float lean = -shift / P.legPx * Mth.RAD_TO_DEG;
            for (int i = 0; i < 4; i++)
                dsl(r.upper[i], 0, 0, lean, 1f);
            // the trunk, if any, sways and curls on its own
            float curl = Life.wander(age, id, 180f) * idle;
            dsl(r.trunk1, 4f * curl, 0, 0, 1f);
            dsl(r.trunk2, 8f * Life.wander(age - 6f, id, 180f) * idle, 0, 0, 1f);
            dsl(r.trunk3, 12f * Life.wander(age - 12f, id, 180f) * idle, 0, 0, 1f);
        }

        // ---- the look: the neck turns first, the head finishes it ------------------------------------------------
        float yaw = Mth.clamp(netHeadYaw, -50f, 50f), pitch = Mth.clamp(headPitch, -30f, 30f);
        dsl(r.neck, -pitch * P.neckLook * 0.6f, -yaw * P.neckLook, 0, 1f);
        dsl(r.head, -pitch * (1f - P.neckLook) * 0.6f, -yaw * (1f - P.neckLook), 0, 1f);

        // ---- the attack: a horn toss / tusk drive over P.attackTicks from the moment the swing began -----------------
        float t = (age - st[2]) / P.attackTicks;
        if (t >= 0f && t < 1f) {
            int k = 0;
            while (k < KEY_P.length - 2 && t > KEY_P[k + 1])
                k++;
            float f = Life.smooth((t - KEY_P[k]) / (KEY_P[k + 1] - KEY_P[k]));
            dsl(r.body, key(ATTACK, 0, k, f), 0, 0, 1f);
            move(r.body, 0, -key(ATTACK, 1, k, f), key(ATTACK, 2, k, f));
            dsl(r.neck, key(ATTACK, 3, k, f), 0, 0, 1f);
            dsl(r.head, key(ATTACK, 4, k, f), key(ATTACK, 5, k, f), 0, 1f);
            dsl(r.hump, key(ATTACK, 0, k, f) * 0.4f, 0, 0, 1f);
            for (int i = 2; i < 4; i++) {
                dsl(r.upper[i], key(ATTACK, 6, k, f), 0, 0, 1f);
                dsl(r.lower[i], key(ATTACK, 7, k, f), 0, 0, 1f);
                dsl(r.foot[i], -key(ATTACK, 6, k, f) - key(ATTACK, 7, k, f), 0, 0, 1f);
            }
            for (int i = 0; i < 2; i++) {
                dsl(r.upper[i], key(ATTACK, 8, k, f), 0, 0, 1f);
                dsl(r.lower[i], key(ATTACK, 9, k, f), 0, 0, 1f);
                dsl(r.foot[i], -key(ATTACK, 8, k, f) - key(ATTACK, 9, k, f), 0, 0, 1f);
            }
            dsl(r.tail, -key(ATTACK, 0, k, f) * 2f, 0, 0, 1f);
            dsl(r.trunk1, key(ATTACK, 3, k, f) * 0.8f, 0, 0, 1f);
            dsl(r.trunk2, key(ATTACK, 3, k, f) * 0.6f, 0, 0, 1f);
        }

        // ---- the hit flinch: a jerk of the head away, a crouch, the ears back ---------------------------------------
        if (e.hurtTime > 0 && e.deathTime == 0) {
            float h = Mth.sin(e.hurtTime / 10f * (float) Math.PI);
            dsl(r.head, 0, -14f * h, 6f * h, 1f);
            dsl(r.neck, -5f * h, -4f * h, 0, 1f);
            move(r.body, 0, -0.9f * h, 0);
            dsl(r.earL, 0, 0, 30f * h, 1f);
            dsl(r.earR, 0, 0, -30f * h, 1f);
        }

        // ---- the death fall: the legs buckle, the neck drops, then the renderer's roll lays it on its side ----------
        if (e.deathTime > 0) {
            float d = Life.smooth(Math.min(1f, (e.deathTime + 1f) / 14f));
            move(r.body, 0, -9f * d, 0);
            for (int i = 0; i < 4; i++) {
                boolean front = i < 2;
                dsl(r.upper[i], front ? 40f * d : -30f * d, 0, 0, 1f);
                dsl(r.lower[i], front ? -75f * d : 55f * d, 0, 0, 1f);
                dsl(r.foot[i], front ? 35f * d : -25f * d, 0, 0, 1f);
            }
            dsl(r.neck, -22f * d, 8f * d, 0, 1f);
            dsl(r.head, 14f * d, 0, 0, 1f);
            dsl(r.tail, -20f * d, 0, 0, 1f);
            dsl(r.earL, 0, 0, 35f * d, 1f);
            dsl(r.earR, 0, 0, -35f * d, 1f);
        }
    }
}
