package com.solegendary.reignofnether.building.buildings.brass;

import static com.solegendary.reignofnether.building.BuildingUtils.getAbsoluteBlockData;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.blocks.BlockClientEvents;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.addon.RangeIndicatorAddon;
import com.solegendary.reignofnether.building.buildings.placements.RailBatteryPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.research.ResearchClient;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.util.MiscUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

import java.util.List;

/**
 * Brassworks T2 defence - the <b>Rail Battery</b> (design/brassworks_plan.md, slice 3): a brass rail cannon on a
 * blackstone emplacement (rail_battery.nbt, tools/gen_structures.py). It picks the nearest enemy within
 * {@link RailBatteryPlacement#RANGE} blocks, locks a line through it and charges for
 * {@link RailBatteryPlacement#CHARGE_TICKS} ticks with the line drawn on the ground (a telegraph anyone on it can step
 * off), then fires the rail down the whole line: {@link RailBatteryPlacement#DAMAGE} to every enemy on it. Long reach
 * and a punishing shot against a packed column; a dancing raider walks out of it.
 * <p>
 * Built by Tinkers and Engineer-Captains; the button needs Tier 2 and a finished Machine Shop, and the battery stays
 * dormant on the server while its owner lacks Tier 2, like the Storm Oak. Priced like the Storm Oak (the other T2
 * defence that fights alone): more reach and a line hit, but a 1.5 s tell and no group-seeking.
 */
public class RailBattery extends Building implements RangeIndicatorAddon {

    public final static String buildingName = "Rail Battery";
    public final static String structureName = "rail_battery";
    public final static ResourceCost cost = ResourceCosts.RAIL_BATTERY;

    public RailBattery() {
        super(structureName, cost, false);
        this.name = buildingName;
        this.portraitBlock = Blocks.WAXED_CUT_COPPER;
        this.icon = ResourceLocation.fromNamespaceAndPath(com.solegendary.reignofnether.ReignOfNether.MOD_ID, "textures/icons/buildings/rail_battery.png");   // tools/gen_hud_icons.py

        this.startingBlockTypes.add(Blocks.POLISHED_BLACKSTONE);
        this.startingBlockTypes.add(Blocks.DEEPSLATE_BRICKS);

        this.buildTimeModifier = 0.8f;
        this.explodeChance = 0.2f;
        this.maxHealth = 320d;

        setActiveAddon(RangeIndicatorAddon.class, this, true);
    }

    @Override
    public BuildingPlacement createBuildingPlacement(Level level, BlockPos pos, Rotation rotation, String ownerName) {
        return new RailBatteryPlacement(this, level, pos, rotation, ownerName,
            getAbsoluteBlockData(getRelativeBlockData(level), level, pos, rotation), false);
    }

    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String name = key != null ? Component.translatable("buildings." + getFaction().getName() + "." + key.getNamespace() + "." + key.getPath()).getString() : buildingName;
        return new BuildingPlaceButton(
            name,
            this.icon,
            hotkey,
            () -> BuildingClientEvents.getBuildingToPlace() == Buildings.RAIL_BATTERY,
            () -> false,
            () -> (BuildingClientEvents.hasFinishedBuilding(Buildings.MACHINE_SHOP) && ResearchClient.hasResearch(ProductionItems.RESEARCH_TIER_2)) ||
                    ResearchClient.hasCheat("modifythephasevariance"),
            List.of(
                Component.translatable("buildings.reignofnether.rail_battery").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                ResourceCosts.getFormattedCost(cost),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.rail_battery.tooltip1",
                    (int) RailBatteryPlacement.DAMAGE, RailBatteryPlacement.RANGE).getVisualOrderText(),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.rail_battery.tooltip2").getVisualOrderText()
            ),
            this
        );
    }

    @Override
    public int getRange(BuildingPlacement placement) {
        return RailBatteryPlacement.RANGE;
    }

    @Override
    public void updateHighlightBps(BuildingPlacement placement) {
        if (!placement.level.isClientSide())
            return;
        placement.getDataStorage().getData(RangeIndicatorAddon.HIGHLIGHT_BPS_CACHE).clear();
        placement.getDataStorage().getData(RangeIndicatorAddon.HIGHLIGHT_BPS_CACHE).addAll(MiscUtil.getRangeIndicatorCircleBlocks(placement.centrePos,
                getRange(placement) - BlockClientEvents.VISIBLE_BORDER_ADJ, placement.level));
    }

    @Override
    public boolean showOnlyWhenSelected(BuildingPlacement placement) {
        return true;
    }
}
