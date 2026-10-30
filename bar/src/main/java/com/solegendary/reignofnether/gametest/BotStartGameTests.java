package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * A bot's three start workers (PlayerServerEvents.spawnBotStartUnits) stand on dry ground. Each is dropped on the
 * column one block apart along x from the start, onto whatever its top block is - and getHighestNonAirBlock stops at a
 * fluid, so a lava pool beside the seat took a worker before the bot's first think: build run 40632885850, the soak
 * bots_play_each_other_without_errors failed "the Verdant bot should start with 3 Seedshapers, got 2" with "court
 * seedshaper_unit died t10 lava at 1701, 71, 328" (start 1700, 72, 328). The soak's dry-seat scan samples only nine
 * points 12 blocks apart, so a small pool next to the seat passes it.
 * <p>
 * Own holder; a pad of its own high over the arena (nothing else is built at y 250 here).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BotStartGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /**
     * A Verdant start beside a 2x2 lava pool that covers the columns the spawn gives two of its three Seedshapers: all
     * three must join, stay alive and stand out of the lava and the fire 60 ticks later.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void bot_start_workers_never_spawn_over_lava(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_start_lava_" + Long.toHexString(helper.absolutePos(BlockPos.ZERO).asLong());
        BlockPos centre = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 250;
        Vec3 start = new Vec3(centre.getX() + 0.5, y + 1, centre.getZ() + 0.5);
        // the columns spawnBotStartUnits gives its units are (int) (x + i) for i = -1, 0, 1 and (int) z
        int lavaX = (int) (start.x + 1), lavaZ = (int) start.z;
        List<BlockPos> placed = new ArrayList<>();
        for (int dx = -8; dx <= 8; dx++)
            for (int dz = -8; dz <= 8; dz++) {
                BlockPos below = new BlockPos(centre.getX() + dx, y - 1, centre.getZ() + dz);
                BlockPos top = below.above();
                level.setBlock(below, Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(top, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                placed.add(below);
                placed.add(top);
            }
        // the pool: the third unit's column and its neighbour toward the start, one deep, walled by the pad
        for (int px = lavaX - 1; px <= lavaX; px++)
            for (int pz = lavaZ - 1; pz <= lavaZ; pz++)
                level.setBlock(new BlockPos(px, y, pz), Blocks.LAVA.defaultBlockState(), 2);
        int added = PlayerServerEvents.spawnBotStartUnits(level, owner, Factions.VERDANT_COURT, start, false);
        List<LivingEntity> units = new ArrayList<>();
        Runnable cleanup = () -> {
            for (LivingEntity le : UnitServerEvents.getAllUnits())
                if (le instanceof Unit u && owner.equals(u.getOwnerName()))
                    units.add(le);
            units.forEach(Entity::discard);
            for (BlockPos p : placed)
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        };
        if (added != 3) {
            cleanup.run();
            helper.fail("spawnBotStartUnits added " + added + " of 3 Seedshapers");
            return;
        }
        List<LivingEntity> seen = new ArrayList<>();
        helper.runAfterDelay(5, () -> {
            for (LivingEntity le : UnitServerEvents.getAllUnits())
                if (le instanceof Unit u && owner.equals(u.getOwnerName()))
                    seen.add(le);
        });
        helper.runAfterDelay(60, () -> {
            List<String> problems = new ArrayList<>();
            if (seen.size() != 3)
                problems.add(seen.size() + " of 3 Seedshapers joined the unit list");
            for (LivingEntity le : seen) {
                String at = le.blockPosition().toShortString() + " (lava at x " + (lavaX - 1) + ".." + lavaX + ", z "
                    + (lavaZ - 1) + ".." + lavaZ + ")";
                if (!le.isAlive())
                    problems.add("a Seedshaper died at " + at);
                else if (le.isInLava() || le.getRemainingFireTicks() > 0)
                    problems.add("a Seedshaper stands in lava or burns at " + at);
            }
            cleanup.run();
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        });
    }
}
