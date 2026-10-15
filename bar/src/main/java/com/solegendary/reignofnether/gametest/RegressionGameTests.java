package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingReclaim;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.data.DataStorage;
import com.solegendary.reignofnether.building.gameender.GameEnderPlacement;
import com.solegendary.reignofnether.building.gameender.WardSpirePlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Regression tests for the integration bugs found by the code review of the Oct 11 merges (design/code_review.md).
 * Their own holder so the feature branches merging into SkirmishGameTests don't collide with them. Same rules as
 * every other holder: unique owners, own heights, no global counts - all tests run in parallel in one world.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class RegressionGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /**
     * CR-01 / CR-02: a lab being decommissioned refuses new production (the batch command card could queue on it),
     * and its dismantling progress is written to the building's save data and restored from it, so a save/reload no
     * longer leaves a half-dismantled, already-refunded building to repair for free.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void decommission_refuses_production_and_survives_a_reload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_cr_decom_" + helper.absolutePos(BlockPos.ZERO).asLong();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 236;   // this test's own height
        var pool = new Resources(owner, 0, 0, 0);
        ResourcesServerEvents.resourcesList.add(pool);
        BuildingPlacement sold = stamp(level, Buildings.BARRACKS, new BlockPos(base.getX(), y, base.getZ()), owner);
        BuildingPlacement kept = stamp(level, Buildings.BARRACKS, new BlockPos(base.getX(), y, base.getZ() + 14), owner);
        List<String> problems = new ArrayList<>();
        try {
            if (!(sold instanceof ProductionPlacement pp) || kept == null) {
                helper.fail("could not stamp the two Barracks");
                return;
            }
            if (!BuildingReclaim.start(sold, owner)) {
                helper.fail("the decommission was refused: " + BuildingReclaim.refusal(sold, false));
                return;
            }
            if (pp.startProductionItem(ProductionItems.VINDICATOR) || !pp.productionQueue.isEmpty())
                problems.add("a decommissioning Barracks accepted a new unit");
            if (pp.startProductionItem(ProductionItems.UPGRADE_LAB_L2) || !pp.productionQueue.isEmpty())
                problems.add("a decommissioning Barracks accepted a level upgrade");

            BuildingReclaim.tick(2f);   // some work done and paid
            float progress = sold.reclaimProgress, paid = sold.reclaimPaid;
            if (progress <= 0f || progress >= 1f)
                problems.add("unexpected progress after 2 s of work: " + progress);

            // the save path, then a round trip through NBT as the world file would do it
            BuildingReclaim.writeSaveData(sold);
            DataStorage reloaded = DataStorage.read(sold.getDataStorage().write(), level.getServer());
            float[] d = reloaded.getData(BuildingReclaim.DECOMMISSION);
            if (d == null || Math.abs(d[0] - progress) > 1e-4f || Math.abs(d[1] - paid) > 1e-3f)
                problems.add("the saved decommission is " + (d == null ? "missing" : d[0] + "/" + d[1])
                    + ", expected " + progress + "/" + paid);
            float[] none = DataStorage.read(kept.getDataStorage().write(), level.getServer()).getData(BuildingReclaim.DECOMMISSION);
            if (none != null)
                problems.add("a building that is not being decommissioned saved a decommission");

            // what a reload loses (the in-memory fields), then the load path
            sold.decommissioning = false;
            sold.reclaimProgress = 0;
            sold.reclaimPaid = 0;
            BuildingReclaim.restoreFromSave(sold);
            if (!sold.decommissioning || Math.abs(sold.reclaimProgress - progress) > 1e-4f
                    || Math.abs(sold.reclaimPaid - paid) > 1e-3f)
                problems.add("restoreFromSave gave " + sold.decommissioning + " " + sold.reclaimProgress + "/" + sold.reclaimPaid);
            BuildingReclaim.restoreFromSave(kept);
            if (kept.decommissioning)
                problems.add("restoreFromSave started a decommission on a building that never had one");
        } catch (Exception ex) {
            problems.add("threw " + ex);
        } finally {
            clear(level, sold);
            clear(level, kept);
            ResourcesServerEvents.resourcesList.remove(pool);
        }
        if (!problems.isEmpty())
            helper.fail(String.join("; ", problems));
        else
            helper.succeed();
    }

    /**
     * CR-10: Factions.getFaction(Unit) now answers from a per-entity-type cache; it must agree with the registry
     * lookup it replaced, on the first (filling) call and the cached one, for every faction's worker and fighter.
     */
    @GameTest(template = ARENA)
    public static void faction_lookup_cache_matches_the_registry(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<EntityType<?>> types = List.of(EntityRegistrar.VILLAGER_UNIT.get(), EntityRegistrar.VINDICATOR_UNIT.get(),
            EntityRegistrar.GRUNT_UNIT.get(), EntityRegistrar.ZOMBIE_UNIT.get(), EntityRegistrar.SKELETON_UNIT.get());
        List<String> problems = new ArrayList<>();
        for (EntityType<?> type : types) {
            var e = type.create(level);
            if (!(e instanceof Unit u)) {
                problems.add(EntityType.getKey(type) + " is not a unit");
                if (e != null)
                    e.discard();
                continue;
            }
            var expected = Factions.getFaction(Factions.ENTITY_FACTION.get(EntityType.getKey(type)));
            var first = Factions.getFaction(u);
            var second = Factions.getFaction(u);
            if (expected == null || first != expected || second != expected)
                problems.add(EntityType.getKey(type) + ": registry " + expected + ", cache " + first + " then " + second);
            e.discard();
        }
        if (!problems.isEmpty())
            helper.fail(String.join("; ", problems));
        else
            helper.succeed();
    }

    /**
     * CR-06: a Hero-role player is not an RTS player, and the stats table treated every non-RTS player as an
     * observer - so the hero saw the enemy's income, army and storage. They now get their own commanders' rows with
     * data (as allies) and the enemy's without, while a real observer still sees everyone.
     */
    @GameTest(template = ARENA)
    public static void hero_role_player_sees_only_their_teams_stats(GameTestHelper helper) {
        String tag = "gametest_cr_hero_" + helper.absolutePos(BlockPos.ZERO).asLong();
        String hero = tag + "_h", commander = tag + "_c", enemy = tag + "_e", observer = tag + "_o";
        var rows = List.of(
            new com.solegendary.reignofnether.player.PlayerPanelServerEvents.Row(commander, "", true, 10, 20, 1f),
            new com.solegendary.reignofnether.player.PlayerPanelServerEvents.Row(enemy, "", true, 30, 40, 1f));
        int[] teams = { 0, 1 };
        com.solegendary.reignofnether.hero.HeroRoles.assign(hero, List.of(commander));
        List<String> problems = new ArrayList<>();
        try {
            for (var e : com.solegendary.reignofnether.player.PlayerPanelServerEvents.entriesFor(hero, rows, teams)) {
                if (e.name().equals(commander) && (!e.hasAllyData() || e.metalIncome() != 10))
                    problems.add("the hero does not see their commander's numbers");
                if (e.name().equals(enemy) && (e.hasAllyData() || e.metalIncome() != 0))
                    problems.add("the hero sees the enemy's numbers");
            }
            for (var e : com.solegendary.reignofnether.player.PlayerPanelServerEvents.entriesFor(observer, rows, teams))
                if (!e.hasAllyData())
                    problems.add("an observer lost " + e.name() + "'s numbers");
        } finally {
            com.solegendary.reignofnether.hero.HeroRoles.unassign(hero);
        }
        if (!problems.isEmpty())
            helper.fail(String.join("; ", problems));
        else
            helper.succeed();
    }

    // ------------------------------------------------------------------ round 2 (design/code_review.md R2-xx)

    /**
     * R2-01: a game ender's charge (and whether its start was announced) and a Ward Spire's stockpile go through the
     * building's save data. Before, a reload reset both and re-broadcast the game ender's start to every player.
     * Placements are built but never registered or stamped: only the save path is exercised.
     */
    @GameTest(template = ARENA)
    public static void game_ender_charge_and_ward_stockpile_survive_a_reload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_r2_ender_" + helper.absolutePos(BlockPos.ZERO).asLong();
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        List<String> problems = new ArrayList<>();
        try {
            var a = BuildingUtils.getNewBuildingPlacement(Buildings.SUN_SPIRE, level, at, Rotation.NONE, owner, false);
            var b = BuildingUtils.getNewBuildingPlacement(Buildings.SUN_SPIRE, level, at, Rotation.NONE, owner, false);
            var w = BuildingUtils.getNewBuildingPlacement(Buildings.WARD_SPIRE_VILLAGERS, level, at, Rotation.NONE, owner, false);
            var w2 = BuildingUtils.getNewBuildingPlacement(Buildings.WARD_SPIRE_VILLAGERS, level, at, Rotation.NONE, owner, false);
            if (!(a instanceof GameEnderPlacement ender) || !(b instanceof GameEnderPlacement fresh)
                    || !(w instanceof WardSpirePlacement ward) || !(w2 instanceof WardSpirePlacement freshWard)) {
                helper.fail("could not create the Sun Spire / Ward Spire placements");
                return;
            }
            ender.announced = true;
            ender.stage = 2;
            ender.stageProgress = 345;
            ward.charges = WardSpirePlacement.MAX_CHARGES;
            ward.chargeProgress = 77;

            // the save path, an NBT round trip, then the load path on placements that never had the state
            ender.writeSaveState();
            ward.writeSaveState();
            DataStorage enderNbt = DataStorage.read(ender.getDataStorage().write(), level.getServer());
            DataStorage wardNbt = DataStorage.read(ward.getDataStorage().write(), level.getServer());
            fresh.getDataStorage().setData(GameEnderPlacement.CHARGE, enderNbt.getData(GameEnderPlacement.CHARGE));
            freshWard.getDataStorage().setData(WardSpirePlacement.CHARGE, wardNbt.getData(WardSpirePlacement.CHARGE));
            fresh.readSaveState();
            freshWard.readSaveState();
            if (!fresh.announced || fresh.stage != 2 || fresh.stageProgress != 345)
                problems.add("the game ender came back announced=" + fresh.announced + " stage " + fresh.stage + " +"
                    + fresh.stageProgress + ", expected announced, stage 2 +345");
            if (freshWard.charges != WardSpirePlacement.MAX_CHARGES || freshWard.chargeProgress != 77)
                problems.add("the ward came back with " + freshWard.charges + " charges +" + freshWard.chargeProgress);

            // a building that never saved any state loads as a fresh one
            var c = BuildingUtils.getNewBuildingPlacement(Buildings.SUN_SPIRE, level, at, Rotation.NONE, owner, false);
            if (c instanceof GameEnderPlacement none) {
                none.readSaveState();
                if (none.announced || none.stage != 0 || none.stageProgress != 0)
                    problems.add("a game ender with no saved charge did not load fresh");
            }
            // out-of-range values from a damaged or hand-edited save are clamped
            fresh.getDataStorage().setData(GameEnderPlacement.CHARGE, new int[] { 1, 99, -5, -1 });
            fresh.readSaveState();
            if (fresh.stage != GameEnderPlacement.STAGES || fresh.stageProgress != 0)
                problems.add("a corrupt save was not clamped: stage " + fresh.stage + " +" + fresh.stageProgress);
        } catch (Exception ex) {
            problems.add("threw " + ex);
        }
        if (!problems.isEmpty())
            helper.fail(String.join("; ", problems));
        else
            helper.succeed();
    }

    /**
     * R2-05: a hidden trap was never sent to an enemy RTS player, but a Hero-role player is not an RTS player, so an
     * enemy hero saw every trap like a spectator. Now: hidden from enemy players and enemy heroes, shown to the
     * owner's own Hero-role teammate and to spectators, and to everyone once it has fired.
     */
    @GameTest(template = ARENA)
    public static void hidden_trap_is_hidden_from_an_enemy_hero_role_player(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String tag = "gametest_r2_trap_" + helper.absolutePos(BlockPos.ZERO).asLong();
        String owner = tag + "_o", foe = tag + "_f", ownHero = tag + "_oh", foeHero = tag + "_fh", spectator = tag + "_s";
        var bp = BuildingUtils.getNewBuildingPlacement(com.solegendary.reignofnether.building.buildings.defence.Defences.VILLAGERS.trapA(),
            level, helper.absolutePos(new BlockPos(3, 2, 3)), Rotation.NONE, owner, false);
        if (!(bp instanceof com.solegendary.reignofnether.building.buildings.defence.Trap.TrapPlacement trap)) {
            helper.fail("the Kingdom's trap is not a TrapPlacement: " + bp);
            return;
        }
        List<String> problems = new ArrayList<>();
        com.solegendary.reignofnether.hero.HeroRoles.assign(ownHero, List.of(owner));
        com.solegendary.reignofnether.hero.HeroRoles.assign(foeHero, List.of(foe));
        try {
            if (!com.solegendary.reignofnether.unit.Concealment.isBuildingHiddenFromPlayer(trap, foe, true))
                problems.add("the enemy RTS player sees the hidden trap");
            if (!com.solegendary.reignofnether.unit.Concealment.isBuildingHiddenFromPlayer(trap, foeHero, false))
                problems.add("the enemy Hero-role player sees the hidden trap");
            if (com.solegendary.reignofnether.unit.Concealment.isBuildingHiddenFromPlayer(trap, owner, true))
                problems.add("the owner does not see their own trap");
            if (com.solegendary.reignofnether.unit.Concealment.isBuildingHiddenFromPlayer(trap, ownHero, false))
                problems.add("the owner's Hero-role teammate does not see the team's trap");
            if (com.solegendary.reignofnether.unit.Concealment.isBuildingHiddenFromPlayer(trap, spectator, false))
                problems.add("a spectator does not see the trap");
            trap.revealed = true;   // fired once (reveal() would also resync clients; the flag is what is checked)
            if (com.solegendary.reignofnether.unit.Concealment.isBuildingHiddenFromPlayer(trap, foeHero, false))
                problems.add("a revealed trap is still hidden from the enemy hero");
        } finally {
            com.solegendary.reignofnether.hero.HeroRoles.unassign(ownHero);
            com.solegendary.reignofnether.hero.HeroRoles.unassign(foeHero);
        }
        if (!problems.isEmpty())
            helper.fail(String.join("; ", problems));
        else
            helper.succeed();
    }

    /**
     * The tick scheduler (design/ron_rewrite_plan.md, rewrite 1): due order with first-scheduled first inside a tick,
     * each task exactly once, schedule(0) from inside a tick runs at the end of that tick, a task scheduled by a task
     * runs on a later pass (RoN's threw ConcurrentModificationException), and a task that throws is skipped without
     * re-running the others (RoN's re-ran every task of that tick). Drives a private TickScheduler by hand, so no other
     * test's tasks are involved.
     */
    @GameTest(template = ARENA)
    public static void tick_scheduler_runs_each_task_once_in_order_on_time(GameTestHelper helper) {
        var s = new com.solegendary.reignofnether.taskscheduler.TickScheduler("gametest");
        List<String> log = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        s.schedule(2, () -> log.add("a2"));
        s.schedule(0, () -> log.add("b0"));
        s.schedule(2, () -> log.add("c2"));
        s.schedule(1, () -> {
            log.add("t1");
            s.schedule(0, () -> log.add("d"));   // from inside a task: the next pass, not this one
            throw new IllegalStateException("expected by the test");
        });
        s.schedule(-5, () -> log.add("n0"));   // a negative delay is now
        int ran0 = s.tick();
        if (!log.equals(List.of("b0", "n0")) || ran0 != 2)
            problems.add("tick 0 ran " + log);
        log.clear();
        s.tick();
        if (!log.equals(List.of("t1")))
            problems.add("tick 1 ran " + log + " (the throwing task must run once, alone)");
        log.clear();
        s.tick();
        if (!log.equals(List.of("d", "a2", "c2")))
            problems.add("tick 2 ran " + log + ", expected [d, a2, c2]");
        log.clear();
        s.tick();
        if (!log.isEmpty() || s.pending() != 0)
            problems.add("tick 3 ran " + log + " with " + s.pending() + " pending: a task ran twice or stuck");
        s.schedule(1, () -> log.add("x"));
        s.clear();
        s.tick();
        s.tick();
        if (!log.isEmpty())
            problems.add("a task survived clear()");
        if (!problems.isEmpty())
            helper.fail(String.join("; ", problems));
        else
            helper.succeed();
    }

    /**
     * The server scheduler runs its task on the server thread after the right number of server ticks - even when it
     * was scheduled from another thread (as the old CompletableFuture.delayedExecutor callers effectively were).
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void server_tasks_run_on_the_server_thread_in_game_ticks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Thread serverThread = level.getServer().getRunningThread();
        long[] ranAt = { -1, -1 };
        Thread[] ranOn = { null, null };
        long start = level.getGameTime();
        com.solegendary.reignofnether.taskscheduler.TaskSchedulerServerEvents.schedule(5, () -> {
            ranAt[0] = level.getGameTime();
            ranOn[0] = Thread.currentThread();
        });
        Thread other = new Thread(() -> com.solegendary.reignofnether.taskscheduler.TaskSchedulerServerEvents.schedule(3, () -> {
            ranAt[1] = level.getGameTime();
            ranOn[1] = Thread.currentThread();
        }), "gametest-scheduler-caller");
        other.start();
        try {
            other.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        helper.runAfterDelay(20, () -> {
            List<String> problems = new ArrayList<>();
            if (ranOn[0] != serverThread || ranOn[1] != serverThread)
                problems.add("a task ran on " + ranOn[0] + " / " + ranOn[1] + ", not the server thread");
            // the game-time clock and the scheduler's tick differ by at most the tick the task was scheduled in
            if (ranAt[0] < start + 4 || ranAt[0] > start + 7)
                problems.add("the 5-tick task ran after " + (ranAt[0] - start) + " ticks");
            if (ranAt[1] < 0)
                problems.add("the task scheduled from another thread never ran");
            if (!problems.isEmpty())
                helper.fail(String.join("; ", problems));
            else
                helper.succeed();
        });
    }

    // ------------------------------------------------------------------ helpers

    /** A finished building stamped block by block on a stone pad (never constructed), registered server side. */
    static BuildingPlacement stamp(ServerLevel level, Building b, BlockPos origin, String owner) {
        var bp = BuildingUtils.getNewBuildingPlacement(b, level, origin, Rotation.NONE, owner, false);
        if (bp == null)
            return null;
        for (int x = bp.minCorner.getX() - 1; x <= bp.maxCorner.getX() + 1; x++)
            for (int z = bp.minCorner.getZ() - 1; z <= bp.maxCorner.getZ() + 1; z++)
                level.setBlock(new BlockPos(x, bp.minCorner.getY() - 1, z), Blocks.STONE.defaultBlockState(), 2);
        for (var blk : bp.getBlocks())
            if (!blk.getBlockState().isAir())
                level.setBlock(blk.getBlockPos(), blk.getBlockState(), 2);
        bp.initPlacedBlocks();
        bp.isBuilt = true;
        BuildingServerEvents.getBuildings().add(bp);
        return bp;
    }

    static void clear(ServerLevel level, BuildingPlacement bp) {
        if (bp == null)
            return;
        BuildingServerEvents.getBuildings().remove(bp);
        for (var blk : bp.getBlocks())
            level.setBlock(blk.getBlockPos(), Blocks.AIR.defaultBlockState(), 2);
    }

    /**
     * CI's two test shards (GameTestShards) get the same list, one as BAT_GAMETEST_ONLY and one as BAT_GAMETEST_SKIP:
     * every test - its batch listed or not (full or base name, any case), or named by a "test:" prefix - runs in
     * exactly one; with no lists everything runs (a local runGameTestServer) and an empty ONLY list runs nothing.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void test_shards_run_every_test_exactly_once(GameTestHelper helper) {
        var list = GameTestShards.parse(" duel_matrix, Stress_8v8 ,defaultBatch:1,,test:production_buildings_place_");
        java.util.Set<String> none = java.util.Set.of(), noOnly = null;
        String[][] cases = {
            { "duel_matrix:1", "duel_simulator_measures_equal_metal_fights" }, { "duel_matrix:3", "x" },
            { "stress_8v8:1", "eight_v_eight" }, { "defaultBatch:1", "anything" }, { "defaultbatch:2", "other" },
            { "defaultBatch:4", "production_buildings_place_every_unit_6" },
            { "defaultBatch:4", "skirmishgametests.production_buildings_place_every_unit_0" },
            { "defaultBatch:4", "tidewrought_units_are_made_and_attack" }, { "new_batch:1", "new_test" },
            { "noindex", "t" } };
        List<String> problems = new ArrayList<>();
        for (String[] c : cases) {
            boolean a = GameTestShards.keep(c[0], c[1], list, none);
            boolean b = GameTestShards.keep(c[0], c[1], noOnly, list);
            if (a == b)
                problems.add(c[0] + "/" + c[1] + (a ? " runs in both shards" : " runs in neither shard"));
            if (!GameTestShards.keep(c[0], c[1], noOnly, none))
                problems.add(c[0] + "/" + c[1] + " does not run with no lists");
            if (GameTestShards.keep(c[0], c[1], none, none))
                problems.add(c[0] + "/" + c[1] + " runs under an EMPTY ONLY list (an emptied shard list runs nothing)");
        }
        if (!GameTestShards.keep("defaultBatch:4", "production_buildings_place_every_unit_3", list, none)
                || GameTestShards.keep("defaultBatch:4", "tidewrought_units_are_made_and_attack", list, none)
                || GameTestShards.keep("defaultBatch:2", "anything", list, none))
            problems.add("ONLY took the wrong tests (a test: prefix takes only its tests, defaultBatch:1 only itself)");
        if (!problems.isEmpty())
            helper.fail(String.join("; ", problems));
        else
            helper.succeed();
    }
}
