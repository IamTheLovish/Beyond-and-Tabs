package com.solegendary.reignofnether.essence;

import com.solegendary.reignofnether.unit.units.monsters.TombstoneUnit;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/**
 * The Gravebound Tombcaller's lasting effects (design/essence_casters.md "Gravebound Gravecaller"): its tombstones,
 * their grave creep and Risen Skeletons, and Grave Tide. Stub for the game tests written first.
 */
public final class TombcallerServerEvents {

    private TombcallerServerEvents() { }

    /** The tombstones {@code caster} raised that still stand. */
    public static List<TombstoneUnit> stonesOf(LivingEntity caster) {
        return List.of();
    }

    /** The Risen Skeletons of {@code stone} still standing. */
    public static List<Mob> risenOf(TombstoneUnit stone) {
        return List.of();
    }

    public static boolean isRisen(Entity e) {
        return false;
    }

    /** The game time a Risen Skeleton crumbles (0 for anything else). */
    public static long risenEnd(Entity e) {
        return 0;
    }

    /** Whether {@code pos} lies on the grave creep (a standing stone's or a fading one's) that {@code caster} raised. */
    public static boolean onCreepOf(LivingEntity caster, Vec3 pos) {
        return false;
    }

    /** Whether {@code pos} lies on any grave creep {@code owner} raised. */
    public static boolean onCreep(ServerLevel level, Vec3 pos, String owner) {
        return false;
    }

    /** The game time {@code le}'s Grave Tide ends (0: none on it). */
    public static long tideEnd(LivingEntity le) {
        return 0;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
    }
}
