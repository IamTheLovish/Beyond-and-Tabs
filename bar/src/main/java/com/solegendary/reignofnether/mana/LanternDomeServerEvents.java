package com.solegendary.reignofnether.mana;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.storm.LanternFortressUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
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
 * The Lantern Fortress's <b>Lantern Dome</b> (design/stormcallers_plan.md, slice 4; the T2 table's area shield):
 * BastionAegis' dome of light, but <i>carried</i> by the walker (its centre is wherever the Fortress stands) and
 * <i>channelled</i> - {@link #ENERGY_PER_SECOND} energy a second through {@link ManaServerEvents#openChannel} /
 * {@link ManaServerEvents#channel} for as long as the Fortress's toggle is on. Projectile damage to friendly units
 * (the owner's and allies') within {@link #RADIUS} is soaked by the dome, up to {@link #CAPACITY} per raise
 * (x power when Overcharged or by a well). When a second can't be paid at all, or the capacity is spent, the dome
 * <b>collapses</b> and the Fortress cannot raise another for {@link #COLLAPSE_DELAY_TICKS} ticks
 * ({@link LanternFortressUnit#blockDome}); with the toggle still on it re-raises itself after that (the unit's tick).
 * <p>
 * The Ward's area-record pattern: a short list (one dome per Fortress), the damage hook is an empty-list check for
 * every hurt event when no dome stands, the channel is paid once a second, the look is a ring of lantern motes every
 * {@link #FX_TICKS} ticks. Not saved: the Fortress re-raises after a reload.
 */
public final class LanternDomeServerEvents {
    private LanternDomeServerEvents() { }

    public static final float RADIUS = 6f;
    public static final int HEIGHT = 6;
    public static final float ENERGY_PER_SECOND = 15f;
    public static final float CAPACITY = 300f;
    public static final int COLLAPSE_DELAY_TICKS = 5 * 20;
    static final int FX_TICKS = 5;
    private static final DustParticleOptions LANTERN = new DustParticleOptions(new Vector3f(1.0f, 0.62f, 0.2f), 1.1f);

    /** One live dome. Public for the game test. */
    public static final class Dome {
        public final ServerLevel level;
        public final String owner;
        public final LanternFortressUnit walker;
        public final long startTick;
        public final ManaServerEvents.Channel channel;
        public float remaining;
        boolean ended = false;

        Dome(ServerLevel level, String owner, LanternFortressUnit walker, long startTick, ManaServerEvents.Channel channel, float capacity) {
            this.level = level;
            this.owner = owner;
            this.walker = walker;
            this.startTick = startTick;
            this.channel = channel;
            this.remaining = capacity;
        }

        public boolean isActive() { return !ended; }

        public Vec3 centre() { return walker.position(); }

        public boolean contains(LivingEntity le) {
            if (le.level() != level)
                return false;
            double dx = le.getX() - walker.getX(), dz = le.getZ() - walker.getZ();
            return dx * dx + dz * dz <= RADIUS * RADIUS && Math.abs(le.getY() - walker.getY()) <= HEIGHT;
        }

        boolean friendOf(String o) {
            return o != null && (o.equals(owner) || AlliancesServerEvents.isAllied(owner, o));
        }
    }

    private static final List<Dome> DOMES = new ArrayList<>();

    /** Read-only view (game test / debugging). */
    public static List<Dome> domes() {
        return java.util.Collections.unmodifiableList(DOMES);
    }

    /** The live dome carried by this Fortress, or null. */
    public static Dome domeOf(LanternFortressUnit walker) {
        for (int i = 0; i < DOMES.size(); i++)
            if (DOMES.get(i).walker == walker && !DOMES.get(i).ended)
                return DOMES.get(i);
        return null;
    }

    /**
     * Raises the dome over {@code walker}, paying its first second. Returns null when the channel is refused
     * (ManaServerEvents.lastRefusal says why) - nothing raised, nothing paid - or while the walker is still barred
     * after a collapse. An earlier dome of the same walker is replaced. Public for the unit, the bot and the game test.
     */
    public static Dome raise(ServerLevel sl, LanternFortressUnit walker, String owner) {
        if (walker == null || owner == null || owner.isBlank() || !walker.isAlive() || walker.domeBlocked())
            return null;
        ManaServerEvents.Channel ch = ManaServerEvents.openChannel(walker, owner, ENERGY_PER_SECOND);
        if (ch == null)
            return null;
        for (int i = DOMES.size() - 1; i >= 0; i--)
            if (DOMES.get(i).walker == walker)
                end(DOMES.remove(i), false);
        Dome d = new Dome(sl, owner, walker, sl.getGameTime(), ch, CAPACITY * Math.max(1f, ch.power()));
        DOMES.add(d);
        ring(d, 32);
        sl.playSound(null, walker.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 2.0f, 1.4f);
        return d;
    }

    /**
     * How much of a hit gets through: projectile damage to a friendly unit inside a live dome is soaked (the first
     * dome that covers it), draining that dome. Public for the game test.
     */
    public static float absorb(LivingEntity target, DamageSource src, float amount) {
        if (DOMES.isEmpty() || amount <= 0 || !src.is(DamageTypeTags.IS_PROJECTILE) || !(target instanceof Unit u))
            return amount;
        String o = u.getOwnerName();
        if (o == null || !(target.level() instanceof ServerLevel sl))
            return amount;
        for (int i = 0; i < DOMES.size(); i++) {
            Dome d = DOMES.get(i);
            if (d.ended || d.remaining <= 0 || !d.friendOf(o) || !d.contains(target))
                continue;
            float soaked = Math.min(amount, d.remaining);
            d.remaining -= soaked;
            ripple(sl, d, target, src);
            return amount - soaked;
        }
        return amount;
    }

    static void ripple(ServerLevel sl, Dome d, LivingEntity target, DamageSource src) {
        // on the dome's skin, on the side the shot came from (the projectile itself, else the shooter, else above)
        Vec3 c = d.centre().add(0, 1, 0);
        Entity from = src.getDirectEntity() != null ? src.getDirectEntity() : src.getEntity();
        Vec3 dir = from != null ? from.position().subtract(c) : new Vec3(0, 1, 0);
        if (dir.lengthSqr() < 0.01)
            dir = target.position().subtract(c).add(0, 1, 0);
        if (dir.y < 0)
            dir = new Vec3(dir.x, 0, dir.z);
        Vec3 at = c.add(dir.normalize().scale(RADIUS));
        sl.sendParticles(LANTERN, at.x, at.y, at.z, 8, 0.4, 0.4, 0.4, 0.0);
        sl.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 4, 0.3, 0.3, 0.3, 0.03);
        sl.playSound(null, BlockPos.containing(at), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.NEUTRAL, 1.2f, 1.5f);
    }

    // after RoN's own hurt handlers (its projectile rewrite) and the Jade Mirror so the soak is on the final number
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(LivingHurtEvent evt) {
        if (DOMES.isEmpty() || evt.getEntity().level().isClientSide())
            return;
        float left = absorb(evt.getEntity(), evt.getSource(), evt.getAmount());
        if (left <= 0)
            evt.setCanceled(true);
        else if (left != evt.getAmount())
            evt.setAmount(left);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || DOMES.isEmpty())
            return;
        for (int i = DOMES.size() - 1; i >= 0; i--) {
            Dome d = DOMES.get(i);
            if (d.level.getServer() != evt.getServer())
                continue;
            if (!tickDome(d)) {
                DOMES.remove(i);
                collapse(d);
            }
        }
    }

    /** One tick of a dome; false when it must come down. Public for the game test. */
    public static boolean tickDome(Dome d) {
        long age = d.level.getGameTime() - d.startTick;
        if (d.ended || !d.walker.isAlive() || !d.walker.isDomeWanted())
            return false;
        if (d.remaining <= 0)
            return false;   // the lanterns are spent
        // the channel is paid once a second (the first second was paid at the raise); starved to nothing = collapse
        if (age > 0 && age % 20 == 0) {
            ManaServerEvents.channel(d.channel);
            if (!d.channel.isActive())
                return false;
        }
        if (age % FX_TICKS == 0)
            ring(d, 12);
        return true;
    }

    /** The dome falls: the walker is barred from raising another for {@link #COLLAPSE_DELAY_TICKS}. */
    static void collapse(Dome d) {
        boolean starved = !d.channel.isActive() || d.remaining <= 0;
        end(d, true);
        if (starved && d.walker.isAlive())
            d.walker.blockDome(d.level.getGameTime() + COLLAPSE_DELAY_TICKS);
    }

    static void end(Dome d, boolean sound) {
        if (d.ended)
            return;
        d.ended = true;
        d.channel.stop();
        if (sound) {
            d.level.playSound(null, d.walker.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 1.5f, 0.8f);
            d.level.sendParticles(ParticleTypes.SMOKE, d.walker.getX(), d.walker.getY() + 2, d.walker.getZ(), 20, 2.0, 1.0, 2.0, 0.02);
        }
    }

    /** Takes a Fortress's dome down now (the toggle going off, death, the game test's clean-up). */
    public static void lower(LanternFortressUnit walker) {
        for (int i = DOMES.size() - 1; i >= 0; i--)
            if (DOMES.get(i).walker == walker)
                end(DOMES.remove(i), false);
    }

    /** The lanterns: motes on the rim at head height (and a few drifting up inside), server particles for both sides. */
    private static void ring(Dome d, int n) {
        Vec3 c = d.centre();
        double phase = (d.level.getGameTime() % 60) / 60.0 * Math.PI * 2;
        for (int i = 0; i < n; i++) {
            double a = phase + Math.PI * 2 * i / n;
            d.level.sendParticles(LANTERN, c.x + Math.cos(a) * RADIUS, c.y + 1.5, c.z + Math.sin(a) * RADIUS, 1, 0, 0, 0, 0);
        }
        d.level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 2.5, c.z, Math.max(1, n / 6), RADIUS * 0.5, 0.5, RADIUS * 0.5, 0.0);
    }

    /** Defeat: the owner's domes fall. */
    public static void clearOwner(String owner) {
        for (int i = DOMES.size() - 1; i >= 0; i--)
            if (DOMES.get(i).owner.equals(owner))
                end(DOMES.remove(i), false);
    }

    public static void clearAll() {
        for (Dome d : DOMES)
            end(d, false);
        DOMES.clear();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        clearAll();
    }
}
