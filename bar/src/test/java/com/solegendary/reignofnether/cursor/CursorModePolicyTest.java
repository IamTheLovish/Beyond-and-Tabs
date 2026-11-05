package com.solegendary.reignofnether.cursor;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.*;

// C3 AC-2: mode selection (system cursor on -> NORMAL and no re-grab; off -> DISABLED as before)
class CursorModePolicyTest {

    @Test
    void ac2SystemCursorOnUsesNormalAndNoRaw() {
        assertTrue(CursorModePolicy.useSystemCursor(true, true));
        assertEquals(GLFW.GLFW_CURSOR_NORMAL, CursorModePolicy.glfwCursorMode(true));
        assertFalse(CursorModePolicy.wantRawMotion(true));
    }

    @Test
    void ac2SystemCursorOffKeepsLockedVirtualCursor() {
        assertFalse(CursorModePolicy.useSystemCursor(false, true));
        assertEquals(GLFW.GLFW_CURSOR_DISABLED, CursorModePolicy.glfwCursorMode(false));
        assertTrue(CursorModePolicy.wantRawMotion(false));
    }

    @Test
    void ac2OutsideRtsViewNeverUsesSystemCursorMode() {
        assertFalse(CursorModePolicy.useSystemCursor(true, false));
    }

    @Test
    void ac2NoRegrabWhileSystemCursorActive() {
        assertFalse(CursorModePolicy.shouldRegrab(true, true, false, true));
        // setting off: today's behaviour (world view, not grabbed, window active -> grab)
        assertTrue(CursorModePolicy.shouldRegrab(false, true, false, true));
        assertFalse(CursorModePolicy.shouldRegrab(false, false, false, true)); // a screen is open
        assertFalse(CursorModePolicy.shouldRegrab(false, true, true, true));   // already grabbed
        assertFalse(CursorModePolicy.shouldRegrab(false, true, false, false)); // window not focused
    }
}
