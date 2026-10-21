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
 * body > wing_{left,right}_arm > wing_*_fore > wing_*_hand (the membrane panels ride these three).
 *
 * What it does every frame, in the animation standard's order:
 * - breath always: the ribcage alone swells (its own bone, so the neck and tail are not scaled), the neck lifts a
 *   little on the in-breath and the head stays level; slow and deep for a big body;
 * - the beat: a power downstroke and a quicker folded upstroke ({@link Life#wingBeat}); the forearm bends against the
 *   stroke a few ticks behind the arm, the hand behind that, and on the upstroke the elbow and the fingers fold back
 *   in stages so the wing comes up narrow and goes down wide; the body rises on the downstroke, a little late, and
 *   its nose dips with it;
 * - at speed the beat fades into a glide: the wings held flat with a slight dihedral, the tail streaming, the legs
 *   tucked tighter; the body banks into turns, the neck leads the turn, the tail follows it out;
 * - the neck and the tail are chains: the heave of the beat and a slow sway travel down them with lag, link by link;
 *   the head counters what the neck does so it leads and stays steady;
 * - the legs hang tucked and dangle a beat behind the body;
 * - idle, offset per entity: a head shake, a yawn of the jaw, a slow look around, a tail flick;
 * - a bite (the swing), a hit flinch and the death fold from the entity's own state.
 *
 * All angles here are in the concept DSL's vocabulary (degrees; x > 0 lifts a forward-pointing part's nose and drops
 * a backward one's tip; for the left wing z > 0 lifts the tip and y > 0 sweeps it forward; design/models/README.md)
 * and go onto the parts through {@link #dsl}, so a pose keyed here can be drawn by tools/pose_preview.py with the
 * same numbers. The right wing's y and z are mirrored automatically.
 */
@OnlyIn(Dist.CLIENT)
public final class Dragon {

    /** What differs between dragons on the one rig. */
    public static final class Params {
        /** Ticks per wing beat and the downstroke's share of it. A big wyrm beats slowly. */
        public float beatTicks = 40f, down = 0.55f;
        /** The arm's angle at the top and the bottom of the stroke (degrees, left wing: + is up). */
        public float flapUp = 42f, flapDown = -36f;
        /** The glide: the arm's held angle, and the horizontal speed (blocks per tick) where the glide begins / is full. */
        public float glideArm = 10f, glideFrom = 0.07f, glideFull = 0.22f;
        /** The breath's period at rest (ticks); it quickens on the wing. */
        public float breathTicks = 120f;
        /** The chain lag: ticks per link down the neck and the tail. */
        public float neckLag = 2.5f, tailLag = 3f;
        /** How far the body rises on a downstroke (px) and how much the nose dips with it (degrees). */
        public float heavePx = 2.2f, heaveDeg = 2.5f;
        /** The bite clip's length (ticks) from the moment the entity's swing begins. */
        public float attackTicks = 16f;
        /** How often (ticks) the idle head shake and the yawn come round. */
        public float shakeEvery = 260f, yawnEvery = 210f;
    }

    /** The resolved parts of one rig (null where the body has no such bone). */
    public static final class Rig {
        public final ModelPart body, ribs, head, jaw, hornL, hornR, crest, earL, earR, spine1, spine2;
        public final ModelPart[] neck, tail;
        /** [left, right]. */
        public final ModelPart[] arm = new ModelPart[2], fore = new ModelPart[2], hand = new ModelPart[2];
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

    /** Per-entity smoothed state: [glide 0..1, bank deg, last age, attack clip start age, last attackTime seen, hover]. */
    private static final Map<LivingEntity, float[]> STATE = new WeakHashMap<>();

    private static final float[] KEY_P = {0f, 0.3f, 0.55f, 0.75f, 1f};
    // the bite, keys at KEY_P: {neck x (each link), head x, jaw open, body forward px, body x, wing arm z}
    private static final float[][] ATTACK = {
        {0, 0, 0, 0, 0, 0},
        {-5, -8, 14, -2.0f, 3, 10},        // anticipation: the neck draws back and up, the jaw opens, the wings rise
        {7, 10, 34, 5.0f, -4, -14},        // the strike: the neck shoots forward and down, the jaw gapes, the wings drive
        {2, 2, 8, 1.5f, -1, -4},           // recovery
        {0, 0, 0, 0, 0, 0},
    };

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

    /**
     * Pose the rig for this frame, on top of the model's rest pose (call restPose() first). The entity's own state
     * drives everything: its speed over the ground, its turn rate, its swing (attackTime), hurtTime, deathTime, age
     * and id.
     */
    public static void animate(Rig r, Params P, LivingEntity e, float age, float netHeadYaw, float headPitch,
                               float attackTime) {
        int id = e.getId();
        float[] st = STATE.get(e);
        if (st == null)
            STATE.put(e, st = new float[] {0f, 0f, age, -1000f, 0f, 1f});
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
        // the bank: the body rolls into a turn; it builds over a few ticks and eases out
        float yawRate = Mth.wrapDegrees(e.yBodyRot - e.yBodyRotO);
        float wantBank = Mth.clamp(yawRate * 3.5f, -28f, 28f);
        st[1] += (wantBank - st[1]) * Math.min(1f, dt / 8f);
        float bank = st[1];
        if (attackTime > 0f && (st[4] <= 0f || attackTime < st[4]))
            st[3] = age;                              // the swing began this frame: start our own, slower clip
        st[4] = attackTime;

        // ---- the beat -----------------------------------------------------------------------------------------------
        float T = P.beatTicks, D = P.down;
        float beat = Life.wingBeat(age, id, T, D);                 // -1 top .. 1 bottom
        float beat3 = Life.wingBeat(age - 3f, id, T, D);
        float beat6 = Life.wingBeat(age - 6f, id, T, D);
        float up = Life.upstroke(age, id, T, D);                   // 0..1 through the recovery
        float amp = 1f - 0.85f * glide;                            // the glide stills the wings
        float stroke = (beat + 1f) * 0.5f;
        for (int i = 0; i < 2; i++) {
            float s = i == 0 ? 1f : -1f;                           // the right wing mirrors y and z
            float armZ = Mth.lerp(stroke, P.flapUp, P.flapDown) * amp + P.glideArm * glide
                + 2.5f * glide * Mth.sin((age + Life.offset(id)) / 50f);          // a glide is never dead still
            // the forearm bends against the stroke (up on the way down, down on the way up), the hand more and later
            float foreZ = 55f * (beat - beat3) * amp - 16f * up * amp - 3f * glide;
            float handZ = 70f * (beat3 - beat6) * amp - 10f * up * amp - 5f * glide;
            // the folds: the elbow and the fingers sweep back on the upstroke so the wing comes up narrow
            float foreY = -28f * up * amp - 3f * glide;
            float handY = -55f * up * amp - 6f * glide;
            dsl(r.arm[i], 0, 0, s * armZ);
            dsl(r.fore[i], 0, s * foreY, s * foreZ);
            dsl(r.hand[i], 0, s * handY, s * handZ);
            // a bank drops the inside wing a little further and lifts the outside one
            dsl(r.arm[i], 0, 0, bank * 0.25f);
        }
        // the body: carried up by each downstroke a few ticks late, its nose dipping with the stroke; a glide pitches
        // it nose-up a touch; the bank rolls it
        float heave = beat3 * amp;
        move(r.body, 0, P.heavePx * heave, 0);
        dsl(r.body, -P.heaveDeg * beat * amp + 3f * glide, 0, bank * 0.8f);

        // ---- breath, always: the ribcage swells, the neck lifts, the head stays level --------------------------------
        float breathTicks = Mth.lerp(glide, P.breathTicks, P.breathTicks * 0.7f);
        float br = Life.breath(age, id, breathTicks);              // -1 out .. 1 in
        float swell = 0.5f + 0.5f * br;
        ModelPart ribs = r.ribs != null ? r.ribs : r.body;
        ribs.xScale = 1f + 0.045f * swell;
        ribs.yScale = 1f + 0.035f * swell;
        ribs.zScale = 1f + 0.012f * swell;
        if (r.ribs == null)
            move(r.body, 0, 0.3f * br, 0);

        // ---- the neck: a chain the heave and a slow sway roll down; the head counters it all -------------------------
        float neckX = 0f, neckY = 0f;
        int nn = r.neck.length;
        for (int k = 0; k < nn; k++) {
            float f = 1f - 0.12f * k;                              // the base moves most
            float x = -1.6f * f * Life.wingBeat(age - P.neckLag * (k + 1), id, T, D) * amp      // the heave, lagging
                + 1.1f * Life.chain(age, id, 75f, k, 6f) * (1f - 0.5f * glide)                 // a slow nod
                + (k < 2 ? 1.4f * br : 0f)                                                     // the in-breath lifts the base
                - 1.2f * glide;                                                                // stretched out in a glide
            float y = 2.4f * Life.chain(age, id, 110f, k, 7f) * (1f - 0.6f * glide)            // a slow side sway
                - bank * 0.18f;                                                                // the neck leads the turn
            dsl(r.neck[k], x, y, 0);
            neckX += x;
            neckY += y;
        }
        dsl(r.head, -0.85f * neckX, -0.85f * neckY, 0);           // the head leads and stays steady
        dsl(r.head, -0.6f * br, 0, 0);

        // ---- the tail: a tapering chain, the heave and the sway lagging link by link; it streams in a glide ----------
        int nt = r.tail.length;
        for (int k = 0; k < nt; k++) {
            float f = 0.55f + 0.1f * k;                            // the tip swings most
            float x = 2.8f * f * Life.wingBeat(age - P.tailLag * (k + 1), id, T, D) * amp     // trails the heave
                + 1.0f * Life.chain(age, id, 80f, k, 6f) * (1f - 0.5f * glide)
                - 1.5f * glide * (k < 3 ? 1f : 0.4f);                                        // lifts level to stream
            float y = (2.2f + 0.5f * k) * Life.chain(age, id, 100f, k, 8f) * (1f - 0.7f * glide)
                + bank * 0.12f * (k + 1) / nt * 2f;                                           // follows the turn out
            dsl(r.tail[k], x, y, 0);
        }
        dsl(r.spine1, -0.6f * beat3 * amp, 0.8f * Life.chain(age, id, 100f, 0, 0f), 0);
        dsl(r.spine2, -0.8f * beat6 * amp, 1.0f * Life.chain(age, id, 100f, 1, 8f), 0);

        // ---- the legs: tucked, dangling a beat behind the body; tucked tighter in a glide ---------------------------
        float dangle4 = Life.wingBeat(age - 4f, id, T, D) * amp;
        float dangle7 = Life.wingBeat(age - 7f, id, T, D) * amp;
        float dangle10 = Life.wingBeat(age - 10f, id, T, D) * amp;
        for (int i = 0; i < 4; i++) {
            boolean front = i < 2;
            float dir = front ? 1f : -1f;
            dsl(r.upper[i], 2.5f * dangle4 - 7f * glide * dir, 0, 0);
            dsl(r.lower[i], 3.5f * dangle7 * dir, 0, 0);
            dsl(r.foot[i], 6f * dangle10 + 3f * Life.wander(age - 5f * i, id, 90f), 0, 0);
        }

        // ---- idle behaviours (fade with speed) --------------------------------------------------------------------
        if (idle > 0.02f) {
            float shake = Life.shake(age, id, 2, P.shakeEvery, 16f, 2.5f) * idle;
            dsl(r.head, 0, 12f * shake, 6f * shake);
            if (nn > 0)
                dsl(r.neck[nn - 1], 0, 4f * shake, 0);
            float yawn = Life.bump(age, id, 1, P.yawnEvery, 34f) * idle;
            dsl(r.jaw, -24f * yawn, 0, 0);
            dsl(r.head, 6f * yawn, 0, 0);
            if (nn > 1)
                dsl(r.neck[nn - 2], 3f * yawn, 0, 0);
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

        // ---- the bite: over P.attackTicks from the moment the swing began -------------------------------------------
        float t = (age - st[3]) / P.attackTicks;
        if (t >= 0f && t < 1f) {
            int k = 0;
            while (k < KEY_P.length - 2 && t > KEY_P[k + 1])
                k++;
            float f = Life.smooth((t - KEY_P[k]) / (KEY_P[k + 1] - KEY_P[k]));
            for (int i = 0; i < nn; i++)
                dsl(r.neck[i], key(0, k, f), 0, 0);
            dsl(r.head, key(1, k, f), 0, 0);
            dsl(r.jaw, -key(2, k, f), 0, 0);
            move(r.body, 0, 0, -key(3, k, f));
            dsl(r.body, key(4, k, f), 0, 0);
            for (int i = 0; i < 2; i++)
                dsl(r.arm[i], 0, 0, (i == 0 ? 1f : -1f) * key(5, k, f));
        }

        // ---- the hit flinch: the head jerks away, the wings snap up, the body rolls --------------------------------
        if (e.hurtTime > 0 && e.deathTime == 0) {
            float h = Mth.sin(e.hurtTime / 10f * (float) Math.PI);
            dsl(r.head, 4f * h, -14f * h, 6f * h);
            if (nn > 0)
                dsl(r.neck[0], -5f * h, -4f * h, 0);
            dsl(r.body, 0, 0, 5f * h);
            for (int i = 0; i < 2; i++)
                dsl(r.arm[i], 0, 0, (i == 0 ? 1f : -1f) * 12f * h);
        }

        // ---- the death fold: the wings crumple, the neck drops, the legs let go; the renderer lays it down ----------
        if (e.deathTime > 0) {
            float d = Life.smooth(Math.min(1f, (e.deathTime + 1f) / 14f));
            for (int i = 0; i < 2; i++) {
                float s = i == 0 ? 1f : -1f;
                dsl(r.arm[i], 0, 0, s * -30f * d);
                dsl(r.fore[i], 0, s * -60f * d, s * 20f * d);
                dsl(r.hand[i], 0, s * -70f * d, 0);
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
