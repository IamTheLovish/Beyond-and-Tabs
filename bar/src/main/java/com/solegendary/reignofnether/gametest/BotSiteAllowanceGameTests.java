package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.bot.BotPlayer;
import com.solegendary.reignofnether.bot.BotTuning;
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
import java.util.Locale;

/**
 * A bot whose Tier 2 is researched lays its T2 lab even while its build-site allowance is full. The whole build chain
 * runs only while the bot has fewer unbuilt sites than {@code maxConcurrentSites()} - sitesBase plus one per 8 minutes:
 * easy 2, medium 3, hard 4 at minute 12 - so sites waiting on builders (an extractor and a spire "(no builders)", as the
 * T2LAB diag logged them) held an easy bot's Dungeon off for minutes: bots_reach_tier_2 "easy (EASY monsters) never
 * finished its T2 lab (research done 12:08 / 14:23 / 12:21, at 17:59)", flake-stress 42101031597 runners 4, 5, 8 on
 * main e2ed7cab; diag 42134715427 logged no T2 line from the easy bot for whole stretches after its research.
 * The gate is the same for every difficulty. Here, for each of easy, medium and hard (three bots on three platforms): a
 * monsters bot at minute 12, Tier 2 researched, a finished Mausoleum and Graveyard (the Dungeon's T1 lab), metal 0.4 and
 * energy 0.8 full (not floating, not energy starved: no earlier rule takes the think), and as many unbuilt Wind
 * Generators as its allowance. Within five thinks each must order its Dungeon.
 * The second test covers the fix's other tier step: the T2 lab's own T1 requirement, for the Horde (piglins) a Hoglin
 * Stables, laid while the research runs or once it is done. Its platform is crimson nylium over netherrack: a piglin
 * building must stand on nether ground (BuildingValidators.isNonPiglinOrOnNetherBlocks, NetherBlocks.isNetherBlock).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BotSiteAllowanceGameTests {

    static final int HALF = 24, Y = 250, THINKS = 5, SPREAD = 64;
    static final long MINUTE_12 = 20L * 60 * 12;
    static final BlockPos[] SITE_OFFSETS = { new BlockPos(-14, 0, -14), new BlockPos(-14, 0, 14), new BlockPos(14, 0, 14),
        new BlockPos(0, 0, -19), new BlockPos(-19, 0, 0) };
    static final List<BotPlayer.Difficulty> DIFFICULTIES = List.of(BotPlayer.Difficulty.EASY, BotPlayer.Difficulty.MEDIUM,
        BotPlayer.Difficulty.HARD);

    /** One bot's layout: what it owns, and how to take it all away again. */
    record Case(BotPlayer.Difficulty difficulty, String owner, BotPlayer brain, int allowance, Runnable undo) { }

    /** What a case lays and what it waits for: the faction, its capitol, the lab already finished, and the tier building it must order. */
    record Kit(com.solegendary.reignofnether.faction.Faction faction, com.solegendary.reignofnether.building.Building capitol,
               com.solegendary.reignofnether.building.Building finished, com.solegendary.reignofnether.building.Building wind,
               com.solegendary.reignofnether.building.Building wanted, boolean nether,
               net.minecraftforge.registries.RegistryObject<? extends net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob>> worker) { }

    static Kit monsters() {
        return new Kit(Factions.MONSTERS, Buildings.MAUSOLEUM, Buildings.GRAVEYARD, Buildings.WIND_GENERATOR_MONSTERS, Buildings.DUNGEON, false,
            EntityRegistrar.ZOMBIE_VILLAGER_UNIT);
    }

    static Kit piglins() {
        // the Horde's army lab is the Basic Portal; with Tier 2 researched its T2 lab wants a Hoglin Stables first
        return new Kit(Factions.PIGLINS, Buildings.CENTRAL_PORTAL, Buildings.PORTAL_BASIC, Buildings.WIND_GENERATOR_PIGLINS, Buildings.HOGLIN_STABLES, true,
            EntityRegistrar.GRUNT_UNIT);
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 120, batch = "bot_site_allowance")
    public static void a_bot_of_every_difficulty_lays_its_t2_lab_while_its_site_allowance_is_full(GameTestHelper helper) {
        run(helper, monsters(), DIFFICULTIES);
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 120, batch = "bot_site_allowance_nether")
    public static void a_horde_bot_lays_its_hoglin_stables_while_its_site_allowance_is_full(GameTestHelper helper) {
        run(helper, piglins(), DIFFICULTIES);
    }

    private static void run(GameTestHelper helper, Kit kit, List<BotPlayer.Difficulty> difficulties) {
        ServerLevel level = helper.getLevel();
        List<Case> cases = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        boolean[] done = { false };
        Runnable undoAll = () -> {
            if (done[0])
                return;
            done[0] = true;
            for (Case c : cases)
                c.undo().run();
        };
        TestCleanup.atEnd(helper, undoAll);
        for (int i = 0; i < difficulties.size(); i++) {
            Case c = setUp(helper, level, kit, difficulties.get(i), i, problems);
            if (c != null)
                cases.add(c);
        }
        if (!problems.isEmpty()) {
            undoAll.run();
            helper.fail(String.join("; ", problems));
            return;
        }
        // the spawned workers join the unit list at their first tick: think from tick 3
        helper.runAfterDelay(3, () -> {
            if (done[0])
                return;
            for (Case c : cases) {
                try {
                    long now = level.getGameTime();
                    int t = 0;
                    for (; t < THINKS && !ordered(c.owner(), kit.wanted()); t++)
                        c.brain().think(level, now + MINUTE_12 + 20L * t);
                    int unbuilt = Blueprints.pendingOf(c.owner()).size();
                    for (BuildingPlacement b : BuildingServerEvents.getBuildings())
                        if (c.owner().equals(b.ownerName) && !b.isBuilt)
                            unbuilt++;
                    ReignOfNether.LOGGER.info("[SiteAllowance] GameTest {} {}: {} ordered {} after {} thinks; allowance {}, {} unbuilt sites",
                        kit.faction().getName(), c.difficulty(), kit.wanted().name, ordered(c.owner(), kit.wanted()), t, c.allowance(), unbuilt);
                    if (!ordered(c.owner(), kit.wanted()))
                        problems.add(kit.faction().getName() + " " + c.difficulty() + " (minute 12, allowance " + c.allowance() + ", Tier 2"
                            + " researched, " + kit.finished().name + " finished, metal 0.4, energy 0.8) did not order its " + kit.wanted().name
                            + " in " + THINKS + " thinks while " + c.allowance() + " unbuilt Wind Generators held its build-site allowance ("
                            + unbuilt + " unbuilt sites)");
                } catch (Exception e) {
                    problems.add(kit.faction().getName() + " " + c.difficulty() + ": the brain threw: " + e);
                }
            }
            undoAll.run();
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        });
    }

    /** Lays one bot's case on its own platform (the i-th, SPREAD blocks apart); null and a problem when it could not. */
    private static Case setUp(GameTestHelper helper, ServerLevel level, Kit kit, BotPlayer.Difficulty d, int i, List<String> problems) {
        String owner = "gametest_allow_" + d.name().toLowerCase(Locale.ROOT) + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
        int allowance = BotTuning.of(d).sitesBase() + 1;   // + one per 8 minutes, at minute 12
        BlockPos centre = helper.absolutePos(new BlockPos(8, 1, 8));
        BlockPos home = new BlockPos(centre.getX(), Y, centre.getZ() + i * SPREAD);
        for (int dx = -HALF; dx <= HALF; dx++)
            for (int dz = -HALF; dz <= HALF; dz++) {
                level.setBlock(new BlockPos(home.getX() + dx, Y - 1, home.getZ() + dz),
                    (kit.nether() ? Blocks.NETHERRACK : Blocks.DIRT).defaultBlockState(), 2);
                level.setBlock(new BlockPos(home.getX() + dx, Y, home.getZ() + dz),
                    (kit.nether() ? Blocks.CRIMSON_NYLIUM : Blocks.GRASS_BLOCK).defaultBlockState(), 2);
            }
        Resources pool = new Resources(owner, 0, 800, 400);   // energy (wood) 0.8 full, metal (ore) 0.4 full
        ResourcesServerEvents.resourcesList.add(pool);
        ResearchServerEvents.addResearch(owner, ProductionItems.RESEARCH_TIER_2);
        List<Entity> workers = new ArrayList<>();
        Runnable undo = () -> {
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
        BlockPos up = home.above();
        BuildingPlacement capitol = place(kit.capitol(), up, owner);
        BuildingPlacement graveyard = place(kit.finished(), up.offset(12, 0, -10), owner);
        int winds = 0;
        for (int s = 0; s < allowance && s < SITE_OFFSETS.length; s++)
            if (place(kit.wind(), up.offset(SITE_OFFSETS[s]), owner) != null)
                winds++;
        for (int w = 0; w < 4; w++)
            CombatEngineerGameTests.spawn(level, kit.worker().get(), owner, up.offset(2 + w, 0, 6), workers);
        if (capitol == null || graveyard == null || winds != allowance || workers.size() != 4) {
            undo.run();
            problems.add(kit.faction().getName() + " " + d + ": could not lay the case (capitol " + (capitol != null) + ", "
                + kit.finished().name + " " + (graveyard != null) + ", "
                + winds + " of " + allowance + " unbuilt winds, " + workers.size() + " of 4 workers)");
            return null;
        }
        capitol.isBuilt = true;
        graveyard.isBuilt = true;
        BotPlayer brain = new BotPlayer(owner, kit.faction(), d, home);
        brain.enemyFilter = o -> false;
        return new Case(d, owner, brain, allowance, undo);
    }

    private static BuildingPlacement place(com.solegendary.reignofnether.building.Building b, BlockPos at, String owner) {
        // a command placement (no cost, no terrain rules) that workers still have to build: freeBuild, not selfBuilding
        return BuildingServerEvents.placeBuilding(b, at, Rotation.NONE, owner, new int[0], false, false, true, true);
    }

    /** The bot has {@code wanted}: ordered (a blueprint) or placed. */
    private static boolean ordered(String owner, com.solegendary.reignofnether.building.Building wanted) {
        for (BuildingPlacement b : Blueprints.pendingOf(owner))
            if (b.getBuilding() == wanted)
                return true;
        for (BuildingPlacement b : BuildingServerEvents.getBuildings())
            if (owner.equals(b.ownerName) && b.getBuilding() == wanted)
                return true;
        return false;
    }
}
