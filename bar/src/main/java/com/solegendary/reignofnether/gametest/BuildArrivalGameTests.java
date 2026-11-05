package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.Blueprints;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.buildings.defence.Defences;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.goals.BuildRepairGoal;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Build on arrival (user: "buildings should not be constructed until the builder reaches it"): a build order given to
 * builders about 30 blocks away leaves the site at 0 progress with no block placed while they walk, and the site rises
 * once one of them is in build range. Checked every 5 ticks, not only at t20: at no moment may progress or a changed
 * block exist on the site while no assigned builder is in range. One test per order path - a plain order, a mixed
 * builder-type order (the builder card union, PR #232) and the second site of a shift-queue.
 * One stone pad in the sky over the arena (BlueprintGameTests' pattern), a unique owner, all undone in the callback.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BuildArrivalGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    /** One batch per test: pads of tests run side by side in one batch overlap, and one clean-up clears another's floor. */
    static final String BATCH = "build_arrival", BATCH_MIXED = "build_arrival_mixed", BATCH_QUEUE = "build_arrival_queue";
    static final int PAD_Y = 200, PAD_R = 22, PAD_H = 14;
    /** Worker and site offsets from the pad centre along x: about 30 blocks between the workers and the footprint. */
    static final int WORKER_DX = -20, SITE_DX = 10;

    /** One test's pad, owner, units and sites, undone together. */
    static final class Scene {
        final GameTestHelper helper;
        final ServerLevel level;
        final String owner;
        final int cx, cz;
        final List<Entity> made = new ArrayList<>();
        final List<Resources> pools = new ArrayList<>();
        final List<BuildingPlacement> sites = new ArrayList<>();
        final List<String> problems = new ArrayList<>();

        Scene(GameTestHelper helper, String tag) {
            this.helper = helper;
            level = helper.getLevel();
            owner = "gametest_arrival_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
            BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
            cx = base.getX();
            cz = base.getZ();
            CombatEngineerGameTests.skyPad(level, cx, PAD_Y, cz, PAD_R, PAD_H, false);
            Resources pool = new Resources(owner, 0, 5000, 5000);
            ResourcesServerEvents.resourcesList.add(pool);
            pools.add(pool);
        }

        Mob builder(EntityType<? extends Mob> type, int dx, int dz) {
            return CombatEngineerGameTests.spawn(level, type, owner, new BlockPos(cx + dx, PAD_Y + 1, cz + dz), made);
        }

        BlockPos origin(int dx) {
            return new BlockPos(cx + dx, PAD_Y, cz - 1);
        }

        /** A player's raid-tower order through the real placement path (as BuildingServerboundPacket PLACE / PLACE_AND_QUEUE). */
        BuildingPlacement order(BlockPos origin, boolean queue, Mob... builders) {
            int[] ids = new int[builders.length];
            for (int i = 0; i < ids.length; i++)
                ids[i] = builders[i].getId();
            BuildingPlacement bp = BuildingServerEvents.placeBuilding(Defences.VILLAGERS.raidTower(), origin, Rotation.NONE,
                owner, ids, queue, false, false, true);
            if (bp != null)
                sites.add(bp);
            return bp;
        }

        void undo() {
            for (BuildingPlacement bp : sites) {
                Blueprints.cancel(bp, null);
                if (BuildingServerEvents.getBuildings().remove(bp))
                    bp.forceChunk(false);
            }
            CombatEngineerGameTests.cleanUp(made, pools, List.of(owner));
            CombatEngineerGameTests.skyPad(level, cx, PAD_Y, cz, PAD_R, PAD_H, true);
        }

        void finish() {
            try {
                if (problems.isEmpty())
                    helper.succeed();
                else
                    helper.fail(String.join("; ", problems));
            } finally {
                undo();
            }
        }

        void setupFail(String why) {
            undo();
            helper.fail("(setup) " + why);
        }
    }

    /** A site's footprint box (one block round it) as it is now, before any order. */
    static final class Box {
        final BlockPos lo, hi;
        final List<BlockState> before = new ArrayList<>();

        Box(Scene s, BlockPos origin) {
            BuildingPlacement probe = BuildingUtils.getNewBuildingPlacement(Defences.VILLAGERS.raidTower(), s.level, origin,
                Rotation.NONE, s.owner, false);
            lo = probe == null ? origin : probe.minCorner.offset(-1, -1, -1);
            hi = probe == null ? origin : probe.maxCorner.offset(1, 1, 1);
            for (int x = lo.getX(); x <= hi.getX(); x++)
                for (int y = lo.getY(); y <= hi.getY(); y++)
                    for (int z = lo.getZ(); z <= hi.getZ(); z++)
                        before.add(s.level.getBlockState(new BlockPos(x, y, z)));
        }

        int changed(ServerLevel level) {
            int n = 0, i = 0;
            for (int x = lo.getX(); x <= hi.getX(); x++)
                for (int y = lo.getY(); y <= hi.getY(); y++)
                    for (int z = lo.getZ(); z <= hi.getZ(); z++)
                        if (level.getBlockState(new BlockPos(x, y, z)) != before.get(i++))
                            n++;
            return n;
        }
    }

    /** The worker is on this site and within its build range (BuildRepairGoal.isBuilding). */
    static boolean inRange(Mob worker, BuildingPlacement bp) {
        BuildRepairGoal goal = ((WorkerUnit) worker).getBuildRepairGoal();
        return goal != null && goal.getBuildingTarget() == bp && goal.isBuilding();
    }

    static String where(List<Mob> workers) {
        StringBuilder sb = new StringBuilder();
        for (Mob m : workers)
            sb.append(m.getClass().getSimpleName()).append(" at ").append(m.blockPosition().toShortString()).append(' ');
        return sb.toString().trim();
    }

    /**
     * Every 5 ticks until {@code bp} shows progress (or {@code max} ticks): at t20 progress 0, no block changed and no
     * worker yet in range; at any tick, progress or a changed block with no worker in range fails. Then {@code then}.
     */
    static void watch(Scene s, BuildingPlacement bp, Box box, List<Mob> workers, int max, Runnable then) {
        int[] tick = { 0 };
        boolean[] sampled20 = { false };
        Runnable[] step = new Runnable[1];
        step[0] = () -> s.helper.runAfterDelay(5, () -> {
            tick[0] += 5;
            int blocks = box.changed(s.level);
            float progress = bp.buildProgress;
            boolean near = false;
            for (Mob m : workers)
                near |= inRange(m, bp) || bp.getBuilders().contains((WorkerUnit) m);
            if (tick[0] >= 20 && !sampled20[0]) {
                sampled20[0] = true;
                if (near)
                    s.problems.add("(setup) a builder reached the site by t20 - not far enough to test");
                if (progress != 0f)
                    s.problems.add("t20: build progress " + progress + " before a builder arrived (want 0)");
                if (blocks != 0)
                    s.problems.add("t20: " + blocks + " blocks changed on the site before a builder arrived (want 0)");
                if (!s.problems.isEmpty()) {
                    s.finish();
                    return;
                }
            }
            if ((progress > 0f || blocks > 0) && !near) {
                s.problems.add("t" + tick[0] + ": construction (progress " + progress + ", " + blocks
                    + " blocks changed) with no builder in range: " + where(workers));
                s.finish();
                return;
            }
            if (progress > 0f) {
                if (Blueprints.isPending(bp))
                    s.problems.add("progress on a site still listed as a blueprint");
                then.run();
                return;
            }
            if (tick[0] >= max) {
                s.problems.add("no progress after the builders' walk: " + where(workers) + ", pending "
                    + Blueprints.isPending(bp) + ", blocks " + blocks);
                s.finish();
                return;
            }
            step[0].run();
        });
        step[0].run();
    }

    // ------------------------------------------------------------------------------------------------ plain order

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 900)
    public static void a_site_30_blocks_away_stays_unbuilt_until_its_worker_arrives(GameTestHelper helper) {
        Scene s = new Scene(helper, "plain");
        BlockPos origin = s.origin(SITE_DX);
        Box box = new Box(s, origin);
        Mob worker = s.builder(EntityRegistrar.VILLAGER_UNIT.get(), WORKER_DX, 0);
        if (worker == null) {
            s.setupFail("could not create the worker");
            return;
        }
        if (box.lo.getX() + 1 - worker.blockPosition().getX() < 28) {
            s.setupFail("the worker is too close to the site");
            return;
        }
        BuildingPlacement bp = s.order(origin, false, worker);
        if (bp == null) {
            s.setupFail("the build order was refused");
            return;
        }
        if (((WorkerUnit) worker).getBuildRepairGoal().getBuildingTarget() != bp)
            s.problems.add("the worker was not sent to the site");
        watch(s, bp, box, List.of(worker), 850, s::finish);
    }

    // ------------------------------------------------------------------------------------------------ mixed builder types

    /** A Royal Architect and a Field Mason (two builder cards, one union order, PR #232), both about 30 blocks off. */
    @GameTest(template = ARENA, batch = BATCH_MIXED, timeoutTicks = 900)
    public static void a_mixed_builder_order_stays_unbuilt_until_a_builder_arrives(GameTestHelper helper) {
        Scene s = new Scene(helper, "mixed");
        BlockPos origin = s.origin(SITE_DX);
        Box box = new Box(s, origin);
        Mob architect = s.builder(EntityRegistrar.ROYAL_ARCHITECT_UNIT.get(), WORKER_DX, -3);
        Mob mason = s.builder(EntityRegistrar.FIELD_MASON_UNIT.get(), WORKER_DX, 3);
        if (architect == null || mason == null) {
            s.setupFail("could not create the builders");
            return;
        }
        BuildingPlacement bp = s.order(origin, false, architect, mason);
        if (bp == null) {
            s.setupFail("the build order was refused");
            return;
        }
        for (Mob m : List.of(architect, mason))
            if (((WorkerUnit) m).getBuildRepairGoal().getBuildingTarget() != bp)
                s.problems.add("not sent: " + m.getClass().getSimpleName());
        watch(s, bp, box, List.of(architect, mason), 850, s::finish);
    }

    // ------------------------------------------------------------------------------------------------ shift-queue

    /**
     * Shift-queued: site A beside the worker, then site B about 30 blocks away (PLACE_AND_QUEUE both). B stays untouched
     * while the worker builds A; when A is cancelled the worker walks to B, and B rises only once it is there.
     */
    @GameTest(template = ARENA, batch = BATCH_QUEUE, timeoutTicks = 1200)
    public static void the_second_site_of_a_shift_queue_stays_unbuilt_until_the_worker_arrives(GameTestHelper helper) {
        Scene s = new Scene(helper, "queue");
        BlockPos originA = s.origin(WORKER_DX + 3), originB = s.origin(SITE_DX);
        Box boxB = new Box(s, originB);
        Mob worker = s.builder(EntityRegistrar.VILLAGER_UNIT.get(), WORKER_DX, 0);
        if (worker == null) {
            s.setupFail("could not create the worker");
            return;
        }
        BuildingPlacement a = s.order(originA, true, worker);
        BuildingPlacement b = s.order(originB, true, worker);
        if (a == null || b == null) {
            s.setupFail("a queued order was refused: A " + (a != null) + ", B " + (b != null));
            return;
        }
        if (!((WorkerUnit) worker).getBuildRepairGoal().queuedBuildings.contains(b)
                && ((WorkerUnit) worker).getBuildRepairGoal().getBuildingTarget() != b)
            s.problems.add("site B is not in the worker's queue");
        // phase 1: the worker on A for 200 ticks; B must stay a ghost the whole time
        int[] tick = { 0 };
        Runnable[] step = new Runnable[1];
        step[0] = () -> helper.runAfterDelay(5, () -> {
            tick[0] += 5;
            int blocks = boxB.changed(s.level);
            if (b.buildProgress != 0f || blocks != 0 || !Blueprints.isPending(b)) {
                s.problems.add("t" + tick[0] + ": queued site B touched while the worker is on A (progress "
                    + b.buildProgress + ", " + blocks + " blocks changed, pending " + Blueprints.isPending(b)
                    + "), worker at " + worker.blockPosition().toShortString());
                s.finish();
                return;
            }
            if (tick[0] < 200) {
                step[0].run();
                return;
            }
            if (!BuildingServerEvents.getBuildings().contains(a))
                s.problems.add("(setup) site A never started: worker at " + worker.blockPosition().toShortString());
            if (!s.problems.isEmpty()) {
                s.finish();
                return;
            }
            // phase 2: A cancelled - the worker moves on to B, which rises only on arrival
            BuildingServerEvents.cancelBuilding(a, s.owner);
            watch(s, b, boxB, List.of(worker), 900, s::finish);
        });
        step[0].run();
    }
}
