package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.ExperimentalAbilityText;
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
 * War Mammoth: <b>Trample</b> (claude/t3-roles.md: the Flanker's charge). Aim a spot or an enemy: after a short wind-up
 * that draws the lane on the ground, the Mammoth charges up to {@link WarMammothUnit#TRAMPLE_LENGTH} blocks straight at
 * it, hitting and flinging aside every enemy it passes; a wall or a building stops it. The charge itself lives on the
 * Mammoth (its tick drives it, as the Gorehorn Rhino's Breach Charge does); this is the button and the order.
 * {@link #CD_SECONDS} s cooldown - short enough to be the Mammoth's way in AND its way out of a fight.
 * <p>
 * Before t3-roles this was an instant lunge that never moved the Mammoth, on a 60 s cooldown: a slow unit with a
 * trick, not a flanker.
 */
public class Trample extends Ability {

    public static final int CD_SECONDS = 30;
    /** The lane the bot and the autocast count enemies in (BotPlayer.useTitanAbilities). */
    public static final double LENGTH = WarMammothUnit.TRAMPLE_LENGTH;
    public static final double HALF_WIDTH = WarMammothUnit.TRAMPLE_HIT_RADIUS + 1.5;
    public static final float DAMAGE = WarMammothUnit.TRAMPLE_DAMAGE;

    public Trample() {
        super(UnitAction.TRAMPLE, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, (float) LENGTH, 0, true, false);
        this.autoRule(AutoRule.CROWD);   // autocast: a charge through a lane pays off on a clump, not a straggler
        this.showRangeLine = true;
        this.showRangeCircle = true;
    }

    /** Tooltip body. Public for the tooltip game test. */
    public String description() {
        return "Charges at the spot: enemies in the way take " + (int) DAMAGE + " and are flung aside.";
    }

    /** Tooltip range line, from the ability's own values. Public for the tooltip game test. */
    public String rangeLine() {
        return ExperimentalAbilityText.rangeLine(range);
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
                FormattedCharSequence.forward(rangeLine(), Style.EMPTY),
                FormattedCharSequence.forward(description(), Style.EMPTY),
                FormattedCharSequence.forward("A wall stops it. The lane shows first: step aside.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    /** Aimed at a unit (a click on it, the autocast's clump, the bot): the whole lane, through the group. */
    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        if (targetEntity != null)
            start(level, unitUsing, targetEntity.position(), true);
    }

    /** Aimed at the ground: exactly as far as the spot (a reposition, a way out of a trap). */
    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (targetBp != null)
            start(level, unitUsing, Vec3.atBottomCenterOf(targetBp), false);
    }

    void start(Level level, Unit unitUsing, Vec3 at, boolean through) {
        if (level.isClientSide() || !(unitUsing instanceof WarMammothUnit mammoth))
            return;
        if (!mammoth.startTrample(at, through))
            return;
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(mammoth.getId(), this.action, this.cooldownMax);
    }
}
