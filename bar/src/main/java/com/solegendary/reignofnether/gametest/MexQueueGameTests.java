package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.AreaMex;
import com.solegendary.reignofnether.building.Blueprints;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingValidators;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.MetalPatches;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.goals.BuildRepairGoal;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Queued extractor orders (live bug, pack bbbdac2: four workers, five Shift+right-clicked patches - they walked to the
 * last patch and stood there, nothing was built). Orders go through the real placement path with the queue flag the
 * client sends for each target (AreaMex.queueFor: Shift appends every target, a plain order replaces with its first),
 * the workers walk and build on real ticks.
 * <ul>
 *   <li>AC-1: several workers given 3+ queued extractor orders on separate patches build ALL of them;</li>
 *   <li>AC-2: a second Shift batch appends to the first instead of replacing it - every patch of both gets built;</li>
 *   <li>AC-3: no extractor starts before a worker reaches it;</li>
 *   <li>AC-4: no patch ever holds two extractors (or an extractor and a pending blueprint), even when it is ordered twice.</li>
 * </ul>
 * One stone sky pad per test, each at its own height (tests of one batch stand side by side), a unique owner, all
 * undone in the callback.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class MexQueueGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "mex_queue";
    static final int PAD_R = 22, PAD_H = 12;
    static final Building EXTRACTOR = Buildings.METAL_EXTRACTOR_VILLAGERS;   // the workers are villagers (the Stormcallers one refuses a flat stone pad: ground_not_flat)

    static final class Scene {
        final GameTestHelper helper;
        final ServerLevel level;
        final String owner;
        final int cx, cz, padY;
        final List<Entity> made = new ArrayList<>();
        final List<Resources> pools = new ArrayList<>();
        final List<Mob> workers = new ArrayList<>();
        final List<BlockPos> patches = new ArrayList<>();
        final List<BuildingPlacement> sites = new ArrayList<>();
        final List<String> problems = new ArrayList<>();
        final List<String> refusals = new ArrayList<>();   // orders the server refused, with the validator's reason
        final IdentityHashMap<BuildingPlacement, Boolean> seenStarted = new IdentityHashMap<>();
        /** More to undo with the scene (a test's own bigger pad): run even when finish() fails, which throws. */
        final List<Runnable> undos = new ArrayList<>();
        boolean done = false;

        Scene(GameTestHelper helper, String tag, int padY) {
            this.helper = helper;
            this.padY = padY;
            level = helper.getLevel();
            owner = "gametest_mexq_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
            BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
            cx = base.getX();
            cz = base.getZ();
            CombatEngineerGameTests.skyPad(level, cx, padY, cz, PAD_R, PAD_H, false);
            Resources pool = new Resources(owner, 0, 50000, 50000);
            ResourcesServerEvents.resourcesList.add(pool);
            pools.add(pool);
        }

        /** A plus-shaped metal patch set into the pad; returns its centre (the ground block). */
        BlockPos patch(int dx, int dz) {
            BlockPos c = new BlockPos(cx + dx, padY, cz + dz);
            for (int[] d : new int[][]{ {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1} })
                level.setBlock(c.offset(d[0], 0, d[1]), MetalPatches.PATCH_BLOCK.defaultBlockState(), 2);
            patches.add(c);
            return c;
        }

        Mob worker(int dx, int dz) {
            Mob m = CombatEngineerGameTests.spawn(level, EntityRegistrar.VILLAGER_UNIT.get(), owner,
                new BlockPos(cx + dx, padY + 1, cz + dz), made);
            if (m != null)
                workers.add(m);
            return m;
        }

        int[] ids() {
            int[] ids = new int[workers.size()];
            for (int i = 0; i < ids.length; i++)
                ids[i] = workers.get(i).getId();
            return ids;
        }

        /**
         * One click's worth of extractor orders on all the workers, exactly as the client sends them
         * (PatchQuickBuildClientEvents.queueExtractors): target i goes with queue = AreaMex.queueFor(i, append).
         * Returns how many the server accepted.
         */
        int order(List<BlockPos> centres, boolean append) {
            int accepted = 0;
            for (int i = 0; i < centres.size(); i++) {
                BuildingPlacement bp = BuildingServerEvents.placeBuilding(EXTRACTOR, centres.get(i).offset(-2, 0, -2),
                    Rotation.NONE, owner, ids(), AreaMex.queueFor(i, append), false, false, true);
                if (bp != null) {
                    sites.add(bp);
                    accepted++;
                } else {
                    refusals.add(centres.get(i).toShortString() + ": " + BuildingValidators.getPlacementValidityError(level,
                        EXTRACTOR, centres.get(i).offset(-2, 0, -2), owner, Rotation.NONE, false, false, true));
                }
            }
            return accepted;
        }

        void undo() {
            for (BuildingPlacement bp : sites) {
                Blueprints.cancel(bp, null);
                if (BuildingServerEvents.getBuildings().remove(bp))
                    bp.forceChunk(false);
            }
            CombatEngineerGameTests.cleanUp(made, pools, List.of(owner));
            CombatEngineerGameTests.skyPad(level, cx, padY, cz, PAD_R, PAD_H, true);
            undos.forEach(Runnable::run);
        }

        void finish() {
            if (done)
                return;
            done = true;
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
            done = true;
            undo();
            helper.fail("(setup) " + why);
        }

        // ---------------------------------------------------------------------------------------- what we watch

        static boolean started(BuildingPlacement bp) {
            return BuildingServerEvents.getBuildings().contains(bp) || bp.buildProgress > 0 || bp.getBlocksPlaced() > 0;
        }

        boolean workerAt(BuildingPlacement bp) {
            for (Mob m : workers) {
                BuildRepairGoal g = ((WorkerUnit) m).getBuildRepairGoal();
                if (g != null && g.getBuildingTarget() == bp && g.isBuilding())
                    return true;
                if (bp.getBuilders().contains((WorkerUnit) m))
                    return true;
                if (bp.isPosInsideBuilding(m.getOnPos(), BuildRepairGoal.FALLBACK_BUILD_RANGE))
                    return true;
            }
            return false;
        }

        /** Extractors standing or pending (this owner's) whose footprint covers {@code centre}. */
        int extractorsOn(BlockPos centre) {
            int n = 0;
            List<BuildingPlacement> all = new ArrayList<>(BuildingServerEvents.getBuildings());
            all.addAll(Blueprints.all());
            for (BuildingPlacement b : all)
                if (owner.equals(b.ownerName) && !b.isDestroyedServerside && b.minCorner != null && b.maxCorner != null
                        && b.minCorner.getX() <= centre.getX() && b.maxCorner.getX() >= centre.getX()
                        && b.minCorner.getZ() <= centre.getZ() && b.maxCorner.getZ() >= centre.getZ())
                    n++;
            return n;
        }

        String state() {
            StringBuilder sb = new StringBuilder();
            if (!refusals.isEmpty())
                sb.append("refused ").append(refusals).append("; ");
            for (int i = 0; i < sites.size(); i++) {
                BuildingPlacement bp = sites.get(i);
                sb.append("site").append(i).append('@').append(bp.centrePos.toShortString())
                    .append(Blueprints.isPending(bp) ? " pending" : "")
                    .append(BuildingServerEvents.getBuildings().contains(bp) ? " registered" : "")
                    .append(bp.isBuilt ? " built" : "")
                    .append(" blocks ").append(bp.getBlocksPlaced()).append('/').append(bp.getBlocksTotal()).append("; ");
            }
            for (Mob m : workers) {
                BuildRepairGoal g = ((WorkerUnit) m).getBuildRepairGoal();
                BuildingPlacement t = g == null ? null : g.getBuildingTarget();
                sb.append("worker@").append(m.blockPosition().toShortString())
                    .append(" target ").append(t == null ? "none" : "site" + sites.indexOf(t))
                    .append(" queue ").append(g == null ? -1 : g.queuedBuildings.size()).append("; ");
            }
            return sb.toString();
        }

        /**
         * Every 5 ticks until every site is built (or {@code max} ticks): a site that starts with no worker at it fails
         * AC-3, a patch under two extractors fails AC-4. {@code midway} runs once at tick {@code midwayAt} (or never).
         */
        void watch(int max, int midwayAt, Runnable midway) {
            int[] tick = { 0 };
            Runnable[] step = new Runnable[1];
            step[0] = () -> helper.runAfterDelay(5, () -> {
                if (done)
                    return;
                tick[0] += 5;
                for (BuildingPlacement bp : sites)
                    if (!seenStarted.containsKey(bp) && started(bp)) {
                        seenStarted.put(bp, Boolean.TRUE);
                        if (!workerAt(bp))
                            problems.add("t" + tick[0] + ": site" + sites.indexOf(bp) + " started with no worker at it: " + state());
                    }
                for (BlockPos c : patches)
                    if (extractorsOn(c) > 1)
                        problems.add("t" + tick[0] + ": patch " + c.toShortString() + " holds " + extractorsOn(c) + " extractors");
                if (!problems.isEmpty()) {
                    finish();
                    return;
                }
                if (midway != null && tick[0] == midwayAt) {
                    midway.run();
                    if (done)
                        return;
                }
                boolean allBuilt = !sites.isEmpty() && (midway == null || tick[0] >= midwayAt);
                for (BuildingPlacement bp : sites)
                    allBuilt &= bp.isBuilt;
                if (allBuilt) {
                    finish();
                    return;
                }
                if (tick[0] >= max) {
                    int built = 0;
                    for (BuildingPlacement bp : sites)
                        if (bp.isBuilt)
                            built++;
                    problems.add("t" + tick[0] + ": " + built + "/" + sites.size() + " extractors built: " + state());
                    finish();
                    return;
                }
                step[0].run();
            });
            step[0].run();
        }
    }

    /** Four workers at the west edge and six patches in two rows of three, 12 blocks apart. */
    static Scene scene(GameTestHelper helper, String tag, int padY) {
        Scene s = new Scene(helper, tag, padY);
        for (int dz = -2; dz <= 1; dz++)
            if (s.worker(-18, dz * 2) == null) {
                s.setupFail("could not create the workers");
                return null;
            }
        for (int dz : new int[]{ -9, 9 })
            for (int dx : new int[]{ -6, 6, 18 })
                s.patch(dx, dz);
        return s;
    }

    // ------------------------------------------------------------------------------------------------ AC-1

    /** AC-1: four workers, four patches Shift+right-clicked one after another (one order per click): all four rise. */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 4000)
    public static void ac1_several_workers_build_every_queued_extractor(GameTestHelper helper) {
        Scene s = scene(helper, "ac1", 200);
        if (s == null)
            return;
        int accepted = 0;
        for (int i = 0; i < 4; i++)
            accepted += s.order(List.of(s.patches.get(i)), true);
        if (accepted != 4) {
            s.setupFail("only " + accepted + "/4 extractor orders were accepted: " + s.state());
            return;
        }
        s.watch(3900, -1, null);
    }

    // ------------------------------------------------------------------------------------------------ AC-2

    /**
     * AC-2: a Shift batch of two patches, then a second Shift batch of two more 40 ticks later (the workers already
     * walking): the second appends - all four rise, the first batch's sites too.
     */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 4000)
    public static void ac2_a_second_shift_batch_appends_to_the_first(GameTestHelper helper) {
        Scene s = scene(helper, "ac2", 216);
        if (s == null)
            return;
        if (s.order(List.of(s.patches.get(0), s.patches.get(1)), true) != 2) {
            s.setupFail("the first batch was refused: " + s.state());
            return;
        }
        s.watch(3900, 40, () -> {
            List<BuildingPlacement> first = new ArrayList<>(s.sites);
            if (s.order(List.of(s.patches.get(3), s.patches.get(4)), true) != 2) {
                s.problems.add("the second batch was refused: " + s.state());
                s.finish();
                return;
            }
            for (BuildingPlacement bp : first)
                if (!bp.isBuilt && !Blueprints.isPending(bp) && !BuildingServerEvents.getBuildings().contains(bp))
                    s.problems.add("the second batch dropped first-batch site" + s.sites.indexOf(bp) + ": " + s.state());
            for (Mob m : s.workers) {
                BuildRepairGoal g = ((WorkerUnit) m).getBuildRepairGoal();
                for (BuildingPlacement bp : first)
                    if (!bp.isBuilt && g.getBuildingTarget() != bp && !g.queuedBuildings.contains(bp))
                        s.problems.add("a worker lost first-batch site" + s.sites.indexOf(bp) + " from its orders: " + s.state());
            }
            if (!s.problems.isEmpty())
                s.finish();
        });
    }

    // ------------------------------------------------------------------------------------------------ AC-3

    /** AC-3: three queued extractors 24-36 blocks from the workers: none starts before a worker is at it (all rise). */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 4000)
    public static void ac3_no_queued_extractor_starts_before_a_worker_arrives(GameTestHelper helper) {
        Scene s = scene(helper, "ac3", 232);
        if (s == null)
            return;
        // the far column first, then back: the workers must walk past un-started sites
        if (s.order(List.of(s.patches.get(2), s.patches.get(5), s.patches.get(1)), true) != 3) {
            s.setupFail("the orders were refused: " + s.state());
            return;
        }
        for (BuildingPlacement bp : s.sites)
            if (Scene.started(bp))
                s.problems.add("site" + s.sites.indexOf(bp) + " started at order time");
        s.watch(3900, -1, null);
    }

    // ------------------------------------------------------------------------------------------------ AC-4

    /**
     * AC-4: one patch ordered in two batches (and twice in one), a plain order and a Shift one: it only ever holds one
     * extractor, and every distinct patch still gets its own.
     */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 4000)
    public static void ac4_no_patch_gets_two_extractors(GameTestHelper helper) {
        Scene s = scene(helper, "ac4", 248);
        if (s == null)
            return;
        BlockPos a = s.patches.get(0), b = s.patches.get(1), c = s.patches.get(3);
        int first = s.order(List.of(a, b, a), false);
        int second = s.order(List.of(b, c), true);
        if (first != 2 || second != 1) {
            s.problems.add("a patch ordered twice must be accepted once: first batch " + first + "/2, second " + second
                + "/1: " + s.state());
            s.finish();
            return;
        }
        s.watch(3900, -1, null);
    }

    // ------------------------------------------------------------------------------------------------ AC-5

    /**
     * AC-5 (user: "builders have 5x their max queue for blueprints"): one builder takes 5x the old cap of Shift-queued
     * blueprints (BuildRepairGoal.OLD_MAX_QUEUE 32 -> MAX_QUEUE 160), keeps every one of them in order, refuses the
     * next, and builds them in order: the first three rise one after another while the rest wait, still queued in
     * order. (Raising all 160 would take one worker most of an hour of game time.) Wind generators on a 13x13 grid,
     * walked in a serpentine so each next site is beside the last.
     */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 4000)
    public static void ac5_one_builder_keeps_5x_the_old_queue_and_builds_it_in_order(GameTestHelper helper) {
        final int padY = 264, step = 7, n = 13, half = (n - 1) * step / 2;
        Scene s = new Scene(helper, "ac5", padY);
        CombatEngineerGameTests.skyPad(s.level, s.cx, padY, s.cz, half + 6, PAD_H, false);
        Mob worker = s.worker(-half - 4, -half);
        Runnable undoBig = () -> CombatEngineerGameTests.skyPad(s.level, s.cx, padY, s.cz, half + 6, PAD_H, true);
        // s.finish() throws when the test fails, so an undoBig.run() after it never ran: the radius-48 stone pad stayed at
        // y 264 over the next batches' arenas (CI run 40651908207: scar_lands_on_the_real_top_face found it as the ground)
        s.undos.add(undoBig);
        if (worker == null) {
            s.setupFail("could not create the worker");
            undoBig.run();
            return;
        }
        Building wind = Buildings.WIND_GENERATOR_VILLAGERS;
        int want = BuildRepairGoal.MAX_QUEUE;
        if (want != 5 * BuildRepairGoal.OLD_MAX_QUEUE) {
            s.problems.add("the queue cap is " + want + ", want 5 x " + BuildRepairGoal.OLD_MAX_QUEUE);
            s.finish();
            undoBig.run();
            return;
        }
        List<BlockPos> origins = new ArrayList<>();
        for (int row = 0; row < n; row++)
            for (int k = 0; k < n; k++) {
                int col = row % 2 == 0 ? k : n - 1 - k;
                origins.add(new BlockPos(s.cx - half + col * step, padY, s.cz - half + row * step));
            }
        int[] ids = { worker.getId() };
        for (int i = 0; i < want; i++) {
            BuildingPlacement bp = BuildingServerEvents.placeBuilding(wind, origins.get(i), Rotation.NONE, s.owner, ids,
                true, false, false, true);
            if (bp == null) {
                s.problems.add("queued blueprint " + (i + 1) + "/" + want + " was refused at " + origins.get(i).toShortString());
                s.finish();
                undoBig.run();
                return;
            }
            s.sites.add(bp);
        }
        BuildingPlacement over = BuildingServerEvents.placeBuilding(wind, origins.get(want), Rotation.NONE, s.owner, ids,
            true, false, false, true);
        if (over != null) {
            s.sites.add(over);
            s.problems.add("blueprint " + (want + 1) + " was accepted past the cap of " + want);
        }
        BuildRepairGoal g = ((WorkerUnit) worker).getBuildRepairGoal();
        if (!g.queuedBuildings.equals(s.sites.subList(0, want)))
            s.problems.add("the builder's queue is not the " + want + " orders in order (holds " + g.queuedBuildings.size() + ")");
        if (!s.problems.isEmpty()) {
            s.finish();
            undoBig.run();
            return;
        }
        final int buildFirst = 3;
        int[] tick = { 0 };
        Runnable[] stepper = new Runnable[1];
        stepper[0] = () -> helper.runAfterDelay(10, () -> {
            if (s.done)
                return;
            tick[0] += 10;
            // in order: no site starts while an earlier one is unbuilt, and the queue keeps the rest in order
            int built = 0;
            for (int i = 0; i < want; i++) {
                BuildingPlacement bp = s.sites.get(i);
                if (bp.isBuilt) {
                    if (built != i)
                        s.problems.add("t" + tick[0] + ": site " + i + " built before site " + built);
                    built++;
                } else if (Scene.started(bp) && built != i)
                    s.problems.add("t" + tick[0] + ": site " + i + " started while site " + built + " is unbuilt");
            }
            if (s.problems.isEmpty() && Blueprints.pendingOf(s.owner).size() < want - built - 1)
                s.problems.add("t" + tick[0] + ": queued blueprints were dropped: " + Blueprints.pendingOf(s.owner).size()
                    + " pending with " + built + " built");
            if (!s.problems.isEmpty() || built >= buildFirst) {
                if (s.problems.isEmpty() && !g.queuedBuildings.equals(s.sites.subList(built, want)))
                    s.problems.add("after " + built + " built the queue is not the remaining " + (want - built)
                        + " in order (holds " + g.queuedBuildings.size() + ")");
                s.finish();
                undoBig.run();
                return;
            }
            if (tick[0] >= 3800) {
                s.problems.add("t" + tick[0] + ": only " + built + "/" + buildFirst + " built; worker at "
                    + worker.blockPosition().toShortString() + ", target " + s.sites.indexOf(g.getBuildingTarget()));
                s.finish();
                undoBig.run();
                return;
            }
            stepper[0].run();
        });
        stepper[0].run();
    }

    // ------------------------------------------------------------------------------------------------ AC-6

    /**
     * AC-6 (live: four workers ordered to a factory ghost 20 blocks away never moved - one on the roof of the player's
     * extractor, three on beach sand one block below a grass ledge): a worker standing on top of a just-built
     * extractor and one on the low side of a 1-block step both reach an extractor site 20 blocks away on the ledge and
     * start it within 30 s.
     */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 1000)
    public static void ac6_builders_on_a_roof_and_below_a_step_reach_a_site_20_blocks_away(GameTestHelper helper) {
        Scene s = new Scene(helper, "ac6", 280);
        int y = s.padY;
        // the ledge: the east half of the pad one block higher (the beach is the west half)
        for (int dx = 0; dx <= PAD_R; dx++)
            for (int dz = -PAD_R; dz <= PAD_R; dz++)
                s.level.setBlock(new BlockPos(s.cx + dx, y + 1, s.cz + dz),
                    net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
        // the just-built building on the beach, its blocks all standing
        BuildingPlacement roof = BuildingServerEvents.placeBuilding(EXTRACTOR, new BlockPos(s.cx - 20, y, s.cz - 2),
            Rotation.NONE, s.owner, new int[0], false, false, true, true);
        if (roof == null) {
            s.setupFail("the built extractor was refused");
            return;
        }
        s.sites.add(roof);
        for (var b : roof.getBlocks())
            if (!b.getBlockState().isAir())
                s.level.setBlock(b.getBlockPos(), b.getBlockState(), 2);
        roof.initPlacedBlocks();
        int roofTop = roof.maxCorner.getY();
        Mob onRoof = s.worker(-18, 0);
        Mob belowStep = s.worker(-1, -12);   // on the beach against the step; site 1 is 20 blocks away up on the ledge
        Mob onLedge = s.worker(17, 16);      // up on the ledge; site 2 is 20 blocks away down on the beach, against the step
        if (onRoof == null || belowStep == null || onLedge == null) {
            s.setupFail("could not create the workers");
            return;
        }
        onRoof.teleportTo(s.cx - 18 + 0.5, roofTop + 1, s.cz + 0.5);
        onLedge.teleportTo(s.cx + 17 + 0.5, y + 2, s.cz + 16 + 0.5);
        // one extractor site on the ledge 20 blocks from each worker (ledge ground at y + 1), its west edge on the
        // ledge edge: the side the workers come from is the beach, a block lower (the live factory ghost)
        List<Mob> pair = List.of(onRoof, belowStep, onLedge);
        BlockPos[] centres = { new BlockPos(s.cx + 2, y + 1, s.cz), new BlockPos(s.cx + 2, y + 1, s.cz + 8),
            new BlockPos(s.cx - 3, y, s.cz + 16) };   // on the beach, east edge against the ledge: its near side is solid ground
        BuildingPlacement[] site = new BuildingPlacement[3];
        for (int i = 0; i < 3; i++) {
            for (int[] d : new int[][]{ {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1} })
                s.level.setBlock(centres[i].offset(d[0], 0, d[1]), MetalPatches.PATCH_BLOCK.defaultBlockState(), 2);
            s.patches.add(centres[i]);
            BlockPos origin = centres[i].offset(-2, 0, -2);
            site[i] = BuildingServerEvents.placeBuilding(EXTRACTOR, origin, Rotation.NONE, s.owner,
                new int[]{ pair.get(i).getId() }, false, false, false, true);
            if (site[i] == null) {
                s.setupFail("site " + i + " was refused: " + BuildingValidators.getPlacementValidityError(s.level,
                    EXTRACTOR, origin, s.owner, Rotation.NONE, false, false, true));
                return;
            }
            s.sites.add(site[i]);
        }
        int[] tick = { 0 };
        Runnable[] stepper = new Runnable[1];
        stepper[0] = () -> helper.runAfterDelay(10, () -> {
            if (s.done)
                return;
            tick[0] += 10;
            boolean all = Scene.started(site[0]) && Scene.started(site[1]) && Scene.started(site[2]);
            if (all) {
                s.finish();
                return;
            }
            if (tick[0] >= 600) {
                for (int i = 0; i < 3; i++)
                    if (!Scene.started(site[i])) {
                        Mob m = pair.get(i);
                        BuildRepairGoal g = ((WorkerUnit) m).getBuildRepairGoal();
                        s.problems.add((i == 0 ? "the roof worker" : i == 1 ? "the worker below the step" : "the worker above the step") + " did not start its site in 30 s: at "
                            + m.blockPosition().toShortString() + ", site " + site[i].centrePos.toShortString()
                            + ", " + g.describeState());
                    }
                s.finish();
                return;
            }
            stepper[0].run();
        });
        stepper[0].run();
    }
}
