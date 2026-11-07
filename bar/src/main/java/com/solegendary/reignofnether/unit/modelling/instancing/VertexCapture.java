package com.solegendary.reignofnether.unit.modelling.instancing;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Arrays;

/**
 * A vertex sink that keeps everything a model writes: position, colour, uv, overlay, light and normal, as vanilla's
 * ModelPart.Cube.compile hands them over (the 14-argument bulk call; the one-attribute-at-a-time calls are kept too, for
 * any writer that uses them). Two uses: {@link Rig} reads a cube's own vertices through it (compile with an identity
 * pose, once per model), and the same-frame vertex check compares what the old path would have written for a pass with
 * what the instanced path draws ({@link Rig#skin}). No GL, no buffer: plain arrays.
 */
@OnlyIn(Dist.CLIENT)
public final class VertexCapture implements VertexConsumer {
    /** Per vertex: x y z  r g b a  u v  nx ny nz (12 floats) and overlay, light (2 ints). */
    public static final int FLOATS = 12, INTS = 2;
    float[] f = new float[FLOATS * 256];
    int[] i = new int[INTS * 256];
    int count = 0;
    // the attribute-at-a-time path: the vertex being assembled
    private final float[] cur = new float[FLOATS];
    private final int[] curI = new int[INTS];
    private boolean started = false;

    public int count() {
        return count;
    }

    public void clear() {
        count = 0;
        started = false;
    }

    public float x(int v) { return f[v * FLOATS]; }
    public float y(int v) { return f[v * FLOATS + 1]; }
    public float z(int v) { return f[v * FLOATS + 2]; }
    public float r(int v) { return f[v * FLOATS + 3]; }
    public float g(int v) { return f[v * FLOATS + 4]; }
    public float b(int v) { return f[v * FLOATS + 5]; }
    public float a(int v) { return f[v * FLOATS + 6]; }
    public float u(int v) { return f[v * FLOATS + 7]; }
    public float vv(int v) { return f[v * FLOATS + 8]; }
    public float nx(int v) { return f[v * FLOATS + 9]; }
    public float ny(int v) { return f[v * FLOATS + 10]; }
    public float nz(int v) { return f[v * FLOATS + 11]; }
    public int overlay(int v) { return i[v * INTS]; }
    public int light(int v) { return i[v * INTS + 1]; }

    private void grow() {
        if ((count + 1) * FLOATS > f.length) {
            f = Arrays.copyOf(f, f.length * 2);
            i = Arrays.copyOf(i, i.length * 2);
        }
    }

    /** One whole vertex (what a writer that knows every attribute at once calls; the cube compile does). */
    public void put(float x, float y, float z, float r, float g, float b, float a, float u, float v, int overlay,
                    int light, float nx, float ny, float nz) {
        grow();
        int o = count * FLOATS;
        f[o] = x;
        f[o + 1] = y;
        f[o + 2] = z;
        f[o + 3] = r;
        f[o + 4] = g;
        f[o + 5] = b;
        f[o + 6] = a;
        f[o + 7] = u;
        f[o + 8] = v;
        f[o + 9] = nx;
        f[o + 10] = ny;
        f[o + 11] = nz;
        i[count * INTS] = overlay;
        i[count * INTS + 1] = light;
        count++;
    }

    @Override
    public void vertex(float x, float y, float z, float r, float g, float b, float a, float u, float v, int overlay,
                       int light, float nx, float ny, float nz) {
        put(x, y, z, r, g, b, a, u, v, overlay, light, nx, ny, nz);
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        Arrays.fill(cur, 0f);
        Arrays.fill(curI, 0);
        cur[0] = (float) x;
        cur[1] = (float) y;
        cur[2] = (float) z;
        started = true;
        return this;
    }

    @Override
    public VertexConsumer color(int r, int g, int b, int a) {
        cur[3] = r / 255f;
        cur[4] = g / 255f;
        cur[5] = b / 255f;
        cur[6] = a / 255f;
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        cur[7] = u;
        cur[8] = v;
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        curI[0] = (u & 0xFFFF) | (v << 16);
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        curI[1] = (u & 0xFFFF) | (v << 16);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        cur[9] = x;
        cur[10] = y;
        cur[11] = z;
        return this;
    }

    @Override
    public void endVertex() {
        if (!started)
            return;
        put(cur[0], cur[1], cur[2], cur[3], cur[4], cur[5], cur[6], cur[7], cur[8], curI[0], curI[1], cur[9], cur[10],
            cur[11]);
        started = false;
    }

    @Override
    public void defaultColor(int r, int g, int b, int a) {
    }

    @Override
    public void unsetDefaultColor() {
    }
}
