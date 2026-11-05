package com.solegendary.reignofnether.unit.pathfinding;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The movement model grid A* and the flow fields share (GridNeighbors): which steps exist and what they cost, which
 * cells a wide body fits, and how the search is steered away from walls. A broken rule here sends 300 units into a
 * wall or lets a 3-wide mammoth try a 2-wide gap (lovish Oct 21: T3s "slow and clunky, almost unplayable"), so the
 * rules are pinned on small ascii maps.
 * <p>
 * Maps: one char per column (x), one row per z. A digit is solid ground whose standing height is that digit, '~' is
 * shallow water at height 0, 'L' is lava at height 0, '.' is nothing (a hole in the world).
 */
class GridNeighborsTest {

    private static final float EPS = 1e-5f;

    private static final class MapView implements WalkabilityView {
        private final String[] rows;
        private final Set<String> solids = new HashSet<>();
        MobilityClass mob = MobilityClass.HUMANOID;
        int clearance = 2;
        int radius = 0;

        MapView(String... rows) {
            this.rows = rows;
        }

        MapView solid(int x, int y, int z) {
            solids.add(x + "," + y + "," + z);
            return this;
        }

        MapView radius(int r) {
            radius = r;
            return this;
        }

        MapView clearance(int c) {
            clearance = c;
            return this;
        }

        MapView mobility(MobilityClass m) {
            mob = m;
            return this;
        }

        private char cell(int x, int z) {
            if (z < 0 || z >= rows.length || x < 0 || x >= rows[z].length())
                return '.';
            return rows[z].charAt(x);
        }

        @Override
        public byte kindAt(int wx, int y, int wz) {
            char c = cell(wx, wz);
            if (c >= '0' && c <= '9')
                return c - '0' == y ? WalkabilityBuilder.KIND_LAND : WalkabilityBuilder.KIND_BLOCKED;
            if (y != 0)
                return WalkabilityBuilder.KIND_BLOCKED;
            if (c == '~')
                return WalkabilityBuilder.KIND_WATER;
            if (c == 'L')
                return WalkabilityBuilder.KIND_LAVA;
            return WalkabilityBuilder.KIND_BLOCKED;
        }

        @Override public MobilityClass mobility() { return mob; }
        @Override public float fireCost() { return PathfinderConfig.FIRE_AVOID_COST; }
        @Override public int clearanceCells() { return clearance; }
        @Override public int footprintRadius() { return radius; }
        @Override public boolean solidAt(int wx, int y, int wz) { return solids.contains(wx + "," + y + "," + wz); }
        @Override public float crowdAt(int wx, int y, int wz) { return 0f; }
        @Override public boolean canClimb() { return false; }
    }

    // ---------------------------------------------------------------- the neighbour table

    @Test
    void thereAreTwentyDistinctMovesAndNoneIsStandingStill() {
        assertEquals(20, GridNeighbors.COUNT);
        assertEquals(GridNeighbors.COUNT, GridNeighbors.DX.length);
        assertEquals(GridNeighbors.COUNT, GridNeighbors.DZ.length);
        assertEquals(GridNeighbors.COUNT, GridNeighbors.DY.length);
        Set<String> seen = new HashSet<>();
        for (int d = 0; d < GridNeighbors.COUNT; d++) {
            assertFalse(GridNeighbors.DX[d] == 0 && GridNeighbors.DZ[d] == 0 && GridNeighbors.DY[d] == 0, "move " + d + " is a no-op");
            assertTrue(seen.add(GridNeighbors.DX[d] + "," + GridNeighbors.DY[d] + "," + GridNeighbors.DZ[d]), "move " + d + " repeats an earlier one");
            assertTrue(Math.abs(GridNeighbors.DX[d]) <= 1 && Math.abs(GridNeighbors.DZ[d]) <= 1 && Math.abs(GridNeighbors.DY[d]) <= 1);
        }
    }

    @Test
    void theMovesAreEightFlatFourStepsUpAndEightStepsDown() {
        int flat = 0, up = 0, down = 0, upDiagonals = 0, downDiagonals = 0, pureVertical = 0;
        for (int d = 0; d < GridNeighbors.COUNT; d++) {
            boolean diagonal = GridNeighbors.DX[d] != 0 && GridNeighbors.DZ[d] != 0;
            if (GridNeighbors.DX[d] == 0 && GridNeighbors.DZ[d] == 0)
                pureVertical++;
            switch (GridNeighbors.DY[d]) {
                case 0 -> flat++;
                case 1 -> { up++; if (diagonal) upDiagonals++; }
                default -> { down++; if (diagonal) downDiagonals++; }
            }
        }
        assertEquals(8, flat);
        assertEquals(4, up);
        assertEquals(8, down);
        assertEquals(0, upDiagonals, "no diagonal climbs: only descents cut corners");
        assertEquals(4, downDiagonals);
        assertEquals(0, pureVertical);
    }

    @Test
    void everyStepHasAWayBack() {
        // A* and the flow fields both assume a path can be walked in reverse
        Set<String> moves = new HashSet<>();
        for (int d = 0; d < GridNeighbors.COUNT; d++)
            moves.add(GridNeighbors.DX[d] + "," + GridNeighbors.DY[d] + "," + GridNeighbors.DZ[d]);
        for (int d = 0; d < GridNeighbors.COUNT; d++) {
            String back = -GridNeighbors.DX[d] + "," + -GridNeighbors.DY[d] + "," + -GridNeighbors.DZ[d];
            boolean diagonalClimb = GridNeighbors.DY[d] == -1 && GridNeighbors.DX[d] != 0 && GridNeighbors.DZ[d] != 0;
            if (diagonalClimb)
                continue;   // a diagonal descent is deliberately one-way: climbing back goes in two steps
            assertTrue(moves.contains(back), "move " + d + " cannot be walked back");
        }
    }

    @Test
    void stepCostsAreOneForCardinalSqrtTwoForDiagonalAndASurchargeForEveryChangeOfHeight() {
        for (int d = 0; d < GridNeighbors.COUNT; d++) {
            boolean diagonal = GridNeighbors.DX[d] != 0 && GridNeighbors.DZ[d] != 0;
            float flat = diagonal ? 1.41421356f : 1f;
            float expected = GridNeighbors.DY[d] == 0 ? flat : flat + 0.2f;
            assertEquals(expected, GridNeighbors.stepCost(d), EPS, "move " + d);
        }
        assertEquals(1f, GridNeighbors.stepCost(0), EPS);                    // east
        assertEquals(GridNeighbors.SQRT2, GridNeighbors.stepCost(4), EPS);   // north-east
        assertEquals(1.2f, GridNeighbors.stepCost(8), EPS);                  // east, one up
        assertEquals(GridNeighbors.SQRT2 + 0.2f, GridNeighbors.stepCost(16), EPS);   // diagonal descent
    }

    @Test
    void climbingIsNeverCheaperThanWalking() {
        for (int d = 0; d < GridNeighbors.COUNT; d++)
            assertTrue(GridNeighbors.stepCost(d) >= 1f, "move " + d);
        assertTrue(GridNeighbors.CLIMB_COST > GridNeighbors.stepCost(8), "a wall climb must cost more than a step up");
    }

    // ---------------------------------------------------------------- wide bodies

    @Test
    void aOneBlockBodyFitsAnywhereItIsAskedAbout() {
        MapView v = new MapView("...", ".0.", "...").radius(0);
        assertTrue(GridNeighbors.wideFits(v, 1, 0, 1));
        assertTrue(GridNeighbors.wideFits(v, 0, 0, 0), "radius 0 does no footprint test at all");
    }

    @Test
    void aTwoWideBodyNeedsItsNodeAndOneClearQuadrant() {
        MapView field = new MapView("00000", "00000", "00000", "00000", "00000").radius(1);
        assertTrue(GridNeighbors.wideFits(field, 2, 0, 2));

        // a 1-wide corridor: no quadrant is clear
        MapView corridor = new MapView(".....", "00000", ".....").radius(1);
        assertFalse(GridNeighbors.wideFits(corridor, 2, 0, 1), "a 1-wide pinch must refuse a 2x2 body");

        // a 2-wide corridor: the node plus the row beside it is a quadrant
        MapView wide = new MapView(".....", "00000", "00000", ".....").radius(1);
        assertTrue(GridNeighbors.wideFits(wide, 2, 0, 1));
        assertTrue(GridNeighbors.wideFits(wide, 2, 0, 2), "a wall on one side is fine: units may hug it");
    }

    @Test
    void aTwoWideBodyRefusesAnUnstandableNodeAndACornerWithoutItsDiagonal() {
        MapView hole = new MapView("000", "0.0", "000").radius(1);
        assertFalse(GridNeighbors.wideFits(hole, 1, 0, 1), "the node cell itself must be standable");
        // the node and both orthogonal neighbours, but not the diagonal cell: the body would clip the corner
        MapView notch = new MapView("00.", "0..", "...").radius(1);
        assertFalse(GridNeighbors.wideFits(notch, 0, 0, 0));
    }

    @Test
    void aTwoWideBodyMayStraddleASingleStairStep() {
        MapView stair = new MapView("111", "101", "111").radius(1);
        assertTrue(GridNeighbors.wideFits(stair, 1, 0, 1), "ground one block higher still counts as standable");
        MapView cliff = new MapView("222", "202", "222").radius(1);
        assertFalse(GridNeighbors.wideFits(cliff, 1, 0, 1), "a 2-block cliff is not a stair step: no quadrant to stand on");
    }

    @Test
    void aWideBodyDoesNotFitAGapNarrowerThanItsBox() {
        // radius 2 = a body of 2.x - 3 blocks: it needs a 3x3 box of ground that holds the node
        MapView twoWide = new MapView(".......", "0000000", "0000000", ".......").radius(2);
        MapView threeWide = new MapView(".......", "0000000", "0000000", "0000000", ".......").radius(2);
        for (int x = 1; x <= 5; x++) {
            assertFalse(GridNeighbors.wideFits(twoWide, x, 0, 1), "2-wide gap, x=" + x);
            assertFalse(GridNeighbors.wideFits(twoWide, x, 0, 2), "2-wide gap, x=" + x);
            assertTrue(GridNeighbors.wideFits(threeWide, x, 0, 1), "3-wide gap edge, x=" + x);
            assertTrue(GridNeighbors.wideFits(threeWide, x, 0, 2), "3-wide gap middle, x=" + x);
            assertTrue(GridNeighbors.wideFits(threeWide, x, 0, 3), "3-wide gap edge, x=" + x);
        }
    }

    @Test
    void aBodyWiderThanTheWorldNeverFitsAndOneInTheOpenAlwaysDoes() {
        MapView open = new MapView("0000000", "0000000", "0000000", "0000000", "0000000", "0000000", "0000000").radius(3);
        assertTrue(GridNeighbors.wideFits(open, 3, 0, 3));
        MapView tiny = new MapView("00", "00").radius(3);
        assertFalse(GridNeighbors.wideFits(tiny, 0, 0, 0));
    }

    // ---------------------------------------------------------------- footprintBox

    @Test
    void footprintBoxEncodesWhereTheBoxSitsRelativeToTheNode() {
        MapView exact = new MapView("000", "000", "000");
        // the box is the whole 3x3 region; "a" counts columns left of the node, "b" rows above it
        assertEquals(0, GridNeighbors.footprintBox(exact, MobilityClass.HUMANOID, 0, 0, 0, 3, 2, 0f, 0, 0));
        int far = GridNeighbors.footprintBox(exact, MobilityClass.HUMANOID, 2, 0, 2, 3, 2, 0f, 0, 0);
        assertEquals(2, GridNeighbors.boxA(far));
        assertEquals(2, GridNeighbors.boxB(far));
        int topRight = GridNeighbors.footprintBox(exact, MobilityClass.HUMANOID, 2, 0, 0, 3, 2, 0f, 0, 0);
        assertEquals(2, GridNeighbors.boxA(topRight));
        assertEquals(0, GridNeighbors.boxB(topRight));
        assertEquals(10, GridNeighbors.boxA(10 | (11 << 8)));
        assertEquals(11, GridNeighbors.boxB(10 | (11 << 8)));
    }

    @Test
    void footprintBoxSaysNoBoxWhereNoneFits() {
        MobilityClass h = MobilityClass.HUMANOID;
        MapView corridor = new MapView(".....", "00000", ".....");
        assertEquals(GridNeighbors.NO_BOX, GridNeighbors.footprintBox(corridor, h, 2, 0, 1, 3, 2, 0f, 0, 0));
        MapView threeByTwo = new MapView("000", "000", "...");
        assertEquals(GridNeighbors.NO_BOX, GridNeighbors.footprintBox(threeByTwo, h, 1, 0, 0, 3, 2, 0f, 0, 0));
        MapView hole = new MapView("000", "0.0", "000");
        assertEquals(GridNeighbors.NO_BOX, GridNeighbors.footprintBox(hole, h, 1, 0, 1, 3, 2, 0f, 0, 0), "the node's own cell is a hole");
    }

    @Test
    void aBoxOfOneBlockOrLessIsAlwaysTheNodeItself() {
        MapView none = new MapView("...");
        assertEquals(0, GridNeighbors.footprintBox(none, MobilityClass.HUMANOID, 1, 0, 0, 1, 2, 0f, 0, 0));
        assertEquals(0, GridNeighbors.footprintBox(none, MobilityClass.HUMANOID, 1, 0, 0, 0, 2, 0f, 0, 0));
    }

    @Test
    void withRoomToSpareTheBodyWalksTheCellCentre() {
        MapView open = new MapView("00000", "00000", "00000", "00000", "00000");
        assertEquals(GridNeighbors.CENTRED_BOX,
            GridNeighbors.footprintBox(open, MobilityClass.HUMANOID, 2, 0, 2, 3, 2, 0f, 1, 0));
        // a 4x3 patch: the middle columns can still centre a 3-wide body
        MapView patch = new MapView("0000", "0000", "0000");
        assertEquals(GridNeighbors.CENTRED_BOX,
            GridNeighbors.footprintBox(patch, MobilityClass.HUMANOID, 1, 0, 1, 3, 2, 0f, 1, 0));
    }

    @Test
    void atTheEdgeOfTheGroundTheBoxIsPulledOntoTheGroundNotCentred() {
        MapView patch = new MapView("0000", "0000", "0000");
        // node on the west edge: the only 3x3 box that holds it starts at the node, so the body sits east of the node
        int box = GridNeighbors.footprintBox(patch, MobilityClass.HUMANOID, 0, 0, 1, 3, 2, 0f, 1, 0);
        assertFalse(box == GridNeighbors.CENTRED_BOX || box == GridNeighbors.NO_BOX);
        assertEquals(0, GridNeighbors.boxA(box));
        assertEquals(1, GridNeighbors.boxB(box));
    }

    @Test
    void whenTwoBoxesAreEquallyNearTheOneAlignedWithTheTravelWins() {
        // a 3x2 patch and a 2-block body at the top middle: the box can start at the node or one cell west of it
        MapView patch = new MapView("000", "000");
        int east = GridNeighbors.footprintBox(patch, MobilityClass.HUMANOID, 1, 0, 0, 2, 2, 0f, 1, 0);
        int west = GridNeighbors.footprintBox(patch, MobilityClass.HUMANOID, 1, 0, 0, 2, 2, 0f, -1, 0);
        assertEquals(0, GridNeighbors.boxA(east), "travelling east: the body is east of the node (box starts at the node)");
        assertEquals(1, GridNeighbors.boxA(west), "travelling west: the body is west of the node");
    }

    // ---------------------------------------------------------------- standing, diagonals, climbing

    @Test
    void aCellIsStandableOnGroundWithinOneBlockUpOrDown() {
        MapView v = new MapView("0123");
        MobilityClass h = MobilityClass.HUMANOID;
        assertTrue(GridNeighbors.cellStandable(v, h, 0, 0, 0, 2, 0f));    // ground at 0
        assertTrue(GridNeighbors.cellStandable(v, h, 1, 0, 0, 2, 0f));    // ground at 1: a step up
        assertFalse(GridNeighbors.cellStandable(v, h, 2, 0, 0, 2, 0f));   // ground at 2: a wall
        assertFalse(GridNeighbors.cellStandable(v, h, 3, 0, 0, 2, 0f));
        assertFalse(GridNeighbors.cellStandable(v, h, 9, 0, 0, 2, 0f), "off the map is nothing");
    }

    @Test
    void aTallBodyNeedsHeadroomButATwoTallOneDoesNot() {
        MapView lowCeiling = new MapView("00").solid(0, 2, 0);
        assertTrue(GridNeighbors.cellStandable(lowCeiling, MobilityClass.HUMANOID, 0, 0, 0, 2, 0f), "2-tall: feet and head are covered by the floor test");
        assertFalse(GridNeighbors.cellStandable(lowCeiling, MobilityClass.HUMANOID, 0, 0, 0, 3, 0f), "3-tall body hits the block at y+2");
        assertTrue(GridNeighbors.cellStandable(lowCeiling, MobilityClass.HUMANOID, 1, 0, 0, 3, 0f), "the next cell is open");
    }

    @Test
    void waterIsStandableToEveryoneButLavaOnlyToTheFireImmune() {
        MapView v = new MapView("~L");
        assertTrue(GridNeighbors.cellStandable(v, MobilityClass.HUMANOID, 0, 0, 0, 2, 0f));
        assertTrue(GridNeighbors.cellStandable(v, MobilityClass.AQUATIC, 0, 0, 0, 2, 0f));
        assertFalse(GridNeighbors.cellStandable(v, MobilityClass.HUMANOID, 1, 0, 0, 2, 0f));
        assertTrue(GridNeighbors.cellStandable(v, MobilityClass.FIRE_IMMUNE, 1, 0, 0, 2, 0f));
    }

    @Test
    void aDiagonalIsBlockedWhenEitherFlankIsImpassable() {
        MobilityClass h = MobilityClass.HUMANOID;
        // start (0,0) -> (1,1): the flanks are (1,0) and (0,1)
        assertTrue(GridNeighbors.diagonalBlocked(new MapView("0.", "00"), h, 0, 0, 0, 1, 1), "east flank is a hole");
        assertTrue(GridNeighbors.diagonalBlocked(new MapView("00", ".0"), h, 0, 0, 0, 1, 1), "south flank is a hole");
        assertFalse(GridNeighbors.diagonalBlocked(new MapView("00", "00"), h, 0, 0, 0, 1, 1));
        // shallow water is slow (25) but passable: it does not block the corner
        assertFalse(GridNeighbors.diagonalBlocked(new MapView("0~", "00"), h, 0, 0, 0, 1, 1));
        // lava blocks everyone but the fire-immune
        assertTrue(GridNeighbors.diagonalBlocked(new MapView("0L", "00"), h, 0, 0, 0, 1, 1));
        assertFalse(GridNeighbors.diagonalBlocked(new MapView("0L", "00"), MobilityClass.FIRE_IMMUNE, 0, 0, 0, 1, 1));
        // an orthogonal move has no corner to cut
        assertFalse(GridNeighbors.diagonalBlocked(new MapView("..", ".."), h, 0, 0, 0, 1, 0));
        assertFalse(GridNeighbors.diagonalBlocked(new MapView("..", ".."), h, 0, 0, 0, 0, 1));
    }

    @Test
    void aClimberHangsOnlyInAColumnFreeOfSolidsFromFeetToHead() {
        MapView v = new MapView("0").clearance(3).solid(0, 3, 0);
        assertTrue(GridNeighbors.climbColumnClear(v, 0, 0, 0), "a block just above the head is no obstacle");
        assertFalse(GridNeighbors.climbColumnClear(v, 0, 1, 0), "...but one cell up it is inside the body");
        assertFalse(GridNeighbors.climbColumnClear(new MapView("0").clearance(3).solid(0, 0, 0), 0, 0, 0), "feet inside a block");
    }

    @Test
    void aClimberNeedsAWallToPressIntoOnASide() {
        MapView none = new MapView("0").clearance(2);
        assertFalse(GridNeighbors.adjacentToClimbWall(none, 0, 5, 0));
        assertTrue(GridNeighbors.adjacentToClimbWall(new MapView("0").clearance(2).solid(1, 5, 0), 0, 5, 0), "wall east, at feet");
        assertTrue(GridNeighbors.adjacentToClimbWall(new MapView("0").clearance(2).solid(0, 6, -1), 0, 5, 0), "wall north, at head");
        // the cell just above a wall top still counts, so a unit steps onto a lip with a flat move
        assertTrue(GridNeighbors.adjacentToClimbWall(new MapView("0").clearance(2).solid(-1, 4, 0), 0, 5, 0), "wall top one below the feet");
        assertFalse(GridNeighbors.adjacentToClimbWall(new MapView("0").clearance(2).solid(1, 3, 0), 0, 5, 0), "wall two below");
        assertFalse(GridNeighbors.adjacentToClimbWall(new MapView("0").clearance(2).solid(1, 7, 0), 0, 5, 0), "wall above the head");
        assertFalse(GridNeighbors.adjacentToClimbWall(new MapView("0").clearance(2).solid(1, 5, 1), 0, 5, 0), "a diagonal neighbour is not a side");
    }

    // ---------------------------------------------------------------- steering away from walls

    private static final float SIDE = GridNeighbors.SIDE_MALUS;
    private static final float CORNER = GridNeighbors.CORNER_MALUS;

    private static float malus(int clearance, int... solidXYZ) {
        Set<String> s = new HashSet<>();
        for (int i = 0; i < solidXYZ.length; i += 3)
            s.add(solidXYZ[i] + "," + solidXYZ[i + 1] + "," + solidXYZ[i + 2]);
        return GridNeighbors.crowdingMalus((x, y, z) -> s.contains(x + "," + y + "," + z), clearance, 0, 0, 0);
    }

    @Test
    void openGroundCostsNothing() {
        assertEquals(0f, malus(2), 0f);
        assertEquals(0f, malus(4), 0f);
    }

    @Test
    void aWallTouchingTheBodyCostsFullAndOneBlockAwayCostsHalf() {
        assertEquals(SIDE, malus(2, 1, 1, 0), EPS);          // east, touching
        assertEquals(SIDE, malus(2, -1, 1, -1), EPS);        // diagonal, touching
        assertEquals(SIDE / 2, malus(2, 2, 1, 0), EPS);      // one block gap
        assertEquals(SIDE / 2, malus(2, 2, 2, -2), EPS);     // far corner of the distance-2 ring
        assertEquals(0f, malus(2, 3, 1, 0), 0f);             // three blocks away: out of the body's way
    }

    @Test
    void onlyTheNearestWallCountsSoACorridorMiddleBeatsHuggingOneWall() {
        // two walls touching on opposite sides still cost one wall, not two (summing made corridor middles worse)
        assertEquals(SIDE, malus(2, 1, 1, 0, -1, 1, 0), EPS);
        assertEquals(SIDE, malus(2, 1, 1, 0, 2, 1, 0, 0, 1, 2), EPS);
        // a 3-wide corridor: walls at x = -2 and x = +2
        float middle = malus(2, -2, 1, 0, 2, 1, 0);
        assertEquals(SIDE / 2, middle, EPS);
        // one step toward a wall: that wall now touches
        float edge = GridNeighbors.crowdingMalus((x, y, z) -> (x == -2 || x == 2) && y >= 1 && y <= 2, 2, 1, 0, 0);
        assertEquals(SIDE, edge, EPS);
        assertTrue(middle < edge);
    }

    @Test
    void aWallIsAnySolidAboveTheFeetUpToHeadHeight() {
        assertEquals(0f, malus(2, 1, 0, 0), 0f, "a lone block at feet level is a climbable step, not a wall");
        assertEquals(SIDE, malus(2, 1, 2, 0), EPS, "a block at head height counts, even with air below it (overhang)");
        assertEquals(0f, malus(2, 1, 3, 0), 0f, "above the head is no wall for a 2-tall body");
        assertEquals(SIDE, malus(4, 1, 4, 0), EPS, "a taller body is reached by a taller wall");
    }

    @Test
    void aLowCeilingAddsAFlatSurchargeOnTopOfTheWalls() {
        assertEquals(SIDE, malus(2, 0, 2, 0), EPS);
        assertEquals(SIDE, malus(4, 0, 4, 0), EPS);
        assertEquals(0f, malus(2, 0, 1, 0), 0f, "the body's own cells are not a ceiling");
        assertEquals(0f, malus(2, 0, 3, 0), 0f, "a ceiling higher than the head is no cost");
        assertEquals(2 * SIDE, malus(2, 1, 1, 0, 0, 2, 0), EPS, "cramped against a wall AND under a roof costs both");
    }

    @Test
    void anInsideCornerOfRaisedGroundIsPenalisedOncePerCorner() {
        assertEquals(0f, malus(2, 0, 0, -1), 0f, "one raised side alone is a plain step");
        assertEquals(0f, malus(2, -1, 0, 0, 1, 0, 0), 0f, "raised on opposite sides is no corner");
        assertEquals(CORNER, malus(2, 0, 0, -1, -1, 0, 0), EPS);   // north + west
        assertEquals(2 * CORNER, malus(2, 0, 0, -1, -1, 0, 0, 1, 0, 0), EPS);   // + east: north-west and north-east
        assertEquals(4 * CORNER, malus(2, 0, 0, -1, 0, 0, 1, -1, 0, 0, 1, 0, 0), EPS);   // all four sides
    }

    @Test
    void theViewOverloadUsesTheViewsOwnClearance() {
        MapView v = new MapView("0").clearance(4).solid(1, 4, 0);
        assertEquals(SIDE, GridNeighbors.crowdingMalus(v, 0, 0, 0), EPS);
        assertEquals(0f, GridNeighbors.crowdingMalus(new MapView("0").clearance(2).solid(1, 4, 0), 0, 0, 0), 0f);
    }
}
