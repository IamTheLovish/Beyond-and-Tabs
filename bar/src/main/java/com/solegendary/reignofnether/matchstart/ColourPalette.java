package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.player.PlayerPalette;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.IntPredicate;

/**
 * The colour palette popup of both lobbies (BAR's lobby: click your colour square, pick from the palette). It shows
 * PlayerPalette's colours (the ones the match draws through PlayerColors) in display order; a colour another side
 * already holds is crossed out and cannot be picked. Indexes are palette positions: PlayerPalette.ENTRIES index ==
 * PlayerColors.colors index. The screen that owns it decides what a pick means (a skirmish row's colour, or a
 * SET_COLOUR to the lobby server).
 */
final class ColourPalette {

    static final int SWATCH = 15, GAP = 2, PER_ROW = 8;
    static final int EDGE = 0xFF3A3F46, DIM = 0x9A9A9A;

    int x, y;
    private boolean open;
    private int lastHover = -1;   // swatch under the mouse last frame, for the hover tick

    static int rows() {
        return (PlayerPalette.COUNT + PER_ROW - 1) / PER_ROW;
    }

    static int width() {
        return PER_ROW * (SWATCH + GAP) + 6;
    }

    static int height() {
        return rows() * (SWATCH + GAP) + 30;
    }

    boolean isOpen() {
        return open;
    }

    /**
     * Opens (or, already open, moves) beside a row's colour box at (ax, ay) of size {@code size}, kept on a
     * {@code sw} x {@code sh} screen.
     */
    void openBeside(int ax, int ay, int size, int sw, int sh) {
        x = Math.max(4, Math.min(ax + size + 6, sw - width() - 4));
        y = Math.max(4, Math.min(ay - 10, sh - height() - 4));
        if (!open)
            lastHover = -1;
        open = true;
    }

    void close() {
        open = false;
    }

    boolean contains(double mx, double my) {
        return open && mx >= x && mx < x + width() && my >= y && my < y + height();
    }

    /** The palette index under (mx, my), or -1. */
    int hit(double mx, double my) {
        if (!open)
            return -1;
        for (int i = 0; i < PlayerPalette.COUNT; i++) {
            int sx = swatchX(i), sy = swatchY(i);
            if (mx >= sx && mx < sx + SWATCH && my >= sy && my < sy + SWATCH)
                return i;
        }
        return -1;
    }

    int swatchX(int i) {
        return x + 3 + (i % PER_ROW) * (SWATCH + GAP);
    }

    int swatchY(int i) {
        return y + 16 + (i / PER_ROW) * (SWATCH + GAP);
    }

    /** The name of palette colour {@code i} (lang key colour.reignofnether.&lt;name&gt;). */
    static Component name(int i) {
        return Component.translatable("colour.reignofnether." + PlayerPalette.ENTRIES.get(i).name());
    }

    /** The palette index of a map colour id, or -1 (0 = automatic, or not a palette colour). */
    static int indexOf(int mapColorId) {
        for (int i = 0; i < PlayerPalette.COUNT; i++)
            if (PlayerPalette.ENTRIES.get(i).mapColorId() == mapColorId)
                return i;
        return -1;
    }

    /**
     * Draws the popup: {@code current} (a palette index, -1 for automatic) framed white, the colours {@code taken}
     * crossed out, and the name of the colour under the mouse (or the right-click hint) in the footer.
     */
    void render(GuiGraphics gg, Font font, int mx, int my, int current, IntPredicate taken) {
        if (!open)
            return;
        int pw = width(), ph = height();
        gg.pose().pushPose();
        gg.pose().translate(0, 0, 400);   // over the buttons drawn beneath
        gg.fill(x - 1, y - 1, x + pw + 1, y + ph + 1, EDGE);
        gg.fill(x, y, x + pw, y + ph, 0xF0101216);
        gg.drawString(font, Component.translatable("lobby.reignofnether.colour.pick"), x + 4, y + 4, 0xE0E0E0, false);
        int hovered = -1;
        for (int i = 0; i < PlayerPalette.COUNT; i++) {
            int sx = swatchX(i), sy = swatchY(i);
            boolean hover = mx >= sx && mx < sx + SWATCH && my >= sy && my < sy + SWATCH;
            if (hover)
                hovered = i;
            int frame = i == current ? 0xFFFFFFFF : hover ? 0xFFAAAAAA : 0xFF000000;
            gg.fill(sx - 1, sy - 1, sx + SWATCH + 1, sy + SWATCH + 1, frame);
            gg.fill(sx, sy, sx + SWATCH, sy + SWATCH, 0xFF000000 | PlayerPalette.ENTRIES.get(i).hex());
            if (i != current && taken.test(i)) {
                // held by another side: dimmed and crossed out (picking it buzzes)
                gg.fill(sx, sy, sx + SWATCH, sy + SWATCH, 0x80000000);
                for (int d = 2; d < SWATCH - 2; d++) {
                    gg.fill(sx + d, sy + d, sx + d + 1, sy + d + 1, 0xE0FFFFFF);
                    gg.fill(sx + SWATCH - 1 - d, sy + d, sx + SWATCH - d, sy + d + 1, 0xE0FFFFFF);
                }
            }
        }
        if (hovered != lastHover && hovered >= 0)
            LobbySounds.hover();
        lastHover = hovered;
        Component foot = hovered < 0 ? Component.translatable("lobby.reignofnether.colour.auto_hint")
            : hovered != current && taken.test(hovered)
                ? Component.translatable("lobby.reignofnether.colour.taken", name(hovered))
                : name(hovered);
        gg.drawString(font, foot, x + 4, y + ph - 11, DIM, false);
        gg.pose().popPose();
    }
}
