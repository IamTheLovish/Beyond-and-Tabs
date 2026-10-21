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
import com.solegendary.reignofnether.unit.units.tide.LeviathanUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Leviathan: <b>Deep Dive</b> (claude/t3-roles.md: the Amphibious flanker surfaces behind the front). From the water
 * (a river, the coast, a tidepool) the Leviathan sinks out of sight and swims under the surface toward a spot up to
 * {@link LeviathanUnit#DIVE_RANGE} blocks away - unseen, untargetable, unhurt - and surfaces at the water's edge nearest
 * the spot with a tidal slam on everything round it. Bubbles trace the way and boil at the landing for the last second:
 * the enemy who watches the river can step back. The dive itself lives on the Leviathan (its tick drives it); this is
 * the button and the order. {@link #CD_SECONDS} s cooldown. Manual: it needs a chosen spot and a river to use.
 */
public class DeepDive extends Ability {

    public static final int CD_SECONDS = 40;

    public DeepDive() {
        super(UnitAction.DEEP_DIVE, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, LeviathanUnit.DIVE_RANGE, 0, false, true);
        this.autoRule(AutoRule.MANUAL);   // autocast: a flank needs a chosen spot and the water to reach it
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Deep Dive",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/heart_of_the_sea.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.DEEP_DIVE,
            () -> false,
            () -> !(unit instanceof LeviathanUnit l && l.isDiving()),   // greyed while under the surface
            () -> CursorClientEvents.setLeftClickAction(UnitAction.DEEP_DIVE),
            null,
            List.of(
                FormattedCharSequence.forward("Deep Dive  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("From the water: dives out of sight and swims up to " + (int) LeviathanUnit.DIVE_RANGE
                    + " blocks under the surface, then surfaces at the water's edge nearest the spot with a tidal slam ("
                    + (int) TidalSlam.DAMAGE + " to everything round it).", Style.EMPTY),
                FormattedCharSequence.forward("Only from a river, the coast or a tidepool. Bubbles show the way and boil where it will rise.", Style.EMPTY.withItalic(true))
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
        if (level.isClientSide() || !(unitUsing instanceof LeviathanUnit leviathan))
            return;
        if (!leviathan.startDive(at))
            return;   // not in the water, already under, or nowhere wet to surface: not a cast, no cooldown
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(leviathan.getId(), this.action, this.cooldownMax);
    }
}
