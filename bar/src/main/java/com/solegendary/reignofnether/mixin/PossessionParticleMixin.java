package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.hero.PossessionClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Particles born right in front of the possessed unit's camera are shrunk once, as they are added. Every effect in
 * the game is sized for the RTS camera forty blocks up; in the unit feed a melee sweep or a Wind Monk's gust two
 * blocks away is a wall of white pixels that hides the fight (lovish's 23:07 clip, Leafblade feed, 16-28 s). The
 * scale ramps from {@link #FULL_AT} blocks (untouched) down to {@link #MIN_SCALE} at the camera, so nothing pops
 * when the unit walks through smoke. Nothing changes outside possession.
 */
@Mixin(ParticleEngine.class)
public class PossessionParticleMixin {

    private static final double FULL_AT = 4.0;
    private static final float MIN_SCALE = 0.25f;

    @Inject(method = "add(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"))
    private void bar$shrinkNearPossessedCamera(Particle particle, CallbackInfo ci) {
        if (!PossessionClientEvents.isPossessing())
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null)
            return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        AABB bb = particle.getBoundingBox();
        double dx = (bb.minX + bb.maxX) * 0.5 - cam.x;
        double dy = (bb.minY + bb.maxY) * 0.5 - cam.y;
        double dz = (bb.minZ + bb.maxZ) * 0.5 - cam.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist >= FULL_AT)
            return;
        float scale = MIN_SCALE + (1f - MIN_SCALE) * (float) (dist / FULL_AT);
        particle.scale(scale);
    }
}
