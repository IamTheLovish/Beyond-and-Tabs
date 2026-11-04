package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

import static com.solegendary.reignofnether.gametest.TerrainGameTests.*;

/**
 * N3 (BAC-016, design/HANDOFF-lane-c.md C3): a mixed army ordered across a river reaches the far bank. The arena
 * stands in for a river map (a real map does not load in a GameTest): a 3-wide, 2-deep river across the 16x16 arena,
 * six units of three kinds on the near bank, one move order each to the far bank. Judged by where they end up: on a
 * loaded CI server the claim is the far bank, not the speed. Its own batch, so BAT_GAMETEST_ONLY=river_crossing runs
 * it alone.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class RiverCrossingGameTests {

    static final int RIVER_Z0 = 6, RIVER_Z1 = 8, FAR_BANK_Z = 10;

    @GameTest(template = SkirmishGameTests.ARENA, batch = "river_crossing", timeoutTicks = 900)
    public static void a_mixed_army_crosses_a_river_to_the_far_bank(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        // the river: water two blocks deep in the ground (y 0-1) from bank to bank, open air above
        for (int x = 0; x <= 15; x++)
            for (int z = RIVER_Z0; z <= RIVER_Z1; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.WATER);
                helper.setBlock(new BlockPos(x, 1, z), Blocks.WATER);
            }
        List<Entity> made = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        String mine = owner(helper, "river_a");
        List<EntityType<? extends Mob>> kinds = List.of(EntityRegistrar.VINDICATOR_UNIT.get(),
            EntityRegistrar.PILLAGER_UNIT.get(), EntityRegistrar.VILLAGER_UNIT.get());
        List<Mob> army = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Mob m = spawn(level, kinds.get(i % 3), mine, x(helper, 2 + i * 2), y(helper, 2), z(helper, 2), made);
            if (m != null) {
                m.setInvulnerable(true);
                army.add(m);
            }
        }
        if (army.size() != 6) {
            cleanUp(made);
            helper.fail("could not create the army (" + army.size() + " of 6)");
            return;
        }
        int[] ordered = { -1 };
        boolean[] done = { false };
        ArenaReady.whenTicking(helper, made, 200, tick -> {
            ordered[0] = tick;
            for (int i = 0; i < army.size(); i++)
                ((Unit) army.get(i)).setMoveTarget(helper.absolutePos(new BlockPos(2 + i * 2, 2, 13)));
        });
        helper.onEachTick(() -> {
            if (ordered[0] < 0 || done[0])
                return;
            int since = (int) helper.getTick() - ordered[0];
            int across = 0;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < army.size(); i++) {
                Mob m = army.get(i);
                double rel = m.getZ() - helper.absolutePos(BlockPos.ZERO).getZ();
                if (rel >= FAR_BANK_Z && !m.isInWater())
                    across++;
                sb.append(i > 0 ? ", " : "").append(m.getType().getDescriptionId().replace("entity.reignofnether.", ""))
                    .append(" z=").append(f(rel));
            }
            if (across < army.size() && since < 600)
                return;
            done[0] = true;
            try {
                ReignOfNether.LOGGER.info("[River] {} of {} on the far bank {} ticks after the order: {}", across, army.size(), since, sb);
                if (across < army.size())
                    problems.add(across + " of " + army.size() + " units reached the far bank (z >= " + FAR_BANK_Z
                        + ", out of the water) in " + since + " ticks: " + sb);
                finish(helper, problems);
            } finally {
                cleanUp(made);
            }
        });
    }
}
