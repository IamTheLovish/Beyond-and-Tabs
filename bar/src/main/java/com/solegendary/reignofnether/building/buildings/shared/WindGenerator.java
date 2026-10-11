package com.solegendary.reignofnether.building.buildings.shared;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.util.MyRenderer;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * BAR's wind generator: a cheap mast that gives a steady trickle of energy. Build them in rows behind the base;
 * they explode a little when destroyed, so don't bunch them up against your factories.
 */
public class WindGenerator extends Building {

    public final static String buildingName = "Wind Generator";
    public final static String structureName = "wind_generator";
    public final static ResourceCost cost = ResourceCosts.WIND_GENERATOR;

    public WindGenerator() {
        this("");
    }

    /** variant: "" (Kingdom plaster mill), "_dark" (The Fallen), "_nether" (The Gilded Legion), "_verdant" (Verdant Court), "_tide" (Tidewrought) or "_storm" (Stormcallers wind-chime tower). */
    public WindGenerator(String variant) {
        super(structureName + variant, cost, false);
        this.name = buildingName;
        this.portraitBlock = Blocks.IRON_BLOCK;
        // a mast with the faction's sailcloth (tools/gen_hud_icons.py), not a flat iron square
        this.icon = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/buildings/wind_generator" + variant + ".png");

        this.energyIncome = 14.0f;

        this.explodeChance = 0.35f;
        this.maxHealth = 30d;
        this.buildTimeBlocks = 44;   // the original 44-block mill's build time, for every faction's take
    }

    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String name = key != null ? Component.translatable("buildings." + getFaction().getName() + "." + key.getNamespace() + "." + key.getPath()).getString() : buildingName;
        return new BuildingPlaceButton(
                name,
                this.icon,
                hotkey,
                () -> BuildingClientEvents.getBuildingToPlace() == this,
                () -> false,
                () -> true,
                List.of(
                        Component.translatable("buildings.reignofnether.wind_generator").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                        ResourceCosts.getFormattedCost(cost),
                        FormattedCharSequence.forward("", Style.EMPTY),
                        Component.translatable("buildings.reignofnether.wind_generator.tooltip1").getVisualOrderText(),
                        Component.translatable("buildings.reignofnether.wind_generator.tooltip2").getVisualOrderText()
                ),
                this
        );
    }
}
