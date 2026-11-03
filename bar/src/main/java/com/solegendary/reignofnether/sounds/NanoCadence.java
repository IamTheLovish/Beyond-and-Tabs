package com.solegendary.reignofnether.sounds;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * When a builder's construction granule plays (pure, so NanoCadenceTest can simulate a minute of builders).
 *
 * <p>lovish, Oct 29: "u can hear workers buzzing as they build at its annoying". Every builder's beam sends a NANO
 * event every BarFx.NANO_INTERVAL (8) ticks and the client played one granule per 10-tick gap: with anyone building, a
 * granule exactly twice a second all match long (120 a minute, zero variation) - a metronome, heard as a buzz. Now each
 * granule, as it plays, draws the gap to the next one between MIN_GAP and MIN_GAP + SPREAD (one draw per granule - a
 * fresh roll per event bunched the gaps at the minimum when many builders send events), so the loop is about one
 * granule every 1.5-2 s, never regular, and no busier with twenty builders than with three (one gap per faction).
 */
public final class NanoCadence {

    /** Fewest ticks between two granules of one faction's loop. */
    public static final int MIN_GAP = 20;
    /** How much longer than MIN_GAP a gap may run (each granule draws the next one). */
    public static final int SPREAD = 40;

    private NanoCadence() { }

    /** The gap (ticks) before the next granule, drawn as one plays. @param roll a uniform random in [0, 1) */
    public static int nextGap(float roll) {
        return MIN_GAP + (int) (roll * SPREAD);
    }

    /** Whether a beam event at {@code tick} plays: the gap drawn at the last granule has passed. */
    public static boolean shouldPlay(long tick, long lastPlay, int gap) {
        return tick - lastPlay >= gap;
    }

    /**
     * One faction's loop. A beam event whose gap has passed is offered to {@code play}, which says whether the granule
     * really played (SfxClient.playAt: out of reach, over budget, fogged or too quiet - it did not); only a granule that
     * played books the gap and draws the next one. A builder out of earshot never uses up the loop: review r1 on PR 409
     * found the gap booked before playAt, so same-faction builders 48-200 blocks away (BarFx sends NANO that far, and
     * own units are never fogged) took the slot - the builder in view fell from ~28 granules a minute to 2.7 with ten
     * far ones.
     */
    public static final class Loop {
        private long last;
        private int gap;

        /** Offers a beam event at {@code tick}; true if its granule played (and booked the gap). */
        public boolean offer(long tick, BooleanSupplier play, IntSupplier nextGap) {
            if (!shouldPlay(tick, last, gap) || !play.getAsBoolean())
                return false;
            last = tick;
            gap = nextGap.getAsInt();
            return true;
        }

        public void reset() {
            last = 0;
            gap = 0;
        }
    }
}
