package com.solegendary.reignofnether.essence;

import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Essence wisps (design/essence_casters.md; lovish, Oct 29 2026: dead units leave "floating essence rather than metal
 * wrecks", and casters consume it - no BAR-style rez bots). Every unit that dies, either side, drops one wisp where it
 * fell worth {@link Essence#essenceFor} its metal cost; summons drop none ({@link Essence#isSummoned}), so they cannot
 * farm it. A wisp floats in place for {@link #LIFETIME_TICKS} and fades; at most {@link #MAX_WISPS} exist (the oldest
 * go first), so a long 8v8 cannot pile them up. Wrecks are untouched: essence is a second, separate drop.
 * <p>
 * An essence carrier ({@link Essence#isCarrier}) with room pulls the wisps within {@link #GATHER_RANGE} blocks of it:
 * they fly to it at {@link #PULL_SPEED} blocks a tick and give it their essence on touch; a wisp worth more than the
 * room left keeps the rest and floats on, so a full carrier never wastes essence.
 * <p>
 * A wisp is a vanilla block display (like a wreck: no collision, no pathing impact, no packets of our own, saved with
 * the world), a small glowing diamond carrying its value and birth tick in its persistent data. Server side only.
 */
public class EssenceServerEvents {

    public static final String TAG = "bt_essence_wisp";
    static final String KEY_VALUE = "bt_essence_value";
    static final String KEY_BORN = "bt_essence_born";

    /** lovish's draft: a wisp floats for 45 s, at most 200 on the map, a carrier gathers within 8 blocks. */
    public static final int LIFETIME_TICKS = 45 * 20;
    public static final int MAX_WISPS = 200;
    public static final double GATHER_RANGE = 8.0;
    /** Blocks a pulled wisp flies per tick (a wisp 8 blocks out arrives in under a second). */
    public static final double PULL_SPEED = 0.5;
    /** A pulled wisp this close to its carrier's middle is taken. */
    static final double TOUCH = 0.9;
    /** How often (ticks) free wisps look for a carrier; a pulled wisp moves every tick. */
    static final int SCAN_TICKS = 5;
    static final float SIZE = 0.3f;

    private static final List<Entity> wisps = new ArrayList<>();
    // wisp -> the carrier it is flying to (server thread only)
    private static final Map<Entity, LivingEntity> pulls = new IdentityHashMap<>();
    // the scan's carriers with room (reused)
    private static final List<LivingEntity> carriers = new ArrayList<>();

    public static List<Entity> getWisps() {
        return wisps;
    }

    public static boolean isWisp(Entity e) {
        return e != null && e.getTags().contains(TAG);
    }

    public static int valueOf(Entity wisp) {
        return wisp.getPersistentData().getInt(KEY_VALUE);
    }

    /** The game time the wisp was dropped (its fade clock). */
    public static long bornOf(Entity wisp) {
        return wisp.getPersistentData().getLong(KEY_BORN);
    }

    /** Ages a wisp by {@code ticks} (its clock starts that much earlier). For the game test. */
    public static void backdate(Entity wisp, int ticks) {
        wisp.getPersistentData().putLong(KEY_BORN, bornOf(wisp) - ticks);
    }

    // LOW: after HeroServerEvents' Totem of Undying has had its say - a hero it saves is not dead (WreckServerEvents too)
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent evt) {
        LivingEntity le = evt.getEntity();
        if (le.level().isClientSide() || !(le.level() instanceof ServerLevel level) || !(le instanceof Unit unit))
            return;
        if (Essence.isSummoned(le))
            return;
        ResourceCost cost = unit.getCost();
        int value = Essence.essenceFor(cost == null ? 0 : cost.metal());
        spawnWisp(level, le.getX(), le.getY() + Math.min(1.5, le.getBbHeight() * 0.6), le.getZ(), value);
    }

    /** Drops a wisp worth {@code value} essence at x/y/z (its middle); null if none could be made. */
    public static Entity spawnWisp(ServerLevel level, double x, double y, double z, int value) {
        Entity display = EntityType.BLOCK_DISPLAY.create(level);
        if (display == null || value <= 0)
            return null;
        CompoundTag tag = new CompoundTag();
        display.saveWithoutId(tag);
        tag.put("block_state", NbtUtils.writeBlockState(Blocks.SEA_LANTERN.defaultBlockState()));
        // a small cube stood on its corner (a diamond), centred on the entity's position
        Quaternionf tilt = new Quaternionf().rotateX(0.6155f).rotateZ((float) (Math.PI / 4));
        Vector3f centre = tilt.transform(new Vector3f(SIZE / 2, SIZE / 2, SIZE / 2));
        CompoundTag tf = new CompoundTag();
        tf.put("left_rotation", floats(tilt.x, tilt.y, tilt.z, tilt.w));
        tf.put("right_rotation", floats(0, 0, 0, 1));
        tf.put("translation", floats(-centre.x, -centre.y, -centre.z));
        tf.put("scale", floats(SIZE, SIZE, SIZE));
        tag.put("transformation", tf);
        // full bright: essence glows in the dark and under the fog of a canopy
        CompoundTag light = new CompoundTag();
        light.putInt("block", 15);
        light.putInt("sky", 15);
        tag.put("brightness", light);
        display.load(tag);
        display.moveTo(x, y, z, level.random.nextFloat() * 360f, 0);
        display.addTag(TAG);
        display.getPersistentData().putInt(KEY_VALUE, value);
        display.getPersistentData().putLong(KEY_BORN, level.getGameTime());
        level.addFreshEntity(display);   // onJoin registers it (and applies the cap)
        return display;
    }

    static ListTag floats(float... v) {
        ListTag list = new ListTag();
        for (float f : v)
            list.add(FloatTag.valueOf(f));
        return list;
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent evt) {
        if (evt.getLevel().isClientSide() || !isWisp(evt.getEntity()))
            return;
        wisps.removeIf(Entity::isRemoved);   // a faded, taken or unloaded wisp holds no place under the cap
        if (!wisps.contains(evt.getEntity()))
            wisps.add(evt.getEntity());
        while (wisps.size() > MAX_WISPS) {
            // the oldest by birth tick, not by join order: a chunk reloaded later rejoins its old wisps last (PR 360
            // review); a tie (one tick's drops) goes in join order
            int oldest = 0;
            for (int i = 1; i < wisps.size(); i++)
                if (bornOf(wisps.get(i)) < bornOf(wisps.get(oldest)))
                    oldest = i;
            Entity w = wisps.remove(oldest);
            pulls.remove(w);
            w.discard();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        wisps.clear();
        pulls.clear();
        carriers.clear();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.getServer() == null)
            return;
        tick(evt.getServer().overworld().getGameTime());
    }

    /**
     * One tick of the wisps in every dimension (a death anywhere drops one): the fade (once a second), the carrier scan,
     * the pulls. {@code now} is the overworld's game time, which every dimension shares (the wisps' birth ticks too).
     */
    static void tick(long now) {
        if (wisps.isEmpty()) {
            pulls.clear();
            return;
        }
        if (now % 20 == 0)
            fade(now);
        if (now % SCAN_TICKS == 0)
            findCarriers();
        if (!pulls.isEmpty())
            pull();
    }

    private static void fade(long now) {
        boolean shimmer = now % 40 == 0;
        for (int i = wisps.size() - 1; i >= 0; i--) {   // backwards: remove(i)
            Entity w = wisps.get(i);
            if (w.isRemoved()) {
                wisps.remove(i);
                continue;
            }
            if (!(w.level() instanceof ServerLevel level))
                continue;
            long born = bornOf(w);
            if (born > now) {   // a /time set rewound the clock: restart this wisp's
                w.getPersistentData().putLong(KEY_BORN, now);
            } else if (now - born > LIFETIME_TICKS) {
                level.sendParticles(ParticleTypes.SOUL, w.getX(), w.getY(), w.getZ(), 3, 0.15, 0.15, 0.15, 0.02);
                pulls.remove(w);
                w.discard();
                wisps.remove(i);
            } else if (shimmer) {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, w.getX(), w.getY(), w.getZ(), 1, 0.08, 0.08, 0.08, 0.005);
            }
        }
    }

    /** Free wisps within reach of a carrier with room start flying to the nearest one. */
    private static void findCarriers() {
        carriers.clear();
        for (LivingEntity le : UnitServerEvents.getAllUnits())
            if (le.isAlive() && Essence.room(le) > 0)   // room > 0: a carrier, not full
                carriers.add(le);
        if (carriers.isEmpty()) {
            pulls.clear();
            return;
        }
        double r2 = GATHER_RANGE * GATHER_RANGE;
        for (Entity w : wisps) {
            if (w.isRemoved())
                continue;
            LivingEntity cur = pulls.get(w);
            if (cur != null && cur.isAlive() && Essence.room(cur) > 0)
                continue;
            LivingEntity best = null;
            double bestD = r2;
            for (LivingEntity c : carriers) {
                if (c.level() != w.level())
                    continue;
                double d = w.distanceToSqr(c.getX(), c.getY() + c.getBbHeight() * 0.5, c.getZ());
                if (d <= bestD) {
                    bestD = d;
                    best = c;
                }
            }
            if (best != null)
                pulls.put(w, best);
            else
                pulls.remove(w);
        }
    }

    /** Pulled wisps fly one step to their carrier; one that arrives gives what fits and floats on with the rest. */
    private static void pull() {
        Iterator<Map.Entry<Entity, LivingEntity>> it = pulls.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Entity, LivingEntity> e = it.next();
            Entity w = e.getKey();
            LivingEntity c = e.getValue();
            if (w.isRemoved() || !c.isAlive() || c.isRemoved() || c.level() != w.level() || Essence.room(c) <= 0
                    || !(c.level() instanceof ServerLevel level)) {
                it.remove();
                continue;
            }
            Vec3 to = new Vec3(c.getX(), c.getY() + c.getBbHeight() * 0.5, c.getZ());
            Vec3 d = to.subtract(w.position());
            double dist = d.length();
            if (dist <= TOUCH) {
                int value = valueOf(w);
                int took = Essence.add(c, value);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, to.x, to.y, to.z, 4, 0.2, 0.3, 0.2, 0.01);
                level.playSound(null, c.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.5f, 1.6f);
                if (took >= value)
                    w.discard();   // the list drops it on the next fade pass or join
                else
                    w.getPersistentData().putInt(KEY_VALUE, value - took);   // the carrier is full: the rest floats on
                it.remove();
                continue;
            }
            if (dist > GATHER_RANGE * 2) {   // the carrier ran (or was carried) off: the wisp stops where it is
                it.remove();
                continue;
            }
            Vec3 step = d.scale(Math.min(1.0, PULL_SPEED / dist));
            w.setPos(w.getX() + step.x, w.getY() + step.y, w.getZ() + step.z);
        }
    }
}
