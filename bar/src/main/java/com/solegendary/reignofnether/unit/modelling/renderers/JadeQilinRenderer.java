package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.units.storm.JadeQilinUnit;

import net.minecraft.client.model.HorseModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AbstractHorseRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Stormcallers Jade Qilin: the vanilla white horse drawn x{@link JadeQilinUnit#SCALE} under jade scale (block
 * pieces, {@link BlockPieces}): waxed oxidised-copper plates down its back and flanks with gold trim, cloud puffs (white
 * wool) at the shoulders and haunches, and a little shrine on its back - a red-lacquer post box under a jade roof with
 * a gold finial and a hanging lantern, the Stormcallers' look (design/stormcallers_plan.md). Everything sits on the
 * body, which only moves when the horse rears: the head and neck animate, so a horn or mane there would float off.
 * From the vanilla model: the back's top sits at model y = 0.19 over z -0.75..0.63 (the head is at -z), x +-0.31.
 */
@OnlyIn(Dist.CLIENT)
public class JadeQilinRenderer extends AbstractHorseRenderer<Horse, HorseModel<Horse>> {

    // vanilla's HorseRenderer is final: this is its body (the horse model at its 1.1 scale) with only the white coat -
    // no markings, no horse armour
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/horse/horse_white.png");

    public JadeQilinRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HorseModel<>(ctx.bakeLayer(ModelLayers.HORSE)), 1.1f);
        this.shadowRadius *= JadeQilinUnit.SCALE;
        this.addLayer(new ScaleLayer(this, ctx.getBlockRenderDispatcher()));
    }

    @Override
    public ResourceLocation getTextureLocation(Horse entity) {
        return WHITE;
    }

    @Override
    protected void scale(Horse entity, PoseStack poseStack, float partialTick) {
        super.scale(entity, poseStack, partialTick);
        poseStack.scale(JadeQilinUnit.SCALE, JadeQilinUnit.SCALE, JadeQilinUnit.SCALE);
    }

    static class ScaleLayer extends RenderLayer<Horse, HorseModel<Horse>> {
        private static final BlockState JADE = Blocks.WAXED_OXIDIZED_CUT_COPPER.defaultBlockState();
        private static final BlockState GOLD = Blocks.GOLD_BLOCK.defaultBlockState();
        private static final BlockState CLOUD = Blocks.WHITE_WOOL.defaultBlockState();
        private static final BlockState LACQUER = Blocks.RED_TERRACOTTA.defaultBlockState();
        private static final BlockState LANTERN = Blocks.LANTERN.defaultBlockState();
        static final float BACK = 0.19f, MID_Z = -0.06f;

        private final BlockRenderDispatcher blocks;

        ScaleLayer(RenderLayerParent<Horse, HorseModel<Horse>> parent, BlockRenderDispatcher blocks) {
            super(parent);
            this.blocks = blocks;
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int light, Horse entity, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity.isInvisible())
                return;
            // jade plates: a saddle of scale along the back, a skirt down each flank, a gold seam on the spine
            BlockPieces.piece(blocks, ps, buf, light, JADE, 0f, BACK, MID_Z, 0.7f, 0.05f, 1.25f);
            BlockPieces.piece(blocks, ps, buf, light, JADE, -0.33f, BACK + 0.38f, MID_Z, 0.05f, 0.36f, 1.1f);
            BlockPieces.piece(blocks, ps, buf, light, JADE, 0.33f, BACK + 0.38f, MID_Z, 0.05f, 0.36f, 1.1f);
            BlockPieces.piece(blocks, ps, buf, light, GOLD, 0f, BACK - 0.05f, MID_Z, 0.08f, 0.03f, 1.25f);
            // cloud puffs at the shoulders and haunches (the qilin walks on clouds)
            BlockPieces.piece(blocks, ps, buf, light, CLOUD, -0.34f, BACK + 0.55f, MID_Z - 0.5f, 0.12f, 0.16f, 0.22f);
            BlockPieces.piece(blocks, ps, buf, light, CLOUD, 0.34f, BACK + 0.55f, MID_Z - 0.5f, 0.12f, 0.16f, 0.22f);
            BlockPieces.piece(blocks, ps, buf, light, CLOUD, -0.34f, BACK + 0.55f, MID_Z + 0.5f, 0.12f, 0.16f, 0.22f);
            BlockPieces.piece(blocks, ps, buf, light, CLOUD, 0.34f, BACK + 0.55f, MID_Z + 0.5f, 0.12f, 0.16f, 0.22f);
            // the shrine: a lacquer box, a jade roof wider than it, a gold finial, a lantern under the eave
            BlockPieces.piece(blocks, ps, buf, light, LACQUER, 0f, BACK - 0.05f, MID_Z + 0.1f, 0.28f, 0.24f, 0.28f);
            BlockPieces.piece(blocks, ps, buf, light, JADE, 0f, BACK - 0.29f, MID_Z + 0.1f, 0.44f, 0.05f, 0.44f);
            BlockPieces.piece(blocks, ps, buf, light, JADE, 0f, BACK - 0.34f, MID_Z + 0.1f, 0.26f, 0.05f, 0.26f);
            BlockPieces.piece(blocks, ps, buf, light, GOLD, 0f, BACK - 0.39f, MID_Z + 0.1f, 0.06f, 0.12f, 0.06f);
            BlockPieces.piece(blocks, ps, buf, light, LANTERN, 0.2f, BACK - 0.12f, MID_Z + 0.1f, 0.1f, 0.12f, 0.1f);
        }
    }
}
