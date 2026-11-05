package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.unit.units.monsters.SkeletonUnit;
import com.solegendary.reignofnether.unit.units.monsters.SpiderUnit;
import com.solegendary.reignofnether.unit.units.villagers.VillagerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Behaviour locks for the two server scans of plan 005 (the mount search and the neutral-aggro pass): they pass before
 * and after the cheaper versions, which must not change what either one decides.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class PerfScanGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** A skeleton told to find a mount takes the nearest mountable spider of its owner, not a farther one. */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void autofind_mounts_the_nearest_mountable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkeletonUnit rider = EntityRegistrar.SKELETON_UNIT.get().create(level);
        SpiderUnit near = EntityRegistrar.SPIDER_UNIT.get().create(level);
        SpiderUnit far = EntityRegistrar.SPIDER_UNIT.get().create(level);
        if (rider == null || near == null || far == null) {
            helper.fail("could not create the units");
            return;
        }
        place(helper, rider, 3, 8);
        place(helper, near, 7, 8);
        place(helper, far, 14, 8);
        for (var u : new net.minecraft.world.entity.Mob[] { rider, near, far }) {
            ((com.solegendary.reignofnether.unit.interfaces.Unit) u).setOwnerName("gametest_mount_nearest");
            level.addFreshEntity(u);
        }
        rider.getMountGoal().autofind = true;
        helper.succeedWhen(() -> {
            if (far.isVehicle())
                helper.fail("the rider mounted the farther spider");
            if (rider.getVehicle() != near)
                helper.fail("the rider has not mounted the nearest spider yet");
            rider.discard();
            near.discard();
            far.discard();
        });
    }

    /**
     * With neutralAggro on, a wild hostile mob beside a unit picks the closest non-unit it can see as its target. It can
     * never pick a unit: NonUnitServerEvents.onChangeTarget cancels every non-unit mob's change of target to a Unit, so
     * the pass's only visible effect is mob-on-mob. Two husks (they do not burn or drop a target in daylight, unlike
     * spiders) 2 apart hunt each other; the unit stands 6 away so the pass has a unit to start from. On a glass pad so
     * no neighbouring test's units are near.
     */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void neutral_aggro_points_a_hostile_mob_at_a_neighbour(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var rule = level.getGameRules().getRule(GameRuleRegistrar.NEUTRAL_AGGRO);
        boolean before = rule.get();
        rule.set(true, level.getServer());
        // back when the test ends, however it ends: its own reset sits after the assertion in succeedWhen, so a
        // failing run (a timeout) left neutralAggro on for every later test in the world
        TestCleanup.atEnd(helper, () -> rule.set(before, level.getServer()));
        BlockPos pad = ArenaReady.pad(helper, 135);
        VillagerUnit worker = EntityRegistrar.VILLAGER_UNIT.get().create(level);
        Husk a = EntityType.HUSK.create(level);
        Husk b = EntityType.HUSK.create(level);
        if (worker == null || a == null || b == null) {
            helper.fail("could not create the mobs");
            return;
        }
        worker.moveTo(pad.getX() + 8.5, pad.getY(), pad.getZ() + 14.5, 0, 0);
        a.moveTo(pad.getX() + 8.5, pad.getY(), pad.getZ() + 8.5, 0, 0);
        b.moveTo(pad.getX() + 8.5, pad.getY(), pad.getZ() + 10.5, 0, 0);
        worker.setOwnerName("gametest_neutral_aggro");
        a.setPersistenceRequired();
        b.setPersistenceRequired();
        for (var e : new net.minecraft.world.entity.Mob[] { worker, a, b })
            level.addFreshEntity(e);
        TestCleanup.entities(helper, java.util.List.of(worker, a, b));
        helper.succeedWhen(() -> {
            if (a.getTarget() != b && b.getTarget() != a)
                helper.fail("neither husk took the other as a target with neutralAggro on (worker tick "
                    + worker.tickCount + ", a target " + a.getTarget() + ", b target " + b.getTarget() + ")");
            rule.set(before, level.getServer());
            worker.discard();
            a.discard();
            b.discard();
        });
    }

    private static void place(GameTestHelper helper, net.minecraft.world.entity.Mob mob, int x, int z) {
        BlockPos at = helper.absolutePos(new BlockPos(x, 2, z));
        mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
    }
}
