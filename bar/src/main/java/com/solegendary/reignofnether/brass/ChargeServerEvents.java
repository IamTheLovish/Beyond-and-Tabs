package com.solegendary.reignofnether.brass;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.taskscheduler.TaskSchedulerServerEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The Sapper's timed charge (design/brassworks_plan.md section 3.5): planted against an enemy building, it fizzes for
 * {@link #FUSE_TICKS} (a sparking fuse at the wall, so the defender sees it coming) and then deals
 * {@link #CHARGE_DAMAGE} building damage through {@link BuildingPlacement#destroyRandomBlocks(double, net.minecraft.world.entity.Entity)}.
 * No explosion entity: no terrain outside the building, no units hurt, no friends. A handful of scheduled tasks per
 * charge; nothing per tick.
 */
public final class ChargeServerEvents {
    private ChargeServerEvents() { }

    public static final int FUSE_TICKS = 80;
    public static final float CHARGE_DAMAGE = 120f;
    static final int FUSE_FX_EVERY = 10;

    /** Plants a charge on {@code target} at {@code at}; the blast is credited to {@code sapper}. Public for the game test. */
    public static void plant(ServerLevel sl, LivingEntity sapper, BuildingPlacement target, Vec3 at) {
        sl.playSound(null, BlockPos.containing(at), SoundEvents.TNT_PRIMED, SoundSource.NEUTRAL, 1.2f, 1.1f);
        for (int t = FUSE_FX_EVERY; t < FUSE_TICKS; t += FUSE_FX_EVERY)
            TaskSchedulerServerEvents.schedule(t, () -> {
                sl.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 3, 0.15, 0.2, 0.15, 0.02);
                sl.sendParticles(ParticleTypes.SMOKE, at.x, at.y + 0.5, at.z, 2, 0.1, 0.2, 0.1, 0.01);
            });
        TaskSchedulerServerEvents.schedule(FUSE_TICKS, () -> detonate(sl, sapper, target, at));
    }

    /** The blast: building damage only. Public for the game test. */
    public static void detonate(ServerLevel sl, LivingEntity sapper, BuildingPlacement target, Vec3 at) {
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.5, at.z, 12, 0.8, 0.6, 0.8, 0.05);
        sl.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 2.0f, 0.9f);
        if (target == null || target.isDestroyedServerside || !BuildingServerEvents.getBuildings().contains(target))
            return;
        target.destroyRandomBlocks(CHARGE_DAMAGE, sapper);
    }
}
