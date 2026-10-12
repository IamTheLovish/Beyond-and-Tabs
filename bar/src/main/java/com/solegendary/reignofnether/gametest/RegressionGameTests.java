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
}
