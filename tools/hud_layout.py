#!/usr/bin/env python3
"""
HUD layout check: the rectangle maths of the RTS HUD, mirrored from the Java (no Minecraft needed), asserting that
the regions never overlap at GUI scale 2, 3 and 4 on 1920x1080 and 2560x1440.

Mirrors, function for function:
  hud/HudLayout.java              the shared constants and the panel / column / idle-worker maths
  hud/HudClientEvents.java        command panel, selection strip, hover card, alert column, right column, square grid
  hud/EconomyBarRenderer.java     the top bar's width and position
  hud/HoverCards.java             the controls cheat-sheet and the hovered-building card (nothing selected)
  hud/playerdisplay/PlayerPanelClientEvents.java   the stats table (clipped above the minimap) / its collapsed strip
  hud/playerdisplay/ObserverBarClientEvents.java   the spectator observer bar
  hud/HudPanels.java              the chevrons that fold each panel, clean view
  hud/NotificationClientEvents.java, player/ResignVoteClientEvents.java  the alert column; KillFeedClientEvents.java the side feed
  hud/SlimTiles.java              the 18 px tiles (control groups, production queue, helper column)
  minimap/MinimapClientEvents.java  the diamond / square map geometry

Change a constant in the Java, change it here. Run: python3 tools/hud_layout.py (exit 1 on any overlap).
The worst case is modelled everywhere: ten control groups, a two-row production queue (18 px slim tiles), a stall
warning, a danger banner, a resign vote, a toast row (one status line: the newest toast, drawn over the stall strip's
row when that is up), five side-feed lines (0.75x kill feed under the stats table), a 16-player roster, every helper
button showing.

Clearance (design/hud.md, "Human-POV audit"): with the default collapsed panels, at 1920x1080 GUI scale 2 and 3 no
permanent HUD element may enter the playfield centre - the ellipse inscribed in the central 70% x 70% of the screen
(the box's corners are where every RTS keeps its minimap, command card and resource bar). Transient elements - toasts
(hud_toast_seconds), the kill ticker (7 s), the danger banner, hover cards, the open stats table - are exempt. Scenes:
'collapsed' (the defaults), 'clean' (clean view), 'spectator' (observer bar), each with the full and the compact
economy bar.

Not modelled on purpose (documented limits): the large (M) minimap and the square map at 480x270 (GUI scale 4 on
1080p) - a 240 px map on a 270 px screen leaves no room for anything, and lovish plays at scale 2 or 3.
"""
import math
import sys

# ---- HudLayout.java ---------------------------------------------------------------------------------------------
ICON_FRAME = 22
TILE, CARD_COLS = 18, 8                                  # compact command-panel tile; ability / build-menu columns
HUD_START_X = TILE * CARD_COLS + TILE // 2              # 153
PORTRAIT_W, PORTRAIT_H, STATS_W = 32, 43, 68             # the icon frame, the block's height, the stats grid
PANEL_H = max(PORTRAIT_H, ICON_FRAME * 4 + 6) + 8       # 102: alert reserve (cheat sheet), not the panel body
PANEL_BODY_H = max(PORTRAIT_H, TILE * 4 + 6) + 8        # 86
MIN_PER_ROW, MAX_PER_ROW = 3, 8
MAP_CORNER, MAP_BG = 10, 6
SQUARE_SCALE = 1.2
MAP_GAP = 4
GRID_STRIDE, GRID_COLS, GRID_PAD = 28, 5, 12
RIGHT_COL_X = 28                                        # the help / diplomacy button (22 px)
RIGHT_COL_TOP = 40
RIGHT_COL_COUNT = 5
# slim tiles (HudLayout.SLIM_*, hud/SlimTiles.java): control groups, the production queue and the helper column under the
# help button are 18 px frames (Oct 22, lovish: "a lot of things are taking up space in the interface")
SLIM_TILE, SLIM_ICON, SLIM_IMAGE = 18, 10, 12
SLIM_COL_RIGHT = 6
SLIM_COL_TOP = RIGHT_COL_TOP + ICON_FRAME + 2
SLIM_COL_STEP, SLIM_COL_STEP_TIGHT = 22, 20
SLIM_QUEUE_GAP = 4
UNIT_CARD_MIN_W = 70
STATS_TOP, STATS_RIGHT_MARGIN, STATS_PANEL_W, STATS_ROW_H = 32, 60, 206, 11   # 32: under the 28 px bar, was 38
STATS_STRIP_W_MAX, STATS_STRIP_H = 160, 11
HELP_BUTTON = 12
OBS_X, OBS_BOTTOM, OBS_CELL_H, OBS_CELL_W_MIN, OBS_CELL_W_MAX, OBS_MAX_PER_ROW = 4, 4, 24, 52, 84, 8
FEED_SCALE, FEED_LINE_H, FEED_MAX_W, FEED_MAX_LINES = 0.75, 10, 160, 5   # HudLayout: the side feed (kill ticker); 0.75 = the smallest HUD text
STATUS_H = 12                                           # NotificationClientEvents.STATUS_H: the one-row status line
CHEVRON = 9                                             # HudPanels.CHEVRON
ONE_LINE_H = 15                                         # UnitInfoCard.ONE_LINE_H
CHEAT_SHEET_X, CHEAT_SHEET_MAX_W, CHEAT_SHEET_H = 4, 180, 96   # HoverCards' controls cheat-sheet
BUILDING_CARD_W, BUILDING_CARD_H = 136, 61                     # HoverCards' hovered-building card
COMPACT_W, FULL_W = 108, 128                            # UnitInfoCard
INFO_PAD_TOP, INFO_PAD_TOP_HERO, INFO_PAD_PASSIVES, HERO_EXTRA_H = 13, 17, 9, 10   # HudLayout: the info part's padding
SIDE_W, ITEMS_EXTRA_W = 44, 24                          # carried-resources frame / inventory; the Return button's column
BUILDING_ICON, BUILDING_SIDE_W = 32, 52                 # PortraitRendererBuilding
BUILDING_BLOCK_W = BUILDING_ICON + BUILDING_SIDE_W


def square_side(r):
    return int(2 * (r / math.sqrt(2)) * SQUARE_SCALE)


def minimap_left(sw, r, square):
    span = square_side(r) if square else r * 2
    return sw - MAP_CORNER - span - MAP_BG


def minimap_top(sh, r, square):
    span = square_side(r) if square else r * 2
    return sh - MAP_CORNER - span - MAP_BG


def command_panel_limit(sw, r, square):
    if not square:
        return minimap_left(sw, r, False)
    map_left = sw - MAP_CORNER - square_side(r)
    grid_right = map_left - GRID_PAD
    return grid_right - ICON_FRAME - GRID_STRIDE * (GRID_COLS - 1)


def info_height_single_worst():
    """HudLayout.infoHeightSingleWorst: a hero with passive icons (block + mana row + name, exp bar + icons above)."""
    return min(PANEL_H, PORTRAIT_H + HERO_EXTRA_H + INFO_PAD_TOP_HERO + INFO_PAD_PASSIVES)


def info_height_multi(tile=ICON_FRAME):
    """HudClientEvents.infoPanelHeight, a multi-selection: the 2-row type grid and its totals line above."""
    return min(PANEL_H, tile * 2 + 31)


def strip_x():
    return HUD_START_X + PORTRAIT_W + STATS_W + 14


def multi_panel_right(n):
    return HUD_START_X + PORTRAIT_W + STATS_W + TILE * n + 24


def buttons_per_row(limit):
    n = (limit - MAP_GAP - multi_panel_right(0)) // TILE
    return max(MIN_PER_ROW, min(MAX_PER_ROW, n))


def unit_card_width(card_x, limit, preferred):
    return min(preferred, limit - MAP_GAP - 5 - card_x)


def right_column_step(map_top):
    bottom = SLIM_COL_TOP + SLIM_COL_STEP * (RIGHT_COL_COUNT - 2) + SLIM_TILE
    return SLIM_COL_STEP if bottom <= map_top - MAP_GAP else SLIM_COL_STEP_TIGHT


def right_column_y(i, step):
    return RIGHT_COL_TOP if i == 0 else SLIM_COL_TOP + step * (i - 1)


def slim_column_x(sw):
    return sw - SLIM_COL_RIGHT - SLIM_TILE


def stats_top(bar_left, bar_right, bar_bottom, x, w):
    if bar_right > x and bar_left < x + w:
        return max(STATS_TOP, bar_bottom + 3)
    return STATS_TOP


def observer_per_row(limit):
    room = limit - MAP_GAP - OBS_X
    return max(1, min(OBS_MAX_PER_ROW, room // OBS_CELL_W_MIN))


def observer_cell_w(limit, per_row):
    return min(OBS_CELL_W_MAX, (limit - MAP_GAP - OBS_X) // per_row)


def observer_height(rows):
    return rows * OBS_CELL_H + 2


def idle_worker_xy(sw, sh, r):
    # the backing's bottom-left corner (the top-left one reached into the playfield centre at scale 3)
    return minimap_left(sw, r, False) + 2, sh - MAP_CORNER + MAP_BG - 2 - ICON_FRAME


def alert_column_map_limit(sw, sh, r, square, bar_bottom):
    if minimap_top(sh, r, square) < bar_bottom + 100:
        return minimap_left(sw, r, square) - 4
    return 10 ** 9


# ---- EconomyBarRenderer.java ------------------------------------------------------------------------------------
ECO_PANEL_H, ECO_POP_W, ECO_W_MAX, ECO_W_MIN = 28, 58, 210, 130   # 28: the bars are 5 px thick, was 34 / 7
ECO_COMPACT_H = 22


def economy_bar(sw, ctrl_groups, compact=False):
    panel_w = max(ECO_W_MIN, min(ECO_W_MAX, (sw - 200 - ECO_POP_W) // 2))
    total_w = panel_w * 2 + ECO_POP_W
    left = sw // 2 - total_w // 2
    reserved = ctrl_groups * SLIM_TILE + 4 if ctrl_groups else 0
    if left < reserved:
        left = reserved
        room = sw - 40 - left
        if total_w > room:
            panel_w = max(90, (room - ECO_POP_W) // 2)
            total_w = panel_w * 2 + ECO_POP_W
    return (left, 0, left + total_w, (ECO_COMPACT_H if compact else ECO_PANEL_H) + 1)


def economy_chevron(bar):
    """HudPanels.chevron right of the bar (EconomyBarRenderer): folds it to the compact row."""
    return (bar[2] + 1, 1, bar[2] + 1 + CHEVRON, 1 + CHEVRON)


def stall_row(sw, bar):
    """The 'Build speed N%' strip under the bar: centred on the screen, ~200 px wide (EconomyBarRenderer)."""
    return (sw // 2 - 100, bar[3], sw // 2 + 100, bar[3] + 12)


# ---- PlayerPanelClientEvents.java (the stats table and its collapsed strip) --------------------------------------
PP_W, PP_ROW_H, PP_RIGHT_MARGIN, PP_PAD, PP_TAB_H = STATS_PANEL_W, STATS_ROW_H, STATS_RIGHT_MARGIN, 3, 12


def player_panel(sw, sh, r, bar, bar_bottom, rows):
    """The open stats table: a tab row, then section headers and lines, clipped above the minimap ("+N more")."""
    x = sw - PP_RIGHT_MARGIN - PP_W
    y = stats_top(bar[0], bar[2], bar_bottom, x, PP_W)
    full_h = PP_PAD * 2 + PP_TAB_H + rows * PP_ROW_H
    map_top = sh - r * 2 - MAP_CORNER - MAP_BG - 2        # diamond maths in both modes, as the Java does
    shown = rows
    if y + full_h > map_top:
        shown = max(0, int((map_top - y - PP_PAD * 2 - PP_TAB_H) / PP_ROW_H) - 1)   # Java truncates toward zero
        shown = min(shown, rows)
    more = shown < rows
    h = PP_PAD * 2 + PP_TAB_H + (shown + (1 if more else 0)) * PP_ROW_H
    return (x, y, x + PP_W, y + h)


def stats_strip(sw, bar, bar_bottom):
    """
    The collapsed strip at its widest (its width follows its text, its right edge is fixed). A narrower one that no
    longer shares x with the bar sits at STATS_TOP right of the bar: stats_strip_narrow covers that case.
    """
    x = sw - STATS_RIGHT_MARGIN - STATS_STRIP_W_MAX
    y = stats_top(bar[0], bar[2], bar_bottom, x, STATS_STRIP_W_MAX)
    return (x, y, x + STATS_STRIP_W_MAX, y + STATS_STRIP_H)


def stats_strip_narrow(sw, bar):
    right = sw - STATS_RIGHT_MARGIN
    if bar[2] >= right:
        return None
    return (max(bar[2], right - STATS_STRIP_W_MAX), STATS_TOP, right, STATS_TOP + STATS_STRIP_H)


def observer_bar(sw, sh, r, square, teams=(8, 8)):
    """ObserverBarClientEvents: a row per team, wrapped at observer_per_row, bottom-left short of the minimap."""
    limit = command_panel_limit(sw, r, square)
    per_row = observer_per_row(limit)
    cell_w = observer_cell_w(limit, per_row)
    rows = sum(-(-t // per_row) for t in teams)
    h = observer_height(rows)
    return (OBS_X, sh - OBS_BOTTOM - h, OBS_X + cell_w * per_row, sh - OBS_BOTTOM)


# ---- the alert column (HudClientEvents.alertColumn + the toasts, banner and kill feed under it) ------------------
def alert_column(sw, bar, panel_left, queue_right, map_limit=10 ** 9):
    left = max(bar[0], queue_right + 4)
    right = bar[2]
    if panel_left - 4 < right:
        right = panel_left - 4
    if map_limit < right:
        right = map_limit
    if right - left < 100:
        left = 4
        right = min(panel_left - 4, map_limit)
    return left, right


def alert_stack(bar_bottom, under_queue, temp_msg, vote, toast):
    """
    The alert column: the red temp message, the resign-vote banner, then ONE status row (the newest toast; it is drawn
    over the economy bar's stall strip when that is up, so this is the worst case with a banner, where it cannot be).
    The kill feed is not in it any more: it is a small feed at the side (kill_feed).
    """
    alert_bottom = bar_bottom + 3
    if under_queue is not None:
        alert_bottom = max(alert_bottom, under_queue + 4)
    top = alert_bottom
    if temp_msg:
        alert_bottom += 14
    banner = 15 if vote else 0
    toast_h = banner + (STATUS_H if toast else 0) + (2 if (banner or toast) else 0)
    return top, alert_bottom + 2 + toast_h


def kill_feed(sw, sh, r, square, top, bottom_panel_top, lines):
    """KillFeedClientEvents: lines of 0.75x text, 10 px apart, right edge at the stats strip's, under the strip / table."""
    limit = min(bottom_panel_top, minimap_top(sh, r, square))
    n, y = 0, top
    while n < lines and y + FEED_LINE_H <= limit - 2:
        n += 1
        y += FEED_LINE_H
    if n == 0:
        return None
    x2 = sw - STATS_RIGHT_MARGIN
    return (x2 - FEED_MAX_W, top, x2, top + (n - 1) * FEED_LINE_H + round(12 * FEED_SCALE))


# ---- geometry helpers ---------------------------------------------------------------------------------------------
def overlaps(a, b):
    return a[0] < b[2] and b[0] < a[2] and a[1] < b[3] and b[1] < a[3]


def hits_diamond(rect, sw, sh, r):
    """L1 distance from the diamond's centre to the rect <= r + backing: the rect touches the map."""
    cx, cy = sw - MAP_CORNER - r, sh - MAP_CORNER - r
    px = min(max(cx, rect[0]), rect[2] - 1)
    py = min(max(cy, rect[1]), rect[3] - 1)
    return abs(cx - px) + abs(cy - py) <= r + MAP_BG


def tile(x, y):
    return (x, y, x + ICON_FRAME, y + ICON_FRAME)


def slim_tile(x, y):
    return (x, y, x + SLIM_TILE, y + SLIM_TILE)


# ---- one scene ------------------------------------------------------------------------------------------------------
def scene(sw, sh, r, square, selection):
    """Returns {name: rect} plus the minimap description; mirrors HudClientEvents.onDrawScreen's positions."""
    rects = {}
    ctrl_groups = 10
    bar = economy_bar(sw, ctrl_groups)
    rects['economy_bar'] = bar
    rects['economy_chevron'] = economy_chevron(bar)
    rects['stall_row'] = stall_row(sw, bar)
    bar_bottom = rects['stall_row'][3]          # EconomyBarRenderer.Layout.bottom() includes the warning row
    rects['control_groups'] = (0, 0, ctrl_groups * SLIM_TILE, SLIM_TILE)
    queue = (0, SLIM_TILE + SLIM_QUEUE_GAP, 5 * SLIM_TILE + 10, SLIM_TILE + SLIM_QUEUE_GAP + 2 * SLIM_TILE + 10)
    rects['production_queue'] = queue
    panel = player_panel(sw, sh, r, bar, bar_bottom, rows=18)
    rects['player_panel'] = panel
    bottom_panel_top = sh - PANEL_H
    col_l, col_r = alert_column(sw, bar, panel[0], queue[2], alert_column_map_limit(sw, sh, r, square, bar[3]))
    under_queue = queue[3] if col_l < queue[2] + 4 else None
    a_top, a_bottom = alert_stack(bar_bottom, under_queue, True, True, True)
    rects['alert_column'] = (col_l, a_top, col_r, a_bottom)
    # the side feed hangs under the open table (the worst case: it is the tallest thing in that corner)
    feed = kill_feed(sw, sh, r, square, panel[3] + 2, bottom_panel_top, FEED_MAX_LINES)
    if feed:
        rects['kill_feed'] = feed

    limit = command_panel_limit(sw, r, square)
    if selection == 'multi':
        n = buttons_per_row(limit)
        # the L-shaped backing: the orders column full height, the info part (icon block, type grid) as tall as it is
        rects['command_panel'] = (0, bottom_panel_top, HUD_START_X, sh)
        rects['info_panel'] = (HUD_START_X, sh - info_height_multi(), multi_panel_right(n), sh)
        rects['command_chevron'] = (2, bottom_panel_top - CHEVRON - 1, 2 + CHEVRON, bottom_panel_top - 1)
        hover_h = min(113, sh // 3 + 11)
        hx = max(0, min(strip_x(), limit - MAP_GAP - FULL_W))
        rects['hover_type_card'] = (hx, bottom_panel_top - hover_h - 6, hx + FULL_W, bottom_panel_top - 6)
    elif selection in ('single', 'single_items'):
        right = HUD_START_X + PORTRAIT_W + STATS_W + 10
        card_x = HUD_START_X + PORTRAIT_W + STATS_W + (SIDE_W + 4 + ITEMS_EXTRA_W if selection == 'single_items' else 14 - 10)
        card_w = unit_card_width(card_x, limit, COMPACT_W)
        info_top = sh - info_height_single_worst()
        if card_w >= UNIT_CARD_MIN_W:
            right = max(right, card_x + card_w + 5)
            card_right = card_x + card_w
            rects['unitcard_chevron'] = (card_right - CHEVRON, info_top - CHEVRON - 1, card_right, info_top - 1)
        rects['command_panel'] = (0, bottom_panel_top, HUD_START_X, sh)
        rects['info_panel'] = (HUD_START_X, info_top, right, sh)
        rects['command_chevron'] = (2, bottom_panel_top - CHEVRON - 1, 2 + CHEVRON, bottom_panel_top - 1)
    else:
        card_h = min(102, sh // 3)
        rects['hover_card'] = (HUD_START_X, sh - card_h - 4, HUD_START_X + FULL_W, sh - 4)
        rects['hover_building_card'] = (HUD_START_X, sh - BUILDING_CARD_H - 4, HUD_START_X + BUILDING_CARD_W, sh - 4)
        # nothing selected: the cheat-sheet sits in the command panel's corner, drawn only when it clears the map
        if CHEAT_SHEET_X + CHEAT_SHEET_MAX_W <= limit - MAP_GAP:
            rects['cheat_sheet'] = (CHEAT_SHEET_X, sh - CHEAT_SHEET_H - 4, CHEAT_SHEET_X + CHEAT_SHEET_MAX_W, sh - 4)

    # minimap: diamond (L1 test) or square (its bbox plus the button grid left of it)
    map_top = minimap_top(sh, r, square)
    if square:
        side = square_side(r)
        rects['minimap'] = (sw - MAP_CORNER - side - MAP_BG, map_top, sw - MAP_CORNER + MAP_BG, sh - MAP_CORNER + MAP_BG)
        grid_right = sw - MAP_CORNER - side - GRID_PAD
        r0y = sh - MAP_CORNER + 4 - ICON_FRAME      # gridBottom = map bottom + 4
        r2y = r0y - 2 * GRID_STRIDE
        rects['minimap_grid'] = (grid_right - ICON_FRAME - GRID_STRIDE * (GRID_COLS - 1), r2y - 4, grid_right, r0y + ICON_FRAME)
    else:
        rects['idle_worker'] = tile(*idle_worker_xy(sw, sh, r))

    step = right_column_step(map_top)
    names = ['help', 'chat', 'army', 'idle_labs'] + ([] if square else ['attack_warning'])
    for i, name in enumerate(names):
        rects['col_' + name] = (tile(sw - RIGHT_COL_X, right_column_y(i, step)) if i == 0 else
                                slim_tile(slim_column_x(sw), right_column_y(i, step)))
    rects['beacon'] = tile(sw - 56, 40)
    return rects


# everything that must stay apart from everything else; the minimap is handled by the diamond test. The hover
# cards are transient popups that sit over the HUD like a tooltip: they only have to stay off the minimap and on
# the screen (SOFT).
HARD = ['economy_bar', 'economy_chevron', 'stall_row', 'control_groups', 'production_queue', 'player_panel',
        'stats_strip', 'stats_strip_narrow', 'alert_column', 'kill_feed', 'command_panel', 'info_panel', 'command_chevron', 'unitcard_chevron', 'cheat_sheet',
        'cheat_button', 'observer_bar', 'minimap', 'minimap_grid', 'idle_worker',
        'col_help', 'col_chat', 'col_army', 'col_idle_labs', 'col_attack_warning', 'beacon']
# permanent elements the clearance test applies to (transient toasts / kill ticker / banners / hover cards are not)
PERMANENT = ['economy_bar', 'economy_chevron', 'stall_row', 'control_groups', 'production_queue', 'stats_strip',
             'stats_strip_narrow',
             'command_panel', 'info_panel', 'cheat_button', 'observer_bar', 'idle_worker', 'minimap', 'minimap_grid',
             'col_help', 'col_chat', 'col_army', 'col_idle_labs', 'col_attack_warning', 'beacon']
SOFT = ['hover_card', 'hover_type_card', 'hover_building_card']


def check(sw, sh, r, square, selection, mode=None, eco_compact=False):
    rects = scene(sw, sh, r, square, selection) if mode is None else declutter_scene(sw, sh, r, square, selection, mode, eco_compact)
    errors = []
    names = [n for n in HARD if n in rects]
    for i, a in enumerate(names):
        for b in names[i + 1:]:
            if {a, b} == {'stats_strip', 'stats_strip_narrow'}:
                continue    # the same strip at two widths, never both
            if overlaps(rects[a], rects[b]):
                errors.append(f'{a} {rects[a]} overlaps {b} {rects[b]}')
    soft = [n for n in SOFT if n in rects]
    if not square:
        for n in names + soft:
            if n in ('minimap', 'minimap_grid'):
                continue
            if hits_diamond(rects[n], sw, sh, r):
                errors.append(f'{n} {rects[n]} touches the diamond minimap (r={r})')
    else:
        for n in soft:
            for m in ('minimap', 'minimap_grid'):
                if overlaps(rects[n], rects[m]):
                    errors.append(f'{n} {rects[n]} overlaps {m} {rects[m]}')
    for n in names + soft:
        x1, y1, x2, y2 = rects[n]
        if x1 < 0 or y1 < 0 or x2 > sw or y2 > sh:
            errors.append(f'{n} {rects[n]} leaves the screen')
    return errors


# ---- the declutter scenes (design/hud.md, "Human-POV audit") -------------------------------------------------------
def declutter_scene(sw, sh, r, square, selection, mode, eco_compact):
    """
    'collapsed': the defaults - stats strip, kill ticker (one line), "?" cheat-sheet button, every helper showing.
    'clean':     clean view - economy bar, minimap (+ size toggle) and command card only.
    'spectator': the observer bar for 8v8, the strip, no cheat-sheet; the observer bar gives way to a selection.
    """
    rects = {}
    ctrl_groups = 10
    bar = economy_bar(sw, ctrl_groups if mode != 'clean' else 0, eco_compact)
    rects['economy_bar'] = bar
    rects['economy_chevron'] = economy_chevron(bar)
    rects['stall_row'] = stall_row(sw, bar)
    bar_bottom = rects['stall_row'][3]
    bottom_panel_top = sh - PANEL_H
    limit = command_panel_limit(sw, r, square)
    if mode != 'clean':
        rects['control_groups'] = (0, 0, ctrl_groups * SLIM_TILE, SLIM_TILE)
        queue = (0, SLIM_TILE + SLIM_QUEUE_GAP, 5 * SLIM_TILE + 10, SLIM_TILE + SLIM_QUEUE_GAP + 2 * SLIM_TILE + 10)
        rects['production_queue'] = queue
        strip = stats_strip(sw, bar, bar_bottom)
        rects['stats_strip'] = strip
        narrow = stats_strip_narrow(sw, bar)
        if narrow is not None and narrow != strip:
            rects['stats_strip_narrow'] = narrow
        col_l, col_r = alert_column(sw, bar, strip[0], queue[2], alert_column_map_limit(sw, sh, r, square, bar[3]))
        under_queue = queue[3] if col_l < queue[2] + 4 else None
        # worst case for overlaps: temp message, vote banner and a toast row; the ticker is one line at the side
        a_top, a_bottom = alert_stack(bar_bottom, under_queue, True, True, True)
        rects['alert_column'] = (col_l, a_top, col_r, a_bottom)
        feed = kill_feed(sw, sh, r, square, strip[3] + 2, bottom_panel_top, 1)
        if feed:
            rects['kill_feed'] = feed
    if selection == 'multi':
        n = buttons_per_row(limit)
        rects['command_panel'] = (0, bottom_panel_top, HUD_START_X, sh)
        rects['info_panel'] = (HUD_START_X, sh - info_height_multi(), multi_panel_right(n), sh)
        rects['command_chevron'] = (2, bottom_panel_top - CHEVRON - 1, 2 + CHEVRON, bottom_panel_top - 1)
    elif selection == 'single':
        right = HUD_START_X + PORTRAIT_W + STATS_W + 10
        card_x = HUD_START_X + PORTRAIT_W + STATS_W + 4
        card_w = unit_card_width(card_x, limit, COMPACT_W)
        info_top = sh - info_height_single_worst()
        if card_w >= UNIT_CARD_MIN_W:
            right = max(right, card_x + card_w + 5)
            rects['unitcard_chevron'] = (card_x + card_w - CHEVRON, info_top - CHEVRON - 1, card_x + card_w, info_top - 1)
        rects['command_panel'] = (0, bottom_panel_top, HUD_START_X, sh)
        rects['info_panel'] = (HUD_START_X, info_top, right, sh)
        rects['command_chevron'] = (2, bottom_panel_top - CHEVRON - 1, 2 + CHEVRON, bottom_panel_top - 1)
    elif mode == 'collapsed':
        rects['cheat_button'] = (CHEAT_SHEET_X, sh - HELP_BUTTON - 4, CHEAT_SHEET_X + HELP_BUTTON, sh - 4)
    elif mode == 'spectator':
        rects['observer_bar'] = observer_bar(sw, sh, r, square)

    map_top = minimap_top(sh, r, square)
    if square:
        side = square_side(r)
        rects['minimap'] = (sw - MAP_CORNER - side - MAP_BG, map_top, sw - MAP_CORNER + MAP_BG, sh - MAP_CORNER + MAP_BG)
        if mode != 'clean':
            grid_right = sw - MAP_CORNER - side - GRID_PAD
            r0y = sh - MAP_CORNER + 4 - ICON_FRAME
            r2y = r0y - 2 * GRID_STRIDE
            rects['minimap_grid'] = (grid_right - ICON_FRAME - GRID_STRIDE * (GRID_COLS - 1), r2y - 4, grid_right, r0y + ICON_FRAME)
    elif mode != 'clean':
        rects['idle_worker'] = tile(*idle_worker_xy(sw, sh, r))
    if mode != 'clean':
        step = right_column_step(map_top)
        names = ['help', 'chat', 'army', 'idle_labs'] + ([] if square else ['attack_warning'])
        for i, name in enumerate(names):
            rects['col_' + name] = (tile(sw - RIGHT_COL_X, right_column_y(i, step)) if i == 0 else
                                    slim_tile(slim_column_x(sw), right_column_y(i, step)))
        rects['beacon'] = tile(sw - 56, 40)
    return rects


def in_centre(rect, sw, sh, frac=0.70):
    """True when the rect enters the ellipse inscribed in the central frac x frac box (axis-scaled clamp test)."""
    cx, cy, a, b = sw / 2, sh / 2, sw * frac / 2, sh * frac / 2
    px = min(max(cx, rect[0]), rect[2])
    py = min(max(cy, rect[1]), rect[3])
    return ((px - cx) / a) ** 2 + ((py - cy) / b) ** 2 < 1.0


def diamond_in_centre(sw, sh, r, frac=0.70):
    """The diamond minimap (with its backing) against the same ellipse, sampled along its edges."""
    cx, cy = sw - MAP_CORNER - r, sh - MAP_CORNER - r
    R = r + MAP_BG
    ex, ey, a, b = sw / 2, sh / 2, sw * frac / 2, sh * frac / 2
    for i in range(400):
        t = i / 100.0
        k, f = int(t), t - int(t)
        corners = [(cx, cy - R), (cx + R, cy), (cx, cy + R), (cx - R, cy)]
        (x1, y1), (x2, y2) = corners[k], corners[(k + 1) % 4]
        x, y = x1 + (x2 - x1) * f, y1 + (y2 - y1) * f
        if ((x - ex) / a) ** 2 + ((y - ey) / b) ** 2 < 1.0:
            return True
    return False


def clearance(sw, sh, r, square, mode, eco_compact):
    rects = declutter_scene(sw, sh, r, square, 'none', mode, eco_compact)
    errors = []
    for n in PERMANENT:
        if n not in rects:
            continue
        if n == 'minimap' and not square:
            if diamond_in_centre(sw, sh, r):
                errors.append(f'minimap (diamond r={r}) enters the playfield centre')
            continue
        if in_centre(rects[n], sw, sh):
            errors.append(f'{n} {rects[n]} enters the playfield centre')
    return errors


def main_declutter():
    failures = scenes = 0
    for w, h in [(1920, 1080), (2560, 1440)]:
        for scale in (2, 3, 4):
            sw, sh = w // scale, h // scale
            for r in (60, 120):
                for square in (False, True):
                    if (r == 120 or square) and sw < 640:
                        continue
                    if r == 120 and square and sw < 700:
                        continue
                    for mode in ('collapsed', 'clean', 'spectator'):
                        for eco_compact in (False, True):
                            for selection in ('none', 'single', 'multi'):
                                scenes += 1
                                errs = check(sw, sh, r, square, selection, mode, eco_compact)
                                # the clearance promise: defaults at 1920x1080 scale 2 and 3 with the default map
                                if selection == 'none' and w == 1920 and scale in (2, 3) and r == 60 and not square:
                                    errs += clearance(sw, sh, r, square, mode, eco_compact)
                                if errs:
                                    failures += 1
                                    print('FAIL', f'{w}x{h} scale {scale} ({sw}x{sh}) map r={r} '
                                          f'{"square" if square else "diamond"} {mode} eco={"compact" if eco_compact else "full"} {selection}')
                                    for e in errs:
                                        print('     ', e)
    print(f'{scenes - failures}/{scenes} declutter scenes clean')
    return failures


def main():
    screens = [(1920, 1080), (2560, 1440)]
    failures = 0
    scenes = 0
    for w, h in screens:
        for scale in (2, 3, 4):
            sw, sh = w // scale, h // scale
            for r in (60, 120):
                for square in (False, True):
                    if (r == 120 or square) and sw < 640:
                        continue    # documented limit: no room for a large / square map at 480x270
                    if r == 120 and square and sw < 700:
                        continue    # nor for the large square map plus its 5-column grid at 640 wide
                    for selection in ('none', 'single', 'single_items', 'multi'):
                        scenes += 1
                        errs = check(sw, sh, r, square, selection)
                        tag = f'{w}x{h} scale {scale} ({sw}x{sh}) map r={r} {"square" if square else "diamond"} {selection}'
                        if errs:
                            failures += 1
                            print('FAIL', tag)
                            for e in errs:
                                print('     ', e)
    print(f'{scenes - failures}/{scenes} scenes clean')
    failures += main_declutter()
    return 1 if failures else 0


if __name__ == '__main__':
    sys.exit(main())
