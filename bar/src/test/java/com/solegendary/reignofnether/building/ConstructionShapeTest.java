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
