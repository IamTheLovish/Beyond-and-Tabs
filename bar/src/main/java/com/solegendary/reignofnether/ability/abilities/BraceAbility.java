package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.reactions.Brace;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * <b>Brace</b> (claude/combat-reactions.md): a passive reaction on the pilot's shield units. When two or more enemies
 * come for it, or a charger closes in, the unit plants its shield: less damage, slower, for a moment
 * (unit/reactions/Brace). The card entry is the switch and shows the cooldown. One shared instance.
 */
public class BraceAbility extends Ability {

    public static final BraceAbility INSTANCE = new BraceAbility();

    private BraceAbility() {
        super(UnitAction.BRACE, Brace.COOLDOWN_TICKS, 0, 0, false, false);
        this.autoRule(AutoRule.SELF_IN_COMBAT);
        this.autocastByUnit = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Brace",
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/items/shield.png"),
            hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> this.toggleAutocast(unit),
            null,
            List.of(
                FormattedCharSequence.forward("Brace  (passive, " + Brace.COOLDOWN_TICKS / ResourceCost.TICKS_PER_SECOND + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("When " + Brace.THREATS + " or more enemies come for it, or a charger closes within "
                    + (int) Brace.CHARGE_RANGE + " blocks: takes " + (int) ((1 - Brace.DAMAGE_MULT) * 100) + "% less damage ("
                    + (int) ((1 - Brace.DAMAGE_MULT_VETERAN) * 100) + "% at rank 3) and moves at half speed for "
                    + Brace.DURATION_TICKS / 20 + " s.", Style.EMPTY),
                FormattedCharSequence.forward("Click to switch it off or on.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }
}
