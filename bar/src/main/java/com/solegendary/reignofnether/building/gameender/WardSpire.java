package com.solegendary.reignofnether.building.gameender;

import static com.solegendary.reignofnether.building.BuildingUtils.getAbsoluteBlockData;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.blocks.BlockClientEvents;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.addon.RangeIndicatorAddon;
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
 * The <b>Ward Spire</b> - the counter to every game ender (design/big_units.md 2.1), BAR's anti-nuke in this world: an
 * obsidian-and-amethyst needle that stores one interception. Every faction builds the same spire (one instance per
 * faction, like the economy buildings; the variant only names it), with Tier 2 and any constructor - the answer has
 * to be reachable by the player who scouted the threat, not only by one who is already teching.
 * <p>
 * Priced at ~12% of a game ender. Its range is drawn when selected, so a player can see what it covers.
 */
public class WardSpire extends Building implements RangeIndicatorAddon {

    public static final String buildingName = "Ward Spire";
    public static final String structureName = "ward_spire";
    public static final ResourceCost cost = ResourceCosts.WARD_SPIRE;

    public final String variant;

    public WardSpire(String variant) {
        super(structureName, cost, false);
        this.variant = variant;
        this.name = buildingName;
        this.portraitBlock = Blocks.CRYING_OBSIDIAN;
        this.icon = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/amethyst_cluster.png");
        this.buildTimeBlocks = 160;
        this.maxHealth = 600d;
        this.explodeChance = 0.2f;
        setActiveAddon(RangeIndicatorAddon.class, this, true);
    }

    @Override
    public BuildingPlacement createBuildingPlacement(Level level, BlockPos pos, Rotation rotation, String ownerName) {
        return new WardSpirePlacement(this, level, pos, rotation, ownerName,
            getAbsoluteBlockData(getRelativeBlockData(level), level, pos, rotation), false);
    }

    @Override
    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String label = key != null ? Component.translatable("buildings." + getFaction().getName() + "." + key.getNamespace() + "." + key.getPath()).getString() : buildingName;
        return new BuildingPlaceButton(label, icon, hotkey,
            () -> BuildingClientEvents.getBuildingToPlace() == this,
            () -> false,
            () -> ResearchClient.hasResearch(ProductionItems.RESEARCH_TIER_2) || ResearchClient.hasCheat("modifythephasevariance"),
            List.of(
                Component.translatable("buildings.reignofnether.ward_spire").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                ResourceCosts.getFormattedCost(cost),
                FormattedCharSequence.EMPTY,
                Component.translatable("buildings.reignofnether.ward_spire.tooltip1",
                    WardSpirePlacement.CHARGE_TICKS / 20, WardSpirePlacement.RANGE).getVisualOrderText(),
                Component.translatable("buildings.reignofnether.ward_spire.tooltip2").getVisualOrderText()
            ),
            this);
    }

    @Override
    public int getRange(BuildingPlacement placement) {
        return WardSpirePlacement.RANGE;
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
