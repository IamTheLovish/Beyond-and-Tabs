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
 *   <li>spark: two runners trace the frame's top rim (a game ender: four climb its corners);</li>
 *   <li>fill: light steps inward on squares, brighter each step; portal particles stream to the middle;</li>
 *   <li>swirl: two strands spiral up the middle, tightening, round a growing core;</li>
 *   <li>settle: the brightest moment - a white-hot pulse, a light pillar, a column of rising motes, a ring.</li>
 * </ul>
 * A portal burns nether fire (magenta to hot orange), a game ender in its faction's tint. Pieces are spawned only
 * while the site is near the camera (the timeline's running totals keep the count right however the frames fall), and
 * an entry is dropped the moment the timeline is done: nothing ticks or stays behind. Client only.
 */
@OnlyIn(Dist.CLIENT)
final class FormingFx {
    private FormingFx() { }

    /** At most this many formings at once (a whole base of portals finishing together still costs a bounded amount). */
    static final int MAX = 24;

    /**
     * A portal's nether fire (rim magenta, core hot orange: the lit portal it forms into is lava and netherrack - Fable
     * round 1, "violet forming into orange lava is a mismatch") and the game enders' faction tints by BarFx.F_*.
     */
    static final int PORTAL_RIM = 0xD8359A, PORTAL_CORE = 0xFFB25C;
    static final int[] ENDER_TINT = { 0xF0E2B0, 0xFFD96A, 0x5FE6F0, 0xFF8A2E, 0x7CE07A, 0x3CC8BE, 0x4FD1A0, 0xE0A040 };

    static final class F {
        final float x, y, z, half, height, scale, born;
        final int rim, core;
        final boolean portal;
        float done = 0;   // seconds of the timeline already emitted

        F(float x, float y, float z, float half, float height, int style, int faction, float born) {
            this.x = x; this.y = y; this.z = z; this.half = half; this.height = height; this.born = born;
            this.scale = FormingTimeline.scaleFor(half);
            this.portal = style != BarFx.FS_ENDER;
            int tint = ENDER_TINT[Math.max(0, Math.min(ENDER_TINT.length - 1, faction))];
            this.rim = portal ? PORTAL_RIM : tint;
            this.core = portal ? PORTAL_CORE : BarFxClient.lighter(tint, .55f);
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
        LIVE.add(new F(e.x, e.y, e.z, Math.max(1, e.a), Math.max(1, e.b), e.kind, e.flags & BarFx.FACTION_MASK, clock));
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
     * The look (Fable critic round 1, Nov 5: shape 3, timing 3, readability 3 - "equal-weight scattered particles"): every
     * phase has a structure the next one carries on, and one brightness order - a white-hot core at the settle, bright
     * traced lines and rings, dim sparkle. Pieces are placed by their index in the phase, not at random, so the shape
     * is the same whatever the frame rate:
     *   spark  - two runners trace the frame's top rim, with tails (a game ender: four climb its corners to the tip);
     *   fill   - light steps inward on squares, brighter each step (an ender: rings climb the tower);
     *   swirl  - two strands spiral up the middle, tightening, round a core that grows;
     *   settle - the brightest frame: a pulse, a light pillar and a column of rising motes (what reads at far zoom),
     *            a saturated ring; an ender also pulses a ring at each third of its height, top first.
     */
    private static void emit(F f, float t0, float t1) {
        float size = (float) Math.sqrt(f.scale);
        var level = Minecraft.getInstance().level;
        boolean vanilla = level != null;
        float h = f.portal ? Math.min(f.height, 6f) : f.height;   // a portal's frame; an ender's whole tower
        float midY = f.y + h * .5f;
        // 1. spark
        int before = FormingTimeline.emitted(FormingTimeline.Phase.SPARK, t0, f.scale);
        int n = FormingTimeline.between(FormingTimeline.Phase.SPARK, t0, t1, f.scale);
        float total = Math.max(1, FormingTimeline.emitted(FormingTimeline.Phase.SPARK, FormingTimeline.DURATION, f.scale));
        for (int i = 0; i < n; i++) {
            int k = before + i;
            float u = Math.min(1, (k + .5f) / total);
            float[] p = runner(f, k, u), tail = runner(f, k, Math.max(0, u - .08f));
            BarFxClient.flash(p[0], p[1], p[2], .42f * size, .3f, f.core);
            BarFxClient.flash(tail[0], tail[1], tail[2], .26f * size, .22f, f.rim);
            BarFxClient.sparks(p[0], p[1], p[2], 1, 1.3f, f.core, 0, 0);
        }
        // 2. fill
        before = FormingTimeline.emitted(FormingTimeline.Phase.FILL, t0, f.scale);
        n = FormingTimeline.between(FormingTimeline.Phase.FILL, t0, t1, f.scale);
        for (int i = 0; i < n; i++) {
            int k = before + i;
            float tk = t0 + (i + .5f) / n * (t1 - t0);
            float step = (float) Math.floor((1 - FormingTimeline.fillFront(tk)) * 7) / 7f;   // seven squares, outer first
            float r = f.portal ? f.half * (1 - step) : f.half * (.35f + .65f * (1 - step));
            float py = f.portal ? midY : f.y + .4f + h * step;
            float[] q = onSquare(f, r, frac(k * .618034f));
            BarFxClient.flash(q[0], py, q[1], (.3f + .4f * step) * size, .32f, BarFxClient.mix(f.rim, f.core, step));
            if (vanilla && f.portal && k % 2 == 0)   // vanilla's portal particle travels from the offset to the given spot
                level.addParticle(ParticleTypes.PORTAL, f.x, py, f.z, q[0] - f.x, 0, q[1] - f.z);
        }
        // 3. swirl
        before = FormingTimeline.emitted(FormingTimeline.Phase.SWIRL, t0, f.scale);
        n = FormingTimeline.between(FormingTimeline.Phase.SWIRL, t0, t1, f.scale);
        for (int i = 0; i < n; i++) {
            int k = before + i;
            float tk = t0 + (i + .5f) / n * (t1 - t0);
            float rise = FormingTimeline.progress(FormingTimeline.Phase.SWIRL, tk);
            if (k % 10 == 9) {   // the core, growing at the heart of the frame
                BarFxClient.flash(f.x, midY, f.z, (.3f + .7f * rise) * size, .3f, BarFxClient.lighter(f.core, .5f));
                continue;
            }
            double a = FormingTimeline.swirlAngle(tk) + (k % 2) * Math.PI;
            float rad = FormingTimeline.swirlRadius(tk) * Math.max(1.5f, f.half);
            float px = f.x + (float) Math.cos(a) * rad, pz = f.z + (float) Math.sin(a) * rad;
            float py = f.y + .3f + rise * (h + 1.5f);
            BarFxClient.flash(px, py, pz, .3f * size, .35f, k % 2 == 0 ? f.core : f.rim);
            if (vanilla && f.portal && k % 3 == 0)
                level.addParticle(ParticleTypes.REVERSE_PORTAL, px, py, pz, 0, .03, 0);
        }
        // 4. settle
        before = FormingTimeline.emitted(FormingTimeline.Phase.SETTLE, t0, f.scale);
        n = FormingTimeline.between(FormingTimeline.Phase.SETTLE, t0, t1, f.scale);
        float top = f.y + h, heart = f.portal ? midY : top;
        for (int i = 0; i < n; i++) {
            int k = before + i;
            if (k == 0) {
                float pulse = Math.max(2.5f, f.half * .9f);
                BarFxClient.flash(f.x, heart, f.z, pulse, .4f, f.rim);                                  // the halo
                BarFxClient.flash(f.x, heart, f.z, pulse * .5f, .3f, 0xFFFFFF);                         // the white-hot core
                BarFxClient.beam(f.x, f.y, f.z, f.x, top + 8, f.z, .55f, .3f + .1f * f.scale, f.core);  // the pillar
                BarFxClient.ring(f.x, f.y, f.z, f.half * 1.6f + 1, .5f, f.rim);
                for (int m = 0; m < 14; m++)   // the column of motes: a vertical streak survives far zoom (our own, gone in .6 s)
                    BarFxClient.flash(f.x + BarFxClient.rnd(-.25f, .25f), top - 1 + m * .5f, f.z + BarFxClient.rnd(-.25f, .25f),
                        .2f * size, .6f, BarFxClient.lighter(f.core, .4f)).vy = BarFxClient.rnd(5, 9);
            } else if (!f.portal && (k == 3 || k == 6 || k == 9)) {   // an ender's tiers light top to bottom
                float tier = 1 - (k - 3) / 3f * .33f;
                BarFxClient.ring(f.x, f.y + h * tier, f.z, f.half * (1.4f - .4f * tier) + .5f, .4f, f.core);
            } else {   // sparks fired up from the rim
                float[] q = onSquare(f, f.half, frac(k * .618034f));
                BarFxClient.sparks(q[0], f.y + .4f, q[1], 1, 1.8f, f.core, 0, 0);
            }
        }
    }

    /** A spark runner's spot at u (0..1): a portal's two run its frame's top rim from the front corner, meeting at the
     *  back; an ender's four climb its corners to the tip. */
    private static float[] runner(F f, int k, float u) {
        if (!f.portal) {
            float cx = (k % 4 < 2 ? -1 : 1) * f.half, cz = (k % 2 == 0 ? -1 : 1) * f.half;
            return new float[] { f.x + cx, f.y + .3f + u * f.height, f.z + cz };
        }
        float[] q = onSquare(f, f.half, frac((k % 2 == 0 ? u : -u) * .5f + 1));   // half the rim each way
        return new float[] { q[0], f.y + Math.min(f.height, 6f) * .9f, q[1] };
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
