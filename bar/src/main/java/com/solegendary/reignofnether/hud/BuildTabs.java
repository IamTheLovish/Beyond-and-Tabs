package com.solegendary.reignofnether.hud;

import com.solegendary.reignofnether.building.BuildGroup;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.hud.buttons.Button;
import com.solegendary.reignofnether.keybinds.BuildCategoryKeys;
import com.solegendary.reignofnether.keybinds.Keybinding;
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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * The worker's tabbed build menu, keyed like BAR's gridmenu (BuildGroup, keybinds/BuildCategoryKeys): Economy |
 * Military | Defence | Tech as a strip above a 4-wide grid.
 * <ul>
 *   <li>Home (no tab open): the last tab's buildings are drawn click-only, without key badges; Z / X / C / V open a
 *       tab, and the grid keys keep their other meaning (S Stop, F Fight, ...).</li>
 *   <li>Open: the grid keys Q W E R / A S D F / Z X C V pick the building in their cell, the same keys in every tab.
 *       The grid fills from the bottom row up (slot 0 on Z, drawn on the last row), like BAR's gridmenu.
 *       The key of a cell is fixed (BuildGroup.SLOT_KEYS), so another builder type's building added to a mixed card
 *       gets the key of the cell it lands in.</li>
 *   <li>The tab closes (home) when the building it picked is placed or dropped (BAR's CommandNotify), when the
 *       open tab is clicked again, and when the build menu leaves the card (then it also falls back to Economy).</li>
 * </ul>
 */
public final class BuildTabs {
    private BuildTabs() { }

    public static final int STRIP_H = 14;

    private static BuildGroup selected = BuildGroup.ECONOMY;
    private static boolean open = false;
    /** A building was picked while the tab is open: once the ghost is gone again, the tab closes. */
    private static boolean ghostSeen = false;
    private static boolean drawnThisFrame = false;
    private static boolean drawnLastFrame = false;
    /** The buttons drawn in the grid this frame and their cells (the key handling reads the last drawn frame). */
    private static List<Button> drawn = new ArrayList<>();
    private static int[] drawnCells = new int[0];
    private static final Set<Button> drawnSet = Collections.newSetFromMap(new IdentityHashMap<>());

    // the strip's last drawn rectangle, for clicks
    private static int stripX, stripY, stripW;

    public static BuildGroup selected() {
        return selected;
    }

    public static boolean isOpen() {
        return open;
    }

    public static void select(BuildGroup group) {
        selected = group;
    }

    /** Opens a tab (BAR's setCurrentCategory). */
    public static void open(BuildGroup group) {
        selected = group;
        open = true;
        ghostSeen = false;
    }

    /** Back to home (BAR's clearCategory). */
    public static void close() {
        open = false;
        ghostSeen = false;
    }

    /** True while the card shows the build menu (drawn this frame or the last). */
    public static boolean isShowing() {
        return drawnLastFrame || drawnThisFrame;
    }

    /** Start of a HUD frame: once the build menu was not drawn the frame before, home and Economy again. */
    public static void beginFrame() {
        drawnLastFrame = drawnThisFrame;
        if (!drawnLastFrame) {
            selected = BuildGroup.ECONOMY;
            close();
            drawn = new ArrayList<>();
            drawnCells = new int[0];
            drawnSet.clear();
        }
        drawnThisFrame = false;
    }

    /**
     * Grid cell of every button of a tab: its slot, or after the last slot for buttons with none (custom buildings,
     * another builder type's keyless copies on a mixed card) or whose slot an earlier button already holds
     * (BuilderCardUnion.tabCells).
     */
    public static int[] cells(List<? extends Button> tabButtons) {
        int[] slots = new int[tabButtons.size()];
        for (int i = 0; i < slots.length; i++)
            slots[i] = tabButtons.get(i) instanceof BuildingPlaceButton bp ? bp.slot : -1;
        return BuilderCardUnion.tabCells(slots);
    }

    /** Rows the visible buttons of a tab need on the 4-wide grid (at least one, so the strip keeps its place). */
    public static int rows(List<? extends Button> tabButtons) {
        int max = -1;
        for (int c : cells(tabButtons))
            max = Math.max(max, c);
        return Math.max(1, (max + BuildGroup.COLS) / BuildGroup.COLS);
    }

    /** The visible buttons of the selected tab, out of a worker's whole card. */
    public static List<Button> tabButtons(List<? extends Button> all) {
        List<Button> out = new ArrayList<>();
        for (Button b : all)
            if (b instanceof BuildingPlaceButton bp && bp.group == selected && !b.isHidden.get())
                out.add(b);
        return out;
    }

    /** True for a button drawn in the build grid: its key is handled by {@link #onKey}, never by the card loop. */
    public static boolean isGridButton(Button b) {
        return drawnSet.contains(b);
    }

    /**
     * Draws the strip (width perRow * frame, the card's) and the selected tab's 4-wide grid right under it. With the
     * tab open each button shows its cell's grid key; at home no key.
     */
    public static void renderTabs(GuiGraphics g, int stripY, int perRow, int frame, int mouseX, int mouseY,
                                  List<Button> tabButtons, List<Button> renderedButtons, List<Button> cardButtons) {
        drawnThisFrame = true;
        BuildTabs.stripX = 0;
        BuildTabs.stripY = stripY;
        BuildTabs.stripW = perRow * frame;
        renderStrip(g, mouseX, mouseY);
        int[] cells = cells(tabButtons);
        int rows = rows(tabButtons);
        drawn = new ArrayList<>(tabButtons);
        drawnCells = cells;
        drawnSet.clear();
        drawnSet.addAll(tabButtons);
        for (int i = 0; i < tabButtons.size(); i++) {
            Button b = tabButtons.get(i);
            // the badge (and the "key held" frame) is the cell's grid key while the tab is open, none at home
            Keybinding own = b.hotkey;
            b.hotkey = open ? BuildGroup.slotKey(cells[i]) : null;
            try {
                b.render(g, (cells[i] % BuildGroup.COLS) * frame,
                    stripY + STRIP_H + BuildGroup.gridRow(cells[i], rows) * frame, mouseX, mouseY);
            } finally {
                b.hotkey = own;
            }
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
            g.fill(x, stripY, x + w - 1, stripY + STRIP_H, sel ? (open ? 0xC8302810 : 0x96302810) : 0x64000000);
            if (over && !sel)
                g.fill(x, stripY, x + w - 1, stripY + STRIP_H, 0x32FFFFFF);
            if (sel && open) {
                g.fill(x, stripY, x + w - 1, stripY + 1, Button.ACTIVE_EDGE);
                g.fill(x, stripY, x + 1, stripY + STRIP_H, Button.ACTIVE_EDGE);
                g.fill(x + w - 2, stripY, x + w - 1, stripY + STRIP_H, Button.ACTIVE_EDGE);
            } else {
                g.fill(x, stripY + STRIP_H - 1, x + w - 1, stripY + STRIP_H, 0xFFFFC040);
            }
            String key = BuildGroup.TAB_KEYS.get(i).getCurrentLabel();
            String label = I18n.get(groups[i].langKey()) + (key.isEmpty() ? "" : " " + key);
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

    /** A left click on the strip opens a tab; on the open tab it goes home. Returns whether it was on the strip. */
    public static boolean click(int mouseX, int mouseY) {
        if (!isOver(mouseX, mouseY))
            return false;
        BuildGroup[] groups = BuildGroup.values();
        int tabW = stripW / groups.length;
        int i = Math.min(groups.length - 1, (mouseX - stripX) / Math.max(1, tabW));
        if (open && groups[i] == selected)
            close();
        else
            open(groups[i]);
        clickSound();
        return true;
    }

    private static void clickSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null)
            mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.2f, 1.0f);
    }

    private static int[] keyCodes(List<Keybinding> keys) {
        int[] out = new int[keys.size()];
        for (int i = 0; i < out.length; i++)
            out[i] = keys.get(i).getKey();
        return out;
    }

    private static boolean[] usable() {
        boolean[] out = new boolean[drawn.size()];
        for (int i = 0; i < out.length; i++) {
            Button b = drawn.get(i);
            out[i] = (b.isHidden == null || !b.isHidden.get()) && (b.isEnabled == null || b.isEnabled.get());
        }
        return out;
    }

    /** True when the key is a live grid key now (a tab open, a usable building in its cell): the card owns it. */
    public static boolean isLiveKey(int key) {
        if (!open || !isShowing())
            return false;
        int cell = BuildCategoryKeys.indexOf(key, keyCodes(BuildGroup.SLOT_KEYS));
        return cell >= 0 && BuildCategoryKeys.buttonAt(cell, drawnCells, usable()) >= 0;
    }

    /**
     * A key release while the build menu shows (HudClientEvents.onKeyRelease, after the pan-hold and ghost-rotate
     * checks): a tab key opens its tab, a grid key in an open tab picks its cell's building (BuildCategoryKeys.press).
     * Returns true when the key was used; false leaves it to the card's other buttons.
     */
    public static boolean onKey(int key) {
        if (!OrthoviewClientEvents.isEnabled())
            return false;
        boolean ctrlOrAlt = Keybindings.ctrlMod.isDown() || Keybindings.altMod.isDown();
        int tabKey = BuildCategoryKeys.indexOf(key, keyCodes(BuildGroup.TAB_KEYS));
        int gridKey = BuildCategoryKeys.indexOf(key, keyCodes(BuildGroup.SLOT_KEYS));
        boolean[] usable = usable();
        BuildCategoryKeys.Press p = BuildCategoryKeys.press(isShowing(), ctrlOrAlt, open, tabKey, gridKey,
            BuildCategoryKeys.usableCells(BuildGroup.SLOT_KEYS.size(), drawnCells, usable));
        switch (p.action()) {
            case OPEN_TAB -> {
                open(BuildGroup.values()[p.index()]);
                clickSound();
                return true;
            }
            case PICK_CELL -> {
                int i = BuildCategoryKeys.buttonAt(p.index(), drawnCells, usable);
                if (i < 0)
                    return false;
                Button b = drawn.get(i);
                clickSound();
                if (b.onLeftClick != null)
                    b.onLeftClick.run();
                if (b.onLeftClickRelease != null)
                    b.onLeftClickRelease.run();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** Once a client tick: an open tab goes home once the building it picked was placed or dropped. */
    public static void tick() {
        if (!open)
            return;
        boolean ghost = BuildingClientEvents.getBuildingToPlace() != null;
        if (ghost)
            ghostSeen = true;
        else if (ghostSeen)
            close();
    }
}
