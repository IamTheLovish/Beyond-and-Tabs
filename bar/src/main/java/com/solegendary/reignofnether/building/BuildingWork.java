package com.solegendary.reignofnether.building;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Building animations, slice 1 (lovish, Oct 30: smoke + lit windows first, medium strength; "smoke = working", so idle
 * labs lose their roof smoke; the vehicle-lab gate waits for the Engine Shed round). A lab shows it is producing in two
 * ways the RTS camera reads from any side: smoke from its real stacks and warm light in its windows. The design is the
 * Itachi 2 API designer pass of Oct 30 (D:\Command Center\itachi2-api\runs\20261030-033623-anim-design and -cont).
 *
 * Common code (no client classes): the rules the two client renderers follow - BuildingAmbienceClientEvents (stack
 * smoke) and PartRenderClientEvents (window light) - kept pure so BuildingWorkTest can pin them.
 *
 * TESTS FIRST: this is the stub the red run is taken on (today's behaviour where there is one); the next commit
 * replaces it.
 */
public final class BuildingWork {
    private BuildingWork() { }

    // ------------------------------------------------------------------ stack smoke (medium)

    public static final int SMOKE_PERIOD = 8;
    public static final int PUFFS_PER_STACK = 1;
    public static final int CHUFF_PUFFS = 0;
    public static final int WORK_BUDGET = Integer.MAX_VALUE;
    public static final double PUFF_RISE = 0.075;

    // ------------------------------------------------------------------ window light (medium)

    public static final int WINDOW_ON_TICKS = 0, WINDOW_OFF_TICKS = 0;
    public static final float WINDOW_HALF = 0f;
    public static final float WARM_R = 1f, WARM_G = 1f, WARM_B = 1f;
    public static final float WHITE_CONCRETE_R = 207 / 255f, WHITE_CONCRETE_G = 213 / 255f, WHITE_CONCRETE_B = 214 / 255f;
    public static final int MAX_WINDOW_FACES = 48;

    public static boolean isLab(boolean isCapitol, int maxLevel) {
        return maxLevel > 1;
    }

    public static boolean isWorking(boolean built, boolean inSight, boolean queued) {
        return built && queued;
    }

    public static boolean breathesIdle(boolean isCapitol, boolean isEnergy, int maxLevel) {
        return isCapitol || isEnergy || maxLevel > 1;
    }

    public static float windowLevel(boolean working, long ticksSinceChange) {
        return 0f;
    }

    /** A building's working state and the game time it last changed. */
    public static final class Clock {
        private boolean working = false;
        private long since = Long.MIN_VALUE / 4;

        public boolean update(boolean working, long now) {
            return false;
        }

        public boolean working() {
            return working;
        }

        public float windowLevel(long now) {
            return BuildingWork.windowLevel(working, now - since);
        }
    }

    // ------------------------------------------------------------------ a unit leaves: the chuff

    private static final Map<Object, Integer> RELEASES = new WeakHashMap<>();

    public static void noteRelease(Object placement) { }

    public static int takeReleases(Object placement) {
        return 0;
    }

    // ------------------------------------------------------------------ where: stacks and windows

    public static boolean isCampfire(String blockPath) {
        return false;
    }

    public static final int NOT_GLASS = -1, FULL = 0, PANE_X = 1, PANE_Z = 2;

    public static int glassKind(String blockPath, boolean north, boolean south, boolean west, boolean east) {
        return NOT_GLASS;
    }

    public static List<BlockPos> stackTops(Collection<BlockPos> campfires, Set<BlockPos> solid) {
        return new ArrayList<>();
    }

    public static final int UP = 1, NORTH = 2, SOUTH = 3, WEST = 4, EAST = 5;

    public record Face(BlockPos cell, int dir, boolean pane) { }

    public static List<Face> windowFaces(Map<BlockPos, Integer> glass, Set<BlockPos> solid, int cap) {
        return new ArrayList<>();
    }
}
