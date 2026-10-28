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

    @GameTest(template = ARENA, batch = "extractor_footprint", timeoutTicks = FORM_TICKS + 200)
    public static void every_stamped_patch_takes_every_factions_extractor_where_the_bot_looks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        // z far from the soak and the T2 test's rows (they sit at origin z), so no other test's patches or buildings are near;
        // the full scan of the soak (16 steps x 8 lines), as the T2 test does: one strip is often water or cliff
        Vec3[] starts = SkirmishGameTests.soakAnyRow(level, origin.getX() + 1500, origin.getZ() + 6000, 3);
        if (starts == null) {
            helper.fail("no dry ground for three bases near x=" + (origin.getX() + 1500));
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
        int unloaded = MetalPatches.solidTop(level, x, z);
        GameTestChunks.loadNowOrFail(level, x >> 4, z >> 4);
        int loaded = MetalPatches.solidTop(level, x, z);
        if (unloaded != loaded)
            helper.fail("solidTop at [" + x + ", " + z + "] read " + unloaded + " with its chunk unloaded, " + loaded + " loaded");
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
                    + (MetalPatches.padDry(level, c.getX(), c.getZ(), c.getY()) ? "dry" : "WET " + wetCells(level, c)) + ", player " + (playerOk ? "ok" : "refused")
                    + "): " + errors);
            }
        }
        if (bad.isEmpty())
            return null;
        return bad.size() + " of " + patches.size() + " patches refuse an extractor: " + String.join(" | ", bad);
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
