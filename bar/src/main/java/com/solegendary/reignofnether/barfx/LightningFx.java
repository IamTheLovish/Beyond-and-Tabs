package com.solegendary.reignofnether.barfx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * A lightning bolt drawn with server particles (design/stormcallers_plan.md: "StormOak's drawBolt lifted into a shared
 * barfx/LightningFx.bolt"). Used by the Verdant Storm Oak and the Stormcallers' Heaven's Judgement - never a vanilla
 * LightningBolt entity, which sets fires, hits friends and is drawn for every player in range.
 * <p>
 * Cost: a jagged line of ELECTRIC_SPARK particles about every 0.8 blocks (one small packet each per nearby player), so
 * a 12-block bolt is ~15 packets. Callers fire it once per strike, never per tick.
 */
public final class LightningFx {
    private LightningFx() { }

    /** Bolt segments: enough jags to read as lightning from the RTS camera, few enough to stay cheap. */
    public static final int SEGMENTS = 9;

    /**
     * A jagged spark line from (x0, y0, z0) to (x1, y1, z1). The jitter shrinks toward both ends so the bolt leaves its
     * source and meets its target cleanly. No flash, no sound - each caller adds its own.
     */
    public static void bolt(ServerLevel sl, double x0, double y0, double z0, double x1, double y1, double z1) {
        RandomSource rng = sl.getRandom();
        double px = x0, py = y0, pz = z0;
        for (int i = 1; i <= SEGMENTS; i++) {
            double t = (double) i / SEGMENTS;
            double jag = (i == SEGMENTS) ? 0 : 1.1 * Math.sin(Math.PI * t);
            double nx = x0 + (x1 - x0) * t + (rng.nextDouble() - 0.5) * jag;
            double ny = y0 + (y1 - y0) * t + (rng.nextDouble() - 0.5) * jag * 0.5;
            double nz = z0 + (z1 - z0) * t + (rng.nextDouble() - 0.5) * jag;
            double len = Math.sqrt((nx - px) * (nx - px) + (ny - py) * (ny - py) + (nz - pz) * (nz - pz));
            int steps = Math.max(1, (int) (len / 0.8));
            for (int k = 0; k < steps; k++) {
                double s = (double) k / steps;
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, px + (nx - px) * s, py + (ny - py) * s, pz + (nz - pz) * s,
                    1, 0.02, 0.02, 0.02, 0.0);
            }
            px = nx; py = ny; pz = nz;
        }
    }
}
