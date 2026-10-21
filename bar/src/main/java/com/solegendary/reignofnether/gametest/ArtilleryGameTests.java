package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.units.tide.MortarGalleyUnit;
import com.solegendary.reignofnether.unit.units.villagers.VillagerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * IndirectFire (mortars, bombards, rocket batteries) in UnitBowAttackGoal: lobbed shots need no line of sight, and a
 * target inside the dead zone makes the unit back off to fire. tidewrought_t2_units_attack went red twice on Oct 21:
 * its Mortar Galley walked from 14 blocks to 6-7 (inside its 8-block dead zone) and never fired.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class ArtilleryGameTests {

    static MortarGalleyUnit galley(GameTestHelper helper, String owner, double x, double z) {
        MortarGalleyUnit g = EntityRegistrar.MORTAR_GALLEY_UNIT.get().create(helper.getLevel());
        if (g == null)
            return null;
        BlockPos o = helper.absolutePos(new BlockPos(0, 2, 0));
        g.moveTo(o.getX() + x, o.getY(), o.getZ() + z, -90, 0);
        g.setOwnerName(owner);
        helper.getLevel().addFreshEntity(g);
        // the bow it "holds" so UnitBowAttackGoal runs (given on production spawn, not by create): without it the
        // attack goal never starts, the galley never fires and never backs off (CI on #55: "aggressive false")
        g.setupEquipmentAndUpgradesServer();
        return g;
    }

    static VillagerUnit foe(GameTestHelper helper, String owner, double x, double z) {
        VillagerUnit v = EntityRegistrar.VILLAGER_UNIT.get().create(helper.getLevel());
        if (v == null)
            return null;
        BlockPos o = helper.absolutePos(new BlockPos(0, 2, 0));
        v.moveTo(o.getX() + x, o.getY(), o.getZ() + z, 90, 0);
        v.setOwnerName(owner);
        helper.getLevel().addFreshEntity(v);
        return v;
    }

    /** What the galley is doing, for a failure message (CI shows only the message). */
    static String state(MortarGalleyUnit g) {
        var nav = g.getNavigation();
        var path = nav.getPath();
        return "target " + (g.getTarget() == null ? "none" : g.getTarget().getType().toShortString())
            + ", goal target " + (g.getTargetGoal().getTarget() == null ? "none" : "set")
            + ", nav done " + nav.isDone() + ", path " + (path == null ? "none" : path.getTarget().toShortString()
                + " reached " + path.isDone()) + ", move target " + g.getMoveGoal().getMoveTarget()
            + ", bow " + g.getMainHandItem().is(net.minecraft.world.item.Items.BOW)
            + ", goals [" + g.runningGoalsForTest() + "], using item " + g.isUsingItem()
            + ", wants move " + g.getMoveControl().hasWanted()
            + ", motion " + String.format("%.3f", g.getDeltaMovement().horizontalDistance())
            + ", at " + g.blockPosition().toShortString() + ", path index "
            + (nav.getPath() == null ? -1 : nav.getPath().getNextNodeIndex() + "/" + nav.getPath().getNodeCount())
            + ", hold " + g.getHoldPosition() + ", aggressive " + g.isAggressive()
            + ", on ground " + g.onGround() + ", speed attr " + g.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
    }

    static double flat(net.minecraft.world.entity.Entity a, net.minecraft.world.entity.Entity b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** A wall between the galley and its target: it shells over the wall from where it stands, it doesn't walk in. */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 420)
    public static void artillery_fires_over_a_wall_without_walking_in(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_arty_wall_" + helper.absolutePos(BlockPos.ZERO).asLong();
        for (int z = 4; z <= 12; z++)
            for (int y = 2; y <= 5; y++)
                helper.setBlock(new BlockPos(7, y, z), Blocks.STONE);
        MortarGalleyUnit g = galley(helper, owner, 1.5, 8.5);
        VillagerUnit v = foe(helper, owner + "_foe", 15.5, 8.5);
        if (g == null || v == null) {
            helper.fail("could not create the units");
            return;
        }
        double startX = g.getX(), startZ = g.getZ();
        double[] walked = { 0 };
        float full = v.getMaxHealth();
        helper.runAfterDelay(3, () -> ((AttackerUnit) g).setUnitAttackTarget(v));
        helper.succeedWhen(() -> {
            if (g.getTargetGoal().getTarget() != v && v.isAlive())
                ((AttackerUnit) g).setUnitAttackTarget(v);   // a neighbouring test's unit must not pull it away
            walked[0] = Math.max(walked[0], Math.hypot(g.getX() - startX, g.getZ() - startZ));
            if (walked[0] > 3)
                throw new GameTestAssertException("the galley walked " + Math.round(walked[0])
                    + " blocks toward a target it can shell over the wall (now " + Math.round(flat(g, v)) + " away)");
            if (v.isAlive() && v.getHealth() >= full)
                throw new GameTestAssertException("no shell has landed on the target yet");
            g.discard();
            v.discard();
            for (int z = 4; z <= 12; z++)
                for (int y = 2; y <= 5; y++)
                    helper.setBlock(new BlockPos(7, y, z), Blocks.AIR);
        });
    }

    /** A target inside the dead zone: the galley backs out past MIN_RANGE and then hits it. */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 500)
    public static void artillery_backs_out_of_its_dead_zone(GameTestHelper helper) {
        String owner = "gametest_arty_dead_" + helper.absolutePos(BlockPos.ZERO).asLong();
        MortarGalleyUnit g = galley(helper, owner, 7.5, 8.5);
        VillagerUnit v = foe(helper, owner + "_foe", 11.5, 8.5);
        if (g == null || v == null) {
            helper.fail("could not create the units");
            return;
        }
        float full = v.getMaxHealth();
        helper.runAfterDelay(3, () -> ((AttackerUnit) g).setUnitAttackTarget(v));
        helper.succeedWhen(() -> {
            if (g.getTargetGoal().getTarget() != v && v.isAlive())
                ((AttackerUnit) g).setUnitAttackTarget(v);
            if (v.isAlive() && v.getHealth() >= full)
                throw new GameTestAssertException("never hit the target " + String.format("%.1f", flat(g, v))
                    + " blocks away (dead zone " + MortarGalleyUnit.MIN_RANGE + "); galley: " + state(g));
            g.discard();
            v.discard();
        });
    }
}
