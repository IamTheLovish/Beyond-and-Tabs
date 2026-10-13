package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.mana.ManaServerEvents;
import com.solegendary.reignofnether.mana.SealServerEvents;
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
 * Stormcallers Ink Scribe: <b>Seal</b> (design/stormcallers_plan.md, slice 2). Aim at an enemy unit within
 * {@link #RANGE} blocks: it is sealed - it can't use abilities - for {@link SealServerEvents#DURATION_TICKS} ticks (half
 * that on a commander; x1.5 while overcharged). {@link #ENERGY_COST} energy through the Mana core, {@link #CD_SECONDS} s
 * cooldown. Refused (not an enemy, out of reach, inside a friendly Ward, no energy): nothing paid, cooldown untouched.
 */
public class Seal extends Ability {

    public static final int CD_SECONDS = 20;
    public static final int RANGE = 10;
    public static final int ENERGY_COST = 25;

    public Seal() {
        super(UnitAction.SEAL, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, 0, true, true);
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Seal",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/ink_sac.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.SEAL,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.SEAL),
            null,
            List.of(
                FormattedCharSequence.forward("Seal  (" + ENERGY_COST + " energy, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("An enemy unit up to " + RANGE + " blocks away can't use abilities for "
                    + SealServerEvents.DURATION_TICKS / 20 + " s (commanders " + SealServerEvents.COMMANDER_DURATION_TICKS / 20 + " s).", Style.EMPTY),
                FormattedCharSequence.forward("It still moves and fights. A Ward protects its allies.", Style.EMPTY),
                FormattedCharSequence.forward("One stroke, and the spell is unwritten.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        if (level.isClientSide() || !(level instanceof ServerLevel sl) || !(unitUsing instanceof LivingEntity self) || targetEntity == null)
            return;
        if (cast(sl, self, unitUsing.getOwnerName(), targetEntity, false)) {
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        }
    }

    /** Ground clicks (a hidden target is cast at the ground) do nothing: a seal needs a unit. */
    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) { }

    /**
     * Seals {@code target}: checks it first (an enemy unit in reach, not warded), then pays. Returns false when refused
     * (nothing paid). Public for the bot and the game test (no cooldown handling here).
     */
    public static boolean cast(ServerLevel sl, LivingEntity scribe, String owner, LivingEntity target, boolean quiet) {
        if (owner == null || !SealServerEvents.canSeal(owner, target)) {
            if (!quiet && target != null && com.solegendary.reignofnether.mana.WardServerEvents.protects(target))
                HudClientboundPacket.showTempMessageI18n(owner, SealServerEvents.MSG_WARDED);
            return false;
        }
        double reach = RANGE + target.getBbWidth() / 2 + 0.5;
        if (scribe.distanceToSqr(target) > reach * reach)
            return false;
        float power = quiet ? ManaServerEvents.tryCastQuiet(scribe, owner, ENERGY_COST) : ManaServerEvents.tryCast(scribe, owner, ENERGY_COST);
        if (power <= 0)
            return false;
        SealServerEvents.seal(sl, target, power);
        // a brush stroke from the scribe to its target
        com.solegendary.reignofnether.barfx.BarFx.shot(sl, scribe.getEyePosition(), target.getEyePosition(),
            com.solegendary.reignofnether.barfx.BarFx.K_MAGIC, scribe);
        return true;
    }
}
