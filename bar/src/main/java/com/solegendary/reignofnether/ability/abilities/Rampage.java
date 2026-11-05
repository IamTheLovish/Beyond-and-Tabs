package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.piglins.WorldbreakerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Ironhide Horde (Worldbreaker): <b>Rampage</b> (hero-and-tier4-design.md, slice H5a). Aim a spot: the Worldbreaker
 * charges toward it for at most {@link WorldbreakerUnit#RAMPAGE_TICKS} ticks (3 s), every enemy ground unit it passes
 * takes {@link WorldbreakerUnit#RAMPAGE_DAMAGE} and is thrown aside (no stun, no root), every enemy building it touches is
 * dented. The charge itself lives on the unit (its tick drives it, as the War Mammoth's Trample does); this is the button
 * and the order. Un-hosted, the AutoCaster charges only on an attack-move with at least {@link #AUTO_MIN_AHEAD} enemies
 * ahead in the lane.
 */
public class Rampage extends Ability {

    public static final int CD_SECONDS = 20;
    public static final float RANGE = 24f;
    /** The lane the autocast counts enemies in: as long as the charge can run, this wide each side. */
    public static final double LANE_LENGTH = RANGE;
    public static final double LANE_HALF_WIDTH = 3.0;
    public static final int AUTO_MIN_AHEAD = 5;

    public Rampage() {
        super(UnitAction.RAMPAGE, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, 0, true, false);
        this.autoRule(AutoRule.ENEMY_IN_RANGE);   // gated by autocastTargetOk: an attack-move with 5 or more enemies ahead
        this.showRangeLine = true;
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Rampage",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/netherite_axe.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.RAMPAGE,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.RAMPAGE),
            null,
            List.of(
                FormattedCharSequence.forward("Rampage  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Charges toward the spot for up to 3 s: enemies in the way take "
                    + (int) WorldbreakerUnit.RAMPAGE_DAMAGE + " and are thrown aside, buildings are dented.", Style.EMPTY),
                FormattedCharSequence.forward("A wall stops it.", Style.EMPTY.withItalic(true))
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
        if (targetBp != null)
            start(level, unitUsing, Vec3.atBottomCenterOf(targetBp));
    }

    void start(Level level, Unit unitUsing, Vec3 at) {
        if (level.isClientSide() || !(unitUsing instanceof WorldbreakerUnit wb))
            return;
        if (!wb.startRampage(at))
            return;
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(wb.getId(), this.action, this.cooldownMax);
    }

    /** The AutoCaster's gate: only on an attack-move, and at least {@link #AUTO_MIN_AHEAD} enemy ground units in the lane toward the target. */
    @Override
    public boolean autocastTargetOk(Unit caster, LivingEntity target) {
        if (!(caster instanceof WorldbreakerUnit wb) || !(wb.level() instanceof ServerLevel sl) || !WorldbreakerUnit.isGroundTarget(target))
            return false;
        if (wb.getAttackMoveTarget() == null)
            return false;
        Vec3 dir = Earthsplitter.lineDir(wb, target.position());
        Vec3 from = wb.position();
        List<LivingEntity> near = WorldbreakerUnit.groundFoes(sl, wb.getOwnerName(), from.x + dir.x * LANE_LENGTH / 2,
            from.z + dir.z * LANE_LENGTH / 2, LANE_LENGTH / 2 + LANE_HALF_WIDTH + 1);
        int ahead = 0;
        for (LivingEntity le : near) {
            double rx = le.getX() - from.x, rz = le.getZ() - from.z;
            double along = rx * dir.x + rz * dir.z, perp = Math.abs(rx * dir.z - rz * dir.x);
            if (along >= 2 && along <= LANE_LENGTH && perp <= LANE_HALF_WIDTH)
                ahead++;
        }
        return ahead >= AUTO_MIN_AHEAD;
    }
}
