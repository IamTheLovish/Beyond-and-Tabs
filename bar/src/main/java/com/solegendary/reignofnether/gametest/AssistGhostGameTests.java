package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.bot.BotPlayer;
import com.solegendary.reignofnether.building.Blueprints;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.buildings.defence.Defences;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.gametest.BuilderAssistGameTests.Site;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.GuardServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.command.CommandQueues;
import com.solegendary.reignofnether.unit.command.QueueMode;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

import static com.solegendary.reignofnether.gametest.BuilderAssistGameTests.building;
import static com.solegendary.reignofnether.gametest.BuilderAssistGameTests.finish;
import static com.solegendary.reignofnether.gametest.BuilderAssistGameTests.targetOf;
import static com.solegendary.reignofnether.gametest.BuilderAssistGameTests.where;

/**
 * The user's "when a ghost is placed down by another builder, other builders should be able to assist that production
 * or the builder itself" (lane C, Oct 26). Orders go through CommandQueues.issue, which is what the right-click packet
 * (UnitActionServerboundPacket.accept) calls on the server, and the queues and guard passes run on real ticks.
 * <ul>
 *   <li>AC-1: builder B told to build builder A's ghost / site joins it, and two builders raise it faster than one;</li>
 *   <li>AC-2: B guarding A keeps the guard when A is given a build order - even with B standing on A's new footprint
 *       (the feature check's timing: guard, ward's order ~10 ticks later, B idle at t20 and for good) - builds A's site,
 *       and follows A to its next site.</li>
 * </ul>
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class AssistGhostGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    // the AC-2 guards are Royal Architects, fighters: another test's units (another owner, so hostile) within 18
    // blocks of the ward would draw them off the pad - each AC-2 test runs in a batch of its own
    static final String BATCH = "assist_ghost";

    /** A second site on the same pad, undone with it. */
    static BuildingPlacement place(Site s, Building b, BlockPos origin, int... builderIds) {
        return BuildingServerEvents.placeBuilding(b, origin, Rotation.NONE, s.owner, builderIds, false, false, false, true);
    }

    static void drop(BuildingPlacement bp) {
        if (bp == null)
            return;
        Blueprints.cancel(bp, null);
        if (BuildingServerEvents.getBuildings().remove(bp))
            bp.forceChunk(false);
    }

    static boolean started(BuildingPlacement bp) {
        return bp.getBlocksPlaced() > 0 || bp.buildProgress > 0;
    }

    // ------------------------------------------------------------------------------------------------ AC-1

    /**
     * AC-1 (ghost): A's build order is still a ghost (A far away, nothing started) when B - a different builder type, a
     * Royal Architect - is given a build order on it: B is sent to it at once, and both end up building it.
     */
    @GameTest(template = ARENA, timeoutTicks = 600, batch = BATCH)
    public static void ac1_a_builder_ordered_onto_another_builders_ghost_joins_it(GameTestHelper helper) {
        Site s = new Site(helper, "ghost");
        List<String> problems = new ArrayList<>();
        Mob a = s.builder(EntityRegistrar.VILLAGER_UNIT.get(), -11, -6);
        Mob b = s.builder(EntityRegistrar.ROYAL_ARCHITECT_UNIT.get(), 11, 6);
        if (a == null || b == null) {
            s.undo();
            helper.fail("(setup) could not create the builders");
            return;
        }
        if (!s.order(Defences.VILLAGERS.raidTower(), a.getId())) {
            s.undo();
            helper.fail("A's build order was refused");
            return;
        }
        if (!Blueprints.isPending(s.bp))
            problems.add("(setup) A's order is not a ghost when B is ordered: pending " + Blueprints.isPending(s.bp));
        CommandQueues.issue(s.level, s.owner, UnitAction.BUILD_REPAIR, -1, new int[] { b.getId() }, s.bp.centrePos,
            BlockPos.ZERO, QueueMode.REPLACE);
        if (targetOf(b) != s.bp)
            problems.add("B was not sent to A's ghost: " + where(b));
        if (!problems.isEmpty()) {
            finish(helper, s, problems);
            return;
        }
        BlueprintGameTests.poll(helper, 0, 560,
            () -> s.registered() && building(s.bp, a) && building(s.bp, b) && started(s.bp),
            () -> finish(helper, s, problems),
            () -> {
                problems.add("A and B never built A's ghost together: registered " + s.registered() + ", builders "
                    + s.bp.getBuilders().size() + ", progress " + s.bp.buildProgress + "; A " + where(a) + "; B "
                    + where(b));
                finish(helper, s, problems);
            });
    }

    /**
     * AC-1 (site under construction, rate): A builds alone; B - a Field Mason, a third builder type - is then ordered
     * onto A's site: it joins as a builder and the site's progress per tick rises above A's alone.
     */
    @GameTest(template = ARENA, timeoutTicks = 700, batch = BATCH)
    public static void ac1_a_builder_ordered_onto_another_builders_site_makes_it_rise_faster(GameTestHelper helper) {
        Site s = new Site(helper, "rate");
        List<String> problems = new ArrayList<>();
        BotPlayer.Kit kit = BotPlayer.kitFor(Factions.VILLAGERS);
        Building wind = kit == null ? null : kit.wind();
        Mob a = s.builder(EntityRegistrar.VILLAGER_UNIT.get(), -6, 0);
        Mob b = s.builder(EntityRegistrar.FIELD_MASON_UNIT.get(), 10, 10);
        if (wind == null || a == null || b == null) {
            s.undo();
            helper.fail("(setup) need a wind generator and two builders; wind = " + wind);
            return;
        }
        if (!s.order(wind, a.getId())) {
            s.undo();
            helper.fail("A's build order was refused");
            return;
        }
        // A alone: wait until it is building, then measure 20 ticks
        BlueprintGameTests.poll(helper, 0, 300,
            () -> s.registered() && building(s.bp, a) && started(s.bp),
            () -> {
                float p0 = s.bp.buildProgress;
                helper.runAfterDelay(20, () -> {
                    if (s.bp.getBuilders().size() != 1 || s.bp.isBuilt) {
                        problems.add("(setup) A was not building alone for the first window: builders "
                            + s.bp.getBuilders().size() + ", built " + s.bp.isBuilt);
                        finish(helper, s, problems);
                        return;
                    }
                    float alone = s.bp.buildProgress - p0;
                    CommandQueues.issue(s.level, s.owner, UnitAction.BUILD_REPAIR, -1, new int[] { b.getId() },
                        s.bp.centrePos, BlockPos.ZERO, QueueMode.REPLACE);
                    if (targetOf(b) != s.bp) {
                        problems.add("B was not sent to A's site: " + where(b));
                        finish(helper, s, problems);
                        return;
                    }
                    BlueprintGameTests.poll(helper, 0, 300,
                        () -> building(s.bp, a) && building(s.bp, b),
                        () -> {
                            float q0 = s.bp.buildProgress;
                            helper.runAfterDelay(20, () -> {
                                float both = s.bp.buildProgress - q0;
                                if (!s.bp.isBuilt && s.bp.getBuilders().size() < 2)
                                    problems.add("B stopped building during the second window: " + where(b));
                                else if (!(both > alone))
                                    problems.add("two builders were not faster than one: alone " + alone
                                        + " per 20 ticks, both " + both + " (built " + s.bp.isBuilt + ")");
                                finish(helper, s, problems);
                            });
                        },
                        () -> {
                            problems.add("B never joined A's site: builders " + s.bp.getBuilders().size() + "; A "
                                + where(a) + "; B " + where(b));
                            finish(helper, s, problems);
                        });
                });
            },
            () -> {
                problems.add("(setup) A never started building alone: registered " + s.registered() + "; "
                    + where(a));
                finish(helper, s, problems);
            });
    }

    // ------------------------------------------------------------------------------------------------ AC-2

    /**
     * AC-2, the feature check's timing: B (Royal Architect) guards A (worker) standing next to it, A is given a build
     * order (a barracks) 10 ticks later and both stand on its footprint. At t20 B still guards and is aimed at A's
     * site; at t200 it still guards and is building it (or helped build it, if it is already done).
     */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = BATCH + "_guard_footprint")
    public static void ac2_a_guard_standing_on_its_wards_new_footprint_keeps_guarding_and_builds(GameTestHelper helper) {
        Site s = new Site(helper, "guardtime");
        List<String> problems = new ArrayList<>();
        Mob a = s.builder(EntityRegistrar.VILLAGER_UNIT.get(), 1, 1);
        Mob b = s.builder(EntityRegistrar.ROYAL_ARCHITECT_UNIT.get(), 0, 2);
        if (a == null || b == null) {
            s.undo();
            helper.fail("(setup) could not create the builders");
            return;
        }
        s.guards.add(b.getId());
        CommandQueues.issue(s.level, s.owner, UnitAction.GUARD, a.getId(), new int[] { b.getId() },
            a.blockPosition(), BlockPos.ZERO, QueueMode.REPLACE);
        if (!GuardServerEvents.isGuarding(b.getId())) {
            s.undo();
            helper.fail("the guard order did not take");
            return;
        }
        boolean[] seenBuilding = { false };
        BotPlayer.Kit kit = BotPlayer.kitFor(Factions.VILLAGERS);
        Building barracks = kit == null ? null : kit.armyBuilding();
        helper.runAfterDelay(10, () -> {
            if (barracks == null || !s.order(barracks, a.getId())) {
                problems.add("A's build order (barracks " + barracks + ") was refused");
                finish(helper, s, problems);
                return;
            }
            // both on the new footprint, as the feature check had them (architect inside the barracks' site): A next
            // to its corner (it starts the ghost at once), B in the middle, its move goal idle
            a.teleportTo(s.bp.minCorner.getX() + 1.5, BuilderAssistGameTests.PAD_Y + 1, s.bp.minCorner.getZ() + 1.5);
            b.teleportTo(s.bp.centrePos.getX() + 0.5, BuilderAssistGameTests.PAD_Y + 1, s.bp.centrePos.getZ() + 0.5);
            ((com.solegendary.reignofnether.unit.interfaces.Unit) b).getMoveGoal().stopMoving();
            helper.runAfterDelay(10, () -> {   // t20
                if (!GuardServerEvents.isGuarding(b.getId()))
                    problems.add("t20: the guard was dropped after A's build order: " + where(b));
                else if (targetOf(b) != s.bp)
                    problems.add("t20: B guards but is not aimed at A's site: " + where(b));
                BlueprintGameTests.poll(helper, 0, 180,
                    () -> {
                        if (building(s.bp, b))
                            seenBuilding[0] = true;
                        return false;
                    },
                    () -> { },
                    () -> {   // t200
                        if (!GuardServerEvents.isGuarding(b.getId()))
                            problems.add("t200: B no longer guards A: " + where(b));
                        boolean nowBuilding = targetOf(b) == s.bp && building(s.bp, b);
                        if (!nowBuilding && !(s.bp.isBuilt && seenBuilding[0]))
                            problems.add("t200: B is not building A's site: " + where(b) + ", builders "
                                + s.bp.getBuilders().size() + ", built " + s.bp.isBuilt + ", seen building "
                                + seenBuilding[0]);
                        finish(helper, s, problems);
                    });
            });
        });
    }

    /**
     * AC-2, following the ward: B (Royal Architect) guards A; A builds site 1 and B helps; A is then sent to a second
     * site (a plain order) and B, still guarding, follows and builds there.
     */
    @GameTest(template = ARENA, timeoutTicks = 900, batch = BATCH + "_guard_next")
    public static void ac2_a_guard_follows_its_ward_to_the_next_site_and_helps_there(GameTestHelper helper) {
        Site s = new Site(helper, "guardnext");
        List<String> problems = new ArrayList<>();
        Mob a = s.builder(EntityRegistrar.VILLAGER_UNIT.get(), -2, 6);
        Mob b = s.builder(EntityRegistrar.ROYAL_ARCHITECT_UNIT.get(), 0, 7);
        if (a == null || b == null) {
            s.undo();
            helper.fail("(setup) could not create the builders");
            return;
        }
        s.guards.add(b.getId());
        CommandQueues.issue(s.level, s.owner, UnitAction.GUARD, a.getId(), new int[] { b.getId() },
            a.blockPosition(), BlockPos.ZERO, QueueMode.REPLACE);
        BuildingPlacement[] first = { null };
        helper.runAfterDelay(10, () -> {
            first[0] = place(s, Defences.VILLAGERS.raidTower(), new BlockPos(s.cx - 7, BuilderAssistGameTests.PAD_Y,
                s.cz - 1), a.getId());
            if (first[0] == null) {
                problems.add("(setup) the first site was refused");
                finish(helper, s, problems);
                return;
            }
            BlueprintGameTests.poll(helper, 0, 360,
                () -> building(first[0], a) && building(first[0], b),
                () -> {
                    // A's next order: a second site, as a plain (not queued) build order
                    s.bp = place(s, Defences.VILLAGERS.raidTower(), new BlockPos(s.cx + 4,
                        BuilderAssistGameTests.PAD_Y, s.cz - 1), a.getId());
                    if (s.bp == null) {
                        problems.add("(setup) the second site was refused");
                        drop(first[0]);
                        finish(helper, s, problems);
                        return;
                    }
                    BlueprintGameTests.poll(helper, 0, 400,
                        () -> GuardServerEvents.isGuarding(b.getId()) && building(s.bp, a) && building(s.bp, b),
                        () -> {
                            drop(first[0]);
                            finish(helper, s, problems);
                        },
                        () -> {
                            problems.add("B did not follow A to the second site: guarding "
                                + GuardServerEvents.isGuarding(b.getId()) + ", builders " + s.bp.getBuilders().size()
                                + "; A " + where(a) + "; B " + where(b));
                            drop(first[0]);
                            finish(helper, s, problems);
                        });
                },
                () -> {
                    problems.add("B never helped A on the first site: guarding "
                        + GuardServerEvents.isGuarding(b.getId()) + "; A " + where(a) + "; B " + where(b));
                    drop(first[0]);
                    finish(helper, s, problems);
                });
        });
    }
}
