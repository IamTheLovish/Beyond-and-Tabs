package com.solegendary.reignofnether.worldmemory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Trampled ground (world memory slice 2, design/battlefield_effects.md): the pure rules. Written before the code; each
 * number is the design's, so a wrong implementation fails here, not in a still.
 */
class TrampleRulesTest {

    // ------------------------------------------------------------------ which ground wears

    @Test
    void softGroundWearsAndHardGroundNever() {
        assertEquals(TrampleRules.Surface.GRASS, TrampleRules.surfaceOf("minecraft:grass_block"));
        assertEquals(TrampleRules.Surface.GRASS, TrampleRules.surfaceOf("minecraft:podzol"));
        assertEquals(TrampleRules.Surface.SOIL, TrampleRules.surfaceOf("minecraft:dirt"));
        assertEquals(TrampleRules.Surface.SOIL, TrampleRules.surfaceOf("minecraft:coarse_dirt"));
        assertEquals(TrampleRules.Surface.SAND, TrampleRules.surfaceOf("minecraft:sand"));
        assertEquals(TrampleRules.Surface.SNOW, TrampleRules.surfaceOf("minecraft:snow"));
        assertEquals(TrampleRules.Surface.SNOW, TrampleRules.surfaceOf("minecraft:snow_block"));
        for (String hard : new String[] { "minecraft:stone", "minecraft:dirt_path", "minecraft:oak_planks", "minecraft:water",
            "minecraft:cobblestone", "minecraft:stone_slab", "mcwroofs:thatch_steep_roof", "", null })
            assertEquals(TrampleRules.Surface.NONE, TrampleRules.surfaceOf(hard), "should never wear: " + hard);
        assertEquals(0f, TrampleRules.Surface.NONE.wear);
        assertTrue(TrampleRules.Surface.SNOW.wear > TrampleRules.Surface.GRASS.wear, "snow shows feet more than grass");
        assertTrue(TrampleRules.Surface.SOIL.wear < TrampleRules.Surface.GRASS.wear, "bare soil changes less than grass");
    }

    // ------------------------------------------------------------------ how much a unit wears

    @Test
    void wearGrowsWithBodyWidthAndIsClamped() {
        assertEquals(TrampleRules.BASE_PEAK, TrampleRules.peakFor(0.6), "a militia is the reference");
        int last = 0;
        for (double w = 0.2; w <= 6.0; w += 0.1) {
            int p = TrampleRules.peakFor(w);
            assertTrue(p >= last, "wear must not fall as the body widens (" + w + ")");
            last = p;
        }
        assertEquals(Math.round(TrampleRules.BASE_PEAK * TrampleRules.MAX_SCALE), TrampleRules.peakFor(5.0), "a titan is capped at 4x");
        assertEquals(Math.round(TrampleRules.BASE_PEAK * TrampleRules.MIN_SCALE), TrampleRules.peakFor(0.2), "a tiny unit still leaves 0.75x");
        assertTrue(TrampleRules.peakFor(1.95) > TrampleRules.peakFor(0.6) * 3, "a ravager's single pass reads like a squad's");
        assertTrue(TrampleRules.radiusFor(1.95) > TrampleRules.radiusFor(0.6), "a wide body wears a wide track");
        assertTrue(TrampleRules.radiusFor(0.6) >= 0.6 && TrampleRules.radiusFor(20) <= 3.0);
    }

    @Test
    void standingAndTeleportsWearNothing() {
        assertTrue(!TrampleRules.walked(0.1, 0.1), "idle sway");
        assertTrue(!TrampleRules.walked(0, 0));
        assertTrue(TrampleRules.walked(1.5, 1.0), "half a second of walking");
        assertTrue(TrampleRules.walked(0, 7.5), "a charge");
        assertTrue(!TrampleRules.walked(20, 0), "a teleport or a respawn leaves no track across the gap");
    }

    // ------------------------------------------------------------------ the stamp

    /** The trample value of world cell (x, z) of a chunk field at the chunk's origin (0, 0). */
    private static int at(GroundMemory m, int x, int z) {
        return m.trample(GroundMemory.index(x, z));
    }

    @Test
    void aPassWearsEachCellOnceWhateverTheSampleSpacing() {
        // one six-block walk in one sample, and the same walk sampled every block: the same track to the byte (the
        // segments tile the path, so the sample points leave no beads of double wear)
        GroundMemory once = new GroundMemory(0), sampled = new GroundMemory(0);
        once.addTrampleCapsule(0, 0, 2.0, 8.5, 8.0, 8.5, 0.75, 12, null, null);
        for (int i = 0; i < 6; i++)
            sampled.addTrampleCapsule(0, 0, 2.0 + i, 8.5, 3.0 + i, 8.5, 0.75, 12, null, null);
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++)
                assertEquals(at(once, x, z), at(sampled, x, z), "cell " + x + "," + z);
        // the track's own cells are worn at the peak, cells beside it fade, cells far off are untouched
        assertEquals(12, at(once, 4, 8));
        assertEquals(12, at(once, 6, 8));
        assertEquals(0, at(once, 4, 11), "three blocks off the track");
        assertEquals(0, at(once, 12, 8), "beyond the walk's end");
    }

    @Test
    void aSquadFlattensTheGrassAndARoadWearsToSoil() {
        GroundMemory m = new GroundMemory(0);
        int peak = TrampleRules.peakFor(0.6);
        for (int pass = 0; pass < 12; pass++)
            m.addTrampleCapsule(0, 0, 1.0, 8.5, 15.0, 8.5, TrampleRules.radiusFor(0.6), peak, null, null);
        int squad = at(m, 8, 8);
        assertTrue(squad >= 110 && squad < TrampleRules.WORN_FROM, "a squad of 12 flattens the grass, not yet soil: " + squad);
        for (int pass = 12; pass < 20; pass++)
            m.addTrampleCapsule(0, 0, 1.0, 8.5, 15.0, 8.5, TrampleRules.radiusFor(0.6), peak, null, null);
        assertTrue(at(m, 8, 8) >= TrampleRules.WORN_FROM, "twenty passes wear it to soil: " + at(m, 8, 8));
        for (int pass = 0; pass < 100; pass++)
            m.addTrampleCapsule(0, 0, 1.0, 8.5, 15.0, 8.5, TrampleRules.radiusFor(0.6), peak, null, null);
        assertEquals(255, at(m, 8, 8), "a road saturates (and stops changing, so it stops costing sync bytes)");
        long[] changed = m.addTrampleCapsule(0, 0, 1.0, 8.5, 15.0, 8.5, TrampleRules.radiusFor(0.6), peak, null, null);
        assertTrue(!GroundMemory.anyBit(changed), "a saturated road reports no changed cells");
    }

    @Test
    void aWalkAcrossAChunkBorderWearsBothSides() {
        GroundMemory west = new GroundMemory(0), east = new GroundMemory(0);
        west.addTrampleCapsule(0, 0, 12.0, 4.5, 20.0, 4.5, 0.75, 12, null, null);
        east.addTrampleCapsule(1, 0, 12.0, 4.5, 20.0, 4.5, 0.75, 12, null, null);
        assertEquals(12, at(west, 15, 4));
        assertEquals(12, east.trample(GroundMemory.index(0, 4)));
        assertEquals(12, east.trample(GroundMemory.index(3, 4)));
        assertEquals(0, east.trample(GroundMemory.index(6, 4)), "past the walk's end");
    }

    @Test
    void theColumnFilterAndPerCellWearAreHonoured() {
        GroundMemory m = new GroundMemory(0);
        long[] allowed = new long[4];
        int[] peaks = new int[GroundMemory.CELLS];
        for (int x = 0; x < 16; x++) {
            int cell = GroundMemory.index(x, 8);
            if (x != 6) {   // column 6 is stone: not allowed
                GroundMemory.setBit(allowed, cell);
                peaks[cell] = x < 8 ? 12 : 17;   // the east half is snow (wear 1.4)
            }
        }
        m.addTrampleCapsule(0, 0, 1.0, 8.5, 15.0, 8.5, 0.75, 12, peaks, allowed);
        assertEquals(12, at(m, 4, 8));
        assertEquals(0, at(m, 6, 8), "stone stays clean");
        assertEquals(17, at(m, 10, 8), "snow takes the surface's wear");
    }

    // ------------------------------------------------------------------ the look

    /** Luma (Rec. 709) of an sRGB triple in 0..1. */
    private static double luma(double[] c) {
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }

    private static double hue(double[] c) {
        double max = Math.max(c[0], Math.max(c[1], c[2])), min = Math.min(c[0], Math.min(c[1], c[2]));
        if (max - min < 1e-9)
            return 0;
        double h;
        if (max == c[0])
            h = 60 * (((c[1] - c[2]) / (max - min)) % 6);
        else if (max == c[1])
            h = 60 * ((c[2] - c[0]) / (max - min) + 2);
        else
            h = 60 * ((c[0] - c[1]) / (max - min) + 4);
        return h < 0 ? h + 360 : h;
    }

    /** The decal over a lawn colour at noon: the vertex colour times the texture's mean, thinned by the texture's alpha. */
    private static double[] over(int argb, double[] lawn) {
        double a = (argb >>> 24) / 255.0 * TrampleRules.TEXTURE_ALPHA_MEAN;
        double[] c = { ((argb >> 16) & 255) / 255.0, ((argb >> 8) & 255) / 255.0, (argb & 255) / 255.0 };
        double[] out = new double[3];
        for (int i = 0; i < 3; i++)
            out[i] = a * c[i] * TrampleRules.TEXTURE_RGB_MEAN + (1 - a) * lawn[i];
        return out;
    }

    // three lawn shades inside the noon band (luma 0.33-0.40, hue 72-82) and grass's map colour as the ground tint
    private static final double[][] LAWNS = { { 0.30, 0.36, 0.17 }, { 0.33, 0.39, 0.20 }, { 0.36, 0.42, 0.22 } };
    private static final int GRASS_MAP = 0x7FB238;

    @Test
    void aWornTrackReadsAsADirtPathOnEveryLawn() {
        for (double[] lawn : LAWNS) {
            double[] worn = over(TrampleRules.lookArgb(255, TrampleRules.Surface.GRASS, GRASS_MAP), lawn);
            double l = luma(worn), h = hue(worn);
            assertTrue(l >= 0.30 && l <= 0.40, "worn track luma " + l + " outside the dirt/path band 0.30-0.40");
            assertTrue(h >= 28 && h <= 40, "worn track hue " + h + " outside the dirt/path band 28-40");
        }
    }

    @Test
    void flattenedGrassIsALighterStrawNotAHole() {
        for (double[] lawn : LAWNS) {
            double[] flat = over(TrampleRules.lookArgb(110, TrampleRules.Surface.GRASS, GRASS_MAP), lawn);
            double lift = luma(flat) - luma(lawn);
            assertTrue(lift >= 0.02 && lift <= 0.06, "flattened grass should lift the lawn by 0.02-0.06, got " + lift);
            double shift = hue(lawn) - hue(flat);
            assertTrue(shift >= 2 && shift <= 12, "flattened grass leans toward straw by 2-12 degrees, got " + shift);
            // at no stage of wear is the track a dark hole or a shadow
            for (int v = 0; v <= 255; v++) {
                double l = luma(over(TrampleRules.lookArgb(v, TrampleRules.Surface.GRASS, GRASS_MAP), lawn));
                assertTrue(l >= luma(lawn) - 0.04, "value " + v + " darkens the lawn to " + l);
            }
        }
    }

    @Test
    void theAlphaStartsAtTheFirstVisibleStepAndNeverFalls() {
        for (TrampleRules.Surface s : TrampleRules.Surface.values()) {
            assertEquals(0, TrampleRules.lookArgb(TrampleRules.FLAT_FROM - 1, s, GRASS_MAP) >>> 24, s + " below the first step");
            int last = 0;
            for (int v = 0; v <= 255; v++) {
                int a = TrampleRules.lookArgb(v, s, GRASS_MAP) >>> 24;
                if (s == TrampleRules.Surface.NONE)
                    assertEquals(0, a, "NONE draws nothing");
                assertTrue(a + 1 >= last, s + ": alpha falls at " + v);
                last = a;
            }
        }
        assertTrue(TrampleRules.lookArgb(255, TrampleRules.Surface.GRASS, GRASS_MAP) >>> 24 >= 150, "a worn road is clearly drawn");
    }

    // ------------------------------------------------------------------ the client's rebuild throttle

    @Test
    void trampleOnlyChangesAreRebuiltOnAThrottle() {
        assertTrue(TrampleRules.rebuildDue(true, 1000, 999), "a scorch or crater change rebuilds at once");
        assertTrue(!TrampleRules.rebuildDue(false, 1000 + TrampleRules.TRAMPLE_REBUILD_MS - 1, 1000), "a march waits for the throttle");
        assertTrue(TrampleRules.rebuildDue(false, 1000 + TrampleRules.TRAMPLE_REBUILD_MS, 1000));
        assertEquals(1500, TrampleRules.TRAMPLE_REBUILD_MS, "the design's 1.5 s");
    }
}
