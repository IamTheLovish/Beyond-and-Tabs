package com.solegendary.reignofnether.brass;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The Shield Projector's bubble (design/brassworks_plan.md section 3.4): BastionAegis' dome rule on a small scale -
 * a shield of {@link #CAPACITY} projectile damage that <b>follows</b> one friendly unit for {@link #DURATION_TICKS},
 * covering it and every friend within {@link #RADIUS} of it. Melee and spells pass through. Soaked at LOW priority
 * after the Aegis (a dome soaks first) and after the Heat multiplier. A handful of records at most (one per cast,
 * 8 s each); the shimmer is a short ring every {@link #FX_EVERY_TICKS} ticks.
 */
public class ShieldServerEvents {

    public static final float RADIUS = 3f;
    public static final int DURATION_TICKS = 8 * 20;
    public static final float CAPACITY = 60f;
    static final int FX_EVERY_TICKS = 4;

    /** One live bubble. Public for the game test. */
    public static class Shield {
        public final ServerLevel level;
        public final LivingEntity anchor;
        public final String owner;
        public final long expiresAt;
        public float remaining = CAPACITY;

        Shield(ServerLevel level, LivingEntity anchor, String owner, long expiresAt) {
            this.level = level;
            this.anchor = anchor;
            this.owner = owner;
            this.expiresAt = expiresAt;
        }

        boolean covers(LivingEntity le) {
            return le.level() == level && anchor.isAlive() && le.distanceToSqr(anchor) <= RADIUS * RADIUS;
        }
    }

    static final List<Shield> SHIELDS = new ArrayList<>();

    /** Raises a bubble on {@code anchor}; a second cast on the same unit replaces the first. Public for the game test. */
    public static Shield raise(ServerLevel sl, LivingEntity anchor, String owner) {
        SHIELDS.removeIf(s -> s.anchor == anchor);
        Shield s = new Shield(sl, anchor, owner, sl.getGameTime() + DURATION_TICKS);
        SHIELDS.add(s);
        sl.sendParticles(ParticleTypes.END_ROD, anchor.getX(), anchor.getY() + 1, anchor.getZ(), 16, RADIUS * 0.5, 0.6, RADIUS * 0.5, 0.0);
        sl.playSound(null, anchor.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 1.5f, 1.8f);
        return s;
    }

    public static void dispel(Shield s) {
        SHIELDS.remove(s);
    }

    /** The live bubble on this unit, or null. */
    public static Shield on(LivingEntity anchor) {
        for (Shield s : SHIELDS)
            if (s.anchor == anchor && s.remaining > 0)
                return s;
        return null;
    }

    /** How much of a hit gets through: soaks projectile damage to a friend inside a live bubble. Public for the game test. */
    public static float absorb(LivingEntity target, DamageSource src, float amount) {
        if (SHIELDS.isEmpty() || amount <= 0 || !src.is(DamageTypeTags.IS_PROJECTILE) || !(target instanceof Unit u))
            return amount;
        String o = u.getOwnerName();
        if (o == null || !(target.level() instanceof ServerLevel sl))
            return amount;
        long now = sl.getGameTime();
        for (Shield s : SHIELDS) {
            if (s.remaining <= 0 || now >= s.expiresAt || !s.covers(target))
                continue;
            if (!s.owner.equals(o) && !AlliancesServerEvents.isAllied(s.owner, o))
                continue;
            float soaked = Math.min(amount, s.remaining);
            s.remaining -= soaked;
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 5, 0.3, 0.3, 0.3, 0.1);
            sl.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.NEUTRAL, 1.0f, 1.6f);
            if (s.remaining <= 0)
                sl.playSound(null, s.anchor.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 1.5f, 1.0f);
            return amount - soaked;
        }
        return amount;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(LivingHurtEvent evt) {
        if (SHIELDS.isEmpty() || evt.getEntity().level().isClientSide())
            return;
        float left = absorb(evt.getEntity(), evt.getSource(), evt.getAmount());
        if (left <= 0)
            evt.setCanceled(true);
        else if (left != evt.getAmount())
            evt.setAmount(left);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || SHIELDS.isEmpty())
            return;
        Iterator<Shield> it = SHIELDS.iterator();
        while (it.hasNext()) {
            Shield s = it.next();
            long now = s.level.getGameTime();
            if (now >= s.expiresAt || s.remaining <= 0 || !s.anchor.isAlive() || s.level.getServer() != evt.getServer()) {
                it.remove();
                continue;
            }
            if (now % FX_EVERY_TICKS != 0)
                continue;
            double a = s.level.random.nextDouble() * Mth.TWO_PI;
            s.level.sendParticles(ParticleTypes.END_ROD, s.anchor.getX() + Math.cos(a) * RADIUS, s.anchor.getY() + 0.3 + s.level.random.nextDouble() * 1.5,
                s.anchor.getZ() + Math.sin(a) * RADIUS, 1, 0, 0, 0, 0.0);
        }
    }

    /** Defeat / reset: the owner's bubbles go down. */
    public static void clearOwner(String owner) {
        SHIELDS.removeIf(s -> s.owner.equals(owner));
    }

    public static void clearAll() {
        SHIELDS.clear();
    }
}
