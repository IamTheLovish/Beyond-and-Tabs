package com.solegendary.reignofnether.building;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The placement grid's shapes (lovish chose the "rune lattice" + "patch glow", Oct 21): the dots must be quiet at RTS
 * zoom (fade to nothing at the edge), symmetric round the cursor, and the rune circle must always clear the footprint
 * it is drawn round - a circle that cuts a building's corner reads as "this will not fit".
 */
class PlacementGridTest {

    @Test
    void dotsAreFullStrengthAtTheCursorAndFadeToNothingAtTheEdge() {
        assertEquals(PlacementGrid.DOT_ALPHA, PlacementGrid.dotAlpha(0), 1e-6f);
        assertEquals(0f, PlacementGrid.dotAlpha(PlacementGrid.RADIUS), 0f);
        assertEquals(0f, PlacementGrid.dotAlpha(PlacementGrid.RADIUS + 5), 0f);
    }

    @Test
    void theFadeIsQuadraticAndNeverBrightensWithDistance() {
        // half way out: (1 - 1/2)^2 of the cursor's alpha
        assertEquals(PlacementGrid.DOT_ALPHA * 0.25f, PlacementGrid.dotAlpha(PlacementGrid.RADIUS / 2.0), 1e-6f);
        float last = Float.MAX_VALUE;
        for (double d = 0; d <= PlacementGrid.RADIUS + 2; d += 0.25) {
            float a = PlacementGrid.dotAlpha(d);
            assertTrue(a <= last, "alpha rose at distance " + d);
            assertTrue(a >= 0f && a <= PlacementGrid.DOT_ALPHA, "alpha out of range at distance " + d);
            last = a;
        }
    }

    @Test
    void cellsAreTheLatticePointsStrictlyInsideTheRadius() {
        List<int[]> cells = PlacementGrid.cells();
        // Gauss circle: 317 lattice points with dx^2+dz^2 <= 100, minus the 12 that sit exactly on the circle
        // ((+-10,0), (0,+-10), (+-6,+-8), (+-8,+-6)) = 305
        assertEquals(305, cells.size());
        Set<Long> seen = new HashSet<>();
        for (int[] c : cells) {
            assertTrue(c[0] * c[0] + c[1] * c[1] < PlacementGrid.RADIUS * PlacementGrid.RADIUS);
            assertTrue(seen.add(((long) c[0] << 32) ^ (c[1] & 0xffffffffL)), "duplicate cell " + c[0] + "," + c[1]);
        }
        assertTrue(seen.contains(0L), "the cursor's own cell (0,0) must be drawn");
    }

    @Test
    void everyDrawnCellIsVisibleAndTheGridIsSymmetricRoundTheCursor() {
        Set<String> cells = new HashSet<>();
        for (int[] c : PlacementGrid.cells())
            cells.add(c[0] + "," + c[1]);
        for (int[] c : PlacementGrid.cells()) {
            assertTrue(PlacementGrid.dotAlpha(Math.hypot(c[0], c[1])) > 0f,
                    "a drawn dot at " + c[0] + "," + c[1] + " would be invisible");
            assertTrue(cells.contains(-c[0] + "," + c[1]), "no mirror in x of " + c[0] + "," + c[1]);
            assertTrue(cells.contains(c[0] + "," + -c[1]), "no mirror in z of " + c[0] + "," + c[1]);
            assertTrue(cells.contains(c[1] + "," + c[0]), "no transpose of " + c[0] + "," + c[1]);
        }
    }

    @Test
    void theRuneCircleAlwaysClearsTheFootprintCorners() {
        for (int x = 1; x <= 20; x++)
            for (int z = 1; z <= 20; z++) {
                double halfDiagonal = Math.hypot(x, z) / 2.0;
                assertTrue(PlacementGrid.runeRadius(x, z) > halfDiagonal, x + "x" + z + " footprint pokes out of its circle");
                assertEquals(PlacementGrid.runeRadius(x, z), PlacementGrid.runeRadius(z, x), 1e-12);
            }
        assertEquals(2.9, PlacementGrid.runeRadius(3, 4), 1e-9);   // 5/2 + 0.4
    }

    @Test
    void theRingGapIsAClampedShareOfTheRadius() {
        assertEquals(0.3, PlacementGrid.runeGap(1.0), 1e-9);    // 12% would be 0.12: floor 0.3
        assertEquals(0.36, PlacementGrid.runeGap(3.0), 1e-9);   // 12% of 3
        assertEquals(0.6, PlacementGrid.runeGap(10.0), 1e-9);   // 12% would be 1.2: ceiling 0.6
        assertEquals(0.6, PlacementGrid.runeGap(100.0), 1e-9);
    }

    @Test
    void glyphCountIsABalancedMultipleOfFourThatGrowsWithTheCircle() {
        assertEquals(8, PlacementGrid.glyphCount(1.0));    // small circles still get the minimum
        assertEquals(28, PlacementGrid.glyphCount(10.0));  // round(25) = 25 -> next multiple of 4
        assertEquals(32, PlacementGrid.glyphCount(20.0));  // capped
        assertEquals(32, PlacementGrid.glyphCount(1000.0));
        int last = 0;
        for (double outer = 0.5; outer <= 40; outer += 0.1) {
            int n = PlacementGrid.glyphCount(outer);
            assertEquals(0, n % 4, "not balanced at radius " + outer);
            assertTrue(n >= 8 && n <= 32, "out of range at radius " + outer);
            assertTrue(n >= last, "fewer glyphs on a bigger circle at radius " + outer);
            last = n;
        }
    }
}
