package com.solegendary.reignofnether.ambient.mist;

/**
 * Every rule of the living valley mist (MistClientEvents, MistParticle; lovish Oct 28: "can we make the fog more
 * animated and alive or rich"), without the world, so plain JUnit tests walk them (MistMathTest):
 * <ul>
 *   <li>where mist pools: a score from a small height grid round a column - a hollow (the column below its neighbours
 *       within ~6 blocks), water at or next to it, the foot of a 3+ block drop; thinned on a hilltop and on high
 *       ground (real valley fog is cold air settling: it drains off rises and gathers in low ground);</li>
 *   <li>when: thick at dawn, burnt off from late morning to mid afternoon, rising again at dusk, a little at night;</li>
 *   <li>how much: a sprite cap that shrinks as the RTS camera zooms out, zero at strategic zoom (pillar 2, readable
 *       at every zoom: the icons are all a player should see there);</li>
 *   <li>the look: soft alpha (0.05-0.18), big (3-8 blocks) and slowly growing, low (0.3-2.5 blocks up), slow drift
 *       along the shared wind (0.02-0.06 blocks a tick), cool grey by day and a little warm at dusk, never pure
 *       white; a unit running through it pushes it aside a little.</li>
 * </ul>
 */
public final class MistMath {

    private MistMath() { }

    // ---- where mist pools ----

    /** Blocks between the height grid's samples. */
    public static final int GRID_STEP = 2;
    /** Cells each way from the centre: 3 x 2 = the ~6 blocks a hollow is judged over. */
    public static final int GRID_R = 3;
    public static final int GRID_N = 2 * GRID_R + 1;
    /** Open flat low ground still gets a little mist at dawn: a meadow is not bare. */
    public static final double BASE_SCORE = 0.15;
    /** A hollow this deep (mean of the others above the centre, blocks) scores its full share. */
    static final double HOLLOW_FULL = 3, HOLLOW_SHARE = 0.6;
    static final double WATER_SHARE = 0.45;
    /** A drop next to the column counts as a cliff / terrace wall from this many blocks. */
    public static final int CLIFF_MIN = 3;
    /** Blocks over sea level where mist starts to thin, and where it is gone. */
    static final int THIN_FROM = 12, THIN_GONE = 48;

    /** The mean height of every other cell minus the centre's: positive in a hollow, negative on a rise. */
    public static double hollowDepth(int[][] h) {
        double sum = 0;
        int n = 0;
        for (int i = 0; i < GRID_N; i++)
            for (int j = 0; j < GRID_N; j++)
                if (i != GRID_R || j != GRID_R) {
                    sum += h[i][j];
                    n++;
                }
        return sum / n - h[GRID_R][GRID_R];
    }

    /** The highest rise of the 8 cells next to the centre (one cell, 2 blocks, away) above it; 0 if none is higher. */
    public static int cliffDrop(int[][] h) {
        int c = h[GRID_R][GRID_R], best = 0;
        for (int i = GRID_R - 1; i <= GRID_R + 1; i++)
            for (int j = GRID_R - 1; j <= GRID_R + 1; j++)
                best = Math.max(best, h[i][j] - c);
        return best;
    }

    /** A wall from CLIFF_MIN blocks: 0.35, a little more for each block higher, at most 0.6. */
    public static double cliffScore(int drop) {
        if (drop < CLIFF_MIN)
            return 0;
        return Math.min(0.6, 0.35 + 0.05 * (drop - CLIFF_MIN));
    }

    /** Water at the centre or in a cell next to it (within 2 blocks). */
    public static boolean nearWater(boolean[][] water) {
        for (int i = GRID_R - 1; i <= GRID_R + 1; i++)
            for (int j = GRID_R - 1; j <= GRID_R + 1; j++)
                if (water[i][j])
                    return true;
        return false;
    }

    /** 1 up to THIN_FROM over sea level, 0 from THIN_GONE, linear between: mist lies in the valleys, not on the peaks. */
    public static double altitudeThin(int y, int seaLevel) {
        double over = y - seaLevel;
        return clamp01(1 - (over - THIN_FROM) / (THIN_GONE - THIN_FROM));
    }

    /**
     * How much a column draws mist, 0-1: BASE_SCORE plus the hollow's, the water's and the cliff foot's shares, times
     * the exposure (a column standing above its ground loses it: 0 at 3 blocks above the mean) and the altitude thin.
     * Heights are absolute block y, indexed [dx][dz] from the grid's corner, the column at [GRID_R][GRID_R].
     */
    public static double score(int[][] h, boolean[][] water, int seaLevel) {
        double depth = hollowDepth(h);
        double hollow = HOLLOW_SHARE * clamp01(depth / HOLLOW_FULL);
        double wet = nearWater(water) ? WATER_SHARE : 0;
        double cliff = cliffScore(cliffDrop(h));
        double exposure = depth < 0 ? clamp01(1 + depth / HOLLOW_FULL) : 1;
        return clamp01((BASE_SCORE + hollow + wet + cliff) * exposure * altitudeThin(h[GRID_R][GRID_R], seaLevel));
    }

    // ---- when ----

    /** Day-time keys of the density curve (ticks, 0 = sunrise) and the density there; linear between, wrapping. */
    private static final long[] KEY_T = { 1500, 4000, 9000, 11500, 13500, 15000, 21500, 23000 };
    private static final double[] KEY_D = { 1.0, 0.0, 0.0, 0.7, 0.7, 0.25, 0.25, 1.0 };

    /**
     * How thick the mist is at a day time, 0-1: thick through dawn (23000-1500), burning off to none by 4000, none
     * until 9000, rising to 0.7 by dusk (11500-13500), settling to 0.25 for the night, thickening again before dawn.
     */
    public static double density(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        int n = KEY_T.length;
        for (int i = 0; i < n; i++) {
            long a = KEY_T[i], b = i + 1 < n ? KEY_T[i + 1] : KEY_T[0] + 24000;
            long tt = t < KEY_T[0] ? t + 24000 : t;      // before the first key: the last span, past midnight
            if (tt >= a && tt <= b) {
                double f = (double) (tt - a) / (b - a);
                return KEY_D[i] + (KEY_D[(i + 1) % n] - KEY_D[i]) * f;
            }
        }
        return 0;   // unreachable: the spans cover the whole day
    }

    // ---- how much ----

    /** Full cap to the default RTS zoom, none from the strategic zoom (StrategicViewClientEvents.STRATEGIC_ZOOM_ENTER). */
    public static final float ZOOM_FULL = 30, ZOOM_NONE = 70;

    /** The sprite cap at this zoom: base up to ZOOM_FULL, 0 from ZOOM_NONE (or whenever the icons are on), linear between. */
    public static int cap(int base, float zoom, boolean strategic) {
        if (strategic || zoom >= ZOOM_NONE)
            return 0;
        if (zoom <= ZOOM_FULL)
            return base;
        return (int) Math.round(base * (ZOOM_NONE - zoom) / (ZOOM_NONE - ZOOM_FULL));
    }

    /** Sprites wanted alive: the cap times the time of day's density. */
    public static int wanted(int cap, double density) {
        return (int) Math.round(cap * clamp01(density));
    }

    /** How many to spawn this pass: never past the cap, at most perPass, never negative. */
    public static int toSpawn(int alive, int cap, int perPass) {
        return Math.max(0, Math.min(perPass, cap - alive));
    }

    /** Blocks round the camera's ground focus where mist may appear: about the view's half-height, 12-48. */
    public static double radius(float zoom) {
        return Math.max(12, Math.min(48, 0.9 * zoom));
    }

    /**
     * The ground point (x, groundY, z) the camera looks at from the eye, Minecraft's yaw / pitch convention (pitch > 0
     * looks down; yaw 0 faces +z, -90 faces +x) - the inverse of OrthoviewClientEvents.centreCameraOnPosInstant. A
     * camera at or above the horizon has no ground point: the eye's own column. (The same rule as #334's
     * AmbientLifeMath.focus; kept here so the mist does not depend on that branch landing first.)
     */
    public static double[] focus(double eyeX, double eyeY, double eyeZ, float yawDeg, float pitchDeg, double groundY) {
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        double down = Math.sin(pitch);
        if (down < 0.05)
            return new double[] { eyeX, groundY, eyeZ };
        double t = (eyeY - groundY) / down;
        double fx = -Math.sin(yaw) * Math.cos(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
        return new double[] { eyeX + fx * t, groundY, eyeZ + fz * t };
    }

    // ---- the look ----

    public static final double ALPHA_MIN = 0.05, ALPHA_MAX = 0.18;
    /** Ticks to fade in (3 s) and out (4 s): mist gathers and thins, it never pops. */
    public static final int FADE_IN = 60, FADE_OUT = 80;

    /** A sprite's alpha at full fade: thicker where the column draws more mist and the hour is thicker. */
    public static double peakAlpha(double score, double density) {
        return ALPHA_MIN + (ALPHA_MAX - ALPHA_MIN) * clamp01(score * density);
    }

    /** 0-1 over a sprite's life: smoothstep in over FADE_IN ticks, out over the last FADE_OUT. */
    public static double fade(int age, int lifetime) {
        double in = smooth(age / (double) FADE_IN), out = smooth((lifetime - age) / (double) FADE_OUT);
        return Math.min(in, out);
    }

    /** Ticks a sprite lives, from a random 0-1: 400-700 (20-35 s). */
    public static int lifetime(double r) {
        return 400 + (int) Math.round(300 * clamp01(r));
    }

    /** Width in blocks at birth (the quad's side; its soft edge fades to nothing), from a random 0-1: 3-6.5. */
    public static double startSize(double r) {
        return 3 + 3.5 * clamp01(r);
    }

    /** It grows slowly: 1.2x by the end of its life (so 3-7.8 blocks, within the 3-8 of the design). */
    public static double size(double start, int age, int lifetime) {
        return start * (1 + 0.2 * clamp01(age / (double) Math.max(1, lifetime)));
    }

    /** Blocks above the ground the sprite lies, from a random 0-1: 0.3-2.5. */
    public static double hover(double r) {
        return 0.3 + 2.2 * clamp01(r);
    }

    /** Blocks a tick it drifts, from a random 0-1: 0.02-0.06. */
    public static double driftSpeed(double r) {
        return 0.02 + 0.04 * clamp01(r);
    }

    /** The drift velocity {vx, vz} along a wind heading (radians, 0 = +x, pi/2 = +z: WindField.heading's convention). */
    public static double[] drift(double heading, double speed) {
        return new double[] { Math.cos(heading) * speed, Math.sin(heading) * speed };
    }

    private static final float[] COOL = { 0.80f, 0.84f, 0.88f };   // a slightly cool white-grey
    private static final float[] WARM = { 0.90f, 0.82f, 0.74f };   // a dusk mist catching the low sun

    /** The sprite tint: cool grey, warming toward 12500 (the middle of dusk) and back. Every channel 0.74-0.90. */
    public static float[] colour(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        double w = clamp01(1 - Math.abs(t - 12500) / 1500.0);
        float[] c = new float[3];
        for (int i = 0; i < 3; i++)
            c[i] = (float) (COOL[i] + (WARM[i] - COOL[i]) * w);
        return c;
    }

    // ---- reacting ----

    /** Units slower than this (blocks a tick) walk through without stirring it; a run (~0.3) does. */
    public static final double PUSH_SPEED = 0.15;
    /** The largest push, blocks a tick: a nudge the mist's own damping takes back to its drift in a few seconds. */
    public static final double MAX_PUSH = 0.05;

    /**
     * The push on a sprite from a unit: (dx, dz) the sprite's centre minus the unit's position, the unit's speed in
     * blocks a tick, the sprite's width in blocks. Away from the unit, stronger the nearer and faster, none outside the
     * sprite (half its width from the centre) or below PUSH_SPEED. A unit dead centre pushes along +x (no NaN).
     */
    public static double[] push(double dx, double dz, double unitSpeed, double size) {
        double d = Math.hypot(dx, dz), reach = size / 2;
        if (unitSpeed < PUSH_SPEED || d >= reach)
            return new double[] { 0, 0 };
        double k = MAX_PUSH * (1 - d / reach) * clamp01((unitSpeed - PUSH_SPEED) / PUSH_SPEED);
        if (d < 1e-6)
            return new double[] { k, 0 };
        return new double[] { dx / d * k, dz / d * k };
    }

    // ---- the eyes rig ----

    /** The day time of the 15_<name>_mist_rts still: the thick of dawn. */
    public static final long EYES_DAWN_TIME = 23500;

    /**
     * Whether mist may show: always in play; while the eyes rig photographs structures (EyesPhotoBooth.ambientLifeOff)
     * only when the run asked for it with -Dbat.eyes.mist=true - the structure stills are measured (dark share, tuft
     * stars), and drifting sprites would change their numbers run to run.
     */
    public static boolean allowed(boolean ambientLifeOff, String eyesMistProperty) {
        return !ambientLifeOff || Boolean.parseBoolean(eyesMistProperty);
    }

    // ----

    static double clamp01(double v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    private static double smooth(double x) {
        double t = clamp01(x);
        return t * t * (3 - 2 * t);
    }
}
