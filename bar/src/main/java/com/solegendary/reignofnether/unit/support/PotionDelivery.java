package com.solegendary.reignofnether.unit.support;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.abilities.ThrowHealingPotion;
import com.solegendary.reignofnether.ability.abilities.ThrowLingeringHarmingPotion;
import com.solegendary.reignofnether.ability.abilities.ThrowLingeringRegenPotion;
import com.solegendary.reignofnether.ability.abilities.ThrowWaterPotion;
import com.solegendary.reignofnether.unit.goals.ThrowPotionGoal;
import com.solegendary.reignofnether.unit.support.SupportMath.Undead;
import com.solegendary.reignofnether.unit.units.villagers.WitchUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;

import javax.annotation.Nullable;

/**
 * The Field Medic's support casts, delivered by thrown potion - one implementation behind the delivery-agnostic support
 * brain (SupportUnit.castSupport; lovish, Oct 21 23:37: the potions are to be replaced by faction heal and harm
 * abilities, which take this class's place). Each SupportKind is one of her four potion abilities: area harm the
 * lingering harming potion (a cloud of radius {@link #CLOUD_RADIUS}), area heal the lingering regeneration potion, single
 * heal the healing splash ({@link #SPLASH_RADIUS}), cleanse the water potion (fire only). Vanilla's instant health and
 * harming work backwards on the undead (INVERTED); regeneration does nothing to them (IMMUNE). The throw itself -
 * walk into range, wind up, release, check a harm once more - is ThrowPotionGoal's.
 */
public final class PotionDelivery {
    private PotionDelivery() { }

    /** A lingering cloud's radius (ThrownPotionMixin: setRadius(3), kept by AdjustableAreaEffectCloud). */
    public static final double CLOUD_RADIUS = 3.0;
    /** A splash potion's reach (vanilla ThrownPotion.applySplash: entities within 4 blocks). */
    public static final double SPLASH_RADIUS = 4.0;
    /** Fires on the ground are looked for once a second (a 20 x 8 x 20 block scan). */
    static final int FIRE_SCAN_TICKS = 20;

    @Nullable
    static Ability abilityFor(WitchUnit witch, SupportKind kind) {
        for (Ability a : witch.getAbilities().get()) {
            boolean match = switch (kind) {
                case AREA_HARM -> a instanceof ThrowLingeringHarmingPotion;
                case AREA_HEAL -> a instanceof ThrowLingeringRegenPotion;
                case SINGLE_HEAL -> a instanceof ThrowHealingPotion;
                case CLEANSE -> a instanceof ThrowWaterPotion;
            };
            if (match)
                return a;
        }
        return null;
    }

    @Nullable
    static Potion potionOf(@Nullable Ability a) {
        if (a instanceof ThrowLingeringHarmingPotion p)
            return p.potion;
        if (a instanceof ThrowLingeringRegenPotion p)
            return p.potion;
        if (a instanceof ThrowHealingPotion p)
            return p.potion;
        if (a instanceof ThrowWaterPotion p)
            return p.potion;
        return null;
    }

    /** Her potion of this kind is off cooldown and researched (and, for her own autocast, switched on). */
    public static boolean canCast(ServerLevel level, WitchUnit witch, SupportKind kind, boolean autocast) {
        Ability a = abilityFor(witch, kind);
        return a != null && (!autocast || a.isAutocasting(witch)) && a.isOffCooldown(witch) && a.canAutocastNow(level, witch);
    }

    public static double radius(SupportKind kind) {
        return kind == SupportKind.AREA_HARM || kind == SupportKind.AREA_HEAL ? CLOUD_RADIUS : SPLASH_RADIUS;
    }

    public static Undead undeadRule(SupportKind kind) {
        return switch (kind) {
            case SINGLE_HEAL, AREA_HARM -> Undead.INVERTED;
            case AREA_HEAL -> Undead.IMMUNE;
            case CLEANSE -> Undead.NORMAL;
        };
    }

    /** The throw: at the unit (she keeps aiming at it as it moves) or at the spot. A harm is checked again at release. */
    public static boolean cast(WitchUnit witch, SupportKind kind, @Nullable LivingEntity target, double x, double y, double z) {
        ThrowPotionGoal goal = witch.getThrowPotionGoal();
        Potion potion = potionOf(abilityFor(witch, kind));
        if (goal == null || potion == null)
            return false;
        double recheck = kind == SupportKind.AREA_HARM ? CLOUD_RADIUS : 0;
        if (target != null)
            goal.selfThrow(potion, target, kind, recheck);
        else
            goal.selfThrow(potion, BlockPos.containing(x, y - 0.2, z), x, z, kind, recheck);   // the block under the spot
        return true;
    }

    /** Water on a burning block nearby when nothing else needs her (the old water autocast's second job). */
    public static void waterOnFire(ServerLevel level, WitchUnit witch) {
        if ((witch.tickCount + witch.getId()) % FIRE_SCAN_TICKS != 0 || !canCast(level, witch, SupportKind.CLEANSE, true))
            return;
        BlockPos fire = witch.getThrowPotionGoal().findNearbyFireBlock();
        if (fire != null)
            witch.getThrowPotionGoal().selfThrow(Potions.WATER, fire, fire.getX() + 0.5, fire.getZ() + 0.5, SupportKind.CLEANSE, 0);
    }

    /** What a clicked potion counts as (the game tests' counters); null for none of the four. */
    @Nullable
    public static SupportKind kindOf(@Nullable Potion potion) {
        if (potion == Potions.STRONG_HEALING || potion == Potions.HEALING)
            return SupportKind.SINGLE_HEAL;
        if (potion == Potions.STRONG_REGENERATION || potion == Potions.REGENERATION)
            return SupportKind.AREA_HEAL;
        if (potion == Potions.STRONG_HARMING || potion == Potions.HARMING)
            return SupportKind.AREA_HARM;
        if (potion == Potions.WATER)
            return SupportKind.CLEANSE;
        return null;
    }
}
