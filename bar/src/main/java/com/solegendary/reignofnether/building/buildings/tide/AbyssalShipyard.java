package com.solegendary.reignofnether.building.buildings.tide;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.research.ResearchClient;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.Blocks;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Tidewrought T3 lab - the <b>Abyssal Shipyard</b> (design/tidewrought_plan.md, slice 4): a drydock of dark prismarine
 * under the ribcage of some drowned leviathan - bone ribs arching over a sea-lantern-lit slip, a skull at its head, a
 * waxed-copper crane and dead coral along the walls (abyssal_shipyard.nbt, tools/gen_structures.py). It raises the
 * faction's experimental, the Leviathan. Like the Castle, Stronghold, Fortress and Heart of the Wild it needs Tier 3
 * research (at the Wreck Harbour), the faction's T2 lab (the Drydock) and a T2 constructor - the Harbourmaster - among
 * the selected builders; the server enforces the last one too ({@code T2Workers.isT3Lab} / {@code buildersMayPlace}).
 * <p>
 * The build button is the client-side Tier 3 gate; the server checks Tier 3 again whenever this lab starts its
 * experimental ({@link #tier3Error}), so a lab that exists without the research still cannot raise one.
 */
public class AbyssalShipyard extends ProductionBuilding {

    public final static String buildingName = "Abyssal Shipyard";
    public final static String structureName = "abyssal_shipyard";
    public final static ResourceCost cost = ResourceCosts.ABYSSAL_SHIPYARD;
    public final static String NEEDS_TIER_3 = "hud.reignofnether.needs_tier_3";
    static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/dark_prismarine.png");

    public AbyssalShipyard() {
        super(structureName, cost, false);
        this.name = buildingName;
        this.portraitBlock = Blocks.SEA_LANTERN;
        this.icon = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/sea_lantern.png");

        this.startingBlockTypes.add(Blocks.DARK_PRISMARINE);
        this.startingBlockTypes.add(Blocks.PRISMARINE_BRICKS);

        this.buildTimeModifier = 0.5f;   // as the other T3 labs
        this.maxHealth = 800d;
        // the Leviathan is 3.5 blocks wide: the default outdoor spawn (one block off the footprint's corner) would put
        // half its body inside the walls, so it walks out three blocks clear (ProductionPlacement.safeSpawnPoint still
        // checks the body fits there)
        this.spawnRadiusOffset = 3f;

        this.productions.add(ProductionItems.LEVIATHAN, Keybindings.abilitySlot4);   // Tidewrought T3 experimental
        this.productions.add(ProductionItems.TIDE_T3B, Keybindings.abilitySlot5);   // second T3 (placeholder body), capped one alive each (T3Cap)
    }

    /** Server and client: null if this lab's owner has Tier 3, else the message to show (the experimental's gate). */
    @Nullable
    public static String tier3Error(ProductionPlacement pp) {
        if (pp == null)
            return NEEDS_TIER_3;
        boolean has = pp.level.isClientSide()
            ? ResearchClient.hasResearch(ProductionItems.RESEARCH_TIER_3)
            : ResearchServerEvents.playerHasResearch(pp.ownerName, ProductionItems.RESEARCH_TIER_3);
        return has ? null : NEEDS_TIER_3;
    }

    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String name = key != null ? Component.translatable("buildings." + getFaction().getName() + "." + key.getNamespace() + "." + key.getPath()).getString() : buildingName;
        return new BuildingPlaceButton(
            name,
            ICON,
            hotkey,
            () -> BuildingClientEvents.getBuildingToPlace() == Buildings.ABYSSAL_SHIPYARD,
            () -> false,
            () -> (BuildingClientEvents.hasFinishedBuilding(Buildings.DRYDOCK)
                    && ResearchClient.hasResearch(ProductionItems.RESEARCH_TIER_3)
                    && com.solegendary.reignofnether.unit.T2Workers.selectedHasT2Worker())   // only the Harbourmaster can lay its keel
                || ResearchClient.hasCheat("modifythephasevariance"),
            List.of(
                Component.translatable("buildings.reignofnether.abyssal_shipyard").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                ResourceCosts.getFormattedCost(cost),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.abyssal_shipyard.tooltip1").getVisualOrderText(),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.abyssal_shipyard.tooltip2").getVisualOrderText()
            ),
            this
        );
    }
}
