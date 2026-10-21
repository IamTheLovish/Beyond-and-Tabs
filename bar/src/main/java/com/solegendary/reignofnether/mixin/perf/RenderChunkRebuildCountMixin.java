package com.solegendary.reignofnether.mixin.perf;

import com.solegendary.reignofnether.perf.SectionRebuilds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Counts vanilla's section rebuilds for {@link SectionRebuilds}: every compile, async (rebuildChunkAsync) or on the
 * render thread (compileSync), starts at createCompileTask. Render thread only; one array lookup per rebuild.
 */
@Mixin(ChunkRenderDispatcher.RenderChunk.class)
public abstract class RenderChunkRebuildCountMixin {

    @Shadow public abstract BlockPos getOrigin();

    @Inject(method = "createCompileTask", at = @At("HEAD"))
    private void reignofnether$countRebuild(RenderRegionCache cache, CallbackInfoReturnable<?> cir) {
        BlockPos o = getOrigin();
        boolean noBlocks = false;
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            LevelChunk chunk = level.getChunkSource().getChunk(o.getX() >> 4, o.getZ() >> 4, false);
            int i = level.getSectionIndex(o.getY());
            noBlocks = chunk == null || i < 0 || i >= chunk.getSections().length || chunk.getSections()[i].hasOnlyAir();
        }
        SectionRebuilds.count(noBlocks, "vanilla");
    }
}
