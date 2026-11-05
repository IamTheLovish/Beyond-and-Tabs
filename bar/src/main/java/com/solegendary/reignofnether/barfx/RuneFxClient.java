package com.solegendary.reignofnether.barfx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/**
 * Rune magic, the client half (RuneFx is the server's): every {@link BarFx#RUNE} event becomes a rune circle in
 * {@link #POOL}, drawn each frame as textured geometry from the atlas tools/gen_rune_textures.py makes (one column per
 * faction, F_* id: the outer ring with the glyph band, the inner sigil, sixteen floating glyphs). Per circle, following
 * the study's numbers (vault refs/rune-magic-study-2026-11-05):
 * <ul>
 *   <li>the ring sweeps in over {@link #DRAW_IN} s (a fan of {@link #RING_SEG} slices lit in turn), then turns slowly
 *       ({@link #RING_SPIN} rev/s) with a 1.5 Hz breath;</li>
 *   <li>the sigil lights a moment later and turns the other way ({@link #SIGIL_SPIN} rev/s);</li>
 *   <li>{@link #GLYPHS} glyphs rise round the rim, bob, and lift away as it fades;</li>
 *   <li>the last {@link #FADE} s of the cast time fade it out with a slight swell; an early end (its caster died) fades
 *       in {@link RunePool#END_FADE} s.</li>
 * </ul>
 * The texture carries the colour (a white-hot core and the faction hue in the halo); vertices carry only the fade. One
 * additive batch for all circles, no allocation per frame (fixed pool, a reused BlockPos), a following circle rides on
 * its caster (interpolated), drawn only in lit chunks (fog of war) and near the camera. At RTS zoom past 30 it grows
 * with the zoom (up to x2, the readability rule's effect scale) so it keeps its size on screen. Client only.
 */
@OnlyIn(Dist.CLIENT)
public final class RuneFxClient {
    private RuneFxClient() { }

    static final float DRAW_IN = 0.3f, FADE = 0.35f, SIGIL_DELAY = 0.12f, GLYPH_DELAY = 0.2f;
    static final float RING_SPIN = 0.06f, SIGIL_SPIN = -0.15f, GLYPH_SPIN = 0.1f;
    static final int RING_SEG = 32, SIGIL_SEG = 16, GLYPHS = 4;
    /** The outer rim sits at this share of a ring cell's width, the sigil's reach at this share of the ring. */
    static final float RIM_IN_CELL = 0.94f, SIGIL_SHARE = 0.62f;

    // the atlas: 2048 x 768, cells of 256 (ring row 0, sigil row 1), glyphs of 64 below (4 x 4 per faction)
    static final float CELL_U = 256f / 2048f, CELL_V = 256f / 768f, GLYPH_U = 64f / 2048f, GLYPH_V = 64f / 768f;

    static final RunePool POOL = new RunePool();
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
    private static long renderNanos = 0, renderFrames = 0;
    private static int peak = 0;

    static void ingest(BarFx.Event e, float clock) {
        int id = Math.round(e.c);
        if ((e.flags & 3) == BarFx.RUNE_END) {
            POOL.end(id, clock);
            return;
        }
        int style = Math.max(0, Math.min(BarFx.FACTION_MASK, e.kind));
        POOL.start(id, e.x, e.y, e.z, e.a, (byte) style, (e.flags & BarFx.RUNE_FOLLOW) != 0, clock, Math.round(e.b));
        peak = Math.max(peak, POOL.count());
    }

    static void clear() {
        POOL.clear();
    }

    /**
     * A circle started on this client alone, as if the server had sent it (the eyes scene's casting crowd, whose units
     * the scene does not own on the server): style F_*, the caster's cast radius, following it.
     */
    public static void castLocal(Entity caster, byte style, int ticks) {
        if (caster == null)
            return;
        POOL.start(caster.getId(), (float) caster.getX(), (float) caster.getY(), (float) caster.getZ(), RuneFx.CAST_RADIUS,
            style, true, BarFxClient.clock, ticks);
        peak = Math.max(peak, POOL.count());
    }

    /** Circles held now (the eyes scene logs it). */
    public static int live() {
        return POOL.count();
    }

    /** Lifetime totals for the eyes scene's cost line: nanoseconds spent drawing, frames drawn, most circles held. */
    public static long renderNanos() { return renderNanos; }
    public static long renderFrames() { return renderFrames; }
    public static int peak() { return peak; }

    /** Draws every live circle (one additive textured batch). Called by BarFxClient.onRenderFrame after the domes. */
    static void render(MultiBufferSource.BufferSource buffers, Matrix4f mat, float clock, float partialTick) {
        POOL.prune(clock);
        if (POOL.count == 0)
            return;
        long t0 = System.nanoTime();
        Minecraft mc = Minecraft.getInstance();
        float scale = BarFxClient.ortho ? Math.max(1, Math.min(2, BarFxClient.zoom / 30f)) : 1;
        VertexConsumer vc = null;
        for (int i = 0; i < POOL.count; i++) {
            if (!POOL.aliveAt(i, clock))
                continue;
            if (POOL.follow[i] && mc.level != null) {
                Entity e = mc.level.getEntity(POOL.id[i]);
                if (e != null) {
                    POOL.x[i] = (float) (e.xo + (e.getX() - e.xo) * partialTick);
                    POOL.y[i] = (float) (e.yo + (e.getY() - e.yo) * partialTick);
                    POOL.z[i] = (float) (e.zo + (e.getZ() - e.zo) * partialTick);
                }
            }
            float R = POOL.radius[i] * scale;
            if (!BarFxClient.near(POOL.x[i], POOL.y[i], POOL.z[i], R + 2) || !lit(POOL.x[i], POOL.y[i], POOL.z[i]))
                continue;
            if (vc == null)
                vc = buffers.getBuffer(BarFxRenderTypes.RUNES);
            circle(vc, mat, i, clock, R);
        }
        if (vc != null)
            buffers.endBatch(BarFxRenderTypes.RUNES);
        renderNanos += System.nanoTime() - t0;
        renderFrames++;
    }

    private static boolean lit(float x, float y, float z) {
        try {
            return FogOfWarClientEvents.isInBrightChunk(POS.set(x, y, z));
        } catch (Exception ex) {
            return true;
        }
    }

    /** 0..1: the draw-in, then full, then the fade at the end of the cast time (or the quick fade of an early end). */
    static float strength(int i, float clock) {
        float t = clock - POOL.born[i];
        float in = Math.min(1, t / DRAW_IN * 1.2f);
        float out = POOL.endAt[i] >= 0 ? 1 - (clock - POOL.endAt[i]) / RunePool.END_FADE : (POOL.life[i] - t) / FADE;
        return Math.max(0, Math.min(in, Math.min(1, out)));
    }

    private static void circle(VertexConsumer vc, Matrix4f m, int i, float clock, float R) {
        float t = clock - POOL.born[i];
        float s = strength(i, clock);
        if (s <= 0.01f)
            return;
        float ending = POOL.endAt[i] >= 0 ? 1 - s : Math.max(0, 1 - (POOL.life[i] - t) / FADE);
        float grow = 1 + 0.12f * ending;
        float breath = 0.92f + 0.08f * (float) Math.sin(t * 2 * Math.PI * 1.5);
        float cx = POOL.x[i], cy = POOL.y[i] + 0.05f, cz = POOL.z[i];
        int f = POOL.style[i];
        float u0 = f * CELL_U;
        // the ring: a fan swept in, its texture turning
        float reveal = Math.min(1, t / DRAW_IN);
        fan(vc, m, cx, cy, cz, R * grow / RIM_IN_CELL, RING_SEG, reveal, t * RING_SPIN * 6.2831855f,
            u0 + CELL_U / 2, CELL_V / 2, 0.95f * s * breath);
        // the sigil, a little later, the other way round
        float sa = s * Math.max(0, Math.min(1, (t - SIGIL_DELAY) / 0.2f));
        if (sa > 0.01f)
            fan(vc, m, cx, cy + 0.01f, cz, R * grow * SIGIL_SHARE / RIM_IN_CELL, SIGIL_SEG, 1, t * SIGIL_SPIN * 6.2831855f,
                u0 + CELL_U / 2, CELL_V * 1.5f, 0.9f * sa);
        // the floating glyphs: rise round the rim, bob, lift away as it fades
        float ga = s * Math.max(0, Math.min(1, (t - GLYPH_DELAY) / 0.2f));
        if (ga <= 0.01f)
            return;
        float half = 0.24f * Math.max(1, R / RuneFx.CAST_RADIUS);
        for (int k = 0; k < GLYPHS; k++) {
            double a = t * GLYPH_SPIN * 6.2831855 + k * Math.PI * 2 / GLYPHS + (POOL.id[i] & 7);
            float gx = cx + (float) Math.cos(a) * R * 0.8f, gz = cz + (float) Math.sin(a) * R * 0.8f;
            float gy = cy + 0.45f + 0.18f * (float) Math.sin(t * 3 + k) + 0.7f * ending;
            int g = (POOL.id[i] + k) & 7;
            float gu = u0 + (g & 3) * GLYPH_U, gv = 2 * CELL_V + (g >> 2) * GLYPH_V;
            billboard(vc, m, gx, gy, gz, half, gu, gv, ga * 0.95f);
        }
    }

    /** A flat disc on the ground of {@code r}, the atlas cell centred at (uc, vc) mapped onto it turned by {@code spin}. */
    private static void fan(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz, float r, int seg, float reveal,
                            float spin, float uc, float vc0, float a) {
        int lit = Math.max(1, Math.min(seg, (int) Math.ceil(reveal * seg)));
        float hu = CELL_U / 2, hv = CELL_V / 2;
        double step = Math.PI * 2 / seg;
        for (int j = 0; j < lit; j++) {
            double t0 = -Math.PI / 2 + j * step, t1 = t0 + step;
            float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0), c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
            float k0 = (float) Math.cos(t0 - spin), l0 = (float) Math.sin(t0 - spin);
            float k1 = (float) Math.cos(t1 - spin), l1 = (float) Math.sin(t1 - spin);
            tv(vc, m, cx, cy, cz, a, uc, vc0);
            tv(vc, m, cx + r * c0, cy, cz + r * s0, a, uc + hu * k0, vc0 + hv * l0);
            tv(vc, m, cx + r * c1, cy, cz + r * s1, a, uc + hu * k1, vc0 + hv * l1);
            tv(vc, m, cx, cy, cz, a, uc, vc0);
        }
    }

    /** A camera-facing glyph of half size {@code h}, the 64 px atlas cell at (u, v). */
    private static void billboard(VertexConsumer vc, Matrix4f m, float x, float y, float z, float h, float u, float v, float a) {
        float lx = BarFxClient.leftX * h, ly = BarFxClient.leftY * h, lz = BarFxClient.leftZ * h;
        float ux = BarFxClient.upX * h, uy = BarFxClient.upY * h, uz = BarFxClient.upZ * h;
        tv(vc, m, x + lx - ux, y + ly - uy, z + lz - uz, a, u, v + GLYPH_V);
        tv(vc, m, x - lx - ux, y - ly - uy, z - lz - uz, a, u + GLYPH_U, v + GLYPH_V);
        tv(vc, m, x - lx + ux, y - ly + uy, z - lz + uz, a, u + GLYPH_U, v);
        tv(vc, m, x + lx + ux, y + ly + uy, z + lz + uz, a, u, v);
    }

    private static void tv(VertexConsumer vc, Matrix4f m, float x, float y, float z, float a, float u, float v) {
        vc.vertex(m, x - BarFxClient.camX, y - BarFxClient.camY, z - BarFxClient.camZ)
            .color(1f, 1f, 1f, Math.max(0, Math.min(1, a))).uv(u, v).endVertex();
    }
}
