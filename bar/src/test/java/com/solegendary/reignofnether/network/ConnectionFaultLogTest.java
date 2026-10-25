package com.solegendary.reignofnether.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Oct 23 server log: one dropped player (a tunnel's "connection lost") made Netty call exceptionCaught ~78,000 times in
 * 30 s on the same dead channel, and ConnectionFaultLogMixin wrote a full ERROR stack trace for each - a 28 MB log in
 * which nothing else could be read. The first fault of a connection is the diagnosis and keeps its stack trace; the
 * repeats on that connection are counted, with one line saying so and a count line every 1,000.
 */
class ConnectionFaultLogTest {

    @Test
    void firstFaultOfAConnectionKeepsItsStackTrace() {
        assertEquals(ConnectionFaultLog.Entry.FULL, ConnectionFaultLog.entryFor(1));
    }

    @Test
    void secondFaultSaysRepeatsAreCountedFromNowOn() {
        assertEquals(ConnectionFaultLog.Entry.REPEATS_FOLLOW, ConnectionFaultLog.entryFor(2));
    }

    @Test
    void laterFaultsLogOnlyACountLineEveryThousand() {
        assertEquals(ConnectionFaultLog.Entry.NONE, ConnectionFaultLog.entryFor(3));
        assertEquals(ConnectionFaultLog.Entry.NONE, ConnectionFaultLog.entryFor(999));
        assertEquals(ConnectionFaultLog.Entry.COUNT, ConnectionFaultLog.entryFor(1000));
        assertEquals(ConnectionFaultLog.Entry.NONE, ConnectionFaultLog.entryFor(1001));
        assertEquals(ConnectionFaultLog.Entry.COUNT, ConnectionFaultLog.entryFor(2000));
    }

    @Test
    void theOct23BurstWritesAHandfulOfLinesNotSeventyEightThousandTraces() {
        int lines = 0, traces = 0;
        for (int fault = 1; fault <= 78_376; fault++) {
            ConnectionFaultLog.Entry e = ConnectionFaultLog.entryFor(fault);
            if (e != ConnectionFaultLog.Entry.NONE)
                lines++;
            if (e == ConnectionFaultLog.Entry.FULL)
                traces++;
        }
        assertEquals(1, traces);
        assertEquals(2 + 78, lines);   // FULL + REPEATS_FOLLOW + one COUNT per thousand
    }
}
