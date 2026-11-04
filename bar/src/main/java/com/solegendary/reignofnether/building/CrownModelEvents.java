package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.ReignOfNether;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

/** Mod-bus client registration for the living crowns: {@link CrownBakedModel} over every leaves block's models. */
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class CrownModelEvents {

    /** LOWEST: outside every other wrapper (the fog tint wraps every model at normal priority). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void wrapLeafModels(ModelEvent.ModifyBakingResult evt) {
        int wrapped = 0;
        for (Map.Entry<ResourceLocation, BakedModel> entry : evt.getModels().entrySet()) {
            if (!(entry.getKey() instanceof ModelResourceLocation mrl) || mrl.getVariant().equals("inventory"))
                continue;
            Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(mrl.getNamespace(), mrl.getPath()));
            if (block instanceof LeavesBlock) {
                entry.setValue(new CrownBakedModel(entry.getValue()));
                wrapped++;
            }
        }
        ReignOfNether.LOGGER.info("[Crowns] wrapped {} leaves block models", wrapped);
    }
}
