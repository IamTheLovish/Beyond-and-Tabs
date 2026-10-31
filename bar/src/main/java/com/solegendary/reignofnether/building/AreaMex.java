package com.solegendary.reignofnether.building;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * BAR's area mex, the pure part: which free metal patches lie inside the circle under the cursor, and how they are
 * split between the selected builders. Kept free of client classes so AreaMexTest and AreaMexGameTests run headless.
 */
public final class AreaMex {

    private AreaMex() {}

    public static final int MIN_RADIUS = 4, MAX_RADIUS = 32;

    /** One selected builder: entity id and where its share of the sites starts (horizontal). */
    public record Builder(int id, double x, double z) {

        /**
         * BAR's start rule (getAvgPositionOfValidBuilders, useQueueEnd = shift), the one both area-mex paths use:
         * with Shift the new sites join the end of the builder's queue, so it starts where that queue ends
         * ({@code queueEnd} x/z, null when it has none); otherwise, or with no queue, where it stands.
         */
        public static Builder of(int id, double hereX, double hereZ, int[] queueEnd, boolean shift) {
            return shift && queueEnd != null ? new Builder(id, queueEnd[0], queueEnd[1]) : new Builder(id, hereX, hereZ);
        }
    }

    public static int clampRadius(int radius) {
        return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
    }

    /** Patch centres within {@code radius} (horizontal) of the cursor that are not taken, in the order given. */
    public static List<BlockPos> freeInside(Iterable<BlockPos> centres, Predicate<BlockPos> taken, BlockPos cursor, int radius) {
        List<BlockPos> out = new ArrayList<>();
        if (cursor == null)
            return out;
        long r2 = (long) radius * radius;
        for (BlockPos c : centres) {
            long dx = c.getX() - cursor.getX(), dz = c.getZ() - cursor.getZ();
            if (dx * dx + dz * dz <= r2 && !taken.test(c))
                out.add(c);
        }
        return out;
    }

    /**
     * Splits {@code patches} between builders, round-robin by nearest: in turn each builder takes the patch nearest
     * to where it will be (its previous pick), until none are left. So a lone builder walks a {@link #chain} and
     * several builders each get a neighbouring cluster. Each list is in build order. With no builders, one entry
     * under the key {@link #NO_BUILDER} holds the chain from {@code cursor}.
     */
    public static Map<Integer, List<BlockPos>> split(List<BlockPos> patches, List<Builder> builders, BlockPos cursor) {
        Map<Integer, List<BlockPos>> out = new LinkedHashMap<>();
        if (builders.isEmpty()) {
            out.put(NO_BUILDER, cursor == null ? chain(patches, 0, 0) : chain(patches, cursor.getX(), cursor.getZ()));
            return out;
        }
        List<BlockPos> left = new ArrayList<>(patches);
        double[] px = new double[builders.size()], pz = new double[builders.size()];
        for (int i = 0; i < builders.size(); i++) {
            out.put(builders.get(i).id(), new ArrayList<>());
            px[i] = builders.get(i).x();
            pz[i] = builders.get(i).z();
        }
        while (!left.isEmpty())
            for (int i = 0; i < builders.size() && !left.isEmpty(); i++) {
                BlockPos next = nearest(left, px[i], pz[i]);
                left.remove(next);
                out.get(builders.get(i).id()).add(next);
                px[i] = next.getX();
                pz[i] = next.getZ();
            }
        return out;
    }

    /**
     * The area-mex build order (BAR's calculateCmdOrder): a greedy nearest-neighbour chain from (x, z) - each next
     * site is the one nearest the previous one. Sorting by distance from the start zig-zagged across a ring of
     * patches (tester, Oct 26: "this pathing doesn't make sense"). The input is not changed.
     */
    public static List<BlockPos> chain(List<BlockPos> patches, double x, double z) {
        List<BlockPos> left = new ArrayList<>(patches);
        List<BlockPos> out = new ArrayList<>(left.size());
        while (!left.isEmpty()) {
            BlockPos next = nearest(left, x, z);
            left.remove(next);
            out.add(next);
            x = next.getX();
            z = next.getZ();
        }
        return out;
    }

    /**
     * The queue flag the client sends with target {@code index} of one click's extractor orders: with {@code append}
     * (Shift, or a one-click quick-build) every target joins the builders' queue, as BAR's Shift always does; without
     * it the first target replaces their orders and the rest join it. MexQueueGameTests drives the server with this.
     */
    public static boolean queueFor(int index, boolean append) {
        return append || index > 0;
    }

    /** Key of the single group {@link #split} returns when no builder is selected. */
    public static final int NO_BUILDER = Integer.MIN_VALUE;

    /** The patch nearest (x, z); equal distances go by x then z, so the order never flickers frame to frame. */
    private static BlockPos nearest(List<BlockPos> from, double x, double z) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos c : from) {
            double dx = c.getX() - x, dz = c.getZ() - z, d = dx * dx + dz * dz;
            if (best == null || d < bestD || d == bestD && (c.getX() < best.getX()
                    || c.getX() == best.getX() && c.getZ() < best.getZ())) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }
}
