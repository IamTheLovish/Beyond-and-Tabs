package com.solegendary.reignofnether.cursor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CursorMathTest {

    @Test
    void windowToGuiIsFractional() {
        // 1920 px window at GUI scale 3 -> 640 GUI units; 10 px = 3.333 units, not a 3 px step
        assertEquals(10f / 3f, CursorMath.windowToGui(10, 1920, 640), 1e-4f);
        assertEquals(640f, CursorMath.windowToGui(1920, 1920, 640), 1e-4f);
        assertEquals(0f, CursorMath.windowToGui(5, 0, 640));
    }

    @Test
    void scaleDeltaAppliesSpeed() {
        assertEquals(110.0, CursorMath.scaleDelta(100, 110, 1.0), 1e-9);
        assertEquals(120.0, CursorMath.scaleDelta(100, 110, 2.0), 1e-9);
        assertEquals(102.5, CursorMath.scaleDelta(100, 110, 0.25), 1e-9);
        assertEquals(95.0, CursorMath.scaleDelta(100, 90, 0.5), 1e-9);
        assertEquals(100.0, CursorMath.scaleDelta(100, 100, 3.0), 1e-9);
    }

    @Test
    void clampKeepsInsideWindow() {
        assertEquals(0.0, CursorMath.clamp(-4, 1080));
        assertEquals(1080.0, CursorMath.clamp(1200, 1080));
        assertEquals(500.5, CursorMath.clamp(500.5, 1080));
    }

    @Test
    void clampDrawKeepsSpriteVisible() {
        assertEquals(635f, CursorMath.clampDraw(700f, 640, 5));
        assertEquals(0f, CursorMath.clampDraw(-2f, 640, 5));
        assertEquals(100.25f, CursorMath.clampDraw(100.25f, 640, 5));
    }
}
