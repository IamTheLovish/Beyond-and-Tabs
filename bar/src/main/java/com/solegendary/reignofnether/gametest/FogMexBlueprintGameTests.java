package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingValidators;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.resources.MetalPatches;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Extractor blueprints on a known patch under fog (live bug: "fog of war prevents blueprints from being dropped on
 * mexes"). The fog snapshot a player is served for never-explored chunks is taken when the first side is seated
 * (FogChunkSnapshot.captureFogChunks from PlayerServerEvents.startRTS), but the battlefield's patches are stamped
 * a few ticks later (BattlefieldSetup, StartAreaClearing.stampPatch). So under that fog the client's blocks are the
 * ground from before the stamp: no patch blocks and no levelled pad, while the synced patch list (MetalPatchesClient,
 * the minimap and the ghost's snap) knows the patch is there. The validator read only the blocks and called the spot
 * "needs a metal patch" or "not flat", so the ghost stayed red and the click did nothing.
 * <p>
 * The client's view is modelled on the server (BuildingValidators.testServerModelsClientView): a patch centre in the
 * patch list, the blocks under it put back to pre-stamp ground, and an owner with no player, so the footprint is not
 * in the owner's vision (as under fog). Without the hook the server judges its real ground, as it must.
 * The server re-checks the real ground when the fog-queued worker arrives, so the validator only has to trust the
 * patch list while the blocks it reads cannot show the patch.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class FogMexBlueprintGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "fog_mex_blueprint";
    static final int PAD_R = 6, PAD_H = 6;
    static final Building EXTRACTOR = Buildings.METAL_EXTRACTOR_VILLAGERS;

    /** A stone sky pad with a known patch centre at its middle; {@code staleGround} leaves the patch blocks out. */
    static final class Scene {
        final ServerLevel level;
        final String owner;
        final BlockPos centre;
        final boolean known;

        Scene(GameTestHelper helper, String tag, int padY, boolean known, boolean staleGround) {
            level = helper.getLevel();
            owner = "gametest_fogmex_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
            BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
            centre = new BlockPos(base.getX(), padY, base.getZ());
            this.known = known;
            CombatEngineerGameTests.skyPad(level, centre.getX(), padY, centre.getZ(), PAD_R, PAD_H, false);
            if (!staleGround)
                for (int[] d : new int[][]{ {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1} })
                    level.setBlock(centre.offset(d[0], 0, d[1]), MetalPatches.PATCH_BLOCK.defaultBlockState(), 2);
            if (known)
                MetalPatches.getPatches(level).add(centre);
        }

        BlockPos origin() {
            return centre.offset(-2, 0, -2);
        }

        /** The validator as the client calls it for the ghost and the click (ignoreFog: the server fog-queues). */
        String error() {
            return BuildingValidators.getPlacementValidityError(level, EXTRACTOR, origin(), owner, Rotation.NONE,
                false, false, true);
        }

        /**
         * Digs the footprint's first and last rows out of the pad: 10 of its 25 columns hang over the drop, more than
         * the validator's cliff share, so the ground reads as not flat (the patch blocks are in the middle rows).
         */
        void digRows() {
            for (int x = 0; x <= 4; x++)
                for (int z : new int[]{ 0, 4 })
                    level.setBlock(origin().offset(x, 0, z), Blocks.AIR.defaultBlockState(), 2);
        }

        void undo() {
            if (known)
                MetalPatches.getPatches(level).remove(centre);
            CombatEngineerGameTests.skyPad(level, centre.getX(), centre.getY(), centre.getZ(), PAD_R, PAD_H, true);
        }
    }

    static void expect(GameTestHelper helper, Scene s, String expected, String what) {
        String error;
        try {
            BuildingValidators.testServerModelsClientView = true;
            error = s.error();
        } finally {
            BuildingValidators.testServerModelsClientView = false;
            s.undo();
        }
        if (expected == null ? error != null : !expected.equals(error))
            helper.fail(what + ": expected " + expected + ", the validator said " + error);
        else
            helper.succeed();
    }

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 100)
    public static void a_fogged_known_patch_takes_an_extractor_blueprint(GameTestHelper helper) {
        // the pre-stamp ground was flat: only the patch blocks are missing from the client's view
        Scene s = new Scene(helper, "flat", 205, true, true);
        expect(helper, s, null, "extractor on a known patch whose blocks the fog view does not show");
    }

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 100)
    public static void a_fogged_known_patch_on_unlevelled_ground_takes_an_extractor_blueprint(GameTestHelper helper) {
        // the stamp levels a 7x7 pad; under fog the client still has the ground from before it, here a drop
        Scene s = new Scene(helper, "rough", 215, true, true);
        s.digRows();
        expect(helper, s, null, "extractor on a known patch over the fog view's unlevelled ground");
    }

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 100)
    public static void fogged_ground_with_no_known_patch_still_needs_one(GameTestHelper helper) {
        Scene s = new Scene(helper, "none", 225, false, true);
        expect(helper, s, "building.reignofnether.needs_metal_patch", "extractor on fogged ground with no patch");
    }

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 100)
    public static void the_server_still_reads_the_real_ground_under_a_listed_patch(GameTestHelper helper) {
        // a patch-list entry with no patch blocks, out of a bot owner's vision: the server's own check (an arrival
        // re-check, a bot's placement) must not trust the list over the ground it really has
        Scene s = new Scene(helper, "server", 245, true, true);
        String error;
        try {
            error = s.error();
        } finally {
            s.undo();
        }
        if (!"building.reignofnether.needs_metal_patch".equals(error))
            helper.fail("server check on a listed patch with no blocks: expected needs_metal_patch, the validator said " + error);
        else
            helper.succeed();
    }

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 100)
    public static void a_patch_the_view_does_show_is_still_checked_for_flat_ground(GameTestHelper helper) {
        // the blocks show the patch (a seen or visible patch): the view is not stale, so its ground is read as before
        Scene s = new Scene(helper, "shown", 235, true, false);
        s.digRows();
        expect(helper, s, "building.reignofnether.ground_not_flat", "extractor on a shown patch over a drop");
    }
}
