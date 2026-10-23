package com.solegendary.reignofnether.research.researchItems;

import com.solegendary.reignofnether.ReignOfNether;
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

import javax.annotation.Nullable;
import java.util.List;

/**
 * Tier 4: Ascension (hero-and-tier4-design.md, slice H5a), researched at the Horde's Fortress (the T3 lab) once it is
 * finished. Unlocks the Worldbreaker, the Horde's Tier 4. Priced in config like every research ({@code ResourceCosts.RESEARCH_ASCENSION}).
 */
public class ResearchAscension extends ProductionItem {

    public final static String itemName = "Ascension";
    public final static ResourceCost cost = ResourceCosts.RESEARCH_ASCENSION;
    /** Lang key of the refusal when the lab is still a building site. */
    public static final String LAB_UNFINISHED_MSG = "hud.reignofnether.lab_unfinished";

    public ResearchAscension() {
        super(cost, ProdDupeRule.DISALLOW);
        this.onComplete = (Level level, ProductionPlacement placement) -> {
            if (!level.isClientSide())
                ResearchServerEvents.addResearch(placement.ownerName, ProductionItems.RESEARCH_ASCENSION);
        };
    }

    public String getItemName() {
        return ResearchAscension.itemName;
    }

    /** The T3 lab must be finished (a half-built Fortress cannot start the Tier 4 research). */
    @Nullable
    @Override
    public String getProduceErrorMsg(ProductionPlacement pp) {
        return pp.isBuilt ? null : LAB_UNFINISHED_MSG;
    }

    public StartProductionButton getStartButton(ProductionPlacement prodBuilding, Keybinding hotkey) {
        return new StartProductionButton(ResearchAscension.itemName,
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/items/book.png"),
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/hud/icon_frame_diamond.png"),
            hotkey,
            () -> ProductionItems.RESEARCH_ASCENSION.itemIsBeingProduced(prodBuilding.ownerName)
                || ResearchClient.hasResearch(ProductionItems.RESEARCH_ASCENSION),
            () -> canProduce(prodBuilding),
            List.of(FormattedCharSequence.forward(I18n.get("research.reignofnether.ascension"), Style.EMPTY.withBold(true)),
                ResourceCosts.getFormattedCost(cost),
                ResourceCosts.getFormattedTime(cost),
                FormattedCharSequence.forward("", Style.EMPTY),
                FormattedCharSequence.forward(I18n.get("research.reignofnether.ascension.tooltip1"), Style.EMPTY),
                FormattedCharSequence.forward(I18n.get("research.reignofnether.ascension.tooltip2"), Style.EMPTY)
            ),
            this
        );
    }

    public StopProductionButton getCancelButton(ProductionPlacement prodBuilding, boolean first) {
        return new StopProductionButton(ResearchAscension.itemName,
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/items/book.png"),
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/hud/icon_frame_diamond.png"),
            prodBuilding,
            this,
            first
        );
    }
}
