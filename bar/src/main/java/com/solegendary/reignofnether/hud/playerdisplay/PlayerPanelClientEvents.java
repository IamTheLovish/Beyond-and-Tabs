package com.solegendary.reignofnether.hud.playerdisplay;

import com.solegendary.reignofnether.alliance.AlliancesClient;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ActiveProduction;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.hud.HoverCards;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.hud.HudLayout;
import com.solegendary.reignofnether.hud.HudPanels;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.minimap.MinimapClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.orthoview.StrategicViewClientEvents;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.player.PlayerColors;
import com.solegendary.reignofnether.player.PlayerPanelClientboundPacket;
import com.solegendary.reignofnether.util.MyRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The stats table (design/hud.md): what used to be BAR's always-on player list in the top-right corner. lovish:
 * "there's a lot of things that pop on the screen that take up a lot of space, like the AI and player teams". It is
 * now collapsed by default to one slim strip - your colour, "Allies n · Enemies m" and a tiny commander-HP pip per
 * allied commander - and I (rebindable, F4 alias) or a click opens a tabbed table in the same corner, the way
 * ZeroSpace's "INCOME [I]" panel and BAR's collapsible list do:
 *
 * - Players: Allies / Enemies / Spectators sections (a spectator sees one section per team), alive or defeated and
 *   the commander's health where the server sends it;
 * - Economy: BAR's two thin eco bars (metal, energy: stored / storage, income beside) - allies only; enemies only
 *   when spectating or defeated, because the server never sends an enemy's economy to a player still in the match;
 * - Army: army value (metal in non-worker units), units, kills, losses - same gate;
 * - Production: what your own and your allies' labs are making right now (from the client's own queues).
 *
 * Open / closed and the tab are remembered (HudPanels, client config). Fed every 2 s by PlayerPanelServerEvents to
 * every client alike, so a lobby match's mid-match joiner gets the same table as the player who started it. All
 * strings are built when the roster arrives (or every 10 ticks for the Production tab while it shows): a frame only
 * draws. A closed table skips its layout entirely; in the strategic view a table that would run into the minimap
 * steps aside, as the old list did.
 */
public class PlayerPanelClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();

    static final int PANEL_W = HudLayout.STATS_PANEL_W;
    static final int ROW_H = HudLayout.STATS_ROW_H;
    static final int TAB_H = 12;
    static final int RIGHT_MARGIN = HudLayout.STATS_RIGHT_MARGIN;
    static final int PAD = 3;
    static final int BG = 0xA0101216;
    static final int METAL_COL = 0xFFB4BEC8, ENERGY_COL = 0xFFF0C83C;   // as on the economy bar
    static final int ARMY_COL = 0xFFFF9A7A, MUTED = 0xFF9AA0A6;
    static final int TAG_COL = 0xFF9AA0A6;
    static final float TAG_SCALE = 0.75f;
    // eco columns, right-aligned from the panel's right edge: hp bar, then energy and metal as [bar][income]. The
    // name gets the rest (65 px at PANEL_W 206: 8-10 characters, the rest is cut with "..")
    static final int HP_W = 20, NUM_W = 28, BAR_W = 16, BAR_H = 5;
    static final int ENERGY_RIGHT = HP_W + 8, METAL_RIGHT = ENERGY_RIGHT + NUM_W + 2 + BAR_W + 4;
    static final int ALLY_COLS_W = METAL_RIGHT + NUM_W + 2 + BAR_W + 4;
    // army columns (right edges from the panel's right edge): losses, kills, units, army value
    static final int COL_W = 26, LOSS_R = 4, KILL_R = LOSS_R + COL_W, UNIT_R = KILL_R + COL_W, ARMY_R = UNIT_R + COL_W;
    static final int NAME_X = 13;
    /** Player colours darker than this (0-255 perceived luminance) are lifted to it so they read on BG. */
    static final int MIN_NAME_LUMA = 135;
    static final int MAX_PIPS = 16, PIP_W = 2, PIP_STEP = 3;
    static final int PROD_EVERY_TICKS = 10, MAX_PROD_ROWS = 24;

    /** One player in the match, with every string a frame needs (built per sync, never per frame). */
    static final class Line {
        PlayerPanelClientboundPacket.Entry e;
        ResourceLocation icon;
        String name, tag, metal, energy, army, units, kills, losses;
        int nameColour;
        // observer bar
        String obsName, obsIncome, obsArmy, obsUnits, obsTier, obsPowers;
    }

    static final int HEADER = 0, PLAYER = 1, SPECTATOR = 2, NOTE = 3, PROD = 4, COLS = 5;

    /** A table row: a section header, a player line, a spectator, a note, a lab, or the army tab's column labels. */
    static final class Row {
        final int kind;
        final Line line;
        final String text, text2;
        final int colour;

        Row(int kind, Line line, String text, String text2, int colour) {
            this.kind = kind;
            this.line = line;
            this.text = text;
            this.text2 = text2;
            this.colour = colour;
        }
    }

    private static List<Line> lines = List.of();
    private static List<Row> playersRows = List.of(), ecoRows = List.of(), armyRows = List.of();
    private static List<Row> prodRows = List.of();
    private static boolean spectatorView = false;
    private static String stripText = "";
    private static int stripTextW = 0, stripW = 0, stripColour = 0xFF808080, pipCount = 0;
    private static final float[] pips = new float[MAX_PIPS];
    private static final String[] tabLabels = new String[HudPanels.TABS];
    private static final int[] tabLabelW = new int[HudPanels.TABS];
    private static final String[] colLabels = { "", "", "", "" };
    private static final String[] COL_KEYS = { "army", "units", "kills", "losses" };
    private static String moreText = "";
    private static int moreN = -1;
    private static int prodTicks = 0;

    // the drawn rectangle this frame (fields, not a RectZone: nothing allocates per frame)
    private static boolean zoneOn = false;
    private static int zoneX, zoneY, zoneW, zoneH;

    /** Match players in the server's order (you, allies, enemies) - the observer bar reads them too. */
    static List<Line> matchLines() {
        return lines;
    }

    /**
     * The server's latest line for {@code name} (every 2 s), or null. The observer table reads its population from
     * here: what the client has loaded is only what is near the camera. No allocation: a walk over at most 16 lines.
     */
    public static PlayerPanelClientboundPacket.Entry entryOf(String name) {
        for (Line l : lines)
            if (l.e.name().equals(name))
                return l.e;
        return null;
    }

    /**
     * The panel's left edge for this screen width, or Integer.MAX_VALUE when it isn't showing: the HUD keeps its
     * centre-top alert banner and toasts left of it (HudClientEvents.alertColumn) so they never draw over it.
     */
    public static int visibleLeft(int screenWidth) {
        if (!HudPanels.showExtras() || lines.isEmpty() || !OrthoviewClientEvents.isEnabled() || isAnyMenuOpen())
            return Integer.MAX_VALUE;
        return screenWidth - RIGHT_MARGIN - (HudPanels.statsOpen ? PANEL_W : stripW);
    }

    public static void sync(List<PlayerPanelClientboundPacket.Entry> entries) {
        String me = MC.player == null ? "" : MC.player.getName().getString();
        spectatorView = !PlayerClientEvents.isRTSPlayer();
        List<Line> out = new ArrayList<>(entries.size());
        List<String> spectators = new ArrayList<>();
        for (var e : entries) {
            if (e.isSpectator()) {
                spectators.add(e.name());
                continue;
            }
            out.add(line(e));
        }
        // a spectator has no "allies": group by team (stable, so alive-before-defeated holds within a team) - the
        // observer bar starts a row at each team change
        if (spectatorView)
            out.sort((a, b) -> Integer.compare(a.e.team(), b.e.team()));
        lines = out;

        // section membership: you + allies vs the rest (spectators group by team instead)
        List<Line> allies = new ArrayList<>(), enemies = new ArrayList<>();
        for (Line l : out)
            (l.e.isSelf() || l.e.isAlly() ? allies : enemies).add(l);

        playersRows = buildRows(allies, enemies, spectators, 0);
        ecoRows = buildRows(allies, enemies, List.of(), 1);
        armyRows = buildRows(allies, enemies, List.of(), 2);
        for (int i = 0; i < HudPanels.TABS; i++) {
            tabLabels[i] = I18n.get("hud.reignofnether.stats.tab." + i);
            tabLabelW[i] = MC.font.width(tabLabels[i]);
        }
        for (int i = 0; i < COL_KEYS.length; i++)
            colLabels[i] = I18n.get("hud.reignofnether.stats.col." + COL_KEYS[i]);

        // the collapsed strip: your colour, the counts, a pip per allied commander (every commander for a spectator)
        String key = Keybindings.statsTable.getCurrentLabel();
        String hint = key.isEmpty() ? "" : "  [" + key + "]";
        stripText = (spectatorView ? I18n.get("hud.reignofnether.stats.strip_spectating", out.size())
            : I18n.get("hud.reignofnether.stats.strip", allies.size(), enemies.size())) + hint;
        stripColour = spectatorView || me.isEmpty() ? 0xFF9AA0A6 : 0xFF000000 | (PlayerColors.getPlayerDisplayColorHex(me) & 0xFFFFFF);
        pipCount = 0;
        for (Line l : spectatorView ? out : allies) {
            if (pipCount >= MAX_PIPS)
                break;
            if (l.e.hasAllyData())
                pips[pipCount++] = l.e.alive() ? l.e.commanderHp() : -1;
        }
        int fixed = 12 + 4 + 2 + HudPanels.CHEVRON + 2;   // swatch + gaps + chevron
        int maxText = HudLayout.STATS_STRIP_W_MAX - fixed - pipCount * PIP_STEP;
        if (MC.font.width(stripText) > maxText) {
            // drop pips before cutting the text
            while (pipCount > 0 && MC.font.width(stripText) > maxText) {
                pipCount--;
                maxText += PIP_STEP;
            }
            if (MC.font.width(stripText) > maxText)
                stripText = MC.font.plainSubstrByWidth(stripText, maxText);
        }
        stripTextW = MC.font.width(stripText);
        stripW = Math.min(HudLayout.STATS_STRIP_W_MAX, fixed + stripTextW + pipCount * PIP_STEP);
        prodTicks = PROD_EVERY_TICKS;   // refresh the Production tab soon
    }

    private static Line line(PlayerPanelClientboundPacket.Entry e) {
        Line l = new Line();
        l.e = e;
        Faction f = Factions.getFaction(e.factionKey().isEmpty() ? null : ResourceLocation.tryParse(e.factionKey()));
        l.icon = f == null || f == Factions.NONE ? null : f.icon;
        String[] nameTag = fitName(e);
        l.name = nameTag[0];
        l.tag = nameTag[1];
        l.metal = "+" + fmt(e.metalIncome());
        l.energy = "+" + fmt(e.energyIncome());
        l.army = fmt(e.armyValue());
        l.units = String.valueOf(e.units());
        l.kills = String.valueOf(e.kills());
        l.losses = String.valueOf(e.losses());
        l.nameColour = e.alive() ? readable(PlayerColors.getPlayerDisplayColorHex(e.name()) & 0xFFFFFF) : 0xFF909090;
        String n = e.name();
        int open = n.lastIndexOf(" (");
        if (open > 0 && n.endsWith(")"))
            n = n.substring(0, open);
        l.obsName = n.length() > 12 ? n.substring(0, 11) + "." : n;
        l.obsIncome = "+" + fmt(e.metalIncome());
        l.obsArmy = fmt(e.armyValue());
        l.obsUnits = e.units() + "u";
        l.obsTier = "T" + Math.max(1, e.tier());
        l.obsPowers = e.powersReady() > 0 ? "P" + e.powersReady() : "";
        return l;
    }

    /**
     * The rows of one tab. Players (0): allies, enemies, spectators - or one section per team for a spectator.
     * Economy (1) and Army (2): only lines that carry data (yours and your allies', or everyone's when spectating),
     * with a note when the enemy's numbers are hidden.
     */
    private static List<Row> buildRows(List<Line> allies, List<Line> enemies, List<String> spectators, int tab) {
        List<Row> rows = new ArrayList<>();
        if (tab == 2)
            rows.add(new Row(COLS, null, null, null, MUTED));
        if (spectatorView) {
            int team = Integer.MIN_VALUE;
            List<Line> byTeam = new ArrayList<>(lines);
            byTeam.sort((a, b) -> Integer.compare(a.e.team(), b.e.team()));
            int teamNo = 0;
            for (Line l : byTeam) {
                if (l.e.team() != team) {
                    team = l.e.team();
                    teamNo++;
                    rows.add(new Row(HEADER, null, I18n.get("hud.reignofnether.stats.team", teamNo), null, MUTED));
                }
                rows.add(new Row(PLAYER, l, null, null, 0));
            }
        } else {
            rows.add(new Row(HEADER, null, I18n.get("hud.reignofnether.stats.allies", allies.size()), null, 0xFF80C880));
            for (Line l : allies)
                rows.add(new Row(PLAYER, l, null, null, 0));
            if (!enemies.isEmpty()) {
                rows.add(new Row(HEADER, null, I18n.get("hud.reignofnether.stats.enemies", enemies.size()), null, 0xFFE08080));
                boolean anyData = false;
                for (Line l : enemies)
                    if (tab == 0 || l.e.hasAllyData()) {
                        rows.add(new Row(PLAYER, l, null, null, 0));
                        anyData = true;
                    }
                if (!anyData)
                    rows.add(new Row(NOTE, null, I18n.get("hud.reignofnether.stats.enemy_hidden"), null, MUTED));
            }
        }
        if (!spectators.isEmpty()) {
            rows.add(new Row(HEADER, null, I18n.get("hud.reignofnether.stats.spectators", spectators.size()), null, MUTED));
            for (String s : spectators)
                rows.add(new Row(SPECTATOR, null, MC.font.plainSubstrByWidth(s, PANEL_W - NAME_X - 4), null, MUTED));
        }
        return rows;
    }

    /** Production tab: the head of every own / allied lab's queue (every lab the client knows of for a spectator). */
    private static void rebuildProduction() {
        String me = MC.player == null ? "" : MC.player.getName().getString();
        List<Row> mine = new ArrayList<>(), theirs = new ArrayList<>();
        for (BuildingPlacement b : BuildingClientEvents.getBuildings()) {
            if (!(b instanceof ProductionPlacement pp) || pp.productionQueue.isEmpty() || b.ownerName == null)
                continue;
            boolean own = b.ownerName.equals(me);
            if (!own && !spectatorView && !AlliancesClient.isAllied(me, b.ownerName))
                continue;
            ActiveProduction ap = pp.productionQueue.get(0);
            float total = ap.item.getCost(true, pp.ownerName).ticks;
            int pct = Math.round(Math.max(0, Math.min(1, total <= 0 ? 0 : 1f - ap.ticksLeft / total)) * 100);
            String text = HoverCards.buildingName(b) + ": " + ap.item.getItemName()
                + (pp.productionQueue.size() > 1 ? " +" + (pp.productionQueue.size() - 1) : "");
            int col = 0xFF000000 | (PlayerColors.getPlayerDisplayColorHex(b.ownerName) & 0xFFFFFF);
            String pctStr = pct + "%";
            int textW = PANEL_W - NAME_X - 6 - MC.font.width(pctStr);
            (own ? mine : theirs).add(new Row(PROD, null, MC.font.plainSubstrByWidth(text, textW), pctStr, col));
        }
        List<Row> rows = new ArrayList<>(Math.min(MAX_PROD_ROWS, mine.size() + theirs.size() + 1));
        for (Row r : mine)
            if (rows.size() < MAX_PROD_ROWS)
                rows.add(r);
        for (Row r : theirs)
            if (rows.size() < MAX_PROD_ROWS)
                rows.add(r);
        if (rows.isEmpty())
            rows.add(new Row(NOTE, null, I18n.get("hud.reignofnether.stats.no_production"), null, MUTED));
        prodRows = rows;
    }

    /**
     * The name cut to its column once per sync (it was measured and substringed every frame), as {name, tag}: a bot's
     * trailing " (hard)" becomes a separate tag drawn small and grey (null if there is none or it doesn't fit).
     */
    static String[] fitName(PlayerPanelClientboundPacket.Entry e) {
        Font font = MC.font;
        int nameW = e.hasAllyData() ? PANEL_W - NAME_X - ALLY_COLS_W
                : PANEL_W - NAME_X - 4 - font.width("defeated") - 4;
        String name = e.name();
        String tag = null;
        int open = name.lastIndexOf(" (");
        if (open > 0 && name.endsWith(")")) {
            tag = name.substring(open + 1);
            name = name.substring(0, open);
        }
        int baseW = font.width(name);
        if (tag != null && baseW + 2 + tagWidth(font, tag) > nameW)
            tag = null;   // the name matters more than the difficulty
        if (baseW > nameW)
            name = font.plainSubstrByWidth(name, nameW - font.width("..")) + "..";
        return new String[] { name, tag };
    }

    static int tagWidth(Font font, String tag) {
        return (int) Math.ceil(font.width(tag) * TAG_SCALE);
    }

    /**
     * A player colour readable on the dark panel: below MIN_NAME_LUMA it is mixed toward white just enough to reach
     * it, so dark blue becomes a light periwinkle rather than grey and teams stay recognisable.
     */
    public static int readable(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int luma = (r * 299 + g * 587 + b * 114) / 1000;
        if (luma >= MIN_NAME_LUMA)
            return 0xFF000000 | rgb;
        float f = (MIN_NAME_LUMA - luma) / (float) (255 - luma);
        r += Math.round((255 - r) * f);
        g += Math.round((255 - g) * f);
        b += Math.round((255 - b) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static void clear() {
        lines = List.of();
        playersRows = List.of();
        ecoRows = List.of();
        armyRows = List.of();
        prodRows = List.of();
        zoneOn = false;
        stripW = 0;
    }

    static String fmt(float v) {
        if (v >= 1000)
            return String.format("%.1fk", v / 1000f);
        if (v >= 100)
            return String.valueOf(Math.round(v));
        return String.format("%.1f", v);
    }

    /** Any RoN menu/overlay drawn over the TopdownGui: diplomacy/observer displays, gamerules, scenario editor. */
    static boolean isAnyMenuOpen() {
        return PlayerDisplayClientEvents.isAnyDisplayOpen()
            || com.solegendary.reignofnether.gamerules.GameruleClient.gamerulesMenuOpen
            || com.solegendary.reignofnether.scenario.ScenarioClientEvents.isMenuOpen()
            || (MC.screen != null && !(MC.screen instanceof TopdownGui));
    }

    public static boolean isMouseOverHud(int mouseX, int mouseY) {
        return zoneOn && mouseX >= zoneX && mouseX < zoneX + zoneW && mouseY >= zoneY && mouseY < zoneY + zoneH;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || !HudPanels.statsOpen || HudPanels.statsTab != 3 || lines.isEmpty()
                || MC.level == null || ++prodTicks < PROD_EVERY_TICKS)
            return;
        prodTicks = 0;
        rebuildProduction();
    }

    @SubscribeEvent
    public static void onDrawScreen(ScreenEvent.Render.Post evt) {
        zoneOn = false;
        if (!HudPanels.showExtras() || lines.isEmpty() || !OrthoviewClientEvents.isEnabled()
                || !(evt.getScreen() instanceof TopdownGui) || MC.level == null)
            return;
        // step aside for every menu or overlay drawn over the RTS view: it rendered on top of the diplomacy screen's
        // Enemy/Allied column (lovish's clip 15-51). Vanilla screens (pause, chat, inventory) replace the TopdownGui,
        // so the instanceof test above already hides it for those.
        if (isAnyMenuOpen())
            return;
        int sw = MC.getWindow().getGuiScaledWidth();
        int sh = MC.getWindow().getGuiScaledHeight();
        GuiGraphics gg = evt.getGuiGraphics();
        var bar = HudClientEvents.economyBar;
        if (!HudPanels.statsOpen) {
            renderStrip(gg, sw, bar);
            return;
        }
        int x = sw - RIGHT_MARGIN - PANEL_W;
        // drop below the economy bar's warning row (stall / WASTING chips) when it's showing over our x range
        int y = bar == null ? HudLayout.STATS_TOP : HudLayout.statsTop(bar.left(), bar.right(), bar.bottom(), x, PANEL_W);
        List<Row> rows = switch (HudPanels.statsTab) {
            case 1 -> ecoRows;
            case 2 -> armyRows;
            case 3 -> prodRows;
            default -> playersRows;
        };
        int fullH = PAD * 2 + TAB_H + rows.size() * ROW_H;

        // the minimap is anchored bottom-right and spans the panel's x range (diamond or square, both fit in 2R)
        int mapTop = sh - MinimapClientEvents.getMapGuiRadius() * 2 - MinimapClientEvents.CORNER_OFFSET
            - MinimapClientEvents.BG_OFFSET - 2;
        int shown = rows.size();
        if (y + fullH > mapTop) {
            if (StrategicViewClientEvents.isStrategicView()) {
                renderStrip(gg, sw, bar);   // the map overview matters more: fold back to the strip for now
                return;
            }
            // keep a line free for "+N more"
            shown = Math.max(0, (mapTop - y - PAD * 2 - TAB_H) / ROW_H - 1);
            if (shown >= rows.size())
                shown = rows.size();
        }
        boolean more = shown < rows.size();
        int h = PAD * 2 + TAB_H + (shown + (more ? 1 : 0)) * ROW_H;

        Font font = MC.font;
        gg.fill(x, y, x + PANEL_W, y + h, BG);
        setZone(x, y, PANEL_W, h);
        renderTabs(gg, font, x, y);

        int ry = y + TAB_H + PAD;
        Line hovered = null;
        int mx = HudClientEvents.mouseX, my = HudClientEvents.mouseY;
        for (int i = 0; i < shown; i++) {
            Row row = rows.get(i);
            renderRow(gg, font, row, x, ry);
            if (row.kind == PLAYER && HudPanels.statsTab == 1 && row.line.e.hasAllyData() && row.line.e.alive()
                    && mx >= x && mx < x + PANEL_W && my >= ry - 1 && my < ry + ROW_H - 1)
                hovered = row.line;
            ry += ROW_H;
        }
        if (more) {
            int n = rows.size() - shown;
            if (n != moreN) {   // rebuilt only when the count changes
                moreN = n;
                moreText = I18n.get("hud.reignofnether.stats.more", n);
            }
            gg.drawString(font, moreText, x + 4, ry + 1, 0xFFAAAAAA);
        }
        // the exact figures behind the bars, only while a line is under the mouse (a tooltip allocates)
        if (hovered != null)
            gg.renderComponentTooltip(font, ecoTooltip(hovered.e), mx, my);
    }

    private static void setZone(int x, int y, int w, int h) {
        zoneOn = true;
        zoneX = x;
        zoneY = y;
        zoneW = w;
        zoneH = h;
    }

    /** The collapsed strip: [your colour] Allies n · Enemies m [I]  ▮▮▮ (allied commander HP)  [>]. */
    private static void renderStrip(GuiGraphics gg, int sw, com.solegendary.reignofnether.hud.EconomyBarRenderer.Layout bar) {
        int w = stripW;
        int x = sw - RIGHT_MARGIN - w;
        int y = bar == null ? HudLayout.STATS_TOP : HudLayout.statsTop(bar.left(), bar.right(), bar.bottom(), x, w);
        int h = HudLayout.STATS_STRIP_H;
        boolean hover = HudClientEvents.mouseX >= x && HudClientEvents.mouseX < x + w
            && HudClientEvents.mouseY >= y && HudClientEvents.mouseY < y + h;
        gg.fill(x, y, x + w, y + h, hover ? 0xC0283038 : BG);
        gg.fill(x + 3, y + 2, x + 9, y + 8, stripColour);
        gg.drawString(MC.font, stripText, x + 12, y + 2, 0xFFE0E4EA);
        int px = x + 12 + stripTextW + 4;
        for (int i = 0; i < pipCount; i++) {
            float hp = pips[i];
            int col = hp < 0 ? 0xFF505050 : hp > 0.6f ? 0xFF40D040 : hp > 0.3f ? 0xFFE0C030 : 0xFFE04030;
            gg.fill(px + i * PIP_STEP, y + 2, px + i * PIP_STEP + PIP_W, y + 9, col);
        }
        // a click anywhere on the strip opens the table (the chevron is its visible handle)
        HudPanels.addZone(HudPanels.STATS, x, y, w, h);
        HudPanels.chevron(gg, HudPanels.STATS, x + w - HudPanels.CHEVRON - 1, y + 1, true);
        setZone(x, y, w, h);
    }

    private static void renderTabs(GuiGraphics gg, Font font, int x, int y) {
        int tabW = (PANEL_W - HudPanels.CHEVRON - 4) / HudPanels.TABS;
        for (int i = 0; i < HudPanels.TABS; i++) {
            int tx = x + 1 + i * tabW;
            boolean sel = i == HudPanels.statsTab;
            gg.fill(tx, y + 1, tx + tabW - 1, y + TAB_H, sel ? 0xE0384048 : 0x80202428);
            if (sel)
                gg.fill(tx, y + TAB_H - 1, tx + tabW - 1, y + TAB_H, 0xFFFFC860);
            String label = tabLabels[i] == null ? "" : tabLabels[i];
            int lw = tabLabelW[i];
            if (lw <= tabW - 4) {
                gg.drawString(font, label, tx + (tabW - lw) / 2, y + 3, sel ? 0xFFFFFFFF : MUTED);
            } else {   // a long translation: 0.75x
                gg.pose().pushPose();
                gg.pose().translate(tx + (tabW - lw * TAG_SCALE) / 2f, y + 4, 0);
                gg.pose().scale(TAG_SCALE, TAG_SCALE, 1f);
                gg.drawString(font, label, 0, 0, sel ? 0xFFFFFFFF : MUTED);
                gg.pose().popPose();
            }
            HudPanels.addZone(HudPanels.TAB_FIRST + i, tx, y, tabW, TAB_H);
        }
        HudPanels.chevron(gg, HudPanels.STATS, x + PANEL_W - HudPanels.CHEVRON - 2, y + 2, false);
    }

    private static void renderRow(GuiGraphics gg, Font font, Row row, int x, int y) {
        switch (row.kind) {
            case HEADER -> {
                gg.drawString(font, row.text, x + 4, y + 1, row.colour);
                gg.fill(x + 4 + font.width(row.text) + 4, y + 4, x + PANEL_W - 4, y + 5, 0x40FFFFFF);
            }
            case COLS -> {
                int right = x + PANEL_W;
                colLabel(gg, font, colLabels[0], right - ARMY_R, y);
                colLabel(gg, font, colLabels[1], right - UNIT_R, y);
                colLabel(gg, font, colLabels[2], right - KILL_R, y);
                colLabel(gg, font, colLabels[3], right - LOSS_R, y);
            }
            case SPECTATOR -> gg.drawString(font, row.text, x + NAME_X, y + 1, MUTED);
            case NOTE -> gg.drawString(font, font.plainSubstrByWidth(row.text, PANEL_W - 8), x + 4, y + 1, MUTED);
            case PROD -> {
                gg.fill(x + 4, y + 2, x + 9, y + 7, row.colour);
                gg.drawString(font, row.text, x + NAME_X, y + 1, 0xFFE0E4EA);
                gg.drawString(font, row.text2, x + PANEL_W - 4 - font.width(row.text2), y + 1, 0xFFFFC860);
            }
            default -> renderLine(gg, font, row.line, x, y);
        }
    }

    // column labels: lang strings resolved at sync
    private static void colLabel(GuiGraphics gg, Font font, String s, int right, int y) {
        gg.pose().pushPose();
        gg.pose().translate(right - font.width(s) * TAG_SCALE, y + 2, 0);
        gg.pose().scale(TAG_SCALE, TAG_SCALE, 1f);
        gg.drawString(font, s, 0, 0, MUTED);
        gg.pose().popPose();
    }

    private static List<net.minecraft.network.chat.Component> ecoTooltip(PlayerPanelClientboundPacket.Entry e) {
        List<net.minecraft.network.chat.Component> tip = new ArrayList<>(3);
        tip.add(net.minecraft.network.chat.Component.literal(e.name()));
        tip.add(net.minecraft.network.chat.Component.translatable("hud.reignofnether.player_panel.metal",
            Math.round(e.metalStored()), Math.round(e.metalStorage()), fmt(e.metalIncome())).withStyle(s -> s.withColor(METAL_COL & 0xFFFFFF)));
        tip.add(net.minecraft.network.chat.Component.translatable("hud.reignofnether.player_panel.energy",
            Math.round(e.energyStored()), Math.round(e.energyStorage()), fmt(e.energyIncome())).withStyle(s -> s.withColor(ENERGY_COL & 0xFFFFFF)));
        return tip;
    }

    /** A thin stored / storage bar: dark trough, the resource's colour as fill, a brighter cap when it is full. */
    private static void ecoBar(GuiGraphics gg, int bx, int by, float fill, int col) {
        gg.fill(bx - 1, by - 1, bx + BAR_W + 1, by + BAR_H - 1, 0xFF000000);
        gg.fill(bx, by, bx + BAR_W, by + BAR_H - 2, 0xFF303438);
        int w = Math.round(BAR_W * fill);
        if (w > 0)
            gg.fill(bx, by, bx + w, by + BAR_H - 2, fill >= 0.98f ? 0xFFFFFFFF : col);
    }

    private static void hpBar(GuiGraphics gg, int right, int y, float commanderHp) {
        int bx = right - 4 - HP_W, bw = HP_W, by = y + 3;
        gg.fill(bx - 1, by - 1, bx + bw + 1, by + 4, 0xFF000000);
        if (commanderHp < 0) {
            gg.fill(bx, by, bx + bw, by + 3, 0xFF404040);
        } else {
            float hp = Math.min(1f, commanderHp);
            int col = hp > 0.6f ? 0xFF40D040 : hp > 0.3f ? 0xFFE0C030 : 0xFFE04030;
            gg.fill(bx, by, bx + Math.max(1, Math.round(bw * hp)), by + 3, col);
        }
    }

    private static void renderLine(GuiGraphics gg, Font font, Line line, int x, int y) {
        var e = line.e;
        if (e.isSelf())
            gg.fill(x + 1, y - 1, x + PANEL_W - 1, y + ROW_H - 1, 0x30FFFFFF);
        if (line.icon != null)
            MyRenderer.renderIcon(gg, line.icon, x + 3, y, 8);
        String name = line.name;
        int nameW = font.width(name);
        gg.drawString(font, name, x + NAME_X, y + 1, line.nameColour, true);
        if (line.tag != null) {
            gg.pose().pushPose();
            gg.pose().translate(x + NAME_X + nameW + 2, y + 3, 0);
            gg.pose().scale(TAG_SCALE, TAG_SCALE, 1f);
            gg.drawString(font, line.tag, 0, 0, TAG_COL, true);
            gg.pose().popPose();
        }
        int right = x + PANEL_W;
        if (!e.alive()) {
            gg.fill(x + NAME_X, y + 4, x + NAME_X + nameW, y + 5, 0xFF909090);   // struck through
            if (HudPanels.statsTab != 2) {
                String s = "defeated";
                gg.drawString(font, s, right - 4 - font.width(s), y + 1, 0xFF905050);
                return;
            }
        }
        switch (HudPanels.statsTab) {
            case 1 -> {
                if (!e.hasAllyData())
                    return;
                // [bar][+income] for metal, then energy: the bar is stored / storage like BAR's list, the number the income
                gg.drawString(font, line.metal, right - METAL_RIGHT - font.width(line.metal), y + 1, METAL_COL);
                ecoBar(gg, right - METAL_RIGHT - NUM_W - 2 - BAR_W, y + 3, e.metalFill(), METAL_COL);
                gg.drawString(font, line.energy, right - ENERGY_RIGHT - font.width(line.energy), y + 1, ENERGY_COL);
                ecoBar(gg, right - ENERGY_RIGHT - NUM_W - 2 - BAR_W, y + 3, e.energyFill(), ENERGY_COL);
                hpBar(gg, right, y, e.commanderHp());
            }
            case 2 -> {
                if (!e.hasAllyData())
                    return;
                gg.drawString(font, line.army, right - ARMY_R - font.width(line.army), y + 1, ARMY_COL);
                gg.drawString(font, line.units, right - UNIT_R - font.width(line.units), y + 1, 0xFFE0E4EA);
                gg.drawString(font, line.kills, right - KILL_R - font.width(line.kills), y + 1, 0xFF80D080);
                gg.drawString(font, line.losses, right - LOSS_R - font.width(line.losses), y + 1, 0xFFE08080);
            }
            default -> {
                if (e.hasAllyData()) {
                    hpBar(gg, right, y, e.commanderHp());
                } else {
                    String s = "alive";
                    gg.drawString(font, s, right - 4 - font.width(s), y + 1, 0xFF60C060);
                }
            }
        }
    }
}
