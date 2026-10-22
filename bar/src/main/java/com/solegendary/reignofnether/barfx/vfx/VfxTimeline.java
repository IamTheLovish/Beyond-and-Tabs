package com.solegendary.reignofnether.barfx.vfx;

/**
 * The timing maths of an effect: which phase it is in, how bright a layer is at a moment (its window, its fades), how
 * big it has grown, how a light falls off. Pure functions of time, so the client, the JUnit tests and any server code
 * agree. No Minecraft classes.
 */
public final class VfxTimeline {
    private VfxTimeline() { }

    public enum Phase { WINDUP, PEAK, FADE, DONE }

    public static Phase phase(VfxDef.Timeline tl, float t) {
        if (t < tl.windup) return Phase.WINDUP;
        if (t < tl.windup + tl.peak) return Phase.PEAK;
        if (t < tl.total()) return Phase.FADE;
        return Phase.DONE;
    }

    /** 0..1 smooth (Hermite) step: eases in and out, no pop at either end. */
    public static float smooth(float x) {
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        return x * x * (3 - 2 * x);
    }

    /** The whole effect's envelope: rises over the wind-up, 1 through the peak, falls over the fade, 0 after. */
    public static float envelope(VfxDef.Timeline tl, float t) {
        return window(t, 0, tl.total(), tl.windup, tl.fade);
    }

    /**
     * Brightness of a window [start, end] with fade-in fi and fade-out fo (seconds) at time t. A zero-length fade is a
     * hard edge; fades longer than the window are squeezed so the peak is still reached at its middle.
     */
    public static float window(float t, float start, float end, float fi, float fo) {
        if (t < start || t > end || end <= start) return 0;
        float len = end - start;
        if (fi + fo > len) {
            float k = len / (fi + fo);
            fi *= k;
            fo *= k;
        }
        float a = fi <= 0 ? 1 : smooth((t - start) / fi);
        float b = fo <= 0 ? 1 : smooth((end - t) / fo);
        return Math.min(a, b);
    }

    /** End of a layer's window in effect time (end < 0 = the effect's end). */
    public static float layerEnd(VfxDef.Layer l, VfxDef.Timeline tl) {
        return l.end < 0 ? tl.total() : Math.min(l.end, tl.total());
    }

    /** A layer's fade-in: its own, else the effect's wind-up when it starts with the effect, else a short 0.15 s. */
    public static float layerFadeIn(VfxDef.Layer l, VfxDef.Timeline tl) {
        if (l.fadeIn >= 0) return l.fadeIn;
        return l.start <= 0 ? tl.windup : 0.15f;
    }

    /** A layer's fade-out: its own, else the effect's fade when it runs to the end, else 0.25 s. */
    public static float layerFadeOut(VfxDef.Layer l, VfxDef.Timeline tl) {
        if (l.fadeOut >= 0) return l.fadeOut;
        return l.end < 0 || l.end >= tl.total() ? tl.fade : 0.25f;
    }

    /** How bright a layer is at effect time t (0..1, its alpha not applied). */
    public static float layerAlpha(VfxDef.Layer l, VfxDef.Timeline tl, float t) {
        return window(t, l.start, layerEnd(l, tl), layerFadeIn(l, tl), layerFadeOut(l, tl));
    }

    /** Is the layer's window open (particles may spawn)? */
    public static boolean layerActive(VfxDef.Layer l, VfxDef.Timeline tl, float t) {
        return t >= l.start && t <= layerEnd(l, tl);
    }

    /** Size factor: growFrom -> 1 over the layer's grow time along its easing (default cubic out), 1 without growth. */
    public static float layerScale(VfxDef.Layer l, float t) {
        if (l.grow <= 0) return 1;
        float x = (t - l.start) / l.grow;
        if (x <= 0) return l.growFrom;
        if (x >= 1) return 1;
        return l.growFrom + (1 - l.growFrom) * l.ease.apply(x);
    }

    /** A light's brightness at effect time t (its own window, else the effect's envelope). */
    public static float lightAlpha(VfxDef.Light li, VfxDef.Timeline tl, float t) {
        float end = li.end < 0 ? tl.total() : Math.min(li.end, tl.total());
        float fi = li.fadeIn >= 0 ? li.fadeIn : (li.start <= 0 ? tl.windup : 0.15f);
        float fo = li.fadeOut >= 0 ? li.fadeOut : tl.fade;
        return window(t, li.start, end, fi, fo);
    }

    /**
     * How much of a light reaches distance d: a smooth inverse-square-like falloff that is exactly 0 at the radius
     * (so a unit walking out of the light never pops).
     */
    public static float falloff(float d, float radius) {
        if (radius <= 0 || d >= radius) return 0;
        float x = d / radius;
        float edge = (1 - x * x);
        return edge * edge / (1 + 4 * x * x);
    }

    /** Particles to spawn this frame for a rate (per s) over dt, carrying the fraction in acc[0]. */
    public static int spawnCount(float rate, float dt, float[] acc) {
        if (rate <= 0 || dt <= 0) return 0;
        acc[0] += rate * dt;
        int n = (int) acc[0];
        acc[0] -= n;
        return n;
    }
}
