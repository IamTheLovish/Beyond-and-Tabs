package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.hero.HeroFx;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Sunforged (Cathedral Warden): <b>Sanctify</b> (second-t3-design.md). A gold ring pulses out from the Warden: every
 * friendly and allied unit within {@link #RADIUS} (the Warden too) heals {@link #HEAL_FRACTION} of its max HP and is
 * cleansed of wither, burning and slowness. {@link #CD_SECONDS} s cooldown, no energy.
 */
public class Sanctify extends Ability {

    public static final int CD_SECONDS = 25;
    public static final float RADIUS = 10f;
    public static final float HEAL_FRACTION = 0.25f;

    public Sanctify() {
        super(UnitAction.SANCTIFY, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, 0, RADIUS, false, true);
        this.autoRule(AutoRule.ALLY_HURT);
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Sanctify",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/golden_apple.png"),
            hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> UnitClientEvents.sendUnitCommand(UnitAction.SANCTIFY),
            null,
            List.of(
                FormattedCharSequence.forward("Sanctify  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("A gold pulse: friends within " + (int) RADIUS + " blocks heal "
                    + (int) (HEAL_FRACTION * 100) + "% of their max HP and shake off wither, burning and slowness.", Style.EMPTY)
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof Mob self) || !(level instanceof ServerLevel sl))
            return;
        pulse(sl, self);
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    /** The pulse: heals and cleanses every friend within {@link #RADIUS} of {@code self}. Returns how many were healed. Public for the game test. */
    public static int pulse(ServerLevel sl, Mob self) {
        Vec3 c = self.position();
        int n = 0;
        for (LivingEntity le : HeroFx.friends(sl, self, c, RADIUS)) {
            if (!le.isAlive())
                continue;
            le.heal(le.getMaxHealth() * HEAL_FRACTION);
            le.removeEffect(MobEffects.WITHER);
            le.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            le.clearFire();
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 4, 0.3, 0.4, 0.3, 0.02);
            n++;
        }
        HeroFx.ring(sl, ParticleTypes.END_ROD, c, RADIUS);
        HeroFx.ring(sl, ParticleTypes.END_ROD, c, RADIUS * 0.5f);
        sl.playSound(null, self.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, net.minecraft.sounds.SoundSource.NEUTRAL, 2f, 1.0f);
        return n;
    }
}
