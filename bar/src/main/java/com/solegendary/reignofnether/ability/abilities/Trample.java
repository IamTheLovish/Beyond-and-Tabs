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
import com.solegendary.reignofnether.unit.units.piglins.WarMammothUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * War Mammoth: <b>Trample</b> (project doc claude/t3-roles.md - the flanker's charge). Aim a spot or an enemy: after a
 * short wind-up that draws the lane on the ground, the mammoth runs up to {@link WarMammothUnit#CHARGE_LENGTH} blocks
 * straight at it, hitting and flinging aside every enemy it runs through; a wall or a building stops it short (no
 * building damage - a wall line is the mammoth's counter, the Gorehorn Rhino is the gate breaker). The charge itself
 * lives on the mammoth (its tick drives it); this is the button and the order. {@link #CD_SECONDS} s cooldown: once
 * per engagement - in, through the line, out - not a lunge it spends on the first straggler (the old 60 s instant lane
 * lunge was "almost useless", lovish Oct 21).
 * <p>
 * Autocast (CROWD): the mammoth charges by itself when three or more enemies stand within {@link #CROWD_RADIUS} of the
 * nearest enemy in its lane's reach - a group to run through, never one body.
 */
public class Trample extends Ability {

    public static final int CD_SECONDS = 30;
    /** The lane: the mammoth's body plus its reach to each side (the bot's lane heuristic, BotPlayer.useTitanAbilities). */
    public static final double LENGTH = WarMammothUnit.CHARGE_LENGTH, HALF_WIDTH = 2.8;
    public static final float DAMAGE = WarMammothUnit.CHARGE_DAMAGE;
    public static final float CROWD_RADIUS = 3.5f;

    public Trample() {
        super(UnitAction.TRAMPLE, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, WarMammothUnit.CHARGE_LENGTH, CROWD_RADIUS, true, false);
        this.autoRule(AutoRule.CROWD);   // autocast: a charge through a lane pays off on a clump, not a straggler
        this.showRangeLine = true;
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Trample",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/leather.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.TRAMPLE,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.TRAMPLE),
            null,
            List.of(
                FormattedCharSequence.forward("Trample  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Charge up to " + (int) LENGTH + " blocks in a straight line: enemies run through take "
                    + (int) DAMAGE + " damage and are flung aside.", Style.EMPTY),
                FormattedCharSequence.forward("A wall or a building stops it. The lane shows first: step aside.", Style.EMPTY)
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
        if (level.isClientSide() || !(unitUsing instanceof WarMammothUnit mammoth))
            return;
        if (!mammoth.startCharge(at))
            return;
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(mammoth.getId(), this.action, this.cooldownMax);
    }
}
