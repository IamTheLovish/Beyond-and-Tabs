package com.solegendary.reignofnether.building;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a rising building's construction effects sit (claude/faction-construction-oct29.md; lovish approved the White
 * Angel concept Oct 29: "i loveeee"). Builders lay blocks strictly bottom-up (BuildingPlacement only offers the lowest
 * unfinished layer), so the build front follows from the per-layer block counts and the count placed - for ANY
 * building, with nothing hand-placed per building (the buildings are being reworked: lovish, Oct 29).
 */
class ConstructionShapeTest {

    /** A stepped tower: a 9x9 base 3 layers high, a 5x5 middle 3 layers, a 1x1 spire 4 layers; origin y 64. */
    private static ConstructionShape tower() {
        List<int[]> b = new ArrayList<>();
        for (int y = 0; y < 3; y++) for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++) b.add(new int[] { 100 + x, 64 + y, 200 + z });
        for (int y = 3; y < 6; y++) for (int x = 2; x < 7; x++) for (int z = 2; z < 7; z++) b.add(new int[] { 100 + x, 64 + y, 200 + z });
        for (int y = 6; y < 10; y++) b.add(new int[] { 104, 64 + y, 204 });
        return ConstructionShape.of(b);
    }

    @Test
    void theFrontIsTheLowestUnfinishedLayer() {
        ConstructionShape s = tower();
        assertEquals(243 + 75 + 4, s.total());
        assertEquals(64f, s.frontY(0), 1e-4, "nothing laid: the front is the bottom of the first layer");
        assertEquals(64.5f, s.frontY(81 / 2 + 1), 0.02, "half of the first 9x9 layer");
        assertEquals(67f, s.frontY(243), 1e-4, "the base done: the front is the bottom of layer 3");
        assertEquals(74f, s.frontY(s.total()), 1e-4, "all laid: the top of the spire");
    }

    @Test
    void theRingHugsTheLayerItIsBuilding() {
        ConstructionShape s = tower();
        float base = s.ringRadius(10), middle = s.ringRadius(243 + 30), spire = s.ringRadius(s.total() - 1);
        System.out.printf("ring radius: base %.2f, middle %.2f, spire %.2f%n", base, middle, spire);
        // wider than the layer's footprint (half-diagonal of 9x9 ~ 6.4) so it reads round the walls, not inside them
        assertTrue(base > 6.4f && base < 9f, "base " + base);
        assertTrue(middle > 3.5f && middle < base, "middle " + middle);
        assertTrue(spire >= ConstructionShape.MIN_RING && spire < middle, "spire " + spire);
    }

    @Test
    void theCentreIsTheFootprintsMiddle() {
        ConstructionShape s = tower();
        assertEquals(104.5f, s.centreX(), 1e-4);
        assertEquals(204.5f, s.centreZ(), 1e-4);
    }

    /**
     * Slice 2: the unbuilt part is drawn as one bold golden outline and is otherwise absent (the fresh critic scored the
     * first concept 5/10 - "built but foggy" - and the outline-only paintover won). The outline is the boxes of the
     * layers still to come, one box per run of layers sharing a footprint, cut off at the build front.
     */
    @Test
    void theOutlineIsTheUnbuiltPartsStepsCutAtTheFront() {
        ConstructionShape s = tower();
        float[] out = new float[6 * s.maxOutlineBoxes()];
        assertEquals(3, s.maxOutlineBoxes(), "a base, a middle and a spire");

        int n = s.outline(s.frontY(0), out);
        assertEquals(3, n, "nothing laid: the whole building is outline");
        assertBox(out, 0, 100, 109, 200, 209, 64, 67);
        assertBox(out, 1, 102, 107, 202, 207, 67, 70);
        assertBox(out, 2, 104, 105, 204, 205, 70, 74);

        n = s.outline(68.5f, out);
        assertEquals(2, n, "the base is built: only the middle (from the front up) and the spire remain");
        assertBox(out, 0, 102, 107, 202, 207, 68.5f, 70);
        assertBox(out, 1, 104, 105, 204, 205, 70, 74);

        assertEquals(0, s.outline(74f, out), "all laid: no outline");
        assertEquals(0, ConstructionShape.of(List.of()).outline(0, new float[0]));
    }

    /**
     * Code review (Oct 30): merging only identical footprints gave a pyramid or a round tower one box per layer - ~3k
     * vertices a site and a staircase of lines. Layers within a block of the run's box on every side join it (the box
     * grows to cover them), and a building never draws more than MAX_OUTLINE_BOXES boxes.
     */
    @Test
    void aPyramidMergesNearLayersAndATallOddBuildingIsCapped() {
        List<int[]> b = new ArrayList<>();
        for (int y = 0; y < 5; y++) for (int x = y; x < 9 - y; x++) for (int z = y; z < 9 - y; z++) b.add(new int[] { x, y, z });
        ConstructionShape pyramid = ConstructionShape.of(b);
        float[] out = new float[6 * pyramid.maxOutlineBoxes()];
        assertEquals(3, pyramid.outline(0, out), "9,7 | 5,3 | 1 - every other layer starts a box");
        assertBox(out, 0, 0, 9, 0, 9, 0, 2);
        assertBox(out, 1, 2, 7, 2, 7, 2, 4);
        assertBox(out, 2, 4, 5, 4, 5, 4, 5);

        b.clear();
        for (int y = 0; y < 30; y++) {
            int in = (y % 2 == 0) ? 0 : 2;   // alternating 9x9 / 5x5: no two neighbours merge
            for (int x = in; x < 9 - in; x++) for (int z = in; z < 9 - in; z++) b.add(new int[] { x, y, z });
        }
        ConstructionShape tall = ConstructionShape.of(b);
        out = new float[6 * tall.maxOutlineBoxes()];
        int n = tall.outline(0, out);
        assertTrue(n <= ConstructionShape.MAX_OUTLINE_BOXES, "capped: " + n);
        assertEquals(0f, out[4], 1e-4, "the boxes still start at the bottom");
        assertEquals(30f, out[(n - 1) * 6 + 5], 1e-4, "and reach the top");
        for (int i = 1; i < n; i++)
            assertEquals(out[(i - 1) * 6 + 5], out[i * 6 + 4], 1e-4, "no gap between box " + (i - 1) + " and " + i);
        assertEquals(0f, out[0], 1e-4, "a merged box covers the widest layer it holds");
    }

    /**
     * Round 2 critic (eyes 41014293234, 4/10): the cut line was a circle round the corners and read as a glow band
     * round the base, not as where blocks are being set. It is now the layer being built's own footprint rectangle.
     */
    @Test
    void theCutLineIsTheFootprintOfTheLayerBeingBuilt() {
        ConstructionShape s = tower();
        float[] r = new float[4];
        s.layerBox(64.5f, r);
        assertEquals(100f, r[0], 1e-4); assertEquals(109f, r[1], 1e-4);
        assertEquals(200f, r[2], 1e-4); assertEquals(209f, r[3], 1e-4);
        s.layerBox(68.2f, r);
        assertEquals(102f, r[0], 1e-4); assertEquals(107f, r[1], 1e-4);
        s.layerBox(80f, r);   // past the top: the last layer
        assertEquals(104f, r[0], 1e-4); assertEquals(105f, r[1], 1e-4);
        s.layerBox(10f, r);   // below the bottom: the first layer
        assertEquals(100f, r[0], 1e-4);
    }

    /**
     * Round 3 (eyes 41023134378): at "39% laid" the walls stood well above the front the COUNT implied, so the gold
     * outline wrapped built walls. Builders mostly go bottom-up, but stragglers (a jammed pool's water, blocks the
     * placement skips) hold the count back. The front now comes from what is actually standing per layer: the lowest
     * layer that is less than half placed.
     */
    @Test
    void theFrontFollowsTheLayersActuallyStandingNotTheCount() {
        ConstructionShape s = tower();
        int[] placed = new int[s.layers()];
        assertEquals(64f, s.frontFromLayers(placed), 1e-4, "nothing standing");
        placed[0] = 81; placed[1] = 81; placed[2] = 70;            // the base's top layer 70 of 81: done enough
        placed[3] = 10;                                            // the middle's first layer barely begun
        assertEquals(67f + 10f / 25f, s.frontFromLayers(placed), 1e-4, "the middle's first layer, 10 of 25 in");
        placed[0] = 60;                                            // a straggling base layer (60 of 81) does not hold it
        assertEquals(67f + 10f / 25f, s.frontFromLayers(placed), 1e-4);
        for (int l = 0; l < s.layers(); l++) placed[l] = 10_000;
        assertEquals(74f, s.frontFromLayers(placed), 1e-4, "all standing: the top");
        assertEquals(1, s.layerOf(65.2f));
        assertEquals(0, s.layerOf(10f));
        assertEquals(s.layers() - 1, s.layerOf(500f));
    }

    /**
     * Round 4 critic (eyes 41031649391, 6.5/10): the tools orbited on the ring's CIRCLE, which round a long building
     * stands far off its long walls, so blows landed on the lawn (a flash with a trail - "a glowing sphere with a comet
     * tail"). The tools now walk the cut line itself: a point a fraction {@code u} round the layer's footprint
     * rectangle, with the outward normal of that side (the tool's grip leans out from the wall).
     */
    @Test
    void aToolWalksTheCutLineRectangleWithItsOutwardNormal() {
        float[] box = { 0, 6, 0, 2 }, p = new float[4];   // 6 x 2: perimeter 16
        assertPoint(box, 0f, p, 0, 0, 0, -1);
        assertPoint(box, 0.25f, p, 4, 0, 0, -1);           // 4 along the first (-z) side
        assertPoint(box, 7f / 16, p, 6, 1, 1, 0);          // 7 along: halfway up the +x side
        assertPoint(box, 11f / 16, p, 3, 2, 0, 1);         // 11 along: the middle of the +z side, walking back
        assertPoint(box, 15f / 16, p, 0, 1, -1, 0);        // 15 along: the -x side
        assertPoint(box, 1.25f, p, 4, 0, 0, -1);           // wraps
        assertPoint(box, -1f / 16, p, 0, 1, -1, 0);        // negative wraps too
        float[] dot = { 3, 3, 5, 5 };                       // a degenerate (empty) footprint: its point, no NaN
        ConstructionShape.perimeterPoint(dot, 0.3f, p);
        assertEquals(3f, p[0], 1e-4);
        assertEquals(5f, p[1], 1e-4);
        assertTrue(!Float.isNaN(p[2]) && !Float.isNaN(p[3]));
    }

    private static void assertPoint(float[] box, float u, float[] p, float x, float z, float nx, float nz) {
        ConstructionShape.perimeterPoint(box, u, p);
        assertEquals(x, p[0], 1e-4, "x at u " + u);
        assertEquals(z, p[1], 1e-4, "z at u " + u);
        assertEquals(nx, p[2], 1e-4, "nx at u " + u);
        assertEquals(nz, p[3], 1e-4, "nz at u " + u);
    }

    private static void assertBox(float[] o, int i, float x0, float x1, float z0, float z1, float y0, float y1) {
        float[] want = { x0, x1, z0, z1, y0, y1 };
        for (int k = 0; k < 6; k++)
            assertEquals(want[k], o[i * 6 + k], 1e-4, "box " + i + " field " + k);
    }

    @Test
    void anEmptyOrOddBuildingNeverThrows() {
        ConstructionShape e = ConstructionShape.of(List.of());
        assertEquals(0, e.total());
        assertEquals(ConstructionShape.MIN_RING, e.ringRadius(0), 1e-4);
        ConstructionShape s = tower();
        assertEquals(64f, s.frontY(-5), 1e-4);
        assertEquals(74f, s.frontY(10_000), 1e-4);
    }
}
