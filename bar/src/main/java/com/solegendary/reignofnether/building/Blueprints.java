package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.network.Audience;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.goals.BuildRepairGoal;
import com.solegendary.reignofnether.unit.goals.ExploreBuildLocationGoal;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Blueprints: a building ordered from a worker is only an ORDER until a worker reaches the site (lovish, Oct 22:
 * "Blueprints shouldn't drop physical prints until the worker gets there, and you shouldn't be able to see enemy
 * blueprints").
 *
 * <p>Before, {@code BuildingServerEvents.placeBuilding} did everything at click time: felled the trees, levelled the
 * footprint, registered the building (so it blocked pathing, other placements and the enemy's eyes), laid scaffolding
 * and the whole foundation layer, and told every client that could see the chunk - while the worker was still walking
 * over. Now it only validates, hands the placement to this registry and to the worker's queue, and sends a ghost to
 * the owner's side. Nothing is registered, nothing in the world changes and no packet leaves for an enemy. When the
 * first assigned worker is in build range, {@link #arrive} re-checks the site and the old placement code runs
 * ({@code BuildingServerEvents.beginConstruction}); from there it is an ordinary building.
 *
 * <p>The pieces:
 * <ul>
 *   <li><b>The ghost</b> is a client-side drawing ({@link BlueprintClientEvents}) of a {@link BlueprintClientboundPacket}.
 *       It goes to {@link Audience#team}: the owner, their allies and spectators (who see everything), never an
 *       enemy - the data is not sent, rather than hidden at render time. A late joiner is caught up by
 *       {@link #syncTo}.</li>
 *   <li><b>Reservation</b>: a pending site reserves its footprint against the same side's own overlapping placements
 *       ({@link #reserves}, read by BuildingValidators), and blocks nothing else: it is not in the building registry,
 *       so units walk through it and an enemy can build on it.</li>
 *   <li><b>Lifetime</b>: a blueprint lives exactly as long as a worker holds it (as its target, in its shift-queue or
 *       in its fog queue), like a BAR constructor's queued build orders. A new order for the worker, Stop, the worker
 *       dying or giving up on an unreachable site drops it ({@link #tick} sweeps every {@link #SWEEP_TICKS}); another
 *       worker ordered onto it keeps it alive.</li>
 *   <li><b>Money</b>: BAR's flow economy pays while it builds (BuildingPlacement.tickFlowConstruction), nothing at
 *       placement - so a blueprint that never started owes and refunds nothing.</li>
 *   <li><b>Arrival</b>: the site is checked again with {@link BuildingValidators} (without the ghosts, which cannot
 *       conflict with themselves). Invalid now (an enemy built there, the terrain changed, an annex lost its lab): the
 *       blueprint is cancelled with a message. Enemy ground units standing on it: it waits for them up to
 *       {@link #OCCUPIED_WAIT_TICKS}, then is cancelled with a message. The owner's own units are walked off as before.</li>
 * </ul>
 * Server thread only, like the building registry.
 */
public final class Blueprints {
    private Blueprints() { }

    /** How often the sweep looks for blueprints no worker holds any more. */
    public static final int SWEEP_TICKS = 10;
    /** A fresh order is not swept for this long: its workers may only join the unit list a tick after they spawn. */
    public static final int GRACE_TICKS = 40;
    /** How long enemy units standing on a site hold the blueprint before it is given up. */
    public static final int OCCUPIED_WAIT_TICKS = 10 * 20;

    public enum Arrival { STARTED, WAITING, CANCELLED }

    private static final ArrayList<BuildingPlacement> PENDING = new ArrayList<>();
    private static final IdentityHashMap<BuildingPlacement, Long> ORDERED_AT = new IdentityHashMap<>();
    private static final IdentityHashMap<BuildingPlacement, Long> OCCUPIED_SINCE = new IdentityHashMap<>();

    // --------------------------------------------------------------------------------------------- the registry

    /** The blueprints waiting for a worker, in order (read-only; tests and the bots read it). */
    public static List<BuildingPlacement> all() {
        return Collections.unmodifiableList(PENDING);
    }

    public static boolean isPending(@Nullable BuildingPlacement b) {
        if (b == null)
            return false;
        for (int i = 0, n = PENDING.size(); i < n; i++)
            if (PENDING.get(i) == b)
                return true;
        return false;
    }

    /** The blueprint whose origin is {@code pos} or whose footprint holds it, else null. */
    @Nullable
    public static BuildingPlacement findAt(@Nullable BlockPos pos) {
        if (pos == null)
            return null;
        for (int i = 0, n = PENDING.size(); i < n; i++) {
            BuildingPlacement p = PENDING.get(i);
            if (p.originPos.equals(pos) || p.isPosInsideBuilding(pos))
                return p;
        }
        return null;
    }

    /** The blueprints {@code owner} has ordered and no worker has started yet (a fresh list). */
    public static List<BuildingPlacement> pendingOf(String owner) {
        List<BuildingPlacement> out = new ArrayList<>();
        for (BuildingPlacement p : PENDING)
            if (p.ownerName.equals(owner))
                out.add(p);
        return out;
    }

    /** A new order: remember it and show its ghost to the owner's side. Not registered as a building. */
    public static void add(BuildingPlacement bp) {
        if (isPending(bp))
            return;
        PENDING.add(bp);
        ORDERED_AT.put(bp, bp.getLevel().getGameTime());
        PacketHandler.INSTANCE.send(Audience.team(bp.ownerName), BlueprintClientboundPacket.add(bp));
    }

    private static boolean remove(BuildingPlacement bp) {
        boolean removed = false;
        for (int i = 0; i < PENDING.size(); i++)
            if (PENDING.get(i) == bp) {
                PENDING.remove(i);
                removed = true;
                break;
            }
        ORDERED_AT.remove(bp);
        OCCUPIED_SINCE.remove(bp);
        return removed;
    }

    /**
     * Drops a blueprint: its ghost goes from the owner's side and, with a {@code messageKey}, the owner is told why.
     * Nothing was built or paid, so there is nothing to undo or refund. Workers still pointing at it notice on their
     * next tick (BuildingUtils.isBuildingBuildable is false) and move on to their next order.
     */
    public static void cancel(BuildingPlacement bp, @Nullable String messageKey) {
        if (!remove(bp))
            return;
        PacketHandler.INSTANCE.send(Audience.team(bp.ownerName), BlueprintClientboundPacket.remove(bp));
        if (messageKey != null)
            HudClientboundPacket.showTempMessageI18n(bp.ownerName, messageKey);
    }

    /** A new match or a stopping server: every pending site goes (the clients drop theirs with the match reset). */
    public static void clear() {
        PENDING.clear();
        ORDERED_AT.clear();
        OCCUPIED_SINCE.clear();
    }

    /** Catches a (re)joining player up with the ghosts of their own side (spectators: all of them). */
    public static void syncTo(ServerPlayer sp) {
        for (BuildingPlacement p : PENDING)
            if (Audience.knowsPrivately(sp, p.ownerName))
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp), BlueprintClientboundPacket.add(p));
    }

    // ------------------------------------------------------------------------------------------- the reservation

    /**
     * Does a pending site of {@code owner}'s side overlap the box min..max (a new placement's footprint)? Read by
     * BuildingValidators: a side's own blueprints keep each other's ground free, and are invisible to everyone else.
     */
    public static boolean reserves(String owner, BlockPos min, BlockPos max) {
        for (int i = 0, n = PENDING.size(); i < n; i++) {
            BuildingPlacement p = PENDING.get(i);
            if (!(owner.equals(p.ownerName) || AlliancesServerEvents.isAllied(owner, p.ownerName)))
                continue;
            if (overlaps(p.minCorner, p.maxCorner, min, max))
                return true;
        }
        return false;
    }

    static boolean overlaps(BlockPos aMin, BlockPos aMax, BlockPos bMin, BlockPos bMax) {
        return aMin.getX() <= bMax.getX() && aMax.getX() >= bMin.getX()
            && aMin.getY() <= bMax.getY() && aMax.getY() >= bMin.getY()
            && aMin.getZ() <= bMax.getZ() && aMax.getZ() >= bMin.getZ();
    }

    // ----------------------------------------------------------------------------------------------- the sweep

    /** Every server tick (BuildingServerEvents.onWorldTick); does work every {@link #SWEEP_TICKS}, and only with blueprints. */
    public static void tick(ServerLevel level) {
        if (PENDING.isEmpty() || level.getGameTime() % SWEEP_TICKS != 0)
            return;
        long now = level.getGameTime();
        IdentityHashMap<BuildingPlacement, Boolean> held = heldTargets();
        for (BuildingPlacement p : new ArrayList<>(PENDING)) {
            if (held.containsKey(p))
                continue;
            Long at = ORDERED_AT.get(p);
            if (at != null && now - at < GRACE_TICKS)
                continue;
            cancel(p, null);   // its last worker died, was given another order or gave up: the order goes with it
        }
    }

    /** Every site some worker holds: as its target, in its shift-queue or in its fog queue. */
    private static IdentityHashMap<BuildingPlacement, Boolean> heldTargets() {
        IdentityHashMap<BuildingPlacement, Boolean> held = new IdentityHashMap<>();
        for (LivingEntity e : UnitServerEvents.getAllUnits()) {
            if (!(e instanceof WorkerUnit worker) || !e.isAlive())
                continue;
            BuildRepairGoal goal = worker.getBuildRepairGoal();
            if (goal != null) {
                if (goal.getBuildingTarget() != null)
                    held.put(goal.getBuildingTarget(), Boolean.TRUE);
                for (BuildingPlacement q : goal.queuedBuildings)
                    held.put(q, Boolean.TRUE);
            }
            ExploreBuildLocationGoal explore = worker.getExploreBuildLocationGoal();
            if (explore != null)
                for (BuildingPlacement q : explore.getFogQueuedBuildings())
                    held.put(q, Boolean.TRUE);
        }
        return held;
    }

    /** The ids of the workers whose current target is {@code bp} (the builders on a site that is about to rise). */
    private static int[] workersOn(BuildingPlacement bp) {
        List<Integer> ids = new ArrayList<>();
        for (LivingEntity e : UnitServerEvents.getAllUnits())
            if (e instanceof WorkerUnit worker && worker.getBuildRepairGoal() != null
                    && worker.getBuildRepairGoal().getBuildingTarget() == bp)
                ids.add(e.getId());
        int[] out = new int[ids.size()];
        for (int i = 0; i < out.length; i++)
            out[i] = ids.get(i);
        return out;
    }

    // ---------------------------------------------------------------------------------------------- the arrival

    /**
     * A worker holding blueprint {@code bp} is in build range of it (BuildRepairGoal.tick). Checks the site again and
     * either starts physical construction (STARTED), waits for enemy units to leave it (WAITING) or cancels the
     * blueprint with a message (CANCELLED). A site that is not pending (already started) is STARTED.
     */
    public static Arrival arrive(BuildingPlacement bp) {
        if (!isPending(bp))
            return Arrival.STARTED;
        ServerLevel level = BuildingServerEvents.getServerLevel();
        if (level == null)
            return Arrival.WAITING;
        long now = level.getGameTime();

        // the ground and the neighbours as they are NOW: an enemy may have built here, the terrain may have changed.
        // Without the ghosts: the site's own side cannot conflict with itself (and this one is in the list)
        String error = BuildingValidators.getPlacementValidityError(level, bp.getBuilding(), bp.originPos, bp.ownerName,
            bp.rotation, bp.isDiagonalBridge, false, true, false);
        if (error != null) {
            cancel(bp, "hud.reignofnether.blueprint_blocked");
            return Arrival.CANCELLED;
        }
        if (hostileUnitOnSite(level, bp)) {
            long since = OCCUPIED_SINCE.computeIfAbsent(bp, k -> now);
            if (now - since >= OCCUPIED_WAIT_TICKS) {
                cancel(bp, "hud.reignofnether.blueprint_occupied");
                return Arrival.CANCELLED;
            }
            return Arrival.WAITING;
        }

        // go: the ghost gives way to the building (whose own packet follows from beginConstruction)
        int[] builders = workersOn(bp);
        remove(bp);
        PacketHandler.INSTANCE.send(Audience.team(bp.ownerName), BlueprintClientboundPacket.remove(bp));
        bp.initPlacedBlocks();   // the world may differ from when the order was given
        BuildingServerEvents.beginConstruction(bp, builders);
        return Arrival.STARTED;
    }

    /** Enemy ground units standing in the bottom layers of the footprint (the owner's own and allies' walk off). */
    static boolean hostileUnitOnSite(ServerLevel level, BuildingPlacement bp) {
        AABB box = new AABB(bp.minCorner.getX(), bp.minCorner.getY(), bp.minCorner.getZ(),
            bp.maxCorner.getX() + 1, bp.minCorner.getY() + 3, bp.maxCorner.getZ() + 1);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (!(e instanceof Unit unit) || !e.isAlive() || !e.onGround())
                continue;
            String owner = unit.getOwnerName();
            if (owner == null || owner.isEmpty() || owner.equals(bp.ownerName) || AlliancesServerEvents.isAllied(owner, bp.ownerName))
                continue;
            return true;
        }
        return false;
    }
}
