package com.solegendary.reignofnether.hud;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * The idle-unit buttons' logic with no Minecraft in it (JUnit-testable): which button an idle worker belongs to, and
 * when a press of the commander button also jumps the camera. A builder shows in exactly one of the three buckets.
 */
public final class IdleButtons {
    private IdleButtons() { }

    /** A press this soon after the previous one (ms) counts as a double press. */
    public static final long DOUBLE_PRESS_MS = 400;

    public enum Bucket { WORKER, T2_BUILDER, COMMANDER }

    /** The commander wins over T2 (a commander of a T2 type is still the commander), T2 wins over plain worker. */
    public static Bucket classify(boolean isCommander, boolean isT2Builder) {
        if (isCommander)
            return Bucket.COMMANDER;
        return isT2Builder ? Bucket.T2_BUILDER : Bucket.WORKER;
    }

    public static final class Split<T> {
        public final List<T> workers = new ArrayList<>();
        public final List<T> t2Builders = new ArrayList<>();
        public final List<T> commanders = new ArrayList<>();
    }

    /** Sorts idle units into the three buckets; every input lands in exactly one. */
    public static <T> Split<T> split(Collection<T> idle, Predicate<T> isCommander, Predicate<T> isT2Builder) {
        Split<T> out = new Split<>();
        for (T t : idle) {
            switch (classify(isCommander.test(t), isT2Builder.test(t))) {
                case COMMANDER -> out.commanders.add(t);
                case T2_BUILDER -> out.t2Builders.add(t);
                default -> out.workers.add(t);
            }
        }
        return out;
    }

    /** The commander button's second press: it jumps the camera when pressed twice quickly or while already selected. */
    public static boolean shouldJump(long lastPressMs, long nowMs, boolean alreadySelected) {
        return alreadySelected || (lastPressMs > 0 && nowMs - lastPressMs <= DOUBLE_PRESS_MS);
    }

    /** Whole-percent health for the commander badge, 0..100 (a dead or unset max reads 0). */
    public static int healthPercent(float health, float maxHealth) {
        if (maxHealth <= 0)
            return 0;
        return Math.max(0, Math.min(100, Math.round(100f * health / maxHealth)));
    }
}
