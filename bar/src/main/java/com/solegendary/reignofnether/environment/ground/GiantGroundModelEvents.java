package com.solegendary.reignofnether.environment.ground;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.config.DisplaySettings;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Installs {@link GiantGroundBakedModel} over the block-state models of the ground blocks in GroundSheetRules.TABLE
 * (all their states but snowy=true: a snowy grass / podzol top is the white snow, which keeps its own look) and bakes
 * their sheets and the clover once the block atlas is uploaded.
 * <p>
 * HIGH priority: before ClientModEvents' fog tint wrapper (NORMAL), which then wraps ours - the fog wrapper stays the
 * outermost on every reload, forwards getModelData / getRenderTypes / getQuads to us, and passes our tinted quads
 * through untouched (the swapped dirt tops keep tint index -1 and get its fog index as before). EdgeOverlayModelEvents
 * (also HIGH) wraps dirt / path / gravel as well; the two compose in either order (see GiantGroundBakedModel). A model
 * already ours is never wrapped twice.
 */
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class GiantGroundModelEvents {

    // this reload's wrappers (their sheets and the clover hang off them): created at ModifyBakingResult, baked once the
    // atlas is uploaded
    private static volatile List<GiantGroundBakedModel> pending = List.of();

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void wrapGroundModels(ModelEvent.ModifyBakingResult evt) {
        CloverLayer clover = new CloverLayer();
        Map<String, GroundSheet> byBlock = new HashMap<>();
        for (GroundSheetRules.Entry e : GroundSheetRules.TABLE)
            byBlock.put(e.block(), new GroundSheet(e, e.lawn() ? DisplaySettings::lawnSheet
                    : DisplaySettings::groundSheets));
        List<GiantGroundBakedModel> models = new ArrayList<>();
        for (Map.Entry<ResourceLocation, BakedModel> entry : evt.getModels().entrySet()) {
            if (!(entry.getKey() instanceof ModelResourceLocation mrl) || !mrl.getNamespace().equals("minecraft"))
                continue;
            String variant = mrl.getVariant();
            if (variant.equals("inventory") || variant.contains("snowy=true"))
                continue;
            GroundSheet sheet = byBlock.get(mrl.getPath());
            if (sheet == null || entry.getValue() instanceof GiantGroundBakedModel)
                continue;
            GiantGroundBakedModel ours = new GiantGroundBakedModel(entry.getValue(), sheet,
                    sheet.entry.clover() ? clover : null);
            entry.setValue(ours);
            models.add(ours);
        }
        pending = List.copyOf(models);
        ReignOfNether.LOGGER.info("[GiantGround] wrapped {} block-state model(s) of {} ground blocks", models.size(),
                byBlock.size());
    }

    /** The models are installed and the block atlas uploaded (ModelManager.apply): look the sheets and the clover up,
     * read whether each base model draws in the cutout layer, and warm the quad caches with every variant (the
     * weighted pick follows the random; 64 seeds reach each of six equal variants all but surely - a list missed here
     * is built on first sight). */
    @SubscribeEvent
    public static void bakeGroundQuads(ModelEvent.BakingCompleted evt) {
        List<GiantGroundBakedModel> models = pending;
        pending = List.of();
        if (models.isEmpty())
            return;
        TextureAtlas atlas = evt.getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        Map<GroundSheet, Integer> sizes = new HashMap<>();
        CloverLayer clover = null;
        for (GiantGroundBakedModel m : models) {
            sizes.computeIfAbsent(m.sheet(), s -> s.bake(atlas::getSprite));
            if (m.clover() != null && clover == null) {
                clover = m.clover();
                boolean on = clover.bake(atlas::getSprite);
                ReignOfNether.LOGGER.info("[GiantGround] clover layer {}", on ? "on"
                        : "off (no block/grass_clover_layer_1..8 in the block atlas)");
            }
        }
        for (Map.Entry<GroundSheet, Integer> e : sizes.entrySet())
            ReignOfNether.LOGGER.info("[GiantGround] {}: {}", e.getKey().entry.top(), e.getValue() > 0
                    ? e.getValue() + "x" + e.getValue() + " sheet"
                    : "no sheet (" + e.getKey().entry.top() + "_g1..4/16/64 not all in the block atlas)");
        int lists = 0;
        for (GiantGroundBakedModel m : models) {
            Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("minecraft", m.sheet().entry.block()));
            if (block == null)
                continue;
            BlockState state = block.defaultBlockState();   // snowy=false for grass and podzol
            m.setBaseCutout(m.original().getRenderTypes(state, RandomSource.create(0), ModelData.EMPTY)
                    .contains(RenderType.cutoutMipped()));
            int cells = m.sheet().size() * m.sheet().size();
            for (long seed = 0; seed < 64; seed++) {
                for (Direction side : new Direction[]{Direction.UP, null}) {
                    var base = m.original().getQuads(state, side, RandomSource.create(seed), ModelData.EMPTY, null);
                    for (int c = 0; c <= cells; c++) {
                        var swapped = c == 0 ? base : m.sheet().swap(base, c);
                        if (side == Direction.UP && m.clover() != null && m.clover().ready())
                            m.clover().with(swapped, 1);
                    }
                    lists++;
                }
            }
        }
        ReignOfNether.LOGGER.info("[GiantGround] caches warmed ({} base lists looked up)", lists);
    }
}
