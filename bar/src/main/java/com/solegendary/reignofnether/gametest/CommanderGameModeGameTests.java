package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * A lobby match puts each seated commander in creative (the RTS camera needs it): lovish joined his dedicated server
 * in survival, sat down in a lobby match and got no RTS view (Oct 21). Spectators and creative players are left alone.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class CommanderGameModeGameTests {

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void a_seated_commander_in_survival_gets_creative(GameTestHelper helper) {
        if (PlayerServerEvents.commanderGameMode(GameType.SURVIVAL) != GameType.CREATIVE)
            helper.fail("survival must become creative");
        else if (PlayerServerEvents.commanderGameMode(GameType.ADVENTURE) != GameType.CREATIVE)
            helper.fail("adventure must become creative");
        else if (PlayerServerEvents.commanderGameMode(GameType.CREATIVE) != GameType.CREATIVE)
            helper.fail("creative stays creative");
        else if (PlayerServerEvents.commanderGameMode(GameType.SPECTATOR) != GameType.SPECTATOR)
            helper.fail("a spectator is left alone");
        else
            helper.succeed();
    }
}
