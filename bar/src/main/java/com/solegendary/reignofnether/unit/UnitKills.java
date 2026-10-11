package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.packets.UnitKillsClientboundPacket;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * BAR's per-unit kill counter (the "kills" line on the selection card). Veterancy only keeps experience, which is a
 * metal ratio, so the plain count lives here: a synced int kept in the entity's persistent data on both sides (no
 * unit class changes). The server bumps it on every enemy unit a unit kills and flushes the changed ids to all
 * players at most once per {@link #FLUSH_TICKS} ticks in one packet (a dozen bytes per kill), and sends a unit's
 * whole tally to a player who joins. Saved with the unit ({@link Unit#addUnitSaveData}).
 */
public class UnitKills {

    public static final String KEY = "bt_kills";
    public static final int FLUSH_TICKS = 20;

    // ids whose counter changed since the last flush (server)
    private static final IntOpenHashSet dirty = new IntOpenHashSet();
    private static final IntArrayList ids = new IntArrayList();
    private static final IntArrayList counts = new IntArrayList();

    public static int get(LivingEntity le) {
        return le.getPersistentData().getInt(KEY);
    }

    public static void set(LivingEntity le, int kills) {
        le.getPersistentData().putInt(KEY, kills);
    }

    /** Adds one kill and queues the sync. Public for the game test. */
    public static void credit(LivingEntity killer) {
        set(killer, get(killer) + 1);
        synchronized (dirty) {
            dirty.add(killer.getId());
        }
    }

    public static void save(LivingEntity le, CompoundTag tag) {
        int k = get(le);
        if (k > 0)
            tag.putInt(KEY, k);
    }

    public static void load(LivingEntity le, CompoundTag tag) {
        if (tag.contains(KEY))
            set(le, tag.getInt(KEY));
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent evt) {
        LivingEntity victim = evt.getEntity();
        if (victim.level().isClientSide() || !(victim instanceof Unit v)
                || !(evt.getSource().getEntity() instanceof LivingEntity killer) || !(killer instanceof Unit k)
                || !killer.isAlive())
            return;
        String ko = k.getOwnerName(), vo = v.getOwnerName();
        if (ko == null || ko.isEmpty() || ko.equals(vo) || AlliancesServerEvents.isAllied(ko, vo))
            return;
        credit(killer);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.getServer() == null || dirty.isEmpty()
                || evt.getServer().getTickCount() % FLUSH_TICKS != 0)
            return;
        Level level = evt.getServer().overworld();
        ids.clear();
        counts.clear();
        synchronized (dirty) {
            for (int id : dirty) {
                if (level.getEntity(id) instanceof LivingEntity le && le.isAlive()) {
                    ids.add(id);
                    counts.add(get(le));
                }
            }
            dirty.clear();
        }
        if (!ids.isEmpty())
            UnitKillsClientboundPacket.sendToAll(ids.toIntArray(), counts.toIntArray());
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        if (!(evt.getEntity() instanceof ServerPlayer sp))
            return;
        ids.clear();
        counts.clear();
        for (LivingEntity le : UnitServerEvents.getAllUnits()) {
            int k = get(le);
            if (k > 0) {
                ids.add(le.getId());
                counts.add(k);
            }
        }
        if (!ids.isEmpty())
            UnitKillsClientboundPacket.sendTo(sp, ids.toIntArray(), counts.toIntArray());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        synchronized (dirty) {
            dirty.clear();
        }
    }
}
