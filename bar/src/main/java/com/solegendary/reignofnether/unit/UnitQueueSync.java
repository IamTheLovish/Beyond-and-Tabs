package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.resources.WreckServerEvents;
import com.solegendary.reignofnether.unit.command.CommandQueue;
import com.solegendary.reignofnether.unit.command.CommandQueues;
import com.solegendary.reignofnether.unit.command.UnitCommand;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.unit.packets.UnitQueueClientboundPacket;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * BAR-style command queue lines. The command queues live only on the server (unit/command/CommandQueues), so the
 * client cannot draw a unit's waypoints by itself. Twice a second this builds, per player, the current order plus
 * the queued orders of that player's own units - read straight from each unit's queue (it used to regroup a global
 * list of everybody's orders first) - and sends it to that player only, and only when it changed since the last
 * packet (a hash compare), so an army standing still costs nothing on the wire. Units whose owner has no player
 * online (bots) are skipped before any work (code_review CR-18: 15 of 16 payloads were built for nobody).
 *
 * An entry is {type, x, y, z, entityId}: type is one of the TYPE_ constants (the client colours by it) and
 * entityId (or -1) lets the client draw to a moving target such as a unit being attacked.
 */
public class UnitQueueSync {

    public static final int TYPE_MOVE = 0;
    public static final int TYPE_ATTACK = 1;
    public static final int TYPE_BUILD = 2;
    public static final int TYPE_RECLAIM = 3;
    public static final int INTS_PER_ENTRY = 5;

    static final int SYNC_TICKS = 10;
    static final int MAX_ENTRIES_PER_UNIT = 16;
    static final int MAX_UNITS_PER_PLAYER = 400;

    private static int ticks = 0;
    // last sent payload hash per player; a missing key means "send the next non-empty state"
    private static final Map<UUID, Long> lastHash = new HashMap<>();

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.getServer() == null)
            return;
        if (++ticks < SYNC_TICKS)
            return;
        ticks = 0;
        List<ServerPlayer> players = evt.getServer().getPlayerList().getPlayers();
        if (players.isEmpty())
            return;

        Set<String> receivers = new HashSet<>(players.size() * 2);
        for (ServerPlayer player : players)
            receivers.add(player.getName().getString());
        Map<String, IntArrayList> byOwner = buildPayloads(receivers);

        for (ServerPlayer player : players) {
            IntArrayList data = byOwner.get(player.getName().getString());
            long hash = 1;
            if (data != null)
                for (int i = 0; i < data.size(); i++)
                    hash = hash * 31 + data.getInt(i);
            Long last = lastHash.get(player.getUUID());
            if (last != null && last == hash)
                continue;
            if (last == null && data == null)
                continue;   // nothing queued and nothing shown on that client yet
            lastHash.put(player.getUUID(), hash);
            int[] payload = data == null ? new int[0] : data.toIntArray();
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), UnitQueueClientboundPacket.of(payload));
        }
    }

    /**
     * The queue-line payload of every owner with at least one ordered unit: [unitId, n, entries...] blocks. Public for
     * the stress benchmark, which times it for every owner.
     */
    public static Map<String, IntArrayList> buildPayloads() {
        return buildPayloads(null);
    }

    /**
     * As {@link #buildPayloads()}, for the owners in {@code receivers} only (null = everyone). One pass over all
     * units, each reading its own queue, so it stays linear at 8v8.
     */
    public static Map<String, IntArrayList> buildPayloads(@Nullable Set<String> receivers) {
        Map<String, IntArrayList> byOwner = new HashMap<>();
        Map<String, int[]> unitCounts = new HashMap<>();
        IntArrayList scratch = new IntArrayList();
        for (LivingEntity le : UnitServerEvents.getAllUnits()) {
            if (!(le instanceof Unit unit))
                continue;
            if (receivers != null && !receivers.contains(unit.getOwnerName()))
                continue;   // nobody would receive this unit's lines
            scratch.clear();
            collect(le, unit, CommandQueues.get(le.getId()), scratch);
            // health (synched entity data) only for the few units that have lines: most units are idle, and in the
            // 8v8 benchmark every extra object touched per unit costs (cold caches between systems; code_review CR-15)
            if (scratch.isEmpty() || !le.isAlive())
                continue;
            String owner = unit.getOwnerName();
            int[] count = unitCounts.computeIfAbsent(owner, k -> new int[1]);
            if (count[0] >= MAX_UNITS_PER_PLAYER)
                continue;
            count[0]++;
            IntArrayList out = byOwner.computeIfAbsent(owner, k -> new IntArrayList());
            out.add(le.getId());
            out.add(scratch.size() / INTS_PER_ENTRY);
            out.addAll(scratch);
        }

        return byOwner;
    }

    /**
     * The unit's current order (if it has a fixed target), as its goals show it - what the unit is really doing, even
     * when something outside the queue set it - followed by the orders waiting in its queue, in order.
     */
    static void collect(LivingEntity le, Unit unit, @Nullable CommandQueue queue, IntArrayList out) {
        boolean queued = queue != null && !queue.isEmpty();
        // current order
        Entity reclaim = le instanceof WorkerUnit ? WreckServerEvents.getReclaimTarget(le) : null;
        BuildingPlacement building = le instanceof WorkerUnit wu && wu.getBuildRepairGoal() != null
            ? wu.getBuildRepairGoal().getBuildingTarget() : null;
        if (reclaim != null) {
            put(out, TYPE_RECLAIM, reclaim.blockPosition(), reclaim.getId());
        } else if (building != null) {
            put(out, TYPE_BUILD, building.centrePos, -1);
        } else if (le instanceof AttackerUnit au && au.getAttackMoveTarget() != null) {
            put(out, TYPE_ATTACK, au.getAttackMoveTarget(), -1);
        } else if (unit.getMoveGoal() != null && unit.getMoveGoal().getMoveTarget() != null) {
            put(out, TYPE_MOVE, unit.getMoveGoal().getMoveTarget(), -1);
        } else if (queued && unit.getTargetGoal() != null && unit.getTargetGoal().getTarget() != null) {
            // an attack target counts only ahead of queued orders: idle units auto-acquire targets all the time,
            // and syncing those would churn packets without the player having ordered anything
            // no position: the target moves, and a moving position would change the hash (and send) every sync;
            // the client draws to the entity itself and skips the entry if it can't see it
            put(out, TYPE_ATTACK, BlockPos.ZERO, unit.getTargetGoal().getTarget().getId());
        }
        // a worker's shift-queued sites live in its build goal, not in CommandQueues, and a site queued in the fog is
        // only on the server: list them here so the owner sees the whole build queue - and sees it again whenever
        // the worker's entity comes back into the client's view, since this goes out to the player, not the entity
        if (le instanceof WorkerUnit wu && wu.getBuildRepairGoal() != null) {
            for (BuildingPlacement b : wu.getBuildRepairGoal().queuedBuildings) {
                if (b == building || b.centrePos == null || out.size() >= MAX_ENTRIES_PER_UNIT * INTS_PER_ENTRY)
                    continue;
                put(out, TYPE_BUILD, b.centrePos, -1);
            }
            if (wu.getExploreBuildLocationGoal() != null)
                for (BuildingPlacement b : wu.getExploreBuildLocationGoal().getFogQueuedBuildings()) {
                    if (b.centrePos == null || out.size() >= MAX_ENTRIES_PER_UNIT * INTS_PER_ENTRY)
                        continue;
                    put(out, TYPE_BUILD, b.centrePos, -1);
                }
        }
        if (!queued)
            return;
        for (int i = 0; i < queue.size(); i++) {
            if (out.size() >= MAX_ENTRIES_PER_UNIT * INTS_PER_ENTRY)
                break;
            UnitCommand c = queue.get(i);
            int type = typeOf(c.action());
            if (type < 0)
                continue;
            BlockPos pos = c.pos();
            int eid = -1;
            if (c.action() == UnitAction.BUILD_REPAIR) {
                BuildingPlacement b = BuildingUtils.findBuilding(false, pos);
                if (b != null)
                    pos = b.centrePos;
            } else if (c.action() == UnitAction.ATTACK || c.action() == UnitAction.FOLLOW
                    || c.action() == UnitAction.RECLAIM) {
                eid = c.targetId();   // position stays the order's (stable hash); client follows the entity
            }
            // an order on a target that has died will be skipped when it comes up (CommandQueues): no line to it
            if (le.level() instanceof ServerLevel sl && CommandQueues.targetGone(sl, c))
                continue;
            if (pos == null || (eid < 0 && pos.getX() == 0 && pos.getY() == 0 && pos.getZ() == 0))
                continue;
            put(out, type, pos, eid);
        }
    }

    static int typeOf(UnitAction action) {
        return switch (action) {
            case MOVE, PATROL, FOLLOW, GARRISON, GUARD -> TYPE_MOVE;
            case ATTACK, ATTACK_MOVE, ATTACK_BUILDING -> TYPE_ATTACK;
            case BUILD_REPAIR, FARM -> TYPE_BUILD;
            case RECLAIM -> TYPE_RECLAIM;
            default -> -1;
        };
    }

    static void put(IntArrayList out, int type, BlockPos pos, int eid) {
        out.add(type);
        out.add(pos.getX());
        out.add(pos.getY());
        out.add(pos.getZ());
        out.add(eid);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent evt) {
        // a sentinel no real hash will match: always send on the next sync, even an empty state, so a client that
        // kept lines from its previous session (stale entity ids) gets them cleared
        lastHash.put(evt.getEntity().getUUID(), Long.MIN_VALUE);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        lastHash.clear();
        ticks = 0;
    }
}
