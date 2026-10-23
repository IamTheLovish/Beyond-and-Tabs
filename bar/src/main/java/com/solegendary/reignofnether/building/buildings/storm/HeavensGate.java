package com.solegendary.reignofnether.building.buildings.storm;

import com.solegendary.reignofnether.ReignOfNether;
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
 * Stormcallers T3 lab - <b>Heaven's Gate</b> (design/stormcallers_plan.md, slice 4): a great jade-roofed gate of
 * red-lacquer pillars on a stone plinth, gold ridge ends and a sun disc over the arch, lantern posts at the four
 * corners and a star chart of green terracotta under the gate (heavens_gate.nbt, tools/gen_structures.py). It raises
 * the faction's experimental, the Celestial Dragon. Like the Castle, Stronghold, Fortress, Heart of the Wild and
 * Abyssal Shipyard it needs Tier 3 research (at the Jade Pagoda), the faction's T2 lab (the Celestial Observatory) and
 * a T2 constructor - the Ley Master - among the selected builders; the server enforces the last one too
 * ({@code T2Workers.isT3Lab} / {@code buildersMayPlace}).
 * <p>
 * The build button is the client-side Tier 3 gate; the server checks Tier 3 again whenever this lab starts its
 * experimental ({@link #tier3Error}), so a gate that exists without the research still cannot raise one.
 */
public class HeavensGate extends ProductionBuilding {

    public final static String buildingName = "Heaven's Gate";
    public final static String structureName = "heavens_gate";
    public final static ResourceCost cost = ResourceCosts.HEAVENS_GATE;
    public final static String NEEDS_TIER_3 = "hud.reignofnether.needs_tier_3";
    static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/buildings/heavens_gate.png");

    public HeavensGate() {
        super(structureName, cost, false);
        this.name = buildingName;
        this.portraitBlock = Blocks.GOLD_BLOCK;
        this.icon = ICON;

        this.startingBlockTypes.add(Blocks.POLISHED_ANDESITE);
        this.startingBlockTypes.add(Blocks.STONE_BRICKS);

        this.buildTimeModifier = 0.5f;   // as the other T3 labs
        this.maxHealth = 800d;
        // the dragon is 2.4 blocks wide: it rises outside the gate, three blocks clear of the walls, like the Leviathan
        this.spawnRadiusOffset = 3f;

        this.productions.add(ProductionItems.CELESTIAL_DRAGON, Keybindings.abilitySlot4);   // Stormcallers T3 experimental
        this.productions.add(ProductionItems.STORM_T3B, Keybindings.abilitySlot5);   // second T3 (placeholder body), capped one alive each (T3Cap)
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
            () -> BuildingClientEvents.getBuildingToPlace() == Buildings.HEAVENS_GATE,
            () -> false,
            () -> (BuildingClientEvents.hasFinishedBuilding(Buildings.CELESTIAL_OBSERVATORY)
                    && ResearchClient.hasResearch(ProductionItems.RESEARCH_TIER_3)
                    && com.solegendary.reignofnether.unit.T2Workers.selectedHasT2Worker())   // only the Ley Master can raise the gate
                || ResearchClient.hasCheat("modifythephasevariance"),
            List.of(
                Component.translatable("buildings.reignofnether.heavens_gate").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                ResourceCosts.getFormattedCost(cost),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.heavens_gate.tooltip1").getVisualOrderText(),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.heavens_gate.tooltip2").getVisualOrderText()
            ),
            this
        );
    }
}
