# RTS camera: BAR's Spring camera

lovish (experienced BAR player, 60% keyboard with no arrow keys): "our RTS camera feels weird and isn't as smooth
as Beyond All Reason; I want the same hotkey camera controls." We copy BAR's default camera, the Spring camera
(the engine's `SpringController` plus BAR's camera widgets), as closely as an orthographic Minecraft view allows.
Code: `orthoview/OrthoviewClientEvents` (camera), `keybinds/Keybindings` (keys),
`config/ReignOfNetherClientConfigs` + `config/CameraSettingsScreen` (sliders). Maths check:
`python3 tools/camera_math_check.py`.

## Rules

- **Shift never moves the camera.** In BAR Shift only queues orders. The old Shift controls (Shift+wheel tilt,
  Shift+WASD rotate/tilt, Shift at a screen edge orbiting) are gone, and so is the old "Shift-based angle control"
  preference. Rotation and tilt use Alt + middle-drag, Alt + pan keys and Ctrl + wheel.
- **The camera owns the wheel over the map.** A selection, a build ghost or a held modifier no longer takes it.
  Only a HUD scroll area (the custom-building commands list) keeps the wheel, and only while the cursor is over it.
- **One integrator.** All motion runs once per rendered frame on real frame time. Zoom (in log space), yaw and tilt
  are critically damped springs, solved exactly so 30 fps and 300 fps match. Panning is velocity-smoothed. Glides
  are a 2D spring on the camera position. Nothing allocates per frame.
- **Works the same on a remote multiplayer client.** All camera motion moves the client's own spectator entity, and
  the server accepts that movement from any client (`ServerGamePacketListenerImplMixin`). Only cuts (match start, a
  scripted fade, a minimap jump) still use the server teleport packet.

## Bindings

Every key below is a Forge `KeyMapping` in the Controls category **"Beyond and Tabs – Camera"**, so all of them can
be rebound. "Config key" means the setting in `reignofnether-client-*.toml`; the sliders are under Mod Settings >
Camera...

| Action | BAR default (Spring camera) | Our default | Binding / config key |
|---|---|---|---|
| Zoom toward the cursor (in), from the centre (out) | Mouse wheel | Mouse wheel | `camera_zoom_speed` (1-30, 10 = 15% per notch), `camera_zoom_out_from_cursor` (off) |
| Invert wheel | "invert zoom" toggle | off: wheel forward zooms in | `camera_zoom_invert` (off) |
| Zoom keys | PageUp / PageDown | PageUp / PageDown (numpad + / - also work) | `key.reignofnether.zoom_in`, `key.reignofnether.zoom_out` |
| Tilt | Ctrl + wheel | Ctrl + wheel (4 degrees per notch) | `camera_pitch_close`, `camera_pitch_far` |
| Tilt follows zoom | Spring tilts as you zoom | on: 55 degrees close up, 75 degrees zoomed out (gentle, linear in log zoom - Oct 12) | `camera_tilt_follows_zoom`, `camera_pitch_close_v2` (55), `camera_pitch_far_v2` (75) |
| Rotate + tilt by mouse | Alt + middle-drag | Alt + middle-drag (0.25 degrees/px, around the screen centre) | (none) |
| Rotate + tilt by keys | tilt modifier + arrows | Alt + WASD / arrows | `key.reignofnether.pan_*` |
| Pan by mouse | Middle-drag | Middle-drag, grab-the-map (at speed 10 the ground stays under the cursor) | `camera_mmb_speed` (10) |
| Invert drag | some drag the map, some drag the camera | off: drag grabs the map; on: drag moves the camera | `camera_mmb_invert` (off) |
| Pan by keys | Arrow keys | WASD (S tap = Stop, hold = pan; KeyTaps) and arrows | `key.reignofnether.pan_up/left/down/right`, `pan_plus_x/minus_x/plus_z/minus_z` |
| Pan speed (keys, scales with zoom) | n/a | 10 | `camera_sensitivity` (1-30) |
| Edge scroll | on | on, speeds up to full over 0.35 s | `camera_edge_scroll`, `camera_edge_speed` (10) |
| Smoothing | engine camera smoothing | 10 = settles in about 0.23 s, the pan reaches full speed in ~0.08 s; 0 = instant | `camera_smoothing` (0-30) |
| Camera shake | (screen shake widgets) | 100% = the small fixed nudge on big nearby blasts and building collapses; 0 = none. Only a multiplier on the output: what earns a shake (never per unit, never with army size) does not change | `camera_shake` (0-100) |
| Overview | Tab | Tab. Tab again, a left click on the map, or releasing a held Tab (over 250 ms) flies back in at the cursor | `key.reignofnether.overview` |
| Store / recall camera position | BAR camera-anchor widget: Ctrl+F1-F4 store, F1-F4 recall (from memory, not verified) | unbound: press = recall (glides), Ctrl + press = store, 4 slots | `key.reignofnether.camera_anchor_1..4` |
| Jump to last alert | Space | Space (now glides) | `key.reignofnether.last_alert` (RTS category) |
| Reset angle, zoom, tilt | (none) | Right Ctrl | `key.reignofnether.reset` |
| 90-degree rotate (minimap buttons) | (none) | Right / Left arrow, shown with Alt or the large map | `key.reignofnether.rot_cw`, `rot_ccw` |
| RTS camera / Minecraft view | Backspace (Spring's mouse-look toggle) | Backspace (F12 also works) | `key.reignofnether.toggle_camera` |
| Spectator free flight (watchers only: no side in a running match) | (spectators fly free in Spring) | Caps Lock: RTS view <-> vanilla spectator flight (noclip, no interaction); back to the RTS view the camera is focused on where the watcher flew to. Scroll sets the speed, 0.5x to 8x vanilla's, shown in the hint. The server refuses anyone holding a side (no scouting past the fog); watchers are never sent to the players | `key.reignofnether.toggle_free_fly` |
| Rotate building ghost | [ and ] | [ and ] (the wheel no longer rotates ghosts) | `key.reignofnether.rotate_building_ccw`, `rotate_building_cw` |
| Shift | queue orders (the engine's "move fast" scroll multiplier is not used by BAR players for the camera) | nothing for the camera | (none) |
| Cycle the selection's unit types | n/a | Ctrl + Tab (Ctrl + Shift + Tab goes backwards); the vanilla player list is also on Ctrl + Tab | `key.reignofnether.tab` (RTS category) |

Glides: Space (last alert), unit/building portraits, control-group double taps, idle factories, the overview and
anchors all glide. Server-driven moves (match start, scripted fades) and minimap clicks cut instantly.

The default angle is the diagonal the diamond minimap (and the 45-degree-rotated square minimap) is drawn for, so
in this mod that diagonal is "map north". Reset returns there, not to world north, because the minimap does not
rotate with the camera.

## The two bugs lovish hit

1. **"With a unit selected the mouse wheel doesn't zoom."** The wheel handler sent the wheel elsewhere whenever
   certain state was held. Shift, which a BAR player holds constantly with units selected to queue orders, turned
   the wheel into tilt. Ctrl turned it into yaw. Any build ghost (a constructor's build hotkey) turned it into ghost
   rotation, and the custom-building commands list took it whenever its flag was left on. Now the wheel always
   zooms (Ctrl + wheel tilts), the event is consumed so nothing else acts on it, ghosts rotate with [ and ], and the
   commands list only takes the wheel while the cursor is over it.
2. **"Shift + drag to place a line swings the camera side to side."** The cause was the edge-pan code: holding Shift
   with the cursor at a screen edge orbited the camera (left/right edges swung the yaw, top/bottom tilted). A
   Shift+drag line (buildings or a formation) that reached the edge of the screen therefore swung the view, and
   Shift+A/D swung it as well. Every Shift camera path has been removed.

## Maths

- View: orthographic, `zoom` = blocks of world in the view height. The ground point drawn at window position
  (ndcX, ndcY) on the plane y = h is `camera + viewOffset(ndc, yaw, pitch, zoom, aspect, camY, h)`.
- Zoom to cursor: capture P = the ground under the cursor (h = the real terrain height under the cursor) before the
  frame's zoom/tilt/yaw step. After the step, place the camera at `P - viewOffset(new state)`. P stays under the
  cursor even while tilt-follows-zoom changes the pitch.
- Rotation pivots the same way around the ground at the screen centre.
- Tilt: `pitch = base(zoom) + offset`, where base rises linearly with ln(zoom) from `camera_pitch_close` at
  zoom 10 to `camera_pitch_far` at zoom 135. The offset is clamped so the final pitch stays within 12-90 degrees.
- Spring: `e(t) = (e0 + (v0 + w*e0) t) e^(-w t)`, with w = 250 / camera_smoothing (10 gives 25/s: within 10% after
  0.15 s, within 2% after 0.23 s). Glides use 0.6 w.
