package com.solegendary.reignofnether.registrars;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.maps.TemplateTreeFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Our worldgen feature types (their configured / placed features are data: data/reignofnether/worldgen/). */
public class FeatureRegistrar {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, ReignOfNether.MOD_ID);

    /** reignofnether:template_tree - one of our tree templates (maps/TreeTemplates). */
    public static final RegistryObject<TemplateTreeFeature> TEMPLATE_TREE =
        FEATURES.register("template_tree", () -> new TemplateTreeFeature(TemplateTreeFeature.Config.CODEC));

    public static void init(FMLJavaModLoadingContext context) {
        FEATURES.register(context.getModEventBus());
    }
}
