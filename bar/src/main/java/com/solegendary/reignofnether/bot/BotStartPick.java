package com.solegendary.reignofnether.bot;

import com.solegendary.reignofnether.maps.MapLayout;
import com.solegendary.reignofnether.maps.StartPhase;
import com.solegendary.reignofnether.resources.MetalPatches;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Where a bot puts its commander in the start-position phase (design/maps.md section 5, design/bot.md "Start
 * position"): inside its team's box, near the richest metal it can reach from its slot. A BAR player does the same
 * with the map open - the start positions and the patches are public, so this is no map hack.
 * <p>
 * Candidates are a grid every {@link #STEP} blocks within {@link #SEARCH} of the slot (never further: every slot
 * owns the metal of its own wedge, and a bot that wandered across its box would crowd a teammate). Each is scored
 * by the patch value it has within {@link #REACH} blocks, falling off with distance, minus a small pull back to the
 * slot and a penalty for stepping toward the map centre (closer to the enemy, a raid's first stop). A candidate whose
 * capitol square would cover a patch's reserved square is skipped (the extractor must still fit). Runs once per
 * bot per match: a few hundred candidates times the patch list.
 */
public final class BotStartPick {
    private BotStartPick() { }

    static final int SEARCH = 32, STEP = 4;
    /** Patches within this of the capitol count toward a spot (the home ring is at 18). */
    static final double REACH = 30;

    /**
     * How good (x, z) is as a start for a slot at (sx, sz), {@code ring} blocks from the map centre (cx, cz): the
     * summed value of the patches within REACH, each weighted 1 at the capitol down to 0 at REACH, minus 0.01 per
     * block from the slot and 0.05 per block closer to the centre than 12 blocks inside the ring. Public for the test.
     */
    public static double score(int x, int z, int sx, int sz, int cx, int cz, int ring, List<BlockPos> patches,
                               ToDoubleFunction<BlockPos> value) {
        double s = 0;
        for (BlockPos p : patches) {
            double dx = p.getX() - x, dz = p.getZ() - z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < REACH)
                s += value.applyAsDouble(p) * (1 - d / REACH);
        }
        s -= 0.01 * Math.hypot(x - sx, z - sz);
        double r = Math.hypot(x - cx, z - cz);
        if (r < ring - 12)
            s -= 0.05 * (ring - 12 - r);
        return s;
    }

    /** True when a capitol of half-width {@code half} at (x, z) keeps clear of every patch's reserved square. */
    static boolean clearOfPatches(int x, int z, int half, List<BlockPos> patches) {
        int r = half + MetalPatches.RESERVED_HALF;
        for (BlockPos p : patches)
            if (Math.abs(p.getX() - x) <= r && Math.abs(p.getZ() - z) <= r)
                return false;
        return true;
    }

    /**
     * The best start for a bot's pick, or null when no candidate is inside the box and clear (the caller then keeps
     * the slot). {@code half} is the capitol's half-width (StartAreaClearing.capitolSpan).
     */
    public static int[] choose(StartPhase phase, StartPhase.Pick pick, List<BlockPos> patches, ToDoubleFunction<BlockPos> value,
                               int half) {
        MapLayout.Slot slot = phase.layout.slots.get(Math.min(pick.slot, phase.layout.slots.size() - 1));
        int sx = phase.cx + slot.dx(), sz = phase.cz + slot.dz();
        int ring = (int) Math.round(Math.hypot(slot.dx(), slot.dz()));
        int[] best = null;
        double bestScore = -Double.MAX_VALUE;
        for (int dx = -SEARCH; dx <= SEARCH; dx += STEP)
            for (int dz = -SEARCH; dz <= SEARCH; dz += STEP) {
                if (dx * dx + dz * dz > SEARCH * SEARCH)
                    continue;
                int x = sx + dx, z = sz + dz;
                if (!phase.inBox(pick.team, x, z) || !clearOfPatches(x, z, half, patches))
                    continue;
                double s = score(x, z, sx, sz, phase.cx, phase.cz, ring, patches, value);
                if (s > bestScore) {
                    bestScore = s;
                    best = new int[] { x, z };
                }
            }
        return best;
    }
}
