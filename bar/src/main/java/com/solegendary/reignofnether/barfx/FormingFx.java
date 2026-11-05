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
 * with BarFx's own light pieces (flashes, sparks, a ring) and, for portals, vanilla's portal particles:
 * <ul>
 *   <li>spark: sparks and small flashes jump along the frame (the footprint's rim, at frame height);</li>
 *   <li>fill: flashes run inward from the frame to the centre, brightening; portal particles stream to the middle;</li>
 *   <li>swirl: three arms turn round the centre, tightening as they rise;</li>
 *   <li>settle: one bright pulse at the centre and a ring that spreads and fades.</li>
 * </ul>
 * A portal glows nether violet, a game ender in its faction's tint. Pieces are spawned only while the site is near
 * the camera (the timeline's running totals keep the count right however the frames fall), and an entry is dropped
 * the moment the timeline is done: nothing ticks or stays behind. Client only.
 */
@OnlyIn(Dist.CLIENT)
final class FormingFx {
    private FormingFx() { }

    /** At most this many formings at once (a whole base of portals finishing together still costs a bounded amount). */
    static final int MAX = 24;

    /** Portal violet (rim, core) and the game enders' faction tints by BarFx.F_* (F_NONE = plain white-gold). */
    static final int PORTAL_RIM = 0x8F45FF, PORTAL_CORE = 0xE8D0FF;
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

    private static void emit(F f, float t0, float t1) {
        float size = (float) Math.sqrt(f.scale);
        var level = Minecraft.getInstance().level;
        boolean vanilla = f.portal && level != null;
        float frameY = f.y + Math.min(f.height, 6f) * .5f;
        // 1. spark: along the frame (the footprint's square rim), jumping up and out
        int n = FormingTimeline.between(FormingTimeline.Phase.SPARK, t0, t1, f.scale);
        for (int i = 0; i < n; i++) {
            float[] p = rimPoint(f);
            float py = f.y + BarFxClient.rnd(.2f, Math.min(f.height, 6f) * .8f);
            BarFxClient.sparks(p[0], py, p[1], 2, 2.2f, f.core, 0, 0);
            BarFxClient.flash(p[0], py, p[1], .35f * size, .22f, f.rim);
        }
        // 2. fill: the front runs inward from the frame; each piece a flash on the front, brighter as it closes in
        n = FormingTimeline.between(FormingTimeline.Phase.FILL, t0, t1, f.scale);
        float front = FormingTimeline.fillFront(t1);
        for (int i = 0; i < n; i++) {
            double a = BarFxClient.R.nextDouble() * Math.PI * 2;
            float r = f.half * front;
            float px = f.x + (float) Math.cos(a) * r, pz = f.z + (float) Math.sin(a) * r;
            BarFxClient.flash(px, frameY + BarFxClient.rnd(-.6f, .6f), pz, (.3f + .25f * (1 - front)) * size, .3f,
                BarFxClient.mix(f.rim, f.core, 1 - front));
            if (vanilla)   // vanilla's portal particle travels from the offset to the given spot: the middle
                level.addParticle(ParticleTypes.PORTAL, f.x, frameY, f.z, px - f.x, BarFxClient.rnd(-.3f, .3f), pz - f.z);
        }
        // 3. swirl: three arms round the centre, tightening as they rise
        n = FormingTimeline.between(FormingTimeline.Phase.SWIRL, t0, t1, f.scale);
        float ang = FormingTimeline.swirlAngle(t1), rad = FormingTimeline.swirlRadius(t1) * f.half;
        float rise = FormingTimeline.progress(FormingTimeline.Phase.SWIRL, t1);
        for (int i = 0; i < n; i++) {
            double a = ang + (i % 3) * Math.PI * 2 / 3 + BarFxClient.rnd(-.15f, .15f);
            float px = f.x + (float) Math.cos(a) * rad, pz = f.z + (float) Math.sin(a) * rad;
            float py = f.y + .4f + rise * Math.min(f.height, 6f) * .7f + BarFxClient.rnd(-.25f, .25f);
            BarFxClient.flash(px, py, pz, .28f * size, .35f, i % 2 == 0 ? f.core : f.rim);
            if (vanilla && i % 2 == 0)
                level.addParticle(ParticleTypes.REVERSE_PORTAL, px, py, pz, 0, .02, 0);
        }
        // 4. settle: the first piece is the pulse and the ring; the rest are a soft rise of sparks from the middle
        int before = FormingTimeline.emitted(FormingTimeline.Phase.SETTLE, t0, f.scale);
        n = FormingTimeline.between(FormingTimeline.Phase.SETTLE, t0, t1, f.scale);
        for (int i = 0; i < n; i++) {
            if (before + i == 0) {
                BarFxClient.flash(f.x, frameY, f.z, Math.max(1.2f, f.half * .8f), .5f, f.core);
                BarFxClient.ring(f.x, f.y, f.z, f.half * 1.6f + 1, .9f, f.rim);
            } else
                BarFxClient.sparks(f.x + BarFxClient.rnd(-.4f, .4f) * f.half, frameY, f.z + BarFxClient.rnd(-.4f, .4f) * f.half,
                    1, 1.4f, f.core, 0, 0);
        }
    }

    /** A random point on the footprint's square rim. */
    private static float[] rimPoint(F f) {
        float s = BarFxClient.rnd(-f.half, f.half);
        return switch (BarFxClient.R.nextInt(4)) {
            case 0 -> new float[] { f.x + s, f.z - f.half };
            case 1 -> new float[] { f.x + s, f.z + f.half };
            case 2 -> new float[] { f.x - f.half, f.z + s };
            default -> new float[] { f.x + f.half, f.z + s };
        };
    }
}
