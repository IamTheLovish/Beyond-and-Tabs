package com.solegendary.reignofnether.unit.modelling.motion;

import com.solegendary.reignofnether.unit.modelling.animations.Life;
import net.minecraft.util.Mth;

import static com.solegendary.reignofnether.unit.modelling.motion.AnimState.*;

/**
 * The death-pose contract (design/animation-foundation-audit.md, section 3, item 3c): a body whose {@link BoneMap}
 * says it falls by itself skips vanilla's 90-degree flip and plays this fall instead, seeded by the blow that killed it,
 * and the client keeps the body lying where it fell ({@link Corpses}) after the server has removed the unit. Pure,
 * like {@link Motion}: the slab and a time in, per-bone offsets out, no allocation.
 *
 * The killing blow is latched once, on the client tick the unit starts dying ({@link #latch}): its direction (the
 * last hit's, if it came within {@link #BLOW_WINDOW} ticks) and its size (the flinch's size class: a drop of 12% of
 * max health makes it heavy). The body falls away from the blow - hit in the chest it goes over backwards, hit from
 * its right it falls to its left - with a seeded jitter so two men cut down side by side never land alike. With no
 * blow (fire, poison, a spell's tick) the knees go first and it crumples forwards.
 *
 * The timeline, in ticks since the death started (vanilla removes the unit at 20, so every fall lands by 18):
 * a reel ({@link #reelTicks}: the blow drives the body back a little, the knees start to give), the fall (the tilt
 * accelerates like a falling plank, 90 degrees about the feet, the head and arms trailing), the impact (one bounce,
 * the head and arms flopping on), then rest: lying, the head turned aside, a knee up. A heavy blow reels for less,
 * falls faster and throws the body further. The body is held still until {@link #HOLD_END}, then sinks into the ground
 * over {@link #SINK_TICKS} ({@link #sinkBlocks}) - render only.
 *
 * Output: {@link #BONES} bones x {@link Motion#CH} channels, Motion's eight bones in Motion's convention (ROOT is the
 * whole body about its feet; its RX / RZ here are a tilt of up to 90 degrees about one horizontal axis, its RY a twist)
 * plus the elbows and knees a jointed rig has. Angles are radians, moves px.
 */
public final class DeathFall {

    private DeathFall() {}

    /** The bones past Motion's eight: a jointed rig's forearms and shins (a vanilla body has none). */
    public static final int FORE_R = 8, FORE_L = 9, KNEE_R = 10, KNEE_L = 11;
    public static final int BONES = 12, OUT = BONES * Motion.CH;

    /** {@link AnimState#DEATH_KIND}: alive (nothing latched); died with no blow; a blow; a heavy blow. */
    public static final float ALIVE = 0f, NO_BLOW = 1f, BLOW = 2f, HEAVY = 3f;

    /** A hit at most this many ticks before the death started is the killing blow. */
    public static final int BLOW_WINDOW = 4;
    /** The client holds the body until this deathTime, then sinks it over SINK_TICKS; at END it is gone. */
    public static final int HOLD_END = 80, SINK_TICKS = 20, END = HOLD_END + SINK_TICKS;
    /** How far the body sinks by END: deeper than a body lying on its side is thick. */
    public static final float SINK_BLOCKS = 0.6f;

    public static final float FALL_DEG = 90f;
    /** The fall's direction jitter, degrees either way: a blow's, and a crumple's (no blow: roughly forwards). */
    static final float BLOW_JITTER_DEG = 14f, CRUMPLE_SPREAD_DEG = 40f;
    /** The body's half thickness front to back and half width at the shoulders, px: lying, it is lifted by these. */
    static final float HALF_DEPTH = 2.5f, HALF_WIDTH = 4.5f;

    private static final float D = Mth.DEG_TO_RAD;

    /** Ticks of the reel before the body tips: a heavy blow 2, a blow 4, a crumple 7 (the knees go first). */
    public static float reelTicks(float kind) {
        return kind == HEAVY ? 2f : kind == BLOW ? 4f : 7f;
    }

    /** Ticks of the fall itself, the unit's own +-1 (a heavy blow is the fastest). */
    public static float fallTicks(float kind, int id) {
        float base = kind == HEAVY ? 8f : 10f;
        return base + (Desync.hash01(id, 7100) * 2f - 1f);
    }

    /** The tick the body hits the ground. */
    public static float impactTick(float kind, int id) {
        return reelTicks(kind) + fallTicks(kind, id);
    }

    /**
     * How far a weapon held on a grip beside the hand (BoneMap#dropped: the Halberdier's halberd) has slipped back to
     * its planted rest at t ticks into the death, 0..1: none through the reel, all of it by the time the body lands.
     */
    public static float dropped(float t, float kind, int id) {
        if (kind == ALIVE || !(t > 0f))
            return 0f;
        return Life.smooth((t - reelTicks(kind)) / fallTicks(kind, id));
    }

    /** How far the blow throws the body along its fall, px. */
    public static float slidePx(float kind) {
        return kind == HEAVY ? 8f : kind == BLOW ? 3f : 0f;
    }

    /**
     * Latches the death once, on the client tick a unit is first seen dying: the fall's direction relative to the body
     * (radians, 0 = forwards, +PI/2 = to its right) and its kind. A unit back on its feet (deathTime 0: a revive)
     * clears it.
     */
    public static void latch(float[] s, int b, int id, int deathTime) {
        if (deathTime <= 0) {
            s[b + DEATH_KIND] = ALIVE;
            s[b + DEATH_DRAWN] = 0f;
            return;
        }
        if (s[b + DEATH_KIND] != ALIVE)
            return;
        float jitter = Desync.hash01(id, 7200) * 2f - 1f;
        float az;
        if (s[b + HIT_AGE] <= deathTime + BLOW_WINDOW && Float.isFinite(s[b + HIT_DIR])) {
            s[b + DEATH_KIND] = s[b + HIT_SIZE] == STAGGER ? HEAVY : BLOW;
            az = s[b + HIT_DIR] + Mth.PI + jitter * BLOW_JITTER_DEG * D;   // away from the blow
        } else {
            s[b + DEATH_KIND] = NO_BLOW;
            az = jitter * CRUMPLE_SPREAD_DEG * D;
        }
        s[b + DEATH_AZ] = Mth.wrapDegrees(az * Mth.RAD_TO_DEG) * D;
    }

    /** The body's tilt toward the ground at t ticks into the death, degrees: the reel, the fall, one bounce, rest. */
    public static float tiltDeg(float t, float kind, int id) {
        float reel = reelTicks(kind), fall = fallTicks(kind, id);
        float reelDeg = kind == HEAVY ? 14f : kind == BLOW ? 10f : 4f;
        if (!(t > 0f))
            return 0f;
        if (t <= reel)
            return reelDeg * Life.smooth(t / reel);
        float u = (t - reel) / fall;
        if (u < 1f)
            return reelDeg + (FALL_DEG - reelDeg) * u * u;   // a falling plank: slow to tip, fastest as it lands
        float after = t - reel - fall;
        float bounce = (kind == HEAVY ? 14f : 9f) * (float) Math.exp(-after / 4f) * Math.abs(Mth.sin(after * Mth.PI / 5f));
        return FALL_DEG - bounce;
    }

    /**
     * Whether a unit is drawn with the red hurt overlay. Vanilla: while hurt or dying. A body that falls by itself
     * (owned by DeathFall) is red only while hurt, as in life: the hurt flash of the killing blow, then its own colours
     * for the fall and the hold - on the body and on every layer (team cloth) alike.
     */
    public static boolean red(int hurtTime, int deathTime, boolean owned) {
        return hurtTime > 0 || (deathTime > 0 && !owned);
    }

    /** How far the body has sunk at deathTime t, blocks: 0 until HOLD_END, then down to SINK_BLOCKS at END. */
    public static float sinkBlocks(float t) {
        if (!(t > HOLD_END))
            return 0f;
        return SINK_BLOCKS * Life.smooth((t - HOLD_END) / SINK_TICKS);
    }

    private static void add(float[] out, int bone, int ch, float v) {
        out[bone * Motion.CH + ch] += v;
    }

    /**
     * The fall at t ticks into the death (deathTime - 1 + the partial tick) into {@code out} ({@link #OUT} floats).
     * Nothing latched (or a non-finite t) leaves it all zero.
     *
     * @param jointed the body has elbows and knees ({@link #FORE_R}..{@link #KNEE_L}): its knees give in the reel; a
     *                vanilla body's straight legs do not kick forward instead
     */
    public static void pose(float[] s, int b, int id, float t, boolean jointed, float[] out) {
        for (int i = 0; i < OUT; i++)
            out[i] = 0f;
        float kind = s[b + DEATH_KIND];
        if (kind == ALIVE || !Float.isFinite(t))
            return;
        t = Mth.clamp(t, 0f, END);
        float az = Float.isFinite(s[b + DEATH_AZ]) ? s[b + DEATH_AZ] : 0f;
        float fwd = Mth.cos(az), right = Mth.sin(az);
        float back = Math.max(0f, -fwd), front = Math.max(0f, fwd);
        float toRight = Math.max(0f, right), toLeft = Math.max(0f, -right);
        boolean blow = kind != NO_BLOW;
        float hard = kind == HEAVY ? 1.5f : 1f;
        float side = Desync.hash01(id, 7300) < 0.5f ? -1f : 1f;   // which way the head rolls, which knee comes up
        float armR = 0.8f + 0.4f * Desync.hash01(id, 7400), armL = 0.8f + 0.4f * Desync.hash01(id, 7500);

        float reel = reelTicks(kind), fall = fallTicks(kind, id), t1 = reel + fall;
        float reelEnv = t <= reel ? Life.smooth(t / reel) : 1f - Life.smooth((t - reel) / fall);
        float u = Mth.clamp((t - reel) / fall, 0f, 1f);
        float fallEnv = Mth.sin(u * Mth.PI);       // mid-fall
        float fallP = Life.smooth(u);              // 0 standing .. 1 down
        float after = Math.max(0f, t - t1);
        float impact = t >= t1 && after < 5f ? Mth.sin(after / 5f * Mth.PI) : 0f;
        float rest = Life.smooth(after / 8f);

        // the whole body: tilted over its feet along the fall, twisted a little, lifted so it lies on the ground
        // rather than through it, thrown along the fall by the blow; a crumple sinks at the knees first
        float tilt = tiltDeg(t, kind, id) * D;
        add(out, Motion.ROOT, Motion.RX, tilt * fwd);
        add(out, Motion.ROOT, Motion.RZ, tilt * right);
        add(out, Motion.ROOT, Motion.RY, side * 12f * fallP * D);
        // the body's cross-section as an ellipse: its half depth lying on its back, its half width on its side
        float lift = Math.abs(Mth.sin(tilt)) * Mth.sqrt(HALF_DEPTH * HALF_DEPTH * fwd * fwd
            + HALF_WIDTH * HALF_WIDTH * right * right);
        float knee = jointed ? (kind == HEAVY ? 15f : kind == BLOW ? 30f : 60f) * reelEnv : 0f;
        float drop = jointed ? 12f * (1f - Mth.cos(knee * 0.5f * D)) * (1f - fallP) : 0f;
        add(out, Motion.ROOT, Motion.TY, lift - drop);
        float slide = slidePx(kind) * Life.smooth(t / t1);
        add(out, Motion.ROOT, Motion.TZ, slide * fwd);
        add(out, Motion.ROOT, Motion.TX, slide * right);

        // the torso: driven back by the blow, then folding at the hips against the fall; a crumple slumps forwards
        if (blow) {
            add(out, Motion.BODY, Motion.RX, fwd * 12f * hard * reelEnv * D);
            add(out, Motion.BODY, Motion.RZ, right * 12f * hard * reelEnv * D);
        } else {
            add(out, Motion.BODY, Motion.RX, 15f * reelEnv * D);
        }
        add(out, Motion.BODY, Motion.RX, (-fwd * 14f * fallEnv - fwd * 4f * rest) * D);
        add(out, Motion.BODY, Motion.RZ, -right * 10f * fallEnv * D);

        // the head: snaps with the blow, tucks against the fall, whips on at the impact, rests turned aside
        if (blow) {
            add(out, Motion.HEAD, Motion.RX, fwd * 18f * hard * reelEnv * D);
            add(out, Motion.HEAD, Motion.RZ, right * 14f * hard * reelEnv * D);
        } else {
            add(out, Motion.HEAD, Motion.RX, 20f * reelEnv * D);
        }
        add(out, Motion.HEAD, Motion.RX, (-fwd * 22f * fallEnv + fwd * 14f * impact) * D);
        add(out, Motion.HEAD, Motion.RZ, (right * 10f * impact + side * 8f * rest) * D);
        add(out, Motion.HEAD, Motion.RY, side * 45f * rest * D);

        // the arms (a hanging limb: RX + = the hand forwards, RZ + = the hand to the body's right): thrown out by the
        // blow; trailing up off a backwards fall and splayed when down; reaching out to brace a forward one; on a
        // side fall the arm underneath goes up past the head and the other drapes over towards the ground
        float flung = (blow ? 25f * hard : 8f) * reelEnv;
        float rx = flung + (back * 60f + front * 50f) * fallEnv + (back * 20f + front * 40f) * fallP;
        float splay = back * 35f * fallP + 10f * reelEnv;
        add(out, Motion.ARM_R, Motion.RX, rx * armR * D);
        add(out, Motion.ARM_L, Motion.RX, rx * armL * D);
        add(out, Motion.ARM_R, Motion.RZ, (splay + toRight * 150f * fallP - toLeft * 25f * fallP) * armR * D);
        add(out, Motion.ARM_L, Motion.RZ, (-splay - toLeft * 150f * fallP + toRight * 25f * fallP) * armL * D);
        add(out, Motion.ARMS, Motion.RX, (30f * fallP + 20f * fallEnv) * D);
        float elbow = (30f * fallP + 20f * fallEnv) * D;
        add(out, FORE_R, Motion.RX, elbow * armR);
        add(out, FORE_L, Motion.RX, elbow * armL);

        // the legs (RX + = the foot forwards): the knees give in the reel (a jointed rig), then at rest one knee is
        // drawn up; on a side fall the upper leg drapes forwards
        float lead = 25f * rest, trail = 5f * rest;
        float upperR = toLeft * 25f * fallP, upperL = toRight * 25f * fallP;   // falling left, the right leg is on top
        boolean rightLeads = side > 0f;
        add(out, Motion.LEG_R, Motion.RX, (knee * 0.5f + (rightLeads ? lead : trail) + upperR) * D);
        add(out, Motion.LEG_L, Motion.RX, (knee * 0.5f + (rightLeads ? trail : lead) + upperL) * D);
        add(out, Motion.LEG_R, Motion.RZ, 6f * rest * D);
        add(out, Motion.LEG_L, Motion.RZ, -6f * rest * D);
        float kneeRest = 45f * rest;
        add(out, KNEE_R, Motion.RX, -(knee + (rightLeads ? kneeRest : 0f)) * D);
        add(out, KNEE_L, Motion.RX, -(knee + (rightLeads ? 0f : kneeRest)) * D);

        for (int i = 0; i < OUT; i++)
            if (!Float.isFinite(out[i]))
                out[i] = 0f;
    }
}
