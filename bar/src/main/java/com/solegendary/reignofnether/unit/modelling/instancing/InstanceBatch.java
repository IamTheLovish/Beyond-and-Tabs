package com.solegendary.reignofnether.unit.modelling.instancing;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * One frame's instanced unit passes, grouped for drawing (pure: no GL; the GL side uploads {@link #bones()} and
 * {@link #headers()} and issues one instanced draw per {@link Group}).
 * <p>
 * Bone matrices go in one float array, {@link #TEXELS_PER_BONE} RGBA texels a bone: the pose's three rows (the affine
 * part, x y z w each) and the normal matrix's three rows (w unused) - the vertex shader reads them back with texelFetch
 * ({@link #unpack} is its Java twin). A unit's passes share one block when their pose did not change between them.
 * <p>
 * Every pass is a record: which rig and variant, which render type (and its kind: lit cutout or the additive "eyes"),
 * the bone block, the pass's light, overlay and colour, and its ORDINAL - the pass's place in its unit's render (0 the
 * body, 1 the first layer, ...). Groups are drawn by ordinal first, so each unit's team cloth and glow go on after every
 * body (the old path draws them right after their own body; a layer meets its own body's depth with LEQUAL either way,
 * and different units' passes never share a surface), then by kind, type, rig and variant, so a render type is set up
 * once per run of its groups. A demoted unit's records are dropped ({@link #kill}).
 */
@OnlyIn(Dist.CLIENT)
public final class InstanceBatch {
    public static final int TEXELS_PER_BONE = 6, FLOATS_PER_BONE = TEXELS_PER_BONE * 4;
    /** Ints per instance header: bone texel, light, overlay, colour (r | g << 8 | b << 16 | a << 24). */
    public static final int HEADER = 4;
    /**
     * The program a pass is drawn with: CUTOUT and TRANSLUCENT both with the lit one (vanilla's entity_translucent runs
     * the entity cutout's exact shader code and blend; the type's own state sets its transparency), EYES with the eyes one.
     */
    public static final int KIND_CUTOUT = 0, KIND_EYES = 1, KIND_TRANSLUCENT = 2;

    /** Is a pass of this kind (-1: a shader this path does not reproduce) drawn instanced at this colour alpha? */
    public static boolean instanceable(int kind, float alpha) {
        if (kind < 0)
            return false;
        // a fading translucent body needs vanilla's back-to-front sort of its quads; an opaque one draws the same in any order
        return kind != KIND_TRANSLUCENT || alpha >= 1f;
    }

    private float[] bones = new float[FLOATS_PER_BONE * 4096];
    private int boneFloats = 0;

    // records, struct of arrays
    private int n = 0;
    private int[] ordinal = new int[1024], kind = new int[1024], typeSeq = new int[1024], boneTexel = new int[1024],
        light = new int[1024], overlay = new int[1024], rgba = new int[1024], unit = new int[1024];
    private Object[] type = new Object[1024];
    private Rig[] rig = new Rig[1024];
    private Rig.Variant[] variant = new Rig.Variant[1024];
    private boolean[] live = new boolean[1024];
    private final Map<Object, Integer> typeOrder = new IdentityHashMap<>();

    private int[] headers = new int[HEADER * 1024];
    private final List<Group> groups = new ArrayList<>();

    /** One instanced draw: its key, and its instances' headers at [first, first + count) (in instances). */
    public static final class Group {
        public int ordinal, kind;
        public Object type;
        public Rig rig;
        public Rig.Variant variant;
        public int first, count;
    }

    public void clear() {
        boneFloats = 0;
        n = 0;
        typeOrder.clear();
        groups.clear();
        Arrays.fill(type, null);
        Arrays.fill(rig, null);
        Arrays.fill(variant, null);
    }

    public boolean isEmpty() {
        return n == 0;
    }

    /** Appends {@code count} bones; returns the first one's texel index. */
    public int addBones(Matrix4f[] pose, Matrix3f[] normal, int count) {
        if (boneFloats + count * FLOATS_PER_BONE > bones.length)
            bones = Arrays.copyOf(bones, Math.max(bones.length * 2, boneFloats + count * FLOATS_PER_BONE));
        int texel = boneFloats / 4;
        for (int s = 0; s < count; s++) {
            pack(pose[s], normal[s], bones, boneFloats);
            boneFloats += FLOATS_PER_BONE;
        }
        return texel;
    }

    /** The bone layout the shader reads: pose rows 0-2 (x y z w), then normal rows 0-2 (x y z, w = 0). */
    static void pack(Matrix4f m, Matrix3f nm, float[] out, int o) {
        out[o] = m.m00(); out[o + 1] = m.m10(); out[o + 2] = m.m20(); out[o + 3] = m.m30();
        out[o + 4] = m.m01(); out[o + 5] = m.m11(); out[o + 6] = m.m21(); out[o + 7] = m.m31();
        out[o + 8] = m.m02(); out[o + 9] = m.m12(); out[o + 10] = m.m22(); out[o + 11] = m.m32();
        out[o + 12] = nm.m00(); out[o + 13] = nm.m10(); out[o + 14] = nm.m20(); out[o + 15] = 0f;
        out[o + 16] = nm.m01(); out[o + 17] = nm.m11(); out[o + 18] = nm.m21(); out[o + 19] = 0f;
        out[o + 20] = nm.m02(); out[o + 21] = nm.m12(); out[o + 22] = nm.m22(); out[o + 23] = 0f;
    }

    /**
     * The shader's read of one bone, in Java (the shader does the same three dot products per row): position out =
     * (row0 . p, row1 . p, row2 . p) with p = (x, y, z, 1); normal out = (nrow0 . n, nrow1 . n, nrow2 . n).
     */
    static void unpack(float[] in, int texel, float x, float y, float z, float nx, float ny, float nz, float[] out) {
        int o = texel * 4;
        out[0] = in[o] * x + in[o + 1] * y + in[o + 2] * z + in[o + 3];
        out[1] = in[o + 4] * x + in[o + 5] * y + in[o + 6] * z + in[o + 7];
        out[2] = in[o + 8] * x + in[o + 9] * y + in[o + 10] * z + in[o + 11];
        out[3] = in[o + 12] * nx + in[o + 13] * ny + in[o + 14] * nz;
        out[4] = in[o + 16] * nx + in[o + 17] * ny + in[o + 18] * nz;
        out[5] = in[o + 20] * nx + in[o + 21] * ny + in[o + 22] * nz;
    }

    /** A colour as the old path's vertex writer stores it: each channel (int) (c * 255), packed r | g << 8 | ... */
    public static int packColour(float r, float g, float b, float a) {
        return (byteOf(r)) | (byteOf(g) << 8) | (byteOf(b) << 16) | (byteOf(a) << 24);
    }

    private static int byteOf(float c) {
        return ((int) (c * 255.0F)) & 0xFF;
    }

    /** One pass of one unit. */
    public void add(int ordinal, int kind, Object type, Rig rig, Rig.Variant variant, int boneTexel, int light,
                    int overlay, int rgba, int unit) {
        if (n == live.length)
            grow();
        Integer seq = typeOrder.get(type);
        if (seq == null) {
            seq = typeOrder.size();
            typeOrder.put(type, seq);
        }
        this.ordinal[n] = ordinal;
        this.kind[n] = kind;
        this.type[n] = type;
        this.typeSeq[n] = seq;
        this.rig[n] = rig;
        this.variant[n] = variant;
        this.boneTexel[n] = boneTexel;
        this.light[n] = light;
        this.overlay[n] = overlay;
        this.rgba[n] = rgba;
        this.unit[n] = unit;
        this.live[n] = true;
        n++;
    }

    private void grow() {
        int c = live.length * 2;
        ordinal = Arrays.copyOf(ordinal, c);
        kind = Arrays.copyOf(kind, c);
        typeSeq = Arrays.copyOf(typeSeq, c);
        boneTexel = Arrays.copyOf(boneTexel, c);
        light = Arrays.copyOf(light, c);
        overlay = Arrays.copyOf(overlay, c);
        rgba = Arrays.copyOf(rgba, c);
        unit = Arrays.copyOf(unit, c);
        type = Arrays.copyOf(type, c);
        rig = Arrays.copyOf(rig, c);
        variant = Arrays.copyOf(variant, c);
        live = Arrays.copyOf(live, c);
    }

    /** Drops every record of unit {@code u} (it was demoted to the old path after some of its passes were taken). */
    public int kill(int u) {
        int k = 0;
        for (int r = n - 1; r >= 0 && unit[r] == u; r--)   // a unit's records are the last ones added
            if (live[r]) {
                live[r] = false;
                k++;
            }
        return k;
    }

    /** Live records. */
    public int liveCount() {
        int k = 0;
        for (int r = 0; r < n; r++)
            if (live[r])
                k++;
        return k;
    }

    /** Sorts the live records into draw groups and fills {@link #headers()}; returns the groups in drawing order. */
    public List<Group> build() {
        groups.clear();
        Integer[] order = new Integer[n];
        int m = 0;
        for (int r = 0; r < n; r++)
            if (live[r])
                order[m++] = r;
        Integer[] o = Arrays.copyOf(order, m);
        Arrays.sort(o, Comparator.<Integer>comparingInt(r -> ordinal[r]).thenComparingInt(r -> kind[r])
            .thenComparingInt(r -> typeSeq[r]).thenComparingInt(r -> rig[r].id).thenComparingInt(r -> variant[r].id)
            .thenComparingInt(r -> r));
        if (headers.length < m * HEADER)
            headers = new int[Math.max(headers.length * 2, m * HEADER)];
        Group g = null;
        for (int k = 0; k < m; k++) {
            int r = o[k];
            if (g == null || g.ordinal != ordinal[r] || g.kind != kind[r] || g.type != type[r] || g.rig != rig[r]
                    || g.variant != variant[r]) {
                g = new Group();
                g.ordinal = ordinal[r];
                g.kind = kind[r];
                g.type = type[r];
                g.rig = rig[r];
                g.variant = variant[r];
                g.first = k;
                groups.add(g);
            }
            g.count++;
            headers[k * HEADER] = boneTexel[r];
            headers[k * HEADER + 1] = light[r];
            headers[k * HEADER + 2] = overlay[r];
            headers[k * HEADER + 3] = rgba[r];
        }
        return groups;
    }

    public float[] bones() {
        return bones;
    }

    public int boneFloats() {
        return boneFloats;
    }

    /** The headers {@link #build} wrote: {@link #HEADER} ints per instance, instances in group order. */
    public int[] headers() {
        return headers;
    }
}
