package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * <b>Safe potions</b> (claude/healer-ai-oct21.md, C): a switch on the Field Medic's card. On - the default, and
 * lovish's open call - her own harming clouds spare every friendly unit standing in them (unit/support/SafePotions);
 * off, a cloud hurts whoever stands in it, friend or foe, as it always did. Her autocast never aims a harming cloud
 * where a friend stands either way: the switch is the safety net for a friend who walks in, and for her clicks.
 * Like Kite, a passive whose card entry is the per-unit switch (AutocastState keyed by {@link UnitAction#SAFE_POTIONS}).
 */
public class SafePotionsAbility extends Ability {

    public static final SafePotionsAbility INSTANCE = new SafePotionsAbility();

    private SafePotionsAbility() {
        super(UnitAction.SAFE_POTIONS, 0, 0, 0, false, false);
        this.autoRule(AutoRule.SELF_IN_COMBAT);   // "can autocast" = the switch exists, on by default; nothing casts it
        this.autocastByUnit = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Safe potions",
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/items/shield.png"),
            hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> this.toggleAutocast(unit),
            null,
            List.of(
                FormattedCharSequence.forward("Safe potions  (passive)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("While on, her harming clouds spare every friendly unit standing in them.", Style.EMPTY),
                FormattedCharSequence.forward("Off: a cloud hurts friend and foe alike. Her own throws never aim at friends either way.", Style.EMPTY),
                FormattedCharSequence.forward("Click to switch it off or on.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }
}
