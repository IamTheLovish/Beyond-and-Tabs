package com.solegendary.reignofnether.unit.modelling.renderers;

import com.github.alexthe666.alexsmobs.client.render.RenderElephant;
import com.github.alexthe666.alexsmobs.entity.EntityElephant;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Stormcallers Lantern Fortress: Alex's Mobs' elephant model and animations (RenderElephant) in our own skin - a
 * red-lacquer caparison with gold trim and a lantern howdah painted on the back (tools/gen_storm_skins.py paints it on
 * the elephant's UV outline), drawn at {@link #SCALE} of a wild bull (a 3-block elephant would fill a street). AM's
 * overlay layers (chests, carpets, tusks - all AM textures) and its item layer are dropped: the Fortress carries
 * lanterns, not cargo.
 */
@OnlyIn(Dist.CLIENT)
public class LanternFortressRenderer extends RenderElephant {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/lantern_fortress_unit.png");
    public static final float SCALE = 0.75f;

    public LanternFortressRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.layers.clear();
        this.shadowRadius = 1.4f * SCALE;
    }

    @Override
    protected void scale(EntityElephant entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(EntityElephant entity) {
        return TEXTURE;
    }
}
