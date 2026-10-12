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
 * Brassworks game ender - the <b>Earthcracker</b> (design/big_units.md 2.9): a steam drill tower (earthcracker.nbt) that
 * drives a seismic charge under the enemy's capitol and splits the ground: a fault line races through the base along
 * the drill's bearing, wrecking every building and throwing every unit on it. Its drill runs hot: after every stage it
 * vents for {@link EarthcrackerPlacement#VENT_TICKS} ticks - no charge, and raid damage counts double, announced to every
 * player. Needs the Great Forge, Tier 3 and an Engineer-Captain.
 */
public class Earthcracker extends GameEnder {

    public static final String buildingName = "Earthcracker";
    public static final String structureName = "earthcracker";
    public static final ResourceCost cost = ResourceCosts.EARTHCRACKER;

    public Earthcracker() {
        super(structureName, buildingName, cost, Blocks.PISTON, "textures/block/piston_top.png", () -> Buildings.GREAT_FORGE);
        this.startingBlockTypes.add(Blocks.POLISHED_BLACKSTONE);
        this.startingBlockTypes.add(Blocks.DEEPSLATE_BRICKS);
    }

    @Override
    public BuildingPlacement createBuildingPlacement(Level level, BlockPos pos, Rotation rotation, String ownerName) {
        return new EarthcrackerPlacement(this, level, pos, rotation, ownerName,
            getAbsoluteBlockData(getRelativeBlockData(level), level, pos, rotation), false);
    }

    @Override
    protected List<FormattedCharSequence> strikeTooltip() {
        return List.of(
            Component.translatable("buildings.reignofnether.earthcracker.tooltip1", EarthcrackerPlacement.FAULT_LENGTH, (int) EarthcrackerPlacement.BUILDING_DAMAGE).getVisualOrderText(),
            Component.translatable("buildings.reignofnether.earthcracker.tooltip2", EarthcrackerPlacement.VENT_TICKS / 20).getVisualOrderText());
    }
}
