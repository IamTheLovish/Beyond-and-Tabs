package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.environment.light.LeafLight;
import com.solegendary.reignofnether.registrars.BlockRegistrar;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Crown lighting (environment/light/LeafLight, lovish Nov 6: "Crowns too dark: fix lighting"): every kind of tree's
 * leaves, placed in the world, shade their neighbours in smooth lighting by the porous leaf shade - asked through the
 * block state, as vanilla's renderer and Embeddium ask - while stone keeps vanilla's full-box 0.2, glass its 1.0 and the
 * mod's decayable wart blocks (leaves to the game, solid wart to the eye) the 0.2 of a solid block.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class LeafLightGameTests {

    /** The porous leaf shade's band (LeafLightTest holds the same): a leaf face inside a crown keeps at least half of
     * its light, and a crown's step still shows (an open top face stays brighter than one beside a rising leaf wall). */
    static final float LEAF_SHADE_MIN = 0.5F, LEAF_SHADE_MAX = 0.9F;

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 60)
    public static void leaves_let_the_sky_through_stone_and_wart_do_not(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8)).atY(170);
        List<Block> leaves = List.of(Blocks.OAK_LEAVES, Blocks.SPRUCE_LEAVES, Blocks.BIRCH_LEAVES, Blocks.JUNGLE_LEAVES,
            Blocks.ACACIA_LEAVES, Blocks.DARK_OAK_LEAVES, Blocks.MANGROVE_LEAVES, Blocks.CHERRY_LEAVES,
            Blocks.AZALEA_LEAVES, Blocks.FLOWERING_AZALEA_LEAVES);
        List<String> wrong = new ArrayList<>();
        int i = 0;
        for (Block b : leaves) {
            BlockPos p = base.offset(i++ * 2, 0, 0);
            BlockState s = b.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
            level.setBlock(p, s, 2);
            float shade = level.getBlockState(p).getShadeBrightness(level, p);
            if (!(shade >= LEAF_SHADE_MIN && shade <= LEAF_SHADE_MAX) || shade != LeafLight.LEAF_SHADE)
                wrong.add(b + " " + shade);
        }
        // the same block in the same world, the other side of the rule: solid (0.2), clear (1.0), wart (solid to the eye)
        Object[][] keep = {
            {Blocks.STONE, 0.2F}, {Blocks.OAK_LOG, 0.2F}, {Blocks.GLASS, 1.0F}, {Blocks.AIR, 1.0F},
            {BlockRegistrar.DECAYABLE_NETHER_WART_BLOCK.get(), 0.2F},
            {BlockRegistrar.DECAYABLE_WARPED_WART_BLOCK.get(), 0.2F}};
        for (Object[] k : keep) {
            BlockPos p = base.offset(i++ * 2, 0, 0);
            Block b = (Block) k[0];
            BlockState s = b instanceof LeavesBlock ? b.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true)
                : b.defaultBlockState();
            level.setBlock(p, s, 2);
            float shade = level.getBlockState(p).getShadeBrightness(level, p);
            if (shade != (Float) k[1])
                wrong.add(b + " " + shade + " (want vanilla " + k[1] + ")");
        }
        for (int j = 0; j < i; j++)
            level.setBlock(base.offset(j * 2, 0, 0), Blocks.AIR.defaultBlockState(), 2);
        if (!wrong.isEmpty()) {
            helper.fail("leaf AO shade (want leaves " + LEAF_SHADE_MIN + "-" + LEAF_SHADE_MAX + " = LeafLight.LEAF_SHADE "
                + LeafLight.LEAF_SHADE + ", the rest vanilla): " + wrong);
            return;
        }
        helper.succeed();
    }
}
