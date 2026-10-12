package com.solegendary.reignofnether.onboarding;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * The onboarding look: the title screen's gothic palette (hud/SplashArt.restyleButtons) - dark stone panels with a lit
 * upper face, a gold border, parchment text - so the tutorial panel, tips, controls sheet and faction card read as one
 * family with the cathedral title screen. 1x font and fill() only, like the rest of the HUD.
 */
public final class OnboardingUi {

    public static final int STONE = 0x1A1512, STONE_HOT = 0x2A211B, STONE_LIT = 0x4A3D33;
    public static final int GOLD = 0xFFB08A3E, GOLD_HOT = 0xFFF0CC72, GOLD_DIM = 0xFF5C4A2C;
    public static final int TEXT = 0xFFE6CF9C, TEXT_HOT = 0xFFFFF2D6, TEXT_DIM = 0xFFA8977A, TEXT_OFF = 0xFF7A6C58;
    public static final int GOOD = 0xFF9CD67A, WARN = 0xFFE0A84A;

    private OnboardingUi() { }

    /** A stone panel with a gold frame. alpha 0..1 scales the whole thing (tips fade out). */
    public static void panel(GuiGraphics g, int x0, int y0, int x1, int y1, float alpha) {
        int a = Mth.clamp((int) (alpha * 240), 0, 255);
        g.fill(x0, y0, x1, y1, (a << 24) | STONE);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y0 + Math.min(12, (y1 - y0) / 3), (Mth.clamp((int) (alpha * 70), 0, 255) << 24) | STONE_LIT);
        g.renderOutline(x0, y0, x1 - x0, y1 - y0, withAlpha(GOLD, alpha));
    }

    /** A small text button in the same style; hot = hovered. */
    public static void button(GuiGraphics g, Font font, int x, int y, int w, int h, String label, boolean hot, boolean active) {
        g.fill(x, y, x + w, y + h, 0xF0000000 | (hot && active ? STONE_HOT : STONE));
        g.fill(x + 1, y + 1, x + w - 1, y + h / 2, 0x4C000000 | STONE_LIT);
        g.renderOutline(x, y, w, h, !active ? GOLD_DIM : hot ? GOLD_HOT : GOLD);
        int c = !active ? TEXT_OFF : hot ? TEXT_HOT : TEXT;
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2 + 1, c, false);
    }

    /** A thin gold rule with a lozenge in the middle (the title screen's filigree, reduced to a HUD line). */
    public static void rule(GuiGraphics g, int x0, int x1, int y) {
        int mid = (x0 + x1) / 2;
        g.fill(x0, y, mid - 3, y + 1, GOLD_DIM);
        g.fill(mid + 4, y, x1, y + 1, GOLD_DIM);
        g.fill(mid - 1, y - 1, mid + 2, y + 2, GOLD);
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public static int withAlpha(int argb, float alpha) {
        int a = Mth.clamp((int) (((argb >>> 24) & 0xFF) * alpha), 0, 255);
        return (a << 24) | (argb & 0xFFFFFF);
    }
}
