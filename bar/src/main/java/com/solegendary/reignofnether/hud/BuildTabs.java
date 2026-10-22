package com.solegendary.reignofnether.hud;

import com.solegendary.reignofnether.building.BuildGroup;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.hud.buttons.Button;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.util.MyRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * The worker's tabbed build menu: Economy | Military | Defence | Tech as a strip above the grid. The selected tab is
 * remembered while the worker card stays up and falls back to Economy once it has been gone for a frame. Click a tab
 * or hold Alt and press the first four slot keys (Q Z E R). Only the selected tab's buildings are drawn, so only its
 * slot keys are live (BuildGroup.SLOT_KEYS). Each building sits in the grid cell of its slot, so a key always points at
 * the same cell, even when the tutorial hides the slots before it.
 */
public final class BuildTabs {
    private BuildTabs() { }

    public static final int STRIP_H = 14;
    private static final int TAB_COUNT = BuildGroup.values().length;

    private static BuildGroup selected = BuildGroup.ECONOMY;
    private static boolean drawnThisFrame = false;
    private static boolean drawnLastFrame = false;
    /** Per tab key: Alt + key already switched tabs, so the key's release must not also press the slot. */
    private static final boolean[] tabKeyHeld = new boolean[TAB_COUNT];
    private static final boolean[] swallowRelease = new boolean[TAB_COUNT];

    // the strip's last drawn rectangle, for clicks
    private static int stripX, stripY, stripW;

    public static BuildGroup selected() {
        return selected;
    }

    public static void select(BuildGroup group) {
        selected = group;
    }

    /** Start of a HUD frame: the tab resets to Economy once the worker card was not drawn the frame before. */
    public static void beginFrame() {
        drawnLastFrame = drawnThisFrame;
        if (!drawnLastFrame)
            selected = BuildGroup.ECONOMY;
        drawnThisFrame = false;
    }

    /** Grid cell of every button of a tab: its slot, or after the last slot for buttons with none (custom buildings). */
    public static int[] cells(List<? extends Button> tabButtons) {
        int next = 0;
        for (Button b : tabButtons)
            if (b instanceof BuildingPlaceButton bp && bp.slot >= 0)
                next = Math.max(next, bp.slot + 1);
        int[] out = new int[tabButtons.size()];
        for (int i = 0; i < out.length; i++)
            out[i] = tabButtons.get(i) instanceof BuildingPlaceButton bp && bp.slot >= 0 ? bp.slot : next++;
        return out;
    }

    /** Rows the visible buttons of a tab need (at least one, so the strip keeps its place over an empty tab). */
    public static int rows(List<? extends Button> tabButtons, int perRow) {
        int max = -1;
        for (int c : cells(tabButtons))
            max = Math.max(max, c);
        return Math.max(1, (max + perRow) / perRow);
    }

    /** The visible buttons of the selected tab, out of a worker's whole card. */
    public static List<Button> tabButtons(List<? extends Button> all) {
        List<Button> out = new ArrayList<>();
        for (Button b : all)
            if (b instanceof BuildingPlaceButton bp && bp.group == selected && !b.isHidden.get())
                out.add(b);
        return out;
    }

    /** Draws the strip and the selected tab's grid; strip at (0, stripY), the grid right under it. */
    public static void renderTabs(GuiGraphics g, int stripY, int perRow, int frame, int mouseX, int mouseY,
                                  List<Button> tabButtons, List<Button> renderedButtons, List<Button> cardButtons) {
        drawnThisFrame = true;
        BuildTabs.stripX = 0;
        BuildTabs.stripY = stripY;
        BuildTabs.stripW = perRow * frame;
        renderStrip(g, mouseX, mouseY);
        int[] cells = cells(tabButtons);
        for (int i = 0; i < tabButtons.size(); i++) {
            Button b = tabButtons.get(i);
            b.render(g, (cells[i] % perRow) * frame, stripY + STRIP_H + (cells[i] / perRow) * frame, mouseX, mouseY);
            renderedButtons.add(b);
            if (cardButtons != null)
                cardButtons.add(b);
        }
    }

    private static void renderStrip(GuiGraphics g, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        BuildGroup[] groups = BuildGroup.values();
        int tabW = stripW / groups.length;
        BuildGroup hovered = null;
        for (int i = 0; i < groups.length; i++) {
            int x = stripX + i * tabW;
            int w = i == groups.length - 1 ? stripW - i * tabW : tabW;
            boolean sel = groups[i] == selected;
            boolean over = mouseX >= x && mouseX < x + w && mouseY >= stripY && mouseY < stripY + STRIP_H;
            if (over)
                hovered = groups[i];
            g.fill(x, stripY, x + w - 1, stripY + STRIP_H, sel ? 0xC8302810 : 0x64000000);
            if (over && !sel)
                g.fill(x, stripY, x + w - 1, stripY + STRIP_H, 0x32FFFFFF);
            if (sel) {
                g.fill(x, stripY, x + w - 1, stripY + 1, Button.ACTIVE_EDGE);
                g.fill(x, stripY, x + 1, stripY + STRIP_H, Button.ACTIVE_EDGE);
                g.fill(x + w - 2, stripY, x + w - 1, stripY + STRIP_H, Button.ACTIVE_EDGE);
            } else {
                g.fill(x, stripY + STRIP_H - 1, x + w - 1, stripY + STRIP_H, 0xFFFFC040);
            }
            String label = I18n.get(groups[i].langKey());
            float scale = 0.75f;
            int tw = Math.round(mc.font.width(label) * scale);
            g.pose().pushPose();
            g.pose().translate(x + (w - 1 - tw) / 2f, stripY + 4, 1);
            g.pose().scale(scale, scale, 1f);
            g.drawString(mc.font, label, 0, 0, sel ? 0xFFFFFF : 0xB0B0B0, false);
            g.pose().popPose();
        }
        if (hovered != null) {
            String key = BuildGroup.TAB_KEYS.get(hovered.ordinal()).getCurrentLabel();
            MyRenderer.renderTooltip(g, List.of(FormattedCharSequence.forward(
                I18n.get("hud.reignofnether.build_tab.tooltip", I18n.get(hovered.langKey()), key), Style.EMPTY)), mouseX, mouseY);
        }
    }

    /** True when the point is on the strip (the world behind it must not be clicked). */
    public static boolean isOver(int mouseX, int mouseY) {
        return drawnThisFrame && mouseX >= stripX && mouseX < stripX + stripW
            && mouseY >= stripY && mouseY < stripY + STRIP_H;
    }

    /** A left click on the strip picks a tab. Returns whether it was on the strip. */
    public static boolean click(int mouseX, int mouseY) {
        if (!isOver(mouseX, mouseY))
            return false;
        BuildGroup[] groups = BuildGroup.values();
        int tabW = stripW / groups.length;
        int i = Math.min(groups.length - 1, (mouseX - stripX) / Math.max(1, tabW));
        switchTo(groups[i]);
        return true;
    }

    private static void switchTo(BuildGroup group) {
        selected = group;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null)
            mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.2f, 1.0f);
    }

    /**
     * Once a client tick: Alt held and one of the first four slot keys going down switches the tab. Polled, not taken
     * from key events, because Q and E reach screens as releases only (HudClientEvents.onKeyRelease); the matching
     * release is swallowed so it does not press the slot as well, whichever of Alt and the key is let go first.
     */
    public static void tick() {
        if (!drawnLastFrame && !drawnThisFrame || !OrthoviewClientEvents.isEnabled() || TextInputClientEvents.isAnyInputFocused()) {
            java.util.Arrays.fill(tabKeyHeld, false);
            return;
        }
        boolean alt = Keybindings.altMod.isDown();
        for (int i = 0; i < TAB_COUNT; i++) {
            boolean down = BuildGroup.TAB_KEYS.get(i).isDown();
            if (down && alt && !tabKeyHeld[i]) {
                switchTo(BuildGroup.values()[i]);
                swallowRelease[i] = true;
            }
            if (!down && !tabKeyHeld[i])
                swallowRelease[i] = false;
            tabKeyHeld[i] = down;
        }
    }

    /** True (once) for the release of a tab key whose press switched the tab: HudClientEvents must ignore it. */
    public static boolean consumeRelease(int key) {
        for (int i = 0; i < TAB_COUNT; i++)
            if (swallowRelease[i] && BuildGroup.TAB_KEYS.get(i).matches(key)) {
                swallowRelease[i] = false;
                return true;
            }
        return false;
    }
}
