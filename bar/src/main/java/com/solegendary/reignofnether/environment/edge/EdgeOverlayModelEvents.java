package com.solegendary.reignofnether.environment.edge;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/**
 * Installs {@link EdgeOverlayBakedModel} over the block-state models of the blocks the grass fringe spills onto.
 * <p>
 * HIGH priority: before ClientModEvents' fog tint wrapper (NORMAL), which then wraps ours - the fog wrapper is the
 * outermost of the two on every reload, it forwards getModelData / getRenderTypes / getQuads to us, and our quads
 * (tint index EDGE_GRASS_TINT_INDEX, never -1) pass through it untouched. A model already ours is never wrapped twice.
 */
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class EdgeOverlayModelEvents {

    /** The blocks that get a fringe, with the height of their top face (dirt_path and farmland sit at 15/16). */
    private static Map<Block, Float> targets() {
        Map<Block, Float> m = new HashMap<>();
        for (Block b : new Block[]{Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.SAND, Blocks.RED_SAND,
                Blocks.GRAVEL})
            m.put(b, 1f);
        m.put(Blocks.DIRT_PATH, 15f / 16f);
        m.put(Blocks.FARMLAND, 15f / 16f);
        return m;
    }

    // this reload's quad sets, one per top height: created with the wrappers, baked once the atlas is uploaded
    private static volatile Map<Float, EdgeOverlayQuads> pending = Map.of();

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void wrapEdgeModels(ModelEvent.ModifyBakingResult evt) {
        Map<Float, EdgeOverlayQuads> byTop = new HashMap<>();
        Map<ResourceLocation, Float> ours = new HashMap<>();
        for (Map.Entry<Block, Float> e : targets().entrySet())
            ours.put(ForgeRegistries.BLOCKS.getKey(e.getKey()), e.getValue());
        int wrapped = 0;
        for (Map.Entry<ResourceLocation, BakedModel> entry : evt.getModels().entrySet()) {
            if (!(entry.getKey() instanceof ModelResourceLocation mrl) || mrl.getVariant().equals("inventory"))
                continue;
            Float top = ours.get(new ResourceLocation(mrl.getNamespace(), mrl.getPath()));
            if (top == null || entry.getValue() instanceof EdgeOverlayBakedModel)
                continue;
            EdgeOverlayQuads quads = byTop.computeIfAbsent(top, EdgeOverlayQuads::new);
            entry.setValue(new EdgeOverlayBakedModel(entry.getValue(), quads));
            wrapped++;
        }
        pending = byTop;
        ReignOfNether.LOGGER.info("[EdgeOverlay] wrapped {} block-state models of {} blocks", wrapped, ours.size());
    }

    /** The models are installed and the block atlas uploaded (ModelManager.apply): bake the fringe quads with the
     * atlas's sprites. ModifyBakingResult runs before the upload, when the atlas still holds the previous sprites. */
    @SubscribeEvent
    public static void bakeEdgeQuads(ModelEvent.BakingCompleted evt) {
        TextureAtlas atlas = evt.getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        for (EdgeOverlayQuads quads : pending.values())
            quads.bake(name -> atlas.getSprite(new ResourceLocation(ReignOfNether.MOD_ID, "block/edge/" + name)));
        pending = Map.of();
    }
}
