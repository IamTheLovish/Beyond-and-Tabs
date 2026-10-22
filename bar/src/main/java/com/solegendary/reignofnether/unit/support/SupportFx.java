package com.solegendary.reignofnether.unit.support;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * The healer's tells (claude/healer-ai-oct21.md, G "feel"): when the Field Medic winds up a throw, a ring of dust marks
 * where it will land - gold round the friend a heal goes to, amber the size of the cloud where a harming potion will
 * burst, so a player sees an incoming cloud and has the wind-up to step out. One ring per throw (every 8-12 s per
 * medic): {@link #POINTS} particles, no packets of our own beyond vanilla's particle sends.
 */
public final class SupportFx {
    private SupportFx() { }

    public static final int POINTS = 12;
    static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0f, 0.82f, 0.30f), 1.0f);
    static final DustParticleOptions AMBER = new DustParticleOptions(new Vector3f(1.0f, 0.50f, 0.08f), 1.2f);

    /** The ring for a throw about to be released at (x, z); {@code target} is the unit it goes to, if any. */
    public static void telegraph(ServerLevel level, @Nullable Potion potion, double x, double y, double z, @Nullable LivingEntity target) {
        if (potion == null)
            return;
        double gy = target != null ? target.getY() + 0.1 : y + 0.1;
        if (potion == Potions.STRONG_HARMING || potion == Potions.HARMING)
            ring(level, AMBER, x, gy, z, SupportMath.CLOUD_RADIUS);
        else if (potion == Potions.STRONG_REGENERATION || potion == Potions.REGENERATION)
            ring(level, GOLD, x, gy, z, SupportMath.CLOUD_RADIUS);
        else if (potion == Potions.STRONG_HEALING || potion == Potions.HEALING)
            ring(level, GOLD, x, gy, z, target != null ? Math.max(0.8, target.getBbWidth()) : 1.0);
    }

    static void ring(ServerLevel level, DustParticleOptions dust, double x, double y, double z, double r) {
        for (int i = 0; i < POINTS; i++) {
            double a = i * (Math.PI * 2 / POINTS);
            level.sendParticles(dust, x + Math.cos(a) * r, y, z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }
}
