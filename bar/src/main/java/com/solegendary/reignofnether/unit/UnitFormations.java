package com.solegendary.reignofnether.unit;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The spread of cells a group is sent to on one move order. It used to be a 1-block spiral round the clicked
 * block: 12 units landed on a 3x4 blob with 0.6-wide bodies a block apart in every direction, and a bear-sized unit
 * shared a cell with the men beside it (Oct 13's clips: "blobs stood on top of each other"). Beyond All Reason
 * sends a group as a shallow line facing where it is going, each unit on its own footprint; this does the same:
 * <ul>
 *   <li>cell spacing from the widest unit in the group ({@link #spacing}: 1 block for ~0.6-wide bodies, 2 for
 *       anything over a block wide, more for the titans), so nobody is put inside a neighbour;</li>
 *   <li>a rectangle about twice as wide across the move as it is deep along it, rotated to face the direction
 *       from the group's centre to the clicked block and centred on that block;</li>
 *   <li>units keep their relative order - the front rank stays in front, the left flank stays left - so paths do not
 *       cross on the way (the old spiral paired by raw x/z sort, which is the same idea for an unrotated square).</li>
 * </ul>
 * The 3-unit case stays a west-east line of three, as before.
 */
public class UnitFormations {

    /** Width-to-depth ratio of the rectangle: BAR's default line is wide and shallow. */
    static final double ASPECT = 2.0;

    /** Cell spacing for a group whose widest body is {@code maxWidth}: 1 for a 0.6-wide man, 2 past a block, 4 for a 3.5-wide titan. */
    public static int spacing(double maxWidth) {
        return Math.max(1, Mth.ceil(maxWidth + 0.3));
    }

    /** The slot cells only, in row-major order (front row first, left to right), for the drag preview and tests. */
    public static List<BlockPos> slots(int count, int spacing, BlockPos moveBp, double dirX, double dirZ) {
        List<BlockPos> cells = new ArrayList<>(count);
        if (count <= 0)
            return cells;
        double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
        double fx = len < 1.0E-4 ? 0 : dirX / len, fz = len < 1.0E-4 ? 1 : dirZ / len;   // forward (default: south)
        double rx = -fz, rz = fx;   // right-hand across the move
        int cols = Math.max(1, Mth.ceil(Math.sqrt(count * ASPECT)));
        int rows = Mth.ceil(count / (double) cols);
        List<BlockPos> taken = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int row = i / cols, col = i % cols;
            // the last row is centred too, so a short back rank does not hang off one flank
            int inRow = row < rows - 1 ? cols : count - row * cols;
            double across = (col - (inRow - 1) / 2.0) * spacing;
            double along = -(row - (rows - 1) / 2.0) * spacing;   // row 0 is the front: furthest along the move
            double x = moveBp.getX() + 0.5 + rx * across + fx * along;
            double z = moveBp.getZ() + 0.5 + rz * across + fz * along;
            BlockPos cell = new BlockPos(Mth.floor(x), moveBp.getY(), Mth.floor(z));
            cell = place(cell, taken, spacing);
            taken.add(cell);
            cells.add(cell);
        }
        return cells;
    }

    /**
     * A rotated lattice floors two points into one cell, or two wide units' cells a block apart, now and then: the nearest
     * cell that keeps a full spacing (as a square) from every cell already placed.
     */
    private static BlockPos place(BlockPos cell, List<BlockPos> taken, int spacing) {
        if (fits(cell, taken, spacing))
            return cell;
        for (int ring = 1; ring <= 2 * spacing + 1; ring++)   // enough for 100 titans at 45 degrees (simulated)
            for (int dx = -ring; dx <= ring; dx++)
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring)
                        continue;
                    BlockPos c = cell.offset(dx, 0, dz);
                    if (fits(c, taken, spacing))
                        return c;
                }
        return cell;
    }

    /** Length of curve one unit takes on a ring or arc: a chord this long keeps a full square spacing at any angle. */
    private static double curveStep(int spacing) {
        return (spacing + 1) * Math.sqrt(2);
    }

    /** {@code count} cells evenly round the circle of {@code radius} on {@code centre}; a group too big for it widens it. */
    public static List<BlockPos> ringSlots(int count, int spacing, BlockPos centre, double radius) {
        double r = Math.max(radius, count * curveStep(spacing) / (2 * Math.PI));
        return onCurve(count, spacing, centre, r, 0, 2 * Math.PI / Math.max(1, count));
    }

    /**
     * {@code count} cells along the half circle of {@code radius} on {@code centre} that bulges toward (dirX, dirZ), flank to
     * flank with the middle slot leading; a group too big for it widens it.
     */
    public static List<BlockPos> arcSlots(int count, int spacing, BlockPos centre, double radius, double dirX, double dirZ) {
        double r = Math.max(radius, count * curveStep(spacing) / Math.PI);
        double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
        double facing = len < 1.0E-4 ? Math.PI / 2 : Math.atan2(dirZ, dirX);   // default: south, as slots()
        double step = count > 1 ? Math.PI / (count - 1) : 0;
        return onCurve(count, spacing, centre, r, facing - step * (count - 1) / 2.0, step);
    }

    private static List<BlockPos> onCurve(int count, int spacing, BlockPos centre, double radius, double start, double step) {
        List<BlockPos> cells = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = start + i * step;
            cells.add(place(new BlockPos(Mth.floor(centre.getX() + 0.5 + radius * Math.cos(angle)), centre.getY(),
                Mth.floor(centre.getZ() + 0.5 + radius * Math.sin(angle))), cells, spacing));
        }
        return cells;
    }

    /**
     * Which slot each unit takes (result[unit] = slot index, one slot per unit) so no two straight paths cross. From index
     * order it swaps any two units whose swap shortens their paths: crossing paths always do (two sides of a triangle
     * beat the third), so it ends with none, and a group already in place stays put.
     */
    public static int[] uncrossed(double[][] units, double[][] slots) {
        int n = units.length;
        int[] pick = new int[n];
        for (int i = 0; i < n; i++)
            pick[i] = i;
        boolean swapped = true;
        while (swapped) {
            swapped = false;
            for (int i = 0; i < n; i++)
                for (int j = i + 1; j < n; j++) {
                    double now = dist(units[i], slots[pick[i]]) + dist(units[j], slots[pick[j]]);
                    double then = dist(units[i], slots[pick[j]]) + dist(units[j], slots[pick[i]]);
                    if (then < now - 1.0E-9) {
                        int t = pick[i];
                        pick[i] = pick[j];
                        pick[j] = t;
                        swapped = true;
                    }
                }
        }
        return pick;
    }

    private static double dist(double[] a, double[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]);
    }

    /** Whether a cell keeps a full spacing (Chebyshev, so bodies on a square lattice never touch) from every placed cell. */
    private static boolean fits(BlockPos cell, List<BlockPos> taken, int spacing) {
        for (BlockPos t : taken)
            if (Math.max(Math.abs(t.getX() - cell.getX()), Math.abs(t.getZ() - cell.getZ())) < spacing)
                return false;
        return true;
    }

    // given a list of entities that were issued a move command to moveBp, find the best pairings of entities and
    // positions that allows each entity the best chance to reach said position without bumping into each other
    public static <T extends LivingEntity> List<Pair<LivingEntity, BlockPos>> getMoveFormation(Level level, List<T> units, BlockPos moveBp) {
        List<Pair<LivingEntity, BlockPos>> formation = new ArrayList<>();
        if (units.isEmpty())
            return formation;

        if (units.size() == 3) {
            // for specifically size 3 just use a 3v1 line
            List<BlockPos> bps = new ArrayList<>(List.of(moveBp, moveBp.west(), moveBp.east()));
            units.sort(Comparator.comparing(u -> u.getOnPos().getX()));
            bps.sort(Comparator.comparing(BlockPos::getX));
            for (int i = 0; i < 3; i++)
                formation.add(new Pair<>(units.get(i), bps.get(i)));
            return formation;
        }

        double cx = 0, cz = 0, maxWidth = 0;
        for (T u : units) {
            cx += u.getX();
            cz += u.getZ();
            maxWidth = Math.max(maxWidth, u.getBbWidth());
        }
        cx /= units.size();
        cz /= units.size();
        double dirX = moveBp.getX() + 0.5 - cx, dirZ = moveBp.getZ() + 0.5 - cz;
        List<BlockPos> cells = slots(units.size(), spacing(maxWidth), moveBp, dirX, dirZ);

        // pair by rank along and across the move: the units furthest forward take the front row, and within a row
        // the leftmost unit takes the leftmost cell, so nobody crosses a neighbour's path
        double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
        final double fx = len < 1.0E-4 ? 0 : dirX / len, fz = len < 1.0E-4 ? 1 : dirZ / len;
        final double rx = -fz, rz = fx;
        List<T> ordered = new ArrayList<>(units);
        ordered.sort(Comparator.comparingDouble((T u) -> -(u.getX() * fx + u.getZ() * fz)));
        int cols = Math.max(1, Mth.ceil(Math.sqrt(units.size() * ASPECT)));
        for (int start = 0; start < ordered.size(); start += cols) {
            int end = Math.min(ordered.size(), start + cols);
            List<T> row = new ArrayList<>(ordered.subList(start, end));
            row.sort(Comparator.comparingDouble((T u) -> u.getX() * rx + u.getZ() * rz));
            List<BlockPos> rowCells = new ArrayList<>(cells.subList(start, end));
            rowCells.sort(Comparator.comparingDouble((BlockPos c) -> c.getX() * rx + c.getZ() * rz));
            for (int i = 0; i < row.size(); i++)
                formation.add(new Pair<>(row.get(i), rowCells.get(i)));
        }
        return formation;
    }

}
