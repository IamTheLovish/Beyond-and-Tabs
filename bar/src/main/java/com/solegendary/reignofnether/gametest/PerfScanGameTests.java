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
        // gone however the test ends: the discards below only ran on a pass, so a timeout left three units standing
        TestCleanup.entities(helper, java.util.List.of(rider, near, far));
        // This test failed on 6 of the last 40 red CI builds (Nov 1-4, shard 1 and shard 8) with only "has not
        // mounted the nearest spider yet" and no way to tell why. A timeout reports the LAST message, so the message
        // carries what the rider did over the run: a line every 10 ticks, kept only when something changed.
        java.util.List<String> history = new java.util.ArrayList<>();
        String[] last = { "" };
        helper.onEachTick(() -> {
            if (helper.getTick() % 10 != 0)
                return;
            String now = mountState(level, rider, near, far);
            if (!now.equals(last[0])) {
                history.add("t" + helper.getTick() + " " + now);
                last[0] = now;
            }
        });
        helper.succeedWhen(() -> {
            if (far.isVehicle())
                helper.fail("the rider mounted the farther spider; history: " + String.join(" | ", history));
            if (rider.getVehicle() != near)
                helper.fail("the rider has not mounted the nearest spider yet; now " + mountState(level, rider, near, far)
                    + "; history: " + String.join(" | ", history));
            rider.discard();
            near.discard();
            far.discard();
        });
    }

    /**
     * The likely cause of the autofind flake, made deterministic: an enemy unit 10 blocks from the rider - inside a
     * Skeleton's aggro range (12) - while it looks for its mount, as a neighbouring arena's units are (arena spots sit 21
     * blocks apart, so a neighbour's units stand ~10 blocks from this test's rider). Unit.isIdle reads the unit's move
     * goal only, not its MountGoal, so a rider on its way to a mount counts as idle and idle auto-aggression can take the
     * enemy as its target. On its own glass pad (ArenaReady.pad, a free slot) so no real neighbour is near; the enemy has
     * its AI off and does nothing. The rider must still mount the nearest spider.
     */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void an_autofinding_rider_mounts_with_an_enemy_in_its_aggro_range(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pad = ArenaReady.pad(helper, 286);
        SkeletonUnit rider = EntityRegistrar.SKELETON_UNIT.get().create(level);
        SpiderUnit near = EntityRegistrar.SPIDER_UNIT.get().create(level);
        SpiderUnit far = EntityRegistrar.SPIDER_UNIT.get().create(level);
        var foe = EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (rider == null || near == null || far == null || foe == null) {
            helper.fail("could not create the units");
            return;
        }
        rider.moveTo(pad.getX() + 3.5, pad.getY(), pad.getZ() + 2.5, 0, 0);
        near.moveTo(pad.getX() + 7.5, pad.getY(), pad.getZ() + 2.5, 0, 0);
        far.moveTo(pad.getX() + 14.5, pad.getY(), pad.getZ() + 2.5, 0, 0);
        foe.moveTo(pad.getX() + 3.5, pad.getY(), pad.getZ() + 12.5, 0, 0);
        for (var u : new net.minecraft.world.entity.Mob[] { rider, near, far })
            ((com.solegendary.reignofnether.unit.interfaces.Unit) u).setOwnerName("gametest_mount_aggro");
        foe.setOwnerName("gametest_mount_aggro_foe");
        foe.setNoAi(true);
        for (var u : new net.minecraft.world.entity.Mob[] { rider, near, far, foe })
            level.addFreshEntity(u);
        rider.getMountGoal().autofind = true;
        TestCleanup.entities(helper, java.util.List.of(rider, near, far, foe));
        java.util.List<String> history = new java.util.ArrayList<>();
        String[] last = { "" };
        helper.onEachTick(() -> {
            if (helper.getTick() % 10 != 0)
                return;
            String now = mountState(level, rider, near, far);
            if (!now.equals(last[0])) {
                history.add("t" + helper.getTick() + " " + now);
                last[0] = now;
            }
        });
        helper.succeedWhen(() -> {
            if (rider.getVehicle() != near)
                helper.fail("with an enemy 10 blocks away the rider has not mounted the nearest spider; now "
                    + mountState(level, rider, near, far) + "; history: " + String.join(" | ", history));
        });
    }

    /** What the mount test's failure needs to name: where the three are, the goal's state, what holds the rider. */
    static String mountState(ServerLevel level, SkeletonUnit rider, SpiderUnit near, SpiderUnit far) {
        var goal = rider.getMountGoal();
        BlockPos mt = goal.getMoveTarget();
        net.minecraft.world.entity.LivingEntity target = goal.getTarget();
        String targetName = target == null ? "none" : target == near ? "near" : target == far ? "far" : target.getType().toShortString();
        net.minecraft.world.entity.LivingEntity attack = rider.getTarget();
        String attackName = attack == null ? "none" : attack.getType().toShortString()
            + (attack instanceof com.solegendary.reignofnether.unit.interfaces.Unit u ? "(" + u.getOwnerName() + ")" : "");
        boolean webbed = level.getBlockState(rider.blockPosition()).is(net.minecraft.world.level.block.Blocks.COBWEB)
            || level.getBlockState(rider.blockPosition().above()).is(net.minecraft.world.level.block.Blocks.COBWEB);
        return String.format(java.util.Locale.ROOT,
            "rider %s%s at %.1f,%.1f,%.1f vehicle %s | near at %.1f,%.1f,%.1f d %.2f passengers %d alive %s | far passengers %d"
                + " | goal autofind %s target %s moveTarget %s running %s unreachable %s | attack %s | webbed %s",
            rider.isAlive() ? "alive" : "DEAD", rider.isRemoved() ? " REMOVED" : "",
            rider.getX(), rider.getY(), rider.getZ(),
            rider.getVehicle() == null ? "none" : rider.getVehicle() == near ? "near" : rider.getVehicle() == far ? "far" : rider.getVehicle().getType().toShortString(),
            near.getX(), near.getY(), near.getZ(), rider.distanceTo(near), near.getPassengers().size(), near.isAlive(),
            far.getPassengers().size(),
            goal.autofind, targetName, mt == null ? "none" : mt.toShortString(), goal.isGoalRunning(),
            goal.getLastUnreachableTarget() == null ? "none" : goal.getLastUnreachableTarget().toShortString(),
            attackName, webbed);
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
