package com.solegendary.reignofnether.guiscreen;

// pure GUI scale rule for the RTS view (no Minecraft types, so it can be unit tested)
public class TopdownGuiScale {

    public static final int MAX_GUI_SCALE_MOD = 3;

    // Auto (setting 0) is forced to 3, but never above what vanilla Auto fits in this window, so a small window
    // does not push the build card off screen. vanillaAutoScale = Window.calculateScale(0, forceUnicode).
    public static int autoScale(int vanillaAutoScale) {
        return Math.max(1, Math.min(MAX_GUI_SCALE_MOD, vanillaAutoScale));
    }
}
