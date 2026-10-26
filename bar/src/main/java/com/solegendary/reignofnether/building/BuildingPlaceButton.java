package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.hud.buttons.Button;
import com.solegendary.reignofnether.keybinds.Keybinding;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.function.Supplier;

public class BuildingPlaceButton extends Button {
    /** The build-menu tab this button is drawn in (custom buildings, added without a registration, go to Tech). */
    public BuildGroup group = BuildGroup.TECH;
    /** Position in its tab (the grid cell and the slot key); -1 = none, drawn after the last slot. */
    public int slot = -1;

    public BuildingPlaceButton(String name, ResourceLocation rl, Keybinding hotkey, Supplier<Boolean> isSelected,
                               Supplier<Boolean> isHidden, Supplier<Boolean> isEnabled,
                               List<FormattedCharSequence> tooltipLines, Building building) {

        // generate x/y based on given position (starting at 0 which is bottom left 1 row above generic action buttons)
        super(name, Button.itemIconSize, rl, hotkey, isSelected, isHidden, isEnabled, () -> BuildingClientEvents.setBuildingToPlace(building), null, tooltipLines);
    }

    /**
     * Another builder type's building on a mixed card whose key is taken (HudClientEvents.keylessCopy,
     * BuilderCardUnion): no key, same tab, no slot, so BuildTabs draws it after the tab's last slot. The caller copies
     * the remaining look fields.
     */
    public BuildingPlaceButton keylessCopy() {
        return new BuildingPlaceButton(this);
    }

    private BuildingPlaceButton(BuildingPlaceButton o) {
        super(o.name, o.iconSize, o.iconResource, o.frameResource, null, o.isSelected, o.isHidden, o.isEnabled,
            o.onLeftClick, o.onRightClick, o.tooltipLines);
        this.group = o.group;
        this.slot = -1;
    }
}
