package com.solegendary.reignofnether.building.buildings.tide;

import static com.solegendary.reignofnether.building.BuildingUtils.getAbsoluteBlockData;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.blocks.BlockClientEvents;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.addon.RangeIndicatorAddon;
import com.solegendary.reignofnether.building.buildings.placements.LighthouseBatteryPlacement;
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
 * Tidewrought T2 defence - the <b>Lighthouse Battery</b> (design/tidewrought_plan.md, slice 3): a striped prismarine
 * and sandstone tower with a sea-lantern lamp room under a waxed-copper cap (lighthouse_battery.nbt,
 * tools/gen_structures.py). It sees {@link LighthouseBatteryPlacement#SIGHT_RANGE} blocks, its turning beam strips
 * the cover from thicket-, fog- and cloak-hidden enemies it sweeps over (Concealment.revealFor, the Holy Bell's hook),
 * and a light swivel gun pecks at the nearest visible enemy (see {@link LighthouseBatteryPlacement}).
 * <p>
 * Built by Shipwrights and Harbourmasters; the button needs Tier 2 and a finished Drydock, and the tower stays dormant
 * on the server while its owner lacks Tier 2, like the Storm Oak. Balance: a scout-and-picket building rather than a
 * killer - a third of the Storm Oak's damage, so it costs a little less than the oak.
 */
public class LighthouseBattery extends Building implements RangeIndicatorAddon {

    public final static String buildingName = "Lighthouse Battery";
    public final static String structureName = "lighthouse_battery";
    public final static ResourceCost cost = ResourceCosts.LIGHTHOUSE_BATTERY;
    static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/sea_lantern.png");

    public LighthouseBattery() {
        super(structureName, cost, false);
        this.name = buildingName;
        this.portraitBlock = Blocks.SEA_LANTERN;
        this.icon = ICON;

        this.startingBlockTypes.add(Blocks.SMOOTH_SANDSTONE);
        this.startingBlockTypes.add(Blocks.PRISMARINE_BRICKS);

        this.buildTimeModifier = 0.8f;
        this.maxHealth = 300d;

        setActiveAddon(RangeIndicatorAddon.class, this, true);
    }

    @Override
    public BuildingPlacement createBuildingPlacement(Level level, BlockPos pos, Rotation rotation, String ownerName) {
        return new LighthouseBatteryPlacement(this, level, pos, rotation, ownerName,
            getAbsoluteBlockData(getRelativeBlockData(level), level, pos, rotation), false);
    }

    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String name = key != null ? Component.translatable("buildings." + getFaction().getName() + "." + key.getNamespace() + "." + key.getPath()).getString() : buildingName;
        return new BuildingPlaceButton(
            name,
            ICON,
            hotkey,
            () -> BuildingClientEvents.getBuildingToPlace() == Buildings.LIGHTHOUSE_BATTERY,
            () -> false,
            () -> (BuildingClientEvents.hasFinishedBuilding(Buildings.DRYDOCK) && ResearchClient.hasResearch(ProductionItems.RESEARCH_TIER_2)) ||
                    ResearchClient.hasCheat("modifythephasevariance"),
            List.of(
                Component.translatable("buildings.reignofnether.lighthouse_battery").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                ResourceCosts.getFormattedCost(cost),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.lighthouse_battery.tooltip1",
                    LighthouseBatteryPlacement.SIGHT_RANGE, LighthouseBatteryPlacement.BEAM_RANGE).getVisualOrderText(),
                Component.translatable("buildings.reignofnether.lighthouse_battery.tooltip2",
                    (int) LighthouseBatteryPlacement.DAMAGE, LighthouseBatteryPlacement.ATTACK_COOLDOWN_TICKS / 20,
                    LighthouseBatteryPlacement.ATTACK_RANGE).getVisualOrderText(),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.lighthouse_battery.tooltip3").getVisualOrderText()
            ),
            this
        );
    }

    @Override
    public int getRange(BuildingPlacement placement) {
        return LighthouseBatteryPlacement.ATTACK_RANGE;
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
