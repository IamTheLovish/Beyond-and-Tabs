# Charnel Works (Gravebound T1 vehicle lab) - expected numbers before each round's stills land

The r12 method writes these before the eyes stills are photographed. The measured numbers come from
`tools/camera_colour.py` (the table) and `tools/test_gravebound_lab.py`, and are filled in when the stills land.
Bands are from the designer pass (itachi2-api run 20261030-211330-gravebound-r1-design).

## Detail round 1 (be36aada; eyes 41157506389, control gravebound_l3_round1)
| Item | Band | Expected | Measured |
|---|---|---|---|
| Landmark lead (spire 25 over the next outside its zone) | 4-8 | 6 (rib apex 19; flank needles 17) | 5 (the rib caps reach 20) |
| Gate seen into (34, step 2, >= 3 deep) | >= 200 | ~250 (7-wide passage) | 313 |
| Gate frame vs wall field (rts_close / 34) | frame 0.45-0.80, gap >= 0.20 | frame ~0.55, wall ~0.20 | frame 0.452 / 0.473 (the dark arch head is in the frame), wall 0.189 / 0.186 |
| Wall field west / south | 0.15-0.24 / 0.22-0.32 | 0.19 / 0.26 (bricks, blackstone posts, tuff feet) | 0.189 / 0.255 (rts_close), 0.186 / 0.253 (34) |
| Roofs from above | 0.16-0.24 | 0.20 (Macaw's deepslate) | 0.215 |
| Heart green share, rts / 34 | 1.5-4 % / <= 4 % | ~2 % / ~3.3 % (lime glass ~ the pane: hue 88, sat 0.33-0.56, val 0.31-0.35) | 1.9 % / 3.3 % |
| Lime glass block, west face | - | hue 85-95, sat 0.30-0.50, val 0.30-0.40 | hue 83, sat 0.36, val 0.53 (brighter than the pane) |
| Smoke glass block | - | val 0.27-0.32, sat <= 0.06 (as the pane) | 0.17 west / 0.20 south / 0.25 up, sat 0.06 (darker than the pane) |
| Snow share (bone tops capped) | <= 5 % | < 1 % | 0.0 % |
| Light share (val >= 0.50) | 6-25 % | ~11 % | 11.7 % rts / 17.6 % 34 |
| Dusk | heart the single source | the heart and lancets glow green-white; no cyan | see the critics |

Tests: 19 / 19 green locally on the camera table with this run's blocks added (--add-only).

## Detail round 2 (control: detail round 1 = gravebound_l3_d1.nbt, in the same eyes run)
Critics 20261030-221323 (a) / -221335 (b): 5.5 / 5 vs the control's 4 / 4. One change per element:
| Element | Change | Expected | Measured |
|---|---|---|---|
| Heart | bone ring + bone inner corners -> polished blackstone ring, full glass disc (L1 3 x 3) | no plus / cross; the lime (0.53) the brightest point of the front; green 2.0 % rts / 3.4 % 34 | |
| Ribs | dark slab caps removed, one continuous arc, odd span (x 9..15) | read as three arcs, not chevrons; snow 3.4 % rts (bone tops 0.839), under 5 % | |
| Base | tuff feet -> cobbled deepslate, paving polished deepslate -> deepslate tiles, lane -> polished blackstone | feet 0.19-0.26 (at or under the walls), lane up ~0.21, the throat the darkest floor | |
| Flank caps | 5 x 5 hipped caps -> a blackstone parapet round a 3 x 3 cap, needle 16 | from above only the spire's cap is a big pyramid; lead 6 (rib apex 19) | |
