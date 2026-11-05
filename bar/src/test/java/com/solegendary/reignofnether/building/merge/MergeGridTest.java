package com.solegendary.reignofnether.building.merge;

import com.solegendary.reignofnether.building.merge.MergeGrid.Cell;
import com.solegendary.reignofnether.building.merge.MergeGrid.Match;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a grid of the same building is cut into complexes (claude/merged-buildings.md 2b): biggest shapes first, so a
 * 4x3 grid of twelve farms is one Quarter + one Pair + one single and never three Farms; pairs last, as a maximum
 * matching, so a Quarter that loses its centre becomes four Pairs rather than three plus two strays. The cut must be
 * deterministic (the server scores the same grid the client previews) and must never put a member in two complexes.
 */
class MergeGridTest {

    private static final int SLICE_1 = 9;   // MergedBuildings.MAX_MEMBERS: slice 1 stops at the Quarter

    private static long count(List<Match> matches, MergedShape shape) {
        return matches.stream().filter(m -> m.shape() == shape).count();
    }

    private static List<Cell> cells(int... xz) {
        List<Cell> out = new ArrayList<>();
        for (int i = 0; i < xz.length; i += 2)
            out.add(new Cell(xz[i], xz[i + 1]));
        return out;
    }

    /** Every cut must be a real partition: matches inside the input, no cell twice, each w*d == its shape's members. */
    private static void assertSound(List<Cell> input, List<Match> matches) {
        Set<Cell> in = new HashSet<>(input);
        Set<Cell> used = new HashSet<>();
        for (Match m : matches) {
            assertEquals(m.shape().members(), m.w() * m.d(), "shape/rectangle mismatch " + m);
            for (Cell c : m.cells()) {
                assertTrue(in.contains(c), "match " + m + " covers " + c + " which is not in the grid");
                assertTrue(used.add(c), "cell " + c + " is in two complexes");
            }
        }
        List<Cell> singles = MergeGrid.singles(input, matches);
        assertEquals(in.size(), used.size() + singles.size(), "every cell is either merged or single");
        for (Cell c : singles)
            assertFalse(used.contains(c));
    }

    // ---------------------------------------------------------------- the ladder, biggest first

    @Test
    void aFullRectangleOfAShapeBecomesThatOneShape() {
        Object[][] table = {
            // w, d, shape
            { 2, 1, MergedShape.PAIR }, { 1, 2, MergedShape.PAIR },
            { 2, 2, MergedShape.FARM },
            { 3, 2, MergedShape.ROW }, { 2, 3, MergedShape.ROW },
            { 3, 3, MergedShape.QUARTER },
        };
        for (Object[] row : table) {
            int w = (Integer) row[0], d = (Integer) row[1];
            List<Cell> grid = MergeGrid.rectangle(w, d);
            List<Match> matches = MergeGrid.partition(grid, SLICE_1);
            assertEquals(1, matches.size(), w + "x" + d + " must be one complex, got " + matches);
            assertEquals(row[2], matches.get(0).shape(), w + "x" + d);
            assertEquals(w, matches.get(0).w(), w + "x" + d + " keeps its orientation");
            assertEquals(d, matches.get(0).d(), w + "x" + d + " keeps its orientation");
            assertTrue(MergeGrid.singles(grid, matches).isEmpty());
        }
    }

    @Test
    void aLoneBuildingStaysSingle() {
        List<Cell> grid = MergeGrid.rectangle(1, 1);
        List<Match> matches = MergeGrid.partition(grid, SLICE_1);
        assertTrue(matches.isEmpty());
        assertEquals(grid, MergeGrid.singles(grid, matches));
    }

    @Test
    void twelveInAFourByThreeIsAQuarterAPairAndASingleNeverThreeFarms() {
        List<Cell> grid = MergeGrid.rectangle(4, 3);
        List<Match> matches = MergeGrid.partition(grid, SLICE_1);
        assertEquals(1, count(matches, MergedShape.QUARTER));
        assertEquals(1, count(matches, MergedShape.PAIR));
        assertEquals(0, count(matches, MergedShape.FARM));
        assertEquals(2, matches.size());
        assertEquals(1, MergeGrid.singles(grid, matches).size());
        assertSound(grid, matches);
    }

    @Test
    void theBiggestShapeAlwaysWinsOverSmallerOnes() {
        // 5x5 with slice-1's cap: a Quarter first, then Rows, then a Farm - never twelve pairs
        List<Cell> grid = MergeGrid.rectangle(5, 5);
        List<Match> matches = MergeGrid.partition(grid, SLICE_1);
        assertEquals(1, count(matches, MergedShape.QUARTER));
        assertEquals(2, count(matches, MergedShape.ROW));
        assertEquals(1, count(matches, MergedShape.FARM));
        assertEquals(0, count(matches, MergedShape.PAIR));
        assertEquals(25, matches.stream().mapToInt(m -> m.shape().members()).sum());
        assertSound(grid, matches);
    }

    @Test
    void theMemberCapLimitsTheShapes() {
        List<Cell> grid = MergeGrid.rectangle(3, 3);
        // cap 4: no Row or Quarter. A Farm in the corner, then the L-shaped leftover of five cells is a path -> two pairs
        List<Match> capped = MergeGrid.partition(grid, 4);
        assertEquals(1, count(capped, MergedShape.FARM));
        assertEquals(2, count(capped, MergedShape.PAIR));
        assertEquals(1, MergeGrid.singles(grid, capped).size());
        assertSound(grid, capped);
        // cap 2: only pairs - a 2x2 is two pairs, not a Farm
        List<Match> pairsOnly = MergeGrid.partition(MergeGrid.rectangle(2, 2), 2);
        assertEquals(2, count(pairsOnly, MergedShape.PAIR));
        assertEquals(2, pairsOnly.size());
        // cap 1: nothing merges
        assertTrue(MergeGrid.partition(grid, 1).isEmpty());
        assertTrue(MergeGrid.partition(grid, 0).isEmpty());
    }

    @Test
    void theLiveCapRaisesTheCeilingToTheCitadelAndTheWonder() {
        assertEquals(MergedShape.CITADEL, MergeGrid.partition(MergeGrid.rectangle(4, 4), 16).get(0).shape());
        assertEquals(MergedShape.WONDER, MergeGrid.partition(MergeGrid.rectangle(5, 5), 25).get(0).shape());
        // a Citadel (16) does not fit a cap of 9: it is cut into smaller shapes, all of them sound
        List<Cell> grid = MergeGrid.rectangle(4, 4);
        assertSound(grid, MergeGrid.partition(grid, SLICE_1));
    }

    // ---------------------------------------------------------------- pairs as a maximum matching

    @Test
    void aQuarterThatLosesItsCentreBecomesFourPairsWithNoStray() {
        List<Cell> ring = new ArrayList<>(MergeGrid.rectangle(3, 3));
        ring.remove(new Cell(1, 1));
        List<Match> matches = MergeGrid.partition(ring, SLICE_1);
        assertEquals(4, count(matches, MergedShape.PAIR));
        assertEquals(4, matches.size());
        assertTrue(MergeGrid.singles(ring, matches).isEmpty(), "a perfect matching exists, no member may be left single");
        assertSound(ring, matches);
    }

    @Test
    void aGreedyScanWouldStrandMembersButTheMatchingDoesNot() {
        // a 1x4 line: a naive left-to-right scan that paired (1,0)-(2,0) would strand both ends; the matching pairs 0-1 and 2-3
        List<Cell> line = MergeGrid.rectangle(4, 1);
        List<Match> matches = MergeGrid.partition(line, SLICE_1);
        assertEquals(2, count(matches, MergedShape.PAIR));
        assertTrue(MergeGrid.singles(line, matches).isEmpty());
        // a line of five leaves exactly one single
        List<Cell> five = MergeGrid.rectangle(5, 1);
        assertEquals(1, MergeGrid.singles(five, MergeGrid.partition(five, SLICE_1)).size());
    }

    @Test
    void pairsKeepTheirOrientation() {
        Match horizontal = MergeGrid.partition(cells(0, 0, 1, 0), SLICE_1).get(0);
        assertEquals(2, horizontal.w());
        assertEquals(1, horizontal.d());
        Match vertical = MergeGrid.partition(cells(0, 0, 0, 1), SLICE_1).get(0);
        assertEquals(1, vertical.w());
        assertEquals(2, vertical.d());
    }

    @Test
    void buildingsThatDoNotTouchNeverMerge() {
        List<Cell> apart = cells(0, 0, 2, 0, 0, 2, 5, 5);
        List<Match> matches = MergeGrid.partition(apart, SLICE_1);
        assertTrue(matches.isEmpty());
        assertEquals(4, MergeGrid.singles(apart, matches).size());
        // diagonal neighbours do not touch either
        assertTrue(MergeGrid.partition(cells(0, 0, 1, 1), SLICE_1).isEmpty());
    }

    // ---------------------------------------------------------------- the world is the truth (touching)

    @Test
    void aCliffBetweenTwoCellsCutsTheShapeThatWouldSpanIt() {
        // 2x2 block, but (0,0)-(1,0) is a cliff edge: no Farm, the block becomes two vertical pairs
        List<Cell> block = MergeGrid.rectangle(2, 2);
        List<Match> matches = MergeGrid.partition(block, SLICE_1,
            (a, b) -> !(Math.min(a.x(), b.x()) == 0 && a.z() == 0 && b.z() == 0));
        assertEquals(0, count(matches, MergedShape.FARM));
        assertEquals(2, count(matches, MergedShape.PAIR));
        for (Match m : matches) {
            assertEquals(1, m.w(), "the pairs must run along z, across no cliff");
            assertEquals(2, m.d());
        }
        assertSound(block, matches);
    }

    @Test
    void withNothingTouchingNothingMerges() {
        List<Cell> grid = MergeGrid.rectangle(3, 3);
        assertTrue(MergeGrid.partition(grid, SLICE_1, (a, b) -> false).isEmpty());
    }

    // ---------------------------------------------------------------- determinism and soundness

    @Test
    void theCutDoesNotDependOnTheOrderTheCellsComeIn() {
        List<Cell> shape = new ArrayList<>(MergeGrid.rectangle(5, 4));
        shape.remove(new Cell(2, 1));
        shape.remove(new Cell(4, 3));
        List<Match> reference = MergeGrid.partition(shape, SLICE_1);
        Random rng = new Random(12345);
        for (int i = 0; i < 25; i++) {
            List<Cell> shuffled = new ArrayList<>(shape);
            Collections.shuffle(shuffled, rng);
            assertEquals(reference, MergeGrid.partition(shuffled, SLICE_1), "shuffle " + i);
        }
    }

    @Test
    void matchesComeBackSortedRowByRow() {
        List<Match> matches = MergeGrid.partition(MergeGrid.rectangle(6, 4), 4);
        for (int i = 1; i < matches.size(); i++) {
            Match a = matches.get(i - 1), b = matches.get(i);
            assertTrue(a.z0() < b.z0() || (a.z0() == b.z0() && a.x0() <= b.x0()), "out of order: " + a + " then " + b);
        }
    }

    @Test
    void randomBlobsAreAlwaysCutIntoASoundPartition() {
        Random rng = new Random(7);
        for (int round = 0; round < 200; round++) {
            Set<Cell> blob = new HashSet<>();
            int n = 1 + rng.nextInt(30);
            for (int i = 0; i < n; i++)
                blob.add(new Cell(rng.nextInt(7) - 3, rng.nextInt(7) - 3));
            List<Cell> grid = new ArrayList<>(blob);
            for (int cap : new int[] { 2, 4, 9, 16, 25 })
                assertSound(grid, MergeGrid.partition(grid, cap));
        }
    }

    @Test
    void noGridIsLeftWithTwoTouchingSinglesWhenPairsAreAllowed() {
        // maximum matching: if two leftover members touch, the matching could have been bigger
        Random rng = new Random(99);
        for (int round = 0; round < 200; round++) {
            Set<Cell> blob = new HashSet<>();
            for (int i = 0, n = 2 + rng.nextInt(20); i < n; i++)
                blob.add(new Cell(rng.nextInt(5), rng.nextInt(5)));
            List<Cell> grid = new ArrayList<>(blob);
            List<Match> matches = MergeGrid.partition(grid, 2);
            Set<Cell> singles = new HashSet<>(MergeGrid.singles(grid, matches));
            for (Cell c : singles)
                assertFalse(singles.contains(new Cell(c.x() + 1, c.z())) || singles.contains(new Cell(c.x(), c.z() + 1)),
                    "two touching members were both left single in " + grid);
        }
    }

    // ---------------------------------------------------------------- helpers

    @Test
    void rectangleIsRowMajorAndEmptyForNonsense() {
        assertEquals(cells(0, 0, 1, 0, 2, 0, 0, 1, 1, 1, 2, 1), MergeGrid.rectangle(3, 2));
        assertTrue(MergeGrid.rectangle(0, 5).isEmpty());
        assertTrue(MergeGrid.rectangle(4, 0).isEmpty());
        assertTrue(MergeGrid.rectangle(-2, 3).isEmpty());
    }

    @Test
    void aMatchListsItsCellsRowByRowAndKnowsWhatItContains() {
        Match m = new Match(MergedShape.ROW, 2, 5, 3, 2);
        assertEquals(cells(2, 5, 3, 5, 4, 5, 2, 6, 3, 6, 4, 6), m.cells());
        assertTrue(m.contains(new Cell(4, 6)));
        assertFalse(m.contains(new Cell(5, 6)));
        assertFalse(m.contains(new Cell(2, 7)));
        assertFalse(m.contains(new Cell(1, 5)));
    }

    // ---------------------------------------------------------------- the shape ladder

    @Test
    void theLadderListsTheLiveShapesBiggestFirst() {
        assertEquals(List.of(MergedShape.QUARTER, MergedShape.ROW, MergedShape.FARM, MergedShape.PAIR), MergedShape.ladder(9));
        assertEquals(List.of(MergedShape.PAIR), MergedShape.ladder(2));
        assertTrue(MergedShape.ladder(1).isEmpty());
        assertEquals(MergedShape.WONDER, MergedShape.ladder(100).get(0));
    }

    @Test
    void everyShapeIsLookedUpByItsMemberCountAndEarnsMoreThanTheOneBelow() {
        for (MergedShape s : MergedShape.values())
            assertEquals(s, MergedShape.ofMembers(s.members()));
        assertNull(MergedShape.ofMembers(5));
        assertNull(MergedShape.ofMembers(0));
        MergedShape[] all = MergedShape.values();
        for (int i = 1; i < all.length; i++) {
            assertTrue(all[i].members() > all[i - 1].members(), all[i] + " must be bigger than " + all[i - 1]);
            assertTrue(all[i].bonus > all[i - 1].bonus, all[i] + " must earn more than " + all[i - 1]);
        }
        // the bonuses stay small (+4% .. +15%): the big steps add capabilities instead
        assertEquals(8, MergedShape.FARM.bonusPercent());
        assertEquals(15, MergedShape.WONDER.bonusPercent());
        assertEquals(1.08f, MergedShape.FARM.outputMultiplier(), 1e-6f);
    }
}
