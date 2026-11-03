package com.solegendary.reignofnether.building;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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
 */
public final class BuildingWork {
    private BuildingWork() { }

    // ------------------------------------------------------------------ stack smoke (medium)

    /** Ticks between working-smoke passes. */
    public static final int SMOKE_PERIOD = 8;
    /**
     * Puffs from each stack a pass. Critic round 2 (Oct 30) judged round 3's column - ~15 puffs per block of height -
     * readable and not loud; this keeps that density at the faster rise below (6 / 8 ticks / 0.048 = 15.6 per block).
     */
    public static final int PUFFS_PER_STACK = 6;
    /** Extra puffs from each stack on the pass after a unit leaves: the "chuff" that marks the moment (twice a pass). */
    public static final int CHUFF_PUFFS = 12;
    /** Labs a working pass may puff from, whatever the base size (8v8 safety); idle life keeps its own 40. */
    public static final int WORK_BUDGET = 24;
    /**
     * A puff's life: vanilla's cosy campfire smoke lives 80-130 ticks (105 on average, PUFF_MAX_LIFE_TICKS at most). Round
     * 3 tried the signal-fire smoke (280-330 ticks): critic round 2 saw it still hanging over a lab 10 s after its queue
     * emptied - against lovish's "smoke = working". With this life the last puff is gone within 6.5 s.
     */
    public static final int PUFF_LIFE_TICKS = 105, PUFF_MAX_LIFE_TICKS = 130;
    /** A puff's rise, blocks a tick: PUFF_LIFE_TICKS x this = the ~5-block column (lovish's medium). */
    public static final double PUFF_RISE = 0.048;

    // ------------------------------------------------------------------ window light (medium)

    /** Ticks at half light on the way in, and on the way out (heavier: the light lingers a beat). */
    public static final int WINDOW_ON_TICKS = 10, WINDOW_OFF_TICKS = 20;
    public static final float WINDOW_HALF = 0.6f;
    /**
     * Warm white, as a tint on white concrete drawn fullbright: lit pane luma ~0.76, saturation ~0.23 (lovish's medium:
     * 0.72-0.80 and 0.20-0.28). No orange - orange-red is the enemy in relation mode.
     */
    public static final float WARM_R = 1f, WARM_G = 0.90f, WARM_B = 0.75f;
    /** Vanilla white concrete's mean texel (the face the light is drawn with). */
    public static final float WHITE_CONCRETE_R = 207 / 255f, WHITE_CONCRETE_G = 213 / 255f, WHITE_CONCRETE_B = 214 / 255f;
    /** Lit faces a lab draws at most, roof first (the top of a building is its face from the RTS camera). */
    public static final int MAX_WINDOW_FACES = 48;

    /** A lab: a levelled army building. The capitol levels too but keeps its idle life. */
    public static boolean isLab(boolean isCapitol, int maxLevel) {
        return !isCapitol && maxLevel > 1;
    }

    /**
     * Working: built, in sight now, something in the queue. The server sends a lab's queue to everyone who can see it;
     * a remembered building's queue is stale, so it shows nothing.
     */
    public static boolean isWorking(boolean built, boolean inSight, boolean queued) {
        return built && inSight && queued;
    }

    /** Idle life (smoke, glints, petals): capitols and energy buildings breathe; labs don't - smoke on a lab is work. */
    public static boolean breathesIdle(boolean isCapitol, boolean isEnergy, int maxLevel) {
        return isCapitol || isEnergy;
    }

    /** Window light, 0 (dark) to 1, {@code ticksSinceChange} after the working state last changed: steps, never a pop. */
    public static float windowLevel(boolean working, long ticksSinceChange) {
        if (ticksSinceChange < 0)   // the clock went back (a new world): settled
            ticksSinceChange = Long.MAX_VALUE;
        if (working)
            return ticksSinceChange < WINDOW_ON_TICKS ? WINDOW_HALF : 1f;
        return ticksSinceChange < WINDOW_OFF_TICKS ? WINDOW_HALF : 0f;
    }

    /** A building's working state and the game time it last changed. */
    public static final class Clock {
        private boolean working = false;
        private long since = Long.MIN_VALUE / 4;   // never changed: long settled

        /** True when the state changed. */
        public boolean update(boolean working, long now) {
            if (working == this.working)
                return false;
            this.working = working;
            since = now;
            return true;
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

    /** A unit just left this building (the client's COMPLETE_PRODUCTION). Client thread only. */
    public static void noteRelease(Object placement) {
        if (placement != null)
            RELEASES.merge(placement, 1, Integer::sum);
    }

    /** The releases since the last call, once. */
    public static int takeReleases(Object placement) {
        if (placement == null)
            return 0;
        Integer n = RELEASES.remove(placement);
        return n == null ? 0 : n;
    }

    // ------------------------------------------------------------------ where: stacks and windows

    public static boolean isCampfire(String blockPath) {
        return blockPath.equals("campfire") || blockPath.equals("soul_campfire");
    }

    /** Glass kinds: a full block lights any open face but its underside; a pane only the two faces across its run. */
    public static final int NOT_GLASS = -1, FULL = 0, PANE_X = 1, PANE_Z = 2;

    /** A block's glass kind from its id path and, for panes, which sides it connects to. */
    public static int glassKind(String blockPath, boolean north, boolean south, boolean west, boolean east) {
        if (blockPath.endsWith("glass_pane")) {
            boolean alongX = west || east, alongZ = north || south;
            if (alongX == alongZ)   // a corner or a lone post: no flat face to light
                return NOT_GLASS;
            return alongX ? PANE_X : PANE_Z;
        }
        return blockPath.endsWith("glass") ? FULL : NOT_GLASS;
    }

    private static final Comparator<BlockPos> TOP_FIRST = Comparator.<BlockPos>comparingInt(BlockPos::getY).reversed()
        .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ);

    /** Every open campfire on a lab is a stack: one with no structure block straight above it smokes into the open. */
    public static List<BlockPos> stackTops(Collection<BlockPos> campfires, Set<BlockPos> solid) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos c : campfires)
            if (!solid.contains(c.above()))
                out.add(c);
        out.sort(TOP_FIRST);
        return out;
    }

    /** Face directions as Direction ordinals (DOWN 0 is never lit). */
    public static final int UP = 1, NORTH = 2, SOUTH = 3, WEST = 4, EAST = 5;
    private static final int[] DX = { 0, 0, 0, 0, -1, 1 }, DY = { -1, 1, 0, 0, 0, 0 }, DZ = { 0, 0, -1, 1, 0, 0 };

    /** A lit window face: the glass cell, the way its light faces, and whether it is a pane (lit on its middle plane). */
    public record Face(BlockPos cell, int dir, boolean pane) { }

    /**
     * The faces of a building's glass that light up: every face whose ray out of the building is clear of structure
     * blocks to the edge of its box - the outside of a wall or a roof, never the room side. Roof faces first, then from
     * the top down; at most {@code cap}.
     */
    public static List<Face> windowFaces(Map<BlockPos, Integer> glass, Set<BlockPos> solid, int cap) {
        List<Face> out = new ArrayList<>();
        if (glass.isEmpty() || cap <= 0)
            return out;
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (BlockPos p : solid) {
            x0 = Math.min(x0, p.getX()); y0 = Math.min(y0, p.getY()); z0 = Math.min(z0, p.getZ());
            x1 = Math.max(x1, p.getX()); y1 = Math.max(y1, p.getY()); z1 = Math.max(z1, p.getZ());
        }
        for (Map.Entry<BlockPos, Integer> e : glass.entrySet()) {
            BlockPos c = e.getKey();
            int kind = e.getValue();
            for (int d = UP; d <= EAST; d++) {
                if (kind == PANE_X && d != NORTH && d != SOUTH || kind == PANE_Z && d != WEST && d != EAST)
                    continue;
                int x = c.getX() + DX[d], y = c.getY() + DY[d], z = c.getZ() + DZ[d];
                boolean open = true;
                while (x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1) {
                    if (solid.contains(new BlockPos(x, y, z))) {
                        open = false;
                        break;
                    }
                    x += DX[d]; y += DY[d]; z += DZ[d];
                }
                if (open)
                    out.add(new Face(c, d, kind != FULL));
            }
        }
        out.sort(Comparator.comparingInt((Face f) -> f.dir() == UP ? 0 : 1)
            .thenComparing(Face::cell, TOP_FIRST).thenComparingInt(Face::dir));
        return out.size() > cap ? new ArrayList<>(out.subList(0, cap)) : out;
    }
}
