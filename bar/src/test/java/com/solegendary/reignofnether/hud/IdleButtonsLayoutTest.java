package com.solegendary.reignofnether.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** AC-3: the three idle buttons overlap neither each other nor the minimap at GUI scale 2, 3 and 4. */
class IdleButtonsLayoutTest {

    private record Rect(String name, int x, int y, int w, int h) {
        boolean overlaps(Rect o) {
            return x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h;
        }
    }

    private static final int[][] SCREENS = { {1920, 1080}, {2560, 1440} };
    private static final int[] SCALES = { 2, 3, 4 };
    private static final int[] RADII = { 60, 120 };   // MinimapClientEvents: small / large map

    private static final int F = HudLayout.ICON_FRAME;

    @Test
    void ac3_diamond_map_buttons_clear_each_other_and_the_diamond() {
        for (int[] scr : SCREENS) for (int scale : SCALES) for (int r : RADII) {
            int w = scr[0] / scale, h = scr[1] / scale;
            String ctx = w + "x" + h + " r" + r;
            Rect[] buttons = {
                new Rect("worker", HudLayout.idleWorkerX(w, r), HudLayout.idleWorkerY(h, r), F, F),
                new Rect("commander", HudLayout.idleRowX(w, r, false, 0), HudLayout.idleRowY(h), F, F),
                new Rect("t2", HudLayout.idleRowX(w, r, false, 1), HudLayout.idleRowY(h), F, F),
            };
            int cx = w - HudLayout.MAP_CORNER - r, cy = h - HudLayout.MAP_CORNER - r;
            for (Rect b : buttons) {
                assertTrue(b.x >= 0 && b.y >= 0 && b.x + b.w <= w && b.y + b.h <= h, b.name + " off screen at " + ctx);
                // nearest point of the rect to the diamond centre must be outside |dx|+|dy| < r
                int dx = Math.max(Math.max(b.x - cx, cx - (b.x + b.w)), 0);
                int dy = Math.max(Math.max(b.y - cy, cy - (b.y + b.h)), 0);
                assertTrue(dx + dy >= r, b.name + " is on the diamond map at " + ctx);
            }
            assertNoPairOverlap(buttons, ctx);
        }
    }

    @Test
    void ac3_square_map_buttons_clear_each_other_and_the_square() {
        for (int[] scr : SCREENS) for (int scale : SCALES) for (int r : RADII) {
            int w = scr[0] / scale, h = scr[1] / scale;
            String ctx = "square " + w + "x" + h + " r" + r;
            int side = HudLayout.squareSide(r);
            Rect map = new Rect("minimap", w - HudLayout.MAP_CORNER - side, h - HudLayout.MAP_CORNER - side, side, side);
            Rect[] buttons = {
                new Rect("worker", HudLayout.squareIdleWorkerX(w, r), HudLayout.squareIdleWorkerY(h), F, F),
                new Rect("commander", HudLayout.idleRowX(w, r, true, 0), HudLayout.idleRowY(h), F, F),
                new Rect("t2", HudLayout.idleRowX(w, r, true, 1), HudLayout.idleRowY(h), F, F),
            };
            for (Rect b : buttons)
                assertFalse(b.overlaps(map), b.name + " overlaps the minimap at " + ctx);
            assertNoPairOverlap(buttons, ctx);
        }
    }

    private static void assertNoPairOverlap(Rect[] rects, String ctx) {
        for (int i = 0; i < rects.length; i++)
            for (int j = i + 1; j < rects.length; j++)
                assertFalse(rects[i].overlaps(rects[j]), rects[i].name + " overlaps " + rects[j].name + " at " + ctx);
    }
}
