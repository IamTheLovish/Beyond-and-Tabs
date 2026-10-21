package com.solegendary.reignofnether.building;

/**
 * Key rules while a building ghost is out, kept free of client classes so PlacementKeysGameTests can check them
 * headless. Alt alone still drops the ghost (inherited from Reign of Nether), but with Shift held Alt is the grid
 * modifier of BuildingLineClientEvents: dropping the ghost there made Shift+Alt grid placement impossible to use
 * (Lane C playtest, Oct 20: "hold alt and shift at the same time to build structures in a grid").
 */
public final class PlacementKeys {

    private PlacementKeys() {}

    /** Does pressing Alt drop the building being placed? Not while Shift is held: then Alt means "grid". */
    public static boolean altCancelsPlacement(boolean shiftHeld) {
        return !shiftHeld;
    }
}
