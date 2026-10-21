package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.buildings.placements.FarmPlacement;
import com.solegendary.reignofnether.building.buildings.shared.AbstractBridge;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.util.MiscUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * The building lookups as they were before {@link BuildingGrid}: every one walks the whole list. Kept, with the old
 * bodies unchanged, as the reference BuildingGridGameTests checks the grid against on random layouts, and as the
 * "before" path of its [Stress] benchmark ({@link BuildingGrid#legacyScans}). Nothing in play calls these.
 */
public final class BuildingScansLegacy {
    private BuildingScansLegacy() { }

    /** BuildingUtils.isPosInsideAnyBuilding before the grid. */
    public static boolean isPosInsideAnyBuilding(List<BuildingPlacement> buildings, BlockPos bp) {
        for (BuildingPlacement building : buildings)
            if (building.isPosInsideBuilding(bp))
                return true;
        return false;
    }

    /** BuildingUtils.isPosInsideFarm before the grid. */
    public static boolean isPosInsideFarm(List<BuildingPlacement> buildings, BlockPos bp) {
        for (BuildingPlacement building : buildings)
            if (building instanceof FarmPlacement && building.isPosInsideBuilding(bp))
                return true;
        return false;
    }

    /** UnitUnstick.nearBuilding before the grid: in, or within {@code edge} cells of, any building's box. */
    public static boolean isPosNearAnyBuilding(List<BuildingPlacement> buildings, BlockPos cell, int edge) {
        for (BuildingPlacement b : buildings)
            if (b.isPosInsideBuilding(cell, edge))
                return true;
        return false;
    }

    /** BuildingUtils.findBuilding before the grid. */
    @Nullable
    public static BuildingPlacement findBuilding(List<BuildingPlacement> buildings, BlockPos pos) {
        for (BuildingPlacement building : buildings)
            if (building.originPos.equals(pos) || building.isPosInsideBuilding(pos))
                return building;
        return null;
    }

    /** The origin loop of BuildingUtils.isBuildingBuildable before the grid. */
    public static boolean hasOrigin(List<BuildingPlacement> buildings, BlockPos origin) {
        var flag = false;
        for (BuildingPlacement buildingPlacement : buildings) {
            if (buildingPlacement.originPos.equals(origin)) {
                flag = true;
                break;
            }
        }
        return flag;
    }

    /** BuildingUtils.findClosestBuilding before the grid. */
    @Nullable
    public static BuildingPlacement findClosestBuilding(List<BuildingPlacement> buildings, Vec3 pos, Predicate<BuildingPlacement> condition) {
        double closestDist = 9999;
        BuildingPlacement closestBuilding = null;
        for (BuildingPlacement building : buildings) {
            if (condition.test(building)) {
                BlockPos bp = building.centrePos;
                Vec3 bpVec3 = new Vec3(bp.getX(), bp.getY(), bp.getZ());
                double dist = bpVec3.distanceToSqr(pos);
                if (dist < closestDist) {
                    closestDist = dist;
                    closestBuilding = building;
                }
            }
        }
        return closestBuilding;
    }

    /** MiscUtil.findClosestAttackableBuilding before the grid: no distance check before the footprint walk. */
    @Nullable
    public static BuildingPlacement findClosestAttackableBuilding(Mob unitMob, float range, List<BuildingPlacement> buildings) {
        double closestDist = range;
        BuildingPlacement closestBuilding = null;

        for (BuildingPlacement building : buildings) {
            // Check if the building is attackable, taking into account the relationship
            if (MiscUtil.isBuildingAutoAttackable(unitMob, building) && !(building.getBuilding() instanceof AbstractBridge)) {
                BlockPos attackPos = building.getClosestGroundPos(unitMob.blockPosition(), 1);
                double dist = Math.sqrt(unitMob.blockPosition().distSqr(attackPos));
                if (dist < closestDist) {
                    closestDist = dist;
                    closestBuilding = building;
                }
            }
        }
        return closestBuilding;
    }

    /** The target choice of AttackerUnit.attackMoveNearestEnemyBuilding before the grid (copy, filter, sort). */
    @Nullable
    public static BuildingPlacement nearestEnemyBuildingForAttackMove(Unit unit, List<BuildingPlacement> buildings) {
        ArrayList<BuildingPlacement> eligibleTargets = new ArrayList<>();
        List<BuildingPlacement> buildingsCopy = new ArrayList<>(buildings); // defensive copy
        for (BuildingPlacement buildingPlacement : buildingsCopy) {
            if (!unit.getOwnerName().equals(buildingPlacement.ownerName) &&
                    !AlliancesServerEvents.isAllied(unit.getOwnerName(), buildingPlacement.ownerName) &&
                    !buildingPlacement.ownerName.isBlank() &&
                    buildingPlacement.isAttackable()) {
                eligibleTargets.add(buildingPlacement);
            }
        }
        eligibleTargets.sort(Comparator.comparing(b -> b.centrePos.distToCenterSqr(((Entity) unit).position())));
        return eligibleTargets.isEmpty() ? null : eligibleTargets.get(0);
    }
}
