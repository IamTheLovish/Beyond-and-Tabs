package com.solegendary.reignofnether.mana;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.barfx.LightningFx;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The Celestial Dragon's <b>Tempest</b> (design/stormcallers_plan.md, slice 4): a roaming storm. A record
 * {@code {owner, centre, velocity, ticksLeft}} of radius {@link #RADIUS} that starts over the dragon and drifts toward
 * the aimed spot at {@link #DRIFT} blocks a tick for {@link #DURATION_TICKS} ticks; every {@link #STRIKE_EVERY} ticks it
 * strikes one enemy unit inside - the one standing among the most other enemies, the Storm Oak's pickTarget rule - for
 * {@link #DAMAGE} x power with {@link LightningFx#bolt}: indirect magic from the dragon (kill credit), no LightningBolt
 * entity, no fire, no terrain. Friends never. Paid {@link #ENERGY_UPFRONT} at the cast and then
 * {@link #ENERGY_PER_SECOND} a second through {@link ManaServerEvents#openChannel} / {@link ManaServerEvents#channel}
 * at spell priority, so a Tempest stalls its own side's construction; when a second can't be paid at all the storm
 * fizzles. It also ends if the dragon dies.
 * <p>
 * The Ward's area-record pattern: a short list (one storm per dragon), one UnitGrid query per strike into a shared
 * scratch list (no allocation per strike), a ring of cloud motes every {@link #FX_TICKS} ticks. Not saved: a storm
 * lasts 10 s.
 */
public final class TempestServerEvents {
    private TempestServerEvents() { }

    public static final float RADIUS = 6f;
    public static final int HEIGHT = 8;
    public static final double DRIFT = 0.15;
    public static final int DURATION_TICKS = 10 * 20;
    public static final int STRIKE_EVERY = 10;
    public static final float DAMAGE = 18f;
    public static final float ENERGY_UPFRONT = 400f;
    public static final float ENERGY_PER_SECOND = 60f;
    /** How close two foes must stand to count as a clump for the strike's pick. */
    public static final double GROUP_RADIUS = 3.0;
    /** How high above the centre the cloud (and the bolt's start) sits. */
    public static final double CLOUD_HEIGHT = 7.0;
    static final int FX_TICKS = 2;

    /** One live storm. Public for the game test. */
    public static final class Storm {
        public final ServerLevel level;
        public final String owner;
        public final LivingEntity caster;
        public final float power;
        public final long startTick;
        public final ManaServerEvents.Channel channel;
        public double x, y, z;          // the centre (y = the ground it rolls over)
        public double vx, vz;           // the drift this tick
        final double aimX, aimZ;
        public int ticksLeft;
        public int strikes = 0;
        boolean ended = false;

        Storm(ServerLevel level, String owner, LivingEntity caster, float power, long startTick, ManaServerEvents.Channel channel,
              Vec3 from, double aimX, double aimZ) {
            this.level = level;
            this.owner = owner;
            this.caster = caster;
            this.power = power;
            this.startTick = startTick;
            this.channel = channel;
            this.x = from.x;
            this.y = from.y;
            this.z = from.z;
            this.aimX = aimX;
            this.aimZ = aimZ;
            this.ticksLeft = DURATION_TICKS;
        }

        public boolean isActive() { return !ended; }

        public Vec3 centre() { return new Vec3(x, y, z); }

        public boolean contains(LivingEntity le) {
            if (le.level() != level)
                return false;
            double dx = le.getX() - x, dz = le.getZ() - z;
            return dx * dx + dz * dz <= RADIUS * RADIUS && Math.abs(le.getY() - y) <= HEIGHT;
        }

        boolean foeOf(String o) {
            return o != null && !o.isBlank() && !o.equals(owner) && !AlliancesServerEvents.isAllied(owner, o);
        }
    }

    private static final List<Storm> STORMS = new ArrayList<>();
    private static final List<LivingEntity> scratch = new ArrayList<>();   // server thread only
    private static final List<LivingEntity> foes = new ArrayList<>();

    /** Read-only view (game test / debugging). */
    public static List<Storm> storms() {
        return java.util.Collections.unmodifiableList(STORMS);
    }

    /** The live storm cast by this unit, or null. */
    @Nullable
    public static Storm stormOf(LivingEntity caster) {
        for (int i = 0; i < STORMS.size(); i++)
            if (STORMS.get(i).caster == caster && !STORMS.get(i).ended)
                return STORMS.get(i);
        return null;
    }

    /**
     * Raises a storm over {@code caster} rolling toward {@code aim} (ground level {@code groundY}): pays the up-front
     * cost and the first second of the channel. Returns null when refused - the up-front cost could not be paid
     * (ManaServerEvents.lastRefusal says why; nothing paid). If the channel's first second cannot be opened right after
     * the up-front payment (a pool that held exactly the cost) the storm fizzles at once with its cost spent. A caster's
     * earlier storm is replaced. Public for the ability, the bot and the game test (no cooldown handling here).
     */
    @Nullable
    public static Storm raise(ServerLevel sl, LivingEntity caster, String owner, double aimX, double aimZ, double groundY, boolean quiet) {
        if (caster == null || owner == null || owner.isBlank())
            return null;
        float power = quiet ? ManaServerEvents.tryCastQuiet(caster, owner, ENERGY_UPFRONT) : ManaServerEvents.tryCast(caster, owner, ENERGY_UPFRONT);
        if (power <= 0)
            return null;
        for (int i = STORMS.size() - 1; i >= 0; i--)
            if (STORMS.get(i).caster == caster)
                end(STORMS.remove(i), false);
        ManaServerEvents.Channel ch = ManaServerEvents.openChannel(caster, owner, ENERGY_PER_SECOND);
        Vec3 from = new Vec3(caster.getX(), groundY, caster.getZ());
        Storm s = new Storm(sl, owner, caster, power, sl.getGameTime(),
            ch == null ? ManaServerEvents.stoppedChannel(caster, owner, ENERGY_PER_SECOND) : ch, from, aimX, aimZ);
        STORMS.add(s);
        if (ch == null)
            s.ticksLeft = 0;   // fizzles on its first tick: the cost was paid, the storm never gathered
        cloud(s, 24);
        sl.playSound(null, BlockPos.containing(from), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.NEUTRAL, 2.5f, 0.6f);
        sl.playSound(null, BlockPos.containing(from), SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 1.5f, 0.8f);
        return s;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || STORMS.isEmpty())
            return;
        for (int i = STORMS.size() - 1; i >= 0; i--) {
            Storm s = STORMS.get(i);
            if (s.level.getServer() != evt.getServer())
                continue;
            if (!tickStorm(s))
                end(STORMS.remove(i), true);
        }
    }

    /** One tick of a storm: drift, pay, strike; false when it must fizzle. Public for the game test. */
    public static boolean tickStorm(Storm s) {
        if (s.ended || s.ticksLeft <= 0 || !s.caster.isAlive())
            return false;
        long age = s.level.getGameTime() - s.startTick;
        // the channel is paid once a second (the first second was paid at the cast); starved to nothing = fizzle
        if (age > 0 && age % 20 == 0) {
            ManaServerEvents.channel(s.channel);
            if (!s.channel.isActive())
                return false;
        }
        // drift toward the aim at DRIFT a tick, and stop over it
        double dx = s.aimX - s.x, dz = s.aimZ - s.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > DRIFT) {
            s.vx = dx / d * DRIFT;
            s.vz = dz / d * DRIFT;
            s.x += s.vx;
            s.z += s.vz;
        } else {
            s.vx = s.vz = 0;
            s.x = s.aimX;
            s.z = s.aimZ;
        }
        s.ticksLeft--;
        if (age > 0 && age % STRIKE_EVERY == 0)   // the cloud gathers first (half a second): the danger is shown before it strikes
            strike(s, null);
        if (age % FX_TICKS == 0)
            cloud(s, 6);
        return true;
    }

    /**
     * One strike: the enemy unit inside the storm standing among the most other enemies (nearest to the centre on a
     * tie) takes {@link #DAMAGE} x power. Returns the unit struck, or null. {@code onlyFoe} (null in play) limits it to
     * one owner so parallel game tests never touch each other's units. Public for the game test.
     */
    @Nullable
    public static LivingEntity strike(Storm s, @Nullable String onlyFoe) {
        LivingEntity target = pickTarget(s, onlyFoe);
        if (target == null)
            return null;
        double x1 = target.getX(), y1 = target.getY() + target.getBbHeight() * 0.6, z1 = target.getZ();
        LightningFx.bolt(s.level, s.x, s.y + CLOUD_HEIGHT, s.z, x1, y1, z1);
        s.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x1, y1, z1, 8, 0.3, 0.3, 0.3, 0.1);
        // indirect magic from the dragon: kill credit and veterancy go to it, nothing catches fire
        target.hurt(s.level.damageSources().indirectMagic(s.caster, s.caster), DAMAGE * s.power);
        s.level.playSound(null, BlockPos.containing(x1, y1, z1), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.NEUTRAL, 0.6f, 1.5f);
        s.strikes++;
        return target;
    }

    /** The Storm Oak's rule: the foe inside with the most other foes within GROUP_RADIUS, nearest the centre on a tie. */
    @Nullable
    static LivingEntity pickTarget(Storm s, @Nullable String onlyFoe) {
        foes.clear();
        for (LivingEntity le : UnitGrid.near(s.level, s.x, s.z, RADIUS, scratch)) {
            if (!le.isAlive() || !(le instanceof Unit u) || !s.contains(le))
                continue;
            String o = u.getOwnerName();
            if (!s.foeOf(o) || (onlyFoe != null && !onlyFoe.equals(o)))
                continue;
            foes.add(le);
        }
        scratch.clear();
        LivingEntity best = null;
        int bestCount = -1;
        double bestDist = Double.MAX_VALUE, g2 = GROUP_RADIUS * GROUP_RADIUS;
        for (int i = 0, n = foes.size(); i < n; i++) {
            LivingEntity a = foes.get(i);
            int count = 0;
            for (int j = 0; j < n; j++)
                if (j != i && a.distanceToSqr(foes.get(j)) <= g2)
                    count++;
            double dx = a.getX() - s.x, dz = a.getZ() - s.z, d = dx * dx + dz * dz;
            if (count > bestCount || (count == bestCount && d < bestDist)) {
                best = a;
                bestCount = count;
                bestDist = d;
            }
        }
        foes.clear();
        return best;
    }

    static void end(Storm s, boolean sound) {
        if (s.ended)
            return;
        s.ended = true;
        s.channel.stop();
        if (sound) {
            s.level.sendParticles(ParticleTypes.CLOUD, s.x, s.y + CLOUD_HEIGHT, s.z, 30, RADIUS * 0.5, 0.8, RADIUS * 0.5, 0.05);
            s.level.playSound(null, BlockPos.containing(s.x, s.y, s.z), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 1.5f, 0.5f);
        }
    }

    /** Takes a storm down now (the game test cleans up with this). */
    public static void dispel(Storm s) {
        if (STORMS.remove(s))
            end(s, false);
    }

    // the cloud: motes on the rim up at cloud height and a few sparks inside, plus rain-like dust on the ground ring
    private static void cloud(Storm s, int n) {
        double phase = (s.level.getGameTime() % 50) / 50.0 * Math.PI * 2;
        for (int i = 0; i < n; i++) {
            double a = phase + Math.PI * 2 * i / n;
            s.level.sendParticles(ParticleTypes.CLOUD, s.x + Math.cos(a) * RADIUS, s.y + CLOUD_HEIGHT, s.z + Math.sin(a) * RADIUS, 1, 0.2, 0.1, 0.2, 0.0);
            s.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, s.x + Math.cos(a) * RADIUS, s.y + 0.3, s.z + Math.sin(a) * RADIUS, 1, 0, 0, 0, 0.0);
        }
        s.level.sendParticles(ParticleTypes.CLOUD, s.x, s.y + CLOUD_HEIGHT, s.z, Math.max(1, n / 2), RADIUS * 0.4, 0.3, RADIUS * 0.4, 0.0);
    }

    /** Defeat: the owner's storms blow out. */
    public static void clearOwner(String owner) {
        for (int i = STORMS.size() - 1; i >= 0; i--)
            if (STORMS.get(i).owner.equals(owner))
                end(STORMS.remove(i), false);
    }

    public static void clearAll() {
        for (Storm s : STORMS)
            end(s, false);
        STORMS.clear();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        clearAll();
    }
}
