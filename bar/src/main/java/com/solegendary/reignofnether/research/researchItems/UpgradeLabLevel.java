package com.solegendary.reignofnether.research.researchItems;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingLevels;
import com.solegendary.reignofnether.building.BuildingStats;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProdDupeRule;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.building.production.StartProductionButton;
import com.solegendary.reignofnether.building.production.StopProductionButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Raises a lab one level in place (design/building_upgrades.md): L1 -> L2 (UPGRADE_LAB_L2) or L2 -> L3
 * (UPGRADE_LAB_L3). It sits in the lab's own queue, so the lab makes nothing while it rebuilds itself - the tempo
 * price of concentrating production in one building - and it is flow-paid and refundable like any other item.
 * On completion the lab changes into its taller level structure; the level is read back from that structure.
 */
public class UpgradeLabLevel extends ProductionItem {

    /** The level this order upgrades TO (2 or 3). */
    public final int toLevel;

    public UpgradeLabLevel(int toLevel) {
        super(BuildingLevels.upgradeCost(toLevel - 1), ProdDupeRule.DISALLOW_FOR_BUILDING);
        this.toLevel = toLevel;
        this.onComplete = (Level level, ProductionPlacement placement) -> {
            if (!level.isClientSide() && placement.getBuildingLevel() == toLevel - 1)
                BuildingLevels.levelUp(placement);
        };
    }

    public String getItemName() {
        return toLevel == 2 ? "Upgrade to Level 2" : "Upgrade to Level 3";
    }

    // a lab takes the order only from the level just below (no skipping, no repeat), and never while it still has the
    // other level's order queued - L3 waits until L2 has actually finished
    @Nullable
    @Override
    public String getProduceErrorMsg(ProductionPlacement pp) {
        if (pp.getBuilding().maxLevel < toLevel)
            return "hud.reignofnether.upgrade.max_level";
        if (pp.getBuildingLevel() != toLevel - 1)
            return "hud.reignofnether.upgrade.wrong_level";
        if (pp.decommissioning)
            return "hud.reignofnether.upgrade.decommissioning";
        return null;
    }

    private boolean hidden(ProductionPlacement pp) {
        return pp.getBuilding().maxLevel < toLevel || pp.getBuildingLevel() != toLevel - 1;
    }

    private ResourceLocation icon() {
        return ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/hud/level_up.png");
    }

    private ResourceLocation frame() {
        return ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID,
            toLevel >= 3 ? "textures/hud/icon_frame_diamond.png" : "textures/hud/icon_frame_bronze.png");
    }

    public StartProductionButton getStartButton(ProductionPlacement prodBuilding, Keybinding hotkey) {
        ResourceCost cost = defaultCost;
        List<FormattedCharSequence> tooltip = new ArrayList<>();
        tooltip.add(FormattedCharSequence.forward(I18n.get("research.reignofnether.upgrade_lab", toLevel), Style.EMPTY.withBold(true)));
        tooltip.add(ResourceCosts.getFormattedCost(cost));
        tooltip.add(ResourceCosts.getFormattedTime(cost));
        tooltip.add(FormattedCharSequence.forward("", Style.EMPTY));
        // what it does, from the one stats provider (current -> next level)
        for (String line : BuildingStats.upgradeLines(prodBuilding, toLevel))
            tooltip.add(FormattedCharSequence.forward(line, Style.EMPTY));
        tooltip.add(FormattedCharSequence.forward(I18n.get("research.reignofnether.upgrade_lab.pause"), Style.EMPTY));
        return new StartProductionButton(getItemName(), icon(), frame(), hotkey,
            () -> hidden(prodBuilding) || itemIsBeingProducedAt(prodBuilding),
            () -> !hidden(prodBuilding),   // L2 and L3 share the key: only the one for the next level may answer it
            tooltip,
            this
        );
    }

    public StopProductionButton getCancelButton(ProductionPlacement prodBuilding, boolean first) {
        return new StopProductionButton(getItemName(), icon(), frame(), prodBuilding, this, first);
    }
}
