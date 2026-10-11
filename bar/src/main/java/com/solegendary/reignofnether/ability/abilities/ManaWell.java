package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.mana.ManaServerEvents;
import com.solegendary.reignofnether.mana.ManaWellServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Stormcallers Ley Master: <b>Mana Well</b> (design/stormcallers_plan.md section 3.4, slice 3). Aim at the ground within
 * {@link #RANGE} blocks: a well of mana rises there (ManaWellServerEvents). Every spell the Ley Master's side casts
 * within {@link ManaServerEvents#WELL_RADIUS} of it is x{@link ManaServerEvents#WELL_POWER} stronger and
 * x{@link ManaServerEvents#WELL_DISCOUNT} cheaper; at most {@link ManaServerEvents#WELLS_PER_PLAYER} per player, and
 * wells don't stack. Aim at one of your own wells to take it down instead (free). {@link #ENERGY_COST} energy through
 * the Mana core, {@link #CD_SECONDS} s cooldown; refused at the cap or without the energy: nothing paid, no cooldown.
 */
public class ManaWell extends Ability {

    public static final int CD_SECONDS = 20;
    public static final int RANGE = 8;
    public static final int ENERGY_COST = 80;

    public ManaWell() {
        // one click, one well: a box of Ley Masters must not dig a field of them
        super(UnitAction.MANA_WELL, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, ManaServerEvents.WELL_RADIUS, false, true);
        this.showRangeCircle = true;
        this.showRadiusCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Mana Well",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/soul_lantern.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.MANA_WELL,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.MANA_WELL),
            null,
            List.of(
                FormattedCharSequence.forward("Mana Well  (" + ENERGY_COST + " energy, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Raises a well of mana at a spot within " + RANGE + " blocks. Spells cast within "
                    + ManaServerEvents.WELL_RADIUS + " blocks of it are " + Math.round((ManaServerEvents.WELL_POWER - 1) * 100) + "% stronger and "
                    + Math.round((1 - ManaServerEvents.WELL_DISCOUNT) * 100) + "% cheaper.", Style.EMPTY),
                FormattedCharSequence.forward("At most " + ManaServerEvents.WELLS_PER_PLAYER + " wells; they don't stack. Aim at one of yours to take it down.", Style.EMPTY),
                FormattedCharSequence.forward("Where the ley lines cross, the sky listens.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        if (targetEntity != null)
            use(level, unitUsing, targetEntity.getOnPos());
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl) || targetBp == null)
            return;
        if (cast(sl, self, unitUsing.getOwnerName(), targetBp, false)) {
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        }
    }

    /**
     * Raises (or, on an own well, dismantles) a well at {@code at}: checks reach and the cap first, then pays. Returns
     * whether anything happened (nothing paid otherwise). Public for the bot and the game test (no cooldown here).
     */
    public static boolean cast(ServerLevel sl, LivingEntity master, String owner, BlockPos at, boolean quiet) {
        if (owner == null || at == null)
            return false;
        double dx = at.getX() + 0.5 - master.getX(), dz = at.getZ() + 0.5 - master.getZ();
        if (dx * dx + dz * dz > (RANGE + 1.0) * (RANGE + 1.0))
            return false;
        // taking an own well down is free - the cast is checked before paying
        if (ManaWellServerEvents.nearest(sl, owner, at, ManaWellServerEvents.DISMANTLE_RADIUS) != null)
            return ManaWellServerEvents.raise(sl, owner, at, quiet) == null;
        if (ManaServerEvents.wellCount(owner) >= ManaServerEvents.WELLS_PER_PLAYER) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, ManaServerEvents.Refusal.WELL_CAP.langKey);
            return false;
        }
        float power = quiet ? ManaServerEvents.tryCastQuiet(master, owner, ENERGY_COST) : ManaServerEvents.tryCast(master, owner, ENERGY_COST);
        if (power <= 0)
            return false;
        return ManaWellServerEvents.raise(sl, owner, at, quiet) != null;
    }
}
