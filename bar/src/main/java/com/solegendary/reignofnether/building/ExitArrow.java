package com.solegendary.reignofnether.building;

/**
 * Pure geometry of a production building's exit arrow (tester request 2026-10-26: "all structures should have an arrow
 * that shows where the units they produce comes out of"). The arrow's spot comes from ProductionPlacement.exitArrowPos
 * (the same calls produceUnit makes); this class only decides which way it points and its outline, so it is unit
 * tested without Minecraft. No Minecraft types here.
 */
public final class ExitArrow {

    private ExitArrow() { }

    /** Blocks behind the spot the arrow's tail starts; the rest of its length is in front of it. */
    public static final float TAIL = 0.6f;
    /** Total length in blocks: readable at normal RTS zoom. */
    public static final float LENGTH = 3.2f;
    public static final float SHAFT_HALF_WIDTH = 0.28f;
    public static final float HEAD_LENGTH = 1.2f;
    public static final float HEAD_HALF_WIDTH = 0.8f;
    /** Below this distance the walk target is "here" (an outdoor spawn stands on its default rally). */
    public static final double MIN_WALK = 1.0;

    /** Client asks while something is shown: once a second at rest. */
    public static final int ASK_STEADY_TICKS = 20;
    /** ...and at most 4 a second (the server's PacketAuth limit) when something new needs an answer. */
    public static final int ASK_BURST_TICKS = 5;

    /** Whether the client should send an ExitArrowServerboundPacket now. */
    public static boolean shouldAsk(boolean anythingShown, boolean somethingNew, long ticksSinceLastAsk) {
        // a negative gap: the game clock went back (another world or server), so the last ask means nothing (review C-11)
        return anythingShown && (ticksSinceLastAsk < 0 || ticksSinceLastAsk >= (somethingNew ? ASK_BURST_TICKS : ASK_STEADY_TICKS));
    }

    /**
     * The type key a ghost asks the server with, {@code key} giving the type's registry key or null: "" for no type,
     * null for a type with no key. A custom building lives outside the BUILDING registry (Codex review 2026-11-01:
     * {@code getKey(...).toString()} threw while rendering its ghost), so it asks nothing and shows no ghost arrow.
     */
    public static <T> String askKey(T type, java.util.function.Function<T, ?> key) {
        if (type == null)
            return "";
        Object k = key.apply(type);
        return k == null ? null : k.toString();
    }

    /** What the placement ghost's arrow does this frame (ExitArrowClientEvents.render). */
    public enum GhostStep { ASK, NONE, DRAW }

    /**
     * {@code known}: the server's rule for the type is already held, {@code hasRule}: that rule spawns units,
     * {@code askKey}: {@link #askKey} of the type (only used while not known). A type with no key never asks.
     */
    public static GhostStep ghostStep(boolean known, boolean hasRule, String askKey) {
        if (!known)
            return askKey == null ? GhostStep.NONE : GhostStep.ASK;
        return hasRule ? GhostStep.DRAW : GhostStep.NONE;
    }

    /**
     * The way a produced unit walks out, as a unit vector into {@code out[0..1]} (x, z): from the spawn spot towards the
     * spot it walks to with no rally (the building's default rally, its outdoor spawn point). An outdoor spawn already
     * stands there, so it points away from the building's centre instead; if the spawn is the centre as well, along
     * -x -z (the side the default outdoor spawn point is on).
     */
    public static void walkDir(double sx, double sz, double tx, double tz, double cx, double cz, float[] out) {
        double dx = tx - sx, dz = tz - sz;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < MIN_WALK) {
            dx = sx - cx;
            dz = sz - cz;
            d = Math.sqrt(dx * dx + dz * dz);
        }
        if (d < 1e-6) {
            dx = -1;
            dz = -1;
            d = Math.sqrt(2);
        }
        out[0] = (float) (dx / d);
        out[1] = (float) (dz / d);
    }

    /**
     * The arrow's outline as 7 points (x, z pairs into {@code out[0..13]}): tail-left, shaft-left, head-left, tip,
     * head-right, shaft-right, tail-right. {@code pad} grows it on every side (the dark edge drawn under it).
     */
    public static void outline(float ax, float az, float dx, float dz, float pad, float[] out) {
        float px = -dz, pz = dx;   // left of the direction
        float tail = -TAIL - pad;
        float neck = LENGTH - TAIL - HEAD_LENGTH;
        float tip = LENGTH - TAIL + pad * 1.6f;
        float sw = SHAFT_HALF_WIDTH + pad, hw = HEAD_HALF_WIDTH + pad * 1.6f;
        put(out, 0, ax, az, dx, dz, px, pz, tail, sw);
        put(out, 1, ax, az, dx, dz, px, pz, neck, sw);
        put(out, 2, ax, az, dx, dz, px, pz, neck, hw);
        put(out, 3, ax, az, dx, dz, px, pz, tip, 0);
        put(out, 4, ax, az, dx, dz, px, pz, neck, -hw);
        put(out, 5, ax, az, dx, dz, px, pz, neck, -sw);
        put(out, 6, ax, az, dx, dz, px, pz, tail, -sw);
    }

    private static void put(float[] out, int i, float ax, float az, float dx, float dz, float px, float pz, float along, float side) {
        out[i * 2] = ax + dx * along + px * side;
        out[i * 2 + 1] = az + dz * along + pz * side;
    }
}
