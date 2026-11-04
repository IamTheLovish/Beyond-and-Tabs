package com.solegendary.reignofnether.orthoview;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The RTS view's face mask (OrthoFaceCulling): every face that faces an orthographic camera is drawn, wherever it is.
 * The worked case is the eyes RTS camera (yaw 225 = looking north-east, 65 degrees down) over the block-sampler pad,
 * whose south edge stands a block above the lawn: Embeddium's position rule dropped that south face for the pad's
 * sections south of the camera (lp11 stills, the dark seam along the pad's south edge).
 */
class OrthoFaceCullingTest {

    private static final int[][] NORMALS = {
        { 1, 0, 0 }, { 0, 1, 0 }, { 0, 0, 1 }, { -1, 0, 0 }, { 0, -1, 0 }, { 0, 0, -1 } };

    // vanilla Camera's look vector for MC yaw / pitch (pitch + = looking down), as OrthoviewClientEvents.viewOffset
    private static double[] look(double yawMc, double pitchMc) {
        double yR = Math.toRadians(yawMc), xR = Math.toRadians(pitchMc);
        return new double[] { -Math.sin(yR) * Math.cos(xR), -Math.sin(xR), Math.cos(yR) * Math.cos(xR) };
    }

    private static boolean has(int mask, int bit) {
        return (mask & (1 << bit)) != 0;
    }

    @Test
    void theEyesRtsCameraDrawsTopSouthAndWestFaces() {
        double[] d = look(225, 65);
        int mask = OrthoFaceCulling.visibleFaces(d[0], d[1], d[2]);
        assertTrue(has(mask, OrthoFaceCulling.POS_Z), "south faces (the pad's raised south edge) face this camera");
        assertTrue(has(mask, OrthoFaceCulling.NEG_X), "west faces (lp10's sampler cubes) face this camera");
        assertTrue(has(mask, OrthoFaceCulling.POS_Y), "tops face a camera looking down");
        assertTrue(has(mask, OrthoFaceCulling.UNASSIGNED), "undirected quads are always drawn");
        // and the half facing away still saves its draws
        assertTrue(!has(mask, OrthoFaceCulling.POS_X) && !has(mask, OrthoFaceCulling.NEG_Z)
            && !has(mask, OrthoFaceCulling.NEG_Y), "east, north and bottom faces face away");
    }

    @Test
    void everyFaceThatFacesTheCameraIsDrawnForAnyDirection() {
        Random r = new Random(7);
        for (int i = 0; i < 20000; i++) {
            double[] d = look(r.nextDouble() * 360 - 180, r.nextDouble() * 180 - 90);
            int mask = OrthoFaceCulling.visibleFaces(d[0], d[1], d[2]);
            for (int f = 0; f < 6; f++) {
                double dot = NORMALS[f][0] * d[0] + NORMALS[f][1] * d[1] + NORMALS[f][2] * d[2];
                if (dot < 0)
                    assertTrue(has(mask, f), "face " + f + " faces the camera (n.d = " + dot + ") but was culled");
                if (dot > OrthoFaceCulling.EDGE_ON)
                    assertTrue(!has(mask, f), "face " + f + " faces away (n.d = " + dot + ") but was drawn");
            }
        }
    }

    @Test
    void edgeOnFacesAreDrawnAndADegenerateDirectionCullsNothing() {
        // straight down: the four sides are edge-on and are kept, so turning the camera can never open a seam
        int down = OrthoFaceCulling.visibleFaces(0, -1, 0);
        for (int f : new int[] { OrthoFaceCulling.POS_X, OrthoFaceCulling.POS_Z, OrthoFaceCulling.NEG_X,
                OrthoFaceCulling.NEG_Z })
            assertTrue(has(down, f));
        assertEquals(OrthoFaceCulling.ALL, OrthoFaceCulling.visibleFaces(0, 0, 0));
        assertEquals(OrthoFaceCulling.ALL, OrthoFaceCulling.visibleFaces(Double.NaN, 0, 0));
    }

    @Test
    void theLengthOfTheLookVectorDoesNotMatter() {
        double[] d = look(225, 65);
        assertEquals(OrthoFaceCulling.visibleFaces(d[0], d[1], d[2]),
            OrthoFaceCulling.visibleFaces(d[0] * 40, d[1] * 40, d[2] * 40));
    }
}
