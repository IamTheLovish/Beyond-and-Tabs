package com.solegendary.reignofnether.unit.units.storm;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.units.tide.BombardShell;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * The Rocket Battery's rocket (lovish, Nov 5: "change the rocket bots motor shots to another animation and
 * projectile"): the Bombard Crew's {@link BombardShell} with its own entity type, flight, model and burst.
 * <p>
 * What it shares, on purpose, is every rule: the splash, its falloff, what a building and each target class takes
 * ({@code BombardShell.burst}, BuildingDamage, FieldArtillery's no-air rule, the {@code instanceof BombardShell} checks
 * on the Leviathan and the Ghost Furnace), so the artillery balance has one entry and cannot fork. What is its own:
 * <ul>
 * <li>flight - {@link RocketFlight}: about half the mortar's time on a flatter arc, through the same solve
 * ({@link #flightTicksAt}, {@link #getGravity});</li>
 * <li>look - RocketShellRenderer draws a finned rocket with a flame, along its path; the smoke trail, the muzzle flash
 * and the impact flash come from BarFx, which classes it {@code K_ROCKET} (BarFx.kindOf);</li>
 * <li>burst - sparks, flame and a firework crack instead of the mortar's smoke and thud ({@link #burstEffects}).</li>
 * </ul>
 */
public class RocketShell extends BombardShell {

    public RocketShell(EntityType<? extends RocketShell> type, Level level) {
        super(type, level);
    }

    public RocketShell(Level level, LivingEntity shooter, float damage) {
        super(EntityRegistrar.ROCKET_SHELL.get(), level, shooter, damage);
    }

    /** Only the default item of a thrown projectile; RocketShellRenderer draws the rocket itself. */
    @Override
    protected @NotNull Item getDefaultItem() {
        return Items.FIREWORK_ROCKET;
    }

    @Override
    protected float getGravity() {
        return (float) RocketFlight.GRAVITY;
    }

    @Override
    protected int flightTicksAt(double flat) {
        return RocketFlight.flightTicks(flat);
    }

    @Override
    protected void burstEffects(ServerLevel sl, double x, double y, double z, float splash) {
        sl.sendParticles(ParticleTypes.EXPLOSION, x, y + 0.3, z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.FLAME, x, y + 0.2, z, 10, splash * 0.3, 0.15, splash * 0.3, 0.07);
        sl.sendParticles(ParticleTypes.FIREWORK, x, y + 0.3, z, 12, splash * 0.35, 0.3, splash * 0.35, 0.1);
        sl.sendParticles(ParticleTypes.SMOKE, x, y + 0.4, z, 6, splash * 0.3, 0.25, splash * 0.3, 0.04);
        sl.playSound(null, x, y, z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.HOSTILE, 1.5f, 0.85f);
        sl.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.9f, 1.7f);
    }
}
