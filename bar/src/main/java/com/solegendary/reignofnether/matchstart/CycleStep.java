package com.solegendary.reignofnether.matchstart;

/**
 * One step of a value-cycling button (Map, Size, Metal, Spawns, Pace, time, Day, Starts, difficulty, team): left click
 * forward, right click backward, wrapping both ways (tester, Oct 26). Pure, so it is tested headless (CycleStepTest);
 * the clicks are wired once in CycleClicks.
 */
public final class CycleStep {

    private CycleStep() {}

    /** The index after {@code index} in a list of {@code count}, forward or back, wrapping; 0 for an empty list. */
    public static int step(int index, int count, boolean back) {
        if (count <= 0)
            return 0;
        return Math.floorMod(index + (back ? -1 : 1), count);
    }
}
