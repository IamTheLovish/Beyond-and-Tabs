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

    /** One selected builder: entity id and where it stands (horizontal). */
    public record Builder(int id, int x, int z) {}

    public static int clampRadius(int radius) {
        return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
    }

    /** Patch centres within {@code radius} (horizontal) of the cursor that are not taken, in the order given. */
    public static List<BlockPos> freeInside(Iterable<BlockPos> centres, Predicate<BlockPos> taken, BlockPos cursor, int radius) {
        List<BlockPos> out = new ArrayList<>();
        if (cursor == null)
            return out;
        long r2 = (long) radius * radius;
        for (BlockPos c : centres)
            if (dist2(c.getX(), c.getZ(), cursor.getX(), cursor.getZ()) <= r2 && !taken.test(c))
                out.add(c);
        return out;
    }

    /**
     * Splits {@code patches} between builders, round-robin by nearest: in turn each builder takes the patch nearest
     * to where it will be (its previous pick), until none are left. So a lone builder walks a nearest-first chain and
     * several builders each get a neighbouring cluster. Each list is in build order. With no builders, one entry
     * under the key {@link #NO_BUILDER} holds the patches nearest-first from {@code cursor}.
     */
    public static Map<Integer, List<BlockPos>> split(List<BlockPos> patches, List<Builder> builders, BlockPos cursor) {
        Map<Integer, List<BlockPos>> out = new LinkedHashMap<>();
        List<BlockPos> left = new ArrayList<>(patches);
        if (builders.isEmpty()) {
            int cx = cursor == null ? 0 : cursor.getX(), cz = cursor == null ? 0 : cursor.getZ();
            List<BlockPos> chain = new ArrayList<>();
            int x = cx, z = cz;
            while (!left.isEmpty()) {
                BlockPos next = nearest(left, x, z);
                left.remove(next);
                chain.add(next);
                x = next.getX();
                z = next.getZ();
            }
            out.put(NO_BUILDER, chain);
            return out;
        }
        int[] px = new int[builders.size()], pz = new int[builders.size()];
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

    /** Key of the single group {@link #split} returns when no builder is selected. */
    public static final int NO_BUILDER = Integer.MIN_VALUE;

    private static BlockPos nearest(List<BlockPos> from, int x, int z) {
        BlockPos best = null;
        long bestD = Long.MAX_VALUE;
        for (BlockPos c : from) {
            long d = dist2(c.getX(), c.getZ(), x, z);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    private static long dist2(int ax, int az, int bx, int bz) {
        long dx = ax - bx, dz = az - bz;
        return dx * dx + dz * dz;
    }
}
