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
}
