package com.solegendary.reignofnether.mana;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The Thunder Sage's <b>Ward</b> (design/stormcallers_plan.md, slice 2): a gold sigil {@link #RADIUS} blocks across on
 * the ground where the Sage stands, for up to {@link #DURATION_TICKS} ticks. Friendly units inside (the owner's and
 * allies') take {@link #REDUCTION} less projectile and spell damage and can't be sealed (a seal on one is lifted as it
 * comes in). It is a <b>channelled</b> spell: {@link #ENERGY_PER_SECOND} energy a second through
 * {@link ManaServerEvents#openChannel} / {@link ManaServerEvents#channel}; the first second is paid at the cast, and
 * when a second can't be paid at all the sigil collapses early. It also ends if the Sage dies or is sealed.
 * Overcharge (x1.5 power) widens the sigil.
 * <p>
 * The FogBank/BastionAegis area-record pattern: a short list (one ward per Sage, so at most 16 at 8v8). The damage
 * hook is an empty-list check for every hurt event when no ward stands; the per-second pass pays the channel and
 * lifts seals with one grid query per ward; the sigil is a ring of dust particles every {@link #FX_TICKS} ticks.
 * Not saved: a ward lasts 8 s.
 */
public final class WardServerEvents {
    private WardServerEvents() { }

    public static final float RADIUS = 5f;
    public static final int HEIGHT = 4;
    public static final int DURATION_TICKS = 8 * 20;
    public static final float ENERGY_PER_SECOND = 12f;
    /** Fraction of projectile / spell damage a ward takes off a friend inside it. */
    public static final float REDUCTION = 0.30f;
    static final int FX_TICKS = 5;
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(0.95f, 0.78f, 0.25f), 1.2f);

    /** One live ward. Public for the game test. */
    public static final class Ward {
        public final ServerLevel level;
        public final String owner;
        public final LivingEntity caster;
        public final Vec3 centre;
        public final float radius;
        public final long startTick, expireTick;
        public final ManaServerEvents.Channel channel;
        boolean ended = false;

        Ward(ServerLevel level, String owner, LivingEntity caster, Vec3 centre, float radius, long startTick,
             ManaServerEvents.Channel channel) {
            this.level = level;
            this.owner = owner;
            this.caster = caster;
            this.centre = centre;
            this.radius = radius;
            this.startTick = startTick;
            this.expireTick = startTick + DURATION_TICKS;
            this.channel = channel;
        }

        public boolean isActive() { return !ended; }

        public boolean contains(LivingEntity le) {
            if (le.level() != level)
                return false;
            double dx = le.getX() - centre.x, dz = le.getZ() - centre.z;
            return dx * dx + dz * dz <= radius * radius && Math.abs(le.getY() - centre.y) <= HEIGHT;
        }

        boolean friendOf(String o) {
            return o != null && (o.equals(owner) || AlliancesServerEvents.isAllied(owner, o));
        }
    }

    private static final List<Ward> WARDS = new ArrayList<>();
    private static final List<LivingEntity> scratch = new ArrayList<>();   // server thread only

    /** Read-only view (game test / debugging). */
    public static List<Ward> wards() {
        return java.util.Collections.unmodifiableList(WARDS);
    }

    /**
     * Raises a ward at {@code centre} for {@code caster}'s owner, paying its first second. Returns null when the channel
     * is refused (ManaServerEvents.lastRefusal says why) - nothing raised, nothing paid. A Sage's earlier ward is
     * replaced. Public for the ability, the bot and the game test (no cooldown handling here).
     */
    public static Ward raise(ServerLevel sl, LivingEntity caster, String owner, Vec3 centre) {
        if (caster == null || owner == null || owner.isBlank())
            return null;
        ManaServerEvents.Channel ch = ManaServerEvents.openChannel(caster, owner, ENERGY_PER_SECOND);
        if (ch == null)
            return null;
        for (int i = WARDS.size() - 1; i >= 0; i--)
            if (WARDS.get(i).caster == caster)
                end(WARDS.remove(i), false);
        Ward w = new Ward(sl, owner, caster, centre, RADIUS * Math.max(1f, ch.power()), sl.getGameTime(), ch);
        WARDS.add(w);
        liftSeals(w);
        ring(w, 40);
        sl.playSound(null, BlockPos.containing(centre), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.NEUTRAL, 2.5f, 0.7f);
        sl.playSound(null, BlockPos.containing(centre), SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 1.5f, 1.4f);
        return w;
    }

    /** Is this unit inside a live ward of its own side (immune to Seal)? */
    public static boolean protects(LivingEntity le) {
        return wardOf(le) != null;
    }

    private static Ward wardOf(LivingEntity le) {
        if (WARDS.isEmpty() || !(le instanceof Unit u))
            return null;
        String o = u.getOwnerName();
        for (int i = 0; i < WARDS.size(); i++) {
            Ward w = WARDS.get(i);
            if (!w.ended && w.friendOf(o) && w.contains(le))
                return w;
        }
        return null;
    }

    /** Projectiles and spells (magic, indirect magic) - what a ward softens; melee, fire and blasts pass. */
    public static boolean isWardable(DamageSource src) {
        return src.is(DamageTypeTags.IS_PROJECTILE) || src.is(DamageTypes.MAGIC) || src.is(DamageTypes.INDIRECT_MAGIC);
    }

    /** How much of a hit gets through to {@code target}. Public for the game test. */
    public static float reduce(LivingEntity target, DamageSource src, float amount) {
        if (WARDS.isEmpty() || amount <= 0 || !isWardable(src))
            return amount;
        Ward w = wardOf(target);
        if (w == null)
            return amount;
        if (target.level() instanceof ServerLevel sl)
            sl.sendParticles(ParticleTypes.ENCHANT, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                6, 0.3, 0.3, 0.3, 0.4);
        return amount * (1f - REDUCTION);
    }

    // after RoN's own hurt handlers (its projectile rewrite) so the cut is on the final number
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(LivingHurtEvent evt) {
        if (WARDS.isEmpty() || evt.getEntity().level().isClientSide())
            return;
        float left = reduce(evt.getEntity(), evt.getSource(), evt.getAmount());
        if (left != evt.getAmount())
            evt.setAmount(left);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || WARDS.isEmpty())
            return;
        for (int i = WARDS.size() - 1; i >= 0; i--) {
            Ward w = WARDS.get(i);
            if (w.level.getServer() != evt.getServer())
                continue;
            if (!tickWard(w))
                end(WARDS.remove(i), true);
        }
    }

    /** One tick of a ward; false when it must collapse. Public for the game test. */
    public static boolean tickWard(Ward w) {
        long now = w.level.getGameTime();
        long age = now - w.startTick;
        if (w.ended || now >= w.expireTick || !w.caster.isAlive() || SealServerEvents.isSealed(w.caster))
            return false;
        // the channel is paid once a second (the first second was paid at the cast); starved to nothing = collapse
        if (age > 0 && age % 20 == 0) {
            ManaServerEvents.channel(w.channel);
            if (!w.channel.isActive())
                return false;
            liftSeals(w);
        }
        if (age % FX_TICKS == 0)
            ring(w, 10);
        return true;
    }

    /** Friends who come into the sigil shake off a seal (once a second; the immunity itself is protects()). */
    static void liftSeals(Ward w) {
        for (LivingEntity le : UnitGrid.near(w.level, w.centre.x, w.centre.z, w.radius + 1, scratch))
            if (le instanceof Unit u && w.friendOf(u.getOwnerName()) && w.contains(le))
                SealServerEvents.unseal(le);
        scratch.clear();
    }

    static void end(Ward w, boolean sound) {
        if (w.ended)
            return;
        w.ended = true;
        w.channel.stop();
        if (sound)
            w.level.playSound(null, BlockPos.containing(w.centre), SoundEvents.BEACON_DEACTIVATE, SoundSource.NEUTRAL, 1.5f, 1.3f);
    }

    /** Takes a ward down now (the game test cleans up with this). */
    public static void dispel(Ward w) {
        if (WARDS.remove(w))
            end(w, false);
    }

    // the sigil: points on the rim (and a few inside) in gold dust, server particles so both sides read the edge
    private static void ring(Ward w, int n) {
        double phase = (w.level.getGameTime() % 40) / 40.0 * Math.PI * 2;
        for (int i = 0; i < n; i++) {
            double a = phase + Math.PI * 2 * i / n;
            w.level.sendParticles(GOLD, w.centre.x + Math.cos(a) * w.radius, w.centre.y + 0.15, w.centre.z + Math.sin(a) * w.radius,
                1, 0, 0, 0, 0);
        }
        w.level.sendParticles(ParticleTypes.ENCHANT, w.centre.x, w.centre.y + 0.6, w.centre.z, Math.max(2, n / 3),
            w.radius * 0.5, 0.2, w.radius * 0.5, 0.5);
    }

    /** Defeat: the owner's wards collapse. */
    public static void clearOwner(String owner) {
        for (int i = WARDS.size() - 1; i >= 0; i--)
            if (WARDS.get(i).owner.equals(owner))
                end(WARDS.remove(i), false);
    }

    public static void clearAll() {
        for (Ward w : WARDS)
            end(w, false);
        WARDS.clear();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        clearAll();
    }
}
