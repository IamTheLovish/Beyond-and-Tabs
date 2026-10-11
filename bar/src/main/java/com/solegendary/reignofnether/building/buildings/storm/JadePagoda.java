package com.solegendary.reignofnether.building.buildings.storm;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * Stormcallers capitol - the <b>Jade Pagoda</b> (design/stormcallers_plan.md, slice 1): a three-tier pagoda on a stone
 * plinth - red-terracotta columns, jade roof tiers (waxed oxidised cut copper, so they never weather mid-match) with
 * gold ridge ends, standing lanterns and red banners (jade_pagoda.nbt, tools/gen_structures.py). Trains Geomancers and
 * Paper Cranes and researches Tier 2 and Tier 3 in the same slots as the other capitols: Tier 2 opens the Celestial
 * Observatory (the T2 lab, slice 3) and the extractor refit; Tier 3 opens Heaven's Gate (the T3 lab, slice 4) and its
 * Celestial Dragon. Overcharge is toggled from the HUD chip under the energy bar and its keybind
 * (ManaClientEvents), which every Stormcaller player has whatever is selected.
 */
public class JadePagoda extends ProductionBuilding {

    public final static String buildingName = "Jade Pagoda";
    public final static String structureName = "jade_pagoda";
    public final static ResourceCost cost = ResourceCosts.JADE_PAGODA;
    static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/buildings/jade_pagoda.png");   // tools/gen_hud_icons.py

    public JadePagoda() {
        super(structureName, cost, true);
        this.name = buildingName;
        this.portraitBlock = Blocks.WAXED_OXIDIZED_CUT_COPPER;
        this.icon = ICON;

        this.maxHealth = 400d;
        this.buildTimeModifier = 0.3f; // about a minute with 3 workers, like the other capitols
        this.canAcceptResources = true;

        this.startingBlockTypes.add(Blocks.POLISHED_ANDESITE);
        this.startingBlockTypes.add(Blocks.STONE_BRICKS);

        this.productions.add(ProductionItems.GEOMANCER, Keybindings.abilitySlot1);
        this.productions.add(ProductionItems.PAPER_CRANE, Keybindings.abilitySlot2);
        this.productions.add(ProductionItems.RESEARCH_TIER_2, Keybindings.abilitySlot9);   // the Observatory and the extractor refit
        this.productions.add(ProductionItems.RESEARCH_TIER_3, Keybindings.abilitySlot10);  // Heaven's Gate (slice 4)
    }

    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String name = key != null ? Component.translatable("buildings." + getFaction().getName() + "." + key.getNamespace() + "." + key.getPath()).getString() : buildingName;
        return new BuildingPlaceButton(name,
            ICON,
            hotkey,
            () -> BuildingClientEvents.getBuildingToPlace() == Buildings.JADE_PAGODA,
            () -> false,
            () -> true,
            List.of(Component.translatable("buildings.reignofnether.jade_pagoda").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                ResourceCosts.getFormattedCost(cost),
                ResourceCosts.getFormattedPop(cost),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.jade_pagoda.tooltip1").getVisualOrderText(),
                Component.translatable("buildings.reignofnether.jade_pagoda.tooltip2").getVisualOrderText()
            ),
            this
        );
    }
}
