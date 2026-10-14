package com.solegendary.reignofnether.research.researchItems;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.StewardServerEvents;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProdDupeRule;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.building.production.StartProductionButton;
import com.solegendary.reignofnether.building.production.StopProductionButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.research.ResearchClient;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The Steward: a capitol research after which the capitol trains a worker on its own whenever its owner holds fewer
 * than the tier's quota (counting workers already in training) and nothing else is queued there. Lovish's ask (Oct 14):
 * "being able to make additional workers with an upgrade so it doesn't need to be made over and over again". The tick
 * that does the work is {@link StewardServerEvents}; each capitol can be paused from its HUD toggle.
 */
public class ResearchSteward extends ProductionItem {

    public final static String itemName = "Steward";
    public final static ResourceCost cost = ResourceCosts.RESEARCH_STEWARD;
    private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/items/hopper.png");
    private static final ResourceLocation FRAME = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/hud/icon_frame_bronze.png");

    public ResearchSteward() {
        super(cost, ProdDupeRule.DISALLOW);
        this.onComplete = (Level level, ProductionPlacement placement) -> {
            if (level.isClientSide())
                ResearchClient.addResearch(placement.ownerName, ProductionItems.RESEARCH_STEWARD);
            else
                ResearchServerEvents.addResearch(placement.ownerName, ProductionItems.RESEARCH_STEWARD);
        };
    }

    public String getItemName() {
        return ResearchSteward.itemName;
    }

    public StartProductionButton getStartButton(ProductionPlacement prodBuilding, Keybinding hotkey) {
        return new StartProductionButton(ResearchSteward.itemName, ICON, FRAME, hotkey,
            () -> ProductionItems.RESEARCH_STEWARD.itemIsBeingProduced(prodBuilding.ownerName)
                || ResearchClient.hasResearch(ProductionItems.RESEARCH_STEWARD),
            () -> true,
            List.of(FormattedCharSequence.forward(I18n.get("research.reignofnether.steward"), Style.EMPTY.withBold(true)),
                ResourceCosts.getFormattedCost(cost),
                ResourceCosts.getFormattedTime(cost),
                FormattedCharSequence.forward("", Style.EMPTY),
                FormattedCharSequence.forward(I18n.get("research.reignofnether.steward.tooltip1"), Style.EMPTY),
                FormattedCharSequence.forward(I18n.get("research.reignofnether.steward.tooltip2",
                    StewardServerEvents.QUOTA_T1, StewardServerEvents.QUOTA_T2, StewardServerEvents.QUOTA_T3), Style.EMPTY),
                FormattedCharSequence.forward(I18n.get("research.reignofnether.steward.tooltip3"), Style.EMPTY)
            ),
            this
        );
    }

    public StopProductionButton getCancelButton(ProductionPlacement prodBuilding, boolean first) {
        return new StopProductionButton(ResearchSteward.itemName, ICON, FRAME, prodBuilding, this, first);
    }
}
