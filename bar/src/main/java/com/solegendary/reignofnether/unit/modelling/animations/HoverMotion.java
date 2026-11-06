package com.solegendary.reignofnether.unit.modelling.animations;

import com.solegendary.reignofnether.unit.modelling.motion.Desync;
import net.minecraft.util.Mth;

/**
 * The motion of a hovering body - the Light Grid's drones (design/ai_faction.md, PR 379: the Builder Drone first;
 * the Seeker, Flak and Patch drones next). Pure: per-unit spring state and inputs in, a pose out, no Minecraft client
 * types and no allocation, so HoverMotionTest pins every rule and any hover rig ({@link Hover}) draws the same motion.
 *
 * What it does, from the animation craft rules (BAT: anticipation, staging, follow-through, idle is never still):
 * <ul>
 *   <li><b>Idle</b>: a slow bob round the hover height (two incommensurate sines, so it never ticks like a metronome),
 *   each unit at its own phase and tempo (Desync), steadier while it travels (the thrusters work against the air).</li>
 *   <li><b>Travel</b>: the nose leans into the move with speed and harder while it accelerates, lifts when it brakes,
 *   and the body banks into a turn (more at speed) - springs, so a sudden order rocks it rather than snapping it.</li>
 *   <li><b>Work</b>: the manipulator arms reach forward to the job with a fine tremor and the emitter pulses.</li>
 *   <li><b>Strike</b>: the arms wind back, drive forward to the hit at {@link Params#hitP} and hold the extreme a few
 *   ticks (it must read at 20 tps from the RTS camera), the emitter flares on the hit, then they settle.</li>
 *   <li><b>Death</b>: the thrust sputters out over a few ticks and the bob stops; the fall itself is the shared death
 *   pose (DeathFall#poseHover), so the body is kept and the faction's finish plays on it.</li>
 * </ul>
 * Signs (AnimState's): pitch + = nose down, roll + = rolled to the unit's right, yaw rate + = turning right; arm
 * angles + = swung forward; lift in model px (+ = up). Angles are degrees.
 */
public final class HoverMotion {

    private HoverMotion() {}

    /** Spring slots ({@link Life#SPRING} wide) and the smoothed travel speed. */
    static final int PITCH = 0, ROLL = PITCH + Life.SPRING, WORK = ROLL + Life.SPRING, SPEED = WORK + Life.SPRING;
    public static final int STATE = SPEED + 3;

    /** A hover body's tunables. One set per body type; {@link #drone()} is the Builder Drone's. */
    public static final class Params {
        /** The hull's bottom above the ground at rest, model px (the death drop falls this far). */
        public float hoverPx = 10f;
        /** The idle bob: amplitude px and period ticks. */
        public float bobPx = 0.8f, bobTicks = 48f;
        /** Nose-down degrees per block/tick of speed and per block/tick^2 of acceleration; the cap. */
        public float pitchPerSpeed = 48f, pitchPerAccel = 220f, maxPitch = 14f;
        /** Roll degrees per degree/tick of turn (at cruise; a third of it turning on the spot); the cap. */
        public float bankPerYawRate = 1.6f, maxBank = 18f;
        /** The speed (blocks/tick) the lean and the bank are scaled to. */
        public float cruise = 0.25f;
        /** Springs: stiffness (rad/tick) and damping (below 1 rocks once before it settles - a hull on its air). */
        public float pitchOmega = 0.45f, pitchZeta = 0.62f, rollOmega = 0.40f, rollZeta = 0.75f;
        /** Arm angles: folded at rest, reaching while working, wound back and struck forward in an attack. */
        public float armRest = 6f, armWork = 42f, armWind = -26f, armStrike = 58f;
        /** The strike's timeline as fractions of the swing: wind-up ends, the hit lands, the hold lasts. */
        public float windP = 0.32f, hitP = 0.45f, holdP = 0.12f;
        /** Ticks the thrust takes to sputter out when the body dies. */
        public float thrustOutTicks = 6f;

        public static Params drone() {
            return new Params();
        }
    }

    /** One frame's pose. */
    public static final class Pose {
        /** px up from the rest height; degrees. */
        public float lift, pitch, roll, yaw, armL, armR;
        /** The emitter's light 0..1 and the thrust 0..1 (out = 0). */
        public float emit, thrust;
    }

    public static float[] newState() {
        return new float[STATE];
    }

    /**
     * One client tick (dt ticks) of the springs toward this tick's travel: speed (blocks/tick), acceleration along the
     * heading (blocks/tick^2), yaw rate (degrees/tick), whether it is working.
     */
    public static void step(Params p, float[] s, float speed, float accel, float yawRate, boolean working, float dt) {
        if (!(dt > 0f))
            return;
        float pitch = Mth.clamp(speed * p.pitchPerSpeed + accel * p.pitchPerAccel, -0.6f * p.maxPitch, p.maxPitch);
        float atSpeed = Math.min(1f, Math.abs(speed) / p.cruise);
        float roll = Mth.clamp(yawRate * p.bankPerYawRate * (0.35f + 0.65f * atSpeed), -p.maxBank, p.maxBank);
        Life.spring(s, PITCH, pitch, p.pitchOmega, p.pitchZeta, dt);
        Life.spring(s, ROLL, roll, p.rollOmega, p.rollZeta, dt);
        Life.spring(s, WORK, working ? 1f : 0f, 0.3f, 1f, dt);
        s[SPEED] += (Math.abs(speed) - s[SPEED]) * Math.min(1f, 0.25f * dt);
    }

    /**
     * The pose at {@code partial} between the last two steps. {@code age} ticks + partial (the bob's clock), the
     * unit's id (its phase and tempo), {@code attackP} 0..1 through a swing (negative: not attacking), {@code deathT}
     * ticks into its death (negative: alive).
     */
    public static void pose(Params p, float[] s, float partial, float age, int id, float attackP, float deathT,
                            Pose out) {
        out.pitch = Mth.clamp(Life.springAt(s, PITCH, partial), -0.6f * p.maxPitch, p.maxPitch);
        out.roll = Mth.clamp(Life.springAt(s, ROLL, partial), -p.maxBank, p.maxBank);
        float work = Mth.clamp(Life.springAt(s, WORK, partial), 0f, 1f);
        float tempo = 1f + 0.08f * (2f * Desync.hash01(id, 8200) - 1f);
        float phase = age * tempo / p.bobTicks + Desync.hash01(id, 8100);
        out.yaw = 2f * Mth.sin(phase * 0.43f * Mth.TWO_PI + 0.7f);   // the nose drifts a little, slower than the bob
        if (deathT >= 0f) {
            out.lift = 0f;
            out.armL = out.armR = p.armRest;
            out.emit = 0f;
            out.yaw = 0f;
            out.thrust = deathT >= p.thrustOutTicks ? 0f
                : (1f - deathT / p.thrustOutTicks) * (0.55f + 0.45f * Mth.cos(deathT * 2.6f));
            return;
        }
        out.thrust = 1f;
        float steady = 1f - 0.6f * Mth.clamp(s[SPEED] / p.cruise, 0f, 1f);
        out.lift = p.bobPx * steady * (0.82f * Mth.sin(phase * Mth.TWO_PI) + 0.18f * Mth.sin(phase / 0.37f * Mth.TWO_PI + 1.3f));
        float arm = p.armRest + (p.armWork - p.armRest) * work + work * 2.5f * Mth.sin(age * Mth.TWO_PI / 9f + id);
        float pulse = 0.5f + 0.5f * Mth.sin(age * Mth.TWO_PI / 7f + id * 0.37f);
        float emit = 0.15f + work * (0.35f + 0.65f * pulse);
        if (attackP >= 0f) {
            float a = Math.min(attackP, 1f);
            float wound = p.armWind;
            float flare;
            if (a < p.windP) {
                arm = arm + (wound - arm) * Life.smooth(a / p.windP);
                flare = 0f;
            } else if (a < p.hitP) {
                float u = Life.smooth((a - p.windP) / (p.hitP - p.windP));
                arm = wound + (p.armStrike - wound) * u;
                flare = u;
            } else if (a < p.hitP + p.holdP) {
                arm = p.armStrike;
                flare = 1f;
            } else {
                float u = Life.smooth((a - p.hitP - p.holdP) / (1f - p.hitP - p.holdP));
                arm = p.armStrike + (arm - p.armStrike) * u;
                flare = 1f - u;
            }
            emit = Math.max(emit, flare);
        }
        out.armL = out.armR = arm;
        out.emit = Mth.clamp(emit, 0f, 1f);
    }
}
