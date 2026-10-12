package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.brass.Heat;
import com.solegendary.reignofnether.unit.units.brass.IronLeviathanEngineUnit;
import net.minecraft.client.model.RavagerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RavagerRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.ModelData;

/**
 * The Brassworks Iron Leviathan Engine: the ravager model at {@link IronLeviathanEngineUnit#SCALE}x in a riveted
 * brass-and-soot war-engine skin (iron-plated face with a furnace grille, brass flanks, soot legs; 128x128, the
 * ravager layout, tools/gen_brass_skins.py) carrying a foundry on its back, drawn from block models by
 * {@link FoundryLayer} - the Leviathan's fortress trick - so it reads as a walking foundry-fortress from the RTS
 * camera: a blackstone deck, a furnace keep with a lit mouth, a brass boiler drum, a smokestack at the back and the
 * heavy cannon's barrel reaching out over the head. The Field Upgrade layer sits on top like every machine's.
 */
@OnlyIn(Dist.CLIENT)
public class IronLeviathanEngineRenderer extends RavagerRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/iron_leviathan_engine_unit.png");

    public IronLeviathanEngineRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius *= IronLeviathanEngineUnit.SCALE;
        this.addLayer(new FoundryLayer(this, context.getBlockRenderDispatcher()));
        this.addLayer(new BrassUpgradeLayer<>(this, 1.6f));
    }

    @Override
    public ResourceLocation getTextureLocation(Ravager entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(Ravager entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(IronLeviathanEngineUnit.SCALE, IronLeviathanEngineUnit.SCALE, IronLeviathanEngineUnit.SCALE);
    }

    /**
     * The foundry on the back. Layers draw in the model's space (1 unit = 1 block before the renderer's scale, y
     * pointing down, the feet at y = 1.5); the ravager's body (rotated upright in RavagerModel) has its back at about
     * y = -0.75 over z -0.5..0.5, x -0.44..0.44, the head out at -z. Each piece is a block model scaled down and stood on
     * the piece below. Static (it doesn't bob with the body); the furnace mouth goes dark while the engine vents (the
     * synced vent counter) so the silence reads. No allocation per frame.
     */
    static class FoundryLayer extends RenderLayer<Ravager, RavagerModel> {
        private static final BlockState DECK = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        private static final BlockState KEEP = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        private static final BlockState FURNACE_LIT = Blocks.BLAST_FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.LIT, true);
        private static final BlockState FURNACE_COLD = Blocks.BLAST_FURNACE.defaultBlockState();
        private static final BlockState BOILER = Blocks.WAXED_COPPER_BLOCK.defaultBlockState();
        private static final BlockState BRASS = Blocks.WAXED_CUT_COPPER.defaultBlockState();
        private static final BlockState STACK = Blocks.DEEPSLATE_TILES.defaultBlockState();
        private static final BlockState BARREL = Blocks.IRON_BLOCK.defaultBlockState();
        private static final BlockState LAMP = Blocks.LANTERN.defaultBlockState();
        static final float BACK_TOP = -0.75f;

        private final BlockRenderDispatcher blocks;

        FoundryLayer(RenderLayerParent<Ravager, RavagerModel> parent, BlockRenderDispatcher blocks) {
            super(parent);
            this.blocks = blocks;
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int light, Ravager entity, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity.isInvisible())
                return;
            boolean venting = entity instanceof IronLeviathanEngineUnit e && Heat.isVenting(e);
            float y = BACK_TOP;
            // the deck over the whole back, brass rails along its sides
            piece(ps, buf, light, DECK, 0f, y, 0f, 0.95f, 0.06f, 1.10f);
            y -= 0.06f;
            piece(ps, buf, light, BRASS, 0.44f, y, 0f, 0.06f, 0.06f, 1.0f);
            piece(ps, buf, light, BRASS, -0.44f, y, 0f, 0.06f, 0.06f, 1.0f);
            // the keep at the back half, the furnace mouth on its front face, the boiler drum on top
            piece(ps, buf, light, KEEP, 0f, y, 0.20f, 0.56f, 0.34f, 0.50f);
            piece(ps, buf, light, venting ? FURNACE_COLD : FURNACE_LIT, 0f, y, -0.06f, 0.26f, 0.24f, 0.04f);
            piece(ps, buf, light, BOILER, 0f, y - 0.34f, 0.20f, 0.36f, 0.22f, 0.40f);
            // the smokestack rising from the keep's back corner
            piece(ps, buf, light, STACK, 0.18f, y - 0.34f, 0.40f, 0.12f, 0.55f, 0.12f);
            // the cannon: a breech block on the deck's front half and a long barrel out over the head
            piece(ps, buf, light, BRASS, 0f, y, -0.25f, 0.24f, 0.16f, 0.24f);
            piece(ps, buf, light, BARREL, 0f, y - 0.06f, -0.62f, 0.12f, 0.10f, 0.62f);
            // lanterns on the front corners of the deck
            piece(ps, buf, light, LAMP, 0.38f, y, -0.46f, 0.10f, 0.12f, 0.10f);
            piece(ps, buf, light, LAMP, -0.38f, y, -0.46f, 0.10f, 0.12f, 0.10f);
        }

        /** One block model sx x sy x sz (model units) centred on (x, z), its bottom face at model height y. */
        private void piece(PoseStack ps, MultiBufferSource buf, int light, BlockState state,
                           float x, float y, float z, float sx, float sy, float sz) {
            ps.pushPose();
            ps.translate(x, y, z);
            ps.scale(-sx, -sy, sz);   // back to y-up (two flips keep the faces facing out), sized to the piece
            ps.translate(-0.5, 0.0, -0.5);
            blocks.renderSingleBlock(state, ps, buf, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            ps.popPose();
        }
    }
}
