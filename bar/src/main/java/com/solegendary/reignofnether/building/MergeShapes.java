package com.solegendary.reignofnether.building;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * The shapes a grid of the same building grows into (project doc claude/merged-buildings.md, section 2b): the
 * ladder of rectangles, their size bonus (2c) and the matcher that partitions a set of lattice cells into those
 * rectangles, biggest first, so "a 4x3 grid of 12 = a 3x3 complex + a pair + one single building".
 * <p>
 * Pure: no world, no placements - cells are lattice coordinates (column, row) of same-type buildings standing edge
 * to edge, and the caller says which lattice neighbours really touch ({@link #match}'s predicate). Shared by the
 * server (BuildingMerge), the client's grid-ghost preview (BuildingLineClientEvents) and the GameTests.
 * <p>
 * Slice 1 (White Angel wind generators) enables sizes 2-9: {@link #MAX_MEMBERS}. The Citadel (16) and Wonder (25)
 * are declared so the ladder is complete and the matcher already handles them; raising the cap is slice 2.
 */
public final class MergeShapes {
    private MergeShapes() { }

    /** Biggest shape a complex may take right now (members). The ladder above it waits for its art and powers. */
    public static final int MAX_MEMBERS = 9;

    /**
     * The ladder. width x depth in cells (either orientation matches), the output bonus the complex earns and the
     * lang key of its name ("hud.reignofnether.merged.&lt;key&gt;").
     */
    public enum Shape {
        PAIR(2, 1, 0.04f, "pair"),
        FARM(2, 2, 0.08f, "farm"),
        ROW(3, 2, 0.10f, "row"),
        QUARTER(3, 3, 0.12f, "quarter"),
        CITADEL(4, 4, 0.14f, "citadel"),
        WONDER(5, 5, 0.15f, "wonder");

        public final int width, depth;
        public final float bonus;
        public final String key;

        Shape(int width, int depth, float bonus, String key) {
            this.width = width;
            this.depth = depth;
            this.bonus = bonus;
            this.key = key;
        }

        public int members() { return width * depth; }

        /** Output multiplier: 1 + the bonus (a Farm's 4 generators earn 4 x 1.08). */
        public float outputMultiplier() { return 1f + bonus; }

        /** The shape with exactly this many members, or null (a shape is defined by its member count). */
        public static Shape ofMembers(int members) {
            for (Shape s : values())
                if (s.members() == members)
                    return s;
            return null;
        }
    }

    /** A lattice cell: column along +x, row along +z. */
    public record Cell(int col, int row) { }

    /**
     * One matched rectangle: its shape, the cells it covers (row-major) and whether it lies rotated (depth along x,
     * width along z - a Row standing 2 wide and 3 deep).
     */
    public record Group(Shape shape, List<Cell> cells, boolean rotated) {
        public int members() { return cells.size(); }
    }

    /**
     * Partitions {@code cells} into shapes, biggest first ({@code maxMembers} caps the shape size; cells left over
     * are singles and stay separate buildings). For each shape, both orientations are tried at every anchor cell in
     * row-major order; a rectangle fits when all its cells are present and unused and every pair of lattice-adjacent
     * cells inside it really touches ({@code touching} - the lattice is only a guide, the world is the truth).
     * Deterministic: the same cells give the same groups on the server, on every client and in a test.
     */
    public static List<Group> match(Set<Cell> cells, int maxMembers, BiPredicate<Cell, Cell> touching) {
        List<Group> groups = new ArrayList<>();
        if (cells == null || cells.size() < 2)
            return groups;
        int minCol = Integer.MAX_VALUE, minRow = Integer.MAX_VALUE, maxCol = Integer.MIN_VALUE, maxRow = Integer.MIN_VALUE;
        for (Cell c : cells) {
            minCol = Math.min(minCol, c.col());
            maxCol = Math.max(maxCol, c.col());
            minRow = Math.min(minRow, c.row());
            maxRow = Math.max(maxRow, c.row());
        }
        Set<Cell> free = new HashSet<>(cells);
        List<Shape> ladder = new ArrayList<>(List.of(Shape.values()));
        Collections.reverse(ladder);   // WONDER first ... PAIR last
        for (Shape shape : ladder) {
            if (shape.members() > maxMembers || shape.members() > free.size())
                continue;
            for (int row = minRow; row <= maxRow; row++)
                for (int col = minCol; col <= maxCol; col++) {
                    Cell anchor = new Cell(col, row);
                    if (!free.contains(anchor))
                        continue;
                    Group g = fit(shape, anchor, false, free, touching);
                    if (g == null && shape.width != shape.depth)
                        g = fit(shape, anchor, true, free, touching);
                    if (g != null) {
                        groups.add(g);
                        free.removeAll(g.cells());
                    }
                }
        }
        return groups;
    }

    /** {@link #match} with the current cap. */
    public static List<Group> match(Set<Cell> cells, BiPredicate<Cell, Cell> touching) {
        return match(cells, MAX_MEMBERS, touching);
    }

    private static Group fit(Shape shape, Cell anchor, boolean rotated, Set<Cell> free, BiPredicate<Cell, Cell> touching) {
        int w = rotated ? shape.depth : shape.width;
        int d = rotated ? shape.width : shape.depth;
        List<Cell> covered = new ArrayList<>(w * d);
        for (int j = 0; j < d; j++)
            for (int i = 0; i < w; i++) {
                Cell c = new Cell(anchor.col() + i, anchor.row() + j);
                if (!free.contains(c))
                    return null;
                covered.add(c);
            }
        // every lattice edge inside the rectangle must be a real edge-to-edge contact
        for (int j = 0; j < d; j++)
            for (int i = 0; i < w; i++) {
                Cell c = covered.get(j * w + i);
                if (i + 1 < w && !touching.test(c, covered.get(j * w + i + 1)))
                    return null;
                if (j + 1 < d && !touching.test(c, covered.get((j + 1) * w + i)))
                    return null;
            }
        return new Group(shape, covered, rotated);
    }

    /** Every cell of a w x d rectangle at the origin - the grid ghost's preview and the tests build grids with it. */
    public static Set<Cell> rectangle(int w, int d) {
        Set<Cell> cells = new HashSet<>();
        for (int j = 0; j < d; j++)
            for (int i = 0; i < w; i++)
                cells.add(new Cell(i, j));
        return cells;
    }
}
