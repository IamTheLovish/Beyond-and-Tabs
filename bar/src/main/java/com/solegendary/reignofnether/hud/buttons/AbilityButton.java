package com.solegendary.reignofnether.hud.buttons;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.HeroAbility;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.util.MyRenderer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;

public class AbilityButton extends Button {

    // can be null for stuff like production buttons (handled separately)
    // or simple abilities with no cooldown and the logic can be handled entirely in onLeftClick()
    public Ability ability;
    @Nullable private Unit unit;
    @Nullable private BuildingPlacement placement;

    public AbilityButton(String name, ResourceLocation rl, Keybinding hotkey, Supplier<Boolean> isSelected,
                         Supplier<Boolean> isHidden, Supplier<Boolean> isEnabled, Runnable onLeftClick, Runnable onRightClick,
                         List<FormattedCharSequence> tooltipLines, @Nullable Ability ability, Unit unit) {

        // generate x/y based on given position (starting at 0 which is bottom left 1 row above generic action buttons)
        super(name, Button.itemIconSize, rl, hotkey, isSelected, isHidden, isEnabled, onLeftClick, onRightClick, tooltipLines);

        this.ability = ability;

        Runnable originalOnLeftClick = this.onLeftClick;
        this.onLeftClick = () -> {
            if (this.ability != null && (this.ability.getCooldown(unit) > 0 && !this.ability.canBypassCooldown(unit)))
                HudClientEvents.showTemporaryMessage(I18n.get("hud.buttons.reignofnether.on_cooldown", Math.round(this.ability.getCooldown(unit) / 20)));
            else if (this.ability instanceof HeroAbility heroAbility && heroAbility.manaCost > 0 && unit instanceof HeroUnit hero && hero.getMana() < heroAbility.manaCost)
                HudClientEvents.showTemporaryMessage(I18n.get("hud.buttons.reignofnether.not_enough_mana"));
            else if (originalOnLeftClick != null)
                originalOnLeftClick.run();
        };
        this.unit = unit;
        // autocast (ability/AutocastState): every autocastable ability of a non-hero unit toggles on right click and says
        // so in its tooltip; abilities with their own right click (the RoN potions, the hero passives) keep theirs
        if (ability != null && unit != null && ability.hasAutocastSwitch(unit)) {
            if (this.onRightClick == null)
                this.onRightClick = () -> ability.toggleAutocast(unit);
            // the RoN abilities with their own toggle orders already say "Right click to auto-cast" in their tooltips
            if (this.tooltipLines != null && ability.autocastEnableAction == null) {
                List<FormattedCharSequence> lines = new java.util.ArrayList<>(this.tooltipLines);
                lines.add(FormattedCharSequence.forward(I18n.get("abilities.reignofnether.autocast"),
                    net.minecraft.network.chat.Style.EMPTY.withItalic(true)));
                this.tooltipLines = lines;
            }
        }
    }
    /** The corner mark's colour: the BAR toggle amber (Button.ACTIVE_EDGE). */
    static final int AUTOCAST_MARK = 0xFFC040;

    /** The switch is on for this button's unit (never for a hero, never for a MANUAL ability). */
    public boolean isAutocastOn() {
        return ability != null && unit != null && ability.hasAutocastSwitch(unit) && ability.isAutocasting(unit);
    }

    public AbilityButton(String name, ResourceLocation rl, Keybinding hotkey, Supplier<Boolean> isSelected,
                         Supplier<Boolean> isHidden, Supplier<Boolean> isEnabled, Runnable onLeftClick, Runnable onRightClick,
                         List<FormattedCharSequence> tooltipLines, @Nullable Ability ability, BuildingPlacement placement) {

        // generate x/y based on given position (starting at 0 which is bottom left 1 row above generic action buttons)
        super(name, Button.itemIconSize, rl, hotkey, isSelected, isHidden, isEnabled, onLeftClick, onRightClick, tooltipLines);

        this.ability = ability;

        Runnable originalOnLeftClick = this.onLeftClick;
        this.onLeftClick = () -> {
            if (this.ability != null && (this.ability.getCooldown(placement) > 0 && !this.ability.canBypassCooldown(building)))
                HudClientEvents.showTemporaryMessage(I18n.get("hud.buttons.reignofnether.on_cooldown", Math.round(this.ability.getCooldown(placement) / 20)));
            else if (originalOnLeftClick != null)
                originalOnLeftClick.run();
        };

        this.placement = placement;
    }

    @Override
    protected void renderHotkey(GuiGraphics guiGraphics, int x, int y) {
        if (this.ability == null || !this.ability.usesCharges())
            super.renderHotkey(guiGraphics, x, y);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        float cooldown = 0f;

        if (this.ability != null && ability.cooldownMax > 0) {
            if (unit != null) {
                cooldown = ability.getCooldown(unit);
            } else if (placement != null) {
                cooldown = ability.getCooldown(placement);
            }
            this.greyPercent = 1.0f - (cooldown / ability.cooldownMax);
        }
        super.render(guiGraphics, x, y, mouseX, mouseY);

        // autocast on: the frame RoN lit for its auto-cast potions (the selected frame) plus an "A" badge in the top-left
        // corner (the hotkey keeps the top-right, the charges the bottom-right), so it reads apart from a press
        if (isAutocastOn()) {
            int xyDiff = (DEFAULT_ICON_SIZE - iconSize) / 2;
            if (frameResource != null) {
                guiGraphics.pose().translate(0, 0, 1);
                MyRenderer.renderIcon(guiGraphics,
                    ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/hud/icon_frame_selected.png"),
                    x - 1 + xyDiff, y - 1 + xyDiff, iconSelectedFrameSize);
            }
            renderBadge(guiGraphics, "A", x, y, Corner.TOP_LEFT, AUTOCAST_MARK);
        }

        // charges remaining, a badge in the bottom-right corner (the hotkey keeps the top-right)
        if (this.ability != null && this.ability.usesCharges()) {
            int charges = unit != null ? unit.getAbilityCharges(ability) : building.getCharges(ability);
            int colour = 0xFFFFFF;
            if (charges >= this.ability.maxCharges)
                colour = 0x00FF00;
            else if (charges <= 0)
                colour = 0xFF0000;
            else if (charges == 1)
                colour = 0xFFFF00;
            renderBadge(guiGraphics, String.valueOf(charges), x, y, Corner.BOTTOM_RIGHT, colour);
            super.renderHotkey(guiGraphics, x, y);
        }
    }
}
