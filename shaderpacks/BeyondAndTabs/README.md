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

## Cloud shadows

Soft patches (median ~31 blocks across, about a quarter of the ground) drift downwind at 2 blocks/s. They multiply
the sun term only, down to 0.70 of it at the thickest; the sky's fill is untouched, so a unit under a cloud is a
little dimmer, never harder to read. Computed in ALU (a two-octave value noise), no texture. Option: `CLOUD_SHADOWS` (on).

## Texture sharpness in the RTS view (and the shimmer trade-off)

The RTS camera is orthographic at ~30-36 screen pixels per block, so a 128 px block texture puts ~4 texels on each
pixel and the GPU reads the atlas's mip 2: correct, but soft. `TEXTURE_SHARPNESS` (default 0.5) biases terrain's
albedo, normal and specular reads that many mip levels finer, only under the ortho camera; the atlas blends between
levels, so 0.5 is half way to mip 1. The price is shimmer: a finer level than the pixel grid can hold aliases, and
fine-grained textures (grass, gravel, sand) sparkle while the camera pans. 0.5 sharpens noticeably with mild shimmer
on a pan; 1.0 is a full level finer and shimmers clearly; 0 is the hardware's choice. The game's Mipmap Levels video
setting must be 2 or more for any of this to matter. No extra texture reads: the cost is the finer level's cache
traffic only.

Anisotropic filtering cannot be asked for from the pack: the shader-pack format (OptiFine / Iris, so Oculus) has no
directive for the block atlas's sampler state, and GLSL 1.20 has none either; it is the atlas's own setting, and
Embeddium offers no anisotropy option. It would add little here anyway: from the RTS tilt (55-75 degrees) open
ground is seen nearly face on (a pixel's footprint is at most ~1.2x longer one way than the other), and vanilla's
atlas reads nearest within a level, where anisotropy changes little.
