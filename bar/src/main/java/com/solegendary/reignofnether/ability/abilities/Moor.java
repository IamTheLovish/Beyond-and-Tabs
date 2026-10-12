package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.tide.ShellbackTurtleUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Shellback Siege-Turtle: <b>Moor</b> (design/big_units.md 1.5), a toggle. Drops anchor (or weighs it): moored the turtle
 * is a fort - half damage, a tidepool round it, the deck battery firing - and can't move. The anchor takes
 * {@link ShellbackTurtleUnit#MOOR_TICKS} ticks either way, so leaving is never instant. No cooldown: the anchor time is
 * the price. The button lights while moored (or mooring).
 */
public class Moor extends Ability {

    public Moor() {
        super(UnitAction.MOOR, 0, 0, 0, false, false);
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        ShellbackTurtleUnit turtle = unit instanceof ShellbackTurtleUnit t ? t : null;
        return new AbilityButton("Moor",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/chain.png"),
            hotkey,
            () -> turtle != null && turtle.isMooredClient(),
            () -> false,
            () -> true,
            () -> UnitClientEvents.sendUnitCommand(UnitAction.MOOR),
            null,
            List.of(
                FormattedCharSequence.forward("Moor / Weigh Anchor", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Anchors in " + ShellbackTurtleUnit.MOOR_TICKS / 20 + " s: takes half damage, raises a tidepool round itself and fires its deck battery ("
                    + (int) ShellbackTurtleUnit.BATTERY_DAMAGE + " every " + ShellbackTurtleUnit.BATTERY_EVERY / 20 + " s, "
                    + (int) ShellbackTurtleUnit.BATTERY_MIN_RANGE + "-" + (int) ShellbackTurtleUnit.BATTERY_RANGE + " blocks; buildings when no unit is in range).", Style.EMPTY),
                FormattedCharSequence.forward("It can't move or bite while anchored, and weighing anchor takes as long.", Style.EMPTY)
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (!level.isClientSide() && unitUsing instanceof ShellbackTurtleUnit turtle)
            turtle.toggleMoor();
    }
}
