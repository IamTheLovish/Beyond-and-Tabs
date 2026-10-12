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
 * Stormcallers game ender - the <b>Heaven's Bell</b> (design/big_units.md 2.8): a jade bell tower (heavens_bell.nbt)
 * whose toll calls a storm down on the enemy's capitol - fifteen seconds of lightning, units first. Every stage costs
 * {@link HeavensBellPlacement#STAGE_ENERGY} energy up front and waits while the owner can't pay: kill the wind and the
 * bell falls silent. Needs Heaven's Gate, Tier 3 and a Ley Master.
 */
public class HeavensBell extends GameEnder {

    public static final String buildingName = "Heaven's Bell";
    public static final String structureName = "heavens_bell";
    public static final ResourceCost cost = ResourceCosts.HEAVENS_BELL;

    public HeavensBell() {
        super(structureName, buildingName, cost, Blocks.BELL, "textures/item/bell.png", () -> Buildings.HEAVENS_GATE);
        this.startingBlockTypes.add(Blocks.POLISHED_ANDESITE);
        this.startingBlockTypes.add(Blocks.STONE_BRICKS);
    }

    @Override
    public BuildingPlacement createBuildingPlacement(Level level, BlockPos pos, Rotation rotation, String ownerName) {
        return new HeavensBellPlacement(this, level, pos, rotation, ownerName,
            getAbsoluteBlockData(getRelativeBlockData(level), level, pos, rotation), false);
    }

    @Override
    protected List<FormattedCharSequence> strikeTooltip() {
        return List.of(
            Component.translatable("buildings.reignofnether.heavens_bell.tooltip1", HeavensBellPlacement.STORM_TICKS / 20, HeavensBellPlacement.RADIUS).getVisualOrderText(),
            Component.translatable("buildings.reignofnether.heavens_bell.tooltip2", HeavensBellPlacement.STAGE_ENERGY).getVisualOrderText());
    }
}
