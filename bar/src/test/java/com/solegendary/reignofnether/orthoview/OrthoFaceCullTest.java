package com.solegendary.reignofnether.orthoview;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * lovish (Nov 3): in the RTS view, tall buildings at the bottom edge of the screen looked sliced and see-through.
 * Embeddium's block-face culling judges faces from the camera point (a perspective rule); the RTS view is
 * orthographic. This builds a 24 x 24 hall whose footprint sits near the bottom edge of the screen, places the camera
 * the way OrthoviewClientEvents + CameraMixin do, and checks every on-screen face that faces the viewer against
 * Embeddium 0.3.31's rule (mirrored from its bytecode below).
 */
class OrthoFaceCullTest {

    // ModelQuadFacing ordinals in Embeddium 0.3.31
    private static final int POS_X = 0, POS_Y = 1, POS_Z = 2, NEG_X = 3, NEG_Y = 4, NEG_Z = 5, UNASSIGNED = 6;
    private static final int[][] NORMALS = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {-1, 0, 0}, {0, -1, 0}, {0, 0, -1}};
    // a face nearer edge-on than this covers under 0.1% of its area on screen; either answer is fine there
    private static final double EDGE_ON = 1e-3;

    /** Embeddium 0.3.31 DefaultChunkRenderer.getVisibleFaces, line for line (javap of the tester pack's jar). */
    private static int embeddiumVisibleFaces(int camX, int camY, int camZ, int chunkX, int chunkY, int chunkZ) {
        int minX = chunkX << 4, maxX = minX + 16;
        int minY = chunkY << 4, maxY = minY + 16;
        int minZ = chunkZ << 4, maxZ = minZ + 16;
        int planes = 1 << UNASSIGNED;
        planes |= (camX > minX - 3 ? 1 : 0) << POS_X;
        planes |= (camY > minY - 3 ? 1 : 0) << POS_Y;
        planes |= (camZ > minZ - 3 ? 1 : 0) << POS_Z;
        planes |= (camX < maxX + 3 ? 1 : 0) << NEG_X;
        planes |= (camY < maxY + 3 ? 1 : 0) << NEG_Y;
        planes |= (camZ < maxZ + 3 ? 1 : 0) << NEG_Z;
        return planes;
    }

    /** The mixin's call: Embeddium's camera ints (CameraTransform truncates) through OrthoFaceCull. */
    private static int facesAsDrawn(double[] cam, int sx, int sy, int sz) {
        return embeddiumVisibleFaces(OrthoFaceCull.camX((int) cam[0]), OrthoFaceCull.camY((int) cam[1]),
                OrthoFaceCull.camZ((int) cam[2]), sx, sy, sz);
    }

    // OrthoviewClientEvents.viewOffset's basis: MC yaw / pitch (pitch + = looking down)
    private static double[][] basis(double yaw, double pitch) {
        double yR = Math.toRadians(yaw), xR = Math.toRadians(pitch);
        double[] f = {-Math.sin(yR) * Math.cos(xR), -Math.sin(xR), Math.cos(yR) * Math.cos(xR)};
        double[] r = {-Math.cos(yR), 0, -Math.sin(yR)};
        double[] u = {-r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1]};
        return new double[][]{f, r, u};
    }

    /** {on-screen faces that face the viewer, how many of them the terrain pass drops}. */
    private static int[] hallAtBottomEdge(double yaw, double pitch, double zoom, int height, boolean fix) {
        double[][] b = basis(yaw, pitch);
        double[] f = b[0], r = b[1], u = b[2];
        int ground = 64;
        double aspect = 16.0 / 9.0;
        // the player stands 30 above the ground (orthoviewPlayerBaseY); CameraMixin puts the camera 4 + 20 back
        double[] eye = {0.5, ground + 30 + 1.62, 0.5};
        double[] cam = {eye[0] - 24 * f[0], eye[1] - 24 * f[1], eye[2] - 24 * f[2]};
        OrthoFaceCull.beginPass(fix, (float) f[0], (float) f[1], (float) f[2]);
        // the hall's centre on the ground drawn at window y = -0.8 (the bottom edge of the screen)
        double down = -0.8 * zoom / 2;
        double ox = u[0] * down, oy = u[1] * down, oz = u[2] * down;
        double t = (ground - cam[1] - oy) / f[1];
        int x0 = (int) Math.floor(cam[0] + ox + f[0] * t) - 12;
        int z0 = (int) Math.floor(cam[2] + oz + f[2] * t) - 12;
        int shown = 0, dropped = 0;
        for (int face = 0; face < 6; face++) {
            int[] n = NORMALS[face];
            if (n[0] * f[0] + n[1] * f[1] + n[2] * f[2] > -EDGE_ON)
                continue; // a back face (or edge-on): never part of the picture
            for (int x = x0; x < x0 + 24; x++) {
                for (int y = ground; y < ground + height; y++) {
                    for (int z = z0; z < z0 + 24; z++) {
                        int nx = x + n[0], ny = y + n[1], nz = z + n[2];
                        if (nx >= x0 && nx < x0 + 24 && ny >= ground && ny < ground + height && nz >= z0 && nz < z0 + 24)
                            continue; // inside the hall
                        double cx = x + 0.5 + n[0] * 0.5 - cam[0], cy = y + 0.5 + n[1] * 0.5 - cam[1];
                        double cz = z + 0.5 + n[2] * 0.5 - cam[2];
                        double sx = (cx * r[0] + cz * r[2]) / (zoom * aspect / 2);
                        double sy = (cx * u[0] + cy * u[1] + cz * u[2]) / (zoom / 2);
                        if (Math.abs(sx) > 1 || Math.abs(sy) > 1)
                            continue; // off screen
                        shown++;
                        if ((facesAsDrawn(cam, x >> 4, y >> 4, z >> 4) >> face & 1) == 0)
                            dropped++;
                    }
                }
            }
        }
        return new int[]{shown, dropped};
    }

    @AfterEach
    void reset() {
        OrthoFaceCull.beginPass(false, 0, 0, 0);
    }

    @Test
    void theCameraPointAloneDropsTheFrontOfATallHallAtTheBottomEdge() {
        // the bug as lovish saw it (Embeddium's rule fed the real camera point): zoomed out, a tall hall at the bottom
        // edge loses most of the walls and roofs that face the viewer
        int[] r = hallAtBottomEdge(37, 65, 60, 35, false);
        assertTrue(r[0] > 1000, "the hall is on screen: " + r[0]);
        assertTrue(r[1] > r[0] / 2, "dropped " + r[1] + " of " + r[0]);
        // ... and close up the same hall is whole, which is why it showed only "at certain angles"
        assertEquals(0, hallAtBottomEdge(37, 65, 30, 35, false)[1]);
    }

    @Test
    void everyFaceTheViewerSeesIsDrawn() {
        long shown = 0;
        for (double yaw : new double[]{0, 37, 90, 135, 200, 300})
            for (double pitch : new double[]{12, 30, 55, 65, 75, 90})
                for (double zoom : new double[]{10, 30, 60, 90, 200})
                    for (int height : new int[]{20, 35, 50}) {
                        int[] r = hallAtBottomEdge(yaw, pitch, zoom, height, true);
                        shown += r[0];
                        assertEquals(0, r[1], "yaw " + yaw + " pitch " + pitch + " zoom " + zoom + " height " + height
                                + ": dropped " + r[1] + " of " + r[0] + " visible faces");
                    }
        assertTrue(shown > 100_000, "the sweep looked at real faces: " + shown);
    }

    @Test
    void backFacesStayCulled() {
        // the fix keeps Embeddium's saving: faces turned away from the view are still skipped, for every section
        // around the camera out to the render distance
        for (double yaw : new double[]{0, 37, 135, 300})
            for (double pitch : new double[]{12, 55, 75}) {
                double[] f = basis(yaw, pitch)[0];
                OrthoFaceCull.beginPass(true, (float) f[0], (float) f[1], (float) f[2]);
                for (int sx = -32; sx <= 32; sx += 4)
                    for (int sy = -4; sy <= 20; sy += 4)
                        for (int sz = -32; sz <= 32; sz += 4) {
                            int mask = embeddiumVisibleFaces(OrthoFaceCull.camX(0), OrthoFaceCull.camY(100),
                                    OrthoFaceCull.camZ(0), sx, sy, sz);
                            for (int face = 0; face < 6; face++) {
                                int[] n = NORMALS[face];
                                double dot = n[0] * f[0] + n[1] * f[1] + n[2] * f[2];
                                if (dot > EDGE_ON)
                                    assertEquals(0, mask >> face & 1, "back face " + face + " kept, yaw " + yaw + " pitch " + pitch);
                                if (dot < -EDGE_ON)
                                    assertEquals(1, mask >> face & 1, "front face " + face + " culled, yaw " + yaw + " pitch " + pitch);
                            }
                            assertEquals(1, mask >> UNASSIGNED & 1);
                        }
            }
    }

    @Test
    void outsideTheRtsViewTheCameraPointIsUntouched() {
        OrthoFaceCull.beginPass(false, 0.3f, -0.9f, 0.3f);
        assertEquals(123, OrthoFaceCull.camX(123));
        assertEquals(-45, OrthoFaceCull.camY(-45));
        assertEquals(30_000_000, OrthoFaceCull.camZ(30_000_000));
        OrthoFaceCull.beginPass(true, 0f, -1f, 0f);
        assertEquals(100 + OrthoFaceCull.BACK, OrthoFaceCull.camY(100));
        assertEquals(7, OrthoFaceCull.camX(7));
    }
}
