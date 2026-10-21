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
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * Brassworks capitol - the <b>Foundry Hall</b> (design/brassworks_plan.md, slice 1), drawn since Oct 19 as the Grand
 * Foundry (tools/capitol_foundry_hall.py, 15 x 22 x 15): a soot plinth and yard, a 9 x 9 hall the same on every side
 * (corner towers, furnace bays with lit blast furnaces and orange windows, dark-oak buttresses, gabled portals), a
 * dark-oak nave, three smokestacks, pipes everywhere, a crane, a copper clock tower with a great gear on each face under
 * a riveted dome, and four brass automatons guarding the yard. Trains Tinkers and Gyro Scouts and researches Tier 2 and Tier 3 in the same slots as the
 * other capitols: Tier 2 opens the extractor refit (and the Assembly Works, slice 2); Tier 3 the Great Forge (slice 4).
 */
public class FoundryHall extends ProductionBuilding {

    public final static String buildingName = "Foundry Hall";
    public final static String structureName = "foundry_hall";
    public final static ResourceCost cost = ResourceCosts.FOUNDRY_HALL;
    static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/buildings/foundry_hall.png");   // tools/gen_hud_icons.py

    public FoundryHall() {
        super(structureName, cost, true);
        this.name = buildingName;
        this.portraitBlock = Blocks.WAXED_CUT_COPPER;
        this.icon = ICON;

        this.maxHealth = 400d;
        this.buildTimeModifier = 0.3f; // about a minute with 3 workers, like the other capitols
        // the Grand Foundry (Oct 19, 1504 blocks) keeps the 428-block hall's build time
        this.buildTimeBlocks = 428;
        this.canAcceptResources = true;

        this.startingBlockTypes.add(Blocks.POLISHED_BLACKSTONE);
        this.startingBlockTypes.add(Blocks.DEEPSLATE_BRICKS);

        this.productions.add(ProductionItems.TINKER, Keybindings.abilitySlot1);
        this.productions.add(ProductionItems.GYRO_SCOUT, Keybindings.abilitySlot2);
        this.productions.add(ProductionItems.RESEARCH_STEWARD, Keybindings.abilitySlot8);   // auto-trains workers (StewardServerEvents)
        this.productions.add(ProductionItems.RESEARCH_TIER_2, Keybindings.abilitySlot9);   // the extractor refit (and the T2 lab, slice 2)
        this.productions.add(ProductionItems.RESEARCH_TIER_3, Keybindings.abilitySlot10);  // the Great Forge (slice 4)
    }

    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String name = key != null ? Component.translatable("buildings." + getFaction().getName() + "." + key.getNamespace() + "." + key.getPath()).getString() : buildingName;
        return new BuildingPlaceButton(name,
            ICON,
            hotkey,
            () -> BuildingClientEvents.getBuildingToPlace() == Buildings.FOUNDRY_HALL,
            () -> false,
            () -> true,
            List.of(Component.translatable("buildings.reignofnether.foundry_hall").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                ResourceCosts.getFormattedCost(cost),
                ResourceCosts.getFormattedPop(cost),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.foundry_hall.tooltip1").getVisualOrderText(),
                Component.translatable("buildings.reignofnether.foundry_hall.tooltip2").getVisualOrderText()
            ),
            this
        );
    }
}
