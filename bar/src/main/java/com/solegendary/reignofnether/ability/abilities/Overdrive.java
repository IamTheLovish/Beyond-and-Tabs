package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.engineers.PioneerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Brassworks Pioneer (combat engineer, design/combat_engineers.md): <b>Overdrive</b>. The Pioneer opens its steam rig:
 * x{@link #POWER_MULT} build power for {@link #DRIVE_TICKS} ticks - construction, wreck reclaim and field repair all
 * read build power, so all three speed up - and then the rig vents for {@link #VENT_TICKS} ticks with no work at all.
 * {@link #CD_SECONDS} s cooldown, free. The state lives on the Pioneer ({@link PioneerUnit#getBuildPower}).
 * <p>
 * The Brassworks' Heat (machines that must vent after a burst) on a builder. Not machine repair (the Tinker, the
 * Engineer-Captain and the Repair Crawler mend machines) and not demolition (the Sapper): a burst of build power. Tempo:
 * a tower up before the raiders arrive, a wreck field stripped in seconds. Counter: the steam plume says when, and the
 * vent is the window - hit the half-built site or the crew while the rig cools.
 */
public class Overdrive extends Ability {

    public static final int CD_SECONDS = 30;
    public static final int DRIVE_TICKS = 8 * 20;
    public static final int VENT_TICKS = 4 * 20;
    public static final float POWER_MULT = 3f;

    public Overdrive() {
        super(UnitAction.OVERDRIVE, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, 0, 0, false, false);
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Overdrive",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/blast_furnace_front_on.png"),
            hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> UnitClientEvents.sendUnitCommand(UnitAction.OVERDRIVE),
            null,
            List.of(
                FormattedCharSequence.forward("Overdrive  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward((int) POWER_MULT + "x build power for " + DRIVE_TICKS / 20
                    + " s - building, reclaim and repair - then the rig vents: " + VENT_TICKS / 20 + " s with no work.", Style.EMPTY),
                FormattedCharSequence.forward("Full pressure. Mind the gauge.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        use(level, unitUsing, targetEntity.getOnPos());
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(level instanceof ServerLevel sl) || !(unitUsing instanceof PioneerUnit pioneer))
            return;
        if (pioneer.startOverdrive(sl.getGameTime())) {
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(pioneer.getId(), this.action, this.cooldownMax);
        }
    }

    /** No cursor, no target: it is the Pioneer's own rig. */
    @Override
    public boolean shouldResetBehaviours() {
        return false;
    }
}
