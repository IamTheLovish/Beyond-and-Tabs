package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.orthoview.OrthoFaceCulling;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In the RTS (orthographic) view, Embeddium's block-face culling takes its per-section face mask from the camera's
 * look direction instead of its position (OrthoFaceCulling has the why: the position rule dropped the south / west
 * faces of everything south / west of the camera, the dark 1-block seams in the eyes stills).
 * <p>
 * DefaultChunkRenderer.getVisibleFaces only runs for the main view: Oculus turns block-face culling off for the
 * shadow pass before it is called, so the sun's shadow map is untouched. Outside the RTS view (hero possession, the
 * perspective camera) Embeddium's own rule stands.
 * <p>
 * Optional: {@code @Pseudo} and {@code require = 0}, so the plain client (no Embeddium) loads without it.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.DefaultChunkRenderer", remap = false)
public abstract class EmbeddiumOrthoFaceCullingMixin {

    @Inject(method = "getVisibleFaces", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void reignofnether$orthoFaces(int originX, int originY, int originZ, int chunkX, int chunkY,
                                                 int chunkZ, CallbackInfoReturnable<Integer> cir) {
        if (!OrthoviewClientEvents.isEnabled())
            return;
        var look = Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector();
        cir.setReturnValue(OrthoFaceCulling.visibleFaces(look.x(), look.y(), look.z()));
    }
}
