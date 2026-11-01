package com.solegendary.reignofnether.unit.pathfinding;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Set;

/**
 * A wide unit's full-range vanilla search that found no way to its targets, remembered for a while (one per
 * navigation, PathNavigationMixin). lovish's Oct 29 game (server log and its [PerfAuto] profiles, perf survey Oct 30):
 * a Sun Colossus's vanilla A* took 12-33 ms PER search on the server thread (a husk's 0.15 ms) - 8 colossi were 6 ms of
 * every tick, one search alone a whole slow tick. A body that wide has the slow A* (VanillaPathBudget.WIDE_NODE_CAP),
 * and a chase on a target it cannot reach repeated the same two searches - the short one, then the full-range fallback
 * - at every repath (every 20 ticks, plus StuckNudge's), each finding again that there is no way.
 *
 * <p>Now, for {@link #TICKS} ticks after a full-range search found no complete path, the same unit searching for the
 * targets from the same spot (both about) keeps the short search's answer and skips the fallback. "About": within
 * {@link #NEAR} blocks - a StuckNudge sidestep moves the unit a block, and a chase re-claims another ring slot round
 * the same target. The short search still runs every time, so a way that opens within its range (a gate, a wall
 * knocked down, the target stepping off its ledge) is found at once; only a detour longer than the short range waits
 * up to {@link #TICKS} ticks. A complete path, other targets or a unit that walked off start over. Server side; not
 * thread-safe (the server thread only, like the navigation it belongs to).
 */
public final class WidePathMemo {
    /** How long a failed full-range search answers for the same targets from the same spot: 5 s. */
    public static final int TICKS = 100;
    /** "About the same" place, for the unit and for each target: within this many blocks on each axis. */
    public static final int NEAR = 3;

    /** The memo of a navigation (PathNavigationMixin implements it). */
    public interface Holder {
        WidePathMemo reignofnether$wideMemo();
    }

    private BlockPos from;
    private List<BlockPos> targets;
    private long until = Long.MIN_VALUE;
    /** Full-range searches this navigation ran, for GameTests (WidePathMemoGameTests). */
    public int fullSearches;

    /** A full-range search for these targets from about here found no way less than {@link #TICKS} ticks ago. */
    public boolean failedLately(BlockPos from, Set<BlockPos> targets, long now) {
        if (this.targets == null || now >= until || !near(from, this.from) || targets.size() != this.targets.size())
            return false;
        for (BlockPos t : targets) {
            boolean seen = false;
            for (BlockPos r : this.targets)
                if (near(t, r)) {
                    seen = true;
                    break;
                }
            if (!seen)
                return false;
        }
        return true;
    }

    /** A full-range search just ran: remembered when it found no complete path, forgotten when it did. */
    public void fullSearched(BlockPos from, Set<BlockPos> targets, long now, boolean reached) {
        fullSearches++;
        if (reached) {
            this.targets = null;
            return;
        }
        this.from = from.immutable();
        this.targets = targets.stream().map(BlockPos::immutable).toList();
        this.until = now + TICKS;
    }

    static boolean near(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) <= NEAR && Math.abs(a.getY() - b.getY()) <= NEAR
            && Math.abs(a.getZ() - b.getZ()) <= NEAR;
    }
}
