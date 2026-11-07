package com.solegendary.reignofnether.worldmemory;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The trample sampler's cost (TrampleGameTests.trample_cost_stays_inside_the_budget failed 5 of 8 runners at 2.39-6.44 us
 * a walk, budget 3.0, stress 42306472448): a marching block of units crosses the same columns walk after walk, and each
 * walk read the world for each of them. The memo reads a column once a pass and answers exactly as the per-column rule
 * did; a saturated cell asks nothing at all.
 */
class TrampleColumnMemoTest {

    private static final double REACH = 1.5;   // GroundMemoryServer.TRAMPLE_REACH_Y

    /** A flat world at top Y 63 of one surface, counting every read. */
    private static final class CountingGround implements TrampleColumnMemo.Ground {
        int topY = 63;
        float wear = TrampleRules.Surface.GRASS.wear;
        int reads = 0;

        @Override
        public long topAndWear(int x, int z) {
            reads++;
            return TrampleColumnMemo.pack(topY, wear);
        }
    }

    @Test
    void a_column_is_read_once_a_pass_however_many_walks_cross_it() {
        TrampleColumnMemo memo = new TrampleColumnMemo();
        CountingGround ground = new CountingGround();
        for (int walk = 0; walk < 52; walk++)
            for (int x = 0; x < 4; x++)
                for (int z = 0; z < 16; z++)
                    memo.peakAt(x, z, 1, 64.0, REACH, 40, ground);
        assertEquals(4 * 16, ground.reads, "52 walks over the same 64 columns in one pass read each column once");
    }

    @Test
    void a_new_pass_reads_the_world_again() {
        TrampleColumnMemo memo = new TrampleColumnMemo();
        CountingGround ground = new CountingGround();
        assertEquals(40, memo.peakAt(5, 5, 1, 64.0, REACH, 40, ground));
        ground.wear = TrampleRules.Surface.NONE.wear;   // the grass was paved between the two passes
        assertEquals(0, memo.peakAt(5, 5, 2, 64.0, REACH, 40, ground), "pass 2 sees the new block");
        assertEquals(2, ground.reads);
    }

    @Test
    void it_answers_as_the_per_column_rule_did() {
        TrampleColumnMemo memo = new TrampleColumnMemo();
        CountingGround ground = new CountingGround();
        long epoch = 0;
        for (TrampleRules.Surface s : TrampleRules.Surface.values())
            for (int topY : new int[] { -64, 47, 63, 200 })
                for (double footY : new double[] { topY + 1.0, topY + 2.4, topY + 2.6, topY - 0.6, topY - 0.4 })
                    for (int peak : new int[] { 1, 3, 40 }) {
                        ground.topY = topY;
                        ground.wear = s.wear;
                        int expected = Math.abs(topY + 1 - footY) > REACH || s == TrampleRules.Surface.NONE
                            ? 0 : Math.max(1, Math.round(peak * s.wear));
                        assertEquals(expected, memo.peakAt(-17, 33, ++epoch, footY, REACH, peak, ground),
                            s + " top " + topY + " feet " + footY + " peak " + peak);
                    }
    }

    @Test
    void walks_at_other_heights_share_the_read_but_not_the_answer() {
        TrampleColumnMemo memo = new TrampleColumnMemo();
        CountingGround ground = new CountingGround();
        assertEquals(40, memo.peakAt(1, 1, 7, 64.0, REACH, 40, ground), "a unit on the ground");
        assertEquals(0, memo.peakAt(1, 1, 7, 70.0, REACH, 40, ground), "a unit on a roof 6 blocks up wears nothing below");
        assertEquals(1, ground.reads);
    }

    @Test
    void a_saturated_cell_never_asks_the_world() {
        GroundMemory mem = new GroundMemory(0);
        Map<Long, Integer> asked = new HashMap<>();
        GroundMemory.TramplePeaks peaks = (x, z) -> {
            asked.merge(((long) x << 32) ^ (z & 0xFFFFFFFFL), 1, Integer::sum);
            return 255;
        };
        mem.addTrample(0, 0, 2.0, 8.5, 12.0, 8.5, 1.0, peaks);   // a full-strength pass saturates the strip's core
        int first = asked.size();
        boolean[] saturated = new boolean[256];
        int saturatedCount = 0;
        for (int cell = 0; cell < 256; cell++) {
            saturated[cell] = mem.get(cell, GroundMemory.TRAMPLE) == 255;
            if (saturated[cell])
                saturatedCount++;
        }
        asked.clear();
        mem.addTrample(0, 0, 2.0, 8.5, 12.0, 8.5, 1.0, peaks);
        for (Long key : asked.keySet()) {
            int x = (int) (key >> 32), z = (int) (long) key;
            assertEquals(false, saturated[GroundMemory.index(x, z)],
                "the world was asked about a cell already saturated before the walk (" + x + ", " + z + ")");
        }
        assertEquals(true, saturatedCount > 0, "the first full-strength walk saturated the strip's core");
        assertEquals(first - saturatedCount, asked.size(), "the second walk asked only about the cells not saturated");
    }
}
