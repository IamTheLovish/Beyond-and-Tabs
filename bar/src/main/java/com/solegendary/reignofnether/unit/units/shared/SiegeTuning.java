package com.solegendary.reignofnether.unit.units.shared;

/**
 * The T1 siege lobber's numbers in one table (design/siege_t1.md, lovish Oct 29: BAR's Rocko). Every faction's lobber
 * ({@link SiegeLobberUnit}) shares them; only its name, skin and lab differ. Plain arithmetic, no world: the JUnit
 * test (SiegeTuningTest) checks the times the design promises.
 * <p>
 * Building health is the number the HUD shows: a shell's building damage A takes 2 A health off (RaidTuning).
 */
public final class SiegeTuning {
    private SiegeTuning() { }

    // ---------------------------------------------------------------- the unit
    public static final float MAX_HEALTH = 80f;   // above the towers' "light" line (70): it takes their plain rate
    public static final float ARMOR = 2f;
    public static final float MOVEMENT_SPEED = 0.23f;
    /** Out-reaches the anti-raid tower (14), not the enhanced one (16). */
    public static final float ATTACK_RANGE = 15f;
    /** Below its range (lovish's artillery rule): it needs a spotter for its full reach. */
    public static final int SIGHT_RANGE = 12;
    /** The dead zone: nothing nearer is shelled (IndirectFire backs it off). */
    public static final float MIN_RANGE = 3f;
    public static final float ATTACKS_PER_SECOND = 0.4f;   // a shell every 2.5 s
    /** 1 s set-up after moving (the long artillery needs SetUp.ARTILLERY_TICKS, 2 s). */
    public static final int SET_UP_TICKS = 20;

    // ---------------------------------------------------------------- the shell
    /** Against units, at the centre of the splash (half at the rim). */
    public static final float DAMAGE = 4f;
    public static final float SPLASH_RADIUS = 1.5f;
    /** A high lob, 2 s in the air whatever the range, aimed where the target stands (no lead): walkers are gone. */
    public static final int FLIGHT_TICKS = 40;
    /** BombardShell.BUILDING_DAMAGE_MULT: a shell's building damage is its damage times this ... */
    public static final float SHELL_BUILDING_MULT = 1.5f;
    /** ... times (1 + this), the unit's BUILDING_DAMAGE_BONUS. */
    public static final float BUILDING_DAMAGE_BONUS = 3.5f;
    /** Labs, economy, capitols and the factions' own T2 defences take this share; defences, walls and gates all of it. */
    public static final float OTHER_BUILDING_SHARE = 0.5f;

    // ---------------------------------------------------------------- arithmetic

    /** destroyRandomBlocks amount per shell on a building. */
    public static float buildingDamage(boolean defence) {
        return DAMAGE * SHELL_BUILDING_MULT * (1f + BUILDING_DAMAGE_BONUS) * (defence ? 1f : OTHER_BUILDING_SHARE);
    }

    /** HUD health per shell (54 on a defence, 27 on anything else). */
    public static float healthPerShell(boolean defence) {
        return buildingDamage(defence) * 2f;
    }

    /** Ticks between shells. */
    public static int cooldownTicks() {
        return Math.round(20f / ATTACKS_PER_SECOND);
    }

    /** Shells it takes to bring down a building of this health. */
    public static int shellsToRaze(double health, boolean defence) {
        return (int) Math.ceil(health / healthPerShell(defence) - 1e-6);
    }

    /**
     * Seconds from a standing order (in range, not yet set up) to the building falling, for n lobbers firing together:
     * the set-up, the flight of the first volley and one cooldown per further volley.
     */
    public static float secondsToRaze(double health, int lobbers, boolean defence) {
        int volleys = (int) Math.ceil(shellsToRaze(health, defence) / (double) Math.max(1, lobbers));
        return (SET_UP_TICKS + FLIGHT_TICKS + (volleys - 1) * cooldownTicks()) / 20f;
    }
}
