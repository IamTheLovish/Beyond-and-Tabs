"""16x16 HUD icons for the Solar Collector and the Advanced Solar (claude/solar-spec.md). Called from
gen_hud_icons.py's main with its drawing helpers: make(canvas, px, rect, outline, line, darker, lighter)."""


def make(canvas, px, rect, outline, line, darker, lighter):
    def panel(big):
        im = canvas()
        frame = (176, 112, 70, 255)      # copper
        glass = (70, 130, 210, 255)
        shine = (160, 210, 255, 255)
        rect(im, 1, 11, 14, 14, darker(frame, .7))          # plinth
        rect(im, 2, 5 if big else 6, 13, 10, frame)         # panel frame
        rect(im, 3, 6 if big else 7, 12, 9, glass)
        for x in (5, 8, 11):                                # cell dividers
            line(im, x, 6 if big else 7, x, 9, darker(glass, .75))
        line(im, 3, 7 if big else 8, 12, 7 if big else 8, darker(glass, .75))
        px(im, 4, 6 if big else 7, shine); px(im, 5, 6 if big else 7, shine)
        rect(im, 7, 2 if big else 3, 8, 4 if big else 5, (170, 176, 186, 255))   # mast
        px(im, 7, 1 if big else 2, (250, 220, 90, 255)); px(im, 8, 1 if big else 2, (250, 220, 90, 255))
        if big:                                              # a second, raised row: the advanced one
            rect(im, 4, 3, 11, 4, glass); line(im, 4, 3, 11, 3, shine)
        return im
    return {"solar_collector.png": panel(False), "advanced_solar.png": panel(True)}
