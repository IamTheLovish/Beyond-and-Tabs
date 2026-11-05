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
 * faction, F_* id). The look went through blind rounds of a fresh critic against lovish's reference sheet
 * (tools/rune_preview.py renders it offline with these same numbers; keep the two in step). One cast, by layer:
 * <ul>
 *   <li>a light underlay inside the ring ({@link BarFxRenderTypes#RUNES_SHADE}, {@link #UNDER_A}), the faction's hue at
 *       a low value (Gravebound's a violet underglow; none under the light factions): additive light can only brighten,
 *       and a green rune on sunlit grass needs the ground under it a little darker;</li>
 *   <li>the ground ring with its glyph band sweeps in over {@link #DRAW_IN} s, starting on the camera's far side so the
 *       sweep's straight edge sits behind the unit, its leading edge feathered and a glyph riding its tip (the circle is
 *       being written), a seed of light at the feet the moment it starts; it turns ({@link #RING_SPIN}) and breathes so
 *       the hold keeps moving;</li>
 *   <li>the release, at the end of the draw-in: the ring flashes for {@link #FLASH_T} s (the ring only, never the
 *       unit), the glyph band bursts out to {@link #SHOCK_R} x, a plain pulse ring runs out to {@link #PULSE_TO} x (the
 *       moment, readable at zoom 30), and a thin column of light rises from the caster's head;</li>
 *   <li>a glyph band at the waist ({@link #TIER_H}, {@link #TIER_R}) sweeping up after the release, turning against
 *       the ground ring; and the faction's sigil over the head ({@link #SIGIL_H}): the one shape that names the faction
 *       is never under the unit (from this pitch the unit hid a sigil on the ground), it grows in with the draw-in,
 *       spins, and shrinks away with the fade;</li>
 *   <li>motes in the faction's own shape and way of moving, kept below {@link #RISE} blocks so they stay with their
 *       caster (stars rise, ash sinks, embers are thrown, petals spiral, pixels climb lines, sparks zigzag, cogs orbit at
 *       head height), and three glyphs climbing;</li>
 *   <li>the last {@link #FADE} s of the cast time fade it with a slight swell; an early end (its caster died) fades in
 *       {@link RunePool#END_FADE} s.</li>
 * </ul>
 * Alphas stay low enough that two overlapping circles do not burn to white, and a circle with others within
 * {@link #CROWD_R} blocks dims its light (not below {@link #CROWD_MIN}); when many casters release at once their flashes
 * are dimmed together ({@link #FLASH_CAP}) so a big fight does not strobe. The texture carries the colour (the
 * faction's light tint in the core line, its hue in the halo); vertices carry only the alpha (and the underlay's
 * tint). The units hide what is behind them (drawn after entities, depth tested). Two batches for all circles, no
 * allocation per frame (fixed pool and scratch arrays, a reused BlockPos), a following circle rides on its caster (interpolated), drawn only in lit chunks (fog of war) and near the camera. At RTS
 * zoom past 30 it grows with the zoom (up to x2) so it keeps its size on screen. Client only.
 */
@OnlyIn(Dist.CLIENT)
public final class RuneFxClient {
    private RuneFxClient() { }

    static final float DRAW_IN = 0.3f, FADE = 0.35f, SIGIL_DELAY = 0.08f;
    static final float RING_SPIN = 0.17f, GLYPH_SPIN = 0.1f;
    static final int RING_SEG = 32, SIGIL_SEG = 16, TIER_SEG = 24, SHOCK_SEG = 32;
    /** Slices over which the sweep's leading edge fades in. */
    static final int FEATHER = 2;
    /** The outer rim sits at this share of a ring cell's width. */
    static final float RIM_IN_CELL = 0.94f;
    /** The sigil over the head: its height in blocks (T1), its radius as a share of the circle's, rev/s. */
    static final float SIGIL_H = 2.45f, SIGIL_R = 0.38f, SIGIL_TURN = 60 / 360f;
    /** The seed at the feet: half size in blocks, life in s. The hold's breath: base, depth, Hz. */
    static final float SEED_HALF = 0.5f, SEED_T = 0.25f, BREATH = 0.9f, BREATH_DEPTH = 0.1f, BREATH_HZ = 2;
    /** The underlay: its radius as a share of the circle's (inside the ring), its alpha per faction (F_* id). */
    static final float UNDER_R = 0.85f;
    static final float[] UNDER_A = {0.15f, 0, 0.2f, 0.15f, 0.15f, 0, 0.15f, 0.15f};
    /**
     * The underlay's tint per faction: its hue at value 0.25 (tools/rune_preview.py under_tint); Gravebound's a dark
     * violet, so its chartreuse stands off Verdant's emerald. The light factions (Sunforged, Light Grid) have none.
     */
    static final float[][] UNDER_RGB = {{0.227f, 0.227f, 0.25f}, {0, 0, 0}, {0.165f, 0.04f, 0.23f}, {0.25f, 0.073f, 0.025f},
        {0.059f, 0.25f, 0.12f}, {0, 0, 0}, {0.151f, 0.09f, 0.25f}, {0.25f, 0.181f, 0.06f}};
    static final float RING_A = 1.0f, SIGIL_A = 0.95f, FLASH_A = 0.45f, FLASH_T = 0.1f;
    /** The glyph riding the sweep's tip: half size in blocks. */
    static final float TIP_HALF = 0.16f;
    /** Others within this many blocks dim a circle's light to 1 / sqrt(1 + n), not below CROWD_MIN. */
    static final float CROWD_R = 4, CROWD_MIN = 0.55f;
    /** How many circles may flash at full strength in one frame; past it every flash is dimmed to share that much. */
    static final float FLASH_CAP = 3;
    static final float SHOCK_R = 1.55f, SHOCK_T = 0.2f, SHOCK_A = 0.8f;
    /** The pulse ring: from this share of the circle's radius to PULSE_TO over PULSE_T s, alpha PULSE_A down to 0. */
    static final float PULSE_FROM = 0.625f, PULSE_TO = 1.375f, PULSE_T = 0.25f, PULSE_A = 0.5f;
    /** The waist band: height in blocks (T1), radius as a share of the circle's, start time, alpha, rev/s. */
    static final float TIER_H = 0.8f, TIER_R = 0.62f, TIER_AT = 0.3f, TIER_A = 0.7f, TIER_SPIN = -0.2f;
    static final float TIER_RISE = 0.12f;
    static final float PILLAR_FROM = 2.0f, PILLAR_H = 2.5f, PILLAR_HW = 0.125f, PILLAR_A = 0.5f, PILLAR_T = 0.12f;
    static final int MOTES = 24, GLYPHS = 3;
    /** Motes run this much faster than the cast's clock (short lives). */
    static final float MOTE_SPEED = 2;
    static final float MOTE_A = 0.8f, MOTE_HALF = 0.145f, RISE = 1.5f;
    /** Brassworks' cogs: this many orbiting at head height (blocks, T1), at a share of the radius, rev/s, half size. */
    static final int COGS = 10;
    static final float COG_H = 1.6f, COG_R = 0.75f, COG_SPIN = 0.6f, COG_HALF = 0.18f;

    // the atlas: 2048 x 1152. Per faction column of 256: ring (y 0), sigil (256), 16 glyphs of 64 (512), the glyph band
    // tier (768), then at y 1024 the plain tier ring (128) | mote, beam over spark, shade (64 each)
    static final float AW = 2048f, AH = 1152f;
    static final float CELL_U = 256f / AW, CELL_V = 256f / AH, GLYPH_U = 64f / AW, GLYPH_V = 64f / AH;
    static final float V_SIGIL = 256f / AH, V_GLYPH = 512f / AH, V_BAND = 768f / AH, V_FX = 1024f / AH;
    /** Half a texel: sprites stop this far inside their cell so linear filtering never reaches the neighbour's. */
    static final float EU = 0.5f / AW, EV = 0.5f / AH;

    static final RunePool POOL = new RunePool();
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
    private static final boolean[] SHOWN = new boolean[RunePool.MAX];
    private static final float[] RADIUS = new float[RunePool.MAX], CROWD = new float[RunePool.MAX];
    private static float flashScale = 1, sweepStart = (float) (-Math.PI / 2);
    private static float mx, my, mz, ma;   // mote()'s answer
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

    /** Draws every live circle: the underlays in one batch, then the light in one additive batch. After the domes. */
    static void render(MultiBufferSource.BufferSource buffers, Matrix4f mat, float clock, float partialTick) {
        POOL.prune(clock);
        if (POOL.count == 0)
            return;
        long t0 = System.nanoTime();
        Minecraft mc = Minecraft.getInstance();
        float scale = BarFxClient.ortho ? Math.max(1, Math.min(2, BarFxClient.zoom / 30f)) : 1;
        int shown = 0, flashing = 0;
        for (int i = 0; i < POOL.count; i++) {
            SHOWN[i] = false;
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
            float R = RADIUS[i] = POOL.radius[i] * scale;
            if (!BarFxClient.near(POOL.x[i], POOL.y[i], POOL.z[i], R + 3) || !lit(POOL.x[i], POOL.y[i], POOL.z[i]))
                continue;
            SHOWN[i] = true;
            shown++;
            float rel = clock - POOL.born[i] - DRAW_IN;
            if (rel >= 0 && rel < FLASH_T)
                flashing++;
        }
        if (shown > 0) {
            flashScale = Math.min(1, FLASH_CAP / Math.max(1, flashing));
            sweepStart = (float) Math.atan2(BarFxClient.lookZ, BarFxClient.lookX);
            for (int i = 0; i < POOL.count; i++) {
                if (!SHOWN[i])
                    continue;
                int n = 0;
                for (int j = 0; j < POOL.count; j++) {
                    if (j == i || !SHOWN[j])
                        continue;
                    float dx = POOL.x[j] - POOL.x[i], dz = POOL.z[j] - POOL.z[i];
                    if (dx * dx + dz * dz < CROWD_R * CROWD_R)
                        n++;
                }
                CROWD[i] = Math.max(CROWD_MIN, 1 / (float) Math.sqrt(1 + n));
            }
            VertexConsumer sh = buffers.getBuffer(BarFxRenderTypes.RUNES_SHADE);
            for (int i = 0; i < POOL.count; i++)
                if (SHOWN[i])
                    underlay(sh, mat, i, clock, RADIUS[i]);
            buffers.endBatch(BarFxRenderTypes.RUNES_SHADE);
            VertexConsumer vc = buffers.getBuffer(BarFxRenderTypes.RUNES);
            for (int i = 0; i < POOL.count; i++)
                if (SHOWN[i])
                    circle(vc, mat, i, clock, RADIUS[i]);
            buffers.endBatch(BarFxRenderTypes.RUNES);
        }
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

    private static float ending(int i, float clock, float s) {
        return POOL.endAt[i] >= 0 ? 1 - s : Math.max(0, 1 - (POOL.life[i] - (clock - POOL.born[i])) / FADE);
    }

    private static float ease(float x) {
        x = Math.max(0, Math.min(1, x));
        float k = 1 - x;
        return 1 - k * k * k;
    }

    private static void underlay(VertexConsumer vc, Matrix4f m, int i, float clock, float R) {
        int f = POOL.style[i];
        float s = strength(i, clock);
        if (s <= 0.01f || UNDER_A[f] <= 0)
            return;
        float grow = 1 + 0.12f * ending(i, clock, s);
        float reveal = Math.min(1, (clock - POOL.born[i]) / DRAW_IN);
        float[] c = UNDER_RGB[f];
        fan(vc, m, POOL.x[i], POOL.y[i] + 0.04f, POOL.z[i], R * UNDER_R * grow, 16, reveal, sweepStart, FEATHER, 0,
            f * CELL_U + 3.5f * GLYPH_U, V_FX + 1.5f * GLYPH_V, GLYPH_U / 2 - EU, GLYPH_V / 2 - EV, c[0], c[1], c[2],
            UNDER_A[f] * s);
    }

    private static void circle(VertexConsumer vc, Matrix4f m, int i, float clock, float R) {
        float t = clock - POOL.born[i];
        float s = strength(i, clock);
        if (s <= 0.01f)
            return;
        float sl = s * CROWD[i];   // the light, dimmed in a crowd
        float sc = R / RuneFx.CAST_RADIUS;
        float ending = ending(i, clock, s);
        float grow = 1 + 0.12f * ending;
        float breath = BREATH + BREATH_DEPTH * (float) Math.sin(t * 2 * Math.PI * BREATH_HZ);
        float rel = t - DRAW_IN;
        float flash = rel >= 0 ? flashScale * Math.max(0, 1 - rel / FLASH_T) : 0;
        float cx = POOL.x[i], cy = POOL.y[i] + 0.05f, cz = POOL.z[i];
        int f = POOL.style[i];
        float u0 = f * CELL_U;
        float tau = 6.2831855f;
        if (t < SEED_T)   // the seed: light gathers at the feet the moment the cast starts
            billboard(vc, m, cx, cy + 0.15f, cz, SEED_HALF * Math.max(1, sc), u0 + 2 * GLYPH_U, V_FX + GLYPH_V, 1 - t / SEED_T);
        // the ground ring (swept in from the far side, then its flash)
        float ringR = R * grow / RIM_IN_CELL;
        fan(vc, m, cx, cy, cz, ringR, RING_SEG, Math.min(1, t / DRAW_IN), sweepStart, FEATHER, t * RING_SPIN * tau,
            u0 + CELL_U / 2, CELL_V / 2, CELL_U / 2, CELL_V / 2, 1, 1, 1, RING_A * sl * breath);
        if (t < DRAW_IN) {   // the glyph riding the sweep's tip: the circle is being written
            double a = sweepStart + Math.min(1, t / DRAW_IN) * tau;
            int g = POOL.id[i] & 15;
            billboard(vc, m, cx + (float) Math.cos(a) * R * 0.92f, cy + 0.25f, cz + (float) Math.sin(a) * R * 0.92f,
                TIP_HALF * Math.max(1, sc), u0 + (g & 3) * GLYPH_U, V_GLYPH + (g >> 2) * GLYPH_V, sl);
        }
        if (flash > 0.01f)
            disc(vc, m, cx, cy, cz, ringR, RING_SEG, t * RING_SPIN * tau, u0 + CELL_U / 2, CELL_V / 2, CELL_U / 2, CELL_V / 2,
                FLASH_A * flash * sl);
        // the release: the glyph band thrown out, easing out, fading fast
        if (rel >= 0 && rel < SHOCK_T) {
            float q = rel / SHOCK_T;
            disc(vc, m, cx, cy + 0.02f, cz, R * (1 + (SHOCK_R - 1) * ease(q)) / RIM_IN_CELL, SHOCK_SEG, t * 0.3f,
                u0 + CELL_U / 2, V_BAND + CELL_V / 2, CELL_U / 2, CELL_V / 2, SHOCK_A * (1 - q) * (1 - q) * sl);
        }
        if (rel >= 0 && rel < PULSE_T) {   // and the plain pulse ring, the moment read at zoom 30
            float q = rel / PULSE_T;
            disc(vc, m, cx, cy + 0.03f, cz, R * (PULSE_FROM + (PULSE_TO - PULSE_FROM) * ease(q)) / RIM_IN_CELL, TIER_SEG, 0,
                u0 + GLYPH_U, V_FX + GLYPH_V, GLYPH_U - EU, GLYPH_V - EV, PULSE_A * (1 - q) * sl);
        }
        // the waist band, sweeping up after the release
        float rise = ease((t - TIER_AT) / TIER_RISE);
        if (TIER_A * sl * rise > 0.01f)
            disc(vc, m, cx, cy + TIER_H * sc * rise, cz, R * TIER_R * grow / RIM_IN_CELL, TIER_SEG, t * TIER_SPIN * tau,
                u0 + CELL_U / 2, V_BAND + CELL_V / 2, CELL_U / 2, CELL_V / 2, TIER_A * sl * rise);
        // the faction's sigil over the head: grows in with the draw-in, spins, shrinks away with the fade
        float ks = ease((t - SIGIL_DELAY) / 0.22f) * Math.max(0, Math.min(1, (POOL.life[i] - t) / 0.4f));
        if (ks > 0.01f)
            disc(vc, m, cx, cy + SIGIL_H * sc, cz, R * SIGIL_R * ks / RIM_IN_CELL, SIGIL_SEG, t * SIGIL_TURN * tau,
                u0 + CELL_U / 2, V_SIGIL + CELL_V / 2, CELL_U / 2, CELL_V / 2, SIGIL_A * sl * Math.min(1, ks * 1.5f));
        // the column of light from the head, at the release only
        if (rel >= 0 && rel < PILLAR_T) {
            float pa = PILLAR_A * flashScale * sl * (1 - rel / PILLAR_T);
            pillar(vc, m, cx, cy + PILLAR_FROM * sc, cz, PILLAR_H * sc, PILLAR_HW * sc, u0 + 3 * GLYPH_U, V_FX, pa);
        }
        // motes, each faction its own way, short lived; Brassworks' cogs orbit at head height instead
        float mA = sl * Math.max(0, Math.min(1, (t - 0.2f) / 0.2f)) * MOTE_A;
        if (mA > 0.01f) {
            if (f == BarFx.F_BRASS) {
                for (int k = 0; k < COGS; k++) {
                    double a = tau * ((float) k / COGS + t * COG_SPIN);
                    billboard(vc, m, cx + (float) Math.cos(a) * R * COG_R, cy + COG_H * sc, cz + (float) Math.sin(a) * R * COG_R,
                        COG_HALF, u0 + 2 * GLYPH_U, V_FX, mA);
                }
            } else {
                float half = MOTE_HALF * Math.max(1, (float) Math.sqrt(sc));
                for (int k = 0; k < MOTES; k++) {
                    mote(f, k, t * MOTE_SPEED, POOL.id[i], R);
                    billboard(vc, m, cx + mx, cy + my, cz + mz, half, u0 + 2 * GLYPH_U, V_FX, mA * ma);
                }
            }
        }
        // glyphs climbing
        float ga = sl * Math.max(0, Math.min(1, (t - 0.4f) / 0.2f));
        if (ga > 0.01f) {
            int id = POOL.id[i];
            for (int k = 0; k < GLYPHS; k++) {
                float u = frac(t * 0.42f + (float) k / GLYPHS + hash01(id + k * 3));
                double a = t * GLYPH_SPIN * tau + k * tau / GLYPHS + (id & 7);
                float gy = cy + 0.4f * sc + 1.1f * sc * u;
                int g = (id + k) & 15;
                billboard(vc, m, cx + (float) Math.cos(a) * R * 0.75f, gy, cz + (float) Math.sin(a) * R * 0.75f,
                    0.17f * Math.max(1, sc), u0 + (g & 3) * GLYPH_U, V_GLYPH + (g >> 2) * GLYPH_V,
                    ga * 0.7f * (float) Math.sin(Math.PI * u));
            }
        }
    }

    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }

    /** 0..1 from an int, the same as tools/rune_preview.py's hash01. */
    static float hash01(int n) {
        n *= 0x9E3779B1;
        n ^= n >>> 15;
        return (n & 0xFFFF) / 65535f;
    }

    /**
     * Where mote k of a circle is at time t (offset from the centre) and its alpha, into mx, my, mz, ma (Brassworks'
     * cogs are circle()'s own).
     */
    static void mote(int f, int k, float t, int id, float R) {
        float ph = (float) k / MOTES + hash01(id * 31 + k);
        float sc = R / RuneFx.CAST_RADIUS;
        float u, turn, r, y;
        switch (f) {
            case 2 -> {   // Gravebound: ash sinking from the top onto the rim
                u = frac(t * 0.55f + ph);
                turn = ph + t * 0.05f;
                r = R * (0.5f + 0.45f * u);
                y = RISE * sc * (1 - u);
            }
            case 3 -> {   // Horde: embers thrown up and out, falling back
                u = frac(t * 0.9f + ph);
                turn = ph * 3.1f;
                r = R * (0.35f + 0.85f * u);
                y = sc * (2.6f * u - 2.0f * u * u) + 0.1f;
            }
            case 4 -> {   // Verdant: petals on a wide slow spiral
                u = frac(t * 0.45f + ph);
                turn = ph + t * 0.25f + 0.4f * u;
                r = R * (0.7f + 0.35f * (float) Math.sin(6.2831855f * (u + ph)));
                y = RISE * sc * u;
            }
            case 5 -> {   // Light Grid: pixels climbing straight lines
                u = frac(t * 0.8f + ph);
                turn = Math.round(ph * 12) / 12f;
                r = R * 0.82f;
                y = RISE * sc * u;
            }
            case 6 -> {   // Stormcallers: sparks zigzagging up fast
                u = frac(t * 1.25f + ph);
                turn = ph + 0.06f * (Math.sin(t * 40 + k * 7) > 0 ? 1 : -1);
                r = R * (0.9f - 0.45f * u);
                y = RISE * 1.15f * sc * u;
            }
            case 1 -> {   // Sunforged: stars rising straight round the rim
                u = frac(t * 0.7f + ph);
                turn = ph;
                r = R * 0.88f;
                y = RISE * sc * u;
            }
            default -> {  // a plain helix
                u = frac(t * 0.7f + ph);
                turn = ph + t * 0.35f;
                r = R * (0.95f - 0.5f * u);
                y = RISE * sc * u;
            }
        }
        double a = 6.2831855 * turn;
        mx = (float) Math.cos(a) * r;
        my = y;
        mz = (float) Math.sin(a) * r;
        ma = (float) Math.sin(Math.PI * u);
    }

    /** A whole disc in white light: {@link #fan} with nothing to sweep. */
    private static void disc(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz, float r, int seg, float spin,
                             float uc, float vc0, float hu, float hv, float a) {
        fan(vc, m, cx, cy, cz, r, seg, 1, 0, 0, spin, uc, vc0, hu, hv, 1, 1, 1, a);
    }

    /**
     * A flat disc of radius {@code r} at height {@code cy}: the atlas cell centred at (uc, vc0), half size (hu, hv), mapped
     * onto it turned by {@code spin}; vertex colour (cr, cg, cb). Swept in: its slices light from the angle {@code start}
     * (radians on the ground, x towards z) up to {@code reveal} of the way round, the leading {@code feather} slices
     * fading in.
     */
    private static void fan(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz, float r, int seg, float reveal,
                            float start, int feather, float spin, float uc, float vc0, float hu, float hv, float cr, float cg,
                            float cb, float a) {
        float litF = reveal * (seg + feather);
        int lit = Math.max(1, Math.min(seg, (int) Math.ceil(litF)));
        double step = Math.PI * 2 / seg;
        for (int j = 0; j < lit; j++) {
            float aj = feather > 0 ? a * Math.max(0, Math.min(1, (litF - j) / feather)) : a;
            if (aj <= 0.004f)
                continue;
            double t0 = start + j * step, t1 = t0 + step;
            float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0), c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
            float k0 = (float) Math.cos(t0 - spin), l0 = (float) Math.sin(t0 - spin);
            float k1 = (float) Math.cos(t1 - spin), l1 = (float) Math.sin(t1 - spin);
            tv(vc, m, cx, cy, cz, cr, cg, cb, aj, uc, vc0);
            tv(vc, m, cx + r * c0, cy, cz + r * s0, cr, cg, cb, aj, uc + hu * k0, vc0 + hv * l0);
            tv(vc, m, cx + r * c1, cy, cz + r * s1, cr, cg, cb, aj, uc + hu * k1, vc0 + hv * l1);
            tv(vc, m, cx, cy, cz, cr, cg, cb, aj, uc, vc0);
        }
    }

    /** A camera-facing glyph or mote of half size {@code h}, the 64 px atlas cell at (u, v). */
    private static void billboard(VertexConsumer vc, Matrix4f m, float x, float y, float z, float h, float u, float v, float a) {
        if (a <= 0.01f)
            return;
        float lx = BarFxClient.leftX * h, ly = BarFxClient.leftY * h, lz = BarFxClient.leftZ * h;
        float ux = BarFxClient.upX * h, uy = BarFxClient.upY * h, uz = BarFxClient.upZ * h;
        float u0 = u + EU, u1 = u + GLYPH_U - EU, v0 = v + EV, v1 = v + GLYPH_V - EV;
        tv(vc, m, x + lx - ux, y + ly - uy, z + lz - uz, 1, a, u0, v1);
        tv(vc, m, x - lx - ux, y - ly - uy, z - lz - uz, 1, a, u1, v1);
        tv(vc, m, x - lx + ux, y - ly + uy, z - lz + uz, 1, a, u1, v0);
        tv(vc, m, x + lx + ux, y + ly + uy, z + lz + uz, 1, a, u0, v0);
    }

    /** A column of light from (x, y, z) up {@code height}, turned about y to face the camera; alpha {@code a} at its foot. */
    private static void pillar(VertexConsumer vc, Matrix4f m, float x, float y, float z, float height, float hw, float u, float v,
                               float a) {
        if (a <= 0.01f)
            return;
        float lx = BarFxClient.leftX, lz = BarFxClient.leftZ;
        float n = (float) Math.sqrt(lx * lx + lz * lz);
        if (n < 1e-4f) {
            lx = 1;
            lz = 0;
            n = 1;
        }
        lx = lx / n * hw;
        lz = lz / n * hw;
        float vm = v + GLYPH_V / 2;
        tv(vc, m, x + lx, y, z + lz, 1, a, u + EU, vm);
        tv(vc, m, x - lx, y, z - lz, 1, a, u + GLYPH_U - EU, vm);
        tv(vc, m, x - lx, y + height, z - lz, 1, 0, u + GLYPH_U - EU, vm);
        tv(vc, m, x + lx, y + height, z + lz, 1, 0, u + EU, vm);
    }

    private static void tv(VertexConsumer vc, Matrix4f m, float x, float y, float z, float c, float a, float u, float v) {
        tv(vc, m, x, y, z, c, c, c, a, u, v);
    }

    private static void tv(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float g, float b, float a,
                           float u, float v) {
        vc.vertex(m, x - BarFxClient.camX, y - BarFxClient.camY, z - BarFxClient.camZ)
            .color(r, g, b, Math.max(0, Math.min(1, a))).uv(u, v).endVertex();
    }
}
