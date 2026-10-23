#!/usr/bin/env python3
"""
HUD footprint audit: every HUD element at GUI scale 3 on 1080p (a 640x360 logical screen, the default 60 px diamond
map, the default collapsed panels), its size and its share of the screen, before and after a change. The sizes come
from tools/hud_layout.py's model of the Java (so a number here is the number the layout checker asserts), plus the few
elements that model leaves out (the hover card, the chat feed), whose rules are written down below.

  python3 tools/hud_audit.py                    # the current tools/hud_layout.py on its own
  python3 tools/hud_audit.py --old /tmp/old_hud_layout.py   # before (that file) vs after (this checkout), markdown

Get the "before" with:  git show origin/main:tools/hud_layout.py > /tmp/old_hud_layout.py

Typical case (a single unit with 2 abilities, 5 control groups in use, 3 queued lab items): the worst case the
checker draws is larger. "Always" = on screen the whole match; "selection" = while something is selected;
"on demand" = only while it has something to say. Elements overlap nowhere (the checker proves that), so areas add.
"""
import argparse
import importlib.util
import os
import sys

SW, SH, R = 640, 360, 60          # GUI scale 3 on 1920x1080, the default diamond map
GROUPS = 5                        # control groups in use
ABILITIES = 2                     # a typical unit's ability rows on its card
CHAT_WIDTH, CHAT_LINE = 320, 9    # vanilla's default chat width and line height, chat scale 100%
CHAT_LINES_BEFORE, CHAT_LINES_AFTER, CHAT_SCALE_AFTER = 4, 2, 0.75   # ForgeGuiMixin


def load(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def size(rect):
    return rect[2] - rect[0], rect[3] - rect[1]


def row(label, when, rect_or_wh):
    w, h = rect_or_wh if len(rect_or_wh) == 2 else size(rect_or_wh)
    return label, when, (w, h), w * h


def elements(m):
    """[(label, when, (w, h), area)] for one version of the layout model."""
    new = hasattr(m, 'INFO_PAD_TOP')               # the L-shaped command panel and the slim info block
    slim = hasattr(m, 'SLIM_TILE')                 # slim 18 px tiles, one status line, the side feed
    tile = m.SLIM_TILE if slim else m.ICON_FRAME
    rows = []
    bar = m.economy_bar(SW, GROUPS)
    rows.append(row('Economy bar (metal / pop / energy)', 'always', bar))
    rows.append(row('Minimap (diamond backing)', 'always', (m.MAP_BG * 2 + R * 2, m.MAP_BG * 2 + R * 2)))
    rows.append(row('Control groups (%d in use)' % GROUPS, 'always', (GROUPS * tile, tile)))
    rows.append(row('Stats strip (Allies / Enemies)', 'always', (m.STATS_STRIP_W_MAX, m.STATS_STRIP_H)))
    # the helper column under the help button: tile sizes only (the air between them is not counted)
    rows.append(row('Helper buttons: chat, army', 'always', (tile, tile * 2)))
    # transient / on demand
    rects = m.declutter_scene(SW, SH, R, False, 'none', 'collapsed', False)
    rows.append(row('Production queue (3 queued)', 'on demand', (3 * tile + 10, tile + 10)))
    rows.append(row('Production queue (10 queued, as in the 18-12-12 clip)', 'on demand', (5 * tile + 10, 2 * tile + 10)))
    rows.append(row('Stall / waste / overflow row', 'on demand', size(rects['stall_row'])))
    # before: temp message, banner, three stacked toasts and the kill ticker in one column; after: the same column with
    # ONE toast row (it takes the stall strip's row when that is up), and the ticker is a small feed at the side
    # (the column's width is what it may use, which moves with the queue beside it: 200 px, the stall strip's, for both)
    rows.append(row('Alert column: banner + toasts' + ('' if slim else ' + ticker'), 'on demand',
                    (200, size(rects['alert_column'])[1])))
    rows.append(row('Kill feed ticker (side, 0.75x)', 'on demand',
                    size(rects['kill_feed']) if 'kill_feed' in rects else (0, 0)))
    if new:
        chat = (round(CHAT_WIDTH * CHAT_SCALE_AFTER), round(CHAT_LINES_AFTER * CHAT_LINE * CHAT_SCALE_AFTER))
    else:
        chat = (CHAT_WIDTH, CHAT_LINES_BEFORE * CHAT_LINE)
    rows.append(row('Announcements (chat feed)', 'on demand', chat))
    rows.append(row('Helper buttons: idle labs, attack, idle worker, beacon', 'on demand',
                    (m.ICON_FRAME, tile * 2 + m.ICON_FRAME * 2)))   # idle labs + attack (slim); idle worker + beacon (22 px)
    hover_h = 62 + 10 * ABILITIES if new else min(102, SH // 3)
    rows.append(row('Hover card (a unit under the cursor)', 'on demand', (m.FULL_W, hover_h)))
    rows.append(row('Controls cheat-sheet (opened with ?)', 'on demand', (m.CHEAT_SHEET_MAX_W, m.CHEAT_SHEET_H)))
    # the command panel: what a selection puts at the bottom-left
    limit = m.command_panel_limit(SW, R, False)
    card_x = m.HUD_START_X + m.PORTRAIT_W + m.STATS_W + 4
    card_w = m.unit_card_width(card_x, limit, m.COMPACT_W)
    right = max(m.HUD_START_X + m.PORTRAIT_W + m.STATS_W + 10, card_x + card_w + 5)
    if new:
        card_h = 26 if ABILITIES == 0 else 27 + 10 * ABILITIES
        info_h = max(m.PORTRAIT_H + m.INFO_PAD_TOP, card_h + 8)
        left = (m.HUD_START_X, m.PANEL_H)
        rows.append(row('Command panel, one unit: orders column', 'selection', left))
        rows.append(row('Command panel, one unit: info block + card', 'selection', (right - m.HUD_START_X, info_h)))
        n = m.buttons_per_row(limit)
        rows.append(row('Command panel, multi: orders column', 'selection', left))
        rows.append(row('Command panel, multi: info block + type grid', 'selection',
                        (m.multi_panel_right(n) - m.HUD_START_X, m.info_height_multi())))
    else:
        rows.append(row('Command panel, one unit: orders column', 'selection', (m.HUD_START_X, m.PANEL_H)))
        rows.append(row('Command panel, one unit: info block + card', 'selection', (right - m.HUD_START_X, m.PANEL_H)))
        n = m.buttons_per_row(limit)
        rows.append(row('Command panel, multi: orders column', 'selection', (m.HUD_START_X, m.PANEL_H)))
        rows.append(row('Command panel, multi: info block + type grid', 'selection',
                        (m.multi_panel_right(n) - m.HUD_START_X, m.PANEL_H)))
    return rows


def pct(area):
    return 100.0 * area / (SW * SH)


def totals(rows):
    always = sum(r[3] for r in rows if r[1] == 'always')
    one = always + sum(r[3] for r in rows if r[0].startswith('Command panel, one unit'))
    multi = always + sum(r[3] for r in rows if r[0].startswith('Command panel, multi'))
    return always, one, multi


def report(old, new):
    out = ['| Element | When | Before (px) | Before | After (px) | After | Saved |', '|---|---|---|---|---|---|---|']
    for (lo, when, (ow, oh), oa), (ln, _, (nw, nh), na) in zip(old, new):
        saved = '' if oa == na else ('moved' if oa == 0 else '%.0f%%' % (100.0 * (oa - na) / oa))
        out.append('| %s | %s | %dx%d | %.1f%% | %dx%d | %.1f%% | %s |' % (ln, when, ow, oh, pct(oa), nw, nh, pct(na), saved))
    ot, nt = totals(old), totals(new)
    out.append('')
    out.append('| Total HUD footprint | Before | After | Saved |')
    out.append('|---|---|---|---|')
    for label, o, n in zip(('Nothing selected (always-on elements)', 'One unit selected', 'Multi-selection'), ot, nt):
        out.append('| %s | %.1f%% | %.1f%% | %.0f%% |' % (label, pct(o), pct(n), 100.0 * (o - n) / o))
    return '\n'.join(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--old', help='an older tools/hud_layout.py to compare against')
    args = ap.parse_args()
    here = os.path.dirname(os.path.abspath(__file__))
    new = elements(load(os.path.join(here, 'hud_layout.py'), 'hud_layout_new'))
    old = elements(load(args.old, 'hud_layout_old')) if args.old else new
    print('GUI scale 3, 1920x1080 (%dx%d logical), diamond map r=%d, %d control groups, %d abilities on the card\n'
          % (SW, SH, R, GROUPS, ABILITIES))
    print(report(old, new))
    return 0


if __name__ == '__main__':
    sys.exit(main())
