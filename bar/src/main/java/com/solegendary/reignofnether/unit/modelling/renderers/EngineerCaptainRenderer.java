package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.unit.units.brass.TinkerUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** The Brassworks Engineer-Captain: the Tinker's body and tool layer in an officer's brass-trimmed soot coat with a peaked cap (tools/gen_brass_skins.py). */
@OnlyIn(Dist.CLIENT)
public class EngineerCaptainRenderer extends TinkerRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/engineer_captain_unit.png");

    public EngineerCaptainRenderer(EntityRendererProvider.Context context) {
        super(context);
        // the T2 constructor's back banner in the owner's colour: found at a glance among the T1 workers
        this.addLayer(new com.solegendary.reignofnether.unit.modelling.layers.BackBannerLayer<>(this));
    }

    @Override
    public ResourceLocation getTextureLocation(TinkerUnit unit) {
        return TEXTURE;
    }
}
