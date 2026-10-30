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

    /**
     * Round 13 critic: the ghost still drew a lattice - a hollow building's missing walls showed their inner faces and
     * edges through the outer ones. The ghost's VOLUME is the missing blocks plus the air they will enclose: air walled
     * in on all four sides in its layer with a missing block above it in its column (a room under a roof to come). Not
     * air under nothing (an open courtyard), not air under a standing roof (a finished room).
     */
    @Test
    void theGhostsVolumeTakesInTheRoomItWillEnclose() {
        List<int[]> b = new ArrayList<>();
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) b.add(new int[] { x, 0, z });          // floor
        for (int y = 1; y < 3; y++)
            for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
                if (x != 1 || z != 1) b.add(new int[] { x, y, z });                                      // walls
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) b.add(new int[] { x, 3, z });          // roof
        ConstructionGrid g = ConstructionGrid.of(b);
        g.clear();
        for (int[] p : b)
            g.mark(p[0], p[1], p[2], p[1] <= 1);   // the floor and the first wall course stand
        g.finish();
        assertTrue(g.isVolume(1, 1, 1), "the room inside the standing course, the roof still to come");
        assertTrue(g.isVolume(1, 2, 1), "the room inside the missing course");
        assertTrue(g.isVolume(0, 2, 0), "a missing wall block");
        assertTrue(!g.isVolume(0, 1, 0), "a standing wall block");
        assertTrue(!g.isVolume(1, 0, 1), "the standing floor");
        assertTrue(!g.isVolume(5, 2, 5), "outside");
        // the roof built: the room is finished, no longer ghost
        g.clear();
        for (int[] p : b)
            g.mark(p[0], p[1], p[2], p[1] != 2 || p[0] != 0);   // only the x = 0 side of the second course missing
        g.finish();
        assertTrue(!g.isVolume(1, 1, 1), "under a standing roof");
        // an open court: walls round it, nothing above
        assertTrue(!site().isVolume(11, 65, 21), "the site's open middle has nothing to come above it");
    }

    @Test
    void aColumnsTopIsItsHighestStandingBlock() {
        ConstructionGrid g = site();
        assertEquals(66f, g.columnTop(10, 20), 1e-4);
        assertEquals(65f, g.columnTop(12, 22), 1e-4, "only the floor stands there");
        assertEquals(65f, g.columnTop(11, 21), 1e-4, "the air middle: the floor");
        assertTrue(Float.isNaN(g.columnTop(0, 0)), "outside");
    }

    /**
     * A wall 6 long (x 0-5, z 0), 3 high, origin (0, 0, 0): the foot course stands, the second course only at x 0, the
     * third nothing - the builders started the next course at one end.
     */
    private static ConstructionGrid wall() {
        List<int[]> b = new ArrayList<>();
        for (int y = 0; y < 3; y++) for (int x = 0; x < 6; x++) b.add(new int[] { x, y, 0 });
        ConstructionGrid g = ConstructionGrid.of(b);
        g.clear();
        for (int[] p : b)
            g.mark(p[0], p[1], p[2], p[1] == 0 || (p[1] == 1 && p[0] == 0));
        g.finish();
        return g;
    }

    /**
     * Round 13 critic: the build front read as a short orange tick - it was drawn on the HIGHEST rim row only, which is
     * the few blocks the builders have started on the next course, not the course the wall is at. The front row is the
     * rim row with the most edges (ties: the higher); the front runs along it and steps up over whatever already stands
     * above it, never down into a lagging hole.
     */
    @Test
    void theFrontRowIsTheCourseMostOfTheWallIsAt() {
        ConstructionGrid g = wall();
        // the foot course x 1-5: -z and +z each, and +x at x 5 = 11 edges at y 1; x 0 of the second course: 3 at y 2
        assertEquals(1f, g.frontRow(), 1e-4, "the dominant course, not the 3-edge start on the next one");
        int front = 0;
        for (int i = 0; i < g.rimCount(); i++)
            if (g.isFront(i)) front++;
        assertEquals(14, front, "the front row and the stand above it: one band along the whole top");
        assertEquals(66f, site().frontRow(), 1e-4, "the ring site: 14 edges at 66 beat the corner's 2 at 65");
        int low = 0;
        ConstructionGrid s = site();
        for (int i = 0; i < s.rimCount(); i++)
            if (!s.isFront(i)) { low++; assertEquals(65f, s.rimY(i), 1e-4, "only the lagging hole is left out"); }
        assertEquals(2, low);
    }

    /**
     * Round 13 critic: make the NEXT ghost row read as next - the missing blocks sitting on the front row, and only
     * those. With nothing standing yet (no rim), the lowest missing course.
     */
    @Test
    void theNextRowIsTheMissingCourseOnTheFront() {
        ConstructionGrid g = wall();
        for (int x = 1; x < 6; x++) assertTrue(g.isNextRow(x, 1, 0), "the second course's missing blocks: " + x);
        assertTrue(!g.isNextRow(0, 1, 0), "standing");
        assertTrue(!g.isNextRow(0, 2, 0) && !g.isNextRow(3, 2, 0), "the third course is not next");
        ConstructionGrid s = site();
        assertTrue(s.isNextRow(10, 66, 20), "the ring's top course");
        assertTrue(!s.isNextRow(12, 65, 22), "the lagging corner is under the front, not next");
        List<int[]> b = new ArrayList<>();
        for (int y = 0; y < 2; y++) for (int x = 0; x < 3; x++) b.add(new int[] { x, 70 + y, 0 });
        ConstructionGrid e = ConstructionGrid.of(b);
        e.clear();
        for (int[] p : b) e.mark(p[0], p[1], p[2], false);
        e.finish();
        assertTrue(Float.isNaN(e.frontRow()));
        assertTrue(e.isNextRow(1, 70, 0), "nothing stands: the lowest missing course is next");
        assertTrue(!e.isNextRow(1, 71, 0));
    }

    /**
     * Round 13 critic: the ghost read as a lattice of translucent cubes. One volume: a missing block's face is drawn only
     * where the block beyond it is not part of the site (air or outside) - never between two missing blocks, never
     * against standing stone.
     */
    @Test
    void theGhostHasOnlyItsExteriorFaces() {
        ConstructionGrid g = wall();
        // dirs: 0 -z, 1 +x, 2 +z, 3 -x, 4 +y, 5 -y
        assertTrue(g.ghostFace(2, 1, 0, 0) && g.ghostFace(2, 1, 0, 2), "the wall's two long faces");
        assertTrue(!g.ghostFace(2, 1, 0, 1) && !g.ghostFace(2, 1, 0, 3), "between two missing blocks");
        assertTrue(!g.ghostFace(2, 1, 0, 4), "under the missing course above");
        assertTrue(!g.ghostFace(2, 1, 0, 5), "on standing stone");
        assertTrue(!g.ghostFace(1, 1, 0, 3), "against the standing block at x 0");
        assertTrue(g.ghostFace(5, 2, 0, 4) && g.ghostFace(5, 2, 0, 1), "the top course's top and end");
        assertTrue(g.ghostFace(0, 2, 0, 3) && !g.ghostFace(0, 2, 0, 5), "over the stand at x 0: its end, not its foot");
        assertTrue(!g.ghostFace(0, 0, 0, 4), "a standing block has no ghost face");
        int faces = 0;
        for (int i = 0; i < g.missingCount(); i++)
            for (int dir = 0; dir < 6; dir++)
                if (g.ghostFace(g.missingX(i), g.missingY(i), g.missingZ(i), dir)) faces++;
        // the 11 missing blocks' hull: -z and +z 11 each, the top 6, the ends: +x 2 (courses 1, 2), -x 1 (course 2)
        assertEquals(11 + 11 + 6 + 2 + 1, faces);
        // a hollow hall (isVolume): the missing walls and the room they enclose are one hull - no wall's inner face
        List<int[]> b = new ArrayList<>();
        for (int y = 0; y < 4; y++)
            for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
                if (y == 0 || y == 3 || x != 1 || z != 1) b.add(new int[] { x, y, z });
        ConstructionGrid h = ConstructionGrid.of(b);
        h.clear();
        for (int[] p : b) h.mark(p[0], p[1], p[2], p[1] <= 1);
        h.finish();
        assertTrue(!h.ghostFace(0, 2, 1, 1), "the wall's face into the room it will enclose");
        assertTrue(h.ghostFace(0, 2, 1, 3), "its outer face");
        assertTrue(!h.ghostFace(1, 2, 1, 4) && !h.ghostFace(1, 2, 1, 3), "the room: under the roof, beside the wall");
        assertTrue(h.ghostFace(1, 3, 1, 4), "the roof's top");
    }

    /**
     * The seams between ghost cells (round 13 critic: inner cube seams visible): a face lies GHOST inset inside its cell
     * (never on a standing block's plane) but spans its whole cell in its own plane, so two neighbouring faces meet edge
     * to edge with no gap. Corners in the order a quad is wound.
     */
    @Test
    void ghostFacesSpanTheirCellAndMeetEdgeToEdge() {
        float[] a = new float[12], b = new float[12];
        ConstructionGrid.ghostQuad(2, 1, 0, 0, 0.01f, a);   // -z face of (2, 1, 0)
        ConstructionGrid.ghostQuad(3, 1, 0, 0, 0.01f, b);   // ... of its +x neighbour
        float ax0 = 9, ax1 = -9, bx0 = 9, bx1 = -9;
        for (int k = 0; k < 4; k++) {
            assertEquals(0.01f, a[k * 3 + 2], 1e-6, "the plane, inset into the cell");
            ax0 = Math.min(ax0, a[k * 3]); ax1 = Math.max(ax1, a[k * 3]);
            bx0 = Math.min(bx0, b[k * 3]); bx1 = Math.max(bx1, b[k * 3]);
            assertTrue(a[k * 3 + 1] == 1f || a[k * 3 + 1] == 2f, "full height in its plane");
        }
        assertEquals(2f, ax0, 1e-6);
        assertEquals(3f, ax1, 1e-6, "no gap to its neighbour");
        assertEquals(3f, bx0, 1e-6);
        ConstructionGrid.ghostQuad(2, 1, 0, 4, 0.01f, a);   // the top
        for (int k = 0; k < 4; k++) assertEquals(1.99f, a[k * 3 + 1], 1e-6);
        ConstructionGrid.ghostQuad(2, 1, 0, 1, 0.01f, a);   // +x
        for (int k = 0; k < 4; k++) assertEquals(2.99f, a[k * 3], 1e-6);
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
