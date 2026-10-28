package com.solegendary.reignofnether.barfx;

/**
 * Combat feel, part 1 (lovish approved Oct 28): a trauma-decay camera shake, the pure half (no Minecraft classes, so it
 * is tested headless in CameraShakeTest; {@code CameraShakeClient} feeds it and puts it on the camera).
 *
 * Big events add trauma (0..1, clamped); trauma decays linearly at {@link #DECAY_PER_SECOND}; the shake is trauma
 * squared - small jolts are barely felt, a big blast is - times smoothed value noise per axis (yaw, pitch, roll) plus
 * a small screen offset. First person may be strong ({@link #FP_MAX_DEG}); the RTS view is very gentle
 * ({@link #RTS_MAX_DEG}, about a tenth) and fades as the camera zooms out ({@link #rtsZoomGain}); how far an event is
 * from the screen centre is applied when it is fed ({@link #falloff}, {@link #rtsRange}).
 *
 * The rule from the old shake stays (BarFxClient, playtest Oct 10): a shake never grows with army size. Big events are
 * few by nature (blasts, collapses, heavy shells); the many small ones - T3 hits, a big unit landing nearby - go
 * through {@link #addMinor}, a bucket of {@link #MINOR_CAP} that refills at {@link #MINOR_PER_SECOND}, so a T3 brawl
 * stays a rumble whatever its size.
 *
 * Allocation-free; one instance per client.
 */
public final class CameraShake {

    public static final int OFF = 0, SUBTLE = 1, FULL = 2;
    public static final String[] MODE_NAMES = {"Off", "Subtle", "Full"};

    /** Output channels: degrees for the angles, blocks for the offsets (RTS view: the orthographic projection's). */
    public static final int YAW = 0, PITCH = 1, ROLL = 2, OFF_X = 3, OFF_Y = 4, OUT = 5;

    /** A full jolt (trauma 1) is gone in ~0.7 s. */
    public static final float DECAY_PER_SECOND = 1.4f;
    /** First person at Full: the most yaw / pitch and roll at trauma 1, degrees. */
    public static final float FP_MAX_DEG = 3f, FP_MAX_ROLL_DEG = 2f;
    /** The RTS view at Full: about a tenth of first person, and a small screen offset in blocks. */
    public static final float RTS_MAX_DEG = 0.3f, RTS_MAX_ROLL_DEG = 0.2f, RTS_MAX_OFFSET = 0.3f;
    /** Subtle is half of Full. */
    public static final float SUBTLE_SCALE = 0.5f;
    /** Noise rate (lattice points per second): a quick judder first person, a slower sway from above. */
    public static final float FP_FREQ = 16f, RTS_FREQ = 10f;
    /** The RTS shake is full at or under this zoom and fades to {@link #RTS_FAR_GAIN} at {@link #RTS_ZOOM_QUIET}. */
    public static final float RTS_ZOOM_FULL = 25f, RTS_ZOOM_QUIET = 110f, RTS_FAR_GAIN = 0.04f;
    /** Minor feeds: at most this much trauma at once, refilled at this rate. */
    public static final float MINOR_CAP = 0.3f, MINOR_PER_SECOND = 0.25f;

    // ------------------------------------------------------------------ how much an event earns (pure)

    public static int sanitiseMode(int mode) {
        return mode < OFF || mode > FULL ? SUBTLE : mode;
    }

    public static float modeScale(int mode) {
        return switch (sanitiseMode(mode)) {
            case OFF -> 0f;
            case FULL -> 1f;
            default -> SUBTLE_SCALE;
        };
    }

    /** An explosion of this radius: nothing under 2.5 (small pops), then growing to a full jolt at ~11. */
    public static float traumaForBlast(float radius) {
        if (!(radius >= 2.5f))
            return 0f;
        return clamp01(0.1f + 0.08f * radius);
    }

    /** A building collapse of this horizontal half size. */
    public static float traumaForCollapse(float halfSize) {
        if (!(halfSize > 0f))
            return 0f;
        return Math.min(0.9f, 0.35f + 0.04f * halfSize);
    }

    /** A heavy shell / T3 ability landing (BarFx SCORCH), by its blast reach. */
    public static float traumaForHeavyImpact(float radius) {
        if (!(radius > 0f))
            return 0f;
        return Math.min(0.6f, 0.12f + 0.06f * radius);
    }

    /** A landing at this fall speed (blocks per tick, down = positive): nothing for a step, more for a real drop. */
    public static float traumaForLanding(float fallSpeed) {
        if (!(fallSpeed > 0.5f))
            return 0f;
        return Math.min(0.6f, (fallSpeed - 0.4f) * 0.8f);
    }

    /** Linear falloff to 0 at {@code range}; 0 for a bad distance. */
    public static float falloff(float dist, float range) {
        if (!(dist >= 0f) || !(range > 0f))
            return 0f;
        return Math.max(0f, 1f - dist / range);
    }

    /** How far from the screen centre an event still shakes the RTS view: about the visible half height and a margin. */
    public static float rtsRange(float zoom) {
        return 0.5f * Math.max(10f, finiteOr(zoom, 30f)) + 14f;
    }

    /** The RTS view's gain by zoom: 1 close in, fading to {@link #RTS_FAR_GAIN} at a far zoom-out. */
    public static float rtsZoomGain(float zoom) {
        float z = finiteOr(zoom, RTS_ZOOM_QUIET);
        if (z <= RTS_ZOOM_FULL)
            return 1f;
        if (z >= RTS_ZOOM_QUIET)
            return RTS_FAR_GAIN;
        float k = (z - RTS_ZOOM_FULL) / (RTS_ZOOM_QUIET - RTS_ZOOM_FULL);
        float s = 1f - k;
        return RTS_FAR_GAIN + (1f - RTS_FAR_GAIN) * s * s;   // eases off quickly past the close zooms
    }

    /**
     * Smoothed value noise in -1..1: random values on an integer lattice (a hash, no Random), joined by a smoothstep so
     * it is continuous with a continuous slope. One independent stream per axis.
     */
    public static float noise(float t, int axis) {
        if (!Float.isFinite(t))
            return 0f;
        double ft = Math.floor(t);
        int i = (int) (long) ft;
        float f = (float) (t - ft);
        float a = lattice(i, axis), b = lattice(i + 1, axis);
        float u = f * f * (3f - 2f * f);
        return a + (b - a) * u;
    }

    private static float lattice(int i, int axis) {
        int h = i * 0x27D4EB2D ^ (axis + 1) * 0x165667B1;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 32767.5f - 1f;
    }

    // ------------------------------------------------------------------ state

    private float trauma = 0f;
    private float clock = 0f;
    private float minorLeft = MINOR_CAP;

    public float trauma() {
        return trauma;
    }

    /** Sets the trauma (tests and resets); clamped to 0..1. */
    public void setTrauma(float t) {
        trauma = Float.isFinite(t) ? clamp01(t) : 0f;
    }

    /** A big event: adds its trauma (already scaled for distance by the caller); negative or bad values are ignored. */
    public void add(float amount) {
        if (!(amount > 0f) || !Float.isFinite(amount))
            return;
        trauma = clamp01(trauma + amount);
    }

    /** A small, frequent event: adds at most what the minor bucket holds, so many of them never make a big shake. */
    public void addMinor(float amount) {
        if (!(amount > 0f) || !Float.isFinite(amount))
            return;
        float a = Math.min(amount, minorLeft);
        minorLeft -= a;
        add(a);
    }

    /** One frame: decay, the noise clock, the minor bucket refill. Bad or negative frame times count as nothing. */
    public void step(float dt) {
        if (!(dt > 0f) || !Float.isFinite(dt))
            return;
        dt = Math.min(dt, 0.25f);   // a hitch never skips the noise far ahead
        trauma = Math.max(0f, trauma - DECAY_PER_SECOND * dt);
        clock = (clock + dt) % 4096f;   // keeps the float clock precise
        minorLeft = Math.min(MINOR_CAP, minorLeft + MINOR_PER_SECOND * dt);
    }

    public void reset() {
        trauma = 0f;
        minorLeft = MINOR_CAP;
    }

    /**
     * This frame's shake into {@code out} ({@link #OUT} floats): yaw, pitch, roll in degrees and the screen offset in
     * blocks. {@code rts}: the RTS view (gentle, by zoom); {@code mode}: Off / Subtle / Full; {@code strength}: the
     * camera page's shake slider, 0..1.
     */
    public void output(boolean rts, int mode, float strength, float zoom, float[] out) {
        float k = trauma * trauma * modeScale(mode) * clamp01(finiteOr(strength, 0f));
        if (rts)
            k *= rtsZoomGain(zoom);
        if (!(k > 0f)) {
            for (int i = 0; i < OUT; i++)
                out[i] = 0f;
            return;
        }
        float t = clock * (rts ? RTS_FREQ : FP_FREQ);
        float deg = rts ? RTS_MAX_DEG : FP_MAX_DEG, roll = rts ? RTS_MAX_ROLL_DEG : FP_MAX_ROLL_DEG;
        out[YAW] = k * deg * noise(t, 0);
        out[PITCH] = k * deg * noise(t, 1);
        out[ROLL] = k * roll * noise(t, 2);
        float off = rts ? RTS_MAX_OFFSET : 0f;   // first person turns only: a moving eye reads as nausea, not impact
        out[OFF_X] = k * off * noise(t, 3);
        out[OFF_Y] = k * off * noise(t, 4);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    private static float finiteOr(float v, float or) {
        return Float.isFinite(v) ? v : or;
    }
}
