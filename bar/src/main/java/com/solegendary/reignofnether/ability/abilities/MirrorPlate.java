package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.storm.JadeSentinelUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Stormcallers (Jade Sentinel): <b>Mirror Plate</b> (second-t3-design.md). Raises the jade plate for
 * {@link #DURATION_TICKS} ticks: ranged projectiles that strike its front arc are turned back on their shooter instead
 * of hurting it ({@link JadeSentinelUnit#hurt}), and it walks at about half speed meanwhile. Melee, spells and anything
 * from behind pass the plate. {@link #CD_SECONDS} s cooldown (counted from the cast), no energy.
 */
public class MirrorPlate extends Ability {

    public static final int CD_SECONDS = 20;
    public static final int DURATION_TICKS = 4 * 20;
    /** Slowness III: about half speed. */
    public static final int SLOW_AMPLIFIER = 2;

    public MirrorPlate() {
        super(UnitAction.MIRROR_PLATE, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, 0, 0, false, true);
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Mirror Plate",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/shield.png"),
            hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> UnitClientEvents.sendUnitCommand(UnitAction.MIRROR_PLATE),
            null,
            List.of(
                FormattedCharSequence.forward("Mirror Plate  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("For " + DURATION_TICKS / 20 + " s, arrows and bolts that hit the Sentinel's front are "
                    + "sent back at their shooter. It walks at half speed meanwhile.", Style.EMPTY),
                FormattedCharSequence.forward("Melee, spells and shots from behind get through.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel))
            return;
        if (self instanceof JadeSentinelUnit sentinel)
            sentinel.raiseMirror();
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }
}
