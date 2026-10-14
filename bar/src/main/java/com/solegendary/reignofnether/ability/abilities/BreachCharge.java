package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.piglins.GorehornRhinoUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Gorehorn Rhino: <b>Breach Charge</b> (design/big_units.md 1.2). Aim a spot or an enemy: after a short wind-up that
 * draws the lane on the ground, the rhino charges up to {@link GorehornRhinoUnit#BREACH_LENGTH} blocks straight at it,
 * hitting and flinging aside every enemy it passes; the first building in its way stops it and takes
 * {@link GorehornRhinoUnit#BREACH_BUILDING_DAMAGE}. The charge itself lives on the rhino (its tick drives it); this is
 * the button and the order. {@link #CD_SECONDS} s cooldown.
 */
public class BreachCharge extends Ability {

    public static final int CD_SECONDS = 25;

    public BreachCharge() {
        super(UnitAction.BREACH_CHARGE, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, GorehornRhinoUnit.BREACH_LENGTH, 0, true, false);
        this.autoRule(AutoRule.CROWD);   // autocast: a charge through a lane pays off on a clump, not a straggler
        this.showRangeLine = true;
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Breach Charge",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/iron_horse_armor.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.BREACH_CHARGE,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.BREACH_CHARGE),
            null,
            List.of(
                FormattedCharSequence.forward("Breach Charge  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Charge up to " + (int) GorehornRhinoUnit.BREACH_LENGTH + " blocks in a straight line: enemies in the way take "
                    + (int) GorehornRhinoUnit.BREACH_DAMAGE + " and are flung aside.", Style.EMPTY),
                FormattedCharSequence.forward("The first building in the way stops it and takes "
                    + (int) GorehornRhinoUnit.BREACH_BUILDING_DAMAGE + ". The lane shows first: step aside.", Style.EMPTY)
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        if (targetEntity != null)
            start(level, unitUsing, targetEntity.position());
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        start(level, unitUsing, Vec3.atBottomCenterOf(targetBp));
    }

    void start(Level level, Unit unitUsing, Vec3 at) {
        if (level.isClientSide() || !(unitUsing instanceof GorehornRhinoUnit rhino))
            return;
        if (!rhino.startBreach(at))
            return;
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(rhino.getId(), this.action, this.cooldownMax);
    }
}
