package com.solegendary.reignofnether.building;

import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.function.Predicate;

/**
 * BAR's mex snap: while a Metal Extractor is being placed, its ghost jumps to the nearest free metal patch near the
 * cursor, so a click a few blocks off still lands centred on the patch (Lane C playtest, Oct 20: "auto snap
 * extractors to the closest free spot"). Kept free of client classes so ExtractorSnapGameTests can check it headless.
 */
public final class ExtractorSnap {

    private ExtractorSnap() {}

    /** How far (blocks, horizontal) from the cursor a free patch pulls the ghost onto itself. */
    public static final int RANGE = 12;

    /** The free patch centre nearest the cursor within {@code range}, or null to leave the ghost on the cursor. */
    public static BlockPos nearestFree(Collection<BlockPos> centres, Predicate<BlockPos> taken, BlockPos cursor, int range) {
        if (cursor == null)
            return null;
        BlockPos best = null;
        long bestD = (long) range * range;
        for (BlockPos c : centres) {
            long dx = c.getX() - cursor.getX(), dz = c.getZ() - cursor.getZ();
            long d = dx * dx + dz * dz;
            if (d <= bestD && !taken.test(c)) {
                best = c;
                bestD = d;
            }
        }
        return best;
    }
}
