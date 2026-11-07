package com.solegendary.reignofnether.environment.lawn;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Installs {@link GiantLawnBakedModel} over the grass block's snowy=false block-state model (snowy grass keeps its
 * own white top).
 * <p>
 * HIGH priority: before ClientModEvents' fog tint wrapper (NORMAL), which then wraps ours - the fog wrapper stays the
 * outermost on every reload, forwards getModelData / getRenderTypes / getQuads to us, and our quads (tint index 0, never
 * -1) pass through it untouched. EdgeOverlayModelEvents (also HIGH) wraps dirt / path / sand / gravel, never grass, and
 * the branch-wind sway wrapper only its own limb blocks, so nothing else of ours wraps the grass block; a model already
 * ours is never wrapped twice.
 */
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class GiantLawnModelEvents {

    // this reload's wrappers and their shared quads: created at ModifyBakingResult, baked once the atlas is uploaded
    private static volatile GiantLawnQuads pending = null;
    private static volatile List<GiantLawnBakedModel> pendingModels = List.of();

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void wrapLawnModels(ModelEvent.ModifyBakingResult evt) {
        ResourceLocation grass = ForgeRegistries.BLOCKS.getKey(Blocks.GRASS_BLOCK);
        GiantLawnQuads quads = new GiantLawnQuads();
        List<GiantLawnBakedModel> models = new ArrayList<>();
        for (Map.Entry<ResourceLocation, BakedModel> entry : evt.getModels().entrySet()) {
            if (!(entry.getKey() instanceof ModelResourceLocation mrl) || !mrl.getVariant().equals("snowy=false"))
                continue;
            if (!mrl.getNamespace().equals(grass.getNamespace()) || !mrl.getPath().equals(grass.getPath()))
                continue;
            if (entry.getValue() instanceof GiantLawnBakedModel)
                continue;
            GiantLawnBakedModel ours = new GiantLawnBakedModel(entry.getValue(), quads);
            entry.setValue(ours);
            models.add(ours);
        }
        pending = quads;
        pendingModels = List.copyOf(models);
        ReignOfNether.LOGGER.info("[GiantLawn] wrapped {} grass block-state model(s)", models.size());
    }

    /** The models are installed and the block atlas uploaded (ModelManager.apply): look the sheet up, and warm the
     * remap cache with every variant of the grass model (the weighted pick follows the random; 64 seeds reach each of
     * its six equal variants all but surely - a list missed here is built on first sight). */
    @SubscribeEvent
    public static void bakeLawnQuads(ModelEvent.BakingCompleted evt) {
        GiantLawnQuads quads = pending;
        List<GiantLawnBakedModel> models = pendingModels;
        pending = null;
        pendingModels = List.of();
        if (quads == null)
            return;
        TextureAtlas atlas = evt.getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        if (!quads.bake(atlas::getSprite)) {
            ReignOfNether.LOGGER.info("[GiantLawn] no grass_block_top_g1..g" + GiantLawnCells.CELLS + " in the block atlas: lawn sheet off");
            return;
        }
        BlockState state = Blocks.GRASS_BLOCK.defaultBlockState().setValue(BlockStateProperties.SNOWY, false);
        int tops = 0;
        for (GiantLawnBakedModel m : models) {
            for (long seed = 0; seed < 64; seed++) {
                for (Direction side : new Direction[]{Direction.UP, null}) {
                    var base = m.original().getQuads(state, side, RandomSource.create(seed), ModelData.EMPTY, null);
                    if (quads.swap(base, 1) != base) tops++;
                }
            }
        }
        ReignOfNether.LOGGER.info("[GiantLawn] lawn sheet on ({} warm-up swaps)", tops);
    }
}
