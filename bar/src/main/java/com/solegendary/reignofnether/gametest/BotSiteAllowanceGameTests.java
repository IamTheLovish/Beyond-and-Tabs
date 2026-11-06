package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.bot.BotPlayer;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.Blueprints;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * A bot whose Tier 2 is researched lays its T2 lab even while its build-site allowance is full. The whole build chain
 * runs only while the bot has fewer unbuilt sites than {@code maxConcurrentSites()} - for an easy bot 1, plus one per 8
 * minutes (2 at minutes 8-15) - so two sites waiting on builders (an extractor and a spire "(no builders)", as the
 * T2LAB diag logged them) held the Dungeon off for minutes: bots_reach_tier_2 "easy (EASY monsters) never finished its
 * T2 lab (research done 12:08 / 14:23 / 12:21, at 17:59)", flake-stress 42101031597 runners 4, 5, 8 on main e2ed7cab;
 * diag 42134715427 logged no T2 line from the easy bot for whole stretches after its research (12:13 to 16:00).
 * Here: an easy monsters bot at minute 12 (its allowance 2), Tier 2 researched, a finished Mausoleum and Graveyard (the
 * Dungeon's T1 lab), metal 0.4 and energy 0.8 full (not floating, not energy starved: no earlier rule takes the think),
 * and two unbuilt Wind Generators holding the allowance. Within five thinks it must order its Dungeon.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BotSiteAllowanceGameTests {

    static final int HALF = 24, Y = 250, THINKS = 5;
    static final long MINUTE_12 = 20L * 60 * 12;

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 120, batch = "bot_site_allowance")
    public static void an_easy_bot_lays_its_t2_lab_while_its_site_allowance_is_full(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_allow_" + helper.absolutePos(BlockPos.ZERO).asLong();
        BlockPos centre = helper.absolutePos(new BlockPos(8, 1, 8));
        BlockPos home = new BlockPos(centre.getX(), Y, centre.getZ());
        for (int dx = -HALF; dx <= HALF; dx++)
            for (int dz = -HALF; dz <= HALF; dz++) {
                level.setBlock(new BlockPos(home.getX() + dx, Y - 1, home.getZ() + dz), Blocks.DIRT.defaultBlockState(), 2);
                level.setBlock(new BlockPos(home.getX() + dx, Y, home.getZ() + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            }
        Resources pool = new Resources(owner, 0, 800, 400);   // energy (wood) 0.8 full, metal (ore) 0.4 full
        ResourcesServerEvents.resourcesList.add(pool);
        ResearchServerEvents.addResearch(owner, ProductionItems.RESEARCH_TIER_2);
        List<Entity> workers = new ArrayList<>();
        List<BuildingPlacement> placed = new ArrayList<>();
        boolean[] done = { false };
        Runnable undo = () -> {
            done[0] = true;
            for (BuildingPlacement b : new ArrayList<>(Blueprints.pendingOf(owner)))
                Blueprints.cancel(b, null);
            for (BuildingPlacement b : new ArrayList<>(BuildingServerEvents.getBuildings()))
                if (owner.equals(b.ownerName))
                    BuildingServerEvents.getBuildings().remove(b);
            workers.forEach(Entity::discard);
            ResourcesServerEvents.resourcesList.remove(pool);
            ResearchServerEvents.removeAllResearchFor(owner);
            for (int dx = -HALF; dx <= HALF; dx++)
                for (int dz = -HALF; dz <= HALF; dz++)
                    for (int dy = -1; dy <= 12; dy++)
                        level.setBlock(new BlockPos(home.getX() + dx, Y + dy, home.getZ() + dz), Blocks.AIR.defaultBlockState(), 2);
        };
        TestCleanup.atEnd(helper, () -> {
            if (!done[0])
                undo.run();
        });
        BlockPos up = home.above();
        BuildingPlacement capitol = place(Buildings.MAUSOLEUM, up, owner, placed);
        BuildingPlacement graveyard = place(Buildings.GRAVEYARD, up.offset(12, 0, -10), owner, placed);
        BuildingPlacement windA = place(Buildings.WIND_GENERATOR_MONSTERS, up.offset(-14, 0, -14), owner, placed);
        BuildingPlacement windB = place(Buildings.WIND_GENERATOR_MONSTERS, up.offset(-14, 0, 14), owner, placed);
        if (capitol == null || graveyard == null || windA == null || windB == null) {
            undo.run();
            helper.fail("could not place the layout: capitol " + (capitol != null) + ", graveyard " + (graveyard != null)
                + ", two unbuilt winds " + (windA != null) + "/" + (windB != null));
            return;
        }
        capitol.isBuilt = true;
        graveyard.isBuilt = true;
        for (int i = 0; i < 4; i++)
            CombatEngineerGameTests.spawn(level, EntityRegistrar.ZOMBIE_VILLAGER_UNIT.get(), owner, up.offset(2 + i, 0, 6), workers);
        if (workers.size() != 4) {
            undo.run();
            helper.fail("could not create four Zombie Villagers");
            return;
        }
        BotPlayer brain = new BotPlayer(owner, Factions.MONSTERS, BotPlayer.Difficulty.EASY, home);
        brain.enemyFilter = o -> false;
        // the spawned workers join the unit list a tick after they are added: think from tick 3
        helper.runAfterDelay(3, () -> {
            if (done[0])
                return;
            List<String> problems = new ArrayList<>();
            try {
                long now = level.getGameTime();
                int t = 0;
                for (; t < THINKS && !ordered(owner); t++)
                    brain.think(level, now + MINUTE_12 + 20L * t);
                int unbuilt = 0;
                for (BuildingPlacement b : BuildingServerEvents.getBuildings())
                    if (owner.equals(b.ownerName) && !b.isBuilt)
                        unbuilt++;
                unbuilt += Blueprints.pendingOf(owner).size();
                ReignOfNether.LOGGER.info("[SiteAllowance] GameTest: Dungeon ordered {} after {} thinks; {} unbuilt sites (incl. any Dungeon)",
                    ordered(owner), t, unbuilt);
                if (!ordered(owner))
                    problems.add("the easy bot (minute 12, Tier 2 researched, Graveyard finished, metal 0.4, energy 0.8) did not order"
                        + " its Dungeon in " + THINKS + " thinks while two unbuilt Wind Generators held its build-site allowance"
                        + " (" + unbuilt + " unbuilt sites)");
            } catch (Exception e) {
                problems.add("the easy monsters brain threw: " + e);
            }
            undo.run();
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        });
    }

    private static BuildingPlacement place(com.solegendary.reignofnether.building.Building b, BlockPos at, String owner,
                                           List<BuildingPlacement> placed) {
        BuildingPlacement bp = BuildingServerEvents.placeBuilding(b, at, Rotation.NONE, owner, new int[0], false, false, true, true);
        if (bp != null)
            placed.add(bp);
        return bp;
    }

    /** The bot has its Dungeon: ordered (a blueprint) or placed. */
    private static boolean ordered(String owner) {
        for (BuildingPlacement b : Blueprints.pendingOf(owner))
            if (b.getBuilding() == Buildings.DUNGEON)
                return true;
        for (BuildingPlacement b : BuildingServerEvents.getBuildings())
            if (owner.equals(b.ownerName) && b.getBuilding() == Buildings.DUNGEON)
                return true;
        return false;
    }
}
