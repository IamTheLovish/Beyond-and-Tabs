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
 * <li>look - a charged bolt (lovish, Nov 5: the Stormcallers' rocket should read as lightning, not gunpowder):
 * RocketShellRenderer draws a glowing blue-white bolt along its path; the jagged flickering trail, the muzzle flash and the
 * impact (a blue-white flash and a branching crack across the ground) come from BarFx, which classes it {@code K_BOLT}
 * (BarFx.kindOf);</li>
 * <li>burst - a small thunderclap instead of the mortar's smoke and thud ({@link #burstEffects}).</li>
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
        // a small thunderclap: electric sparks and a few white motes flung out (the blue-white flash and the branching crack
        // along the ground are BarFx's, K_BOLT), a sharp crack and a low rumble
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.25, z, 18, splash * 0.35, 0.2, splash * 0.35, 0.35);
        sl.sendParticles(ParticleTypes.END_ROD, x, y + 0.4, z, 5, splash * 0.3, 0.25, splash * 0.3, 0.06);
        sl.sendParticles(ParticleTypes.CLOUD, x, y + 0.3, z, 3, splash * 0.2, 0.1, splash * 0.2, 0.02);
        sl.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 0.9f, 1.55f);
        sl.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 0.5f, 1.1f);
    }
}
