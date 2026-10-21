package com.solegendary.reignofnether.building;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The mesh angel's keyed wing fold (BuildingParts.meshFold) against the sculpt's own posing: the angles below are
 * tools/sculpt_angel.fold_pose's (the sheets lovish judges), turned into ModelPart radians. If the game's slerp or the
 * key constants drift from the sculpt, the statue in game is not the statue on the sheets - and nothing on screen would
 * say so as plainly as this. Regenerate the rows from the sculpt when its keys change (sculpt_angel.fold_constants).
 */
class StatueMeshFoldTest {
    /** fold degrees, 0 wing / 1 hand, then xRot, yRot, zRot (radians, the left side). */
    private static final float[][] EXPECTED = {
        {0.0f, 0f, 0.121121f, -0.290588f, -0.401717f},
        {0.0f, 1f, -0.000000f, 0.279253f, -0.000000f},
        {1.2f, 0f, 0.133805f, -0.296078f, -0.416447f},
        {1.2f, 1f, 0.000386f, 0.278081f, 0.022093f},
        {14.0f, 0f, 0.275068f, -0.342992f, -0.579101f},
        {14.0f, 1f, 0.002789f, 0.265212f, 0.257262f},
        {28.0f, 0f, 0.438649f, -0.368023f, -0.765480f},
        {28.0f, 1f, 0.001788f, 0.250950f, 0.513519f},
        {42.0f, 0f, 0.604991f, -0.363938f, -0.954452f},
        {42.0f, 1f, -0.002895f, 0.237397f, 0.768899f},
        {56.0f, 0f, 0.766503f, -0.331030f, -1.138891f},
        {56.0f, 1f, -0.010926f, 0.225428f, 1.023570f},
        {63.0f, 0f, 0.342875f, -0.944032f, -0.278828f},
        {63.0f, 1f, 0.069705f, 0.312424f, 1.555756f},
        {70.0f, 0f, -0.786927f, -0.836297f, 1.230856f},
        {70.0f, 1f, 0.187462f, 0.347003f, 2.104660f},
    };

    @Test
    void meshFoldMatchesTheSculpt() {
        float[] out = new float[3];
        for (float[] row : EXPECTED) {
            BuildingParts.meshFold(row[1] > 0.5f, row[0], out);
            for (int i = 0; i < 3; i++)
                assertEquals(row[2 + i], out[i], 2e-4f, "fold " + row[0] + (row[1] > 0.5f ? " hand" : " wing") + " angle " + i);
        }
    }

    @Test
    void theRaisedKeyIsLevelTwo() {
        // L2's settled fold (wingOpenAt(2) = 0.2 of the way open) is where the wings stand raised and balanced
        assertEquals((1f - BuildingParts.wingOpenAt(2)) * BuildingParts.WING_FOLD_MAX,
            BuildingParts.MESH_WING_RAISE_AT * BuildingParts.WING_FOLD_MAX, 1e-3f);
    }
}
