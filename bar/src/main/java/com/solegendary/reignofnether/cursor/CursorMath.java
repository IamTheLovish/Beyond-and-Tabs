package com.solegendary.reignofnether.cursor;

// pure maths for the RTS cursor (CursorClientEvents): sub-pixel GUI position, speed scaling and the draw clamp
public final class CursorMath {
    private CursorMath() {}

    // window (screen) pixels -> fractional GUI-scaled position
    public static float windowToGui(double windowPos, int windowSize, int guiSize) {
        if (windowSize <= 0) return 0;
        return (float) (windowPos * guiSize / windowSize);
    }

    // new virtual position after scaling this frame's raw delta by the speed setting
    public static double scaleDelta(double last, double current, double speed) {
        return last + (current - last) * speed;
    }

    // keep a position in [0, max] (window pixels or GUI units)
    public static double clamp(double v, double max) {
        return Math.max(0, Math.min(v, max));
    }

    // the cursor sprite is held this far inside the right/bottom edge so it stays visible
    public static float clampDraw(float guiPos, int guiSize, int margin) {
        return Math.max(0f, Math.min(guiPos, guiSize - margin));
    }
}
