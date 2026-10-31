package com.solegendary.reignofnether.unit.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;

import java.util.Set;

/**
 * How far and how hard a unit's VANILLA A* searches (PathNavigationMixin.createPath). Every unit has follow range 64
 * (Unit.getFollowRange), so every vanilla createPath - each chase repath, each move order with the rtsPathfinding
 * gamerule off - built a 129-block navigation region (~100 chunk lookups) and could expand 1024 nodes, even for a
 * melee slot two blocks away. The Oct 22 match ran every path that way and fell 25 s behind.
 *
 * <p>Now the first search reaches {@code clamp(distance + MARGIN, MIN_RANGE, full)} blocks with a node budget scaled
 * the same way (wide bodies at most {@link #WIDE_NODE_CAP} nodes, their A* is the slow one). If that finds no path or
 * only a partial one, the search runs ONCE more exactly as before (full range, full budget), so a long detour round a
 * wall is still found - the only cost of a miss is the short search before it. Server side only; plain counters for
 * the [Paths] line (ServerTickProfile).
 */
public final class VanillaPathBudget {
    private VanillaPathBudget() { }

    public static final float MARGIN = 16f;
    public static final float MIN_RANGE = 16f;
    public static final int WIDE_NODE_CAP = 400;
    /** Never search with less than this share of the full node budget (MIN_RANGE / 64). */
    public static final float MIN_NODE_SHARE = 0.25f;

    /** Counters since the last [Paths] line: unit createPath calls, how many took the short search, how many fell back. */
    public static int calls = 0, shortSearches = 0, fallbacks = 0;
    /** Fallbacks a wide unit skipped since the last [Paths] line: the same search failed a moment ago (WidePathMemo). */
    public static int wideRepeats = 0;
    /** Every fallback since the server started (never reset: tests compare it before and after). */
    public static long fallbacksTotal = 0;

    /** The farthest target's distance from the mob (blocks), the first search's range is built from it. */
    public static double farthest(Mob mob, Set<BlockPos> targets) {
        double best = 0;
        for (BlockPos t : targets) {
            double dx = t.getX() + 0.5 - mob.getX(), dy = t.getY() - mob.getY(), dz = t.getZ() + 0.5 - mob.getZ();
            best = Math.max(best, dx * dx + dy * dy + dz * dz);
        }
        return Math.sqrt(best);
    }

    /** GameTests only: caps the short search's range so a small arena can make it miss and take the fallback. */
    public static float shortRangeCapForTests = Float.MAX_VALUE;

    /** The first search's range, or {@code full} when the short search would not be shorter. */
    public static float shortRange(double distance, float full) {
        float r = (float) Math.max(MIN_RANGE, Math.min(full, distance + MARGIN));
        return Math.min(Math.min(r, full), shortRangeCapForTests);
    }

    /** The node-budget multiplier for a short search of {@code range} out of {@code full}. */
    public static float nodeMultiplier(float baseMultiplier, float range, float full, boolean wide, double baseFollowRange) {
        float m = baseMultiplier * Math.max(MIN_NODE_SHARE, range / full);
        if (wide && baseFollowRange > 0) {
            // PathFinder's budget is floor(base follow range * 16) * multiplier (PathNavigation's constructor)
            float cap = (float) (WIDE_NODE_CAP / (baseFollowRange * 16.0));
            m = Math.min(m, cap);
        }
        return m;
    }

    public static String report() {
        String s = "[Paths] last 10 s: " + calls + " unit vanilla createPath, " + shortSearches + " short searches, "
            + fallbacks + " full-range fallbacks, " + wideRepeats + " wide-body repeats skipped";
        calls = 0;
        wideRepeats = 0;
        shortSearches = 0;
        fallbacks = 0;
        return s;
    }
}
