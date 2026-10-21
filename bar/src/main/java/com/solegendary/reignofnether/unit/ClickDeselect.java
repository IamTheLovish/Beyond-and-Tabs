package com.solegendary.reignofnether.unit;

/**
 * A plain left click on empty ground lets go of the selection (tester, design/playtest_oct20_lane_c.md item 16, asked
 * twice: "after selecting units, a click that isn't an order lets go of them, so you can select new ones").
 * No client or Minecraft imports, so the rule is plain-Java testable (ClickDeselectGameTests).
 * <p>
 * The press decides whether the click may deselect (what was under the cursor and armed when the button went down);
 * the release only checks that the mouse did not travel, since a drag is a box selection (CursorClientEvents).
 * Shift / Ctrl / Alt clicks keep the selection: they add, remove or are part of another gesture.
 */
public final class ClickDeselect {
    private ClickDeselect() { }

    /** A press that moves further than this many GUI pixels before release is a drag, not a click. */
    public static final double MAX_CLICK_TRAVEL = 4;

    /** Whether a left press may deselect once it is released in place. */
    public static boolean pressMayDeselect(boolean hasSelection, boolean overHud, boolean orderArmed,
                                           boolean placingBuilding, boolean unitUnderCursor,
                                           boolean buildingUnderCursor, boolean shift, boolean ctrl, boolean alt) {
        return hasSelection && !overHud && !orderArmed && !placingBuilding && !unitUnderCursor
            && !buildingUnderCursor && !shift && !ctrl && !alt;
    }

    /** Whether the release completes a deselecting click: the press allowed it and the mouse stayed put. */
    public static boolean releaseDeselects(boolean pressMayDeselect, double pressX, double pressY, double releaseX,
                                           double releaseY) {
        double dx = releaseX - pressX, dy = releaseY - pressY;
        return pressMayDeselect && dx * dx + dy * dy <= MAX_CLICK_TRAVEL * MAX_CLICK_TRAVEL;
    }
}
