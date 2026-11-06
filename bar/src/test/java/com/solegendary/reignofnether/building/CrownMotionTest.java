package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.environment.wind.WindField;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Living crowns (lovish, Nov 4: "ardent [Verdant] buildings can have trees growing out of them with animations"; he
 * picked "Living crowns" with a "Gentle" sway: ~0.15 block on a small crown, ~0.3 on Heartwood Hall's). The rules
 * the renderer moves the Verdant leaf crowns by, pinned on the committed structures: the crowns are found where the
 * trees are, they lean gently with the map wind and never leave their trunk, a laid leaf grows in, the crown swells
 * once on completion, and the frame budget takes the nearest buildings first.
 */
class CrownMotionTest {

    private static final File DIR = new File("src/main/resources/data/reignofnether/structures");

    // ---------------------------------------------------------------- the crowns are where the trees are

    @Test
    void heartwoodHallsCrownIsOneGreatCrownOnItsTrunk() throws IOException {
        Leaves s = read("heartwood_hall");
        List<CrownMotion.Crown> crowns = CrownMotion.crowns(s.xs, s.ys, s.zs, new int[s.xs.length]);
        CrownMotion.Crown big = crowns.get(0);
        assertTrue(big.cells() >= 400, "the great cherry crown is one crown: " + big);
        assertEquals(18, big.baseY(), "it sits on the trunk at y 18 (its blossom drips; the crown proper from y 21), lifted high over the roof (the tall tree, Nov 5; his references, Nov 5 evening; framed in the RTS view, Nov 6): " + big);
        assertTrue(big.radius() >= 7f && big.radius() <= 8.5f, "a ~15-block crown: " + big);
        // its pivot is over the trunk: wood within 1.5 blocks of it, in the crown's base layer or the two below
        assertTrue(s.woodNear(big.cx(), big.cz(), big.baseY() - 2, big.baseY(), 1.5f) > 0, "no trunk under " + big);
    }

    @Test
    void everyVerdantTreeBuildingHasACrownOverItsTrunk() throws IOException {
        // the buildings that carry a real tree (a trunk under a crown of 20+ cells); the Circle of Elders' ring of
        // leaves has no trunk at its middle and is left out of this one
        for (String name : new String[] { "heartwood_hall", "grove", "grove_l2", "grove_l3", "heart_of_the_wild", "storm_oak" }) {
            Leaves s = read(name);
            CrownMotion.Crown big = CrownMotion.crowns(s.xs, s.ys, s.zs, new int[s.xs.length]).get(0);
            assertTrue(big.cells() >= 20, name + ": " + big);
            assertTrue(s.woodNear(big.cx(), big.cz(), big.baseY() - 2, big.baseY(), 1.5f) > 0, name + ": no trunk under " + big);
        }
    }

    @Test
    void cellsFallIntoCrownsByTouchingBiggestFirst() {
        // two 2-cell clumps (one touching only diagonally) and a lone cell 5 blocks away
        int[] xs = { 0, 1, 2, 9 }, ys = { 0, 1, 1, 0 }, zs = { 0, 1, 0, 0 };
        int[] of = new int[4];
        List<CrownMotion.Crown> crowns = CrownMotion.crowns(xs, ys, zs, of);
        assertEquals(2, crowns.size(), "diagonal neighbours share a crown: " + crowns);
        assertEquals(3, crowns.get(0).cells());
        assertEquals(0, of[0]);
        assertEquals(0, of[2]);
        assertEquals(1, of[3]);
        assertEquals(1, crowns.get(1).cells());
        assertEquals(2, crowns.get(0).height(), "from the bottom face of y 0 to the top face of y 1");
    }

    // ---------------------------------------------------------------- "Gentle": the sway he picked

    @Test
    void theSwayIsGentleOnEveryVerdantCrown() throws IOException {
        float hall = topPush(read("heartwood_hall"));
        float grove = topPush(read("grove"));
        // average wind, full gust, at the top: ~0.3 on Heartwood Hall's crown, ~0.15-0.2 on the Grove's
        assertTrue(hall >= 0.27f && hall <= 0.34f, "Heartwood Hall's top moves " + hall);
        assertTrue(grove >= 0.12f && grove <= 0.22f, "the Grove's top moves " + grove);
        File[] files = DIR.listFiles((d, n) -> n.endsWith(".nbt") && (n.contains("verdant") || n.startsWith("grove")
            || n.startsWith("heartwood") || n.startsWith("heart_of_the_wild") || n.startsWith("circle_of_elders")
            || n.startsWith("storm_oak")));
        assertTrue(files != null && files.length >= 15, "Verdant structures found: " + (files == null ? 0 : files.length));
        int withLeaves = 0;
        for (File f : files) {
            Leaves s = read(f);
            if (s.xs.length == 0)
                continue;
            withLeaves++;
            for (CrownMotion.Crown c : CrownMotion.crowns(s.xs, s.ys, s.zs, new int[s.xs.length])) {
                assertTrue(c.sway() >= CrownMotion.MIN_SWAY && c.sway() <= CrownMotion.MAX_SWAY, f.getName() + ": " + c);
                // the strongest match wind, gust and flutter at their peaks: never more than ~0.6 block
                assertTrue(CrownMotion.maxPush(c.sway()) <= 0.6f, f.getName() + ": " + c);
            }
        }
        assertTrue(withLeaves >= 15, "Verdant structures with leaves: " + withLeaves);
    }

    @Test
    void theCrownLeansWithTheWindAndStaysOnItsTrunk() {
        float sway = 0.2f;
        for (double gust = -0.4; gust <= 1.0; gust += 0.1) {
            assertEquals(0f, CrownMotion.push(sway, 1.7, gust, CrownMotion.heightWeight(12f, 12, 6), 1), 1e-6,
                "the crown's base stays on its trunk at gust " + gust);
            assertEquals(0f, CrownMotion.sag(sway, 1.7, gust, CrownMotion.heightWeight(12f, 12, 6)), 1e-6);
        }
        // the top takes the whole lean, a point halfway up a quarter of it (a bough bends most toward its tip)
        assertEquals(1f, CrownMotion.heightWeight(18f, 12, 6), 1e-6);
        assertEquals(0.25f, CrownMotion.heightWeight(15f, 12, 6), 1e-6);
        float prev = -1;
        for (float y = 12; y <= 18; y += 0.5f) {
            float w = CrownMotion.heightWeight(y, 12, 6);
            assertTrue(w > prev, "the lean grows up the crown");
            prev = w;
        }
        // downwind on the gust, pushed further in a stronger wind, still at no wind, and it sinks a little when pushed
        assertTrue(CrownMotion.push(sway, 1, 1, 1, 0) > 0);
        assertTrue(CrownMotion.push(sway, 1.5, 1, 1, 0) > CrownMotion.push(sway, 1, 1, 1, 0));
        assertEquals(0f, CrownMotion.push(sway, 0, 1, 1, 1), 1e-6);
        assertTrue(CrownMotion.sag(sway, 1, 1, 1) < 0);
        // the match wind is clamped where the map trees' is
        assertEquals(CrownMotion.push(sway, WindField.MAX_WIND_MUL, 1, 1, 1), CrownMotion.push(sway, 5, 1, 1, 1), 1e-6);
    }

    @Test
    void aCornerTwoCellsShareMovesAsOne() {
        // the flutter is a smooth function of the vertex's own position: the corner (3, 14, 5) of the cell below and of
        // the cell beside gets one phase, so neighbouring leaves never part
        double a = CrownMotion.flutterPhase(3, 14, 5);
        assertEquals(a, CrownMotion.flutterPhase(3.0, 14.0, 5.0), 0);
        double step = Math.abs(CrownMotion.flutterPhase(3.001, 14, 5) - a);
        assertTrue(step < 0.01, "smooth in x: " + step);
    }

    // ---------------------------------------------------------------- growing in, swelling on completion

    @Test
    void aLaidLeafGrowsOutAndSettles() {
        assertEquals(0f, CrownMotion.growScale(0), 1e-6, "laid: nothing yet");
        assertEquals(1f, CrownMotion.growScale(CrownMotion.GROW_TICKS), 1e-6);
        assertEquals(1f, CrownMotion.growScale(CrownMotion.GROW_TICKS * 5), 1e-6, "grown stays grown");
        float peak = 0, prev = -1;
        boolean rising = true;
        for (float t = 0; t <= CrownMotion.GROW_TICKS; t += 0.25f) {
            float g = CrownMotion.growScale(t);
            assertTrue(g >= 0, "never inside out");
            if (g < prev)
                rising = false;
            else
                assertTrue(rising, "it rises once, then only settles");
            peak = Math.max(peak, g);
            prev = g;
        }
        assertTrue(peak > 1f && peak <= 1.06f, "a little past full size, then settles: " + peak);
        assertTrue(CrownMotion.GROW_TICKS >= 8 && CrownMotion.GROW_TICKS <= 20, "half a second or so: visible, not slow");
    }

    @Test
    void theCrownSwellsOnceOnCompletionAndSettles() {
        assertEquals(1f, CrownMotion.swellScale(0), 1e-6);
        assertEquals(1f + CrownMotion.SWELL, CrownMotion.swellScale(CrownMotion.SWELL_TICKS / 2f), 1e-5);
        assertEquals(1f, CrownMotion.swellScale(CrownMotion.SWELL_TICKS), 1e-6);
        assertEquals(1f, CrownMotion.swellScale(1e6f), 1e-6, "settled long after");
        assertTrue(CrownMotion.SWELL > 0.03f && CrownMotion.SWELL <= 0.08f, "seen, not a balloon");
    }

    // ---------------------------------------------------------------- hanging lanterns (the tree-tower reference)

    @Test
    void heartwoodHallHangsLanternsUnderItsCrownsOpenUnderside() throws IOException {
        // lovish's Verdant reference (Nov 4): lanterns hang on chains from the branches, swaying and glowing
        Leaves s = read("heartwood_hall");
        int[] of = new int[s.xs.length];
        List<CrownMotion.Crown> crowns = CrownMotion.crowns(s.xs, s.ys, s.zs, of);
        List<int[]> hung = CrownMotion.lanterns(s.xs, s.ys, s.zs, of, crowns, s.solid);
        CrownMotion.Crown big = crowns.get(0);
        long onBig = hung.stream().filter(h -> h[3] == 0).count();
        assertEquals(CrownMotion.lanternCount(big.radius()), onBig, "one per sector round the great crown");
        assertTrue(onBig >= 3 && onBig <= 6, "a few lanterns, not a fairground: " + onBig);
        java.util.Set<Long> leaves = new java.util.HashSet<>();
        for (int i = 0; i < s.xs.length; i++)
            leaves.add(CrownMotion.cellKey(s.xs[i], s.ys[i], s.zs[i]));
        for (int[] h : hung) {
            assertTrue(leaves.contains(CrownMotion.cellKey(h[0], h[1], h[2])), "hangs from a leaf cell");
            for (int k = 1; k <= CrownMotion.LANTERN_CLEAR; k++)
                assertFalse(s.solid.contains(CrownMotion.cellKey(h[0], h[1] - k, h[2])), "room below " + h[0] + "," + h[1] + "," + h[2]);
            CrownMotion.Crown c = crowns.get(h[3]);
            float dx = h[0] + 0.5f - c.cx(), dz = h[2] + 0.5f - c.cz();
            assertTrue(Math.sqrt(dx * dx + dz * dz) >= 0.5f * c.radius(), "in the crown's outer half, where it is seen");
        }
        assertEquals(hung.size(), hung.stream().map(h -> CrownMotion.cellKey(h[0], h[1], h[2])).distinct().count(),
            "never two on one cell");
    }

    @Test
    void aSmallCrownCarriesNoLanternAndTheSwingIsBounded() {
        assertEquals(0, CrownMotion.lanternCount(2.5f));
        assertEquals(6, CrownMotion.lanternCount(40f));
        float peak = 0;
        for (double t = 0; t < 30; t += 0.05) {
            float a = CrownMotion.lanternSwing(1, 5, t, 0.3);
            peak = Math.max(peak, Math.abs(a));
            assertTrue(Math.abs(a) <= CrownMotion.LANTERN_MAX_DEG + 1e-4, "at most the max swing: " + a);
        }
        assertTrue(peak > 0.8f * CrownMotion.LANTERN_MAX_DEG, "the strongest wind swings them nearly fully: " + peak);
        assertEquals(0f, CrownMotion.lanternSwing(1, 0, 3, 0), 1e-6, "still with no wind");
        // two lanterns with different phases are not in lockstep
        assertTrue(Math.abs(CrownMotion.lanternSwing(0.3, 1, 2, 0) - CrownMotion.lanternSwing(0.3, 1, 2, 2)) > 0.5f);
    }

    @Test
    void aLanternSwingsDownwindFromItsHangPoint() {
        // one lantern hung at (0, 10, 0) from a crown of sway 0.2 (lean share 0, at the crown's base); one vertex 2 below
        float[] hang = { 0, 10, 0 };
        LanternMesh m = new LanternMesh(hang, new int[] { 0 }, new float[] { 0 }, new float[] { 0 },
            new float[] { 0, 8, 0 }, new float[2], new int[1], new int[1], new int[1], new int[] { 0 },
            new int[] { 0 }, new int[] { 1 });
        float[] out = new float[3];
        m.pose(0, 0, 0, 0.55, 1.7, 0, new float[] { 1f }, new float[] { 0.2f }, out);   // wind toward +x, a full gust
        assertTrue(out[0] > 0.1f, "swung downwind: " + out[0]);
        assertEquals(0f, out[2], 1e-6, "only in the wind's plane");
        float len = (float) Math.sqrt(out[0] * out[0] + (out[1] - 10) * (out[1] - 10));
        assertEquals(2f, len, 1e-4, "the chain keeps its length");
        m.pose(0, 0, 0, 0.55, 0, 0, new float[] { 1f }, new float[] { 0.2f }, out);
        assertEquals(0f, out[0], 1e-6, "still with no wind");
        assertEquals(8f, out[1], 1e-6);
    }

    // ---------------------------------------------------------------- the pilot and the frame budget

    @Test
    void thePilotIsHeartwoodHallAndItsLevels() {
        assertTrue(CrownMotion.animates("heartwood_hall", true));
        assertTrue(CrownMotion.animates("heartwood_hall_l2", true));
        assertFalse(CrownMotion.animates("heartwood_hall", false), "only Verdant buildings");
        assertFalse(CrownMotion.animates(null, true));
    }

    @Test
    void theBudgetTakesTheNearestFirst() {
        int[] verts = { 9000, 3000, 3000, 40000, 100 };
        assertEquals(3, CrownMotion.takeCount(verts, 5, 16000), "nearest first while within the budget");
        assertEquals(1, CrownMotion.takeCount(new int[] { 90000, 10 }, 2, 16000), "the nearest is always drawn");
        assertEquals(0, CrownMotion.takeCount(verts, 0, 16000));
        assertEquals(5, CrownMotion.takeCount(verts, 5, 1_000_000));
    }

    // ---------------------------------------------------------------- the frame: CrownMesh moves what it captured

    @Test
    void theMeshMovesTheTopDownwindAndKeepsTheBase() {
        // one crown of two stacked cells, base y 10; one vertex at its base corner and one at its top corner
        int[] cx = { 0, 0 }, cy = { 10, 11 }, cz = { 0, 0 }, of = new int[2];
        List<CrownMotion.Crown> crowns = CrownMotion.crowns(cx, cy, cz, of);
        float[] born = { Float.NaN, Float.NaN };
        float[] pos = { 0, 0, 0, 0, 2, 0 };           // relative to the anchor (0, 10, 0)
        CrownMesh mesh = new CrownMesh(new int[] { 0, 10, 0 }, cx, cy, cz, of, crowns, born, pos, new float[4],
            new int[2], new int[2], new int[2], new int[] { 0, 1 }, new int[] { 0 }, new int[] { 2 });
        float[] out = new float[6];
        double heading = 0;                            // the wind toward +x
        mesh.pose(0, 0, 0, 0, 1, heading, new float[] { 1f }, 100, 1e6f, out);
        assertEquals(0f, out[0], 1e-6, "the base stays");
        assertEquals(0f, out[1], 1e-6);
        assertTrue(out[3] > 0.02f, "the top leans downwind: " + out[3]);
        assertTrue(Math.abs(out[5]) < 1e-6, "and only downwind");
        // a cell laid this tick is at its middle, not yet grown
        born[1] = 100;
        mesh.pose(0, 0, 0, 0, 1, heading, new float[] { 1f }, 100, 1e6f, out);
        assertEquals(0.5f, out[3], 1e-6);
        assertEquals(1.5f, out[4], 1e-6);
        assertTrue(mesh.settle(100 + CrownMotion.GROW_TICKS - 1), "still growing");
        assertFalse(mesh.settle(100 + CrownMotion.GROW_TICKS), "grown");
    }

    // ---------------------------------------------------------------- helpers

    /** The top of a structure's biggest crown at a full gust of the average wind, no flutter. */
    private static float topPush(Leaves s) {
        CrownMotion.Crown big = CrownMotion.crowns(s.xs, s.ys, s.zs, new int[s.xs.length]).get(0);
        return CrownMotion.push(big.sway(), 1, 1, 1, 0);
    }

    /** A structure's leaf cells, its wood (logs, wood) to find the trunk by, and every non-air cell (CrownMotion.cellKey). */
    private record Leaves(int[] xs, int[] ys, int[] zs, Map<Long, Boolean> wood, java.util.Set<Long> solid) {
        int woodNear(float x, float z, int y0, int y1, float r) {
            int n = 0;
            for (int y = y0; y <= y1; y++)
                for (int dx = -3; dx <= 3; dx++)
                    for (int dz = -3; dz <= 3; dz++) {
                        int bx = (int) Math.floor(x) + dx, bz = (int) Math.floor(z) + dz;
                        float ex = bx + 0.5f - x, ez = bz + 0.5f - z;
                        if (ex * ex + ez * ez <= r * r && wood.containsKey(key(bx, y, bz)))
                            n++;
                    }
            return n;
        }
    }

    private static long key(int x, int y, int z) {
        return ((long) x << 40) ^ ((long) y << 20) ^ z;
    }

    private static Leaves read(String name) throws IOException {
        return read(new File(DIR, name + ".nbt"));
    }

    private static Leaves read(File file) throws IOException {
        CompoundTag root = NbtIo.readCompressed(file);
        ListTag palette = root.getList("palette", Tag.TAG_COMPOUND);
        ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);
        List<int[]> leaves = new ArrayList<>();
        Map<Long, Boolean> wood = new HashMap<>();
        java.util.Set<Long> solid = new java.util.HashSet<>();
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag b = blocks.getCompound(i);
            ListTag pos = b.getList("pos", Tag.TAG_INT);
            int x = pos.getInt(0), y = pos.getInt(1), z = pos.getInt(2);
            String name = palette.getCompound(b.getInt("state")).getString("Name");
            if (!name.endsWith(":air") && !name.endsWith("structure_void"))
                solid.add(CrownMotion.cellKey(x, y, z));
            if (name.endsWith("_leaves"))
                leaves.add(new int[] { x, y, z });
            else if (name.endsWith("_log") || name.endsWith("_wood"))
                wood.put(key(x, y, z), true);
        }
        int[] xs = new int[leaves.size()], ys = new int[leaves.size()], zs = new int[leaves.size()];
        for (int i = 0; i < leaves.size(); i++) {
            xs[i] = leaves.get(i)[0];
            ys[i] = leaves.get(i)[1];
            zs[i] = leaves.get(i)[2];
        }
        return new Leaves(xs, ys, zs, wood, solid);
    }
}
