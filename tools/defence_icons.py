#!/usr/bin/env python3
"""16x16 build-button icons for the static defences (design/defences.md), called by tools/gen_hud_icons.py: per faction
a wall, a gate, two traps, the anti-raid tower and the anti-T3 emplacement, each drawn as the same silhouette in the
faction's colours (one shape per job across all seven factions - BAR readability - and the colours say whose).
Writes textures/icons/buildings/<building id>.png. Deterministic."""

# (stone, dark accent, bright accent) per faction suffix
COLOURS = {
    "villagers": ((200, 196, 186, 255), (120, 112, 100, 255), (232, 190, 60, 255)),
    "monsters": ((222, 216, 196, 255), (52, 48, 60, 255), (90, 200, 220, 255)),
    "piglins": ((120, 40, 44, 255), (54, 24, 28, 255), (240, 140, 40, 255)),
    "verdant": ((96, 150, 70, 255), (70, 52, 34, 255), (220, 140, 190, 255)),
    "tide": ((90, 170, 160, 255), (40, 80, 90, 255), (210, 180, 90, 255)),
    "storm": ((80, 170, 130, 255), (170, 40, 46, 255), (240, 200, 70, 255)),
    "brass": ((196, 140, 70, 255), (40, 36, 40, 255), (250, 150, 50, 255)),
}
TRAPS = {
    "villagers": ("spikes", "snare"), "monsters": ("spikes", "snare"), "piglins": ("spikes", "mine"),
    "verdant": ("snare", "spikes"), "tide": ("mine", "snare"), "storm": ("mine", "snare"), "brass": ("mine", "snare"),
}


def make(canvas, px, rect, outline, line, darker, lighter):
    """Returns {file name: image}; the drawing helpers come from gen_hud_icons so both files draw alike."""

    def wall(c):
        stone, dark, acc = c
        im = canvas()
        rect(im, 1, 6, 14, 14, stone)
        for x in range(1, 15, 3):   # battlements
            rect(im, x, 4, x + 1, 5, stone)
        for y in (8, 11):           # mortar courses
            line(im, 1, y, 14, y, darker(stone))
        for x in (4, 9):
            line(im, x, 6, x, 8, darker(stone)); line(im, x + 2, 9, x + 2, 11, darker(stone))
        outline(im, 1, 6, 14, 14, dark)
        px(im, 7, 13, acc); px(im, 8, 13, acc)
        return im

    def gate(c):
        stone, dark, acc = c
        im = canvas()
        rect(im, 0, 4, 15, 14, stone)
        rect(im, 4, 7, 11, 14, (30, 26, 24, 255))     # the opening
        for x in range(4, 12, 2):                     # portcullis bars
            line(im, x, 7, x, 13, acc)
        line(im, 4, 10, 11, 10, acc)
        rect(im, 5, 6, 10, 6, stone)                  # the arch
        outline(im, 0, 4, 15, 14, dark)
        for x in (0, 3, 12, 15):
            px(im, x, 3, stone)
        return im

    def tower(c):
        stone, dark, acc = c
        im = canvas()
        rect(im, 5, 5, 10, 14, stone)
        rect(im, 4, 3, 11, 5, lighter(stone, .15))
        px(im, 4, 2, stone); px(im, 7, 2, stone); px(im, 8, 2, stone); px(im, 11, 2, stone)
        outline(im, 5, 5, 10, 14, dark)
        line(im, 8, 4, 14, 1, acc)                    # the shot
        px(im, 14, 1, lighter(acc, .5))
        rect(im, 7, 8, 8, 10, dark)                   # arrow slit
        return im

    def anti_t3(c):
        stone, dark, acc = c
        im = canvas()
        rect(im, 1, 10, 14, 14, stone)
        outline(im, 1, 10, 14, 14, dark)
        rect(im, 5, 6, 10, 9, darker(stone, .85))
        outline(im, 5, 6, 10, 9, dark)
        line(im, 2, 5, 13, 5, acc)                    # the ballista arms
        line(im, 2, 5, 7, 8, dark); line(im, 13, 5, 8, 8, dark)
        line(im, 7, 7, 7, 0, lighter(acc, .4))        # the bolt, pointing up at something big
        line(im, 8, 7, 8, 0, acc)
        px(im, 6, 1, acc); px(im, 9, 1, acc)
        return im

    def heavy_tower(c):
        """The raid tower, broader, with an accent band and two shots: the enhanced twin."""
        stone, dark, acc = c
        im = canvas()
        rect(im, 4, 5, 11, 14, stone)
        rect(im, 3, 3, 12, 5, lighter(stone, .15))
        for x in (3, 6, 9, 12):
            px(im, x, 2, stone)
        rect(im, 4, 9, 11, 10, acc)                   # the band
        outline(im, 4, 5, 11, 14, dark)
        line(im, 7, 4, 13, 0, acc); line(im, 9, 4, 15, 1, acc)
        rect(im, 6, 7, 6, 8, dark); rect(im, 9, 7, 9, 8, dark)
        return im

    def sky_watch(c, heavy=False):
        """A slim tower with the shot going straight up - points at the sky, reads as anti-air."""
        stone, dark, acc = c
        im = canvas()
        top = 4 if heavy else 6
        rect(im, 5, top, 10, 14, stone)
        rect(im, 4, top - 1, 11, top, lighter(stone, .15))
        if heavy:
            rect(im, 5, 9, 10, 10, acc)
            px(im, 4, top - 2, stone); px(im, 11, top - 2, stone)
        outline(im, 5, top, 10, 14, dark)
        line(im, 7, top - 2, 7, 0, lighter(acc, .4))  # the shot, straight up
        line(im, 8, top - 2, 8, 0, acc)
        if heavy:
            line(im, 10, top - 2, 13, 0, acc)
        # a small flyer being hit
        px(im, 12, 2, dark); px(im, 13, 1, dark); px(im, 11, 1, dark)
        return im

    def flak(c):
        """A low battery with four barrels up and a burst in the sky."""
        stone, dark, acc = c
        im = canvas()
        rect(im, 1, 10, 14, 14, stone)
        outline(im, 1, 10, 14, 14, dark)
        for x in (4, 6, 9, 11):
            line(im, x, 9, x, 5, dark)
        burst = lighter(acc, .5)
        for dx, dy in ((0, 0), (-1, 0), (1, 0), (0, -1), (0, 1), (-2, -2), (2, -2), (-2, 2), (2, 2)):
            px(im, 8 + dx, 2 + dy, burst if (dx, dy) == (0, 0) else acc)
        return im

    def trap(c, kind):
        stone, dark, acc = c
        im = canvas()
        ground = darker(stone, .55)
        rect(im, 1, 11, 14, 14, ground)
        for x, y in ((2, 12), (5, 13), (9, 12), (12, 13)):
            px(im, x, y, lighter(ground, .2))
        if kind == "spikes":
            for x in (3, 6, 9, 12):
                line(im, x, 11, x, 6, (200, 200, 210, 255))
                px(im, x, 5, (240, 240, 250, 255))
            line(im, 2, 10, 13, 10, dark)
        elif kind == "snare":
            for x in range(3, 13, 2):                  # a net
                line(im, x, 4, x + 2, 10, acc)
                line(im, x + 2, 4, x, 10, acc)
            line(im, 2, 4, 13, 4, dark)
        else:                                          # mine: a round charge with a fuse spark
            rect(im, 5, 7, 10, 11, dark)
            rect(im, 4, 8, 11, 10, dark)
            px(im, 6, 8, lighter(dark, .4))
            line(im, 10, 7, 12, 4, acc)
            px(im, 13, 3, (255, 240, 160, 255)); px(im, 12, 2, (255, 200, 80, 255)); px(im, 14, 4, (255, 200, 80, 255))
        return im

    def fortified(c):
        """The wall, with a bright band and studs: the fortified one."""
        im = wall(c)
        stone, dark, acc = c
        line(im, 1, 9, 14, 9, acc)
        for x in (3, 7, 11):
            px(im, x, 12, acc)
        return im

    def trap_enhanced(c, kind):
        """The trap with a bright frame round its patch: the enhanced one."""
        im = trap(c, kind)
        stone, dark, acc = c
        outline(im, 0, 10, 15, 15, acc)
        return im

    out = {}
    for s, c in COLOURS.items():
        out[f"wall_{s}.png"] = wall(c)
        out[f"wall_gate_{s}.png"] = gate(c)
        out[f"raid_tower_{s}.png"] = tower(c)
        out[f"anti_t3_{s}.png"] = anti_t3(c)
        out[f"raid_tower_heavy_{s}.png"] = heavy_tower(c)
        out[f"sky_watch_{s}.png"] = sky_watch(c)
        out[f"sky_watch_heavy_{s}.png"] = sky_watch(c, heavy=True)
        out[f"flak_{s}.png"] = flak(c)
        out[f"wall_fortified_{s}.png"] = fortified(c)
        for kind in TRAPS[s]:
            out[f"trap_{kind}_{s}.png"] = trap(c, kind)
            out[f"trap_{kind}_enhanced_{s}.png"] = trap_enhanced(c, kind)
    return out
