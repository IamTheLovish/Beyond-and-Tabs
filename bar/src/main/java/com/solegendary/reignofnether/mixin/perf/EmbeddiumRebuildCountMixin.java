package com.solegendary.reignofnether.mixin.perf;

import com.solegendary.reignofnether.perf.SectionRebuilds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Counts Embeddium's section rebuilds for {@link SectionRebuilds} (Embeddium 0.3.31 replaces vanilla's chunk renderer,
 * so RenderChunkRebuildCountMixin never runs there). RenderSectionManager.createRebuildTask is where every rebuild
 * starts; it returns null for a section with no blocks (WorldSlice.prepare), which Embeddium then skips.
 *
 * Optional: {@code @Pseudo} and {@code require = 0}, so the plain client (no Embeddium - the eyes plain lane, the Mac
 * pack) loads without it, and a later Embeddium that renames the method only loses the count, never the game.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
public abstract class EmbeddiumRebuildCountMixin {

    @Inject(method = "createRebuildTask", at = @At("RETURN"), remap = false, require = 0)
    private void reignofnether$countRebuild(CallbackInfoReturnable<Object> cir) {
        SectionRebuilds.count(cir.getReturnValue() == null, "embeddium");
    }
}
