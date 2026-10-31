package com.solegendary.reignofnether.building;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure decisions of the Oct 26 build-order fixes, the same functions the client calls: the area-mex order and
 * its start rule (BUG 1, AreaMexCircle.order / AreaMex.split / AreaMex.Builder.of) and the half-built click pick
 * (BUG 2, GhostHit.rank inside GhostHit.target; target itself is pinned by BuildOrderFixGameTests AC-3/AC-4).
 */
class BuildOrderFixTest {

    private static BlockPos p(int x, int z) {
        return new BlockPos(x, 64, z);
    }

    /** One builder standing at (x, z), no Shift. */
    private static List<AreaMex.Builder> at(double x, double z) {
        return List.of(AreaMex.Builder.of(1, x, z, null, false));
    }

    /** The old order: sorted by distance from the start (what the tester saw zig-zag). Written out here. */
    private static List<BlockPos> distanceSort(List<BlockPos> sites, double x, double z) {
        List<BlockPos> out = new ArrayList<>(sites);
        out.sort(Comparator.comparingDouble(s -> Math.hypot(s.getX() - x, s.getZ() - z)));
        return out;
    }

    @Test
    void theTourVisitsTheNearestNextSiteNotTheNextNearestToTheStart() {
        List<BlockPos> sites = List.of(p(-7, 0), p(8, 0), p(-15, 0), p(16, 0));
        List<BlockPos> tour = AreaMexCircle.order(sites, at(0, 0), p(99, 99));
        assertEquals(List.of(p(-7, 0), p(-15, 0), p(8, 0), p(16, 0)), tour);
        // the old order zig-zags
        assertEquals(List.of(p(-7, 0), p(8, 0), p(-15, 0), p(16, 0)), distanceSort(sites, 0, 0));
        assertTrue(AreaMexCircle.pathLength(tour, 0, 0) < AreaMexCircle.pathLength(distanceSort(sites, 0, 0), 0, 0));
    }

    @Test
    void aRingIsWalkedAroundNotAcross() {
        List<BlockPos> ring = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            double a = Math.PI * 2 * i / 7;
            ring.add(p((int) Math.round(Math.cos(a) * 14), (int) Math.round(Math.sin(a) * 14)));
        }
        List<BlockPos> tour = AreaMexCircle.order(ring, at(2, 0), p(99, 99));
        double len = AreaMexCircle.pathLength(tour, 2, 0);
        // walking round: start leg (~12) plus 6 chords of ~12.1, far below any crossing order
        assertTrue(len < 90, "tour length " + len);
        assertTrue(len <= AreaMexCircle.pathLength(distanceSort(ring, 2, 0), 2, 0) + 1e-9);
    }

    @Test
    void theTourKeepsItsInputAndIsStableOnTies() {
        List<BlockPos> in = new ArrayList<>(List.of(p(5, 0), p(-5, 0), p(0, 5)));
        List<BlockPos> copy = new ArrayList<>(in);
        assertEquals(AreaMexCircle.order(in, at(0, 0), p(0, 0)), AreaMexCircle.order(copy, at(0, 0), p(0, 0)));
        assertEquals(copy, in);
        assertEquals(p(-5, 0), AreaMexCircle.order(in, at(0, 0), p(0, 0)).get(0));   // tie: lowest x first
    }

    @Test
    void aShiftTourStartsAtTheQueueEndsAPlainOneWhereTheBuildersStand() {
        int[] end = { 20, 20 };
        assertEquals(new AreaMex.Builder(1, 0, 0), AreaMex.Builder.of(1, 0, 0, end, false));
        assertEquals(new AreaMex.Builder(1, 20, 20), AreaMex.Builder.of(1, 0, 0, end, true));
        assertEquals(new AreaMex.Builder(2, 10, 0), AreaMex.Builder.of(2, 10, 0, null, true));
        // the drag averages the starts: plain (0,0)+(10,0) -> (5,0); Shift (20,20)+(10,0) -> (15,10)
        List<BlockPos> sites = List.of(p(5, 1), p(15, 9));
        assertEquals(List.of(p(5, 1), p(15, 9)), AreaMexCircle.order(sites,
            List.of(AreaMex.Builder.of(1, 0, 0, end, false), AreaMex.Builder.of(2, 10, 0, null, false)), p(99, 99)));
        assertEquals(List.of(p(15, 9), p(5, 1)), AreaMexCircle.order(sites,
            List.of(AreaMex.Builder.of(1, 0, 0, end, true), AreaMex.Builder.of(2, 10, 0, null, true)), p(99, 99)));
        // no builder: from the fallback (the circle's centre)
        assertEquals(List.of(p(15, 9), p(5, 1)), AreaMexCircle.order(sites, List.of(), p(20, 10)));
    }

    @Test
    void bothAreaMexPathsUseTheQueueEndWithShift() {
        // the builder stands west, its queue ends east; patches near each
        int[] end = { 30, 0 };
        List<BlockPos> patches = List.of(p(5, 0), p(25, 0));
        List<AreaMex.Builder> shift = List.of(AreaMex.Builder.of(7, 0, 0, end, true));
        List<AreaMex.Builder> plain = List.of(AreaMex.Builder.of(7, 0, 0, end, false));
        assertEquals(List.of(p(25, 0), p(5, 0)), AreaMex.split(patches, shift, p(15, 0)).get(7));   // Shift circle
        assertEquals(List.of(p(25, 0), p(5, 0)), AreaMexCircle.order(patches, shift, p(15, 0)));     // drag
        assertEquals(List.of(p(5, 0), p(25, 0)), AreaMex.split(patches, plain, p(15, 0)).get(7));
        assertEquals(List.of(p(5, 0), p(25, 0)), AreaMexCircle.order(patches, plain, p(15, 0)));
    }

    @Test
    void aRayThroughAHalfBuiltFrameToTheGroundBehindPicksTheBuilding() {
        // building box (10,65,10)..(16,72,16); the ray comes from -z, high, and lands on the ground at z=19
        double ox = 13.5, oy = 100, oz = -20, tx = 13.5, ty = 65, tz = 19.5;
        double len = Math.sqrt((ty - oy) * (ty - oy) + (tz - oz) * (tz - oz));
        double dy = (ty - oy) / len, dz = (tz - oz) / len;
        BlockPos picked = new BlockPos(13, 64, 19);
        double box = GhostHit.rank(ox, oy, oz, 0, dy, dz, len, picked, new BlockPos(10, 65, 10), new BlockPos(16, 72, 16));
        assertTrue(box >= 0, "the ray through the frame hits its box");
        // a box to the side is not hit; of two in line the nearer ranks first
        assertEquals(-1, GhostHit.rank(ox, oy, oz, 0, dy, dz, len, picked,
            new BlockPos(40, 65, 10), new BlockPos(46, 72, 16)));
        double behind = GhostHit.rank(ox, oy, oz, 0, dy, dz, len, picked,
            new BlockPos(10, 65, 18), new BlockPos(16, 72, 24));
        assertTrue(behind >= 0 && box < behind, "box " + box + ", behind " + behind);
    }

    @Test
    void onlyFriendlyUnfinishedBuildingsAreBoxTargets() {
        assertTrue(GhostHit.assistTarget(false, true));
        assertFalse(GhostHit.assistTarget(true, true));
        assertFalse(GhostHit.assistTarget(false, false));
    }
}
