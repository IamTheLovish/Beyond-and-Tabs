package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.bot.BotPlayer;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.resources.MetalPatches;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * A bot never picks a metal patch outside its play area. Metal patches are one list per dimension, so on a server
 * running many bases side by side a bot whose nearby patch failed walked to the next-nearest patch in the world - a
 * neighbouring test's base 160 blocks on, and on, out to 3.5 km (bots_reach_tier_2, run 42165929068 shard 2: "hard
 * (HARD villagers) never finished its T2 lab", extractor sites cancelled at 35, 67, 185, 339 ... 3,478 blocks from its
 * builders). Here a bot's play area is the 128-block square round its home; the only free patch in reach is 160 blocks
 * away, outside it (where a neighbour's base would be). freePatch must answer nothing (or a patch inside the area), never
 * that one. Its own batch: patches are global (MetalPatches.forgetAll after, as ExtractorFootprintGameTests does).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BotPlayAreaGameTests {

    static final int Y = 230, AREA_HALF = 64, NEIGHBOUR = 160, PAD = 6;

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 60, batch = "bot_play_area")
    public static void a_bot_never_picks_a_metal_patch_outside_its_play_area(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos c = helper.absolutePos(new BlockPos(8, 1, 8));
        BlockPos home = new BlockPos(c.getX(), Y, c.getZ());
        int px = home.getX(), pz = home.getZ() + NEIGHBOUR;   // a neighbour's patch, outside the area
        TestCleanup.blocks(helper).region(new BlockPos(px - PAD - 2, Y - 2, pz - PAD - 2), new BlockPos(px + PAD + 2, Y + 8, pz + PAD + 2));
        for (int dx = -PAD - 2; dx <= PAD + 2; dx++)
            for (int dz = -PAD - 2; dz <= PAD + 2; dz++) {
                level.setBlock(new BlockPos(px + dx, Y - 1, pz + dz), Blocks.DIRT.defaultBlockState(), 2);
                level.setBlock(new BlockPos(px + dx, Y, pz + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            }
        String owner = "gametest_play_area_" + helper.absolutePos(BlockPos.ZERO).asLong();
        try {
            BlockPos far = MetalPatches.stamp(level, px, pz, 1f);
            BotPlayer brain = new BotPlayer(owner, Factions.VILLAGERS, BotPlayer.Difficulty.HARD, home);
            brain.enemyFilter = o -> false;
            brain.playArea = new BoundingBox(home.getX() - AREA_HALF, Y - 64, home.getZ() - AREA_HALF,
                home.getX() + AREA_HALF, Y + 64, home.getZ() + AREA_HALF);
            BlockPos picked = brain.freePatch(level);
            ReignOfNether.LOGGER.info("[PlayArea] GameTest: neighbour's patch at {}, the bot's area +-{} round {}: freePatch picked {}",
                far, AREA_HALF, home.toShortString(), picked);
            if (far == null)
                helper.fail("could not stamp the neighbour's patch");
            else if (picked != null && !brain.inPlayArea(picked))
                helper.fail("the bot picked the metal patch at " + picked.toShortString() + ", " + (int) Math.sqrt(picked.distSqr(home))
                    + " blocks from its home and outside its play area (+-" + AREA_HALF + " round " + home.toShortString() + ")");
            else
                helper.succeed();
        } finally {
            MetalPatches.forgetAll();
        }
    }
}
