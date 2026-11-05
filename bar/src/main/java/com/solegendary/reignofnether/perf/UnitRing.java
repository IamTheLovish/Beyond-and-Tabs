package com.solegendary.reignofnether.perf;

import java.util.concurrent.ConcurrentHashMap;

/**
 * The unit circle as line segments, built once per segment count: point i is (x[i], z[i]) and segment i (from point
 * i-1 to point i) runs along (nx[i], nz[i]), a unit vector (what the lines shader widens a segment along). A ring of
 * any radius is the table scaled and offset, so a marker ring costs multiplications instead of a sine, a cosine and a
 * square root per segment per frame for a circle that never changes. Pure Java.
 */
public final class UnitRing {

    private static final ConcurrentHashMap<Integer, UnitRing> TABLES = new ConcurrentHashMap<>();

    public final int segments;
    /** Points 0..segments (point 0 and point segments coincide). */
    public final float[] x, z;
    /** Unit direction of segment i, 1..segments (index 0 unused). */
    public final float[] nx, nz;

    private UnitRing(int segments) {
        this.segments = segments;
        x = new float[segments + 1];
        z = new float[segments + 1];
        nx = new float[segments + 1];
        nz = new float[segments + 1];
        x[0] = 1f;
        for (int i = 1; i <= segments; i++) {
            double ang = Math.PI * 2 * i / segments;
            x[i] = (float) Math.cos(ang);
            z[i] = (float) Math.sin(ang);
            float dx = x[i] - x[i - 1], dz = z[i] - z[i - 1];
            float len = (float) Math.sqrt(dx * dx + dz * dz);
            nx[i] = dx / len;
            nz[i] = dz / len;
        }
    }

    public static UnitRing of(int segments) {
        return TABLES.computeIfAbsent(segments, UnitRing::new);
    }
}
