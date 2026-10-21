package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.sounds.ZoomVolume;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** sounds/ZoomVolume (BAR-style, Oct 21): full volume up close, falling smoothly to QUIETEST zoomed out; UI untouched. */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class SoundLevelGameTests {

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void world_sounds_get_quieter_as_the_camera_zooms_out(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (ZoomVolume.factor(10) != 1f || ZoomVolume.factor(ZoomVolume.NEAR) != 1f)
            problems.add("close in must be full volume");
        if (Math.abs(ZoomVolume.factor(ZoomVolume.FAR) - ZoomVolume.QUIETEST) > 1e-4 || ZoomVolume.factor(400) != ZoomVolume.QUIETEST)
            problems.add("zoomed out must hold at the quietest volume");
        float prev = 2f;
        for (float z = 5; z <= 250; z += 5) {
            float f = ZoomVolume.factor(z);
            if (f > prev + 1e-6f)
                problems.add("volume rose while zooming out at " + z);
            if (f < ZoomVolume.QUIETEST - 1e-6f || f > 1f)
                problems.add("volume out of range at " + z + ": " + f);
            prev = f;
        }
        float atDefault = ZoomVolume.factor(30);
        if (atDefault < 0.5f || atDefault > 0.85f)
            problems.add("the default zoom (30) should be clearly quieter than close in but well audible: " + atDefault);
        if (ZoomVolume.scaled(SoundSource.MASTER) || ZoomVolume.scaled(SoundSource.MUSIC))
            problems.add("the UI / announcer (MASTER) and music must not be scaled");
        if (!ZoomVolume.scaled(SoundSource.BLOCKS) || !ZoomVolume.scaled(SoundSource.HOSTILE))
            problems.add("world sounds must be scaled");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
