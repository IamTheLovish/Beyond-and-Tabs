package com.solegendary.reignofnether.util;

import java.util.HashMap;
import java.util.Map;

/**
 * The particle burst cap (N5, design/HANDOFF-lane-c.md C2): in a big fight, many bursts landing in one spot covered
 * whole groups of units and dropped frames. Each game tick, a 16x16-block column cell grants at most
 * {@link #PER_CELL_PER_TICK} burst particles and the whole client at most {@link #PER_TICK}; a burst gets what is
 * left and spawns that many. A lone burst (a death puff, a spell) is under the cap and looks as before. The N1
 * graphics presets may later scale these numbers. No Minecraft types: ParticleBudgetTest checks it headless.
 */
public final class ParticleBudget {

    public static final int CELL_BLOCKS = 16;
    public static final int PER_CELL_PER_TICK = 48;
    public static final int PER_TICK = 400;

    private long tick = Long.MIN_VALUE;
    private int usedThisTick = 0;
    private final Map<Long, Integer> usedByCell = new HashMap<>();

    /** How many of {@code wanted} burst particles at (x, z) may spawn in game tick {@code gameTick}; records them. */
    public int grant(long gameTick, double x, double z, int wanted) {
        if (wanted <= 0)
            return 0;
        if (gameTick != tick) {
            tick = gameTick;
            usedThisTick = 0;
            usedByCell.clear();
        }
        long cell = ((long) Math.floorDiv((int) Math.floor(x), CELL_BLOCKS) << 32)
            ^ (Math.floorDiv((int) Math.floor(z), CELL_BLOCKS) & 0xffffffffL);
        int cellUsed = usedByCell.getOrDefault(cell, 0);
        int granted = Math.max(0, Math.min(wanted, Math.min(PER_CELL_PER_TICK - cellUsed, PER_TICK - usedThisTick)));
        if (granted > 0) {
            usedByCell.put(cell, cellUsed + granted);
            usedThisTick += granted;
        }
        return granted;
    }
}
