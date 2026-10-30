package com.solegendary.reignofnether.cursor;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// C3 AC-1: ring buffer keeps the last N, jump detection threshold, log line format
class CursorDiagTest {

    @Test
    void ac1RingBufferKeepsLastN() {
        CursorDiag d = new CursorDiag(3, null);
        for (int i = 1; i <= 5; i++)
            d.log(1000L * i, i, "ev", "n" + i);
        assertEquals(3, d.size());
        List<CursorDiag.Entry> last = d.last(10);
        assertEquals("n3", last.get(0).detail());
        assertEquals("n5", last.get(2).detail());
        assertEquals(2, d.last(2).size());
        assertEquals("n4", d.last(2).get(0).detail());
        assertEquals(0, d.last(0).size());
    }

    @Test
    void ac1JumpThresholdIsQuarterOfWindow() {
        assertFalse(CursorDiag.isJump(480, 0, 1920, 1080));   // exactly 25%: not over
        assertTrue(CursorDiag.isJump(481, 0, 1920, 1080));
        assertTrue(CursorDiag.isJump(0, -271, 1920, 1080));   // either axis, either direction
        assertFalse(CursorDiag.isJump(0, 270, 1920, 1080));
        assertFalse(CursorDiag.isJump(100, 100, 1920, 1080));
        assertFalse(CursorDiag.isJump(5000, 5000, 0, 0));     // no window yet
    }

    @Test
    void ac1LogLineFormat() {
        CursorDiag.Entry e = new CursorDiag.Entry(0L, 42, "mode-change", "GLFW_CURSOR NORMAL->DISABLED");
        assertEquals("1970-01-01T00:00:00Z f=42 mode-change GLFW_CURSOR NORMAL->DISABLED", CursorDiag.formatLine(e));
        assertEquals("1970-01-01T00:00:01Z f=7 focus-lost",
                CursorDiag.formatLine(new CursorDiag.Entry(1000L, 7, "focus-lost", "")));
    }

    @Test
    void ac1SinkReceivesOneLinePerEvent() {
        List<String> lines = new ArrayList<>();
        CursorDiag d = new CursorDiag(8, lines::add);
        d.log(0L, 1, "grab", "by OrthoviewClientEvents.onClientTick");
        d.log(0L, 2, "release", "by OrthoviewClientEvents.onClientTick");
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).endsWith("grab by OrthoviewClientEvents.onClientTick"));
    }

    @Test
    void ac1CallerIsFirstFrameOutsideDiag() {
        StackTraceElement[] st = {
                new StackTraceElement(CursorDiag.class.getName(), "callerOf", "x", 1),
                new StackTraceElement("com.solegendary.reignofnether.cursor.CursorDiagClient", "grabCall", "x", 1),
                new StackTraceElement("com.solegendary.reignofnether.orthoview.OrthoviewClientEvents", "onClientTick", "x", 1),
        };
        assertEquals("OrthoviewClientEvents.onClientTick", CursorDiag.callerOf(st));
        assertEquals("unknown", CursorDiag.callerOf(new StackTraceElement[0]));
    }
}
