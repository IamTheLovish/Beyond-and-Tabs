package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.player.CommanderServerEvents;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Commander health is twice (worker base + the commander bonus) (Joseph, Oct 22: "increase commander health by 2x"),
 * spawns at full health, and a second makeCommander call does not stack the doubling.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class CommanderHealthGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void commander_health_is_doubled_once(GameTestHelper helper) {
        Mob m = EntityRegistrar.VILLAGER_UNIT.get().create(helper.getLevel());
        if (m == null) {
            helper.fail("could not create a worker");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(3, 2, 3));
        m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        ((Unit) m).setOwnerName("gametest_cmdhp_" + helper.absolutePos(BlockPos.ZERO).asLong());
        helper.getLevel().addFreshEntity(m);
        float base = m.getMaxHealth();
        CommanderServerEvents.makeCommander(m);
        float once = m.getMaxHealth();
        CommanderServerEvents.makeCommander(m);
        float twice = m.getMaxHealth();
        float health = m.getHealth();
        m.discard();
        float want = (base + 80f) * 2f;
        if (Math.abs(once - want) > 0.01f)
            helper.fail("commander max health " + once + ", want " + want + " (worker base " + base + ")");
        else if (twice != once)
            helper.fail("a second makeCommander changed max health " + once + " -> " + twice);
        else if (Math.abs(health - once) > 0.01f)
            helper.fail("commander spawned at " + health + " of " + once);
        else
            helper.succeed();
    }
}
