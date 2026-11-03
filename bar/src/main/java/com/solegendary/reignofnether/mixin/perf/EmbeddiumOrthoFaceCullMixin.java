package com.solegendary.reignofnether.mixin.perf;

import com.solegendary.reignofnether.orthoview.OrthoFaceCull;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.postfx.ShaderPacks;
import net.minecraft.client.Minecraft;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Embeddium's block-face culling judges faces from the camera point, which is wrong for the orthographic RTS view: tall
 * buildings near the bottom of the screen lost their front walls and roofs (see {@link OrthoFaceCull}). Each terrain
 * pass ({@code render}) takes the view direction once; {@code getVisibleFaces} then gets the camera point pushed
 * {@link OrthoFaceCull#BACK} blocks back along it. Its other uses of the camera (placing the regions) are untouched.
 * <p>
 * Not in a shader pack's shadow pass: that pass draws from the sun, not along our view. Optional: {@code @Pseudo} and
 * {@code require = 0}, so the plain client (no Embeddium) loads without it and a later Embeddium that renames the
 * methods only brings the old culling back, never stops the game.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.DefaultChunkRenderer", remap = false)
public abstract class EmbeddiumOrthoFaceCullMixin {

    @Inject(method = "render", at = @At("HEAD"), remap = false, require = 0)
    private void reignofnether$orthoFaceCullPass(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        boolean ortho = OrthoviewClientEvents.isEnabled() && mc.gameRenderer != null && !ShaderPacks.renderingShadowPass();
        if (!ortho) {
            OrthoFaceCull.beginPass(false, 0, 0, 0);
            return;
        }
        Vector3f f = mc.gameRenderer.getMainCamera().getLookVector();
        OrthoFaceCull.beginPass(true, f.x(), f.y(), f.z());
    }

    @ModifyVariable(method = "getVisibleFaces", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false, require = 0)
    private static int reignofnether$orthoCamX(int x) {
        return OrthoFaceCull.camX(x);
    }

    @ModifyVariable(method = "getVisibleFaces", at = @At("HEAD"), argsOnly = true, ordinal = 1, remap = false, require = 0)
    private static int reignofnether$orthoCamY(int y) {
        return OrthoFaceCull.camY(y);
    }

    @ModifyVariable(method = "getVisibleFaces", at = @At("HEAD"), argsOnly = true, ordinal = 2, remap = false, require = 0)
    private static int reignofnether$orthoCamZ(int z) {
        return OrthoFaceCull.camZ(z);
    }
}
