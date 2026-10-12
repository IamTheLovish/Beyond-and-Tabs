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
 * Gravebound game ender - the <b>Necropolis</b> (design/big_units.md 2.4): a black-stone and bone mausoleum with
 * soul-fire braziers (necropolis.nbt, tools/gen_structures.py). At full charge it raises an undead host inside the
 * enemy base - more for every wreck lying near the target. Needs the Stronghold, Tier 3 and an Embalmer.
 */
public class Necropolis extends GameEnder {

    public static final String buildingName = "Necropolis";
    public static final String structureName = "necropolis";
    public static final ResourceCost cost = ResourceCosts.NECROPOLIS;

    public Necropolis() {
        super(structureName, buildingName, cost, Blocks.BONE_BLOCK, "textures/block/soul_lantern.png", () -> Buildings.STRONGHOLD);
        this.startingBlockTypes.add(Blocks.POLISHED_BLACKSTONE_BRICKS);
        this.startingBlockTypes.add(Blocks.BLACKSTONE);
    }

    @Override
    public BuildingPlacement createBuildingPlacement(Level level, BlockPos pos, Rotation rotation, String ownerName) {
        return new NecropolisPlacement(this, level, pos, rotation, ownerName,
            getAbsoluteBlockData(getRelativeBlockData(level), level, pos, rotation), false);
    }

    @Override
    protected List<FormattedCharSequence> strikeTooltip() {
        return List.of(
            Component.translatable("buildings.reignofnether.necropolis.tooltip1", NecropolisPlacement.BASE_RISEN,
                NecropolisPlacement.LIFETIME_TICKS / 20).getVisualOrderText(),
            Component.translatable("buildings.reignofnether.necropolis.tooltip2", NecropolisPlacement.MAX_WRECK_BONUS).getVisualOrderText());
    }
}
