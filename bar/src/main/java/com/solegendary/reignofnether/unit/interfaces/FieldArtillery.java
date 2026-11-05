package com.solegendary.reignofnether.unit.interfaces;

import com.solegendary.reignofnether.unit.SetUp;

/**
 * The shell-lobbing field artillery: Bombard Crew, Mortar Crawler, Rocket Battery and Mortar Galley (every unit that
 * fires {@link com.solegendary.reignofnether.unit.units.tide.BombardShell} from the ground except the Iron Leviathan
 * Engine, a T3 with its own role). Lovish, Oct 24: massed shell artillery formed an unapproachable death ball, so
 * <ul>
 * <li>it <b>cannot hit air</b> ({@link AttackerUnit#canTargetAir}): no flyer is ever its target, and its shells pass
 * through flyers and never splash them - anti-air and flyers are the answer to a gun line;</li>
 * <li>it needs <b>2 s to set up</b> after moving ({@link #getSetUpTicks}, kept by {@link SetUp}): a gun line that
 * keeps walking forward does not fire, so it has to stop and commit, and a raid that catches it on the move gets a
 * free moment.</li>
 * </ul>
 * Both leave it its job - out-ranging and shelling a line or a base from behind it, standing still (LANES.md, Oct 21:
 * weaknesses never ruin a unit; artillery may stay back).
 */
public interface FieldArtillery extends IndirectFire, AttackerUnit {

    @Override
    default boolean canTargetAir() {
        return false;
    }

    /** Ticks it must stand still after moving before it can fire (unit/SetUp). */
    default int getSetUpTicks() {
        return SetUp.ARTILLERY_TICKS;
    }
}
