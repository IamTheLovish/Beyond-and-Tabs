package com.solegendary.reignofnether.unit.modelling.renderers;

import com.github.alexthe666.alexsmobs.client.model.ModelLobster;
import com.github.alexthe666.alexsmobs.client.render.RenderLobster;
import com.github.alexthe666.alexsmobs.entity.EntityLobster;
import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.units.tide.LeviathanUnit;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.ModelData;

/**
 * The Tidewrought Leviathan: Alex's Mobs' lobster model drawn {@link LeviathanUnit#SCALE}x in an abyssal shell skin
 * (dark teal plates, brass rivets, barnacles; tools/gen_tide_skins.py) with a small fortress on its back - a dark
 * prismarine deck, a prismarine keep, a waxed-copper turret and a sea-lantern lamp, drawn from block models by
 * {@link FortressLayer} so it reads as a walking fortress from the RTS camera.
 */
@OnlyIn(Dist.CLIENT)
public class LeviathanRenderer extends RenderLobster {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/leviathan_unit.png");

    public LeviathanRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.25F * LeviathanUnit.SCALE;
        this.addLayer(new FortressLayer(this, context.getBlockRenderDispatcher()));
    }

    @Override
    protected void scale(EntityLobster entity, PoseStack poseStack, float partialTickTime) {
        poseStack.scale(LeviathanUnit.SCALE, LeviathanUnit.SCALE, LeviathanUnit.SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(EntityLobster entity) {
        return TEXTURE;
    }

    /**
     * The fortress on the shell. Layers draw in the model's space (1 unit = 1 block before the renderer's scale, y
     * pointing down, the feet at y = 1.5); the lobster's carapace top sits at y = 1.35 over z -0.44..0 (its head is
     * at -z). Each piece is a block model scaled down and stood on the piece below - the mooshroom's mushroom-layer
     * trick. Static (it doesn't bob with the body), no allocation per frame.
     */
    static class FortressLayer extends RenderLayer<EntityLobster, ModelLobster> {
        private static final BlockState DECK = Blocks.DARK_PRISMARINE.defaultBlockState();
        private static final BlockState KEEP = Blocks.PRISMARINE_BRICKS.defaultBlockState();
        private static final BlockState TURRET = Blocks.WAXED_CUT_COPPER.defaultBlockState();
        private static final BlockState LAMP = Blocks.SEA_LANTERN.defaultBlockState();
        private static final BlockState CORAL = Blocks.DEAD_BRAIN_CORAL_BLOCK.defaultBlockState();
        static final float SHELL_TOP = 1.35f, SHELL_Z = -0.22f;

        private final BlockRenderDispatcher blocks;

        FortressLayer(RenderLayerParent<EntityLobster, ModelLobster> parent, BlockRenderDispatcher blocks) {
            super(parent);
            this.blocks = blocks;
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int light, EntityLobster entity, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity.isInvisible())
                return;
            float y = SHELL_TOP;
            piece(ps, buf, light, DECK, 0f, y, SHELL_Z, 0.30f, 0.05f, 0.40f);
            y -= 0.05f;
            // corner towers on the deck, then the keep, the turret and its lamp up the middle
            piece(ps, buf, light, DECK, 0.12f, y, SHELL_Z - 0.16f, 0.06f, 0.10f, 0.06f);
            piece(ps, buf, light, DECK, -0.12f, y, SHELL_Z - 0.16f, 0.06f, 0.10f, 0.06f);
            piece(ps, buf, light, CORAL, 0.12f, y, SHELL_Z + 0.16f, 0.05f, 0.06f, 0.05f);
            piece(ps, buf, light, CORAL, -0.12f, y, SHELL_Z + 0.16f, 0.05f, 0.06f, 0.05f);
            piece(ps, buf, light, KEEP, 0f, y, SHELL_Z, 0.16f, 0.14f, 0.18f);
            y -= 0.14f;
            piece(ps, buf, light, TURRET, 0f, y, SHELL_Z, 0.10f, 0.06f, 0.10f);
            y -= 0.06f;
            piece(ps, buf, light, LAMP, 0f, y, SHELL_Z, 0.06f, 0.06f, 0.06f);
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
