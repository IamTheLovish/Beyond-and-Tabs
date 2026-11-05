package com.solegendary.reignofnether.unit.units.storm;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.units.tide.BombardShell;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * The Rocket Battery's rocket: a {@link BombardShell} with its own entity type, flight, look and burst. STUB: nothing
 * fires it yet.
 */
public class RocketShell extends BombardShell {

    public RocketShell(EntityType<? extends RocketShell> type, Level level) {
        super(type, level);
    }

    public RocketShell(Level level, LivingEntity shooter, float damage) {
        super(EntityRegistrar.ROCKET_SHELL.get(), level, shooter, damage);
    }
}
