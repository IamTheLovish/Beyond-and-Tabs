package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.modelling.models.gen.HalberdierAnim;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * A body with a keyed attack clip swings for the clip's length (unit/interfaces/KeyedSwing + LivingEntityMixin).
 * Vanilla's swing is 6 ticks: swingTime counts up from the swing and {@code swinging} drops once it reaches the
 * duration, so a unit is still swinging 9 ticks after a swing only when its duration is longer than vanilla's. The
 * Halberdier's thrust is keyed for HalberdierAnim.THRUST_TICKS (18) and was played over vanilla's 6 - every eyes clip
 * showed it as a 3-frame flicker with no wind-up. Red before the fix: the Halberdier had stopped by tick 9.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class KeyedSwingGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    /** Vanilla's swing has ended by here (swinging drops on the 7th tick); the keyed 18-tick swing has not. */
    static final int MID = 9;
    /** Past the keyed clip's last tick: the long swing must end too, or the next thrust would never start. */
    static final int AFTER = HalberdierAnim.THRUST_TICKS + 3;

    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void halberdier_swing_lasts_its_keyed_thrust_clip(GameTestHelper helper) {
        String owner = "gametest_swing_" + helper.absolutePos(BlockPos.ZERO).asLong();
        Mob halberdier = BigUnitGameTests.spawn(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 4.5, 4.5);
        if (halberdier == null) {
            helper.fail("could not create the Halberdier");
            return;
        }
        halberdier.setNoAi(true);   // nothing but this test's swing moves its arm
        halberdier.swing(InteractionHand.MAIN_HAND);
        helper.runAfterDelay(MID, () -> {
            if (!halberdier.swinging)
                helper.fail("the Halberdier's swing had ended " + MID + " ticks in (swingTime " + halberdier.swingTime
                    + "): its thrust is keyed for " + HalberdierAnim.THRUST_TICKS + " ticks, vanilla's 6 rush it");
        });
        helper.runAfterDelay(AFTER, () -> {
            if (halberdier.swinging)
                helper.fail("the Halberdier was still swinging " + AFTER + " ticks in (swingTime " + halberdier.swingTime
                    + "): the keyed swing must end with its clip");
            halberdier.discard();
            helper.succeed();
        });
    }

    /** A VindicatorUnit subclass draws another body with no keyed attack (the Cutlass Raider): vanilla's 6 ticks stay. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void a_body_without_a_keyed_attack_keeps_vanillas_swing(GameTestHelper helper) {
        String owner = "gametest_swing_" + helper.absolutePos(BlockPos.ZERO).asLong();
        Mob raider = BigUnitGameTests.spawn(helper, EntityRegistrar.CUTLASS_RAIDER_UNIT.get(), owner, 4.5, 4.5);
        if (raider == null) {
            helper.fail("could not create the Cutlass Raider");
            return;
        }
        raider.setNoAi(true);
        raider.swing(InteractionHand.MAIN_HAND);
        helper.runAfterDelay(MID, () -> {
            if (raider.swinging)
                helper.fail("the Cutlass Raider was still swinging " + MID + " ticks in (swingTime " + raider.swingTime
                    + "): it has no keyed attack clip and should keep vanilla's 6-tick swing");
            raider.discard();
            helper.succeed();
        });
    }
}
