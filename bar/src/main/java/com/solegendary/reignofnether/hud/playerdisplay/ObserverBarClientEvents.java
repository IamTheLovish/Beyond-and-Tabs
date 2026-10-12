package com.solegendary.reignofnether.hud.playerdisplay;

import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.config.ReignOfNetherClientConfigs;
import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.hud.HudLayout;
import com.solegendary.reignofnether.hud.HudPanels;
import com.solegendary.reignofnether.minimap.MinimapClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.player.PlayerColors;
import com.solegendary.reignofnether.unit.UnitClientEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The spectator observer bar (design/hud.md; ZeroSpace's per-player bottom bar, BAR's spectator panels): while you
 * watch - defeated, a bots-only Watch Battle, or a lobby player who joined mid-match - a compact strip along the
 * bottom-left shows one cell per player: name in their colour, metal income, army value, unit count, tech tier and
 * how many faction powers are ready. One row per team (wrapped when a team has more players than fit), always short
 * of the minimap (HudLayout.observerPerRow / observerCellW, mirrored by tools/hud_layout.py).
 *
 * The figures come from the 2 s roster packet, which gives a spectator every side's numbers; every string is built
 * when it arrives (PlayerPanelClientEvents.Line), so a frame only draws. It gives way to the command panel when the
 * spectator selects something, and hides with clean view, F1 / hideGui and the hud_observer_bar setting.
 */
public class ObserverBarClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();
    static final float SCALE = 0.75f;
    static final int LINE = 7;   // 0.75x text line height
    static final int BG = 0xB0101216, CELL_BG = 0x60202428;

    private static boolean zoneOn = false;
    private static int zoneX, zoneY, zoneW, zoneH;

    public static boolean isMouseOverHud(int mx, int my) {
        return zoneOn && mx >= zoneX && mx < zoneX + zoneW && my >= zoneY && my < zoneY + zoneH;
    }

    /** True while the bar should be drawn (a spectator, a live roster, nothing selected, extras showing). */
    static boolean showing() {
        if (!HudPanels.showExtras() || PlayerClientEvents.isRTSPlayer() || !OrthoviewClientEvents.isEnabled())
            return false;
        if (PlayerPanelClientEvents.matchLines().isEmpty() || PlayerPanelClientEvents.isAnyMenuOpen())
            return false;
        if (!UnitClientEvents.getSelectedUnits().isEmpty() || !BuildingClientEvents.getSelectedBuildings().isEmpty())
            return false;
        try {
            return ReignOfNetherClientConfigs.HUD_OBSERVER_BAR.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    @SubscribeEvent
    public static void onDrawScreen(ScreenEvent.Render.Post evt) {
        zoneOn = false;
        if (!(evt.getScreen() instanceof TopdownGui) || MC.level == null || !showing())
            return;
        List<PlayerPanelClientEvents.Line> lines = PlayerPanelClientEvents.matchLines();
        int sw = MC.getWindow().getGuiScaledWidth();
        int sh = MC.getWindow().getGuiScaledHeight();
        boolean square = ReignOfNetherClientConfigs.SQUARE_MINIMAP.get();
        int limit = HudLayout.commandPanelLimit(sw, MinimapClientEvents.getMapGuiRadius(), square);
        int perRow = HudLayout.observerPerRow(limit);
        int cellW = HudLayout.observerCellW(limit, perRow);

        // rows: a new row at each team change (lines arrive grouped by team for a spectator: sorted at sync) or
        // when a row is full - counted first so the bar can sit on the bottom edge
        int rows = 0, col = 0, lastTeam = Integer.MIN_VALUE;
        for (int i = 0; i < lines.size(); i++) {
            int team = lines.get(i).e.team();
            if (rows == 0 || team != lastTeam || col >= perRow) {
                rows++;
                col = 0;
                lastTeam = team;
            }
            col++;
        }
        int h = HudLayout.observerHeight(rows);
        int x0 = HudLayout.OBS_X, y0 = sh - HudLayout.OBS_BOTTOM - h;
        int barW = cellW * perRow;
        GuiGraphics gg = evt.getGuiGraphics();
        Font font = MC.font;
        gg.fill(x0, y0, x0 + barW, y0 + h, BG);
        zoneOn = true;
        zoneX = x0;
        zoneY = y0;
        zoneW = barW;
        zoneH = h;

        int row = -1;
        col = 0;
        lastTeam = Integer.MIN_VALUE;
        PlayerPanelClientEvents.Line hovered = null;
        int mx = HudClientEvents.mouseX, my = HudClientEvents.mouseY;
        for (int i = 0; i < lines.size(); i++) {
            PlayerPanelClientEvents.Line l = lines.get(i);
            int team = l.e.team();
            if (row < 0 || team != lastTeam || col >= perRow) {
                row++;
                col = 0;
                lastTeam = team;
            }
            int cx = x0 + col * cellW, cy = y0 + 1 + row * HudLayout.OBS_CELL_H;
            renderCell(gg, font, l, cx, cy, cellW);
            if (mx >= cx && mx < cx + cellW && my >= cy && my < cy + HudLayout.OBS_CELL_H)
                hovered = l;
            col++;
        }
        // the exact numbers on hover (allocates only while hovered)
        if (hovered != null) {
            var e = hovered.e;
            List<Component> tip = new ArrayList<>(4);
            tip.add(Component.literal(e.name()));
            tip.add(Component.translatable("hud.reignofnether.observer.tip_eco", hovered.metal, hovered.energy));
            tip.add(Component.translatable("hud.reignofnether.observer.tip_army", hovered.army, e.units(), e.kills(), e.losses()));
            tip.add(Component.translatable("hud.reignofnether.observer.tip_tech", Math.max(1, e.tier()), e.powersReady()));
            gg.renderComponentTooltip(font, tip, mx, my);
        }
    }

    private static void renderCell(GuiGraphics gg, Font font, PlayerPanelClientEvents.Line l, int x, int y, int w) {
        gg.fill(x + 1, y, x + w - 1, y + HudLayout.OBS_CELL_H - 1, CELL_BG);
        gg.fill(x + 1, y, x + 3, y + HudLayout.OBS_CELL_H - 1, 0xFF000000 | (PlayerColors.getPlayerDisplayColorHex(l.e.name()) & 0xFFFFFF));
        gg.pose().pushPose();
        gg.pose().translate(x + 5, y + 2, 0);
        gg.pose().scale(SCALE, SCALE, 1f);
        int inner = (int) ((w - 7) / SCALE);
        // line 1: name (struck grey once defeated), tier at the right
        gg.drawString(font, l.obsName, 0, 0, l.nameColour);
        int tierW = font.width(l.obsTier);
        gg.drawString(font, l.obsTier, inner - tierW, 0, 0xFFB4DCFF);
        if (l.e.alive()) {
            // line 2: metal income, army value at the right
            gg.drawString(font, l.obsIncome, 0, 10, PlayerPanelClientEvents.METAL_COL);
            gg.drawString(font, l.obsArmy, inner - font.width(l.obsArmy), 10, PlayerPanelClientEvents.ARMY_COL);
            // line 3: units, powers ready at the right
            gg.drawString(font, l.obsUnits, 0, 20, 0xFFE0E4EA);
            if (!l.obsPowers.isEmpty())
                gg.drawString(font, l.obsPowers, inner - font.width(l.obsPowers), 20, 0xFFFFC860);
        } else {
            gg.drawString(font, "defeated", 0, 10, 0xFF905050);
        }
        gg.pose().popPose();
    }
}
