package com.solegendary.reignofnether.unit.units.villagers;

/**
 * The Sunlance Trebuchet's numbers in one table (design/defences_tiers.md). Sunforged's T2 siege unit: the counter
 * audit (DefenceCounterGameTests) found Sunforged, like Gravebound, unable to out-reach its own T1-lab / Tier 2 towers
 * below T3 (the Pillager's 16 against the enhanced anti-raid tower's 16 and the anti-siege tower's 20). It hurls a
 * lance of light past them, as BAR's Sheldon out-ranges a T1 gauntlet, and it is weak against units. The same job and
 * price as Gravebound's T2 siege unit (one price per job across factions). First numbers for the sim; lovish tunes them
 * and has the G2 choice to confirm (a new unit, the Angel Caller's summons, or a Pillager range research).
 * <p>
 * Its designed weakness: slow, a long set-up after moving, blind inside {@link #MIN_RANGE}, sees only
 * {@link #SIGHT_RANGE} (it needs a spotter), never shoots air, and it shells the nearest target - chaff eats its lances.
 * Building health is the number the HUD shows: a shell's building damage A takes 2 A off.
 */
public final class SunlanceTrebuchetTuning {
    private SunlanceTrebuchetTuning() { }

    // ---------------------------------------------------------------- the unit
    public static final float MAX_HEALTH = 160f;   // twice a T1 lobber: a T2 commitment, still dies to a raid
    public static final float ARMOR = 3f;
    public static final float MOVEMENT_SPEED = 0.18f;   // a heavy engine: slower than any lobber (0.23) or mortar (0.21)
    /** Past the anti-siege tower (20) and the enhanced anti-raid tower (16); the T2 artillery's reach. */
    public static final float ATTACK_RANGE = 22f;
    /** Below its range: it needs a spotter for its full reach. */
    public static final int SIGHT_RANGE = 12;
    /** The dead zone: nothing nearer is shelled (IndirectFire backs it off). */
    public static final float MIN_RANGE = 6f;
    public static final float ATTACKS_PER_SECOND = 0.22f;   // a lance about every 4.5 s
    /** 1.5 s set-up after moving (the long artillery's is 2 s, the lobber's 1 s). */
    public static final int SET_UP_TICKS = 30;

    // ---------------------------------------------------------------- the lance of light it hurls
    /** Against units, at the centre of the splash. */
    public static final float DAMAGE = 6f;
    public static final float SPLASH_RADIUS = 1.5f;
    /** A 2 s lob whatever the range, aimed where the target stands. */
    public static final int FLIGHT_TICKS = 40;
    /** BombardShell.BUILDING_DAMAGE_MULT (1.5) times (1 + this) times DAMAGE is a lance's building damage on a defence. */
    public static final float BUILDING_DAMAGE_BONUS = 4f;
    /** BombardShell.BUILDING_DAMAGE_MULT, written out for the arithmetic. */
    public static final float SHELL_BUILDING_MULT = 1.5f;

    /** HUD health a lance takes off a defence (90; the T1 lobber takes 54). */
    public static float healthPerShellOnADefence() {
        return DAMAGE * SHELL_BUILDING_MULT * (1f + BUILDING_DAMAGE_BONUS) * 2f;
    }
}
