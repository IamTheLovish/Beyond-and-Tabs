package com.solegendary.reignofnether.unit.modelling.models;

import com.solegendary.reignofnether.unit.modelling.models.CrossbowLevyUnitModel.XbowString;
import org.junit.jupiter.api.Test;

import static com.solegendary.reignofnether.unit.modelling.models.CrossbowLevyUnitModel.XB_NUT_Z;
import static com.solegendary.reignofnether.unit.modelling.models.CrossbowLevyUnitModel.XB_RELAX;
import static com.solegendary.reignofnether.unit.modelling.models.CrossbowLevyUnitModel.XB_REST_YAW;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The Crossbow Levy's string (CrossbowLevyUnitModel.XbowString) against the model's own numbers: drawn, it must be
 * exactly the modelled string (else every loaded levy shows a kinked or stretched string), and let go it must run
 * straight across the prod. A failure here means the XB_ constants and tools/gen_levy.py's crossbow() disagree.
 */
class CrossbowLevyStringTest {

    private static final float[] SIDES = {1f, -1f};   // string_l on prod_l, string_r on prod_r

    @Test
    void drawnIsTheModelledString() {
        float[] g = new float[4];
        for (float s : SIDES) {
            XbowString.geom(s, 1f, g);
            assertEquals(0f, g[0], 1e-4f, "the prod limb at its rest");
            assertEquals(0f, g[1], 1e-3f, "no turn on the string's rest yaw");
            assertEquals(1f, g[2], 1e-4f, "the string at its modelled length");
            assertEquals(XB_NUT_Z, g[3], 1e-4f, "drawn to the nut");
        }
    }

    @Test
    void letGoIsStraight() {
        float[] g = new float[4];
        for (float s : SIDES) {
            XbowString.geom(s, 0f, g);
            assertEquals(s * XB_RELAX, g[0], 1e-4f, "the prod limb swept forward");
            assertEquals(0f, g[0] + s * XB_REST_YAW + g[1], 1e-3f, "the half straight across the stock");
        }
    }
}
