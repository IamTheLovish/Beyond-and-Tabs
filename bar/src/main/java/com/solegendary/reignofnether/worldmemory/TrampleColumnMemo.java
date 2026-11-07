package com.solegendary.reignofnether.worldmemory;

/**
 * One chunk's trample columns as the world showed them in the current sample pass: the top block's Y and its surface
 * wear, read once a pass however many walks cross the column. A marching block of units shares every column of its
 * path, and each walk asked the world for each of them again (the 52-unit cost test: 2.39-6.44 us a walk, budget 3.0,
 * stress 42306472448). Exact: the world does not change during one pass (one thread, one tick), and a new pass, or a walk
 * outside a pass, has a new epoch, so a column is always read again then. Pure (no Minecraft), so JUnit counts the reads.
 */
public final class TrampleColumnMemo {

    /** The world under a column: {@link #pack} of the top block's Y and its surface wear (0 = it never wears). */
    @FunctionalInterface
    public interface Ground {
        long topAndWear(int worldX, int worldZ);
    }

    public static long pack(int topY, float wear) {
        return ((long) topY << 32) | (Float.floatToRawIntBits(wear) & 0xFFFFFFFFL);
    }

    private final long[] epochOf = new long[256];   // 0 = never read (epochs start at 1)
    private final int[] top = new int[256];
    private final float[] wear = new float[256];

    /**
     * The peak wear this walk puts on column (x, z): 0 when the top block is more than {@code reachY} from the feet or is
     * hard ground, else {@code peak} scaled by the surface's wear, at least 1 (the rule GroundMemoryServer applied
     * a column at a time before this memo).
     */
    public int peakAt(int x, int z, long epoch, double footY, double reachY, int peak, Ground ground) {
        int cell = GroundMemory.index(x, z);
        if (epochOf[cell] != epoch) {
            long tw = ground.topAndWear(x, z);
            top[cell] = (int) (tw >> 32);
            wear[cell] = Float.intBitsToFloat((int) tw);
            epochOf[cell] = epoch;
        }
        if (Math.abs(top[cell] + 1 - footY) > reachY)
            return 0;
        float w = wear[cell];
        if (w <= 0)
            return 0;
        return Math.max(1, Math.round(peak * w));
    }
}
