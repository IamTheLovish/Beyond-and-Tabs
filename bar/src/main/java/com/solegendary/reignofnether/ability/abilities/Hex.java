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
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitTier;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * <b>Hex</b>, the Voodoo Shaman's cheap essence spell (design/essence_casters.md, lovish's draft 2): one enemy unit of
 * tier 1-2 ({@link UnitTier}) within {@link #RANGE} can neither attack nor cast for {@link #DURATION_SECONDS} s, for
 * {@link #ESSENCE_COST} essence and a {@link #CD_SECONDS} s cooldown. The hex is two existing effects for the same time:
 * DISARM (AttackerUnit's attack cooldown never runs out, and VoodooServerEvents cancels any blow it still lands) and the
 * Ink Scribe's SEALED (every ability gate already refuses a sealed unit). Refused - nothing spent, no cooldown - on a T3
 * unit, a hero or commander, an ally, a unit out of reach or one already hexed, or when the caster is short of essence.
 * Autocast on the nearest valid enemy (ENEMY_IN_RANGE), like the other single-target strikes.
 */
public class Hex extends Ability {

    public static final int ESSENCE_COST = 5;
    public static final int CD_SECONDS = 12;
    public static final int DURATION_SECONDS = 4;
    public static final int RANGE = 8;
    public static final int MAX_TIER = 2;
    public static final String MSG_BAD_TARGET = "hud.essence.reignofnether.hex_bad_target";
    public static final String MSG_TIER = "hud.essence.reignofnether.hex_tier";

    public Hex() {
        super(UnitAction.HEX, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, 0, true, true);
        this.autoRule(AutoRule.ENEMY_IN_RANGE);
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Hex",
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/abilities/curse_of_insomnia.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.HEX,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.HEX),
            () -> toggleAutocast(unit),
            List.of(
                FormattedCharSequence.forward("Hex  (" + ESSENCE_COST + " essence, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("An enemy unit of tier 1 or 2 within " + RANGE + " blocks can neither attack nor cast for "
                    + DURATION_SECONDS + " s.", Style.EMPTY),
                FormattedCharSequence.forward("The bones say no.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    /** The AutoCaster picks only what the cast would accept: a T1-T2 enemy not hexed already. */
    @Override
    public boolean autocastTargetOk(Unit caster, LivingEntity target) {
        return canHex(caster.getOwnerName(), target) == null;
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

    /** Ground clicks do nothing: Hex needs a unit. */
    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) { }

    /** Why {@code target} cannot be hexed by {@code owner} (a HUD message key), or null when it can. */
    static String canHex(String owner, LivingEntity target) {
        if (owner == null || target == null || !target.isAlive() || !(target instanceof Unit u)
                || AlliancesServerEvents.isAlliedOrOwned(u.getOwnerName(), owner) || isHexed(target))
            return MSG_BAD_TARGET;
        if (UnitTier.of(target) > MAX_TIER)
            return MSG_TIER;
        return null;
    }

    public static boolean isHexed(LivingEntity e) {
        return e.hasEffect(MobEffectRegistrar.DISARM.get());
    }

    /**
     * The rule: checks the target (a living T1-T2 enemy within reach, not hexed already), then spends the essence, then
     * hexes - so a refusal never costs essence. True when cast; no cooldown handling here (the bot and the tests).
     */
    public static boolean cast(ServerLevel sl, LivingEntity caster, String owner, LivingEntity target, boolean quiet) {
        if (true) return false;   // STUB (tests-first commit): the real rule lands with the implementation
        String why = canHex(owner, target);
        if (why != null) {
            if (!quiet && owner != null)
                HudClientboundPacket.showTempMessageI18n(owner, why);
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
        int ticks = DURATION_SECONDS * ResourceCost.TICKS_PER_SECOND;
        target.addEffect(new MobEffectInstance(MobEffectRegistrar.DISARM.get(), ticks, 0, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffectRegistrar.SEALED.get(), ticks, 0, false, true, true));
        sl.sendParticles(ParticleTypes.WITCH, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
            18, 0.3, 0.4, 0.3, 0.02);
        BarFx.shot(sl, caster.getEyePosition(), target.getEyePosition(), BarFx.K_MAGIC, caster);
        return true;
    }
}
