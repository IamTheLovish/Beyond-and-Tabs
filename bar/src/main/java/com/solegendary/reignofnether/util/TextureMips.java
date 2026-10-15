package com.solegendary.reignofnether.util;

/**
 * The mip filter for our generated unit atlases (client: unit/modelling/renderers/MipmappedTextures; spec:
 * claude/unit-mipmaps.md). Pure arithmetic on packed 32-bit pixels with alpha in the top byte - the three colour bytes
 * are treated alike, so it works on NativeImage's ABGR ints and on ImageIO's ARGB ints - and no client classes, so the
 * headless GameTests can check it.
 *
 * Why it exists: vanilla gives entity textures no mipmaps. Our bodies are painted 8 texels to the model pixel, so from
 * the RTS camera one screen pixel covers many texels and nearest sampling picks a different one every frame - the
 * units shimmer. Minecraft's blocks don't, because the block atlas is mipmapped.
 */
public final class TextureMips {
    private TextureMips() { }

    /**
     * At most three levels below the painted one: the generated atlases (tools/dsl_to_game.py) pack every island at
     * whole model pixels with density 8, so each 8x8 texel block belongs to one island and levels 1-3 never mix two
     * islands. A fourth level would average 2x2 model pixels across island borders.
     */
    public static final int MAX_LEVELS = 3;

    private static final float[] TO_LINEAR = new float[256];
    private static final int LINEAR_STEPS = 4095;
    private static final int[] TO_SRGB = new int[LINEAR_STEPS + 1];

    static {
        for (int i = 0; i < 256; i++) {
            double c = i / 255.0;
            TO_LINEAR[i] = (float) (c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4));
        }
        for (int j = 0; j <= LINEAR_STEPS; j++) {
            double l = j / (double) LINEAR_STEPS;
            double s = l <= 0.0031308 ? 12.92 * l : 1.055 * Math.pow(l, 1 / 2.4) - 0.055;
            TO_SRGB[j] = (int) Math.round(Math.max(0, Math.min(1, s)) * 255);
        }
    }

    /**
     * How many levels below level 0 a w x h texture gets, up to {@code wanted}: each level halves both sides exactly
     * (an odd side would shift texels between islands).
     */
    public static int levels(int w, int h, int wanted) {
        int l = 0;
        while (l < wanted && ((w >> l) & 1) == 0 && ((h >> l) & 1) == 0 && (w >> (l + 1)) > 0 && (h >> (l + 1)) > 0)
            l++;
        return l;
    }

    /**
     * One level down: each texel is its 2x2 block averaged in linear light (a black and a white texel make sRGB 188,
     * not the too-dark 128), colours weighted by alpha so empty texels lend no colour, alpha the plain mean. A block
     * with no alpha at all stays fully empty (0) - our glow is drawn additively, so empty must stay black.
     */
    public static int[] halve(int[] src, int w, int h) {
        int w2 = Math.max(1, w / 2), h2 = Math.max(1, h / 2);
        int[] out = new int[w2 * h2];
        for (int y = 0; y < h2; y++) {
            for (int x = 0; x < w2; x++) {
                float a = 0, c0 = 0, c1 = 0, c2 = 0;
                for (int dy = 0; dy < 2; dy++) {
                    int sy = Math.min(h - 1, 2 * y + dy);
                    for (int dx = 0; dx < 2; dx++) {
                        int p = src[sy * w + Math.min(w - 1, 2 * x + dx)];
                        int al = p >>> 24;
                        if (al == 0)
                            continue;
                        a += al;
                        c0 += al * TO_LINEAR[p & 0xFF];
                        c1 += al * TO_LINEAR[(p >>> 8) & 0xFF];
                        c2 += al * TO_LINEAR[(p >>> 16) & 0xFF];
                    }
                }
                if (a == 0) {
                    out[y * w2 + x] = 0;
                    continue;
                }
                int alpha = Math.round(a / 4f);
                out[y * w2 + x] = (alpha << 24) | (srgb(c2 / a) << 16) | (srgb(c1 / a) << 8) | srgb(c0 / a);
            }
        }
        return out;
    }

    private static int srgb(float linear) {
        return TO_SRGB[Math.round(Math.max(0f, Math.min(1f, linear)) * LINEAR_STEPS)];
    }

    /**
     * The invariant that makes levels 1..log2(size) exact for an atlas: every size x size block (aligned to the
     * texture's origin) is all empty or all non-empty. Returns the first offending block as "x,y" in texels, or null.
     */
    public static String firstMixedBlock(int[] px, int w, int h, int size) {
        for (int by = 0; by < h; by += size) {
            for (int bx = 0; bx < w; bx += size) {
                boolean empty = false, filled = false;
                for (int y = by; y < Math.min(h, by + size); y++)
                    for (int x = bx; x < Math.min(w, bx + size); x++) {
                        if ((px[y * w + x] >>> 24) == 0)
                            empty = true;
                        else
                            filled = true;
                    }
                if (empty && filled)
                    return bx + "," + by;
            }
        }
        return null;
    }
}
