package com.solegendary.reignofnether.building.buildings.brass;

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
 * Brassworks T3 lab - the <b>Great Forge</b> (design/brassworks_plan.md, slice 4): a great foundry under a stepped brass
 * roof - four blackstone corner towers, a forge mouth of lit blast furnaces in the back wall, glowing casting channels
 * in the floor, twin smokestacks (great_forge.nbt, tools/gen_structures.py). It casts the faction's experimental, the
 * Iron Leviathan Engine. Like Heaven's Gate and the other T3 labs it needs Tier 3 research (at the Foundry Hall), the
 * faction's T2 lab (the Assembly Works) and a T2 constructor - the Engineer-Captain - among the selected builders; the
 * server enforces the last one too ({@code T2Workers.isT3Lab} / {@code buildersMayPlace}).
 * <p>
 * The build button is the client-side Tier 3 gate; the server checks Tier 3 again whenever this lab starts its
 * experimental ({@link #tier3Error}), so a forge that exists without the research still cannot cast one. It also takes
 * the labs' in-place levels (design/building_upgrades.md, great_forge_l2/_l3 from tools/gen_lab_levels.py) like the
 * Assembly Works: a faster forge for the price of one juicier target.
 */
public class GreatForge extends ProductionBuilding {

    public final static String buildingName = "Great Forge";
    public final static String structureName = "great_forge";
    public final static ResourceCost cost = ResourceCosts.GREAT_FORGE;
    public final static String NEEDS_TIER_3 = "hud.reignofnether.needs_tier_3";
    static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/buildings/great_forge.png");   // tools/gen_hud_icons.py

    public GreatForge() {
        super(structureName, cost, false);
        this.name = buildingName;
        this.portraitBlock = Blocks.BLAST_FURNACE;
        this.icon = ICON;

        this.startingBlockTypes.add(Blocks.POLISHED_BLACKSTONE);
        this.startingBlockTypes.add(Blocks.DEEPSLATE_BRICKS);

        this.buildTimeModifier = 0.5f;   // as the other T3 labs
        this.maxHealth = 800d;
        // the engine is 3.3 blocks wide: it walks out three blocks clear of the towers (Heaven's Gate's rule)
        this.spawnRadiusOffset = 3f;

        this.productions.add(ProductionItems.IRON_LEVIATHAN_ENGINE, Keybindings.abilitySlot4);   // Brassworks T3 experimental
        this.productions.add(ProductionItems.BRASS_T3B, Keybindings.abilitySlot5);   // second T3 (placeholder body), capped one alive each (T3Cap)
        // building levels (design/building_upgrades.md): L1 -> L3 in place, on the ' key, as the Assembly Works
        this.maxLevel = com.solegendary.reignofnether.building.BuildingLevels.MAX_LEVEL;
        this.productions.add(ProductionItems.UPGRADE_LAB_L2, Keybindings.hotkey12);
        this.productions.add(ProductionItems.UPGRADE_LAB_L3, Keybindings.hotkey12);
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
            () -> BuildingClientEvents.getBuildingToPlace() == Buildings.GREAT_FORGE,
            () -> false,
            () -> (BuildingClientEvents.hasFinishedBuilding(Buildings.ASSEMBLY_WORKS)
                    && ResearchClient.hasResearch(ProductionItems.RESEARCH_TIER_3)
                    && com.solegendary.reignofnether.unit.T2Workers.selectedHasT2Worker())   // only the Engineer-Captain can raise the forge
                || ResearchClient.hasCheat("modifythephasevariance"),
            List.of(
                Component.translatable("buildings.reignofnether.great_forge").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                ResourceCosts.getFormattedCost(cost),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.great_forge.tooltip1").getVisualOrderText(),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.great_forge.tooltip2").getVisualOrderText()
            ),
            this
        );
    }
}
