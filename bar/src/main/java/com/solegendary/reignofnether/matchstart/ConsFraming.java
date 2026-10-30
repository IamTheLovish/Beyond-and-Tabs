package com.solegendary.reignofnether.matchstart;

/**
 * How the eyes tour frames a rising site (06b / 06c, EyesDirector.sceneConstruction). Pure - no Minecraft types - so
 * ConsFramingTest covers it.
 * <p>Construction round 16 critic: the camera aimed 60% of the way toward the Commander (a non-worker standing on the
 * castle edge, no tool), none of the five workers was in frame, and the close shot was cut off - 4,297 wall px under the
 * top HUD band, the ghost's upper courses and the selection box off screen. Now, in this order:
 * <ol>
 *   <li>the building is FITTED: its box plus {@link #HEADROOM} (the ghost of the top course, the selection box) inside
 *   screen x {@link #SAFE_X0}-{@link #SAFE_X1}, y {@link #SAFE_Y0}-{@link #SAFE_Y1}, centred on the box;</li>
 *   <li>only then a pan toward ONE worker - one whose task is this site, within {@link #MAX_WORKER} blocks of it
 *   ({@link #pickWorker}); the Commander or a passer-by never moves the camera - and only as far as needed to bring it
 *   inside {@link #WORKER_X0}-{@link #WORKER_X1}, {@link #WORKER_Y0}-{@link #WORKER_Y1} (above the selection panel), and
 *   never so far that a corner of the building leaves the safe area: if the worker would still be outside, the whole
 *   building wins.</li>
 * </ol>
 * The RTS camera is orthographic: {@code zoom} blocks of view height, the view {@code zoom * aspect} wide; yaw and
 * pitch as OrthoviewClientEvents.centreCameraOnPosInstant uses them (getCamRotX / getCamRotY, degrees).
 */
public final class ConsFraming {

    private ConsFraming() { }

    /** The safe area every corner of the building (with headroom) stays in: clear of the top HUD band (y < 0.08 on the
     *  1600 x 900 stills) and the screen edges. */
    public static final double SAFE_X0 = 0.08, SAFE_X1 = 0.92, SAFE_Y0 = 0.12, SAFE_Y1 = 0.88;
    /** Where a worker is brought to: above the selection panel (bottom-left, y > ~0.8) and off the edges. */
    public static final double WORKER_X0 = 0.15, WORKER_X1 = 0.85, WORKER_Y0 = 0.15, WORKER_Y1 = 0.75;
    /** Blocks of ghost headroom above the box (its top course, the selection box). */
    public static final double HEADROOM = 1;
    /** The furthest a worker may stand from the site (horizontally, blocks) to be panned toward. */
    public static final double MAX_WORKER = 12;

    /** Forward, right and up of the camera (unit vectors), into {@code o[0..8]}. */
    static void basis(float yawDeg, float pitchDeg, double[] o) {
        double yR = Math.toRadians(yawDeg), xR = Math.toRadians(pitchDeg);
        double fx = -Math.sin(yR) * Math.cos(xR), fy = -Math.sin(xR), fz = Math.cos(yR) * Math.cos(xR);
        double rx = -fz, rz = fx, rl = Math.sqrt(rx * rx + rz * rz);
        if (rl < 1e-9) { rx = 1; rz = 0; rl = 1; }   // straight down: any horizontal right
        rx /= rl; rz /= rl;
        // up = right x forward (its y is positive for a camera looking down)
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
        o[0] = fx; o[1] = fy; o[2] = fz; o[3] = rx; o[4] = 0; o[5] = rz; o[6] = ux; o[7] = uy; o[8] = uz;
    }

    /** Point (x, y, z) on screen with the camera centred on {@code aim}: {x, y} fractions into {@code out}, y from the top. */
    public static void screen(double x, double y, double z, double[] aim, float yaw, float pitch, float aspect, float zoom,
                              double[] out) {
        double[] b = new double[9];
        basis(yaw, pitch, b);
        double dx = x - aim[0], dy = y - aim[1], dz = z - aim[2];
        double r = dx * b[3] + dy * b[4] + dz * b[5], u = dx * b[6] + dy * b[7] + dz * b[8];
        out[0] = 0.5 + r / (zoom * aspect);
        out[1] = 0.5 - u / zoom;
    }

    /** The box's centre with its headroom: {x, y, z}. */
    private static void centre(double[] box, double[] c) {
        c[0] = (box[0] + box[3]) * 0.5; c[1] = (box[1] + box[4] + HEADROOM) * 0.5; c[2] = (box[2] + box[5]) * 0.5;
    }

    /**
     * The zoom (blocks of view height) at which the whole of {@code box} {x0, y0, z0, x1, y1, z1} plus HEADROOM fits the
     * safe area centred on the box - never closer than {@code minZoom}.
     */
    public static float fitZoom(double[] box, float yaw, float pitch, float aspect, float minZoom) {
        double[] b = new double[9], c = new double[3];
        basis(yaw, pitch, b);
        centre(box, c);
        double need = 0;
        for (int k = 0; k < 8; k++) {
            double dx = ((k & 1) == 0 ? box[0] : box[3]) - c[0];
            double dy = ((k & 2) == 0 ? box[1] : box[4] + HEADROOM) - c[1];
            double dz = ((k & 4) == 0 ? box[2] : box[5]) - c[2];
            double r = dx * b[3] + dy * b[4] + dz * b[5], u = dx * b[6] + dy * b[7] + dz * b[8];
            need = Math.max(need, Math.abs(r) / ((0.5 - SAFE_X0) * aspect));
            need = Math.max(need, Math.abs(u) / (0.5 - SAFE_Y0));
        }
        return (float) Math.max(minZoom, need);
    }

    /**
     * Where to centre the camera at {@code zoom}: the box's centre (with headroom), panned toward {@code worker} {x, y, z}
     * (null: none) by the least that brings it into the worker area, clamped so every corner stays in the safe area.
     * The aim into {@code out}; true when the worker ends up inside the safe area.
     */
    public static boolean aim(double[] box, float yaw, float pitch, float aspect, float zoom, double[] worker,
                              double[] out) {
        double[] b = new double[9], c = new double[3];
        basis(yaw, pitch, b);
        centre(box, c);
        out[0] = c[0]; out[1] = c[1]; out[2] = c[2];
        if (worker == null)
            return false;
        double zw = zoom * aspect;
        // screen offsets of the corners (right r, up u) from the centre: the shift s keeps each in the safe area while
        // offset - s lies in [lo, hi] (right: x = 0.5 + (r - s) / zw; up: y = 0.5 - (u - s) / zoom)
        double rLoS = (SAFE_X0 - 0.5) * zw, rHiS = (SAFE_X1 - 0.5) * zw;
        double uLoS = (0.5 - SAFE_Y1) * zoom, uHiS = (0.5 - SAFE_Y0) * zoom;
        double rMin = Double.MAX_VALUE, rMax = -Double.MAX_VALUE, uMin = Double.MAX_VALUE, uMax = -Double.MAX_VALUE;
        for (int k = 0; k < 8; k++) {
            double dx = ((k & 1) == 0 ? box[0] : box[3]) - c[0];
            double dy = ((k & 2) == 0 ? box[1] : box[4] + HEADROOM) - c[1];
            double dz = ((k & 4) == 0 ? box[2] : box[5]) - c[2];
            double r = dx * b[3] + dy * b[4] + dz * b[5], u = dx * b[6] + dy * b[7] + dz * b[8];
            rMin = Math.min(rMin, r); rMax = Math.max(rMax, r); uMin = Math.min(uMin, u); uMax = Math.max(uMax, u);
        }
        double wx = worker[0] - c[0], wy = worker[1] - c[1], wz = worker[2] - c[2];
        double wr = wx * b[3] + wy * b[4] + wz * b[5], wu = wx * b[6] + wy * b[7] + wz * b[8];
        double sr = shift(wr, (WORKER_X0 - 0.5) * zw, (WORKER_X1 - 0.5) * zw, rMax - rHiS, rMin - rLoS);
        double su = shift(wu, (0.5 - WORKER_Y1) * zoom, (0.5 - WORKER_Y0) * zoom, uMax - uHiS, uMin - uLoS);
        out[0] += b[3] * sr + b[6] * su;
        out[1] += b[4] * sr + b[7] * su;
        out[2] += b[5] * sr + b[8] * su;
        double fr = wr - sr, fu = wu - su;
        return fr >= rLoS - 1e-9 && fr <= rHiS + 1e-9 && fu >= uLoS - 1e-9 && fu <= uHiS + 1e-9;
    }

    /** One axis: the least shift s (from 0) putting {@code w - s} in [lo, hi], clamped to the building's [sMin, sMax]. */
    private static double shift(double w, double lo, double hi, double sMin, double sMax) {
        double want = w - hi > 0 ? w - hi : w - lo < 0 ? w - lo : 0;
        if (sMin > sMax)   // the building does not fit at this zoom (fitZoom was not used): keep it centred
            return 0;
        return Math.max(sMin, Math.min(sMax, want));
    }

    /** Horizontal distance (blocks) from (x, z) to the box's footprint; 0 inside it. */
    public static double distance(double[] box, double x, double z) {
        double dx = x < box[0] ? box[0] - x : x > box[3] ? x - box[3] : 0;
        double dz = z < box[2] ? box[2] - z : z > box[5] ? z - box[5] : 0;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * The worker to pan toward: the nearest of the first {@code n} {x, y, z} whose task is this site ({@code onSite})
     * within MAX_WORKER of its footprint; -1 for none (never the Commander or a passer-by: they are not on the site).
     */
    public static int pickWorker(double[] box, double[][] workers, boolean[] onSite, int n) {
        int best = -1;
        double bestD = MAX_WORKER;
        for (int i = 0; i < n; i++) {
            if (!onSite[i])
                continue;
            double d = distance(box, workers[i][0], workers[i][2]);
            if (d <= bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }
}
