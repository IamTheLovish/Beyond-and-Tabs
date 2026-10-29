package com.solegendary.reignofnether.building.buildings.piglins;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;

public class PortalMilitary extends AbstractPortal {

    public final static String buildingName = "Military Portal";
    public final static String structureName = "portal_military";

    public final static ResourceCost cost = ResourceCosts.BASIC_PORTAL;

    public PortalMilitary() {
        super(structureName, cost);
        this.name = buildingName;
        this.portraitBlock = Blocks.RED_GLAZED_TERRACOTTA;
        this.icon = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/buildings/portal_military.png");   // tools/gen_hud_icons.py
        this.canSetRallyPoint = true;
        this.maxHealthBonusPerUpgradeLevel = 0;   // the level multiplier (BuildingLevels) grows its HP instead
        this.maxHealth = 100d;                    // what 75 + one 25 bonus gave the refitted portal before
        this.buildTimeBlocks = 50;                // RoN's 50-block portal's build time (the Ember Gate's art has 132)
        productions.add(ProductionItems.BRUTE, Keybindings.abilitySlot1);
        productions.add(ProductionItems.HEADHUNTER, Keybindings.abilitySlot2);
        productions.add(ProductionItems.MARAUDER, Keybindings.abilitySlot3);
        productions.add(ProductionItems.HOGLIN, Keybindings.abilitySlot4);
        // the faction hero and its revive share a slot (the revive only shows while the hero is dead): design/hero_mode.md
        productions.add(ProductionItems.HORDE_WARCHIEF, Keybindings.abilitySlot8);
        productions.add(ProductionItems.HORDE_WARCHIEF_REVIVE, Keybindings.abilitySlot8);
        productions.add(ProductionItems.BOULDER_CHUCKER, Keybindings.abilitySlot5);   // T1 siege lobber (design/siege_t1.md)
        // building levels (design/building_upgrades.md): L1 -> L3 in place, on the ' key
        maxLevel = com.solegendary.reignofnether.building.BuildingLevels.MAX_LEVEL;
        productions.add(ProductionItems.UPGRADE_LAB_L2, Keybindings.hotkey12);
        productions.add(ProductionItems.UPGRADE_LAB_L3, Keybindings.hotkey12);
        // (Blaze, Wither Skeleton, Magma Cube and Ghast are T2 now - Flame Sanctuary, the Horde T2 lab)
    }

    // 1 marks "a refitted portal" in saves and syncs (the load path refits it); building levels ride on top of that:
    // the code is the level itself, 1..3 (design/building_upgrades.md)
    @Override
    public int getUpgradeLevel(BuildingPlacement placement) {
        return Math.max(1, placement.getBuildingLevel());
    }

    @Override
    public int getLevelFromUpgradeCode(int upgradeCode) {
        return Math.max(1, Math.min(maxLevel, upgradeCode));
    }
}