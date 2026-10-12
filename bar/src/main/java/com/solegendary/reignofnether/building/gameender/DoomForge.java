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
 * Ironhide Horde game ender - the <b>Doom Forge</b> (design/big_units.md 2.5): a blackstone forge-ziggurat with a
 * magma heart and four bellows chimneys (doom_forge.nbt, tools/gen_structures.py) that smelts a star and drops it on the
 * enemy's capitol - a crater that burns on after the impact. It is stoked with metal: every stage burns
 * {@link DoomForgePlacement#STOKE_METAL} up front and waits while the owner can't pay, so a raid on the Horde's
 * extractors slows it as surely as one on the forge. Needs the Fortress, Tier 3 and a Bonewright.
 */
public class DoomForge extends GameEnder {

    public static final String buildingName = "Doom Forge";
    public static final String structureName = "doom_forge";
    public static final ResourceCost cost = ResourceCosts.DOOM_FORGE;

    public DoomForge() {
        super(structureName, buildingName, cost, Blocks.BLAST_FURNACE, "textures/block/magma.png", () -> Buildings.FORTRESS);
        this.startingBlockTypes.add(Blocks.POLISHED_BLACKSTONE);
        this.startingBlockTypes.add(Blocks.BLACKSTONE);
    }

    @Override
    public BuildingPlacement createBuildingPlacement(Level level, BlockPos pos, Rotation rotation, String ownerName) {
        return new DoomForgePlacement(this, level, pos, rotation, ownerName,
            getAbsoluteBlockData(getRelativeBlockData(level), level, pos, rotation), false);
    }

    @Override
    protected List<FormattedCharSequence> strikeTooltip() {
        return List.of(
            Component.translatable("buildings.reignofnether.doom_forge.tooltip1", DoomForgePlacement.RADIUS, (int) DoomForgePlacement.BUILDING_DAMAGE, (int) DoomForgePlacement.UNIT_DAMAGE, DoomForgePlacement.CRATER_TICKS / 20).getVisualOrderText(),
            Component.translatable("buildings.reignofnether.doom_forge.tooltip2", DoomForgePlacement.STOKE_METAL).getVisualOrderText());
    }
}
