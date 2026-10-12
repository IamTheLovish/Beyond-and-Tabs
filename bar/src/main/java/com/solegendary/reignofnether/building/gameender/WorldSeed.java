package com.solegendary.reignofnether.building.gameender;

import static com.solegendary.reignofnether.building.BuildingUtils.getAbsoluteBlockData;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

import java.util.List;

/**
 * Verdant Court game ender - the <b>World Seed</b> (design/big_units.md 2.6): a colossal seedling on a mossy mound
 * (world_seed.nbt) that, at full charge, flings its seed into the enemy's capitol: a <b>Heartroot</b> bursts up there and
 * the ground around it turns to grove for as long as it lives - enemy buildings are torn by roots, enemy units are
 * slowed and hurt, the Court's units heal. The counter is in the strike itself: kill the Heartroot and the grove dies.
 * Needs the Heart of the Wild, Tier 3 and an Elder Druid.
 */
public class WorldSeed extends GameEnder {

    public static final String buildingName = "World Seed";
    public static final String structureName = "world_seed";
    public static final ResourceCost cost = ResourceCosts.WORLD_SEED;

    public WorldSeed() {
        super(structureName, buildingName, cost, Blocks.FLOWERING_AZALEA_LEAVES, "textures/block/flowering_azalea_top.png", () -> Buildings.HEART_OF_THE_WILD);
        this.startingBlockTypes.add(Blocks.MOSS_BLOCK);
        this.startingBlockTypes.add(Blocks.ROOTED_DIRT);
    }

    @Override
    public BuildingPlacement createBuildingPlacement(Level level, BlockPos pos, Rotation rotation, String ownerName) {
        return new WorldSeedPlacement(this, level, pos, rotation, ownerName,
            getAbsoluteBlockData(getRelativeBlockData(level), level, pos, rotation), false);
    }

    @Override
    protected List<FormattedCharSequence> strikeTooltip() {
        return List.of(
            Component.translatable("buildings.reignofnether.world_seed.tooltip1", WorldSeedPlacement.RADIUS, WorldSeedPlacement.OVERGROWTH_TICKS / 20, (int) WorldSeedPlacement.BUILDING_DPS).getVisualOrderText(),
            Component.translatable("buildings.reignofnether.world_seed.tooltip2", (int) WorldSeedPlacement.HEARTROOT_HP).getVisualOrderText());
    }
}
