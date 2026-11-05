package com.solegendary.reignofnether.building.production;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every way the game (or a test) changes a production queue must move ProductionQueueList's epoch, or a per-tick
 * cache over the queues would count a stale queue: the queues are public lists changed from placements,
 * packets, match end and GameTests, through add / remove(0) / remove(item) / removeIf / clear and iterators. Reading a
 * queue must not move it.
 */
class ProductionQueueListTest {

    private static ProductionQueueList<String> queue(String... items) {
        ProductionQueueList<String> q = new ProductionQueueList<>();
        q.addAll(List.of(items));
        return q;
    }

    private static void moves(String how, Consumer<ProductionQueueList<String>> change) {
        ProductionQueueList<String> q = queue("a", "b", "c");
        long before = ProductionQueueList.epoch();
        change.accept(q);
        assertTrue(ProductionQueueList.epoch() > before, how + " did not move the epoch");
    }

    @Test
    void every_change_moves_the_epoch() {
        moves("add", q -> q.add("d"));
        moves("add at", q -> q.add(0, "d"));
        moves("addAll", q -> q.addAll(List.of("d", "e")));
        moves("addAll at", q -> q.addAll(1, List.of("d")));
        moves("set", q -> q.set(0, "z"));
        moves("remove(0)", q -> q.remove(0));
        moves("remove(item)", q -> q.remove("b"));
        moves("removeAll", q -> q.removeAll(List.of("a")));
        moves("retainAll", q -> q.retainAll(List.of("a")));
        moves("removeIf", q -> q.removeIf(s -> s.equals("c")));
        moves("replaceAll", q -> q.replaceAll(s -> s + "!"));
        moves("sort", q -> q.sort(null));
        moves("clear", List::clear);
        moves("iterator remove", q -> {
            Iterator<String> it = q.iterator();
            it.next();
            it.remove();
        });
        moves("list iterator add", q -> {
            ListIterator<String> it = q.listIterator();
            it.next();
            it.add("x");
        });
        moves("list iterator set", q -> {
            ListIterator<String> it = q.listIterator();
            it.next();
            it.set("x");
        });
        moves("subList clear", q -> q.subList(0, 2).clear());
        moves("subList add", q -> q.subList(0, 2).add("x"));
        moves("subList remove", q -> q.subList(0, 2).remove(0));
    }

    @Test
    void reading_does_not_move_the_epoch() {
        ProductionQueueList<String> q = queue("a", "b");
        long before = ProductionQueueList.epoch();
        int n = 0;
        for (String s : q)
            n += s.length();
        n += q.size() + q.indexOf("b") + (q.contains("a") ? 1 : 0) + q.get(0).length();
        assertEquals(before, ProductionQueueList.epoch(), "reading the queue moved the epoch");
        assertEquals(2 + 2 + 1 + 1 + 1, n);
    }

    @Test
    void it_is_still_an_ordinary_list() {
        ProductionQueueList<String> q = queue("a", "b", "c");
        q.remove(0);
        q.add("d");
        q.removeIf(s -> s.equals("c"));
        assertEquals(List.of("b", "d"), q);
    }
}
