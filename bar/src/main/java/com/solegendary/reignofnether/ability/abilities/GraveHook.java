package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.TitheBinderUnit;
import com.solegendary.reignofnether.unit.units.monsters.TitheLedger;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * <b>Grave Hook</b>, the Tithe Binder's active (design sheet 5.7): his own rusted hook (slot 1) thrown on its chain at
 * one enemy unit within {@link #RANGE} blocks, {@link #CD_SECONDS} s cooldown. It wraps the target and drags it
 * {@link TitheLedger#DRAG_BLOCKS} blocks toward him over {@link TitheLedger#DRAG_TICKS} ticks, then it stays stunned
 * {@link TitheLedger#STUN_TICKS} ticks more; a heavy (max health >= 150 or a hitbox >= 1.4 wide - the design's
 * "population >= 3", this game has no population) is rooted {@link TitheLedger#ROOT_TICKS} ticks instead and the
 * Binder is pulled 1 block toward it. Works at one weapon: the fresh Binder's reason to exist. The rule lives in
 * {@link TitheBinderUnit#startGraveHook}; a refusal (not an enemy, out of reach, a hook already out) costs no
 * cooldown. Autocast on an enemy in range, like the other single-target strikes.
 */
public class GraveHook extends Ability {

    public static final int CD_SECONDS = TitheLedger.HOOK_COOLDOWN_SECONDS;
    public static final int RANGE = TitheLedger.HOOK_RANGE;
    public static final String MSG_BAD_TARGET = "hud.reignofnether.grave_hook_bad_target";
    public static final String MSG_OUT_OF_RANGE = "hud.reignofnether.grave_hook_out_of_range";

    public GraveHook() {
        super(UnitAction.GRAVE_HOOK, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, 0, true, true);
        this.autoRule(AutoRule.ENEMY_IN_RANGE);
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Grave Hook",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/chain.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.GRAVE_HOOK,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.GRAVE_HOOK),
            () -> toggleAutocast(unit),
            List.of(
                FormattedCharSequence.forward("Grave Hook  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Hooks an enemy unit within " + RANGE + " blocks and drags it "
                    + (int) TitheLedger.DRAG_BLOCKS + " blocks closer, stunned. A heavy unit is rooted instead.", Style.EMPTY),
                FormattedCharSequence.forward("The chain shows who outweighs whom.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    /** The AutoCaster picks only what the cast would accept. */
    @Override
    public boolean autocastTargetOk(Unit caster, LivingEntity target) {
        return caster instanceof TitheBinderUnit binder && binder.graveHookRefusal(target) == null;
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        if (level.isClientSide() || !(unitUsing instanceof TitheBinderUnit binder) || targetEntity == null)
            return;
        String why = binder.startGraveHook(targetEntity);
        if (why == null) {
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(binder.getId(), this.action, this.cooldownMax);
        } else if (!why.isEmpty()) {
            HudClientboundPacket.showTempMessageI18n(binder.getOwnerName(), why);
        }
    }

    /** Ground clicks do nothing: the hook needs a unit. */
    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) { }
}
