package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.ability.abilities.CallAngel;
import com.solegendary.reignofnether.unit.units.villagers.AngelUnit;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.VexModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

/**
 * The Angel Caller's angels (AngelUnit), placeholders until real angel art comes: vanilla's vex body drawn bright and
 * a little translucent, scaled up so it can be clicked from the RTS camera, and tinted per kind - the Mender Angel a
 * pale healing green-white, the Seraph Archer sun gold, the Sword Seraph white steel. Only the vex's model and texture
 * are borrowed (VexModel's own animation wants a Vex), so the wings are flapped here by hand, as the Wisp Choir does
 * with the allay's.
 */
@OnlyIn(Dist.CLIENT)
public class AngelRenderer<T extends AngelUnit> extends MobRenderer<T, AngelRenderer.AngelModel<T>> {
    private static final ResourceLocation VEX = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/illager/vex.png");

    public AngelRenderer(EntityRendererProvider.Context context) {
        super(context, new AngelModel<>(new VexModel(context.bakeLayer(ModelLayers.VEX))), 0.3F);
    }

    @Override
    public ResourceLocation getTextureLocation(T unit) {
        return VEX;
    }

    // angels glow: never darker than full light
    @Override
    protected int getBlockLightLevel(T unit, BlockPos pos) {
        return 15;
    }

    @Override
    protected void scale(T unit, PoseStack poseStack, float partialTick) {
        float s = unit.kind() == CallAngel.Kind.SWORD ? 1.7f : 1.45f;
        poseStack.scale(s, s, s);
    }

    /** The wrapped vex model, tinted by the kind of the angel it was last posed for. */
    public static class AngelModel<T extends AngelUnit> extends EntityModel<T> {
        private final VexModel vex;
        @Nullable private final ModelPart rightWing, leftWing;
        private float r = 1f, g = 1f, b = 1f;

        AngelModel(VexModel vex) {
            super(RenderType::entityTranslucent);
            this.vex = vex;
            ModelPart body = child(vex.root(), "body");
            this.rightWing = body == null ? null : child(body, "right_wing");
            this.leftWing = body == null ? null : child(body, "left_wing");
        }

        @Nullable
        private static ModelPart child(ModelPart part, String name) {
            try {
                return part.getChild(name);
            } catch (java.util.NoSuchElementException e) {
                return null;   // a changed vanilla layer only costs the wing flap
            }
        }

        @Override
        public void setupAnim(T unit, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            switch (unit.kind()) {
                case MENDER -> { r = 0.85f; g = 1.0f; b = 0.85f; }
                case ARCHER -> { r = 1.0f; g = 0.85f; b = 0.45f; }
                case SWORD -> { r = 0.95f; g = 0.95f; b = 1.0f; }
            }
            float flap = Mth.cos(ageInTicks * 0.9f) * (float) Math.PI * 0.12f;
            if (rightWing != null)
                rightWing.yRot = 0.47123894f + flap;
            if (leftWing != null)
                leftWing.yRot = -0.47123894f - flap;
        }

        @Override
        public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, float red, float green, float blue, float alpha) {
            vex.renderToBuffer(pose, buffer, LightTexture.FULL_BRIGHT, overlay, red * r, green * g, blue * b, alpha * 0.92f);
        }
    }
}
