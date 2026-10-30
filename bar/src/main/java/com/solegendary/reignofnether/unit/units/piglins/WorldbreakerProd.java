package com.solegendary.reignofnether.unit.units.piglins;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.IUnitProductionItem;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.building.production.StartProductionButton;
import com.solegendary.reignofnether.building.production.StopProductionButton;
import com.solegendary.reignofnether.hud.buttons.UnitSpawnButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.ResearchClient;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.T4Cap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The Horde's Tier 4, trained at the Fortress after the Ascension research (hero-and-tier4-design.md, slice H5a). Refused
 * without the research ({@link #NEEDS_ASCENSION_MSG}) and while the player already has one alive or queued
 * ({@link T4Cap}). Starting it tells every player ({@link T4Cap#announceStarted}); the body announces its arrival.
 */
public class WorldbreakerProd extends ProductionItem implements IUnitProductionItem {

    public final static String itemName = WorldbreakerUnit.ITEM_NAME;
    public final static ResourceCost cost = ResourceCosts.WORLDBREAKER;
    public static final String NEEDS_ASCENSION_MSG = "hud.reignofnether.needs_ascension";

    public WorldbreakerProd() {
        super(cost);
        this.onComplete = (Level level, ProductionPlacement placement) -> {
            if (!level.isClientSide())
                placement.produceUnit((ServerLevel) level, EntityRegistrar.WORLDBREAKER_UNIT.get(), placement.ownerName, false);
        };
    }

    public String getItemName() {
        return itemName;
    }

    /** Needs the Ascension research; on the server also the one-alive cap (the client has no unit lists to count). */
    @Nullable
    @Override
    public String getProduceErrorMsg(ProductionPlacement pp) {
        if (pp.getLevel().isClientSide())
            return ResearchClient.hasResearch(ProductionItems.RESEARCH_ASCENSION) ? null : NEEDS_ASCENSION_MSG;
        if (!ResearchServerEvents.playerHasResearch(pp.ownerName, ProductionItems.RESEARCH_ASCENSION))
            return NEEDS_ASCENSION_MSG;
        return T4Cap.productionError(pp, this);
    }

    private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/brute.png");

    public UnitSpawnButton getPlaceButton() {
        return new UnitSpawnButton(itemName, ICON, List.of(
            Component.translatable("entity.reignofnether.worldbreaker_unit").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
            FormattedCharSequence.EMPTY,
            Component.translatable("entity.reignofnether.worldbreaker_unit.tooltip1").getVisualOrderText(),
            Component.translatable("entity.reignofnether.worldbreaker_unit.tooltip2").getVisualOrderText()));
    }

    public StartProductionButton getStartButton(ProductionPlacement prodBuilding, Keybinding hotkey) {
        return new StartProductionButton(itemName, ICON, hotkey,
            () -> false,
            () -> canProduce(prodBuilding),
            List.of(
                Component.translatable("entity.reignofnether.worldbreaker_unit").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                ResourceCosts.getFormattedCost(cost),
                ResourceCosts.getFormattedPopAndTime(cost),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("entity.reignofnether.worldbreaker_unit.tooltip1").getVisualOrderText(),
                Component.translatable("entity.reignofnether.worldbreaker_unit.tooltip2").getVisualOrderText()
            ),
            this);
    }

    public StopProductionButton getCancelButton(ProductionPlacement prodBuilding, boolean first) {
        return new StopProductionButton(itemName, ICON, prodBuilding, this, first);
    }
}
