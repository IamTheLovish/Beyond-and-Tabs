package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.Blueprints;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.buildings.defence.Defences;
import com.solegendary.reignofnether.bot.BotPlayer;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.CombatEngineers;
import com.solegendary.reignofnether.unit.GuardServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.command.CommandQueues;
import com.solegendary.reignofnether.unit.command.QueueMode;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Builders assist builders (BAR: any constructor may assist any build; playtest Oct 20 lane C item 13, and the
 * tester's "multiple builders including different types ... it only sends one"):
 * <ul>
 *   <li>every selected builder, whatever its type, is sent to a build order and adds build power there;</li>
 *   <li>a builder that may not start the building itself (a combat engineer and a non-field-defence) still assists;</li>
 *   <li>a builder guarding a builder (right-click on a friendly builder) helps with its next construction, stays out of
 *       the footprint, and stops at its next order;</li>
 *   <li>the right-click rule: builders selected + a friendly builder under the cursor = guard, otherwise follow.</li>
 * </ul>
 * Each test on its own stone pad in the sky over its arena (BlueprintGameTests' pattern), unique owners, all undone in
 * the same callback.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BuilderAssistGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final int PAD_Y = 200, PAD_R = 12, PAD_H = 14;

    /** One test's pad, owner, pool, units and the site, undone together. */
    static final class Site {
        final ServerLevel level;
        final String owner;
        final int cx, cz;
        final List<Entity> made = new ArrayList<>();
        final List<Resources> pools = new ArrayList<>();
        final List<Integer> guards = new ArrayList<>();
        BuildingPlacement bp;

        Site(GameTestHelper helper, String tag) {
            level = helper.getLevel();
            owner = "gametest_assist_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
            BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
            cx = base.getX();
            cz = base.getZ();
            CombatEngineerGameTests.skyPad(level, cx, PAD_Y, cz, PAD_R, PAD_H, false);
            Resources pool = new Resources(owner, 0, 5000, 5000);
            ResourcesServerEvents.resourcesList.add(pool);
            pools.add(pool);
        }

        BlockPos origin() {
            return new BlockPos(cx - 1, PAD_Y, cz - 1);
        }

        /** A builder of {@code type} standing {@code dx, dz} from the pad's centre. */
        Mob builder(EntityType<? extends Mob> type, int dx, int dz) {
            return CombatEngineerGameTests.spawn(level, type, owner, new BlockPos(cx + dx, PAD_Y + 1, cz + dz), made);
        }

        /** A player's build order through the real placement path (not command-placed: a blueprint its builders raise). */
        boolean order(Building building, int... builderIds) {
            bp = BuildingServerEvents.placeBuilding(building, origin(), Rotation.NONE, owner, builderIds,
                false, false, false, true);
            return bp != null;
        }

        boolean registered() {
            return bp != null && BuildingServerEvents.getBuildings().contains(bp);
        }

        void undo() {
            for (int id : guards)
                GuardServerEvents.clear(id);
            if (bp != null) {
                Blueprints.cancel(bp, null);
                if (BuildingServerEvents.getBuildings().remove(bp))
                    bp.forceChunk(false);
            }
            CombatEngineerGameTests.cleanUp(made, pools, List.of(owner));
            CombatEngineerGameTests.skyPad(level, cx, PAD_Y, cz, PAD_R, PAD_H, true);
        }
    }

    static void finish(GameTestHelper helper, Site s, List<String> problems) {
        try {
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        } finally {
            s.undo();
        }
    }

    static BuildingPlacement targetOf(Mob m) {
        return ((WorkerUnit) m).getBuildRepairGoal().getBuildingTarget();
    }

    static boolean building(BuildingPlacement bp, Mob m) {
        for (WorkerUnit w : bp.getBuilders())
            if (w == m)
                return true;
        return false;
    }

    static String where(Mob m) {
        return m.getClass().getSimpleName() + " at " + m.blockPosition().toShortString() + " target "
            + (targetOf(m) == null ? "none" : targetOf(m).originPos.toShortString());
    }

    /** Inside the site's footprint (x/z), where a unit would stand in the way of the blocks. */
    static boolean onFootprint(BuildingPlacement bp, Mob m) {
        BlockPos p = m.blockPosition();
        return p.getX() >= bp.minCorner.getX() && p.getX() <= bp.maxCorner.getX()
            && p.getZ() >= bp.minCorner.getZ() && p.getZ() <= bp.maxCorner.getZ();
    }

    // ------------------------------------------------------------------------------------------------ mixed types

    /**
     * A worker and a T2 constructor (Royal Architect) - two builder types in one selection - ordered onto one site:
     * both are given the site, and both stand on it adding build power at the same time once it rises.
     */
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void two_builder_types_both_go_to_a_build_order_and_both_build(GameTestHelper helper) {
        Site s = new Site(helper, "mixed");
        List<String> problems = new ArrayList<>();
        // three builder types from three sides of the pad, at different distances: a worker, a T2 constructor and a
        // commander (a worker with the commander tag)
        Mob worker = s.builder(EntityRegistrar.VILLAGER_UNIT.get(), -11, -4);
        Mob architect = s.builder(EntityRegistrar.ROYAL_ARCHITECT_UNIT.get(), 11, 6);
        Mob commander = s.builder(EntityRegistrar.VILLAGER_UNIT.get(), 2, 11);
        if (worker == null || architect == null || commander == null) {
            s.undo();
            helper.fail("(setup) could not create the builders");
            return;
        }
        com.solegendary.reignofnether.player.CommanderServerEvents.makeCommander(commander);
        Mob[] all = { worker, architect, commander };
        if (!s.order(Defences.VILLAGERS.raidTower(), worker.getId(), architect.getId(), commander.getId())) {
            s.undo();
            helper.fail("the build order was refused");
            return;
        }
        for (Mob m : all)
            if (targetOf(m) != s.bp)
                problems.add("not sent: " + where(m));
        if (!problems.isEmpty()) {
            finish(helper, s, problems);
            return;
        }
        BlueprintGameTests.poll(helper, 0, 560,
            () -> s.registered() && building(s.bp, worker) && building(s.bp, architect) && building(s.bp, commander)
                && (s.bp.getBlocksPlaced() > 0 || s.bp.buildProgress > 0),
            () -> finish(helper, s, problems),
            () -> {
                problems.add("the three builders never built together: registered " + s.registered() + ", builders "
                    + s.bp.getBuilders().size() + ", progress " + s.bp.buildProgress + "; " + where(worker) + "; "
                    + where(architect) + "; " + where(commander));
                finish(helper, s, problems);
            });
    }

    /**
     * A combat engineer may not START a wind generator (field defences only), but in a group with a worker it is sent
     * too and assists: both build it.
     */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void a_builder_that_cannot_start_the_building_still_assists(GameTestHelper helper) {
        Site s = new Site(helper, "cannot");
        List<String> problems = new ArrayList<>();
        BotPlayer.Kit kit = BotPlayer.kitFor(Factions.VILLAGERS);
        Building wind = kit == null ? null : kit.wind();
        if (wind == null || CombatEngineers.mayStart(wind)) {
            s.undo();
            helper.fail("(setup) need a building a combat engineer may not start; wind = " + wind);
            return;
        }
        Mob worker = s.builder(EntityRegistrar.VILLAGER_UNIT.get(), -7, 0);
        Mob engineer = s.builder(EntityRegistrar.FIELD_MASON_UNIT.get(), 7, 0);
        if (worker == null || engineer == null) {
            s.undo();
            helper.fail("(setup) could not create the builders");
            return;
        }
        if (!s.order(wind, worker.getId(), engineer.getId())) {
            s.undo();
            helper.fail("the build order was refused");
            return;
        }
        if (targetOf(engineer) != s.bp)
            problems.add("the engineer was not sent to assist: " + where(engineer));
        if (!problems.isEmpty()) {
            finish(helper, s, problems);
            return;
        }
        BlueprintGameTests.poll(helper, 0, 360,
            () -> s.registered() && building(s.bp, worker) && building(s.bp, engineer)
                && (s.bp.getBlocksPlaced() > 0 || s.bp.buildProgress > 0),
            () -> finish(helper, s, problems),
            () -> {
                problems.add("the engineer never assisted: registered " + s.registered() + ", builders "
                    + s.bp.getBuilders().size() + ", progress " + s.bp.buildProgress + "; " + where(worker) + "; "
                    + where(engineer));
                finish(helper, s, problems);
            });
    }

    // ------------------------------------------------------------------------------------------------ guard assist

    /**
     * A worker told to guard another worker (the right-click order) helps with the ward's next construction from
     * outside its footprint, and a new order (a move) ends the guard: it leaves the site and stays off it.
     */
    @GameTest(template = ARENA, timeoutTicks = 500)
    public static void a_builder_guarding_a_builder_assists_its_next_build_and_stops_on_a_new_order(GameTestHelper helper) {
        Site s = new Site(helper, "guard");
        List<String> problems = new ArrayList<>();
        Mob ward = s.builder(EntityRegistrar.VILLAGER_UNIT.get(), -6, 0);
        Mob guard = s.builder(EntityRegistrar.ROYAL_ARCHITECT_UNIT.get(), -9, 3);
        if (ward == null || guard == null) {
            s.undo();
            helper.fail("(setup) could not create the builders");
            return;
        }
        s.guards.add(guard.getId());
        CommandQueues.issue(s.level, s.owner, UnitAction.GUARD, ward.getId(), new int[] { guard.getId() },
            ward.blockPosition(), BlockPos.ZERO, QueueMode.REPLACE);
        if (!GuardServerEvents.isGuarding(guard.getId())) {
            s.undo();
            helper.fail("the guard order did not take");
            return;
        }
        helper.runAfterDelay(20, () -> {
            // the ward alone is ordered to build
            if (!s.order(Defences.VILLAGERS.raidTower(), ward.getId())) {
                problems.add("the ward's build order was refused");
                finish(helper, s, problems);
                return;
            }
            BlueprintGameTests.poll(helper, 0, 360,
                () -> s.registered() && building(s.bp, ward) && building(s.bp, guard)
                    && (s.bp.getBlocksPlaced() > 0 || s.bp.buildProgress > 0),
                () -> {
                    if (onFootprint(s.bp, guard))
                        problems.add("the guard stands on the footprint: " + where(guard) + ", site "
                            + s.bp.minCorner.toShortString() + " .. " + s.bp.maxCorner.toShortString());
                    BlockPos away = new BlockPos(s.cx + 9, PAD_Y + 1, s.cz + 9);
                    CommandQueues.issue(s.level, s.owner, UnitAction.MOVE, -1, new int[] { guard.getId() }, away,
                        BlockPos.ZERO, QueueMode.REPLACE);
                    if (GuardServerEvents.isGuarding(guard.getId()))
                        problems.add("the move order did not end the guard");
                    // past two guard passes (every 10 ticks): the guard must not be pulled back onto the site
                    helper.runAfterDelay(40, () -> {
                        if (GuardServerEvents.isGuarding(guard.getId()))
                            problems.add("guarding again after the move order");
                        if (targetOf(guard) == s.bp)
                            problems.add("still assisting the ward after the move order: " + where(guard));
                        finish(helper, s, problems);
                    });
                },
                () -> {
                    problems.add("the guard never helped the ward build: registered " + s.registered() + ", builders "
                        + s.bp.getBuilders().size() + ", guarding " + GuardServerEvents.isGuarding(guard.getId())
                        + "; ward " + where(ward) + "; guard " + where(guard));
                    finish(helper, s, problems);
                });
        });
    }

    /**
     * The right-click rule (UnitClientEvents calls the same method): builders selected + a friendly builder under the
     * cursor = guard (assist). A non-builder target, an unfriendly target or a selection with no builder = follow as
     * before; a builder right-clicking itself alone is not a guard order.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void right_click_on_a_friendly_builder_with_builders_selected_is_guard(GameTestHelper helper) {
        Site s = new Site(helper, "rclick");
        List<String> problems = new ArrayList<>();
        Mob worker = s.builder(EntityRegistrar.VILLAGER_UNIT.get(), -4, 0);
        Mob architect = s.builder(EntityRegistrar.ROYAL_ARCHITECT_UNIT.get(), 4, 0);
        Mob soldier = s.builder(EntityRegistrar.VINDICATOR_UNIT.get(), 0, 4);
        if (worker == null || architect == null || soldier == null) {
            s.undo();
            helper.fail("(setup) could not create the units");
            return;
        }
        List<LivingEntity> builders = List.of(worker);
        List<LivingEntity> mixed = List.of(worker, soldier);
        List<LivingEntity> fighters = List.of(soldier);
        if (!GuardServerEvents.rightClickGuards(builders, architect, true))
            problems.add("a worker right-clicking a friendly T2 constructor does not guard");
        if (!GuardServerEvents.rightClickGuards(mixed, architect, true))
            problems.add("a mixed selection with a builder right-clicking a friendly builder does not guard");
        if (GuardServerEvents.rightClickGuards(builders, soldier, true))
            problems.add("right-click on a non-builder guards (should follow)");
        if (GuardServerEvents.rightClickGuards(builders, architect, false))
            problems.add("right-click on an unfriendly builder guards");
        if (GuardServerEvents.rightClickGuards(fighters, architect, true))
            problems.add("a selection with no builder guards (should follow)");
        if (GuardServerEvents.rightClickGuards(builders, worker, true))
            problems.add("a lone builder right-clicking itself guards");
        finish(helper, s, problems);
    }
}
