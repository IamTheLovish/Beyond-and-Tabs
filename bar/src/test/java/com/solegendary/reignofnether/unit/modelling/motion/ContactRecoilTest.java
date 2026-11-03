package com.solegendary.reignofnether.unit.modelling.motion;

import com.solegendary.reignofnether.unit.modelling.models.gen.HalberdierModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The contact event (design/animation-foundation-audit.md, section 3, item 3b): when a unit's blow lands, its arms and
 * torso jolt back on the same tick its target flinches, then shudder still; and a generated human (the Halberdier)
 * flinches with its chest, not only its head.
 */
class ContactRecoilTest {

    private static final float D = Mth.DEG_TO_RAD;

    private static float[] fresh() {
        float[] s = new float[AnimState.STRIDE];
        Kinematics.step(s, 0, 0f, 0f, 0f, 0f, 20f, 20f, false, true);
        return s;
    }

    private static void still(float[] s) {
        Kinematics.step(s, 0, 0f, 0f, 0f, 0f, 20f, 20f, false, false);
    }

    @Test
    void aLandedBlowJoltsTheAttackerBackThenItShuddersStill() {
        float[] s = fresh();
        Kinematics.onContact(s, 0);
        float peak = 0f, min = 0f;
        int peakTick = -1;
        for (int t = 1; t <= 30; t++) {
            still(s);
            float v = s[AnimState.SP_RECOIL];
            if (v > peak) {
                peak = v;
                peakTick = t;
            }
            min = Math.min(min, v);
        }
        assertTrue(peakTick >= 1 && peakTick <= 3, "a jolt, not a drift: peak at tick " + peakTick);
        assertTrue(peak > 5f && peak < 15f, "visible, not a flail: " + peak + " degrees");
        assertTrue(min < -0.3f, "one shudder back through rest: " + min);
        assertEquals(0f, s[AnimState.SP_RECOIL], 0.2f, "still by a second and a half");
    }

    @Test
    void theAttackerRecoilsOnTheSameTickTheTargetFlinches() {
        float[] attacker = fresh(), target = fresh();
        for (int t = 0; t < 5; t++) {
            still(attacker);
            still(target);
        }
        // AnimStates.onDamage stamps both from one damage event
        Kinematics.onContact(attacker, 0);
        Kinematics.onHit(target, 0, 0f);
        still(attacker);
        still(target);
        assertTrue(attacker[AnimState.SP_RECOIL] > 1f, "the attacker moved this tick");
        assertTrue(Math.abs(target[AnimState.SP_FLINCH_X]) > 1f, "and so did the target");
    }

    @Test
    void theRecoilPullsTheArmsAndTorsoBack() {
        float[] s = fresh();
        Kinematics.onContact(s, 0);
        still(s);
        still(s);
        float[] out = new float[Motion.OUT];
        float[] sliders = {1, 1, 1, 1, 1, 1, 1};
        Motion.pose(s, 0, 5, 100f, 1f, 0f, sliders, true, out);
        assertTrue(out[Motion.ARM_R * Motion.CH + Motion.RX] < -3f * D, "the weapon arm knocked back");
        assertTrue(out[Motion.BODY * Motion.CH + Motion.RX] < -1f * D, "the torso rocks back");
        sliders[Motion.FLINCH] = 0f;
        Motion.pose(s, 0, 5, 100f, 1f, 0f, sliders, true, out);
        assertEquals(0f, out[Motion.ARM_R * Motion.CH + Motion.RX], 1e-6f, "the hit-reaction slider switches it off");
    }

    @Test
    void theHalberdierFlinchesWithItsChest() {
        BoneMap hal = BoneMaps.forModel(new HalberdierModel<Entity>(HalberdierModel.createBodyLayer().bakeRoot()));
        assertNotNull(hal);
        assertNotNull(hal.parts[Motion.BODY], "its chest is the torso the flinch bends");
        assertTrue(hal.flinchOnly && hal.noBreath, "still flinch only: its own clips do the rest");
    }
}
