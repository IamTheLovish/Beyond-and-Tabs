package com.solegendary.reignofnether.building.buildings.brass;

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
 * Brassworks T1 lab - the <b>Machine Shop</b> (design/brassworks_plan.md, slice 1): an open-sided workshop under a brass
 * roof - deepslate posts, a bench of smithing tables and anvils, a crucible, chain hoists (machine_shop.nbt,
 * tools/gen_structures.py) - that trains the faction's whole T1: Steam Runners, Rifle Squads, Iron Wardens, Flak
 * Carts, Mortar Crawlers, Shield Projectors and Sappers. Needs a finished Foundry Hall, as the Barracks needs a Town
 * Centre.
 */
public class MachineShop extends ProductionBuilding {

    public final static String buildingName = "Machine Shop";
    public final static String structureName = "machine_shop";
    public final static ResourceCost cost = ResourceCosts.MACHINE_SHOP;
    static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/buildings/machine_shop.png");   // tools/gen_hud_icons.py

    public MachineShop() {
        super(structureName, cost, false);
        this.name = buildingName;
        this.portraitBlock = Blocks.ANVIL;
        this.icon = ICON;

        this.startingBlockTypes.add(Blocks.POLISHED_BLACKSTONE);
        this.startingBlockTypes.add(Blocks.DEEPSLATE_BRICKS);

        this.explodeChance = 0.2f;
        this.maxHealth = 150d;

        this.productions.add(ProductionItems.STEAM_RUNNER, Keybindings.abilitySlot1);
        this.productions.add(ProductionItems.RIFLE_SQUAD, Keybindings.abilitySlot2);
        this.productions.add(ProductionItems.IRON_WARDEN, Keybindings.abilitySlot3);
        this.productions.add(ProductionItems.FLAK_CART, Keybindings.abilitySlot4);
        this.productions.add(ProductionItems.MORTAR_CRAWLER, Keybindings.abilitySlot5);
        this.productions.add(ProductionItems.SHIELD_PROJECTOR, Keybindings.abilitySlot6);
        this.productions.add(ProductionItems.SAPPER, Keybindings.abilitySlot7);
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
                () -> BuildingClientEvents.getBuildingToPlace() == Buildings.MACHINE_SHOP,
                () -> false,
                () -> BuildingClientEvents.hasFinishedBuilding(Buildings.FOUNDRY_HALL) ||
                        ResearchClient.hasCheat("modifythephasevariance"),
                List.of(
                        Component.translatable("buildings.reignofnether.machine_shop").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                        ResourceCosts.getFormattedCost(cost),
                        FormattedCharSequence.forward("", Style.EMPTY),
                        Component.translatable("buildings.reignofnether.machine_shop.tooltip1").getVisualOrderText()
                ),
                this
        );
    }
}
