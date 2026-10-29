package com.solegendary.reignofnether.unit.command;

import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;

/**
 * One unit's command queue (Spring / BAR: CCommandAI's commandQue): the order it is carrying out ({@link #current}),
 * if the queue started it, and the orders waiting behind it, oldest first. Server side, touched on the server thread
 * only, and only through {@link CommandQueues} (the mutators are package-private).
 * <p>
 * The waiting orders live in a small ring buffer instead of an ArrayDeque so they can be read by index - the queue
 * lines and the tests walk them without an iterator - and so starting or adding an order allocates nothing once the
 * buffer has grown to the unit's usual depth.
 */
public final class CommandQueue {

    final LivingEntity unit;
    /** The order this queue last started; it may have finished since (the queue only looks again when it needs to). */
    @Nullable UnitCommand current;
    /** Game tick {@link #current} was started on: the next order never starts in the same tick (see CommandQueues). */
    long currentTick = Long.MIN_VALUE;
    /** In CommandQueues' executor list (from the first waiting order until the executor sees the queue drained). */
    boolean listed;
    /** The unit left the level: the executor drops the queue on its next pass. */
    boolean dead;

    private UnitCommand[] ring = new UnitCommand[4];   // capacity stays a power of two
    private int head, size;

    CommandQueue(LivingEntity unit) {
        this.unit = unit;
    }

    public LivingEntity unit() {
        return unit;
    }

    @Nullable
    public UnitCommand current() {
        return current;
    }

    /** The game tick {@link #current} was started on (a newer command has a later one): hero/ai/OrderLock reads it. */
    public long currentStartedAt() {
        return currentTick;
    }

    /** Waiting orders (the current one not counted). */
    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /** The i-th waiting order, 0 = the next to start. */
    public UnitCommand get(int i) {
        if (i < 0 || i >= size)
            throw new IndexOutOfBoundsException("queued order " + i + " of " + size);
        return ring[(head + i) & (ring.length - 1)];
    }

    void addLast(UnitCommand c) {
        if (size == ring.length)
            grow();
        ring[(head + size) & (ring.length - 1)] = c;
        size++;
    }

    void addFirst(UnitCommand c) {
        if (size == ring.length)
            grow();
        head = (head - 1) & (ring.length - 1);
        ring[head] = c;
        size++;
    }

    @Nullable
    UnitCommand pollFirst() {
        if (size == 0)
            return null;
        UnitCommand c = ring[head];
        ring[head] = null;
        head = (head + 1) & (ring.length - 1);
        size--;
        return c;
    }

    /** Removes the newest waiting order (BAR's command_cancel_last); null if none is waiting. */
    @Nullable
    UnitCommand pollLast() {
        if (size == 0)
            return null;
        int i = (head + size - 1) & (ring.length - 1);
        UnitCommand c = ring[i];
        ring[i] = null;
        size--;
        return c;
    }

    /** Forgets the waiting orders (the current one stays: the unit is still doing it). */
    void clearWaiting() {
        for (int i = 0; i < size; i++)
            ring[(head + i) & (ring.length - 1)] = null;
        head = 0;
        size = 0;
    }

    void start(UnitCommand c, long tick) {
        current = c;
        currentTick = tick;
    }

    private void grow() {
        UnitCommand[] bigger = new UnitCommand[ring.length * 2];
        for (int i = 0; i < size; i++)
            bigger[i] = ring[(head + i) & (ring.length - 1)];
        ring = bigger;
        head = 0;
    }
}
