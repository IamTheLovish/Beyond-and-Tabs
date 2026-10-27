package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.bot.BotPlayer;
import com.solegendary.reignofnether.building.AreaMexCircle;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.GhostHit;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.gametest.BuilderAssistGameTests.Site;
import com.solegendary.reignofnether.gametest.MexQueueGameTests.Scene;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitQueueSync;
import com.solegendary.reignofnether.unit.command.CommandQueues;
import com.solegendary.reignofnether.unit.command.QueueMode;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.solegendary.reignofnether.gametest.BuilderAssistGameTests.building;
import static com.solegendary.reignofnether.gametest.BuilderAssistGameTests.finish;
import static com.solegendary.reignofnether.gametest.BuilderAssistGameTests.targetOf;
import static com.solegendary.reignofnether.gametest.BuilderAssistGameTests.where;

/**
 * Tester reports of Oct 26:
 * <ul>
 *   <li>BUG 1 "this pathing doesn't make sense": an area-mex command queued its sites sorted by distance from the
 *       builders, which zig-zags across a ring of patches. Now (BAR's cmd_area_mex calculateCmdOrder) a greedy
 *       nearest-neighbour tour (AreaMexCircle.tour). AC-1 the queue - and the queue lines the server sends - follow
 *       the tour; AC-2 sites Shift-clicked one by one keep the player's order.</li>
 *   <li>BUG 2 "unable to assist a production in progress": the cursor ray goes through a half-built frame to the
 *       ground, so the picked block is not in the building and the right-click became a move. Now the client finds a
 *       friendly unfinished building by its box, like a ghost (GhostHit.pick, BlueprintClientEvents.ghostUnderCursor)
 *       and sends BUILD_REPAIR at its centre. AC-3 that click's order makes a second builder assist and the site rise
 *       faster; AC-4 a finished building is not a box target (unchanged).</li>
 *   <li>BAR parity: AC-5 a Shift area-mex tour starts where the builders' queues end (getAvgPositionOfValidBuilders
 *       with useQueueEnd), a plain one where they stand.</li>
 * </ul>
 * Orders go through the server paths the client packets reach (BuildingServerEvents.placeBuilding with
 * AreaMex.queueFor, CommandQueues.issue); queue lines are read from UnitQueueSync, what the client draws.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BuildOrderFixGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "build_order_fix";

    /** The x/z of the unit's queue-line entries, as the server sends them to the owner. */
    static List<int[]> lineXZ(String owner, int unitId) {
        List<int[]> out = new ArrayList<>();
        IntArrayList p = UnitQueueSync.buildPayloads(Set.of(owner)).get(owner);
        if (p == null)
            return out;
        int i = 0;
        while (i + 1 < p.size()) {
            int id = p.getInt(i++), n = p.getInt(i++);
            for (int k = 0; k < n; k++, i += UnitQueueSync.INTS_PER_ENTRY)
                if (id == unitId)
                    out.add(new int[] { p.getInt(i + 1), p.getInt(i + 3) });
        }
        return out;
    }

    /** The worker's build order: its current site, then the queued ones (no repeats). */
    static List<BuildingPlacement> buildOrder(Mob m) {
        List<BuildingPlacement> out = new ArrayList<>();
        var g = ((WorkerUnit) m).getBuildRepairGoal();
        if (g.getBuildingTarget() != null)
            out.add(g.getBuildingTarget());
        for (BuildingPlacement b : g.queuedBuildings)
            if (!out.contains(b))
                out.add(b);
        return out;
    }

    /** The patch centre (x, z) each site in {@code order} stands on, or a problem if one is not on a patch. */
    static List<BlockPos> patchesOf(List<BuildingPlacement> order, List<BlockPos> patches, List<String> problems) {
        List<BlockPos> out = new ArrayList<>();
        for (BuildingPlacement b : order) {
            BlockPos hit = null;
            for (BlockPos c : patches)
                if (b.minCorner.getX() <= c.getX() && b.maxCorner.getX() >= c.getX()
                        && b.minCorner.getZ() <= c.getZ() && b.maxCorner.getZ() >= c.getZ())
                    hit = c;
            if (hit == null)
                problems.add("site " + b.originPos.toShortString() + " is on no patch");
            out.add(hit);
        }
        return out;
    }

    /** Checks the worker's queue and its queue lines follow {@code expected} (patch centres). */
    static void checkQueue(Scene s, Mob w, List<BlockPos> expected, String what) {
        List<BuildingPlacement> order = buildOrder(w);
        List<BlockPos> got = patchesOf(order, s.patches, s.problems);
        if (!got.equals(expected))
            s.problems.add(what + ": the worker's queue is " + got + ", expected " + expected);
        List<int[]> lines = lineXZ(s.owner, w.getId());
        if (lines.size() != order.size()) {
            s.problems.add(what + ": " + lines.size() + " queue-line entries for " + order.size() + " sites");
            return;
        }
        for (int i = 0; i < order.size(); i++) {
            BlockPos c = order.get(i).centrePos;
            if (lines.get(i)[0] != c.getX() || lines.get(i)[1] != c.getZ())
                s.problems.add(what + ": queue line " + i + " goes to " + lines.get(i)[0] + "," + lines.get(i)[1]
                    + ", the site is at " + c.getX() + "," + c.getZ());
        }
    }

    /** A greedy nearest-neighbour tour written out here, independent of AreaMexCircle.tour. */
    static List<BlockPos> greedy(List<BlockPos> sites, double x, double z) {
        List<BlockPos> left = new ArrayList<>(sites), out = new ArrayList<>();
        while (!left.isEmpty()) {
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            for (BlockPos p : left) {
                double d = Math.hypot(p.getX() - x, p.getZ() - z);
                if (d < bestD) {
                    bestD = d;
                    best = p;
                }
            }
            left.remove(best);
            out.add(best);
            x = best.getX();
            z = best.getZ();
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------ BUG 1

    /**
     * AC-1: patches on both sides of the builder (the tester's ring around the capitol, flattened to a line): the
     * area-mex order is the greedy tour - same length as one computed here, no longer than the zig-zag click order,
     * shorter than the old distance sort - and the worker's queue and queue lines follow it.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = BATCH)
    public static void ac1_area_mex_queue_is_a_nearest_neighbour_tour(GameTestHelper helper) {
        Scene s = new Scene(helper, "bof1", 150);
        Mob w = s.worker(0, 4);
        if (w == null) {
            s.setupFail("no worker");
            return;
        }
        // the zig-zag the tester saw: near-left, near-right, far-left, far-right
        List<BlockPos> zigzag = List.of(s.patch(-7, 0), s.patch(8, 0), s.patch(-15, 0), s.patch(16, 0));
        double wx = w.getX(), wz = w.getZ();
        List<BlockPos> tour = AreaMexCircle.tour(zigzag, wx, wz);
        double tourLen = AreaMexCircle.pathLength(tour, wx, wz);
        double greedyLen = AreaMexCircle.pathLength(greedy(zigzag, wx, wz), wx, wz);
        double clickLen = AreaMexCircle.pathLength(zigzag, wx, wz);
        double oldLen = AreaMexCircle.pathLength(AreaMexCircle.nearestFirst(zigzag, wx, wz), wx, wz);
        if (Math.abs(tourLen - greedyLen) > 1e-6)
            s.problems.add("the tour is " + tourLen + " long, the greedy tour " + greedyLen);
        if (tourLen > clickLen + 1e-6)
            s.problems.add("the tour (" + tourLen + ") is longer than the click order (" + clickLen + ")");
        if (!(tourLen < oldLen))
            s.problems.add("the tour (" + tourLen + ") is not shorter than the old distance sort (" + oldLen + ")");
        // the client sends the tour, first replacing, the rest queued (PatchQuickBuildClientEvents.queueExtractors)
        int accepted = s.order(tour, false);
        if (accepted != tour.size())
            s.problems.add("only " + accepted + " of " + tour.size() + " orders accepted: " + s.refusals);
        else
            checkQueue(s, w, tour, "area mex");
        s.finish();
    }

    /** AC-2: sites Shift-clicked one at a time, in a zig-zag, stay in the player's order (queue and lines). */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = BATCH)
    public static void ac2_shift_clicked_sites_keep_their_order(GameTestHelper helper) {
        Scene s = new Scene(helper, "bof2", 158);
        Mob w = s.worker(0, 4);
        if (w == null) {
            s.setupFail("no worker");
            return;
        }
        List<BlockPos> clicks = List.of(s.patch(16, 0), s.patch(-7, 0), s.patch(8, 0), s.patch(-15, 0));
        int accepted = 0;
        for (BlockPos c : clicks)   // one click each: AreaMex.queueFor(0, true)
            accepted += s.order(List.of(c), true);
        if (accepted != clicks.size())
            s.problems.add("only " + accepted + " of " + clicks.size() + " clicks accepted: " + s.refusals);
        else
            checkQueue(s, w, clicks, "Shift-clicks");
        s.finish();
    }

    // ------------------------------------------------------------------------------------------------ BUG 2

    /**
     * AC-3: A builds a wind generator alone; while it is half built, the right-click on it is modelled the way the
     * client sees it - a camera ray through the empty upper frame to the pad behind (the picked block is NOT in the
     * building: the bug) - and GhostHit.pick must find the site by its box. That click's order (BUILD_REPAIR at the
     * site's centre, CommandQueues.issue) sends B, B builds with A, and the site rises faster than with A alone.
     */
    @GameTest(template = ARENA, timeoutTicks = 800, batch = BATCH)
    public static void ac3_right_click_on_a_half_built_building_assists(GameTestHelper helper) {
        Site s = new Site(helper, "bofhalf");
        List<String> problems = new ArrayList<>();
        BotPlayer.Kit kit = BotPlayer.kitFor(Factions.VILLAGERS);
        Building wind = kit == null ? null : kit.wind();
        Mob a = s.builder(EntityRegistrar.VILLAGER_UNIT.get(), -6, 0);
        Mob b = s.builder(EntityRegistrar.VILLAGER_UNIT.get(), 8, 8);
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
        BlueprintGameTests.poll(helper, 0, 300,
            () -> s.registered() && building(s.bp, a) && (s.bp.getBlocksPlaced() > 0 || s.bp.buildProgress > 0),
            () -> {
                float p0 = s.bp.buildProgress;
                helper.runAfterDelay(20, () -> {
                    BuildingPlacement bp = s.bp;
                    if (bp.isBuilt || bp.getBuilders().size() != 1) {
                        problems.add("(setup) A was not building alone and unfinished: builders "
                            + bp.getBuilders().size() + ", built " + bp.isBuilt);
                        finish(helper, s, problems);
                        return;
                    }
                    float alone = bp.buildProgress - p0;
                    // the click: from high in front (-z) of the site, through its box, onto the pad 2 blocks behind it
                    double tx = bp.centrePos.getX() + 0.5, ty = bp.minCorner.getY(), tz = bp.maxCorner.getZ() + 2.5;
                    double ox = tx, oy = bp.maxCorner.getY() + 30, oz = bp.minCorner.getZ() - 30;
                    double len = Math.sqrt((tx - ox) * (tx - ox) + (ty - oy) * (ty - oy) + (tz - oz) * (tz - oz));
                    double dx = (tx - ox) / len, dy = (ty - oy) / len, dz = (tz - oz) / len;
                    BlockPos picked = new BlockPos((int) Math.floor(tx), (int) ty - 1, (int) Math.floor(tz));
                    if (bp.isPosInsideBuilding(picked))
                        problems.add("(setup) the ray's picked block " + picked.toShortString() + " is in the building");
                    if (!GhostHit.assistTarget(bp.isBuilt, true))
                        problems.add("a half-built friendly building is not a box click target");
                    GhostHit.Box box = new GhostHit.Box(bp.minCorner.getX(), bp.minCorner.getY(), bp.minCorner.getZ(),
                        bp.maxCorner.getX(), bp.maxCorner.getY(), bp.maxCorner.getZ());
                    GhostHit.Box elsewhere = new GhostHit.Box(box.minX() + 40, box.minY(), box.minZ(),
                        box.maxX() + 40, box.maxY(), box.maxZ());
                    int hit = GhostHit.pick(ox, oy, oz, dx, dy, dz, len,
                        new int[] { picked.getX(), picked.getY(), picked.getZ() }, List.of(elsewhere, box));
                    if (hit != 1) {
                        problems.add("the ray through the half-built frame did not pick it: " + hit);
                        finish(helper, s, problems);
                        return;
                    }
                    CommandQueues.issue(s.level, s.owner, UnitAction.BUILD_REPAIR, -1, new int[] { b.getId() },
                        bp.centrePos, BlockPos.ZERO, QueueMode.REPLACE);
                    if (targetOf(b) != bp) {
                        problems.add("B was not sent to the half-built site: " + where(b));
                        finish(helper, s, problems);
                        return;
                    }
                    BlueprintGameTests.poll(helper, 0, 300,
                        () -> building(bp, a) && building(bp, b),
                        () -> {
                            float q0 = bp.buildProgress;
                            helper.runAfterDelay(20, () -> {
                                float both = bp.buildProgress - q0;
                                if (!bp.isBuilt && bp.getBuilders().size() < 2)
                                    problems.add("B stopped building during the second window: " + where(b));
                                else if (!(both > alone))
                                    problems.add("two builders were not faster than one: alone " + alone
                                        + " per 20 ticks, both " + both + " (built " + bp.isBuilt + ")");
                                finish(helper, s, problems);
                            });
                        },
                        () -> {
                            problems.add("B never joined the half-built site: builders " + bp.getBuilders().size()
                                + "; A " + where(a) + "; B " + where(b));
                            finish(helper, s, problems);
                        });
                });
            },
            () -> {
                problems.add("(setup) A never started building: registered " + s.registered() + "; " + where(a));
                finish(helper, s, problems);
            });
    }

    /**
     * AC-4: a finished, undamaged building is not a box click target (GhostHit.assistTarget), and is not buildable,
     * so the click keeps its old meaning (a move; a damaged one is repaired through the picked-block branch).
     */
    @GameTest(template = ARENA, timeoutTicks = 1400, batch = BATCH)
    public static void ac4_finished_building_click_unchanged(GameTestHelper helper) {
        Site s = new Site(helper, "boffin");
        List<String> problems = new ArrayList<>();
        BotPlayer.Kit kit = BotPlayer.kitFor(Factions.VILLAGERS);
        Building wind = kit == null ? null : kit.wind();
        Mob a = s.builder(EntityRegistrar.VILLAGER_UNIT.get(), -6, 0);
        Mob b = s.builder(EntityRegistrar.VILLAGER_UNIT.get(), 6, 0);
        if (wind == null || a == null || b == null) {
            s.undo();
            helper.fail("(setup) need a wind generator and two builders; wind = " + wind);
            return;
        }
        // two workers raise it to the end
        if (!s.order(wind, a.getId(), b.getId())) {
            s.undo();
            helper.fail("(setup) the building was refused");
            return;
        }
        BlueprintGameTests.poll(helper, 0, 1300,
            () -> s.bp.isBuilt,
            () -> {
                if (GhostHit.assistTarget(s.bp.isBuilt, true))
                    problems.add("a finished building became a box click target");
                if (s.bp.getHealth() >= s.bp.getMaxHealth() && BuildingUtils.isBuildingBuildable(false, s.bp))
                    problems.add("a finished, undamaged building is buildable (the click would not be a move)");
                finish(helper, s, problems);
            },
            () -> {
                problems.add("(setup) the building never finished: progress " + s.bp.buildProgress);
                finish(helper, s, problems);
            });
    }

    // ------------------------------------------------------------------------------------------------ BAR parity

    /**
     * AC-5 (BAR getAvgPositionOfValidBuilders, useQueueEnd = shift): the worker stands west, its queue ends east. A
     * Shift area mex starts its tour at the queue end (the line the client has), so the east patch comes first and
     * the queue is [old site, east, west]; a plain one would start where the worker stands (west first).
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = BATCH)
    public static void ac5_shift_area_mex_tour_starts_at_the_queue_end(GameTestHelper helper) {
        Scene s = new Scene(helper, "bof5", 166);
        Mob w = s.worker(-2, 4);
        if (w == null) {
            s.setupFail("no worker");
            return;
        }
        BlockPos first = s.patch(16, 0);
        BlockPos west = s.patch(-6, 0), east = s.patch(9, 0);
        if (s.order(List.of(first), false) != 1) {
            s.setupFail("the first order was refused: " + s.refusals);
            return;
        }
        List<int[]> lines = lineXZ(s.owner, w.getId());
        int[] end = lines.isEmpty() ? null : lines.get(lines.size() - 1);
        List<double[]> here = List.<double[]>of(new double[] { w.getX(), w.getZ() });
        List<int[]> ends = new ArrayList<>();
        ends.add(end);
        double[] plain = AreaMexCircle.tourStart(here, ends, false);
        double[] shift = AreaMexCircle.tourStart(here, ends, true);
        List<BlockPos> plainTour = AreaMexCircle.tour(List.of(west, east), plain[0], plain[1]);
        List<BlockPos> shiftTour = AreaMexCircle.tour(List.of(west, east), shift[0], shift[1]);
        if (end == null)
            s.problems.add("the worker has no queue line to start from");
        if (!plainTour.equals(List.of(west, east)))
            s.problems.add("a plain area mex did not start where the worker stands: " + plainTour);
        if (!shiftTour.equals(List.of(east, west)))
            s.problems.add("a Shift area mex did not start at the queue end " + (end == null ? "-" : end[0] + "," + end[1])
                + ": " + shiftTour);
        if (s.problems.isEmpty()) {
            int accepted = s.order(shiftTour, true);
            if (accepted != shiftTour.size())
                s.problems.add("only " + accepted + " Shift orders accepted: " + s.refusals);
            else
                checkQueue(s, w, List.of(first, east, west), "Shift area mex");
        }
        s.finish();
    }
}
