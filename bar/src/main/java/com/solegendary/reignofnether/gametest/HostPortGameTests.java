package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.matchstart.HostPort;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** matchstart/HostPort: Host Online keeps the configured port when it is free, else falls back to any free port. */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class HostPortGameTests {

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void host_online_keeps_the_configured_port(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (HostPort.choose(25565, p -> true, () -> 40000) != 25565)
            problems.add("a free configured port must be used");
        if (HostPort.choose(25565, p -> false, () -> 40000) != 40000)
            problems.add("a taken configured port must fall back to a free one");
        if (HostPort.choose(0, p -> true, () -> 40000) != 40000)
            problems.add("host_port 0 means any free port");
        if (HostPort.choose(70000, p -> true, () -> 40000) != 40000)
            problems.add("an out-of-range port must fall back");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
