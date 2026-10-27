package com.solegendary.reignofnether.hud;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** AC-1: idle units land in exactly one of the worker / T2 builder / commander buttons. */
class IdleButtonsTest {

    // id, commander, t2
    private record U(int id, boolean commander, boolean t2) { }

    @Test
    void ac1_buckets_do_not_overlap_and_cover_everything() {
        List<U> idle = List.of(
            new U(1, false, false), new U(2, false, true), new U(3, true, false),
            new U(4, false, false), new U(5, false, true),
            new U(6, true, true));          // a commander of a T2 type is still the commander
        IdleButtons.Split<U> s = IdleButtons.split(idle, U::commander, U::t2);

        assertEquals(List.of(1, 4), ids(s.workers));
        assertEquals(List.of(2, 5), ids(s.t2Builders));
        assertEquals(List.of(3, 6), ids(s.commanders));

        Set<Integer> seen = new HashSet<>();
        for (List<U> bucket : List.of(s.workers, s.t2Builders, s.commanders))
            for (U u : bucket)
                assertTrue(seen.add(u.id()), "unit " + u.id() + " is in two buttons");
        assertEquals(idle.size(), seen.size(), "every idle unit is in some button");
    }

    @Test
    void ac1_empty_input_gives_empty_buckets() {
        IdleButtons.Split<U> s = IdleButtons.split(List.of(), U::commander, U::t2);
        assertTrue(s.workers.isEmpty() && s.t2Builders.isEmpty() && s.commanders.isEmpty());
    }

    @Test
    void commander_press_jumps_on_double_press_or_when_already_selected() {
        assertFalse(IdleButtons.shouldJump(0, 10_000, false));
        assertTrue(IdleButtons.shouldJump(9_800, 10_000, false));
        assertFalse(IdleButtons.shouldJump(5_000, 10_000, false));
        assertTrue(IdleButtons.shouldJump(0, 10_000, true));
    }

    @Test
    void commander_health_percent_is_clamped() {
        assertEquals(50, IdleButtons.healthPercent(50, 100));
        assertEquals(0, IdleButtons.healthPercent(-3, 100));
        assertEquals(100, IdleButtons.healthPercent(150, 100));
        assertEquals(0, IdleButtons.healthPercent(10, 0));
    }

    private static List<Integer> ids(List<U> l) {
        List<Integer> out = new ArrayList<>();
        for (U u : l) out.add(u.id());
        return out;
    }
}
