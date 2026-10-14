package com.solegendary.reignofnether.barfx;

import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Barriers drawn as geometry (claude/art-direction.md "Effects as geometry": the target for the bubble-dome is a thin
 * bright rim, a faint fill and a ripple on hit). One hemisphere per live dome from {@link BarFx#DOME} events:
 * <ul>
 *   <li>rise: the radius eases out from nothing over {@link #RISE} s; fade: {@link #FADE} s at the end (or a white
 *       flash and a fast fade when it is broken);</li>
 *   <li>fill: a faint translucent skin, a little denser near the ground so the edge reads from the RTS camera;</li>
 *   <li>light (additive): the base ring where it meets the ground, a sparse lattice of rings and meridians, and a
 *       shimmer band that climbs the dome every {@link #SHIMMER_PERIOD} s;</li>
 *   <li>ripples: every soaked hit spreads a ring across the skin from where it struck ({@link #RIPPLE_LIFE} s).</li>
 * </ul>
 * A following dome (the Brassworks shield) rides on its entity, interpolated. Drawn only when its centre is in a
 * lit chunk (fog of war). Client only; everything here is a few hundred quads per dome.
 */
@OnlyIn(Dist.CLIENT)
final class DomeFx {
    private DomeFx() { }

    static final float RISE = 0.35f, FADE = 0.5f, BREAK_FADE = 0.25f, RIPPLE_LIFE = 0.7f, SHIMMER_PERIOD = 2.6f;
    static final int LAT = 10, LON = 40;
    static final int MAX_DOMES = 32, MAX_RIPPLES = 12;

    /** Rim / fill colour per style (BarFx.DS_*). */
    static final int[] RIM = { 0xFFD96A, 0x8FE0FF, 0xFFA040 };
    static final int[] FILL = { 0xFFE9A8, 0xBFEFFF, 0xFFC27A };

    static final class Ripple {
        final float dx, dy, dz;   // unit direction from the centre to where it struck
        final float born;

        Ripple(float dx, float dy, float dz, float born) {
            this.dx = dx; this.dy = dy; this.dz = dz; this.born = born;
        }
    }

    static final class D {
        final int id;
        float x, y, z, r;
        final float born;
        float life;   // seconds from born; a refresh (the same id announced again) extends it
        final int style;
        final boolean follow;
        float endAt = -1;
        boolean broken;
        final List<Ripple> ripples = new ArrayList<>();

        D(int id, float x, float y, float z, float r, float born, float life, int style, boolean follow) {
            this.id = id; this.x = x; this.y = y; this.z = z; this.r = r; this.born = born; this.life = life;
            this.style = style; this.follow = follow;
        }
    }

    private static final Map<Integer, D> DOMES = new HashMap<>();

    static boolean isEmpty() {
        return DOMES.isEmpty();
    }

    static void clear() {
        DOMES.clear();
    }

    /** For tests and the debug overlay: how many domes the client holds. */
    static int count() {
        return DOMES.size();
    }

    static void ingest(BarFx.Event e, float clock) {
        int id = Math.round(e.c);
        int what = e.flags & 3;
        int style = Math.max(0, Math.min(RIM.length - 1, e.kind));
        switch (what) {
            case BarFx.DOME_START -> {
                D old = DOMES.get(id);
                if (old != null && old.endAt < 0) {
                    // a refresh: the servers re-announce live domes every couple of seconds (a channelled one has no
                    // fixed end, and a player who scrolls over late must still see it) - extend, don't re-rise
                    old.life = (clock - old.born) + Math.max(1, e.b) / 20f;
                    old.r = Math.max(0.5f, e.a);
                    if (!old.follow) {
                        old.x = e.x; old.y = e.y; old.z = e.z;
                    }
                    return;
                }
                if (DOMES.size() >= MAX_DOMES && old == null)
                    return;
                DOMES.put(id, new D(id, e.x, e.y, e.z, Math.max(0.5f, e.a), clock, Math.max(1, e.b) / 20f, style,
                    (e.flags & BarFx.DOME_FOLLOW) != 0));
            }
            case BarFx.DOME_HIT -> {
                D d = DOMES.get(id);
                if (d == null)
                    return;
                float dx = e.x - d.x, dy = Math.max(0, e.y - d.y), dz = e.z - d.z;
                float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (len < 1e-3f) {
                    dx = 0; dy = 1; dz = 0; len = 1;
                }
                if (d.ripples.size() >= MAX_RIPPLES)
                    d.ripples.remove(0);
                d.ripples.add(new Ripple(dx / len, dy / len, dz / len, clock));
            }
            default -> {   // DOME_END / DOME_BROKEN
                D d = DOMES.get(id);
                if (d == null || d.endAt >= 0)
                    return;
                d.endAt = clock;
                d.broken = what == BarFx.DOME_BROKEN;
            }
        }
    }

    static void render(MultiBufferSource.BufferSource buffers, Matrix4f mat, float clock, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        List<D> draw = new ArrayList<>();
        Iterator<D> it = DOMES.values().iterator();
        while (it.hasNext()) {
            D d = it.next();
            float age = clock - d.born;
            boolean over = d.endAt >= 0 ? clock - d.endAt > (d.broken ? BREAK_FADE : FADE) : age > d.life + 1.5f;   // a lost END
            if (over) {
                it.remove();
                continue;
            }
            if (d.follow && mc.level != null) {
                Entity e = mc.level.getEntity(d.id);
                if (e != null) {
                    d.x = (float) (e.xo + (e.getX() - e.xo) * partialTick);
                    d.y = (float) (e.yo + (e.getY() - e.yo) * partialTick);
                    d.z = (float) (e.zo + (e.getZ() - e.zo) * partialTick);
                }
            }
            if (!BarFxClient.near(d.x, d.y, d.z, d.r + 2) || !lit(d))
                continue;
            d.ripples.removeIf(rp -> clock - rp.born > RIPPLE_LIFE);
            draw.add(d);
        }
        if (draw.isEmpty())
            return;
        BarFxClient.begin(buffers, BarFxRenderTypes.TRANSLUCENT, mat);
        for (D d : draw)
            fill(d, clock);
        buffers.endBatch(BarFxRenderTypes.TRANSLUCENT);
        BarFxClient.begin(buffers, BarFxRenderTypes.ADDITIVE, mat);
        for (D d : draw)
            light(d, clock);
        buffers.endBatch(BarFxRenderTypes.ADDITIVE);
    }

    private static boolean lit(D d) {
        try {
            return FogOfWarClientEvents.isInBrightChunk(BlockPos.containing(d.x, d.y, d.z));
        } catch (Exception ex) {
            return true;
        }
    }

    /** 0..1: rise, steady, fade (fast after a break). */
    static float strength(D d, float clock) {
        float age = clock - d.born;
        float rise = Math.min(1, age / RISE);
        float k = 1;
        if (d.endAt >= 0)
            k = 1 - (clock - d.endAt) / (d.broken ? BREAK_FADE : FADE);
        else if (age > d.life - FADE)
            k = (d.life - age) / FADE;
        return Math.max(0, Math.min(rise, k));
    }

    /** The radius now: eases out from 0 over RISE, swells a touch as a broken dome bursts. */
    static float radius(D d, float clock) {
        float t = Math.min(1, (clock - d.born) / RISE);
        float ease = 1 - (1 - t) * (1 - t) * (1 - t);
        float burst = d.broken && d.endAt >= 0 ? 1 + 0.25f * (clock - d.endAt) / BREAK_FADE : 1;
        return d.r * ease * burst;
    }

    // ------------------------------------------------------------------ geometry

    /** A point on the hemisphere: phi from the ground (0) to the top (PI/2), theta round the vertical. */
    private static float px(D d, float R, double phi, double theta) { return d.x + (float) (R * Math.cos(phi) * Math.cos(theta)); }
    private static float py(D d, float R, double phi) { return d.y + (float) (R * Math.sin(phi)); }
    private static float pz(D d, float R, double phi, double theta) { return d.z + (float) (R * Math.cos(phi) * Math.sin(theta)); }

    private static void quad(D d, float R, double p0, double p1, double t0, double t1, int rgb, float a0, float a1) {
        BarFxClient.v(px(d, R, p0, t0), py(d, R, p0), pz(d, R, p0, t0), rgb, a0);
        BarFxClient.v(px(d, R, p0, t1), py(d, R, p0), pz(d, R, p0, t1), rgb, a0);
        BarFxClient.v(px(d, R, p1, t1), py(d, R, p1), pz(d, R, p1, t1), rgb, a1);
        BarFxClient.v(px(d, R, p1, t0), py(d, R, p1), pz(d, R, p1, t0), rgb, a1);
    }

    private static void fill(D d, float clock) {
        float s = strength(d, clock), R = radius(d, clock);
        if (s <= 0 || R <= 0.05f)
            return;
        int col = FILL[d.style];
        double dp = Math.PI / 2 / LAT, dt = Math.PI * 2 / LON;
        for (int i = 0; i < LAT; i++) {
            double p0 = i * dp, p1 = (i + 1) * dp;
            // denser near the ground: the dome's outline is what a top-down camera reads
            float a0 = s * (0.07f + 0.09f * (float) (1 - Math.sin(p0)));
            float a1 = s * (0.07f + 0.09f * (float) (1 - Math.sin(p1)));
            for (int j = 0; j < LON; j++)
                quad(d, R, p0, p1, j * dt, (j + 1) * dt, col, a0, a1);
        }
    }

    private static void light(D d, float clock) {
        float s = strength(d, clock), R = radius(d, clock);
        if (s <= 0 || R <= 0.05f)
            return;
        int rim = RIM[d.style];
        double dt = Math.PI * 2 / LON;
        // the base ring: a bright band from the ground up, and a thin flat ring on the ground just outside it
        double band = Math.min(0.12, 0.45 / R);
        for (int j = 0; j < LON; j++) {
            double t0 = j * dt, t1 = (j + 1) * dt;
            quad(d, R, 0, band, t0, t1, rim, 0.85f * s, 0.25f * s);
            float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0), c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
            float y = d.y + 0.03f, r2 = R + 0.18f;
            BarFxClient.v(d.x + R * c0, y, d.z + R * s0, rim, 0.6f * s);
            BarFxClient.v(d.x + R * c1, y, d.z + R * s1, rim, 0.6f * s);
            BarFxClient.v(d.x + r2 * c1, y, d.z + r2 * s1, rim, 0);
            BarFxClient.v(d.x + r2 * c0, y, d.z + r2 * s0, rim, 0);
        }
        // the lattice: three rings and eight meridians, faint
        double w = Math.min(0.03, 0.12 / R);
        for (int k = 1; k <= 3; k++) {
            double p = k * Math.PI / 2 / 4;
            for (int j = 0; j < LON; j++)
                quad(d, R, p - w, p + w, j * dt, (j + 1) * dt, rim, 0.16f * s, 0.16f * s);
        }
        double wt = Math.min(0.025, 0.1 / R);
        for (int m = 0; m < 8; m++) {
            double t = m * Math.PI / 4 + clock * 0.08;   // the lattice turns, slowly
            for (int i = 0; i < LAT; i++) {
                double p0 = i * Math.PI / 2 / LAT, p1 = (i + 1) * Math.PI / 2 / LAT;
                float a = 0.14f * s * (float) (1 - 0.6 * Math.sin(p1));
                quad(d, R, p0, p1, t - wt, t + wt, rim, a, a);
            }
        }
        // the shimmer: a soft band climbing from the ground to the top
        double ph = ((clock - d.born) / SHIMMER_PERIOD) % 1.0 * Math.PI / 2;
        double sw = Math.PI / 2 / 10;
        for (int j = 0; j < LON; j++)
            quad(d, R, Math.max(0, ph - sw), Math.min(Math.PI / 2, ph + sw), j * dt, (j + 1) * dt, rim, 0.0f, 0.22f * s);
        // ripples from every soaked hit
        for (Ripple rp : d.ripples)
            ripple(d, R, rp, (clock - rp.born) / RIPPLE_LIFE, rim, s);
        // a broken dome: a white flash over the whole skin
        if (d.broken && d.endAt >= 0) {
            float f = 1 - (clock - d.endAt) / BREAK_FADE;
            if (f > 0)
                for (int i = 0; i < LAT; i++)
                    for (int j = 0; j < LON; j++)
                        quad(d, R, i * Math.PI / 2 / LAT, (i + 1) * Math.PI / 2 / LAT, j * dt, (j + 1) * dt, 0xFFFFFF, 0.35f * f, 0.35f * f);
        }
    }

    /** A ring on the skin round the hit direction, its angular radius growing from 4 to 45 degrees as it fades. */
    private static void ripple(D d, float R, Ripple rp, float k, int rim, float s) {
        if (k < 0 || k >= 1)
            return;
        double rho = Math.toRadians(4 + 41 * k), w = Math.toRadians(5 + 3 * k);
        float a = 0.95f * (1 - k) * s;
        // an orthonormal pair round the direction
        float ax = rp.dx, ay = rp.dy, az = rp.dz;
        float ux, uy, uz;
        if (Math.abs(ay) < 0.9f) { ux = -az; uy = 0; uz = ax; } else { ux = 1; uy = 0; uz = 0; }
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul; uy /= ul; uz /= ul;
        float vx = ay * uz - az * uy, vy = az * ux - ax * uz, vz = ax * uy - ay * ux;
        int seg = 28;
        for (int j = 0; j < seg; j++) {
            double t0 = j * Math.PI * 2 / seg, t1 = (j + 1) * Math.PI * 2 / seg;
            float[] q00 = onSkin(d, R, ax, ay, az, ux, uy, uz, vx, vy, vz, rho - w, t0);
            float[] q01 = onSkin(d, R, ax, ay, az, ux, uy, uz, vx, vy, vz, rho - w, t1);
            float[] q11 = onSkin(d, R, ax, ay, az, ux, uy, uz, vx, vy, vz, rho + w, t1);
            float[] q10 = onSkin(d, R, ax, ay, az, ux, uy, uz, vx, vy, vz, rho + w, t0);
            if (q00[1] < d.y && q01[1] < d.y && q11[1] < d.y && q10[1] < d.y)
                continue;   // under the ground: the dome has no floor
            BarFxClient.v(q00[0], Math.max(d.y, q00[1]), q00[2], rim, a);
            BarFxClient.v(q01[0], Math.max(d.y, q01[1]), q01[2], rim, a);
            BarFxClient.v(q11[0], Math.max(d.y, q11[1]), q11[2], rim, 0);
            BarFxClient.v(q10[0], Math.max(d.y, q10[1]), q10[2], rim, 0);
        }
        // the strike itself: a bright fleck for the first moment
        if (k < 0.2f) {
            float f = (1 - k / 0.2f) * s;
            float cx = d.x + ax * R, cy = d.y + ay * R, cz = d.z + az * R, h = 0.35f;
            BarFxClient.v(cx - ux * h, cy - uy * h, cz - uz * h, 0xFFFFFF, f);
            BarFxClient.v(cx - vx * h, cy - vy * h, cz - vz * h, 0xFFFFFF, f);
            BarFxClient.v(cx + ux * h, cy + uy * h, cz + uz * h, 0xFFFFFF, f);
            BarFxClient.v(cx + vx * h, cy + vy * h, cz + vz * h, 0xFFFFFF, f);
        }
    }

    private static float[] onSkin(D d, float R, float ax, float ay, float az, float ux, float uy, float uz,
                                  float vx, float vy, float vz, double rho, double theta) {
        double c = Math.cos(rho), sn = Math.sin(rho), ct = Math.cos(theta), st = Math.sin(theta);
        double x = c * ax + sn * (ct * ux + st * vx);
        double y = c * ay + sn * (ct * uy + st * vy);
        double z = c * az + sn * (ct * uz + st * vz);
        return new float[] { d.x + (float) (R * x), d.y + (float) (R * y), d.z + (float) (R * z) };
    }
}
