package com.solegendary.reignofnether.hud;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.custombuilding.CustomBuilding;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.hud.KillFeedClientboundPacket.Entry;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Server half of the BAR kill feed. Every unit killed by an enemy unit and every building an enemy unit brings down
 * becomes a pending line; pending lines are merged (same killer type, victim type and owners -> one line with a
 * count) and flushed every {@link #FLUSH_TICKS} ticks, each player getting only the lines it may know about: it owns
 * or is allied with either side, or the victim fell inside its vision (spectators see all). So an enemy's losses
 * deep in the fog never reach a client. At most {@link KillFeedClientboundPacket#MAX_ENTRIES} lines per flush; a
 * bigger fight just shows its first eight.
 */
public class KillFeedServerEvents {

    public static final int FLUSH_TICKS = 5;
    static final int MAX_PENDING = 64;

    /** A pending line with where the victim fell (for the vision test). */
    record Pending(Entry entry, int x, int z) {
        Pending merged() {
            Entry e = entry;
            return new Pending(new Entry(e.killerOwner(), e.killerKey(), e.victimOwner(), e.victimKey(), e.count() + 1,
                e.building()), x, z);
        }
    }

    private static final List<Pending> pending = new ArrayList<>();
    // the last few lines queued, for the game test (never asserted on by count)
    private static final List<Entry> recent = new ArrayList<>();
    static final int RECENT_MAX = 32;

    public static List<Entry> recentEntries() {
        synchronized (pending) {
            return new ArrayList<>(recent);
        }
    }

    static void queue(Entry entry, int x, int z) {
        synchronized (pending) {
            for (int i = 0; i < pending.size(); i++) {
                if (pending.get(i).entry().sameKill(entry)) {
                    pending.set(i, pending.get(i).merged());
                    remember(entry);
                    return;
                }
            }
            if (pending.size() < MAX_PENDING)
                pending.add(new Pending(entry, x, z));
            remember(entry);
        }
    }

    private static void remember(Entry entry) {
        recent.add(entry);
        if (recent.size() > RECENT_MAX)
            recent.remove(0);
    }

    static String ownerOrEmpty(String s) {
        return s == null ? "" : s;
    }

    public static void onUnitKilled(LivingEntity killer, LivingEntity victim) {
        if (!(killer instanceof Unit k) || !(victim instanceof Unit v))
            return;
        String ko = k.getOwnerName(), vo = v.getOwnerName();
        if (ko == null || ko.isEmpty() || ko.equals(vo) || AlliancesServerEvents.isAllied(ko, vo))
            return;
        queue(new Entry(ko, killer.getType().getDescriptionId(), ownerOrEmpty(vo), victim.getType().getDescriptionId(),
            1, false), victim.getBlockX(), victim.getBlockZ());
    }

    /** Called from BuildingPlacement.destroy for a building brought down by a unit (not a quiet demolition). */
    public static void onBuildingDestroyed(BuildingPlacement placement, Entity attacker) {
        if (!(attacker instanceof Unit k) || placement.ownerName == null || placement.ownerName.isEmpty())
            return;
        String ko = k.getOwnerName();
        if (ko == null || ko.isEmpty() || ko.equals(placement.ownerName)
                || AlliancesServerEvents.isAllied(ko, placement.ownerName))
            return;
        String key;
        if (placement.getBuilding() instanceof CustomBuilding custom) {
            key = custom.name;
        } else {
            ResourceLocation rl = ReignOfNetherRegistries.BUILDING.getKey(placement.getBuilding());
            key = rl == null ? "buildings.reignofnether.unknown" : "buildings.reignofnether." + rl.getPath();
        }
        queue(new Entry(ko, attacker.getType().getDescriptionId(), placement.ownerName, key, 1, true),
            placement.centrePos.getX(), placement.centrePos.getZ());
    }

    /**
     * Whether {@code viewer} may learn about a kill: it owns or is allied with either side, or the spot where the
     * victim fell is inside its vision (fog off / spectator: always). Public for the game test.
     */
    public static boolean canSee(ServerPlayer viewer, String killerOwner, String victimOwner, int x, int z) {
        String name = viewer.getName().getString();
        if (name.equals(killerOwner) || name.equals(victimOwner))
            return true;
        if (!killerOwner.isEmpty() && AlliancesServerEvents.isAllied(name, killerOwner))
            return true;
        if (!victimOwner.isEmpty() && AlliancesServerEvents.isAllied(name, victimOwner))
            return true;
        return FogOfWarServerEvents.isBlockVisibleFor(viewer, x, z);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent evt) {
        LivingEntity victim = evt.getEntity();
        if (victim.level().isClientSide() || !(victim instanceof Unit)
                || !(evt.getSource().getEntity() instanceof LivingEntity killer))
            return;
        onUnitKilled(killer, victim);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.getServer() == null || pending.isEmpty()
                || evt.getServer().getTickCount() % FLUSH_TICKS != 0)
            return;
        flush(evt.getServer());
    }

    static void flush(MinecraftServer server) {
        List<Pending> batch;
        synchronized (pending) {
            batch = new ArrayList<>(pending);
            pending.clear();
        }
        List<Entry> forPlayer = new ArrayList<>(KillFeedClientboundPacket.MAX_ENTRIES);
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            forPlayer.clear();
            for (Pending p : batch) {
                if (forPlayer.size() >= KillFeedClientboundPacket.MAX_ENTRIES)
                    break;
                Entry e = p.entry();
                if (canSee(sp, e.killerOwner(), e.victimOwner(), p.x(), p.z()))
                    forPlayer.add(e);
            }
            if (!forPlayer.isEmpty())
                KillFeedClientboundPacket.sendTo(sp, new ArrayList<>(forPlayer));
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        synchronized (pending) {
            pending.clear();
            recent.clear();
        }
    }
}
