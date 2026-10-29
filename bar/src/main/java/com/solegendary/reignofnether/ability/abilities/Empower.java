package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.essence.Essence;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.VeterancyServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * <b>Empower</b>, the ability every essence caster shares (design/essence_casters.md; lovish, Oct 29 2026: "essence
 * can also buy veterancy"). Aim at a friendly unit within {@link #RANGE} blocks: it gains one veterancy rank through
 * the kill path's own {@link VeterancyServerEvents#award} (+10% max health and damage per rank, at most
 * {@link VeterancyServerEvents#MAX_RANK}) for {@link #ESSENCE_COST} of the caster's essence, {@link #CD_SECONDS} s
 * cooldown. Refused - nothing spent, no cooldown, so the AutoCaster's "a refusal is not a cast" holds - on an enemy,
 * out of reach, at the top rank, or when the caster is short of essence. Manual: essence is scarce, the player picks
 * who earns a rank.
 */
public class Empower extends Ability {

    public static final int ESSENCE_COST = 15;
    public static final int RANGE = 6;
    /** Not in lovish's draft (only the cost and reach were): a placeholder for him to tune. */
    public static final int CD_SECONDS = 10;
    public static final String MSG_BAD_TARGET = "hud.essence.reignofnether.empower_bad_target";
    public static final String MSG_MAX_RANK = "hud.essence.reignofnether.empower_max_rank";
    public static final String MSG_SHORT = "hud.essence.reignofnether.empower_short";
    /** Revision 1: a target takes at most one Empower per this many ticks (60 s). */
    public static final int RANK_EVERY_TICKS = 60 * ResourceCost.TICKS_PER_SECOND;
    /** The game tick a unit was last empowered (persistent data). */
    public static final String KEY_LAST = "bt_empowered_at";

    /** STUB (Revision 1): replaces the rank for one unit type. */
    public interface TargetHook {
        default boolean accepts(LivingEntity target) {
            return true;
        }

        void apply(ServerLevel level, LivingEntity caster, LivingEntity target);
    }

    public static void setTargetHook(net.minecraft.world.entity.EntityType<?> type, TargetHook hook) { }

    public static void clearTargetHook(net.minecraft.world.entity.EntityType<?> type) { }

    public Empower() {
        super(UnitAction.EMPOWER, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, 0, true, true);
        this.autoRule(AutoRule.MANUAL);
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Empower",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/experience_bottle.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.EMPOWER,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.EMPOWER),
            null,
            List.of(
                FormattedCharSequence.forward("Empower  (" + ESSENCE_COST + " essence, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("A friendly unit within " + RANGE + " blocks gains a veterancy rank: +"
                    + Math.round(VeterancyServerEvents.HP_PER_RANK * 100) + "% health and damage, up to rank " + VeterancyServerEvents.MAX_RANK + ".", Style.EMPTY),
                FormattedCharSequence.forward("What the fallen leave behind, the living carry forward.", Style.EMPTY.withItalic(true))
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

    /** Ground clicks do nothing: Empower needs a unit. */
    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) { }

    /**
     * The rule: checks the target (a living friendly unit within reach, under the top rank), then spends the essence,
     * then awards the rank - so a refusal never costs essence. True when cast. Public for the bot and the game test
     * (no cooldown handling here).
     */
    public static boolean cast(ServerLevel sl, LivingEntity caster, String owner, LivingEntity target, boolean quiet) {
        if (owner == null || target == null || !target.isAlive() || !(target instanceof Unit u)
                || !AlliancesServerEvents.isAlliedOrOwned(u.getOwnerName(), owner)) {
            if (!quiet && owner != null)
                HudClientboundPacket.showTempMessageI18n(owner, MSG_BAD_TARGET);
            return false;
        }
        double reach = RANGE + target.getBbWidth() / 2 + 0.5;
        if (caster.distanceToSqr(target) > reach * reach)
            return false;
        if (VeterancyServerEvents.getRank(target) >= VeterancyServerEvents.MAX_RANK) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, MSG_MAX_RANK);
            return false;
        }
        if (!Essence.trySpend(caster, ESSENCE_COST)) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, MSG_SHORT);
            return false;
        }
        VeterancyServerEvents.award(target, 1f);   // +1 experience is exactly one more rank
        sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
            16, 0.3, 0.5, 0.3, 0.03);
        com.solegendary.reignofnether.barfx.BarFx.shot(sl, caster.getEyePosition(), target.getEyePosition(),
            com.solegendary.reignofnether.barfx.BarFx.K_MAGIC, caster);
        return true;
    }
}
