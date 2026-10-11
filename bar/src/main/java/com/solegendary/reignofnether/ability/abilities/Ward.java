package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.mana.ManaServerEvents;
import com.solegendary.reignofnether.mana.WardServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourcesClientboundPacket;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
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
 * Stormcallers commander (the Thunder Sage): <b>Ward</b> (design/stormcallers_plan.md, slice 2) - its third button, next
 * to Blink and Heaven's Judgement (the Fog Bank precedent: CommanderAbility holds one Kind, extra powers are their own
 * abilities; granted by CommanderServerEvents.ensureAbility to Mana-faction commanders). Instant: a gold sigil of radius
 * {@link WardServerEvents#RADIUS} where the Sage stands, for up to {@link WardServerEvents#DURATION_TICKS} ticks -
 * friends inside take {@link WardServerEvents#REDUCTION} less projectile and spell damage and can't be sealed. Channelled
 * at {@link WardServerEvents#ENERGY_PER_SECOND} energy a second (collapses early when starved). {@link #CD_SECONDS} s
 * cooldown, started only when the ward rises.
 */
public class Ward extends Ability {

    public static final int CD_SECONDS = 40;

    public Ward() {
        super(UnitAction.WARD, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, 0, WardServerEvents.RADIUS, false, true);
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Ward",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/gold_nugget.png"),
            hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> UnitClientEvents.sendUnitCommand(UnitAction.WARD),
            null,
            List.of(
                FormattedCharSequence.forward("Ward  (commander, " + (int) WardServerEvents.ENERGY_PER_SECOND + " energy/s, "
                    + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("A sigil " + (int) WardServerEvents.RADIUS + " blocks round the Sage for "
                    + WardServerEvents.DURATION_TICKS / 20 + " s: friends inside take "
                    + Math.round(WardServerEvents.REDUCTION * 100) + "% less projectile and spell damage and can't be sealed.", Style.EMPTY),
                FormattedCharSequence.forward("Drains energy every second; ends early when the pool runs dry.", Style.EMPTY),
                FormattedCharSequence.forward("Ink and arrows break on the same gold line.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        use(level, unitUsing, (BlockPos) null);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl))
            return;
        if (cast(sl, self, unitUsing.getOwnerName(), false) != null) {
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        }
    }

    /**
     * Raises the ward under the Sage. Refused (null, nothing paid) when the first second can't be paid; the owner sees the
     * usual "not enough energy" warning unless {@code quiet}. Public for the bot and the game test (no cooldown here).
     */
    public static WardServerEvents.Ward cast(ServerLevel sl, LivingEntity sage, String owner, boolean quiet) {
        WardServerEvents.Ward w = WardServerEvents.raise(sl, sage, owner, sage.position());
        if (w == null && !quiet && ManaServerEvents.lastRefusal() == ManaServerEvents.Refusal.NOT_ENOUGH_ENERGY)
            ResourcesClientboundPacket.warnInsufficientResources(owner, true, false, true, true);
        return w;
    }
}
