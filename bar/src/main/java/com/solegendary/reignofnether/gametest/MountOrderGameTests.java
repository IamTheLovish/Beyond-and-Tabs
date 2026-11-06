package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitActionItem;
import com.solegendary.reignofnether.unit.units.monsters.SkeletonUnit;
import com.solegendary.reignofnether.unit.units.monsters.SpiderUnit;
import com.solegendary.reignofnether.unit.units.villagers.VillagerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A Mount order is an order (#507, Command Center Eris.13, Nov 5): a unit the player has told to mount is busy until it
 * is up, the way a unit with a move order is, so an enemy walking past does not turn it into a fight. Before, Unit.isIdle
 * read the move goal only: a rider on its way to a mount (its MountGoal walking it there, a path still being worked
 * out) counted as idle, and idle auto-aggression took the passing enemy as its target - the
 * autofind_mounts_the_nearest_mountable flake (#507's history: the rider's attack target named a neighbouring
 * arena's unit).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class MountOrderGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String OWNER = "gametest_mount_order";
    static final String FOE = "gametest_mount_order_foe";

    /**
     * The player orders a skeleton to mount its spider 10 blocks off (the Mount Spider button on the spider, the order
     * the client sends); an enemy villager walks past 6 blocks to the side, inside the skeleton's aggro range (12), the
     * whole time. The skeleton must not take the villager as a target before it is up, and it must mount. The spider is
     * held still (no AI) so only the rider's behaviour is under test. On its own glass pad (ArenaReady.pad, a free
     * slot) so no neighbouring test's units are near.
     */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void a_unit_ordered_to_mount_ignores_an_enemy_walking_past_and_mounts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pad = ArenaReady.pad(helper, 232);   // y + 6 must stay under the build limit (320): 326 put the pad in the void
        SkeletonUnit rider = EntityRegistrar.SKELETON_UNIT.get().create(level);
        SpiderUnit spider = EntityRegistrar.SPIDER_UNIT.get().create(level);
        VillagerUnit foe = EntityRegistrar.VILLAGER_UNIT.get().create(level);
        if (rider == null || spider == null || foe == null) {
            helper.fail("could not create the units");
            return;
        }
        rider.moveTo(pad.getX() + 2.5, pad.getY(), pad.getZ() + 2.5, 0, 0);
        spider.moveTo(pad.getX() + 12.5, pad.getY(), pad.getZ() + 2.5, 0, 0);
        foe.moveTo(pad.getX() + 0.5, pad.getY(), pad.getZ() + 8.5, 0, 0);
        rider.setOwnerName(OWNER);
        spider.setOwnerName(OWNER);
        foe.setOwnerName(FOE);
        spider.setNoAi(true);
        for (var u : List.of(rider, spider, foe))
            level.addFreshEntity(u);
        TestCleanup.entities(helper, List.of(rider, spider, foe));

        // the enemy walks along the pad, 6 blocks to the side of the rider's way, and back
        foe.getMoveGoal().setMoveTarget(pad.offset(15, -1, 8));
        // the player's Mount order: the Mount Spider ability on the spider
        new UnitActionItem(OWNER, UnitAction.MOUNT_SPIDER, spider.getId(), new int[] { rider.getId() }).action(level);
        // a unit with a Mount order is busy: never idle until it is up - else idle auto-aggression may take a passing
        // enemy (Unit.isIdle gates it)
        String[] idle = { rider.isIdle() ? "right after the order, " + state(rider, spider, foe) : null };
        // the setup check: the order took (green 42123437360 saw one run where it had not, right after the order)
        String tookNot = rider.getMountGoal().getTarget() != spider
            ? "the Mount order did not take (nothing tested): " + state(rider, spider, foe) : null;

        List<String> history = new ArrayList<>();
        String[] last = { "" };
        String[] tookFoe = { null };
        helper.onEachTick(() -> {
            if (rider.getVehicle() == spider)
                return;
            if (foe.getMoveGoal().getMoveTarget() == null && foe.position().x > pad.getX() + 13)
                foe.getMoveGoal().setMoveTarget(pad.offset(0, -1, 8));
            else if (foe.getMoveGoal().getMoveTarget() == null && foe.position().x < pad.getX() + 2)
                foe.getMoveGoal().setMoveTarget(pad.offset(15, -1, 8));
            if (idle[0] == null && rider.isIdle())
                idle[0] = "t" + helper.getTick() + " " + state(rider, spider, foe);
            if (tookFoe[0] == null && (rider.getTarget() == foe || rider.getTargetGoal().getTarget() == foe))
                tookFoe[0] = "t" + helper.getTick() + " " + state(rider, spider, foe);
            if (helper.getTick() % 10 != 0)
                return;
            String now = state(rider, spider, foe);
            if (!now.equals(last[0])) {
                history.add("t" + helper.getTick() + " " + now);
                last[0] = now;
            }
        });
        helper.succeedWhen(() -> {
            if (tookNot != null)
                helper.fail(tookNot);
            if (idle[0] != null)
                helper.fail("ordered to mount, the rider counted as idle before it was up: " + idle[0]
                    + "; history: " + String.join(" | ", history));
            if (tookFoe[0] != null)
                helper.fail("ordered to mount, the rider took the enemy walking past as its target: " + tookFoe[0]
                    + "; history: " + String.join(" | ", history));
            if (rider.getVehicle() != spider)
                helper.fail("ordered to mount, the rider is not on its spider yet; now " + state(rider, spider, foe)
                    + "; history: " + String.join(" | ", history));
        });
    }

    /**
     * The Mount Spider cursor sends whichever spider the player clicks, an enemy's too, and a rider mounts only its own
     * side's (MountGoal.tick). Ordered onto an enemy's spider, the rider must drop the order - not stand "going to its
     * mount" for good, never idle and so never fighting back (Mode A review round 1 on #507, blocker 1).
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void a_mount_order_on_an_enemys_spider_is_dropped(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pad = ArenaReady.pad(helper, 232);
        SkeletonUnit rider = EntityRegistrar.SKELETON_UNIT.get().create(level);
        SpiderUnit spider = EntityRegistrar.SPIDER_UNIT.get().create(level);
        if (rider == null || spider == null) {
            helper.fail("could not create the units");
            return;
        }
        rider.moveTo(pad.getX() + 2.5, pad.getY(), pad.getZ() + 2.5, 0, 0);
        spider.moveTo(pad.getX() + 8.5, pad.getY(), pad.getZ() + 2.5, 0, 0);
        rider.setOwnerName(OWNER);
        spider.setOwnerName(FOE);
        spider.setNoAi(true);
        for (var u : List.of(rider, spider))
            level.addFreshEntity(u);
        TestCleanup.entities(helper, List.of(rider, spider));
        new UnitActionItem(OWNER, UnitAction.MOUNT_SPIDER, spider.getId(), new int[] { rider.getId() }).action(level);
        // the setup check: the order took (the rider set off for the spider)
        boolean took = rider.getMountGoal().getTarget() == spider;
        List<String> history = new ArrayList<>();
        String[] last = { "" };
        helper.onEachTick(() -> {
            String now = state(rider, spider, spider);
            if (!now.equals(last[0]) && history.size() < 40) {
                history.add("t" + helper.getTick() + " " + now);
                last[0] = now;
            }
        });
        helper.runAfterDelay(100, () -> {
            List<String> problems = new ArrayList<>();
            if (!took)
                problems.add("the Mount order on the enemy's spider did not take (nothing tested): " + history);
            if (rider.getVehicle() == spider)
                problems.add("the rider mounted an enemy's spider");
            // (dropped, it may well fight the spider: that is the point - only the order must be gone)
            if (rider.getMountGoal().getTarget() != null)
                problems.add("5 s after a Mount order on an enemy's spider the rider still holds it: " + state(rider, spider, spider)
                    + "; history: " + String.join(" | ", history));
            TerrainGameTests.finish(helper, problems);
        });
    }

    /**
     * A spider walled in (stone 3 blocks out on every side, a roof): no way to it. The rider must give the Mount order
     * up (MountGoal: no block nearer in GIVE_UP_TICKS), within the 30 s the move tests allow - not stand "going to its
     * mount" for good (review round 1 on #507, blocker 2).
     */
    @GameTest(template = ARENA, timeoutTicks = 900)
    public static void a_mount_order_on_a_walled_in_spider_is_given_up(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pad = ArenaReady.pad(helper, 232);
        TestCleanup.BlockUndo box = TestCleanup.blocks(helper);
        BlockPos at = pad.offset(11, 0, 8);
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++)
                for (int dy = 0; dy <= 3; dy++)
                    if (Math.abs(dx) == 3 || Math.abs(dz) == 3 || dy == 3)
                        box.set(at.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        SkeletonUnit rider = EntityRegistrar.SKELETON_UNIT.get().create(level);
        SpiderUnit spider = EntityRegistrar.SPIDER_UNIT.get().create(level);
        if (rider == null || spider == null) {
            helper.fail("could not create the units");
            return;
        }
        rider.moveTo(pad.getX() + 2.5, pad.getY(), pad.getZ() + 8.5, 0, 0);
        spider.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        rider.setOwnerName(OWNER);
        spider.setOwnerName(OWNER);
        spider.setNoAi(true);
        for (var u : List.of(rider, spider))
            level.addFreshEntity(u);
        TestCleanup.entities(helper, List.of(rider, spider));
        new UnitActionItem(OWNER, UnitAction.MOUNT_SPIDER, spider.getId(), new int[] { rider.getId() }).action(level);
        helper.runAfterDelay(600, () -> {
            List<String> problems = new ArrayList<>();
            if (rider.getVehicle() == spider)
                problems.add("the rider reached a walled-in spider (the box is open: nothing tested)");
            else if (rider.getMountGoal().getTarget() != null || !rider.isIdle())
                problems.add("30 s after a Mount order on a walled-in spider the rider still holds it: " + state(rider, spider, spider)
                    + ", last unreachable " + rider.getMountGoal().getLastUnreachableTarget());
            TerrainGameTests.finish(helper, problems);
        });
    }

    /** Where the three are, the mount goal's state and the rider's attack target. */
    static String state(SkeletonUnit rider, SpiderUnit spider, net.minecraft.world.entity.Mob foe) {
        var goal = rider.getMountGoal();
        BlockPos mt = goal.getMoveTarget();
        LivingEntity target = goal.getTarget();
        LivingEntity attack = rider.getTarget() != null ? rider.getTarget() : rider.getTargetGoal().getTarget();
        return String.format(Locale.ROOT,
            "rider at %.1f,%.1f,%.1f vehicle %s idle %s d(spider) %.2f | foe at %.1f,%.1f d %.2f | goal target %s"
                + " moveTarget %s pathPending %s | attack %s",
            rider.getX(), rider.getY(), rider.getZ(),
            rider.getVehicle() == null ? "none" : rider.getVehicle() == spider ? "spider" : rider.getVehicle().getType().toShortString(),
            rider.isIdle(), rider.distanceTo(spider), foe.getX(), foe.getZ(), rider.distanceTo(foe),
            target == null ? "none" : target == spider ? "spider" : target.getType().toShortString(),
            mt == null ? "none" : mt.toShortString(), goal.isPathPending(),
            attack == null ? "none" : attack == foe ? "FOE" : attack.getType().toShortString());
    }
}
