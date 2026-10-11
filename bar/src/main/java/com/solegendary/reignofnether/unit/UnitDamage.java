package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit damage with an explicit amount. RoN's {@code LivingEntityMixin.actuallyHurt} rewrites every unit-sourced
 * MOB_ATTACK / projectile hit to the attacker's own attack damage - right for a normal swing or arrow, wrong for
 * secondary damage (a slam's splash, a cleave) that is deliberately a fraction of it: that used to land at full damage.
 * {@link #hurtExplicit} marks the hit so the mixin keeps the given amount (still applying the target's armour and
 * resist, so the splash stays a physical blow), and {@link #meleeSplash} is the shared "half a blow to the foes round
 * the target" used by the T3 titans.
 *
 * Server thread only (LivingEntity.hurt returns early on the client), so a plain depth counter is a safe marker.
 */
public final class UnitDamage {
    private UnitDamage() { }

    private static int explicitDepth = 0;
    private static final List<LivingEntity> scratch = new ArrayList<>();   // grid query results, reused
    private static boolean splashing = false;   // the scratch list is in use (a splash can't splash again)

    /** True while a {@link #hurtExplicit} hit is being applied: the mixin must keep its amount. */
    public static boolean isExplicitAmount() {
        return explicitDepth > 0;
    }

    /** {@code target.hurt(source, amount)} with the amount kept as given even for a unit's MOB_ATTACK source. */
    public static boolean hurtExplicit(LivingEntity target, DamageSource source, float amount) {
        explicitDepth++;
        try {
            return target.hurt(source, amount);
        } finally {
            explicitDepth--;
        }
    }

    /**
     * A melee blow's splash: every enemy unit of {@code attacker}'s owner within {@code radius} blocks of {@code target}
     * (never the target itself, the attacker, friends or allies) takes {@code amount} as a MOB_ATTACK from the attacker.
     * Returns how many were hit. One grid query, no allocation.
     */
    public static int meleeSplash(ServerLevel sl, LivingEntity attacker, Entity target, float radius, float amount) {
        if (splashing || !(attacker instanceof Unit au) || amount <= 0)
            return 0;
        String owner = au.getOwnerName();
        if (owner == null || owner.isEmpty())
            return 0;
        double r2 = radius * radius;
        int hit = 0;
        splashing = true;
        try {
            for (LivingEntity le : UnitGrid.near(sl, target.getX(), target.getZ(), radius, scratch)) {
                if (le == target || le == attacker || !le.isAlive() || !isFoe(owner, le) || le.distanceToSqr(target) > r2)
                    continue;
                if (hurtExplicit(le, sl.damageSources().mobAttack(attacker), amount))
                    hit++;
            }
        } finally {
            scratch.clear();
            splashing = false;
        }
        return hit;
    }

    static boolean isFoe(String owner, LivingEntity le) {
        if (!(le instanceof Unit u))
            return false;
        String o = u.getOwnerName();
        return o != null && !o.isEmpty() && !owner.equals(o) && !AlliancesServerEvents.isAllied(owner, o);
    }
}
