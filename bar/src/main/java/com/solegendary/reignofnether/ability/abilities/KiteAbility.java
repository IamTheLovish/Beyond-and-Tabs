package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.reactions.Kite;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * <b>Kite</b> (claude/combat-reactions.md): a passive reaction on the pilot's ranged units. The unit itself steps
 * back from a melee enemy that closes in and keeps shooting (unit/reactions/Kite). This card entry is the switch:
 * left or right click turns it off or on for the selected units (#23: units act on their own, the player toggles).
 * One shared instance ({@link #INSTANCE}) for every unit type that has it.
 */
public class KiteAbility extends Ability {

    public static final KiteAbility INSTANCE = new KiteAbility();

    private KiteAbility() {
        super(UnitAction.KITE, 0, 0, 0, false, false);
        this.autoRule(AutoRule.SELF_IN_COMBAT);   // "can autocast" = the switch exists; the unit's own tick runs it
        this.autocastByUnit = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Kite",
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/items/boots.png"),
            hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> this.toggleAutocast(unit),
            null,
            List.of(
                FormattedCharSequence.forward("Kite  (passive)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Steps back from a melee enemy that closes to " + (int) (Kite.CLOSE_FRACTION * 100)
                    + "% of its range, to " + (int) (Kite.STEP_FRACTION * 100) + "%, and keeps shooting.", Style.EMPTY),
                FormattedCharSequence.forward("One step every " + Kite.STEP_COOLDOWN / 20.0 + " s (" + Kite.STEP_COOLDOWN_VETERAN / 20.0
                    + " s at rank 3). Never from a faster enemy, never into water, never when cornered.", Style.EMPTY),
                FormattedCharSequence.forward("Click to switch it off or on. Hold Fire also stops it.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }
}
