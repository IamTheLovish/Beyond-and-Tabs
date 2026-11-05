package com.solegendary.reignofnether.barfx;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The forming moment on the client (barfx/PortalForming sends one FORMING event per activation; lovish, Nov 5: "our
 * own version" of a portal that forms instead of switching on). Each live forming plays {@link FormingTimeline}
 * with BarFx's own light pieces (flashes, sparks, a beam, rings) and, for portals, vanilla's portal particles:
 * <ul>
 *   <li>spark: two lights climb the frame's pillars from its feet and meet in the lintel's middle, trailing and
 *       shedding sparks (a game ender: four climb its corners); a wide ring on the ground;</li>
 *   <li>fill: the doorway lights row by row from the sill, violet to white, portal particles streaming in from the
 *       frame (an ender: rings climb the tower); a faint pillar rises through it from 0.35 s;</li>
 *   <li>swirl: two strands spiral up through the doorway, tightening, four flat rings lighting up the pillar;</li>
 *   <li>settle: the brightest moment - the doorway opens in a ring of light, a full pillar, a white-hot pulse, portal
 *       particles pulled in, a ground ring a beat later, and embers thrown up from the lintel that fall.</li>
 * </ul>
 * One brightness order throughout: the pillar and pulse, then the rings and traced lines, then dim sparkle. A portal
 * forms in nether violet and throws lava-orange embers (the lit portal it becomes); a game ender in its faction's tint.
 * Pieces are spawned only while the site is near the camera (the timeline's running totals keep the count right
 * however the frames fall), and an entry is dropped the moment the timeline is done: nothing ticks or stays behind.
 * Client only.
 */
@OnlyIn(Dist.CLIENT)
final class FormingFx {
    private FormingFx() { }

    /** At most this many formings at once (a whole base of portals finishing together still costs a bounded amount). */
    static final int MAX = 24;

    /** A portal's violet (rim, core) and lava embers; the game enders' faction tints by BarFx.F_*. */
    static final int PORTAL_RIM = 0xA24BFF, PORTAL_CORE = 0xF2E4FF, PORTAL_EMBER = 0xFF9A3C;
    static final int[] ENDER_TINT = { 0xF0E2B0, 0xFFD96A, 0x5FE6F0, 0xFF8A2E, 0x7CE07A, 0x3CC8BE, 0x4FD1A0, 0xE0A040 };

    static final class F {
        final float x, y, z, half, height, scale, born;
        final int rim, core, ember;
        final boolean portal;
        /** A portal's doorway (PortalForming.doorway, or one of its own): runs along x?, half-width, centre, sill, top. */
        final boolean alongX;
        final float dw, dx, dz, dy0, dy1;
        float done = 0;   // seconds of the timeline already emitted

        F(float x, float y, float z, float half, float height, int style, int faction, float doorway, float born) {
            this.x = x; this.y = y; this.z = z; this.half = half; this.height = height; this.born = born;
            this.scale = FormingTimeline.scaleFor(half);
            this.portal = style != BarFx.FS_ENDER;
            int tint = ENDER_TINT[Math.max(0, Math.min(ENDER_TINT.length - 1, faction))];
            this.rim = portal ? PORTAL_RIM : tint;
            this.core = portal ? PORTAL_CORE : BarFxClient.lighter(tint, .55f);
            this.ember = portal ? PORTAL_EMBER : BarFxClient.lighter(tint, .2f);
            float[] d = portal ? PortalForming.doorwayOf(doorway) : null;
            if (d == null)   // no NETHER_PORTAL blocks (an ender, a custom portal): a doorway in the middle of its own
                d = new float[] { 1, Math.max(1, Math.min(2, half * .4f)), 0, 0, 1, 1 + Math.min(4, Math.max(1, height - 1)) };
            this.alongX = d[0] > .5f;
            this.dw = d[1];
            this.dx = x + (alongX ? d[2] : d[3]);
            this.dz = z + (alongX ? d[3] : d[2]);
            this.dy0 = (float) Math.floor(y) + d[4];
            this.dy1 = (float) Math.floor(y) + d[5];
        }

        /** A point in the doorway's plane: {@code a} along the door from its centre, at height {@code py}. */
        float[] door(float a, float py) {
            return alongX ? new float[] { dx + a, py, dz } : new float[] { dx, py, dz + a };
        }
    }

    private static final List<F> LIVE = new ArrayList<>();
    /** Formings received since the client started (never reset): the eyes scene waits for the next one by it. */
    private static int started = 0;

    static int started() {
        return started;
    }

    static boolean isEmpty() {
        return LIVE.isEmpty();
    }

    static int count() {
        return LIVE.size();
    }

    static void clear() {
        LIVE.clear();
    }

    /** Seconds since the newest forming began, or -1 with none live (the eyes scene times its stills by it). */
    static float newestAge(float clock) {
        return LIVE.isEmpty() ? -1 : clock - LIVE.get(LIVE.size() - 1).born;
    }

    static void ingest(BarFx.Event e, float clock) {
        if (LIVE.size() >= MAX)
            return;
        started++;
        LIVE.add(new F(e.x, e.y, e.z, Math.max(1, e.a), Math.max(1, e.b), e.kind, e.flags & BarFx.FACTION_MASK, e.c, clock));
    }

    /** Once per frame: emit what each forming's timeline owes since the last frame, drop the finished ones. */
    static void step(float clock) {
        for (Iterator<F> it = LIVE.iterator(); it.hasNext(); ) {
            F f = it.next();
            float t = Math.min(clock - f.born, FormingTimeline.DURATION);
            if (t > f.done) {
                if (BarFxClient.near(f.x, f.y, f.z, f.half + 8))
                    emit(f, f.done, t);
                f.done = t;
            }
            if (FormingTimeline.done(clock - f.born))
                it.remove();
        }
    }

    /*
     * The look, by the Fable critic's rounds (Nov 5; round 1 shape 3 - "equal-weight scattered particles"; round 2
     * shape 4, readability 3 - "the spark is not on the frame", "the settle is a hairline", "nothing survives far
     * zoom"; round 3 shape 6, timing 6, readability 4 - "one vertical light pillar through the doorway that persists
     * across all phases, thin and dim at the spark and wide and white at the settle, with flat rings climbing it";
     * round 4 lost the blind A/B to round 3 - the pillar was white from 0.6 s, so the swirl outshone the settle: the
     * brightness order is the rule - build-up at half strength or less, full strength only at the arrival).
     * Pieces are placed by their index in the phase, and the pillar and rings on fixed moments of the timeline, never
     * per frame, so the shape and brightness are the same at any frame rate.
     */
    private static void emit(F f, float t0, float t1) {
        float size = (float) Math.sqrt(f.scale);
        var level = Minecraft.getInstance().level;
        boolean vanilla = level != null && f.portal;
        float h = f.portal ? f.dy1 + 1 - f.y : f.height;   // a portal's frame to its lintel; an ender's whole tower
        float top = f.y + h;
        float cx = f.portal ? f.dx : f.x, cz = f.portal ? f.dz : f.z, foot = f.portal ? f.dy0 : top;
        // 0. the pillar: a segment every 0.1 s from 0.35 s to the settle, a thread at ~40% growing wider - never as
        //    bright as the settle's own pillar
        for (int j = 4; j <= 12; j++) {
            float at = j * .1f - .05f;
            if (t0 < at && at <= t1) {
                float g = (at - .35f) / .95f;
                BarFxClient.P seg = BarFxClient.add(BarFxClient.BEAM, cx, foot, cz, .14f, .25f + .45f * g,
                    BarFxClient.scale(f.rim, .4f));
                seg.x2 = cx; seg.y2 = top + 10; seg.z2 = cz; seg.steady = true;   // steady: no fade flicker between segments
            }
        }
        if (t0 < .2f && .2f <= t1)   // the spark's first light reaches the ground: one wide ring, the far zoom's cue
            BarFxClient.ring(f.x, f.y, f.z, 7, .4f, BarFxClient.mix(f.rim, f.core, .5f));
        // ...and four flat rings lighting up it, bottom to top, through the swirl
        for (int j = 0; j < 4; j++) {
            float at = .65f + .2f * j;
            if (t0 < at && at <= t1) {
                float ry = f.portal ? f.dy0 + .3f + j * (f.dy1 + 1 - f.dy0) / 3f : f.y + h * (j + 1) / 4f;
                BarFxClient.ring(cx, ry, cz, f.portal ? 1.6f : f.half * 1.1f + .5f, .5f,
                    BarFxClient.scale(BarFxClient.mix(f.rim, f.core, j / 3f), .75f));
            }
        }
        if (t0 < 1.4f && 1.4f <= t1)   // the ground ring, a beat after the doorway opens
            BarFxClient.ring(cx, f.portal ? f.dy0 : top, cz, 3, .3f, 0xFFFFFF);
        if (t0 < 1.5f && 1.5f <= t1)   // and a lavender after-ring a block up
            BarFxClient.ring(cx, (f.portal ? f.dy0 : top) + 1, cz, 2, .35f, f.rim);
        // 1. spark
        int before = FormingTimeline.emitted(FormingTimeline.Phase.SPARK, t0, f.scale);
        int n = FormingTimeline.between(FormingTimeline.Phase.SPARK, t0, t1, f.scale);
        float total = Math.max(1, FormingTimeline.emitted(FormingTimeline.Phase.SPARK, FormingTimeline.DURATION, f.scale));
        for (int i = 0; i < n; i++) {
            int k = before + i;
            float u = Math.min(1, (k + .5f) / total);
            float[] p = walker(f, k, u);
            BarFxClient.flash(p[0], p[1], p[2], .55f * size, .25f, BarFxClient.lighter(f.core, .5f));
            for (int j = 1; j <= 2; j++) {   // the trail, dimmer behind it
                float[] q = walker(f, k, Math.max(0, u - .035f * j));
                BarFxClient.flash(q[0], q[1], q[2], (.4f - .08f * j) * size, .2f, f.rim);
            }
            BarFxClient.sparks(p[0], p[1], p[2], 2, 1.4f, f.core, 0, 0);
            if (f.portal && k + 1 >= (int) total) {   // the two meet at the lintel's middle: the hand-off to the fill
                float[] m = f.door(0, f.dy1 + .5f);
                BarFxClient.flash(m[0], m[1], m[2], 1.2f, .3f, BarFxClient.lighter(f.core, .5f));
            }
        }
        // 2. fill
        before = FormingTimeline.emitted(FormingTimeline.Phase.FILL, t0, f.scale);
        n = FormingTimeline.between(FormingTimeline.Phase.FILL, t0, t1, f.scale);
        for (int i = 0; i < n; i++) {
            int k = before + i;
            float tk = t0 + (i + .5f) / n * (t1 - t0);
            float glow = 1 - FormingTimeline.fillFront(tk);   // 0 at the frame's first light, 1 when the door is full
            if (f.portal) {   // the doorway fills row by row from the sill, lavender at the bottom, white at the top
                int rows = Math.max(1, Math.round(f.dy1 - f.dy0)), row = Math.min(rows - 1, (int) (glow * rows));
                int fill = (k / 2) % (row + 1);   // every lit row keeps glowing while the next one comes
                float a = -f.dw + (k % 2 + .5f) * f.dw, py = f.dy0 + fill + .5f;
                float[] p = f.door(a, py);
                BarFxClient.flash(p[0], p[1], p[2], .8f * size, .3f,
                    BarFxClient.scale(BarFxClient.mix(f.rim, f.core, (fill + 1f) / rows), .7f));
                if (vanilla && k % 2 == 0) {   // vanilla's portal particle travels from the offset to the given spot
                    float[] edge = f.door(k % 4 < 2 ? -f.dw - .5f : f.dw + .5f, py);
                    float[] mid = f.door(0, (f.dy0 + f.dy1) * .5f);
                    level.addParticle(ParticleTypes.PORTAL, mid[0], mid[1], mid[2], edge[0] - mid[0], edge[1] - mid[1], edge[2] - mid[2]);
                }
            } else {   // an ender: rings of light climb the tower
                float step = (float) Math.floor(glow * 7) / 7f;
                float[] q = onSquare(f, f.half * (.4f + .6f * (1 - step)), frac(k * .618034f));
                BarFxClient.flash(q[0], f.y + .4f + h * step, q[1], (.5f + .35f * step) * size, .35f, BarFxClient.mix(f.rim, f.core, step));
            }
        }
        // 3. swirl
        before = FormingTimeline.emitted(FormingTimeline.Phase.SWIRL, t0, f.scale);
        n = FormingTimeline.between(FormingTimeline.Phase.SWIRL, t0, t1, f.scale);
        float y0 = f.portal ? f.dy0 : f.y + .3f;
        float span = (f.portal ? f.dy1 - f.dy0 : h) + 2f, reach = f.portal ? Math.max(1.5f, f.dw + .5f) : Math.max(1.5f, f.half);
        for (int i = 0; i < n; i++) {
            int k = before + i;
            float tk = t0 + (i + .5f) / n * (t1 - t0);
            float rise = FormingTimeline.progress(FormingTimeline.Phase.SWIRL, tk);
            float rad = FormingTimeline.swirlRadius(tk) * reach, py = y0 + rise * span;
            double a = FormingTimeline.swirlAngle(tk) + (k % 2) * Math.PI;
            float px = cx + (float) Math.cos(a) * rad, pz = cz + (float) Math.sin(a) * rad;
            BarFxClient.flash(px, py, pz, .3f * size, .35f, k % 2 == 0 ? f.core : f.rim);
            if (vanilla && k % 3 == 0)
                level.addParticle(ParticleTypes.REVERSE_PORTAL, px, py, pz, 0, .03, 0);
        }
        // 4. settle
        before = FormingTimeline.emitted(FormingTimeline.Phase.SETTLE, t0, f.scale);
        n = FormingTimeline.between(FormingTimeline.Phase.SETTLE, t0, t1, f.scale);
        float heartY = f.portal ? (f.dy0 + f.dy1) * .5f : top, wide = Math.max(4, f.half * .8f);
        for (int i = 0; i < n; i++) {
            int k = before + i;
            if (k == 0) {
                BarFxClient.flash(cx, heartY, cz, 1.8f, .35f, f.rim);                                  // the halo
                BarFxClient.flash(cx, heartY, cz, .9f, .3f, 0xFFFFFF);                                 // the white-hot core
                BarFxClient.beam(cx, f.portal ? f.dy0 : top, cz, cx, top + 12, cz, .4f, .8f,
                    BarFxClient.scale(BarFxClient.mix(f.rim, f.core, .6f), .9f));                      // the pillar
                BarFxClient.ring(f.x, top, f.z, wide, .5f, BarFxClient.lighter(f.rim, .4f));   // one wide ring: far zoom
                if (f.portal) {   // the doorway opens: a ring of light in its own plane, out to its edge in 0.25 s
                    float rr = Math.min(f.dw, (f.dy1 - f.dy0) * .5f) + .3f;
                    for (int m = 0; m < 16; m++) {
                        double a = m * Math.PI / 8;
                        float ca = (float) Math.cos(a) * rr / .25f, sa = (float) Math.sin(a) * rr / .25f;
                        BarFxClient.P o = BarFxClient.flash(cx, heartY, cz, .3f, .3f, BarFxClient.mix(f.rim, 0xFFFFFF, m % 2 * .6f));
                        o.vy = sa;
                        if (f.alongX) o.vx = ca; else o.vz = ca;
                    }
                }
                float ey = f.portal ? f.dy1 + .5f : top;
                for (int m = 0; m < 48; m++)   // embers thrown up from the lintel (an ender: its tip), falling
                    ember(cx + BarFxClient.rnd(-f.dw, f.dw), ey, cz + BarFxClient.rnd(-.4f, .4f),
                        m % 3 == 0 ? BarFxClient.lighter(f.ember, .5f) : f.ember);
            } else if (!f.portal && (k == 3 || k == 6 || k == 9)) {   // an ender's tiers light top to bottom
                float tier = 1 - (k - 3) / 3f * .33f;
                BarFxClient.ring(f.x, f.y + h * tier, f.z, f.half * (1.4f - .4f * tier) + .5f, .4f, f.core);
            } else if (vanilla) {   // the swirl drawn in: portal particles pulled from 3 blocks round into the doorway
                double a = k * 2.39996;
                level.addParticle(ParticleTypes.PORTAL, cx, heartY, cz, Math.cos(a) * 3, BarFxClient.rnd(-1, 1.5f), Math.sin(a) * 3);
            } else {   // an ender: a last sparkle round its tip
                BarFxClient.sparks(f.x + BarFxClient.rnd(-1, 1), top, f.z + BarFxClient.rnd(-1, 1), 1, 1.6f, f.core, 0, 0);
            }
        }
    }

    /** One ember: up and out, falling back, gone in under 1.4 s. */
    private static void ember(float x, float y, float z, int c) {
        BarFxClient.P p = BarFxClient.add(BarFxClient.SPARK, x, y, z, BarFxClient.rnd(1f, 1.4f), BarFxClient.rnd(.04f, .06f), c);
        double a = BarFxClient.R.nextDouble() * Math.PI * 2;
        float out = BarFxClient.rnd(1, 3f);
        p.vx = (float) Math.cos(a) * out; p.vz = (float) Math.sin(a) * out; p.vy = BarFxClient.rnd(7, 11);
    }

    /**
     * The spark's spot at u (0..1). A portal's two walkers start at the frame's feet, climb its pillars and run the
     * lintel to meet in the middle (Fable round 4). An ender's four climb its corners to the tip.
     */
    private static float[] walker(F f, int k, float u) {
        if (!f.portal) {
            float cx = (k % 4 < 2 ? -1 : 1) * f.half, cz = (k % 2 == 0 ? -1 : 1) * f.half;
            return new float[] { f.x + cx, f.y + .3f + u * f.height, f.z + cz };
        }
        float w = f.dw + .5f, foot = f.dy0, head = f.dy1 + .5f, up = head - foot, side = k % 2 == 0 ? -1 : 1;
        float s = u * (up + w);
        if (s < up)
            return f.door(side * w, foot + s);
        return f.door(side * (w - (s - up)), head);
    }

    /** A point on the square of half-size r round the site, at a fraction s (0..1) of its perimeter. */
    private static float[] onSquare(F f, float r, float s) {
        float d = s * 4, along = (d - (float) Math.floor(d)) * 2 - 1;
        return switch ((int) d % 4) {
            case 0 -> new float[] { f.x + along * r, f.z - r };
            case 1 -> new float[] { f.x + r, f.z + along * r };
            case 2 -> new float[] { f.x - along * r, f.z + r };
            default -> new float[] { f.x - r, f.z - along * r };
        };
    }

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }
}
