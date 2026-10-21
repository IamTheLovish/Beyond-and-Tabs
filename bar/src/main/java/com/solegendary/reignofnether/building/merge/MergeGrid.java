package com.solegendary.reignofnether.building.merge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The shape matcher of merged buildings (claude/merged-buildings.md 2b): given the cells of a grid of the same
 * building - cell (x, z) = one member, neighbours one footprint apart - it cuts the grid into the complexes it
 * becomes, <b>biggest shapes first</b>, so a 4x3 grid of 12 is one Quarter + one Pair + one single, never three
 * Farms. Pure integer geometry, no Minecraft, so the GameTests can check every layout headless and the client can
 * preview a grid ghost with the same code the server scores with.
 * <p>
 * The rules, in order (deterministic: the same cells always give the same cut, whatever order they come in):
 * <ol>
 *   <li>Shapes of four or more members, biggest first; each shape is tried lying (cols x rows) then standing
 *       (rows x cols); anchors are scanned row by row (z, then x), and a rectangle whose cells are all still free is
 *       taken at once.</li>
 *   <li>Pairs last, as a <i>maximum matching</i> of the leftover cells (Kuhn's augmenting paths on the grid graph,
 *       which is bipartite by cell parity) - so a Quarter that loses its centre becomes four Pairs, not three Pairs
 *       and two strays. Which pairs is settled by scan order, so it is deterministic too.</li>
 * </ol>
 * Everything else stays a single building.
 */
public final class MergeGrid {
    private MergeGrid() { }

    /** One member's place in a grid. */
    public record Cell(int x, int z) {
        Cell east() { return new Cell(x + 1, z); }
        Cell south() { return new Cell(x, z + 1); }
        Cell west() { return new Cell(x - 1, z); }
        Cell north() { return new Cell(x, z - 1); }
    }

    /** One complex-to-be: the shape over the cells [x0, x0 + w) x [z0, z0 + d), with w x d == shape.members(). */
    public record Match(MergedShape shape, int x0, int z0, int w, int d) {
        /** Its cells, row by row (z, then x) - the order the members keep in the complex. */
        public List<Cell> cells() {
            List<Cell> out = new ArrayList<>(w * d);
            for (int z = z0; z < z0 + d; z++)
                for (int x = x0; x < x0 + w; x++)
                    out.add(new Cell(x, z));
            return out;
        }

        public boolean contains(Cell c) {
            return c.x() >= x0 && c.x() < x0 + w && c.z() >= z0 && c.z() < z0 + d;
        }
    }

    static final Comparator<Cell> ROW_MAJOR = Comparator.comparingInt(Cell::z).thenComparingInt(Cell::x);

    /**
     * Cuts {@code cells} into shapes of at most {@code maxMembers} members (see the class comment). The matches
     * come back sorted row by row; cells in no match stay single buildings.
     */
    public static List<Match> partition(Collection<Cell> cells, int maxMembers) {
        Set<Cell> free = new HashSet<>(cells);
        List<Cell> order = new ArrayList<>(free);
        order.sort(ROW_MAJOR);
        List<Match> out = new ArrayList<>();
        for (MergedShape shape : MergedShape.ladder(maxMembers)) {
            if (shape == MergedShape.PAIR)
                continue;   // pairs are matched below, where the leftovers are known
            int[][] orientations = shape.cols == shape.rows
                ? new int[][] { { shape.cols, shape.rows } }
                : new int[][] { { shape.cols, shape.rows }, { shape.rows, shape.cols } };
            for (int[] wd : orientations) {
                for (Cell anchor : order) {
                    if (!free.contains(anchor))
                        continue;
                    Match m = new Match(shape, anchor.x(), anchor.z(), wd[0], wd[1]);
                    List<Cell> mc = m.cells();
                    if (free.containsAll(mc)) {
                        free.removeAll(mc);
                        out.add(m);
                    }
                }
            }
        }
        if (MergedShape.PAIR.members() <= maxMembers)
            out.addAll(matchPairs(free));
        out.sort(Comparator.comparingInt(Match::z0).thenComparingInt(Match::x0));
        return out;
    }

    /** Cells of a full {@code w} x {@code d} rectangle at the origin - what a Shift+Alt grid drag lays. */
    public static List<Cell> rectangle(int w, int d) {
        List<Cell> cells = new ArrayList<>(Math.max(0, w * d));
        for (int z = 0; z < d; z++)
            for (int x = 0; x < w; x++)
                cells.add(new Cell(x, z));
        return cells;
    }

    /** The cells of {@code cells} that no match covers (the buildings that stay single). */
    public static List<Cell> singles(Collection<Cell> cells, List<Match> matches) {
        List<Cell> out = new ArrayList<>();
        for (Cell c : cells) {
            boolean covered = false;
            for (Match m : matches)
                if (m.contains(c)) {
                    covered = true;
                    break;
                }
            if (!covered)
                out.add(c);
        }
        out.sort(ROW_MAJOR);
        return out;
    }

    // ---- pairs: maximum matching on the grid graph of the free cells ----

    private static List<Match> matchPairs(Set<Cell> free) {
        List<Cell> left = new ArrayList<>();   // even parity: the grid graph is bipartite by (x + z) parity
        for (Cell c : free)
            if (Math.floorMod(c.x() + c.z(), 2) == 0)
                left.add(c);
        left.sort(ROW_MAJOR);
        Map<Cell, Cell> matchOfRight = new HashMap<>();   // right cell -> its left partner
        for (Cell u : left)
            augment(u, free, matchOfRight, new HashSet<>());
        List<Match> out = new ArrayList<>();
        for (Map.Entry<Cell, Cell> e : matchOfRight.entrySet()) {
            Cell a = e.getValue(), b = e.getKey();
            int x0 = Math.min(a.x(), b.x()), z0 = Math.min(a.z(), b.z());
            boolean horizontal = a.z() == b.z();
            out.add(new Match(MergedShape.PAIR, x0, z0, horizontal ? 2 : 1, horizontal ? 1 : 2));
        }
        return out;
    }

    /** Kuhn: tries to give {@code u} a partner, re-routing partners along an augmenting path. Neighbours in a fixed order. */
    private static boolean augment(Cell u, Set<Cell> free, Map<Cell, Cell> matchOfRight, Set<Cell> visited) {
        for (Cell v : new Cell[] { u.east(), u.south(), u.west(), u.north() }) {
            if (!free.contains(v) || !visited.add(v))
                continue;
            Cell current = matchOfRight.get(v);
            if (current == null || augment(current, free, matchOfRight, visited)) {
                matchOfRight.put(v, u);
                return true;
            }
        }
        return false;
    }
}
