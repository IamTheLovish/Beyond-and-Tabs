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
 * Sunforged game ender - the <b>Sun Spire</b> (design/big_units.md 2.3): a white-and-gold quartz needle with a beacon
 * crown (sun_spire.nbt, tools/gen_structures.py) that gathers the sun through three stages and lances it down on the
 * enemy's capitol. Needs the Castle, Tier 3 and a Royal Architect.
 */
public class SunSpire extends GameEnder {

    public static final String buildingName = "Sun Spire";
    public static final String structureName = "sun_spire";
    public static final ResourceCost cost = ResourceCosts.SUN_SPIRE;

    public SunSpire() {
        super(structureName, buildingName, cost, Blocks.QUARTZ_PILLAR, "textures/block/beacon.png", () -> Buildings.CASTLE);
        this.startingBlockTypes.add(Blocks.SMOOTH_QUARTZ);
        this.startingBlockTypes.add(Blocks.QUARTZ_BRICKS);
    }

    @Override
    public BuildingPlacement createBuildingPlacement(Level level, BlockPos pos, Rotation rotation, String ownerName) {
        return new SunSpirePlacement(this, level, pos, rotation, ownerName,
            getAbsoluteBlockData(getRelativeBlockData(level), level, pos, rotation), false);
    }

    @Override
    protected List<FormattedCharSequence> strikeTooltip() {
        return List.of(
            Component.translatable("buildings.reignofnether.sun_spire.tooltip1", SunSpirePlacement.RADIUS,
                (int) SunSpirePlacement.BUILDING_DAMAGE, (int) SunSpirePlacement.UNIT_DAMAGE).getVisualOrderText(),
            Component.translatable("buildings.reignofnether.sun_spire.tooltip2").getVisualOrderText());
    }
}
