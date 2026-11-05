package com.solegendary.reignofnether.unit;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** BAC-022 (lane C4): the ring and arc slot math, and straight paths to the slots that never cross. */
class FormationShapesTest {

    private static final BlockPos CENTRE = new BlockPos(100, 64, 200);

    private static void assertApart(List<BlockPos> cells, int count, int spacing) {
        assertEquals(count, cells.size());
        assertEquals(count, new HashSet<>(cells).size(), "two units share a cell");
        for (int i = 0; i < cells.size(); i++)
            for (int j = i + 1; j < cells.size(); j++) {
                BlockPos a = cells.get(i), b = cells.get(j);
                assertTrue(Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getZ() - b.getZ())) >= spacing,
                    a + " and " + b + " closer than " + spacing);
            }
    }

    private static double distance(BlockPos c) {
        return Math.hypot(c.getX() - CENTRE.getX(), c.getZ() - CENTRE.getZ());
    }

    @Test
    void ringPutsEveryUnitOnTheDraggedCircle() {
        List<BlockPos> ring = UnitFormations.ringSlots(12, 1, CENTRE, 8);
        assertApart(ring, 12, 1);
        for (BlockPos c : ring)
            assertEquals(8, distance(c), 1.5, c + " is off the ring");
    }

    @Test
    void ringKeepsTheOrdersHeight() {
        for (BlockPos c : UnitFormations.ringSlots(8, 1, CENTRE, 6))
            assertEquals(64, c.getY());
    }

    @Test
    void ringGrowsPastTheDragWhenTheGroupDoesNotFit() {
        for (int spacing : new int[] { 1, 2, 4 }) {
            List<BlockPos> ring = UnitFormations.ringSlots(60, spacing, CENTRE, 2);
            assertApart(ring, 60, spacing);
            assertTrue(distance(ring.get(0)) > 2, "the ring stayed at the dragged radius");
        }
    }

    @Test
    void arcBulgesTowardItsFacingWithTheMiddleUnitLeading() {
        // facing south (+z): every slot is on the south half, and the middle unit is furthest forward
        List<BlockPos> arc = UnitFormations.arcSlots(11, 1, CENTRE, 10, 0, 1);
        assertApart(arc, 11, 1);
        int far = Integer.MIN_VALUE;
        for (BlockPos c : arc) {
            assertTrue(c.getZ() >= CENTRE.getZ() - 1, c + " is behind the arc's centre");
            assertEquals(10, distance(c), 1.5, c + " is off the arc");
            far = Math.max(far, c.getZ());
        }
        assertEquals(far, arc.get(5).getZ(), 1, "the middle slot should lead");
    }

    @Test
    void arcFollowsTheFacing() {
        for (BlockPos c : UnitFormations.arcSlots(9, 1, CENTRE, 10, 1, 0))
            assertTrue(c.getX() >= CENTRE.getX() - 1, c + " is behind an arc facing east");
    }

    @Test
    void arcGrowsWhenTheGroupDoesNotFit() {
        assertApart(UnitFormations.arcSlots(40, 2, CENTRE, 3, 0, 1), 40, 2);
    }

    @Test
    void noSlotsForNoUnits() {
        assertTrue(UnitFormations.ringSlots(0, 1, CENTRE, 5).isEmpty());
        assertTrue(UnitFormations.arcSlots(0, 1, CENTRE, 5, 0, 1).isEmpty());
    }

    private static boolean crosses(double[] a, double[] b, double[] c, double[] d) {
        double d1 = side(c, d, a), d2 = side(c, d, b), d3 = side(a, b, c), d4 = side(a, b, d);
        return d1 * d2 < 0 && d3 * d4 < 0;
    }

    private static double side(double[] o, double[] p, double[] q) {
        return (p[0] - o[0]) * (q[1] - o[1]) - (p[1] - o[1]) * (q[0] - o[0]);
    }

    @Test
    void assignmentGivesEveryUnitItsOwnSlotAndNoPathsCross() {
        Random random = new Random(20);
        for (int round = 0; round < 40; round++) {
            int n = 2 + random.nextInt(40);
            double[][] units = new double[n][], slots = new double[n][];
            for (int i = 0; i < n; i++) {
                units[i] = new double[] { random.nextDouble() * 30, random.nextDouble() * 30 };
                double angle = i * 2 * Math.PI / n;
                slots[i] = new double[] { 15 + 12 * Math.cos(angle), 15 + 12 * Math.sin(angle) };
            }
            int[] pick = UnitFormations.uncrossed(units, slots);
            assertEquals(n, Arrays.stream(pick).distinct().count(), "two units took one slot");
            for (int i = 0; i < n; i++)
                for (int j = i + 1; j < n; j++)
                    assertFalse(crosses(units[i], slots[pick[i]], units[j], slots[pick[j]]),
                        "round " + round + ": paths of units " + i + " and " + j + " cross");
        }
    }

    @Test
    void assignmentLeavesAGroupThatIsAlreadyInPlace() {
        double[][] same = { { 0, 0 }, { 5, 0 }, { 10, 0 } };
        assertArrayEquals(new int[] { 0, 1, 2 }, UnitFormations.uncrossed(same, same));
    }
}
