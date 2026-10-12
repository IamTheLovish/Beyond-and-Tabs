package com.solegendary.reignofnether.hero;

import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The small toolkit the hero abilities are written in (HeroKits): who is an enemy or a friend around a point (one
 * UnitGrid query into a shared scratch list - the server thread only, never per frame), damage that credits the hero
 * (so kills give XP and kill counts), stun/root/slow, knockback, heals, particles and sounds, and safe landing spots for
 * the movement abilities. Every helper takes the hero as a Mob that is also a Unit.
 */
public final class HeroFx {
    private HeroFx() { }

    private static final List<LivingEntity> scratch = new ArrayList<>();

    public static boolean isEnemy(Mob hero, LivingEntity e) {
        return hero instanceof Unit u && e != hero && e.isAlive()
            && UnitServerEvents.getUnitToEntityRelationship(u, e) == Relationship.HOSTILE;
    }

    public static boolean isFriend(Mob hero, LivingEntity e) {
        if (!(hero instanceof Unit u) || !e.isAlive() || !(e instanceof Unit))
            return false;
        Relationship r = UnitServerEvents.getUnitToEntityRelationship(u, e);
        return r == Relationship.OWNED || r == Relationship.FRIENDLY;
    }

    /** Hostile units within {@code r} of {@code c} (a new list: casts are rare, the grid query is not repeated). */
    public static List<LivingEntity> enemies(ServerLevel level, Mob hero, Vec3 c, double r) {
        List<LivingEntity> out = new ArrayList<>();
        double r2 = r * r;
        for (LivingEntity le : UnitGrid.near(level, c.x, c.z, r, scratch))
            if (le.position().distanceToSqr(c) <= r2 && isEnemy(hero, le))
                out.add(le);
        return out;
    }

    /** Own and allied units within {@code r} of {@code c}, the hero included. */
    public static List<LivingEntity> friends(ServerLevel level, Mob hero, Vec3 c, double r) {
        List<LivingEntity> out = new ArrayList<>();
        double r2 = r * r;
        for (LivingEntity le : UnitGrid.near(level, c.x, c.z, r, scratch))
            if (le.position().distanceToSqr(c) <= r2 && isFriend(hero, le))
                out.add(le);
        if (!out.contains(hero) && hero.position().distanceToSqr(c) <= r2)
            out.add(hero);
        return out;
    }

    @Nullable
    public static LivingEntity nearestEnemy(ServerLevel level, Mob hero, Vec3 c, double r) {
        LivingEntity best = null;
        double bestD = r * r;
        for (LivingEntity le : UnitGrid.near(level, c.x, c.z, r, scratch)) {
            double d = le.position().distanceToSqr(c);
            if (d <= bestD && isEnemy(hero, le)) {
                best = le;
                bestD = d;
            }
        }
        return best;
    }

    /** Enemies within {@code width} of the segment from -> to (a piercing line). */
    public static List<LivingEntity> enemiesOnLine(ServerLevel level, Mob hero, Vec3 from, Vec3 to, double width) {
        List<LivingEntity> out = new ArrayList<>();
        Vec3 d = to.subtract(from);
        double len2 = Math.max(1e-6, d.lengthSqr());
        Vec3 mid = from.add(d.scale(0.5));
        double reach = Math.sqrt(len2) / 2 + width + 1;
        for (LivingEntity le : UnitGrid.near(level, mid.x, mid.z, reach, scratch)) {
            if (!isEnemy(hero, le))
                continue;
            Vec3 p = le.position().add(0, le.getBbHeight() / 2, 0);
            double t = Math.max(0, Math.min(1, p.subtract(from).dot(d) / len2));
            Vec3 closest = from.add(d.scale(t));
            double w = width + le.getBbWidth() / 2;
            if (p.distanceToSqr(closest) <= w * w)
                out.add(le);
        }
        return out;
    }

    /** Damage credited to the hero: physical (armour applies) or magic (magic resist applies). */
    public static void damage(Mob hero, LivingEntity target, float amount, boolean magic) {
        if (amount <= 0 || !target.isAlive())
            return;
        target.hurt(magic ? hero.damageSources().indirectMagic(hero, hero) : hero.damageSources().mobAttack(hero), amount);
    }

    /** Stunned: no orders, no attacks (RoN's STUN effect). Capped at 1.5 s by the design's balance rule 3. */
    public static void stun(LivingEntity t, int ticks) {
        t.addEffect(new MobEffectInstance(MobEffectRegistrar.STUN.get(), Math.min(30, ticks), 0, false, false));
    }

    /** Rooted: Slowness VII, as the Thornbow's thorns and Crypt Tide root. Capped at 1.5 s like a stun. */
    public static void root(LivingEntity t, int ticks) {
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, Math.min(30, ticks), 6, false, false));
    }

    public static void slow(LivingEntity t, int ticks, int amplifier) {
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, amplifier, false, false));
    }

    public static void buff(LivingEntity t, MobEffect effect, int ticks, int amplifier) {
        t.addEffect(new MobEffectInstance(effect, ticks, amplifier, false, true));
    }

    /** Pushes {@code t} away from {@code from} horizontally, with a little lift. */
    public static void knockback(Vec3 from, LivingEntity t, double strength) {
        Vec3 d = t.position().subtract(from);
        double len = Math.sqrt(d.x * d.x + d.z * d.z);
        if (len < 1e-3)
            d = new Vec3(1, 0, 0);
        else
            d = new Vec3(d.x / len, 0, d.z / len);
        double resist = 1 - Math.min(1, t.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE));
        t.push(d.x * strength * resist, 0.25 * resist, d.z * strength * resist);
        t.hurtMarked = true;
    }

    public static void particles(ServerLevel level, ParticleOptions type, Vec3 at, int count, double spread, double speed) {
        level.sendParticles(type, at.x, at.y, at.z, count, spread, spread * 0.5, spread, speed);
    }

    /** A ring of particles on the ground round {@code c}: shows an ability's radius. */
    public static void ring(ServerLevel level, ParticleOptions type, Vec3 c, double r) {
        int n = (int) Math.max(12, r * 8);
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            level.sendParticles(type, c.x + Math.cos(a) * r, c.y + 0.2, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    /** A line of particles from -> to. */
    public static void trail(ServerLevel level, ParticleOptions type, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        int n = (int) Math.max(4, d.length() * 2);
        for (int i = 0; i <= n; i++) {
            Vec3 p = from.add(d.scale(i / (double) n));
            level.sendParticles(type, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
        }
    }

    public static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, BlockPos.containing(at), sound, SoundSource.NEUTRAL, volume, pitch);
    }

    /**
     * Where a hero lands when it blinks / leaps toward {@code aim}: pulled in to {@code maxRange} from the hero, inside
     * the world border, standing on the top block there.
     */
    public static Vec3 landingSpot(ServerLevel level, Mob hero, Vec3 aim, double maxRange) {
        Vec3 from = hero.position();
        Vec3 d = new Vec3(aim.x - from.x, 0, aim.z - from.z);
        if (d.horizontalDistanceSqr() > maxRange * maxRange)
            aim = from.add(d.normalize().scale(maxRange));
        BlockPos col = BlockPos.containing(aim.x, from.y, aim.z);
        if (!level.getWorldBorder().isWithinBounds(col))
            return from;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col.getX(), col.getZ());
        // a cliff far above or a pit far below: stop at the hero's own height rather than climbing a wall
        double y = Math.abs(top - from.y) > 12 ? from.y : top;
        return new Vec3(col.getX() + 0.5, y, col.getZ() + 0.5);
    }

    /** True for the Gravebound (Judgement smites the undead harder). */
    public static boolean isUndead(LivingEntity e) {
        return e.getMobType() == net.minecraft.world.entity.MobType.UNDEAD;
    }
}
