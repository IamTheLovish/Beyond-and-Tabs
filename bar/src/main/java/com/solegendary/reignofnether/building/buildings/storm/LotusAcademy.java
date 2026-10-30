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
 * Stormcallers T1 lab - the <b>Lotus Academy</b> (design/stormcallers_plan.md, slice 1): the Lotus Court, a jade-roofed
 * hall over a court with a torii and a lotus sculpture (lotus_academy.nbt, tools/lab_lotus_academy.py) that trains the faction's
 * T1 army: Blade Dancers, Fire Adepts, Jade Guards and Wind Monks, and (slice 2) Frost Adepts, Ink Scribes and Rocket
 * Batteries. Needs a finished Jade Pagoda, as the Barracks needs a
 * Town Centre.
 */
public class LotusAcademy extends ProductionBuilding {

    public final static String buildingName = "Lotus Academy";
    public final static String structureName = "lotus_academy";
    public final static ResourceCost cost = ResourceCosts.LOTUS_ACADEMY;
    // a drawn courtyard-and-moon-gate (tools/gen_hud_icons.py): the plain cherry-planks face it shipped with was the
    // "pink block" tile on the Geomancer build menu and the rows of pink squares when several academies were selected
    static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/buildings/lotus_academy.png");

    public LotusAcademy() {
        super(structureName, cost, false);
        this.name = buildingName;
        this.portraitBlock = Blocks.RED_TERRACOTTA;
        this.icon = ICON;

        this.startingBlockTypes.add(Blocks.POLISHED_ANDESITE);
        this.startingBlockTypes.add(Blocks.STONE_BRICKS);
        this.buildTimeBlocks = 181;   // the old 181-block courtyard's build time; the Lotus Court's art (tools/lab_lotus_academy.py) has 320

        this.maxHealth = 150d;

        this.productions.add(ProductionItems.BLADE_DANCER, Keybindings.abilitySlot1);
        this.productions.add(ProductionItems.FIRE_ADEPT, Keybindings.abilitySlot2);
        this.productions.add(ProductionItems.JADE_GUARD, Keybindings.abilitySlot3);
        this.productions.add(ProductionItems.WIND_MONK, Keybindings.abilitySlot4);
        // slice 2: the rest of T1
        this.productions.add(ProductionItems.FROST_ADEPT, Keybindings.abilitySlot5);
        this.productions.add(ProductionItems.INK_SCRIBE, Keybindings.abilitySlot6);
        this.productions.add(ProductionItems.ROCKET_BATTERY, Keybindings.abilitySlot7);
        // the faction hero and its revive share a slot (the revive only shows while the hero is dead): design/hero_mode.md
        this.productions.add(ProductionItems.STORM_WITCH, Keybindings.abilitySlot8);
        this.productions.add(ProductionItems.STORM_WITCH_REVIVE, Keybindings.abilitySlot8);
        this.productions.add(ProductionItems.THUNDERSTONE_MONK, Keybindings.abilitySlot9);   // T1 siege lobber (design/siege_t1.md)
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
                () -> BuildingClientEvents.getBuildingToPlace() == Buildings.LOTUS_ACADEMY,
                () -> false,
                () -> BuildingClientEvents.hasFinishedBuilding(Buildings.JADE_PAGODA) ||
                        ResearchClient.hasCheat("modifythephasevariance"),
                List.of(
                        Component.translatable("buildings.reignofnether.lotus_academy").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                        ResourceCosts.getFormattedCost(cost),
                        FormattedCharSequence.forward("", Style.EMPTY),
                        Component.translatable("buildings.reignofnether.lotus_academy.tooltip1").getVisualOrderText()
                ),
                this
        );
    }
}
