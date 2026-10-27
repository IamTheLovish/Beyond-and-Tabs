package com.solegendary.reignofnether.building;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AreaMexTest {

    private static BlockPos p(int x, int z) {
        return new BlockPos(x, 64, z);
    }

    @Test
    void onlyFreePatchesInsideTheCircleCount() {
        BlockPos in = p(5, 0), edge = p(12, 0), out = p(13, 0), taken = p(0, 6);
        List<BlockPos> got = AreaMex.freeInside(List.of(in, edge, out, taken), Set.of(taken)::contains, p(0, 0), 12);
        assertEquals(List.of(in, edge), got);
    }

    @Test
    void radiusIsClamped() {
        assertEquals(4, AreaMex.clampRadius(-3));
        assertEquals(32, AreaMex.clampRadius(99));
        assertEquals(12, AreaMex.clampRadius(12));
    }

    @Test
    void oneBuilderWalksANearestFirstChain() {
        Map<Integer, List<BlockPos>> m = AreaMex.split(List.of(p(30, 0), p(10, 0), p(20, 0)),
            List.of(new AreaMex.Builder(7, 0, 0)), p(0, 0));
        assertEquals(List.of(p(10, 0), p(20, 0), p(30, 0)), m.get(7));
    }

    @Test
    void severalBuildersSplitRoundRobinByNearest() {
        // west builder at x=0, east builder at x=100; patches on both sides
        List<BlockPos> patches = List.of(p(5, 0), p(95, 0), p(15, 0), p(85, 0));
        Map<Integer, List<BlockPos>> m = AreaMex.split(patches,
            List.of(new AreaMex.Builder(1, 0, 0), new AreaMex.Builder(2, 100, 0)), p(50, 0));
        assertEquals(List.of(p(5, 0), p(15, 0)), m.get(1));
        assertEquals(List.of(p(95, 0), p(85, 0)), m.get(2));
    }

    @Test
    void everyPatchIsAssignedExactlyOnce() {
        List<BlockPos> patches = List.of(p(1, 1), p(2, 9), p(8, 3), p(5, 5), p(9, 9));
        Map<Integer, List<BlockPos>> m = AreaMex.split(patches,
            List.of(new AreaMex.Builder(1, 0, 0), new AreaMex.Builder(2, 10, 10), new AreaMex.Builder(3, 5, 5)), p(5, 5));
        int total = m.values().stream().mapToInt(List::size).sum();
        assertEquals(patches.size(), total);
        assertTrue(m.values().stream().flatMap(List::stream).distinct().count() == patches.size());
    }

    @Test
    void noBuilderOrdersFromTheCursor() {
        Map<Integer, List<BlockPos>> m = AreaMex.split(List.of(p(9, 0), p(3, 0)), List.of(), p(0, 0));
        assertEquals(List.of(p(3, 0), p(9, 0)), m.get(AreaMex.NO_BUILDER));
    }
}
