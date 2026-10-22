package com.solegendary.reignofnether.hud;

/**
 * The HUD's rectangle maths, with no Minecraft in it, so the same numbers can be checked without a client:
 * tools/hud_layout.py mirrors every function here line for line and asserts the regions never overlap at GUI scale
 * 2, 3 and 4 on 1920x1080 and 2560x1440 (lovish plays at scale 2 or 3 on a 1080p screen: at scale 3 the logical
 * screen is only 640x360, and the bottom-left command panel used to run under the minimap). Keep the two in step:
 * change a constant here, change it there.
 *
 * Regions (BAR priority): the economy bar top-centre, the player panel top-right, the alert column under the bar
 * and left of the panel, the command panel (orders grid, portrait, unit card or selection strip) bottom-left, the
 * minimap bottom-right with its button grid on its left, the helper buttons (chat, army, idle labs, attack warning)
 * in a column on the right edge between the panel and the minimap, and the idle-worker cycler on the map's corner.
 */
public final class HudLayout {
    private HudLayout() { }

    public static final int ICON_FRAME = 22;                              // Button.DEFAULT_ICON_FRAME_SIZE
    // the command panel (orders, abilities / build menu, selection strip) uses compact tiles: 18 px frame, 12 px icon
    // (Button.compactTiles); everything else on the HUD keeps ICON_FRAME. The ability / build-menu rows are CARD_COLS
    // tiles wide (8, was 6) so the Commander's ~40 buildings need 5 rows instead of 7; the orders stay ROW = 6 wide.
    public static final int TILE = 18, CARD_COLS = 8;
    public static final int HUD_START_X = TILE * CARD_COLS + TILE / 2;    // 153: portrait starts right of the 8-wide card
    public static final int PORTRAIT_W = 60, PORTRAIT_H = 60, STATS_W = 44; // PortraitRendererUnit
    // height the alerts / kill feed keep clear above the screen bottom: the old 22 px panel and the 96 px cheat sheet
    // need it, so it stays 102 though the compact panel itself is PANEL_BODY_H tall
    public static final int PANEL_H = Math.max(PORTRAIT_H, ICON_FRAME * 4 + 6) + 8;   // 102
    public static final int PANEL_BODY_H = Math.max(PORTRAIT_H, TILE * 4 + 6) + 8;    // 86
    public static final int MIN_PER_ROW = 3, MAX_PER_ROW = 8;
    public static final int MAP_CORNER = 10, MAP_BG = 6;                  // MinimapClientEvents.CORNER_OFFSET / BG_OFFSET
    public static final double SQUARE_SCALE = 1.2;                        // MinimapClientEvents.SQUARE_SCALE
    public static final int MAP_GAP = 4;                                  // air between the command panel and the map
    // square-map button grid (HudClientEvents): 5 columns on a 28 px stride, 12 px left of the map
    public static final int GRID_STRIDE = 28, GRID_COLS = 5, GRID_PAD = 12;
    public static final int RIGHT_COL_X = 28;                             // helper buttons: screenWidth - 28
    public static final int RIGHT_COL_TOP = 40;                           // help/diplomacy, chat, army, idle labs, alert
    public static final int RIGHT_COL_STEP = 30, RIGHT_COL_STEP_TIGHT = 24, RIGHT_COL_COUNT = 5;
    public static final int UNIT_CARD_MIN_W = 70;
    // the controls cheat-sheet (HoverCards) when nothing is selected: bottom-left, 4 px in, up to 180 x 96
    public static final int CHEAT_SHEET_X = 4, CHEAT_SHEET_MAX_W = 180, CHEAT_SHEET_H = 96;
    // the hovered-building card (HoverCards): at HUD_START_X like the hovered-unit card
    public static final int BUILDING_CARD_W = 136, BUILDING_CARD_H = 61;   // 61: + the BuildingStats line

    // HUD declutter (design/hud.md, "Human-POV audit"). The stats table / its collapsed strip sit top-right where the
    // old always-on player list was (PlayerPanelClientEvents): TOP under the economy bar, dropped below the bar's
    // warning rows when they share x. Collapsed it is one 11 px strip at most STATS_STRIP_W_MAX wide.
    public static final int STATS_TOP = 38, STATS_RIGHT_MARGIN = 60, STATS_PANEL_W = 206, STATS_ROW_H = 11;
    public static final int STATS_STRIP_W_MAX = 160, STATS_STRIP_H = 11;
    // the collapsed controls cheat-sheet: a "?" button in the command panel's corner
    public static final int HELP_BUTTON = 12;
    // the spectator observer bar (ObserverBarClientEvents): bottom-left, one cell per player, a team per row (wrapped
    // when a team has more players than fit), always short of the minimap like the command panel
    public static final int OBS_X = 4, OBS_BOTTOM = 4, OBS_CELL_H = 24, OBS_CELL_W_MIN = 52, OBS_CELL_W_MAX = 84;
    public static final int OBS_MAX_PER_ROW = 8;

    /** Top of the stats strip / table: STATS_TOP, or under the economy bar's warning rows when they share its x range. */
    public static int statsTop(int barLeft, int barRight, int barBottom, int x, int w) {
        if (barRight > x && barLeft < x + w)
            return Math.max(STATS_TOP, barBottom + 3);
        return STATS_TOP;
    }

    /** Observer-bar cells per row: as many 52+ px cells as fit left of the minimap, at most 8. */
    public static int observerPerRow(int panelLimit) {
        int room = panelLimit - MAP_GAP - OBS_X;
        return Math.max(1, Math.min(OBS_MAX_PER_ROW, room / OBS_CELL_W_MIN));
    }

    public static int observerCellW(int panelLimit, int perRow) {
        return Math.min(OBS_CELL_W_MAX, (panelLimit - MAP_GAP - OBS_X) / perRow);
    }

    public static int observerHeight(int rows) {
        return rows * OBS_CELL_H + 2;
    }

    /** Side of the square minimap (MinimapClientEvents.renderSquareMap: 2 * half). */
    public static int squareSide(int mapRadius) {
        return (int) (2 * (mapRadius / Math.sqrt(2)) * SQUARE_SCALE);
    }

    /** Left edge of the minimap's backing (diamond or square). */
    public static int minimapLeft(int screenWidth, int mapRadius, boolean square) {
        int span = square ? squareSide(mapRadius) : mapRadius * 2;
        return screenWidth - MAP_CORNER - span - MAP_BG;
    }

    /** Top edge of the minimap's backing (diamond or square). */
    public static int minimapTop(int screenHeight, int mapRadius, boolean square) {
        int span = square ? squareSide(mapRadius) : mapRadius * 2;
        return screenHeight - MAP_CORNER - span - MAP_BG;
    }

    /**
     * The hard right limit of the bottom-left command panel: the minimap backing (diamond; its own buttons sit
     * inside the backing's bottom-left corner), or the square map's button grid, which hangs left of the square.
     */
    public static int commandPanelLimit(int screenWidth, int mapRadius, boolean square) {
        if (!square)
            return minimapLeft(screenWidth, mapRadius, false);
        int mapLeft = screenWidth - MAP_CORNER - squareSide(mapRadius);
        int gridRight = mapLeft - GRID_PAD;
        return gridRight - ICON_FRAME - GRID_STRIDE * (GRID_COLS - 1);
    }

    /** Where the selection strip / multi-building grid / production queue columns start (frame left edge is -5). */
    public static int stripX() {
        return HUD_START_X + PORTRAIT_W + STATS_W + 14;
    }

    /** Right edge of the command panel backing for a multi-selection with this many strip columns. */
    public static int multiPanelRight(int buttonsPerRow) {
        return HUD_START_X + PORTRAIT_W + STATS_W + TILE * buttonsPerRow + 24;
    }

    /**
     * Columns of the selection strip: as many as fit between the stats and the minimap, 3..8. RoN sized this off
     * the screen width alone (ceil((w - 340) / 22)), which at 640 px gave 8 columns and a panel 447 px wide under a
     * minimap whose backing starts at 504 - fine - but a large map (384) or GUI scale 4 (344) ran straight under it.
     */
    public static int buttonsPerRow(int panelLimit) {
        int n = (panelLimit - MAP_GAP - multiPanelRight(0)) / TILE;
        return Math.max(MIN_PER_ROW, Math.min(MAX_PER_ROW, n));
    }

    /**
     * Width of the single-unit card beside the stats: the full 124 px when there is room, narrower before the map
     * (its backing ends 5 px right of the card). Under UNIT_CARD_MIN_W the caller leaves the card out.
     */
    public static int unitCardWidth(int cardX, int panelLimit, int preferred) {
        return Math.min(preferred, panelLimit - MAP_GAP - 5 - cardX);
    }

    /**
     * Spacing of the right-edge helper column: 30 px when all five rows clear the minimap's top, else 24 (22 px
     * tiles with 2 px air) so a large map, or GUI scale 4, doesn't put the attack warning on the diamond.
     */
    public static int rightColumnStep(int minimapTop) {
        int bottom = RIGHT_COL_TOP + RIGHT_COL_STEP * (RIGHT_COL_COUNT - 1) + ICON_FRAME;
        return bottom <= minimapTop - MAP_GAP ? RIGHT_COL_STEP : RIGHT_COL_STEP_TIGHT;
    }

    /** y of the n-th helper button on the right edge (0 = help/diplomacy, 1 = chat, 2 = army, 3 = idle labs, 4 = alert). */
    public static int rightColumnY(int index, int step) {
        return RIGHT_COL_TOP + step * index;
    }

    /**
     * The idle-worker cycler (BAR's idle-constructor button) sits on the minimap's top-left corner: on the diamond
     * that corner of the backing is empty (a 22 px tile there stays outside the diamond for any radius over 42), and
     * the panel above and the command panel to the left both stop short of the backing. For the square map it
     * takes the free top-right cell of the button grid instead (HudClientEvents).
     */
    public static int idleWorkerX(int screenWidth, int mapRadius) {
        return minimapLeft(screenWidth, mapRadius, false) + 2;
    }

    /**
     * The backing's bottom-left corner (it was the top-left one): at GUI scale 3 the top-left corner reached into the
     * playfield centre the declutter pass keeps clear (tools/hud_layout.py clearance), the bottom-left one is just
     * as empty (the diamond's own buttons sit right of it) and further from the middle of the screen.
     */
    public static int idleWorkerY(int screenHeight, int mapRadius) {
        return screenHeight - MAP_CORNER + MAP_BG - 2 - ICON_FRAME;
    }

    /**
     * Right edge limit of the centre-top alert column: when the minimap's top comes within 100 px of the economy bar
     * (the large map on a small window), the column stops short of the map's left edge so a stack of toasts can't
     * run onto the map's corner.
     */
    public static int alertColumnMapLimit(int screenWidth, int screenHeight, int mapRadius, boolean square, int barBottom) {
        if (minimapTop(screenHeight, mapRadius, square) < barBottom + 100)
            return minimapLeft(screenWidth, mapRadius, square) - 4;
        return Integer.MAX_VALUE;
    }
}
