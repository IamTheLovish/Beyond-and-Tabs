package com.solegendary.reignofnether.barfx;

import com.solegendary.reignofnether.building.BuildingPlacement;

/** STUB (red proof): the forming moment's server hook, signatures only; the real hook follows in the next commit. */
public final class PortalForming {
    private PortalForming() { }

    public static final int MIN_AGE_TICKS = 40;

    public static boolean onBuilt(BuildingPlacement bp) {
        return false;
    }
}
