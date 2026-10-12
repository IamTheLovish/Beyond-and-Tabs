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
    public static final int HUD_START_X = ICON_FRAME * 6 + ICON_FRAME / 2; // 143: the orders grid is 6 tiles wide
    public static final int PORTRAIT_W = 60, PORTRAIT_H = 60, STATS_W = 44; // PortraitRendererUnit
    public static final int PANEL_H = Math.max(PORTRAIT_H, ICON_FRAME * 4 + 6) + 8;   // 102
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
        return HUD_START_X + PORTRAIT_W + STATS_W + ICON_FRAME * buttonsPerRow + 24;
    }

    /**
     * Columns of the selection strip: as many as fit between the stats and the minimap, 3..8. RoN sized this off
     * the screen width alone (ceil((w - 340) / 22)), which at 640 px gave 8 columns and a panel 447 px wide under a
     * minimap whose backing starts at 504 - fine - but a large map (384) or GUI scale 4 (344) ran straight under it.
     */
    public static int buttonsPerRow(int panelLimit) {
        int n = (panelLimit - MAP_GAP - multiPanelRight(0)) / ICON_FRAME;
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

    public static int idleWorkerY(int screenHeight, int mapRadius) {
        return minimapTop(screenHeight, mapRadius, false) + 2;
    }
}
