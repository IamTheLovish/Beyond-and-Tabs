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
 * Brassworks T2 lab - the <b>Assembly Works</b> (design/brassworks_plan.md, slice 2): a brass-roofed assembly hall on a
 * blackstone plinth, open at the front for the machines to roll out, with a travelling gantry crane over the floor, two
 * smokestacks and a boiler at the back (assembly_works.nbt, tools/gen_structures.py). Trains the faction's T2 army -
 * Steam Juggernauts, Tesla Walkers, Gyrocopter Gunships, Drill Burrowers, Repair Crawlers and Clockwork Spiders - and
 * the Engineer-Captain, its T2 constructor. Like the Celestial Observatory it needs Tier 2 research (at the Foundry
 * Hall) and the faction's T1 lab, the Machine Shop.
 * <p>
 * The build button is the client-side gate; the server checks Tier 2 again whenever this lab starts an item
 * ({@link #tier2Error}, read by every BrassT2Prod), so a Works that exists without the research still cannot train T2.
 * It also takes the T1 labs' in-place levels (design/building_upgrades.md, assembly_works_l2/_l3 from
 * tools/gen_lab_levels.py): the same upgrade order, the same numbers - a T2 queue is the scarcer one, so concentrating
 * it is the same chess trade.
 */
public class AssemblyWorks extends ProductionBuilding {

    public final static String buildingName = "Assembly Works";
    public final static String structureName = "assembly_works";
    public final static ResourceCost cost = ResourceCosts.ASSEMBLY_WORKS;
    public final static String NEEDS_TIER_2 = "hud.reignofnether.needs_tier_2";
    static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/buildings/assembly_works.png");   // tools/gen_hud_icons.py

    public AssemblyWorks() {
        super(structureName, cost, false);
        this.name = buildingName;
        this.portraitBlock = Blocks.WAXED_CUT_COPPER;
        this.icon = ICON;

        this.startingBlockTypes.add(Blocks.POLISHED_BLACKSTONE);
        this.startingBlockTypes.add(Blocks.DEEPSLATE_BRICKS);

        this.buildTimeModifier = 0.7f;   // as the other T2 labs
        this.explodeChance = 0.2f;
        this.maxHealth = 340d;
        // the Juggernaut is wide: it walks out clear of the hall's walls (the Observatory's rule)
        this.spawnRadiusOffset = 2f;

        this.productions.add(ProductionItems.STEAM_JUGGERNAUT, Keybindings.abilitySlot1);
        this.productions.add(ProductionItems.TESLA_WALKER, Keybindings.abilitySlot2);
        this.productions.add(ProductionItems.GYROCOPTER_GUNSHIP, Keybindings.abilitySlot3);
        this.productions.add(ProductionItems.DRILL_BURROWER, Keybindings.abilitySlot4);
        this.productions.add(ProductionItems.ENGINEER_CAPTAIN, Keybindings.abilitySlot5);   // T2 constructor
        this.productions.add(ProductionItems.REPAIR_CRAWLER, Keybindings.abilitySlot6);
        this.productions.add(ProductionItems.CLOCKWORK_SPIDER, Keybindings.abilitySlot7);
        this.productions.add(ProductionItems.STEAM_HAULER, Keybindings.abilitySlot8);   // T2 beast (design/big_units.md)
        // building levels (design/building_upgrades.md): L1 -> L3 in place, on the ' key, as the Machine Shop
        this.maxLevel = com.solegendary.reignofnether.building.BuildingLevels.MAX_LEVEL;
        this.productions.add(ProductionItems.UPGRADE_LAB_L2, Keybindings.hotkey12);
        this.productions.add(ProductionItems.UPGRADE_LAB_L3, Keybindings.hotkey12);
    }

    /** Server and client: null if this lab's owner has Tier 2, else the message to show (the T2 items' produce gate). */
    @Nullable
    public static String tier2Error(ProductionPlacement pp) {
        if (pp == null)
            return NEEDS_TIER_2;
        boolean has = pp.level.isClientSide()
            ? ResearchClient.hasResearch(ProductionItems.RESEARCH_TIER_2)
            : ResearchServerEvents.playerHasResearch(pp.ownerName, ProductionItems.RESEARCH_TIER_2);
        return has ? null : NEEDS_TIER_2;
    }

    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String name = key != null ? Component.translatable("buildings." + getFaction().getName() + "." + key.getNamespace() + "." + key.getPath()).getString() : buildingName;
        return new BuildingPlaceButton(
            name,
            ICON,
            hotkey,
            () -> BuildingClientEvents.getBuildingToPlace() == Buildings.ASSEMBLY_WORKS,
            () -> false,
            () -> (BuildingClientEvents.hasFinishedBuilding(Buildings.MACHINE_SHOP) && ResearchClient.hasResearch(ProductionItems.RESEARCH_TIER_2)) ||   // T2 lab: needs Tier 2
                    ResearchClient.hasCheat("modifythephasevariance"),
            List.of(
                Component.translatable("buildings.reignofnether.assembly_works").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                ResourceCosts.getFormattedCost(cost),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.assembly_works.tooltip1").getVisualOrderText(),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.assembly_works.tooltip2").getVisualOrderText()
            ),
            this
        );
    }
}
