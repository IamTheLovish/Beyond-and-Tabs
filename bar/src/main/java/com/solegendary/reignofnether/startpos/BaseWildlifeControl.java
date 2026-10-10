package com.solegendary.reignofnether.startpos;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps bases clear of wild clutter. Alex's Mobs bison, grizzlies and the like wandered into bases and fights
 * (lovish's clip 15-51), soaking shots and blocking units. Within BASE_RADIUS of every capitol (and of every start
 * position until its capitol stands) hostile and large wild mobs are removed at match start and by a cheap sweep
 * every two seconds, and natural spawns of them there are cancelled. Small peaceful ambience (pigs, chickens,
 * rabbits...) stays, as do units, tamed/named/leashed mobs, villagers and golems.
 */
public final class BaseWildlifeControl {
    private BaseWildlifeControl() { }

    /** Horizontal radius kept clear round each base: the cleared start area. */
    public static final float BASE_RADIUS = StartAreaClearing.BASE_RADIUS;
    /** Vertical reach of a sweep: bases are flat, and a mob on a far cliff top is not in the base. */
    static final int VERTICAL = 20;
    static final int SWEEP_TICKS = 40;
    /** A wild mob at least this wide (bison, grizzly, horse...) is "large"; a pig is 0.9. */
    static final float LARGE_WIDTH = 1.25f;
    static final float LARGE_HEALTH = 25f;

    /** Start positions of the formed battlefield, guarded before their capitols exist. */
    private static final List<BlockPos> starts = new ArrayList<>();
    private static int sweepTicks = 0;

    public static synchronized void registerBases(ServerLevel level, List<BlockPos> positions) {
        for (BlockPos p : positions)
            if (!starts.contains(p))
                starts.add(p);
        for (BlockPos p : positions)
            sweep(level, p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        synchronized (BaseWildlifeControl.class) {
            starts.clear();
        }
    }

    /** Wild clutter: hostile, or big enough to get in the way. Never a unit or anyone's pet. */
    public static boolean isClutter(Entity e) {
        if (!(e instanceof Mob m) || e instanceof Unit || !e.isAlive())
            return false;
        if (m.hasCustomName() || m.isLeashed() || m.isVehicle() || m.isPassenger())
            return false;
        if (m instanceof OwnableEntity o && o.getOwnerUUID() != null)
            return false;
        if (m instanceof AbstractGolem || m instanceof AbstractVillager || m instanceof EnderDragon || m instanceof WitherBoss)
            return false;
        if (m instanceof Enemy)
            return true;
        return m.getBbWidth() >= LARGE_WIDTH || m.getMaxHealth() >= LARGE_HEALTH;
    }

    /** Whether (x, z) at height y is inside a guarded base. */
    static boolean inBase(double x, double y, double z) {
        double r2 = BASE_RADIUS * BASE_RADIUS;
        for (BuildingPlacement b : BuildingServerEvents.getBuildings()) {
            if (!b.getBuilding().isCapitol || b.isDestroyedServerside || b.centrePos == null)
                continue;
            BlockPos c = b.centrePos;
            double dx = c.getX() + 0.5 - x, dz = c.getZ() + 0.5 - z;
            if (dx * dx + dz * dz <= r2 && Math.abs(c.getY() - y) <= VERTICAL)
                return true;
        }
        synchronized (BaseWildlifeControl.class) {
            for (BlockPos c : starts) {
                double dx = c.getX() + 0.5 - x, dz = c.getZ() + 0.5 - z;
                if (dx * dx + dz * dz <= r2 && Math.abs(c.getY() - y) <= VERTICAL * 2)
                    return true;
            }
        }
        return false;
    }

    /** Natural, chunk-generation and patrol spawns of clutter never happen inside a base. */
    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn evt) {
        MobSpawnType t = evt.getSpawnType();
        if (t != MobSpawnType.NATURAL && t != MobSpawnType.CHUNK_GENERATION && t != MobSpawnType.PATROL
                && t != MobSpawnType.STRUCTURE && t != MobSpawnType.EVENT && t != MobSpawnType.JOCKEY
                && t != MobSpawnType.REINFORCEMENT)
            return;
        if (evt.getLevel().isClientSide() || !isClutter(evt.getEntity()))
            return;
        if (inBase(evt.getX(), evt.getY(), evt.getZ()))
            evt.setSpawnCancelled(true);
    }

    /** Every two seconds: one entity query per capitol (a handful even at 8v8). */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.level.isClientSide() || evt.level.dimension() != Level.OVERWORLD)
            return;
        if (++sweepTicks < SWEEP_TICKS)
            return;
        sweepTicks = 0;
        ServerLevel level = (ServerLevel) evt.level;
        for (BuildingPlacement b : BuildingServerEvents.getBuildings()) {
            if (b.getBuilding().isCapitol && !b.isDestroyedServerside && b.centrePos != null)
                sweep(level, b.centrePos.getX() + 0.5, b.centrePos.getY(), b.centrePos.getZ() + 0.5);
        }
        List<BlockPos> copy;
        synchronized (BaseWildlifeControl.class) {
            if (starts.isEmpty())
                return;
            copy = new ArrayList<>(starts);
        }
        for (BlockPos p : copy)
            sweep(level, p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
    }

    /** Removes clutter within BASE_RADIUS of (x, z). Returns how many went. */
    public static int sweep(ServerLevel level, double x, double y, double z) {
        if (level == null)
            return 0;
        AABB box = new AABB(x - BASE_RADIUS, y - VERTICAL, z - BASE_RADIUS, x + BASE_RADIUS, y + VERTICAL, z + BASE_RADIUS);
        double r2 = BASE_RADIUS * BASE_RADIUS;
        int removed = 0;
        for (Mob m : level.getEntitiesOfClass(Mob.class, box, BaseWildlifeControl::isClutter)) {
            double dx = m.getX() - x, dz = m.getZ() - z;
            if (dx * dx + dz * dz > r2)
                continue;
            m.discard();   // no drops, no death animation: it simply was never there
            removed++;
        }
        return removed;
    }
}
