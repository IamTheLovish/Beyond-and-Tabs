package com.solegendary.reignofnether.unit.modelling.motion;

import com.solegendary.reignofnether.unit.modelling.animations.Life;

/**
 * The layout of one unit's motion snapshot in the shared slab (design/living_army.md, section 3): the offsets of its
 * floats from the slot's base. Written once per client tick by {@link Kinematics} and {@link Idle} (through
 * {@link AnimStates}), read by every rig and by the living army's motion pass. Spring slots are {@link Life#SPRING}
 * wide: [value, velocity, previous value]; read them with {@link Life#springAt}.
 *
 * Signs: speeds in blocks per tick, YAW_RATE in degrees per tick (+ = turning right), SP_LEAN degrees (+ = rolled to
 * the unit's right), SP_PITCH degrees (+ = pitched forward), HIT_DIR radians (0 = hit from the front, +PI/2 = from the
 * unit's right), SP_FLINCH_X degrees (+ = torso bent forward), SP_FLINCH_Z degrees (+ = bent to the right),
 * SP_STAGGER (+ = the whole body rocked back, away from HIT_DIR), SP_HEAD_YAW / SP_HEAD_PITCH degrees, SP_ARMS degrees
 * (+ = arms swung forward), SP_WEIGHT (-1 left leg .. 1 right leg).
 */
public final class AnimState {

    private AnimState() {}

    public static final int SPEED = 0, ACCEL = 1, YAW_RATE = 2, VSPEED = 3, SINCE_STOP = 4, SINCE_START = 5,
        EXERTION = 6, HIT_AGE = 7, HIT_DIR = 8, HIT_SIZE = 9, LAST_HEALTH = 10, HIT_HEALTH0 = 11, SEEN_TICK = 12;

    /** The idle scheduler's fields ({@link Idle}). */
    public static final int IDLE_INIT = 16, IDLE_KIND = 17, IDLE_START = 18, IDLE_LEN = 19, IDLE_NEXT = 20,
        IDLE_COUNT = 21, IDLE_LAST = 22, QUIET_UNTIL = 23, WEIGHT_NEXT = 24, WEIGHT_SIDE = 25, WEIGHT_START = 26,
        GLANCE_YAW = 27, GLANCE_PITCH = 28,
        /** The tick the scheduler last ran at (the entity's own tickCount). */
        IDLE_CLOCK = 29;

    /** Spring slots. */
    public static final int SP_LEAN = 30, SP_PITCH = SP_LEAN + Life.SPRING, SP_FLINCH_X = SP_PITCH + Life.SPRING,
        SP_FLINCH_Z = SP_FLINCH_X + Life.SPRING, SP_STAGGER = SP_FLINCH_Z + Life.SPRING,
        SP_HEAD_YAW = SP_STAGGER + Life.SPRING, SP_HEAD_PITCH = SP_HEAD_YAW + Life.SPRING,
        SP_ARMS = SP_HEAD_PITCH + Life.SPRING, SP_WEIGHT = SP_ARMS + Life.SPRING;

    /** Floats per unit (room left for round 2's look solver and foot IK). */
    public static final int STRIDE = 64;

    /** HIT_SIZE values. */
    public static final float NONE = 0f, FLINCH = 1f, STAGGER = 2f;
}
