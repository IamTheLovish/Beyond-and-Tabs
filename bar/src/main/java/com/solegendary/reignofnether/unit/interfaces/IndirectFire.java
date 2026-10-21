package com.solegendary.reignofnether.unit.interfaces;

/**
 * Artillery that lobs its shot (mortars, bombards, rocket batteries). Two rules in UnitBowAttackGoal:
 * <ul>
 * <li>No line of sight needed: the shell arcs over walls and ridges. The bow goal used to walk any shooter that lost
 * sight of its target straight at it - a Mortar Galley (a low turtle body) walked from 14 blocks to 6-7, inside its own
 * dead zone, and never fired again (tidewrought_t2_units_attack, red twice on Oct 21).</li>
 * <li>A target inside the dead zone ({@link #getMinRange()}) makes it back off to fire again instead of standing
 * helpless (BAR artillery has the dead zone; backing off is ours).</li>
 * </ul>
 */
public interface IndirectFire {
    /** Closer than this (blocks, horizontal) the shot can't be fired. */
    float getMinRange();
}
