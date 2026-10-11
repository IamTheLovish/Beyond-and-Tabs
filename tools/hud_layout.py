#!/usr/bin/env python3
"""
HUD layout check: the rectangle maths of the RTS HUD, mirrored from the Java (no Minecraft needed), asserting that
the regions never overlap at GUI scale 2, 3 and 4 on 1920x1080 and 2560x1440.

Mirrors, function for function:
  hud/HudLayout.java              the shared constants and the panel / column / idle-worker maths
  hud/HudClientEvents.java        command panel, selection strip, hover card, alert column, right column, square grid
  hud/EconomyBarRenderer.java     the top bar's width and position
  hud/playerdisplay/PlayerPanelClientEvents.java   the player list (clipped above the minimap)
  hud/NotificationClientEvents.java, KillFeedClientEvents.java, player/ResignVoteClientEvents.java  the alert stack
  minimap/MinimapClientEvents.java  the diamond / square map geometry

Change a constant in the Java, change it here. Run: python3 tools/hud_layout.py (exit 1 on any overlap).
The worst case is modelled everywhere: ten control groups, a two-row production queue, a stall warning, a danger
banner, a resign vote, three toasts, five kill-feed lines, a 16-player roster, every helper button showing.

Not modelled on purpose (documented limits): the large (M) minimap and the square map at 480x270 (GUI scale 4 on
1080p) - a 240 px map on a 270 px screen leaves no room for anything, and lovish plays at scale 2 or 3.
"""
import math
import sys

# ---- HudLayout.java ---------------------------------------------------------------------------------------------
ICON_FRAME = 22
HUD_START_X = ICON_FRAME * 6 + ICON_FRAME // 2          # 143
PORTRAIT_W, PORTRAIT_H, STATS_W = 60, 60, 44
PANEL_H = max(PORTRAIT_H, ICON_FRAME * 4 + 6) + 8       # 102
MIN_PER_ROW, MAX_PER_ROW = 3, 8
MAP_CORNER, MAP_BG = 10, 6
SQUARE_SCALE = 1.2
MAP_GAP = 4
GRID_STRIDE, GRID_COLS, GRID_PAD = 28, 5, 12
RIGHT_COL_X = 28
RIGHT_COL_TOP = 40
RIGHT_COL_STEP, RIGHT_COL_STEP_TIGHT, RIGHT_COL_COUNT = 30, 24, 5
UNIT_CARD_MIN_W = 70
COMPACT_W, FULL_W = 124, 128                            # UnitInfoCard


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


def strip_x():
    return HUD_START_X + PORTRAIT_W + STATS_W + 14


def multi_panel_right(n):
    return HUD_START_X + PORTRAIT_W + STATS_W + ICON_FRAME * n + 24


def buttons_per_row(limit):
    n = (limit - MAP_GAP - multi_panel_right(0)) // ICON_FRAME
    return max(MIN_PER_ROW, min(MAX_PER_ROW, n))


def unit_card_width(card_x, limit, preferred):
    return min(preferred, limit - MAP_GAP - 5 - card_x)


def right_column_step(map_top):
    bottom = RIGHT_COL_TOP + RIGHT_COL_STEP * (RIGHT_COL_COUNT - 1) + ICON_FRAME
    return RIGHT_COL_STEP if bottom <= map_top - MAP_GAP else RIGHT_COL_STEP_TIGHT


def right_column_y(i, step):
    return RIGHT_COL_TOP + step * i


def idle_worker_xy(sw, sh, r):
    return minimap_left(sw, r, False) + 2, minimap_top(sh, r, False) + 2


# ---- EconomyBarRenderer.java ------------------------------------------------------------------------------------
ECO_PANEL_H, ECO_POP_W, ECO_W_MAX, ECO_W_MIN = 34, 58, 210, 130


def economy_bar(sw, ctrl_groups):
    panel_w = max(ECO_W_MIN, min(ECO_W_MAX, (sw - 200 - ECO_POP_W) // 2))
    total_w = panel_w * 2 + ECO_POP_W
    left = sw // 2 - total_w // 2
    reserved = ctrl_groups * ICON_FRAME + 4 if ctrl_groups else 0
    if left < reserved:
        left = reserved
        room = sw - 40 - left
        if total_w > room:
            panel_w = max(90, (room - ECO_POP_W) // 2)
            total_w = panel_w * 2 + ECO_POP_W
    return (left, 0, left + total_w, ECO_PANEL_H + 1)


def stall_row(sw, bar):
    """The 'Build speed N%' strip under the bar: centred on the screen, ~200 px wide (EconomyBarRenderer)."""
    return (sw // 2 - 100, bar[3], sw // 2 + 100, bar[3] + 12)


# ---- PlayerPanelClientEvents.java -------------------------------------------------------------------------------
PP_W, PP_ROW_H, PP_TOP, PP_RIGHT_MARGIN, PP_PAD = 206, 11, 38, 60, 3


def player_panel(sw, sh, r, bar, bar_bottom, lines, allies):
    x = sw - PP_RIGHT_MARGIN - PP_W
    y = PP_TOP
    if bar[2] > x and bar[0] < x + PP_W:
        y = max(PP_TOP, bar_bottom + 3)
    split = 0 < allies < lines
    full_h = PP_PAD * 2 + lines * PP_ROW_H + (3 if split else 0)
    map_top = sh - r * 2 - MAP_CORNER - MAP_BG - 2        # diamond maths in both modes, as the Java does
    shown = lines
    if y + full_h > map_top:
        shown = max(1, (map_top - y - PP_PAD * 2 - (3 if split else 0)) // PP_ROW_H - 1)
        shown = min(shown, lines)
    more = shown < lines
    h = PP_PAD * 2 + (shown + (1 if more else 0)) * PP_ROW_H + (3 if split and shown > allies else 0)
    return (x, y, x + PP_W, y + h)


# ---- the alert column (HudClientEvents.alertColumn + the toasts, banner and kill feed under it) ------------------
def alert_column(sw, bar, panel_left, queue_right):
    left = max(bar[0], queue_right + 4)
    right = bar[2]
    if panel_left - 4 < right:
        right = panel_left - 4
    if right - left < 100:
        left = 4
        right = panel_left - 4
    return left, right


def alert_stack(bar_bottom, under_queue, temp_msg, vote, toasts, kills, bottom_panel_top):
    alert_bottom = bar_bottom + 3
    if under_queue is not None:
        alert_bottom = max(alert_bottom, under_queue + 4)
    top = alert_bottom
    if temp_msg:
        alert_bottom += 14
    banner = 15 if vote else 0
    toast_h = banner + toasts * 13 + (2 if (banner or toasts) else 0)
    y = alert_bottom + 2 + toast_h
    bottom = y
    for _ in range(kills):
        if y + 10 > bottom_panel_top - 2:
            break
        bottom = y + 10
        y += 12
    return top, max(bottom, alert_bottom + 2 + toast_h)


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


# ---- one scene ------------------------------------------------------------------------------------------------------
def scene(sw, sh, r, square, selection):
    """Returns {name: rect} plus the minimap description; mirrors HudClientEvents.onDrawScreen's positions."""
    rects = {}
    ctrl_groups = 10
    bar = economy_bar(sw, ctrl_groups)
    rects['economy_bar'] = bar
    rects['stall_row'] = stall_row(sw, bar)
    bar_bottom = rects['stall_row'][3]          # EconomyBarRenderer.Layout.bottom() includes the warning row
    rects['control_groups'] = (0, 0, ctrl_groups * ICON_FRAME, ICON_FRAME)
    queue = (0, ICON_FRAME + 6, 5 * ICON_FRAME + 10, ICON_FRAME + 6 + 2 * ICON_FRAME + 10)
    rects['production_queue'] = queue
    panel = player_panel(sw, sh, r, bar, bar_bottom, lines=16, allies=8)
    rects['player_panel'] = panel
    bottom_panel_top = sh - PANEL_H
    col_l, col_r = alert_column(sw, bar, panel[0], queue[2])
    under_queue = queue[3] if col_l < queue[2] + 4 else None
    a_top, a_bottom = alert_stack(bar_bottom, under_queue, True, True, 3, 5, bottom_panel_top)
    rects['alert_column'] = (col_l, a_top, col_r, a_bottom)

    limit = command_panel_limit(sw, r, square)
    if selection == 'multi':
        n = buttons_per_row(limit)
        rects['command_panel'] = (0, bottom_panel_top, multi_panel_right(n), sh)
        hover_h = min(113, sh // 3 + 11)
        hx = max(0, min(strip_x(), limit - MAP_GAP - FULL_W))
        rects['hover_type_card'] = (hx, bottom_panel_top - hover_h - 6, hx + FULL_W, bottom_panel_top - 6)
    elif selection in ('single', 'single_items'):
        right = HUD_START_X + PORTRAIT_W + STATS_W + 10
        card_x = HUD_START_X + PORTRAIT_W + STATS_W + (STATS_W + 4 if selection == 'single_items' else 14 - 10)
        card_w = unit_card_width(card_x, limit, COMPACT_W)
        if card_w >= UNIT_CARD_MIN_W:
            right = max(right, card_x + card_w + 5)
        rects['command_panel'] = (0, bottom_panel_top, right, sh)
    else:
        card_h = min(102, sh // 3)
        rects['hover_card'] = (HUD_START_X, sh - card_h - 4, HUD_START_X + FULL_W, sh - 4)

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
        rects['col_' + name] = tile(sw - RIGHT_COL_X, right_column_y(i, step))
    rects['beacon'] = tile(sw - 56, 40)
    return rects


# everything that must stay apart from everything else; the minimap is handled by the diamond test. The hover
# cards are transient popups that sit over the HUD like a tooltip: they only have to stay off the minimap and on
# the screen (SOFT).
HARD = ['economy_bar', 'stall_row', 'control_groups', 'production_queue', 'player_panel', 'alert_column',
        'command_panel', 'minimap', 'minimap_grid', 'idle_worker',
        'col_help', 'col_chat', 'col_army', 'col_idle_labs', 'col_attack_warning', 'beacon']
SOFT = ['hover_card', 'hover_type_card']


def check(sw, sh, r, square, selection):
    rects = scene(sw, sh, r, square, selection)
    errors = []
    names = [n for n in HARD if n in rects]
    for i, a in enumerate(names):
        for b in names[i + 1:]:
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
    return 1 if failures else 0


if __name__ == '__main__':
    sys.exit(main())
