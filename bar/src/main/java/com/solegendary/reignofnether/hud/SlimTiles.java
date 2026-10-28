package com.solegendary.reignofnether.hud;

import com.solegendary.reignofnether.hud.buttons.Button;

/**
 * The slim 18 px tile (HudLayout.SLIM_TILE) for the HUD's secondary rows - control groups, the global production queue
 * and the helper column on the right edge - so they take 18 x 18 instead of 22 x 22 each (Oct 22, lovish: "a lot of
 * things are taking up space in the interface, which makes it more difficult to see what's going on").
 *
 * A {@link Button}'s frame is drawn {@code (DEFAULT_ICON_SIZE - iconSize) / 2} px inside the x / y it is rendered at
 * (the xyDiff the button class already has for its odd sizes), so {@link #at} gives the x / y to render at so the
 * FRAME's corner lands on the coordinate the layout (HudLayout, tools/hud_layout.py) is speaking about. Everything else
 * - frame art, hotkey badge, hover light, grey-out, click test - is the button's own code at its own size.
 */
public final class SlimTiles {
    private SlimTiles() { }

    /** Shrinks a button made at the default size to the slim tile; safe to call every frame. Returns the button. */
    public static Button apply(Button b) {
        b.iconSize = HudLayout.SLIM_ICON;
        b.iconFrameSize = HudLayout.SLIM_ICON + 8;
        b.iconSelectedFrameSize = HudLayout.SLIM_ICON + 10;
        b.imageSize = HudLayout.SLIM_IMAGE;
        return b;
    }

    /** The x (or y) to pass to {@code Button.render} so the slim frame's edge sits on {@code frameEdge}. */
    public static int at(int frameEdge) {
        return frameEdge - (Button.DEFAULT_ICON_SIZE - HudLayout.SLIM_ICON) / 2;
    }
}
