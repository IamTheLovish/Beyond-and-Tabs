package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.buildings.defence.Defences;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Buildings are tanky (lovish, Oct 28): a standard unit must bring one down twice as fast as it did, with the
 * building's health unchanged. The old rates are written out here as literals (an infantry blow is the unit's damage
 * x 0.25 x its building multiplier, an arrow half of that again, a point of damage costs the building 2 health), so a
 * change of the one tunable constant moves the game and these tests keep saying what the design is: twice.
 * <p>
 * Melee is timed on the real goal from the first lost block to the fall, on a far glass pad (the Raid tests' pads and
 * driver), against the arithmetic of the old rate; a building falls at half its blocks and every unit's first blow
 * lands at once, so a fight runs about a third quicker than the arithmetic - the bar of 0.5 of the arithmetic sits
 * between the old rate (about 0.67) and the new one (about 0.33). Arrows are counted instead of timed: a volley of
 * arrows fired down onto a tower must take twice the health off that the old rate would.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BuildingDamageGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    /** The old rates, as design numbers. */
    static final float OLD_MELEE_MULT = 0.25f, OLD_ARROW_MULT = 0.5f, HEALTH_PER_DAMAGE = 2f;

    /**
     * Six Vindicators on a Sunforged windmill (110 health): first lost block to fall within half the time the old
     * rate's arithmetic gives, and not absurdly fast either.
     */
    @GameTest(template = ARENA, timeoutTicks = 1500)
    public static void six_melee_units_raze_a_windmill_in_half_the_old_time(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RaidGameTests.Pads pads = new RaidGameTests.Pads(level, 10, 1);
        RaidGameTests.drive(helper, pads, 900, () -> {
            List<RaidGameTests.Sim> sims = new ArrayList<>();
            RaidGameTests.Sim s = new RaidGameTests.Sim("Sunforged", "windmill", pads.cells.get(0), "gametest_bd_a", "gametest_bd_b");
            RaidGameTests.raiders(level, s, EntityRegistrar.VINDICATOR_UNIT, 6);
            RaidGameTests.siege(level, s, com.solegendary.reignofnether.building.Buildings.WIND_GENERATOR_VILLAGERS, false);
            sims.add(s);
            return sims;
        }, sims -> {
            RaidGameTests.Sim s = sims.get(0);
            if (s.attackers.size() != 6 || s.target == null) {
                helper.fail("setup: " + s.attackers.size() + " attackers, target " + s.target);
                return;
            }
            AttackerUnit au = (AttackerUnit) s.attackers.get(0);
            float hitsPerSecond = 20f / Math.max(1, (int) au.getAttackCooldown());
            float oldHps = au.getUnitAttackDamage() * OLD_MELEE_MULT * au.getBuildingDamageMultiplier() * hitsPerSecond * HEALTH_PER_DAMAGE;
            float oldArithmetic = s.target.getMaxHealth() / (6 * oldHps);
            float pure = s.pure();
            ReignOfNether.LOGGER.info("[BuildingDamage] 6 Vindicators on a windmill ({} HP): {}s first block to fall (old rate's arithmetic {}s, ratio {})",
                s.target.getMaxHealth(), RaidGameTests.fmt(pure), RaidGameTests.fmt(oldArithmetic),
                pure < 0 ? "n/a" : String.format(Locale.ROOT, "%.2f", pure / oldArithmetic));
            if (pure < 0)
                helper.fail("the windmill never fell");
            else if (pure > oldArithmetic * 0.5f)
                helper.fail("6 Vindicators took " + RaidGameTests.fmt(pure) + " s to raze a windmill; at the old rate the arithmetic is "
                    + RaidGameTests.fmt(oldArithmetic) + " s (about 0.67 of it in play) and the bar is half of that: " + RaidGameTests.fmt(oldArithmetic * 0.5f) + " s");
            else if (pure < oldArithmetic * 0.15f)
                helper.fail("6 Vindicators razed a windmill in " + RaidGameTests.fmt(pure) + " s, under 0.15 of the old arithmetic " + RaidGameTests.fmt(oldArithmetic) + " s: far too fast");
            else
                helper.succeed();
        });
    }

    /**
     * Sixty of a Skeleton's arrows fired straight down onto a Gravebound-owned raid tower from a Sunforged-owned
     * Skeleton (the arrow chips an enemy building it lands on - AbstractArrowMixin): the health they take off must be
     * twice what the old rate gives - not once (red on main), not four times (the multiplier applied twice).
     */
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void arrows_take_twice_the_old_health_off_a_building(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RaidGameTests.Pads pads = new RaidGameTests.Pads(level, 11, 1);
        GameTestChunks.whenEntitiesReady(helper, level, pads.chunks, 5, 400, 1300, "building damage pad", () -> {
            RaidGameTests.Cell c = pads.cells.get(0);
            Building tower = Defences.sets().get(0).raidTower();
            BlockPos origin = new BlockPos(c.x0() + 19, RaidGameTests.PAD_Y + 1, c.z0() + 5);
            BuildingPlacement bp = RaidGameTests.build(level, tower, origin, "gametest_bd_foe", false);
            Mob archer = RaidGameTests.spawn(level, EntityRegistrar.SKELETON_UNIT.get(), "gametest_bd_archer", c.x0() + 4, c.z0() + 6);
            if (bp == null || archer == null) {
                pads.release();
                helper.fail("could not make the tower or the Skeleton");
                return;
            }
            AttackerUnit au = (AttackerUnit) archer;
            int arrows = 60, perTick = 6;
            float before = bp.getHealth();
            float expectedOld = arrows * au.getUnitAttackDamage() * OLD_MELEE_MULT * au.getBuildingDamageMultiplier() * OLD_ARROW_MULT * HEALTH_PER_DAMAGE;
            double x = bp.centrePos.getX() + 0.5, z = bp.centrePos.getZ() + 0.5, y = bp.maxCorner.getY() + 12;
            int[] fired = { 0, -1 };   // arrows fired; the tick the last one left
            List<Arrow> shot = new ArrayList<>();
            boolean[] over = { false };
            helper.onEachTick(() -> {
                if (over[0])
                    return;
                if (fired[0] < arrows) {
                    for (int i = 0; i < perTick && fired[0] < arrows; i++, fired[0]++) {
                        Arrow a = new Arrow(level, archer);
                        a.setPos(x, y, z);
                        a.shoot(0, -1, 0, 1.5f, 0);
                        level.addFreshEntity(a);
                        shot.add(a);
                    }
                    fired[1] = (int) helper.getTick();
                    return;
                }
                if (helper.getTick() < fired[1] + 40)   // the last arrow falls 12 blocks at 1.5 a tick: 40 ticks is plenty
                    return;
                over[0] = true;
                float lost = before - bp.getHealth();
                ReignOfNether.LOGGER.info("[BuildingDamage] {} arrows on a raid tower ({} HP): {} health lost, the old rate would take {} ({}x)",
                    arrows, bp.getMaxHealth(), String.format(Locale.ROOT, "%.1f", lost), String.format(Locale.ROOT, "%.1f", expectedOld),
                    String.format(Locale.ROOT, "%.2f", lost / expectedOld));
                shot.forEach(a -> a.discard());
                archer.discard();
                BuildingServerEvents.getBuildings().remove(bp);
                pads.release();
                if (lost < expectedOld * 1.6f)
                    helper.fail(arrows + " arrows took " + String.format(Locale.ROOT, "%.1f", lost) + " health off a tower; the old rate takes "
                        + String.format(Locale.ROOT, "%.1f", expectedOld) + " and twice that is wanted");
                else if (lost > expectedOld * 2.5f)
                    helper.fail(arrows + " arrows took " + String.format(Locale.ROOT, "%.1f", lost) + " health off a tower; twice the old "
                        + String.format(Locale.ROOT, "%.1f", expectedOld) + " is wanted, this is more than that (applied twice?)");
                else
                    helper.succeed();
            });
        });
    }
}
