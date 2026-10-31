package com.solegendary.reignofnether.perf;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The frame table behind EntityShadowMixin's bare-cell skip (PR #460): what a frame learns, it forgets at the next. */
class ShadowCellsTest {

    @Test
    void remembersEachCellForTheFrameOnly() {
        ShadowCells cells = new ShadowCells();
        long a = 0x123456789L, b = -42L;
        assertEquals(ShadowCells.UNKNOWN, cells.get(a));
        cells.put(a, true);
        cells.put(b, false);
        assertEquals(ShadowCells.BARE, cells.get(a));
        assertEquals(ShadowCells.OPEN, cells.get(b));
        cells.put(a, false);
        assertEquals(ShadowCells.OPEN, cells.get(a), "a cell looked at again keeps the last answer");

        cells.newFrame();
        assertEquals(ShadowCells.UNKNOWN, cells.get(a), "a new frame forgets every cell");
        assertEquals(ShadowCells.UNKNOWN, cells.get(b));
    }

    @Test
    void growsPastHalfFullAndKeepsEveryAnswer() {
        ShadowCells cells = new ShadowCells();
        Random rnd = new Random(31);
        Map<Long, Boolean> truth = new HashMap<>();
        int n = ShadowCells.START_CAPACITY * 3;   // forces two doublings
        for (int i = 0; i < n; i++) {
            // block positions as a squad covers them: dense x / z, a few heights
            long key = net(rnd.nextInt(200) - 100, 60 + rnd.nextInt(4), rnd.nextInt(200) - 100);
            boolean bare = rnd.nextBoolean();
            cells.put(key, bare);
            truth.put(key, bare);
        }
        assertTrue(cells.capacity() >= 2 * truth.size(), "at most half full: " + cells.capacity() + " for " + truth.size());
        for (Map.Entry<Long, Boolean> e : truth.entrySet())
            assertEquals(e.getValue() ? ShadowCells.BARE : ShadowCells.OPEN, cells.get(e.getKey()));

        cells.newFrame();
        for (long key : truth.keySet())
            assertEquals(ShadowCells.UNKNOWN, cells.get(key));
        // the next frame fills the same table again
        cells.put(net(1, 64, 1), true);
        assertEquals(ShadowCells.BARE, cells.get(net(1, 64, 1)));
    }

    /** BlockPos.asLong's packing (26 bits x, 26 bits z, 12 bits y), without loading Minecraft. */
    private static long net(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
