package com.solegendary.reignofnether.unit;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/**
 * Focus fire does not bounce its target around (design-draft-combat-feel-2026-10-30.md, gain 5). Every hit with an
 * attacker is a vanilla 0.4 shove, and RTS hits stack (LivingEntityMixin.unitHitsStack opens the hurt window for each
 * attacker), so a unit under six attackers was shoved six times in a few ticks and jittered across the ground.
 * <p>
 * A unit now takes at most one light shove per {@link #WINDOW_TICKS} server ticks. The first shove that lands opens a
 * window; inside it a shove lands only if it is stronger than every shove that landed in it (the strongest wins), or if
 * it is the same blow's second shove (the attack-knockback part Mob.doHurtTarget adds after the hurt shove: same tick,
 * same attacker), so one attacker hits exactly as before. Heavy shoves ({@link #HEAVY} or more: attack knockback 1.0 or
 * more through doHurtTarget's x0.5, the Mammoth, Ravager, Warden, Marauder's ability and full Horde momentum, and every
 * ability's fling) always land.
 * <p>
 * Pure: the caller gives the victim's id, the server tick and the attacker's id ({@link #NO_HITTER} when the shove
 * follows no hit this tick). One small entry per recently shoved entity; {@link #sweep} drops the closed ones.
 */
public final class KnockbackGate {

    /** At most one light shove per this many server ticks. */
    public static final int WINDOW_TICKS = 5;
    /** A shove this strong or more always lands. */
    public static final double HEAVY = 0.5;
    /** The shove follows no hit this tick (a direct knockback call, or a hit with no living attacker). */
    public static final int NO_HITTER = -1;

    private static final class Window {
        int openTick;
        int hitter;
        double strength;
    }

    private final Int2ObjectOpenHashMap<Window> windows = new Int2ObjectOpenHashMap<>();

    /** Whether a shove of {@code strength} on {@code victimId} at server tick {@code now} lands; a landed one counts. */
    public boolean allow(int victimId, int now, double strength, int hitter) {
        Window w = windows.get(victimId);
        if (w == null || !open(w, now)) {
            if (w == null) {
                w = new Window();
                windows.put(victimId, w);
            }
            w.openTick = now;
            w.hitter = hitter;
            w.strength = strength;
            return true;
        }
        boolean sameBlow = now == w.openTick && hitter != NO_HITTER && hitter == w.hitter;
        if (strength >= HEAVY || sameBlow || strength > w.strength) {
            w.strength = Math.max(w.strength, strength);
            return true;
        }
        return false;
    }

    /** Drops the windows that have closed (call it now and then, e.g. once a second). */
    public void sweep(int now) {
        windows.values().removeIf(w -> !open(w, now));
    }

    /** Entries held: for tests. */
    public int size() {
        return windows.size();
    }

    public void clear() {
        windows.clear();
    }

    /** A window is open for WINDOW_TICKS ticks from its first shove; a tick before it (a new world) closes it too. */
    private static boolean open(Window w, int now) {
        int age = now - w.openTick;
        return age >= 0 && age < WINDOW_TICKS;
    }
}
