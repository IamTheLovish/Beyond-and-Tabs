package com.solegendary.reignofnether.building;

// class for static building functions

import com.solegendary.reignofnether.building.addon.NetherConvertingAddon;
import com.solegendary.reignofnether.building.buildings.monsters.SculkCatalyst;
import com.solegendary.reignofnether.building.buildings.monsters.Stronghold;
import com.solegendary.reignofnether.building.buildings.piglins.Fortress;
import com.solegendary.reignofnether.building.buildings.placements.BeaconPlacement;
import com.solegendary.reignofnether.building.buildings.placements.SculkCatalystPlacement;
import com.solegendary.reignofnether.building.buildings.shared.AbstractBridge;
import com.solegendary.reignofnether.building.buildings.villagers.Castle;
import com.solegendary.reignofnether.keybinds.Keybinding;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

public class BuildingUtils {

    public static List<Keybinding> keybindings = Arrays.asList();

    public static boolean isBridge(Building building) {
        return building instanceof AbstractBridge;
    }

    // gets the cursor position rotated according to the preselected building
    public static BlockPos getBuildingOriginPos(BlockPos cursorBp, boolean isBridge, Rotation buildingRotation, Vec3i buildingDimensions) {
        int xAdj = 0;
        int zAdj = 0;
        int xRadius = buildingDimensions.getX() / 2;
        int zRadius = buildingDimensions.getZ() / 2;

        switch (buildingRotation) {
            case NONE -> {
                xAdj = -xRadius;
                zAdj = -zRadius;
            }
            case CLOCKWISE_90 -> {
                xAdj = xRadius;
                zAdj = -zRadius;
            }
            case CLOCKWISE_180 -> {
                xAdj = xRadius;
                zAdj = zRadius;
            }
            case COUNTERCLOCKWISE_90 -> {
                xAdj = -xRadius;
                zAdj = zRadius;
            }
        }
        if (isBridge) {
            cursorBp = cursorBp.offset(0, -1, 0);
        }

        return cursorBp.offset(xAdj, 0, zAdj);
    }


    public static int getTotalCompletedBuildingsOwned(boolean isClientSide, String ownerName) {
        List<BuildingPlacement> buildings;
        if (isClientSide)
            buildings = BuildingClientEvents.getBuildings();
        else
            buildings = BuildingServerEvents.getBuildings();

        List<BuildingPlacement> list = new ArrayList<>();
        for (BuildingPlacement b : buildings) {
            if (b.isBuilt && b.ownerName.equals(ownerName)) {
                list.add(b);
            }
        }
        return list.size();
    }

    public static boolean castleOwned(boolean isClientSide, String ownerName) {
        List<BuildingPlacement> buildings;
        if (isClientSide)
            buildings = BuildingClientEvents.getBuildings();
        else
            buildings = BuildingServerEvents.getBuildings();

        for (BuildingPlacement building : buildings) {
            if (ownerName.equals(building.ownerName) &&
                building.isBuilt &&
                (building.getBuilding() instanceof Castle ||
                building.getBuilding() instanceof Fortress ||
                building.getBuilding() instanceof Stronghold)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isBuildingBuildable(boolean isClientSide, BuildingPlacement building) {
        // is a building standing at its origin (the cell of the BuildingGrid, not the whole list)
        boolean flag = BuildingGrid.legacyScans
            ? BuildingScansLegacy.hasOrigin(getBuildingsList(isClientSide), building.originPos)
            : BuildingGrid.of(isClientSide).hasOrigin(building.originPos);
        // a blueprint (a pending site, Blueprints) is buildable too: that is how a worker gets sent to start it
        if (!flag && !isClientSide)
            flag = Blueprints.isPending(building);
        return flag && building.getBlocksPlaced() < building.getBlocksTotal() && (!building.isBuilt || building.getBuilding().repairable);
    }

    /** The registered building at {@code pos} (as {@link #findBuilding}), else the server's blueprint there, else null. */
    @Nullable
    public static BuildingPlacement findBuildingOrBlueprint(boolean isClientSide, BlockPos pos) {
        BuildingPlacement b = findBuilding(isClientSide, pos);
        if (b == null && !isClientSide)
            b = Blueprints.findAt(pos);
        return b;
    }

    // returns a list of BPs that may reside in unique chunks for fog of war calcs
    public static ArrayList<BlockPos> getUniqueChunkBps(BuildingPlacement building) {
        AABB aabb = new AABB(
                building.minCorner,
                building.maxCorner.offset(1,1,1)
        );

        ArrayList<BlockPos> bps = new ArrayList<>();
        double x = aabb.minX;
        double z = aabb.minZ;
        do {
            do {
                bps.add(new BlockPos((int) x, (int) aabb.minY, (int) z));
                x += 16;
            }
            while (x <= aabb.maxX);
            z += 16;
            x = aabb.minX;
        }
        while (z <= aabb.maxZ);

        // include far corners
        bps.add(new BlockPos((int) aabb.maxX, (int) aabb.minY, (int) aabb.minZ));
        bps.add(new BlockPos((int) aabb.minX, (int) aabb.minY, (int) aabb.maxZ));
        bps.add(new BlockPos((int) aabb.maxX, (int) aabb.minY, (int) aabb.maxZ));

        return bps;
    }

    public static BuildingPlacement getNewBuildingPlacement(Building building, Level level, BlockPos pos, Rotation rotation, String ownerName, boolean isDiagonalBridge) {
        BuildingPlacement buildingPlacement;
        if (building instanceof AbstractBridge bridge) {
            buildingPlacement = bridge.createBuildingPlacement(level, pos, rotation, ownerName, isDiagonalBridge);
        } else {
            buildingPlacement = building.createBuildingPlacement(level, pos, rotation, ownerName);
        }

        if (buildingPlacement != null) {
            buildingPlacement.updateButtons();
            buildingPlacement.setLevel(level);
        }

        return buildingPlacement;
    }

    // note originPos may be an air block
    // the first building in list order with its origin at pos or its box round it, from the BuildingGrid cell of pos
    public static BuildingPlacement findBuilding(boolean isClientSide, BlockPos pos) {
        if (BuildingGrid.legacyScans)
            return BuildingScansLegacy.findBuilding(getBuildingsList(isClientSide), pos);
        return BuildingGrid.of(isClientSide).findAt(pos);
    }

    // functions for corners/centrePos given only blocks
    // if you have access to the Building itself, you should use .minCorner, .maxCorner and .centrePos
    public static BlockPos getMinCorner(List<BuildingBlock> blocks) {
        MinMaxValues minMax = calculateMinMax(blocks);
        return new BlockPos(minMax.minX, minMax.minY, minMax.minZ);
    }

    public static BlockPos getMaxCorner(List<BuildingBlock> blocks) {
        MinMaxValues minMax = calculateMinMax(blocks);
        return new BlockPos(minMax.maxX, minMax.maxY, minMax.maxZ);
    }

    public static BlockPos getCentrePos(List<BuildingBlock> blocks) {
        MinMaxValues minMax = calculateMinMax(blocks);
        return new BlockPos(
                (minMax.minX + minMax.maxX) / 2,
                (minMax.minY + minMax.maxY) / 2,
                (minMax.minZ + minMax.maxZ) / 2
        );
    }

    private static MinMaxValues calculateMinMax(List<BuildingBlock> blocks) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (BuildingBlock block : blocks) {
            BlockPos pos = block.getBlockPos();
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();

            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (z < minZ) minZ = z;

            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;
        }

        return new MinMaxValues(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static class MinMaxValues {
        int minX, minY, minZ;
        int maxX, maxY, maxZ;

        public MinMaxValues(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }
    }



    public static Vec3i getBuildingSize(ArrayList<BuildingBlock> blocks) {
        BlockPos min = getMinCorner(blocks);
        BlockPos max = getMaxCorner(blocks);
        return new Vec3i(
                max.getX() - min.getX(),
                max.getY() - min.getY(),
                max.getZ() - min.getZ()
        );
    }

    public static ArrayList<BuildingBlock> getAbsoluteBlockData(ArrayList<BuildingBlock> staticBlocks, LevelAccessor level, BlockPos originPos, Rotation rotation, Vec3i blockOffset) {
        ArrayList<BuildingBlock> blocks = new ArrayList<>();

        for (BuildingBlock block : staticBlocks) {
            block = block.rotate(level, rotation);
            BlockPos bp = block.getBlockPos();

            block.setBlockPos(new BlockPos(
                    bp.getX() + originPos.getX() + blockOffset.getX(),
                    bp.getY() + originPos.getY() + blockOffset.getY() + 1,
                    bp.getZ() + originPos.getZ() + blockOffset.getZ()
            ));
            blocks.add(block);
        }
        return blocks;
    }

    // get BlockPos values with absolute world positions
    public static ArrayList<BuildingBlock> getAbsoluteBlockData(ArrayList<BuildingBlock> staticBlocks, LevelAccessor level, BlockPos originPos, Rotation rotation) {
        return getAbsoluteBlockData(staticBlocks, level, originPos, rotation, new Vec3i(0,0,0));
    }

    // returns whether the given pos is part of ANY building in the level
    // WARNING: very processing expensive!
    public static boolean isPosPartOfAnyBuilding(boolean isClientSide, BlockPos bp, boolean onlyPlacedBlocks, int range) {
        List<BuildingPlacement> buildings = isClientSide
                ? BuildingClientEvents.getBuildings()
                : BuildingServerEvents.getBuildings();

        // Precompute range squared to avoid repeated calculation
        int rangeSquared = range * range;

        for (BuildingPlacement building : buildings) {
            if ((range == 0 || bp.distSqr(building.centrePos) < rangeSquared) &&
                building.isPosPartOfBuilding(bp, onlyPlacedBlocks)) {
                return true;
            }
        }
        return false;
    }


    // returns whether the given pos is inside ANY building's box in the level
    // (43 call sites, some per unit per tick: reads the BuildingGrid cell of bp instead of every building)
    public static boolean isPosInsideAnyBuilding(boolean isClientSide, BlockPos bp) {
        if (BuildingGrid.legacyScans)
            return BuildingScansLegacy.isPosInsideAnyBuilding(getBuildingsList(isClientSide), bp);
        return BuildingGrid.of(isClientSide).anyInside(bp, 0, false);
    }

    public static boolean isPosInsideFarm(boolean isClientSide, BlockPos bp) {
        if (BuildingGrid.legacyScans)
            return BuildingScansLegacy.isPosInsideFarm(getBuildingsList(isClientSide), bp);
        return BuildingGrid.of(isClientSide).anyInside(bp, 0, true);
    }

    /**
     * The building passing {@code condition} whose centre is closest to {@code pos} (squared distance under 9999,
     * about 100 blocks); on a tie the one earlier in the list. A ring search of the BuildingGrid's centre cells: it
     * stops once nothing unread can be closer, instead of testing every building (idle builders ask every 5 ticks).
     */
    @Nullable
    public static BuildingPlacement findClosestBuilding(boolean isClientSide, Vec3 pos, Predicate<BuildingPlacement> condition) {
        if (BuildingGrid.legacyScans)
            return BuildingScansLegacy.findClosestBuilding(getBuildingsList(isClientSide), pos, condition);
        return findClosestBuilding(BuildingGrid.of(isClientSide), pos, condition);
    }

    /** {@link #findClosestBuilding(boolean, Vec3, Predicate)} over the given grid (the GameTests pass their own). */
    @Nullable
    public static BuildingPlacement findClosestBuilding(BuildingGrid grid, Vec3 pos, Predicate<BuildingPlacement> condition) {
        return grid.nearestByCentre(pos.x, pos.y, pos.z, 0, 9999, building -> {
            // the old new Vec3(centrePos).distanceToSqr(pos), without the Vec3
            BlockPos bp = building.centrePos;
            double d0 = pos.x - bp.getX();
            double d1 = pos.y - bp.getY();
            double d2 = pos.z - bp.getZ();
            return d0 * d0 + d1 * d1 + d2 * d2;
        }, condition);
    }

    public static boolean isInNetherRange(boolean isClientSide, BlockPos bp) {
        List<BuildingPlacement> buildings = getBuildingsList(isClientSide);

        for (BuildingPlacement building : buildings) {
            NetherConvertingAddon ncb;
            if ((ncb = building.getBuilding().getActiveAddon(NetherConvertingAddon.class)) != null && ncb.getMaxNetherRange(building) > 0) {
                double maxRangeSquared = Math.pow(ncb.getMaxNetherRange(building), 2);
                if (bp.distSqr(building.centrePos) <= maxRangeSquared) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isWithinRangeOfMaxedCatalyst(LivingEntity entity) {
        List<BuildingPlacement> buildings = getBuildingsList(entity.level().isClientSide());

        double maxCatalystRangeSquared = SculkCatalyst.ESTIMATED_RANGE * SculkCatalyst.ESTIMATED_RANGE;

        for (BuildingPlacement building : buildings) {
            if (building instanceof SculkCatalystPlacement sc) {
                if (entity.distanceToSqr(Vec3.atCenterOf(sc.centrePos)) < maxCatalystRangeSquared &&
                        sc.getUncappedNightRange() >= SculkCatalyst.MAX_NIGHT_RANGE * 1.5f) {
                    return true;
                }
            }
        }
        return false;
    }

    // Helper method to get the buildings list based on client or server side.
    public static List<BuildingPlacement> getBuildingsList(boolean isClientSide) {
        return isClientSide
                ? BuildingClientEvents.getBuildings()
                : BuildingServerEvents.getBuildings();
    }

    public static BeaconPlacement getBeacon(boolean isClientSide) {
        List<BuildingPlacement> buildings = getBuildingsList(isClientSide);
        for (BuildingPlacement building : buildings)
            if (building instanceof BeaconPlacement beacon)
                return beacon;
        return null;
    }

    public static void clearBuildingArea(BuildingPlacement building) {
        if (building != null) {
            for (int x = building.minCorner.getX() - 1; x < building.maxCorner.getX() + 2; x++)
                for (int y = building.minCorner.getY(); y < building.maxCorner.getY() + 2; y++)
                    for (int z = building.minCorner.getZ() - 1; z < building.maxCorner.getZ() + 2; z++)
                        if (!isPosInsideAnyBuilding(building.level.isClientSide(), new BlockPos(x,y,z)))
                            building.getLevel().setBlockAndUpdate(new BlockPos(x,y,z), Blocks.AIR.defaultBlockState());
        }
    }

    public static BlockPos rotatePos(BlockPos pos, Rotation rot) {
        if (rot == Rotation.NONE) {
            return new BlockPos(pos.getX(), pos.getY(), pos.getZ());
        } else if (rot == Rotation.CLOCKWISE_90) {
            return new BlockPos(-pos.getX(), pos.getY(), pos.getZ());
        } else if (rot == Rotation.CLOCKWISE_180) {
            return new BlockPos(-pos.getX(), pos.getY(), -pos.getZ());
        } else {
            return new BlockPos(pos.getX(), pos.getY(), -pos.getZ());
        }
    }

    // not sure why, but this needs to be slightly different to BuildingUtils.rotatePos for some uses
    public static BlockPos altRotatePos(BlockPos pos, Rotation rot) {
        if (rot == Rotation.NONE) {
            return new BlockPos(pos.getX(), pos.getY(), pos.getZ());
        } else if (rot == Rotation.CLOCKWISE_90) {
            return new BlockPos(-pos.getZ(), pos.getY(), pos.getX()); // z and x switched
        } else if (rot == Rotation.CLOCKWISE_180) {
            return new BlockPos(-pos.getX(), pos.getY(), -pos.getZ());
        } else {
            return new BlockPos(pos.getZ(), pos.getY(), -pos.getX()); // z and x switched
        }
    }

    // does the player own one of these buildings?
    public static int numFinishedBuildings(boolean isClientSide, Building building, String playerName) {
        List<BuildingPlacement> buildings = getBuildingsList(isClientSide);
        int count = 0;
        for (BuildingPlacement bpl : buildings) {
            if (bpl.getBuilding().isTypeOf(building) && bpl.isBuilt && bpl.ownerName.equals(playerName)) {
                count += 1;
            }
        }
        return count;
    }
}
