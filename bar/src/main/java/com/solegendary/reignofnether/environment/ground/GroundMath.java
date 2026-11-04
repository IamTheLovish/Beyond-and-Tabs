package com.solegendary.reignofnether.environment.ground;

/**
 * The arithmetic the ground rules share (GroundSheetRules, CloverLayerRules, FlowerLayerRules): the position hash and
 * the floating layers' up quad. No Minecraft types, so the rules' JUnit tests reach it.
 */
final class GroundMath {

    /** Odd 64-bit multipliers (the golden ratio's and murmur3's), one per coordinate, so x, y and z never cancel. */
    static final long KX = 0x9E3779B97F4A7C15L, KZ = 0xC2B2AE3D27D4EB4FL, KY = 0x165667B19E3779F9L;

    private GroundMath() {
    }

    /** murmur3's 64-bit finaliser: every input bit reaches every output bit, so a salt XORed into the input gives an
     * independent stream (the clover, the flowers and the sheet variants each have their own). */
    static long mix(long h) {
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h;
    }

    /** A well-mixed hash of a column (x, z) under a salt. */
    static long hash(long x, long z, long salt) {
        return mix(x * KX ^ z * KZ ^ salt);
    }

    /** A well-mixed hash of a block (x, y, z) under a salt. */
    static long hash(long x, long y, long z, long salt) {
        return mix(x * KX ^ z * KZ ^ y * KY ^ salt);
    }

    /** Bits [shift, shift + 16) of h as a fraction in [0, 1). */
    static double unit16(long h, int shift) {
        return ((h >>> shift) & 0xFFFF) / 65536.0;
    }

    /**
     * A quad over the whole block at the given height in the BLOCK vertex format (position, colour, uv, light, normal:
     * 8 ints a vertex), in vanilla's up-face corner order (x0 z0, x0 z1, x1 z1, x1 z0: front face from above), the
     * sprite [u0, u1] x [v0, v1] turned by rotation quarter turns (corner i shows the sprite corner that corner
     * i + rotation shows unturned; 0 = u along +x, v along +z). White (a tint colours it, or nothing does), light 0
     * (the lighter fills it), normal up.
     */
    static int[] upQuad(float height, int rotation, float u0, float u1, float v0, float v1) {
        float[][] xz = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
        float[][] uv = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};
        int[] q = new int[32];
        for (int i = 0; i < 4; i++) {
            int o = i * 8;
            float[] t = uv[(i + rotation) & 3];
            q[o] = Float.floatToRawIntBits(xz[i][0]);
            q[o + 1] = Float.floatToRawIntBits(height);
            q[o + 2] = Float.floatToRawIntBits(xz[i][1]);
            q[o + 3] = -1;
            q[o + 4] = Float.floatToRawIntBits(t[0]);
            q[o + 5] = Float.floatToRawIntBits(t[1]);
            q[o + 6] = 0;
            q[o + 7] = 0x7F << 8;
        }
        return q;
    }
}
