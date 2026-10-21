package com.solegendary.reignofnether.building.buildings.defence;

import com.solegendary.reignofnether.ReignOfNether;
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * What every static defence shares (design/defences.md): a faction {@link DefenceStyle}, a drawn icon
 * (textures/icons/buildings/&lt;id&gt;.png, tools/defence_icons.py) and a build button whose name, cost and two tooltip
 * lines come from the lang file under the building's id. The prerequisite is the faction's finished capitol for the T1
 * defences (walls, gates, traps, the anti-raid tower, the Sky Watch), any finished T1 lab for the enhanced T1 towers,
 * Tier 2 research and a T2 constructor among the selection for the flak (the server checks the builders:
 * T2Workers.needsT2Builder), or a finished T3 lab plus Tier 3 research for the anti-T3 emplacement.
 */
public abstract class DefenceBuilding extends Building {

    public final DefenceStyle style;
    /** The registry path (also the structure name): "wall_villagers", "raid_tower_tide", ... */
    public final String id;
    /** The labs it waits for, any one finished (null: the capitol is enough). Resolved lazily - labs register first. */
    protected Supplier<List<Building>> anyOfLabs = null;
    protected boolean needsTier2 = false;
    protected boolean needsTier3 = false;
    /** Only a T2 constructor may place it (T2Workers.needsT2Builder - the build button checks the selection). */
    protected boolean t2Builder = false;

    protected DefenceBuilding(String id, String displayName, DefenceStyle style, ResourceCost cost) {
        super(id, cost, false);
        this.id = id;
        this.style = style;
        this.name = displayName;
        this.icon = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/buildings/" + id + ".png");   // tools/defence_icons.py
        this.repairable = true;
    }

    /** The values the tooltip's first line formats (%1$s, %2$s, ...). */
    protected Object[] tooltipArgs() {
        return new Object[0];
    }

    /** The labs it waits for, any one finished (null: none) - shared so a faction's enhanced defences use one T1 lab list. */
    @javax.annotation.Nullable
    public Supplier<List<Building>> requiredLabs() {
        return anyOfLabs;
    }

    /** Whether it waits for a lab (the enhanced T1 defences, the anti-T3 emplacement): a base commitment. */
    public boolean requiresLab() {
        return anyOfLabs != null;
    }

    /** Whether only a T2 constructor may place it (read by T2Workers.needsT2Builder on both sides). */
    public boolean needsT2Builder() {
        return t2Builder;
    }

    /** Whether the local player may place it now (client side: the build button's enabled state). */
    protected boolean clientPrerequisitesMet() {
        if (ResearchClient.hasCheat("modifythephasevariance"))
            return true;
        if (t2Builder && !com.solegendary.reignofnether.unit.T2Workers.selectedHasT2Worker())
            return false;
        if (needsTier2 && !ResearchClient.hasResearch(ProductionItems.RESEARCH_TIER_2))
            return false;
        if (needsTier3 && !ResearchClient.hasResearch(ProductionItems.RESEARCH_TIER_3))
            return false;
        if (anyOfLabs != null) {
            for (Building lab : anyOfLabs.get())
                if (lab != null && BuildingClientEvents.hasFinishedBuilding(lab))
                    return true;
            return false;
        }
        ResourceLocation capitol = getFaction().capitolBuilding;
        Building cap = capitol == null ? null : ReignOfNetherRegistries.BUILDING.get(capitol);
        return cap == null || BuildingClientEvents.hasFinishedBuilding(cap);
    }

    @Override
    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String langKey = "buildings." + ReignOfNether.MOD_ID + "." + id;
        String shown = key != null ? Component.translatable(langKey).getString() : name;
        List<FormattedCharSequence> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable(langKey).withStyle(Style.EMPTY.withBold(true)).getVisualOrderText());
        tooltip.add(ResourceCosts.getFormattedCost(cost));
        tooltip.add(FormattedCharSequence.forward("", Style.EMPTY));
        tooltip.add(Component.translatable(langKey + ".tooltip1", tooltipArgs()).getVisualOrderText());
        tooltip.add(FormattedCharSequence.forward("", Style.EMPTY));
        tooltip.add(Component.translatable(langKey + ".tooltip2").getVisualOrderText());
        return new BuildingPlaceButton(
            shown,
            icon,
            hotkey,
            () -> BuildingClientEvents.getBuildingToPlace() == this,
            () -> false,
            this::clientPrerequisitesMet,
            tooltip,
            this
        );
    }
}
