package com.solegendary.reignofnether.building.merge;

import java.util.ArrayList;
import java.util.List;

/**
 * The ladder of merged buildings (claude/merged-buildings.md 2b/2c, lovish Oct 21): a Shift+Alt grid of the same
 * building grows into one complex whose size is one of these shapes. Each step adds a little output - his Oct 20
 * rule, "+10% per stat upgrade, capabilities are the fun upgrades" - so the bonus stays small and the big steps add a
 * capability (Surge at 9, Grid Ward at 16, the faction landmark at 25; those are slice 2, not here).
 * <p>
 * {@link #cols} is the long side and {@link #rows} the short one, in members; a shape fits a grid either way round
 * (a Row is 3x2 or 2x3). {@link MergedBuildings#MAX_MEMBERS} says which steps are live: slice 1 stops at the Quarter.
 */
public enum MergedShape {
    PAIR(2, 1, 0.04f),
    FARM(2, 2, 0.08f),
    ROW(3, 2, 0.10f),
    QUARTER(3, 3, 0.12f),
    CITADEL(4, 4, 0.14f),
    WONDER(5, 5, 0.15f);

    public final int cols, rows;
    /** Output bonus on every member (0.08 = +8%). */
    public final float bonus;

    MergedShape(int cols, int rows, float bonus) {
        this.cols = cols;
        this.rows = rows;
        this.bonus = bonus;
    }

    public int members() {
        return cols * rows;
    }

    /** What each member earns, times this (the spec's "sum of the members x size bonus"). */
    public float outputMultiplier() {
        return 1f + bonus;
    }

    /** The bonus as a whole percentage, for the HUD ("+8%"). */
    public int bonusPercent() {
        return Math.round(bonus * 100f);
    }

    /** The lang key of the shape's name ("Pair", "Farm", ...). */
    public String nameKey() {
        return "merged.reignofnether." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /** The shapes of at most {@code maxMembers} members, biggest first - the matcher's order (the spec: "fills the biggest shapes first"). */
    public static List<MergedShape> ladder(int maxMembers) {
        List<MergedShape> out = new ArrayList<>();
        MergedShape[] all = values();
        for (int i = all.length - 1; i >= 0; i--)
            if (all[i].members() <= maxMembers)
                out.add(all[i]);
        return out;
    }

    /** The shape of exactly {@code members} members, or null. */
    public static MergedShape ofMembers(int members) {
        for (MergedShape s : values())
            if (s.members() == members)
                return s;
        return null;
    }
}
