package com.solegendary.reignofnether.player;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

/**
 * Owner name to display colour (0xRRGGBB), remembered for one frame by a per-frame overlay that tints every unit or
 * building by its owner: the unit underlines, the building boxes, the strategic icons and plates.
 * <p>
 * Each of those asked {@link PlayerColors#getPlayerDisplayColorHex} - and for units
 * {@link PlayerClientEvents#isRTSPlayer(String)}, a walk of the roster with string compares - once per unit or building
 * per frame, and wrapped the answer in a new java.awt.Color: ~2,300 lookups a frame in lovish's Oct 21 3-player match
 * (600 units, 1,700 buildings) for a handful of distinct owners (perf plan R2). The owner of the overlay calls
 * {@link #clear()} at the start of each frame, so a colour setting, a team change or an alliance change shows on the
 * very next frame, exactly as before; within a frame the answers are the same ones PlayerColors gives.
 * <p>
 * Client render thread only (one instance per overlay, no locking).
 */
public final class OwnerColours {

    private static final int UNSET = -1;   // a cached colour is masked to 24 bits, so never -1

    private final Object2IntOpenHashMap<String> display = new Object2IntOpenHashMap<>();
    private final Object2IntOpenHashMap<String> unit = new Object2IntOpenHashMap<>();

    public OwnerColours() {
        display.defaultReturnValue(UNSET);
        unit.defaultReturnValue(UNSET);
    }

    /** Forget the last frame's answers (call once at the start of every frame the overlay draws). */
    public void clear() {
        display.clear();
        unit.clear();
    }

    /** {@code PlayerColors.getPlayerDisplayColorHex(owner) & 0xFFFFFF}: a building's (or any owner's) display colour. */
    public int display(String owner) {
        if (owner == null)
            return PlayerColors.getPlayerDisplayColorHex(null) & 0xFFFFFF;
        int rgb = display.getInt(owner);
        if (rgb == UNSET) {
            rgb = PlayerColors.getPlayerDisplayColorHex(owner) & 0xFFFFFF;
            display.put(owner, rgb);
        }
        return rgb;
    }

    /**
     * A unit owner's colour: its player's display colour when the owner is a player of the match, else grey (neutral
     * units, units of a player who left) - the rule the unit underlines and the strategic icons have always used.
     */
    public int unit(String owner) {
        if (owner == null)
            return PlayerColors.COLOR_GRAY.hexCode & 0xFFFFFF;
        int rgb = unit.getInt(owner);
        if (rgb == UNSET) {
            rgb = PlayerClientEvents.isRTSPlayer(owner) ? display(owner) : PlayerColors.COLOR_GRAY.hexCode & 0xFFFFFF;
            unit.put(owner, rgb);
        }
        return rgb;
    }
}
