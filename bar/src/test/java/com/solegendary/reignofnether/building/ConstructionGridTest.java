package com.solegendary.reignofnether.building;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What stands and what is still missing on a rising site, cell by cell (round 9 critic, eyes 41084414751): the ghost
 * caged finished walls and the tools hung over the lawn because everything keyed off ONE build front - the lowest layer
 * under half placed - while the walls above it stood. The grid answers per block: what is missing (the ghost), where a
 * standing block has a missing block on top (the rim: the build front of that column), and which standing faces the
 * tools can strike.
 */
class ConstructionGridTest {

    /**
     * A 3 x 3 site, 3 layers: layer 0 a solid floor; layers 1-2 a ring of wall round an empty (air) middle. Laid: the
     * floor, layer 1 except its +x / +z corner block, nothing of layer 2.
     */
    private static ConstructionGrid site() {
        List<int[]> b = new ArrayList<>();
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) b.add(new int[] { 10 + x, 64, 20 + z });
        for (int y = 1; y < 3; y++)
            for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
                if (x != 1 || z != 1) b.add(new int[] { 10 + x, 64 + y, 20 + z });
        ConstructionGrid g = ConstructionGrid.of(b);
        g.clear();
        for (int[] p : b) {
            boolean placed = p[1] == 64 || (p[1] == 65 && !(p[0] == 12 && p[2] == 22));
            g.mark(p[0], p[1], p[2], placed);
        }
        g.finish();
        return g;
    }

    @Test
    void missingBlocksAreTheGhost() {
        ConstructionGrid g = site();
        assertEquals(8 + 1, g.missingCount(), "layer 2's ring of 8 and layer 1's corner");
        assertTrue(g.isMissing(12, 65, 22));
        assertTrue(g.isMissing(10, 66, 20));
        assertTrue(!g.isMissing(10, 65, 20), "standing");
        assertTrue(!g.isMissing(11, 65, 21), "air in the middle is never ghosted");
        assertTrue(!g.isMissing(0, 0, 0), "outside the site");
    }

    /**
     * The rim: the top edge of a standing block with a missing block on it, on each side open to air or the outside -
     * so it runs along the top of every standing wall still to be built on, not round one layer's bounding box.
     */
    @Test
    void theRimRunsAlongStandingTopsWithMissingBlocksOnThem() {
        ConstructionGrid g = site();
        // layer 1's 7 standing blocks each carry a missing block: 2 open sides each (outside, or the air middle; a side
        // against a standing or a missing block is not open) = 14 edges at y 66; the floor's +x/+z corner carries
        // layer 1's missing corner: its 2 outer sides = 2 edges at y 65
        assertEquals(16, g.rimCount());
        int at66 = 0, at65 = 0;
        for (int i = 0; i < g.rimCount(); i++) {
            if (g.rimY(i) == 66f) at66++;
            if (g.rimY(i) == 65f) at65++;
            float len = Math.abs(g.rimX1(i) - g.rimX0(i)) + Math.abs(g.rimZ1(i) - g.rimZ0(i));
            assertEquals(1f, len, 1e-4, "one block edge, axis-aligned");
        }
        assertEquals(14, at66);
        assertEquals(2, at65);
        assertEquals((14 * 66f + 2 * 65f) / 16, g.meanRimY(), 1e-4);
        // block (10, 65, 20): its -z edge runs (10,20)-(11,20), its -x edge (10,21)-(10,20)
        assertTrue(hasRim(g, 10, 20, 11, 20, 66), "-z top edge of the corner block");
        assertTrue(hasRim(g, 10, 21, 10, 20, 66), "-x top edge of the corner block");
        assertTrue(hasRim(g, 13, 22, 13, 23, 65), "+x top edge of the floor under the missing corner");
    }

    private static boolean hasRim(ConstructionGrid g, float x0, float z0, float x1, float z1, float y) {
        for (int i = 0; i < g.rimCount(); i++)
            if (g.rimX0(i) == x0 && g.rimZ0(i) == z0 && g.rimX1(i) == x1 && g.rimZ1(i) == z1 && g.rimY(i) == y)
                return true;
        return false;
    }

    /**
     * The tools strike only standing faces: a standing block with a missing block on it, the face on a side open to
     * air or the outside. Never a missing block, never a face against another block.
     */
    @Test
    void toolsGetStandingFacesOnly() {
        ConstructionGrid g = site();
        assertTrue(g.faceCount() > 0);
        for (int i = 0; i < g.faceCount(); i++) {
            int x = g.faceX(i), y = g.faceY(i), z = g.faceZ(i), side = g.faceSide(i);
            assertTrue(y == 65 || y == 64, "on a course being built on: " + y);
            assertTrue(!g.isMissing(x, y, z), "a standing block");
            assertTrue(g.isMissing(x, y + 1, z), "with a missing block on it");
            int nx = x + (side == 1 ? 1 : side == 3 ? -1 : 0), nz = z + (side == 2 ? 1 : side == 0 ? -1 : 0);
            assertTrue(!g.isStructure(nx, y, nz), "the face is open: " + nx + "," + nz);
        }
    }

    /**
     * Round 11 critic: run the rim up VERTICAL build fronts too - where a standing block's open face meets a missing
     * block's coplanar face beside it, the vertical edge between them. The site's missing corner (12, 65, 22) has two:
     * beside (11, 65, 22) on the +z face (x = 12, z = 23) and beside (12, 65, 21) on the +x face (x = 13, z = 22).
     */
    @Test
    void verticalFrontsRunWhereAStandingFaceMeetsAMissingOne() {
        ConstructionGrid g = site();
        assertEquals(2, g.frontCount());
        boolean a = false, b = false;
        for (int i = 0; i < g.frontCount(); i++) {
            assertEquals(65, g.frontY(i), "from the block's foot, a block high");
            if (g.frontX(i) == 12f && g.frontZ(i) == 23f && g.frontSide(i) == 2) a = true;
            if (g.frontX(i) == 13f && g.frontZ(i) == 22f && g.frontSide(i) == 1) b = true;
        }
        assertTrue(a, "the +z face's front at x 12");
        assertTrue(b, "the +x face's front at z 22");
    }

    @Test
    void aColumnsTopIsItsHighestStandingBlock() {
        ConstructionGrid g = site();
        assertEquals(66f, g.columnTop(10, 20), 1e-4);
        assertEquals(65f, g.columnTop(12, 22), 1e-4, "only the floor stands there");
        assertEquals(65f, g.columnTop(11, 21), 1e-4, "the air middle: the floor");
        assertTrue(Float.isNaN(g.columnTop(0, 0)), "outside");
    }

    @Test
    void anEmptySiteNeverThrows() {
        ConstructionGrid g = ConstructionGrid.of(List.of());
        g.clear();
        g.finish();
        assertEquals(0, g.missingCount());
        assertEquals(0, g.rimCount());
        assertTrue(Float.isNaN(g.meanRimY()));
    }
}
