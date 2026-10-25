package com.solegendary.reignofnether.perf;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * worldMarkers cost 2.2-3.5 ms a frame at 505 units + 1,715 buildings (eyes scene 18). The handler that scales with
 * the army is BlockClientEvents (range indicators): it asked the fog of war about every building's whole footprint
 * and every unit's block (an allocation and a set lookup each) BEFORE asking whether the thing has a range indicator
 * at all, which almost nothing has. And the metal rings recomputed 64 sines, cosines and square roots per patch per
 * frame for a circle that never changes.
 */
class WorldMarkerCostTest {

    private static String src(String file) throws IOException {
        Path p = Path.of("src/main/java/com/solegendary/reignofnether/" + file);
        if (!Files.exists(p))
            p = Path.of("bar/src/main/java/com/solegendary/reignofnether/" + file);
        assertTrue(Files.exists(p), "source not found: " + file);
        return Files.readString(p);
    }

    @Test
    void ringTableIsBuiltOnceAndMatchesTheCircle() {
        UnitRing a = UnitRing.of(32), b = UnitRing.of(32);
        assertSame(a, b, "one table per segment count, built once");
        assertEquals(33, a.x.length);
        assertEquals(1f, a.x[0], 1e-6f);
        assertEquals(0f, a.z[0], 1e-6f);
        assertEquals(1f, a.x[32], 1e-5f);
        for (int i = 1; i <= 32; i++) {
            double ang = Math.PI * 2 * i / 32;
            assertEquals((float) Math.cos(ang), a.x[i], 1e-6f);
            assertEquals((float) Math.sin(ang), a.z[i], 1e-6f);
            assertEquals(1f, a.nx[i] * a.nx[i] + a.nz[i] * a.nz[i], 1e-4f, "segment direction is a unit vector");
        }
        assertNotNull(UnitRing.of(8));
    }

    @Test
    void rangeIndicatorsAskTheCheapQuestionBeforeTheFogQuestion() throws IOException {
        String s = src("blocks/BlockClientEvents.java");
        int handler = s.indexOf("public static void onRenderLevel");
        int end = s.indexOf("maintain a mapping of night sources");
        String h = s.substring(handler, end);
        assertTrue(h.indexOf("getActiveAddon(RangeIndicatorAddon.class)") < h.indexOf("isBuildingInBrightChunk"),
                "buildings: has a range indicator? before the footprint fog scan");
        assertTrue(h.indexOf("instanceof RangeIndicator") < h.indexOf("isBlockVisible(le.getOnPos())"),
                "units: is it a RangeIndicator? before the fog lookup");
        assertTrue(!h.contains("ResourceLocation.parse"), "the white texture location is a constant, not parsed per frame");
    }

    @Test
    void refreshGateRebuildsOnlyWhenDue() {
        RefreshGate g = new RefreshGate(5);
        assertTrue(g.due(100, 7), "first call builds");
        assertTrue(!g.due(101, 7) && !g.due(104, 7), "same list, inside the interval: reuse");
        assertTrue(g.due(105, 7), "interval passed");
        assertTrue(g.due(106, 8), "list size changed");
        assertTrue(g.due(3, 8), "clock went backwards: a new world");
        assertTrue(!g.due(3, 8));
    }

    @Test
    void rangeIndicatorsAreFoundOnceNotPerFrame() throws IOException {
        String s = src("blocks/BlockClientEvents.java");
        int handler = s.indexOf("public static void onRenderLevel");
        String h = s.substring(handler, s.indexOf("maintain a mapping of night sources"));
        assertTrue(s.contains("RefreshGate"), "the subset of range-indicator buildings and units is cached");
        assertTrue(!h.contains("for (BuildingPlacement building : BuildingClientEvents.getBuildings())")
                && !h.contains("for (LivingEntity le : UnitClientEvents.getAllUnits())"),
                "the frame does not walk every building and unit");
    }

    @Test
    void buffersAreFetchedWhenSomethingIsDrawnNotEveryFrame() throws IOException {
        String s = src("blocks/BlockClientEvents.java");
        String h = s.substring(s.indexOf("public static void onRenderLevel"), s.indexOf("maintain a mapping of night sources"));
        assertTrue(!h.contains("getBuffer("), "BlockClientEvents: a getBuffer with nothing drawn flushes the open batch");
        String m = src("resources/MetalPatchMarkerClientEvents.java");
        assertTrue(m.indexOf("getBuffer(RenderType.lines())") > m.indexOf("for (BlockPos patch : MetalPatchesClient.getPatches())"),
                "metal rings: the lines buffer is fetched at the first ring, inside the loop");
    }

    @Test
    void metalRingsUseTheTable() throws IOException {
        String s = src("resources/MetalPatchMarkerClientEvents.java");
        assertTrue(s.contains("UnitRing"), "rings read the precomputed circle");
        assertTrue(!s.contains("Math.cos(") && !s.contains("Mth.sqrt("), "no trig or sqrt per segment per frame");
    }
}
