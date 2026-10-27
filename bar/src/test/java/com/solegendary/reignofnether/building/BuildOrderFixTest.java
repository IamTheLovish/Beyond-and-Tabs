package com.solegendary.reignofnether.building;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pure math of the Oct 26 build-order fixes: the area-mex tour (BUG 1) and the half-built click pick (BUG 2). */
class BuildOrderFixTest {

    private static BlockPos p(int x, int z) {
        return new BlockPos(x, 64, z);
    }

    @Test
    void theTourVisitsTheNearestNextSiteNotTheNextNearestToTheStart() {
        List<BlockPos> sites = List.of(p(-7, 0), p(8, 0), p(-15, 0), p(16, 0));
        assertEquals(List.of(p(-7, 0), p(-15, 0), p(8, 0), p(16, 0)), AreaMexCircle.tour(sites, 0, 0));
        // the old order zig-zags
        assertEquals(List.of(p(-7, 0), p(8, 0), p(-15, 0), p(16, 0)), AreaMexCircle.nearestFirst(sites, 0, 0));
        assertTrue(AreaMexCircle.pathLength(AreaMexCircle.tour(sites, 0, 0), 0, 0)
            < AreaMexCircle.pathLength(AreaMexCircle.nearestFirst(sites, 0, 0), 0, 0));
    }

    @Test
    void aRingIsWalkedAroundNotAcross() {
        List<BlockPos> ring = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            double a = Math.PI * 2 * i / 7;
            ring.add(p((int) Math.round(Math.cos(a) * 14), (int) Math.round(Math.sin(a) * 14)));
        }
        List<BlockPos> tour = AreaMexCircle.tour(ring, 2, 0);
        double len = AreaMexCircle.pathLength(tour, 2, 0);
        // walking round: start leg (~12) plus 6 chords of ~12.1, far below any crossing order
        assertTrue(len < 90, "tour length " + len);
        assertTrue(len <= AreaMexCircle.pathLength(AreaMexCircle.nearestFirst(ring, 2, 0), 2, 0) + 1e-9);
    }

    @Test
    void theTourKeepsItsInputAndIsStableOnTies() {
        List<BlockPos> in = new ArrayList<>(List.of(p(5, 0), p(-5, 0), p(0, 5)));
        List<BlockPos> copy = new ArrayList<>(in);
        assertEquals(AreaMexCircle.tour(in, 0, 0), AreaMexCircle.tour(copy, 0, 0));
        assertEquals(copy, in);
        assertEquals(p(-5, 0), AreaMexCircle.tour(in, 0, 0).get(0));   // tie: lowest x first
    }

    @Test
    void aShiftTourStartsAtTheQueueEndsAPlainOneWhereTheBuildersStand() {
        List<double[]> here = List.of(new double[] { 0, 0 }, new double[] { 10, 0 });
        List<int[]> ends = Arrays.asList(new int[] { 20, 20 }, null);
        assertArrayEquals(new double[] { 5, 0 }, AreaMexCircle.tourStart(here, ends, false));
        assertArrayEquals(new double[] { 15, 10 }, AreaMexCircle.tourStart(here, ends, true));
        assertNull(AreaMexCircle.tourStart(List.of(), List.of(), true));
    }

    @Test
    void aRayThroughAHalfBuiltFrameToTheGroundBehindPicksTheBuilding() {
        // building box (10,65,10)..(16,72,16); the ray comes from -z, high, and lands on the ground at z=19
        GhostHit.Box box = new GhostHit.Box(10, 65, 10, 16, 72, 16);
        double ox = 13.5, oy = 100, oz = -20, tx = 13.5, ty = 65, tz = 19.5;
        double len = Math.sqrt((ty - oy) * (ty - oy) + (tz - oz) * (tz - oz));
        int[] picked = { 13, 64, 19 };
        assertEquals(0, GhostHit.pick(ox, oy, oz, 0, (ty - oy) / len, (tz - oz) / len, len, picked, List.of(box)));
        // a box to the side is not picked; the nearer of two in line is
        GhostHit.Box side = new GhostHit.Box(40, 65, 10, 46, 72, 16);
        assertEquals(-1, GhostHit.pick(ox, oy, oz, 0, (ty - oy) / len, (tz - oz) / len, len, picked, List.of(side)));
        GhostHit.Box behind = new GhostHit.Box(10, 65, 18, 16, 72, 24);
        assertEquals(1, GhostHit.pick(ox, oy, oz, 0, (ty - oy) / len, (tz - oz) / len, len, picked,
            List.of(behind, box)));
    }

    @Test
    void onlyFriendlyUnfinishedBuildingsAreBoxTargets() {
        assertTrue(GhostHit.assistTarget(false, true));
        assertFalse(GhostHit.assistTarget(true, true));
        assertFalse(GhostHit.assistTarget(false, false));
    }
}
