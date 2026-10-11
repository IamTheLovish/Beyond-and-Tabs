package com.solegendary.reignofnether.hud;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.util.MyRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

public class TradeAbilityButton extends AbilityButton {

    private final int rate;

    public TradeAbilityButton(String name, ResourceLocation rl, Keybinding hotkey, Supplier<Boolean> isSelected, Supplier<Boolean> isHidden,
                              Supplier<Boolean> isEnabled, Runnable onLeftClick, Runnable onRightClick, List<FormattedCharSequence> tooltipLines,
                              @Nullable Ability ability, BuildingPlacement placement, int rate) {
        super(name, rl, hotkey, isSelected, isHidden, isEnabled, onLeftClick, onRightClick, tooltipLines, ability, placement);
        this.rate = rate;
    }

    @Override
    protected void renderHotkey(GuiGraphics guiGraphics, int x, int y) {
        super.renderHotkey(guiGraphics, x, y);                                   // top-right
        renderBadge(guiGraphics, String.valueOf(rate), x, y, Corner.TOP_LEFT, 0xFFFFFF);
    }
}
