package com.solegendary.reignofnether.mixin;

import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.locks.Lock;

/**
 * design/code_review.md CR-11: every synched-data read - {@code Unit.getOwnerName()}, {@code getHealth()}, the shared
 * flags - went through {@code SynchedEntityData.getItem}, which takes and releases a {@code ReentrantReadWriteLock}
 * read lock around one hash lookup. The per-unit passes of an 8v8 (queue lines, the stats table, formation, targeting,
 * the bots' enemy scans: tens of thousands of owner and health reads a second) paid two atomic operations per read.
 * Caching the owner in a field instead would have meant editing all ~100 unit classes (each declares its own
 * accessor); removing the lock fixes every read at once.
 * <p>
 * Why it is safe: the lock never protected anything a game thread relies on. Each entity's data is read and written on
 * its own side's main thread (server thread for server entities, client thread for client entities); the map behind
 * getItem is only structurally changed by {@code define}, during construction; and later versions of the game dropped
 * this lock altogether. The one way this mod used to touch game state off-thread - wall-clock
 * {@code CompletableFuture.delayedExecutor} callbacks - now runs on the game thread (taskscheduler.TickScheduler); the
 * pathfinder workers read only their own walkability snapshots, never an entity.
 * <p>
 * The writer side (define, under the write lock) is untouched. Both redirects must apply (the default require = 1): if a
 * future game version changes getItem, the mod fails at startup instead of silently keeping the cost.
 */
@Mixin(SynchedEntityData.class)
public abstract class SynchedEntityDataMixin {

    @Redirect(method = "getItem", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/locks/Lock;lock()V"))
    private void reignofnether$skipReadLock(Lock lock) { }

    @Redirect(method = "getItem", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/locks/Lock;unlock()V"))
    private void reignofnether$skipReadUnlock(Lock lock) { }
}
