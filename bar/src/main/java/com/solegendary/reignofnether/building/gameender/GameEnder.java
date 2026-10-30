package com.solegendary.reignofnether.building.gameender;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.research.ResearchClient;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.function.Supplier;

/**
 * A faction's <b>game ender</b> building (design/big_units.md section 2): the shared stats and build button. Each
 * faction's subclass names its structure, its T3 lab (the button needs it built, plus Tier 3 research and a T2
 * constructor selected - T2Workers.needsT2Builder enforces the last on the server) and its placement, which carries
 * the charge-and-strike life ({@link GameEnderPlacement}).
 * <p>
 * Construction is long on purpose ({@link #BUILD_TIME_BLOCKS}: ~4 minutes with six T2 builders' worth of build power)
 * and the site is announced to everyone the moment it is placed: the window in which scouting and raiding win.
 */
public abstract class GameEnder extends Building {

    public static final int BUILD_TIME_BLOCKS = 1200;
    public static final double MAX_HEALTH = 2400d;

    private final Supplier<Building> t3Lab;
    private final ResourceLocation buttonIcon;

    protected GameEnder(String structureName, String buildingName, ResourceCost cost, Block portrait, String vanillaIconPath,
                        Supplier<Building> t3Lab) {
        super(structureName, cost, false);
        this.name = buildingName;
        this.portraitBlock = portrait;
        this.buttonIcon = ResourceLocation.fromNamespaceAndPath("minecraft", vanillaIconPath);
        this.icon = buttonIcon;
        this.t3Lab = t3Lab;
        this.buildTimeBlocks = BUILD_TIME_BLOCKS;
        this.maxHealth = MAX_HEALTH;
    }

    /** Two lines of tooltip that say what the strike does. */
    protected abstract List<FormattedCharSequence> strikeTooltip();

    @Override
    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String label = key != null ? Component.translatable("buildings." + getFaction().getName() + "." + key.getNamespace() + "." + key.getPath()).getString() : name;
        List<FormattedCharSequence> tip = new java.util.ArrayList<>();
        tip.add(Component.translatable("buildings.reignofnether." + structureName).withStyle(Style.EMPTY.withBold(true)).getVisualOrderText());
        tip.add(ResourceCosts.getFormattedCost(cost));
        tip.add(FormattedCharSequence.EMPTY);
        tip.addAll(strikeTooltip());
        tip.add(FormattedCharSequence.EMPTY);
        tip.add(Component.translatable("buildings.reignofnether.game_ender.tooltip",
            GameEnderPlacement.STAGES, GameEnderPlacement.STAGE_TICKS / 20).getVisualOrderText());
        tip.add(Component.translatable("buildings.reignofnether.game_ender.tooltip2").getVisualOrderText());
        return new BuildingPlaceButton(label, buttonIcon, hotkey,
            () -> BuildingClientEvents.getBuildingToPlace() == this,
            () -> false,
            () -> (t3Lab.get() != null && BuildingClientEvents.hasFinishedBuilding(t3Lab.get())
                    && ResearchClient.hasResearch(ProductionItems.RESEARCH_TIER_3)
                    && com.solegendary.reignofnether.unit.T2Workers.selectedHasT2Worker())
                || ResearchClient.hasCheat("modifythephasevariance"),
            tip, this);
    }
}
