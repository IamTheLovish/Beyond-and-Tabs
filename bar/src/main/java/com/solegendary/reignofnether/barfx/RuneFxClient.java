package com.solegendary.reignofnether.barfx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/**
 * Rune magic, the client half (RuneFx is the server's): every {@link BarFx#RUNE} event becomes a rune circle in
 * {@link #POOL}, drawn each frame as textured geometry from the atlas tools/gen_rune_textures.py makes (one column per
 * faction, F_* id). The look went through blind rounds of a fresh critic against lovish's reference sheet
 * (tools/rune_preview.py renders it offline, up to v9; from round 9 on the in-game eyes stills are the judge).
 * The first in-game round (eyes stills of a 505-unit army) found what the offline preview hid: the textured rim
 * was about 1 px on screen, grass blades cut it, Verdant and Sunforged sank into sunlit grass, and 48 overlapping
 * circles made a haze at zoom 45. So the rim is now drawn as geometry, not texture. One cast, by layer:
 * <ul>
 *   <li>the dark ground ({@link BarFxRenderTypes#RUNES_SHADE}): an underlay filling the ring ({@link #UNDER_A}) and a
 *       darker line under the rim, both in the faction's dark ({@link #DARK}): additive light can only brighten, so every
 *       faction gets a darker ground to stand on;</li>
 *   <li>the rim: a stroke of light ({@link #stroke}) with a near-white core ({@link #CORE}) in a faction halo
 *       ({@link #HALO}), its width set in screen pixels as well as blocks ({@link #CORE_PX}) so it never falls under
 *       3 px at any zoom, settling a little over the hold ({@link #SETTLE}); a thinner inner line ({@link #INNER_R}) the
 *       second weight; between them the faction's rune ring from the atlas, with its glyph band. All sweep in over
 *       {@link #DRAW_IN} s from the camera's far side (the sweep's straight edge sits behind the unit), the leading
 *       edge feathered and a glyph riding the tip, a seed of light at the feet the moment it starts; the ring turns
 *       ({@link #RING_SPIN}) and the rim breathes so the hold keeps moving;</li>
 *   <li>the ground layer is drawn before the entities ({@link #renderGround}), so every unit stands over its circle,
 *       and {@link #PULL} blocks toward the RTS camera: in its orthographic view that moves only the depth, so grass
 *       blades no longer cut the rim;</li>
 *   <li>the release, at the end of the draw-in: the rim flares for {@link #FLASH_T} s, is thrown out to
 *       {@link #PULSE_TO} x and fades (the moment, readable at any zoom), a white burst at the feet and a thin column
 *       of light from the head;</li>
 *   <li>a glyph band at the waist ({@link #TIER_H}, {@link #TIER_R}) sweeping up after the release, turning against
 *       the ground ring; and the faction's sigil over the head ({@link #SIGIL_H}) in a ring of its own light: the one
 *       shape that names the faction is never under the unit, it grows in with the draw-in, spins, and shrinks away
 *       (its ring in the faction's hue: a white one read as a selection marker);</li>
 *   <li>motes in the faction's own shape and way of moving, kept below {@link #RISE} blocks so they stay with their
 *       caster (stars rise, ash sinks, embers are thrown, petals spiral, pixels climb lines, sparks zigzag, cogs orbit at
 *       the rim);</li>
 *   <li>the last {@link #FADE} s of the cast time fade it with a slight swell; an early end (its caster died) fades in
 *       {@link RunePool#END_FADE} s.</li>
 * </ul>
 * At RTS zoom past {@link #LOD_FROM} the details (atlas ring, band, sigil, motes, column) fade out; past
 * {@link #LOD_TO}, where a circle is a few pixels wide, only the rim, its dark line and the release are drawn. A circle
 * with others within {@link #CROWD_R} blocks dims its light (not below {@link #CROWD_MIN}); when many casters release at
 * once their flashes are dimmed together ({@link #FLASH_CAP}) so a big fight does not strobe. A ring's slices follow
 * its size on screen ({@link #segs}). Two batches for the ground of all circles and one for the light above, no
 * allocation per frame (fixed pool and scratch arrays, a reused BlockPos), a following circle rides on its
 * caster (interpolated), drawn only in lit chunks (fog of war) and near the camera. At RTS zoom past 30 it grows with
 * the zoom (up to {@link #SCALE_MAX} x). Client only.
 */
@OnlyIn(Dist.CLIENT)
public final class RuneFxClient {
    private RuneFxClient() { }

    static final float DRAW_IN = 0.3f, FADE = 0.35f, SIGIL_DELAY = 0.08f;
    static final float RING_SPIN = 0.17f;
    static final int RING_SEG = 32, SIGIL_SEG = 16, TIER_SEG = 24, STROKE_SEG = 32, DARK_SEG = 24;
    /**
     * A ring's slices are sized on screen ({@link #segs}): as few as keep each chord within SAG_PX pixels of the true
     * circle, at least SEG_MIN, never more than asked. A circle 7 px across at zoom 200 needs 12, not 32; one 70 px
     * across (zoom 30 at a 900 px window) needs 21.
     */
    static final float SAG_PX = 0.4f;
    static final int SEG_MIN = 12;
    /**
     * The cost cuts (the zoom-30 budget, Eris.15): a wide soft stroke (the halo, the dark line: wider than SOFT_K core
     * widths) takes chords within SAG_SOFT_PX, its fading edges hide the corners; a stroke thinner than THIN_PX on
     * screen is one strip of the same light, not three (its two fading strips would be under a pixel); and a circle
     * off the screen is not drawn ({@link BarFxClient#onScreen}). Always on in play; the eyes scene turns them off on
     * half its slices to time the old path on the same runner.
     */
    static final float SAG_SOFT_PX = 1.0f, SOFT_K = 1.5f, THIN_PX = 1.75f;
    public static boolean lean = true;
    /** Slices over which the sweep's leading edge fades in. */
    static final int FEATHER = 3;
    /** The outer rim sits at this share of a ring cell's width. */
    static final float RIM_IN_CELL = 0.94f;
    /** The sigil over the head: its height in blocks (T1), its radius as a share of the circle's, rev/s. */
    static final float SIGIL_H = 2.45f, SIGIL_R = 0.38f, SIGIL_TURN = 0.25f;
    /** The seed at the feet: half size in blocks, life in s. The hold's breath: base, depth, Hz. */
    static final float SEED_HALF = 0.5f, SEED_T = 0.25f, BREATH = 0.9f, BREATH_DEPTH = 0.1f, BREATH_HZ = 2;
    /** The underlay's alpha: it fills the ring, in the faction's {@link #DARK}. */
    static final float UNDER_A = 0.2f;
    /**
     * The rim per faction (F_* id): its core (light enough to read on sunlit grass, never pure hue, never pure white:
     * overlapping rims summed to white in round 9; round 13: neutral's and Sunforged's cores blew out to white, so they
     * keep their hue; round 14: Sunforged's went cream and Gravebound's, Horde's and Verdant's rims clipped to white, so
     * they hold more of their hue too), its halo (the faction's hue), and the dark line under both.
     */
    static final int[] CORE = {0xDCE6FF, 0xFFDC70, 0xC4F850, 0xFF9068, 0x90F0A0, 0xC8FCFF, 0xE2D0FF, 0xFFC870};
    static final int[] HALO = {0x8FB0FF, 0xFFE070, 0x7CE000, 0xFF2A00, 0x28C850, 0x40E4FF, 0xA472FF, 0xD07818};
    static final int[] DARK = {0x202040, 0x3A1E00, 0x1A2A00, 0x3A0800, 0x0B3D1E, 0x002A3A, 0x1E0A40, 0x3A2400};
    /**
     * The rim core's half width: at least CORE_W blocks and CORE_PX screen pixels (RTS camera). The halo is HALO_K times
     * as wide, the dark line DARK_K times.
     */
    static final float CORE_W = 0.05f, CORE_PX = 2.0f, HALO_K = 2.7f, DARK_K = 2.2f;
    static final float CORE_A = 0.95f, HALO_A = 0.36f, DARK_A = 0.55f;
    /** The rim's core settles by SETTLE over the hold, from SETTLE_FROM s to SETTLE_TO s. */
    static final float SETTLE = 0.3f, SETTLE_FROM = 0.5f, SETTLE_TO = 1.3f;
    /** The hold's beat: from BEAT_FROM s, every BEAT_EVERY s the core brightens by BEAT, fading over BEAT_T s. */
    static final float BEAT_FROM = 0.5f, BEAT_EVERY = 0.4f, BEAT_T = 0.1f, BEAT = 0.25f;
    /** The underlay reaches this share of the rim's radius (its soft edge just past the rim). */
    static final float UNDER_R = 1.1f;
    /** The inner line: its radius as a share of the rim's, its width as a share of the core's, its alpha. */
    static final float INNER_R = 0.62f, INNER_W = 0.6f, INNER_A = 0.75f;
    /** The sigil's ring of light (in the faction's halo hue): its alpha as a share of the sigil's. */
    static final float SIGIL_RING_A = 0.6f;
    /** Blocks the ground layer is drawn toward the RTS camera (its orthographic view: only the depth changes). */
    static final float PULL = 1.0f;
    /**
     * On a hillside the ground under a circle rises above the caster's feet and hid its far side (round 10: half rings on
     * terraced ground). Each circle's pull grows by how far the ground under it rises ({@link #lift}), up to LIFT_MAX.
     */
    static final float LIFT_MAX = 3.0f;
    /** RTS zoom: the details fade out from LOD_FROM to LOD_TO. */
    static final float LOD_FROM = 28, LOD_TO = 40;
    /** Past zoom 30 the circle grows with the zoom, up to this. */
    static final float SCALE_MAX = 1.2f;
    static final float RING_A = 1.0f, SIGIL_A = 0.95f, FLASH_T = 0.1f;
    /** The white burst at the feet at the release: half size in blocks, life in s, alpha. */
    static final float BURST_HALF = 0.6f, BURST_T = 0.1f, BURST_A = 0.8f;
    /** The glyph riding the sweep's tip: half size in blocks. */
    static final float TIP_HALF = 0.16f;
    /**
     * Others within this many blocks dim a circle's light to 1 / sqrt(1 + n), not below CROWD_MIN (round 11: at .6 a
     * dense army's rings read as thin faint arcs; at .75 in round 9 they summed toward white). With CROWD_N or more
     * others that close, the circle also draws at CROWD_SHRINK of its radius (round 13: in a dense block the rings
     * stacked into one soup; not smaller, round 12 wanted the rim clear of the unit's base).
     */
    static final float CROWD_R = 3, CROWD_MIN = 0.78f, CROWD_SHRINK = 0.9f;
    /**
     * The halo thins further in a crowd: HALO_A times 1 - HALO_CROWD per other circle within CROWD_R, not below
     * HALO_CROWD_MIN (round 14: three or more stacked halos still summed to a white wash; the thin core keeps the shape).
     */
    static final float HALO_CROWD = 0.12f, HALO_CROWD_MIN = 0.45f;
    static final int CROWD_N = 3;
    /** How many circles may flash at full strength in one frame; past it every flash is dimmed to share that much. */
    static final float FLASH_CAP = 3;
    /** The rim thrown out at the release: from this share of the circle's radius to PULSE_TO over PULSE_T s. */
    static final float PULSE_FROM = 1.0f, PULSE_TO = 1.35f, PULSE_T = 0.2f;
    /** The waist band: height in blocks (T1), radius as a share of the circle's, start time, alpha, rev/s. */
    static final float TIER_H = 0.8f, TIER_R = 0.62f, TIER_AT = 0.3f, TIER_A = 0.7f, TIER_SPIN = -0.2f;
    static final float TIER_RISE = 0.12f;
    static final float PILLAR_FROM = 2.0f, PILLAR_H = 2.5f, PILLAR_HW = 0.125f, PILLAR_A = 0.5f, PILLAR_T = 0.12f;
    /** Motes per circle (round 13: 12 cluttered a dense army). */
    static final int MOTES = 8;
    /** Motes run this much faster than the cast's clock (short lives). */
    static final float MOTE_SPEED = 2;
    static final float MOTE_A = 1.0f, MOTE_HALF = 0.26f, RISE = 1.5f;
    /**
     * Brassworks' cogs: this many turning round the rim just over the ground (blocks, T1), at a share of the radius, rev/s,
     * half size (round 13: ten at head height drifted over the neighbours; round 14: at chest height they still read as
     * confetti off the ring, so they sit on it, evenly spaced, like a mechanism).
     */
    static final int COGS = 6;
    static final float COG_H = 0.3f, COG_R = 0.95f, COG_SPIN = 0.6f, COG_HALF = 0.216f;

    // the atlas: 2048 x 1152. Per faction column of 256: ring (y 0), sigil (256), 16 glyphs of 64 (512), the glyph band
    // tier (768), then at y 1024 the plain tier ring (128) | mote, beam over spark, shade (64 each)
    static final float AW = 2048f, AH = 1152f;
    static final float CELL_U = 256f / AW, CELL_V = 256f / AH, GLYPH_U = 64f / AW, GLYPH_V = 64f / AH;
    static final float V_SIGIL = 256f / AH, V_GLYPH = 512f / AH, V_BAND = 768f / AH, V_FX = 1024f / AH;
    /** Half a texel: sprites stop this far inside their cell so linear filtering never reaches the neighbour's. */
    static final float EU = 0.5f / AW, EV = 0.5f / AH;
    /** A texel of plain white (the middle of the shade disc): what the strokes are drawn with. */
    static final float WHITE_U = 3.5f * GLYPH_U, WHITE_V = V_FX + 1.5f * GLYPH_V;

    static final RunePool POOL = new RunePool();
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
    private static final boolean[] SHOWN = new boolean[RunePool.MAX];
    private static final float[] RADIUS = new float[RunePool.MAX], CROWD = new float[RunePool.MAX];
    private static final float[] HALO_C = new float[RunePool.MAX];   // each circle's halo share, thinned in a crowd
    private static final float[] PULLD = new float[RunePool.MAX];   // each circle's pull toward the camera, blocks
    private static float flashScale = 1, sweepStart = (float) (-Math.PI / 2);
    // this frame's: detail (1 close .. 0 far), the rim core's half width, blocks per screen pixel
    private static float near = 1, wc = CORE_W, bpp;
    private static float mx, my, mz, ma;   // mote()'s answer
    private static long renderNanos = 0, renderFrames = 0, vertices = 0;
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

    /** The effects' clock, seconds (BarFxClient's: held back on a slow frame): the eyes scene casts by it. */
    public static float clock() {
        return BarFxClient.clock;
    }

    /** Circles held now (the eyes scene logs it). */
    public static int live() {
        return POOL.count();
    }

    /** Lifetime totals for the eyes scene's cost line: nanoseconds spent drawing, frames drawn, most circles held. */
    public static long renderNanos() { return renderNanos; }
    public static long renderFrames() { return renderFrames; }
    /** Lifetime vertices written (a runner's speed does not change it). */
    public static long vertices() { return vertices; }
    public static int peak() { return peak; }

    /** The circles the ground pass drew this frame, and its clock: the air pass draws on the same state. */
    private static boolean prepared = false;
    private static float frameClock;
    private static int shown = 0;

    /** This frame's shared state: the zoom's level of detail and widths, which circles show, their crowd dimming. */
    private static int prepare(float clock, float partialTick) {
        POOL.prune(clock);
        shown = 0;
        if (POOL.count == 0)
            return 0;
        Minecraft mc = Minecraft.getInstance();
        boolean ortho = BarFxClient.ortho;
        float scale = ortho ? Math.max(1, Math.min(SCALE_MAX, BarFxClient.zoom / 30f)) : 1;
        near = ortho ? Math.max(0, Math.min(1, (LOD_TO - BarFxClient.zoom) / (LOD_TO - LOD_FROM))) : 1;
        bpp = ortho ? BarFxClient.zoom / Math.max(1, mc.getWindow().getHeight()) : 0;   // blocks per pixel
        wc = Math.max(CORE_W, CORE_PX * bpp);
        int flashing = 0;
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
            boolean seen = lean ? BarFxClient.onScreen(POOL.x[i], POOL.y[i], POOL.z[i], R * PULSE_TO + 3)
                : BarFxClient.near(POOL.x[i], POOL.y[i], POOL.z[i], R + 3);
            if (!seen || !lit(POOL.x[i], POOL.y[i], POOL.z[i]))
                continue;
            SHOWN[i] = true;
            shown++;
            PULLD[i] = ortho ? PULL + lift(mc, POOL.x[i], POOL.y[i], POOL.z[i], R) : 0;
            float rel = clock - POOL.born[i] - DRAW_IN;
            if (rel >= 0 && rel < FLASH_T)
                flashing++;
        }
        if (shown > 0) {   // (the crowd and the flash share)
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
                HALO_C[i] = Math.max(HALO_CROWD_MIN, 1 - HALO_CROWD * n);
                if (n >= CROWD_N)
                    RADIUS[i] *= CROWD_SHRINK;
            }
        }
        return shown;
    }

    /**
     * The ground layer, before the entities (RenderLevelStageEvent AFTER_CUTOUT_BLOCKS): the underlays in one batch, then
     * the rims' light in one additive batch. Units drawn after it stand over their circle (round 9: rings crossing the
     * units' bodies broke the ground); the pull still keeps it over the grass.
     */
    static void renderGround(MultiBufferSource.BufferSource buffers, Matrix4f mat, float clock, float partialTick) {
        prepared = false;
        long t0 = System.nanoTime();
        if (prepare(clock, partialTick) > 0) {
            VertexConsumer sh = buffers.getBuffer(BarFxRenderTypes.RUNES_SHADE);
            for (int i = 0; i < POOL.count; i++)
                if (SHOWN[i])
                    underlay(sh, mat, i, clock, RADIUS[i]);
            buffers.endBatch(BarFxRenderTypes.RUNES_SHADE);
            VertexConsumer vc = buffers.getBuffer(BarFxRenderTypes.RUNES);
            for (int i = 0; i < POOL.count; i++)
                if (SHOWN[i])
                    ground(vc, mat, i, clock, RADIUS[i]);
            buffers.endBatch(BarFxRenderTypes.RUNES);
        }
        prepared = true;
        frameClock = clock;
        renderNanos += System.nanoTime() - t0;
    }

    /**
     * The light above the ground, after the domes (AFTER_TRANSLUCENT_BLOCKS): seed, tip glyph, burst, waist band, sigil,
     * pillar, motes. On the ground pass's frame and clock; when that pass did not run, the whole circle here.
     */
    static void render(MultiBufferSource.BufferSource buffers, Matrix4f mat, float clock, float partialTick) {
        boolean whole = !prepared;
        if (prepared)
            clock = frameClock;
        prepared = false;
        long t0 = System.nanoTime();
        if (whole)
            prepare(clock, partialTick);
        if (POOL.count == 0)
            return;
        if (shown > 0) {
            if (whole) {
                VertexConsumer sh = buffers.getBuffer(BarFxRenderTypes.RUNES_SHADE);
                for (int i = 0; i < POOL.count; i++)
                    if (SHOWN[i])
                        underlay(sh, mat, i, clock, RADIUS[i]);
                buffers.endBatch(BarFxRenderTypes.RUNES_SHADE);
            }
            VertexConsumer vc = buffers.getBuffer(BarFxRenderTypes.RUNES);
            for (int i = 0; i < POOL.count; i++) {
                if (!SHOWN[i])
                    continue;
                if (whole)
                    ground(vc, mat, i, clock, RADIUS[i]);
                air(vc, mat, i, clock, RADIUS[i]);
            }
            buffers.endBatch(BarFxRenderTypes.RUNES);
        }
        renderNanos += System.nanoTime() - t0;
        renderFrames++;
    }

    /**
     * How far the ground under a circle of radius {@code r} at (x, y, z) rises above y (the caster's feet): the top of
     * the motion-blocking column at the centre and eight points round the rim, at most {@link #LIFT_MAX}.
     */
    static float lift(Minecraft mc, float x, float y, float z, float r) {
        if (mc.level == null)
            return 0;
        int top = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
        for (int k = 0; k < 8; k++) {
            double a = Math.PI / 4 * k;
            top = Math.max(top, mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING,
                (int) Math.floor(x + r * Math.cos(a)), (int) Math.floor(z + r * Math.sin(a))));
        }
        return Math.max(0, Math.min(LIFT_MAX, top - y));
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
        if (s <= 0.01f)
            return;
        float rr = R * (1 + 0.12f * ending(i, clock, s));
        float reveal = Math.min(1, (clock - POOL.born[i]) / DRAW_IN);
        int dk = DARK[f];
        float d = PULLD[i];
        float gx = POOL.x[i] - BarFxClient.lookX * d, gy = POOL.y[i] + 0.04f - BarFxClient.lookY * d,
            gz = POOL.z[i] - BarFxClient.lookZ * d;
        fan(vc, m, gx, gy, gz, rr * UNDER_R, 16, reveal, sweepStart, FEATHER, 0, f * CELL_U + 3.5f * GLYPH_U, V_FX + 1.5f * GLYPH_V,
            GLYPH_U / 2 - EU, GLYPH_V / 2 - EV, ((dk >> 16) & 255) / 255f, ((dk >> 8) & 255) / 255f, (dk & 255) / 255f,
            UNDER_A * s);
        stroke(vc, m, gx, gy + 0.01f, gz, rr, wc * DARK_K, DARK_SEG, reveal, DARK[f], DARK_A * s);
    }

    private static void ground(VertexConsumer vc, Matrix4f m, int i, float clock, float R) {
        float t = clock - POOL.born[i];
        float s = strength(i, clock);
        if (s <= 0.01f)
            return;
        float sl = s * CROWD[i];   // the light, dimmed in a crowd
        float ending = ending(i, clock, s);
        float grow = 1 + 0.12f * ending;
        float breath = BREATH + BREATH_DEPTH * (float) Math.sin(t * 2 * Math.PI * BREATH_HZ);
        float rel = t - DRAW_IN;
        float flash = rel >= 0 ? flashScale * Math.max(0, 1 - rel / FLASH_T) : 0;
        float cx = POOL.x[i], cy = POOL.y[i] + 0.05f, cz = POOL.z[i];
        int f = POOL.style[i];
        float u0 = f * CELL_U;
        float tau = 6.2831855f;
        // the ground layer, pulled toward the RTS camera: the atlas ring (close only), the rim's halo and core
        float rr = R * grow, reveal = Math.min(1, t / DRAW_IN);
        float d = PULLD[i];
        float gx = cx - BarFxClient.lookX * d, gy = cy - BarFxClient.lookY * d, gz = cz - BarFxClient.lookZ * d;
        if (near > 0.01f)
            fan(vc, m, gx, gy, gz, rr / RIM_IN_CELL, RING_SEG, reveal, sweepStart, FEATHER, t * RING_SPIN * tau,
                u0 + CELL_U / 2, CELL_V / 2, CELL_U / 2, CELL_V / 2, 1, 1, 1, RING_A * sl * breath * near);
        stroke(vc, m, gx, gy, gz, rr, wc * HALO_K, STROKE_SEG, reveal, HALO[f],
            HALO_A * HALO_C[i] * sl * (1 + 0.5f * flash));
        float settle = 1 - SETTLE * Math.max(0, Math.min(1, (t - SETTLE_FROM) / (SETTLE_TO - SETTLE_FROM)));
        if (t > BEAT_FROM)   // the hold's beat: the hold is never still
            settle *= 1 + BEAT * Math.max(0, 1 - frac((t - BEAT_FROM) / BEAT_EVERY) * BEAT_EVERY / BEAT_T);
        stroke(vc, m, gx, gy, gz, rr, wc, STROKE_SEG, reveal, CORE[f],
            Math.min(1, CORE_A * sl * breath * settle + 0.5f * flash * sl));
        if (near > 0.01f)   // the inner line: the second weight under the rim
            stroke(vc, m, gx, gy, gz, rr * INNER_R, wc * INNER_W, STROKE_SEG, reveal, CORE[f], INNER_A * sl * near);
        if (rel >= 0 && rel < PULSE_T) {   // the release: the rim thrown out, the moment read at any zoom
            float q = rel / PULSE_T;
            float rp = rr * (PULSE_FROM + (PULSE_TO - PULSE_FROM) * ease(q));
            stroke(vc, m, gx, gy + 0.02f, gz, rp, wc * HALO_K, STROKE_SEG, 1, HALO[f], HALO_A * HALO_C[i] * (1 - q) * sl);
            stroke(vc, m, gx, gy + 0.02f, gz, rp, wc, STROKE_SEG, 1, CORE[f], (1 - q) * sl);
        }
    }

    private static void air(VertexConsumer vc, Matrix4f m, int i, float clock, float R) {
        float t = clock - POOL.born[i];
        float s = strength(i, clock);
        if (s <= 0.01f)
            return;
        float sl = s * CROWD[i];   // the light, dimmed in a crowd
        float sc = R / RuneFx.CAST_RADIUS;
        float ending = ending(i, clock, s);
        float grow = 1 + 0.12f * ending;
        float rel = t - DRAW_IN;
        float cx = POOL.x[i], cy = POOL.y[i] + 0.05f, cz = POOL.z[i];
        int f = POOL.style[i];
        float u0 = f * CELL_U;
        float tau = 6.2831855f;
        float rr = R * grow, reveal = Math.min(1, t / DRAW_IN);
        if (t < SEED_T)   // the seed: light gathers at the feet the moment the cast starts
            billboard(vc, m, cx, cy + 0.15f, cz, SEED_HALF * Math.max(1, sc), u0 + 2 * GLYPH_U, V_FX + GLYPH_V, 1 - t / SEED_T);
        if (t < DRAW_IN && near > 0.01f) {   // the glyph riding the sweep's tip: the circle is being written
            double a = sweepStart + reveal * tau;
            int g = POOL.id[i] & 15;
            billboard(vc, m, cx + (float) Math.cos(a) * rr * 0.92f, cy + 0.25f, cz + (float) Math.sin(a) * rr * 0.92f,
                TIP_HALF * Math.max(1, sc), u0 + (g & 3) * GLYPH_U, V_GLYPH + (g >> 2) * GLYPH_V, sl * near);
        }
        if (rel >= 0 && rel < BURST_T)   // and a white burst at the feet
            billboard(vc, m, cx, cy + 0.15f, cz, BURST_HALF * Math.max(1, sc), u0 + 2 * GLYPH_U, V_FX + GLYPH_V,
                BURST_A * flashScale * (1 - rel / BURST_T));
        if (near <= 0.01f)   // far: a few pixels wide, the rim says it all
            return;
        // the waist band, sweeping up after the release
        float rise = ease((t - TIER_AT) / TIER_RISE);
        if (TIER_A * sl * rise * near > 0.01f)
            disc(vc, m, cx, cy + TIER_H * sc * rise, cz, R * TIER_R * grow / RIM_IN_CELL, TIER_SEG, t * TIER_SPIN * tau,
                u0 + CELL_U / 2, V_BAND + CELL_V / 2, CELL_U / 2, CELL_V / 2, TIER_A * sl * rise * near);
        // the faction's sigil over the head: grows in with the draw-in, spins, shrinks away with the fade
        float ks = ease((t - SIGIL_DELAY) / 0.22f) * Math.max(0, Math.min(1, (POOL.life[i] - t) / 0.4f));
        if (ks > 0.01f) {   // in a ring of its own light
            float sa = SIGIL_A * sl * Math.min(1, ks * 1.5f) * near, sr = R * SIGIL_R * ks;
            disc(vc, m, cx, cy + SIGIL_H * sc, cz, sr / RIM_IN_CELL, SIGIL_SEG, t * SIGIL_TURN * tau,
                u0 + CELL_U / 2, V_SIGIL + CELL_V / 2, CELL_U / 2, CELL_V / 2, sa);
            stroke(vc, m, cx, cy + SIGIL_H * sc, cz, sr * 1.04f, wc * 0.8f, SIGIL_SEG, 1, HALO[f], sa * SIGIL_RING_A);
        }
        // the column of light from the head, at the release only
        if (rel >= 0 && rel < PILLAR_T) {
            float pa = PILLAR_A * flashScale * sl * (1 - rel / PILLAR_T) * near;
            pillar(vc, m, cx, cy + PILLAR_FROM * sc, cz, PILLAR_H * sc, PILLAR_HW * sc, u0 + 3 * GLYPH_U, V_FX, pa);
        }
        // motes, each faction its own way, short lived; Brassworks' cogs turn round the rim instead
        float mA = sl * Math.max(0, Math.min(1, (t - 0.2f) / 0.2f)) * MOTE_A * near;
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
     * cogs are air()'s own).
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
                r = R * (0.35f + 0.6f * u);
                y = sc * (2.6f * u - 2.0f * u * u) + 0.1f;
            }
            case 4 -> {   // Verdant: petals on a wide slow spiral
                u = frac(t * 0.45f + ph);
                turn = ph + t * 0.25f + 0.4f * u;
                r = R * (0.65f + 0.25f * (float) Math.sin(6.2831855f * (u + ph)));
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
        seg = segs(r, seg);
        float litF = reveal * (seg + feather);
        int lit = Math.max(1, Math.min(seg, (int) Math.ceil(litF)));
        double step = Math.PI * 2 / seg;
        double cs = Math.cos(step), sn = Math.sin(step), cp = Math.cos(spin), sp = Math.sin(spin);
        double c0 = Math.cos(start), s0 = Math.sin(start);   // turned by one slice each step: no trig per slice
        for (int j = 0; j < lit; j++) {
            double c1 = c0 * cs - s0 * sn, s1 = s0 * cs + c0 * sn;
            float aj = feather > 0 ? a * Math.max(0, Math.min(1, (litF - j) / feather)) : a;
            if (aj > 0.004f) {   // (t - spin) by the angle-difference identities
                float k0 = (float) (c0 * cp + s0 * sp), l0 = (float) (s0 * cp - c0 * sp);
                float k1 = (float) (c1 * cp + s1 * sp), l1 = (float) (s1 * cp - c1 * sp);
                tv(vc, m, cx, cy, cz, cr, cg, cb, aj, uc, vc0);
                tv(vc, m, cx + r * (float) c0, cy, cz + r * (float) s0, cr, cg, cb, aj, uc + hu * k0, vc0 + hv * l0);
                tv(vc, m, cx + r * (float) c1, cy, cz + r * (float) s1, cr, cg, cb, aj, uc + hu * k1, vc0 + hv * l1);
                tv(vc, m, cx, cy, cz, cr, cg, cb, aj, uc, vc0);
            }
            c0 = c1;
            s0 = s1;
        }
    }

    /**
     * A flat ring of light of radius {@code r} at height {@code cy}, full within w / 2 of it and fading to nothing at w
     * either side (three quad strips, plain white texel, vertex colour {@code rgb}): geometry, so its width is whatever
     * the zoom needs. Swept in as {@link #fan}, from {@link #sweepStart}.
     */
    private static void stroke(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz, float r, float w, int seg,
                               float reveal, int rgb, float a) {
        if (a <= 0.01f)
            return;
        boolean soft = lean && w > wc * SOFT_K, thin = lean && bpp > 0 && w < THIN_PX * bpp;
        seg = segs(r + w, seg, soft ? SAG_SOFT_PX : SAG_PX);
        float cr = ((rgb >> 16) & 255) / 255f, cg = ((rgb >> 8) & 255) / 255f, cb = (rgb & 255) / 255f;
        float r0 = Math.max(0, r - w), r1 = Math.max(0, r - w / 2), r2 = r + w / 2, r3 = r + w;
        if (thin) {   // one strip carrying the three's light: full over 1.5 w
            r1 = Math.max(0, r - 0.75f * w);
            r2 = r + 0.75f * w;
        }
        float litF = reveal * (seg + FEATHER);
        int lit = Math.max(1, Math.min(seg, (int) Math.ceil(litF)));
        double step = Math.PI * 2 / seg;
        double cs = Math.cos(step), sn = Math.sin(step);
        double c0 = Math.cos(sweepStart), s0 = Math.sin(sweepStart);   // turned by one slice each step, as fan()
        for (int j = 0; j < lit; j++) {
            double c1 = c0 * cs - s0 * sn, s1 = s0 * cs + c0 * sn;
            float aj = a * Math.max(0, Math.min(1, (litF - j) / FEATHER));
            if (aj > 0.004f) {
                float a0 = (float) c0, b0 = (float) s0, a1 = (float) c1, b1 = (float) s1;
                if (!thin)
                    strip(vc, m, cx, cy, cz, a0, b0, a1, b1, r0, 0, r1, aj, cr, cg, cb);
                strip(vc, m, cx, cy, cz, a0, b0, a1, b1, r1, aj, r2, aj, cr, cg, cb);
                if (!thin)
                    strip(vc, m, cx, cy, cz, a0, b0, a1, b1, r2, aj, r3, 0, cr, cg, cb);
            }
            c0 = c1;
            s0 = s1;
        }
    }

    /**
     * The slices for a ring of radius {@code r} (blocks): {@code seg} at most, fewer when it is small on screen. A chord
     * of a slice of angle 2 pi / n sags r (1 - cos(pi / n)) inside the circle; n is the least that keeps that within
     * {@link #SAG_PX}.
     */
    static int segs(float r, int seg) {
        return segs(r, seg, SAG_PX);
    }

    /** {@link #segs(float, int)} with {@code sagPx} pixels of sag allowed. */
    static int segs(float r, int seg, float sagPx) {
        if (bpp <= 0 || r <= 0)
            return seg;
        float sag = sagPx * bpp / r;   // the sag allowed, as a share of the radius
        if (sag >= 1)
            return Math.min(seg, SEG_MIN);
        int want = (int) Math.ceil(Math.PI / Math.acos(1 - sag));
        return Math.min(seg, Math.max(SEG_MIN, want));
    }

    /** One quad of a ring between radii ra (alpha aa) and rb (alpha ab), from angle (c0, s0) to (c1, s1). */
    private static void strip(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz, float c0, float s0, float c1,
                              float s1, float ra, float aa, float rb, float ab, float cr, float cg, float cb) {
        tv(vc, m, cx + ra * c0, cy, cz + ra * s0, cr, cg, cb, aa, WHITE_U, WHITE_V);
        tv(vc, m, cx + rb * c0, cy, cz + rb * s0, cr, cg, cb, ab, WHITE_U, WHITE_V);
        tv(vc, m, cx + rb * c1, cy, cz + rb * s1, cr, cg, cb, ab, WHITE_U, WHITE_V);
        tv(vc, m, cx + ra * c1, cy, cz + ra * s1, cr, cg, cb, aa, WHITE_U, WHITE_V);
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

    /**
     * One vertex, camera-relative, through the pose {@code m} by hand: {@code vertex(Matrix4f, ...)} makes a Vector4f
     * per vertex, and a dense army of circles is tens of thousands of vertices a frame.
     */
    private static void tv(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float g, float b, float a,
                           float u, float v) {
        float lx = x - BarFxClient.camX, ly = y - BarFxClient.camY, lz = z - BarFxClient.camZ;
        vertices++;
        vc.vertex(m.m00() * lx + m.m10() * ly + m.m20() * lz + m.m30(),
                m.m01() * lx + m.m11() * ly + m.m21() * lz + m.m31(),
                m.m02() * lx + m.m12() * ly + m.m22() * lz + m.m32())
            .color(r, g, b, Math.max(0, Math.min(1, a))).uv(u, v).endVertex();
    }
}
