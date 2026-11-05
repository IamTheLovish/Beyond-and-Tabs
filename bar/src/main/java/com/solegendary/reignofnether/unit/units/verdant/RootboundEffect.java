package com.solegendary.reignofnether.unit.units.verdant;

import com.solegendary.reignofnether.registrars.MobEffectRegistrar;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * <b>Rootbound</b> (lovish, Oct 28: "rooted champs get slowed and damaged overtime that stacks the more they get
 * rooted"): what a Rooted Treant's spike line leaves on every enemy it hits. Each hit adds a stack (up to
 * {@link #MAX_STACKS}) and refreshes it to {@link #SECONDS}; each stack slows by {@link #SLOW_PER_STACK} and deals
 * {@link #DAMAGE_PER_STACK} a second. The stack count is the effect's amplifier + 1, so vanilla saves, syncs and expires
 * it, the slow scales with it (a MULTIPLY_BASE modifier is multiplied by amplifier + 1) and cleanses remove it.
 */
public class RootboundEffect extends MobEffect {

    public static final int SECONDS = 4;
    public static final int MAX_STACKS = 3;
    public static final double SLOW_PER_STACK = 0.12;          // 3 stacks: 36% slower
    public static final float DAMAGE_PER_STACK = 1f;           // each second; 3 stacks: 3 a second

    public RootboundEffect() {
        super(MobEffectCategory.HARMFUL, 0x6B4A2B);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, "5d1e8a3c-7b42-4f19-a6d0-2c9e4b7f1a58", -SLOW_PER_STACK, AttributeModifier.Operation.MULTIPLY_BASE);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Override
    public void applyEffectTick(LivingEntity le, int amplifier) {
        if (!le.level().isClientSide())
            le.hurt(le.damageSources().magic(), DAMAGE_PER_STACK * (amplifier + 1));
    }

    /** Stacks on {@code le} now: 0 when it is not rootbound. */
    public static int stacks(LivingEntity le) {
        MobEffectInstance e = le.getEffect(MobEffectRegistrar.ROOTBOUND.get());
        return e == null ? 0 : e.getAmplifier() + 1;
    }

    /** One more stack (capped at MAX_STACKS) and the time refreshed. Returns the stacks it now has. */
    public static int addStack(LivingEntity le) {
        int next = Math.min(MAX_STACKS, stacks(le) + 1);
        le.removeEffect(MobEffectRegistrar.ROOTBOUND.get());   // a lower amplifier would not replace a higher one: start clean
        le.addEffect(new MobEffectInstance(MobEffectRegistrar.ROOTBOUND.get(), SECONDS * 20, next - 1));
        return next;
    }
}
