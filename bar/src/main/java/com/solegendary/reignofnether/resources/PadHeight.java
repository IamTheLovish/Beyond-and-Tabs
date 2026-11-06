package com.solegendary.reignofnether.resources;

import java.util.List;

/**
 * The height {@link MetalPatches#stamp} levels a patch's 7x7 pad to. Pure (no Minecraft types) so it is unit tested
 * (PadHeightTest).
 *
 * Two patches closer than a pad's width share cells, and the leveller leaves an earlier pad's cells alone (re-levelling
 * them would cut a step into that pad). So a later pad at its own ground height kept a strip at the neighbour's height:
 * Oct 29, a patch 5 from its neighbour and one block lower had two rows at +1, and a player's 5x5 extractor refused
 * (ground_not_flat) where the bot only got by on its raised-origin retries. A pad that overlaps an earlier one takes
 * that pad's height, so the two are one flat floor - unless they are more than {@link #MAX_SNAP} apart (a cliff: the
 * patch keeps its own ground, as drySpot never moves a patch more than 3 up or down).
 */
public final class PadHeight {

    /** Half-width of a patch's pad (a 7x7) - MetalPatches.PAD_HALF is this value. */
    public static final int PAD_HALF = 3;

    /** The most a pad is raised or lowered to meet an overlapping earlier pad. */
    public static final int MAX_SNAP = 3;

    private PadHeight() { }

    /**
     * @param x       the patch centre's x
     * @param z       the patch centre's z
     * @param ownY    the ground height at the centre
     * @param earlier earlier pads' centres as {x, y, z}
     * @return the pad height: the nearest overlapping earlier pad's, when within MAX_SNAP of {@code ownY}, else ownY
     */
    public static int of(int x, int z, int ownY, List<int[]> earlier) {
        int reach = 2 * PAD_HALF;   // pads overlap when the centres are within two half-widths on both axes
        int best = Integer.MAX_VALUE, y = ownY;
        for (int[] o : earlier) {
            int d = Math.max(Math.abs(o[0] - x), Math.abs(o[2] - z));
            if (d > reach || d >= best || Math.abs(o[1] - ownY) > MAX_SNAP)
                continue;
            best = d;
            y = o[1];
        }
        return y;
    }
}
