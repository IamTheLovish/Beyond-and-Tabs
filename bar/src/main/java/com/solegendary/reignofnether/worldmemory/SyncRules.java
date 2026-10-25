package com.solegendary.reignofnether.worldmemory;

import java.util.Arrays;

/**
 * Which cells of a chunk's field one viewer must be sent this pass: the pure rule of GroundMemorySync, so the headless
 * GameTests check it without players or packets (the way Audience.recipients and MapDrawRules are tested).
 * <p>
 * A viewer holds, per chunk, {@link State}: the field generation its copy is at, and {@code current} - the columns
 * whose value it holds AT that generation. Under fog of war a viewer of an edge chunk sees some columns and not others
 * (the fog's per-column mask); the fogged columns are never sent (the client keeps what it last saw, like terrain
 * under the veil), and are remembered as not current, so they go out the moment they come into view - even if nothing
 * is stamped meanwhile (a scorch that landed under the fog shows when the fog lifts).
 * <ul>
 *   <li>No state (first sight, or back after losing sight): every visible column.</li>
 *   <li>At the previous generation: the cells stamped since (the pass's dirty mask), plus visible columns not yet
 *       current. A delta.</li>
 *   <li>At the current generation: only visible columns not yet current.</li>
 *   <li>Further behind (missed passes out of range): every visible column.</li>
 * </ul>
 */
public final class SyncRules {
    private SyncRules() { }

    /** One viewer's knowledge of one chunk. */
    public static final class State {
        public int generation;
        public final long[] current = new long[4];

        public State(int generation) {
            this.generation = generation;
        }
    }

    /**
     * The cells to send: a 256-bit mask (null when nothing is due). {@code dirty} is this pass's stamped cells (null
     * when the chunk was not stamped since the last pass), {@code visible} the columns the viewer may see.
     */
    public static long[] plan(State have, int generation, int previousGeneration, long[] dirty, long[] visible) {
        if (visible == null || !GroundMemory.anyBit(visible))
            return null;
        long[] send = new long[4];
        if (have == null || (have.generation != generation && have.generation != previousGeneration)) {
            System.arraycopy(visible, 0, send, 0, 4);
        } else {
            boolean behind = have.generation != generation;
            for (int i = 0; i < 4; i++) {
                long notCurrent = visible[i] & ~have.current[i];
                long changed = behind && dirty != null ? (visible[i] & dirty[i]) : 0;
                send[i] = notCurrent | changed;
            }
        }
        return GroundMemory.anyBit(send) ? send : null;
    }

    /**
     * The viewer's state after this pass: at {@code generation}, holding what it held and still stands (a viewer one
     * generation behind keeps its unchanged cells, loses the stamped ones it was not sent) plus {@code sent} (null =
     * nothing sent). Returns null when the viewer knows nothing current (nothing sent on first sight).
     */
    public static State after(State have, int generation, int previousGeneration, long[] dirty, long[] sent) {
        if (have == null) {
            if (sent == null)
                return null;
            State s = new State(generation);
            System.arraycopy(sent, 0, s.current, 0, 4);
            return s;
        }
        if (have.generation != generation) {
            if (have.generation == previousGeneration) {
                if (dirty != null)
                    for (int i = 0; i < 4; i++)
                        have.current[i] &= ~dirty[i];
            } else {
                Arrays.fill(have.current, 0L);
            }
            have.generation = generation;
        }
        if (sent != null)
            for (int i = 0; i < 4; i++)
                have.current[i] |= sent[i];
        return have;
    }
}
