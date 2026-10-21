package com.solegendary.reignofnether.environment.wind;

import com.solegendary.reignofnether.ReignOfNether;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Mod-bus client registration for the branch wind: the renderer, and the sway wrapper over the limb blocks' models. */
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class BranchWindModEvents {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers evt) {
        evt.registerBlockEntityRenderer(SwayBlocks.SWAY_BLOCK_ENTITY.get(), BranchWindRenderer::new);
    }

    /** LOWEST: outside every other wrapper (the fog tint wraps every model at normal priority). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void wrapSwayModels(ModelEvent.ModifyBakingResult evt) {
        Set<ResourceLocation> ours = new HashSet<>();
        for (RegistryObject<SwayWoodBlock> r : SwayBlocks.WOOD_BLOCKS)
            ours.add(ForgeRegistries.BLOCKS.getKey(r.get()));
        for (RegistryObject<SwayFenceBlock> r : SwayBlocks.FENCE_BLOCKS)
            ours.add(ForgeRegistries.BLOCKS.getKey(r.get()));
        int wrapped = 0;
        for (Map.Entry<ResourceLocation, BakedModel> entry : evt.getModels().entrySet()) {
            if (!(entry.getKey() instanceof ModelResourceLocation mrl) || mrl.getVariant().equals("inventory"))
                continue;
            ResourceLocation block = new ResourceLocation(mrl.getNamespace(), mrl.getPath());
            if (ours.contains(block)) {
                entry.setValue(new SwayBakedModel(entry.getValue()));
                wrapped++;
            }
        }
        ReignOfNether.LOGGER.info("[BranchWind] wrapped {} sway block models for {} blocks", wrapped, ours.size());
    }
}
