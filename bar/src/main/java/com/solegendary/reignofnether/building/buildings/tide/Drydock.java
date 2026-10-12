package com.solegendary.reignofnether.building.buildings.tide;

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
 * Tidewrought T2 lab - the <b>Drydock</b> (design/tidewrought_plan.md, slice 3): a stone graving dock with a hull on its
 * keel blocks under a waxed-copper gantry, dark-prismarine walls and dead coral on the steps (drydock.nbt,
 * tools/gen_structures.py). Trains the faction's T2 army - Kraken Callers, Mortar Galleys and Sky Corsairs - and the
 * Harbourmaster, its T2 constructor. Like the Circle of Elders it needs Tier 2 research (at the Wreck Harbour) and the
 * faction's T1 lab, the Slipway.
 * <p>
 * The build button is the client-side gate; the server checks Tier 2 again whenever this lab starts an item
 * ({@link #tier2Error}, read by every TideT2Prod), so a Drydock that exists without the research still cannot train T2.
 */
public class Drydock extends ProductionBuilding {

    public final static String buildingName = "Drydock";
    public final static String structureName = "drydock";
    public final static ResourceCost cost = ResourceCosts.DRYDOCK;
    public final static String NEEDS_TIER_2 = "hud.reignofnether.needs_tier_2";
    // vanilla has no waxed_cut_copper.png (waxed blocks share cut_copper's texture), so this was the missing-texture
    // "?" in lovish's clip; now an anchor on copper (tools/gen_hud_icons.py)
    static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/buildings/drydock.png");

    public Drydock() {
        super(structureName, cost, false);
        this.name = buildingName;
        this.portraitBlock = Blocks.WAXED_CUT_COPPER;
        this.icon = ICON;

        this.startingBlockTypes.add(Blocks.SMOOTH_SANDSTONE);
        this.startingBlockTypes.add(Blocks.DARK_PRISMARINE);

        this.buildTimeModifier = 0.7f;   // as the other T2 labs
        this.explodeChance = 0.2f;
        this.maxHealth = 340d;

        this.productions.add(ProductionItems.KRAKEN_CALLER, Keybindings.abilitySlot1);
        this.productions.add(ProductionItems.MORTAR_GALLEY, Keybindings.abilitySlot2);
        this.productions.add(ProductionItems.SKY_CORSAIR, Keybindings.abilitySlot3);
        this.productions.add(ProductionItems.HARBOURMASTER, Keybindings.abilitySlot4);   // T2 constructor
        this.productions.add(ProductionItems.SHELLBACK_TURTLE, Keybindings.abilitySlot5);   // T2 beast (design/big_units.md)
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
            () -> BuildingClientEvents.getBuildingToPlace() == Buildings.DRYDOCK,
            () -> false,
            () -> (BuildingClientEvents.hasFinishedBuilding(Buildings.SLIPWAY) && ResearchClient.hasResearch(ProductionItems.RESEARCH_TIER_2)) ||   // T2 lab: needs Tier 2
                    ResearchClient.hasCheat("modifythephasevariance"),
            List.of(
                Component.translatable("buildings.reignofnether.drydock").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                ResourceCosts.getFormattedCost(cost),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.drydock.tooltip1").getVisualOrderText(),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.drydock.tooltip2").getVisualOrderText()
            ),
            this
        );
    }
}
