package com.solegendary.reignofnether.maps;

/**
 * Where each successive match arena goes on a server that keeps hosting new games (issue #35, Joisco: "an option to
 * reset the map, or move to a new location, so a server can keep hosting new games"). NOT BUILT YET: this is the
 * stub the tests are written against (lovish's tests-first rule, Oct 22 19:58); every method returns what a world
 * with no such feature does - the same ground again.
 */
public final class ArenaSlots {
    private ArenaSlots() { }

    public static final int MAX_HALF_EXTENT = 1024;
    public static final int MARGIN = 512;
    public static final int PITCH = pitch(MAX_HALF_EXTENT, MARGIN);

    public static int pitch(int halfExtent, int margin) {
        return 0;
    }

    public static int[] cell(int slot) {
        return new int[] { 0, 0 };
    }

    public static int[] origin(int baseX, int baseZ, int slot) {
        return new int[] { baseX, baseZ };
    }

    /** The arena slots a world has handed out so far. */
    public static final class Cursor {
        private int baseX, baseZ;

        public int slot() {
            return -1;
        }

        public int[] shift(int hintX, int hintZ) {
            baseX = hintX;
            baseZ = hintZ;
            return new int[] { baseX, baseZ };
        }

        public int[] current() {
            return null;
        }

        public int[] toInts() {
            return new int[0];
        }

        public static Cursor fromInts(int[] saved) {
            return new Cursor();
        }
    }
}
