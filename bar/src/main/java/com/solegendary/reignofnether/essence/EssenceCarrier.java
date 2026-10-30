package com.solegendary.reignofnether.essence;

/**
 * Marker for a unit that gathers and holds essence (design/essence_casters.md): the race casters implement it, so
 * {@link Essence#isCarrier} needs no per-unit flag for them. Any other unit can be made a carrier at run time with
 * {@link Essence#setCarrier} (the game tests do).
 */
public interface EssenceCarrier {
}
