package com.solegendary.reignofnether.unit.units.piglins;

import com.solegendary.reignofnether.ability.abilities.BloodTotem;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.AbstractTotem;
import com.solegendary.reignofnether.util.MiscUtil;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * The Voodoo Shaman's <b>Blood Totem</b> ({@link BloodTotem}): a bought-totem body (AbstractTotem: 20 HP, it stands, it
 * can be killed) that lives {@link BloodTotem#DURATION_SECONDS} s. Every half second, every unit within
 * {@link BloodTotem#RADIUS} of it gets a short effect: an ally BLOOD_FRENZY ({@link #ATTACK_SPEED_BONUS} faster attacks,
 * read in AttackerUnit.getAttackCooldownMultiplier), anyone else BLOOD_TOTEM_SLOW ({@link #ENEMY_SLOW} off its move
 * speed). The effects outlast one pulse by half a second, so leaving the circle (or the totem falling) ends them within
 * a second. The base's own ally aura is left empty. Always spawned marked summoned (no essence on death).
 */
public class BloodTotemUnit extends AbstractTotem {

    public static final float ATTACK_SPEED_BONUS = 0.25f;
    public static final float ENEMY_SLOW = 0.15f;
    static final int PULSE_TICKS = 10;
    static final int EFFECT_TICKS = 20;

    public BloodTotemUnit(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
        this.lifeTimeTicks = BloodTotem.DURATION_SECONDS * 20;
    }

    @Override
    public void tick() {
        super.tick();
        if (true) return;   // STUB (tests-first commit): no aura yet
        if (!(level() instanceof ServerLevel sl) || !isAlive() || tickCount % PULSE_TICKS != 1)
            return;
        double r = BloodTotem.RADIUS;
        for (Mob mob : sl.getEntitiesOfClass(Mob.class, new AABB(position(), position()).inflate(r))) {
            if (mob == this || !mob.isAlive() || !(mob instanceof Unit unit) || mob instanceof AbstractTotem
                    || mob.distanceToSqr(this) > r * r)
                continue;
            if (AlliancesServerEvents.isAlliedOrOwned(unit.getOwnerName(), getOwnerName()))
                mob.addEffect(new MobEffectInstance(MobEffectRegistrar.BLOOD_FRENZY.get(), EFFECT_TICKS, 0, false, true));
            else
                mob.addEffect(new MobEffectInstance(MobEffectRegistrar.BLOOD_TOTEM_SLOW.get(), EFFECT_TICKS, 0, false, true));
        }
        if (tickCount % (PULSE_TICKS * 4) == 1)
            sl.sendParticles(ParticleTypes.DAMAGE_INDICATOR, getX(), getY() + 1.2, getZ(), 4, 0.25, 0.3, 0.25, 0.02);
    }

    /** The range circle shows the totem's own radius, not the bought totems' 10. */
    @Override
    public void updateHighlightBps(Level level) {
        if (level().isClientSide()) {
            getHighlightBps().clear();
            getHighlightBps().addAll(MiscUtil.getRangeIndicatorCircleBlocks(blockPosition(), BloodTotem.RADIUS - 1, level()));
        }
    }
}
