package com.solegendary.reignofnether.unit.support;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.abilities.ThrowHealingPotion;
import com.solegendary.reignofnether.ability.abilities.ThrowLingeringHarmingPotion;
import com.solegendary.reignofnether.ability.abilities.ThrowLingeringRegenPotion;
import com.solegendary.reignofnether.ability.abilities.ThrowWaterPotion;
import com.solegendary.reignofnether.mana.SealServerEvents;
import com.solegendary.reignofnether.unit.goals.ThrowPotionGoal;
import com.solegendary.reignofnether.unit.support.SupportState.Mode;
import com.solegendary.reignofnether.unit.units.villagers.WitchUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;

import javax.annotation.Nullable;

import static com.solegendary.reignofnether.unit.support.SupportField.*;

/**
 * The Field Medic's potions on the support brain's look (claude/healer-ai-oct21.md, section C). The four potions share
 * one cooldown (a throw puts all four on theirs), so the order of the choice is the medic's priorities:
 * <ol>
 *   <li>an attack order on an enemy: a harming throw at it - only when no friend stands, or will stand in a second,
 *       within the cloud and a margin (SupportMath.harmSafeAt); otherwise she holds her standoff;</li>
 *   <li>a regeneration cloud on two or more hurt friends, never where the enemies under it outnumber them;</li>
 *   <li>a healing splash on the friend with the most missing health x value, never next to an undead friend (it
 *       would hurt it), herself only below half health;</li>
 *   <li>water on a burning friend or a fire nearby (unchanged);</li>
 *   <li>harming only where it touches no friend: aimed a block beyond the enemy cluster, away from our side. No safe
 *       spot, no harming.</li>
 * </ol>
 * The old goal threw harming first, at whichever enemy the entity query listed first, onto allies in melee with it,
 * and healed only when fully idle (healer-ai root causes 2 and 3). A click still throws exactly where clicked.
 */
public final class MedicPotions {
    private MedicPotions() { }

    /** A friend must miss at least this much health for a healing throw (not a scratch: the cooldown is shared). */
    public static final float HEAL_MIN_MISSING = 3f;
    /** How far past her throw range she walks for a heal: idle, and under a support order. */
    public static final double IDLE_WALK = 4.0, ORDER_WALK = 2.0;
    /** Fires on the ground are looked for once a second (a 20 x 8 x 20 block scan). */
    static final int FIRE_SCAN_TICKS = 20;

    private static final SupportMath.Aim AIM = new SupportMath.Aim();
    private static final double[] OURS = new double[2];

    /** From WitchUnit.supportThink, on SupportBrain's look ({@code f} holds the units round her). */
    public static void think(ServerLevel level, WitchUnit witch, SupportField f, long now) {
        ThrowPotionGoal goal = witch.getThrowPotionGoal();
        SupportState s = witch.getSupportState();
        if (goal == null || goal.isBusy() || now < s.kiteUntil || SealServerEvents.isSealed(witch))
            return;
        int me = f.self();
        if (me < 0)
            return;
        double sx = f.x[me], sz = f.z[me];
        double range = witch.getPotionThrowRange();

        // 1. the attack order
        if (s.mode == Mode.STANDOFF_UNIT && s.orderTarget != null) {
            ThrowLingeringHarmingPotion harm = ability(witch, ThrowLingeringHarmingPotion.class);
            LivingEntity t = s.orderTarget;
            if (harm != null && harm.isOffCooldown(witch) && t.getMobType() != MobType.UNDEAD
                    && SupportMath.harmSafeAt(f, t.getX(), t.getZ())) {
                goal.selfThrow(harm.potion, t);
                return;
            }
        }
        if (!SupportBrain.canAutocastNow(witch, witch))
            return;
        boolean planted = witch.getHoldPosition() || witch.isGarrisoned();
        double healReach = planted ? range : range + (s.mode != Mode.NONE ? ORDER_WALK : IDLE_WALK);

        // 2. regeneration on a cluster
        ThrowLingeringRegenPotion regen = ability(witch, ThrowLingeringRegenPotion.class);
        if (ready(level, witch, regen) && SupportMath.regenAim(f, sx, sz, healReach, HEAL_MIN_MISSING, AIM)) {
            SupportBrain.armHome(witch, witch);
            goal.selfThrow(regen.potion, groundAt(f, AIM.index, AIM.x, AIM.z), AIM.x, AIM.z);
            return;
        }
        // 3. a healing splash
        ThrowHealingPotion heal = ability(witch, ThrowHealingPotion.class);
        if (ready(level, witch, heal)) {
            int t = SupportMath.healTarget(f, sx, sz, healReach, HEAL_MIN_MISSING);
            if (t >= 0) {
                SupportBrain.armHome(witch, witch);
                goal.selfThrow(heal.potion, (LivingEntity) f.ref[t]);
                return;
            }
        }
        // 4. water (the old rule: the burning friend lowest on health, else a fire block nearby)
        ThrowWaterPotion water = ability(witch, ThrowWaterPotion.class);
        if (ready(level, witch, water)) {
            int t = burningFriend(f, sx, sz, range + IDLE_WALK);
            if (t >= 0) {
                goal.selfThrow(water.potion, (LivingEntity) f.ref[t]);
                return;
            }
            if ((witch.tickCount + witch.getId()) % FIRE_SCAN_TICKS == 0) {
                BlockPos fire = goal.findNearbyFireBlock();
                if (fire != null) {
                    goal.selfThrow(water.potion, fire, fire.getX() + 0.5, fire.getZ() + 0.5);
                    return;
                }
            }
        }
        // 5. harming, only where no friend is
        ThrowLingeringHarmingPotion harm = ability(witch, ThrowLingeringHarmingPotion.class);
        if (ready(level, witch, harm)) {
            if (SupportMath.centroid(f, F_FRIEND, F_SELF | F_SUPPORT, sx, sz, 1e4, OURS) == 0) {
                OURS[0] = sx;
                OURS[1] = sz;
            }
            if (SupportMath.harmingAim(f, sx, sz, range, OURS[0], OURS[1], AIM))
                goal.selfThrow(harm.potion, groundAt(f, AIM.index, AIM.x, AIM.z), AIM.x, AIM.z);
        }
    }

    static boolean ready(ServerLevel level, WitchUnit witch, @Nullable Ability a) {
        return a != null && a.isAutocasting(witch) && a.isOffCooldown(witch) && a.canAutocastNow(level, witch);
    }

    @Nullable
    static <T extends Ability> T ability(WitchUnit witch, Class<T> type) {
        for (Ability a : witch.getAbilities().get())
            if (type.isInstance(a))
                return type.cast(a);
        return null;
    }

    /** The block under the aim, at the height of the unit the aim was built on (the throw goal walks to it if needed). */
    static BlockPos groundAt(SupportField f, int index, double x, double z) {
        int y = ((Entity) f.ref[index]).getOnPos().getY();
        return BlockPos.containing(x, y, z);
    }

    /** The burning friend lowest on health within {@code reach}, or -1. */
    static int burningFriend(SupportField f, double sx, double sz, double reach) {
        int best = -1;
        float bestFrac = Float.MAX_VALUE;
        for (int i = 0; i < f.n; i++) {
            if ((f.flags[i] & (F_FRIEND | F_BURNING)) != (F_FRIEND | F_BURNING))
                continue;
            if (SupportMath.d2(f.x[i], f.z[i], sx, sz) > reach * reach)
                continue;
            float frac = f.health[i] / Math.max(1f, f.maxHealth[i]);
            if (frac < bestFrac) {
                bestFrac = frac;
                best = i;
            }
        }
        return best;
    }
}
