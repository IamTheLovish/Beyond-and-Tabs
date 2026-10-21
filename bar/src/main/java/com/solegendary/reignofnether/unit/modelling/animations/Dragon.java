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
 *   legs tucked tighter with a slow swing; the body banks into turns, the neck leads the turn, the tail counter-sways;
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
        public float flapUp = 32f, flapDown = -38f;
        /** The glide: the arm's held angle, and the horizontal speed (blocks per tick) where the glide begins / is full. */
        public float glideArm = 7f, glideFrom = 0.07f, glideFull = 0.22f;
        /** The breath's period at rest (ticks); it quickens on the wing. */
        public float breathTicks = 90f;
        /** The chain lag: ticks per link down the neck and the tail. */
        public float neckLag = 2f, tailLag = 3f;
        /** The chains' travelling waves: degrees per link (the neck nods, the tail sways sideways). */
        public float neckWave = 6f, tailWave = 7f;
        /** How far the body rises on a downstroke and drops through the top (px); the nose up at the bottom / down at the top (degrees). */
        public float riseP = 6.5f, dropPx = 4f, pitchUp = 10f, pitchDown = 6f;
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

    /** Per-entity smoothed state: [glide 0..1, bank deg, last age, attack clip start age, last attackTime seen, hover]. */
    private static final Map<LivingEntity, float[]> STATE = new WeakHashMap<>();

    private static final float[] KEY_P = {0f, 0.2f, 0.5f, 0.62f, 1f};
    // the bite, keys at KEY_P: {neck x (each link), head x, jaw open, body forward px, body x}
    private static final float[][] ATTACK = {
        {0, 0, 0, 0, 0},
        {-3, -8, 20, -1.5f, 2},         // anticipation: the neck draws back and up, the jaw opens
        {6, 6, 35, 4.0f, -3},           // the strike: the neck extends ~30 forward and down over the links, the jaw gapes
        {5, 4, 2, 3.5f, -2},            // the snap: the jaw shuts hard, the neck still out
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

        pose(r, P, id, age, glide, idle, bank, (age - st[3]) / P.attackTicks, e.hurtTime, e.deathTime, netHeadYaw,
            headPitch);
    }

    /**
     * The pose itself from the resolved drivers (the Python twin calls this with the same arguments): glide and idle
     * 0..1, bank in degrees, attackT 0..1 while a bite runs (outside that range: none).
     */
    public static void pose(Rig r, Params P, int id, float age, float glide, float idle, float bank, float attackT,
                            int hurtTime, int deathTime, float netHeadYaw, float headPitch) {
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
        float amp = 1f - 0.85f * glide;                            // the glide stills the wings
        float stroke = (beat + 1f) * 0.5f;
        for (int i = 0; i < 2; i++) {
            float s = i == 0 ? 1f : -1f;                           // the right wing mirrors y and z
            float armZ = Mth.lerp(stroke, P.flapUp, P.flapDown) * amp + P.glideArm * glide
                + 2.5f * glide * Mth.sin((age + Life.offset(id)) / 50f);          // a glide is never dead still
            float armY = -8f * up0 * amp;                                           // the arm sweeps back a little on the recovery
            // the forearm bends against the stroke (up on the way down, down on the way up), the hand more and later
            float foreZ = 55f * (beat - beat3) * amp - 16f * up2 * amp - 3f * glide;
            // the wrist trails the elbow by a quarter cycle: the hand is still driving down as the elbow turns up
            float handZ = 60f * (beatH - beatH3) * amp - 10f * upH * amp - 5f * glide;
            // the twist about the wing's long axis: the leading edge turns down through the power stroke (pronation)
            // and up through the recovery (supination), the hand on its own lag; x is not mirrored
            float foreX = -34f * (beat - beat3) * amp;
            float handX = -45f * (beatH - beatH3) * amp;
            // the folds: the elbow, the hand and then the fingers sweep back on the upstroke so the wing comes up
            // narrow; they open again at the top
            float foreY = -35f * up2 * amp - 3f * glide;
            float handY = -60f * upH * amp - 6f * glide;
            dsl(r.arm[i], 0, s * armY, s * armZ + bank * 0.25f);
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
            dsl(r.mem[i][0], 28f * fill1 * amp + 0.5f * belly - 4f * glide, 0, 0);
            dsl(r.memB[i][0], 30f * fill1b * amp + belly, 0, 0);
            dsl(r.mem[i][1], 34f * fill2 * amp + 0.5f * belly - 5f * glide, 0, 0);
            dsl(r.memB[i][1], 34f * fill2b * amp + belly, 0, 0);
            for (int k = 0; k < 4; k++) {
                dsl(r.web[i][k], 36f * fill3 * amp + 0.5f * bellyH - 5f * glide, 0, 0);
                dsl(r.webB[i][k], 36f * fill3b * amp + bellyH, 0, 0);
            }
            dsl(r.mem0[i], 14f * fill1 * amp + 0.4f * belly - 3f * glide, 0, 0);   // the flank strip follows the arm sail, damped
            for (int k = 0; k < 3; k++)                            // the trailing fingers close toward the leading one
                dsl(r.finger[i][k], 0, s * (18f + 14f * k) * upF * amp, 0);
        }
        // the body: dropped through the top of the stroke, carried up by the downstroke a few ticks late, its nose
        // dipping with the stroke; a glide pitches it nose-up a touch; the bank rolls it
        float heave = beat3 * amp;
        move(r.body, 0, heave > 0f ? P.riseP * heave : P.dropPx * heave, 0);
        // the body flies: nose up at the bottom of the stroke, down at the top; a glide holds it nose-up a touch
        float pitch0 = (beat > 0f ? P.pitchUp : P.pitchDown) * beat * amp;
        dsl(r.body, pitch0 + 3f * glide, 0, bank * 0.8f);

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
        for (int k = 0; k < nn; k++) {
            float f = 1f - 0.1f * k;
            float x = -2.6f * f * stroke(age - P.neckLag * (k + 1), id, T, D) * amp          // the heave, lagging
                + P.neckWave * Life.chain(age, id, 64f, k, P.neckLag) * (1f - 0.5f * glide)           // the travelling nod
                + (k < 2 ? 2.5f * br : 0f)                                                            // the in-breath lifts the base
                - (k < 2 ? 0.35f * pitch0 : 0f)                                                       // counters the body's pitch (~4 over two links)
                - 1.2f * glide;                                                                       // stretched out in a glide
            float y = 2.4f * Life.chain(age, id, 110f, k, 7f) * (1f - 0.6f * glide)                   // a slow side sway
                - bank * 0.18f;                                                                       // the neck leads the turn
            dsl(r.neck[k], x, y, 0);
            neckX += x;
            neckY += y;
        }
        dsl(r.head, -0.85f * neckX, -0.85f * neckY, 0);           // the head leads and stays steady
        dsl(r.head, -0.6f * br, 0, 0);

        // ---- the tail: a lateral wave three ticks a link, the heave lagging down it; it lifts to stream in a glide --
        int nt = r.tail.length;
        for (int k = 0; k < nt; k++) {
            float f = 0.55f + 0.1f * k;                            // the tip swings most
            float x = 5f * f * stroke(age - T * 0.25f - P.tailLag * k, id, T, D) * amp   // trails the heave a quarter beat, then link by link
                + 1.0f * Life.chain(age, id, 80f, k, 6f) * (1f - 0.5f * glide)
                - 2.5f * glide * (k < 3 ? 1f : 0.5f);                                        // lifts level to stream
            float y = (P.tailWave + 0.6f * k) * Life.chain(age, id, 72f, k, P.tailLag) * (1f - 0.6f * glide)   // the wave
                + bank * 0.3f * (k + 1) / nt;                                                 // counter-sways the bank
            x -= 0.12f * pitch0;                                                               // counters the body's pitch
            dsl(r.tail[k], x, y, 0);
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

        // ---- the bite: over P.attackTicks from the moment the swing began -------------------------------------------
        if (attackT >= 0f && attackT < 1f) {
            int k = 0;
            while (k < KEY_P.length - 2 && attackT > KEY_P[k + 1])
                k++;
            float f = Life.smooth((attackT - KEY_P[k]) / (KEY_P[k + 1] - KEY_P[k]));
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
