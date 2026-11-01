package com.solegendary.reignofnether.unit.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Path;

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
 *
 * <p>Every unit, not only wide ones (Oct 31, lovish's game 03:14: 42 headhunters were 18% of the server tick, their
 * vanilla searches ~5 ms each, 158 of 209 short searches in 10 s failing on to the full range; ticks spiked to 184 ms).
 * A ranged unit chases with navigation.moveTo(target): standing at the end of a partial path to a target it cannot
 * reach, vanilla searched again on EVERY tick. So two answers are remembered, for any unit:
 * <ul>
 * <li>any search that found no complete path answers again for {@link #REPEAT_TICKS} ticks (the same targets from
 *     the same spot, "about" as above): the path it found is handed back, no search at all - a chase that asks every
 *     tick searches once a second;</li>
 * <li>a failed FULL-range search answers for {@link #TICKS} ticks, as before for wide bodies: the short search runs,
 *     the fallback does not.</li>
 * </ul>
 * A way that opens is found within a second inside the short range and within {@link #TICKS} ticks beyond it.
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

    /** How long any failed search answers again for the same targets from the same spot, every unit: 1 s. */
    public static final int REPEAT_TICKS = 20;

    private BlockPos from;
    private List<BlockPos> targets;
    private long until = Long.MIN_VALUE;
    /** Full-range searches this navigation ran, for GameTests (WidePathMemoGameTests). */
    public int fullSearches;
    /** Searches of any range this navigation ran, for GameTests. */
    public int searches;

    // the last search that found no complete path, handed back for REPEAT_TICKS
    private BlockPos lastFrom;
    private List<BlockPos> lastTargets;
    private Path lastPath;
    private long lastUntil = Long.MIN_VALUE;

    /** The path a search for these targets from about here found, without reaching them, under {@link #REPEAT_TICKS} ago; else null. */
    public Path repeatOf(BlockPos from, Set<BlockPos> targets, long now) {
        if (lastPath == null || now >= lastUntil || !near(from, lastFrom) || !same(targets, lastTargets))
            return null;
        return lastPath;
    }

    /** A search of any range just ran and gave {@code path}: remembered when it did not reach, forgotten when it did. */
    public void searched(BlockPos from, Set<BlockPos> targets, long now, Path path) {
        searches++;
        if (path == null || path.canReach()) {   // no path at all is vanilla's own answer each time: not held
            lastPath = null;
            lastTargets = null;
            return;
        }
        lastFrom = from.immutable();
        lastTargets = targets.stream().map(BlockPos::immutable).toList();
        lastPath = path;
        lastUntil = now + REPEAT_TICKS;
    }

    /** A full-range search for these targets from about here found no way less than {@link #TICKS} ticks ago. */
    public boolean failedLately(BlockPos from, Set<BlockPos> targets, long now) {
        return this.targets != null && now < until && near(from, this.from) && same(targets, this.targets);
    }

    /** Each target is about one of the remembered ones, and as many. */
    static boolean same(Set<BlockPos> targets, List<BlockPos> remembered) {
        if (remembered == null || targets.size() != remembered.size())
            return false;
        for (BlockPos t : targets) {
            boolean seen = false;
            for (BlockPos r : remembered)
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
