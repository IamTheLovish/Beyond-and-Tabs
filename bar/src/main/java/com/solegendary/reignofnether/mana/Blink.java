package com.solegendary.reignofnether.mana;

import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Stormcallers' short teleport (design/stormcallers_plan.md: the Thunder Sage's <b>Blink</b> and the Blade Dancer's
 * <b>Blink Strike</b>), one place for the landing rules so both read the same. Teleport's targeting rules: the spot is
 * pulled in to the range, it needs line of sight from the caster's eyes (no blinking through walls into a base), and it
 * must be standable - solid ground under the feet and room for the body. When the aimed spot fails, the blink shortens
 * along the line (3/4, 1/2, 1/4) before giving up.
 * <p>
 * No cost here: callers pay through {@link ManaServerEvents} first and only then call {@link #blinkTo}. Cost: at most
 * four ray casts and a few collision checks per cast, never per tick.
 */
public final class Blink {
    private Blink() { }

    /**
     * Where {@code who} lands blinking toward {@code target}, at most {@code range} blocks away (flat), or null if no
     * standable spot in sight lies on the line.
     */
    public static Vec3 landingSpot(ServerLevel sl, LivingEntity who, Vec3 target, double range) {
        double dx = target.x - who.getX(), dz = target.z - who.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.75)
            return null;
        double scale = Math.min(1.0, range / dist);
        for (double f : new double[] { 1.0, 0.75, 0.5, 0.25 }) {
            double x = who.getX() + dx * scale * f, z = who.getZ() + dz * scale * f;
            if (Math.sqrt((x - who.getX()) * (x - who.getX()) + (z - who.getZ()) * (z - who.getZ())) < 0.75)
                break;
            double baseY = f == 1.0 ? target.y : who.getY() + (target.y - who.getY()) * f;
            Vec3 spot = standable(sl, who, x, baseY, z);
            if (spot != null && inSight(sl, who, spot))
                return spot;
        }
        return null;
    }

    /**
     * The Y a unit would stand at in column (x, z) near {@code nearY}: the first air-over-solid step searched 3 blocks
     * up and 6 down from nearY, else nearY itself. The heightmap is useless for the ground spells (Ground Slam,
     * Singularity, Mana Well): under a roof, a tree or an arena ceiling it returns the top of the world, not the floor
     * the caster stands on.
     */
    public static int groundY(ServerLevel sl, int x, int z, int nearY) {
        for (int dy : new int[] { 0, 1, -1, 2, -2, 3, -3, -4, -5, -6 }) {
            BlockPos feet = new BlockPos(x, nearY + dy, z);
            if (sl.getBlockState(feet).isAir() && sl.getBlockState(feet.below()).isFaceSturdy(sl, feet.below(), Direction.UP))
                return feet.getY();
        }
        return nearY;
    }

    /** A spot near (x, y, z) - searched 3 blocks up and 4 down - with solid ground underneath and room for the body. */
    static Vec3 standable(ServerLevel sl, LivingEntity who, double x, double y, double z) {
        if (!sl.getWorldBorder().isWithinBounds(BlockPos.containing(x, y, z)))
            return null;
        int by = (int) Math.floor(y);
        for (int dy : new int[] { 0, 1, -1, 2, -2, 3, -3, -4 }) {
            BlockPos feet = BlockPos.containing(x, by + dy, z);
            BlockPos below = feet.below();
            if (!sl.getBlockState(below).isFaceSturdy(sl, below, Direction.UP))
                continue;
            if (!sl.getFluidState(feet).isEmpty())
                continue;
            AABB box = who.getDimensions(who.getPose()).makeBoundingBox(x, feet.getY(), z);
            if (sl.noCollision(who, box))
                return new Vec3(x, feet.getY(), z);
        }
        return null;
    }

    /** Line of sight from the caster's eyes to the landing spot's eye height (blocks only, entities don't block). */
    static boolean inSight(ServerLevel sl, LivingEntity who, Vec3 spot) {
        Vec3 from = who.getEyePosition();
        Vec3 to = spot.add(0, who.getEyeHeight(), 0);
        return sl.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, who)).getType()
            == HitResult.Type.MISS;
    }

    /** Moves {@code who} to {@code spot} (from {@link #landingSpot}): sparks at both ends, a crackle, its path cleared. */
    public static void blinkTo(ServerLevel sl, LivingEntity who, Vec3 spot) {
        Vec3 from = who.position();
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, from.x, from.y + 1, from.z, 14, 0.3, 0.6, 0.3, 0.15);
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1, from.z, 10, 0.3, 0.5, 0.3, 0.05);
        if (who instanceof Mob mob)
            mob.getNavigation().stop();
        who.teleportTo(spot.x, spot.y, spot.z);
        who.setDeltaMovement(Vec3.ZERO);
        who.fallDistance = 0;
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, spot.x, spot.y + 1, spot.z, 14, 0.3, 0.6, 0.3, 0.15);
        sl.sendParticles(ParticleTypes.END_ROD, spot.x, spot.y + 0.2, spot.z, 6, 0.4, 0.1, 0.4, 0.02);
        sl.playSound(null, BlockPos.containing(from), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 1.0f, 1.6f);
        sl.playSound(null, BlockPos.containing(spot), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.NEUTRAL, 0.4f, 1.8f);
    }

    /** True if {@code u} belongs to an enemy of {@code owner} (not the owner, not an ally). */
    public static boolean isEnemy(String owner, Unit u) {
        String o = u.getOwnerName();
        return o != null && !o.isEmpty() && !o.equals(owner)
            && !com.solegendary.reignofnether.alliance.AlliancesServerEvents.isAllied(owner, o);
    }
}
