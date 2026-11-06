package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.brass.Machine;
import com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.storm.RocketBatteryUnit;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Explosive deaths (backlog 11, Command Center Eris.11, Nov 4): a Brassworks machine's boiler and a Rocket Battery's
 * rack burst when they die, hurting the units round them.
 * <ul>
 *   <li><b>Who bursts:</b> every {@link Machine} (anything that carries Heat: its boiler) and the
 *       {@link RocketBatteryUnit} - only on a real death (LivingDeathEvent, not cancelled, so a Totem that saves the
 *       unit stops it too). A unit taken off the field without dying (despawned, discarded, gone into a
 *       garrison) never bursts; nor does one killed to clear it ({@link #quietly}: a match reset's
 *       PlayerServerEvents.killForReset, a defeated side's DefeatCleanupServerEvents, the player's Delete order), nor
 *       one that dies while garrisoned: the building holds it.</li>
 *   <li><b>Radius</b> by size: {@link #radius} = {@value #RADIUS_BASE} + {@value #RADIUS_PER_WIDTH} x the body's width,
 *       {@value #RADIUS_MIN}..{@value #RADIUS_MAX} blocks (Rocket Battery 2.0, Iron Warden 2.7, Steam Juggernaut 3.3,
 *       Iron Leviathan Engine 5.7).</li>
 *   <li><b>Damage</b>: {@link #peak} = {@value #PEAK_FRACTION} of the dying unit's max health, {@value #PEAK_MIN}..
 *       {@value #PEAK_MAX} (Rocket Battery 6, Iron Warden 14.4, Steam Juggernaut 40, the Leviathan 60), at the centre;
 *       it falls off linearly to 0 at the radius ({@link #damageAt}), measured from the body's middle to the nearest
 *       point of the target's box.</li>
 *   <li><b>Friendly fire:</b> own and allied units take {@value #FRIENDLY_SHARE} of it ({@link #share}); enemies and
 *       neutrals all of it. Buildings, players and wrecks are not hurt. A unit the burst kills can burst in turn.</li>
 *   <li><b>Look and sound</b> from vanilla: explosion particles (the emitter for a radius of 4 and more) and the
 *       generic explosion sound, quieter and higher for a small unit; the client's Brassworks steam (BarFxClient) still
 *       plays. Both go out through the fog gates (particles and positional sounds).</li>
 * </ul>
 * The numbers are this PR's proposal, to confirm with lovish (PR body). Server thread only; one grid-free AABB query
 * per burst (the radius is at most 6).
 */
public final class DeathBurst {
    private DeathBurst() { }

    public static final float RADIUS_BASE = 1.0f;
    public static final float RADIUS_PER_WIDTH = 1.5f;
    public static final float RADIUS_MIN = 2.0f;
    public static final float RADIUS_MAX = 6.0f;
    public static final float PEAK_FRACTION = 0.2f;
    public static final float PEAK_MIN = 4.0f;
    public static final float PEAK_MAX = 60.0f;
    public static final float FRIENDLY_SHARE = 0.5f;

    /** The burst's radius for a body {@code width} blocks wide. */
    public static float radius(float width) {
        return Math.max(RADIUS_MIN, Math.min(RADIUS_MAX, RADIUS_BASE + RADIUS_PER_WIDTH * width));
    }

    /** The burst's damage at its centre, for a unit of {@code maxHealth}. */
    public static float peak(float maxHealth) {
        return Math.max(PEAK_MIN, Math.min(PEAK_MAX, maxHealth * PEAK_FRACTION));
    }

    /** Linear falloff: {@code peak} at the centre, 0 at {@code radius} and beyond. */
    public static float damageAt(float peak, float radius, double distance) {
        if (distance >= radius)
            return 0f;
        return (float) (peak * (1.0 - Math.max(0.0, distance) / radius));
    }

    /** The share of the damage a target takes: own or allied {@value #FRIENDLY_SHARE}, everyone else all of it. */
    public static float share(boolean friendly) {
        return friendly ? FRIENDLY_SHARE : 1f;
    }

    /** Does this unit burst when it dies? */
    public static boolean burstsOnDeath(Entity e) {
        return e instanceof Machine || e instanceof RocketBatteryUnit;
    }

    // > 0 while units are cleared off the field (quietly): those kills are no deaths in battle
    private static int quietDepth = 0;

    /**
     * Runs {@code clear} with bursts off. A match reset (review round 1 on #517), a defeated side's cleanup and the
     * player's Delete order (lovish, Nov 5: "Quiet on cleanup + delete") take units off the field with
     * entity.kill(), and a machine killed that way must not burst into its neighbours. Nests (a depth count, as
     * UnitDamage's explicit scope); server thread only.
     */
    public static void quietly(Runnable clear) {
        quietDepth++;
        try {
            clear.run();
        } finally {
            quietDepth--;
        }
    }

    /** Own or allied (a neutral, ownerless unit is nobody's friend). */
    static boolean friendly(String deadOwner, String targetOwner) {
        if (deadOwner == null || deadOwner.isBlank() || targetOwner == null || targetOwner.isBlank())
            return false;
        return deadOwner.equals(targetOwner) || AlliancesServerEvents.isAllied(deadOwner, targetOwner);
    }

    /** Distance from {@code p} to the nearest point of {@code box} (0 inside it). */
    static double distanceTo(AABB box, Vec3 p) {
        double dx = Math.max(Math.max(box.minX - p.x, 0), p.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - p.y, 0), p.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - p.z, 0), p.z - box.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // after the Totem of Undying's handlers (normal priority) and never for a cancelled death
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent evt) {
        LivingEntity dead = evt.getEntity();
        if (quietDepth > 0 || !(dead.level() instanceof ServerLevel level) || !burstsOnDeath(dead))
            return;
        if (dead instanceof Unit u && GarrisonableBuildingAddon.getGarrison(u) != null)
            return;   // inside a building: the walls take it
        burst(level, dead);
    }

    /** The burst itself: hurts the units in range, plays the look and sound. Returns how many units it hurt. */
    public static int burst(ServerLevel level, LivingEntity dead) {
        float radius = radius(dead.getBbWidth());
        float peak = peak(dead.getMaxHealth());
        Vec3 centre = dead.position().add(0, dead.getBbHeight() / 2.0, 0);
        String owner = dead instanceof Unit u ? u.getOwnerName() : "";
        List<LivingEntity> near = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class,
            new AABB(centre, centre).inflate(radius), e -> e != dead && e instanceof Unit && !(e instanceof Player)
                && e.isAlive() && !e.isInvulnerable()));
        int hurt = 0;
        for (LivingEntity target : near) {
            if (!target.isAlive())
                continue;   // an earlier link of a chain took it
            float amount = damageAt(peak, radius, distanceTo(target.getBoundingBox(), centre))
                * share(friendly(owner, ((Unit) target).getOwnerName()));
            if (amount <= 0f)
                continue;
            // the dead unit is the direct source and nobody the causing one (a burst is no unit's kill). With no
            // causing unit, LivingEntityMixin.unitHitsStack leaves vanilla's hurt window shut, so open it here, as
            // BombardShell does: a unit hit earlier this tick, or by another burst, still takes the full burst.
            // (The explosion type's difficulty scaling runs only in Player.hurt, and players are never hit.)
            target.invulnerableTime = 0;
            target.hurt(level.damageSources().explosion(dead, null), amount);
            hurt++;
        }
        level.sendParticles(radius >= 4f ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION,
            centre.x, centre.y, centre.z, radius >= 4f ? 1 : 2, radius * 0.15, 0.2, radius * 0.15, 0);
        level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL,
            0.4f + 0.1f * radius, 1.35f - 0.08f * radius);
        return hurt;
    }
}
