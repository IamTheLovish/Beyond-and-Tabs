package com.solegendary.reignofnether.hero.ai;

import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Reading the fight (lovish's hero AI requirement 2: "army strength (HP x damage both sides) -> engage/hold/disengage;
 * never chase into towers or a bigger army"), from what {@link Perception} saw and nothing else.
 * <ul>
 *   <li><b>Power ratio.</b> Each side's power is its summed health times its summed damage per second - Lanchester's
 *       square law for aimed fire, the fight's research pick (claude/hero-ai-research-2026-10-26.md) - over the units
 *       seen round the hero; an enemy tower whose guns reach the fight counts on the enemy side. The ratio is ours
 *       over theirs.</li>
 *   <li><b>Stance, with hysteresis.</b> ENGAGE from a ratio of {@link #ENGAGE_AT}, DISENGAGE below
 *       {@link #DISENGAGE_BELOW}, and in between the stance stays what it was - so a fight near even does not flip the
 *       hero every second.</li>
 *   <li><b>Near death, with hysteresis.</b> Below {@link #FLEE_HP} of its health the hero is near death and stays so
 *       until healed to {@link #HEALED_HP}: stepping out of reach and back is not a new danger (OrderLock, D030).</li>
 *   <li><b>Threatened.</b> An enemy seen within {@link #THREAT_RADIUS} (or its own reach, if longer), or the hero
 *       inside an enemy tower's reach.</li>
 * </ul>
 * The arithmetic is static and plain so it can be read and tuned in one place.
 */
public final class FightEval {
    private FightEval() { }

    public enum Stance { ENGAGE, HOLD, DISENGAGE }

    public static final double ENGAGE_AT = 1.2;
    public static final double DISENGAGE_BELOW = 0.8;
    /** v1's LOW_HP (HeroBot): below this the hero saves itself first. */
    public static final double FLEE_HP = 0.30;
    public static final double HEALED_HP = 0.45;
    public static final double THREAT_RADIUS = 8;
    /** The ratio reported when nothing hostile is seen. */
    public static final double UNOPPOSED = 99;

    public record Reading(double ratio, Stance stance, boolean threatened, boolean nearDeath, double hp) { }

    public static final Reading START = new Reading(UNOPPOSED, Stance.HOLD, false, false, 1);

    /** Ours over theirs; {@link #UNOPPOSED} with no enemy power, 0 with none of ours. */
    public static double ratio(double ourHealth, double ourDps, double theirHealth, double theirDps) {
        double them = theirHealth * theirDps, us = ourHealth * ourDps;
        if (them <= 0)
            return UNOPPOSED;
        return Math.min(UNOPPOSED, us / them);
    }

    public static Stance stance(double ratio, Stance before) {
        if (ratio >= ENGAGE_AT)
            return Stance.ENGAGE;
        if (ratio < DISENGAGE_BELOW)
            return Stance.DISENGAGE;
        return before;
    }

    public static boolean nearDeath(double hp, boolean before) {
        return before ? hp < HEALED_HP : hp < FLEE_HP;
    }

    static double dps(LivingEntity e) {
        return e instanceof AttackerUnit a ? a.getUnitAttackDamage() * a.getAttacksPerSecond() : 0;
    }

    public static Reading evaluate(Mob hero, Perception.Snapshot s, Reading before) {
        double ourHp = 0, ourDps = 0, theirHp = 0, theirDps = 0;
        for (LivingEntity f : s.friends)
            if (f.isAlive()) {
                ourHp += f.getHealth();
                ourDps += dps(f);
            }
        boolean threatened = false;
        double hx = hero.getX(), hz = hero.getZ();
        for (LivingEntity e : s.enemies) {
            if (!e.isAlive())
                continue;
            theirHp += e.getHealth();
            theirDps += dps(e);
            double reach = Math.max(THREAT_RADIUS, e instanceof AttackerUnit a ? a.getAttackRange() + 2 : 0);
            double dx = e.getX() - hx, dz = e.getZ() - hz;
            if (dx * dx + dz * dz <= reach * reach)
                threatened = true;
        }
        for (Perception.Tower t : s.towers) {
            theirHp += t.health();
            theirDps += t.dps();
            if (t.reaches(hx, hz, 1.0))
                threatened = true;
        }
        double r = ratio(ourHp, ourDps, theirHp, theirDps);
        double hp = hero.getMaxHealth() <= 0 ? 1 : hero.getHealth() / hero.getMaxHealth();
        return new Reading(r, stance(r, before.stance()), threatened, nearDeath(hp, before.nearDeath()), hp);
    }
}
