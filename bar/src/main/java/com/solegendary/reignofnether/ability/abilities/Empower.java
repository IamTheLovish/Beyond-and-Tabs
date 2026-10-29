package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.buildings.defence.DefenceTower;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.essence.Essence;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.player.CommanderServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.Summons;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.VeterancyServerEvents;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>Empower</b>, the ability every essence caster shares (design/essence_casters.md; lovish, Oct 29 2026: "essence
 * can also buy veterancy"). Aim at a friendly unit within {@link #RANGE} blocks: it gains one veterancy rank through
 * the kill path's own {@link VeterancyServerEvents#award} (+10% max health and damage per rank, at most
 * {@link VeterancyServerEvents#MAX_RANK}) for {@link #ESSENCE_COST} of the caster's essence, {@link #CD_SECONDS} s
 * cooldown. Manual: essence is scarce, the player picks who earns a rank.
 * <p>
 * Revision 1 (the Oct 29 design review: three casters made a Sun Colossus rank 3 in one breath, a price the kill path
 * puts at 5520 metal of kills) limits it: a 20 s cooldown, one rank per target per {@link #RANK_EVERY_TICKS} (60 s), and
 * never on a hero, a commander, an experimental ({@link DefenceTower#isExperimental}, the anti-T3 tower's rule) or a
 * summon ({@link Summons}). A unit type with a {@link TargetHook} takes the hook's effect instead of the rank, and the
 * hook's own {@link TargetHook#accepts} replaces those refusals (the Grave Adept, a later PR: 120 XP, not a level).
 * <p>
 * Every refusal - an enemy, out of reach, too soon, a refused kind, the top rank, short of essence - spends nothing
 * and sets no cooldown, so the AutoCaster's "a refusal is not a cast" holds. Essence is spent last.
 */
public class Empower extends Ability {

    public static final int ESSENCE_COST = 15;
    public static final int RANGE = 6;
    /** Revision 1: 20 s (lovish's draft named only the cost and reach). */
    public static final int CD_SECONDS = 20;
    /** Revision 1: a target takes at most one Empower per this many ticks (60 s), whoever casts it. */
    public static final int RANK_EVERY_TICKS = 60 * ResourceCost.TICKS_PER_SECOND;
    /** The game tick a unit was last empowered (persistent data, saved with it). */
    public static final String KEY_LAST = "bt_empowered_at";
    public static final String MSG_BAD_TARGET = "hud.essence.reignofnether.empower_bad_target";
    public static final String MSG_MAX_RANK = "hud.essence.reignofnether.empower_max_rank";
    public static final String MSG_SHORT = "hud.essence.reignofnether.empower_short";
    public static final String MSG_RECENT = "hud.essence.reignofnether.empower_recent";
    public static final String MSG_NOT_THIS = "hud.essence.reignofnether.empower_not_this";

    /** What Empower does to one unit type instead of a veterancy rank. */
    public interface TargetHook {
        /** Whether Empower may be cast on {@code target} now; asked before any essence is spent. */
        default boolean accepts(LivingEntity target) {
            return true;
        }

        /** The effect, after the essence is paid. */
        void apply(ServerLevel level, LivingEntity caster, LivingEntity target);
    }

    // unit type -> its hook (server thread; set once by the unit's own class, and by the game test)
    private static final Map<EntityType<?>, TargetHook> HOOKS = new HashMap<>();

    public static void setTargetHook(EntityType<?> type, TargetHook hook) {
        HOOKS.put(type, hook);
    }

    public static void clearTargetHook(EntityType<?> type) {
        HOOKS.remove(type);
    }

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
                FormattedCharSequence.forward("Once a minute per unit. Not on heroes, commanders, experimentals or summons.", Style.EMPTY),
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

    /** Whether Empower never takes {@code target} (without a hook): a hero, a commander, an experimental or a summon. */
    public static boolean isRefusedKind(LivingEntity target) {
        return target instanceof HeroUnit || CommanderServerEvents.isCommander(target)
            || DefenceTower.isExperimental(target) || Summons.isSummoned(target);
    }

    /**
     * The rule: checks the target (a living friendly unit within reach, not empowered in the last 60 s, of a kind that
     * takes it and under the top rank - or accepted by its type's hook), then spends the essence, then applies the rank
     * or the hook - so a refusal never costs essence. True when cast. Public for the bot and the game test (no cooldown
     * handling here).
     */
    public static boolean cast(ServerLevel sl, LivingEntity caster, String owner, LivingEntity target, boolean quiet) {
        if (owner == null || target == null || !target.isAlive() || !(target instanceof Unit u)
                || !AlliancesServerEvents.isAlliedOrOwned(u.getOwnerName(), owner)) {
            refuse(owner, MSG_BAD_TARGET, quiet);
            return false;
        }
        double reach = RANGE + target.getBbWidth() / 2 + 0.5;
        if (caster.distanceToSqr(target) > reach * reach)
            return false;
        long now = sl.getGameTime();
        if (target.getPersistentData().contains(KEY_LAST)
                && now - target.getPersistentData().getLong(KEY_LAST) < RANK_EVERY_TICKS) {
            refuse(owner, MSG_RECENT, quiet);
            return false;
        }
        TargetHook hook = HOOKS.get(target.getType());
        if (hook != null) {
            if (!hook.accepts(target)) {
                refuse(owner, MSG_NOT_THIS, quiet);
                return false;
            }
        } else if (isRefusedKind(target)) {
            refuse(owner, MSG_NOT_THIS, quiet);
            return false;
        } else if (VeterancyServerEvents.getRank(target) >= VeterancyServerEvents.MAX_RANK) {
            refuse(owner, MSG_MAX_RANK, quiet);
            return false;
        }
        if (!Essence.trySpend(caster, ESSENCE_COST)) {
            refuse(owner, MSG_SHORT, quiet);
            return false;
        }
        target.getPersistentData().putLong(KEY_LAST, now);
        if (hook != null)
            hook.apply(sl, caster, target);
        else
            VeterancyServerEvents.award(target, 1f);   // +1 experience is exactly one more rank
        sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
            16, 0.3, 0.5, 0.3, 0.03);
        com.solegendary.reignofnether.barfx.BarFx.shot(sl, caster.getEyePosition(), target.getEyePosition(),
            com.solegendary.reignofnether.barfx.BarFx.K_MAGIC, caster);
        return true;
    }

    private static void refuse(String owner, String msg, boolean quiet) {
        if (!quiet && owner != null)
            HudClientboundPacket.showTempMessageI18n(owner, msg);
    }
}
