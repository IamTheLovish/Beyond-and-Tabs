package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.barfx.BarFx;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.essence.Essence;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitTier;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.piglins.SpiritPuppets;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * <b>Spirit Puppet</b>, the Voodoo Shaman's big essence spell (design/essence_casters.md, lovish's draft 2): one enemy
 * T1 unit ({@link UnitTier}) within {@link #RANGE} becomes the caster's owner's for {@link #DURATION_SECONDS} s, then
 * goes back to its owner ({@link SpiritPuppets} keeps the record, the clock and the hand-back), for
 * {@link #ESSENCE_COST} essence and a {@link #CD_SECONDS} s cooldown. Refused - nothing spent, no cooldown - on a T2+
 * unit, a hero or commander, an ally, a puppet already, a unit someone possesses, a garrisoned, mounted or carrying
 * unit, a summon ({@link SpiritPuppets#mayTake}), a unit out of reach, or when the caster is short of essence.
 * A channel (Revision 1): the puppet goes back at once if the shaman is hit, stunned or more than
 * {@link #CHANNEL_RANGE} blocks from it. Manual: the player picks the one unit worth 20 essence.
 */
public class SpiritPuppet extends Ability {

    public static final int ESSENCE_COST = 20;
    public static final int CD_SECONDS = 60;
    public static final int DURATION_SECONDS = 15;
    public static final int RANGE = 8;
    public static final int MAX_TIER = 1;
    /** Revision 1: the channel breaks when the shaman is more than this far from its puppet. */
    public static final int CHANNEL_RANGE = 16;
    public static final String MSG_BAD_TARGET = "hud.essence.reignofnether.puppet_bad_target";
    public static final String MSG_TIER = "hud.essence.reignofnether.puppet_tier";

    public SpiritPuppet() {
        super(UnitAction.SPIRIT_PUPPET, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, 0, true, true);
        this.autoRule(AutoRule.MANUAL);
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Spirit Puppet",
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/abilities/possess.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.SPIRIT_PUPPET,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.SPIRIT_PUPPET),
            null,
            List.of(
                FormattedCharSequence.forward("Spirit Puppet  (" + ESSENCE_COST + " essence, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Takes control of an enemy tier 1 unit within " + RANGE + " blocks for "
                    + DURATION_SECONDS + " s, then it goes back to its owner.", Style.EMPTY),
                FormattedCharSequence.forward("A channel: it breaks if the shaman is hit, stunned or more than " + CHANNEL_RANGE + " blocks away.", Style.EMPTY),
                FormattedCharSequence.forward("Dance, little one.", Style.EMPTY.withItalic(true))
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

    /** Ground clicks do nothing: Spirit Puppet needs a unit. */
    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) { }

    /**
     * The rule: checks the target (a living T1 enemy within reach, not a puppet already), then spends the essence, then
     * takes it - so a refusal never costs essence. True when cast; no cooldown handling here (the bot and the tests).
     */
    public static boolean cast(ServerLevel sl, LivingEntity caster, String owner, LivingEntity target, boolean quiet) {
        if (owner == null || owner.isBlank() || target == null || !target.isAlive() || !(target instanceof Unit u)
                || u.getOwnerName().isBlank() || AlliancesServerEvents.isAlliedOrOwned(u.getOwnerName(), owner)
                || SpiritPuppets.isPuppet(target) || !SpiritPuppets.mayTake(target)) {
            if (!quiet && owner != null)
                HudClientboundPacket.showTempMessageI18n(owner, MSG_BAD_TARGET);
            return false;
        }
        if (UnitTier.of(target) > MAX_TIER) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, MSG_TIER);
            return false;
        }
        double reach = RANGE + target.getBbWidth() / 2 + 0.5;
        if (caster.distanceToSqr(target) > reach * reach)
            return false;
        if (!Essence.trySpend(caster, ESSENCE_COST)) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, Empower.MSG_SHORT);
            return false;
        }
        SpiritPuppets.take(sl, caster, target, owner, DURATION_SECONDS * ResourceCost.TICKS_PER_SECOND);
        BarFx.shot(sl, caster.getEyePosition(), target.getEyePosition(), BarFx.K_MAGIC, caster);
        return true;
    }
}
