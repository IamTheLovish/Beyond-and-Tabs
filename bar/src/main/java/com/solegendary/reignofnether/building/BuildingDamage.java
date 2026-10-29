package com.solegendary.reignofnether.building;

/**
 * How fast units bring buildings down. The one tunable is {@link #GLOBAL_MULT} (lovish, Oct 28: "buildings are so
 * tanky" - normal units knock them down twice as fast, building health unchanged).
 * <p>
 * It is applied once, where a unit's own damage becomes building damage:
 * <ul>
 *   <li>{@link #MELEE_MULT} - what {@code Building.getMeleeDamageMult} returns. Every melee blow
 *       ({@code MeleeAttackBuildingGoal}), every archer's arrow ({@code AbstractArrowMixin}) and the Warden's sonic boom
 *       ({@code BuildingPlacement.getMagicDamageMult}) multiply a unit's damage by it, so they all follow.</li>
 *   <li>{@link #unit(float)} - the unit-caused hits that do not go through that multiplier: a Creeper's, Pillager's and
 *       Ghast's explosion ({@code BuildingServerEvents.onExplosion}) and a Bombard Crew's shell
 *       ({@code BombardShell}, on top of its own {@code BUILDING_DAMAGE_MULT}).</li>
 * </ul>
 * Not scaled: hits that already carry their own building number (a Ravager's roar, a Gorehorn Rhino's breach, a
 * Shellback Turtle's battery, a Royal Guard's mace slam, a sapper's charge), the game-enders and the commander's blast,
 * TNT, the converter's chain blast and the console command. Never apply it a second time to something that went through
 * {@link #MELEE_MULT}.
 */
public final class BuildingDamage {
    private BuildingDamage() { }

    /** How many times faster than the base rate a unit's hit brings a building down. */
    public static final float GLOBAL_MULT = 2.0f;

    /** A melee blow's damage on a building before {@link #GLOBAL_MULT}: 25% of the unit's damage (a block is 2 health, so 50% visually). */
    public static final float BASE_MELEE_MULT = 0.25f;

    /** What a unit's damage is multiplied by on a building, melee and arrows (the base rate and the global multiplier). */
    public static final float MELEE_MULT = BASE_MELEE_MULT * GLOBAL_MULT;

    /** A unit-caused hit that has no melee multiplier of its own, scaled by {@link #GLOBAL_MULT}. */
    public static float unit(float damage) {
        return damage * GLOBAL_MULT;
    }

    /** {@link #unit(float)} for the double amounts building damage is passed around as. */
    public static double unit(double damage) {
        return damage * GLOBAL_MULT;
    }
}
