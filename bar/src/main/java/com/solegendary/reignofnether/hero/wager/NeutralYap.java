package com.solegendary.reignofnether.hero.wager;

import com.solegendary.reignofnether.hero.HeroFx;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * "Lovish yapping" (lovish's answer to Q7): sometimes a refusal turns into a monologue that casts a random effect that
 * is neither good nor bad - cosmetic weather, a harmless swap, a silly sound, a short neutral status. Nothing here
 * deals damage, heals, pays, spawns or takes anything: it is flavour, not a consolation prize (the GameTest
 * wager_yapping_casts_only_neutral_effects checks each one).
 */
public final class NeutralYap {
    private NeutralYap() { }

    public static final List<String> EFFECTS = List.of("confetti", "snow_flurry", "silly_sound", "bubbles", "slow_fall", "swap");

    /** Casts neutral effect {@code index} (mod the list) around the hero; returns its id. */
    public static String cast(ServerLevel level, Mob hero, int index) {
        String id = EFFECTS.get(Math.floorMod(index, EFFECTS.size()));
        Vec3 at = hero.position();
        switch (id) {
            case "confetti" -> HeroFx.particles(level, ParticleTypes.FIREWORK, at.add(0, 2, 0), 30, 0.8, 0.1);
            case "snow_flurry" -> HeroFx.particles(level, ParticleTypes.SNOWFLAKE, at.add(0, 3, 0), 40, 2.0, 0.02);
            case "silly_sound" -> HeroFx.sound(level, at, SoundEvents.GOAT_SCREAMING_AMBIENT, 1.0f, 1.6f);
            // bubbles instead of a glow: an outline through walls would tell the enemy where he is (Mode A note 5)
            case "bubbles" -> HeroFx.particles(level, ParticleTypes.BUBBLE_POP, at.add(0, 1.5, 0), 25, 0.6, 0.05);
            case "slow_fall" -> hero.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, true));
            case "swap" -> swap(level, hero);
            default -> { }
        }
        HeroFx.particles(level, ParticleTypes.NOTE, at.add(0, 2.2, 0), 3, 0.4, 0.5);
        return id;
    }

    /** He and his nearest own ungarrisoned unit within 6 trade places (nothing else moves, nobody is hurt). */
    static void swap(ServerLevel level, Mob hero) {
        LivingEntity other = null;
        double best = 36;
        String owner = hero instanceof Unit hu ? hu.getOwnerName() : null;
        for (LivingEntity e : HeroFx.friends(level, hero, hero.position(), 6)) {
            double d = e.distanceToSqr(hero);
            // his own units only, never an ally's or one garrisoned in a tower
            if (e != hero && d < best && e instanceof Unit u && u.getOwnerName() != null && u.getOwnerName().equals(owner)
                    && !u.isGarrisoned()) {
                best = d;
                other = e;
            }
        }
        if (other == null)
            return;
        Vec3 a = hero.position(), b = other.position();
        hero.getNavigation().stop();
        hero.teleportTo(b.x, b.y, b.z);
        other.teleportTo(a.x, a.y, a.z);
        HeroFx.trail(level, ParticleTypes.PORTAL, a.add(0, 1, 0), b.add(0, 1, 0));
    }
}
