package com.solegendary.reignofnether.building;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Placing a building over fogged / never-sent ground (tester, Oct 26): the cursor fallback and the client's deferral. */
class FogPlacementTest {

    /** The RTS camera looks down at 45 degrees along +z. */
    private static final double DY = -Math.sqrt(0.5), DZ = Math.sqrt(0.5);

    @Test
    void ac1_a_missed_ray_lands_on_the_plane_of_the_last_hit() {
        // camera 200 blocks up the ray from (10, 64, 30): the plane y=64 is crossed exactly there
        double t = 200;
        double[] p = FogPlacement.rayToPlaneY(10, 64 - DY * t, 30 - DZ * t, 0, DY, DZ, 64);
        assertArrayEquals(new double[] { 10, 64, 30 }, p, 1e-9);
    }

    @Test
    void ac1_the_fallback_moves_with_the_cursor() {
        double[] a = FogPlacement.rayToPlaneY(0, 200, 0, 0, DY, DZ, 70);
        double[] b = FogPlacement.rayToPlaneY(5, 200, 3, 0, DY, DZ, 70);
        assertArrayEquals(new double[] { a[0] + 5, 70, a[2] + 3 }, b, 1e-9);
    }

    @Test
    void ac1_no_plane_ahead_gives_null_not_a_far_point() {
        assertNull(FogPlacement.rayToPlaneY(0, 50, 0, 0, DY, DZ, 64));   // plane behind the ray's start
        assertNull(FogPlacement.rayToPlaneY(0, 80, 0, 1, 0, 0, 64));     // ray parallel to it
    }

    @Test
    void ac2_only_missing_ground_in_fog_is_left_to_the_server() {
        assertTrue(FogPlacement.clientDefers(FogPlacement.NOT_LOADED, true));
        assertFalse(FogPlacement.clientDefers("building.reignofnether.ground_not_flat", true));
        assertFalse(FogPlacement.clientDefers("building.reignofnether.too_close", true));
        assertFalse(FogPlacement.clientDefers(null, true));
    }

    @Test
    void ac3_visible_ground_never_defers() {
        assertFalse(FogPlacement.clientDefers(FogPlacement.NOT_LOADED, false));
        assertFalse(FogPlacement.clientDefers("building.reignofnether.ground_not_flat", false));
    }
}
