package com.solegendary.reignofnether.unit.modelling.renderers;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Horde's essence caster, the Voodoo Shaman (design/essence_casters.md): the piglin renderer with, as a placeholder
 * until it gets a skin of its own, the Spear Thrower's fur-cloaked one (and its owner-coloured _team layer). A subclass
 * like the Wrecker's, so PiglinUnitRenderer's texture map stays untouched.
 */
@OnlyIn(Dist.CLIENT)
public class VoodooShamanRenderer extends PiglinUnitRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/headhunter_unit.png");

    public VoodooShamanRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(Mob entity) {
        return TEXTURE;
    }
}
