package com.solegendary.reignofnether.tide;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Tide Seer's lasting effects (design/essence_casters.md; the Tidewrought essence caster): the <b>whirlpools</b>
 * Drowned Tithe leaves on the ground and the <b>shoves</b> Brine Bolt and Rising Tide give.
 * <p>
 * A whirlpool lasts its ticks; every tick each enemy ground unit inside it carries one fixed-UUID transient speed
 * modifier (two pools never stack it), which comes off the tick it is in none, and every 20 ticks of the pool's own
 * clock each takes the pool's damage. A shove slides a unit a set distance over a few ticks with {@link Entity#move}, so
 * it stops at a wall, needs no AI (a still test unit slides too) and lands at the same distance on every PC - a velocity
 * knockback would depend on friction and on the target's AI ticking. Knockback resistance shortens it (a resistance of
 * 1, a golem or treant, holds its ground). Server thread only; nothing is saved (a pool lasts 8 s, a shove under 1 s).
 */
public final class TideSeerServerEvents {

    private TideSeerServerEvents() { }

    /** The whirlpool slow's modifier: fixed, so overlapping pools slow a unit once. */
    public static final UUID SLOW_UUID = UUID.fromString("5b0f3c9e-2a7d-4c61-9e43-7d1c2b8a6f15");
    /** Shove speed: blocks per tick. */
    public static final double SHOVE_PER_TICK = 0.5;

    /** One Drowned Tithe pool. */
    public static final class Whirlpool {
        public final ServerLevel level;
        public final Vec3 centre;
        public final String owner;
        public final float radius, damagePerSecond;
        public final double slow;
        public final long start, end;
        final Entity caster;

        Whirlpool(ServerLevel level, Vec3 centre, String owner, Entity caster, float radius, int ticks, double slow, float damagePerSecond) {
            this.level = level;
            this.centre = centre;
            this.owner = owner;
            this.caster = caster;
            this.radius = radius;
            this.slow = slow;
            this.damagePerSecond = damagePerSecond;
            this.start = level.getGameTime();
            this.end = start + ticks;
        }

        public boolean contains(LivingEntity le) {
            double dx = le.getX() - centre.x, dz = le.getZ() - centre.z;
            return le.level() == level && dx * dx + dz * dz <= radius * radius && Math.abs(le.getY() - centre.y) <= 4;
        }
    }

    private static final class Shove {
        final double dx, dz;
        int ticksLeft;

        Shove(double dx, double dz, int ticks) {
            this.dx = dx;
            this.dz = dz;
            this.ticksLeft = ticks;
        }
    }

    private static final List<Whirlpool> POOLS = new ArrayList<>();
    private static final Map<LivingEntity, Shove> SHOVES = new IdentityHashMap<>();
    // the units carrying the slow now (so the one that left every pool loses it)
    private static final Set<LivingEntity> SLOWED = new HashSet<>();
    private static final List<LivingEntity> scratch = new ArrayList<>();

    /** An enemy ground unit of {@code owner} (the whirlpool's and the wave's rule: flyers pass over the water). */
    public static boolean isEnemyGround(String owner, Entity e) {
        return isEnemy(owner, e) && !((Unit) e).isFlyingUnit();
    }

    /** A living unit of another, unallied owner. */
    public static boolean isEnemy(String owner, Entity e) {
        if (!(e instanceof Unit u) || !(e instanceof LivingEntity le) || !le.isAlive() || owner == null || owner.isBlank())
            return false;
        String o = u.getOwnerName();
        return o != null && !o.isBlank() && !owner.equals(o) && !AlliancesServerEvents.isAllied(owner, o);
    }

    /** Opens a whirlpool at {@code centre} (the surface it swirls on) and slows the enemies in it at once. */
    public static Whirlpool addWhirlpool(ServerLevel level, Vec3 centre, String owner, Entity caster, float radius, int ticks,
                                         double slow, float damagePerSecond) {
        Whirlpool w = new Whirlpool(level, centre, owner, caster, radius, ticks, slow, damagePerSecond);
        POOLS.add(w);
        updateSlows();
        return w;
    }

    public static List<Whirlpool> getWhirlpools() {
        return POOLS;
    }

    /**
     * Slides {@code le} {@code blocks} away from {@code from} (horizontally), less its knockback resistance, at
     * {@link #SHOVE_PER_TICK}. A new shove replaces one under way. Returns the distance it will slide (before walls).
     */
    public static double shove(LivingEntity le, Vec3 from, double blocks) {
        double resist = Math.min(1, Math.max(0, le.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)));
        double dist = blocks * (1 - resist);
        if (dist <= 1e-3)
            return 0;
        double x = le.getX() - from.x, z = le.getZ() - from.z;
        double len = Math.sqrt(x * x + z * z);
        if (len < 1e-3) {
            x = 1;
            z = 0;
            len = 1;
        }
        int ticks = (int) Math.ceil(dist / SHOVE_PER_TICK);
        double step = dist / ticks;
        SHOVES.put(le, new Shove(x / len * step, z / len * step, ticks));
        if (le instanceof Mob mob)
            mob.getNavigation().stop();
        return dist;
    }

    /** Whether {@code le} is still being shoved (the tests wait it out). */
    public static boolean isShoved(LivingEntity le) {
        return SHOVES.containsKey(le);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        tickShoves();
        if (POOLS.isEmpty() && SLOWED.isEmpty())
            return;
        Iterator<Whirlpool> it = POOLS.iterator();
        while (it.hasNext()) {
            Whirlpool w = it.next();
            long now = w.level.getGameTime();
            long age = now - w.start;
            // a pulse at the end of each of its seconds, the last on its final tick (8 s = 8 pulses)
            if (age > 0 && age % 20 == 0 && now <= w.end)
                pulse(w);
            if (now >= w.end) {
                it.remove();
                continue;
            }
            if (age % 5 == 0)
                swirl(w, age);
        }
        updateSlows();
    }

    private static void tickShoves() {
        if (SHOVES.isEmpty())
            return;
        Iterator<Map.Entry<LivingEntity, Shove>> it = SHOVES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, Shove> e = it.next();
            LivingEntity le = e.getKey();
            Shove s = e.getValue();
            if (!le.isAlive() || le.isRemoved() || s.ticksLeft <= 0) {
                it.remove();
                continue;
            }
            le.move(MoverType.SELF, new Vec3(s.dx, 0, s.dz));
            le.hurtMarked = true;
            s.ticksLeft--;
            if (le.horizontalCollision)
                s.ticksLeft = 0;
            if (le.level() instanceof ServerLevel sl && s.ticksLeft % 2 == 0)
                sl.sendParticles(ParticleTypes.SPLASH, le.getX(), le.getY() + 0.2, le.getZ(), 4, 0.3, 0.1, 0.3, 0.1);
        }
    }

    /** One second of a pool: its damage to every enemy ground unit inside. */
    private static void pulse(Whirlpool w) {
        for (LivingEntity le : UnitGrid.near(w.level, w.centre.x, w.centre.z, w.radius + 1, scratch)) {
            if (!isEnemyGround(w.owner, le) || !w.contains(le))
                continue;
            le.invulnerableTime = 0;   // a second apart: never eaten by another hit's i-frames
            le.hurt(w.caster != null && w.caster.isAlive()
                ? w.level.damageSources().indirectMagic(w.caster, w.caster)
                : w.level.damageSources().magic(), w.damagePerSecond);
            w.level.sendParticles(ParticleTypes.BUBBLE_POP, le.getX(), le.getY() + 0.5, le.getZ(), 6, 0.3, 0.3, 0.3, 0.05);
        }
    }

    /** The look: a turning spiral of splash and bubbles over the pool. */
    private static void swirl(Whirlpool w, long age) {
        double turn = age * 0.15;
        for (int arm = 0; arm < 3; arm++) {
            for (int i = 1; i <= 4; i++) {
                double r = w.radius * i / 4.0;
                double a = turn + arm * Math.PI * 2 / 3 + i * 0.6;
                w.level.sendParticles(ParticleTypes.SPLASH, w.centre.x + Math.cos(a) * r, w.centre.y + 0.1,
                    w.centre.z + Math.sin(a) * r, 2, 0.1, 0.0, 0.1, 0.0);
            }
        }
        w.level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, w.centre.x, w.centre.y + 0.2, w.centre.z, 6, 0.6, 0.1, 0.6, 0.02);
    }

    /** Every enemy ground unit inside a live pool carries the slow; every other one that carried it loses it. */
    private static void updateSlows() {
        Set<LivingEntity> now = new HashSet<>();
        for (Whirlpool w : POOLS) {
            if (w.level.getGameTime() >= w.end)
                continue;
            for (LivingEntity le : UnitGrid.near(w.level, w.centre.x, w.centre.z, w.radius + 1, scratch)) {
                if (!isEnemyGround(w.owner, le) || !w.contains(le))
                    continue;
                AttributeInstance speed = le.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speed == null)
                    continue;
                if (speed.getModifier(SLOW_UUID) == null)
                    speed.addTransientModifier(new AttributeModifier(SLOW_UUID, "Drowned Tithe", -w.slow,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
                now.add(le);
            }
        }
        for (LivingEntity le : SLOWED)
            if (!now.contains(le)) {
                AttributeInstance speed = le.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speed != null)
                    speed.removeModifier(SLOW_UUID);
            }
        SLOWED.clear();
        SLOWED.addAll(now);
    }

    /** Test cleanup: closes the pools touching {@code level} within {@code r} of {@code at} and lifts their slows. */
    public static void clearNear(ServerLevel level, Vec3 at, double r) {
        POOLS.removeIf(w -> w.level == level && w.centre.distanceTo(at) <= r);
        updateSlows();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        POOLS.clear();
        SHOVES.clear();
        SLOWED.clear();
    }
}
