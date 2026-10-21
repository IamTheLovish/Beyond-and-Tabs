package com.solegendary.reignofnether.worldmemory;

import net.minecraft.network.FriendlyByteBuf;

import java.util.Arrays;

/**
 * How each channel of the ground memory field fades (claude/world-that-remembers.md, sections 3.1, 4.1, 4.2, 9.1).
 * <p>
 * The defaults are the spec's recommendations, kept as one place for lovish to change ({@link #DEFAULT}, and the
 * [World memory] block of the common config, which overrides them at server start): scars last the whole match;
 * scorch has a 6-minute half-life down to a 30% floor (reached after ~10 minutes), craters fade to 50% over ~20
 * minutes, trample (slice 2) halves every 8 minutes to nothing, stains (slice 6) are gone in ~3 minutes.
 * <p>
 * Decay runs in {@link #QUANTUM}-tick steps at absolute boundaries (GroundMemory.decayTo). A step is a table lookup:
 * {@code round(v * 2^(-QUANTUM*stepEvery/halfLife))}, never stalling above the floor (a value that would round to
 * itself drops by one), never below the floor. The crater nibble (0-15) steps only every {@link #CRATER_STEP_EVERY}
 * quanta so its coarse range can follow a 20-minute half-life. Tables are built with StrictMath and integer rounding,
 * so a server and its clients compute identical bytes from identical parameters - which is why the parameters ride in
 * every sync packet instead of being read from each side's own config file.
 */
public final class DecayParams {

    /** Ticks per decay step: 20 s. Shorter steps would be smoother; this keeps a 6-minute half-life at 18 steps. */
    public static final int QUANTUM = 400;
    /** The crater nibble steps every this many quanta (320 s): 15 -> 8 in three steps, close to the 20-minute spec. */
    public static final int CRATER_STEP_EVERY = 16;

    /** Half-lives in ticks and floors in channel units, by GroundMemory channel. The spec's defaults. */
    public static final int SCORCH_HALF_LIFE = 6 * 60 * 20, SCORCH_FLOOR = 77;      // 30% of 255
    public static final int TRAMPLE_HALF_LIFE = 8 * 60 * 20, TRAMPLE_FLOOR = 0;
    public static final int CRATER_HALF_LIFE = 20 * 60 * 20, CRATER_FLOOR = 8;      // 50% of 15
    public static final int STAIN_HALF_LIFE = 36 * 20, STAIN_FLOOR = 0;

    public static final DecayParams DEFAULT = new DecayParams(
        new int[] { SCORCH_HALF_LIFE, TRAMPLE_HALF_LIFE, CRATER_HALF_LIFE, STAIN_HALF_LIFE },
        new int[] { SCORCH_FLOOR, TRAMPLE_FLOOR, CRATER_FLOOR, STAIN_FLOOR });

    final int[] halfLife = new int[GroundMemory.CHANNELS];
    final int[] floor = new int[GroundMemory.CHANNELS];
    /** One step of each byte channel (256 entries); the crater nibble's own 16-entry table. */
    private final byte[][] step = new byte[GroundMemory.CHANNELS][];
    private final byte[] craterStep = new byte[16];

    public DecayParams(int[] halfLifeTicks, int[] floors) {
        for (int c = 0; c < GroundMemory.CHANNELS; c++) {
            halfLife[c] = Math.max(1, halfLifeTicks[c]);
            floor[c] = Math.max(0, Math.min(c == GroundMemory.CRATER_STAIN ? GroundMemory.CRATER_MAX : 255, floors[c]));
        }
        for (int c = 0; c < GroundMemory.CHANNELS; c++)
            if (c != GroundMemory.CRATER_STAIN)
                step[c] = table(256, halfLife[c], 1, floor[c]);
        byte[] crater = table(16, halfLife[GroundMemory.CRATER_STAIN], CRATER_STEP_EVERY, floor[GroundMemory.CRATER_STAIN]);
        System.arraycopy(crater, 0, craterStep, 0, 16);
    }

    public int halfLife(int channel) {
        return halfLife[channel];
    }

    public int floor(int channel) {
        return floor[channel];
    }

    /** The per-step multiplier of a channel: 2^(-QUANTUM*stepEvery/halfLife). StrictMath: the same on every JVM. */
    static double factor(int halfLifeTicks, int stepEvery) {
        return StrictMath.pow(2.0, -(double) QUANTUM * stepEvery / halfLifeTicks);
    }

    private static byte[] table(int size, int halfLifeTicks, int stepEvery, int floor) {
        byte[] t = new byte[size];
        double a = factor(halfLifeTicks, stepEvery);
        for (int v = 0; v < size; v++) {
            int nv = v;
            if (v > floor) {
                nv = (int) Math.round(v * a);
                if (nv == v)
                    nv = v - 1;   // never stall above the floor
                nv = Math.max(nv, floor);
            }
            t[v] = (byte) nv;
        }
        return t;
    }

    /** The quantum boundary at or below {@code tick}. */
    public static long boundary(long tick) {
        return Math.floorDiv(tick, QUANTUM) * (long) QUANTUM;
    }

    /** One step of a byte channel (public for the tests' exactness check). */
    public int stepOnce(int channel, int value) {
        return channel == GroundMemory.CRATER_STAIN ? -1 : step[channel][value & 0xFF] & 0xFF;
    }

    public int stepCraterOnce(int depth) {
        return craterStep[depth & 15] & 0xFF;
    }

    /** How many steps a channel takes between quantum indices q0 and q1 (crater: every CRATER_STEP_EVERY quanta). */
    static int stepsBetween(int channel, long q0, long q1) {
        if (q1 <= q0)
            return 0;
        if (channel == GroundMemory.CRATER_STAIN)
            return (int) Math.min(16, Math.floorDiv(q1, CRATER_STEP_EVERY) - Math.floorDiv(q0, CRATER_STEP_EVERY));
        // a monotone map on 256 values reaches its fixed point within 256 steps: more steps change nothing
        return (int) Math.min(256, q1 - q0);
    }

    /**
     * Applies the steps between quantum indices q0 and q1 to every cell of {@code cells} in place; true if any byte
     * changed.
     */
    boolean decaySteps(byte[] cells, long q0, long q1) {
        boolean changed = false;
        for (int c = 0; c < GroundMemory.CHANNELS; c++) {
            int n = stepsBetween(c, q0, q1);
            if (n == 0)
                continue;
            if (c == GroundMemory.CRATER_STAIN) {
                for (int i = c; i < cells.length; i += GroundMemory.CHANNELS) {
                    int b = cells[i] & 0xFF, depth = b >> 4;
                    if (depth == 0)
                        continue;
                    for (int s = 0; s < n; s++)
                        depth = craterStep[depth] & 0xFF;
                    int nb = (depth << 4) | (b & 15);
                    if (nb != b) {
                        cells[i] = (byte) nb;
                        changed = true;
                    }
                }
            } else {
                byte[] t = step[c];
                for (int i = c; i < cells.length; i += GroundMemory.CHANNELS) {
                    int v = cells[i] & 0xFF;
                    if (v == 0)
                        continue;
                    int nv = v;
                    for (int s = 0; s < n; s++)
                        nv = t[nv] & 0xFF;
                    if (nv != v) {
                        cells[i] = (byte) nv;
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    /** The steps between quantum indices applied to one packed cell (GroundMemory.packed layout). */
    int decayCell(int packed, long q0, long q1) {
        if (q1 <= q0)
            return packed;
        int out = 0;
        for (int c = 0; c < GroundMemory.CHANNELS; c++) {
            int b = (packed >>> (8 * c)) & 0xFF;
            int n = stepsBetween(c, q0, q1);
            if (c == GroundMemory.CRATER_STAIN) {
                int depth = b >> 4;
                for (int s = 0; s < n && depth > 0; s++)
                    depth = craterStep[depth] & 0xFF;
                b = (depth << 4) | (b & 15);
            } else {
                byte[] t = step[c];
                for (int s = 0; s < n && b > 0; s++)
                    b = t[b] & 0xFF;
            }
            out |= b << (8 * c);
        }
        return out;
    }

    // ------------------------------------------------------------------ wire

    public void encode(FriendlyByteBuf buf) {
        for (int c = 0; c < GroundMemory.CHANNELS; c++) {
            buf.writeVarInt(halfLife[c]);
            buf.writeByte(floor[c]);
        }
    }

    public static DecayParams decode(FriendlyByteBuf buf) {
        int[] h = new int[GroundMemory.CHANNELS], f = new int[GroundMemory.CHANNELS];
        for (int c = 0; c < GroundMemory.CHANNELS; c++) {
            h[c] = buf.readVarInt();
            f[c] = buf.readUnsignedByte();
        }
        return new DecayParams(h, f);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DecayParams p && Arrays.equals(halfLife, p.halfLife) && Arrays.equals(floor, p.floor);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(halfLife) * 31 + Arrays.hashCode(floor);
    }

    @Override
    public String toString() {
        return "DecayParams{halfLife=" + Arrays.toString(halfLife) + ", floor=" + Arrays.toString(floor) + "}";
    }
}
