package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.bot.BotPlayer;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingValidators;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.FactionTraits;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.resources.MetalPatches;
import com.solegendary.reignofnether.startpos.BattlefieldSetup;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "no valid footprint over the patch" (CI, the bot skirmish's T2 timing test): the easy bot's log showed it failing to
 * place metal extractors for minutes, hard and medium too. This forms a real battlefield the way a match does - three
 * bases in the generated overworld, the economy stamped round them - and asks the placement validator, for every faction's
 * extractor, whether every stamped patch takes one at the origins the bot tries (BotPlayer.EXTRACTOR_OFFSETS) and at
 * the origin a player's ghost uses (centre - 2). No building stands yet, so any refusal is the patch itself (its
 * pad, its neighbours, the wall), not a rival building. Own batch: it forms a battlefield and builds a wall.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class ExtractorFootprintGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** Ticks the battlefield gets to form (BattlefieldSetup settles for 60) and to be stamped before the patches are read. */
    static final int FORM_TICKS = 400;

    /** How far from one of its bases a battlefield stamps patches (the far lane patches sit about 125 out). */
    static final int BATTLEFIELD_REACH = 400;

    @GameTest(template = ARENA, batch = "extractor_footprint", timeoutTicks = FORM_TICKS + 200)
    public static void every_stamped_patch_takes_every_factions_extractor_where_the_bot_looks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        // z far from the soak and the T2 test's rows (they sit at origin z), so no other test's patches or buildings are near;
        // the full scan of the soak (16 steps x 8 lines), as the T2 test does: one strip is often water or cliff
        Vec3[] starts = SkirmishGameTests.soakAnyRow(level, origin.getX() + 1500, origin.getZ() + 6000, 3);
        // the whole scan can still be sea or cliff (flake-stress 40652936389: 2 of 8 seeds) - then the other side
        if (starts == null)
            starts = SkirmishGameTests.soakAnyRow(level, origin.getX() - 1500, origin.getZ() + 6000, 3);
        if (starts == null) {
            helper.fail("no dry ground for three bases near x=" + (origin.getX() + 1500) + " or x=" + (origin.getX() - 1500)
                + " (z=" + (origin.getZ() + 6000) + ")");
            return;
        }
        MetalPatches.forgetAll();
        BattlefieldSetup.discardPending();
        BattlefieldSetup.expectFormation = true;
        List<BlockPos> startPositions = new ArrayList<>();
        for (Vec3 v : starts) {
            BlockPos s = BlockPos.containing(v);
            startPositions.add(s);
            BattlefieldSetup.onStart(level, s);
        }
        helper.runAfterDelay(FORM_TICKS, () -> {
            List<BlockPos> patches;
            List<BlockPos> live = MetalPatches.getPatches(level);
            synchronized (live) {
                patches = new ArrayList<>(live);
            }
            // this battlefield's patches only: in a full CI shard another batch's test stamps its own (and builds on them
            // - CI 40614999963 judged patches 6,500 blocks off, under that test's walls)
            patches.removeIf(c -> startPositions.stream().noneMatch(s -> s.distSqr(c) <= BATTLEFIELD_REACH * BATTLEFIELD_REACH));
            try {
                if (patches.isEmpty()) {
                    helper.fail("no patches were stamped in " + FORM_TICKS + " ticks");
                    return;
                }
                // run alone (flake-stress) nothing holds the battlefield, so its chunks may have unloaded by now and
                // every patch would read "not_loaded": bring each footprint's chunks back (the save keeps the terrain)
                for (BlockPos c : patches)
                    for (int dx = -6; dx <= 6; dx += 12)
                        for (int dz = -6; dz <= 6; dz += 12)
                            GameTestChunks.loadNowOrFail(level,
                                (c.getX() + dx) >> 4, (c.getZ() + dz) >> 4);
                String problem = check(level, patches, startPositions);
                if (problem == null)
                    helper.succeed();
                else
                    helper.fail(problem);
            } catch (RuntimeException e) {
                helper.fail("check threw " + e);
            } finally {
                MetalPatches.forgetAll();
                BattlefieldSetup.discardPending();
            }
        });
    }

    /**
     * The cause of the last "pad WET" refusals: MetalPatches.drySpot read solidTop before anything had loaded the chunk,
     * Level.getHeight answers the min build height there, and padDry at y -64 said "dry" - then the pad was stamped on
     * the lake bed under the water, which the leveller leaves alone. solidTop must read real ground in an unloaded chunk.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void solid_top_reads_real_ground_in_an_unloaded_chunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        // far from every test's ground: a chunk no one has loaded yet (stepping on until one is found)
        int x = origin.getX() + 1500, z = origin.getZ() - 9000;
        for (int k = 0; k < 64 && level.getChunkSource().getChunkNow(x >> 4, z >> 4) != null; k++)
            z -= 64;
        if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) != null) {
            helper.fail("no unloaded chunk found near x=" + x);
            return;
        }
        // generate it through the bounded wait (a synchronous getChunk of new ground could park the server thread on CI),
        // read the ground, then let its one-tick ticket lapse and read again once it has unloaded (back from the save)
        GameTestChunks.loadNowOrFail(level, x >> 4, z >> 4);
        int loaded = MetalPatches.solidTop(level, x, z);
        if (loaded <= level.getMinBuildHeight()) {
            helper.fail("solidTop at [" + x + ", " + z + "] read " + loaded + " with its chunk loaded: no ground to compare");
            return;
        }
        awaitUnloadThenCompare(helper, level, x, z, loaded, 150);
    }

    private static void awaitUnloadThenCompare(GameTestHelper helper, ServerLevel level, int x, int z, int loaded, int ticksLeft) {
        if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) != null) {
            if (ticksLeft <= 0)
                helper.fail("chunk " + (x >> 4) + "," + (z >> 4) + " never unloaded after its ticket lapsed");
            else
                helper.runAfterDelay(1, () -> awaitUnloadThenCompare(helper, level, x, z, loaded, ticksLeft - 1));
            return;
        }
        int unloaded = MetalPatches.solidTop(level, x, z);
        if (unloaded != loaded)
            helper.fail("solidTop at [" + x + ", " + z + "] read " + unloaded + " with its chunk unloaded, " + loaded + " loaded");
        else
            helper.succeed();
    }

    /**
     * A bamboo jungle (local run of the battlefield test: "pad dry", every obstruction "+9 bamboo"): the pad clears 8 up,
     * the stalks above stay standing in the extractor's footprint and it clips them. A platform high above the terrain
     * (as SkirmishGameTests.extractor_and_windmill_fit_on_a_stamped_patch) with bamboo 16 tall, then the player's origin.
     */
    @GameTest(template = ARENA)
    public static void a_stamped_patch_clears_bamboo_taller_than_the_pad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 220;
        int cx = base.getX(), cz = base.getZ();
        // the platform, what grows on it and what the stamp cuts (above the arena) are put back when the test ends,
        // pass or fail: the next test at this slot stands units at 228-240 (HeroGameTests' possession strips)
        TestCleanup.blocks(helper).region(new BlockPos(cx - 8, y - 1, cz - 8), new BlockPos(cx + 8, y + 20, cz + 8));
        for (int dx = -8; dx <= 8; dx++)
            for (int dz = -8; dz <= 8; dz++) {
                level.setBlock(new BlockPos(cx + dx, y - 1, cz + dz), net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), 3);
                level.setBlock(new BlockPos(cx + dx, y, cz + dz), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                if ((dx + dz) % 3 == 0)
                    for (int k = 1; k <= 16; k++)
                        level.setBlock(new BlockPos(cx + dx, y + k, cz + dz), net.minecraft.world.level.block.Blocks.BAMBOO.defaultBlockState(), 2);
            }
        MetalPatches.forgetAll();
        try {
            MetalPatches.stamp(level, cx, cz);
            BlockPos centre = new BlockPos(cx, y, cz);
            if (!level.getBlockState(centre).is(MetalPatches.PATCH_BLOCK)) {
                helper.fail("stamp() did not leave a patch block at " + centre + " (solidTop says " + MetalPatches.solidTop(level, cx, cz) + ")");
                return;
            }
            List<String> refused = new ArrayList<>();
            for (ResourceLocation key : new ArrayList<>(Factions.PLAYABLE_FACTIONS)) {
                Faction f = Factions.getFaction(key);
                Building e = Factions.isLive(f) ? FactionTraits.of(f).metalExtractor.get() : null;
                if (e == null)
                    continue;
                String err = BuildingValidators.getPlacementValidityError(level, e, centre.offset(-2, 0, -2), "gametest_footprint",
                    Rotation.NONE, false, false, true);
                if (err != null)
                    refused.add(e.structureName + " " + err);
            }
            if (refused.isEmpty())
                helper.succeed();
            else
                helper.fail("a patch stamped in bamboo 16 tall refuses: " + refused + " (pad " + obstructions(level, centre) + ")");
        } finally {
            MetalPatches.forgetAll();
        }
    }

    /**
     * A patch cut into a hillside (flake-stress 40609781800: "pad dry (rim +9)", every obstruction "+9 grass_block" or
     * "+9 dirt_path"): the pad clears 8 up, the hill's top stays as a roof over the footprint and the extractor clips it.
     * A platform high above the terrain with a mound 12 tall over two rows of the pad, then the player's origin.
     */
    @GameTest(template = ARENA)
    public static void a_stamped_patch_clears_a_hill_taller_than_the_pad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 230;
        int cx = base.getX(), cz = base.getZ();
        // the platform, what grows on it and what the stamp cuts (above the arena) are put back when the test ends,
        // pass or fail: the next test at this slot stands units at 228-240 (HeroGameTests' possession strips)
        TestCleanup.blocks(helper).region(new BlockPos(cx - 8, y - 1, cz - 8), new BlockPos(cx + 8, y + 20, cz + 8));
        for (int dx = -8; dx <= 8; dx++)
            for (int dz = -8; dz <= 8; dz++) {
                level.setBlock(new BlockPos(cx + dx, y - 1, cz + dz), net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), 3);
                level.setBlock(new BlockPos(cx + dx, y, cz + dz), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                if (dz <= -2)   // the hill: dirt up to +11, grass on top
                    for (int k = 1; k <= 12; k++)
                        level.setBlock(new BlockPos(cx + dx, y + k, cz + dz), (k == 12 ? net.minecraft.world.level.block.Blocks.GRASS_BLOCK
                            : net.minecraft.world.level.block.Blocks.DIRT).defaultBlockState(), 2);
            }
        MetalPatches.forgetAll();
        try {
            MetalPatches.stamp(level, cx, cz);
            BlockPos centre = new BlockPos(cx, y, cz);
            if (!level.getBlockState(centre).is(MetalPatches.PATCH_BLOCK)) {
                helper.fail("stamp() did not leave a patch block at " + centre + " (solidTop says " + MetalPatches.solidTop(level, cx, cz) + ")");
                return;
            }
            List<String> refused = new ArrayList<>();
            for (ResourceLocation key : new ArrayList<>(Factions.PLAYABLE_FACTIONS)) {
                Faction f = Factions.getFaction(key);
                Building e = Factions.isLive(f) ? FactionTraits.of(f).metalExtractor.get() : null;
                if (e == null)
                    continue;
                String err = BuildingValidators.getPlacementValidityError(level, e, centre.offset(-2, 0, -2), "gametest_footprint",
                    Rotation.NONE, false, false, true);
                if (err != null)
                    refused.add(e.structureName + " " + err);
            }
            if (refused.isEmpty())
                helper.succeed();
            else
                helper.fail("a patch stamped against a hill 12 tall refuses: " + refused + " (pad " + obstructions(level, centre) + ")");
        } finally {
            MetalPatches.forgetAll();
        }
    }

    /**
     * MultiplayerLobby.siteOk had solidTop's bug: Level.getHeight of an unloaded chunk is the min build height, so a site
     * whose chunks no one had loaded read "dry and flat" at y -64 wherever it was. Once solidTop loaded the centre chunk the
     * lobby tests' probes mixed real ground with -64 and found "no dry ground" (CI 40614999963). A pond on the site, the
     * chunks left to unload, then siteOk must still see the water.
     */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void site_ok_reads_real_ground_in_unloaded_chunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        // a chunk centre far from every test's ground, so siteOk's probes (+-12) span 3x3 chunks no one else holds
        int x = ((origin.getX() + 1500) & ~15) + 8, z = ((origin.getZ() - 12000) & ~15) + 8;
        List<long[]> chunks = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                chunks.add(new long[] { (x >> 4) + dx, (z >> 4) + dz });
        for (long[] c : chunks)
            GameTestChunks.loadNowOrFail(level, (int) c[0], (int) c[1]);
        int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        if (top <= level.getMinBuildHeight()) {
            helper.fail("no ground at [" + x + ", " + z + "] with its chunk loaded");
            return;
        }
        level.setBlock(new BlockPos(x, top, z), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), 2);
        if (com.solegendary.reignofnether.matchstart.MultiplayerLobby.siteOk(level, x, z)) {
            helper.fail("siteOk at [" + x + ", " + z + "] took a site with water at its centre, chunks loaded");
            return;
        }
        awaitUnloadThenCheckSite(helper, level, x, z, chunks, 250);
    }

    private static void awaitUnloadThenCheckSite(GameTestHelper helper, ServerLevel level, int x, int z, List<long[]> chunks, int ticksLeft) {
        for (long[] c : chunks)
            if (level.getChunkSource().getChunkNow((int) c[0], (int) c[1]) != null) {
                if (ticksLeft <= 0)
                    helper.fail("chunk " + c[0] + "," + c[1] + " never unloaded after its ticket lapsed");
                else
                    helper.runAfterDelay(1, () -> awaitUnloadThenCheckSite(helper, level, x, z, chunks, ticksLeft - 1));
                return;
            }
        if (com.solegendary.reignofnether.matchstart.MultiplayerLobby.siteOk(level, x, z))
            helper.fail("siteOk at [" + x + ", " + z + "] took a site with water at its centre once its chunks had unloaded");
        else
            helper.succeed();
    }

    /** Each unplaceable patch with the errors it gave, per extractor; null when every patch takes every extractor. */
    private static String check(ServerLevel level, List<BlockPos> patches, List<BlockPos> starts) {
        List<Building> extractors = new ArrayList<>();
        for (ResourceLocation key : new ArrayList<>(Factions.PLAYABLE_FACTIONS)) {
            Faction f = Factions.getFaction(key);
            if (!Factions.isLive(f))
                continue;
            Building e = FactionTraits.of(f).metalExtractor.get();
            if (e != null && !extractors.contains(e))
                extractors.add(e);
        }
        List<String> bad = new ArrayList<>();
        for (BlockPos c : patches) {
            Map<String, String> errors = new LinkedHashMap<>();
            boolean playerOk = true;
            for (Building e : extractors) {
                String botErr = null;
                boolean botOk = false;
                for (int dy : BotPlayer.EXTRACTOR_DY) {
                    for (int[] o : BotPlayer.EXTRACTOR_OFFSETS) {
                        String err = BuildingValidators.getPlacementValidityError(level, e,
                            new BlockPos(c.getX() + o[0], c.getY() + dy, c.getZ() + o[1]), "gametest_footprint",
                            Rotation.NONE, false, false, true);
                        if (err == null)
                            botOk = true;
                        else if (botErr == null)
                            botErr = err;
                    }
                }
                String playerErr = BuildingValidators.getPlacementValidityError(level, e, c.offset(-2, 0, -2),
                    "gametest_footprint", Rotation.NONE, false, false, true);
                if (playerErr != null)
                    playerOk = false;
                if (!botOk || playerErr != null)
                    errors.put(e.structureName, (botOk ? "bot ok" : "bot " + botErr) + ", player " + (playerErr == null ? "ok" : playerErr));
            }
            if (!errors.isEmpty()) {
                int nearest = Integer.MAX_VALUE;
                for (BlockPos s : starts)
                    nearest = Math.min(nearest, (int) Math.sqrt(s.distSqr(c)));
                int neighbour = Integer.MAX_VALUE;
                for (BlockPos o : patches)
                    if (!o.equals(c))
                        neighbour = Math.min(neighbour, Math.max(Math.abs(o.getX() - c.getX()), Math.abs(o.getZ() - c.getZ())));
                bad.add(c.toShortString() + " (" + nearest + " from a base, nearest patch " + neighbour + ", pad "
                    + (MetalPatches.padDry(level, c.getX(), c.getZ(), c.getY()) ? "dry " + obstructions(level, c) : "WET " + wetCells(level, c)) + ", player " + (playerOk ? "ok" : "refused")
                    + "): " + errors);
            }
        }
        if (bad.isEmpty())
            return null;
        return bad.size() + " of " + patches.size() + " patches refuse an extractor: " + String.join(" | ", bad);
    }

    /**
     * A dry pad that still refuses: each pad cell whose ground is not at the pad's height or that has a solid block within
     * 16 above it, as (dx,dz ground dy, first solid +k block), plus the highest ground round the pad (a pit's rim).
     */
    private static String obstructions(ServerLevel level, BlockPos c) {
        List<String> cells = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int rim = Integer.MIN_VALUE;
        for (int dx = -MetalPatches.PAD_HALF - 3; dx <= MetalPatches.PAD_HALF + 3; dx++)
            for (int dz = -MetalPatches.PAD_HALF - 3; dz <= MetalPatches.PAD_HALF + 3; dz++) {
                int x = c.getX() + dx, z = c.getZ() + dz;
                if (Math.max(Math.abs(dx), Math.abs(dz)) > MetalPatches.PAD_HALF) {
                    rim = Math.max(rim, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1 - c.getY());
                    continue;
                }
                String ground = level.getBlockState(p.set(x, c.getY(), z)).isSolid() ? "" : " ground "
                    + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).getPath();
                String above = "";
                for (int k = 1; k <= 16; k++)
                    if (level.getBlockState(p.set(x, c.getY() + k, z)).isSolid()) {
                        above = " +" + k + " " + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).getPath();
                        break;
                    }
                if (!ground.isEmpty() || !above.isEmpty())
                    cells.add(dx + "," + dz + ground + above);
            }
        return "(rim +" + rim + ") " + cells;
    }

    /** The pad's wet cells as (dx,dz,dy=0|1 source|flowing amount, block below), so a refusal says how the water got there. */
    private static String wetCells(ServerLevel level, BlockPos c) {
        List<String> cells = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -MetalPatches.PAD_HALF; dx <= MetalPatches.PAD_HALF; dx++)
            for (int dz = -MetalPatches.PAD_HALF; dz <= MetalPatches.PAD_HALF; dz++)
                for (int dy = 0; dy <= 1; dy++) {
                    var fluid = level.getFluidState(p.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz));
                    if (fluid.isEmpty())
                        continue;
                    String below = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .getKey(level.getBlockState(p.move(0, -1, 0)).getBlock()).getPath();
                    cells.add(dx + "," + dz + "," + dy + (fluid.isSource() ? " src" : " flow" + fluid.getAmount()) + "/" + below);
                }
        return cells.toString();
    }
}
