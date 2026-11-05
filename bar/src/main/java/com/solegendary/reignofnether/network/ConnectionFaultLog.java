package com.solegendary.reignofnether.network;

/**
 * What ConnectionFaultLogMixin writes for the n-th fault (1-based) of one connection. A dying channel can fire
 * exceptionCaught tens of thousands of times (Oct 23: ~78,000 in 30 s after one tunnel dropped), so only the first
 * fault - the one that names the code that threw - keeps its stack trace; the repeats are counted.
 */
public final class ConnectionFaultLog {

    public static final int COUNT_EVERY = 1000;

    public enum Entry { FULL, REPEATS_FOLLOW, COUNT, NONE }

    private ConnectionFaultLog() { }

    public static Entry entryFor(int fault) {
        if (fault <= 1)
            return Entry.FULL;
        if (fault == 2)
            return Entry.REPEATS_FOLLOW;
        return fault % COUNT_EVERY == 0 ? Entry.COUNT : Entry.NONE;
    }
}
