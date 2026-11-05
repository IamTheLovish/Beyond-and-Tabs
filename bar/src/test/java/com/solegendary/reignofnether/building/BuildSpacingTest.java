package com.solegendary.reignofnether.building;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** BAR's buildspacing for Shift-drag formations: the gap math, the clamp and the per-type session memory. */
class BuildSpacingTest {

    @AfterEach
    void forget() {
        BuildSpacing.reset();
    }

    @Test
    void adjustStepsByOneAndClampsToZeroAndEight() {
        assertEquals(BuildSpacing.DEFAULT, BuildSpacing.get("Windmill"));
        assertEquals(0, BuildSpacing.adjust("Windmill", -1));
        assertEquals(1, BuildSpacing.adjust("Windmill", 3));   // one step per wheel notch, whatever the delta
        for (int i = 0; i < 20; i++)
            BuildSpacing.adjust("Windmill", 1);
        assertEquals(BuildSpacing.MAX, BuildSpacing.get("Windmill"));
        assertEquals(8, BuildSpacing.MAX);
    }

    @Test
    void spacingIsRememberedPerTypeAndAnUnchangedTypeIsNotSet() {
        BuildSpacing.adjust("Windmill", 1);
        BuildSpacing.adjust("Windmill", 1);
        assertEquals(2, BuildSpacing.get("Windmill"));
        assertTrue(BuildSpacing.isSet("Windmill"));
        assertFalse(BuildSpacing.isSet("Watchtower"));
        assertEquals(0, BuildSpacing.get("Watchtower"));
        assertFalse(BuildSpacing.isSet(null));
        assertEquals(0, BuildSpacing.adjust(null, 1));   // walls (no type) are never spaced
    }

    @Test
    void lineAlongXLeavesExactlyTheGapBetweenFootprints() {
        int width = 5;
        for (int gap = 0; gap <= BuildSpacing.MAX; gap++) {
            List<int[]> c = BuildSpacing.line(100, 50, 160, 50, width, width, gap, 24);
            int prevMin = 100 - width / 2;
            for (int[] p : c) {
                int min = p[0] - width / 2;
                assertEquals(gap, BuildSpacing.gap(prevMin, prevMin + width - 1, min, min + width - 1), "gap " + gap);
                assertEquals(50, p[1]);
                prevMin = min;
            }
            assertEquals(60 / (width + gap), c.size());
        }
    }

    @Test
    void lineUsesTheMainAxisWidthOfARectangleAndWorksBackwardsAndDiagonally() {
        // 3 wide on x, 7 on z, dragged towards -z: steps of 7 + gap on z
        List<int[]> c = BuildSpacing.line(0, 0, 1, -40, 3, 7, 2, 24);
        assertEquals(4, c.size());
        assertArrayEquals(new int[] { 0, -9 }, c.get(0));
        assertArrayEquals(new int[] { 0, -18 }, c.get(1));
        // a 45-degree drag: the main-axis gap is exact, the other axis follows the drag
        List<int[]> d = BuildSpacing.line(0, 0, 30, 30, 4, 4, 1, 24);
        assertArrayEquals(new int[] { 5, 5 }, d.get(0));
        assertArrayEquals(new int[] { 10, 10 }, d.get(1));
        assertEquals(0, BuildSpacing.line(3, 3, 3, 3, 4, 4, 1, 24).size());
    }

    @Test
    void lineIsCappedAtTheFormationMaximum() {
        assertEquals(24, BuildSpacing.line(0, 0, 1000, 0, 2, 2, 0, 24).size());
    }

    @Test
    void legacyLineIsTodaysStepAndGridAtSpacingZeroIsTodaysStep() {
        // the pre-spacing line: step = larger footprint side, along the drag (3x3 building: dims 2 + 1)
        List<int[]> c = BuildSpacing.legacyLine(0, 0, 10, 0, 3, 24);
        assertEquals(3, c.size());
        assertArrayEquals(new int[] { 3, 0 }, c.get(0));
        assertArrayEquals(new int[] { 9, 0 }, c.get(2));
        assertArrayEquals(new int[] { 3, 5 }, BuildSpacing.gridSteps(3, 5, 0));
        assertArrayEquals(new int[] { 7, 9 }, BuildSpacing.gridSteps(3, 5, 4));
    }

    @Test
    void gapIsMeasuredFootprintToFootprintEitherWay() {
        assertEquals(0, BuildSpacing.gap(0, 4, 5, 9));
        assertEquals(3, BuildSpacing.gap(0, 4, 8, 12));
        assertEquals(3, BuildSpacing.gap(8, 12, 0, 4));
        assertTrue(BuildSpacing.gap(0, 4, 4, 8) < 0);
    }
}
