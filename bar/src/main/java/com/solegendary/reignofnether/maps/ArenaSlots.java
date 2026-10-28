package com.solegendary.reignofnether.maps;

/**
 * Where each successive match arena goes on a server that keeps hosting new games (issue #35, Joisco: "an option to
 * reset the map, or move to a new location, so a server can keep hosting new games"). Pure arithmetic - no world, no
 * Minecraft classes - so the sequence is unit-tested (ArenaSlotsTest).
 * <p>
 * The world is cut into square slots on a grid, one arena per slot. Slot 0 is the arena the first match was played on
 * (the grid's base); slots 1, 2, ... follow a square spiral outwards from it, so the nearest unused ground is always
 * taken next and no slot is ever handed out twice. The pitch (the distance between two slot centres) is a whole
 * arena plus a margin, sized for the largest arena any lobby setting can build ({@link #MAX_HALF_EXTENT}), so two
 * arenas never touch whatever size they were - which is also what keeps one match's world border, fog snapshot and
 * navmesh clear of the last one's.
 * <p>
 * Deterministic and saved with the world ({@link Cursor#toInts}, ArenaOrigins): a server that restarts carries on with
 * the slot after the last and never reuses ground. The old arenas are simply left where they are (no regeneration, no
 * world reload); their chunks unload and save like any others.
 */
public final class ArenaSlots {
    private ArenaSlots() { }

    /**
     * Half the width of the widest arena (boundary wall and gatehouses included) any lobby setting can build: the
     * biggest lobby wall is 560, a 1024 half-extent leaves room for a random-size wall (rolled up to about 700) and
     * the widest ring a full lobby makes on a pre-made map (NewMatchCases checks every preset at the lobby's 16 seats
     * against this).
     */
    public static final int MAX_HALF_EXTENT = 1024;
    /** Empty ground kept between any two arenas, so a view distance of chunks at the edge of one never shows the next. */
    public static final int MARGIN = 512;
    /** The distance between two slot centres. */
    public static final int PITCH = pitch(MAX_HALF_EXTENT, MARGIN);

    /** Slot spacing for arenas up to {@code halfExtent} either side of their centre and {@code margin} of empty ground. */
    public static int pitch(int halfExtent, int margin) {
        return 2 * halfExtent + margin;
    }

    /**
     * The grid cell of {@code slot} on the square spiral: (0,0), then ring 1 starting east of the base and walking
     * north (+z), west, south and east round it, then ring 2, and so on. Consecutive slots are always neighbours.
     */
    public static int[] cell(int slot) {
        if (slot <= 0)
            return new int[] { 0, 0 };
        long s = slot;
        long k = 1;
        while ((2 * k + 1) * (2 * k + 1) <= s)   // ring k holds slots (2k-1)^2 .. (2k+1)^2 - 1
            k++;
        long i = s - (2 * k - 1) * (2 * k - 1);   // 0 .. 8k-1 along the ring
        long side = i / (2 * k), off = i % (2 * k);
        long x, z;
        switch ((int) side) {
            case 0 -> { x = k; z = -k + 1 + off; }       // east edge, going +z
            case 1 -> { x = k - 1 - off; z = k; }        // north edge, going -x
            case 2 -> { x = -k; z = k - 1 - off; }       // west edge, going -z
            default -> { x = -k + 1 + off; z = -k; }     // south edge, going +x
        }
        return new int[] { (int) x, (int) z };
    }

    /** The block centre x, z of {@code slot} on the grid whose slot 0 is at ({@code baseX}, {@code baseZ}). */
    public static int[] origin(int baseX, int baseZ, int slot) {
        int[] c = cell(slot);
        return new int[] { baseX + c[0] * PITCH, baseZ + c[1] * PITCH };
    }

    /**
     * The slots a world has handed out so far: the grid's base (the arena of the match that was being played when the
     * first shift came) and the slot in use. Plain ints so ArenaOrigins can save it and a unit test can round-trip it.
     */
    public static final class Cursor {
        static final int VERSION = 1;

        private boolean hasBase = false;
        private int baseX, baseZ;
        private int slot = -1;

        /** The slot in use (0 is the base arena), or -1 before the first shift. */
        public int slot() {
            return slot;
        }

        /**
         * Moves on to the next slot and returns its centre x, z. The very first shift anchors the grid at
         * ({@code hintX}, {@code hintZ}) - where the arena just played stood; after that the hint is ignored, so the
         * grid never moves and no slot can come up twice.
         */
        public int[] shift(int hintX, int hintZ) {
            if (!hasBase) {
                hasBase = true;
                baseX = hintX;
                baseZ = hintZ;
                slot = 0;
            }
            slot++;
            return origin(baseX, baseZ, slot);
        }

        /** The centre x, z of the slot in use, or null before the first shift. */
        public int[] current() {
            return slot < 0 ? null : origin(baseX, baseZ, slot);
        }

        /** {version, hasBase, baseX, baseZ, slot}. */
        public int[] toInts() {
            return new int[] { VERSION, hasBase ? 1 : 0, baseX, baseZ, slot };
        }

        /** A cursor from {@link #toInts}; a missing, short, foreign or inconsistent save gives a fresh one. */
        public static Cursor fromInts(int[] saved) {
            Cursor c = new Cursor();
            if (saved == null || saved.length < 5 || saved[0] != VERSION)
                return c;
            boolean base = saved[1] == 1;
            int slot = saved[4];
            if (slot < -1 || (slot >= 0 && !base))
                return c;
            c.hasBase = base;
            c.baseX = saved[2];
            c.baseZ = saved[3];
            c.slot = base ? Math.max(slot, 0) : -1;
            return c;
        }
    }
}
