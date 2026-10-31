package com.solegendary.reignofnether.perf;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Arrays;

/**
 * The cells under entity round shadows that cannot take one, remembered for the frame being drawn (PR #460). In the RTS
 * view EntityShadowMixin draws every entity's shadow at full strength; vanilla's renderShadow walks a 2x2x2 box of cells
 * per entity and, for each, reads the block below, its render shape and collision shape and the light at the cell
 * (Embeddium's replacement of renderBlockShadow does the same reads). About half the box is the layer under the ground,
 * where the light is 0 and nothing is drawn, and the units of a squad stand on the same cells. A cell that cannot take a
 * shadow ("bare": the block below is invisible or not a full block, its shape is empty, or the light at the cell is 3
 * or less) is the same for every entity over it until the world changes - and the world does not change inside a
 * frame (block packets and light updates run before the level is drawn), so a cell found bare is skipped for the rest
 * of the frame: the same quads, fewer reads. Cells that can take a shadow still go through renderBlockShadow (vanilla's
 * or Embeddium's), whose alpha depends on each entity's height.
 *
 * An open-addressing table of block positions (BlockPos.asLong), stamped with the frame: a new frame empties it in O(1).
 * Render thread only.
 */
public final class ShadowCells {

    public static final int UNKNOWN = 0, BARE = 1, OPEN = 2;
    static final int START_CAPACITY = 4096;   // a power of two; the table doubles past half full

    /** The client's table, emptied at the start of every frame. */
    public static final ShadowCells CLIENT = new ShadowCells();

    private long[] keys = new long[START_CAPACITY];
    private int[] stamps = new int[START_CAPACITY];
    private boolean[] bare = new boolean[START_CAPACITY];
    private int frame = 1, used = 0;

    /** BARE, OPEN, or UNKNOWN when this frame has not looked at the cell yet. */
    public int get(long key) {
        int mask = keys.length - 1;
        for (int i = slot(key, mask); ; i = (i + 1) & mask) {
            if (stamps[i] != frame)
                return UNKNOWN;
            if (keys[i] == key)
                return bare[i] ? BARE : OPEN;
        }
    }

    public void put(long key, boolean isBare) {
        if (2 * (used + 1) > keys.length)
            grow();
        int mask = keys.length - 1;
        int i = slot(key, mask);
        while (stamps[i] == frame) {
            if (keys[i] == key) {
                bare[i] = isBare;
                return;
            }
            i = (i + 1) & mask;
        }
        stamps[i] = frame;
        keys[i] = key;
        bare[i] = isBare;
        used++;
    }

    /** Forgets every cell (a new frame: the world may have changed since). */
    public void newFrame() {
        used = 0;
        if (++frame == 0) {   // the stamp wrapped after 2^32 frames: clear for real once
            Arrays.fill(stamps, 0);
            frame = 1;
        }
    }

    int capacity() {
        return keys.length;
    }

    private void grow() {
        long[] oldKeys = keys;
        int[] oldStamps = stamps;
        boolean[] oldBare = bare;
        keys = new long[oldKeys.length * 2];
        stamps = new int[oldKeys.length * 2];
        bare = new boolean[oldKeys.length * 2];
        int mask = keys.length - 1;
        for (int j = 0; j < oldKeys.length; j++) {
            if (oldStamps[j] != frame)
                continue;
            int i = slot(oldKeys[j], mask);
            while (stamps[i] == frame)
                i = (i + 1) & mask;
            stamps[i] = frame;
            keys[i] = oldKeys[j];
            bare[i] = oldBare[j];
        }
    }

    private static int slot(long key, int mask) {
        long h = key * 0x9E3779B97F4A7C15L;
        return (int) (h ^ (h >>> 32)) & mask;
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent evt) {
        if (evt.phase == TickEvent.Phase.START)
            CLIENT.newFrame();
    }
}
