package com.solegendary.reignofnether.cursor;

// pure choice of how the RTS view holds the mouse (CursorClientEvents, OrthoviewClientEvents): the OS cursor
// (GLFW_CURSOR_NORMAL + a hardware cursor image) when rts_system_cursor is on, else the locked virtual cursor
public final class CursorModePolicy {
    private CursorModePolicy() {}

    // GLFW_CURSOR_NORMAL / GLFW_CURSOR_DISABLED (literals keep this free of LWJGL, so tests run headless)
    public static final int GLFW_CURSOR_NORMAL = 0x00034001;
    public static final int GLFW_CURSOR_DISABLED = 0x00034003;

    // the OS cursor drives the RTS view only when the setting is on
    public static boolean useSystemCursor(boolean settingOn, boolean inRtsView) {
        return settingOn && inRtsView;
    }

    // the GLFW input mode the RTS view wants
    public static int glfwCursorMode(boolean systemCursor) {
        return systemCursor ? GLFW_CURSOR_NORMAL : GLFW_CURSOR_DISABLED;
    }

    // raw motion bypasses the user's pointer settings: only the locked virtual cursor wants it
    public static boolean wantRawMotion(boolean systemCursor) {
        return !systemCursor;
    }

    // OrthoviewClientEvents' "world view lost the mouse, take it back" check; never while the system cursor is in use
    public static boolean shouldRegrab(boolean systemCursorActive, boolean noScreen, boolean grabbed, boolean windowActive) {
        return !systemCursorActive && noScreen && !grabbed && windowActive;
    }
}
