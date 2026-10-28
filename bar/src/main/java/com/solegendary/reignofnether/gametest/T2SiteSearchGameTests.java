package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.bot.BotPlayer;
import com.solegendary.reignofnether.bot.BotServerEvents;
import com.solegendary.reignofnether.building.Blueprints;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.BuildingValidators;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.nether.NetherBlocks;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.EconomyPace;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.resources.TierGate;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.util.MiscUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Horde bot must find a site for its T2 lab wherever its own nether ground is, not only round its layout slots.
 * <p>
 * Root cause (test first, red on main): the Horde's converter, lab annex, Hoglin Stables and Flame Sanctuary must stand
 * on nether blocks (BuildingValidators.isNonPiglinOrOnNetherBlocks: over 80% of the footprint's solid bottom blocks on
 * NetherBlocks), while winds, extractors, portals and defences may go anywhere. The bot's wind field
 * (BotPlayer.layoutSlot WIND, 14-26 blocks behind the capitol) is laid over the capitol's own netherrack (CentralPortal's
 * nether range reaches 30). BotPlayer.placeTechBuilding only walks its ARMY slots, and placeNearImpl only samples 65
 * points (rings of 4 blocks out to 32) round a slot, so once the netherrack near home is built over the T2 lab has no
 * valid site for the rest of the match, even with the bot's own netherrack standing free a little further out.
 * <p>
 * CI evidence: stress run 40607484452 sample 4 - medium Horde research done 10:44, Hoglin Stables min 9, Flame
 * Sanctuary never placed; blk showed energy_converter_nether / lab_annex_nether "no valid site within 32 blocks". The
 * fix comes after PR #203 merges; this test only says WHAT must hold (the bot orders its T2 lab when it owns a valid
 * nether site), not how the bot finds it.
 * <p>
 * The scene is a sky pad of its own far east of the arena, stamped by hand rather than seated through
 * PlayerServerEvents.startRTSBot: that forms a battlefield round the start (BattlefieldSetup clears the start area,
 * stamps metal patches and raises the boundary wall), which would rewrite the netherrack laid here and give the bot
 * extractor work. The brain's real think() is driven once a second, the cadence BotServerEvents gives a seated bot.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class T2SiteSearchGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** Ground level of the pad: above ordinary terrain (DuelGameTests' pads use the same height); the air over it is cleared. */
    static final int PAD_Y = 210;
    /** East of the arena, clear of the other far columns (+1500 bot battlefields, +4000, +12000). */
    static final int OFFSET_X = 5200;
    /** CentralPortal.getMaxNetherRange: how far a capitol's netherrack reaches round its centre. */
    static final int NEAR_NETHER = 30;
    /**
     * The wind field over that netherrack: cells out to this radius on a 7-block pitch. Any 9-block run then holds a whole
     * 3-block wind, so every 9x9 footprint over the netherrack holds a wind (or one of the stamped buildings).
     */
    static final int WIND_FIELD = 38, WIND_PITCH = 7;
    /**
     * Farthest origin the layout search can try: the farthest ARMY slot (36 behind home) plus placeNearImpl's 8 rings of
     * 4 blocks, rounded up. Every origin this close to home, whichever way "back" points, is checked below.
     */
    static final int SEARCH_REACH = 70;
    /** The far portal's netherrack: centred this far east of the capitol's centre, with this radius (nearest edge ~85 from home). */
    static final int FAR_DISTANCE = 100, FAR_NETHER = 20;
    /** Pad beyond anything a searched footprint or the far netherrack can touch. */
    static final int MARGIN = 14;
    /** BotServerEvents thinks a seated bot once a second. */
    static final int THINK_EVERY = 20;
    /** 55 thinks, all inside the bot's minute 0 at Standard pace, so no clock rule (labs, extractors, defences) joins in. */
    static final int THINK_TICKS = 1100;
    /** Cap on the wait for the pad's entity sections (GameTestChunks.whenEntitiesReady). */
    static final int ENTITY_CAP = 600;

    @GameTest(template = ARENA, timeoutTicks = ENTITY_CAP + THINK_TICKS + 300)
    public static void a_horde_bot_places_its_t2_lab_on_its_own_nether_ground_beyond_the_layout_search(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        Scene s = new Scene(level, "gametest_t2site_" + helper.absolutePos(BlockPos.ZERO).asLong(),
            new BlockPos(base.getX() + OFFSET_X, PAD_Y, base.getZ()));
        GameTestChunks.holdAndAwaitOrFail(level, s.chunks, "t2 site pad");
        // entity ticking needs the ring round the held chunks FULL as well (RaidGameTests.Pads, run 40578551395)
        GameTestChunks.awaitRingOrFail(level, s.chunks, "t2 site pad");
        boolean[] done = { false };
        long[] firstThink = { -1 };
        GameTestChunks.whenEntitiesReady(helper, level, s.chunks, 5, 400, ENTITY_CAP, "t2 site pad", () -> {
            String error;
            try {
                error = s.build();
            } catch (RuntimeException e) {
                ReignOfNether.LOGGER.error("[T2Site] setup threw", e);
                error = "threw " + e;
            }
            if (error != null) {
                done[0] = true;
                s.finish(helper, "setup: " + error);
                return;
            }
            firstThink[0] = helper.getTick() + 5;   // the workers join the unit list a tick after they are added
        });
        helper.onEachTick(() -> {
            if (done[0] || firstThink[0] < 0)
                return;
            long t = helper.getTick() - firstThink[0];
            if (t < 0 || t % THINK_EVERY != 0)
                return;
            String verdict;   // null: think on; "": the T2 lab is ordered; else the failure
            try {
                verdict = s.step(t);
            } catch (RuntimeException e) {
                ReignOfNether.LOGGER.error("[T2Site] think threw", e);
                verdict = "the bot's think threw " + e;
            }
            if (verdict == null)
                return;
            done[0] = true;
            s.finish(helper, verdict.isEmpty() ? null : verdict);
        });
    }

    /** The pad, the bot's stamped base and brain, and everything the test must take out again. */
    static final class Scene {
        final ServerLevel level;
        final String bot;
        /** The capitol's origin: the bot's home (BotPlayer takes home from its capitol's originPos). */
        final BlockPos home;
        final BotPlayer.Kit kit = BotPlayer.kitFor(Factions.PIGLINS);
        final int x0, z0, x1, z1;
        final List<long[]> chunks;
        final List<BuildingPlacement> stamped = new ArrayList<>();
        final List<Entity> made = new ArrayList<>();
        Resources pool;
        BotPlayer brain;
        BlockPos nearCentre, farCentre, farSite;
        boolean padLaid, cleaned;
        int thinks, t2Misses;

        Scene(ServerLevel level, String bot, BlockPos home) {
            this.level = level;
            this.bot = bot;
            this.home = home;
            // the capitol is about 11 x 9: its centre is a few blocks from the origin, so pad by the reach plus a margin
            x0 = home.getX() - SEARCH_REACH - MARGIN;
            z0 = home.getZ() - SEARCH_REACH - MARGIN;
            x1 = home.getX() + 10 + FAR_DISTANCE + FAR_NETHER + MARGIN;
            z1 = home.getZ() + SEARCH_REACH + MARGIN;
            chunks = new ArrayList<>();
            for (int cx = x0 >> 4; cx <= x1 >> 4; cx++)
                for (int cz = z0 >> 4; cz <= z1 >> 4; cz++)
                    chunks.add(new long[] { cx, cz });
        }

        Vec3i size(Building b) {
            return BuildingUtils.getBuildingSize(b.getRelativeBlockData(level));
        }

        BuildingPlacement stamp(Building b, int x, int z) {
            BuildingPlacement bp = CombatEngineerGameTests.stampedBuilt(level, b, new BlockPos(x, PAD_Y, z), bot);
            // registered, so the bot (its think lists its own buildings), TierGate.ownsFinished and every overlap check see it
            BuildingServerEvents.getBuildings().add(bp);
            stamped.add(bp);
            return bp;
        }

        static boolean within(int x, int z, BlockPos c, int r) {
            int dx = x - c.getX(), dz = z - c.getZ();
            return dx * dx + dz * dz <= r * r;
        }

        static boolean boxesMeet(int ax0, int az0, int ax1, int az1, BuildingPlacement b) {
            return ax0 <= b.maxCorner.getX() && ax1 >= b.minCorner.getX() && az0 <= b.maxCorner.getZ() && az1 >= b.minCorner.getZ();
        }

        /** Lays the pad and the bot's base; null when the scene is what the test needs, else what is wrong with it. */
        String build() {
            Building t2Lab = kit == null ? null : kit.t2Lab();
            Building t1Req = t2Lab == null ? null : TierGate.t1LabFor(t2Lab);
            if (t2Lab == null || t1Req == null)
                return "the Horde kit has no T2 lab or the T2 lab no T1 prerequisite";
            Vec3i cap = size(kit.capitol());
            nearCentre = home.offset(cap.getX() / 2, 0, cap.getZ() / 2);
            farCentre = nearCentre.offset(FAR_DISTANCE, 0, 0);

            // flat ground: grass on dirt, netherrack on the capitol's nether range and round the far portal; whatever terrain
            // rises past the pad is cleared so nothing but the stamped buildings stands on it
            for (int x = x0; x <= x1; x++)
                for (int z = z0; z <= z1; z++) {
                    int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                    for (int y = PAD_Y + 1; y < top; y++)
                        level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    boolean nether = within(x, z, nearCentre, NEAR_NETHER) || within(x, z, farCentre, FAR_NETHER);
                    level.setBlock(new BlockPos(x, PAD_Y - 1, z), Blocks.DIRT.defaultBlockState(), 2);
                    level.setBlock(new BlockPos(x, PAD_Y, z),
                        (nether ? Blocks.NETHERRACK : Blocks.GRASS_BLOCK).defaultBlockState(), 2);
                }
            padLaid = true;

            // a medium Horde bot at Tier 2: researched, the capitol, its army lab and its finished Hoglin Stables (the
            // Flame Sanctuary's T1 prerequisite). Military Portals: the Horde's lab after its refit, so the bot does not
            // rebuild a portal mid-test. Stamped, so no NetherZone starts spreading (onBuilt never runs)
            ResearchServerEvents.addResearch(bot, ProductionItems.RESEARCH_TIER_2);
            EconomyPace.setTestOverride(bot, EconomyPace.STANDARD);   // the bot's clock: minute 0 for the whole run
            // metal under BotTuning.FLOAT_FRAC of storage for the whole run - 200 plus ~8.5/s (capitol, three extractors,
            // the converter) over 56 s stays under 0.5 of 1500 - because a floating bot goes for every metal patch in the
            // world, other tests' included. Placement itself costs nothing up front (flow economy). Energy well over the
            // starved mark: that wind rule ranks above the T2 lab
            pool = new Resources(bot, 0, 1200, 200);
            ResourcesServerEvents.resourcesList.add(pool);
            stamp(kit.capitol(), home.getX(), home.getZ());
            Vec3i lab = size(Buildings.PORTAL_MILITARY);
            stamp(Buildings.PORTAL_MILITARY, home.getX() + 1, home.getZ() - 2 - lab.getZ());
            stamp(t1Req, home.getX() + cap.getX() + 3, home.getZ());
            // the wind field over the rest of the capitol's netherrack (what the real bot does with layoutSlot WIND); one
            // cell holds the converter instead, so the converter rule (energy floats) is met and stays quiet
            List<BuildingPlacement> base = new ArrayList<>(stamped);
            BlockPos converterWant = home.offset(-8, 0, cap.getZ() / 2);
            int[] converterCell = null;
            List<int[]> cells = new ArrayList<>();
            for (int gx = nearCentre.getX() - WIND_FIELD; gx <= nearCentre.getX() + WIND_FIELD; gx += WIND_PITCH)
                for (int gz = nearCentre.getZ() - WIND_FIELD; gz <= nearCentre.getZ() + WIND_FIELD; gz += WIND_PITCH) {
                    if (!within(gx + 1, gz + 1, nearCentre, WIND_FIELD))
                        continue;
                    boolean free = true;
                    for (BuildingPlacement b : base)
                        free &= !boxesMeet(gx, gz, gx + 2, gz + 2, b);
                    if (!free)
                        continue;
                    int[] cell = { gx, gz };
                    cells.add(cell);
                    if (converterCell == null || new BlockPos(gx, PAD_Y, gz).distSqr(converterWant)
                            < new BlockPos(converterCell[0], PAD_Y, converterCell[1]).distSqr(converterWant))
                        converterCell = cell;
                }
            for (int[] c : cells)
                stamp(c == converterCell ? kit.converter() : kit.wind(), c[0], c[1]);
            // extractors met for minute 0 (2 + minutes), well over 45 blocks from home so the refit and its energy rule
            // stay out of it - off any patch, so no worker walks to another test's arena for one
            for (int i = 0; i < 3; i++)
                stamp(kit.extractor(), home.getX() - SEARCH_REACH + 12, home.getZ() - 20 + 9 * i);
            // the bot's own nether ground beyond the layout search: a second army lab in its netherrack, room to spare
            stamp(Buildings.PORTAL_MILITARY, farCentre.getX() - lab.getX() / 2, farCentre.getZ() - 2 - lab.getZ());
            Vec3i sanctuary = size(t2Lab);
            farSite = new BlockPos(farCentre.getX() - sanctuary.getX() / 2, PAD_Y, farCentre.getZ() + 2);

            // three workers on free grass in front of the base
            for (int i = 0; i < 3; i++) {
                Mob m = EntityRegistrar.GRUNT_UNIT.get().create(level);
                if (m == null)
                    return "could not create a Grunt";
                m.moveTo(home.getX() + 4.5 + 2 * i, PAD_Y + 1, home.getZ() - WIND_FIELD - 8 + 0.5, 0, 0);
                if (m instanceof Unit u)
                    u.setOwnerName(bot);
                if (!level.addFreshEntity(m))
                    return "a Grunt did not join the level";
                made.add(m);
            }
            brain = new BotPlayer(bot, Factions.PIGLINS, BotPlayer.Difficulty.MEDIUM, home);
            brain.enemyFilter = o -> false;   // its army never goes for another test's units

            // the bot is in the state the T2 lab rule wants: Tier 2, a finished T1 prerequisite, the server's gate open
            if (!ResearchServerEvents.playerHasResearch(bot, ProductionItems.RESEARCH_TIER_2))
                return "the bot does not have Tier 2";
            if (!TierGate.ownsFinished(bot, t1Req))
                return "the bot owns no finished " + t1Req.name;
            String gate = TierGate.placementError(bot, t2Lab);
            if (gate != null)
                return "the tier gate refuses the " + t2Lab.name + ": " + gate;
            // a valid T2 lab site stands on the bot's own netherrack...
            String far = BuildingValidators.getPlacementValidityError(level, t2Lab, farSite, bot, Rotation.NONE, false, false, true);
            if (far != null)
                return "the far site " + farSite.toShortString() + " is not valid for the " + t2Lab.name + ": " + far;
            // ...and none anywhere the layout search reaches
            return nearSearchFindsNothing(t2Lab);
        }

        /**
         * Every origin within SEARCH_REACH of home (so every ARMY slot's ring-0 point and its whole spiral, whichever way
         * "back" points), on the ground the bot would sample there (MiscUtil.getHighestNonAirBlock, leaves skipped, as
         * placeNearImpl does), with any nether block under the footprint's solid bottom blocks: none may be a valid T2
         * lab site. Null when that holds and the capitol's netherrack is really there (built over, not missing).
         */
        String nearSearchFindsNothing(Building t2Lab) {
            ArrayList<BuildingBlock> rel = t2Lab.getRelativeBlockData(level);
            int minY = Integer.MAX_VALUE;
            for (BuildingBlock b : rel)
                minY = Math.min(minY, b.getBlockPos().getY());
            // the blocks BuildingValidators.isOnNetherBlocks counts: the solid ones of the bottom layer (Rotation.NONE)
            List<int[]> under = new ArrayList<>();
            for (BuildingBlock b : rel)
                if (b.getBlockPos().getY() == minY && b.getBlockState().isSolid())
                    under.add(new int[] { b.getBlockPos().getX(), b.getBlockPos().getZ() });
            if (under.isEmpty())
                return "the " + t2Lab.name + " has no solid bottom blocks";
            int allNether = 0, checked = 0;
            List<String> valid = new ArrayList<>();
            for (int x = home.getX() - SEARCH_REACH; x <= home.getX() + SEARCH_REACH; x++)
                for (int z = home.getZ() - SEARCH_REACH; z <= home.getZ() + SEARCH_REACH; z++) {
                    if (!within(x, z, home, SEARCH_REACH))
                        continue;
                    BlockPos surface = MiscUtil.getHighestNonAirBlock(level, new BlockPos(x, 0, z), true);
                    int nether = 0;
                    // a building's bottom layer sits at origin.y + 1 + minY (BuildingUtils.getAbsoluteBlockData): this is under it
                    for (int[] u : under)
                        if (NetherBlocks.isNetherBlock(level, new BlockPos(x + u[0], surface.getY() + minY, z + u[1])))
                            nether++;
                    if (nether == 0)
                        continue;   // refused as off the nether whatever the threshold
                    if (nether == under.size())
                        allNether++;
                    checked++;
                    if (BuildingValidators.isPlacementValid(level, t2Lab, surface, bot, Rotation.NONE, false, false, true)
                            && valid.size() < 3)
                        valid.add(surface.toShortString());
                }
            if (!valid.isEmpty())
                return "the layout search can reach a valid " + t2Lab.name + " site (" + String.join(", ", valid)
                    + ") within " + SEARCH_REACH + " blocks of home " + home.toShortString() + " - the scene does not cover the near netherrack";
            if (allNether == 0)
                return "no origin near home stands wholly on netherrack: the capitol's nether ground is missing, not built over";
            ReignOfNether.LOGGER.info("[T2Site] {} origins over nether near home checked ({} wholly on netherrack), none valid; far site {} valid",
                checked, allNether, farSite.toShortString());
            return null;
        }

        /** The bot's T2 lab, ordered (a blueprint its workers walk to) or started; null when there is none. */
        BuildingPlacement t2LabOrder() {
            for (BuildingPlacement bp : Blueprints.pendingOf(bot))
                if (bp.getBuilding() == kit.t2Lab())
                    return bp;
            for (BuildingPlacement bp : BuildingServerEvents.getBuildings())
                if (bot.equals(bp.ownerName) && bp.getBuilding() == kit.t2Lab() && !bp.isDestroyedServerside)
                    return bp;
            return null;
        }

        int count(List<BuildingPlacement> list, boolean ownerCheck) {
            int n = 0;
            for (BuildingPlacement bp : list)
                if (bp.getBuilding() == kit.t2Lab() && (!ownerCheck || bot.equals(bp.ownerName)))
                    n++;
            return n;
        }

        /** One think at {@code t} ticks into the run: null to go on, "" when the T2 lab is ordered, else the failure. */
        String step(long t) {
            if (t == 0) {
                int workers = 0;
                for (LivingEntity le : UnitServerEvents.getAllUnits())
                    if (le instanceof WorkerUnit && le instanceof Unit u && bot.equals(u.getOwnerName()) && le.isAlive())
                        workers++;
                if (workers == 0)
                    return "setup: none of the bot's three Grunts is in the unit list";
            }
            int shift = brain.techSlotShift;
            brain.think(level, level.getGameTime());
            thinks++;
            if (brain.techSlotShift != shift)
                t2Misses++;   // placeTechBuilding moves the slot on only when the T2 lab found no site
            BuildingPlacement order = t2LabOrder();
            if (order != null) {
                ReignOfNether.LOGGER.info("[T2Site] the bot ordered its {} at {} ({} blocks from home) on think {}",
                    kit.t2Lab().name, order.originPos.toShortString(), (int) Math.sqrt(order.originPos.distSqr(home)), thinks);
                return "";
            }
            if (t + THINK_EVERY <= THINK_TICKS)
                return null;
            return "the medium Horde bot never ordered its " + kit.t2Lab().name + " in " + thinks + " thinks ("
                + thinks * THINK_EVERY / 20 + " s) though a valid site stands on its own netherrack at " + farSite.toShortString()
                + ", " + (int) Math.sqrt(farSite.distSqr(home)) + " blocks from home " + home.toShortString()
                + " (beyond the layout search's " + SEARCH_REACH + "): its T2 lab found no site on " + t2Misses
                + " thinks, techSlotShift " + brain.techSlotShift + ", placeFailures " + brain.placeFailures
                + ", lastBlockedReason '" + brain.lastBlockedReason + "', T2 lab placements: "
                + count(Blueprints.pendingOf(bot), false) + " pending, " + count(BuildingServerEvents.getBuildings(), true) + " registered";
        }

        void finish(GameTestHelper helper, String error) {
            cleanUp();
            if (error == null)
                helper.succeed();
            else
                helper.fail(error);
        }

        /** Everything back as it was found: orders, buildings, units, pool, research, pace, the pad and the chunk tickets. */
        void cleanUp() {
            if (cleaned)
                return;
            cleaned = true;
            for (BuildingPlacement bp : Blueprints.pendingOf(bot))
                Blueprints.cancel(bp, null);
            Map<BuildingPlacement, Boolean> ours = new IdentityHashMap<>();
            for (BuildingPlacement bp : stamped)
                ours.put(bp, Boolean.TRUE);
            List<BuildingPlacement> owned = new ArrayList<>();
            for (BuildingPlacement bp : BuildingServerEvents.getBuildings())
                if (bot.equals(bp.ownerName))
                    owned.add(bp);
            for (BuildingPlacement bp : owned) {
                BuildingServerEvents.getBuildings().remove(bp);
                if (!ours.containsKey(bp))
                    bp.forceChunk(false);   // a site the bot raised itself forced its chunk; the stamps never did
            }
            for (LivingEntity le : new ArrayList<>(UnitServerEvents.getAllUnits()))
                if (le instanceof Unit u && bot.equals(u.getOwnerName()))
                    le.discard();
            made.forEach(Entity::discard);
            if (pool != null)
                ResourcesServerEvents.resourcesList.remove(pool);
            ResearchServerEvents.removeAllResearchFor(bot);
            EconomyPace.setTestOverride(bot, null);
            BotServerEvents.brains.remove(bot);
            BotServerEvents.thinkFailures.remove(bot);
            if (padLaid) {
                BlockState air = Blocks.AIR.defaultBlockState();
                for (BuildingPlacement bp : owned)
                    for (int x = bp.minCorner.getX(); x <= bp.maxCorner.getX(); x++)
                        for (int y = bp.minCorner.getY(); y <= bp.maxCorner.getY() + 1; y++)
                            for (int z = bp.minCorner.getZ(); z <= bp.maxCorner.getZ(); z++)
                                level.setBlock(new BlockPos(x, y, z), air, 2);
                for (int x = x0; x <= x1; x++)
                    for (int z = z0; z <= z1; z++) {
                        level.setBlock(new BlockPos(x, PAD_Y - 1, z), air, 2);
                        level.setBlock(new BlockPos(x, PAD_Y, z), air, 2);
                    }
            }
            GameTestChunks.releaseAll(level, chunks);
        }
    }
}
