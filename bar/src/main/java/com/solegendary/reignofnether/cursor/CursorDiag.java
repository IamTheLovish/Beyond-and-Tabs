package com.solegendary.reignofnether.cursor;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// cursor diagnostics core: a ring buffer of the last events plus an optional line sink (logs/bat-cursor.log).
// Pure Java (no Minecraft types) so it is unit-tested; CursorDiagClient feeds it from the game.
// Events only - callers must not log per frame.
public final class CursorDiag {
    public static final int RING_SIZE = 64;
    public static final double JUMP_FRACTION = 0.25; // a cursor move over this share of the window in one frame

    public record Entry(long timeMs, long frame, String kind, String detail) {}

    private final int capacity;
    private final ArrayDeque<Entry> ring;
    private Consumer<String> sink;

    public CursorDiag(int capacity, Consumer<String> sink) {
        this.capacity = Math.max(1, capacity);
        this.ring = new ArrayDeque<>(this.capacity);
        this.sink = sink;
    }

    public synchronized void setSink(Consumer<String> sink) {
        this.sink = sink;
    }

    public synchronized void log(long timeMs, long frame, String kind, String detail) {
        if (ring.size() >= capacity)
            ring.pollFirst();
        Entry e = new Entry(timeMs, frame, kind, detail);
        ring.addLast(e);
        if (sink != null)
            sink.accept(formatLine(e));
    }

    // newest last, at most n
    public synchronized List<Entry> last(int n) {
        List<Entry> all = new ArrayList<>(ring);
        return all.subList(Math.max(0, all.size() - Math.max(0, n)), all.size());
    }

    public synchronized int size() {
        return ring.size();
    }

    // "2026-10-23T12:00:00.123Z f=1234 mode-change GLFW_CURSOR NORMAL->DISABLED"
    public static String formatLine(Entry e) {
        return Instant.ofEpochMilli(e.timeMs()) + " f=" + e.frame() + " " + e.kind() + (e.detail().isEmpty() ? "" : " " + e.detail());
    }

    // true when the cursor moved more than JUMP_FRACTION of the window on either axis in one frame
    public static boolean isJump(double dx, double dy, double winW, double winH) {
        if (winW <= 0 || winH <= 0) return false;
        return Math.abs(dx) > winW * JUMP_FRACTION || Math.abs(dy) > winH * JUMP_FRACTION;
    }

    // "Class.method" of the first stack frame outside CursorDiag, java.lang.Thread and the skip prefix
    public static String callerOf(StackTraceElement[] stack) {
        for (StackTraceElement f : stack) {
            String c = f.getClassName();
            if (c.equals(CursorDiag.class.getName()) || c.equals(CursorDiagClient.class.getName()) || c.startsWith("java.lang.Thread"))
                continue;
            int dot = c.lastIndexOf('.');
            return c.substring(dot + 1) + "." + f.getMethodName();
        }
        return "unknown";
    }
}
