package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The battlefield world is shallow (design/battlefield-height.md): its dimension type and its noise settings must load
 * and agree, or worldgen would build a column the dimension does not have (or the world would fail to open).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BattlefieldHeightGameTests {

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void battlefield_dimension_type_and_noise_settings_agree_and_are_shallow(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        DimensionType type = access.registryOrThrow(Registries.DIMENSION_TYPE)
            .get(ResourceKey.create(Registries.DIMENSION_TYPE, new ResourceLocation(ReignOfNether.MOD_ID, "battlefield")));
        NoiseGeneratorSettings noise = access.registryOrThrow(Registries.NOISE_SETTINGS)
            .get(ResourceKey.create(Registries.NOISE_SETTINGS, new ResourceLocation(ReignOfNether.MOD_ID, "battlefield")));
        if (type == null || noise == null)
            helper.fail("battlefield dimension type or noise settings did not load: " + type + " / " + noise);
        if (type.minY() != noise.noiseSettings().minY() || type.height() != noise.noiseSettings().height())
            helper.fail("dimension y " + type.minY() + " h " + type.height() + " but noise y "
                + noise.noiseSettings().minY() + " h " + noise.noiseSettings().height());
        if (type.height() > 256 || type.logicalHeight() > type.height())
            helper.fail("battlefield is not shallow: height " + type.height() + ", logical " + type.logicalHeight());
        int sea = noise.seaLevel();
        if (sea <= type.minY() || sea + 100 > type.minY() + type.height())
            helper.fail("sea level " + sea + " leaves no floor or no headroom in y " + type.minY() + ".." + (type.minY() + type.height()));
        helper.succeed();
    }
}
