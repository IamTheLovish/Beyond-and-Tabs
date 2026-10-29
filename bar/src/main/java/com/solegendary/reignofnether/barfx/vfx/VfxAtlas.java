package com.solegendary.reignofnether.barfx.vfx;

/**
 * The tiles of {@code textures/vfx/vfx_atlas.png}, written by tools/gen_vfx_textures.py (a 4x4 grid of 512 px tiles,
 * the mask in alpha and premultiplied into RGB). One atlas so the whole effects pass binds one texture and batches into one buffer per
 * render type. The order here IS the generator's NAMES order - change both together (VfxDefTest checks the count and
 * the names the generator writes in its docstring are the same words).
 * Pure Java: no Minecraft classes (JUnit-tested).
 */
public final class VfxAtlas {
    private VfxAtlas() { }

    public static final int GRID = 4;
    public static final String[] NAMES = { "glow", "ring_glyph", "ring_thin", "sigil_star", "sigil_tiles", "sigil_oct",
            "beam", "smoke", "spark", "glint", "trail", "shockwave", "rune_disc", "ember", "heat", "empty" };

    public static final int GLOW = 0, RING_GLYPH = 1, RING_THIN = 2, SIGIL_STAR = 3, SIGIL_TILES = 4, SIGIL_OCT = 5,
            BEAM = 6, SMOKE = 7, SPARK = 8, GLINT = 9, TRAIL = 10, SHOCKWAVE = 11, RUNE_DISC = 12, EMBER = 13, HEAT = 14;

    /** A half-texel inset (of 2048) so bilinear filtering never reads the neighbouring tile. */
    static final float INSET = 0.5f / 2048f;

    /** Tile index by name, or -1. */
    public static int byName(String name) {
        if (name == null) return -1;
        for (int i = 0; i < NAMES.length; i++)
            if (NAMES[i].equals(name)) return i;
        return -1;
    }

    public static float u0(int tile) { return (tile % GRID) / (float) GRID + INSET; }
    public static float u1(int tile) { return (tile % GRID + 1) / (float) GRID - INSET; }
    public static float v0(int tile) { return (tile / GRID) / (float) GRID + INSET; }
    public static float v1(int tile) { return (tile / GRID + 1) / (float) GRID - INSET; }

    /** u at fraction f (0..1) across the tile. */
    public static float u(int tile, float f) { return u0(tile) + (u1(tile) - u0(tile)) * f; }

    /** v at fraction f (0..1) down the tile. */
    public static float v(int tile, float f) { return v0(tile) + (v1(tile) - v0(tile)) * f; }
}
