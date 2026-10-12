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
 * Tidewrought game ender - the <b>Maelstrom Engine</b> (design/big_units.md 2.7): a prismarine-and-copper pump with a
 * conduit heart (maelstrom_engine.nbt) that opens a whirlpool in the enemy's base. It drinks from a ring of
 * {@link MaelstromEnginePlacement#FLOOD_POOLS} tidepools it floods round itself, and charges only as fast as that ring is
 * full: drain the pools (any constructor's reclaim order) and it slows, drain them all and it stops. Needs the Abyssal
 * Shipyard, Tier 3 and a Harbourmaster.
 */
public class MaelstromEngine extends GameEnder {

    public static final String buildingName = "Maelstrom Engine";
    public static final String structureName = "maelstrom_engine";
    public static final ResourceCost cost = ResourceCosts.MAELSTROM_ENGINE;

    public MaelstromEngine() {
        super(structureName, buildingName, cost, Blocks.PRISMARINE_BRICKS, "textures/block/conduit.png", () -> Buildings.ABYSSAL_SHIPYARD);
        this.startingBlockTypes.add(Blocks.PRISMARINE_BRICKS);
        this.startingBlockTypes.add(Blocks.DARK_PRISMARINE);
    }

    @Override
    public BuildingPlacement createBuildingPlacement(Level level, BlockPos pos, Rotation rotation, String ownerName) {
        return new MaelstromEnginePlacement(this, level, pos, rotation, ownerName,
            getAbsoluteBlockData(getRelativeBlockData(level), level, pos, rotation), false);
    }

    @Override
    protected List<FormattedCharSequence> strikeTooltip() {
        return List.of(
            Component.translatable("buildings.reignofnether.maelstrom_engine.tooltip1", MaelstromEnginePlacement.RADIUS, MaelstromEnginePlacement.WHIRL_TICKS / 20).getVisualOrderText(),
            Component.translatable("buildings.reignofnether.maelstrom_engine.tooltip2", MaelstromEnginePlacement.FLOOD_POOLS).getVisualOrderText());
    }
}
