package com.solegendary.reignofnether.unit.goals;

import com.solegendary.reignofnether.building.Blueprints;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.unit.UnitChunkLoader;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import javax.annotation.Nullable;
import java.util.*;

// used to move a worker over to the location of a place to build in fog before transferring control over to BuildRepairGoal
public class ExploreBuildLocationGoal extends MoveToTargetBlockGoal {

    private WorkerUnit workerUnit;
    public ExploreBuildLocationGoal(WorkerUnit workerUnit) {
        super((Mob) workerUnit, true, 0);
        this.workerUnit = workerUnit;
    }

    private final ArrayList<BuildingPlacement> fogQueuedBuildings = new ArrayList<>();
    public ArrayList<BuildingPlacement> getFogQueuedBuildings() { return fogQueuedBuildings; }
    private final Map<BlockPos, List<BuildingBlock>> fogQueuedBlocksToDraw = new HashMap<>();

    /**
     * Client side only: the ghosts of the sites this player queued in the fog, per worker id. They used to live on the
     * worker's client entity, and a client entity is thrown away and rebuilt whenever the server stops and starts
     * tracking it (the camera scrolling off, the worker leaving then re-entering view), which wiped the ghosts and
     * made a still-running server queue look cancelled. Keyed by the entity id (the same on both sides), dropped when
     * the server says the unit left the level ({@link #forgetGhosts}).
     */
    private static final Map<Integer, Map<BlockPos, List<BuildingBlock>>> CLIENT_GHOSTS = new HashMap<>();

    public Map<BlockPos, List<BuildingBlock>> getFogQueuedBlocksToDraw() {
        if (this.mob.level().isClientSide())
            return CLIENT_GHOSTS.computeIfAbsent(this.mob.getId(), id -> new HashMap<>());
        return fogQueuedBlocksToDraw;
    }

    public static void forgetGhosts(int entityId) {
        CLIENT_GHOSTS.remove(entityId);
    }

    /** Client side: a new world or server (ids start over). */
    public static void forgetAllGhosts() {
        CLIENT_GHOSTS.clear();
    }

    @Override
    public boolean canUse() {
        return super.canUse() || !fogQueuedBuildings.isEmpty();
    }

    @Override
    public boolean canContinueToUse() {
        return super.canContinueToUse() || !fogQueuedBuildings.isEmpty();
    }

    public void tick() {
        boolean hasBuildingTarget = workerUnit.getBuildRepairGoal().getBuildingTarget() != null;
        int tickCount = mob.tickCount % 2 == 0 ? mob.tickCount : mob.tickCount + 1;
        if (hasBuildingTarget && !workerUnit.getBuildRepairGoal().isBuilding() && tickCount % 20 == 0) {
            workerUnit.getBuildRepairGoal().start();
            super.stopMoving();
        }
        else if (!this.mob.level().isClientSide() && !fogQueuedBuildings.isEmpty() && !hasBuildingTarget) {
            BuildingPlacement bpl = fogQueuedBuildings.get(0);
            boolean isPlaced = BuildingServerEvents.isRegistered(bpl) || Blueprints.isPending(bpl);   // explored: a blueprint, until the worker arrives

            // not placed and is explored -> place
            if (!isPlaced && FogOfWarServerEvents.isBlockVisibleFor(((Unit) mob).getOwnerName(), bpl.centrePos.getX(), bpl.centrePos.getZ())) {
                BuildingPlacement newBuilding = BuildingServerEvents.placeBuilding(bpl, bpl.originPos, bpl.rotation, bpl.ownerName, new int[]{}, false, bpl.isDiagonalBridge, false, true);
                if (newBuilding == null) { // failed to place due to obstacles in fog or some other reason
                    fogQueuedBuildings.remove(bpl);
                }
                super.stopMoving();
            } else if (isPlaced) { // is placed - >start building
                super.stopMoving();
                fogQueuedBuildings.remove(bpl);
                workerUnit.getBuildRepairGoal().queuedBuildings.add(0, bpl);
                workerUnit.getBuildRepairGoal().startNextQueuedBuilding();
            } else { // not explored -> go explore
                // keep the site's chunks loaded while the worker walks there (it is not placed, so no building holds
                // them): an unloaded target is "no path" and the worker would stand, however long the owner's
                // camera is away
                if (this.mob.level() instanceof ServerLevel sl && mob.tickCount % 20 == 0)
                    UnitChunkLoader.holdSite(sl, bpl.centrePos);
                setMoveTarget(bpl.centrePos);
            }
        }
    }

    // don't override checkpoints
    @Override
    public void setMoveTarget(@Nullable BlockPos bp) {
        boolean changed = !Objects.equals(bp, this.moveTarget);
        if (changed) {
            resetRecalcBackoff();
            recalcCooldown = 0;
        }
        this.moveTarget = bp;

        if (changed && !this.mob.level().isClientSide())
            this.start();
    }

    // if we override stop() it for some reason is called after start() and we can never begin this goal...
    public void reset() {
        fogQueuedBuildings.clear();
        fogQueuedBlocksToDraw.clear();
        if (this.mob.level().isClientSide())
            CLIENT_GHOSTS.remove(this.mob.getId());
        super.stopMoving();
    }
}
