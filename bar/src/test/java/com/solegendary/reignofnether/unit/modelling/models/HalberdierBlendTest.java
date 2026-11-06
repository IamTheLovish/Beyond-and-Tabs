package com.solegendary.reignofnether.unit.modelling.models;

import com.solegendary.reignofnether.unit.modelling.models.gen.HalberdierModel;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Halberdier's thrust over its walk (lovish, Nov 5: "more and more fluid so the battles look more articulated
 * rather than just weird moving units and animations smacking each other"). Until this, the thrust replaced the walk:
 * the tick a unit on the move began a thrust its legs jumped from mid-stride to the guard, and jumped back when it ended.
 * HumanBase.actOver layers the thrust on the base pose instead. Posed on the real generated model (as
 * perf/AnimationCostBench builds it), through {@link HalberdierUnitModel#poseKeys} - no entity needed.
 */
class HalberdierBlendTest {

    private static final String[] BONES = {"body", "chest", "head", "halberd", "right_arm", "right_fore", "left_arm",
        "left_fore", "left_thigh", "left_shin", "left_foot", "right_thigh", "right_shin", "right_foot", "tabard_front",
        "tabard_back"};
    private static final int ID = 7;

    private static HalberdierUnitModel model() {
        return new HalberdierUnitModel(HalberdierModel.createBodyLayer().bakeRoot());
    }

    /** Every bone's three angles, degrees, after posing the keys. */
    private static float[] pose(HalberdierUnitModel m, float ls, float amt, float age, float attack) {
        m.restPose();
        m.poseKeys(ls, amt, age, ID, attack);
        float[] out = new float[BONES.length * 3];
        for (int i = 0; i < BONES.length; i++) {
            ModelPart p = m.bone(BONES[i]);
            out[i * 3] = (float) Math.toDegrees(p.xRot);
            out[i * 3 + 1] = (float) Math.toDegrees(p.yRot);
            out[i * 3 + 2] = (float) Math.toDegrees(p.zRot);
        }
        return out;
    }

    private static float worst(float[] a, float[] b, StringBuilder where) {
        float w = 0f;
        for (int i = 0; i < a.length; i++) {
            float d = Math.abs(a[i] - b[i]);
            if (d > w) {
                w = d;
                where.setLength(0);
                where.append(BONES[i / 3]).append(" axis ").append(i % 3);
            }
        }
        return w;
    }

    @Test
    void aThrustOnTheMoveStartsAndEndsWhereTheWalkingBodyIs() {
        HalberdierUnitModel m = model();
        for (float ls = 0f; ls < 12f; ls += 0.7f) {
            float age = 40f + ls;
            StringBuilder where = new StringBuilder();
            float in = worst(pose(m, ls, 1f, age, 0f), pose(m, ls, 1f, age, 0.002f), where);
            assertTrue(in < 1f, "ls " + ls + ": entering the thrust jumps " + in + " degrees (" + where + ")");
            float out = worst(pose(m, ls, 1f, age, 0f), pose(m, ls, 1f, age, 0.998f), where);
            assertTrue(out < 1f, "ls " + ls + ": leaving the thrust jumps " + out + " degrees (" + where + ")");
        }
    }

    @Test
    void theLegsKeepWalkingThroughAThrustOnTheMove() {
        HalberdierUnitModel m = model();
        int thigh = 8 * 3;   // left_thigh x
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (float ls = 0f; ls < 12f; ls += 0.25f) {
            float x = pose(m, ls, 1f, 40f, 0.5f)[thigh];
            lo = Math.min(lo, x);
            hi = Math.max(hi, x);
        }
        float walking = 0f;
        float wlo = Float.MAX_VALUE, whi = -Float.MAX_VALUE;
        for (float ls = 0f; ls < 12f; ls += 0.25f) {
            float x = pose(m, ls, 1f, 40f, 0f)[thigh];
            wlo = Math.min(wlo, x);
            whi = Math.max(whi, x);
        }
        walking = whi - wlo;
        assertTrue(walking > 10f, "the walk itself swings the thigh " + walking + " degrees");
        assertTrue(hi - lo > 0.8f * walking, "mid-thrust the thigh swings " + (hi - lo) + " of the walk's " + walking
            + " degrees");
    }

    @Test
    void aThrustStandingStillStartsAndEndsInTheGuard() {
        HalberdierUnitModel m = model();
        StringBuilder where = new StringBuilder();
        float in = worst(pose(m, 0f, 0f, 40f, 0f), pose(m, 0f, 0f, 40f, 0.002f), where);
        assertTrue(in < 1f, "entering a standing thrust jumps " + in + " degrees (" + where + ")");
        float out = worst(pose(m, 0f, 0f, 40f, 0f), pose(m, 0f, 0f, 40f, 0.998f), where);
        assertTrue(out < 1f, "leaving a standing thrust jumps " + out + " degrees (" + where + ")");
    }
}
