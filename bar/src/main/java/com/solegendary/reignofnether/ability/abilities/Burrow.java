package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.brass.DrillBurrowerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Brassworks Drill Burrower: <b>Burrow</b> (design/brassworks_plan.md, slice 2). Aim a spot (or an enemy) within
 * {@link #RANGE}: the Drill Burrower bores into the ground and is gone - untargetable and unhurt - for
 * {@link DrillBurrowerUnit#BURROW_TICKS}, a dust trail creeping toward the spot and, for its last
 * {@link DrillBurrowerUnit#TELEGRAPH_TICKS}, the ground cracking where it will come up (the telegraph a defender can
 * read). Then it bursts up there, throwing every enemy within {@link DrillBurrowerUnit#KNOCK_RADIUS} into the air for
 * {@link DrillBurrowerUnit#KNOCK_DAMAGE}. {@link #CD_SECONDS} s cooldown; every selected Burrower digs.
 * The dig itself is DrillBurrowerUnit's (its own tick drives it: nothing global to sweep).
 */
public class Burrow extends Ability {

    public static final int CD_SECONDS = 20;
    public static final int RANGE = 16;

    public Burrow() {
        super(UnitAction.BURROW, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, 0, true, false);
        this.showRangeLine = true;
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Burrow",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/iron_pickaxe.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.BURROW,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.BURROW),
            null,
            List.of(
                FormattedCharSequence.forward("Burrow  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Tunnel up to " + RANGE + " blocks: untargetable for "
                    + DrillBurrowerUnit.BURROW_TICKS / 20 + " s, then burst up and throw nearby enemies into the air ("
                    + (int) DrillBurrowerUnit.KNOCK_DAMAGE + " damage).", Style.EMPTY),
                FormattedCharSequence.forward("The ground cracks where it will surface.", Style.EMPTY.withItalic(true))
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
        if (level.isClientSide() || !(unitUsing instanceof DrillBurrowerUnit drill))
            return;
        if (!drill.startBurrow(at))
            return;
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(drill.getId(), this.action, this.cooldownMax);
    }
}
