# Beyond and Tabs shader pack

Our own shader pack for Beyond and Tabs, loaded by Oculus (Iris for Forge). Written for the RTS camera first: its sun
shadow map is fitted to what the top-down view shows, its haze measures distance the way the game's own look does, and
its grade is the game's Enchanted look, so players with and without the pack see the same mood.

**Original work, written clean-room: not derived from any other shader pack.** Sources: the public documentation of
the OptiFine / Iris shader-pack format, general graphics knowledge, and the game's own code. Licence: GPL v2 or later,
the same as the game (`LICENSE`).

Slices and how each is measured: `design/own_shader_pack.md` in the game's repository.

## Wind

Plants sway from the foot up and leaves shimmer in the mod's own wind field (`environment/wind/WindField.java`, the
one the tree limbs move in): two gust waves travel across the map along a slowly turning heading, so from the RTS
camera the wind is seen rolling over the grass. Amplitudes are in blocks (a tuft's top ~0.12 at a full gust, a leaf
~0.035). The shadow pass moves leaves by the same function, so their shadows stay attached; plants cast no shadow.
Options: `WIND` (on), `WIND_STRENGTH` (1.0).
