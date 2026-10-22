package com.solegendary.reignofnether.unit.goals;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.Blueprints;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon;
import com.solegendary.reignofnether.building.buildings.placements.FarmPlacement;
import com.solegendary.reignofnether.building.buildings.shared.AbstractBridge;
import com.solegendary.reignofnether.building.buildings.villagers.OakStockpile;
import com.solegendary.reignofnether.resources.ResourceName;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.util.MiscUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

// Move towards a building to build/repair it
// will continually try to move towards the building if too far away as long as this goal is being enacted

// if isBuilding(), stop the random lookaround goal (look at the next block to place instead)
// and run Player place block animations with arms shown

public class BuildRepairGoal extends MoveToTargetBlockGoal {

    public boolean ignoreNextCheckpoint = false;
    public final List<BuildingPlacement> queuedBuildings = new ArrayList<>();
    /** Shift-queue depth per builder (sites queued here plus ones waiting in fog); BuildingServerEvents enforces it. */
    public static final int MAX_QUEUE = 32;
    private BuildingPlacement buildingTarget;

    private Boolean isBuildingServerside = false;

    // bumped whenever any worker's building target changes, so BuilderIndex (target -> workers) knows to rebuild
    private static int targetGeneration = 0;

    public static int getTargetGeneration() {
        return targetGeneration;
    }

    public boolean autocastRepair = false;

    // A worker sent to a site it can't reach (walled in, across water, a cliff) used to stand forever with the order
    // (persistent goal, so it never went idle and the idle-constructor alert never fired). Server side, measured in game
    // time so it doesn't matter how often tick() runs: if the worker hasn't got a block closer to the site for
    // RANGE_FALLBACK_TICKS it may build from where it is if that's within FALLBACK_BUILD_RANGE (BAR constructors
    // build from range anyway); if it still isn't building after GIVE_UP_TICKS it drops the site and goes idle.
    public static final int RANGE_FALLBACK_TICKS = 8 * 20;
    public static final int GIVE_UP_TICKS = 15 * 20;
    public static final int FALLBACK_BUILD_RANGE = 4;
    public static final int BUILD_RANGE = 2;
    private long stallSinceTick = -1;
    private double bestSiteDistSqr = Double.MAX_VALUE;
    private boolean buildFromRange = false;
    @Nullable private net.minecraft.world.phys.Vec3 stallAnchor = null;
    private static final java.util.Map<String, Long> lastGiveUpMsgTick = new java.util.HashMap<>();

    public BuildRepairGoal(Mob mob) {
        super(mob, true, 0);
    }

    // isBuilding() accepts the worker as arrived at range 2, but moveReachRange is 0 so the base recalc
    // threshold is the 1.5-block settle distance. That leaves a 1.5-2 block band where a worker is building
    // yet still "too far -> repath" - which loops it on the spot. Match the threshold to the build range so a
    // worker in build range never triggers a recalc.
    @Override
    public double getMinDistToRecalculateSqr() {
        return Math.max(super.getMinDistToRecalculateSqr(), 2.0 * 2.0);
    }

    public void setIsBuildingServerside(boolean isBuilding) {
        this.isBuildingServerside = isBuilding;
    }

    public boolean startNextQueuedBuilding() {
        queuedBuildings.removeIf(b -> !BuildingUtils.isBuildingBuildable(this.mob.level().isClientSide(), b));
        if (queuedBuildings.size() > 0) {
            setBuildingTarget(queuedBuildings.get(0));
            return true;
        }
        return false;
    }

    public void tick() {
        if (this.mob.tickCount % 5 != 0)
            return;

        if (buildingTarget == null) {
            if (!this.mob.level().isClientSide() && WorkerUnit.isIdle((WorkerUnit) this.mob) && autocastRepair) {
                BuildingPlacement building = BuildingUtils.findClosestBuilding(
                        this.mob.level().isClientSide(),
                        this.mob.getEyePosition(),
                        b -> b.getBlocksPlaced() < b.getBlocksTotal() &&
                        BuildingServerEvents.getUnitToBuildingRelationship((Unit) this.mob, b) != Relationship.HOSTILE
                );
                if (building != null)
                    setBuildingTarget(building);
            }
            return;
        }
        if (!BuildingUtils.isBuildingBuildable(this.mob.level().isClientSide(), buildingTarget)) {
            if (!startNextQueuedBuilding()) {
                if (buildingTarget instanceof FarmPlacement && mob instanceof WorkerUnit) {
                    ((WorkerUnit) mob).getGatherResourceGoal().setTargetResourceName(ResourceName.FOOD);
                    ((WorkerUnit) mob).getGatherResourceGoal().setTargetFarm(buildingTarget);
                }
                stopBuilding();
            }
            return;
        }
        calcMoveTarget();
        if (!this.mob.level().isClientSide() && checkUnreachable())
            return;
        // a blueprint (Blueprints) comes to life when its worker is in range: the site is checked again and physical
        // construction starts, or it is cancelled and the worker moves on to its next order
        if (!this.mob.level().isClientSide() && buildingTarget != null && Blueprints.isPending(buildingTarget)
                && isBuilding() && Blueprints.arrive(buildingTarget) == Blueprints.Arrival.CANCELLED) {
            if (!startNextQueuedBuilding())
                stopBuilding();
            return;
        }
        if (isBuilding() && buildingTarget != null) {
            BlockPos bp = buildingTarget.centrePos;
            this.mob.getLookControl().setLookAt(bp.getX(), bp.getY(), bp.getZ());
            mob.getLookControl().lookAtCooldown = 20;
        }
    }

    // horizontal distance (squared) from the worker to the nearest point of the site's footprint
    private double siteDistSqr() {
        double x = this.mob.getX(), z = this.mob.getZ();
        double cx = Math.max(buildingTarget.minCorner.getX(), Math.min(x, buildingTarget.maxCorner.getX() + 1));
        double cz = Math.max(buildingTarget.minCorner.getZ(), Math.min(z, buildingTarget.maxCorner.getZ() + 1));
        return (x - cx) * (x - cx) + (z - cz) * (z - cz);
    }

    private void resetStall() {
        stallSinceTick = -1;
        bestSiteDistSqr = Double.MAX_VALUE;
        buildFromRange = false;
        stallAnchor = null;
    }

    // true if the worker gave up on its site this tick (see GIVE_UP_TICKS)
    private boolean checkUnreachable() {
        long now = this.mob.level().getGameTime();
        if (isBuilding()) {
            stallSinceTick = now;
            bestSiteDistSqr = siteDistSqr();
            return false;
        }
        double d = siteDistSqr();
        // progress = a block closer than ever, or walking somewhere (1.5+ blocks from where progress was last seen):
        // a worker detouring round a lake moves away from its site for a while and must not give up for it
        if (stallSinceTick < 0 || stallAnchor == null || Math.sqrt(d) < Math.sqrt(bestSiteDistSqr) - 1.0
                || this.mob.position().distanceToSqr(stallAnchor) > 1.5 * 1.5) {
            stallSinceTick = now;
            bestSiteDistSqr = Math.min(bestSiteDistSqr, d);
            stallAnchor = this.mob.position();
            return false;
        }
        long stalled = now - stallSinceTick;
        if (stalled >= RANGE_FALLBACK_TICKS && !buildFromRange
                && buildingTarget.isPosInsideBuilding(mob.getOnPos(), FALLBACK_BUILD_RANGE)) {
            buildFromRange = true;
            stallSinceTick = now;   // give the range build its own window before judging it again
            return false;
        }
        if (stalled < GIVE_UP_TICKS)
            return false;
        String owner = ((Unit) this.mob).getOwnerName();
        Long last = lastGiveUpMsgTick.get(owner);
        if (last == null || now - last > 60) {   // one message for a group giving up together
            lastGiveUpMsgTick.put(owner, now);
            com.solegendary.reignofnether.hud.HudClientboundPacket.showTempMessageI18n(owner,
                    "hud.reignofnether.build_site_unreachable");
        }
        queuedBuildings.remove(buildingTarget);
        if (!startNextQueuedBuilding())
            stopBuilding();
        return true;
    }

    private void calcMoveTarget() {
        if (this.buildingTarget == null)
            return;
        // Hold ONE approach cell instead of recomputing the closest perimeter cell from the unit's CURRENT
        // position every tick. That recompute made the target flip between two near-equidistant cells as the
        // unit moved, so it oscillated around the boundary ("back and forth") instead of committing to a path.
        // Like the gather goal holding a fixed block, keep the chosen cell until it's null or built over, then
        // re-pick. setBuildingTarget nulls moveTarget so a NEW building still gets a fresh approach cell.
        if (this.moveTarget != null && !isApproachInvalid(this.moveTarget))
            return;
        this.moveTarget = this.buildingTarget.getClosestGroundPos(mob.getOnPos(), 1, true);
    }

    // Re-pick the approach cell only if it got built over (non-bridge), mirroring getClosestGroundPos's own
    // exclusion. Bridges have special over-water geometry, so their cell is held until the target changes.
    private boolean isApproachInvalid(BlockPos bp) {
        return !(buildingTarget.getBuilding() instanceof AbstractBridge)
                && BuildingUtils.isPosInsideAnyBuilding(this.mob.level().isClientSide(), bp);
    }

    // only count as building if in range of the target - building is actioned in Building.tick()
    public boolean isBuilding() {
        if (this.mob.level().isClientSide())
            return isBuildingServerside;

        if (GarrisonableBuildingAddon.getGarrison((Unit) this.mob) != null)
            return false;

        if (buildingTarget != null) {
            Relationship rs = BuildingServerEvents.getUnitToBuildingRelationship((Unit) this.mob, buildingTarget);
            if (rs == Relationship.OWNED || buildingTarget.getBuilding() instanceof AbstractBridge) {
                return buildingTarget.isPosInsideBuilding(mob.getOnPos(), buildFromRange ? FALLBACK_BUILD_RANGE : BUILD_RANGE);
            }
        }
        return false;
    }

    public void setBuildingTarget(@Nullable BuildingPlacement target) {
        if (target != null && !BuildingUtils.isBuildingBuildable(this.mob.level().isClientSide(), target))
            return;

        if (target != null) {
            if (ignoreNextCheckpoint)
                ignoreNextCheckpoint = false;
            else {
                MiscUtil.addUnitCheckpoint((Unit) mob, new BlockPos(
                        target.centrePos.getX(),
                        target.originPos.getY() + 1,
                        target.centrePos.getZ()),
                        true
                );
            }
        }
        this.buildingTarget = target;
        targetGeneration++;
        resetStall();
        this.moveTarget = null; // force a fresh approach cell for the new target (calcMoveTarget then holds it)
        calcMoveTarget();
        this.start();
    }

    public BuildingPlacement getBuildingTarget() { return buildingTarget; }

    // if we override stop() it for some reason is called after start() and we can never begin this goal...
    public void stopBuilding() {
        queuedBuildings.clear();
        buildingTarget = null;
        targetGeneration++;
        resetStall();
        super.stopMoving();
    }
}
