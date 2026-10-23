package com.solegendary.reignofnether.maps;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The slot sequence behind "a new match on a running server" (issue #35, Joisco). Written before the code
 * (lovish's rule, Oct 22 19:58): the sequence is deterministic, never repeats, never lets two arenas touch (given the
 * largest arena the lobby can build plus a margin) and survives a save and a restart.
 */
class ArenaSlotsTest {

    private static final int SLOTS = 2500;   // 25 rings round the first arena: far more matches than a server will see

    private static int cheb(int[] a, int[] b) {
        return Math.max(Math.abs(a[0] - b[0]), Math.abs(a[1] - b[1]));
    }

    private static String key(int[] p) {
        return p[0] + "," + p[1];
    }

    @Test
    void theFirstSlotIsTheBaseItself() {
        assertArrayEquals(new int[] { 0, 0 }, ArenaSlots.cell(0));
        assertArrayEquals(new int[] { 700, -300 }, ArenaSlots.origin(700, -300, 0));
    }

    @Test
    void theSpiralWalksRingByRingWithNoGapsAndNoRepeats() {
        Set<String> seen = new HashSet<>();
        int[] prev = null;
        for (int s = 0; s < SLOTS; s++) {
            int[] c = ArenaSlots.cell(s);
            assertTrue(seen.add(key(c)), "slot " + s + " repeats cell " + key(c));
            if (prev != null)
                assertEquals(1, Math.abs(c[0] - prev[0]) + Math.abs(c[1] - prev[1]),
                    "slot " + s + " is not next to slot " + (s - 1) + ": " + key(prev) + " -> " + key(c));
            prev = c;
        }
        // the first nine slots are the 3x3 block round the base, the first 25 the 5x5: nearest ground is used first
        for (int ring = 1; ring <= 4; ring++) {
            int n = (2 * ring + 1) * (2 * ring + 1);
            int far = 0;
            for (int s = 0; s < n; s++)
                far = Math.max(far, Math.max(Math.abs(ArenaSlots.cell(s)[0]), Math.abs(ArenaSlots.cell(s)[1])));
            assertEquals(ring, far, "the first " + n + " slots must fill the " + (2 * ring + 1) + "x" + (2 * ring + 1) + " block");
        }
    }

    @Test
    void noTwoArenasEverTouchEvenForTheLargestArena() {
        int half = ArenaSlots.MAX_HALF_EXTENT;
        assertEquals(2 * half + ArenaSlots.MARGIN, ArenaSlots.PITCH, "the pitch is a whole arena plus the margin");
        List<int[]> origins = new ArrayList<>();
        for (int s = 0; s < 400; s++)
            origins.add(ArenaSlots.origin(-12_345, 6_789, s));
        for (int i = 0; i < origins.size(); i++)
            for (int j = i + 1; j < origins.size(); j++)
                assertTrue(cheb(origins.get(i), origins.get(j)) >= 2 * half + ArenaSlots.MARGIN,
                    "slots " + i + " and " + j + " are only " + cheb(origins.get(i), origins.get(j)) + " apart");
    }

    @Test
    void theMarginHoldsForEveryLobbyArenaSize() {
        // the lobby's four wall radii (SkirmishServerboundPacket.arenaRadius) plus a gatehouse's reach
        for (int radius : new int[] { 230, 320, 420, 560 }) {
            int half = radius + 8;
            for (int s = 1; s < 60; s++)
                assertTrue(cheb(ArenaSlots.origin(0, 0, 0), ArenaSlots.origin(0, 0, s)) - 2 * half >= ArenaSlots.MARGIN,
                    "a " + radius + " arena in slot " + s + " is closer than the margin to the base arena");
        }
        assertTrue(ArenaSlots.MAX_HALF_EXTENT >= 560 + 8, "the slot must hold the largest lobby arena");
    }

    @Test
    void theCursorNeverRepeatsGroundAndLeavesTheOldArena() {
        ArenaSlots.Cursor c = new ArenaSlots.Cursor();
        assertEquals(-1, c.slot());
        assertNull(c.current(), "nothing is claimed before the first shift");
        Set<String> seen = new HashSet<>();
        int[] base = { 100, 200 };
        seen.add(key(base));   // the arena the first match was played on
        for (int i = 1; i <= 500; i++) {
            int[] o = c.shift(100, 200);
            assertNotNull(o);
            assertEquals(i, c.slot());
            assertTrue(seen.add(key(o)), "shift " + i + " went back to " + key(o));
            assertTrue(cheb(o, base) >= ArenaSlots.PITCH, "shift " + i + " is on top of the first arena");
            assertArrayEquals(o, c.current());
        }
    }

    @Test
    void theSameBaseGivesTheSameSequenceAlways() {
        ArenaSlots.Cursor a = new ArenaSlots.Cursor(), b = new ArenaSlots.Cursor();
        for (int i = 0; i < 60; i++)
            assertArrayEquals(a.shift(-40, 900), b.shift(-40, 900), "shift " + (i + 1) + " differs between two runs");
    }

    @Test
    void theFirstShiftAnchorsTheGridAndLaterHintsAreIgnored() {
        ArenaSlots.Cursor c = new ArenaSlots.Cursor();
        int[] first = c.shift(1000, 1000);
        int[] second = c.shift(-5000, 7777);   // the caller stands somewhere else now: the grid must not move
        assertArrayEquals(ArenaSlots.origin(1000, 1000, 1), first);
        assertArrayEquals(ArenaSlots.origin(1000, 1000, 2), second);
    }

    @Test
    void aSavedCursorResumesWhereItStoppedAndNeverHandsOutUsedGround() {
        ArenaSlots.Cursor uninterrupted = new ArenaSlots.Cursor();
        ArenaSlots.Cursor before = new ArenaSlots.Cursor();
        Set<String> used = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            used.add(key(before.shift(30, -60)));
            uninterrupted.shift(30, -60);
        }
        // the server stops and starts: the cursor comes back from its saved ints
        ArenaSlots.Cursor after = ArenaSlots.Cursor.fromInts(before.toInts());
        assertEquals(5, after.slot());
        assertArrayEquals(before.current(), after.current(), "the arena in use is still the same one");
        for (int i = 0; i < 20; i++) {
            int[] expected = uninterrupted.shift(30, -60);
            int[] got = after.shift(999, 999);   // the hint is not used again: the base was saved
            assertArrayEquals(expected, got, "shift " + (6 + i) + " after the restart differs from an uninterrupted run");
            assertTrue(used.add(key(got)), "shift " + (6 + i) + " after the restart reused ground");
        }
    }

    @Test
    void aMissingOrDamagedSaveStartsClean() {
        for (int[] bad : new int[][] { null, new int[0], new int[] { 1 }, new int[] { 99, 1, 0, 0, 5 }, new int[] { 1, 1, 0, 0, -9 } }) {
            ArenaSlots.Cursor c = ArenaSlots.Cursor.fromInts(bad);
            assertEquals(-1, c.slot(), "a damaged save must not invent a slot");
            assertNull(c.current());
            assertFalse(c.shift(5, 5) == null);
            assertEquals(1, c.slot());
        }
    }
}
