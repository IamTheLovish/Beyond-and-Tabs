package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.gametest.GameTestChunks;
import com.solegendary.reignofnether.gametest.TestStartGate;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Holds each GameTest's first tick until its structure's chunks are ready for entities ({@link TestStartGate}).
 * <p>
 * Vanilla's GameTestInfo.tick computes {@code tickCount = gameTime - startTick} and runs the test function at
 * tickCount 0. While the gate holds, this cancels the tick and moves {@code startTick} to the next game tick, so the
 * test's tick 0 (and every tick number, delayed task and timeout counted from it) comes on the first tick at which
 * its arena is entity-ready. Setup ticks (gameTime still before startTick) are left to vanilla. Only the headless
 * GameTest server ({@link GameTestChunks#asyncStructureLoad}); normal play never runs a GameTestInfo.
 */
@Mixin(GameTestInfo.class)
public abstract class GameTestInfoMixin {

    @Shadow
    private long startTick;

    @Unique
    private TestStartGate.Gate reignofnether$startGate;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void reignofnether$holdStartUntilEntitiesReady(CallbackInfo ci) {
        if (reignofnether$startGate != null && reignofnether$startGate.isOpen())
            return;
        GameTestInfo self = (GameTestInfo) (Object) this;
        ServerLevel level = self.getLevel();
        if (self.isDone() || !GameTestChunks.asyncStructureLoad(level) || level.getGameTime() < startTick)
            return;
        if (reignofnether$startGate == null)
            reignofnether$startGate = new TestStartGate.Gate();
        if (TestStartGate.hold(self, level, reignofnether$startGate)) {
            startTick = level.getGameTime() + 1;
            ci.cancel();
        }
    }
}
