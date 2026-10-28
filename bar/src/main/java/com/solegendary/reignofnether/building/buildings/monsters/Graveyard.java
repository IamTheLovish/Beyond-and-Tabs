package com.solegendary.reignofnether.building.buildings.monsters;

import com.solegendary.reignofnether.ReignOfNether;
import static com.solegendary.reignofnether.building.BuildingUtils.getAbsoluteBlockData;

import com.solegendary.reignofnether.ability.abilities.SetGraveyardReleaseOff;
import com.solegendary.reignofnether.ability.abilities.SetGraveyardReleaseOn;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.GraveyardPlacement;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.research.ResearchClient;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

import java.util.List;

public class Graveyard extends ProductionBuilding {

    public final static String buildingName = "Graveyard";
    public final static String structureName = "graveyard";
    public final static String upgradedStructureName = "overflowing_graveyard";
    public final static ResourceCost cost = ResourceCosts.GRAVEYARD;

    public final static int OVERFLOW_AMOUNT = 10;
    public final static int OVERFLOW_AMOUNT_UPGRADED = 20;

    public Graveyard() {
        super(structureName, cost, false);
        this.name = buildingName;
        this.portraitBlock = Blocks.MOSSY_STONE_BRICKS;
        this.icon = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/buildings/graveyard.png");   // tools/gen_hud_icons.py

        this.startingBlockTypes.add(Blocks.DEEPSLATE_BRICKS);

        this.maxHealth = 120d;
        this.buildTimeBlocks = 99;   // RoN's 99-block graveyard's build time; the Ossuary Yard's art (tools/lab_graveyard.py) is 217 blocks

        this.abilities.add(new SetGraveyardReleaseOff(), Keybindings.abilitySlot9);
        this.abilities.add(new SetGraveyardReleaseOn(), Keybindings.abilitySlot9);

        this.productions.add(ProductionItems.ZOMBIE, Keybindings.abilitySlot1);
        this.productions.add(ProductionItems.HUSK, Keybindings.abilitySlot1);
        this.productions.add(ProductionItems.DROWNED, Keybindings.abilitySlot2);
        this.productions.add(ProductionItems.SKELETON, Keybindings.abilitySlot3);
        this.productions.add(ProductionItems.STRAY, Keybindings.abilitySlot3);
        this.productions.add(ProductionItems.BOGGED, Keybindings.abilitySlot4);
        this.productions.add(ProductionItems.RESEARCH_OVERFLOWING_GRAVEYARD, Keybindings.abilitySlot5);
        // the faction hero and its revive share a slot (the revive only shows while the hero is dead): design/hero_mode.md
        this.productions.add(ProductionItems.GRAVECALLER, Keybindings.abilitySlot8);
        this.productions.add(ProductionItems.GRAVECALLER_REVIVE, Keybindings.abilitySlot8);
        // building levels (design/building_upgrades.md): L1 -> L3 in place, on the ' key
        this.maxLevel = com.solegendary.reignofnether.building.BuildingLevels.MAX_LEVEL;
        this.productions.add(ProductionItems.UPGRADE_LAB_L2, Keybindings.hotkey12);
        this.productions.add(ProductionItems.UPGRADE_LAB_L3, Keybindings.hotkey12);
    }

    @Override
    public String getUpgradedName(BuildingPlacement placement) {
        return Component.translatable("buildings.reignofnether.graveyard.upgraded").getString();
    }

    @Override
    public BuildingPlacement createBuildingPlacement(Level level, BlockPos pos, Rotation rotation, String ownerName) {
        return new GraveyardPlacement(this, level, pos, rotation, ownerName, getAbsoluteBlockData(getRelativeBlockData(level), level, pos, rotation), false);
    }


    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String name = key != null ? Component.translatable("buildings." + getFaction().getName() + "." + key.getNamespace() + "." + key.getPath()).getString() : buildingName;
        return new BuildingPlaceButton(
            name,
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/mossy_stone_bricks.png"),
            hotkey,
            () -> BuildingClientEvents.getBuildingToPlace() == Buildings.GRAVEYARD,
            () -> false,
            () -> BuildingClientEvents.hasFinishedBuilding(Buildings.MAUSOLEUM) ||
                    ResearchClient.hasCheat("modifythephasevariance"),
            List.of(
                Component.translatable("buildings.reignofnether.graveyard").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                ResourceCosts.getFormattedCost(cost),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.graveyard.tooltip1").getVisualOrderText()
            ),
            this
        );
    }

    // building levels (design/building_upgrades.md): L2 and L3 are drawn on the Overflowing Graveyard, so a levelled
    // graveyard is overflowing too. The saved / synced code: 0 plain, 1 overflowing, 2 = L2, 3 = L3.
    @Override
    public int getLevelFromUpgradeCode(int upgradeCode) {
        return upgradeCode >= 2 ? Math.min(maxLevel, upgradeCode) : 1;
    }

    @Override
    public int getUpgradeLevel(BuildingPlacement placement) {
        int level = placement.getBuildingLevel();
        if (level > 1)
            return level;
        for (BuildingBlock block : placement.getBlocks())
            if (block.getBlockState().getBlock() == Blocks.DEEPSLATE_TILES ||
                block.getBlockState().getBlock() == Blocks.CHISELED_DEEPSLATE) {
                return 1;
            }
        return 0;
    }

    @Override
    public String getUpgradedStructureName(int upgradeLevel) {
        return upgradeLevel > 0 ? upgradedStructureName : structureName;
    }
}
