package com.solegendary.reignofnether.building.buildings.verdant;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.research.ResearchClient;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * Verdant Court T1 lab - the <b>Grove</b> (design/verdant_court_plan.md, slice 1): a training glade under a living arch
 * (grove.nbt, tools/lab_grove.py) that trains the Court's T1 army: Leafblades, Thornbows, Sentinel Treants, Moonwell Bearers, Hive Keepers and Owl Watchers.
 * Needs a finished Heartwood Hall, as the Barracks needs a Town Centre.
 */
public class Grove extends ProductionBuilding {

    public final static String buildingName = "Grove";
    public final static String structureName = "grove";
    public final static ResourceCost cost = ResourceCosts.GROVE;
    private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/flowering_azalea_top.png");

    public Grove() {
        super(structureName, cost, false);
        this.name = buildingName;
        this.portraitBlock = Blocks.FLOWERING_AZALEA;
        // the flowering azalea's crown: the Grove grows its units (the target block was a placeholder that showed up as a
        // bullseye on control group 4 and the selection strip in lovish's 23:07 clip)
        this.icon = ICON;

        this.startingBlockTypes.add(Blocks.MOSS_BLOCK);
        this.startingBlockTypes.add(Blocks.MOSSY_STONE_BRICKS);
        this.buildTimeBlocks = 287;   // the shipped grove's build time; the Stag Glade (tools/lab_grove.py) has 307 blocks

        this.explodeChance = 0.2f;
        this.maxHealth = 150d;

        this.productions.add(ProductionItems.LEAFBLADE, Keybindings.abilitySlot1);
        this.productions.add(ProductionItems.THORNBOW, Keybindings.abilitySlot2);
        this.productions.add(ProductionItems.SENTINEL_TREANT, Keybindings.abilitySlot3);
        // slice 2: the rest of the T1 roster
        this.productions.add(ProductionItems.MOONWELL_BEARER, Keybindings.abilitySlot4);
        this.productions.add(ProductionItems.HIVE_KEEPER, Keybindings.abilitySlot5);
        this.productions.add(ProductionItems.OWL_WATCHER, Keybindings.abilitySlot6);
        // the faction hero and its revive share a slot (the revive only shows while the hero is dead): design/hero_mode.md
        this.productions.add(ProductionItems.THORNHEART_DRUID, Keybindings.abilitySlot8);
        this.productions.add(ProductionItems.THORNHEART_DRUID_REVIVE, Keybindings.abilitySlot8);
        this.productions.add(ProductionItems.BURL_SLINGER, Keybindings.abilitySlot7);   // T1 siege lobber (design/siege_t1.md)
        this.productions.add(ProductionItems.THORNWEAVER, Keybindings.abilitySlot9);    // essence caster: Tier 1 (lovish, Oct 29)
        // building levels (design/building_upgrades.md): L1 -> L3 in place, on the ' key
        this.maxLevel = com.solegendary.reignofnether.building.BuildingLevels.MAX_LEVEL;
        this.productions.add(ProductionItems.UPGRADE_LAB_L2, Keybindings.hotkey12);
        this.productions.add(ProductionItems.UPGRADE_LAB_L3, Keybindings.hotkey12);
    }

    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String name = key != null ? Component.translatable("buildings." + getFaction().getName() + "." + key.getNamespace() + "." + key.getPath()).getString() : buildingName;
        return new BuildingPlaceButton(
                name,
                ICON,
                hotkey,
                () -> BuildingClientEvents.getBuildingToPlace() == Buildings.GROVE,
                () -> false,
                () -> BuildingClientEvents.hasFinishedBuilding(Buildings.HEARTWOOD_HALL) ||
                        ResearchClient.hasCheat("modifythephasevariance"),
                List.of(
                        Component.translatable("buildings.reignofnether.grove").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                        ResourceCosts.getFormattedCost(cost),
                        FormattedCharSequence.forward("", Style.EMPTY),
                        Component.translatable("buildings.reignofnether.grove.tooltip1").getVisualOrderText()
                ),
                this
        );
    }
}
