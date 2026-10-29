package com.solegendary.reignofnether.building.buildings.storm;

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
 * Stormcallers T2 lab - the <b>Celestial Observatory</b> (design/stormcallers_plan.md, slice 3): a tall red-lacquer and
 * jade pagoda tower on a stone plinth with a brass orrery turning on its top deck (celestial_observatory.nbt,
 * tools/gen_structures.py). Trains the faction's T2 army - Thunder Dragons, Earthshakers, Gravity Monks, Mirror
 * Mages, Seers, Lantern Fortresses and Phoenixes - and the Ley Master, its T2 constructor. Like the Drydock and the Circle of Elders it needs Tier 2 research
 * (at the Jade Pagoda) and the faction's T1 lab, the Lotus Academy.
 * <p>
 * The build button is the client-side gate; the server checks Tier 2 again whenever this lab starts an item
 * ({@link #tier2Error}, read by every StormT2Prod), so an Observatory that exists without the research still cannot
 * train T2.
 */
public class CelestialObservatory extends ProductionBuilding {

    public final static String buildingName = "Celestial Observatory";
    public final static String structureName = "celestial_observatory";
    public final static ResourceCost cost = ResourceCosts.CELESTIAL_OBSERVATORY;
    public final static String NEEDS_TIER_2 = "hud.reignofnether.needs_tier_2";
    static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/spyglass.png");

    public CelestialObservatory() {
        super(structureName, cost, false);
        this.name = buildingName;
        this.portraitBlock = Blocks.WAXED_OXIDIZED_CUT_COPPER;
        this.icon = ICON;

        this.startingBlockTypes.add(Blocks.POLISHED_ANDESITE);
        this.startingBlockTypes.add(Blocks.STONE_BRICKS);

        this.buildTimeBlocks = com.solegendary.reignofnether.resources.TierGate.T2_LAB_BUILD_BLOCKS;   // one build time for every faction's T2 lab (resources/TierGate)
        this.explodeChance = 0.2f;
        this.maxHealth = 340d;
        // the Lantern Fortress is 2 blocks wide: it walks out clear of the tower's walls (the Abyssal Shipyard's rule)
        this.spawnRadiusOffset = 2f;

        this.productions.add(ProductionItems.THUNDER_DRAGON, Keybindings.abilitySlot1);
        this.productions.add(ProductionItems.EARTHSHAKER, Keybindings.abilitySlot2);
        this.productions.add(ProductionItems.GRAVITY_MONK, Keybindings.abilitySlot3);
        this.productions.add(ProductionItems.MIRROR_MAGE, Keybindings.abilitySlot4);
        this.productions.add(ProductionItems.LEY_MASTER, Keybindings.abilitySlot5);   // T2 constructor
        // slice 4: the rest of the T2 table
        this.productions.add(ProductionItems.SEER, Keybindings.abilitySlot6);
        this.productions.add(ProductionItems.LANTERN_FORTRESS, Keybindings.abilitySlot7);
        this.productions.add(ProductionItems.PHOENIX, Keybindings.abilitySlot8);
        this.productions.add(ProductionItems.JADE_QILIN, Keybindings.abilitySlot9);   // T2 beast (design/big_units.md)
        this.productions.add(ProductionItems.JADE_MENDER, Keybindings.abilitySlot10);   // combat engineer (design/combat_engineers.md)
        // essence caster (design/essence_casters.md): the ten ability slots are taken, so the build menu's spare key
        this.productions.add(ProductionItems.SPARK_SAGE, Keybindings.hotkey11);
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
            () -> BuildingClientEvents.getBuildingToPlace() == Buildings.CELESTIAL_OBSERVATORY,
            () -> false,
            () -> (BuildingClientEvents.hasFinishedBuilding(Buildings.LOTUS_ACADEMY) && ResearchClient.hasResearch(ProductionItems.RESEARCH_TIER_2)) ||   // T2 lab: needs Tier 2
                    ResearchClient.hasCheat("modifythephasevariance"),
            List.of(
                Component.translatable("buildings.reignofnether.celestial_observatory").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                ResourceCosts.getFormattedCost(cost),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.celestial_observatory.tooltip1").getVisualOrderText(),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.celestial_observatory.tooltip2").getVisualOrderText()
            ),
            this
        );
    }
}
