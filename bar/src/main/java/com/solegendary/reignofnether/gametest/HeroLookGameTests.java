package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * How heroes look (design-draft-heroes-better-2026-10-30 #4; lovish, Oct 30: the Ash Shaman gets its own ash-and-ember
 * skin now, its real model later). The Ash Shaman wore the Warchief's skin on the Warchief's body
 * (HeroRenderers.AshShaman: tex("horde_warchief"), "placeholder skin"): the Horde's two heroes were the same unit to
 * look at. The skin is read from the mod's own resources, as the client loads it. Written before the code (tests first).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class HeroLookGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String ENTITIES = "/assets/reignofnether/textures/entities/";

    static BufferedImage skin(String file) {
        try (InputStream in = HeroLookGameTests.class.getResourceAsStream(ENTITIES + file)) {
            return in == null ? null : ImageIO.read(in);
        } catch (Exception e) {
            return null;
        }
    }

    /** ash_shaman_unit.png exists, has the Warchief's piglin UV layout (same size), and is not the Warchief's pixels. */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void hero_look_ash_shaman_has_a_skin_of_its_own(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        BufferedImage shaman = skin("ash_shaman_unit.png"), warchief = skin("horde_warchief_unit.png");
        if (warchief == null)
            problems.add("setup: horde_warchief_unit.png could not be read");
        if (shaman == null)
            problems.add("there is no ash_shaman_unit.png: the Ash Shaman still wears the Warchief's skin");
        if (shaman != null && warchief != null) {
            if (shaman.getWidth() != warchief.getWidth() || shaman.getHeight() != warchief.getHeight())
                problems.add("the Ash Shaman's skin is " + shaman.getWidth() + "x" + shaman.getHeight()
                    + ", not the piglin body's " + warchief.getWidth() + "x" + warchief.getHeight());
            else {
                int differ = 0;
                for (int y = 0; y < shaman.getHeight(); y++)
                    for (int x = 0; x < shaman.getWidth(); x++)
                        if (shaman.getRGB(x, y) != warchief.getRGB(x, y))
                            differ++;
                if (differ < shaman.getWidth() * shaman.getHeight() / 10)
                    problems.add("only " + differ + " pixels differ from the Warchief's skin");
            }
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
