package com.solegendary.reignofnether.worldmemory;

import net.minecraft.nbt.CompoundTag;

import java.util.Arrays;

/**
 * The ground memory field of one chunk (claude/world-that-remembers.md, section 3.1): 256 cells, one per block column,
 * four bytes each. Blasts, heavy impacts, deaths and collapses stamp into it on the server; the client keeps a copy
 * and draws it as a ground-hugging decal mesh (GroundMemoryClient). It is what makes "the battlefield keeps a record":
 * today's BarFx scorch marks lived 12 s on the client that saw the blast; this lives in the chunk's NBT for the match.
 * <p>
 * Channels (one byte each, {@link #CHANNELS} per cell, cell index = (localX << 4) | localZ):
 * <ul>
 *   <li>{@link #SCORCH}: 0-255 burnt ground (slice 1);</li>
 *   <li>{@link #TRAMPLE}: 0-255 worn ground (slice 2 writes it; the format and decay are here so it plugs in);</li>
 *   <li>{@link #CRATER_STAIN}: high nibble crater depth 0-15 (slice 1), low nibble stain kind 0-15 (slice 6);</li>
 *   <li>{@link #STAIN}: 0-255 stain strength (slice 6).</li>
 * </ul>
 * <b>Writes are stamps</b> (a soft disc added with saturation, {@link #addScorchDisc} / {@link #addCraterDisc}):
 * fifty shells on one spot are one deeper burn, never a pile of discs (the "black blob" clip that capped BarFx at 24
 * marks).
 * <p>
 * <b>Decay is lazy and exact</b>: nothing ticks the field. {@link #decayTo} brings a chunk up to date when it is
 * stamped, synced, saved or drawn, in {@link DecayParams#QUANTUM}-tick steps at absolute boundaries (quantum index =
 * floorDiv(gameTime, QUANTUM)), each step the same per-channel table ({@link DecayParams}). The result depends only
 * on the value and the number of boundaries crossed, never on how often decay was asked for - so the server and every
 * client, each decaying its own copy whenever it likes, agree byte for byte. Each channel has a floor (scorch settles
 * at ~30% and stays for the match), so the ground remembers without staying black.
 * <p>
 * Plain data, no Minecraft classes but NBT: the headless GameTests exercise everything here.
 */
public final class GroundMemory {

    public static final int CELLS = 256, CHANNELS = 4, BYTES = CELLS * CHANNELS;
    public static final int SCORCH = 0, TRAMPLE = 1, CRATER_STAIN = 2, STAIN = 3;
    public static final int CRATER_MAX = 15;

    static final String TAG_CELLS = "cells", TAG_DECAY = "decay";

    /** The cells; null until the first stamp (an unmarked chunk costs a reference). */
    private byte[] cells;
    /** The quantum boundary (a multiple of {@link DecayParams#QUANTUM}) up to which {@link #cells} are decayed. */
    private long lastDecayTick;
    /**
     * Bumped by the server's sync pass when cells changed since the last pass; the client's copy of a chunk is "at" a
     * generation (GroundMemorySync). Unique over a server run, never saved: a reloaded chunk gets a fresh one and
     * every client re-syncs it (a snapshot is always safe).
     */
    int generation, previousGeneration;

    public GroundMemory(long now) {
        this.lastDecayTick = DecayParams.boundary(now);
    }

    // ------------------------------------------------------------------ reads

    public boolean isEmpty() {
        return cells == null;
    }

    /** Whether any cell holds any value (an all-zero field after decay is still "marked" until it is pruned). */
    public boolean hasAnyMark() {
        if (cells == null)
            return false;
        for (byte b : cells)
            if (b != 0)
                return true;
        return false;
    }

    public long lastDecayTick() {
        return lastDecayTick;
    }

    /** The raw cells (null for an unmarked chunk); the renderer and the codec read it in place. */
    public byte[] cells() {
        return cells;
    }

    public static int index(int localX, int localZ) {
        return ((localX & 15) << 4) | (localZ & 15);
    }

    /** Channel value 0-255 of a cell; 0 for an unmarked chunk. */
    public int get(int cell, int channel) {
        return cells == null ? 0 : cells[cell * CHANNELS + channel] & 0xFF;
    }

    public int scorch(int cell) {
        return get(cell, SCORCH);
    }

    public int trample(int cell) {
        return get(cell, TRAMPLE);
    }

    /** Crater depth 0-15 of a cell. */
    public int crater(int cell) {
        return get(cell, CRATER_STAIN) >> 4;
    }

    public int stainKind(int cell) {
        return get(cell, CRATER_STAIN) & 15;
    }

    public int stain(int cell) {
        return get(cell, STAIN);
    }

    // ------------------------------------------------------------------ writes

    private byte[] ensure() {
        if (cells == null)
            cells = new byte[BYTES];
        return cells;
    }

    /** Sets a channel outright (0-255; the crater nibble through {@link #setCrater}). */
    public void set(int cell, int channel, int value) {
        ensure()[cell * CHANNELS + channel] = (byte) Math.max(0, Math.min(255, value));
    }

    public void setCrater(int cell, int depth) {
        byte[] c = ensure();
        int i = cell * CHANNELS + CRATER_STAIN;
        c[i] = (byte) ((Math.max(0, Math.min(CRATER_MAX, depth)) << 4) | (c[i] & 15));
    }

    /** Adds to a byte channel with saturation; returns true if the cell changed. */
    public boolean add(int cell, int channel, int amount) {
        if (amount <= 0)
            return false;
        byte[] c = ensure();
        int i = cell * CHANNELS + channel;
        int v = c[i] & 0xFF;
        int nv = Math.min(255, v + amount);
        if (nv == v)
            return false;
        c[i] = (byte) nv;
        return true;
    }

    /** Raises the crater depth to at least {@code depth} (craters deepen, they never add past the max). */
    public boolean raiseCrater(int cell, int depth) {
        depth = Math.min(CRATER_MAX, depth);
        if (depth <= 0)
            return false;
        byte[] c = ensure();
        int i = cell * CHANNELS + CRATER_STAIN;
        int v = (c[i] & 0xFF) >> 4;
        if (depth <= v)
            return false;
        c[i] = (byte) ((depth << 4) | (c[i] & 15));
        return true;
    }

    /**
     * The weight of a soft disc at distance {@code d} from its centre: 1 inside {@code radius * core}, falling smoothly
     * to 0 at {@code radius}. Pure, so the stamp tests can check the shape.
     */
    public static float discWeight(double d, double radius, double core) {
        if (radius <= 0)
            return 0;
        double inner = radius * core;
        if (d <= inner)
            return 1;
        if (d >= radius)
            return 0;
        double t = (d - inner) / (radius - inner);
        return (float) (1 - t * t * (3 - 2 * t));   // smoothstep down
    }

    /**
     * Stamps a soft scorch disc into THIS chunk's cells: {@code peak} at the centre, fading to nothing at
     * {@code radius} (world block coordinates; the chunk's origin is {@code chunkX << 4, chunkZ << 4}).
     * {@code allowed} (a 256-bit mask, or null = all) is the caller's column filter (no water, not a roof far above
     * the blast). Returns the 256-bit mask of the cells that changed.
     */
    public long[] addScorchDisc(int chunkX, int chunkZ, double cx, double cz, double radius, int peak, long[] allowed) {
        return stampDisc(chunkX, chunkZ, cx, cz, radius, 0.35, peak, allowed, SCORCH);
    }

    /** Stamps a crater dish (depth 1-15 at the centre, a flat core then a smooth fall) the same way. */
    public long[] addCraterDisc(int chunkX, int chunkZ, double cx, double cz, double radius, int depth, long[] allowed) {
        return stampDisc(chunkX, chunkZ, cx, cz, radius, 0.3, depth, allowed, CRATER_STAIN);
    }

    private long[] stampDisc(int chunkX, int chunkZ, double cx, double cz, double radius, double core, int peak,
                             long[] allowed, int channel) {
        long[] changed = new long[4];
        if (peak <= 0 || radius <= 0)
            return changed;
        int ox = chunkX << 4, oz = chunkZ << 4;
        int x0 = Math.max(ox, (int) Math.floor(cx - radius)), x1 = Math.min(ox + 15, (int) Math.floor(cx + radius));
        int z0 = Math.max(oz, (int) Math.floor(cz - radius)), z1 = Math.min(oz + 15, (int) Math.floor(cz + radius));
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                int cell = index(x - ox, z - oz);
                if (allowed != null && !bit(allowed, cell))
                    continue;
                double dx = x + 0.5 - cx, dz = z + 0.5 - cz;
                float w = discWeight(Math.sqrt(dx * dx + dz * dz), radius, core);
                if (w <= 0)
                    continue;
                int amount = Math.round(peak * w);
                boolean did = channel == CRATER_STAIN ? raiseCrater(cell, amount) : add(cell, channel, amount);
                if (did)
                    setBit(changed, cell);
            }
        return changed;
    }

    // ------------------------------------------------------------------ decay

    /**
     * Brings the cells up to date at game time {@code now}: one table step per quantum boundary crossed since
     * {@link #lastDecayTick}. Returns true if any cell changed (the renderer rebuilds, the server marks nothing - a
     * decayed value needs no sync, every client decays its own copy the same way).
     */
    public boolean decayTo(long now, DecayParams params) {
        long boundary = DecayParams.boundary(now);
        if (boundary <= lastDecayTick)
            return false;
        long q0 = Math.floorDiv(lastDecayTick, DecayParams.QUANTUM), q1 = Math.floorDiv(boundary, DecayParams.QUANTUM);
        lastDecayTick = boundary;
        if (cells == null)
            return false;
        return params.decaySteps(cells, q0, q1);
    }

    /**
     * Decays a single packed cell (4 bytes, channel 0 in the low byte) from quantum index {@code q0} to {@code q1}:
     * what a client applies to an incoming cell whose chunk copy is already further decayed than the sender's.
     */
    public static int decayCell(int packed, long q0, long q1, DecayParams params) {
        return params.decayCell(packed, q0, q1);
    }

    /** Forces the decay clock (tests, and the client's copy taking a snapshot's clock). */
    public void setLastDecayTick(long tick) {
        this.lastDecayTick = DecayParams.boundary(tick);
    }

    // ------------------------------------------------------------------ cells as ints (codec, client)

    /** The 4 bytes of a cell packed little-endian: scorch in the low byte, stain in the high byte. */
    public int packed(int cell) {
        if (cells == null)
            return 0;
        int i = cell * CHANNELS;
        return (cells[i] & 0xFF) | ((cells[i + 1] & 0xFF) << 8) | ((cells[i + 2] & 0xFF) << 16) | ((cells[i + 3] & 0xFF) << 24);
    }

    public void setPacked(int cell, int packed) {
        byte[] c = ensure();
        int i = cell * CHANNELS;
        c[i] = (byte) packed;
        c[i + 1] = (byte) (packed >>> 8);
        c[i + 2] = (byte) (packed >>> 16);
        c[i + 3] = (byte) (packed >>> 24);
    }

    /** Replaces every cell (a snapshot); {@code data} must be {@link #BYTES} long. */
    public void setAll(byte[] data) {
        if (data == null || data.length != BYTES)
            throw new IllegalArgumentException("ground memory snapshot of " + (data == null ? "null" : data.length) + " bytes");
        cells = Arrays.copyOf(data, BYTES);
    }

    public byte[] copyCells() {
        return cells == null ? new byte[BYTES] : Arrays.copyOf(cells, BYTES);
    }

    /** Drops an all-zero field so the chunk's NBT loses the tag again. Returns true if it was dropped. */
    public boolean pruneIfBlank() {
        if (cells != null && !hasAnyMark()) {
            cells = null;
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ bit masks (256 cells = 4 longs)

    public static boolean bit(long[] mask, int cell) {
        return (mask[cell >> 6] & (1L << (cell & 63))) != 0;
    }

    public static void setBit(long[] mask, int cell) {
        mask[cell >> 6] |= 1L << (cell & 63);
    }

    public static boolean anyBit(long[] mask) {
        return mask != null && (mask[0] | mask[1] | mask[2] | mask[3]) != 0;
    }

    public static int bitCount(long[] mask) {
        return mask == null ? 0 : Long.bitCount(mask[0]) + Long.bitCount(mask[1]) + Long.bitCount(mask[2]) + Long.bitCount(mask[3]);
    }

    public static long[] fullMask() {
        return new long[] { -1L, -1L, -1L, -1L };
    }

    public static boolean isFull(long[] mask) {
        return mask != null && mask[0] == -1L && mask[1] == -1L && mask[2] == -1L && mask[3] == -1L;
    }

    // ------------------------------------------------------------------ NBT (the chunk capability's format)

    /** Writes the field into {@code tag} (nothing for an unmarked chunk). */
    public void save(CompoundTag tag) {
        if (cells == null)
            return;
        tag.putByteArray(TAG_CELLS, Arrays.copyOf(cells, BYTES));
        tag.putLong(TAG_DECAY, lastDecayTick);
    }

    /** A field read from a chunk's NBT; empty when the tag carries none. */
    public static GroundMemory load(CompoundTag tag, long now) {
        GroundMemory m = new GroundMemory(now);
        if (tag == null || !tag.contains(TAG_CELLS))
            return m;
        byte[] data = tag.getByteArray(TAG_CELLS);
        if (data.length == BYTES) {
            m.cells = Arrays.copyOf(data, BYTES);
            m.lastDecayTick = tag.contains(TAG_DECAY) ? DecayParams.boundary(tag.getLong(TAG_DECAY)) : DecayParams.boundary(now);
        }
        return m;
    }

    public boolean sameCells(GroundMemory other) {
        if (other == null)
            return false;
        if (cells == null || other.cells == null)
            return (cells == null) == (other.cells == null);
        return Arrays.equals(cells, other.cells);
    }
}
