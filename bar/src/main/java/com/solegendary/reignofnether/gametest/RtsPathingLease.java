package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.unit.UnitServerEvents;

/**
 * The server-wide rtsPathfinding flag, switched on for the tests of a batch that want the match pathfinder (a real
 * match turns it on: ArenaPathing / WorldBorderServerEvents) and back to what it was after the LAST of them. The tests
 * of one batch run side by side, so a test that put the flag back when it finished used to turn it off under its
 * neighbours still running; batches run one at a time, so nothing outside the batch sees it.
 * Every {@link #take()} is matched by exactly one {@link #release()}.
 */
final class RtsPathingLease {
    private RtsPathingLease() { }

    private static int holders = 0;
    private static boolean saved = false;

    static void take() {
        if (holders++ == 0)
            saved = UnitServerEvents.rtsPathfinding;
        UnitServerEvents.rtsPathfinding = true;
    }

    static void release() {
        if (holders > 0 && --holders == 0)
            UnitServerEvents.rtsPathfinding = saved;
    }
}
