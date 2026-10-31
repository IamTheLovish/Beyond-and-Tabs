package com.solegendary.reignofnether.building.production;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.resources.ResourceCost;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

public abstract class ProductionBuilding extends Building {
    public ProductionItemList productions = new ProductionItemList();
    public boolean canSetRallyPoint = true;
    public float spawnRadiusOffset = 1.0F;

    public ProductionBuilding(String structureName, ResourceCost cost, boolean isCapitol) {
        super(structureName, cost, isCapitol);
    }

    public BuildingPlacement createBuildingPlacement(Level level, BlockPos pos, Rotation rotation, String ownerName) {
        return new ProductionPlacement(this, level, pos, rotation, ownerName, BuildingUtils.getAbsoluteBlockData(this.getRelativeBlockData(level), level, pos, rotation), this.isCapitol);
    }

    public BlockPos getDefaultOutdoorSpawnPoint(BlockPos minCorner, ProductionPlacement pp) {
        return minCorner.offset((int) -spawnRadiusOffset, 0, (int) -spawnRadiusOffset);
    }

    public BlockPos getIndoorSpawnPoint(Level level, BuildingPlacement placement) {
        BlockPos spawnPoint;
        // stops at the bottom of the world: over void (the End) it would never end (review C-12)
        for(spawnPoint = placement.centrePos; spawnPoint.getY() > level.getMinBuildHeight() && level.getBlockState(spawnPoint.below()).isAir(); spawnPoint = spawnPoint.offset(0, -1, 0)) {
        }

        return spawnPoint;
    }
}
