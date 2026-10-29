package com.solegendary.reignofnether.entities.renderers;

import com.solegendary.reignofnether.entities.NecromancerProjectile;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * The Gravebound soul bolt. Oct 22 (vfx foundation): the first projectile on the projectile visual layer - its look
 * is assets/reignofnether/vfx/soul_bolt.json (core, ribbon, shed wisps, cold light); the old texture is only the
 * fallback when that file is missing.
 */
public class NecromancerProjectileRenderer extends VfxProjectileRenderer<NecromancerProjectile> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/necromancer_projectile.png");

    public NecromancerProjectileRenderer(EntityRendererProvider.Context ctx) { super(ctx, TEXTURE); }
}
