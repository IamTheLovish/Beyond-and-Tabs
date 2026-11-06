package com.solegendary.reignofnether.building;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * The order a structure's blocks are laid in: layer by layer, bottom up, and in each layer the full cubes first, then
 * the rest (stairs, slabs, panes, vines, lanterns...). Vanilla's StructureTemplate lays a structure the same way, and
 * for the same reason: a vine laid before the full block it hangs on turns to air as soon as a neighbour is set beside
 * it, and a building that loses a block so can never count as built (#522's round-13 Heartwood Hall, build
 * 42139034765). The sort is stable, so each group keeps the file's own order.
 */
public final class LayOrder {

    private LayOrder() {
    }

    /** Sorts blocks into lay order: by y, then the blocks {@code full} accepts before the rest, each kept in order. */
    public static <T> void sort(List<T> blocks, ToIntFunction<T> y, Predicate<T> full) {
        blocks.sort(Comparator.comparingInt(y).thenComparingInt(b -> full.test(b) ? 0 : 1));   // List.sort is stable
    }
}
