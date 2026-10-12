package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.unit.units.storm.GeomancerUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** The Stormcallers Ley Master: the Geomancer's body in gold robes with jade discs on the shoulders and a tall jade-and-gold crown (tools/gen_storm_skins.py). */
@OnlyIn(Dist.CLIENT)
public class LeyMasterRenderer extends GeomancerRenderer {
    private static final ResourceLocation LEY_MASTER_UNIT = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/ley_master_unit.png");

    public LeyMasterRenderer(EntityRendererProvider.Context context) {
        super(context);
        // the T2 constructor's back banner in the owner's colour: found at a glance among the T1 workers
        this.addLayer(new com.solegendary.reignofnether.unit.modelling.layers.BackBannerLayer<>(this));
    }

    @Override
    public ResourceLocation getTextureLocation(GeomancerUnit unit) {
        return LEY_MASTER_UNIT;
    }
}
