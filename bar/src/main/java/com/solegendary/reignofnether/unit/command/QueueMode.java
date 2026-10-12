package com.solegendary.reignofnether.unit.command;

/**
 * How a new order meets a unit's command queue (BAR / Spring's command options). Shared by the client, which picks
 * it from the modifier keys, and the server, which applies it in {@link CommandQueues#issue}.
 * <ul>
 *   <li>{@link #REPLACE}: a plain click - the queue is emptied and the order starts at once;</li>
 *   <li>{@link #APPEND}: Shift - the order waits behind everything queued (starting at once if the unit has nothing
 *       to do);</li>
 *   <li>{@link #INSERT_FRONT}: Ctrl - the order starts at once and the one it interrupts goes back to the head of
 *       the queue, to resume afterwards (Spring's CMD_INSERT at position 0; BAR binds it to Space, which this mod
 *       uses for the shop, so the brief put it on Ctrl - a Ctrl+right-click is free, Ctrl+right-drag stays the area
 *       command).</li>
 * </ul>
 * Travels by ordinal in UnitActionServerboundPacket: REPLACE and APPEND keep the byte values of the old shift
 * boolean, so new values go at the END.
 */
public enum QueueMode {
    REPLACE,
    APPEND,
    INSERT_FRONT;

    private static final QueueMode[] VALUES = values();

    /** Ctrl wins over Shift: Ctrl+Shift inserts at the front too. */
    public static QueueMode of(boolean shift, boolean ctrl) {
        return ctrl ? INSERT_FRONT : shift ? APPEND : REPLACE;
    }

    /** Decodes a mode from the wire; an unknown value (a modified client) is a plain order. */
    public static QueueMode byOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= VALUES.length ? REPLACE : VALUES[ordinal];
    }
}
