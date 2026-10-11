package com.solegendary.reignofnether.mana;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.player.CommanderServerEvents;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Ink Scribe's <b>Seal</b> (design/stormcallers_plan.md 3.5): a sealed unit can't use abilities for a while - it
 * still walks and uses its basic attack ("silenced", not stunned). The state is the {@code SEALED} MobEffect, so vanilla
 * saves, syncs (the client sees the icon and the ink-coloured swirl) and expires it; this class is the one place that
 * applies it and the one check every ability path asks:
 * <ul>
 *   <li>UnitActionItem's ability dispatch refuses a sealed caster (the HUD tells its owner why);</li>
 *   <li>autocasts and the bot ask {@link #isSealed} before casting (Blink Strike, Leaf Dash, Kraken grab, the bot's
 *       commander / faction-power / Stormcaller habits);</li>
 *   <li>channelled spells end when their caster is sealed (the Thunder Sage's Ward).</li>
 * </ul>
 * Commanders are sealed for half as long ({@link #COMMANDER_DURATION_TICKS}): a seal must not lock a D-gun out for
 * long. Friendly units standing in a Ward are immune (WardServerEvents.protects). Cost: one effect lookup per check.
 */
public final class SealServerEvents {
    private SealServerEvents() { }

    public static final int DURATION_TICKS = 6 * 20;
    public static final int COMMANDER_DURATION_TICKS = 3 * 20;
    public static final String MSG_SEALED = "hud.mana.reignofnether.sealed";
    public static final String MSG_WARDED = "hud.mana.reignofnether.seal_warded";

    /** Is this unit sealed right now (both sides: the effect is synced)? */
    public static boolean isSealed(LivingEntity le) {
        return le != null && le.hasEffect(MobEffectRegistrar.SEALED.get());
    }

    /** How long a seal of {@code power} (Overcharge x1.5 scales its duration) lasts on this target. */
    public static int durationFor(LivingEntity target, float power) {
        int base = CommanderServerEvents.isCommander(target) ? COMMANDER_DURATION_TICKS : DURATION_TICKS;
        return Math.round(base * Math.max(1f, power));
    }

    /** Can {@code owner} seal this unit: an enemy unit, alive, and not inside a Ward of its own side. */
    public static boolean canSeal(String owner, LivingEntity target) {
        if (owner == null || target == null || !target.isAlive() || !(target instanceof Unit u))
            return false;
        String o = u.getOwnerName();
        if (o == null || o.isBlank() || o.equals(owner) || AlliancesServerEvents.isAllied(owner, o))
            return false;
        return !WardServerEvents.protects(target);
    }

    /**
     * Seals {@code target} for {@link #durationFor} ticks. The caller has already checked {@link #canSeal} and paid.
     * Returns the duration applied.
     */
    public static int seal(ServerLevel sl, LivingEntity target, float power) {
        int ticks = durationFor(target, power);
        target.addEffect(new MobEffectInstance(MobEffectRegistrar.SEALED.get(), ticks, 0, false, true, true));
        // the moment: an ink burst round the head and a brush-stroke sound
        sl.sendParticles(ParticleTypes.SQUID_INK, target.getX(), target.getY() + target.getBbHeight() * 0.9, target.getZ(),
            12, 0.3, 0.25, 0.3, 0.02);
        sl.sendParticles(ParticleTypes.ENCHANT, target.getX(), target.getY() + target.getBbHeight() + 0.3, target.getZ(),
            10, 0.3, 0.2, 0.3, 0.3);
        sl.playSound(null, target.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.HOSTILE, 2f, 0.6f);
        return ticks;
    }

    /** Lifts a seal (a Ward taking a friend in). */
    public static void unseal(LivingEntity le) {
        if (isSealed(le))
            le.removeEffect(MobEffectRegistrar.SEALED.get());
    }

    /** The HUD line the owner of a sealed unit sees when one of its orders is refused. */
    public static void warn(String owner) {
        if (owner != null && !owner.isBlank())
            HudClientboundPacket.showTempMessageI18n(owner, MSG_SEALED);
    }
}
