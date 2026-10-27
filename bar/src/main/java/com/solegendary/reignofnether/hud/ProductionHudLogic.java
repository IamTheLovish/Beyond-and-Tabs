package com.solegendary.reignofnether.hud;

import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.unit.Relationship;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure rules behind two HUD pieces, kept free of rendering so GameTests can cover them:
 * the Factory tab (which factories get an icon) and the Repeat button (when it shows, what a press sets).
 * Why: playtest - Repeat vanished as soon as two factories were selected, and the Factory tab only grouped
 * queued items instead of showing each busy factory.
 */
public final class ProductionHudLogic {

    private ProductionHudLogic() { }

    /** A factory counts as producing once it is built and has something at the front of its queue. */
    public static boolean isProducing(boolean built, int queueSize) {
        return built && queueSize > 0;
    }

    /** Repeat shows when at least one building is selected and every selected building is an owned, built factory. */
    public static boolean repeatVisible(int selectedCount, int ownedBuiltFactoryCount) {
        return selectedCount >= 1 && ownedBuiltFactoryCount == selectedCount;
    }

    /** What one press sets on every selected factory: all on if any is off (mixed or all off), all off if all on. */
    public static boolean repeatTarget(List<Boolean> repeatStates) {
        for (Boolean on : repeatStates)
            if (!Boolean.TRUE.equals(on))
                return true;
        return false;
    }

    /** The selected buildings Repeat applies to: owned, built production buildings of any type. */
    public static List<ProductionPlacement> repeatGroup(List<BuildingPlacement> selected) {
        List<ProductionPlacement> out = new ArrayList<>();
        for (BuildingPlacement b : selected)
            if (b instanceof ProductionPlacement pp && b.isBuilt
                    && BuildingClientEvents.getPlayerToBuildingRelationship(b) == Relationship.OWNED)
                out.add(pp);
        return out;
    }

    /** Repeat is offered for the current selection (see {@link #repeatVisible}). */
    public static boolean repeatVisible(List<BuildingPlacement> selected) {
        return repeatVisible(selected.size(), repeatGroup(selected).size());
    }

    // ------------------------------------------------------------------ factory guard (BAR factoryguard, Ctrl+G)

    /** Ctrl+G toggles factory guard when no unit is selected and every selected building is an owned, built factory. */
    public static boolean ctrlGToggles(int selectedUnits, List<BuildingPlacement> selected) {
        return selectedUnits == 0 && repeatVisible(selected);
    }

    /** True when every factory in the group has factory guard on (what the button shows). */
    public static boolean allGuarding(List<ProductionPlacement> group) {
        if (group.isEmpty())
            return false;
        for (ProductionPlacement pp : group)
            if (!pp.factoryGuard)
                return false;
        return true;
    }

    /** The card's factory guard toggle for {@code group} (client only): tick/cross and a lit frame while on. */
    public static com.solegendary.reignofnether.hud.buttons.Button factoryGuardButton(List<ProductionPlacement> group) {
        return new com.solegendary.reignofnether.hud.buttons.BooleanButton(
            com.solegendary.reignofnether.util.LanguageUtil.getTranslation("hud.reignofnether.factory_guard"),
            allGuarding(group),
            () -> com.solegendary.reignofnether.building.BuildingProductionServerboundPacket.toggleFactoryGuard(group),
            com.solegendary.reignofnether.util.LanguageUtil.getTranslation("hud.reignofnether.factory_guard.tooltip"),
            true);
    }
}
